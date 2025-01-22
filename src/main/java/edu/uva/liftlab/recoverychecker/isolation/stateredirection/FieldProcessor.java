package edu.uva.liftlab.recoverychecker.isolation.stateredirection;

import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;
import java.util.ArrayList;
import java.util.List;

import static edu.uva.liftlab.recoverychecker.util.Constants.DRY_RUN_SUFFIX;

public class FieldProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(FieldProcessor.class);
    private final FieldRedirector fieldRedirector;
    private final UnitGenerator unitGenerator;
    private int id = 0;

    public FieldProcessor() {
        this.fieldRedirector = new FieldRedirector();
        this.unitGenerator = new UnitGenerator();
    }

    public void redirectFieldAccesses(Body body, SootMethod method) {
        UnitPatchingChain units = body.getUnits();
        List<Unit> originalUnits = new ArrayList<>(units);
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        for (Unit u : originalUnits) {
            processAssignStmt(u, units, lg, method);
            processInvokeStmt(u, units, lg, method);
        }
    }

    private void processAssignStmt(Unit u, UnitPatchingChain units,
                                   LocalGeneratorUtil lg, SootMethod method) {
        if (!(u instanceof AssignStmt)) return;

        AssignStmt stmt = (AssignStmt) u;
        Value rightOp = stmt.getRightOp();
        Value leftOp = stmt.getLeftOp();

        // 处理右操作数的字段引用
        if (rightOp instanceof FieldRef) {
            handleFieldRef((FieldRef) rightOp, stmt, true, units, u, lg, method);
        }

        // 处理左操作数的字段引用
        if (leftOp instanceof FieldRef) {
            handleFieldRef((FieldRef) leftOp, stmt, false, units, u, lg, method);
        }
    }

    private void processInvokeStmt(Unit u, UnitPatchingChain units,
                                   LocalGeneratorUtil lg, SootMethod method) {
        InvokeExpr invoke = getInvokeExpr(u);
        if (invoke == null || !(invoke instanceof InstanceInvokeExpr)) {
            return;
        }

        Value base = ((InstanceInvokeExpr) invoke).getBase();
        if (base instanceof FieldRef) {
            handleFieldRef((FieldRef) base, null, true, units, u, lg, method);
        }
    }

    private InvokeExpr getInvokeExpr(Unit u) {
        if (u instanceof InvokeStmt) {
            return ((InvokeStmt) u).getInvokeExpr();
        }
        if (u instanceof AssignStmt && ((AssignStmt)u).getRightOp() instanceof InvokeExpr) {
            return (InvokeExpr) ((AssignStmt)u).getRightOp();
        }
        return null;
    }

    private void handleFieldRef(FieldRef fieldRef, AssignStmt stmt, boolean isRightOp,
                                UnitPatchingChain units, Unit u, LocalGeneratorUtil lg,
                                SootMethod method) {
        FieldRef dryRunRef = fieldRedirector.getDryRunFieldRef(fieldRef);
        if (dryRunRef == null) {
            return;
        }

        List<Unit> newUnits = generateFieldAccessUnits(fieldRef, lg, method);
        if (newUnits.isEmpty()) {
            return;
        }

        // 插入新生成的单元
        units.insertBefore(newUnits, u);

        // 更新语句中的字段引用
        if (stmt != null) {
            if (isRightOp) {
                stmt.setRightOp(dryRunRef);
            } else {
                stmt.setLeftOp(dryRunRef);
            }
        } else if (u instanceof InvokeStmt &&
                ((InvokeStmt)u).getInvokeExpr() instanceof InstanceInvokeExpr) {
            ((InstanceInvokeExpr)((InvokeStmt)u).getInvokeExpr()).setBase(dryRunRef);
        }
    }

    private List<Unit> generateFieldAccessUnits(FieldRef fieldRef,
                                                LocalGeneratorUtil lg,
                                                SootMethod method) {
        id++;
        FieldInfo fieldInfo = new FieldInfo(fieldRef);
        if (!fieldInfo.isValid()) {
            return new ArrayList<>();
        }

        return unitGenerator.generateUnits(fieldRef, lg, fieldInfo, method, id);
    }

    // 辅助方法：检查是否需要处理该字段
    private boolean shouldProcessField(SootField field) {
        return !field.getName().endsWith(DRY_RUN_SUFFIX) &&
                !field.getName().contains("assertionsDisabled");
    }

    // 辅助方法：获取字段的默认值
    private Value getDefaultValue(Type type) {
        if (type instanceof RefType) {
            return NullConstant.v();
        } else if (type instanceof IntType || type instanceof ByteType
                || type instanceof ShortType || type instanceof CharType) {
            return IntConstant.v(0);
        } else if (type instanceof LongType) {
            return LongConstant.v(0L);
        } else if (type instanceof FloatType) {
            return FloatConstant.v(0.0f);
        } else if (type instanceof DoubleType) {
            return DoubleConstant.v(0.0d);
        } else if (type instanceof BooleanType) {
            return IntConstant.v(0);
        }
        return NullConstant.v();
    }

    // 日志相关方法
    private void logFieldProcessing(SootField field, String operation) {
        LOG.debug("Processing field {} with operation: {}",
                field.getName(), operation);
    }

    private void logError(String message, Exception e) {
        LOG.error(message, e);
    }
}