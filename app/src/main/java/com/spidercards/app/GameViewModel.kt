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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import spider.game.Action
import spider.game.GameState
import spider.game.Solver
import spider.game.Move
import spider.game.ScoreEntry
import spider.game.Session
import spider.game.Spider
import spider.game.Stats
import java.time.LocalDate

/** The suggested next step, shown on the table. [winning] is true when it's the first step of a found win. */
data class HintView(val action: Action, val winning: Boolean)

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

    /** When on, new games are only dealt once the solver has proven them winnable. */
    var winnableOnly by mutableStateOf(storage.loadWinnableOnly())
        private set

    /** True while the player waits for a winnable deal to be found. */
    var findingDeal by mutableStateOf(false)
        private set

    /** A winnable deal found in the background, ready for the next new game. */
    private var nextDeal: GameState? = storage.loadNextDeal()
    private var dealJob: Job? = null

    var hint by mutableStateOf<HintView?>(null)
        private set
    var thinking by mutableStateOf(false)
        private set
    private var hintJob: Job? = null

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
        if (findingDeal) return
        win = null
        stuckDismissedAt = -1
        stopClock()
        if (!winnableOnly) {
            startGame(Spider.newGame())
            return
        }
        val ready = nextDeal
        if (ready != null) {
            nextDeal = null
            storage.saveNextDeal(null)
            startGame(ready)
        } else {
            // Nothing prepared yet: wait for the background search, which usually takes a second or two.
            findingDeal = true
            prepareNextDeal()
        }
    }

    private fun startGame(deal: GameState) {
        elapsedMs = 0
        apply(Session(deal), countStart = false)
        prepareNextDeal()
    }

    fun changeWinnableOnly(on: Boolean) {
        winnableOnly = on
        storage.saveWinnableOnly(on)
        if (on) prepareNextDeal() else dealJob?.cancel()
    }

    /** Finds the next winnable deal in the background, so New game is instant when the time comes. */
    private fun prepareNextDeal() {
        if (!winnableOnly || nextDeal != null || dealJob?.isActive == true) return
        dealJob = viewModelScope.launch {
            val deal = withContext(Dispatchers.Default) {
                Solver.winnableDeal { !isActive }
            } ?: return@launch
            if (findingDeal) {
                findingDeal = false
                startGame(deal)
            } else {
                nextDeal = deal
                withContext(Dispatchers.IO) { storage.saveNextDeal(deal) }
            }
        }
    }

    /** Looks for a winning line from here and shows its first step, or the most promising move if none is found. */
    fun requestHint() {
        if (thinking || state.isWon) return
        val position = state
        thinking = true
        hintJob = viewModelScope.launch {
            val found = withContext(Dispatchers.Default) {
                val solver = Solver(nodeLimit = HINT_NODE_LIMIT)
                val solution = solver.solve(position) { !isActive }
                ensureActive()
                when {
                    solution != null -> HintView(solution.first(), winning = true)
                    else -> solver.candidateMoves(position).firstOrNull()?.let { HintView(Action.MoveCards(it), false) }
                        ?: if (Spider.canDeal(position)) HintView(Action.Deal, false) else null
                }
            }
            thinking = false
            if (state !== position) return@launch
            if (found == null) {
                showToast("No moves left from here. Try undo.")
                return@launch
            }
            hint = found
            if (!found.winning) showToast("No sure win found from here, but this looks best.")
            delay(HINT_SHOW_MS)
            if (hint == found) hint = null
        }
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
        hintJob?.cancel()
        hint = null
        thinking = false
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
        prepareNextDeal()
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

    companion object {
        const val HINT_NODE_LIMIT = 60_000
        const val HINT_SHOW_MS = 3000L
    }
}
