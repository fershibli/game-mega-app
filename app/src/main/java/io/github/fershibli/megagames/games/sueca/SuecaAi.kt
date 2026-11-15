package io.github.fershibli.megagames.games.sueca

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.tricks.AceTen
import io.github.fershibli.megagames.games.tricks.AceTenSense
import io.github.fershibli.megagames.games.tricks.Knowledge
import io.github.fershibli.megagames.games.tricks.Tricks
import kotlin.random.Random

/**
 * The computer players of Sueca (the two opponents and your partner).
 *
 * They never look at hidden cards: they know their own hand, the cards played, the turned trump
 * card (still in the dealer's hand until played) and which suits a player has shown they lack.
 * - Easy: often plays a random legal card.
 * - Medium: [AceTenSense]: covers the partner's tricks with points, never overtakes a partner who
 *   wins safely, takes points cheaply and keeps its trumps.
 * - Hard: imagines many deals of the unseen cards and plays each legal card to the end of the hand
 *   with the Medium policy, keeping the card that brings its side the most points.
 */
object SuecaAi {
    fun knowledge(s: SuecaState, seat: Int = s.turn): Knowledge {
        val played = HashSet<Card>()
        s.tricks.forEach { t -> t.forEach { played += it.card } }
        s.trick.forEach { played += it.card }
        val legal = if (seat == s.turn) Sueca.legal(s) else s.hands[seat]
        return Knowledge(seat, Sueca.SEATS, s.hands[seat], legal, s.trick, s.trump, played, Tricks.voids(Sueca.SEATS, s.tricks + listOf(s.trick)))
    }

    /** The Medium choice for the seat in turn (also the Hint for a person). */
    fun sensible(s: SuecaState): Card = AceTenSense.choose(knowledge(s))

    fun choose(s: SuecaState, d: Difficulty, random: Random): Card {
        val k = knowledge(s)
        return when (d) {
            Difficulty.EASY -> AceTenSense.casual(k, random)
            Difficulty.MEDIUM -> AceTenSense.choose(k)
            Difficulty.HARD -> if (k.legal.size == 1) k.legal[0] else search(s, k, random)
        }
    }

    private fun search(s: SuecaState, k: Knowledge, random: Random): Card {
        val seat = s.turn
        val team = Sueca.team(seat)
        val samples = 110
        val totals = DoubleArray(k.legal.size)
        val fallback = AceTenSense.choose(k)
        repeat(samples) {
            val world = imagine(s, seat, random)
            k.legal.forEachIndexed { i, card -> totals[i] += playOut(Sueca.play(world, card)!!, team) }
        }
        val best = k.legal.indices.maxBy { totals[it] + if (k.legal[it] == fallback) 0.5 * samples else 0.0 }
        return k.legal[best]
    }

    /** The real state with the other three hands replaced by an imagined deal of the unseen cards. */
    fun imagine(s: SuecaState, seat: Int, random: Random): SuecaState {
        val seen = HashSet<Card>(s.hands[seat])
        s.tricks.forEach { t -> t.forEach { seen += it.card } }
        s.trick.forEach { seen += it.card }
        // Everyone saw the turned card: while unplayed it is in the dealer's hand.
        val trumpCard = s.trumpCard
        val fixed = trumpCard != null && s.dealer != seat && trumpCard !in seen
        if (fixed) seen += trumpCard!!
        val unseen = AceTen.CARDS.filter { it !in seen }
        val sizes = IntArray(Sueca.SEATS) { i -> if (i == seat) 0 else s.hands[i].size - if (fixed && i == s.dealer) 1 else 0 }
        val voids = Tricks.voids(Sueca.SEATS, s.tricks + listOf(s.trick))
        val dealt = Tricks.imagine(unseen, sizes, voids, random)
        val hands = List(Sueca.SEATS) { i ->
            when {
                i == seat -> s.hands[i]
                fixed && i == s.dealer -> dealt[i] + trumpCard!!
                else -> dealt[i]
            }
        }
        return s.copy(hands = hands)
    }

    /** Plays the hand to the end with the Medium policy; returns [team]'s card points. */
    private fun playOut(start: SuecaState, team: Int): Double {
        var st = start
        while (true) {
            if (st.trickComplete) {
                st = Sueca.collect(st)
                if (st.handOver) break
                continue
            }
            st = Sueca.play(st, AceTenSense.choose(knowledge(st))) ?: break
        }
        return st.result?.points?.get(team)?.toDouble() ?: 0.0
    }

    /** One computer move: collects a full trick or plays a card. */
    fun step(s: SuecaState, d: Difficulty, random: Random): SuecaState = when {
        s.handOver || s.finished -> s
        s.trickComplete -> Sueca.collect(s)
        else -> Sueca.play(s, choose(s, d, random)) ?: s
    }
}
