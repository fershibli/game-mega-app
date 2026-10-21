package io.github.fershibli.megagames.core

import kotlinx.serialization.Serializable
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
 * A number of seats a game can be played with, and an optional note about it ("in pairs").
 * Against the computer, the other seats are computer players; taking turns, they are people.
 */
data class TableSize(val seats: Int, @StringRes val note: Int? = null)

/**
 * How a game is set up before it starts: against the computer or [together] (people taking
 * turns on this device), and with how many [seats] at the table (0 = the game's usual number).
 */
@Serializable
data class PlaySetup(val together: Boolean = false, val seats: Int = 0)

/**
 * Every game shipped in the app. The [key] is persisted in save files, so it must never change
 * once released. A game with [variants] asks which rules to use before the difficulty (the last
 * choice comes preselected); a game without [hasDifficulty] (a pet, a farm) starts right away.
 * [takeTurns] adds "take turns on this phone" to the difficulty list, and more than one entry in
 * [tables] asks how many sit at the table.
 */
enum class GameId(
    val key: String,
    @StringRes val title: Int,
    @StringRes val howTo: Int,
    val category: GameCategory,
    val variants: List<GameVariant> = emptyList(),
    val hasDifficulty: Boolean = true,
    val takeTurns: Boolean = false,
    val tables: List<TableSize> = emptyList(),
) {
    SUDOKU("sudoku", R.string.game_sudoku, R.string.howto_sudoku, GameCategory.PUZZLES),
    SOLITAIRE("solitaire", R.string.game_solitaire, R.string.howto_solitaire, GameCategory.CARDS),
    BURACO("buraco", R.string.game_buraco, R.string.howto_buraco, GameCategory.CARDS),
    BACKGAMMON("backgammon", R.string.game_backgammon, R.string.howto_backgammon, GameCategory.BOARD),
    MAHJONG("mahjong", R.string.game_mahjong, R.string.howto_mahjong, GameCategory.PUZZLES),
    REVERSI("reversi", R.string.game_reversi, R.string.howto_reversi, GameCategory.BOARD, takeTurns = true),
    CROSSWORD("crossword", R.string.game_crossword, R.string.howto_crossword, GameCategory.PUZZLES),
    PIPES("pipes", R.string.game_pipes, R.string.howto_pipes, GameCategory.PUZZLES),
    CONNECT("connect", R.string.game_connect, R.string.howto_connect, GameCategory.PUZZLES),
    DOTS("dots", R.string.game_dots, R.string.howto_dots, GameCategory.PUZZLES),
    CHECKERS("checkers", R.string.game_checkers, R.string.howto_checkers, GameCategory.BOARD, io.github.fershibli.megagames.games.checkers.CheckersRules.VARIANTS),
    CHESS("chess", R.string.game_chess, R.string.howto_chess, GameCategory.BOARD, listOf(GameVariant("standard", R.string.chess_variant_standard, R.string.chess_variant_standard_desc), GameVariant("famous", R.string.chess_variant_famous, R.string.chess_variant_famous_desc))),
    PET("pet", R.string.game_pet, R.string.howto_pet, GameCategory.PASTIMES, hasDifficulty = false),
    CACHETA("cacheta", R.string.game_cacheta, R.string.howto_cacheta, GameCategory.CARDS, listOf(GameVariant("classic", R.string.cacheta_variant_classic, R.string.cacheta_variant_classic_desc), GameVariant("wild", R.string.cacheta_variant_wild, R.string.cacheta_variant_wild_desc), GameVariant("pife", R.string.cacheta_variant_pife, R.string.cacheta_variant_pife_desc))),
    TRUCO("truco", R.string.game_truco, R.string.howto_truco, GameCategory.CARDS, listOf(GameVariant("paulista", R.string.truco_variant_paulista, R.string.truco_variant_paulista_desc), GameVariant("mineiro", R.string.truco_variant_mineiro, R.string.truco_variant_mineiro_desc))),
    CUBE("cube", R.string.game_cube, R.string.howto_cube, GameCategory.PUZZLES, listOf(GameVariant("2", R.string.cube_variant_2, R.string.cube_variant_2_desc), GameVariant("3", R.string.cube_variant_3, R.string.cube_variant_3_desc), GameVariant("4", R.string.cube_variant_4, R.string.cube_variant_4_desc), GameVariant("5", R.string.cube_variant_5, R.string.cube_variant_5_desc))),
    ;

    fun variant(key: String?): GameVariant? = variants.firstOrNull { it.key == key } ?: variants.firstOrNull()

    companion object {
        fun fromKey(key: String): GameId? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Result reported by a game when it ends. [detail] is an already localized, optional line.
 * [shared] is a game played by people taking turns: nobody "won against the phone", so the end
 * shows "End of the game" and [detail] says who won.
 */
data class GameOutcome(val won: Boolean, val detail: String? = null, val shared: Boolean = false)
