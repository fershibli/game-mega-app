package io.github.fershibli.megagames.games.memory

import io.github.fershibli.megagames.ui.theme.spec
import io.github.fershibli.megagames.ui.theme.currentMotion
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.core.rememberWorldStore
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette

/** How long a card takes to turn over. */
private const val FLIP_MS = 260

/** Pause after the last pair before the end-of-game message. */
private const val END_PAUSE_MS = 700L

/** Width / height of a card. */
private const val CARD_RATIO = 0.72f

@Composable
fun MemoryGame(host: GameHost) {
    val variant = host.variant?.key ?: Memory.SYMBOLS
    LoadGame(host, MemoryState.serializer(), { Memory.newGame(variant, host.difficulty) }) { state -> MemoryScreen(state, host) }
}

@Composable
private fun MemoryScreen(state: MutableState<MemoryState>, host: GameHost) {
    val pace = currentPace
    val s = state.value
    val worlds = rememberWorldStore()
    val records = remember { worlds.load(Memory.STORE_KEY, MemoryRecords.serializer()) ?: MemoryRecords() }
    val best = records.of(s.variant, host.difficulty)
    val resources = LocalResources.current

    LaunchedEffect(s.won) {
        if (!s.won) return@LaunchedEffect
        pace.wait(END_PAUSE_MS)
        val (updated, record) = records.with(s.variant, host.difficulty, s.moves)
        if (record) worlds.save(Memory.STORE_KEY, MemoryRecords.serializer(), updated)
        val lines = listOfNotNull(
            resources.getQuantityString(R.plurals.memory_result, s.moves, s.moves),
            if (record && best != null) resources.getString(R.string.memory_record) else null,
            if (!record && best != null) resources.getQuantityString(R.plurals.memory_best_line, best, best) else null,
        )
        host.finish(GameOutcome(true, lines.joinToString("\n")))
    }

    GameLayout(
        status = {
            val action = when {
                s.won -> stringResource(R.string.memory_all_found)
                s.missShowing -> stringResource(R.string.memory_miss)
                s.open.size == 1 -> stringResource(R.string.memory_second)
                s.justMatched -> stringResource(R.string.memory_match)
                else -> stringResource(R.string.memory_first)
            }
            val counts = stringResource(R.string.memory_counts, s.moves, s.found, s.pairs) +
                (best?.let { " · " + stringResource(R.string.memory_best, it) } ?: "")
            StatusPill(counts + "\n" + action, emphasized = s.missShowing || s.justMatched)
        },
    ) {
        val p = LocalPalette.current
        val cardPalette = remember(p) { memoryCardPalette(p) }
        val measurer = rememberTextMeasurer()
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            // Portrait grid on a phone; turned around when the screen is wider than tall.
            val wide = maxWidth > maxHeight
            val cols = if (wide) maxOf(s.cols, s.rows) else minOf(s.cols, s.rows)
            val rows = s.faces.size / cols
            val gap = 8.dp
            val cellW = (maxWidth - gap * (cols - 1)) / cols
            val cellH = (maxHeight - gap * (rows - 1)) / rows
            val cardW: Dp = minOf(cellW, cellH * CARD_RATIO)
            val cardH: Dp = cardW / CARD_RATIO
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (r in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (c in 0 until cols) {
                            val i = r * cols + c
                            MemoryCard(
                                index = i, s = s, p = p, cardPalette = cardPalette, measurer = measurer,
                                modifier = Modifier.size(cardW, cardH),
                                onTap = { Memory.tap(state.value, i)?.let { state.value = it } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryCard(index: Int, s: MemoryState, p: Palette, cardPalette: Palette, measurer: TextMeasurer, modifier: Modifier, onTap: () -> Unit) {
    val found = s.matched[index]
    val shown = found || index in s.open
    val angle by animateFloatAsState(if (shown) 180f else 0f, currentMotion.spec(FLIP_MS), label = "flip")
    val label = stringResource(if (found) R.string.memory_card_found else R.string.memory_card, index + 1)
    Canvas(
        modifier
            .testTag("memory-card-$index")
            .semantics { contentDescription = label }
            .graphicsLayer {
                // Turn around the vertical axis; past halfway the face shows (not mirrored).
                rotationY = if (angle <= 90f) angle else angle - 180f
                cameraDistance = 14f * density
            }
            .clickable(interactionSource = null, indication = null, enabled = !found, role = Role.Button, onClick = onTap),
    ) {
        drawMemoryCard(s.variant, s.faces[index], Offset.Zero, size, p, cardPalette, measurer, faceUp = angle > 90f, found = found)
    }
}
