package io.github.fershibli.megagames.games.go

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.BoardSide
import io.github.fershibli.megagames.ui.components.Handoff
import io.github.fershibli.megagames.ui.components.HandoffOverlay
import io.github.fershibli.megagames.ui.components.sideSeat
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.random.Random

/** Pause before the computer answers, so the player sees their own stone land first. */
private const val AI_DELAY_MS = 450L

/** From this board size on, a stone is placed with two taps (the crossings get small). */
const val CONFIRM_FROM_SIZE = 13

@Serializable
data class GoState(
    val size: Int = 9,
    /** Every move so far, `row * size + column`, or [Go.PASS]. Black plays first. */
    val moves: List<Int> = emptyList(),
    val komi: Double = Go.KOMI,
    /** The color the computer plays; the player has the other one. */
    val computer: Int = Go.WHITE,
    /** After two passes: the player is checking which groups are dead before the count. */
    val scoring: Boolean = false,
    /** Stones marked dead while counting (`row * size + column`). */
    val dead: List<Int> = emptyList(),
)

@Composable
fun GoGame(host: GameHost) {
    val size = Go.sizeOf(host.variant?.key)
    LoadGame(host, GoState.serializer(), { GoState(size) }) { state -> GoScreen(state, host) }
}

/** A score like "42" or "42.5", with the decimal separator of the language (and Western digits). */
fun goPoints(x: Double, locale: Locale): String {
    val whole = kotlin.math.floor(x).toLong()
    return if (x - whole < 0.25) whole.toString() else "$whole${DecimalFormatSymbols.getInstance(locale).decimalSeparator}5"
}

/** The computer's move as stored in the game: a point index or [Go.PASS]. */
fun goComputerMove(record: GoRecord, s: GoState, difficulty: Difficulty, random: Random): Int {
    val p = GoAi.choose(record.board, record.toMove, s.komi, difficulty, random, record.passes == 1, record.played)
    return if (p == Go.NONE) Go.PASS else record.board.index(p)
}

@Composable
private fun GoScreen(state: MutableState<GoState>, host: GameHost) {
    val s = state.value
    val res = LocalResources.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    val scope = rememberCoroutineScope()
    val record = remember(s.size, s.moves) { GoRecord(s.size, s.moves) }
    val n = s.size
    // Taking turns on this phone: black and white are both people. At the end they agree together,
    // on the same screen, which stones are dead.
    val together = host.setup.together
    val handoff = remember { Handoff() }
    val blackSeat = sideSeat(BoardSide.BLACK, Color(0xFF232323))
    val whiteSeat = sideSeat(BoardSide.WHITE, Color(0xFFF4F1EA))
    val human = if (together) record.toMove else Go.opponent(s.computer)
    val myTurn = !s.scoring && !record.over && record.toMove == human
    var ghost by remember { mutableIntStateOf(-1) }
    var hint by remember { mutableStateOf<Int?>(null) }
    var warning by remember { mutableIntStateOf(Go.OK) }
    val dead = remember(s.dead) { s.dead.toSet() }
    val score = remember(record, dead, s.scoring) { if (s.scoring) Go.score(record.board, dead, s.komi) else null }

    val pace = currentPace
    LaunchedEffect(s) {
        when {
            s.scoring -> Unit
            record.over -> {
                // Two passes: propose which groups look dead, then let the player check.
                val proposal = withContext(Dispatchers.Default) {
                    val board = record.board.copy()
                    GoAi.estimateDead(board, record.toMove, s.komi, Random.Default).map { board.index(it) }
                }
                state.value = s.copy(scoring = true, dead = proposal.sorted())
            }
            record.toMove == s.computer && !together -> {
                pace.wait(AI_DELAY_MS)
                val move = withContext(Dispatchers.Default) { goComputerMove(record, s, host.difficulty, Random.Default) }
                state.value = s.copy(moves = s.moves + move)
            }
        }
    }

    fun play(move: Int) {
        ghost = -1
        hint = null
        warning = Go.OK
        val moves = s.moves + move
        state.value = s.copy(moves = moves)
        if (together && !GoRecord(n, moves).over) handoff.passTo(if (record.toMove == Go.BLACK) whiteSeat else blackSeat)
    }

    fun tap(i: Int) {
        if (s.scoring) {
            val group = record.stringIndices(i)
            if (group.isEmpty()) return
            val now = if (group.all { it in dead }) s.dead - group.toSet() else (s.dead + group).distinct()
            state.value = s.copy(dead = now.sorted())
            return
        }
        if (!myTurn) return
        val why = record.whyIllegal(i)
        if (why != Go.OK) {
            ghost = -1
            warning = if (why == Go.OCCUPIED) Go.OK else why
            return
        }
        warning = Go.OK
        if (n >= CONFIRM_FROM_SIZE && ghost != i) ghost = i else play(i)
    }

    Box(Modifier.fillMaxSize()) {
        GameLayout(
            status = {
                val lines = ArrayList<String>()
                if (score != null && together) {
                    lines += stringResource(R.string.go_score_together, goPoints(score.black.toDouble(), locale), goPoints(score.whiteTotal, locale))
                    lines += stringResource(R.string.go_komi_together, goPoints(s.komi, locale))
                    lines += stringResource(R.string.go_mark_dead_together)
                } else if (together) {
                    lines += stringResource(R.string.go_captured_together, record.board.captured[Go.BLACK], record.board.captured[Go.WHITE])
                    lines += when {
                        record.over -> stringResource(R.string.go_counting)
                        warning == Go.KO -> stringResource(R.string.go_ko)
                        warning == Go.SUICIDE -> stringResource(R.string.go_suicide)
                        hint == Go.PASS -> stringResource(R.string.go_hint_pass)
                        ghost >= 0 -> stringResource(R.string.go_confirm)
                        record.last == Go.PASS -> stringResource(if (record.toMove == Go.BLACK) BoardSide.BLACK.turn else BoardSide.WHITE.turn) + "\n" + stringResource(R.string.go_other_passed)
                        else -> stringResource(if (record.toMove == Go.BLACK) BoardSide.BLACK.turn else BoardSide.WHITE.turn)
                    }
                } else if (score != null) {
                    val mine = if (human == Go.BLACK) score.black.toDouble() else score.whiteTotal
                    val theirs = if (human == Go.BLACK) score.whiteTotal else score.black.toDouble()
                    lines += stringResource(R.string.go_score, goPoints(mine, locale), goPoints(theirs, locale))
                    lines += stringResource(R.string.go_komi, goPoints(s.komi, locale))
                    lines += stringResource(R.string.go_mark_dead)
                } else {
                    lines += stringResource(R.string.go_captured, record.board.captured[human], record.board.captured[s.computer])
                    lines += when {
                        record.over -> stringResource(R.string.go_counting)
                        record.toMove == s.computer -> stringResource(R.string.computer_turn)
                        warning == Go.KO -> stringResource(R.string.go_ko)
                        warning == Go.SUICIDE -> stringResource(R.string.go_suicide)
                        hint == Go.PASS -> stringResource(R.string.go_hint_pass)
                        ghost >= 0 -> stringResource(R.string.go_confirm)
                        record.last == Go.PASS -> stringResource(R.string.go_ai_passed)
                        else -> stringResource(R.string.go_your_turn)
                    }
                }
                StatusPill(lines.joinToString("\n"), emphasized = myTurn || s.scoring)
            },
            controls = {
                if (s.scoring) {
                    ActionBar {
                        BigButton(stringResource(R.string.go_keep_playing), {
                            // Back to the game: take away the passes that ended it.
                            state.value = s.copy(moves = s.moves.dropLastWhile { it == Go.PASS }, scoring = false, dead = emptyList())
                        }, style = ButtonStyle.SECONDARY)
                        BigButton(stringResource(R.string.go_count), {
                            val final = score ?: return@BigButton
                            if (together) {
                                val margin = goPoints(kotlin.math.abs(final.black - final.whiteTotal), locale)
                                val result = res.getString(if (final.black > final.whiteTotal) R.string.go_black_wins_by else R.string.go_white_wins_by, margin)
                                val score = res.getString(R.string.go_score_together, goPoints(final.black.toDouble(), locale), goPoints(final.whiteTotal, locale))
                                host.finish(GameOutcome(true, result + "\n" + score, shared = true))
                                return@BigButton
                            }
                            val mine = if (human == Go.BLACK) final.black.toDouble() else final.whiteTotal
                            val theirs = if (human == Go.BLACK) final.whiteTotal else final.black.toDouble()
                            val margin = goPoints(kotlin.math.abs(mine - theirs), locale)
                            val result = res.getString(if (mine > theirs) R.string.go_win else R.string.go_lose, margin)
                            val detail = result + "\n" + res.getString(R.string.go_score, goPoints(mine, locale), goPoints(theirs, locale))
                            host.finish(GameOutcome(mine > theirs, detail))
                        }, icon = AppIcon.CHECK)
                    }
                } else {
                    ActionBar {
                        // Against the computer Undo takes back its answer too; taking turns, one move.
                        val back = if (together) 1 else 2
                        BigButton(stringResource(R.string.undo), {
                            state.value = s.copy(moves = s.moves.dropLast(back))
                            ghost = -1
                            hint = null
                            warning = Go.OK
                        }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = myTurn && s.moves.size >= back)
                        BigButton(stringResource(R.string.go_pass), { play(Go.PASS) }, style = ButtonStyle.SECONDARY, enabled = myTurn)
                        BigButton(stringResource(R.string.hint), {
                            ghost = -1
                            warning = Go.OK
                            scope.launch {
                                val best = withContext(Dispatchers.Default) {
                                    val board = record.board.copy()
                                    val p = GoAi.choose(board, record.toMove, s.komi, Difficulty.MEDIUM, Random.Default, false, record.played)
                                    if (p == Go.NONE) Go.PASS else board.index(p)
                                }
                                // Only if the player has not moved in the meantime.
                                if (state.value.moves == s.moves) hint = best
                            }
                        }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = myTurn)
                    }
                }
            },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                GridBoard(
                    n, Modifier.size(side).testTag("go-board"),
                    stoneAt = { record.board.at(it) },
                    last = record.last?.takeIf { it >= 0 } ?: -1,
                    ghost = ghost,
                    ghostColor = human,
                    hint = hint?.takeIf { it >= 0 } ?: -1,
                    territory = score?.territory,
                    dead = if (s.scoring) dead else emptySet(),
                    onTap = { tap(it) },
                )
            }
        }
        HandoffOverlay(handoff) {}
    }
}
