package io.github.fershibli.megagames.games

import androidx.compose.runtime.Composable
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.games.solitaire.SolitaireGame
import io.github.fershibli.megagames.games.sudoku.SudokuGame

/** Routes a [GameId] to its screen. */
@Composable
fun GameContent(game: GameId, host: GameHost) {
    when (game) {
        GameId.SUDOKU -> SudokuGame(host)
        GameId.SOLITAIRE -> SolitaireGame(host)
        else -> androidx.compose.material3.Text(game.key)
    }
}
