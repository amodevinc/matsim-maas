/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.maas.experiment;

// DrtAnalysisModule not available in this version
import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.vis.otfvis.OTFVisConfigGroup;
import org.matsim.maas.experiment.analysis.BaselineMetricsHandler;

/**
 * Enhanced baseline DRT simulation for Hwaseong scenario with comprehensive logging
 * and support for different population files and experimental configurations.
 * 
 * This is the foundation for comparing preference-aware RL dispatching algorithms
 * against standard MATSim DRT performance.
 * 
 * @author Preference-Aware DRT Research Team
 */
public class RunHwaseongBaseline {

    /**
     * Run DRT simulation with enhanced logging and metrics collection
     * @param config MATSim configuration
     * @param otfvis Whether to enable visualization
     * @param experimentName Name for this experiment run
     */
    public static void run(Config config, boolean otfvis, String experimentName) {
        System.out.println("=== RunHwaseongBaseline: Starting " + experimentName + " ===");
        
        // Log key configuration parameters
        logConfigurationSummary(config, experimentName);
        
        // Create controler with DRT capabilities
        Controler controler = DrtControlerCreator.createControler(config, otfvis);
        
        // Add comprehensive analysis and logging
        addAnalysisModules(controler, experimentName);
        
        // Run simulation
        System.out.println("RunHwaseongBaseline: Starting simulation for " + experimentName);
        long startTime = System.currentTimeMillis();
        
        controler.run();
        
        long endTime = System.currentTimeMillis();
        double durationMinutes = (endTime - startTime) / 60000.0;
        System.out.printf("RunHwaseongBaseline: Completed %s in %.2f minutes%n", 
                         experimentName, durationMinutes);
        
        // Log final summary
        logExperimentSummary(config, experimentName, durationMinutes);
    }
    
    /**
     * Add analysis modules for comprehensive metrics collection
     */
    private static void addAnalysisModules(Controler controler, String experimentName) {
        // Add our custom baseline metrics handler
        controler.addOverridingModule(new org.matsim.core.controler.AbstractModule() {
            @Override
            public void install() {
                addEventHandlerBinding().toInstance(new BaselineMetricsHandler(
                    getConfig().controller().getOutputDirectory()));
            }
        });
        
        System.out.println("RunHwaseongBaseline: Added analysis modules for " + experimentName);
    }
    
    /**
     * Log configuration summary for reproducibility
     */
    private static void logConfigurationSummary(Config config, String experimentName) {
        System.out.println("\n=== CONFIGURATION SUMMARY: " + experimentName + " ===");
        
        // Basic configuration
        System.out.println("Network file: " + config.network().getInputFile());
        System.out.println("Population file: " + config.plans().getInputFile());
        System.out.println("Last iteration: " + config.controller().getLastIteration());
        System.out.println("Random seed: " + config.global().getRandomSeed());
        System.out.println("Output directory: " + config.controller().getOutputDirectory());
        
        // DRT configuration
        MultiModeDrtConfigGroup drtConfig = MultiModeDrtConfigGroup.get(config);
        for (var drtCfg : drtConfig.getModalElements()) {
            System.out.println("\n--- DRT Mode: " + drtCfg.getMode() + " ---");
            System.out.println("Vehicles file: " + drtCfg.vehiclesFile);
            System.out.println("Operational scheme: " + drtCfg.operationalScheme);
            System.out.println("Max wait time: " + drtCfg.maxWaitTime + "s");
            System.out.println("Max travel time alpha: " + drtCfg.maxTravelTimeAlpha);
            System.out.println("Max travel time beta: " + drtCfg.maxTravelTimeBeta + "s");
            System.out.println("Stop duration: " + drtCfg.stopDuration + "s");
        }
        
        System.out.println("===============================================\n");
    }
    
    /**
     * Log experiment summary for analysis
     */
    private static void logExperimentSummary(Config config, String experimentName, double durationMinutes) {
        System.out.println("\n=== EXPERIMENT SUMMARY: " + experimentName + " ===");
        System.out.printf("Simulation duration: %.2f minutes%n", durationMinutes);
        System.out.println("Output directory: " + config.controller().getOutputDirectory());
        System.out.println("Check output directory for detailed results and analysis");
        System.out.println("Key files to examine:");
        System.out.println("  - drt_legs_*.csv: Trip-level statistics");
        System.out.println("  - drt_vehicles_*.csv: Vehicle utilization");
        System.out.println("  - drt_customer_stats_*.csv: Customer service statistics");
        System.out.println("  - events.xml.gz: Detailed simulation events");
        System.out.println("================================================\n");
    }
    
    /**
     * Create configuration with appropriate output directory naming
     */
    public static Config createConfig(String configFile, String populationFile, String experimentName) {
        Config config = ConfigUtils.loadConfig(configFile,
                new MultiModeDrtConfigGroup(), new DvrpConfigGroup(), new OTFVisConfigGroup());
        
        // Set population file
        if (populationFile != null && !populationFile.isEmpty()) {
            config.plans().setInputFile(populationFile);
        }
        
        // Set output directory with experiment name
        String outputDir = "output/" + experimentName;
        config.controller().setOutputDirectory(outputDir);
        config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        
        // Add run ID for tracking
        config.controller().setRunId(experimentName);
        
        return config;
    }
    
    /**
     * Main method with command-line argument support
     * Usage: RunHwaseongBaseline [configFile] [populationFile] [experimentName]
     */
    public static void main(String[] args) {
        String configFile = "data/baseline_config.xml";
        String populationFile = null;
        String experimentName = "baseline_default";
        
        // Parse command line arguments
        if (args.length >= 1) {
            configFile = args[0];
        }
        if (args.length >= 2) {
            populationFile = args[1];
            // Extract experiment name from population file if not provided
            if (args.length < 3) {
                experimentName = extractExperimentName(populationFile);
            }
        }
        if (args.length >= 3) {
            experimentName = args[2];
        }
        
        System.out.println("RunHwaseongBaseline: Configuration");
        System.out.println("  Config file: " + configFile);
        System.out.println("  Population file: " + (populationFile != null ? populationFile : "default from config"));
        System.out.println("  Experiment name: " + experimentName);
        
        // Create and run configuration
        Config config = createConfig(configFile, populationFile, experimentName);
        run(config, false, experimentName);
    }
    
    /**
     * Extract experiment name from population file path
     */
    private static String extractExperimentName(String populationFile) {
        if (populationFile == null) {
            return "baseline_default";
        }
        
        // Extract filename without path and extension
        String filename = populationFile.substring(populationFile.lastIndexOf("/") + 1);
        if (filename.endsWith("_population.xml.gz")) {
            filename = filename.substring(0, filename.length() - "_population.xml.gz".length());
        }
        
        return "baseline_" + filename;
    }
}