package org.matsim.maas.utils;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.config.ConfigUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Scales MATSim population sizes based on trip multipliers while maintaining 
 * representative demographic and spatial distributions.
 * Handles both down-scaling (0.5x) and up-scaling (1.5x) scenarios.
 */
public class PopulationScaler {
    
    private final Random random;
    private int nextPersonId;
    
    public PopulationScaler(long seed) {
        this.random = new Random(seed);
        this.nextPersonId = 0;
    }
    
    /**
     * Result container for scaled population
     */
    public static class ScaledPopulation {
        public Population population;
        public int originalSize;
        public int scaledSize;
        public double actualMultiplier;
        public String scalingMethod;
        
        public ScaledPopulation(Population population, int originalSize, String scalingMethod) {
            this.population = population;
            this.originalSize = originalSize;
            this.scaledSize = population.getPersons().size();
            this.actualMultiplier = (double) scaledSize / originalSize;
            this.scalingMethod = scalingMethod;
        }
        
        public void printSummary() {
            System.out.println("=== Population Scaling Summary ===");
            System.out.println("Original Size: " + originalSize);
            System.out.println("Scaled Size: " + scaledSize);
            System.out.println("Target Multiplier: " + actualMultiplier);
            System.out.println("Scaling Method: " + scalingMethod);
        }
    }
    
    /**
     * Scale population based on trip multiplier
     */
    public ScaledPopulation scalePopulation(Population originalPopulation, double tripMultiplier, String scenario, int rule) {
        if (Math.abs(tripMultiplier - 1.0) < 0.001) {
            // No scaling needed for 1.0x multiplier
            Population clonedPop = clonePopulation(originalPopulation, scenario, rule);
            return new ScaledPopulation(clonedPop, originalPopulation.getPersons().size(), "NO_SCALING");
        } else if (tripMultiplier < 1.0) {
            // Down-scale for under-prediction scenarios
            return downScalePopulation(originalPopulation, tripMultiplier, scenario, rule);
        } else {
            // Up-scale for over-prediction scenarios  
            return upScalePopulation(originalPopulation, tripMultiplier, scenario, rule);
        }
    }
    
    /**
     * Down-scale population by sampling representative subset
     */
    private ScaledPopulation downScalePopulation(Population originalPopulation, double tripMultiplier, String scenario, int rule) {
        int originalSize = originalPopulation.getPersons().size();
        int targetSize = (int) Math.round(originalSize * tripMultiplier);
        
        // Create stratified sample to maintain spatial distribution
        Map<String, List<Person>> spatialStrata = createSpatialStrata(originalPopulation);
        
        Population scaledPopulation = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        
        // Sample from each stratum proportionally
        for (Map.Entry<String, List<Person>> stratum : spatialStrata.entrySet()) {
            List<Person> stratumPersons = stratum.getValue();
            int stratumTargetSize = Math.max(1, (int) Math.round(stratumPersons.size() * tripMultiplier));
            
            // Randomly sample from this stratum
            Collections.shuffle(stratumPersons, random);
            for (int i = 0; i < Math.min(stratumTargetSize, stratumPersons.size()); i++) {
                Person originalPerson = stratumPersons.get(i);
                Person clonedPerson = clonePerson(originalPerson, scenario, rule, nextPersonId++);
                scaledPopulation.addPerson(clonedPerson);
            }
        }
        
        return new ScaledPopulation(scaledPopulation, originalSize, "STRATIFIED_SAMPLING");
    }
    
    /**
     * Up-scale population by duplicating and slightly varying persons
     */
    private ScaledPopulation upScalePopulation(Population originalPopulation, double tripMultiplier, String scenario, int rule) {
        int originalSize = originalPopulation.getPersons().size();
        int targetSize = (int) Math.round(originalSize * tripMultiplier);
        int additionalPersons = targetSize - originalSize;
        
        Population scaledPopulation = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        
        // First, add all original persons
        for (Person originalPerson : originalPopulation.getPersons().values()) {
            Person clonedPerson = clonePerson(originalPerson, scenario, rule, nextPersonId++);
            scaledPopulation.addPerson(clonedPerson);
        }
        
        // Then, duplicate persons with variations to reach target size
        List<Person> originalPersonsList = new ArrayList<>(originalPopulation.getPersons().values());
        
        for (int i = 0; i < additionalPersons; i++) {
            // Select random person to duplicate
            Person originalPerson = originalPersonsList.get(random.nextInt(originalPersonsList.size()));
            Person duplicatedPerson = clonePersonWithVariation(originalPerson, scenario, rule, nextPersonId++);
            scaledPopulation.addPerson(duplicatedPerson);
        }
        
        return new ScaledPopulation(scaledPopulation, originalSize, "DUPLICATION_WITH_VARIATION");
    }
    
    /**
     * Create spatial strata for stratified sampling based on home locations
     */
    private Map<String, List<Person>> createSpatialStrata(Population population) {
        Map<String, List<Person>> strata = new HashMap<>();
        
        for (Person person : population.getPersons().values()) {
            Coord homeCoord = getHomeCoordinate(person);
            if (homeCoord != null) {
                // Create spatial grid for stratification (1km x 1km cells)
                String stratum = getSpatialStratum(homeCoord, 1000.0);
                strata.computeIfAbsent(stratum, k -> new ArrayList<>()).add(person);
            }
        }
        
        return strata;
    }
    
    /**
     * Get home coordinate from person's first activity
     */
    private Coord getHomeCoordinate(Person person) {
        Plan selectedPlan = person.getSelectedPlan();
        if (selectedPlan != null && !selectedPlan.getPlanElements().isEmpty()) {
            PlanElement firstElement = selectedPlan.getPlanElements().get(0);
            if (firstElement instanceof Activity) {
                Activity homeActivity = (Activity) firstElement;
                if ("home".equals(homeActivity.getType())) {
                    return homeActivity.getCoord();
                }
            }
        }
        return null;
    }
    
    /**
     * Get spatial stratum identifier for coordinate
     */
    private String getSpatialStratum(Coord coord, double gridSize) {
        int gridX = (int) Math.floor(coord.getX() / gridSize);
        int gridY = (int) Math.floor(coord.getY() / gridSize);
        return gridX + "_" + gridY;
    }
    
    /**
     * Clone population with new person IDs
     */
    private Population clonePopulation(Population originalPopulation, String scenario, int rule) {
        Population clonedPopulation = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        
        for (Person originalPerson : originalPopulation.getPersons().values()) {
            Person clonedPerson = clonePerson(originalPerson, scenario, rule, nextPersonId++);
            clonedPopulation.addPerson(clonedPerson);
        }
        
        return clonedPopulation;
    }
    
    /**
     * Clone a person with new ID
     */
    private Person clonePerson(Person originalPerson, String scenario, int rule, int newPersonId) {
        String newPersonIdStr = scenario + "_r" + rule + "_person_" + newPersonId;
        Person clonedPerson = PopulationUtils.getFactory().createPerson(Id.createPersonId(newPersonIdStr));
        
        // Clone the selected plan
        Plan originalPlan = originalPerson.getSelectedPlan();
        if (originalPlan != null) {
            Plan clonedPlan = PopulationUtils.getFactory().createPlan();
            
            for (PlanElement element : originalPlan.getPlanElements()) {
                if (element instanceof Activity) {
                    Activity originalActivity = (Activity) element;
                    Activity clonedActivity = PopulationUtils.getFactory().createActivityFromCoord(originalActivity.getType(), originalActivity.getCoord());
                    if (originalActivity.getEndTime().isDefined()) {
                        clonedActivity.setEndTime(originalActivity.getEndTime().seconds());
                    }
                    clonedPlan.addActivity(clonedActivity);
                } else if (element instanceof Leg) {
                    Leg originalLeg = (Leg) element;
                    Leg clonedLeg = PopulationUtils.getFactory().createLeg(originalLeg.getMode());
                    clonedPlan.addLeg(clonedLeg);
                }
            }
            
            clonedPerson.addPlan(clonedPlan);
            clonedPerson.setSelectedPlan(clonedPlan);
        }
        
        return clonedPerson;
    }
    
    /**
     * Clone a person with slight variations in timing and coordinates
     */
    private Person clonePersonWithVariation(Person originalPerson, String scenario, int rule, int newPersonId) {
        Person clonedPerson = clonePerson(originalPerson, scenario, rule, newPersonId);
        
        // Apply small variations to timing and coordinates
        Plan clonedPlan = clonedPerson.getSelectedPlan();
        if (clonedPlan != null) {
            for (PlanElement element : clonedPlan.getPlanElements()) {
                if (element instanceof Activity) {
                    Activity activity = (Activity) element;
                    
                    // Add small coordinate variation (within 100m)
                    Coord originalCoord = activity.getCoord();
                    if (originalCoord != null) {
                        double xVariation = (random.nextGaussian() * 50.0); // 50m std deviation
                        double yVariation = (random.nextGaussian() * 50.0);
                        Coord variedCoord = new Coord(
                            originalCoord.getX() + xVariation,
                            originalCoord.getY() + yVariation
                        );
                        activity.setCoord(variedCoord);
                    }
                    
                    // Add small timing variation (within 15 minutes)
                    if (activity.getEndTime().isDefined()) {
                        double originalTime = activity.getEndTime().seconds();
                        double timeVariation = random.nextGaussian() * 900.0; // 15 min std deviation
                        double variedTime = Math.max(0, originalTime + timeVariation);
                        activity.setEndTime(variedTime);
                    }
                }
            }
        }
        
        return clonedPerson;
    }
    
    /**
     * Validate scaled population maintains key characteristics
     */
    public static boolean validateScaledPopulation(Population originalPop, ScaledPopulation scaledPop, double tolerance) {
        // Check activity type distribution
        Map<String, Integer> originalActivityTypes = countActivityTypes(originalPop);
        Map<String, Integer> scaledActivityTypes = countActivityTypes(scaledPop.population);
        
        for (String actType : originalActivityTypes.keySet()) {
            double originalRatio = (double) originalActivityTypes.get(actType) / (originalPop.getPersons().size() * 3); // 3 activities per person
            double scaledRatio = (double) scaledActivityTypes.getOrDefault(actType, 0) / (scaledPop.scaledSize * 3);
            
            if (Math.abs(originalRatio - scaledRatio) > tolerance) {
                System.err.println("Activity type ratio deviation for " + actType + ": " + 
                    Math.abs(originalRatio - scaledRatio));
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Count activity types in population
     */
    private static Map<String, Integer> countActivityTypes(Population population) {
        Map<String, Integer> activityCounts = new HashMap<>();
        
        for (Person person : population.getPersons().values()) {
            Plan selectedPlan = person.getSelectedPlan();
            if (selectedPlan != null) {
                for (PlanElement element : selectedPlan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        activityCounts.merge(activity.getType(), 1, Integer::sum);
                    }
                }
            }
        }
        
        return activityCounts;
    }
    
    /**
     * Main method for testing and command-line usage
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: PopulationScaler <population_file> <multiplier> <scenario>");
            System.exit(1);
        }
        
        String populationFile = args[0];
        double multiplier = Double.parseDouble(args[1]);
        String scenario = args[2];
        
        try {
            // Load original population
            PopulationAnalyzer.PopulationStatistics originalStats = 
                PopulationAnalyzer.analyzePopulation(populationFile);
            originalStats.printSummary();
            
            org.matsim.api.core.v01.Scenario matsimScenario = 
                org.matsim.core.scenario.ScenarioUtils.createScenario(ConfigUtils.createConfig());
            org.matsim.core.population.io.PopulationReader reader = 
                new org.matsim.core.population.io.PopulationReader(matsimScenario);
            reader.readFile(populationFile);
            Population originalPopulation = matsimScenario.getPopulation();
            
            // Scale population
            PopulationScaler scaler = new PopulationScaler(42); // Fixed seed for reproducibility
            ScaledPopulation scaledPop = scaler.scalePopulation(originalPopulation, multiplier, scenario, 1);
            scaledPop.printSummary();
            
            // Validate scaling
            boolean isValid = validateScaledPopulation(originalPopulation, scaledPop, 0.05); // 5% tolerance
            System.out.println("Scaling validation: " + (isValid ? "PASSED" : "FAILED"));
            
            // Analyze scaled population
            PopulationAnalyzer.PopulationStatistics scaledStats = 
                PopulationAnalyzer.analyzePopulation(scaledPop.population);
            System.out.println("\n=== Scaled Population Analysis ===");
            scaledStats.printSummary();
            
        } catch (Exception e) {
            System.err.println("Error scaling population: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
} 