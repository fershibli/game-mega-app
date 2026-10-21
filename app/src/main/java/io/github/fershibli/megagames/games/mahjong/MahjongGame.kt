package io.github.fershibli.megagames.games.mahjong

import io.github.fershibli.megagames.ui.theme.wait
import io.github.fershibli.megagames.ui.theme.currentPace
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameOutcome
import io.github.fershibli.megagames.ui.art.darker
import io.github.fershibli.megagames.ui.art.drawMahjongTile
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.GameLayout
import io.github.fershibli.megagames.ui.components.LoadGame
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class MahjongState(
    val tiles: List<MTile>,
    val total: Int,
    val selected: Int = -1,
    /** Remaining "shuffle" uses; 99 is shown as unlimited. */
    val shufflesLeft: Int,
)

const val UNLIMITED = 99

fun newMahjong(d: Difficulty, random: Random = Random.Default): MahjongState {
    val tiles = Mahjong.newGame(d, random)
    return MahjongState(tiles, tiles.size, shufflesLeft = when (d) { Difficulty.EASY -> UNLIMITED; Difficulty.MEDIUM -> 3; Difficulty.HARD -> 1 })
}

@Composable
fun MahjongGame(host: GameHost) {
    LoadGame(host, MahjongState.serializer(), { newMahjong(host.difficulty) }) { state -> MahjongScreen(state, host) }
}

/** Board geometry: fits the whole layout in the available space. */
private class MahjongGeometry(tiles: List<MTile>, all: List<Pos>, width: Float, height: Float) {
    val minX = all.minOf { it.x }
    val minY = all.minOf { it.y }
    val spanX = all.maxOf { it.x } + 2 - minX
    val spanY = all.maxOf { it.y } + 2 - minY
    val maxZ = all.maxOf { it.z }
    val unit: Float = run {
        val pad = 0.96f
        minOf(width * pad / (spanX + 0.24f * (maxZ + 1)), height * pad / (1.3f * spanY + 0.24f * (maxZ + 1)))
    }
    val tileW = unit * 2
    val tileH = unit * 2 * 1.3f
    val depth = unit * 0.24f
    val ox = (width - (spanX * unit + depth * (maxZ + 1))) / 2
    val oy = (height - (1.3f * spanY * unit + depth * (maxZ + 1))) / 2
    fun rect(t: MTile) = Rect(Offset(ox + maxZ * depth + (t.x - minX) * unit - t.z * depth, oy + maxZ * depth + (t.y - minY) * unit * 1.3f - t.z * depth), Size(tileW, tileH))
    val order: List<MTile> = tiles.sortedWith(compareBy({ it.z }, { it.y }, { it.x }))
    fun hit(p: Offset): MTile? = order.lastOrNull { rect(it).contains(p) }
}

@Composable
private fun MahjongScreen(state: MutableState<MahjongState>, host: GameHost) {
    val s = state.value
    val history = remember { mutableStateListOf<MahjongState>() }
    var hint by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // Layout bounds come from the full layout so the board does not jump as it empties.
    val initialTiles = remember { MahjongLayouts.forDifficulty(host.difficulty) }
    val pairs = remember(s.tiles) { Mahjong.availablePairs(s.tiles) }
    val dimBlocked = host.difficulty != Difficulty.HARD
    val free = remember(s.tiles) { s.tiles.filter { Mahjong.isFree(it, s.tiles) }.map { it.id }.toSet() }

    val pace = currentPace
    LaunchedEffect(s.tiles.size, pairs.isEmpty(), s.shufflesLeft) {
        if (s.tiles.isEmpty()) host.finish(GameOutcome(true, null))
        else if (pairs.isEmpty() && s.shufflesLeft == 0) {
            pace.wait(800)
            host.finish(GameOutcome(false, null))
        }
    }
    LaunchedEffect(hint) { if (hint.isNotEmpty()) { pace.wait(2500); hint = emptySet() } }

    fun tap(t: MTile?) {
        if (t == null) { state.value = s.copy(selected = -1); return }
        if (t.id !in free) return
        val sel = s.tiles.firstOrNull { it.id == s.selected }
        when {
            sel == null || sel.id == t.id -> state.value = s.copy(selected = if (sel?.id == t.id) -1 else t.id)
            MahjongFaces.matches(sel.face, t.face) -> {
                history += s
                hint = emptySet()
                state.value = s.copy(tiles = s.tiles.filter { it.id != sel.id && it.id != t.id }, selected = -1)
            }
            else -> state.value = s.copy(selected = t.id)
        }
    }

    GameLayout(
        status = {
            val text = if (pairs.isEmpty() && s.tiles.isNotEmpty()) stringResource(R.string.mahjong_stuck)
            else stringResource(R.string.mahjong_status, s.tiles.size, pairs.size)
            StatusPill(text, emphasized = pairs.isEmpty())
        },
        controls = {
            ActionBar {
                BigButton(stringResource(R.string.undo), { history.removeLastOrNull()?.let { state.value = it.copy(shufflesLeft = s.shufflesLeft) } }, icon = AppIcon.UNDO, style = ButtonStyle.SECONDARY, enabled = history.isNotEmpty(), compact = true)
                BigButton(stringResource(R.string.hint), { hint = pairs.firstOrNull()?.let { setOf(it.first.id, it.second.id) } ?: emptySet() }, icon = AppIcon.HINT, style = ButtonStyle.SECONDARY, enabled = pairs.isNotEmpty(), compact = true)
                val label = if (s.shufflesLeft >= UNLIMITED) stringResource(R.string.mahjong_shuffle) else stringResource(R.string.mahjong_shuffle_count, s.shufflesLeft)
                BigButton(label, {
                    history.clear()
                    state.value = s.copy(tiles = Mahjong.reshuffle(s.tiles, Random.Default), selected = -1, shufflesLeft = if (s.shufflesLeft >= UNLIMITED) UNLIMITED else s.shufflesLeft - 1)
                }, icon = AppIcon.SHUFFLE, style = if (pairs.isEmpty()) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY, enabled = s.shufflesLeft > 0 && s.tiles.isNotEmpty(), compact = true)
            }
        },
    ) {
        val p = LocalPalette.current
        val measurer = rememberTextMeasurer()
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(s) {
                    detectTapGestures { pos -> tap(MahjongGeometry(s.tiles, initialTiles, size.width.toFloat(), size.height.toFloat()).hit(pos)) }
                },
        ) {
            drawRect(Brush.verticalGradient(listOf(p.felt, p.feltDark.darker(0.1f))))
            if (s.tiles.isEmpty()) return@Canvas
            val g = MahjongGeometry(s.tiles, initialTiles, size.width, size.height)
            for (t in g.order) {
                val r = g.rect(t)
                drawMahjongTile(
                    t.face, r.topLeft, r.size, g.depth, p, measurer,
                    selected = t.id == s.selected, hinted = t.id in hint,
                    dimmed = dimBlocked && t.id !in free,
                )
            }
        }
    }
}
