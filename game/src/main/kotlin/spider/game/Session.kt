package spider.game

/**
 * A game in progress: the current position, every earlier position (for unlimited undo),
 * and the time played so far.
 */
data class Session(
    val state: GameState,
    val history: List<GameState> = emptyList(),
    val elapsedMs: Long = 0,
) {
    val canUndo: Boolean get() = history.isNotEmpty() && !state.isWon
    val started: Boolean get() = state.moves > 0

    /** Records [next] as the new position, keeping the current one for undo. */
    fun advance(next: GameState): Session = copy(state = next, history = history + state)

    /** Goes back one position. Undo counts as a move, so it costs a point like any other. */
    fun undo(): Session {
        if (!canUndo) return this
        val previous = history.last()
        return copy(
            state = previous.copy(moves = state.moves + 1),
            history = history.dropLast(1),
        )
    }

    companion object {
        fun new(random: kotlin.random.Random = kotlin.random.Random.Default) = Session(Spider.newGame(random))
    }
}
