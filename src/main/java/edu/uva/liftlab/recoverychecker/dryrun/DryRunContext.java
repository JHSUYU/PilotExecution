package edu.uva.liftlab.recoverychecker.dryrun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DryRunContext {
    private static final Logger LOG = LoggerFactory.getLogger(DryRunContext.class);
    private static Map<Object, Object> objectCopies = new IdentityHashMap<>();
    private static Map<String, Object> globalVariables = new HashMap<>();
    public static Object deepCopy(Object obj){
        return obj;
    }

    public void trackWholeObject(Object object){
        if(object == null) {
            LOG.warn("Tracking null object");
        }
        objectCopies.put(object, deepCopy(object));
    }

    public void trackModifiedField(Object object, String fieldName) {
        if (object == null) {
            LOG.warn("Tracking modified field of null object");
        }
        if (!objectCopies.containsKey(object)) {
            LOG.info("Tracking modified field of object that was not tracked");
            objectCopies.put(object, deepCopy(object));
        }
    }


    private Set<String> modifiedFields = new HashSet<>();
    private Map<String, Set<String>> modifiedParameters = new HashMap<>();
    private Set<String> accessedFields = new HashSet<>();
    private Set<String> potentiallyModifiedVariables = new HashSet<>();

    public void addModifiedField(String fieldName) {
        modifiedFields.add(fieldName);
    }

    public void addModifiedParameter(String methodName, String paramName) {
        modifiedParameters.computeIfAbsent(methodName, k -> new HashSet<>()).add(paramName);
    }

    public void addAccessedField(String fieldName) {
        accessedFields.add(fieldName);
    }

    public void addPotentiallyModifiedVariable(String varName) {
        potentiallyModifiedVariables.add(varName);
    }

    // Getters for these collections...
}
