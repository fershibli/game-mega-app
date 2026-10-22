package io.github.fershibli.megagames.games.escova

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.tricks.AceTen
import io.github.fershibli.megagames.games.tricks.HandCards
import io.github.fershibli.megagames.games.tricks.ResultPanel
import io.github.fershibli.megagames.games.tricks.ScoreBar
import io.github.fershibli.megagames.games.tricks.ScoreCell
import io.github.fershibli.megagames.games.tricks.SeatTable
import io.github.fershibli.megagames.games.tricks.TableColumn
import io.github.fershibli.megagames.games.tricks.TableCover
import io.github.fershibli.megagames.games.tricks.Tricks
import io.github.fershibli.megagames.games.tricks.cardLabel
import io.github.fershibli.megagames.games.tricks.rememberTableView
import io.github.fershibli.megagames.games.tricks.seatNames
import io.github.fershibli.megagames.games.tricks.tableCard
import io.github.fershibli.megagames.games.tricks.teamColors
import io.github.fershibli.megagames.games.tricks.teamNames
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.drawCard
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.Seat
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

private const val AI_PAUSE = 1100L
private const val END_PAUSE = 2500L

@Composable
fun EscovaGame(host: GameHost) {
    val seats = host.setup.seats.takeIf { it == 2 || it == 4 } ?: 2
    LoadGame(host, EscovaState.serializer(), { Escova.newGame(seats, Random.Default) }) { state ->
        if (!state.value.started) LaunchedEffect(Unit) { state.value = Escova.newGame(seats, Random.Default) } else EscovaScreen(state, host)
    }
}

/** "7♦" with its value when it differs from the number on the card: "K♣ (10)". */
private fun named(c: Card, french: Boolean) = cardLabel(c, french) + if (c.rank > 10) " (${Escova.value(c)})" else ""

@Composable
private fun EscovaScreen(state: MutableState<EscovaState>, host: GameHost) {
    val s = state.value
    val res = LocalResources.current
    val pace = currentPace
    val together = host.setup.together
    val french = LocalGameLanguage.current == AppLanguage.FRENCH
    val names = seatNames(s.seats, together, clockwise = false, pairs = true)
    val teams = teamNames(s.seats, together, names)
    val colors = teamColors(s.seats)
    val actor = Escova.actor(s)
    fun human(seat: Int) = together || seat == 0
    val view = rememberTableView(together, actor) { Seat(names[it], colors[it]) }
    var card by remember { mutableStateOf<Card?>(null) }
    var picked by remember { mutableStateOf<Set<Card>>(emptySet()) }
    var notice by remember { mutableStateOf<String?>(null) }
    val mine = actor >= 0 && human(actor) && actor == view.viewer && view.showsHand(actor)
    val handCard = card?.takeIf { mine && it in s.hands[actor] }
    val taken = picked.filter { it in s.table }

    LaunchedEffect(s.finished) {
        if (!s.finished) return@LaunchedEffect
        pace.wait(END_PAUSE)
        val final = res.getString(R.string.escova_final, teams[0], s.scores[0], teams[1], s.scores[1])
        val winner = if (s.scores[0] > s.scores[1]) 0 else 1
        if (together) host.finish(GameOutcome(true, res.getString(R.string.tricks_winner, teams[winner]) + "\n" + final, shared = true))
        else host.finish(GameOutcome(winner == 0, final))
    }

    LaunchedEffect(s) {
        if (s.finished || s.handOver || actor < 0 || human(actor)) return@LaunchedEffect
        pace.wait(AI_PAUSE)
        state.value = withContext(Dispatchers.Default) { EscovaAi.step(s, host.difficulty, Random.Default) }
    }

    fun clear() {
        card = null
        picked = emptySet()
        notice = null
    }

    fun submit() {
        val c = handCard ?: return
        when (Escova.check(s, c, taken)) {
            Escova.Problem.MUST_TAKE -> notice = res.getString(R.string.escova_must_take)
            Escova.Problem.NOT_FIFTEEN -> notice = res.getString(R.string.escova_not_fifteen, Escova.value(c) + taken.sumOf { Escova.value(it) })
            null -> Escova.play(s, c, taken)?.let { state.value = it; clear() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        GameLayout(
            status = {
                val last = s.last
                val text = when {
                    s.finished -> stringResource(R.string.escova_final, teams[0], s.scores[0], teams[1], s.scores[1])
                    s.handOver -> stringResource(R.string.escova_hand_over)
                    mine && notice != null -> notice!!
                    mine && handCard != null && taken.isEmpty() -> stringResource(R.string.escova_prompt_table)
                    mine && handCard != null -> stringResource(R.string.escova_prompt_take)
                    mine && last == null && s.opening > 0 -> stringResource(R.string.escova_opening, names[s.dealer])
                    mine && together -> names[actor] + ": " + stringResource(R.string.escova_prompt_card)
                    mine -> stringResource(R.string.escova_prompt_card)
                    !together && s.seats == 4 && actor == 2 -> stringResource(R.string.tricks_partner_turn)
                    together && actor >= 0 -> stringResource(R.string.tricks_player_turn, names[actor])
                    else -> stringResource(R.string.computer_turn)
                }
                StatusPill(text, emphasized = mine || s.handOver)
            },
            controls = {
                ActionBar {
                    if (s.handOver) {
                        BigButton(stringResource(R.string.tricks_next_hand), { state.value = Escova.nextHand(s, Random.Default); clear() }, icon = AppIcon.NEXT, enabled = !s.finished)
                    } else {
                        BigButton(
                            stringResource(if (taken.isEmpty()) R.string.escova_lay else R.string.escova_take), { submit() },
                            icon = AppIcon.CHECK, enabled = mine && handCard != null, compact = true,
                        )
                        BigButton(stringResource(R.string.escova_clear), { clear() }, icon = AppIcon.CLOSE, style = ButtonStyle.SECONDARY,
                            enabled = mine && (handCard != null || picked.isNotEmpty()), compact = true)
                        BigButton(stringResource(R.string.hint), {
                            val m = EscovaAi.sensible(s)
                            card = m.card
                            picked = m.taken.toSet()
                            notice = null
                        }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = mine, compact = true)
                    }
                }
            },
        ) {
            val p = LocalPalette.current
            val scale = LocalSettings.current.pieceScale
            TableColumn {
                ScoreBar {
                    ScoreCell(teams[0], "${s.scores[0]}", p.primary)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.escova_goal), color = p.text, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.escova_stock, s.stock.size), color = p.textMuted, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.escova_escovas, s.escovas[0], s.escovas[1]), color = p.textMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                    ScoreCell(teams[1], "${s.scores[1]}", p.danger)
                }
                val result = s.result
                if (result != null) {
                    fun to(team: Int) = if (team < 0) "" else " → " + teams[team]
                    val t = result.tallies
                    ResultPanel(
                        stringResource(R.string.escova_hand_points, teams[0], result.points[0], teams[1], result.points[1]),
                        listOf(
                            stringResource(R.string.escova_line_escovas) to "${t[0].escovas} – ${t[1].escovas}",
                            stringResource(R.string.escova_line_cards) to "${t[0].cards} – ${t[1].cards}${to(result.mostCards)}",
                            stringResource(R.string.escova_line_diamonds) to "${t[0].diamonds} – ${t[1].diamonds}${to(result.mostDiamonds)}",
                            stringResource(R.string.escova_line_sete_belo) to teams[result.seteBelo],
                            stringResource(R.string.escova_line_sevens) to "${t[0].sevens} – ${t[1].sevens}${to(result.mostSevens)}",
                        ),
                    )
                } else {
                    SeatTable(
                        seats = s.seats, viewer = view.viewer, clockwise = false, names = names, counts = s.hands.map { it.size },
                        active = actor, trick = emptyList(), winning = null, french = french, tag = "escova-table",
                        height = (if (s.seats == 2) 300 * scale else 330 * scale).coerceIn(280f, 440f).dp, teamColors = colors,
                    ) {
                        TableCards(s.table, picked, french, enabled = mine && handCard != null) { c ->
                            picked = if (c in picked) picked - c else picked + c
                            notice = null
                        }
                    }
                    LastMove(s, names, french)
                }
                if (handCard != null) SumBar(handCard, taken)
                val viewer = view.viewer
                if (viewer >= 0 && view.showsHand(actor) && !s.handOver) {
                    HandCards(
                        Tricks.sorted(s.hands[viewer], AceTen::power), french, enabled = mine,
                        onTap = { c ->
                            if (c == handCard) submit() else {
                                card = c
                                notice = null
                            }
                        },
                        selected = setOfNotNull(handCard), badge = { Escova.value(it).toString() }, tag = "escova-hand",
                    )
                }
            }
        }
        TableCover(view)
    }
}

/** The cards face up in the middle of the table, each with its value; tap to add it to the sum. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableCards(table: List<Card>, picked: Set<Card>, french: Boolean, enabled: Boolean, onTap: (Card) -> Unit) {
    val p = LocalPalette.current
    val scale = LocalSettings.current.pieceScale
    BoxWithConstraints(Modifier.testTag("escova-middle"), contentAlignment = Alignment.Center) {
        val perRow = if (table.size <= 4) 4 else (table.size + 1) / 2
        val cardW = minOf((62 * scale).dp, (maxWidth - 6.dp * (perRow - 1)) / perRow.coerceIn(4, 6))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in Tricks.sorted(table, AceTen::power)) {
                val isSel = c in picked
                val label = named(c, french)
                val m = rememberTextMeasurer()
                Box(
                    Modifier
                        .padding(top = if (isSel) 0.dp else 8.dp, bottom = if (isSel) 8.dp else 0.dp)
                        .size(cardW, cardW * 1.42f)
                        .clickable(enabled = enabled, role = Role.Checkbox) { onTap(c) }
                        .semantics { contentDescription = label; this.selected = isSel },
                ) {
                    Canvas(Modifier.fillMaxSize()) { tableCard(c, Offset.Zero, size, p, m, french, selected = isSel, badge = Escova.value(c).toString()) }
                }
            }
        }
    }
}

/** Big sum help: the values chosen and their total, green at exactly 15. */
@Composable
private fun SumBar(card: Card, taken: List<Card>) {
    val p = LocalPalette.current
    val total = Escova.value(card) + taken.sumOf { Escova.value(it) }
    val good = total == Escova.SUM && taken.isNotEmpty()
    val color = if (good) p.success else p.text
    val parts = (listOf(card) + taken).joinToString(" + ") { Escova.value(it).toString() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(horizontal = 12.dp, vertical = 6.dp).testTag("escova-sum"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$parts = $total" + if (good) " ✓" else "", color = color, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        val note = when {
            taken.isEmpty() -> null
            good -> stringResource(R.string.escova_fifteen)
            total < Escova.SUM -> stringResource(R.string.escova_need_more, Escova.SUM - total)
            else -> stringResource(R.string.escova_too_much)
        }
        if (note != null) Text(note, color = color, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

/** What the last player did: "Left took 5♦ 3♣ with 7♠", or "laid 4♥", or "Escova!". */
@Composable
private fun LastMove(s: EscovaState, names: List<String>, french: Boolean) {
    val m = s.last ?: return
    val text = when {
        m.escova -> stringResource(R.string.escova_swept, names[m.seat], named(m.card, french))
        m.taken.isNotEmpty() -> stringResource(R.string.escova_took, names[m.seat], m.taken.joinToString(" ") { named(it, french) }, named(m.card, french))
        else -> stringResource(R.string.escova_laid, names[m.seat], named(m.card, french))
    }
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            text, Modifier.clip(RoundedCornerShape(12.dp)).background(if (m.escova) p.highlight else Color.Black.copy(alpha = 0.3f)).padding(horizontal = 12.dp, vertical = 4.dp),
            color = if (m.escova) Color.Black else Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = if (m.escova) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

/** Home screen picture: a 7 and an 8-valued jack taking cards from the table, with the sete belo. */
fun escovaThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = with(scope) {
    drawRect(Brush.radialGradient(listOf(p.felt.lighter(0.12f), p.feltDark.darker(0.2f)), center, size.width * 0.8f))
    val cw = size.width * 0.26f
    val ch = cw * 1.42f
    val y = size.height * 0.14f
    val table = listOf(Card(Suit.DIAMONDS, 7), Card(Suit.CLUBS, 3), Card(Suit.HEARTS, 5))
    table.forEachIndexed { i, c -> drawCard(c, Offset(size.width * 0.06f + i * (cw + size.width * 0.04f), y), Size(cw, ch), p, m, selected = i > 0) }
    rotate(-10f, Offset(size.width / 2, size.height * 0.8f)) {
        drawCard(Card(Suit.SPADES, 7), Offset(size.width / 2 - cw / 2, size.height * 0.56f), Size(cw, ch), p, m)
    }
}
