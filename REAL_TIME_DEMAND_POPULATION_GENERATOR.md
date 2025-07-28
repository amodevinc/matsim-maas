# Real-Time Demand Population Generator

This module provides comprehensive functionality to read real-time demand data files and generate MATSim population files with uncertainty modeling for demand prediction research.

## Overview

The module processes CSV files containing real-time demand requests and converts them into MATSim-compatible population XML files. It supports:

- **Coordinate Transformation**: Converts WGS84 coordinates to projected coordinates (EPSG:5179) compatible with MATSim
- **Uncertainty Modeling**: Applies demand prediction error rules with different multipliers and perturbation patterns
- **Multiple Scenarios**: Processes base, S1, S2, S3, and S4 scenarios automatically
- **Population Generation**: Creates both full round-trip and simplified one-way trip populations

## Module Components

### 1. CoordinateTransformationUtil
Handles coordinate system transformations between WGS84 (longitude/latitude) and Korean projected coordinates (EPSG:5179).

**Key Features:**
- Transforms real-time demand coordinates to MATSim network coordinate system
- Provides bidirectional transformation utilities
- Optimized for Korean coordinate systems

### 2. DemandRequest
Represents a single demand request with all spatial and temporal information.

**Contains:**
- Original zone IDs and H3 identifiers
- WGS84 and projected coordinates for origin/destination
- Precise request timing information
- Utility methods for MATSim integration

### 3. DemandDataReader
Reads and parses real-time demand CSV files.

**Capabilities:**
- Validates CSV header format
- Handles coordinate transformations automatically
- Supports reading single files or entire directories
- Filters by scenario name
- Robust error handling and logging

### 4. UncertaintyRule & UncertaintyProcessor
Implements demand uncertainty modeling for prediction error research.

**Uncertainty Rules:**
- **Multipliers**: 0.5× (under-prediction), 1.0× (perfect), 1.5× (over-prediction)
- **Rule Numbers**: 1, 2, 3 (different random perturbation patterns)
- **Temporal Perturbation**: Adds realistic noise to request timing

### 5. PopulationGenerator (Enhanced)
Generates MATSim population XML files from demand requests.

**Features:**
- Supports both O/D matrix (original) and real-time demand processing
- Creates proper MATSim plans with activities and legs
- Links activities to closest network nodes
- Adds person attributes for analysis
- Supports simplified population generation

### 6. RealTimeDemandPopulationGenerator (Main Module)
Orchestrates the entire population generation process.

**Functions:**
- Processes all scenarios automatically
- Generates base populations and uncertainty variants
- Creates comprehensive file naming conventions
- Provides statistical reporting

## File Structure

```
data/demands/hwaseong/real_time/
├── valid_requests_base_real_time.csv
├── valid_requests_S1_real_time.csv
├── valid_requests_S2_real_time.csv
├── valid_requests_S3_real_time.csv
└── valid_requests_S4_real_time.csv

output: data/populations_from_real_time/
├── base_real_time_population.xml.gz
├── base_trip0.5_rule1_real_time_population.xml.gz
├── base_trip0.5_rule2_real_time_population.xml.gz
├── base_trip0.5_rule3_real_time_population.xml.gz
├── base_trip1.0_rule1_real_time_population.xml.gz
├── ... (all uncertainty variants)
└── simplified/
    ├── base_real_time_simplified_population.xml.gz
    └── ... (simplified versions)
```

## Usage

### 1. Basic Usage (Generate All Populations)

```bash
# Using Maven
mvn exec:java -Dexec.mainClass=org.matsim.maas.utils.RealTimeDemandPopulationGenerator

# Using compiled JAR
java -cp matsim-maas-master-SNAPSHOT.jar org.matsim.maas.utils.RealTimeDemandPopulationGenerator
```

### 2. Custom Paths

```bash
java -cp matsim-maas-master-SNAPSHOT.jar \
  org.matsim.maas.utils.RealTimeDemandPopulationGenerator \
  /path/to/demand/files \
  /path/to/output/directory
```

### 3. Single Population Generation

```bash
java -cp matsim-maas-master-SNAPSHOT.jar \
  org.matsim.maas.utils.RealTimeDemandPopulationGenerator \
  /path/to/demand/files \
  /path/to/output/directory \
  S1 \
  1.5_3
```

### 4. Programmatic Usage

```java
// Initialize the generator
RealTimeDemandPopulationGenerator generator = new RealTimeDemandPopulationGenerator();

// Generate all populations
generator.generateAllPopulations("data/demands/hwaseong/real_time", "output/populations");

// Generate simplified populations
generator.generateSimplifiedPopulations("data/demands/hwaseong/real_time", "output/populations");

// Generate single population with uncertainty
UncertaintyRule rule = new UncertaintyRule(1.5, 2); // 1.5x demand, rule 2
generator.generateSinglePopulation("data/demands/hwaseong/real_time", "output", "S1", rule);
```

## Data Format

### Input CSV Format
```csv
idx,o,d,hour,o_h3,d_h3,o_x,o_y,d_x,d_y,time
2,50,6,7,8930e038e4fffff,8930e039dabffff,126.82237537318804,37.211429475524056,126.82332995061448,37.204166667431096,2024-10-24 07:01:56
```

**Fields:**
- `idx`: Unique request identifier
- `o`, `d`: Origin and destination zone IDs (1-72)
- `hour`: Hour of day (7-22)
- `o_h3`, `d_h3`: H3 hexagon identifiers
- `o_x`, `o_y`: Origin longitude, latitude (WGS84)
- `d_x`, `d_y`: Destination longitude, latitude (WGS84)
- `time`: Exact timestamp of request

### Output Population Features
- **Person IDs**: `{scenario}_person_{request_idx}` or `{scenario}_{rule}_person_{request_idx}`
- **Activities**: Home (origin) → Trip (destination) → Home (return)
- **Transport Mode**: DRT for all legs
- **Timing**: Based on actual request timestamps
- **Coordinates**: Automatically transformed to projected system
- **Attributes**: Preserves original zone IDs, H3 codes, and timing information

## Uncertainty Modeling

### Demand Multipliers
- **0.5×**: Simulates under-prediction (50% of actual demand)
- **1.0×**: Perfect prediction baseline
- **1.5×**: Simulates over-prediction (150% of actual demand)

### Rule Patterns
- **Rule 1**: ±1 minute temporal perturbation (low uncertainty)
- **Rule 2**: ±3 minutes temporal perturbation (medium uncertainty)  
- **Rule 3**: ±5 minutes temporal perturbation (high uncertainty)

### Implementation Details
- **Sampling**: Under-prediction randomly samples subset of requests
- **Duplication**: Over-prediction duplicates requests with temporal variations
- **Perturbation**: Gaussian noise applied to request timing
- **Bounds**: All times constrained to service hours (07:00-22:00)
- **Reproducibility**: Deterministic random seeds based on rule parameters

## Integration with MATSim

### Network Compatibility
- Uses existing Hwaseong network (`data/networks/hwaseong_network.xml`)
- Automatically finds closest network links for activities
- Maintains coordinate system consistency (EPSG:5179)

### Population Attributes
Each generated person includes:
```xml
<person id="base_person_2">
  <attributes>
    <attribute name="original_request_idx" class="java.lang.Integer">2</attribute>
    <attribute name="origin_zone" class="java.lang.Integer">50</attribute>
    <attribute name="destination_zone" class="java.lang.Integer">6</attribute>
    <attribute name="request_hour" class="java.lang.Integer">7</attribute>
    <attribute name="origin_h3" class="java.lang.String">8930e038e4fffff</attribute>
    <attribute name="destination_h3" class="java.lang.String">8930e039dabffff</attribute>
  </attributes>
  <plan>
    <!-- Activities and legs here -->
  </plan>
</person>
```

### Activity Types
- **home**: Origin and return activities
- **trip**: Destination activities (full plans)
- **origin/destination**: Simplified one-way plans

## Performance Considerations

### Coordinate Transformation
- Uses efficient MATSim transformation utilities
- Caches transformation objects to avoid repeated initialization
- Batch processes coordinates for optimal performance

### Memory Usage
- Processes scenarios sequentially to manage memory
- Clears populations between generations
- Suitable for large demand datasets (thousands of requests)

### File I/O
- Creates compressed XML output (.xml.gz) by default
- Validates input file formats before processing
- Robust error handling prevents data loss

## Error Handling

### Input Validation
- Validates CSV header format
- Checks coordinate ranges and zone ID validity
- Handles missing or malformed data gracefully

### Logging
- Comprehensive logging with different levels (INFO, WARN, ERROR)
- Progress tracking for large batch operations
- Detailed error messages for troubleshooting

### Recovery
- Continues processing other scenarios if one fails
- Skips invalid records rather than terminating
- Reports statistics on successful vs. failed operations

## Extension Points

### Custom Uncertainty Rules
```java
// Create custom uncertainty patterns
UncertaintyRule customRule = new UncertaintyRule(2.0, 4); // 2x demand, rule 4
List<DemandRequest> modified = processor.applyUncertaintyRule(baseDemand, customRule);
```

### Custom Population Attributes
```java
// Extend PopulationGenerator to add custom attributes
person.getAttributes().putAttribute("custom_attribute", customValue);
```

### Alternative Coordinate Systems
```java
// Modify CoordinateTransformationUtil for different projections
private static final String PROJECTED_CRS = "EPSG:32652"; // UTM Zone 52N
```

## Troubleshooting

### Common Issues

1. **Coordinate Transformation Errors**
   - Verify network file uses EPSG:5179 coordinates
   - Check that demand coordinates are valid WGS84

2. **Missing Demand Files**
   - Ensure files follow naming convention: `valid_requests_{scenario}_real_time.csv`
   - Check file permissions and paths

3. **Memory Issues**
   - Increase JVM heap size: `-Xmx4g`
   - Process scenarios individually rather than in batch

4. **Invalid Network Links**
   - Verify network file exists and is readable
   - Check network coordinate bounds match transformed coordinates

### Validation
```bash
# Compile and test basic functionality
mvn clean compile
mvn test

# Generate single test population
java -cp target/classes org.matsim.maas.utils.RealTimeDemandPopulationGenerator \
  data/demands/hwaseong/real_time \
  test_output \
  base \
  1.0_1
```

## Research Applications

This module supports various DRT research scenarios:

### Algorithm Testing
- Compare baseline vs. preference-aware DRT algorithms
- Test robustness under demand uncertainty
- Evaluate performance across different scenarios

### Demand Prediction Studies
- Model impact of prediction errors on service quality
- Compare different uncertainty patterns
- Analyze sensitivity to demand variations

### Fleet Optimization
- Test fleet sizing under uncertain demand
- Optimize vehicle deployment strategies
- Evaluate service area coverage

### Temporal Analysis
- Study impact of temporal perturbations
- Analyze peak hour vs. off-peak performance
- Model real-world demand variability

The module provides a comprehensive foundation for MATSim-based DRT research with realistic demand modeling and uncertainty quantification.