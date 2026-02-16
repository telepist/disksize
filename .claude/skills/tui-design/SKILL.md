---
name: tui-design
description: Design beautiful, modern terminal user interfaces (TUI). Use this skill when the user asks to build, redesign, or improve TUI components, screens, layouts, or visual elements. Generates distinctive, polished terminal interfaces that feel crafted and intentional.
---

This skill guides creation of distinctive, production-grade terminal user interfaces that feel handcrafted rather than thrown together. Every pixel-cell on screen should earn its place. Implement real working code with exceptional attention to terminal aesthetics and spatial composition.

The user provides TUI requirements: a component, screen, layout, or visual element to build or improve. They may include context about the tool's purpose, audience, or technical constraints.

## Design Thinking

Before coding, understand the context and commit to a clear aesthetic direction:
- **Purpose**: What task does this tool serve? Who uses it and in what environment? A sysadmin SSH'd into a server has different needs than a developer in a local terminal.
- **Tone**: Pick a direction: clean and surgical, warm and inviting, dense and information-rich, spacious and calm, retro terminal nostalgia, futuristic dashboard, utilitarian and honest, elegant and refined. The best TUIs have a point of view.
- **Constraints**: Terminal capabilities (color depth, Unicode support, minimum dimensions), performance requirements, accessibility (colorblind-safe palettes, screen reader compatibility).
- **Differentiation**: What makes this TUI memorable? What will someone notice the first time they use it?

**CRITICAL**: A great TUI doesn't try to be a GUI. It embraces the grid. It uses the constraints of the terminal as creative fuel. The best terminal interfaces feel inevitable — every character placed with purpose. As brandur.org puts it: "A successful interface isn't one that looks good in a screenshot, it's one that maximizes productivity and lets us keep moving."

The terminal's greatest strengths: negligible startup time, instantaneous transitions, uniform elements, and composability. Never sacrifice these for decoration.

## Terminal Aesthetics Guidelines

### Box Drawing & Borders
Use Unicode box drawing characters with intention (128 characters in U+2500 block):
- **Light borders** (`─│┌┐└┘├┤┬┴┼`) for subtle structure — let content breathe
- **Heavy/double borders** (`═║╔╗╚╝╠╣╦╩╬`) for emphasis and primary frames — use sparingly
- **Rounded corners** (`╭╮╰╯`) for a softer, friendlier feel
- **Mixed weight** borders create visual hierarchy — heavy for outer frame, light for inner divisions
- **No borders** is also a valid choice — whitespace and alignment can define structure without drawing a single line
- **Block borders** (`█`) for solid, high-contrast framing
- **Half-block borders** (`▀▄▌▐`) for thinner borders that occupy half a cell

Avoid border overload. If everything has a box around it, nothing stands out. Reserve borders for the outermost frame or key structural divisions.

### Color Philosophy

**Three tiers of terminal color support:**
1. **16 Named ANSI Colors** (universal): black, red, green, yellow, blue, magenta, cyan, white + bright variants
2. **256-Color Palette** (indexed): 16 ANSI + 216 color cube + 24 grayscale ramp
3. **True Color / 24-bit RGB** (modern terminals — notably absent from macOS Terminal.app)

**Critical reality**: Users customize their terminal's ANSI color definitions. Your app's appearance depends on their theme. Design for graceful adaptation, not pixel-perfect control.

Terminal color is precious. Spend it wisely:
- **Establish a dominant color** that defines the tool's identity (cyan for monitoring, green for success-oriented tools, amber for retro feel)
- **Use accent colors surgically** — a single red value in a sea of cool tones draws the eye exactly where it needs to go
- **Dim/muted tones** (`Color(100, 100, 100)`) for secondary information, disabled states, decorative elements
- **Bright/bold** only for actionable or critical elements — if everything is bright, nothing is
- **Background colors** are powerful but dangerous — use sparingly for selection highlighting or modal overlays
- **Semantic consistency**: once a color means something, it always means that thing. Red = danger/large. Green = selected/success. Yellow = warning/attention. Don't break this contract.

**Size-based color coding** is a powerful pattern for data-heavy TUIs:
```
Critical threshold  → Red (demands attention)
Warning threshold   → Yellow (worth noticing)
Normal range        → Primary color (expected)
Below threshold     → Default/white (unremarkable)
```

**Layered background depth** (inspired by Catppuccin's design system):
```
Base        → Primary application background
Mantle      → Secondary background (sidebars, panels)
Surface 0-2 → Progressive elevation (cards, inputs, active areas)
Overlay 0-2 → Temporary elements (tooltips, dialogs, popups)
```
Each layer slightly lighter/darker than the previous, creating perceived depth without borders.

**Text hierarchy through color:**
```
Text        → Primary content (full brightness)
Subtext     → Secondary information (reduced brightness)
Overlay     → Timestamps, hints, labels (further reduced)
Dim         → Decorative, disabled, structural (lowest)
```

Never use color as the sole indicator — pair with symbols, position, or text for accessibility.

**Palette generation tip**: Use the Okhsl color model for perceptually uniform palettes. Unlike HSL where yellow appears much brighter than blue at the same lightness, Okhsl produces consistent perceived brightness across hues.

### Typography & Characters
The terminal is a character grid. Every glyph is a design choice:
- **Block elements** (`█▓▒░`) for progress bars, histograms, heatmaps — they create density and visual weight
- **Eighth blocks** (`▏▎▍▌▋▊▉█`) for progress bars with sub-character precision — 8 levels per cell
- **Half blocks** (`▀▄`) for double vertical resolution — combine with foreground/background colors to render two independently-colored "pixels" per cell
- **Arrows & pointers** (`▸▾▹▿►▼→←↑↓`) for navigation, expansion, direction
- **Geometric shapes** (`●○◆◇■□▪▫`) for bullets, status indicators, toggles
- **Mathematical symbols** (`±≈≠≤≥∞Σ`) when showing calculations or thresholds
- **Braille patterns** (`⠀⠁⠂⠃...⣿`, U+2800-U+28FF) for ultra-dense sparklines and micro-charts — each character is a 2x4 dot grid giving 2x horizontal and 4x vertical sub-character resolution
- **Line-drawing connectors** (`├──└──│`) for tree structures — they tell a story of hierarchy
- **Separators** (`·` middle dot, `•` bullet, `│` thin pipe) between inline items

**Selector characters matter**: `>` is utilitarian, `▸` is polished, `→` is directional, `●` is status-oriented. Choose what fits the tone.

**Sparkline characters** (`▁▂▃▄▅▆▇█`) provide 8 height levels per cell for compact inline bar charts — perfect for showing trends in a single line.

**Character width caution**: Characters occupy single or double width (CJK, emoji) or zero width (combining marks). Never use string length for terminal width — use proper `wcwidth()` calculations with caching for performance. Restrict emoji to Unicode 9.0 for reliable cross-terminal rendering.

### Spatial Composition
Terminal space is finite and valuable:
- **Alignment is everything** — misaligned columns feel broken, even by one character
- **Right-align numbers** — always. Decimal points should form a vertical line.
- **Generous gutters** between columns (2-3 chars) prevent visual crowding
- **Vertical rhythm** — consistent spacing between sections creates calm. Inconsistent spacing creates anxiety.
- **Information density** should be intentional: dashboards can be dense, interactive menus should breathe
- **The status bar** is prime real estate — put the most important information there
- **Scroll indicators** (`↑ 3 more`, `↓ 12 more`) should be subtle but discoverable
- **Use fractions, not floats** for proportional layout math — floating-point rounding errors cause single-character gaps in borders and alignment

### Layout Patterns
- **Header → Content → Status** is the canonical TUI layout for good reason — users know where to look
- **Fixed regions** (header, status bar) + **scrollable region** (content) — never scroll everything
- **Modal dialogs** should dim or overlay the background to create depth
- **Progressive disclosure**: show summary first, expand on demand. Don't overwhelm.
- **Responsive to terminal size** — calculate available space, never hardcode row/column counts. Set sensible minimums and degrade gracefully.
- **Panel-based layouts** expose all relevant information at once — segment the screen to eliminate state queries. The user should never need to run a command to see current state.
- **Docking**: Headers and footers auto-position at screen edges. Content fills the middle.

**The Lazygit principle**: The interface should always show current state, available actions, and the result of those actions — all in one view. Traditional CLIs separate commands from queries; great TUIs unify them.

### Animation & Feedback
Terminal animation should be functional, not decorative:
- **Spinners** (`|/-\` or braille rotation `⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏`) for indeterminate progress — keep them small and peripheral
- **Progress bars** for determinate progress — use eighth-block characters (`▏▎▍▌▋▊▉█`) for smooth sub-character precision. Show actual throughput/stats alongside, not just a bar.
- **X of Y pattern** ("3 of 10 files") — the most honest progress indicator
- **Flash/highlight** briefly to confirm an action, then return to normal. Flash messages with expiry timestamps can overlay the status bar temporarily.
- **Cursor movement** should feel instant — never animate navigation between items
- **Loading states** should show what's happening, not just that something is happening

**Anti-flicker techniques:**
- **Overwrite, never clear**: Write new content directly over existing content. Clearing creates visible blank frames.
- **Single write operations**: Batch all output into one `write()` call to stdout. Multiple writes show partial updates.
- **Synchronized Output Protocol**: Mark beginning and end of a frame for hardware-synchronized terminal updates.
- **Target 60fps**: Higher framerates provide no noticeable benefit in terminals.

**Animation philosophy**: Gratuitous animation (sidebar sliding in) adds latency and annoys power users. Functional animation (smooth scrolling to maintain reading position) genuinely helps. When in doubt, skip the animation — speed is the terminal's greatest virtue.

### Selection & Focus
- **Selected items** need clear visual distinction: background color change, prefix character, or both
- **Background highlighting** should be subtle — `Color(0, 40, 0)` (dark green) over full bright green
- **Exactly one component active at any time** — indicate with highlighted border, distinct title color, or brightness change
- **Keyboard hints** should be visible but not dominant — right-aligned, dimmed, or in the status bar
- **Context-sensitive hints**: Show different key bindings based on current state (empty input vs active task vs confirmation pending)
- **Multi-select** needs a different indicator than single-select (checkbox `[x]` vs highlight)
- **Single-key context-dependent actions** reduce cognitive load — the same key does "the right thing" regardless of item state (Lazygit maps one key to "discard changes" whether that means `git reset`, `git checkout`, or `git clean` underneath)

### Status Bar Design
The status bar is the TUI's most valuable real estate:
- **Adaptive layout**: Single-line when space permits, two-line when width is constrained, hint-only as last resort
- **Content organization**: Items separated by dim separators (`·` or ` │ `), right-aligned hints
- **Silently skip missing data** — never show empty placeholders
- **Mode indicators**: Colored brackets `[ Scan ]` (yellow), `[ Browse ]` (green), `[ Delete ]` (red) — only when in a non-default mode
- **Command transparency**: Consider showing the underlying operation being performed — users gain confidence understanding what happens beneath the UI

### Information Hierarchy
Create clear visual layers:
1. **Primary**: The data the user came for — brightest, largest, most prominent
2. **Secondary**: Context and metadata — standard brightness, supporting position
3. **Tertiary**: Navigation hints, decorative borders, labels — dimmed, peripheral
4. **Background**: Frame structure that should be felt but not read — lowest contrast

### Data Visualization in Terminal
When displaying data-heavy content:
- **Braille charts**: 2x4 dot grid per character cell — the highest resolution available in terminal. Ideal for line charts, scatter plots, and area fills.
- **Block-element histograms**: `▁▂▃▄▅▆▇█` for vertical bars, `▏▎▍▌▋▊▉█` for horizontal bars
- **Heat maps**: Map values to color gradients across a character grid
- **Half-block pixel rendering**: Use `▄` with different foreground/background colors to render 2 independently-colored rows per character cell — effectively doubling vertical resolution
- **Shade characters** (`░▒▓█`) for density visualization — four levels from sparse to solid
- **Inline sparklines**: Embed `▁▃▅▇▅▃▁` directly in text for at-a-glance trends

**Gradient rendering**: Interpolate between color stops using HSV for vibrant results or RGB for subtle transitions. HSV "short spin" takes the shortest hue path; "long spin" creates rainbow effects.

## Anti-Patterns to Avoid

- **Rainbow soup**: Using every available color with no system or hierarchy
- **Border prisons**: Wrapping every element in a box until the screen is more lines than content
- **Wall of text**: Dumping unformatted data without visual structure or whitespace
- **GUI envy**: Trying to recreate dropdown menus, floating windows, or rounded buttons — embrace the grid
- **Inconsistent spacing**: Different padding in different sections destroys visual coherence
- **Mystery meat navigation**: Key bindings with no visible hints anywhere on screen
- **Flickering**: Clearing and redrawing when you could overwrite in place
- **Hardcoded dimensions**: Assuming 80x24 terminals — always measure and adapt
- **Color-only signaling**: Information conveyed only through color is invisible to colorblind users and `NO_COLOR` environments
- **Input lag from over-rendering**: Reactive frameworks can trigger full re-renders on every keystroke — debounce or optimize hot paths
- **Emoji overreach**: Multi-codepoint emoji (skin tones, ZWJ sequences) render unpredictably across terminals — stick to Unicode 9.0 or earlier

## Accessibility

TUI accessibility is often overlooked but critically important:

- **Respect `NO_COLOR`**: When the `NO_COLOR` environment variable is set, disable color output. Use bold, underline, and italic as alternatives — the standard only disables color, not other ANSI attributes.
- **Never rely solely on color**: Pair color with symbols (`✓`/`✗`), position, indentation, or text labels
- **Provide monochrome/high-contrast modes** as configuration options
- **Minimize cursor teleportation**: Continuous redraws (spinners, live timers) cause screen readers to announce random content fragments. Constrain dynamic updates to small, predictable regions.
- **Graceful color degradation**: Auto-degrade TrueColor → ANSI256 → ANSI16 → no color based on terminal capabilities
- **Light/dark awareness**: Provide separate color values for light and dark terminal backgrounds (AdaptiveColor pattern), or detect background and adjust

## Implementation Principles

- **Screen = f(state)**: The entire screen should be a pure function of application state. No partial updates, no mutation. This is the single most important architectural decision.
- **Line-based rendering**: Build the screen as a list of styled lines, each containing styled segments. This makes testing and reasoning about layout trivial.
- **Segment model**: Each piece of text carries its own foreground color, background color, and style. Compose segments into lines, lines into screens.
- **Width-aware everything**: Every string operation must account for terminal width. Truncate with ellipsis, never wrap unexpectedly.
- **Measure twice, render once**: Calculate all positions and sizes before emitting any output.
- **Batch writes**: Accumulate the entire frame, then write to stdout in a single operation.
- **Style inheritance with overrides**: Unset properties inherit from parent context; explicitly set properties always win. Exclude padding/margins from inheritance.

## Exemplary TUI Applications

Study these for inspiration — they represent the best of terminal interface design:

- **btop**: Information-dense dashboard with braille-character graphs. Proves that density and beauty coexist.
- **lazygit**: State + actions + results in one view. Context-dependent keys. Command transparency log. The gold standard for interactive TUIs.
- **lazydocker**: Live overview replaces repeated `docker ps`. Unified workflow across containers, images, volumes.
- **k9s**: Kubernetes dashboard that makes complex infrastructure navigable. Excellent use of color hierarchy.
- **ncdu / gdu**: Disk usage analysis with focused, simple navigation. Solves one problem exceptionally well.
- **fx**: JSON viewer with collapse/expand and syntax highlighting. Shows how to handle hierarchical data.
- **ranger / lf**: File managers that prove terminal navigation can be faster than GUI equivalents.

Remember: The greatest terminal interfaces feel like they were designed by someone who loves the terminal. They don't apologize for being text. They celebrate it. Build interfaces worthy of that tradition.
