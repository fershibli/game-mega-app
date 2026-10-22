package io.github.fershibli.megagames.games.truco

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import io.github.fershibli.megagames.games.cards.SeatMark
import io.github.fershibli.megagames.games.cards.SeatName
import io.github.fershibli.megagames.games.cards.tableSeat
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.drawCard
import io.github.fershibli.megagames.ui.art.drawCardBack
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
fun TrucoGame(host: GameHost) {
    val rules = TrucoRules.of(host.variant?.key)
    LoadGame(host, TrucoState.serializer(), { newGame(rules, host) }) { state -> TrucoScreen(state, host) }
}

/** A new game for the table chosen before starting: 1 vs 1, or 2 vs 2 (4 seats). */
private fun newGame(rules: TrucoRules, host: GameHost) =
    Truco.newGame(rules, if (host.setup.seats == 4) 4 else 2, Random.Default, host.setup.together)

private fun label(c: Card, french: Boolean): String = Card.rankLabel(c.rank, french) + when (c.suit) {
    Suit.SPADES -> "♠"
    Suit.HEARTS -> "♥"
    Suit.DIAMONDS -> "♦"
    Suit.CLUBS -> "♣"
}

@Composable
private fun shoutText(shout: Shout): String = stringResource(
    when (shout) {
        Shout.TRUCO -> R.string.truco_call_truco
        Shout.SEIS -> R.string.truco_call_seis
        Shout.NOVE -> R.string.truco_call_nove
        Shout.DOZE -> R.string.truco_call_doze
        Shout.ACCEPT -> R.string.truco_shout_accept
        Shout.RUN -> R.string.truco_shout_run
        Shout.PLAY_ELEVEN -> R.string.truco_shout_play_eleven
        Shout.RUN_ELEVEN -> R.string.truco_shout_run_eleven
    },
)

@Composable
private fun TrucoScreen(state: MutableState<TrucoState>, host: GameHost) {
    val s = state.value
    if (s.seats == 0) {
        // A game saved before the table screen chose the seats: deal it now.
        LaunchedEffect(Unit) { state.value = newGame(s.rules, host) }
        return
    }
    val res = LocalResources.current
    val pace = currentPace
    val p = LocalPalette.current
    // Taking turns on this phone: every seat is a person, and the phone is passed between them.
    val together = host.setup.together
    val handoff = remember { Handoff() }
    // The seat whose cards are shown. Taking turns, nobody's until the first person says "ready".
    var viewer by remember { mutableIntStateOf(if (together) -1 else 0) }
    val me = viewer.coerceAtLeast(0)
    val showHand = viewer >= 0 && handoff.showsPrivate
    fun human(seat: Int) = together || seat == 0
    val names = seatNames(s.seats, together)
    fun teamName(team: Int): String = when {
        s.seats == 4 -> res.getString(R.string.truco_pair, "${team + 1}", "${team + 3}")
        else -> names[team]
    }
    var chosen by remember { mutableStateOf<Card?>(null) }
    val selected = chosen?.takeIf { showHand && it in s.hands[me] }
    val french = LocalGameLanguage.current == AppLanguage.FRENCH

    LaunchedEffect(s.finished) {
        if (!s.finished) return@LaunchedEffect
        pace.wait(3000)
        if (together) {
            val winner = res.getString(R.string.truco_winner, teamName(if (s.score[0] > s.score[1]) 0 else 1))
            host.finish(GameOutcome(true, winner + "\n" + res.getString(R.string.truco_final_sides, teamName(0), s.score[0], teamName(1), s.score[1]), shared = true))
        } else {
            host.finish(GameOutcome(s.score[0] > s.score[1], res.getString(R.string.truco_final, s.score[0], s.score[1])))
        }
    }

    // Computer seats act one step at a time; a finished trick stays on the table for a moment.
    LaunchedEffect(s) {
        if (s.finished || s.handOver) return@LaunchedEffect
        if (s.trickComplete) {
            pace.wait(1500)
            state.value = Truco.collect(s)
            return@LaunchedEffect
        }
        if (human(Truco.actor(s))) return@LaunchedEffect
        pace.wait(1100)
        state.value = withContext(Dispatchers.Default) { TrucoAi.step(s, host.difficulty, Random.Default) }
    }

    val actor = Truco.actor(s)
    val call = s.call
    // The person holding the phone must act now (and may see their cards).
    val acting = actor == me && human(actor) && showHand
    val myTurn = acting && call == null && !s.elevenPending

    // Taking turns: when another person must act, cover the cards and ask to pass the phone.
    LaunchedEffect(actor, s.finished) {
        if (together && !s.finished && actor >= 0 && actor != viewer) handoff.passTo(tableSeat(p, actor, names[actor]))
    }

    fun playSelected() {
        val card = selected ?: return
        Truco.play(s, card)?.let { state.value = it; chosen = null }
    }

    Box(Modifier.fillMaxSize()) {
        GameLayout(
            status = {
                val text = when {
                    s.finished && together -> stringResource(R.string.truco_final_sides, teamName(0), s.score[0], teamName(1), s.score[1])
                    s.finished -> stringResource(R.string.truco_final, s.score[0], s.score[1])
                    s.handOver && together && s.handWinner != -1 -> stringResource(R.string.truco_hand_to, teamName(s.handWinner ?: 0), s.handPoints)
                    s.handOver && s.handWinner == 0 -> stringResource(R.string.truco_hand_won, s.handPoints)
                    s.handOver && s.handWinner == 1 -> stringResource(R.string.truco_hand_lost, s.handPoints)
                    s.handOver -> stringResource(R.string.truco_hand_tied)
                    s.trickComplete -> when (val w = Truco.trickWinner(s, s.table)) {
                        -1 -> stringResource(R.string.truco_trick_tie)
                        else -> if (together) stringResource(R.string.truco_trick_to, teamName(w))
                        else if (w == 0) stringResource(R.string.truco_trick_us) else stringResource(R.string.truco_trick_them)
                    }
                    together && !acting -> stringResource(R.string.truco_playing, names[actor.coerceAtLeast(0)])
                    s.elevenPending && acting -> stringResource(R.string.truco_prompt_eleven)
                    call != null && acting -> stringResource(R.string.truco_prompt_answer, shoutText(Truco.shoutFor(call.value)), call.value, s.value)
                    call != null -> stringResource(R.string.truco_waiting_answer)
                    myTurn && selected != null -> stringResource(R.string.truco_prompt_again)
                    myTurn -> stringResource(R.string.truco_prompt_play)
                    s.seats == 4 && actor == 2 -> stringResource(R.string.truco_partner_turn)
                    else -> stringResource(R.string.computer_turn)
                }
                // Taking turns, every instruction starts with who is playing.
                StatusPill(if (together && acting) names[me] + " · " + text else text, emphasized = acting || s.handOver)
            },
            controls = {
                ActionBar {
                    when {
                        s.handOver -> BigButton(stringResource(R.string.truco_next_hand), { state.value = Truco.nextHand(s, Random.Default) },
                            icon = AppIcon.NEXT, enabled = !s.finished)
                        s.elevenPending && acting -> {
                            BigButton(stringResource(R.string.truco_eleven_play), { state.value = Truco.elevenChoice(s, true) }, icon = AppIcon.CHECK, compact = true)
                            BigButton(stringResource(R.string.truco_eleven_run), { state.value = Truco.elevenChoice(s, false) }, style = ButtonStyle.SECONDARY, compact = true)
                        }
                        call != null && acting -> {
                            BigButton(stringResource(R.string.truco_accept), { Truco.answer(s, Truco.Answer.ACCEPT)?.let { state.value = it } }, icon = AppIcon.CHECK, compact = true, bigText = true)
                            BigButton(stringResource(R.string.truco_run), { Truco.answer(s, Truco.Answer.RUN)?.let { state.value = it } }, style = ButtonStyle.SECONDARY, compact = true, bigText = true)
                            if (call.value < Truco.TARGET) {
                                BigButton(shoutText(Truco.shoutFor(Truco.nextValue(call.value))), { Truco.answer(s, Truco.Answer.RAISE)?.let { state.value = it } },
                                    style = ButtonStyle.DANGER, compact = true, bigText = true)
                            }
                        }
                        else -> {
                            BigButton(stringResource(R.string.truco_play_card), { playSelected() }, icon = AppIcon.CHECK, enabled = myTurn && selected != null, compact = true)
                            BigButton(shoutText(Truco.shoutFor(Truco.nextValue(s.value))), { Truco.raise(s)?.let { state.value = it } },
                                style = ButtonStyle.DANGER, enabled = myTurn && Truco.canRaise(s, me), compact = true, bigText = true)
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScoreBar(s, if (together) ::teamName else null)
                Table(s, french, scale, me, names, showHand)
                Hand(s.hands[me], selected, french, enabled = myTurn, hidden = !showHand, isManilha = { Truco.strength(s, it) >= 10 }) { card ->
                    if (selected == card) playSelected() else chosen = card
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        HandoffOverlay(handoff) {
            Truco.actor(state.value).takeIf { it >= 0 }?.let { viewer = it }
            chosen = null
        }
    }
}

/** Names of the seats: "Partner" and "Computer", or "Player 1"... when people take turns. */
@Composable
private fun seatNames(seats: Int, together: Boolean): List<String> =
    if (together) List(seats) { stringResource(R.string.player_number, it + 1) }
    else List(seats) { seat -> stringResource(if (seats == 4 && seat == 2) R.string.truco_partner else R.string.truco_computer) }

/** Points of each side, what the hand is worth and the tricks won so far (✓ ours, ✗ theirs, = tie). */
@Composable
private fun ScoreBar(s: TrucoState, teamName: ((Int) -> String)?) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(p.surface).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TeamLabel(s, 0, teamName?.invoke(0) ?: stringResource(R.string.truco_us), marks = teamName != null)
            Text("${s.score[0]}", color = p.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.truco_worth, s.value), color = p.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 3) {
                    val t = s.tricks.getOrNull(i)
                    val (mark, color) = when (t) {
                        0 -> "✓" to p.success
                        1 -> "✗" to p.danger
                        -1 -> "=" to p.textMuted
                        else -> "·" to p.textMuted
                    }
                    Box(Modifier.size(30.dp).border(2.dp, color, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                        // Taking turns there is no "us": the trick shows the symbol of the side that won it.
                        if (teamName != null && (t == 0 || t == 1)) SeatMark(t, size = 18.dp)
                        else Text(mark, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TeamLabel(s, 1, teamName?.invoke(1) ?: stringResource(R.string.truco_them), marks = teamName != null)
            Text("${s.score[1]}", color = p.danger, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** A side's name, after the symbols of its seats when people take turns. */
@Composable
private fun TeamLabel(s: TrucoState, team: Int, name: String, marks: Boolean) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (marks) for (seat in team until s.seats step 2) SeatMark(seat, size = 16.dp)
        Text(name, color = p.text, style = MaterialTheme.typography.titleSmall)
    }
}

/** Position of a seat around the table: the one holding the phone at the bottom, then counterclockwise. */
private enum class Side { BOTTOM, RIGHT, TOP, LEFT }

private fun side(s: TrucoState, seat: Int, me: Int): Side =
    if (s.seats == 2) (if (seat == me) Side.BOTTOM else Side.TOP) else Side.entries[(seat - me + 4) % 4]

@Composable
private fun Table(s: TrucoState, french: Boolean, scale: Float, me: Int, names: List<String>, showHand: Boolean) {
    val p = LocalPalette.current
    BoxWithConstraints(Modifier.fillMaxWidth().height((320 * scale).coerceAtMost(420f).dp).testTag("truco-table")) {
        val w = maxWidth
        val h = maxHeight
        val cardW = minOf((62 * scale).dp, w / 5.2f)
        val cardH = cardW * 1.42f
        val small = cardW * 0.62f

        // Hidden hands of the other seats (your partner's are shown in your hand of eleven).
        val showPartner = showHand && s.elevenPending && Truco.elevenTeam(s) == Truco.team(me)
        for (seat in 0 until s.seats) {
            if (seat == me) continue
            val sd = side(s, seat, me)
            val faceUp = showPartner && s.seats == 4 && seat == (me + 2) % 4
            val align = when (sd) {
                Side.TOP -> Alignment.TopCenter
                Side.LEFT -> Alignment.CenterStart
                Side.RIGHT -> Alignment.CenterEnd
                Side.BOTTOM -> Alignment.BottomCenter
            }
            val active = Truco.actor(s) == seat
            Column(Modifier.align(align), horizontalAlignment = Alignment.CenterHorizontally) {
                SeatName(seat, names[seat], active)
                val cards = s.hands[seat]
                val cw = if (faceUp) cardW * 0.8f else small
                val step = cw * (if (faceUp) 0.75f else 0.45f)
                val m = rememberTextMeasurer()
                Canvas(Modifier.size(cw + step * (cards.size - 1).coerceAtLeast(0), cw * 1.42f)) {
                    val px = cw.toPx()
                    cards.forEachIndexed { k, c ->
                        val o = Offset(k * step.toPx(), 0f)
                        if (faceUp) drawCard(c, o, Size(px, px * 1.42f), p, m, french = french) else drawCardBack(o, Size(px, px * 1.42f), p)
                    }
                }
            }
        }

        // Vira (paulista) or the fixed manilhas (mineiro), in the top-left corner.
        Column(Modifier.align(Alignment.TopStart)) {
            val vira = s.vira
            if (vira != null) {
                val m = rememberTextMeasurer()
                Text(stringResource(R.string.truco_vira), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Canvas(Modifier.size(small * 1.1f, small * 1.1f * 1.42f).semantics { contentDescription = label(vira, french) }) {
                    drawCard(vira, Offset.Zero, size, p, m, french = french)
                }
                Text(stringResource(R.string.truco_manilha, Card.rankLabel(Truco.manilhaRank(vira), french)), color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            } else {
                Text(stringResource(R.string.truco_manilhas_fixed), color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(max = w * 0.3f))
            }
        }

        // Cards of the current trick, each pushed toward the seat that played it.
        val m = rememberTextMeasurer()
        val center = Offset(w.value / 2, h.value / 2 + 6)
        val best = if (s.trickComplete) s.table.maxOf { Truco.strength(s, it.card) } else -1
        for (pl in s.table) {
            val (dx, dy) = when (side(s, pl.seat, me)) {
                Side.BOTTOM -> 0f to cardH.value * 0.38f
                Side.TOP -> 0f to -cardH.value * 0.38f
                Side.LEFT -> -cardW.value * 0.75f to 0f
                Side.RIGHT -> cardW.value * 0.75f to 0f
            }
            val x = (center.x + dx - cardW.value / 2).dp
            val y = (center.y + dy - cardH.value / 2).dp
            val winning = s.trickComplete && Truco.strength(s, pl.card) == best
            Canvas(Modifier.offset(x, y).size(cardW, cardH).semantics { contentDescription = label(pl.card, french) }) {
                drawCard(pl.card, Offset.Zero, size, p, m, french = french, selected = winning, wildMark = Truco.strength(s, pl.card) >= 10)
            }
        }

        // Speech balloon of the last call or answer.
        val shout = s.shout
        if (shout != null) {
            val align = when (side(s, shout.seat, me)) {
                Side.BOTTOM -> Alignment.BottomCenter
                Side.TOP -> Alignment.TopCenter
                Side.LEFT -> Alignment.CenterStart
                Side.RIGHT -> Alignment.CenterEnd
            }
            // Below the other seats' cards (clear of the cards on the table), just over your hand.
            val nudge = when (side(s, shout.seat, me)) {
                Side.TOP -> Modifier.offset(y = small * 1.42f + 22.dp)
                Side.LEFT, Side.RIGHT -> Modifier.offset(y = small * 1.42f + 30.dp)
                Side.BOTTOM -> Modifier.offset(y = (-8).dp)
            }
            Balloon(shoutText(shout.shout), Modifier.align(align).then(nudge))
        }
    }
}

@Composable
private fun Balloon(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Color(0xFFFFF4C2))
            .border(3.dp, p.danger, shape)
            .padding(horizontal = 22.dp, vertical = 10.dp)
            .testTag("truco-balloon"),
    ) {
        Text(text, color = Color(0xFF7A1010), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
    }
}

/** Your three cards, big, manilhas marked with a star. Tap one to choose it, tap it again to play it. */
@Composable
private fun Hand(cards: List<Card>, selected: Card?, french: Boolean, enabled: Boolean, hidden: Boolean, isManilha: (Card) -> Boolean, onTap: (Card) -> Unit) {
    val p = LocalPalette.current
    val scale = LocalSettings.current.pieceScale
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("truco-hand"), contentAlignment = Alignment.Center) {
        val cardW: Dp = minOf((96 * scale).dp, maxWidth / 3.4f)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            // While the phone is passed to the next person, only the backs of the cards show.
            if (hidden) {
                repeat(cards.size) {
                    Canvas(Modifier.padding(top = 18.dp).size(cardW, cardW * 1.42f)) { drawCardBack(Offset.Zero, size, p) }
                }
                return@Row
            }
            for (card in cards) {
                val isSel = card == selected
                val text = label(card, french)
                val m = rememberTextMeasurer()
                Box(
                    Modifier
                        .padding(top = if (isSel) 0.dp else 18.dp, bottom = if (isSel) 18.dp else 0.dp)
                        .size(cardW, cardW * 1.42f)
                        .clickable(enabled = enabled, role = Role.Button) { onTap(card) }
                        .semantics { contentDescription = text; this.selected = isSel },
                ) {
                    Canvas(Modifier.fillMaxSize()) { drawCard(card, Offset.Zero, size, p, m, selected = isSel, french = french, wildMark = isManilha(card)) }
                }
            }
        }
    }
}

/** Home screen picture: the three strongest cards fanned under a speech balloon. */
fun trucoThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = with(scope) {
    drawRect(Brush.radialGradient(listOf(p.felt.lighter(0.12f), p.feltDark.darker(0.2f)), center, size.width * 0.8f))
    val cw = size.width * 0.3f
    val ch = cw * 1.42f
    val pivot = Offset(size.width / 2, size.height * 1.1f)
    val cards = listOf(Card(Suit.DIAMONDS, 7), Card(Suit.HEARTS, 7), Card(Suit.CLUBS, 4))
    cards.forEachIndexed { i, c ->
        rotate(-22f + i * 22f, pivot) { drawCard(c, Offset(size.width / 2 - cw / 2, size.height * 0.4f), Size(cw, ch), p, m) }
    }
    // Speech balloon with three bold "shout" strokes, no text.
    val bw = size.width * 0.56f
    val bh = size.height * 0.26f
    val bx = size.width * 0.22f
    val by = size.height * 0.06f
    drawRoundRect(Color(0xFFFFF4C2), Offset(bx, by), Size(bw, bh), CornerRadius(bh / 2))
    val tail = Path().apply { moveTo(bx + bw * 0.3f, by + bh * 0.9f); lineTo(bx + bw * 0.22f, by + bh * 1.35f); lineTo(bx + bw * 0.45f, by + bh * 0.9f); close() }
    drawPath(tail, Color(0xFFFFF4C2))
    for (k in 0 until 3) {
        val x = bx + bw * (0.3f + k * 0.2f)
        drawLine(p.danger, Offset(x, by + bh * 0.22f), Offset(x, by + bh * 0.58f), bh * 0.13f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawCircle(p.danger, bh * 0.07f, Offset(x, by + bh * 0.78f))
    }
}
