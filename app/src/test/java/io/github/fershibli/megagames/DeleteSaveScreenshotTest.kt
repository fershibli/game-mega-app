package io.github.fershibli.megagames

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import android.graphics.Bitmap
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.ui.screens.InGameActions
import io.github.fershibli.megagames.ui.screens.SettingsScreen
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import java.io.File
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screens of "delete the saved game" for docs/screenshots (opt-in with -Pscreenshots=true). Apart
 * from ScreenshotTest because the bar needs the newer compose test rule to stop halfway.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class DeleteSaveScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Before
    fun onlyWhenRequested() = Assume.assumeTrue(System.getProperty("screenshots") == "true")

    private fun settingsOfASudoku() {
        val pt = Settings(language = AppLanguage.PORTUGUESE)
        val inGame = InGameActions(onResume = {}, onNewGame = {}, onNewOptions = {}, onLearn = {}, onDeleteSave = {}, deleteSaveOf = "Sudoku", onMainMenu = {})
        rule.setContent {
            MegaGamesTheme(pt) { Box(Modifier.fillMaxSize().background(LocalPalette.current.background)) { SettingsScreen(pt, {}, {}, inGame) } }
        }
        rule.onNodeWithTag("delete-save").performScrollTo()
        rule.waitForIdle()
    }

    private fun save(name: String) {
        val dir = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The red button at the very end of a game's settings. */
    @Test fun settingsDeleteSavePt() {
        settingsOfASudoku()
        save("settings_delete_save_pt")
    }

    /** The last of the three steps: the bar halfway, with the big Interrupt button. */
    @Test fun settingsDeleteProgressPt() {
        settingsOfASudoku()
        rule.onNodeWithTag("delete-save").performClick()
        rule.onNodeWithText("Entendi").performClick()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Sim, apagar").performClick()
        rule.mainClock.advanceTimeBy(2_000)
        save("settings_delete_progress_pt")
    }
}
