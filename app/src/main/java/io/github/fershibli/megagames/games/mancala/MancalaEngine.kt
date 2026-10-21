package io.github.fershibli.megagames.games.mancala

import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameVariant
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** One set of mancala rules. */
data class MancalaRules(
    val key: String,
    /** Oware (abapa) instead of Kalah: no stores in the round, captures of 2 and 3, feeding. */
    val oware: Boolean,
) {
    companion object {
        val KALAH = MancalaRules("kalah", oware = false)
        val OWARE = MancalaRules("oware", oware = true)
        val ALL = listOf(KALAH, OWARE)

        fun of(key: String?): MancalaRules = ALL.firstOrNull { it.key == key } ?: KALAH

        /** The rules offered on the rules screen (keys are stored in save files: never change them). */
        val VARIANTS = listOf(
            GameVariant("kalah", R.string.mancala_variant_kalah, R.string.mancala_variant_kalah_desc),
            GameVariant("oware", R.string.mancala_variant_oware, R.string.mancala_variant_oware_desc),
        )
    }
}

@Serializable
data class MancalaState(
    /** Seeds in each of the 14 holes (see [Mancala]). */
    val pits: List<Int>,
    /** Side to play (0 or 1), or -1 when the game is over. */
    val turn: Int = 0,
    /** The pit played last, or -1. */
    val last: Int = -1,
    /** The holes before the last move, to show its sowing. */
    val before: List<Int>? = null,
    /** The last move earned the same side another turn (Kalah). */
    val again: Boolean = false,
    /** Moves in a row without a capture (Oware: the game ends after [Mancala.OWARE_QUIET]). */
    val quiet: Int = 0,
)

/**
 * Mancala (Kalah and Oware): rules engine and computer opponent for two sides.
 *
 * The 14 holes go counterclockwise: side 0's pits 0..5 (left to right along the bottom), its
 * store 6 on the right, side 1's pits 7..12 (right to left along the top) and its store 13 on
 * the left. The pit facing pit i is 12 − i. In Oware the stores are not sown into: they only keep
 * each side's captured seeds.
 */
object Mancala {
    const val PITS = 6
    const val SEEDS = 4
    const val TOTAL = PITS * 2 * SEEDS

    /** Oware: moves in a row without a capture before each side takes the seeds on its own side. */
    const val OWARE_QUIET = 100

    fun store(side: Int) = side * 7 + 6
    fun pit(side: Int, k: Int) = side * 7 + k
    fun owner(hole: Int) = if (hole < 7) 0 else 1
    fun isStore(hole: Int) = hole == 6 || hole == 13

    fun initial() = MancalaState(List(14) { if (isStore(it)) 0 else SEEDS })

    fun seedsOnSide(pits: IntArray, side: Int): Int {
        var n = 0
        for (k in 0 until PITS) n += pits[pit(side, k)]
        return n
    }

    /** The holes, in order, that the seeds of [from] fall into (Oware skips the pit being emptied). */
    fun sowPath(pits: IntArray, from: Int, rules: MancalaRules): IntArray {
        val side = owner(from)
        val out = IntArray(pits[from])
        var pos = from
        for (n in out.indices) {
            do {
                pos = (pos + 1) % 14
            } while ((rules.oware && (pos == from || isStore(pos))) || (!rules.oware && pos == store(1 - side)))
            out[n] = pos
        }
        return out
    }

    /** Pits [side] may play: any pit with seeds; in Oware, only those that feed an empty rival. */
    fun legalMoves(pits: IntArray, side: Int, rules: MancalaRules): List<Int> {
        val moves = (0 until PITS).map { pit(side, it) }.filter { pits[it] > 0 }
        if (!rules.oware || seedsOnSide(pits, 1 - side) > 0) return moves
        return moves.filter { m -> sowPath(pits, m, rules).any { owner(it) != side } }
    }

    /**
     * Sows [from] in place and makes the captures. Returns whether the same side plays again
     * (Kalah: the last seed in its own store) in bit 0, and whether something was captured in bit 1.
     */
    fun sow(pits: IntArray, from: Int, rules: MancalaRules): Int {
        val side = owner(from)
        val path = sowPath(pits, from, rules)
        pits[from] = 0
        for (h in path) pits[h]++
        val last = path.last()
        if (!rules.oware) {
            if (last == store(side)) return 1
            val opposite = 12 - last
            if (owner(last) == side && pits[last] == 1 && pits[opposite] > 0) {
                pits[store(side)] += pits[opposite] + 1
                pits[last] = 0
                pits[opposite] = 0
                return 2
            }
            return 0
        }
        // Oware: going back from the last pit, take every rival pit that now holds 2 or 3.
        if (owner(last) == side) return 0
        var h = last
        var taken = 0
        val captured = IntArray(14)
        while (h >= 0 && owner(h) != side && !isStore(h) && pits[h] in 2..3) {
            captured[h] = pits[h]
            taken += pits[h]
            h--
        }
        if (taken == 0) return 0
        // Grand slam: a move that would take every seed of the rival takes nothing.
        if (taken == seedsOnSide(pits, 1 - side)) return 0
        for (i in captured.indices) if (captured[i] > 0) pits[i] = 0
        pits[store(side)] += taken
        return 2
    }

    /** The final count when the game ends: each side keeps the seeds still on its own side. */
    private fun sweep(pits: IntArray) {
        for (side in 0..1) for (k in 0 until PITS) {
            pits[store(side)] += pits[pit(side, k)]
            pits[pit(side, k)] = 0
        }
    }

    /** Has the game ended with the board [pits] and [next] to play? */
    private fun over(pits: IntArray, next: Int, quiet: Int, rules: MancalaRules): Boolean = if (rules.oware) {
        pits[6] > TOTAL / 2 || pits[13] > TOTAL / 2 || quiet >= OWARE_QUIET || legalMoves(pits, next, rules).isEmpty()
    } else {
        seedsOnSide(pits, 0) == 0 || seedsOnSide(pits, 1) == 0
    }

    /** Plays [from] for the side to play and works out what comes next. */
    fun play(s: MancalaState, from: Int, rules: MancalaRules): MancalaState {
        val pits = s.pits.toIntArray()
        val result = sow(pits, from, rules)
        val again = result and 1 != 0
        val quiet = if (result and 2 != 0) 0 else s.quiet + 1
        val next = if (again) s.turn else 1 - s.turn
        val ended = over(pits, next, quiet, rules)
        if (ended) sweep(pits)
        return MancalaState(pits.toList(), if (ended) -1 else next, from, s.pits, again && !ended, quiet)
    }

    /** Winner of a finished game (0 or 1), or -1 for a draw. */
    fun winner(s: MancalaState): Int = when {
        s.pits[6] > s.pits[13] -> 0
        s.pits[13] > s.pits[6] -> 1
        else -> -1
    }

    // ----- Computer opponent -----------------------------------------------------------------

    private const val WIN = 1_000_000

    private class Search(val rules: MancalaRules, val deadline: Long) {
        var nodes = 0
        var aborted = false

        /** Value for [side] (to play) of [pits]: stores and, in Kalah, a little for seeds on its side. */
        fun negamax(pits: IntArray, side: Int, quiet: Int, depth: Int, alphaIn: Int, beta: Int): Int {
            if ((++nodes and 1023) == 0 && System.nanoTime() > deadline) aborted = true
            if (aborted) return 0
            if (over(pits, side, quiet, rules)) {
                val end = pits.copyOf()
                sweep(end)
                val diff = end[store(side)] - end[store(1 - side)]
                return diff * 100 + Integer.signum(diff) * WIN / 2
            }
            if (depth <= 0) {
                val diff = pits[store(side)] - pits[store(1 - side)]
                val onSide = if (rules.oware) 0 else seedsOnSide(pits, side) - seedsOnSide(pits, 1 - side)
                return diff * 100 + onSide * 25
            }
            var alpha = alphaIn
            var best = Int.MIN_VALUE
            for (m in legalMoves(pits, side, rules)) {
                val next = pits.copyOf()
                val r = sow(next, m, rules)
                val q = if (r and 2 != 0) 0 else quiet + 1
                val v = if (r and 1 != 0) negamax(next, side, q, depth - 1, alpha, beta)
                else -negamax(next, 1 - side, q, depth - 1, -beta, -alpha)
                if (aborted) return 0
                if (v > best) best = v
                if (best > alpha) alpha = best
                if (alpha >= beta) break
            }
            return best
        }
    }

    /** Search depth and time budget (milliseconds) for each level. */
    private fun limits(difficulty: Difficulty): Pair<Int, Long> = when (difficulty) {
        Difficulty.EASY -> 1 to 100L
        Difficulty.MEDIUM -> 4 to 300L
        Difficulty.HARD -> 16 to 650L
    }

    /**
     * The pit the side to play should sow. Every level takes a move that wins at once; Easy
     * looks one move ahead and now and then plays at random, Medium four, and Hard deepens as
     * far as it can in under a second.
     */
    fun bestMove(s: MancalaState, rules: MancalaRules, difficulty: Difficulty, random: Random): Int? {
        val side = s.turn
        if (side < 0) return null
        val pits = s.pits.toIntArray()
        val moves = legalMoves(pits, side, rules)
        if (moves.size <= 1) return moves.firstOrNull()
        moves.firstOrNull { m -> play(s, m, rules).let { it.turn == -1 && winner(it) == side } }?.let { return it }
        if (difficulty == Difficulty.EASY && random.nextFloat() < 0.35f) return moves.random(random)
        val (maxDepth, budget) = limits(difficulty)
        val search = Search(rules, System.nanoTime() + budget * 1_000_000)
        var order = moves.shuffled(random)
        var best = order.first()
        for (depth in 1..maxDepth) {
            var alpha = -WIN * 2
            var bestHere = -1
            val scores = HashMap<Int, Int>()
            for (m in order) {
                val next = pits.copyOf()
                val r = sow(next, m, rules)
                val q = if (r and 2 != 0) 0 else s.quiet + 1
                val v = if (r and 1 != 0) search.negamax(next, side, q, depth - 1, alpha, WIN * 2)
                else -search.negamax(next, 1 - side, q, depth - 1, -WIN * 2, -alpha)
                if (search.aborted) break
                scores[m] = v
                if (v > alpha) {
                    alpha = v
                    bestHere = m
                }
            }
            if (search.aborted) {
                if (bestHere >= 0 && order.first() in scores) best = bestHere
                break
            }
            if (bestHere >= 0) best = bestHere
            order = order.sortedByDescending { scores[it] ?: Int.MIN_VALUE }
        }
        return best
    }
}
