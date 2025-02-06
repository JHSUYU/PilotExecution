package edu.uva.liftlab.recoverychecker.isolation.stateredirection;

import edu.uva.liftlab.recoverychecker.util.PropertyType;
import edu.uva.liftlab.recoverychecker.util.SootUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootClass;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static edu.uva.liftlab.recoverychecker.util.Constants.DRY_RUN;

public class ClassFilterHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ClassFilterHelper.class);

    private final String configPath;
    private final Set<String> whiteList;
    private final Set<String> blackList;

    private final Set<String> startingPoints;
    private final Set<String> manualInstrumentation;

    public ClassFilterHelper(String configPath) {
        this.configPath = configPath;
        this.whiteList = new HashSet<>(SootUtils.getListFromProperty(configPath, PropertyType.WHITE_LIST));
        this.blackList = new HashSet<>(SootUtils.getListFromProperty(configPath, PropertyType.BLACK_LIST));
        this.manualInstrumentation = new HashSet<>(SootUtils.getListFromProperty(configPath, PropertyType.MANUAL_INSTRUMENTATION));
        this.startingPoints = new LinkedHashSet<>(SootUtils.getListFromProperty(configPath, PropertyType.START_POINTS));

    }

    public boolean shouldSkip(SootClass sc) {

        if (sc.isInterface() || sc.isPhantom()) {
            LOG.debug("Skipping interface or phantom class: {}", sc.getName());
            return true;
        }

//        boolean isInWhiteList = isClassInList(sc, whiteList);
//        if (isInWhiteList) {
//            LOG.debug("Class {} is in whitelist, not skipping", sc.getName());
//            return false;
//        }

        if (isDryRunClass(sc)) {
            LOG.debug("Skipping dry run class: {}", sc.getName());
            return true;
        }

        if (isClassInList(sc, blackList)) {
            LOG.debug("Class {} is in blacklist, skipping", sc.getName());
            return true;
        }

        if (isClassInList(sc, manualInstrumentation)) {
            LOG.debug("Class {} is manually instrumented, skipping", sc.getName());
            return true;
        }

        return false;
    }


    public boolean shouldSkipForTracing(SootClass sc) {

        if (sc.isInterface() || sc.isPhantom()) {
            LOG.debug("Skipping interface or phantom class: {}", sc.getName());
            return true;
        }

        boolean isInWhiteList = isClassInList(sc, whiteList);
        if (isInWhiteList) {
            LOG.debug("Class {} is in whitelist, not skipping", sc.getName());
            return false;
        }

        if (isDryRunClass(sc)) {
            LOG.debug("Skipping dry run class: {}", sc.getName());
            return true;
        }

        if (isClassInList(sc, blackList)) {
            LOG.debug("Class {} is in blacklist, skipping", sc.getName());
            return true;
        }

        if (isClassInList(sc, manualInstrumentation)) {
            LOG.debug("Class {} is manually instrumented, skipping", sc.getName());
            return true;
        }

        return false;
    }

    private boolean isDryRunClass(SootClass sc) {
        return sc.getName().contains(DRY_RUN);
    }

    private boolean isClassInList(SootClass sc, Set<String> list) {
        String className = sc.getName();
        for (String pattern : list) {
            if (pattern.isEmpty()) {
                continue;
            }
            if (className.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    public boolean isInWhiteList(SootClass sc) {
        boolean res = isClassInList(sc, whiteList);
        //print white list
        for(String s: whiteList){
            LOG.info("WhiteList: {}", s);
        }
        if (res) {
            LOG.info("Class {} is in whitelist", sc.getName());
        }
        return res;
    }

    public boolean isInBlackList(SootClass sc) {
        return isClassInList(sc, blackList);
    }

    public boolean isManuallyInstrumented(SootClass sc) {
        return isClassInList(sc, manualInstrumentation);
    }

    public Set<String> getWhiteList() {
        return new HashSet<>(whiteList);
    }

    public Set<String> getBlackList() {
        return new HashSet<>(blackList);
    }

    public Set<String> getManualInstrumentation() {
        return new HashSet<>(manualInstrumentation);
    }

    public Set<String> getStartingPoints() {
        return new LinkedHashSet<>(startingPoints);
    }
}