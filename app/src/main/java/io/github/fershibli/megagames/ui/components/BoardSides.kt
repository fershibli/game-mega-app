package io.github.fershibli.megagames.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.fershibli.megagames.R

/**
 * A side of a two-player board game played by people taking turns on this phone: its name
 * ("Black"), the status while it plays ("Black to play") and the end line ("Black wins!").
 */
enum class BoardSide(@StringRes val label: Int, @StringRes val turn: Int, @StringRes val wins: Int) {
    WHITE(R.string.side_white, R.string.side_white_turn, R.string.side_white_wins),
    BLACK(R.string.side_black, R.string.side_black_turn, R.string.side_black_wins),
    RED(R.string.side_red, R.string.side_red_turn, R.string.side_red_wins),
    BLUE(R.string.side_blue, R.string.side_blue_turn, R.string.side_blue_wins),
    LIGHT(R.string.side_light, R.string.side_light_turn, R.string.side_light_wins),
    DARK(R.string.side_dark, R.string.side_dark_turn, R.string.side_dark_wins),
    BOTTOM(R.string.side_bottom, R.string.side_bottom_turn, R.string.side_bottom_wins),
    TOP(R.string.side_top, R.string.side_top_turn, R.string.side_top_wins),
}

/** Who to pass the phone to when it is [side]'s turn, shown with a piece of [color] when it has one. */
@Composable
fun sideSeat(side: BoardSide, color: Color? = null) = Seat(stringResource(side.label), color)
