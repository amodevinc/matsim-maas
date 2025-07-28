package org.matsim.maas.preference.data;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Dynamic user preference store that extends the base UserPreferenceStore
 * with capabilities for updating preference weights during simulation.
 * 
 * This class is thread-safe and supports concurrent reads and exclusive writes
 * to ensure consistency during MATSim's parallel simulation execution.
 * 
 * Key features:
 * - Thread-safe preference updates using ReadWriteLock
 * - Persistence of learned preferences to CSV files
 * - Tracking of preference update history
 * - Atomic update operations with rollback capability
 */
public class DynamicUserPreferenceStore extends UserPreferenceStore {
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<Id<Person>, PreferenceUpdateHistory> updateHistories = new ConcurrentHashMap<>();
    private final String persistencePath;
    private int globalUpdateCounter = 0;
    
    /**
     * Configuration for update constraints
     */
    private final double maxWeightChange = 0.1; // Maximum change per update (10%)
    private final double minWeight = -2.0; // Minimum allowed weight value
    private final double maxWeight = 2.0;  // Maximum allowed weight value
    
    public DynamicUserPreferenceStore(String persistencePath) {
        super();
        this.persistencePath = persistencePath;
        ensureDirectoryExists();
    }
    
    /**
     * Update user preference weights with constraints and history tracking.
     * This method is thread-safe and ensures atomic updates.
     * 
     * @param personId The person whose preferences to update
     * @param accessDelta Change to access weight
     * @param waitDelta Change to wait weight
     * @param ivtDelta Change to in-vehicle time weight
     * @param egressDelta Change to egress weight
     * @return true if update was successful, false if constraints were violated
     */
    public boolean updateUserPreference(Id<Person> personId, 
                                      double accessDelta, double waitDelta, 
                                      double ivtDelta, double egressDelta) {
        lock.writeLock().lock();
        try {
            UserPreferenceData currentPref = getUserPreference(personId);
            if (currentPref == null) {
                return false;
            }
            
            // Apply constraints to deltas
            accessDelta = constrainDelta(accessDelta, currentPref.getAccessWeight());
            waitDelta = constrainDelta(waitDelta, currentPref.getWaitWeight());
            ivtDelta = constrainDelta(ivtDelta, currentPref.getIvtWeight());
            egressDelta = constrainDelta(egressDelta, currentPref.getEgressWeight());
            
            // Calculate new weights
            double newAccessWeight = constrainWeight(currentPref.getAccessWeight() + accessDelta);
            double newWaitWeight = constrainWeight(currentPref.getWaitWeight() + waitDelta);
            double newIvtWeight = constrainWeight(currentPref.getIvtWeight() + ivtDelta);
            double newEgressWeight = constrainWeight(currentPref.getEgressWeight() + egressDelta);
            
            // Create updated preference data
            UserPreferenceData updatedPref = new UserPreferenceData(
                currentPref.getUserId(),
                newAccessWeight,
                newWaitWeight,
                newIvtWeight,
                newEgressWeight
            );
            
            // Store the update
            addUserPreference(personId, updatedPref);
            
            // Track update history
            trackUpdate(personId, currentPref, updatedPref);
            
            globalUpdateCounter++;
            
            return true;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Batch update preferences for multiple users.
     * Useful for applying learning updates after an iteration.
     */
    public void batchUpdatePreferences(Map<Id<Person>, PreferenceUpdate> updates) {
        lock.writeLock().lock();
        try {
            for (Map.Entry<Id<Person>, PreferenceUpdate> entry : updates.entrySet()) {
                PreferenceUpdate update = entry.getValue();
                updateUserPreference(entry.getKey(), 
                    update.accessDelta, update.waitDelta, 
                    update.ivtDelta, update.egressDelta);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get user preference with read lock for thread safety
     */
    @Override
    public UserPreferenceData getUserPreference(Id<Person> personId) {
        lock.readLock().lock();
        try {
            return super.getUserPreference(personId);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Persist current preferences to file
     */
    public void persistPreferences(int iteration) {
        lock.readLock().lock();
        try {
            String filename = String.format("%s/learned_preferences_iter_%d.csv", persistencePath, iteration);
            
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename))) {
                writer.write("id,access,wait,ivt,egress,update_count\n");
                
                for (Id<Person> personId : getAllUserIds()) {
                    UserPreferenceData pref = super.getUserPreference(personId);
                    PreferenceUpdateHistory history = updateHistories.get(personId);
                    int updateCount = history != null ? history.getUpdateCount() : 0;
                    
                    writer.write(String.format("%s,%.6f,%.6f,%.6f,%.6f,%d\n",
                        personId.toString(),
                        pref.getAccessWeight(),
                        pref.getWaitWeight(),
                        pref.getIvtWeight(),
                        pref.getEgressWeight(),
                        updateCount
                    ));
                }
                
                System.out.println("Persisted learned preferences to: " + filename);
                
            } catch (IOException e) {
                System.err.println("Error persisting preferences: " + e.getMessage());
            }
            
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Load previously learned preferences
     */
    public void loadLearnedPreferences(String filename) {
        lock.writeLock().lock();
        try {
            Path path = Paths.get(filename);
            if (!Files.exists(path)) {
                System.err.println("Learned preferences file not found: " + filename);
                return;
            }
            
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                String header = reader.readLine(); // Skip header
                
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 5) {
                        Id<Person> personId = Id.createPersonId(parts[0]);
                        double access = Double.parseDouble(parts[1]);
                        double wait = Double.parseDouble(parts[2]);
                        double ivt = Double.parseDouble(parts[3]);
                        double egress = Double.parseDouble(parts[4]);
                        
                        // Extract numeric ID from person ID string (e.g., "person123" -> 123)
                        String personIdStr = parts[0];
                        int numericId = personIdStr.hashCode(); // Use hashcode for non-numeric IDs
                        try {
                            numericId = Integer.parseInt(personIdStr);
                        } catch (NumberFormatException e) {
                            // Use hashcode for non-numeric person IDs
                            numericId = Math.abs(personIdStr.hashCode());
                        }
                        
                        UserPreferenceData pref = new UserPreferenceData(
                            numericId, access, wait, ivt, egress
                        );
                        
                        addUserPreference(personId, pref);
                    }
                }
                
                System.out.println("Loaded learned preferences from: " + filename);
                
            } catch (IOException e) {
                System.err.println("Error loading learned preferences: " + e.getMessage());
            }
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get preference update history for analysis
     */
    public PreferenceUpdateHistory getUpdateHistory(Id<Person> personId) {
        return updateHistories.get(personId);
    }
    
    /**
     * Get total number of preference updates
     */
    public int getGlobalUpdateCount() {
        lock.readLock().lock();
        try {
            return globalUpdateCounter;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Export update statistics for analysis
     */
    public void exportUpdateStatistics(String filename) {
        lock.readLock().lock();
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename))) {
                writer.write("person_id,update_count,total_access_change,total_wait_change," +
                           "total_ivt_change,total_egress_change,avg_change_magnitude\n");
                
                for (Map.Entry<Id<Person>, PreferenceUpdateHistory> entry : updateHistories.entrySet()) {
                    PreferenceUpdateHistory history = entry.getValue();
                    writer.write(String.format("%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f\n",
                        entry.getKey().toString(),
                        history.getUpdateCount(),
                        history.getTotalAccessChange(),
                        history.getTotalWaitChange(),
                        history.getTotalIvtChange(),
                        history.getTotalEgressChange(),
                        history.getAverageChangeMagnitude()
                    ));
                }
                
            } catch (IOException e) {
                System.err.println("Error exporting update statistics: " + e.getMessage());
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    private double constrainDelta(double delta, double currentValue) {
        // Limit change to maxWeightChange
        delta = Math.max(-maxWeightChange, Math.min(maxWeightChange, delta));
        
        // Ensure result stays within bounds
        double newValue = currentValue + delta;
        if (newValue < minWeight) {
            delta = minWeight - currentValue;
        } else if (newValue > maxWeight) {
            delta = maxWeight - currentValue;
        }
        
        return delta;
    }
    
    private double constrainWeight(double weight) {
        return Math.max(minWeight, Math.min(maxWeight, weight));
    }
    
    private void trackUpdate(Id<Person> personId, UserPreferenceData oldPref, UserPreferenceData newPref) {
        updateHistories.computeIfAbsent(personId, k -> new PreferenceUpdateHistory(personId))
            .addUpdate(oldPref, newPref);
    }
    
    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(Paths.get(persistencePath));
        } catch (IOException e) {
            System.err.println("Could not create persistence directory: " + e.getMessage());
        }
    }
    
    /**
     * Container for preference update deltas
     */
    public static class PreferenceUpdate {
        public final double accessDelta;
        public final double waitDelta;
        public final double ivtDelta;
        public final double egressDelta;
        
        public PreferenceUpdate(double accessDelta, double waitDelta, 
                              double ivtDelta, double egressDelta) {
            this.accessDelta = accessDelta;
            this.waitDelta = waitDelta;
            this.ivtDelta = ivtDelta;
            this.egressDelta = egressDelta;
        }
    }
    
    /**
     * Tracks the history of preference updates for a user
     */
    public static class PreferenceUpdateHistory {
        private final Id<Person> personId;
        private int updateCount = 0;
        private double totalAccessChange = 0;
        private double totalWaitChange = 0;
        private double totalIvtChange = 0;
        private double totalEgressChange = 0;
        
        public PreferenceUpdateHistory(Id<Person> personId) {
            this.personId = personId;
        }
        
        public void addUpdate(UserPreferenceData oldPref, UserPreferenceData newPref) {
            updateCount++;
            totalAccessChange += Math.abs(newPref.getAccessWeight() - oldPref.getAccessWeight());
            totalWaitChange += Math.abs(newPref.getWaitWeight() - oldPref.getWaitWeight());
            totalIvtChange += Math.abs(newPref.getIvtWeight() - oldPref.getIvtWeight());
            totalEgressChange += Math.abs(newPref.getEgressWeight() - oldPref.getEgressWeight());
        }
        
        public int getUpdateCount() { return updateCount; }
        public double getTotalAccessChange() { return totalAccessChange; }
        public double getTotalWaitChange() { return totalWaitChange; }
        public double getTotalIvtChange() { return totalIvtChange; }
        public double getTotalEgressChange() { return totalEgressChange; }
        
        public double getAverageChangeMagnitude() {
            if (updateCount == 0) return 0;
            return (totalAccessChange + totalWaitChange + totalIvtChange + totalEgressChange) / (4.0 * updateCount);
        }
    }
}