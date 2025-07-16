package org.matsim.maas.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates MATSim DRT fleet vehicle XML files with various distribution strategies.
 * Supports different fleet sizes, vehicle capacities, and deployment strategies.
 */
public class FleetVehicleGenerator {
    
    public enum DistributionStrategy {
        RANDOM,           // Random distribution across all DRT-enabled links
        STOP_BASED,       // Deploy near virtual stops
        DEMAND_BASED,     // Deploy based on demand density (origin locations)
        CENTRALIZED       // Deploy in city center
    }
    
    private final Network network;
    private final List<Link> drtLinks;
    private final Random random;
    
    public FleetVehicleGenerator(String networkFilePath) throws IOException {
        this.network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(networkFilePath);
        
        // Filter links that support DRT mode
        this.drtLinks = network.getLinks().values().stream()
                .filter(link -> link.getAllowedModes().contains("drt") || 
                               link.getAllowedModes().contains("car"))
                .collect(Collectors.toList());
        
        this.random = new Random(42); // Fixed seed for reproducibility
        
        System.out.println("Loaded network with " + network.getLinks().size() + " total links");
        System.out.println("Found " + drtLinks.size() + " DRT-compatible links");
    }
    
    /**
     * Generate fleet vehicles XML file
     */
    public void generateFleet(String outputPath, int fleetSize, int vehicleCapacity, 
                            DistributionStrategy strategy, double operatingHours) throws IOException {
        
        List<Link> deploymentLinks = selectDeploymentLinks(strategy, fleetSize);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            // Write XML header
            writer.write("<!DOCTYPE vehicles SYSTEM \"http://matsim.org/files/dtd/dvrp_vehicles_v1.dtd\">\n\n");
            writer.write("<vehicles>\n");
            
            // Generate vehicles
            for (int i = 0; i < fleetSize; i++) {
                Link startLink = deploymentLinks.get(i % deploymentLinks.size());
                String vehicleId = "drt_" + i;
                double startTime = 0.0; // Start at midnight
                double endTime = operatingHours * 3600.0; // Convert hours to seconds
                
                writer.write(String.format(
                    "        <vehicle id=\"%s\" start_link=\"%s\" t_0=\"%.1f\" t_1=\"%.1f\" capacity=\"%d\"/>\n",
                    vehicleId, startLink.getId().toString(), startTime, endTime, vehicleCapacity
                ));
            }
            
            writer.write("</vehicles>\n");
        }
        
        System.out.println("Generated " + fleetSize + " vehicles with capacity " + vehicleCapacity);
        System.out.println("Distribution strategy: " + strategy);
        System.out.println("Operating hours: " + operatingHours);
        System.out.println("Fleet file written to: " + outputPath);
    }
    
    /**
     * Select deployment links based on strategy
     */
    private List<Link> selectDeploymentLinks(DistributionStrategy strategy, int fleetSize) {
        List<Link> selectedLinks = new ArrayList<>();
        
        switch (strategy) {
            case RANDOM:
                selectedLinks = selectRandomLinks(fleetSize);
                break;
                
            case STOP_BASED:
                selectedLinks = selectStopBasedLinks(fleetSize);
                break;
                
            case DEMAND_BASED:
                selectedLinks = selectDemandBasedLinks(fleetSize);
                break;
                
            case CENTRALIZED:
                selectedLinks = selectCentralizedLinks(fleetSize);
                break;
                
            default:
                selectedLinks = selectRandomLinks(fleetSize);
        }
        
        return selectedLinks;
    }
    
    /**
     * Random distribution across all DRT links
     */
    private List<Link> selectRandomLinks(int fleetSize) {
        List<Link> selected = new ArrayList<>();
        List<Link> shuffledLinks = new ArrayList<>(drtLinks);
        Collections.shuffle(shuffledLinks, random);
        
        for (int i = 0; i < fleetSize; i++) {
            selected.add(shuffledLinks.get(i % shuffledLinks.size()));
        }
        
        System.out.println("Random deployment: " + selected.size() + " deployment locations");
        return selected;
    }
    
    /**
     * Deploy near high-connectivity nodes (simulating stop-based strategy)
     */
    private List<Link> selectStopBasedLinks(int fleetSize) {
        // Sort links by their node connectivity (proxy for stop importance)
        List<Link> sortedLinks = drtLinks.stream()
                .sorted((l1, l2) -> {
                    int conn1 = l1.getFromNode().getInLinks().size() + l1.getFromNode().getOutLinks().size();
                    int conn2 = l2.getFromNode().getInLinks().size() + l2.getFromNode().getOutLinks().size();
                    return Integer.compare(conn2, conn1); // Descending order
                })
                .collect(Collectors.toList());
        
        List<Link> selected = new ArrayList<>();
        for (int i = 0; i < fleetSize; i++) {
            selected.add(sortedLinks.get(i % Math.min(sortedLinks.size(), 50))); // Top 50 most connected
        }
        
        System.out.println("Stop-based deployment: Using " + Math.min(50, sortedLinks.size()) + " high-connectivity locations");
        return selected;
    }
    
    /**
     * Deploy based on demand patterns (simplified to geographic spread)
     */
    private List<Link> selectDemandBasedLinks(int fleetSize) {
        // For now, use geographic distribution as proxy for demand
        // In a real implementation, this would use actual demand data
        return selectGeographicSpread(fleetSize);
    }
    
    /**
     * Deploy in centralized locations (network center)
     */
    private List<Link> selectCentralizedLinks(int fleetSize) {
        // Find network centroid
        double centerX = drtLinks.stream()
                .mapToDouble(link -> (link.getFromNode().getCoord().getX() + 
                                    link.getToNode().getCoord().getX()) / 2.0)
                .average().orElse(0.0);
        
        double centerY = drtLinks.stream()
                .mapToDouble(link -> (link.getFromNode().getCoord().getY() + 
                                    link.getToNode().getCoord().getY()) / 2.0)
                .average().orElse(0.0);
        
        // Sort links by distance to center
        List<Link> sortedLinks = drtLinks.stream()
                .sorted((l1, l2) -> {
                    double dist1 = calculateDistance(l1, centerX, centerY);
                    double dist2 = calculateDistance(l2, centerX, centerY);
                    return Double.compare(dist1, dist2);
                })
                .collect(Collectors.toList());
        
        List<Link> selected = new ArrayList<>();
        for (int i = 0; i < fleetSize; i++) {
            selected.add(sortedLinks.get(i % Math.min(sortedLinks.size(), 20))); // Central 20 links
        }
        
        System.out.println("Centralized deployment: Center at (" + Math.round(centerX*1000)/1000.0 + 
                          ", " + Math.round(centerY*1000)/1000.0 + ")");
        return selected;
    }
    
    /**
     * Geographic spread deployment
     */
    private List<Link> selectGeographicSpread(int fleetSize) {
        if (fleetSize <= 1) {
            return selectRandomLinks(fleetSize);
        }
        
        List<Link> selected = new ArrayList<>();
        List<Link> remaining = new ArrayList<>(drtLinks);
        
        // Start with a random link
        Link first = remaining.get(random.nextInt(remaining.size()));
        selected.add(first);
        remaining.remove(first);
        
        // Greedily select links that are far from already selected ones
        while (selected.size() < fleetSize && !remaining.isEmpty()) {
            Link best = null;
            double maxMinDistance = 0;
            
            for (Link candidate : remaining) {
                double minDistance = Double.MAX_VALUE;
                for (Link existing : selected) {
                    double dist = calculateDistance(candidate, existing);
                    minDistance = Math.min(minDistance, dist);
                }
                if (minDistance > maxMinDistance) {
                    maxMinDistance = minDistance;
                    best = candidate;
                }
            }
            
            if (best != null) {
                selected.add(best);
                remaining.remove(best);
            } else {
                break;
            }
        }
        
        System.out.println("Geographic spread: " + selected.size() + " well-distributed locations");
        return selected;
    }
    
    /**
     * Calculate distance between a link and a point
     */
    private double calculateDistance(Link link, double x, double y) {
        double linkX = (link.getFromNode().getCoord().getX() + link.getToNode().getCoord().getX()) / 2.0;
        double linkY = (link.getFromNode().getCoord().getY() + link.getToNode().getCoord().getY()) / 2.0;
        
        return Math.sqrt(Math.pow(linkX - x, 2) + Math.pow(linkY - y, 2));
    }
    
    /**
     * Calculate distance between two links
     */
    private double calculateDistance(Link link1, Link link2) {
        double x1 = (link1.getFromNode().getCoord().getX() + link1.getToNode().getCoord().getX()) / 2.0;
        double y1 = (link1.getFromNode().getCoord().getY() + link1.getToNode().getCoord().getY()) / 2.0;
        double x2 = (link2.getFromNode().getCoord().getX() + link2.getToNode().getCoord().getX()) / 2.0;
        double y2 = (link2.getFromNode().getCoord().getY() + link2.getToNode().getCoord().getY()) / 2.0;
        
        return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }
    
    /**
     * Generate fleet statistics
     */
    public void generateFleetStats(String fleetFilePath) {
        // This could be implemented to analyze existing fleet files
        System.out.println("Fleet statistics analysis not yet implemented");
    }
    
    /**
     * Main method for command-line usage
     */
    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: FleetVehicleGenerator <networkFile> <outputFile> <fleetSize> <capacity> <strategy> [operatingHours]");
            System.err.println("  networkFile: MATSim network XML file");
            System.err.println("  outputFile: Output vehicles XML file");
            System.err.println("  fleetSize: Number of vehicles to generate");
            System.err.println("  capacity: Vehicle capacity (passengers)");
            System.err.println("  strategy: RANDOM, STOP_BASED, DEMAND_BASED, or CENTRALIZED");
            System.err.println("  operatingHours: Operating hours (default: 24)");
            return;
        }
        
        String networkFile = args[0];
        String outputFile = args[1];
        int fleetSize = Integer.parseInt(args[2]);
        int capacity = Integer.parseInt(args[3]);
        DistributionStrategy strategy = DistributionStrategy.valueOf(args[4].toUpperCase());
        double operatingHours = args.length > 5 ? Double.parseDouble(args[5]) : 24.0;
        
        try {
            FleetVehicleGenerator generator = new FleetVehicleGenerator(networkFile);
            generator.generateFleet(outputFile, fleetSize, capacity, strategy, operatingHours);
            
            System.out.println("Fleet generation completed successfully!");
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error during fleet generation: " + e.getMessage());
        }
    }
} 