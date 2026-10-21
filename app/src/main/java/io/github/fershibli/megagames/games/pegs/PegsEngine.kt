package io.github.fershibli.megagames.games.pegs

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** One jump: the peg at [from] jumps over [over] (which is removed) and lands on [to]. */
@Serializable
data class PegMove(val from: Int, val over: Int, val to: Int)

/**
 * The game in progress. Cells are numbered row by row on a 7×7 grid (0..48); cells outside the
 * board are never holes. [history] holds every jump made, so Undo can go back to the start.
 */
@Serializable
data class PegsState(
    val french: Boolean,
    val pegs: List<Boolean>,
    /** The board as dealt, for Restart. */
    val start: List<Boolean>,
    val history: List<PegMove> = emptyList(),
    val selected: Int = -1,
    /** Legal jumps are highlighted (Easy and Medium). */
    val showJumps: Boolean = true,
    /** Where the last peg should end for the perfect finish (the center on the English board). */
    val goal: Int = Pegs.CENTER,
) {
    val count: Int get() = pegs.count { it }
}

/**
 * Peg solitaire rules on two boards: the English cross of 33 holes and the European (French)
 * board of 37 holes, which adds one hole in each inner corner.
 *
 * A peg jumps straight (never diagonally) over a neighbouring peg into an empty hole two steps
 * away, and the peg jumped over is removed. The game ends when no jump is left; one peg left is
 * a win, and one peg left in the very center is the perfect finish.
 */
object Pegs {
    const val SIZE = 7
    const val CELLS = SIZE * SIZE
    const val CENTER = 24

    private val englishHoles = BooleanArray(CELLS) { inCross(it) }
    private val frenchHoles = BooleanArray(CELLS) { inCross(it) || (it / SIZE in setOf(1, 5) && it % SIZE in setOf(1, 5)) }

    private fun inCross(i: Int): Boolean {
        val r = i / SIZE
        val c = i % SIZE
        return r in 2..4 || c in 2..4
    }

    fun isHole(french: Boolean, i: Int): Boolean = i in 0 until CELLS && (if (french) frenchHoles else englishHoles)[i]

    fun holes(french: Boolean): List<Int> = (0 until CELLS).filter { isHole(french, it) }

    private val englishJumps = jumps(false)
    private val frenchJumps = jumps(true)

    /** Every jump that fits on the board, whatever the pegs. */
    private fun jumps(french: Boolean): List<PegMove> {
        val out = ArrayList<PegMove>()
        for (from in 0 until CELLS) {
            if (!isHole(french, from)) continue
            val r = from / SIZE
            val c = from % SIZE
            for ((dr, dc) in listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)) {
                val r2 = r + 2 * dr
                val c2 = c + 2 * dc
                if (r2 !in 0 until SIZE || c2 !in 0 until SIZE) continue
                val over = (r + dr) * SIZE + c + dc
                val to = r2 * SIZE + c2
                if (isHole(french, over) && isHole(french, to)) out += PegMove(from, over, to)
            }
        }
        return out
    }

    fun legalMoves(french: Boolean, pegs: List<Boolean>): List<PegMove> =
        (if (french) frenchJumps else englishJumps).filter { pegs[it.from] && pegs[it.over] && !pegs[it.to] }

    fun movesFrom(french: Boolean, pegs: List<Boolean>, from: Int): List<PegMove> = legalMoves(french, pegs).filter { it.from == from }

    fun apply(pegs: List<Boolean>, m: PegMove): List<Boolean> =
        pegs.toMutableList().also { it[m.from] = false; it[m.over] = false; it[m.to] = true }

    fun revert(pegs: List<Boolean>, m: PegMove): List<Boolean> =
        pegs.toMutableList().also { it[m.from] = true; it[m.over] = true; it[m.to] = false }

    /** Plays [from] → [to] when it is a legal jump; null otherwise. */
    fun play(s: PegsState, from: Int, to: Int): PegsState? {
        val m = legalMoves(s.french, s.pegs).firstOrNull { it.from == from && it.to == to } ?: return null
        return s.copy(pegs = apply(s.pegs, m), history = s.history + m, selected = -1)
    }

    fun undo(s: PegsState): PegsState {
        val m = s.history.lastOrNull() ?: return s
        return s.copy(pegs = revert(s.pegs, m), history = s.history.dropLast(1), selected = -1)
    }

    fun restart(s: PegsState): PegsState = s.copy(pegs = s.start, history = emptyList(), selected = -1)

    fun isOver(s: PegsState): Boolean = legalMoves(s.french, s.pegs).isEmpty()

    /**
     * One peg, in the goal hole: the perfect finish. On the English board that is the center; on
     * the French board the center cannot be reached from a single empty hole, so the goal is the
     * hole facing the one that started empty, on the other side of the center.
     */
    fun isPerfect(s: PegsState): Boolean = s.count == 1 && s.pegs[s.goal]

    /** The usual start: every hole filled but one ([emptyHole]). */
    fun fullBoard(french: Boolean, emptyHole: Int): List<Boolean> = List(CELLS) { isHole(french, it) && it != emptyHole }

    // ----- Known solutions ---------------------------------------------------------------------
    // Each is a list of (from, to) cells that clears the board down to one peg. Easy deals are made
    // by playing the first part of one of them (under a random turn or mirror of the board), so a
    // partly solved board can always still be finished with a single peg.

    /** English board, center empty, finishing in the center. */
    private val englishSolution = listOf(
        10 to 24, 15 to 17, 2 to 16, 4 to 2, 17 to 15, 14 to 16, 18 to 4, 20 to 18, 23 to 9, 2 to 16,
        21 to 23, 23 to 9, 25 to 23, 27 to 25, 25 to 11, 4 to 18, 30 to 16, 9 to 23, 28 to 30, 31 to 29,
        39 to 25, 34 to 32, 44 to 30, 23 to 37, 46 to 44, 44 to 30, 29 to 31, 31 to 33, 18 to 32, 33 to 31,
        38 to 24,
    )

    /** French board: the top middle hole starts empty; the last peg lands at the bottom middle. */
    const val FRENCH_EMPTY = 10
    private val frenchSolution = listOf(
        8 to 10, 11 to 9, 22 to 8, 8 to 10, 17 to 15, 3 to 17, 14 to 16, 17 to 15, 19 to 17, 24 to 10,
        28 to 14, 14 to 16, 23 to 9, 2 to 16, 33 to 19, 12 to 26, 31 to 33, 29 to 31, 33 to 19, 20 to 18,
        25 to 11, 4 to 18, 44 to 30, 46 to 32, 31 to 33, 34 to 32, 45 to 31, 31 to 33, 40 to 26, 27 to 25,
        25 to 11, 11 to 9, 9 to 23, 23 to 37, 36 to 38,
    )

    fun startHole(french: Boolean): Int = if (french) FRENCH_EMPTY else CENTER

    /** The hole across the center from [i]. */
    fun opposite(i: Int): Int = CELLS - 1 - i

    fun solution(french: Boolean): List<Pair<Int, Int>> = if (french) frenchSolution else englishSolution

    /** The 8 turns and mirrors of the square grid (both boards look the same under all of them). */
    fun symmetry(k: Int, i: Int): Int {
        var r = i / SIZE
        var c = i % SIZE
        if (k and 4 != 0) c = SIZE - 1 - c
        repeat(k and 3) { val t = r; r = c; c = SIZE - 1 - t }
        return r * SIZE + c
    }

    /** Pegs left on an Easy board. */
    private val easyLeft = 10..12

    fun newGame(french: Boolean, difficulty: Difficulty, random: Random = Random.Default): PegsState {
        val empty = startHole(french)
        val showJumps = difficulty != Difficulty.HARD
        if (difficulty != Difficulty.EASY) {
            val board = fullBoard(french, empty)
            return PegsState(french, board, board, showJumps = showJumps, goal = opposite(empty))
        }
        val k = random.nextInt(8)
        var board = fullBoard(french, symmetry(k, empty))
        val total = board.count { it }
        val plays = total - easyLeft.random(random)
        for ((from, to) in solution(french).take(plays)) {
            val m = legalMoves(french, board).first { it.from == symmetry(k, from) && it.to == symmetry(k, to) }
            board = apply(board, m)
        }
        return PegsState(french, board, board, showJumps = showJumps, goal = opposite(symmetry(k, empty)))
    }
}
