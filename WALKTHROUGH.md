# Reinforcement Learning Enhanced Preference-Aware DRT System Walkthrough

This document provides a comprehensive walkthrough of the reinforcement learning (RL) system that has been integrated into the MATSim-based preference-aware DRT simulation. The system enables dynamic learning and updating of user preferences based on their ride acceptance/rejection behavior and trip satisfaction.

## 🏗️ System Architecture Overview

The RL-enhanced system consists of several key components working together:

```
User Makes Request → Preference-Aware Cost Calculator → DRT Optimizer
                                   ↑                        ↓
                         Dynamic Preference Store    Accept/Reject Decision
                                   ↑                        ↓
                         Preference Learner ← RL Event Handler
                                   ↑                        ↓
                         Learning Configuration    Update Events & Metrics
```

## 📁 File Structure

```
src/main/java/org/matsim/maas/preference/
├── data/
│   ├── DynamicUserPreferenceStore.java     # Thread-safe preference storage with updates
│   ├── UserPreferenceStore.java            # Base preference storage (existing)
│   └── PreferenceDataLoader.java           # Data loading utilities (existing)
├── events/
│   ├── PreferenceUpdateEvent.java          # Event for preference changes
│   ├── PreferenceUpdateEventHandler.java   # Handler interface
│   └── PreferenceUpdateTracker.java        # Analytics and monitoring
├── learning/
│   ├── PreferenceLearner.java              # RL algorithm interface
│   ├── PolicyGradientPreferenceLearner.java # REINFORCE implementation
│   └── LearningConfiguration.java          # RL hyperparameters
└── cost/
    ├── PrefAwareInsertionCostCalculator.java # Cost calculator (existing)
    └── PrefCostQSimModule.java              # MATSim integration (existing)

src/test/java/org/matsim/maas/preference/
├── data/
│   └── DynamicUserPreferenceStoreTest.java # Comprehensive unit tests
└── events/
    └── PreferenceUpdateEventTest.java      # Event system tests
```

## 🔧 Component Deep Dive

### 1. DynamicUserPreferenceStore (`data/DynamicUserPreferenceStore.java`)

**Purpose**: Thread-safe storage that extends the base UserPreferenceStore with dynamic update capabilities.

**Key Features**:
- **Thread Safety**: Uses ReadWriteLock for concurrent access during MATSim's parallel simulation
- **Constrained Updates**: Limits weight changes to ±10% per update, bounds weights to [-2.0, 2.0]
- **History Tracking**: Maintains update history for each user for analysis
- **Persistence**: Saves learned preferences to CSV files for continuation across runs
- **Batch Operations**: Supports bulk updates for efficiency

**Core Methods**:
```java
// Update a single user's preferences with safety constraints
boolean updateUserPreference(Id<Person> personId, 
                            double accessDelta, double waitDelta, 
                            double ivtDelta, double egressDelta)

// Batch update multiple users
void batchUpdatePreferences(Map<Id<Person>, PreferenceUpdate> updates)

// Persist learned preferences to file
void persistPreferences(int iteration)
```

**Safety Mechanisms**:
- Maximum 10% change per update to prevent instability
- Weight bounds [-2.0, 2.0] to maintain reasonable preference ranges
- Atomic updates with rollback capability
- Thread-safe concurrent access patterns

### 2. PreferenceUpdateEvent System (`events/`)

**Purpose**: MATSim event system integration for tracking and analyzing preference changes.

#### PreferenceUpdateEvent
- Immutable event following MATSim conventions
- Captures old weights, new weights, update reason, and learning reward
- Provides delta calculations and update magnitude metrics
- Integrates with MATSim's event infrastructure

#### PreferenceUpdateTracker
- Implements both PreferenceUpdateEventHandler and IterationEndsListener
- Tracks all preference updates during simulation
- Generates detailed analytics and CSV outputs
- Provides real-time statistics for monitoring learning progress

**Output Files**:
- `preference_updates_iter_X.csv`: Detailed log of all updates
- `preference_learning_summary.csv`: Iteration-level statistics
- `preference_person_stats_iter_X.csv`: Per-person learning metrics

### 3. Learning System (`learning/`)

#### PreferenceLearner Interface
**Purpose**: Abstract interface for different RL algorithms.

**Core Learning Methods**:
```java
// Learn from ride acceptance (positive reward)
PreferenceUpdate learnFromAcceptance(Id<Person> personId, ...)

// Learn from ride rejection (negative reward)  
PreferenceUpdate learnFromRejection(Id<Person> personId, ...)

// Learn from trip completion (satisfaction-based reward)
PreferenceUpdate learnFromCompletion(Id<Person> personId, ...)

// Batch learning for stability
Map<Id<Person>, PreferenceUpdate> batchLearn(...)
```

#### LearningConfiguration
**Purpose**: Comprehensive configuration management for RL hyperparameters.

**Key Parameters**:
- **Learning Rate**: Initial rate (0.005), decay (0.001), minimum (0.0001)
- **Momentum**: 0.9 for gradient smoothing
- **Batch Size**: 16 experiences per update
- **Exploration**: Initial rate (0.1) with decay for exploration-exploitation balance
- **Regularization**: L2 weight (0.001) and weight decay (0.0001)
- **Safety**: Maximum weight change (0.1) and total change (0.3) constraints

**Pre-configured Options**:
```java
LearningConfiguration.createDefault()       // Balanced learning
LearningConfiguration.createConservative()  // Slow, stable learning
LearningConfiguration.createAggressive()    // Fast learning
```

#### PolicyGradientPreferenceLearner
**Purpose**: REINFORCE algorithm implementation for preference learning.

**Algorithm Overview**:
1. **Policy Gradient Calculation**: Uses REINFORCE to estimate gradients from rewards
2. **Momentum Updates**: Applies momentum (0.9) for smoother learning
3. **Gradient Clipping**: Prevents instability from large gradients
4. **Exploration Noise**: Adds Gaussian noise for better exploration
5. **Batch Processing**: Groups experiences for stable updates
6. **Safety Constraints**: Ensures updates don't destabilize the system

**Mathematical Foundation**:
```
Policy: π(accept|features, weights) = σ(weights · features)
Gradient: ∇ log π(action|state) × reward
Update: weights ← weights + learning_rate × momentum_gradient
```

**Key Features**:
- Normalizes features to prevent gradient explosion
- Applies L2 regularization to prevent overfitting
- Uses temperature scaling in softmax for smoother probabilities
- Implements batch normalization for stable batch updates

## 🔄 Learning Process Flow

### 1. **Request Submission**
```
User submits DRT request → PrefRLEventHandler captures event
```

### 2. **Cost Calculation**
```
PrefAwareInsertionCostCalculator → Uses current preferences from DynamicUserPreferenceStore
                                 → Calculates preference-adjusted cost
```

### 3. **Decision & Learning**
```
DRT System Decision:
├─ Accept → PolicyGradientPreferenceLearner.learnFromAcceptance()
├─ Reject → PolicyGradientPreferenceLearner.learnFromRejection()
└─ Complete → PolicyGradientPreferenceLearner.learnFromCompletion()
```

### 4. **Preference Update**
```
Learner calculates weight deltas → DynamicUserPreferenceStore.updateUserPreference()
                                 → PreferenceUpdateEvent fired
                                 → PreferenceUpdateTracker logs analytics
```

### 5. **Iteration End**
```
MATSim iteration ends → Batch learning applied
                     → Preferences persisted to CSV
                     → Learning parameters updated (decay rates)
                     → Analytics written to files
```

## 📊 Data Flow & Analytics

### Input Data
- **User Preferences**: `data/user_preference/weights.csv` (initial weights)
- **User History**: `data/user_preference/user_history.csv` (past choices)
- **Configuration**: Learning parameters via LearningConfiguration

### Learning Data
- **Features**: [access_time, wait_time, ivt_time, egress_time]
- **Rewards**: Based on acceptance (+), rejection (-), completion (satisfaction)
- **Constraints**: Safety bounds and change limits

### Output Analytics
1. **Real-time Metrics**: Console logging during simulation
2. **Detailed Logs**: CSV files with all preference updates
3. **Summary Statistics**: Iteration-level learning progress
4. **Personal Analytics**: Per-user learning trajectories
5. **Convergence Monitoring**: Update magnitude and frequency tracking

## 🧪 Testing Strategy

### Unit Tests Coverage
- **DynamicUserPreferenceStore**: 11 comprehensive test cases
  - Basic updates, constraints, thread safety, persistence
  - Batch operations, history tracking, file I/O
  - Concurrent access patterns, error handling
- **PreferenceUpdateEvent System**: 10 test cases
  - Event creation, tracking, analytics generation
  - File output verification, statistics calculation

### Test Features
- **Thread Safety**: Concurrent update testing with 10 threads × 100 updates
- **Constraint Validation**: Weight bounds and change limits
- **Persistence**: File I/O and data integrity
- **Event Integration**: MATSim event system compatibility
- **Analytics Accuracy**: Statistical calculation verification

## 🔧 Integration Points

### Existing System Integration
1. **PrefAwareInsertionCostCalculator**: Will be updated to use DynamicUserPreferenceStore
2. **PrefCostQSimModule**: Will provide dynamic store instead of static
3. **PrefRLEventHandler**: Will be enhanced to integrate PolicyGradientPreferenceLearner

### MATSim Compatibility
- Follows MATSim event patterns and naming conventions
- Uses MATSim's dependency injection (Guice) patterns
- Implements proper AbstractDvrpModeQSimModule extensions
- Maintains backward compatibility with static preferences

## 🎯 Key Benefits

### 1. **Adaptive Learning**
- User preferences evolve based on actual behavior
- System learns from both positive and negative feedback
- Gradual adaptation prevents sudden preference shifts

### 2. **Robust Implementation**
- Thread-safe for MATSim's parallel execution
- Fail-safe fallbacks to static preferences
- Comprehensive safety constraints and bounds

### 3. **Research-Ready**
- Extensive analytics and monitoring capabilities
- Configurable hyperparameters for experimentation
- Reproducible results with controlled randomness

### 4. **Scalable Architecture**
- Interface-based design allows different RL algorithms
- Efficient batch processing for large user populations
- Memory-conscious with configurable experience buffers

## 🔄 Next Steps

### Remaining Implementation Tasks
1. **PolicyGradientPreferenceLearner Tests**: Comprehensive unit testing
2. **PrefCostQSimModule Update**: Integration with dynamic store
3. **PrefRLEventHandler Enhancement**: Integrate learner with event handling
4. **Integration Testing**: End-to-end system validation
5. **Performance Optimization**: Profiling and optimization

### Research Opportunities
1. **Algorithm Comparison**: Test different RL algorithms (Q-learning, Actor-Critic)
2. **Hyperparameter Tuning**: Systematic optimization of learning parameters
3. **Convergence Analysis**: Study learning patterns and convergence properties
4. **Real-world Validation**: Compare learned vs. stated preferences

## 🏁 Conclusion

The RL-enhanced preference-aware DRT system provides a robust foundation for dynamic preference learning in transportation systems. The implementation follows MATSim best practices while introducing sophisticated machine learning capabilities that can adapt to user behavior in real-time.

The system is designed for both research and practical applications, with extensive configuration options, comprehensive analytics, and safety mechanisms that ensure stable operation even with dynamic preference updates.