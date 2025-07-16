package org.matsim.maas.preference.cost;

import javax.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.optimizer.insertion.InsertionCostCalculator;
import org.matsim.contrib.drt.optimizer.insertion.InsertionGenerator.Insertion;
import org.matsim.contrib.drt.optimizer.insertion.InsertionDetourTimeCalculator.DetourTimeInfo;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.maas.preference.data.UserPreferenceStore;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;
import org.matsim.core.utils.geometry.CoordUtils;

/**
 * Preference-aware cost calculator that uses individual user utility weights
 * to calculate personalized costs for DRT dispatch decisions.
 * Implements MATSim's InsertionCostCalculator interface for proper integration.
 */
public class PrefCostCalculator implements InsertionCostCalculator {
    
    private final UserPreferenceStore preferenceStore;
    private final boolean usePreferenceWeights;
    
    // Default weights for users without preference data
    private static final double DEFAULT_ACCESS_WEIGHT = 0.2;
    private static final double DEFAULT_WAIT_WEIGHT = 0.5;
    private static final double DEFAULT_IVT_WEIGHT = 0.3;
    private static final double DEFAULT_EGRESS_WEIGHT = 0.1;
    
    @Inject
    public PrefCostCalculator(UserPreferenceStore preferenceStore, 
                             boolean usePreferenceWeights) {
        this.preferenceStore = preferenceStore;
        this.usePreferenceWeights = usePreferenceWeights;
        
        System.out.println("PrefCostCalculator: Initialized with " + 
                          (usePreferenceWeights ? "preference-aware" : "default") + " cost calculation");
        
        // Validation guards as recommended by guidelines
        if (preferenceStore == null) {
            throw new IllegalArgumentException("UserPreferenceStore cannot be null");
        }
    }
    
    @Override
    public double calculate(DrtRequest request, Insertion insertion, DetourTimeInfo detourTimeInfo) {
        // Input validation guards
        if (request == null || insertion == null) {
            throw new IllegalArgumentException("Request and insertion cannot be null");
        }
        
        // Simple baseline cost calculation
        double baseCost = calculateSimpleBaseCost(request, insertion, detourTimeInfo);
        
        if (!usePreferenceWeights) {
            return baseCost;
        }
        
        Id<Person> personId = request.getPassengerIds().iterator().next();
        
        // Get user preference data
        UserPreferenceData prefData = preferenceStore.getUserPreference(personId);
        if (prefData == null) {
            // No preference data available, use baseline cost with default weights
            double accessTime = calculateAccessTime(request, insertion);
            double waitTime = calculateWaitTime(request, insertion, detourTimeInfo);
            double ivtTime = calculateInVehicleTime(request, insertion, detourTimeInfo);
            double egressTime = calculateEgressTime(request, insertion);
            
            // Calculate default utility and convert to cost adjustment
            double defaultUtility = calculateDefaultUtility(accessTime, waitTime, ivtTime, egressTime);
            double defaultCostAdjustment = -defaultUtility * 0.1; // Same scaling as preference weights
            return baseCost + defaultCostAdjustment;
        }
        
        // Calculate cost components using actual insertion timing data
        double accessTime = calculateAccessTime(request, insertion);
        double waitTime = calculateWaitTime(request, insertion, detourTimeInfo);
        double ivtTime = calculateInVehicleTime(request, insertion, detourTimeInfo);
        double egressTime = calculateEgressTime(request, insertion);
        
        // Add NaN guards as recommended by guidelines
        assert !Double.isNaN(accessTime) : "Access time calculation resulted in NaN";
        assert !Double.isNaN(waitTime) : "Wait time calculation resulted in NaN";
        assert !Double.isNaN(ivtTime) : "IVT calculation resulted in NaN";
        assert !Double.isNaN(egressTime) : "Egress time calculation resulted in NaN";
        
        // Calculate preference-adjusted cost
        // Note: prefData.calculateUtility returns a UTILITY (higher = better)
        // We need to convert this to a COST (lower = better)
        double utility = prefData.calculateUtility(accessTime, waitTime, ivtTime, egressTime);
        
        // Convert utility to cost: cost = -utility (since higher utility = lower cost)
        // Scale utility to be comparable to baseline costs (which are in seconds)
        // Utilities are typically in hundreds, baseline costs in tens to hundreds of seconds
        double prefCostAdjustment = -utility * 0.1; // Reduce scaling significantly
        
        // Combine baseline cost with preference adjustment
        double finalCost = baseCost + prefCostAdjustment;
        assert !Double.isNaN(finalCost) : "Final cost calculation resulted in NaN";
        
        return finalCost;
    }
    
    /**
     * Simple baseline cost calculation when no base calculator is available
     */
    private double calculateSimpleBaseCost(DrtRequest request, Insertion insertion, DetourTimeInfo detourTimeInfo) {
        // Simple cost: primarily based on detour time losses
        double cost = 0.0;
        
        if (detourTimeInfo != null) {
            cost += detourTimeInfo.pickupDetourInfo.pickupTimeLoss * 2.0; // Weight wait time more heavily
            cost += detourTimeInfo.dropoffDetourInfo.dropoffTimeLoss * 1.0; // In-vehicle time detour
        }
        
        // Add some base cost for the insertion
        cost += 60.0; // Base 1-minute cost for any insertion
        
        return cost;
    }
    
    
    /**
     * Calculate access time (time from origin to pickup)
     */
    private double calculateAccessTime(DrtRequest request, Insertion insertion) {
        if (insertion.pickup.newWaypoint == null) {
            return 0.0;
        }
        double distance = CoordUtils.calcEuclideanDistance(request.getFromLink().getCoord(), insertion.pickup.newWaypoint.getLink().getCoord());
        double walkingSpeed = 1.34; // m/s ≈ 4.8 km/h
        return distance / walkingSpeed;
    }
    
    /**
     * Calculate wait time (time from request to pickup)
     */
    private double calculateWaitTime(DrtRequest request, Insertion insertion, DetourTimeInfo detourTimeInfo) {
        if (detourTimeInfo != null && detourTimeInfo.pickupDetourInfo != null) {
            return detourTimeInfo.pickupDetourInfo.pickupTimeLoss;
        }
        return 300.0; // 5 minutes default
    }
    
    /**
     * Calculate in-vehicle time (time from pickup to dropoff)
     */
    private double calculateInVehicleTime(DrtRequest request, Insertion insertion, DetourTimeInfo detourTimeInfo) {
        if (detourTimeInfo != null && detourTimeInfo.pickupDetourInfo != null && detourTimeInfo.dropoffDetourInfo != null) {
            return detourTimeInfo.dropoffDetourInfo.dropoffTimeLoss;
        }
        return calculateDirectTravelTime(request) * 1.3;
    }
    
    /**
     * Calculate egress time (time from dropoff to destination)
     */
    private double calculateEgressTime(DrtRequest request, Insertion insertion) {
        if (insertion.dropoff.newWaypoint == null) {
            return 0.0;
        }
        double distance = CoordUtils.calcEuclideanDistance(insertion.dropoff.newWaypoint.getLink().getCoord(), request.getToLink().getCoord());
        double walkingSpeed = 1.34; // m/s
        return distance / walkingSpeed;
    }
    
    /**
     * Calculate direct travel time between origin and destination
     */
    private double calculateDirectTravelTime(DrtRequest request) {
        // Simple estimation based on euclidean distance
        // In a real implementation, this would use the network and travel time calculator
        double distance = Math.sqrt(
            Math.pow(request.getFromLink().getCoord().getX() - request.getToLink().getCoord().getX(), 2) +
            Math.pow(request.getFromLink().getCoord().getY() - request.getToLink().getCoord().getY(), 2)
        );
        
        // Assume average speed of 30 km/h (8.33 m/s)
        double averageSpeed = 8.33; // m/s
        return distance / averageSpeed; // travel time in seconds
    }
    
    /**
     * Calculate default utility using standard weights
     * Note: These are UTILITY weights (negative coefficients for time components)
     */
    private double calculateDefaultUtility(double accessTime, double waitTime, 
                                          double ivtTime, double egressTime) {
        // Use negative weights to represent disutility (time components reduce utility)
        return -DEFAULT_ACCESS_WEIGHT * accessTime + 
               -DEFAULT_WAIT_WEIGHT * waitTime + 
               -DEFAULT_IVT_WEIGHT * ivtTime + 
               -DEFAULT_EGRESS_WEIGHT * egressTime;
    }
    
    /**
     * Get preference data for a specific user
     */
    public UserPreferenceData getUserPreference(Id<Person> personId) {
        return preferenceStore.getUserPreference(personId);
    }
    
    /**
     * Check if preference weights are being used
     */
    public boolean isUsingPreferenceWeights() {
        return usePreferenceWeights;
    }
    
    /**
     * Get statistics about preference data usage
     */
    public PrefCostStats getStats() {
        return new PrefCostStats(
            preferenceStore.getTotalUsers(),
            preferenceStore.getTotalUsersWithHistory(),
            usePreferenceWeights
        );
    }
    
    /**
     * Statistics about preference cost calculation
     */
    public static class PrefCostStats {
        private final int totalUsersWithPreferences;
        private final int totalUsersWithHistory;
        private final boolean usingPreferenceWeights;
        
        public PrefCostStats(int totalUsersWithPreferences, int totalUsersWithHistory, 
                            boolean usingPreferenceWeights) {
            this.totalUsersWithPreferences = totalUsersWithPreferences;
            this.totalUsersWithHistory = totalUsersWithHistory;
            this.usingPreferenceWeights = usingPreferenceWeights;
        }
        
        public int getTotalUsersWithPreferences() { return totalUsersWithPreferences; }
        public int getTotalUsersWithHistory() { return totalUsersWithHistory; }
        public boolean isUsingPreferenceWeights() { return usingPreferenceWeights; }
        
        @Override
        public String toString() {
            return String.format("PrefCostStats{usersWithPrefs=%d, usersWithHistory=%d, usingPrefs=%b}", 
                               totalUsersWithPreferences, totalUsersWithHistory, usingPreferenceWeights);
        }
    }
}