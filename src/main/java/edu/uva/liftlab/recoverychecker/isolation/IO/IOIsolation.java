package edu.uva.liftlab.recoverychecker.isolation.IO;

import edu.uva.liftlab.recoverychecker.isolation.stateredirection.ClassFilterHelper;
import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;

import java.util.ArrayList;
import java.util.List;

public class IOIsolation {
    private static final Logger LOG = LoggerFactory.getLogger(IOIsolation.class);

    private final SootClass sootClass;
    private final IOIsolationProcessor ioProcessor;
    private SootMethod currentMethod;

    public IOIsolation(SootClass sootClass) {
        this.sootClass = sootClass;
        this.ioProcessor = new IOIsolationProcessor();
    }

    public static void redirectAllClassesIO(ClassFilterHelper filter) {
        for (SootClass sc : Scene.v().getApplicationClasses()) {
//            if (filter.shouldSkip(sc)) {
//                continue;
//            }
            LOG.info("Processing class for IO isolation: {}", sc.getName());
            new IOIsolation(sc).redirectIO();
        }
    }

    private void redirectIO() {
        for (SootMethod method : sootClass.getMethods()) {
            if (!shouldInstrumentMethod(method)) {
                continue;
            }
            currentMethod = method;
            redirectMethodIO(method);
        }
    }

    private void redirectMethodIO(SootMethod method) {
        try {
            if (!method.hasActiveBody()) {
                return;
            }

            Body originalBody = method.retrieveActiveBody();
            Body instrumentationBody = Jimple.v().newBody(method);
            instrumentationBody.importBodyContentsFrom(originalBody);

            redirectIOOperations(instrumentationBody);

            instrumentationBody.validate();
            method.setActiveBody(instrumentationBody);

            LOG.debug("Successfully redirected IO in method: {}", method.getName());
        } catch (Exception e) {
            LOG.error("Failed to redirect IO in method {}: {}", method.getName(), e.getMessage());
        }
    }

    private void redirectIOOperations(Body body) {
        UnitPatchingChain units = body.getUnits();
        List<Unit> originalUnits = new ArrayList<>(units);
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        for (Unit unit : originalUnits) {
            if (unit instanceof InvokeStmt ||
                    (unit instanceof AssignStmt && ((AssignStmt)unit).getRightOp() instanceof InvokeExpr)) {
                ioProcessor.handleIOOperation(unit, units, lg, currentMethod);
            }
        }
    }

    private boolean shouldInstrumentMethod(SootMethod method) {
        return !(method.isAbstract() || method.isNative()) && method.hasActiveBody();
    }
}
