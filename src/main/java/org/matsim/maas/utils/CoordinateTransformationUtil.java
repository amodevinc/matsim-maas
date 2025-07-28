package org.matsim.maas.utils;

import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

/**
 * Utility class for coordinate transformations in the Hwaseong scenario.
 * Transforms between WGS84 (EPSG:4326) and projected coordinates used in MATSim.
 * 
 * Based on analysis of the Hwaseong network file, the projected coordinates appear
 * to be in a Korean projected coordinate system (likely EPSG:5179 or similar).
 */
public class CoordinateTransformationUtil {
    
    // WGS84 coordinate system
    private static final String WGS84_CRS = "EPSG:4326";
    
    // Korean projected coordinate system - using EPSG:5179 (Korea 2000 / Central Belt)
    // This is commonly used for Korean GIS data and matches the coordinate range in the network
    private static final String PROJECTED_CRS = "EPSG:5179";
    
    private final CoordinateTransformation wgs84ToProjected;
    private final CoordinateTransformation projectedToWgs84;
    
    public CoordinateTransformationUtil() {
        this.wgs84ToProjected = TransformationFactory.getCoordinateTransformation(WGS84_CRS, PROJECTED_CRS);
        this.projectedToWgs84 = TransformationFactory.getCoordinateTransformation(PROJECTED_CRS, WGS84_CRS);
    }
    
    /**
     * Transform coordinates from WGS84 (longitude, latitude) to projected coordinates (x, y).
     * 
     * @param longitude Longitude in decimal degrees
     * @param latitude Latitude in decimal degrees
     * @return Projected coordinates as Coord object
     */
    public Coord transformFromWGS84(double longitude, double latitude) {
        Coord wgs84Coord = new Coord(longitude, latitude);
        return wgs84ToProjected.transform(wgs84Coord);
    }
    
    /**
     * Transform coordinates from projected system back to WGS84.
     * 
     * @param x X coordinate in projected system
     * @param y Y coordinate in projected system
     * @return WGS84 coordinates as Coord object (longitude, latitude)
     */
    public Coord transformToWGS84(double x, double y) {
        Coord projectedCoord = new Coord(x, y);
        return projectedToWgs84.transform(projectedCoord);
    }
    
    /**
     * Convenience method to transform from WGS84 using a Coord object.
     * 
     * @param wgs84Coord Coordinates in WGS84 (longitude, latitude)
     * @return Projected coordinates as Coord object
     */
    public Coord transformFromWGS84(Coord wgs84Coord) {
        return wgs84ToProjected.transform(wgs84Coord);
    }
    
    /**
     * Convenience method to transform to WGS84 using a Coord object.
     * 
     * @param projectedCoord Coordinates in projected system
     * @return WGS84 coordinates as Coord object
     */
    public Coord transformToWGS84(Coord projectedCoord) {
        return projectedToWgs84.transform(projectedCoord);
    }
    
    /**
     * Get the coordinate transformation object for WGS84 to projected coordinates.
     * Useful for external classes that need direct access to the transformation.
     * 
     * @return CoordinateTransformation object
     */
    public CoordinateTransformation getWGS84ToProjectedTransformation() {
        return wgs84ToProjected;
    }
    
    /**
     * Get the coordinate transformation object for projected to WGS84 coordinates.
     * 
     * @return CoordinateTransformation object
     */
    public CoordinateTransformation getProjectedToWGS84Transformation() {
        return projectedToWgs84;
    }
}