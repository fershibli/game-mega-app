package io.github.fershibli.megagames.games.gomoku

import io.github.fershibli.megagames.core.Difficulty
import kotlin.random.Random

/**
 * Five in a row on a 15×15 board, free style: Black plays first and five or more stones in a line
 * (across, down or diagonally) win. The tournament restrictions on Black (no "double three",
 * "double four" or overlines) are not used.
 *
 * Cells are numbered `row * 15 + column`; values are 0 (empty), [BLACK] or [WHITE].
 */
object Gomoku {
    const val N = 15
    const val CELLS = N * N
    const val EMPTY = 0
    const val BLACK = 1
    const val WHITE = 2
    const val CENTER = (N / 2) * N + N / 2

    fun opponent(c: Int) = 3 - c

    /** The board after [moves] (Black first). */
    fun board(moves: List<Int>): IntArray {
        val b = IntArray(CELLS)
        moves.forEachIndexed { k, m -> if (m in 0 until CELLS) b[m] = if (k % 2 == 0) BLACK else WHITE }
        return b
    }

    fun colorOf(moveNumber: Int) = if (moveNumber % 2 == 0) BLACK else WHITE

    private val DIRS = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))

    /** The whole line of five or more through [at] (in order), or empty when there is none. */
    fun winningLine(board: IntArray, at: Int): List<Int> {
        val c = board.getOrNull(at) ?: return emptyList()
        if (c == EMPTY) return emptyList()
        val r0 = at / N
        val c0 = at % N
        for ((dr, dc) in DIRS) {
            var back = 0
            while (inside(r0 - (back + 1) * dr, c0 - (back + 1) * dc) && board[(r0 - (back + 1) * dr) * N + c0 - (back + 1) * dc] == c) back++
            var ahead = 0
            while (inside(r0 + (ahead + 1) * dr, c0 + (ahead + 1) * dc) && board[(r0 + (ahead + 1) * dr) * N + c0 + (ahead + 1) * dc] == c) ahead++
            if (back + ahead + 1 >= 5) return (-back..ahead).map { k -> (r0 + k * dr) * N + c0 + k * dc }
        }
        return emptyList()
    }

    private fun inside(r: Int, c: Int) = r in 0 until N && c in 0 until N

    /** Every run of five cells on the board; the computer scores the position window by window. */
    internal val WINDOWS: Array<IntArray> = buildList {
        for (r in 0 until N) for (c in 0 until N) for ((dr, dc) in DIRS) {
            if (inside(r + 4 * dr, c + 4 * dc)) add(IntArray(5) { k -> (r + k * dr) * N + c + k * dc })
        }
    }.toTypedArray()

    /** The windows each cell belongs to (up to 20). */
    internal val CELL_WINDOWS: Array<IntArray> = Array(CELLS) { cell -> WINDOWS.indices.filter { cell in WINDOWS[it] }.toIntArray() }

    /** Cells within two steps of each cell, for the list of moves worth looking at. */
    internal val AROUND: Array<IntArray> = Array(CELLS) { cell ->
        val r = cell / N
        val c = cell % N
        buildList { for (dr in -2..2) for (dc in -2..2) if ((dr != 0 || dc != 0) && inside(r + dr, c + dc)) add((r + dr) * N + c + dc) }.toIntArray()
    }
}

/**
 * The computer's five-in-a-row player.
 *
 * The position is scored over every window of five cells: a window that holds stones of one color
 * only is worth more the fuller it is. Counts are kept up to date stone by stone, so a move costs
 * about twenty small updates. On top of it:
 *
 * - a four (a window one stone from five) is always finished, and an opponent's four is always
 *   blocked (two of them at once lose), at every depth;
 * - Medium and Hard search with alpha-beta over the best-looking cells, deeper and deeper until
 *   the time runs out; Hard also looks for a win by an unbroken series of fours;
 * - Easy picks the most attractive cell, and now and then a worse one on purpose.
 */
object GomokuAi {
    private const val MEDIUM_MS = 250L
    private const val HARD_MS = 600L
    private const val WIN = 1_000_000

    /** Value of a window holding 0..5 stones of one color only. */
    private val SCORE = intArrayOf(0, 2, 24, 260, 3_000, WIN)

    fun bestMove(board: IntArray, c: Int, difficulty: Difficulty, random: Random): Int {
        if (board.all { it == Gomoku.EMPTY }) return Gomoku.CENTER
        val s = Search(board, Long.MAX_VALUE)
        s.completing(c).firstOrNull()?.let { return it }
        val blocks = s.completing(Gomoku.opponent(c))
        if (blocks.isNotEmpty() && (difficulty != Difficulty.EASY || random.nextFloat() < 0.85f)) return blocks.first()
        val ordered = s.ordered(c, 12)
        if (ordered.isEmpty()) return board.indexOfFirst { it == Gomoku.EMPTY }
        return when (difficulty) {
            Difficulty.EASY -> if (random.nextFloat() < 0.25f) ordered.take(6).random(random) else ordered.first()
            Difficulty.MEDIUM -> deepen(board, c, MEDIUM_MS, maxDepth = 4, random)
            Difficulty.HARD -> {
                val quick = Search(board, System.nanoTime() + HARD_MS * 1_000_000 / 4)
                quick.vcf(c, 10).takeIf { it >= 0 } ?: deepen(board, c, HARD_MS * 3 / 4, maxDepth = 10, random)
            }
        }
    }

    private fun deepen(board: IntArray, c: Int, budgetMs: Long, maxDepth: Int, random: Random): Int {
        val s = Search(board, System.nanoTime() + budgetMs * 1_000_000)
        var order = s.ordered(c, 12)
        // Equal-looking openings are shuffled a little so games differ.
        if (order.size > 2 && random.nextBoolean()) order = listOf(order[1], order[0]) + order.drop(2)
        var best = order.first()
        var depth = 2
        while (depth <= maxDepth) {
            var alpha = -WIN * 2
            var bestHere = -1
            val scores = HashMap<Int, Int>()
            for (m in order) {
                s.place(m, c)
                val v = -s.negamax(Gomoku.opponent(c), depth - 1, -WIN * 2, -alpha, 1)
                s.remove(m, c)
                if (s.aborted) break
                scores[m] = v
                if (v > alpha) {
                    alpha = v
                    bestHere = m
                }
            }
            if (s.aborted) {
                if (bestHere >= 0 && order.first() in scores) best = bestHere
                break
            }
            if (bestHere >= 0) best = bestHere
            if (alpha >= WIN / 2 || alpha <= -WIN / 2) break // the result is known
            order = order.sortedByDescending { scores[it] ?: Int.MIN_VALUE }
            depth += 2
        }
        return best
    }

    /** Window counts of a position, kept up to date while the search places and removes stones. */
    private class Search(board: IntArray, private val deadline: Long) {
        val b = board.copyOf()
        private val count = arrayOf(IntArray(0), IntArray(Gomoku.WINDOWS.size), IntArray(Gomoku.WINDOWS.size))
        /** Black's score minus White's. */
        private var score = 0
        private val near = IntArray(Gomoku.CELLS)
        private var nodes = 0
        var aborted = false
            private set

        init {
            for (cell in 0 until Gomoku.CELLS) if (b[cell] != Gomoku.EMPTY) {
                val c = b[cell]
                b[cell] = Gomoku.EMPTY
                place(cell, c)
            }
        }

        private fun value(w: Int): Int {
            val black = count[Gomoku.BLACK][w]
            val white = count[Gomoku.WHITE][w]
            return when {
                black > 0 && white > 0 -> 0
                black > 0 -> SCORE[black]
                else -> -SCORE[white]
            }
        }

        fun place(cell: Int, c: Int) {
            for (w in Gomoku.CELL_WINDOWS[cell]) {
                score -= value(w)
                count[c][w]++
                score += value(w)
            }
            b[cell] = c
            for (x in Gomoku.AROUND[cell]) near[x]++
        }

        fun remove(cell: Int, c: Int) {
            for (w in Gomoku.CELL_WINDOWS[cell]) {
                score -= value(w)
                count[c][w]--
                score += value(w)
            }
            b[cell] = Gomoku.EMPTY
            for (x in Gomoku.AROUND[cell]) near[x]--
        }

        /** Empty cells that would give [c] five at once (one per window holding four of [c] and nothing else). */
        fun completing(c: Int): List<Int> {
            val o = Gomoku.opponent(c)
            val out = ArrayList<Int>(2)
            val mine = count[c]
            val theirs = count[o]
            for (w in Gomoku.WINDOWS.indices) {
                if (mine[w] != 4 || theirs[w] != 0) continue
                for (x in Gomoku.WINDOWS[w]) if (b[x] == Gomoku.EMPTY && x !in out) out += x
            }
            return out
        }

        /** How attractive an empty cell is for [c]: what it builds plus what it blocks. */
        private fun priority(cell: Int, c: Int): Int {
            val o = Gomoku.opponent(c)
            var attack = 0
            var defence = 0
            for (w in Gomoku.CELL_WINDOWS[cell]) {
                val m = count[c][w]
                val t = count[o][w]
                if (t == 0) attack += SCORE[m + 1] - SCORE[m]
                if (m == 0) defence += SCORE[t + 1] - SCORE[t]
            }
            return attack + defence * 9 / 10
        }

        /** The [limit] most attractive empty cells near the stones, best first. */
        fun ordered(c: Int, limit: Int): List<Int> {
            val cells = ArrayList<Int>()
            val prio = ArrayList<Int>()
            for (cell in 0 until Gomoku.CELLS) {
                if (b[cell] != Gomoku.EMPTY || near[cell] == 0) continue
                cells += cell
                prio += priority(cell, c)
            }
            return cells.indices.sortedByDescending { prio[it] }.take(limit).map { cells[it] }
        }

        private fun timeUp(): Boolean {
            if (++nodes and 255 == 0 && System.nanoTime() > deadline) aborted = true
            return aborted
        }

        fun negamax(c: Int, depth: Int, alpha0: Int, beta: Int, ply: Int): Int {
            if (timeUp()) return 0
            val o = Gomoku.opponent(c)
            if (completing(c).isNotEmpty()) return WIN - ply
            val blocks = completing(o)
            if (blocks.size >= 2) return -(WIN - ply - 1)
            // A forced block does not use up depth (bounded, the board fills up).
            if (depth <= 0 && (blocks.isEmpty() || ply > 24)) return if (c == Gomoku.BLACK) score else -score
            val moves = if (blocks.size == 1) blocks else ordered(c, 9)
            if (moves.isEmpty()) return 0
            val nextDepth = if (blocks.size == 1) depth else depth - 1
            var alpha = alpha0
            var best = -WIN * 2
            for (m in moves) {
                place(m, c)
                val v = -negamax(o, nextDepth, -beta, -alpha, ply + 1)
                remove(m, c)
                if (aborted) return 0
                if (v > best) best = v
                if (v > alpha) alpha = v
                if (alpha >= beta) break
            }
            return best
        }

        /**
         * A first move that wins by fours alone: each move makes a four, the only answer is to
         * block it, and in the end two fours come at once. -1 when none is found within [depth].
         */
        fun vcf(c: Int, depth: Int): Int {
            if (depth <= 0 || timeUp()) return -1
            val o = Gomoku.opponent(c)
            if (completing(o).isNotEmpty()) return -1
            val tries = LinkedHashSet<Int>()
            for (w in Gomoku.WINDOWS.indices) {
                if (count[c][w] != 3 || count[o][w] != 0) continue
                for (x in Gomoku.WINDOWS[w]) if (b[x] == Gomoku.EMPTY) tries += x
            }
            for (m in tries) {
                place(m, c)
                val threats = completing(c)
                var wins = threats.size >= 2
                if (threats.size == 1) {
                    val block = threats.first()
                    place(block, o)
                    wins = completing(o).isEmpty() && vcf(c, depth - 1) >= 0
                    remove(block, o)
                }
                remove(m, c)
                if (aborted) return -1
                if (wins) return m
            }
            return -1
        }
    }
}
