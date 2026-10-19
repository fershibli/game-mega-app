package io.github.fershibli.megagames

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.GameVariant
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every game of the catalog, with every set of rules, opens and draws without crashing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class CatalogTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun keysAreUniqueAndStable() {
        assertEquals(GameId.entries.size, GameId.entries.map { it.key }.toSet().size)
        for (game in GameId.entries) {
            assertEquals("variant keys of $game", game.variants.size, game.variants.map { it.key }.toSet().size)
            assertTrue(game.key.matches(Regex("[a-z0-9_]+")))
        }
    }

    @Test
    fun everyGameAndEveryVariantOpens() {
        val runs = GameId.entries.flatMap { game -> (game.variants.ifEmpty { listOf(null) }).map { game to it } }
        var current by mutableStateOf(runs.first())
        rule.setContent {
            MegaGamesTheme(Settings()) {
                Box(Modifier.fillMaxSize()) {
                    val (game, variant) = current
                    key(current) { GameContent(game, GameHost(game, Difficulty.EASY, null, null, variant as GameVariant?) {}) }
                }
            }
        }
        for (run in runs) {
            current = run
            rule.waitForIdle()
            // Wait until the game is generated (the loading spinner is gone).
            rule.waitUntil(60_000) {
                rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes().isEmpty()
            }
        }
    }
}
