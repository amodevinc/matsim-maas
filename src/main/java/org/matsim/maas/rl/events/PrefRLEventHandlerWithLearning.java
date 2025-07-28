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
import org.matsim.maas.preference.data.DynamicUserPreferenceStore;
import org.matsim.maas.preference.data.DynamicUserPreferenceStore.PreferenceUpdate;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;
import org.matsim.maas.preference.events.PreferenceUpdateEvent;
import org.matsim.maas.preference.events.PreferenceUpdateTracker;
import org.matsim.maas.preference.learning.PolicyGradientPreferenceLearner;
import org.matsim.maas.preference.learning.PreferenceLearner.LearningExperience;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced RL event handler that integrates preference learning.
 * 
 * This handler extends the basic PrefRLEventHandler with actual learning capabilities:
 * - Captures DRT events (request, acceptance, rejection, completion)
 * - Applies policy gradient learning to update user preferences
 * - Fires PreferenceUpdateEvents for monitoring
 * - Provides comprehensive RL analytics
 * 
 * The learning process:
 * 1. User submits request → capture state
 * 2. Request accepted → learn from acceptance with positive reward
 * 3. Request rejected → learn from rejection with negative reward
 * 4. Trip completed → learn from completion with satisfaction reward
 * 5. Update preferences in DynamicUserPreferenceStore
 * 6. Fire PreferenceUpdateEvent for analytics
 */
public class PrefRLEventHandlerWithLearning implements 
    DrtRequestSubmittedEventHandler,
    PassengerRequestScheduledEventHandler,
    PassengerRequestRejectedEventHandler,
    PersonDepartureEventHandler,
    PersonArrivalEventHandler,
    IterationEndsListener {

    private final String outputDirectory;
    private final DynamicUserPreferenceStore dynamicStore;
    private final PolicyGradientPreferenceLearner learner;
    private final PreferenceUpdateTracker updateTracker;
    
    // Event tracking maps
    private final Map<Id<org.matsim.contrib.dvrp.optimizer.Request>, DrtRequestRecord> activeRequests = new HashMap<>();
    private final Map<Id<Person>, PersonTripRecord> activeTrips = new HashMap<>();
    
    // Learning metrics
    private int totalLearningUpdates = 0;
    private int acceptanceLearningCount = 0;
    private int rejectionLearningCount = 0;
    private int completionLearningCount = 0;
    private double totalLearningReward = 0.0;
    
    // Reward calculation parameters
    private static final double ACCEPTANCE_BASE_REWARD = 1.0;
    private static final double REJECTION_BASE_PENALTY = -0.5;
    private static final double COMPLETION_BASE_REWARD = 1.5;
    
    public PrefRLEventHandlerWithLearning(String outputDirectory, 
                                        DynamicUserPreferenceStore dynamicStore,
                                        PolicyGradientPreferenceLearner learner,
                                        PreferenceUpdateTracker updateTracker) {
        this.outputDirectory = outputDirectory;
        this.dynamicStore = dynamicStore;
        this.learner = learner;
        this.updateTracker = updateTracker;
        
        System.out.println("PrefRLEventHandlerWithLearning: Initialized with learning capabilities");
        System.out.println("PrefRLEventHandlerWithLearning: Output directory: " + outputDirectory);
        System.out.println("PrefRLEventHandlerWithLearning: Learning config: " + learner.getConfiguration().toString());
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
        
        System.out.println("RL-Learning: Request submitted - Person: " + personId + ", Time: " + event.getTime());
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
            
            // Learn from acceptance
            Id<Person> personId = record.getPersonId();
            UserPreferenceData currentPref = dynamicStore.getUserPreference(personId);
            
            if (currentPref != null) {
                // Estimate service characteristics
                double estimatedAccessTime = 300.0; // 5 minutes default
                double estimatedWaitTime = waitTime;
                double estimatedIvtTime = event.getDropoffTime() - event.getPickupTime();
                double estimatedEgressTime = 180.0; // 3 minutes default
                
                // Calculate reward based on wait time (shorter wait = higher reward)
                double waitTimeReward = Math.max(0.1, ACCEPTANCE_BASE_REWARD * (1.0 - waitTime / 1800.0)); // Scale by 30 min max
                
                // Apply learning
                PreferenceUpdate update = learner.learnFromAcceptance(
                    personId, estimatedAccessTime, estimatedWaitTime, 
                    estimatedIvtTime, estimatedEgressTime, waitTimeReward);
                
                // Apply update to store
                boolean success = dynamicStore.updateUserPreference(personId,
                    update.accessDelta, update.waitDelta, update.ivtDelta, update.egressDelta);
                
                if (success) {
                    // Fire preference update event
                    UserPreferenceData newPref = dynamicStore.getUserPreference(personId);
                    PreferenceUpdateEvent prefEvent = new PreferenceUpdateEvent(
                        event.getTime(), personId,
                        currentPref.getAccessWeight(), currentPref.getWaitWeight(),
                        currentPref.getIvtWeight(), currentPref.getEgressWeight(),
                        newPref.getAccessWeight(), newPref.getWaitWeight(),
                        newPref.getIvtWeight(), newPref.getEgressWeight(),
                        "request_accepted", waitTimeReward
                    );
                    updateTracker.handleEvent(prefEvent);
                    
                    acceptanceLearningCount++;
                    totalLearningUpdates++;
                    totalLearningReward += waitTimeReward;
                    
                    System.out.println("RL-Learning: Acceptance learning applied - Person: " + personId + 
                                     ", Reward: " + String.format("%.3f", waitTimeReward) +
                                     ", Update magnitude: " + String.format("%.6f", prefEvent.getUpdateMagnitude()));
                }
            }
        }
    }

    @Override
    public void handleEvent(PassengerRequestRejectedEvent event) {
        DrtRequestRecord record = activeRequests.get(event.getRequestId());
        if (record != null) {
            record.setRejected(true);
            
            // Learn from rejection
            Id<Person> personId = record.getPersonId();
            UserPreferenceData currentPref = dynamicStore.getUserPreference(personId);
            
            if (currentPref != null) {
                // Estimate what the service would have been
                double estimatedAccessTime = 600.0; // Higher estimates for rejected requests
                double estimatedWaitTime = 1200.0;
                double estimatedIvtTime = 1800.0;
                double estimatedEgressTime = 300.0;
                
                // Apply learning with penalty
                PreferenceUpdate update = learner.learnFromRejection(
                    personId, estimatedAccessTime, estimatedWaitTime,
                    estimatedIvtTime, estimatedEgressTime, REJECTION_BASE_PENALTY);
                
                // Apply update to store
                boolean success = dynamicStore.updateUserPreference(personId,
                    update.accessDelta, update.waitDelta, update.ivtDelta, update.egressDelta);
                
                if (success) {
                    // Fire preference update event
                    UserPreferenceData newPref = dynamicStore.getUserPreference(personId);
                    PreferenceUpdateEvent prefEvent = new PreferenceUpdateEvent(
                        event.getTime(), personId,
                        currentPref.getAccessWeight(), currentPref.getWaitWeight(),
                        currentPref.getIvtWeight(), currentPref.getEgressWeight(),
                        newPref.getAccessWeight(), newPref.getWaitWeight(),
                        newPref.getIvtWeight(), newPref.getEgressWeight(),
                        "request_rejected", REJECTION_BASE_PENALTY
                    );
                    updateTracker.handleEvent(prefEvent);
                    
                    rejectionLearningCount++;
                    totalLearningUpdates++;
                    totalLearningReward += REJECTION_BASE_PENALTY;
                    
                    System.out.println("RL-Learning: Rejection learning applied - Person: " + personId + 
                                     ", Penalty: " + String.format("%.3f", REJECTION_BASE_PENALTY) +
                                     ", Update magnitude: " + String.format("%.6f", prefEvent.getUpdateMagnitude()));
                }
            }
            
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
                
                // Learn from completion
                Id<Person> personId = event.getPersonId();
                UserPreferenceData currentPref = dynamicStore.getUserPreference(personId);
                
                if (currentPref != null) {
                    // Calculate actual service characteristics
                    double actualAccessTime = 300.0; // Could be estimated from data
                    double actualWaitTime = requestRecord.getWaitTime();
                    double actualIvtTime = travelTime;
                    double actualEgressTime = 180.0;
                    
                    // Calculate satisfaction based on performance
                    double expectedTime = actualWaitTime + actualIvtTime;
                    double satisfactionReward = COMPLETION_BASE_REWARD;
                    
                    // Bonus for efficient service (short wait + reasonable travel time)
                    if (actualWaitTime < 300) satisfactionReward *= 1.2; // Bonus for quick service
                    if (actualWaitTime > 900) satisfactionReward *= 0.8; // Penalty for long wait
                    
                    // Apply completion learning
                    PreferenceUpdate update = learner.learnFromCompletion(
                        personId, actualAccessTime, actualWaitTime,
                        actualIvtTime, actualEgressTime, satisfactionReward);
                    
                    // Apply update to store
                    boolean success = dynamicStore.updateUserPreference(personId,
                        update.accessDelta, update.waitDelta, update.ivtDelta, update.egressDelta);
                    
                    if (success) {
                        // Fire preference update event
                        UserPreferenceData newPref = dynamicStore.getUserPreference(personId);
                        PreferenceUpdateEvent prefEvent = new PreferenceUpdateEvent(
                            event.getTime(), personId,
                            currentPref.getAccessWeight(), currentPref.getWaitWeight(),
                            currentPref.getIvtWeight(), currentPref.getEgressWeight(),
                            newPref.getAccessWeight(), newPref.getWaitWeight(),
                            newPref.getIvtWeight(), newPref.getEgressWeight(),
                            "trip_completed", satisfactionReward
                        );
                        updateTracker.handleEvent(prefEvent);
                        
                        completionLearningCount++;
                        totalLearningUpdates++;
                        totalLearningReward += satisfactionReward;
                        
                        System.out.println("RL-Learning: Completion learning applied - Person: " + personId + 
                                         ", Satisfaction: " + String.format("%.3f", satisfactionReward) +
                                         ", Update magnitude: " + String.format("%.6f", prefEvent.getUpdateMagnitude()));
                    }
                }
                
                // Clean up
                activeTrips.remove(event.getPersonId());
                activeRequests.values().removeIf(r -> r.getPersonId().equals(event.getPersonId()));
            }
        }
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        int iteration = event.getIteration();
        
        // Update learning parameters
        learner.updateLearningParameters(iteration);
        
        // Log RL learning metrics
        System.out.println("\n=== RL LEARNING SUMMARY - Iteration " + iteration + " ===");
        System.out.printf("Total learning updates: %d%n", totalLearningUpdates);
        System.out.printf("  ├─ Acceptance learning: %d%n", acceptanceLearningCount);
        System.out.printf("  ├─ Rejection learning: %d%n", rejectionLearningCount);
        System.out.printf("  └─ Completion learning: %d%n", completionLearningCount);
        System.out.printf("Total learning reward: %.3f%n", totalLearningReward);
        System.out.printf("Average reward per update: %.3f%n", 
                         totalLearningUpdates > 0 ? totalLearningReward / totalLearningUpdates : 0.0);
        
        // Display learner statistics
        var learnerStats = learner.getStatistics();
        System.out.printf("Current learning rate: %.6f%n", learnerStats.currentLearningRate);
        System.out.printf("Current exploration rate: %.4f%n", learnerStats.currentExplorationRate);
        System.out.printf("Global preference updates: %d%n", dynamicStore.getGlobalUpdateCount());
        System.out.println("=========================================\n");
        
        // Write RL learning metrics to file
        writeRLLearningMetrics(iteration);
        
        // Reset counters for next iteration
        resetCounters();
    }
    
    private void writeRLLearningMetrics(int iteration) {
        String filename = outputDirectory + "/rl_learning_metrics.csv";
        boolean fileExists = Files.exists(Paths.get(filename));
        
        try {
            Files.createDirectories(Paths.get(outputDirectory));
            
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename), 
                                                                  StandardOpenOption.CREATE, 
                                                                  StandardOpenOption.APPEND)) {
                
                // Write header if file is new
                if (!fileExists) {
                    writer.write("iteration,total_learning_updates,acceptance_learning,rejection_learning," +
                               "completion_learning,total_reward,avg_reward,learning_rate,exploration_rate," +
                               "global_updates\n");
                }
                
                var learnerStats = learner.getStatistics();
                double avgReward = totalLearningUpdates > 0 ? totalLearningReward / totalLearningUpdates : 0.0;
                
                // Write metrics data
                writer.write(String.format("%d,%d,%d,%d,%d,%.4f,%.4f,%.6f,%.4f,%d\n",
                                         iteration, totalLearningUpdates, acceptanceLearningCount,
                                         rejectionLearningCount, completionLearningCount,
                                         totalLearningReward, avgReward,
                                         learnerStats.currentLearningRate, learnerStats.currentExplorationRate,
                                         dynamicStore.getGlobalUpdateCount()));
                
            }
        } catch (IOException e) {
            System.err.println("Error writing RL learning metrics: " + e.getMessage());
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
        
        totalLearningUpdates = 0;
        acceptanceLearningCount = 0;
        rejectionLearningCount = 0;
        completionLearningCount = 0;
        totalLearningReward = 0.0;
    }

    @Override
    public void reset(int iteration) {
        // Reset is handled in notifyIterationEnds to ensure metrics are calculated first
    }
    
    // Getters for access to learning components
    public PolicyGradientPreferenceLearner getLearner() { return learner; }
    public DynamicUserPreferenceStore getDynamicStore() { return dynamicStore; }
    public int getTotalLearningUpdates() { return totalLearningUpdates; }
}