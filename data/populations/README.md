# Population Files Decompression Tools

This directory contains MATSim population files in compressed `.xml.gz` format and tools to decompress them for viewing.

## Files

- **45 population files**: Various scenarios (base, S1-S4) × trip multipliers (0.5x, 1.0x, 1.5x) × rules (1, 2, 3)
- **Decompression scripts**: Two equivalent tools for converting `.xml.gz` to `.xml`

## Decompression Scripts

### Python Script: `decompress_population.py`

**Usage:**
```bash
# List all available .xml.gz files
python decompress_population.py --list

# Decompress a specific file
python decompress_population.py base_trip1.0_rule1_population.xml.gz

# Decompress all .xml.gz files in directory
python decompress_population.py --all

# Show help
python decompress_population.py --help
```

### Shell Script: `decompress_population.sh`

**Usage:**
```bash
# List all available .xml.gz files
./decompress_population.sh --list

# Decompress a specific file
./decompress_population.sh base_trip1.0_rule1_population.xml.gz

# Decompress all .xml.gz files in directory
./decompress_population.sh --all

# Show help
./decompress_population.sh --help
```

## Features

- **Safe decompression**: Original `.xml.gz` files are preserved
- **Progress reporting**: Shows compression ratios and file sizes
- **Error handling**: Validates file existence and extensions
- **Batch processing**: Can decompress all files at once
- **Cross-platform**: Python script works anywhere Python is available

## Example Output

```
Decompressing: base_trip1.0_rule1_population.xml.gz
Output: base_trip1.0_rule1_population.xml
✓ Success! Decompressed 99,072 bytes → 1,098,234 bytes
  Compression ratio: 0.09x
```

## File Sizes

The compressed `.xml.gz` files range from ~66KB to ~180KB, while decompressed `.xml` files are typically ~900KB to ~2MB each. The compression ratio is approximately 10:1.

## Notes

- Decompressed `.xml` files are suitable for viewing in text editors or XML viewers
- For simulation use, MATSim can directly read the compressed `.xml.gz` files
- Both scripts automatically clean up partial files if decompression fails 