package org.matsim.maas.preference.cost;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.matsim.contrib.drt.optimizer.insertion.InsertionCostCalculator;
import org.matsim.contrib.drt.optimizer.insertion.DefaultInsertionCostCalculator;
import org.matsim.contrib.drt.optimizer.insertion.CostCalculationStrategy;
import org.matsim.contrib.dvrp.run.AbstractDvrpModeQSimModule;
import org.matsim.maas.preference.data.PreferenceDataLoader;
import org.matsim.maas.preference.data.UserPreferenceStore;

/**
 * QSim module for preference-aware cost calculation.
 * This module operates at the QSim level to properly override the default
 * InsertionCostCalculator binding from DrtModeOptimizerQSimModule.
 * 
 * Follows MATSim customization guidelines for QSim-level overrides:
 * - Extends AbstractDvrpModeQSimModule for proper DRT QSim integration
 * - Uses configureQSim() method to bind modal components
 * - Properly overrides default DRT optimizer bindings
 */
public class PrefCostQSimModule extends AbstractDvrpModeQSimModule {
    
    private final boolean usePreferenceWeights;
    
    public PrefCostQSimModule(String mode, boolean usePreferenceWeights) {
        super(mode);
        this.usePreferenceWeights = usePreferenceWeights;
        System.out.println("PrefCostQSimModule: Created for mode '" + mode + "' with preference weights: " + usePreferenceWeights);
    }
    
    @Override
    protected void configureQSim() {
        System.out.println("PrefCostQSimModule: Configuring QSim-level bindings for mode: " + getMode());
        System.out.println("PrefCostQSimModule: Using preference weights: " + usePreferenceWeights);
        
        // Bind the preference-aware cost calculator at QSim level
        // This properly overrides the default binding from DrtModeOptimizerQSimModule
        bindModal(InsertionCostCalculator.class)
            .toProvider(this::createPrefAwareInsertionCostCalculator)
            .in(Singleton.class);
        
        System.out.println("PrefCostQSimModule: Bound PrefAwareInsertionCostCalculator as InsertionCostCalculator for mode: " + getMode());
    }
    
    private PrefAwareInsertionCostCalculator createPrefAwareInsertionCostCalculator() {
        // Create default calculator using the proper strategy pattern
        // Use the standard DiscourageSoftConstraintViolations strategy
        CostCalculationStrategy strategy = new CostCalculationStrategy.DiscourageSoftConstraintViolations();
        DefaultInsertionCostCalculator defaultCalculator = new DefaultInsertionCostCalculator(strategy);
        
        // Create preference store
        UserPreferenceStore store = provideUserPreferenceStore();
        
        // Create and return preference-aware calculator
        return new PrefAwareInsertionCostCalculator(defaultCalculator, store, usePreferenceWeights);
    }
    
    @Provides
    @Singleton
    public UserPreferenceStore provideUserPreferenceStore() {
        System.out.println("PrefCostQSimModule: Loading user preference data...");
        UserPreferenceStore store = PreferenceDataLoader.loadAllPreferenceData();
        System.out.println("PrefCostQSimModule: Loaded preferences for " + store.getTotalUsers() + " users");
        return store;
    }
} 