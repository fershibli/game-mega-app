package io.github.fershibli.megagames.games.xiangqi

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
fun XiangqiGame(host: GameHost) = EasternGame(host, XiangqiRules)

/** Red ink for the player's discs (vermilion in the color-blind palette, which keeps it apart from black). */
internal fun redInk(p: Palette) = if (p.mode == ColorMode.COLORBLIND) Color(0xFFD55E00) else Color(0xFFB3261E)

/** Xiangqi on the shared screen: red discs for the player, black for the computer. */
object XiangqiRules : EasternRules() {
    override val key = "xiangqi"
    override val cols = 9
    override val rows = 10
    override val points = true
    override val texts = EasternTexts(
        pickPiece = R.string.xiangqi_pick_piece, pickTarget = R.string.xiangqi_pick_target, check = R.string.xiangqi_check,
        pieces = R.string.xiangqi_pieces, guideTitle = R.string.xiangqi_guide_title, guideMarks = R.string.xiangqi_guide_marks,
        checkmate = R.string.xiangqi_checkmate, noMoves = R.string.xiangqi_no_moves, drawRepetition = R.string.xiangqi_draw_repetition,
        drawLimit = R.string.xiangqi_draw_limit, drawMaterial = R.string.xiangqi_draw_material,
    )

    private const val RED = " 帥仕相馬車炮兵"
    private const val BLACK = " 將士象馬車砲卒"

    /** Movement marks per piece type (see [MoveMark]). */
    val MARKS: List<List<MoveMark>> = listOf(
        emptyList(),
        marks(ORTHOGONAL),
        marks(DIAGONAL),
        marks(DIAGONAL, dots = 2),
        marks(HORSE_JUMPS),
        marks(ORTHOGONAL, MarkKind.SLIDE),
        marks(ORTHOGONAL, MarkKind.HOP),
        listOf(MoveMark(0, -1), MoveMark(-1, 0, MarkKind.LATER), MoveMark(1, 0, MarkKind.LATER)),
    )

    override fun position(variant: String?, moves: List<Int>): GridPosition = Xiangqi.fromMoves(moves)

    override fun DrawScope.drawBoard(g: BoardGeometry, p: Palette, m: TextMeasurer) {
        drawPalaceBoard(g, p, river = true, m = m)
    }

    override fun DrawScope.drawPiece(piece: Int, center: Offset, cell: Float, p: Palette, m: TextMeasurer, small: Boolean) {
        val type = abs(piece)
        val mine = piece > 0
        drawDisc(
            center, cell * 0.47f, (if (mine) RED else BLACK)[type].toString(), if (mine) redInk(p) else Color(0xFF1B1B1B),
            MARKS[type], flipMarks = !mine, doubleRing = !mine, p = p, m = m, showMarks = !small,
        )
    }

    override fun description(piece: Int) = when (abs(piece)) {
        Xiangqi.GENERAL -> R.string.xiangqi_piece_general
        Xiangqi.ADVISOR -> R.string.xiangqi_piece_advisor
        Xiangqi.ELEPHANT -> R.string.xiangqi_piece_elephant
        Xiangqi.HORSE -> R.string.xiangqi_piece_horse
        Xiangqi.CHARIOT -> R.string.xiangqi_piece_chariot
        Xiangqi.CANNON -> R.string.xiangqi_piece_cannon
        else -> R.string.xiangqi_piece_soldier
    }

    override fun guide(variant: String?) = listOf(Xiangqi.GENERAL, Xiangqi.ADVISOR, Xiangqi.ELEPHANT, Xiangqi.HORSE, Xiangqi.CHARIOT, Xiangqi.CANNON, Xiangqi.SOLDIER)
}

/** Home screen picture: a corner of the board by the river with a few discs. */
fun DrawScope.xiangqiThumbnail(p: Palette, m: TextMeasurer) {
    val g = BoardGeometry(size.width * 2.1f, size.height * 2.1f, 9, 10, true, 0f)
    translate(-g.left - g.cell * 3.7f, -g.top - g.cell * 3.7f) {
        drawPalaceBoard(g, p, river = true, m = null)
        with(XiangqiRules) {
            drawPiece(Xiangqi.CHARIOT, g.center(6 * 9 + 5), g.cell, p, m, small = true)
            drawPiece(-Xiangqi.CANNON, g.center(4 * 9 + 6), g.cell, p, m, small = true)
            drawPiece(Xiangqi.HORSE, g.center(7 * 9 + 7), g.cell, p, m, small = true)
            drawPiece(-Xiangqi.SOLDIER, g.center(5 * 9 + 4), g.cell, p, m, small = true)
        }
    }
}
