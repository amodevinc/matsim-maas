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

package org.matsim.maas.preference;

import org.matsim.contrib.drt.run.DrtControlerCreator;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.maas.preference.cost.PrefCostQSimModule;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

/**
 * Test run for preference-aware DRT cost calculation.
 * Integrates the PrefCostCalculator into the DRT optimization system
 * to verify proper integration with MATSim's DRT framework.
 * 
 * Follows MATSim guidelines:
 * - Uses DrtControlerCreator.createControler() for proper DRT setup
 * - Adds custom module via controler.addOverridingModule()
 * - Automatically detects DRT modes from configuration
 * - Provides comprehensive logging for debugging
 */
public class RunPreferenceAwareDrt {

    public static void run(Config config, boolean otfvis, boolean usePreferenceWeights) {
        System.out.println("=== PREFERENCE-AWARE DRT SIMULATION ===");
        
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
        
        // Fix population file path if needed
        String currentPopFile = config.plans().getInputFile();
        
        // Creates a MATSim Controler and preloads all DRT related packages
        System.out.println("🚀 Creating DRT controler...");
        Controler controler = DrtControlerCreator.createControler(config, otfvis);
        
        // Add preference-aware cost calculation module for each DRT mode
        // Following MATSim guidelines: addOverridingQSimModule() for QSim-level overrides
        for (DrtConfigGroup drtCfg : multiModeDrtConfig.getModalElements()) {
            String mode = drtCfg.getMode();
            System.out.println("🔧 Installing PrefCostQSimModule for mode: " + mode);
            controler.addOverridingQSimModule(new PrefCostQSimModule(mode, usePreferenceWeights));
            System.out.println("✅ PrefCostQSimModule installed for mode: " + mode);
        }
        
        System.out.println("🎯 Starting simulation with preference-aware cost calculation");
        System.out.println("🎯 Using preference weights: " + usePreferenceWeights);
        System.out.println("🎯 Population: " + config.plans().getInputFile());
        System.out.println("🎯 Output directory: " + config.controller().getOutputDirectory());
        
        // Starts the simulation
        controler.run();
        
        System.out.println("✅ Simulation completed successfully!");
        System.out.println("📁 Results available in: " + config.controller().getOutputDirectory());
    }

    public static void main(String[] args) {
        System.out.println("=== PREFERENCE-AWARE DRT SIMULATION TEST ===");
        
        // Parse command line arguments
        boolean usePreferenceWeights = true;
        String configPath = "data/baseline_config.xml";
        
        if (args.length >= 1) {
            configPath = args[0];
        }
        if (args.length >= 2) {
            usePreferenceWeights = Boolean.parseBoolean(args[1]);
        }
        
        System.out.println("📁 Config file: " + configPath);
        System.out.println("⚙️  Use preference weights: " + usePreferenceWeights);
        
        // Load configuration following MATSim guidelines
        System.out.println("📖 Loading MATSim configuration...");
        Config config = ConfigUtils.loadConfig(configPath,
                new MultiModeDrtConfigGroup(), new DvrpConfigGroup(), new OTFVisConfigGroup());
        
        // Use the population file specified in the config
        System.out.println("👥 Using population file: " + config.plans().getInputFile());
        
        // Test with preference-aware mode
        System.out.println("\n--- Testing preference-aware DRT ---");
        
        try {
            run(config, false, usePreferenceWeights);
        } catch (Exception e) {
            System.err.println("❌ Simulation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}