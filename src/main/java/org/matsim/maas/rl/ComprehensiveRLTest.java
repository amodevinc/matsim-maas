package org.matsim.maas.rl;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.preference.data.PreferenceDataLoader;
import org.matsim.maas.preference.data.UserPreferenceStore;
import org.matsim.maas.rl.events.DrtRequestRecord;
import org.matsim.maas.rl.events.PersonTripRecord;
import org.matsim.maas.rl.events.PrefRLEventHandler;
import org.matsim.maas.rl.events.RLState;
import org.matsim.maas.rl.reward.RewardCalculator;
import org.matsim.maas.rl.state.RLStateManager;
import org.matsim.maas.rl.state.StateActionRecord;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive testing suite for the RL system.
 * Tests all components, edge cases, and integration scenarios.
 */
public class ComprehensiveRLTest {
    
    private static UserPreferenceStore preferenceStore;
    private static int testsPassed = 0;
    private static int testsTotal = 0;
    private static List<String> failedTests = new ArrayList<>();
    
    public static void main(String[] args) {
        System.out.println("=== COMPREHENSIVE RL SYSTEM TEST ===");
        
        // Load preference data once
        preferenceStore = PreferenceDataLoader.loadAllPreferenceData();
        
        // Run all test suites
        testStateRepresentation();
        testStateManagement();
        testRewardCalculation();
        testEventHandling();
        testDataExport();
        testEdgeCases();
        testIntegration();
        testPerformance();
        
        // Print summary
        printTestSummary();
    }
    
    private static void testStateRepresentation() {
        System.out.println("\n1. TESTING STATE REPRESENTATION");
        
        // Test 1.1: State creation and feature vector
        testCase("State creation and basic features", () -> {
            RLState state = new RLState(7200, 5, 8, 2, 180.0, 1, 25, 30);
            
            double[] features = state.toFeatureVector();
            assertEquals(features.length, 10, "Feature vector length");
            assertEquals(state.getHourOfDay(), 2, "Hour calculation");
            assertEquals(state.isRushHour(), false, "Rush hour detection (2 AM)");
            assertTrue(state.getSystemLoad() > 0, "System load calculation");
            
            return true;
        });
        
        // Test 1.2: Rush hour detection
        testCase("Rush hour detection", () -> {
            RLState morningRush = new RLState(8.5 * 3600, 10, 5, 5, 300, 2, 10, 15);
            RLState eveningRush = new RLState(18 * 3600, 8, 6, 4, 250, 1, 20, 25);
            RLState offPeak = new RLState(14 * 3600, 3, 8, 2, 120, 0, 12, 18);
            
            assertTrue(morningRush.isRushHour(), "Morning rush detection");
            assertTrue(eveningRush.isRushHour(), "Evening rush detection");
            assertFalse(offPeak.isRushHour(), "Off-peak detection");
            
            return true;
        });
        
        // Test 1.3: Feature normalization
        testCase("Feature normalization", () -> {
            RLState state = new RLState(12 * 3600, 25, 15, 5, 450, 8, 36, 60);
            double[] features = state.toFeatureVector();
            
            for (int i = 0; i < features.length; i++) {
                assertTrue(features[i] >= 0 && features[i] <= 1, 
                    "Feature " + i + " normalized: " + features[i]);
            }
            
            return true;
        });
        
        // Test 1.4: Discrete state encoding
        testCase("Discrete state encoding", () -> {
            RLState state1 = new RLState(8 * 3600, 5, 10, 0, 200, 0, 10, 20);
            RLState state2 = new RLState(8 * 3600, 5, 10, 0, 200, 0, 10, 20);
            RLState state3 = new RLState(14 * 3600, 15, 5, 5, 400, 5, 30, 40);
            
            assertEquals(state1.getDiscreteStateId(), state2.getDiscreteStateId(), 
                "Identical states have same discrete ID");
            assertNotEquals(state1.getDiscreteStateId(), state3.getDiscreteStateId(), 
                "Different states have different discrete IDs");
            
            return true;
        });
    }
    
    private static void testStateManagement() {
        System.out.println("\n2. TESTING STATE MANAGEMENT");
        
        // Test 2.1: State transitions
        testCase("State transitions", () -> {
            RLStateManager manager = new RLStateManager();
            double time = 8 * 3600;
            
            // Initial state
            RLState initial = manager.getCurrentState(time);
            assertEquals(initial.getActiveRequests(), 0, "Initial active requests");
            
            // Request submission
            Id<Person> person1 = Id.createPersonId("test1");
            manager.recordRequestSubmission(person1, initial, time);
            
            // Check state evolution
            RLState afterSubmission = manager.getCurrentState(time + 1);
            assertEquals(afterSubmission.getActiveRequests(), 1, "Active requests after submission");
            
            return true;
        });
        
        // Test 2.2: History tracking
        testCase("History tracking", () -> {
            RLStateManager manager = new RLStateManager();
            Id<Person> person = Id.createPersonId("historyTest");
            double time = 9 * 3600;
            
            // Record multiple events
            RLState state1 = manager.getCurrentState(time);
            manager.recordRequestSubmission(person, state1, time);
            
            RLState state2 = manager.getCurrentState(time + 120);
            manager.recordSchedulingDecision(person, state2, 5.0, time + 120);
            
            RLState state3 = manager.getCurrentState(time + 720);
            manager.recordTripCompletion(person, state3, 10.0, time + 720);
            
            // Check history
            List<StateActionRecord> history = manager.getPersonHistory(person);
            assertEquals(history.size(), 3, "Person history length");
            assertEquals(history.get(0).getAction(), "REQUEST_SUBMITTED", "First action");
            assertEquals(history.get(1).getAction(), "REQUEST_SCHEDULED", "Second action");
            assertEquals(history.get(2).getAction(), "TRIP_COMPLETED", "Third action");
            
            return true;
        });
        
        // Test 2.3: System metrics tracking
        testCase("System metrics tracking", () -> {
            RLStateManager manager = new RLStateManager();
            
            // Add wait times
            manager.addWaitTime(120.0);
            manager.addWaitTime(180.0);
            manager.addWaitTime(300.0);
            
            RLStateManager.SystemMetrics metrics = manager.getCurrentMetrics();
            assertTrue(metrics.averageWaitTime > 0, "Average wait time calculated");
            
            return true;
        });
    }
    
    private static void testRewardCalculation() {
        System.out.println("\n3. TESTING REWARD CALCULATION");
        
        // Test 3.1: User-specific rewards
        testCase("User-specific reward calculation", () -> {
            RewardCalculator calculator = new RewardCalculator(preferenceStore);
            
            // Test with known users
            Id<Person> user1 = Id.createPersonId(1);
            Id<Person> user2 = Id.createPersonId(2);
            
            DrtRequestRecord request1 = createTestRequest(user1, 300.0); // 5 min wait
            DrtRequestRecord request2 = createTestRequest(user2, 300.0); // Same wait
            
            double reward1 = calculator.calculateSchedulingReward(request1, preferenceStore);
            double reward2 = calculator.calculateSchedulingReward(request2, preferenceStore);
            
            // Users should have different rewards due to different preferences
            assertNotEquals(reward1, reward2, "Different users have different rewards");
            
            return true;
        });
        
        // Test 3.2: Wait time impact on rewards
        testCase("Wait time impact on rewards", () -> {
            RewardCalculator calculator = new RewardCalculator(preferenceStore);
            Id<Person> user = Id.createPersonId(1);
            
            DrtRequestRecord shortWait = createTestRequest(user, 120.0); // 2 min
            DrtRequestRecord longWait = createTestRequest(user, 600.0);  // 10 min
            
            double shortWaitReward = calculator.calculateSchedulingReward(shortWait, preferenceStore);
            double longWaitReward = calculator.calculateSchedulingReward(longWait, preferenceStore);
            
            assertTrue(shortWaitReward > longWaitReward, "Shorter wait times have higher rewards");
            
            return true;
        });
        
        // Test 3.3: Rejection penalties
        testCase("Rejection penalties", () -> {
            RewardCalculator calculator = new RewardCalculator(preferenceStore);
            
            Id<Person> user1 = Id.createPersonId(1);
            Id<Person> user2 = Id.createPersonId(2);
            
            DrtRequestRecord request1 = createTestRequest(user1, 0);
            DrtRequestRecord request2 = createTestRequest(user2, 0);
            
            double penalty1 = calculator.calculateRejectionPenalty(request1, preferenceStore);
            double penalty2 = calculator.calculateRejectionPenalty(request2, preferenceStore);
            
            assertTrue(penalty1 < 0, "Rejection penalty is negative");
            assertTrue(penalty2 < 0, "Rejection penalty is negative");
            
            return true;
        });
        
        // Test 3.4: Completion rewards
        testCase("Completion rewards", () -> {
            RewardCalculator calculator = new RewardCalculator(preferenceStore);
            Id<Person> user = Id.createPersonId(1);
            
            DrtRequestRecord request = createTestRequest(user, 300.0);
            request.setPickupTime(request.getScheduledTime());
            request.setDropoffTime(request.getScheduledTime() + 600);
            
            PersonTripRecord trip = new PersonTripRecord(user, request.getScheduledTime(), 
                Id.createLinkId("testLink"));
            trip.setArrivalTime(request.getDropoffTime());
            
            double completionReward = calculator.calculateCompletionReward(request, trip, preferenceStore);
            assertTrue(completionReward > 0, "Completion reward is positive");
            
            return true;
        });
    }
    
    private static void testEventHandling() {
        System.out.println("\n4. TESTING EVENT HANDLING");
        
        // Test 4.1: Event handler initialization
        testCase("Event handler initialization", () -> {
            PrefRLEventHandler handler = new PrefRLEventHandler("output/test", preferenceStore);
            
            assertNotNull(handler.getStateManager(), "State manager initialized");
            assertNotNull(handler.getRewardCalculator(), "Reward calculator initialized");
            assertEquals(handler.getTotalRequests(), 0, "Initial request count");
            
            return true;
        });
        
        // Test 4.2: Request tracking
        testCase("Request lifecycle tracking", () -> {
            PrefRLEventHandler handler = new PrefRLEventHandler("output/test", preferenceStore);
            
            // Simulate request submission
            assertEquals(handler.getTotalRequests(), 0, "Initial requests");
            
            // Test would require actual MATSim event objects, which are complex to mock
            // This validates the handler structure is correct
            assertTrue(handler.getStateManager() != null, "Handler has state manager");
            
            return true;
        });
    }
    
    private static void testDataExport() {
        System.out.println("\n5. TESTING DATA EXPORT");
        
        // Test 5.1: CSV export format
        testCase("CSV export format", () -> {
            RLStateManager manager = new RLStateManager();
            Id<Person> person = Id.createPersonId("exportTest");
            RLState state = new RLState(9 * 3600, 2, 8, 2, 150, 0, 15, 20);
            
            manager.recordRequestSubmission(person, state, 9 * 3600);
            
            List<StateActionRecord> history = manager.getCompleteHistory();
            assertEquals(history.size(), 1, "History recorded");
            
            StateActionRecord record = history.get(0);
            String csv = record.toCSVString();
            assertTrue(csv.contains("exportTest"), "CSV contains person ID");
            assertTrue(csv.contains("REQUEST_SUBMITTED"), "CSV contains action");
            
            return true;
        });
        
        // Test 5.2: State action record features
        testCase("State action record features", () -> {
            RLState state = new RLState(10 * 3600, 3, 7, 3, 200, 1, 25, 35);
            StateActionRecord record = new StateActionRecord(
                Id.createPersonId("featureTest"), state, "TEST_ACTION", 5.5, 10 * 3600);
            
            double[] features = record.getFeatureVector();
            assertEquals(features.length, 10, "Feature vector length");
            assertEquals(record.getActionAsInt(), -1, "Unknown action returns -1");
            
            return true;
        });
    }
    
    private static void testEdgeCases() {
        System.out.println("\n6. TESTING EDGE CASES");
        
        // Test 6.1: Extreme state values
        testCase("Extreme state values", () -> {
            // Test with very high values
            RLState extremeState = new RLState(23.5 * 3600, 100, 0, 50, 1800, 20, 72, 1);
            double[] features = extremeState.toFeatureVector();
            
            // All features should still be normalized
            for (double feature : features) {
                assertTrue(feature >= 0 && feature <= 1, "Feature normalized despite extreme values");
            }
            
            return true;
        });
        
        // Test 6.2: Zero/negative values
        testCase("Zero and boundary values", () -> {
            RLState zeroState = new RLState(0, 0, 0, 0, 0, 0, 1, 1);
            double[] features = zeroState.toFeatureVector();
            
            assertEquals(features[0], 0.0, "Zero active requests normalized to 0");
            assertEquals(features[5], 0.0, "Zero system load");
            
            return true;
        });
        
        // Test 6.3: Missing preference data
        testCase("Missing preference data handling", () -> {
            RewardCalculator calculator = new RewardCalculator(preferenceStore);
            Id<Person> unknownUser = Id.createPersonId("unknown999");
            
            DrtRequestRecord request = createTestRequest(unknownUser, 300.0);
            double reward = calculator.calculateSchedulingReward(request, preferenceStore);
            
            // Should not crash and should return a reasonable default reward
            assertTrue(!Double.isNaN(reward), "Reward calculated for unknown user");
            assertTrue(Math.abs(reward) < 100, "Reasonable reward magnitude");
            
            return true;
        });
        
        // Test 6.4: Time boundary conditions
        testCase("Time boundary conditions", () -> {
            // Test midnight boundary
            RLState midnightState = new RLState(0 * 3600, 1, 5, 0, 100, 0, 10, 15);
            assertEquals(midnightState.getHourOfDay(), 0, "Midnight hour calculation");
            
            // Test end of day
            RLState endDayState = new RLState(23.9 * 3600, 1, 5, 0, 100, 0, 10, 15);
            assertEquals(endDayState.getHourOfDay(), 23, "End of day hour calculation");
            
            return true;
        });
    }
    
    private static void testIntegration() {
        System.out.println("\n7. TESTING INTEGRATION");
        
        // Test 7.1: Full workflow integration
        testCase("Complete RL workflow integration", () -> {
            RLStateManager stateManager = new RLStateManager();
            RewardCalculator rewardCalculator = new RewardCalculator(preferenceStore);
            
            double currentTime = 8 * 3600;
            Id<Person> person = Id.createPersonId("integrationTest");
            
            // Complete workflow: submit -> schedule -> complete
            RLState state1 = stateManager.getCurrentState(currentTime);
            stateManager.recordRequestSubmission(person, state1, currentTime);
            
            RLState state2 = stateManager.getCurrentState(currentTime + 180);
            DrtRequestRecord request = createTestRequest(person, 180.0);
            double schedulingReward = rewardCalculator.calculateSchedulingReward(request, preferenceStore);
            stateManager.recordSchedulingDecision(person, state2, schedulingReward, currentTime + 180);
            
            RLState state3 = stateManager.getCurrentState(currentTime + 900);
            PersonTripRecord trip = new PersonTripRecord(person, currentTime + 180, Id.createLinkId("test"));
            trip.setArrivalTime(currentTime + 900);
            request.setPickupTime(currentTime + 180);
            request.setDropoffTime(currentTime + 900);
            
            double completionReward = rewardCalculator.calculateCompletionReward(request, trip, preferenceStore);
            stateManager.recordTripCompletion(person, state3, completionReward, currentTime + 900);
            
            List<StateActionRecord> history = stateManager.getPersonHistory(person);
            assertEquals(history.size(), 3, "Complete workflow recorded");
            
            return true;
        });
        
        // Test 7.2: Multiple concurrent users
        testCase("Multiple concurrent users", () -> {
            RLStateManager stateManager = new RLStateManager();
            RewardCalculator rewardCalculator = new RewardCalculator(preferenceStore);
            
            double baseTime = 9 * 3600;
            List<Id<Person>> users = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                users.add(Id.createPersonId("concurrent" + i));
            }
            
            // Submit all requests simultaneously
            for (Id<Person> user : users) {
                RLState state = stateManager.getCurrentState(baseTime);
                stateManager.recordRequestSubmission(user, state, baseTime);
            }
            
            // Check system state reflects all requests
            RLState finalState = stateManager.getCurrentState(baseTime + 1);
            assertEquals(finalState.getActiveRequests(), 5, "All concurrent requests tracked");
            
            return true;
        });
    }
    
    private static void testPerformance() {
        System.out.println("\n8. TESTING PERFORMANCE");
        
        // Test 8.1: State calculation performance
        testCase("State calculation performance", () -> {
            long startTime = System.currentTimeMillis();
            
            // Create many states
            for (int i = 0; i < 1000; i++) {
                RLState state = new RLState(i * 60, i % 20, 10, i % 10, i * 0.5, i % 5, 
                    (i % 72) + 1, ((i + 10) % 72) + 1);
                double[] features = state.toFeatureVector();
                int discreteId = state.getDiscreteStateId();
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            assertTrue(duration < 1000, "State calculation performance acceptable: " + duration + "ms");
            
            return true;
        });
        
        // Test 8.2: Reward calculation performance
        testCase("Reward calculation performance", () -> {
            RewardCalculator calculator = new RewardCalculator(preferenceStore);
            long startTime = System.currentTimeMillis();
            
            // Calculate many rewards
            for (int i = 1; i <= 500; i++) {
                Id<Person> user = Id.createPersonId(i);
                DrtRequestRecord request = createTestRequest(user, i * 0.5);
                double reward = calculator.calculateSchedulingReward(request, preferenceStore);
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            assertTrue(duration < 2000, "Reward calculation performance acceptable: " + duration + "ms");
            
            return true;
        });
    }
    
    // Helper methods
    private static DrtRequestRecord createTestRequest(Id<Person> personId, double waitTime) {
        DrtRequestRecord request = new DrtRequestRecord(
            Id.create("req_" + personId, org.matsim.contrib.dvrp.optimizer.Request.class),
            personId,
            7200.0, // 2:00 AM submission
            Id.createLinkId("fromLink"),
            Id.createLinkId("toLink")
        );
        request.setScheduledTime(7200.0 + waitTime);
        request.setWaitTime(waitTime);
        return request;
    }
    
    private static void testCase(String name, TestFunction test) {
        testsTotal++;
        try {
            boolean result = test.run();
            if (result) {
                testsPassed++;
                System.out.println("  ✅ " + name);
            } else {
                failedTests.add(name);
                System.out.println("  ❌ " + name);
            }
        } catch (Exception e) {
            failedTests.add(name + " (Exception: " + e.getMessage() + ")");
            System.out.println("  ❌ " + name + " - Exception: " + e.getMessage());
        }
    }
    
    private static void printTestSummary() {
        System.out.println("\n=== TEST SUMMARY ===");
        System.out.printf("Tests passed: %d/%d (%.1f%%)%n", 
            testsPassed, testsTotal, (testsPassed * 100.0 / testsTotal));
        
        if (failedTests.isEmpty()) {
            System.out.println("🎉 ALL TESTS PASSED!");
        } else {
            System.out.println("\n❌ Failed tests:");
            for (String test : failedTests) {
                System.out.println("  - " + test);
            }
        }
        System.out.println("========================");
    }
    
    // Helper assertion methods
    private static void assertEquals(Object actual, Object expected, String message) {
        if (!actual.equals(expected)) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }
    
    private static void assertNotEquals(Object actual, Object unexpected, String message) {
        if (actual.equals(unexpected)) {
            throw new AssertionError(message + ": values should not be equal: " + actual);
        }
    }
    
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
    
    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }
    
    private static void assertNotNull(Object object, String message) {
        if (object == null) {
            throw new AssertionError(message + ": object was null");
        }
    }
    
    @FunctionalInterface
    interface TestFunction {
        boolean run() throws Exception;
    }
}