package io.github.fershibli.megagames.games.pet

import androidx.annotation.StringRes
import io.github.fershibli.megagames.R
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Key of the pet's file in the [io.github.fershibli.megagames.core.WorldStore]. Never change it. */
const val PET_WORLD_KEY = "pet"

/** Where the time comes from. The screen uses the phone's clock; tests move time forward by hand. */
fun interface PetClock {
    fun now(): Long
}

val SystemPetClock = PetClock { System.currentTimeMillis() }

/** The ten kinds of friend. The [key] is saved, so it must never change. */
enum class Species(val key: String, @StringRes val label: Int, val favorite: Food) {
    CAT("cat", R.string.pet_species_cat, Food.FISH),
    DOG("dog", R.string.pet_species_dog, Food.COOKIE),
    BUNNY("bunny", R.string.pet_species_bunny, Food.CARROT),
    CHICK("chick", R.string.pet_species_chick, Food.COOKIE),
    PANDA("panda", R.string.pet_species_panda, Food.APPLE),
    FROG("frog", R.string.pet_species_frog, Food.APPLE),
    FOX("fox", R.string.pet_species_fox, Food.FISH),
    PENGUIN("penguin", R.string.pet_species_penguin, Food.FISH),
    HAMSTER("hamster", R.string.pet_species_hamster, Food.CARROT),
    TURTLE("turtle", R.string.pet_species_turtle, Food.APPLE),
    ;

    companion object {
        fun fromKey(key: String?): Species = entries.firstOrNull { it.key == key } ?: CAT
    }
}

/** Food on the menu: how much it fills the tummy and how much fun it is. */
enum class Food(val key: String, @StringRes val label: Int, val fills: Int, val joy: Int) {
    APPLE("apple", R.string.pet_food_apple, 25, 3),
    CARROT("carrot", R.string.pet_food_carrot, 25, 3),
    FISH("fish", R.string.pet_food_fish, 35, 3),
    COOKIE("cookie", R.string.pet_food_cookie, 20, 8),
}

/** Little things the pet can wear. The [key] is saved. */
enum class Accessory(val key: String, @StringRes val label: Int) {
    NONE("none", R.string.pet_wear_none),
    BOW("bow", R.string.pet_wear_bow),
    HAT("hat", R.string.pet_wear_hat),
    FLOWER("flower", R.string.pet_wear_flower),
    SCARF("scarf", R.string.pet_wear_scarf),
    ;

    companion object {
        fun fromKey(key: String?): Accessory = entries.firstOrNull { it.key == key } ?: NONE
    }
}

/**
 * The four needs. Each one slowly goes from full to [PetRules.FLOOR] in [hoursToFloor] hours and
 * never lower: the pet never gets sick, never runs away and is always glad to see you.
 */
enum class Need(@StringRes val label: Int, val hoursToFloor: Int) {
    HAPPINESS(R.string.pet_need_happiness, 24),
    FULLNESS(R.string.pet_need_fullness, 20),
    ENERGY(R.string.pet_need_energy, 30),
    CLEANLINESS(R.string.pet_need_cleanliness, 36),
    ;

    val msToFloor: Long get() = hoursToFloor * 3_600_000L
}

/**
 * One friend. There are no timers: for each need we keep the moment it was (or would have been)
 * completely cared for, and the bar is worked out from the time gone by since then. Caring raises
 * a bar by moving that moment closer to now.
 */
@Serializable
data class Pet(
    val species: String,
    val name: String,
    val adoptedAt: Long,
    val happyAt: Long,
    val fedAt: Long,
    val restedAt: Long,
    val cleanAt: Long,
    /** When the pet went to bed, or null when it is awake. Energy comes back while it sleeps. */
    val asleepSince: Long? = null,
    val accessory: String = Accessory.NONE.key,
) {
    val kind: Species get() = Species.fromKey(species)
    val wearing: Accessory get() = Accessory.fromKey(accessory)
    val asleep: Boolean get() = asleepSince != null

    fun caredAt(need: Need): Long = when (need) {
        Need.HAPPINESS -> happyAt
        Need.FULLNESS -> fedAt
        Need.ENERGY -> restedAt
        Need.CLEANLINESS -> cleanAt
    }

    fun withCaredAt(need: Need, at: Long): Pet = when (need) {
        Need.HAPPINESS -> copy(happyAt = at)
        Need.FULLNESS -> copy(fedAt = at)
        Need.ENERGY -> copy(restedAt = at)
        Need.CLEANLINESS -> copy(cleanAt = at)
    }
}

/**
 * Every friend adopted so far and who is visiting now. Adopting another kind of friend never
 * loses the others: they wait in the garden and can be called back from "Friends".
 */
@Serializable
data class PetWorld(
    val pets: List<Pet> = emptyList(),
    val visiting: String? = null,
) {
    val current: Pet? get() = pets.firstOrNull { it.species == visiting } ?: pets.firstOrNull()

    fun friend(species: Species): Pet? = pets.firstOrNull { it.species == species.key }
}

/** The rules of the pet: needs from timestamps, and what each activity does. Pure Kotlin. */
object PetRules {
    const val FLOOR = 20
    const val FULL = 100

    /** Energy goes from the floor back to full after this much sleep. */
    const val SLEEP_TO_FULL_MS = 2 * 60_000L

    /** Level shown when a pet is first adopted: a little hungry, so there is something to do. */
    private val START = mapOf(Need.HAPPINESS to 80, Need.FULLNESS to 60, Need.ENERGY to 90, Need.CLEANLINESS to 90)

    /** Level of a need cared for at [caredAt], seen at [now], from 20 to 100. */
    fun levelAt(need: Need, caredAt: Long, now: Long): Double {
        val elapsed = (now - caredAt).coerceAtLeast(0L)
        val drop = elapsed.toDouble() * (FULL - FLOOR) / need.msToFloor
        return (FULL - drop).coerceIn(FLOOR.toDouble(), FULL.toDouble())
    }

    /** Moment a need would have been full to be at [level] at [now]. */
    private fun caredAtFor(need: Need, level: Double, now: Long): Long =
        now - ((FULL - level) * need.msToFloor / (FULL - FLOOR)).roundToLong()

    private fun exact(pet: Pet, need: Need, now: Long): Double {
        val base = levelAt(need, pet.caredAt(need), now)
        val since = pet.asleepSince ?: return base
        if (need != Need.ENERGY) return base
        // Asleep: energy as it was at bedtime, then rising while the pet sleeps.
        val atBedtime = levelAt(need, pet.restedAt, since)
        val slept = (now - since).coerceAtLeast(0L)
        return (atBedtime + slept.toDouble() * (FULL - FLOOR) / SLEEP_TO_FULL_MS).coerceAtMost(FULL.toDouble())
    }

    /** Percentage shown on the bar of [need], always between 20 and 100. */
    fun level(pet: Pet, need: Need, now: Long): Int = exact(pet, need, now).roundToInt().coerceIn(FLOOR, FULL)

    /** Raises (or, for a negative [amount], gently lowers) a need, keeping it between 20 and 100. */
    fun change(pet: Pet, need: Need, amount: Int, now: Long): Pet {
        val target = (exact(pet, need, now) + amount).coerceIn(FLOOR.toDouble(), FULL.toDouble())
        return if (need == Need.ENERGY && pet.asleepSince != null) {
            // Keep the sleep going from here: the new level becomes the bedtime level.
            pet.copy(restedAt = caredAtFor(need, target, now), asleepSince = now)
        } else {
            pet.withCaredAt(need, caredAtFor(need, target, now))
        }
    }

    /** The need that most wants attention, when one is low (40 % or less). */
    fun lowest(pet: Pet, now: Long): Need? =
        Need.entries.map { it to level(pet, it, now) }.filter { it.second <= 40 }.minByOrNull { it.second }?.first

    fun newPet(species: Species, name: String, now: Long): Pet {
        fun at(need: Need) = caredAtFor(need, START.getValue(need).toDouble(), now)
        return Pet(
            species = species.key, name = name.trim().take(MAX_NAME), adoptedAt = now,
            happyAt = at(Need.HAPPINESS), fedAt = at(Need.FULLNESS), restedAt = at(Need.ENERGY), cleanAt = at(Need.CLEANLINESS),
        )
    }

    const val MAX_NAME = 16

    // Activities. Each one returns the pet after it; none can ever harm it.

    fun feed(pet: Pet, food: Food, now: Long): Pet {
        val joy = food.joy + if (food == pet.kind.favorite) 10 else 0
        return change(change(pet, Need.FULLNESS, food.fills, now), Need.HAPPINESS, joy, now)
    }

    fun stroke(pet: Pet, now: Long): Pet = change(pet, Need.HAPPINESS, 3, now)

    fun cuddle(pet: Pet, now: Long): Pet = change(pet, Need.HAPPINESS, 6, now)

    fun kiss(pet: Pet, now: Long): Pet = change(pet, Need.HAPPINESS, 8, now)

    fun wave(pet: Pet, now: Long): Pet = change(pet, Need.HAPPINESS, 4, now)

    fun bath(pet: Pet, now: Long): Pet = change(change(pet, Need.CLEANLINESS, 70, now), Need.HAPPINESS, 3, now)

    fun walk(pet: Pet, now: Long): Pet = change(change(pet, Need.HAPPINESS, 20, now), Need.ENERGY, -8, now)

    fun playBall(pet: Pet, now: Long): Pet = change(change(pet, Need.HAPPINESS, 12, now), Need.ENERGY, -5, now)

    /** One round of rock-paper-scissors: fun whoever wins. */
    fun playRound(pet: Pet, now: Long): Pet = change(change(pet, Need.HAPPINESS, 6, now), Need.ENERGY, -2, now)

    /** End of a tic-tac-toe game. */
    fun playBoard(pet: Pet, now: Long): Pet = change(change(pet, Need.HAPPINESS, 15, now), Need.ENERGY, -4, now)

    fun dress(pet: Pet, accessory: Accessory, now: Long): Pet = change(pet.copy(accessory = accessory.key), Need.HAPPINESS, 3, now)

    fun sleep(pet: Pet, now: Long): Pet = if (pet.asleep) pet else pet.copy(asleepSince = now)

    /** Wakes the pet, keeping all the energy it got back while sleeping. */
    fun wake(pet: Pet, now: Long): Pet {
        if (!pet.asleep) return pet
        val energy = exact(pet, Need.ENERGY, now)
        return pet.copy(asleepSince = null, restedAt = caredAtFor(Need.ENERGY, energy, now))
    }

    // The world of friends.

    /** Adopts a new kind of friend (or calls back the one of that kind) and makes it the visitor. */
    fun adopt(world: PetWorld, species: Species, name: String, now: Long): PetWorld {
        if (world.friend(species) != null) return world.copy(visiting = species.key)
        return world.copy(pets = world.pets + newPet(species, name, now), visiting = species.key)
    }

    fun update(world: PetWorld, pet: Pet): PetWorld =
        world.copy(pets = world.pets.map { if (it.species == pet.species) pet else it })
}
