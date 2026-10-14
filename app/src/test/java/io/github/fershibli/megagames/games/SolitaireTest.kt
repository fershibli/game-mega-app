package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.solitaire.Move
import io.github.fershibli.megagames.games.solitaire.Pile
import io.github.fershibli.megagames.games.solitaire.Solitaire
import io.github.fershibli.megagames.games.solitaire.SolitaireState
import io.github.fershibli.megagames.games.solitaire.TCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SolitaireTest {
    @Test
    fun dealHas52UniqueCardsInTheRightShape() {
        val s = SolitaireState.deal(Difficulty.EASY, Random(3))
        val all = s.stock + s.tableau.flatten().map { it.card }
        assertEquals(52, all.toSet().size)
        assertEquals((1..7).toList(), s.tableau.map { it.size })
        assertTrue(s.tableau.all { col -> col.last().faceUp && col.dropLast(1).none { it.faceUp } })
        assertEquals(24, s.stock.size)
    }

    @Test
    fun drawCountsAndPassesFollowDifficulty() {
        assertEquals(1, SolitaireState.deal(Difficulty.EASY, Random(1)).drawCount)
        assertEquals(3, SolitaireState.deal(Difficulty.MEDIUM, Random(1)).drawCount)
        val hard = SolitaireState.deal(Difficulty.HARD, Random(1))
        var s = hard
        repeat(8) { s = Solitaire.tapStock(s)!! } // 24 cards, 3 at a time
        assertTrue(s.stock.isEmpty())
        s = Solitaire.tapStock(s)!! // recycle 1
        assertEquals(1, s.passesLeft)
    }

    @Test
    fun movesFollowColorAndRank() {
        val s = SolitaireState(
            stock = emptyList(), waste = listOf(Card(Suit.HEARTS, 6)),
            foundations = List(4) { emptyList() },
            tableau = listOf(listOf(TCard(Card(Suit.SPADES, 7), true)), listOf(TCard(Card(Suit.DIAMONDS, 7), true))) + List(5) { emptyList() },
            drawCount = 1, passesLeft = -1,
        )
        assertNotNull(Solitaire.apply(s, Move(Pile.Waste, 0, Pile.Column(0))))
        assertNull(Solitaire.apply(s, Move(Pile.Waste, 0, Pile.Column(1))))
        assertNull(Solitaire.apply(s, Move(Pile.Waste, 0, Pile.Column(2)))) // only kings on empty columns
        val best = Solitaire.bestMove(s, Pile.Waste, 0)
        assertEquals(Pile.Column(0), best?.to)
    }

    @Test
    fun acesGoHomeAndUncoveredCardsFlip() {
        val s = SolitaireState(
            stock = emptyList(), waste = emptyList(), foundations = List(4) { emptyList() },
            tableau = listOf(listOf(TCard(Card(Suit.CLUBS, 9), false), TCard(Card(Suit.CLUBS, 1), true))) + List(6) { emptyList() },
            drawCount = 1, passesLeft = -1,
        )
        val m = Solitaire.bestMove(s, Pile.Column(0), 1)!!
        assertTrue(m.to is Pile.Foundation)
        val after = Solitaire.apply(s, m)!!
        assertTrue(after.tableau[0].single().faceUp)
        assertFalse(after.won)
    }
}
