package disksize.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import disksize.domain.model.FileNode
import disksize.presentation.ExplorerState
import disksize.util.SizeFormatter

/**
 * Main screen composable for DiskSize TUI with interactive navigation.
 */
@Composable
fun MainScreen(
    state: ExplorerState,
    onMoveSelection: (Int) -> Unit,
    onOpenSelected: () -> Unit,
    onNavigateUp: () -> Unit,
    onCycleSort: () -> Unit,
    onQuit: () -> Unit
) {
    Column(
        modifier = Modifier.onKeyEvent { event ->
            handleKey(
                event = event,
                moveSelection = onMoveSelection,
                openSelected = onOpenSelected,
                navigateUp = onNavigateUp,
                cycleSort = onCycleSort,
                quit = onQuit
            )
        }
    ) {
        HeaderBar()
        PathBar(state.currentPath)
        Statistics(state)
        DirectoryList(state)
        StatusBar(state)
    }
}

private fun handleKey(
    event: KeyEvent,
    moveSelection: (Int) -> Unit,
    openSelected: () -> Unit,
    navigateUp: () -> Unit,
    cycleSort: () -> Unit,
    quit: () -> Unit
): Boolean {
    return when (event.key) {
        "ArrowDown", "j" -> {
            moveSelection(1); true
        }
        "ArrowUp", "k" -> {
            moveSelection(-1); true
        }
        "Enter", "ArrowRight", "l" -> {
            openSelected(); true
        }
        "Backspace", "ArrowLeft", "h" -> {
            navigateUp(); true
        }
        "s", "S" -> {
            cycleSort(); true
        }
        "q", "Q" -> {
            quit(); true
        }
        else -> false
    }
}

@Composable
private fun HeaderBar() {
    Text("╔═════════════════════════════════════════════════════════════╗", color = Color.Cyan)
    Text("║ DiskSize - Disk Space Analyzer                              ║", color = Color.Cyan)
    Text("╠═════════════════════════════════════════════════════════════╣", color = Color.Cyan)
}

@Composable
private fun PathBar(path: String) {
    val truncatedPath = if (path.length > 56) {
        "..." + path.takeLast(53)
    } else {
        path
    }
    Text("║ Path: $truncatedPath${" ".repeat(56 - truncatedPath.length)}║", color = Color.Cyan)
    Text("╠═════════════════════════════════════════════════════════════╣", color = Color.Cyan)
}

@Composable
private fun Statistics(state: ExplorerState) {
    val totalSize = if (state.scanResult != null) {
        SizeFormatter.format(state.totalSize).padEnd(48)
    } else {
        "--".padEnd(48)
    }
    val files = if (state.scanResult != null) {
        state.fileCount.toString().padEnd(53)
    } else {
        "--".padEnd(53)
    }
    val directories = if (state.scanResult != null) {
        state.directoryCount.toString().padEnd(48)
    } else {
        "--".padEnd(48)
    }

    Text("║                                                             ║")
    Text("║  Total Size: $totalSize║")
    Text("║  Files: $files║")
    Text("║  Directories: $directories║")
    Text("║                                                             ║")
}

@Composable
private fun DirectoryList(state: ExplorerState) {
    val directories = state.directories
    Text(sectionHeader("Subdirectories (Sort: ${state.sortOrder.label})"))
    Text("║  ┌────────────────────────────────────────────────────┐    ║")

    when {
        state.isLoading -> {
            val pathLine = "Scanning ${state.spinnerFrame} ${shortenPath(state.currentPath, 40)}"
            Text(contentLine(pathLine))
            Text(contentLine("Progress: [${loadingProgress(state.spinnerIndex)}]"))
            Text(contentLine("Collecting directory statistics..."))
        }
        state.errorMessage != null -> {
            val message = "ERROR: ${state.errorMessage.take(46)}"
            Text(contentLine(message))
        }
        directories.isEmpty() -> {
            Text(contentLine("(no subdirectories found)"))
        }
        else -> {
            val maxVisible = 10
            val totalCount = directories.size
            val selectedIndex = state.selectedIndex
            val windowStart = when {
                totalCount <= maxVisible -> 0
                selectedIndex < maxVisible -> 0
                selectedIndex >= totalCount - maxVisible -> (totalCount - maxVisible).coerceAtLeast(0)
                else -> selectedIndex - maxVisible / 2
            }
            val windowEnd = (windowStart + maxVisible).coerceAtMost(totalCount)
            val visible = directories.subList(windowStart, windowEnd)
            val totalSize = directories.sumOf { it.totalSize() }.coerceAtLeast(1L)

            if (windowStart > 0) {
                Text(contentLine("... (more above)"))
            }

            visible.forEachIndexed { index, node ->
                val absoluteIndex = windowStart + index
                val isSelected = absoluteIndex == selectedIndex
                DirectoryItem(node, totalSize, isSelected)
            }

            if (windowEnd < totalCount) {
                Text(contentLine("... (more below)"))
            }
        }
    }

    if (state.warningCount > 0 && !state.isLoading) {
        val warningText = "Warnings: ${state.warningCount} item(s) skipped"
        Text(contentLine(warningText))
    }

    Text("║  └────────────────────────────────────────────────────┘   ║")
    Text("║                                                             ║")
}

private fun sectionHeader(title: String): String {
    val innerWidth = 55
    val padded = title.padEnd(innerWidth)
    return "║  ${padded.take(innerWidth)} ║"
}

private fun contentLine(text: String): String {
    val innerWidth = 54
    val padded = text.padEnd(innerWidth)
    return "║  │ ${padded.take(innerWidth)}│   ║"
}

private const val PROGRESS_BAR_WIDTH = 20

private fun loadingProgress(step: Int): String {
    val range = PROGRESS_BAR_WIDTH * 2
    val pulse = step % range
    val filled = if (pulse <= PROGRESS_BAR_WIDTH) pulse else range - pulse
    val safeFilled = filled.coerceIn(0, PROGRESS_BAR_WIDTH)
    return "█".repeat(safeFilled) + "░".repeat(PROGRESS_BAR_WIDTH - safeFilled)
}

private fun shortenPath(path: String, maxLength: Int): String {
    if (path.length <= maxLength) return path
    if (maxLength <= 3) return path.take(maxLength)
    return "..." + path.takeLast(maxLength - 3)
}

@Composable
private fun DirectoryItem(node: FileNode, totalParentSize: Long, isSelected: Boolean) {
    val size = node.totalSize()
    val percentage = if (totalParentSize > 0) {
        size.toDouble() / totalParentSize * 100
    } else {
        0.0
    }

    val sizeStr = SizeFormatter.format(size)
    val percentStr = formatPercentage(percentage)

    val barWidth = 20
    val filledWidth = ((percentage / 100.0) * barWidth).toInt().coerceIn(0, barWidth)
    val bar = "█".repeat(filledWidth) + " ".repeat(barWidth - filledWidth)

    val sizeColor = when {
        size >= 1_000_000_000 -> Color.Red
        size >= 100_000_000 -> Color.Yellow
        size >= 10_000_000 -> Color.Cyan
        else -> Color.White
    }

    val displayName = if (node.name.length > 18) {
        node.name.take(15) + "..."
    } else {
        node.name
    }
    val nameWithType = if (node.isDirectory) "$displayName/" else displayName

    val selector = if (isSelected) ">" else " "
    val nameColor = if (isSelected) Color.Green else if (node.isDirectory) Color.Blue else Color.White

    Row {
        Text("║  │ $selector")
        Text(" ")
        Text(nameWithType.padEnd(19), color = nameColor)
        Text(" ")
        Text(sizeStr.padEnd(8), color = sizeColor)
        Text(" (")
        Text("$percentStr%", color = sizeColor)
        Text(") ")
        Text(bar, color = if (isSelected) Color.Green else Color.Magenta)
        Text(" │   ║")
    }
}

@Composable
private fun StatusBar(state: ExplorerState) {
    Text("╠═════════════════════════════════════════════════════════════╣", color = Color.Cyan)

    Row {
        Text("║ ", color = Color.Cyan)
        if (state.isLoading) {
            val statusMsg = "[Scanning ${state.spinnerFrame}]"
            val pathInfo = shortenPath(state.currentPath, 32)
            Text(statusMsg, color = Color.Yellow)
            Text("  Path: $pathInfo", color = Color.Cyan)
        } else {
            val statusMsg = when {
                state.errorMessage != null -> "[${state.errorMessage.take(36)}]"
                state.scanResult != null -> {
                    val seconds = state.scanDurationMs / 1000.0
                    val warningSuffix = if (state.warningCount > 0) " with ${state.warningCount} warning(s)" else ""
                    "[Scan completed in ${formatDuration(seconds)}s$warningSuffix]"
                }
                else -> "[Idle]"
            }
            val statusColor = when {
                state.errorMessage != null -> Color.Red
                state.scanResult != null -> Color.Green
                else -> Color.Cyan
            }
            val selected = state.selectedDirectory
            val selectedSummary = selected?.let {
                val size = SizeFormatter.format(it.totalSize())
                "${it.name} ($size)"
            } ?: "--"

            Text(statusMsg, color = statusColor)
            Text("  Sort: ${state.sortOrder.label}", color = Color.Cyan)
            Text("  Selected: $selectedSummary", color = Color.Green)
            if (state.warningCount > 0 && state.errorMessage == null) {
                Text(" ⚠${state.warningCount}", color = Color.Yellow)
            }
        }
        Text("  q: Quit", color = Color.Yellow)
        Text(" ║", color = Color.Cyan)
    }

    Text("╚═════════════════════════════════════════════════════════════╝", color = Color.Cyan)
}

private fun formatPercentage(percentage: Double): String {
    val rounded = kotlin.math.round(percentage * 10) / 10.0
    val integerPart = rounded.toInt()
    val fractionalPart = rounded - integerPart
    val decimalDigit = kotlin.math.round(fractionalPart * 10).toInt()
    return "$integerPart.$decimalDigit"
}

private fun formatDuration(seconds: Double): String {
    val rounded = kotlin.math.round(seconds * 10) / 10.0
    val integerPart = rounded.toInt()
    val fractionalPart = rounded - integerPart
    val decimalDigit = kotlin.math.round(fractionalPart * 10).toInt()
    return "$integerPart.$decimalDigit"
}
