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
 * The whole lasting journey. [hp] and [mp] are each member's current life and mana by id
 * ("hero" or a companion's name). [cleared] holds the spots of the Forest already done.
 */
@Serializable
data class JourneyWorld(
    val hero: Hero? = null,
    val party: List<Friend> = emptyList(),
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
) {
    val up: Boolean get() = hp > 0
    val speed: Int get() = if (slowed > 0) max(1, spd / 2) else spd
}

/** What a fighter does on its turn. */
@Serializable
data class Action(val kind: Kind, val spell: Spell? = null, val target: Int = -1, val item: Item? = null) {
    enum class Kind { ATTACK, SPELL, DEFEND, ITEM }
}

enum class Item { POTION, ETHER }

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
)

enum class EventKind { DAMAGE, HEAL, MANA, DEFEND, PROTECT, SLOW, OUT }

/** How a battle ended. */
enum class Outcome { WON, LOST }

/**
 * A battle in progress. [queue] is who still acts this round, in order; [events] is what the last
 * turn did. [rng] makes every roll repeatable from the seed.
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
) {
    val current: Int get() = queue.firstOrNull() ?: -1
}

/** The rules of the journey. Pure Kotlin. */
object Journey {
    const val MAX_LEVEL = 20
    const val MAX_COMPANIONS = 3
    const val DEFEND_MANA = 2

    fun xpFor(level: Int) = 12 * level * level

    // -------------------------------------------------------------------------------------
    // The group.
    // -------------------------------------------------------------------------------------

    fun heroStats(h: Hero): Stats =
        h.heroClass.base + h.heroClass.growth.times(h.level - 1) + Stats(h.bonus.hp, h.bonus.mp, h.bonus.atk, h.bonus.def, h.bonus.spd)

    fun companionStats(c: Friend, level: Int): Stats = c.base + c.growth.times(level - 1)

    /** The ids of the group, hero first. */
    fun members(w: JourneyWorld): List<String> = listOf("hero") + w.party.map { it.name }

    fun maxStats(w: JourneyWorld, id: String): Stats {
        val h = w.hero ?: error("no hero")
        return if (id == "hero") heroStats(h) else companionStats(Friend.valueOf(id), h.level)
    }

    fun hp(w: JourneyWorld, id: String) = w.hp[id] ?: maxStats(w, id).hp
    fun mp(w: JourneyWorld, id: String) = w.mp[id] ?: maxStats(w, id).mp

    /** A new journey: the hero, with Bento the blacksmith at their side. */
    fun start(name: String, heroClass: HeroClass, difficulty: Difficulty = Difficulty.EASY, seed: Long = 1L): JourneyWorld {
        val clean = name.trim().take(14).ifEmpty { "?" }
        return JourneyWorld(hero = Hero(clean, heroClass), party = listOf(Friend.BENTO), difficulty = difficulty, seed = seed)
    }

    /** A night at the inn: everyone back to full life and mana. */
    fun rest(w: JourneyWorld): JourneyWorld = w.copy(hp = emptyMap(), mp = emptyMap())

    fun setDifficulty(w: JourneyWorld, d: Difficulty): JourneyWorld = w.copy(difficulty = d)

    // -------------------------------------------------------------------------------------
    // The map.
    // -------------------------------------------------------------------------------------

    /** A spot can be visited when the one before it is done; battles already won can be fought again. */
    fun open(w: JourneyWorld, spot: Int): Boolean = spot == 0 || (spot - 1) in w.cleared

    fun canVisit(w: JourneyWorld, spot: Int): Boolean {
        if (spot !in Regions.FOREST.indices || !open(w, spot) || w.battle != null) return false
        val kind = Regions.FOREST[spot].kind
        return spot !in w.cleared || kind == SpotKind.BATTLE
    }

    /** The whole Forest is done when its boss has been beaten. */
    fun chapterDone(w: JourneyWorld): Boolean = Regions.FOREST.lastIndex in w.cleared

    /**
     * Goes to [spot]: a battle starts there, or the chest is opened, or the friend met. Returns
     * the new world and whether something was found (for the message on screen).
     */
    fun visit(w: JourneyWorld, spot: Int): JourneyWorld {
        if (!canVisit(w, spot)) return w
        val s = Regions.FOREST[spot]
        return when (s.kind) {
            SpotKind.CHEST, SpotKind.FRIEND -> w.copy(
                potions = w.potions + s.potions,
                ethers = w.ethers + s.ethers,
                gold = w.gold + s.gold,
                cleared = w.cleared + spot,
            )
            SpotKind.BATTLE, SpotKind.RESCUE, SpotKind.BOSS -> w.copy(battle = newBattle(w, spot, s.foes))
        }
    }

    // -------------------------------------------------------------------------------------
    // Battles.
    // -------------------------------------------------------------------------------------

    private fun scale(d: Difficulty) = when (d) {
        Difficulty.EASY -> 0.8f
        Difficulty.MEDIUM -> 1f
        Difficulty.HARD -> 1.25f
    }

    fun newBattle(w: JourneyWorld, spot: Int, foes: List<Foe>): Battle {
        val h = w.hero ?: error("no hero")
        val allies = members(w).map { id ->
            val m = maxStats(w, id)
            val spells = if (id == "hero") h.spells else Friend.valueOf(id).spells
            Fighter(id, true, null, hp(w, id).coerceAtLeast(1), m.hp, mp(w, id), m.mp, m.atk, m.def, m.spd, spells)
        }
        val k = scale(w.difficulty)
        // Enemies grow a little with the hero, so going back to an old spot still gives a fight.
        val grow = 1f + (h.level - 1) * 0.06f
        val enemies = foes.mapIndexed { i, f ->
            val hp = (f.stats.hp * k * grow).roundToInt()
            Fighter("foe$i", false, f, hp, hp, 0, 0, (f.stats.atk * k * grow).roundToInt(), (f.stats.def * grow).roundToInt(), f.stats.spd)
        }
        val fighters = allies + enemies
        val rng = w.seed * 6364136223846793005L + spot * 1442695040888963407L + w.cleared.size
        return Battle(spot, fighters, order(fighters), potions = w.potions, ethers = w.ethers, rng = rng)
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
        val events = ArrayList<Event>()
        var potions = b.potions
        var ethers = b.ethers
        // A turn ends what was kept up since the last one.
        fs[i] = fs[i].copy(defending = false, protecting = false)
        val a = fs[i]

        fun hit(target: Int, power: Float, school: School, spell: Spell?) {
            // Blows aimed at a friend go to whoever is protecting the group.
            var t = target
            val guard = fs.indices.firstOrNull { fs[it].protecting && fs[it].up && fs[it].ally == fs[target].ally && it != target }
            if (guard != null && school == School.PHYSICAL && !a.ally) t = guard
            val target0 = fs[t]
            val (r, next) = roll(rng)
            rng = next
            val f = factor(target0, school)
            var dmg = max(1f, a.atk * power - target0.def * 0.5f) * f * (0.9f + 0.2f * r)
            if (target0.defending) dmg *= 0.5f
            val amount = max(1, dmg.roundToInt())
            val hp = (target0.hp - amount).coerceAtLeast(0)
            fs[t] = target0.copy(hp = hp, slowed = if (spell?.slows == true && hp > 0) 2 else target0.slowed)
            events += Event(EventKind.DAMAGE, i, t, amount, spell, weak = f > 1f, resisted = f < 1f)
            if (spell?.slows == true && hp > 0) events += Event(EventKind.SLOW, i, t)
            if (hp == 0) events += Event(EventKind.OUT, i, t)
        }

        fun heal(target: Int, amount: Int, spell: Spell?, item: Item?) {
            val t = fs[target]
            val gain = amount.coerceAtMost(t.maxHp - t.hp)
            fs[target] = t.copy(hp = t.hp + gain)
            events += Event(EventKind.HEAL, i, target, gain, spell, item)
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
            Action.Kind.SPELL -> {
                val s = action.spell!!
                fs[i] = fs[i].copy(mp = a.mp - s.cost)
                when (s.effect) {
                    Effect.DAMAGE -> {
                        val targets = if (s.reach == Reach.ALL_ENEMIES) fs.indices.filter { fs[it].up && fs[it].ally != a.ally } else listOf(action.target)
                        for (t in targets) hit(t, s.power, s.school, s)
                    }
                    Effect.HEAL -> {
                        val targets = if (s.reach == Reach.ALL_ALLIES) fs.indices.filter { fs[it].up && fs[it].ally == a.ally } else listOf(action.target)
                        for (t in targets) heal(t, max(1, (fs[t].maxHp * s.power).roundToInt()), s, null)
                    }
                    Effect.PROTECT -> {
                        fs[i] = fs[i].copy(protecting = true, defending = true)
                        events += Event(EventKind.PROTECT, i, i, spell = s)
                    }
                }
            }
        }
        return advance(b.copy(fighters = fs, potions = potions, ethers = ethers, events = events, rng = rng))
    }

    /** Moves to the next fighter standing, starting a new round when everyone has played. */
    private fun advance(b: Battle): Battle {
        val fs = b.fighters
        val outcome = when {
            fs.none { !it.ally && it.up } -> Outcome.WON
            fs.none { it.ally && it.up } -> Outcome.LOST
            else -> null
        }
        if (outcome != null) return b.copy(queue = emptyList(), outcome = outcome)
        val rest = b.queue.drop(1).filter { fs[it].up }
        if (rest.isNotEmpty()) return b.copy(queue = rest)
        // A new round: slowness wears off a little, and the order is worked out again.
        val next = fs.map { if (it.slowed > 0) it.copy(slowed = it.slowed - 1) else it }
        return b.copy(fighters = next, queue = order(next), round = b.round + 1)
    }

    /**
     * What the enemy whose turn it is does: the boss sometimes charges the whole group, the others
     * go for the friend with the least life left (or, now and then, any friend).
     */
    fun enemyAction(b: Battle): Action {
        val a = b.fighters[b.current]
        val targets = b.fighters.indices.filter { b.fighters[it].ally && b.fighters[it].up }
        val (r, _) = roll(b.rng)
        val target = if (r < 0.6f) targets.minBy { b.fighters[it].hp } else targets[(r * 1000).toInt() % targets.size]
        return Action(Action.Kind.ATTACK, target = target).also { require(!a.ally) }
    }

    /** The enemy whose turn it is plays. The boss charges everyone every third round. */
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
        for (t in fs.indices) {
            val f = fs[t]
            if (!f.ally || !f.up) continue
            val (r, next) = roll(rng)
            rng = next
            var dmg = max(1f, a.atk * 0.7f - f.def * 0.5f) * (0.9f + 0.2f * r)
            if (f.defending) dmg *= 0.5f
            val amount = max(1, dmg.roundToInt())
            val hp = (f.hp - amount).coerceAtLeast(0)
            fs[t] = f.copy(hp = hp)
            events += Event(EventKind.DAMAGE, i, t, amount)
            if (hp == 0) events += Event(EventKind.OUT, i, t)
        }
        return advance(b.copy(fighters = fs, events = events, rng = rng))
    }

    /**
     * A good move for the friend whose turn it is, like the hint of other games: heal someone in
     * danger, hit a known weak spot, hit everyone when there are several enemies, or attack the
     * weakest enemy.
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
        return Action(Action.Kind.ATTACK, target = target)
    }

    /** Whose weak spots the last turn showed. */
    fun discovered(b: Battle): Set<Foe> =
        b.events.filter { it.kind == EventKind.DAMAGE && (it.weak || it.resisted) }.mapNotNull { b.fighters[it.target].foe }.toSet()

    /**
     * The battle is over: back to the map. A win brings experience, gold and the spot cleared
     * (and Lia, after her rescue); a loss wakes the group at the inn, rested, with nothing lost.
     */
    fun finish(w: JourneyWorld): JourneyWorld {
        val b = w.battle ?: return w
        val outcome = b.outcome ?: return w
        if (outcome == Outcome.LOST) return rest(w.copy(battle = null, seed = w.seed + 1))
        val foes = b.fighters.mapNotNull { it.foe }
        val xp = foes.sumOf { it.xp }
        val gold = foes.sumOf { it.gold }
        // Life and mana carry on to the next fight; friends who fainted get up with a little life.
        val hp = HashMap<String, Int>()
        val mp = HashMap<String, Int>()
        for (f in b.fighters.filter { it.ally }) {
            hp[f.id] = max(f.hp, f.maxHp / 4)
            mp[f.id] = f.mp
        }
        var party = w.party
        if (Regions.FOREST[b.spot].kind == SpotKind.RESCUE && Friend.LIA !in party && party.size < MAX_COMPANIONS) party = party + Friend.LIA
        return gainXp(w.copy(battle = null, hp = hp, mp = mp, gold = w.gold + gold, potions = b.potions, ethers = b.ethers, cleared = w.cleared + b.spot, party = party, seed = w.seed + 1), xp)
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
