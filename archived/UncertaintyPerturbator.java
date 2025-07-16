package org.matsim.maas.utils;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.*;
import org.matsim.maas.utils.RulesDataParser.DemandPattern;

import java.util.*;

public class UncertaintyPerturbator {
    
    private final Random random;
    private static final double TEMPORAL_PERTURBATION_STD_MINUTES = 20.0;
    private static final double SPATIAL_PERTURBATION_STD_METERS = 200.0;
    
    public UncertaintyPerturbator(long seed) {
        this.random = new Random(seed);
    }
    
    public static class PerturbationResult {
        public Population perturbedPopulation;
        public String perturbationRule;
        public int temporalPerturbations;
        public int spatialPerturbations;
        public int demandPerturbations;
        public double perturbationIntensity;
        public Map<String, Integer> perturbationSummary;
        
        public PerturbationResult(Population population, String rule) {
            this.perturbedPopulation = population;
            this.perturbationRule = rule;
            this.perturbationSummary = new HashMap<>();
        }
        
        public void printSummary() {
            System.out.println("=== Uncertainty Perturbation Summary ===");
            System.out.println("Perturbation Rule: " + perturbationRule);
            System.out.println("Perturbation Intensity: " + String.format("%.3f", perturbationIntensity));
            System.out.println("Temporal Perturbations: " + temporalPerturbations);
            System.out.println("Spatial Perturbations: " + spatialPerturbations);
            System.out.println("Demand Perturbations: " + demandPerturbations);
        }
    }
    
    public PerturbationResult applyUncertaintyPerturbations(Population population, DemandPattern demandPattern) {
        
        Population perturbedPopulation = clonePopulationStructure(population);
        String ruleDescription = getPerturbationRuleDescription(demandPattern.rule);
        
        PerturbationResult result = new PerturbationResult(perturbedPopulation, ruleDescription);
        
        switch (demandPattern.rule) {
            case 1:
                applyRule1TemporalPerturbations(perturbedPopulation, result);
                break;
            case 2:
                applyRule2SpatialPerturbations(perturbedPopulation, result);
                break;
            case 3:
                applyRule3CombinedPerturbations(perturbedPopulation, result);
                break;
            default:
                break;
        }
        
        result.perturbationIntensity = calculatePerturbationIntensity(result);
        
        return result;
    }
    
    private void applyRule1TemporalPerturbations(Population population, PerturbationResult result) {
        int temporalChanges = 0;
        
        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                for (PlanElement element : plan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        
                        if (random.nextDouble() < 0.3) {
                            double currentTime = activity.getEndTime().orElse(0.0);
                            
                            double perturbationMinutes = random.nextGaussian() * TEMPORAL_PERTURBATION_STD_MINUTES;
                            double perturbationSeconds = perturbationMinutes * 60.0;
                            
                            double newTime = Math.max(0.0, currentTime + perturbationSeconds);
                            
                            if (Math.abs(newTime - currentTime) > 60.0) {
                                activity.setEndTime(newTime);
                                temporalChanges++;
                                
                                String shiftCategory = categorizeTimeShift(perturbationMinutes);
                                result.perturbationSummary.merge(shiftCategory, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }
        
        result.temporalPerturbations = temporalChanges;
    }
    
    private void applyRule2SpatialPerturbations(Population population, PerturbationResult result) {
        int spatialChanges = 0;
        
        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                for (PlanElement element : plan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        
                        if (random.nextDouble() < 0.25) {
                            Coord currentCoord = activity.getCoord();
                            
                            double angle = random.nextDouble() * 2 * Math.PI;
                            double distance = Math.abs(random.nextGaussian()) * SPATIAL_PERTURBATION_STD_METERS;
                            
                            double xOffset = Math.cos(angle) * distance;
                            double yOffset = Math.sin(angle) * distance;
                            
                            Coord newCoord = new Coord(currentCoord.getX() + xOffset, currentCoord.getY() + yOffset);
                            
                            if (distance > 50.0) {
                                activity.setCoord(newCoord);
                                spatialChanges++;
                                
                                String distanceCategory = categorizeDistance(distance);
                                result.perturbationSummary.merge(distanceCategory, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }
        
        result.spatialPerturbations = spatialChanges;
    }
    
    private void applyRule3CombinedPerturbations(Population population, PerturbationResult result) {
        int temporalChanges = 0;
        int spatialChanges = 0;
        int demandChanges = 0;
        
        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                
                double perturbationType = random.nextDouble();
                
                if (perturbationType < 0.15) {
                    temporalChanges += applyTemporalPerturbationsToPerson(person, result.perturbationSummary);
                    
                } else if (perturbationType < 0.25) {
                    spatialChanges += applySpatialPerturbationsToPerson(person, result.perturbationSummary);
                    
                } else if (perturbationType < 0.30) {
                    temporalChanges += applyTemporalPerturbationsToPerson(person, result.perturbationSummary);
                    spatialChanges += applySpatialPerturbationsToPerson(person, result.perturbationSummary);
                    result.perturbationSummary.merge("combined_temporal_spatial", 1, Integer::sum);
                    
                } else if (perturbationType < 0.35) {
                    demandChanges += applyDemandPerturbationsToPerson(person, result.perturbationSummary);
                }
            }
        }
        
        result.temporalPerturbations = temporalChanges;
        result.spatialPerturbations = spatialChanges;
        result.demandPerturbations = demandChanges;
    }
    
    private int applyTemporalPerturbationsToPerson(Person person, Map<String, Integer> summary) {
        int changes = 0;
        Plan plan = person.getSelectedPlan();
        
        for (PlanElement element : plan.getPlanElements()) {
            if (element instanceof Activity) {
                Activity activity = (Activity) element;
                
                double currentTime = activity.getEndTime().orElse(0.0);
                double perturbationMinutes = random.nextGaussian() * (TEMPORAL_PERTURBATION_STD_MINUTES * 0.7);
                double perturbationSeconds = perturbationMinutes * 60.0;
                
                double newTime = Math.max(0.0, currentTime + perturbationSeconds);
                
                if (Math.abs(newTime - currentTime) > 60.0) {
                    activity.setEndTime(newTime);
                    changes++;
                    summary.merge("temporal_" + categorizeTimeShift(perturbationMinutes), 1, Integer::sum);
                }
            }
        }
        
        return changes;
    }
    
    private int applySpatialPerturbationsToPerson(Person person, Map<String, Integer> summary) {
        int changes = 0;
        Plan plan = person.getSelectedPlan();
        
        for (PlanElement element : plan.getPlanElements()) {
            if (element instanceof Activity) {
                Activity activity = (Activity) element;
                
                Coord currentCoord = activity.getCoord();
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = Math.abs(random.nextGaussian()) * (SPATIAL_PERTURBATION_STD_METERS * 0.7);
                
                double xOffset = Math.cos(angle) * distance;
                double yOffset = Math.sin(angle) * distance;
                
                Coord newCoord = new Coord(currentCoord.getX() + xOffset, currentCoord.getY() + yOffset);
                
                if (distance > 50.0) {
                    activity.setCoord(newCoord);
                    changes++;
                    summary.merge("spatial_" + categorizeDistance(distance), 1, Integer::sum);
                }
            }
        }
        
        return changes;
    }
    
    private int applyDemandPerturbationsToPerson(Person person, Map<String, Integer> summary) {
        Plan plan = person.getSelectedPlan();
        
        List<PlanElement> elements = plan.getPlanElements();
        if (elements.size() >= 5) {
            Activity workActivity = (Activity) elements.get(2);
            
            double workEndTime = workActivity.getEndTime().orElse(0.0);
            double durationPerturbation = (random.nextGaussian() * 2 * 3600);
            
            double newWorkEndTime = Math.max(workEndTime - 4 * 3600, workEndTime + durationPerturbation);
            workActivity.setEndTime(newWorkEndTime);
            
            summary.merge("demand_duration_change", 1, Integer::sum);
            return 1;
        }
        
        return 0;
    }
    
    private String categorizeTimeShift(double perturbationMinutes) {
        double absPerturbation = Math.abs(perturbationMinutes);
        if (absPerturbation < 5) return "small_shift_<5min";
        else if (absPerturbation < 15) return "medium_shift_5-15min";
        else if (absPerturbation < 30) return "large_shift_15-30min";
        else return "very_large_shift_>30min";
    }
    
    private String categorizeDistance(double distanceMeters) {
        if (distanceMeters < 100) return "small_distance_<100m";
        else if (distanceMeters < 300) return "medium_distance_100-300m";
        else if (distanceMeters < 500) return "large_distance_300-500m";
        else return "very_large_distance_>500m";
    }
    
    private double calculatePerturbationIntensity(PerturbationResult result) {
        int totalPersons = result.perturbedPopulation.getPersons().size();
        int totalPerturbations = result.temporalPerturbations + result.spatialPerturbations + result.demandPerturbations;
        
        if (totalPersons == 0) return 0.0;
        
        return (double) totalPerturbations / (totalPersons * 3.0);
    }
    
    private Population clonePopulationStructure(Population originalPopulation) {
        Population clonedPopulation = org.matsim.core.population.PopulationUtils.createPopulation(
            org.matsim.core.config.ConfigUtils.createConfig());
        
        for (org.matsim.api.core.v01.population.Person originalPerson : originalPopulation.getPersons().values()) {
            String newPersonId = "perturbed_person_" + originalPerson.getId().toString();
            org.matsim.api.core.v01.population.Person clonedPerson = 
                org.matsim.core.population.PopulationUtils.getFactory().createPerson(
                    org.matsim.api.core.v01.Id.createPersonId(newPersonId));
            
            Plan originalPlan = originalPerson.getSelectedPlan();
            if (originalPlan != null) {
                Plan clonedPlan = org.matsim.core.population.PopulationUtils.getFactory().createPlan();
                
                for (PlanElement element : originalPlan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity originalActivity = (Activity) element;
                        Activity clonedActivity = org.matsim.core.population.PopulationUtils.getFactory()
                            .createActivityFromCoord(originalActivity.getType(), originalActivity.getCoord());
                        if (originalActivity.getEndTime().isDefined()) {
                            clonedActivity.setEndTime(originalActivity.getEndTime().seconds());
                        }
                        clonedPlan.addActivity(clonedActivity);
                    } else if (element instanceof Leg) {
                        Leg originalLeg = (Leg) element;
                        Leg clonedLeg = org.matsim.core.population.PopulationUtils.getFactory()
                            .createLeg(originalLeg.getMode());
                        clonedPlan.addLeg(clonedLeg);
                    }
                }
                
                clonedPerson.addPlan(clonedPlan);
                clonedPerson.setSelectedPlan(clonedPlan);
            }
            
            clonedPopulation.addPerson(clonedPerson);
        }
        
        return clonedPopulation;
    }
    
    private String getPerturbationRuleDescription(int rule) {
        switch (rule) {
            case 1: return "TEMPORAL_UNCERTAINTY_PERTURBATIONS";
            case 2: return "SPATIAL_UNCERTAINTY_PERTURBATIONS";
            case 3: return "COMBINED_UNCERTAINTY_PERTURBATIONS";
            default: return "NO_UNCERTAINTY_PERTURBATIONS";
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: UncertaintyPerturbator <population_file> <scenario> <multiplier> <rule>");
            System.exit(1);
        }
        
        String populationFile = args[0];
        String scenario = args[1];
        double multiplier = Double.parseDouble(args[2]);
        int rule = Integer.parseInt(args[3]);
        
        try {
            System.out.println("=== Testing Uncertainty Perturbation ===");
            System.out.println("Scenario: " + scenario + ", Multiplier: " + multiplier + ", Rule: " + rule);
            
            org.matsim.api.core.v01.Scenario matsimScenario = 
                org.matsim.core.scenario.ScenarioUtils.createScenario(org.matsim.core.config.ConfigUtils.createConfig());
            org.matsim.core.population.io.PopulationReader reader = 
                new org.matsim.core.population.io.PopulationReader(matsimScenario);
            reader.readFile(populationFile);
            Population population = matsimScenario.getPopulation();
            
            RulesDataParser.RulesDataSet rulesData = RulesDataParser.loadAllRulesData(
                "data/demands/hwaseong/rules", "data/demands/hwaseong/real_time");
            
            RulesDataParser.DemandPattern demandPattern = rulesData.getDemandPattern(scenario, multiplier, rule);
            
            if (demandPattern == null) {
                System.err.println("Demand pattern not found!");
                System.exit(1);
            }
            
            UncertaintyPerturbator perturbator = new UncertaintyPerturbator(42);
            PerturbationResult result = perturbator.applyUncertaintyPerturbations(population, demandPattern);
            
            result.printSummary();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
