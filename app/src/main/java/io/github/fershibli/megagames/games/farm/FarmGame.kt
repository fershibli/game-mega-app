package io.github.fershibli.megagames.games.farm

import androidx.annotation.StringRes
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.rememberWorldStore
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.BigDialog
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.ZoomableBoard
import io.github.fershibli.megagames.ui.components.rememberZoomState
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** The clock the farm reads. Tests provide their own to move time forward. */
val LocalFarmClock = staticCompositionLocalOf { SystemFarmClock }

/** How often the beds and animals look at the clock. */
private const val TICK_MS = 1_000L

/** How long a bed just bought blinks, and each stage of the picture of a bed growing. */
private const val FRESH_MS = 6_000L
private const val BED_FRAME_MS = 900L

/** How long a message stays on its card (scaled by the pace) and the gap above it. */
private const val NOTE_MS = 5_000L
private val NOTE_TOP = 5.dp

private enum class Tab(@StringRes val label: Int) {
    FARM(R.string.game_farm),
    ORDERS(R.string.farm_tab_orders),
    BARN(R.string.farm_tab_barn),
}

/**
 * A lasting farm, like the pet: no autosave and no end. Its world is kept in the
 * [io.github.fershibli.megagames.core.WorldStore] and saved on every change, so "New game" in the
 * settings only reopens it.
 */
@Composable
fun FarmGame(host: GameHost) {
    val store = rememberWorldStore()
    val clock = LocalFarmClock.current
    var world by remember(host) {
        store.flush()
        mutableStateOf(FarmRules.rescue(FarmRules.fillOrders(store.load(FARM_WORLD_KEY, FarmWorld.serializer()) ?: FarmRules.newFarm())))
    }
    // Every change applies to the latest farm, so quick taps in a row all count.
    val change: ((FarmWorld) -> FarmWorld) -> Unit = { f ->
        val next = f(world)
        if (next != world) {
            world = next
            store.save(FARM_WORLD_KEY, FarmWorld.serializer(), next)
        }
    }
    var now by remember { mutableLongStateOf(clock.now()) }
    LaunchedEffect(clock) {
        while (true) {
            // Waits for a real frame first, so it rests while the app is in the background.
            withInfiniteAnimationFrameMillis { }
            now = clock.now()
            delay(TICK_MS)
        }
    }
    // Taps read the clock afresh, so a crop that just ripened can be picked at once.
    val time = { clock.now().also { now = it } }
    var tab by remember { mutableStateOf(Tab.FARM) }
    // Each message is a new object, so saying the same thing twice restarts the timer.
    var message by remember { mutableStateOf<FarmNote?>(null) }
    val say: (String?) -> Unit = { message = it?.let(::FarmNote) }
    val pace = currentPace
    LaunchedEffect(message) { if (message != null) { pace.wait(NOTE_MS); message = null } }
    val p = LocalPalette.current
    val w = world

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            FarmPurse(w.coins, w.stars)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tab.entries.forEach { t ->
                BigButton(
                    stringResource(t.label), { tab = t; say(null) },
                    Modifier.weight(1f).testTag("farm-tab-${t.name.lowercase()}").semantics { role = Role.Tab },
                    style = if (t == tab) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, compact = true,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.FARM -> SceneTab(w, now, time, p, change, say)
                Tab.ORDERS -> OrdersTab(w, p, change, say)
                Tab.BARN -> BarnTab(w, p, change)
            }
            // Over the content, not in the layout: nothing below it moves, and taps outside it pass through.
            message?.let { InfoCard(it.text, p, Modifier.align(Alignment.TopCenter).padding(top = NOTE_TOP, start = 12.dp, end = 12.dp)) { message = null } }
        }
    }
}

/** One message on the card; a new object each time, so the same words again start a new timer. */
private class FarmNote(val text: String)

/** A card with what the farm says. It reads out politely, wraps at any text size and goes away when tapped. */
@Composable
private fun InfoCard(text: String, p: Palette, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    Surface(
        modifier.widthIn(max = 360.dp).testTag("farm-note").semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(16.dp), color = p.surface, shadowElevation = 6.dp,
        border = BorderStroke(if (p.strongOutlines) 3.dp else 1.dp, p.outline),
    ) {
        Text(
            text, Modifier.clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 10.dp),
            color = p.text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// The scene: beds and animals on one isometric farm.
// ---------------------------------------------------------------------------------------------

@Composable
private fun SceneTab(w: FarmWorld, now: Long, time: () -> Long, p: Palette, change: ((FarmWorld) -> FarmWorld) -> Unit, say: (String?) -> Unit) {
    var sowing by remember { mutableStateOf<Int?>(null) }
    var buying by remember { mutableStateOf(false) }
    var planning by remember { mutableStateOf(false) }
    // The bed just bought blinks for a while, so it is easy to find on the field.
    var fresh by remember { mutableStateOf<Int?>(null) }
    val pace = currentPace
    LaunchedEffect(fresh) { if (fresh != null) { pace.wait(FRESH_MS); fresh = null } }
    val sowFirst = stringResource(R.string.farm_plot_sow_first)
    val newBedHere = stringResource(R.string.farm_plot_here)
    val harvested = stringResource(R.string.farm_harvested)
    val inBarn = stringResource(R.string.farm_in_barn)
    val itemNames = Item.entries.associateWith { stringResource(it.label) }
    val animalNames = Animal.entries.associateWith { stringResource(it.label) }
    val emptyBed = stringResource(R.string.farm_plot_empty)
    val tapToSow = stringResource(R.string.farm_tap_to_sow)
    val ready = stringResource(R.string.farm_ready)
    val waterMe = stringResource(R.string.farm_water_me)
    val lessMinute = stringResource(R.string.farm_less_minute)
    val minutesLeft = stringResource(R.string.farm_minutes_left)
    val penReady = stringResource(R.string.farm_pen_ready)
    fun left(ms: Long): String {
        val minutes = (ms + 59_999L) / 60_000L
        return if (minutes <= 1) lessMinute else minutesLeft.format(minutes.toInt())
    }

    /** What a bed says: its crop and how it is doing. */
    fun plotText(i: Int, at: Long): String {
        val plot = w.plots[i]
        val crop = plot.sown ?: return "$emptyBed, $tapToSow"
        val name = itemNames.getValue(crop.item)
        return when {
            FarmRules.stage(plot, at) == Stage.RIPE -> "$name, $ready"
            FarmRules.canWater(plot, at) -> "$name, ${left(FarmRules.remainingMs(plot, at))}, $waterMe"
            else -> "$name, ${left(FarmRules.remainingMs(plot, at))}"
        }
    }

    fun penText(i: Int, at: Long): String {
        val pen = w.pens[i]
        val name = animalNames.getValue(pen.kind)
        val n = FarmRules.waiting(pen, at)
        return if (n > 0) "$name, ${penReady.format(itemNames.getValue(pen.kind.item), n)}"
        else "$name, ${left(((1f - FarmRules.penProgress(pen, at)) * pen.kind.periodMs).toLong())}"
    }

    fun tapPlot(i: Int) {
        val t = time()
        val plot = w.plots.getOrNull(i) ?: return
        when {
            plot.sown == null -> sowing = i
            FarmRules.stage(plot, t) == Stage.RIPE -> { change { FarmRules.harvest(it, i, t) }; say(harvested) }
            FarmRules.canWater(plot, t) -> { change { FarmRules.water(it, i, t) }; say(plotText(i, t)) }
            else -> say(plotText(i, t))
        }
    }

    fun tapPen(i: Int) {
        val t = time()
        val pen = w.pens.getOrNull(i) ?: return
        if (FarmRules.waiting(pen, t) > 0) change { FarmRules.collect(it, i, t) }
        say(penText(i, time()))
    }

    val gatherable = FarmRules.readyCount(w, now)
    val thirsty = w.plots.count { FarmRules.canWater(it, now) }
    // The scene's tap handler lives as long as the scene: it must always reach the latest farm.
    val onPlot by rememberUpdatedState<(Int) -> Unit> { tapPlot(it) }
    val onPen by rememberUpdatedState<(Int) -> Unit> { tapPen(it) }
    Column(Modifier.fillMaxSize()) {
        ZoomableBoard(Modifier.weight(1f).fillMaxWidth(), rememberZoomState(), panWithOneFinger = true) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val wPx = with(density) { maxWidth.toPx() }
                val hPx = with(density) { maxHeight.toPx() }
                val grid = FarmMap.grid(wPx, hPx, FarmMap.columns(w.plots.size, w.pens.size))
                val description = stringResource(R.string.farm_scene, w.plots.size, w.pens.size)
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .testTag("farm-scene")
                        .semantics { contentDescription = description }
                        .pointerInput(w.plots.size, w.pens.size, wPx, hPx) {
                            detectTapGestures { pos ->
                                val hit = FarmMap.hit(grid, pos, w.plots.size, w.pens.size) ?: return@detectTapGestures
                                if (hit.first) onPlot(hit.second) else onPen(hit.second)
                            }
                        },
                ) { drawFarmScene(FarmScene(w, now, (now / 1000L).toInt(), fresh), p, grid) }
                // One node per bed and animal for screen readers and tests. Touches go through
                // them to the scene, which picks the nearest bed or animal.
                val spot = 40.dp
                val spotPx = with(density) { spot.toPx() }
                w.plots.indices.forEach { i ->
                    val (col, row) = FarmMap.plots.getOrNull(i) ?: return@forEach
                    val center = grid.center(col, row)
                    val label = plotText(i, now)
                    Box(
                        Modifier
                            .offset { IntOffset((center.x - spotPx / 2).roundToInt(), (center.y - spotPx / 2).roundToInt()) }
                            .size(spot)
                            .testTag("farm-plot-$i")
                            .semantics {
                                contentDescription = label
                                role = Role.Button
                                onClick { tapPlot(i); true }
                            },
                    )
                }
                w.pens.indices.forEach { i ->
                    val (col, row) = FarmMap.pens.getOrNull(i) ?: return@forEach
                    val center = grid.center(col, row)
                    val label = penText(i, now)
                    Box(
                        Modifier
                            .offset { IntOffset((center.x - spotPx / 2).roundToInt(), (center.y - spotPx / 2).roundToInt()) }
                            .size(spot)
                            .testTag("farm-pen-$i")
                            .semantics {
                                contentDescription = label
                                role = Role.Button
                                onClick { tapPen(i); true }
                            },
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(p.surface).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionBar {
                BigButton(
                    stringResource(R.string.farm_collect_all),
                    {
                        val brought = FarmRules.gatherAll(w, time()).second
                        change { FarmRules.gatherAll(it, time()).first }
                        if (brought.isNotEmpty()) say(inBarn.format(brought.entries.joinToString(", ") { "${itemNames.getValue(it.key)} ×${it.value}" }))
                    },
                    Modifier.testTag("farm-collect-all"), style = if (gatherable > 0) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, enabled = gatherable > 0, compact = true,
                )
                BigButton(stringResource(R.string.farm_water_all), { change { FarmRules.waterAll(it, time()) } }, style = ButtonStyle.SECONDARY, enabled = thirsty > 0, compact = true)
            }
            ActionBar {
                if (w.plots.size < FarmRules.MAX_PLOTS) {
                    val cost = FarmRules.plotCost(w)
                    // With nothing growing, the button says why a bed can't be bought yet.
                    BigButton(
                        stringResource(R.string.farm_buy_plot, cost), { if (FarmRules.somethingGrowing(w)) planning = true else say(sowFirst) },
                        Modifier.testTag("farm-buy-plot"), style = ButtonStyle.SECONDARY, enabled = w.coins >= cost, compact = true,
                    )
                }
                BigButton(stringResource(R.string.farm_buy_animal), { buying = true }, Modifier.testTag("farm-buy-animal"), style = ButtonStyle.SECONDARY, enabled = w.pens.size < FarmRules.MAX_PENS, compact = true)
            }
        }
    }

    val index = sowing
    if (index != null) {
        val choices = FarmRules.unlocked(w).filter { it.seedCost <= w.coins }
        val options = choices.map { crop ->
            stringResource(R.string.farm_seed_option, itemNames.getValue(crop.item), crop.seedCost, crop.minutes) to {
                change { FarmRules.plant(it, index, crop, time()) }
                sowing = null
            }
        }
        val more = FarmRules.unlocked(w).size < Crop.entries.size
        val note = listOfNotNull(
            if (choices.isEmpty()) stringResource(R.string.farm_no_coins) else null,
            if (more) stringResource(R.string.farm_more_with_stars) else null,
        ).joinToString("\n").ifEmpty { null }
        BigDialog(stringResource(R.string.farm_sow_title), note, { sowing = null }, options + (stringResource(R.string.cancel) to { sowing = null }), primaryIndex = -1)
    }
    if (planning) {
        PlotDialog(FarmRules.plotCost(w), p, onBuy = {
            val index = w.plots.size
            change { FarmRules.buyPlot(it) }
            planning = false
            fresh = index
            say(newBedHere)
        }) { planning = false }
    }
    if (buying) {
        val choices = Animal.entries.filter { it.stars <= w.stars && it.cost <= w.coins }
        val options = choices.map { a ->
            stringResource(R.string.farm_animal_option, animalNames.getValue(a), a.cost) to {
                change { FarmRules.buyAnimal(it, a, time()) }
                buying = false
            }
        }
        val more = Animal.entries.any { it.stars > w.stars }
        val note = listOfNotNull(
            if (choices.isEmpty()) stringResource(R.string.farm_no_coins) else null,
            if (more) stringResource(R.string.farm_more_with_stars) else null,
        ).joinToString("\n").ifEmpty { null }
        BigDialog(stringResource(R.string.farm_buy_animal_title), note, { buying = false }, options + (stringResource(R.string.cancel) to { buying = false }), primaryIndex = -1)
    }
}

/**
 * Asks before a new bed is bought, with a picture of a bed growing from bare soil to the harvest,
 * one stage at a time, slowly enough to follow.
 */
@Composable
private fun PlotDialog(cost: Int, p: Palette, onBuy: () -> Unit, onCancel: () -> Unit) {
    var frame by remember { mutableIntStateOf(0) }
    val pace = currentPace
    LaunchedEffect(Unit) {
        while (true) {
            pace.wait(BED_FRAME_MS)
            frame = (frame + 1) % BED_FRAMES
        }
    }
    val picture = stringResource(R.string.farm_plot_picture)
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = p.surface,
        title = { Text(stringResource(R.string.farm_plot_title), style = MaterialTheme.typography.headlineSmall, color = p.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(Modifier.fillMaxWidth().height(150.dp).testTag("farm-plot-picture").semantics { contentDescription = picture }) { drawBedPreview(p, frame) }
                Text(stringResource(R.string.farm_plot_note, cost), style = MaterialTheme.typography.bodyLarge, color = p.text)
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(stringResource(R.string.farm_plot_buy), onBuy, Modifier.fillMaxWidth().testTag("farm-plot-confirm"))
                BigButton(stringResource(R.string.cancel), onCancel, Modifier.fillMaxWidth(), style = ButtonStyle.SECONDARY)
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Orders and the barn.
// ---------------------------------------------------------------------------------------------

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surfaceAlt)
            .border(BorderStroke(if (p.strongOutlines) 2.dp else 1.dp, p.outline), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ItemIcon(item: Item, p: Palette) {
    Canvas(Modifier.size(40.dp)) { drawProduct(item) }
}

@Composable
private fun OrdersTab(w: FarmWorld, p: Palette, change: ((FarmWorld) -> FarmWorld) -> Unit, say: (String?) -> Unit) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        w.orders.forEachIndexed { i, order ->
            val ok = FarmRules.canDeliver(w, order)
            val delivered = stringResource(R.string.farm_delivered, order.coins)
            Card(Modifier.testTag("farm-order-$i")) {
                order.items.forEach { (key, need) ->
                    val item = Item.fromKey(key) ?: return@forEach
                    val have = w.count(item)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemIcon(item, p)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.farm_have_need, stringResource(item.label), minOf(have, need), need),
                            style = MaterialTheme.typography.titleMedium, color = if (have >= need) p.success else p.text,
                        )
                    }
                }
                Text(stringResource(R.string.farm_order_reward, order.coins), style = MaterialTheme.typography.titleMedium, color = p.text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(stringResource(R.string.farm_deliver), { change { FarmRules.deliver(it, i) }; say(delivered) }, Modifier.weight(1f).testTag("farm-deliver-$i"), enabled = ok, compact = true)
                    BigButton(stringResource(R.string.farm_skip), { change { FarmRules.skip(it, i) } }, Modifier.weight(1f), style = ButtonStyle.SECONDARY, compact = true)
                }
            }
        }
    }
}

@Composable
private fun BarnTab(w: FarmWorld, p: Palette, change: ((FarmWorld) -> FarmWorld) -> Unit) {
    val stock = Item.entries.filter { w.count(it) > 0 }
    if (stock.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.farm_barn_empty), Modifier.padding(24.dp), style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center)
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stock.forEach { item ->
            Card(Modifier.testTag("farm-barn-${item.key}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemIcon(item, p)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.farm_barn_row, stringResource(item.label), w.count(item), item.price), style = MaterialTheme.typography.titleMedium, color = p.text)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(stringResource(R.string.farm_sell_one), { change { FarmRules.sell(it, item, 1) } }, Modifier.weight(1f), style = ButtonStyle.SECONDARY, compact = true)
                    BigButton(stringResource(R.string.farm_sell_all), { change { FarmRules.sell(it, item, it.count(item)) } }, Modifier.weight(1f).testTag("farm-sell-all-${item.key}"), style = ButtonStyle.SECONDARY, compact = true)
                }
            }
        }
    }
}
