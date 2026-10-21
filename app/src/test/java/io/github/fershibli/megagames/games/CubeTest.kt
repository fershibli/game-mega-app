package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.cube.Cube
import io.github.fershibli.megagames.games.cube.CubeGeometry
import io.github.fershibli.megagames.games.cube.CubeMove
import io.github.fershibli.megagames.games.cube.CubeSolver
import io.github.fershibli.megagames.games.cube.CubeState
import io.github.fershibli.megagames.games.cube.newCube
import io.github.fershibli.megagames.games.cube.nextHint
import io.github.fershibli.megagames.games.cube.turn
import io.github.fershibli.megagames.games.cube.undo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CubeTest {
    private val sizes = 2..5

    private fun allMoves(n: Int) = (0 until 3).flatMap { a -> (0 until n).flatMap { l -> listOf(CubeMove(a, l, 1), CubeMove(a, l, -1)) } }

    @Test
    fun everyMoveIsAPermutationAndItsInverseUndoesIt() {
        for (n in sizes) {
            val start = Cube.scramble(n, 20, Random(n)).let { Cube.applyAll(Cube.solved(n), it, n) }
            for (m in allMoves(n)) {
                val perm = CubeGeometry.of(n).perm(m)
                assertEquals("move $m on $n is a permutation", perm.size, perm.toSet().size)
                val after = Cube.apply(start, m, n)
                assertFalse("move $m on $n changes the cube", after.contentEquals(start))
                assertArrayEquals(start, Cube.apply(after, m.inverse(), n))
            }
        }
    }

    @Test
    fun fourQuarterTurnsAreIdentity() {
        for (n in sizes) {
            val start = Cube.applyAll(Cube.solved(n), Cube.scramble(n, 15, Random(7)), n)
            for (m in allMoves(n)) {
                assertArrayEquals("$m x4 on $n", start, Cube.applyAll(start, List(4) { m }, n))
            }
        }
    }

    @Test
    fun aTurnMovesExactlyOneLayerOfStickers() {
        for (n in sizes) {
            val per = n * n
            for (m in allMoves(n)) {
                val perm = CubeGeometry.of(n).perm(m)
                val moved = perm.indices.count { perm[it] != it }
                val outer = m.layer == 0 || m.layer == n - 1
                // 4n side stickers, plus the face itself for an outer layer (its center stays on odd sizes).
                val expected = 4 * n + if (outer) per - (if (n % 2 == 1) 1 else 0) else 0
                assertEquals("stickers moved by $m on $n", expected, moved)
            }
        }
    }

    @Test
    fun clockwiseFrontTurnSendsTheTopRowToTheRight() {
        val n = 3
        val g = CubeGeometry.of(n)
        val after = Cube.apply(Cube.solved(n), Cube.faceTurn(Cube.F, 0, true, n), n)
        // The bottom row of the top face (next to the front) goes to the left column of the right face.
        for (r in 0 until 3) assertEquals(Cube.U, after[g.index(Cube.R, r, 0)])
        for (c in 0 until 3) assertEquals(Cube.R, after[g.index(Cube.D, 0, c)])
        // Front face itself still all front color.
        for (i in 0 until 9) assertEquals(Cube.F, after[Cube.F * 9 + i])
    }

    @Test
    fun scrambleUndoAndWinDetection() {
        for (n in sizes) for (d in Difficulty.entries) {
            val s = newCube(n, d, Random(n * 10 + d.ordinal))
            val stickers = s.stickers.toIntArray()
            assertFalse(Cube.isSolved(stickers, n))
            assertEquals(Cube.scrambleLength(n, d), s.scramble.size)
            // Undoing the mixing turns in reverse order gives back the solved cube.
            assertTrue(Cube.isSolved(Cube.applyAll(stickers, s.scramble.asReversed().map { it.inverse() }, n), n))
            assertTrue(Cube.isSolved(Cube.applyAll(stickers, Cube.wayBack(s.scramble, emptyList(), n), n), n))
        }
        assertTrue(Cube.isSolved(Cube.solved(4), 4))
        val oneOff = Cube.solved(3).also { it[0] = Cube.R }
        assertFalse(Cube.isSolved(oneOff, 3))
    }

    @Test
    fun simplifyKeepsTheEffectAndCancels() {
        val n = 4
        val r = Random(3)
        repeat(50) {
            val moves = List(30) { CubeMove(r.nextInt(2), r.nextInt(n), if (r.nextBoolean()) 1 else -1) }
            val s = Cube.simplify(moves, n)
            assertArrayEquals(Cube.applyAll(Cube.solved(n), moves, n), Cube.applyAll(Cube.solved(n), s, n))
            assertTrue(s.size <= moves.size)
        }
        val u = CubeMove(1, 3, -1)
        assertEquals(emptyList<CubeMove>(), Cube.simplify(listOf(u, CubeMove(0, 0, 1), CubeMove(0, 0, -1), u.inverse()), n))
        assertEquals(listOf(u.inverse()), Cube.simplify(listOf(u, u, u), n))
    }

    @Test
    fun wayBackSolvesTheBigCubesAfterPlayerTurns() {
        for (n in listOf(4, 5)) {
            val s = newCube(n, Difficulty.HARD, Random(n))
            val history = Cube.scramble(n, 25, Random(99))
            val now = Cube.applyAll(s.stickers.toIntArray(), history, n)
            val back = Cube.wayBack(s.scramble, history, n)
            assertTrue(back.size <= s.scramble.size + history.size)
            assertTrue(Cube.isSolved(Cube.applyAll(now, back, n), n))
        }
    }

    @Test
    fun solverSolvesRandom3x3QuicklyWithinAMoveLimit() {
        var worstMoves = 0
        var worstMs = 0L
        repeat(200) { seed ->
            val start = Cube.applyAll(Cube.solved(3), Cube.scramble(3, 30, Random(seed)), 3)
            val t0 = System.nanoTime()
            val steps = CubeSolver.solve(start, 3)
            val ms = (System.nanoTime() - t0) / 1_000_000
            worstMs = maxOf(worstMs, ms)
            worstMoves = maxOf(worstMoves, steps.size)
            assertTrue("seed $seed", Cube.isSolved(Cube.applyAll(start, steps.map { it.move }, 3), 3))
            // Steps come in the order of the method.
            val order = steps.map { CubeSolver.stages(3).indexOf(it.stage) }
            assertEquals(order.sorted(), order)
        }
        println("3x3 solver: worst $worstMoves quarter turns, worst $worstMs ms")
        assertTrue("too many turns: $worstMoves", worstMoves <= 300)
        assertTrue("too slow: $worstMs ms", worstMs < 2000)
    }

    @Test
    fun solverWorksAfterMiddleLayerTurnsOn3x3() {
        repeat(30) { seed ->
            val r = Random(500 + seed)
            val moves = List(25) { CubeMove(r.nextInt(3), r.nextInt(3), if (r.nextBoolean()) 1 else -1) }
            val start = Cube.applyAll(Cube.solved(3), moves, 3)
            if (Cube.isSolved(start, 3)) return@repeat
            val steps = CubeSolver.solve(start, 3)
            assertTrue("seed $seed", Cube.isSolved(Cube.applyAll(start, steps.map { it.move }, 3), 3))
        }
    }

    @Test
    fun solverSolvesRandom2x2() {
        var worstMoves = 0
        var worstMs = 0L
        repeat(200) { seed ->
            val start = Cube.applyAll(Cube.solved(2), Cube.scramble(2, 25, Random(seed)), 2)
            val t0 = System.nanoTime()
            val steps = CubeSolver.solve(start, 2)
            worstMs = maxOf(worstMs, (System.nanoTime() - t0) / 1_000_000)
            worstMoves = maxOf(worstMoves, steps.size)
            assertTrue("seed $seed", Cube.isSolved(Cube.applyAll(start, steps.map { it.move }, 2), 2))
        }
        println("2x2 solver: worst $worstMoves quarter turns, worst $worstMs ms")
        assertTrue(worstMoves <= 120)
        assertTrue(worstMs < 2000)
    }

    @Test
    fun followingTheHintsOneByOneSolvesEverySize() {
        for (n in sizes) {
            var s = newCube(n, Difficulty.HARD, Random(n + 40))
            // A few turns of the player first, one of them undone.
            s = s.turn(CubeMove(0, 0, 1)).turn(CubeMove(1, n - 1, -1)).turn(CubeMove(2, 0, 1)).undo()!!
            var turns = 0
            while (true) {
                val (planned, hint) = nextHint(s) ?: break
                s = planned.turn(hint.move, byHint = true)
                if (n <= 3) assertTrue(hint.stage in CubeSolver.stages(n)) else assertEquals(-1, hint.stage)
                assertTrue("n=$n stage=${hint.stage} left=${hint.left}", ++turns < 400)
                // Undo in the middle of the plan and take the hint again: the plan is kept.
                if (turns == 5) s = s.undo()!!
            }
            assertTrue(Cube.isSolved(s.stickers.toIntArray(), n))
            assertTrue(s.hints > 0)
        }
    }

    /** Moves in the usual letters (as written in the player guide). */
    private fun alg(text: String, n: Int = 3): List<CubeMove> = text.split(' ').flatMap { t ->
        val m = Cube.faceTurn("UDFBLR".indexOf(t[0]), 0, !t.endsWith("'"), n)
        if (t.endsWith("2")) listOf(m, m) else listOf(m)
    }

    /** The cube that [moves] solve. */
    private fun before(moves: List<CubeMove>, n: Int = 3) = Cube.applyAll(Cube.solved(n), moves.asReversed().map { it.inverse() }, n)

    @Test
    fun guideAlgorithmsDoWhatTheGuideSays() {
        val g = CubeGeometry.of(3)
        // Step 5: R U R' U R U2 R' U fixes the top edges when the matching two are at the back and right.
        val s5 = before(alg("R U R' U R U2 R' U"))
        fun edgeOk(r: Int, c: Int, side: Int) = s5[g.index(Cube.U, r, c)] == Cube.U && s5[g.index(side, 0, 1)] == side
        assertTrue(edgeOk(0, 1, Cube.B) && edgeOk(1, 2, Cube.R))
        assertFalse(edgeOk(2, 1, Cube.F) || edgeOk(1, 0, Cube.L))
        // Step 6: U R U' L' U R' U' L keeps the front-right top corner where it is.
        val s6 = before(alg("U R U' L' U R' U' L"))
        assertEquals(listOf(Cube.U, Cube.F, Cube.R), listOf(s6[g.index(Cube.U, 2, 2)], s6[g.index(Cube.F, 0, 2)], s6[g.index(Cube.R, 0, 0)]))
        // Step 7: R' D' R D six times changes nothing.
        assertArrayEquals(Cube.solved(3), Cube.applyAll(Cube.solved(3), List(6) { alg("R' D' R D") }.flatten(), 3))
        // 2x2: the last algorithm swaps the two corners on the right of the top and nothing else.
        val g2 = CubeGeometry.of(2)
        val s2 = before(alg("R U R' U' R' F R2 U' R' U' R U R' F'", 2), 2)
        val changed = (0 until g2.count).filter { s2[it] != it / 4 }.map { g2.layerOf(it, 0) to g2.layerOf(it, 1) }.toSet()
        assertEquals(setOf(1 to 1), changed)
        // Step 4: F R U R' U' F' turns a line from left to right into a cross.
        val s4 = before(alg("F R U R' U' F'"))
        assertEquals(Cube.U, s4[g.index(Cube.U, 1, 0)])
        assertEquals(Cube.U, s4[g.index(Cube.U, 1, 2)])
        assertNotEquals(Cube.U, s4[g.index(Cube.U, 0, 1)])
    }

    @Test
    fun stateRoundTripsThroughJson() {
        val s = newCube(3, Difficulty.MEDIUM, Random(1)).copy(history = listOf(CubeMove(0, 2, -1)))
        val json = io.github.fershibli.megagames.core.AppJson
        assertEquals(s, json.decodeFromString(CubeState.serializer(), json.encodeToString(CubeState.serializer(), s)))
    }
}
