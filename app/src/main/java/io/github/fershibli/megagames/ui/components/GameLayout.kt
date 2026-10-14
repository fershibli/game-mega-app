package io.github.fershibli.megagames.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer

/**
 * Standard game layout: a status line, the zoomable board in the middle and the controls at the
 * bottom (where the thumb is).
 */
@Composable
fun GameLayout(
    modifier: Modifier = Modifier,
    zoom: ZoomState = rememberZoomState(),
    status: (@Composable () -> Unit)? = null,
    controls: (@Composable ColumnScope.() -> Unit)? = null,
    board: @Composable BoxScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(modifier.fillMaxSize()) {
        if (status != null) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) { status() }
        }
        ZoomableBoard(Modifier.weight(1f).fillMaxWidth(), zoom, board)
        if (controls != null) {
            Column(
                Modifier.fillMaxWidth().background(p.surface).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = controls,
            )
        }
    }
}

/**
 * Loads a game's state: restored from the autosave when available, otherwise created by
 * [create] on a background thread (puzzle generation can take a moment). Every change to the
 * state is autosaved.
 */
@Composable
fun <T : Any> LoadGame(
    host: GameHost,
    serializer: KSerializer<T>,
    create: suspend () -> T,
    content: @Composable (MutableState<T>) -> Unit,
) {
    var loaded by remember(host) { mutableStateOf<MutableState<T>?>(null) }
    LaunchedEffect(host) {
        val initial = host.restore(serializer) ?: withContext(Dispatchers.Default) { create() }
        val state = mutableStateOf(initial)
        loaded = state
        snapshotFlow { state.value }.collect { host.save(serializer, it) }
    }
    val state = loaded
    if (state == null) LoadingBox(Modifier.fillMaxSize()) else content(state)
}
