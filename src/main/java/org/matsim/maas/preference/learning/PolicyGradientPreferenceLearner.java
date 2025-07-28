package org.matsim.maas.preference.learning;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.preference.data.DynamicUserPreferenceStore;
import org.matsim.maas.preference.data.DynamicUserPreferenceStore.PreferenceUpdate;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Policy gradient-based preference learner implementing the REINFORCE algorithm.
 * 
 * This learner updates user preference weights based on the policy gradient method,
 * where the gradient is estimated from the rewards received for different actions.
 * 
 * The key idea is that positive rewards increase the probability of similar
 * preferences in the future, while negative rewards decrease them.
 * 
 * Algorithm overview:
 * 1. For each experience, calculate the gradient of the log-probability
 * 2. Scale the gradient by the reward (advantage)
 * 3. Apply momentum and learning rate
 * 4. Clip gradients to prevent instability
 * 5. Update weights with safety constraints
 */
public class PolicyGradientPreferenceLearner implements PreferenceLearner {
    
    private final DynamicUserPreferenceStore preferenceStore;
    private final LearningConfiguration config;
    private final Map<Id<Person>, MomentumState> momentumStates;
    private final Map<Id<Person>, List<LearningExperience>> experienceBuffer;
    private final Random random;
    
    private int currentIteration = 0;
    private int totalUpdates = 0;
    
    public PolicyGradientPreferenceLearner(DynamicUserPreferenceStore preferenceStore,
                                         LearningConfiguration config) {
        this.preferenceStore = preferenceStore;
        this.config = config;
        this.momentumStates = new ConcurrentHashMap<>();
        this.experienceBuffer = new ConcurrentHashMap<>();
        this.random = new Random(42); // Fixed seed for reproducibility
    }
    
    @Override
    public PreferenceUpdate learnFromAcceptance(Id<Person> personId,
                                              double accessTime, double waitTime,
                                              double ivtTime, double egressTime,
                                              double reward) {
        
        // Scale reward
        reward *= config.getAcceptanceRewardScale();
        
        // Calculate gradient based on positive reward
        double[] gradient = calculatePolicyGradient(personId, 
            accessTime, waitTime, ivtTime, egressTime, reward);
        
        // Apply learning update
        return applyGradientUpdate(personId, gradient);
    }
    
    @Override
    public PreferenceUpdate learnFromRejection(Id<Person> personId,
                                             double accessTime, double waitTime,
                                             double ivtTime, double egressTime,
                                             double penalty) {
        
        // Scale penalty (ensure it's negative)
        penalty = -Math.abs(penalty) * config.getRejectionPenaltyScale();
        
        // Calculate gradient based on negative reward
        double[] gradient = calculatePolicyGradient(personId,
            accessTime, waitTime, ivtTime, egressTime, penalty);
        
        // Apply learning update
        return applyGradientUpdate(personId, gradient);
    }
    
    @Override
    public PreferenceUpdate learnFromCompletion(Id<Person> personId,
                                              double actualAccessTime, double actualWaitTime,
                                              double actualIvtTime, double actualEgressTime,
                                              double satisfaction) {
        
        // Scale satisfaction reward
        satisfaction *= config.getCompletionRewardScale();
        
        // Calculate gradient based on actual experience
        double[] gradient = calculatePolicyGradient(personId,
            actualAccessTime, actualWaitTime, actualIvtTime, actualEgressTime, satisfaction);
        
        // Apply learning update
        return applyGradientUpdate(personId, gradient);
    }
    
    @Override
    public Map<Id<Person>, PreferenceUpdate> batchLearn(Map<Id<Person>, LearningExperience> learningExperiences) {
        Map<Id<Person>, PreferenceUpdate> updates = new HashMap<>();
        
        // Group experiences by person
        Map<Id<Person>, List<LearningExperience>> experiencesByPerson = new HashMap<>();
        for (Map.Entry<Id<Person>, LearningExperience> entry : learningExperiences.entrySet()) {
            experiencesByPerson.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                .add(entry.getValue());
        }
        
        // Process each person's experiences
        for (Map.Entry<Id<Person>, List<LearningExperience>> entry : experiencesByPerson.entrySet()) {
            Id<Person> personId = entry.getKey();
            List<LearningExperience> experiences = entry.getValue();
            
            // Add to buffer
            experienceBuffer.computeIfAbsent(personId, k -> new ArrayList<>()).addAll(experiences);
            
            // Check if we should update
            if (experienceBuffer.get(personId).size() >= config.getBatchSize()) {
                PreferenceUpdate update = processBatchForPerson(personId);
                if (update != null) {
                    updates.put(personId, update);
                }
            }
        }
        
        return updates;
    }
    
    @Override
    public LearningConfiguration getConfiguration() {
        return config;
    }
    
    @Override
    public void updateLearningParameters(int iteration) {
        this.currentIteration = iteration;
    }
    
    @Override
    public void reset() {
        momentumStates.clear();
        experienceBuffer.clear();
        currentIteration = 0;
        totalUpdates = 0;
    }
    
    /**
     * Calculate policy gradient using REINFORCE algorithm
     */
    private double[] calculatePolicyGradient(Id<Person> personId,
                                           double accessTime, double waitTime,
                                           double ivtTime, double egressTime,
                                           double reward) {
        
        UserPreferenceData currentPref = preferenceStore.getUserPreference(personId);
        if (currentPref == null) {
            return new double[]{0, 0, 0, 0}; // No gradient if no preferences
        }
        
        // Calculate current utility
        double utility = currentPref.calculateUtility(accessTime, waitTime, ivtTime, egressTime);
        
        // Calculate policy probability (softmax-like)
        double probAccept = 1.0 / (1.0 + Math.exp(-utility / 100.0)); // Temperature = 100
        
        // Calculate log-probability gradient
        // For accepted rides: grad = features * (1 - p) * reward
        // For rejected rides: grad = -features * p * reward
        double gradientScale = reward > 0 ? (1 - probAccept) : -probAccept;
        gradientScale *= reward;
        
        // Normalize features to prevent gradient explosion
        double totalTime = accessTime + waitTime + ivtTime + egressTime;
        if (totalTime > 0) {
            accessTime /= totalTime;
            waitTime /= totalTime;
            ivtTime /= totalTime;
            egressTime /= totalTime;
        }
        
        // Calculate gradients for each weight
        double[] gradient = new double[4];
        gradient[0] = gradientScale * accessTime;
        gradient[1] = gradientScale * waitTime;
        gradient[2] = gradientScale * ivtTime;
        gradient[3] = gradientScale * egressTime;
        
        // Apply L2 regularization
        if (config.getL2RegularizationWeight() > 0) {
            gradient[0] -= config.getL2RegularizationWeight() * currentPref.getAccessWeight();
            gradient[1] -= config.getL2RegularizationWeight() * currentPref.getWaitWeight();
            gradient[2] -= config.getL2RegularizationWeight() * currentPref.getIvtWeight();
            gradient[3] -= config.getL2RegularizationWeight() * currentPref.getEgressWeight();
        }
        
        return gradient;
    }
    
    /**
     * Apply gradient update with momentum and safety constraints
     */
    private PreferenceUpdate applyGradientUpdate(Id<Person> personId, double[] gradient) {
        // Get or create momentum state
        MomentumState momentum = momentumStates.computeIfAbsent(personId, k -> new MomentumState());
        
        // Get current learning rate
        double learningRate = config.getCurrentLearningRate(currentIteration);
        
        // Apply momentum
        momentum.updateWithGradient(gradient, config.getMomentum());
        
        // Get momentum-adjusted gradient
        double[] momentumGradient = momentum.getMomentum();
        
        // Clip gradients
        double gradientNorm = Math.sqrt(
            momentumGradient[0] * momentumGradient[0] +
            momentumGradient[1] * momentumGradient[1] +
            momentumGradient[2] * momentumGradient[2] +
            momentumGradient[3] * momentumGradient[3]
        );
        
        if (gradientNorm > config.getGradientClipThreshold()) {
            double scale = config.getGradientClipThreshold() / gradientNorm;
            for (int i = 0; i < 4; i++) {
                momentumGradient[i] *= scale;
            }
        }
        
        // Calculate weight updates
        double accessDelta = learningRate * momentumGradient[0];
        double waitDelta = learningRate * momentumGradient[1];
        double ivtDelta = learningRate * momentumGradient[2];
        double egressDelta = learningRate * momentumGradient[3];
        
        // Apply exploration noise if configured
        if (config.getCurrentExplorationRate(currentIteration) > 0) {
            double noise = config.getCurrentExplorationRate(currentIteration);
            accessDelta += (random.nextGaussian() * noise * 0.01);
            waitDelta += (random.nextGaussian() * noise * 0.01);
            ivtDelta += (random.nextGaussian() * noise * 0.01);
            egressDelta += (random.nextGaussian() * noise * 0.01);
        }
        
        // Constrain total change
        double totalChange = Math.abs(accessDelta) + Math.abs(waitDelta) + 
                           Math.abs(ivtDelta) + Math.abs(egressDelta);
        
        if (totalChange > config.getMaxTotalChange()) {
            double scale = config.getMaxTotalChange() / totalChange;
            accessDelta *= scale;
            waitDelta *= scale;
            ivtDelta *= scale;
            egressDelta *= scale;
        }
        
        totalUpdates++;
        
        return new PreferenceUpdate(accessDelta, waitDelta, ivtDelta, egressDelta);
    }
    
    /**
     * Process batch of experiences for a person
     */
    private PreferenceUpdate processBatchForPerson(Id<Person> personId) {
        List<LearningExperience> experiences = experienceBuffer.get(personId);
        if (experiences == null || experiences.isEmpty()) {
            return null;
        }
        
        // Take batch
        int batchSize = Math.min(config.getBatchSize(), experiences.size());
        List<LearningExperience> batch = new ArrayList<>(experiences.subList(0, batchSize));
        experiences.subList(0, batchSize).clear();
        
        // Calculate average gradient over batch
        double[] avgGradient = new double[4];
        double totalReward = 0;
        
        for (LearningExperience exp : batch) {
            double[] gradient = calculatePolicyGradient(personId,
                exp.accessTime, exp.waitTime, exp.ivtTime, exp.egressTime, exp.reward);
            
            for (int i = 0; i < 4; i++) {
                avgGradient[i] += gradient[i];
            }
            totalReward += exp.reward;
        }
        
        // Average the gradients
        for (int i = 0; i < 4; i++) {
            avgGradient[i] /= batchSize;
        }
        
        // Apply batch normalization if configured
        if (config.isUseBatchNormalization() && batchSize > 1) {
            normalizeBatchGradient(avgGradient);
        }
        
        // Apply update
        return applyGradientUpdate(personId, avgGradient);
    }
    
    /**
     * Normalize gradient across batch
     */
    private void normalizeBatchGradient(double[] gradient) {
        double mean = (gradient[0] + gradient[1] + gradient[2] + gradient[3]) / 4.0;
        double variance = 0;
        
        for (int i = 0; i < 4; i++) {
            variance += (gradient[i] - mean) * (gradient[i] - mean);
        }
        variance /= 4.0;
        
        double std = Math.sqrt(variance + 1e-8); // Add small epsilon for stability
        
        for (int i = 0; i < 4; i++) {
            gradient[i] = (gradient[i] - mean) / std;
        }
    }
    
    /**
     * Get learning statistics
     */
    public LearningStatistics getStatistics() {
        return new LearningStatistics(
            totalUpdates,
            currentIteration,
            config.getCurrentLearningRate(currentIteration),
            config.getCurrentExplorationRate(currentIteration),
            momentumStates.size(),
            experienceBuffer.values().stream().mapToInt(List::size).sum()
        );
    }
    
    /**
     * Container for momentum state
     */
    private static class MomentumState {
        private double[] momentum = new double[4];
        
        public void updateWithGradient(double[] gradient, double momentumFactor) {
            for (int i = 0; i < 4; i++) {
                momentum[i] = momentumFactor * momentum[i] + (1 - momentumFactor) * gradient[i];
            }
        }
        
        public double[] getMomentum() {
            return momentum.clone();
        }
    }
    
    /**
     * Container for learning statistics
     */
    public static class LearningStatistics {
        public final int totalUpdates;
        public final int currentIteration;
        public final double currentLearningRate;
        public final double currentExplorationRate;
        public final int activeUsers;
        public final int bufferedExperiences;
        
        public LearningStatistics(int totalUpdates, int currentIteration,
                                double currentLearningRate, double currentExplorationRate,
                                int activeUsers, int bufferedExperiences) {
            this.totalUpdates = totalUpdates;
            this.currentIteration = currentIteration;
            this.currentLearningRate = currentLearningRate;
            this.currentExplorationRate = currentExplorationRate;
            this.activeUsers = activeUsers;
            this.bufferedExperiences = bufferedExperiences;
        }
    }
}