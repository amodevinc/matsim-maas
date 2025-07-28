package org.matsim.maas.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.population.Population;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Main module for generating MATSim population files from real-time demand data.
 * This module orchestrates the entire process:
 * 1. Reading real-time demand CSV files
 * 2. Applying uncertainty rules for demand prediction error modeling
 * 3. Generating MATSim population XML files for each scenario and uncertainty variant
 */
public class RealTimeDemandPopulationGenerator {
    
    private static final Logger log = LogManager.getLogger(RealTimeDemandPopulationGenerator.class);
    
    private static final String DEFAULT_DEMAND_DIR = "data/demands/hwaseong/real_time";
    private static final String DEFAULT_OUTPUT_DIR = "data/populations_from_real_time";
    
    private final DemandDataReader demandReader;
    private final UncertaintyProcessor uncertaintyProcessor;
    private final PopulationGenerator populationGenerator;
    
    public RealTimeDemandPopulationGenerator() {
        this.demandReader = new DemandDataReader();
        this.uncertaintyProcessor = new UncertaintyProcessor();
        this.populationGenerator = new PopulationGenerator();
    }
    
    /**
     * Generate population files for all scenarios in the real-time demand directory.
     * Creates both the base population files and uncertainty variants.
     * 
     * @param demandDirectoryPath Path to directory containing real-time demand CSV files
     * @param outputDirectoryPath Path to directory where population files will be written
     * @throws IOException If files cannot be read or written
     */
    public void generateAllPopulations(String demandDirectoryPath, String outputDirectoryPath) 
            throws IOException {
        
        Path demandDir = Paths.get(demandDirectoryPath);
        Path outputDir = Paths.get(outputDirectoryPath);
        
        // Create output directory if it doesn't exist
        Files.createDirectories(outputDir);
        
        log.info("Starting population generation from real-time demand data");
        log.info("Demand directory: {}", demandDir);
        log.info("Output directory: {}", outputDir);
        
        // Get list of scenarios from available files
        String[] scenarios = {"base", "S1", "S2", "S3"};
        
        for (String scenario : scenarios) {
            try {
                generatePopulationsForScenario(demandDir, outputDir, scenario);
            } catch (Exception e) {
                log.error("Failed to generate populations for scenario {}: {}", scenario, e.getMessage());
                // Continue with other scenarios
            }
        }
        
        log.info("Population generation completed");
    }
    
    /**
     * Generate population files for a specific scenario.
     * Creates the base population and all uncertainty variants.
     */
    private void generatePopulationsForScenario(Path demandDir, Path outputDir, String scenario) 
            throws IOException {
        
        log.info("Processing scenario: {}", scenario);
        
        // Read demand data for this scenario
        List<DemandRequest> baseDemand = demandReader.readDemandFilesByScenario(demandDir, scenario);
        
        if (baseDemand.isEmpty()) {
            log.warn("No demand data found for scenario: {}", scenario);
            return;
        }
        
        log.info("Loaded {} demand requests for scenario {}", baseDemand.size(), scenario);
        
        // Generate base population (no uncertainty)
        String basePopulationFilename = String.format("%s_real_time_population.xml.gz", scenario);
        Path baseOutputPath = outputDir.resolve(basePopulationFilename);
        
        populationGenerator.generateAndWritePopulation(baseDemand, scenario, baseOutputPath);
        log.info("Generated base population: {}", baseOutputPath);
        
        // Generate uncertainty variants
        generateUncertaintyVariants(baseDemand, outputDir, scenario);
    }
    
    /**
     * Generate all uncertainty variants for a scenario.
     */
    private void generateUncertaintyVariants(List<DemandRequest> baseDemand, 
                                           Path outputDir, String scenario) {
        
        log.info("Generating uncertainty variants for scenario: {}", scenario);
        
        // Get all standard uncertainty rules
        List<UncertaintyRule> uncertaintyRules = uncertaintyProcessor.generateStandardUncertaintyRules();
        
        for (UncertaintyRule rule : uncertaintyRules) {
            try {
                // Apply uncertainty rule to demand
                List<DemandRequest> modifiedDemand = uncertaintyProcessor.applyUncertaintyRule(baseDemand, rule);
                
                // Generate population filename
                String populationFilename = String.format("%s_%s_real_time_population.xml.gz", 
                                                        scenario, rule.getScenarioSuffix());
                Path outputPath = outputDir.resolve(populationFilename);
                
                // Generate and write population
                String populationScenarioName = String.format("%s_%s", scenario, rule.getScenarioSuffix());
                populationGenerator.generateAndWritePopulation(modifiedDemand, populationScenarioName, outputPath);
                
                log.info("Generated uncertainty variant: {} (rule: {})", populationFilename, rule);
                
            } catch (Exception e) {
                log.error("Failed to generate uncertainty variant for rule {}: {}", rule, e.getMessage());
                // Continue with other rules
            }
        }
    }
    
    /**
     * Generate population files for a single scenario and specific uncertainty rule.
     * Useful for testing or when only specific variants are needed.
     */
    public void generateSinglePopulation(String demandDirectoryPath, String outputDirectoryPath,
                                       String scenario, UncertaintyRule uncertaintyRule) throws IOException {
        
        Path demandDir = Paths.get(demandDirectoryPath);
        Path outputDir = Paths.get(outputDirectoryPath);
        Files.createDirectories(outputDir);
        
        // Read demand data
        List<DemandRequest> baseDemand = demandReader.readDemandFilesByScenario(demandDir, scenario);
        
        // Apply uncertainty rule
        List<DemandRequest> modifiedDemand = uncertaintyProcessor.applyUncertaintyRule(baseDemand, uncertaintyRule);
        
        // Generate filename and population
        String suffix = uncertaintyRule.isBaseline() ? "" : "_" + uncertaintyRule.getScenarioSuffix();
        String populationFilename = String.format("%s%s_real_time_population.xml.gz", scenario, suffix);
        Path outputPath = outputDir.resolve(populationFilename);
        
        String populationScenarioName = uncertaintyRule.isBaseline() ? 
            scenario : String.format("%s_%s", scenario, uncertaintyRule.getScenarioSuffix());
        
        populationGenerator.generateAndWritePopulation(modifiedDemand, populationScenarioName, outputPath);
        
        log.info("Generated single population: {}", outputPath);
    }
    
    /**
     * Generate population files using simplified plans (one-way trips only).
     * Useful for DRT simulations where return trips are not needed.
     */
    public void generateSimplifiedPopulations(String demandDirectoryPath, String outputDirectoryPath) 
            throws IOException {
        
        Path demandDir = Paths.get(demandDirectoryPath);
        Path outputDir = Paths.get(outputDirectoryPath).resolve("simplified");
        Files.createDirectories(outputDir);
        
        log.info("Generating simplified populations (one-way trips only)");
        
        String[] scenarios = {"base", "S1", "S2", "S3"};
        
        for (String scenario : scenarios) {
            try {
                List<DemandRequest> baseDemand = demandReader.readDemandFilesByScenario(demandDir, scenario);
                
                if (!baseDemand.isEmpty()) {
                    String filename = String.format("%s_real_time_simplified_population.xml.gz", scenario);
                    Path outputPath = outputDir.resolve(filename);
                    
                    Population population = populationGenerator.generateSimplifiedPopulation(baseDemand, scenario);
                    populationGenerator.writePopulation(population, outputPath);
                    
                    log.info("Generated simplified population: {}", filename);
                }
            } catch (Exception e) {
                log.error("Failed to generate simplified population for scenario {}: {}", scenario, e.getMessage());
            }
        }
    }
    
    /**
     * Print statistics about the generated populations.
     */
    public void printStatistics(String demandDirectoryPath) throws IOException {
        Path demandDir = Paths.get(demandDirectoryPath);
        
        log.info("=== DEMAND DATA STATISTICS ===");
        
        String[] scenarios = {"base", "S1", "S2", "S3"};
        for (String scenario : scenarios) {
            try {
                List<DemandRequest> demands = demandReader.readDemandFilesByScenario(demandDir, scenario);
                
                log.info("Scenario {}: {} requests", scenario, demands.size());
                
                // Print hourly distribution
                Map<Integer, Long> hourlyDistribution = demands.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                        DemandRequest::getHour, 
                        java.util.stream.Collectors.counting()));
                
                log.info("  Hourly distribution: {}", hourlyDistribution);
                
            } catch (Exception e) {
                log.warn("Could not read scenario {}: {}", scenario, e.getMessage());
            }
        }
    }
    
    public static void main(String[] args) {
        RealTimeDemandPopulationGenerator generator = new RealTimeDemandPopulationGenerator();
        
        try {
            if (args.length == 0) {
                // Generate all populations with default paths
                generator.generateAllPopulations(DEFAULT_DEMAND_DIR, DEFAULT_OUTPUT_DIR);
                
                // Also generate simplified versions
                generator.generateSimplifiedPopulations(DEFAULT_DEMAND_DIR, DEFAULT_OUTPUT_DIR);
                
                // Print statistics
                generator.printStatistics(DEFAULT_DEMAND_DIR);
                
            } else if (args.length == 2) {
                // Generate all populations with custom paths
                generator.generateAllPopulations(args[0], args[1]);
                
            } else if (args.length == 4) {
                // Generate single population: demandDir outputDir scenario rule
                String scenario = args[2];
                double multiplier = Double.parseDouble(args[3].split("_")[0]);
                int ruleNumber = Integer.parseInt(args[3].split("_")[1]);
                UncertaintyRule rule = new UncertaintyRule(multiplier, ruleNumber);
                
                generator.generateSinglePopulation(args[0], args[1], scenario, rule);
                
            } else {
                System.out.println("Usage:");
                System.out.println("  RealTimeDemandPopulationGenerator");
                System.out.println("    - Generate all populations with default paths");
                System.out.println("  RealTimeDemandPopulationGenerator <demandDir> <outputDir>");
                System.out.println("    - Generate all populations with custom paths");
                System.out.println("  RealTimeDemandPopulationGenerator <demandDir> <outputDir> <scenario> <multiplier_rule>");
                System.out.println("    - Generate single population (e.g., 'base 1.0_2')");
            }
            
        } catch (IOException e) {
            log.error("Population generation failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}