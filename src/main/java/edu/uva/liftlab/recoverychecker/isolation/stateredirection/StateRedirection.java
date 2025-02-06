package edu.uva.liftlab.recoverychecker.isolation.stateredirection;

import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.List;

import static edu.uva.liftlab.recoverychecker.util.Constants.INSTRUMENTATION_SUFFIX;

public class StateRedirection {
    private static final Logger LOG = LoggerFactory.getLogger(StateRedirection.class);

    private final SootClass sootClass;
    private final FieldAccessProcessor fieldAccessProcessor;
    private SootMethod currentMethod;

    public StateRedirection(SootClass sootClass) {
        this.sootClass = sootClass;
        this.fieldAccessProcessor = new FieldAccessProcessor();
    }

    /**
     * 重定向所有类的状态
     */
    public static void redirectAllClassesStates(ClassFilterHelper filter) {
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            if (filter.shouldSkip(sc)) {
                continue;
            }
            LOG.info("Processing class: {}", sc.getName());
            new StateRedirection(sc).redirectStatesInFunc$instrumentation();
        }
    }

    /**
     * 重定向当前类中的状态
     */
    public void redirectStatesInFunc$instrumentation() {
        LOG.info("Processing class method instrumentation: {}", sootClass.getName());

        for (SootMethod method : sootClass.getMethods()) {
            if (!shouldInstrumentMethod(method)) {
                continue;
            }
            currentMethod = method;
            redirectMethod(method);
        }
    }

    /**
     * 重定向单个方法
     */
    private void redirectMethod(SootMethod method) {
        try {
            if (!method.hasActiveBody()) {
                LOG.debug("Method {} has no active body, skipping", method.getName());
                return;
            }

            Body originalBody = method.retrieveActiveBody();
            Body instrumentationBody = Jimple.v().newBody(method);
            instrumentationBody.importBodyContentsFrom(originalBody);

            redirectFieldAccesses(instrumentationBody);

            instrumentationBody.validate();
            method.setActiveBody(instrumentationBody);

            LOG.debug("Successfully redirected method: {}", method.getName());
        } catch (Exception e) {
            LOG.error("Failed to redirect method {}: {}", method.getName(), e.getMessage());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Stack trace:", e);
            }
        }
    }

    private void redirectFieldAccesses(Body body) {
        UnitPatchingChain units = body.getUnits();
        List<Unit> originalUnits = new ArrayList<>(units);
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        for (Unit unit : originalUnits) {
            if (unit instanceof AssignStmt) {
                fieldAccessProcessor.handleAssignStmt((AssignStmt) unit, units, lg, currentMethod);
            }
            if (unit instanceof InvokeStmt ||
                    (unit instanceof AssignStmt && ((AssignStmt)unit).getRightOp() instanceof InvokeExpr)) {
                fieldAccessProcessor.handleInvokeStmt(unit, units, lg, currentMethod);
            }
        }
    }

    private boolean shouldInstrumentMethod(SootMethod method) {
        if (method.isAbstract() || method.isNative()) {
            LOG.debug("Skipping abstract or native method: {}", method.getName());
            return false;
        }

        if (!method.getName().endsWith(INSTRUMENTATION_SUFFIX)) {
            LOG.debug("Skipping already instrumented method: {}", method.getName());
            return false;
        }

        if (!method.hasActiveBody()) {
            LOG.debug("Skipping method without active body: {}", method.getName());
            return false;
        }

        return true;
    }

    public SootClass getSootClass() {
        return sootClass;
    }

    public SootMethod getCurrentMethod() {
        return currentMethod;
    }

    /**
     * 用于测试的方法
     */
    protected FieldAccessProcessor getFieldAccessProcessor() {
        return fieldAccessProcessor;
    }

}