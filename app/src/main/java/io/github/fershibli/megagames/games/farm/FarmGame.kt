package io.github.fershibli.megagames.games.farm

import androidx.annotation.StringRes
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.rememberWorldStore
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.BigDialog
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette
import kotlinx.coroutines.delay

/** The clock the farm reads. Tests provide their own to move time forward. */
val LocalFarmClock = staticCompositionLocalOf { SystemFarmClock }

/** How often the beds and animals look at the clock. */
private const val TICK_MS = 1_000L

private enum class Tab(@StringRes val label: Int) {
    FIELD(R.string.farm_tab_field),
    ANIMALS(R.string.farm_tab_animals),
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
        mutableStateOf(FarmRules.fillOrders(store.load(FARM_WORLD_KEY, FarmWorld.serializer()) ?: FarmRules.newFarm()))
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
    var tab by remember { mutableStateOf(Tab.FIELD) }
    var message by remember { mutableStateOf<String?>(null) }
    val p = LocalPalette.current
    val w = world

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            val purse = stringResource(R.string.farm_coins, w.coins) + " · " + stringResource(R.string.farm_stars, w.stars)
            StatusPill(if (message != null) purse + "\n" + message else purse, Modifier.testTag("farm-status"))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tab.entries.forEach { t ->
                BigButton(
                    stringResource(t.label), { tab = t; message = null },
                    Modifier.weight(1f).testTag("farm-tab-${t.name.lowercase()}").semantics { role = Role.Tab },
                    style = if (t == tab) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, compact = true,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.FIELD -> FieldTab(w, now, time, p, change) { message = it }
                Tab.ANIMALS -> AnimalsTab(w, now, time, p, change)
                Tab.ORDERS -> OrdersTab(w, p, change) { message = it }
                Tab.BARN -> BarnTab(w, p, change)
            }
        }
    }
}

/** Minutes left, rounded up, or "under a minute". */
@Composable
private fun timeLeft(ms: Long): String {
    val minutes = (ms + 59_999L) / 60_000L
    return if (minutes <= 1) stringResource(R.string.farm_less_minute) else stringResource(R.string.farm_minutes_left, minutes.toInt())
}

@Composable
private fun Controls(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(p.surface).padding(horizontal = 12.dp, vertical = 10.dp)) { ActionBar { content() } }
}

/** A grid of [count] squares, [columns] to a row, that scrolls when it does not fit. */
@Composable
private fun Grid(count: Int, columns: Int, modifier: Modifier = Modifier, cell: @Composable (Int, Modifier) -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0 until (count + columns - 1) / columns) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0 until columns) {
                    val i = row * columns + col
                    if (i < count) cell(i, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The field.
// ---------------------------------------------------------------------------------------------

@Composable
private fun FieldTab(w: FarmWorld, now: Long, time: () -> Long, p: Palette, change: ((FarmWorld) -> FarmWorld) -> Unit, say: (String?) -> Unit) {
    var sowing by remember { mutableStateOf<Int?>(null) }
    val ripe = FarmRules.ripeCount(w, now)
    val thirsty = w.plots.count { FarmRules.canWater(it, now) }
    val harvested = stringResource(R.string.farm_harvested)
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val columns = if (maxWidth > 560.dp) 4 else 3
            Grid(w.plots.size, columns) { i, m ->
                val plot = w.plots[i]
                val stage = FarmRules.stage(plot, now)
                val crop = plot.sown
                val waterable = FarmRules.canWater(plot, now)
                val caption = when {
                    crop == null -> stringResource(R.string.farm_tap_to_sow)
                    stage == Stage.RIPE -> stringResource(R.string.farm_ready)
                    else -> timeLeft(FarmRules.remainingMs(plot, now))
                }
                val name = crop?.let { stringResource(it.item.label) } ?: stringResource(R.string.farm_plot_empty)
                val hint = if (waterable) stringResource(R.string.farm_water_me) else null
                Column(
                    m.clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button) {
                            val t = time()
                            when {
                                crop == null -> sowing = i
                                FarmRules.stage(plot, t) == Stage.RIPE -> { change { FarmRules.harvest(it, i, t) }; say(harvested) }
                                FarmRules.canWater(plot, t) -> change { FarmRules.water(it, i, t) }
                            }
                        }
                        .testTag("farm-plot-$i")
                        .clearAndSetSemantics { contentDescription = listOfNotNull(name, caption, hint).joinToString(", ") },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) { drawPlot(plot, stage, waterable, p) }
                    Text(name, style = MaterialTheme.typography.titleMedium, color = p.text, textAlign = TextAlign.Center, maxLines = 1)
                    Text(caption, style = MaterialTheme.typography.bodyMedium, color = if (stage == Stage.RIPE) p.success else p.textMuted, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
        Controls {
            BigButton(stringResource(R.string.farm_harvest_all), { change { FarmRules.harvestAll(it, time()) }; say(harvested) }, Modifier.testTag("farm-harvest-all"), enabled = ripe > 0, compact = true)
            BigButton(stringResource(R.string.farm_water_all), { change { FarmRules.waterAll(it, time()) } }, style = ButtonStyle.SECONDARY, enabled = thirsty > 0, compact = true)
            if (w.plots.size < FarmRules.MAX_PLOTS) {
                val cost = FarmRules.plotCost(w)
                BigButton(stringResource(R.string.farm_buy_plot, cost), { change { FarmRules.buyPlot(it) } }, style = ButtonStyle.SECONDARY, enabled = w.coins >= cost, compact = true)
            }
        }
    }
    val index = sowing
    if (index != null) {
        val choices = FarmRules.unlocked(w).filter { it.seedCost <= w.coins }
        val options = choices.map { crop ->
            stringResource(R.string.farm_seed_option, stringResource(crop.item.label), crop.seedCost, crop.minutes) to {
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
}

// ---------------------------------------------------------------------------------------------
// The animals.
// ---------------------------------------------------------------------------------------------

@Composable
private fun AnimalsTab(w: FarmWorld, now: Long, time: () -> Long, p: Palette, change: ((FarmWorld) -> FarmWorld) -> Unit) {
    var buying by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (w.pens.isEmpty()) {
                Text(stringResource(R.string.farm_no_animals), Modifier.align(Alignment.Center).padding(24.dp), style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center)
            } else {
                Grid(w.pens.size, 2) { i, m ->
                    val pen = w.pens[i]
                    val ready = FarmRules.waiting(pen, now)
                    val label = stringResource(pen.kind.label)
                    val caption = if (ready > 0) stringResource(R.string.farm_pen_ready, stringResource(pen.kind.item.label), ready) else timeLeft(((1f - FarmRules.penProgress(pen, now)) * pen.kind.periodMs).toLong())
                    Column(
                        m.clip(RoundedCornerShape(16.dp))
                            .background(p.surfaceAlt)
                            .border(BorderStroke(if (p.strongOutlines) 2.dp else 1.dp, p.outline), RoundedCornerShape(16.dp))
                            .clickable(role = Role.Button) { change { FarmRules.collect(it, i, time()) } }
                            .padding(8.dp)
                            .testTag("farm-pen-$i")
                            .clearAndSetSemantics { contentDescription = "$label, $caption" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Canvas(Modifier.fillMaxWidth().aspectRatio(1.3f)) {
                            drawAnimal(pen.kind, p)
                            for (k in 0 until ready) drawItem(pen.kind.item, Offset(size.width * (0.15f + k * 0.14f), size.height * 0.15f), size.width * 0.07f, p)
                        }
                        Text(label, style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(caption, style = MaterialTheme.typography.bodyMedium, color = if (ready > 0) p.success else p.textMuted, textAlign = TextAlign.Center, maxLines = 2)
                        Progress(FarmRules.penProgress(pen, now), p)
                    }
                }
            }
        }
        Controls {
            BigButton(stringResource(R.string.farm_collect_all), { change { FarmRules.collectAll(it, time()) } }, Modifier.testTag("farm-collect-all"), enabled = FarmRules.waitingCount(w, now) > 0, compact = true)
            BigButton(stringResource(R.string.farm_buy_animal), { buying = true }, Modifier.testTag("farm-buy-animal"), style = ButtonStyle.SECONDARY, enabled = w.pens.size < FarmRules.MAX_PENS, compact = true)
        }
    }
    if (buying) {
        val choices = Animal.entries.filter { it.stars <= w.stars && it.cost <= w.coins }
        val options = choices.map { a ->
            stringResource(R.string.farm_animal_option, stringResource(a.label), a.cost) to {
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

@Composable
private fun Progress(value: Float, p: Palette) {
    Canvas(Modifier.fillMaxWidth().height(10.dp).padding(horizontal = 6.dp)) {
        drawRoundRect(p.outline.copy(alpha = 0.3f), Offset.Zero, size, CornerRadius(size.height / 2))
        drawRoundRect(p.success, Offset.Zero, Size(size.width * value.coerceIn(0f, 1f), size.height), CornerRadius(size.height / 2))
    }
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
    Canvas(Modifier.size(40.dp)) { drawItem(item, center, size.width * 0.42f, p) }
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
