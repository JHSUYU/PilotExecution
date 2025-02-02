package edu.uva.liftlab.recoverychecker.microfork;

import edu.uva.liftlab.recoverychecker.util.SootUtils;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Body;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

import java.util.*;

public class LockAnalyzer {
    public Map<SootMethod, Map<Unit, String>> methodUnitIds = new HashMap<>();

    private Map<Unit, SootMethod> unitToMethod = new HashMap<>();
    private Map<Unit, Set<List<Unit>>> lockCallChains = new HashMap<>();
    private Map<Unit, Set<List<Unit>>> awaitCallChains = new HashMap<>();

    public Map<Unit, SootMethod> divergePoints = new HashMap<>();

    private Set<SootMethod> visitedMethods = new HashSet<>();

    private SootClass threadClass;

    public LockAnalyzer(SootClass threadClass){
        this.threadClass = threadClass;
    }

    public void analyze() {
        SootMethod runMethod = threadClass.getMethodByName("run");
        List<Unit> currentChain = new ArrayList<>();
        analyzeMethod(runMethod,currentChain);
        buildMethodUnitIds(lockCallChains, awaitCallChains);
    }



    private boolean isHandlerUnitOfAnyTrap(Unit unit, Body body) {
        for (soot.Trap trap : body.getTraps()) {
            if (trap.getHandlerUnit() == unit) {
                return true;
            }
        }
        return false;
    }

    private Unit findTrapEnd(Unit handlerUnit, Body body) {
        // 找到下一个try块的开始或者下一个catch块的开始
        Unit current = body.getUnits().getSuccOf(handlerUnit);
        while (current != null) {
            if (isHandlerUnitOfAnyTrap(current, body)) {
                return current;
            }
            for (soot.Trap trap : body.getTraps()) {
                if (trap.getBeginUnit() == current) {
                    return current;
                }
            }
            current = body.getUnits().getSuccOf(current);
        }
        return null;  // 如果没有找到，返回null表示到方法结束
    }

    private Set<Unit> getUnitsInCatchBlocks(Body body) {
        Set<Unit> unitsInCatch = new HashSet<>();
        for (soot.Trap trap : body.getTraps()) {
            Unit handlerUnit = trap.getHandlerUnit();
            Unit trapEnd = findTrapEnd(handlerUnit, body);

            Unit current = handlerUnit;
            // 收集从handlerUnit到trapEnd之间的所有Unit
            while (current != trapEnd && current != null) {
                unitsInCatch.add(current);
                current = body.getUnits().getSuccOf(current);
            }
        }
        return unitsInCatch;
    }

    private void analyzeMethod(SootMethod method, List<Unit> currentChain) {
        if (visitedMethods.contains(method)) {
            return;
        }
        visitedMethods.add(method);

        if (!method.hasActiveBody()) {
            return;
        }
        Body body = method.getActiveBody();

        Set<Unit> unitsInCatch = getUnitsInCatchBlocks(body);

        for (Unit unit : body.getUnits()) {
            // 跳过catch块中的Unit
            if (unitsInCatch.contains(unit)) {
                continue;
            }

            InvokeExpr invoke = SootUtils.getInvokeExpr(unit);

            if (invoke != null) {
                List<Unit> newChain = new ArrayList<>(currentChain);
                newChain.add(unit);
                unitToMethod.put(unit, method);

                SootMethod calledMethod = invoke.getMethod();

                if (calledMethod.getName().equals("lock")) {
                    lockCallChains.computeIfAbsent(unit, k -> new HashSet<>())
                            .add(new ArrayList<>(newChain));
                    divergePoints.put(unit, method);
                }
                else if (calledMethod.getName().equals("await")) {
                    awaitCallChains.computeIfAbsent(unit, k -> new HashSet<>())
                            .add(new ArrayList<>(newChain));
                    divergePoints.put(unit, method);
                }

                if (!calledMethod.getDeclaringClass().getName().startsWith("java.")
                        && !calledMethod.getDeclaringClass().getName().contains("LockWrapper")
                        && !calledMethod.getDeclaringClass().getName().contains("ConditionVariableWrapper")) {
                    analyzeMethod(calledMethod, newChain);
                }
            }
        }
    }

    private void buildMethodUnitIds(Map<Unit, Set<List<Unit>>> lockChains, Map<Unit, Set<List<Unit>>> awaitChains) {
        Map<SootMethod, Set<Unit>> methodToUnits = new HashMap<>();


        Set<Unit> allUnits = new HashSet<>();
        for (Set<List<Unit>> chains : lockChains.values()) {
            for (List<Unit> chain : chains) {
                allUnits.addAll(chain);
            }
        }
        for (Set<List<Unit>> chains : awaitChains.values()) {
            for (List<Unit> chain : chains) {
                allUnits.addAll(chain);
            }
        }


        for (Unit unit : allUnits) {
            SootMethod method = unitToMethod.get(unit);
            assert method!=null;
            methodToUnits.computeIfAbsent(method, k -> new HashSet<>()).add(unit);
        }


        for (Map.Entry<SootMethod, Set<Unit>> entry : methodToUnits.entrySet()) {
            SootMethod method = entry.getKey();
            Set<Unit> units = entry.getValue();
            Map<Unit, String> unitIds = new HashMap<>();

            List<Unit> orderedUnits = new ArrayList<>(method.getActiveBody().getUnits());

            orderedUnits.retainAll(units);
            assert orderedUnits.size() == units.size();
            int counter = 0;
            for (Unit unit : orderedUnits) {
                String unitId = String.valueOf(counter++);
                unitIds.put(unit, unitId);
            }

            methodUnitIds.put(method, unitIds);
        }
    }


}
