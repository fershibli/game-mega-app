package io.github.fershibli.megagames.games.kakuro

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.numbers.SumLogic
import kotlin.random.Random

/**
 * Kakuro: a crossword of digits. Every white square holds a digit 1 to 9; each horizontal or
 * vertical run of white squares adds up to the number printed in the black square before it,
 * and a run never repeats a digit.
 *
 * Generator:
 * 1. A random, symmetric layout of black squares where every run is 2 to [Level.maxRun] long
 *    and all white squares are connected.
 * 2. A random filling with different digits in every run (backtracking).
 * 3. The sums are read from the filling. [candidates] reasons run by run like a person would
 *    (which digits can still make this sum here?); while it cannot settle every square, one
 *    unsettled digit at a time is changed (see [makeLogical]). The puzzle is kept only when
 *    logic alone settles every square, so it has exactly one solution, reachable without
 *    guessing.
 */
object Kakuro {
    /** A run of white squares: [clue] is the black square that shows its [sum]. */
    class Run(val cells: IntArray, val sum: Int, val across: Boolean, val clue: Int)

    /** Grid size (black border included) and the longest run allowed, per level. */
    data class Level(val width: Int, val height: Int, val maxRun: Int, val blockShare: Float)

    fun level(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> Level(6, 6, 4, 0.16f)
        Difficulty.MEDIUM -> Level(8, 8, 5, 0.18f)
        Difficulty.HARD -> Level(10, 10, 5, 0.2f)
    }

    /** A finished puzzle: [solution] holds 0 in the black squares. */
    class Puzzle(val width: Int, val height: Int, val solution: IntArray) {
        val runs: List<Run> = runsOf(width, height, solution)
    }

    /** Every run of the grid; [values] gives the sums (0 = black square). */
    fun runsOf(width: Int, height: Int, values: IntArray): List<Run> {
        val out = ArrayList<Run>()
        for (i in values.indices) {
            if (values[i] != 0) continue
            val r = i / width
            val c = i % width
            if (c + 1 < width && values[i + 1] != 0) {
                var e = c + 1
                while (e < width && values[r * width + e] != 0) e++
                val cells = IntArray(e - c - 1) { r * width + c + 1 + it }
                out += Run(cells, cells.sumOf { values[it] }, true, i)
            }
            if (r + 1 < height && values[i + width] != 0) {
                var e = r + 1
                while (e < height && values[e * width + c] != 0) e++
                val cells = IntArray(e - r - 1) { (r + 1 + it) * width + c }
                out += Run(cells, cells.sumOf { values[it] }, false, i)
            }
        }
        return out
    }

    fun generate(difficulty: Difficulty, random: Random): Puzzle {
        val level = level(difficulty)
        val w = level.width
        val h = level.height
        while (true) {
            val white = layout(level, random) ?: continue
            val runs = runsOf(w, h, IntArray(w * h) { if (white[it]) 1 else 0 })
            val values = fill(w * h, white, runs, random) ?: continue
            if (makeLogical(values, runs.map { it.cells }, random)) return Puzzle(w, h, values)
        }
    }

    /**
     * A random filling usually leaves some sums that several fillings share. This changes one
     * digit at a time among the squares logic cannot settle yet, keeping each change that does
     * not make things worse, until logic settles every square (true) or it gives up (false).
     */
    private fun makeLogical(values: IntArray, runs: List<IntArray>, random: Random): Boolean {
        val sums = IntArray(runs.size)
        val runsOfCell = Array(values.size) { IntArray(0) }
        runs.forEachIndexed { k, cells -> cells.forEach { runsOfCell[it] += k } }
        fun score(): IntArray {
            for (k in runs.indices) sums[k] = runs[k].sumOf { values[it] }
            return candidates(values.size, runs, sums) ?: error("the filling itself always fits")
        }
        var cand = score()
        var best = cand.sumOf { if (it == 0) 0 else Integer.bitCount(it) - 1 }
        var steps = 0
        while (best > 0 && steps++ < 2500) {
            val open = cand.indices.filter { Integer.bitCount(cand[it]) > 1 }
            val cell = open.random(random)
            var taken = 1 shl values[cell]
            for (k in runsOfCell[cell]) for (other in runs[k]) taken = taken or (1 shl values[other])
            val choices = SumLogic.digits(SumLogic.ALL and taken.inv())
            if (choices.isEmpty()) continue
            val old = values[cell]
            // Usually pick the digit that leaves its two runs the fewest ways of making their sums.
            values[cell] = if (random.nextInt(3) == 0) choices.random(random) else choices.minBy { d ->
                runsOfCell[cell].sumOf { k -> SumLogic.combos(sums[k] - old + d, runs[k].size).size } * 16 + random.nextInt(16)
            }
            val next = score()
            val total = next.sumOf { if (it == 0) 0 else Integer.bitCount(it) - 1 }
            if (total <= best) {
                best = total
                cand = next
            } else {
                values[cell] = old
            }
        }
        return best == 0
    }

    /**
     * Solves by logic alone: for every run, keep only the digits that take part in some way of
     * making its sum with what is still possible in its squares, until nothing changes.
     * Returns the solution, or null when logic alone cannot finish it.
     */
    fun solve(size: Int, runs: List<Run>): IntArray? {
        val cand = candidates(size, runs.map { it.cells }, IntArray(runs.size) { runs[it].sum }) ?: return null
        val out = IntArray(size)
        for (i in 0 until size) {
            val m = cand[i]
            if (m == 0) continue
            if (Integer.bitCount(m) != 1) return null
            out[i] = Integer.numberOfTrailingZeros(m)
        }
        return out
    }

    /**
     * What logic alone can tell about each square (bit masks, 0 for black squares), or null on
     * a contradiction. Only the runs whose squares changed are looked at again.
     */
    fun candidates(size: Int, runs: List<IntArray>, sums: IntArray): IntArray? {
        val cand = IntArray(size)
        val runsOfCell = Array(size) { IntArray(0) }
        runs.forEachIndexed { k, cells -> cells.forEach { cand[it] = SumLogic.ALL; runsOfCell[it] += k } }
        val dirty = BooleanArray(runs.size) { true }
        val before = IntArray(size)
        var again = true
        while (again) {
            again = false
            for (k in runs.indices) {
                if (!dirty[k]) continue
                dirty[k] = false
                val cells = runs[k]
                for (c in cells) before[c] = cand[c]
                when (SumLogic.narrow(sums[k], cells, cand)) {
                    -1 -> return null
                    1 -> {
                        again = true
                        for (c in cells) if (cand[c] != before[c]) for (r in runsOfCell[c]) if (r != k) dirty[r] = true
                    }
                }
            }
        }
        return cand
    }

    /** Random symmetric layout (true = white square), or null when this attempt went wrong. */
    private fun layout(level: Level, random: Random): BooleanArray? {
        val w = level.width
        val h = level.height
        val white = BooleanArray(w * h) { it / w > 0 && it % w > 0 }
        // The layout looks the same when turned upside down, like printed puzzles.
        fun mirror(i: Int) = (h - i / w) * w + (w - i % w)
        fun block(i: Int) {
            white[i] = false
            white[mirror(i)] = false
        }
        val interior = (1 until h).flatMap { r -> (1 until w).map { c -> r * w + c } }
        repeat((interior.size * level.blockShare / 2).toInt()) { block(interior.random(random)) }
        var changed = true
        var rounds = 0
        while (changed && rounds++ < 100) {
            changed = false
            for (run in runsOf(w, h, IntArray(w * h) { if (white[it]) 1 else 0 })) {
                val len = run.cells.size
                if (len > level.maxRun) {
                    // Cut the long run where both pieces stay at least two squares long.
                    block(run.cells[2 + random.nextInt(len - 4)])
                    changed = true
                    break
                }
            }
            if (changed) continue
            for (i in interior) {
                if (!white[i]) continue
                val alone = (!white[i - 1] && (i % w == w - 1 || !white[i + 1])) ||
                    (!white[i - w] && (i / w == h - 1 || !white[i + w]))
                if (alone) {
                    block(i)
                    changed = true
                }
            }
        }
        if (changed) return null
        val whites = white.count { it }
        if (whites < interior.size * 0.6f) return null
        // Every white square must be reachable from every other one.
        val start = white.indexOfFirst { it }
        val seen = BooleanArray(w * h)
        val stack = ArrayDeque<Int>().apply { add(start) }
        seen[start] = true
        var reached = 0
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            reached++
            for (j in intArrayOf(i - 1, i + 1, i - w, i + w)) {
                if (j in 0 until w * h && white[j] && !seen[j] && (j / w == i / w || j % w == i % w)) {
                    seen[j] = true
                    stack.addLast(j)
                }
            }
        }
        return if (reached == whites) white else null
    }

    /** Random digits with no repetition in any run, or null when the search took too long. */
    private fun fill(size: Int, white: BooleanArray, runs: List<Run>, random: Random): IntArray? {
        val across = IntArray(size) { -1 }
        val down = IntArray(size) { -1 }
        runs.forEachIndexed { k, run -> run.cells.forEach { if (run.across) across[it] = k else down[it] = k } }
        val used = IntArray(runs.size)
        val values = IntArray(size)
        val cells = (0 until size).filter { white[it] }
        var budget = 20_000

        fun free(i: Int) = SumLogic.ALL and used[across[i]].inv() and used[down[i]].inv()

        fun step(): Boolean {
            if (--budget < 0) return false
            // Most constrained square first.
            var best = -1
            var bestCount = 10
            for (i in cells) {
                if (values[i] != 0) continue
                val count = Integer.bitCount(free(i))
                if (count < bestCount) {
                    best = i
                    bestCount = count
                }
            }
            if (best < 0) return true
            for (d in SumLogic.digits(free(best)).shuffled(random)) {
                val bit = 1 shl d
                values[best] = d
                used[across[best]] = used[across[best]] or bit
                used[down[best]] = used[down[best]] or bit
                if (step()) return true
                used[across[best]] = used[across[best]] xor bit
                used[down[best]] = used[down[best]] xor bit
                values[best] = 0
            }
            return false
        }
        return if (step()) values else null
    }

    /** Squares whose digit is repeated in one of their runs, or that complete a run with the wrong sum. */
    fun conflicts(values: List<Int>, runs: List<Run>): Set<Int> {
        val out = HashSet<Int>()
        for (run in runs) {
            for (a in run.cells) for (b in run.cells) if (a != b && values[a] != 0 && values[a] == values[b]) out += a
            if (run.cells.all { values[it] != 0 } && run.cells.sumOf { values[it] } != run.sum) out += run.cells.toList()
        }
        return out
    }
}
