package io.github.fershibli.megagames

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.TutorialStep
import io.github.fershibli.megagames.games.solitaire.solitaireTutorial
import io.github.fershibli.megagames.games.tutorialOf
import io.github.fershibli.megagames.ui.components.TutorialGuide
import io.github.fershibli.megagames.ui.screens.TutorialScreen
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The lessons played on a game's own board. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class TutorialTest {
    @get:Rule val rule = createComposeRule()

    /** The ring and the place being followed never stop, so the clock is moved by hand. */
    private fun advanceUntil(what: String, condition: () -> Boolean) {
        repeat(300) {
            if (condition()) return
            rule.mainClock.advanceTimeBy(100)
        }
        throw AssertionError("Never happened: $what")
    }

    private fun enabled(text: String) = rule.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theSolitaireLessonIsFollowedToTheEndByTouchingTheBoard() {
        rule.mainClock.autoAdvance = false
        val guide = TutorialGuide()
        val lesson = solitaireTutorial
        var exited = false
        rule.setContent {
            MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) {
                TutorialScreen(GameId.SOLITAIRE, lesson, onExit = { exited = true }, onRules = {}, onSettings = {}, guide = guide)
            }
        }
        lesson.steps.forEachIndexed { i, step ->
            val last = i == lesson.steps.lastIndex
            advanceUntil("step ${i + 1} on screen") { rule.onAllNodes(hasText("${i + 1} / ${lesson.steps.size}")).fetchSemanticsNodes().isNotEmpty() }
            if (step.kind == TutorialStep.Kind.TRY) {
                advanceUntil("step ${i + 1} waits for the player") { guide.expected == step.target }
                rule.onNodeWithText("Your turn").assertIsDisplayed()
                rule.onNodeWithText("Next").assertIsNotEnabled()
                val place = guide.rect(step.target!!)
                assertNotNull("step ${i + 1}: ${step.target} is on the board", place)
                rule.onRoot().performTouchInput { click(place!!.center) }
                advanceUntil("step ${i + 1} is played") { guide.done }
                advanceUntil("step ${i + 1} says well done") { rule.onAllNodes(hasText("Well done!")).fetchSemanticsNodes().isNotEmpty() }
            }
            // A "watch" step plays its move by itself, then lets the lesson go on.
            val button = if (last) "Done" else "Next"
            advanceUntil("step ${i + 1} lets the lesson go on") { enabled(button) }
            rule.onNodeWithText(button).performClick()
            rule.mainClock.advanceTimeBy(50)
        }
        assertTrue(exited)
    }

    @Test
    fun onlyThePlaceOfTheStepAnswersAndTheHandShowsItAfterTwoOtherTouches() {
        rule.mainClock.autoAdvance = false
        val guide = TutorialGuide()
        rule.setContent {
            MegaGamesTheme(Settings(language = AppLanguage.ENGLISH)) {
                TutorialScreen(GameId.SOLITAIRE, solitaireTutorial, onExit = {}, onRules = {}, onSettings = {}, guide = guide)
            }
        }
        // Step 1 is read, step 2 is watched, step 3 asks for the two of spades.
        rule.onNodeWithText("Next").performClick()
        advanceUntil("the ace is played") { enabled("Next") }
        rule.onNodeWithText("Next").performClick()
        advanceUntil("the two is asked") { guide.expected == "column:2:2" }
        val other = guide.rect("column:6:1")!!
        repeat(2) { rule.onRoot().performTouchInput { click(other.center) } }
        rule.mainClock.advanceTimeBy(200)
        assertEquals(2, guide.misses)
        assertEquals("column:2:2", guide.expected)
        // The buttons of the game wait too.
        rule.onNodeWithText("Hint").assertIsNotEnabled()
        rule.onNodeWithText("Undo").assertIsNotEnabled()
        rule.onNodeWithText("Next").assertIsNotEnabled()
    }

    @Test
    fun aGameWithoutALessonGoesToItsPageOfRules() {
        assertNotNull(tutorialOf(GameId.SOLITAIRE))
        assertEquals(null, tutorialOf(GameId.SUDOKU))
    }
}
