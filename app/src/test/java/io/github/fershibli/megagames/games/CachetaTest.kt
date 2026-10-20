package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cacheta.Cacheta
import io.github.fershibli.megagames.games.cacheta.CachetaAi
import io.github.fershibli.megagames.games.cacheta.CachetaRules
import io.github.fershibli.megagames.games.cacheta.CachetaState
import io.github.fershibli.megagames.games.cacheta.CachetaTable
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CachetaTest {
    private fun h(r: Int, d: Int = 0) = Card(Suit.HEARTS, r, d)
    private fun s(r: Int, d: Int = 0) = Card(Suit.SPADES, r, d)
    private fun c(r: Int, d: Int = 0) = Card(Suit.CLUBS, r, d)
    private fun d(r: Int, d: Int = 0) = Card(Suit.DIAMONDS, r, d)

    private val classic = CachetaTable(CachetaRules.CLASSIC, null)
    private val pife = CachetaTable(CachetaRules.PIFE, null)

    @Test
    fun triosNeedDifferentSuits() {
        assertTrue(classic.isGroup(listOf(h(8), s(8), c(8))))
        assertTrue(classic.isGroup(listOf(h(8), s(8), c(8), d(8))))
        assertFalse(classic.isGroup(listOf(h(8), h(8, 1), c(8))))
        assertFalse(classic.isGroup(listOf(h(8), s(8))))
        assertFalse(classic.isGroup(listOf(h(8), s(8), c(9))))
    }

    @Test
    fun sequencesOfTheSameSuit() {
        assertTrue(classic.isGroup(listOf(h(5), h(7), h(6))))
        assertTrue(classic.isGroup((3..11).map { s(it) }))
        assertFalse(classic.isGroup(listOf(h(5), h(6), s(7))))
        assertFalse(classic.isGroup(listOf(h(5), h(6), h(8))))
        // The ace is low or high...
        assertTrue(classic.isGroup(listOf(h(1), h(2), h(3))))
        assertTrue(classic.isGroup(listOf(h(12), h(13), h(1))))
        // ...but only Pife lets a sequence turn the corner.
        assertFalse(classic.isGroup(listOf(h(13), h(1), h(2))))
        assertTrue(pife.isGroup(listOf(h(13), h(1), h(2))))
        assertTrue(pife.isGroup(listOf(h(12), h(13), h(1), h(2), h(3))))
    }

    @Test
    fun theViraChoosesTheWildCard() {
        val wild = CachetaTable(CachetaRules.WILD, h(4))
        assertTrue(wild.isWild(h(5)) && wild.isWild(h(5, 1)))
        assertFalse(wild.isWild(s(5)) || wild.isWild(h(4)))
        // The 5♥ fills a gap in a sequence or completes a trio...
        assertTrue(wild.isGroup(listOf(s(7), h(5), s(9))))
        assertTrue(wild.isGroup(listOf(c(8), d(8), h(5))))
        // ...which is not allowed without wild cards.
        assertFalse(classic.isGroup(listOf(s(7), h(5), s(9))))
        // A king turned up makes the ace of its suit wild.
        assertTrue(CachetaTable(CachetaRules.WILD, c(13)).isWild(c(1)))
    }

    @Test
    fun goingOutWithNineOrTenCards() {
        val nine = listOf(h(3), h(4), h(5), s(9), c(9), d(9), c(11), c(12), c(13))
        assertEquals(3, classic.partition(nine)!!.size)
        // Ten cards: one is thrown away to go out.
        val out = classic.goOut(nine + d(2))!!
        assertEquals(d(2), out.discard)
        // Ten cards that all fit: "batida de dez".
        val ten = classic.goOut(nine + h(6))!!
        assertNull(ten.discard)
        // Not ready.
        assertNull(classic.goOut(listOf(h(3), h(4), h(6), s(9), c(9), d(8), c(11), c(12), c(13), d(2))))
        // The card just taken from the pile cannot be the last discard.
        assertNull(classic.goOut(nine + d(2), forbidden = d(2)))
    }

    @Test
    fun dealAndTurnFlow() {
        val st = Cacheta.deal(CachetaRules.WILD, 4, Random(3))
        assertEquals(List(4) { 9 }, st.hands.map { it.size })
        assertNotNull(st.vira)
        assertEquals(104, st.stock.size + st.hands.sumOf { it.size } + 1)
        val drawn = Cacheta.drawFromStock(st, Random(1))
        assertEquals(10, drawn.hands[0].size)
        assertTrue(drawn.drawn)
        val card = drawn.hands[0].first()
        val next = Cacheta.discard(drawn, card)!!
        assertEquals(1, next.turn)
        assertEquals(card, next.discard.last())
        // The next player takes it and may not throw it straight back.
        val took = Cacheta.takeDiscard(next)!!
        assertNull(Cacheta.discard(took, card))
        assertNotNull(Cacheta.discard(took, took.hands[1].first()))
    }

    @Test
    fun anEmptyDeckIsRefilledFromTheDiscardPile() {
        val st = CachetaState(
            variant = "classic", players = 2, hands = listOf(listOf(h(2)), listOf(s(2))), stock = emptyList(),
            discard = listOf(c(5), c(6), c(7)), taken = listOf(emptyList(), emptyList()),
        )
        val drawn = Cacheta.drawFromStock(st, Random(1))
        assertEquals(listOf(c(7)), drawn.discard)
        assertEquals(2, drawn.hands[0].size)
        assertEquals(1, drawn.stock.size)
    }

    @Test
    fun pifeLetsYouGrabADiscardToGoOut() {
        val mine = listOf(h(3), h(4), h(5), s(9), c(9), d(9), c(11), c(12), d(1))
        val st = CachetaState(
            variant = "pife", players = 3, hands = listOf(mine, listOf(s(2), s(6), d(13), d(13, 1)), listOf(h(10))),
            stock = listOf(d(4)), turn = 1, drawn = true, taken = List(3) { emptyList() },
        )
        val after = Cacheta.discard(st, d(13))!!
        // The K♦ does not help you (seat 0); the K♣ would complete your J-Q-K.
        assertTrue(after.offers.isEmpty())
        val withClub = st.copy(hands = listOf(mine, listOf(s(2), c(13), d(13)), listOf(h(10))))
        val offered = Cacheta.discard(withClub, c(13))!!
        assertEquals(listOf(0), offered.offers)
        assertEquals(0, Cacheta.actor(offered))
        val grabbed = Cacheta.grab(offered)!!
        assertTrue(grabbed.finished)
        assertEquals(0, grabbed.winner)
        assertEquals(d(1), grabbed.discard.last())
        // Passing lets the turn go on as usual.
        assertEquals(2, Cacheta.actor(Cacheta.pass(offered)))
        // The classic rules have no such grab.
        assertTrue(Cacheta.discard(withClub.copy(variant = "classic"), c(13))!!.offers.isEmpty())
    }

    @Test
    fun savedGamesRoundTrip() {
        val st = Cacheta.drawFromStock(Cacheta.deal(CachetaRules.WILD, 3, Random(9)), Random(2))
        val json = AppJson.encodeToString(CachetaState.serializer(), st)
        assertEquals(st, AppJson.decodeFromString(CachetaState.serializer(), json))
    }

    @Test
    fun theHintKeepsCardsThatFormGroups() {
        val hand = listOf(h(3), h(4), h(5), s(9), c(9), d(9), c(11), c(12), c(13), d(2))
        val st = CachetaState(players = 2, hands = listOf(hand, emptyList()), drawn = true, taken = listOf(emptyList(), emptyList()))
        assertEquals(d(2), CachetaAi.chooseDiscard(st, danger = false, noise = 0.0, random = Random(1)))
    }

    @Test
    fun computerGamesEndWithoutLosingCardsAndEachStepIsFast() {
        for (rules in CachetaRules.entries) for (level in Difficulty.entries) for (players in 2..4) {
            val r = Random(players * 31 + level.ordinal)
            var st = Cacheta.deal(rules, players, r)
            var steps = 0
            var slowest = 0L
            while (!st.finished && steps < 3000) {
                val t0 = System.nanoTime()
                st = CachetaAi.step(st, level, r)
                slowest = maxOf(slowest, System.nanoTime() - t0)
                val total = st.stock.size + st.discard.size + st.hands.sumOf { it.size } + (if (st.vira != null) 1 else 0)
                assertEquals(104, total)
                steps++
            }
            assertTrue("$rules $level $players did not finish", st.finished)
            if (st.winner >= 0) {
                assertEquals(st.hands[st.winner].size, st.groups.sumOf { it.size })
                st.groups.forEach { assertTrue(st.table.isGroup(it)) }
            }
            assertTrue("slowest step ${slowest / 1_000_000} ms", slowest < 500_000_000L)
        }
    }

    @Test
    fun hardBeatsEasyMoreOftenThanNot() {
        var hardWins = 0
        var easyWins = 0
        repeat(30) { g ->
            val r = Random(500 + g)
            var st = Cacheta.deal(CachetaRules.CLASSIC, 2, r)
            val hard = g % 2
            while (!st.finished) st = CachetaAi.step(st, if (Cacheta.actor(st) == hard) Difficulty.HARD else Difficulty.EASY, r)
            if (st.winner == hard) hardWins++ else if (st.winner >= 0) easyWins++
        }
        assertTrue("hard $hardWins vs easy $easyWins", hardWins > easyWins)
    }
}
