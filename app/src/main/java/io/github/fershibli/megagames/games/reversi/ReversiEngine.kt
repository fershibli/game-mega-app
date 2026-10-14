package io.github.fershibli.megagames.games.reversi

import io.github.fershibli.megagames.core.Difficulty
import kotlin.random.Random

/**
 * Reversi (Othello) rules and computer opponent.
 *
 * Board: 64 cells, 0 = empty, [PLAYER] = human (dark, moves first), [AI] = computer (light).
 *
 * The opponent is a classic alpha-beta minimax search. Its evaluation mixes a positional weight
 * table (corners are precious, cells next to corners are dangerous), mobility (how many moves
 * each side has) and, near the end, the plain disc count. Difficulty changes the search depth and
 * how often the easy opponent picks a random move.
 */
object Reversi {
    const val EMPTY = 0
    const val PLAYER = 1
    const val AI = 2

    fun other(side: Int) = 3 - side

    private val DIRS = intArrayOf(-9, -8, -7, -1, 1, 7, 8, 9)

    fun initial(): IntArray = IntArray(64).also {
        it[27] = AI; it[36] = AI
        it[28] = PLAYER; it[35] = PLAYER
    }

    private fun step(pos: Int, dir: Int): Int {
        val c = pos % 8
        val next = pos + dir
        if (next !in 0..63) return -1
        val nc = next % 8
        if (kotlin.math.abs(nc - c) > 1) return -1 // wrapped around the edge
        return next
    }

    /** Cells that would flip if [side] plays at [pos] (empty if the move is illegal). */
    fun flips(board: IntArray, pos: Int, side: Int): List<Int> {
        if (board[pos] != EMPTY) return emptyList()
        val out = ArrayList<Int>()
        val opp = other(side)
        for (d in DIRS) {
            val line = ArrayList<Int>()
            var p = step(pos, d)
            while (p >= 0 && board[p] == opp) {
                line += p
                p = step(p, d)
            }
            if (p >= 0 && board[p] == side && line.isNotEmpty()) out += line
        }
        return out
    }

    fun legalMoves(board: IntArray, side: Int): List<Int> = (0 until 64).filter { flips(board, it, side).isNotEmpty() }

    fun play(board: IntArray, pos: Int, side: Int): IntArray? {
        val f = flips(board, pos, side)
        if (f.isEmpty()) return null
        return board.copyOf().also { b ->
            b[pos] = side
            f.forEach { b[it] = side }
        }
    }

    fun count(board: IntArray, side: Int) = board.count { it == side }

    fun gameOver(board: IntArray) = legalMoves(board, PLAYER).isEmpty() && legalMoves(board, AI).isEmpty()

    private val WEIGHTS = intArrayOf(
        100, -20, 10, 5, 5, 10, -20, 100,
        -20, -50, -2, -2, -2, -2, -50, -20,
        10, -2, 1, 1, 1, 1, -2, 10,
        5, -2, 1, 0, 0, 1, -2, 5,
        5, -2, 1, 0, 0, 1, -2, 5,
        10, -2, 1, 1, 1, 1, -2, 10,
        -20, -50, -2, -2, -2, -2, -50, -20,
        100, -20, 10, 5, 5, 10, -20, 100,
    )

    /** Static evaluation from [side]'s point of view. */
    fun evaluate(board: IntArray, side: Int): Int {
        val opp = other(side)
        val empties = board.count { it == EMPTY }
        val myMoves = if (empties == 0) 0 else legalMoves(board, side).size
        val oppMoves = if (empties == 0) 0 else legalMoves(board, opp).size
        if (myMoves + oppMoves == 0) {
            val diff = count(board, side) - count(board, opp)
            return diff * 1000
        }
        var positional = 0
        for (i in 0 until 64) {
            when (board[i]) {
                side -> positional += WEIGHTS[i]
                opp -> positional -= WEIGHTS[i]
            }
        }
        val mobility = 100 * (myMoves - oppMoves) / (myMoves + oppMoves)
        val discs = count(board, side) - count(board, opp)
        return if (empties < 12) positional + mobility + discs * 8 else positional * 2 + mobility * 2
    }

    private fun search(board: IntArray, side: Int, me: Int, depth: Int, alphaIn: Int, betaIn: Int): Int {
        if (depth == 0) return evaluate(board, me)
        val moves = legalMoves(board, side)
        if (moves.isEmpty()) {
            if (legalMoves(board, other(side)).isEmpty()) return evaluate(board, me)
            return search(board, other(side), me, depth - 1, alphaIn, betaIn)
        }
        var alpha = alphaIn
        var beta = betaIn
        if (side == me) {
            var best = Int.MIN_VALUE
            for (m in moves.sortedByDescending { WEIGHTS[it] }) {
                val v = search(play(board, m, side)!!, other(side), me, depth - 1, alpha, beta)
                if (v > best) best = v
                if (best > alpha) alpha = best
                if (alpha >= beta) break
            }
            return best
        } else {
            var best = Int.MAX_VALUE
            for (m in moves.sortedByDescending { WEIGHTS[it] }) {
                val v = search(play(board, m, side)!!, other(side), me, depth - 1, alpha, beta)
                if (v < best) best = v
                if (best < beta) beta = best
                if (alpha >= beta) break
            }
            return best
        }
    }

    fun depthFor(difficulty: Difficulty, board: IntArray): Int {
        val empties = board.count { it == EMPTY }
        return when (difficulty) {
            Difficulty.EASY -> 1
            Difficulty.MEDIUM -> if (empties <= 8) 8 else 3
            Difficulty.HARD -> if (empties <= 12) 12 else 6
        }
    }

    /** Best move for [side], or null when it has to pass. */
    fun bestMove(board: IntArray, side: Int, difficulty: Difficulty, random: Random): Int? {
        val moves = legalMoves(board, side)
        if (moves.isEmpty()) return null
        if (difficulty == Difficulty.EASY && random.nextFloat() < 0.35f) return moves.random(random)
        val depth = depthFor(difficulty, board)
        var best = moves.first()
        var bestScore = Int.MIN_VALUE
        for (m in moves.shuffled(random)) {
            val v = search(play(board, m, side)!!, other(side), side, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE)
            if (v > bestScore) {
                bestScore = v
                best = m
            }
        }
        return best
    }
}
