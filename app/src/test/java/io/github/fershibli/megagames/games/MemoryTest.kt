package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cards.Suit
import io.github.fershibli.megagames.games.memory.Memory
import io.github.fershibli.megagames.games.memory.MemoryRecords
import io.github.fershibli.megagames.games.memory.MemoryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MemoryTest {
    private val variants = listOf(Memory.CARDS, Memory.FRUITS, Memory.SYMBOLS, Memory.ANIMALS)

    @Test
    fun everyLevelDealsDistinctPairs() {
        val sizes = mapOf(Difficulty.EASY to 12, Difficulty.MEDIUM to 16, Difficulty.HARD to 30)
        for (v in variants) for ((d, n) in sizes) {
            val s = Memory.newGame(v, d, Random(1))
            assertEquals(n, s.faces.size)
            assertEquals(s.rows * s.cols, n)
            assertEquals(n, s.faces.toSet().size)
            val pairs = s.faces.groupingBy { Memory.pairOf(it) }.eachCount()
            assertEquals(n / 2, pairs.size)
            assertTrue(pairs.values.all { it == 2 })
            assertTrue(pairs.keys.all { it < Memory.kinds(v) })
        }
    }

    @Test
    fun playingCardPairsShareRankAndColorButNotSuit() {
        for (kind in 0 until Memory.kinds(Memory.CARDS)) {
            val a = Memory.card(kind * 2)
            val b = Memory.card(kind * 2 + 1)
            assertEquals(a.rank, b.rank)
            assertEquals(a.suit.red, b.suit.red)
            assertTrue(a.suit != b.suit)
        }
        assertEquals(Suit.HEARTS, Memory.card(0).suit)
        assertEquals(26, (0 until 52).map { Memory.card(it).let { c -> c.rank to c.suit.red } }.toSet().size)
    }

    private fun game(vararg faces: Int) = MemoryState(Memory.SYMBOLS, 1, faces.size, faces.toList(), List(faces.size) { false })

    @Test
    fun aMatchStaysUpAndAMissWaitsForTheNextTap() {
        // Positions: 0 and 2 are a pair, 1 and 3 are a pair.
        var s = game(0, 2, 1, 3)
        s = Memory.tap(s, 0)!!
        assertEquals(listOf(0), s.open)
        assertNull(Memory.tap(s, 0)) // the same card again does nothing
        s = Memory.tap(s, 1)!!
        assertTrue(s.missShowing)
        assertEquals(1, s.moves)
        // The miss stays until the next tap, which also turns the tapped card up.
        s = Memory.tap(s, 3)!!
        assertEquals(listOf(3), s.open)
        s = Memory.tap(s, 1)!!
        assertTrue(s.justMatched)
        assertEquals(2, s.moves)
        assertEquals(1, s.found)
        assertNull(Memory.tap(s, 1)) // found cards cannot be turned again
        s = Memory.tap(Memory.tap(s, 0)!!, 2)!!
        assertTrue(s.won)
        assertEquals(3, s.moves)
    }

    @Test
    fun tappingACardOfTheMissJustTurnsThemBack() {
        var s = game(0, 2, 1, 3)
        s = Memory.tap(Memory.tap(s, 0)!!, 1)!!
        s = Memory.tap(s, 1)!!
        assertTrue(s.open.isEmpty())
        assertFalse(s.won)
    }

    @Test
    fun recordsKeepTheFewestMovesPerVariantAndLevel() {
        var r = MemoryRecords()
        val (r1, first) = r.with(Memory.FRUITS, Difficulty.EASY, 12)
        assertTrue(first)
        r = r1
        assertFalse(r.with(Memory.FRUITS, Difficulty.EASY, 14).second)
        val (r2, better) = r.with(Memory.FRUITS, Difficulty.EASY, 9)
        assertTrue(better)
        assertEquals(9, r2.of(Memory.FRUITS, Difficulty.EASY))
        assertNull(r2.of(Memory.FRUITS, Difficulty.HARD))
        assertNull(r2.of(Memory.ANIMALS, Difficulty.EASY))
    }
}
