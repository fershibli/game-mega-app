package io.github.fershibli.megagames.games.sudoku

import io.github.fershibli.megagames.core.Difficulty
import kotlin.random.Random

/**
 * Sudoku generator.
 *
 * 1. Build a random complete grid with a randomized backtracking solver.
 * 2. Remove clues one by one in random order, keeping a removal only if the puzzle still has
 *    exactly one solution (checked by counting solutions up to 2).
 *
 * The number of clues left depends on the difficulty. Every puzzle has a unique solution.
 */
object SudokuEngine {
    class Puzzle(val givens: IntArray, val solution: IntArray)

    fun targetClues(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> 40
        Difficulty.MEDIUM -> 32
        Difficulty.HARD -> 26
    }

    fun generate(difficulty: Difficulty, random: Random): Puzzle {
        val solution = IntArray(81)
        fillRandom(solution, random)
        val puzzle = solution.copyOf()
        val target = targetClues(difficulty)
        var clues = 81
        for (cell in (0 until 81).shuffled(random)) {
            if (clues <= target) break
            val keep = puzzle[cell]
            puzzle[cell] = 0
            if (countSolutions(puzzle.copyOf(), 2) != 1) puzzle[cell] = keep else clues--
        }
        return Puzzle(puzzle, solution)
    }

    fun row(i: Int) = i / 9
    fun col(i: Int) = i % 9
    fun box(i: Int) = (row(i) / 3) * 3 + col(i) / 3

    private val peerTable: Array<IntArray> = Array(81) { i ->
        (0 until 81).filter { it != i && (row(it) == row(i) || col(it) == col(i) || box(it) == box(i)) }.toIntArray()
    }

    /** Cells that share a row, column or box with [i] (excluding [i]). */
    fun peers(i: Int): IntArray = peerTable[i]

    private fun fillRandom(grid: IntArray, random: Random): Boolean {
        val cell = grid.indexOfFirst { it == 0 }
        if (cell < 0) return true
        for (v in (1..9).shuffled(random)) {
            if (canPlace(grid, cell, v)) {
                grid[cell] = v
                if (fillRandom(grid, random)) return true
                grid[cell] = 0
            }
        }
        return false
    }

    fun canPlace(grid: IntArray, cell: Int, v: Int): Boolean {
        val r = row(cell)
        val c = col(cell)
        for (k in 0 until 9) {
            if (grid[r * 9 + k] == v || grid[k * 9 + c] == v) return false
        }
        val br = r / 3 * 3
        val bc = c / 3 * 3
        for (dr in 0 until 3) for (dc in 0 until 3) if (grid[(br + dr) * 9 + bc + dc] == v) return false
        return true
    }

    /** Counts solutions, stopping at [limit]. Uses the most-constrained-cell heuristic. */
    fun countSolutions(grid: IntArray, limit: Int): Int {
        var best = -1
        var bestMask = 0
        var bestCount = 10
        for (i in 0 until 81) {
            if (grid[i] != 0) continue
            var mask = 0
            for (v in 1..9) if (canPlace(grid, i, v)) mask = mask or (1 shl v)
            val count = Integer.bitCount(mask)
            if (count == 0) return 0
            if (count < bestCount) {
                best = i; bestMask = mask; bestCount = count
                if (count == 1) break
            }
        }
        if (best < 0) return 1
        var total = 0
        for (v in 1..9) {
            if (bestMask and (1 shl v) == 0) continue
            grid[best] = v
            total += countSolutions(grid, limit - total)
            grid[best] = 0
            if (total >= limit) break
        }
        return total
    }

    /** Indices of filled cells that clash with another cell in their row, column or box. */
    fun conflicts(values: List<Int>): Set<Int> {
        val out = HashSet<Int>()
        for (i in 0 until 81) {
            val v = values[i]
            if (v == 0) continue
            for (j in peers(i)) if (values[j] == v) out += i
        }
        return out
    }
}
