package io.github.fershibli.megagames

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.ui.art.GameThumbnail
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.components.TopBar
import io.github.fershibli.megagames.ui.screens.HomeScreen
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
 * Renders the Google Play store graphics into fastlane/metadata/android (the usual layout for store
 * listings): 1080x1920 phone screenshots per language, the 512x512 icon and the 1024x500 feature
 * graphic. Opt-in: ./gradlew :app:testDebugUnitTest -PplayAssets=true --tests '*PlayAssetsTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class PlayAssetsTest {
    @get:Rule val rule = createComposeRule()

    @Before
    fun onlyWhenRequested() = Assume.assumeTrue(System.getProperty("playAssets") == "true")

    private val root get() = File(System.getProperty("playAssetsDir") ?: "build/play")

    private fun save(file: File) {
        file.parentFile?.mkdirs()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun screenshots(locale: String, language: AppLanguage) {
        val settings = Settings(language = language)
        val dir = File(root, "$locale/images/phoneScreenshots")
        var screen by mutableStateOf<GameId?>(null)
        rule.setContent {
            MegaGamesTheme(settings) {
                Box(Modifier.fillMaxSize().background(LocalPalette.current.background)) {
                    when (val game = screen) {
                        null -> HomeScreen({}, {})
                        else -> Column(Modifier.fillMaxSize()) {
                            TopBar(stringResource(game.title), {})
                            Box(Modifier.weight(1f)) {
                                key(game) { GameContent(game, GameHost(game, Difficulty.EASY, null, null) {}) }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(File(dir, "1_home.png"))
        listOf(GameId.SUDOKU, GameId.SOLITAIRE, GameId.BACKGAMMON, GameId.BURACO, GameId.MAHJONG).forEachIndexed { i, game ->
            screen = game
            rule.waitForIdle()
            // Wait until the puzzle is generated (the loading spinner is gone).
            rule.waitUntil(60_000) { rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes().isEmpty() }
            rule.waitForIdle()
            save(File(dir, "${i + 2}_${game.key}.png"))
        }
    }

    @Test fun screenshotsEnglish() = screenshots("en-US", AppLanguage.ENGLISH)
    @Test fun screenshotsPortuguese() = screenshots("pt-BR", AppLanguage.PORTUGUESE)
    @Test fun screenshotsSpanish() = screenshots("es-419", AppLanguage.SPANISH)
    @Test fun screenshotsFrench() = screenshots("fr-FR", AppLanguage.FRENCH)
    @Test fun screenshotsChinese() = screenshots("zh-CN", AppLanguage.CHINESE)

    /** 512x512, full square: Google Play rounds the corners itself. */
    @Test
    @Config(qualifiers = "w256dp-h256dp-xhdpi")
    fun icon() {
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF2E6B8A))) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
            }
        }
        rule.waitForIdle()
        save(File(root, "en-US/images/icon.png"))
    }

    /** 1024x500 banner shown at the top of the store page. Only the app name, so it suits every language. */
    @Test
    @Config(qualifiers = "w512dp-h250dp-xhdpi")
    fun featureGraphic() {
        rule.setContent {
            MegaGamesTheme(Settings()) {
                val p = LocalPalette.current
                Row(
                    Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2E6B8A), Color(0xFF2E6B8A).darker(0.35f)))).padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Mili Games", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (row in GameId.entries.chunked(3).take(2)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (game in row) {
                                    GameThumbnail(
                                        game,
                                        Modifier.size(84.dp).shadow(6.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(p.surface),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        save(File(root, "en-US/images/featureGraphic.png"))
    }
}
