package io.github.fershibli.megagames.games

import androidx.compose.runtime.Composable
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.games.backgammon.BackgammonGame
import io.github.fershibli.megagames.games.buraco.BuracoGame
import io.github.fershibli.megagames.games.connect.ConnectGame
import io.github.fershibli.megagames.games.crossword.CrosswordGame
import io.github.fershibli.megagames.games.dots.DotsGame
import io.github.fershibli.megagames.games.mahjong.MahjongGame
import io.github.fershibli.megagames.games.pipes.PipesGame
import io.github.fershibli.megagames.games.reversi.ReversiGame
import io.github.fershibli.megagames.games.solitaire.SolitaireGame
import io.github.fershibli.megagames.games.sudoku.SudokuGame

/** Routes a [GameId] to its screen. */
@Composable
fun GameContent(game: GameId, host: GameHost) {
    when (game) {
        GameId.SUDOKU -> SudokuGame(host)
        GameId.SOLITAIRE -> SolitaireGame(host)
        GameId.REVERSI -> ReversiGame(host)
        GameId.MAHJONG -> MahjongGame(host)
        GameId.PIPES -> PipesGame(host)
        GameId.CONNECT -> ConnectGame(host)
        GameId.DOTS -> DotsGame(host)
        GameId.CROSSWORD -> CrosswordGame(host)
        GameId.BACKGAMMON -> BackgammonGame(host)
        GameId.BURACO -> BuracoGame(host)
        GameId.CHECKERS -> io.github.fershibli.megagames.games.checkers.CheckersGame(host)
        GameId.CHESS -> io.github.fershibli.megagames.games.chess.ChessGame(host)
        GameId.LUDO -> io.github.fershibli.megagames.games.ludo.LudoGame(host)
        GameId.MORRIS -> io.github.fershibli.megagames.games.morris.MorrisGame(host)
        GameId.MANCALA -> io.github.fershibli.megagames.games.mancala.MancalaGame(host)
    }
}
