# Design: Integrated Preference-Aware Cost Calculator

## Overview
Design for a new `PrefAwareInsertionCostCalculator` that properly integrates with MATSim's default cost calculation while adding preference-based adjustments.

## Key Design Principles

1. **Composition over Replacement**: Wrap/extend the default calculator instead of replacing it
2. **Preserve Default Structure**: Maintain MATSim's cost formula (alpha * detour + beta * delay) 
3. **Preference as Adjustment**: Apply preferences as multiplicative factors, not additive costs
4. **Proper Interface Implementation**: Use exact MATSim interface: `calculate(DrtRequest, Insertion, DetourTimeInfo)`

## Architecture

### Class: PrefAwareInsertionCostCalculator
```java
public class PrefAwareInsertionCostCalculator implements InsertionCostCalculator {
    private final InsertionCostCalculator defaultCalculator;  // Composition
    private final UserPreferenceStore preferenceStore;
    private final boolean usePreferences;
}
```

### Core Algorithm
```
1. Get default cost from MATSim's calculator
2. If preferences disabled: return default cost
3. If preferences enabled:
   a. Extract timing components from DetourTimeInfo
   b. Get user preference weights for this person
   c. Calculate preference utility for this insertion
   d. Convert utility to cost adjustment factor
   e. Apply factor to default cost: adjustedCost = defaultCost * factor
4. Return adjusted cost
```

## Implementation Details

### Default Calculator Integration
- Inject MATSim's default `InsertionCostCalculator` via constructor
- Call `defaultCalculator.calculate(request, insertion, detourTimeInfo)` first
- Only adjust the result, never replace the calculation

### Preference Adjustment Formula
```
utility = w_access * accessTime + w_wait * waitTime + w_ivt * ivtTime + w_egress * egressTime
normalizedUtility = utility / maxUtility  // Normalize to [-1, 1] range
adjustmentFactor = 1.0 + (normalizedUtility * 0.2)  // ±20% adjustment max
finalCost = defaultCost * adjustmentFactor
```

### Time Component Extraction
Use MATSim's DetourTimeInfo structure:
- `detourTimeInfo.pickupDetourInfo.pickupTimeLoss` → wait time
- `detourTimeInfo.dropoffDetourInfo.dropoffTimeLoss` → in-vehicle detour 
- Calculate access/egress from coordinate distances
- Use direct travel time for comparison

### Fallback Handling
- If no preference data: return default cost (no adjustment)
- If DetourTimeInfo is null: return default cost
- If calculation fails: log warning, return default cost

## Module Integration

### Provider Pattern
```java
@Provides @Singleton
public InsertionCostCalculator provideInsertionCostCalculator(
    @Named("default") InsertionCostCalculator defaultCalc,
    UserPreferenceStore prefStore) {
    return new PrefAwareInsertionCostCalculator(defaultCalc, prefStore, usePreferences);
}
```

### QSim Module Binding
```java
public class PrefAwareQSimModule extends AbstractDvrpModeQSimModule {
    protected void configureQSim() {
        // First bind the default calculator with a name
        bindModal(InsertionCostCalculator.class)
            .annotatedWith(Names.named("default"))
            .toProvider(DefaultInsertionCostCalculatorProvider.class)
            .in(Singleton.class);
            
        // Then bind our wrapper as the main calculator
        bindModal(InsertionCostCalculator.class)
            .toProvider(this::providePrefAwareCalculator)
            .in(Singleton.class);
    }
}
```

## Testing Strategy

### Unit Tests
1. **Default Passthrough**: When preferences disabled, output = default input
2. **Preference Adjustment**: When enabled, verify proper utility calculation and factor application
3. **Boundary Conditions**: Null inputs, missing preference data, extreme utility values
4. **Performance**: Verify minimal overhead over default calculator

### Integration Tests
1. **Baseline Reproduction**: With preferences off, match original baseline performance
2. **Preference Impact**: With preferences on, verify improved satisfaction without breaking performance
3. **Scale Testing**: Verify performance with large numbers of requests

## Success Criteria

1. **Baseline Performance**: When `usePreferences=false`, matches original baseline (218s wait time)
2. **Preference Effectiveness**: When `usePreferences=true`, shows measurable improvement in user satisfaction
3. **Performance Stability**: No significant increase in computation time
4. **Integration**: Properly integrates with MATSim's optimizer without binding conflicts

## Risk Mitigation

1. **Calculation Errors**: Extensive logging and fallback to default cost
2. **Performance Impact**: Minimal computation overhead, cached preference lookups
3. **Integration Issues**: Composition pattern ensures compatibility with MATSim updates
4. **Scaling Issues**: Preference adjustment factors limited to ±20% range

## Implementation Timeline

1. **Phase 1**: Create wrapper class with passthrough functionality
2. **Phase 2**: Add preference utility calculation
3. **Phase 3**: Implement cost adjustment logic
4. **Phase 4**: Integration testing and performance validation 