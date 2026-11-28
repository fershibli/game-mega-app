package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.games.farm.Animal
import io.github.fershibli.megagames.games.farm.Building
import io.github.fershibli.megagames.games.farm.Crop
import io.github.fershibli.megagames.games.farm.FarmMap
import io.github.fershibli.megagames.games.farm.FarmRules
import io.github.fershibli.megagames.games.farm.Pen
import io.github.fershibli.megagames.games.farm.FarmWorld
import io.github.fershibli.megagames.games.farm.Item
import io.github.fershibli.megagames.games.farm.FarmSprites
import io.github.fershibli.megagames.games.farm.Mill
import io.github.fershibli.megagames.games.farm.MillJob
import io.github.fershibli.megagames.games.farm.Order
import io.github.fershibli.megagames.games.farm.Plot
import io.github.fershibli.megagames.games.farm.Recipe
import io.github.fershibli.megagames.games.farm.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarmTest {
    private val t0 = 1_800_000_000_000L
    private val min = 60_000L

    @Test
    fun aCropGrowsInRealTimeAndNeverWilts() {
        var w = FarmRules.plant(FarmRules.newFarm(), 0, Crop.CARROT, t0)
        assertEquals(FarmRules.START_COINS - Crop.CARROT.seedCost, w.coins)
        assertEquals(Stage.SPROUT, FarmRules.stage(w.plots[0], t0 + min))
        assertEquals(Stage.GROWING, FarmRules.stage(w.plots[0], t0 + 3 * min))
        assertEquals(Stage.RIPE, FarmRules.stage(w.plots[0], t0 + 5 * min))
        // A week later it is still ripe and waiting.
        assertEquals(Stage.RIPE, FarmRules.stage(w.plots[0], t0 + 7 * 24 * 60 * min))
        w = FarmRules.harvest(w, 0, t0 + 6 * min)
        assertEquals(FarmRules.YIELD, w.count(Item.CARROT))
        assertEquals(null, w.plots[0].crop)
    }

    @Test
    fun anUnripeCropCannotBeHarvestedAndABusyBedCannotBeSown() {
        val w = FarmRules.plant(FarmRules.newFarm(), 0, Crop.LETTUCE, t0)
        assertEquals(w, FarmRules.harvest(w, 0, t0 + min / 2))
        assertEquals(w, FarmRules.plant(w, 0, Crop.CARROT, t0))
    }

    @Test
    fun wateringMakesTheRestGrowTwiceAsFast() {
        var w = FarmRules.plant(FarmRules.newFarm().copy(stars = 1), 0, Crop.CORN, t0) // 10 minutes
        w = FarmRules.water(w, 0, t0 + 4 * min)
        // 4 minutes dry, then 3 watered minutes count as 6: ripe at 7 minutes instead of 10.
        assertEquals(Stage.GROWING, FarmRules.stage(w.plots[0], t0 + 6 * min))
        assertEquals(Stage.RIPE, FarmRules.stage(w.plots[0], t0 + 7 * min))
        assertEquals(min, FarmRules.remainingMs(w.plots[0], t0 + 6 * min))
        // Once is enough.
        assertFalse(FarmRules.canWater(w.plots[0], t0 + 5 * min))
    }

    @Test
    fun seedsNeedCoinsAndStars() {
        val poor = FarmWorld(coins = 1)
        assertEquals(poor, FarmRules.plant(poor, 0, Crop.LETTUCE, t0))
        val noStars = FarmWorld(coins = 500)
        assertEquals(noStars, FarmRules.plant(noStars, 0, Crop.PUMPKIN, t0))
        assertTrue(Crop.PUMPKIN !in FarmRules.unlocked(noStars))
        assertTrue(Crop.PUMPKIN in FarmRules.unlocked(noStars.copy(stars = 8)))
    }

    @Test
    fun animalsMakeProductsUpToThreeAndKeepTheirProgress() {
        var w = FarmRules.buyAnimal(FarmWorld(coins = 100), Animal.CHICKEN, t0)
        assertEquals(60, w.coins)
        val pen = w.pens.single()
        assertEquals(0, FarmRules.waiting(pen, t0 + 9 * min))
        assertEquals(2, FarmRules.waiting(pen, t0 + 25 * min))
        assertEquals(3, FarmRules.waiting(pen, t0 + 24 * 60 * min))
        w = FarmRules.collect(w, 0, t0 + 25 * min)
        assertEquals(2, w.count(Item.EGG))
        // The egg in the making is half done: five more minutes give the next one.
        assertEquals(1, FarmRules.waiting(w.pens[0], t0 + 30 * min))
    }

    @Test
    fun gatherAllTakesRipeCropsAndAnimalProductsTogether() {
        val w = FarmWorld(
            coins = 5,
            plots = listOf(Plot("lettuce", t0), Plot("carrot", t0), Plot("corn", t0 + 30 * min), Plot()),
            pens = listOf(Pen("chicken", t0), Pen("cow", t0)),
        )
        val now = t0 + 35 * min
        assertEquals(2, FarmRules.ripeCount(w, now))
        assertEquals(4, FarmRules.waitingCount(w, now))
        assertEquals(6, FarmRules.readyCount(w, now))
        val (after, brought) = FarmRules.gatherAll(w, now)
        assertEquals(mapOf(Item.LETTUCE to 2, Item.CARROT to 2, Item.EGG to 3, Item.MILK to 1), brought)
        assertEquals(2, after.count(Item.LETTUCE))
        assertEquals(3, after.count(Item.EGG))
        assertEquals(0, FarmRules.ripeCount(after, now))
        assertEquals(0, FarmRules.waitingCount(after, now))
        assertEquals(0, FarmRules.readyCount(after, now))
        // The corn is still growing and stays in its bed.
        assertEquals("corn", after.plots[2].crop)
    }

    @Test
    fun gatherAllWithNothingReadyChangesNothing() {
        val w = FarmWorld(coins = 5, plots = listOf(Plot("corn", t0), Plot()), pens = listOf(Pen("chicken", t0)))
        val now = t0 + 2 * min
        assertEquals(0, FarmRules.readyCount(w, now))
        val (after, brought) = FarmRules.gatherAll(w, now)
        assertEquals(w, after)
        assertTrue(brought.isEmpty())
    }

    @Test
    fun theMarketBuysAndOrdersPayMoreWithAStar() {
        var w = FarmWorld(coins = 0, barn = mapOf("carrot" to 4, "egg" to 1), orders = listOf(Order(mapOf("carrot" to 3), 30)))
        w = FarmRules.sell(w, Item.EGG, 5)
        assertEquals(Item.EGG.price, w.coins)
        assertEquals(0, w.count(Item.EGG))
        assertTrue(FarmRules.canDeliver(w, w.orders[0]))
        w = FarmRules.deliver(w, 0)
        assertEquals(Item.EGG.price + 30, w.coins)
        assertEquals(1, w.stars)
        assertEquals(1, w.count(Item.CARROT))
        assertEquals(FarmRules.ORDERS, w.orders.size)
    }

    @Test
    fun ordersAskForThingsTheFarmCanMakeAndPayMoreThanTheMarket() {
        var w = FarmRules.newFarm()
        repeat(40) { i ->
            val order = FarmRules.newOrder(w, i)
            val value = order.items.entries.sumOf { (k, n) -> Item.fromKey(k)!!.price * n }
            assertTrue(order.coins > value)
            order.items.keys.forEach { assertTrue(it, Item.fromKey(it) in FarmRules.available(w)) }
        }
        w = FarmRules.skip(w, 0)
        assertEquals(FarmRules.ORDERS, w.orders.size)
    }

    @Test
    fun theFieldGrowsUpToTwelveBeds() {
        var w = FarmRules.plant(FarmWorld(coins = 9_999), 0, Crop.LETTUCE, 0L)
        while (w.plots.size < FarmRules.MAX_PLOTS) w = FarmRules.buyPlot(w)
        assertEquals(w, FarmRules.buyPlot(w))
    }

    @Test
    fun aNewBedNeedsSomethingGrowingSoTheFarmCanNeverGetStuck() {
        // A bare field: the last coins can't go on a bed nobody can sow afterwards.
        val bare = FarmWorld(coins = 30)
        assertFalse(FarmRules.canBuyPlot(bare))
        assertEquals(bare, FarmRules.buyPlot(bare))
        // With a crop in the ground the bed is sold, and its harvest brings coins back.
        val growing = FarmRules.plant(bare, 0, Crop.LETTUCE, 0L)
        assertTrue(FarmRules.canBuyPlot(growing))
        val bought = FarmRules.buyPlot(growing)
        assertEquals(FarmRules.START_PLOTS + 1, bought.plots.size)
        assertEquals(growing.coins - FarmRules.plotCost(growing), bought.coins)
        assertFalse(FarmRules.canBuyPlot(FarmRules.plant(FarmWorld(coins = 10), 0, Crop.LETTUCE, 0L)))
    }

    @Test
    fun aFarmLeftWithNothingGetsTheCoinsForOneSeed() {
        val stuck = FarmWorld(coins = 1)
        assertEquals(Crop.LETTUCE.seedCost, FarmRules.rescue(stuck).coins)
        // Anything that can still bring coins back means it isn't stuck.
        assertEquals(1, FarmRules.rescue(FarmRules.plant(FarmWorld(coins = 3), 0, Crop.LETTUCE, 0L).copy(coins = 1)).coins)
        assertEquals(1, FarmRules.rescue(stuck.copy(barn = mapOf("egg" to 1))).coins)
        assertEquals(1, FarmRules.rescue(stuck.copy(pens = listOf(Pen("chicken", 0L)))).coins)
        assertEquals(30, FarmRules.rescue(FarmWorld()).coins)
    }

    @Test
    fun theAnimalsStandOnTheirOwnGroundAwayFromTheBeds() {
        val lastBedRow = FarmMap.plots.maxOf { it.second }
        // A strip of grass lies between the field and the paddock, which holds every animal.
        assertTrue(FarmMap.paddockRows.first > lastBedRow + 1)
        FarmMap.pens.forEach { assertTrue(it.second in FarmMap.paddockRows) }
        assertTrue(FarmMap.plots.none { it.second in FarmMap.paddockRows })
        // A tap on a bed or an animal always finds that one, never a neighbour of the other kind.
        val grid = FarmMap.grid(800f, 1200f, FarmMap.columns(FarmMap.plots.size, FarmMap.pens.size))
        FarmMap.plots.forEachIndexed { i, (c, r) -> assertEquals(true to i, FarmMap.hit(grid, grid.center(c, r), FarmMap.plots.size, FarmMap.pens.size)) }
        FarmMap.pens.forEachIndexed { i, (c, r) -> assertEquals(false to i, FarmMap.hit(grid, grid.center(c, r), FarmMap.plots.size, FarmMap.pens.size)) }
    }

    // The mill, wheat and the Build list.

    private fun withMill(coins: Int = 0, barn: Map<String, Int> = emptyMap(), jobs: List<MillJob> = emptyList()) =
        FarmWorld(coins = coins, stars = 6, barn = barn, mill = Mill(jobs))

    @Test
    fun theMillNeedsSixStarsAnd120CoinsAndIsBuiltOnlyOnce() {
        assertEquals(6, Building.MILL.stars)
        assertEquals(120, Building.MILL.cost)
        val fewStars = FarmWorld(coins = 500, stars = 5)
        assertFalse(FarmRules.canBuild(fewStars, Building.MILL))
        assertEquals(1, FarmRules.starsMissing(fewStars, Building.MILL))
        assertEquals(fewStars, FarmRules.build(fewStars, Building.MILL))
        val fewCoins = FarmWorld(coins = 119, stars = 8)
        assertFalse(FarmRules.canBuild(fewCoins, Building.MILL))
        assertEquals(0, FarmRules.starsMissing(fewCoins, Building.MILL))
        assertEquals(fewCoins, FarmRules.build(fewCoins, Building.MILL))
        val ready = FarmWorld(coins = 130, stars = 6)
        assertEquals(listOf(Building.MILL), FarmRules.toBuild(ready))
        val built = FarmRules.build(ready, Building.MILL)
        assertEquals(10, built.coins)
        assertNotNull(built.mill)
        assertTrue(FarmRules.isBuilt(built, Building.MILL))
        // Everything built: the Build button has nothing left to show, and nothing is charged twice.
        assertTrue(FarmRules.toBuild(built).isEmpty())
        val rich = built.copy(coins = 999)
        assertEquals(rich, FarmRules.build(rich, Building.MILL))
    }

    @Test
    fun wheatIsOfferedOnlyOnceTheMillIsBuilt() {
        assertEquals(7, Crop.WHEAT.seedCost)
        assertEquals(15, Crop.WHEAT.minutes)
        assertEquals(9, Item.WHEAT.price)
        val rich = FarmWorld(coins = 200, stars = 8)
        assertTrue(Crop.WHEAT !in FarmRules.unlocked(rich))
        assertEquals(rich, FarmRules.plant(rich, 0, Crop.WHEAT, t0))
        val milled = FarmRules.build(rich, Building.MILL)
        assertTrue(Crop.WHEAT in FarmRules.unlocked(milled))
        var w = FarmRules.plant(milled, 0, Crop.WHEAT, t0)
        assertEquals(milled.coins - 7, w.coins)
        assertEquals(Stage.SPROUT, FarmRules.stage(w.plots[0], t0 + 5 * min))
        assertEquals(Stage.GROWING, FarmRules.stage(w.plots[0], t0 + 8 * min))
        assertEquals(Stage.GROWING, FarmRules.stage(w.plots[0], t0 + 14 * min))
        assertEquals(Stage.RIPE, FarmRules.stage(w.plots[0], t0 + 15 * min))
        w = FarmRules.harvest(w, 0, t0 + 15 * min)
        assertEquals(FarmRules.YIELD, w.count(Item.WHEAT))
    }

    @Test
    fun grindingTakesTheIngredientsAndFinishesAfterItsTime() {
        assertEquals(2, Recipe.CORN_MEAL.amount)
        assertEquals(10, Recipe.CORN_MEAL.minutes)
        assertEquals(22, Item.CORN_MEAL.price)
        assertEquals(2, Recipe.FLOUR.amount)
        assertEquals(12, Recipe.FLOUR.minutes)
        assertEquals(26, Item.FLOUR.price)
        var w = withMill(barn = mapOf("corn" to 5, "egg" to 1))
        w = FarmRules.grind(w, Recipe.CORN_MEAL, t0)
        assertEquals(3, w.count(Item.CORN))
        assertEquals(1, w.count(Item.EGG))
        assertEquals(1, FarmRules.millJobs(w))
        assertTrue(FarmRules.millWorking(w, t0 + min))
        assertEquals(0.5f, FarmRules.millProgress(w, t0 + 5 * min), 0.001f)
        assertEquals(5 * min, FarmRules.millRemainingMs(w, t0 + 5 * min))
        assertEquals(0, FarmRules.millDone(w, t0 + 9 * min))
        // Nothing to collect yet: the barn gets nothing.
        assertEquals(w, FarmRules.collectMill(w, t0 + 9 * min))
        assertEquals(1, FarmRules.millDone(w, t0 + 10 * min))
        assertFalse(FarmRules.millWorking(w, t0 + 10 * min))
        w = FarmRules.collectMill(w, t0 + 10 * min)
        assertEquals(1, w.count(Item.CORN_MEAL))
        assertEquals(0, FarmRules.millJobs(w))
        // A sack that is ready waits for as long as it takes, and nothing spoils.
        val later = FarmRules.grind(withMill(barn = mapOf("wheat" to 2)), Recipe.FLOUR, t0)
        assertEquals(1, FarmRules.millDone(later, t0 + 30 * 24 * 60 * min))
        assertEquals(1, FarmRules.collectMill(later, t0 + 30 * 24 * 60 * min).count(Item.FLOUR))
    }

    @Test
    fun theMillRefusesWithoutIngredientsOrWithoutAMill() {
        val short = withMill(barn = mapOf("corn" to 1))
        assertEquals(1, FarmRules.missing(short, Recipe.CORN_MEAL))
        assertEquals(2, FarmRules.missing(short, Recipe.FLOUR))
        assertFalse(FarmRules.canGrind(short, Recipe.CORN_MEAL))
        assertEquals(short, FarmRules.grind(short, Recipe.CORN_MEAL, t0))
        assertEquals(short, FarmRules.grind(short, Recipe.FLOUR, t0))
        val noMill = FarmWorld(barn = mapOf("corn" to 9))
        assertFalse(FarmRules.canGrind(noMill, Recipe.CORN_MEAL))
        assertEquals(noMill, FarmRules.grind(noMill, Recipe.CORN_MEAL, t0))
        assertEquals(noMill, FarmRules.collectMill(noMill, t0))
        assertEquals(0, FarmRules.missing(withMill(barn = mapOf("corn" to 2)), Recipe.CORN_MEAL))
    }

    @Test
    fun theMillWorksOnOneRecipeAtATimeAndQueuesUpToThree() {
        var w = withMill(barn = mapOf("corn" to 20, "wheat" to 20))
        w = FarmRules.grind(w, Recipe.CORN_MEAL, t0)
        w = FarmRules.grind(w, Recipe.FLOUR, t0 + min)
        w = FarmRules.grind(w, Recipe.CORN_MEAL, t0 + 2 * min)
        assertEquals(3, FarmRules.millJobs(w))
        // The fourth is refused and takes nothing from the barn.
        assertFalse(FarmRules.canGrind(w, Recipe.CORN_MEAL))
        assertEquals(w, FarmRules.grind(w, Recipe.CORN_MEAL, t0 + 3 * min))
        // One after the other: 10 minutes, then 12, then 10.
        assertEquals(listOf(t0, t0 + 10 * min, t0 + 22 * min), w.mill!!.jobs.map { it.start })
        assertEquals(Item.CORN_MEAL, FarmRules.millCurrent(w, t0 + 5 * min)!!.kind.output)
        assertEquals(Item.FLOUR, FarmRules.millCurrent(w, t0 + 11 * min)!!.kind.output)
        assertEquals(1, FarmRules.millDone(w, t0 + 11 * min))
        // Collecting takes what is done and the rest goes on grinding.
        val part = FarmRules.collectMill(w, t0 + 25 * min)
        assertEquals(1, part.count(Item.CORN_MEAL))
        assertEquals(1, part.count(Item.FLOUR))
        assertEquals(1, FarmRules.millJobs(part))
        assertEquals(Item.CORN_MEAL, FarmRules.millCurrent(part, t0 + 25 * min)!!.kind.output)
        // Room again: a new job waits for the one running.
        val again = FarmRules.grind(part, Recipe.FLOUR, t0 + 25 * min)
        assertEquals(t0 + 32 * min, again.mill!!.jobs.last().start)
    }

    @Test
    fun aNewJobOnAnIdleMillStartsNowEvenAfterLongRests() {
        var w = withMill(barn = mapOf("corn" to 4))
        w = FarmRules.grind(w, Recipe.CORN_MEAL, t0)
        val later = t0 + 3 * 24 * 60 * min
        w = FarmRules.collectMill(w, later)
        w = FarmRules.grind(w, Recipe.CORN_MEAL, later)
        assertEquals(later, w.mill!!.jobs.single().start)
        assertEquals(0f, FarmRules.millProgress(w, later), 0.001f)
    }

    @Test
    fun collectAllAlsoBringsTheMillsSacksAndTheButtonCountsThem() {
        val w = FarmWorld(
            coins = 5, stars = 6, plots = listOf(Plot("lettuce", t0), Plot()),
            mill = Mill(listOf(MillJob("cornmeal", t0), MillJob("flour", t0 + 10 * min), MillJob("cornmeal", t0 + 22 * min))),
        )
        val now = t0 + 25 * min
        assertEquals(1, FarmRules.ripeCount(w, now))
        assertEquals(2, FarmRules.millDone(w, now))
        assertEquals(3, FarmRules.readyCount(w, now))
        val (after, brought) = FarmRules.gatherAll(w, now)
        assertEquals(mapOf(Item.LETTUCE to 2, Item.CORN_MEAL to 1, Item.FLOUR to 1), brought)
        assertEquals(1, after.count(Item.CORN_MEAL))
        assertEquals(1, FarmRules.millJobs(after))
        assertEquals(0, FarmRules.readyCount(after, now))
        // The mill alone is enough to light the button.
        assertEquals(1, FarmRules.readyCount(withMill(jobs = listOf(MillJob("flour", t0))), t0 + 12 * min))
        assertEquals(0, FarmRules.readyCount(withMill(jobs = listOf(MillJob("flour", t0))), t0 + 11 * min))
    }

    @Test
    fun theNeighboursAskForTheMillsProductsOnlyOnceItIsBuilt() {
        val plain = FarmWorld(stars = 8)
        assertTrue(Item.CORN_MEAL !in FarmRules.available(plain))
        assertTrue(Item.WHEAT !in FarmRules.available(plain))
        val milled = FarmRules.build(plain.copy(coins = 200), Building.MILL)
        assertTrue(FarmRules.available(milled).containsAll(listOf(Item.WHEAT, Item.CORN_MEAL, Item.FLOUR)))
        val asked = HashSet<String>()
        repeat(120) { i ->
            val order = FarmRules.newOrder(milled, i)
            val value = order.items.entries.sumOf { (k, n) -> Item.fromKey(k)!!.price * n }
            assertTrue(order.coins > value)
            order.items.keys.forEach { assertTrue(it, Item.fromKey(it) in FarmRules.available(milled)) }
            asked += order.items.keys
        }
        assertTrue("cornmeal" in asked)
        assertTrue("flour" in asked)
        // Before the mill, no order mentions them.
        repeat(120) { i -> FarmRules.newOrder(plain, i).items.keys.forEach { assertTrue(it, it != "cornmeal" && it != "flour" && it != "wheat") } }
        // And they can be delivered like anything else.
        var w = milled.copy(barn = mapOf("flour" to 2), orders = listOf(Order(mapOf("flour" to 2), 90)))
        assertTrue(FarmRules.canDeliver(w, w.orders[0]))
        w = FarmRules.deliver(w, 0)
        assertEquals(milled.stars + 1, w.stars)
        assertEquals(0, w.count(Item.FLOUR))
    }

    @Test
    fun anOldFarmLoadsUnchangedAndGetsTheMillWhenItReachesTheStars() {
        val old = """{"coins":42,"stars":3,"plots":[{"crop":"corn","plantedAt":5}],"pens":[{"animal":"cow","since":7}],"barn":{"corn":4,"egg":1},"orders":[{"items":{"egg":1},"coins":20}],"orderSerial":9}"""
        val w = AppJson.decodeFromString(FarmWorld.serializer(), old)
        assertNull(w.mill)
        assertEquals(42, w.coins)
        assertEquals(3, w.stars)
        assertEquals(4, w.count(Item.CORN))
        assertEquals("corn", w.plots.single().crop)
        assertEquals(listOf(Building.MILL), FarmRules.toBuild(w))
        assertEquals(2, FarmRules.starsMissing(w.copy(stars = 4), Building.MILL))
        val grown = FarmRules.build(w.copy(stars = 6, coins = 120), Building.MILL)
        assertNotNull(grown.mill)
        // A farm with a mill round-trips through the save.
        val ground = FarmRules.grind(grown.copy(barn = mapOf("corn" to 2)), Recipe.CORN_MEAL, t0)
        val back = AppJson.decodeFromString(FarmWorld.serializer(), AppJson.encodeToString(FarmWorld.serializer(), ground))
        assertEquals(ground, back)
        assertEquals("cornmeal", back.mill!!.jobs[0].recipe)
    }

    @Test
    fun savedKeysNeverChange() {
        val items = Item.entries.associate { it.name to it.key }
        assertEquals("lettuce", items["LETTUCE"])
        assertEquals("corn", items["CORN"])
        assertEquals("wool", items["WOOL"])
        assertEquals("wheat", items["WHEAT"])
        assertEquals("cornmeal", items["CORN_MEAL"])
        assertEquals("flour", items["FLOUR"])
        assertEquals("wheat", Crop.WHEAT.key)
        assertEquals("mill", Building.MILL.key)
        assertEquals(listOf("cornmeal", "flour"), Recipe.entries.map { it.key })
        assertEquals(Item.entries.size, Item.entries.map { it.key }.toSet().size)
        assertEquals(Crop.entries.size, Crop.entries.map { it.key }.toSet().size)
    }

    @Test
    fun aMillWithJobsKeepsTheFarmFromBeingStuck() {
        val busy = FarmWorld(coins = 0, stars = 6, mill = Mill(listOf(MillJob("flour", t0))))
        assertEquals(0, FarmRules.rescue(busy).coins)
        assertEquals(Crop.LETTUCE.seedCost, FarmRules.rescue(busy.copy(mill = Mill())).coins)
    }

    @Test
    fun theMillStandsInAnEmptySpotWithoutGrowingTheFarmAndItsPictureTurns() {
        // The farm is as wide as before, whatever is built.
        assertEquals(7, FarmMap.columns(FarmRules.START_PLOTS, 0))
        val grid = FarmMap.grid(800f, 1200f, FarmMap.columns(FarmRules.START_PLOTS, 0))
        val bounds = FarmMap.millBounds(grid)
        assertTrue("$bounds", bounds.left >= 0f)
        assertTrue("$bounds", bounds.right <= 800f)
        assertTrue("$bounds", bounds.top >= 0f)
        // It stands on the back row, behind every bed.
        assertTrue(FarmMap.plots.all { it.second > FarmMap.MILL_ROW + 1 })
        // At least two sail frames, all the same size.
        val frames = FarmSprites.mill
        assertTrue(frames.size >= 2)
        assertEquals(1, frames.map { it.width to it.height }.toSet().size)
    }
}
