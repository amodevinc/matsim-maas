package org.matsim.maas.utils;

import org.matsim.api.core.v01.population.*;
import org.matsim.maas.utils.RulesDataParser.DemandPattern;
import org.matsim.maas.utils.RulesDataParser.RealTimeTrip;

import java.util.*;

/**
 * Adjusts population activity departure times to match hourly demand patterns from rules CSV files.
 * Handles temporal constraints, activity chaining, and scenario-specific patterns.
 */
public class TemporalPatternAdjuster {
    
    private final Random random;
    private static final double WORK_DURATION_HOURS = 8.0;
    private static final double TRAVEL_TIME_MINUTES = 30.0;
    
    public TemporalPatternAdjuster(long seed) {
        this.random = new Random(seed);
    }
    
    /**
     * Result container for temporal adjustment
     */
    public static class TemporalAdjustmentResult {
        public Population adjustedPopulation;
        public Map<Integer, Integer> hourlyDepartureDistribution;
        public Map<Integer, Integer> targetHourlyDistribution;
        public String adjustmentMethod;
        public double temporalFitness;
        
        public TemporalAdjustmentResult(Population population, String method) {
            this.adjustedPopulation = population;
            this.adjustmentMethod = method;
            this.hourlyDepartureDistribution = new HashMap<>();
            this.targetHourlyDistribution = new HashMap<>();
        }
        
        public void printSummary() {
            System.out.println("=== Temporal Adjustment Summary ===");
            System.out.println("Adjustment Method: " + adjustmentMethod);
            System.out.println("Temporal Fitness: " + String.format("%.3f", temporalFitness));
            System.out.println("Hourly Distribution (Target vs Actual):");
            
            Set<Integer> allHours = new TreeSet<>();
            allHours.addAll(targetHourlyDistribution.keySet());
            allHours.addAll(hourlyDepartureDistribution.keySet());
            
            for (Integer hour : allHours) {
                int target = targetHourlyDistribution.getOrDefault(hour, 0);
                int actual = hourlyDepartureDistribution.getOrDefault(hour, 0);
                System.out.println(String.format("  %02d:00 -> Target: %4d, Actual: %4d, Diff: %+4d", 
                    hour, target, actual, actual - target));
            }
        }
    }
    
    /**
     * Adjust population temporal patterns based on demand pattern
     */
    public TemporalAdjustmentResult adjustTemporalPatterns(Population population, DemandPattern demandPattern, 
                                                           List<RealTimeTrip> realTimeTrips) {
        
        // Clone population structure
        Population adjustedPopulation = clonePopulationStructure(population);
        
        // Get target hourly distribution
        Map<Integer, Integer> targetHourlyDist = calculateTargetDistribution(demandPattern);
        
        // Apply scenario-specific adjustments
        String adjustmentMethod = getAdjustmentMethod(demandPattern.scenario);
        applyScenarioAdjustments(adjustedPopulation, demandPattern, targetHourlyDist, realTimeTrips);
        
        // Create result and calculate fitness
        TemporalAdjustmentResult result = new TemporalAdjustmentResult(adjustedPopulation, adjustmentMethod);
        result.targetHourlyDistribution = targetHourlyDist;
        result.hourlyDepartureDistribution = calculateActualDistribution(adjustedPopulation);
        result.temporalFitness = calculateTemporalFitness(result.targetHourlyDistribution, result.hourlyDepartureDistribution);
        
        return result;
    }
    
    /**
     * Calculate target hourly distribution from demand pattern
     */
    private Map<Integer, Integer> calculateTargetDistribution(DemandPattern demandPattern) {
        Map<Integer, Integer> targetDist = new HashMap<>();
        
        for (Map.Entry<Integer, Double> entry : demandPattern.hourlyTotals.entrySet()) {
            int hour = entry.getKey();
            double trips = entry.getValue();
            int departures = (int) Math.round(trips);
            if (departures > 0) {
                targetDist.put(hour, departures);
            }
        }
        
        return targetDist;
    }
    
    /**
     * Apply scenario-specific temporal adjustments
     */
    private void applyScenarioAdjustments(Population population, DemandPattern demandPattern, 
                                        Map<Integer, Integer> targetDist, List<RealTimeTrip> realTimeTrips) {
        
        // Redistribute activities to match target distribution
        redistributeActivitiesByTargetDistribution(population, targetDist);
        
        // Apply scenario-specific modifications
        switch (demandPattern.scenario) {
            case "S1":
                enhanceTemporalPeaks(population, Arrays.asList(7, 8, 17, 18));
                break;
            case "S2":
                addSpatialConcentrationEffects(population);
                break;
            case "S3":
                enhanceTemporalPeaks(population, Arrays.asList(7, 8, 17, 18));
                addSpatialConcentrationEffects(population);
                break;
            case "S4":
                if (!realTimeTrips.isEmpty()) {
                    applyRealTimePatterns(population, realTimeTrips);
                }
                break;
        }
        
        // Ensure activity chaining constraints
        enforceActivityChaining(population);
    }
    
    /**
     * Redistribute activities to match target hourly distribution
     */
    private void redistributeActivitiesByTargetDistribution(Population population, Map<Integer, Integer> targetDist) {
        // Collect all home departure activities
        List<ActivityInfo> departures = collectHomeDepartures(population);
        
        // Create time slots based on target distribution
        List<TimeSlot> timeSlots = createTimeSlots(targetDist);
        
        // Randomly assign time slots to departures
        Collections.shuffle(timeSlots, random);
        Collections.shuffle(departures, random);
        
        int assignmentCount = Math.min(departures.size(), timeSlots.size());
        for (int i = 0; i < assignmentCount; i++) {
            departures.get(i).activity.setEndTime(timeSlots.get(i).departureTime);
        }
    }
    
    /**
     * Collect home departure activities from population
     */
    private List<ActivityInfo> collectHomeDepartures(Population population) {
        List<ActivityInfo> departures = new ArrayList<>();
        
        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                for (int i = 0; i < plan.getPlanElements().size(); i++) {
                    PlanElement element = plan.getPlanElements().get(i);
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        if ("home".equals(activity.getType()) && activity.getEndTime().isDefined()) {
                            departures.add(new ActivityInfo(activity, person, i));
                        }
                    }
                }
            }
        }
        
        return departures;
    }
    
    /**
     * Create time slots based on target distribution
     */
    private List<TimeSlot> createTimeSlots(Map<Integer, Integer> targetDist) {
        List<TimeSlot> timeSlots = new ArrayList<>();
        
        for (Map.Entry<Integer, Integer> entry : targetDist.entrySet()) {
            int hour = entry.getKey();
            int count = entry.getValue();
            
            for (int i = 0; i < count; i++) {
                double timeInHour = random.nextDouble() * 3600.0;
                double departureTime = hour * 3600.0 + timeInHour;
                timeSlots.add(new TimeSlot(departureTime, hour));
            }
        }
        
        return timeSlots;
    }
    
    /**
     * Enhance temporal peaks for specific hours
     */
    private void enhanceTemporalPeaks(Population population, List<Integer> peakHours) {
        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                for (PlanElement element : plan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        if ("home".equals(activity.getType()) && activity.getEndTime().isDefined()) {
                            double currentTime = activity.getEndTime().seconds();
                            int currentHour = (int) (currentTime / 3600);
                            
                            // Find nearest peak hour
                            int nearestPeak = peakHours.stream()
                                .min(Comparator.comparingInt(h -> Math.abs(h - currentHour)))
                                .orElse(currentHour);
                            
                            // Adjust towards peak with 60% probability
                            if (random.nextDouble() < 0.6) {
                                double peakTime = nearestPeak * 3600.0 + (random.nextGaussian() * 900.0);
                                activity.setEndTime(Math.max(0, peakTime));
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Add spatial concentration temporal effects
     */
    private void addSpatialConcentrationEffects(Population population) {
        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                for (PlanElement element : plan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        if (activity.getEndTime().isDefined()) {
                            double currentTime = activity.getEndTime().seconds();
                            
                            // Add clustering variation with 30% probability
                            if (random.nextDouble() < 0.3) {
                                double variation = random.nextGaussian() * 600.0; // 10min std dev
                                activity.setEndTime(Math.max(0, currentTime + variation));
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Apply real-time patterns from actual trip data
     */
    private void applyRealTimePatterns(Population population, List<RealTimeTrip> realTimeTrips) {
        List<Person> persons = new ArrayList<>(population.getPersons().values());
        Collections.shuffle(persons, random);
        
        int personIndex = 0;
        for (RealTimeTrip trip : realTimeTrips) {
            if (personIndex >= persons.size()) break;
            
            Person person = persons.get(personIndex++);
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                // Find first home departure activity
                for (PlanElement element : plan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        if ("home".equals(activity.getType()) && activity.getEndTime().isDefined()) {
                            activity.setEndTime(trip.departureTimeSeconds);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Enforce activity chaining constraints (arrival <= departure times)
     */
    private void enforceActivityChaining(Population population) {
        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                List<PlanElement> elements = plan.getPlanElements();
                
                // Update work activity end times
                for (int i = 0; i < elements.size(); i++) {
                    PlanElement element = elements.get(i);
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        if ("work".equals(activity.getType())) {
                            double arrivalTime = calculateArrivalTime(elements, i);
                            double workEndTime = arrivalTime + (WORK_DURATION_HOURS * 3600.0);
                            activity.setEndTime(workEndTime);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Calculate arrival time at activity
     */
    private double calculateArrivalTime(List<PlanElement> elements, int activityIndex) {
        if (activityIndex < 2) return 0.0;
        
        PlanElement prevElement = elements.get(activityIndex - 2);
        if (prevElement instanceof Activity) {
            Activity prevActivity = (Activity) prevElement;
            if (prevActivity.getEndTime().isDefined()) {
                return prevActivity.getEndTime().seconds() + (TRAVEL_TIME_MINUTES * 60);
            }
        }
        
        return 0.0;
    }
    
    /**
     * Calculate actual hourly departure distribution
     */
    private Map<Integer, Integer> calculateActualDistribution(Population population) {
        Map<Integer, Integer> distribution = new HashMap<>();
        
        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                for (PlanElement element : plan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        if ("home".equals(activity.getType()) && activity.getEndTime().isDefined()) {
                            int hour = (int) (activity.getEndTime().seconds() / 3600);
                            distribution.merge(hour, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        
        return distribution;
    }
    
    /**
     * Calculate temporal fitness between target and actual distributions
     */
    private double calculateTemporalFitness(Map<Integer, Integer> target, Map<Integer, Integer> actual) {
        Set<Integer> allHours = new HashSet<>();
        allHours.addAll(target.keySet());
        allHours.addAll(actual.keySet());
        
        double totalSquaredError = 0.0;
        double totalTarget = 0.0;
        
        for (Integer hour : allHours) {
            int targetCount = target.getOrDefault(hour, 0);
            int actualCount = actual.getOrDefault(hour, 0);
            
            totalSquaredError += Math.pow(targetCount - actualCount, 2);
            totalTarget += targetCount;
        }
        
        double rmse = Math.sqrt(totalSquaredError / Math.max(1, allHours.size()));
        double avgTarget = totalTarget / Math.max(1, allHours.size());
        double normalizedRmse = avgTarget > 0 ? rmse / avgTarget : 0.0;
        
        return Math.max(0.0, 1.0 - normalizedRmse);
    }
    
    /**
     * Clone population structure
     */
    private Population clonePopulationStructure(Population originalPopulation) {
        PopulationScaler scaler = new PopulationScaler(random.nextLong());
        PopulationScaler.ScaledPopulation scaledPop = scaler.scalePopulation(originalPopulation, 1.0, "temp", 1);
        return scaledPop.population;
    }
    
    /**
     * Get adjustment method name for scenario
     */
    private String getAdjustmentMethod(String scenario) {
        switch (scenario) {
            case "S1": return "TEMPORAL_PEAKS_ENHANCEMENT";
            case "S2": return "SPATIAL_CONCENTRATION_TEMPORAL";
            case "S3": return "COMBINED_TEMPORAL_SPATIAL";
            case "S4": return "SMARTCARD_PATTERN_MATCHING";
            default: return "BASE_UNIFORM_DISTRIBUTION";
        }
    }
    
    /**
     * Helper classes
     */
    private static class ActivityInfo {
        Activity activity;
        Person person;
        int elementIndex;
        
        ActivityInfo(Activity activity, Person person, int elementIndex) {
            this.activity = activity;
            this.person = person;
            this.elementIndex = elementIndex;
        }
    }
    
    private static class TimeSlot {
        double departureTime;
        int hour;
        
        TimeSlot(double departureTime, int hour) {
            this.departureTime = departureTime;
            this.hour = hour;
        }
    }
    
    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: TemporalPatternAdjuster <population_file> <scenario> <multiplier> <rule>");
            System.exit(1);
        }
        
        String populationFile = args[0];
        String scenario = args[1];
        double multiplier = Double.parseDouble(args[2]);
        int rule = Integer.parseInt(args[3]);
        
        try {
            System.out.println("=== Testing Temporal Pattern Adjustment ===");
            System.out.println("Scenario: " + scenario + ", Multiplier: " + multiplier + ", Rule: " + rule);
            
            // Load population
            org.matsim.api.core.v01.Scenario matsimScenario = 
                org.matsim.core.scenario.ScenarioUtils.createScenario(org.matsim.core.config.ConfigUtils.createConfig());
            org.matsim.core.population.io.PopulationReader reader = 
                new org.matsim.core.population.io.PopulationReader(matsimScenario);
            reader.readFile(populationFile);
            Population population = matsimScenario.getPopulation();
            
            // Load rules data
            RulesDataParser.RulesDataSet rulesData = RulesDataParser.loadAllRulesData(
                "data/demands/hwaseong/rules", "data/demands/hwaseong/real_time");
            
            RulesDataParser.DemandPattern demandPattern = rulesData.getDemandPattern(scenario, multiplier, rule);
            List<RulesDataParser.RealTimeTrip> realTimeTrips = rulesData.getRealTimeTrips(scenario);
            
            if (demandPattern == null) {
                System.err.println("Demand pattern not found!");
                System.exit(1);
            }
            
            // Apply temporal adjustments
            TemporalPatternAdjuster adjuster = new TemporalPatternAdjuster(42);
            TemporalAdjustmentResult result = adjuster.adjustTemporalPatterns(population, demandPattern, realTimeTrips);
            
            result.printSummary();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
} 