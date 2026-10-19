package io.github.fershibli.megagames.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.fershibli.megagames.BuildConfig
import io.github.fershibli.megagames.R
import io.github.fershibli.megagames.core.AppLanguage
import io.github.fershibli.megagames.core.ColorMode
import io.github.fershibli.megagames.core.Difficulty
import io.github.fershibli.megagames.core.OneHandZoom
import io.github.fershibli.megagames.core.Settings
import io.github.fershibli.megagames.ui.components.ActionBar
import io.github.fershibli.megagames.ui.components.AppIcon
import io.github.fershibli.megagames.ui.components.BigButton
import io.github.fershibli.megagames.ui.components.BigDialog
import io.github.fershibli.megagames.ui.components.ButtonStyle
import io.github.fershibli.megagames.ui.components.RoundIconButton
import io.github.fershibli.megagames.ui.components.Stepper
import io.github.fershibli.megagames.ui.theme.LocalPalette
import io.github.fershibli.megagames.ui.theme.LocalSettings
import io.github.fershibli.megagames.ui.theme.Palette
import kotlin.math.roundToInt

/** Extra actions shown when Settings is opened from inside a game. */
class InGameActions(val onResume: () -> Unit, val onNewGame: () -> Unit, val onMainMenu: () -> Unit)

const val PROJECT_URL = "https://github.com/fershibli/game-mega-app"

/** Opens the project page (source code and documentation) in the browser. The app itself never connects. */
private fun openProject(context: Context) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** "Why Mili Games exists": the promise (free, no ads, ever) and how the app was made. */
@Composable
internal fun MissionDialog(onClose: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = p.surface,
        title = { Text(stringResource(R.string.mission_title), style = MaterialTheme.typography.headlineSmall, color = p.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (id in listOf(R.string.mission_why, R.string.mission_promise, R.string.mission_ai, R.string.mission_open)) {
                    Text(stringResource(id), style = MaterialTheme.typography.bodyLarge, color = p.text)
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(stringResource(R.string.close), onClose, Modifier.fillMaxWidth())
                BigButton(stringResource(R.string.mission_source), { openProject(context) }, Modifier.fillMaxWidth(), style = ButtonStyle.SECONDARY)
            }
        },
    )
}

/**
 * Settings. The list starts below a tall header so that the first options are already in the
 * lower half of the screen, and every other option can be scrolled down towards the thumb.
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onClose: () -> Unit,
    inGame: InGameActions?,
) {
    val p = LocalPalette.current
    val left = LocalSettings.current.leftHanded
    var confirmExit by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    var pickLanguage by remember { mutableStateOf(false) }
    var showMission by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(p.background)) {
        Box(Modifier.fillMaxWidth().height(68.dp).background(p.surface).padding(horizontal = 8.dp)) {
            Text(
                stringResource(R.string.settings), Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.headlineSmall, color = p.text,
            )
            RoundIconButton(
                AppIcon.CLOSE, stringResource(R.string.close), inGame?.onResume ?: onClose,
                Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd),
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val headerSpace = maxHeight * 0.3f
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(headerSpace), contentAlignment = Alignment.BottomCenter) {
                    Text(
                        stringResource(R.string.settings_scroll_hint), style = MaterialTheme.typography.bodyMedium,
                        color = p.textMuted, modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                Section(stringResource(R.string.section_language)) {
                    val current = if (settings.language == AppLanguage.SYSTEM) stringResource(R.string.language_system) else settings.language.nativeName
                    BigButton(current, { pickLanguage = true }, Modifier.fillMaxWidth(), style = ButtonStyle.SECONDARY)
                }

                Section(stringResource(R.string.section_size)) {
                    ScaleRow(stringResource(R.string.text_size), settings.fontScale) { onChange(settings.copy(fontScale = it)) }
                    ScaleRow(stringResource(R.string.piece_size), settings.pieceScale) { onChange(settings.copy(pieceScale = it)) }
                }

                Section(stringResource(R.string.section_colors)) {
                    ColorMode.entries.forEach { mode ->
                        ColorModeOption(mode, settings.colorMode == mode, settings.darkMode) { onChange(settings.copy(colorMode = mode)) }
                    }
                    ToggleRow(stringResource(R.string.dark_mode), stringResource(R.string.dark_mode_desc), settings.darkMode) {
                        onChange(settings.copy(darkMode = it))
                    }
                }

                Section(stringResource(R.string.section_controls)) {
                    ToggleRow(stringResource(R.string.pinch_zoom), stringResource(R.string.pinch_zoom_desc), settings.pinchZoom) {
                        onChange(settings.copy(pinchZoom = it))
                    }
                    ToggleRow(stringResource(R.string.left_handed), stringResource(R.string.left_handed_desc), settings.leftHanded) {
                        onChange(settings.copy(leftHanded = it))
                    }
                    ToggleRow(stringResource(R.string.one_hand), stringResource(R.string.one_hand_desc), settings.oneHand) {
                        onChange(settings.copy(oneHand = it))
                    }
                    if (settings.oneHand) {
                        Text(stringResource(R.string.one_hand_zoom), style = MaterialTheme.typography.titleMedium, color = p.text)
                        OneHandZoom.entries.forEach { z ->
                            RadioCard(stringResource(z.label), stringResource(z.description), settings.oneHandZoom == z) {
                                onChange(settings.copy(oneHandZoom = z))
                            }
                        }
                    }
                }

                Section(stringResource(R.string.default_difficulty)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Difficulty.entries.forEach { d ->
                            BigButton(
                                stringResource(d.label), { onChange(settings.copy(defaultDifficulty = d)) },
                                Modifier.weight(1f), compact = true,
                                style = if (settings.defaultDifficulty == d) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY,
                            )
                        }
                    }
                }

                Section(stringResource(R.string.section_about)) {
                    Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyLarge, color = p.text)
                    Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
                    Text(stringResource(R.string.about_ai), style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
                    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
                    val context = LocalContext.current
                    BigButton(stringResource(R.string.about_open_project), { openProject(context) }, Modifier.fillMaxWidth(), style = ButtonStyle.SECONDARY)
                    BigButton(stringResource(R.string.reset_settings), { onChange(Settings()) }, Modifier.fillMaxWidth(), style = ButtonStyle.SECONDARY)
                    BigButton(stringResource(R.string.mission_button), { showMission = true }, Modifier.fillMaxWidth(), icon = AppIcon.INFO, style = ButtonStyle.SECONDARY)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        Column(Modifier.fillMaxWidth().background(p.surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (inGame != null) {
                ActionBar {
                    BigButton(stringResource(R.string.resume), inGame.onResume, icon = AppIcon.CHECK)
                    BigButton(stringResource(R.string.new_game), { confirmNew = true }, icon = AppIcon.NEW_GAME, style = ButtonStyle.SECONDARY)
                }
                BigButton(stringResource(R.string.main_menu), { confirmExit = true }, Modifier.fillMaxWidth(), icon = AppIcon.HOME, style = ButtonStyle.DANGER)
            } else {
                BigButton(stringResource(R.string.done), onClose, Modifier.fillMaxWidth(), icon = AppIcon.CHECK)
            }
        }
    }

    if (showMission) MissionDialog(onClose = { showMission = false })

    if (pickLanguage) {
        BigDialog(
            title = stringResource(R.string.section_language),
            message = null,
            onDismiss = { pickLanguage = false },
            buttons = AppLanguage.entries.map { lang ->
                val label = if (lang == AppLanguage.SYSTEM) stringResource(R.string.language_system) else lang.nativeName
                (if (lang == settings.language) "✓ $label" else label) to {
                    onChange(settings.copy(language = lang))
                    pickLanguage = false
                }
            },
            primaryIndex = AppLanguage.entries.indexOf(settings.language),
        )
    }
    if (confirmExit && inGame != null) {
        BigDialog(
            title = stringResource(R.string.leave_game_title),
            message = stringResource(R.string.leave_game_message),
            onDismiss = { confirmExit = false },
            buttons = listOf(
                stringResource(R.string.leave_game_yes) to { confirmExit = false; inGame.onMainMenu() },
                stringResource(R.string.cancel) to { confirmExit = false },
            ),
        )
    }
    if (confirmNew && inGame != null) {
        BigDialog(
            title = stringResource(R.string.new_game_title),
            message = stringResource(R.string.new_game_message),
            onDismiss = { confirmNew = false },
            buttons = listOf(
                stringResource(R.string.new_game) to { confirmNew = false; inGame.onNewGame() },
                stringResource(R.string.cancel) to { confirmNew = false },
            ),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = p.surface,
        border = BorderStroke(if (p.strongOutlines) 2.dp else 1.dp, p.outline.copy(alpha = if (p.strongOutlines) 1f else 0.3f)),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = p.primary)
            content()
        }
    }
}

@Composable
private fun ScaleRow(label: String, value: Float, onValue: (Float) -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = p.text)
        val pct = (value * 100).roundToInt()
        Stepper(
            "$pct%",
            onMinus = { onValue(((value - Settings.SCALE_STEP) * 10).roundToInt() / 10f) },
            onPlus = { onValue(((value + Settings.SCALE_STEP) * 10).roundToInt() / 10f) },
            minusEnabled = value > Settings.SCALE_MIN + 0.01f,
            plusEnabled = value < Settings.SCALE_MAX - 0.01f,
        )
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .toggleable(checked, role = Role.Switch, onValueChange = onChecked)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = p.text)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = null, modifier = Modifier.scale(1.25f).padding(end = 6.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onPrimary, checkedTrackColor = p.primary,
                uncheckedThumbColor = p.outline, uncheckedTrackColor = p.surfaceAlt, uncheckedBorderColor = p.outline,
            ),
        )
    }
}

@Composable
private fun RadioCard(title: String, description: String?, selected: Boolean, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) p.primary.copy(alpha = 0.14f) else Color.Transparent)
            .border(if (selected) 3.dp else 1.dp, if (selected) p.primary else p.outline.copy(alpha = 0.5f), shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).border(3.dp, if (selected) p.primary else p.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(13.dp).background(p.primary, CircleShape)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = p.text, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = p.textMuted)
        }
        if (leading != null) {
            Spacer(Modifier.width(8.dp))
            leading()
        }
    }
}

@Composable
private fun ColorModeOption(mode: ColorMode, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val preview = Palette.of(mode, dark)
    RadioCard(stringResource(mode.label), stringResource(mode.description), selected, leading = {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(preview.background, preview.primary, preview.pieces[0], preview.pieces[1], preview.pieces[2]).forEach {
                Box(Modifier.size(width = 12.dp, height = 34.dp).clip(RoundedCornerShape(4.dp)).background(it).border(1.dp, preview.outline, RoundedCornerShape(4.dp)))
            }
        }
    }, onClick = onClick)
}
