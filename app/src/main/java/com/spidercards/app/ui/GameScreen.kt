package com.spidercards.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spidercards.app.GameViewModel
import spider.game.Move
import spider.game.Spider

object TableColors {
    val felt = Color(0xFF1F6B3A)
    val feltDark = Color(0xFF15502A)
    val text = Color(0xFFF1F5EE)
    val dim = Color(0xFFB9D3BF)
    val accent = Color(0xFFFFC107)
}

@Composable
fun GameScreen(vm: GameViewModel) {
    var showStats by remember { mutableStateOf(false) }
    var confirmNewGame by remember { mutableStateOf(false) }
    val state = vm.state

    fun requestNewGame() {
        if (vm.session.started && !state.isWon) confirmNewGame = true else vm.newGame()
    }

    Box(Modifier.fillMaxSize().background(TableColors.felt)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            StatusBar(
                timeMs = vm.elapsedMs,
                score = state.score,
                moves = state.moves,
                runs = state.foundations.size,
            )
            Board(
                state = state,
                onMove = { from, index, to -> vm.move(Move(from, index, to)) },
                onTapCard = { column, index -> vm.autoMove(column, index) },
                onDeal = vm::deal,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            )
            val hint = when {
                vm.toast != null -> vm.toast
                vm.outlook == Spider.Outlook.DEAL_NEEDED ->
                    if (state.columns.any { it.isEmpty() }) {
                        "No useful moves left. Fill the empty columns, then deal."
                    } else {
                        "No useful moves left. Tap the stock to deal."
                    }
                else -> null
            }
            Text(
                hint ?: "",
                color = TableColors.accent,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().height(20.dp),
            )
            ActionBar(
                canUndo = vm.session.canUndo,
                onNewGame = ::requestNewGame,
                onUndo = vm::undo,
                onStats = { showStats = true },
            )
        }

        if (showStats) {
            BackHandler { showStats = false }
            StatsScreen(stats = vm.stats, onClose = { showStats = false })
        }
    }

    if (confirmNewGame) {
        AlertDialog(
            onDismissRequest = { confirmNewGame = false },
            title = { Text("Start a new game?") },
            text = { Text("The current game will be abandoned.") },
            confirmButton = {
                TextButton(onClick = { confirmNewGame = false; vm.newGame() }) { Text("New game") }
            },
            dismissButton = { TextButton(onClick = { confirmNewGame = false }) { Text("Keep playing") } },
        )
    }

    if (vm.showStuckWarning) {
        AlertDialog(
            onDismissRequest = vm::dismissStuckWarning,
            title = { Text("No moves left") },
            text = { Text("There are no useful moves and the stock is empty. Undo to try another way, or start a new game.") },
            confirmButton = {
                TextButton(onClick = { vm.dismissStuckWarning(); vm.undo() }, enabled = vm.session.canUndo) {
                    Text("Undo")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::dismissStuckWarning) { Text("Keep looking") }
                    TextButton(onClick = vm::newGame) { Text("New game") }
                }
            },
        )
    }

    vm.win?.let { win ->
        AlertDialog(
            onDismissRequest = vm::dismissWin,
            title = { Text("You won!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Time: ${formatTime(win.timeMs)}")
                    Text("Score: ${win.score}")
                    Text("Moves: ${win.moves}")
                    if (win.newBestTime) Text("New best time!", fontWeight = FontWeight.Bold)
                    win.scoreRank?.let { Text("Number $it on your high score board", fontWeight = FontWeight.Bold) }
                }
            },
            confirmButton = { TextButton(onClick = vm::newGame) { Text("New game") } },
            dismissButton = {
                TextButton(onClick = { vm.dismissWin(); showStats = true }) { Text("Stats") }
            },
        )
    }
}

@Composable
private fun StatusBar(timeMs: Long, score: Int, moves: Int, runs: Int) {
    Row(
        Modifier.fillMaxWidth().background(TableColors.feltDark).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatusItem("Time", formatTime(timeMs))
        StatusItem("Score", score.toString())
        StatusItem("Moves", moves.toString())
        StatusItem("Runs", "$runs/8")
    }
}

@Composable
private fun StatusItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TableColors.dim, fontSize = 11.sp)
        Text(value, color = TableColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionBar(canUndo: Boolean, onNewGame: () -> Unit, onUndo: () -> Unit, onStats: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(TableColors.feltDark).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BarButton("New game", onClick = onNewGame)
        BarButton("Undo", enabled = canUndo, onClick = onUndo)
        BarButton("Stats", onClick = onStats)
    }
}

@Composable
private fun BarButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(8.dp)) {
        Text(
            label,
            color = if (enabled) TableColors.text else TableColors.dim.copy(alpha = 0.4f),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
