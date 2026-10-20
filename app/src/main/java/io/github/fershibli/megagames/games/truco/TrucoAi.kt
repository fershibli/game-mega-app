package io.github.fershibli.megagames.games.truco

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import kotlin.random.Random

/**
 * The computer players of Truco (opponents and, in 2 vs 2, your partner).
 *
 * The computer never sees hidden cards. To judge its hand it imagines many possible deals of the
 * cards it has not seen, plays each one out with a simple sensible policy and counts how often
 * its side wins ("win chance"). It asks for truco when that chance is high, accepts or raises
 * according to it, and sometimes bluffs.
 *
 * - Easy: imagines few deals, plays a random card now and then, misjudges answers and bluffs
 *   rarely and at bad moments.
 * - Medium: plays sensibly (wins the trick as cheaply as possible, saves cards when the partner
 *   is winning) and answers by the win chance.
 * - Hard: also tries each card against the imagined deals before playing, knows that a refusal
 *   which would end the game is not worth it and bluffs more cleverly.
 */
object TrucoAi {
    private fun str(s: TrucoState, c: Card) = Truco.strength(s, c)

    /** A simple, sensible card for [seat] (also used to play out imagined deals). */
    fun greedyCard(s: TrucoState, seat: Int): Card {
        val hand = s.hands[seat]
        val byStrength = hand.sortedBy { str(s, it) }
        val weakest = byStrength.first()
        val strongest = byStrength.last()
        val myTeam = Truco.team(seat)
        if (s.table.isEmpty()) {
            // First trick: with two big cards, lead the smaller one and keep the best.
            if (s.tricks.isEmpty() && byStrength.size >= 2 && str(s, byStrength[byStrength.size - 2]) >= 8) return byStrength[byStrength.size - 2]
            return strongest
        }
        val best = s.table.maxOf { str(s, it.card) }
        val bestTeams = s.table.filter { str(s, it.card) == best }.map { Truco.team(it.seat) }.toSet()
        if (bestTeams == setOf(myTeam)) return weakest
        byStrength.firstOrNull { str(s, it) > best }?.let { return it }
        val tie = byStrength.firstOrNull { str(s, it) == best }
        // A tie after winning the first trick wins the hand.
        if (tie != null && s.tricks.firstOrNull() == myTeam) return tie
        return weakest
    }

    /** Cards [seat] has not seen (its partner's are seen in a hand of eleven). */
    private fun unseen(s: TrucoState, seat: Int, seesPartner: Boolean): MutableList<Card> {
        val seen = HashSet<Card>()
        seen += s.hands[seat]
        if (seesPartner && s.seats == 4) seen += s.hands[(seat + 2) % 4]
        s.vira?.let { seen += it }
        s.table.forEach { seen += it.card }
        s.history.forEach { t -> t.forEach { seen += it.card } }
        return Truco.CARDS.filterTo(ArrayList()) { it !in seen }
    }

    /** Plays the rest of the hand with [greedyCard] for everybody; returns 1, 0.5 (nobody) or 0. */
    private fun playOut(start: TrucoState, team: Int): Double {
        var st = start
        var guard = 0
        while (!st.handOver && guard++ < 40) {
            st = if (st.trickComplete) Truco.collect(st) else Truco.play(st, greedyCard(st, st.turn)) ?: break
        }
        return when (st.handWinner) {
            team -> 1.0
            -1, null -> 0.5
            else -> 0.0
        }
    }

    /** Imagines [samples] deals of the unseen cards; [first] is played by [seat] before playing out. */
    fun winChance(s: TrucoState, seat: Int, samples: Int, random: Random, seesPartner: Boolean = false, first: Card? = null): Double {
        if (s.handOver) return if (s.handWinner == Truco.team(seat)) 1.0 else 0.0
        val pool = unseen(s, seat, seesPartner)
        val base = s.copy(call = null, elevenPending = false, noRaise = true, shout = null)
        var total = 0.0
        repeat(samples) {
            pool.shuffle(random)
            var k = 0
            val hands = s.hands.mapIndexed { i, h ->
                if (i == seat || (seesPartner && s.seats == 4 && i == (seat + 2) % 4)) h else List(h.size) { pool[k++] }
            }
            var st = base.copy(hands = hands)
            if (first != null) st = Truco.play(st, first) ?: st
            total += playOut(st, Truco.team(seat))
        }
        return total / samples
    }

    private fun samples(d: Difficulty) = when (d) {
        Difficulty.EASY -> 30
        Difficulty.MEDIUM -> 120
        Difficulty.HARD -> 200
    }

    fun chooseCard(s: TrucoState, d: Difficulty, random: Random): Card {
        val seat = s.turn
        val hand = s.hands[seat]
        return when (d) {
            Difficulty.EASY -> if (random.nextFloat() < 0.35f) hand.random(random) else greedyCard(s, seat)
            Difficulty.MEDIUM -> greedyCard(s, seat)
            Difficulty.HARD -> if (hand.size == 1) hand[0] else hand.maxBy { winChance(s, seat, 70, random, first = it) + if (it == greedyCard(s, seat)) 0.02 else 0.0 }
        }
    }

    private fun wantsRaise(s: TrucoState, d: Difficulty, random: Random): Boolean {
        val seat = s.turn
        val p = winChance(s, seat, samples(d), random)
        val wonFirst = s.tricks.firstOrNull() == Truco.team(seat)
        return when (d) {
            Difficulty.EASY -> (p > 0.8 && random.nextFloat() < 0.5f) || random.nextFloat() < 0.04f
            Difficulty.MEDIUM -> p > 0.75 || (p > 0.25 && random.nextFloat() < 0.07f)
            Difficulty.HARD -> p > 0.68 || ((wonFirst || s.tricks.isEmpty()) && p > 0.35 && random.nextFloat() < 0.12f)
        }
    }

    private fun answer(s: TrucoState, d: Difficulty, random: Random): Truco.Answer {
        val call = s.call ?: return Truco.Answer.ACCEPT
        val seat = Truco.responder(s)
        val p = winChance(s, seat, samples(d), random)
        val runningLoses = s.score[call.team] + s.value >= Truco.TARGET
        val canRaise = call.value < Truco.TARGET
        return when (d) {
            Difficulty.EASY -> {
                val noisy = p + (random.nextDouble() - 0.5) * 0.3
                when {
                    canRaise && noisy > 0.92 -> Truco.Answer.RAISE
                    noisy > 0.35 -> Truco.Answer.ACCEPT
                    else -> Truco.Answer.RUN
                }
            }
            Difficulty.MEDIUM -> when {
                canRaise && p > 0.85 -> Truco.Answer.RAISE
                p > 0.45 || runningLoses -> Truco.Answer.ACCEPT
                else -> Truco.Answer.RUN
            }
            Difficulty.HARD -> when {
                canRaise && (p > 0.8 || (p > 0.55 && random.nextFloat() < 0.06f)) -> Truco.Answer.RAISE
                p > 0.42 || runningLoses -> Truco.Answer.ACCEPT
                else -> Truco.Answer.RUN
            }
        }
    }

    private fun playsEleven(s: TrucoState, d: Difficulty, random: Random): Boolean {
        val seat = Truco.actor(s)
        val p = winChance(s, seat, samples(d), random, seesPartner = true)
        return when (d) {
            Difficulty.EASY -> p > 0.3 + random.nextDouble() * 0.4
            Difficulty.MEDIUM -> p > 0.5
            Difficulty.HARD -> p > 0.45
        }
    }

    /** One decision of the computer seat that must act now (or collects a finished trick). */
    fun step(s: TrucoState, d: Difficulty, random: Random): TrucoState = when {
        s.finished || s.handOver -> s
        s.trickComplete -> Truco.collect(s)
        s.elevenPending -> Truco.elevenChoice(s, playsEleven(s, d, random))
        s.call != null -> Truco.answer(s, answer(s, d, random)) ?: Truco.answer(s, Truco.Answer.ACCEPT)!!
        Truco.canRaise(s, s.turn) && wantsRaise(s, d, random) -> Truco.raise(s)!!
        else -> Truco.play(s, chooseCard(s, d, random))!!
    }
}
