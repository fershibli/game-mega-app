package io.github.fershibli.megagames.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import android.os.SystemClock
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.hapticfeedback.HapticFeedback
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.OneHandZoom
import io.github.fershibli.megagames.ui.theme.LocalSettings
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.exp

/** Zoom/pan state of a game board. Offsets are in viewport pixels, scale is 1x..[MAX_SCALE]. */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    internal var viewport: IntSize = IntSize.Zero

    val isZoomed: Boolean get() = scale > 1.01f

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /** Zooms to [newScale] keeping the viewport point [focus] fixed under the finger. */
    fun zoomAt(focus: Offset, newScale: Float) {
        val target = newScale.coerceIn(1f, MAX_SCALE)
        val ratio = target / scale
        offset = clamp(focus - (focus - offset) * ratio, target)
        scale = target
    }

    fun panBy(delta: Offset) {
        offset = clamp(offset + delta, scale)
    }

    /** One-hand "double tap" zoom: 1x -> 2x -> 3x -> back to 1x. */
    fun step(focus: Offset) = when {
        scale >= 2.9f -> reset()
        scale >= 1.9f -> zoomAt(focus, 3f)
        else -> zoomAt(focus, 2f)
    }

    private fun clamp(o: Offset, s: Float): Offset {
        val minX = viewport.width - viewport.width * s
        val minY = viewport.height - viewport.height * s
        return Offset(o.x.coerceIn(minX, 0f), o.y.coerceIn(minY, 0f))
    }

    companion object {
        const val MAX_SCALE = 4f
    }
}

@Composable
fun rememberZoomState(): ZoomState = remember { ZoomState() }

/**
 * Wraps a game board and adds the zoom gestures configured in Settings:
 *
 * - Two-finger mode (default): pinch to zoom, drag with two fingers to move.
 * - One-hand mode, "double tap": double tap zooms in steps (1x, 2x, 3x, back to 1x);
 *   double tap and drag moves the view.
 * - One-hand mode, "press & rotate": touch and hold, then draw circles (clockwise zooms in,
 *   counter-clockwise zooms out); double tap and drag moves the view; double tap resets.
 *
 * Single-finger taps and drags always reach the game underneath, so the games themselves never
 * need to know about zoom. Coordinates inside [content] are already transformed.
 */
@Composable
fun ZoomableBoard(
    modifier: Modifier = Modifier,
    state: ZoomState = rememberZoomState(),
    content: @Composable BoxScope.() -> Unit,
) {
    val settings = LocalSettings.current
    val haptics = LocalHapticFeedback.current
    val twoFinger = settings.pinchZoom
    val oneHand = settings.oneHand
    val oneHandMode = settings.oneHandZoom
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { state.viewport = it }
            .pointerInput(twoFinger, oneHand, oneHandMode) {
                zoomGestures(state, twoFinger, oneHand, oneHandMode, haptics)
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.offset.x
                    translationY = state.offset.y
                },
            content = content,
        )
        if (state.isZoomed) {
            RoundIconButton(
                AppIcon.ZOOM_RESET, stringResource(R.string.zoom_reset), { state.reset() },
                Modifier.align(if (settings.leftHanded) Alignment.BottomStart else Alignment.BottomEnd).padding(8.dp),
                size = 48.dp, filled = true,
            )
        }
    }
}

private enum class Mode { NONE, MULTI, ROTATE }

private suspend fun PointerInputScope.zoomGestures(
    state: ZoomState,
    twoFinger: Boolean,
    oneHand: Boolean,
    oneHandMode: OneHandZoom,
    haptics: HapticFeedback,
) {
    var lastTapUp = 0L
    var lastTapPos = Offset.Zero
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    val slop = viewConfiguration.touchSlop
    val rotateRadius = 28.dp.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

        // Second tap of a double tap (one-hand mode): we own the whole gesture.
        if (oneHand && down.uptimeMillis - lastTapUp < doubleTapTimeout &&
            (down.position - lastTapPos).getDistance() < slop * 4
        ) {
            down.consume()
            lastTapUp = 0L
            var panning = false
            var last = down.position
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    change.consume()
                    if (!panning) {
                        if (oneHandMode == OneHandZoom.DOUBLE_TAP) state.step(down.position) else state.reset()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    break
                }
                if (!panning && (change.position - down.position).getDistance() > slop) panning = true
                if (panning) state.panBy(change.position - last)
                last = change.position
                change.consume()
            }
            return@awaitEachGesture
        }

        var mode = Mode.NONE
        var moved = false
        var lastAngle: Float? = null
        var anchor = down.position
        val longPressAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
        val wantsRotate = oneHand && oneHandMode == OneHandZoom.PRESS_ROTATE

        while (true) {
            val waitForLongPress = wantsRotate && mode == Mode.NONE && !moved
            val event = if (waitForLongPress) {
                val remaining = (longPressAt - SystemClock.uptimeMillis()).coerceAtLeast(1L)
                withTimeoutOrNull(remaining) { awaitPointerEvent(PointerEventPass.Initial) }
            } else {
                awaitPointerEvent(PointerEventPass.Initial)
            }
            if (event == null) {
                mode = Mode.ROTATE
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                continue
            }
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                if (mode == Mode.NONE && !moved) {
                    lastTapUp = event.changes.first().uptimeMillis
                    lastTapPos = event.changes.first().position
                }
                if (mode != Mode.NONE) event.changes.forEach { it.consume() }
                break
            }
            if (twoFinger && pressed.size >= 2 && mode != Mode.ROTATE) {
                mode = Mode.MULTI
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = true)
                if (zoom != 1f) state.zoomAt(centroid, state.scale * zoom)
                if (pan != Offset.Zero) state.panBy(pan)
                event.changes.forEach { it.consume() }
                continue
            }
            if (mode == Mode.MULTI) {
                event.changes.forEach { it.consume() }
                continue
            }
            if (mode == Mode.ROTATE) {
                val p = pressed.first().position
                val v = p - anchor
                if (v.getDistance() > rotateRadius) {
                    val angle = atan2(v.y, v.x)
                    lastAngle?.let { prev ->
                        var d = angle - prev
                        if (d > PI) d -= (2 * PI).toFloat()
                        if (d < -PI) d += (2 * PI).toFloat()
                        state.zoomAt(anchor, state.scale * exp(d * 0.35f))
                    }
                    lastAngle = angle
                }
                event.changes.forEach { it.consume() }
                continue
            }
            if ((pressed.first().position - down.position).getDistance() > slop) moved = true
            anchor = down.position
        }
    }
}
