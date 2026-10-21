package io.github.fershibli.megagames.games.go

import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameVariant

/**
 * A Go board of n×n points with fast captures, used both by the rules and by the computer's
 * playouts.
 *
 * Outside this file points are numbered `row * n + column` (save files, the screen). Inside, the
 * board has a one-point border of [Go.WALL] so neighbours never need bounds checks: a point is
 * `(row + 1) * w + column + 1` with `w = n + 2` (see [point] and [index]).
 *
 * Stones of a string are kept in a circular list ([next]) with one representative ([head]) that
 * holds the string's size and its pseudo-liberties: the number of (stone, empty neighbour) pairs.
 * Pseudo-liberties are zero exactly when the string has no liberty, which is all captures and
 * suicide need, and they are cheap to update.
 */
class GoBoard(val n: Int) {
    val w = n + 2
    private val color = IntArray(w * w)
    private val head = IntArray(w * w)
    private val next = IntArray(w * w)
    private val libs = IntArray(w * w)
    private val stones = IntArray(w * w)
    private val emptyList = IntArray(n * n)
    private val emptyAt = IntArray(w * w)

    /** Number of empty points; the first [empties] entries of the empty list. */
    var empties = 0
        private set

    /** The point that cannot be played right now because of the ko rule, or [Go.NONE]. */
    var ko = Go.NONE
        private set

    /** Stones captured by each color ([Go.BLACK], [Go.WHITE]). */
    val captured = IntArray(3)

    private val dirs = intArrayOf(-1, 1, -w, w)
    private val diags = intArrayOf(-w - 1, -w + 1, w - 1, w + 1)
    private val mark = IntArray(w * w)
    private var stamp = 0

    init {
        for (p in 0 until w * w) {
            val r = p / w
            val c = p % w
            if (r == 0 || c == 0 || r == w - 1 || c == w - 1) color[p] = Go.WALL
            else {
                emptyAt[p] = empties
                emptyList[empties++] = p
            }
        }
    }

    fun point(index: Int) = (index / n + 1) * w + index % n + 1
    fun index(point: Int) = (point / w - 1) * n + point % w - 1

    operator fun get(point: Int) = color[point]

    /** Color at `row * n + column`. */
    fun at(index: Int) = color[point(index)]

    fun emptyPoint(k: Int) = emptyList[k]

    fun copy(): GoBoard = GoBoard(n).also { it.copyFrom(this) }

    fun copyFrom(o: GoBoard) {
        System.arraycopy(o.color, 0, color, 0, color.size)
        System.arraycopy(o.head, 0, head, 0, head.size)
        System.arraycopy(o.next, 0, next, 0, next.size)
        System.arraycopy(o.libs, 0, libs, 0, libs.size)
        System.arraycopy(o.stones, 0, stones, 0, stones.size)
        System.arraycopy(o.emptyList, 0, emptyList, 0, emptyList.size)
        System.arraycopy(o.emptyAt, 0, emptyAt, 0, emptyAt.size)
        System.arraycopy(o.captured, 0, captured, 0, captured.size)
        empties = o.empties
        ko = o.ko
    }

    /** Whether [c] may play at [p]: an empty point, not the ko point, and not suicide. */
    fun isLegal(p: Int, c: Int): Boolean = whyIllegal(p, c) == Go.OK

    /** [Go.OK], or why the move is not allowed ([Go.OCCUPIED], [Go.KO], [Go.SUICIDE]). */
    fun whyIllegal(p: Int, c: Int): Int {
        if (color[p] != Go.EMPTY) return Go.OCCUPIED
        if (p == ko) return Go.KO
        for (d in dirs) if (color[p + d] == Go.EMPTY) return Go.OK
        for (d in dirs) {
            val q = p + d
            val x = color[q]
            if (x != Go.BLACK && x != Go.WHITE) continue
            val h = head[q]
            // Pseudo-liberties of that string that are not the point being played.
            var shared = 0
            for (e in dirs) if (color[p + e] == x && head[p + e] == h) shared++
            if (x == c && libs[h] > shared) return Go.OK
            if (x != c && libs[h] == shared) return Go.OK // captures it
        }
        return Go.SUICIDE
    }

    /** Plays a legal move of [c] at [p] and returns the number of stones captured. */
    fun play(p: Int, c: Int): Int {
        ko = Go.NONE
        removeEmpty(p)
        color[p] = c
        head[p] = p
        next[p] = p
        stones[p] = 1
        libs[p] = 0
        for (d in dirs) {
            val q = p + d
            when (color[q]) {
                Go.EMPTY -> libs[p]++
                Go.BLACK, Go.WHITE -> libs[head[q]]--
            }
        }
        for (d in dirs) {
            val q = p + d
            if (color[q] == c && head[q] != head[p]) merge(head[p], head[q])
        }
        val other = Go.opponent(c)
        var taken = 0
        var lastTaken = Go.NONE
        for (d in dirs) {
            val q = p + d
            if (color[q] == other && libs[head[q]] == 0) {
                taken += removeString(head[q])
                lastTaken = q
            }
        }
        // A single stone that took a single stone and now has one liberty: the ko.
        val h = head[p]
        if (taken == 1 && stones[h] == 1 && libs[h] == 1) ko = lastTaken
        captured[c] += taken
        return taken
    }

    fun pass() {
        ko = Go.NONE
    }

    private fun merge(a0: Int, b0: Int) {
        var a = a0
        var b = b0
        if (stones[a] < stones[b]) { val t = a; a = b; b = t }
        var s = b
        do {
            head[s] = a
            s = next[s]
        } while (s != b)
        val t = next[a]
        next[a] = next[b]
        next[b] = t
        libs[a] += libs[b]
        stones[a] += stones[b]
    }

    private fun removeString(h: Int): Int {
        var s = h
        var count = 0
        do {
            color[s] = Go.EMPTY
            emptyAt[s] = empties
            emptyList[empties++] = s
            count++
            s = next[s]
        } while (s != h)
        s = h
        do {
            for (d in dirs) {
                val q = s + d
                val x = color[q]
                if (x == Go.BLACK || x == Go.WHITE) libs[head[q]]++
            }
            s = next[s]
        } while (s != h)
        return count
    }

    private fun removeEmpty(p: Int) {
        val i = emptyAt[p]
        val last = emptyList[--empties]
        emptyList[i] = last
        emptyAt[last] = i
    }

    /**
     * An eye of [c]: every neighbour is [c] (or the edge) and the diagonals do not make it false
     * (at most one enemy diagonal in the middle of the board, none on the edge).
     */
    fun isEye(p: Int, c: Int): Boolean {
        if (color[p] != Go.EMPTY) return false
        for (d in dirs) {
            val x = color[p + d]
            if (x != c && x != Go.WALL) return false
        }
        var enemy = 0
        var edge = false
        for (d in diags) {
            val x = color[p + d]
            if (x == Go.WALL) edge = true else if (x == Go.opponent(c)) enemy++
        }
        return if (edge) enemy == 0 else enemy <= 1
    }

    /** All the stones of the string at [p]. */
    fun stringAt(p: Int): IntArray {
        val h = head[p]
        val out = IntArray(stones[h])
        var s = h
        var i = 0
        do {
            out[i++] = s
            s = next[s]
        } while (s != h)
        return out
    }

    fun stringHead(p: Int) = head[p]

    /** Exact number of liberties of the string at [p]. */
    fun liberties(p: Int): Int {
        stamp++
        val h = head[p]
        var s = h
        var count = 0
        do {
            for (d in dirs) {
                val q = s + d
                if (color[q] == Go.EMPTY && mark[q] != stamp) {
                    mark[q] = stamp
                    count++
                }
            }
            s = next[s]
        } while (s != h)
        return count
    }

    /** One liberty of the string at [p], or [Go.NONE]. */
    fun anyLiberty(p: Int): Int {
        val h = head[p]
        var s = h
        do {
            for (d in dirs) if (color[s + d] == Go.EMPTY) return s + d
            s = next[s]
        } while (s != h)
        return Go.NONE
    }

    /** Stones that [c] would capture by playing at [p] (the move must be legal). */
    fun wouldCapture(p: Int, c: Int): Int {
        val other = Go.opponent(c)
        var total = 0
        var seen1 = -1
        var seen2 = -1
        var seen3 = -1
        for (d in dirs) {
            val q = p + d
            if (color[q] != other) continue
            val h = head[q]
            if (h == seen1 || h == seen2 || h == seen3) continue
            if (seen1 < 0) seen1 = h else if (seen2 < 0) seen2 = h else seen3 = h
            var shared = 0
            for (e in dirs) if (color[p + e] == other && head[p + e] == h) shared++
            if (libs[h] == shared) total += stones[h]
        }
        return total
    }

    /** Owner of [p] at the end of a playout: its stone, or the only color around an empty point. */
    fun simpleOwner(p: Int): Int {
        val x = color[p]
        if (x == Go.BLACK || x == Go.WHITE) return x
        if (x == Go.WALL) return Go.EMPTY
        var seen = 0
        for (d in dirs) {
            val y = color[p + d]
            if (y == Go.BLACK || y == Go.WHITE) seen = seen or y
        }
        return if (seen == Go.BLACK || seen == Go.WHITE) seen else Go.EMPTY
    }

    /** Every point from the first to the last playable one (border points in between included). */
    val allPoints: IntRange get() = w + 1 until w * w - w - 1
}

/** Final count of a game: area scoring (stones on the board plus the empty points they surround). */
class GoScore(val black: Int, val white: Int, val komi: Double, /** For each `row * n + column`: whose territory it is, or 0. */ val territory: IntArray) {
    val whiteTotal get() = white + komi
    /** Black's lead (negative when White leads). */
    val margin get() = black - whiteTotal
}

/**
 * Go rules as played here: Chinese (area) scoring, captures, no suicide, simple ko (the immediate
 * recapture of a single stone is forbidden), and the game ends after two passes in a row.
 */
object Go {
    const val EMPTY = 0
    const val BLACK = 1
    const val WHITE = 2
    const val WALL = 3

    /** "No point" inside the board (the corner of the border, never playable). */
    const val NONE = 0

    /** A pass in the list of moves (moves are `row * n + column`). */
    const val PASS = -1

    const val OK = 0
    const val OCCUPIED = 1
    const val KO = 2
    const val SUICIDE = 3

    /** Compensation given to White for playing second. */
    const val KOMI = 6.5

    fun opponent(c: Int) = 3 - c

    /** The board sizes offered on the rules screen (keys are stored in save files: never change them). */
    val VARIANTS = listOf(
        GameVariant("9", R.string.go_variant_9, R.string.go_variant_9_desc),
        GameVariant("13", R.string.go_variant_13, R.string.go_variant_13_desc),
        GameVariant("19", R.string.go_variant_19, R.string.go_variant_19_desc),
    )

    fun sizeOf(key: String?): Int = when (key) {
        "13" -> 13
        "19" -> 19
        else -> 9
    }

    /** The traditional dots drawn on the board ("star points"), as `row * n + column`. */
    fun starPoints(n: Int): List<Int> {
        val lines = when (n) {
            9 -> listOf(2, 6)
            13 -> listOf(3, 9)
            19 -> listOf(3, 9, 15)
            15 -> listOf(3, 11)
            else -> emptyList()
        }
        val corners = lines.flatMap { r -> lines.map { c -> r * n + c } }
        return if (n % 2 == 1 && n != 19) corners + ((n / 2) * n + n / 2) else corners
    }

    /**
     * Area score with the strings in [dead] taken off the board: each side counts its stones plus
     * the empty points (and dead stones) of the regions that only its stones border.
     */
    fun score(board: GoBoard, dead: Set<Int>, komi: Double): GoScore {
        val n = board.n
        val g = IntArray(n * n) { i -> if (i in dead) EMPTY else board.at(i) }
        val territory = IntArray(n * n)
        val seen = BooleanArray(n * n)
        val stack = IntArray(n * n)
        val region = IntArray(n * n)
        var black = g.count { it == BLACK }
        var white = g.count { it == WHITE }
        for (start in 0 until n * n) {
            if (g[start] != EMPTY || seen[start]) continue
            var top = 0
            var size = 0
            var borders = 0
            stack[top++] = start
            seen[start] = true
            while (top > 0) {
                val i = stack[--top]
                region[size++] = i
                val r = i / n
                val c = i % n
                for (k in 0 until 4) {
                    val rr = r + DR[k]
                    val cc = c + DC[k]
                    if (rr !in 0 until n || cc !in 0 until n) continue
                    val j = rr * n + cc
                    if (g[j] == EMPTY) {
                        if (!seen[j]) { seen[j] = true; stack[top++] = j }
                    } else borders = borders or g[j]
                }
            }
            if (borders == BLACK || borders == WHITE) {
                for (k in 0 until size) territory[region[k]] = borders
                if (borders == BLACK) black += size else white += size
            }
        }
        return GoScore(black, white, komi, territory)
    }

    private val DR = intArrayOf(-1, 1, 0, 0)
    private val DC = intArrayOf(0, 0, -1, 1)
}

/**
 * A game replayed from its list of moves (`row * n + column`, or [Go.PASS]). Black plays first.
 * Replaying stops at the first move that is not legal, so a damaged save cannot break the board.
 */
class GoRecord(val size: Int, moves: List<Int>) {
    val board = GoBoard(size)
    var toMove = Go.BLACK
        private set
    /** Passes in a row at the end of the game so far (two end the game). */
    var passes = 0
        private set
    /** The last move played: a point index, [Go.PASS], or null at the start. */
    var last: Int? = null
        private set
    var played = 0
        private set

    init {
        for (m in moves) {
            if (m == Go.PASS) {
                board.pass()
                passes++
            } else {
                if (m !in 0 until size * size) break
                val p = board.point(m)
                if (!board.isLegal(p, toMove)) break
                board.play(p, toMove)
                passes = 0
            }
            last = m
            played++
            toMove = Go.opponent(toMove)
        }
    }

    val over get() = passes >= 2

    /** [Go.OK] or why the side to move cannot play at [index]. */
    fun whyIllegal(index: Int) = board.whyIllegal(board.point(index), toMove)

    /** All the stones (as indices) of the string at [index], or empty. */
    fun stringIndices(index: Int): List<Int> {
        val p = board.point(index)
        if (board[p] != Go.BLACK && board[p] != Go.WHITE) return emptyList()
        return board.stringAt(p).map { board.index(it) }
    }
}
