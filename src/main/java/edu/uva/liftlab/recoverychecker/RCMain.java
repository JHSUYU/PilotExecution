package edu.uva.liftlab.recoverychecker;

import edu.uva.liftlab.recoverychecker.analysis.AnalysisManager;
import edu.uva.liftlab.recoverychecker.analysis.PhaseInfo;
import edu.uva.liftlab.recoverychecker.option.OptionError;
import edu.uva.liftlab.recoverychecker.option.OptionParser;
import edu.uva.liftlab.recoverychecker.option.RCOptions;
import edu.uva.liftlab.recoverychecker.transformer.DryRunTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.*;
import soot.options.Options;

import java.io.PrintStream;
import java.util.*;

import static soot.SootClass.HIERARCHY;
import static soot.SootClass.SIGNATURES;

public class RCMain {
    private static final Logger LOG = LoggerFactory.getLogger(RCMain.class);

    // Arguments passed through the command line
    static public RCOptions options;
    private boolean initialized;

    static public Date analyzeFinishTime=null;

    public RCMain(RCOptions options) {
        this.options = options;
        initialized = false;
    }

    /**
     * Invoke Soot with our customized options and additional arguments.
     */
    public boolean run() {
        if (!initialized) {
            System.err.println("T2C is not initialized");
            return false;
        }
        if (!Options.v().parse(options.getArgs())) {
            System.err.println("Error in parsing Soot options");
            return false;
        }
        Options.v().warnNonexistentPhase();
        if (Options.v().phase_list()) {
            System.out.println(Options.v().getPhaseList());
            return true;
        }
        if (!Options.v().phase_help().isEmpty()) {
            for (String phase : Options.v().phase_help()) {
                System.out.println(Options.v().getPhaseHelp(phase));
            }
            return true;
        }
        if (AnalysisManager.getInstance().enabledAnalyses().isEmpty()) {
            System.err.println("No T2C analysis is specified.");
            System.err.println("Run with --list to list the analysis available in T2C");
            return false;
        }
        if (Options.v().on_the_fly()) {
            Options.v().set_whole_program(true);
            PhaseOptions.v().setPhaseOption("cg", "off");
        }
        // Invoke Soot's pack manager to run the packs
        try {
            Date start = new Date();
            LOG.info("RecoveryChecker started on " + start);
            Timers.v().totalTimer.start();
            Scene.v().loadNecessaryClasses();
            //        Scene.v().addBasicClass("org.apache.hadoop.hbase.io.hfile.LruBlockCache", HIERARCHY);
//        Scene.v().addBasicClass("org.apache.hadoop.hbase.io.hfile.LruBlockCache$EvictionThread", SIGNATURES);
//        Scene.v().addBasicClass("org.apache.hadoop.hbase.io.hfile.LruBlockCache$ShadowEvictionThread", SIGNATURES);
            System.out.println("Loaded application classes:");
            for (SootClass sc : Scene.v().getApplicationClasses()) {
                System.out.println(sc.getName());
            }
//            System.out.println("=== Soot Packs and Phases Before Execution ===");
//            for (Pack pack : PackManager.v().allPacks()) {
//                System.out.println("Pack: " + pack.getPhaseName());
//                for (Transform transform : pack) {
//                    System.out.println("  Phase: " + transform.getPhaseName());
//                    System.out.println("    Transformer: " + transform.getTransformer().getClass().getName());
//                }
//            }
            Options.v().setPhaseOption("wjtp", "enabled:true");
            PackManager.v().runPacks();

//            LOG.info("Starting RecoveryAnalyzer analysis");
//            recoveryAnalyzer.analyze();
//            LOG.info("RecoveryAnalyzer analysis completed");

            System.out.println("=== Classes processed by Soot ===");
//            MethodCounter methodCounter = (MethodCounter) PackManager.v().getPack("wjtp").get("wjtp.countmethod").getTransformer();
//            System.out.println("=== Classes processed by MethodCounter ===");
//            for (String className : methodCounter.getProcessedClasses()) {
//                System.out.println("Processed class: " + className);
//            }
            if (!Options.v().oaat()) {
                PackManager.v().writeOutput();
            }
            Timers.v().totalTimer.end();
            // Print out time stats.
            if (Options.v().time())
                Timers.v().printProfilingInformation();
            Date finish = new Date();
            LOG.info("T2C finished on " + finish);
            long runtime = finish.getTime() - start.getTime();

            //analyzeFinishTime could be null if running countMethod scripts
            //TODO: refine the logic of setting analyzeFinishTime
            if(analyzeFinishTime!=null) {
                long analysisTime = analyzeFinishTime.getTime() - start.getTime();
                long generationTime = finish.getTime() - analyzeFinishTime.getTime();
                LOG.info("Analysis Time: " + (analysisTime / 60000) + " min. "
                        + ((analysisTime % 60000) / 1000) + " sec. " + (analysisTime % 1000)
                        + " ms.");
                LOG.info("Generation Time: " + (generationTime / 60000) + " min. "
                        + ((generationTime % 60000) / 1000) + " sec. " + (generationTime % 1000)
                        + " ms.");
            }
            LOG.info("T2C has run for " + (runtime / 60000) + " min. "
                    + ((runtime % 60000) / 1000) + " sec. " + (runtime % 1000) + " ms.");
        } catch (StackOverflowError e ) {
            LOG.error( "T2C has run out of stack memory." );
            return false;
        } catch (OutOfMemoryError e) {
            LOG.error( "Soot has run out of the memory allocated to it by the Java VM." );
            return false;
        } catch (RuntimeException e) {
            LOG.error("Unexpected exception encountered: " + e);
            e.printStackTrace();
            return false;
        } finally {
            //AutoIO.getInstance().closeAll();
        }
        return true;
    }

    /**
     * Register the analyses to run with Soot pack manager
     */
    private void registerAnalyses() {
        DryRunTransformer dryRunTransformer = new DryRunTransformer(options.getConfigFilePath());
        AnalysisManager.getInstance().registerAnalysis(dryRunTransformer, DryRunTransformer.PHASE_INFO);
        AnalysisManager.getInstance().validateAllRegistered();
    }

    /**
     * Load basic classes to Soot
     */
    private void loadClasses() {
        // add input classes
        String[] classes = options.getClasses();
        if (classes != null) {
            for (String cls : classes) {
                LOG.info("Load class, add class "+cls.toString());
                Options.v().classes().add(cls); // all to Soot class to be loaded
            }
        }

        // add basic classes
        Class<?>[] basicClasses = {PrintStream.class, System.class,
                Thread.class,
                //Preload a dummy class here so we can use it later
                //Assertion.class
        };
        for (Class<?> cls : basicClasses) {
            LOG.debug("Adding basic class " + cls.getCanonicalName() + " to Soot");
            Scene.v().addBasicClass(cls.getCanonicalName(), SIGNATURES);
            for (Class<?> innerCls : cls.getDeclaredClasses()) {
                // Must use getName instead of getCanonicalName for inner class
                LOG.debug("- inner class " + innerCls.getName() + " added");
                Scene.v().addBasicClass(innerCls.getName(), SIGNATURES);
            }
        }
        Scene.v().addBasicClass("org.apache.hadoop.hbase.trace.WrapContext",SIGNATURES);

        Scene.v().addBasicClass("int[]", SootClass.HIERARCHY);
        Scene.v().addBasicClass("boolean[]", SootClass.HIERARCHY);
        Scene.v().addBasicClass("byte[]", SootClass.HIERARCHY);
        Scene.v().addBasicClass("char[]", SootClass.HIERARCHY);
        Scene.v().addBasicClass("short[]", SootClass.HIERARCHY);
        Scene.v().addBasicClass("float[]", SootClass.HIERARCHY);
        Scene.v().addBasicClass("double[]", SootClass.HIERARCHY);
        Scene.v().addBasicClass("long[]", SootClass.HIERARCHY);

//        Scene.v().addBasicClass("org.apache.hadoop.hbase.io.hfile.LruBlockCache", HIERARCHY);
//        Scene.v().addBasicClass("org.apache.hadoop.hbase.io.hfile.LruBlockCache$EvictionThread", SIGNATURES);
//        Scene.v().addBasicClass("org.apache.hadoop.hbase.io.hfile.LruBlockCache$ShadowEvictionThread", SIGNATURES);



    }

    /**
     * Reset the analysis
     */
    public void reset() {
        // Don't put the G.reset() at the beginning of the initialize() as in the Unit test
        // we may register some special phases, e.g., TestHelper, before calling initialize().
        // They will get cleared if we do reset()...
        G.reset(); // reset Soot
    }

    /**
     * Prepare environment to run t2c: set options, register analyses,
     * load classes, initialize Soot, etc.
     *
     * @return true if the initialization is successful; false otherwise
     */
    public boolean initialize() {
        registerAnalyses(); // register analyses with Soot

        /* Setup Soot options */
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_soot_classpath(options.getClassPath());
        Options.v().set_java_version(8);
        //Options.v().set_output_format(Options.output_format_J);

        if (options.noOutput()) {
            Options.v().set_output_format(Options.output_format_none);
        } else {
            if (!options.genExecutable() && !options.isOutputJar()) {
                // If the output format is not a jar or .class files,
                // we output Jimple by default
                Options.v().set_output_format(Options.output_format_J);
            }
            Options.v().set_output_jar(options.isOutputJar());
        }
        // Well, the truth is even if we specify output format as none
        // T2C still relies on the output dir option to decide
        // where to write its intermediate results... :|
        if (options.getOutputDir() != null) {
            Options.v().set_output_dir(options.getOutputDir());
        }
        //Options.v().set_validate(options.keepValidate());
        Options.v().set_keep_line_number(options.keepDebug());
        Options.v().set_main_class(options.getMainClass());
        Options.v().set_whole_program(true);
        if (!options.isInputListEmpty()) {
            Options.v().set_process_dir(options.getInputList());
        }
        String[] analyses = options.getAnalyses();
        if (analyses == null || analyses.length == 0) {
            // If no analysis is specified, run the default analysis
            analyses = new String[] {DryRunTransformer.PHASE_INFO.getFullName()};
        }

        if (analyses != null) {
            boolean need_call_graph = false;
            for (String analysis : analyses) {
                // Enable the analysis in the manager
                AnalysisManager.getInstance().enableAnalysis(analysis);
                PhaseInfo phaseInfo = AnalysisManager.getInstance().getPhaseInfo(analysis);
                // if any phase needs call graph, we should enable it
                if (phaseInfo.needCallGraph())
                    need_call_graph = true;
            }
//            if (options.isWholeProgram() && !need_call_graph) {
//                // if it is whole program analysis and we don't need call graph analysis
//                // we should explicitly disable it
//                PhaseOptions.v().setPhaseOption("cg", "off");
//            }
        }

        //We enable spark to get on-the-fly callgraph
        Options.v().setPhaseOption("cg.spark","enabled:true");
        //We enable context-sensitive points-to analysis to better achieve
        Options.v().setPhaseOption("cg.spark","cs-demand:true");
        Options.v().setPhaseOption("cg.spark","apponly:true");
        Options.v().setPhaseOption("cg.spark","geom-pta:true");
        //Options.v().setPhaseOption("cg.paddle","enabled:true");

        Map<String, List<String>> all_phase_options = options.getPhaseOptions();
        Set<String> analysis_with_options = new HashSet<>();
        if (all_phase_options != null) {
            for (Map.Entry<String, List<String>> entry : all_phase_options.entrySet()) {
                String phase = entry.getKey();
                // If the the option from command line is for an T2C analysis
                // We must both enable it and add the custom option str
                if (AnalysisManager.getInstance().isAnalysiEnabled(phase)) {
                    analysis_with_options.add(phase);
                    PhaseOptions.v().setPhaseOption(phase, "enabled:true");
                }
                for(String option_str: entry.getValue())
                {
                    // Otherwise, the option from command line is for a standard Soot phase
                    // e.g., -p jb use-original-names:true, we will just pass it along
                    // to Soot
                    boolean success = PhaseOptions.v().setPhaseOption(phase, option_str);
                    if(!success)
                    {
                        System.err.println("ERROR: options not set successfully");
                    }
                }
            }
        }
        if (analyses != null) {
            for (String analysis : analyses) {
                // For any specified analysis that does not have an option from command line
                // We must at least enable it in Soot
                if (!analysis_with_options.contains(analysis)) {
                    Options.v().setPhaseOption(analysis, "on");
                }
            }
        }
        String[] args = options.getArgs();
        if (args == null) {
            args = new String[]{};
        }
        if (args.length == 0) {
            Options.v().set_unfriendly_mode(true); // allow no arguments to be specified for Soot
        }


        // load classes
        loadClasses();
        // load config
//        if (!T2CConfig.getInstance().load() || !AutoIO.getInstance().initialize()) {
//            return false;
//        }

        // override any config that is specified through command line argument
//        if (options.getOverrideProperties() != null) {
//            LOG.info("Overriding config file setting with command line config settings: " +
//                    options.getOverrideProperties().toString());
//            T2CConfig.getInstance().parseProperties(options.getOverrideProperties());
//        }

//        recoveryAnalyzer.initializeAnalysis();
//        initialized = true;
//        LOG.info("RecoveryChecker initialization finished");
        initialized = true;
        return true;
    }

    public static void main(String[] args) {
        System.out.println("Enter main line292");
        System.out.println("Enter main line293");
        OptionParser parser = new OptionParser();
        RCOptions options = null;
        System.out.println("Line295");
        try {
            options = parser.parse(args);
        } catch (OptionError optionError) {
            System.out.println("Error in parsing options: " + optionError.getMessage() + "\n");
            parser.printHelp();
            System.exit(1);
        }
        System.out.println("Line303");
        if (options.isSootHelp()) {
            parser.printHelp();
            System.out.println("\n*********************************************");
            System.out.println("Soot OPTIONS:\n");
            System.out.println(Options.v().getUsage());
            System.exit(0);
        } else if (options.isHelp()) {
            parser.printHelp();
            System.exit(0);
        }
        System.out.println("Line314");
        if (options.listAnalysis()) {
            parser.listAnalyses();
            System.exit(0);
        }
        if (options.isInputListEmpty() && options.getClasses() == null) {
            System.out.println("Must set either a jar file/input directory or a list of classes as input.");
            parser.printHelp();
            System.exit(1);
        }
        System.out.println("Parsed options: " + options);
        // Create T2C now with the parsed options
        RCMain main = new RCMain(options);
        if (!main.initialize() || !main.run()) {
            System.exit(1);
        }

    }
}
