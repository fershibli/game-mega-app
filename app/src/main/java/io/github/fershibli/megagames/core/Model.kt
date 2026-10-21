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
 * A game that cannot be played in every language (a cryptogram needs an alphabet) lists the
 * [languages] it can use, for example `languages = setOf(AppLanguage.ENGLISH, AppLanguage.SPANISH)`;
 * `null` means every language. It is still listed, and asks which language to play in when the
 * app language is not one of them.
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
    val languages: Set<AppLanguage>? = null,
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
    BLOCKS("blocks", R.string.game_blocks, R.string.howto_blocks, GameCategory.PUZZLES),
    PAIRS("pairs", R.string.game_pairs, R.string.howto_pairs, GameCategory.PUZZLES),
    SNAKE("snake", R.string.game_snake, R.string.howto_snake, GameCategory.CALM_ACTION),
    XIANGQI("xiangqi", R.string.game_xiangqi, R.string.howto_xiangqi, GameCategory.BOARD),
    JANGGI("janggi", R.string.game_janggi, R.string.howto_janggi, GameCategory.BOARD),
    SHOGI("shogi", R.string.game_shogi, R.string.howto_shogi, GameCategory.BOARD, listOf(GameVariant("standard", R.string.shogi_variant_standard, R.string.shogi_variant_standard_desc), GameVariant("mini", R.string.shogi_variant_mini, R.string.shogi_variant_mini_desc))),
    PEGS("pegs", R.string.game_pegs, R.string.howto_pegs, GameCategory.PUZZLES, listOf(GameVariant("english", R.string.pegs_variant_english, R.string.pegs_variant_english_desc), GameVariant("french", R.string.pegs_variant_french, R.string.pegs_variant_french_desc))),
    MEMORY("memory", R.string.game_memory, R.string.howto_memory, GameCategory.PUZZLES, listOf(GameVariant("cards", R.string.memory_variant_cards, R.string.memory_variant_cards_desc), GameVariant("fruits", R.string.memory_variant_fruits, R.string.memory_variant_fruits_desc), GameVariant("symbols", R.string.memory_variant_symbols, R.string.memory_variant_symbols_desc), GameVariant("animals", R.string.memory_variant_animals, R.string.memory_variant_animals_desc))),
    SEQUENCE("sequence", R.string.game_sequence, R.string.howto_sequence, GameCategory.PUZZLES),
    NONOGRAM("nonogram", R.string.game_nonogram, R.string.howto_nonogram, GameCategory.PUZZLES),
    KAKURO("kakuro", R.string.game_kakuro, R.string.howto_kakuro, GameCategory.PUZZLES),
    KILLER("killer", R.string.game_killer, R.string.howto_killer, GameCategory.PUZZLES),
    SPIDER("spider", R.string.game_spider, R.string.howto_spider, GameCategory.CARDS, io.github.fershibli.megagames.games.spider.SpiderRules.VARIANTS, hasDifficulty = false),
    FREECELL("freecell", R.string.game_freecell, R.string.howto_freecell, GameCategory.CARDS),
    PYRAMID("pyramid", R.string.game_pyramid, R.string.howto_pyramid, GameCategory.CARDS),
    WORDSEARCH("wordsearch", R.string.game_wordsearch, R.string.howto_wordsearch, GameCategory.PUZZLES),
    ANAGRAM("anagram", R.string.game_anagram, R.string.howto_anagram, GameCategory.PUZZLES),
    DAYWORD("dayword", R.string.game_dayword, R.string.howto_dayword, GameCategory.PUZZLES),
    GO("go", R.string.game_go, R.string.howto_go, GameCategory.BOARD, io.github.fershibli.megagames.games.go.Go.VARIANTS),
    GOMOKU("gomoku", R.string.game_gomoku, R.string.howto_gomoku, GameCategory.BOARD),
    FOURINROW("fourinrow", R.string.game_fourinrow, R.string.howto_fourinrow, GameCategory.BOARD),
    LUDO("ludo", R.string.game_ludo, R.string.howto_ludo, GameCategory.BOARD, io.github.fershibli.megagames.games.ludo.LudoRules.VARIANTS),
    MORRIS("morris", R.string.game_morris, R.string.howto_morris, GameCategory.BOARD, io.github.fershibli.megagames.games.morris.MorrisRules.VARIANTS),
    MANCALA("mancala", R.string.game_mancala, R.string.howto_mancala, GameCategory.BOARD, io.github.fershibli.megagames.games.mancala.MancalaRules.VARIANTS),
    ;

    fun variant(key: String?): GameVariant? = variants.firstOrNull { it.key == key } ?: variants.firstOrNull()

    /** True when the game can be played in [language] ([AppLanguage.SYSTEM] is resolved first). */
    fun supports(language: AppLanguage): Boolean = GameLanguages.supports(languages, language)

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
