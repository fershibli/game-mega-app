package io.github.fershibli.megagames.core

import androidx.annotation.StringRes
import kotlinx.serialization.json.JsonElement

/**
 * A lesson played on a game's own board: a position prepared for it and a few short steps.
 * Each step says one thing ([TutorialStep.caption]) about one place of the board
 * ([TutorialStep.target], a name the game understands, such as "stock" or "column:3:1").
 */
class Tutorial(
    /** The position the lesson starts from, as the game would save it. */
    val state: () -> JsonElement,
    val steps: List<TutorialStep>,
)

data class TutorialStep(@StringRes val caption: Int, val kind: Kind = Kind.READ, val target: String? = null) {
    enum class Kind {
        /** Something to read; the target, if any, is only pointed out. */
        READ,

        /** The app plays the move itself: a hand goes to the target and touches it. */
        SHOW,

        /** The player plays the move: only the target answers the touch. */
        TRY,
    }
}
