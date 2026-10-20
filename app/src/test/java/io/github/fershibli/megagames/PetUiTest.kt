package io.github.fershibli.megagames

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.geometry.Offset
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.WorldStore
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.games.pet.LocalPetClock
import io.github.fershibli.megagames.games.pet.Need
import io.github.fershibli.megagames.games.pet.PET_H
import io.github.fershibli.megagames.games.pet.PET_WORLD_KEY
import io.github.fershibli.megagames.games.pet.PetClock
import io.github.fershibli.megagames.games.pet.PetRules
import io.github.fershibli.megagames.games.pet.PetWorld
import io.github.fershibli.megagames.games.pet.Species
import io.github.fershibli.megagames.games.pet.petPlacement
import io.github.fershibli.megagames.ui.screens.GameScreen
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The pet like a person uses it: adopt, feed, stroke with a finger, and it is still there later. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class PetUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()
    private val hour = 3_600_000L

    /** A clock the test moves by hand. */
    private class FakeClock(var time: Long) : PetClock {
        override fun now() = time
    }

    private val clock = FakeClock(1_800_000_000_000L)

    private fun show() {
        rule.setContent {
            CompositionLocalProvider(LocalPetClock provides clock) {
                MegaGamesTheme(Settings()) { GameContent(GameId.PET, GameHost(GameId.PET, Difficulty.EASY, null, null) {}) }
            }
        }
        rule.waitForIdle()
    }

    private fun seed(world: PetWorld) = WorldStore(app).run {
        save(PET_WORLD_KEY, PetWorld.serializer(), world)
        flush()
    }

    private fun stored(): PetWorld? {
        val store = WorldStore(app)
        store.flush()
        return store.load(PET_WORLD_KEY, PetWorld.serializer())
    }

    /** The percentage read out for a bar, e.g. "Food: 45%" → 45. */
    private fun bar(need: Need): Int {
        val node: SemanticsNodeInteraction = rule.onNodeWithTag("pet-need-${need.name.lowercase()}")
        val description = node.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        return Regex("""(\d+)\s*%""").find(description)!!.groupValues[1].toInt()
    }

    @Test
    fun firstVisitAdoptsAFriendWithASuggestedOrTypedName() {
        show()
        rule.onNodeWithTag("pet-species-dog").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Biscuit").performClick()
        rule.onNodeWithTag("pet-name-field").assertTextContains("Biscuit").performTextReplacement("Rex")
        rule.onNodeWithText("Adopt").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Rex is happy to see you!").assertIsDisplayed()
        val world = stored()
        assertEquals("dog", world?.current?.species)
        assertEquals("Rex", world?.current?.name)
    }

    @Test
    fun feedingAndStrokingRaiseTheBarsAndAreSaved() {
        seed(PetRules.adopt(PetWorld(), Species.CAT, "Sunny", clock.time))
        clock.time += 10 * hour // food is down to the floor, happiness lower too
        show()
        assertEquals(20, bar(Need.FULLNESS))
        rule.onNodeWithText("Sunny is a little hungry.").assertIsDisplayed()

        rule.onNodeWithText("Feed").performClick()
        rule.onNodeWithText("Apple").performClick()
        rule.waitForIdle()
        assertEquals(45, bar(Need.FULLNESS))
        rule.onAllNodesWithContentDescription("Food: 45%").onFirst().assertIsDisplayed()

        // Stroke the pet back and forth with a finger: every stroke is a cuddle.
        val before = bar(Need.HAPPINESS)
        rule.onNodeWithTag("pet-scene").performTouchInput {
            val place = petPlacement(width.toFloat(), height.toFloat())
            val y = place.top + PET_H * place.px / 2
            val left = Offset(width / 2f - place.px * 6, y)
            val right = Offset(width / 2f + place.px * 6, y)
            down(left)
            repeat(4) {
                moveTo(right)
                moveTo(left)
            }
            up()
        }
        rule.waitForIdle()
        val after = bar(Need.HAPPINESS)
        assertTrue("happiness should go up: $before → $after", after >= before + 6)

        // Everything is on the phone already: a later visit finds the same pet and bars.
        val saved = stored()?.current
        assertNotNull(saved)
        assertEquals(45, PetRules.level(saved!!, Need.FULLNESS, clock.time))
        assertEquals(after, PetRules.level(saved, Need.HAPPINESS, clock.time))
    }

    @Test
    fun sleepingDimsTheRoomAndBringsEnergyBack() {
        seed(PetRules.adopt(PetWorld(), Species.PANDA, "Mochi", clock.time - 100 * hour))
        show()
        assertEquals(20, bar(Need.ENERGY))
        rule.onNodeWithText("Sleep").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Shh… Mochi is sleeping.").assertIsDisplayed()
        clock.time += 60_000
        rule.onNodeWithText("Wake up").performClick()
        rule.waitForIdle()
        assertEquals(60, bar(Need.ENERGY))
        assertEquals(false, stored()?.current?.asleep)
    }

    @Test
    fun theSettingsNewGameKeepsThePet() {
        seed(PetRules.adopt(PetWorld(), Species.FROG, "Pip", clock.time))
        val saves = SaveStore(app).apply { clear() }
        rule.setContent {
            CompositionLocalProvider(LocalPetClock provides clock) {
                GameScreen(
                    game = GameId.PET, difficulty = Difficulty.EASY, saved = null, saveStore = saves, settings = Settings(),
                    onSettingsChange = {}, askResume = false, onResumeAnswered = {}, onExit = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Kiss").performClick()
        rule.onAllNodesWithContentDescription("Settings").onFirst().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("New game").onFirst().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("New game").onLast().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Pip is happy to see you!").assertIsDisplayed()
        assertEquals(1, stored()?.pets?.size)
    }

    @Test
    fun adoptingAnotherFriendAsksFirstAndKeepsBoth() {
        seed(PetRules.adopt(PetWorld(), Species.CAT, "Sunny", clock.time))
        show()
        rule.onNodeWithText("Friends and adopt").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("pet-species-turtle").performScrollTo().performClick()
        rule.onNodeWithText("Daisy").performClick()
        rule.onNodeWithText("Adopt").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Change friend?").assertIsDisplayed()
        rule.onNodeWithText("Yes, change").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Daisy is happy to see you!").assertIsDisplayed()
        val world = stored()!!
        assertEquals(listOf("cat", "turtle"), world.pets.map { it.species })
        assertEquals("turtle", world.visiting)
    }

    @Test
    fun ticTacToeAndRockPaperScissorsArePlayable() {
        seed(PetRules.adopt(PetWorld(), Species.HAMSTER, "Coco", clock.time))
        show()
        rule.onNodeWithText("Play").performClick()
        rule.onNodeWithText("Rock, paper, scissors").performClick()
        rule.onNodeWithText("Pick one!", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Rock").performClick()
        // The pet shakes its hand for a moment, then shows it.
        val results = listOf("You win!", "Coco wins!", "It’s a tie!")
        rule.waitUntil(10_000) { results.any { rule.onAllNodes(hasText(it, substring = true)).fetchSemanticsNodes().isNotEmpty() } }
        rule.onNodeWithText("Back").performClick()

        rule.onNodeWithText("Play").performClick()
        rule.onNodeWithText("Tic-tac-toe").performClick()
        rule.onNodeWithText("Your turn").assertIsDisplayed()
        rule.onNodeWithTag("pet-board").performTouchInput { click(Offset(width / 2f, height / 2f)) }
        rule.onNodeWithText("Coco is thinking…").assertIsDisplayed()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Your turn").fetchSemanticsNodes().isNotEmpty() }
    }
}
