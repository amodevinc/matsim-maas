package org.matsim.maas.preference;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.preference.data.PreferenceDataLoader;
import org.matsim.maas.preference.data.UserPreferenceStore;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;
import org.matsim.maas.preference.data.UserPreferenceStore.UserChoiceRecord;
import org.matsim.maas.preference.cost.PrefCostCalculator;

import java.util.List;

/**
 * Test class for validating preference data loading and cost calculation.
 * Provides smoke testing for Phase 2 implementation.
 */
public class PreferenceDataTest {
    
    public static void main(String[] args) {
        System.out.println("=== PREFERENCE DATA TEST ===");
        
        // Test 1: Load preference data
        System.out.println("\n1. Loading preference data...");
        UserPreferenceStore store = PreferenceDataLoader.loadAllPreferenceData();
        
        // Test 2: Check data integrity
        System.out.println("\n2. Checking data integrity...");
        testDataIntegrity(store);
        
        // Test 3: Test cost calculation
        System.out.println("\n3. Testing cost calculation...");
        testCostCalculation(store);
        
        // Test 4: Test preference-aware vs default costs
        System.out.println("\n4. Testing preference-aware vs default costs...");
        testPreferenceComparison(store);
        
        System.out.println("\n=== PREFERENCE DATA TEST COMPLETE ===");
    }
    
    private static void testDataIntegrity(UserPreferenceStore store) {
        System.out.println("Total users with preferences: " + store.getTotalUsers());
        System.out.println("Total users with choice history: " + store.getTotalUsersWithHistory());
        
        // Check a few sample users
        int sampleCount = 0;
        for (Id<Person> personId : store.getAllUserIds()) {
            if (sampleCount >= 3) break;
            
            UserPreferenceData prefData = store.getUserPreference(personId);
            List<UserChoiceRecord> choiceHistory = store.getUserChoiceHistory(personId);
            
            System.out.println("User " + personId + ": " + prefData);
            System.out.println("  Choice history: " + choiceHistory.size() + " records");
            
            if (!choiceHistory.isEmpty()) {
                System.out.println("  Sample choice: " + choiceHistory.get(0));
            }
            
            sampleCount++;
        }
    }
    
    private static void testCostCalculation(UserPreferenceStore store) {
        // Test utility calculation for a sample user
        Id<Person> samplePersonId = Id.createPersonId(1);
        UserPreferenceData prefData = store.getUserPreference(samplePersonId);
        
        if (prefData != null) {
            System.out.println("Sample user preferences: " + prefData);
            
            // Test utility calculation with sample time values
            double accessTime = 2.0; // 2 minutes access
            double waitTime = 5.0;   // 5 minutes wait
            double ivtTime = 10.0;   // 10 minutes in-vehicle
            double egressTime = 1.0; // 1 minute egress
            
            double utility = prefData.calculateUtility(accessTime, waitTime, ivtTime, egressTime);
            System.out.println("Sample utility calculation:");
            System.out.println("  Access: " + accessTime + " * " + prefData.getAccessWeight() + " = " + (accessTime * prefData.getAccessWeight()));
            System.out.println("  Wait: " + waitTime + " * " + prefData.getWaitWeight() + " = " + (waitTime * prefData.getWaitWeight()));
            System.out.println("  IVT: " + ivtTime + " * " + prefData.getIvtWeight() + " = " + (ivtTime * prefData.getIvtWeight()));
            System.out.println("  Egress: " + egressTime + " * " + prefData.getEgressWeight() + " = " + (egressTime * prefData.getEgressWeight()));
            System.out.println("  Total utility: " + utility);
        } else {
            System.out.println("No preference data found for sample user");
        }
    }
    
    private static void testPreferenceComparison(UserPreferenceStore store) {
        // Create preference-aware and default cost calculators
        PrefCostCalculator prefAwareCalculator = new PrefCostCalculator(store, true);
        PrefCostCalculator defaultCalculator = new PrefCostCalculator(store, false);
        
        System.out.println("Created calculators:");
        System.out.println("  Preference-aware: " + prefAwareCalculator.getStats());
        System.out.println("  Default: " + defaultCalculator.getStats());
        
        // Test with sample users
        int userCount = 0;
        for (Id<Person> personId : store.getAllUserIds()) {
            if (userCount >= 3) break;
            
            UserPreferenceData prefData = store.getUserPreference(personId);
            if (prefData != null) {
                System.out.println("User " + personId + " preferences: " + prefData);
                
                // Show how different users have different utility weights
                double sampleUtility = prefData.calculateUtility(2.0, 5.0, 10.0, 1.0);
                System.out.println("  Utility for standard trip: " + sampleUtility);
            }
            
            userCount++;
        }
    }
}