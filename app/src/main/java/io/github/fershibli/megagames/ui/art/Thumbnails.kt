package io.github.fershibli.megagames.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.ui.theme.LocalPalette

@Composable
fun GameThumbnail(game: GameId, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Canvas(modifier) { drawRect(p.pieces[game.ordinal % p.pieces.size]) }
}
