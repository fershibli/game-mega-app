package io.github.fershibli.megagames.games.sequence

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.rememberWorldStore
import io.github.fershibli.megagames.games.memory.drawPictureSymbol
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.lighter
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.components.TouchTarget
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Quiet moment before the colors are shown (longer after a mistake). */
private const val LEAD_MS = 900L
private const val RETRY_LEAD_MS = 1500L

/** How long a square stays lit after the player taps it. */
private const val TAP_FLASH_MS = 220L

/** Pause before the end-of-game message. */
private const val END_PAUSE_MS = 700L

/** Symbol on each square, so the colors can be told apart without seeing color. */
private val PAD_SYMBOLS = intArrayOf(0, 4, 5, 1) // star, drop, leaf, heart
private val PAD_NAMES = intArrayOf(R.string.sequence_pad_star, R.string.sequence_pad_drop, R.string.sequence_pad_leaf, R.string.sequence_pad_heart)

/** The four colors, from the palette so the accessible modes apply. */
fun padColor(p: Palette, pad: Int): Color = p.pieces[intArrayOf(5, 1, 2, 7)[pad]]

@Composable
fun SequenceGame(host: GameHost) {
    LoadGame(host, SequenceState.serializer(), { Sequence.newGame(host.difficulty) }) { state -> SequenceScreen(state, host) }
}

@Composable
private fun SequenceScreen(state: MutableState<SequenceState>, host: GameHost) {
    val s = state.value
    val d = host.difficulty
    val pace = Sequence.pace(d)
    val worlds = rememberWorldStore()
    var records by remember { mutableStateOf(worlds.load(Sequence.STORE_KEY, SequenceRecords.serializer()) ?: SequenceRecords()) }
    val previousBest = remember { records.of(d) }
    var shownPad by remember { mutableIntStateOf(-1) }
    var tappedPad by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val resources = LocalResources.current

    // The game lights the squares one after the other, then hands over to the player.
    LaunchedEffect(s.shows, s.phase == SequencePhase.WATCH) {
        if (s.phase != SequencePhase.WATCH) return@LaunchedEffect
        delay(if (s.retry) RETRY_LEAD_MS else LEAD_MS)
        for (k in 0 until s.round) {
            shownPad = s.sequence[k]
            delay(pace.lit)
            shownPad = -1
            delay(pace.gap)
        }
        state.value = Sequence.shown(state.value)
    }

    // Every longer sequence repeated is kept as the new best right away.
    LaunchedEffect(s.completed) {
        if (s.completed > records.of(d)) {
            records = records.with(d, s.completed)
            worlds.save(Sequence.STORE_KEY, SequenceRecords.serializer(), records)
        }
    }

    LaunchedEffect(s.phase == SequencePhase.DONE) {
        if (s.phase != SequencePhase.DONE) return@LaunchedEffect
        delay(END_PAUSE_MS)
        val line = if (s.won) resources.getQuantityString(R.plurals.sequence_win, s.goal, s.goal) else resources.getString(R.string.sequence_lose, s.completed)
        val record = if (s.completed > previousBest && previousBest > 0) "\n" + resources.getString(R.string.sequence_record) else ""
        host.finish(GameOutcome(s.won, line + record))
    }

    fun tap(pad: Int) {
        val cur = state.value
        if (cur.phase != SequencePhase.INPUT) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        tappedPad = pad
        scope.launch {
            delay(TAP_FLASH_MS)
            if (tappedPad == pad) tappedPad = -1
        }
        state.value = Sequence.press(cur, pad, d)
    }

    GameLayout(
        status = {
            val action = when (s.phase) {
                SequencePhase.READY -> stringResource(R.string.sequence_ready)
                SequencePhase.WATCH -> stringResource(if (s.retry) R.string.sequence_again else R.string.sequence_watch)
                SequencePhase.INPUT -> stringResource(R.string.sequence_your_turn)
                SequencePhase.DONE -> stringResource(if (s.won) R.string.sequence_well_done else R.string.sequence_oops)
            }
            val counts = stringResource(R.string.sequence_progress, s.round, s.goal) +
                (records.of(d).takeIf { it > 0 }?.let { " · " + stringResource(R.string.sequence_best, it) } ?: "")
            StatusPill(action + "\n" + counts, emphasized = s.phase == SequencePhase.INPUT)
        },
        controls = {
            when {
                s.phase == SequencePhase.READY -> ActionBar {
                    BigButton(stringResource(R.string.sequence_start), { state.value = Sequence.start(state.value) }, icon = AppIcon.NEXT)
                }
                Sequence.forgiving(d) && s.phase == SequencePhase.INPUT -> ActionBar {
                    BigButton(stringResource(R.string.sequence_watch_again), { state.value = Sequence.watchAgain(state.value) }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY)
                }
                // Keeps the squares from jumping when the button comes and goes.
                else -> Spacer(Modifier.fillMaxWidth().height(TouchTarget))
            }
        },
    ) {
        val p = LocalPalette.current
        BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            val gap = 18.dp
            val pad = (minOf(maxWidth, maxHeight) - gap) / 2
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (col in 0 until 2) {
                            val i = row * 2 + col
                            ColorPad(i, lit = shownPad == i || tappedPad == i, p = p, modifier = Modifier.size(pad), enabled = s.phase == SequencePhase.INPUT) { tap(i) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPad(index: Int, lit: Boolean, p: Palette, modifier: Modifier, enabled: Boolean, onTap: () -> Unit) {
    val glow by animateFloatAsState(if (lit) 1f else 0f, tween(if (lit) 90 else 200), label = "glow")
    val label = stringResource(PAD_NAMES[index])
    Canvas(
        modifier
            .testTag("sequence-pad-$index")
            .semantics { contentDescription = label }
            .clickable(interactionSource = null, indication = null, enabled = enabled, role = Role.Button, onClick = onTap),
    ) { drawPad(index, glow, p) }
}

/** A big rounded square: calm when resting, bright with a white rim when lit. */
fun DrawScope.drawPad(index: Int, glow: Float, p: Palette) {
    val base = padColor(p, index)
    val r = CornerRadius(size.minDimension * 0.18f)
    val inset = size.minDimension * 0.04f * (1f - glow)
    val tl = Offset(inset, inset)
    val sz = Size(size.width - inset * 2, size.height - inset * 2)
    drawRoundRect(Color.Black.copy(alpha = 0.2f + 0.1f * glow), tl + Offset(0f, size.minDimension * 0.03f), sz, r)
    val face = androidx.compose.ui.graphics.lerp(base.darker(0.18f), base.lighter(0.55f), glow)
    drawRoundRect(Brush.verticalGradient(listOf(face.lighter(0.12f), face.darker(0.08f)), tl.y, tl.y + sz.height), tl, sz, r)
    if (glow > 0f) drawRoundRect(Color.White.copy(alpha = glow), tl, sz, r, style = Stroke(size.minDimension * 0.06f))
    if (p.strongOutlines) drawRoundRect(p.outline, tl, sz, r, style = Stroke(size.minDimension * 0.025f))
    val symbol = size.minDimension * (0.46f + 0.08f * glow)
    val ink = androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.92f), base.darker(0.55f), glow)
    drawPictureSymbol(PAD_SYMBOLS[index], center, symbol, ink)
}

/** Home screen picture: the four squares, one of them lit. */
fun DrawScope.sequenceThumbnail(p: Palette) {
    drawRect(p.boardDark)
    val gap = size.minDimension * 0.06f
    val side = (size.minDimension - gap * 3) / 2
    for (i in 0 until 4) {
        val tl = Offset(gap + (i % 2) * (side + gap), gap + (i / 2) * (side + gap))
        translateTo(tl, side) { drawPad(i, if (i == 1) 1f else 0f, p) }
    }
}

private inline fun DrawScope.translateTo(tl: Offset, side: Float, crossinline block: DrawScope.() -> Unit) {
    val scope = this
    scope.drawContext.transform.translate(tl.x, tl.y)
    val old = scope.drawContext.size
    scope.drawContext.size = Size(side, side)
    scope.block()
    scope.drawContext.size = old
    scope.drawContext.transform.translate(-tl.x, -tl.y)
}
