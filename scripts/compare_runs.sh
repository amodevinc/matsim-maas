#!/bin/bash

# Check if Python and required packages are available
if ! command -v python3 &> /dev/null; then
    echo "Error: Python 3 is required but not found"
    exit 1
fi

# Check if required packages are installed
python3 -c "import pandas, numpy, scipy" &> /dev/null
if [ $? -ne 0 ]; then
    echo "Installing required Python packages..."
    pip3 install pandas numpy scipy
fi

# Create scripts directory if it doesn't exist
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$SCRIPT_DIR"

# Check arguments
if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <baseline_dir> <preference_dir> <output_dir>"
    echo "Example: $0 output/baseline_run output/preference_run output/comparisons"
    echo ""
    echo "This script compares two MATSim DRT simulation outputs:"
    echo "  baseline_dir   - Directory containing baseline DRT output files"
    echo "  preference_dir - Directory containing preference-aware DRT output files"
    echo "  output_dir     - Directory where comparison results will be saved"
    exit 1
fi

BASELINE_DIR="$1"
PREFERENCE_DIR="$2"
OUTPUT_DIR="$3"

# Validate input directories exist
if [ ! -d "$BASELINE_DIR" ]; then
    echo "Error: Baseline directory '$BASELINE_DIR' not found"
    exit 1
fi

if [ ! -d "$PREFERENCE_DIR" ]; then
    echo "Error: Preference directory '$PREFERENCE_DIR' not found"
    exit 1
fi

echo "Comparing DRT runs:"
echo "  Baseline:    $BASELINE_DIR"
echo "  Preference:  $PREFERENCE_DIR"
echo "  Output:      $OUTPUT_DIR"
echo ""

# Run the Python script
python3 "$SCRIPT_DIR/compare_drt_runs.py" "$BASELINE_DIR" "$PREFERENCE_DIR" "$OUTPUT_DIR" 