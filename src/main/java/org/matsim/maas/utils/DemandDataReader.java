package org.matsim.maas.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads demand data from CSV files in the Hwaseong real-time demand format.
 * Handles coordinate transformations and parsing of all demand request data.
 */
public class DemandDataReader {
    
    private static final Logger log = LogManager.getLogger(DemandDataReader.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final CoordinateTransformationUtil coordTransform;
    
    public DemandDataReader() {
        this.coordTransform = new CoordinateTransformationUtil();
    }
    
    /**
     * Read demand requests from a real-time demand CSV file.
     * 
     * @param demandFilePath Path to the demand CSV file
     * @return List of DemandRequest objects
     * @throws IOException If file cannot be read
     */
    public List<DemandRequest> readDemandFile(Path demandFilePath) throws IOException {
        List<DemandRequest> requests = new ArrayList<>();
        
        log.info("Reading demand data from: {}", demandFilePath);
        
        try (BufferedReader reader = Files.newBufferedReader(demandFilePath)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("Empty demand file: " + demandFilePath);
            }
            
            // Validate header format
            validateHeader(headerLine);
            
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    DemandRequest request = parseDemandLine(line);
                    requests.add(request);
                } catch (Exception e) {
                    log.warn("Error parsing line {} in file {}: {}", lineNumber, demandFilePath, e.getMessage());
                    // Continue processing other lines
                }
            }
        }
        
        log.info("Successfully read {} demand requests from {}", requests.size(), demandFilePath);
        return requests;
    }
    
    /**
     * Validate that the CSV header matches the expected format.
     */
    private void validateHeader(String headerLine) throws IOException {
        String expectedHeader = "idx,o,d,hour,o_h3,d_h3,o_x,o_y,d_x,d_y,time";
        if (!headerLine.trim().equals(expectedHeader)) {
            throw new IOException("Invalid header format. Expected: " + expectedHeader + 
                                ", but got: " + headerLine);
        }
    }
    
    /**
     * Parse a single line of demand data into a DemandRequest object.
     */
    private DemandRequest parseDemandLine(String line) {
        String[] parts = line.split(",");
        if (parts.length != 11) {
            throw new IllegalArgumentException("Invalid line format. Expected 11 fields, got " + parts.length);
        }
        
        try {
            int idx = Integer.parseInt(parts[0].trim());
            int originZone = Integer.parseInt(parts[1].trim());
            int destinationZone = Integer.parseInt(parts[2].trim());
            int hour = Integer.parseInt(parts[3].trim());
            String originH3 = parts[4].trim();
            String destinationH3 = parts[5].trim();
            double originLon = Double.parseDouble(parts[6].trim());
            double originLat = Double.parseDouble(parts[7].trim());
            double destLon = Double.parseDouble(parts[8].trim());
            double destLat = Double.parseDouble(parts[9].trim());
            LocalDateTime requestTime = LocalDateTime.parse(parts[10].trim(), TIME_FORMATTER);
            
            return new DemandRequest(idx, originZone, destinationZone, hour,
                                   originH3, destinationH3,
                                   originLon, originLat, destLon, destLat,
                                   requestTime, coordTransform);
                                   
        } catch (NumberFormatException | java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Error parsing fields in line: " + line, e);
        }
    }
    
    /**
     * Read all demand files from a directory.
     * 
     * @param demandDirectory Directory containing demand CSV files
     * @return List of all demand requests from all files
     * @throws IOException If directory cannot be read
     */
    public List<DemandRequest> readAllDemandFiles(Path demandDirectory) throws IOException {
        List<DemandRequest> allRequests = new ArrayList<>();
        
        try {
            Files.list(demandDirectory)
                 .filter(path -> path.toString().endsWith(".csv"))
                 .filter(path -> path.getFileName().toString().contains("real_time"))
                 .forEach(path -> {
                     try {
                         List<DemandRequest> requests = readDemandFile(path);
                         allRequests.addAll(requests);
                         log.info("Added {} requests from {}", requests.size(), path.getFileName());
                     } catch (IOException e) {
                         log.error("Failed to read demand file {}: {}", path, e.getMessage());
                     }
                 });
        } catch (IOException e) {
            log.error("Failed to list files in directory {}: {}", demandDirectory, e.getMessage());
            throw e;
        }
        
        log.info("Total demand requests loaded: {}", allRequests.size());
        return allRequests;
    }
    
    /**
     * Filter demand requests by scenario name.
     * 
     * @param demandDirectory Directory containing demand files
     * @param scenarioName Scenario name (e.g., "base", "S1", "S2", etc.)
     * @return List of demand requests for the specified scenario
     * @throws IOException If files cannot be read
     */
    public List<DemandRequest> readDemandFilesByScenario(Path demandDirectory, String scenarioName) throws IOException {
        String filename = String.format("valid_requests_%s_real_time.csv", scenarioName);
        Path demandFile = demandDirectory.resolve(filename);
        
        if (!Files.exists(demandFile)) {
            throw new IOException("Demand file not found: " + demandFile);
        }
        
        return readDemandFile(demandFile);
    }
}