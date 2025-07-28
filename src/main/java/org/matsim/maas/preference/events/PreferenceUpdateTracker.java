package org.matsim.maas.preference.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks preference update events and provides analytics.
 * Implements PreferenceUpdateEventHandler to capture all preference changes
 * and IterationEndsListener to write statistics at the end of each iteration.
 * 
 * This handler is essential for monitoring the RL learning process and
 * understanding how preferences evolve over time.
 */
public class PreferenceUpdateTracker implements PreferenceUpdateEventHandler, IterationEndsListener {
    
    private final String outputDirectory;
    private final Map<Id<Person>, List<PreferenceUpdateEvent>> updatesByPerson = new ConcurrentHashMap<>();
    private final List<PreferenceUpdateEvent> allUpdates = Collections.synchronizedList(new ArrayList<>());
    
    // Metrics
    private int totalUpdates = 0;
    private double totalReward = 0.0;
    private double totalMagnitude = 0.0;
    private final Map<String, Integer> updateReasonCounts = new ConcurrentHashMap<>();
    
    public PreferenceUpdateTracker(String outputDirectory) {
        this.outputDirectory = outputDirectory;
        ensureDirectoryExists();
    }
    
    @Override
    public void handleEvent(PreferenceUpdateEvent event) {
        // Store event
        allUpdates.add(event);
        updatesByPerson.computeIfAbsent(event.getPersonId(), k -> new ArrayList<>()).add(event);
        
        // Update metrics
        totalUpdates++;
        totalReward += event.getLearningReward();
        totalMagnitude += event.getUpdateMagnitude();
        updateReasonCounts.merge(event.getUpdateReason(), 1, Integer::sum);
    }
    
    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        int iteration = event.getIteration();
        
        // Write detailed update log
        writeDetailedUpdateLog(iteration);
        
        // Write summary statistics
        writeSummaryStatistics(iteration);
        
        // Write per-person statistics
        writePerPersonStatistics(iteration);
        
        // Log to console
        logIterationSummary(iteration);
        
        // Reset for next iteration
        reset(iteration);
    }
    
    private void writeDetailedUpdateLog(int iteration) {
        String filename = String.format("%s/preference_updates_iter_%d.csv", outputDirectory, iteration);
        
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename))) {
            writer.write("time,person_id,old_access,old_wait,old_ivt,old_egress," +
                        "new_access,new_wait,new_ivt,new_egress," +
                        "delta_access,delta_wait,delta_ivt,delta_egress," +
                        "update_reason,learning_reward,update_magnitude\n");
            
            for (PreferenceUpdateEvent event : allUpdates) {
                writer.write(String.format("%.1f,%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f," +
                                         "%.6f,%.6f,%.6f,%.6f,%s,%.6f,%.6f\n",
                    event.getTime(),
                    event.getPersonId(),
                    event.getOldAccessWeight(),
                    event.getOldWaitWeight(),
                    event.getOldIvtWeight(),
                    event.getOldEgressWeight(),
                    event.getNewAccessWeight(),
                    event.getNewWaitWeight(),
                    event.getNewIvtWeight(),
                    event.getNewEgressWeight(),
                    event.getAccessWeightDelta(),
                    event.getWaitWeightDelta(),
                    event.getIvtWeightDelta(),
                    event.getEgressWeightDelta(),
                    event.getUpdateReason(),
                    event.getLearningReward(),
                    event.getUpdateMagnitude()
                ));
            }
            
        } catch (IOException e) {
            System.err.println("Error writing preference update log: " + e.getMessage());
        }
    }
    
    private void writeSummaryStatistics(int iteration) {
        String filename = outputDirectory + "/preference_learning_summary.csv";
        boolean fileExists = Files.exists(Paths.get(filename));
        
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename),
                                                            StandardOpenOption.CREATE,
                                                            StandardOpenOption.APPEND)) {
            
            if (!fileExists) {
                writer.write("iteration,total_updates,unique_persons_updated,avg_reward," +
                           "avg_update_magnitude,total_reward,total_magnitude\n");
            }
            
            double avgReward = totalUpdates > 0 ? totalReward / totalUpdates : 0.0;
            double avgMagnitude = totalUpdates > 0 ? totalMagnitude / totalUpdates : 0.0;
            
            writer.write(String.format("%d,%d,%d,%.6f,%.6f,%.6f,%.6f\n",
                iteration,
                totalUpdates,
                updatesByPerson.size(),
                avgReward,
                avgMagnitude,
                totalReward,
                totalMagnitude
            ));
            
        } catch (IOException e) {
            System.err.println("Error writing summary statistics: " + e.getMessage());
        }
    }
    
    private void writePerPersonStatistics(int iteration) {
        String filename = String.format("%s/preference_person_stats_iter_%d.csv", outputDirectory, iteration);
        
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename))) {
            writer.write("person_id,num_updates,total_reward,avg_reward,total_magnitude,avg_magnitude," +
                        "final_access,final_wait,final_ivt,final_egress\n");
            
            for (Map.Entry<Id<Person>, List<PreferenceUpdateEvent>> entry : updatesByPerson.entrySet()) {
                List<PreferenceUpdateEvent> updates = entry.getValue();
                if (updates.isEmpty()) continue;
                
                double personTotalReward = updates.stream()
                    .mapToDouble(PreferenceUpdateEvent::getLearningReward).sum();
                double personTotalMagnitude = updates.stream()
                    .mapToDouble(PreferenceUpdateEvent::getUpdateMagnitude).sum();
                
                PreferenceUpdateEvent lastUpdate = updates.get(updates.size() - 1);
                
                writer.write(String.format("%s,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f\n",
                    entry.getKey(),
                    updates.size(),
                    personTotalReward,
                    personTotalReward / updates.size(),
                    personTotalMagnitude,
                    personTotalMagnitude / updates.size(),
                    lastUpdate.getNewAccessWeight(),
                    lastUpdate.getNewWaitWeight(),
                    lastUpdate.getNewIvtWeight(),
                    lastUpdate.getNewEgressWeight()
                ));
            }
            
        } catch (IOException e) {
            System.err.println("Error writing per-person statistics: " + e.getMessage());
        }
    }
    
    private void logIterationSummary(int iteration) {
        System.out.println("\n=== PREFERENCE LEARNING SUMMARY - Iteration " + iteration + " ===");
        System.out.printf("Total preference updates: %d%n", totalUpdates);
        System.out.printf("Unique persons updated: %d%n", updatesByPerson.size());
        
        if (totalUpdates > 0) {
            System.out.printf("Average reward per update: %.4f%n", totalReward / totalUpdates);
            System.out.printf("Average update magnitude: %.4f%n", totalMagnitude / totalUpdates);
            
            System.out.println("Update reasons:");
            for (Map.Entry<String, Integer> entry : updateReasonCounts.entrySet()) {
                System.out.printf("  %s: %d (%.1f%%)%n", 
                    entry.getKey(), 
                    entry.getValue(),
                    100.0 * entry.getValue() / totalUpdates);
            }
        }
        
        System.out.println("=============================================\n");
    }
    
    @Override
    public void reset(int iteration) {
        // Clear data for next iteration
        updatesByPerson.clear();
        allUpdates.clear();
        totalUpdates = 0;
        totalReward = 0.0;
        totalMagnitude = 0.0;
        updateReasonCounts.clear();
    }
    
    /**
     * Get statistics for analysis
     */
    public PreferenceLearningStats getStats() {
        return new PreferenceLearningStats(
            totalUpdates,
            updatesByPerson.size(),
            totalReward,
            totalMagnitude,
            new HashMap<>(updateReasonCounts)
        );
    }
    
    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(Paths.get(outputDirectory));
        } catch (IOException e) {
            System.err.println("Could not create output directory: " + e.getMessage());
        }
    }
    
    /**
     * Container for preference learning statistics
     */
    public static class PreferenceLearningStats {
        public final int totalUpdates;
        public final int uniquePersonsUpdated;
        public final double totalReward;
        public final double totalMagnitude;
        public final Map<String, Integer> updateReasonCounts;
        
        public PreferenceLearningStats(int totalUpdates, int uniquePersonsUpdated,
                                     double totalReward, double totalMagnitude,
                                     Map<String, Integer> updateReasonCounts) {
            this.totalUpdates = totalUpdates;
            this.uniquePersonsUpdated = uniquePersonsUpdated;
            this.totalReward = totalReward;
            this.totalMagnitude = totalMagnitude;
            this.updateReasonCounts = updateReasonCounts;
        }
        
        public double getAverageReward() {
            return totalUpdates > 0 ? totalReward / totalUpdates : 0.0;
        }
        
        public double getAverageMagnitude() {
            return totalUpdates > 0 ? totalMagnitude / totalUpdates : 0.0;
        }
    }
}