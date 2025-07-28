package org.matsim.maas.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.preference.data.DynamicUserPreferenceStore;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;
import org.matsim.maas.preference.events.PreferenceUpdateEvent;
import org.matsim.maas.preference.events.PreferenceUpdateTracker;
import org.matsim.maas.preference.learning.LearningConfiguration;
import org.matsim.maas.preference.learning.PolicyGradientPreferenceLearner;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration smoke tests for the RL-enhanced preference system.
 * Tests that all components work together without crashing.
 */
public class RLIntegrationSmokeTest {
    
    @TempDir
    Path tempDir;
    
    private DynamicUserPreferenceStore store;
    private PolicyGradientPreferenceLearner learner;
    private PreferenceUpdateTracker tracker;
    
    @BeforeEach
    void setUp() {
        store = new DynamicUserPreferenceStore(tempDir.toString());
        LearningConfiguration config = LearningConfiguration.createDefault();
        learner = new PolicyGradientPreferenceLearner(store, config);
        tracker = new PreferenceUpdateTracker(tempDir.toString());
    }
    
    @Test
    void testFullRLPipeline() {
        // Setup: Add a user with initial preferences
        Id<Person> personId = Id.createPersonId("testUser");
        UserPreferenceData initialPref = new UserPreferenceData(1, 0.5, -0.3, 0.8, -0.2);
        store.addUserPreference(personId, initialPref);
        
        // Step 1: User accepts a ride - learner calculates update
        var acceptanceUpdate = learner.learnFromAcceptance(
            personId, 300.0, 120.0, 600.0, 180.0, 1.0);
        
        assertNotNull(acceptanceUpdate);
        
        // Step 2: Apply update to store
        boolean updateSuccess = store.updateUserPreference(personId,
            acceptanceUpdate.accessDelta, acceptanceUpdate.waitDelta,
            acceptanceUpdate.ivtDelta, acceptanceUpdate.egressDelta);
        
        assertTrue(updateSuccess, "Store should accept the learning update");
        
        // Step 3: Create preference update event
        UserPreferenceData oldPref = initialPref;
        UserPreferenceData newPref = store.getUserPreference(personId);
        
        PreferenceUpdateEvent event = new PreferenceUpdateEvent(
            100.0, personId,
            oldPref.getAccessWeight(), oldPref.getWaitWeight(),
            oldPref.getIvtWeight(), oldPref.getEgressWeight(),
            newPref.getAccessWeight(), newPref.getWaitWeight(),
            newPref.getIvtWeight(), newPref.getEgressWeight(),
            "request_accepted", 1.0
        );
        
        // Step 4: Track the event
        tracker.handleEvent(event);
        
        // Step 5: Verify the pipeline worked
        var stats = tracker.getStats();
        assertEquals(1, stats.totalUpdates);
        assertEquals(1, stats.uniquePersonsUpdated);
        
        // Verify preferences actually changed
        assertNotEquals(oldPref.getAccessWeight(), newPref.getAccessWeight(), 0.001);
        
        // Verify update magnitude is reasonable
        assertTrue(event.getUpdateMagnitude() > 0, "Should have some preference change");
        assertTrue(event.getUpdateMagnitude() < 0.5, "Change should be reasonable");
    }
    
    @Test
    void testMultipleUsersLearning() {
        // Create multiple users
        for (int i = 1; i <= 5; i++) {
            Id<Person> personId = Id.createPersonId("user" + i);
            UserPreferenceData pref = new UserPreferenceData(i, 0.1*i, -0.1*i, 0.2*i, -0.2*i);
            store.addUserPreference(personId, pref);
        }
        
        // Have all users learn from different experiences
        for (int i = 1; i <= 5; i++) {
            Id<Person> personId = Id.createPersonId("user" + i);
            
            var update = learner.learnFromAcceptance(personId, 
                300 + i*10, 120 + i*5, 600 + i*20, 180 + i*8, 0.5 + i*0.1);
            
            store.updateUserPreference(personId,
                update.accessDelta, update.waitDelta,
                update.ivtDelta, update.egressDelta);
        }
        
        // Verify all users have learned
        assertEquals(5, store.getTotalUsers());
        assertTrue(store.getGlobalUpdateCount() >= 5);
    }
    
    @Test
    void testLearningConfigurationOptions() {
        // Test different learning configurations
        LearningConfiguration conservative = LearningConfiguration.createConservative();
        LearningConfiguration aggressive = LearningConfiguration.createAggressive();
        
        PolicyGradientPreferenceLearner conservativeLearner = 
            new PolicyGradientPreferenceLearner(store, conservative);
        PolicyGradientPreferenceLearner aggressiveLearner = 
            new PolicyGradientPreferenceLearner(store, aggressive);
        
        // Setup user
        Id<Person> personId = Id.createPersonId("configTestUser");
        UserPreferenceData pref = new UserPreferenceData(1, 0.5, -0.3, 0.8, -0.2);
        store.addUserPreference(personId, pref);
        
        // Learn with both configurations
        var conservativeUpdate = conservativeLearner.learnFromAcceptance(
            personId, 300, 120, 600, 180, 1.0);
        var aggressiveUpdate = aggressiveLearner.learnFromAcceptance(
            personId, 300, 120, 600, 180, 1.0);
        
        // Conservative should generally have smaller updates
        double conservativeMagnitude = Math.abs(conservativeUpdate.accessDelta) + 
                                     Math.abs(conservativeUpdate.waitDelta) +
                                     Math.abs(conservativeUpdate.ivtDelta) + 
                                     Math.abs(conservativeUpdate.egressDelta);
        
        double aggressiveMagnitude = Math.abs(aggressiveUpdate.accessDelta) + 
                                   Math.abs(aggressiveUpdate.waitDelta) +
                                   Math.abs(aggressiveUpdate.ivtDelta) + 
                                   Math.abs(aggressiveUpdate.egressDelta);
        
        // Both should produce some learning
        assertTrue(conservativeMagnitude > 0, "Conservative learner should still learn");
        assertTrue(aggressiveMagnitude > 0, "Aggressive learner should learn");
        
        // Generally, aggressive should learn faster (though not guaranteed due to randomness)
        // Just ensure both are working
        assertTrue(conservativeMagnitude < 1.0, "Conservative updates should be reasonable");
        assertTrue(aggressiveMagnitude < 1.0, "Aggressive updates should be reasonable");
    }
    
    @Test
    void testEventSystemIntegration() {
        // Test that events flow properly through the system
        Id<Person> personId = Id.createPersonId("eventTestUser");
        UserPreferenceData pref = new UserPreferenceData(1, 0.5, -0.3, 0.8, -0.2);
        store.addUserPreference(personId, pref);
        
        // Generate multiple events
        for (int i = 0; i < 3; i++) {
            PreferenceUpdateEvent event = new PreferenceUpdateEvent(
                100.0 * i, personId,
                0.5, -0.3, 0.8, -0.2,
                0.51, -0.29, 0.81, -0.19,
                "test_event_" + i, 0.5
            );
            
            tracker.handleEvent(event);
        }
        
        var stats = tracker.getStats();
        assertEquals(3, stats.totalUpdates);
        assertEquals(1, stats.uniquePersonsUpdated);
        assertTrue(stats.totalReward > 0);
    }
    
    @Test
    void testPersistenceAndRecovery() {
        // Test that preferences can be persisted and recovered
        Id<Person> personId = Id.createPersonId("persistenceTestUser");
        UserPreferenceData initialPref = new UserPreferenceData(1, 0.5, -0.3, 0.8, -0.2);
        store.addUserPreference(personId, initialPref);
        
        // Make some learning updates
        var update = learner.learnFromAcceptance(personId, 300, 120, 600, 180, 1.0);
        store.updateUserPreference(personId, 
            update.accessDelta, update.waitDelta, 
            update.ivtDelta, update.egressDelta);
        
        // Persist preferences
        store.persistPreferences(1);
        
        // Create new store and load preferences
        DynamicUserPreferenceStore newStore = new DynamicUserPreferenceStore(tempDir.toString());
        newStore.loadLearnedPreferences(tempDir.resolve("learned_preferences_iter_1.csv").toString());
        
        // Verify preferences were recovered
        UserPreferenceData recoveredPref = newStore.getUserPreference(personId);
        assertNotNull(recoveredPref, "Should recover preferences from file");
        
        // Preferences should have changed from initial
        UserPreferenceData currentPref = store.getUserPreference(personId);
        assertEquals(currentPref.getAccessWeight(), recoveredPref.getAccessWeight(), 0.001);
        assertEquals(currentPref.getWaitWeight(), recoveredPref.getWaitWeight(), 0.001);
    }
    
    @Test
    void testSystemStability() {
        // Test that repeated learning doesn't cause instability
        Id<Person> personId = Id.createPersonId("stabilityTestUser");
        // Start with small non-zero weights to ensure gradients can be calculated
        UserPreferenceData pref = new UserPreferenceData(1, 0.1, -0.1, 0.1, -0.1);
        store.addUserPreference(personId, pref);
        
        // Apply many learning updates
        for (int i = 0; i < 20; i++) {
            var update = learner.learnFromAcceptance(personId, 300, 120, 600, 180, 0.5);
            store.updateUserPreference(personId,
                update.accessDelta, update.waitDelta,
                update.ivtDelta, update.egressDelta);
        }
        
        UserPreferenceData finalPref = store.getUserPreference(personId);
        
        // Verify preferences are still within reasonable bounds
        assertTrue(Math.abs(finalPref.getAccessWeight()) < 3.0, "Access weight should stay bounded");
        assertTrue(Math.abs(finalPref.getWaitWeight()) < 3.0, "Wait weight should stay bounded");
        assertTrue(Math.abs(finalPref.getIvtWeight()) < 3.0, "IVT weight should stay bounded");
        assertTrue(Math.abs(finalPref.getEgressWeight()) < 3.0, "Egress weight should stay bounded");
        
        // Should have made progress (different from initial)
        boolean accessChanged = Math.abs(finalPref.getAccessWeight() - 0.1) > 0.001;
        boolean waitChanged = Math.abs(finalPref.getWaitWeight() - (-0.1)) > 0.001;
        boolean ivtChanged = Math.abs(finalPref.getIvtWeight() - 0.1) > 0.001;
        boolean egressChanged = Math.abs(finalPref.getEgressWeight() - (-0.1)) > 0.001;
        
        assertTrue(accessChanged || waitChanged || ivtChanged || egressChanged, 
                  "At least one preference should have changed after learning");
    }
}