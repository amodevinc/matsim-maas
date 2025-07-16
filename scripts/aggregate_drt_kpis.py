#!/usr/bin/env python3

import argparse
import pandas as pd
import os
from pathlib import Path
import glob

def find_stats_file(directory, pattern):
    """Find a stats file matching the pattern in the directory."""
    matches = list(directory.glob(f"*{pattern}"))
    if not matches:
        print(f"Warning: No files matching *{pattern} found in {directory}")
        return None
    return matches[0]

def load_drt_stats(filepath):
    """Load DRT stats CSV with proper delimiter handling."""
    try:
        # Try semicolon first (MATSim default)
        df = pd.read_csv(filepath, sep=';')
    except:
        try:
            # Fallback to comma
            df = pd.read_csv(filepath)
        except Exception as e:
            print(f"Error reading {filepath}: {e}")
            return None
    return df

def aggregate_kpis(input_dir, output_dir):
    """Aggregate KPIs from multiple DRT stats files into a single summary."""
    input_path = Path(input_dir)
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    # Find the stats files (they may have a prefix like "hwaseong_NEW.")
    customer_stats_file = find_stats_file(input_path, "drt_customer_stats_drt.csv")
    vehicle_stats_file = find_stats_file(input_path, "drt_vehicle_stats_drt.csv")
    sharing_stats_file = find_stats_file(input_path, "drt_sharing_metrics_drt.csv")

    if not all([customer_stats_file, vehicle_stats_file, sharing_stats_file]):
        print("Error: Could not find all required stats files")
        return False

    # Load the stats files
    customer_stats = load_drt_stats(customer_stats_file)
    vehicle_stats = load_drt_stats(vehicle_stats_file)
    sharing_stats = load_drt_stats(sharing_stats_file)

    if not all([customer_stats is not None, vehicle_stats is not None, sharing_stats is not None]):
        print("Error: Could not load all required stats files")
        return False

    # Extract key KPIs from customer stats
    customer_kpis = customer_stats[[
        'runId', 'iteration',
        'rides', 'rides_pax', 'wait_average', 'wait_max', 'wait_p95',
        'inVehicleTravelTime_mean', 'totalTravelTime_mean',
        'distance_m_mean', 'directDistance_m_mean',
        'rejections', 'rejectionRate'
    ]]

    # Extract key KPIs from vehicle stats
    vehicle_kpis = vehicle_stats[[
        'runId', 'iteration',
        'vehicles', 'totalDistance', 'totalEmptyDistance', 'emptyRatio',
        'totalPassengerDistanceTraveled', 'averageDrivenDistance',
        'minShareIdleVehicles'
    ]]

    # Extract key KPIs from sharing stats
    sharing_kpis = sharing_stats[[
        'runId', 'iteration',
        'poolingRate', 'sharingFactor', 'nPooled', 'nTotal'
    ]]

    # Merge all KPIs on runId and iteration
    merged_kpis = customer_kpis.merge(
        vehicle_kpis, on=['runId', 'iteration']
    ).merge(
        sharing_kpis, on=['runId', 'iteration']
    )

    # Add derived KPIs
    merged_kpis['detour_factor'] = merged_kpis['distance_m_mean'] / merged_kpis['directDistance_m_mean']
    merged_kpis['avg_occupancy'] = merged_kpis['totalPassengerDistanceTraveled'] / merged_kpis['totalDistance']
    
    # Save summary
    run_id = customer_stats['runId'].iloc[0]
    output_file = output_path / f"{run_id}_summary_kpis.csv"
    merged_kpis.to_csv(output_file, index=False)
    print(f"Summary KPIs saved to: {output_file}")

    # Generate quick stats for last iteration
    last_iter = merged_kpis.iloc[-1]
    print("\nFinal Iteration KPIs:")
    print(f"Service Rate: {(1 - last_iter['rejectionRate'])*100:.1f}%")
    print(f"Avg Wait Time: {last_iter['wait_average']:.1f}s")
    print(f"Avg Travel Time: {last_iter['inVehicleTravelTime_mean']:.1f}s")
    print(f"Pooling Rate: {last_iter['poolingRate']*100:.1f}%")
    print(f"Fleet Utilization: {(1 - last_iter['minShareIdleVehicles'])*100:.1f}%")

    return True

def main():
    parser = argparse.ArgumentParser(description='Aggregate DRT KPIs from MATSim output files')
    parser.add_argument('input_dir', help='Directory containing DRT output CSVs')
    parser.add_argument('output_dir', help='Directory to save aggregated KPIs')
    args = parser.parse_args()

    if aggregate_kpis(args.input_dir, args.output_dir):
        print("KPI aggregation completed successfully")
    else:
        print("KPI aggregation failed")

if __name__ == "__main__":
    main() 