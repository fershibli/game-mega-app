package io.github.fershibli.megagames.games.journey

import io.github.fershibli.megagames.core.Difficulty
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

/** Where the lasting journey is kept in the WorldStore. */
const val JOURNEY_WORLD_KEY = "journey"

/** The hero: chosen class and name, level, experience, the extras picked when levelling up and the spells known. */
@Serializable
data class Hero(
    val name: String,
    val heroClass: HeroClass,
    val level: Int = 1,
    val xp: Int = 0,
    val bonus: Bonus = Bonus(),
    val spells: List<Spell> = heroClass.start,
)

/** Extra points picked on level ups. */
@Serializable
data class Bonus(val hp: Int = 0, val mp: Int = 0, val atk: Int = 0, val def: Int = 0, val spd: Int = 0)

/** A choice offered on a level up. */
@Serializable
data class LevelChoice(val stat: Stat? = null, val spell: Spell? = null)

/** The numbers a level-up choice can raise, and by how much. */
enum class Stat(val amount: Int) { HP(8), MP(5), ATK(2), DEF(2), SPD(1) }

/**
 * The whole lasting journey. [party] are the friends travelling with the hero (up to three);
 * [reserve] the others, waiting at the tavern. [hp] and [mp] are each member's current life and
 * mana by id ("hero" or a friend's name). [cleared] holds the spots already done, numbered across
 * regions (see [Regions.id]).
 */
@Serializable
data class JourneyWorld(
    val hero: Hero? = null,
    val party: List<Friend> = emptyList(),
    val reserve: List<Friend> = emptyList(),
    val hp: Map<String, Int> = emptyMap(),
    val mp: Map<String, Int> = emptyMap(),
    val gold: Int = 0,
    val potions: Int = 3,
    val ethers: Int = 1,
    val cleared: Set<Int> = emptySet(),
    val difficulty: Difficulty = Difficulty.EASY,
    /** Enemy kinds whose weak spot the group has found (shown from then on). */
    val known: Set<Foe> = emptySet(),
    val battle: Battle? = null,
    /** Level ups still waiting for the player to pick an extra. */
    val levelUps: Int = 0,
    val seed: Long = 1L,
    /** The region shown on the map. */
    val region: Region = Region.FOREST,
    /** Gear bought in the shop, and what the hero wears. */
    val owned: Set<Gear> = emptySet(),
    val worn: Set<Gear> = emptySet(),
    /** Scenes of the story already told (see [Story]). */
    val seen: Set<String> = emptySet(),
)

/** One side of a battle, with its numbers fixed for the fight. */
@Serializable
data class Fighter(
    val id: String,
    val ally: Boolean,
    val foe: Foe? = null,
    val hp: Int,
    val maxHp: Int,
    val mp: Int,
    val maxMp: Int,
    val atk: Int,
    val def: Int,
    val spd: Int,
    val spells: List<Spell> = emptyList(),
    /** Rounds left with half speed. */
    val slowed: Int = 0,
    val defending: Boolean = false,
    /** Takes the blows aimed at friends until its next turn. */
    val protecting: Boolean = false,
    /** Lasting states and the rounds they still last. */
    val effects: Map<Status, Int> = emptyMap(),
) {
    val up: Boolean get() = hp > 0
    val speed: Int get() = if (slowed > 0) max(1, spd / 2) else spd
    fun has(s: Status) = (effects[s] ?: 0) > 0
}

/** What a fighter does on its turn. [friend] is who comes in on a swap. */
@Serializable
data class Action(val kind: Kind, val spell: Spell? = null, val target: Int = -1, val item: Item? = null, val friend: Friend? = null) {
    enum class Kind { ATTACK, SPELL, DEFEND, ITEM, SWAP }
}

enum class Item(val price: Int) { POTION(20), ETHER(30) }

/** One line of what happened, for the screen: who, to whom, how much and how it went. */
@Serializable
data class Event(
    val kind: EventKind,
    val actor: Int = -1,
    val target: Int = -1,
    val amount: Int = 0,
    val spell: Spell? = null,
    val item: Item? = null,
    val weak: Boolean = false,
    val resisted: Boolean = false,
    val status: Status? = null,
    val friend: Friend? = null,
)

enum class EventKind { DAMAGE, HEAL, MANA, DEFEND, PROTECT, SLOW, OUT, STATUS, POISON, ASLEEP, WAKE, FROZEN, BURN, STOLE, MISSED, SWAP }

/** How a battle ended. */
enum class Outcome { WON, LOST }

/**
 * A battle in progress. [queue] is who still acts this round, in order; [events] is what the last
 * turn did. [bench] holds the friends who are not fighting (they can swap in). [rng] makes every
 * roll repeatable from the seed.
 */
@Serializable
data class Battle(
    val spot: Int,
    val fighters: List<Fighter>,
    val queue: List<Int>,
    val round: Int = 1,
    val potions: Int,
    val ethers: Int,
    val events: List<Event> = emptyList(),
    val outcome: Outcome? = null,
    val rng: Long,
    val bench: List<Fighter> = emptyList(),
    /** Gold picked up by stealing. */
    val loot: Int = 0,
) {
    val current: Int get() = queue.firstOrNull() ?: -1
}

/** The rules of the journey. Pure Kotlin. */
object Journey {
    const val MAX_LEVEL = 20
    const val MAX_COMPANIONS = 3
    const val DEFEND_MANA = 2

    /**
     * Experience for the next level: it grows step by step, as the enemies of each region give
     * more, so a level takes about the same few battles all along the journey.
     */
    fun xpFor(level: Int) = 20 * level

    /** How much stronger the enemies of each region are than their base numbers. */
    private val REGION_POWER = floatArrayOf(1f, 1.3f, 1.6f, 1.9f, 2.2f)

    // -------------------------------------------------------------------------------------
    // The group.
    // -------------------------------------------------------------------------------------

    fun heroStats(h: Hero, worn: Set<Gear> = emptySet()): Stats =
        h.heroClass.base + h.heroClass.growth.times(h.level - 1) +
            Stats(h.bonus.hp, h.bonus.mp + worn.sumOf { it.mp }, h.bonus.atk + worn.sumOf { it.atk }, h.bonus.def + worn.sumOf { it.def }, h.bonus.spd)

    fun companionStats(c: Friend, level: Int): Stats = c.base + c.growth.times(level - 1)

    /** The ids of the group that travels, hero first. */
    fun members(w: JourneyWorld): List<String> = listOf("hero") + w.party.map { it.name }

    fun maxStats(w: JourneyWorld, id: String): Stats {
        val h = w.hero ?: error("no hero")
        return if (id == "hero") heroStats(h, w.worn) else companionStats(Friend.valueOf(id), h.level)
    }

    fun hp(w: JourneyWorld, id: String) = (w.hp[id] ?: maxStats(w, id).hp).coerceAtMost(maxStats(w, id).hp)
    fun mp(w: JourneyWorld, id: String) = (w.mp[id] ?: maxStats(w, id).mp).coerceAtMost(maxStats(w, id).mp)

    /** A new journey: the hero, with Bento the blacksmith at their side. */
    fun start(name: String, heroClass: HeroClass, difficulty: Difficulty = Difficulty.EASY, seed: Long = 1L): JourneyWorld {
        val clean = name.trim().take(14).ifEmpty { "?" }
        return JourneyWorld(hero = Hero(clean, heroClass), party = listOf(Friend.BENTO), difficulty = difficulty, seed = seed)
    }

    /** A night at the inn: everyone back to full life and mana. */
    fun rest(w: JourneyWorld): JourneyWorld = w.copy(hp = emptyMap(), mp = emptyMap())

    fun setDifficulty(w: JourneyWorld, d: Difficulty): JourneyWorld = w.copy(difficulty = d)

    // -------------------------------------------------------------------------------------
    // The tavern and the shop.
    // -------------------------------------------------------------------------------------

    /** Friends met so far: travelling first, then waiting at the tavern. */
    fun friends(w: JourneyWorld): List<Friend> = w.party + w.reserve

    /** At the tavern: [f] joins the travelling group (if there is room) or stays behind. */
    fun setTravelling(w: JourneyWorld, f: Friend, travelling: Boolean): JourneyWorld = when {
        travelling && f in w.reserve && w.party.size < MAX_COMPANIONS -> w.copy(party = w.party + f, reserve = w.reserve - f)
        !travelling && f in w.party -> w.copy(party = w.party - f, reserve = w.reserve + f)
        else -> w
    }

    /** Buys and wears [g], when there is gold enough; the piece it replaces stays owned. */
    fun buy(w: JourneyWorld, g: Gear): JourneyWorld {
        if (g in w.owned || w.gold < g.price) return w
        return wear(w.copy(gold = w.gold - g.price, owned = w.owned + g), g)
    }

    /** Wears [g] (already owned) in its slot. */
    fun wear(w: JourneyWorld, g: Gear): JourneyWorld {
        if (g !in w.owned) return w
        return w.copy(worn = w.worn.filter { it.slot != g.slot }.toSet() + g)
    }

    fun buy(w: JourneyWorld, item: Item): JourneyWorld {
        if (w.gold < item.price) return w
        return when (item) {
            Item.POTION -> w.copy(gold = w.gold - item.price, potions = w.potions + 1)
            Item.ETHER -> w.copy(gold = w.gold - item.price, ethers = w.ethers + 1)
        }
    }

    // -------------------------------------------------------------------------------------
    // The map.
    // -------------------------------------------------------------------------------------

    /** A region opens when the boss of the one before it has been beaten. */
    fun regionOpen(w: JourneyWorld, r: Region): Boolean = r.ordinal == 0 || chapterDone(w, Region.entries[r.ordinal - 1])

    /** A spot can be visited when the one before it is done; battles already won can be fought again. */
    fun open(w: JourneyWorld, id: Int): Boolean {
        val r = Regions.region(id)
        val i = Regions.index(id)
        return regionOpen(w, r) && (i == 0 || (id - 1) in w.cleared)
    }

    fun canVisit(w: JourneyWorld, id: Int): Boolean {
        val r = id / 100
        if (id < 0 || r !in Region.entries.indices || Regions.index(id) !in Region.entries[r].spots.indices) return false
        if (!open(w, id) || w.battle != null) return false
        return id !in w.cleared || Regions.spot(id).kind == SpotKind.BATTLE
    }

    /** A region is done when its boss has been beaten. */
    fun chapterDone(w: JourneyWorld, r: Region = Region.FOREST): Boolean = Regions.id(r, r.spots.lastIndex) in w.cleared

    fun showRegion(w: JourneyWorld, r: Region): JourneyWorld = if (regionOpen(w, r)) w.copy(region = r) else w

    /** Goes to spot [id]: a battle starts there, or the chest is opened (a scroll teaches a spell), or the friend met. */
    fun visit(w: JourneyWorld, id: Int): JourneyWorld {
        if (!canVisit(w, id)) return w
        val s = Regions.spot(id)
        return when (s.kind) {
            SpotKind.CHEST, SpotKind.FRIEND -> {
                val h = w.hero!!
                val hero = if (s.scroll != null && s.scroll !in h.spells) h.copy(spells = h.spells + s.scroll) else h
                w.copy(hero = hero, potions = w.potions + s.potions, ethers = w.ethers + s.ethers, gold = w.gold + s.gold, cleared = w.cleared + id)
            }
            SpotKind.BATTLE, SpotKind.RESCUE, SpotKind.BOSS -> w.copy(battle = newBattle(w, id, s.foes))
        }
    }

    // -------------------------------------------------------------------------------------
    // Battles.
    // -------------------------------------------------------------------------------------

    private fun scale(d: Difficulty) = when (d) {
        Difficulty.EASY -> 0.75f
        Difficulty.MEDIUM -> 1f
        Difficulty.HARD -> 1.25f
    }

    private fun friendFighter(w: JourneyWorld, id: String): Fighter {
        val m = maxStats(w, id)
        val spells = if (id == "hero") w.hero!!.spells else Friend.valueOf(id).spells
        return Fighter(id, true, null, hp(w, id).coerceAtLeast(1), m.hp, mp(w, id), m.mp, m.atk, m.def, m.spd, spells)
    }

    /** Enemies grow a little when the hero comes back much stronger, so old spots still give a fight. */
    private fun growth(level: Int, spot: Int): Float {
        val regionLevel = 1 + Regions.region(spot).ordinal * 4
        return 1f + (level - regionLevel).coerceAtLeast(0) * 0.05f
    }

    fun newBattle(w: JourneyWorld, spot: Int, foes: List<Foe>): Battle {
        val h = w.hero ?: error("no hero")
        val allies = members(w).map { friendFighter(w, it) }
        val k = scale(w.difficulty)
        val grow = growth(h.level, spot)
        val power = REGION_POWER[Regions.region(spot).ordinal]
        val enemies = foes.mapIndexed { i, f ->
            // Bosses already have big numbers: the region's power grows them more gently.
            val pw = if (f.boss) kotlin.math.sqrt(power) else power
            val hp = (f.stats.hp * k * grow * pw).roundToInt()
            Fighter("foe$i", false, f, hp, hp, 0, 0, (f.stats.atk * k * grow * pw).roundToInt(), (f.stats.def * grow).roundToInt(), f.stats.spd)
        }
        val fighters = allies + enemies
        val rng = w.seed * 6364136223846793005L + spot * 1442695040888963407L + w.cleared.size
        val bench = w.reserve.map { friendFighter(w, it.name) }
        return Battle(spot, fighters, order(fighters), potions = w.potions, ethers = w.ethers, rng = rng, bench = bench)
    }

    /** Everyone still standing, fastest first; on a tie the group goes before the enemies. */
    fun order(fighters: List<Fighter>): List<Int> =
        fighters.indices.filter { fighters[it].up }.sortedWith(compareByDescending<Int> { fighters[it].speed }.thenBy { if (fighters[it].ally) 0 else 1 }.thenBy { it })

    /** The next pseudo-random number in 0 until 1, and the new state. */
    private fun roll(rng: Long): Pair<Float, Long> {
        var z = rng + -0x61c8864680b583ebL
        val next = z
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        return ((z ushr 40).toFloat() / (1L shl 24).toFloat()) to next
    }

    /** How hard [school] hits [target]: 1.5 on a weak spot, 0.5 when it resists. */
    fun factor(target: Fighter, school: School): Float {
        val f = target.foe ?: return 1f
        return when (school) {
            in f.weak -> 1.5f
            in f.resists -> 0.5f
            else -> 1f
        }
    }

    /** What [a] can do now: spells it has the mana for. */
    fun castable(a: Fighter): List<Spell> = a.spells.filter { it.cost <= a.mp }

    /** Friends waiting on the bench who could come in now. */
    fun swappable(b: Battle): List<Friend> = b.bench.filter { it.up }.map { Friend.valueOf(it.id) }

    /** Whether [action] makes sense for the fighter whose turn it is. */
    fun valid(b: Battle, action: Action): Boolean {
        val i = b.current
        if (i < 0 || b.outcome != null) return false
        val a = b.fighters[i]
        val t = b.fighters.getOrNull(action.target)
        return when (action.kind) {
            Action.Kind.DEFEND -> true
            Action.Kind.ATTACK -> t != null && t.up && t.ally != a.ally
            Action.Kind.ITEM -> a.ally && t != null && t.ally && t.up && when (action.item) {
                Item.POTION -> b.potions > 0
                Item.ETHER -> b.ethers > 0
                null -> false
            }
            Action.Kind.SWAP -> a.ally && t != null && t.ally && t.id != "hero" && action.friend != null && action.friend in swappable(b)
            Action.Kind.SPELL -> {
                val s = action.spell ?: return false
                if (s !in a.spells || s.cost > a.mp) return false
                when (s.reach) {
                    Reach.ONE_ENEMY -> t != null && t.up && t.ally != a.ally
                    Reach.ONE_ALLY -> t != null && t.up && t.ally == a.ally
                    else -> true
                }
            }
        }
    }

    /**
     * The fighter whose turn it is does [action]. Turns then move on; enemies do not act here
     * (see [enemyTurn]), so the screen can show each of them in turn.
     */
    fun act(b: Battle, action: Action): Battle {
        if (!valid(b, action)) return b
        val i = b.current
        var rng = b.rng
        val fs = b.fighters.toMutableList()
        val bench = b.bench.toMutableList()
        val events = ArrayList<Event>()
        var potions = b.potions
        var ethers = b.ethers
        var loot = b.loot
        // A turn ends what was kept up since the last one.
        fs[i] = fs[i].copy(defending = false, protecting = false)
        val a = fs[i]
        val courage = if (a.has(Status.COURAGE)) 1.3f else 1f

        fun hit(target: Int, power: Float, school: School, spell: Spell?) {
            // Blows aimed at a friend go to whoever is protecting the group.
            var t = target
            val guard = fs.indices.firstOrNull { fs[it].protecting && fs[it].up && fs[it].ally == fs[target].ally && it != target }
            if (guard != null && school == School.PHYSICAL && !a.ally) t = guard
            val target0 = fs[t]
            val (r, next) = roll(rng)
            rng = next
            val f = factor(target0, school)
            var dmg = max(1f, a.atk * courage * power - target0.def * 0.5f) * f * (0.9f + 0.2f * r)
            if (target0.defending) dmg *= 0.5f
            if (target0.has(Status.SHIELD)) dmg *= 0.5f
            var effects = target0.effects
            // Combos: ice on a wet enemy freezes it; fire on an oiled one burns hard.
            var frozen = false
            if (school == School.ICE && target0.has(Status.WET)) {
                dmg *= 1.5f
                frozen = true
                effects = effects - Status.WET + (Status.SLEEP to 1)
            }
            var burnt = false
            if (school == School.FIRE && target0.has(Status.OIL)) {
                dmg *= 2f
                burnt = true
                effects = effects - Status.OIL
            }
            val amount = max(1, dmg.roundToInt())
            val hp = (target0.hp - amount).coerceAtLeast(0)
            // A blow wakes a sleeper (but not one just frozen).
            if (!frozen && target0.has(Status.SLEEP)) {
                effects = effects - Status.SLEEP
                events += Event(EventKind.WAKE, i, t)
            }
            if (spell?.status != null && hp > 0) effects = effects + (spell.status to spell.rounds)
            fs[t] = target0.copy(hp = hp, slowed = if (spell?.slows == true && hp > 0) 2 else target0.slowed, effects = effects)
            events += Event(EventKind.DAMAGE, i, t, amount, spell, weak = f > 1f, resisted = f < 1f)
            if (frozen) events += Event(EventKind.FROZEN, i, t)
            if (burnt) events += Event(EventKind.BURN, i, t)
            if (spell?.slows == true && hp > 0) events += Event(EventKind.SLOW, i, t)
            if (spell?.status != null && hp > 0) events += Event(EventKind.STATUS, i, t, status = spell.status)
            if (hp == 0) events += Event(EventKind.OUT, i, t)
        }

        fun heal(target: Int, amount: Int, spell: Spell?, item: Item?) {
            val t = fs[target]
            val gain = amount.coerceAtMost(t.maxHp - t.hp)
            fs[target] = t.copy(hp = t.hp + gain)
            events += Event(EventKind.HEAL, i, target, gain, spell, item)
        }

        fun put(target: Int, s: Spell) {
            val t = fs[target]
            // Bosses only doze for a moment.
            val rounds = if (s.status == Status.SLEEP && t.foe?.boss == true) 1 else s.rounds
            fs[target] = t.copy(effects = t.effects + (s.status!! to rounds))
            events += Event(EventKind.STATUS, i, target, spell = s, status = s.status)
        }

        when (action.kind) {
            Action.Kind.ATTACK -> hit(action.target, 1f, School.PHYSICAL, null)
            Action.Kind.DEFEND -> {
                val gain = DEFEND_MANA.coerceAtMost(a.maxMp - a.mp)
                fs[i] = a.copy(defending = true, mp = a.mp + gain)
                events += Event(EventKind.DEFEND, i, i, gain)
            }
            Action.Kind.ITEM -> when (action.item!!) {
                Item.POTION -> {
                    potions--
                    heal(action.target, (fs[action.target].maxHp + 1) / 2, null, Item.POTION)
                }
                Item.ETHER -> {
                    ethers--
                    val t = fs[action.target]
                    val gain = 10.coerceAtMost(t.maxMp - t.mp)
                    fs[action.target] = t.copy(mp = t.mp + gain)
                    events += Event(EventKind.MANA, i, action.target, gain, item = Item.ETHER)
                }
            }
            Action.Kind.SWAP -> {
                // The friend on the bench takes the place (and the turns) of the one leaving.
                val out = fs[action.target]
                val k = bench.indexOfFirst { it.id == action.friend!!.name }
                val incoming = bench[k]
                bench[k] = out.copy(defending = false, protecting = false, effects = emptyMap(), slowed = 0)
                fs[action.target] = incoming
                events += Event(EventKind.SWAP, i, action.target, friend = action.friend)
            }
            Action.Kind.SPELL -> {
                val s = action.spell!!
                fs[i] = fs[i].copy(mp = a.mp - s.cost)
                val enemiesOf = { fs.indices.filter { fs[it].up && fs[it].ally != a.ally } }
                val friendsOf = { fs.indices.filter { fs[it].up && fs[it].ally == a.ally } }
                when (s.effect) {
                    Effect.DAMAGE -> {
                        val targets = if (s.reach == Reach.ALL_ENEMIES) enemiesOf() else listOf(action.target)
                        for (t in targets) hit(t, s.power, s.school, s)
                    }
                    Effect.HEAL -> {
                        val targets = if (s.reach == Reach.ALL_ALLIES) friendsOf() else listOf(action.target)
                        for (t in targets) heal(t, max(1, (fs[t].maxHp * s.power).roundToInt()), s, null)
                    }
                    Effect.PROTECT -> {
                        fs[i] = fs[i].copy(protecting = true, defending = true)
                        events += Event(EventKind.PROTECT, i, i, spell = s)
                    }
                    Effect.STATUS -> {
                        val targets = when (s.reach) {
                            Reach.ALL_ALLIES -> friendsOf()
                            Reach.ALL_ENEMIES -> enemiesOf()
                            Reach.SELF -> listOf(i)
                            else -> listOf(action.target)
                        }
                        for (t in targets) put(t, s)
                    }
                    Effect.STEAL -> {
                        hit(action.target, s.power, School.PHYSICAL, s)
                        val (r, next) = roll(rng)
                        rng = next
                        when {
                            r < 0.2f -> {
                                potions++
                                events += Event(EventKind.STOLE, i, action.target, item = Item.POTION)
                            }
                            r < 0.35f -> {
                                ethers++
                                events += Event(EventKind.STOLE, i, action.target, item = Item.ETHER)
                            }
                            r < 0.7f -> {
                                val gold = 5 + (r * 20).toInt()
                                loot += gold
                                events += Event(EventKind.STOLE, i, action.target, gold)
                            }
                            else -> events += Event(EventKind.MISSED, i, action.target)
                        }
                    }
                }
            }
        }
        return advance(b.copy(fighters = fs, potions = potions, ethers = ethers, events = events, rng = rng, bench = bench, loot = loot))
    }

    /** Moves to the next fighter standing, starting a new round when everyone has played, then starts its turn. */
    private fun advance(b: Battle): Battle {
        var fs = b.fighters
        val outcome = when {
            fs.none { !it.ally && it.up } -> Outcome.WON
            fs.none { it.ally && it.up } -> Outcome.LOST
            else -> null
        }
        if (outcome != null) return b.copy(queue = emptyList(), outcome = outcome)
        val rest = b.queue.drop(1).filter { fs[it].up }
        if (rest.isNotEmpty()) return startTurn(b.copy(queue = rest))
        // A new round: lasting states wear off a little (sleep is counted on the sleeper's own turns), and the order is worked out again.
        fs = fs.map { f ->
            val worn = f.effects.mapValues { (s, n) -> if (s == Status.SLEEP) n else n - 1 }.filterValues { it > 0 }
            f.copy(slowed = (f.slowed - 1).coerceAtLeast(0), effects = worn)
        }
        return startTurn(b.copy(fighters = fs, queue = order(fs), round = b.round + 1))
    }

    /**
     * The fighter whose turn begins: poison takes a little life (never the last bit of a friend's),
     * and a sleeper loses its turn.
     */
    private fun startTurn(b: Battle): Battle {
        val i = b.current
        if (i < 0) return b
        val f = b.fighters[i]
        val events = b.events.toMutableList()
        var fs = b.fighters
        if (f.has(Status.POISON)) {
            val amount = max(1, f.maxHp / 12)
            val hp = if (f.ally) (f.hp - amount).coerceAtLeast(1) else (f.hp - amount).coerceAtLeast(0)
            fs = fs.toMutableList().also { it[i] = f.copy(hp = hp) }
            events += Event(EventKind.POISON, i, i, f.hp - hp)
            if (hp == 0) {
                events += Event(EventKind.OUT, i, i)
                return advance(b.copy(fighters = fs, events = events))
            }
        }
        val g = fs[i]
        if (g.has(Status.SLEEP)) {
            val left = g.effects.getValue(Status.SLEEP) - 1
            val effects = if (left > 0) g.effects + (Status.SLEEP to left) else g.effects - Status.SLEEP
            fs = fs.toMutableList().also { it[i] = g.copy(effects = effects) }
            events += Event(EventKind.ASLEEP, i, i)
            return advance(b.copy(fighters = fs, events = events))
        }
        return b.copy(fighters = fs, events = events)
    }

    /**
     * What the enemy whose turn it is does: go for the friend with the least life left (or, now
     * and then, any friend).
     */
    fun enemyAction(b: Battle): Action {
        val targets = b.fighters.indices.filter { b.fighters[it].ally && b.fighters[it].up }
        val (r, _) = roll(b.rng)
        val target = if (r < 0.6f) targets.minBy { b.fighters[it].hp } else targets[(r * 1000).toInt() % targets.size]
        return Action(Action.Kind.ATTACK, target = target)
    }

    /** The enemy whose turn it is plays. A boss charges everyone every third round. */
    fun enemyTurn(b: Battle): Battle {
        val i = b.current
        if (i < 0 || b.fighters[i].ally || b.outcome != null) return b
        val a = b.fighters[i]
        if (a.foe?.boss == true && b.round % 3 == 0) return charge(b)
        return act(b, enemyAction(b))
    }

    /** The boss's charge: a weaker blow on every friend standing. */
    private fun charge(b: Battle): Battle {
        val i = b.current
        val a = b.fighters[i]
        var rng = b.rng
        val fs = b.fighters.toMutableList()
        val events = ArrayList<Event>()
        fs[i] = a.copy(defending = false)
        for (t in fs.indices) {
            val f = fs[t]
            if (!f.ally || !f.up) continue
            val (r, next) = roll(rng)
            rng = next
            var dmg = max(1f, a.atk * 0.7f - f.def * 0.5f) * (0.9f + 0.2f * r)
            if (f.defending) dmg *= 0.5f
            if (f.has(Status.SHIELD)) dmg *= 0.5f
            val amount = max(1, dmg.roundToInt())
            val hp = (f.hp - amount).coerceAtLeast(0)
            fs[t] = f.copy(hp = hp, effects = f.effects - Status.SLEEP)
            events += Event(EventKind.DAMAGE, i, t, amount)
            if (hp == 0) events += Event(EventKind.OUT, i, t)
        }
        return advance(b.copy(fighters = fs, events = events, rng = rng))
    }

    /**
     * A good move for the friend whose turn it is, like the hint of other games: heal someone in
     * danger, raise the group's courage, hit a known weak spot, hit everyone when there are
     * several enemies, steal (a rogue) or attack the weakest enemy.
     */
    fun bestAction(b: Battle, known: Set<Foe>): Action {
        val i = b.current
        val a = b.fighters[i]
        val fs = b.fighters
        val friends = fs.indices.filter { fs[it].ally && fs[it].up }
        val enemies = fs.indices.filter { !fs[it].ally && fs[it].up }
        val castable = castable(a)
        // Someone is in danger: heal them.
        val hurt = friends.filter { fs[it].hp * 100 < fs[it].maxHp * 35 }.minByOrNull { fs[it].hp }
        if (hurt != null) {
            val healAll = castable.firstOrNull { it.effect == Effect.HEAL && it.reach == Reach.ALL_ALLIES }
            if (healAll != null && friends.count { fs[it].hp * 2 < fs[it].maxHp } >= 2) return Action(Action.Kind.SPELL, healAll)
            castable.firstOrNull { it.effect == Effect.HEAL && it.reach == Reach.ONE_ALLY }?.let { return Action(Action.Kind.SPELL, it, hurt) }
            if (b.potions > 0) return Action(Action.Kind.ITEM, target = hurt, item = Item.POTION)
        }
        // Courage for the group at the start of a long fight.
        castable.firstOrNull { it.status == Status.COURAGE }?.let { s ->
            if (friends.none { fs[it].has(Status.COURAGE) } && enemies.sumOf { fs[it].hp } > a.atk * 6) return Action(Action.Kind.SPELL, s)
        }
        val damage = castable.filter { it.effect == Effect.DAMAGE }
        // Several enemies left: a spell that reaches them all.
        if (enemies.size >= 2) {
            damage.filter { it.reach == Reach.ALL_ENEMIES }.maxByOrNull { s -> enemies.sumOf { (s.power * factor(fs[it], s.school) * 100).toInt() } }?.let { s ->
                if (enemies.all { factor(fs[it], s.school) >= 1f }) return Action(Action.Kind.SPELL, s)
            }
        }
        val target = enemies.minBy { fs[it].hp }
        // A spell on a weak spot already found.
        val foe = fs[target].foe
        val weakSpell = damage.filter { it.reach == Reach.ONE_ENEMY && foe != null && foe in known && factor(fs[target], it.school) > 1f }.maxByOrNull { it.power }
        if (weakSpell != null) return Action(Action.Kind.SPELL, weakSpell, target)
        // A rogue's hands are better used stealing.
        if (Spell.STEAL in a.spells) return Action(Action.Kind.SPELL, Spell.STEAL, target)
        return Action(Action.Kind.ATTACK, target = target)
    }

    /** Whose weak spots the last turn showed. */
    fun discovered(b: Battle): Set<Foe> =
        b.events.filter { it.kind == EventKind.DAMAGE && (it.weak || it.resisted) }.mapNotNull { b.fighters[it.target].foe }.toSet()

    /**
     * The battle is over: back to the map. A win brings experience, gold and the spot cleared
     * (and a new friend, after a rescue); a loss wakes the group at the inn, rested, with nothing
     * lost. Friends swapped in stay in the travelling group.
     */
    fun finish(w: JourneyWorld): JourneyWorld {
        val b = w.battle ?: return w
        val outcome = b.outcome ?: return w
        // Who travels now: the friends on the field after any swaps; the bench waits at the tavern.
        val party = b.fighters.filter { it.ally && it.id != "hero" }.map { Friend.valueOf(it.id) }
        val reserve = b.bench.map { Friend.valueOf(it.id) }
        if (outcome == Outcome.LOST) return rest(w.copy(battle = null, seed = w.seed + 1, party = party, reserve = reserve))
        val foes = b.fighters.mapNotNull { it.foe }
        // Enemies that grew with the hero give more experience too, so practice always helps.
        val xp = (foes.sumOf { it.xp } * growth(w.hero!!.level, b.spot)).roundToInt()
        val gold = foes.sumOf { it.gold } + b.loot
        // Life and mana carry on to the next fight; friends who fainted get up with a little life.
        val hp = HashMap<String, Int>()
        val mp = HashMap<String, Int>()
        for (f in b.fighters.filter { it.ally } + b.bench) {
            hp[f.id] = max(f.hp, f.maxHp / 4)
            mp[f.id] = f.mp
        }
        var newParty = party
        var newReserve = reserve
        val recruit = Regions.spot(b.spot).recruit
        if (recruit != null && recruit !in newParty && recruit !in newReserve) {
            if (newParty.size < MAX_COMPANIONS) newParty = newParty + recruit else newReserve = newReserve + recruit
        }
        return gainXp(
            w.copy(
                battle = null, hp = hp, mp = mp, gold = w.gold + gold, potions = b.potions, ethers = b.ethers,
                cleared = w.cleared + b.spot, party = newParty, reserve = newReserve, seed = w.seed + 1,
            ),
            xp,
        )
    }

    /** Experience for the hero; each level gained waits for a choice. */
    fun gainXp(w: JourneyWorld, xp: Int): JourneyWorld {
        val h = w.hero ?: return w
        var level = h.level
        var total = h.xp + xp
        var ups = 0
        while (level < MAX_LEVEL && total >= xpFor(level)) {
            total -= xpFor(level)
            level++
            ups++
        }
        if (level == MAX_LEVEL) total = 0
        return w.copy(hero = h.copy(level = level, xp = total), levelUps = w.levelUps + ups)
    }

    /** The two extras offered on the next level up: a new spell on even levels (while there are any), numbers otherwise. */
    fun choices(w: JourneyWorld): Pair<LevelChoice, LevelChoice> {
        val h = w.hero ?: error("no hero")
        val reached = h.level - w.levelUps + 1
        val next = h.heroClass.later.firstOrNull { it !in h.spells }
        val pairs = listOf(Stat.HP to Stat.ATK, Stat.MP to Stat.DEF, Stat.ATK to Stat.SPD, Stat.DEF to Stat.HP)
        return if (reached % 2 == 0 && next != null) {
            LevelChoice(spell = next) to LevelChoice(stat = if (h.heroClass == HeroClass.MAGE || h.heroClass == HeroClass.HEALER) Stat.MP else Stat.HP)
        } else {
            val (a, b) = pairs[reached % pairs.size]
            LevelChoice(stat = a) to LevelChoice(stat = b)
        }
    }

    /** Takes [choice] for one waiting level up. */
    fun choose(w: JourneyWorld, choice: LevelChoice): JourneyWorld {
        val h = w.hero ?: return w
        if (w.levelUps <= 0) return w
        val (a, b) = choices(w)
        if (choice != a && choice != b) return w
        var hero = h
        choice.spell?.let { hero = hero.copy(spells = hero.spells + it) }
        choice.stat?.let { st ->
            val bonus = hero.bonus
            hero = hero.copy(
                bonus = when (st) {
                    Stat.HP -> bonus.copy(hp = bonus.hp + st.amount)
                    Stat.MP -> bonus.copy(mp = bonus.mp + st.amount)
                    Stat.ATK -> bonus.copy(atk = bonus.atk + st.amount)
                    Stat.DEF -> bonus.copy(def = bonus.def + st.amount)
                    Stat.SPD -> bonus.copy(spd = bonus.spd + st.amount)
                },
            )
        }
        return w.copy(hero = hero, levelUps = w.levelUps - 1)
    }
}
