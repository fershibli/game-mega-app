package io.github.fershibli.megagames.games.spider

import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
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
import io.github.fershibli.megagames.games.cards.columnOffsets
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
import kotlin.random.Random

@Composable
fun SpiderGame(host: GameHost) {
    val suits = SpiderRules.suitsOf(host.variant?.key)
    LoadGame(host, SpiderState.serializer(), { Spider.deal(suits, Random.Default) }) { state ->
        SpiderScreen(state, host)
    }
}

/** What the player touched on the table. */
internal sealed interface SpiderHit {
    data object Stock : SpiderHit
    data class Card(val col: Int, val index: Int) : SpiderHit
}

/** Geometry of the table, shared by drawing, touch handling and tests so they always agree. */
internal class SpiderLayout(val width: Float, val height: Float, val leftHanded: Boolean, scale: Float, s: SpiderState) {
    val gap = width * 0.01f
    val cardW = (width - gap * (Spider.COLUMNS + 1)) / Spider.COLUMNS
    val cardH = cardW * 1.42f
    val top = gap * 2
    fun colX(i: Int) = gap + i * (cardW + gap)

    /** The stock sits on the side of the thumb; the finished runs on the other side. */
    val stockCol = if (leftHanded) 0 else Spider.COLUMNS - 1
    fun doneCol(k: Int) = if (leftHanded) 2 + k else k
    val tableauTop = top + cardH + cardW * 0.5f

    /** Rect of every card on the columns, in drawing order (last = topmost). */
    val cards = ArrayList<Pair<Rect, SpiderHit.Card>>()

    init {
        for (c in 0 until Spider.COLUMNS) {
            val col = s.tableau[c]
            val ys = columnOffsets(col.map { it.faceUp }, cardH, tableauTop, height - gap, scale)
            col.indices.forEach { i -> cards += Rect(Offset(colX(c), ys[i]), Size(cardW, cardH)) to SpiderHit.Card(c, i) }
        }
    }

    val stockRect = Rect(Offset(colX(stockCol), top), Size(cardW, cardH))
    fun doneSlot(k: Int) = Rect(Offset(colX(doneCol(k)), top), Size(cardW, cardH))
    fun columnSlot(c: Int) = Rect(Offset(colX(c), tableauTop), Size(cardW, cardH))

    /** The card at [col], [index] (for drawing hints and for tests). */
    fun rectOf(col: Int, index: Int): Rect? = cards.firstOrNull { it.second.col == col && it.second.index == index }?.first

    fun hit(p: Offset): SpiderHit? {
        if (stockRect.contains(p)) return SpiderHit.Stock
        return cards.lastOrNull { it.first.contains(p) }?.second
    }
}

/** A move just made by a tap, kept so that tapping the same card again tries the next place. */
private class TapCycle(val before: SpiderState, val options: List<SpiderMove>, val k: Int, val after: SpiderState, val card: Card)

@Composable
private fun SpiderScreen(state: MutableState<SpiderState>, host: GameHost) {
    val pace = currentPace
    val s = state.value
    val history = remember { mutableStateListOf<SpiderState>() }
    var flash by remember { mutableStateOf<Card?>(null) }
    var refused by remember { mutableStateOf<Card?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    var hintMove by remember { mutableStateOf<SpiderMove?>(null) }
    var hintDeal by remember { mutableStateOf(false) }
    var cycle by remember { mutableStateOf<TapCycle?>(null) }
    var showStuck by remember { mutableStateOf(false) }
    var stuckDismissed by remember { mutableStateOf(false) }
    val leftHanded = LocalSettings.current.leftHanded
    val scale = LocalSettings.current.pieceScale
    val french = LocalGameLanguage.current == AppLanguage.FRENCH
    val context = LocalResources.current
    val stuck = remember(s) { Spider.isStuck(s) }

    fun commit(next: SpiderState, moved: Card?) {
        history += s
        if (history.size > 300) history.removeAt(0)
        state.value = next
        flash = moved
        hintMove = null
        hintDeal = false
        message = null
        cycle = null
        if (next.won) host.finish(GameOutcome(true, null))
    }

    fun undo() {
        history.removeLastOrNull()?.let { state.value = it }
        hintMove = null
        hintDeal = false
        cycle = null
        showStuck = false
    }

    // A game saved right at the winning move still ends properly when it is restored.
    LaunchedEffect(Unit) { if (state.value.won) host.finish(GameOutcome(true, null)) }
    LaunchedEffect(flash) { if (flash != null) { pace.wait(CARD_FLASH_MS); flash = null } }
    LaunchedEffect(refused) { if (refused != null) { pace.wait(CARD_REFUSED_MS); refused = null } }
    LaunchedEffect(message) { if (message != null) { pace.wait(CARD_MESSAGE_MS); message = null } }
    LaunchedEffect(stuck) {
        if (!stuck) stuckDismissed = false
        else if (!stuckDismissed) { pace.wait(CARD_FLASH_MS); showStuck = true }
    }

    fun onTap(hit: SpiderHit?) {
        when (hit) {
            SpiderHit.Stock -> {
                val next = Spider.dealRow(s)
                if (next != null) commit(next, null)
                else if (s.stock.isNotEmpty()) message = R.string.spider_fill_columns
            }
            is SpiderHit.Card -> {
                val tc = s.tableau[hit.col][hit.index]
                if (!tc.faceUp) return
                // Tapping the card that just moved tries the next place it can go.
                val c = cycle
                if (c != null && c.after === s && c.card == tc.card && c.options.size > 1) {
                    val k = (c.k + 1) % c.options.size
                    val next = Spider.apply(c.before, c.options[k]) ?: return
                    state.value = next
                    flash = tc.card
                    cycle = TapCycle(c.before, c.options, k, next, tc.card)
                    if (next.won) host.finish(GameOutcome(true, null))
                    return
                }
                val options = Spider.destinations(s, hit.col, hit.index)
                if (options.isEmpty()) {
                    refused = tc.card
                    if (!Spider.canPickUp(s, hit.col, hit.index)) message = R.string.spider_cannot_move
                    return
                }
                val next = Spider.apply(s, options.first()) ?: return
                commit(next, tc.card)
                cycle = TapCycle(s, options, 0, next, tc.card)
            }
            null -> Unit
        }
    }

    if (showStuck) {
        NoMovesDialog(
            title = stringResource(R.string.spider_stuck_title),
            message = stringResource(R.string.spider_stuck_message),
            giveUp = stringResource(R.string.spider_give_up),
            keepLooking = stringResource(R.string.spider_keep_playing),
            canUndo = history.isNotEmpty(),
            onUndo = { showStuck = false; undo() },
            onGiveUp = { showStuck = false; host.finish(GameOutcome(false, context.getString(R.string.spider_lost))) },
            onKeepLooking = { showStuck = false; stuckDismissed = true },
        )
    }

    GameLayout(
        status = {
            val text = when {
                message != null -> stringResource(message!!)
                hintDeal -> stringResource(R.string.spider_hint_deal)
                else -> stringResource(R.string.spider_status, s.done.size, s.dealsLeft)
            }
            StatusPill(text)
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), { undo() }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty())
                if (stuck) {
                    BigButton(stringResource(R.string.spider_give_up), { showStuck = true }, icon = AppIcon.CLOSE, style = ButtonStyle.SECONDARY)
                } else {
                    BigButton(stringResource(R.string.hint), {
                        hintMove = Spider.hint(s)
                        hintDeal = hintMove == null && s.canDeal
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
                .testTag("spider-table")
                .pointerInput(s, leftHanded, scale) {
                    detectTapGestures { pos -> onTap(SpiderLayout(size.width.toFloat(), size.height.toFloat(), leftHanded, scale, s).hit(pos)) }
                },
        ) {
            val l = SpiderLayout(size.width, size.height, leftHanded, scale, s)
            drawRect(Brush.radialGradient(listOf(p.felt, p.feltDark), center, size.maxDimension * 0.75f))
            // Finished runs: their King on top.
            for (k in 0 until Spider.RUNS) {
                val r = l.doneSlot(k)
                val suit = s.done.getOrNull(k)
                if (suit != null) drawCard(Card(suit, 13), r.topLeft, r.size, p, measurer, french = french)
                else drawCardSlot(r.topLeft, r.size, p.cardFace)
            }
            // Stock: one back per row still to deal, and how many rows are left.
            val sr = l.stockRect
            if (s.stock.isNotEmpty()) {
                val toward = if (leftHanded) 1 else -1 // spread towards the middle of the table
                for (k in s.dealsLeft - 1 downTo 0) drawCardBack(sr.topLeft + Offset(toward * k * l.gap * 0.5f, 0f), sr.size, p)
                text(measurer, s.dealsLeft.toString(), Offset(sr.center.x, sr.bottom + l.cardW * 0.25f), p.cardFace, pxToSp(l.cardW * 0.32f))
                if (hintDeal) drawRoundRect(p.hint, sr.topLeft, sr.size, CornerRadius(sr.width * 0.1f), style = Stroke(sr.width * 0.08f))
            } else {
                drawCardSlot(sr.topLeft, sr.size, p.cardFace)
            }
            for (c in 0 until Spider.COLUMNS) if (s.tableau[c].isEmpty()) l.columnSlot(c).let { drawCardSlot(it.topLeft, it.size, p.cardFace) }
            // Cards
            val hint = hintMove
            for ((rect, hit) in l.cards) {
                val tc = s.tableau[hit.col][hit.index]
                val inHint = hint != null && hit.col == hint.from && hit.index >= hint.index
                drawCard(tc.card, rect.topLeft, rect.size, p, measurer, faceUp = tc.faceUp, selected = tc.faceUp && (tc.card == flash || inHint), french = french)
                if (tc.card == refused && tc.faceUp) drawRoundRect(p.danger, rect.topLeft, rect.size, CornerRadius(rect.width * 0.1f), style = Stroke(rect.width * 0.07f))
            }
            hint?.let { m ->
                val target = l.rectOf(m.to, s.tableau[m.to].lastIndex) ?: l.columnSlot(m.to)
                drawRoundRect(p.hint, target.topLeft, target.size, CornerRadius(target.width * 0.1f), style = Stroke(target.width * 0.08f))
            }
        }
    }
}
