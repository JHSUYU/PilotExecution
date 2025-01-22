package edu.uva.liftlab.recoverychecker.microfork;

import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import soot.util.Chain;
import java.util.*;

import static edu.uva.liftlab.recoverychecker.util.Constants.*;
import static edu.uva.liftlab.recoverychecker.util.SootUtils.getLastIdentityStmt;
import static edu.uva.liftlab.recoverychecker.util.SootUtils.printLog;

public class ThreadAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadAnalyzer.class);
    private SootClass sootClass;
    private SootClass shadowSootClass;

    private HashMap<SootMethod, Unit> divergePoints = new HashMap<>();

    private Pair<SootMethod, Unit> divergePoint;


    private Pair<SootMethod, Unit> shadowDivergePoint;

    private HashMap<SootMethod, Unit> shadowDivergePoints = new HashMap<>();

    private HashSet<String> divergePointSigs = new HashSet<>();

    private HashSet<String> shadowDivergePointSigs = new HashSet<>();

    private HashMap<SootMethod, Unit> goToPoints = new HashMap<>();

    public ThreadAnalyzer(SootClass sootClass, SootClass sootShadowClass){
        this.sootClass = sootClass;
        this.shadowSootClass = sootShadowClass;
    }

    public void hookMicroFork() {
        SootMethod shadowMethod = createShadowVersionMethods();
        createShadowVersionMethodsForProcessAssignQueue();
        findDivergePoints(sootClass);
        //print the item in divergePoints
        for (Map.Entry<SootMethod, Unit> entry : divergePoints.entrySet()) {
            SootMethod method = entry.getKey();
            Unit divergePoint = entry.getValue();
            LOG.info("After Completion, the DivergePoint is: " + divergePoint + " in method: " + method);
        }

        findShadowDivergePoints(shadowSootClass);
        for (Map.Entry<SootMethod, Unit> entry : shadowDivergePoints.entrySet()) {
            SootMethod method = entry.getKey();
            Unit divergePoint = entry.getValue();
            LOG.info("After Completion, the ShadowDivergePoint is: " + divergePoint + " in method: " + method);
        }

        instrumentOriginalThread();
        instrumentShadowThread();
    }

    public SootMethod createShadowVersionMethodsForRun(){
        SootClass targetClass = Scene.v().getSootClass("org.apache.hadoop.hbase.master.assignment.AssignmentManager");
        SootMethod originalMethod = targetClass.getMethodByName("waitOnAssignQueue");

        SootMethod methodToRemove = targetClass.getMethodByName("waitOnAssignQueue$shadow");

        targetClass.removeMethod(methodToRemove);

        Body originalBody = originalMethod.retrieveActiveBody();

        SootMethod shadowMethod = new SootMethod(
                "waitOnAssignQueue$shadow",
                originalMethod.getParameterTypes(),
                originalMethod.getReturnType(),
                originalMethod.getModifiers()
        );

        Body newBody = (Body) originalBody.clone();

        SootField originalField = originalMethod.getDeclaringClass().getFieldByName("pendingAssignQueue");

        String newFieldName = "pendingAssignQueue$dryrun";
        SootField newField = null;
        try {
            newField = originalMethod.getDeclaringClass().getFieldByName(newFieldName);
        } catch (RuntimeException e) {
            // 如果字段不存在，创建一个新的，保持和原始字段相同的类型和修饰符
            newField = new SootField(
                    newFieldName,
                    originalField.getType(),
                    originalField.getModifiers()
            );
            originalMethod.getDeclaringClass().addField(newField);
        }


        for (Unit unit : newBody.getUnits()) {
            for (ValueBox valueBox : unit.getUseAndDefBoxes()) {
                Value value = valueBox.getValue();
                if (value instanceof FieldRef) {
                    FieldRef fieldRef = (FieldRef) value;
                    if (fieldRef.getField().equals(originalField)) {
                        // 创建新的字段引用
                        if (fieldRef instanceof StaticFieldRef) {
                            valueBox.setValue(Jimple.v().newStaticFieldRef(newField.makeRef()));
                        } else if (fieldRef instanceof InstanceFieldRef) {
                            InstanceFieldRef instanceRef = (InstanceFieldRef) fieldRef;
                            valueBox.setValue(
                                    Jimple.v().newInstanceFieldRef(
                                            instanceRef.getBase(),
                                            newField.makeRef()
                                    )
                            );
                        }
                    }
                }
            }
        }

        // 4. 设置新方法的 body
        shadowMethod.setActiveBody(newBody);
        targetClass.addMethod(shadowMethod);

        return shadowMethod;
    }

    public SootMethod createShadowVersionMethods(){
        SootClass targetClass = Scene.v().getSootClass("org.apache.hadoop.hbase.master.assignment.AssignmentManager");
        SootMethod originalMethod = targetClass.getMethodByName("waitOnAssignQueue");

        SootMethod methodToRemove = targetClass.getMethodByName("waitOnAssignQueue$shadow");

        targetClass.removeMethod(methodToRemove);

        Body originalBody = originalMethod.retrieveActiveBody();

        SootMethod shadowMethod = new SootMethod(
                "waitOnAssignQueue$shadow",
                originalMethod.getParameterTypes(),
                originalMethod.getReturnType(),
                originalMethod.getModifiers()
        );

        Body newBody = (Body) originalBody.clone();

        SootField originalField = originalMethod.getDeclaringClass().getFieldByName("pendingAssignQueue");

        String newFieldName = "pendingAssignQueue$dryrun";
        SootField newField = null;
        try {
            newField = originalMethod.getDeclaringClass().getFieldByName(newFieldName);
        } catch (RuntimeException e) {
            // 如果字段不存在，创建一个新的，保持和原始字段相同的类型和修饰符
            newField = new SootField(
                    newFieldName,
                    originalField.getType(),
                    originalField.getModifiers()
            );
            originalMethod.getDeclaringClass().addField(newField);
        }


        for (Unit unit : newBody.getUnits()) {
            for (ValueBox valueBox : unit.getUseAndDefBoxes()) {
                Value value = valueBox.getValue();
                if (value instanceof FieldRef) {
                    FieldRef fieldRef = (FieldRef) value;
                    if (fieldRef.getField().equals(originalField)) {
                        // 创建新的字段引用
                        if (fieldRef instanceof StaticFieldRef) {
                            valueBox.setValue(Jimple.v().newStaticFieldRef(newField.makeRef()));
                        } else if (fieldRef instanceof InstanceFieldRef) {
                            InstanceFieldRef instanceRef = (InstanceFieldRef) fieldRef;
                            valueBox.setValue(
                                    Jimple.v().newInstanceFieldRef(
                                            instanceRef.getBase(),
                                            newField.makeRef()
                                    )
                            );
                        }
                    }
                }
            }
        }

        // 4. 设置新方法的 body
        shadowMethod.setActiveBody(newBody);
        targetClass.addMethod(shadowMethod);

        return shadowMethod;
    }

    public SootMethod createShadowVersionMethodsForProcessAssignQueue() {
        SootClass targetClass = Scene.v().getSootClass("org.apache.hadoop.hbase.master.assignment.AssignmentManager");
        SootMethod originalMethod = targetClass.getMethodByName("processAssignQueue");

        // 移除已存在的shadow方法(如果存在)
        SootMethod methodToRemove = targetClass.getMethodByName("processAssignQueue$shadow");
        if (methodToRemove != null) {
            targetClass.removeMethod(methodToRemove);
        }

        Body originalBody = originalMethod.retrieveActiveBody();

        // 创建新的shadow方法
        SootMethod shadowMethod = new SootMethod(
                "processAssignQueue$shadow",
                originalMethod.getParameterTypes(),
                originalMethod.getReturnType(),
                originalMethod.getModifiers()
        );

        // 克隆原始方法体
        Body newBody = (Body) originalBody.clone();

        // 获取this的local变量
        Local thisLocal = newBody.getThisLocal();

        // 替换方法调用
        for (Unit unit : newBody.getUnits()) {
            if (unit instanceof Stmt) {
                Stmt stmt = (Stmt) unit;
                if (stmt.containsInvokeExpr()) {
                    InvokeExpr invoke = stmt.getInvokeExpr();
                    if (invoke.getMethod().getName().equals("waitOnAssignQueue")) {
                        // 创建对shadow方法的调用
                        SootMethodRef shadowMethodRef = Scene.v().makeMethodRef(
                                targetClass,
                                "waitOnAssignQueue$shadow",
                                invoke.getMethod().getParameterTypes(),
                                invoke.getMethod().getReturnType(),
                                invoke.getMethod().isStatic()
                        );

                        // 替换方法引用
                        if (invoke instanceof InstanceInvokeExpr) {
                            stmt.getInvokeExprBox().setValue(
                                    Jimple.v().newVirtualInvokeExpr(
                                            thisLocal,  // 使用已有的this local
                                            shadowMethodRef
                                    )
                            );
                        } else if (invoke instanceof StaticInvokeExpr) {
                            stmt.getInvokeExprBox().setValue(
                                    Jimple.v().newStaticInvokeExpr(shadowMethodRef)
                            );
                        }
                    }
                }
            }
        }

        // 设置新方法的 body
        shadowMethod.setActiveBody(newBody);
        targetClass.addMethod(shadowMethod);

        return shadowMethod;
    }

    private void findShadowDivergePoints(SootClass cls){
        SootMethod runMethod = cls.getMethodByName("run");
        Set<SootMethod> visitedMethods = new HashSet<>();
        findShadowDivergePointInMethod(runMethod, visitedMethods);
    }

    private void findDivergePointInMethod(SootMethod method, Set<SootMethod> visitedMethods){
        if(visitedMethods.contains(method)){
            return;
        }

        Body body = method.retrieveActiveBody();
        boolean foundDivergePoint = false;

        List<Unit> originalUnits = new ArrayList<>(body.getUnits());

        for(Unit unit: originalUnits){
            LOG.info("Unit is: " + unit+ "  type is: " + unit.getClass());
            if(!foundDivergePoint){
                if(unit instanceof InvokeStmt){
                    LOG.info("InstanceInvokeExpr: " + ((InvokeStmt) unit).getInvokeExpr().getMethod().getName());
                }
                if (unit instanceof InvokeStmt &&
                        ((((InvokeStmt) unit).getInvokeExpr().getMethod().getName().contains(TARGET_FUNC_NAME1)) ||  ((InvokeStmt) unit).getInvokeExpr().getMethod().getName().contains(TARGET_FUNC_NAME2))&&
                        ((InvokeStmt) unit).getInvokeExpr().getMethod().getDeclaringClass().getName().equals("org.apache.hadoop.hbase.master.assignment.AssignmentManager")
                ) {
                    LOG.info("Found InstanceInvokeExpr: " + unit);
                    InvokeExpr invokeExpr = ((InvokeStmt) unit).getInvokeExpr();
                    SootMethod targetMethod = invokeExpr.getMethod();
                    divergePoints.put(method, unit);
                    divergePointSigs.add(method.getName());
                    foundDivergePoint = true;
                    findDivergePointInMethod(targetMethod, visitedMethods);
                } else if (unit instanceof AssignStmt && ((AssignStmt) unit).getRightOp() instanceof InvokeExpr) {
                    AssignStmt assignStmt = (AssignStmt) unit;
                    if (assignStmt.getRightOp() instanceof InvokeExpr) {
                        InvokeExpr invokeExpr = (InvokeExpr) assignStmt.getRightOp();
                        SootMethod invokedMethod = invokeExpr.getMethod();

                        // 检查是否是SimpleProcedureScheduler或AbstractProcedureScheduler的特定方法调用
                        if ((invokedMethod.getName().equals("waitOnAssignQueue") &&
                                (invokedMethod.getDeclaringClass().getName().equals("org.apache.hadoop.hbase.master.assignment.AssignmentManager") ||
                                        invokedMethod.getDeclaringClass().getName().equals("org.apache.hadoop.hbase.procedure2.AbstractProcedureScheduler"))) ||
                                (invokedMethod.getName().equals("queueHasRunnables") &&
                                        invokedMethod.getDeclaringClass().getName().equals("org.apache.hadoop.hbase.procedure2.SimpleProcedureScheduler"))) {
                            LOG.info("Found SimpleProcedureScheduler poll call: " + unit);
                            divergePoints.put(method, unit);
                            divergePointSigs.add(method.getName());
                            foundDivergePoint = true;
                            findDivergePointInMethod(invokedMethod, visitedMethods);
                        }
                    }
                } else {
                    if(isDivergePoint(unit) && !method.getName().contains("run")){
                        LOG.info("Found DivergePoint: " + unit + " in method: " + method);
                        foundDivergePoint = true;
                        divergePoints.put(method, unit);
                        divergePointSigs.add(method.getName());
                        divergePoint = Pair.of(method, unit);
                    }
                }
            }
        }
    }

    private void findDivergePoints(SootClass cls){
        SootMethod runMethod = cls.getMethodByName("run");
        Set<SootMethod> visitedMethods = new HashSet<>();
        findDivergePointInMethod(runMethod, visitedMethods);
    }

    private void findShadowDivergePointInMethod(SootMethod method, Set<SootMethod> visitedMethods){
        if(visitedMethods.contains(method)){
            return;
        }

        Body body = method.retrieveActiveBody();
        boolean foundDivergePoint = false;

        List<Unit> originalUnits = new ArrayList<>(body.getUnits());

        for(Unit unit: originalUnits){
            LOG.info("Unit is: " + unit+ "  type is: " + unit.getClass());
            if(!foundDivergePoint){
                if(unit instanceof InvokeStmt){
                    LOG.info("InstanceInvokeExpr: " + ((InvokeStmt) unit).getInvokeExpr().getMethod().getName());
                }
                if (unit instanceof InvokeStmt &&
                        ((((InvokeStmt) unit).getInvokeExpr().getMethod().getName().contains(TARGET_FUNC_NAME1)) ||  ((InvokeStmt) unit).getInvokeExpr().getMethod().getName().contains(TARGET_FUNC_NAME2))&&
                        ((InvokeStmt) unit).getInvokeExpr().getMethod().getDeclaringClass().getName().equals("org.apache.hadoop.hbase.master.assignment.AssignmentManager")
                ) {
                    LOG.info("Found InstanceInvokeExpr: " + unit);
                    InvokeExpr invokeExpr = ((InvokeStmt) unit).getInvokeExpr();
                    SootMethod targetMethod = invokeExpr.getMethod();
                    shadowDivergePoints.put(method, unit);
                    shadowDivergePointSigs.add(method.getName());
                    foundDivergePoint = true;
                    findShadowDivergePointInMethod(targetMethod, visitedMethods);
                } else if (unit instanceof AssignStmt && ((AssignStmt) unit).getRightOp() instanceof InvokeExpr) {
                    AssignStmt assignStmt = (AssignStmt) unit;
                    if (assignStmt.getRightOp() instanceof InvokeExpr) {
                        InvokeExpr invokeExpr = (InvokeExpr) assignStmt.getRightOp();
                        SootMethod invokedMethod = invokeExpr.getMethod();
                        // 检查是否是SimpleProcedureScheduler或AbstractProcedureScheduler的特定方法调用
                        if ((invokedMethod.getName().contains("waitOnAssignQueue") &&
                                (invokedMethod.getDeclaringClass().getName().equals("org.apache.hadoop.hbase.master.assignment.AssignmentManager") ||
                                        invokedMethod.getDeclaringClass().getName().equals("org.apache.hadoop.hbase.procedure2.AbstractProcedureScheduler"))) ||
                                (invokedMethod.getName().equals("queueHasRunnables") &&
                                        invokedMethod.getDeclaringClass().getName().equals("org.apache.hadoop.hbase.procedure2.SimpleProcedureScheduler"))) {
                            LOG.info("Found SimpleProcedureScheduler poll call: " + unit);
                            shadowDivergePoints.put(method, unit);
                            shadowDivergePointSigs.add(method.getName());
                            foundDivergePoint = true;
                            findShadowDivergePointInMethod(invokedMethod, visitedMethods);
                        }
                    }
                } else {
                    if(isDivergePoint(unit) && !method.getName().contains("run")){
                        LOG.info("Found DivergePoint: " + unit + " in method: " + method);
                        foundDivergePoint = true;
                        shadowDivergePoints.put(method, unit);
                        shadowDivergePointSigs.add(method.getName());
                        shadowDivergePoint = Pair.of(method, unit);
                    }
                }
            }
        }

    }

    public boolean isDivergePoint(Unit unit){
        if(mode.equals("SEDA")){
            return isDivergePointSEDA(unit);
        }

        if(debug){
            return isDivergePointDebug(unit);
        }else {
            return isDivergePointHbase(unit);
        }

    }

    public boolean isDivergePointSEDA(Unit unit){
        if(unit instanceof InvokeStmt){
            InvokeStmt invokeStmt = (InvokeStmt) unit;
            InvokeExpr invokeExpr = invokeStmt.getInvokeExpr();
            String name = invokeExpr.getMethod().getName();
            String className = invokeExpr.getMethod().getDeclaringClass().getName();
            if(name.contains("await") && className.contains("org.apache.hadoop.hbase.util.ConditionVariableWrapper")){
                return true;
            }
        }
        return false;
    }

    public boolean isDivergePointDebug(Unit unit){
        if (unit instanceof IfStmt){
            return true;
        }
        return false;
    }

    public boolean isDivergePointHbase(Unit unit){
        if (unit instanceof AssignStmt) {
            AssignStmt assign = (AssignStmt)unit;
            Value rightOp = assign.getRightOp();


            if (rightOp instanceof FieldRef) {
                FieldRef fieldRef = (FieldRef)rightOp;
                if ("map".equals(fieldRef.getField().getName()) &&
                        "org.apache.hadoop.hbase.io.hfile.LruBlockCache".equals(fieldRef.getField().getDeclaringClass().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void instrumentOriginalThread(){
        LOG.info("DivergePoints size is: " + divergePoints.size());
        initializeLocalsForOriginalThread();
        for(Map.Entry<SootMethod, Unit> entry : divergePoints.entrySet()) {
            SootMethod method = entry.getKey();
            Unit divergePoint = entry.getValue();

            Body body = method.retrieveActiveBody();
            Chain<Unit> units = body.getUnits();

            // 在每个方法的 diverge point 处插入记录代码
            List<Unit> monitorCode = createStateRecordCode(body);
            units.insertBefore(monitorCode, divergePoint);
            List<Unit> monitorFieldCode = createFieldRecordCode(body);
            units.insertBefore(monitorFieldCode, divergePoint);
        }
        //createShadowThread();
    }

    public void createShadowThread() {
        SootMethod targetMethod = divergePoint.getLeft();
        Unit unit = divergePoint.getRight();

        if (unit instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) unit;
            Value rightOp = assignStmt.getRightOp();

            if (rightOp instanceof InstanceFieldRef) {
                InstanceFieldRef fieldRef = (InstanceFieldRef) rightOp;
                SootField runnablesField = fieldRef.getField();

                // 获取runnables的类型
                Type type = runnablesField.getType();
                if (!(type instanceof RefType)) {
                    return;
                }
                SootClass runnablesClass = ((RefType) type).getSootClass();

                Chain<Unit> units = targetMethod.retrieveActiveBody().getUnits();
                Unit targetUnit = divergePoint.getRight();
                List<Unit> tobeInserted = new ArrayList<>();

                // 创建临时变量来存储runnables对象
                Local runnablesLocal = Jimple.v().newLocal("$runnablesTemp", runnablesField.getType());
                targetMethod.retrieveActiveBody().getLocals().add(runnablesLocal);

                // 创建临时变量来存储isTainted值
                Local isTaintedLocal = Jimple.v().newLocal("$isTaintedTemp", BooleanType.v());
                targetMethod.retrieveActiveBody().getLocals().add(isTaintedLocal);

                // 获取runnables字段的值
                tobeInserted.add(
                        Jimple.v().newAssignStmt(
                                runnablesLocal,
                                Jimple.v().newInstanceFieldRef(
                                        fieldRef.getBase(),
                                        runnablesField.makeRef()
                                )
                        )
                );

                // 获取runnables的isTainted值
                tobeInserted.add(
                        Jimple.v().newAssignStmt(
                                isTaintedLocal,
                                Jimple.v().newInstanceFieldRef(
                                        runnablesLocal,
                                        Scene.v().makeFieldRef(
                                                runnablesClass,
                                                "isTainted",
                                                BooleanType.v(),
                                                false
                                        )
                                )
                        )
                );

                // 创建NopStmt作为if语句的跳转目标
                NopStmt endIfStmt = Jimple.v().newNopStmt();

                // 创建if条件 - 如果isTainted为false，跳转到endIfStmt
                IfStmt ifStmt = Jimple.v().newIfStmt(
                        Jimple.v().newEqExpr(isTaintedLocal, IntConstant.v(0)),
                        endIfStmt
                );
                tobeInserted.add(ifStmt);

                // 在if条件为true时创建shadow thread
                tobeInserted.add(
                        Jimple.v().newInvokeStmt(
                                Jimple.v().newStaticInvokeExpr(
                                        Scene.v().makeMethodRef(
                                                Scene.v().getSootClass(CREATE_SHADOW_THREAD_CLASS),
                                                "createShadowThread",
                                                Collections.emptyList(),
                                                VoidType.v(),
                                                true
                                        )
                                )
                        )
                );

                // 添加endIfStmt
                tobeInserted.add(endIfStmt);

                // 插入所有新的语句
                for (Unit insertUnit : tobeInserted) {
                    units.insertBefore(insertUnit, targetUnit);
                }
            }
        }
    }

    public Set<Local> getParameterLocals(SootMethod method) {
        Body body = method.retrieveActiveBody();
        HashSet<Local> res = new HashSet<>();
        for(Unit u: body.getUnits()) {
            if(u instanceof IdentityStmt) {
                IdentityStmt identityStmt = (IdentityStmt) u;
                // 检查右操作数是否为参数引用
                if(identityStmt.getLeftOp() instanceof Local &&
                        identityStmt.getRightOp() instanceof ParameterRef) {
                    res.add((Local) identityStmt.getLeftOp());
                }

                if(identityStmt.getLeftOp() instanceof Local &&
                        identityStmt.getRightOp() instanceof ThisRef) {
                    res.add((Local) identityStmt.getLeftOp());
                }


            }
        }
        return res;
    }

    public void initializeLocalsForOriginalThread() {
        LOG.info("DivergePoints size in initializeLocalsForOriginalThread is: " + divergePoints.size());

        for(Map.Entry<SootMethod, Unit> entry:divergePoints.entrySet()){
            SootMethod method = entry.getKey();
            Body body = method.retrieveActiveBody();
            Chain<Unit> units = body.getUnits();
            Unit lastIdentityStmt = getLastIdentityStmt(body);
            List<Unit> tobeInserted = new ArrayList<>();
            Set<Local> parameterLocals = getParameterLocals(method);
            for(Local local:body.getLocals()){
                if (parameterLocals.contains(local)){
                    continue;
                }
                LOG.info("Initializing local: " + local.getName() + " in method: " + body.getMethod().getSignature() + " type: " + local.getType() + " isParameter: " + parameterLocals.contains(local));
                Type type  = local.getType();
                if(type instanceof PrimType) {
                    if(type instanceof IntType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                IntConstant.v(0)
                        ));
                    } else if(type instanceof BooleanType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                IntConstant.v(0)  // boolean 在 Jimple 中用 0 表示 false
                        ));
                    } else if(type instanceof FloatType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                FloatConstant.v(0.0f)
                        ));
                    } else if(type instanceof DoubleType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                DoubleConstant.v(0.0d)
                        ));
                    } else if(type instanceof LongType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                LongConstant.v(0L)
                        ));
                    } else if(type instanceof ByteType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                IntConstant.v(0)  // byte 在 Jimple 中用 int 表示
                        ));
                    } else if(type instanceof ShortType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                IntConstant.v(0)  // short 在 Jimple 中用 int 表示
                        ));
                    } else if(type instanceof CharType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                IntConstant.v(0)  // char 在 Jimple 中用 int 表示
                        ));
                    }
                } else {
                    tobeInserted.add(Jimple.v().newAssignStmt(
                            local,
                            NullConstant.v()
                    ));
                }
            }
            assert lastIdentityStmt!=null;
            LOG.info("Tobeinserted size in initializeLocalsForOriginalThread is: " + tobeInserted.size());
            units.insertAfter(tobeInserted, lastIdentityStmt);
        }
    }

    public void initializeLocalsForShadowThread() {
        for(Map.Entry<SootMethod, Unit> entry:shadowDivergePoints.entrySet()){
            SootMethod method = entry.getKey();
            Body body = method.retrieveActiveBody();
            Chain<Unit> units = body.getUnits();
            Unit lastIdentityStmt = getLastIdentityStmt(body);
            List<Unit> tobeInserted = new ArrayList<>();
            Set<Local> parameterLocals = getParameterLocals(method);
            for(Local local:body.getLocals()){
                if (parameterLocals.contains(local)){
                    continue;
                }
                Type type  = local.getType();
                if(type instanceof PrimType) {
                    if(type instanceof IntType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                IntConstant.v(0)
                        ));
                    } else if(type instanceof BooleanType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                IntConstant.v(0)  // boolean 在 Jimple 中用 0 表示 false
                        ));
                    } else if(type instanceof FloatType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                FloatConstant.v(0.0f)
                        ));
                    } else if(type instanceof DoubleType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                DoubleConstant.v(0.0d)
                        ));
                    } else if(type instanceof LongType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                LongConstant.v(0L)
                        ));
                    } else if(type instanceof ByteType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                IntConstant.v(0)  // byte 在 Jimple 中用 int 表示
                        ));
                    } else if(type instanceof ShortType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                IntConstant.v(0)  // short 在 Jimple 中用 int 表示
                        ));
                    } else if(type instanceof CharType) {
                        tobeInserted.add(Jimple.v().newAssignStmt(
                                local,
                                IntConstant.v(0)  // char 在 Jimple 中用 int 表示
                        ));
                    }
                } else {
                    tobeInserted.add(Jimple.v().newAssignStmt(
                            local,
                            NullConstant.v()
                    ));
                }
            }
            assert lastIdentityStmt!=null;
            units.insertAfter(tobeInserted, lastIdentityStmt);
        }
    }

    public void  instrumentShadowThread(){
        LOG.info("ShadowDivergePoints size is: " + shadowDivergePoints.size());
        for (Map.Entry<SootMethod, Unit> entry: shadowDivergePoints.entrySet()) {
            SootMethod method = entry.getKey();
            Unit divergePoint = entry.getValue();

            Body body = method.retrieveActiveBody();
            Chain<Unit> units = body.getUnits();
            List<Unit> restoreCode = createRestoreCode(body, method);
            List<Unit> fieldRestoreCode = createFieldRestoreCode(body, method);
            LocalGeneratorUtil lg = new LocalGeneratorUtil(body);
            NopStmt skipRestoreLabel = Jimple.v().newNopStmt();
            NopStmt gotoStmt = Jimple.v().newNopStmt();
            List<Unit> tobeInserted = new ArrayList<>();
            tobeInserted.add(gotoStmt);
            Local isFastForward = lg.generateLocalWithId(BooleanType.v(), "$isFastForward");
            tobeInserted.add(Jimple.v().newAssignStmt(
                    isFastForward,
                    Jimple.v().newStaticInvokeExpr(
                            Scene.v().makeMethodRef(
                                    Scene.v().getSootClass(UTIL_CLASS_NAME),
                                    "isFastForward",
                                    Collections.emptyList(),
                                    BooleanType.v(),
                                    true
                            )
                    )
            ));
            tobeInserted.add(Jimple.v().newIfStmt(
                    Jimple.v().newEqExpr(
                            isFastForward,
                            IntConstant.v(0)  // false
                    ),
                    skipRestoreLabel
            ));
            tobeInserted.addAll(restoreCode);
            tobeInserted.addAll(fieldRestoreCode);
            tobeInserted.add(skipRestoreLabel);

            goToPoints.put(method, gotoStmt);

            units.insertBefore(tobeInserted, divergePoint);
        }

        for (Map.Entry<SootMethod, Unit> entry: goToPoints.entrySet()) {
            SootMethod method = entry.getKey();
            Unit GotoPoint = entry.getValue();
            Body body = method.retrieveActiveBody();

            LocalGeneratorUtil lg = new LocalGeneratorUtil(body);
            Chain<Unit> units = body.getUnits();
            List<Unit> originalUnits = new ArrayList<>(body.getUnits());
            Local isFastForward = lg.generateLocalWithId(BooleanType.v(), "$isFastForward");

            List<Unit> tobeInserted = new ArrayList<>();
            tobeInserted.add(Jimple.v().newAssignStmt(
                isFastForward,
                Jimple.v().newStaticInvokeExpr(
                    Scene.v().makeMethodRef(
                        Scene.v().getSootClass(UTIL_CLASS_NAME),
                        "isFastForward",
                        Collections.emptyList(),
                        BooleanType.v(),
                        true
                    )
                )
            ));
            tobeInserted.add(Jimple.v().newIfStmt(
               Jimple.v().newEqExpr(
                            isFastForward,
                            IntConstant.v(1)
               ),
                GotoPoint
            ));
            Unit lastIdentitiyStmt = getLastIdentityStmt(body);
            assert lastIdentitiyStmt != null;
            units.insertAfter(tobeInserted, lastIdentitiyStmt);

        }
        insertTrace();

        Body body = shadowDivergePoint.getLeft().retrieveActiveBody();
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);
        Chain<Unit> units = body.getUnits();
        List<Unit> tobeInserted = new ArrayList<>();

        NopStmt skipLabel = Jimple.v().newNopStmt();

        Local isFastForward = lg.generateLocalWithId(BooleanType.v(), "$isFastForward");
        tobeInserted.add(
                Jimple.v().newAssignStmt(
                        isFastForward,
                        Jimple.v().newStaticInvokeExpr(
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass(UTIL_CLASS_NAME),
                                        "isFastForward",
                                        Collections.emptyList(),
                                        BooleanType.v(),
                                        true
                                )
                        )
                )
        );

        tobeInserted.add(
                Jimple.v().newIfStmt(
                        Jimple.v().newEqExpr(isFastForward, IntConstant.v(0)),
                        skipLabel
                )
        );

        tobeInserted.add(
                Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass(UTIL_CLASS_NAME),
                                        "clearBaggage",
                                        Collections.emptyList(),
                                        VoidType.v(),
                                        true
                                )
                        )
                )
        );

        tobeInserted.add(
                Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass(UTIL_CLASS_NAME),
                                        "createDryRunBaggage",
                                        Collections.emptyList(),
                                        RefType.v("io.opentelemetry.api.baggage.Baggage"),
                                        true
                                )
                        )
                )
        );

        tobeInserted.add(
                Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass(UTIL_CLASS_NAME),
                                        "createShadowBaggage",
                                        Collections.emptyList(),
                                        RefType.v("io.opentelemetry.api.baggage.Baggage"),
                                        true
                                )
                        )
                )
        );

        tobeInserted.add(skipLabel);


        units.insertBefore(tobeInserted, shadowDivergePoint.getRight());
        initializeLocalsForShadowThread();
    }

    public void insertTrace() {
        SootMethod runMethod = shadowSootClass.getMethodByName("run");
        Body body = runMethod.retrieveActiveBody();
        Chain<Unit> units = body.getUnits();
        Unit lastIdentityStmt = getLastIdentityStmt(body);

        // 创建条件判断变量
        Local conditionLocal = Jimple.v().newLocal("$isDryRun", BooleanType.v());
        body.getLocals().add(conditionLocal);

        List<Unit> tobeInserted = new ArrayList<>();

        // 添加 isDryRun() 调用
        tobeInserted.add(
                Jimple.v().newAssignStmt(
                        conditionLocal,
                        Jimple.v().newStaticInvokeExpr(
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass(UTIL_CLASS_NAME),
                                        "isDryRun",
                                        Collections.emptyList(),
                                        BooleanType.v(),
                                        true
                                )
                        )
                )
        );

        // 创建if语句，使用EqExpr进行比较
        NopStmt endIf = Jimple.v().newNopStmt();
        tobeInserted.add(
                Jimple.v().newIfStmt(
                        Jimple.v().newEqExpr(
                                conditionLocal,
                                IntConstant.v(1)  // true 对应 1
                        ),
                        endIf
                )
        );

        // createFastForwardBaggage调用
        tobeInserted.add(
                Jimple.v().newInvokeStmt(
                        Jimple.v().newStaticInvokeExpr(
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass(UTIL_CLASS_NAME),
                                        "createFastForwardBaggage",
                                        Collections.emptyList(),
                                        RefType.v("io.opentelemetry.api.baggage.Baggage"),
                                        true
                                ),
                                Collections.emptyList()
                        )
                )
        );

        tobeInserted.add(endIf);

        units.insertAfter(tobeInserted, lastIdentityStmt);
    }

    private FieldRef createFieldRef(SootField field, Local thisRef) {
        return field.isStatic()
                ? Jimple.v().newStaticFieldRef(field.makeRef())
                : Jimple.v().newInstanceFieldRef(thisRef, field.makeRef());
    }

    private void processField(SootField field, Local thisRef, Type type, List<Unit> code,
                              LocalGeneratorUtil lg, Local stateMap) {
        if(type instanceof PrimType) {
            if(type instanceof IntType) {
                Local tempValue = lg.generateLocalWithId(IntType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Integer"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Integer"),
                                        "intValue",
                                        Collections.emptyList(),
                                        IntType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof BooleanType) {
                Local tempValue = lg.generateLocalWithId(BooleanType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Boolean"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Boolean"),
                                        "booleanValue",
                                        Collections.emptyList(),
                                        BooleanType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof FloatType) {
                Local tempValue = lg.generateLocalWithId(FloatType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Float"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Float"),
                                        "floatValue",
                                        Collections.emptyList(),
                                        FloatType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof DoubleType) {
                Local tempValue = lg.generateLocalWithId(DoubleType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Double"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Double"),
                                        "doubleValue",
                                        Collections.emptyList(),
                                        DoubleType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof LongType) {
                Local tempValue = lg.generateLocalWithId(LongType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Long"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Long"),
                                        "longValue",
                                        Collections.emptyList(),
                                        LongType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof ByteType) {
                Local tempValue = lg.generateLocalWithId(ByteType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Byte"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Byte"),
                                        "byteValue",
                                        Collections.emptyList(),
                                        ByteType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof ShortType) {
                Local tempValue = lg.generateLocalWithId(ShortType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Short"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Short"),
                                        "shortValue",
                                        Collections.emptyList(),
                                        ShortType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            } else if(type instanceof CharType) {
                Local tempValue = lg.generateLocalWithId(CharType.v(), "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newVirtualInvokeExpr(
                                getWrappedValue(code, lg, stateMap, field, "java.lang.Character"),
                                Scene.v().makeMethodRef(
                                        Scene.v().getSootClass("java.lang.Character"),
                                        "charValue",
                                        Collections.emptyList(),
                                        CharType.v(),
                                        false
                                )
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            }
        } else {
            String typeName;
            if(type instanceof RefType) {
                typeName = ((RefType)type).getClassName();

                Local tempValue = lg.generateLocalWithId(type, "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        getWrappedValue(code, lg, stateMap, field, typeName)
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));

            } else if(type instanceof ArrayType) {
                typeName = "java.lang.Object";

                Local tempValue = lg.generateLocalWithId(type, "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        Jimple.v().newCastExpr(
                                getWrappedValue(code, lg, stateMap, field, typeName),
                                type
                        )
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));

            } else {
                // 其他特殊类型情况，使用 Object
                typeName = "java.lang.Object";

                Local tempValue = lg.generateLocalWithId(type, "$temp");
                code.add(Jimple.v().newAssignStmt(
                        tempValue,
                        getWrappedValue(code, lg, stateMap, field, typeName)
                ));
                code.add(Jimple.v().newAssignStmt(
                        createFieldRef(field, thisRef),
                        tempValue
                ));
            }
        }
    }

    private List<Unit> createFieldRestoreCode(Body body, SootMethod method) {
        List<Unit> code = new ArrayList<>();
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        Local stateMap = lg.generateLocalWithId(RefType.v("java.util.HashMap"), "$fieldState");
        code.add(Jimple.v().newAssignStmt(
                stateMap,
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass(UTIL_CLASS_NAME),
                                "getFieldState",
                                Arrays.asList(RefType.v("java.lang.String")),
                                RefType.v("java.util.HashMap"),
                                true
                        ),
                        Collections.singletonList(
                                StringConstant.v(body.getMethod().getSignature())
                        )
                )
        ));

        Local thisRef = null;
        boolean isStatic = body.getMethod().isStatic();

        if(!isStatic) {
            thisRef = body.getThisLocal();
        }
        Chain<SootField> fields = body.getMethod().getDeclaringClass().getFields();

        for(SootField field : fields) {
            Type type = field.getType();

            if(field.getName().contains(IS_FAST_FORWARD_BAGGAGE)){
                continue;
            }

            if(field.isStatic()){
                processField(field, null, type, code, lg, stateMap);
            } else {
                if(isStatic){
                    continue;
                }

                if(thisRef == null){
                    throw new RuntimeException("thisRef is null");
                }

                processField(field, thisRef, type, code, lg, stateMap);
            }
        }

        return code;
    }

    private List<Unit> createFieldRecordCode(Body body) {
        List<Unit> code = new ArrayList<>();
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        // 创建存储field的HashMap
        Local stateMap = lg.generateLocalWithId(RefType.v("java.util.HashMap"), "$fieldState");
        code.add(Jimple.v().newAssignStmt(
                stateMap,
                Jimple.v().newNewExpr(RefType.v("java.util.HashMap"))
        ));

        // 初始化HashMap
        code.add(Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(
                        stateMap,
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass("java.util.HashMap"),
                                "<init>",
                                Collections.emptyList(),
                                VoidType.v(),
                                false
                        )
                )
        ));


        Local thisRef = null;
        boolean isStatic = body.getMethod().isStatic();

        if(!isStatic) {
            thisRef = body.getThisLocal();
        }


        Chain<SootField> fields = body.getMethod().getDeclaringClass().getFields();

        for(SootField field : fields) {
            Type type = field.getType();
            Local wrappedValue = null;

            if(field.getName().equals("this")){
                continue;
            }

            Local fieldValue = lg.generateLocalWithId(type, "$fieldValue");


            if(field.isStatic()) {
                // 静态字段使用staticFieldRef
                code.add(Jimple.v().newAssignStmt(
                        fieldValue,
                        Jimple.v().newStaticFieldRef(field.makeRef())
                ));
            } else {
                // 实例字段使用instanceFieldRef
                if(thisRef == null){
                    continue;
                }
                code.add(Jimple.v().newAssignStmt(
                        fieldValue,
                        Jimple.v().newInstanceFieldRef(
                                thisRef,
                                field.makeRef()
                        )
                ));
            }

            if(type instanceof PrimType) {
                // 基本类型需要装箱
                if(type instanceof IntType) {

                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Integer"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Integer"),
                                            "valueOf",
                                            Arrays.asList(IntType.v()),
                                            RefType.v("java.lang.Integer"),
                                            true
                                    ),
                                    Collections.singletonList(fieldValue)
                            )
                    ));
                } else if (type instanceof BooleanType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Boolean"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Boolean"),
                                            "valueOf",
                                            Arrays.asList(BooleanType.v()),
                                            RefType.v("java.lang.Boolean"),
                                            true
                                    ),
                                    Collections.singletonList(fieldValue)
                            )
                    ));
                } else if (type instanceof FloatType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Float"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Float"),
                                            "valueOf",
                                            Arrays.asList(FloatType.v()),
                                            RefType.v("java.lang.Float"),
                                            true
                                    ),
                                    Collections.singletonList(fieldValue)
                            )
                    ));
                } else if (type instanceof DoubleType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Double"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Double"),
                                            "valueOf",
                                            Arrays.asList(DoubleType.v()),
                                            RefType.v("java.lang.Double"),
                                            true
                                    ),
                                    Collections.singletonList(fieldValue)
                            )
                    ));
                } else if (type instanceof LongType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Long"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Long"),
                                            "valueOf",
                                            Arrays.asList(LongType.v()),
                                            RefType.v("java.lang.Long"),
                                            true
                                    ),
                                    Collections.singletonList(fieldValue)
                            )
                    ));
                } else if (type instanceof ByteType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Byte"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Byte"),
                                            "valueOf",
                                            Arrays.asList(LongType.v()),
                                            RefType.v("java.lang.Byte"),
                                            true
                                    ),
                                    Collections.singletonList(fieldValue)
                            )
                    ));
                }

            } else {
                // 引用类型不需要装箱
                wrappedValue = fieldValue;
            }

            // 创建WrapContext并初始化
            Local wrapContextLocal = lg.generateLocalWithId(RefType.v(WRAP_CONTEXT_CLASS_NAME), "$wrapContext");
            code.add(Jimple.v().newAssignStmt(
                    wrapContextLocal,
                    Jimple.v().newNewExpr(RefType.v(WRAP_CONTEXT_CLASS_NAME))
            ));

            code.add(Jimple.v().newInvokeStmt(
                    Jimple.v().newSpecialInvokeExpr(
                            wrapContextLocal,
                            Scene.v().makeMethodRef(
                                    Scene.v().getSootClass(WRAP_CONTEXT_CLASS_NAME),
                                    "<init>",
                                    Arrays.asList(RefType.v("java.lang.Object")),
                                    VoidType.v(),
                                    false
                            ),
                            Arrays.asList(wrappedValue)
                    )
            ));

            // 存入HashMap
            code.add(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(
                            stateMap,
                            Scene.v().makeMethodRef(
                                    Scene.v().getSootClass("java.util.HashMap"),
                                    "put",
                                    Arrays.asList(RefType.v("java.lang.Object"), RefType.v("java.lang.Object")),
                                    RefType.v("java.lang.Object"),
                                    false
                            ),
                            Arrays.asList(
                                    StringConstant.v(field.getName()),
                                    wrapContextLocal
                            )
                    )
            ));
        }

        // 调用TraceUtil记录状态
        code.add(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass(UTIL_CLASS_NAME),
                                "recordFieldState",  // 新方法名,区分field状态记录
                                Arrays.asList(
                                        RefType.v("java.lang.String"),
                                        RefType.v("java.util.HashMap")
                                ),
                                VoidType.v(),
                                true
                        ),
                        Arrays.asList(
                                StringConstant.v(body.getMethod().getSignature()),
                                stateMap
                        )
                )
        ));

        return code;
    }
    private List<Unit> createStateRecordCode(Body body) {
        List<Unit> code = new ArrayList<>();
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);


        Local stateMap = lg.generateLocalWithId(RefType.v("java.util.HashMap"), "$state");


        code.add(Jimple.v().newAssignStmt(
                stateMap,
                Jimple.v().newNewExpr(RefType.v("java.util.HashMap"))
        ));

        code.add(Jimple.v().newInvokeStmt(
                Jimple.v().newSpecialInvokeExpr(
                        stateMap,
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass("java.util.HashMap"),
                                "<init>",
                                Collections.emptyList(),
                                VoidType.v(),
                                false
                        )
                )
        ));

        Set<Local> excludedLocals = getExcludedLocals(body, divergePoints, stateMap);

        List<Local> originalLocals = new ArrayList<>(body.getLocals());

        for(Local local : originalLocals) {

            Type type = local.getType();

            if( excludedLocals.contains(local)){
                continue;
            }


            Local wrappedValue = null;

            if(type instanceof PrimType){
                if(type instanceof IntType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Integer"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Integer"),
                                            "valueOf",
                                            Arrays.asList(IntType.v()),
                                            RefType.v("java.lang.Integer"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if (type instanceof BooleanType) {
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Boolean"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Boolean"),
                                            "valueOf",
                                            Arrays.asList(BooleanType.v()),
                                            RefType.v("java.lang.Boolean"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if (type instanceof FloatType){
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Float"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Float"),
                                            "valueOf",
                                            Arrays.asList(FloatType.v()),
                                            RefType.v("java.lang.Float"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if(type instanceof DoubleType){
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Double"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Double"),
                                            "valueOf",
                                            Arrays.asList(DoubleType.v()),
                                            RefType.v("java.lang.Double"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if(type instanceof LongType){
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Long"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Long"),
                                            "valueOf",
                                            Arrays.asList(LongType.v()),
                                            RefType.v("java.lang.Long"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if(type instanceof ByteType){
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Byte"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Byte"),
                                            "valueOf",
                                            Arrays.asList(ByteType.v()),
                                            RefType.v("java.lang.Byte"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if(type instanceof ShortType){
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Short"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Short"),
                                            "valueOf",
                                            Arrays.asList(ShortType.v()),
                                            RefType.v("java.lang.Short"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                } else if(type instanceof CharType){
                    wrappedValue = lg.generateLocalWithId(RefType.v("java.lang.Short"), "$wrapped");
                    code.add(Jimple.v().newAssignStmt(
                            wrappedValue,
                            Jimple.v().newStaticInvokeExpr(
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Short"),
                                            "valueOf",
                                            Arrays.asList(ShortType.v()),
                                            RefType.v("java.lang.Short"),
                                            true
                                    ),
                                    Collections.singletonList(local)
                            )
                    ));
                }
            } else {
                wrappedValue = local;
            }

            Local wrapContextLocal = lg.generateLocalWithId(RefType.v(WRAP_CONTEXT_CLASS_NAME), "$wrapContext");

            code.add(Jimple.v().newAssignStmt(
                    wrapContextLocal,
                    Jimple.v().newNewExpr(RefType.v(WRAP_CONTEXT_CLASS_NAME))
            ));

            code.add(Jimple.v().newInvokeStmt(
                    Jimple.v().newSpecialInvokeExpr(
                            wrapContextLocal,
                            Scene.v().makeMethodRef(
                                    Scene.v().getSootClass(WRAP_CONTEXT_CLASS_NAME),
                                    "<init>",
                                    Arrays.asList(RefType.v("java.lang.Object")),  // 构造函数参数类型
                                    VoidType.v(),
                                    false
                            ),
                            Arrays.asList(wrappedValue)  // 传入wrappedValue作为构造函数参数
                    )
            ));


            code.add(Jimple.v().newInvokeStmt(
                    Jimple.v().newVirtualInvokeExpr(
                            stateMap,
                            Scene.v().makeMethodRef(
                                    Scene.v().getSootClass("java.util.HashMap"),
                                    "put",
                                    Arrays.asList(
                                            RefType.v("java.lang.Object"),
                                            RefType.v("java.lang.Object")
                                    ),
                                    RefType.v("java.lang.Object"),
                                    false
                            ),
                            Arrays.asList(
                                    StringConstant.v(local.getName()),
                                    wrapContextLocal
                            )
                    )
            ));
        }

        LOG.info("TraceUtil.recordState is called");
        List<Unit> printLogsBefore = printLog("Recording state for method : " + body.getMethod().getSignature(),lg);
        code.addAll(printLogsBefore);
        code.add(Jimple.v().newInvokeStmt(
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass(UTIL_CLASS_NAME),
                                "recordState",
                                Arrays.asList(
                                        RefType.v("java.lang.String"),
                                        RefType.v("java.util.HashMap")
                                ),
                                VoidType.v(),
                                true
                        ),
                        Arrays.asList(
                                StringConstant.v(body.getMethod().getSignature()),
                                stateMap
                        )
                )
        ));
        List<Unit> printLogsAfter = printLog("Recorded state for method: " + body.getMethod().getSignature(),lg);
        code.addAll(printLogsAfter);

        body.validate();

        return code;
    }


    public Set<Local> getExcludedLocals(Body body, HashMap<SootMethod, Unit> divergePoints, Local stateMap){
        Set<Local> res = new HashSet<Local>();
        for(Unit unit:body.getUnits()){
            if(unit instanceof DefinitionStmt){
                DefinitionStmt defStmt = (DefinitionStmt) unit;
                if(defStmt.getLeftOp() instanceof Local && (((Local) defStmt.getLeftOp()).getName().equals(stateMap.getName()))){
                    res.add((Local) defStmt.getLeftOp());
                }
            }

            if(unit instanceof DefinitionStmt){
                DefinitionStmt defStmt = (DefinitionStmt) unit;
                if(defStmt.getLeftOp() instanceof Local && (defStmt.getRightOp() instanceof ThisRef)){
                    res.add((Local) defStmt.getLeftOp());
                }
            }
        }
        return res;
    }

    private List<Unit> createRestoreCode(Body body, SootMethod method){
        List<Unit> code = new ArrayList<>();
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        List<Local> originalLocals = new ArrayList<>(body.getLocals());

        Local stateMap = lg.generateLocalWithId(RefType.v("java.util.HashMap"), "$state");

        code.add(Jimple.v().newAssignStmt(
                stateMap,
                Jimple.v().newStaticInvokeExpr(
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass(UTIL_CLASS_NAME),
                                "getState",
                                Arrays.asList(RefType.v("java.lang.String")),
                                RefType.v("java.util.HashMap"),
                                true
                        ),
                        Collections.singletonList(
                                StringConstant.v(body.getMethod().getSignature())
                        )
                )
        ));

        Set<Local> excludedLocals = getExcludedLocals(body, divergePoints, stateMap);


        for(Local local: originalLocals){
            if(local instanceof ThisRef || excludedLocals.contains(local)){
                continue;
            }
            Type type = local.getType();

            if(type instanceof PrimType){
                if(type instanceof IntType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Integer"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Integer"),
                                            "intValue",
                                            Collections.emptyList(),
                                            IntType.v(),
                                            false
                                    )
                            )
                    ));
                }else if(type instanceof BooleanType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Boolean"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Boolean"),
                                            "booleanValue",
                                            Collections.emptyList(),
                                            BooleanType.v(),
                                            false
                                    )
                            )
                    ));
                }else if(type instanceof FloatType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Float"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Float"),
                                            "floatValue",
                                            Collections.emptyList(),
                                            FloatType.v(),
                                            false
                                    )
                            )
                    ));
                } else if(type instanceof DoubleType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Double"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Double"),
                                            "doubleValue",
                                            Collections.emptyList(),
                                            DoubleType.v(),
                                            false
                                    )
                            )
                    ));
                } else if(type instanceof LongType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Long"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Long"),
                                            "longValue",
                                            Collections.emptyList(),
                                            LongType.v(),
                                            false
                                    )
                            )
                    ));
                } else if(type instanceof ByteType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Byte"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Byte"),
                                            "byteValue",
                                            Collections.emptyList(),
                                            ByteType.v(),
                                            false
                                    )
                            )
                    ));
                } else if(type instanceof ShortType){
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Short"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Short"),
                                            "shortValue",
                                            Collections.emptyList(),
                                            ShortType.v(),
                                            false
                                    )
                            )
                    ));
                }else if(type instanceof CharType) {
                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newVirtualInvokeExpr(
                                    getWrappedValue(code, lg, stateMap, local, "java.lang.Character"),
                                    Scene.v().makeMethodRef(
                                            Scene.v().getSootClass("java.lang.Character"),
                                            "charValue",
                                            Collections.emptyList(),
                                            CharType.v(),
                                            false
                                    )
                            )
                    ));
                }
            } else {
                String typeName;
                if(type instanceof RefType) {
                    typeName = ((RefType)type).getClassName();
                } else if(type instanceof ArrayType) {
                    typeName = "java.lang.Object";

                    code.add(Jimple.v().newAssignStmt(
                            local,
                            Jimple.v().newCastExpr(
                                    getWrappedValue(code, lg, stateMap, local, typeName),
                                    type
                            )
                    ));
                    continue;
                } else {
                    // 其他特殊类型情况，暂时使用 Object
                    typeName = "java.lang.Object";
                }

                code.add(Jimple.v().newAssignStmt(
                        local,
                        getWrappedValue(code, lg, stateMap, local, typeName)
                ));
            }
        }

        return code;
    }



    private Local getWrappedValue(List<Unit> code, LocalGeneratorUtil lg, Local stateMap, SootField field, String wrapperClassName) {
        Local tempResult = lg.generateLocalWithId(RefType.v(WRAP_CONTEXT_CLASS_NAME), "temp");

        Local objectResult = lg.generateLocalWithId(RefType.v("java.lang.Object"), "$objectResult");

        VirtualInvokeExpr getExpr = Jimple.v().newVirtualInvokeExpr(
                stateMap,
                Scene.v().makeMethodRef(
                        Scene.v().getSootClass("java.util.HashMap"),
                        "get",
                        Arrays.asList(RefType.v("java.lang.Object")),
                        RefType.v("java.lang.Object"),
                        false
                ),
                Collections.singletonList(StringConstant.v(field.getName()))
        );

        code.add(Jimple.v().newAssignStmt(objectResult, getExpr));

        code.add(Jimple.v().newAssignStmt(
                tempResult,
                Jimple.v().newCastExpr(
                        objectResult,
                        RefType.v(WRAP_CONTEXT_CLASS_NAME)
                )
        ));


        Local wrappedValue = lg.generateLocalWithId(RefType.v(wrapperClassName), "$wrapped");
        Local objectValue = lg.generateLocalWithId(RefType.v("java.lang.Object"), "$objectValue");
        code.add(Jimple.v().newAssignStmt(
                objectValue,
                Jimple.v().newInstanceFieldRef(
                        tempResult,
                        Scene.v().makeFieldRef(
                                Scene.v().getSootClass(WRAP_CONTEXT_CLASS_NAME),
                                "value",
                                RefType.v("java.lang.Object"),
                                false
                        )
                )
        ));

        // 然后做类型转换
        code.add(Jimple.v().newAssignStmt(
                wrappedValue,
                Jimple.v().newCastExpr(
                        objectValue,
                        RefType.v(wrapperClassName)
                )
        ));
        return wrappedValue;
    }

    private Local getWrappedValue(List<Unit> code, LocalGeneratorUtil lg, Local stateMap, Local local, String wrapperClassName) {
        Local tempResult = lg.generateLocalWithId(RefType.v(WRAP_CONTEXT_CLASS_NAME), "temp");

        // 添加中间变量存储 HashMap.get 的 Object 结果
        Local objectResult = lg.generateLocalWithId(RefType.v("java.lang.Object"), "$objectResult");

        VirtualInvokeExpr getExpr = Jimple.v().newVirtualInvokeExpr(
                stateMap,
                Scene.v().makeMethodRef(
                        Scene.v().getSootClass("java.util.HashMap"),
                        "get",
                        Arrays.asList(RefType.v("java.lang.Object")),
                        RefType.v("java.lang.Object"),
                        false
                ),
                Collections.singletonList(StringConstant.v(local.getName()))
        );

        // 先把 HashMap.get 结果存为 Object
        code.add(Jimple.v().newAssignStmt(objectResult, getExpr));

        // 将 Object 转换为 WrapContext
        code.add(Jimple.v().newAssignStmt(
                tempResult,
                Jimple.v().newCastExpr(
                        objectResult,
                        RefType.v(WRAP_CONTEXT_CLASS_NAME)
                )
        ));

        Local wrappedValue = lg.generateLocalWithId(RefType.v(wrapperClassName), "$wrapped");
        Local objectValue = lg.generateLocalWithId(RefType.v("java.lang.Object"), "$objectValue");
        code.add(Jimple.v().newAssignStmt(
                objectValue,
                Jimple.v().newVirtualInvokeExpr(
                        tempResult,
                        Scene.v().makeMethodRef(
                                Scene.v().getSootClass(WRAP_CONTEXT_CLASS_NAME),
                                "getValue",
                                Collections.emptyList(),  // getValue 方法没有参数
                                RefType.v("java.lang.Object"),  // 返回类型是 Object
                                false
                        )
                )
        ));
        code.add(Jimple.v().newAssignStmt(
                wrappedValue,
                Jimple.v().newCastExpr(
                        objectValue,
                        RefType.v(wrapperClassName)
                )
        ));
        return wrappedValue;
    }

}