package io.github.fershibli.megagames.games.tricks

import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Shared pieces of the trick-taking card games (Sueca, Bisca, Hearts) and of Escova: a card
 * played by a seat, who wins a trick, following suit, and dealing imagined hands for the
 * computer players (which never look at hidden cards).
 */
@Serializable
data class Play(val seat: Int, val card: Card)

/**
 * The 40-card deck of Sueca and Bisca (no 8, 9, 10): in each suit A > 7 > K > J > Q > 6 > 5 >
 * 4 > 3 > 2, worth A 11, 7 10, K 4, J 3, Q 2 and nothing for the rest: 120 points in all.
 */
object AceTen {
    val RANKS = listOf(1, 2, 3, 4, 5, 6, 7, 11, 12, 13)
    const val TOTAL = 120

    /** Every card once, in a fixed order. */
    val CARDS: List<Card> = Suit.entries.flatMap { s -> RANKS.map { Card(s, it) } }

    fun deck(random: Random): List<Card> = CARDS.shuffled(random)

    /** Strength inside a suit: 2 is 0, ..., Q 5, J 6, K 7, 7 8, A 9. */
    fun power(card: Card): Int = when (card.rank) {
        1 -> 9
        7 -> 8
        13 -> 7
        11 -> 6
        12 -> 5
        else -> card.rank - 2
    }

    fun points(card: Card): Int = when (card.rank) {
        1 -> 11
        7 -> 10
        13 -> 4
        11 -> 3
        12 -> 2
        else -> 0
    }

    fun points(cards: Iterable<Card>): Int = cards.sumOf { points(it) }
}

object Tricks {
    /** Suits in hand order: alternating colors. */
    val SUIT_ORDER = listOf(Suit.SPADES, Suit.HEARTS, Suit.CLUBS, Suit.DIAMONDS)

    /** The play that wins [trick]: the strongest trump, otherwise the strongest card of the suit led. */
    fun winning(trick: List<Play>, trump: Suit?, power: (Card) -> Int): Play {
        val led = trick.first().card.suit
        var best = trick.first()
        for (pl in trick.drop(1)) {
            val c = pl.card
            val b = best.card
            val beats = when {
                c.suit == b.suit -> power(c) > power(b)
                trump != null && c.suit == trump -> true
                else -> false
            }
            if (beats && (c.suit == led || c.suit == trump)) best = pl
        }
        return best
    }

    /** Cards of [hand] that may go on [trick] when following suit is compulsory. */
    fun following(hand: List<Card>, trick: List<Play>): List<Card> {
        if (trick.isEmpty()) return hand
        val led = trick.first().card.suit
        return hand.filter { it.suit == led }.ifEmpty { hand }
    }

    /** Suits each seat has shown it no longer holds (it did not follow when it had to). */
    fun voids(seats: Int, tricks: List<List<Play>>): List<Set<Suit>> {
        val out = List(seats) { HashSet<Suit>() }
        for (t in tricks) {
            if (t.isEmpty()) continue
            val led = t.first().card.suit
            for (pl in t.drop(1)) if (pl.card.suit != led) out[pl.seat] += led
        }
        return out
    }

    /**
     * Shares [unseen] cards among the seats as an imagined deal: seat i gets [sizes][i] cards,
     * avoiding the suits in [voids] when possible. Used only by the computer players, which
     * imagine many such deals instead of looking at the real hidden cards.
     */
    fun imagine(unseen: List<Card>, sizes: IntArray, voids: List<Set<Suit>>, random: Random): List<MutableList<Card>> {
        val total = sizes.sum()
        for (attempt in 0 until 12) {
            val hands = List(sizes.size) { ArrayList<Card>(sizes[it]) }
            val pool = unseen.shuffled(random).take(total)
            // Cards that fewer seats can hold go first.
            val ordered = pool.sortedBy { c -> sizes.indices.count { sizes[it] > 0 && c.suit !in voids[it] } }
            var ok = true
            for (c in ordered) {
                val open = sizes.indices.filter { hands[it].size < sizes[it] && c.suit !in voids[it] }
                if (open.isEmpty()) { ok = false; break }
                // Prefer the seat with the most room left, so nobody is left with only impossible cards.
                val room = open.maxOf { sizes[it] - hands[it].size }
                val best = open.filter { sizes[it] - hands[it].size == room }
                hands[best[random.nextInt(best.size)]] += c
            }
            if (ok) return hands
        }
        // Constraints could not be met (should not happen): deal ignoring them.
        val pool = unseen.shuffled(random)
        var k = 0
        return sizes.map { n -> MutableList(n) { pool[k++] } }
    }

    /** [hand] sorted by suit (alternating colors, [first] suit at the front) and then by [power], strongest first. */
    fun sorted(hand: List<Card>, power: (Card) -> Int, first: Suit? = null): List<Card> {
        val order = if (first == null) SUIT_ORDER else listOf(first) + SUIT_ORDER.filter { it != first }
        return hand.sortedWith(compareBy<Card>({ order.indexOf(it.suit) }, { -power(it) }))
    }
}
