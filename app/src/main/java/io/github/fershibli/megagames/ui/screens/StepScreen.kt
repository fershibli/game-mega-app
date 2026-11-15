package io.github.fershibli.megagames.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.ui.art.GameThumbnail
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.FloatingGear
import io.github.fershibli.megagames.ui.components.TopBar
import io.github.fershibli.megagames.ui.theme.LocalPalette

/**
 * One step of the way from the menu to a game: the game's picture on top, and below it a panel
 * with a single question ([content]) and its buttons ([actions]). Each step asks one thing, so
 * there is little to read at a time, and everything you touch sits in the lower half of the screen.
 *
 * The panel takes the height it needs and the picture the rest; on a small screen with big text
 * the picture gives way first, then the question scrolls ([scrollable]; a wheel scrolls by itself).
 */
@Composable
internal fun StepScreen(
    game: GameId,
    onSettings: () -> Unit,
    actions: @Composable () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(game.title), onSettings)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                val side = min(min(maxWidth, maxHeight), 220.dp)
                if (side >= 72.dp) {
                    Box(
                        Modifier
                            .size(side)
                            .aspectRatio(1f)
                            .shadow(6.dp, RoundedCornerShape(22.dp))
                            .clip(RoundedCornerShape(22.dp))
                            .background(p.surface),
                    ) { GameThumbnail(game, Modifier.fillMaxSize()) }
                }
            }
            Column(
                Modifier.fillMaxWidth().background(p.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth().then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content,
                )
                ActionBar { actions() }
            }
        }
        FloatingGear(onSettings)
    }
}
