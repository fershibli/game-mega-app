package io.github.fershibli.megagames.games.blocks

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.theme.Palette

/**
 * One soft, rounded block: a darker base gives it a little thickness and a light band on top
 * makes it look like a smooth wooden toy block. Plain shapes only, no gradients, so drawing a
 * full board allocates nothing.
 */
fun DrawScope.drawBlock(topLeft: Offset, cell: Float, color: Color, alpha: Float = 1f) {
    val inset = cell * 0.06f
    val side = cell - inset * 2
    val radius = CornerRadius(cell * 0.2f)
    val depth = cell * 0.1f
    drawRoundRect(color.darker(0.3f), Offset(topLeft.x + inset, topLeft.y + inset), Size(side, side), radius, alpha = alpha)
    drawRoundRect(color, Offset(topLeft.x + inset, topLeft.y + inset), Size(side, side - depth), radius, alpha = alpha)
    drawRoundRect(
        color.lighter(0.45f), Offset(topLeft.x + inset + side * 0.18f, topLeft.y + inset + side * 0.1f),
        Size(side * 0.64f, side * 0.14f), CornerRadius(side * 0.07f), alpha = alpha * 0.8f,
    )
}

/** Color of block color [index] in the current palette. */
fun blockColor(p: Palette, index: Int): Color = p.pieces[index % p.pieces.size]

/** Home screen picture: a small board with a full row about to clear and a piece waiting below. */
fun DrawScope.blocksThumbnail(p: Palette) {
    drawRect(p.boardDark)
    val n = 5
    val pad = size.width * 0.08f
    val cell = (size.width - pad * 2) / n
    val board = intArrayOf(
        0, 0, 4, 0, 0,
        2, 0, 4, 4, 0,
        1, 1, 1, 1, 1,
        3, 3, 0, 5, 5,
    )
    val top = pad * 0.6f
    for (i in board.indices) {
        val tl = Offset(pad + (i % n) * cell, top + (i / n) * cell)
        drawRoundRect(p.boardLight, tl + Offset(cell * 0.05f, cell * 0.05f), Size(cell * 0.9f, cell * 0.9f), CornerRadius(cell * 0.15f))
        if (i / n == 2) drawRect(p.highlight.copy(alpha = 0.55f), tl, Size(cell, cell))
        if (board[i] != 0) drawBlock(tl, cell, blockColor(p, board[i]))
    }
    // A small corner piece offered below the board.
    val small = cell * 0.62f
    val y = top + 4 * cell + cell * 0.2f
    val x = size.width / 2 - small
    drawBlock(Offset(x, y), small, blockColor(p, 0))
    drawBlock(Offset(x, y + small), small, blockColor(p, 0))
    drawBlock(Offset(x + small, y + small), small, blockColor(p, 0))
}
