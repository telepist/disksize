package disksize.ui

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import disksize.domain.model.FileNode
import disksize.domain.model.ScanResult
import disksize.util.SizeFormatter

/**
 * Main screen composable for DiskSize TUI.
 * Displays scan results with directory information.
 */
@Composable
fun MainScreen(scanResult: ScanResult) {
    Column {
        // Header
        HeaderBar()

        // Path
        PathBar(scanResult.rootPath)

        // Statistics
        Statistics(scanResult)

        // Directory list
        DirectoryList(scanResult.rootNode.children)

        // Status bar
        StatusBar(scanResult)
    }
}

@Composable
private fun HeaderBar() {
    Text("╔═════════════════════════════════════════════════════════════╗", color = Color.Cyan)
    Text("║ DiskSize - Disk Space Analyzer                             ║", color = Color.Cyan)
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
private fun Statistics(scanResult: ScanResult) {
    Text("║                                                             ║")
    Text("║  Total Size: ${SizeFormatter.format(scanResult.totalSize).padEnd(48)}║")
    Text("║  Files: ${scanResult.fileCount.toString().padEnd(53)}║")
    Text("║  Directories: ${scanResult.directoryCount.toString().padEnd(48)}║")
    Text("║                                                             ║")
}

@Composable
private fun DirectoryList(children: List<FileNode>) {
    Text("║  Subdirectories:                                           ║")
    Text("║  ┌────────────────────────────────────────────────────┐   ║")

    if (children.isEmpty()) {
        Text("║  │ (empty directory)                                  │   ║")
    } else {
        // Sort by size descending and take top entries
        val sortedChildren = children.sortedByDescending { it.totalSize() }
        val displayCount = minOf(sortedChildren.size, 10)

        for (i in 0 until displayCount) {
            val child = sortedChildren[i]
            DirectoryItem(child, children.sumOf { it.totalSize() })
        }

        if (sortedChildren.size > displayCount) {
            Text("║  │ ... and ${sortedChildren.size - displayCount} more${" ".repeat(35 - (sortedChildren.size - displayCount).toString().length)}│   ║")
        }
    }

    Text("║  └────────────────────────────────────────────────────┘   ║")
    Text("║                                                             ║")
}

@Composable
private fun DirectoryItem(node: FileNode, totalParentSize: Long) {
    val size = node.totalSize()
    val percentage = if (totalParentSize > 0) {
        (size.toDouble() / totalParentSize * 100)
    } else {
        0.0
    }

    val sizeStr = SizeFormatter.format(size)
    val percentStr = formatPercentage(percentage)

    // Create visual bar
    val barWidth = 20
    val filledWidth = ((percentage / 100.0) * barWidth).toInt()
    val bar = "█".repeat(filledWidth) + " ".repeat(barWidth - filledWidth)

    // Get color based on size
    val sizeColor = when {
        size >= 1_000_000_000 -> Color.Red      // >= 1 GB
        size >= 100_000_000 -> Color.Yellow     // >= 100 MB
        size >= 10_000_000 -> Color.Cyan        // >= 10 MB
        else -> Color.White
    }

    // Truncate name if too long
    val displayName = if (node.name.length > 18) {
        node.name.take(15) + "..."
    } else {
        node.name
    }
    val nameWithType = if (node.isDirectory) "$displayName/" else displayName

    // Format line
    Row {
        Text("║  │ ")
        Text(nameWithType.padEnd(20), color = if (node.isDirectory) Color.Blue else Color.White)
        Text(" ")
        Text(sizeStr.padEnd(8), color = sizeColor)
        Text(" (")
        Text("$percentStr%", color = sizeColor)
        Text(") ")
        Text(bar, color = Color.Magenta)
        Text(" │   ║")
    }
}

@Composable
private fun StatusBar(scanResult: ScanResult) {
    Text("╠═════════════════════════════════════════════════════════════╣", color = Color.Cyan)

    val durationSec = scanResult.scanDurationMs / 1000.0
    val durationStr = formatDuration(durationSec)
    val statusMsg = "[Scanning completed in ${durationStr}s]"
    val padding = 39 - statusMsg.length

    Row {
        Text("║ ", color = Color.Cyan)
        Text(statusMsg, color = Color.Green)
        Text(" ".repeat(padding))
        Text("q: Quit", color = Color.Yellow)
        Text("  ║", color = Color.Cyan)
    }

    Text("╚═════════════════════════════════════════════════════════════╝", color = Color.Cyan)
}

/**
 * Format a percentage value to one decimal place without using String.format().
 */
private fun formatPercentage(percentage: Double): String {
    val rounded = kotlin.math.round(percentage * 10) / 10.0
    val integerPart = rounded.toInt()
    val fractionalPart = rounded - integerPart
    val decimalDigit = kotlin.math.round(fractionalPart * 10).toInt()
    return "$integerPart.$decimalDigit"
}

/**
 * Format a duration value to one decimal place without using String.format().
 */
private fun formatDuration(seconds: Double): String {
    val rounded = kotlin.math.round(seconds * 10) / 10.0
    val integerPart = rounded.toInt()
    val fractionalPart = rounded - integerPart
    val decimalDigit = kotlin.math.round(fractionalPart * 10).toInt()
    return "$integerPart.$decimalDigit"
}
