package io.github.fershibli.megagames.games.janggi

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.ColorMode
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.games.eastern.BoardGeometry
import io.github.fershibli.megagames.ui.components.BoardSide
import io.github.fershibli.megagames.games.eastern.DIAGONAL
import io.github.fershibli.megagames.games.eastern.EasternGame
import io.github.fershibli.megagames.games.eastern.EasternRules
import io.github.fershibli.megagames.games.eastern.EasternTexts
import io.github.fershibli.megagames.games.eastern.GridPosition
import io.github.fershibli.megagames.games.eastern.HORSE_JUMPS
import io.github.fershibli.megagames.games.eastern.MarkKind
import io.github.fershibli.megagames.games.eastern.MoveMark
import io.github.fershibli.megagames.games.eastern.ORTHOGONAL
import io.github.fershibli.megagames.games.eastern.drawDisc
import io.github.fershibli.megagames.games.eastern.drawPalaceBoard
import io.github.fershibli.megagames.games.eastern.marks
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.abs

@Composable
fun JanggiGame(host: GameHost) = EasternGame(host, JanggiRules)

/** Janggi on the shared screen: blue discs (Cho) for the player, red (Han) for the computer. */
object JanggiRules : EasternRules() {
    override val key = "janggi"
    override val sides = listOf(BoardSide.BLUE, BoardSide.RED)
    override fun sideColor(p: Palette, side: Int): Color = if (side == 0) blueInk(p) else redInk(p)
    override val cols = 9
    override val rows = 10
    override val points = true
    override val canPass = true
    override val texts = EasternTexts(
        pickPiece = R.string.janggi_pick_piece, pickTarget = R.string.janggi_pick_target, check = R.string.janggi_check,
        pieces = R.string.janggi_pieces, guideTitle = R.string.janggi_guide_title, guideMarks = R.string.janggi_guide_marks,
        checkmate = R.string.janggi_checkmate, noMoves = R.string.janggi_checkmate, drawRepetition = R.string.janggi_draw_repetition,
        drawLimit = R.string.janggi_draw_limit, drawMaterial = R.string.janggi_draw_material, pass = R.string.janggi_pass,
        aiPassed = R.string.janggi_ai_passed, mustPass = R.string.janggi_must_pass, generalsFacing = R.string.janggi_bikjang,
        bothPassed = R.string.janggi_draw_passes,
    )

    private const val CHO = " 楚士象馬車包卒"
    private const val HAN = " 漢士象馬車包兵"
    /** Traditional janggi pieces come in three sizes: the general biggest, guards and soldiers smallest. */
    private val RADIUS = floatArrayOf(0f, 0.49f, 0.41f, 0.45f, 0.45f, 0.46f, 0.45f, 0.41f)

    private val ELEPHANT_JUMPS = listOf(2 to -3, 3 to -2, 3 to 2, 2 to 3, -2 to 3, -3 to 2, -3 to -2, -2 to -3)

    val MARKS: List<List<MoveMark>> = listOf(
        emptyList(),
        marks(ORTHOGONAL) + marks(DIAGONAL, MarkKind.LATER),
        marks(ORTHOGONAL) + marks(DIAGONAL, MarkKind.LATER),
        marks(ELEPHANT_JUMPS, dots = 2),
        marks(HORSE_JUMPS),
        marks(ORTHOGONAL, MarkKind.SLIDE) + marks(DIAGONAL, MarkKind.LATER),
        marks(ORTHOGONAL, MarkKind.HOP),
        listOf(MoveMark(0, -1), MoveMark(-1, 0), MoveMark(1, 0), MoveMark(-1, -1, MarkKind.LATER), MoveMark(1, -1, MarkKind.LATER)),
    )

    /** Blue ink of the player's (Cho) pieces. */
    private fun blueInk(p: Palette) = if (p.mode == ColorMode.COLORBLIND) Color(0xFF0072B2) else Color(0xFF1C4F9C)
    private fun redInk(p: Palette) = if (p.mode == ColorMode.COLORBLIND) Color(0xFFD55E00) else Color(0xFFB3261E)

    override fun position(variant: String?, moves: List<Int>): GridPosition = Janggi.fromMoves(moves)

    override fun DrawScope.drawBoard(g: BoardGeometry, p: Palette, m: TextMeasurer) {
        drawPalaceBoard(g, p, river = false, m = null)
    }

    override fun DrawScope.drawPiece(piece: Int, center: Offset, cell: Float, p: Palette, m: TextMeasurer, small: Boolean) {
        val type = abs(piece)
        val mine = piece > 0
        drawDisc(
            center, cell * RADIUS[type], (if (mine) CHO else HAN)[type].toString(), if (mine) blueInk(p) else redInk(p),
            MARKS[type], flipMarks = !mine, doubleRing = !mine, p = p, m = m, showMarks = !small,
        )
    }

    override fun description(piece: Int) = when (abs(piece)) {
        Janggi.GENERAL -> R.string.janggi_piece_general
        Janggi.GUARD -> R.string.janggi_piece_guard
        Janggi.ELEPHANT -> R.string.janggi_piece_elephant
        Janggi.HORSE -> R.string.janggi_piece_horse
        Janggi.CHARIOT -> R.string.janggi_piece_chariot
        Janggi.CANNON -> R.string.janggi_piece_cannon
        else -> R.string.janggi_piece_soldier
    }

    override fun guide(variant: String?) = listOf(Janggi.GENERAL, Janggi.GUARD, Janggi.ELEPHANT, Janggi.HORSE, Janggi.CHARIOT, Janggi.CANNON, Janggi.SOLDIER)
}

/** Home screen picture: a palace with its diagonals and a few pieces of both sides. */
fun DrawScope.janggiThumbnail(p: Palette, m: TextMeasurer) {
    val g = BoardGeometry(size.width * 2.1f, size.height * 2.1f, 9, 10, true, 0f)
    translate(-g.left - g.cell * 2.2f, -g.top - g.cell * 5.0f) {
        drawPalaceBoard(g, p, river = false, m = null)
        with(JanggiRules) {
            drawPiece(Janggi.GENERAL, g.center(8 * 9 + 4), g.cell, p, m, small = true)
            drawPiece(Janggi.GUARD, g.center(9 * 9 + 5), g.cell, p, m, small = true)
            drawPiece(-Janggi.CHARIOT, g.center(7 * 9 + 3), g.cell, p, m, small = true)
            drawPiece(Janggi.CANNON, g.center(6 * 9 + 6), g.cell, p, m, small = true)
        }
    }
}
