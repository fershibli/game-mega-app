package io.github.fershibli.megagames

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
    fun collectEggsAndDeliverAnOrderForAStar() {
        val t = clock.time
        seed(FarmWorld(coins = 0, pens = listOf(Pen("chicken", t - 25 * minute)), orders = listOf(Order(mapOf("egg" to 2), 40), Order(mapOf("milk" to 9), 999), Order(mapOf("wool" to 9), 999))))
        show()
        rule.onNodeWithTag("farm-tab-orders").performClick()
        rule.onNodeWithTag("farm-deliver-0").assertIsNotEnabled()
        rule.onNodeWithTag("farm-tab-animals").performClick()
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
    fun aNewFarmShowsSixEmptyBedsAndBuyingABedAddsOne() {
        seed(FarmWorld(coins = 100))
        show()
        rule.onNodeWithText("New bed (coins: 25)").performClick()
        rule.waitForIdle()
        assertEquals(7, stored().plots.size)
        assertEquals(75, stored().coins)
        rule.onNodeWithTag("farm-plot-6").performScrollTo().assertExists()
    }
}
