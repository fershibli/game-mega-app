package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.games.pet.Hand
import io.github.fershibli.megagames.games.pet.RockPaperScissors
import io.github.fershibli.megagames.games.pet.RoundResult
import io.github.fershibli.megagames.games.pet.TicTacToe
import io.github.fershibli.megagames.games.pet.TicTacToe.EMPTY
import io.github.fershibli.megagames.games.pet.TicTacToe.PET
import io.github.fershibli.megagames.games.pet.TicTacToe.YOU
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PetMiniGamesTest {
    @Test
    fun rockPaperScissorsRules() {
        assertTrue(Hand.PAPER.beats(Hand.ROCK))
        assertTrue(Hand.SCISSORS.beats(Hand.PAPER))
        assertTrue(Hand.ROCK.beats(Hand.SCISSORS))
        for (a in Hand.entries) {
            assertFalse(a.beats(a))
            assertEquals(RoundResult.TIE, RockPaperScissors.play(a, a))
            for (b in Hand.entries) if (a != b) assertTrue("exactly one of $a, $b wins", a.beats(b) xor b.beats(a))
        }
        assertEquals(RoundResult.WIN, RockPaperScissors.play(Hand.ROCK, Hand.SCISSORS))
        assertEquals(RoundResult.LOSE, RockPaperScissors.play(Hand.ROCK, Hand.PAPER))
    }

    @Test
    fun thePetPicksEveryHandFairly() {
        val random = Random(3)
        val counts = IntArray(3)
        repeat(3000) { counts[RockPaperScissors.petHand(random).ordinal]++ }
        for (c in counts) assertTrue("each hand about a third of the time: ${counts.toList()}", c in 850..1150)
    }

    private fun board(s: String) = s.filter { it in "XO." }.map { when (it) { 'X' -> YOU; 'O' -> PET; else -> EMPTY } }

    @Test
    fun ticTacToeFindsWinsAndDraws() {
        assertEquals(EMPTY, TicTacToe.winner(TicTacToe.empty()))
        assertFalse(TicTacToe.isOver(TicTacToe.empty()))
        assertEquals(YOU, TicTacToe.winner(board("XXX OO. ...")))
        assertEquals(PET, TicTacToe.winner(board("X.O XO. O.X")))
        assertEquals(YOU, TicTacToe.winner(board("X.O .XO ..X")))
        assertEquals(listOf(2, 4, 6), TicTacToe.winningLine(board("X.O XO. O.X"))?.toList())
        val draw = board("XOX XOO OXX")
        assertEquals(EMPTY, TicTacToe.winner(draw))
        assertTrue(TicTacToe.isFull(draw))
        assertTrue(TicTacToe.isOver(draw))
        assertFalse(TicTacToe.canPlay(board("XXX OO. ..."), 8)) // the game is over
        assertFalse(TicTacToe.canPlay(TicTacToe.play(TicTacToe.empty(), 4, YOU), 4)) // taken
        assertTrue(TicTacToe.canPlay(TicTacToe.play(TicTacToe.empty(), 4, YOU), 0))
    }

    @Test
    fun thePetTakesItsWinAndBlocksWhenAttentive() {
        // O can win at 2; X threatens at 8.
        val b = board("OO. .X. X.X")
        assertEquals(2, TicTacToe.finishingCell(b, PET))
        assertEquals(2, TicTacToe.petMove(b, Random(1), winChance = 1.0, blockChance = 1.0))
        val threat = board("X.. .X. O..")
        assertNull(TicTacToe.finishingCell(threat, PET))
        assertEquals(8, TicTacToe.petMove(threat, Random(1), winChance = 1.0, blockChance = 1.0))
    }

    @Test
    fun thePetSometimesLetsYouWin() {
        val threat = board("X.. .X. O..")
        val moves = (0 until 200).map { TicTacToe.petMove(threat, Random(it)) }
        assertTrue("usually blocks", moves.count { it == 8 } > 80)
        assertTrue("but not always", moves.count { it != 8 } > 30)
    }

    @Test
    fun wholeGamesAlwaysEndWithLegalMoves() {
        val random = Random(42)
        var youWon = 0
        repeat(300) {
            var b = TicTacToe.empty()
            var you = true
            while (!TicTacToe.isOver(b)) {
                val cell = if (you) {
                    // A simple player: win if possible, else block, else random.
                    TicTacToe.finishingCell(b, YOU) ?: TicTacToe.finishingCell(b, PET) ?: (0..8).filter { b[it] == EMPTY }.random(random)
                } else {
                    TicTacToe.petMove(b, random)
                }
                assertTrue(TicTacToe.canPlay(b, cell))
                b = TicTacToe.play(b, cell, if (you) YOU else PET)
                you = !you
            }
            if (TicTacToe.winner(b) == YOU) youWon++
        }
        assertTrue("a casual player wins quite often: $youWon/300", youWon > 60)
    }
}
