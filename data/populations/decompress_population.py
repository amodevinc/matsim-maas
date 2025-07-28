#!/usr/bin/env python3
"""
Script to decompress MATSim population .xml.gz files to .xml format for viewing.

Usage:
    python decompress_population.py <filename.xml.gz>     # Decompress specific file
    python decompress_population.py --all                # Decompress all .xml.gz files
    python decompress_population.py --list               # List all .xml.gz files

Examples:
    python decompress_population.py base_trip1.0_rule1_population.xml.gz
    python decompress_population.py --all
"""

import gzip
import os
import sys
import argparse
import glob
from pathlib import Path

def decompress_file(gz_filepath, output_filepath=None):
    """
    Decompress a single .xml.gz file to .xml
    
    Args:
        gz_filepath (str): Path to the .xml.gz file
        output_filepath (str, optional): Output path. If None, removes .gz extension
    
    Returns:
        str: Path to the decompressed file
    """
    # Convert to absolute path to handle relative paths correctly
    gz_filepath = os.path.abspath(gz_filepath)
    
    if not os.path.exists(gz_filepath):
        raise FileNotFoundError(f"File not found: {gz_filepath}")
    
    if not gz_filepath.endswith('.xml.gz'):
        raise ValueError(f"File must have .xml.gz extension: {gz_filepath}")
    
    # Determine output filename
    if output_filepath is None:
        output_filepath = gz_filepath[:-3]  # Remove .gz extension
    
    print(f"Decompressing: {gz_filepath}")
    print(f"Output: {output_filepath}")
    
    try:
        with gzip.open(gz_filepath, 'rb') as gz_file:
            with open(output_filepath, 'wb') as output_file:
                # Read and write in chunks to handle large files efficiently
                chunk_size = 1024 * 1024  # 1MB chunks
                while True:
                    chunk = gz_file.read(chunk_size)
                    if not chunk:
                        break
                    output_file.write(chunk)
        
        # Get file sizes for reporting
        original_size = os.path.getsize(gz_filepath)
        decompressed_size = os.path.getsize(output_filepath)
        
        print(f"✓ Success! Decompressed {original_size:,} bytes → {decompressed_size:,} bytes")
        print(f"  Compression ratio: {original_size/decompressed_size:.2f}x")
        
        return output_filepath
        
    except Exception as e:
        print(f"✗ Error decompressing {gz_filepath}: {e}")
        # Clean up partial file if it exists
        if os.path.exists(output_filepath):
            os.remove(output_filepath)
        raise

def list_gz_files(directory=None):
    """List all .xml.gz files in specified directory or current directory"""
    if directory is None:
        directory = os.getcwd()
    
    pattern = os.path.join(directory, "*.xml.gz")
    gz_files = glob.glob(pattern)
    gz_files.sort()
    
    if not gz_files:
        print(f"No .xml.gz files found in directory: {directory}")
        return []
    
    print(f"Found {len(gz_files)} .xml.gz files in {directory}:")
    for i, filepath in enumerate(gz_files, 1):
        filename = os.path.basename(filepath)
        size = os.path.getsize(filepath)
        print(f"  {i:2d}. {filename} ({size:,} bytes)")
    
    return gz_files

def decompress_all(directory=None):
    """Decompress all .xml.gz files in specified directory or current directory"""
    if directory is None:
        directory = os.getcwd()
    
    pattern = os.path.join(directory, "*.xml.gz")
    gz_files = glob.glob(pattern)
    
    if not gz_files:
        print(f"No .xml.gz files found in directory: {directory}")
        return
    
    print(f"Found {len(gz_files)} .xml.gz files to decompress in {directory}...")
    print()
    
    success_count = 0
    for gz_file in sorted(gz_files):
        try:
            decompress_file(gz_file)
            success_count += 1
            print()
        except Exception as e:
            print(f"Failed to decompress {gz_file}: {e}")
            print()
    
    print(f"Decompression complete: {success_count}/{len(gz_files)} files successful")

def main():
    parser = argparse.ArgumentParser(
        description="Decompress MATSim population .xml.gz files to .xml format",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
    %(prog)s base_trip1.0_rule1_population.xml.gz
    %(prog)s --all
    %(prog)s --list
    %(prog)s --all --directory data/populations
        """
    )
    
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('filename', nargs='?', help='Specific .xml.gz file to decompress')
    group.add_argument('--all', action='store_true', help='Decompress all .xml.gz files in specified directory')
    group.add_argument('--list', action='store_true', help='List all .xml.gz files in specified directory')
    
    parser.add_argument('--directory', '-d', help='Directory to search for .xml.gz files (default: current directory, or script directory if no files found)')
    
    args = parser.parse_args()
    
    # Determine working directory
    work_directory = args.directory
    if work_directory is None:
        # If no directory specified, try current directory first
        current_dir = os.getcwd()
        if args.filename:
            # For specific file, just use the file path as-is
            work_directory = None
        else:
            # For --list or --all, check current directory first
            if glob.glob(os.path.join(current_dir, "*.xml.gz")):
                work_directory = current_dir
            else:
                # Fall back to script directory (populations folder)
                script_dir = Path(__file__).parent
                work_directory = str(script_dir)
                print(f"No .xml.gz files in current directory, using: {work_directory}")
    
    try:
        if args.list:
            list_gz_files(work_directory)
        elif args.all:
            decompress_all(work_directory)
        elif args.filename:
            # Handle both absolute and relative paths
            if work_directory and not os.path.isabs(args.filename):
                filename = os.path.join(work_directory, args.filename)
            else:
                filename = args.filename
                
            if not os.path.exists(filename):
                print(f"Error: File not found: {filename}")
                if work_directory:
                    print(f"\nAvailable .xml.gz files in {work_directory}:")
                    list_gz_files(work_directory)
                sys.exit(1)
            decompress_file(filename)
        
    except KeyboardInterrupt:
        print("\nOperation cancelled by user.")
        sys.exit(1)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main() 