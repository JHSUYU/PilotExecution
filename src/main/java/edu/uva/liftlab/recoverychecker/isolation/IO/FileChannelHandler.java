package edu.uva.liftlab.recoverychecker.isolation.IO;

import soot.SootMethodRef;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.StaticInvokeExpr;

class FileChannelHandler extends BaseIOHandler {
    private static final String FILE_CHANNEL_CLASS = "java.nio.channels.FileChannel";
    private static final String SHADOW_FILE_CHANNEL = "org.pilot.filesystem.ShadowFileChannel";

    @Override
    public boolean handle(IOContext context) {
        InvokeExpr expr = context.getInvokeExpr();
        if (expr == null || !isFileChannelOpen(expr)) {
            return false;
        }

        SootMethodRef shadowOpen = makeMethodRef(
                SHADOW_FILE_CHANNEL,
                "open",
                expr.getMethod().getParameterTypes(),
                expr.getMethod().getReturnType(),
                true
        );

        StaticInvokeExpr newExpr = Jimple.v().newStaticInvokeExpr(
                shadowOpen,
                expr.getArgs()
        );

        replaceStatement(context, newExpr);
        return true;
    }

    private boolean isFileChannelOpen(InvokeExpr expr) {
        return expr.getMethod().getDeclaringClass().getName().equals(FILE_CHANNEL_CLASS)
                && expr.getMethod().getName().equals("open");
    }
}

