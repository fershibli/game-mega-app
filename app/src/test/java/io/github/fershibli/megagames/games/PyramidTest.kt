package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.pyramid.PyPick
import io.github.fershibli.megagames.games.pyramid.Pyramid
import io.github.fershibli.megagames.games.pyramid.PyramidSolver
import io.github.fershibli.megagames.games.pyramid.PyramidState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PyramidTest {
    /** A pyramid with only the given cards left (by position), and the given stock. */
    private fun table(cards: Map<Int, Card>, stock: List<Card> = emptyList(), waste: List<Card> = emptyList(), passes: Int = -1) =
        PyramidState(List(Pyramid.SIZE) { cards[it] }, stock, waste, passes)

    @Test
    fun dealShapeAndPassesByLevel() {
        val expected = mapOf(Difficulty.EASY to -1, Difficulty.MEDIUM to 2, Difficulty.HARD to 0)
        for ((d, passes) in expected) {
            val s = Pyramid.deal(d, Random(d.ordinal + 1L))
            assertEquals(28, s.pyramid.filterNotNull().size)
            assertEquals(24, s.stock.size)
            assertEquals(52, (s.pyramid.filterNotNull() + s.stock).toSet().size)
            assertEquals(passes, s.passesLeft)
            assertEquals(7, Pyramid.exposedIndices(s).size)
            // Every deal can be cleared when the stock may be turned without limit.
            assertTrue(PyramidSolver.solve(s) is PyramidSolver.Result.Solved)
        }
        assertEquals(listOf(0, 1, 1, 2, 2, 2, 6), listOf(0, 1, 2, 3, 4, 5, 27).map { Pyramid.row(it) })
        assertEquals(27, Pyramid.index(6, 6))
    }

    @Test
    fun pairsAddUpToThirteenAndKingsGoAlone() {
        val s = table(
            mapOf(
                Pyramid.index(5, 0) to Card(Suit.HEARTS, 4),
                Pyramid.index(6, 0) to Card(Suit.SPADES, 12),
                Pyramid.index(6, 1) to Card(Suit.CLUBS, 1),
                Pyramid.index(6, 2) to Card(Suit.HEARTS, 13),
                Pyramid.index(6, 3) to Card(Suit.CLUBS, 5),
            ),
            waste = listOf(Card(Suit.DIAMONDS, 8)),
        )
        val a = PyPick.Pyramid(Pyramid.index(6, 0))
        val b = PyPick.Pyramid(Pyramid.index(6, 1))
        val covered = PyPick.Pyramid(Pyramid.index(5, 0))
        assertNull(Pyramid.cardAt(s, covered))
        assertNull(Pyramid.remove(s, a)) // a Queen does not go alone
        assertNull(Pyramid.remove(s, a, PyPick.Pyramid(Pyramid.index(6, 3))))
        val afterPair = Pyramid.remove(s, a, b)!!
        // The 4 above them is now free.
        assertNotNull(Pyramid.cardAt(afterPair, covered))
        val afterKing = Pyramid.remove(afterPair, PyPick.Pyramid(Pyramid.index(6, 2)))!!
        val last = Pyramid.remove(afterKing, PyPick.Pyramid(Pyramid.index(6, 3)), PyPick.Waste)!!
        assertTrue(last.waste.isEmpty())
        assertFalse(last.won)
        assertTrue(Pyramid.remove(last, covered) == null)
    }

    @Test
    fun stockTurnsAndPassesRunOut() {
        val stock = listOf(Card(Suit.CLUBS, 2), Card(Suit.CLUBS, 3))
        val one = table(mapOf(0 to Card(Suit.SPADES, 7)), stock, passes = 0)
        var s = Pyramid.tapStock(Pyramid.tapStock(one)!!)!!
        assertEquals(listOf(Card(Suit.CLUBS, 3), Card(Suit.CLUBS, 2)), s.waste)
        assertNull(Pyramid.tapStock(s)) // Hard: no second pass
        assertTrue(Pyramid.isStuck(s))
        s = Pyramid.tapStock(Pyramid.tapStock(table(mapOf(0 to Card(Suit.SPADES, 7)), stock, passes = 1))!!)!!
        val turned = Pyramid.tapStock(s)!!
        assertEquals(stock, turned.stock)
        assertEquals(0, turned.passesLeft)
    }

    @Test
    fun stuckWhenNothingCanEverPair() {
        // A 7 alone in the pyramid and no 6 anywhere: turning the stock forever would not help.
        val s = table(mapOf(0 to Card(Suit.SPADES, 7)), listOf(Card(Suit.CLUBS, 2), Card(Suit.CLUBS, 5)))
        assertTrue(Pyramid.isStuck(s))
        val hope = table(mapOf(0 to Card(Suit.SPADES, 7)), listOf(Card(Suit.CLUBS, 6), Card(Suit.CLUBS, 5)))
        assertFalse(Pyramid.isStuck(hope))
        assertEquals(Pyramid.Hint.Stock, Pyramid.hint(hope))
        assertTrue(PyramidSolver.solve(s) == PyramidSolver.Result.Unsolvable)
    }

    @Test
    fun followingTheHintClearsEveryEasyDeal() {
        for (seed in 1..10L) {
            var s = Pyramid.deal(Difficulty.EASY, Random(seed))
            var steps = 0
            while (!s.won && steps < 600) {
                s = when (val h = Pyramid.hint(s)!!) {
                    Pyramid.Hint.Stock -> Pyramid.tapStock(s)!!
                    is Pyramid.Hint.Take -> Pyramid.remove(s, h.picks[0], h.picks.getOrNull(1))!!
                }
                steps++
            }
            assertTrue("deal $seed", s.won)
        }
    }

    @Test
    fun dealingAndHintsAreQuick() {
        val t0 = System.nanoTime()
        repeat(10) { Pyramid.hint(Pyramid.deal(Difficulty.HARD, Random(it.toLong()))) }
        assertTrue((System.nanoTime() - t0) / 10 < 500_000_000)
    }

    @Test
    fun stateSurvivesSaving() {
        val s = Pyramid.tapStock(Pyramid.deal(Difficulty.MEDIUM, Random(2)))!!
        val back = AppJson.decodeFromJsonElement(PyramidState.serializer(), AppJson.encodeToJsonElement(PyramidState.serializer(), s))
        assertEquals(s, back)
    }
}
