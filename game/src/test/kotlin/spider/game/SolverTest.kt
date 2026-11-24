package spider.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SolverTest {
    /** Plays [actions] from [start] and returns the final position. */
    private fun replay(start: GameState, actions: List<Action>): GameState =
        actions.fold(start) { state, action ->
            when (action) {
                is Action.MoveCards -> assertNotNull(Spider.move(state, action.move), "illegal move $action")
                Action.Deal -> assertNotNull(Spider.deal(state), "illegal deal")
            }
        }

    @Test
    fun solutionsReallyWin() {
        var solved = 0
        for (seed in 0 until 12) {
            val game = Spider.newGame(Random(seed))
            val solution = Solver().solve(game) ?: continue
            solved++
            assertTrue(replay(game, solution).isWon, "solution for seed $seed doesn't win")
        }
        assertTrue(solved >= 4, "expected most deals to be solved, got $solved of 12")
    }

    @Test
    fun winnableDealIsProvenWinnable() {
        val game = assertNotNull(Solver.winnableDeal(Random(42)))
        val solution = assertNotNull(Solver().solve(game))
        assertTrue(replay(game, solution).isWon)
    }

    @Test
    fun givesUpWithinTheLimit() {
        val solver = Solver(nodeLimit = 50)
        val result = solver.solve(Spider.newGame(Random(3)))
        assertTrue(solver.nodesSearched <= 51)
        if (result != null) assertTrue(replay(Spider.newGame(Random(3)), result).isWon)
    }

    @Test
    fun cancellationStopsTheSearch() {
        assertNull(Solver.winnableDeal(Random(1)) { true })
    }
}
