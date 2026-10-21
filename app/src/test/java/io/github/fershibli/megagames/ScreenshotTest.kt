package io.github.fershibli.megagames

import io.github.fershibli.megagames.R
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.res.stringResource
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.games.pet.PET_WORLD_KEY
import io.github.fershibli.megagames.games.pet.PetRules
import io.github.fershibli.megagames.games.pet.PetWorld
import io.github.fershibli.megagames.games.pet.Species
import org.robolectric.RuntimeEnvironment
import io.github.fershibli.megagames.ui.components.TopBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.ui.screens.DifficultyScreen
import io.github.fershibli.megagames.ui.screens.GameScreen
import io.github.fershibli.megagames.ui.screens.HomeScreen
import io.github.fershibli.megagames.ui.screens.InGameActions
import io.github.fershibli.megagames.ui.screens.SettingsScreen
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

    private fun gameShot(name: String, game: GameId, difficulty: Difficulty = Difficulty.EASY, settings: Settings = Settings(), variant: String? = null) {
        rule.setContent {
            MegaGamesTheme(settings) {
                Column(Modifier.fillMaxSize().background(LocalPalette.current.background)) {
                    TopBar(stringResource(game.title), {})
                    Box(Modifier.weight(1f)) { GameContent(game, GameHost(game, difficulty, null, null, game.variant(variant)) {}) }
                }
            }
        }
        // Wait until the puzzle is generated (the loading spinner is gone), whatever the language.
        rule.waitUntil(60_000) { rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
        save(name)
    }

    private fun save(name: String) {
        val dir = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() = shot("home") { HomeScreen({}, {}) }
    @Test fun homeDark() = shot("home_dark", Settings(darkMode = true)) { HomeScreen({}, {}) }
    @Test fun homeHighContrast() = shot("home_high_contrast", Settings(colorMode = io.github.fershibli.megagames.core.ColorMode.HIGH_CONTRAST)) { HomeScreen({}, {}) }
    @Test fun homeColorblindDark() = shot("home_colorblind_dark", Settings(colorMode = io.github.fershibli.megagames.core.ColorMode.COLORBLIND, darkMode = true)) { HomeScreen({}, {}) }
    @Test fun homeOneHand() = shot("home_one_hand", Settings(oneHand = true)) { HomeScreen({}, {}) }
    @Test fun homeChinese() = shot("home_zh", Settings(language = io.github.fershibli.megagames.core.AppLanguage.CHINESE)) { HomeScreen({}, {}) }
    @Test fun homeArabic() = shot("home_ar", Settings(language = io.github.fershibli.megagames.core.AppLanguage.ARABIC)) { HomeScreen({}, {}) }
    @Test fun settingsArabic() = shot("settings_ar", Settings(language = io.github.fershibli.megagames.core.AppLanguage.ARABIC)) { SettingsScreen(Settings(language = io.github.fershibli.megagames.core.AppLanguage.ARABIC), {}, {}, null) }
    @Test fun sudokuArabic() = gameShot("game_sudoku_ar", GameId.SUDOKU, settings = Settings(language = io.github.fershibli.megagames.core.AppLanguage.ARABIC))
    @Test fun crosswordArabic() = gameShot("game_crossword_ar", GameId.CROSSWORD, Difficulty.MEDIUM, Settings(language = io.github.fershibli.megagames.core.AppLanguage.ARABIC))
    @Test fun homeJapanese() = shot("home_ja", Settings(language = io.github.fershibli.megagames.core.AppLanguage.JAPANESE)) { HomeScreen({}, {}) }
    @Test fun buracoPt() = gameShot("game_buraco_pt", GameId.BURACO, settings = Settings(language = io.github.fershibli.megagames.core.AppLanguage.PORTUGUESE))
    @Test fun sudokuFrLeftHanded() = gameShot("game_sudoku_fr_left", GameId.SUDOKU, settings = Settings(language = io.github.fershibli.megagames.core.AppLanguage.FRENCH, leftHanded = true))
    @Test fun solitaireBigText() = gameShot("game_solitaire_big_text", GameId.SOLITAIRE, settings = Settings(fontScale = 1.4f, pieceScale = 1.4f))
    @Test fun difficulty() = shot("difficulty") { DifficultyScreen(GameId.MAHJONG, Difficulty.MEDIUM, {}, {}, {}) }
    @Test fun takeTurnsPt() = shot("take_turns_pt", Settings(language = io.github.fershibli.megagames.core.AppLanguage.PORTUGUESE)) {
        DifficultyScreen(GameId.REVERSI, Difficulty.EASY, {}, {}, {}, onTogether = {}, initialTogether = true)
    }
    @Test fun handoffPt() = shot("handoff_pt", Settings(language = io.github.fershibli.megagames.core.AppLanguage.PORTUGUESE)) {
        val handoff = androidx.compose.runtime.remember { io.github.fershibli.megagames.ui.components.Handoff().apply { passTo(io.github.fershibli.megagames.ui.components.Seat("Jogador 2", androidx.compose.ui.graphics.Color(0xFFD9534F))) } }
        io.github.fershibli.megagames.ui.components.HandoffOverlay(handoff) {}
    }
    @Test fun tablePt() = shot("table_pt", Settings(language = io.github.fershibli.megagames.core.AppLanguage.PORTUGUESE)) {
        val tables = listOf(io.github.fershibli.megagames.core.TableSize(2), io.github.fershibli.megagames.core.TableSize(4, R.string.reversi_dark))
        io.github.fershibli.megagames.ui.screens.TableScreen(GameId.BURACO, false, tables[1], {}, {}, {}, tables)
    }
    @Test fun settings() = shot("settings") { SettingsScreen(Settings(), {}, {}, null) }
    @Test fun settingsInGamePt() = shot("settings_in_game_pt", Settings(language = io.github.fershibli.megagames.core.AppLanguage.PORTUGUESE)) {
        SettingsScreen(Settings(language = io.github.fershibli.megagames.core.AppLanguage.PORTUGUESE), {}, {}, InGameActions({}, {}, {}))
    }
    @Test fun missionPt() = shot("mission_pt", Settings(language = io.github.fershibli.megagames.core.AppLanguage.PORTUGUESE)) { io.github.fershibli.megagames.ui.screens.MissionDialog {} }
    @Test fun resumeDialog() = shot("resume_dialog") {
        val store = SaveStore(org.robolectric.RuntimeEnvironment.getApplication())
        GameScreen(GameId.SUDOKU, Difficulty.EASY, null, store, Settings(), {}, askResume = true, onResumeAnswered = {}, onExit = {})
    }
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
    @Test fun cube3() = gameShot("game_cube", GameId.CUBE, Difficulty.MEDIUM, variant = "3")
    @Test fun cube5Colorblind() = gameShot("game_cube_5_colorblind", GameId.CUBE, Difficulty.HARD, Settings(colorMode = io.github.fershibli.megagames.core.ColorMode.COLORBLIND), variant = "5")
    @Test fun dotsHighContrastDark() = gameShot("game_dots_hc_dark", GameId.DOTS, settings = Settings(colorMode = io.github.fershibli.megagames.core.ColorMode.HIGH_CONTRAST, darkMode = true))
    @Test fun crosswordEasy() = gameShot("game_crossword_easy", GameId.CROSSWORD)
    @Test fun crosswordHard() = gameShot("game_crossword_hard", GameId.CROSSWORD, Difficulty.MEDIUM, Settings(language = io.github.fershibli.megagames.core.AppLanguage.PORTUGUESE))
    @Test fun crosswordChinese() = gameShot("game_crossword_zh", GameId.CROSSWORD, Difficulty.MEDIUM, Settings(language = io.github.fershibli.megagames.core.AppLanguage.CHINESE))
    @Test fun crosswordJapanese() = gameShot("game_crossword_ja", GameId.CROSSWORD, Difficulty.MEDIUM, Settings(language = io.github.fershibli.megagames.core.AppLanguage.JAPANESE))
    @Test fun crosswordRussian() = gameShot("game_crossword_ru", GameId.CROSSWORD, Difficulty.MEDIUM, Settings(language = io.github.fershibli.megagames.core.AppLanguage.RUSSIAN))
    @Test fun crosswordGerman() = gameShot("game_crossword_de", GameId.CROSSWORD, Difficulty.MEDIUM, Settings(language = io.github.fershibli.megagames.core.AppLanguage.GERMAN))
    @Test fun backgammon() = gameShot("game_backgammon", GameId.BACKGAMMON)
    @Test fun backgammonDark() = gameShot("game_backgammon_dark", GameId.BACKGAMMON, settings = Settings(darkMode = true))
    @Test fun backgammonUpright() = gameShot("game_backgammon_upright", GameId.BACKGAMMON, settings = Settings(backgammonUpright = true))
    @Test fun buraco() = gameShot("game_buraco", GameId.BURACO)
    @Test fun sudokuDark() = gameShot("game_sudoku_dark", GameId.SUDOKU, settings = Settings(darkMode = true))
    @Test fun checkers() = gameShot("game_checkers", GameId.CHECKERS)
    @Test fun chess() = gameShot("game_chess", GameId.CHESS)
    @Test fun petChoose() = gameShot("game_pet_choose", GameId.PET)
    @Test fun petRoom() {
        // A cat already living in the room.
        WorldStore(RuntimeEnvironment.getApplication()).run {
            save(PET_WORLD_KEY, PetWorld.serializer(), PetRules.adopt(PetWorld(), Species.CAT, "Sunny", System.currentTimeMillis()))
            flush()
        }
        gameShot("game_pet", GameId.PET)
    }
    @Test fun cacheta() = CachetaUiTest.screenshotHost().let { host -> shot("game_cacheta") { GameContent(GameId.CACHETA, host) } }
    @Test fun trucoPt() = TrucoUiTest.screenshotHost().let { host -> shot("game_truco_pt", Settings(language = io.github.fershibli.megagames.core.AppLanguage.PORTUGUESE)) { GameContent(GameId.TRUCO, host) } }

}
