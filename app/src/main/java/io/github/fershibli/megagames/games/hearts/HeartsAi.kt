package io.github.fershibli.megagames.games.hearts

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Decks
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.tricks.Tricks
import kotlin.random.Random

/**
 * The three computer players of Hearts. They see their own cards, the cards played, the cards
 * they passed (and so know who holds them) and which suits a player has shown they lack; never
 * the hidden cards.
 * - Easy: passes three random cards and often plays a random legal card.
 * - Medium: passes the Q♠, high spades and high hearts and empties a short suit; plays under the
 *   winning card when it can ("ducking"), drops the Q♠ and high hearts on others when it lacks
 *   the suit, and leads small spades to drive out the Q♠.
 * - Hard: passes like Medium; for each card imagines many deals of the unseen cards and plays the
 *   hand to the end with the Medium policy, keeping the card that costs it the fewest points.
 */
object HeartsAi {
    private val P = Hearts::power

    // ----- Passing ---------------------------------------------------------------------------

    /** How much [c] hurts in [hand]: the three worst are passed. */
    private fun danger(hand: List<Card>, c: Card): Int {
        val spades = hand.count { it.suit == Suit.SPADES }
        val sameSuit = hand.count { it.suit == c.suit }
        return when {
            c == Hearts.QUEEN_OF_SPADES -> if (spades >= 5) 12 else 100
            c.suit == Suit.SPADES && P(c) > 12 -> if (Hearts.QUEEN_OF_SPADES in hand || spades >= 5) 15 else 80
            c.suit == Suit.HEARTS -> P(c) * 3 + if (P(c) >= 11) 10 else 0
            c == Hearts.TWO_OF_CLUBS -> 0
            else -> P(c) * 2 + if (sameSuit <= 2) 12 else 0
        }
    }

    fun choosePass(s: HeartsState, seat: Int, d: Difficulty, random: Random): List<Card> {
        val hand = s.hands[seat]
        if (d == Difficulty.EASY) return hand.shuffled(random).take(Hearts.PASS)
        return hand.sortedByDescending { danger(hand, it) }.take(Hearts.PASS)
    }

    // ----- Playing ---------------------------------------------------------------------------

    private fun played(s: HeartsState): Set<Card> {
        val out = HashSet<Card>()
        s.tricks.forEach { t -> t.forEach { out += it.card } }
        s.trick.forEach { out += it.card }
        return out
    }

    /** A sensible card for the seat in turn (Medium, the Hint and the policy of imagined deals). */
    fun sensible(s: HeartsState, played: Set<Card> = played(s)): Card {
        val legal = Hearts.legal(s)
        if (legal.size == 1) return legal[0]
        val seat = s.turn
        val hand = s.hands[seat]
        val queenOut = Hearts.QUEEN_OF_SPADES !in played && Hearts.QUEEN_OF_SPADES !in hand
        if (s.trick.isEmpty()) {
            // Drive out the queen with small spades when it is safe for us.
            if (queenOut && hand.none { it.suit == Suit.SPADES && P(it) > 12 }) {
                legal.filter { it.suit == Suit.SPADES && P(it) < 12 }.maxByOrNull(P)?.let { return it }
            }
            // Otherwise the smallest card, from the shortest suit, never a high spade while the queen is out.
            return legal.minWith(compareBy<Card>(
                { if (it.suit == Suit.SPADES && P(it) >= 12) 1 else 0 },
                { if (it.suit == Suit.HEARTS) 1 else 0 },
                { P(it) },
                { c -> hand.count { it.suit == c.suit } },
            ))
        }
        val led = s.trick.first().card.suit
        val best = Hearts.winner(s.trick).card
        val last = s.trick.size == Hearts.SEATS - 1
        val points = s.trick.sumOf { Hearts.points(it.card) }
        if (legal.first().suit == led) {
            // First trick: nobody can give points, so get rid of the biggest club.
            if (s.tricks.isEmpty()) return legal.maxBy(P)
            val under = legal.filter { P(it) < P(best) }
            if (under.isNotEmpty()) return under.maxBy(P)
            // We must take it.
            if (last) {
                val safe = legal.filter { it != Hearts.QUEEN_OF_SPADES }
                return (safe.ifEmpty { legal }).maxBy(P)
            }
            if (points == 0 && led != Suit.SPADES) return legal.maxBy(P)
            return legal.filter { it != Hearts.QUEEN_OF_SPADES }.ifEmpty { legal }.minBy(P)
        }
        // We lack the suit: unload the worst card.
        if (Hearts.QUEEN_OF_SPADES in legal) return Hearts.QUEEN_OF_SPADES
        if (queenOut) legal.filter { it.suit == Suit.SPADES && P(it) > 12 }.maxByOrNull(P)?.let { return it }
        legal.filter { it.suit == Suit.HEARTS }.maxByOrNull(P)?.let { if (P(it) >= 9 || legal.all { c -> c.suit == Suit.HEARTS }) return it }
        return legal.maxWith(compareBy<Card>({ P(it) }, { c -> -hand.count { it.suit == c.suit } }))
    }

    fun choose(s: HeartsState, d: Difficulty, random: Random): Card {
        val legal = Hearts.legal(s)
        return when (d) {
            Difficulty.EASY -> if (random.nextFloat() < 0.45f) legal.random(random) else sensible(s)
            Difficulty.MEDIUM -> sensible(s)
            Difficulty.HARD -> if (legal.size == 1) legal[0] else search(s, legal, random)
        }
    }

    private fun search(s: HeartsState, legal: List<Card>, random: Random): Card {
        val seat = s.turn
        val samples = maxOf(30, 360 / legal.size)
        val totals = DoubleArray(legal.size)
        val fallback = sensible(s)
        repeat(samples) {
            val world = imagine(s, seat, random)
            legal.forEachIndexed { i, card -> totals[i] += playOut(Hearts.play(world, card)!!, seat) }
        }
        val best = legal.indices.maxBy { totals[it] + if (legal[it] == fallback) 0.3 * samples else 0.0 }
        return legal[best]
    }

    /** The other hands replaced by an imagined deal of the cards [seat] has not seen. */
    fun imagine(s: HeartsState, seat: Int, random: Random): HeartsState {
        val seen = HashSet<Card>(s.hands[seat])
        s.tricks.forEach { t -> t.forEach { seen += it.card } }
        s.trick.forEach { seen += it.card }
        // The cards we passed are still with the seat we gave them to, unless played.
        val gaveTo = if (s.direction != PassDirection.NONE) Hearts.target(s, seat) else -1
        val given = s.passing[seat].orEmpty().filter { it !in seen }
        seen += given
        val unseen = ALL.filter { it !in seen }
        val sizes = IntArray(Hearts.SEATS) { i -> if (i == seat) 0 else s.hands[i].size - if (i == gaveTo) given.size else 0 }
        val dealt = Tricks.imagine(unseen, sizes, Tricks.voids(Hearts.SEATS, s.tricks + listOf(s.trick)), random)
        val hands = List(Hearts.SEATS) { i ->
            when (i) {
                seat -> s.hands[i]
                gaveTo -> dealt[i] + given
                else -> dealt[i]
            }
        }
        return s.copy(hands = hands)
    }

    private val ALL = Decks.standard(Random(0)).sortedWith(compareBy({ it.suit }, { it.rank }))

    /** Plays the hand out; higher is better for [seat]: the others' average points minus its own. */
    private fun playOut(start: HeartsState, seat: Int): Double {
        var st = start
        while (!st.handOver) {
            st = if (st.trickComplete) Hearts.collect(st) else Hearts.play(st, sensible(st)) ?: break
        }
        val pts = st.handPoints ?: return 0.0
        return (pts.sum() - pts[seat]) / 3.0 - pts[seat]
    }

    /** One computer action: a pass, collecting a full trick or a card. */
    fun step(s: HeartsState, d: Difficulty, random: Random): HeartsState {
        val actor = Hearts.actor(s)
        return when {
            s.handOver || s.finished -> s
            s.passingPhase && actor >= 0 -> Hearts.choosePass(s, actor, choosePass(s, actor, d, random)) ?: s
            s.trickComplete -> Hearts.collect(s)
            else -> Hearts.play(s, choose(s, d, random)) ?: s
        }
    }
}
