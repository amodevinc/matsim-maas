package org.matsim.maas.preference.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for DynamicUserPreferenceStore.
 * Tests thread safety, update constraints, persistence, and history tracking.
 */
public class DynamicUserPreferenceStoreTest {
    
    @TempDir
    Path tempDir;
    
    private DynamicUserPreferenceStore store;
    private Id<Person> testPersonId;
    private UserPreferenceData initialPref;
    
    @BeforeEach
    void setUp() {
        store = new DynamicUserPreferenceStore(tempDir.toString());
        testPersonId = Id.createPersonId("testPerson1");
        
        // Initialize with test preference data
        initialPref = new UserPreferenceData(1, 0.5, -0.3, 0.8, -0.2);
        store.addUserPreference(testPersonId, initialPref);
    }
    
    @Test
    void testBasicPreferenceUpdate() {
        // Test basic update functionality
        boolean result = store.updateUserPreference(testPersonId, 0.05, -0.02, 0.03, -0.01);
        
        assertTrue(result, "Update should succeed");
        
        UserPreferenceData updated = store.getUserPreference(testPersonId);
        assertNotNull(updated);
        assertEquals(0.55, updated.getAccessWeight(), 0.001);
        assertEquals(-0.32, updated.getWaitWeight(), 0.001);
        assertEquals(0.83, updated.getIvtWeight(), 0.001);
        assertEquals(-0.21, updated.getEgressWeight(), 0.001);
    }
    
    @Test
    void testUpdateConstraints() {
        // Test that updates are constrained to maxWeightChange (10%)
        boolean result = store.updateUserPreference(testPersonId, 0.5, -0.5, 0.5, -0.5);
        
        assertTrue(result);
        
        UserPreferenceData updated = store.getUserPreference(testPersonId);
        // Should be clamped to ±0.1 change
        assertEquals(0.6, updated.getAccessWeight(), 0.001);
        assertEquals(-0.4, updated.getWaitWeight(), 0.001);
        assertEquals(0.9, updated.getIvtWeight(), 0.001);
        assertEquals(-0.3, updated.getEgressWeight(), 0.001);
    }
    
    @Test
    void testWeightBounds() {
        // Add a user with extreme initial weights
        Id<Person> extremeUser = Id.createPersonId("extremeUser");
        UserPreferenceData extremePref = new UserPreferenceData(2, 1.95, -1.95, 0.0, 0.0);
        store.addUserPreference(extremeUser, extremePref);
        
        // Try to push beyond bounds
        store.updateUserPreference(extremeUser, 0.1, -0.1, 2.5, -2.5);
        
        UserPreferenceData updated = store.getUserPreference(extremeUser);
        // Should be clamped to [-2.0, 2.0]
        assertEquals(2.0, updated.getAccessWeight(), 0.001);
        assertEquals(-2.0, updated.getWaitWeight(), 0.001);
        assertTrue(updated.getIvtWeight() <= 2.0);
        assertTrue(updated.getEgressWeight() >= -2.0);
    }
    
    @Test
    void testUpdateNonExistentUser() {
        Id<Person> nonExistent = Id.createPersonId("nonExistent");
        boolean result = store.updateUserPreference(nonExistent, 0.1, 0.1, 0.1, 0.1);
        
        assertFalse(result, "Update should fail for non-existent user");
    }
    
    @Test
    void testBatchUpdate() {
        // Create multiple users
        Map<Id<Person>, DynamicUserPreferenceStore.PreferenceUpdate> updates = new HashMap<>();
        
        for (int i = 1; i <= 5; i++) {
            Id<Person> personId = Id.createPersonId("person" + i);
            store.addUserPreference(personId, new UserPreferenceData(i, 0.1*i, -0.1*i, 0.2*i, -0.2*i));
            
            updates.put(personId, new DynamicUserPreferenceStore.PreferenceUpdate(0.01, -0.01, 0.02, -0.02));
        }
        
        // Batch update
        store.batchUpdatePreferences(updates);
        
        // Verify all updates
        for (int i = 1; i <= 5; i++) {
            Id<Person> personId = Id.createPersonId("person" + i);
            UserPreferenceData pref = store.getUserPreference(personId);
            assertEquals(0.1*i + 0.01, pref.getAccessWeight(), 0.001);
            assertEquals(-0.1*i - 0.01, pref.getWaitWeight(), 0.001);
        }
    }
    
    @Test
    void testThreadSafety() throws InterruptedException {
        // Test concurrent updates
        int numThreads = 10;
        int updatesPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        // Create users for each thread
        for (int i = 0; i < numThreads; i++) {
            Id<Person> personId = Id.createPersonId("thread" + i);
            store.addUserPreference(personId, new UserPreferenceData(i, 0.0, 0.0, 0.0, 0.0));
            
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    Id<Person> myPersonId = Id.createPersonId("thread" + threadId);
                    for (int j = 0; j < updatesPerThread; j++) {
                        boolean success = store.updateUserPreference(myPersonId, 0.001, 0.001, 0.001, 0.001);
                        if (success) successCount.incrementAndGet();
                        
                        // Also do some reads
                        UserPreferenceData pref = store.getUserPreference(myPersonId);
                        assertNotNull(pref);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown(); // Start all threads
        assertTrue(endLatch.await(10, TimeUnit.SECONDS), "All threads should complete");
        
        executor.shutdown();
        
        // Verify results
        assertEquals(numThreads * updatesPerThread, successCount.get());
        
        // Each thread should have made exactly updatesPerThread updates
        for (int i = 0; i < numThreads; i++) {
            Id<Person> personId = Id.createPersonId("thread" + i);
            UserPreferenceData pref = store.getUserPreference(personId);
            
            // Each update adds 0.001, but capped at 0.1 per update
            // With 100 updates of 0.001 each = 0.1 total
            assertEquals(0.1, pref.getAccessWeight(), 0.001);
        }
    }
    
    @Test
    void testUpdateHistory() {
        // Make several updates
        store.updateUserPreference(testPersonId, 0.05, -0.02, 0.03, -0.01);
        store.updateUserPreference(testPersonId, 0.03, -0.01, 0.02, -0.02);
        store.updateUserPreference(testPersonId, -0.02, 0.03, -0.01, 0.01);
        
        DynamicUserPreferenceStore.PreferenceUpdateHistory history = store.getUpdateHistory(testPersonId);
        assertNotNull(history);
        assertEquals(3, history.getUpdateCount());
        
        // Verify cumulative changes are tracked
        assertTrue(history.getTotalAccessChange() > 0);
        assertTrue(history.getTotalWaitChange() > 0);
        assertTrue(history.getTotalIvtChange() > 0);
        assertTrue(history.getTotalEgressChange() > 0);
        
        // Average change magnitude should be reasonable
        double avgChange = history.getAverageChangeMagnitude();
        assertTrue(avgChange > 0 && avgChange < 0.1);
    }
    
    @Test
    void testPersistence() throws IOException {
        // Make some updates
        store.updateUserPreference(testPersonId, 0.05, -0.02, 0.03, -0.01);
        
        Id<Person> person2 = Id.createPersonId("person2");
        store.addUserPreference(person2, new UserPreferenceData(2, 0.1, 0.2, 0.3, 0.4));
        store.updateUserPreference(person2, -0.01, -0.02, -0.03, -0.04);
        
        // Persist preferences
        store.persistPreferences(10);
        
        // Verify file was created
        Path expectedFile = tempDir.resolve("learned_preferences_iter_10.csv");
        assertTrue(Files.exists(expectedFile));
        
        // Read and verify content
        String content = Files.readString(expectedFile);
        assertTrue(content.contains("id,access,wait,ivt,egress,update_count"));
        assertTrue(content.contains("testPerson1"));
        assertTrue(content.contains("person2"));
        
        // Verify update counts are included
        assertTrue(content.contains(",1\n") || content.contains(",1\r\n")); // testPerson1 has 1 update
    }
    
    @Test
    void testLoadLearnedPreferences() throws IOException {
        // Create a preferences file
        Path prefsFile = tempDir.resolve("test_prefs.csv");
        String content = "id,access,wait,ivt,egress\n" +
                        "3,0.123,0.456,0.789,-0.321\n" +
                        "4,-0.111,-0.222,-0.333,-0.444\n";
        Files.writeString(prefsFile, content);
        
        // Clear store and load preferences
        store.clear();
        store.loadLearnedPreferences(prefsFile.toString());
        
        // Verify loaded preferences
        Id<Person> person3 = Id.createPersonId("3");
        Id<Person> person4 = Id.createPersonId("4");
        
        UserPreferenceData pref3 = store.getUserPreference(person3);
        assertNotNull(pref3);
        assertEquals(0.123, pref3.getAccessWeight(), 0.001);
        assertEquals(0.456, pref3.getWaitWeight(), 0.001);
        
        UserPreferenceData pref4 = store.getUserPreference(person4);
        assertNotNull(pref4);
        assertEquals(-0.111, pref4.getAccessWeight(), 0.001);
        assertEquals(-0.222, pref4.getWaitWeight(), 0.001);
    }
    
    @Test
    void testExportUpdateStatistics() throws IOException {
        // Make various updates
        for (int i = 0; i < 5; i++) {
            store.updateUserPreference(testPersonId, 0.01, -0.01, 0.02, -0.02);
        }
        
        Id<Person> person2 = Id.createPersonId("person2");
        store.addUserPreference(person2, new UserPreferenceData(2, 0.0, 0.0, 0.0, 0.0));
        store.updateUserPreference(person2, 0.1, 0.0, 0.0, 0.0);
        
        // Export statistics
        Path statsFile = tempDir.resolve("update_stats.csv");
        store.exportUpdateStatistics(statsFile.toString());
        
        // Verify file content
        assertTrue(Files.exists(statsFile));
        String content = Files.readString(statsFile);
        assertTrue(content.contains("person_id,update_count"));
        assertTrue(content.contains("testPerson1,5")); // 5 updates
        assertTrue(content.contains("person2,1")); // 1 update
    }
    
    @Test
    void testGlobalUpdateCounter() {
        int initialCount = store.getGlobalUpdateCount();
        
        // Make updates
        store.updateUserPreference(testPersonId, 0.01, 0.01, 0.01, 0.01);
        assertEquals(initialCount + 1, store.getGlobalUpdateCount());
        
        // Batch update
        Map<Id<Person>, DynamicUserPreferenceStore.PreferenceUpdate> updates = new HashMap<>();
        updates.put(testPersonId, new DynamicUserPreferenceStore.PreferenceUpdate(0.01, 0.01, 0.01, 0.01));
        
        Id<Person> person2 = Id.createPersonId("person2");
        store.addUserPreference(person2, new UserPreferenceData(2, 0.0, 0.0, 0.0, 0.0));
        updates.put(person2, new DynamicUserPreferenceStore.PreferenceUpdate(0.01, 0.01, 0.01, 0.01));
        
        store.batchUpdatePreferences(updates);
        assertEquals(initialCount + 3, store.getGlobalUpdateCount()); // +1 from single, +2 from batch
    }
}