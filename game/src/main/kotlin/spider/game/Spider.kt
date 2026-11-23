package spider.game

import kotlin.random.Random

/** The rules of two-suit Spider Solitaire. Every function is pure: it returns a new state. */
object Spider {

    fun newGame(random: Random = Random.Default): GameState {
        val deck = buildList {
            var id = 0
            repeat(4) {
                for (suit in Suit.entries) {
                    for (rank in 1..13) add(Card(id++, rank, suit))
                }
            }
        }.shuffled(random)

        // 54 cards to the tableau: the first four columns get six cards, the rest get five.
        var next = 0
        val columns = (0 until GameState.COLUMN_COUNT).map { c ->
            val size = if (c < 4) 6 else 5
            val cards = deck.subList(next, next + size).toMutableList()
            next += size
            cards[cards.lastIndex] = cards.last().copy(faceUp = true)
            cards.toList()
        }
        val stock = deck.subList(next, deck.size).toList()
        return GameState(columns, stock, emptyList())
    }

    /** True when the cards from [index] to the top of [column] form a same-suit descending run. */
    fun isMovableRun(column: List<Card>, index: Int): Boolean {
        if (index !in column.indices) return false
        for (i in index until column.size) {
            val card = column[i]
            if (!card.faceUp) return false
            if (i > index) {
                val below = column[i - 1]
                if (card.suit != below.suit || card.rank != below.rank - 1) return false
            }
        }
        return true
    }

    fun canMove(state: GameState, move: Move): Boolean {
        if (move.from == move.to) return false
        if (move.from !in state.columns.indices || move.to !in state.columns.indices) return false
        val source = state.columns[move.from]
        if (!isMovableRun(source, move.index)) return false
        val target = state.columns[move.to]
        if (target.isEmpty()) return true
        return target.last().rank == source[move.index].rank + 1
    }

    /** Applies [move], or returns null when it isn't legal. */
    fun move(state: GameState, move: Move): GameState? {
        if (!canMove(state, move)) return null
        val columns = state.columns.map { it.toMutableList() }
        val source = columns[move.from]
        val moving = source.subList(move.index, source.size).toList()
        repeat(moving.size) { source.removeAt(source.lastIndex) }
        flipTop(source)
        columns[move.to].addAll(moving)

        val foundations = state.foundations.toMutableList()
        collectRun(columns[move.to], foundations)
        return state.copy(
            columns = columns.map { it.toList() },
            foundations = foundations,
            moves = state.moves + 1,
        )
    }

    fun canDeal(state: GameState): Boolean =
        state.stock.size >= GameState.COLUMN_COUNT && state.columns.none { it.isEmpty() }

    /** Deals one face-up card onto every column, or returns null when dealing isn't allowed. */
    fun deal(state: GameState): GameState? {
        if (!canDeal(state)) return null
        val stock = state.stock.toMutableList()
        val columns = state.columns.map { it.toMutableList() }
        val foundations = state.foundations.toMutableList()
        for (column in columns) {
            column.add(stock.removeAt(stock.lastIndex).copy(faceUp = true))
        }
        for (column in columns) collectRun(column, foundations)
        return state.copy(
            columns = columns.map { it.toList() },
            stock = stock,
            foundations = foundations,
            moves = state.moves + 1,
        )
    }

    /** Every legal move in the position. */
    fun legalMoves(state: GameState): List<Move> = buildList {
        state.columns.forEachIndexed { from, column ->
            for (index in column.indices.reversed()) {
                if (!isMovableRun(column, index)) break
                for (to in state.columns.indices) {
                    val move = Move(from, index, to)
                    if (canMove(state, move)) add(move)
                }
            }
        }
    }

    /**
     * Legal moves that actually change something: they reveal a card, free up a card, empty a
     * column, or join cards of the same suit. Shuffling a run between two equal spots is ignored.
     */
    fun usefulMoves(state: GameState): List<Move> = legalMoves(state).filter { isUseful(state, it) }

    fun isUseful(state: GameState, move: Move): Boolean {
        val source = state.columns[move.from]
        val card = source[move.index]
        val target = state.columns[move.to]
        val below = source.getOrNull(move.index - 1)

        if (target.isEmpty()) {
            // Moving a whole column into an empty one changes nothing.
            if (below == null) return false
            // Splitting a natural same-suit run gains nothing either.
            return !(below.faceUp && below.suit == card.suit && below.rank == card.rank + 1)
        }

        val joinsSuit = target.last().suit == card.suit
        return when {
            below == null -> true // empties a column
            !below.faceUp -> true // reveals a hidden card
            below.rank != card.rank + 1 -> true // frees the card underneath
            below.suit != card.suit && joinsSuit -> true // builds a same-suit run
            else -> false
        }
    }

    /**
     * The best place to send the run starting at [index] in column [from] when it's tapped, or null.
     * Prefers a same-suit card, then any card one rank higher, then an empty column.
     */
    fun bestDestination(state: GameState, from: Int, index: Int): Int? {
        val column = state.columns.getOrNull(from) ?: return null
        if (!isMovableRun(column, index)) return null
        val card = column[index]
        val order = (1 until GameState.COLUMN_COUNT).map { (from + it) % GameState.COLUMN_COUNT }
        val candidates = order.filter { canMove(state, Move(from, index, it)) }
        return candidates.firstOrNull { state.columns[it].lastOrNull()?.suit == card.suit }
            ?: candidates.firstOrNull { state.columns[it].isNotEmpty() }
            ?: candidates.firstOrNull().takeIf { index > 0 }
    }

    enum class Outlook {
        /** There is still at least one useful move. */
        PLAYABLE,

        /** No useful moves, but the stock can still be dealt (once columns are filled). */
        DEAL_NEEDED,

        /** No useful moves and nothing left to deal. */
        STUCK,

        WON,
    }

    fun outlook(state: GameState): Outlook = when {
        state.isWon -> Outlook.WON
        usefulMoves(state).isNotEmpty() -> Outlook.PLAYABLE
        state.stock.isNotEmpty() -> Outlook.DEAL_NEEDED
        else -> Outlook.STUCK
    }

    private fun flipTop(column: MutableList<Card>) {
        if (column.isNotEmpty() && !column.last().faceUp) {
            column[column.lastIndex] = column.last().copy(faceUp = true)
        }
    }

    /** Removes a completed King-to-Ace run from the top of [column], if there is one. */
    private fun collectRun(column: MutableList<Card>, foundations: MutableList<List<Card>>) {
        if (column.size < 13) return
        val start = column.size - 13
        if (column[start].rank != 13 || !isMovableRun(column, start)) return
        val run = column.subList(start, column.size).toList()
        repeat(13) { column.removeAt(column.lastIndex) }
        foundations.add(run)
        flipTop(column)
    }
}
