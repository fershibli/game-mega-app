package io.github.fershibli.megagames.games.trail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.fershibli.megagames.core.ColorMode
import io.github.fershibli.megagames.games.ludo.ludoColor
import io.github.fershibli.megagames.ui.art.PixelArt
import io.github.fershibli.megagames.ui.art.PixelColors
import io.github.fershibli.megagames.ui.art.adaptedTo
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.drawSymbol
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.art.pixelArt
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The life trail as a pixel-art board game: a wooden frame around six landscapes, one per stage
 * of life, and a winding path of round squares from the playground at the bottom to the little
 * house at the top. Everything is measured in board pixels ("dots") and drawn at a whole number
 * of screen pixels per dot, so the picture stays sharp.
 */

// ---------------------------------------------------------------------------------------------
// The map.
// ---------------------------------------------------------------------------------------------

/** Where everything sits on the board, in dots. */
object TrailMap {
    /** The board with its frame. */
    const val WIDTH = 96
    const val HEIGHT = 116

    /** Room under the board for the die. */
    const val DIE_ROOM = 18
    const val TOTAL_HEIGHT = HEIGHT + DIE_ROOM

    const val FRAME = 4
    const val BAND = 18
    const val PER_BAND = Trail.COLUMNS

    /** Top of the landscape of [stage] (0 = childhood, at the bottom). */
    fun bandTop(stage: Int) = FRAME + (Stage.entries.size - 1 - stage) * BAND

    /** Column of square [index] along its band: the path turns at the ends, like a snake. */
    fun column(index: Int): Int {
        val band = index / PER_BAND
        val k = index % PER_BAND
        return if (band % 2 == 0) k else PER_BAND - 1 - k
    }

    private fun columnX(col: Int) = FRAME + 7 + (col * 21 + 1) / 2

    /** Small fixed nudges, so the path looks drawn by hand rather than on a grid. */
    private val NUDGE_X = intArrayOf(0, 1, 0, -1, 1, 0, -1, 0)
    private val NUDGE_Y = intArrayOf(0, -1, 1, 0, 0, 1, 0, -1)

    /** The center of every square, in dots: a fixed list of 48 points along the path. */
    val POINTS: List<Pair<Int, Int>> = List(Trail.SQUARES) { i ->
        val band = i / PER_BAND
        val col = column(i)
        // Even columns sit low in the band, odd ones high: the path zigzags across.
        val special = i == 0 || i == Trail.FINISH
        val n = (i * 5 + band * 3) % NUDGE_X.size
        val x = columnX(col) + if (special) 0 else NUDGE_X[n]
        val y = bandTop(band) + (if (col % 2 == 0) 12 else 6) + if (special) 0 else NUDGE_Y[n]
        x to y
    }

    /** Width and height of square [index]: the special ones are bigger, the others vary a little. */
    fun squareSize(index: Int): Pair<Int, Int> = when {
        index == 0 || index == Trail.FINISH -> 11 to 9
        Trail.square(index) == Square.CHOICE -> 11 to 7
        index % 3 == 1 -> 11 to 7
        else -> 9 to 7
    }

    /**
     * The free corner next to each square where a little picture of the landscape goes: above
     * the low squares and below the high ones. Returns the center x and the bottom y, in dots,
     * or null where the path climbs to the next band.
     */
    fun pocket(stage: Int, col: Int): Pair<Int, Int>? {
        if (stage % 2 == 1 && (col == 0 || col == PER_BAND - 1)) return null
        if (stage == Stage.entries.size - 1 && col == PER_BAND - 1) return null
        val x = columnX(col)
        val top = bandTop(stage)
        return if (col % 2 == 0) x to top + 8 else x to top + BAND
    }
}

// ---------------------------------------------------------------------------------------------
// Pictures.
// ---------------------------------------------------------------------------------------------

private const val INK = "o=3B2A20"

/** The ground of each landscape, from childhood to the quiet years. */
private val GROUND = listOf(
    Color(0xFF9CCC65), // playground grass
    Color(0xFFB7C4CC), // city pavement
    Color(0xFFD9C08E), // sandy yard of the workshop
    Color(0xFF7FBF55), // garden lawn
    Color(0xFFE6D3A0), // beach sand
    Color(0xFFC9CF73), // orchard meadow
)

/** The color of the squares of each stage. */
private val SQUARE_FILL = listOf(
    Color(0xFFE6F4D2), Color(0xFFD7F0FC), Color(0xFFFFE9C9), Color(0xFFFBD3E1), Color(0xFFE3DAF2), Color(0xFFFFF7B8),
)

object TrailSprites {
    val heart = pixelArt(
        "r=E53935 h=FF8A80",
        "rr.rr",
        "rhrrr",
        "rrrrr",
        ".rrr.",
        "..r..",
    )
    val book = pixelArt(
        "w=FFFFFF l=9E9E9E s=5D4037",
        "wwsww",
        "llsll",
        "wwsww",
        "llsll",
        "sssss",
    )
    val signpost = pixelArt(
        "b=1E88E5 a=FB8C00 t=6D4C41",
        "bbt..",
        "bbtaa",
        "..taa",
        "..t..",
        ".ttt.",
    )
    val flag = pixelArt(
        "g=43A047 l=81C784 t=5D4037",
        "tlg..",
        "tggg.",
        "tgg..",
        "t....",
        "t....",
    )
    val home = pixelArt(
        "r=C0392B w=FFF8E1 h=E53935 d=8D6E63",
        "...r...",
        "..rrr..",
        ".rrrrr.",
        "rrrrrrr",
        ".wwwww.",
        ".whwdw.",
        ".wwwdw.",
    )
    val plain = pixelArt("d=000000", "d")

    // Childhood: playground and school.
    val swing = pixelArt(
        "t=8D5A33 r=5D4037 s=E53935",
        "ttttttttt",
        "t.r...r.t",
        "t.r...r.t",
        "t.r...r.t",
        "t.r...r.t",
        "t.sssss.t",
        "t.......t",
        "t.......t",
    )
    val slide = pixelArt(
        "t=8D5A33 s=FFB300 d=E08E00",
        "tt.......",
        "tt.......",
        "ttss.....",
        "t.tds....",
        "tt..ds...",
        "t.t..ds..",
        "tt....ds.",
        "t.t....dd",
    )
    val school = pixelArt(
        "$INK k=FFD54F r=C0503A y=FFE0B2 b=81D4FA d=8D6E63",
        "....k....",
        "...ror...",
        "..rrrrr..",
        ".rrrrrrr.",
        "ooooooooo",
        ".ybyyyby.",
        ".yyyyyyy.",
        ".yyydyyy.",
        ".yyydyyy.",
    )
    val ball = pixelArt(
        "r=E53935 w=FFFFFF",
        ".rw.",
        "rwwr",
        ".rr.",
    )

    // Youth: city and university.
    val building = pixelArt(
        "$INK b=8EA2AE w=FFF59D d=5D4037",
        "ooooooo",
        "obwbwbo",
        "obbbbbo",
        "obwbwbo",
        "obbbbbo",
        "obwbwbo",
        "obbbbbo",
        "obbdbbo",
        "obbdbbo",
    )
    val university = pixelArt(
        "$INK g=78909C c=ECEFF1 s=B0BEC5",
        "....g....",
        "..ggggg..",
        "ggggggggg",
        ".c.c.c.c.",
        ".c.c.c.c.",
        ".c.c.c.c.",
        "sssssssss",
        "ooooooooo",
    )
    val lamp = pixelArt(
        "p=455A64 l=FFF176",
        "ppp",
        "plp",
        ".p.",
        ".p.",
        ".p.",
        ".p.",
        ".p.",
        "ppp",
    )

    // Work: offices and a workshop.
    val office = pixelArt(
        "f=546E7A b=4FC3F7 l=B3E5FC",
        "ffffff",
        "fblbbf",
        "ffffff",
        "fbblbf",
        "ffffff",
        "flbbbf",
        "ffffff",
        "fbbblf",
        "ffffff",
    )
    val workshop = pixelArt(
        "$INK r=8D6E63 w=BCAAA4 g=757575 k=FFB300",
        "r..r..r..",
        "rr.rr.rr.",
        "rrrrrrrrr",
        "wwwwwwwww",
        "wkwgggggw",
        "kkkgggggw",
        "wkwgggggw",
        "ooooooooo",
    )
    val crate = pixelArt(
        "c=B9824F d=7A5230",
        "dddd",
        "dccd",
        "dcdd",
        "dddd",
    )

    // Family: houses and gardens.
    val house = pixelArt(
        "$INK r=D84315 w=FFF3D6 b=81D4FA d=6D4C41 c=8D6E63",
        "......c..",
        "...rrrc..",
        "..rrrrrr.",
        ".rrrrrrrr",
        "ooooooooo",
        ".wbwwwbw.",
        ".wwwdwww.",
        ".wwwdwww.",
    )
    val fence = pixelArt(
        "w=FFFFFF g=CFD8DC",
        "w.w.w.w",
        "wwwwwww",
        "w.w.w.w",
        "wgwgwgw",
    )
    val flowers = pixelArt(
        "p=EC407A y=FFEB3B b=5C6BC0 g=388E3C",
        "p.y.b",
        "g.g.g",
        "ggggg",
    )

    // Travel: port and mountains.
    val mountain = pixelArt(
        "w=FFFFFF s=9E9E9E d=757575",
        "....w....",
        "...www...",
        "..swwss..",
        "..ssssd..",
        ".sssdsdd.",
        ".ssssddd.",
        "sssssdddd",
    )
    val boat = pixelArt(
        "t=6D4C41 w=FFFFFF r=C0503A d=8E3B2A",
        "...t....",
        "...tw...",
        "...tww..",
        "...twww.",
        "rrrrrrrr",
        ".dddddd.",
    )
    val lighthouse = pixelArt(
        "$INK l=FFF176 r=E53935 w=FFFFFF",
        ".ooo.",
        ".olo.",
        "ooooo",
        ".rrr.",
        ".www.",
        ".rrr.",
        ".www.",
        "rrrrr",
        "ooooo",
    )

    // The quiet years: porch and orchard.
    val porch = pixelArt(
        "$INK r=6D4C41 w=FFE0B2 p=A1887F c=8D5A33",
        "..rrrrr..",
        ".rrrrrrr.",
        "rrrrrrrrr",
        "p.wwwww.p",
        "p.wwwww.p",
        "p.c.www.p",
        "pccpppppp",
        "ooooooooo",
    )
    val fruitTree = pixelArt(
        "l=6BBF4A g=4E9A3E f=FF7043 t=7A5230",
        "..lll..",
        ".lgflg.",
        "llflllf",
        "lglllgl",
        ".lfllg.",
        "..lll..",
        "...t...",
        "...t...",
        "..ttt..",
    )
    val bench = pixelArt(
        "c=B9824F d=7A5230",
        "cccccc",
        "......",
        "dddddd",
        "d....d",
    )
    val tree = pixelArt(
        "l=6BBF4A g=4E9A3E d=2E6B2A t=7A5230",
        "..lll..",
        ".lllgl.",
        "llglllg",
        "lllllgl",
        ".gldll.",
        "..ddd..",
        "...t...",
        "...t...",
        "..ttt..",
    )

    /** The little pictures of each landscape, one per column (null: just a tuft of grass). */
    val scenery: List<List<PixelArt?>> = listOf(
        listOf(null, ball, swing, tree, school, flowers, slide, tree),
        listOf(null, lamp, building, tree, university, lamp, building, null),
        listOf(office, crate, workshop, tree, office, crate, workshop, tree),
        listOf(null, flowers, house, fence, tree, flowers, house, null),
        listOf(mountain, boat, mountain, null, lighthouse, boat, mountain, null),
        listOf(null, bench, fruitTree, flowers, porch, fruitTree, fruitTree, null),
    )

    /** The picture on a story card of each stage. */
    val vignette: List<PixelArt> = listOf(swing, university, workshop, house, boat, porch)

    /** A walking person, in the color [c] (with [d] a darker shade for the trousers). */
    private val PERSON = listOf(
        "..ooo..",
        ".ohhho.",
        ".ossso.",
        "occccco",
        "scccccs",
        "occccco",
        ".odddo.",
        ".od.do.",
        ".oo.oo.",
    )
    private val STEP = listOf(
        "..ooo..",
        ".ohhho.",
        ".ossso.",
        "occccco",
        "scccccs",
        "occccco",
        ".odddo.",
        "od..do.",
        "oo...oo",
    )
    private val people = HashMap<Pair<Color, Boolean>, PixelArt>()

    fun person(color: Color, walking: Boolean, outline: Color): PixelArt = synchronized(people) {
        people.getOrPut(color to walking) {
            val palette = mapOf(
                'o' to outline, 'h' to Color(0xFF6D4C41), 's' to Color(0xFFF2C29B),
                'c' to color, 'd' to color.darker(0.45f),
            )
            pixelArt(if (walking) STEP else PERSON, palette)
        }
    }

    const val PERSON_W = 7
    const val PERSON_H = 9
}

/** An oval square of the path, built once per size and color. */
private val ovals = HashMap<String, PixelArt>()

private fun oval(w: Int, h: Int, fill: Color, rim: Color, shade: Color, shine: Color): PixelArt {
    val key = "$w $h ${fill.value} ${rim.value}"
    return synchronized(ovals) {
        ovals.getOrPut(key) {
            val inside = Array(h) { y ->
                BooleanArray(w) { x ->
                    val dx = (x + 0.5f - w / 2f) / (w / 2f)
                    val dy = (y + 0.5f - h / 2f) / (h / 2f)
                    dx * dx + dy * dy <= 1.08f
                }
            }
            fun at(x: Int, y: Int) = x in 0 until w && y in 0 until h && inside[y][x]
            val rows = List(h) { y ->
                String(CharArray(w) { x ->
                    when {
                        !at(x, y) -> '.'
                        !at(x - 1, y) || !at(x + 1, y) || !at(x, y - 1) || !at(x, y + 1) -> 'o'
                        !at(x, y + 2) -> 'd'
                        !at(x, y - 2) && x < w / 2 -> 'l'
                        else -> 'f'
                    }
                })
            }
            pixelArt(rows, mapOf('o' to rim, 'f' to fill, 'd' to shade, 'l' to shine))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Drawing.
// ---------------------------------------------------------------------------------------------

/** Where the board is on the screen: the top-left corner and the size of one dot. */
class TrailView(val left: Float, val top: Float, val px: Float) {
    fun at(x: Float, y: Float) = Offset(left + x * px, top + y * px)

    /** The middle of square [index] on the screen. */
    fun square(index: Int): Offset {
        val (x, y) = TrailMap.POINTS[index]
        return at(x + 0.5f, y + 0.5f)
    }

    companion object {
        /** The largest view of the board (and the die under it, if [withDie]) that fits in [size]. */
        fun fitting(size: Size, withDie: Boolean = true): TrailView {
            val h = if (withDie) TrailMap.TOTAL_HEIGHT else TrailMap.HEIGHT
            val raw = min(size.width / TrailMap.WIDTH, size.height / h)
            val px = if (raw >= 1f) floor(raw) else raw
            return TrailView(floor((size.width - TrailMap.WIDTH * px) / 2), floor((size.height - h * px) / 2), px)
        }
    }
}

private fun DrawScope.dots(v: TrailView, x: Int, y: Int, w: Int, h: Int, color: Color) =
    drawRect(color, v.at(x.toFloat(), y.toFloat()), Size(w * v.px, h * v.px))

private fun DrawScope.sprite(v: TrailView, art: PixelArt, left: Float, top: Float, p: Palette) =
    art.adaptedTo(p).draw(this, v.left + left * v.px, v.top + top * v.px, v.px)

/** A sprite centered on dot column [cx] with its feet on row [bottom]. */
private fun DrawScope.standing(v: TrailView, art: PixelArt, cx: Int, bottom: Int, p: Palette) =
    sprite(v, art, (cx - art.width / 2).toFloat(), (bottom - art.height).toFloat(), p)

/** A thick line of dots from one square to the next. */
private fun DrawScope.road(v: TrailView, a: Pair<Int, Int>, b: Pair<Int, Int>, width: Int, color: Color) {
    val steps = max(abs(b.first - a.first), abs(b.second - a.second)).coerceAtLeast(1)
    val half = width / 2
    for (k in 0..steps) {
        val x = a.first + (b.first - a.first) * k / steps
        val y = a.second + (b.second - a.second) * k / steps
        dots(v, x - half, y - half, width, width, color)
    }
}

private fun groundOf(stage: Int, p: Palette) = PixelColors.adapt(GROUND[stage], p)

/** The board without pawns or die: frame, landscapes, path and squares. */
fun DrawScope.drawTrailBoard(v: TrailView, p: Palette) {
    val c = PixelColors.of(p)
    val w = TrailMap.WIDTH
    val h = TrailMap.HEIGHT
    val f = TrailMap.FRAME
    // Landscapes, from the bottom up, each with a ragged edge into the next one.
    for (stage in Stage.entries.indices) {
        val top = TrailMap.bandTop(stage)
        val ground = groundOf(stage, p)
        dots(v, f, top, w - 2 * f, TrailMap.BAND, ground)
        val speck = ground.darker(0.12f)
        for (n in 0 until 16) {
            val x = f + 1 + (n * 37 + stage * 11) % (w - 2 * f - 2)
            val y = top + 1 + (n * 7 + stage * 5) % (TrailMap.BAND - 2)
            dots(v, x, y, 1, 1, speck)
        }
        if (stage == 4) {
            // The sea along the bottom of the travel band, with little waves.
            dots(v, f, top + 14, w - 2 * f, 4, c.water)
            for (x in f + 1 until w - f step 6) dots(v, x, top + 14, 2, 1, c.waterLight)
        }
        if (stage == 1) {
            // The pavement of the city, in slabs.
            val line = ground.lighter(0.25f)
            for (x in f + 5 until w - f step 9) dots(v, x, top + 1, 1, TrailMap.BAND - 2, line)
        }
        if (stage < Stage.entries.size - 1) {
            val above = groundOf(stage + 1, p)
            for (x in f until w - f step 2) dots(v, x, top, 1, 1, above)
        }
    }
    // The pictures of each landscape, in the corners the path leaves free.
    for (stage in Stage.entries.indices) for (col in 0 until TrailMap.PER_BAND) {
        val (x, bottom) = TrailMap.pocket(stage, col) ?: continue
        val art = TrailSprites.scenery[stage][col]
        if (art != null) {
            standing(v, art, x, bottom, p)
        } else {
            val tuft = groundOf(stage, p).darker(0.3f)
            dots(v, x - 2, bottom - 2, 1, 2, tuft)
            dots(v, x, bottom - 3, 1, 3, tuft)
            dots(v, x + 2, bottom - 2, 1, 2, tuft)
        }
    }
    // The path: a dirt road under the squares.
    val points = TrailMap.POINTS
    for (i in 0 until Trail.FINISH) road(v, points[i], points[i + 1], 5, c.soilDark)
    for (i in 0 until Trail.FINISH) road(v, points[i], points[i + 1], 3, c.soil)
    // The squares, each with its mark.
    val rim = if (p.strongOutlines) Color.Black else c.outline
    for (i in 0 until Trail.SQUARES) {
        val (x, y) = points[i]
        val (sw, sh) = TrailMap.squareSize(i)
        val special = i == 0 || i == Trail.FINISH
        val fill = PixelColors.adapt(if (special) Color(0xFFFFF8E1) else SQUARE_FILL[Trail.stage(i).ordinal], p)
        // Already in the palette's colors: drawn as they are.
        oval(sw, sh, fill, rim, fill.darker(0.18f), fill.lighter(0.5f)).draw(this, v.left + (x - sw / 2) * v.px, v.top + (y - sh / 2) * v.px, v.px)
        val mark = when (Trail.square(i)) {
            Square.HEART -> TrailSprites.heart
            Square.STORY -> TrailSprites.book
            Square.CHOICE -> TrailSprites.signpost
            Square.START -> TrailSprites.flag
            Square.FINISH -> TrailSprites.home
            Square.PLAIN -> null
        }
        if (mark != null) {
            sprite(v, mark, (x - mark.width / 2).toFloat(), (y - mark.height / 2).toFloat(), p)
        } else {
            dots(v, x, y, 1, 1, fill.darker(0.35f))
        }
    }
    drawFrame(v, p, c)
}

/** The wooden frame around the board, with a grain and a nail in each corner. */
private fun DrawScope.drawFrame(v: TrailView, p: Palette, c: PixelColors) {
    val w = TrailMap.WIDTH
    val h = TrailMap.HEIGHT
    val f = TrailMap.FRAME
    val edge = if (p.strongOutlines) Color.Black else c.outline
    dots(v, 0, 0, w, f, c.wood)
    dots(v, 0, h - f, w, f, c.wood)
    dots(v, 0, 0, f, h, c.wood)
    dots(v, w - f, 0, f, h, c.wood)
    // Grain: short darker strokes along the planks.
    for (x in 3 until w - 3 step 7) {
        dots(v, x, 1, 3, 1, c.woodDark)
        dots(v, x + 3, h - 3, 3, 1, c.woodDark)
    }
    for (y in 5 until h - 3 step 8) {
        dots(v, 1, y, 1, 3, c.woodDark)
        dots(v, w - 3, y + 3, 1, 3, c.woodDark)
    }
    // Outer and inner edges.
    dots(v, 0, 0, w, 1, edge)
    dots(v, 0, h - 1, w, 1, edge)
    dots(v, 0, 0, 1, h, edge)
    dots(v, w - 1, 0, 1, h, edge)
    dots(v, f - 1, f - 1, w - 2 * f + 2, 1, c.woodDark)
    dots(v, f - 1, h - f, w - 2 * f + 2, 1, c.woodDark)
    dots(v, f - 1, f - 1, 1, h - 2 * f + 2, c.woodDark)
    dots(v, w - f, f - 1, 1, h - 2 * f + 2, c.woodDark)
    for ((x, y) in listOf(1 to 1, w - 3 to 1, 1 to h - 3, w - 3 to h - 3)) {
        dots(v, x, y, 2, 2, c.stone)
        dots(v, x, y, 1, 1, c.stone.lighter(0.5f))
    }
}

/** The symbol of each pawn color (Ludo's), so color is never the only cue. */
private val SYMBOLS = intArrayOf(0, 2, 1, 3)

/**
 * A pawn: a little person of color [color] (a Ludo color index) standing with its feet at
 * [foot], [scale] times the board's dots. [hop] lifts it while it walks; [turn] puts an arrow
 * over its head.
 */
fun DrawScope.drawTrailPerson(v: TrailView, foot: Offset, p: Palette, color: Int, scale: Float = 1f, walking: Boolean = false, turn: Boolean = false) {
    val px = v.px * scale
    val outline = if (p.strongOutlines) Color.Black else Color(0xFF3B2A20)
    val fill = ludoColor(p, color)
    val art = TrailSprites.person(fill, walking, outline)
    val left = foot.x - TrailSprites.PERSON_W * px / 2
    val top = foot.y - TrailSprites.PERSON_H * px
    // A soft shadow on the ground.
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(foot.x - 3 * px, foot.y - px * 0.6f), Size(6 * px, px * 1.4f))
    art.draw(this, left, top, px)
    val mark = if (fill.red * 0.3f + fill.green * 0.59f + fill.blue * 0.11f > 0.5f) Color.Black.copy(alpha = 0.75f) else Color.White
    drawSymbol(SYMBOLS[color], Offset(foot.x, top + 4.5f * px), px * 2.6f, mark)
    if (turn) {
        val arrow = p.highlight
        val y = top - 3.5f * px
        drawRect(arrow, Offset(foot.x - 2.5f * px, y), Size(5 * px, px))
        drawRect(arrow, Offset(foot.x - 1.5f * px, y + px), Size(3 * px, px))
        drawRect(arrow, Offset(foot.x - 0.5f * px, y + 2 * px), Size(px, px))
    }
}

/** The feet of a pawn [k] of [n] sharing a square, around the square's middle. */
fun pawnFoot(v: TrailView, center: Offset, k: Int, n: Int): Offset {
    val spread = when (n) {
        1 -> 0f
        2 -> (k - 0.5f) * 5f
        else -> (k - (n - 1) / 2f) * 3.5f
    }
    return center + Offset(spread * v.px, 2f * v.px)
}

/** A point between squares [from] and [to] at [t] (0..1), lifted a little in the middle of the hop. */
fun hopFoot(v: TrailView, from: Int, to: Int, t: Float): Offset {
    val a = v.square(from)
    val b = v.square(to)
    val lift = sin(t * Math.PI).toFloat() * 3f * v.px
    return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t - lift + 2f * v.px)
}

/** The die, under the board, on a mat of the color of the seat to play. [shake] tips it while it rolls. */
fun DrawScope.drawTrailDie(v: TrailView, value: Int, mat: Color, dimmed: Boolean, shake: Int, p: Palette) {
    val c = PixelColors.of(p)
    val edge = if (p.strongOutlines) Color.Black else c.outline
    val cx = TrailMap.WIDTH / 2
    val top = TrailMap.HEIGHT + 3
    dots(v, cx - 8, top - 1, 16, 15, mat.darker(0.3f))
    dots(v, cx - 9, top, 18, 13, mat.darker(0.3f))
    dots(v, cx - 8, top, 16, 13, mat)
    val left = cx - 6 + shake
    val dieTop = top + 1 - abs(shake)
    val face = Color(0xFFFBF7EE)
    dots(v, left + 1, dieTop, 10, 12, edge)
    dots(v, left, dieTop + 1, 12, 10, edge)
    dots(v, left + 1, dieTop + 1, 10, 10, face)
    dots(v, left + 1, dieTop + 9, 10, 2, face.darker(0.18f))
    val ink = Color(0xFF222222).copy(alpha = if (dimmed) 0.3f else 1f)
    fun pip(i: Int, j: Int) = dots(v, left + 2 + i * 3, dieTop + 2 + j * 3, 2, 2, ink)
    when (value) {
        1 -> pip(1, 1)
        2 -> { pip(0, 0); pip(2, 2) }
        3 -> { pip(0, 0); pip(1, 1); pip(2, 2) }
        4 -> { pip(0, 0); pip(2, 0); pip(0, 2); pip(2, 2) }
        5 -> { pip(0, 0); pip(2, 0); pip(1, 1); pip(0, 2); pip(2, 2) }
        6 -> { pip(0, 0); pip(2, 0); pip(0, 1); pip(2, 1); pip(0, 2); pip(2, 2) }
    }
}

/** The picture on the story card of [stage]: a bit of its landscape with its main sight. */
fun DrawScope.drawTrailVignette(stage: Int, p: Palette) {
    val art = TrailSprites.vignette[stage]
    val dotsW = art.width + 6
    val dotsH = art.height + 4
    val px = max(1f, floor(min(size.width / dotsW, size.height / dotsH)))
    val v = TrailView(floor((size.width - dotsW * px) / 2), floor((size.height - dotsH * px) / 2), px)
    val c = PixelColors.of(p)
    dots(v, 0, 0, dotsW, dotsH, c.sky)
    val ground = groundOf(stage, p)
    dots(v, 0, dotsH - 3, dotsW, 3, if (stage == 4) c.water else ground)
    standing(v, art, dotsW / 2, dotsH - 1, p)
    val edge = if (p.strongOutlines) Color.Black else c.outline
    dots(v, 0, 0, dotsW, 1, edge)
    dots(v, 0, dotsH - 1, dotsW, 1, edge)
    dots(v, 0, 0, 1, dotsH, edge)
    dots(v, dotsW - 1, 0, 1, dotsH, edge)
}

/** The color of the story card's paper for [p]. */
fun trailPaper(p: Palette): Color = when {
    p.mode == ColorMode.HIGH_CONTRAST -> p.surface
    p.isDark -> Color(0xFF3A3227)
    else -> Color(0xFFFFF4DC)
}
