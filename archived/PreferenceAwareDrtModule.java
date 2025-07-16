package org.matsim.maas.utils;

import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.drt.optimizer.DrtOptimizer;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.schedule.DrtTaskFactory;
import org.matsim.contrib.dvrp.fleet.Fleet;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeModule;
import org.matsim.contrib.dvrp.run.DvrpMode;
import org.matsim.contrib.dvrp.schedule.ScheduleTimingUpdater;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.framework.MobsimTimer;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Provides;
import com.google.inject.Singleton;

/**
 * Guice module for preference-aware DRT functionality.
 * 
 * This module binds all the preference-aware components and replaces the default
 * DRT optimizer with our preference-aware version using a @Provides method approach.
 * 
 * @author MATSim-MaaS Research Team
 */
public class PreferenceAwareDrtModule extends AbstractDvrpModeModule {
    
    private final String preferencesPath;
    private final boolean enableLearning;
    private final boolean enableDetailedLogging;
    
    public PreferenceAwareDrtModule(String mode, String preferencesPath, boolean enableLearning, boolean enableDetailedLogging) {
        super(mode);
        this.preferencesPath = preferencesPath;
        this.enableLearning = enableLearning;
        this.enableDetailedLogging = enableDetailedLogging;
        
        System.out.println("🔧 Installing PreferenceAwareDrtModule for mode: " + mode);
        System.out.println("   - Preferences path: " + preferencesPath);
        System.out.println("   - Learning enabled: " + enableLearning);
        System.out.println("   - Detailed logging: " + enableDetailedLogging);
    }
    
    @Override
    public void install() {
        // Create and configure preference data loader
        PreferenceDataLoader preferenceLoader = new PreferenceDataLoader();
        
        // Try to load preference data
        try {
            String pathSeparator = preferencesPath.endsWith("/") ? "" : "/";
            preferenceLoader.loadPreferenceData(
                preferencesPath + pathSeparator + "weights.csv",
                preferencesPath + pathSeparator + "user_history.csv",
                preferencesPath + pathSeparator + "features.csv"
            );
            System.out.println("✅ PreferenceDataLoader configured and data loaded");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load preference data: " + e.getMessage());
            System.err.println("   Module will continue with default preferences");
        }
        
        // Bind preference data loader as singleton instance
        bind(PreferenceDataLoader.class).toInstance(preferenceLoader);
        
        // Bind preference-aware components without scoping (let MATSim handle it)
        bind(PreferenceAwareStopFinder.class);
        bind(PreferenceAwareInsertionCostCalculator.class);
        bind(PolicyGradientLearner.class);
        
        // Create and bind preference-aware event handler (instead of replacing DrtOptimizer)
        bind(PreferenceAwareDrtHandler.class).asEagerSingleton();
        addEventHandlerBinding().to(PreferenceAwareDrtHandler.class);
        
        System.out.println("   - PreferenceAwareDrtHandler installed as event handler");
        System.out.println("   - All preference-aware components bound");
        System.out.println("✅ PreferenceAwareDrtModule modal bindings installed (event-based approach)");
    }
    
    /**
     * Print configuration summary
     */
    public void printConfiguration() {
        System.out.println("\n🎯 PreferenceAwareDrtModule Configuration:");
        System.out.println("   ┌─ Data Path: " + preferencesPath);
        System.out.println("   ├─ Learning: " + (enableLearning ? "ENABLED" : "DISABLED"));
        System.out.println("   ├─ Logging: " + (enableDetailedLogging ? "DETAILED" : "MINIMAL"));
        System.out.println("   └─ Mode: " + getMode());
        System.out.println();
    }
} 