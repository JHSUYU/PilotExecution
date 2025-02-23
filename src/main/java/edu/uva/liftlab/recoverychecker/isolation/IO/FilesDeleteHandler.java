package edu.uva.liftlab.recoverychecker.isolation.IO;
import edu.uva.liftlab.recoverychecker.isolation.stateredirection.ClassFilterHelper;
import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.List;
class FilesDeleteHandler extends BaseIOHandler {
    private static final String FILES_CLASS = "java.nio.file.Files";
    private static final String SHADOW_FILES = "org.pilot.filesystem.ShadowFiles";

    @Override
    public boolean handle(IOContext context) {
        InvokeExpr expr = context.getInvokeExpr();
        if (expr == null || !isFilesDelete(expr)) {
            return false;
        }

        SootMethodRef deleteMethod = makeMethodRef(
                SHADOW_FILES,
                "delete",
                expr.getMethod().getParameterTypes(),
                expr.getMethod().getReturnType(),
                true
        );

        StaticInvokeExpr newExpr = Jimple.v().newStaticInvokeExpr(
                deleteMethod,
                expr.getArgs()
        );

        replaceStatement(context, newExpr);
        return true;
    }

    private boolean isFilesDelete(InvokeExpr expr) {
        return expr.getMethod().getDeclaringClass().getName().equals(FILES_CLASS)
                && expr.getMethod().getName().equals("delete");
    }
}