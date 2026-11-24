package com.spidercards.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spider.game.Stats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
private fun LocalDate.pretty(): String = format(dateFormat)

@Composable
fun StatsScreen(stats: Stats, onClose: () -> Unit, onReset: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(TableColors.feltDark)
            // Swallow taps so they don't reach the table underneath.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Statistics",
                color = TableColors.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text("Close", color = TableColors.accent, fontSize = 16.sp) }
        }

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Played", stats.played.toString(), Modifier.weight(1f))
                Tile("Won", stats.won.toString(), Modifier.weight(1f))
                Tile("Win rate", "${stats.winRate}%", Modifier.weight(1f))
            }

            Panel("Best time") {
                val best = stats.bestTime
                if (best == null) {
                    Muted("Win a game to set your first best time.")
                } else {
                    Text(formatTime(best.timeMs), color = TableColors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Muted("Set on ${best.date.pretty()}")
                }
            }

            Panel("Best time history") {
                if (stats.bestTimes.isEmpty()) {
                    Muted("Every time you beat your best time, it's listed here with the date.")
                } else {
                    stats.bestTimes.asReversed().forEach { record ->
                        TableRow(listOf(record.date.pretty(), formatTime(record.timeMs)), listOf(2f, 1f))
                    }
                }
            }

            Panel("High scores") {
                if (stats.highScores.isEmpty()) {
                    Muted("Your top ${Stats.MAX_HIGH_SCORES} winning scores will appear here.")
                } else {
                    TableRow(listOf("#", "Score", "Time", "Moves", "Date"), weights, header = true)
                    stats.highScores.forEachIndexed { i, entry ->
                        TableRow(
                            listOf(
                                "${i + 1}",
                                entry.score.toString(),
                                formatTime(entry.timeMs),
                                entry.moves.toString(),
                                entry.date.pretty(),
                            ),
                            weights,
                        )
                    }
                }
            }
            TextButton(
                onClick = { confirmReset = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Reset all stats", color = TableColors.dim, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all stats?") },
            text = { Text("Games played, wins, best times and high scores will all go back to zero. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

private val weights = listOf(0.5f, 1f, 1f, 1f, 2f)

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(TableColors.felt, RoundedCornerShape(12.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = TableColors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TableColors.dim, fontSize = 13.sp)
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(TableColors.felt, RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = TableColors.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun Muted(text: String) = Text(text, color = TableColors.dim, fontSize = 14.sp)

@Composable
private fun TableRow(cells: List<String>, weights: List<Float>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                color = if (header) TableColors.dim else TableColors.text,
                fontSize = if (header) 12.sp else 15.sp,
                fontWeight = if (header) FontWeight.Normal else FontWeight.Medium,
                textAlign = if (i == cells.lastIndex) TextAlign.End else TextAlign.Start,
                maxLines = 1,
                modifier = Modifier.weight(weights[i]),
            )
        }
    }
}
