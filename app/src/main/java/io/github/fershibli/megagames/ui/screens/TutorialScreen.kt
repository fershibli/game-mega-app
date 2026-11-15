package io.github.fershibli.megagames.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.GameHost
import io.github.fershibli.megagames.core.GameId
import io.github.fershibli.megagames.core.Motion
import io.github.fershibli.megagames.core.Tutorial
import io.github.fershibli.megagames.core.TutorialStep
import io.github.fershibli.megagames.games.GameContent
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.IconView
import io.github.fershibli.megagames.ui.components.LocalTutorial
import io.github.fershibli.megagames.ui.components.TopBar
import io.github.fershibli.megagames.ui.components.TutorialGuide
import io.github.fershibli.megagames.ui.components.TutorialOverlay
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.ProvideGameLanguage
import io.github.fershibli.megagames.ui.theme.currentMotion
import io.github.fershibli.megagames.ui.theme.currentPace
import io.github.fershibli.megagames.ui.theme.wait
import kotlinx.coroutines.flow.first

/**
 * A lesson on the game's own board: one short sentence at a time on top, the real game in the
 * middle and "Next" at the bottom. In a "watch" step a hand plays the move; in a "your turn" step
 * the place to touch is ringed and nothing else answers, and after two touches elsewhere the hand
 * shows where. Nothing is saved: the game in progress, if any, is left alone.
 */
@Composable
fun TutorialScreen(
    game: GameId,
    lesson: Tutorial,
    onExit: () -> Unit,
    onRules: () -> Unit,
    onSettings: () -> Unit,
    language: AppLanguage? = null,
    guide: TutorialGuide = remember { TutorialGuide() },
) {
    val p = LocalPalette.current
    val pace = currentPace
    val motion = currentMotion
    val host = remember { GameHost(game, Difficulty.EASY, lesson.state(), null) {}.also { it.language = language } }
    var index by remember { mutableIntStateOf(0) }
    val step = lesson.steps[index]
    // Whether "Next" may be pressed: at once when there is only something to read, otherwise after the move.
    var ready by remember { mutableStateOf(false) }
    val hand = remember { Animatable(0f) }
    var press by remember { mutableStateOf(false) }
    val pulse by rememberInfiniteTransition(label = "tutorial").animateFloat(0f, if (motion == Motion.NONE) 0f else 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "ring")

    suspend fun reach() {
        if (motion == Motion.NONE) hand.snapTo(1f) else hand.animateTo(1f, tween((900 * motion.duration).toInt()))
    }
    LaunchedEffect(index) {
        guide.reset()
        hand.snapTo(0f)
        press = false
        when (step.kind) {
            TutorialStep.Kind.READ -> ready = true
            TutorialStep.Kind.SHOW -> {
                ready = false
                pace.wait(900)
                reach()
                press = true
                pace.wait(350)
                guide.perform?.invoke(step.target!!)
                press = false
                pace.wait(700)
                hand.snapTo(0f)
                ready = true
            }
            TutorialStep.Kind.TRY -> {
                ready = false
                guide.expected = step.target
                snapshotFlow { guide.done }.first { it }
                hand.snapTo(0f)
                ready = true
            }
        }
    }
    // Two touches somewhere else: the hand goes to the place and waits there.
    LaunchedEffect(index, guide.misses >= 2) {
        if (step.kind == TutorialStep.Kind.TRY && guide.misses >= 2 && !guide.done) reach()
    }

    Column(Modifier.fillMaxSize().background(p.background)) {
        TopBar(stringResource(game.title), onSettings)
        Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), shape = RoundedCornerShape(18.dp), color = p.surface, shadowElevation = 2.dp) {
            Column(Modifier.heightIn(min = 104.dp).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tutorial_progress, index + 1, lesson.steps.size), style = MaterialTheme.typography.titleMedium, color = p.textMuted)
                    val done = step.kind == TutorialStep.Kind.TRY && ready
                    val label = when {
                        done -> stringResource(R.string.tutorial_well_done)
                        step.kind == TutorialStep.Kind.TRY -> stringResource(R.string.your_turn)
                        step.kind == TutorialStep.Kind.SHOW -> stringResource(R.string.tutorial_watch)
                        else -> null
                    }
                    if (done) IconView(AppIcon.CHECK, p.primary, size = 22.dp)
                    if (label != null) Text(label, style = MaterialTheme.typography.titleMedium, color = p.primary)
                }
                // The lesson is read in the language the game will be played in.
                ProvideGameLanguage(language) {
                    Text(stringResource(step.caption), Modifier.testTag("tutorial-caption"), style = MaterialTheme.typography.titleMedium, color = p.text)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CompositionLocalProvider(LocalTutorial provides guide) {
                ProvideGameLanguage(language) { GameContent(game, host) }
            }
            TutorialOverlay(guide, step.target, hand.value, press, pulse, Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxWidth().background(p.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
            ActionBar {
                val last = index == lesson.steps.lastIndex
                BigButton(
                    stringResource(if (last) R.string.done else R.string.tutorial_next), { if (last) onExit() else index++ },
                    icon = if (last) AppIcon.CHECK else AppIcon.NEXT, enabled = ready,
                )
                BigButton(stringResource(R.string.tutorial_rules), onRules, icon = AppIcon.INFO, style = ButtonStyle.SECONDARY)
                BigButton(stringResource(R.string.close), onExit, icon = AppIcon.CLOSE, style = ButtonStyle.SECONDARY)
            }
        }
    }
}
