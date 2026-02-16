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
make build-release
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

### Command-Line Usage
```
Usage: disksize [PATH]

Arguments:
  [PATH]    Directory path to analyze (default: current directory)
```

Note: Command-line flags (--help, --hidden, --max-depth, etc.) are not yet implemented. See the TODO section for planned features.

## User Interface

### Screen Layout
```
╔═══════════════════════════════════════════════════════════════╗
║              DiskSize - Disk Space Analyzer                   ║ ← Title
╠═══════════════════════════════════════════════════════════════╣
║Path: /Users/username/Documents                                ║ ← Current path
║                                                               ║
║Total Size: 15.2 GB                                            ║ ← Stats
║Files: 1,234                                                   ║
║Directories: 156                                               ║
║                                                               ║
║Entries (Sort: Size ↓)                                         ║ ← Entry list
║> ▾ Projects/       8.5 GB (55.9%) ▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░ ║
║  ├── ▸ my-app/     3.2 GB (37.6%) ▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░ ║
║  ...                                                          ║
║                                                               ║
╠═══════════════════════════════════════════════════════════════╣
║Scan completed in 2.3s  Enter: Expand  s: Sort  r: Refresh     ║ ← Status bar
╚═══════════════════════════════════════════════════════════════╝
```

- `>` marks the selected entry (green background highlight)
- `▾` / `▸` indicate expanded / collapsed directories
- Tree connectors (`├──`, `└──`) show hierarchy under expanded directories
- Usage bars show relative size with `▓` (filled) and `░` (empty)

### Color Coding
DiskSize uses colors to help you quickly identify large items:

- **Red/Magenta**: Very large items (>1 GB) - Attention needed
- **Yellow**: Large items (100 MB - 1 GB)
- **Cyan**: Medium items (10 MB - 100 MB)
- **White/Grey**: Small items (<10 MB)
- **Green**: Selected item or success messages

## Keyboard Controls

### Navigation
| Key                       | Action                                      |
|---------------------------|---------------------------------------------|
| `↑` or `k`                | Move selection up                           |
| `↓` or `j`                | Move selection down                         |
| `Page Up`                 | Move selection up by one page               |
| `Page Down`               | Move selection down by one page             |
| `Home`                    | Jump to the first entry                     |
| `End`                     | Jump to the last entry                      |
| `Enter`                   | Toggle expand/collapse for directories      |
| `→` or `l`                | Expand directory or enter if already expanded |
| `←` or `h`                | Collapse directory or go to parent          |
| `Backspace`               | Navigate to the parent directory            |
| `s`                       | Cycle sort order (Size ↓ → Name ↑ → Date ↓) |
| `r`                       | Refresh (rescan) the current directory      |
| `Delete`                  | Delete the selected file or directory       |
| `q`                       | Quit the application                        |

### Planned Controls (Not Yet Implemented)
| Key         | Action                               |
|-------------|--------------------------------------|
| `/`         | Search for files/directories         |
| `f`         | Filter by file type                  |
| `v`         | Change view mode                     |
| `.`         | Toggle hidden files visibility       |
| `?` or `F1` | Show help screen                     |

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
Each directory shows a usage bar indicating its size relative to sibling directories:
```
▾ Projects/       8.5 GB (55.9%) ▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░
▸ Photos/         4.2 GB (27.6%) ▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░
▸ Documents/      1.8 GB (11.8%) ▓▓▓░░░░░░░░░░░░░░░░░░░░░
```
Bars use `▓` for the filled portion (magenta) and `░` for empty. When a directory is selected, the bar turns green with `█` characters.

### File Type Icons (Planned)
File type icons are not yet implemented. Currently, directories are shown with a `/` suffix and files are shown with their full name.

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
- **Size ↓ (Descending)**: Largest items first (default)
- **Name ↑ (Ascending)**: Alphabetical order
- **Date ↓ (Descending)**: Recently modified first

### Filtering by Size
Size filtering via command-line flags is not yet implemented. Items are displayed in the selected sort order, with the largest items shown first by default.

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
A: Yes! Press the `Delete` key on any selected file or directory. You'll be prompted to confirm before deletion (press `y` to confirm or `n`/`Escape` to cancel).

**Q: Does it work on Windows?**
A: Yes, DiskSize is cross-platform and works on Windows, macOS, and Linux.

**Q: Can I scan network drives?**
A: Yes, but it may be slow depending on network speed.

**Q: Does it follow symbolic links?**
A: By default, no. Use `--follow-links` flag to follow symlinks (use with caution to avoid loops).

**Q: Is my data sent anywhere?**
A: No. DiskSize runs entirely locally on your machine. No data is transmitted anywhere.

## Getting Help

- **Report issues**: https://github.com/username/disksize/issues
- **Documentation**: https://github.com/username/disksize/wiki

Note: In-app help (`?` / `F1`) and `--help` are planned but not yet implemented.

## Version History

### Current
- Interactive keyboard navigation with vim-style shortcuts
- Page Up/Down, Home/End navigation
- Tree view with expandable/collapsible directory nodes
- Directory hierarchy exploration
- Multiple sort modes (size, name, date)
- File and directory display
- Refresh functionality
- File deletion with confirmation
- Intelligent caching for snappy navigation
- Streaming progress updates with throughput display
- Proper symlink handling
- Color-coded size indicators with percentage bars
- Green background highlight for selected entry
- Enhanced status bar with contextual hints
- Cross-platform: macOS, Linux, Windows
- Cross-platform install/uninstall via Make

### Planned
- File type detection and icons
- Search and filter
- Command-line options (--help, --hidden, --max-depth, etc.)
- Export results (CSV, JSON)
- In-app help system
