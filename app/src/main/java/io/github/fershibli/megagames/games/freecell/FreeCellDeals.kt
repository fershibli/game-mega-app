package io.github.fershibli.megagames.games.freecell

import io.github.fershibli.megagames.core.Difficulty
import kotlin.random.Random

/**
 * Chooses deals that can be won, and how hard they are, with [FreeCellSolver]:
 * - Easy: the solver wins quickly using only two of the four free cells.
 * - Medium: it needs three free cells.
 * - Hard: it needs all four.
 */
object FreeCellDeals {
    /** Positions the solver may look at to win a deal with all four free cells. */
    private const val SOLVE_NODES = 30_000

    /** Positions it may look at with fewer cells: a deal it cannot win that fast is harder. */
    private const val GRADE_NODES = 3_000

    /** Positions the hint may look at (well under a second on a slow phone). */
    private const val HINT_NODES = 25_000

    /** Positions looked at after each move to tell whether the game is lost. */
    private const val LOST_NODES = 3_000

    /** Time allowed to look for a deal of the right level before settling for a close one. */
    private const val DEAL_BUDGET_NS = 2_500_000_000L

    private fun wins(s: FreeCellState, cells: Int, nodes: Int) = FreeCellSolver.solve(s, cells, nodes) is FreeCellSolver.Result.Solved

    /**
     * The level of a deal, or null if the solver could not win it: Easy when it is won with two
     * free cells, Hard when even three are not enough, Medium in between.
     */
    fun grade(s: FreeCellState): Difficulty? = when {
        wins(s, 2, GRADE_NODES) -> Difficulty.EASY
        !wins(s, 4, SOLVE_NODES) -> null
        wins(s, 3, GRADE_NODES) -> Difficulty.MEDIUM
        else -> Difficulty.HARD
    }

    /** True when the deal can be won and has the [difficulty] wanted, checking the cheapest test first. */
    private fun matches(s: FreeCellState, difficulty: Difficulty): Boolean = when (difficulty) {
        Difficulty.EASY -> wins(s, 2, GRADE_NODES)
        Difficulty.MEDIUM -> !wins(s, 2, GRADE_NODES) && wins(s, 3, GRADE_NODES)
        Difficulty.HARD -> !wins(s, 3, GRADE_NODES) && wins(s, 4, SOLVE_NODES)
    }

    /** A winnable deal of the [difficulty] wanted (or a winnable one if time runs out). */
    fun deal(difficulty: Difficulty, random: Random): FreeCellState {
        val start = System.nanoTime()
        var fallback: FreeCellState? = null
        while (System.nanoTime() - start < DEAL_BUDGET_NS) {
            val s = FreeCell.deal(random.nextLong(1, Long.MAX_VALUE))
            if (matches(s, difficulty)) return s
            if (fallback == null && wins(s, 4, SOLVE_NODES)) fallback = s
        }
        return fallback ?: generateSequence { FreeCell.deal(random.nextLong(1, Long.MAX_VALUE)) }.first { wins(it, 4, SOLVE_NODES) }
    }

    /** The next move of a winning line, or a card that can go home, or null. */
    fun hint(s: FreeCellState): FcMove? {
        if (s.won) return null
        val solved = FreeCellSolver.solve(s, maxNodes = HINT_NODES) as? FreeCellSolver.Result.Solved
        return solved?.moves?.firstOrNull() ?: FreeCell.simpleHint(s)
    }

    /** True when no move is left at all, or when a short search proves the game cannot be won. */
    fun isLost(s: FreeCellState): Boolean =
        !s.won && (!FreeCell.anyMove(s) || FreeCellSolver.solve(s, maxNodes = LOST_NODES) == FreeCellSolver.Result.Unsolvable)
}
