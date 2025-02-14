package edu.uva.liftlab.recoverychecker.isolation.IO;

import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import soot.*;
import soot.jimple.*;

public class IOIsolationProcessor {
    private static final String FILE_CHANNEL_CLASS = "java.nio.channels.FileChannel";
    private static final String SHADOW_FILE_CHANNEL = "org.pilot.filesystem.ShadowFileChannel";

    public void handleIOOperation(Unit unit, UnitPatchingChain units,
                                  LocalGeneratorUtil lg, SootMethod method) {
        InvokeExpr invokeExpr = null;

        if (unit instanceof InvokeStmt) {
            invokeExpr = ((InvokeStmt) unit).getInvokeExpr();
        } else if (unit instanceof AssignStmt) {
            Value rightOp = ((AssignStmt) unit).getRightOp();
            if (rightOp instanceof InvokeExpr) {
                invokeExpr = (InvokeExpr) rightOp;
            }
        }

        if (invokeExpr != null) {
            redirectFileChannelOpen(unit, units, invokeExpr, lg);
        }
    }

    private void redirectFileChannelOpen(Unit unit, UnitPatchingChain units,
                                         InvokeExpr invokeExpr, LocalGeneratorUtil lg) {
        if (isFileChannelOpen(invokeExpr)) {
            // 替换为 ShadowFileChannel.open 调用
            SootMethodRef shadowOpen = Scene.v().makeMethodRef(
                    Scene.v().getSootClass(SHADOW_FILE_CHANNEL),
                    "open",
                    invokeExpr.getMethod().getParameterTypes(),
                    invokeExpr.getMethod().getReturnType(),
                    true
            );

            StaticInvokeExpr newInvokeExpr = Jimple.v().newStaticInvokeExpr(
                    shadowOpen,
                    invokeExpr.getArgs()
            );

            if (unit instanceof AssignStmt) {
                units.insertBefore(
                        Jimple.v().newAssignStmt(
                                ((AssignStmt) unit).getLeftOp(),
                                newInvokeExpr
                        ),
                        unit
                );
            } else {
                units.insertBefore(
                        Jimple.v().newInvokeStmt(newInvokeExpr),
                        unit
                );
            }
            units.remove(unit);
        }
    }

    private boolean isFileChannelOpen(InvokeExpr expr) {
        return expr.getMethod().getDeclaringClass().getName().equals(FILE_CHANNEL_CLASS)
                && expr.getMethod().getName().equals("open");
    }
}
