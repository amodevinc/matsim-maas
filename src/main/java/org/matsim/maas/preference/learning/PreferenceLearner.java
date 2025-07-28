package org.matsim.maas.preference.learning;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.maas.preference.data.DynamicUserPreferenceStore.PreferenceUpdate;

import java.util.Map;

/**
 * Interface for preference learning algorithms.
 * 
 * Implementations of this interface are responsible for calculating
 * preference weight updates based on user behavior and rewards.
 * 
 * The learning process follows these steps:
 * 1. Observe user decision (accept/reject ride, complete trip)
 * 2. Calculate reward based on outcome
 * 3. Compute gradient or update direction
 * 4. Return preference weight updates
 * 
 * This interface allows for different RL algorithms to be plugged in,
 * such as policy gradient, Q-learning, or other approaches.
 */
public interface PreferenceLearner {
    
    /**
     * Learn from a ride acceptance event.
     * 
     * @param personId The person who accepted the ride
     * @param accessTime Access time to pickup point (seconds)
     * @param waitTime Wait time for vehicle (seconds)
     * @param ivtTime In-vehicle travel time (seconds)
     * @param egressTime Egress time from dropoff (seconds)
     * @param reward Positive reward for acceptance
     * @return Preference weight updates to apply
     */
    PreferenceUpdate learnFromAcceptance(Id<Person> personId,
                                       double accessTime,
                                       double waitTime,
                                       double ivtTime,
                                       double egressTime,
                                       double reward);
    
    /**
     * Learn from a ride rejection event.
     * 
     * @param personId The person whose request was rejected
     * @param accessTime Estimated access time (seconds)
     * @param waitTime Estimated wait time (seconds)
     * @param ivtTime Estimated in-vehicle time (seconds)
     * @param egressTime Estimated egress time (seconds)
     * @param penalty Negative reward (penalty) for rejection
     * @return Preference weight updates to apply
     */
    PreferenceUpdate learnFromRejection(Id<Person> personId,
                                      double accessTime,
                                      double waitTime,
                                      double ivtTime,
                                      double egressTime,
                                      double penalty);
    
    /**
     * Learn from a completed trip.
     * 
     * @param personId The person who completed the trip
     * @param actualAccessTime Actual access time experienced
     * @param actualWaitTime Actual wait time experienced
     * @param actualIvtTime Actual in-vehicle time experienced
     * @param actualEgressTime Actual egress time experienced
     * @param satisfaction Satisfaction score or reward for completion
     * @return Preference weight updates to apply
     */
    PreferenceUpdate learnFromCompletion(Id<Person> personId,
                                       double actualAccessTime,
                                       double actualWaitTime,
                                       double actualIvtTime,
                                       double actualEgressTime,
                                       double satisfaction);
    
    /**
     * Perform batch learning from multiple events.
     * This allows for more stable updates using mini-batch gradient descent.
     * 
     * @param learningExperiences Map of person IDs to their learning experiences
     * @return Map of person IDs to preference updates
     */
    Map<Id<Person>, PreferenceUpdate> batchLearn(Map<Id<Person>, LearningExperience> learningExperiences);
    
    /**
     * Get the learning configuration parameters.
     * 
     * @return Current learning configuration
     */
    LearningConfiguration getConfiguration();
    
    /**
     * Update learning parameters (e.g., learning rate decay).
     * 
     * @param iteration Current simulation iteration
     */
    void updateLearningParameters(int iteration);
    
    /**
     * Reset learner state for a new simulation run.
     */
    void reset();
    
    /**
     * Container for a single learning experience.
     */
    class LearningExperience {
        public enum ExperienceType { ACCEPTANCE, REJECTION, COMPLETION }
        
        public final ExperienceType type;
        public final double accessTime;
        public final double waitTime;
        public final double ivtTime;
        public final double egressTime;
        public final double reward;
        public final double timestamp;
        
        public LearningExperience(ExperienceType type, 
                                double accessTime, double waitTime,
                                double ivtTime, double egressTime,
                                double reward, double timestamp) {
            this.type = type;
            this.accessTime = accessTime;
            this.waitTime = waitTime;
            this.ivtTime = ivtTime;
            this.egressTime = egressTime;
            this.reward = reward;
            this.timestamp = timestamp;
        }
        
        /**
         * Create experience from acceptance
         */
        public static LearningExperience fromAcceptance(double accessTime, double waitTime,
                                                      double ivtTime, double egressTime,
                                                      double reward, double timestamp) {
            return new LearningExperience(ExperienceType.ACCEPTANCE, 
                accessTime, waitTime, ivtTime, egressTime, reward, timestamp);
        }
        
        /**
         * Create experience from rejection
         */
        public static LearningExperience fromRejection(double accessTime, double waitTime,
                                                     double ivtTime, double egressTime,
                                                     double penalty, double timestamp) {
            return new LearningExperience(ExperienceType.REJECTION,
                accessTime, waitTime, ivtTime, egressTime, penalty, timestamp);
        }
        
        /**
         * Create experience from completion
         */
        public static LearningExperience fromCompletion(double accessTime, double waitTime,
                                                      double ivtTime, double egressTime,
                                                      double satisfaction, double timestamp) {
            return new LearningExperience(ExperienceType.COMPLETION,
                accessTime, waitTime, ivtTime, egressTime, satisfaction, timestamp);
        }
    }
}