package spider.game

import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpiderTest {
    private var nextId = 1000
    private fun up(rank: Int, suit: Suit = Suit.SPADES) = Card(nextId++, rank, suit, faceUp = true)
    private fun down(rank: Int, suit: Suit = Suit.SPADES) = Card(nextId++, rank, suit, faceUp = false)

    /** A state with the given columns (padded to ten with single cards) and an optional stock. */
    private fun state(vararg cols: List<Card>, stock: List<Card> = emptyList(), pad: Boolean = true): GameState {
        val columns = cols.toMutableList()
        while (columns.size < 10) columns.add(if (pad) listOf(up(1, Suit.HEARTS)) else emptyList())
        return GameState(columns, stock, emptyList())
    }

    @Test
    fun newGameDealsTheStandardLayout() {
        val game = Spider.newGame(Random(1))
        assertEquals(listOf(6, 6, 6, 6, 5, 5, 5, 5, 5, 5), game.columns.map { it.size })
        assertEquals(50, game.stock.size)
        assertEquals(5, game.dealsLeft)
        assertEquals(500, game.score)
        game.columns.forEach { col ->
            assertTrue(col.last().faceUp)
            assertTrue(col.dropLast(1).none { it.faceUp })
        }
        val all = game.columns.flatten() + game.stock
        assertEquals(104, all.map { it.id }.toSet().size)
        Suit.entries.forEach { suit ->
            (1..13).forEach { rank -> assertEquals(4, all.count { it.suit == suit && it.rank == rank }) }
        }
    }

    @Test
    fun movesOntoOneRankHigherOfAnySuit() {
        val s = state(listOf(down(3), up(8, Suit.HEARTS)), listOf(up(9)))
        val after = Spider.move(s, Move(0, 1, 1))
        assertNotNull(after)
        assertEquals(listOf(9, 8), after.columns[1].map { it.rank })
        assertTrue(after.columns[0].last().faceUp, "hidden card is turned over")
        assertEquals(1, after.moves)
        assertEquals(499, after.score)
    }

    @Test
    fun rejectsWrongRankAndMixedSuitRuns() {
        val s = state(listOf(up(8), up(7, Suit.HEARTS)), listOf(up(10)), listOf(up(9)))
        assertNull(Spider.move(s, Move(0, 1, 1)), "7 can't go on 10")
        assertNull(Spider.move(s, Move(0, 0, 2)), "8-7 is mixed suit so can't move together")
    }

    @Test
    fun anythingMovesToAnEmptyColumn() {
        val s = state(listOf(down(2), up(5)), emptyList())
        val after = Spider.move(s, Move(0, 1, 1))
        assertNotNull(after)
        assertEquals(5, after.columns[1].single().rank)
    }

    @Test
    fun completedRunIsCollectedAndScores() {
        val run = (13 downTo 2).map { up(it) }
        val s = state(listOf(down(4)) + run, listOf(up(5, Suit.HEARTS), up(1)))
        val after = Spider.move(s, Move(1, 1, 0))
        assertNotNull(after)
        assertEquals(1, after.foundations.size)
        assertEquals(1, after.columns[0].size)
        assertTrue(after.columns[0].single().faceUp)
        assertEquals(500 - 1 + 100, after.score)
    }

    @Test
    fun dealNeedsEveryColumnFilled() {
        val stock = List(10) { down(it % 13 + 1) }
        val blocked = state(listOf(up(3)), stock = stock, pad = false)
        assertFalse(Spider.canDeal(blocked))
        val ok = state(listOf(up(3)), stock = stock)
        val dealt = Spider.deal(ok)
        assertNotNull(dealt)
        assertEquals(0, dealt.stock.size)
        assertTrue(dealt.columns.all { it.size == 2 && it.last().faceUp })
    }

    @Test
    fun bestDestinationPrefersSameSuit() {
        val s = state(listOf(up(6, Suit.HEARTS)), listOf(up(7)), listOf(up(7, Suit.HEARTS)), emptyList(), pad = false)
        assertEquals(2, Spider.bestDestination(s, 0, 0))
        val noSameSuit = state(listOf(up(6, Suit.HEARTS)), listOf(up(7)), emptyList(), pad = false)
        assertEquals(1, Spider.bestDestination(noSameSuit, 0, 0))
        val onlyEmpty = state(listOf(up(6, Suit.HEARTS)), emptyList(), pad = false)
        assertNull(Spider.bestDestination(onlyEmpty, 0, 0), "moving a whole column to an empty one is pointless")
    }

    @Test
    fun outlookSpotsWhenStuck() {
        val stuck = GameState(
            columns = List(10) { listOf(up(if (it % 2 == 0) 2 else 13)) },
            stock = emptyList(),
            foundations = emptyList(),
        )
        assertEquals(Spider.Outlook.STUCK, Spider.outlook(stuck))
        assertEquals(Spider.Outlook.DEAL_NEEDED, Spider.outlook(stuck.copy(stock = List(10) { down(5) })))
        val playable = stuck.copy(columns = listOf(listOf(down(4), up(2))) + List(9) { listOf(up(3)) })
        assertEquals(Spider.Outlook.PLAYABLE, Spider.outlook(playable))
    }

    @Test
    fun shufflingARunBetweenEqualCardsIsNotUseful() {
        val s = state(listOf(up(9, Suit.HEARTS), up(8)), listOf(up(9)), listOf(up(1)))
        // 8♠ on 9♥ moving to 9♠ joins its suit: useful.
        assertTrue(Spider.isUseful(s, Move(0, 1, 1)))
        val back = Spider.move(s, Move(0, 1, 1))!!
        // 8♠ on 9♠ moving back to 9♥ breaks a suit run: not useful.
        assertFalse(Spider.isUseful(back, Move(1, 1, 0)))
    }

    @Test
    fun undoRestoresPositionButCountsAsAMove() {
        var session = Session(state(listOf(down(3), up(8)), listOf(up(9))))
        session = session.advance(Spider.move(session.state, Move(0, 1, 1))!!)
        assertEquals(499, session.state.score)
        session = session.undo()
        assertEquals(8, session.state.columns[0].last().rank)
        assertFalse(session.state.columns[0].first().faceUp)
        assertEquals(2, session.state.moves)
        assertEquals(498, session.state.score)
        assertFalse(session.canUndo)
    }

    @Test
    fun sessionRoundTripsThroughText() {
        var session = Session.new(Random(7))
        val move = Spider.legalMoves(session.state).firstOrNull()
        if (move != null) session = session.advance(Spider.move(session.state, move)!!)
        session = session.advance(Spider.deal(session.state)!!).copy(elapsedMs = 12345)
        val decoded = Codec.decodeSession(Codec.encodeSession(session))
        assertEquals(session, decoded)
        assertNull(Codec.decodeSession("rubbish"))
    }

    @Test
    fun statsTrackBestTimesAndHighScores() {
        val d1 = LocalDate.of(2026, 1, 1)
        val d2 = LocalDate.of(2026, 2, 1)
        var stats = Stats().recordStarted().recordStarted().recordStarted()
        val first = stats.recordWin(ScoreEntry(900, 300_000, 200, d1))
        assertTrue(first.newBestTime)
        assertEquals(1, first.scoreRank)
        stats = first.stats
        val slower = stats.recordWin(ScoreEntry(950, 400_000, 150, d2))
        assertFalse(slower.newBestTime)
        assertEquals(1, slower.scoreRank)
        stats = slower.stats
        val faster = stats.recordWin(ScoreEntry(800, 200_000, 300, d2))
        assertTrue(faster.newBestTime)
        assertEquals(3, faster.scoreRank)
        stats = faster.stats
        assertEquals(listOf(300_000L, 200_000L), stats.bestTimes.map { it.timeMs })
        assertEquals(d2, stats.bestTime!!.date)
        assertEquals(3, stats.won)
        assertEquals(100, stats.winRate)
        assertEquals(stats, Codec.decodeStats(Codec.encodeStats(stats)))
    }

    @Test
    fun highScoreBoardKeepsTopTen() {
        var stats = Stats()
        repeat(12) { stats = stats.recordWin(ScoreEntry(700 + it, 1000L, 100, LocalDate.of(2026, 1, 1))).stats }
        assertEquals(10, stats.highScores.size)
        assertEquals(711, stats.highScores.first().score)
        val low = stats.recordWin(ScoreEntry(100, 1L, 1, LocalDate.of(2026, 1, 1)))
        assertNull(low.scoreRank)
        assertTrue(low.newBestTime)
    }

    @Test
    fun randomPlayNeverBreaksTheRules() {
        repeat(30) { seed ->
            val random = Random(seed)
            var session = Session.new(random)
            repeat(400) {
                val moves = Spider.usefulMoves(session.state)
                session = when {
                    moves.isNotEmpty() && random.nextInt(5) != 0 ->
                        session.advance(Spider.move(session.state, moves.random(random))!!)
                    Spider.canDeal(session.state) -> session.advance(Spider.deal(session.state)!!)
                    session.canUndo && random.nextBoolean() -> session.undo()
                    else -> return@repeat
                }
                val s = session.state
                assertEquals(104, s.columns.sumOf { it.size } + s.stock.size + s.foundations.sumOf { it.size })
                assertEquals(104, (s.columns.flatten() + s.stock + s.foundations.flatten()).map { it.id }.toSet().size)
                s.columns.forEach { col -> if (col.isNotEmpty()) assertTrue(col.last().faceUp) }
            }
        }
    }
}
