package org.matsim.maas.preference.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.controler.events.IterationEndsEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PreferenceUpdateEvent and related components.
 */
public class PreferenceUpdateEventTest {
    
    @TempDir
    Path tempDir;
    
    private PreferenceUpdateEvent event;
    private Id<Person> personId;
    
    @BeforeEach
    void setUp() {
        personId = Id.createPersonId("testPerson");
        event = new PreferenceUpdateEvent(
            100.0, // time
            personId,
            0.5, -0.3, 0.8, -0.2, // old weights
            0.55, -0.31, 0.82, -0.19, // new weights
            "request_accepted",
            0.75 // learning reward
        );
    }
    
    @Test
    void testEventCreation() {
        assertEquals(100.0, event.getTime());
        assertEquals(personId, event.getPersonId());
        assertEquals("preferenceUpdate", event.getEventType());
        assertEquals("request_accepted", event.getUpdateReason());
        assertEquals(0.75, event.getLearningReward());
    }
    
    @Test
    void testOldWeights() {
        assertEquals(0.5, event.getOldAccessWeight());
        assertEquals(-0.3, event.getOldWaitWeight());
        assertEquals(0.8, event.getOldIvtWeight());
        assertEquals(-0.2, event.getOldEgressWeight());
    }
    
    @Test
    void testNewWeights() {
        assertEquals(0.55, event.getNewAccessWeight());
        assertEquals(-0.31, event.getNewWaitWeight());
        assertEquals(0.82, event.getNewIvtWeight());
        assertEquals(-0.19, event.getNewEgressWeight());
    }
    
    @Test
    void testWeightDeltas() {
        assertEquals(0.05, event.getAccessWeightDelta(), 0.001);
        assertEquals(-0.01, event.getWaitWeightDelta(), 0.001);
        assertEquals(0.02, event.getIvtWeightDelta(), 0.001);
        assertEquals(0.01, event.getEgressWeightDelta(), 0.001);
    }
    
    @Test
    void testUpdateMagnitude() {
        double expectedMagnitude = 0.05 + 0.01 + 0.02 + 0.01; // 0.09
        assertEquals(expectedMagnitude, event.getUpdateMagnitude(), 0.001);
    }
    
    @Test
    void testEventAttributes() {
        Map<String, String> attrs = event.getAttributes();
        
        assertTrue(attrs.containsKey("person"));
        assertTrue(attrs.containsKey("oldAccessWeight"));
        assertTrue(attrs.containsKey("newAccessWeight"));
        assertTrue(attrs.containsKey("updateReason"));
        assertTrue(attrs.containsKey("learningReward"));
        assertTrue(attrs.containsKey("updateMagnitude"));
        
        assertEquals("testPerson", attrs.get("person"));
        assertEquals("0.5", attrs.get("oldAccessWeight"));
        assertEquals("0.55", attrs.get("newAccessWeight"));
        assertEquals("request_accepted", attrs.get("updateReason"));
    }
    
    @Test
    void testPreferenceUpdateTracker() throws IOException {
        PreferenceUpdateTracker tracker = new PreferenceUpdateTracker(tempDir.toString());
        
        // Fire multiple events
        tracker.handleEvent(event);
        
        Id<Person> person2 = Id.createPersonId("person2");
        PreferenceUpdateEvent event2 = new PreferenceUpdateEvent(
            200.0, person2,
            0.1, 0.2, 0.3, 0.4,
            0.15, 0.18, 0.35, 0.38,
            "request_rejected",
            -0.5
        );
        tracker.handleEvent(event2);
        
        // Same person, second update
        PreferenceUpdateEvent event3 = new PreferenceUpdateEvent(
            300.0, personId,
            0.55, -0.31, 0.82, -0.19,
            0.56, -0.30, 0.83, -0.18,
            "trip_completed",
            1.2
        );
        tracker.handleEvent(event3);
        
        // Get statistics
        PreferenceUpdateTracker.PreferenceLearningStats stats = tracker.getStats();
        assertEquals(3, stats.totalUpdates);
        assertEquals(2, stats.uniquePersonsUpdated);
        assertEquals(0.75 + (-0.5) + 1.2, stats.totalReward, 0.001);
        
        // Test iteration end event
        IterationEndsEvent iterEvent = new IterationEndsEvent(null, 5, false);
        
        tracker.notifyIterationEnds(iterEvent);
        
        // Verify files were created
        Path updateLog = tempDir.resolve("preference_updates_iter_5.csv");
        assertTrue(Files.exists(updateLog));
        
        String content = Files.readString(updateLog);
        assertTrue(content.contains("time,person_id"));
        assertTrue(content.contains("testPerson"));
        assertTrue(content.contains("person2"));
        assertTrue(content.contains("request_accepted"));
        assertTrue(content.contains("request_rejected"));
        assertTrue(content.contains("trip_completed"));
        
        Path summaryFile = tempDir.resolve("preference_learning_summary.csv");
        assertTrue(Files.exists(summaryFile));
        
        Path personStats = tempDir.resolve("preference_person_stats_iter_5.csv");
        assertTrue(Files.exists(personStats));
        
        String personStatsContent = Files.readString(personStats);
        assertTrue(personStatsContent.contains("person_id,num_updates"));
        assertTrue(personStatsContent.contains("testPerson,2")); // 2 updates for testPerson
        assertTrue(personStatsContent.contains("person2,1")); // 1 update for person2
    }
    
    @Test
    void testTrackerDirectHandling() {
        PreferenceUpdateTracker tracker = new PreferenceUpdateTracker(tempDir.toString());
        
        // Process event directly
        tracker.handleEvent(event);
        
        PreferenceUpdateTracker.PreferenceLearningStats stats = tracker.getStats();
        assertEquals(1, stats.totalUpdates);
        assertEquals("request_accepted", stats.updateReasonCounts.keySet().iterator().next());
    }
    
    @Test
    void testUpdateReasonCounting() {
        PreferenceUpdateTracker tracker = new PreferenceUpdateTracker(tempDir.toString());
        
        // Fire events with different reasons
        for (int i = 0; i < 5; i++) {
            tracker.handleEvent(new PreferenceUpdateEvent(
                100.0 * i, personId,
                0.5, 0.5, 0.5, 0.5,
                0.51, 0.51, 0.51, 0.51,
                "request_accepted",
                0.5
            ));
        }
        
        for (int i = 0; i < 3; i++) {
            tracker.handleEvent(new PreferenceUpdateEvent(
                100.0 * i, personId,
                0.5, 0.5, 0.5, 0.5,
                0.49, 0.49, 0.49, 0.49,
                "request_rejected",
                -0.5
            ));
        }
        
        for (int i = 0; i < 2; i++) {
            tracker.handleEvent(new PreferenceUpdateEvent(
                100.0 * i, personId,
                0.5, 0.5, 0.5, 0.5,
                0.52, 0.52, 0.52, 0.52,
                "trip_completed",
                1.0
            ));
        }
        
        PreferenceUpdateTracker.PreferenceLearningStats stats = tracker.getStats();
        assertEquals(10, stats.totalUpdates);
        assertEquals(5, stats.updateReasonCounts.get("request_accepted").intValue());
        assertEquals(3, stats.updateReasonCounts.get("request_rejected").intValue());
        assertEquals(2, stats.updateReasonCounts.get("trip_completed").intValue());
    }
    
    @Test
    void testAverageMetrics() {
        PreferenceUpdateTracker tracker = new PreferenceUpdateTracker(tempDir.toString());
        
        tracker.handleEvent(new PreferenceUpdateEvent(
            100.0, personId,
            0.0, 0.0, 0.0, 0.0,
            0.1, 0.1, 0.1, 0.1,
            "test", 2.0
        ));
        
        tracker.handleEvent(new PreferenceUpdateEvent(
            200.0, personId,
            0.1, 0.1, 0.1, 0.1,
            0.2, 0.2, 0.2, 0.2,
            "test", 3.0
        ));
        
        PreferenceUpdateTracker.PreferenceLearningStats stats = tracker.getStats();
        assertEquals(2.5, stats.getAverageReward(), 0.001); // (2.0 + 3.0) / 2
        assertEquals(0.4, stats.getAverageMagnitude(), 0.001); // Each update has magnitude 0.4
    }
}