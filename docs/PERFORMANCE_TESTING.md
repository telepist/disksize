# Performance Testing Guide

This guide explains how to use the DiskSize performance testing framework to measure and track scanning performance.

## Overview

The `perf-test.sh` script provides automated performance benchmarking for the DiskSize scanning engine. It:

- Creates realistic test directory structures with configurable depth and size
- Runs the actual DiskSize binary against real filesystem data
- Measures scan performance over multiple runs
- Calculates statistics (average, min, max, standard deviation)
- Supports baseline comparison to track performance changes
- Generates detailed performance reports

## Quick Start

### Run a quick benchmark

```bash
./perf-test.sh -s small -r 3
```

### Save a baseline for comparison

```bash
./perf-test.sh -s medium -r 5 -o perf-results/baseline.txt
```

### Compare against baseline

```bash
# After making code changes
./perf-test.sh -s medium -r 5 -b perf-results/baseline.txt
```

## Usage

```
./perf-test.sh [OPTIONS]

Options:
  -s, --size SIZE       Test size: small, medium, large, deep, wide (default: medium)
  -c, --custom D:W:F    Custom config: depth:dirs_per_level:files_per_dir
  -r, --runs N          Number of runs to average (default: 3)
  -d, --debug           Use debug build instead of release
  -k, --keep            Keep test directory after run
  -o, --output FILE     Save results to file
  -b, --baseline FILE   Compare against baseline results
  -h, --help            Show this help
```

## Test Configurations

### Predefined Sizes

The script includes several predefined test configurations:

| Size   | Depth | Dirs/Level | Files/Dir | Total Files | Use Case                    |
|--------|-------|------------|-----------|-------------|-----------------------------|
| small  | 5     | 3          | 50        | ~18,000     | Quick testing, CI           |
| medium | 7     | 5          | 100       | ~312,000    | Regular benchmarking        |
| large  | 10    | 5          | 200       | ~2,500,000  | Stress testing              |
| deep   | 15    | 3          | 50        | ~100,000    | Deep hierarchy performance  |
| wide   | 5     | 20         | 20        | ~40,000     | Wide hierarchy performance  |

### Custom Configurations

You can specify custom configurations using the `-c` flag with the format `depth:dirs_per_level:files_per_dir`:

```bash
# Create a 8-level deep tree with 10 directories per level and 100 files each
./perf-test.sh -c 8:10:100 -r 3
```

## Examples

### Basic Performance Test

Run a medium-sized benchmark with 5 iterations:

```bash
./perf-test.sh -s medium -r 5
```

Output:
```
╔════════════════════════════════════════════════╗
║            Performance Results                 ║
╠════════════════════════════════════════════════╣
║ Average Time:           145 ms               ║
║ Min Time:               138 ms               ║
║ Max Time:               156 ms               ║
║ Std Deviation:            7 ms               ║
╠════════════════════════════════════════════════╣
║ Files Scanned:       312000                  ║
║ Dirs Scanned:          6240                  ║
║ Total Size:            5.8G                  ║
╠════════════════════════════════════════════════╣
║ Files/sec:          2151724                  ║
║ Dirs/sec:             43034                  ║
╚════════════════════════════════════════════════╝
```

### Establish Performance Baseline

Before making optimization changes, establish a baseline:

```bash
# Run on current code
git checkout main
./perf-test.sh -s medium -r 10 -o perf-results/baseline-v0.2.0.txt
```

### Test Performance Changes

After making changes, compare against the baseline:

```bash
# After implementing optimization
git checkout feature/optimization
./perf-test.sh -s medium -r 10 -b perf-results/baseline-v0.2.0.txt
```

Output includes comparison:
```
Baseline Comparison
═══════════════════════════════════════════════════
Baseline:    145ms
Current:     128ms
Difference:  -17ms
✓ Performance improved by 11.72%
```

### Deep Hierarchy Testing

Test performance with deeply nested directories:

```bash
./perf-test.sh -s deep -r 5
```

### Wide Hierarchy Testing

Test performance with many subdirectories:

```bash
./perf-test.sh -s wide -r 5
```

### Keep Test Directory for Analysis

Sometimes you want to inspect the test structure or run additional manual tests:

```bash
./perf-test.sh -s small -r 3 -k
# Test directory will be kept at /tmp/disksize-perf-test
```

### Custom Test Configuration

For specific testing scenarios:

```bash
# Very deep but narrow tree
./perf-test.sh -c 20:2:30 -r 3

# Shallow but very wide tree
./perf-test.sh -c 3:50:50 -r 3
```

## Performance Workflow

### 1. Pre-Optimization

```bash
# Establish baseline on main branch
git checkout main
./perf-test.sh -s medium -r 10 -o perf-results/before-optimization.txt
```

### 2. During Development

```bash
# Quick checks during development
./perf-test.sh -s small -r 3
```

### 3. Post-Optimization

```bash
# Full comparison after changes
./perf-test.sh -s medium -r 10 -b perf-results/before-optimization.txt
./perf-test.sh -s large -r 5 -b perf-results/before-optimization.txt
```

### 4. Save Results

```bash
# Document the improvement
./perf-test.sh -s medium -r 10 -o perf-results/after-optimization.txt
```

## Interpreting Results

### Scan Time

- **Average Time**: Mean scan duration over all runs
- **Min/Max Time**: Range of observed scan times
- **Std Deviation**: Consistency of measurements (lower is better)

### Throughput

- **Files/sec**: Number of files scanned per second
- **Dirs/sec**: Number of directories scanned per second

Higher throughput indicates better performance.

### Baseline Comparison

When comparing against a baseline:
- **Negative difference**: Performance improved (faster)
- **Positive difference**: Performance degraded (slower)
- **Percentage**: Relative change in performance

### Variability

High standard deviation (>10% of average) may indicate:
- System load affecting measurements
- Filesystem cache effects
- Need for more benchmark runs

Solution: Increase `-r` value for more stable averages.

## Best Practices

### Consistent Testing Environment

1. **Close other applications** to minimize system load
2. **Run multiple iterations** (5-10) for statistical significance
3. **Use same test size** for before/after comparisons
4. **Test on same hardware** for valid comparisons

### Comprehensive Testing

Test multiple scenarios to understand performance characteristics:

```bash
# Test different hierarchy patterns
./perf-test.sh -s small -r 5 -o results/small.txt
./perf-test.sh -s medium -r 5 -o results/medium.txt
./perf-test.sh -s deep -r 5 -o results/deep.txt
./perf-test.sh -s wide -r 5 -o results/wide.txt
```

### Tracking Changes

Keep a performance log:

```bash
# Create dated baseline
./perf-test.sh -s medium -r 10 -o "perf-results/$(date +%Y-%m-%d)-baseline.txt"

# Compare later
./perf-test.sh -s medium -r 10 -b "perf-results/2024-11-14-baseline.txt"
```

### CI Integration

You can use the script in CI to catch performance regressions:

```bash
# In CI pipeline
./perf-test.sh -s small -r 5 -o current.txt
./perf-test.sh -s small -r 5 -b baseline.txt
# Parse output to fail if regression > threshold
```

## Optimization Guidelines

### What to Measure

Focus on:
1. **Scan time** - Primary metric for user experience
2. **Throughput** - Files/second processed
3. **Memory usage** - (requires additional profiling)
4. **Scalability** - Performance across different sizes

### Common Optimizations

Areas to investigate based on results:

- **Deep hierarchy slow?** → Optimize recursion or use iterative approach
- **Wide hierarchy slow?** → Optimize directory listing or use parallel scanning
- **Large files slow?** → Check if unnecessary file I/O is happening
- **High variability?** → Look for filesystem cache dependencies

### Measuring Specific Changes

```bash
# 1. Baseline
git checkout main
./perf-test.sh -s medium -r 10 -o before.txt

# 2. Make changes
git checkout -b feature/parallel-scan
# ... implement changes ...

# 3. Test
./perf-test.sh -s medium -r 10 -b before.txt

# 4. Document
git commit -m "Add parallel scanning - 25% performance improvement"
./perf-test.sh -s medium -r 10 -o after-parallel-scan.txt
```

## Troubleshooting

### Tests Run Too Quickly

If results are under 10ms, they may be unreliable:
- Use larger test size: `-s medium` or `-s large`
- Increase test depth/width with `-c`

### High Variability

If standard deviation is high:
- Increase number of runs: `-r 10` or more
- Close background applications
- Disable spotlight indexing for test directory

### Out of Disk Space

Large configurations can consume significant space:
- Use smaller configurations
- Ensure cleanup is working (test directory deleted after run)
- Manually clean: `rm -rf /tmp/disksize-perf-test`

## Technical Details

### Test Directory Structure

The script creates a directory tree with:
- Files of varying sizes (1KB to 100KB)
- Randomized content
- Realistic directory hierarchy
- Consistent structure per configuration

### Timing Method

- Uses wall-clock time (Python `time.time()`)
- Measures total execution including scan + minimal UI overhead
- Quits immediately after scan completes
- Multiple runs averaged to reduce variance

### Build Configuration

- Default: Debug build (faster to compile, good for development)
- Use `-d` flag for debug (default), or modify script for release builds
- Binary is rebuilt before each test session

## Future Enhancements

Potential improvements to the testing framework:

- [ ] Memory profiling integration
- [ ] CPU profiling integration
- [ ] Parallel test execution
- [ ] Performance regression detection
- [ ] Historical performance tracking
- [ ] Flamegraph generation
- [ ] Comparison across multiple baselines
- [ ] JSON output format for parsing
- [ ] Platform-specific optimizations

## See Also

- [Architecture Documentation](ARCHITECTURE.md)
- [Project Plan](PROJECT_PLAN.md)
- [Testing Strategy](TESTING_STRATEGY.md)
