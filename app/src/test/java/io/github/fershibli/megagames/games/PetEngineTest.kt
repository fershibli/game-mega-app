package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.games.pet.Accessory
import io.github.fershibli.megagames.games.pet.Food
import io.github.fershibli.megagames.games.pet.Need
import io.github.fershibli.megagames.games.pet.Pet
import io.github.fershibli.megagames.games.pet.PetRules
import io.github.fershibli.megagames.games.pet.PetWorld
import io.github.fershibli.megagames.games.pet.Species
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetEngineTest {
    private val hour = 3_600_000L
    private val t0 = 1_800_000_000_000L

    private fun Pet.at(now: Long) = Need.entries.associateWith { PetRules.level(this, it, now) }

    @Test
    fun aNewFriendStartsALittleHungry() {
        val pet = PetRules.newPet(Species.PANDA, "  Coco  ", t0)
        assertEquals("Coco", pet.name)
        assertEquals(mapOf(Need.HAPPINESS to 80, Need.FULLNESS to 60, Need.ENERGY to 90, Need.CLEANLINESS to 90), pet.at(t0))
        assertEquals(Species.PANDA, pet.kind)
        assertFalse(pet.asleep)
    }

    @Test
    fun barsComeFromTimestampsAndStopAtTheFloor() {
        val full = Pet("cat", "Pip", t0, happyAt = t0, fedAt = t0, restedAt = t0, cleanAt = t0)
        assertEquals(100, PetRules.level(full, Need.FULLNESS, t0))
        // Food goes from full to the floor in 20 hours: 80 points, 4 per hour.
        assertEquals(60, PetRules.level(full, Need.FULLNESS, t0 + 10 * hour))
        assertEquals(20, PetRules.level(full, Need.FULLNESS, t0 + 20 * hour))
        // Happiness takes a day, energy 30 hours, cleanliness a day and a half.
        assertEquals(60, PetRules.level(full, Need.HAPPINESS, t0 + 12 * hour))
        assertEquals(60, PetRules.level(full, Need.ENERGY, t0 + 15 * hour))
        assertEquals(60, PetRules.level(full, Need.CLEANLINESS, t0 + 18 * hour))
        // A long holiday: everything rests on the floor, never lower.
        val later = t0 + 400 * 24 * hour
        assertEquals(Need.entries.associateWith { 20 }, full.at(later))
        // A clock set backwards never shows more than full.
        assertEquals(100, PetRules.level(full, Need.HAPPINESS, t0 - 5 * hour))
    }

    @Test
    fun activitiesRaiseTheBarsAndNothingGoesBelowTheFloor() {
        var pet = PetRules.newPet(Species.DOG, "Rex", t0)
        val now = t0 + 10 * hour // food is down to 20 by now
        assertEquals(20, PetRules.level(pet, Need.FULLNESS, now))
        pet = PetRules.feed(pet, Food.APPLE, now)
        assertEquals(45, PetRules.level(pet, Need.FULLNESS, now))
        pet = PetRules.feed(pet, Food.FISH, now)
        assertEquals(80, PetRules.level(pet, Need.FULLNESS, now))
        pet = PetRules.feed(pet, Food.FISH, now)
        assertEquals(100, PetRules.level(pet, Need.FULLNESS, now))

        val happy = PetRules.level(pet, Need.HAPPINESS, now)
        val kissed = PetRules.kiss(pet, now)
        assertEquals(minOf(100, happy + 8), PetRules.level(kissed, Need.HAPPINESS, now))
        val stroked = PetRules.stroke(PetRules.stroke(pet, now), now)
        assertEquals(minOf(100, happy + 6), PetRules.level(stroked, Need.HAPPINESS, now))

        val washed = PetRules.bath(pet, now)
        assertEquals(100, PetRules.level(washed, Need.CLEANLINESS, now))

        // A walk is great fun and a little tiring, but a tired pet is never pushed under 20 %.
        val tired = PetRules.newPet(Species.DOG, "Rex", t0 - 100 * hour)
        assertEquals(20, PetRules.level(tired, Need.ENERGY, t0))
        val walked = PetRules.walk(tired, t0)
        assertEquals(20, PetRules.level(walked, Need.ENERGY, t0))
        assertEquals(40, PetRules.level(walked, Need.HAPPINESS, t0))
        // After caring, time keeps going from the new level.
        assertEquals(36, PetRules.level(walked, Need.HAPPINESS, t0 + 72 * 60_000L))
    }

    @Test
    fun theFavoriteFoodIsTheMostFun() {
        val pet = PetRules.newPet(Species.BUNNY, "Daisy", t0)
        val carrot = PetRules.feed(pet, Food.CARROT, t0)
        val apple = PetRules.feed(pet, Food.APPLE, t0)
        assertEquals(Food.CARROT, Species.BUNNY.favorite)
        assertEquals(PetRules.level(pet, Need.HAPPINESS, t0) + 13, PetRules.level(carrot, Need.HAPPINESS, t0))
        assertTrue(PetRules.level(carrot, Need.HAPPINESS, t0) > PetRules.level(apple, Need.HAPPINESS, t0))
    }

    @Test
    fun energyComesBackWhileSleeping() {
        val pet = PetRules.newPet(Species.FOX, "Filou", t0 - 100 * hour)
        assertEquals(20, PetRules.level(pet, Need.ENERGY, t0))
        val asleep = PetRules.sleep(pet, t0)
        assertTrue(asleep.asleep)
        assertEquals(asleep, PetRules.sleep(asleep, t0 + 1000)) // already asleep
        // From the floor to full in two minutes of sleep.
        assertEquals(60, PetRules.level(asleep, Need.ENERGY, t0 + 60_000))
        assertEquals(100, PetRules.level(asleep, Need.ENERGY, t0 + 120_000))
        assertEquals(100, PetRules.level(asleep, Need.ENERGY, t0 + 10 * hour))
        // Waking up keeps the energy gained; then it slowly goes down again.
        val awake = PetRules.wake(asleep, t0 + 60_000)
        assertFalse(awake.asleep)
        assertEquals(60, PetRules.level(awake, Need.ENERGY, t0 + 60_000))
        assertEquals(56, PetRules.level(awake, Need.ENERGY, t0 + 60_000 + 90 * 60_000L))
        // A change of energy during sleep does not wake the pet and the sleep goes on from there.
        val nudged = PetRules.change(asleep, Need.ENERGY, -10, t0 + 60_000)
        assertTrue(nudged.asleep)
        assertEquals(50, PetRules.level(nudged, Need.ENERGY, t0 + 60_000))
        assertEquals(70, PetRules.level(nudged, Need.ENERGY, t0 + 90_000))
    }

    @Test
    fun theLowestNeedIsWhatThePetHintsAt() {
        val pet = PetRules.newPet(Species.CHICK, "Pip", t0)
        assertNull(PetRules.lowest(pet, t0))
        assertEquals(Need.FULLNESS, PetRules.lowest(pet, t0 + 5 * hour))
        assertEquals(Need.HAPPINESS, PetRules.lowest(PetRules.feed(pet, Food.FISH, t0 + 15 * hour), t0 + 15 * hour))
    }

    @Test
    fun adoptingAnotherFriendKeepsTheFirstOne() {
        var world = PetRules.adopt(PetWorld(), Species.CAT, "Mimi", t0)
        assertEquals("Mimi", world.current?.name)
        world = PetRules.adopt(world, Species.TURTLE, "Tito", t0 + hour)
        assertEquals(2, world.pets.size)
        assertEquals("Tito", world.current?.name)
        // Choosing a kind already adopted just calls that friend back, with its own name and bars.
        world = PetRules.adopt(world, Species.CAT, "Other", t0 + 2 * hour)
        assertEquals(2, world.pets.size)
        assertEquals("Mimi", world.current?.name)
        val dressed = PetRules.dress(world.current!!, Accessory.HAT, t0 + 2 * hour)
        world = PetRules.update(world, dressed)
        assertEquals(Accessory.HAT, world.friend(Species.CAT)?.wearing)
        assertEquals(Accessory.NONE, world.friend(Species.TURTLE)?.wearing)
    }

    @Test
    fun theWorldSurvivesSavingAndLoading() {
        var world = PetRules.adopt(PetWorld(), Species.PENGUIN, "Bubbles", t0)
        world = PetRules.update(world, PetRules.sleep(PetRules.dress(world.current!!, Accessory.SCARF, t0), t0 + hour))
        val text = AppJson.encodeToString(PetWorld.serializer(), world)
        val back = AppJson.decodeFromString(PetWorld.serializer(), text)
        assertEquals(world, back)
        assertEquals(world.current!!.at(t0 + 30 * hour), back.current!!.at(t0 + 30 * hour))
        // Unknown species or accessories from a newer version fall back gently.
        val odd = back.current!!.copy(species = "dragon", accessory = "crown")
        assertEquals(Species.CAT, odd.kind)
        assertEquals(Accessory.NONE, odd.wearing)
    }
}
