package org.matsim.maas.preference.cost;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.optimizer.insertion.InsertionCostCalculator;
import org.matsim.contrib.drt.optimizer.insertion.InsertionGenerator.Insertion;
import org.matsim.contrib.drt.optimizer.insertion.InsertionDetourTimeCalculator.DetourTimeInfo;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.maas.preference.data.UserPreferenceStore;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;

/**
 * Preference-aware insertion cost calculator that wraps MATSim's default calculator
 * and applies user preference-based adjustments using composition pattern.
 * 
 * Key features:
 * - Preserves MATSim's default cost structure when preferences disabled
 * - Applies preference adjustments as multiplicative factors (±20% max)
 * - Fallback to default cost on any calculation errors
 * - Proper integration with MATSim's optimization algorithm
 */
public class PrefAwareInsertionCostCalculator implements InsertionCostCalculator {
    
    private final InsertionCostCalculator defaultCalculator;
    private final UserPreferenceStore preferenceStore;
    private final boolean usePreferences;
    
    // Preference adjustment parameters
    private static final double MAX_ADJUSTMENT_FACTOR = 0.2; // ±20% max adjustment
    private static final double WALKING_SPEED = 1.34; // m/s (4.8 km/h)
    
    // Normalization parameters for utility scaling
    private static final double TYPICAL_MAX_UTILITY = 500.0; // Empirical max utility for normalization
    
    public PrefAwareInsertionCostCalculator(InsertionCostCalculator defaultCalculator,
                                          UserPreferenceStore preferenceStore,
                                          boolean usePreferences) {
        this.defaultCalculator = defaultCalculator;
        this.preferenceStore = preferenceStore;
        this.usePreferences = usePreferences;
        
        if (defaultCalculator == null) {
            throw new IllegalArgumentException("Default calculator cannot be null");
        }
        if (preferenceStore == null) {
            throw new IllegalArgumentException("Preference store cannot be null");
        }
        
        System.out.println("PrefAwareInsertionCostCalculator: Initialized with preferences " +
                          (usePreferences ? "enabled" : "disabled"));
    }
    
    @Override
    public double calculate(DrtRequest request, Insertion insertion, DetourTimeInfo detourTimeInfo) {
        // Input validation
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (insertion == null) {
            throw new IllegalArgumentException("Insertion cannot be null");
        }
        
        // Always get the default cost first
        double defaultCost = defaultCalculator.calculate(request, insertion, detourTimeInfo);
        
        // If preferences disabled, return default cost
        if (!usePreferences) {
            return defaultCost;
        }
        
        try {
            // Apply preference-based adjustment
            double adjustmentFactor = calculatePreferenceAdjustmentFactor(request, insertion, detourTimeInfo);
            double adjustedCost = defaultCost * adjustmentFactor;
            
            return adjustedCost;
            
        } catch (Exception e) {
            // Fallback to default cost on any error
            System.err.println("Warning: Preference calculation failed, using default cost. Error: " + e.getMessage());
            return defaultCost;
        }
    }
    
    /**
     * Calculate preference-based adjustment factor for the default cost.
     * @return Factor between 0.8 and 1.2 (±20% adjustment)
     */
    private double calculatePreferenceAdjustmentFactor(DrtRequest request, Insertion insertion, 
                                                     DetourTimeInfo detourTimeInfo) {
        // If no detour time info, can't calculate preferences
        if (detourTimeInfo == null) {
            return 1.0; // No adjustment
        }
        
        // Get user preference data
        Id<Person> personId = request.getPassengerIds().iterator().next();
        UserPreferenceData prefData = preferenceStore.getUserPreference(personId);
        
        if (prefData == null) {
            return 1.0; // No adjustment when no preference data
        }
        
        // Extract time components from insertion
        double accessTime = calculateAccessTime(request, insertion);
        double waitTime = calculateWaitTime(detourTimeInfo);
        double ivtTime = calculateInVehicleTime(request, insertion, detourTimeInfo);
        double egressTime = calculateEgressTime(request, insertion);
        
        // Calculate preference utility
        double utility = prefData.calculateUtility(accessTime, waitTime, ivtTime, egressTime);
        
        // Normalize utility to [-1, 1] range
        double normalizedUtility = Math.max(-1.0, Math.min(1.0, utility / TYPICAL_MAX_UTILITY));
        
        // Convert to adjustment factor: 1.0 + (normalizedUtility * MAX_ADJUSTMENT_FACTOR)
        // Positive utility (user likes) -> factor < 1.0 -> lower cost
        // Negative utility (user dislikes) -> factor > 1.0 -> higher cost
        double adjustmentFactor = 1.0 + (-normalizedUtility * MAX_ADJUSTMENT_FACTOR);
        
        // Ensure factor stays within bounds [0.8, 1.2]
        adjustmentFactor = Math.max(0.8, Math.min(1.2, adjustmentFactor));
        
        return adjustmentFactor;
    }
    
    /**
     * Calculate access time (walking from origin to pickup point)
     */
    private double calculateAccessTime(DrtRequest request, Insertion insertion) {
        if (insertion.pickup == null || insertion.pickup.newWaypoint == null) {
            return 0.0;
        }
        
        double distance = CoordUtils.calcEuclideanDistance(
            request.getFromLink().getCoord(),
            insertion.pickup.newWaypoint.getLink().getCoord()
        );
        
        return distance / WALKING_SPEED;
    }
    
    /**
     * Calculate wait time from detour time info
     */
    private double calculateWaitTime(DetourTimeInfo detourTimeInfo) {
        if (detourTimeInfo.pickupDetourInfo == null) {
            return 300.0; // Default 5 minutes
        }
        return detourTimeInfo.pickupDetourInfo.pickupTimeLoss;
    }
    
    /**
     * Calculate in-vehicle time including detours
     */
    private double calculateInVehicleTime(DrtRequest request, Insertion insertion, DetourTimeInfo detourTimeInfo) {
        if (detourTimeInfo.dropoffDetourInfo == null) {
            // Fallback: direct travel time with sharing factor
            double directTime = calculateDirectTravelTime(request);
            return directTime * 1.3; // 30% longer due to sharing
        }
        
        return detourTimeInfo.dropoffDetourInfo.dropoffTimeLoss;
    }
    
    /**
     * Calculate egress time (walking from dropoff point to destination)
     */
    private double calculateEgressTime(DrtRequest request, Insertion insertion) {
        if (insertion.dropoff == null || insertion.dropoff.newWaypoint == null) {
            return 0.0;
        }
        
        double distance = CoordUtils.calcEuclideanDistance(
            insertion.dropoff.newWaypoint.getLink().getCoord(),
            request.getToLink().getCoord()
        );
        
        return distance / WALKING_SPEED;
    }
    
    /**
     * Calculate direct travel time between origin and destination
     */
    private double calculateDirectTravelTime(DrtRequest request) {
        double distance = CoordUtils.calcEuclideanDistance(
            request.getFromLink().getCoord(),
            request.getToLink().getCoord()
        );
        
        double averageSpeed = 8.33; // m/s (30 km/h)
        return distance / averageSpeed;
    }
} 