package io.github.fershibli.megagames.core

import androidx.annotation.StringRes
import io.github.fershibli.megagames.R

/** Difficulty chosen before each game. Every game interprets it in its own way. */
enum class Difficulty(@StringRes val label: Int) {
    EASY(R.string.difficulty_easy),
    MEDIUM(R.string.difficulty_medium),
    HARD(R.string.difficulty_hard),
}

/**
 * Every game shipped in the app. The [key] is persisted in save files, so it must never change
 * once released.
 */
enum class GameId(val key: String, @StringRes val title: Int, @StringRes val howTo: Int) {
    SUDOKU("sudoku", R.string.game_sudoku, R.string.howto_sudoku),
    SOLITAIRE("solitaire", R.string.game_solitaire, R.string.howto_solitaire),
    BURACO("buraco", R.string.game_buraco, R.string.howto_buraco),
    BACKGAMMON("backgammon", R.string.game_backgammon, R.string.howto_backgammon),
    MAHJONG("mahjong", R.string.game_mahjong, R.string.howto_mahjong),
    REVERSI("reversi", R.string.game_reversi, R.string.howto_reversi),
    CROSSWORD("crossword", R.string.game_crossword, R.string.howto_crossword),
    PIPES("pipes", R.string.game_pipes, R.string.howto_pipes),
    CONNECT("connect", R.string.game_connect, R.string.howto_connect),
    DOTS("dots", R.string.game_dots, R.string.howto_dots);

    companion object {
        fun fromKey(key: String): GameId? = entries.firstOrNull { it.key == key }
    }
}

/** Result reported by a game when it ends. [detail] is an already localized, optional line. */
data class GameOutcome(val won: Boolean, val detail: String? = null)
