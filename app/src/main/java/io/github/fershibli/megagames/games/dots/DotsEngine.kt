package io.github.fershibli.megagames.games.dots

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * "Two Dots"-style game. Connect neighbouring dots of one color (up, down, left, right). Closing a
 * loop clears every dot of that color. Cleared dots count toward the level goals; new dots fall
 * from the top. Win by reaching every goal before running out of moves.
 */
@Serializable
data class DotsState(
    val cols: Int,
    val rows: Int,
    val colors: Int,
    /** Color of each cell, row by row. */
    val grid: List<Int>,
    /** Number of dots to collect per color (0 = no goal for that color). */
    val goals: List<Int>,
    val collected: List<Int>,
    val movesLeft: Int,
) {
    val won get() = goals.indices.all { collected[it] >= goals[it] }
    val lost get() = !won && movesLeft <= 0
}

object Dots {
    fun newGame(d: Difficulty, random: Random): DotsState {
        val (size, colors, moves) = when (d) {
            Difficulty.EASY -> Triple(6, 4, 22)
            Difficulty.MEDIUM -> Triple(7, 5, 20)
            Difficulty.HARD -> Triple(8, 5, 18)
        }
        val goalCount = if (d == Difficulty.HARD) 4 else 3
        val target = when (d) {
            Difficulty.EASY -> 15
            Difficulty.MEDIUM -> 22
            Difficulty.HARD -> 26
        }
        val goalColors = (0 until colors).shuffled(random).take(goalCount).toSet()
        val grid = List(size * size) { random.nextInt(colors) }
        return ensureMoves(
            DotsState(size, size, colors, grid, List(colors) { if (it in goalColors) target else 0 }, List(colors) { 0 }, moves),
            random,
        )
    }

    fun adjacent(a: Int, b: Int, cols: Int) =
        (a / cols == b / cols && kotlin.math.abs(a - b) == 1) || kotlin.math.abs(a - b) == cols

    fun hasMove(s: DotsState): Boolean = s.grid.indices.any { i ->
        val c = i % s.cols
        (c < s.cols - 1 && s.grid[i] == s.grid[i + 1]) || (i + s.cols < s.grid.size && s.grid[i] == s.grid[i + s.cols])
    }

    /** Shuffles the board until at least one move exists. */
    fun ensureMoves(s: DotsState, random: Random): DotsState {
        var cur = s
        var tries = 0
        while (!hasMove(cur)) {
            cur = cur.copy(grid = if (tries++ < 20) cur.grid.shuffled(random) else List(s.grid.size) { random.nextInt(s.colors) })
        }
        return cur
    }

    /**
     * Applies a finished line. [path] must be same-colored neighbours; [loop] clears every dot of
     * that color. Returns null when the line is not a valid move.
     */
    fun play(s: DotsState, path: List<Int>, loop: Boolean, random: Random): DotsState? {
        if (path.size < 2) return null
        val color = s.grid[path[0]]
        if (path.any { s.grid[it] != color }) return null
        if (path.zipWithNext().any { (a, b) -> !adjacent(a, b, s.cols) }) return null
        val cleared = if (loop) s.grid.indices.filter { s.grid[it] == color }.toSet() else path.toSet()
        val grid = s.grid.toMutableList()
        // Gravity, column by column.
        for (c in 0 until s.cols) {
            val kept = (s.rows - 1 downTo 0).map { r -> r * s.cols + c }.filter { it !in cleared }.map { s.grid[it] }
            for (r in s.rows - 1 downTo 0) {
                val k = s.rows - 1 - r
                grid[r * s.cols + c] = if (k < kept.size) kept[k] else {
                    // New dots: after a loop, avoid flooding the board with the cleared color.
                    var n = random.nextInt(s.colors)
                    if (loop && n == color) n = random.nextInt(s.colors)
                    n
                }
            }
        }
        val collected = s.collected.toMutableList().also { it[color] += cleared.size }
        return ensureMoves(s.copy(grid = grid, collected = collected, movesLeft = s.movesLeft - 1), random)
    }
}
