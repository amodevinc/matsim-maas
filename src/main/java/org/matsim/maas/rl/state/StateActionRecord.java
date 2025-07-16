package org.matsim.maas.rl.state;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.rl.events.RLState;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Record of a state-action-reward tuple for RL learning.
 * Contains the complete information needed for training RL algorithms.
 */
public class StateActionRecord {
    
    private final Id<Person> personId;
    private final RLState state;
    private final String action;
    private final double reward;
    private final double timestamp;
    
    public StateActionRecord(Id<Person> personId, RLState state, String action, double reward, double timestamp) {
        this.personId = personId;
        this.state = state;
        this.action = action;
        this.reward = reward;
        this.timestamp = timestamp;
    }
    
    // Getters
    public Id<Person> getPersonId() { return personId; }
    public RLState getState() { return state; }
    public String getAction() { return action; }
    public double getReward() { return reward; }
    public double getTimestamp() { return timestamp; }
    
    /**
     * Convert to CSV format for export
     */
    public String toCSVString() {
        String stateFeatures = Arrays.stream(state.toFeatureVector())
            .mapToObj(String::valueOf)
            .collect(Collectors.joining(","));
        
        return String.format("%s,%.1f,%s,%.4f,%s", 
                           personId.toString(), timestamp, action, reward, stateFeatures);
    }
    
    /**
     * Get feature vector for ML algorithms
     */
    public double[] getFeatureVector() {
        return state.toFeatureVector();
    }
    
    /**
     * Get action as numeric value for ML algorithms
     */
    public int getActionAsInt() {
        switch (action) {
            case "REQUEST_SUBMITTED": return 0;
            case "REQUEST_SCHEDULED": return 1;
            case "REQUEST_REJECTED": return 2;
            case "TRIP_COMPLETED": return 3;
            default: return -1;
        }
    }
    
    @Override
    public String toString() {
        return String.format("StateActionRecord{person=%s, action=%s, reward=%.3f, time=%.1f}", 
                           personId, action, reward, timestamp);
    }
}