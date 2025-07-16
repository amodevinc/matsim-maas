package org.matsim.maas.utils;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive performance metrics collection system for DRT demand uncertainty analysis.
 * Extracts, aggregates, and analyzes key performance indicators from MATSim simulation outputs.
 */
public class PerformanceMetricsCollector {
    
    public static class DrtMetrics {
        public String scenarioId;
        public String scenario;        // base, S1, S2, S3, S4
        public String tripMultiplier;  // 0.5, 1.0, 1.5
        public String rule;            // rule1, rule2, rule3
        public int fleetSize;
        
        // Service Level Metrics
        public int totalRequests;
        public int servedRequests;
        public int rejectedRequests;
        public double rejectionRate;
        public double serviceRate;
        
        // Time Performance Metrics
        public double avgWaitTime;
        public double maxWaitTime;
        public double avgTravelTime;
        public double avgInVehicleTime;
        public double totalTravelTime;
        
        // Distance and Efficiency Metrics
        public double avgDirectDistance;
        public double avgDistance;
        public double detourFactor;
        public double totalDistance;
        public double totalEmptyDistance;
        public double occupancyRate;
        
        // Quality of Service Metrics
        public int tripsWithWaitTimeLessThan5Min;
        public int tripsWithWaitTimeLessThan10Min;
        public double serviceQuality5Min;
        public double serviceQuality10Min;
        
        // Operational Metrics
        public double avgOccupancy;
        public double fleetUtilization;
        public double avgRideSharing;
        public int peakHourRequests;
        public double peakRejectionRate;
        
        // Uncertainty Impact Metrics
        public double demandVariability;
        public double performanceVariability;
        public double robustnessScore;
        
        @Override
        public String toString() {
            return String.format("DrtMetrics{scenario=%s, trips=%d/%d (%.1f%% served), wait=%.1fmin, travel=%.1fmin}", 
                scenarioId, servedRequests, totalRequests, serviceRate*100, avgWaitTime/60.0, avgTravelTime/60.0);
        }
    }
    
    public static class MetricsReport {
        public List<DrtMetrics> allMetrics;
        public Map<String, DrtMetrics> baselineMetrics;
        public Map<String, List<DrtMetrics>> scenarioComparisons;
        public Map<String, Double> uncertaintyImpacts;
        public String reportTimestamp;
        public String summaryAnalysis;
        
        public MetricsReport() {
            this.allMetrics = new ArrayList<>();
            this.baselineMetrics = new HashMap<>();
            this.scenarioComparisons = new HashMap<>();
            this.uncertaintyImpacts = new HashMap<>();
            this.reportTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }
    
    /**
     * Main method for batch metrics collection from experimental results
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: PerformanceMetricsCollector <experiments_directory> <output_report_path>");
            System.out.println("Example: PerformanceMetricsCollector output/experiments output/performance_analysis.csv");
            return;
        }
        
        String experimentsDir = args[0];
        String outputReportPath = args[1];
        
        try {
            System.out.println("=== Performance Metrics Collection System ===");
            System.out.println("Experiments Directory: " + experimentsDir);
            System.out.println("Output Report: " + outputReportPath);
            
            MetricsReport report = collectMetricsFromExperiments(experimentsDir);
            generateComprehensiveReport(report, outputReportPath);
            
            System.out.println("\n=== Metrics Collection Complete ===");
            System.out.println("Total scenarios analyzed: " + report.allMetrics.size());
            System.out.println("Report generated: " + outputReportPath);
            
        } catch (Exception e) {
            System.err.println("Error during metrics collection: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Collect metrics from all experiment directories
     */
    public static MetricsReport collectMetricsFromExperiments(String experimentsDir) throws IOException {
        MetricsReport report = new MetricsReport();
        
        File expDir = new File(experimentsDir);
        if (!expDir.exists() || !expDir.isDirectory()) {
            throw new IOException("Experiments directory not found: " + experimentsDir);
        }
        
        System.out.println("Scanning experiment directories...");
        
        File[] scenarioDirs = expDir.listFiles(File::isDirectory);
        if (scenarioDirs == null) {
            throw new IOException("No experiment directories found in: " + experimentsDir);
        }
        
        int processedCount = 0;
        for (File scenarioDir : scenarioDirs) {
            try {
                DrtMetrics metrics = extractMetricsFromScenario(scenarioDir);
                if (metrics != null) {
                    report.allMetrics.add(metrics);
                    processedCount++;
                    
                    // Track baseline scenarios for comparison
                    if (metrics.scenario.equals("base")) {
                        String key = metrics.tripMultiplier + "_" + metrics.rule;
                        report.baselineMetrics.put(key, metrics);
                    }
                    
                    System.out.printf("Processed [%d]: %s\n", processedCount, metrics);
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to process " + scenarioDir.getName() + ": " + e.getMessage());
            }
        }
        
        // Perform comparative analysis
        performComparativeAnalysis(report);
        
        return report;
    }
    
    /**
     * Extract DRT metrics from a single experiment directory
     */
    public static DrtMetrics extractMetricsFromScenario(File scenarioDir) throws IOException {
        // Parse scenario information from directory name
        String scenarioName = scenarioDir.getName();
        DrtMetrics metrics = parseScenarioIdentifiers(scenarioName);
        
        if (metrics == null) {
            System.out.println("Skipping non-scenario directory: " + scenarioName);
            return null;
        }
        
        // Find DRT customer stats file
        File customerStatsFile = findDrtCustomerStatsFile(scenarioDir);
        if (customerStatsFile == null) {
            throw new IOException("DRT customer stats file not found in: " + scenarioDir.getPath());
        }
        
        // Parse DRT metrics from customer stats
        parseDrtCustomerStats(customerStatsFile, metrics);
        
        // Extract additional metrics from other output files
        extractAdditionalMetrics(scenarioDir, metrics);
        
        // Calculate derived metrics
        calculateDerivedMetrics(metrics);
        
        return metrics;
    }
    
    /**
     * Parse scenario identifiers from directory name
     */
    private static DrtMetrics parseScenarioIdentifiers(String scenarioName) {
        // Expected formats: 
        // - base_trip1.0_rule1_20250630_153842 
        // - S1_trip1.5_rule2_vehicles20_20250630_154022
        // - exp_base_trip1.0_rule1_10veh_20250630_154114 (experimental framework format)
        
        DrtMetrics metrics = new DrtMetrics();
        metrics.scenarioId = scenarioName;
        
        // Handle experimental framework prefix
        String cleanName = scenarioName;
        if (scenarioName.startsWith("exp_")) {
            cleanName = scenarioName.substring(4); // Remove "exp_" prefix
        }
        
        // Extract scenario (base, S1, S2, S3, S4)
        if (cleanName.startsWith("base_")) {
            metrics.scenario = "base";
        } else if (cleanName.startsWith("S1_")) {
            metrics.scenario = "S1";
        } else if (cleanName.startsWith("S2_")) {
            metrics.scenario = "S2";
        } else if (cleanName.startsWith("S3_")) {
            metrics.scenario = "S3";
        } else if (cleanName.startsWith("S4_")) {
            metrics.scenario = "S4";
        } else {
            return null; // Not a scenario directory
        }
        
        // Extract trip multiplier
        if (cleanName.contains("trip0.5")) {
            metrics.tripMultiplier = "0.5";
        } else if (cleanName.contains("trip1.0")) {
            metrics.tripMultiplier = "1.0";
        } else if (cleanName.contains("trip1.5")) {
            metrics.tripMultiplier = "1.5";
        } else {
            metrics.tripMultiplier = "unknown";
        }
        
        // Extract rule
        if (cleanName.contains("rule1")) {
            metrics.rule = "rule1";
        } else if (cleanName.contains("rule2")) {
            metrics.rule = "rule2";
        } else if (cleanName.contains("rule3")) {
            metrics.rule = "rule3";
        } else {
            metrics.rule = "unknown";
        }
        
        // Extract fleet size (default to 8 if not specified)
        // Handle both formats: "vehicles8" and "8veh"
        if (scenarioName.contains("vehicles4") || scenarioName.contains("4veh")) {
            metrics.fleetSize = 4;
        } else if (scenarioName.contains("vehicles8") || scenarioName.contains("8veh")) {
            metrics.fleetSize = 8;
        } else if (scenarioName.contains("vehicles12") || scenarioName.contains("12veh")) {
            metrics.fleetSize = 12;
        } else {
            metrics.fleetSize = 8; // Default fleet size from config
        }
        
        return metrics;
    }
    
    /**
     * Find DRT customer stats file in experiment directory
     */
    private static File findDrtCustomerStatsFile(File scenarioDir) {
        File[] files = scenarioDir.listFiles((dir, name) -> 
            name.contains("drt_customer_stats") && name.endsWith(".csv"));
        
        return (files != null && files.length > 0) ? files[0] : null;
    }
    
    /**
     * Parse DRT customer statistics from CSV file
     */
    private static void parseDrtCustomerStats(File customerStatsFile, DrtMetrics metrics) throws IOException {
        List<String> lines = Files.readAllLines(customerStatsFile.toPath());
        
        if (lines.isEmpty()) {
            throw new IOException("Empty customer stats file: " + customerStatsFile.getPath());
        }
        
        // Parse header and data rows
        String[] headers = lines.get(0).split(";");
        Map<String, Integer> columnMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            columnMap.put(headers[i].trim(), i);
        }
        
        // Process data rows (skip header)
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] values = lines.get(i).split(";");
            Map<String, String> row = new HashMap<>();
            for (int j = 0; j < Math.min(values.length, headers.length); j++) {
                row.put(headers[j].trim(), values[j].trim());
            }
            rows.add(row);
        }
        
        if (rows.isEmpty()) {
            throw new IOException("No data rows in customer stats file");
        }
        
        // Extract metrics from the aggregated row (usually the last row)
        Map<String, String> dataRow = rows.get(rows.size() - 1);
        
        try {
            // Parse served requests and rejections
            metrics.servedRequests = parseIntValue(dataRow, "rides");
            metrics.rejectedRequests = parseIntValue(dataRow, "rejections");
            metrics.totalRequests = metrics.servedRequests + metrics.rejectedRequests;
            
            // Calculate rates
            if (metrics.totalRequests > 0) {
                metrics.rejectionRate = (double) metrics.rejectedRequests / metrics.totalRequests;
                metrics.serviceRate = (double) metrics.servedRequests / metrics.totalRequests;
            } else {
                metrics.rejectionRate = 0.0;
                metrics.serviceRate = 0.0;
            }
            
            // Parse time metrics (convert from seconds to maintain consistency)
            metrics.avgWaitTime = parseDoubleValue(dataRow, "wait_average");
            metrics.avgInVehicleTime = parseDoubleValue(dataRow, "inVehicleTravelTime_mean");
            metrics.avgTravelTime = parseDoubleValue(dataRow, "totalTravelTime_mean");
            
            // Parse distance metrics (convert from meters to kilometers for readability)
            metrics.avgDirectDistance = parseDoubleValue(dataRow, "directDistance_m_mean") / 1000.0;
            metrics.avgDistance = parseDoubleValue(dataRow, "distance_m_mean") / 1000.0;
            
            // Calculate additional metrics
            if (metrics.avgDirectDistance > 0) {
                metrics.detourFactor = metrics.avgDistance / metrics.avgDirectDistance;
            }
            
        } catch (Exception e) {
            throw new IOException("Error parsing customer stats data: " + e.getMessage());
        }
    }
    
    private static int parseIntValue(Map<String, String> row, String key) {
        String value = row.get(key);
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private static double parseDoubleValue(Map<String, String> row, String key) {
        String value = row.get(key);
        if (value == null || value.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    /**
     * Extract additional metrics from other simulation output files
     */
    private static void extractAdditionalMetrics(File scenarioDir, DrtMetrics metrics) {
        // Add vehicle stats, link stats, and other performance indicators
        // For now, calculate derived metrics from existing data
        
        // Calculate service quality metrics
        if (metrics.avgWaitTime > 0) {
            // Estimate service quality (simplified approximation)
            metrics.serviceQuality5Min = Math.max(0, 1.0 - (metrics.avgWaitTime - 300) / 300);
            metrics.serviceQuality10Min = Math.max(0, 1.0 - (metrics.avgWaitTime - 600) / 600);
        }
        
        // Estimate fleet utilization (simplified)
        if (metrics.fleetSize > 0 && metrics.servedRequests > 0) {
            metrics.fleetUtilization = Math.min(1.0, (double) metrics.servedRequests / (metrics.fleetSize * 10));
        }
    }
    
    /**
     * Calculate derived performance metrics
     */
    private static void calculateDerivedMetrics(DrtMetrics metrics) {
        // Calculate total metrics
        metrics.totalTravelTime = metrics.avgTravelTime * metrics.servedRequests;
        metrics.totalDistance = metrics.avgDistance * metrics.servedRequests;
        
        // Calculate performance scores
        metrics.robustnessScore = calculateRobustnessScore(metrics);
        metrics.demandVariability = calculateDemandVariability(metrics);
        metrics.performanceVariability = calculatePerformanceVariability(metrics);
    }
    
    private static double calculateRobustnessScore(DrtMetrics metrics) {
        // Robustness score based on service rate, wait time, and rejection rate
        double serviceScore = metrics.serviceRate;
        double waitScore = Math.max(0, 1.0 - metrics.avgWaitTime / 1200); // Normalize to 20 min max
        double rejectionScore = 1.0 - metrics.rejectionRate;
        
        return (serviceScore + waitScore + rejectionScore) / 3.0;
    }
    
    private static double calculateDemandVariability(DrtMetrics metrics) {
        // Estimate demand variability based on scenario and trip multiplier
        double baseVariability = 0.1;
        
        if (metrics.tripMultiplier.equals("0.5")) {
            baseVariability += 0.2; // Under-demand creates variability
        } else if (metrics.tripMultiplier.equals("1.5")) {
            baseVariability += 0.3; // Over-demand creates more variability
        }
        
        // Uncertainty rules add variability
        if (metrics.rule.equals("rule2") || metrics.rule.equals("rule3")) {
            baseVariability += 0.1;
        }
        
        return Math.min(1.0, baseVariability);
    }
    
    private static double calculatePerformanceVariability(DrtMetrics metrics) {
        // Performance variability based on rejection rate and wait time variance
        return Math.min(1.0, metrics.rejectionRate + (metrics.avgWaitTime / 1800.0));
    }
    
    /**
     * Perform comparative analysis between scenarios
     */
    private static void performComparativeAnalysis(MetricsReport report) {
        System.out.println("Performing comparative analysis...");
        
        // Group scenarios by type
        Map<String, List<DrtMetrics>> scenarioGroups = report.allMetrics.stream()
            .collect(Collectors.groupingBy(m -> m.scenario));
        
        report.scenarioComparisons = scenarioGroups;
        
        // Calculate uncertainty impacts
        for (DrtMetrics metrics : report.allMetrics) {
            if (!metrics.scenario.equals("base")) {
                String baseKey = metrics.tripMultiplier + "_" + metrics.rule;
                DrtMetrics baseline = report.baselineMetrics.get(baseKey);
                
                if (baseline != null) {
                    double impact = calculateUncertaintyImpact(baseline, metrics);
                    report.uncertaintyImpacts.put(metrics.scenarioId, impact);
                }
            }
        }
    }
    
    private static double calculateUncertaintyImpact(DrtMetrics baseline, DrtMetrics scenario) {
        // Calculate weighted impact score
        double serviceImpact = Math.abs(baseline.serviceRate - scenario.serviceRate);
        double waitImpact = Math.abs(baseline.avgWaitTime - scenario.avgWaitTime) / 600.0; // Normalize by 10 min
        double robustnessImpact = Math.abs(baseline.robustnessScore - scenario.robustnessScore);
        
        return (serviceImpact + waitImpact + robustnessImpact) / 3.0;
    }
    
    /**
     * Generate comprehensive performance report
     */
    public static void generateComprehensiveReport(MetricsReport report, String outputPath) throws IOException {
        System.out.println("Generating comprehensive performance report...");
        
        // Create parent directories if needed
        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();
        
        try (FileWriter writer = new FileWriter(outputFile)) {
            // Write CSV header
            writer.write("ScenarioID,Scenario,TripMultiplier,Rule,FleetSize,");
            writer.write("TotalRequests,ServedRequests,RejectedRequests,RejectionRate,ServiceRate,");
            writer.write("AvgWaitTime,AvgTravelTime,AvgInVehicleTime,");
            writer.write("AvgDirectDistance,AvgDistance,DetourFactor,");
            writer.write("ServiceQuality5Min,ServiceQuality10Min,FleetUtilization,");
            writer.write("RobustnessScore,DemandVariability,PerformanceVariability,UncertaintyImpact\n");
            
            // Write data rows
            for (DrtMetrics metrics : report.allMetrics) {
                Double uncertaintyImpact = report.uncertaintyImpacts.get(metrics.scenarioId);
                
                writer.write(String.format("%s,%s,%s,%s,%d,",
                    metrics.scenarioId, metrics.scenario, metrics.tripMultiplier, metrics.rule, metrics.fleetSize));
                writer.write(String.format("%d,%d,%d,%.4f,%.4f,",
                    metrics.totalRequests, metrics.servedRequests, metrics.rejectedRequests, 
                    metrics.rejectionRate, metrics.serviceRate));
                writer.write(String.format("%.2f,%.2f,%.2f,",
                    metrics.avgWaitTime, metrics.avgTravelTime, metrics.avgInVehicleTime));
                writer.write(String.format("%.2f,%.2f,%.4f,",
                    metrics.avgDirectDistance, metrics.avgDistance, metrics.detourFactor));
                writer.write(String.format("%.4f,%.4f,%.4f,",
                    metrics.serviceQuality5Min, metrics.serviceQuality10Min, metrics.fleetUtilization));
                writer.write(String.format("%.4f,%.4f,%.4f,%.4f\n",
                    metrics.robustnessScore, metrics.demandVariability, metrics.performanceVariability,
                    uncertaintyImpact != null ? uncertaintyImpact : 0.0));
            }
        }
        
        // Generate summary analysis
        generateSummaryAnalysis(report, outputPath.replace(".csv", "_summary.txt"));
    }
    
    /**
     * Generate summary analysis report
     */
    private static void generateSummaryAnalysis(MetricsReport report, String summaryPath) throws IOException {
        try (FileWriter writer = new FileWriter(summaryPath)) {
            writer.write("=== DRT DEMAND UNCERTAINTY PERFORMANCE ANALYSIS ===\n");
            writer.write("Report Generated: " + report.reportTimestamp + "\n");
            writer.write("Total Scenarios Analyzed: " + report.allMetrics.size() + "\n\n");
            
            // Scenario breakdown
            Map<String, Long> scenarioCounts = report.allMetrics.stream()
                .collect(Collectors.groupingBy(m -> m.scenario, Collectors.counting()));
            
            writer.write("Scenario Distribution:\n");
            for (Map.Entry<String, Long> entry : scenarioCounts.entrySet()) {
                writer.write(String.format("  %s: %d experiments\n", entry.getKey(), entry.getValue()));
            }
            
            // Performance summary by scenario
            writer.write("\nPerformance Summary by Scenario:\n");
            for (String scenario : Arrays.asList("base", "S1", "S2", "S3", "S4")) {
                List<DrtMetrics> scenarioMetrics = report.allMetrics.stream()
                    .filter(m -> m.scenario.equals(scenario))
                    .collect(Collectors.toList());
                
                if (!scenarioMetrics.isEmpty()) {
                    double avgServiceRate = scenarioMetrics.stream()
                        .mapToDouble(m -> m.serviceRate).average().orElse(0.0);
                    double avgWaitTime = scenarioMetrics.stream()
                        .mapToDouble(m -> m.avgWaitTime).average().orElse(0.0);
                    double avgRobustness = scenarioMetrics.stream()
                        .mapToDouble(m -> m.robustnessScore).average().orElse(0.0);
                    
                    writer.write(String.format("  %s: %.1f%% service rate, %.1f min wait time, %.3f robustness\n",
                        scenario, avgServiceRate*100, avgWaitTime/60.0, avgRobustness));
                }
            }
            
            // Uncertainty impact analysis
            writer.write("\nUncertainty Impact Analysis:\n");
            if (!report.uncertaintyImpacts.isEmpty()) {
                double avgImpact = report.uncertaintyImpacts.values().stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0.0);
                double maxImpact = report.uncertaintyImpacts.values().stream()
                    .mapToDouble(Double::doubleValue).max().orElse(0.0);
                
                writer.write(String.format("  Average uncertainty impact: %.3f\n", avgImpact));
                writer.write(String.format("  Maximum uncertainty impact: %.3f\n", maxImpact));
                
                // Find most impacted scenario
                String mostImpacted = report.uncertaintyImpacts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("unknown");
                
                writer.write(String.format("  Most impacted scenario: %s (%.3f impact)\n", 
                    mostImpacted, report.uncertaintyImpacts.get(mostImpacted)));
            }
            
            writer.write("\n=== END OF ANALYSIS ===\n");
        }
    }
} 