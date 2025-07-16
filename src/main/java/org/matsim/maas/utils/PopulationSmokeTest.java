package org.matsim.maas.utils;

import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Smoke test utility to verify population file generation and validate trip distributions
 */
public class PopulationSmokeTest {

    public static void main(String[] args) {
        System.out.println("PopulationSmokeTest: Starting validation of all population files");
        
        String populationDir = "data/populations/";
        String[] scenarios = {"base", "S1", "S2", "S3", "S4"};
        String[] multipliers = {"0.5", "1.0", "1.5"};
        String[] rules = {"1", "2", "3"};
        
        Map<String, Integer> populationCounts = new HashMap<>();
        int totalFiles = 0;
        int validFiles = 0;
        
        for (String scenario : scenarios) {
            for (String multiplier : multipliers) {
                for (String rule : rules) {
                    String filename = String.format("%s_trip%s_rule%s_population.xml.gz", 
                                                   scenario, multiplier, rule);
                    String filepath = populationDir + filename;
                    
                    totalFiles++;
                    
                    try {
                        // Load and validate population file
                        var config = ConfigUtils.createConfig();
                        var scenario_obj = ScenarioUtils.createScenario(config);
                        new PopulationReader(scenario_obj).readFile(filepath);
                        Population population = scenario_obj.getPopulation();
                        
                        int personCount = population.getPersons().size();
                        populationCounts.put(filename, personCount);
                        
                        System.out.printf("✓ %s: %d persons%n", filename, personCount);
                        validFiles++;
                        
                        // Basic validation
                        if (personCount == 0) {
                            System.err.printf("⚠ WARNING: %s has 0 persons%n", filename);
                        }
                        
                        // Verify trip structure for first person
                        if (!population.getPersons().isEmpty()) {
                            var firstPerson = population.getPersons().values().iterator().next();
                            var plan = firstPerson.getSelectedPlan();
                            if (plan == null || plan.getPlanElements().isEmpty()) {
                                System.err.printf("⚠ WARNING: %s has invalid plan structure%n", filename);
                            }
                        }
                        
                    } catch (Exception e) {
                        System.err.printf("✗ ERROR loading %s: %s%n", filename, e.getMessage());
                    }
                }
            }
        }
        
        System.out.println("\n=== VALIDATION SUMMARY ===");
        System.out.printf("Total files expected: %d%n", totalFiles);
        System.out.printf("Valid files loaded: %d%n", validFiles);
        System.out.printf("Success rate: %.1f%%%n", (validFiles * 100.0 / totalFiles));
        
        // Analyze population size patterns
        System.out.println("\n=== POPULATION SIZE ANALYSIS ===");
        for (String scenario : scenarios) {
            System.out.printf("\n%s scenario:%n", scenario);
            for (String multiplier : multipliers) {
                System.out.printf("  %sx demand: ", multiplier);
                for (String rule : rules) {
                    String filename = String.format("%s_trip%s_rule%s_population.xml.gz", 
                                                   scenario, multiplier, rule);
                    Integer count = populationCounts.get(filename);
                    if (count != null) {
                        System.out.printf("%d ", count);
                    } else {
                        System.out.print("ERR ");
                    }
                }
                System.out.println();
            }
        }
        
        // Validate expected patterns
        System.out.println("\n=== PATTERN VALIDATION ===");
        validateMultiplierPattern(populationCounts, scenarios, rules);
        validateFileExistence(populationDir);
        
        System.out.println("\nPopulationSmokeTest: Validation complete");
    }
    
    private static void validateMultiplierPattern(Map<String, Integer> counts, String[] scenarios, String[] rules) {
        for (String scenario : scenarios) {
            for (String rule : rules) {
                String file05 = String.format("%s_trip0.5_rule%s_population.xml.gz", scenario, rule);
                String file10 = String.format("%s_trip1.0_rule%s_population.xml.gz", scenario, rule);
                String file15 = String.format("%s_trip1.5_rule%s_population.xml.gz", scenario, rule);
                
                Integer count05 = counts.get(file05);
                Integer count10 = counts.get(file10);
                Integer count15 = counts.get(file15);
                
                if (count05 != null && count10 != null && count15 != null) {
                    // Check if multiplier pattern is reasonable (0.5x < 1.0x < 1.5x)
                    if (count05 < count10 && count10 < count15) {
                        System.out.printf("✓ %s rule%s: Multiplier pattern valid (%d < %d < %d)%n", 
                                         scenario, rule, count05, count10, count15);
                    } else {
                        System.err.printf("⚠ WARNING: %s rule%s: Unexpected multiplier pattern (%d, %d, %d)%n", 
                                         scenario, rule, count05, count10, count15);
                    }
                }
            }
        }
    }
    
    private static void validateFileExistence(String populationDir) {
        File dir = new File(populationDir);
        if (!dir.exists()) {
            System.err.println("✗ ERROR: Population directory does not exist");
            return;
        }
        
        File[] files = dir.listFiles((d, name) -> name.endsWith("_population.xml.gz"));
        if (files != null) {
            System.out.printf("✓ Found %d population files in directory%n", files.length);
            
            // Check for any unexpected files
            for (File file : files) {
                String name = file.getName();
                if (!name.matches("(base|S[1-4])_trip(0\\.5|1\\.0|1\\.5)_rule[1-3]_population\\.xml\\.gz")) {
                    System.err.printf("⚠ WARNING: Unexpected file pattern: %s%n", name);
                }
            }
        }
    }
}