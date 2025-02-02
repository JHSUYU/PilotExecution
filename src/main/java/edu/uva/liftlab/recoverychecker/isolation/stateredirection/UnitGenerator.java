package edu.uva.liftlab.recoverychecker.isolation.stateredirection;

import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static edu.uva.liftlab.recoverychecker.util.Constants.STATE_ISOLATION_CLASS;
import static edu.uva.liftlab.recoverychecker.util.SootUtils.printLog4j;
import static edu.uva.liftlab.recoverychecker.util.SootUtils.printValue;

public class UnitGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(UnitGenerator.class);

    public List<Unit> generateUnits(FieldRef fieldRef, LocalGeneratorUtil lg,
                                    FieldInfo fieldInfo, SootMethod method, int id) {
        List<Unit> units = new ArrayList<>();

        Local originalValueLocal = lg.generateLocalWithId(fieldInfo.getType(),
                "tmp_" + fieldInfo.getOriginalFieldName());
        Local dryRunValueLocal = lg.generateLocalWithId(fieldInfo.getType(),
                "tmp_" + fieldInfo.getDryRunFieldName());
        Local isDefaultLocal = lg.generateLocalWithId(BooleanType.v(),
                "isDefault_" + fieldInfo.getOriginalFieldName());


        Value base = fieldRef instanceof InstanceFieldRef ?
                ((InstanceFieldRef) fieldRef).getBase() : null;
        FieldRef dryRunRef = fieldInfo.createDryRunFieldRef(base);
        FieldRef originalRef = fieldInfo.createOriginalFieldRef(base);
        FieldRef setByDryRunRef = fieldInfo.createSetByDryRunFieldRef(base);


        addBasicAssignments(units, originalValueLocal, dryRunValueLocal,
                isDefaultLocal, originalRef, dryRunRef, setByDryRunRef);


        List<Unit> logUnits = generateLogUnits(fieldInfo, method, lg, id);


        Stmt setByDryRunStmt = Jimple.v().newAssignStmt(setByDryRunRef, IntConstant.v(1));


        if (fieldInfo.isPrimitiveType()) {
            generatePrimitiveTypeUnits(units, originalValueLocal, dryRunValueLocal,
                    isDefaultLocal, dryRunRef, logUnits, setByDryRunStmt, lg,
                    fieldInfo, id);
        } else {
            generateReferenceTypeUnits(units, originalValueLocal, dryRunValueLocal,
                    isDefaultLocal, dryRunRef, logUnits, setByDryRunStmt, lg,
                    fieldInfo);
        }

        return units;
    }

    private void addBasicAssignments(List<Unit> units, Local originalValueLocal,
                                     Local dryRunValueLocal, Local isDefaultLocal,
                                     FieldRef originalRef, FieldRef dryRunRef,
                                     FieldRef setByDryRunRef) {
        units.add(Jimple.v().newAssignStmt(originalValueLocal, originalRef));
        units.add(Jimple.v().newAssignStmt(dryRunValueLocal, dryRunRef));
        units.add(Jimple.v().newAssignStmt(isDefaultLocal, setByDryRunRef));
    }

    private List<Unit> generateLogUnits(FieldInfo fieldInfo, SootMethod method,
                                        LocalGeneratorUtil lg, int id) {
        String message = String.format("%s with id %d in function %s in class %s is set to be 1",
                fieldInfo.getSetByDryRunFieldName(),
                id,
                method.getName(),
                fieldInfo.getDeclaringClass().getName());
        return printLog4j(message, lg);
    }

    private void generatePrimitiveTypeUnits(List<Unit> units, Local originalValueLocal,
                                            Local dryRunValueLocal, Local isDefaultLocal,
                                            FieldRef dryRunRef, List<Unit> logUnits,
                                            Stmt setByDryRunStmt, LocalGeneratorUtil lg,
                                            FieldInfo fieldInfo, int id) {
        Stmt nopStmt = Jimple.v().newNopStmt();
        Stmt copyStmt = Jimple.v().newAssignStmt(dryRunRef, originalValueLocal);

        // 生成条件跳转
        units.add(Jimple.v().newIfStmt(
                Jimple.v().newEqExpr(isDefaultLocal, IntConstant.v(1)),
                nopStmt));
        units.add(copyStmt);
        units.add(setByDryRunStmt);
        units.addAll(logUnits);
        units.add(nopStmt);

        // 添加值打印
        List<Unit> valueLogUnits = generateValueLogUnits(originalValueLocal, dryRunValueLocal,
                lg, fieldInfo, id);
        units.addAll(valueLogUnits);
    }

    private void generateReferenceTypeUnits(List<Unit> units, Local originalValueLocal,
                                            Local dryRunValueLocal, Local isDefaultLocal,
                                            FieldRef dryRunRef, List<Unit> logUnits,
                                            Stmt setByDryRunStmt, LocalGeneratorUtil lg,
                                            FieldInfo fieldInfo) {
        RefType objectType = RefType.v("java.lang.Object");

        // 获取深拷贝方法引用
        SootMethodRef shallowCopyMethod = Scene.v().makeMethodRef(
                Scene.v().getSootClass(STATE_ISOLATION_CLASS),
                "shallowCopy",
                Arrays.asList(objectType, objectType, BooleanType.v()),
                objectType,
                true);

        // 生成临时变量
        Local resultLocal = lg.generateLocal(fieldInfo.getType());
        Local castOriginal = lg.generateLocal(objectType);
        Local castDryRun = lg.generateLocal(objectType);

        // 添加类型转换语句
        units.add(Jimple.v().newAssignStmt(
                castOriginal,
                Jimple.v().newCastExpr(originalValueLocal, objectType)));
        units.add(Jimple.v().newAssignStmt(
                castDryRun,
                Jimple.v().newCastExpr(dryRunValueLocal, objectType)));

        // 调用拷贝方法
        Local tmpResult = lg.generateLocal(objectType);
        units.add(Jimple.v().newAssignStmt(
                tmpResult,
                Jimple.v().newStaticInvokeExpr(shallowCopyMethod,
                        castOriginal,
                        castDryRun,
                        isDefaultLocal)));

        // 转换回原始类型
        units.add(Jimple.v().newAssignStmt(
                resultLocal,
                Jimple.v().newCastExpr(tmpResult, fieldInfo.getType())));

        // 设置结果和标记
        units.add(Jimple.v().newAssignStmt(dryRunRef, resultLocal));
        units.add(setByDryRunStmt);
        units.addAll(logUnits);
    }

    private List<Unit> generateValueLogUnits(Local originalValueLocal, Local dryRunValueLocal,
                                             LocalGeneratorUtil lg, FieldInfo fieldInfo, int id) {
        List<Unit> units = new ArrayList<>();

        String originalMessage = String.format(" with id %d originalField is %s in class %s",
                id,
                fieldInfo.getOriginalFieldName(),
                fieldInfo.getDeclaringClass().getName());
        units.addAll(printValue(originalValueLocal, lg, originalMessage));

        String dryRunMessage = String.format(" with id %d dryRunField is %s in class %s",
                id,
                fieldInfo.getDryRunFieldName(),
                fieldInfo.getDeclaringClass().getName());
        units.addAll(printValue(dryRunValueLocal, lg, dryRunMessage));

        return units;
    }
}