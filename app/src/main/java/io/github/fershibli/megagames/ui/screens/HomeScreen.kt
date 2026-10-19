package io.github.fershibli.megagames.ui.screens

import io.github.fershibli.megagames.core.GameCategory
import androidx.compose.ui.semantics.heading
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.ui.art.GameThumbnail
import io.github.fershibli.megagames.ui.components.FloatingGear
import io.github.fershibli.megagames.ui.components.TopBar
import io.github.fershibli.megagames.ui.theme.LocalPalette

/** Number of times the list of games repeats in each direction; effectively endless. */
private const val CYCLES = 1000

/**
 * Game chooser: two columns of square, text-free thumbnails grouped under category headings
 * (cards, board games, puzzles...), scrolling endlessly in both directions (the list wraps
 * around). Each thumbnail still has a spoken name for TalkBack.
 */
@Composable
fun HomeScreen(onGame: (GameId) -> Unit, onSettings: () -> Unit) {
    // Each category heading followed by its games; categories without games are left out.
    val entries: List<Any> = remember {
        GameCategory.entries.flatMap { category ->
            val games = GameId.entries.filter { it.category == category }
            if (games.isEmpty()) emptyList() else listOf(category) + games
        }
    }
    val p = LocalPalette.current
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = entries.size * CYCLES / 2)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.choose_game), onSettings)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = state,
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    count = entries.size * CYCLES,
                    key = { it },
                    span = { if (entries[it % entries.size] is GameCategory) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                ) { index ->
                    val entry = entries[index % entries.size]
                    if (entry is GameCategory) {
                        Text(
                            stringResource(entry.label),
                            Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp).semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall, color = p.text,
                        )
                        return@items
                    }
                    val game = entry as GameId
                    val name = stringResource(game.title)
                    val shape = RoundedCornerShape(22.dp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .shadow(6.dp, shape)
                            .clip(shape)
                            .background(p.surface)
                            .border(if (p.strongOutlines) 3.dp else 1.dp, p.outline.copy(alpha = if (p.strongOutlines) 1f else 0.35f), shape)
                            .clickable(role = Role.Button) { onGame(game) }
                            .semantics { contentDescription = name },
                    ) {
                        GameThumbnail(game, Modifier.fillMaxSize())
                    }
                }
            }
        }
        FloatingGear(onSettings)
    }
}
