package io.github.fershibli.megagames.games.trail

import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.fershibli.megagames.ui.theme.Palette

/** Home-screen picture: the whole pixel board with two pawns on the way. */
fun trailThumbnail(scope: DrawScope, p: Palette) = scope.run {
    val v = TrailView.fitting(size, withDie = false)
    drawTrailBoard(v, p)
    drawTrailPerson(v, pawnFoot(v, v.square(10), 0, 1), p, 0)
    drawTrailPerson(v, pawnFoot(v, v.square(21), 0, 1), p, 3)
}
