package io.github.fershibli.megagames.games.tangram

import androidx.annotation.StringRes
import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A point of the table, in units where the small triangle's long side is 2. */
data class Pt(val x: Double, val y: Double) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    fun distanceTo(o: Pt) = hypot(x - o.x, y - o.y)
}

/** The five kinds of pieces with their outline before any turn (y grows downwards). */
enum class TanKind(val code: Char, val outline: List<Pt>) {
    LARGE('L', listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(2.0, 2.0))),
    MEDIUM('M', listOf(Pt(0.0, 0.0), Pt(2.0, 0.0), Pt(0.0, 2.0))),
    SMALL('S', listOf(Pt(0.0, 0.0), Pt(2.0, 0.0), Pt(1.0, 1.0))),
    SQUARE('Q', listOf(Pt(0.0, 0.0), Pt(1.0, 1.0), Pt(0.0, 2.0), Pt(-1.0, 1.0))),
    PARALLELOGRAM('P', listOf(Pt(0.0, 0.0), Pt(2.0, 0.0), Pt(3.0, 1.0), Pt(1.0, 1.0))),
}

/**
 * One piece on the table: its kind (index in [TanKind]), [rot] eighths of a turn clockwise,
 * mirrored first when [flip], and moved by ([x], [y]).
 */
@Serializable
data class TanPiece(val kind: Int, val rot: Int, val flip: Boolean, val x: Double, val y: Double) {
    val tanKind: TanKind get() = TanKind.entries[kind]
    fun corners(): List<Pt> = Tangram.place(tanKind, rot, flip, x, y)
}

/** A silhouette to fill, with the pieces of one way to make it ([data], see [TangramShapes]). */
class TanShape(val key: String, @StringRes val name: Int, private val root2: Boolean, private val data: String) {
    /** The pieces of the known solution, moved so the shape starts at (0, 0). */
    val solution: List<TanPiece> by lazy {
        val unit = if (root2) sqrt(2.0) else 1.0
        val raw = data.split(' ').map { token ->
            val kind = TanKind.entries.first { it.code == token[0] }
            val flip = token.contains('f')
            val (x, y) = token.substringAfter(':').split(',').map { it.toInt() * unit }
            TanPiece(kind.ordinal, token[1].digitToInt(), flip, x, y)
        }
        val corners = raw.flatMap { it.corners() }
        val minX = corners.minOf { it.x }
        val minY = corners.minOf { it.y }
        raw.map { it.copy(x = it.x - minX, y = it.y - minY) }
    }
    val width: Double by lazy { solution.flatMap { it.corners() }.maxOf { it.x } }
    val height: Double by lazy { solution.flatMap { it.corners() }.maxOf { it.y } }
}

/**
 * A tangram in progress. [pieces] are the player's seven pieces in drawing order (the last one
 * is on top), [lines] the solution pieces whose inner edges are drawn faintly, [hinted] the
 * solution places already filled by a Hint.
 */
@Serializable
data class TangramState(
    val shape: Int,
    val pieces: List<TanPiece>,
    val lines: List<Int>,
    val hints: Int = 0,
)

/**
 * Tangram: seven flat pieces cut from a square (two large triangles, a medium one, two small
 * ones, a square and a parallelogram) fill a silhouette without overlapping. Pieces are dragged
 * on the table, a tap turns one by an eighth of a turn and a button flips the parallelogram.
 *
 * The silhouette is placed at the top of a table; the pieces start below it, in a tray. A piece
 * let go near a place of the known solution that has its kind and its turn snaps into it; one
 * let go with a corner near another corner snaps there, so other solutions can be built too. The
 * figure is done when sample points cover the silhouette (allowing for rounding).
 */
object Tangram {
    /** The kinds of the seven pieces, in their fixed order. */
    val SET = listOf(TanKind.LARGE, TanKind.LARGE, TanKind.MEDIUM, TanKind.SMALL, TanKind.SMALL, TanKind.SQUARE, TanKind.PARALLELOGRAM)
    const val AREA = 16.0
    private const val TRAY_GAP = 1.2
    private const val MARGIN = 0.6

    val SHAPES: List<TanShape> get() = TangramShapes.ALL

    private val COS = DoubleArray(8) { cos(it * Math.PI / 4) }
    private val SIN = DoubleArray(8) { sin(it * Math.PI / 4) }

    /** The corners of a [kind] piece turned [rot] eighths clockwise (after a mirror), moved by (x, y). */
    fun place(kind: TanKind, rot: Int, flip: Boolean, x: Double, y: Double): List<Pt> {
        val r = ((rot % 8) + 8) % 8
        return kind.outline.map { p ->
            val px = if (flip) -p.x else p.x
            Pt(px * COS[r] - p.y * SIN[r] + x, px * SIN[r] + p.y * COS[r] + y)
        }
    }

    fun centroid(corners: List<Pt>) = Pt(corners.sumOf { it.x } / corners.size, corners.sumOf { it.y } / corners.size)

    fun area(corners: List<Pt>): Double {
        var a = 0.0
        for (i in corners.indices) {
            val p = corners[i]
            val q = corners[(i + 1) % corners.size]
            a += p.x * q.y - q.x * p.y
        }
        return abs(a) / 2
    }

    /** True when [p] is inside the convex outline [corners] (or within [margin] of it). */
    fun contains(corners: List<Pt>, p: Pt, margin: Double = 0.0): Boolean {
        var sign = 0
        var inside = true
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (s == 0) continue
            if (sign == 0) sign = s else if (s != sign) inside = false
        }
        if (inside) return true
        if (margin <= 0.0) return false
        return corners.indices.any { i -> segmentDistance(p, corners[i], corners[(i + 1) % corners.size]) <= margin }
    }

    private fun segmentDistance(p: Pt, a: Pt, b: Pt): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /** Area shared by two convex outlines (Sutherland–Hodgman clipping). */
    fun overlap(a: List<Pt>, b: List<Pt>): Double {
        var out = a
        // Clip against every edge of b, keeping the side b's inside is on.
        val orientation = signedArea(b)
        for (i in b.indices) {
            if (out.isEmpty()) break
            val e1 = b[i]
            val e2 = b[(i + 1) % b.size]
            fun side(p: Pt) = ((e2.x - e1.x) * (p.y - e1.y) - (e2.y - e1.y) * (p.x - e1.x)) * orientation
            val input = out
            out = ArrayList()
            for (j in input.indices) {
                val cur = input[j]
                val prev = input[(j + input.size - 1) % input.size]
                val sc = side(cur)
                val sp = side(prev)
                if (sc >= 0) {
                    if (sp < 0) out.add(cross(prev, cur, sp, sc))
                    out.add(cur)
                } else if (sp >= 0) {
                    out.add(cross(prev, cur, sp, sc))
                }
            }
        }
        return if (out.size < 3) 0.0 else area(out)
    }

    private fun signedArea(c: List<Pt>): Double {
        var a = 0.0
        for (i in c.indices) a += c[i].x * c[(i + 1) % c.size].y - c[(i + 1) % c.size].x * c[i].y
        return if (a >= 0) 1.0 else -1.0
    }

    private fun cross(p: Pt, q: Pt, sp: Double, sq: Double): Pt {
        val t = sp / (sp - sq)
        return Pt(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t)
    }

    // ----- The table --------------------------------------------------------------------------

    /** Where the table's parts are, for one shape: the silhouette on top, the tray below it. */
    class Table(val shape: TanShape) {
        val trayWidth = 11.0
        val trayHeight = 4.6
        val width = maxOf(shape.width, trayWidth) + MARGIN * 2
        val height = shape.height + TRAY_GAP + trayHeight + MARGIN * 2
        /** The solution pieces, where the silhouette is drawn on the table. */
        val target: List<TanPiece> = shape.solution.map { it.copy(x = it.x + (width - shape.width) / 2, y = it.y + MARGIN) }
        val targetCorners: List<List<Pt>> = target.map { it.corners() }
        val trayTop = MARGIN + shape.height + TRAY_GAP

        /** The starting place of piece [i] in the tray. */
        fun home(i: Int): TanPiece {
            val left = (width - 10.8) / 2
            val (x, y) = when (i) {
                0 -> 0.0 to 0.0
                1 -> 4.4 to 0.0
                2 -> 8.8 to 0.0
                3 -> 0.0 to 2.8
                4 -> 2.5 to 2.8
                5 -> 6.0 to 2.5
                else -> 7.6 to 2.8
            }
            return TanPiece(SET[i].ordinal, 0, false, left + x, trayTop + y)
        }

        /** Points spread over the silhouette, used to tell when it is covered. */
        val samples: List<Pt> by lazy {
            val step = 0.25
            buildList {
                var y = MARGIN + step * 0.43
                while (y < MARGIN + shape.height) {
                    var x = step * 0.37
                    while (x < width) {
                        val p = Pt(x, y)
                        if (targetCorners.any { contains(it, p) }) add(p)
                        x += step
                    }
                    y += step
                }
            }
        }
    }

    private val tables = HashMap<Int, Table>()

    fun table(shape: Int): Table = synchronized(tables) { tables.getOrPut(shape) { Table(SHAPES[shape]) } }

    fun newGame(d: Difficulty, random: Random = Random.Default, avoid: Int = -1): TangramState {
        var shape = random.nextInt(SHAPES.size)
        if (shape == avoid) shape = (shape + 1) % SHAPES.size
        return start(shape, d, random)
    }

    fun start(shape: Int, d: Difficulty, random: Random = Random.Default): TangramState {
        val t = table(shape)
        val lines = when (d) {
            Difficulty.EASY -> (0 until 7).toList()
            Difficulty.MEDIUM -> (0 until 7).shuffled(random).take(3).sorted()
            Difficulty.HARD -> emptyList()
        }
        return TangramState(shape, List(7) { t.home(it) }, lines)
    }

    /** Puts every piece back in the tray. */
    fun restart(s: TangramState): TangramState = s.copy(pieces = List(7) { table(s.shape).home(it) })

    /** Share of the silhouette covered by the pieces (1.0 when it is filled). */
    fun coverage(s: TangramState): Double {
        val t = table(s.shape)
        val corners = s.pieces.map { it.corners() }
        val covered = t.samples.count { p -> corners.any { contains(it, p) } }
        return covered.toDouble() / t.samples.size
    }

    /** True when the silhouette is filled (some rounding is allowed for). */
    fun solved(s: TangramState): Boolean = coverage(s) >= 0.97

    /** Moves piece [i] to the top of the pile (drawn last, touched first). */
    fun lift(s: TangramState, i: Int): Pair<TangramState, Int> {
        if (i == s.pieces.size - 1) return s to i
        val list = s.pieces.toMutableList()
        val p = list.removeAt(i)
        list.add(p)
        return s.copy(pieces = list) to list.size - 1
    }

    /** The topmost piece under [p], with a touch margin around each piece. */
    fun pieceAt(s: TangramState, p: Pt, margin: Double): Int {
        for (i in s.pieces.indices.reversed()) if (contains(s.pieces[i].corners(), p, 0.0)) return i
        for (i in s.pieces.indices.reversed()) if (contains(s.pieces[i].corners(), p, margin)) return i
        return -1
    }

    fun move(s: TangramState, i: Int, dx: Double, dy: Double): TangramState {
        val t = table(s.shape)
        val piece = s.pieces[i]
        val c = centroid(piece.corners())
        // Keep the piece's middle on the table.
        val nx = (c.x + dx).coerceIn(0.2, t.width - 0.2) - c.x
        val ny = (c.y + dy).coerceIn(0.2, t.height - 0.2) - c.y
        return s.copy(pieces = s.pieces.toMutableList().also { it[i] = piece.copy(x = piece.x + nx, y = piece.y + ny) })
    }

    /** Turns piece [i] by an eighth of a turn clockwise around its middle. */
    fun rotate(s: TangramState, i: Int): TangramState = turnInPlace(s, i) { it.copy(rot = (it.rot + 1) % 8) }

    /** Flips the parallelogram over (the only piece that changes when flipped). */
    fun flip(s: TangramState): TangramState {
        val i = s.pieces.indexOfFirst { it.tanKind == TanKind.PARALLELOGRAM }
        return turnInPlace(s, i) { it.copy(flip = !it.flip) }
    }

    private fun turnInPlace(s: TangramState, i: Int, change: (TanPiece) -> TanPiece): TangramState {
        val before = s.pieces[i]
        val c0 = centroid(before.corners())
        val turned = change(before)
        val c1 = centroid(turned.corners())
        val moved = turned.copy(x = turned.x + c0.x - c1.x, y = turned.y + c0.y - c1.y)
        return snap(s.copy(pieces = s.pieces.toMutableList().also { it[i] = moved }), i)
    }

    private fun sameCorners(a: List<Pt>, b: List<Pt>, tolerance: Double = 1e-6): Boolean =
        a.size == b.size && a.all { p -> b.any { it.distanceTo(p) < tolerance } }

    /** The solution places of [piece]'s kind that it fills exactly. */
    private fun slotOf(t: Table, piece: TanPiece): Int {
        val corners = piece.corners()
        return t.target.indices.firstOrNull { t.target[it].kind == piece.kind && sameCorners(t.targetCorners[it], corners, 1e-3) } ?: -1
    }

    /**
     * Snaps piece [i] after it was let go: into a solution place of its kind and turn when it is
     * close, otherwise corner to corner when one of its corners is near the silhouette's or
     * another piece's.
     */
    fun snap(s: TangramState, i: Int, reach: Double = 0.75): TangramState {
        val t = table(s.shape)
        val piece = s.pieces[i]
        val corners = piece.corners()
        val c = centroid(corners)
        val taken = s.pieces.indices.filter { it != i }.map { slotOf(t, s.pieces[it]) }.toSet()
        var best: TanPiece? = null
        var bestDistance = reach
        for (k in t.target.indices) {
            val goal = t.target[k]
            if (goal.kind != piece.kind || k in taken) continue
            val gc = centroid(t.targetCorners[k])
            val shift = gc - c
            val d = hypot(shift.x, shift.y)
            if (d < bestDistance && sameCorners(t.targetCorners[k], corners.map { it + shift }, 1e-3)) {
                bestDistance = d
                best = piece.copy(x = piece.x + shift.x, y = piece.y + shift.y)
            }
        }
        if (best == null) {
            // Corner to corner: the closest pair within a small reach.
            val others = t.targetCorners.flatten() + s.pieces.indices.filter { it != i }.flatMap { s.pieces[it].corners() }
            var shift: Pt? = null
            var d = reach * 0.6
            for (p in corners) for (q in others) {
                val dd = p.distanceTo(q)
                if (dd < d) {
                    d = dd
                    shift = q - p
                }
            }
            if (shift != null) best = piece.copy(x = piece.x + shift.x, y = piece.y + shift.y)
        }
        if (best == null) return s
        return s.copy(pieces = s.pieces.toMutableList().also { it[i] = best })
    }

    /** A tray place for a piece of [kind] that no piece is lying on yet (or the first one). */
    private fun freeHome(t: Table, pieces: List<TanPiece>, kind: Int): TanPiece {
        val homes = SET.indices.filter { SET[it].ordinal == kind }.map { t.home(it) }
        return homes.firstOrNull { h -> pieces.none { it == h } } ?: homes.first()
    }

    /** True when piece [i] fills a place of the known solution. */
    fun inPlace(s: TangramState, i: Int): Boolean = slotOf(table(s.shape), s.pieces[i]) >= 0

    /**
     * Places one piece where it belongs: the first solution place not filled yet gets a piece of
     * its kind that is not in place. Pieces lying on that place go back to the tray.
     */
    fun hint(s: TangramState): TangramState {
        val t = table(s.shape)
        val filled = s.pieces.map { slotOf(t, it) }
        val slot = t.target.indices.firstOrNull { it !in filled } ?: return s
        val goal = t.target[slot]
        val i = s.pieces.indices.firstOrNull { s.pieces[it].kind == goal.kind && filled[it] < 0 } ?: return s
        val pieces = s.pieces.toMutableList()
        pieces[i] = goal
        // Pieces lying on that place, and not in their own, go back to the tray.
        val area = t.targetCorners[slot]
        for (j in pieces.indices) {
            if (j == i || filled[j] >= 0) continue
            if (overlap(pieces[j].corners(), area) > 0.05) pieces[j] = freeHome(t, pieces, pieces[j].kind)
        }
        val moved = s.copy(pieces = pieces, hints = s.hints + 1)
        return lift(moved, i).first
    }
}
