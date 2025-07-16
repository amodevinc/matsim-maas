package org.matsim.maas.preference.data;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Store for user preference data including utility weights and choice history.
 * Provides efficient access to preference data for RL-driven dispatching algorithms.
 */
public class UserPreferenceStore {
    
    private final Map<Id<Person>, UserPreferenceData> userPreferences;
    private final Map<Id<Person>, List<UserChoiceRecord>> userChoiceHistory;
    
    public UserPreferenceStore() {
        this.userPreferences = new HashMap<>();
        this.userChoiceHistory = new HashMap<>();
    }
    
    /**
     * Add user preference data
     */
    public void addUserPreference(Id<Person> personId, UserPreferenceData preferenceData) {
        userPreferences.put(personId, preferenceData);
    }
    
    /**
     * Get user preference data
     */
    public UserPreferenceData getUserPreference(Id<Person> personId) {
        return userPreferences.get(personId);
    }
    
    /**
     * Check if user has preference data
     */
    public boolean hasUserPreference(Id<Person> personId) {
        return userPreferences.containsKey(personId);
    }
    
    /**
     * Add choice history for a user
     */
    public void addUserChoiceHistory(Id<Person> personId, List<UserChoiceRecord> choiceHistory) {
        userChoiceHistory.put(personId, choiceHistory);
    }
    
    /**
     * Get choice history for a user
     */
    public List<UserChoiceRecord> getUserChoiceHistory(Id<Person> personId) {
        return userChoiceHistory.getOrDefault(personId, Collections.emptyList());
    }
    
    /**
     * Get all user IDs with preference data
     */
    public Iterable<Id<Person>> getAllUserIds() {
        return userPreferences.keySet();
    }
    
    /**
     * Get total number of users with preference data
     */
    public int getTotalUsers() {
        return userPreferences.size();
    }
    
    /**
     * Get total number of users with choice history
     */
    public int getTotalUsersWithHistory() {
        return userChoiceHistory.size();
    }
    
    /**
     * Clear all preference data
     */
    public void clear() {
        userPreferences.clear();
        userChoiceHistory.clear();
    }
    
    /**
     * User preference data containing utility weights for different cost components
     */
    public static class UserPreferenceData {
        private final int userId;
        private final double accessWeight;
        private final double waitWeight;
        private final double ivtWeight;  // In-Vehicle Time
        private final double egressWeight;
        
        public UserPreferenceData(int userId, double accessWeight, double waitWeight, 
                                 double ivtWeight, double egressWeight) {
            this.userId = userId;
            this.accessWeight = accessWeight;
            this.waitWeight = waitWeight;
            this.ivtWeight = ivtWeight;
            this.egressWeight = egressWeight;
        }
        
        public int getUserId() { return userId; }
        public double getAccessWeight() { return accessWeight; }
        public double getWaitWeight() { return waitWeight; }
        public double getIvtWeight() { return ivtWeight; }
        public double getEgressWeight() { return egressWeight; }
        
        /**
         * Calculate utility score for a given set of cost components
         */
        public double calculateUtility(double accessTime, double waitTime, 
                                     double ivtTime, double egressTime) {
            return accessWeight * accessTime + 
                   waitWeight * waitTime + 
                   ivtWeight * ivtTime + 
                   egressWeight * egressTime;
        }
        
        @Override
        public String toString() {
            return String.format("UserPreferenceData{userId=%d, access=%.3f, wait=%.3f, ivt=%.3f, egress=%.3f}", 
                               userId, accessWeight, waitWeight, ivtWeight, egressWeight);
        }
    }
    
    /**
     * Record of a user's choice in a specific situation
     */
    public static class UserChoiceRecord {
        private final int userId;
        private final int situationId;
        private final int choiceId;
        
        public UserChoiceRecord(int userId, int situationId, int choiceId) {
            this.userId = userId;
            this.situationId = situationId;
            this.choiceId = choiceId;
        }
        
        public int getUserId() { return userId; }
        public int getSituationId() { return situationId; }
        public int getChoiceId() { return choiceId; }
        
        @Override
        public String toString() {
            return String.format("UserChoiceRecord{userId=%d, situation=%d, choice=%d}", 
                               userId, situationId, choiceId);
        }
    }
}