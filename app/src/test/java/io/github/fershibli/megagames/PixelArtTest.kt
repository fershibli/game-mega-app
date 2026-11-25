package io.github.fershibli.megagames

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.github.fershibli.megagames.ui.art.IsoGrid
import io.github.fershibli.megagames.ui.art.PIXEL_ART_MAX
import io.github.fershibli.megagames.ui.art.PixelColors
import io.github.fershibli.megagames.ui.art.palette
import io.github.fershibli.megagames.ui.art.pixelArt
import io.github.fershibli.megagames.ui.theme.HighContrastLight
import io.github.fershibli.megagames.ui.theme.NormalDark
import io.github.fershibli.megagames.ui.theme.NormalLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PixelArtTest {
    @Test
    fun aPaletteReadsCharactersAndHexColors() {
        val p = palette("o=4A3426 a=F4A259")
        assertEquals(Color(0xFF4A3426), p['o'])
        assertEquals(Color(0xFFF4A259), p['a'])
    }

    @Test
    fun rowsBecomeOneRunPerStretchOfColorAndDotsAreTransparent() {
        val art = pixelArt("o=000000 a=FFFFFF", "oooaa", "..o..", ".....")
        assertEquals(5, art.width)
        assertEquals(3, art.height)
        // "ooo", "aa" on the first row, "o" on the second; the empty row draws nothing.
        assertEquals(3, art.runCount)
    }

    @Test
    fun anUnknownCharacterIsAnErrorAndPicturesHaveAMaximumSize() {
        assertThrows(IllegalStateException::class.java) { pixelArt("o=000000", "ox") }
        val big = List(PIXEL_ART_MAX + 1) { "o" }
        assertThrows(IllegalArgumentException::class.java) { pixelArt(big, palette("o=000000")) }
        // The largest allowed picture still packs every run correctly.
        val wide = pixelArt(List(PIXEL_ART_MAX) { "o".repeat(PIXEL_ART_MAX) }, palette("o=000000"))
        assertEquals(PIXEL_ART_MAX, wide.width)
        assertEquals(PIXEL_ART_MAX, wide.runCount)
    }

    @Test
    fun recoloringKeepsTheShape() {
        val art = pixelArt("o=000000 a=FFFFFF", "oa", "ao")
        val night = art.recolored { it.copy(alpha = 0.5f) }
        assertEquals(art.width, night.width)
        assertEquals(art.runCount, night.runCount)
    }

    private fun close(a: Float, b: Float) = abs(a - b) < 1e-3f

    @Test
    fun isometricCellsGoToTheScreenAndBack() {
        val grid = IsoGrid(64f, 32f, Offset(300f, 40f))
        assertEquals(Offset(300f, 40f), grid.toScreen(0f, 0f))
        // One column to the right goes half a tile right and down; one row goes half a tile left and down.
        assertEquals(Offset(332f, 56f), grid.toScreen(1f, 0f))
        assertEquals(Offset(268f, 56f), grid.toScreen(0f, 1f))
        for (c in 0 until 6) for (r in 0 until 6) {
            val center = grid.center(c, r)
            assertEquals(c to r, grid.cellAt(center))
            val (mc, mr) = grid.toMap(center)
            assertTrue(close(mc, c + 0.5f) && close(mr, r + 0.5f))
        }
        // A point above the top corner of the map is outside it.
        val (c, r) = grid.cellAt(Offset(300f, 10f))
        assertTrue(c < 0 || r < 0)
    }

    @Test
    fun aFittedGridUsesWholePixelTilesAndStaysInside() {
        val grid = IsoGrid.fitting(columns = 6, rows = 4, width = 400f, height = 300f)
        assertEquals(0f, grid.tileW % 1f)
        assertEquals(grid.tileW / 2, grid.tileH)
        // Every corner of the map is inside the drawing area.
        for ((c, r) in listOf(0f to 0f, 6f to 0f, 0f to 4f, 6f to 4f)) {
            val p = grid.toScreen(c, r)
            assertTrue("$p", p.x in 0f..400f && p.y in 0f..300f)
        }
    }

    @Test
    fun theBaseColorsAdaptToDarkAndHighContrast() {
        val day = PixelColors.of(NormalLight)
        val night = PixelColors.of(NormalDark)
        val strong = PixelColors.of(HighContrastLight)
        assertEquals(PixelColors.Day.grass, day.grass)
        assertNotEquals(day.grass, night.grass)
        assertTrue(night.grass.luminanceApprox() < day.grass.luminanceApprox())
        assertEquals(Color.Black, strong.outline)
    }

    private fun Color.luminanceApprox() = 0.3f * red + 0.59f * green + 0.11f * blue
}
