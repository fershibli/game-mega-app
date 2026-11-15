package io.github.fershibli.megagames.games.bisca

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.tricks.AceTen
import io.github.fershibli.megagames.games.tricks.AceTenSense
import io.github.fershibli.megagames.games.tricks.Knowledge
import io.github.fershibli.megagames.games.tricks.Tricks
import kotlin.random.Random

/**
 * The computer players of Bisca. Like a person at the table they know their own cards, the cards
 * played, the turned trump card under the stock and, once following suit is compulsory, which
 * suits a player has shown they lack. Never the hidden cards or the order of the stock.
 * - Easy: often plays a random card.
 * - Medium: [AceTenSense]: small cards on empty tricks, takes aces and sevens as cheaply as it can,
 *   does not lead aces that could be trumped for free, and helps its partner in pairs.
 * - Hard: imagines many orders of the unseen cards and plays every card to the end of the hand with
 *   the Medium policy, keeping the one that brings the most points.
 */
object BiscaAi {
    fun knowledge(s: BiscaState, seat: Int = s.turn): Knowledge {
        val played = HashSet<Card>()
        s.tricks.forEach { t -> t.forEach { played += it.card } }
        s.trick.forEach { played += it.card }
        val legal = if (seat == s.turn) Bisca.legal(s) else s.hands[seat]
        return Knowledge(seat, s.seats, s.hands[seat], legal, s.trick, s.trump, played, voids(s), s.stock.size)
    }

    /** Lacking a suit only shows once following it is compulsory. */
    private fun voids(s: BiscaState) =
        Tricks.voids(s.seats, (s.tricks + listOf(s.trick)).drop(Bisca.freeTricks(s.seats)))

    fun sensible(s: BiscaState): Card = AceTenSense.choose(knowledge(s))

    fun choose(s: BiscaState, d: Difficulty, random: Random): Card {
        val k = knowledge(s)
        return when (d) {
            Difficulty.EASY -> AceTenSense.casual(k, random)
            Difficulty.MEDIUM -> AceTenSense.choose(k)
            Difficulty.HARD -> if (k.legal.size == 1) k.legal[0] else search(s, k, random)
        }
    }

    private fun search(s: BiscaState, k: Knowledge, random: Random): Card {
        val team = Bisca.team(s.turn)
        val samples = if (s.seats == 2) 120 else 90
        val totals = DoubleArray(k.legal.size)
        val fallback = AceTenSense.choose(k)
        repeat(samples) {
            val world = imagine(s, s.turn, random)
            k.legal.forEachIndexed { i, card -> totals[i] += playOut(Bisca.play(world, card)!!, team) }
        }
        val best = k.legal.indices.maxBy { totals[it] + if (k.legal[it] == fallback) 0.5 * samples else 0.0 }
        return k.legal[best]
    }

    /** The other hands and the stock replaced by an imagined shuffle of the unseen cards. */
    fun imagine(s: BiscaState, seat: Int, random: Random): BiscaState {
        val seen = HashSet<Card>(s.hands[seat])
        s.tricks.forEach { t -> t.forEach { seen += it.card } }
        s.trick.forEach { seen += it.card }
        val trumpCard = s.trumpCard
        val trumpInStock = trumpCard != null && s.stock.isNotEmpty() && s.stock.last() == trumpCard
        if (trumpInStock) seen += trumpCard!!
        val unseen = AceTen.CARDS.filter { it !in seen }
        val stockHidden = s.stock.size - if (trumpInStock) 1 else 0
        // One more "seat" for the hidden part of the stock, which may hold any suit.
        val sizes = IntArray(s.seats + 1) { i -> if (i == s.seats) stockHidden else if (i == seat) 0 else s.hands[i].size }
        val voids = voids(s) + listOf(emptySet())
        val dealt = Tricks.imagine(unseen, sizes, voids, random)
        val hands = List(s.seats) { i -> if (i == seat) s.hands[i] else dealt[i] }
        val stock = if (trumpInStock) dealt[s.seats] + trumpCard!! else dealt[s.seats]
        return s.copy(hands = hands, stock = stock)
    }

    private fun playOut(start: BiscaState, team: Int): Double {
        var st = start
        while (true) {
            if (st.trickComplete) {
                st = Bisca.collect(st)
                if (st.handOver) break
                continue
            }
            st = Bisca.play(st, AceTenSense.choose(knowledge(st))) ?: break
        }
        return st.result?.points?.get(team)?.toDouble() ?: 0.0
    }

    fun step(s: BiscaState, d: Difficulty, random: Random): BiscaState = when {
        s.handOver || s.finished -> s
        s.trickComplete -> Bisca.collect(s)
        else -> Bisca.play(s, choose(s, d, random)) ?: s
    }
}
