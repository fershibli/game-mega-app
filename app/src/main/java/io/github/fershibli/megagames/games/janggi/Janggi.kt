package io.github.fershibli.megagames.games.janggi

import io.github.fershibli.megagames.games.eastern.EndReason
import io.github.fershibli.megagames.games.eastern.GridPosition
import io.github.fershibli.megagames.games.eastern.Mv
import io.github.fershibli.megagames.games.eastern.Zobrist
import kotlin.math.abs

/**
 * Janggi (Korean chess) rules on the 9 × 10 points of the board, without a river. The player is
 * Cho (blue, at the bottom, moving first); the computer is Han (red). Inside both palaces the
 * diagonal lines can be followed by the general, the guards, the chariots, the cannons and (in
 * the opponent's palace) the soldiers.
 *
 * Simplifications, as in casual play: when the two generals face each other on an open file
 * (bikjang) the game is a draw; when both players pass one after the other it is a draw; a
 * position seen for the third time, or 60 moves each without a capture, is a draw. Every game
 * uses the same setup (horse, elephant, elephant, horse from the left).
 */
class Janggi private constructor() : GridPosition(9, 10) {
    val generals = IntArray(2)

    override val repetitionLimit = 2

    override fun passMove() = Mv.PASS

    override fun make(move: Int) {
        if (move == Mv.PASS) {
            pushHistory(move, 0)
            hash = hash xor Zobrist.side
            quiet++
            side = 1 - side
            return
        }
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
        if (move == Mv.PASS) return
        val from = Mv.from(move)
        val to = Mv.to(move)
        val piece = board[to]
        board[from] = piece
        board[to] = captured
        if (abs(piece) == GENERAL) generals[owner(piece)] = from
    }

    private fun inPalace(side: Int, r: Int, c: Int) = c in 3..5 && if (side == 0) r in 7..9 else r in 0..2

    private fun add(out: IntArray, n: Int, from: Int, to: Int): Int {
        if (isOwn(board[to], side)) return n
        out[n] = Mv.of(from, to)
        return n + 1
    }

    /** Adds a cannon landing on [to]: an empty point, or an enemy piece that is not a cannon. */
    private fun addCannon(out: IntArray, n: Int, from: Int, to: Int): Int {
        val t = board[to]
        if (t != 0 && (!isEnemy(t, side) || abs(t) == CANNON)) return n
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
                GENERAL, GUARD -> {
                    for (d in 0..3) {
                        val nr = r + DR[d]; val nc = c + DC[d]
                        if (inPalace(s, nr, nc)) n = add(out, n, sq, nr * 9 + nc)
                    }
                    for (d in 0..3) if (diagonal(sq, d)) n = add(out, n, sq, sq + XD[d])
                }
                HORSE -> for (d in 0..3) {
                    val lr = r + DR[d]; val lc = c + DC[d]
                    if (lr !in 0..9 || lc !in 0..8 || board[lr * 9 + lc] != 0) continue
                    for (k in -1..1 step 2) {
                        val nr = r + 2 * DR[d] + (if (DR[d] == 0) k else 0)
                        val nc = c + 2 * DC[d] + (if (DC[d] == 0) k else 0)
                        if (nr in 0..9 && nc in 0..8) n = add(out, n, sq, nr * 9 + nc)
                    }
                }
                ELEPHANT -> for (d in 0..3) {
                    val l1r = r + DR[d]; val l1c = c + DC[d]
                    if (l1r !in 0..9 || l1c !in 0..8 || board[l1r * 9 + l1c] != 0) continue
                    for (k in -1..1 step 2) {
                        val pr = if (DR[d] == 0) k else 0
                        val pc = if (DC[d] == 0) k else 0
                        val l2r = r + 2 * DR[d] + pr; val l2c = c + 2 * DC[d] + pc
                        val nr = r + 3 * DR[d] + 2 * pr; val nc = c + 3 * DC[d] + 2 * pc
                        if (nr !in 0..9 || nc !in 0..8 || board[l2r * 9 + l2c] != 0) continue
                        n = add(out, n, sq, nr * 9 + nc)
                    }
                }
                CHARIOT -> {
                    for (d in 0..3) {
                        var nr = r + DR[d]; var nc = c + DC[d]
                        while (nr in 0..9 && nc in 0..8) {
                            val t = nr * 9 + nc
                            if (board[t] == 0) out[n++] = Mv.of(sq, t) else {
                                if (isEnemy(board[t], s)) out[n++] = Mv.of(sq, t)
                                break
                            }
                            nr += DR[d]; nc += DC[d]
                        }
                    }
                    for (d in 0..3) {
                        var cur = sq
                        while (diagonal(cur, d)) {
                            cur += XD[d]
                            if (board[cur] == 0) out[n++] = Mv.of(sq, cur) else {
                                if (isEnemy(board[cur], s)) out[n++] = Mv.of(sq, cur)
                                break
                            }
                        }
                    }
                }
                CANNON -> {
                    for (d in 0..3) {
                        var nr = r + DR[d]; var nc = c + DC[d]
                        var jumped = false
                        while (nr in 0..9 && nc in 0..8) {
                            val t = nr * 9 + nc
                            if (!jumped) {
                                if (board[t] != 0) {
                                    if (abs(board[t]) == CANNON) break // a cannon never jumps a cannon
                                    jumped = true
                                }
                            } else {
                                n = addCannon(out, n, sq, t)
                                if (board[t] != 0) break
                            }
                            nr += DR[d]; nc += DC[d]
                        }
                    }
                    for (d in 0..3) {
                        if (!diagonal(sq, d)) continue
                        val mid = sq + XD[d]
                        if (board[mid] == 0 || abs(board[mid]) == CANNON || !diagonal(mid, d)) continue
                        n = addCannon(out, n, sq, mid + XD[d])
                    }
                }
                SOLDIER -> {
                    val fr = r - sg
                    if (fr in 0..9) n = add(out, n, sq, fr * 9 + c)
                    if (c > 0) n = add(out, n, sq, sq - 1)
                    if (c < 8) n = add(out, n, sq, sq + 1)
                    for (d in 0..3) if (XR[d] == -sg && diagonal(sq, d)) n = add(out, n, sq, sq + XD[d])
                }
            }
        }
        return n
    }

    /** Whether [owner]'s general on [k] is attacked. */
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
                        if (p == e * CHARIOT) return true
                        if (abs(p) == CANNON) break
                        screen = true
                    } else {
                        if (p == e * CANNON) return true
                        break
                    }
                }
                nr += DR[d]; nc += DC[d]
            }
        }
        for (d in 0..3) {
            if (!diagonal(k, d)) continue
            var cur = k
            while (diagonal(cur, d)) {
                cur += XD[d]
                if (board[cur] != 0) {
                    if (board[cur] == e * CHARIOT) return true
                    break
                }
            }
            val mid = k + XD[d]
            if (board[mid] != 0 && abs(board[mid]) != CANNON && diagonal(mid, d) && board[mid + XD[d]] == e * CANNON) return true
        }
        for (d in 0..3) for (k2 in -1..1 step 2) {
            val pr = if (DR[d] == 0) k2 else 0
            val pc = if (DC[d] == 0) k2 else 0
            // Horse: two along one line and one across, its leg next to it.
            val hr = r - 2 * DR[d] - pr; val hc = c - 2 * DC[d] - pc
            if (hr in 0..9 && hc in 0..8 && board[hr * 9 + hc] == e * HORSE && board[(r - DR[d] - pr) * 9 + (c - DC[d] - pc)] == 0) return true
            // Elephant: three along one line and two across, two points of its path in between.
            val er = r - 3 * DR[d] - 2 * pr; val ec = c - 3 * DC[d] - 2 * pc
            if (er in 0..9 && ec in 0..8 && board[er * 9 + ec] == e * ELEPHANT &&
                board[(r - 2 * DR[d] - 2 * pr) * 9 + (c - 2 * DC[d] - 2 * pc)] == 0 &&
                board[(r - DR[d] - pr) * 9 + (c - DC[d] - pc)] == 0
            ) return true
        }
        val sr = r + e
        if (sr in 0..9) {
            if (board[sr * 9 + c] == e * SOLDIER) return true
            for (dc in -1..1 step 2) {
                val scol = c - dc
                if (scol !in 0..8) continue
                val s = sr * 9 + scol
                if (board[s] == e * SOLDIER) {
                    val d = diagIndex(-e, dc)
                    if (diagonal(s, d) && s + XD[d] == k) return true
                }
            }
        }
        if (c > 0 && board[k - 1] == e * SOLDIER) return true
        if (c < 8 && board[k + 1] == e * SOLDIER) return true
        return false
    }

    /** Whether the two generals face each other with nothing between them (bikjang). */
    fun generalsFacing(): Boolean {
        val a = generals[0]
        val b = generals[1]
        if (a % 9 != b % 9) return false
        var sq = b + 9
        while (sq < a) {
            if (board[sq] != 0) return false
            sq += 9
        }
        return true
    }

    override fun lastMoveLegal(): Boolean {
        val mover = 1 - side
        return !attacked(generals[mover], mover)
    }

    override fun inCheck() = attacked(generals[side], side)

    override fun captureValue(move: Int): Int = if (move == Mv.PASS) 0 else VALUE[abs(board[Mv.to(move)])]
    override fun moverValue(move: Int): Int = if (move == Mv.PASS) 0 else VALUE[abs(board[Mv.from(move)])]

    override fun ruleDraw(): EndReason? {
        if (generalsFacing()) return EndReason.GENERALS_FACING
        if (plies >= 2 && lastMove == Mv.PASS && moveAt(plies - 2) == Mv.PASS) return EndReason.BOTH_PASSED
        if (quiet >= 120) return EndReason.MOVE_LIMIT
        for (p in board) {
            val t = abs(p)
            if (t != 0 && t != GENERAL && t != GUARD) return null
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
            val adv = if (s == 0) 9 - r else r
            val v = when (abs(p)) {
                GUARD -> 300
                ELEPHANT -> 300 + (if (adv in 2..6) 15 else 0)
                HORSE -> {
                    var legs = 0
                    for (d in 0..3) {
                        val lr = r + DR[d]; val lc = c + DC[d]
                        if (lr in 0..9 && lc in 0..8 && board[lr * 9 + lc] == 0) legs++
                    }
                    500 + legs * 8 + (if (c in 2..6) 10 else 0)
                }
                CHARIOT -> {
                    var free = 0
                    for (d in 0..3) {
                        var nr = r + DR[d]; var nc = c + DC[d]
                        while (nr in 0..9 && nc in 0..8 && board[nr * 9 + nc] == 0) { free++; nr += DR[d]; nc += DC[d] }
                    }
                    1300 + free * 4
                }
                CANNON -> 700
                SOLDIER -> 200 + (if (adv >= 5) (adv - 4) * 15 else 0) + (if (adv >= 7 && c in 3..5) 20 else 0)
                else -> 0
            }
            score += if (s == side) v else -v
        }
        return score
    }

    fun copy(): Janggi = fromMoves((0 until plies).map { moveAt(it) })

    companion object {
        const val GENERAL = 1
        const val GUARD = 2
        const val ELEPHANT = 3
        const val HORSE = 4
        const val CHARIOT = 5
        const val CANNON = 6
        const val SOLDIER = 7

        private val DR = intArrayOf(-1, 0, 1, 0)
        private val DC = intArrayOf(0, 1, 0, -1)
        private val XR = intArrayOf(-1, -1, 1, 1)
        private val XC = intArrayOf(-1, 1, 1, -1)
        private val XD = IntArray(4) { XR[it] * 9 + XC[it] }
        private val VALUE = intArrayOf(0, 10_000, 300, 300, 500, 1300, 700, 200)

        private fun diagIndex(dr: Int, dc: Int) = (0..3).first { XR[it] == dr && XC[it] == dc }

        private fun diagonalPoint(r: Int, c: Int) =
            (c == 4 && (r == 1 || r == 8)) || ((c == 3 || c == 5) && (r == 0 || r == 2 || r == 7 || r == 9))

        /** For each point and diagonal direction: whether a palace line leads one step that way. */
        private val DIAGONAL = BooleanArray(90 * 4) { i ->
            val sq = i / 4
            val d = i % 4
            val r = sq / 9
            val c = sq % 9
            val nr = r + XR[d]
            val nc = c + XC[d]
            nr in 0..9 && nc in 0..8 && diagonalPoint(r, c) && diagonalPoint(nr, nc) && (r <= 2) == (nr <= 2)
        }

        private fun diagonal(sq: Int, d: Int) = DIAGONAL[sq * 4 + d]

        const val START = "rhea1aehr/4k4/1c5c1/s1s1s1s1s/9/9/S1S1S1S1S/1C5C1/4K4/RHEA1AEHR"
        private const val LETTERS = ".kaehrcs"

        /** Same notation as xiangqi: k general, a guard, e elephant, h horse, r chariot, c cannon, s soldier. */
        fun parse(rows: String, sideToMove: Int = 0): Janggi {
            val x = Janggi()
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

        /** Whether [move] can be played now: a legal move, or a pass when not in check. */
        fun isPlayable(position: Janggi, move: Int): Boolean =
            if (move == Mv.PASS) !position.inCheck() else move in position.legalMoves()

        fun fromMoves(moves: List<Int>): Janggi {
            val x = start()
            for (m in moves) {
                if (x.drawReason() != null || !isPlayable(x, m)) break
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
