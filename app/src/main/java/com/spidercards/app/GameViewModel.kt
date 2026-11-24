package com.spidercards.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import spider.game.GameState
import spider.game.Move
import spider.game.ScoreEntry
import spider.game.Session
import spider.game.Spider
import spider.game.Stats
import java.time.LocalDate

/** Shown when a game is won. */
data class WinSummary(
    val score: Int,
    val timeMs: Long,
    val moves: Int,
    val newBestTime: Boolean,
    val scoreRank: Int?,
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = Storage(app)

    var session by mutableStateOf(storage.loadSession()?.takeIf { !it.state.isWon } ?: Session.new())
        private set
    var stats by mutableStateOf(storage.loadStats())
        private set

    /** Live clock, in milliseconds. Kept separate from [session] so ticking doesn't redraw the board. */
    var elapsedMs by mutableStateOf(session.elapsedMs)
        private set

    var outlook by mutableStateOf(Spider.outlook(session.state))
        private set

    /** The no-moves warning, once dismissed, stays hidden until something changes. */
    var stuckDismissedAt by mutableStateOf(-1)
        private set

    var win by mutableStateOf<WinSummary?>(null)
        private set

    /** A short message for the bottom of the screen, such as why a deal was refused. */
    var toast by mutableStateOf<String?>(null)
        private set
    private var toastJob: Job? = null

    private var resumed = false
    private var clockJob: Job? = null
    private var clockBase = 0L

    val state: GameState get() = session.state

    val showStuckWarning: Boolean
        get() = outlook == Spider.Outlook.STUCK && stuckDismissedAt != state.moves && win == null

    fun move(move: Move): Boolean {
        val next = Spider.move(state, move) ?: return false
        apply(session.advance(next))
        return true
    }

    /** Tap-to-move: sends the run starting at [index] to the best spot. Returns false if there is none. */
    fun autoMove(column: Int, index: Int): Boolean {
        val to = Spider.bestDestination(state, column, index) ?: return false
        return move(Move(column, index, to))
    }

    fun deal() {
        when {
            state.stock.isEmpty() -> return
            !Spider.canDeal(state) -> showToast("Fill every empty column before dealing")
            else -> apply(session.advance(Spider.deal(state)!!))
        }
    }

    fun undo() {
        if (session.canUndo) apply(session.undo())
    }

    fun newGame() {
        win = null
        stuckDismissedAt = -1
        stopClock()
        elapsedMs = 0
        apply(Session.new(), countStart = false)
    }

    fun resetStats() {
        updateStats(Stats())
    }

    fun dismissStuckWarning() {
        stuckDismissedAt = state.moves
    }

    fun dismissWin() {
        win = null
    }

    private fun apply(next: Session, countStart: Boolean = true) {
        if (countStart && !session.started && next.started) {
            updateStats(stats.recordStarted())
        }
        session = next.copy(elapsedMs = elapsedMs)
        outlook = Spider.outlook(state)
        if (state.isWon) {
            onWin()
        } else {
            ensureClock()
        }
        save()
    }

    private fun onWin() {
        stopClock()
        session = session.copy(elapsedMs = elapsedMs)
        val entry = ScoreEntry(state.score, elapsedMs, state.moves, LocalDate.now())
        val result = stats.recordWin(entry)
        updateStats(result.stats)
        win = WinSummary(entry.score, entry.timeMs, entry.moves, result.newBestTime, result.scoreRank)
    }

    private fun updateStats(next: Stats) {
        stats = next
        val snapshot = next
        viewModelScope.launch(Dispatchers.IO) { storage.saveStats(snapshot) }
    }

    fun onResume() {
        resumed = true
        ensureClock()
    }

    fun onPause() {
        resumed = false
        stopClock()
        session = session.copy(elapsedMs = elapsedMs)
        storage.saveSession(session)
    }

    private fun ensureClock() {
        if (!resumed || clockJob != null || !session.started || state.isWon) return
        clockBase = System.currentTimeMillis() - elapsedMs
        clockJob = viewModelScope.launch {
            while (isActive) {
                elapsedMs = System.currentTimeMillis() - clockBase
                delay(250)
            }
        }
    }

    private fun stopClock() {
        clockJob?.cancel()
        clockJob = null
    }

    private fun save() {
        val snapshot = session
        viewModelScope.launch(Dispatchers.IO) { storage.saveSession(snapshot) }
    }

    private fun showToast(message: String) {
        toastJob?.cancel()
        toast = message
        toastJob = viewModelScope.launch {
            delay(2500)
            toast = null
        }
    }
}
