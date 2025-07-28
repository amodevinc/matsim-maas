package org.matsim.maas.preference.cost;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.matsim.contrib.drt.optimizer.insertion.InsertionCostCalculator;
import org.matsim.contrib.drt.optimizer.insertion.DefaultInsertionCostCalculator;
import org.matsim.contrib.drt.optimizer.insertion.CostCalculationStrategy;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.maas.preference.data.DynamicUserPreferenceStore;
import org.matsim.maas.preference.data.PreferenceDataLoader;
import org.matsim.maas.preference.data.UserPreferenceStore;

/**
 * Enhanced QSim module for preference-aware cost calculation with RL support.
 * 
 * This module supports both static and dynamic preference learning:
 * - Static mode: Uses traditional UserPreferenceStore (backward compatible)
 * - Dynamic mode: Uses DynamicUserPreferenceStore with learning capabilities
 * 
 * The module automatically determines which preference store to use based on
 * the enableDynamicLearning parameter and provides the appropriate bindings.
 * 
 * Key Features:
 * - Backward compatibility with existing static preference system
 * - Seamless integration with RL learning components
 * - Thread-safe preference access during simulation
 * - Proper MATSim QSim-level binding override
 */
public class PrefCostQSimModuleWithRL extends AbstractDvrpModeQSimModule {
    
    private final boolean usePreferenceWeights;
    private final DynamicUserPreferenceStore dynamicStore;
    private final boolean enableDynamicLearning;
    
    public PrefCostQSimModuleWithRL(String mode, boolean usePreferenceWeights, 
                                   DynamicUserPreferenceStore dynamicStore,
                                   boolean enableDynamicLearning) {
        super(mode);
        this.usePreferenceWeights = usePreferenceWeights;
        this.dynamicStore = dynamicStore;
        this.enableDynamicLearning = enableDynamicLearning;
        
        System.out.println("PrefCostQSimModuleWithRL: Created for mode '" + mode + 
                          "' with preferences=" + usePreferenceWeights + 
                          ", dynamic learning=" + enableDynamicLearning);
    }
    
    @Override
    protected void configureQSim() {
        System.out.println("PrefCostQSimModuleWithRL: Configuring QSim-level bindings for mode: " + getMode());
        System.out.println("PrefCostQSimModuleWithRL: Using preference weights: " + usePreferenceWeights);
        System.out.println("PrefCostQSimModuleWithRL: Dynamic learning enabled: " + enableDynamicLearning);
        
        // Bind the preference-aware cost calculator at QSim level
        // This properly overrides the default binding from DrtModeOptimizerQSimModule
        bindModal(InsertionCostCalculator.class)
            .toProvider(this::createPrefAwareInsertionCostCalculator)
            .in(Singleton.class);
        
        System.out.println("PrefCostQSimModuleWithRL: Bound enhanced PrefAwareInsertionCostCalculator for mode: " + getMode());
    }
    
    private PrefAwareInsertionCostCalculator createPrefAwareInsertionCostCalculator() {
        // Create default calculator using the proper strategy pattern
        CostCalculationStrategy strategy = new CostCalculationStrategy.DiscourageSoftConstraintViolations();
        DefaultInsertionCostCalculator defaultCalculator = new DefaultInsertionCostCalculator(strategy);
        
        // Get preference store (dynamic or static)
        UserPreferenceStore store = provideUserPreferenceStore();
        
        // Create and return preference-aware calculator
        return new PrefAwareInsertionCostCalculator(defaultCalculator, store, usePreferenceWeights);
    }
    
    @Provides
    @Singleton
    public UserPreferenceStore provideUserPreferenceStore() {
        if (enableDynamicLearning && dynamicStore != null) {
            System.out.println("PrefCostQSimModuleWithRL: Using DynamicUserPreferenceStore for RL learning");
            
            // If dynamic store is empty, load initial preferences
            if (dynamicStore.getTotalUsers() == 0) {
                System.out.println("PrefCostQSimModuleWithRL: Loading initial preferences into dynamic store...");
                UserPreferenceStore staticStore = PreferenceDataLoader.loadAllPreferenceData();
                
                // Copy preferences from static store to dynamic store
                for (var personId : staticStore.getAllUserIds()) {
                    var pref = staticStore.getUserPreference(personId);
                    dynamicStore.addUserPreference(personId, pref);
                }
                
                System.out.println("PrefCostQSimModuleWithRL: Loaded " + dynamicStore.getTotalUsers() + 
                                  " initial preferences into dynamic store");
            }
            
            return dynamicStore;
            
        } else {
            System.out.println("PrefCostQSimModuleWithRL: Using static UserPreferenceStore (traditional mode)");
            UserPreferenceStore store = PreferenceDataLoader.loadAllPreferenceData();
            System.out.println("PrefCostQSimModuleWithRL: Loaded preferences for " + store.getTotalUsers() + " users");
            return store;
        }
    }
    
    /**
     * Get the dynamic preference store if available.
     * This allows other components to access the dynamic store for learning.
     */
    public DynamicUserPreferenceStore getDynamicStore() {
        return enableDynamicLearning ? dynamicStore : null;
    }
    
    /**
     * Check if dynamic learning is enabled
     */
    public boolean isDynamicLearningEnabled() {
        return enableDynamicLearning;
    }
}