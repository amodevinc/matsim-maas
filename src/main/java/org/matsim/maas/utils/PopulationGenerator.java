package org.matsim.maas.utils;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Utility class to generate MATSim population files from O/D demand matrices.
 * Converts CSV-based demand data to MATSim-compatible population XML files.
 */
public class PopulationGenerator {

    private static final String NETWORK_FILE = "data/networks/hwaseong_network.xml";
    private static final String ZONE_CENTROIDS_FILE = "data/candidate_stops/hwaseong/stops.csv";
    private static final Random random = new Random(42);

    private Network network;
    private Map<Integer, Coord> zoneCentroids;
    private PopulationFactory populationFactory;

    public PopulationGenerator() {
        loadNetwork();
        loadZoneCentroids();
        this.populationFactory = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation().getFactory();
    }

    private void loadNetwork() {
        var scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(NETWORK_FILE);
        this.network = scenario.getNetwork();
        System.out.println("PopulationGenerator: Loaded network with " + network.getLinks().size() + " links");
    }

    private void loadZoneCentroids() {
        this.zoneCentroids = new HashMap<>();
        
        // Create a simple spatial grid for zones 1-72
        // Based on Hwaseong network coordinate bounds in EPSG:5179
        // X range: 939266.6476699853 to 941656.6018873577
        // Y range: 1910347.072038808 to 1913238.4957806764
        double baseX = 940461.0; // Center of X range
        double baseY = 1911792.0; // Center of Y range
        double spacing = 300.0; // ~300m spacing in projected coordinates
        
        for (int i = 1; i <= 72; i++) {
            int row = (i - 1) / 9; // 8 rows (0-7)
            int col = (i - 1) % 9; // 9 columns (0-8)
            
            double x = baseX + (col - 4) * spacing; // Center the grid
            double y = baseY + (row - 4) * spacing;
            
            zoneCentroids.put(i, new Coord(x, y));
        }
        
        System.out.println("PopulationGenerator: Created " + zoneCentroids.size() + " zone centroids in EPSG:5179 coordinates");
    }

    /**
     * Generate population from O/D matrix CSV file
     * @param odMatrixFile Path to O/D matrix CSV file
     * @param outputFile Path to output population XML file
     */
    public void generatePopulation(String odMatrixFile, String outputFile) {
        System.out.println("PopulationGenerator: Generating population from " + odMatrixFile);
        
        Population population = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(odMatrixFile))) {
            String headerLine = reader.readLine();
            String[] headers = headerLine.split(",");
            
            // Parse time headers (t_7, t_8, ..., t_22)
            Map<String, Integer> timeToHour = new HashMap<>();
            for (int i = 2; i < headers.length; i++) {
                String timeHeader = headers[i];
                int hour = Integer.parseInt(timeHeader.substring(2)); // Extract hour from "t_7"
                timeToHour.put(timeHeader, hour);
            }
            
            String line;
            int personId = 1;
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                int origin = Integer.parseInt(parts[0]);
                int destination = Integer.parseInt(parts[1]);
                
                // Skip if origin equals destination
                if (origin == destination) continue;
                
                // Process each time period
                for (int i = 2; i < parts.length && i < headers.length; i++) {
                    double tripCount = Double.parseDouble(parts[i]);
                    if (tripCount > 0) {
                        String timeHeader = headers[i];
                        int hour = timeToHour.get(timeHeader);
                        
                        // Create trips for this O/D pair and time period
                        int numTrips = (int) Math.round(tripCount);
                        for (int trip = 0; trip < numTrips; trip++) {
                            Person person = createPerson(personId++, origin, destination, hour);
                            population.addPerson(person);
                        }
                    }
                }
            }
            
            System.out.println("PopulationGenerator: Generated " + population.getPersons().size() + " persons");
            
            // Write population to file
            new PopulationWriter(population).write(outputFile);
            System.out.println("PopulationGenerator: Written population to " + outputFile);
            
        } catch (IOException e) {
            System.err.println("Error generating population: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Person createPerson(int personId, int origin, int destination, int hour) {
        Person person = populationFactory.createPerson(Id.createPersonId(personId));
        Plan plan = populationFactory.createPlan();
        
        // Get coordinates for origin and destination
        Coord originCoord = zoneCentroids.get(origin);
        Coord destCoord = zoneCentroids.get(destination);
        
        if (originCoord == null || destCoord == null) {
            System.err.println("Warning: Missing coordinates for zones " + origin + " or " + destination);
            return person;
        }
        
        // Add some random variation to coordinates (within 100m)
        originCoord = addRandomVariation(originCoord, 100);
        destCoord = addRandomVariation(destCoord, 100);
        
        // Find nearest network links - use first link as default
        org.matsim.api.core.v01.network.Link originLink = network.getLinks().values().iterator().next();
        org.matsim.api.core.v01.network.Link destLink = network.getLinks().values().iterator().next();
        
        // Find closest links
        double minOriginDistance = Double.MAX_VALUE;
        double minDestDistance = Double.MAX_VALUE;
        
        for (org.matsim.api.core.v01.network.Link link : network.getLinks().values()) {
            double distanceToOrigin = CoordUtils.calcEuclideanDistance(originCoord, link.getCoord());
            if (distanceToOrigin < minOriginDistance) {
                minOriginDistance = distanceToOrigin;
                originLink = link;
            }
            
            double distanceToDest = CoordUtils.calcEuclideanDistance(destCoord, link.getCoord());
            if (distanceToDest < minDestDistance) {
                minDestDistance = distanceToDest;
                destLink = link;
            }
        }
        
        // Create home activity (before trip)
        Activity homeActivity = populationFactory.createActivityFromLinkId("home", originLink.getId());
        homeActivity.setCoord(originCoord);
        
        // Random departure time within the hour (with some variation)
        int departureTime = hour * 3600 + random.nextInt(3600); // Random minute within hour
        homeActivity.setEndTime(departureTime);
        
        plan.addActivity(homeActivity);
        
        // Create trip leg
        Leg leg = populationFactory.createLeg(TransportMode.drt);
        plan.addLeg(leg);
        
        // Create destination activity
        Activity destActivity = populationFactory.createActivityFromLinkId("work", destLink.getId());
        destActivity.setCoord(destCoord);
        destActivity.setMaximumDuration(8 * 3600); // 8 hours
        
        plan.addActivity(destActivity);
        
        // Add return trip leg
        Leg returnLeg = populationFactory.createLeg(TransportMode.drt);
        plan.addLeg(returnLeg);
        
        // Create return home activity
        Activity returnHomeActivity = populationFactory.createActivityFromLinkId("home", originLink.getId());
        returnHomeActivity.setCoord(originCoord);
        
        plan.addActivity(returnHomeActivity);
        
        person.addPlan(plan);
        plan.setPerson(person);
        
        return person;
    }

    private Coord addRandomVariation(Coord coord, double radiusMeters) {
        // For projected coordinates (EPSG:5179), we can add variation directly in meters
        double deltaX = random.nextGaussian() * radiusMeters;
        double deltaY = random.nextGaussian() * radiusMeters;
        
        return new Coord(coord.getX() + deltaX, coord.getY() + deltaY);
    }

    /**
     * Generate all population variants for the experiment
     */
    public void generateAllPopulations() {
        String[] scenarios = {"base", "S1", "S2", "S3", "S4"};
        String[] multipliers = {"0.5", "1.0", "1.5"};
        String[] rules = {"1", "2", "3"};
        
        for (String scenario : scenarios) {
            for (String multiplier : multipliers) {
                for (String rule : rules) {
                    String inputFile = String.format("data/demands/hwaseong/rules/%s_trip%s_rule%s.csv", 
                                                   scenario, multiplier, rule);
                    String outputFile = String.format("data/populations/%s_trip%s_rule%s_population.xml.gz", 
                                                    scenario, multiplier, rule);
                    
                    generatePopulation(inputFile, outputFile);
                }
            }
        }
    }

    public static void main(String[] args) {
        PopulationGenerator generator = new PopulationGenerator();
        
        if (args.length == 2) {
            // Generate single population file
            generator.generatePopulation(args[0], args[1]);
        } else if (args.length == 0) {
            // Generate all population files
            generator.generateAllPopulations();
        } else {
            System.out.println("Usage: PopulationGenerator [inputFile outputFile]");
            System.out.println("  or: PopulationGenerator (to generate all populations)");
        }
    }
}