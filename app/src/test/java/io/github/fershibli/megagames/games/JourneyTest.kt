package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.journey.Action
import io.github.fershibli.megagames.games.journey.Battle
import io.github.fershibli.megagames.games.journey.Gear
import io.github.fershibli.megagames.games.journey.Region
import io.github.fershibli.megagames.games.journey.Status
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

    // ---------------------------------------------------------------------------------------
    // Chapter 2 onwards: regions, friends, the shop and the tavern, lasting states and combos.
    // ---------------------------------------------------------------------------------------

    /** A battle where the fighter [actor] plays first, against [foes]. */
    private fun battleWith(w: JourneyWorld, foes: List<Foe>, actor: String = "hero"): Battle {
        val b = Journey.newBattle(w, 0, foes)
        val i = b.fighters.indexOfFirst { it.id == actor }
        return b.copy(queue = listOf(i) + b.queue.filter { it != i })
    }

    @Test
    fun aRegionOpensOnlyAfterTheBossBeforeIt() {
        val w = world()
        assertFalse(Journey.regionOpen(w, Region.RIVER))
        assertFalse(Journey.canVisit(w, Regions.id(Region.RIVER, 0)))
        assertEquals(w, Journey.showRegion(w, Region.RIVER))
        val done = w.copy(cleared = Region.FOREST.spots.indices.map { Regions.id(Region.FOREST, it) }.toSet())
        assertTrue(Journey.regionOpen(done, Region.RIVER))
        assertTrue(Journey.canVisit(done, Regions.id(Region.RIVER, 0)))
        assertEquals(Region.RIVER, Journey.showRegion(done, Region.RIVER).region)
        assertFalse(Journey.regionOpen(done, Region.HILLS))
    }

    @Test
    fun aChestScrollTeachesTheHeroASpell() {
        val river = Regions.id(Region.RIVER, 1)
        var w = world(HeroClass.KNIGHT).copy(cleared = Region.FOREST.spots.indices.map { Regions.id(Region.FOREST, it) }.toSet() + Regions.id(Region.RIVER, 0))
        w = Journey.visit(w, river)
        assertTrue(Spell.THORNS in w.hero!!.spells)
        assertTrue(river in w.cleared)
    }

    @Test
    fun poisonTakesLifeEachTurnButNeverAFriendsLastBit() {
        val w = world(HeroClass.ARCHER).copy(hero = world(HeroClass.ARCHER).hero!!.copy(spells = listOf(Spell.THORNS)))
        var b = battleWith(w, listOf(Foe.CRAB))
        val crab = b.fighters.indexOfFirst { it.foe == Foe.CRAB }
        b = Journey.act(b, Action(Action.Kind.SPELL, Spell.THORNS, crab))
        assertTrue(b.fighters[crab].has(Status.POISON))
        val before = b.fighters[crab].hp
        // Play on until the crab's turn has started: poison bit it.
        var turns = 0
        while (b.events.none { it.kind == EventKind.POISON } && turns++ < 10) b = if (b.fighters[b.current].ally) Journey.act(b, Action(Action.Kind.DEFEND)) else Journey.enemyTurn(b)
        assertTrue(b.fighters[crab].hp < before)
        // A poisoned friend keeps at least one point of life.
        val hero = b.fighters.indexOfFirst { it.id == "hero" }
        val weak = b.copy(fighters = b.fighters.mapIndexed { i, f -> if (i == hero) f.copy(hp = 1, effects = mapOf(Status.POISON to 3)) else f })
        var c = weak
        turns = 0
        while (c.fighters[hero].hp > 0 && c.events.none { it.kind == EventKind.POISON && it.target == hero } && turns++ < 10) c = if (c.fighters[c.current].ally) Journey.act(c, Action(Action.Kind.DEFEND)) else c.copy(queue = c.queue.drop(1).ifEmpty { listOf(hero) })
        assertTrue(c.fighters[hero].hp >= 1)
    }

    @Test
    fun aSleeperSkipsItsTurnUntilItIsHit() {
        val tito = Journey.start("Ana", HeroClass.MAGE).copy(party = listOf(Friend.TITO))
        var b = battleWith(tito, listOf(Foe.GHOST), actor = "TITO")
        val ghost = b.fighters.indexOfFirst { it.foe == Foe.GHOST }
        b = Journey.act(b, Action(Action.Kind.SPELL, Spell.SLEEP, ghost))
        assertTrue(b.fighters[ghost].has(Status.SLEEP))
        // Let everyone play a round: the ghost never attacks while asleep.
        repeat(4) { if (b.outcome == null) b = if (b.fighters[b.current].ally) Journey.act(b, Action(Action.Kind.DEFEND)) else Journey.enemyTurn(b) }
        assertTrue(b.events.none { it.kind == EventKind.DAMAGE && it.actor == ghost } || !b.fighters[ghost].has(Status.SLEEP))
        // A blow wakes it.
        val i = b.fighters.indexOfFirst { it.id == "hero" }
        val asleep = b.copy(fighters = b.fighters.mapIndexed { k, f -> if (k == ghost) f.copy(effects = mapOf(Status.SLEEP to 2)) else f }, queue = listOf(i))
        val hit = Journey.act(asleep, Action(Action.Kind.SPELL, Spell.SPARK, ghost))
        assertFalse(hit.fighters[ghost].has(Status.SLEEP))
        assertTrue(hit.events.any { it.kind == EventKind.WAKE })
    }

    @Test
    fun wetThenIceFreezesAndOilThenFireBurnsHard() {
        val mage = world(HeroClass.MAGE)
        var b = battleWith(mage, listOf(Foe.IMP, Foe.IMP))
        val imp = b.fighters.indexOfFirst { it.foe == Foe.IMP }
        val plain = Journey.act(b, Action(Action.Kind.SPELL, Spell.FROST, imp)).events.first { it.kind == EventKind.DAMAGE }.amount
        val wet = b.copy(fighters = b.fighters.mapIndexed { i, f -> if (i == imp) f.copy(effects = mapOf(Status.WET to 2)) else f })
        val frozen = Journey.act(wet, Action(Action.Kind.SPELL, Spell.FROST, imp))
        assertTrue(frozen.events.any { it.kind == EventKind.FROZEN })
        assertTrue(frozen.events.first { it.kind == EventKind.DAMAGE }.amount > plain)
        // Frozen: it sleeps, and loses its turn (at once, if it plays next).
        assertTrue(frozen.fighters[imp].has(Status.SLEEP) || frozen.events.any { it.kind == EventKind.ASLEEP && it.target == imp })
        // Fire on oil: twice the damage.
        b = battleWith(mage, listOf(Foe.SLIME))
        val slime = b.fighters.indexOfFirst { it.foe == Foe.SLIME }
        val dry = Journey.act(b, Action(Action.Kind.SPELL, Spell.SPARK, slime)).events.first { it.kind == EventKind.DAMAGE }.amount
        val oiled = b.copy(fighters = b.fighters.mapIndexed { i, f -> if (i == slime) f.copy(effects = mapOf(Status.OIL to 3)) else f })
        val burnt = Journey.act(oiled, Action(Action.Kind.SPELL, Spell.SPARK, slime))
        assertTrue(burnt.events.any { it.kind == EventKind.BURN })
        assertTrue(burnt.events.first { it.kind == EventKind.DAMAGE }.amount >= dry * 2 - 2)
    }

    @Test
    fun zecaStealsAndTheLootCountsAtTheEnd() {
        val w = Journey.start("Ana", HeroClass.KNIGHT).copy(party = listOf(Friend.ZECA))
        var stole = false
        for (seed in 1L..20L) {
            val b = battleWith(w.copy(seed = seed), listOf(Foe.GNOME), actor = "ZECA")
            val gnome = b.fighters.indexOfFirst { it.foe == Foe.GNOME }
            val after = Journey.act(b, Action(Action.Kind.SPELL, Spell.STEAL, gnome))
            assertTrue(after.events.any { it.kind == EventKind.STOLE || it.kind == EventKind.MISSED })
            if (after.loot > 0 || after.potions > b.potions || after.ethers > b.ethers) stole = true
        }
        assertTrue(stole)
    }

    @Test
    fun aFriendOnTheBenchCanSwapInAndStaysInTheGroup() {
        val w = Journey.start("Ana", HeroClass.KNIGHT).copy(party = listOf(Friend.BENTO, Friend.LIA, Friend.IARA), reserve = listOf(Friend.ZECA))
        var b = battleWith(w, listOf(Foe.SLIME))
        assertEquals(listOf(Friend.ZECA), Journey.swappable(b))
        val lia = b.fighters.indexOfFirst { it.id == "LIA" }
        b = Journey.act(b, Action(Action.Kind.SWAP, target = lia, friend = Friend.ZECA))
        assertEquals("ZECA", b.fighters[lia].id)
        assertEquals(listOf(Friend.LIA), Journey.swappable(b))
        // The hero can never be swapped out.
        assertFalse(Journey.valid(b, Action(Action.Kind.SWAP, target = 0, friend = Friend.LIA)))
        val won = w.copy(battle = b.copy(outcome = Outcome.WON, fighters = b.fighters.map { if (it.ally) it else it.copy(hp = 0) }))
        val after = Journey.finish(won)
        assertEquals(listOf(Friend.BENTO, Friend.ZECA, Friend.IARA), after.party)
        assertEquals(listOf(Friend.LIA), after.reserve)
    }

    @Test
    fun theTavernKeepsAtMostThreeTravelling() {
        var w = Journey.start("Ana", HeroClass.KNIGHT).copy(party = listOf(Friend.BENTO, Friend.LIA, Friend.IARA), reserve = listOf(Friend.ZECA))
        assertEquals(w, Journey.setTravelling(w, Friend.ZECA, true))
        w = Journey.setTravelling(w, Friend.LIA, false)
        w = Journey.setTravelling(w, Friend.ZECA, true)
        assertEquals(listOf(Friend.BENTO, Friend.IARA, Friend.ZECA), w.party)
        assertEquals(listOf(Friend.LIA), w.reserve)
    }

    @Test
    fun theShopSellsGearThatChangesTheHerosNumbers() {
        var w = world().copy(gold = 100)
        val atk = Journey.maxStats(w, "hero").atk
        assertEquals(w, Journey.buy(w, Gear.STEEL_SWORD)) // too dear
        w = Journey.buy(w, Gear.IRON_SWORD)
        assertEquals(100 - Gear.IRON_SWORD.price, w.gold)
        assertEquals(atk + Gear.IRON_SWORD.atk, Journey.maxStats(w, "hero").atk)
        w = Journey.buy(w, io.github.fershibli.megagames.games.journey.Item.POTION)
        assertEquals(4, w.potions)
        // Wearing another weapon replaces the first; both stay owned.
        w = w.copy(gold = 500)
        w = Journey.buy(w, Gear.STEEL_SWORD)
        assertEquals(setOf(Gear.STEEL_SWORD), w.worn)
        w = Journey.wear(w, Gear.IRON_SWORD)
        assertEquals(setOf(Gear.IRON_SWORD), w.worn)
        assertEquals(setOf(Gear.IRON_SWORD, Gear.STEEL_SWORD), w.owned)
    }

    @Test
    fun aRescueBringsTheRegionsFriendAndAFullGroupSendsThemToTheTavern() {
        val forest = Region.FOREST.spots.indices.map { Regions.id(Region.FOREST, it) }.toSet()
        val rescue = Regions.id(Region.RIVER, 4)
        var w = world().copy(party = listOf(Friend.BENTO, Friend.LIA, Friend.ZECA), cleared = forest + (0..3).map { Regions.id(Region.RIVER, it) })
        w = Journey.visit(w, rescue)
        val b = w.battle!!
        w = Journey.finish(w.copy(battle = b.copy(outcome = Outcome.WON, fighters = b.fighters.map { if (it.ally) it else it.copy(hp = 0) })))
        assertTrue(Friend.IARA in w.reserve)
    }

    @Test
    fun theWholeJourneyCanBePlayedFollowingTheBestMove() {
        // Easy and Medium: every class gets to the end, practising a little when needed.
        for (d in listOf(Difficulty.EASY, Difficulty.MEDIUM)) for (c in HeroClass.entries) {
            val result = wholeJourney(c, d)
            assertFalse("$d $c: $result", result.startsWith("STUCK"))
        }
        // Hard asks for more practice, but a knight still makes it.
        val hard = wholeJourney(HeroClass.KNIGHT, Difficulty.HARD)
        assertFalse(hard, hard.startsWith("STUCK"))
    }

    private fun wholeJourney(cls: HeroClass, d: Difficulty): String {
        var w = world(cls).copy(difficulty = d)
        var battles = 0
        for (region in Region.entries) {
            for (i in region.spots.indices) {
                val id = Regions.id(region, i)
                var tries = 0
                while (id !in w.cleared && tries++ < 40) {
                    // Rest and shop between fights, like a person would.
                    w = Journey.rest(w)
                    for (g in Gear.entries) if (w.gold >= g.price + 60) w = Journey.buy(w, g)
                    while (w.gold >= 80 && w.potions < 6) w = Journey.buy(w, io.github.fershibli.megagames.games.journey.Item.POTION)
                    w = Journey.visit(w, id)
                    val b = w.battle
                    if (b != null) {
                        battles++
                        w = w.copy(battle = play(b, w.known))
                        w = Journey.finish(w).let { it.copy(known = it.known + Foe.entries) }
                        // Not strong enough yet: practise on the battle before.
                        if (id !in w.cleared && i > 0) {
                            val practice = (0 until i).map { Regions.id(region, it) }.lastOrNull { Regions.spot(it).kind == SpotKind.BATTLE }
                            if (practice != null) {
                                w = Journey.visit(Journey.rest(w), practice)
                                w.battle?.let { pb -> w = Journey.finish(w.copy(battle = play(pb, w.known))) }
                            }
                        }
                    }
                    while (w.levelUps > 0) w = Journey.choose(w, Journey.choices(w).first)
                }
                if (id !in w.cleared) return "STUCK $region $i battles=$battles level=${w.hero!!.level}"
            }
            assertTrue(Journey.chapterDone(w, region))
        }
        // Every friend was met on the way.
        assertEquals(Friend.entries.toSet(), Journey.friends(w).toSet())
        return "battles=$battles level=${w.hero!!.level}"
    }
}
