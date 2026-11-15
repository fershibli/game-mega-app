package io.github.fershibli.megagames.games.hearts

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.games.cards.Card
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.tricks.HandCards
import io.github.fershibli.megagames.games.tricks.ResultPanel
import io.github.fershibli.megagames.games.tricks.ScoreBar
import io.github.fershibli.megagames.games.tricks.SeatTable
import io.github.fershibli.megagames.games.tricks.TableColumn
import io.github.fershibli.megagames.games.tricks.TableCover
import io.github.fershibli.megagames.games.tricks.Tricks
import io.github.fershibli.megagames.games.tricks.rememberTableView
import io.github.fershibli.megagames.games.tricks.seatNames
import io.github.fershibli.megagames.games.tricks.suitSymbol
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

private const val AI_PAUSE = 800L
private const val PASS_PAUSE = 400L
private const val TRICK_PAUSE = 1600L
private const val END_PAUSE = 2500L

@Composable
fun HeartsGame(host: GameHost) {
    LoadGame(host, HeartsState.serializer(), { Hearts.newGame(Random.Default) }) { state -> HeartsScreen(state, host) }
}

@Composable
private fun HeartsScreen(state: MutableState<HeartsState>, host: GameHost) {
    val s = state.value
    val res = LocalResources.current
    val pace = currentPace
    val together = host.setup.together
    val french = LocalGameLanguage.current == AppLanguage.FRENCH
    val names = seatNames(Hearts.SEATS, together, clockwise = true, pairs = false)
    val actor = Hearts.actor(s)
    fun human(seat: Int) = together || seat == 0
    val view = rememberTableView(together, actor) { Seat(names[it]) }
    var chosen by remember { mutableStateOf<Set<Card>>(emptySet()) }
    var notice by remember { mutableStateOf<String?>(null) }
    val mine = actor >= 0 && human(actor) && actor == view.viewer && view.showsHand(actor)
    val passing = s.passingPhase
    val legal = if (mine && !passing) Hearts.legal(s).toSet() else emptySet()
    val selectedCard = chosen.singleOrNull()?.takeIf { it in legal }
    val passPick = if (mine && passing) chosen.filter { it in s.hands[actor] }.toSet() else emptySet()

    LaunchedEffect(s.finished) {
        if (!s.finished) return@LaunchedEffect
        pace.wait(END_PAUSE)
        val leaders = Hearts.leaders(s)
        val final = s.scores.indices.joinToString(" · ") { "${names[it]}: ${s.scores[it]}" }
        val winners = res.getString(R.string.hearts_lowest_wins, leaders.joinToString(", ") { names[it] })
        if (together) host.finish(GameOutcome(true, winners + "\n" + final, shared = true))
        else host.finish(GameOutcome(0 in leaders, winners + "\n" + final))
    }

    LaunchedEffect(s) {
        if (s.finished || s.handOver || !s.started) return@LaunchedEffect
        if (s.trickComplete) {
            pace.wait(TRICK_PAUSE)
            state.value = Hearts.collect(s)
            return@LaunchedEffect
        }
        if (actor < 0 || human(actor)) return@LaunchedEffect
        pace.wait(if (passing) PASS_PAUSE else AI_PAUSE)
        state.value = withContext(Dispatchers.Default) { HeartsAi.step(s, host.difficulty, Random.Default) }
    }

    fun reason(): String {
        val first = s.tricks.isEmpty()
        return when {
            first && s.trick.isEmpty() -> res.getString(R.string.hearts_lead_two)
            s.trick.isEmpty() -> res.getString(R.string.hearts_not_broken)
            s.hands[actor].any { it.suit == s.trick.first().card.suit } -> res.getString(R.string.tricks_follow_suit, suitSymbol(s.trick.first().card.suit))
            else -> res.getString(R.string.hearts_first_trick)
        }
    }

    fun play(card: Card) {
        Hearts.play(s, card)?.let { state.value = it; chosen = emptySet(); notice = null }
    }

    Box(Modifier.fillMaxSize()) {
        GameLayout(
            status = {
                val pts = s.handPoints
                val text = when {
                    s.finished -> stringResource(R.string.hearts_lowest_wins, Hearts.leaders(s).joinToString(", ") { names[it] })
                    pts != null && s.moon >= 0 -> stringResource(R.string.hearts_moon, names[s.moon])
                    pts != null -> stringResource(R.string.hearts_hand_over)
                    s.trickComplete -> {
                        val w = Hearts.winner(s.trick).seat
                        if (!together && w == 0) stringResource(R.string.tricks_trick_you) else stringResource(R.string.tricks_trick_taken, names[w])
                    }
                    mine && passing -> {
                        val dir = when (s.direction) {
                            PassDirection.LEFT -> R.string.hearts_pass_left
                            PassDirection.RIGHT -> R.string.hearts_pass_right
                            else -> R.string.hearts_pass_across
                        }
                        (if (together) names[actor] + ": " else "") + stringResource(dir)
                    }
                    passing -> stringResource(R.string.hearts_waiting_pass)
                    mine && notice != null -> notice!!
                    mine && selectedCard != null -> stringResource(R.string.tricks_prompt_again)
                    mine && s.tricks.isEmpty() && s.trick.isEmpty() -> (if (together) names[actor] + ": " else "") + stringResource(R.string.hearts_lead_two)
                    mine && together -> stringResource(R.string.tricks_player_turn, names[actor])
                    mine -> stringResource(R.string.tricks_prompt_play)
                    together && actor >= 0 -> stringResource(R.string.tricks_player_turn, names[actor])
                    else -> stringResource(R.string.computer_turn)
                }
                StatusPill(text, emphasized = mine || s.handOver)
            },
            controls = {
                ActionBar {
                    when {
                        s.handOver -> BigButton(stringResource(R.string.tricks_next_hand), { state.value = Hearts.nextHand(s, Random.Default) }, icon = AppIcon.NEXT, enabled = !s.finished)
                        passing -> {
                            BigButton(stringResource(R.string.hearts_pass_button), {
                                Hearts.choosePass(s, actor, passPick.toList())?.let { state.value = it; chosen = emptySet() }
                            }, icon = AppIcon.CHECK, enabled = mine && passPick.size == Hearts.PASS, compact = true)
                            BigButton(stringResource(R.string.hint), {
                                chosen = HeartsAi.choosePass(s, actor, Difficulty.MEDIUM, Random.Default).toSet()
                            }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = mine, compact = true)
                        }
                        else -> {
                            BigButton(stringResource(R.string.tricks_play_card), { selectedCard?.let { play(it) } }, icon = AppIcon.CHECK, enabled = mine && selectedCard != null, compact = true)
                            BigButton(stringResource(R.string.hint), { chosen = setOf(HeartsAi.sensible(s)); notice = null }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = mine, compact = true)
                        }
                    }
                }
            },
        ) {
            val p = LocalPalette.current
            val scale = LocalSettings.current.pieceScale
            TableColumn(wood = true) {
                val taken = Hearts.taken(s)
                ScoreBar {
                    for (seat in 0 until Hearts.SEATS) {
                        val active = actor == seat
                        Column(
                            Modifier.then(if (active) Modifier.border(3.dp, p.highlight, RoundedCornerShape(10.dp)) else Modifier).padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(names[seat], color = p.text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text("${s.scores[seat]}", color = if (seat == 0 && !together) p.primary else p.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(if (s.handOver) "+${s.handPoints!![seat]}" else "♥ ${taken[seat]}", color = p.danger, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                val pts = s.handPoints
                if (pts != null) {
                    ResultPanel(stringResource(R.string.hearts_hand_points), (0 until Hearts.SEATS).map { names[it] to "+${pts[it]} → ${s.scores[it]}" })
                } else {
                    SeatTable(
                        seats = Hearts.SEATS, viewer = view.viewer, clockwise = true, names = names, counts = s.hands.map { it.size },
                        active = actor, trick = s.trick, winning = if (s.trickComplete) Hearts.winner(s.trick) else null, french = french,
                        tag = "hearts-table", height = (280 * scale).coerceIn(260f, 380f).dp,
                        star = { Hearts.points(it) > 0 && it.suit == Suit.SPADES },
                    )
                }
                val viewer = view.viewer
                if (viewer >= 0 && view.showsHand(actor) && !s.handOver) {
                    val hand = Tricks.sorted(s.hands[viewer], Hearts::power)
                    HandCards(
                        hand, french, enabled = mine,
                        onTap = { card ->
                            if (passing) {
                                chosen = if (card in passPick) passPick - card else if (passPick.size < Hearts.PASS) passPick + card else passPick
                            } else when {
                                card !in legal -> notice = reason()
                                card == selectedCard -> play(card)
                                else -> { chosen = setOf(card); notice = null }
                            }
                        },
                        selected = if (passing) passPick else setOfNotNull(selectedCard),
                        playable = if (mine && !passing) legal else null,
                        marked = if (s.tricks.isEmpty()) s.received[viewer].toSet() else emptySet(),
                        star = { it == Hearts.QUEEN_OF_SPADES },
                        tag = "hearts-hand",
                    )
                }
            }
        }
        TableCover(view)
    }
}

/** Home screen picture: on a wooden table, the queen of spades between two hearts, under a big heart. */
fun heartsThumbnail(scope: DrawScope, p: Palette, m: TextMeasurer) = with(scope) {
    drawRect(Brush.verticalGradient(listOf(p.wood.lighter(0.1f), p.woodDark)))
    val w = size.width
    // A big soft heart behind the cards.
    val heart = Path().apply {
        moveTo(w * 0.5f, w * 0.42f)
        cubicTo(w * 0.5f, w * 0.3f, w * 0.3f, w * 0.12f, w * 0.18f, w * 0.26f)
        cubicTo(w * 0.06f, w * 0.42f, w * 0.3f, w * 0.62f, w * 0.5f, w * 0.78f)
        cubicTo(w * 0.7f, w * 0.62f, w * 0.94f, w * 0.42f, w * 0.82f, w * 0.26f)
        cubicTo(w * 0.7f, w * 0.12f, w * 0.5f, w * 0.3f, w * 0.5f, w * 0.42f)
        close()
    }
    drawPath(heart, Color(0xFFD9534F).copy(alpha = 0.45f))
    val cw = w * 0.3f
    val ch = cw * 1.42f
    val pivot = Offset(w / 2, size.height * 1.15f)
    val cards = listOf(Card(Suit.HEARTS, 1), Card(Suit.SPADES, 12), Card(Suit.HEARTS, 10))
    cards.forEachIndexed { i, c ->
        rotate(-20f + i * 20f, pivot) { drawCard(c, Offset(w / 2 - cw / 2, size.height * 0.38f), Size(cw, ch), p, m, selected = i == 1) }
    }
}
