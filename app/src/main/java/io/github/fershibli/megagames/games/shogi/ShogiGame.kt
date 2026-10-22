package io.github.fershibli.megagames.games.shogi

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.games.eastern.BoardGeometry
import io.github.fershibli.megagames.ui.components.BoardSide
import io.github.fershibli.megagames.games.eastern.EasternGame
import io.github.fershibli.megagames.games.eastern.EasternRules
import io.github.fershibli.megagames.games.eastern.EasternTexts
import io.github.fershibli.megagames.games.eastern.GridPosition
import io.github.fershibli.megagames.games.eastern.MarkKind
import io.github.fershibli.megagames.games.eastern.MoveMark
import io.github.fershibli.megagames.games.eastern.boardInk
import io.github.fershibli.megagames.games.eastern.drawWedge
import io.github.fershibli.megagames.games.eastern.drawWoodBoard
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.abs

@Composable
fun ShogiGame(host: GameHost) = EasternGame(host, ShogiRules)

/** Shogi on the shared screen, standard (9 × 9) or minishogi (5 × 5), with the hands below and above. */
object ShogiRules : EasternRules() {
    const val STANDARD = "standard"
    const val MINI = "mini"

    override val key = "shogi"
    override val sides = listOf(BoardSide.BOTTOM, BoardSide.TOP)
    override val cols = 9
    override val rows = 9
    override val points = false
    override val hands = true
    override val strip = 1.05f
    override val texts = EasternTexts(
        pickPiece = R.string.shogi_pick_piece, pickTarget = R.string.shogi_pick_target, check = R.string.shogi_check,
        pieces = R.string.shogi_pieces, guideTitle = R.string.shogi_guide_title, guideMarks = R.string.shogi_guide_marks,
        checkmate = R.string.shogi_checkmate, noMoves = R.string.shogi_checkmate, drawRepetition = R.string.shogi_draw_repetition,
        drawLimit = R.string.shogi_draw_limit, pickDrop = R.string.shogi_pick_drop, promoteTitle = R.string.shogi_promote_title,
        promoteYes = R.string.shogi_promote_yes, promoteNo = R.string.shogi_promote_no,
    )

    private fun mini(variant: String?) = variant == MINI
    override fun cols(variant: String?) = if (mini(variant)) 5 else 9
    override fun rows(variant: String?) = cols(variant)

    /** Characters by piece type; the player's king is 王, the computer's 玉. */
    private const val CHARS = " 歩香桂銀金角飛王と杏圭全馬龍"

    /** Movement marks of each type, read from the engine's own move tables. */
    val MARKS: List<List<MoveMark>> = (0..14).map { t ->
        val list = ArrayList<MoveMark>()
        for (i in 0..7) {
            if (Shogi.SLIDES[t] and (1 shl i) != 0) list += MoveMark(Shogi.DX[i], Shogi.DY[i], MarkKind.SLIDE)
            else if (Shogi.STEPS[t] and (1 shl i) != 0) list += MoveMark(Shogi.DX[i], Shogi.DY[i])
        }
        if (t == Shogi.KNIGHT) { list += MoveMark(-1, -2); list += MoveMark(1, -2) }
        list
    }

    override fun position(variant: String?, moves: List<Int>): GridPosition = Shogi.fromMoves(mini(variant), moves)

    override fun DrawScope.drawBoard(g: BoardGeometry, p: Palette, m: TextMeasurer) {
        drawWoodBoard(g, p)
        val ink = boardInk(p)
        val n = g.cols
        // The player's promotion zone, softly tinted.
        val zone = if (n == 5) 1 else 3
        drawRect(p.highlight.copy(alpha = 0.18f), Offset(g.left, g.top), Size(n * g.cell, zone * g.cell))
        val w = if (p.strongOutlines) g.cell * 0.045f else g.cell * 0.025f
        for (k in 0..n) {
            drawLine(ink, Offset(g.left + k * g.cell, g.top), Offset(g.left + k * g.cell, g.top + n * g.cell), w)
            drawLine(ink, Offset(g.left, g.top + k * g.cell), Offset(g.left + n * g.cell, g.top + k * g.cell), w)
        }
        if (n == 9) for (r in listOf(3, 6)) for (c in listOf(3, 6)) drawCircle(ink, g.cell * 0.07f, Offset(g.left + c * g.cell, g.top + r * g.cell))
    }

    override fun DrawScope.drawPiece(piece: Int, center: Offset, cell: Float, p: Palette, m: TextMeasurer, small: Boolean) {
        val type = abs(piece)
        val char = if (type == Shogi.KING && piece < 0) "玉" else CHARS[type].toString()
        drawWedge(center, cell * 0.98f, char, type > Shogi.KING, MARKS[type], upsideDown = piece < 0, p = p, m = m)
    }

    override fun description(piece: Int) = when (abs(piece)) {
        Shogi.KING -> R.string.shogi_piece_king
        Shogi.ROOK -> R.string.shogi_piece_rook
        Shogi.BISHOP -> R.string.shogi_piece_bishop
        Shogi.GOLD -> R.string.shogi_piece_gold
        Shogi.SILVER -> R.string.shogi_piece_silver
        Shogi.KNIGHT -> R.string.shogi_piece_knight
        Shogi.LANCE -> R.string.shogi_piece_lance
        Shogi.PAWN -> R.string.shogi_piece_pawn
        Shogi.DRAGON -> R.string.shogi_piece_dragon
        Shogi.HORSE -> R.string.shogi_piece_horse
        else -> R.string.shogi_piece_promoted
    }

    override fun guide(variant: String?) = if (mini(variant)) {
        listOf(Shogi.KING, Shogi.ROOK, Shogi.BISHOP, Shogi.GOLD, Shogi.SILVER, Shogi.PAWN, Shogi.DRAGON, Shogi.HORSE, Shogi.TOKIN)
    } else {
        listOf(Shogi.KING, Shogi.ROOK, Shogi.BISHOP, Shogi.GOLD, Shogi.SILVER, Shogi.KNIGHT, Shogi.LANCE, Shogi.PAWN, Shogi.DRAGON, Shogi.HORSE, Shogi.TOKIN)
    }

    override fun handTypes(variant: String?) = if (mini(variant)) {
        listOf(Shogi.ROOK, Shogi.BISHOP, Shogi.GOLD, Shogi.SILVER, Shogi.PAWN)
    } else {
        listOf(Shogi.ROOK, Shogi.BISHOP, Shogi.GOLD, Shogi.SILVER, Shogi.KNIGHT, Shogi.LANCE, Shogi.PAWN)
    }

    override fun handCount(position: GridPosition, side: Int, type: Int) = (position as Shogi).hand(side, type)
}

/** Home screen picture: a few wedges on the squares, one of them pointing back at the player. */
fun DrawScope.shogiThumbnail(p: Palette, m: TextMeasurer) {
    val g = BoardGeometry(size.width * 1.6f, size.height * 1.6f, 9, 9, false, 0f)
    translate(-g.left - g.cell * 2.2f, -g.top - g.cell * 2.2f) {
        with(ShogiRules) {
            drawBoard(g, p, m)
            drawPiece(-Shogi.ROOK, g.center(3 * 9 + 4), g.cell, p, m)
            drawPiece(Shogi.SILVER, g.center(5 * 9 + 3), g.cell, p, m)
            drawPiece(Shogi.DRAGON, g.center(4 * 9 + 5), g.cell, p, m)
            drawPiece(Shogi.PAWN, g.center(6 * 9 + 4), g.cell, p, m)
        }
    }
}
