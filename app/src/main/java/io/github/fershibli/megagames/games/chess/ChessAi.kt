package io.github.fershibli.megagames.games.chess

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.chess.Chess.BISHOP
import io.github.fershibli.megagames.games.chess.Chess.KING
import io.github.fershibli.megagames.games.chess.Chess.KNIGHT
import io.github.fershibli.megagames.games.chess.Chess.PAWN
import io.github.fershibli.megagames.games.chess.Chess.QUEEN
import io.github.fershibli.megagames.games.chess.Chess.ROOK
import kotlin.math.abs
import kotlin.random.Random

/**
 * Computer chess opponent: alpha-beta (negamax) with iterative deepening inside a time budget,
 * a quiescence search on captures so it does not stop in the middle of an exchange, move ordering
 * by "most valuable victim, least valuable attacker" plus killer moves, and an evaluation made of
 * material and piece-square tables (the well-known "simplified evaluation function").
 *
 * Levels: Easy looks one move ahead with some noise (and sometimes a random move); Medium three
 * moves; Hard as deep as the budget allows, usually four or five. The computer never cheats.
 */
object ChessAi {
    private const val MATE = 100_000
    private val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 20_000)

    // Tables are written from White's side with rank 8 on the first line, as usually printed.
    private val PST_PAWN = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val PST_KNIGHT = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50,
    )
    private val PST_BISHOP = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20,
    )
    private val PST_ROOK = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, 10, 10, 10, 10, 5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 0, 5, 5, 0, 0, 0,
    )
    private val PST_QUEEN = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20,
    )
    private val PST_KING_MIDDLE = intArrayOf(
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        20, 20, 0, 0, 0, 0, 20, 20,
        20, 30, 10, 0, 0, 10, 30, 20,
    )
    private val PST_KING_END = intArrayOf(
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50,
    )
    private val PST = arrayOf(IntArray(64), PST_PAWN, PST_KNIGHT, PST_BISHOP, PST_ROOK, PST_QUEEN, PST_KING_MIDDLE)

    /** Table index for a piece of [side] on [sq]. */
    private fun index(sq: Int, side: Int) = if (side == Chess.WHITE) (7 - Chess.rank(sq)) * 8 + Chess.file(sq) else sq

    /** Evaluation in centipawns from the point of view of the side to move. */
    fun evaluate(pos: ChessPosition): Int {
        val b = pos.board
        var score = 0
        var phase = 0 // 0 = endgame ... 24 = all pieces on the board
        for (sq in 0 until 64) {
            val t = abs(b[sq])
            phase += when (t) { KNIGHT, BISHOP -> 1; ROOK -> 2; QUEEN -> 4; else -> 0 }
        }
        phase = phase.coerceAtMost(24)
        for (sq in 0 until 64) {
            val v = b[sq]
            if (v == 0) continue
            val side = if (v > 0) Chess.WHITE else Chess.BLACK
            val t = abs(v)
            val i = index(sq, side)
            val s = if (t == KING) (PST_KING_MIDDLE[i] * phase + PST_KING_END[i] * (24 - phase)) / 24 else VALUE[t] + PST[t][i]
            score += s * side
        }
        return score * pos.side
    }

    private class Search(val deadline: Long, val history: Set<Long>) {
        var nodes = 0L
        var aborted = false
        val killers = Array(64) { IntArray(2) }

        private fun timeUp(): Boolean {
            if ((++nodes and 1023L) == 0L && System.nanoTime() > deadline) aborted = true
            return aborted
        }

        /** Ordering score: captures by victim value then cheap attacker, promotions, then killer moves. */
        private fun order(pos: ChessPosition, moves: IntArray, ply: Int, first: Int) {
            val keys = IntArray(moves.size) { k ->
                val m = moves[k]
                when {
                    m == first -> 1_000_000
                    Chess.isCapture(m) -> {
                        val victim = if (m and Chess.FLAG_EP != 0) PAWN else abs(pos.board[Chess.to(m)])
                        100_000 + VALUE[victim] * 10 - abs(pos.board[Chess.from(m)])
                    }
                    Chess.promo(m) != 0 -> 90_000 + Chess.promo(m)
                    ply < 64 && (m == killers[ply][0] || m == killers[ply][1]) -> 50_000
                    else -> 0
                }
            }
            // Insertion sort: move lists are short.
            for (i in 1 until moves.size) {
                val m = moves[i]
                val k = keys[i]
                var j = i - 1
                while (j >= 0 && keys[j] < k) {
                    moves[j + 1] = moves[j]
                    keys[j + 1] = keys[j]
                    j--
                }
                moves[j + 1] = m
                keys[j + 1] = k
            }
        }

        fun quiesce(pos: ChessPosition, alphaIn: Int, beta: Int, ply: Int): Int {
            if (timeUp()) return 0
            val stand = evaluate(pos)
            if (stand >= beta) return stand
            var alpha = maxOf(alphaIn, stand)
            if (ply > 40) return stand
            val moves = Chess.pseudoMoves(pos, capturesOnly = true)
            order(pos, moves, ply, 0)
            for (m in moves) {
                val next = Chess.play(pos, m)
                if (Chess.leftKingInCheck(next)) continue
                val v = -quiesce(next, -beta, -alpha, ply + 1)
                if (aborted) return 0
                if (v >= beta) return v
                if (v > alpha) alpha = v
            }
            return alpha
        }

        fun negamax(pos: ChessPosition, depthIn: Int, alphaIn: Int, beta: Int, ply: Int): Int {
            if (timeUp()) return 0
            if (ply > 0 && (pos.halfmove >= 100 || Chess.key(pos) in history)) return 0
            val check = Chess.inCheck(pos)
            val depth = if (check && ply < 12) depthIn + 1 else depthIn
            if (depth <= 0) return quiesce(pos, alphaIn, beta, ply)
            val moves = Chess.pseudoMoves(pos)
            order(pos, moves, ply, 0)
            var alpha = alphaIn
            var best = -MATE - 1
            var legal = 0
            for (m in moves) {
                val next = Chess.play(pos, m)
                if (Chess.leftKingInCheck(next)) continue
                legal++
                val v = -negamax(next, depth - 1, -beta, -alpha, ply + 1)
                if (aborted) return 0
                if (v > best) best = v
                if (v > alpha) alpha = v
                if (alpha >= beta) {
                    if (!Chess.isCapture(m) && ply < 64 && killers[ply][0] != m) {
                        killers[ply][1] = killers[ply][0]
                        killers[ply][0] = m
                    }
                    break
                }
            }
            if (legal == 0) return if (check) -MATE + ply else 0
            return best
        }

        fun root(pos: ChessPosition, moves: IntArray, depth: Int, scores: IntArray, exact: Boolean): Int {
            var alpha = -MATE - 1
            var best = -1
            for (k in moves.indices) {
                // [exact] scores every move fully (for Easy's noise); otherwise only the best one is exact.
                val v = -negamax(Chess.play(pos, moves[k]), depth - 1, -MATE - 1, if (exact) MATE + 1 else -alpha, 1)
                if (aborted) break
                scores[k] = v
                if (v > alpha) {
                    alpha = v
                    best = k
                }
            }
            return best
        }
    }

    /** Depth limit and time budget in milliseconds for each level. */
    private fun limits(difficulty: Difficulty): Pair<Int, Long> = when (difficulty) {
        Difficulty.EASY -> 1 to 300L
        Difficulty.MEDIUM -> 3 to 600L
        Difficulty.HARD -> 5 to 700L
    }

    /**
     * Best move for the side to move in [pos] (null when there is none). [history] holds the keys
     * of the earlier positions of the game, so a winning computer does not walk into a repetition.
     */
    fun bestMove(
        pos: ChessPosition,
        difficulty: Difficulty,
        random: Random,
        history: Set<Long> = emptySet(),
        budgetMs: Long? = null,
    ): Int? {
        val legal = Chess.legalMoves(pos).shuffled(random).toIntArray()
        if (legal.size <= 1) return legal.firstOrNull()
        if (difficulty == Difficulty.EASY && random.nextFloat() < 0.15f) return legal.random(random)
        val (maxDepth, defaultBudget) = limits(difficulty)
        val search = Search(System.nanoTime() + (budgetMs ?: defaultBudget) * 1_000_000, history)
        var moves = legal
        var best = moves[0]
        var bestScores = IntArray(moves.size)
        for (depth in 1..maxDepth) {
            val scores = IntArray(moves.size) { Int.MIN_VALUE }
            val k = search.root(pos, moves, depth, scores, difficulty == Difficulty.EASY)
            if (search.aborted) {
                // Usable when the previous best move (searched first) was finished.
                if (k >= 0 && scores[0] != Int.MIN_VALUE) best = moves[k]
                break
            }
            if (k >= 0) best = moves[k]
            bestScores = scores
            if (scores[k.coerceAtLeast(0)] >= MATE - 100) break
            // Next iteration: best moves first.
            val order = moves.indices.sortedByDescending { scores[it] }
            moves = IntArray(order.size) { moves[order[it]] }
            bestScores = IntArray(order.size) { scores[order[it]] }
        }
        if (difficulty == Difficulty.EASY) {
            // Some noise on the scores: Easy often misses the best move but rarely gives a piece away.
            var pick = best
            var top = Int.MIN_VALUE
            for (i in moves.indices) {
                if (bestScores[i] == Int.MIN_VALUE) continue
                val noisy = bestScores[i] + random.nextInt(-120, 121)
                if (noisy > top) { top = noisy; pick = moves[i] }
            }
            return pick
        }
        return best
    }
}
