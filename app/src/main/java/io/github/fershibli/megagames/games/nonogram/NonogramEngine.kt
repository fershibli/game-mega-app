package io.github.fershibli.megagames.games.nonogram

import io.github.fershibli.megagames.core.Difficulty
import kotlin.random.Random

/** A picture made of letters, one per square ('.' = empty); see [NonogramPictures]. */
class NonogramPicture(val key: String, val rows: List<String>) {
    val width: Int get() = rows.first().length
    val height: Int get() = rows.size
    val colors: String get() = rows.joinToString("")
}

/**
 * Nonogram ("paint by numbers"): the numbers beside each row and above each column give the
 * lengths of the painted blocks in that line, in order, with at least one empty square between
 * two blocks. Painting the right squares reveals a picture.
 *
 * Puzzles come from the hand-made [NonogramPictures] (or, if none fits, a random symmetric
 * pattern) and are kept only when [isLineSolvable]: looking at one line at a time is always
 * enough to find another square, so the picture is unique and never needs a guess.
 */
object Nonogram {
    const val UNKNOWN = -1
    const val EMPTY = 0
    const val FILLED = 1

    /** Player mark of a square marked as surely empty (in the grid being solved it counts as [EMPTY]). */
    const val CROSSED = 2

    /** Name used for the random patterns (pictures have their own key). */
    const val PATTERN = "pattern"

    fun sizeFor(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> 5
        Difficulty.MEDIUM -> 10
        Difficulty.HARD -> 15
    }

    /** Lengths of the painted blocks of a line, in order (empty for a blank line). */
    fun clues(line: List<Boolean>): IntArray {
        val out = ArrayList<Int>()
        var run = 0
        for (v in line) {
            if (v) run++ else if (run > 0) { out += run; run = 0 }
        }
        if (run > 0) out += run
        return out.toIntArray()
    }

    fun rowClues(width: Int, height: Int, filled: List<Boolean>): List<IntArray> =
        List(height) { r -> clues(List(width) { filled[r * width + it] }) }

    fun columnClues(width: Int, height: Int, filled: List<Boolean>): List<IntArray> =
        List(width) { c -> clues(List(height) { filled[it * width + c] }) }

    /**
     * Everything one line tells on its own: every square that is painted (or empty) in every
     * arrangement of the [clue] that agrees with what is already known in [cells]
     * ([UNKNOWN] / [EMPTY] / [FILLED]). Returns null when no arrangement fits.
     */
    fun solveLine(cells: IntArray, clue: IntArray): IntArray? {
        val n = cells.size
        val k = clue.size
        // fits[i][j]: blocks j.. can be placed in squares i.. (i == n means past the end).
        val fits = Array(n + 2) { BooleanArray(k + 1) }
        fun blockFits(i: Int, j: Int): Boolean {
            val end = i + clue[j]
            if (end > n) return false
            for (t in i until end) if (cells[t] == EMPTY) return false
            return end == n || cells[end] != FILLED
        }
        for (i in n + 1 downTo 0) {
            for (j in k downTo 0) {
                fits[i][j] = when {
                    i >= n -> j == k
                    else -> (cells[i] != FILLED && fits[i + 1][j]) ||
                        (j < k && blockFits(i, j) && fits[minOf(i + clue[j] + 1, n + 1)][j + 1])
                }
            }
        }
        if (!fits[0][0]) return null
        val canFill = BooleanArray(n)
        val canEmpty = BooleanArray(n)
        // Walk only through the arrangements that can be completed.
        val reach = Array(n + 2) { BooleanArray(k + 1) }
        reach[0][0] = true
        for (i in 0 until n) {
            for (j in 0..k) {
                if (!reach[i][j] || !fits[i][j]) continue
                if (cells[i] != FILLED && fits[i + 1][j]) {
                    canEmpty[i] = true
                    reach[i + 1][j] = true
                }
                if (j < k && blockFits(i, j)) {
                    val next = minOf(i + clue[j] + 1, n + 1)
                    if (fits[next][j + 1]) {
                        for (t in i until i + clue[j]) canFill[t] = true
                        if (i + clue[j] < n) canEmpty[i + clue[j]] = true
                        reach[next][j + 1] = true
                    }
                }
            }
        }
        return IntArray(n) {
            when {
                canFill[it] && !canEmpty[it] -> FILLED
                canEmpty[it] && !canFill[it] -> EMPTY
                else -> cells[it]
            }
        }
    }

    /**
     * Solves line by line from [start] until nothing changes. Returns the grid (squares left
     * [UNKNOWN] when lines alone are not enough), or null when the clues contradict it.
     */
    fun lineSolve(width: Int, height: Int, rows: List<IntArray>, columns: List<IntArray>, start: IntArray = IntArray(width * height) { UNKNOWN }): IntArray? {
        val grid = start.copyOf()
        var changed = true
        while (changed) {
            changed = false
            for (r in 0 until height) {
                val line = IntArray(width) { grid[r * width + it] }
                val solved = solveLine(line, rows[r]) ?: return null
                for (c in 0 until width) if (solved[c] != line[c]) { grid[r * width + c] = solved[c]; changed = true }
            }
            for (c in 0 until width) {
                val line = IntArray(height) { grid[it * width + c] }
                val solved = solveLine(line, columns[c]) ?: return null
                for (r in 0 until height) if (solved[r] != line[r]) { grid[r * width + c] = solved[r]; changed = true }
            }
        }
        return grid
    }

    /** True when solving one line at a time finds the whole picture (so it is the only one). */
    fun isLineSolvable(width: Int, height: Int, filled: List<Boolean>): Boolean {
        val grid = lineSolve(width, height, rowClues(width, height, filled), columnClues(width, height, filled)) ?: return false
        return grid.indices.all { grid[it] == if (filled[it]) FILLED else EMPTY }
    }

    fun isLineSolvable(picture: NonogramPicture): Boolean =
        isLineSolvable(picture.width, picture.height, picture.colors.map { it != '.' })

    /** A random picture of the right size; a random pattern only if no picture works. */
    fun generate(difficulty: Difficulty, random: Random, pictures: List<NonogramPicture> = NonogramPictures.all): NonogramPicture {
        val size = sizeFor(difficulty)
        pictures.filter { it.width == size }.shuffled(random).firstOrNull { isLineSolvable(it) }?.let { return it }
        return randomPattern(size, random)
    }

    /**
     * A random pattern, mirrored left to right like a tile, smoothed so it has blobs rather
     * than noise, and colored with one color outside and another in the middle.
     */
    fun randomPattern(size: Int, random: Random): NonogramPicture {
        val palette = "rgbopvyc"
        repeat(500) {
            var cells = BooleanArray(size * size) { random.nextFloat() < 0.58f }
            // One smoothing pass: a square follows the majority of its neighbourhood.
            cells = BooleanArray(size * size) { i ->
                val r = i / size
                val c = i % size
                var on = 0
                var all = 0
                for (dr in -1..1) for (dc in -1..1) {
                    val rr = r + dr
                    val cc = c + dc
                    if (rr !in 0 until size || cc !in 0 until size) continue
                    all++
                    if (cells[rr * size + cc]) on++
                }
                on * 2 > all || (on * 2 == all && cells[i])
            }
            for (r in 0 until size) for (c in size / 2 until size) cells[r * size + c] = cells[r * size + size - 1 - c]
            val filled = cells.toList()
            if (filled.count { it } < size * size / 3 || !isLineSolvable(size, size, filled)) return@repeat
            val a = palette[random.nextInt(palette.length)]
            val b = palette.filter { it != a }[random.nextInt(palette.length - 1)]
            val rows = List(size) { r ->
                String(CharArray(size) { c ->
                    val ring = minOf(minOf(r, size - 1 - r), minOf(c, size - 1 - c))
                    if (!cells[r * size + c]) '.' else if (ring >= size / 3) b else a
                })
            }
            return NonogramPicture(PATTERN, rows)
        }
        // Practically never reached: a frame is always solvable.
        return NonogramPicture(PATTERN, List(size) { r -> String(CharArray(size) { c -> if (r == 0 || c == 0 || r == size - 1 || c == size - 1) 'b' else '.' }) })
    }

    /**
     * A square for the Hint button: a wrong mark first, otherwise a square that one line,
     * looked at with the player's correct marks, already decides. Returns the square and
     * whether it must be painted, or null when everything is right.
     */
    fun hint(width: Int, height: Int, solution: List<Boolean>, marks: List<Int>): Pair<Int, Boolean>? {
        for (i in solution.indices) {
            if (marks[i] == FILLED && !solution[i]) return i to false
            if (marks[i] == CROSSED && solution[i]) return i to true
        }
        val known = IntArray(width * height) {
            when {
                marks[it] == FILLED -> FILLED
                marks[it] == CROSSED -> EMPTY
                else -> UNKNOWN
            }
        }
        val rows = rowClues(width, height, solution)
        val columns = columnClues(width, height, solution)
        val deduced = ArrayList<Int>()
        for (r in 0 until height) {
            val line = solveLine(IntArray(width) { known[r * width + it] }, rows[r]) ?: continue
            for (c in 0 until width) if (known[r * width + c] == UNKNOWN && line[c] == FILLED) deduced += r * width + c
        }
        for (c in 0 until width) {
            val line = solveLine(IntArray(height) { known[it * width + c] }, columns[c]) ?: continue
            for (r in 0 until height) if (known[r * width + c] == UNKNOWN && line[r] == FILLED) deduced += r * width + c
        }
        val pick = deduced.firstOrNull() ?: solution.indices.firstOrNull { solution[it] && marks[it] != FILLED } ?: return null
        return pick to true
    }
}
