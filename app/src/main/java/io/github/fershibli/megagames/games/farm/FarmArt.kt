package io.github.fershibli.megagames.games.farm

import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.fershibli.megagames.ui.theme.Palette

/** A farm in full swing, for the home-screen picture: ripe beds, a growing one and animals. */
private val thumbnailWorld: FarmWorld by lazy {
    val m = 60_000L
    FarmWorld(
        plots = listOf(
            Plot("tomato", -200 * m), Plot("carrot", -200 * m), Plot("pumpkin", -200 * m),
            Plot("corn", -4 * m), Plot("strawberry", -200 * m), Plot("lettuce", -200 * m),
        ),
        pens = listOf(Pen("chicken", -25 * m), Pen("cow", -5 * m), Pen("sheep", -70 * m)),
    )
}

/** Home-screen picture: the farm scene itself, small. */
fun farmThumbnail(scope: DrawScope, p: Palette) = scope.drawFarmScene(FarmScene(thumbnailWorld, 0L, 0), p)
