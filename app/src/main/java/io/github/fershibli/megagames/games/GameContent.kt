package io.github.fershibli.megagames.games

import androidx.compose.runtime.Composable
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.games.backgammon.BackgammonGame
import io.github.fershibli.megagames.games.buraco.BuracoGame
import io.github.fershibli.megagames.games.connect.ConnectGame
import io.github.fershibli.megagames.games.crossword.CrosswordGame
import io.github.fershibli.megagames.games.cube.CubeGame
import io.github.fershibli.megagames.games.dots.DotsGame
import io.github.fershibli.megagames.games.janggi.JanggiGame
import io.github.fershibli.megagames.games.mahjong.MahjongGame
import io.github.fershibli.megagames.games.pet.PetGame
import io.github.fershibli.megagames.games.memory.MemoryGame
import io.github.fershibli.megagames.games.pegs.PegsGame
import io.github.fershibli.megagames.games.pipes.PipesGame
import io.github.fershibli.megagames.games.reversi.ReversiGame
import io.github.fershibli.megagames.games.shogi.ShogiGame
import io.github.fershibli.megagames.games.sequence.SequenceGame
import io.github.fershibli.megagames.games.solitaire.SolitaireGame
import io.github.fershibli.megagames.games.sudoku.SudokuGame
import io.github.fershibli.megagames.games.xiangqi.XiangqiGame

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
        GameId.PET -> PetGame(host)
        GameId.CACHETA -> io.github.fershibli.megagames.games.cacheta.CachetaGame(host)
        GameId.TRUCO -> io.github.fershibli.megagames.games.truco.TrucoGame(host)
        GameId.CUBE -> CubeGame(host)
        GameId.BLOCKS -> io.github.fershibli.megagames.games.blocks.BlocksGame(host)
        GameId.PAIRS -> io.github.fershibli.megagames.games.pairs.PairsGame(host)
        GameId.SNAKE -> io.github.fershibli.megagames.games.snake.SnakeGame(host)
        GameId.XIANGQI -> XiangqiGame(host)
        GameId.JANGGI -> JanggiGame(host)
        GameId.SHOGI -> ShogiGame(host)
        GameId.PEGS -> PegsGame(host)
        GameId.MEMORY -> MemoryGame(host)
        GameId.SEQUENCE -> SequenceGame(host)
        GameId.NONOGRAM -> io.github.fershibli.megagames.games.nonogram.NonogramGame(host)
        GameId.KAKURO -> io.github.fershibli.megagames.games.kakuro.KakuroGame(host)
        GameId.KILLER -> io.github.fershibli.megagames.games.killer.KillerGame(host)
        GameId.SPIDER -> io.github.fershibli.megagames.games.spider.SpiderGame(host)
        GameId.FREECELL -> io.github.fershibli.megagames.games.freecell.FreeCellGame(host)
        GameId.PYRAMID -> io.github.fershibli.megagames.games.pyramid.PyramidGame(host)
        GameId.WORDSEARCH -> io.github.fershibli.megagames.games.wordsearch.WordSearchGame(host)
        GameId.ANAGRAM -> io.github.fershibli.megagames.games.anagram.AnagramGame(host)
        GameId.DAYWORD -> io.github.fershibli.megagames.games.dayword.DayWordGame(host)
        GameId.GO -> io.github.fershibli.megagames.games.go.GoGame(host)
        GameId.GOMOKU -> io.github.fershibli.megagames.games.gomoku.GomokuGame(host)
        GameId.FOURINROW -> io.github.fershibli.megagames.games.fourinrow.FourInRowGame(host)
        GameId.LUDO -> io.github.fershibli.megagames.games.ludo.LudoGame(host)
        GameId.MORRIS -> io.github.fershibli.megagames.games.morris.MorrisGame(host)
        GameId.MANCALA -> io.github.fershibli.megagames.games.mancala.MancalaGame(host)
        GameId.GUESSWORD -> io.github.fershibli.megagames.games.guessword.GuessWordGame(host)
        GameId.CRYPTOGRAM -> io.github.fershibli.megagames.games.cryptogram.CryptogramGame(host)
        GameId.MAZE -> io.github.fershibli.megagames.games.maze.MazeGame(host)
        GameId.LIGHTS -> io.github.fershibli.megagames.games.lights.LightsGame(host)
        GameId.BOXES -> io.github.fershibli.megagames.games.boxes.BoxesGame(host)
        GameId.MERGE -> io.github.fershibli.megagames.games.merge.MergeGame(host)
        GameId.TANGRAM -> io.github.fershibli.megagames.games.tangram.TangramGame(host)
    }
}
