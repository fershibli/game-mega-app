package io.github.fershibli.megagames.games.tricks

import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import kotlin.random.Random

/**
 * What one seat knows when it is its turn in Sueca or Bisca: its own cards, the cards already
 * played (including the trick on the table), the suits others have shown they lack and how many
 * cards are left to draw. Nothing about the hidden hands.
 */
class Knowledge(
    val seat: Int,
    val seats: Int,
    val hand: List<Card>,
    val legal: List<Card>,
    val trick: List<Play>,
    val trump: Suit,
    val played: Set<Card>,
    val voids: List<Set<Suit>>,
    val stockLeft: Int = 0,
)

/**
 * A sensible card for Sueca and Bisca, used by the Medium computer, as the policy of the Hard
 * computer's imagined deals and for the Hint.
 *
 * Every legal card gets a value: the chance that our side takes the trick after it (sure when we
 * are last or hold the top card left in the suit, a guess otherwise) times the points at stake,
 * minus what the card would still be worth later (trumps and aces are precious). So it:
 * - never overtakes a partner who is already winning the trick safely, and loads it with points
 *   instead ("carregar": an ace or seven of another suit on the partner's sure trick);
 * - takes points as cheaply as possible, and does not spend trumps on empty tricks;
 * - throws its smallest card when the trick is lost;
 * - leads aces and sevens that are sure winners, and small cards otherwise (in Bisca, while there
 *   are cards to draw, it does not lead aces the other side could trump for free).
 */
object AceTenSense {
    private fun pts(c: Card) = AceTen.points(c)
    private fun pw(c: Card) = AceTen.power(c)

    fun teamOf(seat: Int, seats: Int) = if (seats == 4) seat % 2 else seat

    /** True when no card of [c]'s suit stronger than [c] is still out of sight of [k]'s seat. */
    fun isTop(k: Knowledge, c: Card): Boolean =
        AceTen.RANKS.none { r -> val o = Card(c.suit, r); pw(o) > pw(c) && o !in k.played && o !in k.hand }

    private fun beats(c: Card, best: Card, led: Suit, trump: Suit): Boolean = when {
        c.suit == best.suit -> pw(c) > pw(best)
        c.suit == trump -> true
        else -> false
    } && (c.suit == led || c.suit == trump)

    /** Value of keeping [c] for later. */
    private fun keep(k: Knowledge, c: Card): Double = when {
        c.suit == k.trump -> 4.0 + pw(c) + pts(c)
        pts(c) >= 10 && isTop(k, c) -> 6.0
        pts(c) >= 10 -> 2.0
        else -> pw(c) * 0.1
    }

    /** True when an opponent still to play in this trick may lack [suit] (and so could trump). */
    private fun opponentMayCut(k: Knowledge, suit: Suit, after: Int): Boolean {
        if (suit == k.trump) return false
        val me = teamOf(k.seat, k.seats)
        for (i in 1..after) {
            val seat = (k.seat + i) % k.seats
            if (teamOf(seat, k.seats) != me && suit in k.voids[seat]) return true
        }
        return false
    }

    fun value(k: Knowledge, c: Card): Double {
        val me = teamOf(k.seat, k.seats)
        val after = k.seats - 1 - k.trick.size
        val mustFollowLater = k.stockLeft == 0
        if (k.trick.isEmpty()) {
            // Leading.
            val top = isTop(k, c)
            var q = when {
                top && c.suit == k.trump -> 0.95
                top && mustFollowLater && !opponentMayCut(k, c.suit, after) -> 0.85
                top -> 0.4
                else -> if (k.seats == 4) 0.4 else 0.25
            }
            if (!mustFollowLater && c.suit != k.trump && pts(c) >= 10) q = minOf(q, 0.35)
            var keepValue = keep(k, c)
            // With many trumps, drawing the others' trumps out is worth it.
            if (c.suit == k.trump && mustFollowLater && k.hand.count { it.suit == k.trump } >= 4) keepValue *= 0.4
            return (2 * q - 1) * (pts(c) + 3) - keepValue
        }
        val led = k.trick.first().card.suit
        val cur = Tricks.winning(k.trick, k.trump, ::pw)
        val trickPts = k.trick.sumOf { pts(it.card) }
        val partnerWinning = teamOf(cur.seat, k.seats) == me
        val q = if (beats(c, cur.card, led, k.trump)) {
            when {
                partnerWinning -> 1.0
                after == 0 -> 1.0
                isTop(k, c) && !opponentMayCut(k, led, after) -> 0.95
                c.suit == k.trump && led != k.trump -> 0.7
                else -> 0.45
            }
        } else if (partnerWinning) {
            val safe = after == 0 || ((cur.card.suit == k.trump || !opponentMayCut(k, led, after)) && isTop(k, cur.card))
            if (safe) 1.0 else 0.55
        } else {
            // A partner still to play might take it back.
            if (k.seats == 4 && after == 2) 0.25 else 0.0
        }
        // Overtaking a partner who already wins wastes a strong card: count it as kept value lost.
        val waste = if (partnerWinning && beats(c, cur.card, led, k.trump)) 1.5 else 0.0
        return (2 * q - 1) * (trickPts + pts(c)) - keep(k, c) - waste
    }

    fun choose(k: Knowledge): Card {
        if (k.legal.size == 1) return k.legal[0]
        return k.legal.maxWith(compareBy<Card>({ value(k, it) }, { -pw(it) }, { it.suit.ordinal }))
    }

    /** Easy: a random legal card now and then, otherwise the sensible one. */
    fun casual(k: Knowledge, random: Random): Card =
        if (random.nextFloat() < 0.6f) k.legal.random(random) else choose(k)
}
