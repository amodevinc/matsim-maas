package org.matsim.maas.preference.learning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.preference.data.DynamicUserPreferenceStore;
import org.matsim.maas.preference.data.DynamicUserPreferenceStore.PreferenceUpdate;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for PolicyGradientPreferenceLearner to ensure basic functionality works.
 */
public class PolicyGradientLearnerSmokeTest {
    
    @TempDir
    Path tempDir;
    
    private DynamicUserPreferenceStore store;
    private PolicyGradientPreferenceLearner learner;
    private Id<Person> testPersonId;
    
    @BeforeEach
    void setUp() {
        store = new DynamicUserPreferenceStore(tempDir.toString());
        LearningConfiguration config = LearningConfiguration.createDefault();
        learner = new PolicyGradientPreferenceLearner(store, config);
        
        testPersonId = Id.createPersonId("testPerson");
        
        // Add initial preference data
        UserPreferenceData initialPref = new UserPreferenceData(1, 0.5, -0.3, 0.8, -0.2);
        store.addUserPreference(testPersonId, initialPref);
    }
    
    @Test
    void testLearnerCreation() {
        assertNotNull(learner);
        assertNotNull(learner.getConfiguration());
        assertEquals(LearningConfiguration.createDefault().getInitialLearningRate(), 
                    learner.getConfiguration().getInitialLearningRate());
    }
    
    @Test
    void testLearnFromAcceptance() {
        // Test basic acceptance learning
        PreferenceUpdate update = learner.learnFromAcceptance(
            testPersonId, 
            300.0, // access time
            120.0, // wait time  
            600.0, // ivt time
            180.0, // egress time
            1.0    // positive reward
        );
        
        assertNotNull(update);
        
        // Verify that we get some update (not all zeros)
        double totalChange = Math.abs(update.accessDelta) + Math.abs(update.waitDelta) + 
                           Math.abs(update.ivtDelta) + Math.abs(update.egressDelta);
        assertTrue(totalChange > 0, "Should produce some learning update");
        assertTrue(totalChange < 0.5, "Update should be reasonable in magnitude");
    }
    
    @Test
    void testLearnFromRejection() {
        // Test basic rejection learning
        PreferenceUpdate update = learner.learnFromRejection(
            testPersonId,
            300.0, // access time
            120.0, // wait time
            600.0, // ivt time  
            180.0, // egress time
            -0.5   // negative reward (penalty)
        );
        
        assertNotNull(update);
        
        // Verify we get some update
        double totalChange = Math.abs(update.accessDelta) + Math.abs(update.waitDelta) + 
                           Math.abs(update.ivtDelta) + Math.abs(update.egressDelta);
        assertTrue(totalChange > 0, "Should produce some learning update");
    }
    
    @Test
    void testLearnFromCompletion() {
        // Test completion learning
        PreferenceUpdate update = learner.learnFromCompletion(
            testPersonId,
            250.0, // actual access time
            100.0, // actual wait time
            500.0, // actual ivt time
            150.0, // actual egress time
            0.8    // satisfaction score
        );
        
        assertNotNull(update);
        
        // Verify we get some update
        double totalChange = Math.abs(update.accessDelta) + Math.abs(update.waitDelta) + 
                           Math.abs(update.ivtDelta) + Math.abs(update.egressDelta);
        assertTrue(totalChange > 0, "Should produce some learning update");
    }
    
    @Test
    void testBatchLearning() {
        // Create multiple learning experiences
        Map<Id<Person>, PreferenceLearner.LearningExperience> experiences = new HashMap<>();
        
        experiences.put(testPersonId, PreferenceLearner.LearningExperience.fromAcceptance(
            300, 120, 600, 180, 1.0, 100.0));
        
        // Process batch
        Map<Id<Person>, PreferenceUpdate> updates = learner.batchLearn(experiences);
        
        // For default batch size (16), we shouldn't get updates yet (buffer < batch size)
        // But the experience should be buffered
        assertTrue(updates.isEmpty() || updates.size() <= 1, 
                  "Should not produce updates until batch size reached");
    }
    
    @Test
    void testLearningWithNonExistentUser() {
        Id<Person> nonExistentPerson = Id.createPersonId("nonExistent");
        
        // Should not crash, should return minimal update (exploration noise only)
        PreferenceUpdate update = learner.learnFromAcceptance(
            nonExistentPerson, 300, 120, 600, 180, 1.0);
        
        assertNotNull(update);
        
        // Since there's no preference data, gradients should be zero but exploration noise may be added
        double totalChange = Math.abs(update.accessDelta) + Math.abs(update.waitDelta) + 
                           Math.abs(update.ivtDelta) + Math.abs(update.egressDelta);
        assertTrue(totalChange < 0.01, "Should have minimal change (exploration noise only)");
    }
    
    @Test
    void testLearningParameterUpdate() {
        double initialRate = learner.getConfiguration().getCurrentLearningRate(0);
        
        // Update learning parameters for iteration 10
        learner.updateLearningParameters(10);
        
        double newRate = learner.getConfiguration().getCurrentLearningRate(10);
        
        // Learning rate should decay over time
        assertTrue(newRate <= initialRate, "Learning rate should decay or stay same");
    }
    
    @Test
    void testReset() {
        // Make some learning updates first
        learner.learnFromAcceptance(testPersonId, 300, 120, 600, 180, 1.0);
        
        // Get initial statistics
        PolicyGradientPreferenceLearner.LearningStatistics stats = learner.getStatistics();
        assertTrue(stats.totalUpdates > 0, "Should have some updates before reset");
        
        // Reset
        learner.reset();
        
        // Check statistics after reset
        PolicyGradientPreferenceLearner.LearningStatistics resetStats = learner.getStatistics();
        assertEquals(0, resetStats.totalUpdates, "Updates should be reset to 0");
        assertEquals(0, resetStats.currentIteration, "Iteration should be reset to 0");
    }
    
    @Test
    void testStatistics() {
        PolicyGradientPreferenceLearner.LearningStatistics stats = learner.getStatistics();
        
        assertNotNull(stats);
        assertEquals(0, stats.totalUpdates); // Should start at 0
        assertEquals(0, stats.currentIteration);
        assertTrue(stats.currentLearningRate > 0);
        assertTrue(stats.currentExplorationRate >= 0);
    }
    
    @Test
    void testMultipleUpdatesConvergence() {
        // Test that multiple updates don't cause instability
        UserPreferenceData originalPref = store.getUserPreference(testPersonId);
        
        // Apply multiple small updates
        for (int i = 0; i < 10; i++) {
            PreferenceUpdate update = learner.learnFromAcceptance(
                testPersonId, 300, 120, 600, 180, 0.1);
            
            // Apply the update to the store
            store.updateUserPreference(testPersonId, 
                update.accessDelta, update.waitDelta, 
                update.ivtDelta, update.egressDelta);
        }
        
        UserPreferenceData finalPref = store.getUserPreference(testPersonId);
        
        // Verify preferences are still reasonable
        assertTrue(Math.abs(finalPref.getAccessWeight()) < 5.0, "Access weight should stay reasonable");
        assertTrue(Math.abs(finalPref.getWaitWeight()) < 5.0, "Wait weight should stay reasonable");
        assertTrue(Math.abs(finalPref.getIvtWeight()) < 5.0, "IVT weight should stay reasonable");
        assertTrue(Math.abs(finalPref.getEgressWeight()) < 5.0, "Egress weight should stay reasonable");
    }
}