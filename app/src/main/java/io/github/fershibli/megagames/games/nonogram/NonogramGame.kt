package io.github.fershibli.megagames.games.nonogram

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.nonogram.Nonogram.CROSSED
import io.github.fershibli.megagames.games.nonogram.Nonogram.EMPTY
import io.github.fershibli.megagames.games.nonogram.Nonogram.FILLED
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.text
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
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class NonogramState(
    val width: Int,
    val height: Int,
    /** Key of the hidden picture ([Nonogram.PATTERN] for a random pattern). */
    val picture: String,
    /** The picture, one letter per square ('.' = empty), see [NonogramPictures]. */
    val colors: String,
    /** The player's marks: [EMPTY], [FILLED] or [CROSSED]. */
    val marks: List<Int>,
    /** Taps mark squares with ✕ instead of painting them. */
    val crossMode: Boolean = false,
    val hintsUsed: Int = 0,
) {
    val solution: List<Boolean> get() = colors.map { it != '.' }
    val solved: Boolean get() = colors.indices.all { (marks[it] == FILLED) == (colors[it] != '.') }
}

fun newNonogram(difficulty: Difficulty, random: Random = Random.Default): NonogramState {
    val picture = Nonogram.generate(difficulty, random)
    return NonogramState(picture.width, picture.height, picture.key, picture.colors, List(picture.width * picture.height) { EMPTY })
}

@Composable
fun NonogramGame(host: GameHost) {
    LoadGame(host, NonogramState.serializer(), { newNonogram(host.difficulty) }) { state ->
        NonogramScreen(state, host)
    }
}

/** Clue squares are a little narrower than the grid squares, to leave more room for the grid. */
private const val CLUE_SHARE = 0.8f

/** A drag in progress: the mark it gives and the marks it replaces, along one line. */
private class Drag(val start: Int, val from: Int, val to: Int) {
    var horizontal: Boolean? = null
}

@Composable
private fun NonogramScreen(state: MutableState<NonogramState>, host: GameHost) {
    val s = state.value
    val history = remember { mutableStateListOf<NonogramState>() }
    val showErrors = host.difficulty != Difficulty.HARD
    val solved = s.solved
    val drag = remember { arrayOfNulls<Drag>(1) }
    val name = stringResource(pictureName(s.picture))
    val detail = stringResource(R.string.nonogram_it_is, name)

    LaunchedEffect(solved) {
        if (solved) {
            // Time to enjoy the picture in color before the end-of-game message.
            delay(1800)
            host.finish(GameOutcome(true, detail))
        }
    }

    fun current() = state.value

    fun update(next: NonogramState, record: Boolean = true) {
        val now = current()
        if (next == now || now.solved) return
        if (record) {
            history += now
            if (history.size > 200) history.removeAt(0)
        }
        state.value = next
    }

    /** What a tap does to a square: paint / clear, or cross / clear in ✕ mode. */
    fun tapped(mark: Int, cross: Boolean) = when {
        cross -> if (mark == CROSSED) EMPTY else CROSSED
        else -> if (mark == FILLED || mark == CROSSED) EMPTY else FILLED
    }

    fun setMark(cell: Int, mark: Int, record: Boolean) {
        val now = current()
        update(now.copy(marks = now.marks.toMutableList().also { it[cell] = mark }), record)
    }

    val callbacks = NonogramInput(
        tap = { cell -> setMark(cell, tapped(current().marks[cell], current().crossMode), true) },
        // A long press does the other action: ✕ while painting, paint while crossing.
        longPress = { cell -> setMark(cell, tapped(current().marks[cell], !current().crossMode), true) },
        dragStart = { cell ->
            val from = current().marks[cell]
            val to = tapped(from, current().crossMode)
            drag[0] = Drag(cell, from, to)
            setMark(cell, to, true)
        },
        dragTo = { cell ->
            val d = drag[0]
            if (d != null && cell != d.start) {
                val now = current()
                val w = now.width
                if (d.horizontal == null) d.horizontal = cell / w == d.start / w
                val line = if (d.horizontal == true) {
                    val r = d.start / w
                    val c = cell % w
                    (minOf(c, d.start % w)..maxOf(c, d.start % w)).map { r * w + it }
                } else {
                    val c = d.start % w
                    val r = cell / w
                    (minOf(r, d.start / w)..maxOf(r, d.start / w)).map { it * w + c }
                }
                val marks = now.marks.toMutableList()
                for (i in line) if (marks[i] == d.from) marks[i] = d.to
                update(now.copy(marks = marks), record = false)
            }
        },
        dragEnd = { drag[0] = null },
    )

    fun hint() {
        val now = current()
        val (cell, paint) = Nonogram.hint(now.width, now.height, now.solution, now.marks) ?: return
        val marks = now.marks.toMutableList().also { it[cell] = if (paint) FILLED else CROSSED }
        update(now.copy(marks = marks, hintsUsed = now.hintsUsed + 1))
    }

    GameLayout(
        status = {
            when {
                solved -> StatusPill(detail, emphasized = true)
                s.crossMode -> StatusPill(stringResource(R.string.nonogram_cross_on))
                else -> StatusPill(stringResource(R.string.nonogram_tap_cell))
            }
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), { history.removeLastOrNull()?.let { state.value = it } }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty() && !solved, compact = true)
                BigButton(stringResource(R.string.hint), ::hint, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = !solved, compact = true)
                BigButton(
                    stringResource(R.string.nonogram_cross), { state.value = s.copy(crossMode = !s.crossMode) },
                    icon = AppIcon.CLOSE, style = if (s.crossMode) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, compact = true,
                )
            }
        },
    ) {
        val rows = remember(s.colors) { Nonogram.rowClues(s.width, s.height, s.solution) }
        val columns = remember(s.colors) { Nonogram.columnClues(s.width, s.height, s.solution) }
        val clueCols = maxOf(1, rows.maxOf { it.size })
        val clueRows = maxOf(1, columns.maxOf { it.size })
        BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
            val across = s.width + clueCols * CLUE_SHARE
            val down = s.height + clueRows * CLUE_SHARE
            val cell = minOf(maxWidth / across, maxHeight / down)
            NonogramBoard(
                s, rows, columns, clueCols, clueRows, showErrors, callbacks,
                Modifier.size(cell * across, cell * down).testTag("nonogram-board"),
            )
        }
    }
}

/** What the player does on the grid; see [NonogramBoard]. */
private class NonogramInput(
    val tap: (Int) -> Unit,
    val longPress: (Int) -> Unit,
    val dragStart: (Int) -> Unit,
    val dragTo: (Int) -> Unit,
    val dragEnd: () -> Unit,
)

/**
 * The clues and the grid. A tap paints (or crosses) a square, a long press does the other
 * action and dragging along a row or a column does the same to every square on the way.
 * A second finger (pinch zoom) cancels the gesture.
 */
@Composable
private fun NonogramBoard(
    s: NonogramState,
    rows: List<IntArray>,
    columns: List<IntArray>,
    clueCols: Int,
    clueRows: Int,
    showErrors: Boolean,
    input: NonogramInput,
    modifier: Modifier,
) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    // The gesture detector lives as long as the board: it must always reach the latest callbacks.
    val actions by rememberUpdatedState(input)
    val width = s.width
    val height = s.height
    Canvas(
        modifier.pointerInput(width, height, clueCols, clueRows) {
            fun cellAt(pos: Offset): Int? {
                val cell = size.width / (width + clueCols * CLUE_SHARE)
                val x = pos.x - clueCols * CLUE_SHARE * cell
                val y = pos.y - clueRows * CLUE_SHARE * cell
                if (x < 0 || y < 0) return null
                val c = (x / cell).toInt()
                val r = (y / cell).toInt()
                return if (c < width && r < height) r * width + c else null
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                val first = cellAt(down.position) ?: return@awaitEachGesture
                val longPressAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                var dragging = false
                while (true) {
                    val event = if (dragging) {
                        awaitPointerEvent()
                    } else {
                        withTimeoutOrNull((longPressAt - SystemClock.uptimeMillis()).coerceAtLeast(1L)) { awaitPointerEvent() }
                    }
                    if (event == null) {
                        actions.longPress(first)
                        // Wait for the finger to lift.
                        do {
                            val e = awaitPointerEvent()
                        } while (e.changes.any { it.pressed })
                        break
                    }
                    if (event.changes.size > 1 || event.changes.any { it.isConsumed }) {
                        if (dragging) actions.dragEnd()
                        break
                    }
                    val change = event.changes.first()
                    if (!change.pressed) {
                        if (dragging) actions.dragEnd() else actions.tap(first)
                        break
                    }
                    val now = cellAt(change.position)
                    if (!dragging && now != null && now != first) {
                        dragging = true
                        actions.dragStart(first)
                    }
                    if (dragging && now != null) actions.dragTo(now)
                    change.consume()
                }
            }
        },
    ) {
        val cell = size.width / (width + clueCols * CLUE_SHARE)
        val clue = cell * CLUE_SHARE
        val left = clueCols * clue
        val top = clueRows * clue
        val solved = s.solved
        drawRect(p.surfaceAlt, Offset.Zero, size)
        drawRect(p.boardLight, Offset(left, top), Size(width * cell, height * cell))
        // Clues: right-aligned beside each row, bottom-aligned above each column; a line whose
        // painted squares already match its clue is dimmed.
        for (r in 0 until height) {
            val line = List(width) { s.marks[r * width + it] == FILLED }
            val done = Nonogram.clues(line).contentEquals(rows[r])
            if (r % 2 == 1) drawRect(p.boardLight.copy(alpha = 0.35f), Offset(0f, top + r * cell), Size(left, cell))
            val clues = rows[r].takeIf { it.isNotEmpty() } ?: intArrayOf(0)
            clues.forEachIndexed { k, n ->
                val x = left - (clues.size - k - 0.5f) * clue
                clueText(measurer, n, Offset(x, top + (r + 0.5f) * cell), clue, done, p)
            }
        }
        for (c in 0 until width) {
            val line = List(height) { s.marks[it * width + c] == FILLED }
            val done = Nonogram.clues(line).contentEquals(columns[c])
            if (c % 2 == 1) drawRect(p.boardLight.copy(alpha = 0.35f), Offset(left + c * cell, 0f), Size(cell, top))
            val clues = columns[c].takeIf { it.isNotEmpty() } ?: intArrayOf(0)
            clues.forEachIndexed { k, n ->
                val y = top - (clues.size - k - 0.5f) * clue
                clueText(measurer, n, Offset(left + (c + 0.5f) * cell, y), clue, done, p)
            }
        }
        // Squares.
        val gap = cell * 0.06f
        for (i in 0 until width * height) {
            val tl = Offset(left + i % width * cell, top + i / width * cell)
            val shouldFill = s.colors[i] != '.'
            when (s.marks[i]) {
                FILLED -> {
                    val color = when {
                        solved -> pictureColor(s.colors[i], p)
                        showErrors && !shouldFill -> p.danger
                        else -> p.text
                    }
                    drawRoundRect(color, tl + Offset(gap, gap), Size(cell - gap * 2, cell - gap * 2), CornerRadius(cell * 0.12f))
                }
                CROSSED -> if (!solved) {
                    val color = if (showErrors && shouldFill) p.danger else p.textMuted
                    val a = cell * 0.28f
                    val w = cell * 0.08f
                    drawLine(color, tl + Offset(a, a), tl + Offset(cell - a, cell - a), w)
                    drawLine(color, tl + Offset(cell - a, a), tl + Offset(a, cell - a), w)
                }
            }
        }
        // Grid lines, thicker every five squares.
        val thin = if (p.strongOutlines) 1.5.dp.toPx() else 1.dp.toPx()
        val thick = if (p.strongOutlines) 3.5.dp.toPx() else 2.5.dp.toPx()
        for (c in 0..width) {
            val major = c % 5 == 0 || c == width
            drawLine(p.boardLine.copy(alpha = if (major) 1f else 0.35f), Offset(left + c * cell, if (major) 0f else top), Offset(left + c * cell, size.height), if (major) thick else thin)
        }
        for (r in 0..height) {
            val major = r % 5 == 0 || r == height
            drawLine(p.boardLine.copy(alpha = if (major) 1f else 0.35f), Offset(if (major) 0f else left, top + r * cell), Offset(size.width, top + r * cell), if (major) thick else thin)
        }
    }
}

private fun DrawScope.clueText(measurer: TextMeasurer, n: Int, center: Offset, clue: Float, done: Boolean, p: Palette) {
    val color = if (done) p.textMuted.copy(alpha = 0.45f) else p.text
    text(measurer, n.toString(), center, color, pxToSp(clue * if (n >= 10) 0.5f else 0.66f), bold = !done)
}

/** Color of a picture letter once the picture is revealed ('k' follows the text color so it shows in dark mode). */
fun pictureColor(letter: Char, p: Palette): Color = when (letter) {
    'r' -> Color(0xFFD64545)
    'o' -> Color(0xFFF08A24)
    'y' -> Color(0xFFF2C230)
    'g' -> Color(0xFF4CAF50)
    'd' -> Color(0xFF2E7D32)
    'b' -> Color(0xFF3B7DD8)
    'c' -> Color(0xFF8CCBEF)
    'p' -> Color(0xFFE26BA8)
    'v' -> Color(0xFF8E5BD6)
    'n' -> Color(0xFF8B5A2B)
    'e' -> Color(0xFF9E9E9E)
    else -> p.text
}

@StringRes
fun pictureName(key: String): Int = when (key) {
        "anchor" -> R.string.nonogram_pic_anchor
        "apple" -> R.string.nonogram_pic_apple
        "bell" -> R.string.nonogram_pic_bell
        "bird" -> R.string.nonogram_pic_bird
        "boat" -> R.string.nonogram_pic_boat
        "bottle" -> R.string.nonogram_pic_bottle
        "butterfly" -> R.string.nonogram_pic_butterfly
        "cactus" -> R.string.nonogram_pic_cactus
        "candle" -> R.string.nonogram_pic_candle
        "car" -> R.string.nonogram_pic_car
        "cat" -> R.string.nonogram_pic_cat
        "cherries" -> R.string.nonogram_pic_cherries
        "crown" -> R.string.nonogram_pic_crown
        "cup" -> R.string.nonogram_pic_cup
        "dog" -> R.string.nonogram_pic_dog
        "duck" -> R.string.nonogram_pic_duck
        "elephant" -> R.string.nonogram_pic_elephant
        "envelope" -> R.string.nonogram_pic_envelope
        "fish" -> R.string.nonogram_pic_fish
        "flower" -> R.string.nonogram_pic_flower
        "hat" -> R.string.nonogram_pic_hat
        "heart" -> R.string.nonogram_pic_heart
        "house" -> R.string.nonogram_pic_house
        "icecream" -> R.string.nonogram_pic_icecream
        "key" -> R.string.nonogram_pic_key
        "leaf" -> R.string.nonogram_pic_leaf
        "lighthouse" -> R.string.nonogram_pic_lighthouse
        "moon" -> R.string.nonogram_pic_moon
        "mushroom" -> R.string.nonogram_pic_mushroom
        "music" -> R.string.nonogram_pic_music
        "owl" -> R.string.nonogram_pic_owl
        "pine" -> R.string.nonogram_pic_pine
        "rabbit" -> R.string.nonogram_pic_rabbit
        "rocket" -> R.string.nonogram_pic_rocket
        "smile" -> R.string.nonogram_pic_smile
        "snail" -> R.string.nonogram_pic_snail
        "snowman" -> R.string.nonogram_pic_snowman
        "star" -> R.string.nonogram_pic_star
        "strawberry" -> R.string.nonogram_pic_strawberry
        "sun" -> R.string.nonogram_pic_sun
        "teapot" -> R.string.nonogram_pic_teapot
        "tree" -> R.string.nonogram_pic_tree
        "tulip" -> R.string.nonogram_pic_tulip
        "turtle" -> R.string.nonogram_pic_turtle
        "umbrella" -> R.string.nonogram_pic_umbrella
        "whale" -> R.string.nonogram_pic_whale
    else -> R.string.nonogram_pic_pattern
}

/** Home screen picture: a small nonogram with its numbers and a heart being painted. */
fun nonogramThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = scope.run {
    val heart = listOf(".r.r.", "rrrrr", "rrrrr", ".rrr.", "..r..")
    val n = 5
    val clue = size.width * 0.2f
    val cell = (size.width - clue) / n
    drawRect(p.surfaceAlt)
    drawRect(p.boardLight, Offset(clue, clue), Size(n * cell, n * cell))
    val rows = listOf("1 1", "5", "5", "3", "1")
    val cols = listOf("2", "4", "4", "4", "2")
    for (k in 0 until n) {
        text(m, rows[k], Offset(clue / 2, clue + (k + 0.5f) * cell), p.text, pxToSp(cell * 0.42f))
        text(m, cols[k], Offset(clue + (k + 0.5f) * cell, clue / 2), p.text, pxToSp(cell * 0.5f))
    }
    for (r in 0 until n) for (c in 0 until n) {
        if (heart[r][c] == '.' || r * n + c > 17) continue
        drawRoundRect(p.pieces[0], Offset(clue + c * cell + cell * 0.07f, clue + r * cell + cell * 0.07f), Size(cell * 0.86f, cell * 0.86f), CornerRadius(cell * 0.12f))
    }
    for (k in 0..n) {
        drawLine(p.boardLine, Offset(clue + k * cell, clue), Offset(clue + k * cell, size.height), cell * 0.04f)
        drawLine(p.boardLine, Offset(clue, clue + k * cell), Offset(size.width, clue + k * cell), cell * 0.04f)
    }
}
