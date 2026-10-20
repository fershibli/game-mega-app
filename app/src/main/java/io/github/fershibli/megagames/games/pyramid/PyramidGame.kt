package io.github.fershibli.megagames.games.pyramid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.cards.CARD_FLASH_MS
import io.github.fershibli.megagames.games.cards.CARD_MESSAGE_MS
import io.github.fershibli.megagames.games.cards.CARD_REFUSED_MS
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.NoMovesDialog
import io.github.fershibli.megagames.ui.art.drawCard
import io.github.fershibli.megagames.ui.art.drawCardBack
import io.github.fershibli.megagames.ui.art.drawCardSlot
import io.github.fershibli.megagames.ui.art.pxToSp
import io.github.fershibli.megagames.ui.art.text
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalGameLanguage
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Composable
fun PyramidGame(host: GameHost) {
    LoadGame(host, PyramidState.serializer(), { Pyramid.deal(host.difficulty, Random.Default) }) { state ->
        PyramidScreen(state, host)
    }
}

/** What the player touched on the table. */
internal sealed interface PyramidHit {
    data object Stock : PyramidHit
    data class Pick(val pick: PyPick) : PyramidHit
}

/** Geometry of the table, shared by drawing, touch handling and tests so they always agree. */
internal class PyramidLayout(val width: Float, val height: Float, val leftHanded: Boolean, scale: Float) {
    val gap = width * 0.015f

    /** How far each row comes down over the one above (more with bigger pieces, [scale]). */
    private val rowStep = 0.48f + 0.14f * ((scale - 1f) / 0.6f).coerceIn(0f, 1f)
    private val label = width * 0.07f
    val cardW = minOf(
        (width - gap * (Pyramid.ROWS + 1)) / Pyramid.ROWS,
        (height - gap * 9 - label) / (1.42f * (2 + (Pyramid.ROWS - 1) * rowStep)),
    )
    val cardH = cardW * 1.42f
    val top = gap * 2

    fun rect(index: Int): Rect {
        val r = Pyramid.row(index)
        val rowWidth = (r + 1) * cardW + r * gap
        val x = (width - rowWidth) / 2 + Pyramid.col(index) * (cardW + gap)
        return Rect(Offset(x, top + r * rowStep * cardH), Size(cardW, cardH))
    }

    private val deckTop = top + cardH * (1 + (Pyramid.ROWS - 1) * rowStep) + gap * 5

    /** The stock on the side of the thumb, the turned card next to it. */
    val stockRect = Rect(Offset(if (leftHanded) gap * 2 else width - gap * 2 - cardW, deckTop), Size(cardW, cardH))
    val wasteRect = Rect(Offset(if (leftHanded) gap * 2 + cardW + gap * 3 else width - gap * 5 - cardW * 2, deckTop), Size(cardW, cardH))
    val labelCenter = Offset(stockRect.center.x, stockRect.bottom + label * 0.6f)

    fun hit(p: Offset, s: PyramidState): PyramidHit? {
        if (stockRect.contains(p)) return PyramidHit.Stock
        if (wasteRect.contains(p) && s.waste.isNotEmpty()) return PyramidHit.Pick(PyPick.Waste)
        // Lower rows lie on top of the upper ones: look from the bottom row up.
        for (i in Pyramid.SIZE - 1 downTo 0) if (s.pyramid[i] != null && rect(i).contains(p)) return PyramidHit.Pick(PyPick.Pyramid(i))
        return null
    }
}

@Composable
private fun PyramidScreen(state: MutableState<PyramidState>, host: GameHost) {
    val s = state.value
    val history = remember { mutableStateListOf<PyramidState>() }
    var selected by remember { mutableStateOf<PyPick?>(null) }
    var refused by remember { mutableStateOf<Card?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<Pyramid.Hint?>(null) }
    var showStuck by remember { mutableStateOf(false) }
    var stuckDismissed by remember { mutableStateOf(false) }
    val leftHanded = LocalSettings.current.leftHanded
    val scale = LocalSettings.current.pieceScale
    val french = LocalGameLanguage.current == AppLanguage.FRENCH
    val context = LocalResources.current
    val scope = rememberCoroutineScope()
    val stuck = remember(s) { Pyramid.isStuck(s) }

    fun commit(next: PyramidState) {
        history += s
        if (history.size > 300) history.removeAt(0)
        state.value = next
        selected = null
        hint = null
        message = null
        if (next.won) host.finish(GameOutcome(true, null))
    }

    fun undo() {
        history.removeLastOrNull()?.let { state.value = it }
        selected = null
        hint = null
        message = null
        showStuck = false
    }

    // A game saved right at the winning move still ends properly when it is restored.
    LaunchedEffect(Unit) { if (state.value.won) host.finish(GameOutcome(true, null)) }
    LaunchedEffect(refused) { if (refused != null) { delay(CARD_REFUSED_MS); refused = null } }
    LaunchedEffect(message) { if (message != null) { delay(CARD_MESSAGE_MS); message = null } }
    LaunchedEffect(stuck) {
        if (!stuck) stuckDismissed = false
        else if (!stuckDismissed) { delay(CARD_FLASH_MS); showStuck = true }
    }

    fun onTap(hit: PyramidHit?) {
        when (hit) {
            PyramidHit.Stock -> Pyramid.tapStock(s)?.let { commit(it) }
            is PyramidHit.Pick -> {
                val card = Pyramid.cardAt(s, hit.pick)
                if (card == null) {
                    // A covered card of the pyramid.
                    refused = (hit.pick as? PyPick.Pyramid)?.let { s.pyramid[it.index] }
                    message = context.getString(R.string.pyramid_covered)
                    return
                }
                val first = selected?.let { Pyramid.cardAt(s, it) }
                when {
                    card.rank == 13 -> commit(Pyramid.remove(s, hit.pick)!!)
                    selected == hit.pick -> selected = null
                    first != null && first.rank + card.rank == 13 -> commit(Pyramid.remove(s, selected!!, hit.pick)!!)
                    first != null -> {
                        refused = card
                        message = context.getString(R.string.pyramid_not_13, first.rank, card.rank, first.rank + card.rank)
                        selected = null
                    }
                    else -> {
                        selected = hit.pick
                        hint = null
                        message = context.getString(R.string.pyramid_needs, card.rank, 13 - card.rank)
                    }
                }
            }
            null -> selected = null
        }
    }

    if (showStuck) {
        NoMovesDialog(
            title = stringResource(R.string.pyramid_stuck_title),
            message = stringResource(R.string.pyramid_stuck_message),
            giveUp = stringResource(R.string.pyramid_give_up),
            keepLooking = stringResource(R.string.pyramid_keep_playing),
            canUndo = history.isNotEmpty(),
            onUndo = { showStuck = false; undo() },
            onGiveUp = { showStuck = false; host.finish(GameOutcome(false, context.getString(R.string.pyramid_lost))) },
            onKeepLooking = { showStuck = false; stuckDismissed = true },
        )
    }

    GameLayout(
        status = {
            val lines = mutableListOf<String>()
            lines += if (s.passesLeft >= 0) {
                stringResource(R.string.pyramid_left, s.cardsLeft) + "  ·  " + stringResource(R.string.pyramid_passes, s.passesLeft)
            } else {
                stringResource(R.string.pyramid_left, s.cardsLeft)
            }
            lines += when {
                message != null -> message!!
                hint == Pyramid.Hint.Stock -> stringResource(R.string.pyramid_hint_deck)
                else -> stringResource(R.string.pyramid_values)
            }
            StatusPill(lines.joinToString("\n"))
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), { undo() }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty())
                if (stuck) {
                    BigButton(stringResource(R.string.pyramid_give_up), { showStuck = true }, icon = AppIcon.CLOSE, style = ButtonStyle.SECONDARY)
                } else {
                    BigButton(stringResource(R.string.hint), {
                        val at = s
                        scope.launch {
                            val h = withContext(Dispatchers.Default) { Pyramid.hint(at) }
                            if (state.value === at) { hint = h; selected = null; message = null }
                        }
                    }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY)
                }
            }
        },
    ) {
        val p = LocalPalette.current
        val measurer = rememberTextMeasurer()
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("pyramid-table")
                .pointerInput(s, leftHanded, scale) {
                    detectTapGestures { pos -> onTap(PyramidLayout(size.width.toFloat(), size.height.toFloat(), leftHanded, scale).hit(pos, s)) }
                },
        ) {
            val l = PyramidLayout(size.width, size.height, leftHanded, scale)
            drawRect(Brush.radialGradient(listOf(p.felt, p.feltDark), center, size.maxDimension * 0.75f))
            val hinted = (hint as? Pyramid.Hint.Take)?.picks.orEmpty()
            fun outline(r: Rect, color: androidx.compose.ui.graphics.Color) =
                drawRoundRect(color, r.topLeft, r.size, CornerRadius(r.width * 0.1f), style = Stroke(r.width * 0.08f))
            for (i in 0 until Pyramid.SIZE) {
                val card = s.pyramid[i] ?: continue
                val r = l.rect(i)
                val pick = PyPick.Pyramid(i)
                val open = Pyramid.isExposed(s, i)
                drawCard(card, r.topLeft, r.size, p, measurer, selected = pick == selected || pick in hinted, dimmed = !open, french = french)
                if (pick in hinted) outline(r, p.hint)
                if (card == refused) outline(r, p.danger)
            }
            // Stock and the turned card.
            val sr = l.stockRect
            if (s.stock.isNotEmpty()) {
                val layers = minOf(3, (s.stock.size + 7) / 8)
                for (k in layers - 1 downTo 0) drawCardBack(sr.topLeft + Offset(k * 2f, -k * 2f), sr.size, p)
                text(measurer, s.stock.size.toString(), l.labelCenter, p.cardFace, pxToSp(l.cardW * 0.3f))
            } else {
                drawCardSlot(sr.topLeft, sr.size, p.cardFace, highlighted = s.canRecycle)
                if (s.canRecycle) drawCircle(p.cardFace, sr.width * 0.28f, sr.center, style = Stroke(sr.width * 0.06f))
            }
            if (hint == Pyramid.Hint.Stock) outline(sr, p.hint)
            val wr = l.wasteRect
            val top = s.waste.lastOrNull()
            if (top != null) {
                drawCard(top, wr.topLeft, wr.size, p, measurer, selected = selected == PyPick.Waste || PyPick.Waste in hinted, french = french)
                if (PyPick.Waste in hinted) outline(wr, p.hint)
                if (top == refused) outline(wr, p.danger)
            } else {
                drawCardSlot(wr.topLeft, wr.size, p.cardFace)
            }
        }
    }
}
