package org.matsim.maas.rl.events;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;

/**
 * Record for tracking person trip details for RL algorithms.
 * Complements DrtRequestRecord with actual travel timing information.
 */
public class PersonTripRecord {
    
    private final Id<Person> personId;
    private final double departureTime;
    private final Id<Link> departureLinkId;
    
    private double arrivalTime = -1;
    private double travelTime = -1;
    
    public PersonTripRecord(Id<Person> personId, double departureTime, Id<Link> departureLinkId) {
        this.personId = personId;
        this.departureTime = departureTime;
        this.departureLinkId = departureLinkId;
    }
    
    // Getters
    public Id<Person> getPersonId() { return personId; }
    public double getDepartureTime() { return departureTime; }
    public Id<Link> getDepartureLinkId() { return departureLinkId; }
    public double getArrivalTime() { return arrivalTime; }
    public double getTravelTime() { return travelTime; }
    
    // Setters
    public void setArrivalTime(double arrivalTime) { 
        this.arrivalTime = arrivalTime;
        if (arrivalTime >= 0) {
            this.travelTime = arrivalTime - departureTime;
        }
    }
    
    public void setTravelTime(double travelTime) { this.travelTime = travelTime; }
    
    // Status checks
    public boolean isCompleted() { return arrivalTime >= 0; }
    
    @Override
    public String toString() {
        return String.format("PersonTripRecord{person=%s, departure=%.1f, travel=%.1f}", 
                           personId, departureTime, travelTime);
    }
}