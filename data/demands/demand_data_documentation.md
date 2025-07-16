# Hwaseong DRT Demand Dataset Documentation

## Overview

This document provides comprehensive documentation for the Hwaseong Living-Lab demand-responsive transit (DRT) dataset. The dataset contains synthetic but realistic demand patterns designed for testing DRT dispatching algorithms and operational strategies.

## Dataset Structure

```
data/demands/hwaseong/
├── zones/          # Spatial data files
├── rules/          # Static O/D matrices and scenario variants
└── real_time/      # Time-stamped trip request streams
```

## Spatial Framework

### Study Area
- **Location**: Hwaseong, South Korea
- **Center Point**: [37.206462°N, 126.827929°E]
- **Coverage**: ~3km radius circular area
- **Spatial Resolution**: H3 hexagons at resolution 9 (~120m across)

### Zone Structure
- **Total Hexagons**: 73 H3 hexagons
- **Zone Groups**: 9 aggregated zones (Groups 1-9)
- **Zone IDs**: Individual zones numbered 1-72
- **Aggregation Purpose**: Computational efficiency and data compactness

## File Descriptions

### 1. Spatial Data Files (`zones/` directory)

#### Zone Polygons (`OD_Zone.*`)
- **Format**: Shapefile (`.shp`, `.shx`, `.dbf`, `.prj`, `.cpg`, `.qmd`)
- **Content**: Polygon geometries for each of the 72 zones
- **Attributes**: 
  - Zone ID
  - Group assignment (1-9)
  - Commercial buildings count (CB)
  - Households count (LA)
  - Population count (Pop)

#### Zone Centroids (`Zone_centroid.*`)
- **Format**: Shapefile (`.shp`, `.shx`, `.dbf`, `.prj`, `.cpg`, `.qmd`)
- **Content**: Point geometries representing zone centers
- **Purpose**: Routing calculations and distance computations

### 2. Static O/D Matrices (`rules/` directory)

#### Base Scenarios
- **`base.csv`**: Reference demand pattern (2,016 daily trips)
- **`S1.csv`**: Temporal peaks scenario
- **`S2.csv`**: Spatial concentration scenario  
- **`S3.csv`**: Combined temporal and spatial effects
- **`S4.csv`**: Full smart-card derived patterns

#### Prediction Error Test Sets
- **Naming Convention**: `{scenario}_trip{multiplier}_rule{r}.csv`
- **Multipliers**: 0.5, 1.0, 1.5 (demand scaling factors)
- **Rules**: 1, 2, 3 (different random perturbation patterns)
- **Total Files**: 45 prediction error variants (5 scenarios × 3 multipliers × 3 rules)

#### File Format
```csv
o,d,t_7,t_8,t_9,t_10,t_11,t_12,t_13,t_14,t_15,t_16,t_17,t_18,t_19,t_20,t_21,t_22
1,1,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0
1,2,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0
...
```

**Field Definitions**:
- `o`: Origin zone ID (1-72)
- `d`: Destination zone ID (1-72)
- `t_7` to `t_22`: Hourly trip counts from 07:00 to 22:00

### 3. Real-time Trip Streams (`real_time/` directory)

#### Available Files
- **`base_real_time.csv`**: Base scenario trip stream
- **`S1_real_time.csv`**: S1 scenario trip stream
- **`S2_real_time.csv`**: S2 scenario trip stream
- **`S3_real_time.csv`**: S3 scenario trip stream
- **`S4_real_time.csv`**: S4 scenario trip stream

#### File Format
```csv
idx,o,d,hour,o_h3,d_h3,o_x,o_y,d_x,d_y,time
0,71,34,7,8930e038a0bffff,8930e039d17ffff,126.8410244074143,37.20620702903305,126.82097855680809,37.203846841343136,2024-10-24 07:00:34
```

**Field Definitions**:
- `idx`: Unique trip request ID
- `o`: Origin zone ID (1-72)
- `d`: Destination zone ID (1-72)
- `hour`: Hour of day (7-22)
- `o_h3`: Origin H3 hexagon ID
- `d_h3`: Destination H3 hexagon ID
- `o_x`, `o_y`: Origin coordinates (longitude, latitude)
- `d_x`, `d_y`: Destination coordinates (longitude, latitude)
- `time`: Exact timestamp of trip request

## Scenario Descriptions

### Base Scenario
- **Purpose**: Reference demand pattern
- **Characteristics**: Uniform temporal distribution
- **Daily Trips**: 2,016
- **Use Case**: Baseline comparison

### S1: Temporal Peaks
- **Purpose**: Realistic hourly ridership profiles
- **Characteristics**: Morning and evening peak periods
- **Modification**: Applied temporal multipliers to base demand
- **Use Case**: Testing algorithm response to demand surges

### S2: Spatial Concentration
- **Purpose**: Activity-based demand concentration
- **Characteristics**: Extra trips to zones 4 & 6 (high POI areas)
- **Modification**: Spatial reallocation of demand
- **Use Case**: Testing spatial optimization capabilities

### S3: Combined Effects
- **Purpose**: Realistic combined temporal and spatial patterns
- **Characteristics**: Both temporal peaks and spatial concentration
- **Modification**: S1 + S2 effects combined
- **Use Case**: Comprehensive algorithm testing

### S4: Smart-card Derived
- **Purpose**: Real-world temporal patterns
- **Characteristics**: Full smart-card time-of-day distributions
- **Modification**: Empirically-derived temporal patterns
- **Use Case**: Real-world validation

## Prediction Error Modeling

### Demand Multipliers
- **0.5×**: Under-prediction scenario (50% of actual demand)
- **1.0×**: Perfect prediction scenario (baseline)
- **1.5×**: Over-prediction scenario (150% of actual demand)

### Random Rules
- **Rule 1, 2, 3**: Different uniform random perturbation patterns
- **Purpose**: Model forecast uncertainty and variability
- **MAPE Values**: Documented in original PDF for each combination

## Data Quality and Validation

### Consistency Checks
- **Spatial Integrity**: All coordinates within study area bounds
- **Temporal Consistency**: All timestamps within 07:00-22:00 range
- **Zone Mapping**: All zone IDs valid (1-72)
- **Trip Conservation**: Total trips consistent across formats

### Known Limitations
- **Synthetic Data**: Not real passenger behavior
- **Simplified Geography**: H3 hexagons may not reflect actual road network
- **Fixed Service Hours**: Limited to 15-hour daily operation
- **Zone Aggregation**: Some spatial detail lost in 72-zone simplification

## Usage Guidelines

### For Algorithm Development
1. **Start with Base Scenario**: Establish baseline performance
2. **Test Temporal Robustness**: Use S1 for peak-hour handling
3. **Test Spatial Optimization**: Use S2 for demand concentration
4. **Comprehensive Evaluation**: Use S3 and S4 for realistic conditions
5. **Uncertainty Testing**: Use prediction error variants

### For Performance Evaluation
- **Metrics**: Service time, vehicle utilization, passenger waiting time
- **Statistical Testing**: Use multiple rule variants for significance
- **Scenario Comparison**: Evaluate across all scenarios for robustness
- **Scalability**: Test with different demand multipliers

### For Visualization
- **Spatial Analysis**: Use zone shapefiles for mapping
- **Temporal Patterns**: Plot hourly demand distributions
- **Flow Analysis**: Visualize O/D patterns and trip densities
- **Performance Dashboards**: Combine spatial and temporal metrics

## Technical Specifications

### Coordinate System
- **CRS**: WGS84 (EPSG:4326)
- **Units**: Decimal degrees
- **Precision**: ~1 meter accuracy

### H3 Hexagon Properties
- **Resolution**: 9
- **Average Area**: ~0.014 km²
- **Average Edge Length**: ~66 meters
- **Neighbor Count**: 6 (hexagonal grid)

### File Sizes
- **Static O/D Files**: ~358KB each (5,186 rows)
- **Real-time Files**: ~276KB each (~2,018 trips)
- **Spatial Files**: ~12KB (zone polygons), ~2KB (centroids)

## Research Applications

### Suitable Research Questions
1. **Dispatching Algorithms**: Real-time vehicle assignment optimization
2. **Demand Prediction**: Forecasting with uncertainty quantification
3. **Service Planning**: Fleet sizing and deployment strategies
4. **Routing Optimization**: Multi-objective vehicle routing problems
5. **Performance Analysis**: KPI development and benchmarking

### Experimental Design Considerations
- **Baseline Establishment**: Use base scenario for initial validation
- **Robustness Testing**: Include all scenarios and error variants
- **Statistical Significance**: Multiple runs with different random seeds
- **Comparative Analysis**: Benchmark against existing algorithms
- **Scalability Assessment**: Test with different fleet sizes and demand levels

## Citation and Attribution

When using this dataset, please cite:
- Original data source documentation
- Hwaseong Living-Lab project
- H3 spatial indexing system (Uber Technologies)
- Any derived analysis or algorithm papers

## Contact and Support

For questions about data usage, interpretation, or technical issues:
- Review original PDF documentation
- Check data consistency using provided validation scripts
- Consult H3 documentation for spatial operations
- Reference DRT literature for algorithm benchmarking 