package io.github.fershibli.megagames

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.Motion
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.farm.Animal
import io.github.fershibli.megagames.games.farm.FARM_WORLD_KEY
import io.github.fershibli.megagames.games.farm.FarmClock
import io.github.fershibli.megagames.games.farm.FarmRules
import io.github.fershibli.megagames.games.farm.FarmWorld
import io.github.fershibli.megagames.games.farm.Item
import io.github.fershibli.megagames.games.farm.Fish
import io.github.fershibli.megagames.games.farm.Lake
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import io.github.fershibli.megagames.games.farm.Mill
import io.github.fershibli.megagames.games.farm.MillJob
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

    private fun show(settings: Settings = Settings()) {
        rule.setContent {
            CompositionLocalProvider(LocalFarmClock provides clock) {
                MegaGamesTheme(settings) { GameContent(GameId.FARM, GameHost(GameId.FARM, Difficulty.EASY, null, null) {}) }
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
        rule.onNodeWithContentDescription("Lettuce · coins: 2 · 2 min").performClick()
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
        rule.onNodeWithContentDescription("Lettuce · coins: 2 · 2 min").performClick()
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

    @Test
    fun pricesShowTheCoinPictureAndStillReadTheWords() {
        val t = clock.time
        val world = FarmWorld(coins = 80, stars = 3, plots = listOf(Plot("lettuce", t - minute), Plot()), orders = listOf(Order(mapOf("egg" to 2), 40)), barn = mapOf("egg" to 3))
        val bed = FarmRules.plotCost(world)
        seed(world)
        show()
        // The new-bed button: the coin picture and the number, the words for screen readers.
        rule.onNodeWithContentDescription("New bed (coins: $bed)").assertExists()
        rule.onNodeWithText("coins", substring = true).assertDoesNotExist()
        rule.onAllNodesWithTag("farm-price-coin", useUnmergedTree = true).assertCountEquals(1)
        // The seed options of the sow dialog.
        rule.onNodeWithTag("farm-plot-1").performClick()
        rule.onNodeWithContentDescription("Lettuce · coins: 2 · 2 min").assertExists()
        rule.onNodeWithContentDescription("Carrot · coins: 4 · 5 min").assertExists()
        rule.onNodeWithText("coins:", substring = true).assertDoesNotExist()
        assertEquals(true, rule.onAllNodesWithTag("farm-price-coin", useUnmergedTree = true).fetchSemanticsNodes().size > 2)
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        // The new-bed question.
        rule.onNodeWithTag("farm-buy-plot").performClick()
        rule.onNodeWithContentDescription("Price in coins: $bed", substring = true).assertExists()
        rule.onNode(hasTestTag("farm-price-coin") and hasAnyAncestor(hasTestTag("farm-plot-note")), useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        // The animals.
        rule.onNodeWithTag("farm-buy-animal").performClick()
        rule.onNodeWithContentDescription("Chicken · coins: ${Animal.CHICKEN.cost}").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        // The order reward and the barn.
        rule.onNodeWithTag("farm-tab-orders").performClick()
        rule.onNodeWithContentDescription("Reward: a star, coins +40").assertExists()
        rule.onNode(hasTestTag("farm-price-coin") and hasAnyAncestor(hasTestTag("farm-order-0")), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("farm-tab-barn").performClick()
        rule.onNodeWithContentDescription("Egg: 3 · price: ${Item.EGG.price}").assertExists()
        rule.onNode(hasTestTag("farm-price-coin") and hasAnyAncestor(hasTestTag("farm-barn-egg")), useUnmergedTree = true).assertExists()
    }

    @Test
    fun theBuildListShowsTheMillFadedBelowSixStarsAndDoesNotBuildIt() {
        seed(FarmWorld(coins = 500, stars = 4))
        show()
        rule.onNodeWithTag("farm-build").performClick()
        rule.waitForIdle()
        // Picture, price with the coin and what is missing; it can't be chosen yet.
        rule.onNodeWithContentDescription("Mill · Price in coins: 120 · Stars missing: 2").assertExists()
        rule.onNodeWithTag("farm-build-mill").assertIsNotEnabled()
        rule.onNode(hasTestTag("farm-price-coin") and hasAnyAncestor(hasTestTag("farm-build-mill")), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("farm-build-mill").performClick()
        rule.waitForIdle()
        assertEquals(null, stored().mill)
        assertEquals(500, stored().coins)
        rule.onNodeWithText("Close").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("farm-mill").assertDoesNotExist()
    }

    @Test
    fun buildingTheMillCostsCoinsPutsItOnTheFarmAndHidesTheBuildButton() {
        seed(FarmWorld(coins = 130, stars = 6))
        show()
        rule.onNodeWithTag("farm-mill").assertDoesNotExist()
        rule.onNodeWithTag("farm-build").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Mill · Price in coins: 120 · Tap to build").assertExists()
        rule.onNodeWithTag("farm-build-mill").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(10, stored().coins)
        assertEquals(true, stored().mill != null)
        rule.onNodeWithText("The mill is built!", substring = true).assertExists()
        rule.onNodeWithTag("farm-mill").assertExists()
        // The lake and its net are still to come, so the button stays.
        rule.onNodeWithTag("farm-build").assertExists()
    }

    @Test
    fun theBuildButtonGoesAwayOnceTheMillTheLakeAndTheNetAreBuilt() {
        seed(FarmWorld(coins = 0, stars = 20, mill = Mill(), lake = Lake(FarmRules.BAITS, clock.time, net = clock.time)))
        show()
        rule.onNodeWithTag("farm-lake").assertExists()
        rule.onNodeWithTag("farm-build").assertDoesNotExist()
    }

    // The lake.

    private fun lakeFarm(baits: Int = FarmRules.BAITS, since: Long = clock.time, stars: Int = 10) =
        FarmWorld(coins = 7, stars = stars, barn = mapOf("egg" to 2), lake = Lake(baits, since))

    /** Moves the farm's clock half a second at a time until the button says Pull!. */
    private fun waitForTheBite() {
        repeat(40) {
            if (rule.onAllNodesWithText("Pull!").fetchSemanticsNodes().isNotEmpty()) return
            clock.time += 500
            rule.mainClock.advanceTimeBy(300)
            rule.waitForIdle()
        }
        rule.onNodeWithText("Pull!").assertExists()
    }

    @Test
    fun castWaitForTheBiteAndPullCatchesAFishForTheBarn() {
        seed(lakeFarm())
        show()
        rule.onNodeWithTag("farm-lake").assertContentDescriptionEquals("Lake, tap to fish").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("farm-fishing-card").assertExists()
        rule.onNodeWithTag("farm-fishing-baits").assertTextEquals("Bait: 5/5")
        rule.onNodeWithTag("farm-fishing-button").assertTextEquals("Cast the rod").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("farm-fishing-baits").assertTextEquals("Bait: 4/5")
        rule.onNodeWithTag("farm-fishing-status").assertTextEquals("The float rests on the water. Wait for a bite.")
        rule.onNodeWithTag("farm-fishing-button").assertIsNotEnabled()
        assertEquals(4, FarmRules.baits(stored(), clock.time))
        waitForTheBite()
        // The bite is written and read out politely, not only felt.
        rule.onNodeWithTag("farm-fishing-status").assertTextEquals("A fish is biting! Tap Pull!")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithTag("farm-fishing-button").assertIsEnabled().performClick()
        rule.waitForIdle()
        val after = stored()
        val fish = Fish.entries.single { after.count(it.item) == 1 }
        rule.onNodeWithText("Caught: ${app.getString(fish.item.label)}! It is in the barn.").assertExists()
        assertEquals(2, after.count(Item.EGG))
        assertEquals(7, after.coins)
        // Ready to cast again.
        rule.onNodeWithTag("farm-fishing-button").assertTextEquals("Cast the rod").assertIsEnabled()
    }

    @Test
    fun aLatePullLetsTheFishGoAndNothingIsLost() {
        seed(lakeFarm())
        show()
        rule.onNodeWithTag("farm-lake").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("farm-fishing-button").performClick()
        rule.waitForIdle()
        waitForTheBite()
        // Long past the time to pull.
        clock.time += 20_000
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        rule.onNodeWithTag("farm-fishing-status").assertTextEquals("The fish swam away. Cast again whenever you like.")
        rule.onNodeWithTag("farm-fishing-button").assertTextEquals("Cast the rod").assertIsEnabled()
        val after = stored()
        assertEquals(7, after.coins)
        assertEquals(mapOf("egg" to 2), after.barn)
        assertEquals(4, FarmRules.baits(after, clock.time))
    }

    @Test
    fun closingTheCardBeforeACatchGivesTheBaitBack() {
        seed(lakeFarm())
        show()
        rule.onNodeWithTag("farm-lake").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("farm-fishing-button").performClick()
        rule.waitForIdle()
        assertEquals(4, FarmRules.baits(stored(), clock.time))
        rule.onNodeWithTag("farm-fishing-close").performClick()
        rule.waitForIdle()
        assertEquals(FarmRules.BAITS, FarmRules.baits(stored(), clock.time))
        rule.onNodeWithTag("farm-fishing-card").assertDoesNotExist()
    }

    @Test
    fun withNoBaitTheButtonSaysWhenTheNextOneComesBack() {
        seed(lakeFarm(baits = 0, since = clock.time - 3 * minute))
        show()
        rule.onNodeWithTag("farm-lake").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("farm-fishing-baits").assertTextEquals("Bait: 0/5")
        rule.onNodeWithTag("farm-fishing-button").assertTextEquals("Next bait: 7 min").assertIsNotEnabled()
        rule.onNodeWithTag("farm-fishing-status").assertTextEquals("No bait for now. One comes back every 10 min.")
        // Seven minutes later it is back.
        clock.time += 7 * minute
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        rule.onNodeWithTag("farm-fishing-baits").assertTextEquals("Bait: 1/5")
        rule.onNodeWithTag("farm-fishing-button").assertTextEquals("Cast the rod").assertIsEnabled()
    }

    @Test
    fun theLakeIsBuiltFromTheListAndTheNetShowsFadedUntilFifteenStars() {
        seed(FarmWorld(coins = 210, stars = 12, mill = Mill()))
        show()
        rule.onNodeWithTag("farm-lake").assertDoesNotExist()
        rule.onNodeWithTag("farm-build").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Lake net · Free · Stars missing: 3").assertExists()
        rule.onNodeWithTag("farm-build-net").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Lake · Price in coins: 200 · Tap to build").assertExists()
        rule.onNodeWithTag("farm-build-lake").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(10, stored().coins)
        assertEquals(FarmRules.BAITS, FarmRules.baits(stored(), clock.time))
        rule.onNodeWithText("The lake is ready!", substring = true).assertExists()
        rule.onNodeWithTag("farm-lake").assertExists()
        // Still twelve stars: the net stays faded.
        rule.onNodeWithTag("farm-build").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("farm-build-net").assertIsNotEnabled()
        rule.onNodeWithTag("farm-build-net").performClick()
        rule.waitForIdle()
        assertEquals(null, stored().lake!!.net)
    }

    @Test
    fun theNetNeedsTheLakeEvenWithFifteenStars() {
        seed(FarmWorld(coins = 0, stars = 15))
        show()
        rule.onNodeWithTag("farm-build").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Lake net · Free · Build the lake first").assertExists()
        rule.onNodeWithTag("farm-build-net").assertIsNotEnabled()
    }

    @Test
    fun withFifteenStarsAndTheLakeTheNetIsPlacedForFree() {
        seed(lakeFarm(stars = 15).copy(mill = Mill()))
        show()
        rule.onNodeWithTag("farm-build").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Lake net · Free · Tap to build").assertExists()
        rule.onNodeWithTag("farm-build-net").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(clock.time, stored().lake!!.net)
        assertEquals(7, stored().coins)
        rule.onNodeWithText("The net is in the lake!", substring = true).assertExists()
        rule.onNodeWithTag("farm-build").assertDoesNotExist()
    }

    @Test
    fun collectAllAndATapOnTheLakeBringTheNetsFish() {
        // Two catches waiting in the net, and the lake shows them.
        seed(lakeFarm(stars = 15).copy(lake = Lake(FarmRules.BAITS, clock.time, net = clock.time - 45 * minute)))
        show()
        rule.onNodeWithTag("farm-collect-all").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(2, Fish.entries.sumOf { stored().count(it.item) })
        // Two more, then a tap on the lake takes them instead of opening the card.
        clock.time += 40 * minute
        rule.onNodeWithTag("farm-lake").performClick()
        rule.waitForIdle()
        assertEquals(4, Fish.entries.sumOf { stored().count(it.item) })
        rule.onNodeWithTag("farm-fishing-card").assertDoesNotExist()
        rule.onNodeWithText("In the barn:", substring = true).assertExists()
    }

    @Test
    fun grindingFromTheMillCardTakesTheBarnsIngredientsAndTappingTheMillCollects() {
        seed(FarmWorld(coins = 0, stars = 6, barn = mapOf("corn" to 3, "wheat" to 1), mill = Mill()))
        show()
        rule.onNodeWithTag("farm-mill").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("farm-mill-card").assertExists()
        rule.onNodeWithText("The mill is resting. Choose what to grind.").assertExists()
        rule.onNodeWithText("Queue: 0 of 3").assertExists()
        // Both recipes show what they need and how many the barn has; a missing one says so on its button.
        rule.onNodeWithTag("farm-recipe-cornmeal-have").assertTextEquals("In the barn: 3")
        rule.onNodeWithTag("farm-recipe-flour-have").assertTextEquals("In the barn: 1")
        rule.onNodeWithTag("farm-grind-flour").assertIsNotEnabled()
        rule.onNodeWithText("Missing: Wheat ×1").assertExists()
        rule.onNodeWithTag("farm-grind-cornmeal").assertIsEnabled().performClick()
        rule.waitForIdle()
        val ground = stored()
        assertEquals(1, ground.count(Item.CORN))
        assertEquals(1, ground.mill!!.jobs.size)
        rule.onNodeWithText("Queue: 1 of 3").assertExists()
        rule.onNodeWithText("Grinding: Corn meal · 10 min").assertExists()
        rule.onNodeWithTag("farm-mill-bar").assertExists()
        // One corn is left and the recipe needs two.
        rule.onNodeWithTag("farm-grind-cornmeal").assertIsNotEnabled()
        rule.onNodeWithText("Missing: Corn ×1").assertExists()
        rule.onNodeWithTag("farm-mill-close").performClick()
        rule.waitForIdle()
        // Not ready yet: a tap on the mill shows its card again. When it is ready, the tap collects.
        clock.time += 10 * minute
        rule.onNodeWithTag("farm-mill").performClick()
        rule.waitForIdle()
        assertEquals(1, stored().count(Item.CORN_MEAL))
        assertEquals(0, stored().mill!!.jobs.size)
        rule.onNodeWithText("In the barn: Corn meal ×1", substring = true).assertExists()
    }

    @Test
    fun theMillQueuesThreeAndThenSaysItIsFull() {
        seed(FarmWorld(coins = 0, stars = 6, barn = mapOf("corn" to 20), mill = Mill()))
        show()
        rule.onNodeWithTag("farm-mill").performClick()
        rule.waitForIdle()
        repeat(3) {
            rule.onNodeWithTag("farm-grind-cornmeal").assertIsEnabled().performClick()
            rule.waitForIdle()
        }
        assertEquals(3, stored().mill!!.jobs.size)
        assertEquals(14, stored().count(Item.CORN))
        rule.onNodeWithText("Queue: 3 of 3").assertExists()
        rule.onNodeWithTag("farm-grind-cornmeal").assertIsNotEnabled()
        // Both recipes say so on their own buttons.
        rule.onAllNodesWithText("Queue full").assertCountEquals(2)
        rule.onNodeWithTag("farm-grind-flour").assertIsNotEnabled()
    }

    @Test
    fun collectAllBringsWhatTheMillFinishedAndTheMillShowsItsSack() {
        val t = clock.time
        seed(FarmWorld(coins = 0, stars = 6, mill = Mill(listOf(MillJob("cornmeal", t - 11 * minute), MillJob("flour", t - minute)))))
        show()
        rule.onNodeWithContentDescription("Mill, Corn meal ready: 1").assertExists()
        rule.onNodeWithTag("farm-collect-all").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(1, stored().count(Item.CORN_MEAL))
        assertEquals(1, stored().mill!!.jobs.size)
        rule.onNodeWithText("In the barn: Corn meal ×1", substring = true).assertExists()
        // The flour is still grinding, so the button rests until it is done.
        rule.onNodeWithTag("farm-collect-all").assertIsNotEnabled()
        clock.time += 12 * minute
        rule.onNodeWithTag("farm-mill").performClick()
        rule.waitForIdle()
        assertEquals(1, stored().count(Item.FLOUR))
    }

    @Test
    fun wheatIsNotOnTheSeedListBeforeTheMill() {
        seed(FarmWorld(coins = 100, stars = 6))
        show()
        rule.onNodeWithTag("farm-plot-0").performClick()
        rule.onNodeWithContentDescription("Carrot · coins: 4 · 5 min").assertExists()
        rule.onNodeWithContentDescription("Wheat · coins: 7 · 15 min").assertDoesNotExist()
    }

    @Test
    fun wheatIsOnTheSeedListOnceTheMillIsBuilt() {
        seed(FarmWorld(coins = 100, stars = 6, mill = Mill()))
        show()
        rule.onNodeWithTag("farm-plot-0").performClick()
        rule.onNodeWithContentDescription("Wheat · coins: 7 · 15 min").performClick()
        rule.waitForIdle()
        assertEquals("wheat", stored().plots[0].crop)
        assertEquals(93, stored().coins)
    }

    @Test
    fun tappingAnAnimalShowsHeartsAndStillCollectsItsProduct() {
        val t = clock.time
        seed(FarmWorld(coins = 0, pens = listOf(Pen("chicken", t - 25 * minute, adopted = t - minute))))
        show()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("farm-hearts").assertDoesNotExist()
        rule.onNodeWithTag("farm-pen-0").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("farm-hearts").assertContentDescriptionEquals("Affection: 1/5")
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        // The tap collected the eggs as before, and the pat was kept.
        assertEquals(2, stored().count(Item.EGG))
        assertEquals(1, stored().pens[0].pats)
        // The hearts float away on their own.
        rule.onNodeWithTag("farm-hearts").assertDoesNotExist()
        // A second tap right away shows the hearts again but does not count as another pat.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("farm-pen-0").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("farm-hearts").assertExists()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(1, stored().pens[0].pats)
        // Later, with the cooldown over, it counts again.
        clock.time += 4 * minute
        rule.onNodeWithTag("farm-pen-0").performClick()
        rule.waitForIdle()
        assertEquals(2, stored().pens[0].pats)
    }

    @Test
    fun manyPatsGrowTheHeartsShownOnTheAnimal() {
        val t = clock.time
        seed(FarmWorld(coins = 0, pens = listOf(Pen("cow", t, adopted = t, pats = 8, lastPat = t - 10 * minute))))
        show()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("farm-pen-0").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("farm-hearts").assertContentDescriptionEquals("Affection: 4/5")
    }

    @Test
    fun theAnimalIsDescribedWithItsHearts() {
        val t = clock.time
        seed(FarmWorld(coins = 0, pens = listOf(Pen("cow", t, adopted = t, pats = 3))))
        show()
        rule.onNodeWithTag("farm-pen-0").assertContentDescriptionEquals("Cow, 30 min, Affection: 2/5")
    }

    @Test
    fun tappingAPlantShowsAHeartAndStillHarvests() {
        val t = clock.time
        seed(FarmWorld(coins = 0, plots = listOf(Plot("lettuce", t - 10 * minute), Plot())))
        show()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("farm-plot-0").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("farm-hearts").assertContentDescriptionEquals("Heart")
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(2, stored().count(Item.LETTUCE))
        // An empty bed has nothing to pet: it just opens the sowing card.
        rule.onNodeWithTag("farm-plot-1").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("farm-hearts").assertDoesNotExist()
    }

    @Test
    fun withMovementOffTheHeartStaysAMomentWithoutMoving() {
        val t = clock.time
        seed(FarmWorld(coins = 0, pens = listOf(Pen("sheep", t, adopted = t))))
        show(Settings(motion = Motion.NONE))
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("farm-pen-0").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("farm-hearts").assertExists()
        // Well before the stay is over it is still there; well past it, it is gone.
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag("farm-hearts").assertExists()
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithTag("farm-hearts").assertDoesNotExist()
    }

    @Test
    fun theAchievementsTabShowsWhatIsUnlockedAndWhatIsStillLocked() {
        seed(FarmWorld(coins = 0, stars = 3))
        show()
        rule.onNodeWithTag("farm-tab-achievements").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("farm-achievements").assertExists()
        // Tomato needs three stars: open. The mill needs six: locked, and it says how many are missing.
        rule.onNodeWithContentDescription("Tomato · Stars needed: 3 · Unlocked").assertExists()
        rule.onNodeWithContentDescription("Mill · Stars needed: 6 · Stars missing: 3").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Lake net · Stars needed: 15 · Stars missing: 12").performScrollTo().assertExists()
    }

    @Test
    fun withTheStarsButNoLakeTheNetSaysToBuildTheLakeFirst() {
        seed(FarmWorld(coins = 0, stars = 15))
        show()
        rule.onNodeWithTag("farm-tab-achievements").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Lake net · Stars needed: 15 · Build the lake first").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Wheat · Stars needed: 6 · Build the mill first").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Mill · Stars needed: 6 · Unlocked").performScrollTo().assertExists()
    }
}
