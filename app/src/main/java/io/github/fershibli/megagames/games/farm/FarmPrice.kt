package io.github.fershibli.megagames.games.farm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.fershibli.megagames.core.ColorMode
import io.github.fershibli.megagames.ui.art.adaptedTo
import io.github.fershibli.megagames.ui.theme.LocalPalette

/** Marks where the coin picture goes in a price text: see [price]. */
private const val COIN_MARK = '\uE000'

/** A price for a "shown" string: the coin picture goes before [amount] (a number, "+25"...). */
fun price(amount: Any): String = "$COIN_MARK$amount"

/**
 * Text with the coin picture in place of the word "coins": [text] is a "shown" string formatted
 * with [price]. The picture is as tall as the letters and moves with them at any text size.
 * Screen readers hear [description] (the same sentence in words); the picture is decoration.
 */
@Composable
fun PricedText(
    text: String,
    description: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    shrink: Boolean = false,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    val p = LocalPalette.current
    val coin = if (p.mode == ColorMode.HIGH_CONTRAST && !p.isDark) FarmSprites.coin.adaptedTo(p) else FarmSprites.coin
    val content = remember(text) {
        val at = text.indexOf(COIN_MARK)
        buildAnnotatedString {
            append(text.substring(0, maxOf(at, 0)))
            if (at >= 0) {
                appendInlineContent("coin", "¤")
                // A word joiner keeps the number on the same line as its coin.
                append('\u2060')
                append(text.substring(at + 1))
            }
        }
    }
    // The picture is a little taller than the letters, with a gap on its right; both scale with the text.
    val inline = mapOf(
        "coin" to InlineTextContent(Placeholder(1.35.em, 1.1.em, PlaceholderVerticalAlign.TextCenter)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Canvas(Modifier.fillMaxHeight().aspectRatio(1f).testTag("farm-price-coin")) {
                    // Next to text the picture follows the letters exactly, so squares may be fractions of a pixel.
                    coin.draw(this, 0f, 0f, minOf(size.width / coin.width, size.height / coin.height))
                }
            }
        },
    )
    BasicText(
        content,
        modifier.semantics { contentDescription = description },
        style = style.copy(color = color, textAlign = textAlign),
        maxLines = maxLines,
        inlineContent = inline,
        autoSize = if (shrink) TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = style.fontSize, stepSize = 1.sp) else null,
    )
}
