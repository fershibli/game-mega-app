package io.github.fershibli.megagames

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
import io.github.fershibli.megagames.games.journey.HeroClass
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
        rule.onNodeWithTag("journey-status").assertExists()
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
}
