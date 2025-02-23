package edu.uva.liftlab.recoverychecker.util;

public class Constants {

    //TODO: ideally this should be automatically generated for the system,
    // for now we just hardcoded to write it in this way
    public static String DRIVER_NAME = "org.apache.cassandra.t2c.T2CHelper";

    public static final String DRY_RUN_SUFFIX = "$dryRun";

    public static final String ORIGINAL_SUFFIX = "$original";
    public static final String INSTRUMENTATION_SUFFIX = "$instrumentation";

    public static final String INSTRUMENTATION_SUFFIX_FOR_INIT_FUNC = "_$instrumentation";

    public static final String BLACK_LIST = "blacklist.classes";

    public static final String WHITE_LIST = "whitelist.classes";

    public static final String MANUAL_INSTRUMENTATION = "manual.ignore.classes";

    public static final String START_METHODS = "startpoint.methods";

    public static final String DRY_RUN = "dryrun";

    public static final String SHADOW= "$shadow";

    public static final String SHADOW_FIELD= "isShadow";

    public static final String ORIGINAL_THREAD_ID_FIELD = "originalThreadId";

    public static final String WRAP_CONTEXT_CLASS_NAME = "org.apache.hadoop.hbase.trace.WrapContext";

    public static final String UTIL_CLASS_NAME = "org.apache.hadoop.hbase.trace.DryRunTraceUtil";

    public static final String TARGET_FUNC_NAME1 = "access";

    public static final String TARGET_FUNC_NAME2 = "processAssignQueue";

    public static final String TARGET_FUNC_NAME_SHADOW = "evict$shadow";

    public static final String CREATE_SHADOW_THREAD_CLASS = "org.apache.hadoop.hbase.procedure2.util.DryRunUtil";

    public static final String IS_FAST_FORWARD_BAGGAGE = "isFastForward";

    public static final boolean debug = false;

    public static final String mode = "SEDA";

    public static final String STATE_ISOLATION_CLASS = "org.pilot.State";

    public static final String SHOULD_BE_CONTEXT_WRAP_METHOD_SIGNATURE = "boolean shouldBeContextWrap(java.lang.Runnable,java.util.concurrent.Executor)";

    public static final String PILOT_UTIL_CLASS_NAME = "org.pilot.PilotUtil";


}
