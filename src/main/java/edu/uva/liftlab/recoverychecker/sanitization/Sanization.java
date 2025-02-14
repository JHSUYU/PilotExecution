package edu.uva.liftlab.recoverychecker.sanitization;

import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.List;

import static edu.uva.liftlab.recoverychecker.util.Constants.INSTRUMENTATION_SUFFIX;

public class Sanization {
    private static final Logger LOG = LoggerFactory.getLogger(Sanization.class);
    private static final String SYSTEM_CLASS = "java.lang.System";
    private static final String DEV_API_CLASS = "org.pilot.dev.DevApi";

    private final SootClass sootClass;
    private SootMethod currentMethod;

    public Sanization(SootClass sootClass) {
        this.sootClass = sootClass;
    }

    public static void sanitizeAllClasses() {
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            LOG.info("Processing class for sanization: {}", sc.getName());
            new Sanization(sc).sanitize();
        }
    }

    private void sanitize() {
        for (SootMethod method : sootClass.getMethods()) {
            if (!shouldInstrumentMethod(method)) {
                continue;
            }
            currentMethod = method;
            sanitizeMethod(method);
        }
    }

    private void sanitizeMethod(SootMethod method) {
        try {
            if (!method.hasActiveBody()) {
                return;
            }

            Body originalBody = method.retrieveActiveBody();
            Body instrumentationBody = Jimple.v().newBody(method);
            instrumentationBody.importBodyContentsFrom(originalBody);

            sanitizeDangerousCalls(instrumentationBody);

            instrumentationBody.validate();
            method.setActiveBody(instrumentationBody);

            LOG.debug("Successfully sanitized method: {}", method.getName());
        } catch (Exception e) {
            LOG.error("Failed to sanitize method {}: {}", method.getName(), e.getMessage());
        }
    }

    private void sanitizeDangerousCalls(Body body) {
        UnitPatchingChain units = body.getUnits();
        List<Unit> originalUnits = new ArrayList<>(units);
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        for (Unit unit : originalUnits) {
            if (unit instanceof InvokeStmt) {
                InvokeExpr expr = ((InvokeStmt) unit).getInvokeExpr();
                if (isDangerousCall(expr)) {
                    replaceDangerousCall(unit, units, expr);
                }
            }
        }
    }

    private boolean isDangerousCall(InvokeExpr expr) {
        SootMethod method = expr.getMethod();
        return method.getDeclaringClass().getName().equals(SYSTEM_CLASS)
                && method.getName().equals("exit");
    }

    private void replaceDangerousCall(Unit unit, UnitPatchingChain units, InvokeExpr expr) {
        // 创建对 DevApi.printError() 的调用
        SootMethodRef safeMethod = Scene.v().makeMethodRef(
                Scene.v().getSootClass(DEV_API_CLASS),
                "printError",
                new ArrayList<>(),
                VoidType.v(),
                true
        );

        StaticInvokeExpr newInvokeExpr = Jimple.v().newStaticInvokeExpr(safeMethod);

        // 替换原有的危险调用
        units.insertBefore(
                Jimple.v().newInvokeStmt(newInvokeExpr),
                unit
        );
        units.remove(unit);
    }

    private boolean shouldInstrumentMethod(SootMethod method) {
        return method.getName().endsWith(INSTRUMENTATION_SUFFIX);

    }
}
