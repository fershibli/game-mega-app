package io.github.fershibli.megagames.games.freecell

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
import io.github.fershibli.megagames.games.cards.CARD_AUTO_STEP_MS
import io.github.fershibli.megagames.games.cards.CARD_FLASH_MS
import io.github.fershibli.megagames.games.cards.CARD_MESSAGE_MS
import io.github.fershibli.megagames.games.cards.CARD_REFUSED_MS
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.NoMovesDialog
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.cards.columnOffsets
import io.github.fershibli.megagames.ui.art.drawCard
import io.github.fershibli.megagames.ui.art.drawCardSlot
import io.github.fershibli.megagames.ui.art.drawSuit
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
fun FreeCellGame(host: GameHost) {
    LoadGame(host, FreeCellState.serializer(), { FreeCellDeals.deal(host.difficulty, Random.Default) }) { state ->
        FreeCellScreen(state, host)
    }
}

/** A card the player touched: in a column, a free cell or on a foundation. */
internal data class FcHit(val pile: FcPile, val index: Int)

/** Geometry of the table, shared by drawing, touch handling and tests so they always agree. */
internal class FreeCellLayout(val width: Float, val height: Float, val leftHanded: Boolean, scale: Float, s: FreeCellState) {
    val gap = width * 0.012f
    val cardW = (width - gap * (FreeCell.COLUMNS + 1)) / FreeCell.COLUMNS
    val cardH = cardW * 1.42f
    val top = gap * 2
    fun colX(i: Int) = gap + i * (cardW + gap)

    /** The free cells are on the side of the thumb, the foundations on the other side. */
    fun foundationSlot(i: Int) = Rect(Offset(colX(if (leftHanded) 4 + i else i), top), Size(cardW, cardH))
    fun cellSlot(i: Int) = Rect(Offset(colX(if (leftHanded) i else 4 + i), top), Size(cardW, cardH))
    val tableauTop = top + cardH + gap * 4
    fun columnSlot(c: Int) = Rect(Offset(colX(c), tableauTop), Size(cardW, cardH))

    /** Rect of every card on the columns, in drawing order (last = topmost). */
    val cards = ArrayList<Pair<Rect, FcHit>>()

    init {
        for (c in 0 until FreeCell.COLUMNS) {
            val col = s.columns[c]
            val ys = columnOffsets(List(col.size) { true }, cardH, tableauTop, height - gap, scale)
            col.indices.forEach { i -> cards += Rect(Offset(colX(c), ys[i]), Size(cardW, cardH)) to FcHit(FcPile.Column(c), i) }
        }
        for (i in 0 until FreeCell.CELLS) if (s.cells[i] != null) cards += cellSlot(i) to FcHit(FcPile.Cell(i), 0)
    }

    fun rectOf(pile: FcPile, index: Int): Rect = when (pile) {
        is FcPile.Cell -> cellSlot(pile.index)
        is FcPile.Foundation -> foundationSlot(pile.index)
        is FcPile.Column -> cards.firstOrNull { it.second.pile == pile && it.second.index == index }?.first ?: columnSlot(pile.index)
    }

    fun hit(p: Offset): FcHit? = cards.lastOrNull { it.first.contains(p) }?.second
}

/** A move just made by a tap, kept so that tapping the same card again tries the next place. */
private class TapCycle(val before: FreeCellState, val options: List<FcMove>, val k: Int, val after: FreeCellState, val card: Card)

@Composable
private fun FreeCellScreen(state: MutableState<FreeCellState>, host: GameHost) {
    val s = state.value
    val history = remember { mutableStateListOf<FreeCellState>() }
    var flash by remember { mutableStateOf<Card?>(null) }
    var refused by remember { mutableStateOf<Card?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var hintMove by remember { mutableStateOf<FcMove?>(null) }
    var noHint by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var autoFinishing by remember { mutableStateOf(false) }
    var cycle by remember { mutableStateOf<TapCycle?>(null) }
    var stuck by remember { mutableStateOf(false) }
    var showStuck by remember { mutableStateOf(false) }
    var stuckDismissed by remember { mutableStateOf(false) }
    val leftHanded = LocalSettings.current.leftHanded
    val scale = LocalSettings.current.pieceScale
    val french = LocalGameLanguage.current == AppLanguage.FRENCH
    val context = LocalResources.current
    val scope = rememberCoroutineScope()

    fun clearMarks() {
        hintMove = null
        noHint = false
        message = null
        cycle = null
    }

    fun commit(next: FreeCellState, moved: Card?) {
        history += s
        if (history.size > 300) history.removeAt(0)
        state.value = next
        flash = moved
        clearMarks()
        if (next.won) host.finish(GameOutcome(true, null))
    }

    fun undo() {
        history.removeLastOrNull()?.let { state.value = it }
        clearMarks()
        showStuck = false
    }

    // A game saved right at the winning move still ends properly when it is restored.
    LaunchedEffect(Unit) { if (state.value.won) host.finish(GameOutcome(true, null)) }
    LaunchedEffect(flash) { if (flash != null) { delay(CARD_FLASH_MS); flash = null } }
    LaunchedEffect(refused) { if (refused != null) { delay(CARD_REFUSED_MS); refused = null } }
    LaunchedEffect(message) { if (message != null) { delay(CARD_MESSAGE_MS); message = null } }
    LaunchedEffect(autoFinishing) {
        while (autoFinishing) {
            val cur = state.value
            val next = FreeCell.autoStep(cur) ?: break
            history += cur
            state.value = next
            if (next.won) { host.finish(GameOutcome(true, null)); break }
            delay(CARD_AUTO_STEP_MS)
        }
        autoFinishing = false
    }
    // After every move, a short search tells whether the game can still be won.
    LaunchedEffect(s) {
        val lost = !s.won && withContext(Dispatchers.Default) { FreeCellDeals.isLost(s) }
        stuck = lost
        if (!lost) stuckDismissed = false
        else if (!stuckDismissed) { delay(CARD_FLASH_MS); showStuck = true }
    }

    fun onTap(hit: FcHit?) {
        if (hit == null || autoFinishing) return
        val cards = FreeCell.pickUp(s, hit.pile, hit.index)
        val tapped = when (val pile = hit.pile) {
            is FcPile.Cell -> s.cells[pile.index]
            is FcPile.Column -> s.columns[pile.index][hit.index]
            is FcPile.Foundation -> null
        } ?: return
        // Tapping the card that just moved tries the next place it can go.
        val c = cycle
        if (c != null && c.after === s && c.card == tapped && c.options.size > 1) {
            val k = (c.k + 1) % c.options.size
            val next = FreeCell.apply(c.before, c.options[k]) ?: return
            state.value = next
            flash = tapped
            cycle = TapCycle(c.before, c.options, k, next, tapped)
            if (next.won) host.finish(GameOutcome(true, null))
            return
        }
        if (cards == null) {
            refused = tapped
            message = context.getString(R.string.freecell_not_in_order)
            return
        }
        val options = FreeCell.destinations(s, hit.pile, hit.index)
        if (options.isEmpty()) {
            refused = tapped
            // The run would fit somewhere, but there is not enough room to carry it.
            val fitsSomewhere = (0 until FreeCell.COLUMNS).any { it != (hit.pile as? FcPile.Column)?.index && FreeCell.fits(s, cards, FcPile.Column(it)) }
            if (cards.size > 1 && fitsSomewhere) {
                message = context.getString(R.string.freecell_too_many, cards.size, FreeCell.maxRun(s, false))
            }
            return
        }
        val next = FreeCell.apply(s, options.first()) ?: return
        commit(next, tapped)
        cycle = TapCycle(s, options, 0, next, tapped)
    }

    fun askHint() {
        val at = s
        thinking = true
        noHint = false
        scope.launch {
            val move = withContext(Dispatchers.Default) { FreeCellDeals.hint(at) }
            thinking = false
            if (state.value === at) {
                hintMove = move
                noHint = move == null
            }
        }
    }

    if (showStuck) {
        NoMovesDialog(
            title = stringResource(R.string.freecell_stuck_title),
            message = stringResource(R.string.freecell_stuck_message),
            giveUp = stringResource(R.string.freecell_give_up),
            keepLooking = stringResource(R.string.freecell_keep_playing),
            canUndo = history.isNotEmpty(),
            onUndo = { showStuck = false; undo() },
            onGiveUp = { showStuck = false; host.finish(GameOutcome(false, context.getString(R.string.freecell_lost))) },
            onKeepLooking = { showStuck = false; stuckDismissed = true },
        )
    }

    GameLayout(
        status = {
            val text = when {
                message != null -> message!!
                thinking -> stringResource(R.string.freecell_thinking)
                noHint -> stringResource(R.string.freecell_no_hint)
                else -> stringResource(R.string.freecell_status, s.moves, s.freeCells)
            }
            StatusPill(text)
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), { undo() }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty() && !autoFinishing)
                when {
                    s.canAutoFinish -> BigButton(stringResource(R.string.freecell_finish), { autoFinishing = true }, icon = AppIcon.CHECK, enabled = !autoFinishing)
                    stuck -> BigButton(stringResource(R.string.freecell_give_up), { showStuck = true }, icon = AppIcon.CLOSE, style = ButtonStyle.SECONDARY)
                    else -> BigButton(stringResource(R.string.hint), { askHint() }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = !thinking)
                }
            }
        },
    ) {
        val p = LocalPalette.current
        val measurer = rememberTextMeasurer()
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("freecell-table")
                .pointerInput(s, leftHanded, scale) {
                    detectTapGestures { pos -> onTap(FreeCellLayout(size.width.toFloat(), size.height.toFloat(), leftHanded, scale, s).hit(pos)) }
                },
        ) {
            val l = FreeCellLayout(size.width, size.height, leftHanded, scale, s)
            drawRect(Brush.radialGradient(listOf(p.felt, p.feltDark), center, size.maxDimension * 0.75f))
            // Foundations show their top card, or the suit they wait for.
            for (f in 0 until 4) {
                val r = l.foundationSlot(f)
                val top = FreeCell.foundationCard(s, f)
                if (top != null) drawCard(top, r.topLeft, r.size, p, measurer, selected = top == flash, french = french)
                else {
                    drawCardSlot(r.topLeft, r.size, p.cardFace)
                    drawSuit(Suit.entries[f], r.center, r.width * 0.5f, p.cardFace.copy(alpha = 0.5f))
                }
            }
            for (i in 0 until FreeCell.CELLS) l.cellSlot(i).let { drawCardSlot(it.topLeft, it.size, p.cardFace, highlighted = true) }
            for (c in 0 until FreeCell.COLUMNS) if (s.columns[c].isEmpty()) l.columnSlot(c).let { drawCardSlot(it.topLeft, it.size, p.cardFace) }
            val hint = hintMove
            for ((rect, hit) in l.cards) {
                val card = when (val pile = hit.pile) {
                    is FcPile.Cell -> s.cells[pile.index]!!
                    is FcPile.Column -> s.columns[pile.index][hit.index]
                    is FcPile.Foundation -> continue
                }
                val inHint = hint != null && hint.from == hit.pile && hit.index >= hint.cardIndex
                drawCard(card, rect.topLeft, rect.size, p, measurer, selected = card == flash || inHint, french = french)
                if (card == refused) drawRoundRect(p.danger, rect.topLeft, rect.size, CornerRadius(rect.width * 0.1f), style = Stroke(rect.width * 0.07f))
            }
            hint?.let { m ->
                val target = when (val t = m.to) {
                    is FcPile.Column -> l.rectOf(t, s.columns[t.index].lastIndex)
                    else -> l.rectOf(t, 0)
                }
                drawRoundRect(p.hint, target.topLeft, target.size, CornerRadius(target.width * 0.1f), style = Stroke(target.width * 0.08f))
            }
        }
    }
}
