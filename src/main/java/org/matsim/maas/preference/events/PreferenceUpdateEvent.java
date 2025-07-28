package org.matsim.maas.preference.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;

import java.util.Map;

/**
 * Event representing an update to user preferences during simulation.
 * This event is fired when the RL system updates a user's preference weights
 * based on their ride acceptance/rejection behavior.
 * 
 * Following MATSim event conventions:
 * - Implements Event interface
 * - Provides getEventType() for identification
 * - Immutable with all data passed in constructor
 * - Used for tracking and analyzing preference evolution
 */
public class PreferenceUpdateEvent extends Event {
    
    public static final String EVENT_TYPE = "preferenceUpdate";
    
    private final Id<Person> personId;
    private final double oldAccessWeight;
    private final double oldWaitWeight;
    private final double oldIvtWeight;
    private final double oldEgressWeight;
    private final double newAccessWeight;
    private final double newWaitWeight;
    private final double newIvtWeight;
    private final double newEgressWeight;
    private final String updateReason;
    private final double learningReward;
    
    public PreferenceUpdateEvent(double time, Id<Person> personId,
                               double oldAccessWeight, double oldWaitWeight, 
                               double oldIvtWeight, double oldEgressWeight,
                               double newAccessWeight, double newWaitWeight,
                               double newIvtWeight, double newEgressWeight,
                               String updateReason, double learningReward) {
        super(time);
        this.personId = personId;
        this.oldAccessWeight = oldAccessWeight;
        this.oldWaitWeight = oldWaitWeight;
        this.oldIvtWeight = oldIvtWeight;
        this.oldEgressWeight = oldEgressWeight;
        this.newAccessWeight = newAccessWeight;
        this.newWaitWeight = newWaitWeight;
        this.newIvtWeight = newIvtWeight;
        this.newEgressWeight = newEgressWeight;
        this.updateReason = updateReason;
        this.learningReward = learningReward;
    }
    
    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }
    
    public Id<Person> getPersonId() {
        return personId;
    }
    
    // Getters for old weights
    public double getOldAccessWeight() { return oldAccessWeight; }
    public double getOldWaitWeight() { return oldWaitWeight; }
    public double getOldIvtWeight() { return oldIvtWeight; }
    public double getOldEgressWeight() { return oldEgressWeight; }
    
    // Getters for new weights
    public double getNewAccessWeight() { return newAccessWeight; }
    public double getNewWaitWeight() { return newWaitWeight; }
    public double getNewIvtWeight() { return newIvtWeight; }
    public double getNewEgressWeight() { return newEgressWeight; }
    
    // Getters for deltas
    public double getAccessWeightDelta() { return newAccessWeight - oldAccessWeight; }
    public double getWaitWeightDelta() { return newWaitWeight - oldWaitWeight; }
    public double getIvtWeightDelta() { return newIvtWeight - oldIvtWeight; }
    public double getEgressWeightDelta() { return newEgressWeight - oldEgressWeight; }
    
    public String getUpdateReason() { return updateReason; }
    public double getLearningReward() { return learningReward; }
    
    /**
     * Calculate the magnitude of the preference update
     */
    public double getUpdateMagnitude() {
        return Math.abs(getAccessWeightDelta()) + 
               Math.abs(getWaitWeightDelta()) + 
               Math.abs(getIvtWeightDelta()) + 
               Math.abs(getEgressWeightDelta());
    }
    
    @Override
    public Map<String, String> getAttributes() {
        Map<String, String> attrs = super.getAttributes();
        attrs.put("person", personId.toString());
        attrs.put("oldAccessWeight", Double.toString(oldAccessWeight));
        attrs.put("oldWaitWeight", Double.toString(oldWaitWeight));
        attrs.put("oldIvtWeight", Double.toString(oldIvtWeight));
        attrs.put("oldEgressWeight", Double.toString(oldEgressWeight));
        attrs.put("newAccessWeight", Double.toString(newAccessWeight));
        attrs.put("newWaitWeight", Double.toString(newWaitWeight));
        attrs.put("newIvtWeight", Double.toString(newIvtWeight));
        attrs.put("newEgressWeight", Double.toString(newEgressWeight));
        attrs.put("updateReason", updateReason);
        attrs.put("learningReward", Double.toString(learningReward));
        attrs.put("updateMagnitude", Double.toString(getUpdateMagnitude()));
        return attrs;
    }
}