package io.github.fershibli.megagames.games.solitaire

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.cards.Card
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
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun SolitaireGame(host: GameHost) {
    LoadGame(host, SolitaireState.serializer(), { SolitaireState.deal(host.difficulty, Random.Default) }) { state ->
        SolitaireScreen(state, host)
    }
}

/** What the player touched on the table. */
private sealed interface Hit {
    data object Stock : Hit
    data class Card(val pile: Pile, val index: Int) : Hit
}

/** Geometry of the table, shared by drawing and touch handling so they always agree. */
private class TableLayout(val width: Float, val height: Float, val leftHanded: Boolean, s: SolitaireState) {
    val gap = width * 0.015f
    val cardW = (width - gap * 8) / 7
    val cardH = cardW * 1.42f
    val top = gap * 2
    fun colX(i: Int) = gap + i * (cardW + gap)
    val stockCol = if (leftHanded) 0 else 6
    val wasteCol = if (leftHanded) 1 else 5
    fun foundationCol(i: Int) = if (leftHanded) 3 + i else i
    val tableauTop = top + cardH + gap * 4

    /** Rect of every visible card, in drawing order (last = topmost). */
    val cards = ArrayList<Pair<Rect, Hit>>()
    val wasteShown: List<Card> = s.waste.takeLast(if (s.drawCount == 1) 1 else 3)

    init {
        val shown = wasteShown.size
        wasteShown.forEachIndexed { i, _ ->
            val fan = (shown - 1 - i) * cardW * 0.32f * (if (leftHanded) 1 else -1)
            cards += Rect(Offset(colX(wasteCol) + fan, top), Size(cardW, cardH)) to Hit.Card(Pile.Waste, s.waste.size - shown + i)
        }
        for (f in 0 until 4) {
            val n = s.foundations[f].size
            if (n > 0) cards += Rect(Offset(colX(foundationCol(f)), top), Size(cardW, cardH)) to Hit.Card(Pile.Foundation(f), n - 1)
        }
        val available = height - tableauTop - gap
        for (c in 0 until 7) {
            val col = s.tableau[c]
            var down = cardH * 0.13f
            var up = cardH * 0.3f
            val needed = col.dropLast(1).sumOf { (if (it.faceUp) up else down).toDouble() }.toFloat() + cardH
            if (needed > available && col.size > 1) {
                val k = (available - cardH) / (needed - cardH)
                down *= k
                up *= k
            }
            var y = tableauTop
            col.forEachIndexed { i, tc ->
                cards += Rect(Offset(colX(c), y), Size(cardW, cardH)) to Hit.Card(Pile.Column(c), i)
                y += if (tc.faceUp) up else down
            }
        }
    }

    val stockRect = Rect(Offset(colX(stockCol), top), Size(cardW, cardH))
    fun columnSlot(c: Int) = Rect(Offset(colX(c), tableauTop), Size(cardW, cardH))
    fun foundationSlot(f: Int) = Rect(Offset(colX(foundationCol(f)), top), Size(cardW, cardH))

    fun hit(p: Offset): Hit? {
        if (stockRect.contains(p)) return Hit.Stock
        cards.lastOrNull { it.first.contains(p) }?.let { return it.second }
        for (c in 0 until 7) if (columnSlot(c).contains(p)) return null
        return null
    }
}

@Composable
private fun SolitaireScreen(state: MutableState<SolitaireState>, host: GameHost) {
    val s = state.value
    val history = remember { mutableStateListOf<SolitaireState>() }
    var flash by remember { mutableStateOf<Card?>(null) }
    var refused by remember { mutableStateOf<Card?>(null) }
    var hintMove by remember { mutableStateOf<Move?>(null) }
    var noHint by remember { mutableStateOf(false) }
    var autoFinishing by remember { mutableStateOf(false) }
    val leftHanded = LocalSettings.current.leftHanded
    val french = LocalGameLanguage.current == AppLanguage.FRENCH

    fun commit(next: SolitaireState, moved: Card?) {
        history += s
        if (history.size > 300) history.removeAt(0)
        state.value = next
        flash = moved
        hintMove = null
        noHint = false
        if (next.won) host.finish(GameOutcome(true, null))
    }

    // A game saved right at the winning move still ends properly when it is restored.
    LaunchedEffect(Unit) { if (state.value.won) host.finish(GameOutcome(true, null)) }
    LaunchedEffect(flash) { if (flash != null) { delay(700); flash = null } }
    LaunchedEffect(refused) { if (refused != null) { delay(500); refused = null } }
    LaunchedEffect(autoFinishing) {
        while (autoFinishing) {
            val cur = state.value
            val next = Solitaire.autoStep(cur) ?: break
            history += cur
            state.value = next
            if (next.won) { host.finish(GameOutcome(true, null)); break }
            delay(140)
        }
        autoFinishing = false
    }

    fun onTap(hit: Hit?) {
        when (hit) {
            Hit.Stock -> Solitaire.tapStock(s)?.let { commit(it, null) }
            is Hit.Card -> {
                val cards = Solitaire.pickUp(s, hit.pile, hit.index) ?: return
                val move = Solitaire.bestMove(s, hit.pile, hit.index)
                val next = move?.let { Solitaire.apply(s, it) }
                if (next != null) commit(next, cards.first()) else refused = cards.first()
            }
            null -> Unit
        }
    }

    GameLayout(
        status = {
            val parts = mutableListOf(stringResource(R.string.solitaire_moves, s.moves))
            if (s.passesLeft >= 0) parts += stringResource(R.string.solitaire_passes, s.passesLeft)
            StatusPill(if (noHint) stringResource(R.string.solitaire_no_hint) else parts.joinToString("  ·  "))
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), { history.removeLastOrNull()?.let { state.value = it } }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty() && !autoFinishing)
                if (s.canAutoFinish) {
                    BigButton(stringResource(R.string.solitaire_finish), { autoFinishing = true }, icon = AppIcon.CHECK, enabled = !autoFinishing)
                } else {
                    BigButton(stringResource(R.string.hint), {
                        hintMove = Solitaire.hint(s)
                        noHint = hintMove == null
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
                .pointerInput(s, leftHanded) {
                    detectTapGestures { pos -> onTap(TableLayout(size.width.toFloat(), size.height.toFloat(), leftHanded, s).hit(pos)) }
                },
        ) {
            val l = TableLayout(size.width, size.height, leftHanded, s)
            drawRect(Brush.radialGradient(listOf(p.felt, p.feltDark), center, size.maxDimension * 0.75f))
            // Slots
            for (f in 0 until 4) {
                val r = l.foundationSlot(f)
                drawCardSlot(r.topLeft, r.size, p.cardFace)
                text(measurer, "A", r.center, p.cardFace.copy(alpha = 0.45f), pxToSp(r.width * 0.45f))
            }
            for (c in 0 until 7) l.columnSlot(c).let { drawCardSlot(it.topLeft, it.size, p.cardFace) }
            // Stock
            val sr = l.stockRect
            if (s.stock.isNotEmpty()) {
                val layers = minOf(3, (s.stock.size + 9) / 10)
                for (k in layers - 1 downTo 0) drawCardBack(sr.topLeft + Offset(k * 2f, -k * 2f), sr.size, p)
                text(measurer, s.stock.size.toString(), Offset(sr.center.x, sr.bottom + l.gap * 2.2f), p.cardFace, pxToSp(l.cardW * 0.22f))
            } else {
                drawCardSlot(sr.topLeft, sr.size, p.cardFace, highlighted = s.canRecycle)
                if (s.canRecycle) {
                    drawCircle(p.cardFace, sr.width * 0.28f, sr.center, style = Stroke(sr.width * 0.06f))
                }
            }
            // Cards
            val hintCard = hintMove?.let { Solitaire.pickUp(s, it.from, it.cardIndex)?.first() }
            for ((rect, hit) in l.cards) {
                hit as Hit.Card
                val (card, faceUp) = when (val pile = hit.pile) {
                    Pile.Waste -> s.waste[hit.index] to true
                    is Pile.Foundation -> s.foundations[pile.index][hit.index] to true
                    is Pile.Column -> s.tableau[pile.index][hit.index].let { it.card to it.faceUp }
                }
                drawCard(card, rect.topLeft, rect.size, p, measurer, faceUp = faceUp, selected = card == flash || card == hintCard, french = french)
                if (card == refused) drawRoundRect(p.danger, rect.topLeft, rect.size, CornerRadius(rect.width * 0.1f), style = Stroke(rect.width * 0.07f))
            }
            hintMove?.let { m ->
                val target = when (val t = m.to) {
                    is Pile.Foundation -> l.foundationSlot(t.index)
                    is Pile.Column -> l.cards.lastOrNull { (it.second as Hit.Card).pile == t }?.first ?: l.columnSlot(t.index)
                    Pile.Waste -> null
                }
                target?.let { drawRoundRect(p.hint, it.topLeft, it.size, CornerRadius(it.width * 0.1f), style = Stroke(it.width * 0.08f)) }
            }
        }
    }
}
