package io.github.fershibli.megagames.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import io.github.fershibli.megagames.core.Motion
import io.github.fershibli.megagames.core.Pace
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The [Pace] chosen in the settings; read it in composition, then [wait] inside effects. */
val currentPace: Pace
    @Composable @ReadOnlyComposable get() = LocalSettings.current.pace

/** The [Motion] chosen in the settings. */
val currentMotion: Motion
    @Composable @ReadOnlyComposable get() = LocalSettings.current.motion

/** Waits [millis] scaled by the chosen pace (shorter when quick, longer when calm). */
suspend fun Pace.wait(millis: Long) = delay((millis * factor).toLong())

/** An animation of [millis] that follows the chosen motion: instant, slow or regular. */
fun <T> Motion.spec(millis: Int, easing: Easing = FastOutSlowInEasing): AnimationSpec<T> =
    if (this == Motion.NONE) snap() else tween((millis * duration).roundToInt(), easing = easing)

/** Scales a swing, bounce or shake by the chosen motion (0 when movement is off). */
fun Motion.swing(value: Float): Float = value * amplitude
