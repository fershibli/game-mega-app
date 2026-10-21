package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.solitaire.TCard
import io.github.fershibli.megagames.games.spider.Spider
import io.github.fershibli.megagames.games.spider.SpiderMove
import io.github.fershibli.megagames.games.spider.SpiderRules
import io.github.fershibli.megagames.games.spider.SpiderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpiderTest {
    private fun up(suit: Suit, rank: Int, deck: Int = 0) = TCard(Card(suit, rank, deck), true)
    private fun down(suit: Suit, rank: Int, deck: Int = 0) = TCard(Card(suit, rank, deck), false)

    /** A table with the given columns (the others hold one face-up card each so dealing is allowed). */
    private fun table(vararg cols: List<TCard>, stock: List<Card> = emptyList()): SpiderState {
        val filler = (cols.size until Spider.COLUMNS).map { listOf(up(Suit.DIAMONDS, 13, it)) }
        return SpiderState(4, stock, cols.toList() + filler)
    }

    @Test
    fun everyVariantDealsTwoDecksInTheClassicShape() {
        for (variant in GameId.SPIDER.variants) {
            val suits = SpiderRules.suitsOf(variant.key)
            val s = Spider.deal(suits, Random(11))
            val all = s.stock + s.tableau.flatten().map { it.card }
            assertEquals(104, all.toSet().size)
            assertEquals(suits, all.map { it.suit }.toSet().size)
            assertEquals(listOf(6, 6, 6, 6, 5, 5, 5, 5, 5, 5), s.tableau.map { it.size })
            assertTrue(s.tableau.all { col -> col.last().faceUp && col.dropLast(1).none { it.faceUp } })
            assertEquals(50, s.stock.size)
            assertEquals(5, s.dealsLeft)
            // Every rank appears eight times.
            assertTrue(all.groupBy { it.rank }.values.all { it.size == 8 })
        }
        assertEquals(listOf(1, 2, 4), listOf("one", "two", "four").map { SpiderRules.suitsOf(it) })
    }

    @Test
    fun onlyRunsOfOneSuitMoveButTheyLandOnAnySuit() {
        val s = table(
            listOf(down(Suit.CLUBS, 2), up(Suit.SPADES, 9), up(Suit.SPADES, 8), up(Suit.HEARTS, 7)),
            listOf(up(Suit.HEARTS, 9)),
            listOf(up(Suit.CLUBS, 8)),
        )
        assertFalse(Spider.canPickUp(s, 0, 1)) // 9♠ 8♠ 7♥ is not one suit
        assertTrue(Spider.canPickUp(s, 0, 3))
        assertFalse(Spider.canPickUp(s, 0, 0)) // face down
        // 7♥ on 8♣: another suit is fine for building.
        val after = Spider.apply(s, SpiderMove(0, 3, 2))!!
        assertEquals(Card(Suit.HEARTS, 7), after.tableau[2].last().card)
        // Now 9♠ 8♠ moves together and the face-down card turns over.
        val moved = Spider.apply(after, SpiderMove(0, 1, 1))
        assertNull(moved) // 9♠ cannot go on 9♥
        val s2 = after.copy(tableau = after.tableau.toMutableList().also { it[1] = listOf(up(Suit.HEARTS, 10)) })
        val done = Spider.apply(s2, SpiderMove(0, 1, 1))!!
        assertEquals(listOf(10, 9, 8), done.tableau[1].map { it.card.rank })
        assertTrue(done.tableau[0].single().faceUp)
        assertEquals(after.moves + 1, done.moves)
    }

    @Test
    fun aCompleteRunLeavesTheTableAndUncoversTheCardBelow() {
        val run = (13 downTo 2).map { up(Suit.SPADES, it) }
        val s = table(listOf(down(Suit.HEARTS, 5)) + run, listOf(up(Suit.SPADES, 1)))
        val after = Spider.apply(s, SpiderMove(1, 0, 0))!!
        assertEquals(listOf(Suit.SPADES), after.done)
        assertTrue(after.tableau[0].single().faceUp)
        assertTrue(after.tableau[1].isEmpty())
        // Moving to finish a run is what the tap and the hint choose first.
        assertEquals(SpiderMove(1, 0, 0), Spider.destinations(s, 1, 0).first())
        assertEquals(SpiderMove(1, 0, 0), Spider.hint(s))
    }

    @Test
    fun dealingNeedsEveryColumnFilled() {
        val stock = (1..10).map { Card(Suit.CLUBS, it) }
        val full = table(listOf(up(Suit.SPADES, 4)), stock = stock)
        val dealt = Spider.dealRow(full)!!
        assertTrue(dealt.stock.isEmpty())
        assertTrue(dealt.tableau.all { it.size == 2 && it.last().faceUp })
        val withHole = full.copy(tableau = full.tableau.toMutableList().also { it[3] = emptyList() })
        assertFalse(withHole.canDeal)
        assertNull(Spider.dealRow(withHole))
        // The hint then suggests filling the empty column.
        val hint = Spider.hint(withHole.copy(tableau = withHole.tableau.toMutableList().also { it[0] = listOf(up(Suit.HEARTS, 3), up(Suit.SPADES, 9)) }))
        assertNotNull(hint)
        assertEquals(3, hint!!.to)
    }

    @Test
    fun tapPrefersTheSameSuitAndNeverMovesAWholeColumnToAnEmptyOne() {
        val s = table(
            listOf(up(Suit.HEARTS, 6)),
            listOf(up(Suit.CLUBS, 7)),
            listOf(up(Suit.HEARTS, 7)),
            emptyList(),
        )
        val options = Spider.destinations(s, 0, 0)
        assertEquals(listOf(2, 1), options.map { it.to })
        val s2 = table(listOf(down(Suit.CLUBS, 1), up(Suit.HEARTS, 6)), emptyList())
        assertEquals(listOf(1), Spider.destinations(s2, 0, 1).map { it.to })
    }

    @Test
    fun hintPrefersUncoveringAndIgnoresPointlessMoves() {
        val s = table(
            listOf(down(Suit.CLUBS, 1), up(Suit.HEARTS, 6)),
            listOf(up(Suit.SPADES, 8), up(Suit.HEARTS, 7)),
            listOf(up(Suit.CLUBS, 7)),
            listOf(up(Suit.DIAMONDS, 8)),
        )
        // 6♥ onto either 7: uncovers a card. 7♥ from 8♠ to 8♦ would change nothing.
        val hint = Spider.hint(s)!!
        assertEquals(0, hint.from)
        val pointless = table(listOf(up(Suit.SPADES, 8), up(Suit.HEARTS, 7)), listOf(up(Suit.DIAMONDS, 8)))
        assertNull(Spider.hint(pointless))
        assertTrue(Spider.isStuck(pointless))
        assertFalse(Spider.isStuck(pointless.copy(stock = (1..10).map { Card(Suit.CLUBS, it) })))
    }

    @Test
    fun followingTheHintKeepsEveryCardAndOftenWinsWithOneSuit() {
        var wins = 0
        for (seed in 1..12L) {
            var s = Spider.deal(1, Random(seed))
            var steps = 0
            while (!s.won && steps < 1500) {
                val m = Spider.hint(s)
                s = (if (m != null) Spider.apply(s, m) else Spider.dealRow(s)) ?: break
                assertEquals(104, s.stock.size + s.tableau.sumOf { it.size } + s.done.size * 13)
                steps++
            }
            if (s.won) wins++
        }
        assertTrue("hint wins $wins of 12", wins >= 1)
    }

    @Test
    fun stateSurvivesSaving() {
        val s = Spider.dealRow(Spider.deal(2, Random(5)))!!
        val back = AppJson.decodeFromJsonElement(SpiderState.serializer(), AppJson.encodeToJsonElement(SpiderState.serializer(), s))
        assertEquals(s, back)
    }

    @Test
    fun hintIsFast() {
        val s = Spider.deal(4, Random(3))
        val t0 = System.nanoTime()
        repeat(20) { Spider.hint(s) }
        assertTrue((System.nanoTime() - t0) / 20 < 100_000_000)
    }
}
