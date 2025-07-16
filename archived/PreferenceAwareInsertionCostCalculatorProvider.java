package org.matsim.maas.utils;

import org.matsim.contrib.drt.optimizer.insertion.InsertionCostCalculator;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Provider for the PreferenceAwareInsertionCostCalculator to handle dependency injection.
 */
public class PreferenceAwareInsertionCostCalculatorProvider implements Provider<InsertionCostCalculator> {
    
    private final PreferenceDataLoader preferenceLoader;
    
    @Inject
    public PreferenceAwareInsertionCostCalculatorProvider(PreferenceDataLoader preferenceLoader) {
        this.preferenceLoader = preferenceLoader;
        System.out.println("🎯 PreferenceAwareInsertionCostCalculatorProvider created");
    }
    
    @Override
    public InsertionCostCalculator get() {
        System.out.println("🎯 Creating PreferenceAwareInsertionCostCalculator via provider");
        
        // Create a simplified stop finder for the calculator
        PreferenceAwareStopFinder stopFinder = new PreferenceAwareStopFinder(preferenceLoader);
        
        return new PreferenceAwareInsertionCostCalculator(preferenceLoader, stopFinder);
    }
}