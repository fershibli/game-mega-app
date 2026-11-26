package io.github.fershibli.megagames

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.journey.Friend
import io.github.fershibli.megagames.games.journey.Gear
import io.github.fershibli.megagames.games.journey.HeroClass
import io.github.fershibli.megagames.games.journey.Region
import io.github.fershibli.megagames.games.journey.Regions
import io.github.fershibli.megagames.games.journey.JOURNEY_WORLD_KEY
import io.github.fershibli.megagames.games.journey.Journey
import io.github.fershibli.megagames.games.journey.JourneyWorld
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The journey like a person plays it: make a hero, go to the first spot, win the battle, and it is all kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class JourneyUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var store: WorldStore

    @Before fun clean() {
        store = WorldStore(app)
        store.save(JOURNEY_WORLD_KEY, JourneyWorld.serializer(), JourneyWorld())
        store.flush()
    }

    private fun show() {
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.JOURNEY, GameHost(GameId.JOURNEY, Difficulty.EASY, null, null) {}) } }
        rule.waitForIdle()
        listen()
    }

    /** Goes through the scenes of the story on screen, if any; returns who spoke. */
    private fun listen(): List<String> {
        val who = ArrayList<String>()
        while (rule.onAllNodes(hasTestTag("journey-scene-next")).fetchSemanticsNodes().isNotEmpty()) {
            who += rule.onNodeWithTag("journey-scene-who", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
            rule.onNodeWithTag("journey-scene-next").performClick()
            rule.waitForIdle()
        }
        return who
    }

    private fun saved(): JourneyWorld {
        store.flush()
        return store.load(JOURNEY_WORLD_KEY, JourneyWorld.serializer())!!
    }

    @Test
    fun makeAHeroWinTheFirstBattleAndItIsKept() {
        show()
        rule.onNodeWithTag("journey-class-mage").performClick()
        rule.onNodeWithTag("journey-name").performTextReplacement("Rosa")
        rule.onNodeWithTag("journey-start").performClick()
        rule.waitForIdle()
        // The Queen calls for help (Bento answers), then the Saci tells about the forest.
        assertEquals(listOf("Queen Clara", "Queen Clara", "Bento", "Saci"), listen())
        assertTrue(saved().seen.containsAll(listOf("prologue", "arrive_forest")))
        rule.onNodeWithTag("journey-status").assertExists()
        rule.onNodeWithText("Lights: 0/5", substring = true).assertExists()
        assertEquals("Rosa", saved().hero!!.name)
        assertEquals(HeroClass.MAGE, saved().hero!!.heroClass)
        // The first spot: a battle with two slimes. Let the group play by itself.
        rule.onNodeWithTag("journey-spot-0").performClick()
        rule.waitForIdle()
        assertNotNull(saved().battle)
        rule.onNodeWithTag("journey-auto").performClick()
        rule.mainClock.autoAdvance = false
        var ms = 0L
        while (ms < 120_000L && rule.onAllNodes(hasText("Victory", substring = true)).fetchSemanticsNodes().isEmpty()) {
            rule.mainClock.advanceTimeBy(500)
            ms += 500
        }
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("journey-ok").performClick()
        rule.waitForIdle()
        val w = saved()
        assertTrue(0 in w.cleared)
        assertEquals(2, w.hero!!.level)
        // The new level waits for a choice.
        rule.onNodeWithTag("journey-choice-0").performClick()
        rule.waitForIdle()
        assertEquals(0, saved().levelUps)
    }

    @Test
    fun theInnRestsTheGroupAndTheLevelOfTheEnemiesCanChange() {
        val tired = Journey.start("Rosa", HeroClass.KNIGHT).let { it.copy(hp = mapOf("hero" to 5)) }
        store.save(JOURNEY_WORLD_KEY, JourneyWorld.serializer(), tired)
        store.flush()
        show()
        rule.onNodeWithTag("journey-to-village").performClick()
        rule.onNodeWithTag("journey-inn").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Everyone rested", substring = true).assertExists()
        rule.onNodeWithTag("journey-message-ok").performClick()
        rule.onNodeWithTag("journey-level-hard").performClick()
        rule.waitForIdle()
        val w = saved()
        assertEquals(Journey.maxStats(w, "hero").hp, Journey.hp(w, "hero"))
        assertEquals(Difficulty.HARD, w.difficulty)
    }

    private fun seed(w: JourneyWorld) {
        store.save(JOURNEY_WORLD_KEY, JourneyWorld.serializer(), w)
        store.flush()
    }

    @Test
    fun theShopSellsASwordAndTheTavernSwapsFriends() {
        seed(Journey.start("Rosa", HeroClass.KNIGHT).copy(gold = 200, party = listOf(Friend.BENTO, Friend.LIA, Friend.IARA), reserve = listOf(Friend.ZECA)))
        show()
        rule.onNodeWithTag("journey-to-village").performClick()
        rule.onNodeWithTag("journey-shop").performClick()
        rule.onNodeWithTag("journey-gear-iron_sword").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("journey-message-ok").performClick()
        var w = saved()
        assertTrue(Gear.IRON_SWORD in w.worn)
        assertEquals(200 - Gear.IRON_SWORD.price, w.gold)
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithTag("journey-tavern").performClick()
        // The group is full: Lia stays behind so Zeca can come.
        rule.onNodeWithTag("journey-leave-lia").performClick()
        rule.onNodeWithTag("journey-take-zeca").performClick()
        rule.waitForIdle()
        w = saved()
        assertEquals(listOf(Friend.BENTO, Friend.IARA, Friend.ZECA), w.party)
        assertEquals(listOf(Friend.LIA), w.reserve)
    }

    @Test
    fun theNextRegionStaysClosedBeforeTheForestBoss() {
        seed(Journey.start("Rosa", HeroClass.ARCHER))
        show()
        rule.onNodeWithTag("journey-region-next").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Not yet", substring = true).assertExists()
        rule.onNodeWithTag("journey-message-ok").performClick()
        assertEquals(Region.FOREST, saved().region)
    }

    @Test
    fun withTheForestDoneTheRiverOpens() {
        val forest = Region.FOREST.spots.indices.map { Regions.id(Region.FOREST, it) }.toSet()
        seed(Journey.start("Rosa", HeroClass.ARCHER).copy(cleared = forest))
        show()
        rule.onNodeWithTag("journey-region-next").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("River").assertExists()
        assertEquals(Region.RIVER, saved().region)
        // The boto tells about the river the first time only.
        assertEquals(listOf("Boto"), listen())
        rule.onNodeWithTag("journey-region-prev").performClick()
        rule.onNodeWithTag("journey-region-next").performClick()
        rule.waitForIdle()
        assertEquals(emptyList<String>(), listen())
        rule.onNodeWithText("Lights: 1/5", substring = true).assertExists()
    }

    @Test
    fun withTheFiveLightsBackTheQueenTellsTheEnd() {
        val all = Region.entries.flatMap { r -> r.spots.indices.map { Regions.id(r, it) } }.toSet()
        seed(Journey.start("Rosa", HeroClass.HEALER).copy(cleared = all, seen = setOf("prologue", "arrive_forest")))
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.JOURNEY, GameHost(GameId.JOURNEY, Difficulty.EASY, null, null) {}) } }
        rule.waitForIdle()
        assertEquals(listOf("Queen Clara", "Saci", "Rosa"), listen())
        assertTrue("epilogue" in saved().seen)
        rule.onNodeWithText("Lights: 5/5", substring = true).assertExists()
    }
}
