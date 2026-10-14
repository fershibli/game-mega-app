package io.github.fershibli.megagames.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Top bar: centered title and the gear on the right (left in left-handed mode). In one-hand
 * mode the gear moves to a floating bubble near the thumb instead, see [FloatingGear].
 */
@Composable
fun TopBar(title: String, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val s = LocalSettings.current
    Box(
        modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(p.surface)
            .padding(horizontal = 8.dp),
    ) {
        Text(
            title,
            Modifier.align(Alignment.Center).padding(horizontal = 68.dp),
            style = MaterialTheme.typography.headlineSmall,
            color = p.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!s.oneHand) {
            RoundIconButton(
                AppIcon.GEAR, stringResource(R.string.settings), onSettings,
                Modifier.align(if (s.leftHanded) Alignment.CenterStart else Alignment.CenterEnd),
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.outline.copy(alpha = if (p.strongOutlines) 1f else 0.3f)))
}

/**
 * One-hand mode gear: a floating bubble docked on the thumb side. After a few seconds it tucks
 * itself half into the screen edge; a tap brings it back, a second tap opens Settings. It can be
 * dragged up and down along the edge.
 */
@Composable
fun FloatingGear(onSettings: () -> Unit) {
    val s = LocalSettings.current
    if (!s.oneHand) return
    val p = LocalPalette.current
    val density = LocalDensity.current
    val label = stringResource(R.string.settings)
    var tucked by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var dragY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(interaction) {
        tucked = false
        delay(3500)
        tucked = true
    }
    val bubble = 64.dp
    val tuck by animateFloatAsState(if (tucked) 1f else 0f, label = "tuck")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxDrag = with(density) { (maxHeight * 0.6f).toPx() }
        val bubblePx = with(density) { bubble.toPx() }
        val shift = (bubblePx * 0.55f * tuck).roundToInt() * (if (s.leftHanded) -1 else 1)
        Box(
            Modifier
                .align(if (s.leftHanded) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(bottom = 140.dp, start = 10.dp, end = 10.dp)
                .offset { IntOffset(shift, dragY.roundToInt()) }
                .size(bubble)
                .alpha(1f - 0.35f * tuck)
                .shadow(8.dp, CircleShape)
                .background(p.primary, CircleShape)
                .border(2.dp, p.onPrimary.copy(alpha = 0.6f), CircleShape)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragStart = { interaction++ }) { change, amount ->
                        change.consume()
                        dragY = (dragY + amount).coerceIn(-maxDrag, 0f)
                    }
                }
                .clickable(role = Role.Button) {
                    if (tucked) interaction++ else onSettings()
                }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            IconView(AppIcon.GEAR, p.onPrimary, size = 34.dp)
        }
    }
}

/**
 * Scroll-wheel selector (like an alarm clock picker): the middle row is the selection. Rows can
 * also simply be tapped. Used for the difficulty choice.
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val rowHeight = 76.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val scope = rememberCoroutineScope()
    val fling = rememberSnapFlingBehavior(state)
    val center by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: selected
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { center }.distinctUntilChanged().collect { onSelected(it) }
    }
    Box(modifier.height(rowHeight * 3), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .background(p.primary, RoundedCornerShape(20.dp)),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = rowHeight),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(items) { index, label ->
                val isCenter = index == center
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .graphicsLayer { alpha = if (isCenter) 1f else 0.55f }
                        .clickable(role = Role.RadioButton) { scope.launch { state.animateScrollToItem(index) } }
                        .semantics { this.selected = isCenter },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCenter) p.onPrimary else p.text,
                    )
                }
            }
        }
    }
}

/** Plus/minus stepper used for text and piece size. */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean, plusEnabled: Boolean) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(AppIcon.MINUS, stringResource(R.string.decrease), onMinus, enabled = minusEnabled)
        Text(value, Modifier.width(88.dp), style = MaterialTheme.typography.titleLarge, color = p.text, textAlign = TextAlign.Center)
        RoundIconButton(AppIcon.PLUS, stringResource(R.string.increase), onPlus, enabled = plusEnabled, filled = true)
        Spacer(Modifier.width(4.dp))
    }
}
