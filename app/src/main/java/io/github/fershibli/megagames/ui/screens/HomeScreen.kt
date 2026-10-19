package io.github.fershibli.megagames.ui.screens

import io.github.fershibli.megagames.core.catalogOrder
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.core.ChoiceStore
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
import androidx.compose.foundation.lazy.grid.LazyGridState
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

/**
 * What the home screen lists: games grouped under category headings ([grouped]), or one single
 * list ([all]), each in its own order.
 */
data class HomeOrder(
    val categories: List<GameCategory>,
    val gamesByCategory: Map<GameCategory, List<GameId>>,
    val all: List<GameId>,
    val grouped: Boolean = true,
) {
    fun games(category: GameCategory): List<GameId> = gamesByCategory[category].orEmpty()

    companion object {
        /** The order of the catalog itself, used when "recently played first" is off. */
        val Default = HomeOrder(GameCategory.entries, GameCategory.entries.associateWith { c -> GameId.entries.filter { it.category == c } }, catalogOrder())

        /** Most recently played category and games first. */
        fun recent(store: ChoiceStore) = HomeOrder(store.categories(), GameCategory.entries.associateWith { store.gamesIn(it) }, store.allGames())

        /** The home screen for these settings. */
        fun of(settings: Settings, store: ChoiceStore) =
            (if (settings.recentFirst) recent(store) else Default).copy(grouped = settings.showCategories)
    }
}

/** Number of times the list of games repeats in each direction; effectively endless. */
private const val CYCLES = 1000

/**
 * Game chooser: two columns of square, text-free thumbnails grouped under category headings
 * (cards, board games, puzzles...), scrolling endlessly in both directions (the list wraps
 * around). Each thumbnail still has a spoken name for TalkBack.
 */
@Composable
fun HomeScreen(onGame: (GameId) -> Unit, onSettings: () -> Unit, order: HomeOrder = HomeOrder.Default) {
    // Each category heading followed by its games (categories without games are left out), or
    // simply every game when the categories are turned off.
    val entries: List<Any> = remember(order) {
        if (!order.grouped) return@remember order.all
        order.categories.flatMap { category ->
            val games = order.games(category)
            if (games.isEmpty()) emptyList() else listOf(category) + games
        }
    }
    val p = LocalPalette.current
    // Starts at the beginning of a cycle, so the first category (or game) is at the top; a new order starts over.
    val state = remember(entries) { LazyGridState(firstVisibleItemIndex = entries.size * CYCLES / 2) }
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
