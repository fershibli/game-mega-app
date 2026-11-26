package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.journey.Action
import io.github.fershibli.megagames.games.journey.Battle
import io.github.fershibli.megagames.games.journey.Friend
import io.github.fershibli.megagames.games.journey.EventKind
import io.github.fershibli.megagames.games.journey.Foe
import io.github.fershibli.megagames.games.journey.HeroClass
import io.github.fershibli.megagames.games.journey.Item
import io.github.fershibli.megagames.games.journey.Journey
import io.github.fershibli.megagames.games.journey.JourneyWorld
import io.github.fershibli.megagames.games.journey.Outcome
import io.github.fershibli.megagames.games.journey.Regions
import io.github.fershibli.megagames.games.journey.School
import io.github.fershibli.megagames.games.journey.Spell
import io.github.fershibli.megagames.games.journey.SpotKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyTest {
    private fun world(cls: HeroClass = HeroClass.KNIGHT) = Journey.start("Ana", cls)

    /** Plays a battle to its end: the group follows the best move, the enemies play their turns. */
    private fun play(b0: Battle, known: Set<io.github.fershibli.megagames.games.journey.Foe> = emptySet()): Battle {
        var b = b0
        var turns = 0
        while (b.outcome == null && turns < 500) {
            b = if (b.fighters[b.current].ally) Journey.act(b, Journey.bestAction(b, known)) else Journey.enemyTurn(b)
            turns++
        }
        return b
    }

    @Test
    fun aNewJourneyHasTheHeroAndBentoAtFullStrength() {
        val w = world()
        assertEquals("Ana", w.hero!!.name)
        assertEquals(listOf(Friend.BENTO), w.party)
        assertEquals(Journey.maxStats(w, "hero").hp, Journey.hp(w, "hero"))
        assertEquals(listOf("hero", "BENTO"), Journey.members(w))
        // Only the first spot of the forest is open.
        assertTrue(Journey.canVisit(w, 0))
        assertFalse(Journey.canVisit(w, 1))
    }

    @Test
    fun theFastestPlaysFirstAndSlownessHalvesSpeed() {
        val w = world(HeroClass.ARCHER)
        val b = Journey.newBattle(w, 3, listOf(Foe.WOLF, Foe.SLIME))
        val speeds = b.queue.map { b.fighters[it].speed }
        assertEquals(speeds.sortedDescending(), speeds)
        val wolf = b.fighters.indexOfFirst { it.foe == Foe.WOLF }
        val slowed = b.fighters[wolf].copy(slowed = 2)
        assertEquals(b.fighters[wolf].spd / 2, slowed.speed)
    }

    @Test
    fun weakSpotsHurtMoreAndResistanceLess() {
        val w = world(HeroClass.MAGE)
        var b = Journey.newBattle(w, 0, listOf(Foe.SLIME, Foe.SLIME))
        // Let the enemies play until it is the hero's turn.
        while (b.fighters[b.current].id != "hero") b = if (b.fighters[b.current].ally) Journey.act(b, Action(Action.Kind.DEFEND)) else Journey.enemyTurn(b)
        val slime = b.fighters.indexOfFirst { it.foe == Foe.SLIME && it.up }
        val fire = Journey.act(b, Action(Action.Kind.SPELL, Spell.SPARK, slime))
        val hit = fire.events.first { it.kind == EventKind.DAMAGE }
        assertTrue(hit.weak)
        assertEquals(setOf(Foe.SLIME), Journey.discovered(fire))
        val ice = Journey.act(b, Action(Action.Kind.SPELL, Spell.FROST, slime))
        val cold = ice.events.first { it.kind == EventKind.DAMAGE }
        assertTrue(cold.resisted)
        assertTrue(hit.amount > cold.amount)
        assertEquals(1.5f, Journey.factor(b.fighters[slime], School.FIRE))
        // The spell cost its mana.
        assertEquals(b.fighters[0].mp - Spell.SPARK.cost, fire.fighters[0].mp)
    }

    @Test
    fun defendingHalvesTheBlowAndGivesBackSomeMana() {
        val w = world()
        var b = Journey.newBattle(w, 0, listOf(Foe.BOAR))
        val hero = 0
        b = b.copy(fighters = b.fighters.mapIndexed { i, f -> if (i == hero) f.copy(mp = 0) else f }, queue = listOf(hero) + b.queue.filter { it != hero })
        val after = Journey.act(b, Action(Action.Kind.DEFEND))
        assertTrue(after.fighters[hero].defending)
        assertEquals(Journey.DEFEND_MANA, after.fighters[hero].mp)
        // The same blow, defended or not.
        val boar = after.fighters.indexOfFirst { it.foe == Foe.BOAR }
        val onDefended = Journey.act(after.copy(queue = listOf(boar)), Action(Action.Kind.ATTACK, target = hero))
        val onOpen = Journey.act(after.copy(queue = listOf(boar), fighters = after.fighters.mapIndexed { i, f -> if (i == hero) f.copy(defending = false) else f }), Action(Action.Kind.ATTACK, target = hero))
        val d1 = onDefended.events.first { it.kind == EventKind.DAMAGE }.amount
        val d2 = onOpen.events.first { it.kind == EventKind.DAMAGE }.amount
        assertTrue("$d1 vs $d2", d1 < d2)
    }

    @Test
    fun bentoProtectsTheGroupFromBlows() {
        val w = world(HeroClass.MAGE)
        var b = Journey.newBattle(w, 0, listOf(Foe.BOAR))
        val bento = b.fighters.indexOfFirst { it.id == "BENTO" }
        b = Journey.act(b.copy(queue = listOf(bento) + b.queue.filter { it != bento }), Action(Action.Kind.SPELL, Spell.PROTECT))
        assertTrue(b.fighters[bento].protecting)
        val boar = b.fighters.indexOfFirst { it.foe == Foe.BOAR }
        val hit = Journey.act(b.copy(queue = listOf(boar)), Action(Action.Kind.ATTACK, target = 0))
        assertEquals(bento, hit.events.first { it.kind == EventKind.DAMAGE }.target)
        assertEquals(b.fighters[0].hp, hit.fighters[0].hp)
    }

    @Test
    fun aPotionHealsHalfAndIsUsedUp() {
        val w = world()
        var b = Journey.newBattle(w, 0, listOf(Foe.SLIME))
        b = b.copy(fighters = b.fighters.mapIndexed { i, f -> if (i == 0) f.copy(hp = 5) else f }, queue = listOf(0) + b.queue.filter { it != 0 })
        val after = Journey.act(b, Action(Action.Kind.ITEM, target = 0, item = Item.POTION))
        assertEquals(5 + (b.fighters[0].maxHp + 1) / 2, after.fighters[0].hp)
        assertEquals(b.potions - 1, after.potions)
        // No potions left: not allowed.
        assertFalse(Journey.valid(b.copy(potions = 0), Action(Action.Kind.ITEM, target = 0, item = Item.POTION)))
    }

    @Test
    fun theSameSeedGivesTheSameBattle() {
        val w = world(HeroClass.ARCHER)
        val a = play(Journey.newBattle(w, 0, listOf(Foe.SLIME, Foe.SLIME)))
        val b = play(Journey.newBattle(w, 0, listOf(Foe.SLIME, Foe.SLIME)))
        assertEquals(a, b)
    }

    @Test
    fun winningGivesExperienceGoldAndClearsTheSpot() {
        var w = Journey.visit(world(), 0)
        assertNotNull(w.battle)
        w = w.copy(battle = play(w.battle!!))
        assertEquals(Outcome.WON, w.battle!!.outcome)
        val before = w.gold
        w = Journey.finish(w)
        assertNull(w.battle)
        assertTrue(0 in w.cleared)
        assertEquals(before + 2 * Foe.SLIME.gold, w.gold)
        // Two slimes give exactly the experience of level 1: the hero reaches level 2 and gets a choice.
        assertEquals(Journey.xpFor(1), 2 * Foe.SLIME.xp)
        assertEquals(2, w.hero!!.level)
        assertEquals(1, w.levelUps)
        assertTrue(Journey.canVisit(w, 1))
    }

    @Test
    fun losingWakesTheGroupRestedAtTheInnWithNothingLost() {
        var w = world().copy(gold = 50, potions = 2)
        w = Journey.visit(w, 0)
        val lost = w.battle!!.copy(fighters = w.battle!!.fighters.map { if (it.ally) it.copy(hp = 0) else it }, outcome = Outcome.LOST)
        w = Journey.finish(w.copy(battle = lost))
        assertNull(w.battle)
        assertEquals(50, w.gold)
        assertEquals(2, w.potions)
        assertEquals(Journey.maxStats(w, "hero").hp, Journey.hp(w, "hero"))
        assertFalse(0 in w.cleared)
    }

    @Test
    fun chestsAndFriendsGiveTheirGiftsOnce() {
        var w = world().copy(cleared = setOf(0))
        w = Journey.visit(w, 1)
        assertEquals(3 + Regions.FOREST[1].potions, w.potions)
        assertEquals(Regions.FOREST[1].gold, w.gold)
        assertFalse(Journey.canVisit(w, 1))
        // A battle won can be fought again, for practice.
        assertTrue(Journey.canVisit(w, 0))
    }

    @Test
    fun levelUpsWaitForAChoiceAndAddWhatWasChosen() {
        var w = Journey.gainXp(world(HeroClass.MAGE), Journey.xpFor(1))
        assertEquals(2, w.hero!!.level)
        assertEquals(1, w.levelUps)
        val (a, b) = Journey.choices(w)
        // Level 2 offers the mage's next spell.
        assertEquals(Spell.BLAZE, a.spell)
        w = Journey.choose(w, a)
        assertTrue(Spell.BLAZE in w.hero!!.spells)
        assertEquals(0, w.levelUps)
        // A choice that was not offered does nothing.
        assertEquals(w, Journey.choose(w, b))
    }

    @Test
    fun theBestMoveHealsSomeoneInDangerAndUsesKnownWeakSpots() {
        val w = world(HeroClass.HEALER)
        var b = Journey.newBattle(w, 0, listOf(Foe.WOLF))
        b = b.copy(queue = listOf(0) + b.queue.filter { it != 0 })
        val hurt = b.copy(fighters = b.fighters.map { if (it.id == "BENTO") it.copy(hp = 3) else it })
        val heal = Journey.bestAction(hurt, emptySet())
        assertEquals(Spell.MEND, heal.spell)
        assertEquals(hurt.fighters.indexOfFirst { it.id == "BENTO" }, heal.target)
        // The wolf fears light, and the group knows it.
        val strike = Journey.bestAction(b, setOf(Foe.WOLF))
        assertEquals(Spell.RADIANCE, strike.spell)
    }

    @Test
    fun theWholeForestCanBePlayedOnEasyFollowingTheBestMove() {
        var w = world(HeroClass.KNIGHT)
        for (spot in Regions.FOREST.indices) {
            var tries = 0
            while (spot !in w.cleared && tries++ < 20) {
                w = Journey.rest(Journey.visit(w, spot))
                val b = w.battle ?: continue
                w = w.copy(battle = play(b, w.known))
                w = Journey.finish(w).let { it.copy(known = it.known + Foe.entries) }
                while (w.levelUps > 0) w = Journey.choose(w, Journey.choices(w).first)
            }
            assertTrue("spot $spot", spot in w.cleared)
        }
        assertTrue(Journey.chapterDone(w))
        // Lia joined after her rescue.
        assertTrue(Friend.LIA in w.party)
        assertTrue(Regions.FOREST.last().kind == SpotKind.BOSS)
    }

    @Test
    fun theWorldSavesAndLoads() {
        val w = Journey.visit(world(HeroClass.ARCHER), 0)
        val text = AppJson.encodeToString(JourneyWorld.serializer(), w)
        assertEquals(w, AppJson.decodeFromString(JourneyWorld.serializer(), text))
        assertTrue(Difficulty.EASY == w.difficulty)
    }
}
