package org.matsim.maas.rl.state;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.rl.events.RLState;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Manages RL state transitions and tracks state-action-reward sequences
 * for reinforcement learning algorithms in DRT dispatching.
 */
public class RLStateManager {
    
    private RLState currentState;
    private final List<StateActionRecord> stateActionHistory;
    private final Map<Id<Person>, List<StateActionRecord>> personStateHistory;
    
    // System state tracking
    private int activeRequestCount = 0;
    private int availableVehicleCount = 10; // Default fleet size
    private int busyVehicleCount = 0;
    private final Queue<Double> recentWaitTimes = new LinkedList<>();
    private final Queue<Boolean> recentRejections = new LinkedList<>();
    private final Map<Integer, Integer> recentOriginZones = new HashMap<>();
    private final Map<Integer, Integer> recentDestZones = new HashMap<>();
    
    // Configuration
    private static final int RECENT_HISTORY_SIZE = 20;
    private static final double STATE_UPDATE_INTERVAL = 300.0; // 5 minutes
    
    private double lastStateUpdateTime = 0.0;
    
    public RLStateManager() {
        this.stateActionHistory = new ArrayList<>();
        this.personStateHistory = new HashMap<>();
        this.currentState = createInitialState();
        
        System.out.println("RLStateManager: Initialized for RL state tracking");
    }
    
    /**
     * Get current system state
     */
    public RLState getCurrentState(double currentTime) {
        // Update state if enough time has passed
        if (currentTime - lastStateUpdateTime >= STATE_UPDATE_INTERVAL) {
            updateSystemState(currentTime);
            lastStateUpdateTime = currentTime;
        }
        return currentState;
    }
    
    /**
     * Record request submission event
     */
    public void recordRequestSubmission(Id<Person> personId, RLState state, double time) {
        activeRequestCount++;
        
        // Update spatial tracking
        int originZone = extractZoneFromPersonId(personId); // Simplified zone extraction
        recentOriginZones.merge(originZone, 1, Integer::sum);
        
        StateActionRecord record = new StateActionRecord(
            personId, state, "REQUEST_SUBMITTED", 0.0, time
        );
        
        stateActionHistory.add(record);
        personStateHistory.computeIfAbsent(personId, k -> new ArrayList<>()).add(record);
        
        updateSystemState(time);
    }
    
    /**
     * Record scheduling decision
     */
    public void recordSchedulingDecision(Id<Person> personId, RLState state, double reward, double time) {
        activeRequestCount = Math.max(0, activeRequestCount - 1);
        busyVehicleCount++;
        availableVehicleCount = Math.max(0, availableVehicleCount - 1);
        
        StateActionRecord record = new StateActionRecord(
            personId, state, "REQUEST_SCHEDULED", reward, time
        );
        
        stateActionHistory.add(record);
        personStateHistory.computeIfAbsent(personId, k -> new ArrayList<>()).add(record);
        
        updateSystemState(time);
    }
    
    /**
     * Record rejection decision
     */
    public void recordRejection(Id<Person> personId, RLState state, double penalty, double time) {
        activeRequestCount = Math.max(0, activeRequestCount - 1);
        
        // Track rejection
        recentRejections.offer(true);
        if (recentRejections.size() > RECENT_HISTORY_SIZE) {
            recentRejections.poll();
        }
        
        StateActionRecord record = new StateActionRecord(
            personId, state, "REQUEST_REJECTED", penalty, time
        );
        
        stateActionHistory.add(record);
        personStateHistory.computeIfAbsent(personId, k -> new ArrayList<>()).add(record);
        
        updateSystemState(time);
    }
    
    /**
     * Record trip completion
     */
    public void recordTripCompletion(Id<Person> personId, RLState state, double reward, double time) {
        busyVehicleCount = Math.max(0, busyVehicleCount - 1);
        availableVehicleCount++;
        
        // Update destination tracking
        int destZone = extractZoneFromPersonId(personId); // Simplified zone extraction
        recentDestZones.merge(destZone, 1, Integer::sum);
        
        StateActionRecord record = new StateActionRecord(
            personId, state, "TRIP_COMPLETED", reward, time
        );
        
        stateActionHistory.add(record);
        personStateHistory.computeIfAbsent(personId, k -> new ArrayList<>()).add(record);
        
        updateSystemState(time);
    }
    
    /**
     * Update system state based on current conditions
     */
    private void updateSystemState(double currentTime) {
        double avgWaitTime = recentWaitTimes.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        int recentRejectionCount = (int) recentRejections.stream()
            .mapToLong(rejected -> rejected ? 1 : 0)
            .sum();
        
        // Find dominant origin and destination zones
        int dominantOrigin = recentOriginZones.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(1);
        
        int dominantDest = recentDestZones.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(1);
        
        currentState = new RLState(
            currentTime,
            activeRequestCount,
            availableVehicleCount,
            busyVehicleCount,
            avgWaitTime,
            recentRejectionCount,
            dominantOrigin,
            dominantDest
        );
    }
    
    /**
     * Create initial state
     */
    private RLState createInitialState() {
        return new RLState(0.0, 0, availableVehicleCount, 0, 0.0, 0, 1, 1);
    }
    
    /**
     * Extract zone ID from person ID (simplified)
     */
    private int extractZoneFromPersonId(Id<Person> personId) {
        // Simple hash-based zone assignment for demonstration
        // In practice, this would use actual location data
        return Math.abs(personId.toString().hashCode()) % 72 + 1;
    }
    
    /**
     * Add wait time to recent history
     */
    public void addWaitTime(double waitTime) {
        recentWaitTimes.offer(waitTime);
        if (recentWaitTimes.size() > RECENT_HISTORY_SIZE) {
            recentWaitTimes.poll();
        }
    }
    
    /**
     * Export state-action data for RL learning
     */
    public void exportStateActionData(String outputDirectory, int iteration) {
        String filename = outputDirectory + "/rl_state_action_" + iteration + ".csv";
        
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename), 
                                                              StandardOpenOption.CREATE, 
                                                              StandardOpenOption.TRUNCATE_EXISTING)) {
            
            // Write header
            writer.write("person_id,time,action,reward," + String.join(",", RLState.getFeatureNames()) + "\n");
            
            // Write state-action records
            for (StateActionRecord record : stateActionHistory) {
                writer.write(record.toCSVString() + "\n");
            }
            
            System.out.println("RLStateManager: Exported " + stateActionHistory.size() + 
                             " state-action records to " + filename);
            
        } catch (IOException e) {
            System.err.println("Error exporting state-action data: " + e.getMessage());
        }
    }
    
    /**
     * Get state-action history for a specific person
     */
    public List<StateActionRecord> getPersonHistory(Id<Person> personId) {
        return personStateHistory.getOrDefault(personId, Collections.emptyList());
    }
    
    /**
     * Get complete state-action history
     */
    public List<StateActionRecord> getCompleteHistory() {
        return new ArrayList<>(stateActionHistory);
    }
    
    /**
     * Clear history (typically at end of iteration)
     */
    public void clearHistory() {
        stateActionHistory.clear();
        personStateHistory.clear();
        
        // Reset spatial tracking
        recentOriginZones.clear();
        recentDestZones.clear();
    }
    
    /**
     * Get current system metrics
     */
    public SystemMetrics getCurrentMetrics() {
        return new SystemMetrics(
            activeRequestCount,
            availableVehicleCount,
            busyVehicleCount,
            recentWaitTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0),
            (int) recentRejections.stream().mapToLong(rejected -> rejected ? 1 : 0).sum()
        );
    }
    
    /**
     * Simple metrics container
     */
    public static class SystemMetrics {
        public final int activeRequests;
        public final int availableVehicles;
        public final int busyVehicles;
        public final double averageWaitTime;
        public final int recentRejections;
        
        public SystemMetrics(int activeRequests, int availableVehicles, int busyVehicles, 
                           double averageWaitTime, int recentRejections) {
            this.activeRequests = activeRequests;
            this.availableVehicles = availableVehicles;
            this.busyVehicles = busyVehicles;
            this.averageWaitTime = averageWaitTime;
            this.recentRejections = recentRejections;
        }
        
        @Override
        public String toString() {
            return String.format("SystemMetrics{active=%d, avail=%d, busy=%d, avgWait=%.1f, rejections=%d}", 
                               activeRequests, availableVehicles, busyVehicles, averageWaitTime, recentRejections);
        }
    }
}