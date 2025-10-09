package helloworld

import androidx.compose.runtime.*
import com.jakewharton.mosaic.runMosaic
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        runMosaic {
            var count by remember { mutableStateOf(0) }
            var isRunning by remember { mutableStateOf(true) }

            // Auto-increment counter
            LaunchedEffect(isRunning) {
                if (isRunning) {
                    while (true) {
                        delay(500)
                        count++
                        if (count >= 20) {
                            isRunning = false
                        }
                    }
                }
            }

            // TUI Layout
            Column {
                Text("╔════════════════════════════════════════╗", color = Color.Cyan)
                Text("║   Kotlin Mosaic TUI Prototype          ║", color = Color.Cyan)
                Text("╚════════════════════════════════════════╝", color = Color.Cyan)
                Text("")

                Row {
                    Text("  Counter: ")
                    Text("$count", color = if (count < 10) Color.Green else Color.Yellow)
                }

                Text("")

                Row {
                    Text("  Status: ")
                    Text(
                        if (isRunning) "Running..." else "Completed!",
                        color = if (isRunning) Color.Blue else Color.Green
                    )
                }

                Text("")
                Row {
                    Text("  Progress: [")
                    Text("█".repeat(count), color = Color.Magenta)
                    Text(" ".repeat(20 - count))
                    Text("] ${count * 5}%")
                }
                Text("")

                if (!isRunning) {
                    Text("  ✓ Demo finished!", color = Color.Green)
                }
            }
        }
    }
}
