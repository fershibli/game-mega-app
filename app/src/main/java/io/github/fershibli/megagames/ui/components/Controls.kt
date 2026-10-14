package io.github.fershibli.megagames.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings

/** Minimum comfortable touch target for the app (bigger than the 48dp guideline). */
val TouchTarget = 56.dp

enum class ButtonStyle { PRIMARY, SECONDARY, DANGER }

/** Large rounded button with a subtle vertical gradient so it reads as "pressable". */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    style: ButtonStyle = ButtonStyle.PRIMARY,
    enabled: Boolean = true,
    compact: Boolean = false,
    bigText: Boolean = false,
) {
    val p = LocalPalette.current
    val (bg, fg) = when (style) {
        ButtonStyle.PRIMARY -> p.primary to p.onPrimary
        ButtonStyle.SECONDARY -> p.surfaceAlt to p.text
        ButtonStyle.DANGER -> p.danger to Color.White
    }
    val shape = RoundedCornerShape(18.dp)
    val alpha = if (enabled) 1f else 0.4f
    val border = if (p.strongOutlines || style == ButtonStyle.SECONDARY) BorderStroke(if (p.strongOutlines) 2.dp else 1.dp, p.outline) else null
    Box(
        modifier
            .defaultMinSize(minHeight = if (compact) 52.dp else TouchTarget, minWidth = TouchTarget)
            .shadow(if (enabled) 3.dp else 0.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(bg, Color.White, 0.12f).copy(alpha = alpha), bg.copy(alpha = alpha))))
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                IconView(icon, fg.copy(alpha = alpha), size = 26.dp)
                if (text.isNotEmpty()) Spacer(Modifier.width(10.dp))
            }
            if (text.isNotEmpty()) {
                val style = if (bigText) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.labelLarge
                // Text shrinks to fit narrow buttons instead of breaking a word in the middle.
                BasicText(
                    text,
                    style = style.copy(color = fg.copy(alpha = alpha), textAlign = TextAlign.Center),
                    maxLines = if (' ' in text.trim()) 2 else 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = style.fontSize, stepSize = 1.sp),
                )
            }
        }
    }
}

/** Keyboard key: big letter, no inner padding so even "W" fits on narrow phones. */
@Composable
fun KeyButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(p.primary, Color.White, 0.12f), p.primary)))
            .then(if (p.strongOutlines) Modifier.border(2.dp, p.outline, shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = p.onPrimary, style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
    }
}

/** Round icon-only button (gear, undo...). Always has an accessible description. */
@Composable
fun RoundIconButton(
    icon: AppIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = TouchTarget,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val p = LocalPalette.current
    val bg = if (filled) p.primary else p.surface
    val fg = if (filled) p.onPrimary else p.text
    Box(
        modifier
            .size(size)
            .shadow(if (filled) 4.dp else 1.dp, CircleShape)
            .clip(CircleShape)
            .background(bg)
            .border(if (p.strongOutlines) 2.dp else 1.dp, p.outline.copy(alpha = if (p.strongOutlines) 1f else 0.5f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        IconView(icon, fg.copy(alpha = if (enabled) 1f else 0.35f), size = size * 0.5f)
    }
}

/** Equal-width action bar. Main action first; mirrored automatically for handedness. */
@Composable
fun ActionBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val left = LocalSettings.current.leftHanded
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 10.dp.roundToPx()
        val n = measurables.size.coerceAtLeast(1)
        val each = ((constraints.maxWidth - gap * (n - 1)) / n).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = each, maxWidth = each, minHeight = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { i, pl ->
                val slot = if (left) i else n - 1 - i
                pl.placeRelative(slot * (each + gap), (height - pl.height) / 2)
            }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(56.dp), color = LocalPalette.current.primary, strokeWidth = 6.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.loading), style = MaterialTheme.typography.titleMedium, color = LocalPalette.current.text)
    }
}

/** Dialog with big, stacked buttons (easier to hit than Material's small text buttons). */
@Composable
fun BigDialog(
    title: String,
    message: String?,
    onDismiss: () -> Unit,
    buttons: List<Pair<String, () -> Unit>>,
    dismissible: Boolean = true,
    primaryIndex: Int = 0,
) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        containerColor = p.surface,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = p.text) },
        text = if (message == null) null else ({ Text(message, style = MaterialTheme.typography.bodyLarge, color = p.text) }),
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                buttons.forEachIndexed { i, (label, action) ->
                    BigButton(label, action, Modifier.fillMaxWidth(), style = if (i == primaryIndex) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY)
                }
            }
        },
    )
}

/** A small rounded "chip" for status text such as "Your turn" or a score. */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val p = LocalPalette.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (emphasized) p.primary else p.surfaceAlt,
        border = if (p.strongOutlines) BorderStroke(2.dp, p.outline) else null,
    ) {
        Text(
            text, Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            color = if (emphasized) p.onPrimary else p.text, style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center, maxLines = 2,
        )
    }
}
