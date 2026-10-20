package io.github.fershibli.megagames.games.pegs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette

/**
 * Where things are on a round board of [side] pixels: the 7×7 grid of holes sits in the middle
 * of a wooden disc, with room for the rim.
 */
class PegsGeometry(val side: Float) {
    val cell = side / 7.6f
    private val origin = (side - Pegs.SIZE * cell) / 2

    fun center(i: Int) = Offset(origin + (i % Pegs.SIZE + 0.5f) * cell, origin + (i / Pegs.SIZE + 0.5f) * cell)

    /** The cell under [pos], or -1 outside the grid. */
    fun cellAt(pos: Offset): Int {
        val c = ((pos.x - origin) / cell).let { if (it < 0) -1 else it.toInt() }
        val r = ((pos.y - origin) / cell).let { if (it < 0) -1 else it.toInt() }
        return if (c in 0 until Pegs.SIZE && r in 0 until Pegs.SIZE) r * Pegs.SIZE + c else -1
    }
}

/** The color of the marbles: a deep glass blue, or the strongest blue of the accessible palettes. */
fun marbleColor(p: Palette): Color = p.pieces[1]

/**
 * A round wooden board with its holes and glass marbles. [targets] are the holes the selected
 * marble can jump to and [movable] the marbles that have a jump (both empty on Hard).
 */
fun DrawScope.drawPegBoard(
    french: Boolean,
    pegs: List<Boolean>,
    p: Palette,
    selected: Int = -1,
    targets: Set<Int> = emptySet(),
    movable: Set<Int> = emptySet(),
    goal: Int = -1,
) {
    val side = size.minDimension
    val g = PegsGeometry(side)
    val mid = Offset(side / 2, side / 2)
    val radius = side / 2
    // Shadow under the board, the rim and the top with a warm light from the top-left.
    drawCircle(Color.Black.copy(alpha = 0.22f), radius * 0.98f, mid + Offset(radius * 0.02f, radius * 0.04f))
    drawCircle(p.woodDark, radius * 0.97f, mid)
    drawCircle(
        Brush.radialGradient(listOf(p.wood.lighter(0.28f), p.wood, p.wood.darker(0.18f)), mid - Offset(radius * 0.3f, radius * 0.35f), radius * 1.4f),
        radius * 0.92f, mid,
    )
    // Wood grain: a few soft rings, like the end of a turned board.
    for (k in 1..5) drawCircle(p.woodDark.copy(alpha = 0.10f), radius * (0.16f * k + 0.05f), mid + Offset(radius * 0.04f, -radius * 0.03f), style = Stroke(radius * 0.012f))
    drawCircle(Color.White.copy(alpha = 0.18f), radius * 0.92f, mid, style = Stroke(radius * 0.015f))
    if (p.strongOutlines) drawCircle(p.outline, radius * 0.97f, mid, style = Stroke(radius * 0.02f))

    val hole = g.cell * 0.3f
    val marble = g.cell * 0.4f
    val color = marbleColor(p)
    for (i in 0 until Pegs.CELLS) {
        if (!Pegs.isHole(french, i)) continue
        val c = g.center(i)
        // The hole: dark, with its lower edge catching the light.
        drawCircle(p.woodDark.lighter(0.25f), hole * 1.08f, c + Offset(0f, hole * 0.08f))
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.75f), p.woodDark.darker(0.3f)), c - Offset(0f, hole * 0.2f), hole * 1.2f), hole, c)
        if (i == goal && !pegs[i]) drawCircle(p.highlight.copy(alpha = 0.9f), hole * 0.55f, c, style = Stroke(hole * 0.16f))
        if (i in targets) {
            drawCircle(p.hint.copy(alpha = 0.55f), hole * 1.25f, c)
            drawCircle(p.hint, hole * 1.25f, c, style = Stroke(hole * 0.22f))
        }
        if (!pegs[i]) continue
        val lifted = i == selected
        val r = if (lifted) marble * 1.1f else marble
        val at = if (lifted) c - Offset(0f, marble * 0.12f) else c
        if (i in movable && !lifted) drawCircle(p.hint, marble * 1.16f, c, style = Stroke(marble * 0.14f))
        drawGlassMarble(at, r, color, shadow = if (lifted) marble * 0.3f else marble * 0.12f)
        if (lifted) drawCircle(p.highlight, r * 1.12f, at, style = Stroke(r * 0.2f))
        if (p.strongOutlines) drawCircle(p.outline, r, at, style = Stroke(r * 0.08f))
    }
}

/** A glass marble: a colored ball, a soft inner glow, a bright reflection and a shadow. */
fun DrawScope.drawGlassMarble(center: Offset, radius: Float, color: Color, shadow: Float = radius * 0.12f) {
    drawCircle(Color.Black.copy(alpha = 0.3f), radius, center + Offset(shadow * 0.6f, shadow))
    drawCircle(
        Brush.radialGradient(listOf(color.lighter(0.45f), color, color.darker(0.45f)), center - Offset(radius * 0.35f, radius * 0.4f), radius * 1.6f),
        radius, center,
    )
    // Light passing through the glass shows on the far side.
    drawCircle(color.lighter(0.35f).copy(alpha = 0.45f), radius * 0.4f, center + Offset(radius * 0.25f, radius * 0.3f))
    drawCircle(Color.White.copy(alpha = 0.85f), radius * 0.2f, center - Offset(radius * 0.38f, radius * 0.4f))
    drawCircle(Color.White.copy(alpha = 0.35f), radius * 0.09f, center + Offset(radius * 0.42f, -radius * 0.05f))
}

/** Home screen picture: an English board in the middle of a game, one marble lifted. */
fun DrawScope.pegsThumbnail(p: Palette) {
    drawRect(p.felt)
    val pegs = Pegs.fullBoard(false, Pegs.CENTER).toMutableList()
    for (i in listOf(10, 17, 2, 16, 22, 30, 33, 46, 3)) pegs[i] = false
    pegs[24] = true
    drawPegBoard(false, pegs, p, selected = 31, targets = setOf(33))
}
