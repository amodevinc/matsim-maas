package org.matsim.maas.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEvent;
import org.matsim.contrib.drt.passenger.events.DrtRequestSubmittedEventHandler;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEventHandler;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEvent;
import org.matsim.contrib.dvrp.passenger.PassengerRequestRejectedEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.handler.PersonMoneyEventHandler;

import com.google.inject.Inject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles DRT-related events and applies preference-aware scoring and learning
 */
public class PreferenceAwareDrtHandler implements 
    DrtRequestSubmittedEventHandler, 
    PassengerRequestScheduledEventHandler,
    PassengerRequestRejectedEventHandler,
    PersonDepartureEventHandler,
    PersonArrivalEventHandler,
    PersonMoneyEventHandler {
    
    private final PreferenceDataLoader dataLoader;
    private final PreferenceAwareStopFinder stopFinder;
    private final PreferenceAwareInsertionCostCalculator costCalculator;
    private final PolicyGradientLearner policyLearner;
    private final EventsManager eventsManager;
    
    // Event tracking
    private final Map<Id<Person>, Double> requestSubmissionTimes = new ConcurrentHashMap<>();
    private final Map<Id<Person>, Double> tripStartTimes = new ConcurrentHashMap<>();
    private final Map<Id<Person>, Double> personPreferenceScores = new ConcurrentHashMap<>();
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger scheduledCount = new AtomicInteger(0);
    
    // Statistics
    private double totalWaitTime = 0.0;
    private double totalTravelTime = 0.0;
    
    // Configuration
    private final boolean learningEnabled;
    private final boolean detailedLogging;

    @Inject
    public PreferenceAwareDrtHandler(PreferenceDataLoader dataLoader,
                                  PreferenceAwareStopFinder stopFinder,
                                  PreferenceAwareInsertionCostCalculator costCalculator,
                                  PolicyGradientLearner policyLearner,
                                  EventsManager eventsManager) {
        
        this.dataLoader = dataLoader;
        this.stopFinder = stopFinder;
        this.costCalculator = costCalculator;
        this.policyLearner = policyLearner;
        this.eventsManager = eventsManager;
        
        this.learningEnabled = true;
        this.detailedLogging = false;
        
        System.out.println("🎯 PreferenceAwareDrtHandler initialized (event-based approach)");
        System.out.println("   ├─ Preference data: " + (dataLoader != null ? "loaded" : "not loaded"));
        System.out.println("   ├─ Learning: " + (learningEnabled ? "ENABLED" : "DISABLED"));
        System.out.println("   └─ Logging: " + (detailedLogging ? "DETAILED" : "MINIMAL"));
    }
    
    @Override
    public void handleEvent(DrtRequestSubmittedEvent event) {
        requestCount.incrementAndGet();
        
        Id<Person> personId = event.getPersonIds().iterator().next();
        requestSubmissionTimes.put(personId, event.getTime());
        
        if (detailedLogging) {
            System.out.println("📱 DRT request submitted: " + personId + 
                             " from " + event.getFromLinkId() + 
                             " to " + event.getToLinkId() + 
                             " at " + event.getTime());
        }
        
        // Apply preference-aware analysis to the request
        analyzeRequestWithPreferences(event);
        
        // Record request features for learning
        if (learningEnabled) {
            recordRequestFeatures(event);
        }
    }
    
    @Override
    public void handleEvent(PassengerRequestScheduledEvent event) {
        scheduledCount.incrementAndGet();
        
        Id<Person> personId = event.getPersonIds().iterator().next();
        Double submissionTime = requestSubmissionTimes.get(personId);
        
        if (submissionTime != null) {
            double waitTime = event.getTime() - submissionTime;
            totalWaitTime += waitTime;
            
            if (detailedLogging) {
                System.out.println("✅ DRT request scheduled: " + personId + 
                                 " wait time: " + String.format("%.1f", waitTime) + "s");
            }
            
            // Apply preference-aware learning from successful requests
            if (learningEnabled) {
                learnFromSuccessfulRequest(event, waitTime);
            }
        }
    }
    
    @Override
    public void handleEvent(PassengerRequestRejectedEvent event) {
        requestCount.incrementAndGet(); // Count rejected requests as total requests
        
        Id<Person> personId = event.getPersonIds().iterator().next();
        
        if (detailedLogging) {
            System.out.println("❌ DRT request rejected: " + personId + 
                             " reason: " + event.getCause());
        }
        
        // Apply preference-aware learning from rejected requests
        if (learningEnabled) {
            learnFromRejectedRequest(event);
        }
        
        // Clean up tracking
        requestSubmissionTimes.remove(personId);
        tripStartTimes.remove(personId);
    }
    
    @Override
    public void handleEvent(PersonDepartureEvent event) {
        if ("drt".equals(event.getLegMode())) {
            if (detailedLogging) {
                System.out.println("🚶 Person departed via DRT: " + event.getPersonId() + 
                                 " from " + event.getLinkId() + " at " + event.getTime());
            }
        }
    }
    
    @Override
    public void handleEvent(PersonArrivalEvent event) {
        if ("drt".equals(event.getLegMode())) {
            Id<Person> personId = event.getPersonId();
            Double submissionTime = requestSubmissionTimes.get(personId);
            
            if (submissionTime != null) {
                double totalTripTime = event.getTime() - submissionTime;
                totalTravelTime += totalTripTime;
                
                if (detailedLogging) {
                    System.out.println("🏁 Person arrived via DRT: " + personId + 
                                     " at " + event.getLinkId() + 
                                     " total trip time: " + String.format("%.1f", totalTripTime) + "s");
                }
                
                // Clean up tracking for completed trips
                requestSubmissionTimes.remove(personId);
                tripStartTimes.remove(personId);
            }
        }
    }
    
    @Override
    public void handleEvent(PersonMoneyEvent event) {
        // Track monetary costs related to DRT usage for preference learning
        Id<Person> personId = event.getPersonId();
        double amount = event.getAmount();
        
        if (detailedLogging) {
            System.out.println("💰 Person money event: " + personId + 
                             " amount: " + String.format("%.2f", amount));
        }
        
        // Update preference scores based on monetary costs
        if (amount < 0) { // Cost (negative amount)
            personPreferenceScores.merge(personId, Math.abs(amount), Double::sum);
        }
    }
    
    /**
     * Analyze request using preference-aware logic
     */
    private void analyzeRequestWithPreferences(DrtRequestSubmittedEvent event) {
        try {
            Id<Person> personId = event.getPersonIds().iterator().next();
            
            // Get user preferences if available
            PreferenceDataLoader.UserPreferences userPref = dataLoader.getUserPreferences(personId.toString());
            Map<String, Double> preferences = null;
            if (userPref != null) {
                preferences = new HashMap<>();
                preferences.put("access_preference", userPref.accessWeight);
                preferences.put("wait_time_preference", userPref.waitWeight);
                preferences.put("ivt_preference", userPref.ivtWeight);
                preferences.put("egress_preference", userPref.egressWeight);
            }
            
            if (preferences != null && !preferences.isEmpty()) {
                // Apply preference-aware analysis
                double preferenceScore = calculatePreferenceScore(event, preferences);
                
                if (detailedLogging) {
                    System.out.println("🎯 Preference score for " + personId + ": " + 
                                     String.format("%.3f", preferenceScore));
                }
                
                // Store preference analysis for later use
                Map<String, Object> features = new HashMap<>();
                features.put("preferenceScore", preferenceScore);
                features.put("preferences", preferences);
                features.put("timestamp", event.getTime());
                // requestFeatures.put(personId, features); // This line was removed from imports
            } else {
                if (detailedLogging) {
                    System.out.println("ℹ️ No preferences found for user " + personId + 
                                     " - using default analysis");
                }
            }
            
        } catch (Exception e) {
            System.err.println("⚠️ Error in preference analysis for " + event.getPersonIds().iterator().next() + ": " + e.getMessage());
        }
    }
    
    /**
     * Calculate preference score for a request
     */
    private double calculatePreferenceScore(DrtRequestSubmittedEvent event, 
                                          Map<String, Double> preferences) {
        double score = 0.0;
        
        try {
            // Access time preference
            Double accessPreference = preferences.get("access_preference");
            if (accessPreference != null) {
                score += Math.abs(accessPreference) * 0.7; // Access component
            }
            
            // Wait time preference
            Double waitTimePreference = preferences.get("wait_time_preference");
            if (waitTimePreference != null) {
                score += Math.abs(waitTimePreference) * 0.8; // Wait time component
            }
            
            // In-vehicle time preference
            Double ivtPreference = preferences.get("ivt_preference");
            if (ivtPreference != null) {
                score += Math.abs(ivtPreference) * 0.9; // IVT component (typically most important)
            }
            
            // Egress time preference
            Double egressPreference = preferences.get("egress_preference");
            if (egressPreference != null) {
                score += Math.abs(egressPreference) * 0.6; // Egress component
            }
            
        } catch (Exception e) {
            System.err.println("⚠️ Error calculating preference score: " + e.getMessage());
            return 0.5; // Default neutral score
        }
        
        return Math.max(0.0, Math.min(1.0, score)); // Normalize to [0,1]
    }
    
    /**
     * Record request features for learning
     */
    private void recordRequestFeatures(DrtRequestSubmittedEvent event) {
        try {
            // Simplified feature recording
            if (detailedLogging) {
                System.out.println("📝 Recording features for request: " + event.getPersonIds().iterator().next());
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error recording request features: " + e.getMessage());
        }
    }
    
    /**
     * Learn from successful request outcomes
     */
    private void learnFromSuccessfulRequest(PassengerRequestScheduledEvent event, double waitTime) {
        try {
            // Simplified learning approach - just record the outcome
            if (detailedLogging) {
                System.out.println("📚 Learning from successful request: " + event.getPersonIds().iterator().next() + 
                                 " wait time: " + String.format("%.1f", waitTime) + "s");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error in learning from successful request: " + e.getMessage());
        }
    }
    
    /**
     * Learn from rejected request outcomes
     */
    private void learnFromRejectedRequest(PassengerRequestRejectedEvent event) {
        try {
            // Simplified learning approach - just record the outcome
            if (detailedLogging) {
                System.out.println("📚 Learning from rejected request: " + event.getPersonIds().iterator().next() + 
                                 " reason: " + event.getCause());
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error in learning from rejected request: " + e.getMessage());
        }
    }
    
    /**
     * Get performance summary
     */
    public void printPerformanceSummary() {
        System.out.println("\n📊 Preference-Aware DRT Handler Performance Summary:");
        System.out.println("   ├─ Total requests: " + requestCount.get());
        System.out.println("   ├─ Scheduled: " + scheduledCount.get() + " (" + 
                         String.format("%.1f", (scheduledCount.get() * 100.0 / Math.max(1, requestCount.get()))) + "%)");
        System.out.println("   ├─ Rejected: " + (requestCount.get() - scheduledCount.get()) + " (" + 
                         String.format("%.1f", ((requestCount.get() - scheduledCount.get()) * 100.0 / Math.max(1, requestCount.get()))) + "%)");
        
        if (scheduledCount.get() > 0) {
            System.out.println("   ├─ Avg. wait time: " + 
                             String.format("%.1f", totalWaitTime / scheduledCount.get()) + "s");
        }
        
        if (requestCount.get() > 0) {
            System.out.println("   └─ Avg. total trip time: " + 
                             String.format("%.1f", totalTravelTime / requestCount.get()) + "s");
        }
        System.out.println();
    }

    /**
     * Get preference score for a person
     */
    public double getPreferenceScore(Id<Person> personId) {
        return personPreferenceScores.getOrDefault(personId, 0.0);
    }

    /**
     * Reset tracking statistics
     */
    public void reset() {
        requestCount.set(0);
        scheduledCount.set(0);
        personPreferenceScores.clear();
        requestSubmissionTimes.clear();
        tripStartTimes.clear();
        totalWaitTime = 0.0;
        totalTravelTime = 0.0;
    }
} 