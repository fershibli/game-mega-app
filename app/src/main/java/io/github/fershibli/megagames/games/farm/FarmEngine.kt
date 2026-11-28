package io.github.fershibli.megagames.games.farm

import androidx.annotation.StringRes
import io.github.fershibli.megagames.R
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Key of the farm's file in the [io.github.fershibli.megagames.core.WorldStore]. Never change it. */
const val FARM_WORLD_KEY = "farm"

/** Where the time comes from. The screen uses the phone's clock; tests move time forward by hand. */
fun interface FarmClock {
    fun now(): Long
}

val SystemFarmClock = FarmClock { System.currentTimeMillis() }

private const val MINUTE = 60_000L

/**
 * Everything the farm makes and keeps in the barn. The [key] is saved, so it must never change.
 * [price] is what the market pays for one.
 */
enum class Item(val key: String, @StringRes val label: Int, val price: Int) {
    LETTUCE("lettuce", R.string.farm_item_lettuce, 3),
    CARROT("carrot", R.string.farm_item_carrot, 5),
    CORN("corn", R.string.farm_item_corn, 8),
    TOMATO("tomato", R.string.farm_item_tomato, 12),
    STRAWBERRY("strawberry", R.string.farm_item_strawberry, 16),
    PUMPKIN("pumpkin", R.string.farm_item_pumpkin, 28),
    EGG("egg", R.string.farm_item_egg, 10),
    MILK("milk", R.string.farm_item_milk, 22),
    WOOL("wool", R.string.farm_item_wool, 40),
    WHEAT("wheat", R.string.farm_item_wheat, 9),
    CORN_MEAL("cornmeal", R.string.farm_item_cornmeal, 22),
    FLOUR("flour", R.string.farm_item_flour, 26),
    ;

    companion object {
        fun fromKey(key: String?): Item? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What can be sown. A crop grows in [minutes] of real time (half as long once watered) and gives
 * [YIELD] of its [item]. It never wilts: a ripe crop waits for as long as it takes. A crop is
 * offered once the farm has [stars] (earned by delivering orders) and, for [needsMill], once the
 * mill is built.
 */
enum class Crop(val key: String, val item: Item, val minutes: Int, val seedCost: Int, val stars: Int, val needsMill: Boolean = false) {
    LETTUCE("lettuce", Item.LETTUCE, 2, 2, 0),
    CARROT("carrot", Item.CARROT, 5, 4, 0),
    CORN("corn", Item.CORN, 10, 6, 1),
    TOMATO("tomato", Item.TOMATO, 20, 9, 3),
    STRAWBERRY("strawberry", Item.STRAWBERRY, 40, 12, 5),
    PUMPKIN("pumpkin", Item.PUMPKIN, 90, 20, 8),
    WHEAT("wheat", Item.WHEAT, 15, 7, 0, needsMill = true),
    ;

    val growMs: Long get() = minutes * MINUTE

    companion object {
        fun fromKey(key: String?): Crop? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Farm animals. Each one makes one [item] every [minutes] and keeps up to [MAX_WAITING] of them
 * waiting to be collected; it never needs feeding and never gets ill.
 */
enum class Animal(val key: String, @StringRes val label: Int, val item: Item, val minutes: Int, val cost: Int, val stars: Int) {
    CHICKEN("chicken", R.string.farm_animal_chicken, Item.EGG, 10, 40, 0),
    COW("cow", R.string.farm_animal_cow, Item.MILK, 30, 120, 2),
    SHEEP("sheep", R.string.farm_animal_sheep, Item.WOOL, 60, 200, 4),
    ;

    val periodMs: Long get() = minutes * MINUTE

    companion object {
        fun fromKey(key: String?): Animal? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What the mill grinds: [amount] of [input] from the barn become one [output] after [minutes].
 * The [key] is saved in the queue, so it must never change.
 */
enum class Recipe(val key: String, val input: Item, val amount: Int, val output: Item, val minutes: Int) {
    CORN_MEAL("cornmeal", Item.CORN, 2, Item.CORN_MEAL, 10),
    FLOUR("flour", Item.WHEAT, 2, Item.FLOUR, 12),
    ;

    val durationMs: Long get() = minutes * MINUTE

    companion object {
        fun fromKey(key: String?): Recipe? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Things the farm can build. Each costs [cost] coins and needs [stars]. The [key] never changes.
 * The lake joins this list later.
 */
enum class Building(val key: String, @StringRes val label: Int, val cost: Int, val stars: Int) {
    MILL("mill", R.string.farm_building_mill, 120, 6),
    ;

    companion object {
        fun fromKey(key: String?): Building? = entries.firstOrNull { it.key == key }
    }
}

/** One bed of the field: empty, or sown with [crop] at [plantedAt] (and maybe watered at [wateredAt]). */
@Serializable
data class Plot(val crop: String? = null, val plantedAt: Long = 0, val wateredAt: Long? = null) {
    val sown: Crop? get() = Crop.fromKey(crop)
}

/** One animal and the moment its next product started being made. */
@Serializable
data class Pen(val animal: String, val since: Long) {
    val kind: Animal get() = Animal.fromKey(animal) ?: Animal.CHICKEN
}

/** One grinding job in the mill's queue: what it makes and the moment it starts (or started). */
@Serializable
data class MillJob(val recipe: String, val start: Long) {
    val kind: Recipe get() = Recipe.fromKey(recipe) ?: Recipe.CORN_MEAL
    val end: Long get() = start + kind.durationMs
}

/** The mill once built: its jobs in order; the finished ones wait there until they are collected. */
@Serializable
data class Mill(val jobs: List<MillJob> = emptyList())

/** A neighbour's order: these items, for [coins] and one star. */
@Serializable
data class Order(val items: Map<String, Int>, val coins: Int)

/** The whole farm. It is kept forever in the world store; nothing in it can ever be lost. */
@Serializable
data class FarmWorld(
    val coins: Int = FarmRules.START_COINS,
    val stars: Int = 0,
    val plots: List<Plot> = List(FarmRules.START_PLOTS) { Plot() },
    val pens: List<Pen> = emptyList(),
    val barn: Map<String, Int> = emptyMap(),
    val orders: List<Order> = emptyList(),
    /** How many orders were made so far: the seed of the next one, so orders never repeat in a row. */
    val orderSerial: Int = 0,
    /** The mill, once built; null before. */
    val mill: Mill? = null,
) {
    fun count(item: Item): Int = barn[item.key] ?: 0
}

/** What a bed shows. */
enum class Stage { EMPTY, SPROUT, GROWING, RIPE }

/** The rules of the farm: growth from timestamps, buying, selling and orders. Pure Kotlin. */
object FarmRules {
    const val START_COINS = 30
    const val START_PLOTS = 6
    const val MAX_PLOTS = 12
    const val MAX_PENS = 6
    const val MAX_WAITING = 3
    const val YIELD = 2
    const val ORDERS = 3

    /** The mill grinds one thing at a time and holds up to this many jobs, finished or not. */
    const val MILL_QUEUE = 3

    /** Price of the next bed: dearer as the field grows. */
    fun plotCost(world: FarmWorld): Int = 25 * (world.plots.size - START_PLOTS + 1)

    /** Milliseconds of growth a bed has had: watered time counts twice. */
    private fun grown(plot: Plot, now: Long): Long =
        (now - plot.plantedAt).coerceAtLeast(0L) + (plot.wateredAt?.let { (now - it).coerceAtLeast(0L) } ?: 0L)

    /** How grown a bed is, from 0 to 1. After watering it grows twice as fast. */
    fun progress(plot: Plot, now: Long): Float {
        val crop = plot.sown ?: return 0f
        return (grown(plot, now).toDouble() / crop.growMs).coerceIn(0.0, 1.0).toFloat()
    }

    fun stage(plot: Plot, now: Long): Stage {
        if (plot.sown == null) return Stage.EMPTY
        val g = progress(plot, now)
        return when {
            g >= 1f -> Stage.RIPE
            g >= 0.5f -> Stage.GROWING
            else -> Stage.SPROUT
        }
    }

    /** Milliseconds until a bed is ripe (0 when it already is). */
    fun remainingMs(plot: Plot, now: Long): Long {
        val crop = plot.sown ?: return 0L
        val left = (crop.growMs - grown(plot, now)).coerceAtLeast(0L)
        return if (plot.wateredAt != null) (left + 1) / 2 else left
    }

    fun unlocked(world: FarmWorld): List<Crop> =
        Crop.entries.filter { it.stars <= world.stars && (!it.needsMill || world.mill != null) }

    fun canWater(plot: Plot, now: Long): Boolean = plot.sown != null && plot.wateredAt == null && stage(plot, now) != Stage.RIPE

    fun plant(world: FarmWorld, index: Int, crop: Crop, now: Long): FarmWorld {
        val plot = world.plots.getOrNull(index) ?: return world
        if (plot.sown != null || world.coins < crop.seedCost || crop !in unlocked(world)) return world
        return world.copy(coins = world.coins - crop.seedCost, plots = world.plots.replace(index, Plot(crop.key, now)))
    }

    fun water(world: FarmWorld, index: Int, now: Long): FarmWorld {
        val plot = world.plots.getOrNull(index) ?: return world
        if (!canWater(plot, now)) return world
        return world.copy(plots = world.plots.replace(index, plot.copy(wateredAt = now)))
    }

    fun waterAll(world: FarmWorld, now: Long): FarmWorld =
        world.copy(plots = world.plots.map { if (canWater(it, now)) it.copy(wateredAt = now) else it })

    fun harvest(world: FarmWorld, index: Int, now: Long): FarmWorld {
        val plot = world.plots.getOrNull(index) ?: return world
        val crop = plot.sown ?: return world
        if (stage(plot, now) != Stage.RIPE) return world
        return world.copy(plots = world.plots.replace(index, Plot()), barn = world.barn.add(crop.item, YIELD))
    }

    fun harvestAll(world: FarmWorld, now: Long): FarmWorld =
        world.plots.indices.fold(world) { w, i -> harvest(w, i, now) }

    fun ripeCount(world: FarmWorld, now: Long): Int = world.plots.count { stage(it, now) == Stage.RIPE }

    /** Whether a crop is in the ground: its harvest will always bring coins back. */
    fun somethingGrowing(world: FarmWorld): Boolean = world.plots.any { it.sown != null }

    /**
     * A new bed can be bought only while something grows: spending the last coins on an empty
     * field could leave the farm with nothing to sow and nothing to sell.
     */
    fun canBuyPlot(world: FarmWorld): Boolean =
        world.plots.size < MAX_PLOTS && world.coins >= plotCost(world) && somethingGrowing(world)

    fun buyPlot(world: FarmWorld): FarmWorld {
        if (!canBuyPlot(world)) return world
        return world.copy(coins = world.coins - plotCost(world), plots = world.plots + Plot())
    }

    /**
     * A farm that can't go on (nothing growing, no animals, an empty barn and not even the coins
     * for the cheapest seed) gets back just enough for it. Farms saved before beds needed a crop
     * growing could end up like that.
     */
    fun rescue(world: FarmWorld): FarmWorld {
        val seed = Crop.entries.minOf { it.seedCost }
        val stuck = !somethingGrowing(world) && world.pens.isEmpty() && world.mill?.jobs.isNullOrEmpty() && world.barn.values.all { it <= 0 } && world.coins < seed
        return if (stuck) world.copy(coins = seed) else world
    }

    // Animals.

    /** Products an animal has waiting, from 0 to [MAX_WAITING]. */
    fun waiting(pen: Pen, now: Long): Int = ((now - pen.since).coerceAtLeast(0L) / pen.kind.periodMs).toInt().coerceIn(0, MAX_WAITING)

    /** Share of the next product already made, from 0 to 1 (1 when the animal is full). */
    fun penProgress(pen: Pen, now: Long): Float {
        if (waiting(pen, now) >= MAX_WAITING) return 1f
        return (((now - pen.since).coerceAtLeast(0L) % pen.kind.periodMs).toDouble() / pen.kind.periodMs).toFloat()
    }

    fun buyAnimal(world: FarmWorld, animal: Animal, now: Long): FarmWorld {
        if (world.pens.size >= MAX_PENS || world.coins < animal.cost || animal.stars > world.stars) return world
        return world.copy(coins = world.coins - animal.cost, pens = world.pens + Pen(animal.key, now))
    }

    fun collect(world: FarmWorld, index: Int, now: Long): FarmWorld {
        val pen = world.pens.getOrNull(index) ?: return world
        val n = waiting(pen, now)
        if (n == 0) return world
        // A full animal starts afresh; otherwise the product in the making keeps its progress.
        val since = if (n >= MAX_WAITING) now else pen.since + n * pen.kind.periodMs
        return world.copy(pens = world.pens.replace(index, pen.copy(since = since)), barn = world.barn.add(pen.kind.item, n))
    }

    fun collectAll(world: FarmWorld, now: Long): FarmWorld = world.pens.indices.fold(world) { w, i -> collect(w, i, now) }

    fun waitingCount(world: FarmWorld, now: Long): Int = world.pens.sumOf { waiting(it, now) }

    // Buildings.

    fun isBuilt(world: FarmWorld, building: Building): Boolean = when (building) {
        Building.MILL -> world.mill != null
    }

    /** The buildings not built yet: the Build button lists them and goes away when none is left. */
    fun toBuild(world: FarmWorld): List<Building> = Building.entries.filter { !isBuilt(world, it) }

    /** Stars still missing for [building] (0 when the farm has enough). */
    fun starsMissing(world: FarmWorld, building: Building): Int = (building.stars - world.stars).coerceAtLeast(0)

    fun canBuild(world: FarmWorld, building: Building): Boolean =
        !isBuilt(world, building) && world.stars >= building.stars && world.coins >= building.cost

    fun build(world: FarmWorld, building: Building): FarmWorld {
        if (!canBuild(world, building)) return world
        val paid = world.copy(coins = world.coins - building.cost)
        return when (building) {
            Building.MILL -> paid.copy(mill = Mill())
        }
    }

    // The mill.

    /** Jobs the mill has finished by [now], waiting to be collected. */
    fun millDone(world: FarmWorld, now: Long): Int = world.mill?.jobs?.count { it.end <= now } ?: 0

    /** What the mill has finished by [now], by item: what a tap on it brings to the barn. */
    fun millReady(world: FarmWorld, now: Long): Map<Item, Int> =
        world.mill?.jobs?.filter { it.end <= now }?.groupingBy { it.kind.output }?.eachCount() ?: emptyMap()

    /** Jobs in the queue, finished or not. */
    fun millJobs(world: FarmWorld): Int = world.mill?.jobs?.size ?: 0

    /** The job being ground at [now] (the first one not finished), or null when the mill is idle. */
    fun millCurrent(world: FarmWorld, now: Long): MillJob? = world.mill?.jobs?.firstOrNull { it.end > now }

    /** Whether the sails turn: a job is not finished yet. */
    fun millWorking(world: FarmWorld, now: Long): Boolean = millCurrent(world, now) != null

    /** Share of the job being ground now, from 0 to 1 (0 when the mill is idle). */
    fun millProgress(world: FarmWorld, now: Long): Float {
        val job = millCurrent(world, now) ?: return 0f
        return ((now - job.start).coerceAtLeast(0L).toDouble() / job.kind.durationMs).coerceIn(0.0, 1.0).toFloat()
    }

    /** Milliseconds until the job being ground now is finished (0 when the mill is idle). */
    fun millRemainingMs(world: FarmWorld, now: Long): Long = millCurrent(world, now)?.let { (it.end - now).coerceAtLeast(0L) } ?: 0L

    /** How many of the recipe's ingredient the barn lacks (0 when it has enough). */
    fun missing(world: FarmWorld, recipe: Recipe): Int = (recipe.amount - world.count(recipe.input)).coerceAtLeast(0)

    fun canGrind(world: FarmWorld, recipe: Recipe): Boolean =
        world.mill != null && millJobs(world) < MILL_QUEUE && missing(world, recipe) == 0

    /**
     * Takes the ingredients from the barn and puts the recipe at the end of the queue. It starts
     * now, or when the job before it ends.
     */
    fun grind(world: FarmWorld, recipe: Recipe, now: Long): FarmWorld {
        val mill = world.mill ?: return world
        if (!canGrind(world, recipe)) return world
        val start = maxOf(now, mill.jobs.maxOfOrNull { it.end } ?: now)
        return world.copy(
            barn = world.barn.add(recipe.input, -recipe.amount),
            mill = mill.copy(jobs = mill.jobs + MillJob(recipe.key, start)),
        )
    }

    /** Moves everything the mill has finished into the barn; what is still grinding goes on. */
    fun collectMill(world: FarmWorld, now: Long): FarmWorld {
        val mill = world.mill ?: return world
        val (done, rest) = mill.jobs.partition { it.end <= now }
        if (done.isEmpty()) return world
        return world.copy(barn = done.fold(world.barn) { barn, job -> barn.add(job.kind.output, 1) }, mill = mill.copy(jobs = rest))
    }

    // Gathering everything at once.

    /**
     * Brings into the barn everything that is ready: every ripe bed, every animal's products and
     * what the mill has ground. Returns the new farm and what entered the barn, by item. Later
     * sources (the lake) join here, so the one button keeps gathering everything.
     */
    fun gatherAll(world: FarmWorld, now: Long): Pair<FarmWorld, Map<Item, Int>> {
        val gathered = collectMill(collectAll(harvestAll(world, now), now), now)
        val brought = Item.entries.mapNotNull { item ->
            val n = gathered.count(item) - world.count(item)
            if (n > 0) item to n else null
        }.toMap()
        return gathered to brought
    }

    /** How many things are ready to gather: ripe beds, products waiting and jobs the mill finished. */
    fun readyCount(world: FarmWorld, now: Long): Int = ripeCount(world, now) + waitingCount(world, now) + millDone(world, now)

    // The market.

    fun sell(world: FarmWorld, item: Item, count: Int): FarmWorld {
        val n = count.coerceAtMost(world.count(item))
        if (n <= 0) return world
        return world.copy(coins = world.coins + n * item.price, barn = world.barn.add(item, -n))
    }

    // Orders.

    /** Items the farm can make now: unlocked crops, its animals' products and what its mill grinds. */
    fun available(world: FarmWorld): List<Item> =
        unlocked(world).map { it.item } + world.pens.map { it.kind.item }.distinct() +
            if (world.mill != null) Recipe.entries.map { it.output } else emptyList()

    /** Tops the board up to [ORDERS] orders. */
    fun fillOrders(world: FarmWorld): FarmWorld {
        var w = world
        while (w.orders.size < ORDERS) w = w.copy(orders = w.orders + newOrder(w, w.orderSerial), orderSerial = w.orderSerial + 1)
        return w
    }

    /** An order of one to three kinds of item the farm can make, paying more than the market. */
    fun newOrder(world: FarmWorld, serial: Int): Order {
        val random = Random(serial * 7919 + 17)
        val pool = available(world).shuffled(random)
        val kinds = (1 + random.nextInt(minOf(3, pool.size))).coerceAtMost(pool.size)
        val items = pool.take(kinds).associate { it.key to (1 + random.nextInt(if (it.price >= 20) 2 else 4)) }
        val value = items.entries.sumOf { (k, n) -> (Item.fromKey(k)?.price ?: 0) * n }
        return Order(items, value + value / 2 + 5)
    }

    fun canDeliver(world: FarmWorld, order: Order): Boolean =
        order.items.all { (k, n) -> (world.barn[k] ?: 0) >= n }

    fun deliver(world: FarmWorld, index: Int): FarmWorld {
        val order = world.orders.getOrNull(index) ?: return world
        if (!canDeliver(world, order)) return world
        var barn = world.barn
        order.items.forEach { (k, n) -> Item.fromKey(k)?.let { barn = barn.add(it, -n) } }
        val after = world.copy(coins = world.coins + order.coins, stars = world.stars + 1, barn = barn, orders = world.orders.filterIndexed { i, _ -> i != index })
        return fillOrders(after)
    }

    /** Swaps an order for another one, free of charge. */
    fun skip(world: FarmWorld, index: Int): FarmWorld {
        if (index !in world.orders.indices) return world
        val fresh = newOrder(world, world.orderSerial)
        return world.copy(orders = world.orders.replace(index, fresh), orderSerial = world.orderSerial + 1)
    }

    /** A brand-new farm with its first orders on the board. */
    fun newFarm(): FarmWorld = fillOrders(FarmWorld())

    private fun <T> List<T>.replace(index: Int, value: T): List<T> = mapIndexed { i, v -> if (i == index) value else v }

    private fun Map<String, Int>.add(item: Item, n: Int): Map<String, Int> {
        val total = (this[item.key] ?: 0) + n
        return if (total <= 0) this - item.key else this + (item.key to total)
    }
}
