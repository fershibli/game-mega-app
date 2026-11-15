package io.github.fershibli.megagames.games.sueca

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.tricks.AceTen
import io.github.fershibli.megagames.games.tricks.CornerCard
import io.github.fershibli.megagames.games.tricks.HandCards
import io.github.fershibli.megagames.games.tricks.ResultPanel
import io.github.fershibli.megagames.games.tricks.ScoreBar
import io.github.fershibli.megagames.games.tricks.ScoreCell
import io.github.fershibli.megagames.games.tricks.SeatTable
import io.github.fershibli.megagames.games.tricks.TableColumn
import io.github.fershibli.megagames.games.tricks.TableCover
import io.github.fershibli.megagames.games.tricks.Tricks
import io.github.fershibli.megagames.games.tricks.rememberTableView
import io.github.fershibli.megagames.games.tricks.seatNames
import io.github.fershibli.megagames.games.tricks.suitSymbol
import io.github.fershibli.megagames.games.tricks.teamColors
import io.github.fershibli.megagames.games.tricks.teamNames
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.drawCard
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.art.suitColor
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

private const val AI_PAUSE = 900L
private const val TRICK_PAUSE = 1600L
private const val END_PAUSE = 2500L

@Composable
fun SuecaGame(host: GameHost) {
    LoadGame(host, SuecaState.serializer(), { Sueca.newGame(Random.Default) }) { state -> SuecaScreen(state, host) }
}

@Composable
private fun SuecaScreen(state: MutableState<SuecaState>, host: GameHost) {
    val s = state.value
    val res = LocalResources.current
    val pace = currentPace
    val together = host.setup.together
    val french = LocalGameLanguage.current == AppLanguage.FRENCH
    val names = seatNames(Sueca.SEATS, together, clockwise = false, pairs = true)
    val teams = teamNames(Sueca.SEATS, together, names)
    val colors = teamColors(Sueca.SEATS)
    val actor = Sueca.actor(s)
    fun human(seat: Int) = together || seat == 0
    val view = rememberTableView(together, actor) { Seat(names[it], colors[it]) }
    var chosen by remember { mutableStateOf<Card?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val myTurn = actor >= 0 && human(actor) && actor == view.viewer && view.showsHand(actor)
    val legal = if (myTurn) Sueca.legal(s).toSet() else emptySet()
    val selected = chosen?.takeIf { it in legal }

    LaunchedEffect(s.finished) {
        if (!s.finished) return@LaunchedEffect
        pace.wait(END_PAUSE)
        val final = res.getString(R.string.tricks_final, teams[0], s.games[0], teams[1], s.games[1])
        val winner = s.result?.team ?: 0
        if (together) host.finish(GameOutcome(true, res.getString(R.string.tricks_winner, teams[winner]) + "\n" + final, shared = true))
        else host.finish(GameOutcome(winner == 0, final))
    }

    // Computer seats play one card at a time; a full trick stays on the table for a moment.
    LaunchedEffect(s) {
        if (s.finished || s.handOver || !s.started) return@LaunchedEffect
        if (s.trickComplete) {
            pace.wait(TRICK_PAUSE)
            state.value = Sueca.collect(s)
            return@LaunchedEffect
        }
        if (human(actor)) return@LaunchedEffect
        pace.wait(AI_PAUSE)
        state.value = withContext(Dispatchers.Default) { SuecaAi.step(s, host.difficulty, Random.Default) }
    }

    fun play(card: Card) {
        Sueca.play(s, card)?.let { state.value = it; chosen = null; notice = null }
    }

    Box(Modifier.fillMaxSize()) {
        GameLayout(
            status = {
                val result = s.result
                val text = when {
                    s.finished -> stringResource(R.string.tricks_final, teams[0], s.games[0], teams[1], s.games[1])
                    result != null && result.team < 0 -> stringResource(R.string.tricks_hand_tie)
                    result != null && result.bandeira -> stringResource(R.string.sueca_bandeira, teams[result.team])
                    result != null -> stringResource(R.string.tricks_hand_won, teams[result.team])
                    s.trickComplete -> {
                        val w = Sueca.winner(s).seat
                        if (!together && w == 0) stringResource(R.string.tricks_trick_you) else stringResource(R.string.tricks_trick_taken, names[w])
                    }
                    myTurn && notice != null -> notice!!
                    myTurn && selected != null -> stringResource(R.string.tricks_prompt_again)
                    myTurn && together -> stringResource(R.string.tricks_player_turn, names[actor])
                    myTurn -> stringResource(R.string.tricks_prompt_play)
                    !together && actor == 2 -> stringResource(R.string.tricks_partner_turn)
                    together && actor >= 0 -> stringResource(R.string.tricks_player_turn, names[actor])
                    else -> stringResource(R.string.computer_turn)
                }
                StatusPill(text, emphasized = myTurn || s.handOver)
            },
            controls = {
                ActionBar {
                    if (s.handOver) {
                        BigButton(stringResource(R.string.tricks_next_hand), { state.value = Sueca.nextHand(s, Random.Default) }, icon = AppIcon.NEXT, enabled = !s.finished)
                    } else {
                        BigButton(stringResource(R.string.tricks_play_card), { selected?.let { play(it) } }, icon = AppIcon.CHECK, enabled = myTurn && selected != null, compact = true)
                        BigButton(stringResource(R.string.hint), { chosen = SuecaAi.sensible(s); notice = null }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = myTurn, compact = true)
                    }
                }
            },
        ) {
            val p = LocalPalette.current
            val scale = LocalSettings.current.pieceScale
            TableColumn {
                ScoreBar {
                    ScoreCell(teams[0] + " · " + stringResource(R.string.tricks_games), "${s.games[0]}", p.primary)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.tricks_trumps), color = p.text, style = MaterialTheme.typography.titleSmall)
                        Text(suitSymbol(s.trump), color = suitColor(s.trump, p), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        val won = s.winners.count { Sueca.team(it) == 0 }
                        Text(stringResource(R.string.tricks_tricks, won, s.winners.size - won), color = p.textMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                    ScoreCell(teams[1] + " · " + stringResource(R.string.tricks_games), "${s.games[1]}", p.danger)
                }
                val result = s.result
                if (result != null) {
                    val lines = mutableListOf(teams[0] to "${result.points[0]}", teams[1] to "${result.points[1]}")
                    if (result.team >= 0) lines += stringResource(R.string.tricks_games_won) to "+${result.games}"
                    else lines += stringResource(R.string.sueca_carry) to "+${s.carry}"
                    ResultPanel(stringResource(R.string.tricks_card_points), lines)
                } else {
                    val trumpCard = s.trumpCard
                    SeatTable(
                        seats = Sueca.SEATS, viewer = view.viewer, clockwise = false, names = names, counts = s.hands.map { it.size },
                        active = actor, trick = s.trick, winning = if (s.trickComplete) Sueca.winner(s) else null, french = french,
                        tag = "sueca-table", height = (300 * scale).coerceIn(280f, 400f).dp, teamColors = colors, star = { it.suit == s.trump },
                        corner = if (trumpCard != null && trumpCard in s.hands[s.dealer]) ({
                            CornerCard(stringResource(R.string.tricks_trumps), trumpCard, french, note = names[s.dealer])
                        }) else null,
                    )
                }
                val viewer = view.viewer
                if (viewer >= 0 && view.showsHand(actor) && !s.handOver) {
                    HandCards(
                        Tricks.sorted(s.hands[viewer], AceTen::power, s.trump), french, enabled = myTurn,
                        onTap = { card ->
                            when {
                                card !in legal -> notice = res.getString(R.string.tricks_follow_suit, suitSymbol(s.trick.first().card.suit))
                                card == selected -> play(card)
                                else -> { chosen = card; notice = null }
                            }
                        },
                        selected = setOfNotNull(selected), playable = if (myTurn) legal else null,
                        star = { it.suit == s.trump }, tag = "sueca-hand",
                    )
                }
            }
        }
        TableCover(view)
    }
}

/** Home screen picture: a trick of four cards crossing on the felt, the trump ace on top. */
fun suecaThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = with(scope) {
    drawRect(Brush.radialGradient(listOf(p.felt.lighter(0.12f), p.feltDark.darker(0.2f)), center, size.width * 0.8f))
    val cw = size.width * 0.3f
    val ch = cw * 1.42f
    val c = center
    val cards = listOf(Card(Suit.CLUBS, 13) to Offset(0f, -ch * 0.32f), Card(Suit.DIAMONDS, 7) to Offset(cw * 0.6f, 0f),
        Card(Suit.SPADES, 11) to Offset(-cw * 0.6f, 0f), Card(Suit.HEARTS, 1) to Offset(0f, ch * 0.3f))
    cards.forEachIndexed { i, (card, d) ->
        rotate(-12f + i * 8f, c + d) { drawCard(card, Offset(c.x + d.x - cw / 2, c.y + d.y - ch / 2), Size(cw, ch), p, m, selected = i == 3) }
    }
}
