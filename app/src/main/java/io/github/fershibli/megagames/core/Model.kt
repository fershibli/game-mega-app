package io.github.fershibli.megagames.core

import androidx.annotation.StringRes
import io.github.fershibli.megagames.R

/** Difficulty chosen before each game. Every game interprets it in its own way. */
enum class Difficulty(@StringRes val label: Int) {
    EASY(R.string.difficulty_easy),
    MEDIUM(R.string.difficulty_medium),
    HARD(R.string.difficulty_hard),
}

/** Groups shown on the home screen, in this order. */
enum class GameCategory(@StringRes val label: Int) {
    CARDS(R.string.category_cards),
    BOARD(R.string.category_board),
    PUZZLES(R.string.category_puzzles),
    CALM_ACTION(R.string.category_calm_action),
    PASTIMES(R.string.category_pastimes),
}

/**
 * A set of rules a game can be played with (regional rules of checkers, cacheta, truco...).
 * The [key] is persisted in save files and in the remembered choice, so it must never change.
 */
data class GameVariant(val key: String, @StringRes val name: Int, @StringRes val description: Int)

/**
 * Every game shipped in the app. The [key] is persisted in save files, so it must never change
 * once released. A game with [variants] asks which rules to use before the difficulty (the last
 * choice comes preselected); a game without [hasDifficulty] (a pet, a farm) starts right away.
 */
enum class GameId(
    val key: String,
    @StringRes val title: Int,
    @StringRes val howTo: Int,
    val category: GameCategory,
    val variants: List<GameVariant> = emptyList(),
    val hasDifficulty: Boolean = true,
) {
    SUDOKU("sudoku", R.string.game_sudoku, R.string.howto_sudoku, GameCategory.PUZZLES),
    SOLITAIRE("solitaire", R.string.game_solitaire, R.string.howto_solitaire, GameCategory.CARDS),
    BURACO("buraco", R.string.game_buraco, R.string.howto_buraco, GameCategory.CARDS),
    BACKGAMMON("backgammon", R.string.game_backgammon, R.string.howto_backgammon, GameCategory.BOARD),
    MAHJONG("mahjong", R.string.game_mahjong, R.string.howto_mahjong, GameCategory.PUZZLES),
    REVERSI("reversi", R.string.game_reversi, R.string.howto_reversi, GameCategory.BOARD),
    CROSSWORD("crossword", R.string.game_crossword, R.string.howto_crossword, GameCategory.PUZZLES),
    PIPES("pipes", R.string.game_pipes, R.string.howto_pipes, GameCategory.PUZZLES),
    CONNECT("connect", R.string.game_connect, R.string.howto_connect, GameCategory.PUZZLES),
    DOTS("dots", R.string.game_dots, R.string.howto_dots, GameCategory.PUZZLES),
    CHECKERS("checkers", R.string.game_checkers, R.string.howto_checkers, GameCategory.BOARD, io.github.fershibli.megagames.games.checkers.CheckersRules.VARIANTS),
    CHESS("chess", R.string.game_chess, R.string.howto_chess, GameCategory.BOARD, listOf(GameVariant("standard", R.string.chess_variant_standard, R.string.chess_variant_standard_desc), GameVariant("famous", R.string.chess_variant_famous, R.string.chess_variant_famous_desc))),
    LUDO("ludo", R.string.game_ludo, R.string.howto_ludo, GameCategory.BOARD, io.github.fershibli.megagames.games.ludo.LudoRules.VARIANTS),
    MORRIS("morris", R.string.game_morris, R.string.howto_morris, GameCategory.BOARD, io.github.fershibli.megagames.games.morris.MorrisRules.VARIANTS),
    MANCALA("mancala", R.string.game_mancala, R.string.howto_mancala, GameCategory.BOARD, io.github.fershibli.megagames.games.mancala.MancalaRules.VARIANTS),
    ;

    fun variant(key: String?): GameVariant? = variants.firstOrNull { it.key == key } ?: variants.firstOrNull()

    companion object {
        fun fromKey(key: String): GameId? = entries.firstOrNull { it.key == key }
    }
}

/** Result reported by a game when it ends. [detail] is an already localized, optional line. */
data class GameOutcome(val won: Boolean, val detail: String? = null)
