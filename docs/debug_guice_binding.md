# Debug Report: Performance Degradation in Preference-Aware DRT

## Symptom
Severe performance degradation: wait times increased from 218s (baseline) to 809s (preference-aware) - 270% increase.

## Root Cause Analysis

### Initial Hypothesis (INCORRECT)
Initially suspected Guice binding conflict or preference weight scaling issues.

### Actual Root Cause (CONFIRMED)
**The entire PrefCostCalculator implementation is fundamentally incompatible with MATSim's insertion optimization algorithm.**

Evidence:
1. Even with `usePreferenceWeights=false`, performance degrades to 809s wait time
2. The custom cost calculator produces costs that don't align with MATSim's optimization logic
3. My cost calculation method is creating insertion costs that mislead the optimizer

### Technical Issues
1. **Wrong Cost Calculation Approach**: Using simple baseline cost + preference adjustment doesn't match MATSim's expected cost structure
2. **Inappropriate Time Calculations**: My access/wait/IVT/egress time calculations don't match what MATSim's optimizer expects
3. **Cost Scale Mismatch**: The cost values I'm producing don't align with the optimization algorithm's decision criteria

## Recommended Fix
1. **Study Default Implementation**: Examine MATSim's default `InsertionCostCalculator` implementation
2. **Proper Integration**: Modify preference calculation to work WITH the existing cost structure, not replace it
3. **Cost Adjustment Approach**: Apply preferences as adjustments to the default cost rather than computing from scratch

## Impact
This explains why the preference-aware system performs worse than baseline even when preferences are disabled - the core cost calculation is broken.

## Next Steps
- Examine default MATSim InsertionCostCalculator implementation
- Rewrite PrefCostCalculator to properly extend/adjust default behavior 