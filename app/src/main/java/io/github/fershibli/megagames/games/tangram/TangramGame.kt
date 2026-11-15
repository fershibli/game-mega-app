package io.github.fershibli.megagames.games.tangram

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.art.darker
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
import io.github.fershibli.megagames.ui.theme.currentMotion
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.spec
import io.github.fershibli.megagames.ui.theme.wait

/** Pause on the finished figure, with its name, before the end of the game. */
private const val DONE_PAUSE_MS = 2000L

/** How the table is laid on the screen: [scale] pixels per unit, then shifted by [left]/[top]. */
internal class TableView(width: Float, height: Float, val table: Tangram.Table) {
    val scale = minOf(width / table.width, height / table.height).toFloat()
    val left = (width - table.width.toFloat() * scale) / 2
    val top = (height - table.height.toFloat() * scale) / 2
    fun toScreen(p: Pt) = Offset(left + p.x.toFloat() * scale, top + p.y.toFloat() * scale)
    fun toTable(o: Offset) = Pt(((o.x - left) / scale).toDouble(), ((o.y - top) / scale).toDouble())
}

@Composable
fun TangramGame(host: GameHost) {
    LoadGame(host, TangramState.serializer(), { Tangram.newGame(host.difficulty) }) { state -> TangramScreen(state, host) }
}

@Composable
private fun TangramScreen(state: MutableState<TangramState>, host: GameHost) {
    val s = state.value
    val p = LocalPalette.current
    val pace = currentPace
    val motion = currentMotion
    val margin = 0.35 * LocalSettings.current.pieceScale
    val resources = LocalResources.current
    val shape = Tangram.SHAPES[s.shape]
    val shapeName = stringResource(shape.name)
    val solved = remember(s.pieces) { Tangram.solved(s) }
    // The piece under the finger and how far it has been dragged (in table units), drawn on top
    // and only saved when it is let go.
    var dragging by remember { mutableStateOf(-1) }
    var dragBy by remember { mutableStateOf(Pt(0.0, 0.0)) }
    // The finished figure glows for a moment.
    val glow = remember { Animatable(if (solved) 1f else 0f) }

    LaunchedEffect(solved) {
        if (!solved) return@LaunchedEffect
        glow.animateTo(1f, motion.spec(900))
        pace.wait(DONE_PAUSE_MS)
        val detail = buildString {
            append(resources.getString(R.string.tangram_done, shapeName))
            if (s.hints > 0) append(' ').append(resources.getString(R.string.tangram_hints_used, s.hints))
        }
        host.finish(GameOutcome(true, detail))
    }

    GameLayout(
        status = {
            val msg = if (solved) stringResource(R.string.tangram_done, shapeName) else stringResource(R.string.tangram_shape, shapeName) + "\n" + stringResource(R.string.tangram_how)
            StatusPill(msg, emphasized = solved)
        },
        controls = {
            val playing = !solved && !host.finished
            ActionBar {
                BigButton(stringResource(R.string.hint), { state.value = Tangram.hint(state.value) }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = playing, compact = true)
                BigButton(stringResource(R.string.tangram_flip), { state.value = Tangram.flip(state.value) }, icon = AppIcon.ROTATE, style = ButtonStyle.SECONDARY, enabled = playing, compact = true)
                BigButton(stringResource(R.string.tangram_restart), { state.value = Tangram.restart(state.value) }, icon = AppIcon.NEW_GAME, style = ButtonStyle.SECONDARY, enabled = playing, compact = true)
            }
        },
    ) {
        val description = stringResource(R.string.tangram_board, shapeName)
        val paths = remember { List(14) { Path() } }
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(6.dp)
                .semantics { contentDescription = description }
                .testTag("tangram-board")
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val cur = state.value
                        if (host.finished || Tangram.solved(cur)) return@awaitEachGesture
                        val view = TableView(size.width.toFloat(), size.height.toFloat(), Tangram.table(cur.shape))
                        val hit = Tangram.pieceAt(cur, view.toTable(down.position), margin)
                        if (hit < 0) return@awaitEachGesture
                        val (lifted, i) = Tangram.lift(cur, hit)
                        state.value = lifted
                        dragging = i
                        dragBy = Pt(0.0, 0.0)
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) { // a zoom gesture took over
                                dragging = -1
                                return@awaitEachGesture
                            }
                            if (!change.pressed) break
                            if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (moved) {
                                val d = change.position - down.position
                                dragBy = Pt((d.x / view.scale).toDouble(), (d.y / view.scale).toDouble())
                                change.consume()
                            }
                        }
                        val now = state.value
                        state.value = if (moved) Tangram.snap(Tangram.move(now, i, dragBy.x, dragBy.y), i) else Tangram.rotate(now, i)
                        dragging = -1
                    }
                },
        ) {
            val view = TableView(size.width, size.height, Tangram.table(s.shape))
            drawTable(p, view, s, dragging, dragBy, glow.value, paths)
        }
    }
}

/** Fills [path] with the outline [corners] on the screen. */
private fun Path.outline(view: TableView, corners: List<Pt>, shift: Pt = Pt(0.0, 0.0)) {
    reset()
    corners.forEachIndexed { k, c ->
        val o = view.toScreen(c + shift)
        if (k == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
    }
    close()
}

/** Each kind of piece has its own color (the two large and the two small triangles match). */
private fun pieceColor(p: Palette, kind: Int): Color = p.pieces[KIND_COLORS[kind] % p.pieces.size]

private val KIND_COLORS = intArrayOf(0, 1, 3, 2, 4)

/**
 * Draws the table: the tray, the silhouette (with the inner lines of [TangramState.lines]) and
 * the seven pieces; the one being dragged follows the finger with a soft shadow.
 */
private fun DrawScope.drawTable(p: Palette, view: TableView, s: TangramState, dragging: Int, dragBy: Pt, glow: Float, paths: List<Path>) {
    val t = view.table
    val u = view.scale
    drawRoundRect(p.boardLight, Offset(view.left, view.top), Size(t.width.toFloat() * u, t.height.toFloat() * u), CornerRadius(u * 0.5f))
    drawRoundRect(p.boardDark, Offset(view.left + u * 0.2f, view.top + (t.trayTop - 0.5).toFloat() * u), Size((t.width - 0.4).toFloat() * u, (t.trayHeight + 0.7).toFloat() * u), CornerRadius(u * 0.4f))
    // The silhouette: every solution piece filled in one color, with a thin edge of the same
    // color so no seam shows between them.
    val shadow = lerp(lerp(p.boardLight, p.boardLine, 0.62f), p.success, glow * 0.6f)
    t.targetCorners.forEachIndexed { k, corners ->
        val path = paths[7 + k]
        path.outline(view, corners)
        drawPath(path, shadow)
        drawPath(path, shadow, style = Stroke(u * 0.04f, join = StrokeJoin.Round))
    }
    val line = p.boardLight.copy(alpha = 0.55f)
    for (k in s.lines) drawPath(paths[7 + k], line, style = Stroke(u * 0.05f, join = StrokeJoin.Round))
    // The pieces, the dragged one last.
    val order = s.pieces.indices.filter { it != dragging } + listOfNotNull(dragging.takeIf { it >= 0 })
    for (i in order) {
        val piece = s.pieces[i]
        val shift = if (i == dragging) dragBy else Pt(0.0, 0.0)
        val path = paths[i]
        path.outline(view, piece.corners(), shift)
        val color = pieceColor(p, piece.kind)
        if (i == dragging) {
            translateShadow(path, u)
        }
        drawPath(path, color)
        drawPath(path, color.darker(if (p.strongOutlines) 0.7f else 0.35f), style = Stroke(u * if (p.strongOutlines) 0.08f else 0.05f, join = StrokeJoin.Round))
        // A light edge on top for a cut-wood look.
        drawPath(path, color.lighter(0.35f).copy(alpha = 0.5f), style = Stroke(u * 0.02f, join = StrokeJoin.Round))
    }
}

private fun DrawScope.translateShadow(path: Path, u: Float) {
    drawContext.transform.translate(u * 0.08f, u * 0.14f)
    drawPath(path, Color.Black.copy(alpha = 0.25f))
    drawContext.transform.translate(-u * 0.08f, -u * 0.14f)
}

/** Home-screen picture: the sitting cat made of the seven colored pieces. */
fun tangramThumbnail(scope: DrawScope, p: Palette) = scope.drawCatThumbnail(p)

private fun DrawScope.drawCatThumbnail(p: Palette) {
    val cat = Tangram.SHAPES.indexOfFirst { it.key == "cat" }
    val shape = Tangram.SHAPES[cat]
    val scale = minOf(size.width / shape.width.toFloat(), size.height / shape.height.toFloat()) * 0.9f
    val left = (size.width - shape.width.toFloat() * scale) / 2
    val top = (size.height - shape.height.toFloat() * scale) / 2
    val path = Path()
    shape.solution.forEach { piece ->
        path.reset()
        piece.corners().forEachIndexed { j, c ->
            val x = left + c.x.toFloat() * scale
            val y = top + c.y.toFloat() * scale
            if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val color = pieceColor(p, piece.kind)
        drawPath(path, color)
        drawPath(path, color.darker(0.35f), style = Stroke(scale * 0.06f, join = StrokeJoin.Round))
    }
}
