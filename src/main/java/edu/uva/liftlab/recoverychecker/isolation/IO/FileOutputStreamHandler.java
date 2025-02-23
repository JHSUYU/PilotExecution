package edu.uva.liftlab.recoverychecker.isolation.IO;
import edu.uva.liftlab.recoverychecker.isolation.stateredirection.ClassFilterHelper;
import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.List;
class FileOutputStreamHandler extends BaseIOHandler {
    private static final String FILE_OUTPUT_STREAM_CLASS = "java.io.FileOutputStream";
    private static final String SHADOW_FILE_OUTPUT_STREAM =
            "org.pilot.filesystem.ShadowFileOutputStream";

    @Override
    public boolean handle(IOContext context) {
        NewExpr expr = context.getNewExpr();
        if (expr == null || !isFileOutputStreamConstructor(expr)) {
            return false;
        }


        // 获取构造函数调用信息
        SpecialInvokeExpr constructorInvoke = context.getConstructorInvoke();
        System.out.println("FileOutputstream constructorInvoke = " + constructorInvoke);
        if (constructorInvoke == null) {
            return false;
        }

        // 创建静态方法调用
        SootMethodRef initMethod = makeMethodRef(
                SHADOW_FILE_OUTPUT_STREAM,
                "initShadowFileOutputStream",
                constructorInvoke.getMethod().getParameterTypes(),
                Scene.v().getSootClass(FILE_OUTPUT_STREAM_CLASS).getType(),
                true  // 改为静态方法
        );
        if (initMethod == null) {
            return false;
        }

        // 创建静态初始化调用
        StaticInvokeExpr initExpr = Jimple.v().newStaticInvokeExpr(
                initMethod,
                constructorInvoke.getArgs()
        );

        // 创建赋值语句，将结果赋给原始的左值
        Unit newUnit = Jimple.v().newAssignStmt(
                ((AssignStmt) context.getUnit()).getLeftOp(),
                initExpr
        );

        context.getUnits().insertAfter(newUnit, context.getConstructorUnit());
        return true;
    }

    private boolean isFileOutputStreamConstructor(NewExpr expr) {
        return expr.getBaseType().toString().contains(FILE_OUTPUT_STREAM_CLASS);
    }
}
