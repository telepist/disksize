# MVP 1 Implementation Status

This document records what shipped in MVP 1, which items remain open, and what was deferred to later milestones. It replaces the earlier day-by-day checklist that no longer matched the current architecture.

## Goal Recap
Deliver a basic disk space analyzer that scans a directory and renders a static Mosaic TUI summarising totals and top-level subdirectories.

## Acceptance Criteria
- [x] Optional path argument (defaults to current working directory)
- [x] Recursive scan calculates total size, file count, and directory count
- [x] Subdirectories appear largest-first with name, human-readable size, percentage, and bar
- [x] Layout uses box-drawing characters for a clean TUI presentation
- [ ] Press `q` to quit the app <!-- deferred to MVP 2 interactive work -->
- [ ] Surface permission errors to the user <!-- currently skipped silently -->
- [x] Verified on macOS (primary development platform)
- [ ] Test coverage >= 80% <!-- suite exists but target not enforced -->

## Delivered Work
**Domain & Use Case**
- `FileNode`, `ScanResult`, and `ScanError` data classes with unit tests
- `ScanDirectoryUseCase` orchestrates repository calls and aggregates metrics
- `SizeFormatter` converts binary units with exhaustive tests

**Data Layer**
- `FileSystemRepository` interface plus a fake test double
- Shared POSIX implementation (`src/posixMain/...`) reused by all macOS/Linux targets
- Lightweight error handling (skips inaccessible entries; detailed messaging deferred)

**UI & Entry Point**
- `MainScreen` and supporting composables render header, stats, directory list, and status bar
- Percentage bars sized and coloured according to directory weight
- CLI entry point builds dependencies and launches Mosaic

## Deferred or Out of Scope
- Dedicated view-model/state container (current UI remains stateless)
- Sort modes and richer directory metadata
- Percentage-bar utility abstraction and associated tests
- Real filesystem integration tests and automated coverage enforcement
- Enhanced error reporting (status bar warnings, dialogs)

## Next Steps Toward MVP 2
1. Add manual refresh shortcuts and incremental rescan feedback.
2. Provide a warnings panel or command to review skipped entries in detail.
3. Backfill coverage gaps and add integration tests for real filesystem scenarios.
