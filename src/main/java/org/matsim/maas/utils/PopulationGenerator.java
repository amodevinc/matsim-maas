package org.matsim.maas.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Utility class to generate MATSim population files from O/D demand matrices and real-time demand data.
 * Converts CSV-based demand data to MATSim-compatible population XML files.
 * Supports both traditional O/D matrices and real-time demand requests with coordinate transformations.
 */
public class PopulationGenerator {

    private static final Logger log = LogManager.getLogger(PopulationGenerator.class);
    private static final String NETWORK_FILE = "data/networks/hwaseong_network.xml";
    private static final String ZONE_CENTROIDS_FILE = "data/candidate_stops/hwaseong/stops.csv";
    private static final Random random = new Random(42);

    // Activity types
    private static final String HOME_ACTIVITY = "home";
    private static final String WORK_ACTIVITY = "work";
    
    // Default activity durations
    private static final double HOME_DURATION = 8 * 3600; // 8 hours
    private static final double TRIP_DURATION = 1 * 3600;  // 1 hour

    private Network network;
    private Map<Integer, Coord> zoneCentroids;
    private PopulationFactory populationFactory;
    private Population population;

    public PopulationGenerator() {
        loadNetwork();
        loadZoneCentroids();
        var scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        this.population = scenario.getPopulation();
        this.populationFactory = population.getFactory();
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

    // ========================================================================================
    // NEW METHODS FOR REAL-TIME DEMAND PROCESSING
    // ========================================================================================

    /**
     * Generate a MATSim population from real-time demand requests.
     * 
     * @param demandRequests List of demand requests
     * @param scenarioName Name of the scenario (for person ID prefixes)
     * @return Generated Population object
     */
    public Population generatePopulationFromRequests(List<DemandRequest> demandRequests, String scenarioName) {
        log.info("Generating population for scenario '{}' with {} demand requests", 
                scenarioName, demandRequests.size());
        
        population.getPersons().clear(); // Clear any existing persons
        
        for (DemandRequest request : demandRequests) {
            Person person = createPersonFromRequest(request, scenarioName);
            population.addPerson(person);
        }
        
        log.info("Generated population with {} persons", population.getPersons().size());
        return population;
    }

    /**
     * Create a MATSim person from a demand request.
     */
    private Person createPersonFromRequest(DemandRequest request, String scenarioName) {
        // Create unique person ID
        String personId = String.format("%s_person_%d", scenarioName, request.getIdx());
        Person person = populationFactory.createPerson(Id.createPersonId(personId));
        
        // Create plan
        Plan plan = populationFactory.createPlan();
        
        // Add activities and legs to the plan
        addActivitiesAndLegsFromRequest(plan, request);
        
        // Add person attributes
        addPersonAttributesFromRequest(person, request);
        
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        
        return person;
    }

    /**
     * Add activities and legs to a person's plan based on the demand request.
     */
    private void addActivitiesAndLegsFromRequest(Plan plan, DemandRequest request) {
        // Find closest network links to origin and destination
        org.matsim.api.core.v01.network.Link originLink = findClosestLink(request.getOriginProjected());
        org.matsim.api.core.v01.network.Link destLink = findClosestLink(request.getDestinationProjected());
        
        // Start with home activity at origin
        Activity homeActivity = populationFactory.createActivityFromLinkId(HOME_ACTIVITY, originLink.getId());
        homeActivity.setCoord(request.getOriginProjected());
        
        // Set end time based on request departure time
        double departureTime = request.getDepartureTimeSeconds();
        homeActivity.setEndTime(departureTime);
        plan.addActivity(homeActivity);
        
        // Add leg for the trip (using DRT mode)
        Leg leg = populationFactory.createLeg(TransportMode.drt);
        plan.addLeg(leg);
        
        // Add destination activity 
        Activity destinationActivity = populationFactory.createActivityFromLinkId(WORK_ACTIVITY, destLink.getId());
        destinationActivity.setCoord(request.getDestinationProjected());
        destinationActivity.setStartTime(departureTime + 600); // Assume 10 min travel time minimum
        destinationActivity.setEndTime(departureTime + TRIP_DURATION);
        plan.addActivity(destinationActivity);
        
        // Add return leg (optional, for complete round trip)
        Leg returnLeg = populationFactory.createLeg(TransportMode.drt);
        plan.addLeg(returnLeg);
        
        // Return home activity
        Activity returnHomeActivity = populationFactory.createActivityFromLinkId(HOME_ACTIVITY, originLink.getId());
        returnHomeActivity.setCoord(request.getOriginProjected());
        returnHomeActivity.setStartTime(departureTime + TRIP_DURATION + 600);
        plan.addActivity(returnHomeActivity);
    }

    /**
     * Find the closest network link to a coordinate.
     */
    private org.matsim.api.core.v01.network.Link findClosestLink(Coord coord) {
        org.matsim.api.core.v01.network.Link closestLink = network.getLinks().values().iterator().next();
        double minDistance = Double.MAX_VALUE;
        
        for (org.matsim.api.core.v01.network.Link link : network.getLinks().values()) {
            double distance = CoordUtils.calcEuclideanDistance(coord, link.getCoord());
            if (distance < minDistance) {
                minDistance = distance;
                closestLink = link;
            }
        }
        
        return closestLink;
    }

    /**
     * Add person attributes based on demand request properties.
     */
    private void addPersonAttributesFromRequest(Person person, DemandRequest request) {
        // Add custom attributes that might be useful for analysis
        person.getAttributes().putAttribute("original_request_idx", request.getIdx());
        person.getAttributes().putAttribute("origin_zone", request.getOriginZone());
        person.getAttributes().putAttribute("destination_zone", request.getDestinationZone());
        person.getAttributes().putAttribute("request_hour", request.getHour());
        person.getAttributes().putAttribute("origin_h3", request.getOriginH3());
        person.getAttributes().putAttribute("destination_h3", request.getDestinationH3());
    }

    /**
     * Create a simplified population with only one-way trips (no return).
     * Useful for DRT analysis where return trips are not needed.
     */
    public Population generateSimplifiedPopulation(List<DemandRequest> demandRequests, String scenarioName) {
        log.info("Generating simplified population for scenario '{}' with {} demand requests", 
                scenarioName, demandRequests.size());
        
        population.getPersons().clear();
        
        for (DemandRequest request : demandRequests) {
            Person person = createSimplifiedPersonFromRequest(request, scenarioName);
            population.addPerson(person);
        }
        
        log.info("Generated simplified population with {} persons", population.getPersons().size());
        return population;
    }

    /**
     * Create a person with simplified plan (one-way trip only).
     */
    private Person createSimplifiedPersonFromRequest(DemandRequest request, String scenarioName) {
        String personId = String.format("%s_person_%d", scenarioName, request.getIdx());
        Person person = populationFactory.createPerson(Id.createPersonId(personId));
        
        Plan plan = populationFactory.createPlan();
        
        // Find closest network links
        org.matsim.api.core.v01.network.Link originLink = findClosestLink(request.getOriginProjected());
        org.matsim.api.core.v01.network.Link destLink = findClosestLink(request.getDestinationProjected());
        
        // Origin activity
        Activity originActivity = populationFactory.createActivityFromLinkId(HOME_ACTIVITY, originLink.getId());
        originActivity.setCoord(request.getOriginProjected());
        originActivity.setEndTime(request.getDepartureTimeSeconds());
        plan.addActivity(originActivity);
        
        // Trip leg
        Leg leg = populationFactory.createLeg(TransportMode.drt);
        plan.addLeg(leg);
        
        // Destination activity (open-ended)
        Activity destinationActivity = populationFactory.createActivityFromLinkId(WORK_ACTIVITY, destLink.getId());
        destinationActivity.setCoord(request.getDestinationProjected());
        plan.addActivity(destinationActivity);
        
        // Add attributes
        addPersonAttributesFromRequest(person, request);
        
        person.addPlan(plan);
        person.setSelectedPlan(plan);
        
        return person;
    }

    /**
     * Write population to XML file.
     * 
     * @param population Population to write
     * @param outputPath Path for the output XML file
     * @throws IOException If file cannot be written
     */
    public void writePopulation(Population population, Path outputPath) throws IOException {
        log.info("Writing population to: {}", outputPath);
        
        PopulationWriter writer = new PopulationWriter(population);
        writer.write(outputPath.toString());
        
        log.info("Successfully wrote population with {} persons to {}", 
                population.getPersons().size(), outputPath);
    }

    /**
     * Generate and write population file directly from demand requests.
     * 
     * @param demandRequests List of demand requests
     * @param scenarioName Scenario name
     * @param outputPath Output file path
     * @throws IOException If file cannot be written
     */
    public void generateAndWritePopulation(List<DemandRequest> demandRequests, 
                                          String scenarioName, 
                                          Path outputPath) throws IOException {
        Population pop = generatePopulationFromRequests(demandRequests, scenarioName);
        writePopulation(pop, outputPath);
    }

    // ========================================================================================
    // ORIGINAL METHODS FOR O/D MATRIX PROCESSING (PRESERVED)
    // ========================================================================================

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