#!/usr/bin/env python3

import argparse
import pandas as pd
import numpy as np
from pathlib import Path
from scipy import stats
import sys
import os

# Add the scripts directory to the path so we can import our KPI aggregator
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from aggregate_drt_kpis import aggregate_kpis

def generate_kpis_if_needed(directory, temp_dir):
    """Generate KPI summary if it doesn't exist."""
    kpi_dir = temp_dir / "temp_kpis"
    kpi_dir.mkdir(parents=True, exist_ok=True)
    
    if not aggregate_kpis(directory, kpi_dir):
        return None
    
    # Find the generated KPI file
    kpi_files = list(kpi_dir.glob("*_summary_kpis.csv"))
    if not kpi_files:
        return None
    
    return kpi_files[0]

def load_kpi_summary(kpi_file):
    """Load KPI summary CSV."""
    try:
        return pd.read_csv(kpi_file)
    except Exception as e:
        print(f"Error loading KPI summary {kpi_file}: {e}")
        return None

def calculate_statistical_significance(baseline_values, preference_values, alpha=0.05):
    """Calculate statistical significance using appropriate tests."""
    # Use Mann-Whitney U test (non-parametric) for robustness
    try:
        statistic, p_value = stats.mannwhitneyu(baseline_values, preference_values, alternative='two-sided')
        is_significant = p_value < alpha
        return p_value, is_significant
    except:
        return np.nan, False

def compare_runs(baseline_dir, preference_dir, output_dir):
    """Compare two MATSim DRT runs and generate comparison metrics."""
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    temp_dir = output_path / "temp"
    
    print(f"Comparing baseline: {baseline_dir}")
    print(f"With preference-aware: {preference_dir}")
    
    # Generate KPI summaries for both runs
    baseline_kpi_file = generate_kpis_if_needed(baseline_dir, temp_dir)
    preference_kpi_file = generate_kpis_if_needed(preference_dir, temp_dir)
    
    if not baseline_kpi_file or not preference_kpi_file:
        print("Error: Could not generate KPI summaries for one or both runs")
        return False
    
    # Load KPI summaries
    baseline_kpis = load_kpi_summary(baseline_kpi_file)
    preference_kpis = load_kpi_summary(preference_kpi_file)
    
    if baseline_kpis is None or preference_kpis is None:
        print("Error: Could not load KPI summaries")
        return False
    
    # Align by iteration (in case they have different numbers of iterations)
    max_iter = min(baseline_kpis['iteration'].max(), preference_kpis['iteration'].max())
    baseline_aligned = baseline_kpis[baseline_kpis['iteration'] <= max_iter].copy()
    preference_aligned = preference_kpis[preference_kpis['iteration'] <= max_iter].copy()
    
    # Create comparison dataframe
    comparison = pd.DataFrame()
    comparison['iteration'] = baseline_aligned['iteration']
    comparison['baseline_runId'] = baseline_aligned['runId']
    comparison['preference_runId'] = preference_aligned['runId']
    
    # Key metrics to compare
    metrics_to_compare = [
        'rides', 'wait_average', 'wait_max', 'wait_p95',
        'inVehicleTravelTime_mean', 'totalTravelTime_mean',
        'rejectionRate', 'vehicles', 'totalDistance', 'emptyRatio',
        'poolingRate', 'sharingFactor', 'detour_factor', 'avg_occupancy'
    ]
    
    # Add baseline, preference, and delta columns for each metric
    for metric in metrics_to_compare:
        if metric in baseline_aligned.columns and metric in preference_aligned.columns:
            comparison[f'baseline_{metric}'] = baseline_aligned[metric].values
            comparison[f'preference_{metric}'] = preference_aligned[metric].values
            comparison[f'delta_{metric}'] = preference_aligned[metric].values - baseline_aligned[metric].values
            comparison[f'delta_pct_{metric}'] = ((preference_aligned[metric].values - baseline_aligned[metric].values) / 
                                                baseline_aligned[metric].values * 100)
    
    # Calculate overall statistics for final iterations
    final_baseline = baseline_aligned.iloc[-1]
    final_preference = preference_aligned.iloc[-1]
    
    # Generate summary statistics
    summary_stats = {}
    for metric in metrics_to_compare:
        if metric in baseline_aligned.columns and metric in preference_aligned.columns:
            baseline_vals = baseline_aligned[metric].values
            preference_vals = preference_aligned[metric].values
            
            p_value, is_significant = calculate_statistical_significance(baseline_vals, preference_vals)
            
            summary_stats[metric] = {
                'baseline_final': final_baseline[metric],
                'preference_final': final_preference[metric],
                'delta_final': final_preference[metric] - final_baseline[metric],
                'delta_pct_final': ((final_preference[metric] - final_baseline[metric]) / final_baseline[metric] * 100),
                'p_value': p_value,
                'is_significant': is_significant
            }
    
    # Save detailed comparison
    baseline_run_id = baseline_aligned['runId'].iloc[0]
    preference_run_id = preference_aligned['runId'].iloc[0]
    comparison_file = output_path / f"comparison_{baseline_run_id}_vs_{preference_run_id}.csv"
    comparison.to_csv(comparison_file, index=False)
    print(f"Detailed comparison saved to: {comparison_file}")
    
    # Save summary statistics
    summary_df = pd.DataFrame.from_dict(summary_stats, orient='index')
    summary_file = output_path / f"summary_{baseline_run_id}_vs_{preference_run_id}.csv"
    summary_df.to_csv(summary_file)
    print(f"Summary statistics saved to: {summary_file}")
    
    # Print key findings
    print("\n=== KEY FINDINGS ===")
    print(f"Service Rate: {final_baseline['rejectionRate']:.1%} → {final_preference['rejectionRate']:.1%} "
          f"(Δ{(final_preference['rejectionRate'] - final_baseline['rejectionRate'])*100:+.1f}pp)")
    print(f"Avg Wait Time: {final_baseline['wait_average']:.1f}s → {final_preference['wait_average']:.1f}s "
          f"(Δ{final_preference['wait_average'] - final_baseline['wait_average']:+.1f}s)")
    print(f"Avg Travel Time: {final_baseline['inVehicleTravelTime_mean']:.1f}s → {final_preference['inVehicleTravelTime_mean']:.1f}s "
          f"(Δ{final_preference['inVehicleTravelTime_mean'] - final_baseline['inVehicleTravelTime_mean']:+.1f}s)")
    print(f"Pooling Rate: {final_baseline['poolingRate']:.1%} → {final_preference['poolingRate']:.1%} "
          f"(Δ{(final_preference['poolingRate'] - final_baseline['poolingRate'])*100:+.1f}pp)")
    print(f"Fleet Utilization: {(1-final_baseline['minShareIdleVehicles']):.1%} → {(1-final_preference['minShareIdleVehicles']):.1%} "
          f"(Δ{((1-final_preference['minShareIdleVehicles']) - (1-final_baseline['minShareIdleVehicles']))*100:+.1f}pp)")
    
    # Clean up temp files
    import shutil
    if temp_dir.exists():
        shutil.rmtree(temp_dir)
    
    return True

def main():
    parser = argparse.ArgumentParser(description='Compare two MATSim DRT runs')
    parser.add_argument('baseline_dir', help='Directory containing baseline DRT output')
    parser.add_argument('preference_dir', help='Directory containing preference-aware DRT output')
    parser.add_argument('output_dir', help='Directory to save comparison results')
    args = parser.parse_args()

    if compare_runs(args.baseline_dir, args.preference_dir, args.output_dir):
        print("\nComparison completed successfully")
    else:
        print("\nComparison failed")

if __name__ == "__main__":
    main() 