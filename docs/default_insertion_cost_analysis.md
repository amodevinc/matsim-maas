# Analysis of MATSim Default InsertionCostCalculator

## Overview
The default InsertionCostCalculator in MATSim's DRT module is responsible for computing the cost of inserting a new request into an existing vehicle's schedule. It is part of the contrib.drt.optimizer.insertion package.

## Key Components
- **Parameters**: 
  - alpha: Multiplier for vehicle detour time (default 1.0)
  - beta: Multiplier for passenger delay (default 1.0)
  - rejectInsertionCost: High cost penalty if insertion would violate time windows (default 3600s)

- **Main Method**: calculate(Request, Insertion, DetourTimeInfo)
  - Computes additional vehicle time due to detour
  - Computes passenger wait time and travel time
  - Returns cost = alpha * vehicleDetour + beta * (passengerWait + passengerTravel - directTime)
  - If insertion not feasible, returns rejectInsertionCost

## Formulas
- Vehicle Detour = pickupDetour + dropoffDetour
- Passenger Delay = waitTime + inVehicleTime - directRideTime
- Total Cost = alpha * VehicleDetour + beta * PassengerDelay

## Integration Notes
- Used in DefaultUnplannedRequestInserter to evaluate possible insertions
- Costs are in seconds; lower cost means better insertion
- Custom implementations should preserve this structure to maintain optimizer behavior 