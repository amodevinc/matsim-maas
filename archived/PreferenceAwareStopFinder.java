package org.matsim.maas.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.optimizer.insertion.InsertionGenerator.Insertion;
import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.drt.stops.StopTimeCalculator;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.optimizer.VrpOptimizer;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.trafficmonitoring.DvrpTravelTimeModule;
import org.matsim.core.router.speedy.SpeedyALTFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Preference-aware stop finder for DRT that considers user preferences 
 * when selecting pickup and dropoff stops.
 * 
 * @author MATSim-MaaS Research Team
 */
public class PreferenceAwareStopFinder {
    
    private final PreferenceDataLoader preferenceLoader;
    private final Map<Id<TransitStopFacility>, TransitStopFacility> drtStops;
    private final LeastCostPathCalculator pathCalculator;
    private final TravelTime travelTime;
    private final StopTimeCalculator stopTimeCalculator;
    
    // Performance parameters
    private final double maxWalkDistance;
    private final double walkSpeed; // m/s
    private final int maxStopCandidates;
    
    // Constructor with dependency injection
    @com.google.inject.Inject
    public PreferenceAwareStopFinder(PreferenceDataLoader preferenceLoader) {
        this.preferenceLoader = preferenceLoader;
        this.drtStops = new HashMap<>(); // Empty for now - would be injected in full implementation
        this.pathCalculator = null; // Would be injected in full implementation
        this.travelTime = null; // Would be injected in full implementation  
        this.stopTimeCalculator = null; // Would be injected in full implementation
        this.maxWalkDistance = 500.0; // 500m default
        this.walkSpeed = 1.2; // Average walking speed 1.2 m/s
        this.maxStopCandidates = 5; // Limit candidates for performance
        
        System.out.println("⚠️ PreferenceAwareStopFinder initialized in simplified mode for proof-of-concept");
    }
    
    // Full constructor for dependency injection (when available)
    public PreferenceAwareStopFinder(PreferenceDataLoader preferenceLoader,
                                   Map<Id<TransitStopFacility>, TransitStopFacility> drtStops,
                                   LeastCostPathCalculator pathCalculator,
                                   TravelTime travelTime,
                                   StopTimeCalculator stopTimeCalculator,
                                   double maxWalkDistance) {
        this.preferenceLoader = preferenceLoader;
        this.drtStops = drtStops;
        this.pathCalculator = pathCalculator;
        this.travelTime = travelTime;
        this.stopTimeCalculator = stopTimeCalculator;
        this.maxWalkDistance = maxWalkDistance;
        this.walkSpeed = 1.2; // Average walking speed 1.2 m/s
        this.maxStopCandidates = 5; // Limit candidates for performance
    }
    
    /**
     * Find best pickup stops for a DRT request considering user preferences
     */
    public List<StopCandidate> findBestPickupStops(DrtRequest request, double currentTime) {
        Id<Person> personId = request.getPassengerIds().iterator().next();
        Link fromLink = request.getFromLink();
        
        // Get user preferences
        PreferenceDataLoader.UserPreferences prefs = preferenceLoader.getUserPreferences(personId.toString());
        
        // Find candidate stops within walking distance
        List<StopCandidate> candidates = findCandidateStops(fromLink, currentTime, true);
        
        // Score candidates based on user preferences
        return candidates.stream()
                .map(candidate -> scoreCandidateForPickup(candidate, prefs, fromLink, currentTime))
                .sorted(Comparator.comparingDouble(c -> c.preferenceScore))
                .limit(maxStopCandidates)
                .collect(Collectors.toList());
    }
    
    /**
     * Find best dropoff stops for a DRT request considering user preferences
     */
    public List<StopCandidate> findBestDropoffStops(DrtRequest request, double currentTime) {
        Id<Person> personId = request.getPassengerIds().iterator().next();
        Link toLink = request.getToLink();
        
        // Get user preferences
        PreferenceDataLoader.UserPreferences prefs = preferenceLoader.getUserPreferences(personId.toString());
        
        // Find candidate stops within walking distance
        List<StopCandidate> candidates = findCandidateStops(toLink, currentTime, false);
        
        // Score candidates based on user preferences
        return candidates.stream()
                .map(candidate -> scoreCandidateForDropoff(candidate, prefs, toLink, currentTime))
                .sorted(Comparator.comparingDouble(c -> c.preferenceScore))
                .limit(maxStopCandidates)
                .collect(Collectors.toList());
    }
    
    /**
     * Find candidate stops within walking distance of a link
     */
    private List<StopCandidate> findCandidateStops(Link targetLink, double currentTime, boolean isPickup) {
        List<StopCandidate> candidates = new ArrayList<>();
        
        double targetX = targetLink.getCoord().getX();
        double targetY = targetLink.getCoord().getY();
        
        for (TransitStopFacility stop : drtStops.values()) {
            double stopX = stop.getCoord().getX();
            double stopY = stop.getCoord().getY();
            
            // Calculate Euclidean distance
            double distance = Math.sqrt(Math.pow(targetX - stopX, 2) + Math.pow(targetY - stopY, 2));
            
            if (distance <= maxWalkDistance) {
                double walkTime = distance / walkSpeed; // seconds
                
                StopCandidate candidate = new StopCandidate(
                    stop, distance, walkTime, currentTime
                );
                candidates.add(candidate);
            }
        }
        
        return candidates;
    }
    
    /**
     * Score a stop candidate for pickup considering user preferences
     */
    private StopCandidate scoreCandidateForPickup(StopCandidate candidate, 
                                                PreferenceDataLoader.UserPreferences prefs,
                                                Link fromLink, double currentTime) {
        
        // Access time is the walking time to the stop
        double accessTime = candidate.walkTime / 60.0; // Convert to minutes
        
        // Estimate wait time based on stop activity/accessibility
        double estimatedWaitTime = estimateWaitTime(candidate.stop, currentTime);
        
        // In-vehicle time and egress time are 0 for pickup (handled at dropoff)
        double ivtTime = 0.0;
        double egressTime = 0.0;
        
        // Calculate preference score (higher = less preferred)
        double preferenceScore = prefs.calculatePreferenceScore(accessTime, estimatedWaitTime, ivtTime, egressTime);
        
        candidate.preferenceScore = preferenceScore;
        candidate.accessTime = accessTime;
        candidate.estimatedWaitTime = estimatedWaitTime;
        
        return candidate;
    }
    
    /**
     * Score a stop candidate for dropoff considering user preferences
     */
    private StopCandidate scoreCandidateForDropoff(StopCandidate candidate,
                                                 PreferenceDataLoader.UserPreferences prefs,
                                                 Link toLink, double currentTime) {
        
        // Access time is 0 for dropoff (handled at pickup)
        double accessTime = 0.0;
        double estimatedWaitTime = 0.0;
        double ivtTime = 0.0; // Will be calculated during insertion
        
        // Egress time is the walking time from the stop
        double egressTime = candidate.walkTime / 60.0; // Convert to minutes
        
        // Calculate preference score (higher = less preferred)
        double preferenceScore = prefs.calculatePreferenceScore(accessTime, estimatedWaitTime, ivtTime, egressTime);
        
        candidate.preferenceScore = preferenceScore;
        candidate.egressTime = egressTime;
        
        return candidate;
    }
    
    /**
     * Estimate wait time at a stop based on location and accessibility
     */
    private double estimateWaitTime(TransitStopFacility stop, double currentTime) {
        // Simple heuristic: base wait time with adjustments
        double baseWaitTime = 5.0; // 5 minutes base
        
        // Adjust based on stop location (could be improved with real data)
        // For now, use stop ID hash for consistent but varied estimates
        int stopHash = Math.abs(stop.getId().toString().hashCode());
        double locationAdjustment = (stopHash % 300) / 60.0; // 0-5 minutes variation
        
        // Adjust based on time of day (peak hours have longer wait times)
        double timeOfDay = (currentTime % (24 * 3600)) / 3600.0; // Hours of day
        double peakAdjustment = 0.0;
        
        if ((timeOfDay >= 7 && timeOfDay <= 9) || (timeOfDay >= 17 && timeOfDay <= 19)) {
            peakAdjustment = 2.0; // 2 extra minutes during peak hours
        }
        
        return baseWaitTime + locationAdjustment + peakAdjustment;
    }
    
    /**
     * Calculate total trip score for an insertion considering all trip components
     */
    public double calculateTripScore(DrtRequest request, StopCandidate pickupStop, 
                                   StopCandidate dropoffStop, double ivtTime) {
        Id<Person> personId = request.getPassengerIds().iterator().next();
        PreferenceDataLoader.UserPreferences prefs = preferenceLoader.getUserPreferences(personId.toString());
        
        double accessTime = pickupStop.accessTime;
        double waitTime = pickupStop.estimatedWaitTime;
        double ivtTimeMinutes = ivtTime / 60.0; // Convert to minutes
        double egressTime = dropoffStop.egressTime;
        
        return prefs.calculatePreferenceScore(accessTime, waitTime, ivtTimeMinutes, egressTime);
    }
    
    /**
     * Data class for stop candidates with preference scoring
     */
    public static class StopCandidate {
        public final TransitStopFacility stop;
        public final double walkDistance; // meters
        public final double walkTime; // seconds
        public final double requestTime;
        
        // Preference-related fields
        public double preferenceScore = 0.0;
        public double accessTime = 0.0; // minutes
        public double estimatedWaitTime = 0.0; // minutes
        public double egressTime = 0.0; // minutes
        
        public StopCandidate(TransitStopFacility stop, double walkDistance, 
                           double walkTime, double requestTime) {
            this.stop = stop;
            this.walkDistance = walkDistance;
            this.walkTime = walkTime;
            this.requestTime = requestTime;
        }
        
        @Override
        public String toString() {
            return String.format("Stop %s (dist=%.1fm, score=%.3f)", 
                               stop.getId(), walkDistance, preferenceScore);
        }
    }
    
    /**
     * Get default stop finder for fallback when preferences are not available
     */
    public List<StopCandidate> findDefaultStops(Link targetLink, double currentTime, boolean isPickup) {
        return findCandidateStops(targetLink, currentTime, isPickup).stream()
                .sorted(Comparator.comparingDouble(c -> c.walkDistance))
                .limit(maxStopCandidates)
                .collect(Collectors.toList());
    }
} 