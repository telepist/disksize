#!/usr/bin/env bash
# Performance testing script for DiskSize
# Creates test directory structures and measures scan performance

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default configuration
TEST_DIR="${TMPDIR:-/tmp}/disksize-perf-test"
RESULTS_DIR="./perf-results"
BINARY="./build/bin/macosArm64/debugExecutable/disksize.kexe"
BUILD_TYPE="debug"

# Function to get test config by name
get_test_config() {
    case "$1" in
        small)  echo "5:3:50" ;;       # depth:dirs_per_level:files_per_dir
        medium) echo "7:5:100" ;;
        large)  echo "10:5:200" ;;
        deep)   echo "15:3:50" ;;      # stress test for deep hierarchies
        wide)   echo "5:20:20" ;;      # stress test for wide hierarchies
        *)      echo "" ;;
    esac
}

usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -s, --size SIZE       Test size: small, medium, large, deep, wide (default: medium)"
    echo "  -c, --custom D:W:F    Custom config: depth:dirs_per_level:files_per_dir"
    echo "  -r, --runs N          Number of runs to average (default: 3)"
    echo "  -d, --debug           Use debug build instead of release"
    echo "  -k, --keep            Keep test directory after run"
    echo "  -o, --output FILE     Save results to file"
    echo "  -b, --baseline FILE   Compare against baseline results"
    echo "  -h, --help            Show this help"
    echo ""
    echo "Examples:"
    echo "  $0 -s medium -r 5"
    echo "  $0 -c 8:10:100 -r 3"
    echo "  $0 -s large -o baseline.txt"
    echo "  $0 -s large -b baseline.txt"
    exit 1
}

# Parse arguments
SIZE="medium"
CUSTOM_CONFIG=""
RUNS=3
KEEP_DIR=false
OUTPUT_FILE=""
BASELINE_FILE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -s|--size)
            SIZE="$2"
            shift 2
            ;;
        -c|--custom)
            CUSTOM_CONFIG="$2"
            shift 2
            ;;
        -r|--runs)
            RUNS="$2"
            shift 2
            ;;
        -d|--debug)
            BUILD_TYPE="debug"
            BINARY="./build/bin/macosArm64/debugExecutable/disksize.kexe"
            shift
            ;;
        -k|--keep)
            KEEP_DIR=true
            shift
            ;;
        -o|--output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        -b|--baseline)
            BASELINE_FILE="$2"
            shift 2
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo "Unknown option: $1"
            usage
            ;;
    esac
done

log_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1"
}

header() {
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
    echo -e "${CYAN} $1${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
    echo ""
}

# Create test directory structure
create_test_structure() {
    local depth=$1
    local dirs_per_level=$2
    local files_per_dir=$3
    local current_path=$4
    local current_depth=$5

    # Create files in current directory
    for i in $(seq 1 $files_per_dir); do
        # Create files of varying sizes (1KB to 100KB)
        local size=$((1024 + RANDOM % 102400))
        dd if=/dev/zero of="$current_path/file_$i.dat" bs=$size count=1 2>/dev/null
    done

    # Recurse into subdirectories
    if [ $current_depth -lt $depth ]; then
        for i in $(seq 1 $dirs_per_level); do
            local subdir="$current_path/dir_${current_depth}_$i"
            mkdir -p "$subdir"
            create_test_structure $depth $dirs_per_level $files_per_dir "$subdir" $((current_depth + 1))
        done
    fi
}

# Build the binary
build_binary() {
    header "Building DiskSize ($BUILD_TYPE)"

    log_info "Building binary..."
    ./gradlew linkMacosArm64 -q

    if [ ! -f "$BINARY" ]; then
        log_error "Binary not found at $BINARY"
        exit 1
    fi

    log_success "Binary built successfully"
}

# Setup test directory
setup_test_directory() {
    local config=$1

    header "Setting Up Test Directory"

    # Parse configuration
    IFS=':' read -r depth dirs files <<< "$config"

    log_info "Configuration:"
    echo "  - Depth: $depth levels"
    echo "  - Directories per level: $dirs"
    echo "  - Files per directory: $files"
    echo "  - Test path: $TEST_DIR"

    # Clean up any existing test directory
    if [ -d "$TEST_DIR" ]; then
        log_warning "Removing existing test directory..."
        rm -rf "$TEST_DIR"
    fi

    # Create fresh test directory
    mkdir -p "$TEST_DIR"

    log_info "Creating test structure (this may take a moment)..."
    local start_time=$(date +%s)
    create_test_structure $depth $dirs $files "$TEST_DIR" 0
    local end_time=$(date +%s)
    local setup_duration=$((end_time - start_time))

    # Calculate statistics
    local total_files=$(find "$TEST_DIR" -type f | wc -l | tr -d ' ')
    local total_dirs=$(find "$TEST_DIR" -type d | wc -l | tr -d ' ')
    local total_size=$(du -sh "$TEST_DIR" | cut -f1)

    log_success "Test structure created in ${setup_duration}s"
    echo "  - Total files: $total_files"
    echo "  - Total directories: $total_dirs"
    echo "  - Total size: $total_size"

    # Export for use in benchmarks
    export TOTAL_FILES=$total_files
    export TOTAL_DIRS=$total_dirs
    export TOTAL_SIZE_HUMAN=$total_size
}

# Run single benchmark
run_single_benchmark() {
    local run_number=$1

    # Use time command to measure execution time
    # We'll capture real (wall clock) time and extract just the scan part
    local start_ms=$(python3 -c 'import time; print(int(time.time() * 1000))')

    # Run disksize and quit immediately
    # The q command will quit after scan completes
    echo "q" | timeout 30s "$BINARY" "$TEST_DIR" >/dev/null 2>&1 || true

    local end_ms=$(python3 -c 'import time; print(int(time.time() * 1000))')
    local elapsed_ms=$((end_ms - start_ms))

    echo "$elapsed_ms"
}

# Run benchmark suite
run_benchmarks() {
    header "Running Benchmarks"

    log_info "Running $RUNS benchmark iterations..."

    local times=()
    local total=0

    for i in $(seq 1 $RUNS); do
        echo -n "  Run $i/$RUNS: "
        local time=$(run_single_benchmark $i)
        times+=($time)
        total=$((total + time))
        echo "${time}ms"
    done

    # Calculate statistics
    local avg=$((total / RUNS))

    # Calculate min and max
    local min=${times[0]}
    local max=${times[0]}
    for time in "${times[@]}"; do
        [ $time -lt $min ] && min=$time
        [ $time -gt $max ] && max=$time
    done

    # Calculate standard deviation (simplified)
    local variance=0
    for time in "${times[@]}"; do
        local diff=$((time - avg))
        variance=$((variance + diff * diff))
    done
    variance=$((variance / RUNS))
    local stddev=$(echo "sqrt($variance)" | bc)

    # Calculate throughput
    local files_per_sec=$((TOTAL_FILES * 1000 / avg))
    local dirs_per_sec=$((TOTAL_DIRS * 1000 / avg))

    echo ""
    log_success "Benchmark Complete"
    echo ""
    echo "╔════════════════════════════════════════════════╗"
    echo "║            Performance Results                 ║"
    echo "╠════════════════════════════════════════════════╣"
    printf "║ Average Time:    %10s ms               ║\n" "$avg"
    printf "║ Min Time:        %10s ms               ║\n" "$min"
    printf "║ Max Time:        %10s ms               ║\n" "$max"
    printf "║ Std Deviation:   %10s ms               ║\n" "$stddev"
    echo "╠════════════════════════════════════════════════╣"
    printf "║ Files Scanned:   %10s                  ║\n" "$TOTAL_FILES"
    printf "║ Dirs Scanned:    %10s                  ║\n" "$TOTAL_DIRS"
    printf "║ Total Size:      %10s                  ║\n" "$TOTAL_SIZE_HUMAN"
    echo "╠════════════════════════════════════════════════╣"
    printf "║ Files/sec:       %10s                  ║\n" "$files_per_sec"
    printf "║ Dirs/sec:        %10s                  ║\n" "$dirs_per_sec"
    echo "╚════════════════════════════════════════════════╝"

    # Export results
    export PERF_AVG=$avg
    export PERF_MIN=$min
    export PERF_MAX=$max
    export PERF_STDDEV=$stddev
    export PERF_FILES_SEC=$files_per_sec
}

# Save results to file
save_results() {
    local output_file=$1

    mkdir -p "$(dirname "$output_file")"

    cat > "$output_file" <<EOF
DiskSize Performance Test Results
Generated: $(date)
Build Type: $BUILD_TYPE
Configuration: $SIZE

Test Structure:
  Files: $TOTAL_FILES
  Directories: $TOTAL_DIRS
  Total Size: $TOTAL_SIZE_HUMAN

Performance (averaged over $RUNS runs):
  Average: ${PERF_AVG}ms
  Min: ${PERF_MIN}ms
  Max: ${PERF_MAX}ms
  Std Dev: ${PERF_STDDEV}ms
  Files/sec: $PERF_FILES_SEC
  Dirs/sec: $dirs_per_sec
EOF

    log_success "Results saved to $output_file"
}

# Compare with baseline
compare_baseline() {
    local baseline_file=$1

    header "Baseline Comparison"

    if [ ! -f "$baseline_file" ]; then
        log_error "Baseline file not found: $baseline_file"
        return
    fi

    # Extract baseline average time
    local baseline_avg=$(grep "Average:" "$baseline_file" | sed 's/.*: \([0-9]*\)ms/\1/')

    if [ -z "$baseline_avg" ]; then
        log_error "Could not parse baseline file"
        return
    fi

    local current_avg=$PERF_AVG
    local diff=$((current_avg - baseline_avg))
    local percent=$(echo "scale=2; ($diff * 100) / $baseline_avg" | bc)

    echo "Baseline:    ${baseline_avg}ms"
    echo "Current:     ${current_avg}ms"
    echo "Difference:  ${diff}ms"

    if [ $diff -lt 0 ]; then
        log_success "Performance improved by ${percent#-}%"
    elif [ $diff -gt 0 ]; then
        log_warning "Performance degraded by ${percent}%"
    else
        log_info "Performance unchanged"
    fi
}

# Cleanup
cleanup() {
    if [ "$KEEP_DIR" = false ] && [ -d "$TEST_DIR" ]; then
        header "Cleanup"
        log_info "Removing test directory..."
        rm -rf "$TEST_DIR"
        log_success "Cleanup complete"
    else
        log_info "Test directory kept at: $TEST_DIR"
    fi
}

# Main execution
main() {
    header "DiskSize Performance Test"

    echo "Build Type: $BUILD_TYPE"
    echo "Test Size:  $SIZE"
    echo "Runs:       $RUNS"
    echo ""

    # Determine configuration
    local config
    if [ -n "$CUSTOM_CONFIG" ]; then
        config="$CUSTOM_CONFIG"
    else
        config=$(get_test_config "$SIZE")
        if [ -z "$config" ]; then
            log_error "Unknown size: $SIZE"
            log_info "Available sizes: small, medium, large, deep, wide"
            exit 1
        fi
    fi

    # Run the test suite
    build_binary
    setup_test_directory "$config"
    run_benchmarks

    # Save results if requested
    if [ -n "$OUTPUT_FILE" ]; then
        save_results "$OUTPUT_FILE"
    fi

    # Compare with baseline if requested
    if [ -n "$BASELINE_FILE" ]; then
        compare_baseline "$BASELINE_FILE"
    fi

    cleanup

    echo ""
    log_success "All tests complete!"
}

# Trap cleanup on exit
trap cleanup EXIT

# Run main
main
