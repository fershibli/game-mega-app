package io.github.fershibli.megagames.games

import io.github.fershibli.megagames.games.trail.Stage
import io.github.fershibli.megagames.games.trail.Trail
import io.github.fershibli.megagames.games.trail.TrailMap
import io.github.fershibli.megagames.games.trail.TrailSprites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The pixel board: every square inside its landscape, none on top of another, pictures in free corners. */
class TrailMapTest {
    private val inner = TrailMap.FRAME until TrailMap.WIDTH - TrailMap.FRAME

    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun overlaps(o: Box) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    }

    private fun squareBox(i: Int): Box {
        val (x, y) = TrailMap.POINTS[i]
        val (w, h) = TrailMap.squareSize(i)
        return Box(x - w / 2, y - h / 2, x - w / 2 + w, y - h / 2 + h)
    }

    @Test
    fun everySquareSitsInsideTheFrameInItsOwnLandscape() {
        assertEquals(Trail.SQUARES, TrailMap.POINTS.size)
        for (i in 0 until Trail.SQUARES) {
            val b = squareBox(i)
            assertTrue("square $i", b.left in inner && b.right - 1 in inner)
            val (_, y) = TrailMap.POINTS[i]
            val top = TrailMap.bandTop(Trail.stage(i).ordinal)
            assertTrue("square $i is in its stage", y in top until top + TrailMap.BAND)
        }
    }

    @Test
    fun theSquaresFollowEachOtherWithoutPilingUp() {
        for (i in 0 until Trail.FINISH) {
            val (x0, y0) = TrailMap.POINTS[i]
            val (x1, y1) = TrailMap.POINTS[i + 1]
            // Neighbours are close (the path is easy to follow) but never on the same spot.
            assertTrue("step $i", abs(x1 - x0) + abs(y1 - y0) in 8..20)
        }
        for (i in 0 until Trail.SQUARES) for (j in i + 2 until Trail.SQUARES) {
            val a = squareBox(i)
            val b = squareBox(j)
            // Squares that are not neighbours on the path keep apart, apart from a corner pixel.
            val shrunk = Box(a.left + 1, a.top + 1, a.right - 1, a.bottom - 1)
            assertTrue("squares $i and $j", !shrunk.overlaps(b))
        }
    }

    @Test
    fun thePicturesOfEachLandscapeStayOffTheSquares() {
        for (stage in Stage.entries.indices) for (col in 0 until TrailMap.PER_BAND) {
            val (x, bottom) = TrailMap.pocket(stage, col) ?: continue
            val art = TrailSprites.scenery[stage][col] ?: continue
            val box = Box(x - art.width / 2 + 1, bottom - art.height + 1, x - art.width / 2 + art.width - 1, bottom - 1)
            assertTrue("picture of stage $stage col $col inside", box.left >= TrailMap.FRAME && box.right <= TrailMap.WIDTH - TrailMap.FRAME)
            for (i in 0 until Trail.SQUARES) assertTrue("picture of stage $stage col $col on square $i", !box.overlaps(squareBox(i)))
        }
    }
}
