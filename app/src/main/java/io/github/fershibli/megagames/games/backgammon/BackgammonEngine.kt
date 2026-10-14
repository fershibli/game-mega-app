package io.github.fershibli.megagames.games.backgammon

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Backgammon rules and the computer opponent.
 *
 * Positions are stored from the human player's point of view: points 1..24, the human moves
 * from 24 down to 1 and bears off below 1; the computer moves the other way. To keep the rules in
 * one place, all move generation works on a [View] "seen by the side to move", where that side
 * always moves downward (point 25 = its bar, point 0 = borne off).
 */
@Serializable
data class BgPosition(
    /** Index 1..24: positive = human checkers, negative = computer checkers. Index 0 and 25 unused. */
    val points: List<Int>,
    val barHuman: Int = 0,
    val barAi: Int = 0,
    val offHuman: Int = 0,
    val offAi: Int = 0,
) {
    companion object {
        fun initial(): BgPosition {
            val p = MutableList(26) { 0 }
            p[24] = 2; p[13] = 5; p[8] = 3; p[6] = 5
            p[1] = -2; p[12] = -5; p[17] = -3; p[19] = -5
            return BgPosition(p)
        }
    }
}

/** A single checker move in the mover's own numbering: from 25 = bar, to 0 = off. */
@Serializable
data class BgMove(val from: Int, val to: Int, val die: Int)

const val HUMAN = 1
const val AI = 2

/** The board seen by the side to move ("me"), which always moves from high to low points. */
class View(val my: IntArray, val opp: IntArray, var myBar: Int, var oppBar: Int, var myOff: Int, var oppOff: Int) {
    fun copy() = View(my.copyOf(), opp.copyOf(), myBar, oppBar, myOff, oppOff)

    fun allHome(): Boolean {
        if (myBar > 0) return false
        for (i in 7..24) if (my[i] > 0) return false
        return true
    }

    fun canMove(from: Int, die: Int): Int? {
        if (myBar > 0 && from != 25) return null
        if (from == 25) {
            if (myBar == 0) return null
            val to = 25 - die
            return if (opp[to] < 2) to else null
        }
        if (my[from] == 0) return null
        val to = from - die
        if (to >= 1) return if (opp[to] < 2) to else null
        if (!allHome()) return null
        if (to == 0) return 0
        // Bearing off with a higher die is allowed only from the highest occupied point.
        for (i in from + 1..6) if (my[i] > 0) return null
        return 0
    }

    fun apply(m: BgMove) {
        if (m.from == 25) myBar-- else my[m.from]--
        if (m.to == 0) myOff++ else {
            if (opp[m.to] == 1) {
                opp[m.to] = 0
                oppBar++
            }
            my[m.to]++
        }
    }

    fun pip(): Int {
        var s = myBar * 25
        for (i in 1..24) s += my[i] * i
        return s
    }

    fun oppPip(): Int {
        var s = oppBar * 25
        for (i in 1..24) s += opp[i] * (25 - i)
        return s
    }

    fun key(): String = buildString {
        for (i in 1..24) append(my[i]).append(',').append(opp[i]).append(';')
        append(myBar).append('/').append(oppBar).append('/').append(myOff).append('/').append(oppOff)
    }
}

object Backgammon {
    fun view(pos: BgPosition, side: Int): View {
        val my = IntArray(26)
        val opp = IntArray(26)
        for (i in 1..24) {
            val v = pos.points[i]
            val idx = if (side == HUMAN) i else 25 - i
            if (side == HUMAN) { if (v > 0) my[idx] = v else if (v < 0) opp[idx] = -v }
            else { if (v < 0) my[idx] = -v else if (v > 0) opp[idx] = v }
        }
        return if (side == HUMAN) View(my, opp, pos.barHuman, pos.barAi, pos.offHuman, pos.offAi)
        else View(my, opp, pos.barAi, pos.barHuman, pos.offAi, pos.offHuman)
    }

    fun toPosition(v: View, side: Int): BgPosition {
        val pts = MutableList(26) { 0 }
        for (i in 1..24) {
            val abs = if (side == HUMAN) i else 25 - i
            pts[abs] = if (side == HUMAN) v.my[i] - v.opp[i] else v.opp[i] - v.my[i]
        }
        return if (side == HUMAN) BgPosition(pts, v.myBar, v.oppBar, v.myOff, v.oppOff)
        else BgPosition(pts, v.oppBar, v.myBar, v.oppOff, v.myOff)
    }

    fun diceList(d1: Int, d2: Int) = if (d1 == d2) listOf(d1, d1, d1, d1) else listOf(d1, d2)

    /**
     * Every legal way to play [dice]: the longest possible sequences only, and when just one die
     * can be played, the larger one if possible (official rules).
     */
    fun sequences(v: View, dice: List<Int>): List<List<BgMove>> {
        val out = ArrayList<List<BgMove>>()
        var best = 0
        fun dfs(cur: View, remaining: List<Int>, moves: List<BgMove>) {
            var moved = false
            for (d in remaining.distinct()) {
                val rest = remaining.toMutableList().also { it.remove(d) }
                val froms = if (cur.myBar > 0) listOf(25) else (24 downTo 1).filter { cur.my[it] > 0 }
                for (f in froms) {
                    val to = cur.canMove(f, d) ?: continue
                    val m = BgMove(f, to, d)
                    val next = cur.copy().also { it.apply(m) }
                    moved = true
                    dfs(next, rest, moves + m)
                }
            }
            if (!moved) {
                if (moves.size > best) { best = moves.size; out.clear() }
                if (moves.size == best && moves.isNotEmpty()) out += moves
            }
        }
        dfs(v, dice, emptyList())
        if (best == 1 && dice.size == 2 && dice[0] != dice[1]) {
            val high = maxOf(dice[0], dice[1])
            if (out.any { it[0].die == high }) return out.filter { it[0].die == high }
        }
        return out
    }

    fun play(v: View, moves: List<BgMove>): View = v.copy().also { c -> moves.forEach { c.apply(it) } }

    /** Distinct end positions of a roll, each with one sequence that reaches it. */
    fun options(v: View, dice: List<Int>): List<Pair<List<BgMove>, View>> {
        val seen = HashSet<String>()
        val out = ArrayList<Pair<List<BgMove>, View>>()
        for (s in sequences(v, dice)) {
            val end = play(v, s)
            if (seen.add(end.key())) out += s to end
        }
        if (out.isEmpty()) out += emptyList<BgMove>() to v.copy()
        return out
    }

    // ---- Evaluation ---------------------------------------------------------------------------

    /** Number of the 36 rolls that hit a blot at distance d (ignoring blocking points). */
    private val SHOTS = intArrayOf(0, 11, 12, 14, 15, 15, 17, 6, 6, 5, 3, 2, 3, 0, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1)
    private val POINT_VALUE = intArrayOf(0, 2, 3, 5, 7, 8, 9, 8, 4, 3, 2, 2, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2)

    private fun hasContact(v: View): Boolean {
        var myBack = 0
        for (i in 24 downTo 1) if (v.my[i] > 0) { myBack = i; break }
        if (v.myBar > 0) myBack = 25
        var oppBack = 25
        for (i in 1..24) if (v.opp[i] > 0) { oppBack = i; break }
        if (v.oppBar > 0) oppBack = 0
        return myBack > oppBack
    }

    /**
     * Static evaluation, from the point of view of the side that just moved ("me") with the
     * opponent about to roll. Higher is better.
     */
    fun evaluate(v: View): Double {
        if (v.myOff == 15) return 10_000.0
        val pipDiff = (v.oppPip() - v.pip()).toDouble()
        if (!hasContact(v)) {
            // Pure race: pips, plus credit for checkers already off and a smooth home board.
            var wastage = 0
            for (i in 1..6) if (v.my[i] > 3) wastage += v.my[i] - 3
            return pipDiff * 1.2 + v.myOff * 2.5 - wastage
        }
        var score = pipDiff
        // Made points and primes.
        var run = 0
        var longest = 0
        for (i in 1..24) {
            if (v.my[i] >= 2) {
                score += POINT_VALUE[i] * 1.6
                run++
                if (run > longest) longest = run
                if (v.my[i] > 4) score -= (v.my[i] - 4) * 1.5
            } else run = 0
        }
        score += longest * longest * 1.4
        // Anchors in the opponent's home board protect our back checkers.
        for (i in 19..24) if (v.my[i] >= 2) score += 3.0
        // Blots exposed to direct and indirect shots.
        for (b in 1..24) {
            if (v.my[b] != 1) continue
            var shots = 0
            val seen = BooleanArray(25)
            if (v.oppBar > 0 && b in 1..24) { val d = b; if (d <= 24 && !seen[d]) { shots += SHOTS[d]; seen[d] = true } }
            for (j in 1 until b) {
                if (v.opp[j] == 0) continue
                val d = b - j
                if (d in 1..24 && !seen[d]) { shots += SHOTS[d]; seen[d] = true }
            }
            val p = minOf(shots, 36) / 36.0
            score -= p * (25 - b + 8) * 0.9
        }
        score += v.oppBar * 7.0 - v.myBar * 7.0
        // Closed points in my home board make the opponent's bar checkers stay out.
        if (v.oppBar > 0) {
            var closed = 0
            for (i in 1..6) if (v.my[i] >= 2) closed++
            score += closed * closed * 1.5 * v.oppBar
        }
        return score
    }

    private fun flip(v: View) = View(v.opp.let { o -> IntArray(26).also { for (i in 1..24) it[i] = o[25 - i] } },
        v.my.let { m -> IntArray(26).also { for (i in 1..24) it[i] = m[25 - i] } }, v.oppBar, v.myBar, v.oppOff, v.myOff)

    private val ALL_ROLLS: List<Triple<Int, Int, Int>> = buildList {
        for (a in 1..6) for (b in a..6) add(Triple(a, b, if (a == b) 1 else 2))
    }

    /** Chooses the computer's play for [dice]. */
    fun choose(v: View, dice: List<Int>, difficulty: Difficulty, random: Random): List<BgMove> {
        val opts = options(v, dice)
        if (opts.size == 1) return opts[0].first
        return when (difficulty) {
            Difficulty.EASY -> {
                if (random.nextFloat() < 0.45f) opts.random(random).first
                else opts.maxBy { (_, end) -> (end.oppPip() - end.pip()) + end.oppBar * 6.0 + random.nextDouble() * 4 }.first
            }
            Difficulty.MEDIUM -> opts.maxBy { evaluate(it.second) + random.nextDouble() * 0.5 }.first
            Difficulty.HARD -> {
                val shortlist = opts.sortedByDescending { evaluate(it.second) }.take(5)
                shortlist.maxBy { (_, end) ->
                    // Look one roll ahead: average over the opponent's 21 possible rolls of their best reply.
                    val oppView = flip(end)
                    var total = 0.0
                    for ((a, b, w) in ALL_ROLLS) {
                        val best = options(oppView, diceList(a, b)).maxOf { evaluate(it.second) }
                        total += best * w
                    }
                    -total / 36.0
                }.first
            }
        }
    }

    fun winner(p: BgPosition): Int = when {
        p.offHuman == 15 -> HUMAN
        p.offAi == 15 -> AI
        else -> 0
    }

    /** 1 = single game, 2 = gammon, 3 = backgammon. */
    fun winMultiplier(p: BgPosition, winner: Int): Int {
        val loserOff = if (winner == HUMAN) p.offAi else p.offHuman
        if (loserOff > 0) return 1
        val loserBar = if (winner == HUMAN) p.barAi else p.barHuman
        val inWinnerHome = if (winner == HUMAN) (1..6).any { p.points[it] < 0 } else (19..24).any { p.points[it] > 0 }
        return if (loserBar > 0 || inWinnerHome) 3 else 2
    }
}
