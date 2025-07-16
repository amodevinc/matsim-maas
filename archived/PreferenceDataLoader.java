package org.matsim.maas.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for loading and managing user preference data from CSV files
 * for preference-aware DRT dispatching.
 * 
 * @author MATSim-MaaS Research Team
 */
public class PreferenceDataLoader {
    
    public static class UserPreferences {
        public final int userId;
        public final double accessWeight;
        public final double waitWeight;
        public final double ivtWeight;
        public final double egressWeight;
        
        public UserPreferences(int userId, double accessWeight, double waitWeight, 
                             double ivtWeight, double egressWeight) {
            this.userId = userId;
            this.accessWeight = accessWeight;
            this.waitWeight = waitWeight;
            this.ivtWeight = ivtWeight;
            this.egressWeight = egressWeight;
        }
        
        /**
         * Calculate preference score for a travel option
         * Higher scores indicate less preferred options (more penalty)
         */
        public double calculatePreferenceScore(double accessTime, double waitTime, 
                                             double ivtTime, double egressTime) {
            return accessWeight * accessTime + waitWeight * waitTime + 
                   ivtWeight * ivtTime + egressWeight * egressTime;
        }
    }
    
    public static class ChoiceRecord {
        public final int userId;
        public final int situation;
        public final int choice;
        
        public ChoiceRecord(int userId, int situation, int choice) {
            this.userId = userId;
            this.situation = situation;
            this.choice = choice;
        }
    }
    
    public static class FeatureRecord {
        public final int id;
        public final int situation;
        public final int alternative;
        public final double access;
        public final double wait;
        public final double ivt;
        public final double egress;
        public final double constant;
        public final double linc;
        public final double license;
        
        public FeatureRecord(int id, int situation, int alternative, double access, 
                           double wait, double ivt, double egress, double constant, 
                           double linc, double license) {
            this.id = id;
            this.situation = situation;
            this.alternative = alternative;
            this.access = access;
            this.wait = wait;
            this.ivt = ivt;
            this.egress = egress;
            this.constant = constant;
            this.linc = linc;
            this.license = license;
        }
    }
    
    private Map<Integer, UserPreferences> userPreferences;
    private List<ChoiceRecord> choiceHistory;
    private List<FeatureRecord> features;
    
    public PreferenceDataLoader() {
        this.userPreferences = new HashMap<>();
        this.choiceHistory = new ArrayList<>();
        this.features = new ArrayList<>();
    }
    
    /**
     * Load all preference data from CSV files
     */
    public void loadPreferenceData(String weightsFile, String historyFile, String featuresFile) {
        try {
            loadWeights(weightsFile);
            loadHistory(historyFile);
            loadFeatures(featuresFile);
            System.out.println("Preference data loaded successfully:");
            System.out.println("  - User preferences: " + userPreferences.size() + " users");
            System.out.println("  - Choice history: " + choiceHistory.size() + " records");
            System.out.println("  - Features: " + features.size() + " records");
        } catch (IOException e) {
            System.err.println("Error loading preference data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load user preference weights from CSV
     */
    private void loadWeights(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line = reader.readLine(); // Skip header
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    int userId = Integer.parseInt(parts[0].trim());
                    double accessWeight = Double.parseDouble(parts[1].trim());
                    double waitWeight = Double.parseDouble(parts[2].trim());
                    double ivtWeight = Double.parseDouble(parts[3].trim());
                    double egressWeight = Double.parseDouble(parts[4].trim());
                    
                    userPreferences.put(userId, new UserPreferences(userId, accessWeight, 
                                                                   waitWeight, ivtWeight, egressWeight));
                }
            }
        }
    }
    
    /**
     * Load user choice history from CSV
     */
    private void loadHistory(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line = reader.readLine(); // Skip header
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    int userId = Integer.parseInt(parts[0].trim());
                    int situation = Integer.parseInt(parts[1].trim());
                    int choice = Integer.parseInt(parts[2].trim());
                    
                    choiceHistory.add(new ChoiceRecord(userId, situation, choice));
                }
            }
        }
    }
    
    /**
     * Load feature data from CSV
     */
    private void loadFeatures(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line = reader.readLine(); // Skip header
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 10) {
                    int id = Integer.parseInt(parts[0].trim());
                    int situation = Integer.parseInt(parts[1].trim());
                    int alternative = Integer.parseInt(parts[2].trim());
                    double access = Double.parseDouble(parts[3].trim());
                    double wait = Double.parseDouble(parts[4].trim());
                    double ivt = Double.parseDouble(parts[5].trim());
                    double egress = Double.parseDouble(parts[6].trim());
                    double constant = Double.parseDouble(parts[7].trim());
                    double linc = Double.parseDouble(parts[8].trim());
                    double license = Double.parseDouble(parts[9].trim());
                    
                    features.add(new FeatureRecord(id, situation, alternative, access, wait, 
                                                 ivt, egress, constant, linc, license));
                }
            }
        }
    }
    
    /**
     * Get user preferences by ID
     */
    public UserPreferences getUserPreferences(int userId) {
        return userPreferences.get(userId);
    }
    
    /**
     * Get user preferences by person ID (with fallback to default)
     */
    public UserPreferences getUserPreferences(String personId) {
        try {
            // Extract numeric ID from person ID if possible
            int userId = extractUserIdFromPersonId(personId);
            UserPreferences prefs = userPreferences.get(userId);
            
            if (prefs != null) {
                return prefs;
            }
        } catch (Exception e) {
            // Fall back to default preferences
        }
        
        // Return default preferences if user not found
        return getDefaultPreferences();
    }
    
    /**
     * Extract user ID from MATSim person ID
     */
    private int extractUserIdFromPersonId(String personId) {
        // Handle different person ID formats
        if (personId.contains("_person_")) {
            String[] parts = personId.split("_person_");
            if (parts.length > 1) {
                return Integer.parseInt(parts[1]);
            }
        }
        
        // Try to parse as direct integer
        try {
            return Integer.parseInt(personId);
        } catch (NumberFormatException e) {
            // Use hash-based mapping for consistent assignment
            return Math.abs(personId.hashCode()) % userPreferences.size() + 1;
        }
    }
    
    /**
     * Get default preferences (average of all users)
     */
    public UserPreferences getDefaultPreferences() {
        if (userPreferences.isEmpty()) {
            // Fallback defaults based on typical DRT preferences
            return new UserPreferences(0, -0.1, -0.2, -0.05, -0.1);
        }
        
        double avgAccess = userPreferences.values().stream()
                .mapToDouble(p -> p.accessWeight).average().orElse(-0.1);
        double avgWait = userPreferences.values().stream()
                .mapToDouble(p -> p.waitWeight).average().orElse(-0.2);
        double avgIvt = userPreferences.values().stream()
                .mapToDouble(p -> p.ivtWeight).average().orElse(-0.05);
        double avgEgress = userPreferences.values().stream()
                .mapToDouble(p -> p.egressWeight).average().orElse(-0.1);
        
        return new UserPreferences(0, avgAccess, avgWait, avgIvt, avgEgress);
    }
    
    /**
     * Get all user preferences
     */
    public Map<Integer, UserPreferences> getAllUserPreferences() {
        return new HashMap<>(userPreferences);
    }
    
    /**
     * Get choice history
     */
    public List<ChoiceRecord> getChoiceHistory() {
        return new ArrayList<>(choiceHistory);
    }
    
    /**
     * Get features
     */
    public List<FeatureRecord> getFeatures() {
        return new ArrayList<>(features);
    }
} 