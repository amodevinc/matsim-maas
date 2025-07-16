# Preference-Adaptive RL-Driven DRT Dispatching System - Implementation Plan

## Project Overview
Build a comprehensive preference-adaptive reinforcement learning-driven dispatching algorithm for MATSim-DRT research that leverages existing user preference data to create personalized, adaptive DRT service optimization.

## Current State Analysis

### ✅ Completed Infrastructure
- **MATSim-DRT Framework**: Working baseline with `RunStopBasedDrtExample.java`
- **Population Generation**: Successfully generated all 45 population variants
  - 5 scenarios (base, S1-S4) × 3 demand levels (0.5×, 1.0×, 1.5×) × 3 rules = 45 files
  - Population sizes ranging from ~1,400 to ~3,600 persons
  - All files stored in `data/populations/`

### 🎯 Rich Preference Data Available
- **User Choice Data**: 500 users × 6 choice situations = 3,000 choice records
- **Features**: Alternative attributes (access, wait, IVT, egress times) for each choice
- **Learned Weights**: Pre-trained user preference weights from policy gradient learning
- **Comprehensive Demand**: 72 zones with realistic O/D matrices and temporal patterns

### 📊 Experimental Framework
- **Spatial Framework**: 72 zones in Hwaseong, South Korea with H3 hexagonal grid
- **Temporal Coverage**: 07:00-22:00 daily operation
- **Demand Scenarios**: 
  - Base: Uniform temporal distribution
  - S1: Temporal peaks (morning/evening)
  - S2: Spatial concentration (activity-based)
  - S3: Combined temporal and spatial effects
  - S4: Smart-card derived patterns
- **Configuration**: Stop-based DRT with 8 vehicles, baseline configuration ready

## Implementation Strategy: Incremental Build with Comprehensive Smoke Testing

### Phase 0: Foundation & Data Infrastructure ✅ COMPLETED
1. **✅ Population Generator**: Created utility class to convert O/D matrices to MATSim populations
2. **✅ All Population Files**: Generated 45 population variants from demand data
3. **🔄 Smoke Testing**: Verify population file generation and trip distributions

### Phase 1: Foundation & Baseline System (Days 1-3)
**Goal**: Establish robust baseline with comprehensive logging

1. **Enhanced Baseline System**
   - Fork `RunStopBasedDrtExample.java` → `RunHwaseongBaseline.java`
   - Add comprehensive KPI logging (wait times, detours, service rates, rejections)
   - Support for different population files via command-line arguments
   - **Smoke Test**: Run with each scenario type, verify metrics collection

2. **Experimental Infrastructure**
   - Create `ExperimentRunner` for automated multi-scenario testing
   - Implement result aggregation and comparison tools
   - **Smoke Test**: Run baseline experiments across demand variations

### Phase 2: Preference Data Integration (Days 4-7)
**Goal**: Integrate user preference data into dispatching system

1. **Preference Data Management**
   - Create `PreferenceDataLoader` for CSV data ingestion
   - Build `UserPreferenceStore` with fallback mechanisms
   - Map MATSim person IDs to preference user IDs (1-500)
   - Handle missing preference data gracefully
   - **Smoke Test**: Load preference data, verify user mappings work

2. **Preference-Aware Cost Calculator**
   - Create `PrefCostModule` extending `AbstractDvrpModeModule`
   - Implement `PrefCostCalculator` implementing `DrtInsertionCostCalculator`
   - Algorithm: `baseline_cost + w_access*access + w_wait*wait + w_ivt*ivt + w_egress*egress`
   - **Smoke Test**: Run with static preferences, verify cost calculation activation

### Phase 3: Event-Driven Metrics & Reward System (Days 8-12)
**Goal**: Capture comprehensive trip performance for RL learning

1. **Enhanced Event Handling System**
   - Create `PrefRLEventHandler` implementing multiple DRT event handlers:
     - `DrtRequestSubmittedEventHandler` - capture request timestamp
     - `PassengerRequestScheduledEventHandler` - calculate wait time
     - `PassengerDroppedOffEventHandler` - calculate IVT, egress, total time
   - **Smoke Test**: Run simulation, verify all event types captured correctly

2. **Reward Calculation & State Representation**
   - Implement utility-based reward: `r = -(α*wait + β*ivt + γ*detour + δ*access)`
   - Create state representation: `[timeOfDay, zoneOrigin, zoneDest, userSegment, demandLevel]`
   - Store state-action-reward tuples for learning
   - **Smoke Test**: Verify reward calculations align with utility theory

### Phase 4: Basic RL Integration (Days 13-18)
**Goal**: Implement online learning with exploration

1. **Contextual Bandit Implementation**
   - Create `BanditLearner` with epsilon-greedy exploration
   - Implement simple gradient-based weight updates
   - Add experience buffer for stable learning
   - **Smoke Test**: Run with learning enabled, verify weight updates occur

2. **Real-time Preference Updates**
   - Update user preferences based on trip outcomes
   - Implement preference drift detection
   - Add exploration vs exploitation balance
   - **Smoke Test**: Extended runs showing preference adaptation

### Phase 5: Advanced RL & System Integration (Days 19-25)
**Goal**: Sophisticated RL with full system integration

1. **Enhanced RL System**
   - Implement policy gradient updates using historical choice data
   - Add experience replay for stable learning
   - Implement user clustering for cold-start problems
   - **Smoke Test**: Verify convergence on user choice prediction

2. **Custom Optimizer Integration**
   - Extend `DefaultDrtOptimizerProvider` → `PrefAwareOptimizerProvider`
   - Implement RL-driven insertion search
   - Add real-time policy updates during simulation
   - **Smoke Test**: Verify optimizer integration maintains simulation stability

### Phase 6: Comprehensive Evaluation Framework (Days 26-30)
**Goal**: Complete research infrastructure for paper production

1. **Automated Experimental Pipeline**
   - Create `ExperimentSuite` for systematic evaluation
   - Implement A/B testing: Baseline vs Static Preferences vs RL
   - Run across all 45 demand scenarios
   - **Smoke Test**: Automated pipeline runs without intervention

2. **Statistical Analysis & Visualization**
   - Implement statistical significance testing
   - Create performance dashboards with spatial/temporal analysis
   - Generate learning curves and convergence plots
   - **Smoke Test**: Verify statistical validity of results

## Technical Architecture

### Core Components Structure
```
src/main/java/org/matsim/maas/
├── utils/
│   ├── PopulationGenerator.java ✅
│   ├── ZoneMapper.java
│   └── StatisticalAnalyzer.java
├── preference/
│   ├── data/
│   │   ├── PreferenceDataLoader.java
│   │   ├── UserPreferenceStore.java
│   │   └── DemandDataLoader.java
│   ├── cost/
│   │   ├── PrefCostModule.java
│   │   ├── PrefCostCalculator.java
│   │   └── CostComponentCalculator.java
│   ├── rl/
│   │   ├── BanditLearner.java
│   │   ├── PolicyGradientUpdater.java
│   │   ├── ExperienceBuffer.java
│   │   └── StateRepresentation.java
│   ├── optimization/
│   │   ├── PrefAwareOptimizerProvider.java
│   │   ├── PrefAwareInsertionSearch.java
│   │   └── AdaptiveDispatcher.java
│   └── events/
│       ├── PrefRLEventHandler.java
│       ├── RewardCalculator.java
│       └── MetricsCollector.java
└── experiment/
    ├── RunHwaseongBaseline.java
    ├── ExperimentRunner.java
    ├── ExperimentSuite.java
    └── ResultAnalyzer.java
```

### Key Integration Points
1. **Controler Layer**: Preference data loading, cost calculation, event handling
2. **QSim Layer**: RL-driven optimizer with real-time policy updates
3. **Event Layer**: Comprehensive performance tracking and reward calculation
4. **Data Layer**: Population generation, preference mapping, result analysis

## Data Infrastructure

### Population Files ✅ COMPLETED
All 45 population variants generated with realistic trip distributions:
- **File naming**: `{scenario}_trip{multiplier}_rule{rule}_population.xml.gz`
- **Size range**: 1,400-3,600 persons per scenario
- **Geographic distribution**: 72 zones with spatial grid coordinates
- **Temporal patterns**: Hour-by-hour demand profiles

### Preference Data Available
- **User weights**: 500 users with learned utility coefficients
- **Choice features**: Access, wait, IVT, egress times for each alternative
- **Historical choices**: 3,000 choice records for validation

### Experimental Design

#### Demand Scenario Testing
- **5 Base Scenarios**: base, S1, S2, S3, S4 (different temporal/spatial patterns)
- **3 Demand Levels**: 0.5×, 1.0×, 1.5× (under/perfect/over-prediction)
- **3 Random Rules**: Different perturbation patterns for robustness
- **Total**: 45 experimental conditions

#### Comparison Framework
1. **Baseline DRT**: Standard MATSim DRT with default cost calculation
2. **Static Preferences**: Fixed user preference weights from learned data
3. **Adaptive RL**: Online learning with preference updates
4. **Oracle**: Perfect preference knowledge (theoretical upper bound)

## Success Metrics & Evaluation

### Performance Metrics
- **User Satisfaction**: Wait time, total travel time, service rate
- **Operator Efficiency**: Vehicle utilization, empty kilometers, cost per trip
- **Learning Performance**: Convergence rate, prediction accuracy
- **Adaptability**: Performance across demand variations

### Research Validation
- **Statistical Testing**: Multi-scenario comparison with confidence intervals
- **Ablation Studies**: Component-wise contribution analysis
- **Sensitivity Analysis**: Parameter robustness testing
- **Scalability Assessment**: Different fleet sizes and demand levels

## Smoke Testing Strategy

### Component-Level Testing
- **After each component**: Single iteration runs with logging verification
- **Integration testing**: Short multi-iteration runs with KPI validation
- **System testing**: Extended runs with baseline comparison

### Validation Approach
- **Population verification**: Trip count consistency with O/D matrices
- **Preference verification**: Cost calculation matches expected utility
- **Learning verification**: Preference updates follow expected patterns
- **Performance verification**: Metrics align with theoretical expectations

## Risk Mitigation

### Technical Risks
- **Modular Design**: Independent components for debugging
- **Comprehensive Logging**: All decisions and updates tracked
- **Fallback Mechanisms**: Graceful degradation when data unavailable
- **Incremental Validation**: Each phase verified before proceeding

### Research Risks
- **Baseline Establishment**: Comprehensive baseline metrics for comparison
- **Statistical Rigor**: Multiple runs with different random seeds
- **Reproducibility**: Detailed documentation and version control
- **Validation**: Cross-validation with real-world expectations

## Expected Research Outcomes

### Technical Contributions
1. **Methodology**: Novel preference-adaptive RL dispatching algorithm
2. **Implementation**: Complete MATSim-DRT integration framework
3. **Evaluation**: Comprehensive multi-scenario experimental pipeline

### Research Insights
1. **Performance Benefits**: Quantified improvements from preference adaptation
2. **User Heterogeneity**: Impact of personalized vs uniform service
3. **Demand Variability**: Robustness across different demand patterns
4. **Learning Dynamics**: Convergence properties and stability analysis

### Academic Output
1. **Primary Paper**: Preference-adaptive DRT dispatching with RL
2. **Conference Presentations**: TRB, ISTTT, or similar venues
3. **Open Source**: Reusable research framework for community
4. **Methodology Guide**: Best practices for preference-aware MaaS systems

## Next Steps

### Immediate Actions (Phase 1)
1. **Complete smoke testing** of population generation
2. **Implement RunHwaseongBaseline.java** with enhanced logging
3. **Create ExperimentRunner** for automated testing
4. **Establish baseline metrics** across all 45 scenarios

### Medium-term Goals (Phases 2-4)
1. **Integrate preference data** into cost calculation
2. **Implement basic RL learning** with contextual bandits
3. **Add real-time adaptation** capabilities
4. **Validate learning performance** against historical choices

### Long-term Vision (Phases 5-6)
1. **Deploy advanced RL** with policy gradient optimization
2. **Create comprehensive evaluation** framework
3. **Conduct systematic experiments** across all scenarios
4. **Produce publication-ready results** with statistical validation

This plan provides a complete roadmap from data preparation through advanced RL implementation to comprehensive evaluation, ensuring robust research outcomes suitable for top-tier academic publication.