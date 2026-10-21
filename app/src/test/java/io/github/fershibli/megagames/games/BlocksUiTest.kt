package io.github.fershibli.megagames.games

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.core.AppJson
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.SaveStore
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.games.blocks.BlocksPiece
import io.github.fershibli.megagames.games.blocks.BlocksState
import io.github.fershibli.megagames.ui.theme.MegaGamesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Plays the block puzzle with taps and a drag, the way a person does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class BlocksUiTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun saved(store: SaveStore, until: (BlocksState) -> Boolean): BlocksState? {
        var s: BlocksState? = null
        for (attempt in 0 until 100) {
            Thread.sleep(20)
            s = store.load()?.let { AppJson.decodeFromJsonElement(BlocksState.serializer(), it.state) }
            if (s != null && until(s)) break
        }
        return s
    }

    @Test
    fun tapAPieceThenTheBoardAndDragAnotherOne() {
        val store = SaveStore(app).apply { clear() }
        val n = 10
        // The first row is full except its last cell.
        val board = List(n * n) { if (it in 0..8) 3 else 0 }
        val tray = listOf(BlocksPiece(0, 0, 0), BlocksPiece(4, 0, 1), BlocksPiece(6, 0, 2))
        val start = BlocksState(n, Difficulty.MEDIUM, board, tray, seed = 3)
        val host = GameHost(GameId.BLOCKS, Difficulty.MEDIUM, AppJson.encodeToJsonElement(BlocksState.serializer(), start), store) {}
        rule.setContent { MegaGamesTheme(Settings()) { GameContent(GameId.BLOCKS, host) } }
        rule.waitForIdle()

        val node = rule.onNodeWithTag("blocks-board")
        fun trayCenter(slot: Int, width: Float, height: Float) = Offset((slot + 0.5f) * width / 3, height - width * 0.34f / 2)

        // Tap the single block below the board, then tap the empty end of the first row.
        node.performTouchInput { click(trayCenter(0, width.toFloat(), height.toFloat())) }
        rule.waitForIdle()
        node.performTouchInput { val cell = width / 10f; click(Offset(9.5f * cell, 0.5f * cell)) }
        rule.waitForIdle()
        val afterTap = saved(store) { it.tray[0] == null }!!
        assertNull(afterTap.tray[0])
        assertEquals(1, afterTap.lines)
        assertEquals(0, afterTap.board.count { it != 0 }) // the row was cleared
        assertEquals(1 + 10, afterTap.score)

        // Drag the line of five: the piece floats above the finger, so aim the finger below the target row.
        node.performTouchInput {
            val cell = width / 10f
            val lift = 56.dp.toPx()
            val from = trayCenter(1, width.toFloat(), height.toFloat())
            down(from)
            moveTo(from + Offset(0f, -cell * 2))
            moveTo(Offset(2.5f * cell, 6 * cell + lift))
            moveTo(Offset(2.5f * cell, 6 * cell + lift))
            up()
        }
        rule.waitForIdle()
        val afterDrag = saved(store) { it.tray[1] == null }!!
        assertNull(afterDrag.tray[1])
        // Row 5, columns 0 to 4.
        assertEquals((50..54).toList(), afterDrag.board.indices.filter { afterDrag.board[it] != 0 })
        store.clear()
    }
}
