# DiskSize - UX Design

## Design Philosophy
- **Keyboard-First**: All operations accessible via keyboard
- **Immediate Feedback**: Show results as they become available
- **Visual Clarity**: Use color and layout to convey information hierarchy
- **Minimal Learning Curve**: Intuitive controls, built-in help

## User Interface Overview

### Layout Structure (MVP 1)
```
╔═════════════════════════════════════════════════════════════╗
║ DiskSize - Disk Space Analyzer                              ║
╠═════════════════════════════════════════════════════════════╣
║ Path: /Users/username/Documents                             ║
╠═════════════════════════════════════════════════════════════╣
║                                                             ║
║  Total Size: 15.2 GB                                        ║
║  Files: 1,234                                               ║
║  Directories: 156                                           ║
║                                                             ║
║  Subdirectories:                                            ║
║  ┌───────────────────────────────────────────────────────┐  ║
║  │ Projects/          8.5 GB  (55.9%) ████████████       │  ║
║  │ Photos/            4.2 GB  (27.6%) ██████             │  ║
║  │ Documents/         1.8 GB  (11.8%) ███                │  ║
║  │ Downloads/         650 MB  ( 4.2%) █                  │  ║
║  │ Music/              70 MB  ( 0.5%)                    │  ║
║  └───────────────────────────────────────────────────────┘  ║
║                                                             ║
╠═════════════════════════════════════════════════════════════╣
║ [Scanning completed in 2.3s]                      q: Quit   ║
╚═════════════════════════════════════════════════════════════╝
```

### Layout Structure (MVP 2 - Interactive)
```
╔═════════════════════════════════════════════════════════════╗
║ DiskSize v0.2.0                              [Sort: Size ▼] ║
╠═════════════════════════════════════════════════════════════╣
║ /Users/username/Documents/Projects                          ║
╠═════════════════════════════════════════════════════════════╣
║                                                             ║
║  📁 my-app/              3.2 GB  (37.6%) █████████      ◄  ║
║  📁 website/             2.1 GB  (24.7%) ██████            ║
║  📁 scripts/             890 MB  (10.2%) ███               ║
║  📁 archived/            1.8 GB  (21.2%) █████             ║
║  📁 experiments/         520 MB  ( 6.1%) ██                ║
║  📄 README.md             12 KB  ( 0.0%)                   ║
║                                                             ║
║                                                             ║
║                                                             ║
╠═════════════════════════════════════════════════════════════╣
║ Total: 8.5 GB | 2,345 files    ↑↓:Nav Enter:Open Bsp:Back   ║
╚═════════════════════════════════════════════════════════════╝
```

## Color Scheme

### Size-Based Color Coding
- **Huge (>1GB)**: Red/Magenta - Attention needed
- **Large (100MB-1GB)**: Yellow - Notable size
- **Medium (10MB-100MB)**: Cyan - Moderate size
- **Small (<10MB)**: White/Grey - Normal

### UI Element Colors
- **Header**: Cyan border and text
- **Selected Item**: Green background or highlight
- **Progress Bar**: Green (determinate)
- **Status Messages**:
  - Success: Green
  - Warning: Yellow
  - Error: Red
- **Help Text**: Grey/Dim

## Typography & Icons

### File Type Icons (MVP 3)
- 📁 Directory
- 📄 Regular file
- 🖼️  Image files
- 🎵 Audio files
- 🎬 Video files
- 📦 Archives
- ⚙️  Executables/binaries
- 📝 Text files

### Size Formatting
- Bytes: < 1 KB → "847 B"
- Kilobytes: < 1 MB → "847 KB"
- Megabytes: < 1 GB → "847 MB"
- Gigabytes: < 1 TB → "847 GB"
- Terabytes: >= 1 TB → "1.2 TB"

Always use 1024-based units (binary), show one decimal place for GB/TB.

## User Interactions

### MVP 1 - Static Display
| Action | Key | Description |
|--------|-----|-------------|
| Quit | `q` | Exit application |

### MVP 2 - Interactive Navigation
| Action | Key | Description |
|--------|-----|-------------|
| Move Up | `↑` or `k` | Select previous item |
| Move Down | `↓` or `j` | Select next item |
| Enter Directory | `Enter` or `→` or `l` | Navigate into selected directory |
| Go Up One Level | `Backspace` or `←` or `h` | Navigate to parent directory |
| Sort Toggle | `s` | Cycle sort options (Size ↓ → Name ↑ → Date ↓) |
| Refresh | `r` | Rescan current directory and subdirectories |
| Delete | `Delete` | Delete selected file or directory |
| Quit | `q` | Exit application |

### MVP 3 - Advanced Features
| Action | Key | Description |
|--------|-----|-------------|
| Search | `/` | Enter search mode |
| Filter | `f` | Show filter options |
| View Mode | `v` | Toggle view mode (tree/list/details) |
| Show Hidden | `.` | Toggle hidden files |
| Help | `?` or `F1` | Show help screen |

## User Flow

### Primary Flow (MVP 1)
1. User launches application with optional directory argument
2. Application scans directory (shows progress)
3. Results displayed with sorted subdirectories
4. User reviews information
5. User quits (`q`)

### Enhanced Flow (MVP 2)
1. User launches application
2. Application scans the requested directory and shows a loading banner
3. Results render once the scan completes
4. User navigates with arrow keys (or `j`/`k`)
5. User presses Enter to drill into a directory (triggering a new scan)
6. User presses Backspace to return to the parent directory
7. Status bar highlights scan duration or any errors
8. User quits with `q` when finished

## Error Handling

### Error Display
```
╔═════════════════════════════════════════════════════════════╗
║ ⚠ Warning                                                   ║
╠═════════════════════════════════════════════════════════════╣
║                                                             ║
║  Could not access:                                          ║
║    /System/Library/PrivateData/                             ║
║                                                             ║
║  Reason: Permission denied                                  ║
║                                                             ║
║  Continuing with remaining directories...                   ║
║                                                             ║
║                                             [Press any key] ║
╚═════════════════════════════════════════════════════════════╝
```

### Error Types
- **Permission Denied**: Skip and continue, log to error list
- **Directory Not Found**: Show error, return to parent
- **Disk Error**: Show error, offer retry
- **Invalid Path**: Show error message, prompt for new path

## Progress Indication

### Scanning Progress
Progress is indeterminate - we show actual statistics as the scan proceeds without estimating completion:
```
Scanning | /Users/username/Documents/Projects

Files: 1,234  Dirs: 42  Size: 2.4 GB
Rate: 125 MB/s
Current: /Users/username/Documents/Projects/node_modules
```

This approach provides honest, real-time feedback without the complexity and inaccuracy of pre-scan estimation or progress bar heuristics.

## Accessibility Considerations
- High contrast color scheme option (future)
- All information available via keyboard
- Screen reader support (future)
- No reliance on color alone for critical information

## Responsive Design
- Minimum terminal size: 80x24
- Adapt layout for larger terminals
- Truncate long paths with ellipsis
- Horizontal scrolling for very long names (future)

## Feedback & Confirmation
- Immediate visual feedback for all actions
- No confirmation dialogs for navigation
- Progress indication for long operations
- Success/failure messages in status bar

## Performance Perception
- Show partial results immediately
- Animate progress indicators
- Keep UI responsive during scanning
- Show "working" indicators for any operation >100ms

## Help System (MVP 3)
```
╔═════════════════════════════════════════════════════════════╗
║ DiskSize - Help                                             ║
╠═════════════════════════════════════════════════════════════╣
║                                                             ║
║  Navigation:                                                ║
║    ↑/↓ or j/k     Move selection up/down                    ║
║    Enter or →/l   Open selected directory                   ║
║    Backspace/←/h  Go to parent directory                    ║
║    g/G            Jump to top/bottom                        ║
║                                                             ║
║  Actions:                                                   ║
║    s              Change sort order                         ║
║    r              Refresh current directory                 ║
║    q/Esc          Quit application                          ║
║    ?              Show this help                            ║
║                                                             ║
║                                             [Press any key] ║
╚═════════════════════════════════════════════════════════════╝
```

## Future UX Enhancements
- Mouse support for terminal emulators that support it
- Custom color themes
- Export current view to text/CSV
- Side-by-side comparison mode
- Heatmap visualization
- Duplicate file detection
