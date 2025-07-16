# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a MATSim-based Java project for simulating Mobility as a Service (MaaS) and Mobility on Demand (MoD) systems. The project extends the MATSim mobility simulation framework with specialized modules for various transportation services including DRT (Demand Responsive Transport), taxi services, and autonomous vehicles.

## Build and Run Commands

### Building the Project
```bash
mvn clean compile
mvn package
```

### Running Simulations
```bash
# Run with Maven exec plugin
mvn exec:java -Dexec.mainClass=org.matsim.maas.RunMaas -Dexec.args="./scenarios/cottbus/drtconfig_stopbased.xml"

# Run compiled JAR
java -jar matsim-maas-master-SNAPSHOT.jar ./scenarios/cottbus/drtconfig_stopbased.xml

# Run with GUI
mvn exec:java -Dexec.mainClass=org.matsim.maas.RunMaasGui
```

### Testing
```bash
mvn test
```

## Code Architecture

### Core Structure
- **Main Entry Points**: `RunMaas.java` and `RunMaasGui.java` - primary simulation launchers
- **DRT Module**: `org.matsim.maas.drt` - Demand Responsive Transport implementations
- **Taxi Module**: `org.matsim.maas.taxi` - Traditional taxi and robotaxi services
- **Utils Package**: `org.matsim.maas.utils` - Utility classes and specialized components

### Key Components

#### Transportation Services
- **DRT (Demand Responsive Transport)**: Stop-based and door-to-door implementations
- **Taxi Services**: Traditional dispatch and autonomous vehicle variants
- **Robotaxi**: Shared autonomous vehicle simulation

#### Data Generation and Analysis
- `PopulationXMLGenerator` - Creates population files for simulations
- `FleetVehicleGenerator` - Generates vehicle fleet configurations
- `ValidationAnalyzer` - Validates simulation results
- `PerformanceMetricsCollector` - Collects and analyzes performance metrics

### Configuration System
- Uses MATSim's configuration framework with XML config files
- Supports multi-modal configuration (DRT, taxi, autonomous vehicles)
- Configuration files located in `data/` and `scenarios/` directories

### Data Management
- **Input Data**: Located in `data/` directory with subdirectories for different data types
- **Scenarios**: Test scenarios in `scenarios/` directory (Cottbus, Mielec, Hwaseong)
- **Output**: Simulation results written to `output/` directory with detailed analytics

## Development Patterns

### MATSim Integration
- Uses MATSim's AbstractModule pattern for dependency injection
- Extends MATSim's core simulation framework through contrib modules
- Follows MATSim's event-driven architecture for simulation components

### Modular Design
- Clear separation between different transportation service types
- Utility classes grouped by functionality
- Preference-aware components designed as pluggable modules

### Data Processing
- Heavy use of CSV files for configuration and results
- XML-based configuration following MATSim conventions
- Comprehensive logging and metrics collection

## Key Dependencies

- **MATSim Core**: Version 16.0-2024w15
- **MATSim Contrib Modules**: drt, dvrp, taxi, av, otfvis
- **Database**: SQLite for experimental results storage
- **Testing**: JUnit Jupiter for unit testing
- **Build Tool**: Maven with shade plugin for creating executable JARs

## Research Data Structure

This project is set up for comparative research between MATSim's baseline DRT algorithm and a preference-aware DRT system. The main data is located in the `/data` directory:

### Core Configuration Files
- `baseline_config.xml` - Standard MATSim DRT configuration

### Population Data (`/data/populations/`)
Population files contain agent plans and are organized by scenario and trip characteristics:
- **Base scenarios**: `base_population_NEW.xml.gz`, `S1_population_NEW.xml.gz`, etc.
- **Experiment variations**: Files follow pattern `{scenario}_trip{multiplier}_rule{rulenum}_population.xml.gz`
- **Trip multipliers**: 0.5, 1.0, 1.5 (representing different demand levels)
- **Rule numbers**: 1, 2, 3 (representing different preference rule sets)

### Vehicle Fleet Data (`/data/vehicles_*.xml`)
Fleet configuration files for different fleet sizes:
- `vehicles_4.xml` through `vehicles_80.xml` (4, 8, 10, 12, 20, 40, 80 vehicles)

### Virtual Stops Data (`/data/stops.xml`, `/data/candidate_stops/`)
- `stops.xml` - Main stops configuration
- `candidate_stops/hwaseong/stops.csv` - Candidate stop locations with coordinates and accessibility info

### User Preference Data (`/data/user_preference/`)
- `features.csv` - User choice features (access, wait, in-vehicle time, egress, constants)
- `weights.csv` - User preference weights for different cost components
- `user_history.csv` - Historical user choice data for learning

### Demand Data (`/data/demands/hwaseong/`)
- `rules/` - CSV files containing trip rules for different scenarios and configurations
- `real_time/` - Real-time demand data for validation
- `zones/` - Spatial zone data (shapefiles) for origin-destination analysis

### Network Data (`/data/networks/`)
- `hwaseong_network.xml` - Main network file for Hwaseong scenario
- `hwaseong_drive.graphml`, `hwaseong_walk.graphml` - Network variants
- `combine.py` - Script for network processing


Key experimental variables:
- **Demand levels**: 0.5x, 1.0x, 1.5x base demand
- **Fleet sizes**: 4-80 vehicles
- **Preference rules**: 3 different rule sets per scenario
- **Scenarios**: Base + S1-S4 representing different conditions

## Scenarios and Test Data

The project focuses on the **Hwaseong** scenario (Korean city) with real-world validation data.

Each scenario includes network files, population data, and service configurations.