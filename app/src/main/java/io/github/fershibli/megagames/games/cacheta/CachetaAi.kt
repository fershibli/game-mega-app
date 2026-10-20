package io.github.fershibli.megagames.games.cacheta

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Card
import kotlin.math.abs
import kotlin.random.Random

/**
 * The computer players of Cacheta: a hand-written heuristic, tuned per difficulty.
 *
 * A hand is valued by the most cards it can place in groups (found exactly, by trying every
 * group the cards allow) plus a little for "almost groups": pairs of the same rank and cards of
 * the same suit one or two ranks apart. Wild cards are always worth keeping.
 *
 * - Easy: takes the discard only when it clearly helps (and not always), throws away a fairly
 *   random weak card and sometimes does not notice it could go out.
 * - Medium: keeps the cards that make or nearly make groups and discards the most useless one.
 * - Hard: also avoids throwing away cards close to the ones the next player took from the pile.
 *
 * The computer only looks at its own cards and at what everyone saw on the table.
 */
object CachetaAi {
    /** Cards of [hand] placed in groups, as a bit mask, choosing the largest possible cover. */
    fun coverMask(t: CachetaTable, hand: List<Card>): Int {
        val n = hand.size
        if (n < 3) return 0
        val groups = ArrayList<Int>()
        val buffer = ArrayList<Card>(n)
        for (mask in 1 until (1 shl n)) {
            if (Integer.bitCount(mask) < 3) continue
            buffer.clear()
            for (i in 0 until n) if (mask and (1 shl i) != 0) buffer += hand[i]
            if (t.isGroup(buffer)) groups += mask
        }
        if (groups.isEmpty()) return 0
        val best = IntArray(1 shl n) { -1 }
        val pick = IntArray(1 shl n)
        fun solve(avail: Int): Int {
            if (avail == 0) return 0
            if (best[avail] >= 0) return best[avail]
            val low = avail and -avail
            var value = solve(avail and low.inv())
            var chosen = 0
            for (g in groups) {
                if (g and low == 0 || g and avail != g) continue
                val v = Integer.bitCount(g) + solve(avail and g.inv())
                if (v > value) { value = v; chosen = g }
            }
            best[avail] = value
            pick[avail] = chosen
            return value
        }
        val full = (1 shl n) - 1
        solve(full)
        var covered = 0
        var avail = full
        while (avail != 0) {
            val g = pick[avail]
            if (g != 0) { covered = covered or g; avail = avail and g.inv() } else avail = avail and (avail and -avail).inv()
        }
        return covered
    }

    private fun rankGap(t: CachetaTable, a: Int, b: Int): Int {
        val d = abs(a - b)
        return if (t.rules.wrap) minOf(d, 13 - d) else minOf(d, if (a == 1 || b == 1) abs(maxOf(a, b) - 14) else d)
    }

    /** How good [hand] is: 10 per card in a group plus partial credit for near groups. */
    fun value(t: CachetaTable, hand: List<Card>): Double {
        val covered = coverMask(t, hand)
        var v = Integer.bitCount(covered) * 10.0
        val left = hand.filterIndexed { i, _ -> covered and (1 shl i) == 0 }
        for (i in left.indices) {
            val a = left[i]
            if (t.isWild(a)) { v += 6.0; continue }
            for (j in i + 1 until left.size) {
                val b = left[j]
                if (t.isWild(b)) continue
                v += when {
                    a.rank == b.rank && a.suit != b.suit -> 3.0
                    a.suit == b.suit && rankGap(t, a.rank, b.rank) == 1 -> 3.0
                    a.suit == b.suit && rankGap(t, a.rank, b.rank) == 2 -> 2.0
                    else -> 0.0
                }
            }
        }
        return v
    }

    private fun minus(hand: List<Card>, c: Card) = hand.toMutableList().also { it.remove(c) }

    /**
     * The card to throw away from the hand of the player in turn. [danger] avoids cards close to
     * those the next player took; [noise] adds randomness (mistakes).
     */
    fun chooseDiscard(s: CachetaState, danger: Boolean, noise: Double, random: Random): Card {
        val p = s.turn
        val t = s.table
        val hand = s.hands[p]
        val nextTook = s.taken.getOrNull((p + 1) % s.players).orEmpty()
        val candidates = hand.distinct().filter { it != s.tookFromPile }
        val naturals = candidates.filterNot(t::isWild).ifEmpty { candidates }
        return naturals.maxBy { c ->
            var v = value(t, minus(hand, c))
            if (danger && nextTook.any { k -> (k.rank == c.rank && k.suit != c.suit) || (k.suit == c.suit && rankGap(t, k.rank, c.rank) <= 2) }) v -= 4.0
            v + random.nextDouble() * noise
        }
    }

    /** True if the top of the discard pile is worth taking. */
    fun wantsPile(s: CachetaState, difficulty: Difficulty, random: Random): Boolean {
        val top = s.discard.lastOrNull() ?: return false
        val t = s.table
        val hand = s.hands[s.turn]
        val with = hand + top
        if (t.goOut(with, forbidden = top) != null) return difficulty != Difficulty.EASY || random.nextFloat() < 0.7f
        val base = value(t, hand)
        val best = with.distinct().filter { it != top }.maxOf { value(t, minus(with, it)) }
        return when (difficulty) {
            Difficulty.EASY -> best - base >= 12 && random.nextFloat() < 0.5f
            Difficulty.MEDIUM -> best - base >= 6
            Difficulty.HARD -> best - base >= 5 || t.isWild(top)
        }
    }

    /** One decision of the computer player who must act now (see [Cacheta.actor]). */
    fun step(s: CachetaState, difficulty: Difficulty, random: Random): CachetaState {
        if (s.finished) return s
        if (s.offers.isNotEmpty()) {
            val grab = difficulty != Difficulty.EASY || random.nextFloat() < 0.5f
            return (if (grab) Cacheta.grab(s) else null) ?: Cacheta.pass(s)
        }
        if (!s.drawn) {
            return if (wantsPile(s, difficulty, random)) Cacheta.takeDiscard(s) ?: Cacheta.drawFromStock(s, random) else Cacheta.drawFromStock(s, random)
        }
        val missesIt = difficulty == Difficulty.EASY && random.nextFloat() < 0.25f
        if (!missesIt) Cacheta.goOut(s)?.let { return it }
        val noise = when (difficulty) {
            Difficulty.EASY -> 14.0
            Difficulty.MEDIUM -> 2.0
            Difficulty.HARD -> 0.5
        }
        val card = chooseDiscard(s, danger = difficulty == Difficulty.HARD, noise = noise, random = random)
        return Cacheta.discard(s, card) ?: s
    }
}
