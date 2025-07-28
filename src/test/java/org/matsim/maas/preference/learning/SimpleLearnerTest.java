package org.matsim.maas.preference.learning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.preference.data.DynamicUserPreferenceStore;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to debug learning behavior
 */
public class SimpleLearnerTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testBasicLearning() {
        // Create store and learner
        DynamicUserPreferenceStore store = new DynamicUserPreferenceStore(tempDir.toString());
        LearningConfiguration config = new LearningConfiguration.Builder()
            .initialLearningRate(0.1)  // Higher learning rate
            .explorationRate(0.0)      // No exploration noise
            .maxWeightChange(0.5)      // Allow larger changes
            .build();
        
        PolicyGradientPreferenceLearner learner = new PolicyGradientPreferenceLearner(store, config);
        
        // Add user with clear preferences
        Id<Person> personId = Id.createPersonId("testUser");
        UserPreferenceData initialPref = new UserPreferenceData(1, 0.5, -0.3, 0.8, -0.2);
        store.addUserPreference(personId, initialPref);
        
        System.out.println("Initial preferences: " + initialPref);
        
        // Test learning from acceptance with large reward
        var update = learner.learnFromAcceptance(personId, 300, 120, 600, 180, 10.0);
        
        System.out.println("Learning update: access=" + update.accessDelta + 
                          ", wait=" + update.waitDelta + 
                          ", ivt=" + update.ivtDelta + 
                          ", egress=" + update.egressDelta);
        
        // Verify we get some update
        double totalChange = Math.abs(update.accessDelta) + Math.abs(update.waitDelta) + 
                           Math.abs(update.ivtDelta) + Math.abs(update.egressDelta);
        
        System.out.println("Total change magnitude: " + totalChange);
        
        assertTrue(totalChange > 0.0001, "Should produce some learning update");
        
        // Apply update
        boolean success = store.updateUserPreference(personId, 
            update.accessDelta, update.waitDelta, update.ivtDelta, update.egressDelta);
        
        assertTrue(success, "Update should be successful");
        
        UserPreferenceData finalPref = store.getUserPreference(personId);
        System.out.println("Final preferences: " + finalPref);
        
        // Check if anything changed
        boolean changed = Math.abs(finalPref.getAccessWeight() - initialPref.getAccessWeight()) > 0.0001 ||
                         Math.abs(finalPref.getWaitWeight() - initialPref.getWaitWeight()) > 0.0001 ||
                         Math.abs(finalPref.getIvtWeight() - initialPref.getIvtWeight()) > 0.0001 ||
                         Math.abs(finalPref.getEgressWeight() - initialPref.getEgressWeight()) > 0.0001;
        
        assertTrue(changed, "At least one preference should have changed");
    }
}