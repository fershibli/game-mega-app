package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.sequence.Sequence
import io.github.fershibli.megagames.games.sequence.SequencePhase
import io.github.fershibli.megagames.games.sequence.SequenceRecords
import io.github.fershibli.megagames.games.sequence.SequenceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SequenceTest {
    @Test
    fun goalsAndPaceDifferByLevel() {
        assertEquals(listOf(5, 8, 12), Difficulty.entries.map { Sequence.newGame(it, Random(1)).goal })
        assertTrue(Sequence.pace(Difficulty.EASY).lit > Sequence.pace(Difficulty.MEDIUM).lit)
        assertTrue(Sequence.pace(Difficulty.MEDIUM).lit > Sequence.pace(Difficulty.HARD).lit)
    }

    @Test
    fun neverThreeOfTheSameColorInARow() {
        for (seed in 1..200) {
            val seq = Sequence.newGame(Difficulty.HARD, Random(seed)).sequence
            assertTrue(seq.all { it in 0 until Sequence.PADS })
            assertTrue(seq.windowed(3).none { it.toSet().size == 1 })
        }
    }

    /** Plays [s] perfectly for the current round. */
    private fun repeatRound(s: SequenceState, d: Difficulty): SequenceState {
        var cur = Sequence.shown(s)
        assertEquals(SequencePhase.INPUT, cur.phase)
        for (k in 0 until cur.round) cur = Sequence.press(cur, cur.sequence[k], d)
        return cur
    }

    @Test
    fun repeatingEveryRoundWins() {
        for (d in Difficulty.entries) {
            var s = Sequence.start(Sequence.newGame(d, Random(4)))
            while (s.phase != SequencePhase.DONE) s = repeatRound(s, d)
            assertTrue(s.won)
            assertEquals(s.goal, s.round)
            assertEquals(s.goal, s.completed)
        }
    }

    @Test
    fun tapsAreIgnoredWhileWatching() {
        val s = Sequence.start(Sequence.newGame(Difficulty.EASY, Random(2)))
        assertEquals(SequencePhase.WATCH, s.phase)
        assertEquals(s, Sequence.press(s, 0, Difficulty.EASY))
    }

    @Test
    fun onEasyAMistakeShowsTheSameColorsAgain() {
        var s = Sequence.start(SequenceState(listOf(0, 1, 2, 3, 0)))
        s = repeatRound(s, Difficulty.EASY) // round 1 done
        assertEquals(2, s.round)
        s = Sequence.shown(s)
        val shows = s.shows
        s = Sequence.press(s, 3, Difficulty.EASY) // wrong: should be 0
        assertEquals(SequencePhase.WATCH, s.phase)
        assertTrue(s.retry)
        assertEquals(2, s.round)
        assertEquals(shows + 1, s.shows)
        assertEquals(1, s.completed)
        // Watch again on request keeps the round too.
        s = Sequence.watchAgain(Sequence.shown(s))
        assertEquals(SequencePhase.WATCH, s.phase)
        assertEquals(2, s.round)
    }

    @Test
    fun onMediumAndHardAMistakeEndsTheGame() {
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            var s = Sequence.start(SequenceState(listOf(0, 1, 2, 3, 0, 1, 2, 3)))
            s = repeatRound(repeatRound(s, d), d)
            s = Sequence.press(Sequence.shown(s), 2, d) // wrong: should be 0
            assertEquals(SequencePhase.DONE, s.phase)
            assertFalse(s.won)
            assertEquals(2, s.completed)
        }
    }

    @Test
    fun recordsKeepTheLongestSequencePerLevel() {
        val r = SequenceRecords().with(Difficulty.MEDIUM, 6).with(Difficulty.MEDIUM, 4)
        assertEquals(6, r.of(Difficulty.MEDIUM))
        assertEquals(0, r.of(Difficulty.HARD))
    }
}
