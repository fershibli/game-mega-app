package io.github.fershibli.megagames.games.fourinrow

import io.github.fershibli.megagames.core.Difficulty
import kotlin.random.Random

/**
 * Four in a row: 7 columns of 6 holes; discs drop to the lowest free hole of the column played.
 * Four discs of one color in a line (across, up and down or diagonally) win; a full board is a
 * draw. The first player plays the light discs.
 *
 * For the screen the board is an array of `row * 7 + column` with row 0 at the top, values 0,
 * [FIRST] or [SECOND]. The computer works on bitboards (see [FourInRowAi]).
 */
object FourInRow {
    const val COLS = 7
    const val ROWS = 6
    const val EMPTY = 0
    const val FIRST = 1
    const val SECOND = 2

    fun opponent(side: Int) = 3 - side

    fun sideOf(moveNumber: Int) = if (moveNumber % 2 == 0) FIRST else SECOND

    /** The board after [moves] (columns), ignoring moves into full or missing columns. */
    fun board(moves: List<Int>): IntArray {
        val b = IntArray(ROWS * COLS)
        moves.forEachIndexed { k, col -> landingRow(b, col).takeIf { it >= 0 }?.let { b[it * COLS + col] = sideOf(k) } }
        return b
    }

    /** The row a disc dropped into [col] lands on, or -1 when the column is full. */
    fun landingRow(board: IntArray, col: Int): Int {
        if (col !in 0 until COLS) return -1
        for (r in ROWS - 1 downTo 0) if (board[r * COLS + col] == EMPTY) return r
        return -1
    }

    private val DIRS = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))

    /** The cells of the line of four or more through [cell], or empty. */
    fun winningLine(board: IntArray, cell: Int): List<Int> {
        val side = board.getOrNull(cell) ?: return emptyList()
        if (side == EMPTY) return emptyList()
        val r0 = cell / COLS
        val c0 = cell % COLS
        fun at(r: Int, c: Int) = r in 0 until ROWS && c in 0 until COLS && board[r * COLS + c] == side
        for ((dr, dc) in DIRS) {
            var back = 0
            while (at(r0 - (back + 1) * dr, c0 - (back + 1) * dc)) back++
            var ahead = 0
            while (at(r0 + (ahead + 1) * dr, c0 + (ahead + 1) * dc)) ahead++
            if (back + ahead + 1 >= 4) return (-back..ahead).map { k -> (r0 + k * dr) * COLS + c0 + k * dc }
        }
        return emptyList()
    }
}

/**
 * The computer's four-in-a-row player: negamax with alpha-beta on bitboards, a transposition
 * table and iterative deepening within a time budget.
 *
 * Bitboards follow the classic layout: each column takes 7 bits (6 holes and a spare one on top),
 * bit `column * 7 + row` with row 0 at the bottom. A position is the side to move's discs plus the
 * mask of all discs. Leaves are scored by "threats" (empty holes that would complete a four) and
 * by how central the discs are.
 *
 * - Easy always takes a win, often blocks, and otherwise looks only two moves ahead, sometimes
 *   choosing a column at random.
 * - Medium looks six moves ahead.
 * - Hard searches as deep as it can in about two thirds of a second.
 */
object FourInRowAi {
    private const val H = FourInRow.ROWS
    private const val H1 = H + 1
    private const val WIN = 10_000
    private const val MEDIUM_DEPTH = 6
    private const val HARD_MS = 650L
    private const val TABLE_BITS = 18

    private val BOTTOM: Long = (0 until FourInRow.COLS).fold(0L) { acc, c -> acc or (1L shl (c * H1)) }
    private val BOARD: Long = BOTTOM * ((1L shl H) - 1)
    private val ORDER = intArrayOf(3, 2, 4, 1, 5, 0, 6)

    /** Holes grouped by how many lines of four pass through them (central holes are worth more). */
    private val WEIGHTS = arrayOf(
        intArrayOf(3, 4, 5, 7, 5, 4, 3),
        intArrayOf(4, 6, 8, 10, 8, 6, 4),
        intArrayOf(5, 8, 11, 13, 11, 8, 5),
        intArrayOf(5, 8, 11, 13, 11, 8, 5),
        intArrayOf(4, 6, 8, 10, 8, 6, 4),
        intArrayOf(3, 4, 5, 7, 5, 4, 3),
    )
    private val WEIGHT_MASKS: List<Pair<Int, Long>> = WEIGHTS.flatMapIndexed { r, row -> row.mapIndexed { c, w -> w to (1L shl (c * H1 + (H - 1 - r))) } }
        .groupBy({ it.first }, { it.second }).map { (w, bits) -> w to bits.fold(0L) { a, b -> a or b } }

    private fun column(col: Int): Long = ((1L shl H) - 1) shl (col * H1)

    /** The bitboards (side to move's discs, all discs) of a screen board where [side] is to move. */
    private fun bits(board: IntArray, side: Int): LongArray {
        var mine = 0L
        var mask = 0L
        for (r in 0 until H) for (c in 0 until FourInRow.COLS) {
            val v = board[r * FourInRow.COLS + c]
            if (v == FourInRow.EMPTY) continue
            val bit = 1L shl (c * H1 + (H - 1 - r))
            mask = mask or bit
            if (v == side) mine = mine or bit
        }
        return longArrayOf(mine, mask)
    }

    private fun possible(mask: Long) = (mask + BOTTOM) and BOARD

    /** Empty holes that would complete a four for the discs [pos]. */
    private fun winning(pos: Long, mask: Long): Long {
        var r = (pos shl 1) and (pos shl 2) and (pos shl 3)
        for (d in intArrayOf(H1, H, H + 2)) {
            var p = (pos shl d) and (pos shl 2 * d)
            r = r or (p and (pos shl 3 * d))
            r = r or (p and (pos ushr d))
            p = (pos ushr d) and (pos ushr 2 * d)
            r = r or (p and (pos shl d))
            r = r or (p and (pos ushr 3 * d))
        }
        return r and (BOARD xor mask)
    }

    /** Best column for [side] on a screen [board]. */
    fun bestMove(board: IntArray, side: Int, difficulty: Difficulty, random: Random): Int {
        val legal = (0 until FourInRow.COLS).filter { FourInRow.landingRow(board, it) >= 0 }
        if (legal.size <= 1) return legal.firstOrNull() ?: -1
        val (pos, mask) = bits(board, side).let { it[0] to it[1] }
        val moves = board.count { it != FourInRow.EMPTY }
        val poss = possible(mask)
        fun colOf(bit: Long) = java.lang.Long.numberOfTrailingZeros(bit) / H1
        // A win now, at every level.
        val wins = winning(pos, mask) and poss
        if (wins != 0L) return colOf(wins)
        val threats = winning(pos xor mask, mask) and poss
        if (threats != 0L && (difficulty != Difficulty.EASY || random.nextFloat() < 0.7f)) return colOf(threats)
        // Columns that do not hand the opponent a win right above.
        val oppWin = winning(pos xor mask, mask)
        val safe = legal.filter { c -> (poss and column(c) and (oppWin ushr 1)) == 0L }.ifEmpty { legal }
        return when (difficulty) {
            Difficulty.EASY -> if (random.nextFloat() < 0.35f) safe.random(random) else Search(Long.MAX_VALUE).root(pos, mask, moves, 2, safe, random)
            Difficulty.MEDIUM -> Search(Long.MAX_VALUE).root(pos, mask, moves, MEDIUM_DEPTH, safe, random)
            Difficulty.HARD -> Search(System.nanoTime() + HARD_MS * 1_000_000).root(pos, mask, moves, FourInRow.ROWS * FourInRow.COLS, safe, random)
        }
    }

    private class Search(private val deadline: Long) {
        private val keys = LongArray(1 shl TABLE_BITS)
        /** Packed entry: value (16 bits), depth (8 bits), bound (2 bits), best column (3 bits). */
        private val entries = IntArray(1 shl TABLE_BITS)
        private var nodes = 0
        var aborted = false

        /** Iterative deepening at the root; the best column of the last finished depth. */
        fun root(pos: Long, mask: Long, moves: Int, maxDepth: Int, legal: List<Int>, random: Random): Int {
            var order = legal.sortedBy { ORDER.indexOf(it) }
            // Two equally central first moves are sometimes swapped so games differ.
            if (order.size > 2 && random.nextFloat() < 0.3f) order = listOf(order[1], order[0]) + order.drop(2)
            var best = order.first()
            for (depth in 1..maxDepth) {
                var alpha = -WIN - 1
                var bestHere = -1
                val scores = HashMap<Int, Int>()
                for (c in order) {
                    val move = possible(mask) and column(c)
                    val v = -negamax(pos xor mask, mask or move, moves + 1, depth - 1, -WIN - 1, -alpha)
                    if (aborted) break
                    scores[c] = v
                    if (v > alpha) {
                        alpha = v
                        bestHere = c
                    }
                }
                if (aborted) {
                    if (bestHere >= 0 && order.first() in scores) best = bestHere
                    break
                }
                if (bestHere >= 0) best = bestHere
                if (alpha >= WIN - 100 || alpha <= -WIN + 100) break // the outcome is known
                if (moves + depth >= FourInRow.ROWS * FourInRow.COLS) break // searched to the end
                order = order.sortedByDescending { scores[it] ?: Int.MIN_VALUE }
            }
            return best
        }

        private fun evaluate(pos: Long, mask: Long): Int {
            val opp = pos xor mask
            var v = (java.lang.Long.bitCount(winning(pos, mask)) - java.lang.Long.bitCount(winning(opp, mask))) * 16
            for ((w, bits) in WEIGHT_MASKS) v += w * (java.lang.Long.bitCount(pos and bits) - java.lang.Long.bitCount(opp and bits))
            return v
        }

        /** Score for the side to move ([pos] are its discs). */
        fun negamax(pos: Long, mask: Long, moves: Int, depth: Int, alpha0: Int, beta0: Int): Int {
            if (++nodes and 1023 == 0 && System.nanoTime() > deadline) aborted = true
            if (aborted) return 0
            if (moves >= FourInRow.ROWS * FourInRow.COLS) return 0
            val poss = possible(mask)
            if (winning(pos, mask) and poss != 0L) return WIN - moves - 1
            val opp = pos xor mask
            val oppWin = winning(opp, mask)
            var next = poss
            val forced = poss and oppWin
            if (forced != 0L) {
                if (forced and (forced - 1) != 0L) return -(WIN - moves - 2) // two threats: lost
                next = forced
            }
            next = next and (oppWin ushr 1).inv()
            if (next == 0L) return -(WIN - moves - 2)
            if (depth <= 0) return evaluate(pos, mask)

            var alpha = alpha0
            var beta = beta0
            val key = pos + mask
            val slot = (key xor (key ushr 29)).toInt() and ((1 shl TABLE_BITS) - 1)
            var hashMove = -1
            if (keys[slot] == key) {
                val e = entries[slot]
                val value = (e shr 16).toShort().toInt()
                val d = (e shr 8) and 0xFF
                val bound = (e shr 3) and 3
                hashMove = e and 7
                if (d >= depth) {
                    when (bound) {
                        EXACT -> return value
                        LOWER -> if (value > alpha) alpha = value
                        UPPER -> if (value < beta) beta = value
                    }
                    if (alpha >= beta) return value
                }
            }
            var best = -WIN - 1
            var bestCol = 7
            for (k in -1 until FourInRow.COLS) {
                val c = if (k < 0) hashMove else ORDER[k]
                if (c !in 0 until FourInRow.COLS || (k >= 0 && c == hashMove)) continue
                val move = next and column(c)
                if (move == 0L) continue
                val v = -negamax(opp, mask or move, moves + 1, depth - 1, -beta, -alpha)
                if (aborted) return 0
                if (v > best) {
                    best = v
                    bestCol = c
                }
                if (v > alpha) alpha = v
                if (alpha >= beta) break
            }
            val bound = when {
                best <= alpha0 -> UPPER
                best >= beta -> LOWER
                else -> EXACT
            }
            keys[slot] = key
            entries[slot] = ((best and 0xFFFF) shl 16) or (minOf(depth, 255) shl 8) or (bound shl 3) or bestCol
            return best
        }
    }

    private const val EXACT = 0
    private const val LOWER = 1
    private const val UPPER = 2
}
