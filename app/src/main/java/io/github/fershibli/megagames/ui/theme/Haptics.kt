package io.github.fershibli.megagames.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.fershibli.megagames.core.Settings

/**
 * Short vibrations, in the three groups of the settings: a touch on a game board ([move]), a
 * button or menu ([button]) and the end of a game ([won] / [lost]). They go through the view's
 * haptic feedback, which needs no permission and follows the phone's own "vibrate on touch".
 */
class Haptics(private val view: View?, private val settings: Settings) {
    fun move() {
        if (settings.vibrateMoves) perform(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun button() {
        if (settings.vibrateButtons) perform(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun won() {
        if (settings.vibrateAlerts) perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    }

    fun lost() {
        if (settings.vibrateAlerts) perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    }

    private fun perform(type: Int) {
        view?.performHapticFeedback(type)
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics(null, Settings()) }

/** The vibrations, following the current settings. */
val currentHaptics: Haptics
    @Composable @ReadOnlyComposable get() = LocalHaptics.current
