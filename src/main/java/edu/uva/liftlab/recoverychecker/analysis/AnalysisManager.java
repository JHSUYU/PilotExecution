package edu.uva.liftlab.recoverychecker.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import edu.uva.liftlab.recoverychecker.transformer.DryRunTransformer;
import edu.uva.liftlab.recoverychecker.util.SootUtils;
import soot.Transform;
import soot.Transformer;

/**
 * Manage the major analyses in T2C
 */
public class AnalysisManager {
    private Map<String, Transform> analysisMap;
    private Map<String, PhaseInfo> phaseInfoMap;
    private Set<String> enabledAnalysisSet;

    // Information about all the phases available in T2C.
    // You also need to register at edu.jhu.order.t2c.staticd.T2CMain.registerAnalyses()
    private static PhaseInfo[] PHASES  = {
            DryRunTransformer.PHASE_INFO

    };
    private static AnalysisManager instance;

    private AnalysisManager() {
        analysisMap = new HashMap<>();
        phaseInfoMap = new HashMap<>();
        for (PhaseInfo info : PHASES) {
            phaseInfoMap.put(info.getFullName(), info);
        }
        enabledAnalysisSet = new HashSet<>();
    }

    public static AnalysisManager getInstance() {
        if (instance == null) {
            instance = new AnalysisManager();
        }
        return instance;
    }

    public Transform getAnalysis(String name) {
        return analysisMap.get(name);
    }

    public boolean isAnalysiEnabled(String name) {
        return enabledAnalysisSet.contains(name);
    }

    public boolean enableAnalysis(String name) {
        if (phaseInfoMap.containsKey(name)) {
            // Enable only it is available
            enabledAnalysisSet.add(name);
            return true;
        }
        return false;
    }

    public Set<String> enabledAnalyses() {
        return enabledAnalysisSet;
    }

    public PhaseInfo getPhaseInfo(String name) {
        return phaseInfoMap.get(name);
    }

    public Iterator<PhaseInfo> phaseInfoIterator() {
        return phaseInfoMap.values().iterator();
    }

    public Transform registerAnalysis(Transformer analysis, PhaseInfo info) {
        Transform phase = SootUtils.addNewTransform(info.getPack(), info.getFullName(), analysis);
        analysisMap.put(info.getFullName(), phase);
        phaseInfoMap.put(info.getFullName(), info);
        return phase;
    }

    public void validateAllRegistered() {
        for (PhaseInfo info : PHASES) {
            if(!analysisMap.containsKey(info.getFullName()))
                throw new RuntimeException(info.getFullName()+" not registered! " +
                        "Should add the analysis in edu.jhu.order.t2c.staticd.T2CMain.registerAnalyses");
        }
    }
}
