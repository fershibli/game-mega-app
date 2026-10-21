package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.snake.Snake
import io.github.fershibli.megagames.games.snake.SnakeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakeTest {
    /** A snake on a 10x10 board with its fruit far away (cell 99) unless told otherwise. */
    private fun snake(d: Difficulty, body: List<Int>, dir: Int = Snake.RIGHT, fruit: Int = 99) =
        SnakeState(d, 10, body, dir = dir, fruit = fruit, seed = 5)

    @Test
    fun levelsAreSlowAndGetALittleFaster() {
        assertTrue(Snake.stepMillis(Difficulty.EASY) > Snake.stepMillis(Difficulty.MEDIUM))
        assertTrue(Snake.stepMillis(Difficulty.MEDIUM) > Snake.stepMillis(Difficulty.HARD))
        assertTrue(Snake.stepMillis(Difficulty.HARD) >= 300)
        val s = Snake.newGame(Difficulty.MEDIUM, 3)
        assertEquals(Snake.START_LENGTH, s.length)
        assertTrue(s.fruit !in s.body)
    }

    @Test
    fun movesOneCellPerStepAndCannotTurnBack() {
        var s = snake(Difficulty.MEDIUM, listOf(44, 43, 42))
        s = Snake.step(s)
        assertEquals(listOf(45, 44, 43), s.body)
        s = Snake.turn(s, Snake.LEFT) // straight back into its neck: ignored
        s = Snake.step(s)
        assertEquals(46, s.body.first())
        s = Snake.step(Snake.turn(s, Snake.DOWN))
        assertEquals(56, s.body.first())
        // Two quick turns are both kept.
        s = Snake.turn(Snake.turn(s, Snake.RIGHT), Snake.UP)
        s = Snake.step(Snake.step(s))
        assertEquals(47, s.body.first())
    }

    @Test
    fun eatingFruitMakesItLongerAndBringsANewFruit() {
        var s = snake(Difficulty.EASY, listOf(44, 43, 42), fruit = 45)
        s = Snake.step(s)
        assertEquals(4, s.length)
        assertEquals(1, s.eaten)
        assertTrue(s.fruit >= 0 && s.fruit !in s.body)
        s = Snake.step(s)
        assertEquals(4, s.length) // grows once per fruit
    }

    @Test
    fun easyAndMediumPassThroughTheEdgesHardHasWalls() {
        for (d in listOf(Difficulty.EASY, Difficulty.MEDIUM)) {
            val s = Snake.step(snake(d, listOf(49, 48, 47)))
            assertEquals(40, s.body.first())
            assertFalse(s.over)
            val up = Snake.step(snake(d, listOf(4, 14, 24), dir = Snake.UP))
            assertEquals(94, up.body.first())
        }
        val hard = Snake.step(snake(Difficulty.HARD, listOf(49, 48, 47)))
        assertTrue(hard.over)
        assertEquals(listOf(49, 48, 47), hard.body)
    }

    @Test
    fun touchingItselfShortensOnEasyAndEndsOtherwise() {
        // A curled snake whose head (33) is about to go down into its own body at 43.
        val body = listOf(33, 34, 44, 43, 42, 41)
        val easy = Snake.step(snake(Difficulty.EASY, body, dir = Snake.DOWN))
        assertFalse(easy.over)
        assertEquals(listOf(43, 33, 34, 44), easy.body)
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) assertTrue(Snake.step(snake(d, body, dir = Snake.DOWN)).over)
        // Following its own tail is fine: the tail moves away on the same step.
        val ring = listOf(44, 45, 55, 54)
        assertFalse(Snake.step(snake(Difficulty.HARD, ring, dir = Snake.DOWN)).over)
    }

    @Test
    fun theSameSeedGivesTheSameGameAndItSurvivesASave() {
        fun play(seed: Long): SnakeState {
            var s = Snake.newGame(Difficulty.EASY, seed)
            repeat(60) { s = Snake.step(if (it % 7 == 0) Snake.turn(s, (it / 7) % 4) else s) }
            return s
        }
        assertEquals(play(9), play(9))
        val s = play(9)
        assertEquals(s, AppJson.decodeFromString(SnakeState.serializer(), AppJson.encodeToString(SnakeState.serializer(), s)))
    }
}
