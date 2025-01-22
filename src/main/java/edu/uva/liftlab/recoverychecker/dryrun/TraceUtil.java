package edu.uva.liftlab.recoverychecker.dryrun;

import com.rits.cloning.Cloner;

import java.util.HashMap;
import java.util.Map;

public class TraceUtil {
    Cloner cloner = new Cloner();

    private static Map<String, Map<String, Object>> stateMap = new HashMap<>();

    public static void recordState(String methodSig, HashMap<String, Object> state){
        Map<String, Object> varMap = new HashMap<>();

        for(Map.Entry<String, Object> entry: state.entrySet()){
            String name = entry.getKey();
            Object value = entry.getValue();
            varMap.put(name, value);
        }

        stateMap.putIfAbsent(methodSig, varMap);

    }

    public static Map<String, Object> getState(String methodSig){
        Map<String, Object> state = stateMap.get(methodSig);
        return state;
    }

    public static void clearStates() {
        stateMap.clear();
    }
}
