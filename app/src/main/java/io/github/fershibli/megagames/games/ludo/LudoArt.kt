package io.github.fershibli.megagames.games.ludo

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import io.github.fershibli.megagames.core.ColorMode
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.drawSymbol
import io.github.fershibli.megagames.ui.art.glossyDisc
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.art.raisedBlock
import io.github.fershibli.megagames.ui.theme.Palette

/**
 * Geometry of the classic cross-shaped board: 15×15 cells, a 6×6 yard in each corner, three
 * rows of track along each arm and the home triangles in the 3×3 center. Positions are in cell
 * units (x = column, y = row); multiply by the cell size to draw.
 */
object LudoBoard {
    const val CELLS = 15

    /** Row × 15 + column of each of the 52 track squares, clockwise from color 0's start square. */
    val TRACK: IntArray = buildList {
        for (c in 1..5) add(6 * 15 + c)
        for (r in 5 downTo 0) add(r * 15 + 6)
        add(0 * 15 + 7)
        for (r in 0..5) add(r * 15 + 8)
        for (c in 9..14) add(6 * 15 + c)
        add(7 * 15 + 14)
        for (c in 14 downTo 9) add(8 * 15 + c)
        for (r in 9..14) add(r * 15 + 8)
        add(14 * 15 + 7)
        for (r in 14 downTo 9) add(r * 15 + 6)
        for (c in 5 downTo 0) add(8 * 15 + c)
        add(7 * 15 + 0)
        add(6 * 15 + 0)
    }.toIntArray()

    /** Top-left cell of each color's yard. */
    private val YARD_ROW = intArrayOf(0, 0, 9, 9)
    private val YARD_COL = intArrayOf(0, 9, 9, 0)

    /** Cell (row × 15 + column) of the [k]-th square (0..4) of [color]'s home column. */
    fun homeColumnCell(color: Int, k: Int): Int = when (color) {
        0 -> 7 * 15 + 1 + k
        1 -> (1 + k) * 15 + 7
        2 -> 7 * 15 + 13 - k
        else -> (13 - k) * 15 + 7
    }

    /** Cell of a piece of [color] at [pos], or -1 in the yard or at home. */
    fun cell(color: Int, pos: Int): Int = when {
        pos in 0..Ludo.LAST_TRACK -> TRACK[Ludo.square(color, pos)]
        pos in Ludo.LAST_TRACK + 1 until Ludo.HOME -> homeColumnCell(color, pos - Ludo.LAST_TRACK - 1)
        else -> -1
    }

    fun yardRow(color: Int) = YARD_ROW[color]
    fun yardCol(color: Int) = YARD_COL[color]

    /** Whether cell (row, col) is inside [color]'s yard. */
    fun inYard(color: Int, row: Int, col: Int) = row - YARD_ROW[color] in 0..5 && col - YARD_COL[color] in 0..5

    /** The center 3×3, where the die is. */
    fun inCenter(row: Int, col: Int) = row in 6..8 && col in 6..8

    /** Position of piece [piece] of [color] waiting in its yard. */
    fun yardSpot(color: Int, piece: Int) = Offset(YARD_COL[color] + 2f + 2f * (piece % 2), YARD_ROW[color] + 2f + 2f * (piece / 2))

    /** Position of piece [piece] of [color] once it is home, along the base of its triangle. */
    fun homeSpot(color: Int, piece: Int): Offset {
        val along = 6.75f + piece * 0.5f
        return when (color) {
            0 -> Offset(6.25f, along)
            1 -> Offset(along, 6.25f)
            2 -> Offset(8.75f, along)
            else -> Offset(along, 8.75f)
        }
    }

    fun cellCenter(cell: Int) = Offset(cell % 15 + 0.5f, cell / 15 + 0.5f)
}

/** Palette color of each Ludo color (red, green, yellow, blue), the same hues in every mode. */
fun ludoColor(p: Palette, color: Int): Color {
    val index = if (p.mode == ColorMode.COLORBLIND) intArrayOf(5, 2, 3, 4)[color] else intArrayOf(0, 2, 3, 1)[color]
    return p.pieces[index]
}

/** Symbol of each color ([drawSymbol] index), so color is never the only cue: circle, triangle, square, diamond. */
private val SYMBOLS = intArrayOf(0, 2, 1, 3)

/** A Ludo piece: a glossy disc with its color's symbol on top. */
fun DrawScope.drawLudoPiece(center: Offset, radius: Float, p: Palette, color: Int, highlight: Color? = null) {
    val fill = ludoColor(p, color)
    val rim = if (p.strongOutlines) Color.Black else fill.darker(0.45f)
    if (highlight != null) drawCircle(highlight, radius * 1.32f, center, style = Stroke(radius * 0.3f))
    glossyDisc(center, radius, fill, rim)
    val mark = if (fill.luminance() > 0.45f) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.92f)
    drawSymbol(SYMBOLS[color], center, radius * 0.85f, mark)
}

/** A five-pointed star outline, used for the safe squares. */
private fun DrawScope.star(center: Offset, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val a = Math.PI * 2 * i / 10 - Math.PI / 2
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = center.x + (rr * kotlin.math.cos(a)).toFloat()
        val y = center.y + (rr * kotlin.math.sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Stroke(r * 0.18f))
}

/**
 * The board without pieces: the yards of the [playing] colors in full color (the others faded),
 * the track with the start squares, the stars when [stars] is true, the home columns and the
 * center triangles.
 */
fun DrawScope.drawLudoBoard(cell: Float, p: Palette, playing: Set<Int>, stars: Boolean) {
    val side = cell * LudoBoard.CELLS
    val line = if (p.strongOutlines) Color.Black else p.boardLine.copy(alpha = 0.55f)
    drawRect(p.boardLight, Offset.Zero, Size(side, side))
    fun tint(color: Int, f: Float): Color {
        val c = ludoColor(p, color)
        return if (color in playing) c.lighter(f) else lerpGray(c.lighter(f), p)
    }
    // Yards: colored frame with a light inner square and four round spots.
    for (color in 0..3) {
        val tl = Offset(LudoBoard.yardCol(color) * cell, LudoBoard.yardRow(color) * cell)
        drawRect(tint(color, 0f), tl, Size(cell * 6, cell * 6))
        drawRoundRect(p.boardLight, tl + Offset(cell, cell), Size(cell * 4, cell * 4), CornerRadius(cell * 0.4f))
        for (k in 0 until 4) drawCircle(tint(color, 0.55f), cell * 0.62f, LudoBoard.yardSpot(color, k) * cell)
    }
    // Track squares, start squares and home columns.
    for (i in 0 until Ludo.TRACK) {
        val cellIndex = LudoBoard.TRACK[i]
        val tl = Offset(cellIndex % 15 * cell, cellIndex / 15 * cell)
        if (i % 13 == 0) drawRect(tint(i / 13, 0.15f), tl, Size(cell, cell))
        if (stars && i % 13 == 8) star(tl + Offset(cell / 2, cell / 2), cell * 0.36f, line)
        drawRect(line, tl, Size(cell, cell), style = Stroke(1.5f))
    }
    for (color in 0..3) for (k in 0 until 5) {
        val cellIndex = LudoBoard.homeColumnCell(color, k)
        val tl = Offset(cellIndex % 15 * cell, cellIndex / 15 * cell)
        drawRect(tint(color, 0.15f), tl, Size(cell, cell))
        drawRect(line, tl, Size(cell, cell), style = Stroke(1.5f))
    }
    // Center: one triangle per color, pointing to the middle.
    val c = Offset(7.5f * cell, 7.5f * cell)
    val corners = arrayOf(Offset(6f, 6f), Offset(9f, 6f), Offset(9f, 9f), Offset(6f, 9f))
    // Color 0's triangle is on the left (between the bottom-left and top-left corners), then clockwise.
    for (color in 0..3) {
        val a = corners[(color + 3) % 4] * cell
        val b = corners[color] * cell
        val path = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }
        drawPath(path, tint(color, 0.05f))
        drawPath(path, line, style = Stroke(1.5f))
    }
    drawRect(if (p.strongOutlines) Color.Black else p.boardLine, Offset.Zero, Size(side, side), style = Stroke(cell * 0.06f))
}

private fun lerpGray(c: Color, p: Palette): Color =
    androidx.compose.ui.graphics.lerp(c, if (p.isDark) Color(0xFF3A3A3A) else Color(0xFFD0D0D0), 0.75f)

/** The die, [value] 1..6 (0 shows a blank face), in a frame of [frame]'s color. */
fun DrawScope.drawLudoDie(center: Offset, side: Float, value: Int, frame: Color, dimmed: Boolean) {
    val tl = center - Offset(side / 2, side / 2)
    drawRoundRect(frame, tl - Offset(side * 0.12f, side * 0.12f), Size(side * 1.24f, side * 1.24f), CornerRadius(side * 0.3f))
    val face = Color(0xFFFBF7EE)
    raisedBlock(tl, Size(side, side), face, face.darker(0.25f), face.darker(0.45f), side * 0.06f, side * 0.18f)
    val pip = Color(0xFF222222).copy(alpha = if (dimmed) 0.3f else 1f)
    val o = side * 0.26f
    fun dot(dx: Float, dy: Float) = drawCircle(pip, side * 0.1f, center + Offset(dx, dy))
    when (value) {
        1 -> dot(0f, 0f)
        2 -> { dot(-o, -o); dot(o, o) }
        3 -> { dot(-o, -o); dot(0f, 0f); dot(o, o) }
        4 -> { dot(-o, -o); dot(o, -o); dot(-o, o); dot(o, o) }
        5 -> { dot(-o, -o); dot(o, -o); dot(0f, 0f); dot(-o, o); dot(o, o) }
        6 -> { dot(-o, -o); dot(o, -o); dot(-o, 0f); dot(o, 0f); dot(-o, o); dot(o, o) }
    }
}

/** Home screen picture: the cross-shaped board with pieces of the four colors and the die. */
fun ludoThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val cell = size.minDimension / LudoBoard.CELLS
    drawLudoBoard(cell, p, setOf(0, 1, 2, 3), stars = true)
    val r = cell * 0.62f
    drawLudoPiece(LudoBoard.yardSpot(0, 0) * cell, r * 1.3f, p, 0)
    drawLudoPiece(LudoBoard.yardSpot(1, 3) * cell, r * 1.3f, p, 1)
    drawLudoPiece(LudoBoard.yardSpot(2, 1) * cell, r * 1.3f, p, 2)
    drawLudoPiece(LudoBoard.yardSpot(3, 2) * cell, r * 1.3f, p, 3)
    drawLudoDie(Offset(7.5f, 7.5f) * cell, cell * 2.2f, 5, ludoColor(p, 3), false)
}
