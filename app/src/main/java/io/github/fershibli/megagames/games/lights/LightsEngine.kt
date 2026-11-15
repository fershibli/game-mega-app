package io.github.fershibli.megagames.games.lights

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A grid of lamps in progress. Lamps are numbered row by row. [presses] holds every lamp tapped
 * since the start (for Undo and the move counter); [hint] is the lamp ringed by the last Hint.
 */
@Serializable
data class LightsState(
    val size: Int,
    val on: List<Boolean>,
    /** The grid as dealt, for Restart. */
    val start: List<Boolean>,
    val presses: List<Int> = emptyList(),
    val hint: Int = -1,
    val hints: Int = 0,
) {
    val moves: Int get() = presses.size
    val done: Boolean get() = on.none { it }
}

/**
 * Switch off the lights: tapping a lamp switches it and its four neighbours (up, down, left,
 * right) on or off. The goal is to switch every lamp off.
 *
 * Grids are made by tapping random lamps of a dark grid, so every grid can be switched off
 * (tapping the same lamps again undoes them). The solver treats the grid as a system of
 * equations over GF(2): each lamp's final state is the sum, modulo 2, of the taps on it and on
 * its neighbours. Gaussian elimination gives one solution, and trying every combination of the
 * free variables (none on 3×3 and 6×6, two on 5×5) gives the one with the fewest taps.
 */
object Lights {
    /** Lamps on each side: 3×3, 5×5 and 6×6. */
    fun sizeFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 5
        Difficulty.HARD -> 6
    }

    /** Random taps used to make a grid: about the number of taps needed to solve it. */
    fun tapsFor(d: Difficulty) = when (d) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 6
        Difficulty.HARD -> 10
    }

    /** The lamps switched by a tap on [cell]: the lamp itself and its neighbours. */
    fun affected(cell: Int, n: Int): List<Int> {
        val r = cell / n
        val c = cell % n
        return buildList {
            add(cell)
            if (r > 0) add(cell - n)
            if (r < n - 1) add(cell + n)
            if (c > 0) add(cell - 1)
            if (c < n - 1) add(cell + 1)
        }
    }

    fun toggle(on: List<Boolean>, n: Int, cell: Int): List<Boolean> {
        val next = on.toMutableList()
        for (i in affected(cell, n)) next[i] = !next[i]
        return next
    }

    fun newGame(d: Difficulty, random: Random = Random.Default): LightsState {
        val n = sizeFor(d)
        while (true) {
            var on = List(n * n) { false }
            val taps = (0 until n * n).shuffled(random).take(tapsFor(d))
            for (t in taps) on = toggle(on, n, t)
            // Taps can cancel out into a dark (or nearly dark) grid: deal again.
            if (on.count { it } >= n) return LightsState(n, on, on)
        }
    }

    fun press(s: LightsState, cell: Int): LightsState {
        if (s.done || cell !in 0 until s.size * s.size) return s
        return s.copy(on = toggle(s.on, s.size, cell), presses = s.presses + cell, hint = -1)
    }

    /** Takes back the last tap (a second tap on the same lamp undoes it). */
    fun undo(s: LightsState): LightsState {
        if (s.presses.isEmpty()) return s
        return s.copy(on = toggle(s.on, s.size, s.presses.last()), presses = s.presses.dropLast(1), hint = -1)
    }

    fun restart(s: LightsState): LightsState = s.copy(on = s.start, presses = emptyList(), hint = -1)

    /** Rings one lamp of the shortest way to switch everything off. */
    fun hint(s: LightsState): LightsState {
        if (s.done) return s
        val way = solve(s.on, s.size) ?: return s
        // Prefer the lamp nearest to the top-left, so hints read in order.
        return s.copy(hint = way.minOrNull() ?: -1, hints = s.hints + 1)
    }

    /**
     * The fewest lamps to tap (each once, in any order) to switch off every lamp of [on], or
     * `null` when it cannot be done (never for grids made by [newGame]).
     */
    fun solve(on: List<Boolean>, n: Int): List<Int>? {
        val cells = n * n
        require(cells <= 62) { "grid too big for the bit solver" }
        val rhsBit = 1L shl cells
        // One equation per lamp: the taps that reach it, and whether it is on now.
        val rows = LongArray(cells) { i ->
            var mask = 0L
            for (j in affected(i, n)) mask = mask or (1L shl j)
            if (on[i]) mask or rhsBit else mask
        }
        val pivotOfColumn = IntArray(cells) { -1 }
        var rank = 0
        for (col in 0 until cells) {
            val bit = 1L shl col
            val pivot = (rank until cells).firstOrNull { rows[it] and bit != 0L } ?: continue
            rows[pivot] = rows[rank].also { rows[rank] = rows[pivot] }
            for (r in 0 until cells) if (r != rank && rows[r] and bit != 0L) rows[r] = rows[r] xor rows[rank]
            pivotOfColumn[col] = rank
            rank++
        }
        // An equation 0 = 1 left over: no solution.
        for (r in rank until cells) if (rows[r] and rhsBit != 0L) return null
        val free = (0 until cells).filter { pivotOfColumn[it] < 0 }
        var best: Long = -1
        var bestCount = Int.MAX_VALUE
        for (combo in 0 until (1 shl free.size)) {
            var x = 0L
            free.forEachIndexed { k, col -> if (combo shr k and 1 == 1) x = x or (1L shl col) }
            for (col in 0 until cells) {
                val r = pivotOfColumn[col]
                if (r < 0) continue
                // Pivot value = right-hand side + the free variables in its row.
                var v = rows[r] and rhsBit != 0L
                for (f in free) if (rows[r] and (1L shl f) != 0L && x and (1L shl f) != 0L) v = !v
                if (v) x = x or (1L shl col)
            }
            val count = java.lang.Long.bitCount(x)
            if (count < bestCount) {
                bestCount = count
                best = x
            }
        }
        return (0 until cells).filter { best and (1L shl it) != 0L }
    }
}
