package io.github.fershibli.megagames.games.cacheta

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.SeatChip
import io.github.fershibli.megagames.games.cards.tableSeat
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.drawCard
import io.github.fershibli.megagames.ui.art.drawCardBack
import io.github.fershibli.megagames.ui.art.drawCardSlot
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.Handoff
import io.github.fershibli.megagames.ui.components.HandoffOverlay
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalGameLanguage
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import io.github.fershibli.megagames.ui.theme.Palette
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CachetaGame(host: GameHost) {
    val rules = CachetaRules.of(host.variant?.key)
    LoadGame(host, CachetaState.serializer(), { newGame(rules, host) }) { state -> CachetaScreen(state, host) }
}

/** A new hand for the table chosen before starting (2 to 4 seats), with the hands sorted by suit. */
private fun newGame(rules: CachetaRules, host: GameHost): CachetaState {
    val players = host.setup.seats.takeIf { it in 2..4 } ?: 2
    var s = Cacheta.deal(rules, players, Random.Default)
    // Taking turns, everybody's hand starts sorted; otherwise only yours is ever shown.
    for (seat in 0 until if (host.setup.together) players else 1) s = Cacheta.sorted(s, true, seat)
    return s
}

/** Short spoken/written name of a card, e.g. "7♥". */
fun cardLabel(c: Card, french: Boolean = false): String = Card.rankLabel(c.rank, french) + when (c.suit) {
    Suit.SPADES -> "♠"
    Suit.HEARTS -> "♥"
    Suit.DIAMONDS -> "♦"
    Suit.CLUBS -> "♣"
}

@Composable
private fun CachetaScreen(state: MutableState<CachetaState>, host: GameHost) {
    val s = state.value
    if (s.players == 0) {
        // A game saved before the table screen chose the seats: deal it now.
        LaunchedEffect(Unit) { state.value = newGame(s.rules, host) }
        return
    }
    val res = LocalResources.current
    val pace = currentPace
    val p = LocalPalette.current
    var selected by remember { mutableStateOf<Card?>(null) }
    var error by remember { mutableStateOf(false) }
    val french = LocalGameLanguage.current == AppLanguage.FRENCH
    val t = s.table
    // Taking turns on this phone: every seat is a person, and the phone is passed between them.
    val together = host.setup.together
    val handoff = remember { Handoff() }
    // The seat whose hand is shown. Taking turns, nobody's until the first person says "ready".
    var viewer by remember { mutableIntStateOf(if (together) -1 else 0) }
    val me = viewer.coerceAtLeast(0)
    val showHand = viewer >= 0 && handoff.showsPrivate
    fun human(seat: Int) = together || seat == 0
    val names = List(s.players) { if (together) stringResource(R.string.player_number, it + 1) else stringResource(R.string.cacheta_computer, it) }
    val actor = Cacheta.actor(s)

    LaunchedEffect(error) { if (error) { pace.wait(3500); error = false } }

    LaunchedEffect(s.finished) {
        if (!s.finished) return@LaunchedEffect
        val detail = when {
            together && s.winner >= 0 -> res.getString(if (s.tenCards) R.string.cacheta_went_out_ten_seat else R.string.cacheta_went_out_seat, names[s.winner])
            s.winner == 0 && s.tenCards -> res.getString(R.string.cacheta_won_ten)
            s.winner == 0 -> res.getString(R.string.cacheta_won)
            s.winner > 0 -> res.getString(R.string.cacheta_lost, s.winner)
            else -> res.getString(R.string.cacheta_deck_over)
        }
        pace.wait(3000)
        host.finish(GameOutcome(together || s.winner == 0, detail, shared = together))
    }

    // The computers play one step at a time, so you can follow every draw and discard.
    LaunchedEffect(s) {
        if (s.finished || human(Cacheta.actor(s))) return@LaunchedEffect
        pace.wait(if (s.drawn) 700 else 900)
        state.value = withContext(Dispatchers.Default) { CachetaAi.step(s, host.difficulty, Random.Default) }
    }

    // Taking turns: when another person must act, cover the hand and ask to pass the phone.
    LaunchedEffect(actor, s.finished) {
        if (together && !s.finished && actor != viewer) handoff.passTo(tableSeat(p, actor, names[actor]))
    }

    // The person holding the phone must act now (and may see their hand).
    val acting = actor == me && human(me) && showHand && !s.finished
    val offered = acting && s.offers.firstOrNull() == me
    val myTurn = acting && s.turn == me && s.offers.isEmpty()
    val goOut = if (myTurn && s.drawn) t.goOut(s.hands[me], forbidden = s.tookFromPile, preferred = selected) else null
    val inGroups = remember(s.hands[me], s.vira, s.variant) {
        val hand = s.hands[me]
        val mask = CachetaAi.coverMask(t, hand)
        hand.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
    }

    Box(Modifier.fillMaxSize()) {
        GameLayout(
            status = {
                val text = when {
                    error -> stringResource(R.string.cacheta_err_taken)
                    s.finished && together && s.winner >= 0 -> stringResource(if (s.tenCards) R.string.cacheta_went_out_ten_seat else R.string.cacheta_went_out_seat, names[s.winner])
                    s.finished && s.winner == 0 -> stringResource(if (s.tenCards) R.string.cacheta_won_ten else R.string.cacheta_won)
                    s.finished && s.winner > 0 -> stringResource(R.string.cacheta_lost, s.winner)
                    s.finished -> stringResource(R.string.cacheta_deck_over)
                    offered -> stringResource(R.string.cacheta_offer, cardLabel(s.discard.last(), french))
                    together && !acting -> stringResource(R.string.cacheta_seat_playing, names[actor])
                    !myTurn -> stringResource(R.string.cacheta_playing, actor)
                    !s.drawn -> stringResource(R.string.cacheta_prompt_draw)
                    goOut != null -> stringResource(R.string.cacheta_prompt_go_out)
                    else -> stringResource(R.string.cacheta_prompt_discard)
                }
                // Taking turns, every instruction starts with who is playing.
                StatusPill(if (together && acting && !error) names[me] + " · " + text else text, emphasized = error || offered || goOut != null || (myTurn && !s.drawn))
            },
            controls = {
                ActionBar {
                    when {
                        offered -> {
                            BigButton(stringResource(R.string.cacheta_go_out), { Cacheta.grab(s)?.let { state.value = it } }, icon = AppIcon.CHECK, compact = true)
                            BigButton(stringResource(R.string.cacheta_skip), { state.value = Cacheta.pass(s) }, style = ButtonStyle.SECONDARY, compact = true)
                        }
                        myTurn && !s.drawn -> {
                            BigButton(stringResource(R.string.cacheta_draw), { state.value = Cacheta.drawFromStock(s, Random.Default) }, icon = AppIcon.PLUS, compact = true)
                            BigButton(stringResource(R.string.cacheta_take), { Cacheta.takeDiscard(s)?.let { state.value = it } },
                                icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = s.discard.isNotEmpty(), compact = true)
                        }
                        else -> {
                            BigButton(stringResource(R.string.cacheta_discard), {
                                val card = selected ?: return@BigButton
                                val next = Cacheta.discard(s, card)
                                if (next == null) error = true else { state.value = next; selected = null }
                            }, icon = AppIcon.CHECK, enabled = myTurn && s.drawn && selected != null, compact = true)
                            BigButton(stringResource(R.string.cacheta_go_out), {
                                Cacheta.goOut(s, selected)?.let { state.value = it; selected = null }
                            }, style = if (goOut != null) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, enabled = goOut != null, compact = true)
                            BigButton(stringResource(R.string.hint), {
                                selected = CachetaAi.chooseDiscard(s, danger = false, noise = 0.0, random = Random.Default)
                            }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = myTurn && s.drawn, compact = true)
                        }
                    }
                }
            },
        ) {
            val scale = LocalSettings.current.pieceScale
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(p.felt, p.feltDark)))
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The other players, in playing order after you: only the backs of their cards.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (k in 1 until s.players) {
                        val i = (me + k) % s.players
                        SeatChip(i, names[i], s.hands[i].size, active = (!s.finished && actor == i) || s.winner == i)
                    }
                }

                // Deck, discard pile and, with wild cards, the vira and the wild card it chooses.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                    Pile(stringResource(R.string.cacheta_deck, s.stock.size), (64 * scale).dp, enabled = myTurn && !s.drawn, tag = "cacheta-deck",
                        onClick = { state.value = Cacheta.drawFromStock(s, Random.Default) }) { size, _ ->
                        if (s.stock.isNotEmpty()) drawCardBack(Offset.Zero, size, p) else drawCardSlot(Offset.Zero, size, p.cardFace)
                    }
                    Pile(stringResource(R.string.cacheta_pile), (64 * scale).dp, enabled = (myTurn && !s.drawn && s.discard.isNotEmpty()) || offered, tag = "cacheta-pile",
                        onClick = { if (offered) Cacheta.grab(s)?.let { state.value = it } else Cacheta.takeDiscard(s)?.let { state.value = it } }) { size, m ->
                        val top = s.discard.lastOrNull()
                        if (top != null) drawCard(top, Offset.Zero, size, p, m, french = french, wildMark = t.isWild(top)) else drawCardSlot(Offset.Zero, size, p.cardFace)
                    }
                    val vira = s.vira
                    if (vira != null) {
                        Pile(stringResource(R.string.cacheta_vira), (44 * scale).dp, enabled = false, tag = "cacheta-vira", onClick = {}) { size, m ->
                            drawCard(vira, Offset.Zero, size, p, m, french = french)
                        }
                        val wild = Card(vira.suit, vira.rank % 13 + 1)
                        Pile(stringResource(R.string.cacheta_wild), (44 * scale).dp, enabled = false, tag = "cacheta-wild", onClick = {}) { size, m ->
                            drawCard(wild, Offset.Zero, size, p, m, french = french, wildMark = true)
                        }
                    }
                }

                // How the winner went out.
                if (s.finished && s.groups.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (g in s.groups) MiniFan(g, (34 * scale).dp, french) { t.isWild(it) }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.cacheta_your_hand), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (inGroups.isNotEmpty()) Text(stringResource(R.string.cacheta_groups_note), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    SortButton(s, me, enabled = showHand) { state.value = it }
                }
                CardFan(
                    s.hands[me], selected, inGroups, t::isWild, french,
                    enabled = myTurn && s.drawn,
                    hidden = !showHand,
                    onTap = { card -> selected = if (selected == card) null else card },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        HandoffOverlay(handoff) {
            viewer = Cacheta.actor(state.value)
            selected = null
        }
    }
}

@Composable
private fun SortButton(s: CachetaState, seat: Int, enabled: Boolean, onSorted: (CachetaState) -> Unit) {
    BigButton(
        stringResource(if (s.sortBySuit) R.string.cacheta_sort_rank else R.string.cacheta_sort_suit),
        { onSorted(Cacheta.sorted(s, !s.sortBySuit, seat)) },
        style = ButtonStyle.SECONDARY, compact = true, enabled = enabled,
    )
}

@Composable
private fun Pile(
    label: String,
    width: Dp,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    draw: DrawScope.(Size, TextMeasurer) -> Unit,
) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(width, width * 1.42f)
                .clip(RoundedCornerShape(8.dp))
                .then(if (enabled) Modifier.border(3.dp, p.highlight, RoundedCornerShape(8.dp)) else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .testTag(tag)
                .semantics { contentDescription = label },
        ) {
            val measurer = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) { draw(size, measurer) }
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A small group of overlapping cards (how the winner went out). */
@Composable
private fun MiniFan(cards: List<Card>, cardWidth: Dp, french: Boolean, isWild: (Card) -> Boolean) {
    val p = LocalPalette.current
    val measurer = rememberTextMeasurer()
    val step = cardWidth * 0.45f
    Canvas(Modifier.size(cardWidth + step * (cards.size - 1), cardWidth * 1.42f)) {
        val cw = cardWidth.toPx()
        cards.forEachIndexed { k, c -> drawCard(c, Offset(k * step.toPx(), 0f), Size(cw, cw * 1.42f), p, measurer, french = french, shadow = k == 0, wildMark = isWild(c)) }
    }
}

/**
 * Your hand as a fan of big overlapping cards. Each card's visible left strip is its touch area;
 * the selected card rises. A green bar under the cards that already form groups.
 */
@Composable
private fun CardFan(
    cards: List<Card>,
    selected: Card?,
    inGroups: Set<Card>,
    isWild: (Card) -> Boolean,
    french: Boolean,
    enabled: Boolean,
    hidden: Boolean,
    onTap: (Card) -> Unit,
) {
    val p = LocalPalette.current
    val scale = LocalSettings.current.pieceScale
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("cacheta-hand")) {
        val n = cards.size.coerceAtLeast(1)
        val cardW = minOf((92 * scale).dp, maxWidth / 2.4f)
        val cardH = cardW * 1.42f
        val step = if (n == 1) 0.dp else minOf(cardW + 6.dp, (maxWidth - cardW) / (n - 1))
        val start = ((maxWidth - (step * (n - 1) + cardW)) / 2).coerceAtLeast(0.dp)
        val lift = 18.dp
        Box(Modifier.fillMaxWidth().height(cardH + lift + 12.dp)) {
            cards.forEachIndexed { i, card ->
                val isSel = card == selected
                val x = start + step * i
                val label = cardLabel(card, french)
                Box(
                    Modifier
                        .offset(x = x, y = if (isSel) 0.dp else lift)
                        .size(cardW, cardH)
                        .clickable(enabled = enabled, role = Role.Button) { onTap(card) }
                        .then(if (hidden) Modifier else Modifier.semantics { contentDescription = label; this.selected = isSel }),
                ) {
                    val measurer = rememberTextMeasurer()
                    // While the phone is passed to the next person, only the backs of the cards show.
                    Canvas(Modifier.fillMaxSize()) {
                        if (hidden) drawCardBack(Offset.Zero, size, p)
                        else drawCard(card, Offset.Zero, size, p, measurer, selected = isSel, french = french, wildMark = isWild(card))
                    }
                }
                if (card in inGroups && !hidden) {
                    val barWidth = (if (i == cards.lastIndex) cardW else minOf(step, cardW)) - 4.dp
                    Box(
                        Modifier
                            .offset(x = x + 2.dp, y = cardH + lift + 3.dp)
                            .size(barWidth.coerceAtLeast(4.dp), 7.dp)
                            .background(p.success, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}

/** Home screen picture: a fanned hand with a sequence and a trio, and a pile beside it. */
fun cachetaThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = with(scope) {
    // A blue cloth tells it apart from the other card games at a glance (accessible palettes keep their felt).
    val cloth = if (p.mode == io.github.fershibli.megagames.core.ColorMode.NORMAL) Color(0xFF1F4A7A) else p.felt
    drawRect(Brush.radialGradient(listOf(cloth.lighter(0.12f), cloth.darker(0.3f)), center, size.width * 0.8f))
    val cw = size.width * 0.25f
    val ch = cw * 1.42f
    drawCardBack(Offset(size.width * 0.08f, size.height * 0.07f), Size(cw * 0.8f, ch * 0.8f), p)
    drawCard(Card(Suit.CLUBS, 9), Offset(size.width * 0.08f + cw * 0.9f, size.height * 0.07f), Size(cw * 0.8f, ch * 0.8f), p, m)
    val pivot = Offset(size.width / 2, size.height * 1.15f)
    val hand = listOf(Card(Suit.HEARTS, 4), Card(Suit.HEARTS, 5), Card(Suit.HEARTS, 6), Card(Suit.SPADES, 12), Card(Suit.DIAMONDS, 12), Card(Suit.CLUBS, 12))
    hand.forEachIndexed { i, c ->
        rotate(-30f + i * 12f, pivot) {
            drawCard(c, Offset(size.width / 2 - cw / 2, size.height * 0.36f), Size(cw, ch), p, m)
        }
    }
}
