package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.freecell.FcMove
import io.github.fershibli.megagames.games.freecell.FcPile
import io.github.fershibli.megagames.games.freecell.FreeCell
import io.github.fershibli.megagames.games.freecell.FreeCellDeals
import io.github.fershibli.megagames.games.freecell.FreeCellSolver
import io.github.fershibli.megagames.games.freecell.FreeCellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FreeCellTest {
    private fun c(suit: Suit, rank: Int) = Card(suit, rank)

    /** Plays [moves] with the game's own rules and returns the final position. */
    private fun replay(start: FreeCellState, moves: List<FcMove>): FreeCellState =
        moves.fold(start) { s, m -> FreeCell.apply(s, m) ?: error("illegal move $m") }

    @Test
    fun dealShapeAndSameSeedSameDeal() {
        val s = FreeCell.deal(1234)
        assertEquals(listOf(7, 7, 7, 7, 6, 6, 6, 6), s.columns.map { it.size })
        assertEquals(52, s.columns.flatten().toSet().size)
        assertTrue(s.cells.all { it == null })
        assertEquals(s, FreeCell.deal(1234))
        assertEquals(1234L, s.seed)
    }

    @Test
    fun cardsStackDownInAlternatingColors() {
        val s = FreeCellState(listOf(listOf(c(Suit.SPADES, 9)), listOf(c(Suit.HEARTS, 8)), listOf(c(Suit.CLUBS, 8))) + List(5) { emptyList() })
        assertNotNull(FreeCell.apply(s, FcMove(FcPile.Column(1), 0, FcPile.Column(0))))
        assertNull(FreeCell.apply(s, FcMove(FcPile.Column(2), 0, FcPile.Column(0))))
        // Into a free cell and back.
        val parked = FreeCell.apply(s, FcMove(FcPile.Column(2), 0, FcPile.Cell(0)))!!
        assertEquals(c(Suit.CLUBS, 8), parked.cells[0])
        assertEquals(3, parked.freeCells)
        assertNull(FreeCell.apply(parked, FcMove(FcPile.Column(1), 0, FcPile.Cell(0))))
        // Foundations take their own suit from the Ace up.
        val ace = FreeCellState(listOf(listOf(c(Suit.DIAMONDS, 1)), listOf(c(Suit.DIAMONDS, 2))) + List(6) { emptyList() })
        assertNull(FreeCell.apply(ace, FcMove(FcPile.Column(0), 0, FcPile.Foundation(Suit.HEARTS.ordinal))))
        val home = FreeCell.apply(ace, FcMove(FcPile.Column(0), 0, FcPile.Foundation(Suit.DIAMONDS.ordinal)))!!
        assertEquals(1, home.foundations[Suit.DIAMONDS.ordinal])
        assertEquals(FcPile.Foundation(Suit.DIAMONDS.ordinal), FreeCell.destinations(home, FcPile.Column(1), 0).first().to)
    }

    @Test
    fun runsMoveOnlyWithEnoughRoom() {
        val run = listOf(c(Suit.SPADES, 7), c(Suit.HEARTS, 6), c(Suit.CLUBS, 5), c(Suit.DIAMONDS, 4))
        val filler = (1..6).map { listOf(c(Suit.CLUBS, 13 - it)) } // no empty column
        val base = FreeCellState(listOf(run, listOf(c(Suit.DIAMONDS, 8))) + filler)
        // Four empty cells: (4 + 1) × 2^0 = 5 cards may move.
        assertEquals(5, FreeCell.maxRun(base, false))
        assertNotNull(FreeCell.apply(base, FcMove(FcPile.Column(0), 0, FcPile.Column(1))))
        // With three cells taken, only two cards at a time.
        val busy = base.copy(cells = listOf(c(Suit.HEARTS, 13), c(Suit.HEARTS, 12), c(Suit.HEARTS, 11), null))
        assertEquals(2, FreeCell.maxRun(busy, false))
        assertNull(FreeCell.apply(busy, FcMove(FcPile.Column(0), 0, FcPile.Column(1))))
        assertTrue(FreeCell.destinations(busy, FcPile.Column(0), 0).isEmpty())
        // An empty column doubles it, but not when it is the destination.
        val withHole = busy.copy(columns = busy.columns.toMutableList().also { it[7] = emptyList() })
        assertEquals(4, FreeCell.maxRun(withHole, false))
        assertEquals(2, FreeCell.maxRun(withHole, true))
        assertNotNull(FreeCell.apply(withHole, FcMove(FcPile.Column(0), 0, FcPile.Column(1))))
        // Cards out of order never move together.
        val mixed = base.copy(columns = base.columns.toMutableList().also { it[0] = listOf(c(Suit.SPADES, 7), c(Suit.CLUBS, 6)) })
        assertNull(FreeCell.pickUp(mixed, FcPile.Column(0), 0))
    }

    @Test
    fun solverSolutionsFollowTheRules() {
        for (seed in 1..8L) {
            val s = FreeCell.deal(seed)
            val r = FreeCellSolver.solve(s)
            assertTrue("deal $seed", r is FreeCellSolver.Result.Solved)
            assertTrue(replay(s, (r as FreeCellSolver.Result.Solved).moves).won)
        }
    }

    @Test
    fun everyLevelDealsAWinnableGameOfThatLevel() {
        for (d in Difficulty.entries) {
            repeat(3) { k ->
                val s = FreeCellDeals.deal(d, Random(100L * d.ordinal + k))
                assertEquals("level of a $d deal", d, FreeCellDeals.grade(s))
                val r = FreeCellSolver.solve(s) as FreeCellSolver.Result.Solved
                assertTrue(replay(s, r.moves).won)
            }
        }
        // Easy deals are won with only two free cells: with the four, they are comfortable.
        val easy = FreeCellDeals.deal(Difficulty.EASY, Random(9))
        assertTrue(FreeCellSolver.solve(easy, cellLimit = 2, maxNodes = 3_000) is FreeCellSolver.Result.Solved)
    }

    @Test
    fun followingTheHintWinsAndIsQuick() {
        var s = FreeCellDeals.deal(Difficulty.HARD, Random(4))
        var slowest = 0L
        var steps = 0
        while (!s.won && steps < 400) {
            val t0 = System.nanoTime()
            val m = FreeCellDeals.hint(s)!!
            slowest = maxOf(slowest, System.nanoTime() - t0)
            s = FreeCell.apply(s, m)!!
            steps++
        }
        assertTrue(s.won)
        assertTrue("slowest hint ${slowest / 1_000_000} ms", slowest < 1_000_000_000)
    }

    @Test
    fun aBlockedTableIsLost() {
        // Cells full of Kings, every column topped by a red card that fits nowhere.
        val tops = listOf(c(Suit.HEARTS, 2), c(Suit.DIAMONDS, 2), c(Suit.HEARTS, 3), c(Suit.DIAMONDS, 3), c(Suit.HEARTS, 4), c(Suit.DIAMONDS, 4), c(Suit.HEARTS, 5), c(Suit.DIAMONDS, 5))
        val kings = Suit.entries.map { c(it, 13) }
        val rest = Suit.entries.flatMap { s -> (1..13).map { c(s, it) } } - tops.toSet() - kings.toSet()
        val columns = (0 until 8).map { i -> rest.filterIndexed { k, _ -> k % 8 == i } + tops[i] }
        val s = FreeCellState(columns, kings)
        assertFalse(FreeCell.anyMove(s))
        assertTrue(FreeCellDeals.isLost(s))
        assertFalse(FreeCellDeals.isLost(FreeCell.deal(7)))
    }

    @Test
    fun sortedColumnsFinishAutomatically() {
        val columns = Suit.entries.map { s -> (13 downTo 1).map { c(s, it) } } + List(4) { emptyList<Card>() }
        var s = FreeCellState(columns)
        assertTrue(s.canAutoFinish)
        var steps = 0
        while (!s.won) { s = FreeCell.autoStep(s)!!; steps++ }
        assertEquals(52, steps)
        assertFalse(FreeCellState(listOf(listOf(c(Suit.SPADES, 2), c(Suit.HEARTS, 5))) + List(7) { emptyList() }).canAutoFinish)
    }

    @Test
    fun stateSurvivesSaving() {
        val s = FreeCell.apply(FreeCell.deal(3), FcMove(FcPile.Column(0), 6, FcPile.Cell(2)))!!
        val back = AppJson.decodeFromJsonElement(FreeCellState.serializer(), AppJson.encodeToJsonElement(FreeCellState.serializer(), s))
        assertEquals(s, back)
    }
}
