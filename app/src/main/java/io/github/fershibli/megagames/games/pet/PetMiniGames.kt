package io.github.fershibli.megagames.games.pet

import androidx.annotation.StringRes
import io.github.fershibli.megagames.R
import kotlin.random.Random

/** Rock-paper-scissors. The pet picks at random, without looking at your hand. */
enum class Hand(@StringRes val label: Int) {
    ROCK(R.string.pet_rock),
    PAPER(R.string.pet_paper),
    SCISSORS(R.string.pet_scissors),
    ;

    /** Paper wraps rock, scissors cut paper, rock breaks scissors. */
    fun beats(other: Hand): Boolean = (ordinal - other.ordinal + 3) % 3 == 1
}

enum class RoundResult { WIN, LOSE, TIE }

object RockPaperScissors {
    fun petHand(random: Random): Hand = Hand.entries[random.nextInt(Hand.entries.size)]

    /** Result for you. */
    fun play(you: Hand, pet: Hand): RoundResult = when {
        you == pet -> RoundResult.TIE
        you.beats(pet) -> RoundResult.WIN
        else -> RoundResult.LOSE
    }
}

/**
 * Tic-tac-toe on a 3×3 board stored as 9 cells (row by row). You are [YOU] and always start; the
 * pet plays simply: it takes a win it sees most of the time, blocks you only sometimes, and
 * otherwise likes the middle and the corners. So you can win, and it still feels like a game.
 */
object TicTacToe {
    const val EMPTY = 0
    const val YOU = 1
    const val PET = 2

    val LINES: List<IntArray> = listOf(
        intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
    )

    fun empty(): List<Int> = List(9) { EMPTY }

    /** The completed line, if any. */
    fun winningLine(board: List<Int>): IntArray? =
        LINES.firstOrNull { (a, b, c) -> board[a] != EMPTY && board[a] == board[b] && board[b] == board[c] }

    /** [YOU], [PET] or [EMPTY] when nobody has three in a row. */
    fun winner(board: List<Int>): Int = winningLine(board)?.let { board[it[0]] } ?: EMPTY

    fun isFull(board: List<Int>): Boolean = board.none { it == EMPTY }

    fun isOver(board: List<Int>): Boolean = winner(board) != EMPTY || isFull(board)

    fun canPlay(board: List<Int>, cell: Int): Boolean = cell in 0..8 && board[cell] == EMPTY && !isOver(board)

    fun play(board: List<Int>, cell: Int, who: Int): List<Int> = board.toMutableList().also { it[cell] = who }

    /** A cell that would complete a line for [who], if there is one. */
    fun finishingCell(board: List<Int>, who: Int): Int? {
        for (line in LINES) {
            val mine = line.count { board[it] == who }
            val free = line.filter { board[it] == EMPTY }
            if (mine == 2 && free.size == 1) return free[0]
        }
        return null
    }

    /**
     * The pet's move. [winChance] and [blockChance] are how often it notices a winning move or
     * your threat; below 1 it sometimes lets you win.
     */
    fun petMove(board: List<Int>, random: Random, winChance: Double = 0.85, blockChance: Double = 0.6): Int {
        val free = (0..8).filter { board[it] == EMPTY }
        require(free.isNotEmpty() && !isOver(board)) { "no move to make" }
        finishingCell(board, PET)?.let { if (random.nextDouble() < winChance) return it }
        finishingCell(board, YOU)?.let { if (random.nextDouble() < blockChance) return it }
        if (board[4] == EMPTY && random.nextDouble() < 0.7) return 4
        val corners = listOf(0, 2, 6, 8).filter { board[it] == EMPTY }
        if (corners.isNotEmpty() && random.nextBoolean()) return corners[random.nextInt(corners.size)]
        return free[random.nextInt(free.size)]
    }
}
