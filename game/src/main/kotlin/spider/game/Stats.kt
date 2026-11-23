package spider.game

import java.time.LocalDate

data class ScoreEntry(val score: Int, val timeMs: Long, val moves: Int, val date: LocalDate)

/** A best time and the day it was set. */
data class TimeRecord(val timeMs: Long, val date: LocalDate)

/**
 * Lifetime statistics.
 *
 * @param bestTimes every time the fastest win was beaten, oldest first. The last entry is the current record.
 * @param highScores the top scores, best first.
 */
data class Stats(
    val played: Int = 0,
    val won: Int = 0,
    val bestTimes: List<TimeRecord> = emptyList(),
    val highScores: List<ScoreEntry> = emptyList(),
) {
    val bestTime: TimeRecord? get() = bestTimes.lastOrNull()
    val winRate: Int get() = if (played == 0) 0 else (won * 100 + played / 2) / played

    fun recordStarted(): Stats = copy(played = played + 1)

    /** Adds a win. The result says whether it set a new best time and where it landed on the board. */
    fun recordWin(entry: ScoreEntry): WinResult {
        val newBest = bestTime == null || entry.timeMs < bestTime!!.timeMs
        val times = if (newBest) bestTimes + TimeRecord(entry.timeMs, entry.date) else bestTimes
        val board = (highScores + entry)
            .sortedWith(compareByDescending<ScoreEntry> { it.score }.thenBy { it.timeMs })
            .take(MAX_HIGH_SCORES)
        val rank = board.indexOfFirst { it === entry }.takeIf { it >= 0 }?.plus(1)
        return WinResult(
            stats = copy(won = won + 1, bestTimes = times, highScores = board),
            newBestTime = newBest,
            scoreRank = rank,
        )
    }

    companion object {
        const val MAX_HIGH_SCORES = 10
    }
}

data class WinResult(val stats: Stats, val newBestTime: Boolean, val scoreRank: Int?)
