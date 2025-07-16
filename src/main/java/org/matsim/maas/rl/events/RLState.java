package org.matsim.maas.rl.events;

import java.util.Objects;

/**
 * Represents the state in the RL environment for DRT dispatching decisions.
 * Contains contextual information about the current system state when decisions are made.
 */
public class RLState {
    
    private final double currentTime;
    private final int activeRequests;
    private final int availableVehicles;
    private final int busyVehicles;
    private final double averageWaitTime;
    private final int recentRejections;
    private final double systemLoad; // ratio of requests to vehicles
    
    // Time-based features
    private final int hourOfDay;
    private final boolean isRushHour;
    
    // Spatial features (simplified)
    private final int dominantOriginZone;
    private final int dominantDestinationZone;
    
    public RLState(double currentTime, 
                   int activeRequests, 
                   int availableVehicles, 
                   int busyVehicles,
                   double averageWaitTime, 
                   int recentRejections,
                   int dominantOriginZone,
                   int dominantDestinationZone) {
        this.currentTime = currentTime;
        this.activeRequests = activeRequests;
        this.availableVehicles = availableVehicles;
        this.busyVehicles = busyVehicles;
        this.averageWaitTime = averageWaitTime;
        this.recentRejections = recentRejections;
        this.dominantOriginZone = dominantOriginZone;
        this.dominantDestinationZone = dominantDestinationZone;
        
        // Derived features
        this.systemLoad = (availableVehicles + busyVehicles) > 0 ? 
            (double) activeRequests / (availableVehicles + busyVehicles) : 0.0;
        
        this.hourOfDay = (int) (currentTime / 3600) % 24;
        this.isRushHour = (hourOfDay >= 7 && hourOfDay <= 9) || (hourOfDay >= 17 && hourOfDay <= 19);
    }
    
    // Getters
    public double getCurrentTime() { return currentTime; }
    public int getActiveRequests() { return activeRequests; }
    public int getAvailableVehicles() { return availableVehicles; }
    public int getBusyVehicles() { return busyVehicles; }
    public double getAverageWaitTime() { return averageWaitTime; }
    public int getRecentRejections() { return recentRejections; }
    public double getSystemLoad() { return systemLoad; }
    public int getHourOfDay() { return hourOfDay; }
    public boolean isRushHour() { return isRushHour; }
    public int getDominantOriginZone() { return dominantOriginZone; }
    public int getDominantDestinationZone() { return dominantDestinationZone; }
    
    /**
     * Convert state to feature vector for RL algorithms
     */
    public double[] toFeatureVector() {
        return new double[] {
            // Normalized system state features
            Math.min(activeRequests / 50.0, 1.0),  // Normalize to [0,1] assuming max 50 active requests
            Math.min(availableVehicles / 20.0, 1.0), // Normalize assuming max 20 vehicles
            Math.min(busyVehicles / 20.0, 1.0),
            Math.min(averageWaitTime / 600.0, 1.0), // Normalize assuming max 10 minutes wait
            Math.min(recentRejections / 10.0, 1.0), // Normalize assuming max 10 recent rejections
            Math.min(systemLoad, 1.0), // Cap system load at 1.0 for normalization
            
            // Time features
            hourOfDay / 24.0, // Normalize to [0,1]
            isRushHour ? 1.0 : 0.0, // Binary feature
            
            // Spatial features (normalized zone IDs)
            dominantOriginZone / 72.0, // Normalize assuming 72 zones
            dominantDestinationZone / 72.0
        };
    }
    
    /**
     * Get feature names for interpretation
     */
    public static String[] getFeatureNames() {
        return new String[] {
            "active_requests_norm",
            "available_vehicles_norm", 
            "busy_vehicles_norm",
            "avg_wait_time_norm",
            "recent_rejections_norm",
            "system_load",
            "hour_of_day_norm",
            "is_rush_hour",
            "dominant_origin_zone_norm",
            "dominant_dest_zone_norm"
        };
    }
    
    /**
     * Discretize state for simple Q-learning (if needed)
     */
    public int getDiscreteStateId() {
        // Simple discretization: combine key features into discrete bins
        int loadBin = (int) Math.min(systemLoad * 5, 4); // 5 bins: 0-4
        int timeBin = isRushHour ? 1 : 0; // 2 bins
        int rejectionBin = Math.min(recentRejections / 3, 2); // 3 bins: 0-2
        
        // Combine into single state ID
        return loadBin * 6 + timeBin * 3 + rejectionBin; // Max ID: 29
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RLState rlState = (RLState) o;
        return Double.compare(rlState.currentTime, currentTime) == 0 &&
               activeRequests == rlState.activeRequests &&
               availableVehicles == rlState.availableVehicles &&
               busyVehicles == rlState.busyVehicles &&
               recentRejections == rlState.recentRejections &&
               dominantOriginZone == rlState.dominantOriginZone &&
               dominantDestinationZone == rlState.dominantDestinationZone;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(currentTime, activeRequests, availableVehicles, busyVehicles, 
                          recentRejections, dominantOriginZone, dominantDestinationZone);
    }
    
    @Override
    public String toString() {
        return String.format("RLState{time=%.0f, active=%d, avail=%d, busy=%d, load=%.2f, rush=%b}", 
                           currentTime, activeRequests, availableVehicles, busyVehicles, systemLoad, isRushHour);
    }
}