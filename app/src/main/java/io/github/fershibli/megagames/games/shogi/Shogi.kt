package io.github.fershibli.megagames.games.shogi

import io.github.fershibli.megagames.games.eastern.EndReason
import io.github.fershibli.megagames.games.eastern.GridPosition
import io.github.fershibli.megagames.games.eastern.Mv
import io.github.fershibli.megagames.games.eastern.Zobrist
import kotlin.math.abs
import kotlin.math.max

/**
 * Shogi (Japanese chess) rules, on the standard 9 × 9 board or on the 5 × 5 board of minishogi.
 * The player moves first from the bottom (sente); the computer plays from the top (gote).
 *
 * Captured pieces go to the captor's hand and can be dropped back on an empty square instead of
 * moving. Promotion happens when a piece moves into, inside or out of the promotion zone (the
 * last three rows; the last row in minishogi) and is forced when the piece could not move again
 * otherwise. Drops follow the usual restrictions: no second unpromoted pawn on a file, no pawn
 * drop that gives checkmate at once, and no piece dropped where it could never move.
 *
 * Simplifications: the fourth repetition of a position is a draw (the rule that makes perpetual
 * check lose is not applied), and a game reaching 400 moves is a draw.
 */
class Shogi private constructor(val mini: Boolean) : GridPosition(if (mini) 5 else 9, if (mini) 5 else 9) {
    /** Pieces in hand: index `side * 8 + type` for types [PAWN]..[ROOK]. */
    val hands = IntArray(16)
    val kings = IntArray(2)
    private val zone = if (mini) 1 else 3
    private val n = width

    override val repetitionLimit = 3

    fun hand(side: Int, type: Int) = hands[side * 8 + type]

    /** Rows still ahead of a piece of [side] standing on row [r] (0 = on the last row). */
    private fun ahead(side: Int, r: Int) = if (side == 0) r else n - 1 - r
    fun inZone(side: Int, r: Int) = ahead(side, r) < zone

    private fun setHand(side: Int, type: Int, count: Int) {
        val i = side * 8 + type
        hash = hash xor Zobrist.hand(side, type, hands[i]) xor Zobrist.hand(side, type, count)
        hands[i] = count
    }

    override fun make(move: Int) {
        val sg = sign(side)
        val to = Mv.to(move)
        if (Mv.isDrop(move)) {
            val t = Mv.dropType(move)
            pushHistory(move, 0)
            setHand(side, t, hand(side, t) - 1)
            board[to] = sg * t
            hash = hash xor Zobrist.piece(sg * t, to) xor Zobrist.side
            quiet++
            side = 1 - side
            return
        }
        val from = Mv.from(move)
        val piece = board[from]
        val captured = board[to]
        pushHistory(move, captured)
        val moved = if (Mv.promotes(move)) sg * PROMOTED[abs(piece)] else piece
        if (captured != 0) {
            val bt = BASE[abs(captured)]
            setHand(side, bt, hand(side, bt) + 1)
            hash = hash xor Zobrist.piece(captured, to)
            quiet = 0
        } else quiet++
        hash = hash xor Zobrist.piece(piece, from) xor Zobrist.piece(moved, to) xor Zobrist.side
        board[to] = moved
        board[from] = 0
        if (abs(piece) == KING) kings[side] = to
        side = 1 - side
    }

    override fun unmake() {
        val move = lastMove
        val captured = popHistory()
        val to = Mv.to(move)
        // popHistory restored the hash; the hands are set back directly.
        if (Mv.isDrop(move)) {
            val t = Mv.dropType(move)
            hands[side * 8 + t]++
            board[to] = 0
            return
        }
        val from = Mv.from(move)
        val moved = board[to]
        val piece = if (Mv.promotes(move)) sign(side) * BASE[abs(moved)] else moved
        board[from] = piece
        board[to] = captured
        if (captured != 0) hands[side * 8 + BASE[abs(captured)]]--
        if (abs(piece) == KING) kings[side] = from
    }

    private fun addMove(out: IntArray, k: Int, from: Int, to: Int, type: Int): Int {
        var c = k
        val s = side
        if (CAN_PROMOTE[type] && (inZone(s, from / n) || inZone(s, to / n))) {
            val a = ahead(s, to / n)
            val forced = ((type == PAWN || type == LANCE) && a == 0) || (type == KNIGHT && a <= 1)
            out[c++] = Mv.of(from, to, true)
            if (!forced) out[c++] = Mv.of(from, to)
        } else out[c++] = Mv.of(from, to)
        return c
    }

    override fun generate(out: IntArray, start: Int): Int {
        var k = start
        val s = side
        val sg = sign(s)
        for (sq in 0 until n * n) {
            val type = board[sq] * sg
            if (type <= 0) continue
            val r = sq / n
            val c = sq % n
            val steps = STEPS[type]
            val slides = SLIDES[type]
            for (i in 0..7) {
                val bit = 1 shl i
                if (steps and bit == 0 && slides and bit == 0) continue
                val d = if (s == 0) i else FLIP[i]
                var nr = r + DY[d]; var nc = c + DX[d]
                while (nr in 0 until n && nc in 0 until n) {
                    val t = nr * n + nc
                    val target = board[t]
                    if (isOwn(target, s)) break
                    k = addMove(out, k, sq, t, type)
                    if (target != 0 || slides and bit == 0) break
                    nr += DY[d]; nc += DX[d]
                }
            }
            if (type == KNIGHT) {
                val nr = r - 2 * sg
                for (dc in -1..1 step 2) {
                    val nc = c + dc
                    if (nr in 0 until n && nc in 0 until n && !isOwn(board[nr * n + nc], s)) k = addMove(out, k, sq, nr * n + nc, type)
                }
            }
        }
        // Drops.
        var pawnFiles = 0
        for (sq in 0 until n * n) if (board[sq] == sg * PAWN) pawnFiles = pawnFiles or (1 shl (sq % n))
        for (t in PAWN..ROOK) {
            if (hands[s * 8 + t] == 0) continue
            for (sq in 0 until n * n) {
                if (board[sq] != 0) continue
                val a = ahead(s, sq / n)
                if ((t == PAWN || t == LANCE) && a == 0) continue
                if (t == KNIGHT && a <= 1) continue
                if (t == PAWN && pawnFiles and (1 shl (sq % n)) != 0) continue
                out[k++] = Mv.drop(t, sq)
            }
        }
        return k
    }

    /** Whether [owner]'s king on [kSq] is attacked. */
    fun attacked(kSq: Int, owner: Int): Boolean {
        val e = 1 - owner
        val es = sign(e)
        val r = kSq / n
        val c = kSq % n
        for (j in 0..7) {
            var nr = r + DY[j]; var nc = c + DX[j]
            var dist = 1
            while (nr in 0 until n && nc in 0 until n) {
                val p = board[nr * n + nc]
                if (p != 0) {
                    if (isEnemy(p, owner)) {
                        val back = (j + 4) % 8 // the enemy piece moves this way to reach the king
                        val frame = if (e == 0) back else FLIP[back]
                        val type = p * es
                        if (SLIDES[type] and (1 shl frame) != 0) return true
                        if (dist == 1 && STEPS[type] and (1 shl frame) != 0) return true
                    }
                    break
                }
                nr += DY[j]; nc += DX[j]; dist++
            }
        }
        val kr = r + 2 * es
        if (kr in 0 until n) {
            if (c > 0 && board[kr * n + c - 1] == es * KNIGHT) return true
            if (c < n - 1 && board[kr * n + c + 1] == es * KNIGHT) return true
        }
        return false
    }

    override fun lastMoveLegal(): Boolean {
        val mover = 1 - side
        if (attacked(kings[mover], mover)) return false
        val m = lastMove
        // A pawn drop must not checkmate at once (uchifuzume).
        if (Mv.isDrop(m) && Mv.dropType(m) == PAWN && attacked(kings[side], side) && !hasEvasion()) return false
        return true
    }

    /** Whether the side to move can get out of check (only the king's safety is looked at). */
    private fun hasEvasion(): Boolean {
        val buf = IntArray(MAX_MOVES)
        val count = generate(buf, 0)
        for (i in 0 until count) {
            make(buf[i])
            val mover = 1 - side
            val ok = !attacked(kings[mover], mover)
            unmake()
            if (ok) return true
        }
        return false
    }

    override fun inCheck() = attacked(kings[side], side)

    override fun captureValue(move: Int): Int = if (Mv.isDrop(move)) 0 else VALUE[abs(board[Mv.to(move)])]
    override fun moverValue(move: Int): Int = if (Mv.isDrop(move)) VALUE[Mv.dropType(move)] else VALUE[abs(board[Mv.from(move)])]

    override fun ruleDraw(): EndReason? = if (plies >= 400) EndReason.MOVE_LIMIT else null

    override fun evaluate(): Int {
        var score = 0
        val cells = n * n
        for (sq in 0 until cells) {
            val p = board[sq]
            if (p == 0) continue
            val s = owner(p)
            val t = abs(p)
            val r = sq / n
            val c = sq % n
            var v = VALUE[t]
            when (t) {
                BISHOP, ROOK, HORSE, DRAGON -> {
                    var free = 0
                    for (i in 0..7) {
                        if (SLIDES[t] and (1 shl i) == 0) continue
                        var nr = r + DY[i]; var nc = c + DX[i]
                        while (nr in 0 until n && nc in 0 until n && board[nr * n + nc] == 0) { free++; nr += DY[i]; nc += DX[i] }
                    }
                    v += free * 3
                }
                PAWN -> v += (n - 1 - ahead(s, r)) * 3
                KING -> if (!mini) v -= max(0, n - 3 - ahead(s, r)) * 12 // the king is safer at home
                else -> if (t == SILVER || STEPS[t] == STEPS[GOLD]) {
                    if (near(sq, kings[s])) v += 20
                    if (near(sq, kings[1 - s])) v += 15
                }
            }
            score += if (s == side) v else -v
        }
        for (t in PAWN..ROOK) {
            val v = VALUE[t] * 115 / 100
            score += (hands[side * 8 + t] - hands[(1 - side) * 8 + t]) * v
        }
        return score
    }

    private fun near(a: Int, b: Int) = abs(a / n - b / n) <= 2 && abs(a % n - b % n) <= 2

    fun copy(): Shogi = fromMoves(mini, (0 until plies).map { moveAt(it) })

    companion object {
        const val PAWN = 1
        const val LANCE = 2
        const val KNIGHT = 3
        const val SILVER = 4
        const val GOLD = 5
        const val BISHOP = 6
        const val ROOK = 7
        const val KING = 8
        const val TOKIN = 9
        const val PROMOTED_LANCE = 10
        const val PROMOTED_KNIGHT = 11
        const val PROMOTED_SILVER = 12
        const val HORSE = 13
        const val DRAGON = 14

        // Directions seen by the player: 0 forward (up), then clockwise.
        val DX = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        val DY = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
        /** The same direction for the computer, whose forward is down. */
        val FLIP = intArrayOf(4, 3, 2, 1, 0, 7, 6, 5)

        private fun bits(vararg d: Int) = d.fold(0) { acc, i -> acc or (1 shl i) }
        private val GOLD_STEPS = bits(0, 1, 2, 4, 6, 7)
        /** One-step directions of each piece type, as bits of the direction indexes. */
        val STEPS = intArrayOf(
            0, bits(0), 0, 0, bits(0, 1, 3, 5, 7), GOLD_STEPS, 0, 0, bits(0, 1, 2, 3, 4, 5, 6, 7),
            GOLD_STEPS, GOLD_STEPS, GOLD_STEPS, GOLD_STEPS, bits(0, 2, 4, 6), bits(1, 3, 5, 7),
        )
        /** Directions in which each piece type slides any distance. */
        val SLIDES = intArrayOf(0, 0, bits(0), 0, 0, 0, bits(1, 3, 5, 7), bits(0, 2, 4, 6), 0, 0, 0, 0, 0, bits(1, 3, 5, 7), bits(0, 2, 4, 6))
        val PROMOTED = intArrayOf(0, TOKIN, PROMOTED_LANCE, PROMOTED_KNIGHT, PROMOTED_SILVER, GOLD, HORSE, DRAGON, KING, 9, 10, 11, 12, 13, 14)
        val BASE = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, PAWN, LANCE, KNIGHT, SILVER, BISHOP, ROOK)
        val CAN_PROMOTE = booleanArrayOf(false, true, true, true, true, false, true, true, false, false, false, false, false, false, false)
        private val VALUE = intArrayOf(0, 90, 300, 350, 500, 550, 800, 950, 0, 520, 500, 500, 520, 1050, 1250)

        const val START = "lnsgkgsnl/1r5b1/ppppppppp/9/9/9/PPPPPPPPP/1B5R1/LNSGKGSNL"
        const val MINI_START = "rbsgk/4p/5/P4/KGSBR"
        private const val LETTERS = ".plnsgbrk"

        /**
         * Position from rows written top to bottom ('/' between rows): p pawn, l lance, n knight,
         * s silver, g gold, b bishop, r rook, k king, '+' before a promoted piece, capitals for the
         * player, digits for empty squares. [hand] lists pieces in hand the same way ("BPp").
         */
        fun parse(rows: String, sideToMove: Int = 0, hand: String = ""): Shogi {
            val lines = rows.split('/')
            val x = Shogi(lines.size == 5)
            lines.forEachIndexed { r, line ->
                var c = 0
                var promoted = false
                for (ch in line) {
                    when {
                        ch == '+' -> promoted = true
                        ch.isDigit() -> c += ch - '0'
                        else -> {
                            var type = LETTERS.indexOf(ch.lowercaseChar())
                            require(type > 0) { "bad piece $ch" }
                            if (promoted) type = PROMOTED[type]
                            x.board[r * x.n + c] = if (ch.isUpperCase()) type else -type
                            promoted = false
                            c++
                        }
                    }
                }
            }
            for (ch in hand) {
                val type = LETTERS.indexOf(ch.lowercaseChar())
                x.hands[(if (ch.isUpperCase()) 0 else 1) * 8 + type]++
            }
            x.side = sideToMove
            x.rehash()
            return x
        }

        fun start(mini: Boolean) = parse(if (mini) MINI_START else START)

        fun fromMoves(mini: Boolean, moves: List<Int>): Shogi {
            val x = start(mini)
            for (m in moves) {
                if (m !in x.legalMoves()) break
                x.make(m)
            }
            return x
        }
    }

    private fun rehash() {
        var h = if (side == 1) Zobrist.side else 0L
        for (sq in 0 until n * n) {
            val p = board[sq]
            if (p != 0) h = h xor Zobrist.piece(p, sq)
            if (p == KING) kings[0] = sq
            if (p == -KING) kings[1] = sq
        }
        for (s in 0..1) for (t in PAWN..ROOK) h = h xor Zobrist.hand(s, t, hands[s * 8 + t])
        hash = h
    }
}
