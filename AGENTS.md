# Repository Guidelines

## Project Structure & Module Organization
- Shared Kotlin code lives in `src/commonMain/kotlin/disksize/`, split into `data`, `domain`, `presentation`, `ui`, and `util` packages; keep new modules under the same package tree.
- POSIX shared code (macOS + Linux entry point and repository) lives in `src/posixMain/kotlin/disksize/`.
- Platform-specific glue sits in `src/<platform>Main/kotlin/` (e.g. `macosArm64Main`, `mingwX64Main`); only add platform-specific code where a shared abstraction is impossible.
- Tests belong in `src/commonTest/kotlin/` alongside the feature they verify; mirror the production package structure.
- Architectural docs, UX flows, and plans reside in `docs/`; update the relevant file when behavior or APIs change.

## Build, Test, and Development Commands
- `make build` compiles the debug binary for the current platform (auto-detected).
- `make run` builds and launches the TUI in a real terminal; skip IDE run buttons because Mosaic requires a TTY.
- `make build-release` produces the optimized release binary.
- `make build-all` builds debug binaries for all five platform targets.
- `make test` runs tests for the current platform.
- `make test-coverage` runs tests with Kover HTML coverage report.
- `make install` / `make uninstall` installs or removes the release binary. Create `Makefile.local` with `INSTALL_DIR = /your/path` to override the default install location.
- Under the hood, Makefile calls `./gradlew linkDebugExecutable<Platform>` / `linkReleaseExecutable<Platform>`.

## Coding Style & Naming Conventions
- Follow standard Kotlin style: four-space indentation, trailing commas where idiomatic, and immutable data first.
- Keep packages under `disksize.<layer>` (for example `disksize.domain.scan`); name use cases `*UseCase`, presenters `*Presenter`, and composables or views `*View`.
- Prefer small public APIs; surface suspend functions for IO paths and inject dependencies rather than using singletons.
- There is no automated formatter yet—use the IDE’s Kotlin reformatter before committing to keep diffs clean.

## Testing Guidelines
- Write tests first (TDD) for new features and bug fixes; add tests when fixing bugs in untested code.
- Use `kotlin.test` assertions from `commonTest`; every new feature should ship with unit coverage or higher-level tests.
- Execute `make test` before pushing.
- For coverage verification, run `make test-coverage` and review `build/reports/kover/html/index.html`.
- Name test classes after the subject (`DiskUsageFormatterTest`) and use descriptive `@Test` method names that read like sentences.

## Commit & Pull Request Guidelines
- Only commit when the user explicitly asks; never commit proactively without being told.
- Write concise, imperative commit summaries (e.g., `Add ScanDirectoryUseCase`, `Fix memory leak in scanner`); keep the subject under 72 characters.
- Keep commit bodies brief—2-4 bullet points maximum explaining what changed and why, not implementation details.
- Avoid verbose explanations; the code diff speaks for itself—focus on the "why" not the "what".
- One feature or fix per commit; include relevant docs or test updates in the same change.
- Pull requests must link the tracking issue, outline verification steps, and attach screenshots or terminal captures for UI/TUI changes.
- Confirm all applicable Make/Gradle commands pass before requesting review and note any follow-up work explicitly.
