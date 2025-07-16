package org.matsim.maas.rl.reward;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.preference.data.UserPreferenceStore;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;
import org.matsim.maas.rl.events.DrtRequestRecord;
import org.matsim.maas.rl.events.PersonTripRecord;

/**
 * Calculates rewards for RL algorithms based on user preferences and system performance.
 * Implements multi-objective reward function combining user satisfaction and system efficiency.
 */
public class RewardCalculator {
    
    private final UserPreferenceStore preferenceStore;
    
    // Reward weights for different objectives
    private static final double USER_SATISFACTION_WEIGHT = 0.6;
    private static final double SYSTEM_EFFICIENCY_WEIGHT = 0.3;
    private static final double FAIRNESS_WEIGHT = 0.1;
    
    // Reward scaling factors
    private static final double BASE_SCHEDULING_REWARD = 10.0;
    private static final double BASE_REJECTION_PENALTY = -15.0;
    private static final double BASE_COMPLETION_REWARD = 20.0;
    
    // Performance thresholds
    private static final double TARGET_WAIT_TIME = 300.0; // 5 minutes
    private static final double TARGET_TRAVEL_TIME_FACTOR = 1.3; // 30% longer than direct
    private static final double MAX_ACCEPTABLE_WAIT = 900.0; // 15 minutes
    
    public RewardCalculator(UserPreferenceStore preferenceStore) {
        this.preferenceStore = preferenceStore;
        System.out.println("RewardCalculator: Initialized with preference-aware reward calculation");
    }
    
    /**
     * Calculate reward for successfully scheduling a request
     */
    public double calculateSchedulingReward(DrtRequestRecord requestRecord, UserPreferenceStore preferenceStore) {
        Id<Person> personId = requestRecord.getPersonId();
        double waitTime = requestRecord.getWaitTime();
        
        // Base reward for successful scheduling
        double baseReward = BASE_SCHEDULING_REWARD;
        
        // User satisfaction component
        double userSatisfactionReward = calculateUserSatisfactionReward(personId, waitTime, 0.0, 0.0, 0.0);
        
        // System efficiency component
        double efficiencyReward = calculateWaitTimeReward(waitTime);
        
        // Combine components
        double totalReward = baseReward + 
                            USER_SATISFACTION_WEIGHT * userSatisfactionReward +
                            SYSTEM_EFFICIENCY_WEIGHT * efficiencyReward;
        
        return totalReward;
    }
    
    /**
     * Calculate penalty for rejecting a request
     */
    public double calculateRejectionPenalty(DrtRequestRecord requestRecord, UserPreferenceStore preferenceStore) {
        Id<Person> personId = requestRecord.getPersonId();
        
        // Base penalty for rejection
        double basePenalty = BASE_REJECTION_PENALTY;
        
        // Additional penalty based on user preferences
        UserPreferenceData prefData = preferenceStore.getUserPreference(personId);
        double userPenalty = 0.0;
        
        if (prefData != null) {
            // Users with high wait time sensitivity get higher penalty for rejection
            userPenalty = -Math.abs(prefData.getWaitWeight()) * 5.0;
        }
        
        return basePenalty + userPenalty;
    }
    
    /**
     * Calculate reward for completing a trip
     */
    public double calculateCompletionReward(DrtRequestRecord requestRecord, 
                                          PersonTripRecord tripRecord, 
                                          UserPreferenceStore preferenceStore) {
        Id<Person> personId = requestRecord.getPersonId();
        double waitTime = requestRecord.getWaitTime();
        double travelTime = tripRecord.getTravelTime();
        double inVehicleTime = requestRecord.getInVehicleTime();
        
        // Base reward for completion
        double baseReward = BASE_COMPLETION_REWARD;
        
        // User satisfaction based on actual trip experience
        double userSatisfactionReward = calculateUserSatisfactionReward(
            personId, waitTime, inVehicleTime, travelTime, 0.0);
        
        // System efficiency rewards
        double efficiencyReward = calculateTravelTimeReward(travelTime) + 
                                 calculateWaitTimeReward(waitTime);
        
        // Quality bonus for good service
        double qualityBonus = calculateQualityBonus(waitTime, travelTime);
        
        // Combine components
        double totalReward = baseReward + 
                            USER_SATISFACTION_WEIGHT * userSatisfactionReward +
                            SYSTEM_EFFICIENCY_WEIGHT * efficiencyReward +
                            qualityBonus;
        
        return totalReward;
    }
    
    /**
     * Calculate user satisfaction reward based on preferences
     */
    private double calculateUserSatisfactionReward(Id<Person> personId, 
                                                  double accessTime, 
                                                  double waitTime, 
                                                  double ivtTime, 
                                                  double egressTime) {
        UserPreferenceData prefData = preferenceStore.getUserPreference(personId);
        
        if (prefData == null) {
            // Use default satisfaction calculation
            return calculateDefaultSatisfactionReward(accessTime, waitTime, ivtTime, egressTime);
        }
        
        // Calculate utility based on user preferences
        double utility = prefData.calculateUtility(accessTime, waitTime, ivtTime, egressTime);
        
        // Convert utility to reward (higher utility = higher reward)
        // Since utilities can be negative, we need to scale appropriately
        double satisfactionReward = -utility; // Negative utility becomes positive reward
        
        // Apply normalization and scaling
        return Math.max(-20.0, Math.min(20.0, satisfactionReward));
    }
    
    /**
     * Calculate default satisfaction reward when no preference data available
     */
    private double calculateDefaultSatisfactionReward(double accessTime, double waitTime, 
                                                     double ivtTime, double egressTime) {
        // Standard satisfaction function - penalize longer times
        double waitPenalty = Math.min(waitTime / TARGET_WAIT_TIME, 3.0) * -5.0;
        double travelPenalty = Math.min(ivtTime / (TARGET_TRAVEL_TIME_FACTOR * 600), 2.0) * -3.0;
        
        return waitPenalty + travelPenalty;
    }
    
    /**
     * Calculate reward based on wait time performance
     */
    private double calculateWaitTimeReward(double waitTime) {
        if (waitTime <= TARGET_WAIT_TIME) {
            // Reward for meeting target
            return 5.0 * (1.0 - waitTime / TARGET_WAIT_TIME);
        } else if (waitTime <= MAX_ACCEPTABLE_WAIT) {
            // Graduated penalty for longer waits
            double penalty = -5.0 * ((waitTime - TARGET_WAIT_TIME) / (MAX_ACCEPTABLE_WAIT - TARGET_WAIT_TIME));
            return penalty;
        } else {
            // Heavy penalty for excessive wait times
            return -10.0;
        }
    }
    
    /**
     * Calculate reward based on travel time efficiency
     */
    private double calculateTravelTimeReward(double actualTravelTime) {
        // Estimate direct travel time (simplified)
        double estimatedDirectTime = actualTravelTime / TARGET_TRAVEL_TIME_FACTOR;
        double efficiency = estimatedDirectTime / actualTravelTime;
        
        if (efficiency >= 0.8) {
            // Reward for efficient travel
            return 3.0 * efficiency;
        } else {
            // Penalty for inefficient travel
            return -3.0 * (0.8 - efficiency);
        }
    }
    
    /**
     * Calculate quality bonus for exceptional service
     */
    private double calculateQualityBonus(double waitTime, double travelTime) {
        double bonus = 0.0;
        
        // Bonus for very short wait times
        if (waitTime <= TARGET_WAIT_TIME * 0.5) {
            bonus += 2.0;
        }
        
        // Bonus for efficient travel
        if (travelTime <= 600.0) { // Less than 10 minutes
            bonus += 1.0;
        }
        
        return bonus;
    }
    
    /**
     * Calculate fairness reward to prevent bias against certain user types
     */
    public double calculateFairnessReward(Id<Person> personId, double serviceLevel) {
        // Simple fairness mechanism - could be enhanced with demographic data
        UserPreferenceData prefData = preferenceStore.getUserPreference(personId);
        
        if (prefData != null) {
            // Encourage serving users with different preference profiles
            double preferenceDiversity = Math.abs(prefData.getWaitWeight()) + 
                                        Math.abs(prefData.getAccessWeight()) +
                                        Math.abs(prefData.getIvtWeight()) +
                                        Math.abs(prefData.getEgressWeight());
            
            return FAIRNESS_WEIGHT * preferenceDiversity * serviceLevel;
        }
        
        return 0.0;
    }
    
    /**
     * Calculate aggregate system performance reward
     */
    public double calculateSystemPerformanceReward(double serviceRate, 
                                                  double averageWaitTime, 
                                                  double vehicleUtilization) {
        double serviceReward = serviceRate * 10.0; // Reward high service rates
        double waitPenalty = -Math.max(0, averageWaitTime - TARGET_WAIT_TIME) / 60.0; // Penalty for long waits
        double utilizationReward = vehicleUtilization * 5.0; // Reward efficient vehicle use
        
        return serviceReward + waitPenalty + utilizationReward;
    }
    
    /**
     * Get reward statistics for analysis
     */
    public RewardStats getRewardStats() {
        return new RewardStats(
            USER_SATISFACTION_WEIGHT,
            SYSTEM_EFFICIENCY_WEIGHT,
            FAIRNESS_WEIGHT,
            TARGET_WAIT_TIME,
            TARGET_TRAVEL_TIME_FACTOR
        );
    }
    
    /**
     * Reward statistics container
     */
    public static class RewardStats {
        public final double userSatisfactionWeight;
        public final double systemEfficiencyWeight;
        public final double fairnessWeight;
        public final double targetWaitTime;
        public final double targetTravelTimeFactor;
        
        public RewardStats(double userSatisfactionWeight, double systemEfficiencyWeight, 
                          double fairnessWeight, double targetWaitTime, double targetTravelTimeFactor) {
            this.userSatisfactionWeight = userSatisfactionWeight;
            this.systemEfficiencyWeight = systemEfficiencyWeight;
            this.fairnessWeight = fairnessWeight;
            this.targetWaitTime = targetWaitTime;
            this.targetTravelTimeFactor = targetTravelTimeFactor;
        }
        
        @Override
        public String toString() {
            return String.format("RewardStats{userWeight=%.2f, sysWeight=%.2f, fairWeight=%.2f, targetWait=%.0fs}", 
                               userSatisfactionWeight, systemEfficiencyWeight, fairnessWeight, targetWaitTime);
        }
    }
}