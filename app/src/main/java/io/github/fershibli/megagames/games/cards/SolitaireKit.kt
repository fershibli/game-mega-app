package io.github.fershibli.megagames.games.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.ui.components.BigDialog

/*
 * Small pieces shared by the one-player card games (Spider, Free Cells, Pyramid): the way
 * columns of overlapping cards are spread out, and the "no more moves" dialog.
 */

/** How long a card that just moved stays highlighted, and how long a refused card shows red. */
const val CARD_FLASH_MS = 700L
const val CARD_REFUSED_MS = 500L

/** How long a short explanation stays in the status line. */
const val CARD_MESSAGE_MS = 4000L

/** Pause between two cards of an automatic finish. */
const val CARD_AUTO_STEP_MS = 140L

/**
 * Vertical position of every card of a column, from [top]. Face-down cards sit close together;
 * face-up cards show their whole corner (a little more with bigger pieces, [scale]). When the
 * column would run past [bottom], everything is squeezed to fit.
 */
fun columnOffsets(faceUp: List<Boolean>, cardH: Float, top: Float, bottom: Float, scale: Float): FloatArray {
    var down = cardH * 0.13f
    var up = cardH * 0.3f * scale.coerceIn(1f, 1.5f)
    val needed = faceUp.dropLast(1).sumOf { (if (it) up else down).toDouble() }.toFloat() + cardH
    val available = bottom - top
    if (needed > available && faceUp.size > 1) {
        val k = ((available - cardH) / (needed - cardH)).coerceAtLeast(0.05f)
        down *= k
        up *= k
    }
    val out = FloatArray(faceUp.size)
    var y = top
    for (i in faceUp.indices) {
        out[i] = y
        y += if (faceUp[i]) up else down
    }
    return out
}

/**
 * Shown when no useful move is left: the player can take moves back, give up (the game ends
 * as lost) or close it and keep looking. The game never leaves anyone stuck without a way out.
 */
@Composable
fun NoMovesDialog(
    title: String,
    message: String,
    giveUp: String,
    keepLooking: String,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onGiveUp: () -> Unit,
    onKeepLooking: () -> Unit,
) {
    val undo = stringResource(R.string.undo)
    BigDialog(
        title = title,
        message = message,
        onDismiss = onKeepLooking,
        buttons = buildList {
            if (canUndo) add(undo to onUndo)
            add(giveUp to onGiveUp)
            add(keepLooking to onKeepLooking)
        },
    )
}
