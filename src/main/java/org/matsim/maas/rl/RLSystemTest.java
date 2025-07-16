package org.matsim.maas.rl;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.preference.data.PreferenceDataLoader;
import org.matsim.maas.preference.data.UserPreferenceStore;
import org.matsim.maas.rl.events.DrtRequestRecord;
import org.matsim.maas.rl.events.PersonTripRecord;
import org.matsim.maas.rl.events.RLState;
import org.matsim.maas.rl.reward.RewardCalculator;
import org.matsim.maas.rl.state.RLStateManager;

/**
 * Test class for validating RL system components including state management,
 * reward calculation, and event tracking for DRT dispatching.
 */
public class RLSystemTest {
    
    public static void main(String[] args) {
        System.out.println("=== RL SYSTEM TEST ===");
        
        // Test 1: Load preference data
        System.out.println("\n1. Loading preference data for RL system...");
        UserPreferenceStore preferenceStore = PreferenceDataLoader.loadAllPreferenceData();
        
        // Test 2: Initialize RL components
        System.out.println("\n2. Initializing RL system components...");
        testRLComponentInitialization(preferenceStore);
        
        // Test 3: Test state management
        System.out.println("\n3. Testing RL state management...");
        testStateManagement();
        
        // Test 4: Test reward calculation
        System.out.println("\n4. Testing reward calculation...");
        testRewardCalculation(preferenceStore);
        
        // Test 5: Test complete RL workflow
        System.out.println("\n5. Testing complete RL workflow...");
        testCompleteRLWorkflow(preferenceStore);
        
        System.out.println("\n=== RL SYSTEM TEST COMPLETE ===");
    }
    
    private static void testRLComponentInitialization(UserPreferenceStore preferenceStore) {
        // Test state manager
        RLStateManager stateManager = new RLStateManager();
        RLState initialState = stateManager.getCurrentState(0.0);
        System.out.println("Initial RL state: " + initialState);
        
        // Test reward calculator
        RewardCalculator rewardCalculator = new RewardCalculator(preferenceStore);
        System.out.println("Reward calculator stats: " + rewardCalculator.getRewardStats());
        
        // Test state features
        double[] features = initialState.toFeatureVector();
        String[] featureNames = RLState.getFeatureNames();
        System.out.println("State features (" + features.length + "):");
        for (int i = 0; i < features.length; i++) {
            System.out.printf("  %s: %.3f%n", featureNames[i], features[i]);
        }
    }
    
    private static void testStateManagement() {
        RLStateManager stateManager = new RLStateManager();
        
        // Simulate state transitions
        double currentTime = 7.5 * 3600; // 7:30 AM (rush hour)
        
        // Test request submission
        Id<Person> person1 = Id.createPersonId(1);
        RLState state1 = stateManager.getCurrentState(currentTime);
        stateManager.recordRequestSubmission(person1, state1, currentTime);
        
        // Test scheduling
        currentTime += 120; // 2 minutes later
        RLState state2 = stateManager.getCurrentState(currentTime);
        stateManager.recordSchedulingDecision(person1, state2, 8.5, currentTime);
        
        // Test completion
        currentTime += 600; // 10 minutes later
        RLState state3 = stateManager.getCurrentState(currentTime);
        stateManager.recordTripCompletion(person1, state3, 15.2, currentTime);
        
        // Check state evolution
        System.out.println("State evolution:");
        System.out.println("  Initial: " + state1);
        System.out.println("  After scheduling: " + state2);
        System.out.println("  After completion: " + state3);
        
        // Check system metrics
        RLStateManager.SystemMetrics metrics = stateManager.getCurrentMetrics();
        System.out.println("Current system metrics: " + metrics);
    }
    
    private static void testRewardCalculation(UserPreferenceStore preferenceStore) {
        RewardCalculator rewardCalculator = new RewardCalculator(preferenceStore);
        
        // Create sample request and trip records
        Id<Person> personId = Id.createPersonId(1);
        Id<Link> fromLink = Id.createLinkId("link1");
        Id<Link> toLink = Id.createLinkId("link2");
        
        DrtRequestRecord requestRecord = new DrtRequestRecord(
            Id.create("req1", org.matsim.contrib.dvrp.optimizer.Request.class),
            personId,
            7200.0, // 2:00 (submission time)
            fromLink,
            toLink
        );
        
        // Test scheduling reward
        requestRecord.setScheduledTime(7320.0); // 2 minutes wait
        requestRecord.setWaitTime(120.0);
        double schedulingReward = rewardCalculator.calculateSchedulingReward(requestRecord, preferenceStore);
        System.out.println("Scheduling reward (2min wait): " + String.format("%.3f", schedulingReward));
        
        // Test with longer wait time
        requestRecord.setWaitTime(600.0); // 10 minutes wait
        double longWaitReward = rewardCalculator.calculateSchedulingReward(requestRecord, preferenceStore);
        System.out.println("Scheduling reward (10min wait): " + String.format("%.3f", longWaitReward));
        
        // Test rejection penalty
        double rejectionPenalty = rewardCalculator.calculateRejectionPenalty(requestRecord, preferenceStore);
        System.out.println("Rejection penalty: " + String.format("%.3f", rejectionPenalty));
        
        // Test completion reward
        PersonTripRecord tripRecord = new PersonTripRecord(personId, 7320.0, fromLink);
        tripRecord.setArrivalTime(8120.0); // 13+ minute trip
        tripRecord.setTravelTime(800.0);
        
        requestRecord.setPickupTime(7320.0);
        requestRecord.setDropoffTime(8120.0);
        
        double completionReward = rewardCalculator.calculateCompletionReward(
            requestRecord, tripRecord, preferenceStore);
        System.out.println("Completion reward: " + String.format("%.3f", completionReward));
        
        // Test with different user preferences
        testUserSpecificRewards(rewardCalculator, preferenceStore);
    }
    
    private static void testUserSpecificRewards(RewardCalculator rewardCalculator, 
                                               UserPreferenceStore preferenceStore) {
        System.out.println("\nUser-specific reward comparison:");
        
        // Test with 3 different users with different preferences
        Id<Person>[] testUsers = new Id[] {
            Id.createPersonId(1), 
            Id.createPersonId(2), 
            Id.createPersonId(3)
        };
        
        for (Id<Person> userId : testUsers) {
            var prefData = preferenceStore.getUserPreference(userId);
            if (prefData != null) {
                System.out.println("User " + userId + " preferences: " + prefData);
                
                // Create sample request
                DrtRequestRecord request = new DrtRequestRecord(
                    Id.create("req_" + userId, org.matsim.contrib.dvrp.optimizer.Request.class),
                    userId,
                    7200.0,
                    Id.createLinkId("link1"),
                    Id.createLinkId("link2")
                );
                request.setWaitTime(300.0); // 5 minutes wait
                
                double reward = rewardCalculator.calculateSchedulingReward(request, preferenceStore);
                System.out.println("  Scheduling reward: " + String.format("%.3f", reward));
            }
        }
    }
    
    private static void testCompleteRLWorkflow(UserPreferenceStore preferenceStore) {
        System.out.println("\nTesting complete RL workflow simulation...");
        
        RLStateManager stateManager = new RLStateManager();
        RewardCalculator rewardCalculator = new RewardCalculator(preferenceStore);
        
        double currentTime = 8.0 * 3600; // 8:00 AM
        double totalReward = 0.0;
        int numRequests = 5;
        
        System.out.println("Simulating " + numRequests + " DRT requests...");
        
        for (int i = 1; i <= numRequests; i++) {
            Id<Person> personId = Id.createPersonId(i);
            
            // 1. Request submission
            RLState state1 = stateManager.getCurrentState(currentTime);
            stateManager.recordRequestSubmission(personId, state1, currentTime);
            System.out.printf("Request %d submitted at %.0f (state: %s)%n", 
                             i, currentTime, state1.toString());
            
            // 2. Decision: schedule or reject (simulate 80% success rate)
            boolean scheduled = Math.random() < 0.8;
            currentTime += 60 + Math.random() * 240; // 1-5 minute processing
            
            if (scheduled) {
                // Schedule the request
                RLState state2 = stateManager.getCurrentState(currentTime);
                DrtRequestRecord requestRecord = new DrtRequestRecord(
                    Id.create("req" + i, org.matsim.contrib.dvrp.optimizer.Request.class),
                    personId, currentTime - (60 + Math.random() * 240),
                    Id.createLinkId("link" + i), Id.createLinkId("link" + (i+10))
                );
                requestRecord.setWaitTime(currentTime - requestRecord.getSubmissionTime());
                
                double schedulingReward = rewardCalculator.calculateSchedulingReward(requestRecord, preferenceStore);
                stateManager.recordSchedulingDecision(personId, state2, schedulingReward, currentTime);
                totalReward += schedulingReward;
                
                System.out.printf("  Scheduled with %.1fs wait, reward: %.3f%n", 
                                 requestRecord.getWaitTime(), schedulingReward);
                
                // 3. Trip completion
                currentTime += 300 + Math.random() * 600; // 5-15 minute trip
                RLState state3 = stateManager.getCurrentState(currentTime);
                
                PersonTripRecord tripRecord = new PersonTripRecord(personId, 
                    requestRecord.getScheduledTime(), requestRecord.getFromLinkId());
                tripRecord.setArrivalTime(currentTime);
                
                requestRecord.setPickupTime(requestRecord.getScheduledTime());
                requestRecord.setDropoffTime(currentTime);
                
                double completionReward = rewardCalculator.calculateCompletionReward(
                    requestRecord, tripRecord, preferenceStore);
                stateManager.recordTripCompletion(personId, state3, completionReward, currentTime);
                totalReward += completionReward;
                
                System.out.printf("  Completed in %.1fs, reward: %.3f%n", 
                                 tripRecord.getTravelTime(), completionReward);
                
            } else {
                // Reject the request
                RLState state2 = stateManager.getCurrentState(currentTime);
                DrtRequestRecord requestRecord = new DrtRequestRecord(
                    Id.create("req" + i, org.matsim.contrib.dvrp.optimizer.Request.class),
                    personId, currentTime - 60,
                    Id.createLinkId("link" + i), Id.createLinkId("link" + (i+10))
                );
                
                double rejectionPenalty = rewardCalculator.calculateRejectionPenalty(requestRecord, preferenceStore);
                stateManager.recordRejection(personId, state2, rejectionPenalty, currentTime);
                totalReward += rejectionPenalty;
                
                System.out.printf("  Rejected, penalty: %.3f%n", rejectionPenalty);
            }
            
            currentTime += 120; // 2 minutes between requests
        }
        
        System.out.printf("\nWorkflow complete - Total reward: %.3f, Average: %.3f%n", 
                         totalReward, totalReward / numRequests);
        
        // Final system state
        RLStateManager.SystemMetrics finalMetrics = stateManager.getCurrentMetrics();
        System.out.println("Final system state: " + finalMetrics);
    }
}