package io.github.fershibli.megagames.games.blocks

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** A piece offered below the board: one of [Blocks.SHAPES], turned [turns] quarter turns, in a color. */
@Serializable
data class BlocksPiece(val shape: Int, val turns: Int, val color: Int)

/**
 * Block placement puzzle. Nothing falls and there is no clock: the player places three offered
 * pieces anywhere they fit, every full row and column is cleared, and three new pieces come when
 * the three are used. The game ends when none of the pieces left fits.
 */
@Serializable
data class BlocksState(
    val size: Int,
    val difficulty: Difficulty,
    /** Row by row, 0 = empty, otherwise the color of the block + 1. */
    val board: List<Int>,
    /** The three offered pieces; null once placed. */
    val tray: List<BlocksPiece?>,
    val score: Int = 0,
    val lines: Int = 0,
    /** Pieces are dealt from [seed] and the number of trays already [dealt], so a restored game continues identically. */
    val seed: Long,
    val dealt: Int = 0,
    val over: Boolean = false,
    /** Best score of this level when the game started (a higher score at the end is a new record). */
    val recordToBeat: Int = 0,
) {
    val canRotate get() = difficulty == Difficulty.EASY
}

/** Where a piece goes: the cell of the board under the top-left corner of its box. */
data class Placement(val slot: Int, val row: Int, val col: Int)

object Blocks {
    /**
     * The piece set: small squares, lines, corners and a few larger shapes, from 1 to 5 blocks.
     * Rows are separated by '|'. Pieces are dealt in any of their four turns.
     */
    val SHAPES: List<String> = listOf(
        "X", // 0 single block
        "XX", // 1 two
        "XXX", // 2 line of three
        "XXXX", // 3 line of four
        "XXXXX", // 4 line of five
        "X.|XX", // 5 small corner
        "XX|XX", // 6 small square
        "X..|X..|XXX", // 7 big corner
        "X.|X.|XX", // 8 long corner
        ".X.|XXX|.X.", // 9 plus
        "X.X|XXX", // 10 cup
        "XX|XX|X.", // 11 square with a tail
    )

    /** How often each shape is dealt on each level: Easy mostly small pieces, Hard more large ones. */
    private val WEIGHTS = mapOf(
        Difficulty.EASY to intArrayOf(6, 6, 5, 2, 0, 6, 5, 0, 2, 0, 0, 0),
        Difficulty.MEDIUM to intArrayOf(3, 4, 4, 3, 2, 4, 4, 2, 3, 1, 1, 2),
        Difficulty.HARD to intArrayOf(1, 2, 3, 3, 3, 3, 3, 3, 3, 2, 2, 3),
    )

    const val COLORS = 6

    fun sizeFor(d: Difficulty) = if (d == Difficulty.EASY) 9 else 10

    /** Cells of every shape in every turn, as (row, col) pairs, with the box starting at (0, 0). */
    private val CELLS: List<List<IntArray>> = SHAPES.map { shape ->
        val rows = shape.split('|')
        var cells = buildList { rows.forEachIndexed { r, line -> line.forEachIndexed { c, ch -> if (ch == 'X') add(r to c) } } }
        List(4) {
            val turn = cells
            // Next quarter turn clockwise: (r, c) -> (c, -r), moved back to the top-left corner.
            val turned = cells.map { (r, c) -> c to -r }
            val minR = turned.minOf { it.first }
            val minC = turned.minOf { it.second }
            cells = turned.map { (r, c) -> (r - minR) to (c - minC) }
            turn.flatMap { listOf(it.first, it.second) }.toIntArray()
        }
    }

    /** Cells of [piece] as (row, col) pairs packed in one array: r0, c0, r1, c1... */
    fun cells(piece: BlocksPiece): IntArray = CELLS[piece.shape][piece.turns and 3]

    fun width(piece: BlocksPiece): Int = cells(piece).let { a -> (1 until a.size step 2).maxOf { a[it] } + 1 }
    fun height(piece: BlocksPiece): Int = cells(piece).let { a -> (a.indices step 2).maxOf { a[it] } + 1 }

    fun newGame(d: Difficulty, seed: Long, recordToBeat: Int = 0): BlocksState {
        val size = sizeFor(d)
        val empty = List(size * size) { 0 }
        val (tray, dealt) = deal(d, seed, 0, empty, size)
        return BlocksState(size, d, empty, tray, seed = seed, dealt = dealt, recordToBeat = recordToBeat)
    }

    /**
     * Deals three pieces. Easy and Medium make sure at least one of them fits the board (when
     * some piece can), so the game does not end on bad luck alone.
     */
    private fun deal(d: Difficulty, seed: Long, dealt: Int, board: List<Int>, size: Int): Pair<List<BlocksPiece?>, Int> {
        val weights = WEIGHTS.getValue(d)
        val total = weights.sum()
        var n = dealt
        var tray: List<BlocksPiece?> = emptyList()
        repeat(if (d == Difficulty.HARD) 1 else 30) {
            val random = Random(seed * 1_000_003L + n * 7_919L)
            n++
            tray = List(3) {
                var pick = random.nextInt(total)
                var shape = 0
                while (pick >= weights[shape]) { pick -= weights[shape]; shape++ }
                BlocksPiece(shape, random.nextInt(4), random.nextInt(COLORS))
            }
            if (tray.any { fitsAnywhere(board, size, it!!, d == Difficulty.EASY) }) return tray to n
        }
        return tray to n
    }

    fun fits(board: List<Int>, size: Int, piece: BlocksPiece, row: Int, col: Int): Boolean {
        val a = cells(piece)
        for (i in a.indices step 2) {
            val r = row + a[i]
            val c = col + a[i + 1]
            if (r !in 0 until size || c !in 0 until size || board[r * size + c] != 0) return false
        }
        return true
    }

    fun fitsAnywhere(board: List<Int>, size: Int, piece: BlocksPiece, anyTurn: Boolean = false): Boolean {
        val turns = if (anyTurn) 0..3 else 0..0
        for (t in turns) {
            val turned = piece.copy(turns = (piece.turns + t) and 3)
            for (r in 0 until size) for (c in 0 until size) if (fits(board, size, turned, r, c)) return true
        }
        return false
    }

    /** Rows and columns that would be full after placing [piece] at ([row], [col]) (rows first, then size + column). */
    fun linesCompleted(board: List<Int>, size: Int, piece: BlocksPiece, row: Int, col: Int): List<Int> {
        val filled = BooleanArray(size * size) { board[it] != 0 }
        val a = cells(piece)
        for (i in a.indices step 2) filled[(row + a[i]) * size + col + a[i + 1]] = true
        return buildList {
            for (r in 0 until size) if ((0 until size).all { filled[r * size + it] }) add(r)
            for (c in 0 until size) if ((0 until size).all { filled[it * size + c] }) add(size + c)
        }
    }

    /** Points for clearing [lines] lines at once: 10, 30, 60, 100... */
    fun linePoints(lines: Int) = 10 * lines * (lines + 1) / 2

    /** The board after putting [piece] at ([row], [col]) and clearing the full lines. */
    fun boardAfter(board: List<Int>, size: Int, piece: BlocksPiece, row: Int, col: Int, lines: List<Int>): List<Int> {
        val next = board.toMutableList()
        val a = cells(piece)
        for (i in a.indices step 2) next[(row + a[i]) * size + col + a[i + 1]] = piece.color + 1
        for (line in lines) {
            if (line < size) for (c in 0 until size) next[line * size + c] = 0
            else for (r in 0 until size) next[r * size + line - size] = 0
        }
        return next
    }

    /** Places the piece of [slot]; null when it does not fit there. */
    fun place(s: BlocksState, slot: Int, row: Int, col: Int): BlocksState? {
        val piece = s.tray.getOrNull(slot) ?: return null
        if (s.over || !fits(s.board, s.size, piece, row, col)) return null
        val lines = linesCompleted(s.board, s.size, piece, row, col)
        val board = boardAfter(s.board, s.size, piece, row, col, lines)
        var tray = s.tray.toMutableList().also { it[slot] = null }.toList()
        var dealt = s.dealt
        if (tray.all { it == null }) deal(s.difficulty, s.seed, s.dealt, board, s.size).let { tray = it.first; dealt = it.second }
        val next = s.copy(
            board = board, tray = tray, dealt = dealt,
            score = s.score + cells(piece).size / 2 + linePoints(lines.size), lines = s.lines + lines.size,
        )
        return next.copy(over = isStuck(next))
    }

    /** True when no piece left in the tray fits anywhere (on Easy, in any turn). */
    fun isStuck(s: BlocksState): Boolean = s.tray.filterNotNull().none { fitsAnywhere(s.board, s.size, it, s.canRotate) }

    /** Turns the piece of [slot] a quarter turn clockwise (Easy only). */
    fun rotate(s: BlocksState, slot: Int): BlocksState {
        val piece = s.tray.getOrNull(slot) ?: return s
        if (!s.canRotate || s.over) return s
        return s.copy(tray = s.tray.toMutableList().also { it[slot] = piece.copy(turns = (piece.turns + 1) and 3) })
    }

    /**
     * The placement covering [row], [col] whose box is best centered on it, for "tap a piece, then
     * tap the board": the player does not have to aim at the piece's corner.
     */
    fun placementAround(s: BlocksState, slot: Int, row: Int, col: Int): Placement? {
        val piece = s.tray.getOrNull(slot) ?: return null
        val a = cells(piece)
        val w = width(piece)
        val h = height(piece)
        var best: Placement? = null
        var bestDistance = Float.MAX_VALUE
        for (i in a.indices step 2) {
            val r0 = row - a[i]
            val c0 = col - a[i + 1]
            if (!fits(s.board, s.size, piece, r0, c0)) continue
            val dr = r0 + (h - 1) / 2f - row
            val dc = c0 + (w - 1) / 2f - col
            val d = dr * dr + dc * dc
            if (d < bestDistance) { bestDistance = d; best = Placement(slot, r0, c0) }
        }
        return best
    }

    /**
     * Hint: the placement that clears the most lines, then the one that leaves the fewest lonely
     * empty cells (a cell with every neighbor filled is hard to fill later).
     */
    fun bestMove(s: BlocksState): Placement? {
        var best: Placement? = null
        var bestScore = Int.MIN_VALUE
        for (slot in s.tray.indices) {
            val piece = s.tray[slot] ?: continue
            for (r in 0 until s.size) for (c in 0 until s.size) {
                if (!fits(s.board, s.size, piece, r, c)) continue
                val lines = linesCompleted(s.board, s.size, piece, r, c)
                val after = boardAfter(s.board, s.size, piece, r, c, lines)
                val score = lines.size * 1000 - holes(after, s.size) * 10 + touching(s.board, s.size, piece, r, c)
                if (score > bestScore) { bestScore = score; best = Placement(slot, r, c) }
            }
        }
        return best
    }

    /** Empty cells walled in on all four sides. */
    private fun holes(board: List<Int>, size: Int): Int {
        fun filled(r: Int, c: Int) = r !in 0 until size || c !in 0 until size || board[r * size + c] != 0
        var n = 0
        for (r in 0 until size) for (c in 0 until size) {
            if (board[r * size + c] == 0 && filled(r - 1, c) && filled(r + 1, c) && filled(r, c - 1) && filled(r, c + 1)) n++
        }
        return n
    }

    /** Sides of the piece touching the edge or other blocks: snug placements keep the board tidy. */
    private fun touching(board: List<Int>, size: Int, piece: BlocksPiece, row: Int, col: Int): Int {
        val a = cells(piece)
        val mine = HashSet<Int>()
        for (i in a.indices step 2) mine += (row + a[i]) * size + col + a[i + 1]
        var n = 0
        for (cell in mine) {
            val r = cell / size
            val c = cell % size
            for ((nr, nc) in arrayOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)) {
                if (nr !in 0 until size || nc !in 0 until size) n++
                else if (nr * size + nc !in mine && board[nr * size + nc] != 0) n++
            }
        }
        return n
    }
}
