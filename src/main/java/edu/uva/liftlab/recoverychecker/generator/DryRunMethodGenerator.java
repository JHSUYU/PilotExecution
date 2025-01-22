package edu.uva.liftlab.recoverychecker.generator;

import edu.uva.liftlab.recoverychecker.isolation.stateredirection.ClassFilterHelper;
import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.tagkit.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static edu.uva.liftlab.recoverychecker.transformer.DryRunTransformer.SET_BY_DRY_RUN;
import static edu.uva.liftlab.recoverychecker.util.Constants.DRY_RUN_SUFFIX;
import static edu.uva.liftlab.recoverychecker.util.Constants.INSTRUMENTATION_SUFFIX;
import static edu.uva.liftlab.recoverychecker.util.SootUtils.*;

public class DryRunMethodGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(DryRunMethodGenerator.class);


    public void processClasses(ClassFilterHelper filter) {
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            if (filter.shouldSkip(sc)) continue;
            addDryRunFields(sc);
            List<SootMethod> methods = new ArrayList<>(sc.getMethods());
            for (SootMethod method : methods) {
                if (!method.getName().endsWith(INSTRUMENTATION_SUFFIX)) {
                    this.addInstrumentedFunction(method, sc);
                    this.addDryRunDivergeCode2OriginalFunc(method, sc, filter);
                }
            }
        }
    }

    private void addDryRunFields(SootClass sootClass){
        if (sootClass.isEnum() || sootClass.isInterface()) {
            return;
        }
        LOG.info("Processing class field instrumentation: " + sootClass.getName());

        ArrayList<SootField> originalFields = new ArrayList<>(sootClass.getFields());
        for(SootField originalField : originalFields) {
//            if (originalField.getName().endsWith(DRY_RUN_SUFFIX)) {
//                continue;
//            }
            if(originalField.getName().contains("assertionsDisabled")){
                continue;
            }
            String newFieldName = originalField.getName() + DRY_RUN_SUFFIX;
            String setByDryRunFieldName = newFieldName + SET_BY_DRY_RUN;

            if(sootClass.declaresFieldByName(newFieldName)) {
                continue;
            }
            int newModifiers = originalField.getModifiers();
            String originalSignature = originalField.getSignature();
            newModifiers &= ~Modifier.FINAL;
            newModifiers |= Modifier.PUBLIC;
            Type fieldType = originalField.getType();
            SootField newField = new SootField(
                    newFieldName,
                    fieldType,
                    newModifiers
                    // default to null for other types
            );
            for (Tag tag : originalField.getTags()) {
                newField.addTag(tag);
            }
            SootField setByDryRunField = new SootField(
                    setByDryRunFieldName,
                    BooleanType.v(),
                    newModifiers
            );

            try {
                sootClass.addField(newField);
                sootClass.addField(setByDryRunField);
                //LOG.info("Added dryrun field {} to class {}", newFieldName, sootClass.getName());
            } catch (Exception e) {
                LOG.error("Failed to add field {} to class {}: {}",
                        newFieldName, sootClass.getName(), e.getMessage());
            }
        }
    }


    public void addInstrumentedFunction(SootMethod originalMethod, SootClass sootClass) {
        if(!originalMethodShouldBeInstrumented(originalMethod, sootClass)) {
            return;
        }

        String instrumentationMethodName = originalMethod.getName() + INSTRUMENTATION_SUFFIX;
        SootMethod instrumentationMethod = new SootMethod(
                instrumentationMethodName,
                originalMethod.getParameterTypes(),
                originalMethod.getReturnType(),
                originalMethod.getModifiers()
        );
        sootClass.addMethod(instrumentationMethod);

        Body originalBody = originalMethod.retrieveActiveBody();
        Body instrumentationBody = Jimple.v().newBody(instrumentationMethod);
        instrumentationBody.importBodyContentsFrom(originalBody);
        instrumentationMethod.setActiveBody(instrumentationBody);
    }

    public void addDryRunDivergeCode2OriginalFunc(SootMethod originalMethod, SootClass sc, ClassFilterHelper filter) {
        if(!originalMethodShouldBeInstrumented(originalMethod, sc) || filter.getStartingPoints().contains(originalMethod.getSignature())) {
            return;
        }

        String instrumentationMethodName = originalMethod.getName() + INSTRUMENTATION_SUFFIX;
        SootMethod instrumentationMethod = sc.getMethod(
                instrumentationMethodName,
                originalMethod.getParameterTypes(),
                originalMethod.getReturnType()
        );
        Body originalBody = originalMethod.getActiveBody();
        Body newBody = (Body) originalBody.clone();
        PatchingChain<Unit> units = newBody.getUnits();
        LocalGeneratorUtil lg = new LocalGeneratorUtil(newBody);
        Local isDryRunLocal = lg.generateLocal(BooleanType.v());
        StaticInvokeExpr isDryRunExpr = Jimple.v().newStaticInvokeExpr(
                Scene.v().makeMethodRef(
                        Scene.v().loadClassAndSupport("org.apache.cassandra.utils.dryrun.TraceUtil"),
                        "isDryRun",
                        Collections.emptyList(),
                        BooleanType.v(),
                        true
                )
        );
        Unit assignDryRun = Jimple.v().newAssignStmt(isDryRunLocal, isDryRunExpr);
        Unit lastIdentityStmt = null;
        Unit firstNonIdentityStmt = null;
        boolean foundNonIdentity = false;

        for (Unit u : units) {
            if (!foundNonIdentity) {
                if (u instanceof IdentityStmt) {
                    lastIdentityStmt = u;
                } else {
                    firstNonIdentityStmt = u;
                    foundNonIdentity = true;
                }
            }
        }
        if (lastIdentityStmt != null) {
            units.insertAfter(assignDryRun, lastIdentityStmt);
        } else {
            units.insertBefore(assignDryRun, units.getFirst());
        }
        // Go to original code
        GotoStmt gotoOriginal = Jimple.v().newGotoStmt(firstNonIdentityStmt);

        // if isDryRun == false;
        IfStmt ifStmt = Jimple.v().newIfStmt(
                Jimple.v().newEqExpr(isDryRunLocal, IntConstant.v(0)),
                gotoOriginal
        );
        units.insertAfter(ifStmt, assignDryRun);

        // Create instrumentation call
        InvokeExpr invokeInstrumentation;
        if (originalMethod.isStatic()) {
            invokeInstrumentation = Jimple.v().newStaticInvokeExpr(
                    instrumentationMethod.makeRef(),
                    newBody.getParameterLocals()
            );
        } else {
//            invokeInstrumentation = Jimple.v().newVirtualInvokeExpr(
//                    newBody.getThisLocal(),
//                    instrumentationMethod.makeRef(),
//                    newBody.getParameterLocals()
//            );
            invokeInstrumentation = Jimple.v().newSpecialInvokeExpr(
                    newBody.getThisLocal(),
                    sc.getMethod(instrumentationMethodName,
                            originalMethod.getParameterTypes(),
                            originalMethod.getReturnType()).makeRef(),
                    newBody.getParameterLocals()
            );
        }

        String logInput = "Enter dry run method "+ originalMethod.getName()+" in class "+sc.getName();
        List<Unit> logInputPrintUnits = printLog4j(logInput,lg);
        String logFinish = "Successfully finish dry run method "+ originalMethod.getName()+" in class "+sc.getName();
        List<Unit> logFinishPrintUnits = printLog4j(logFinish,lg);


        if (!(originalMethod.getReturnType() instanceof VoidType)) {
            Local resultLocal = lg.generateLocal(originalMethod.getReturnType());
            Unit assignResult = Jimple.v().newAssignStmt(resultLocal, invokeInstrumentation);
            Unit returnStmt = Jimple.v().newReturnStmt(resultLocal);

            units.insertAfter(assignResult, ifStmt);
            for(Unit u: logInputPrintUnits){
                units.insertBefore(u, assignResult);
            }
            units.insertAfter(returnStmt, assignResult);
            for (Unit u : logFinishPrintUnits) {
                units.insertBefore(u, returnStmt);
            }
            units.insertAfter(gotoOriginal, returnStmt);
        } else {
            Unit invokeStmt = Jimple.v().newInvokeStmt(invokeInstrumentation);
            Unit returnVoidStmt = Jimple.v().newReturnVoidStmt();

            units.insertAfter(invokeStmt, ifStmt);
            for(Unit u:logInputPrintUnits){
                units.insertBefore(u, invokeStmt);
            }
            units.insertAfter(returnVoidStmt, invokeStmt);
            for (Unit u : logFinishPrintUnits) {
                units.insertBefore(u, returnVoidStmt);
            }
            units.insertAfter(gotoOriginal, returnVoidStmt);
        }

        originalMethod.setActiveBody(newBody);
        newBody.validate();
    }

}
