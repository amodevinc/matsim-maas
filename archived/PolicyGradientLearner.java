package org.matsim.maas.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.drt.passenger.DrtRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Policy gradient learning module for adaptive preference learning in DRT dispatching.
 * Learns from user acceptance/rejection feedback to improve preference weight estimates.
 * 
 * @author MATSim-MaaS Research Team
 */
public class PolicyGradientLearner {
    
    /**
     * Data class for tracking DRT requests with their features and outcomes
     */
    public static class RequestExperience {
        public final String requestId;
        public final String personId;
        public final double accessTime;
        public final double waitTime;
        public final double ivtTime;
        public final double egressTime;
        public final double submissionTime;
        public final boolean accepted;
        public final double insertionCost;
        public final double preferenceScore;
        
        public RequestExperience(String requestId, String personId, double accessTime, double waitTime,
                               double ivtTime, double egressTime, double submissionTime, boolean accepted,
                               double insertionCost, double preferenceScore) {
            this.requestId = requestId;
            this.personId = personId;
            this.accessTime = accessTime;
            this.waitTime = waitTime;
            this.ivtTime = ivtTime;
            this.egressTime = egressTime;
            this.submissionTime = submissionTime;
            this.accepted = accepted;
            this.insertionCost = insertionCost;
            this.preferenceScore = preferenceScore;
        }
    }
    
    /**
     * Learned preference weights for a user
     */
    public static class LearnedPreferences {
        public double accessWeight;
        public double waitWeight;
        public double ivtWeight;
        public double egressWeight;
        public int updateCount;
        public double averageAcceptanceRate;
        
        public LearnedPreferences(double accessWeight, double waitWeight, double ivtWeight, double egressWeight) {
            this.accessWeight = accessWeight;
            this.waitWeight = waitWeight;
            this.ivtWeight = ivtWeight;
            this.egressWeight = egressWeight;
            this.updateCount = 0;
            this.averageAcceptanceRate = 0.5; // Start with neutral assumption
        }
        
        public PreferenceDataLoader.UserPreferences toUserPreferences(int userId) {
            return new PreferenceDataLoader.UserPreferences(userId, accessWeight, waitWeight, ivtWeight, egressWeight);
        }
    }
    
    private final PreferenceDataLoader originalPreferences;
    private final Map<String, LearnedPreferences> learnedPreferences;
    private final List<RequestExperience> experienceBuffer;
    
    // Learning parameters
    private final double learningRate;
    private final double explorationRate;
    private final double decayRate;
    private final int batchSize;
    private final int maxExperienceBuffer;
    
    // Performance tracking
    private int totalUpdates = 0;
    private double totalAcceptanceRate = 0.0;
    private int totalRequests = 0;
    private double averageLearningProgress = 0.0;
    
    @com.google.inject.Inject
    public PolicyGradientLearner(PreferenceDataLoader originalPreferences) {
        this.originalPreferences = originalPreferences;
        this.learnedPreferences = new ConcurrentHashMap<>();
        this.experienceBuffer = new ArrayList<>();
        
        // Learning hyperparameters
        this.learningRate = 0.01;        // Learning rate for gradient updates
        this.explorationRate = 0.1;      // Exploration vs exploitation balance
        this.decayRate = 0.995;          // Decay rate for learning rate
        this.batchSize = 20;             // Batch size for gradient updates
        this.maxExperienceBuffer = 1000; // Maximum experience buffer size
        
        System.out.println("PolicyGradientLearner initialized with learning rate=" + learningRate + 
                         ", exploration=" + explorationRate + ", batch size=" + batchSize);
    }
    
    /**
     * Record a DRT request experience for learning
     */
    public void recordExperience(DrtRequest request, double accessTime, double waitTime, 
                               double ivtTime, double egressTime, boolean accepted, 
                               double insertionCost, double preferenceScore) {
        
        String personId = request.getPassengerIds().iterator().next().toString();
        RequestExperience experience = new RequestExperience(
            request.getId().toString(), personId, accessTime, waitTime, ivtTime, egressTime,
            request.getSubmissionTime(), accepted, insertionCost, preferenceScore
        );
        
        synchronized (experienceBuffer) {
            experienceBuffer.add(experience);
            
            // Maintain buffer size
            if (experienceBuffer.size() > maxExperienceBuffer) {
                experienceBuffer.remove(0); // Remove oldest experience
            }
        }
        
        // Update acceptance rate tracking
        totalRequests++;
        if (accepted) {
            totalAcceptanceRate = ((totalAcceptanceRate * (totalRequests - 1)) + 1.0) / totalRequests;
        } else {
            totalAcceptanceRate = (totalAcceptanceRate * (totalRequests - 1)) / totalRequests;
        }
        
        // Trigger learning update if batch is ready
        if (experienceBuffer.size() >= batchSize) {
            performBatchUpdate();
        }
        
        // Log progress periodically
        if (totalRequests % 100 == 0) {
            System.out.println(String.format("Policy learning progress: %d requests, %.3f acceptance rate, %d users learned",
                             totalRequests, totalAcceptanceRate, learnedPreferences.size()));
        }
    }
    
    /**
     * Get adaptive preferences for a user (original + learned adjustments)
     */
    public PreferenceDataLoader.UserPreferences getAdaptivePreferences(String personId) {
        // Get original preferences as baseline
        PreferenceDataLoader.UserPreferences originalPrefs = originalPreferences.getUserPreferences(personId);
        
        // Check if we have learned adjustments for this user
        LearnedPreferences learned = learnedPreferences.get(personId);
        
        if (learned != null && learned.updateCount > 5) { // Only use if we have sufficient learning
            // Blend original and learned preferences
            double blendFactor = Math.min(0.5, learned.updateCount / 100.0); // Gradually increase learned weight
            
            double accessWeight = (1 - blendFactor) * originalPrefs.accessWeight + blendFactor * learned.accessWeight;
            double waitWeight = (1 - blendFactor) * originalPrefs.waitWeight + blendFactor * learned.waitWeight;
            double ivtWeight = (1 - blendFactor) * originalPrefs.ivtWeight + blendFactor * learned.ivtWeight;
            double egressWeight = (1 - blendFactor) * originalPrefs.egressWeight + blendFactor * learned.egressWeight;
            
            return new PreferenceDataLoader.UserPreferences(originalPrefs.userId, accessWeight, waitWeight, ivtWeight, egressWeight);
        }
        
        // Return original preferences if no sufficient learning yet
        return originalPrefs;
    }
    
    /**
     * Perform batch gradient update using collected experiences
     */
    private void performBatchUpdate() {
        synchronized (experienceBuffer) {
            if (experienceBuffer.size() < batchSize) {
                return;
            }
            
            // Group experiences by person for individual learning
            Map<String, List<RequestExperience>> personExperiences = new HashMap<>();
            for (RequestExperience exp : experienceBuffer) {
                personExperiences.computeIfAbsent(exp.personId, k -> new ArrayList<>()).add(exp);
            }
            
            // Update each person's preferences
            for (Map.Entry<String, List<RequestExperience>> entry : personExperiences.entrySet()) {
                String personId = entry.getKey();
                List<RequestExperience> experiences = entry.getValue();
                
                if (experiences.size() >= 3) { // Need minimum experiences for learning
                    updatePersonPreferences(personId, experiences);
                }
            }
            
            // Clear processed experiences
            experienceBuffer.clear();
            totalUpdates++;
        }
    }
    
    /**
     * Update preferences for a specific person using policy gradient
     */
    private void updatePersonPreferences(String personId, List<RequestExperience> experiences) {
        // Get or initialize learned preferences
        LearnedPreferences learned = learnedPreferences.computeIfAbsent(personId, k -> {
            PreferenceDataLoader.UserPreferences original = originalPreferences.getUserPreferences(personId);
            return new LearnedPreferences(original.accessWeight, original.waitWeight, 
                                        original.ivtWeight, original.egressWeight);
        });
        
        // Calculate gradients based on acceptance patterns
        double[] gradients = calculatePolicyGradients(experiences, learned);
        
        // Apply gradient updates with learning rate decay
        double currentLearningRate = learningRate * Math.pow(decayRate, learned.updateCount);
        
        learned.accessWeight -= currentLearningRate * gradients[0];
        learned.waitWeight -= currentLearningRate * gradients[1];
        learned.ivtWeight -= currentLearningRate * gradients[2];
        learned.egressWeight -= currentLearningRate * gradients[3];
        
        // Update acceptance rate for this user
        long acceptedCount = experiences.stream().mapToLong(exp -> exp.accepted ? 1 : 0).sum();
        learned.averageAcceptanceRate = (double) acceptedCount / experiences.size();
        
        learned.updateCount++;
        
        // Add exploration noise to prevent convergence to local minima
        if (Math.random() < explorationRate) {
            addExplorationNoise(learned);
        }
    }
    
    /**
     * Calculate policy gradients based on acceptance patterns
     */
    private double[] calculatePolicyGradients(List<RequestExperience> experiences, LearnedPreferences preferences) {
        double[] gradients = new double[4]; // access, wait, ivt, egress
        
        for (RequestExperience exp : experiences) {
            // Calculate log probability gradient
            double[] features = {exp.accessTime, exp.waitTime, exp.ivtTime, exp.egressTime};
            double[] weights = {preferences.accessWeight, preferences.waitWeight, 
                              preferences.ivtWeight, preferences.egressWeight};
            
            // Calculate preference score
            double score = 0;
            for (int i = 0; i < 4; i++) {
                score += weights[i] * features[i];
            }
            
            // Calculate acceptance probability (sigmoid)
            double acceptanceProb = 1.0 / (1.0 + Math.exp(score)); // Lower score = higher acceptance
            
            // Policy gradient: (actual - predicted) * feature
            double advantage = (exp.accepted ? 1.0 : 0.0) - acceptanceProb;
            
            for (int i = 0; i < 4; i++) {
                gradients[i] += advantage * features[i];
            }
        }
        
        // Normalize by number of experiences
        for (int i = 0; i < 4; i++) {
            gradients[i] /= experiences.size();
        }
        
        return gradients;
    }
    
    /**
     * Add exploration noise to preferences
     */
    private void addExplorationNoise(LearnedPreferences preferences) {
        double noiseScale = 0.01; // Small noise to encourage exploration
        
        preferences.accessWeight += (Math.random() - 0.5) * noiseScale;
        preferences.waitWeight += (Math.random() - 0.5) * noiseScale;
        preferences.ivtWeight += (Math.random() - 0.5) * noiseScale;
        preferences.egressWeight += (Math.random() - 0.5) * noiseScale;
    }
    
    /**
     * Get learning statistics
     */
    public LearningStats getLearningStats() {
        double avgUpdatesPerUser = learnedPreferences.isEmpty() ? 0.0 : 
            learnedPreferences.values().stream().mapToInt(p -> p.updateCount).average().orElse(0.0);
        
        double avgAcceptanceByLearned = learnedPreferences.isEmpty() ? 0.0 :
            learnedPreferences.values().stream().mapToDouble(p -> p.averageAcceptanceRate).average().orElse(0.0);
        
        return new LearningStats(totalRequests, totalAcceptanceRate, learnedPreferences.size(),
                               avgUpdatesPerUser, experienceBuffer.size(), totalUpdates, avgAcceptanceByLearned);
    }
    
    /**
     * Reset learning state
     */
    public void resetLearning() {
        learnedPreferences.clear();
        experienceBuffer.clear();
        totalUpdates = 0;
        totalAcceptanceRate = 0.0;
        totalRequests = 0;
        
        System.out.println("Policy gradient learning state reset");
    }
    
    /**
     * Learning statistics data class
     */
    public static class LearningStats {
        public final int totalRequests;
        public final double totalAcceptanceRate;
        public final int learnedUsers;
        public final double avgUpdatesPerUser;
        public final int experienceBufferSize;
        public final int totalBatchUpdates;
        public final double avgAcceptanceByLearned;
        
        public LearningStats(int totalRequests, double totalAcceptanceRate, int learnedUsers,
                           double avgUpdatesPerUser, int experienceBufferSize, int totalBatchUpdates,
                           double avgAcceptanceByLearned) {
            this.totalRequests = totalRequests;
            this.totalAcceptanceRate = totalAcceptanceRate;
            this.learnedUsers = learnedUsers;
            this.avgUpdatesPerUser = avgUpdatesPerUser;
            this.experienceBufferSize = experienceBufferSize;
            this.totalBatchUpdates = totalBatchUpdates;
            this.avgAcceptanceByLearned = avgAcceptanceByLearned;
        }
        
        @Override
        public String toString() {
            return String.format("LearningStats[requests=%d, acceptance=%.3f, learnedUsers=%d, avgUpdates=%.1f, buffer=%d, batches=%d]",
                               totalRequests, totalAcceptanceRate, learnedUsers, avgUpdatesPerUser, 
                               experienceBufferSize, totalBatchUpdates);
        }
    }
} 