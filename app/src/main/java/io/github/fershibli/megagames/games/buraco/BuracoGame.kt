package io.github.fershibli.megagames.games.buraco

import io.github.fershibli.megagames.ui.theme.wait
import io.github.fershibli.megagames.ui.theme.currentPace
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.ui.art.drawCard
import io.github.fershibli.megagames.ui.art.drawCardBack
import io.github.fershibli.megagames.ui.art.drawCardSlot
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
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Composable
fun BuracoGame(host: GameHost) {
    LoadGame(host, BuracoState.serializer(), { Buraco.deal(Random.Default) }) { state -> BuracoScreen(state, host) }
}

private val SUIT_ORDER = listOf(Suit.SPADES, Suit.HEARTS, Suit.CLUBS, Suit.DIAMONDS)

/** Hand order: by suit (alternating colors), then rank, wild 2s at the end. */
private fun sortHand(hand: List<Card>) = hand.sortedWith(compareBy({ if (it.rank == 2) 1 else 0 }, { SUIT_ORDER.indexOf(it.suit) }, { it.rank }))

@Composable
private fun BuracoScreen(state: MutableState<BuracoState>, host: GameHost) {
    val s = state.value
    val context = LocalResources.current
    var selected by remember { mutableStateOf<Set<Card>>(emptySet()) }
    var error by remember { mutableStateOf<BuracoError?>(null) }
    var afterDraw by remember { mutableStateOf<BuracoState?>(null) }
    val scale = LocalSettings.current.pieceScale

    val pace = currentPace
    LaunchedEffect(error) { if (error != null) { pace.wait(3500); error = null } }

    LaunchedEffect(s.finished) {
        if (!s.finished) return@LaunchedEffect
        val you = Buraco.score(s, YOU)
        val ai = Buraco.score(s, COMPUTER)
        val lines = buildList {
            add(context.getString(R.string.buraco_points, you.total, ai.total))
            when (s.wentOut) {
                YOU -> add(context.getString(R.string.buraco_went_out_you))
                COMPUTER -> add(context.getString(R.string.buraco_went_out_ai))
                else -> add(context.getString(R.string.buraco_deck_over))
            }
        }
        pace.wait(1200)
        host.finish(GameOutcome(you.total > ai.total, lines.joinToString("\n")))
    }

    // Computer's turn, shown step by step.
    LaunchedEffect(s.turn, s.finished) {
        if (s.turn != COMPUTER || s.finished) return@LaunchedEffect
        pace.wait(900)
        val steps = withContext(Dispatchers.Default) { BuracoAi.playTurn(state.value, host.difficulty, Random.Default) }
        for (st in steps) {
            state.value = st
            pace.wait(750)
        }
    }

    fun handle(r: BuracoResult) {
        when (r) {
            is BuracoResult.Ok -> { state.value = r.state; selected = emptySet() }
            is BuracoResult.Error -> error = r.reason
        }
    }

    val myTurn = s.turn == YOU && !s.finished
    GameLayout(
        status = {
            val text = when {
                error != null -> stringResource(
                    when (error) {
                        BuracoError.INVALID_SEQUENCE -> R.string.buraco_err_invalid
                        BuracoError.KEEP_A_CARD -> R.string.buraco_err_keep
                        BuracoError.NEED_CLEAN_CANASTA -> R.string.buraco_err_canasta
                        BuracoError.SELECT_ONE -> R.string.buraco_err_select_one
                        else -> R.string.buraco_prompt_play
                    },
                )
                !myTurn -> stringResource(R.string.computer_turn)
                !s.drawn -> stringResource(R.string.buraco_prompt_draw)
                selected.isNotEmpty() && s.melds[YOU].isNotEmpty() -> stringResource(R.string.buraco_prompt_add)
                else -> stringResource(R.string.buraco_prompt_play)
            }
            StatusPill(text, emphasized = error != null || (myTurn && !s.drawn))
        },
        controls = {
            ActionBar {
                if (myTurn && !s.drawn) {
                    BigButton(stringResource(R.string.buraco_draw), {
                        val next = Buraco.drawFromStock(s)
                        state.value = next
                        afterDraw = next
                    }, icon = AppIcon.PLUS, compact = true)
                    BigButton(stringResource(R.string.buraco_take_pile, s.discard.size), {
                        Buraco.takeDiscardPile(s)?.let { state.value = it; afterDraw = it }
                    }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = s.discard.isNotEmpty(), compact = true)
                } else {
                    BigButton(stringResource(R.string.buraco_discard), {
                        if (selected.size != 1) error = BuracoError.SELECT_ONE else handle(Buraco.discard(s, selected.first()))
                    }, icon = AppIcon.CHECK, enabled = myTurn && selected.size == 1, compact = true)
                    BigButton(stringResource(R.string.buraco_meld), { handle(Buraco.meldNew(s, selected.toList())) }, icon = AppIcon.PLUS, style = ButtonStyle.SECONDARY, enabled = myTurn && selected.size >= 3, compact = true)
                    BigButton(stringResource(R.string.undo), {
                        afterDraw?.let { state.value = it; selected = emptySet() }
                    }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = myTurn && afterDraw != null && afterDraw != s, compact = true)
                }
            }
        },
    ) {
        val p = LocalPalette.current
        val french = LocalGameLanguage.current == AppLanguage.FRENCH
        val meldCard = (30 * scale).dp
        val handCard = (48 * scale).dp
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(p.felt, p.feltDark)))
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Opponent
            SectionLabel(stringResource(R.string.buraco_computer) + " · " + pluralStringResource(R.plurals.buraco_cards, s.hands[COMPUTER].size, s.hands[COMPUTER].size) +
                if (s.tookMorto[COMPUTER]) " · " + stringResource(R.string.buraco_morto_taken) else "")
            MeldArea(s.melds[COMPUTER], meldCard, french, onClick = null)

            // Deck, discard pile and mortos
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                PileView(stringResource(R.string.buraco_deck) + " (${s.stock.size})", (60 * scale).dp, enabled = myTurn && !s.drawn, onClick = {
                    val next = Buraco.drawFromStock(s)
                    state.value = next
                    afterDraw = next
                }) { size, _ ->
                    if (s.stock.isNotEmpty()) drawCardBack(Offset.Zero, size, p) else drawCardSlot(Offset.Zero, size, p.cardFace)
                }
                PileView(stringResource(R.string.buraco_pile) + " (${s.discard.size})", (60 * scale).dp, enabled = myTurn && !s.drawn && s.discard.isNotEmpty(), onClick = {
                    Buraco.takeDiscardPile(s)?.let { state.value = it; afterDraw = it }
                }) { size, measurer ->
                    if (s.discard.isNotEmpty()) drawCard(s.discard.last(), Offset.Zero, size, p, measurer, french = french) else drawCardSlot(Offset.Zero, size, p.cardFace)
                }
                PileView(stringResource(R.string.buraco_morto) + " ×${s.mortos.size}", (40 * scale).dp, enabled = false, onClick = {}) { size, _ ->
                    repeat(s.mortos.size) { k -> drawCardBack(Offset(k * size.width * 0.18f, 0f), Size(size.width * 0.82f, size.height * 0.82f), p) }
                }
            }

            // My sequences
            SectionLabel(stringResource(R.string.buraco_your_sequences) + if (s.tookMorto[YOU]) " · " + stringResource(R.string.buraco_morto_taken) else "")
            MeldArea(s.melds[YOU], meldCard, french, onClick = if (myTurn && s.drawn && selected.isNotEmpty()) { i -> handle(Buraco.addToMeld(s, i, selected.toList())) } else null)

            // My hand
            SectionLabel(stringResource(R.string.buraco_your_hand) + " (${s.hands[YOU].size})")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (card in sortHand(s.hands[YOU])) {
                    val isSel = card in selected
                    CardView(card, handCard, isSel, french, Modifier
                        .padding(top = if (isSel) 0.dp else 8.dp, bottom = if (isSel) 8.dp else 0.dp)
                        .clickable(enabled = myTurn && s.drawn, role = Role.Checkbox) { selected = if (isSel) selected - card else selected + card })
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CardView(card: Card, width: Dp, selected: Boolean, french: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    Canvas(modifier.size(width, width * 1.42f)) {
        drawCard(card, Offset.Zero, size, p, measurer, selected = selected, french = french, wildMark = card.rank == 2)
    }
}

@Composable
private fun PileView(
    label: String,
    width: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Size, androidx.compose.ui.text.TextMeasurer) -> Unit,
) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width, width * 1.42f)
                .clip(RoundedCornerShape(8.dp))
                .then(if (enabled) Modifier.border(3.dp, p.highlight, RoundedCornerShape(8.dp)) else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
        ) {
            val measurer = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) { draw(size, measurer) }
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

/** Sequences on the table, each drawn as a fan of overlapping cards. */
@Composable
private fun MeldArea(melds: List<List<Card>>, cardWidth: Dp, french: Boolean, onClick: ((Int) -> Unit)?) {
    val p = LocalPalette.current
    if (melds.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(cardWidth * 1.42f).border(2.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp)))
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        melds.forEachIndexed { i, meld ->
            val arranged = BuracoRules.arrange(meld)
            val canasta = BuracoRules.isCanasta(meld)
            val clean = arranged?.wild == null
            val step = cardWidth * 0.42f
            val width = cardWidth + step * (meld.size - 1)
            val measurer = rememberTextMeasurer()
            val border = when {
                canasta && clean -> Color(0xFFE8B923)
                canasta -> Color(0xFFC0C0C0)
                onClick != null -> p.highlight
                else -> Color.Transparent
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(if (canasta || onClick != null) 3.dp else 0.dp, border, RoundedCornerShape(8.dp))
                        .then(if (onClick != null) Modifier.clickable(role = Role.Button) { onClick(i) } else Modifier)
                        .padding(3.dp),
                ) {
                    Canvas(Modifier.size(width, cardWidth * 1.42f)) {
                        val stepPx = step.toPx()
                        val cw = cardWidth.toPx()
                        meld.forEachIndexed { k, c ->
                            drawCard(c, Offset(k * stepPx, 0f), Size(cw, cw * 1.42f), p, measurer, french = french, shadow = k == 0, wildMark = c == arranged?.wild)
                        }
                    }
                }
                if (canasta) {
                    Text(
                        stringResource(if (clean) R.string.buraco_clean else R.string.buraco_dirty),
                        color = border, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                    )
                } else Spacer(Modifier.width(1.dp))
            }
        }
    }
}
