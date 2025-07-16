package org.matsim.maas.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Parses and integrates rules CSV files with real-time demand data for population generation.
 * Handles O/D matrices, temporal patterns, and demand uncertainty scenarios.
 */
public class RulesDataParser {
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * Represents a demand pattern from rules CSV files
     */
    public static class DemandPattern {
        public String scenario;        // base, S1, S2, S3, S4
        public double tripMultiplier;  // 0.5, 1.0, 1.5
        public int rule;              // 1, 2, 3
        public Map<String, Double> odMatrix;  // "origin_dest_hour" -> demand
        public Map<Integer, Double> hourlyTotals;  // hour -> total demand
        public Set<Integer> zones;     // all zones referenced
        
        public DemandPattern(String scenario, double tripMultiplier, int rule) {
            this.scenario = scenario;
            this.tripMultiplier = tripMultiplier;
            this.rule = rule;
            this.odMatrix = new HashMap<>();
            this.hourlyTotals = new HashMap<>();
            this.zones = new HashSet<>();
        }
        
        public String getFilePattern() {
            return scenario + "_trip" + tripMultiplier + "_rule" + rule + ".csv";
        }
        
        public int getTotalTrips() {
            return (int) Math.round(odMatrix.values().stream().mapToDouble(Double::doubleValue).sum());
        }
        
        public double getDemand(int origin, int dest, int hour) {
            String key = origin + "_" + dest + "_" + hour;
            return odMatrix.getOrDefault(key, 0.0);
        }
        
        public void addDemand(int origin, int dest, int hour, double demand) {
            if (demand > 0) {
                String key = origin + "_" + dest + "_" + hour;
                odMatrix.put(key, demand);
                hourlyTotals.merge(hour, demand, Double::sum);
                zones.add(origin);
                zones.add(dest);
            }
        }
    }
    
    /**
     * Represents a real-time trip request
     */
    public static class RealTimeTrip {
        public int idx;
        public int origin;
        public int destination;
        public int hour;
        public String originH3;
        public String destH3;
        public double originX;
        public double originY;
        public double destX;
        public double destY;
        public String timestamp;
        public double departureTimeSeconds;
        
        public RealTimeTrip(String[] csvFields) {
            if (csvFields.length >= 11) {
                this.idx = Integer.parseInt(csvFields[0]);
                this.origin = Integer.parseInt(csvFields[1]);
                this.destination = Integer.parseInt(csvFields[2]);
                this.hour = Integer.parseInt(csvFields[3]);
                this.originH3 = csvFields[4];
                this.destH3 = csvFields[5];
                this.originX = Double.parseDouble(csvFields[6]);
                this.originY = Double.parseDouble(csvFields[7]);
                this.destX = Double.parseDouble(csvFields[8]);
                this.destY = Double.parseDouble(csvFields[9]);
                this.timestamp = csvFields[10];
                
                // Parse timestamp to get departure time in seconds
                if (timestamp.contains(" ")) {
                    String timeOnly = timestamp.split(" ")[1]; // "2024-10-24 07:00:34" -> "07:00:34"
                    LocalTime time = LocalTime.parse(timeOnly, TIME_FORMATTER);
                    this.departureTimeSeconds = time.toSecondOfDay();
                }
            }
        }
    }
    
    /**
     * Container for all rules data and real-time patterns
     */
    public static class RulesDataSet {
        public Map<String, DemandPattern> demandPatterns;  // filePattern -> DemandPattern
        public Map<String, List<RealTimeTrip>> realTimeTrips;  // scenario -> trips
        public Map<String, Map<Integer, List<RealTimeTrip>>> realTimeTripsByHour;  // scenario -> hour -> trips
        
        public RulesDataSet() {
            this.demandPatterns = new HashMap<>();
            this.realTimeTrips = new HashMap<>();
            this.realTimeTripsByHour = new HashMap<>();
        }
        
        public DemandPattern getDemandPattern(String scenario, double tripMultiplier, int rule) {
            String filePattern = scenario + "_trip" + tripMultiplier + "_rule" + rule + ".csv";
            return demandPatterns.get(filePattern);
        }
        
        public List<RealTimeTrip> getRealTimeTrips(String scenario) {
            return realTimeTrips.getOrDefault(scenario, new ArrayList<>());
        }
        
        public List<RealTimeTrip> getRealTimeTrips(String scenario, int hour) {
            return realTimeTripsByHour.getOrDefault(scenario, new HashMap<>())
                    .getOrDefault(hour, new ArrayList<>());
        }
        
        public void printSummary() {
            System.out.println("=== Rules Data Set Summary ===");
            System.out.println("Demand Patterns: " + demandPatterns.size());
            demandPatterns.forEach((pattern, data) -> {
                System.out.println("  " + pattern + ": " + data.getTotalTrips() + " trips, " + 
                                   data.zones.size() + " zones");
            });
            
            System.out.println("\nReal-time Trips:");
            realTimeTrips.forEach((scenario, trips) -> {
                System.out.println("  " + scenario + ": " + trips.size() + " trips");
            });
        }
    }
    
    /**
     * Parse a single rules CSV file
     */
    public static DemandPattern parseRulesFile(String filePath) throws IOException {
        // Extract scenario info from filename
        String filename = filePath.substring(filePath.lastIndexOf('/') + 1);
        String baseFilename = filename.replace(".csv", "");
        
        String scenario;
        double tripMultiplier;
        int rule;
        
        // Handle base scenario files without trip multiplier/rule (e.g., "base.csv", "S1.csv")
        if (!baseFilename.contains("_trip")) {
            scenario = baseFilename;
            tripMultiplier = 1.0;  // Default multiplier
            rule = 1;             // Default rule
        } else {
            // Handle files with trip multiplier and rule (e.g., "base_trip0.5_rule1.csv")
            String[] parts = baseFilename.split("_");
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid rules filename format: " + filename + 
                    " (expected format: scenario_tripX.X_ruleX.csv or scenario.csv)");
            }
            
            scenario = parts[0];
            tripMultiplier = Double.parseDouble(parts[1].replace("trip", ""));  
            rule = Integer.parseInt(parts[2].replace("rule", ""));
        }
        
        DemandPattern pattern = new DemandPattern(scenario, tripMultiplier, rule);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
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
                String[] fields = line.split(",");
                if (fields.length >= 2 + timeColumns.size()) {
                    int origin = Integer.parseInt(fields[0]);
                    int dest = Integer.parseInt(fields[1]);
                    
                    // Process each time period
                    for (int i = 0; i < timeColumns.size(); i++) {
                        String timeCol = timeColumns.get(i);
                        double demand = Double.parseDouble(fields[2 + i]);
                        
                        if (demand > 0) {
                            int hour = Integer.parseInt(timeCol.substring(2)); // t_7 -> 7
                            pattern.addDemand(origin, dest, hour, demand);
                        }
                    }
                }
            }
        }
        
        return pattern;
    }
    
    /**
     * Parse a real-time CSV file
     */
    public static List<RealTimeTrip> parseRealTimeFile(String filePath) throws IOException {
        List<RealTimeTrip> trips = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String headerLine = reader.readLine(); // Skip header: idx,o,d,hour,o_h3,d_h3,o_x,o_y,d_x,d_y,time
            
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length >= 11) {
                    try {
                        RealTimeTrip trip = new RealTimeTrip(fields);
                        trips.add(trip);
                    } catch (Exception e) {
                        System.err.println("Error parsing real-time trip: " + line + " - " + e.getMessage());
                    }
                }
            }
        }
        
        return trips;
    }
    
    /**
     * Load all rules and real-time data from directories
     */
    public static RulesDataSet loadAllRulesData(String rulesDir, String realTimeDir) throws IOException {
        RulesDataSet dataSet = new RulesDataSet();
        
        // Load all rules files
        java.io.File rulesDirectory = new java.io.File(rulesDir);
        if (rulesDirectory.exists() && rulesDirectory.isDirectory()) {
            for (java.io.File file : rulesDirectory.listFiles()) {
                if (file.getName().endsWith(".csv")) {
                    try {
                        DemandPattern pattern = parseRulesFile(file.getAbsolutePath());
                        dataSet.demandPatterns.put(pattern.getFilePattern(), pattern);
                        System.out.println("Loaded rules: " + pattern.getFilePattern() + 
                                           " (" + pattern.getTotalTrips() + " trips)");
                    } catch (Exception e) {
                        System.err.println("Error loading rules file " + file.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
        
        // Load all real-time files
        java.io.File realTimeDirectory = new java.io.File(realTimeDir);
        if (realTimeDirectory.exists() && realTimeDirectory.isDirectory()) {
            for (java.io.File file : realTimeDirectory.listFiles()) {
                if (file.getName().endsWith("_real_time.csv")) {
                    try {
                        String scenario = file.getName().replace("_real_time.csv", "");
                        List<RealTimeTrip> trips = parseRealTimeFile(file.getAbsolutePath());
                        dataSet.realTimeTrips.put(scenario, trips);
                        
                        // Group by hour for efficient lookup
                        Map<Integer, List<RealTimeTrip>> tripsByHour = new HashMap<>();
                        for (RealTimeTrip trip : trips) {
                            tripsByHour.computeIfAbsent(trip.hour, k -> new ArrayList<>()).add(trip);
                        }
                        dataSet.realTimeTripsByHour.put(scenario, tripsByHour);
                        
                        System.out.println("Loaded real-time: " + scenario + " (" + trips.size() + " trips)");
                    } catch (Exception e) {
                        System.err.println("Error loading real-time file " + file.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
        
        return dataSet;
    }
    
    /**
     * Get representative coordinate for a zone from real-time data
     */
    public static org.matsim.api.core.v01.Coord getZoneCoordinate(RulesDataSet dataSet, int zoneId, String scenario, boolean isDestination) {
        List<RealTimeTrip> trips = dataSet.getRealTimeTrips(scenario);
        if (trips.isEmpty()) {
            return null;
        }
        
        // Collect coordinates for this zone
        List<Double> xCoords = new ArrayList<>();
        List<Double> yCoords = new ArrayList<>();
        
        for (RealTimeTrip trip : trips) {
            if (isDestination && trip.destination == zoneId) {
                xCoords.add(trip.destX);
                yCoords.add(trip.destY);
            } else if (!isDestination && trip.origin == zoneId) {
                xCoords.add(trip.originX);
                yCoords.add(trip.originY);
            }
        }
        
        if (xCoords.isEmpty()) {
            return null;
        }
        
        // Return centroid of coordinates
        double avgX = xCoords.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgY = yCoords.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        return new org.matsim.api.core.v01.Coord(avgX, avgY);
    }
    
    /**
     * Main method for testing and command-line usage
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: RulesDataParser <rules_dir> <real_time_dir>");
            System.exit(1);
        }
        
        String rulesDir = args[0];
        String realTimeDir = args[1];
        
        try {
            System.out.println("Loading rules data from: " + rulesDir);
            System.out.println("Loading real-time data from: " + realTimeDir);
            
            RulesDataSet dataSet = loadAllRulesData(rulesDir, realTimeDir);
            dataSet.printSummary();
            
            // Test specific lookups
            System.out.println("\n=== Sample Data Lookups ===");
            DemandPattern basePattern = dataSet.getDemandPattern("base", 1.0, 1);
            if (basePattern != null) {
                System.out.println("Base 1.0x Rule1 total trips: " + basePattern.getTotalTrips());
                System.out.println("Base 1.0x Rule1 zones: " + basePattern.zones.size());
                System.out.println("Base 1.0x Rule1 hourly totals: " + basePattern.hourlyTotals);
            }
            
            // Test coordinate lookup
            org.matsim.api.core.v01.Coord coord = getZoneCoordinate(dataSet, 1, "base", false);
            if (coord != null) {
                System.out.println("Zone 1 origin coordinate: (" + coord.getX() + ", " + coord.getY() + ")");
            }
            
        } catch (Exception e) {
            System.err.println("Error loading rules data: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
} 