# Repository Guidelines

## Project Structure & Module Organization
- Shared Kotlin code lives in `src/commonMain/kotlin/disksize/`, split into `domain`, `ui`, and `presentation` packages; keep new modules under the same package tree.
- Platform adapters sit in `src/<platform>Main/kotlin/`, with entry points in each native target; only add platform-specific code where a shared abstraction is impossible.
- Tests belong in `src/commonTest/kotlin/` alongside the feature they verify; mirror the production package structure.
- Architectural docs, UX flows, and plans reside in `docs/`; update the relevant file when behavior or APIs change.

## Build, Test, and Development Commands
- `make build` (or `./gradlew buildTui`) compiles the macOS ARM64 debug binary to `build/bin/macosArm64/debugExecutable/disksize.kexe`.
- `make run` builds and launches the TUI in a real terminal; skip IDE run buttons because Mosaic requires a TTY.
- `make build-release` or `./gradlew buildTuiRelease` produces the optimized binary in `build/bin/macosArm64/releaseExecutable/`.
- `./gradlew showExePath` prints the exact output locations, handy when scripting packaging steps.

## Coding Style & Naming Conventions
- Follow standard Kotlin style: four-space indentation, trailing commas where idiomatic, and immutable data first.
- Keep packages under `disksize.<layer>` (for example `disksize.domain.scan`); name use cases `*UseCase`, presenters `*Presenter`, and composables or views `*View`.
- Prefer small public APIs; surface suspend functions for IO paths and inject dependencies rather than using singletons.
- There is no automated formatter yet—use the IDE’s Kotlin reformatter before committing to keep diffs clean.

## Testing Guidelines
- Use JUnit 5 with MockK/Truth from `commonTest`; every new feature should ship with unit coverage or higher-level tests.
- Execute `./gradlew test` before pushing; CI enforces the same command.
- For coverage verification, run `./gradlew test koverHtmlReport` and review `build/reports/kover/html/index.html`—keep overall coverage ≥80% and domain modules ≥90%.
- Name test classes after the subject (`DiskUsageFormatterTest`) and use descriptive `@Test` method names that read like sentences.

## Commit & Pull Request Guidelines
- Write imperative, present-tense commit summaries similar to `Add ScanDirectoryUseCase`; keep the subject under 72 characters and include a focused body when needed.
- One feature or fix per commit; include relevant docs or test updates in the same change.
- Pull requests must link the tracking issue, outline verification steps, and attach screenshots or terminal captures for UI/TUI changes.
- Confirm all applicable Make/Gradle commands pass before requesting review and note any follow-up work explicitly.
