package com.classroomadventure.annex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.classroomadventure.annex.R
import com.classroomadventure.annex.Settings

class SettingsActions(
    val back: () -> Unit,
    val openUrl: (String) -> Unit,
    val resetPairing: () -> Unit,
)

@Composable
fun SettingsScreen(settings: Settings, hevcSupported: Boolean, version: String, siteUrl: String, actions: SettingsActions) {
    val b = LocalBrand.current
    Column(
        Modifier.fillMaxSize().background(b.paper).windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.back) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))

            Section(stringResource(R.string.display_size)) {
                var scale by remember { mutableFloatStateOf(settings.scale) }
                val options = listOf(
                    3.0f to R.string.scale_3, 2.5f to R.string.scale_25, 2.0f to R.string.scale_2,
                    1.75f to R.string.scale_175, 1.5f to R.string.scale_15, 1.25f to R.string.scale_125,
                )
                Column(Modifier.selectableGroup()) {
                    options.forEach { (v, label) ->
                        val selected = kotlin.math.abs(scale - v) < 0.01f
                        Row(
                            Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) { scale = v; settings.scale = v }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Hint(stringResource(R.string.display_size_hint))
            }

            Section(stringResource(R.string.display_fps_title)) {
                var fps by remember { mutableIntStateOf(settings.fps) }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(60, 120).forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = fps == f, onClick = { fps = f; settings.fps = f },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(stringResource(R.string.fps_value, f)) }
                    }
                }
                Hint(stringResource(R.string.display_fps_hint))
            }

            Section(stringResource(R.string.quality_title)) {
                var hevc by remember { mutableStateOf(settings.hevc && hevcSupported) }
                SwitchRow(stringResource(R.string.hevc), stringResource(if (hevcSupported) R.string.hevc_hint else R.string.hevc_unsupported),
                    hevc, enabled = hevcSupported) { hevc = it; settings.hevc = it }
            }

            Section(stringResource(R.string.connection_title)) {
                var auto by remember { mutableStateOf(settings.autoUsb) }
                SwitchRow(stringResource(R.string.usb_auto), stringResource(R.string.usb_auto_hint), auto) { auto = it; settings.autoUsb = it }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.reset_pairing), style = MaterialTheme.typography.bodyLarge, color = b.blue,
                    modifier = Modifier.clickable(onClick = actions.resetPairing).padding(vertical = 10.dp),
                )
                Hint(stringResource(R.string.reset_pairing_hint))
            }

            Section(stringResource(R.string.gestures_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Gesture(Icons.Outlined.TouchApp, R.string.g_tap, R.string.g_tap_do)
                    Gesture(Icons.Outlined.PanTool, R.string.g_drag, R.string.g_drag_do)
                    Gesture(Icons.Outlined.Mouse, R.string.g_right, R.string.g_right_do)
                    Gesture(Icons.Outlined.Swipe, R.string.g_scroll, R.string.g_scroll_do)
                    Gesture(Icons.Outlined.Draw, R.string.g_pen, R.string.g_pen_do)
                    Gesture(Icons.Outlined.QueryStats, R.string.g_stats, R.string.g_stats_do)
                }
            }

            Section(stringResource(R.string.about_title)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium, color = b.muted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LinkRow(stringResource(R.string.about_site)) { actions.openUrl(siteUrl) }
                LinkRow(stringResource(R.string.about_source)) { actions.openUrl("https://github.com/ClassroomAdventure/ca-annex") }
                LinkRow(stringResource(R.string.about_issue)) { actions.openUrl("https://github.com/ClassroomAdventure/ca-annex/issues") }
                LinkRow(stringResource(R.string.about_privacy)) { actions.openUrl(siteUrl + "privacy/") }
                Hint(stringResource(R.string.about_license))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val b = LocalBrand.current
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(24.dp)).background(b.surface)
            .border(1.dp, b.line, RoundedCornerShape(24.dp)).padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LocalBrand.current.muted, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = LocalBrand.current.muted)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun Gesture(icon: ImageVector, what: Int, does: Int) {
    val b = LocalBrand.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = b.blue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(stringResource(what), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(stringResource(does), style = MaterialTheme.typography.bodyMedium, color = b.muted)
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(
        text, style = MaterialTheme.typography.bodyLarge, color = LocalBrand.current.blue,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}
