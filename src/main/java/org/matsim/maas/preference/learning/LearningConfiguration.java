package org.matsim.maas.preference.learning;

/**
 * Configuration parameters for preference learning algorithms.
 * 
 * This class encapsulates all hyperparameters needed for RL-based
 * preference learning, including learning rates, exploration parameters,
 * and update schedules.
 * 
 * The configuration is immutable - use the Builder pattern to create instances.
 */
public class LearningConfiguration {
    
    // Learning rate parameters
    private final double initialLearningRate;
    private final double learningRateDecay;
    private final double minLearningRate;
    
    // Gradient parameters
    private final double gradientClipThreshold;
    private final double momentum;
    private final boolean useAdaptiveLearning;
    
    // Batch learning parameters
    private final int batchSize;
    private final int updateFrequency;
    private final boolean useBatchNormalization;
    
    // Exploration parameters
    private final double explorationRate;
    private final double explorationDecay;
    private final double minExplorationRate;
    
    // Reward shaping
    private final double acceptanceRewardScale;
    private final double rejectionPenaltyScale;
    private final double completionRewardScale;
    private final double rewardDiscountFactor;
    
    // Regularization
    private final double l2RegularizationWeight;
    private final double weightDecay;
    
    // Safety constraints
    private final double maxWeightChange;
    private final double maxTotalChange;
    
    private LearningConfiguration(Builder builder) {
        this.initialLearningRate = builder.initialLearningRate;
        this.learningRateDecay = builder.learningRateDecay;
        this.minLearningRate = builder.minLearningRate;
        this.gradientClipThreshold = builder.gradientClipThreshold;
        this.momentum = builder.momentum;
        this.useAdaptiveLearning = builder.useAdaptiveLearning;
        this.batchSize = builder.batchSize;
        this.updateFrequency = builder.updateFrequency;
        this.useBatchNormalization = builder.useBatchNormalization;
        this.explorationRate = builder.explorationRate;
        this.explorationDecay = builder.explorationDecay;
        this.minExplorationRate = builder.minExplorationRate;
        this.acceptanceRewardScale = builder.acceptanceRewardScale;
        this.rejectionPenaltyScale = builder.rejectionPenaltyScale;
        this.completionRewardScale = builder.completionRewardScale;
        this.rewardDiscountFactor = builder.rewardDiscountFactor;
        this.l2RegularizationWeight = builder.l2RegularizationWeight;
        this.weightDecay = builder.weightDecay;
        this.maxWeightChange = builder.maxWeightChange;
        this.maxTotalChange = builder.maxTotalChange;
    }
    
    // Getters
    public double getInitialLearningRate() { return initialLearningRate; }
    public double getLearningRateDecay() { return learningRateDecay; }
    public double getMinLearningRate() { return minLearningRate; }
    public double getGradientClipThreshold() { return gradientClipThreshold; }
    public double getMomentum() { return momentum; }
    public boolean isUseAdaptiveLearning() { return useAdaptiveLearning; }
    public int getBatchSize() { return batchSize; }
    public int getUpdateFrequency() { return updateFrequency; }
    public boolean isUseBatchNormalization() { return useBatchNormalization; }
    public double getExplorationRate() { return explorationRate; }
    public double getExplorationDecay() { return explorationDecay; }
    public double getMinExplorationRate() { return minExplorationRate; }
    public double getAcceptanceRewardScale() { return acceptanceRewardScale; }
    public double getRejectionPenaltyScale() { return rejectionPenaltyScale; }
    public double getCompletionRewardScale() { return completionRewardScale; }
    public double getRewardDiscountFactor() { return rewardDiscountFactor; }
    public double getL2RegularizationWeight() { return l2RegularizationWeight; }
    public double getWeightDecay() { return weightDecay; }
    public double getMaxWeightChange() { return maxWeightChange; }
    public double getMaxTotalChange() { return maxTotalChange; }
    
    /**
     * Calculate current learning rate based on iteration
     */
    public double getCurrentLearningRate(int iteration) {
        double decayedRate = initialLearningRate * Math.pow(1 - learningRateDecay, iteration);
        return Math.max(minLearningRate, decayedRate);
    }
    
    /**
     * Calculate current exploration rate based on iteration
     */
    public double getCurrentExplorationRate(int iteration) {
        double decayedRate = explorationRate * Math.pow(1 - explorationDecay, iteration);
        return Math.max(minExplorationRate, decayedRate);
    }
    
    /**
     * Create default configuration for preference learning
     */
    public static LearningConfiguration createDefault() {
        return new Builder().build();
    }
    
    /**
     * Create conservative configuration with slower learning
     */
    public static LearningConfiguration createConservative() {
        return new Builder()
            .initialLearningRate(0.001)
            .momentum(0.9)
            .maxWeightChange(0.05)
            .explorationRate(0.05)
            .build();
    }
    
    /**
     * Create aggressive configuration with faster learning
     */
    public static LearningConfiguration createAggressive() {
        return new Builder()
            .initialLearningRate(0.01)
            .momentum(0.95)
            .maxWeightChange(0.15)
            .explorationRate(0.2)
            .batchSize(32)
            .build();
    }
    
    /**
     * Builder for LearningConfiguration
     */
    public static class Builder {
        // Default values
        private double initialLearningRate = 0.005;
        private double learningRateDecay = 0.001;
        private double minLearningRate = 0.0001;
        private double gradientClipThreshold = 1.0;
        private double momentum = 0.9;
        private boolean useAdaptiveLearning = true;
        private int batchSize = 16;
        private int updateFrequency = 1;
        private boolean useBatchNormalization = false;
        private double explorationRate = 0.1;
        private double explorationDecay = 0.002;
        private double minExplorationRate = 0.01;
        private double acceptanceRewardScale = 1.0;
        private double rejectionPenaltyScale = 1.0;
        private double completionRewardScale = 1.5;
        private double rewardDiscountFactor = 0.95;
        private double l2RegularizationWeight = 0.001;
        private double weightDecay = 0.0001;
        private double maxWeightChange = 0.1;
        private double maxTotalChange = 0.3;
        
        public Builder initialLearningRate(double rate) {
            this.initialLearningRate = rate;
            return this;
        }
        
        public Builder learningRateDecay(double decay) {
            this.learningRateDecay = decay;
            return this;
        }
        
        public Builder minLearningRate(double minRate) {
            this.minLearningRate = minRate;
            return this;
        }
        
        public Builder gradientClipThreshold(double threshold) {
            this.gradientClipThreshold = threshold;
            return this;
        }
        
        public Builder momentum(double momentum) {
            this.momentum = momentum;
            return this;
        }
        
        public Builder useAdaptiveLearning(boolean use) {
            this.useAdaptiveLearning = use;
            return this;
        }
        
        public Builder batchSize(int size) {
            this.batchSize = size;
            return this;
        }
        
        public Builder updateFrequency(int frequency) {
            this.updateFrequency = frequency;
            return this;
        }
        
        public Builder useBatchNormalization(boolean use) {
            this.useBatchNormalization = use;
            return this;
        }
        
        public Builder explorationRate(double rate) {
            this.explorationRate = rate;
            return this;
        }
        
        public Builder explorationDecay(double decay) {
            this.explorationDecay = decay;
            return this;
        }
        
        public Builder minExplorationRate(double minRate) {
            this.minExplorationRate = minRate;
            return this;
        }
        
        public Builder acceptanceRewardScale(double scale) {
            this.acceptanceRewardScale = scale;
            return this;
        }
        
        public Builder rejectionPenaltyScale(double scale) {
            this.rejectionPenaltyScale = scale;
            return this;
        }
        
        public Builder completionRewardScale(double scale) {
            this.completionRewardScale = scale;
            return this;
        }
        
        public Builder rewardDiscountFactor(double factor) {
            this.rewardDiscountFactor = factor;
            return this;
        }
        
        public Builder l2RegularizationWeight(double weight) {
            this.l2RegularizationWeight = weight;
            return this;
        }
        
        public Builder weightDecay(double decay) {
            this.weightDecay = decay;
            return this;
        }
        
        public Builder maxWeightChange(double maxChange) {
            this.maxWeightChange = maxChange;
            return this;
        }
        
        public Builder maxTotalChange(double maxTotal) {
            this.maxTotalChange = maxTotal;
            return this;
        }
        
        public LearningConfiguration build() {
            // Validate configuration
            if (initialLearningRate <= 0) {
                throw new IllegalArgumentException("Initial learning rate must be positive");
            }
            if (batchSize <= 0) {
                throw new IllegalArgumentException("Batch size must be positive");
            }
            if (momentum < 0 || momentum > 1) {
                throw new IllegalArgumentException("Momentum must be between 0 and 1");
            }
            
            return new LearningConfiguration(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "LearningConfiguration{learningRate=%.4f, momentum=%.2f, batchSize=%d, " +
            "explorationRate=%.2f, maxWeightChange=%.2f, adaptive=%s}",
            initialLearningRate, momentum, batchSize, 
            explorationRate, maxWeightChange, useAdaptiveLearning
        );
    }
}