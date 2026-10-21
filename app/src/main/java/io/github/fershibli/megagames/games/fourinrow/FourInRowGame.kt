package io.github.fershibli.megagames.games.fourinrow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.glossyDisc
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import io.github.fershibli.megagames.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Length of a disc's fall: short, so it reads as movement without slowing anyone down. */
private const val DROP_MS = 260

/** Pause before the computer answers (includes the player's disc falling). */
private const val AI_DELAY_MS = 450L

/** Time to look at the winning line before the result screen. */
private const val END_DELAY_MS = 1_200L

@Serializable
data class FourInRowState(
    /** Columns played so far; the first player starts. */
    val moves: List<Int> = emptyList(),
    /** The side the computer plays; the player has the other one. */
    val computer: Int = FourInRow.SECOND,
)

/** How a game stands after its moves. */
class FourInRowPosition(moves: List<Int>) {
    val board = FourInRow.board(moves)
    val toMove = FourInRow.sideOf(moves.size)
    /** The cell of the last disc, or -1. */
    val last = moves.lastOrNull()?.let { col -> (0 until FourInRow.ROWS).map { it * FourInRow.COLS + col }.firstOrNull { board[it] != FourInRow.EMPTY } } ?: -1
    val line = if (last >= 0) FourInRow.winningLine(board, last) else emptyList()
    val winner = if (line.isNotEmpty()) board[last] else FourInRow.EMPTY
    val full = board.none { it == FourInRow.EMPTY }
    val over get() = winner != FourInRow.EMPTY || full
}

/** Where the holes are inside a canvas of [width]×[height]: left and top of the grid, and the cell side. */
private class FourGeometry(width: Float, height: Float, headroom: Boolean = true) {
    private val above = if (headroom) 1 else 0
    val cell = minOf(width / (FourInRow.COLS + 0.6f), height / (FourInRow.ROWS + above + 0.6f))
    val left = (width - cell * FourInRow.COLS) / 2
    /** One free cell above the frame for the falling disc. */
    val top = (height - cell * (FourInRow.ROWS + above)) / 2 + cell * above
    val frame = cell * 0.3f
    fun center(row: Int, col: Int) = Offset(left + (col + 0.5f) * cell, top + (row + 0.5f) * cell)
    fun columnAt(x: Float): Int = ((x - left) / cell).toInt().takeIf { x >= left && it in 0 until FourInRow.COLS } ?: -1
}

/** Colors of the first and second player's discs: cream and dark, so brightness tells them apart in every palette. */
fun fourDiscColors(p: Palette): Pair<Color, Color> = p.checkerPlayer to p.checkerAi

@Composable
fun FourInRowGame(host: GameHost) {
    LoadGame(host, FourInRowState.serializer(), { FourInRowState() }) { state -> FourInRowScreen(state, host) }
}

@Composable
private fun FourInRowScreen(state: MutableState<FourInRowState>, host: GameHost) {
    val s = state.value
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val pos = remember(s.moves) { FourInRowPosition(s.moves) }
    val human = FourInRow.opponent(s.computer)
    val myTurn = !pos.over && pos.toMove == human
    var hint by remember { mutableIntStateOf(-1) }
    // The newest disc falls into place; a restored game shows its discs at once.
    val drop = remember { Animatable(1f) }
    var shownMoves by remember { mutableIntStateOf(s.moves.size) }
    LaunchedEffect(s.moves.size) {
        if (s.moves.size > shownMoves) {
            drop.snapTo(0f)
            shownMoves = s.moves.size
            drop.animateTo(1f, tween(DROP_MS, easing = FastOutLinearInEasing))
        } else {
            shownMoves = s.moves.size
            drop.snapTo(1f)
        }
    }

    LaunchedEffect(s.moves) {
        when {
            pos.over -> {
                delay(END_DELAY_MS)
                val detail = when (pos.winner) {
                    human -> R.string.fourinrow_win
                    FourInRow.EMPTY -> R.string.fourinrow_draw
                    else -> R.string.fourinrow_lose
                }
                host.finish(GameOutcome(pos.winner == human, res.getString(detail)))
            }
            pos.toMove == s.computer -> {
                delay(AI_DELAY_MS)
                val col = withContext(Dispatchers.Default) { FourInRowAi.bestMove(pos.board, s.computer, host.difficulty, Random.Default) }
                if (col >= 0) state.value = s.copy(moves = s.moves + col)
            }
        }
    }

    fun tap(col: Int) {
        if (!myTurn || FourInRow.landingRow(pos.board, col) < 0) return
        hint = -1
        state.value = s.copy(moves = s.moves + col)
    }

    GameLayout(
        status = {
            val text = when {
                pos.over -> ""
                pos.toMove == s.computer -> stringResource(R.string.computer_turn)
                else -> stringResource(R.string.fourinrow_your_turn)
            }
            if (text.isNotEmpty()) StatusPill(text, emphasized = myTurn)
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), {
                    state.value = s.copy(moves = s.moves.dropLast(2))
                    hint = -1
                }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = myTurn && s.moves.size >= 2)
                BigButton(stringResource(R.string.hint), {
                    scope.launch {
                        val best = withContext(Dispatchers.Default) { FourInRowAi.bestMove(pos.board, human, Difficulty.MEDIUM, Random.Default) }
                        if (state.value.moves == s.moves) hint = best
                    }
                }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = myTurn)
            }
        },
    ) {
        val p = LocalPalette.current
        val scale = LocalSettings.current.pieceScale
        val (firstColor, secondColor) = fourDiscColors(p)
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .testTag("fourinrow-board")
                // The whole height of the board area is the column's touch target.
                .pointerInput(s.moves, myTurn) {
                    detectTapGestures { o -> FourGeometry(size.width.toFloat(), size.height.toFloat()).columnAt(o.x).takeIf { it >= 0 }?.let { tap(it) } }
                },
        ) {
            val g = FourGeometry(size.width, size.height)
            val discR = g.cell * (0.4f * scale).coerceIn(0.33f, 0.44f)
            val holeR = discR + g.cell * 0.03f
            fun discColor(side: Int) = if (side == FourInRow.FIRST) firstColor else secondColor
            // Hint: the column is tinted from top to bottom.
            if (hint >= 0) {
                drawRoundRect(p.hint.copy(alpha = 0.55f), Offset(g.left + hint * g.cell, g.top - g.cell), Size(g.cell, g.cell * (FourInRow.ROWS + 1)), CornerRadius(g.cell * 0.2f))
            }
            // The falling disc is drawn behind the frame, so it slides down inside the column.
            val falling = if (drop.value < 1f && pos.last >= 0) pos.last else -1
            if (falling >= 0) {
                val row = falling / FourInRow.COLS
                val col = falling % FourInRow.COLS
                val target = g.center(row, col)
                val start = g.center(-1, col)
                val at = Offset(target.x, start.y + (target.y - start.y) * drop.value)
                clipRect(0f, 0f, size.width, target.y + discR) {
                    drawDisc(at, discR, discColor(pos.board[falling]), p, pos.board[falling] == FourInRow.SECOND)
                }
            }
            drawFrame(g, holeR, p)
            for (i in pos.board.indices) {
                val side = pos.board[i]
                if (side == FourInRow.EMPTY || i == falling) continue
                drawDisc(g.center(i / FourInRow.COLS, i % FourInRow.COLS), discR, discColor(side), p, side == FourInRow.SECOND)
            }
            if (pos.line.size >= 2 && falling < 0) {
                val a = g.center(pos.line.first() / FourInRow.COLS, pos.line.first() % FourInRow.COLS)
                val b = g.center(pos.line.last() / FourInRow.COLS, pos.line.last() % FourInRow.COLS)
                drawLine(p.highlight.darker(0.3f), a, b, g.cell * 0.24f, StrokeCap.Round)
                drawLine(p.highlight, a, b, g.cell * 0.15f, StrokeCap.Round)
            }
        }
    }
}

/** A disc; the dark one carries a small ring when the palette asks for shapes as well as colors. */
private fun DrawScope.drawDisc(center: Offset, radius: Float, color: Color, p: Palette, marked: Boolean) {
    val rim = when {
        p.strongOutlines -> if (marked) Color.White else Color.Black
        marked -> Color.White.copy(alpha = 0.45f)
        else -> Color.Black.copy(alpha = 0.3f)
    }
    glossyDisc(center, radius, color, rim, shadow = false)
    if (p.symbols && marked) drawCircle(Color.White, radius * 0.3f, center, style = Stroke(radius * 0.12f))
}

/** The wooden frame with its round holes (the holes show the page behind, like a real stand). */
private fun DrawScope.drawFrame(g: FourGeometry, holeR: Float, p: Palette) {
    val w = g.cell * FourInRow.COLS + g.frame * 2
    val h = g.cell * FourInRow.ROWS + g.frame * 2
    val topLeft = Offset(g.left - g.frame, g.top - g.frame)
    val frame = androidx.compose.ui.graphics.Path().apply {
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        addRoundRect(androidx.compose.ui.geometry.RoundRect(topLeft.x, topLeft.y, topLeft.x + w, topLeft.y + h, CornerRadius(g.frame)))
        for (r in 0 until FourInRow.ROWS) for (c in 0 until FourInRow.COLS) {
            addOval(androidx.compose.ui.geometry.Rect(g.center(r, c), holeR))
        }
    }
    drawPath(frame, Brush.linearGradient(listOf(p.wood.lighter(0.18f), p.wood, p.woodDark), topLeft, topLeft + Offset(w, h)))
    // Grain: a few soft lines across the wood.
    val grain = p.woodDark.copy(alpha = 0.18f)
    for (k in 1..5) {
        val y = topLeft.y + h * k / 6f
        drawLine(grain, Offset(topLeft.x + g.frame, y), Offset(topLeft.x + w - g.frame, y + g.cell * 0.05f), g.cell * 0.025f)
    }
    for (r in 0 until FourInRow.ROWS) for (c in 0 until FourInRow.COLS) {
        drawCircle(p.woodDark.darker(0.3f), holeR, g.center(r, c), style = Stroke(g.cell * (if (p.strongOutlines) 0.06f else 0.035f)))
    }
    // Feet of the stand.
    val foot = Size(g.cell * 0.9f, g.frame * 1.2f)
    for (x in listOf(topLeft.x - g.cell * 0.15f, topLeft.x + w - foot.width + g.cell * 0.15f)) {
        drawRoundRect(p.woodDark, Offset(x, topLeft.y + h - foot.height * 0.3f), foot, CornerRadius(foot.height / 2))
    }
}

/** Home screen picture: the wooden stand with a few cream and dark discs. */
fun fourInRowThumbnail(scope: DrawScope, p: Palette) = scope.run {
    drawRect(p.surfaceAlt)
    val g = FourGeometry(size.width, size.height, headroom = false)
    val (first, second) = fourDiscColors(p)
    val discs = mapOf(38 to 1, 39 to 2, 40 to 1, 32 to 2, 31 to 1, 33 to 1, 25 to 2, 24 to 1, 17 to 1, 37 to 2, 26 to 2)
    val r = g.cell * 0.4f
    drawFrame(g, r + g.cell * 0.03f, p)
    for ((cell, side) in discs) drawDisc(g.center(cell / FourInRow.COLS, cell % FourInRow.COLS), r, if (side == 1) first else second, p, side == 2)
}
