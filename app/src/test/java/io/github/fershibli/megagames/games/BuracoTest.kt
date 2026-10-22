package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.buraco.Buraco
import io.github.fershibli.megagames.games.buraco.BuracoAi
import io.github.fershibli.megagames.games.buraco.BuracoError
import io.github.fershibli.megagames.games.buraco.BuracoResult
import io.github.fershibli.megagames.games.buraco.BuracoRules
import io.github.fershibli.megagames.games.buraco.BuracoState
import io.github.fershibli.megagames.games.buraco.COMPUTER
import io.github.fershibli.megagames.games.buraco.LEFT
import io.github.fershibli.megagames.games.buraco.PARTNER
import io.github.fershibli.megagames.games.buraco.RIGHT
import io.github.fershibli.megagames.games.buraco.YOU
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.core.AppJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BuracoTest {
    private fun h(rank: Int, deck: Int = 0) = Card(Suit.HEARTS, rank, deck)
    private fun s(rank: Int, deck: Int = 0) = Card(Suit.SPADES, rank, deck)

    @Test
    fun sequencesOfTheSameSuit() {
        assertNotNull(BuracoRules.arrange(listOf(h(5), h(6), h(7))))
        assertNull(BuracoRules.arrange(listOf(h(5), h(6), s(7))))
        assertNull(BuracoRules.arrange(listOf(h(5), h(7), h(9))))
        assertNull(BuracoRules.arrange(listOf(h(5), h(6))))
        // Ace low and ace high, but no wrap-around.
        assertNotNull(BuracoRules.arrange(listOf(h(1), h(3), h(2, 1))))
        assertNotNull(BuracoRules.arrange(listOf(h(12), h(13), h(1))))
        assertNull(BuracoRules.arrange(listOf(h(13), h(1), h(3), Card(Suit.CLUBS, 2))))
    }

    @Test
    fun wildCards() {
        val gap = BuracoRules.arrange(listOf(h(5), h(7), s(2)))!!
        assertEquals(listOf(h(5), s(2), h(7)), gap.cards)
        assertEquals(s(2), gap.wild)
        // Only one wild per sequence.
        assertNull(BuracoRules.arrange(listOf(h(5), s(2), Card(Suit.CLUBS, 2))))
        // A 2 of the same suit in its own place is natural: the sequence is clean.
        val natural = BuracoRules.arrange(listOf(h(1), h(2), h(3), h(4)))!!
        assertNull(natural.wild)
        // ...and a second 2 may then act as the wild.
        assertNotNull(BuracoRules.arrange(listOf(h(1), h(2), h(3), s(2))))
    }

    @Test
    fun canastasAndCleanliness() {
        val clean = (3..9).map { h(it) }
        assertTrue(BuracoRules.isCanasta(clean) && BuracoRules.isClean(clean))
        val dirty = BuracoRules.arrange((3..8).map { h(it) } + s(2))!!.cards
        assertTrue(BuracoRules.isCanasta(dirty) && !BuracoRules.isClean(dirty))
    }

    @Test
    fun dealHas104CardsAndRightPiles() {
        val st = Buraco.deal(Random(1))
        val all = st.stock + st.discard + st.mortos.flatten() + st.hands.flatten()
        assertEquals(104, all.size)
        assertEquals(104, all.toSet().size)
        assertEquals(listOf(11, 11), st.hands.map { it.size })
        assertEquals(listOf(11, 11), st.mortos.map { it.size })
    }

    @Test
    fun emptyingTheHandTakesTheMortoAndGoingOutNeedsACleanCanasta() {
        val base = BuracoState(
            stock = listOf(s(9)), discard = emptyList(), mortos = listOf((1..11).map { Card(Suit.CLUBS, ((it - 1) % 13) + 1) }),
            hands = listOf(listOf(h(5), h(6), h(7)), listOf(s(13))), melds = listOf(emptyList(), emptyList()), drawn = true,
        )
        val tookMorto = (Buraco.meldNew(base, listOf(h(5), h(6), h(7))) as BuracoResult.Ok).state
        assertTrue(tookMorto.tookMorto[YOU])
        assertEquals(11, tookMorto.hands[YOU].size)
        // Without a clean canasta and with the morto already taken, you cannot empty your hand.
        val noMorto = base.copy(mortos = emptyList(), tookMorto = listOf(true, false))
        assertEquals(BuracoError.NEED_CLEAN_CANASTA, (Buraco.meldNew(noMorto, listOf(h(5), h(6), h(7))) as BuracoResult.Error).reason)
        // With a clean canasta on the table, going out ends the game.
        val withCanasta = noMorto.copy(melds = listOf(listOf((3..9).map { s(it, 1) }), emptyList()))
        val out = (Buraco.meldNew(withCanasta, listOf(h(5), h(6), h(7))) as BuracoResult.Ok).state
        assertTrue(out.finished)
        assertEquals(YOU, out.wentOut)
        assertEquals(100, Buraco.score(out, YOU).wentOut)
    }

    @Test
    fun scoring() {
        val st = BuracoState(
            stock = emptyList(), discard = emptyList(), mortos = emptyList(),
            hands = listOf(listOf(h(1)), emptyList()), melds = listOf(listOf((3..9).map { s(it) }), emptyList()),
            tookMorto = listOf(true, false),
        )
        val sc = Buraco.score(st, YOU)
        assertEquals(5 * 5 + 10 * 2, sc.melded) // 3-7 = 5 points, 8-9 = 10 points
        assertEquals(200, sc.bonus)
        assertEquals(15, sc.inHand)
        assertEquals(200 + 45 - 15, sc.total)
        assertEquals(100, Buraco.score(st, COMPUTER).mortoPenalty)
    }

    @Test
    fun computerVsComputerGamesFinishWithoutLosingCards() {
        for (d in Difficulty.entries) for (seed in 1..4) {
            val r = Random(seed)
            var st = Buraco.deal(r)
            var turns = 0
            while (!st.finished && turns < 400) {
                val steps = BuracoAi.playTurn(st, d, r)
                assertTrue(steps.isNotEmpty())
                st = steps.last()
                val total = st.stock.size + st.discard.size + st.mortos.sumOf { it.size } + st.hands.sumOf { it.size } + st.melds.sumOf { m -> m.sumOf { it.size } }
                assertEquals(104, total)
                st.melds.flatten().forEach { assertNotNull(BuracoRules.arrange(it)) }
                turns++
            }
            assertTrue("$d seed $seed did not finish", st.finished)
        }
    }

    @Test
    fun hardBeatsEasy() {
        var hardWins = 0
        repeat(10) { g ->
            val r = Random(100 + g)
            var st = Buraco.deal(r)
            while (!st.finished) {
                val level = if (st.turn == (g % 2)) Difficulty.HARD else Difficulty.EASY
                st = BuracoAi.playTurn(st, level, r).last()
            }
            val hard = g % 2
            if (Buraco.score(st, hard).total > Buraco.score(st, 1 - hard).total) hardWins++
        }
        assertTrue("hard won $hardWins of 10", hardWins >= 7)
    }

    // ----- Four players in pairs -----------------------------------------------------------

    private fun c(rank: Int, deck: Int = 0) = Card(Suit.CLUBS, rank, deck)

    /** A four-seat table with the given hands, the morto piles and nothing laid down yet. */
    private fun four(hands: List<List<Card>>, turn: Int = YOU) = BuracoState(
        stock = listOf(s(9), s(10)), discard = listOf(c(13)), mortos = listOf((1..11).map { Card(Suit.DIAMONDS, (it - 1) % 13 + 1) }, (1..11).map { Card(Suit.DIAMONDS, (it - 1) % 13 + 1, 1) }),
        hands = hands, melds = listOf(emptyList(), emptyList()), turn = turn, drawn = true, players = 4,
    )

    @Test
    fun fourPlayersDealElevenEachAndTwoMortos() {
        val st = Buraco.deal(Random(7), players = 4)
        assertEquals(4, st.players)
        assertEquals(listOf(11, 11, 11, 11), st.hands.map { it.size })
        assertEquals(listOf(11, 11), st.mortos.map { it.size })
        assertEquals(2, st.melds.size)
        assertEquals(2, st.tookMorto.size)
        val all = st.stock + st.discard + st.mortos.flatten() + st.hands.flatten()
        assertEquals(104, all.toSet().size)
        assertEquals(104 - 44 - 22 - 1, st.stock.size)
    }

    @Test
    fun turnsGoRoundCounterclockwiseAndPartnersShareTheirSequences() {
        val st = four(listOf(listOf(h(5), h(6), h(7), s(13)), listOf(s(3), s(4)), listOf(h(8), s(12)), listOf(c(3), c(4))))
        // You lay down 5-6-7♥ and discard: the player on your right plays next.
        val laid = (Buraco.meldNew(st, listOf(h(5), h(6), h(7))) as BuracoResult.Ok).state
        assertEquals(listOf(listOf(h(5), h(6), h(7))), laid.melds[0])
        val afterYou = (Buraco.discard(laid, s(13)) as BuracoResult.Ok).state
        assertEquals(RIGHT, afterYou.turn)
        val afterRight = (Buraco.discard(Buraco.drawFromStock(afterYou), s(3)) as BuracoResult.Ok).state
        assertEquals(PARTNER, afterRight.turn)
        // Your partner adds the 8♥ to the sequence you laid down: it is the side's sequence.
        val partner = (Buraco.addToMeld(Buraco.drawFromStock(afterRight), 0, listOf(h(8))) as BuracoResult.Ok).state
        assertEquals(listOf(listOf(h(5), h(6), h(7), h(8))), partner.melds[0])
        assertTrue(partner.melds[1].isEmpty())
        val afterPartner = (Buraco.discard(partner, s(12)) as BuracoResult.Ok).state
        assertEquals(LEFT, afterPartner.turn)
        val back = (Buraco.discard(Buraco.takeDiscardPile(afterPartner)!!, c(3)) as BuracoResult.Ok).state
        assertEquals(YOU, back.turn)
        // The other side took the pile: a hard partner remembers it, your own picks stay apart.
        assertTrue(s(12) in back.theirPicks)
        assertTrue(back.yourPicks.isEmpty())
    }

    @Test
    fun eachSidePicksUpOneMorto() {
        val st = four(listOf(listOf(h(9)), listOf(s(3), s(4), s(5), c(9)), listOf(h(5), h(6), h(7)), listOf(c(3))), turn = PARTNER)
        // Your partner empties a hand first: your side picks up its morto.
        val partner = (Buraco.meldNew(st, listOf(h(5), h(6), h(7))) as BuracoResult.Ok).state
        assertEquals(listOf(true, false), partner.tookMorto)
        assertEquals(11, partner.hands[PARTNER].size)
        assertEquals(1, partner.mortos.size)
        // Now your side has no morto left: you cannot empty your hand without a clean canasta.
        val you = partner.copy(turn = YOU)
        assertEquals(BuracoError.NEED_CLEAN_CANASTA, (Buraco.discard(you, h(9)) as BuracoResult.Error).reason)
        assertFalse(Buraco.canEmptyHand(you, YOU))
        assertTrue(Buraco.canEmptyHand(you, PARTNER).not())
        // The other side still has its own morto.
        val right = partner.copy(turn = RIGHT)
        assertTrue(Buraco.canEmptyHand(right, RIGHT))
        val rightLaid = (Buraco.meldNew(right, listOf(s(3), s(4), s(5))) as BuracoResult.Ok).state
        val rightOut = (Buraco.discard(rightLaid, c(9)) as BuracoResult.Ok).state
        assertEquals(listOf(true, true), rightOut.tookMorto)
        assertEquals(11, rightOut.hands[RIGHT].size)
        assertEquals(PARTNER, rightOut.turn)
        assertTrue(rightOut.mortos.isEmpty())
    }

    @Test
    fun aSideGoesOutAndScoresTogether() {
        val canasta = (3..9).map { s(it, 1) }
        val st = four(listOf(listOf(h(1)), listOf(c(13)), listOf(h(5), h(6), h(7), s(13)), listOf(c(4), c(5))), turn = PARTNER).copy(
            tookMorto = listOf(true, false), mortos = listOf((1..11).map { Card(Suit.DIAMONDS, (it - 1) % 13 + 1) }),
            melds = listOf(listOf(canasta), emptyList()),
        )
        val laid = (Buraco.meldNew(st, listOf(h(5), h(6), h(7))) as BuracoResult.Ok).state
        val out = (Buraco.discard(laid, s(13)) as BuracoResult.Ok).state
        assertTrue(out.finished)
        assertEquals(PARTNER, out.wentOut)
        val ours = Buraco.score(out, 0)
        val theirs = Buraco.score(out, 1)
        assertEquals(100, ours.wentOut)
        assertEquals(200, ours.bonus)
        assertEquals(15, ours.inHand) // your ace: the cards left in both partners' hands count
        assertEquals(0, ours.mortoPenalty)
        assertEquals(10 + 5 + 5, theirs.inHand) // K + 4 + 5 from right and left together
        assertEquals(100, theirs.mortoPenalty)
        assertEquals(0, theirs.wentOut)
        assertEquals(Buraco.tablePoints(out, 0) + 100 - 15, ours.total)
    }

    @Test
    fun oldSavesWithoutSeatsAreTwoPlayerGames() {
        val old = """{"stock":[],"discard":[],"mortos":[],"hands":[[],[]],"melds":[[],[]],"turn":1}"""
        val st = AppJson.decodeFromString(BuracoState.serializer(), old)
        assertEquals(2, st.players)
        assertEquals(1, st.turn)
        assertTrue(st.theirPicks.isEmpty())
    }

    @Test
    fun fourComputerPlayersFinishQuicklyWithoutLosingCards() {
        var slowest = 0L
        for (d in Difficulty.entries) for (seed in 1..3) {
            val r = Random(seed * 11)
            var st = Buraco.deal(r, players = 4)
            var turns = 0
            while (!st.finished && turns < 800) {
                val start = System.nanoTime()
                val steps = BuracoAi.playTurn(st, d, r)
                slowest = maxOf(slowest, System.nanoTime() - start)
                st = steps.last()
                val total = st.stock.size + st.discard.size + st.mortos.sumOf { it.size } + st.hands.sumOf { it.size } + st.melds.sumOf { m -> m.sumOf { it.size } }
                assertEquals(104, total)
                assertEquals(4, st.hands.size)
                st.melds.flatten().forEach { assertNotNull(BuracoRules.arrange(it)) }
                turns++
            }
            assertTrue("$d seed $seed did not finish", st.finished)
        }
        assertTrue("slowest computer turn took ${slowest / 1_000_000} ms", slowest < 500_000_000L)
    }

    @Test
    fun hardPairBeatsEasyPair() {
        var hardWins = 0
        repeat(10) { g ->
            val r = Random(300 + g)
            var st = Buraco.deal(r, players = 4)
            val hard = g % 2
            while (!st.finished) st = BuracoAi.playTurn(st, if (st.side(st.turn) == hard) Difficulty.HARD else Difficulty.EASY, r).last()
            if (Buraco.score(st, hard).total > Buraco.score(st, 1 - hard).total) hardWins++
        }
        assertTrue("hard pair won $hardWins of 10", hardWins >= 7)
    }
}
