package io.github.fershibli.megagames.games.xiangqi

import io.github.fershibli.megagames.games.eastern.EndReason
import io.github.fershibli.megagames.games.eastern.GridPosition
import io.github.fershibli.megagames.games.eastern.Mv
import io.github.fershibli.megagames.games.eastern.Zobrist
import kotlin.math.abs

/**
 * Xiangqi (Chinese chess) rules on the 9 × 10 points of the board. The player is red, at the
 * bottom (rows 5-9) and moves first; the computer is black (rows 0-4). The river runs between
 * rows 4 and 5 and the palaces are the 3 × 3 points in the middle of each back side.
 *
 * Draws are simplified: a position seen for the third time, 60 moves each without a capture, or
 * nobody left with a piece that can cross the river. The tournament rules against perpetual
 * check and perpetual chase are not applied (such a repetition is simply a draw).
 */
class Xiangqi private constructor() : GridPosition(9, 10) {
    /** Square of each side's general. */
    val generals = IntArray(2)

    override val repetitionLimit = 2

    override fun make(move: Int) {
        val from = Mv.from(move)
        val to = Mv.to(move)
        val piece = board[from]
        val captured = board[to]
        pushHistory(move, captured)
        hash = hash xor Zobrist.piece(piece, from) xor Zobrist.piece(piece, to) xor Zobrist.side
        if (captured != 0) {
            hash = hash xor Zobrist.piece(captured, to)
            quiet = 0
        } else quiet++
        board[to] = piece
        board[from] = 0
        if (abs(piece) == GENERAL) generals[owner(piece)] = to
        side = 1 - side
    }

    override fun unmake() {
        val move = lastMove
        val captured = popHistory()
        val from = Mv.from(move)
        val to = Mv.to(move)
        val piece = board[to]
        board[from] = piece
        board[to] = captured
        if (abs(piece) == GENERAL) generals[owner(piece)] = from
    }

    private fun inPalace(side: Int, r: Int, c: Int) = c in 3..5 && if (side == 0) r in 7..9 else r in 0..2
    private fun onOwnHalf(side: Int, r: Int) = if (side == 0) r >= 5 else r <= 4

    private fun add(out: IntArray, n: Int, from: Int, to: Int): Int {
        if (isOwn(board[to], side)) return n
        out[n] = Mv.of(from, to)
        return n + 1
    }

    override fun generate(out: IntArray, start: Int): Int {
        var n = start
        val s = side
        val sg = sign(s)
        for (sq in 0 until 90) {
            val p = board[sq] * sg
            if (p <= 0) continue
            val r = sq / 9
            val c = sq % 9
            when (p) {
                GENERAL -> for (d in 0..3) {
                    val nr = r + DR[d]; val nc = c + DC[d]
                    if (inPalace(s, nr, nc)) n = add(out, n, sq, nr * 9 + nc)
                }
                ADVISOR -> for (d in 0..3) {
                    val nr = r + XR[d]; val nc = c + XC[d]
                    if (inPalace(s, nr, nc)) n = add(out, n, sq, nr * 9 + nc)
                }
                ELEPHANT -> for (d in 0..3) {
                    val er = r + XR[d]; val ec = c + XC[d]
                    val nr = r + 2 * XR[d]; val nc = c + 2 * XC[d]
                    if (nr !in 0..9 || nc !in 0..8 || !onOwnHalf(s, nr)) continue
                    if (board[er * 9 + ec] != 0) continue // the elephant's eye is blocked
                    n = add(out, n, sq, nr * 9 + nc)
                }
                HORSE -> for (d in 0..3) {
                    val lr = r + DR[d]; val lc = c + DC[d]
                    if (lr !in 0..9 || lc !in 0..8 || board[lr * 9 + lc] != 0) continue // hobbled
                    for (k in -1..1 step 2) {
                        val nr = r + 2 * DR[d] + (if (DR[d] == 0) k else 0)
                        val nc = c + 2 * DC[d] + (if (DC[d] == 0) k else 0)
                        if (nr in 0..9 && nc in 0..8) n = add(out, n, sq, nr * 9 + nc)
                    }
                }
                CHARIOT -> for (d in 0..3) {
                    var nr = r + DR[d]; var nc = c + DC[d]
                    while (nr in 0..9 && nc in 0..8) {
                        val t = nr * 9 + nc
                        if (board[t] == 0) { out[n++] = Mv.of(sq, t) } else {
                            if (isEnemy(board[t], s)) out[n++] = Mv.of(sq, t)
                            break
                        }
                        nr += DR[d]; nc += DC[d]
                    }
                }
                CANNON -> for (d in 0..3) {
                    var nr = r + DR[d]; var nc = c + DC[d]
                    var jumped = false
                    while (nr in 0..9 && nc in 0..8) {
                        val t = nr * 9 + nc
                        if (!jumped) {
                            if (board[t] == 0) out[n++] = Mv.of(sq, t) else jumped = true
                        } else if (board[t] != 0) {
                            if (isEnemy(board[t], s)) out[n++] = Mv.of(sq, t)
                            break
                        }
                        nr += DR[d]; nc += DC[d]
                    }
                }
                SOLDIER -> {
                    val fr = r - sg
                    if (fr in 0..9) n = add(out, n, sq, fr * 9 + c)
                    if (!onOwnHalf(s, r)) {
                        if (c > 0) n = add(out, n, sq, sq - 1)
                        if (c < 8) n = add(out, n, sq, sq + 1)
                    }
                }
            }
        }
        return n
    }

    /**
     * Whether [owner]'s general on [k] is attacked. The opposing general counts as an attacker
     * when nothing stands between them on the file (the "flying general" rule), so a move that
     * leaves the generals facing each other is illegal.
     */
    fun attacked(k: Int, owner: Int): Boolean {
        val e = -sign(owner)
        val r = k / 9
        val c = k % 9
        for (d in 0..3) {
            var nr = r + DR[d]; var nc = c + DC[d]
            var screen = false
            while (nr in 0..9 && nc in 0..8) {
                val p = board[nr * 9 + nc]
                if (p != 0) {
                    if (!screen) {
                        if (p == e * CHARIOT || p == e * GENERAL) return true
                        screen = true
                    } else {
                        if (p == e * CANNON) return true
                        break
                    }
                }
                nr += DR[d]; nc += DC[d]
            }
        }
        // A horse stands two points away along one line and one across; its leg is next to it.
        for (d in 0..3) for (k2 in -1..1 step 2) {
            val pr = if (DR[d] == 0) k2 else 0
            val pc = if (DC[d] == 0) k2 else 0
            val hr = r - 2 * DR[d] - pr; val hc = c - 2 * DC[d] - pc
            if (hr !in 0..9 || hc !in 0..8 || board[hr * 9 + hc] != e * HORSE) continue
            if (board[(r - DR[d] - pr) * 9 + (c - DC[d] - pc)] == 0) return true
        }
        // Soldiers: one in front (coming forward) or, once across the river, beside.
        val sr = r + e
        if (sr in 0..9 && board[sr * 9 + c] == e * SOLDIER) return true
        if (!onOwnHalf(1 - owner, r)) {
            if (c > 0 && board[k - 1] == e * SOLDIER) return true
            if (c < 8 && board[k + 1] == e * SOLDIER) return true
        }
        return false
    }

    override fun lastMoveLegal(): Boolean {
        val mover = 1 - side
        return !attacked(generals[mover], mover)
    }

    override fun inCheck() = attacked(generals[side], side)

    override fun captureValue(move: Int): Int = VALUE[abs(board[Mv.to(move)])]
    override fun moverValue(move: Int): Int = VALUE[abs(board[Mv.from(move)])]

    override fun ruleDraw(): EndReason? {
        if (quiet >= 120) return EndReason.MOVE_LIMIT
        for (p in board) {
            val t = abs(p)
            if (t == HORSE || t == CHARIOT || t == CANNON || t == SOLDIER) return null
        }
        return EndReason.NO_ATTACKERS
    }

    override fun evaluate(): Int {
        var score = 0
        for (sq in 0 until 90) {
            val p = board[sq]
            if (p == 0) continue
            val s = owner(p)
            val r = sq / 9
            val c = sq % 9
            val adv = if (s == 0) 9 - r else r // 0 = own back row
            val v = when (abs(p)) {
                ADVISOR, ELEPHANT -> 120
                HORSE -> {
                    var legs = 0
                    for (d in 0..3) {
                        val lr = r + DR[d]; val lc = c + DC[d]
                        if (lr in 0..9 && lc in 0..8 && board[lr * 9 + lc] == 0) legs++
                    }
                    400 + legs * 8 + (if (adv in 3..7) 20 else 0) - (if (c == 0 || c == 8) 20 else 0)
                }
                CHARIOT -> {
                    var free = 0
                    for (d in 0..3) {
                        var nr = r + DR[d]; var nc = c + DC[d]
                        while (nr in 0..9 && nc in 0..8 && board[nr * 9 + nc] == 0) { free++; nr += DR[d]; nc += DC[d] }
                    }
                    900 + free * 4
                }
                CANNON -> 450 + (if (c == 4) 15 else 0)
                SOLDIER -> when {
                    adv < 5 -> 100
                    adv == 9 -> 150
                    else -> 180 + (adv - 5) * 10 + (if (c in 3..5) 20 else 0)
                }
                else -> 0
            }
            score += if (s == side) v else -v
        }
        return score
    }

    /** Copy of this position with the same history (for the computer to think on). */
    fun copy(): Xiangqi = fromMoves((0 until plies).map { moveAt(it) })

    companion object {
        const val GENERAL = 1
        const val ADVISOR = 2
        const val ELEPHANT = 3
        const val HORSE = 4
        const val CHARIOT = 5
        const val CANNON = 6
        const val SOLDIER = 7

        private val DR = intArrayOf(-1, 0, 1, 0)
        private val DC = intArrayOf(0, 1, 0, -1)
        private val XR = intArrayOf(-1, -1, 1, 1)
        private val XC = intArrayOf(-1, 1, 1, -1)
        private val VALUE = intArrayOf(0, 10_000, 120, 120, 400, 900, 450, 100)

        const val START = "rheakaehr/9/1c5c1/s1s1s1s1s/9/9/S1S1S1S1S/1C5C1/9/RHEAKAEHR"
        private const val LETTERS = ".kaehrcs"

        /**
         * Position from rows written top to bottom, separated by '/': a letter per piece
         * (k general, a advisor, e elephant, h horse, r chariot, c cannon, s soldier; capitals for
         * the player) and digits for empty points.
         */
        fun parse(rows: String, sideToMove: Int = 0): Xiangqi {
            val x = Xiangqi()
            rows.split('/').forEachIndexed { r, line ->
                var c = 0
                for (ch in line) {
                    if (ch.isDigit()) { c += ch - '0'; continue }
                    val type = LETTERS.indexOf(ch.lowercaseChar())
                    require(type > 0) { "bad piece $ch" }
                    x.board[r * 9 + c] = if (ch.isUpperCase()) type else -type
                    c++
                }
            }
            x.side = sideToMove
            x.rehash()
            return x
        }

        fun start() = parse(START)

        /** The start position after [moves]; stops at the first move that is not legal. */
        fun fromMoves(moves: List<Int>): Xiangqi {
            val x = start()
            for (m in moves) {
                if (m !in x.legalMoves()) break
                x.make(m)
            }
            return x
        }
    }

    private fun rehash() {
        var h = if (side == 1) Zobrist.side else 0L
        for (sq in 0 until 90) {
            val p = board[sq]
            if (p != 0) h = h xor Zobrist.piece(p, sq)
            if (p == GENERAL) generals[0] = sq
            if (p == -GENERAL) generals[1] = sq
        }
        hash = h
    }
}
