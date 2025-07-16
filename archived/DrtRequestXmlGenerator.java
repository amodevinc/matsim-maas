package org.matsim.maas.utils;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.GeotoolsTransformation;
import org.matsim.core.population.io.PopulationWriter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Converts Hwaseong demand CSV data to MATSim population XML with DRT trips.
 * Handles both rules-based (OD matrix) and real-time (timestamped) demand formats.
 */
public class DrtRequestXmlGenerator {
    
    private static final String CRS_WGS84 = "EPSG:4326";
    private static final String CRS_UTM52N = "EPSG:32652"; // UTM Zone 52N for Korea
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    private final PopulationFactory populationFactory;
    private final Population population;
    private final CoordinateTransformation coordTransform;
    private final Map<Integer, Coord> stopIdToCoord;
    
    public DrtRequestXmlGenerator() {
        this.population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        this.populationFactory = population.getFactory();
        this.coordTransform = new GeotoolsTransformation(CRS_WGS84, CRS_UTM52N);
        this.stopIdToCoord = new HashMap<>();
    }
    
    /**
     * Load virtual stop locations from CSV file
     */
    public void loadStopsFile(String stopsFilePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(stopsFilePath))) {
            String line = reader.readLine(); // Skip header
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String stopId = parts[0].replace("VS", "").replace("0", ""); // VS0001 -> 1
                    int id = Integer.parseInt(stopId);
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    
                    // Transform from WGS84 to UTM
                    Coord wgs84Coord = new Coord(x, y);
                    Coord utmCoord = coordTransform.transform(wgs84Coord);
                    stopIdToCoord.put(id, utmCoord);
                }
            }
        }
        System.out.println("Loaded " + stopIdToCoord.size() + " stop locations");
    }
    
    /**
     * Convert real-time demand CSV to MATSim population
     */
    public void convertRealTimeDemand(String demandFilePath) throws IOException {
        int requestCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(demandFilePath))) {
            String line = reader.readLine(); // Skip header: idx,o,d,hour,o_h3,d_h3,o_x,o_y,d_x,d_y,time
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 11) {
                    try {
                        int idx = Integer.parseInt(parts[0]);
                        int originStop = Integer.parseInt(parts[1]);
                        int destStop = Integer.parseInt(parts[2]);
                        int hour = Integer.parseInt(parts[3]);
                        double ox = Double.parseDouble(parts[6]);
                        double oy = Double.parseDouble(parts[7]);
                        double dx = Double.parseDouble(parts[8]);
                        double dy = Double.parseDouble(parts[9]);
                        String timeString = parts[10]; // 2024-10-24 07:00:34
                        
                        // Extract time portion and convert to seconds from midnight
                        String timeOnly = timeString.split(" ")[1]; // 07:00:34
                        LocalTime time = LocalTime.parse(timeOnly, DateTimeFormatter.ofPattern("HH:mm:ss"));
                        double departureTime = time.toSecondOfDay();
                        
                        // Create person and trip
                        createDrtTrip(idx, originStop, destStop, ox, oy, dx, dy, departureTime);
                        requestCount++;
                        
                    } catch (Exception e) {
                        System.err.println("Error processing line: " + line + " - " + e.getMessage());
                    }
                }
            }
        }
        System.out.println("Converted " + requestCount + " DRT requests from real-time demand");
    }
    
    /**
     * Convert rules-based OD matrix to MATSim population
     */
    public void convertRulesDemand(String demandFilePath, double tripMultiplier) throws IOException {
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
                    int origin = Integer.parseInt(parts[0]);
                    int dest = Integer.parseInt(parts[1]);
                    
                    if (!zones.contains(origin)) zones.add(origin);
                    if (!zones.contains(dest)) zones.add(dest);
                    
                    // Process each time period
                    for (int i = 0; i < timeColumns.size(); i++) {
                        String timeCol = timeColumns.get(i);
                        double demand = Double.parseDouble(parts[2 + i]) * tripMultiplier;
                        
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
        // UTM transformation produces: X=306000-308000, Y=4119000-4120000
        // Required offset: X+633000, Y-2208000 (approximately)
        double xOffset = 633000.0;
        double yOffset = -2208000.0;
        
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
     * Main method for command-line usage
     */
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: DrtRequestXmlGenerator <stopsFile> <demandFile> <outputFile> <type>");
            System.err.println("  type: 'realtime' or 'rules'");
            System.err.println("  For rules type, add trip multiplier as 5th argument (default: 1.0)");
            return;
        }
        
        String stopsFile = args[0];
        String demandFile = args[1];
        String outputFile = args[2];
        String type = args[3];
        double tripMultiplier = args.length > 4 ? Double.parseDouble(args[4]) : 1.0;
        
        try {
            DrtRequestXmlGenerator generator = new DrtRequestXmlGenerator();
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
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error during conversion: " + e.getMessage());
        }
    }
} 