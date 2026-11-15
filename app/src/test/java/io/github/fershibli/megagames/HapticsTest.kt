package io.github.fershibli.megagames

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.ui.theme.Haptics
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The three groups of vibration each follow their own switch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HapticsTest {
    private class RecordingView(context: Context) : View(context) {
        val done = ArrayList<Int>()
        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            done += feedbackConstant
            return true
        }
    }

    private fun played(settings: Settings, action: Haptics.() -> Unit): List<Int> {
        val view = RecordingView(ApplicationProvider.getApplicationContext())
        Haptics(view, settings).action()
        return view.done
    }

    @Test
    fun everyGroupVibratesByDefault() {
        assertEquals(listOf(HapticFeedbackConstants.CLOCK_TICK), played(Settings()) { move() })
        assertEquals(listOf(HapticFeedbackConstants.VIRTUAL_KEY), played(Settings()) { button() })
        assertEquals(listOf(HapticFeedbackConstants.CONFIRM, HapticFeedbackConstants.REJECT), played(Settings()) { won(); lost() })
    }

    @Test
    fun eachGroupCanBeTurnedOffAlone() {
        val noMoves = Settings(vibrateMoves = false)
        assertEquals(emptyList<Int>(), played(noMoves) { move() })
        assertEquals(1, played(noMoves) { button() }.size)
        val noButtons = Settings(vibrateButtons = false)
        assertEquals(emptyList<Int>(), played(noButtons) { button() })
        assertEquals(1, played(noButtons) { move() }.size)
        val noAlerts = Settings(vibrateAlerts = false)
        assertEquals(emptyList<Int>(), played(noAlerts) { won(); lost() })
        assertEquals(1, played(noAlerts) { move() }.size)
    }
}
