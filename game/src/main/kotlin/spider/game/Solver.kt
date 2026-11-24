package spider.game

import kotlin.random.Random

/** One step of a solution. */
sealed interface Action {
    data class MoveCards(val move: Move) : Action

    data object Deal : Action
}

/**
 * A depth-first solver for two-suit Spider. It knows where every hidden card is, so a solution proves a deal
 * can be won, though a player still has to find the way. It stops after [nodeLimit] positions and then
 * reports no solution, which means "not found", not "impossible".
 */
class Solver(private val nodeLimit: Int = DEFAULT_NODE_LIMIT) {
    var nodesSearched = 0
        private set
    private val seen = HashSet<Long>()
    private val path = ArrayList<Action>()

    /** A list of actions that wins from [start], or null if none was found within the limit. */
    fun solve(start: GameState, isCancelled: () -> Boolean = { false }): List<Action>? {
        nodesSearched = 0
        seen.clear()
        path.clear()
        return if (search(start, isCancelled) == Outcome.WIN) path.toList() else null
    }

    private enum class Outcome { WIN, DEAD_END, OUT_OF_TIME }

    private fun search(state: GameState, isCancelled: () -> Boolean): Outcome {
        if (state.isWon) return Outcome.WIN
        if (++nodesSearched > nodeLimit) return Outcome.OUT_OF_TIME
        if (nodesSearched % 1024 == 0 && isCancelled()) return Outcome.OUT_OF_TIME
        if (!seen.add(hash(state))) return Outcome.DEAD_END

        for (move in candidateMoves(state)) {
            val next = Spider.move(state, move) ?: continue
            path.add(Action.MoveCards(move))
            when (search(next, isCancelled)) {
                Outcome.WIN -> return Outcome.WIN
                Outcome.OUT_OF_TIME -> return Outcome.OUT_OF_TIME
                Outcome.DEAD_END -> path.removeAt(path.lastIndex)
            }
        }
        val dealt = Spider.deal(state) ?: return Outcome.DEAD_END
        path.add(Action.Deal)
        return when (val result = search(dealt, isCancelled)) {
            Outcome.DEAD_END -> {
                path.removeAt(path.lastIndex)
                Outcome.DEAD_END
            }
            else -> result
        }
    }

    /** Useful moves, most promising first. With nothing useful and an empty column blocking the deal, fill it. */
    fun candidateMoves(state: GameState): List<Move> {
        var moves = Spider.usefulMoves(state)
        if (moves.isEmpty() && state.stock.isNotEmpty() && state.columns.any { it.isEmpty() }) {
            moves = Spider.legalMoves(state).filter { state.columns[it.to].isEmpty() }
        }
        return moves.sortedByDescending { promise(state, it) }
    }

    private fun promise(state: GameState, move: Move): Int {
        val source = state.columns[move.from]
        val card = source[move.index]
        val below = source.getOrNull(move.index - 1)
        val target = state.columns[move.to]
        var score = source.size - move.index // longer runs first
        if (below == null) score += 30 // empties a column
        else if (!below.faceUp) score += 40 // turns over a card
        if (target.isNotEmpty() && target.last().suit == card.suit) score += 20 // builds in suit
        if (target.isEmpty()) score -= 15 // empty columns are precious
        return score
    }

    private fun hash(state: GameState): Long {
        var h = -0x340d631b7bdddcdbL // FNV-1a offset basis
        fun mix(value: Int) {
            h = (h xor value.toLong()) * 0x100000001b3L
        }
        mix(state.stock.size)
        for (column in state.columns) {
            for (card in column) mix(card.rank * 4 + card.suit.ordinal * 2 + if (card.faceUp) 1 else 0)
            mix(99)
        }
        return h
    }

    companion object {
        const val DEFAULT_NODE_LIMIT = 150_000

        /** Deals random games until one is proven winnable. Returns null only if cancelled. */
        fun winnableDeal(random: Random = Random.Default, isCancelled: () -> Boolean = { false }): GameState? {
            val solver = Solver()
            while (!isCancelled()) {
                val game = Spider.newGame(random)
                if (solver.solve(game, isCancelled) != null) return game
            }
            return null
        }
    }
}
