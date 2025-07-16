package org.matsim.maas.utils;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * T3.5 Validation Analyzer - Systematic validation of performance analysis framework
 * across representative uncertainty scenarios to ensure consistency and reliability.
 */
public class ValidationAnalyzer {
    
    public static class ValidationMetrics {
        public String scenarioType;      // base, S1, S2, S3, S4
        public String ruleType;          // rule1, rule2, rule3
        public int fleetSize;            // 20, 40
        public String experimentId;
        
        // Core DRT Performance Metrics
        public int totalRequests;
        public int servedRequests;
        public int rejectedRequests;
        public double serviceRate;
        public double avgWaitTime;
        public double avgTravelTime;
        public double avgDirectDistance;
        public double avgActualDistance;
        public double detourFactor;
        
        // Validation Flags
        public boolean metricsComplete;
        public boolean performanceRealistic;
        public String validationNotes;
    }
    
    public static class ValidationSummary {
        public int totalExperiments;
        public int validExperiments;
        public int invalidExperiments;
        public double validationRate;
        
        // Performance Consistency Analysis
        public Map<String, Double> serviceRateByScenario;
        public Map<String, Double> waitTimeByScenario;
        public Map<Integer, Double> serviceRateByFleet;
        public Map<String, Double> serviceRateByRule;
        
        // Validation Results
        public List<String> consistencyFindings;
        public List<String> anomalies;
        public List<String> recommendations;
        
        public String analysisTimestamp;
    }
    
    /**
     * Main validation analysis method
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: ValidationAnalyzer <validation_output_dir> <analysis_output_file>");
            System.out.println("Example: ValidationAnalyzer output/validation_t35 validation_analysis_t35.csv");
            return;
        }
        
        try {
            String validationDir = args[0];
            String analysisOutput = args[1];
            
            System.out.println("=== T3.5 VALIDATION ANALYSIS ===");
            System.out.println("Validation Directory: " + validationDir);
            System.out.println("Analysis Output: " + analysisOutput);
            
            // Analyze validation experiments
            ValidationSummary summary = analyzeValidationExperiments(validationDir);
            
            // Generate detailed analysis report
            generateValidationReport(summary, analysisOutput);
            
            // Print summary to console
            printValidationSummary(summary);
            
        } catch (Exception e) {
            System.err.println("Validation analysis error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Analyze all validation experiments for performance consistency
     */
    public static ValidationSummary analyzeValidationExperiments(String validationDir) throws IOException {
        ValidationSummary summary = new ValidationSummary();
        summary.analysisTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        summary.serviceRateByScenario = new HashMap<>();
        summary.waitTimeByScenario = new HashMap<>();
        summary.serviceRateByFleet = new HashMap<>();
        summary.serviceRateByRule = new HashMap<>();
        summary.consistencyFindings = new ArrayList<>();
        summary.anomalies = new ArrayList<>();
        summary.recommendations = new ArrayList<>();
        
        // Discover all validation experiment directories
        File validationDirFile = new File(validationDir);
        if (!validationDirFile.exists()) {
            throw new IOException("Validation directory not found: " + validationDir);
        }
        
        File[] expDirs = validationDirFile.listFiles((dir, name) -> name.startsWith("exp_"));
        if (expDirs == null || expDirs.length == 0) {
            throw new IOException("No validation experiments found in: " + validationDir);
        }
        
        List<ValidationMetrics> allMetrics = new ArrayList<>();
        
        System.out.println("\n=== PROCESSING VALIDATION EXPERIMENTS ===");
        for (File expDir : expDirs) {
            ValidationMetrics metrics = extractValidationMetrics(expDir);
            if (metrics != null) {
                allMetrics.add(metrics);
                summary.totalExperiments++;
                
                if (metrics.metricsComplete && metrics.performanceRealistic) {
                    summary.validExperiments++;
                } else {
                    summary.invalidExperiments++;
                    summary.anomalies.add(String.format("%s: %s", 
                        metrics.experimentId, metrics.validationNotes));
                }
            }
        }
        
        if (summary.totalExperiments > 0) {
            summary.validationRate = (double) summary.validExperiments / summary.totalExperiments;
        }
        
        // Analyze performance consistency across dimensions
        analyzePerformanceConsistency(allMetrics, summary);
        
        // Generate findings and recommendations
        generateValidationFindings(summary);
        
        return summary;
    }
    
    /**
     * Extract validation metrics from a single experiment
     */
    private static ValidationMetrics extractValidationMetrics(File expDir) {
        try {
            ValidationMetrics metrics = new ValidationMetrics();
            metrics.experimentId = expDir.getName();
            
            // Parse experiment identifier
            parseExperimentIdentifier(metrics.experimentId, metrics);
            
            // Find DRT customer stats file
            File statsFile = findDrtStatsFile(expDir);
            if (statsFile == null) {
                metrics.metricsComplete = false;
                metrics.validationNotes = "DRT stats file not found";
                return metrics;
            }
            
            // Extract DRT performance metrics
            extractDrtMetrics(statsFile, metrics);
            
            // Validate performance realism
            validatePerformanceRealism(metrics);
            
            System.out.printf("Processed: %s (Valid: %s)\n", 
                metrics.experimentId, metrics.metricsComplete && metrics.performanceRealistic);
            
            return metrics;
            
        } catch (Exception e) {
            System.err.println("Error processing experiment " + expDir.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Parse experiment identifier to extract scenario metadata
     */
    private static void parseExperimentIdentifier(String experimentId, ValidationMetrics metrics) {
        // Format: exp_{scenario}_{fleetSize}veh_{timestamp}
        // Example: exp_base_trip1.0_rule1_20veh_20250630_155805
        
        String[] parts = experimentId.split("_");
        if (parts.length >= 4) {
            // Extract scenario info from exp_base_trip1.0_rule1_20veh pattern
            String scenarioPart = String.join("_", Arrays.copyOfRange(parts, 1, parts.length - 2));
            
            if (scenarioPart.startsWith("base")) {
                metrics.scenarioType = "base";
            } else if (scenarioPart.startsWith("S1")) {
                metrics.scenarioType = "S1";
            } else if (scenarioPart.startsWith("S2")) {
                metrics.scenarioType = "S2";
            } else if (scenarioPart.startsWith("S3")) {
                metrics.scenarioType = "S3";
            } else if (scenarioPart.startsWith("S4")) {
                metrics.scenarioType = "S4";
            }
            
            // Extract rule
            if (scenarioPart.contains("rule1")) {
                metrics.ruleType = "rule1";
            } else if (scenarioPart.contains("rule2")) {
                metrics.ruleType = "rule2";
            } else if (scenarioPart.contains("rule3")) {
                metrics.ruleType = "rule3";
            }
            
            // Extract fleet size
            String fleetPart = parts[parts.length - 2];
            if (fleetPart.endsWith("veh")) {
                try {
                    metrics.fleetSize = Integer.parseInt(fleetPart.replace("veh", ""));
                } catch (NumberFormatException e) {
                    metrics.fleetSize = 0;
                }
            }
        }
    }
    
    /**
     * Extract DRT performance metrics from stats file
     */
    private static void extractDrtMetrics(File statsFile, ValidationMetrics metrics) throws IOException {
        List<String> lines = Files.readAllLines(statsFile.toPath());
        if (lines.size() < 2) {
            metrics.metricsComplete = false;
            metrics.validationNotes = "Insufficient data in stats file";
            return;
        }
        
        String[] headers = lines.get(0).split(";");
        String[] values = lines.get(1).split(";");
        
        Map<String, String> dataMap = new HashMap<>();
        for (int i = 0; i < Math.min(headers.length, values.length); i++) {
            dataMap.put(headers[i].trim(), values[i].trim());
        }
        
        try {
            metrics.servedRequests = parseInt(dataMap.get("rides"), 0);
            metrics.rejectedRequests = parseInt(dataMap.get("rejections"), 0);
            metrics.totalRequests = metrics.servedRequests + metrics.rejectedRequests;
            metrics.serviceRate = metrics.totalRequests > 0 ? 
                (double) metrics.servedRequests / metrics.totalRequests : 0.0;
            metrics.avgWaitTime = parseDouble(dataMap.get("wait_average"), 0.0);
            metrics.avgTravelTime = parseDouble(dataMap.get("inVehicleTravelTime_mean"), 0.0);
            metrics.avgDirectDistance = parseDouble(dataMap.get("directDistance_m_mean"), 0.0) / 1000.0; // Convert to km
            metrics.avgActualDistance = parseDouble(dataMap.get("distance_m_mean"), 0.0) / 1000.0; // Convert to km
            
            if (metrics.avgDirectDistance > 0) {
                metrics.detourFactor = metrics.avgActualDistance / metrics.avgDirectDistance;
            }
            
            metrics.metricsComplete = true;
            
        } catch (Exception e) {
            metrics.metricsComplete = false;
            metrics.validationNotes = "Error parsing metrics: " + e.getMessage();
        }
    }
    
    /**
     * Validate if performance metrics are realistic for DRT systems
     */
    private static void validatePerformanceRealism(ValidationMetrics metrics) {
        List<String> issues = new ArrayList<>();
        
        // Service rate validation (DRT typically 30-70%)
        if (metrics.serviceRate < 0.1 || metrics.serviceRate > 0.9) {
            issues.add(String.format("Service rate %.1f%% outside realistic range", metrics.serviceRate * 100));
        }
        
        // Wait time validation (typically 1-15 minutes)
        if (metrics.avgWaitTime < 60 || metrics.avgWaitTime > 900) {
            issues.add(String.format("Wait time %.1f min outside realistic range", metrics.avgWaitTime / 60.0));
        }
        
        // Detour factor validation (typically 1.1-2.0)
        if (metrics.detourFactor < 1.0 || metrics.detourFactor > 3.0) {
            issues.add(String.format("Detour factor %.2f outside realistic range", metrics.detourFactor));
        }
        
        // Distance validation (positive values)
        if (metrics.avgDirectDistance <= 0 || metrics.avgActualDistance <= 0) {
            issues.add("Invalid distance measurements");
        }
        
        metrics.performanceRealistic = issues.isEmpty();
        if (!issues.isEmpty()) {
            metrics.validationNotes = String.join("; ", issues);
        }
    }
    
    /**
     * Analyze performance consistency across uncertainty dimensions
     */
    private static void analyzePerformanceConsistency(List<ValidationMetrics> allMetrics, ValidationSummary summary) {
        List<ValidationMetrics> validMetrics = allMetrics.stream()
            .filter(m -> m.metricsComplete && m.performanceRealistic)
            .collect(Collectors.toList());
        
        if (validMetrics.isEmpty()) {
            summary.consistencyFindings.add("No valid metrics available for consistency analysis");
            return;
        }
        
        // Group by scenario type
        Map<String, List<ValidationMetrics>> byScenario = validMetrics.stream()
            .collect(Collectors.groupingBy(m -> m.scenarioType));
        
        for (Map.Entry<String, List<ValidationMetrics>> entry : byScenario.entrySet()) {
            double avgServiceRate = entry.getValue().stream()
                .mapToDouble(m -> m.serviceRate)
                .average().orElse(0.0);
            double avgWaitTime = entry.getValue().stream()
                .mapToDouble(m -> m.avgWaitTime)
                .average().orElse(0.0);
            
            summary.serviceRateByScenario.put(entry.getKey(), avgServiceRate);
            summary.waitTimeByScenario.put(entry.getKey(), avgWaitTime);
        }
        
        // Group by fleet size
        Map<Integer, List<ValidationMetrics>> byFleet = validMetrics.stream()
            .collect(Collectors.groupingBy(m -> m.fleetSize));
        
        for (Map.Entry<Integer, List<ValidationMetrics>> entry : byFleet.entrySet()) {
            double avgServiceRate = entry.getValue().stream()
                .mapToDouble(m -> m.serviceRate)
                .average().orElse(0.0);
            summary.serviceRateByFleet.put(entry.getKey(), avgServiceRate);
        }
        
        // Group by rule type
        Map<String, List<ValidationMetrics>> byRule = validMetrics.stream()
            .collect(Collectors.groupingBy(m -> m.ruleType));
        
        for (Map.Entry<String, List<ValidationMetrics>> entry : byRule.entrySet()) {
            double avgServiceRate = entry.getValue().stream()
                .mapToDouble(m -> m.serviceRate)
                .average().orElse(0.0);
            summary.serviceRateByRule.put(entry.getKey(), avgServiceRate);
        }
    }
    
    /**
     * Generate validation findings and recommendations
     */
    private static void generateValidationFindings(ValidationSummary summary) {
        // Validation rate assessment
        if (summary.validationRate >= 0.9) {
            summary.consistencyFindings.add("Excellent validation rate: " + String.format("%.1f%%", summary.validationRate * 100));
        } else if (summary.validationRate >= 0.7) {
            summary.consistencyFindings.add("Good validation rate: " + String.format("%.1f%%", summary.validationRate * 100));
        } else {
            summary.consistencyFindings.add("Low validation rate: " + String.format("%.1f%%", summary.validationRate * 100));
            summary.recommendations.add("Investigate failed experiments and improve simulation stability");
        }
        
        // Fleet size impact analysis
        if (summary.serviceRateByFleet.size() >= 2) {
            double[] rates = summary.serviceRateByFleet.values().stream().mapToDouble(d -> d).toArray();
            if (rates.length == 2 && rates[1] > rates[0]) {
                summary.consistencyFindings.add("Fleet size impact validated: Higher fleet size improves service rate");
            }
        }
        
        // Scenario diversity analysis
        if (summary.serviceRateByScenario.size() >= 3) {
            summary.consistencyFindings.add("Scenario diversity captured: " + summary.serviceRateByScenario.size() + " scenario types analyzed");
        }
        
        // Performance realism check
        double avgServiceRate = summary.serviceRateByScenario.values().stream()
            .mapToDouble(d -> d).average().orElse(0.0);
        
        if (avgServiceRate >= 0.3 && avgServiceRate <= 0.7) {
            summary.consistencyFindings.add("Realistic DRT performance: Average service rate " + String.format("%.1f%%", avgServiceRate * 100));
        }
    }
    
    /**
     * Generate detailed validation report
     */
    private static void generateValidationReport(ValidationSummary summary, String outputFile) throws IOException {
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("=== T3.5 VALIDATION ANALYSIS REPORT ===\n");
            writer.write("Analysis Timestamp: " + summary.analysisTimestamp + "\n\n");
            
            writer.write("VALIDATION SUMMARY:\n");
            writer.write("Total Experiments: " + summary.totalExperiments + "\n");
            writer.write("Valid Experiments: " + summary.validExperiments + "\n");
            writer.write("Invalid Experiments: " + summary.invalidExperiments + "\n");
            writer.write(String.format("Validation Rate: %.1f%%\n\n", summary.validationRate * 100));
            
            writer.write("PERFORMANCE BY SCENARIO:\n");
            for (Map.Entry<String, Double> entry : summary.serviceRateByScenario.entrySet()) {
                writer.write(String.format("  %s: %.1f%% service rate\n", entry.getKey(), entry.getValue() * 100));
            }
            
            writer.write("\nPERFORMANCE BY FLEET SIZE:\n");
            for (Map.Entry<Integer, Double> entry : summary.serviceRateByFleet.entrySet()) {
                writer.write(String.format("  %d vehicles: %.1f%% service rate\n", entry.getKey(), entry.getValue() * 100));
            }
            
            writer.write("\nPERFORMANCE BY RULE TYPE:\n");
            for (Map.Entry<String, Double> entry : summary.serviceRateByRule.entrySet()) {
                writer.write(String.format("  %s: %.1f%% service rate\n", entry.getKey(), entry.getValue() * 100));
            }
            
            writer.write("\nCONSISTENCY FINDINGS:\n");
            for (String finding : summary.consistencyFindings) {
                writer.write("  ✓ " + finding + "\n");
            }
            
            if (!summary.anomalies.isEmpty()) {
                writer.write("\nANOMALIES DETECTED:\n");
                for (String anomaly : summary.anomalies) {
                    writer.write("  ⚠ " + anomaly + "\n");
                }
            }
            
            if (!summary.recommendations.isEmpty()) {
                writer.write("\nRECOMMENDATIONS:\n");
                for (String rec : summary.recommendations) {
                    writer.write("  → " + rec + "\n");
                }
            }
        }
        
        System.out.println("Validation report generated: " + outputFile);
    }
    
    /**
     * Print validation summary to console
     */
    private static void printValidationSummary(ValidationSummary summary) {
        System.out.println("\n=== T3.5 VALIDATION RESULTS ===");
        System.out.printf("Validation Rate: %.1f%% (%d/%d experiments valid)\n", 
            summary.validationRate * 100, summary.validExperiments, summary.totalExperiments);
        
        System.out.println("\nKey Findings:");
        for (String finding : summary.consistencyFindings) {
            System.out.println("  ✓ " + finding);
        }
        
        if (!summary.anomalies.isEmpty()) {
            System.out.println("\nAnomalies: " + summary.anomalies.size());
        }
    }
    
    // Utility methods
    private static File findDrtStatsFile(File expDir) {
        File[] files = expDir.listFiles((dir, name) -> 
            name.contains("drt_customer_stats") && name.endsWith(".csv"));
        return (files != null && files.length > 0) ? files[0] : null;
    }
    
    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
} 