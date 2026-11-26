package io.github.fershibli.megagames.games.journey

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.rememberWorldStore
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.Palette
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait

/** Pauses (milliseconds) so a person can follow every enemy turn. */
private const val ENEMY_PAUSE = 1100L
private const val AUTO_PAUSE = 800L

/** Where the player is when no battle is going on. */
private enum class Place { MAP, VILLAGE }

/** What the battle buttons show: the main actions, or a list to pick from. */
private sealed interface Picking {
    data object Main : Picking
    data object Spells : Picking
    data object Items : Picking
    data class Target(val action: Action) : Picking
}

/**
 * The journey, a lasting world like the farm: no autosave and no end. It is kept in the
 * [io.github.fershibli.megagames.core.WorldStore] and saved on every change.
 */
@Composable
fun JourneyGame(host: GameHost) {
    val store = rememberWorldStore()
    var world by remember(host) {
        store.flush()
        mutableStateOf(store.load(JOURNEY_WORLD_KEY, JourneyWorld.serializer()) ?: JourneyWorld())
    }
    val change: ((JourneyWorld) -> JourneyWorld) -> Unit = { f ->
        val next = f(world)
        if (next != world) {
            world = next
            store.save(JOURNEY_WORLD_KEY, JourneyWorld.serializer(), next)
        }
    }
    var place by remember { mutableStateOf(Place.MAP) }
    var message by remember { mutableStateOf<String?>(null) }
    val w = world
    val p = LocalPalette.current

    Box(Modifier.fillMaxSize()) {
        when {
            w.hero == null -> CreateScreen { name, cls -> change { Journey.start(name, cls, seed = System.nanoTime() and 0xFFFFFF) } }
            w.battle != null -> BattleScreen(w, change) { message = it }
            place == Place.VILLAGE -> VillageScreen(w, change, { place = Place.MAP }) { message = it }
            else -> MapScreen(w, change, { place = Place.VILLAGE }) { message = it }
        }
        val text = message
        if (text != null) {
            MessageCard(text, p) { message = null }
        } else if (w.hero != null && w.battle == null && w.levelUps > 0) {
            LevelUpCard(w, change, p)
        }
    }
}

// -------------------------------------------------------------------------------------------
// Making the hero.
// -------------------------------------------------------------------------------------------

@Composable
private fun CreateScreen(onStart: (String, HeroClass) -> Unit) {
    val p = LocalPalette.current
    var cls by remember { mutableStateOf(HeroClass.KNIGHT) }
    val suggested = stringResource(R.string.journey_default_name)
    var name by remember { mutableStateOf(suggested) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.journey_create_title), style = MaterialTheme.typography.headlineSmall, color = p.text, textAlign = TextAlign.Center)
        for (row in HeroClass.entries.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in row) {
                    val chosen = c == cls
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (chosen) p.primary.copy(alpha = 0.18f) else p.surfaceAlt)
                            .border(BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) p.primary else p.outline), RoundedCornerShape(14.dp))
                            .clickable { cls = c }.semantics { role = Role.RadioButton }
                            .padding(8.dp).testTag("journey-class-${c.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Canvas(Modifier.size(72.dp)) { drawSprite(JourneySprites.hero(c), 0f, 0f, size.width, size.height, p) }
                        Text(stringResource(c.label), style = MaterialTheme.typography.titleMedium, color = p.text)
                        Text(stringResource(c.description), style = MaterialTheme.typography.bodyMedium, color = p.textMuted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        OutlinedTextField(name, { name = it.take(14) }, Modifier.fillMaxWidth().testTag("journey-name"), label = { Text(stringResource(R.string.journey_name)) }, singleLine = true, textStyle = MaterialTheme.typography.titleLarge)
        BigButton(stringResource(R.string.journey_start), { onStart(name, cls) }, Modifier.fillMaxWidth().testTag("journey-start"), enabled = name.isNotBlank())
    }
}

// -------------------------------------------------------------------------------------------
// The map and the village.
// -------------------------------------------------------------------------------------------

@Composable
private fun statusLine(w: JourneyWorld): String {
    val h = w.hero!!
    return h.name + " · " + stringResource(R.string.journey_status, h.level, w.gold) + "\n" + stringResource(R.string.journey_items, w.potions, w.ethers)
}

@Composable
private fun MapScreen(w: JourneyWorld, change: ((JourneyWorld) -> JourneyWorld) -> Unit, toVillage: () -> Unit, say: (String) -> Unit) {
    val p = LocalPalette.current
    val res = LocalResources.current
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        StatusPill(statusLine(w), Modifier.testTag("journey-status"))
        Text(stringResource(R.string.journey_forest), style = MaterialTheme.typography.titleMedium, color = p.text)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
            val mapDescription = stringResource(R.string.journey_map_board)
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = mapDescription }) {
                drawForestMap(p, w.cleared, { Journey.open(w, it) }, -1)
            }
            // A big touch target on each spot, with its name for screen readers.
            Regions.FOREST.forEachIndexed { i, s ->
                val label = stringResource(s.label)
                Box(
                    Modifier.offset(maxWidth * s.x - 28.dp, maxHeight * s.y - 28.dp).size(56.dp)
                        .clickable {
                            when {
                                !Journey.open(w, i) -> say(res.getString(R.string.journey_locked))
                                !Journey.canVisit(w, i) -> say(res.getString(R.string.journey_done_spot))
                                else -> {
                                    val before = w
                                    change { Journey.visit(it, i) }
                                    when (s.kind) {
                                        SpotKind.CHEST -> say(res.getString(R.string.journey_chest_found, s.potions, s.gold))
                                        SpotKind.FRIEND -> say(res.getString(R.string.journey_curupira_says, s.ethers))
                                        else -> Unit
                                    }
                                    if (before == w) Unit
                                }
                            }
                        }
                        .semantics { contentDescription = label; role = Role.Button }
                        .testTag("journey-spot-$i"),
                )
                Text(
                    label, Modifier.offset(maxWidth * s.x - 65.dp, maxHeight * s.y + 22.dp).size(130.dp, 46.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center, maxLines = 2,
                )
            }
        }
        PartyRow(w)
        BigButton(stringResource(R.string.journey_to_village), toVillage, Modifier.fillMaxWidth().testTag("journey-to-village"), style = ButtonStyle.SECONDARY, compact = true)
    }
}

/** The group in a row: each one's picture, life and mana. */
@Composable
private fun PartyRow(w: JourneyWorld) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (id in Journey.members(w)) {
            val m = Journey.maxStats(w, id)
            val art = if (id == "hero") JourneySprites.hero(w.hero!!.heroClass) else JourneySprites.friend(Friend.valueOf(id))
            val name = if (id == "hero") w.hero!!.name else stringResource(Friend.valueOf(id).label)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(p.surfaceAlt).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(40.dp)) { drawSprite(art, 0f, 0f, size.width, size.height, p) }
                Text(name, style = MaterialTheme.typography.labelLarge, color = p.text, maxLines = 1)
                Text(
                    stringResource(R.string.journey_member_line, Journey.hp(w, id), m.hp, Journey.mp(w, id), m.mp),
                    style = MaterialTheme.typography.labelMedium, color = p.textMuted, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun VillageScreen(w: JourneyWorld, change: ((JourneyWorld) -> JourneyWorld) -> Unit, toMap: () -> Unit, say: (String) -> Unit) {
    val p = LocalPalette.current
    val res = LocalResources.current
    var tip by remember { mutableIntStateOf(0) }
    val tips = listOf(R.string.journey_tip_1, R.string.journey_tip_2, R.string.journey_tip_3, R.string.journey_tip_4, R.string.journey_tip_5)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusPill(statusLine(w), Modifier.testTag("journey-status"))
        Text(stringResource(R.string.journey_village), style = MaterialTheme.typography.titleLarge, color = p.text)
        Canvas(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp))) {
            drawBattleScene(p)
            val s = size
            drawSprite(JourneySprites.house, s.width * 0.05f, s.height * 0.1f, s.width * 0.3f, s.height * 0.62f, p)
            drawSprite(JourneySprites.house, s.width * 0.62f, s.height * 0.05f, s.width * 0.34f, s.height * 0.6f, p)
            drawSprite(JourneySprites.saci, s.width * 0.38f, s.height * 0.3f, s.width * 0.22f, s.height * 0.62f, p)
        }
        BigButton(stringResource(R.string.journey_inn), { change { Journey.rest(it) }; say(res.getString(R.string.journey_rested)) }, Modifier.fillMaxWidth().testTag("journey-inn"))
        BigButton(stringResource(R.string.journey_saci), { say(res.getString(tips[tip % tips.size])); tip++ }, Modifier.fillMaxWidth(), style = ButtonStyle.SECONDARY)
        Text(stringResource(R.string.journey_enemies), style = MaterialTheme.typography.titleMedium, color = p.text)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in Difficulty.entries) {
                BigButton(
                    stringResource(d.label), { change { Journey.setDifficulty(it, d) } }, Modifier.weight(1f).testTag("journey-level-${d.name.lowercase()}"),
                    style = if (w.difficulty == d) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, compact = true,
                )
            }
        }
        Text(stringResource(R.string.journey_group), style = MaterialTheme.typography.titleMedium, color = p.text)
        PartyRow(w)
        val h = w.hero!!
        Text(stringResource(R.string.journey_xp, h.xp, Journey.xpFor(h.level)), style = MaterialTheme.typography.bodyLarge, color = p.textMuted)
        BigButton(stringResource(R.string.journey_to_map), toMap, Modifier.fillMaxWidth().testTag("journey-to-map"), style = ButtonStyle.SECONDARY)
    }
}

// -------------------------------------------------------------------------------------------
// Battles.
// -------------------------------------------------------------------------------------------

@Composable
private fun fighterName(w: JourneyWorld, f: Fighter): String = when {
    f.id == "hero" -> w.hero!!.name
    f.foe != null -> stringResource(f.foe.label)
    else -> stringResource(Friend.valueOf(f.id).label)
}

private fun fighterArt(w: JourneyWorld, f: Fighter) = when {
    f.id == "hero" -> JourneySprites.hero(w.hero!!.heroClass)
    f.foe != null -> JourneySprites.foe(f.foe)
    else -> JourneySprites.friend(Friend.valueOf(f.id))
}

@Composable
private fun BattleScreen(w: JourneyWorld, change: ((JourneyWorld) -> JourneyWorld) -> Unit, say: (String) -> Unit) {
    val b = w.battle!!
    val p = LocalPalette.current
    val pace = currentPace
    val res = LocalResources.current
    var picking by remember(b.current, b.round) { mutableStateOf<Picking>(Picking.Main) }
    var auto by remember { mutableStateOf(false) }
    val names = b.fighters.map { fighterName(w, it) }
    val current = b.current
    val myTurn = b.outcome == null && current >= 0 && b.fighters[current].ally

    fun play(action: Action) {
        change { wd ->
            val bd = wd.battle ?: return@change wd
            val next = Journey.act(bd, action)
            wd.copy(battle = next, known = wd.known + Journey.discovered(next))
        }
        picking = Picking.Main
    }

    // Enemies play by themselves, slowly; so does the group on Automatic.
    LaunchedEffect(b, auto) {
        if (b.outcome != null || current < 0) return@LaunchedEffect
        if (!b.fighters[current].ally) {
            pace.wait(ENEMY_PAUSE)
            change { wd -> wd.battle?.let { bd -> val next = Journey.enemyTurn(bd); wd.copy(battle = next, known = wd.known + Journey.discovered(next)) } ?: wd }
        } else if (auto) {
            pace.wait(AUTO_PAUSE)
            play(Journey.bestAction(b, w.known))
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TurnStrip(w, b)
        val description = stringResource(R.string.journey_board, b.fighters.indices.joinToString(", ") { "${names[it]} ${b.fighters[it].hp}/${b.fighters[it].maxHp}" })
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).semantics { contentDescription = description }.testTag("journey-battle")) {
            Canvas(Modifier.fillMaxSize()) { drawBattleScene(p) }
            val allies = b.fighters.indices.filter { b.fighters[it].ally }
            val enemies = b.fighters.indices.filter { !b.fighters[it].ally }
            val target = picking as? Picking.Target
            fun slot(list: List<Int>, i: Int, left: Boolean): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
                val k = list.indexOf(i)
                val n = list.size
                val rowH = maxHeight / (n + 0.4f)
                val x = if (left) maxWidth * (0.04f + 0.08f * (k % 2)) else maxWidth * (0.52f + 0.1f * (k % 2))
                return x to rowH * (k + 0.2f)
            }
            for (i in b.fighters.indices) {
                val f = b.fighters[i]
                val left = f.ally
                val (x, y) = slot(if (left) allies else enemies, i, left)
                val n = if (left) allies.size else enemies.size
                val boss = f.foe?.boss == true
                val boxW = if (boss) maxWidth * 0.46f else maxWidth * 0.4f
                val boxH = if (boss) maxHeight * 0.8f else maxHeight / (n + 0.4f)
                val canTarget = target != null && f.up && targetOk(b, target.action, i)
                FighterView(
                    w, f, names[i], i == current && b.outcome == null, canTarget, f.foe != null && f.foe in w.known,
                    Modifier.offset(x, if (boss) maxHeight * 0.08f else y).size(boxW, boxH),
                ) { if (canTarget) play(target!!.action.copy(target = i)) }
            }
            DamageNumbers(b, w)
        }
        BattleLog(w, b, names)
        when {
            b.outcome != null -> {
                val foes = b.fighters.mapNotNull { it.foe }
                val text = if (b.outcome == Outcome.WON) stringResource(R.string.journey_victory, foes.sumOf { it.xp }, foes.sumOf { it.gold }) else stringResource(R.string.journey_defeat)
                StatusPill(text, Modifier.fillMaxWidth().testTag("journey-outcome"), emphasized = true)
                BigButton(stringResource(R.string.journey_ok), {
                    val spot = Regions.FOREST[b.spot]
                    val won = b.outcome == Outcome.WON
                    val hadLia = Friend.LIA in w.party
                    change { Journey.finish(it) }
                    if (won && spot.kind == SpotKind.RESCUE && !hadLia) say(res.getString(R.string.journey_lia_joins))
                    if (won && spot.kind == SpotKind.BOSS) say(res.getString(R.string.journey_chapter_done))
                }, Modifier.fillMaxWidth().testTag("journey-ok"))
            }
            !myTurn -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(stringResource(if (auto) R.string.journey_auto_stop else R.string.journey_auto), { auto = !auto }, Modifier.weight(1f).testTag("journey-auto"), style = ButtonStyle.SECONDARY, compact = true)
            }
            else -> ActionPanel(w, b, picking, auto, { picking = it }, { auto = !auto }, ::play)
        }
    }
}

/** Whether [i] can be the target of [action] (from the fighter whose turn it is). */
private fun targetOk(b: Battle, action: Action, i: Int): Boolean = Journey.valid(b, action.copy(target = i))

/** Who plays next, in order, as small faces along the top. */
@Composable
private fun TurnStrip(w: JourneyWorld, b: Battle) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.surfaceAlt).padding(4.dp).testTag("journey-turns"),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((k, i) in b.queue.take(7).withIndex()) {
            val f = b.fighters[i]
            Box(
                Modifier.size(if (k == 0) 44.dp else 34.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (f.ally) p.primary.copy(alpha = 0.25f) else p.danger.copy(alpha = 0.22f))
                    .border(if (k == 0) 3.dp else 0.dp, p.highlight, RoundedCornerShape(8.dp)),
            ) {
                Canvas(Modifier.fillMaxSize().padding(3.dp)) { drawSprite(fighterArt(w, f), 0f, 0f, size.width, size.height, p) }
            }
        }
        if (b.queue.isNotEmpty()) {
            Text(stringResource(R.string.journey_turn, fighterName(w, b.fighters[b.queue.first()])), Modifier.padding(start = 6.dp).weight(1f), style = MaterialTheme.typography.titleSmall, color = p.text, maxLines = 2)
        }
    }
}

/** One fighter: the picture, a life bar, the name, and the weak spots once found. */
@Composable
private fun FighterView(w: JourneyWorld, f: Fighter, name: String, turn: Boolean, canTarget: Boolean, known: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val p = LocalPalette.current
    // Once the weak spot is known, screen readers say it too.
    val spots = if (known && f.foe != null) {
        listOfNotNull(
            f.foe.weak.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.journey_weak, it.map { s -> stringResource(s.label) }.joinToString()) },
            f.foe.resists.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.journey_resists, it.map { s -> stringResource(s.label) }.joinToString()) },
        )
    } else {
        emptyList()
    }
    val label = (listOf("$name ${f.hp}/${f.maxHp}") + spots).joinToString(" · ")
    Column(
        modifier.clip(RoundedCornerShape(10.dp))
            .then(if (canTarget) Modifier.border(3.dp, p.highlight, RoundedCornerShape(10.dp)).background(p.highlight.copy(alpha = 0.15f)) else Modifier)
            .clickable(enabled = canTarget, onClick = onTap)
            .semantics { contentDescription = label; if (canTarget) role = Role.Button }
            .testTag("journey-fighter-${f.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.fillMaxSize()) {
                if (turn) drawOval(p.highlight.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.86f), androidx.compose.ui.geometry.Size(size.width * 0.7f, size.height * 0.14f))
                drawSprite(fighterArt(w, f), 0f, 0f, size.width, size.height * 0.96f, p, flipX = !f.ally && f.foe != Foe.WOLF && f.foe != Foe.BOAR && f.foe != Foe.GIANT_BOAR, alpha = if (f.up) 1f else 0.3f)
            }
            // Weak spots, as big icons, once found.
            if (known && f.foe != null) {
                Row(Modifier.align(Alignment.TopEnd)) {
                    for (s in f.foe.weak) Canvas(Modifier.size(26.dp)) { drawSprite(JourneySprites.school(s), 0f, 0f, size.width, size.height, p) }
                }
            }
        }
        LifeBar(f, p)
        Text(name, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp))
    }
}

@Composable
private fun LifeBar(f: Fighter, p: Palette) {
    val frac = f.hp.toFloat() / f.maxHp.coerceAtLeast(1)
    Box(Modifier.fillMaxWidth(0.8f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.4f))) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(8.dp).background(if (frac > 0.35f) p.success else p.danger))
    }
    if (f.ally) {
        val m = f.mp.toFloat() / f.maxMp.coerceAtLeast(1)
        Box(Modifier.padding(top = 2.dp).fillMaxWidth(0.8f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.4f))) {
            Box(Modifier.fillMaxWidth(m.coerceIn(0f, 1f)).height(5.dp).background(Color(0xFF42A5F5)))
        }
    }
}

/** The numbers of the last turn, big, over the battle. */
@Composable
private fun DamageNumbers(b: Battle, w: JourneyWorld) {
    val shown = b.events.filter { it.kind == EventKind.DAMAGE || it.kind == EventKind.HEAL || it.kind == EventKind.MANA }
    if (shown.isEmpty()) return
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        for (e in shown.take(4)) {
            val (text, color) = when (e.kind) {
                EventKind.DAMAGE -> "−${e.amount}" to (if (e.weak) Color(0xFFFFD54F) else Color.White)
                EventKind.HEAL -> "+${e.amount}" to Color(0xFF81C784)
                else -> "+${e.amount}" to Color(0xFF64B5F6)
            }
            Text(
                text, fontSize = 34.sp, fontWeight = FontWeight.Black, color = color,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp),
            )
        }
    }
}

/** What the last turn did, in words. */
@Composable
private fun BattleLog(w: JourneyWorld, b: Battle, names: List<String>) {
    val p = LocalPalette.current
    val lines = b.events.mapNotNull { e ->
        val actor = names.getOrNull(e.actor) ?: ""
        val target = names.getOrNull(e.target) ?: ""
        when (e.kind) {
            EventKind.DAMAGE -> stringResource(R.string.journey_log_damage, actor, target, e.amount) +
                (if (e.weak) " " + stringResource(R.string.journey_log_weak) else "") + (if (e.resisted) " " + stringResource(R.string.journey_log_resist) else "")
            EventKind.HEAL -> stringResource(R.string.journey_log_heal, actor, target, e.amount)
            EventKind.MANA -> stringResource(R.string.journey_log_mana, actor, target, e.amount)
            EventKind.DEFEND -> stringResource(R.string.journey_log_defend, actor)
            EventKind.PROTECT -> stringResource(R.string.journey_log_protect, actor)
            EventKind.SLOW -> stringResource(R.string.journey_log_slow, target)
            EventKind.OUT -> if (b.fighters[e.target].ally) stringResource(R.string.journey_log_faint, target) else stringResource(R.string.journey_log_flee, target)
        }
    }
    if (lines.isNotEmpty()) {
        Text(
            lines.take(3).joinToString("\n"), Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.surfaceAlt).padding(8.dp).testTag("journey-log"),
            style = MaterialTheme.typography.bodyLarge, color = p.text,
        )
    }
}

/** The buttons for the turn of a friend: the actions, a list of spells or items, or the choice of whom. */
@Composable
private fun ActionPanel(w: JourneyWorld, b: Battle, picking: Picking, auto: Boolean, pick: (Picking) -> Unit, toggleAuto: () -> Unit, play: (Action) -> Unit) {
    val p = LocalPalette.current
    val a = b.fighters[b.current]
    fun choose(action: Action) {
        val needsTarget = when (action.kind) {
            Action.Kind.ATTACK, Action.Kind.ITEM -> true
            Action.Kind.SPELL -> action.spell!!.reach == Reach.ONE_ENEMY || action.spell.reach == Reach.ONE_ALLY
            Action.Kind.DEFEND -> false
        }
        if (needsTarget) pick(Picking.Target(action)) else play(action)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (picking) {
            Picking.Main -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BigButton(stringResource(R.string.journey_attack), { choose(Action(Action.Kind.ATTACK)) }, Modifier.weight(1f).testTag("journey-attack"), compact = true)
                    BigButton(stringResource(R.string.journey_magic), { pick(Picking.Spells) }, Modifier.weight(1f).testTag("journey-magic"), compact = true, enabled = a.spells.isNotEmpty())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BigButton(stringResource(R.string.journey_defend), { play(Action(Action.Kind.DEFEND)) }, Modifier.weight(1f).testTag("journey-defend"), compact = true, style = ButtonStyle.SECONDARY)
                    BigButton(stringResource(R.string.journey_item), { pick(Picking.Items) }, Modifier.weight(1f).testTag("journey-item"), compact = true, style = ButtonStyle.SECONDARY, enabled = b.potions + b.ethers > 0)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BigButton(stringResource(R.string.journey_best), { play(Journey.bestAction(b, w.known)) }, Modifier.weight(1f).testTag("journey-best"), compact = true, style = ButtonStyle.SECONDARY)
                    BigButton(stringResource(if (auto) R.string.journey_auto_stop else R.string.journey_auto), toggleAuto, Modifier.weight(1f).testTag("journey-auto"), compact = true, style = ButtonStyle.SECONDARY)
                }
            }
            Picking.Spells -> {
                for (s in a.spells) {
                    val known = s.school != School.PHYSICAL
                    BigButton(
                        stringResource(R.string.journey_spell_line, stringResource(s.label), s.cost) + if (known) " · " + stringResource(s.school.label) else "",
                        { choose(Action(Action.Kind.SPELL, s)) }, Modifier.fillMaxWidth().testTag("journey-spell-${s.name.lowercase()}"), compact = true, enabled = s.cost <= a.mp,
                    )
                }
                BigButton(stringResource(R.string.journey_back), { pick(Picking.Main) }, Modifier.fillMaxWidth(), compact = true, style = ButtonStyle.SECONDARY)
            }
            Picking.Items -> {
                BigButton(stringResource(R.string.journey_potion) + " (${b.potions})", { choose(Action(Action.Kind.ITEM, item = Item.POTION)) }, Modifier.fillMaxWidth(), compact = true, enabled = b.potions > 0)
                BigButton(stringResource(R.string.journey_ether) + " (${b.ethers})", { choose(Action(Action.Kind.ITEM, item = Item.ETHER)) }, Modifier.fillMaxWidth(), compact = true, enabled = b.ethers > 0)
                BigButton(stringResource(R.string.journey_back), { pick(Picking.Main) }, Modifier.fillMaxWidth(), compact = true, style = ButtonStyle.SECONDARY)
            }
            is Picking.Target -> {
                Text(stringResource(R.string.journey_choose_target), style = MaterialTheme.typography.titleMedium, color = p.text)
                // Besides tapping the picture, a button for each one that can be chosen.
                val options = b.fighters.indices.filter { targetOk(b, picking.action, it) }
                for (row in options.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in row) {
                            BigButton(fighterName(w, b.fighters[i]), { play(picking.action.copy(target = i)) }, Modifier.weight(1f).testTag("journey-target-${b.fighters[i].id}"), compact = true)
                        }
                    }
                }
                BigButton(stringResource(R.string.journey_back), { pick(Picking.Main) }, Modifier.fillMaxWidth(), compact = true, style = ButtonStyle.SECONDARY)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Cards over the screen.
// -------------------------------------------------------------------------------------------

@Composable
private fun MessageCard(text: String, p: Palette, onOk: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(20.dp).clip(RoundedCornerShape(16.dp)).background(p.surface).border(2.dp, p.outline, RoundedCornerShape(16.dp)).padding(16.dp).testTag("journey-message"),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text, style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center)
            BigButton(stringResource(R.string.journey_ok), onOk, Modifier.fillMaxWidth().testTag("journey-message-ok"))
        }
    }
}

@Composable
private fun LevelUpCard(w: JourneyWorld, change: ((JourneyWorld) -> JourneyWorld) -> Unit, p: Palette) {
    val h = w.hero!!
    val (a, b) = Journey.choices(w)
    @Composable
    fun label(c: LevelChoice): String = c.spell?.let { stringResource(R.string.journey_opt_spell, stringResource(it.label)) } ?: when (c.stat!!) {
        Stat.HP -> stringResource(R.string.journey_opt_hp)
        Stat.MP -> stringResource(R.string.journey_opt_mp)
        Stat.ATK -> stringResource(R.string.journey_opt_atk)
        Stat.DEF -> stringResource(R.string.journey_opt_def)
        Stat.SPD -> stringResource(R.string.journey_opt_spd)
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(20.dp).clip(RoundedCornerShape(16.dp)).background(p.surface).border(2.dp, p.highlight, RoundedCornerShape(16.dp)).padding(16.dp).testTag("journey-level-up"),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(72.dp)) { drawSprite(JourneySprites.hero(h.heroClass), 0f, 0f, size.width, size.height, p) }
            Text(stringResource(R.string.journey_level_up, h.name, h.level - w.levelUps + 1), style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center)
            BigButton(label(a), { change { Journey.choose(it, a) } }, Modifier.fillMaxWidth().testTag("journey-choice-0"))
            BigButton(label(b), { change { Journey.choose(it, b) } }, Modifier.fillMaxWidth().testTag("journey-choice-1"), style = ButtonStyle.SECONDARY)
        }
    }
}
