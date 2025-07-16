package org.matsim.maas.rl.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEvent;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEventHandler;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEventHandler;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEventHandler;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.maas.preference.data.UserPreferenceStore;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;
import org.matsim.maas.rl.state.RLStateManager;
import org.matsim.maas.rl.reward.RewardCalculator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive event handler for RL-driven DRT dispatching.
 * Tracks all DRT events, calculates rewards, manages state transitions,
 * and provides data for reinforcement learning algorithms.
 */
public class PrefRLEventHandler implements 
    DrtRequestSubmittedEventHandler,
    PassengerRequestScheduledEventHandler,
    PassengerRequestRejectedEventHandler,
    PersonDepartureEventHandler,
    PersonArrivalEventHandler,
    IterationEndsListener {

    private final String outputDirectory;
    private final UserPreferenceStore preferenceStore;
    private final RLStateManager stateManager;
    private final RewardCalculator rewardCalculator;
    
    // Event tracking maps
    private final Map<Id<org.matsim.contrib.dvrp.optimizer.Request>, DrtRequestRecord> activeRequests = new HashMap<>();
    private final Map<Id<Person>, PersonTripRecord> activeTrips = new HashMap<>();
    
    // Metrics for RL learning
    private int totalRequests = 0;
    private int successfulRequests = 0;
    private int rejectedRequests = 0;
    private double totalReward = 0.0;
    private double totalWaitTime = 0.0;
    private double totalTravelTime = 0.0;
    private int completedTrips = 0;
    
    public PrefRLEventHandler(String outputDirectory, UserPreferenceStore preferenceStore) {
        this.outputDirectory = outputDirectory;
        this.preferenceStore = preferenceStore;
        this.stateManager = new RLStateManager();
        this.rewardCalculator = new RewardCalculator(preferenceStore);
        
        System.out.println("PrefRLEventHandler: Initialized for RL-driven DRT tracking");
        System.out.println("PrefRLEventHandler: Output directory: " + outputDirectory);
    }

    @Override
    public void handleEvent(DrtRequestSubmittedEvent event) {
        Id<Person> personId = event.getPersonIds().iterator().next();
        
        // Create request record
        DrtRequestRecord record = new DrtRequestRecord(
            event.getRequestId(),
            personId,
            event.getTime(),
            event.getFromLinkId(),
            event.getToLinkId()
        );
        
        activeRequests.put(event.getRequestId(), record);
        totalRequests++;
        
        // Update state for RL algorithm
        RLState currentState = stateManager.getCurrentState(event.getTime());
        stateManager.recordRequestSubmission(personId, currentState, event.getTime());
        
        System.out.println("RL: Request submitted - Person: " + personId + ", Time: " + event.getTime());
    }

    @Override
    public void handleEvent(PassengerRequestScheduledEvent event) {
        DrtRequestRecord record = activeRequests.get(event.getRequestId());
        if (record != null) {
            record.setScheduledTime(event.getTime());
            record.setPickupTime(event.getPickupTime());
            record.setDropoffTime(event.getDropoffTime());
            
            double waitTime = event.getTime() - record.getSubmissionTime();
            record.setWaitTime(waitTime);
            
            successfulRequests++;
            totalWaitTime += waitTime;
            
            // Calculate reward for successful scheduling
            double reward = rewardCalculator.calculateSchedulingReward(record, preferenceStore);
            totalReward += reward;
            
            // Update RL state
            RLState newState = stateManager.getCurrentState(event.getTime());
            stateManager.recordSchedulingDecision(record.getPersonId(), newState, reward, event.getTime());
            
            System.out.println("RL: Request scheduled - Person: " + record.getPersonId() + 
                             ", Wait: " + String.format("%.1f", waitTime) + "s, Reward: " + String.format("%.3f", reward));
        }
    }

    @Override
    public void handleEvent(PassengerRequestRejectedEvent event) {
        DrtRequestRecord record = activeRequests.get(event.getRequestId());
        if (record != null) {
            record.setRejected(true);
            rejectedRequests++;
            
            // Calculate penalty for rejection
            double penalty = rewardCalculator.calculateRejectionPenalty(record, preferenceStore);
            totalReward += penalty; // penalty is negative
            
            // Update RL state
            RLState newState = stateManager.getCurrentState(event.getTime());
            stateManager.recordRejection(record.getPersonId(), newState, penalty, event.getTime());
            
            System.out.println("RL: Request rejected - Person: " + record.getPersonId() + 
                             ", Penalty: " + String.format("%.3f", penalty));
            
            // Remove from active requests
            activeRequests.remove(event.getRequestId());
        }
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {
        if ("drt".equals(event.getLegMode())) {
            PersonTripRecord tripRecord = new PersonTripRecord(
                event.getPersonId(),
                event.getTime(),
                event.getLinkId()
            );
            activeTrips.put(event.getPersonId(), tripRecord);
        }
    }

    @Override
    public void handleEvent(PersonArrivalEvent event) {
        if ("drt".equals(event.getLegMode())) {
            PersonTripRecord tripRecord = activeTrips.get(event.getPersonId());
            DrtRequestRecord requestRecord = findRequestByPersonId(event.getPersonId());
            
            if (tripRecord != null && requestRecord != null) {
                double travelTime = event.getTime() - tripRecord.getDepartureTime();
                tripRecord.setArrivalTime(event.getTime());
                tripRecord.setTravelTime(travelTime);
                
                totalTravelTime += travelTime;
                completedTrips++;
                
                // Calculate completion reward
                double completionReward = rewardCalculator.calculateCompletionReward(
                    requestRecord, tripRecord, preferenceStore);
                totalReward += completionReward;
                
                // Update RL state
                RLState finalState = stateManager.getCurrentState(event.getTime());
                stateManager.recordTripCompletion(event.getPersonId(), finalState, completionReward, event.getTime());
                
                System.out.println("RL: Trip completed - Person: " + event.getPersonId() + 
                                 ", Travel: " + String.format("%.1f", travelTime) + "s, Reward: " + String.format("%.3f", completionReward));
                
                // Clean up
                activeTrips.remove(event.getPersonId());
                activeRequests.values().removeIf(r -> r.getPersonId().equals(event.getPersonId()));
            }
        }
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        int iteration = event.getIteration();
        
        // Calculate iteration metrics
        double successRate = totalRequests > 0 ? (double) successfulRequests / totalRequests : 0.0;
        double rejectionRate = totalRequests > 0 ? (double) rejectedRequests / totalRequests : 0.0;
        double avgWaitTime = successfulRequests > 0 ? totalWaitTime / successfulRequests : 0.0;
        double avgTravelTime = completedTrips > 0 ? totalTravelTime / completedTrips : 0.0;
        double avgReward = totalRequests > 0 ? totalReward / totalRequests : 0.0;
        
        // Log RL metrics
        System.out.println("\n=== RL METRICS - Iteration " + iteration + " ===");
        System.out.printf("Total requests: %d%n", totalRequests);
        System.out.printf("Successful requests: %d (%.1f%%)%n", successfulRequests, successRate * 100);
        System.out.printf("Rejected requests: %d (%.1f%%)%n", rejectedRequests, rejectionRate * 100);
        System.out.printf("Average wait time: %.1f seconds%n", avgWaitTime);
        System.out.printf("Average travel time: %.1f seconds%n", avgTravelTime);
        System.out.printf("Total reward: %.3f%n", totalReward);
        System.out.printf("Average reward per request: %.3f%n", avgReward);
        System.out.println("============================================\n");
        
        // Write metrics to file
        writeRLMetricsToFile(iteration, successRate, rejectionRate, avgWaitTime, avgTravelTime, avgReward);
        
        // Export state-action data for RL learning
        stateManager.exportStateActionData(outputDirectory, iteration);
        
        // Reset counters for next iteration
        resetCounters();
    }
    
    private void writeRLMetricsToFile(int iteration, double successRate, double rejectionRate, 
                                     double avgWaitTime, double avgTravelTime, double avgReward) {
        String filename = outputDirectory + "/rl_metrics.csv";
        boolean fileExists = Files.exists(Paths.get(filename));
        
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename), 
                                                              StandardOpenOption.CREATE, 
                                                              StandardOpenOption.APPEND)) {
            
            // Write header if file is new
            if (!fileExists) {
                writer.write("iteration,total_requests,successful_requests,rejected_requests," +
                           "success_rate,rejection_rate,avg_wait_time_sec,avg_travel_time_sec," +
                           "total_reward,avg_reward_per_request,completed_trips\n");
            }
            
            // Write metrics data
            writer.write(String.format("%d,%d,%d,%d,%.4f,%.4f,%.2f,%.2f,%.4f,%.4f,%d\n",
                                     iteration, totalRequests, successfulRequests, rejectedRequests,
                                     successRate, rejectionRate, avgWaitTime, avgTravelTime, 
                                     totalReward, avgReward, completedTrips));
            
        } catch (IOException e) {
            System.err.println("Error writing RL metrics: " + e.getMessage());
        }
    }
    
    private DrtRequestRecord findRequestByPersonId(Id<Person> personId) {
        return activeRequests.values().stream()
            .filter(r -> r.getPersonId().equals(personId))
            .findFirst()
            .orElse(null);
    }
    
    private void resetCounters() {
        activeRequests.clear();
        activeTrips.clear();
        
        totalRequests = 0;
        successfulRequests = 0;
        rejectedRequests = 0;
        totalReward = 0.0;
        totalWaitTime = 0.0;
        totalTravelTime = 0.0;
        completedTrips = 0;
    }

    @Override
    public void reset(int iteration) {
        // Reset is handled in notifyIterationEnds to ensure metrics are calculated first
    }
    
    // Getters for metrics access
    public double getTotalReward() { return totalReward; }
    public int getTotalRequests() { return totalRequests; }
    public int getSuccessfulRequests() { return successfulRequests; }
    public RLStateManager getStateManager() { return stateManager; }
    public RewardCalculator getRewardCalculator() { return rewardCalculator; }
}