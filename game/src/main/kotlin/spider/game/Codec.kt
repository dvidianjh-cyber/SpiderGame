package spider.game

import java.time.LocalDate

/**
 * Plain-text save format. Kept hand-written and small so the game needs no extra libraries.
 * Any decode failure returns null, and the app then starts fresh rather than crashing.
 */
object Codec {
    private const val SESSION_HEADER = "spider-session-v1"
    private const val STATS_HEADER = "spider-stats-v1"

    fun encodeSession(session: Session): String = buildString {
        appendLine(SESSION_HEADER)
        appendLine(session.elapsedMs)
        appendLine(encodeState(session.state))
        session.history.forEach { appendLine(encodeState(it)) }
    }

    fun decodeSession(text: String): Session? = runCatching {
        val lines = text.lines().filter { it.isNotBlank() }
        require(lines.first() == SESSION_HEADER)
        val elapsed = lines[1].toLong()
        val state = decodeState(lines[2])
        val history = lines.drop(3).map { decodeState(it) }
        Session(state, history, elapsed)
    }.getOrNull()

    fun encodeState(state: GameState): String = listOf(
        state.moves.toString(),
        state.columns.joinToString("|") { cards(it) },
        cards(state.stock),
        state.foundations.joinToString("|") { cards(it) },
    ).joinToString("/")

    fun decodeState(line: String): GameState {
        val parts = line.split("/")
        require(parts.size == 4)
        val columns = parts[1].split("|").map { parseCards(it) }
        require(columns.size == GameState.COLUMN_COUNT)
        val foundations = if (parts[3].isEmpty()) emptyList() else parts[3].split("|").map { parseCards(it) }
        return GameState(columns, parseCards(parts[2]), foundations, parts[0].toInt())
    }

    private fun cards(cards: List<Card>) =
        cards.joinToString(",") { "${it.id}.${it.rank}${it.suit.code}${if (it.faceUp) "u" else "d"}" }

    private fun parseCards(text: String): List<Card> {
        if (text.isEmpty()) return emptyList()
        return text.split(",").map { token ->
            val (id, rest) = token.split(".")
            val faceUp = when (rest.last()) {
                'u' -> true
                'd' -> false
                else -> error("bad card $token")
            }
            val suit = Suit.fromCode(rest[rest.length - 2])
            val rank = rest.substring(0, rest.length - 2).toInt()
            require(rank in 1..13)
            Card(id.toInt(), rank, suit, faceUp)
        }
    }

    fun encodeStats(stats: Stats): String = buildString {
        appendLine(STATS_HEADER)
        appendLine("played=${stats.played}")
        appendLine("won=${stats.won}")
        stats.bestTimes.forEach { appendLine("best=${it.timeMs};${it.date}") }
        stats.highScores.forEach { appendLine("score=${it.score};${it.timeMs};${it.moves};${it.date}") }
    }

    fun decodeStats(text: String): Stats? = runCatching {
        val lines = text.lines().filter { it.isNotBlank() }
        require(lines.first() == STATS_HEADER)
        var stats = Stats()
        for (line in lines.drop(1)) {
            val (key, value) = line.split("=", limit = 2)
            val fields = value.split(";")
            stats = when (key) {
                "played" -> stats.copy(played = value.toInt())
                "won" -> stats.copy(won = value.toInt())
                "best" -> stats.copy(
                    bestTimes = stats.bestTimes + TimeRecord(fields[0].toLong(), LocalDate.parse(fields[1])),
                )
                "score" -> stats.copy(
                    highScores = stats.highScores + ScoreEntry(
                        fields[0].toInt(), fields[1].toLong(), fields[2].toInt(), LocalDate.parse(fields[3]),
                    ),
                )
                else -> stats // ignore keys from newer versions
            }
        }
        stats
    }.getOrNull()
}
