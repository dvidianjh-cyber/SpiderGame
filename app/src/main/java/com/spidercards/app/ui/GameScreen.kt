package com.spidercards.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spidercards.app.GameViewModel
import spider.game.Move
import spider.game.Spider

object TableColors {
    val felt = Color(0xFF237A35)
    val feltDark = Color(0xFF175A26)
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
        val board = @Composable { modifier: Modifier ->
            Box(modifier) {
                Board(
                    state = state,
                    onMove = { from, index, to -> vm.move(Move(from, index, to)) },
                    onTapCard = { column, index -> vm.autoMove(column, index) },
                    onDeal = vm::deal,
                    hint = vm.hint?.action,
                )
                if (hint != null) HintPill(hint, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
            }
        }
        val controls = Controls(
            timeMs = vm.elapsedMs,
            score = state.score,
            moves = state.moves,
            runs = state.foundations.size,
            canUndo = vm.session.canUndo,
            thinking = vm.thinking,
            onNewGame = ::requestNewGame,
            onUndo = vm::undo,
            onHint = vm::requestHint,
            onStats = { showStats = true },
        )

        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (maxWidth > maxHeight) {
                // Landscape: every bit of height goes to the cards; controls sit in a slim panel on the right.
                Row(Modifier.fillMaxSize()) {
                    board(Modifier.weight(1f).fillMaxHeight().padding(start = 2.dp))
                    SidePanel(controls)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    TopStrip(controls)
                    board(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 2.dp))
                }
            }
        }

        if (vm.findingDeal) {
            Box(
                Modifier.fillMaxSize().zIndex(4000f).background(Color(0x99000000))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = TableColors.accent)
                    Text("Finding a winnable deal...", color = TableColors.text, fontSize = 16.sp)
                }
            }
        }

        if (showStats) {
            BackHandler { showStats = false }
            StatsScreen(
                stats = vm.stats,
                winnableOnly = vm.winnableOnly,
                onWinnableOnlyChange = vm::changeWinnableOnly,
                onClose = { showStats = false },
                onReset = vm::resetStats,
            )
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

private class Controls(
    val timeMs: Long,
    val score: Int,
    val moves: Int,
    val runs: Int,
    val canUndo: Boolean,
    val thinking: Boolean,
    val onNewGame: () -> Unit,
    val onUndo: () -> Unit,
    val onHint: () -> Unit,
    val onStats: () -> Unit,
)

/** Landscape: a narrow column of stats and buttons down the right edge. */
@Composable
private fun SidePanel(c: Controls) {
    Column(
        Modifier.fillMaxHeight().width(84.dp).background(TableColors.feltDark).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatTime(c.timeMs), color = TableColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            SmallStat("Score", c.score.toString())
            SmallStat("Moves", c.moves.toString())
            SmallStat("Runs", "${c.runs}/8")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ControlButtons(c)
        }
    }
}

/** Portrait: one thin row with the numbers and buttons together. */
@Composable
private fun TopStrip(c: Controls) {
    Row(
        Modifier.fillMaxWidth().background(TableColors.feltDark).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(formatTime(c.timeMs), color = TableColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        SmallStat("Score", c.score.toString())
        SmallStat("Runs", "${c.runs}/8")
        Spacer(Modifier.weight(1f))
        ControlButtons(c)
    }
}

@Composable
private fun SmallStat(label: String, value: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = TableColors.dim, fontSize = 11.sp)) { append("$label ") }
            withStyle(SpanStyle(color = TableColors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)) { append(value) }
        },
        maxLines = 1,
    )
}

@Composable
private fun ControlButtons(c: Controls) {
    PanelButton(Icons.AutoMirrored.Filled.Undo, "Undo", enabled = c.canUndo, onClick = c.onUndo)
    PanelButton(Icons.Filled.Lightbulb, if (c.thinking) "..." else "Hint", enabled = !c.thinking, onClick = c.onHint)
    PanelButton(Icons.Filled.Refresh, "New", onClick = c.onNewGame)
    PanelButton(Icons.Filled.BarChart, "Stats", onClick = c.onStats)
}

/** An icon with a small caption underneath. */
@Composable
private fun PanelButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (enabled) TableColors.text else TableColors.dim.copy(alpha = 0.4f)
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(26.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun HintPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = TableColors.feltDark,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .zIndex(3000f)
            .background(TableColors.accent, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
