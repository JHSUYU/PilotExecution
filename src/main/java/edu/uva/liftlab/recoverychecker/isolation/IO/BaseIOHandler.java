package edu.uva.liftlab.recoverychecker.isolation.IO;
import edu.uva.liftlab.recoverychecker.isolation.stateredirection.ClassFilterHelper;
import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.List;
public abstract class BaseIOHandler implements IOOperationHandler{
    protected void replaceStatement(IOContext context, Expr newExpr) {
        Unit unit = context.getUnit();
        UnitPatchingChain units = context.getUnits();
        Value leftOp = context.getLeftOp();

        Unit newUnit = leftOp != null
                ? Jimple.v().newAssignStmt(leftOp, newExpr)
                : Jimple.v().newInvokeStmt(newExpr);

        units.insertBefore(newUnit, unit);
        units.remove(unit);
    }

    protected SootMethodRef makeMethodRef(String className, String methodName,
                                          List<Type> paramTypes, Type returnType,
                                          boolean isStatic) {
        try {
            SootClass sootClass = Scene.v().getSootClass(className);
            // 检查类是否存在
            if (!sootClass.declaresMethod(methodName, paramTypes, returnType)) {
                //logger.error("Method {} not found in class {}", methodName, className);
                return null;
            }
            return Scene.v().makeMethodRef(
                    sootClass,
                    methodName,
                    paramTypes,
                    returnType,
                    isStatic
            );
        } catch (RuntimeException e) {
            return null;
        }
    }
}
