package org.matsim.maas.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Processes demand data with uncertainty rules to model prediction errors.
 * Applies different multipliers and random perturbation patterns to create
 * variants of the base demand for testing algorithm robustness.
 */
public class UncertaintyProcessor {
    
    private static final Logger log = LogManager.getLogger(UncertaintyProcessor.class);
    
    // Standard uncertainty multipliers from the documentation
    private static final double[] STANDARD_MULTIPLIERS = {0.5, 1.0, 1.5};
    
    // Standard rule numbers
    private static final int[] STANDARD_RULES = {1, 2, 3};
    
    /**
     * Apply an uncertainty rule to a list of demand requests.
     * This creates variations in demand timing and spatial distribution.
     * 
     * @param originalRequests Original demand requests
     * @param rule Uncertainty rule to apply
     * @return Modified list of demand requests with uncertainty applied
     */
    public List<DemandRequest> applyUncertaintyRule(List<DemandRequest> originalRequests, 
                                                   UncertaintyRule rule) {
        log.info("Applying uncertainty rule: {}", rule);
        
        Random random = new Random(rule.getRandomSeed());
        List<DemandRequest> modifiedRequests = new ArrayList<>();
        
        // Apply demand multiplier - this affects the total number of requests
        int targetRequestCount = (int) Math.round(originalRequests.size() * rule.getMultiplier());
        
        if (rule.getMultiplier() <= 1.0) {
            // Under-prediction or perfect prediction: sample subset of requests
            modifiedRequests = sampleRequests(originalRequests, targetRequestCount, random);
        } else {
            // Over-prediction: duplicate some requests with perturbations
            modifiedRequests = expandRequests(originalRequests, targetRequestCount, random);
        }
        
        // Apply rule-specific temporal perturbations
        modifiedRequests = applyTemporalPerturbations(modifiedRequests, rule, random);
        
        log.info("Uncertainty rule applied. Original: {} requests, Modified: {} requests", 
                originalRequests.size(), modifiedRequests.size());
        
        return modifiedRequests;
    }
    
    /**
     * Sample a subset of requests for under-prediction scenarios.
     */
    private List<DemandRequest> sampleRequests(List<DemandRequest> requests, 
                                              int targetCount, Random random) {
        if (targetCount >= requests.size()) {
            return new ArrayList<>(requests);
        }
        
        List<DemandRequest> shuffled = new ArrayList<>(requests);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, targetCount);
    }
    
    /**
     * Expand requests for over-prediction scenarios by duplicating with variations.
     */
    private List<DemandRequest> expandRequests(List<DemandRequest> requests, 
                                              int targetCount, Random random) {
        List<DemandRequest> expanded = new ArrayList<>(requests);
        
        int additionalNeeded = targetCount - requests.size();
        for (int i = 0; i < additionalNeeded; i++) {
            // Select a random request to duplicate
            DemandRequest original = requests.get(random.nextInt(requests.size()));
            
            // Create a slightly modified copy with time perturbation
            DemandRequest modified = createPerturbedRequest(original, random, i + requests.size());
            expanded.add(modified);
        }
        
        return expanded;
    }
    
    /**
     * Create a perturbed copy of a demand request.
     */
    private DemandRequest createPerturbedRequest(DemandRequest original, Random random, int newIdx) {
        // Add small temporal perturbation (±5 minutes)
        long timeOffsetSeconds = (long) ((random.nextGaussian() * 300)); // 5 minutes std dev
        java.time.LocalDateTime newTime = original.getRequestTime().plusSeconds(timeOffsetSeconds);
        
        // Keep same spatial locations but with new index
        return new DemandRequest(
            newIdx,
            original.getOriginZone(),
            original.getDestinationZone(),
            newTime.getHour(),
            original.getOriginH3(),
            original.getDestinationH3(),
            original.getOriginWGS84().getX(), // longitude
            original.getOriginWGS84().getY(), // latitude
            original.getDestinationWGS84().getX(), // longitude
            original.getDestinationWGS84().getY(), // latitude
            newTime,
            new CoordinateTransformationUtil()
        );
    }
    
    /**
     * Apply temporal perturbations based on the rule number.
     */
    private List<DemandRequest> applyTemporalPerturbations(List<DemandRequest> requests, 
                                                          UncertaintyRule rule, Random random) {
        // Different rules apply different temporal perturbation patterns
        double perturbationStrength = getTemporalPerturbationStrength(rule.getRuleNumber());
        
        return requests.stream()
                      .map(request -> applyTemporalPerturbation(request, perturbationStrength, random))
                      .collect(Collectors.toList());
    }
    
    /**
     * Get the temporal perturbation strength for a rule number.
     */
    private double getTemporalPerturbationStrength(int ruleNumber) {
        switch (ruleNumber) {
            case 1: return 60.0;   // ±1 minute std deviation
            case 2: return 180.0;  // ±3 minutes std deviation
            case 3: return 300.0;  // ±5 minutes std deviation
            default: return 120.0; // ±2 minutes default
        }
    }
    
    /**
     * Apply temporal perturbation to a single request.
     */
    private DemandRequest applyTemporalPerturbation(DemandRequest original, 
                                                   double perturbationStrength, Random random) {
        // Add Gaussian noise to the request time
        long timeOffsetSeconds = (long) (random.nextGaussian() * perturbationStrength);
        java.time.LocalDateTime newTime = original.getRequestTime().plusSeconds(timeOffsetSeconds);
        
        // Ensure time stays within reasonable bounds (7:00-22:00)
        if (newTime.getHour() < 7) {
            newTime = newTime.withHour(7).withMinute(0).withSecond(0);
        } else if (newTime.getHour() >= 22) {
            newTime = newTime.withHour(21).withMinute(59).withSecond(59);
        }
        
        return new DemandRequest(
            original.getIdx(),
            original.getOriginZone(),
            original.getDestinationZone(),
            newTime.getHour(),
            original.getOriginH3(),
            original.getDestinationH3(),
            original.getOriginWGS84().getX(),
            original.getOriginWGS84().getY(),
            original.getDestinationWGS84().getX(),
            original.getDestinationWGS84().getY(),
            newTime,
            new CoordinateTransformationUtil()
        );
    }
    
    /**
     * Generate all standard uncertainty rules.
     * Creates rules for all combinations of multipliers and rule numbers.
     */
    public List<UncertaintyRule> generateStandardUncertaintyRules() {
        List<UncertaintyRule> rules = new ArrayList<>();
        
        for (double multiplier : STANDARD_MULTIPLIERS) {
            for (int ruleNumber : STANDARD_RULES) {
                rules.add(new UncertaintyRule(multiplier, ruleNumber));
            }
        }
        
        log.info("Generated {} standard uncertainty rules", rules.size());
        return rules;
    }
    
    /**
     * Process base demand with all standard uncertainty rules.
     * 
     * @param baseDemand Base demand requests
     * @return Map of uncertainty rules to processed demand lists
     */
    public Map<UncertaintyRule, List<DemandRequest>> processWithAllStandardRules(
            List<DemandRequest> baseDemand) {
        
        Map<UncertaintyRule, List<DemandRequest>> results = new HashMap<>();
        List<UncertaintyRule> rules = generateStandardUncertaintyRules();
        
        for (UncertaintyRule rule : rules) {
            List<DemandRequest> processedDemand = applyUncertaintyRule(baseDemand, rule);
            results.put(rule, processedDemand);
        }
        
        return results;
    }
}