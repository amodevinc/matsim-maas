package org.matsim.maas.rl.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;

/**
 * Record for tracking DRT request lifecycle for RL algorithms.
 * Contains all timing and location information needed for reward calculation.
 */
public class DrtRequestRecord {
    
    private final Id<org.matsim.contrib.dvrp.optimizer.Request> requestId;
    private final Id<Person> personId;
    private final double submissionTime;
    private final Id<Link> fromLinkId;
    private final Id<Link> toLinkId;
    
    private double scheduledTime = -1;
    private double pickupTime = -1;
    private double dropoffTime = -1;
    private double waitTime = -1;
    private boolean rejected = false;
    
    public DrtRequestRecord(Id<org.matsim.contrib.dvrp.optimizer.Request> requestId,
                           Id<Person> personId,
                           double submissionTime,
                           Id<Link> fromLinkId,
                           Id<Link> toLinkId) {
        this.requestId = requestId;
        this.personId = personId;
        this.submissionTime = submissionTime;
        this.fromLinkId = fromLinkId;
        this.toLinkId = toLinkId;
    }
    
    // Getters
    public Id<org.matsim.contrib.dvrp.optimizer.Request> getRequestId() { return requestId; }
    public Id<Person> getPersonId() { return personId; }
    public double getSubmissionTime() { return submissionTime; }
    public Id<Link> getFromLinkId() { return fromLinkId; }
    public Id<Link> getToLinkId() { return toLinkId; }
    public double getScheduledTime() { return scheduledTime; }
    public double getPickupTime() { return pickupTime; }
    public double getDropoffTime() { return dropoffTime; }
    public double getWaitTime() { return waitTime; }
    public boolean isRejected() { return rejected; }
    
    // Setters
    public void setScheduledTime(double scheduledTime) { this.scheduledTime = scheduledTime; }
    public void setPickupTime(double pickupTime) { this.pickupTime = pickupTime; }
    public void setDropoffTime(double dropoffTime) { this.dropoffTime = dropoffTime; }
    public void setWaitTime(double waitTime) { this.waitTime = waitTime; }
    public void setRejected(boolean rejected) { this.rejected = rejected; }
    
    // Derived properties
    public boolean isScheduled() { return scheduledTime >= 0; }
    public double getInVehicleTime() { 
        return (pickupTime >= 0 && dropoffTime >= 0) ? dropoffTime - pickupTime : -1; 
    }
    public double getTotalTripTime() { 
        return (dropoffTime >= 0) ? dropoffTime - submissionTime : -1; 
    }
    
    @Override
    public String toString() {
        return String.format("DrtRequestRecord{person=%s, submission=%.1f, wait=%.1f, rejected=%b}", 
                           personId, submissionTime, waitTime, rejected);
    }
}