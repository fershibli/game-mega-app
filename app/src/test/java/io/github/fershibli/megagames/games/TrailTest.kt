package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.trail.Square
import io.github.fershibli.megagames.games.trail.Story
import io.github.fershibli.megagames.games.trail.Trail
import io.github.fershibli.megagames.games.trail.TrailState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TrailTest {
    private val random = Random(7)

    @Test
    fun thePathSnakesUpTheBoardOneStagePerRow() {
        assertEquals(0 to 0, Trail.rowCol(0))
        assertEquals(0 to 7, Trail.rowCol(7))
        assertEquals(1 to 7, Trail.rowCol(8))
        assertEquals(1 to 0, Trail.rowCol(15))
        assertEquals(Square.START, Trail.square(0))
        assertEquals(Square.FINISH, Trail.square(Trail.FINISH))
        // Every stage has its own stories.
        assertTrue(Story.entries.groupBy { it.stage }.values.all { it.size == 4 })
    }

    @Test
    fun landingOnAHeartGivesOneAndTheTurnPasses() {
        var s = Trail.roll(Trail.newGame(2), 1, random)
        assertEquals(Square.HEART, Trail.square(1))
        assertEquals(1, s.hearts[0])
        assertFalse(Trail.canRoll(s))
        s = Trail.next(s)
        assertEquals(1, s.turn)
        assertTrue(Trail.canRoll(s))
    }

    @Test
    fun aStoryWaitsToBeReadThenGivesItsHearts() {
        var s = Trail.roll(Trail.newGame(2), 2, random)
        assertNotNull(s.pending)
        val pending = s.pending!!
        assertTrue(pending.story >= 0)
        assertEquals(s, Trail.next(s)) // the turn waits for the story
        val story = Story.entries[pending.story]
        s = Trail.readStory(s)
        assertEquals(story.hearts, s.hearts[0])
        assertEquals((2 + story.move).coerceAtLeast(0), s.positions[0])
        assertEquals(null, s.pending)
    }

    @Test
    fun atACrossroadsStayingGivesHeartsAndMovingOnGivesSquares() {
        val at = Trail.roll(Trail.newGame(2), 4, random)
        assertEquals(Square.CHOICE, Trail.square(4))
        val stay = Trail.choose(at, true)
        assertEquals(Trail.STAY_HEARTS, stay.hearts[0])
        assertEquals(4, stay.positions[0])
        val go = Trail.choose(at, false)
        assertEquals(0, go.hearts[0])
        assertEquals(4 + Trail.GO_SQUARES, go.positions[0])
    }

    @Test
    fun aHeartIsNewsUntilItsCardIsRead() {
        val s = Trail.roll(Trail.newGame(2), 1, random)
        assertTrue(Trail.hasNews(s))
        assertFalse(Trail.hasNews(Trail.see(s)))
        // A plain square has nothing to read; a story or a crossroads is read once answered.
        assertFalse(Trail.hasNews(Trail.roll(Trail.newGame(2), 3, random)))
        assertFalse(Trail.hasNews(Trail.readStory(Trail.roll(Trail.newGame(2), 2, random))))
        assertFalse(Trail.hasNews(Trail.choose(Trail.roll(Trail.newGame(2), 4, random), true)))
        // The next roll brings a new card.
        val again = Trail.roll(Trail.next(Trail.see(s)), 1, random)
        assertEquals(1, again.lastSeat)
        assertTrue(Trail.hasNews(again))
    }

    @Test
    fun theComputersAnswerWaitsOnTheCardUntilItIsMade() {
        val at = Trail.roll(Trail.newGame(2), 4, random)
        val decided = Trail.decide(at, false)
        assertEquals(false, decided.pending!!.stays)
        assertEquals(at.positions, decided.positions)
        assertEquals(4 + Trail.GO_SQUARES, Trail.choose(decided, decided.pending!!.stays!!).positions[0])
        // Only a crossroads takes an answer.
        val story = Trail.roll(Trail.newGame(2), 2, random)
        assertEquals(story, Trail.decide(story, true))
    }

    @Test
    fun aGameSavedBeforeTheCardsStillLoads() {
        val old = """{"seats":2,"positions":[4,0],"hearts":[1,0],"die":4,"pending":{"seat":0,"choiceStage":0}}"""
        val s = AppJson.decodeFromString(TrailState.serializer(), old)
        assertEquals(null, s.pending!!.stays)
        assertFalse(s.seen)
        assertEquals(Trail.STAY_HEARTS + 1, Trail.choose(s, true).hearts[0])
    }

    @Test
    fun arrivingEarnsABonusByOrderAndTheGameEndsWhenEveryoneIsHome() {
        var s = TrailState(2, listOf(Trail.FINISH - 2, Trail.FINISH - 1), listOf(0, 0))
        s = Trail.next(Trail.roll(s, 6, random))
        assertEquals(listOf(0), s.arrived)
        assertEquals(Trail.ARRIVAL_BONUS[0], s.hearts[0])
        s = Trail.roll(s, 1, random)
        assertEquals(Trail.ARRIVAL_BONUS[1], s.hearts[1])
        assertTrue(s.over)
        assertEquals(listOf(0), Trail.leaders(s))
    }

    @Test
    fun seatsAlreadyHomeAreSkipped() {
        val s = TrailState(3, listOf(Trail.FINISH, 10, 20), listOf(0, 0, 0), turn = 2, die = 3, arrived = listOf(0))
        assertEquals(1, Trail.next(s).turn)
    }

    @Test
    fun aWholeGameAgainstTheComputerAlwaysEnds() {
        repeat(30) { g ->
            val r = Random(g)
            var s = Trail.newGame(2 + g % 3)
            var steps = 0
            while (!s.over && steps++ < 2_000) {
                s = when {
                    s.pending?.story?.let { it >= 0 } == true -> Trail.readStory(s)
                    s.pending != null -> Trail.choose(s, Trail.computerStays(s, Difficulty.HARD, r))
                    s.die != 0 -> Trail.next(s)
                    else -> Trail.roll(s, r.nextInt(1, 7), r)
                }
            }
            assertTrue(s.over)
            assertEquals(s.seats, s.arrived.toSet().size)
            assertTrue(s.hearts.all { it >= 0 })
        }
    }
}
