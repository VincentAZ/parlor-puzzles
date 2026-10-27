package com.parlor.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.parlor.core.design.roomColors
import com.parlor.core.design.roomTexture
import com.parlor.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ThemesScreen(settings: PlayerSettings, pro: Boolean, systemDark: Boolean,
                 onTheme: suspend (RoomTheme) -> Unit, onAppearance: suspend (Appearance) -> Unit,
                 onPro: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun save(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = false
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    BackHandler(onBack = onBack)
    val dark = settings.appearance.isDark(systemDark)
    val displayed = RoomTheme.displayed(settings.theme, true)
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.library_back)) }
        Text(stringResource(R.string.library_themes), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.library_themes_body))
        Text(stringResource(R.string.library_appearance), style = MaterialTheme.typography.titleLarge)
        Column(Modifier.selectableGroup()) {
            Appearance.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("appearance-${option.name}")
                    .selectable(selected = option == settings.appearance, enabled = !busy, role = Role.RadioButton,
                        onClick = { save { onAppearance(option) } }).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = option == settings.appearance, onClick = null, enabled = !busy)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(when (option) {
                        Appearance.LIGHT -> R.string.library_appearance_light
                        Appearance.DARK -> R.string.library_appearance_dark
                        Appearance.SYSTEM -> R.string.library_appearance_system
                    }))
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("theme-saving"))
        if (error) Text(stringResource(R.string.library_theme_save_error), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("theme-error").semantics { liveRegion = LiveRegionMode.Polite })
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RoomTheme.entries.forEach { theme ->
                val selected = displayed == theme
                val available = true
                val state = stringResource(when {
                    selected -> R.string.library_theme_selected
                    !available -> R.string.library_theme_pro
                    else -> R.string.library_theme_available
                })
                OutlinedCard(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).testTag("theme-${theme.id}")
                    .selectable(selected = selected, enabled = !busy, role = Role.RadioButton,
                        onClick = { save { onTheme(theme) } })
                    .semantics(mergeDescendants = true) { stateDescription = state }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, onClick = null, enabled = !busy)
                            Spacer(Modifier.width(10.dp))
                            Text(themeName(theme), style = MaterialTheme.typography.titleLarge)
                        }
                        ThemePreview(theme, dark)
                        Text(themeDescription(theme))
                        Text(state, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePreview(theme: RoomTheme, dark: Boolean) {
    val colors = remember(theme, dark) { roomColors(theme, dark) }
    Canvas(Modifier.fillMaxWidth().height(88.dp).clip(MaterialTheme.shapes.medium)
        .background(colors.scheme.background).roomTexture(colors.board).clearAndSetSemantics {}) {
        val gap = 8.dp.toPx(); val edge = size.height - gap * 2
        val w = (size.width - gap * 5) / 4
        listOf(colors.board.tile, colors.board.wood, colors.board.gold, colors.board.target).forEachIndexed { i, color ->
            drawRoundRect(color, Offset(gap + i * (w + gap), gap), Size(w, edge), CornerRadius(8.dp.toPx()))
        }
    }
}

@Composable
fun themeName(theme: RoomTheme): String = stringResource(when (theme) {
    RoomTheme.LINEN -> R.string.library_theme_linen
    RoomTheme.SAGE -> R.string.library_theme_sage
    RoomTheme.ROSE -> R.string.library_theme_rose
    RoomTheme.AMBER -> R.string.library_theme_amber
    RoomTheme.SLATE -> R.string.library_theme_slate
    RoomTheme.WALNUT -> R.string.library_theme_walnut
})

@Composable
private fun themeDescription(theme: RoomTheme): String = stringResource(when (theme) {
    RoomTheme.LINEN -> R.string.library_theme_linen_detail
    RoomTheme.SAGE -> R.string.library_theme_sage_detail
    RoomTheme.ROSE -> R.string.library_theme_rose_detail
    RoomTheme.AMBER -> R.string.library_theme_amber_detail
    RoomTheme.SLATE -> R.string.library_theme_slate_detail
    RoomTheme.WALNUT -> R.string.library_theme_walnut_detail
})
