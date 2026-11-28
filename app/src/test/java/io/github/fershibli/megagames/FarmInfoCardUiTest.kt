package io.github.fershibli.megagames

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.farm.FARM_WORLD_KEY
import io.github.fershibli.megagames.games.farm.FarmClock
import io.github.fershibli.megagames.games.farm.FarmRules
import io.github.fershibli.megagames.games.farm.FarmWorld
import io.github.fershibli.megagames.games.farm.LocalFarmClock
import io.github.fershibli.megagames.games.farm.Pen
import io.github.fershibli.megagames.games.farm.Plot
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What the farm says shows on a card over the map: it never stretches the layout and it goes away. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class FarmInfoCardUiTest {
    @get:Rule val rule = createComposeRule()
    private val minute = 60_000L

    private val clock = object : FarmClock {
        override fun now() = 1_800_000_000_000L
    }

    private fun show() {
        val t = clock.now()
        WorldStore(RuntimeEnvironment.getApplication()).run {
            save(
                FARM_WORLD_KEY, FarmWorld.serializer(),
                FarmWorld(plots = List(FarmRules.START_PLOTS) { if (it < 2) Plot("tomato", t - minute) else Plot() }, pens = listOf(Pen("cow", t - minute))),
            )
            flush()
        }
        rule.setContent {
            CompositionLocalProvider(LocalFarmClock provides clock) {
                MegaGamesTheme(Settings()) { GameContent(GameId.FARM, GameHost(GameId.FARM, Difficulty.EASY, null, null) {}) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodes(hasTestTag("farm-plot-0")).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    private fun cardShown() = rule.onAllNodes(hasTestTag("farm-note")).fetchSemanticsNodes().isNotEmpty()
    private fun pillHeight() = rule.onNodeWithTag("farm-status").fetchSemanticsNode().boundsInRoot.height
    private fun scene() = rule.onNodeWithTag("farm-scene").fetchSemanticsNode().boundsInRoot

    @Test
    fun aGrowingBedSaysItOnACardOverTheMapWithoutStretchingTheStatus() {
        show()
        val before = pillHeight()
        val sceneBefore = scene()
        rule.onNodeWithTag("farm-plot-0").performClick()
        rule.waitForIdle()
        assertTrue(cardShown())
        assertEquals(before, pillHeight(), 0.5f)
        assertEquals(sceneBefore, scene())
        val card = rule.onNodeWithTag("farm-note").fetchSemanticsNode().boundsInRoot
        // Near the top of the map and centred.
        assertTrue(card.top >= sceneBefore.top && card.top - sceneBefore.top < 40f)
        assertEquals(sceneBefore.center.x, card.center.x, 1f)
        rule.onNode(hasText("Tomato", substring = true) and hasAnyAncestor(hasTestTag("farm-note"))).assertExists()
    }

    @Test
    fun theCardGoesAwayByItselfAfterAboutFiveSeconds() {
        show()
        rule.onNodeWithTag("farm-plot-0").performClick()
        rule.waitForIdle()
        assertTrue(cardShown())
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        assertTrue(cardShown())
        rule.mainClock.advanceTimeBy(30_000)
        rule.waitForIdle()
        assertTrue(!cardShown())
    }

    @Test
    fun tappingTheCardDismissesIt() {
        show()
        rule.onNodeWithTag("farm-plot-0").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("farm-note").performClick()
        rule.waitForIdle()
        assertTrue(!cardShown())
    }

    @Test
    fun aSecondMessageReplacesTheFirstAndRestartsTheTimer() {
        show()
        rule.onNodeWithTag("farm-plot-0").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithTag("farm-pen-0").performClick()
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodes(hasTestTag("farm-note")).fetchSemanticsNodes().size)
        rule.onNode(hasText("Cow", substring = true) and hasAnyAncestor(hasTestTag("farm-note"))).assertExists()
        // The first timer would have ended by now; the second one has not.
        rule.mainClock.advanceTimeBy(3_000)
        rule.waitForIdle()
        assertTrue(cardShown())
        rule.mainClock.advanceTimeBy(30_000)
        rule.waitForIdle()
        assertTrue(!cardShown())
    }
}
