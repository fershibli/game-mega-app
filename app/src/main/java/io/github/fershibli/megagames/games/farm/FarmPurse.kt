package io.github.fershibli.megagames.games.farm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.ColorMode
import io.github.fershibli.megagames.ui.art.PixelArt
import io.github.fershibli.megagames.ui.art.adaptedTo
import io.github.fershibli.megagames.ui.art.drawFitted
import io.github.fershibli.megagames.ui.components.StatusPill
import io.github.fershibli.megagames.ui.theme.LocalPalette

/**
 * The purse at the top of the farm: a coin and a star with their amounts, instead of the words.
 * Screen readers hear the words ("Coins: 86, Stars: 8"); the pictures are decoration.
 */
@Composable
fun FarmPurse(coins: Int, stars: Int, modifier: Modifier = Modifier) {
    val spoken = stringResource(R.string.farm_coins, coins) + ", " + stringResource(R.string.farm_stars, stars)
    StatusPill(modifier.testTag("farm-status").clearAndSetSemantics { contentDescription = spoken }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Amount(FarmSprites.coin, coins, "farm-coins")
            Spacer(Modifier.width(18.dp))
            Amount(FarmSprites.star, stars, "farm-stars")
        }
    }
}

@Composable
private fun Amount(art: PixelArt, value: Int, tag: String) {
    val p = LocalPalette.current
    val style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    // The picture follows the text size (sp), a little taller than the digits, and sits on the baseline.
    val side = with(LocalDensity.current) { style.fontSize.toDp() * 1.1f }
    // Gold stays bright on the dark pill; only high contrast on a light pill is pushed further.
    val shown = if (p.mode == ColorMode.HIGH_CONTRAST && !p.isDark) art.adaptedTo(p) else art
    Row(verticalAlignment = Alignment.Top) {
        Canvas(Modifier.size(side).alignBy { it.measuredHeight }.testTag("$tag-icon")) { drawFitted(shown) }
        Spacer(Modifier.width(6.dp))
        Text(value.toString(), Modifier.alignByBaseline().testTag("$tag-value"), color = p.text, style = style, maxLines = 1)
    }
}
