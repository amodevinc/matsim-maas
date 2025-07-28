package org.matsim.maas.utils;

/**
 * Represents an uncertainty rule for demand prediction error modeling.
 * These rules apply different random perturbation patterns to the base demand.
 */
public class UncertaintyRule {
    
    private final double multiplier;
    private final int ruleNumber;
    private final long randomSeed;
    
    /**
     * Create an uncertainty rule.
     * 
     * @param multiplier Demand scaling factor (0.5, 1.0, 1.5)
     * @param ruleNumber Rule identifier (1, 2, 3)
     */
    public UncertaintyRule(double multiplier, int ruleNumber) {
        this.multiplier = multiplier;
        this.ruleNumber = ruleNumber;
        // Generate a deterministic seed based on multiplier and rule for reproducibility
        this.randomSeed = generateSeed(multiplier, ruleNumber);
    }
    
    /**
     * Generate a deterministic seed for reproducible random patterns.
     */
    private long generateSeed(double multiplier, int ruleNumber) {
        // Create a unique seed based on multiplier and rule number
        // This ensures reproducible results for the same rule configuration
        return (long) (multiplier * 1000) * 10 + ruleNumber;
    }
    
    public double getMultiplier() {
        return multiplier;
    }
    
    public int getRuleNumber() {
        return ruleNumber;
    }
    
    public long getRandomSeed() {
        return randomSeed;
    }
    
    /**
     * Get the scenario suffix for this uncertainty rule.
     * Format: "trip{multiplier}_rule{ruleNumber}"
     */
    public String getScenarioSuffix() {
        return String.format("trip%.1f_rule%d", multiplier, ruleNumber);
    }
    
    /**
     * Check if this is the baseline rule (multiplier = 1.0, rule = 1).
     */
    public boolean isBaseline() {
        return Math.abs(multiplier - 1.0) < 0.001 && ruleNumber == 1;
    }
    
    @Override
    public String toString() {
        return String.format("UncertaintyRule{multiplier=%.1f, rule=%d, seed=%d}", 
                           multiplier, ruleNumber, randomSeed);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        UncertaintyRule that = (UncertaintyRule) obj;
        return Double.compare(that.multiplier, multiplier) == 0 && ruleNumber == that.ruleNumber;
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(multiplier, ruleNumber);
    }
}