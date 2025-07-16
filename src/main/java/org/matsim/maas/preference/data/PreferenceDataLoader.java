package org.matsim.maas.preference.data;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.preference.data.UserPreferenceStore.UserPreferenceData;
import org.matsim.maas.preference.data.UserPreferenceStore.UserChoiceRecord;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loader for user preference data from CSV files.
 * Handles loading of utility weights and choice history for preference-aware DRT algorithms.
 */
public class PreferenceDataLoader {
    
    private static final String WEIGHTS_FILE = "data/user_preference/weights.csv";
    private static final String FEATURES_FILE = "data/user_preference/features.csv";
    private static final String HISTORY_FILE = "data/user_preference/user_history.csv";
    
    /**
     * Load all preference data into a UserPreferenceStore
     */
    public static UserPreferenceStore loadAllPreferenceData() {
        UserPreferenceStore store = new UserPreferenceStore();
        
        // Load utility weights
        loadUserWeights(store);
        
        // Load choice history
        loadUserChoiceHistory(store);
        
        System.out.println("PreferenceDataLoader: Loaded preference data for " + 
                          store.getTotalUsers() + " users");
        System.out.println("PreferenceDataLoader: Loaded choice history for " + 
                          store.getTotalUsersWithHistory() + " users");
        
        return store;
    }
    
    /**
     * Load user utility weights from weights.csv
     */
    private static void loadUserWeights(UserPreferenceStore store) {
        try (BufferedReader reader = new BufferedReader(new FileReader(WEIGHTS_FILE))) {
            String headerLine = reader.readLine(); // Skip header
            String line;
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    int userId = Integer.parseInt(parts[0]);
                    double accessWeight = Double.parseDouble(parts[1]);
                    double waitWeight = Double.parseDouble(parts[2]);
                    double ivtWeight = Double.parseDouble(parts[3]);
                    double egressWeight = Double.parseDouble(parts[4]);
                    
                    UserPreferenceData prefData = new UserPreferenceData(
                        userId, accessWeight, waitWeight, ivtWeight, egressWeight);
                    
                    Id<Person> personId = Id.createPersonId(userId);
                    store.addUserPreference(personId, prefData);
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading user weights: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load user choice history from user_history.csv
     */
    private static void loadUserChoiceHistory(UserPreferenceStore store) {
        try (BufferedReader reader = new BufferedReader(new FileReader(HISTORY_FILE))) {
            String headerLine = reader.readLine(); // Skip header
            String line;
            
            Map<Integer, List<UserChoiceRecord>> userChoiceMap = new HashMap<>();
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    // Remove BOM if present
                    String userIdStr = parts[0].replace("\uFEFF", "");
                    int userId = Integer.parseInt(userIdStr);
                    int situationId = Integer.parseInt(parts[1]);
                    int choiceId = Integer.parseInt(parts[2]);
                    
                    UserChoiceRecord record = new UserChoiceRecord(userId, situationId, choiceId);
                    
                    userChoiceMap.computeIfAbsent(userId, k -> new ArrayList<>()).add(record);
                }
            }
            
            // Add choice history to store
            for (Map.Entry<Integer, List<UserChoiceRecord>> entry : userChoiceMap.entrySet()) {
                Id<Person> personId = Id.createPersonId(entry.getKey());
                store.addUserChoiceHistory(personId, entry.getValue());
            }
            
        } catch (IOException e) {
            System.err.println("Error loading user choice history: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load feature data for choice modeling (optional - for advanced RL)
     */
    public static Map<String, List<FeatureRecord>> loadFeatureData() {
        Map<String, List<FeatureRecord>> featureMap = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(FEATURES_FILE))) {
            String headerLine = reader.readLine(); // Skip header
            String line;
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 10) {
                    // Remove BOM if present
                    String userIdStr = parts[0].replace("\uFEFF", "");
                    int userId = Integer.parseInt(userIdStr);
                    int situationId = Integer.parseInt(parts[1]);
                    int alternativeId = Integer.parseInt(parts[2]);
                    double access = Double.parseDouble(parts[3]);
                    double wait = Double.parseDouble(parts[4]);
                    double ivt = Double.parseDouble(parts[5]);
                    double egress = Double.parseDouble(parts[6]);
                    double constant = Double.parseDouble(parts[7]);
                    int linc = Integer.parseInt(parts[8]);
                    int license = Integer.parseInt(parts[9]);
                    
                    FeatureRecord record = new FeatureRecord(
                        userId, situationId, alternativeId, access, wait, ivt, egress, 
                        constant, linc, license);
                    
                    String key = userId + "_" + situationId + "_" + alternativeId;
                    featureMap.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error loading feature data: " + e.getMessage());
            e.printStackTrace();
        }
        
        return featureMap;
    }
    
    /**
     * Feature record from features.csv for choice modeling
     */
    public static class FeatureRecord {
        private final int userId;
        private final int situationId;
        private final int alternativeId;
        private final double access;
        private final double wait;
        private final double ivt;
        private final double egress;
        private final double constant;
        private final int linc;
        private final int license;
        
        public FeatureRecord(int userId, int situationId, int alternativeId, 
                           double access, double wait, double ivt, double egress, 
                           double constant, int linc, int license) {
            this.userId = userId;
            this.situationId = situationId;
            this.alternativeId = alternativeId;
            this.access = access;
            this.wait = wait;
            this.ivt = ivt;
            this.egress = egress;
            this.constant = constant;
            this.linc = linc;
            this.license = license;
        }
        
        // Getters
        public int getUserId() { return userId; }
        public int getSituationId() { return situationId; }
        public int getAlternativeId() { return alternativeId; }
        public double getAccess() { return access; }
        public double getWait() { return wait; }
        public double getIvt() { return ivt; }
        public double getEgress() { return egress; }
        public double getConstant() { return constant; }
        public int getLinc() { return linc; }
        public int getLicense() { return license; }
        
        @Override
        public String toString() {
            return String.format("FeatureRecord{userId=%d, situation=%d, alternative=%d, access=%.1f, wait=%.1f, ivt=%.1f, egress=%.1f}", 
                               userId, situationId, alternativeId, access, wait, ivt, egress);
        }
    }
}