package org.matsim.maas.preference;

import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.maas.preference.cost.PrefCostQSimModuleWithRL;
import org.matsim.maas.preference.data.DynamicUserPreferenceStore;
import org.matsim.maas.preference.events.PreferenceUpdateTracker;
import org.matsim.maas.preference.learning.LearningConfiguration;
import org.matsim.maas.preference.learning.PolicyGradientPreferenceLearner;
import org.matsim.maas.rl.events.PrefRLEventHandlerWithLearning;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

/**
 * Enhanced preference-aware DRT runner with Reinforcement Learning capabilities.
 * 
 * This class extends the basic preference-aware DRT system with dynamic learning
 * that adapts user preferences based on their acceptance/rejection behavior and
 * trip completion satisfaction.
 * 
 * Key Features:
 * - Static or dynamic preference learning
 * - Policy gradient-based preference updates
 * - Comprehensive analytics and monitoring
 * - Backward compatibility with static preferences
 * - Configurable learning parameters
 */
public class RunPreferenceAwareDrtWithRL {

    public static void run(Config config, boolean otfvis, RLConfiguration rlConfig) {
        System.out.println("=== RL-ENHANCED PREFERENCE-AWARE DRT SIMULATION ===");
        
        // Validate DRT configuration
        MultiModeDrtConfigGroup multiModeDrtConfig = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
        if (multiModeDrtConfig.getModalElements().isEmpty()) {
            throw new IllegalArgumentException("No DRT modes configured in the config file. Please check multiModeDrt configuration.");
        }
        
        // Log DRT configuration details
        System.out.println("📊 DRT Configuration Summary:");
        for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
            System.out.println(String.format("   ├─ Mode: %s", drtCfg.getMode()));
            System.out.println(String.format("   ├─ Vehicles file: %s", drtCfg.vehiclesFile));
            System.out.println(String.format("   ├─ Operational scheme: %s", drtCfg.operationalScheme));
            System.out.println(String.format("   ├─ Max wait time: %.0f s", drtCfg.maxWaitTime));
            System.out.println(String.format("   └─ Max travel time: α=%.1f, β=%.0f s", 
                             drtCfg.maxTravelTimeAlpha, drtCfg.maxTravelTimeBeta));
        }
        
        // Log RL configuration
        System.out.println("\n🧠 RL Configuration Summary:");
        System.out.println("   ├─ RL Learning: " + (rlConfig.enableLearning ? "ENABLED" : "DISABLED"));
        System.out.println("   ├─ Use preferences: " + rlConfig.usePreferenceWeights);
        if (rlConfig.enableLearning) {
            System.out.println("   ├─ Learning algorithm: " + rlConfig.learningConfig.toString());
            System.out.println("   ├─ Output directory: " + rlConfig.outputDirectory);
            System.out.println("   └─ Load saved preferences: " + (rlConfig.loadSavedPreferences != null));
        }
        
        // Create dynamic preference store
        String outputDir = config.controller().getOutputDirectory();
        DynamicUserPreferenceStore dynamicStore = new DynamicUserPreferenceStore(outputDir + "/preferences");
        
        // Load initial preferences
        System.out.println("📖 Loading initial preferences...");
        // This would normally load from your data/user_preference/ files
        // For now, we'll let the existing PreferenceDataLoader handle this
        
        // Load saved preferences if specified
        if (rlConfig.loadSavedPreferences != null) {
            System.out.println("🔄 Loading previously learned preferences from: " + rlConfig.loadSavedPreferences);
            dynamicStore.loadLearnedPreferences(rlConfig.loadSavedPreferences);
        }
        
        // Create policy gradient learner
        PolicyGradientPreferenceLearner learner = null;
        if (rlConfig.enableLearning) {
            learner = new PolicyGradientPreferenceLearner(dynamicStore, rlConfig.learningConfig);
            System.out.println("🎯 Created PolicyGradientPreferenceLearner with config: " + rlConfig.learningConfig.toString());
        }
        
        // Create preference update tracker
        PreferenceUpdateTracker updateTracker = new PreferenceUpdateTracker(outputDir + "/rl_analytics");
        
        // Creates a MATSim Controler and preloads all DRT related packages
        System.out.println("🚀 Creating DRT controler...");
        Controler controler = DrtControlerCreator.createControler(config, otfvis);
        
        // Add preference-aware cost calculation module for each DRT mode
        for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
            String mode = drtCfg.getMode();
            System.out.println("🔧 Installing RL-enhanced PrefCostQSimModule for mode: " + mode);
            
            PrefCostQSimModuleWithRL module = new PrefCostQSimModuleWithRL(
                mode, rlConfig.usePreferenceWeights, dynamicStore, rlConfig.enableLearning);
            controler.addOverridingQSimModule(module);
            
            System.out.println("✅ RL-enhanced PrefCostQSimModule installed for mode: " + mode);
        }
        
        // Add RL event handler if learning is enabled
        if (rlConfig.enableLearning && learner != null) {
            PrefRLEventHandlerWithLearning rlEventHandler = new PrefRLEventHandlerWithLearning(
                outputDir + "/rl_events", dynamicStore, learner, updateTracker);
            controler.addOverridingModule(new org.matsim.core.controler.AbstractModule() {
                @Override
                public void install() {
                    bind(PrefRLEventHandlerWithLearning.class).toInstance(rlEventHandler);
                }
            });
            controler.getEvents().addHandler(rlEventHandler);
            controler.getEvents().addHandler(updateTracker);
            controler.addControlerListener(updateTracker);
            
            System.out.println("🎓 Added RL learning event handler");
        }
        
        System.out.println("\n🎯 Starting RL-enhanced preference-aware DRT simulation");
        System.out.println("🎯 RL Learning: " + (rlConfig.enableLearning ? "ENABLED" : "DISABLED"));
        System.out.println("🎯 Using preference weights: " + rlConfig.usePreferenceWeights);
        System.out.println("🎯 Population: " + config.plans().getInputFile());
        System.out.println("🎯 Output directory: " + config.controller().getOutputDirectory());
        
        // Starts the simulation
        controler.run();
        
        // Post-simulation processing
        if (rlConfig.enableLearning) {
            System.out.println("\n📊 Post-simulation RL analysis:");
            
            // Save final learned preferences
            dynamicStore.persistPreferences(config.controller().getLastIteration());
            System.out.println("💾 Saved learned preferences for iteration " + config.controller().getLastIteration());
            
            // Export learning statistics
            String statsFile = outputDir + "/rl_analytics/final_learning_statistics.csv";
            dynamicStore.exportUpdateStatistics(statsFile);
            System.out.println("📈 Exported learning statistics to: " + statsFile);
            
            // Display learning summary
            if (learner != null) {
                var stats = learner.getStatistics();
                System.out.println("🎓 Learning Summary:");
                System.out.println("   ├─ Total updates: " + stats.totalUpdates);
                System.out.println("   ├─ Active users: " + stats.activeUsers);
                System.out.println("   ├─ Final learning rate: " + String.format("%.6f", stats.currentLearningRate));
                System.out.println("   └─ Final exploration rate: " + String.format("%.4f", stats.currentExplorationRate));
            }
        }
        
        System.out.println("\n✅ RL-enhanced simulation completed successfully!");
        System.out.println("📁 Results available in: " + config.controller().getOutputDirectory());
        
        if (rlConfig.enableLearning) {
            System.out.println("🧠 RL Analytics available in: " + outputDir + "/rl_analytics/");
            System.out.println("💾 Learned preferences available in: " + outputDir + "/preferences/");
        }
    }

    public static void main(String[] args) {
        System.out.println("=== RL-ENHANCED PREFERENCE-AWARE DRT SIMULATION ===");
        
        // Parse command line arguments
        RLConfiguration rlConfig = parseArguments(args);
        
        System.out.println("📁 Config file: " + rlConfig.configPath);
        System.out.println("⚙️  Use preference weights: " + rlConfig.usePreferenceWeights);
        System.out.println("🧠 RL Learning enabled: " + rlConfig.enableLearning);
        
        // Load configuration following MATSim guidelines
        System.out.println("📖 Loading MATSim configuration...");
        Config config = ConfigUtils.loadConfig(rlConfig.configPath,
                new MultiModeDrtConfigGroup(), new DvrpConfigGroup(), new OTFVisConfigGroup());
        
        // Use the population file specified in the config
        System.out.println("👥 Using population file: " + config.plans().getInputFile());
        
        // Test with RL-enhanced preference-aware mode
        System.out.println("\n--- Starting RL-enhanced preference-aware DRT ---");
        
        try {
            run(config, false, rlConfig);
        } catch (Exception e) {
            System.err.println("❌ Simulation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static RLConfiguration parseArguments(String[] args) {
        RLConfiguration config = new RLConfiguration();
        
        // Default values
        config.configPath = "data/baseline_config.xml";
        config.usePreferenceWeights = true;
        config.enableLearning = false; // Conservative default
        config.learningConfig = LearningConfiguration.createDefault();
        config.outputDirectory = "output/rl_enhanced";
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config":
                    if (i + 1 < args.length) config.configPath = args[++i];
                    break;
                case "--preferences":
                    config.usePreferenceWeights = Boolean.parseBoolean(args[++i]);
                    break;
                case "--enable-rl":
                    config.enableLearning = Boolean.parseBoolean(args[++i]);
                    break;
                case "--learning-rate":
                    if (i + 1 < args.length) {
                        double rate = Double.parseDouble(args[++i]);
                        config.learningConfig = new LearningConfiguration.Builder()
                            .initialLearningRate(rate)
                            .build();
                    }
                    break;
                case "--load-preferences":
                    if (i + 1 < args.length) config.loadSavedPreferences = args[++i];
                    break;
                case "--conservative":
                    config.learningConfig = LearningConfiguration.createConservative();
                    break;
                case "--aggressive":
                    config.learningConfig = LearningConfiguration.createAggressive();
                    break;
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    // Legacy positional arguments for backward compatibility
                    if (i == 0) config.configPath = args[i];
                    else if (i == 1) config.usePreferenceWeights = Boolean.parseBoolean(args[i]);
                    else if (i == 2) config.enableLearning = Boolean.parseBoolean(args[i]);
                    break;
            }
        }
        
        return config;
    }
    
    private static void printUsage() {
        System.out.println("Usage: RunPreferenceAwareDrtWithRL [options]");
        System.out.println("Options:");
        System.out.println("  --config <file>              MATSim configuration file (default: data/baseline_config.xml)");
        System.out.println("  --preferences <true|false>   Use preference weights (default: true)");
        System.out.println("  --enable-rl <true|false>     Enable RL learning (default: false)");
        System.out.println("  --learning-rate <rate>       Set learning rate (default: 0.005)");
        System.out.println("  --load-preferences <file>    Load previously learned preferences");
        System.out.println("  --conservative               Use conservative learning settings");
        System.out.println("  --aggressive                 Use aggressive learning settings");
        System.out.println("  --help                       Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Run with static preferences only");
        System.out.println("  java RunPreferenceAwareDrtWithRL --config data/baseline_config.xml");
        System.out.println();
        System.out.println("  # Run with RL learning enabled");
        System.out.println("  java RunPreferenceAwareDrtWithRL --config data/baseline_config.xml --enable-rl true");
        System.out.println();
        System.out.println("  # Continue learning from previous run");
        System.out.println("  java RunPreferenceAwareDrtWithRL --enable-rl true --load-preferences output/preferences/learned_preferences_iter_10.csv");
    }
    
    /**
     * Configuration container for RL-enhanced simulation
     */
    public static class RLConfiguration {
        public String configPath;
        public boolean usePreferenceWeights;
        public boolean enableLearning;
        public LearningConfiguration learningConfig;
        public String outputDirectory;
        public String loadSavedPreferences; // Path to previously saved preferences
    }
}