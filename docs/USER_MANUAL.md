# DiskSize - User Manual

## Introduction
DiskSize is a cross-platform terminal user interface (TUI) application for analyzing disk space usage. It helps you quickly identify which directories and files are consuming the most space on your system.

## Installation

### Prerequisites
- macOS, Linux, or Windows
- Terminal emulator with ANSI color support

### Installing from Release
```bash
# macOS (Intel)
curl -L https://github.com/username/disksize/releases/latest/download/disksize-macos-x64 -o disksize
chmod +x disksize
sudo mv disksize /usr/local/bin/

# macOS (Apple Silicon)
curl -L https://github.com/username/disksize/releases/latest/download/disksize-macos-arm64 -o disksize
chmod +x disksize
sudo mv disksize /usr/local/bin/

# Linux (x64)
curl -L https://github.com/username/disksize/releases/latest/download/disksize-linux-x64 -o disksize
chmod +x disksize
sudo mv disksize /usr/local/bin/

# Windows (download from releases page and add to PATH)
```

### Building from Source
```bash
git clone https://github.com/username/disksize.git
cd disksize
./gradlew buildTuiRelease
# Binary will be in build/bin/<platform>/releaseExecutable/
```

## Getting Started

### Basic Usage
Analyze the current directory:
```bash
disksize
```

Analyze a specific directory:
```bash
disksize /path/to/directory
```

Analyze home directory:
```bash
disksize ~
```

### Command-Line Options
```
Usage: disksize [OPTIONS] [PATH]

Arguments:
  [PATH]    Directory path to analyze (default: current directory)

Options:
  -h, --help       Show this help message
  -v, --version    Show version information
  --hidden         Include hidden files and directories
  --follow-links   Follow symbolic links (default: skip)
  --max-depth N    Limit scan depth to N levels
  --min-size SIZE  Only show items larger than SIZE (e.g., 1MB, 100KB)
```

## User Interface

### Screen Layout
```
╔═════════════════════════════════════════════════════════════╗
║ [1] Header       - Application title and current settings  ║
╠═════════════════════════════════════════════════════════════╣
║ [2] Path Bar     - Current directory path                  ║
╠═════════════════════════════════════════════════════════════╣
║ [3] Content Area - List of directories and files           ║
║                    with sizes and percentages              ║
║                                                             ║
╠═════════════════════════════════════════════════════════════╣
║ [4] Status Bar   - Statistics, help hints, messages        ║
╚═════════════════════════════════════════════════════════════╝
```

### Color Coding
DiskSize uses colors to help you quickly identify large items:

- **Red/Magenta**: Very large items (>1 GB) - Attention needed
- **Yellow**: Large items (100 MB - 1 GB)
- **Cyan**: Medium items (10 MB - 100 MB)
- **White/Grey**: Small items (<10 MB)
- **Green**: Selected item or success messages

## Keyboard Controls

### Navigation (MVP 1)
| Key | Action |
|-----|--------|
| `q` | Quit application |

### Interactive Navigation (MVP 2)
| Key | Action |
|-----|--------|
| `↑` or `k` | Move selection up |
| `↓` or `j` | Move selection down |
| `Enter` or `→` or `l` | Enter selected directory |
| `Backspace` or `←` or `h` | Go up to parent directory |
| `Home` or `g` | Jump to first item |
| `End` or `G` | Jump to last item |
| `s` | Change sort order |
| `r` or `F5` | Refresh/re-scan current directory |
| `q` or `Esc` | Quit application |

### Advanced Controls (MVP 3)
| Key | Action |
|-----|--------|
| `/` | Search for files/directories |
| `f` | Filter by file type |
| `v` | Change view mode (tree/list/details) |
| `.` | Toggle hidden files visibility |
| `?` or `F1` | Show help screen |

## Understanding the Display

### Size Information
Sizes are displayed in human-readable format:
- **B**: Bytes (< 1 KB)
- **KB**: Kilobytes (< 1 MB)
- **MB**: Megabytes (< 1 GB)
- **GB**: Gigabytes (< 1 TB)
- **TB**: Terabytes

DiskSize uses binary units (1 KB = 1024 bytes) for accuracy.

### Percentage Bars
Each item shows a percentage bar indicating its size relative to the current directory:
```
Projects/    8.5 GB  (55.9%) ████████████
Photos/      4.2 GB  (27.6%) ██████
Documents/   1.8 GB  (11.8%) ███
```

### File Type Icons (MVP 3)
- 📁 Directory
- 📄 Regular file
- 🖼️  Image file (jpg, png, gif, etc.)
- 🎵 Audio file (mp3, wav, flac, etc.)
- 🎬 Video file (mp4, avi, mkv, etc.)
- 📦 Archive (zip, tar, gz, etc.)
- ⚙️  Executable
- 📝 Text/Document

## Common Tasks

### Finding Large Directories
1. Launch DiskSize in the directory you want to analyze
2. Wait for the scan to complete
3. Directories are sorted by size by default (largest first)
4. Use `Enter` to navigate into large directories
5. Use `Backspace` to go back up

### Analyzing Specific Paths
```bash
# Analyze your home directory
disksize ~

# Analyze a project directory
disksize ~/Projects/my-app

# Analyze system directories (may require sudo)
sudo disksize /var/log
```

### Sorting Options
Press `s` to cycle through sort options:
- **Size (Descending)**: Largest items first (default)
- **Size (Ascending)**: Smallest items first
- **Name (A-Z)**: Alphabetical order
- **Name (Z-A)**: Reverse alphabetical
- **Date (Newest)**: Recently modified first
- **Date (Oldest)**: Oldest modified first

### Filtering by Size
Only show items larger than a certain size:
```bash
# Only show items larger than 100 MB
disksize --min-size 100MB /path/to/scan

# Only show items larger than 1 GB
disksize --min-size 1GB ~
```

## Tips and Tricks

### Quick Wins
1. **Start at /Users or C:\Users**: Home directories often accumulate large files
2. **Check Downloads**: Often contains forgotten large files
3. **Look for node_modules**: JavaScript projects can accumulate GBs
4. **Check caches**: ~/.cache, ~/Library/Caches often grow large

### Performance Tips
1. **Exclude network drives**: Scanning network drives can be slow
2. **Don't follow symlinks**: Use default behavior to avoid loops
3. **Use --max-depth**: Limit depth for faster scans of deep hierarchies
4. **Let it complete**: Initial scan collects data, subsequent navigation is instant

### Working with Permission Errors
Some directories require elevated privileges:
```bash
# On macOS/Linux
sudo disksize /var
sudo disksize /Library

# DiskSize will skip inaccessible directories and continue scanning
```

## Troubleshooting

### "Permission denied" errors
**Problem**: Cannot access certain directories

**Solution**:
- Run with sudo/administrator privileges if needed
- DiskSize will automatically skip inaccessible directories

### Scan is very slow
**Problem**: Scanning takes a long time

**Possible causes**:
- Very large directory structure (millions of files)
- Network-mounted drives
- External drives (USB, Thunderbolt)

**Solutions**:
- Use `--max-depth` to limit scan depth
- Scan specific subdirectories instead of root
- Ensure you're scanning local drives

### Terminal displays garbled characters
**Problem**: UI doesn't render correctly

**Solution**:
- Ensure your terminal supports ANSI colors and UTF-8
- Try a different terminal emulator (iTerm2, Windows Terminal, etc.)
- Check terminal size (minimum 80x24 required)

### Size calculations seem wrong
**Problem**: Sizes don't match other tools

**Note**:
- DiskSize shows actual file sizes (apparent size)
- Disk usage tools like `du` show allocated blocks
- Sparse files may show differently
- Hard links are counted multiple times

## FAQ

**Q: How accurate are the size calculations?**
A: DiskSize reports the actual file sizes (apparent size). This matches file managers but may differ from disk usage tools that report allocated blocks.

**Q: Can I delete files from within DiskSize?**
A: Not in MVP 1-3. This is a viewing/analyzing tool. Use your file manager or terminal to delete files.

**Q: Does it work on Windows?**
A: Yes, DiskSize is cross-platform and works on Windows, macOS, and Linux.

**Q: Can I scan network drives?**
A: Yes, but it may be slow depending on network speed.

**Q: Does it follow symbolic links?**
A: By default, no. Use `--follow-links` flag to follow symlinks (use with caution to avoid loops).

**Q: Is my data sent anywhere?**
A: No. DiskSize runs entirely locally on your machine. No data is transmitted anywhere.

## Getting Help

- **In-app help**: Press `?` or `F1` while running DiskSize
- **Command-line help**: `disksize --help`
- **Report issues**: https://github.com/username/disksize/issues
- **Documentation**: https://github.com/username/disksize/wiki

## Version History

### v0.1.0 (MVP 1) - Current
- Initial release
- Basic directory scanning
- Static display of subdirectories sorted by size
- macOS support

### Planned Releases
- v0.2.0 (MVP 2): Interactive navigation
- v0.3.0 (MVP 3): Tree visualization, filtering
- v0.4.0 (MVP 4): Advanced features, export
- v1.0.0 (MVP 5): Production release, all platforms
