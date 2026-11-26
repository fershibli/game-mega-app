package io.github.fershibli.megagames.games.journey

import androidx.annotation.StringRes
import io.github.fershibli.megagames.R

/*
 * The content of the journey as plain data: classes, companions, spells, enemies, gear and the
 * spots of each region. The rules that use them are in JourneyEngine.kt.
 */

/** Schools of magic (and plain blows); each enemy is weak to some and resists others. */
enum class School(@StringRes val label: Int) {
    PHYSICAL(R.string.journey_school_physical),
    FIRE(R.string.journey_school_fire),
    ICE(R.string.journey_school_ice),
    LIGHT(R.string.journey_school_light),
    NATURE(R.string.journey_school_nature),
    ARCANE(R.string.journey_school_arcane),
}

/** Who a spell reaches. */
enum class Reach { ONE_ENEMY, ALL_ENEMIES, ONE_ALLY, ALL_ALLIES, SELF }

/** What a spell does besides (or instead of) damage. */
enum class Effect { DAMAGE, HEAL, PROTECT, STATUS, STEAL }

/**
 * Lasting states on a fighter, counted in rounds. Poison takes a little life at the start of each
 * turn; a sleeping fighter skips its turns until it is hit; a shield halves the damage taken;
 * courage makes blows stronger. Wet and oiled are for combos: ice on a wet enemy freezes it, fire
 * on an oiled one burns hard.
 */
enum class Status { POISON, SLEEP, SHIELD, COURAGE, WET, OIL }

/**
 * A spell or a skill. [power] multiplies the caster's attack for damage, or its maximum life for
 * healing (as a fraction). [slows] halves the target's speed for two rounds; [status] is put on
 * each target for [rounds].
 */
enum class Spell(
    @StringRes val label: Int,
    val school: School,
    val cost: Int,
    val reach: Reach,
    val effect: Effect,
    val power: Float,
    val slows: Boolean = false,
    val status: Status? = null,
    val rounds: Int = 0,
) {
    POWER_STRIKE(R.string.journey_spell_power_strike, School.PHYSICAL, 4, Reach.ONE_ENEMY, Effect.DAMAGE, 1.8f),
    ARROW_RAIN(R.string.journey_spell_arrow_rain, School.PHYSICAL, 6, Reach.ALL_ENEMIES, Effect.DAMAGE, 0.85f),
    PROTECT(R.string.journey_spell_protect, School.PHYSICAL, 3, Reach.SELF, Effect.PROTECT, 0f),
    SPARK(R.string.journey_spell_spark, School.FIRE, 3, Reach.ONE_ENEMY, Effect.DAMAGE, 1.5f),
    BLAZE(R.string.journey_spell_blaze, School.FIRE, 7, Reach.ALL_ENEMIES, Effect.DAMAGE, 1.0f),
    FROST(R.string.journey_spell_frost, School.ICE, 4, Reach.ONE_ENEMY, Effect.DAMAGE, 1.3f, slows = true),
    BLIZZARD(R.string.journey_spell_blizzard, School.ICE, 8, Reach.ALL_ENEMIES, Effect.DAMAGE, 0.9f, slows = true),
    RADIANCE(R.string.journey_spell_radiance, School.LIGHT, 4, Reach.ONE_ENEMY, Effect.DAMAGE, 1.5f),
    MEND(R.string.journey_spell_mend, School.LIGHT, 4, Reach.ONE_ALLY, Effect.HEAL, 0.45f),
    PRAYER(R.string.journey_spell_prayer, School.LIGHT, 9, Reach.ALL_ALLIES, Effect.HEAL, 0.3f),
    THORNS(R.string.journey_spell_thorns, School.NATURE, 4, Reach.ONE_ENEMY, Effect.DAMAGE, 1.1f, status = Status.POISON, rounds = 3),
    VINES(R.string.journey_spell_vines, School.NATURE, 7, Reach.ALL_ENEMIES, Effect.DAMAGE, 0.7f, slows = true),
    WAVE(R.string.journey_spell_wave, School.NATURE, 5, Reach.ALL_ENEMIES, Effect.DAMAGE, 0.6f, status = Status.WET, rounds = 2),
    ARCANE_BOLT(R.string.journey_spell_arcane_bolt, School.ARCANE, 3, Reach.ONE_ENEMY, Effect.DAMAGE, 1.4f),
    SLEEP(R.string.journey_spell_sleep, School.ARCANE, 5, Reach.ONE_ENEMY, Effect.STATUS, 0f, status = Status.SLEEP, rounds = 2),
    BARRIER(R.string.journey_spell_barrier, School.ARCANE, 5, Reach.ONE_ALLY, Effect.STATUS, 0f, status = Status.SHIELD, rounds = 3),
    SONG(R.string.journey_spell_song, School.ARCANE, 6, Reach.ALL_ALLIES, Effect.STATUS, 0f, status = Status.COURAGE, rounds = 3),
    MELODY(R.string.journey_spell_melody, School.LIGHT, 7, Reach.ALL_ALLIES, Effect.HEAL, 0.22f),
    STEAL(R.string.journey_spell_steal, School.PHYSICAL, 0, Reach.ONE_ENEMY, Effect.STEAL, 0.6f),
    OIL_FLASK(R.string.journey_spell_oil_flask, School.PHYSICAL, 2, Reach.ONE_ENEMY, Effect.DAMAGE, 0.5f, status = Status.OIL, rounds = 3),
}

/** Base numbers of a fighter at level 1, and how much each level adds. */
data class Stats(val hp: Int, val mp: Int, val atk: Int, val def: Int, val spd: Int) {
    operator fun plus(o: Stats) = Stats(hp + o.hp, mp + o.mp, atk + o.atk, def + o.def, spd + o.spd)
    fun times(k: Int) = Stats(hp * k, mp * k, atk * k, def * k, spd * k)
}

/**
 * The hero's classes: their numbers, the spells they start with and the ones they can learn on
 * the way (offered one by one when they level up).
 */
enum class HeroClass(
    @StringRes val label: Int,
    @StringRes val description: Int,
    val base: Stats,
    val growth: Stats,
    val start: List<Spell>,
    val later: List<Spell>,
) {
    KNIGHT(R.string.journey_class_knight, R.string.journey_class_knight_desc, Stats(60, 8, 12, 9, 5), Stats(8, 1, 2, 2, 0), listOf(Spell.POWER_STRIKE), listOf(Spell.RADIANCE, Spell.PROTECT, Spell.BARRIER)),
    ARCHER(R.string.journey_class_archer, R.string.journey_class_archer_desc, Stats(46, 12, 11, 6, 9), Stats(6, 2, 2, 1, 1), listOf(Spell.ARROW_RAIN), listOf(Spell.FROST, Spell.THORNS, Spell.BLIZZARD)),
    MAGE(R.string.journey_class_mage, R.string.journey_class_mage_desc, Stats(40, 24, 7, 5, 7), Stats(5, 4, 1, 1, 0), listOf(Spell.SPARK, Spell.FROST), listOf(Spell.BLAZE, Spell.ARCANE_BOLT, Spell.BLIZZARD, Spell.SLEEP)),
    HEALER(R.string.journey_class_healer, R.string.journey_class_healer_desc, Stats(44, 22, 8, 6, 6), Stats(6, 3, 1, 1, 0), listOf(Spell.MEND, Spell.RADIANCE), listOf(Spell.PRAYER, Spell.BARRIER, Spell.THORNS, Spell.FROST)),
}

/** Friends who join the hero, each with a clear part in the group. Their level follows the hero's. */
enum class Friend(
    @StringRes val label: Int,
    @StringRes val role: Int,
    val base: Stats,
    val growth: Stats,
    val spells: List<Spell>,
) {
    BENTO(R.string.journey_bento, R.string.journey_bento_role, Stats(70, 8, 10, 11, 4), Stats(9, 1, 1, 2, 0), listOf(Spell.PROTECT, Spell.POWER_STRIKE)),
    LIA(R.string.journey_lia, R.string.journey_lia_role, Stats(38, 26, 7, 5, 8), Stats(5, 4, 1, 1, 0), listOf(Spell.SPARK, Spell.BLAZE)),
    IARA(R.string.journey_iara, R.string.journey_iara_role, Stats(44, 26, 7, 6, 7), Stats(6, 4, 1, 1, 0), listOf(Spell.MEND, Spell.BARRIER, Spell.WAVE)),
    ZECA(R.string.journey_zeca, R.string.journey_zeca_role, Stats(42, 12, 10, 5, 13), Stats(5, 2, 2, 1, 1), listOf(Spell.STEAL, Spell.OIL_FLASK)),
    TITO(R.string.journey_tito, R.string.journey_tito_role, Stats(40, 26, 7, 5, 7), Stats(5, 4, 1, 1, 0), listOf(Spell.SLEEP, Spell.ARCANE_BOLT, Spell.VINES)),
    DORA(R.string.journey_dora, R.string.journey_dora_role, Stats(46, 24, 8, 6, 6), Stats(6, 3, 1, 1, 0), listOf(Spell.SONG, Spell.MELODY)),
}

/**
 * A kind of enemy. They do not die: beaten ones faint or run away. [weak] schools hurt them one
 * and a half times as much; [resists] half as much.
 */
enum class Foe(
    @StringRes val label: Int,
    val stats: Stats,
    val weak: Set<School>,
    val resists: Set<School>,
    val xp: Int,
    val gold: Int,
    val boss: Boolean = false,
) {
    // The forest.
    SLIME(R.string.journey_foe_slime, Stats(18, 0, 7, 2, 4), setOf(School.FIRE), setOf(School.ICE), 10, 4),
    BOAR(R.string.journey_foe_boar, Stats(26, 0, 9, 4, 6), setOf(School.ICE), setOf(School.FIRE), 9, 6),
    MUSHROOM(R.string.journey_foe_mushroom, Stats(22, 0, 8, 3, 3), setOf(School.FIRE), setOf(School.LIGHT), 8, 5),
    WOLF(R.string.journey_foe_wolf, Stats(24, 0, 10, 3, 9), setOf(School.LIGHT), setOf(School.ICE), 10, 6),
    GIANT_BOAR(R.string.journey_foe_giant_boar, Stats(130, 0, 13, 6, 5), setOf(School.ICE), setOf(School.FIRE), 70, 80, boss = true),

    // The river.
    FROG(R.string.journey_foe_frog, Stats(30, 0, 12, 5, 8), setOf(School.ICE), setOf(School.NATURE), 13, 9),
    CRAB(R.string.journey_foe_crab, Stats(38, 0, 13, 9, 4), setOf(School.NATURE), setOf(School.ICE), 15, 10),
    PIRANHA(R.string.journey_foe_piranha, Stats(28, 0, 15, 4, 11), setOf(School.LIGHT), setOf(School.FIRE), 14, 9),
    GIANT_CRAB(R.string.journey_foe_giant_crab, Stats(210, 0, 18, 11, 5), setOf(School.NATURE), setOf(School.ICE), 120, 140, boss = true),

    // The hills.
    BAT(R.string.journey_foe_bat, Stats(34, 0, 16, 5, 12), setOf(School.LIGHT), setOf(School.NATURE), 19, 12),
    GNOME(R.string.journey_foe_gnome, Stats(40, 0, 17, 7, 9), setOf(School.FIRE), setOf(School.ARCANE), 20, 14),
    ROCKLING(R.string.journey_foe_rockling, Stats(52, 0, 18, 14, 3), setOf(School.NATURE), setOf(School.FIRE), 22, 14),
    TROLL(R.string.journey_foe_troll, Stats(300, 0, 24, 13, 5), setOf(School.ARCANE), setOf(School.FIRE), 190, 220, boss = true),

    // The ruins.
    GHOST(R.string.journey_foe_ghost, Stats(46, 0, 21, 6, 10), setOf(School.LIGHT), setOf(School.PHYSICAL), 26, 17),
    ARMOR(R.string.journey_foe_armor, Stats(62, 0, 22, 17, 5), setOf(School.ARCANE), setOf(School.ICE), 28, 19),
    SPIDER(R.string.journey_foe_spider, Stats(48, 0, 23, 8, 11), setOf(School.FIRE), setOf(School.NATURE), 27, 18),
    GOLEM(R.string.journey_foe_golem, Stats(400, 0, 29, 17, 4), setOf(School.ARCANE), setOf(School.FIRE), 260, 300, boss = true),

    // The castle.
    IMP(R.string.journey_foe_imp, Stats(56, 0, 27, 9, 12), setOf(School.ICE), setOf(School.FIRE), 33, 22),
    GARGOYLE(R.string.journey_foe_gargoyle, Stats(70, 0, 28, 19, 6), setOf(School.NATURE), setOf(School.ICE), 35, 24),
    DARK_KNIGHT(R.string.journey_foe_dark_knight, Stats(76, 0, 30, 16, 8), setOf(School.LIGHT), setOf(School.ARCANE), 37, 26),
    SHADOW_KING(R.string.journey_foe_shadow_king, Stats(520, 0, 34, 19, 7), setOf(School.LIGHT), setOf(School.ARCANE), 400, 500, boss = true),
}

/** What waits at a spot of the map. */
enum class SpotKind { BATTLE, CHEST, FRIEND, RESCUE, BOSS }

/**
 * A spot on a region's map: its place (0..1 across and down the picture) and what is there. A
 * chest may hold a [scroll] that teaches the hero a spell; a friend has something to [say]; a
 * rescue brings a [recruit] into the group.
 */
data class Spot(
    val kind: SpotKind,
    @StringRes val label: Int,
    val x: Float,
    val y: Float,
    val foes: List<Foe> = emptyList(),
    val potions: Int = 0,
    val ethers: Int = 0,
    val gold: Int = 0,
    val scroll: Spell? = null,
    @StringRes val say: Int = 0,
    val recruit: Friend? = null,
)

/**
 * The five regions, one after the other: each opens once the boss of the one before is beaten.
 * [ground] and [water] tint the map; [chapterDone] is told when the boss runs away.
 */
enum class Region(@StringRes val label: Int, @StringRes val chapterDone: Int, val ground: Long, val water: Boolean, val spots: List<Spot>) {
    FOREST(
        R.string.journey_forest, R.string.journey_chapter_done, 0xFF8BC34A, false,
        listOf(
            Spot(SpotKind.BATTLE, R.string.journey_spot_clearing, 0.22f, 0.78f, listOf(Foe.SLIME, Foe.SLIME)),
            Spot(SpotKind.CHEST, R.string.journey_spot_chest, 0.42f, 0.84f, potions = 2, gold = 30),
            Spot(SpotKind.FRIEND, R.string.journey_spot_curupira, 0.62f, 0.72f, ethers = 2, say = R.string.journey_curupira_says),
            Spot(SpotKind.BATTLE, R.string.journey_spot_path, 0.80f, 0.56f, listOf(Foe.BOAR, Foe.MUSHROOM)),
            Spot(SpotKind.RESCUE, R.string.journey_spot_rescue, 0.58f, 0.42f, listOf(Foe.WOLF, Foe.WOLF), recruit = Friend.LIA),
            Spot(SpotKind.BATTLE, R.string.journey_spot_thicket, 0.34f, 0.32f, listOf(Foe.MUSHROOM, Foe.WOLF, Foe.SLIME)),
            Spot(SpotKind.BOSS, R.string.journey_spot_den, 0.56f, 0.14f, listOf(Foe.GIANT_BOAR)),
        ),
    ),
    RIVER(
        R.string.journey_river, R.string.journey_chapter_river, 0xFFA5C96A, true,
        listOf(
            Spot(SpotKind.BATTLE, R.string.journey_spot_bank, 0.20f, 0.80f, listOf(Foe.FROG, Foe.FROG)),
            Spot(SpotKind.CHEST, R.string.journey_spot_chest, 0.40f, 0.88f, potions = 2, ethers = 1, gold = 60, scroll = Spell.THORNS),
            Spot(SpotKind.FRIEND, R.string.journey_spot_boto, 0.66f, 0.76f, ethers = 2, say = R.string.journey_boto_says),
            Spot(SpotKind.BATTLE, R.string.journey_spot_ford, 0.82f, 0.56f, listOf(Foe.CRAB, Foe.PIRANHA)),
            Spot(SpotKind.RESCUE, R.string.journey_spot_iara, 0.56f, 0.44f, listOf(Foe.PIRANHA, Foe.FROG, Foe.PIRANHA), recruit = Friend.IARA),
            Spot(SpotKind.BATTLE, R.string.journey_spot_falls, 0.30f, 0.32f, listOf(Foe.CRAB, Foe.FROG, Foe.CRAB)),
            Spot(SpotKind.BOSS, R.string.journey_spot_cave, 0.54f, 0.14f, listOf(Foe.GIANT_CRAB)),
        ),
    ),
    HILLS(
        R.string.journey_hills, R.string.journey_chapter_hills, 0xFFC5B56A, false,
        listOf(
            Spot(SpotKind.BATTLE, R.string.journey_spot_slope, 0.20f, 0.80f, listOf(Foe.BAT, Foe.GNOME)),
            Spot(SpotKind.CHEST, R.string.journey_spot_chest, 0.44f, 0.86f, potions = 3, gold = 90, scroll = Spell.ARCANE_BOLT),
            Spot(SpotKind.FRIEND, R.string.journey_spot_caipora, 0.68f, 0.74f, ethers = 2, say = R.string.journey_caipora_says),
            Spot(SpotKind.BATTLE, R.string.journey_spot_mine, 0.84f, 0.54f, listOf(Foe.ROCKLING, Foe.GNOME)),
            Spot(SpotKind.RESCUE, R.string.journey_spot_zeca, 0.58f, 0.42f, listOf(Foe.GNOME, Foe.BAT, Foe.GNOME), recruit = Friend.ZECA),
            Spot(SpotKind.BATTLE, R.string.journey_spot_peak, 0.30f, 0.30f, listOf(Foe.ROCKLING, Foe.BAT, Foe.ROCKLING)),
            Spot(SpotKind.BOSS, R.string.journey_spot_bridge, 0.56f, 0.13f, listOf(Foe.TROLL)),
        ),
    ),
    RUINS(
        R.string.journey_ruins, R.string.journey_chapter_ruins, 0xFFA9B0A0, false,
        listOf(
            Spot(SpotKind.BATTLE, R.string.journey_spot_gate, 0.20f, 0.80f, listOf(Foe.GHOST, Foe.SPIDER)),
            Spot(SpotKind.CHEST, R.string.journey_spot_chest, 0.42f, 0.88f, potions = 3, ethers = 2, gold = 120, scroll = Spell.SLEEP),
            Spot(SpotKind.FRIEND, R.string.journey_spot_owl, 0.66f, 0.76f, ethers = 3, say = R.string.journey_owl_says),
            Spot(SpotKind.BATTLE, R.string.journey_spot_hall, 0.84f, 0.54f, listOf(Foe.ARMOR, Foe.GHOST)),
            Spot(SpotKind.RESCUE, R.string.journey_spot_tito, 0.58f, 0.42f, listOf(Foe.SPIDER, Foe.GHOST, Foe.SPIDER), recruit = Friend.TITO),
            Spot(SpotKind.BATTLE, R.string.journey_spot_library, 0.30f, 0.30f, listOf(Foe.ARMOR, Foe.SPIDER, Foe.GHOST)),
            Spot(SpotKind.BOSS, R.string.journey_spot_altar, 0.56f, 0.13f, listOf(Foe.GOLEM)),
        ),
    ),
    CASTLE(
        R.string.journey_castle, R.string.journey_chapter_castle, 0xFF9FA8B8, false,
        listOf(
            Spot(SpotKind.BATTLE, R.string.journey_spot_drawbridge, 0.20f, 0.80f, listOf(Foe.IMP, Foe.GARGOYLE)),
            Spot(SpotKind.CHEST, R.string.journey_spot_chest, 0.42f, 0.88f, potions = 4, ethers = 2, gold = 160),
            Spot(SpotKind.FRIEND, R.string.journey_spot_guard, 0.66f, 0.76f, ethers = 3, say = R.string.journey_guard_says),
            Spot(SpotKind.BATTLE, R.string.journey_spot_courtyard, 0.84f, 0.54f, listOf(Foe.DARK_KNIGHT, Foe.IMP)),
            Spot(SpotKind.RESCUE, R.string.journey_spot_dora, 0.58f, 0.42f, listOf(Foe.GARGOYLE, Foe.IMP, Foe.GARGOYLE), recruit = Friend.DORA),
            Spot(SpotKind.BATTLE, R.string.journey_spot_tower, 0.30f, 0.30f, listOf(Foe.DARK_KNIGHT, Foe.GARGOYLE, Foe.IMP)),
            Spot(SpotKind.BOSS, R.string.journey_spot_throne, 0.56f, 0.13f, listOf(Foe.SHADOW_KING)),
        ),
    ),
}

/** Gear sold in the village shop, for the hero. One of each slot is worn at a time. */
enum class Slot { WEAPON, ARMOR, CHARM }

enum class Gear(@StringRes val label: Int, val slot: Slot, val price: Int, val atk: Int = 0, val def: Int = 0, val mp: Int = 0) {
    IRON_SWORD(R.string.journey_gear_iron_sword, Slot.WEAPON, 80, atk = 3),
    STEEL_SWORD(R.string.journey_gear_steel_sword, Slot.WEAPON, 220, atk = 6),
    ROYAL_SWORD(R.string.journey_gear_royal_sword, Slot.WEAPON, 480, atk = 10),
    LEATHER(R.string.journey_gear_leather, Slot.ARMOR, 60, def = 2),
    CHAIN_MAIL(R.string.journey_gear_chain_mail, Slot.ARMOR, 190, def = 5),
    PLATE(R.string.journey_gear_plate, Slot.ARMOR, 420, def = 9),
    MOON_CHARM(R.string.journey_gear_moon_charm, Slot.CHARM, 150, mp = 10),
    STAR_CHARM(R.string.journey_gear_star_charm, Slot.CHARM, 360, mp = 20),
}

/** Old names kept so earlier code and saves still find the forest. */
object Regions {
    val FOREST: List<Spot> get() = Region.FOREST.spots

    /** Where the village is drawn on every map. */
    const val VILLAGE_X = 0.12f
    const val VILLAGE_Y = 0.92f

    /** Spots are numbered across regions: a hundred per region, so the forest keeps 0 to 6. */
    fun id(region: Region, spot: Int) = region.ordinal * 100 + spot
    fun region(id: Int) = Region.entries[id / 100]
    fun spot(id: Int): Spot = region(id).spots[id % 100]
    fun index(id: Int) = id % 100
}
