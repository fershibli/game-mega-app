package io.github.fershibli.megagames.games.blocks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.core.rememberWorldStore
import io.github.fershibli.megagames.games.arcade.KeepBest
import io.github.fershibli.megagames.games.arcade.Records
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

/** Height of the tray of offered pieces, as a fraction of the board side. */
private const val TRAY = 0.34f

@Composable
fun BlocksGame(host: GameHost) {
    val store = rememberWorldStore()
    LoadGame(host, BlocksState.serializer(), {
        Blocks.newGame(host.difficulty, Random.nextLong(), Records.best(store, host.game, host.difficulty))
    }) { state -> BlocksScreen(state, host, store) }
}

@Composable
private fun BlocksScreen(state: MutableState<BlocksState>, host: GameHost, store: WorldStore) {
    val s = state.value
    val p = LocalPalette.current
    // Piece picked by a tap (-1 = none), waiting for a tap on the board.
    var selected by remember { mutableIntStateOf(-1) }
    var hint by remember { mutableStateOf<Placement?>(null) }
    var undo by remember { mutableStateOf<BlocksState?>(null) }
    // Piece being dragged (-1 = none), where the finger is, and where the piece would land.
    var dragSlot by remember { mutableIntStateOf(-1) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var preview by remember { mutableStateOf<Placement?>(null) }
    var previewLines by remember { mutableStateOf<List<Int>>(emptyList()) }
    // A board cell tapped where the chosen piece does not fit, circled for a moment.
    var miss by remember { mutableIntStateOf(-1) }

    fun commit(next: BlocksState) {
        undo = state.value
        state.value = next
        selected = -1
        hint = null
    }

    val best = maxOf(s.score, s.recordToBeat)
    val newBest = s.score > s.recordToBeat && s.score > 0
    val detail = listOfNotNull(if (newBest) stringResource(R.string.blocks_new_best) else null, stringResource(R.string.blocks_final, s.score, best)).joinToString("\n")
    KeepBest(store, host.game, host.difficulty, s.score, s.recordToBeat)
    LaunchedEffect(s.over) {
        if (s.over) {
            delay(1500)
            host.finish(GameOutcome(newBest, detail))
        }
    }
    LaunchedEffect(miss) { if (miss >= 0) { delay(700); miss = -1 } }

    GameLayout(
        status = {
            StatusPill(if (s.over) stringResource(R.string.blocks_no_fit) else stringResource(R.string.blocks_score, s.score, best), emphasized = s.over)
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), {
                    undo?.let { state.value = it }
                    undo = null
                    selected = -1
                    hint = null
                }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = undo != null && !s.over)
                BigButton(stringResource(R.string.hint), {
                    hint = Blocks.bestMove(state.value)
                    selected = hint?.slot ?: -1
                }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = !s.over)
                if (s.canRotate) {
                    BigButton(stringResource(R.string.blocks_rotate), {
                        state.value = Blocks.rotate(state.value, selected)
                        hint = null
                    }, icon = AppIcon.ROTATE, style = ButtonStyle.SECONDARY, enabled = selected >= 0 && !s.over)
                }
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            val gap = 16.dp
            val side = minOf(maxWidth, (maxHeight - gap) / (1 + TRAY))
            Canvas(
                Modifier
                    .size(side, side * (1 + TRAY) + gap)
                    .testTag("blocks-board")
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val cur = state.value
                            if (cur.over) return@awaitEachGesture
                            val boardSide = size.width.toFloat()
                            val n = cur.size
                            val cell = boardSide / n
                            val trayTop = size.height - boardSide * TRAY
                            if (down.position.y >= trayTop - cell * 0.4f) {
                                // A piece of the tray: drag it to the board, or tap it to choose it.
                                val slot = (down.position.x / (boardSide / 3)).toInt().coerceIn(0, 2)
                                val piece = cur.tray[slot] ?: return@awaitEachGesture
                                // The piece floats above the finger so the finger never hides it.
                                val lift = 56.dp.toPx()
                                val w = Blocks.width(piece)
                                val h = Blocks.height(piece)
                                var moved = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                        moved = true
                                        dragSlot = slot
                                        selected = slot
                                        hint = null
                                    }
                                    if (moved) {
                                        finger = change.position
                                        val col = ((change.position.x - w * cell / 2) / cell).roundToInt()
                                        val row = ((change.position.y - lift - h * cell) / cell).roundToInt()
                                        val target = if (Blocks.fits(cur.board, n, piece, row, col)) Placement(slot, row, col) else null
                                        if (target != preview) {
                                            preview = target
                                            previewLines = if (target == null) emptyList() else Blocks.linesCompleted(cur.board, n, piece, row, col)
                                        }
                                        change.consume()
                                    }
                                }
                                val target = preview
                                if (moved) {
                                    dragSlot = -1
                                    preview = null
                                    previewLines = emptyList()
                                    if (target != null) Blocks.place(state.value, target.slot, target.row, target.col)?.let { commit(it) }
                                } else {
                                    selected = if (selected == slot) -1 else slot
                                    hint = null
                                }
                            } else {
                                // A tap on the board places the chosen piece around that cell.
                                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                                val row = (up.position.y / cell).toInt()
                                val col = (up.position.x / cell).toInt()
                                if (row !in 0 until n || col !in 0 until n || selected < 0) return@awaitEachGesture
                                val h = hint
                                val target = if (h != null && h.slot == selected && covers(cur, h, row, col)) h else Blocks.placementAround(cur, selected, row, col)
                                val next = target?.let { Blocks.place(cur, it.slot, it.row, it.col) }
                                if (next != null) commit(next) else miss = row * n + col
                            }
                        }
                    },
            ) {
                val n = s.size
                val cell = size.width / n
                drawBoard(s, p, cell, previewLines)
                // Where the piece would go: the hint, or the dragged piece's landing place.
                val ghost = preview ?: hint?.takeIf { dragSlot < 0 }
                ghost?.let { g ->
                    val piece = s.tray[g.slot] ?: return@let
                    val a = Blocks.cells(piece)
                    val color = if (preview != null) blockColor(p, piece.color) else p.hint
                    for (i in a.indices step 2) {
                        val tl = Offset((g.col + a[i + 1]) * cell, (g.row + a[i]) * cell)
                        drawBlock(tl, cell, color, alpha = 0.5f)
                        drawRoundRect(p.text, tl + Offset(cell * 0.06f, cell * 0.06f), Size(cell * 0.88f, cell * 0.88f), CornerRadius(cell * 0.2f), style = Stroke(cell * 0.05f), alpha = 0.6f)
                    }
                }
                if (miss >= 0) {
                    drawCircle(p.danger, cell * 0.42f, Offset((miss % n + 0.5f) * cell, (miss / n + 0.5f) * cell), style = Stroke(cell * 0.1f))
                }
                drawTray(s, p, selected, dragSlot)
                if (dragSlot >= 0) {
                    // The dragged piece at board size, floating above the finger.
                    s.tray[dragSlot]?.let { piece ->
                        val a = Blocks.cells(piece)
                        val left = finger.x - Blocks.width(piece) * cell / 2
                        val top = finger.y - 56.dp.toPx() - Blocks.height(piece) * cell
                        for (i in a.indices step 2) drawBlock(Offset(left + a[i + 1] * cell, top + a[i] * cell), cell, blockColor(p, piece.color), alpha = 0.9f)
                    }
                }
            }
        }
    }
}

/** True when [placement] covers the board cell [row], [col]. */
private fun covers(s: BlocksState, placement: Placement, row: Int, col: Int): Boolean {
    val piece = s.tray[placement.slot] ?: return false
    val a = Blocks.cells(piece)
    for (i in a.indices step 2) if (placement.row + a[i] == row && placement.col + a[i + 1] == col) return true
    return false
}

private fun DrawScope.drawBoard(s: BlocksState, p: Palette, cell: Float, lines: List<Int>) {
    val n = s.size
    val boardSize = Size(cell * n, cell * n)
    drawRoundRect(p.boardDark, Offset.Zero, boardSize, CornerRadius(cell * 0.25f))
    for (i in 0 until n * n) {
        val tl = Offset((i % n) * cell, (i / n) * cell)
        val v = s.board[i]
        if (v == 0) {
            drawRoundRect(p.boardLight, tl + Offset(cell * 0.06f, cell * 0.06f), Size(cell * 0.88f, cell * 0.88f), CornerRadius(cell * 0.18f), alpha = 0.85f)
        } else {
            drawBlock(tl, cell, blockColor(p, v - 1))
        }
    }
    // Rows and columns the dragged piece would complete glow, so the player sees what will clear.
    for (line in lines) {
        if (line < n) drawRect(p.highlight, Offset(0f, line * cell), Size(cell * n, cell), alpha = 0.55f)
        else drawRect(p.highlight, Offset((line - n) * cell, 0f), Size(cell, cell * n), alpha = 0.55f)
    }
    drawRoundRect(p.boardLine, Offset.Zero, boardSize, CornerRadius(cell * 0.25f), style = Stroke(if (p.strongOutlines) cell * 0.08f else cell * 0.04f), alpha = 0.6f)
}

/** The three offered pieces, each centered in its own big slot below the board. */
private fun DrawScope.drawTray(s: BlocksState, p: Palette, selected: Int, dragging: Int) {
    val boardSide = size.width
    val trayTop = size.height - boardSide * TRAY
    val slotW = boardSide / 3
    val slotH = boardSide * TRAY
    for (slot in 0 until 3) {
        val tl = Offset(slot * slotW + slotW * 0.04f, trayTop)
        val box = Size(slotW * 0.92f, slotH)
        drawRoundRect(p.surfaceAlt, tl, box, CornerRadius(slotW * 0.1f))
        if (slot == selected) drawRoundRect(p.primary, tl, box, CornerRadius(slotW * 0.1f), style = Stroke(slotW * 0.04f))
        val piece = s.tray[slot] ?: continue
        val w = Blocks.width(piece)
        val h = Blocks.height(piece)
        val cell = minOf(box.width / 5.2f, box.height / 5.2f) * (if (maxOf(w, h) <= 3) 1.3f else 1f)
        val left = tl.x + (box.width - w * cell) / 2
        val top = tl.y + (box.height - h * cell) / 2
        val a = Blocks.cells(piece)
        val alpha = if (slot == dragging) 0.25f else 1f
        for (i in a.indices step 2) drawBlock(Offset(left + a[i + 1] * cell, top + a[i] * cell), cell, blockColor(p, piece.color), alpha)
    }
}
