package io.github.fershibli.megagames

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.onAllNodesWithText
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.ui.components.TopBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.ui.screens.HomeScreen
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders screens to PNG files under docs/screenshots. Opt-in: ./gradlew :app:testDebugUnitTest -Pscreenshots=true
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Before
    fun onlyWhenRequested() = Assume.assumeTrue(System.getProperty("screenshots") == "true")

    private fun shot(name: String, settings: Settings = Settings(), content: @Composable () -> Unit) {
        rule.setContent {
            MegaGamesTheme(settings) {
                Box(Modifier.fillMaxSize().background(LocalPalette.current.background)) { content() }
            }
        }
        rule.waitForIdle()
        save(name)
    }

    private fun gameShot(name: String, game: GameId, difficulty: Difficulty = Difficulty.EASY, settings: Settings = Settings()) {
        rule.setContent {
            MegaGamesTheme(settings) {
                Column(Modifier.fillMaxSize().background(LocalPalette.current.background)) {
                    TopBar(stringResource(game.title), {})
                    Box(Modifier.weight(1f)) { GameContent(game, GameHost(game, difficulty, null, null) {}) }
                }
            }
        }
        rule.waitUntil(60_000) { rule.onAllNodesWithText("Preparing", substring = true).fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
        save(name)
    }

    private fun save(name: String) {
        val dir = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() = shot("home") { HomeScreen({}, {}) }
    @Test fun sudoku() = gameShot("game_sudoku", GameId.SUDOKU)
    @Test fun solitaire() = gameShot("game_solitaire", GameId.SOLITAIRE)
    @Test fun solitaireColorblind() = gameShot("game_solitaire_colorblind", GameId.SOLITAIRE, settings = Settings(colorMode = io.github.fershibli.megagames.core.ColorMode.COLORBLIND))
    @Test fun reversi() = gameShot("game_reversi", GameId.REVERSI)
    @Test fun mahjongEasy() = gameShot("game_mahjong_easy", GameId.MAHJONG)
    @Test fun mahjongHard() = gameShot("game_mahjong_hard", GameId.MAHJONG, Difficulty.HARD)
    @Test fun mahjongDark() = gameShot("game_mahjong_dark", GameId.MAHJONG, Difficulty.MEDIUM, Settings(darkMode = true))
    @Test fun pipes() = gameShot("game_pipes", GameId.PIPES, Difficulty.MEDIUM)
    @Test fun connect() = gameShot("game_connect", GameId.CONNECT, Difficulty.MEDIUM)
    @Test fun connectColorblind() = gameShot("game_connect_colorblind", GameId.CONNECT, Difficulty.MEDIUM, Settings(colorMode = io.github.fershibli.megagames.core.ColorMode.COLORBLIND))
    @Test fun dots() = gameShot("game_dots", GameId.DOTS)
    @Test fun dotsHighContrastDark() = gameShot("game_dots_hc_dark", GameId.DOTS, settings = Settings(colorMode = io.github.fershibli.megagames.core.ColorMode.HIGH_CONTRAST, darkMode = true))
    @Test fun crosswordEasy() = gameShot("game_crossword_easy", GameId.CROSSWORD)
    @Test fun crosswordHard() = gameShot("game_crossword_hard", GameId.CROSSWORD, Difficulty.MEDIUM, Settings(language = io.github.fershibli.megagames.core.AppLanguage.PORTUGUESE))
    @Test fun crosswordChinese() = gameShot("game_crossword_zh", GameId.CROSSWORD, Difficulty.MEDIUM, Settings(language = io.github.fershibli.megagames.core.AppLanguage.CHINESE))
    @Test fun sudokuDark() = gameShot("game_sudoku_dark", GameId.SUDOKU, settings = Settings(darkMode = true))

}
