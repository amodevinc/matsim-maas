package org.matsim.maas.utils;

import org.matsim.api.core.v01.population.*;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.maas.utils.RulesDataParser.DemandPattern;
import org.matsim.maas.utils.RulesDataParser.RealTimeTrip;

import java.io.File;
import java.util.List;

public class PopulationXMLGenerator {
    
    private final long seed;
    private final PopulationScaler scaler;
    private final TemporalPatternAdjuster temporalAdjuster;
    private final VirtualStopNetworkMapper spatialMapper;
    private final UncertaintyPerturbator perturbator;
    
    public PopulationXMLGenerator(long seed) {
        this.seed = seed;
        this.scaler = new PopulationScaler(seed);
        this.temporalAdjuster = new TemporalPatternAdjuster(seed + 1);
        this.spatialMapper = new VirtualStopNetworkMapper(seed + 2);
        this.perturbator = new UncertaintyPerturbator(seed + 3);
    }
    
    public static class PopulationGenerationResult {
        public Population finalPopulation;
        public String outputFilePath;
        public DemandPattern demandPattern;
        public PopulationScaler.ScaledPopulation scalingResult;
        public TemporalPatternAdjuster.TemporalAdjustmentResult temporalResult;
        public VirtualStopNetworkMapper.SpatialReallocationResult spatialResult;
        public UncertaintyPerturbator.PerturbationResult perturbationResult;
        public long generationTimeMs;
        
        public PopulationGenerationResult(DemandPattern pattern, String outputPath) {
            this.demandPattern = pattern;
            this.outputFilePath = outputPath;
        }
        
        public void printSummary() {
            System.out.println("=== Population Generation Summary ===");
            System.out.println("Scenario: " + demandPattern.scenario);
            System.out.println("Trip Multiplier: " + demandPattern.tripMultiplier);
            System.out.println("Rule: " + demandPattern.rule);
            System.out.println("Output File: " + outputFilePath);
            System.out.println("Generation Time: " + generationTimeMs + "ms");
            System.out.println("Final Population Size: " + finalPopulation.getPersons().size());
        }
    }
    
    public PopulationGenerationResult generatePopulationXML(Population basePopulation, 
                                                           DemandPattern demandPattern, 
                                                           List<RealTimeTrip> realTimeTrips,
                                                           String outputFilePath) {
        
        long startTime = System.currentTimeMillis();
        
        PopulationGenerationResult result = new PopulationGenerationResult(demandPattern, outputFilePath);
        
        try {
            System.out.println("Starting population generation for " + demandPattern.scenario + 
                              "_trip" + demandPattern.tripMultiplier + "_rule" + demandPattern.rule);
            
            // Step 1: Population Scaling
            System.out.println("Step 1: Scaling population (multiplier: " + demandPattern.tripMultiplier + ")");
            result.scalingResult = scaler.scalePopulation(basePopulation, demandPattern.tripMultiplier, 
                                                        demandPattern.scenario, demandPattern.rule);
            
            Population currentPopulation = result.scalingResult.population;
            System.out.println("  Scaled to " + currentPopulation.getPersons().size() + " persons");
            
            // Step 2: Temporal Pattern Adjustment
            System.out.println("Step 2: Adjusting temporal patterns");
            result.temporalResult = temporalAdjuster.adjustTemporalPatterns(currentPopulation, demandPattern, realTimeTrips);
            
            currentPopulation = result.temporalResult.adjustedPopulation;
            System.out.println("  Applied " + result.temporalResult.adjustmentMethod + 
                              " (fitness: " + String.format("%.3f", result.temporalResult.temporalFitness) + ")");
            
            // Step 3: Spatial Demand Reallocation
            System.out.println("Step 3: Reallocating spatial demand");
            result.spatialResult = spatialMapper.reallocateActivities(currentPopulation, demandPattern, realTimeTrips);
            
            currentPopulation = result.spatialResult.reallocatedPopulation;
            System.out.println("  Applied " + result.spatialResult.reallocationMethod + 
                              " (fitness: " + String.format("%.3f", result.spatialResult.spatialFitness) + 
                              ", zones: " + result.spatialResult.totalZonesUsed + ")");
            
            // Step 4: Uncertainty Perturbation
            System.out.println("Step 4: Applying uncertainty perturbations");
            result.perturbationResult = perturbator.applyUncertaintyPerturbations(currentPopulation, demandPattern);
            
            currentPopulation = result.perturbationResult.perturbedPopulation;
            System.out.println("  Applied " + result.perturbationResult.perturbationRule + 
                              " (intensity: " + String.format("%.3f", result.perturbationResult.perturbationIntensity) + ")");
            
            // Step 5: XML Generation and Compression
            System.out.println("Step 5: Writing XML file");
            result.finalPopulation = currentPopulation;
            
            File outputFile = new File(outputFilePath);
            File outputDir = outputFile.getParentFile();
            if (outputDir != null && !outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            PopulationWriter writer = new PopulationWriter(currentPopulation);
            writer.write(outputFilePath);
            
            System.out.println("  Written to: " + outputFilePath);
            
            if (outputFile.exists()) {
                long fileSize = outputFile.length();
                System.out.println("  File size: " + formatFileSize(fileSize));
            }
            
            result.generationTimeMs = System.currentTimeMillis() - startTime;
            System.out.println("Population generation completed in " + result.generationTimeMs + "ms");
            
        } catch (Exception e) {
            System.err.println("Error during population generation: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Population generation failed", e);
        }
        
        return result;
    }
    
    public void generateAllPopulationXMLs(Population basePopulation, 
                                        RulesDataParser.RulesDataSet rulesDataSet,
                                        String outputDirectory) {
        
        System.out.println("=== Batch Population Generation ===");
        System.out.println("Generating " + rulesDataSet.demandPatterns.size() + " population files");
        
        int completed = 0;
        int failed = 0;
        
        for (DemandPattern demandPattern : rulesDataSet.demandPatterns.values()) {
            try {
                List<RealTimeTrip> realTimeTrips = rulesDataSet.getRealTimeTrips(demandPattern.scenario);
                
                String outputFilePath = generateOutputFilePath(outputDirectory, demandPattern);
                
                PopulationGenerationResult result = generatePopulationXML(basePopulation, demandPattern, 
                                                                        realTimeTrips, outputFilePath);
                
                System.out.println("✅ Completed: " + demandPattern.getFilePattern());
                completed++;
                
            } catch (Exception e) {
                System.err.println("❌ Failed: " + demandPattern.getFilePattern() + " - " + e.getMessage());
                failed++;
            }
        }
        
        System.out.println("\n=== Batch Generation Summary ===");
        System.out.println("Completed: " + completed);
        System.out.println("Failed: " + failed);
        System.out.println("Total: " + (completed + failed));
        
        if (failed == 0) {
            System.out.println("🎉 All population files generated successfully!");
        } else {
            System.out.println("⚠️  " + failed + " files failed to generate");
        }
    }
    
    private String generateOutputFilePath(String outputDirectory, DemandPattern demandPattern) {
        String fileName = demandPattern.scenario + "_trip" + demandPattern.tripMultiplier + 
                         "_rule" + demandPattern.rule + "_population.xml.gz";
        return outputDirectory + "/" + fileName;
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: PopulationXMLGenerator <base_population_file> <rules_dir> <real_time_dir> [output_dir]");
            System.err.println("  base_population_file: Path to base population XML file");
            System.err.println("  rules_dir: Directory containing rules CSV files");
            System.err.println("  real_time_dir: Directory containing real-time CSV files");
            System.err.println("  output_dir: Output directory for generated population files (default: output/populations)");
            System.exit(1);
        }
        
        String basePopulationFile = args[0];
        String rulesDir = args[1];
        String realTimeDir = args[2];
        String outputDir = args.length > 3 ? args[3] : "output/populations";
        
        try {
            System.out.println("=== MATSim Population Generator for Demand Uncertainty ===");
            System.out.println("Base Population: " + basePopulationFile);
            System.out.println("Rules Directory: " + rulesDir);
            System.out.println("Real-time Directory: " + realTimeDir);
            System.out.println("Output Directory: " + outputDir);
            
            System.out.println("\nLoading base population...");
            org.matsim.api.core.v01.Scenario matsimScenario = 
                org.matsim.core.scenario.ScenarioUtils.createScenario(org.matsim.core.config.ConfigUtils.createConfig());
            org.matsim.core.population.io.PopulationReader reader = 
                new org.matsim.core.population.io.PopulationReader(matsimScenario);
            reader.readFile(basePopulationFile);
            Population basePopulation = matsimScenario.getPopulation();
            
            System.out.println("Loaded " + basePopulation.getPersons().size() + " persons");
            
            System.out.println("\nLoading rules data...");
            RulesDataParser.RulesDataSet rulesDataSet = RulesDataParser.loadAllRulesData(rulesDir, realTimeDir);
            rulesDataSet.printSummary();
            
            System.out.println("\nStarting batch population generation...");
            PopulationXMLGenerator generator = new PopulationXMLGenerator(42);
            generator.generateAllPopulationXMLs(basePopulation, rulesDataSet, outputDir);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
