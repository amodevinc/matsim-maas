package org.matsim.maas.drt;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtModule;
import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

import org.matsim.maas.utils.PreferenceAwareDrtModule;
import org.matsim.maas.utils.PerformanceMetricsCollector;

import java.io.File;
import java.util.Map;

/**
 * Enhanced DRT example demonstrating preference-aware adaptive dispatching.
 * This example integrates user preference learning with MATSim's DRT simulation
 * to provide personalized and adaptive ride-sharing services.
 * 
 * Usage: Run with Hwaseong DRT configuration for preference-aware simulation.
 * 
 * @author MATSim-MaaS Research Team
 */
public class RunPreferenceAwareDrtExample {
    
    public static void main(String[] args) {
        System.out.println("🎯 MATSim Preference-Aware DRT Example Starting...");
        System.out.println("================================================================");
        
        // Default configuration files
        String configFile = "data/prefAware_config.xml";
        String preferencesPath = "data/user_preference/";
        boolean enableLearning = true;
        boolean enableDetailedLogging = false;
        
        // Parse command line arguments if provided
        if (args.length >= 1) configFile = args[0];
        if (args.length >= 2) preferencesPath = args[1];
        if (args.length >= 3) enableLearning = Boolean.parseBoolean(args[2]);
        if (args.length >= 4) enableDetailedLogging = Boolean.parseBoolean(args[3]);
        
        // Validate inputs
        if (!new File(configFile).exists()) {
            System.err.println("❌ Config file not found: " + configFile);
            System.err.println("   Please ensure the config file exists or provide a valid path");
            return;
        }
        
        if (!new File(preferencesPath).exists()) {
            System.err.println("⚠️ Preferences directory not found: " + preferencesPath);
            System.err.println("   Creating directory and using default preferences...");
            new File(preferencesPath).mkdirs();
        }
        
        System.out.println("📋 Configuration:");
        System.out.println("   ├─ Config file: " + configFile);
        System.out.println("   ├─ Preferences path: " + preferencesPath);
        System.out.println("   ├─ Learning enabled: " + enableLearning);
        System.out.println("   └─ Detailed logging: " + enableDetailedLogging);
        System.out.println();
        
        try {
            runPreferenceAwareDrtSimulation(configFile, preferencesPath, 
                                           enableLearning, enableDetailedLogging);
        } catch (Exception e) {
            System.err.println("❌ Simulation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Run the preference-aware DRT simulation
     */
    public static void runPreferenceAwareDrtSimulation(String configFile, 
                                                      String preferencesPath,
                                                      boolean enableLearning,
                                                      boolean enableDetailedLogging) {
        
        long startTime = System.currentTimeMillis();
        
        System.out.println("🚀 Loading MATSim configuration...");
        
        // Load configuration with DRT modules
        Config config = ConfigUtils.loadConfig(configFile,
            new MultiModeDrtConfigGroup(),
            new DvrpConfigGroup(),
            new OTFVisConfigGroup());
        
        System.out.println("✅ Configuration loaded successfully");
        
        // Validate DRT configuration
        MultiModeDrtConfigGroup multiModeDrtConfig = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
        if (multiModeDrtConfig.getModalElements().isEmpty()) {
            System.err.println("❌ No DRT modes configured in the config file");
            System.err.println("   Please check that the config file contains valid DRT configuration");
            return;
        }
        
        System.out.println("📊 DRT Configuration Summary:");
        for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
            System.out.println(String.format("   ├─ Mode: %s", drtCfg.getMode()));
            System.out.println(String.format("   ├─ Vehicles file: %s", drtCfg.vehiclesFile));
            System.out.println(String.format("   ├─ Max wait time: %.0f s", drtCfg.maxWaitTime));
            System.out.println(String.format("   └─ Max travel time: α=%.1f, β=%.0f s", 
                             drtCfg.maxTravelTimeAlpha, drtCfg.maxTravelTimeBeta));
        }
        System.out.println();
        
        // Create scenario and add DRT routes
        System.out.println("🌍 Creating simulation scenario...");
        Scenario scenario = DrtControlerCreator.createScenarioWithDrtRouteFactory(config);
        ScenarioUtils.loadScenario(scenario);
        
        System.out.println("✅ Scenario loaded:");
        System.out.println(String.format("   ├─ Network links: %d", scenario.getNetwork().getLinks().size()));
        System.out.println(String.format("   ├─ Population size: %d", scenario.getPopulation().getPersons().size()));
        System.out.println(String.format("   └─ Output directory: %s", config.controller().getOutputDirectory()));
        System.out.println();
        
        // Create controler manually to avoid DrtOptimizer binding conflicts
        System.out.println("🎛️ Setting up preference-aware DRT controller...");
        Controler controler = new Controler(scenario);
        
        // Install preference-aware DRT module FIRST (before standard DRT modules)
        // Get the DRT mode from the configuration
        MultiModeDrtConfigGroup drtConfig = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
        String drtMode = drtConfig.getModalElements().iterator().next().getMode();
        
        PreferenceAwareDrtModule preferenceModule = new PreferenceAwareDrtModule(
            drtMode, preferencesPath, enableLearning, enableDetailedLogging);
        preferenceModule.printConfiguration();
        controler.addOverridingModule(preferenceModule);
        
        // Now add standard DRT modules manually (like in RunMaas.java)
        controler.addOverridingModule(new MultiModeDrtModule());
        
        // Add DVRP module
        controler.addOverridingModule(new DvrpModule());
        controler.configureQSimComponents(
            DvrpQSimComponents.activateAllModes(MultiModeDrtConfigGroup.get(config)));
        
        System.out.println("✅ Preference-aware DRT controller configured with manual module installation");
        System.out.println();
        
        // Run simulation
        System.out.println("🏃 Starting MATSim simulation with preference-aware DRT...");
        System.out.println("================================================================");
        
        controler.run();
        
        long simulationTime = System.currentTimeMillis() - startTime;
        
        System.out.println("================================================================");
        System.out.println("🎉 Simulation completed successfully!");
        System.out.println();
        
        // Report performance summary
        // reportSimulationSummary(simulationTime, config.controller().getOutputDirectory());
        
        System.out.println("📁 Results saved to: " + config.controller().getOutputDirectory());
        System.out.println("   ├─ Standard MATSim outputs (events, plans, etc.)");
        System.out.println("   ├─ DRT-specific outputs (customer stats, vehicle stats)");
        System.out.println("   └─ Preference-aware metrics and learning data");
        System.out.println();
        System.out.println("✅ Preference-aware DRT simulation completed successfully!");
    }
    
    /**
     * Report simulation performance summary
     */
    private static void reportSimulationSummary(long simulationTimeMs, String outputDirectory) {
        System.out.println("📊 Simulation Performance Summary:");
        System.out.println(String.format("   ├─ Total simulation time: %.1f minutes", simulationTimeMs / 60000.0));
        System.out.println(String.format("   ├─ Average time per iteration: %.1f seconds", simulationTimeMs / 1000.0));
        System.out.println(String.format("   └─ Output directory: %s", outputDirectory));
        
        // Check for key output files
        File outputDir = new File(outputDirectory);
        if (outputDir.exists()) {
            System.out.println("📁 Key Output Files:");
            
            String[] keyFiles = {
                "output_events.xml.gz", 
                "output_plans.xml.gz", 
                "drt_customer_stats_drt.csv",
                "drt_vehicle_stats_drt.csv"
            };
            
            for (String fileName : keyFiles) {
                File file = new File(outputDir, fileName);
                if (file.exists()) {
                    System.out.println(String.format("   ✅ %s (%.1f MB)", fileName, file.length() / 1024.0 / 1024.0));
                } else {
                    System.out.println("   ⚠️ " + fileName + " (not found)");
                }
            }
        }
        System.out.println();
    }
    
    /**
     * Print usage information
     */
    public static void printUsage() {
        System.out.println("Usage: RunPreferenceAwareDrtExample [config_file] [preferences_path] [enable_learning] [detailed_logging]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  config_file      - Path to MATSim config file (default: data/prefAware_config.xml)");
        System.out.println("  preferences_path - Path to user preferences directory (default: data/user_preference/)");
        System.out.println("  enable_learning  - Enable policy gradient learning (default: true)");
        System.out.println("  detailed_logging - Enable detailed logging output (default: false)");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -cp ... RunPreferenceAwareDrtExample data/prefAware_config.xml data/user_preference/ true false");
    }
} 