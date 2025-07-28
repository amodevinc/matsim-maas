# Research Progress: Preference-Aware Adaptive DRT System

**Date**: July 28, 2025  
**Project**: *Preference-Aware Adaptive Dispatching for Demand-Responsive Transit Under Demand Uncertainty*  
**Framework**: MATSim-based simulation with reinforcement learning

---

## 📋 Project Overview

This research develops and evaluates a **preference-aware adaptive dispatching system** for Demand-Responsive Transit (DRT) using **MATSim simulation** and **policy-gradient reinforcement learning**. The system learns individual passenger preferences and incorporates them into ride assignment decisions to improve service quality under realistic demand uncertainty scenarios.

### Core Research Question
*Can an adaptive dispatching system that learns and respects passenger preferences outperform baseline heuristics in service quality and robustness under demand uncertainty?*

---

## ✅ Completed Components

### 1. Data Infrastructure & Population Generation ✅ **COMPLETE**

**Status**: Fully implemented and validated  
**Files**: 45+ population files generated across scenarios

#### Real-Time Demand Processing System
- **`RealTimeDemandPopulationGenerator`**: Converts real-time demand CSV to MATSim populations
- **`DemandDataReader`**: Processes and validates demand CSV files  
- **`CoordinateTransformationUtil`**: WGS84 to EPSG:5179 coordinate transformation
- **`UncertaintyProcessor`** & **`UncertaintyRule`**: Demand uncertainty modeling

#### Generated Population Files
- **Base scenarios**: `base`, `S1`, `S2`, `S3` real-time populations
- **Uncertainty variants**: 3 multipliers (0.5×, 1.0×, 1.5×) × 3 rules × 4 scenarios = 36 combinations
- **Simplified versions**: One-way trip populations for faster testing
- **Format**: Compressed MATSim XML with full activity chains and person attributes

### 2. Preference-Aware Cost System ✅ **COMPLETE**

**Status**: Fully implemented with RL integration  
**Location**: `src/main/java/org/matsim/maas/preference/`

#### Core Preference Components
- **`PrefAwareInsertionCostCalculator`**: DRT insertion cost calculator with user preferences
- **`PrefCostCalculator`**: Multi-component cost function (access, wait, in-vehicle, egress)
- **`UserPreferenceStore`**: Static preference data management
- **`DynamicUserPreferenceStore`**: Dynamic preference learning and updates
- **`PreferenceDataLoader`**: CSV-based preference data loading

#### MATSim Integration Modules
- **`PrefCostModule`**: Dependency injection for preference system
- **`PrefCostQSimModule`**: QSim integration for standard operation
- **`PrefCostQSimModuleWithRL`**: Enhanced version with RL learning support

### 3. Reinforcement Learning System ✅ **COMPLETE**

**Status**: Fully implemented and tested (38/38 tests passing)  
**Location**: `src/main/java/org/matsim/maas/rl/`, `src/main/java/org/matsim/maas/preference/learning/`

#### Learning Algorithm
- **`PolicyGradientPreferenceLearner`**: REINFORCE-based preference learning
- **`LearningConfiguration`**: Configurable learning parameters (rate, momentum, bounds)
- **Reward Signals**: Acceptance (+), rejection (-), trip completion feedback
- **Safety Mechanisms**: Weight bounds [-2.0, 2.0], max change limits (±10%)

#### Event System
- **`PreferenceUpdateEvent`**: MATSim events for preference changes
- **`PreferenceUpdateEventHandler`**: Event processing and analytics
- **`PreferenceUpdateTracker`**: Learning progress monitoring and CSV output

#### RL State Management
- **`RLStateManager`**: State tracking for RL decisions
- **`RLState`**: Current system state representation
- **`StateActionRecord`**: Action history for learning
- **`RewardCalculator`**: Multi-factor reward calculation

### 4. Simulation Runtime Components ✅ **COMPLETE**

**Status**: Ready for full simulation runs  
**Location**: Main execution classes and event handlers

#### Main Execution Classes
- **`RunPreferenceAwareDrt`**: Standard preference-aware DRT simulation
- **`RunPreferenceAwareDrtWithRL`**: RL-enhanced version with online learning
- **`RunHwaseongBaseline`**: Baseline comparison simulations

#### Event Processing
- **`PrefRLEventHandler`**: Standard RL event processing
- **`PrefRLEventHandlerWithLearning`**: Online learning integration
- **`BaselineMetricsHandler`**: Performance metrics collection

### 5. Testing & Validation Framework ✅ **COMPLETE**

**Status**: Comprehensive test suite passing  
**Coverage**: 38 tests across all major components

#### Test Categories
- **Data Tests**: DynamicUserPreferenceStore (11 tests)
- **Event Tests**: PreferenceUpdateEvent system (10 tests)  
- **Learning Tests**: PolicyGradientPreferenceLearner (10 tests)
- **Integration Tests**: End-to-end system validation (6 tests)
- **Smoke Tests**: Basic functionality verification (1 test)

#### Validated Features
- Thread safety (10 threads × 100 concurrent updates)
- Learning convergence and stability
- Event system integration
- Persistence and recovery
- Error handling and fallbacks

### 6. Configuration & Vehicle Fleets ✅ **COMPLETE**

**Status**: Ready for experiments  
**Location**: `data/` directory

#### Configuration Files
- **`baseline_config.xml`**: Standard MATSim DRT configuration
- **`prefAware_config.xml`**: Preference-aware system configuration
- **Vehicle fleets**: 4, 8, 10, 12, 20, 40, 80 vehicles (7 fleet sizes)

#### Supporting Data
- **Network**: `hwaseong_network.xml` (Korean city scenario)
- **Stops**: Virtual stop configurations with accessibility data
- **User preferences**: Feature weights and historical choice data

---

## 🔄 Integration Status

### Completed Integrations ✅
1. **MATSim Core**: Event system, dependency injection, QSim modules
2. **DRT Contrib**: Insertion cost calculators, request handling
3. **Preference System**: Static and dynamic preference management
4. **RL Learning**: Policy gradient learning with MATSim events
5. **Data Pipeline**: Real-time demand → MATSim populations

### Integration Points Ready ✅
- **PrefCostQSimModuleWithRL**: Connects all RL components
- **RunPreferenceAwareDrtWithRL**: Main execution entry point
- **Event flow**: User behavior → Learning → Preference updates → Cost calculation

---

## 📊 Experimental Setup Status

### Ready for Execution ✅

#### Scenarios
- **Base + S1-S4**: 5 demand scenarios with different spatial/temporal patterns
- **Fleet sizes**: 7 different fleet configurations (4-80 vehicles)
- **Uncertainty**: 9 demand prediction scenarios per base scenario
- **Total combinations**: 5 × 7 × 9 = 315 potential experiments

#### Key Performance Indicators (KPIs)
- Service rate (% of requests served)
- Average waiting time
- In-vehicle travel time  
- Detour factor
- Fleet utilization
- Preference satisfaction scores

#### Comparison Framework
- **Baseline**: Standard MATSim DRT dispatcher
- **Preference-aware**: Static preference integration
- **RL-enhanced**: Dynamic preference learning

---

## 📈 Recent Achievements

### Data Generation Pipeline ✅
- Successfully generated 45+ MATSim population files
- Implemented comprehensive uncertainty modeling
- Created both full and simplified population variants
- Validated coordinate transformations and network compatibility

### RL System Implementation ✅
- Completed policy gradient learning algorithm
- Implemented thread-safe dynamic preference updates
- Added comprehensive event tracking and analytics
- Achieved 100% test pass rate (38/38 tests)

### System Integration ✅  
- Connected all major components through MATSim's dependency injection
- Implemented proper event flow for learning
- Added safety mechanisms and error handling
- Created ready-to-run simulation configurations

---

## 🎯 Current Status: Ready for Full Experiments

### Immediate Next Steps
1. **Batch Experiment Execution**: Run full simulation matrix (baseline vs. preference-aware vs. RL)
2. **KPI Collection**: Gather comprehensive performance metrics across all scenarios
3. **Statistical Analysis**: Compare algorithms across uncertainty conditions
4. **Result Validation**: Verify learning behavior and preference adaptation

### Expected Outcomes
- Quantified performance improvements from preference-aware dispatching
- Analysis of robustness under demand uncertainty
- Insights into fleet sizing trade-offs
- Policy recommendations for DRT deployment

---

## 📝 Documentation Status

### Completed Documentation ✅
- **`REAL_TIME_DEMAND_POPULATION_GENERATOR.md`**: Complete data pipeline documentation
- **`SMOKE_TEST_RESULTS.md`**: Comprehensive testing validation
- **`CLAUDE.md`**: Project structure and build instructions
- **Code comments**: Extensive inline documentation across all components

### Research Outputs Ready
- **Methodology**: Preference-aware RL-based dispatcher design
- **Technical**: Reproducible MATSim experimentation pipeline  
- **Implementation**: Open-source codebase with full documentation

---

## 🔬 Research Contributions Ready for Validation

### 1. Methodological Contribution
- **Novel approach**: RL-based preference learning in DRT dispatching
- **Safety mechanisms**: Bounded learning with stability guarantees
- **Real-world applicability**: Uncertainty modeling based on actual demand patterns

### 2. Technical Contribution  
- **MATSim extensions**: Preference-aware cost calculations and RL integration
- **Reproducible pipeline**: Complete experimental framework for DRT research
- **Performance optimization**: Efficient concurrent learning with thread safety

### 3. Policy Insights (Pending Experiments)
- Fleet sizing recommendations under uncertainty
- Trade-offs between service quality and robustness
- User-centric service design implications

---

## 🚀 Readiness Assessment

**Overall Status**: ✅ **READY FOR FULL EXPERIMENTAL VALIDATION**

- ✅ All core components implemented and tested
- ✅ Data pipeline generating valid MATSim populations  
- ✅ RL system stable and validated
- ✅ Integration testing complete
- ✅ Configurations and scenarios prepared
- ✅ Documentation comprehensive

**Next Phase**: Execute comprehensive simulation experiments and analyze results for TRB paper submission.

The research implementation is **complete and validated**, ready to generate the experimental results needed to support the core research hypothesis about preference-aware adaptive dispatching under demand uncertainty.