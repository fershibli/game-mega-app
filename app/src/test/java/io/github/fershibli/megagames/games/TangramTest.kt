package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.games.tangram.Pt
import io.github.fershibli.megagames.games.tangram.TanKind
import io.github.fershibli.megagames.games.tangram.Tangram
import io.github.fershibli.megagames.games.tangram.TangramState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class TangramTest {
    @Test
    fun theSetHasTheClassicSevenPiecesWithAnAreaOfSixteen() {
        assertEquals(7, Tangram.SET.size)
        val areas = Tangram.SET.map { Tangram.area(Tangram.place(it, 0, false, 0.0, 0.0)) }
        assertEquals(listOf(4.0, 4.0, 2.0, 1.0, 1.0, 2.0, 2.0), areas)
        assertEquals(Tangram.AREA, areas.sum(), 1e-9)
    }

    @Test
    fun everySilhouetteIsExactlyTheSevenPiecesWithoutOverlaps() {
        assertTrue("only ${Tangram.SHAPES.size} silhouettes", Tangram.SHAPES.size >= 30)
        assertEquals(Tangram.SHAPES.size, Tangram.SHAPES.map { it.key }.toSet().size)
        for (shape in Tangram.SHAPES) {
            val pieces = shape.solution
            assertEquals(shape.key, Tangram.SET.map { it.ordinal }.sorted(), pieces.map { it.kind }.sorted())
            val corners = pieces.map { it.corners() }
            assertEquals(shape.key, Tangram.AREA, corners.sumOf { Tangram.area(it) }, 1e-9)
            for (i in corners.indices) for (j in i + 1 until corners.size) {
                assertEquals("${shape.key}: pieces $i and $j overlap", 0.0, Tangram.overlap(corners[i], corners[j]), 1e-6)
            }
            // The pieces hold together: every piece touches the others (at least at a corner).
            val reached = mutableSetOf(0)
            var grew = true
            while (grew) {
                grew = false
                for (i in corners.indices) if (i !in reached && reached.any { touches(corners[i], corners[it]) }) {
                    reached += i
                    grew = true
                }
            }
            assertEquals("${shape.key} falls apart", 7, reached.size)
        }
    }

    private fun touches(a: List<Pt>, b: List<Pt>) =
        a.any { p -> Tangram.contains(b, p, 1e-6) } || b.any { p -> Tangram.contains(a, p, 1e-6) }

    @Test
    fun theKnownSolutionCoversItsSilhouette() {
        for (shapeIndex in Tangram.SHAPES.indices) {
            val table = Tangram.table(shapeIndex)
            val solved = TangramState(shapeIndex, table.target, emptyList())
            assertEquals(Tangram.SHAPES[shapeIndex].key, 1.0, Tangram.coverage(solved), 1e-9)
            assertTrue(Tangram.solved(solved))
            // The tray, where the pieces start, is away from the silhouette.
            val start = Tangram.start(shapeIndex, Difficulty.EASY)
            assertEquals(0.0, Tangram.coverage(start), 1e-9)
            assertFalse(Tangram.solved(start))
        }
    }

    @Test
    fun levelsShowAllSomeOrNoneOfTheInnerLines() {
        assertEquals(7, Tangram.start(0, Difficulty.EASY).lines.size)
        assertEquals(3, Tangram.start(0, Difficulty.MEDIUM).lines.size)
        assertEquals(0, Tangram.start(0, Difficulty.HARD).lines.size)
    }

    @Test
    fun aPieceLetGoNearItsPlaceSnapsIntoIt() {
        val shape = Tangram.SHAPES.indexOfFirst { it.key == "cat" }
        val table = Tangram.table(shape)
        var s = Tangram.start(shape, Difficulty.HARD)
        // Take the medium triangle in the turn of its place, then drop it a little off.
        val i = s.pieces.indexOfFirst { it.tanKind == TanKind.MEDIUM }
        val goal = table.target.first { it.tanKind == TanKind.MEDIUM }
        s = s.copy(pieces = s.pieces.toMutableList().also { it[i] = goal.copy(x = goal.x + 0.3, y = goal.y - 0.25) })
        s = Tangram.snap(s, i)
        assertEquals(goal, s.pieces[i])
        assertTrue(Tangram.inPlace(s, i))
    }

    @Test
    fun turningAndFlippingKeepThePieceInPlace() {
        var s = Tangram.start(3, Difficulty.EASY)
        val i = 0
        val before = Tangram.centroid(s.pieces[i].corners())
        repeat(8) { s = Tangram.rotate(s, i) }
        val after = Tangram.centroid(s.pieces[i].corners())
        assertTrue(hypot(before.x - after.x, before.y - after.y) < 1.0)
        val p = s.pieces.indexOfFirst { it.tanKind == TanKind.PARALLELOGRAM }
        assertTrue(Tangram.flip(s).pieces[p].flip != s.pieces[p].flip)
    }

    @Test
    fun hintsPlaceEveryPieceUntilTheFigureIsDone() {
        for (shapeIndex in Tangram.SHAPES.indices) {
            var s = Tangram.start(shapeIndex, Difficulty.MEDIUM)
            repeat(7) { s = Tangram.hint(s) }
            assertTrue(Tangram.SHAPES[shapeIndex].key, Tangram.solved(s))
            assertEquals(7, s.hints)
        }
    }

    @Test
    fun aDraggedPieceStaysOnTheTable() {
        val s = Tangram.start(0, Difficulty.EASY)
        val far = Tangram.move(s, 0, 500.0, -500.0)
        val c = Tangram.centroid(far.pieces[0].corners())
        val t = Tangram.table(0)
        assertTrue(c.x <= t.width && c.y >= 0.0)
    }
}
