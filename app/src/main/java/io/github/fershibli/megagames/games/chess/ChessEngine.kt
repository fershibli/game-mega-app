package io.github.fershibli.megagames.games.chess

import kotlin.math.abs
import kotlin.random.Random

/**
 * A chess position. Squares are numbered 0..63 from a1 (0), b1 (1)... to h8 (63). Pieces are
 * [Chess.PAWN]..[Chess.KING], positive for White and negative for Black. Positions are immutable:
 * [Chess.play] returns a new one, which keeps the search and the undo simple.
 */
class ChessPosition(
    val board: IntArray,
    val side: Int,
    /** Castling rights: bit 1 = White king side, 2 = White queen side, 4 = Black king side, 8 = Black queen side. */
    val castling: Int,
    /** En passant target square, or -1. */
    val ep: Int,
    /** Half-moves since the last capture or pawn move (fifty-move rule). */
    val halfmove: Int,
    val fullmove: Int,
    val whiteKing: Int,
    val blackKing: Int,
) {
    fun king(side: Int) = if (side == Chess.WHITE) whiteKing else blackKing
}

/**
 * Chess rules: legal moves (castling, en passant, promotion), check, checkmate, stalemate, the
 * draw rules, standard algebraic notation (to read and show game records) and FEN.
 *
 * Moves are packed in an Int: from (bits 0-5), to (6-11), promotion piece (12-14) and flags.
 */
object Chess {
    const val WHITE = 1
    const val BLACK = -1
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6

    const val FLAG_CAPTURE = 1 shl 15
    const val FLAG_EP = 1 shl 16
    const val FLAG_CASTLE = 1 shl 17
    const val FLAG_DOUBLE = 1 shl 18

    fun from(m: Int) = m and 63
    fun to(m: Int) = (m shr 6) and 63
    fun promo(m: Int) = (m shr 12) and 7
    fun isCapture(m: Int) = m and FLAG_CAPTURE != 0
    private fun move(from: Int, to: Int, promo: Int = 0, flags: Int = 0) = from or (to shl 6) or (promo shl 12) or flags

    fun file(sq: Int) = sq and 7
    fun rank(sq: Int) = sq shr 3
    fun squareName(sq: Int) = "${'a' + file(sq)}${rank(sq) + 1}"

    const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    fun start(): ChessPosition = fromFen(START_FEN)

    // ----- Precomputed attack tables -------------------------------------------------------

    private val KNIGHT_STEPS = arrayOf(intArrayOf(1, 2), intArrayOf(2, 1), intArrayOf(2, -1), intArrayOf(1, -2), intArrayOf(-1, -2), intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, 2))
    /** Directions as (file, rank) steps: the first four are straight (rook), the last four diagonal (bishop). */
    private val DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1), intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1))

    private fun steps(sq: Int, deltas: Array<IntArray>): IntArray =
        deltas.mapNotNull { (df, dr) ->
            val f = file(sq) + df
            val r = rank(sq) + dr
            if (f in 0..7 && r in 0..7) r * 8 + f else null
        }.toIntArray()

    private val KNIGHT_TARGETS = Array(64) { steps(it, KNIGHT_STEPS) }
    private val KING_TARGETS = Array(64) { steps(it, DIRS) }

    /** RAYS[sq][dir]: squares from [sq] (excluded) to the edge of the board along DIRS[dir]. */
    private val RAYS = Array(64) { sq ->
        Array(8) { d ->
            val out = ArrayList<Int>()
            var f = file(sq) + DIRS[d][0]
            var r = rank(sq) + DIRS[d][1]
            while (f in 0..7 && r in 0..7) {
                out += r * 8 + f
                f += DIRS[d][0]
                r += DIRS[d][1]
            }
            out.toIntArray()
        }
    }

    /** Whether [sq] is attacked by any piece of side [by]. */
    fun isAttacked(board: IntArray, sq: Int, by: Int): Boolean {
        // Pawns: a White pawn attacks from one rank below, a Black pawn from one rank above.
        val pr = rank(sq) - by
        if (pr in 0..7) {
            val f = file(sq)
            if (f > 0 && board[pr * 8 + f - 1] == by * PAWN) return true
            if (f < 7 && board[pr * 8 + f + 1] == by * PAWN) return true
        }
        for (t in KNIGHT_TARGETS[sq]) if (board[t] == by * KNIGHT) return true
        for (t in KING_TARGETS[sq]) if (board[t] == by * KING) return true
        val rays = RAYS[sq]
        for (d in 0 until 8) {
            for (t in rays[d]) {
                val v = board[t]
                if (v == 0) continue
                if (v * by > 0) {
                    val type = abs(v)
                    if (type == QUEEN || (d < 4 && type == ROOK) || (d >= 4 && type == BISHOP)) return true
                }
                break
            }
        }
        return false
    }

    fun inCheck(pos: ChessPosition) = isAttacked(pos.board, pos.king(pos.side), -pos.side)

    // ----- Move generation -----------------------------------------------------------------

    /** Moves that follow the movement rules, possibly leaving the own king in check. */
    fun pseudoMoves(pos: ChessPosition, capturesOnly: Boolean = false): IntArray {
        val out = IntArray(256)
        var n = 0
        val b = pos.board
        val side = pos.side
        for (sq in 0 until 64) {
            val v = b[sq]
            if (v * side <= 0) continue
            when (abs(v)) {
                PAWN -> {
                    val dir = 8 * side
                    val one = sq + dir
                    val lastRank = if (side == WHITE) 7 else 0
                    val startRank = if (side == WHITE) 1 else 6
                    if (one in 0..63 && b[one] == 0) {
                        if (rank(one) == lastRank) {
                            for (p in intArrayOf(QUEEN, KNIGHT, ROOK, BISHOP)) out[n++] = move(sq, one, p)
                        } else if (!capturesOnly) {
                            out[n++] = move(sq, one)
                            val two = one + dir
                            if (rank(sq) == startRank && b[two] == 0) out[n++] = move(sq, two, 0, FLAG_DOUBLE)
                        }
                    }
                    for (df in intArrayOf(-1, 1)) {
                        val f = file(sq) + df
                        if (f !in 0..7 || one !in 0..63) continue
                        val t = rank(one) * 8 + f
                        if (b[t] * side < 0) {
                            if (rank(t) == lastRank) {
                                for (p in intArrayOf(QUEEN, KNIGHT, ROOK, BISHOP)) out[n++] = move(sq, t, p, FLAG_CAPTURE)
                            } else out[n++] = move(sq, t, 0, FLAG_CAPTURE)
                        } else if (t == pos.ep) {
                            out[n++] = move(sq, t, 0, FLAG_CAPTURE or FLAG_EP)
                        }
                    }
                }
                KNIGHT -> for (t in KNIGHT_TARGETS[sq]) {
                    if (b[t] * side < 0) out[n++] = move(sq, t, 0, FLAG_CAPTURE)
                    else if (b[t] == 0 && !capturesOnly) out[n++] = move(sq, t)
                }
                KING -> {
                    for (t in KING_TARGETS[sq]) {
                        if (b[t] * side < 0) out[n++] = move(sq, t, 0, FLAG_CAPTURE)
                        else if (b[t] == 0 && !capturesOnly) out[n++] = move(sq, t)
                    }
                    if (!capturesOnly) n = castling(pos, sq, out, n)
                }
                else -> {
                    val type = abs(v)
                    val first = if (type == BISHOP) 4 else 0
                    val last = if (type == ROOK) 3 else 7
                    for (d in first..last) {
                        for (t in RAYS[sq][d]) {
                            val w = b[t]
                            if (w == 0) {
                                if (!capturesOnly) out[n++] = move(sq, t)
                                continue
                            }
                            if (w * side < 0) out[n++] = move(sq, t, 0, FLAG_CAPTURE)
                            break
                        }
                    }
                }
            }
        }
        return out.copyOf(n)
    }

    private fun castling(pos: ChessPosition, sq: Int, out: IntArray, start: Int): Int {
        var n = start
        val b = pos.board
        val side = pos.side
        val home = if (side == WHITE) 4 else 60
        if (sq != home) return n
        val kingSide = if (side == WHITE) 1 else 4
        val queenSide = if (side == WHITE) 2 else 8
        if (pos.castling and (kingSide or queenSide) == 0) return n
        if (isAttacked(b, home, -side)) return n
        if (pos.castling and kingSide != 0 && b[home + 1] == 0 && b[home + 2] == 0 && b[home + 3] == side * ROOK &&
            !isAttacked(b, home + 1, -side) && !isAttacked(b, home + 2, -side)
        ) out[n++] = move(home, home + 2, 0, FLAG_CASTLE)
        if (pos.castling and queenSide != 0 && b[home - 1] == 0 && b[home - 2] == 0 && b[home - 3] == 0 && b[home - 4] == side * ROOK &&
            !isAttacked(b, home - 1, -side) && !isAttacked(b, home - 2, -side)
        ) out[n++] = move(home, home - 2, 0, FLAG_CASTLE)
        return n
    }

    /** The position after [m] (which must be at least pseudo-legal). */
    fun play(pos: ChessPosition, m: Int): ChessPosition {
        val b = pos.board.copyOf()
        val from = from(m)
        val to = to(m)
        val piece = b[from]
        val side = pos.side
        val captured = b[to]
        b[from] = 0
        b[to] = if (promo(m) != 0) side * promo(m) else piece
        if (m and FLAG_EP != 0) b[to - 8 * side] = 0
        if (m and FLAG_CASTLE != 0) {
            if (to > from) { b[from + 1] = b[from + 3]; b[from + 3] = 0 } else { b[from - 1] = b[from - 4]; b[from - 4] = 0 }
        }
        var rights = pos.castling
        if (abs(piece) == KING) rights = rights and (if (side == WHITE) 3 else 12).inv()
        for (sq in intArrayOf(from, to)) {
            when (sq) {
                0 -> rights = rights and 2.inv()
                7 -> rights = rights and 1.inv()
                56 -> rights = rights and 8.inv()
                63 -> rights = rights and 4.inv()
            }
        }
        val ep = if (m and FLAG_DOUBLE != 0) (from + to) / 2 else -1
        val half = if (abs(piece) == PAWN || captured != 0) 0 else pos.halfmove + 1
        val wk = if (piece == KING) to else pos.whiteKing
        val bk = if (piece == -KING) to else pos.blackKing
        return ChessPosition(b, -side, rights, ep, half, pos.fullmove + if (side == BLACK) 1 else 0, wk, bk)
    }

    /** Whether the side that just moved left its own king in check (the move was illegal). */
    fun leftKingInCheck(after: ChessPosition) = isAttacked(after.board, after.king(-after.side), after.side)

    fun legalMoves(pos: ChessPosition): List<Int> = pseudoMoves(pos).filter { !leftKingInCheck(play(pos, it)) }

    // ----- End of game ---------------------------------------------------------------------

    enum class Status { PLAYING, CHECKMATE, STALEMATE, INSUFFICIENT, FIFTY_MOVES, REPETITION }

    /** Neither side can possibly checkmate: K v K, K+minor v K, K+B v K+B with bishops on the same color. */
    fun insufficientMaterial(board: IntArray): Boolean {
        val minors = ArrayList<Int>()
        for (sq in 0 until 64) {
            when (abs(board[sq])) {
                0, KING -> {}
                KNIGHT, BISHOP -> minors += sq
                else -> return false
            }
        }
        if (minors.size <= 1) return true
        if (minors.all { abs(board[it]) == BISHOP }) {
            val colors = minors.map { (file(it) + rank(it)) % 2 }.toSet()
            return colors.size == 1
        }
        return false
    }

    /**
     * Status of [pos], given the [history] of position keys of the game so far (including [pos])
     * for threefold repetition.
     */
    fun status(pos: ChessPosition, history: List<Long> = emptyList()): Status {
        val legal = legalMoves(pos)
        if (legal.isEmpty()) return if (inCheck(pos)) Status.CHECKMATE else Status.STALEMATE
        if (insufficientMaterial(pos.board)) return Status.INSUFFICIENT
        if (pos.halfmove >= 100) return Status.FIFTY_MOVES
        if (history.isNotEmpty() && history.count { it == history.last() } >= 3) return Status.REPETITION
        return Status.PLAYING
    }

    // ----- Position keys (Zobrist hashing) ---------------------------------------------------

    private val ZOBRIST = Random(20260101).let { r -> LongArray(13 * 64 + 16 + 8 + 1) { r.nextLong() } }

    /** A key identifying the position for repetitions: pieces, side to move, castling rights and a usable en passant file. */
    fun key(pos: ChessPosition): Long {
        var h = 0L
        val b = pos.board
        for (sq in 0 until 64) if (b[sq] != 0) h = h xor ZOBRIST[(b[sq] + 6) * 64 + sq]
        h = h xor ZOBRIST[13 * 64 + pos.castling]
        if (pos.ep >= 0) {
            // Only when a pawn could really take en passant, as the rules count repetitions.
            val pawnRank = rank(pos.ep) - pos.side
            val f = file(pos.ep)
            if ((f > 0 && b[pawnRank * 8 + f - 1] == pos.side * PAWN) || (f < 7 && b[pawnRank * 8 + f + 1] == pos.side * PAWN)) {
                h = h xor ZOBRIST[13 * 64 + 16 + f]
            }
        }
        if (pos.side == BLACK) h = h xor ZOBRIST[13 * 64 + 24]
        return h
    }

    // ----- Notation -----------------------------------------------------------------------

    private const val LETTERS = "?PNBRQK"

    /** Long algebraic form used in save files: e2e4, e7e8q. */
    fun uci(m: Int): String = squareName(from(m)) + squareName(to(m)) + (if (promo(m) != 0) "?pnbrqk"[promo(m)].toString() else "")

    fun fromUci(pos: ChessPosition, text: String): Int? = legalMoves(pos).firstOrNull { uci(it) == text }

    /** Standard algebraic notation (Nf3, exd5, O-O, e8=Q+, Qh4#). */
    fun san(pos: ChessPosition, m: Int, legal: List<Int> = legalMoves(pos)): String {
        val after = play(pos, m)
        val mark = if (!inCheck(after)) "" else if (legalMoves(after).isEmpty()) "#" else "+"
        return sanWithoutCheck(pos, m, legal) + mark
    }

    /** Algebraic notation without the check or mate sign (cheap: it does not look at the position after). */
    private fun sanWithoutCheck(pos: ChessPosition, m: Int, legal: List<Int>): String {
        val from = from(m)
        val to = to(m)
        val type = abs(pos.board[from])
        val sb = StringBuilder()
        if (m and FLAG_CASTLE != 0) {
            sb.append(if (to > from) "O-O" else "O-O-O")
        } else if (type == PAWN) {
            if (isCapture(m)) sb.append('a' + file(from)).append('x')
            sb.append(squareName(to))
            if (promo(m) != 0) sb.append('=').append(LETTERS[promo(m)])
        } else {
            sb.append(LETTERS[type])
            val rivals = legal.filter { it != m && to(it) == to && abs(pos.board[from(it)]) == type }
            if (rivals.isNotEmpty()) {
                when {
                    rivals.none { file(from(it)) == file(from) } -> sb.append('a' + file(from))
                    rivals.none { rank(from(it)) == rank(from) } -> sb.append(rank(from) + 1)
                    else -> sb.append(squareName(from))
                }
            }
            if (isCapture(m)) sb.append('x')
            sb.append(squareName(to))
        }
        return sb.toString()
    }

    /** Finds the legal move written [text] in algebraic notation, or null. Accepts check marks, !/? and 0-0. */
    fun parseSan(pos: ChessPosition, text: String): Int? {
        val clean = text.trim().replace("0-0-0", "O-O-O").replace("0-0", "O-O").trimEnd('+', '#', '!', '?')
        val legal = legalMoves(pos)
        return legal.firstOrNull { sanWithoutCheck(pos, it, legal) == clean }
            ?: legal.firstOrNull { sanWithoutCheck(pos, it, legal).replace("=", "") == clean.replace("=", "") }
    }

    // ----- FEN ----------------------------------------------------------------------------

    fun fromFen(fen: String): ChessPosition {
        val parts = fen.trim().split(Regex("\\s+"))
        val b = IntArray(64)
        var r = 7
        var f = 0
        for (ch in parts[0]) {
            when {
                ch == '/' -> { r--; f = 0 }
                ch.isDigit() -> f += ch - '0'
                else -> {
                    val type = LETTERS.indexOf(ch.uppercaseChar())
                    b[r * 8 + f] = if (ch.isUpperCase()) type else -type
                    f++
                }
            }
        }
        val side = if (parts.getOrNull(1) == "b") BLACK else WHITE
        var rights = 0
        parts.getOrNull(2)?.forEach {
            when (it) {
                'K' -> rights = rights or 1
                'Q' -> rights = rights or 2
                'k' -> rights = rights or 4
                'q' -> rights = rights or 8
            }
        }
        val ep = parts.getOrNull(3)?.takeIf { it != "-" && it.length == 2 }?.let { (it[1] - '1') * 8 + (it[0] - 'a') } ?: -1
        return ChessPosition(
            b, side, rights, ep, parts.getOrNull(4)?.toIntOrNull() ?: 0, parts.getOrNull(5)?.toIntOrNull() ?: 1,
            b.indexOf(KING), b.indexOf(-KING),
        )
    }

    fun toFen(pos: ChessPosition): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0..7) {
                val v = pos.board[r * 8 + f]
                if (v == 0) { empty++; continue }
                if (empty > 0) { sb.append(empty); empty = 0 }
                val c = LETTERS[abs(v)]
                sb.append(if (v > 0) c else c.lowercaseChar())
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(if (pos.side == WHITE) " w " else " b ")
        val rights = buildString {
            if (pos.castling and 1 != 0) append('K')
            if (pos.castling and 2 != 0) append('Q')
            if (pos.castling and 4 != 0) append('k')
            if (pos.castling and 8 != 0) append('q')
        }
        sb.append(rights.ifEmpty { "-" }).append(' ')
        sb.append(if (pos.ep >= 0) squareName(pos.ep) else "-")
        sb.append(' ').append(pos.halfmove).append(' ').append(pos.fullmove)
        return sb.toString()
    }
}

/**
 * A game replayed from the start position: every position and its key, so the screen can show
 * the last move, find repetitions and undo by simply dropping moves.
 */
class ChessGameLine(val moves: List<Int>, val positions: List<ChessPosition>) {
    val current get() = positions.last()
    val keys: List<Long> by lazy { positions.map { Chess.key(it) } }
    fun status() = Chess.status(current, keys)

    companion object {
        /** Replays moves saved as e2e4 / e7e8q, stopping at the first one that is not legal. */
        fun fromUci(list: List<String>, start: ChessPosition = Chess.start()): ChessGameLine {
            val positions = arrayListOf(start)
            val moves = ArrayList<Int>()
            for (text in list) {
                val m = Chess.fromUci(positions.last(), text) ?: break
                moves += m
                positions += Chess.play(positions.last(), m)
            }
            return ChessGameLine(moves, positions)
        }
    }
}
