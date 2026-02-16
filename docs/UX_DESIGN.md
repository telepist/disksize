# DiskSize - UX Design

## Design Philosophy
- **Keyboard-First**: All operations accessible via keyboard
- **Immediate Feedback**: Show results as they become available
- **Visual Clarity**: Use color and layout to convey information hierarchy
- **Minimal Learning Curve**: Intuitive controls, built-in help

## User Interface Overview

### Screen Layout
The screen is built from these sections, top to bottom:

```
╔═══════════════════════════════════════════════════════════════════════════════════╗  ← Top border
║                          DiskSize - Disk Space Analyzer                           ║  ← Centered title
╠═══════════════════════════════════════════════════════════════════════════════════╣  ← Separator
║Path: /Users/username/Documents                                                    ║  ← Path line
║                                                                                   ║  ← Blank
║Total Size: 15.2 GB                                                                ║  ← Stats section
║Files: 1,234                                                                       ║     (3 lines)
║Directories: 156                                                                   ║
║                                                                                   ║  ← Blank
║Entries (Sort: Size ↓)                                                             ║  ← Entry list header
║> ▾ Projects/                               8.5 GB (55.9%) ▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░║  ← Selected + expanded
║  ├── ▸ my-app/                             3.2 GB (37.6%) ▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░║  ← Tree children
║  ├── ▸ website/                            2.1 GB (24.7%) ▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░║            
║  └── ▸ scripts/                            890 MB (10.5%) ▓▓▓░░░░░░░░░░░░░░░░░░░░░║
║  ▸ Photos/                                 4.2 GB (27.6%) ▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░║  ← Collapsed dirs
║  ▸ Documents/                              1.8 GB (11.8%) ▓▓▓░░░░░░░░░░░░░░░░░░░░░║
║  ▸ Downloads/                              650 MB (4.3%)  ▓░░░░░░░░░░░░░░░░░░░░░░░║
║    Music/                                   70 MB (0.5%)                          ║  ← Dir with no children
║    README.md                                        12 KB                         ║  ← File
║                                                                                   ║  ← Filler
╠═══════════════════════════════════════════════════════════════════════════════════╣  ← Separator
║Scan completed in 2.3s     Enter: Expand  s: Sort  r: Refresh  Del: Delete  q: Quit║  ← Status + key hints
╚═══════════════════════════════════════════════════════════════════════════════════╝  ← Bottom border
```

### Entry Indicators
- `>` marks the currently selected entry (highlighted with green background)
- `▾` expanded directory (Enter to collapse)
- `▸` collapsed directory with children (Enter to expand)
- `⋯` directory not yet scanned
- Spinner (`|`, `/`, `-`, `\`) for directory currently being scanned
- Tree connectors `├──`, `└──`, `│` show hierarchy under expanded directories
- Usage bars: `▓` filled (magenta), `█` filled when selected (green), `░` empty

### Scanning View
While the initial scan is in progress, the entry list area shows live progress:

```
║Entries (Sort: Size ↓)                                        ║
║Scanning / /Users/username/Documents                          ║
║Files: 1,234  Dirs: 42  Size: 2.4 GB                          ║
║Rate: 125 MB/s                                                ║
║Current: /Users/username/Documents/Projects/node_modules      ║
```

### Scroll Indicators
When the entry list is longer than the visible area, scroll indicators appear:

```
║↑ 3 more                                                      ║
║  ...visible entries...                                       ║
║↓ 12 more                                                     ║
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

### File Type Icons (Planned)
Not yet implemented. Currently, directories are indicated with a `/` suffix and symlinks with `@`. Planned icons:
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

### Current - Interactive Navigation
| Action | Key | Description |
|--------|-----|-------------|
| Move Up | `↑` or `k` | Select previous item |
| Move Down | `↓` or `j` | Select next item |
| Page Up | `Page Up` | Move selection up by one page |
| Page Down | `Page Down` | Move selection down by one page |
| Jump to Top | `Home` | Jump to the first entry |
| Jump to Bottom | `End` | Jump to the last entry |
| Toggle Expand | `Enter` | Expand/collapse selected directory |
| Expand/Enter | `→` or `l` | Expand directory or enter if already expanded |
| Collapse/Parent | `←` or `h` | Collapse directory or go to parent |
| Go Up One Level | `Backspace` | Navigate to parent directory |
| Sort Toggle | `s` | Cycle sort options (Size ↓ → Name ↑ → Date ↓) |
| Refresh | `r` | Rescan current directory and subdirectories |
| Delete | `Delete` | Delete selected file or directory |
| Quit | `q` | Exit application |

### Planned - Advanced Features
| Action | Key | Description |
|--------|-----|-------------|
| Search | `/` | Enter search mode |
| Filter | `f` | Show filter options |
| View Mode | `v` | Toggle view mode (tree/list/details) |
| Show Hidden | `.` | Toggle hidden files |
| Help | `?` or `F1` | Show help screen |

## User Flow

### Current Flow
1. User launches `disksize [path]` (defaults to current directory)
2. Application scans the directory, showing live progress (file/dir count, size, throughput)
3. Scan completes; entry list renders sorted by size (largest first)
4. User navigates with arrow keys or `j`/`k`; uses `Page Up`/`Page Down`/`Home`/`End` for fast scrolling
5. User presses `Enter` to expand/collapse a directory in the tree view
6. User presses `→`/`l` to expand or enter a directory (triggering a sub-scan if needed)
7. User presses `←`/`h` to collapse a directory or `Backspace` to navigate to the parent
8. User presses `s` to cycle sort order, `r` to refresh, `Delete` to delete
9. Status bar shows scan duration and warnings (left-aligned) with key hints (right-aligned)
10. User quits with `q`

## Error Handling

### Error Display
Errors are handled gracefully without interrupting the user:
- **Permission denied / IO errors during scan**: Skipped files are counted and shown as warnings in the status bar (e.g., `Scan completed in 2.3s • 5 warning(s)`) and in the entry list footer (`Warnings: 5 item(s) skipped`).
- **Directory not found / scan failure**: Shown inline in the entry list as `Error: <message>` in red.
- **Deletion errors**: The deletion dialog closes and the error is shown in the status bar.

### Error Types
- **Permission Denied**: Skip and continue, count as warning
- **Directory Not Found**: Show error inline in entry list
- **IO Error**: Skip and continue, count as warning
- **Invalid Path**: Show error message in entry list

## Progress Indication

### Scanning Progress
Progress is indeterminate - we show actual statistics as the scan proceeds without estimating completion. The entry list area shows live scan feedback:

```
Entries (Sort: Size ↓)
Scanning / /Users/username/Documents/Projects
Files: 1,234  Dirs: 42  Size: 2.4 GB
Rate: 125 MB/s
Current: /Users/username/Documents/Projects/node_modules
```

The status bar also shows a compact summary with spinner, elapsed time, and throughput:
```
Scanning / (5s) F:1.2K D:42 2.4 GB                                    q: Quit
```

This approach provides honest, real-time feedback without the complexity and inaccuracy of pre-scan estimation or progress bar heuristics.

## Accessibility Considerations
- High contrast color scheme option (future)
- All information available via keyboard
- Screen reader support (future)
- No reliance on color alone for critical information

## Responsive Design
- Minimum effective terminal size: 48 columns x 16 rows (smaller terminals are clamped)
- Adapts layout to fill larger terminals (dynamic row count for entry list)
- Long paths shortened with middle-ellipsis (`/Users/...uments/Projects`)
- Long names truncated with trailing ellipsis (`very-long-directo...`)

## Feedback & Confirmation
- Immediate visual feedback for all actions
- No confirmation dialogs for navigation
- Deletion requires confirmation via a centered dialog (press `y` to confirm, `n`/`Escape` to cancel)
- Deletion in progress shows a spinner dialog with item name and size
- Progress indication for long operations
- Success/failure messages in status bar

## Performance Perception
- Show partial results immediately
- Animate progress indicators
- Keep UI responsive during scanning
- Show "working" indicators for any operation >100ms

## Help System (Planned)
An in-app help screen (triggered by `?` or `F1`) is planned but not yet implemented. Current key hints are shown in the status bar at the bottom of the screen.

## Future UX Enhancements
- Mouse support for terminal emulators that support it
- Custom color themes
- Export current view to text/CSV
- Side-by-side comparison mode
- Heatmap visualization
- Duplicate file detection
