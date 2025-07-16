#!/bin/bash

# Simple shell script to decompress MATSim population .xml.gz files
# Usage: 
#   ./decompress_population.sh <filename.xml.gz>    # Decompress specific file
#   ./decompress_population.sh --all               # Decompress all .xml.gz files
#   ./decompress_population.sh --list              # List all .xml.gz files

# Change to script directory
cd "$(dirname "$0")"

# Function to decompress a single file
decompress_file() {
    local gz_file="$1"
    local xml_file="${gz_file%.gz}"
    
    if [[ ! -f "$gz_file" ]]; then
        echo "Error: File not found: $gz_file"
        return 1
    fi
    
    if [[ ! "$gz_file" =~ \.xml\.gz$ ]]; then
        echo "Error: File must have .xml.gz extension: $gz_file"
        return 1
    fi
    
    echo "Decompressing: $gz_file"
    echo "Output: $xml_file"
    
    if gunzip -c "$gz_file" > "$xml_file"; then
        local original_size=$(stat -f%z "$gz_file" 2>/dev/null || stat -c%s "$gz_file" 2>/dev/null || echo "unknown")
        local decompressed_size=$(stat -f%z "$xml_file" 2>/dev/null || stat -c%s "$xml_file" 2>/dev/null || echo "unknown")
        
        echo "✓ Success! Decompressed $original_size bytes → $decompressed_size bytes"
        if [[ "$original_size" != "unknown" && "$decompressed_size" != "unknown" ]]; then
            local ratio=$(echo "scale=2; $decompressed_size / $original_size" | bc 2>/dev/null || echo "N/A")
            echo "  Expansion ratio: ${ratio}x"
        fi
    else
        echo "✗ Error decompressing $gz_file"
        rm -f "$xml_file"  # Clean up partial file
        return 1
    fi
}

# Function to list all .xml.gz files
list_gz_files() {
    local gz_files=(*.xml.gz)
    
    if [[ "${gz_files[0]}" == "*.xml.gz" ]]; then
        echo "No .xml.gz files found in current directory."
        return 0
    fi
    
    echo "Found ${#gz_files[@]} .xml.gz files:"
    local i=1
    for file in "${gz_files[@]}"; do
        local size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "unknown")
        printf "  %2d. %s (%s bytes)\n" "$i" "$file" "$size"
        ((i++))
    done
}

# Function to decompress all files
decompress_all() {
    local gz_files=(*.xml.gz)
    
    if [[ "${gz_files[0]}" == "*.xml.gz" ]]; then
        echo "No .xml.gz files found in current directory."
        return 0
    fi
    
    echo "Found ${#gz_files[@]} .xml.gz files to decompress..."
    echo
    
    local success_count=0
    for gz_file in "${gz_files[@]}"; do
        if decompress_file "$gz_file"; then
            ((success_count++))
        fi
        echo
    done
    
    echo "Decompression complete: $success_count/${#gz_files[@]} files successful"
}

# Main script logic
case "${1:-}" in
    --list)
        list_gz_files
        ;;
    --all)
        decompress_all
        ;;
    --help|-h)
        echo "Usage: $0 [OPTIONS] [FILENAME]"
        echo ""
        echo "Decompress MATSim population .xml.gz files to .xml format"
        echo ""
        echo "OPTIONS:"
        echo "  --list     List all .xml.gz files in current directory"
        echo "  --all      Decompress all .xml.gz files in current directory"
        echo "  --help     Show this help message"
        echo ""
        echo "FILENAME:"
        echo "  Specific .xml.gz file to decompress"
        echo ""
        echo "Examples:"
        echo "  $0 base_trip1.0_rule1_population.xml.gz"
        echo "  $0 --all"
        echo "  $0 --list"
        ;;
    "")
        echo "Error: No arguments provided."
        echo "Use --help for usage information."
        exit 1
        ;;
    *.xml.gz)
        if [[ -f "$1" ]]; then
            decompress_file "$1"
        else
            echo "Error: File not found: $1"
            echo ""
            echo "Available .xml.gz files:"
            list_gz_files
            exit 1
        fi
        ;;
    *)
        echo "Error: Invalid argument: $1"
        echo "Use --help for usage information."
        exit 1
        ;;
esac 