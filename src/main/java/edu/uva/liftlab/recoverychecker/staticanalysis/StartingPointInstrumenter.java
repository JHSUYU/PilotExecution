package edu.uva.liftlab.recoverychecker.staticanalysis;

import edu.uva.liftlab.recoverychecker.util.LocalGeneratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static edu.uva.liftlab.recoverychecker.util.SootUtils.getFirstNonIdentityStmt;
import static edu.uva.liftlab.recoverychecker.util.SootUtils.getLastIdentityStmt;

public class StartingPointInstrumenter {
    private static final Logger LOG = LoggerFactory.getLogger(StartingPointInstrumenter.class);

    private List<String> startingPoints;

    public StartingPointInstrumenter(Set<String> startingPoints) {
        this.startingPoints = new ArrayList<>(startingPoints);
    }

    public SootMethod instrumentStartingPoint() {
        String targetMethodSignature = startingPoints.get(0);
        String pilotMethodSignature = startingPoints.get(1);
        LOG.info("Starting point method is: {}", targetMethodSignature);

        SootMethod method = Scene.v().getMethod(targetMethodSignature);
        SootMethod pilotMethod = Scene.v().getMethod(pilotMethodSignature);

        Body body = method.getActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        LocalGeneratorUtil lg = new LocalGeneratorUtil(body);

        Local propertyLocal = lg.generateLocal(RefType.v("java.lang.String"));

        // 调用System.getProperty("PilotMode")
        StaticInvokeExpr getPropertyExpr = Jimple.v().newStaticInvokeExpr(
                Scene.v().makeMethodRef(
                        Scene.v().loadClassAndSupport("java.lang.System"),
                        "getProperty",
                        Collections.singletonList(RefType.v("java.lang.String")),
                        RefType.v("java.lang.String"),
                        true
                ),
                StringConstant.v("PilotMode")
        );

        // 添加调用pilot方法的语句
        InvokeExpr invokePilot;
        if (method.isStatic()) {
            invokePilot = Jimple.v().newStaticInvokeExpr(
                    pilotMethod.makeRef(),
                    body.getParameterLocals()
            );
        } else {
            invokePilot = Jimple.v().newSpecialInvokeExpr(
                    body.getThisLocal(),
                    pilotMethod.makeRef(),
                    body.getParameterLocals()
            );
        }

        Unit lastIdentityStmt = getLastIdentityStmt(body);
        Unit firstNonIdentityStmt = getFirstNonIdentityStmt(body);
        GotoStmt gotoOriginal = Jimple.v().newGotoStmt(firstNonIdentityStmt);
        assert lastIdentityStmt != null;

        Unit getPropertyStmt = Jimple.v().newAssignStmt(propertyLocal, getPropertyExpr);

        List<Unit> toBeInserted = new ArrayList<>();


        VirtualInvokeExpr equalsExpr = Jimple.v().newVirtualInvokeExpr(
                propertyLocal,
                Scene.v().makeMethodRef(
                        Scene.v().loadClassAndSupport("java.lang.String"),
                        "equals",
                        Collections.singletonList(RefType.v("java.lang.Object")),
                        BooleanType.v(),
                        false
                ),
                StringConstant.v("enabled")
        );

        if (!(method.getReturnType() instanceof VoidType)) {
            Local resultLocal = lg.generateLocal(method.getReturnType());
            Unit assignResult = Jimple.v().newAssignStmt(resultLocal, invokePilot);
            Unit returnStmt = Jimple.v().newReturnStmt(resultLocal);

            Local isEnabledLocal = lg.generateLocal(BooleanType.v());
            Unit assignTmp = Jimple.v().newAssignStmt(isEnabledLocal, equalsExpr);
            EqExpr condition = Jimple.v().newEqExpr(isEnabledLocal, IntConstant.v(0));
            IfStmt ifStmt = Jimple.v().newIfStmt(condition, gotoOriginal);

            toBeInserted.add(getPropertyStmt);
            toBeInserted.add(assignTmp);
            toBeInserted.add(ifStmt);
            toBeInserted.add(assignResult);
            toBeInserted.add(returnStmt);
            toBeInserted.add(gotoOriginal);
        } else {
            Unit invokeStmt = Jimple.v().newInvokeStmt(invokePilot);
            Unit returnVoidStmt = Jimple.v().newReturnVoidStmt();

            Local isEnabledLocal = lg.generateLocal(BooleanType.v());
            Unit assignTmp = Jimple.v().newAssignStmt(isEnabledLocal, equalsExpr);
            EqExpr condition = Jimple.v().newEqExpr(isEnabledLocal, IntConstant.v(0));
            IfStmt ifStmt = Jimple.v().newIfStmt(condition, assignTmp);

            toBeInserted.add(getPropertyStmt);
            toBeInserted.add(assignTmp);
            toBeInserted.add(ifStmt);
            toBeInserted.add(invokeStmt);
            toBeInserted.add(returnVoidStmt);
            toBeInserted.add(gotoOriginal);
        }

        units.insertAfter(toBeInserted, lastIdentityStmt);

        return method;
    }
}
