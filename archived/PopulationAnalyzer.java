package org.matsim.maas.utils;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Analyzes existing MATSim population files to extract patterns for population generation.
 * Used to understand the structure and characteristics of base population files.
 */
public class PopulationAnalyzer {
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    public static class PopulationStatistics {
        public int totalPersons;
        public int totalActivities;
        public int totalLegs;
        public Map<String, Integer> activityTypeCounts;
        public Map<String, Integer> legModeCounts;
        public List<Double> departureTimes;
        public Coord minCoord;
        public Coord maxCoord;
        public Map<String, Integer> activityDurations;
        
        public PopulationStatistics() {
            this.activityTypeCounts = new HashMap<>();
            this.legModeCounts = new HashMap<>();
            this.departureTimes = new ArrayList<>();
            this.activityDurations = new HashMap<>();
        }
        
        public void printSummary() {
            System.out.println("=== Population Statistics ===");
            System.out.println("Total Persons: " + totalPersons);
            System.out.println("Total Activities: " + totalActivities);
            System.out.println("Total Legs: " + totalLegs);
            System.out.println("\nActivity Types: " + activityTypeCounts);
            System.out.println("Leg Modes: " + legModeCounts);
            System.out.println("\nCoordinate Bounds:");
            System.out.println("  Min: (" + minCoord.getX() + ", " + minCoord.getY() + ")");
            System.out.println("  Max: (" + maxCoord.getX() + ", " + maxCoord.getY() + ")");
            System.out.println("\nDeparture Time Range:");
            if (!departureTimes.isEmpty()) {
                System.out.println("  Min: " + formatTime(departureTimes.stream().min(Double::compareTo).orElse(0.0)));
                System.out.println("  Max: " + formatTime(departureTimes.stream().max(Double::compareTo).orElse(0.0)));
                System.out.println("  Count: " + departureTimes.size());
            }
        }
        
        private String formatTime(double timeSeconds) {
            int hours = (int) (timeSeconds / 3600);
            int minutes = (int) ((timeSeconds % 3600) / 60);
            int seconds = (int) (timeSeconds % 60);
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }
    
    /**
     * Analyze a population file and extract key statistics
     */
    public static PopulationStatistics analyzePopulation(String populationFilePath) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        PopulationReader reader = new PopulationReader(scenario);
        reader.readFile(populationFilePath);
        
        return analyzePopulation(scenario.getPopulation());
    }
    
    /**
     * Analyze a population object and extract key statistics
     */
    public static PopulationStatistics analyzePopulation(Population population) {
        PopulationStatistics stats = new PopulationStatistics();
        
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        
        stats.totalPersons = population.getPersons().size();
        
        for (Person person : population.getPersons().values()) {
            Plan selectedPlan = person.getSelectedPlan();
            if (selectedPlan != null) {
                // Analyze plan elements
                for (PlanElement element : selectedPlan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        stats.totalActivities++;
                        
                        // Count activity types
                        String actType = activity.getType();
                        stats.activityTypeCounts.merge(actType, 1, Integer::sum);
                        
                        // Track coordinates
                        Coord coord = activity.getCoord();
                        if (coord != null) {
                            minX = Math.min(minX, coord.getX());
                            minY = Math.min(minY, coord.getY());
                            maxX = Math.max(maxX, coord.getX());
                            maxY = Math.max(maxY, coord.getY());
                        }
                        
                        // Track departure times
                        if (activity.getEndTime().isDefined()) {
                            stats.departureTimes.add(activity.getEndTime().seconds());
                        }
                        
                    } else if (element instanceof Leg) {
                        Leg leg = (Leg) element;
                        stats.totalLegs++;
                        
                        // Count leg modes
                        String mode = leg.getMode();
                        stats.legModeCounts.merge(mode, 1, Integer::sum);
                    }
                }
            }
        }
        
        stats.minCoord = new Coord(minX, minY);
        stats.maxCoord = new Coord(maxX, maxY);
        
        return stats;
    }
    
    /**
     * Extract temporal distribution from population
     */
    public static Map<Integer, Integer> extractHourlyDepartureDistribution(Population population) {
        Map<Integer, Integer> hourlyDistribution = new HashMap<>();
        
        for (Person person : population.getPersons().values()) {
            Plan selectedPlan = person.getSelectedPlan();
            if (selectedPlan != null) {
                for (PlanElement element : selectedPlan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        if (activity.getEndTime().isDefined()) {
                            double timeSeconds = activity.getEndTime().seconds();
                            int hour = (int) (timeSeconds / 3600);
                            hourlyDistribution.merge(hour, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        
        return hourlyDistribution;
    }
    
    /**
     * Extract spatial distribution (coordinate ranges by activity type)
     */
    public static Map<String, List<Coord>> extractSpatialDistribution(Population population) {
        Map<String, List<Coord>> spatialDistribution = new HashMap<>();
        
        for (Person person : population.getPersons().values()) {
            Plan selectedPlan = person.getSelectedPlan();
            if (selectedPlan != null) {
                for (PlanElement element : selectedPlan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        String actType = activity.getType();
                        Coord coord = activity.getCoord();
                        
                        if (coord != null) {
                            spatialDistribution.computeIfAbsent(actType, k -> new ArrayList<>()).add(coord);
                        }
                    }
                }
            }
        }
        
        return spatialDistribution;
    }
    
    /**
     * Validate population structure and identify patterns
     */
    public static boolean validatePopulationStructure(Population population) {
        boolean isValid = true;
        Set<String> issues = new HashSet<>();
        
        for (Person person : population.getPersons().values()) {
            Plan selectedPlan = person.getSelectedPlan();
            if (selectedPlan == null) {
                issues.add("Person " + person.getId() + " has no selected plan");
                isValid = false;
                continue;
            }
            
            List<PlanElement> elements = selectedPlan.getPlanElements();
            
            // Check plan structure: should be Activity-Leg-Activity-Leg-Activity
            if (elements.size() != 5) {
                issues.add("Person " + person.getId() + " has " + elements.size() + " plan elements (expected 5)");
                isValid = false;
            }
            
            // Check element types
            for (int i = 0; i < elements.size(); i++) {
                PlanElement element = elements.get(i);
                if (i % 2 == 0) { // Even indices should be activities
                    if (!(element instanceof Activity)) {
                        issues.add("Person " + person.getId() + " has non-activity at position " + i);
                        isValid = false;
                    }
                } else { // Odd indices should be legs
                    if (!(element instanceof Leg)) {
                        issues.add("Person " + person.getId() + " has non-leg at position " + i);
                        isValid = false;
                    }
                }
            }
        }
        
        if (!issues.isEmpty()) {
            System.err.println("Population validation issues:");
            issues.forEach(System.err::println);
        }
        
        return isValid;
    }
    
    /**
     * Main method for command-line analysis
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: PopulationAnalyzer <population_file>");
            System.exit(1);
        }
        
        String populationFile = args[0];
        System.out.println("Analyzing population file: " + populationFile);
        
        try {
            PopulationStatistics stats = analyzePopulation(populationFile);
            stats.printSummary();
            
            // Load population for additional analysis
            Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            PopulationReader reader = new PopulationReader(scenario);
            reader.readFile(populationFile);
            Population population = scenario.getPopulation();
            
            System.out.println("\n=== Structure Validation ===");
            boolean isValid = validatePopulationStructure(population);
            System.out.println("Population structure valid: " + isValid);
            
            System.out.println("\n=== Hourly Departure Distribution ===");
            Map<Integer, Integer> hourlyDist = extractHourlyDepartureDistribution(population);
            hourlyDist.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println("Hour " + entry.getKey() + ": " + entry.getValue() + " departures"));
            
        } catch (Exception e) {
            System.err.println("Error analyzing population: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
} 