package io.github.fershibli.megagames.games

import androidx.compose.runtime.Composable
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId

/** Routes a [GameId] to its screen. */
@Composable
fun GameContent(game: GameId, host: GameHost) {
    when (game) {
        else -> androidx.compose.material3.Text(game.key)
    }
}
