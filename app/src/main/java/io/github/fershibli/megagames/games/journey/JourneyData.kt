package io.github.fershibli.megagames.games.journey

import androidx.annotation.StringRes
import io.github.fershibli.megagames.R

/*
 * The content of the journey as plain data: classes, companions, spells, enemies and the spots
 * of each region. The rules that use them are in JourneyEngine.kt.
 */

/** Schools of magic (and plain blows), each enemy is weak to some and resists others. */
enum class School(@StringRes val label: Int) {
    PHYSICAL(R.string.journey_school_physical),
    FIRE(R.string.journey_school_fire),
    ICE(R.string.journey_school_ice),
    LIGHT(R.string.journey_school_light),
}

/** Who a spell reaches. */
enum class Reach { ONE_ENEMY, ALL_ENEMIES, ONE_ALLY, ALL_ALLIES, SELF }

/** What a spell does. */
enum class Effect { DAMAGE, HEAL, PROTECT }

/**
 * A spell or a skill. [power] multiplies the caster's attack for damage, or its maximum life
 * for healing (as a fraction). [slows] halves the target's speed for two rounds.
 */
enum class Spell(
    @StringRes val label: Int,
    val school: School,
    val cost: Int,
    val reach: Reach,
    val effect: Effect,
    val power: Float,
    val slows: Boolean = false,
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
    KNIGHT(R.string.journey_class_knight, R.string.journey_class_knight_desc, Stats(60, 8, 12, 9, 5), Stats(8, 1, 2, 2, 0), listOf(Spell.POWER_STRIKE), listOf(Spell.RADIANCE, Spell.PROTECT)),
    ARCHER(R.string.journey_class_archer, R.string.journey_class_archer_desc, Stats(46, 12, 11, 6, 9), Stats(6, 2, 2, 1, 1), listOf(Spell.ARROW_RAIN), listOf(Spell.FROST, Spell.BLIZZARD)),
    MAGE(R.string.journey_class_mage, R.string.journey_class_mage_desc, Stats(40, 24, 7, 5, 7), Stats(5, 4, 1, 1, 0), listOf(Spell.SPARK, Spell.FROST), listOf(Spell.BLAZE, Spell.BLIZZARD)),
    HEALER(R.string.journey_class_healer, R.string.journey_class_healer_desc, Stats(44, 22, 8, 6, 6), Stats(6, 3, 1, 1, 0), listOf(Spell.MEND, Spell.RADIANCE), listOf(Spell.PRAYER, Spell.FROST)),
}

/** Friends who join the hero. Their level follows the hero's. */
enum class Friend(
    @StringRes val label: Int,
    @StringRes val role: Int,
    val base: Stats,
    val growth: Stats,
    val spells: List<Spell>,
) {
    BENTO(R.string.journey_bento, R.string.journey_bento_role, Stats(70, 8, 10, 11, 4), Stats(9, 1, 1, 2, 0), listOf(Spell.PROTECT, Spell.POWER_STRIKE)),
    LIA(R.string.journey_lia, R.string.journey_lia_role, Stats(38, 26, 7, 5, 8), Stats(5, 4, 1, 1, 0), listOf(Spell.SPARK, Spell.BLAZE)),
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
    SLIME(R.string.journey_foe_slime, Stats(18, 0, 7, 2, 4), setOf(School.FIRE), setOf(School.ICE), 6, 4),
    BOAR(R.string.journey_foe_boar, Stats(26, 0, 9, 4, 6), setOf(School.ICE), setOf(School.FIRE), 9, 6),
    MUSHROOM(R.string.journey_foe_mushroom, Stats(22, 0, 8, 3, 3), setOf(School.FIRE), setOf(School.LIGHT), 8, 5),
    WOLF(R.string.journey_foe_wolf, Stats(24, 0, 10, 3, 9), setOf(School.LIGHT), setOf(School.ICE), 10, 6),
    GIANT_BOAR(R.string.journey_foe_giant_boar, Stats(130, 0, 13, 6, 5), setOf(School.ICE), setOf(School.FIRE), 70, 80, boss = true),
}

/** What waits at a spot of the map. */
enum class SpotKind { BATTLE, CHEST, FRIEND, RESCUE, BOSS }

/** A spot on a region's map: its place (0..1 across and down the picture) and what is there. */
data class Spot(
    val kind: SpotKind,
    @StringRes val label: Int,
    val x: Float,
    val y: Float,
    val foes: List<Foe> = emptyList(),
    val potions: Int = 0,
    val ethers: Int = 0,
    val gold: Int = 0,
)

/** The regions of the journey. More come in later chapters. */
object Regions {
    val FOREST: List<Spot> = listOf(
        Spot(SpotKind.BATTLE, R.string.journey_spot_clearing, 0.22f, 0.78f, listOf(Foe.SLIME, Foe.SLIME)),
        Spot(SpotKind.CHEST, R.string.journey_spot_chest, 0.42f, 0.84f, potions = 2, gold = 30),
        Spot(SpotKind.FRIEND, R.string.journey_spot_curupira, 0.62f, 0.72f, ethers = 2),
        Spot(SpotKind.BATTLE, R.string.journey_spot_path, 0.80f, 0.56f, listOf(Foe.BOAR, Foe.MUSHROOM)),
        Spot(SpotKind.RESCUE, R.string.journey_spot_rescue, 0.58f, 0.42f, listOf(Foe.WOLF, Foe.WOLF)),
        Spot(SpotKind.BATTLE, R.string.journey_spot_thicket, 0.34f, 0.32f, listOf(Foe.MUSHROOM, Foe.WOLF, Foe.SLIME)),
        Spot(SpotKind.BOSS, R.string.journey_spot_den, 0.56f, 0.14f, listOf(Foe.GIANT_BOAR)),
    )

    /** Where the village is drawn on the map. */
    const val VILLAGE_X = 0.12f
    const val VILLAGE_Y = 0.92f
}
