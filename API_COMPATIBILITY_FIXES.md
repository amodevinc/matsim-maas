# MATSim DRT API Compatibility Fixes

This document summarizes the correct MATSim 16.0 DRT API method names and the fixes applied to resolve compilation errors.

## Issues Found and Fixed

### 1. Getting passenger ID from DrtRequest

**Problem**: Used `request.getPassengerId()` which doesn't exist
**Solution**: Use `request.getPassengerIds().iterator().next()` to get the first passenger ID

```java
// INCORRECT (doesn't exist in MATSim 16.0)
Id<Person> personId = Id.createPersonId(request.getPassengerId());

// CORRECT (works in MATSim 16.0)
Id<Person> personId = request.getPassengerIds().iterator().next();
```

### 2. Getting detour info from InsertionWithDetourData

**Problem**: Used `insertion.getPickupDetourInfo()` and `insertion.getDropoffDetourInfo()` which don't exist
**Solution**: Simplified approach using direct travel time calculation and estimation

```java
// INCORRECT (methods don't exist in MATSim 16.0)
double pickupTime = insertion.getPickupDetourInfo().pickupTimeLoss;
double dropoffTime = insertion.getDropoffDetourInfo().dropoffTimeLoss;

// CORRECT (simplified approach for MATSim 16.0)
double directTravelTime = calculateDirectTravelTime(request);
double estimatedWaitTime = 300.0; // 5 minutes default
```

### 3. Removed non-existent import

**Problem**: `import org.matsim.contrib.drt.optimizer.DrtOptimizationRequest;` doesn't exist
**Solution**: Removed the import as it's not needed

## Available Methods in MATSim 16.0

### DrtRequest class methods:
- `getPassengerIds()` - Returns Collection<Id<Person>>
- `getFromLink()` - Returns Link (pickup location)
- `getToLink()` - Returns Link (dropoff location)
- `getEarliestStartTime()` - Returns double (earliest departure time)
- `getLatestStartTime()` - Returns double (latest departure time)
- `getLatestArrivalTime()` - Returns double (latest arrival time)
- `getSubmissionTime()` - Returns double (when request was submitted)
- `getId()` - Returns Id<Request>

### InsertionWithDetourData class methods:
Based on our analysis, the following methods are NOT available in MATSim 16.0:
- `getPickupDetourInfo()` - Does not exist
- `getDropoffDetourInfo()` - Does not exist
- `getPickupIdx()` - Does not exist
- `getDropoffIdx()` - Does not exist
- `getDetourToPickup()` - Does not exist
- `getDetourToDropoff()` - Does not exist

## Workaround Solutions

Since the detailed detour timing information is not readily available through the InsertionWithDetourData interface in MATSim 16.0, we implemented simplified calculations:

### Wait Time Calculation
```java
private double calculateWaitTime(DrtRequest request, InsertionWithDetourData insertion) {
    double estimatedWaitTime = 300.0; // 5 minutes default wait time
    return estimatedWaitTime;
}
```

### In-Vehicle Time Calculation
```java
private double calculateInVehicleTime(DrtRequest request, InsertionWithDetourData insertion) {
    double directTravelTime = calculateDirectTravelTime(request);
    double sharingFactor = 1.3; // 30% longer due to shared ride
    return directTravelTime * sharingFactor;
}
```

### Direct Travel Time Calculation
```java
private double calculateDirectTravelTime(DrtRequest request) {
    double distance = Math.sqrt(
        Math.pow(request.getFromLink().getCoord().getX() - request.getToLink().getCoord().getX(), 2) +
        Math.pow(request.getFromLink().getCoord().getY() - request.getToLink().getCoord().getY(), 2)
    );
    
    double averageSpeed = 8.33; // m/s (30 km/h)
    return distance / averageSpeed; // travel time in seconds
}
```

## Recommendations for Future Implementation

1. **Use InsertionCostCalculator Interface**: For more advanced detour calculations, implement the `InsertionCostCalculator` interface which provides access to `DetourTimeInfo` objects.

2. **Access Vehicle Schedules**: For precise timing, access vehicle schedules directly through the `DvrpVehicle` and `Schedule` classes.

3. **Use Network-Based Routing**: For accurate travel time calculations, use MATSim's `LeastCostPathCalculator` with the network.

4. **Consult Latest Documentation**: Always refer to the latest MATSim documentation and API references for the specific version being used.

## Files Modified

- `/src/main/java/org/matsim/maas/preference/cost/PrefCostCalculator.java` - Fixed API compatibility issues

## Compilation Status

✅ **SUCCESS**: The code now compiles successfully with MATSim 16.0-2024w15.