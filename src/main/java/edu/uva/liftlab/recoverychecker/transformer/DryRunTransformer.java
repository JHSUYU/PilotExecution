package edu.uva.liftlab.recoverychecker.transformer;

import edu.uva.liftlab.recoverychecker.analysis.PhaseInfo;
import edu.uva.liftlab.recoverychecker.distributedtracing.BaggagePropagation;
import edu.uva.liftlab.recoverychecker.execution.DryRunExecutor;
import edu.uva.liftlab.recoverychecker.generator.DryRunMethodGenerator;
import edu.uva.liftlab.recoverychecker.isolation.IO.IOIsolation;
import edu.uva.liftlab.recoverychecker.isolation.ObjectCloner;
import edu.uva.liftlab.recoverychecker.isolation.stateredirection.ClassFilterHelper;
import edu.uva.liftlab.recoverychecker.isolation.stateredirection.StateRedirection;
import edu.uva.liftlab.recoverychecker.microfork.ThreadTransformer;
import edu.uva.liftlab.recoverychecker.sanitization.Sanization;
import edu.uva.liftlab.recoverychecker.staticanalysis.StartingPointInstrumenter;
import edu.uva.liftlab.recoverychecker.tainting.tracetainting.InjectDryRunTrace;
import edu.uva.liftlab.recoverychecker.util.SootUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;

import java.util.*;

public class DryRunTransformer extends SceneTransformer {

    public static final PhaseInfo PHASE_INFO = new PhaseInfo("wjtp", "transformer method",
            "Transform the run method of RpcExecutor$Handler", true, false);

    private static final Logger LOG = LoggerFactory.getLogger(DryRunTransformer.class);

    public static final String SET_BY_DRY_RUN = "$setByDryRun";

    public String config_file_path;

    private DryRunMethodGenerator dryRunMethodGenerator;
    private StartingPointInstrumenter startingPointInstrumenter;
    private ObjectCloner objectCloner;
    private DryRunExecutor dryRunExecutor;

    private ClassFilterHelper filter;

    public static boolean isInnerClass(SootClass sootClass) {
        return sootClass.getName().contains("$");
    }

    public static boolean isLambdaClass(SootClass sootClass) {
        return sootClass.getName().contains("$lambda");
    }

    public static boolean ignoreMethod4debug(SootMethod method) {
        SootClass declaringClass = method.getDeclaringClass();

        if (declaringClass.getName().equals("org.apache.cassandra.streaming.StreamSession")
                && method.getName().equals("messageReceived")) {
            return true;
        }

        Set<String> ignoreSets= new HashSet<>(Arrays.asList("readChunkOffsets"));
        for (String str:ignoreSets) {
            if(method.getName().contains(str)){
                return true;
            }
        }
        return false;
    }

    public static boolean ignoreClass4debugCassabdra13938(SootClass sootClass) {
        Set<String> whiteLists= new HashSet<>(Arrays.asList(

        ));

        Set<String> blackLists= new HashSet<>(Arrays.asList("org.apache.cassandra.io.compress",
                "org.apache.cassandra.utils.memory",
                //"org.apache.cassandra.service.ActiveRepairService",
                //"org.apache.cassandra.serializers",
                "org.apache.cassandra.tracing",
                "org.apache.cassandra.utils.concurrent.SimpleCondition",
                "org.apache.cassandra.concurrent",
                "org.apache.cassandra.utils.concurrent",
                //"org.apache.cassandra.utils",
                //"org.apache.cassandra.locator.AbstractReplicaCollection",
                "org.apache.cassandra.net",
                "org.apache.cassandra.io.sstable.format",
//                "org.apache.cassandra.utils.MerkleTree",
//                "org.apache.cassandra.utils.MerkleTrees",
                //"org.apache.cassandra.io.sstable.format.SSTableReader",
                //"org.apache.cassandra.io.sstable.format.big.BigTableWriter",
                "org.apache.cassandra.utils.concurrent.Ref",
                //"org.apache.cassandra.streaming"
                //"org.apache.cassandra.io"
                "org.apache.cassandra.io.util"
                ));
        for (String str:whiteLists) {
            if(sootClass.getName().contains(str)){
                LOG.info("Whitelist class: {}", sootClass.getName());
                return false;
            }
        }

        for (String str:blackLists) {
            if(sootClass.getName().contains(str)){
                LOG.info("Ignore class: {}", sootClass.getName());
                return true;
            }
        }
        return false;
    }

    public static boolean ignoreClass4Cassandra13938ManualInstrumentation(SootClass sootClass) {
        Set<String> ignoreSets= new HashSet<>(Arrays.asList(
                "org.apache.cassandra.net.Message",
                "org.apache.cassandra.repair.RepairMessageVerbHandler",
                "org.apache.cassandra.streaming.messages.StreamMessage",
                "org.apache.cassandra.streaming.StreamSession",
                "org.apache.cassandra.streaming.async.NettyStreamingMessageSender",
                "org.apache.cassandra.streaming.async.StreamingInboundHandler"));
        for (String str:ignoreSets) {
            if(sootClass.getName().contains(str)){
                LOG.info("Ignore class: {}", sootClass.getName());
                return true;
            }
        }
        return false;
    }

    public DryRunTransformer(String config_file_path) {
        dryRunMethodGenerator = new DryRunMethodGenerator();
        objectCloner = new ObjectCloner();
        dryRunExecutor = new DryRunExecutor();
        this.config_file_path = config_file_path;
        this.filter = new ClassFilterHelper(this.config_file_path);
        startingPointInstrumenter = new StartingPointInstrumenter(this.filter.getStartingPoints());
    }


    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        LOG.info("DryRunTransformer executing with phase: " + phaseName);
//        SootClass dryRunThreadClass = Scene.v().loadClassAndSupport("org.apache.hadoop.hbase.master.assignment.AssignmentManager$ShadowAssignmentThread");
//        SootClass originalThreadClass = Scene.v().loadClassAndSupport("org.apache.hadoop.hbase.master.assignment.AssignmentManager$AssignmentThread");
//        ThreadTransformer threadTransformer = new ThreadTransformer(originalThreadClass);
//        threadTransformer.hookMicroFork();
//        ThreadAnalyzer threadAnalyzer = new ThreadAnalyzer(originalThreadClass, dryRunThreadClass);
//        threadAnalyzer.hookMicroFork();
        LOG.info("DryRunTransformer executing with phase: " + phaseName);


        //IOIsolation.redirectAllClassesIO(filter);

        dryRunMethodGenerator.processClasses(filter);

        //StateRedirection.redirectAllClassesStates(filter);

        Sanization.sanitizeAllClasses();


        LOG.info("Starting to add dry run fields to all classes");
        for (SootClass sc : Scene.v().getApplicationClasses()) {
            // Skip interfaces and phantom classes
            if (sc.isInterface() || sc.isPhantom() || SootUtils.isDryRunClass(sc)) {
                continue;
            }
            InjectDryRunTrace injectDryRunTrace = new InjectDryRunTrace(sc);
            injectDryRunTrace.addNeedDryRunTraceField();
        }

        LOG.info("Starting to propagate baggage");


        for (SootClass sc : Scene.v().getApplicationClasses()) {
            // Skip interfaces and phantom classes
            if(filter.shouldSkip(sc) && !filter.isInWhiteList(sc)) {
                continue;
            }

            BaggagePropagation baggagePropagation = new BaggagePropagation(sc,filter);
            baggagePropagation.propagateBaggage();
            //LOG.info("Processed class: {}", sc.getName());
        }
//        StartingPointInstrumenter startingPointInstrumenter = new StartingPointInstrumenter(this.filter.getStartingPoints());
//        startingPointInstrumenter.instrumentStartingPoint();
//
//        SootClass originalThreadClass = Scene.v().loadClassAndSupport("org.apache.hadoop.hbase.master.assignment.AssignmentManager$AssignmentThread");
//        ThreadTransformer threadTransformer = new ThreadTransformer(originalThreadClass);
//        threadTransformer.hookMicroFork();
    }

}
