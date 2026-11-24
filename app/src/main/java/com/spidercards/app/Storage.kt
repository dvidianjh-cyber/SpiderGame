package com.spidercards.app

import android.content.Context
import spider.game.Codec
import spider.game.GameState
import spider.game.Session
import spider.game.Stats
import java.io.File

/** Saves the game in progress and the stats as small text files in the app's private storage. */
class Storage(context: Context) {
    private val sessionFile = File(context.filesDir, "session.txt")
    private val statsFile = File(context.filesDir, "stats.txt")
    private val nextDealFile = File(context.filesDir, "next_deal.txt")
    private val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val lock = Any()

    fun loadWinnableOnly(): Boolean = settings.getBoolean("winnable_only", false)

    fun saveWinnableOnly(on: Boolean) = settings.edit().putBoolean("winnable_only", on).apply()

    fun loadNextDeal(): GameState? = read(nextDealFile)?.let { runCatching { Codec.decodeState(it.trim()) }.getOrNull() }

    fun saveNextDeal(deal: GameState?) {
        if (deal == null) synchronized(lock) { nextDealFile.delete() } else write(nextDealFile, Codec.encodeState(deal))
    }

    fun loadSession(): Session? = read(sessionFile)?.let { Codec.decodeSession(it) }

    fun saveSession(session: Session) = write(sessionFile, Codec.encodeSession(session))

    fun loadStats(): Stats = read(statsFile)?.let { Codec.decodeStats(it) } ?: Stats()

    fun saveStats(stats: Stats) = write(statsFile, Codec.encodeStats(stats))

    private fun read(file: File): String? = synchronized(lock) {
        runCatching { if (file.exists()) file.readText() else null }.getOrNull()
    }

    /** Writes to a temporary file first, so a crash mid-save never leaves a half-written file. */
    private fun write(file: File, text: String) = synchronized(lock) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }
}
