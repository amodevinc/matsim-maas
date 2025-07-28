package org.matsim.maas.archived;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.GeotoolsTransformation;
import org.matsim.core.population.io.PopulationWriter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Converts Hwaseong demand CSV data to MATSim population XML with DRT trips.
 * Handles both rules-based (OD matrix) and real-time (timestamped) demand formats.
 * Now supports batch processing of multiple valid_requests files.
 */
public class DrtRequestXmlGenerator {
    
    private static final String CRS_WGS84 = "EPSG:4326";
    private static final String CRS_KOREA_CENTRAL = "EPSG:5181"; // KGD2002 / Central Belt for Korea
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    private final PopulationFactory populationFactory;
    private final Population population;
    private final CoordinateTransformation coordTransform;
    private final Map<Integer, Coord> stopIdToCoord;
    
    public DrtRequestXmlGenerator() {
        this.population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        this.populationFactory = population.getFactory();
        this.coordTransform = new GeotoolsTransformation(CRS_WGS84, CRS_KOREA_CENTRAL);
        this.stopIdToCoord = new HashMap<>();
    }
    
    /**
     * Load virtual stop locations from CSV file with improved parsing
     */
    public void loadStopsFile(String stopsFilePath) throws IOException {
        if (!Files.exists(Paths.get(stopsFilePath))) {
            throw new IOException("Stops file not found: " + stopsFilePath);
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(stopsFilePath))) {
            String line = reader.readLine(); // Skip header: id,x,y,road_name,road_direction,side_of_road,pair_id,accessibility_note
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    try {
                        // Fix the stop ID parsing logic - VS0001 should become 1, VS0010 should become 10
                        String stopIdStr = parts[0].trim();
                        if (stopIdStr.startsWith("VS")) {
                            stopIdStr = stopIdStr.substring(2); // Remove "VS" prefix
                            // Remove leading zeros
                            int id = Integer.parseInt(stopIdStr);
                            double x = Double.parseDouble(parts[1].trim());
                            double y = Double.parseDouble(parts[2].trim());
                            
                            // Transform from WGS84 to UTM
                            Coord wgs84Coord = new Coord(x, y);
                            Coord utmCoord = coordTransform.transform(wgs84Coord);
                            stopIdToCoord.put(id, utmCoord);
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing stop line: " + line + " - " + e.getMessage());
                    }
                }
            }
        }
        System.out.println("Loaded " + stopIdToCoord.size() + " stop locations");
    }
    
    /**
     * Convert real-time demand CSV to MATSim population
     */
    public void convertRealTimeDemand(String demandFilePath) throws IOException {
        if (!Files.exists(Paths.get(demandFilePath))) {
            throw new IOException("Demand file not found: " + demandFilePath);
        }
        
        int requestCount = 0;
        int errorCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(demandFilePath))) {
            String line = reader.readLine(); // Skip header: idx,o,d,hour,o_h3,d_h3,o_x,o_y,d_x,d_y,time
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 11) {
                    try {
                        int idx = Integer.parseInt(parts[0].trim());
                        int originStop = Integer.parseInt(parts[1].trim());
                        int destStop = Integer.parseInt(parts[2].trim());
                        int hour = Integer.parseInt(parts[3].trim());
                        double ox = Double.parseDouble(parts[6].trim());
                        double oy = Double.parseDouble(parts[7].trim());
                        double dx = Double.parseDouble(parts[8].trim());
                        double dy = Double.parseDouble(parts[9].trim());
                        String timeString = parts[10].trim(); // 2024-10-24 07:00:34
                        
                        // Extract time portion and convert to seconds from midnight
                        String timeOnly = timeString.split(" ")[1]; // 07:00:34
                        LocalTime time = LocalTime.parse(timeOnly, DateTimeFormatter.ofPattern("HH:mm:ss"));
                        double departureTime = time.toSecondOfDay();
                        
                        // Create person and trip
                        createDrtTrip(idx, originStop, destStop, ox, oy, dx, dy, departureTime);
                        requestCount++;
                        
                    } catch (Exception e) {
                        errorCount++;
                        if (errorCount <= 5) { // Only show first 5 errors to avoid spam
                            System.err.println("Error processing line: " + line + " - " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        if (errorCount > 5) {
            System.err.println("... and " + (errorCount - 5) + " more errors");
        }
        
        System.out.println("Converted " + requestCount + " DRT requests from real-time demand");
        System.out.println("Encountered " + errorCount + " parsing errors");
    }
    
    /**
     * Batch process all valid_requests files in the real_time directory
     */
    public void processAllValidRequestsFiles(String realTimeDir, String outputDir) throws IOException {
        Path realTimePath = Paths.get(realTimeDir);
        Path outputPath = Paths.get(outputDir);
        
        if (!Files.exists(realTimePath)) {
            throw new IOException("Real-time directory not found: " + realTimeDir);
        }
        
        // Create output directory if it doesn't exist
        Files.createDirectories(outputPath);
        
        // Find all valid_requests files
        String[] validRequestsFiles = {
            "valid_requests_base_real_time.csv",
            "valid_requests_S1_real_time.csv", 
            "valid_requests_S2_real_time.csv",
            "valid_requests_S3_real_time.csv"
        };
        
        for (String fileName : validRequestsFiles) {
            Path csvFile = realTimePath.resolve(fileName);
            if (Files.exists(csvFile)) {
                System.out.println("\n" + "=".repeat(60));
                System.out.println("Processing: " + fileName);
                System.out.println("=".repeat(60));
                
                // Reset population for each file
                population.getPersons().clear();
                
                // Convert the CSV file
                convertRealTimeDemand(csvFile.toString());
                
                // Generate output filename
                String scenarioName = fileName.replace("valid_requests_", "").replace("_real_time.csv", "");
                String outputFileName = scenarioName + "_population.xml";
                Path outputFile = outputPath.resolve(outputFileName);
                
                // Write population files
                writePopulation(outputFile.toString());
                
                System.out.println("Completed: " + fileName + " -> " + outputFileName);
            } else {
                System.err.println("Warning: File not found: " + csvFile);
            }
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Batch processing completed!");
        System.out.println("Output directory: " + outputDir);
        System.out.println("=".repeat(60));
    }
    
    /**
     * Comprehensive batch processing of all demand files (real-time + rules-based)
     */
    public void processAllDemandFiles(String realTimeDir, String rulesDir, String outputDir) throws IOException {
        Path realTimePath = Paths.get(realTimeDir);
        Path rulesPath = Paths.get(rulesDir);
        Path outputPath = Paths.get(outputDir);
        
        // Validate input directories
        if (!Files.exists(realTimePath)) {
            throw new IOException("Real-time directory not found: " + realTimeDir);
        }
        if (!Files.exists(rulesPath)) {
            throw new IOException("Rules directory not found: " + rulesDir);
        }
        
        // Create output directory if it doesn't exist
        Files.createDirectories(outputPath);
        
        System.out.println("Starting comprehensive demand file processing...");
        System.out.println("Real-time directory: " + realTimeDir);
        System.out.println("Rules directory: " + rulesDir);
        System.out.println("Output directory: " + outputDir);
        
        // Define scenarios to process
        String[] scenarios = {"base", "S1", "S2", "S3"};
        
        // Process each scenario
        for (String scenario : scenarios) {
            System.out.println("\n" + "█".repeat(80));
            System.out.println("PROCESSING SCENARIO: " + scenario.toUpperCase());
            System.out.println("█".repeat(80));
            
            // 1. Process real-time file
            processRealTimeFile(realTimePath, outputPath, scenario);
            
            // 2. Process base rules file
            processRulesFile(rulesPath, outputPath, scenario, null, 1.0);
            
            // 3. Process trip multiplier and rule combinations
            String[] tripLevels = {"trip0.5", "trip1.0", "trip1.5"};
            String[] ruleNumbers = {"rule1", "rule2", "rule3"};
            
            for (String tripLevel : tripLevels) {
                double tripMultiplier = Double.parseDouble(tripLevel.replace("trip", ""));
                
                for (String rule : ruleNumbers) {
                    String suffix = tripLevel + "_" + rule;
                    processRulesFile(rulesPath, outputPath, scenario, suffix, tripMultiplier);
                }
            }
        }
        
        System.out.println("\n" + "█".repeat(80));
        System.out.println("COMPREHENSIVE PROCESSING COMPLETED!");
        System.out.println("Total files processed for 4 scenarios:");
        System.out.println("- 4 real-time files (valid_requests)");
        System.out.println("- 4 base rules files");
        System.out.println("- 36 trip/rule combination files (4 scenarios × 3 trips × 3 rules)");
        System.out.println("- TOTAL: 44 population files generated");
        System.out.println("Output directory: " + outputDir);
        System.out.println("█".repeat(80));
    }
    
    /**
     * Process a single real-time file for a scenario
     */
    private void processRealTimeFile(Path realTimePath, Path outputPath, String scenario) {
        try {
            String fileName = "valid_requests_" + scenario + "_real_time.csv";
            Path csvFile = realTimePath.resolve(fileName);
            
            if (Files.exists(csvFile)) {
                System.out.println("\n" + "=".repeat(60));
                System.out.println("Processing REAL-TIME: " + fileName);
                System.out.println("=".repeat(60));
                
                // Reset population
                population.getPersons().clear();
                
                // Convert the CSV file
                convertRealTimeDemand(csvFile.toString());
                
                // Generate output filename
                String outputFileName = scenario + "_realtime_population.xml";
                Path outputFile = outputPath.resolve(outputFileName);
                
                // Write population files
                writePopulation(outputFile.toString());
                
                System.out.println("Completed: " + fileName + " -> " + outputFileName);
            } else {
                System.err.println("Warning: Real-time file not found: " + csvFile);
            }
        } catch (IOException e) {
            System.err.println("Error processing real-time file for " + scenario + ": " + e.getMessage());
        }
    }
    
    /**
     * Process a single rules-based file for a scenario
     */
    private void processRulesFile(Path rulesPath, Path outputPath, String scenario, String suffix, double tripMultiplier) {
        try {
            // Determine file name and output name
            String fileName;
            String outputFileName;
            
            if (suffix == null) {
                // Base rules file
                fileName = scenario + ".csv";
                outputFileName = scenario + "_base_population.xml";
            } else {
                // Trip/rule combination file
                fileName = scenario + "_" + suffix + ".csv";
                outputFileName = scenario + "_" + suffix + "_population.xml";
            }
            
            Path csvFile = rulesPath.resolve(fileName);
            
            if (Files.exists(csvFile)) {
                System.out.println("\n" + "=".repeat(60));
                System.out.println("Processing RULES: " + fileName + " (multiplier: " + tripMultiplier + ")");
                System.out.println("=".repeat(60));
                
                // Reset population
                population.getPersons().clear();
                
                // Convert the CSV file
                convertRulesDemand(csvFile.toString(), tripMultiplier);
                
                // Generate output file
                Path outputFile = outputPath.resolve(outputFileName);
                
                // Write population files
                writePopulation(outputFile.toString());
                
                System.out.println("Completed: " + fileName + " -> " + outputFileName);
            } else {
                System.err.println("Warning: Rules file not found: " + csvFile);
            }
        } catch (IOException e) {
            System.err.println("Error processing rules file " + scenario + "/" + suffix + ": " + e.getMessage());
        }
    }
    
    /**
     * Convert rules-based OD matrix to MATSim population
     */
    public void convertRulesDemand(String demandFilePath, double tripMultiplier) throws IOException {
        if (!Files.exists(Paths.get(demandFilePath))) {
            throw new IOException("Demand file not found: " + demandFilePath);
        }
        
        // Store OD demand matrix
        Map<String, Double> odMatrix = new HashMap<>();
        List<Integer> zones = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(demandFilePath))) {
            String headerLine = reader.readLine(); // o,d,t_7,t_8,t_9,...,t_22
            String[] headers = headerLine.split(",");
            
            // Extract time columns (t_7 to t_22)
            List<String> timeColumns = new ArrayList<>();
            for (String header : headers) {
                if (header.startsWith("t_")) {
                    timeColumns.add(header);
                }
            }
            
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2 + timeColumns.size()) {
                    int origin = Integer.parseInt(parts[0].trim());
                    int dest = Integer.parseInt(parts[1].trim());
                    
                    if (!zones.contains(origin)) zones.add(origin);
                    if (!zones.contains(dest)) zones.add(dest);
                    
                    // Process each time period
                    for (int i = 0; i < timeColumns.size(); i++) {
                        String timeCol = timeColumns.get(i);
                        double demand = Double.parseDouble(parts[2 + i].trim()) * tripMultiplier;
                        
                        if (demand > 0) {
                            int hour = Integer.parseInt(timeCol.substring(2)); // t_7 -> 7
                            String key = origin + "_" + dest + "_" + hour;
                            odMatrix.put(key, demand);
                        }
                    }
                }
            }
        }
        
        // Generate trips from OD matrix
        int personId = 0;
        Random random = new Random(42); // Fixed seed for reproducibility
        
        for (Map.Entry<String, Double> entry : odMatrix.entrySet()) {
            String[] keyParts = entry.getKey().split("_");
            int origin = Integer.parseInt(keyParts[0]);
            int dest = Integer.parseInt(keyParts[1]);
            int hour = Integer.parseInt(keyParts[2]);
            double demand = entry.getValue();
            
            // Generate individual trips based on demand volume
            int numTrips = (int) Math.round(demand);
            for (int i = 0; i < numTrips; i++) {
                // Random departure time within the hour
                double departureTime = hour * 3600 + random.nextDouble() * 3600;
                
                // Use stop coordinates if available, otherwise generate random coords
                Coord originCoord = stopIdToCoord.getOrDefault(origin, generateRandomCoord(origin, random));
                Coord destCoord = stopIdToCoord.getOrDefault(dest, generateRandomCoord(dest, random));
                
                createDrtTrip(personId++, origin, dest, 
                    originCoord.getX(), originCoord.getY(),
                    destCoord.getX(), destCoord.getY(), departureTime);
            }
        }
        
        System.out.println("Converted " + personId + " DRT requests from rules-based demand");
    }
    
    private void createDrtTrip(int personId, int originStop, int destStop, 
                              double ox, double oy, double dx, double dy, 
                              double departureTime) {
        
        Person person = populationFactory.createPerson(Id.createPersonId("drt_person_" + personId));
        Plan plan = populationFactory.createPlan();
        
        // Transform coordinates to UTM
        Coord utmOriginCoord = coordTransform.transform(new Coord(ox, oy));
        Coord utmDestCoord = coordTransform.transform(new Coord(dx, dy));
        
        // Apply correction to match network coordinate system
        // Network coordinates: X=939000-942000, Y=1910000-1913000
        // EPSG:5181 transformation produces: X≈184000, Y≈412000
        // Required offset: X+755000, Y+1500000 (approximately)
        double xOffset = 755275.0;
        double yOffset = 1500061.0;
        
        Coord originCoord = new Coord(utmOriginCoord.getX() + xOffset, utmOriginCoord.getY() + yOffset);
        Coord destCoord = new Coord(utmDestCoord.getX() + xOffset, utmDestCoord.getY() + yOffset);
        
        // Debug: Print first few coordinate transformations
        if (personId < 3) {
            System.out.printf("Person %d: WGS84 (%.6f, %.6f) -> UTM (%.2f, %.2f) -> Network (%.2f, %.2f)%n", 
                personId, ox, oy, utmOriginCoord.getX(), utmOriginCoord.getY(), originCoord.getX(), originCoord.getY());
        }
        
        // Home activity at origin
        Activity homeActivity = populationFactory.createActivityFromCoord("home", originCoord);
        homeActivity.setEndTime(departureTime);
        plan.addActivity(homeActivity);
        
        // DRT leg
        Leg drtLeg = populationFactory.createLeg("drt");
        plan.addLeg(drtLeg);
        
        // Destination activity
        Activity destActivity = populationFactory.createActivityFromCoord("work", destCoord);
        // Assume 8-hour activity duration
        destActivity.setEndTime(departureTime + 8 * 3600);
        plan.addActivity(destActivity);
        
        // Return leg and activity
        Leg returnLeg = populationFactory.createLeg("drt");
        plan.addLeg(returnLeg);
        
        Activity returnHomeActivity = populationFactory.createActivityFromCoord("home", originCoord);
        plan.addActivity(returnHomeActivity);
        
        person.addPlan(plan);
        population.addPerson(person);
    }
    
    private Coord generateRandomCoord(int zoneId, Random random) {
        // Generate random coordinates within a reasonable bounds for Hwaseong
        // These are approximate UTM coordinates for Hwaseong area
        double baseX = 313000 + (zoneId % 10) * 2000; // Spread zones across X
        double baseY = 4120000 + (zoneId / 10) * 2000; // Spread zones across Y
        
        double x = baseX + (random.nextDouble() - 0.5) * 1000; // ±500m variation
        double y = baseY + (random.nextDouble() - 0.5) * 1000;
        
        return new Coord(x, y);
    }
    
    /**
     * Write population to both XML and XML.gz formats
     */
    public void writePopulation(String outputPath) {
        // Write regular XML file
        new PopulationWriter(population).write(outputPath);
        System.out.println("Written population to: " + outputPath);
        
        // Create compressed version
        String gzPath = outputPath.replace(".xml", ".xml.gz");
        new PopulationWriter(population).write(gzPath);
        System.out.println("Written compressed population to: " + gzPath);
        
        System.out.println("Total persons: " + population.getPersons().size());
        System.out.println("Conversion completed successfully! Both XML and XML.gz formats created.");
    }
    
    /**
     * Main method with enhanced command-line interface
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        String command = args[0];
        
        try {
            DrtRequestXmlGenerator generator = new DrtRequestXmlGenerator();
            
            if ("complete".equals(command)) {
                // Complete processing mode - all real-time + rules files
                if (args.length < 5) {
                    System.err.println("Usage for complete mode: DrtRequestXmlGenerator complete <stopsFile> <realTimeDir> <rulesDir> <outputDir>");
                    return;
                }
                
                String stopsFile = args[1];
                String realTimeDir = args[2];
                String rulesDir = args[3];
                String outputDir = args[4];
                
                System.out.println("Starting complete processing (all scenarios and rules)...");
                System.out.println("Stops file: " + stopsFile);
                System.out.println("Real-time directory: " + realTimeDir);
                System.out.println("Rules directory: " + rulesDir);
                System.out.println("Output directory: " + outputDir);
                
                generator.loadStopsFile(stopsFile);
                generator.processAllDemandFiles(realTimeDir, rulesDir, outputDir);
                
            } else if ("batch".equals(command)) {
                // Batch processing mode (real-time only)
                if (args.length < 4) {
                    System.err.println("Usage for batch mode: DrtRequestXmlGenerator batch <stopsFile> <realTimeDir> <outputDir>");
                    return;
                }
                
                String stopsFile = args[1];
                String realTimeDir = args[2];
                String outputDir = args[3];
                
                System.out.println("Starting batch processing...");
                System.out.println("Stops file: " + stopsFile);
                System.out.println("Real-time directory: " + realTimeDir);
                System.out.println("Output directory: " + outputDir);
                
                generator.loadStopsFile(stopsFile);
                generator.processAllValidRequestsFiles(realTimeDir, outputDir);
                
            } else if ("single".equals(command)) {
                // Single file processing mode (existing functionality)
                if (args.length < 5) {
                    System.err.println("Usage for single mode: DrtRequestXmlGenerator single <stopsFile> <demandFile> <outputFile> <type>");
                    System.err.println("  type: 'realtime' or 'rules'");
                    System.err.println("  For rules type, add trip multiplier as 6th argument (default: 1.0)");
                    return;
                }
                
                String stopsFile = args[1];
                String demandFile = args[2];
                String outputFile = args[3];
                String type = args[4];
                double tripMultiplier = args.length > 5 ? Double.parseDouble(args[5]) : 1.0;
                
                generator.loadStopsFile(stopsFile);
                
                if ("realtime".equals(type)) {
                    generator.convertRealTimeDemand(demandFile);
                } else if ("rules".equals(type)) {
                    generator.convertRulesDemand(demandFile, tripMultiplier);
                } else {
                    System.err.println("Invalid type: " + type + ". Use 'realtime' or 'rules'");
                    return;
                }
                
                generator.writePopulation(outputFile);
                System.out.println("Conversion completed successfully!");
                
            } else {
                System.err.println("Unknown command: " + command);
                printUsage();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error during conversion: " + e.getMessage());
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage: DrtRequestXmlGenerator <command> [options...]");
        System.out.println("");
        System.out.println("Commands:");
        System.out.println("  complete <stopsFile> <realTimeDir> <rulesDir> <outputDir>");
        System.out.println("    Process ALL demand files: real-time + rules for all scenarios");
        System.out.println("    Generates 44 population files: 4 scenarios × (1 real-time + 1 base + 9 rule variants)");
        System.out.println("    Example: complete data/candidate_stops/hwaseong/stops.csv data/demands/hwaseong/real_time data/demands/hwaseong/rules data/populations_complete");
        System.out.println("");
        System.out.println("  batch <stopsFile> <realTimeDir> <outputDir>");
        System.out.println("    Process only valid_requests_*.csv files (real-time demand)");
        System.out.println("    Example: batch data/candidate_stops/hwaseong/stops.csv data/demands/hwaseong/real_time data/populations_test");
        System.out.println("");
        System.out.println("  single <stopsFile> <demandFile> <outputFile> <type> [tripMultiplier]");
        System.out.println("    Process a single file");
        System.out.println("    type: 'realtime' or 'rules'");
        System.out.println("    tripMultiplier: scaling factor for rules-based demand (default: 1.0)");
        System.out.println("");
        System.out.println("Examples:");
        System.out.println("  # Process ALL files (RECOMMENDED):");
        System.out.println("  mvn exec:java -Dexec.mainClass=\"org.matsim.maas.archived.DrtRequestXmlGenerator\" \\");
        System.out.println("    -Dexec.args=\"complete data/candidate_stops/hwaseong/stops.csv data/demands/hwaseong/real_time data/demands/hwaseong/rules data/populations_complete\"");
        System.out.println("");
        System.out.println("  # Process only real-time files:");
        System.out.println("  mvn exec:java -Dexec.mainClass=\"org.matsim.maas.archived.DrtRequestXmlGenerator\" \\");
        System.out.println("    -Dexec.args=\"batch data/candidate_stops/hwaseong/stops.csv data/demands/hwaseong/real_time data/populations_test\"");
        System.out.println("");
        System.out.println("  # Process single file:");
        System.out.println("  mvn exec:java -Dexec.mainClass=\"org.matsim.maas.archived.DrtRequestXmlGenerator\" \\");
        System.out.println("    -Dexec.args=\"single data/candidate_stops/hwaseong/stops.csv data/demands/hwaseong/rules/base_trip1.0_rule1.csv base_trip1.0_rule1_population.xml rules 1.0\"");
    }
} 