#!/bin/bash

# Check if Python and required packages are available
if ! command -v python3 &> /dev/null; then
    echo "Error: Python 3 is required but not found"
    exit 1
fi

# Check if pandas is installed
python3 -c "import pandas" &> /dev/null
if [ $? -ne 0 ]; then
    echo "Installing required Python packages..."
    pip3 install pandas
fi

# Create scripts directory if it doesn't exist
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$SCRIPT_DIR"

# Check arguments
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <input_dir> <output_dir>"
    echo "Example: $0 output/hwaseong_drt_validation_NEW output/kpi_summaries"
    exit 1
fi

INPUT_DIR="$1"
OUTPUT_DIR="$2"

# Validate input directory exists
if [ ! -d "$INPUT_DIR" ]; then
    echo "Error: Input directory '$INPUT_DIR' not found"
    exit 1
fi

# Run the Python script
python3 "$SCRIPT_DIR/aggregate_drt_kpis.py" "$INPUT_DIR" "$OUTPUT_DIR" 