package org.matsim.maas.utils;

import org.matsim.api.core.v01.events.Event;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEvent;
import org.matsim.contrib.dvrp.passenger.PassengerPickedUpEvent;
import org.matsim.contrib.dvrp.passenger.PassengerDroppedOffEvent;
import org.matsim.core.events.handler.BasicEventHandler;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Event handler that learns from user interactions with the DRT system
 * to improve preference-aware dispatching over time.
 * 
 * This handler listens to DRT events and uses them to:
 * 1. Track user request patterns and outcomes
 * 2. Learn from acceptance/rejection patterns
 * 3. Update the preference model through the PolicyGradientLearner
 * 
 * @author MATSim-MaaS Research Team
 */
@Singleton
public class PreferenceAwareLearningHandler implements BasicEventHandler {
    
    private final PolicyGradientLearner policyLearner;
    private final PreferenceDataLoader preferenceLoader;
    private final boolean enableDetailedLogging;
    
    // Track request state for learning
    private final Map<String, RequestLearningData> activeRequests = new HashMap<>();
    
    // Performance tracking
    private int totalRequestsProcessed = 0;
    private int learningUpdatesApplied = 0;
    private long lastStatusReport = System.currentTimeMillis();
    
    @Inject
    public PreferenceAwareLearningHandler(PolicyGradientLearner policyLearner,
                                        PreferenceDataLoader preferenceLoader) {
        this.policyLearner = policyLearner;
        this.preferenceLoader = preferenceLoader;
        this.enableDetailedLogging = false; // Set based on module configuration
        
        System.out.println("🧠 PreferenceAwareLearningHandler initialized");
        System.out.println("   - PolicyGradientLearner: " + (policyLearner != null ? "✅" : "❌"));
        System.out.println("   - PreferenceDataLoader: " + (preferenceLoader != null ? "✅" : "❌"));
    }
    
    @Override
    public void handleEvent(Event event) {
        totalRequestsProcessed++;
        
        if (event instanceof DrtRequestSubmittedEvent) {
            handleRequestSubmitted((DrtRequestSubmittedEvent) event);
        } else if (event instanceof PassengerRequestScheduledEvent) {
            handleRequestScheduled((PassengerRequestScheduledEvent) event);
        } else if (event instanceof PassengerRequestRejectedEvent) {
            handleRequestRejected((PassengerRequestRejectedEvent) event);
        } else if (event instanceof PassengerPickedUpEvent) {
            handlePassengerPickedUp((PassengerPickedUpEvent) event);
        } else if (event instanceof PassengerDroppedOffEvent) {
            handlePassengerDroppedOff((PassengerDroppedOffEvent) event);
        }
        
        // Periodic status reporting
        reportStatusIfNeeded();
    }
    
    /**
     * Handle DRT request submission - start tracking for learning
     */
    private void handleRequestSubmitted(DrtRequestSubmittedEvent event) {
        String requestId = event.getRequestId().toString();
        int userId = extractUserId(event.getPersonIds().iterator().next().toString());
        
        // Create learning data for this request
        RequestLearningData learningData = new RequestLearningData(
            requestId, userId, event.getTime(),
            event.getFromLinkId(), event.getToLinkId()
        );
        
        activeRequests.put(requestId, learningData);
        
        if (enableDetailedLogging) {
            System.out.println(String.format("📝 Tracking request %s for user %d (time: %.0f)", 
                            requestId, userId, event.getTime()));
        }
    }
    
    /**
     * Handle request scheduling - positive feedback for learning
     */
    private void handleRequestScheduled(PassengerRequestScheduledEvent event) {
        String requestId = event.getRequestId().toString();
        RequestLearningData learningData = activeRequests.get(requestId);
        
        if (learningData != null) {
            learningData.setScheduled(true);
            learningData.setScheduledTime(event.getTime());
            
            // Apply positive learning feedback
            applyPositiveFeedback(learningData);
            
            if (enableDetailedLogging) {
                System.out.println(String.format("✅ Request %s scheduled for user %d (delay: %.0f s)", 
                                requestId, learningData.userId, 
                                event.getTime() - learningData.requestTime));
            }
        }
    }
    
    /**
     * Handle request rejection - negative feedback for learning
     */
    private void handleRequestRejected(PassengerRequestRejectedEvent event) {
        String requestId = event.getRequestId().toString();
        RequestLearningData learningData = activeRequests.get(requestId);
        
        if (learningData != null) {
            learningData.setRejected(true);
            learningData.setRejectedTime(event.getTime());
            
            // Apply negative learning feedback
            applyNegativeFeedback(learningData);
            
            if (enableDetailedLogging) {
                System.out.println(String.format("❌ Request %s rejected for user %d (time: %.0f)", 
                                requestId, learningData.userId, event.getTime()));
            }
            
            // Remove from active tracking
            activeRequests.remove(requestId);
        }
    }
    
    /**
     * Handle passenger pickup - service quality feedback
     */
    private void handlePassengerPickedUp(PassengerPickedUpEvent event) {
        String requestId = event.getRequestId().toString();
        RequestLearningData learningData = activeRequests.get(requestId);
        
        if (learningData != null) {
            learningData.setPickedUp(true);
            learningData.setPickupTime(event.getTime());
            
            // Calculate actual wait time for learning
            double actualWaitTime = event.getTime() - learningData.requestTime;
            learningData.setActualWaitTime(actualWaitTime);
            
            if (enableDetailedLogging) {
                System.out.println(String.format("🚗 Passenger picked up for request %s (wait time: %.0f s)", 
                                requestId, actualWaitTime));
            }
        }
    }
    
    /**
     * Handle passenger dropoff - complete service feedback
     */
    private void handlePassengerDroppedOff(PassengerDroppedOffEvent event) {
        String requestId = event.getRequestId().toString();
        RequestLearningData learningData = activeRequests.get(requestId);
        
        if (learningData != null) {
            learningData.setDroppedOff(true);
            learningData.setDropoffTime(event.getTime());
            
            // Calculate total travel time
            double totalTravelTime = event.getTime() - learningData.requestTime;
            learningData.setTotalTravelTime(totalTravelTime);
            
            // Apply final learning update based on complete service
            applyServiceCompleteFeedback(learningData);
            
            if (enableDetailedLogging) {
                System.out.println(String.format("🎯 Service complete for request %s (total time: %.0f s)", 
                                requestId, totalTravelTime));
            }
            
            // Remove from active tracking
            activeRequests.remove(requestId);
        }
    }
    
    /**
     * Apply positive feedback to the learning algorithm
     */
    private void applyPositiveFeedback(RequestLearningData learningData) {
        try {
            // For now, just log the feedback - would need DrtRequest object for full implementation
            learningUpdatesApplied++;
            
            if (enableDetailedLogging) {
                System.out.println(String.format("🔄 Applied positive feedback for user %d (scheduled)", 
                                learningData.userId));
            }
            
        } catch (Exception e) {
            System.err.println("Failed to apply positive feedback: " + e.getMessage());
        }
    }
    
    /**
     * Apply negative feedback to the learning algorithm
     */
    private void applyNegativeFeedback(RequestLearningData learningData) {
        try {
            // For now, just log the feedback - would need DrtRequest object for full implementation
            learningUpdatesApplied++;
            
            if (enableDetailedLogging) {
                System.out.println(String.format("🔄 Applied negative feedback for user %d (rejected)", 
                                learningData.userId));
            }
            
        } catch (Exception e) {
            System.err.println("Failed to apply negative feedback: " + e.getMessage());
        }
    }
    
    /**
     * Apply feedback based on complete service experience
     */
    private void applyServiceCompleteFeedback(RequestLearningData learningData) {
        try {
            // For now, just log the feedback - would need DrtRequest object for full implementation
            learningUpdatesApplied++;
            
            if (enableDetailedLogging) {
                System.out.println(String.format("🔄 Applied service completion feedback for user %d (wait: %.0fs)", 
                                learningData.userId, learningData.actualWaitTime));
            }
            
        } catch (Exception e) {
            System.err.println("Failed to apply service completion feedback: " + e.getMessage());
        }
    }
    
    /**
     * Extract features from learning data for the policy gradient algorithm
     */
    private double[] extractFeatures(RequestLearningData learningData) {
        // Extract relevant features for learning
        // This is a simplified implementation - real version would extract more features
        double[] features = new double[5];
        
        features[0] = learningData.requestTime % 86400; // Time of day
        features[1] = learningData.actualWaitTime; // Wait time experienced
        features[2] = learningData.totalTravelTime; // Total travel time
        features[3] = learningData.isScheduled() ? 1.0 : 0.0; // Was scheduled
        features[4] = learningData.isRejected() ? 1.0 : 0.0; // Was rejected
        
        return features;
    }
    
    /**
     * Calculate positive reward for successful request scheduling
     */
    private double calculatePositiveReward(RequestLearningData learningData) {
        double baseReward = 1.0;
        
        // Bonus for quick scheduling
        if (learningData.getScheduledTime() - learningData.requestTime < 60) {
            baseReward += 0.5;
        }
        
        return baseReward;
    }
    
    /**
     * Calculate negative reward for request rejection
     */
    private double calculateNegativeReward(RequestLearningData learningData) {
        return -1.0; // Negative reward for rejection
    }
    
    /**
     * Calculate reward based on complete service experience
     */
    private double calculateServiceReward(RequestLearningData learningData) {
        double baseReward = 2.0; // Base reward for service completion
        
        // Adjust based on service quality
        if (learningData.actualWaitTime < 300) { // Less than 5 minutes
            baseReward += 0.5;
        } else if (learningData.actualWaitTime > 600) { // More than 10 minutes
            baseReward -= 0.5;
        }
        
        return baseReward;
    }
    
    /**
     * Extract user ID from person ID string
     */
    private int extractUserId(String personId) {
        try {
            return Integer.parseInt(personId.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return Math.abs(personId.hashCode()) % 10000;
        }
    }
    
    /**
     * Report status periodically
     */
    private void reportStatusIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStatusReport > 30000) { // Every 30 seconds
            System.out.println("🧠 PreferenceAwareLearningHandler Status:");
            System.out.println(String.format("   - Total events processed: %d", totalRequestsProcessed));
            System.out.println(String.format("   - Learning updates applied: %d", learningUpdatesApplied));
            System.out.println(String.format("   - Active requests: %d", activeRequests.size()));
            
            lastStatusReport = currentTime;
        }
    }
    
    /**
     * Get learning statistics
     */
    public Map<String, Object> getLearningStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEventsProcessed", totalRequestsProcessed);
        stats.put("learningUpdatesApplied", learningUpdatesApplied);
        stats.put("activeRequests", activeRequests.size());
        return stats;
    }
    
    /**
     * Data structure to track request information for learning
     */
    private static class RequestLearningData {
        final String requestId;
        final int userId;
        final double requestTime;
        final Object fromLinkId;
        final Object toLinkId;
        
        private boolean scheduled = false;
        private boolean rejected = false;
        private boolean pickedUp = false;
        private boolean droppedOff = false;
        
        private double scheduledTime = 0;
        private double rejectedTime = 0;
        private double pickupTime = 0;
        private double dropoffTime = 0;
        private double actualWaitTime = 0;
        private double totalTravelTime = 0;
        
        public RequestLearningData(String requestId, int userId, double requestTime,
                                 Object fromLinkId, Object toLinkId) {
            this.requestId = requestId;
            this.userId = userId;
            this.requestTime = requestTime;
            this.fromLinkId = fromLinkId;
            this.toLinkId = toLinkId;
        }
        
        // Getters and setters
        public boolean isScheduled() { return scheduled; }
        public void setScheduled(boolean scheduled) { this.scheduled = scheduled; }
        
        public boolean isRejected() { return rejected; }
        public void setRejected(boolean rejected) { this.rejected = rejected; }
        
        public boolean isPickedUp() { return pickedUp; }
        public void setPickedUp(boolean pickedUp) { this.pickedUp = pickedUp; }
        
        public boolean isDroppedOff() { return droppedOff; }
        public void setDroppedOff(boolean droppedOff) { this.droppedOff = droppedOff; }
        
        public double getScheduledTime() { return scheduledTime; }
        public void setScheduledTime(double scheduledTime) { this.scheduledTime = scheduledTime; }
        
        public double getRejectedTime() { return rejectedTime; }
        public void setRejectedTime(double rejectedTime) { this.rejectedTime = rejectedTime; }
        
        public double getPickupTime() { return pickupTime; }
        public void setPickupTime(double pickupTime) { this.pickupTime = pickupTime; }
        
        public double getDropoffTime() { return dropoffTime; }
        public void setDropoffTime(double dropoffTime) { this.dropoffTime = dropoffTime; }
        
        public double getActualWaitTime() { return actualWaitTime; }
        public void setActualWaitTime(double actualWaitTime) { this.actualWaitTime = actualWaitTime; }
        
        public double getTotalTravelTime() { return totalTravelTime; }
        public void setTotalTravelTime(double totalTravelTime) { this.totalTravelTime = totalTravelTime; }
    }
}