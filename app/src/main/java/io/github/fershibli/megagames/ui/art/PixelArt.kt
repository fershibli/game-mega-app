package io.github.fershibli.megagames.ui.art

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import io.github.fershibli.megagames.core.ColorMode
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * Pixel art shared by the games. Every picture is a few lines of text where each character is a
 * color; at start it is turned into horizontal runs of one color, so drawing it is a handful of
 * rectangles and nothing is allocated while animating. Squares are plain rectangles, so the
 * pictures stay crisp at any size.
 */

/** Largest side of a picture, in squares. */
const val PIXEL_ART_MAX = 127

/**
 * A small picture made of colored squares, kept as runs packed in an [IntArray]: 7 bits of
 * column, 7 of row and 7 of length, then the color index.
 */
class PixelArt internal constructor(val width: Int, val height: Int, private val runs: IntArray, private val colors: Array<Color>) {
    /**
     * Draws the picture with its top-left corner at ([left], [top]); each square is [px] wide.
     * [flipX] mirrors it left to right (a sprite walking the other way).
     */
    fun draw(scope: DrawScope, left: Float, top: Float, px: Float, alpha: Float = 1f, flipX: Boolean = false) {
        for (r in runs) {
            val x = r and 127
            val y = (r shr 7) and 127
            val len = (r shr 14) and 127
            val startX = if (flipX) width - x - len else x
            scope.drawRect(colors[r ushr 21], Offset(left + startX * px, top + y * px), Size(len * px, px), alpha = alpha)
        }
    }

    /** The same picture with every color changed by [map] (a darker night, a faded ghost...). */
    fun recolored(map: (Color) -> Color): PixelArt = PixelArt(width, height, runs, Array(colors.size) { map(colors[it]) })

    /** Number of one-color runs (each one rectangle drawn). */
    val runCount: Int get() = runs.size
}

/** Builds a [PixelArt] from rows of characters; '.' and ' ' are transparent. */
fun pixelArt(rows: List<String>, palette: Map<Char, Color>): PixelArt {
    val width = rows.maxOf { it.length }
    require(width <= PIXEL_ART_MAX && rows.size <= PIXEL_ART_MAX) { "picture too big: ${width}x${rows.size}" }
    val colors = ArrayList<Color>()
    val index = HashMap<Char, Int>()
    val runs = ArrayList<Int>()
    rows.forEachIndexed { y, row ->
        var x = 0
        while (x < row.length) {
            val ch = row[x]
            var end = x + 1
            while (end < row.length && row[end] == ch) end++
            if (ch != '.' && ch != ' ') {
                val c = index.getOrPut(ch) { colors.add(palette[ch] ?: error("no color for '$ch'")); colors.size - 1 }
                runs += x or (y shl 7) or ((end - x) shl 14) or (c shl 21)
            }
            x = end
        }
    }
    return PixelArt(width, rows.size, runs.toIntArray(), colors.toTypedArray())
}

/** Shorthand: `pixelArt("o=4A3426 a=F4A259", "..oo..", ".oaao.")`. */
fun pixelArt(palette: String, vararg rows: String): PixelArt = pixelArt(rows.toList(), palette(palette))

/** "o=4A3426 a=F4A259" → map of characters to colors (6 hex digits, opaque). */
fun palette(spec: String): Map<Char, Color> =
    spec.split(' ').filter { it.isNotBlank() }.associate { it[0] to Color(0xFF000000 or it.substring(2).toLong(16)) }

/** Draws [art] as large as fits in the drawing area, centered, on whole pixels. */
fun DrawScope.drawFitted(art: PixelArt, alpha: Float = 1f) {
    val px = max(1f, floor(min(size.width / art.width, size.height / art.height)))
    val left = floor((size.width - art.width * px) / 2)
    val top = floor((size.height - art.height * px) / 2)
    art.draw(this, left, top, px, alpha)
}

// ---------------------------------------------------------------------------------------------
// Tile maps: a scene laid out as rows of characters, one tile picture per character.
// ---------------------------------------------------------------------------------------------

/**
 * A grid of tiles from rows of characters (`'.'` or a character without a tile is left empty).
 * Every tile picture should be [tileSize] squares wide and high.
 */
class TileMap(val rows: List<String>, private val tiles: Map<Char, PixelArt>, val tileSize: Int) {
    val columns: Int = rows.maxOfOrNull { it.length } ?: 0

    fun tileAt(col: Int, row: Int): Char = rows.getOrNull(row)?.getOrNull(col) ?: '.'

    /** Draws the map with its top-left corner at ([left], [top]); each square of a tile is [px] wide. */
    fun draw(scope: DrawScope, left: Float, top: Float, px: Float) {
        val step = tileSize * px
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch -> tiles[ch]?.draw(scope, left + c * step, top + r * step, px) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Isometric (2:1) grids: map cells drawn as diamonds, and taps turned back into cells.
// ---------------------------------------------------------------------------------------------

/**
 * A 2:1 isometric grid. Cell ([col], [row]) is a diamond [tileW] wide and [tileH] high whose top
 * corner sits at [origin] + the cell's offset; columns run down to the right, rows down to the left.
 */
class IsoGrid(val tileW: Float, val tileH: Float, val origin: Offset) {
    /** Top corner of cell ([col], [row]) on screen. Fractions give points inside a cell. */
    fun toScreen(col: Float, row: Float): Offset =
        Offset(origin.x + (col - row) * tileW / 2, origin.y + (col + row) * tileH / 2)

    /** Center of cell ([col], [row]) on screen. */
    fun center(col: Int, row: Int): Offset = toScreen(col + 0.5f, row + 0.5f)

    /** The map position (fractional column and row) under screen point [p]. */
    fun toMap(p: Offset): Pair<Float, Float> {
        val dx = (p.x - origin.x) / (tileW / 2)
        val dy = (p.y - origin.y) / (tileH / 2)
        return (dy + dx) / 2 to (dy - dx) / 2
    }

    /** The cell under screen point [p] (it may be outside the map: check the bounds). */
    fun cellAt(p: Offset): Pair<Int, Int> {
        val (c, r) = toMap(p)
        return floor(c).toInt() to floor(r).toInt()
    }

    companion object {
        /**
         * The largest grid of [columns] × [rows] cells that fits in [width] × [height], centered.
         * Tile widths are whole pixels so pixel-art tiles stay sharp.
         */
        fun fitting(columns: Int, rows: Int, width: Float, height: Float): IsoGrid {
            val span = (columns + rows).toFloat()
            val tileW = max(2f, floor(min(width / span * 2, height / span * 4)))
            val tileH = tileW / 2
            val usedW = span * tileW / 2
            val usedH = span * tileH / 2
            val left = (width - usedW) / 2
            val top = (height - usedH) / 2
            // The top corner of cell (0, 0) is `rows` half-tiles from the left edge of the drawing.
            return IsoGrid(tileW, tileH, Offset(left + rows * tileW / 2, top))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// A warm base palette for pixel scenes, adapted to the app's color modes.
// ---------------------------------------------------------------------------------------------

/**
 * Cozy colors shared by the pixel scenes (farm, board, car park...), so they look like one family.
 * Use [PixelColors.of] to get them adapted to the current [Palette].
 */
class PixelColors(
    val outline: Color,
    val grass: Color,
    val grassDark: Color,
    val soil: Color,
    val soilDark: Color,
    val wood: Color,
    val woodDark: Color,
    val stone: Color,
    val stoneDark: Color,
    val water: Color,
    val waterLight: Color,
    val sky: Color,
    val roof: Color,
    val leaf: Color,
    val sun: Color,
    val cream: Color,
) {
    companion object {
        /** The day colors, before any color mode. */
        val Day = PixelColors(
            outline = Color(0xFF3B2A20), grass = Color(0xFF8BC34A), grassDark = Color(0xFF5E9B3A),
            soil = Color(0xFF9C6B45), soilDark = Color(0xFF6E4A30), wood = Color(0xFFB9824F), woodDark = Color(0xFF7A5230),
            stone = Color(0xFFB8B2A7), stoneDark = Color(0xFF857E73), water = Color(0xFF4FA3E0), waterLight = Color(0xFFA8D8F5),
            sky = Color(0xFFBDE6FA), roof = Color(0xFFC0503A), leaf = Color(0xFF4E9A3E), sun = Color(0xFFFFD54F), cream = Color(0xFFFFF3D6),
        )

        /**
         * The colors for [p]: dimmed at night (dark mode), with a black outline and stronger
         * differences in high contrast.
         */
        fun of(p: Palette): PixelColors {
            val c = Day.map { adapt(it, p) }
            return if (p.mode == ColorMode.HIGH_CONTRAST) c.copyOutline(Color.Black) else c
        }

        /** Any scene color as [of] adapts the base ones: dimmed at night, stronger in high contrast. */
        fun adapt(color: Color, p: Palette): Color {
            var c = color
            if (p.isDark) c = lerp(c, Color(0xFF101828), 0.35f)
            if (p.mode == ColorMode.HIGH_CONTRAST) c = stretch(c)
            return c
        }

        /** Pushes a color away from middle grey, for more contrast between neighbours. */
        private fun stretch(c: Color): Color {
            fun s(v: Float) = ((v - 0.5f) * 1.25f + 0.5f).coerceIn(0f, 1f)
            return Color(s(c.red), s(c.green), s(c.blue), c.alpha)
        }
    }

    fun map(f: (Color) -> Color) = PixelColors(
        f(outline), f(grass), f(grassDark), f(soil), f(soilDark), f(wood), f(woodDark), f(stone), f(stoneDark),
        f(water), f(waterLight), f(sky), f(roof), f(leaf), f(sun), f(cream),
    )

    private fun copyOutline(c: Color) = PixelColors(
        c, grass, grassDark, soil, soilDark, wood, woodDark, stone, stoneDark, water, waterLight, sky, roof, leaf, sun, cream,
    )
}

private val adaptedArt = HashMap<Triple<PixelArt, Boolean, ColorMode>, PixelArt>()

/** This picture with its colors adapted to [p] like [PixelColors.of] (the same picture in the normal day mode). */
fun PixelArt.adaptedTo(p: Palette): PixelArt {
    if (!p.isDark && p.mode != ColorMode.HIGH_CONTRAST) return this
    return synchronized(adaptedArt) { adaptedArt.getOrPut(Triple(this, p.isDark, p.mode)) { recolored { PixelColors.adapt(it, p) } } }
}
