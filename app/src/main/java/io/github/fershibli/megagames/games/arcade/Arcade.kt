package io.github.fershibli.megagames.games.arcade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.IconView
import io.github.fershibli.megagames.ui.theme.LocalGameCovered
import io.github.fershibli.megagames.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/*
 * Small pieces shared by the endless score games (blocks, color drops, snake): the best score of
 * each level, a gentle fixed-step clock that only runs while the game is visible and not paused,
 * and the big arrow buttons.
 */

/** Best score (or length) of each level, kept in the [WorldStore] so a new game never erases it. */
@Serializable
data class ArcadeRecords(val best: Map<String, Int> = emptyMap())

object Records {
    private fun key(game: GameId) = "${game.key}_records"

    fun best(store: WorldStore?, game: GameId, difficulty: Difficulty): Int {
        val records = store?.load(key(game), ArcadeRecords.serializer()) ?: return 0
        return records.best.getOrElse(difficulty.name) { 0 }
    }

    /** Keeps [value] when it beats the stored best. Returns true when it did. */
    fun offer(store: WorldStore, game: GameId, difficulty: Difficulty, value: Int): Boolean {
        val records = store.load(key(game), ArcadeRecords.serializer()) ?: ArcadeRecords()
        if (value <= records.best.getOrElse(difficulty.name) { 0 }) return false
        store.save(key(game), ArcadeRecords.serializer(), records.copy(best = records.best + (difficulty.name to value)))
        return true
    }
}

/** Saves [value] as the new best of this level, off the main thread, whenever it beats [toBeat]. */
@Composable
fun KeepBest(store: WorldStore, game: GameId, difficulty: Difficulty, value: Int, toBeat: Int) {
    LaunchedEffect(value) {
        if (value > toBeat) withContext(Dispatchers.Default) { Records.offer(store, game, difficulty, value) }
    }
}

/**
 * Whether a moving game is running. It always starts paused (a new game waits for "Start", a
 * restored one for "Continue") and pauses by itself when the app goes to the background or the
 * settings cover the game.
 */
@Composable
fun rememberRunning(): MutableState<Boolean> {
    val running = remember { mutableStateOf(false) }
    val covered = LocalGameCovered.current
    LaunchedEffect(covered) { if (covered) running.value = false }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { running.value = false }
    return running
}

/**
 * Calls [onTick] every [intervalMs] while [running]. A plain fixed step (no per-frame work): the
 * game only changes, and the screen only redraws, once per step. Tests drive the engines' step
 * functions directly, and the UI tests advance the test clock.
 */
@Composable
fun GameTicker(running: Boolean, intervalMs: Long, onTick: () -> Unit) {
    val tick by rememberUpdatedState(onTick)
    LaunchedEffect(running, intervalMs) {
        if (!running) return@LaunchedEffect
        while (true) {
            delay(intervalMs)
            tick()
        }
    }
}

/** What a [PadButton] shows. */
enum class PadIcon { UP, DOWN, LEFT, RIGHT, ROTATE, DROP }

/**
 * Big square button for moving a piece. Arrows are drawn in board coordinates, so "left" points
 * left in every language (unlike [AppIcon.PREV], which follows the reading direction).
 */
@Composable
fun PadButton(icon: PadIcon, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    val alpha = if (enabled) 1f else 0.4f
    val fg = p.onPrimary.copy(alpha = alpha)
    Box(
        modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 60.dp)
            .shadow(if (enabled) 3.dp else 0.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(p.primary, Color.White, 0.12f).copy(alpha = alpha), p.primary.copy(alpha = alpha))))
            .then(if (p.strongOutlines) Modifier.border(2.dp, p.outline, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (icon == PadIcon.ROTATE) IconView(AppIcon.ROTATE, fg, size = 32.dp)
        else Canvas(Modifier.matchParentSize()) { drawPadIcon(icon, fg) }
    }
}

// Unit arrow pointing right, drawn inside a 1x1 box.
private val arrowRight = Path().apply {
    moveTo(0.25f, 0.18f); lineTo(0.82f, 0.5f); lineTo(0.25f, 0.82f); close()
}

private fun DrawScope.drawPadIcon(icon: PadIcon, color: Color) {
    val s = minOf(size.width, size.height) * 0.5f
    val origin = Offset((size.width - s) / 2, (size.height - s) / 2)
    when (icon) {
        PadIcon.ROTATE -> Unit // drawn with IconView
        PadIcon.DROP -> {
            translate(origin.x, origin.y - s * 0.1f) { rotateUnitArrow(90f, s, color) }
            drawRect(color, Offset(origin.x + s * 0.18f, origin.y + s * 0.86f), Size(s * 0.64f, s * 0.12f))
        }
        else -> {
            val degrees = when (icon) {
                PadIcon.RIGHT -> 0f
                PadIcon.DOWN -> 90f
                PadIcon.LEFT -> 180f
                else -> 270f
            }
            translate(origin.x, origin.y) { rotateUnitArrow(degrees, s, color) }
        }
    }
}

private fun DrawScope.rotateUnitArrow(degrees: Float, side: Float, color: Color) {
    rotate(degrees, Offset(side / 2, side / 2)) {
        scale(side, side, Offset.Zero) { drawPath(arrowRight, color) }
    }
}
