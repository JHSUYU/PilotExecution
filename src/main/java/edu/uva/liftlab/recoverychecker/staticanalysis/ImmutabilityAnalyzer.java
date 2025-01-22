package edu.uva.liftlab.recoverychecker.staticanalysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

import java.util.*;

public class ImmutabilityAnalyzer {
    private static final Logger LOG = LoggerFactory.getLogger(ImmutabilityAnalyzer.class);

//    public static void analyzeVariableForImmutability(SootMethod method){
//        if (!method.hasActiveBody()) return;
//
//        Body body = method.retrieveActiveBody();
//
//        Set<String> modifiedExternalVariables = new HashSet<>();
//
//        List<Local> parameterLocals = getParameterLocals(method);
//        LOG.info("Parameter locals: " + parameterLocals);
//        Set<Local> declaredLocals = new HashSet<>(body.getLocals());
//        LOG.info("Declared locals: " + declaredLocals);
//
//        for (Unit unit : body.getUnits()) {
//            if (unit instanceof DefinitionStmt) {
//                DefinitionStmt defStmt = (DefinitionStmt) unit;
//                Value lhs = defStmt.getLeftOp();
//
//                if (lhs instanceof Local) {
//                    Local localVar = (Local) lhs;
//
//                    if (!declaredLocals.contains(localVar) || parameterLocals.contains(localVar)) {
//                        modifiedExternalVariables.add(localVar.getName());
//                        LOG.info("Modified external variable: " + localVar.getName() + " in method " + method.getName());
//                    }
//                }
//                // 处理字段的赋值
//                else if (lhs instanceof FieldRef) {
//                    SootField field = ((FieldRef) lhs).getField();
//                    // 记录被修改的字段
//                    modifiedExternalVariables.add(field.getSignature());
//                    System.out.println("Modified external variable: " + field.getSignature() + " in method " + method.getName());
//                }
//                // 处理数组元素等其他情况（根据需要）
//                else {
//                    // 其他类型的左值处理（如果需要）
//                }
//            }
//        }
//    }
//
//    public static void analyzeMethodForImmutability(SootMethod method) {
//        if (!method.hasActiveBody()) {
//            method.retrieveActiveBody();
//        }
//        Map<SootField, Boolean> fieldWrittenMap = new HashMap<>();
//        Body body = method.getActiveBody();
//        for (Unit unit : body.getUnits()) {
//            Stmt stmt = (Stmt) unit;
//
//            if (stmt instanceof IdentityStmt) {
//                continue;
//            }
//
//            if (stmt instanceof AssignStmt) {
//                AssignStmt assignStmt = (AssignStmt) stmt;
//                Value lhs = assignStmt.getLeftOp();
//                if (lhs instanceof InstanceFieldRef) {
//                    InstanceFieldRef fieldRef = (InstanceFieldRef) lhs;
//                    SootField field = fieldRef.getField();
//                    if (fieldWrittenMap.containsKey(field)) {
//                        fieldWrittenMap.put(field, true);
//                    }
//                } else if (lhs instanceof StaticFieldRef) {
//                    StaticFieldRef fieldRef = (StaticFieldRef) lhs;
//                    SootField field = fieldRef.getField();
//                    if (fieldWrittenMap.containsKey(field)) {
//                        fieldWrittenMap.put(field, true);
//                    }
//                }
//            }else if (stmt.containsInvokeExpr()) {
//                // 可以进一步分析方法调用，检查是否修改了字段
//                InvokeExpr invokeExpr = stmt.getInvokeExpr();
//                SootMethod invokedMethod = invokeExpr.getMethod();
//                LOG.info("Invoked Method: " + invokedMethod.getSignature());
//
//                // 检查被调用方法是否可能修改字段
//                //analyzeMethodForFieldWrites(invokedMethod, fieldWrittenMap, new HashSet<>());
//            }
//        }
//
//        // 输出不可变的字段
//        LOG.info("Unchanged Field: ");
//        for (Map.Entry<SootField, Boolean> entry : fieldWrittenMap.entrySet()) {
//            SootField field = entry.getKey();
//            Boolean isWritten = entry.getValue();
//
//            if (!isWritten) {
//                LOG.info(field.getSignature());
//            }
//        }
//
//    }
//
//    private static void analyzeMethodForFieldWrites(SootMethod method, Map<SootField, Boolean> fieldWrittenMap, Set<SootMethod> visitedMethods) {
//        if (!method.isConcrete()) return;
//        if (visitedMethods.contains(method)) return; // 防止递归循环
//        visitedMethods.add(method);
//
//        Body body = method.retrieveActiveBody();
//        for (Unit unit : body.getUnits()) {
//            Stmt stmt = (Stmt) unit;
//
//            if (stmt instanceof AssignStmt) {
//                AssignStmt assignStmt = (AssignStmt) stmt;
//                Value lhs = assignStmt.getLeftOp();
//
//                // 检查字段写入
//                if (lhs instanceof InstanceFieldRef) {
//                    InstanceFieldRef fieldRef = (InstanceFieldRef) lhs;
//                    SootField field = fieldRef.getField();
//
//                    if (fieldWrittenMap.containsKey(field)) {
//                        fieldWrittenMap.put(field, true);
//                    }
//                } else if (lhs instanceof StaticFieldRef) {
//                    StaticFieldRef fieldRef = (StaticFieldRef) lhs;
//                    SootField field = fieldRef.getField();
//
//                    if (fieldWrittenMap.containsKey(field)) {
//                        fieldWrittenMap.put(field, true);
//                    }
//                }
//            }
//            // 递归分析内部方法调用
//            if (stmt.containsInvokeExpr()) {
//                InvokeExpr invokeExpr = stmt.getInvokeExpr();
//                SootMethod invokedMethod = invokeExpr.getMethod();
//
//                analyzeMethodForFieldWrites(invokedMethod, fieldWrittenMap, visitedMethods);
//            }
//        }
//    }
}

