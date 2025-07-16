package org.matsim.maas.utils;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.*;
import org.matsim.maas.utils.RulesDataParser.DemandPattern;
import org.matsim.maas.utils.RulesDataParser.RealTimeTrip;

import java.util.*;
import java.util.stream.Collectors;

public class VirtualStopNetworkMapper {
    
    private final Random random;
    private static final double ZONE_RADIUS_METERS = 500.0;
    private static final double CONCENTRATION_THRESHOLD = 0.7;
    
    public VirtualStopNetworkMapper(long seed) {
        this.random = new Random(seed);
    }
    
    public static class SpatialReallocationResult {
        public Population reallocatedPopulation;
        public Map<Integer, List<Coord>> zoneCoordinates;
        public Map<String, Integer> spatialDistribution;
        public String reallocationMethod;
        public double spatialFitness;
        public int totalZonesUsed;
        public int totalActivitiesReallocated;
        
        public SpatialReallocationResult(Population population, String method) {
            this.reallocatedPopulation = population;
            this.reallocationMethod = method;
            this.zoneCoordinates = new HashMap<>();
            this.spatialDistribution = new HashMap<>();
        }
        
        public void printSummary() {
            System.out.println("=== Spatial Reallocation Summary ===");
            System.out.println("Reallocation Method: " + reallocationMethod);
            System.out.println("Spatial Fitness: " + String.format("%.3f", spatialFitness));
            System.out.println("Total Zones Used: " + totalZonesUsed);
            System.out.println("Total Activities Reallocated: " + totalActivitiesReallocated);
        }
    }
    
    public SpatialReallocationResult reallocateActivities(Population population, DemandPattern demandPattern, 
                                                         List<RealTimeTrip> realTimeTrips) {
        
        Population reallocatedPopulation = clonePopulationStructure(population);
        Map<Integer, List<Coord>> zoneCoordinates = extractZoneCoordinates(realTimeTrips);
        
        String reallocationMethod = getReallocationMethod(demandPattern.scenario);
        applySpatialReallocation(reallocatedPopulation, demandPattern, zoneCoordinates, realTimeTrips);
        
        SpatialReallocationResult result = new SpatialReallocationResult(reallocatedPopulation, reallocationMethod);
        result.zoneCoordinates = zoneCoordinates;
        result.spatialDistribution = calculateSpatialDistribution(reallocatedPopulation, zoneCoordinates);
        result.spatialFitness = calculateSpatialFitness(demandPattern, result.spatialDistribution);
        result.totalZonesUsed = result.spatialDistribution.size();
        result.totalActivitiesReallocated = countTotalActivities(reallocatedPopulation);
        
        return result;
    }
    
    private Map<Integer, List<Coord>> extractZoneCoordinates(List<RealTimeTrip> realTimeTrips) {
        Map<Integer, List<Coord>> zoneCoords = new HashMap<>();
        
        for (RealTimeTrip trip : realTimeTrips) {
            Coord originCoord = new Coord(trip.originX, trip.originY);
            Coord destCoord = new Coord(trip.destX, trip.destY);
            
            zoneCoords.computeIfAbsent(trip.origin, k -> new ArrayList<>()).add(originCoord);
            zoneCoords.computeIfAbsent(trip.destination, k -> new ArrayList<>()).add(destCoord);
        }
        
        return zoneCoords;
    }
    
    private void applySpatialReallocation(Population population, DemandPattern demandPattern, 
                                        Map<Integer, List<Coord>> zoneCoordinates, List<RealTimeTrip> realTimeTrips) {
        
        switch (demandPattern.scenario) {
            case "S2":
                applyS2SpatialConcentration(population, demandPattern, zoneCoordinates);
                break;
            default:
                applyBaseSpatialDistribution(population, demandPattern, zoneCoordinates);
                break;
        }
    }
    
    private void applyBaseSpatialDistribution(Population population, DemandPattern demandPattern, 
                                            Map<Integer, List<Coord>> zoneCoordinates) {
        
        Map<Integer, List<Integer>> odMapping = createODMapping(demandPattern);
        reallocateActivitiesToZones(population, zoneCoordinates, odMapping, 1.0);
    }
    
    private void applyS2SpatialConcentration(Population population, DemandPattern demandPattern, 
                                           Map<Integer, List<Coord>> zoneCoordinates) {
        
        Map<Integer, List<Integer>> odMapping = createODMapping(demandPattern);
        Map<Integer, List<Integer>> concentratedMapping = concentrateHighDemandZones(odMapping, demandPattern, CONCENTRATION_THRESHOLD);
        
        reallocateActivitiesToZones(population, zoneCoordinates, concentratedMapping, 0.9);
    }
    
    private Map<Integer, List<Integer>> createODMapping(DemandPattern demandPattern) {
        Map<Integer, List<Integer>> odMapping = new HashMap<>();
        
        for (Map.Entry<String, Double> entry : demandPattern.odMatrix.entrySet()) {
            String odKey = entry.getKey();
            double demand = entry.getValue();
            
            if (demand > 0) {
                String[] parts = odKey.split("_");
                if (parts.length == 2) {
                    try {
                        int origin = Integer.parseInt(parts[0]);
                        int destination = Integer.parseInt(parts[1]);
                        
                        int weight = Math.max(1, (int) Math.round(demand));
                        for (int i = 0; i < weight; i++) {
                            odMapping.computeIfAbsent(origin, k -> new ArrayList<>()).add(destination);
                        }
                    } catch (NumberFormatException e) {
                        // Skip invalid entries
                    }
                }
            }
        }
        
        return odMapping;
    }
    
    private Map<Integer, List<Integer>> concentrateHighDemandZones(Map<Integer, List<Integer>> odMapping, 
                                                                  DemandPattern demandPattern, double threshold) {
        
        Map<Integer, Double> zoneDemandScores = calculateZoneDemandScores(demandPattern);
        
        double maxDemand = zoneDemandScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double demandThreshold = maxDemand * threshold;
        
        Set<Integer> highDemandZones = zoneDemandScores.entrySet().stream()
            .filter(entry -> entry.getValue() >= demandThreshold)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        
        Map<Integer, List<Integer>> concentratedMapping = new HashMap<>();
        
        for (Integer zone : highDemandZones) {
            if (odMapping.containsKey(zone)) {
                List<Integer> destinations = odMapping.get(zone).stream()
                    .filter(highDemandZones::contains)
                    .collect(Collectors.toList());
                
                if (!destinations.isEmpty()) {
                    concentratedMapping.put(zone, destinations);
                }
            }
        }
        
        return concentratedMapping;
    }
    
    private Map<Integer, Double> calculateZoneDemandScores(DemandPattern demandPattern) {
        Map<Integer, Double> zoneScores = new HashMap<>();
        
        for (Map.Entry<String, Double> entry : demandPattern.odMatrix.entrySet()) {
            String odKey = entry.getKey();
            double demand = entry.getValue();
            
            String[] parts = odKey.split("_");
            if (parts.length == 2) {
                try {
                    int origin = Integer.parseInt(parts[0]);
                    int destination = Integer.parseInt(parts[1]);
                    
                    zoneScores.merge(origin, demand, Double::sum);
                    zoneScores.merge(destination, demand, Double::sum);
                } catch (NumberFormatException e) {
                    // Skip invalid entries
                }
            }
        }
        
        return zoneScores;
    }
    
    private void reallocateActivitiesToZones(Population population, Map<Integer, List<Coord>> zoneCoordinates, 
                                           Map<Integer, List<Integer>> odMapping, double allocationProbability) {
        
        List<Integer> availableOrigins = new ArrayList<>(odMapping.keySet());
        if (availableOrigins.isEmpty()) return;
        
        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                List<PlanElement> elements = plan.getPlanElements();
                
                if (elements.size() >= 5) {
                    Activity homeActivity = (Activity) elements.get(0);
                    Activity workActivity = (Activity) elements.get(2);
                    Activity returnHomeActivity = (Activity) elements.get(4);
                    
                    if (random.nextDouble() < allocationProbability) {
                        int originZone = availableOrigins.get(random.nextInt(availableOrigins.size()));
                        
                        List<Integer> destinationZones = odMapping.get(originZone);
                        if (destinationZones != null && !destinationZones.isEmpty()) {
                            int destZone = destinationZones.get(random.nextInt(destinationZones.size()));
                            
                            setActivityCoordinateInZone(homeActivity, originZone, zoneCoordinates);
                            setActivityCoordinateInZone(workActivity, destZone, zoneCoordinates);
                            setActivityCoordinateInZone(returnHomeActivity, originZone, zoneCoordinates);
                        }
                    }
                }
            }
        }
    }
    
    private void setActivityCoordinateInZone(Activity activity, int zone, Map<Integer, List<Coord>> zoneCoordinates) {
        List<Coord> zoneCoords = zoneCoordinates.get(zone);
        if (zoneCoords != null && !zoneCoords.isEmpty()) {
            Coord baseCoord = zoneCoords.get(random.nextInt(zoneCoords.size()));
            
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * ZONE_RADIUS_METERS;
            
            double xOffset = Math.cos(angle) * distance;
            double yOffset = Math.sin(angle) * distance;
            
            Coord variedCoord = new Coord(baseCoord.getX() + xOffset, baseCoord.getY() + yOffset);
            activity.setCoord(variedCoord);
        }
    }
    
    private Map<String, Integer> calculateSpatialDistribution(Population population, 
                                                            Map<Integer, List<Coord>> zoneCoordinates) {
        
        Map<String, Integer> distribution = new HashMap<>();
        
        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                for (PlanElement element : plan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity activity = (Activity) element;
                        String zone = findZoneForCoordinate(activity.getCoord(), zoneCoordinates);
                        if (zone != null) {
                            distribution.merge(zone, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        
        return distribution;
    }
    
    private String findZoneForCoordinate(Coord coord, Map<Integer, List<Coord>> zoneCoordinates) {
        double minDistance = Double.MAX_VALUE;
        Integer nearestZone = null;
        
        for (Map.Entry<Integer, List<Coord>> entry : zoneCoordinates.entrySet()) {
            for (Coord zoneCoord : entry.getValue()) {
                double distance = calculateDistance(coord, zoneCoord);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestZone = entry.getKey();
                }
            }
        }
        
        return nearestZone != null ? nearestZone.toString() : null;
    }
    
    private double calculateDistance(Coord coord1, Coord coord2) {
        double dx = coord1.getX() - coord2.getX();
        double dy = coord1.getY() - coord2.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    private double calculateSpatialFitness(DemandPattern demandPattern, Map<String, Integer> spatialDistribution) {
        Map<String, Double> expectedDistribution = calculateExpectedSpatialDistribution(demandPattern);
        
        double totalSquaredError = 0.0;
        double totalExpected = 0.0;
        
        Set<String> allZones = new HashSet<>();
        allZones.addAll(expectedDistribution.keySet());
        allZones.addAll(spatialDistribution.keySet());
        
        for (String zone : allZones) {
            double expected = expectedDistribution.getOrDefault(zone, 0.0);
            double actual = spatialDistribution.getOrDefault(zone, 0);
            
            totalSquaredError += Math.pow(expected - actual, 2);
            totalExpected += expected;
        }
        
        double rmse = Math.sqrt(totalSquaredError / Math.max(1, allZones.size()));
        double avgExpected = totalExpected / Math.max(1, allZones.size());
        double normalizedRmse = avgExpected > 0 ? rmse / avgExpected : 0.0;
        
        return Math.max(0.0, 1.0 - normalizedRmse);
    }
    
    private Map<String, Double> calculateExpectedSpatialDistribution(DemandPattern demandPattern) {
        Map<String, Double> expectedDistribution = new HashMap<>();
        
        for (Map.Entry<String, Double> entry : demandPattern.odMatrix.entrySet()) {
            String odKey = entry.getKey();
            double demand = entry.getValue();
            
            String[] parts = odKey.split("_");
            if (parts.length == 2) {
                expectedDistribution.merge(parts[0], demand, Double::sum);
                expectedDistribution.merge(parts[1], demand, Double::sum);
            }
        }
        
        return expectedDistribution;
    }
    
    private int countTotalActivities(Population population) {
        int count = 0;
        for (Person person : population.getPersons().values()) {
            Plan plan = person.getSelectedPlan();
            if (plan != null) {
                for (PlanElement element : plan.getPlanElements()) {
                    if (element instanceof Activity) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
    
    private Population clonePopulationStructure(Population originalPopulation) {
        Population clonedPopulation = org.matsim.core.population.PopulationUtils.createPopulation(
            org.matsim.core.config.ConfigUtils.createConfig());
        
        for (org.matsim.api.core.v01.population.Person originalPerson : originalPopulation.getPersons().values()) {
            String newPersonId = "spatial_person_" + originalPerson.getId().toString();
            org.matsim.api.core.v01.population.Person clonedPerson = 
                org.matsim.core.population.PopulationUtils.getFactory().createPerson(
                    org.matsim.api.core.v01.Id.createPersonId(newPersonId));
            
            Plan originalPlan = originalPerson.getSelectedPlan();
            if (originalPlan != null) {
                Plan clonedPlan = org.matsim.core.population.PopulationUtils.getFactory().createPlan();
                
                for (PlanElement element : originalPlan.getPlanElements()) {
                    if (element instanceof Activity) {
                        Activity originalActivity = (Activity) element;
                        Activity clonedActivity = org.matsim.core.population.PopulationUtils.getFactory()
                            .createActivityFromCoord(originalActivity.getType(), originalActivity.getCoord());
                        if (originalActivity.getEndTime().isDefined()) {
                            clonedActivity.setEndTime(originalActivity.getEndTime().seconds());
                        }
                        clonedPlan.addActivity(clonedActivity);
                    } else if (element instanceof Leg) {
                        Leg originalLeg = (Leg) element;
                        Leg clonedLeg = org.matsim.core.population.PopulationUtils.getFactory()
                            .createLeg(originalLeg.getMode());
                        clonedPlan.addLeg(clonedLeg);
                    }
                }
                
                clonedPerson.addPlan(clonedPlan);
                clonedPerson.setSelectedPlan(clonedPlan);
            }
            
            clonedPopulation.addPerson(clonedPerson);
        }
        
        return clonedPopulation;
    }
    
    private String getReallocationMethod(String scenario) {
        switch (scenario) {
            case "S1": return "TEMPORAL_SPATIAL_PROPORTIONAL";
            case "S2": return "STRONG_SPATIAL_CONCENTRATION";
            case "S3": return "COMBINED_SPATIAL_TEMPORAL";
            case "S4": return "REAL_WORLD_SPATIAL_PATTERNS";
            default: return "BASE_ZONE_DISTRIBUTION";
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: VirtualStopNetworkMapper <population_file> <scenario> <multiplier> <rule>");
            System.exit(1);
        }
        
        String populationFile = args[0];
        String scenario = args[1];
        double multiplier = Double.parseDouble(args[2]);
        int rule = Integer.parseInt(args[3]);
        
        try {
            System.out.println("=== Testing Spatial Demand Reallocation ===");
            System.out.println("Scenario: " + scenario + ", Multiplier: " + multiplier + ", Rule: " + rule);
            
            org.matsim.api.core.v01.Scenario matsimScenario = 
                org.matsim.core.scenario.ScenarioUtils.createScenario(org.matsim.core.config.ConfigUtils.createConfig());
            org.matsim.core.population.io.PopulationReader reader = 
                new org.matsim.core.population.io.PopulationReader(matsimScenario);
            reader.readFile(populationFile);
            Population population = matsimScenario.getPopulation();
            
            RulesDataParser.RulesDataSet rulesData = RulesDataParser.loadAllRulesData(
                "data/demands/hwaseong/rules", "data/demands/hwaseong/real_time");
            
            RulesDataParser.DemandPattern demandPattern = rulesData.getDemandPattern(scenario, multiplier, rule);
            List<RulesDataParser.RealTimeTrip> realTimeTrips = rulesData.getRealTimeTrips(scenario);
            
            if (demandPattern == null) {
                System.err.println("Demand pattern not found!");
                System.exit(1);
            }
            
            VirtualStopNetworkMapper mapper = new VirtualStopNetworkMapper(42);
            SpatialReallocationResult result = mapper.reallocateActivities(population, demandPattern, realTimeTrips);
            
            result.printSummary();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
