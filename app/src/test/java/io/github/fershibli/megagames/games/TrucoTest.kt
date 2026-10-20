package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.truco.Played
import io.github.fershibli.megagames.games.truco.Shout
import io.github.fershibli.megagames.games.truco.Truco
import io.github.fershibli.megagames.games.truco.TrucoAi
import io.github.fershibli.megagames.games.truco.TrucoRules
import io.github.fershibli.megagames.games.truco.TrucoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TrucoTest {
    private fun h(r: Int) = Card(Suit.HEARTS, r)
    private fun s(r: Int) = Card(Suit.SPADES, r)
    private fun c(r: Int) = Card(Suit.CLUBS, r)
    private fun d(r: Int) = Card(Suit.DIAMONDS, r)

    @Test
    fun deckHasFortyCardsWithout8To10() {
        val deck = Truco.deck(Random(1))
        assertEquals(40, deck.toSet().size)
        assertTrue(deck.none { it.rank in 8..10 })
    }

    @Test
    fun paulistaManilhasFollowTheVira() {
        val p = TrucoRules.PAULISTA
        // Vira 7: the queen is the manilha; vira 3 (the top): the 4 is.
        assertEquals(12, Truco.manilhaRank(h(7)))
        assertEquals(4, Truco.manilhaRank(s(3)))
        assertEquals(11, Truco.manilhaRank(d(12)))
        val vira = h(5)
        val order = listOf(c(6), h(6), s(6), d(6), s(3), d(2), c(1), h(13), s(11), d(12), c(7), h(5), s(4))
        for (i in 0 until order.size - 1) assertTrue("${order[i]} > ${order[i + 1]}", Truco.strength(order[i], p, vira) > Truco.strength(order[i + 1], p, vira))
        // Ordinary cards of the same rank tie.
        assertEquals(Truco.strength(s(3), p, vira), Truco.strength(h(3), p, vira))
    }

    @Test
    fun mineiroHasFixedManilhas() {
        val m = TrucoRules.MINEIRO
        val order = listOf(c(4), h(7), s(1), d(7), h(3), c(2), d(1), s(13), h(11), c(12), s(7), d(6), h(5), s(4))
        for (i in 0 until order.size - 1) assertTrue("${order[i]} > ${order[i + 1]}", Truco.strength(order[i], m, null) > Truco.strength(order[i + 1], m, null))
        val st = Truco.newGame(m, 2, Random(4))
        assertNull(st.vira)
        assertEquals(listOf(3, 3), st.hands.map { it.size })
    }

    @Test
    fun handResults() {
        assertNull(Truco.handResult(listOf(0)))
        assertEquals(0, Truco.handResult(listOf(0, 0)))
        assertNull(Truco.handResult(listOf(0, 1)))
        assertEquals(1, Truco.handResult(listOf(0, 1, 1)))
        // A tie in the first trick: the second decides.
        assertEquals(1, Truco.handResult(listOf(-1, 1)))
        // A tie after the first: the first decides.
        assertEquals(0, Truco.handResult(listOf(0, -1)))
        assertEquals(1, Truco.handResult(listOf(1, 0, -1)))
        // Two ties: the third decides; three ties: nobody.
        assertNull(Truco.handResult(listOf(-1, -1)))
        assertEquals(0, Truco.handResult(listOf(-1, -1, 0)))
        assertEquals(-1, Truco.handResult(listOf(-1, -1, -1)))
    }

    private fun handOf(you: List<Card>, them: List<Card>, rules: TrucoRules = TrucoRules.MINEIRO, score: List<Int> = listOf(0, 0)) =
        Truco.deal(TrucoState(variant = rules.key, seats = 2, dealer = 1, score = score), Random(1)).copy(hands = listOf(you, them), vira = null)

    @Test
    fun tricksAndTheLead() {
        var st = handOf(listOf(c(4), h(3), s(5)), listOf(h(7), d(2), d(6)))
        st = Truco.play(st, h(3))!!
        assertEquals(1, st.turn)
        st = Truco.play(st, d(2))!!
        assertTrue(st.trickComplete)
        st = Truco.collect(st)
        assertEquals(listOf(0), st.tricks)
        assertEquals(0, st.turn) // the winner leads
        st = Truco.play(st, c(4))!!
        st = Truco.collect(Truco.play(st, h(7))!!)
        assertEquals(0, st.handWinner)
        assertEquals(listOf(1, 0), st.score)
    }

    @Test
    fun trucoAndRaises() {
        var st = handOf(listOf(c(4), h(3), s(5)), listOf(h(7), d(2), d(6)))
        st = Truco.raise(st)!!
        assertEquals(3, st.call!!.value)
        assertEquals(1, Truco.actor(st)) // the computer answers
        assertEquals(Shout.TRUCO, st.shout!!.shout)
        // Accepted: worth 3, and you cannot raise again yourself.
        val accepted = Truco.answer(st, Truco.Answer.ACCEPT)!!
        assertEquals(3, accepted.value)
        assertFalse(Truco.canRaise(accepted, 0))
        // Raised back to six: you answer.
        val six = Truco.answer(st, Truco.Answer.RAISE)!!
        assertEquals(6, six.call!!.value)
        assertEquals(3, six.value)
        assertEquals(0, Truco.actor(six))
        // You run from the six: they score the 3 already accepted.
        val ran = Truco.answer(six, Truco.Answer.RUN)!!
        assertEquals(1, ran.handWinner)
        assertEquals(listOf(0, 3), ran.score)
        // Refusing the first truco gives the caller 1 point.
        val refused = Truco.answer(st, Truco.Answer.RUN)!!
        assertEquals(listOf(1, 0), refused.score)
        // The ladder ends at twelve.
        assertEquals(listOf(3, 6, 9, 12), listOf(1, 3, 6, 9).map(Truco::nextValue))
    }

    @Test
    fun handOfEleven() {
        val st = handOf(listOf(c(4), h(3), s(5)), listOf(h(7), d(2), d(6)), score = listOf(11, 5))
        assertTrue(st.elevenPending && st.noRaise)
        assertEquals(0, Truco.actor(st))
        assertNull(Truco.play(st, c(4))) // decide first
        val playing = Truco.elevenChoice(st, true)
        assertEquals(3, playing.value)
        assertFalse(Truco.canRaise(playing, 0))
        assertNull(Truco.raise(playing))
        val ran = Truco.elevenChoice(st, false)
        assertEquals(listOf(11, 6), ran.score)
        // Winning the hand of eleven wins the game.
        var won = playing
        won = Truco.collect(Truco.play(won, h(3))!!.let { Truco.play(it, d(2))!! })
        won = Truco.collect(Truco.play(won, c(4))!!.let { Truco.play(it, h(7))!! })
        assertTrue(won.finished)
        assertEquals(12, won.score[0])
        // Both sides at 11: no choice, no truco.
        val iron = handOf(listOf(c(4), h(3), s(5)), listOf(h(7), d(2), d(6)), score = listOf(11, 11))
        assertFalse(iron.elevenPending)
        assertTrue(iron.noRaise)
    }

    @Test
    fun partnersTieIsStillTheirTrick() {
        val st = Truco.newGame(TrucoRules.MINEIRO, 4, Random(2)).copy(
            table = listOf(Played(0, h(3)), Played(1, d(2)), Played(2, s(3)), Played(3, c(6))),
        )
        assertEquals(0, Truco.trickWinner(st, st.table))
        val tie = st.copy(table = listOf(Played(0, h(3)), Played(1, d(3)), Played(2, s(5)), Played(3, c(6))))
        assertEquals(-1, Truco.trickWinner(tie, tie.table))
    }

    @Test
    fun savedGamesRoundTrip() {
        val st = Truco.raise(Truco.newGame(TrucoRules.PAULISTA, 4, Random(3)))!!
        assertEquals(st, AppJson.decodeFromString(TrucoState.serializer(), AppJson.encodeToString(TrucoState.serializer(), st)))
    }

    /** Plays a whole game with the computer in every seat (you included). */
    private fun playOut(rules: TrucoRules, seats: Int, level: (Int) -> Difficulty, r: Random, onStep: (Long) -> Unit = {}): TrucoState {
        var st = Truco.newGame(rules, seats, r)
        var steps = 0
        while (!st.finished && steps < 5000) {
            st = if (st.handOver) Truco.nextHand(st, r) else {
                val actor = Truco.actor(st)
                val t0 = System.nanoTime()
                val next = TrucoAi.step(st, level(if (actor < 0) 0 else actor), r)
                onStep(System.nanoTime() - t0)
                next
            }
            // Every dealt card is in a hand, on the table or in a finished trick.
            assertEquals(3 * seats, st.hands.sumOf { it.size } + st.table.size + st.history.sumOf { it.size })
            steps++
        }
        assertTrue("game did not finish", st.finished)
        return st
    }

    @Test
    fun computerGamesFinishAndEachDecisionIsFast() {
        var slowest = 0L
        for (rules in TrucoRules.entries) for (seats in listOf(2, 4)) for (level in Difficulty.entries) {
            val st = playOut(rules, seats, { level }, Random(seats * 7 + level.ordinal)) { slowest = maxOf(slowest, it) }
            assertTrue(st.score.max() == Truco.TARGET)
        }
        assertTrue("slowest decision ${slowest / 1_000_000} ms", slowest < 500_000_000L)
    }

    @Test
    fun hardBeatsEasy() {
        var hardWins = 0
        val games = 16
        repeat(games) { g ->
            val hardTeam = g % 2
            val st = playOut(TrucoRules.PAULISTA, 2, { seat -> if (Truco.team(seat) == hardTeam) Difficulty.HARD else Difficulty.EASY }, Random(900 + g))
            if (st.score[hardTeam] > st.score[1 - hardTeam]) hardWins++
        }
        assertTrue("hard won $hardWins of $games", hardWins > games / 2)
    }

    @Test
    fun theComputerAnswersAndBluffsSometimes() {
        // With the zap and copas the hard computer accepts or raises a truco.
        var st = handOf(listOf(s(5), d(6), h(4)), listOf(c(4), h(7), h(3)))
        st = Truco.raise(st)!!
        val answered = TrucoAi.step(st, Difficulty.HARD, Random(1))
        assertNotNull(answered.call ?: answered.takeIf { it.value == 3 })
        // With nothing at all it runs.
        var weak = handOf(listOf(c(4), h(7), h(3)), listOf(s(5), d(6), h(4)))
        weak = Truco.raise(weak)!!
        assertEquals(0, TrucoAi.step(weak, Difficulty.HARD, Random(1)).handWinner)
    }
}
