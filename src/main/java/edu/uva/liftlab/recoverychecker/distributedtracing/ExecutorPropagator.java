package edu.uva.liftlab.recoverychecker.distributedtracing;

import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.Units;

import java.util.*;

import static edu.uva.liftlab.recoverychecker.distributedtracing.utils.TracingUtil.*;
import static edu.uva.liftlab.recoverychecker.util.Constants.INSTRUMENTATION_SUFFIX;
import static edu.uva.liftlab.recoverychecker.util.SootUtils.getDryRunTraceFieldName;
import static edu.uva.liftlab.recoverychecker.util.SootUtils.printLog4j;

public class ExecutorPropagator {
    public static final List<String> executorServiceTypes = Arrays.asList(
            "java.util.concurrent.ExecutorService",
            "java.util.concurrent.ScheduledExecutorService",
            "com.google.common.util.concurrent.ListeningExecutorService"
    );
    private static final Logger LOG = LoggerFactory.getLogger(ExecutorPropagator.class);
    public SootClass sootClass;
    ExecutorPropagator(SootClass sootClass) {
        this.sootClass = sootClass;
    }

    public void wrapExecutorParameter(){
        for(SootMethod method : sootClass.getMethods()) {
            LOG.info("Propagating baggage for method: {}", method.getName());
            if(!method.getName().endsWith(INSTRUMENTATION_SUFFIX)){
                continue;
            }
            this.wrapExecutorParameter(method);
        }
    }

    protected void wrapExecutorParameter(SootMethod method) {
        if (!method.hasActiveBody()) {
            return;
        }

        Body body = method.retrieveActiveBody();
        UnitPatchingChain units = body.getUnits();
        List<Unit> originalUnits = new ArrayList<>(units);
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        for (Unit u : originalUnits) {
            if (u instanceof AssignStmt) {
                AssignStmt stmt = (AssignStmt) u;
                Value rightOp = stmt.getRightOp();
                if (rightOp instanceof InvokeExpr && shouldBeWrapped4Runnable((InvokeExpr) rightOp)) {
                    List<Unit> newUnits = new ArrayList<>();
                    LOG.info("Wrapping executor parameter in assignment: {}", stmt);
                    Local wrappedRunnableParameter = wrapRunnable((InstanceInvokeExpr) rightOp, lg, body, newUnits);
                    units.insertBefore(newUnits, u);
                    Local baseLocal = lg.generateLocal(((InstanceInvokeExpr) rightOp).getBase().getType());
                    units.insertBefore(Jimple.v().newAssignStmt(baseLocal, ((InstanceInvokeExpr) rightOp).getBase()), u);
                    InstanceInvokeExpr newInvoke = getNewInvokeWithTracedParameter((InvokeExpr) rightOp, wrappedRunnableParameter, baseLocal);
                    stmt.setRightOp(newInvoke);
                    try {
                        body.validate();
                    } catch (Exception e) {
                        LOG.error("Failed to validate body after wrapping executor parameter in assignment", e);
                        throw e;
                    }

                }
            }
            else if (u instanceof InvokeStmt && shouldBeWrapped4Runnable(((InvokeStmt) u).getInvokeExpr())){
                LOG.info("Wrapping executor parameter in invoke statement: {}", u);
                List<Unit> newUnits = new ArrayList<>();
                InstanceInvokeExpr instanceInvoke = (InstanceInvokeExpr) ((InvokeStmt) u).getInvokeExpr();
                Local wrappedRunnableParameter = wrapRunnable(instanceInvoke, lg, body, newUnits);
                units.insertBefore(newUnits, u);
                Local baseLocal = lg.generateLocal(instanceInvoke.getBase().getType());
                units.insertBefore(Jimple.v().newAssignStmt(baseLocal, (instanceInvoke.getBase())), u);
                InstanceInvokeExpr newInvoke = getNewInvokeWithTracedParameter(instanceInvoke, wrappedRunnableParameter, baseLocal);
                ((InvokeStmt) u).setInvokeExpr(newInvoke);
                try {
                    body.validate();
                } catch (Exception e) {
                    LOG.error("Failed to validate body after wrapping executor parameter in invokeStmt", e);
                    throw e;
                }
            }
        }
    }

    private boolean shouldBeWrapped4Runnable(InvokeExpr invoke){
        if (!(invoke instanceof InstanceInvokeExpr)) {
            LOG.info("Invoke expression is not an instance invoke expression: {}", invoke);
            return false;
        }

        InstanceInvokeExpr instanceInvoke = (InstanceInvokeExpr) invoke;
        Value base = instanceInvoke.getBase();

        if(base == null){
            return false;
        }

        if (isExecutorType(base.getType()) &&
                isExecutorMethod(invoke.getMethod()) &&
                !invoke.getArgs().isEmpty() &&
                isRunnableType(invoke.getArg(0).getType())) {

            return true;
        }

        return false;
    }


    private Local wrapRunnable(InstanceInvokeExpr instanceInvoke, LocalGeneratorUtil lg, Body body, List<Unit> newUnits){
        SootClass traceUtilClass = Scene.v().getSootClass("org.apache.cassandra.utils.dryrun.TraceUtil");
        //print sootmethod in traceUtilClass
        SootMethod shouldBeContextWrapMethod = traceUtilClass.getMethod("boolean shouldBeContextWrap(java.lang.Runnable,java.util.concurrent.Executor)");

        Value originalRunnable = instanceInvoke.getArg(0);
        Local tempRunnable = lg.generateLocal(originalRunnable.getType());

        Unit assignRunnableStmt = Jimple.v().newAssignStmt(tempRunnable, originalRunnable);
        newUnits.add(assignRunnableStmt);

        Local baseLocal = lg.generateLocal(instanceInvoke.getBase().getType());
        newUnits.add(Jimple.v().newAssignStmt(baseLocal, instanceInvoke.getBase()));

        StaticInvokeExpr invokeExpr = Jimple.v().newStaticInvokeExpr(
                shouldBeContextWrapMethod.makeRef(),
                Arrays.asList(originalRunnable, baseLocal)
        );

        Local resultRunnable = lg.generateLocal(BooleanType.v());

        AssignStmt assignStmt = Jimple.v().newAssignStmt(
                resultRunnable,
                invokeExpr
        );
        newUnits.add(assignStmt);

        List<Unit> contextWrapUnits = new ArrayList<>();
        Local wrappedRunnable = wrapExecutorRunnableParameterWithContext(lg, body, tempRunnable, contextWrapUnits);
        contextWrapUnits.add(Jimple.v().newAssignStmt(tempRunnable, wrappedRunnable));

        List<Unit> trycatchWrapUnits = wrapExecutorRunnableParameterWithTryCatch(tempRunnable);

        NopStmt endNop = Jimple.v().newNopStmt();
        NopStmt nop = Jimple.v().newNopStmt();
        IfStmt ifStmt = Jimple.v().newIfStmt(
                Jimple.v().newEqExpr(resultRunnable, IntConstant.v(0)),
                nop
        );
        newUnits.add(ifStmt);

        newUnits.addAll(contextWrapUnits);
        GotoStmt gotoStmt = Jimple.v().newGotoStmt(endNop);
        newUnits.add(gotoStmt);
        newUnits.add(nop);
        newUnits.addAll(trycatchWrapUnits);
        newUnits.add(endNop);
        return tempRunnable;
    }

    public InstanceInvokeExpr getNewInvokeWithTracedParameter(InvokeExpr instanceInvoke, Local tempRunnable, Local baseLocal){
        List<Value> newArgs = new ArrayList<>(instanceInvoke.getArgs());
        newArgs.set(0, tempRunnable);

        // Create appropriate invoke expression based on the original type
        InstanceInvokeExpr newInvoke;
        if (instanceInvoke instanceof VirtualInvokeExpr) {
            newInvoke = Jimple.v().newVirtualInvokeExpr(
                    baseLocal,
                    instanceInvoke.getMethod().makeRef(),
                    newArgs
            );
        } else if (instanceInvoke instanceof InterfaceInvokeExpr) {
            newInvoke = Jimple.v().newInterfaceInvokeExpr(
                    baseLocal,
                    instanceInvoke.getMethod().makeRef(),
                    newArgs
            );
        } else if (instanceInvoke instanceof SpecialInvokeExpr) {
            newInvoke = Jimple.v().newSpecialInvokeExpr(
                    baseLocal,
                    instanceInvoke.getMethod().makeRef(),
                    newArgs
            );
        } else {
            throw new RuntimeException("Unexpected invoke expression type: " + instanceInvoke.getClass());
        }
        return newInvoke;
    }



    private List<Unit> wrapExecutorRunnableParameterWithTryCatch(Local runnableArg) {

        List<Unit> res = new ArrayList<>();

        // Get the declared type of the Runnable parameter
        RefType runnableType = (RefType) runnableArg.getType();
        SootClass runnableClass = runnableType.getSootClass();

        // Set dryRunTrace fields for the runnable
        setFlagForTrace(
                runnableClass,
                runnableArg,
                res);
        return res;
    }

    private Local wrapExecutorRunnableParameterWithContext(LocalGeneratorUtil lg, Body body, Local runnableArg, List<Unit> res) {

        // Get Context class
        SootClass contextClass = Scene.v().getSootClass("io.opentelemetry.context.Context");

        // Generate local for current context
        Local contextLocal = lg.generateLocal(RefType.v(contextClass));

        res.add(Jimple.v().newAssignStmt(
                contextLocal,
                Jimple.v().newStaticInvokeExpr(
                        contextClass.getMethod("current",
                                Collections.emptyList(),
                                RefType.v("io.opentelemetry.context.Context")
                        ).makeRef()
                )
        ));


//        // Create local for the wrapped runnable
//        Local wrappedRunnable = lg.generateLocal(RefType.v("java.lang.Runnable"));


        // Wrap the runnable with Context.wrap()
        res.add(Jimple.v().newAssignStmt(
                runnableArg,
                Jimple.v().newInterfaceInvokeExpr(
                        contextLocal,
                        contextClass.getMethod("wrap",
                                Collections.singletonList(RefType.v("java.lang.Runnable")),
                                RefType.v("java.lang.Runnable")
                        ).makeRef(),
                        runnableArg
                )
        ));

        return runnableArg;
    }
    private void setFlagForTrace(
            SootClass targetClass,
            Value instance,
            List<Unit> newUnits) {

        // Build class hierarchy (parent classes first)
        List<SootClass> classHierarchy = new ArrayList<>();
        SootClass currentClass = targetClass;

        while (currentClass != null && !currentClass.getName().equals("java.lang.Object")) {

            classHierarchy.add(0, currentClass);
            currentClass = currentClass.hasSuperclass() ? currentClass.getSuperclass() : null;
        }

        for (SootClass cls : classHierarchy) {
            String fieldName = getDryRunTraceFieldName(cls);

            // Skip if field doesn't exist in this class
            if (!cls.declaresField(fieldName, BooleanType.v())) {
                LOG.debug("Skipping class {} as it does not declare field {}",
                        cls.getName(), fieldName);
                continue;
            }

            try {
                SootField field = cls.getField(fieldName, BooleanType.v());
                FieldRef needBaggageFieldRef = Jimple.v().newInstanceFieldRef(
                        instance,
                        field.makeRef()
                );

                Unit setNeedBaggage = Jimple.v().newAssignStmt(
                        needBaggageFieldRef,
                        IntConstant.v(1)
                );

                newUnits.add(setNeedBaggage);
                //units.insertAfter(setNeedBaggage, currentInsertPoint);
                //currentInsertPoint = setNeedBaggage;

                LOG.debug("Successfully set dry run trace for class: {}", cls.getName());

            } catch (Exception e) {
                LOG.error("Failed to set dry run trace for class: " + cls.getName(), e);
            }
        }
    }


//    private void  wrapExecutor(InstanceInvokeExpr invoke, Value executorBase,
//                              UnitPatchingChain units, Unit unit, LocalGeneratorUtil lg) {
//        //print all possible message for this function
//        LOG.info("ExecutorPropagator.wrapExecutor: invoke: {}", invoke);
//        // Get Context.current()
//        SootClass contextClass = Scene.v().getSootClass("io.opentelemetry.context.Context");
//        Local contextLocal = lg.generateLocal(RefType.v(contextClass));
//
//        units.insertBefore(
//                Jimple.v().newAssignStmt(
//                        contextLocal,
//                        Jimple.v().newStaticInvokeExpr(
//                                contextClass.getMethod("io.opentelemetry.context.Context current()").makeRef()
//                        )
//                ),
//                unit
//        );
//
//        SootClass executorClass = ((RefType)executorBase.getType()).getSootClass();
//        String wrapMethodSig;
//        Type tempType;
//
//        if (isScheduledExecutorService4Submit(executorClass)) {
//            wrapMethodSig = "java.util.concurrent.ScheduledExecutorService wrap(java.util.concurrent.ScheduledExecutorService)";
//            tempType = RefType.v("java.util.concurrent.ScheduledExecutorService");
//        } else if (isExecutorService4Submit(executorClass)) {
//            wrapMethodSig = "java.util.concurrent.ExecutorService wrap(java.util.concurrent.ExecutorService)";
//            tempType = RefType.v("java.util.concurrent.ExecutorService");
//        } else {
//            wrapMethodSig = "java.util.concurrent.Executor wrap(java.util.concurrent.Executor)";
//            tempType = RefType.v("java.util.concurrent.Executor");
//        }
//
//        //Wrap executor
//        Local tempExecutor = lg.generateLocal(tempType);
//        units.insertBefore(
//                Jimple.v().newAssignStmt(tempExecutor, executorBase),
//                unit
//        );
//
//        Local wrappedExecutor = lg.generateLocal(executorBase.getType());
//        units.insertBefore(
//                Jimple.v().newAssignStmt(
//                        wrappedExecutor,
//                        Jimple.v().newInterfaceInvokeExpr(
//                                contextLocal,
//                                contextClass.getMethod(wrapMethodSig).makeRef(),
//                                tempExecutor
//                        )
//                ),
//                unit
//        );
//
//        // replace original base
//        invoke.setBase(wrappedExecutor);
//    }

}