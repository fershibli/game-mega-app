package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.games.farm.Animal
import io.github.fershibli.megagames.games.farm.Crop
import io.github.fershibli.megagames.games.farm.FarmRules
import io.github.fershibli.megagames.games.farm.Pen
import io.github.fershibli.megagames.games.farm.FarmWorld
import io.github.fershibli.megagames.games.farm.Item
import io.github.fershibli.megagames.games.farm.Order
import io.github.fershibli.megagames.games.farm.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
