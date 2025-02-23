package edu.uva.liftlab.recoverychecker.isolation.IO;
import edu.uva.liftlab.recoverychecker.isolation.stateredirection.ClassFilterHelper;
import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.List;
class BufferedWriterHandler extends BaseIOHandler {
    private static final String FILES_CLASS = "java.nio.file.Files";
    private static final String SHADOW_FILE_OUTPUT_STREAM =
            "org.pilot.filesystem.ShadowFileOutputStream";

    @Override
    public boolean handle(IOContext context) {
        InvokeExpr expr = context.getInvokeExpr();
        if (expr == null || !isFilesNewBufferedWriter(expr)) {
            return false;
        }

        SootMethodRef initMethod = makeMethodRef(
                SHADOW_FILE_OUTPUT_STREAM,
                "initShadowBufferedWriter",
                expr.getMethod().getParameterTypes(),
                expr.getMethod().getReturnType(),
                true  // 改为静态方法
        );

        StaticInvokeExpr newExpr = Jimple.v().newStaticInvokeExpr(
                initMethod,
                expr.getArgs()
        );

        replaceStatement(context, newExpr);
        return true;
    }

    private boolean isFilesNewBufferedWriter(InvokeExpr expr) {
        return expr.getMethod().getDeclaringClass().getName().equals(FILES_CLASS)
                && expr.getMethod().getName().equals("newBufferedWriter");
    }
}
