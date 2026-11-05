package io.github.fershibli.megagames

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.solitaire.SolitaireState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Solitaire with the table turned sideways: touches still land on the card drawn under the finger. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class SolitaireSidewaysUiTest {
    @get:Rule val rule = createComposeRule()

    private fun load(store: SaveStore): SolitaireState? =
        store.load()?.let { runCatching { AppJson.decodeFromJsonElement(SolitaireState.serializer(), it.state) }.getOrNull() }

    @Test
    fun tappingTheStockOnTheSidewaysTableDrawsACard() {
        val store = SaveStore(ApplicationProvider.getApplicationContext()).apply { clear() }
        val host = GameHost(GameId.SOLITAIRE, Difficulty.EASY, null, store, null) {}
        rule.setContent {
            MegaGamesTheme(Settings(language = AppLanguage.ENGLISH, solitaireSideways = true)) { GameContent(GameId.SOLITAIRE, host) }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("solitaire-table").fetchSemanticsNodes().isNotEmpty() && load(store) != null }
        rule.onNodeWithTag("solitaire-table").performTouchInput {
            // The table is laid out on the swapped size; its stock is the top-right card of the
            // table, which shows at the screen's top-left after the quarter turn.
            val tableWidth = height.toFloat()
            val gap = tableWidth * 0.015f
            val cardW = (tableWidth - gap * 8) / 7
            val stock = Offset(gap + 6 * (cardW + gap) + cardW / 2, gap * 2 + cardW * 1.42f / 2)
            click(Offset(stock.y, height - stock.x))
        }
        rule.waitForIdle()
        assertEquals(1, load(store)?.waste?.size)
        store.clear()
    }
}
