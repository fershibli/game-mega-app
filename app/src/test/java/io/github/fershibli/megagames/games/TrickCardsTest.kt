package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.bisca.Bisca
import io.github.fershibli.megagames.games.bisca.BiscaAi
import io.github.fershibli.megagames.games.bisca.BiscaState
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.escova.Escova
import io.github.fershibli.megagames.games.escova.EscovaAi
import io.github.fershibli.megagames.games.escova.EscovaState
import io.github.fershibli.megagames.games.hearts.Hearts
import io.github.fershibli.megagames.games.hearts.HeartsAi
import io.github.fershibli.megagames.games.hearts.HeartsState
import io.github.fershibli.megagames.games.hearts.PassDirection
import io.github.fershibli.megagames.games.sueca.Sueca
import io.github.fershibli.megagames.games.sueca.SuecaAi
import io.github.fershibli.megagames.games.sueca.SuecaState
import io.github.fershibli.megagames.games.tricks.AceTen
import io.github.fershibli.megagames.games.tricks.Play
import io.github.fershibli.megagames.games.tricks.Tricks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Rules, scoring and computer players of Sueca, Bisca, Hearts and Escova. */
class TrickCardsTest {
    private fun h(r: Int) = Card(Suit.HEARTS, r)
    private fun s(r: Int) = Card(Suit.SPADES, r)
    private fun c(r: Int) = Card(Suit.CLUBS, r)
    private fun d(r: Int) = Card(Suit.DIAMONDS, r)

    private val levels = Difficulty.entries

    // ----- Shared ----------------------------------------------------------------------------

    @Test
    fun aceTenDeckIsFortyCardsWorth120() {
        assertEquals(40, AceTen.CARDS.toSet().size)
        assertTrue(AceTen.CARDS.none { it.rank in 8..10 })
        assertEquals(120, AceTen.points(AceTen.CARDS))
        // A > 7 > K > J > Q > 6 > ... > 2 in every suit.
        val order = listOf(1, 7, 13, 11, 12, 6, 5, 4, 3, 2).map { s(it) }
        assertEquals(order, order.sortedByDescending { AceTen.power(it) })
        assertEquals(listOf(11, 10, 4, 3, 2, 0), listOf(1, 7, 13, 11, 12, 6).map { AceTen.points(h(it)) })
    }

    @Test
    fun trumpBeatsAndOffSuitNeverWins() {
        val trick = listOf(Play(0, s(5)), Play(1, h(1)), Play(2, s(6)), Play(3, c(2)))
        // Hearts not trumps: the ace of hearts is just a discard.
        assertEquals(2, Tricks.winning(trick, Suit.DIAMONDS, AceTen::power).seat)
        // Clubs trumps: the smallest trump wins.
        assertEquals(3, Tricks.winning(trick, Suit.CLUBS, AceTen::power).seat)
        // Two trumps: the stronger one.
        val two = listOf(Play(0, s(1)), Play(1, c(2)), Play(2, c(12)))
        assertEquals(2, Tricks.winning(two, Suit.CLUBS, AceTen::power).seat)
    }

    @Test
    fun imaginedDealsRespectVoids() {
        val unseen = AceTen.CARDS.take(20)
        val voids = listOf(setOf(Suit.SPADES), emptySet(), setOf(Suit.HEARTS, Suit.CLUBS))
        repeat(50) { k ->
            val hands = Tricks.imagine(unseen, intArrayOf(6, 7, 7), voids, Random(k))
            assertEquals(listOf(6, 7, 7), hands.map { it.size })
            assertEquals(20, hands.flatten().toSet().size)
            hands.forEachIndexed { i, hand -> assertTrue(hand.none { it.suit in voids[i] }) }
        }
    }

    // ----- Sueca ------------------------------------------------------------------------------

    @Test
    fun suecaDealsTenEachAndTheDealerHoldsTheTrump() {
        val g = Sueca.newGame(Random(3))
        assertEquals(List(4) { 10 }, g.hands.map { it.size })
        assertEquals(40, g.hands.flatten().toSet().size)
        assertTrue(g.trumpCard in g.hands[g.dealer])
        assertEquals((g.dealer + 1) % 4, g.turn)
        val next = Sueca.nextHand(g.copy(result = io.github.fershibli.megagames.games.sueca.SuecaResult(listOf(70, 50), 0, 1)), Random(4))
        assertEquals((g.dealer + 1) % 4, next.dealer)
    }

    @Test
    fun suecaMustFollowSuit() {
        val g = suecaWith(listOf(listOf(s(2), h(3)), listOf(s(4), h(5), c(6)), listOf(c(2), d(3)), listOf(s(5), d(4))), turn = 0)
        val afterLead = Sueca.play(g, s(2))!!
        assertEquals(listOf(s(4)), Sueca.legal(afterLead))
        assertNull(Sueca.play(afterLead, h(5)))
        val two = Sueca.play(afterLead, s(4))!!
        // Seat 2 has no spades: anything goes.
        assertEquals(setOf(c(2), d(3)), Sueca.legal(two).toSet())
    }

    private fun suecaWith(hands: List<List<Card>>, turn: Int, trump: Card = h(1), dealer: Int = 3) =
        SuecaState(hands = hands, dealer = dealer, trumpCard = trump, turn = turn)

    /** A hand ending: the point cards in five tricks, zero cards in the others; [won] says who took each. */
    private fun suecaEnd(won: List<Int>, games: List<Int> = listOf(0, 0), carry: Int = 0): SuecaState {
        val groups = listOf(1, 7, 13, 11, 12).map { r -> Suit.entries.map { Card(it, r) } }
        val zeros = AceTen.CARDS.filter { AceTen.points(it) == 0 && it.suit != Suit.HEARTS }
        val zeroTricks = zeros.chunked(4).take(4)
        val tricks = (groups + zeroTricks).map { t -> t.mapIndexed { i, card -> Play(i, card) } }
        // Last trick: hearts are trumps and seat won[9] takes it with the 5♥.
        val last = won[9]
        val lastTrick = listOf(h(2), h(3), h(4), h(5)).let { cards -> (0 until 4).map { k -> Play((last + 1 + k) % 4, cards[k]) } }
        return SuecaState(
            hands = List(4) { emptyList() }, dealer = 3, trumpCard = h(5), trick = lastTrick, tricks = tricks,
            winners = won.take(9), turn = lastTrick.last().seat, games = games, carry = carry,
        )
    }

    @Test
    fun suecaScoresGamesByPoints() {
        // Aces and sevens (84) and nothing else: 1 game.
        var r = Sueca.collect(suecaEnd(listOf(0, 0, 1, 1, 1, 1, 1, 1, 1, 1)))
        assertEquals(listOf(84, 36), r.result!!.points)
        assertEquals(0, r.result!!.team)
        assertEquals(listOf(1, 0), r.games)
        // Aces, sevens and jacks (96): 2 games.
        r = Sueca.collect(suecaEnd(listOf(0, 0, 1, 0, 1, 1, 1, 1, 1, 1)))
        assertEquals(96, r.result!!.points[0])
        assertEquals(listOf(2, 0), r.games)
        // Every point but one empty trick lost: 2 games, no bandeira.
        r = Sueca.collect(suecaEnd(listOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0)))
        assertEquals(120, r.result!!.points[0])
        assertFalse(r.result!!.bandeira)
        assertEquals(listOf(2, 0), r.games)
        // Every trick: bandeira, 4 games and the match.
        r = Sueca.collect(suecaEnd(List(10) { 2 }))
        assertTrue(r.result!!.bandeira)
        assertTrue(r.finished)
    }

    @Test
    fun suecaTieCarriesAGame() {
        // Aces and kings (60) against the rest (60).
        val tie = Sueca.collect(suecaEnd(listOf(0, 1, 0, 1, 1, 1, 1, 1, 1, 1)))
        assertEquals(listOf(60, 60), tie.result!!.points)
        assertEquals(-1, tie.result!!.team)
        assertEquals(1, tie.carry)
        val next = Sueca.collect(suecaEnd(listOf(1, 1, 0, 0, 0, 0, 0, 0, 0, 0), carry = tie.carry))
        assertEquals(1, next.result!!.team)
        assertEquals(2, next.result!!.games)
        assertEquals(0, next.carry)
        // Four games end the match.
        val end = Sueca.collect(suecaEnd(listOf(0, 0, 1, 1, 1, 1, 1, 1, 1, 1), games = listOf(3, 2)))
        assertTrue(end.finished)
    }

    @Test
    fun suecaPartnerLoadsPointsAndNeverTrumpsAPartnersSureTrick() {
        // You lead the A♠, the right player follows; your partner has no spades, holds a trump and the K♣.
        val g = suecaWith(listOf(listOf(s(1), d(2)), listOf(s(3), d(4)), listOf(h(2), c(13)), listOf(s(4), c(2))), turn = 0)
        val st = Sueca.play(Sueca.play(g, s(1))!!, s(3))!!
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) assertEquals(c(13), SuecaAi.choose(st, d, Random(1)))
        // Following suit, the partner adds its king to your sure ace.
        val g2 = suecaWith(listOf(listOf(s(1), d(2)), listOf(s(3), d(4)), listOf(s(13), s(2)), listOf(s(4), c(2))), turn = 0)
        val st2 = Sueca.play(Sueca.play(g2, s(1))!!, s(3))!!
        assertEquals(s(13), SuecaAi.choose(st2, Difficulty.MEDIUM, Random(1)))
    }

    @Test
    fun suecaComputersPlayWholeMatchesFollowingSuit() {
        for (d in levels) {
            var st = Sueca.newGame(Random(d.ordinal))
            var guard = 0
            while (!st.finished && guard++ < 2000) {
                if (st.handOver) { st = Sueca.nextHand(st, Random(guard)); continue }
                if (!st.trickComplete) {
                    val legal = Sueca.legal(st)
                    val card = SuecaAi.choose(st, d, Random(guard))
                    assertTrue("$d played an illegal card", card in legal)
                }
                val next = SuecaAi.step(st, d, Random(guard))
                assertNotEquals(st, next)
                st = next
                if (!st.handOver) assertEquals(40, st.hands.sumOf { it.size } + st.trick.size + st.tricks.sumOf { it.size })
            }
            assertTrue(st.finished)
            assertTrue(st.games.max() >= Sueca.TARGET)
        }
    }

    @Test
    fun suecaSavesAndRestores() {
        var st = Sueca.newGame(Random(9))
        repeat(7) { st = SuecaAi.step(st, Difficulty.MEDIUM, Random(it)) }
        val json = AppJson.encodeToString(SuecaState.serializer(), st)
        assertEquals(st, AppJson.decodeFromString(SuecaState.serializer(), json))
    }

    // ----- Bisca ------------------------------------------------------------------------------

    @Test
    fun biscaDealsThreeEachWithTheTrumpUnderTheStock() {
        for (seats in listOf(2, 4)) {
            val g = Bisca.newGame(seats, Random(seats))
            assertEquals(List(seats) { 3 }, g.hands.map { it.size })
            assertEquals(40 - 3 * seats, g.stock.size)
            assertEquals(g.trumpCard, g.stock.last())
            assertEquals(40, (g.hands.flatten() + g.stock).toSet().size)
            assertEquals(0, g.turn)
        }
    }

    @Test
    fun biscaFreeWhileDrawingThenFollowSuit() {
        val g = BiscaState(seats = 2, hands = listOf(listOf(s(2), h(3), c(4)), listOf(s(5), d(6), c(7))), stock = listOf(d(1), d(2), h(1)), trumpCard = h(1), turn = 0)
        val led = Bisca.play(g, s(2))!!
        // Stock still there: any card.
        assertEquals(3, Bisca.legal(led).size)
        val trick = Bisca.play(led, d(6))!!
        assertEquals(0, Bisca.winner(trick).seat)
        val after = Bisca.collect(trick)
        // The winner draws first.
        assertTrue(d(1) in after.hands[0])
        assertTrue(d(2) in after.hands[1])
        assertEquals(listOf(h(1)), after.stock)
        // Stock empty: follow suit.
        val end = BiscaState(seats = 2, hands = listOf(listOf(s(2), h(3)), listOf(s(5), c(7))), stock = emptyList(), trumpCard = h(1), turn = 0)
        assertEquals(listOf(s(5)), Bisca.legal(Bisca.play(end, s(2))!!))
    }

    @Test
    fun biscaScoring() {
        fun end(winnerOfAces: Int, winnerOfSevens: Int, winnerOfRest: Int): BiscaState {
            val aces = Suit.entries.map { Card(it, 1) }
            val sevens = Suit.entries.map { Card(it, 7) }
            val rest = AceTen.CARDS.filter { it.rank != 1 && it.rank != 7 && it !in listOf(h(2), s(2)) }
            val tricks = (listOf(aces, sevens) + rest.chunked(2)).map { t -> t.mapIndexed { i, card -> Play(i % 2, card) } }
            val winners = listOf(winnerOfAces, winnerOfSevens) + List(tricks.size - 2) { winnerOfRest }
            val last = listOf(Play(winnerOfRest, h(2)), Play(1 - winnerOfRest, s(2)))
            return BiscaState(seats = 2, hands = List(2) { emptyList() }, trumpCard = h(5), trick = last, tricks = tricks, winners = winners, turn = 1 - winnerOfRest)
        }
        // Aces and sevens (84) against 36: 1 game.
        assertEquals(listOf(1, 0), Bisca.collect(end(0, 0, 1)).games)
        // Aces and the rest (80): 1 game for seat 0; sevens and the rest for seat 1 would be 76.
        assertEquals(listOf(0, 1), Bisca.collect(end(1, 0, 1)).games)
        // Everything: 2 games.
        assertEquals(listOf(2, 0), Bisca.collect(end(0, 0, 0)).games)
    }

    @Test
    fun biscaMediumLeadsASmallCardWhileDrawing() {
        val g = BiscaState(seats = 2, hands = listOf(listOf(s(1), c(2), d(13)), listOf(s(5), d(6), c(7))), stock = List(10) { d(3) } + h(4), trumpCard = h(4), turn = 0)
        assertEquals(c(2), BiscaAi.choose(g, Difficulty.MEDIUM, Random(1)))
        // And takes a led ace cheaply with a small trump.
        val ace = BiscaState(seats = 2, hands = listOf(listOf(s(1)), listOf(h(2), c(4), h(1))), stock = List(10) { d(3) } + h(4), trumpCard = h(4), turn = 0)
        assertEquals(h(2), BiscaAi.choose(Bisca.play(ace, s(1))!!, Difficulty.MEDIUM, Random(1)))
    }

    @Test
    fun biscaComputersPlayEveryTableSize() {
        for (seats in listOf(2, 4)) for (d in levels) {
            var st = Bisca.newGame(seats, Random(seats * 10 + d.ordinal))
            var guard = 0
            while (!st.finished && guard++ < 3000) {
                if (st.handOver) { st = Bisca.nextHand(st, Random(guard)); continue }
                if (!st.trickComplete) assertTrue(BiscaAi.choose(st, d, Random(guard)) in Bisca.legal(st))
                val next = BiscaAi.step(st, d, Random(guard))
                assertNotEquals(st, next)
                st = next
                if (!st.handOver) assertEquals(40, st.hands.sumOf { it.size } + st.stock.size + st.trick.size + st.tricks.sumOf { it.size })
            }
            assertTrue("bisca $seats $d", st.finished)
        }
    }

    // ----- Hearts -----------------------------------------------------------------------------

    @Test
    fun heartsPassingCyclesAndExchanges() {
        val g = Hearts.newGame(Random(1))
        assertEquals(PassDirection.LEFT, g.direction)
        assertTrue(g.passingPhase)
        var st = g
        val given = List(4) { st.hands[it].take(3) }
        for (seat in 0 until 4) {
            assertEquals(seat, Hearts.actor(st))
            st = Hearts.choosePass(st, seat, given[seat])!!
        }
        assertFalse(st.passingPhase)
        // To the left: seat 1 got seat 0's cards.
        assertTrue(st.hands[1].containsAll(given[0]))
        assertEquals(given[0], st.received[1])
        assertTrue(Hearts.TWO_OF_CLUBS in st.hands[st.turn])
        assertEquals(listOf(PassDirection.LEFT, PassDirection.RIGHT, PassDirection.ACROSS, PassDirection.NONE), (0..3).map { HeartsState(handNumber = it).direction })
        // No passing on the fourth hand: play starts at once.
        val fourth = Hearts.deal(HeartsState(handNumber = 3), Random(2))
        assertFalse(fourth.passingPhase)
        assertTrue(Hearts.TWO_OF_CLUBS in fourth.hands[fourth.turn])
    }

    private fun heartsPlay(hands: List<List<Card>>, turn: Int, broken: Boolean = false, firstTrick: Boolean = false) = HeartsState(
        hands = hands, handNumber = 3, passed = true, turn = turn, heartsBroken = broken,
        tricks = if (firstTrick) emptyList() else listOf(listOf(Play(0, c(3)), Play(1, c(4)), Play(2, c(5)), Play(3, c(6)))),
        winners = if (firstTrick) emptyList() else listOf(3),
    )

    @Test
    fun heartsLeadAndDiscardRules() {
        // First trick: the 2♣ leads.
        val first = heartsPlay(listOf(listOf(c(2), h(5)), listOf(s(12), h(2), d(3)), listOf(c(9)), listOf(d(4))), 0, firstTrick = true)
        assertEquals(listOf(c(2)), Hearts.legal(first))
        // Seat 1 has no clubs: no heart and no Q♠ on the first trick.
        assertEquals(listOf(d(3)), Hearts.legal(Hearts.play(first, c(2))!!))
        // Hearts cannot be led before they are broken, unless only hearts are left.
        val lead = heartsPlay(listOf(listOf(h(5), s(3)), emptyList(), emptyList(), emptyList()), 0)
        assertEquals(listOf(s(3)), Hearts.legal(lead))
        assertEquals(listOf(h(5)), Hearts.legal(heartsPlay(listOf(listOf(h(5)), emptyList(), emptyList(), emptyList()), 0)))
        assertEquals(2, Hearts.legal(lead.copy(heartsBroken = true)).size)
        // Following suit.
        val follow = heartsPlay(listOf(listOf(d(9)), listOf(d(2), h(13)), listOf(c(9)), listOf(d(4))), 0)
        assertEquals(listOf(d(2)), Hearts.legal(Hearts.play(follow, d(9))!!))
    }

    @Test
    fun heartsScoringAndTheMoon() {
        assertEquals(listOf(0, 26, 26, 26) to 0, Hearts.handScore(listOf(26, 0, 0, 0)))
        assertEquals(listOf(13, 5, 8, 0) to -1, Hearts.handScore(listOf(13, 5, 8, 0)))
        assertEquals(13, Hearts.points(Hearts.QUEEN_OF_SPADES))
        assertEquals(1, Hearts.points(h(2)))
        assertEquals(0, Hearts.points(s(1)))
        // The ace is high and the suit led wins.
        assertEquals(1, Hearts.winner(listOf(Play(0, d(13)), Play(1, d(1)), Play(2, h(1)), Play(3, d(2)))).seat)
    }

    @Test
    fun heartsComputerDropsTheQueenWhenVoid() {
        val st = heartsPlay(listOf(listOf(d(9)), listOf(s(12), h(13), c(2)), listOf(d(3)), listOf(d(4))), 0)
        val led = Hearts.play(st, d(9))!!
        assertEquals(Hearts.QUEEN_OF_SPADES, HeartsAi.choose(led, Difficulty.MEDIUM, Random(1)))
        // Medium passes the queen of spades.
        val g = Hearts.newGame(Random(5))
        val seat = g.hands.indexOfFirst { hand -> Hearts.QUEEN_OF_SPADES in hand && hand.count { it.suit == Suit.SPADES } < 5 }
        if (seat >= 0) assertTrue(Hearts.QUEEN_OF_SPADES in HeartsAi.choosePass(g, seat, Difficulty.MEDIUM, Random(1)))
    }

    @Test
    fun heartsComputersPlayToAHundred() {
        for (d in levels) {
            var st = Hearts.newGame(Random(30 + d.ordinal))
            var guard = 0
            while (!st.finished && guard++ < 6000) {
                if (st.handOver) {
                    assertEquals(26, st.handPoints!!.sum().let { if (st.moon >= 0) 26 else it })
                    st = Hearts.nextHand(st, Random(guard))
                    continue
                }
                if (!st.passingPhase && !st.trickComplete) assertTrue(HeartsAi.choose(st, d, Random(guard)) in Hearts.legal(st))
                val next = HeartsAi.step(st, d, Random(guard))
                assertNotEquals(st, next)
                st = next
            }
            assertTrue(st.finished)
            assertTrue(st.scores.max() >= Hearts.TARGET)
            assertTrue(Hearts.leaders(st).all { st.scores[it] == st.scores.min() })
        }
    }

    // ----- Escova -----------------------------------------------------------------------------

    @Test
    fun escovaValuesAndCaptures() {
        assertEquals(listOf(1, 7, 8, 9, 10), listOf(1, 7, 11, 12, 13).map { Escova.value(h(it)) })
        val table = listOf(d(5), c(3), h(13), s(2))
        val caps = Escova.captures(table, s(7)).map { it.toSet() }.toSet()
        // 7 needs 8: 5 + 3.
        assertEquals(setOf(setOf(d(5), c(3))), caps)
        // A 2 needs 13: only K + 3. An ace would need 14: no way.
        assertEquals(listOf(setOf(h(13), c(3))), Escova.captures(table, h(2)).map { it.toSet() })
        assertTrue(Escova.captures(table, c(1)).isEmpty())
        assertTrue(Escova.captures(emptyList(), s(7)).isEmpty())
    }

    private fun escovaWith(hands: List<List<Card>>, table: List<Card>, stock: List<Card> = emptyList(), turn: Int = 0) =
        EscovaState(seats = hands.size, hands = hands, table = table, stock = stock, dealer = hands.size - 1, turn = turn)

    @Test
    fun escovaMustTakeAndCountsEscovas() {
        val g = escovaWith(listOf(listOf(s(7), h(2)), listOf(d(4), c(1))), listOf(d(5), c(3)), stock = List(6) { d(6) })
        // The 7 can make 15: it may not be laid down.
        assertEquals(Escova.Problem.MUST_TAKE, Escova.check(g, s(7), emptyList()))
        assertNull(Escova.play(g, s(7), emptyList()))
        assertEquals(Escova.Problem.NOT_FIFTEEN, Escova.check(g, s(7), listOf(d(5))))
        // The 2 cannot: laid down.
        val laid = Escova.play(g, h(2), emptyList())!!
        assertEquals(listOf(d(5), c(3), h(2)), laid.table)
        // Taking every card is an escova.
        val swept = Escova.play(g, s(7), listOf(d(5), c(3)))!!
        assertTrue(swept.table.isEmpty())
        assertEquals(listOf(1, 0), swept.escovas)
        assertEquals(3, swept.captured[0].size)
        assertTrue(swept.last!!.escova)
    }

    @Test
    fun escovaRefillsAndEndsTheHand() {
        // Last cards: the computer lays a card, the leftovers go to whoever took last.
        val g = escovaWith(listOf(listOf(s(7)), listOf(c(6))), listOf(d(5), c(3), h(1)), stock = emptyList())
        val took = Escova.play(g, s(7), listOf(d(5), c(3)))!!
        val end = Escova.play(took, c(6), emptyList())!!
        assertNotNull(end.result)
        // You took 7, 5, 3 and the leftovers A♥ and 6♣.
        assertEquals(5, end.result!!.tallies[0].cards)
        assertEquals(0, end.result!!.tallies[1].cards)
        // Refill: three new cards each from the stock.
        val refill = escovaWith(listOf(listOf(s(2)), emptyList()), listOf(d(6)), stock = AceTen.CARDS.filter { it.rank == 4 || it.rank == 6 }.take(6))
        val after = Escova.play(refill, s(2), emptyList())!!
        assertEquals(listOf(3, 3), after.hands.map { it.size })
        assertTrue(after.stock.isEmpty())
    }

    @Test
    fun escovaOpeningFifteenIsTheDealers() {
        var found = 0
        for (seed in 0 until 400) {
            val g = Escova.newGame(2, Random(seed))
            if (g.opening == 0) continue
            found++
            assertTrue(g.table.isEmpty())
            assertEquals(g.opening, g.escovas[Escova.team(g.dealer)])
            assertEquals(4, g.captured[Escova.team(g.dealer)].size)
        }
        assertTrue(found > 0)
    }

    @Test
    fun escovaEndOfHandPoints() {
        // You hold 7♦ and most diamonds; sevens are 2–2 (tie); cards 21–19.
        val mine = AceTen.CARDS.filter { it.suit == Suit.DIAMONDS } + listOf(s(7)) + AceTen.CARDS.filter { it.suit == Suit.CLUBS && it.rank != 7 } + listOf(h(1))
        val theirs = AceTen.CARDS.filter { it !in mine && it != h(3) }
        val g = EscovaState(seats = 2, hands = listOf(listOf(h(3)), emptyList()), table = emptyList(), stock = emptyList(), turn = 0,
            captured = listOf(mine, theirs), escovas = listOf(1, 2), lastCaptor = 1)
        val end = Escova.play(g, h(3), emptyList())!!
        val r = end.result!!
        assertEquals(21, r.tallies[0].cards)
        assertEquals(0, r.mostCards)
        assertEquals(0, r.mostDiamonds)
        assertEquals(0, r.seteBelo)
        assertEquals(-1, r.mostSevens)
        assertEquals(listOf(1 + 3, 2), r.points)
    }

    @Test
    fun escovaGameEndsOnlyWithAWinner() {
        val g = EscovaState(seats = 2, hands = listOf(listOf(h(3)), emptyList()), table = emptyList(), stock = emptyList(), turn = 0,
            captured = listOf(AceTen.CARDS.filter { it.suit == Suit.DIAMONDS }, AceTen.CARDS.filter { it.suit != Suit.DIAMONDS && it != h(3) }), scores = listOf(13, 15), lastCaptor = 0)
        val end = Escova.play(g, h(3), emptyList())!!
        // Diamonds and sete belo for you (+2, 15), cards and sevens for them (+2, 17).
        assertEquals(listOf(15, 17), end.scores)
        assertTrue(end.finished)
        val tied = Escova.play(g.copy(scores = listOf(15, 15)), h(3), emptyList())!!
        assertEquals(listOf(17, 17), tied.scores)
        assertFalse(tied.finished)
    }

    @Test
    fun escovaComputerTakesTheEscova() {
        val g = escovaWith(listOf(listOf(d(1)), listOf(s(7), c(2), h(4))), listOf(d(5), c(3)), stock = List(6) { d(6) }, turn = 1)
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            val m = EscovaAi.choose(g, d, Random(1))
            assertEquals(s(7), m.card)
            assertTrue(m.escova)
        }
    }

    @Test
    fun escovaComputersPlayEveryTableSize() {
        for (seats in listOf(2, 4)) for (d in levels) {
            var st = Escova.newGame(seats, Random(seats * 7 + d.ordinal))
            var guard = 0
            while (!st.finished && guard++ < 3000) {
                if (st.handOver) { st = Escova.nextHand(st, Random(guard)); continue }
                val next = EscovaAi.step(st, d, Random(guard))
                assertNotEquals(st, next)
                st = next
                if (!st.handOver) assertEquals(40, st.hands.sumOf { it.size } + st.table.size + st.stock.size + st.captured.sumOf { it.size })
            }
            assertTrue("escova $seats $d", st.finished)
        }
    }

    // ----- Speed ------------------------------------------------------------------------------

    @Test
    fun hardComputersAnswerWellUnderASecond() {
        var worst = 0L
        fun timed(block: () -> Unit) {
            val t = System.nanoTime()
            block()
            worst = maxOf(worst, (System.nanoTime() - t) / 1_000_000)
        }
        val r = Random(77)
        var sueca = Sueca.newGame(r)
        while (!sueca.handOver) { val st = sueca; timed { sueca = SuecaAi.step(st, Difficulty.HARD, r) } }
        for (seats in listOf(2, 4)) {
            var bisca = Bisca.newGame(seats, r)
            while (!bisca.handOver) { val st = bisca; timed { bisca = BiscaAi.step(st, Difficulty.HARD, r) } }
            var escova = Escova.newGame(seats, r)
            while (!escova.handOver) { val st = escova; timed { escova = EscovaAi.step(st, Difficulty.HARD, r) } }
        }
        var hearts = Hearts.newGame(r)
        while (!hearts.handOver) { val st = hearts; timed { hearts = HeartsAi.step(st, Difficulty.HARD, r) } }
        println("Slowest hard move: $worst ms")
        assertTrue("slowest move took $worst ms", worst < 1000)
    }

    // ----- Strength (opt-in: -Pbenchmarks=true) -----------------------------------------------

    @Test
    fun levelsFeelDifferent() {
        org.junit.Assume.assumeTrue(System.getProperty("benchmarks") == "true")
        val pairs = listOf(Difficulty.MEDIUM to Difficulty.EASY, Difficulty.HARD to Difficulty.EASY, Difficulty.HARD to Difficulty.MEDIUM)
        for ((a, b) in pairs) {
            val n = 150
            var sueca = 0
            var bisca = 0
            var hearts = 0.0
            var escova = 0
            repeat(n) { g ->
                val r = Random(500 + g)
                var su = Sueca.newGame(r)
                while (!su.handOver) su = SuecaAi.step(su, if (Sueca.team(su.turn) == g % 2) a else b, r)
                sueca += su.result!!.points[g % 2]
                var bi = Bisca.newGame(2, r)
                while (!bi.handOver) bi = BiscaAi.step(bi, if (bi.turn == g % 2) a else b, r)
                bisca += bi.result!!.points[g % 2]
                var he = Hearts.newGame(r)
                val me = g % 4
                while (!he.handOver) he = HeartsAi.step(he, if (Hearts.actor(he).let { if (it < 0) he.turn else it } == me) a else b, r)
                hearts += he.handPoints!![me] - (he.handPoints!!.sum() - he.handPoints!![me]) / 3.0
                var es = Escova.newGame(2, r)
                while (!es.handOver) es = EscovaAi.step(es, if (es.turn == g % 2) a else b, r)
                escova += es.result!!.points[g % 2] - es.result!!.points[1 - g % 2]
            }
            println("BENCHMARK $a vs $b: sueca ${sueca / n} of 120, bisca ${bisca / n} of 120, hearts ${"%.1f".format(hearts / n)} points vs others, escova ${"%.2f".format(escova.toDouble() / n)} margin")
        }
    }
}
