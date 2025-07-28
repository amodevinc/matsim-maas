package org.matsim.maas.utils;

import org.matsim.api.core.v01.Coord;
import java.time.LocalDateTime;

/**
 * Represents a single demand request from the real-time demand data files.
 * Contains all the information about a trip request including origin, destination,
 * timing, and spatial information.
 */
public class DemandRequest {
    
    private final int idx;
    private final int originZone;
    private final int destinationZone;
    private final int hour;
    private final String originH3;
    private final String destinationH3;
    private final Coord originWGS84;
    private final Coord destinationWGS84;
    private final Coord originProjected;
    private final Coord destinationProjected;
    private final LocalDateTime requestTime;
    
    public DemandRequest(int idx, int originZone, int destinationZone, int hour,
                        String originH3, String destinationH3,
                        double originLon, double originLat,
                        double destLon, double destLat,
                        LocalDateTime requestTime,
                        CoordinateTransformationUtil coordTransform) {
        this.idx = idx;
        this.originZone = originZone;
        this.destinationZone = destinationZone;
        this.hour = hour;
        this.originH3 = originH3;
        this.destinationH3 = destinationH3;
        this.originWGS84 = new Coord(originLon, originLat);
        this.destinationWGS84 = new Coord(destLon, destLat);
        this.originProjected = coordTransform.transformFromWGS84(originLon, originLat);
        this.destinationProjected = coordTransform.transformFromWGS84(destLon, destLat);
        this.requestTime = requestTime;
    }
    
    // Getters
    public int getIdx() { return idx; }
    public int getOriginZone() { return originZone; }
    public int getDestinationZone() { return destinationZone; }
    public int getHour() { return hour; }
    public String getOriginH3() { return originH3; }
    public String getDestinationH3() { return destinationH3; }
    public Coord getOriginWGS84() { return originWGS84; }
    public Coord getDestinationWGS84() { return destinationWGS84; }
    public Coord getOriginProjected() { return originProjected; }
    public Coord getDestinationProjected() { return destinationProjected; }
    public LocalDateTime getRequestTime() { return requestTime; }
    
    /**
     * Get the departure time in seconds since midnight.
     * Useful for MATSim activity scheduling.
     */
    public double getDepartureTimeSeconds() {
        return requestTime.getHour() * 3600 + 
               requestTime.getMinute() * 60 + 
               requestTime.getSecond();
    }
    
    @Override
    public String toString() {
        return String.format("DemandRequest{idx=%d, o=%d->d=%d, hour=%d, time=%s, origin=(%f,%f), dest=(%f,%f)}", 
                           idx, originZone, destinationZone, hour, requestTime,
                           originProjected.getX(), originProjected.getY(),
                           destinationProjected.getX(), destinationProjected.getY());
    }
}