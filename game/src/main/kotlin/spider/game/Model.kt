package spider.game

enum class Suit(val symbol: String, val isRed: Boolean, val code: Char) {
    SPADES("♠", false, 'S'),
    HEARTS("♥", true, 'H');

    companion object {
        fun fromCode(c: Char): Suit = entries.first { it.code == c }
    }
}

/** A single card. [id] is unique within a game and stays with the card, so the UI can animate it. */
data class Card(val id: Int, val rank: Int, val suit: Suit, val faceUp: Boolean = false) {
    val rankLabel: String
        get() = when (rank) {
            1 -> "A"
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            else -> rank.toString()
        }
}

/**
 * One position in a game of Spider. Time is kept outside this class so undo never rewinds the clock.
 *
 * @param columns the ten tableau columns, bottom card first.
 * @param stock cards still to be dealt; the last ten are dealt next.
 * @param foundations completed King-to-Ace runs, in the order they were completed.
 * @param moves every action taken so far, including deals and undos.
 */
data class GameState(
    val columns: List<List<Card>>,
    val stock: List<Card>,
    val foundations: List<List<Card>>,
    val moves: Int = 0,
) {
    val score: Int get() = START_SCORE - moves + RUN_BONUS * foundations.size
    val isWon: Boolean get() = foundations.size == RUNS_TO_WIN
    val dealsLeft: Int get() = stock.size / COLUMN_COUNT

    companion object {
        const val COLUMN_COUNT = 10
        const val RUNS_TO_WIN = 8
        const val START_SCORE = 500
        const val RUN_BONUS = 100
    }
}

/** Moving the cards in column [from] starting at [index] onto column [to]. */
data class Move(val from: Int, val index: Int, val to: Int)
