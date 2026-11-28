package io.github.fershibli.megagames

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.farm.FARM_WORLD_KEY
import io.github.fershibli.megagames.games.farm.FarmClock
import io.github.fershibli.megagames.games.farm.FarmRules
import io.github.fershibli.megagames.games.farm.FarmWorld
import io.github.fershibli.megagames.games.farm.Item
import io.github.fershibli.megagames.games.farm.LocalFarmClock
import io.github.fershibli.megagames.games.farm.Order
import io.github.fershibli.megagames.games.farm.Pen
import io.github.fershibli.megagames.games.farm.Plot
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The farm like a person uses it: sow, wait, harvest, sell, fill an order, and it is all kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class FarmUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val minute = 60_000L

    private class FakeClock(var time: Long) : FarmClock {
        override fun now() = time
    }

    private val clock = FakeClock(1_800_000_000_000L)

    private fun show() {
        rule.setContent {
            CompositionLocalProvider(LocalFarmClock provides clock) {
                MegaGamesTheme(Settings()) { GameContent(GameId.FARM, GameHost(GameId.FARM, Difficulty.EASY, null, null) {}) }
            }
        }
        rule.waitForIdle()
    }

    private fun seed(world: FarmWorld) = WorldStore(app).run {
        save(FARM_WORLD_KEY, FarmWorld.serializer(), world)
        flush()
    }

    private fun stored(): FarmWorld {
        val store = WorldStore(app)
        store.flush()
        return store.load(FARM_WORLD_KEY, FarmWorld.serializer())!!
    }

    @Test
    fun sowWaitAndHarvestALettuceThenSellIt() {
        show()
        rule.onNodeWithTag("farm-plot-0").performClick()
        rule.onNodeWithText("Lettuce · coins: 2 · 2 min").performClick()
        rule.waitForIdle()
        assertEquals("lettuce", stored().plots[0].crop)
        assertEquals(FarmRules.START_COINS - 2, stored().coins)
        // Not ripe yet: a tap waters it instead.
        rule.onNodeWithTag("farm-plot-0").performClick()
        rule.waitForIdle()
        assertEquals(clock.time, stored().plots[0].wateredAt)
        clock.time += minute
        rule.onNodeWithTag("farm-plot-0").performClick()
        rule.waitForIdle()
        assertEquals(2, stored().count(Item.LETTUCE))
        rule.onNodeWithText("Harvested! It is in the barn.", substring = true).assertExists()

        rule.onNodeWithTag("farm-tab-barn").performClick()
        rule.onNodeWithTag("farm-sell-all-lettuce").performClick()
        rule.waitForIdle()
        assertEquals(FarmRules.START_COINS - 2 + 2 * Item.LETTUCE.price, stored().coins)
        rule.onNodeWithText("The barn is empty", substring = true).assertExists()
    }

    @Test
    fun aNewFarmHasSixBedsAndANewOneAsksFirstAndNeedsSomethingSown() {
        show()
        // Nothing sown yet: the button says why instead of spending the coins.
        rule.onNodeWithTag("farm-buy-plot").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Sow a bed first", substring = true).assertExists()
        rule.onNodeWithTag("farm-plot-${FarmRules.START_PLOTS}").assertDoesNotExist()
        rule.onNodeWithTag("farm-plot-0").performClick()
        rule.onNodeWithText("Lettuce · coins: 2 · 2 min").performClick()
        rule.waitForIdle()
        // Now it asks, with the picture of a bed growing; Cancel changes nothing.
        rule.onNodeWithTag("farm-buy-plot").performClick()
        rule.onNodeWithTag("farm-plot-picture").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        assertEquals(FarmRules.START_PLOTS, stored().plots.size)
        rule.onNodeWithTag("farm-buy-plot").performClick()
        rule.onNodeWithTag("farm-plot-confirm").performClick()
        rule.waitForIdle()
        assertEquals(FarmRules.START_PLOTS + 1, stored().plots.size)
        assertEquals(FarmRules.START_COINS - 2 - 25, stored().coins)
        rule.onNodeWithText("the one blinking", substring = true).assertExists()
        rule.onNodeWithTag("farm-plot-${FarmRules.START_PLOTS}").assertExists()
    }

    @Test
    fun oneCollectAllButtonGathersCropsAndAnimalProductsAndSaysWhat() {
        val t = clock.time
        seed(FarmWorld(coins = 0, plots = listOf(Plot("lettuce", t - 10 * minute), Plot()), pens = listOf(Pen("chicken", t - 25 * minute))))
        show()
        rule.onNodeWithTag("farm-harvest-all").assertDoesNotExist()
        rule.onNodeWithTag("farm-collect-all").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(2, stored().count(Item.LETTUCE))
        assertEquals(2, stored().count(Item.EGG))
        rule.onNodeWithText("In the barn: Lettuce ×2, Egg ×2", substring = true).assertExists()
        // Nothing is ready any more, so the button rests.
        rule.onNodeWithTag("farm-collect-all").assertIsNotEnabled()
    }

    @Test
    fun collectAllIsDisabledWithNothingReady() {
        show()
        rule.onNodeWithTag("farm-collect-all").assertIsNotEnabled()
    }

    @Test
    fun collectEggsAndDeliverAnOrderForAStar() {
        val t = clock.time
        seed(FarmWorld(coins = 0, pens = listOf(Pen("chicken", t - 25 * minute)), orders = listOf(Order(mapOf("egg" to 2), 40), Order(mapOf("milk" to 9), 999), Order(mapOf("wool" to 9), 999))))
        show()
        rule.onNodeWithTag("farm-tab-orders").performClick()
        rule.onNodeWithTag("farm-deliver-0").assertIsNotEnabled()
        rule.onNodeWithTag("farm-tab-farm").performClick()
        rule.onNodeWithTag("farm-pen-0").performClick()
        rule.waitForIdle()
        assertEquals(2, stored().count(Item.EGG))
        rule.onNodeWithTag("farm-tab-orders").performClick()
        rule.onNodeWithTag("farm-deliver-0").performScrollTo().assertIsEnabled().performClick()
        rule.waitForIdle()
        val after = stored()
        assertEquals(40, after.coins)
        assertEquals(1, after.stars)
        assertEquals(0, after.count(Item.EGG))
        assertEquals(FarmRules.ORDERS, after.orders.size)
    }

    @Test
    fun theTopBarShowsCoinAndStarIconsWithTheAmountsAndStillReadsTheWords() {
        seed(FarmWorld(coins = 86, stars = 8))
        show()
        rule.onNodeWithTag("farm-status").assertContentDescriptionEquals("Coins: 86, Stars: 8")
        rule.onNodeWithTag("farm-coins-icon", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("farm-stars-icon", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("farm-coins-value", useUnmergedTree = true).assertTextEquals("86")
        rule.onNodeWithTag("farm-stars-value", useUnmergedTree = true).assertTextEquals("8")
        // The words are gone from the screen.
        rule.onNodeWithText("Coins:", substring = true).assertDoesNotExist()
    }
}
