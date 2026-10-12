package com.classroomadventure.annex.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classroomadventure.annex.R

/** 接続できる相手（USB か、Bonjour で見つけた Mac） */
data class Target(val name: String, val host: String, val usb: Boolean)

data class HomeState(
    val targets: List<Target>,
    val firstRun: Boolean,
    val waitingFor: String? = null,
)

class HomeActions(
    val connect: (Target) -> Unit,
    val connectManual: (String) -> Unit,
    val openSettings: () -> Unit,
    val shareMacLink: () -> Unit,
    val copyMacLink: () -> Unit,
    val openDeveloperOptions: () -> Unit,
)

@Composable
fun HomeScreen(state: HomeState, macUrl: String, lastHost: String, actions: HomeActions) {
    val b = LocalBrand.current
    var manual by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(b.paper).windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val wide = maxWidth >= 720.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 40.dp else 20.dp, vertical = 16.dp),
        ) {
            Header(onSettings = actions.openSettings)
            Spacer(Modifier.height(if (wide) 36.dp else 24.dp))
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column(Modifier.weight(1f)) { Connect(state, actions, onManual = { manual = true }) }
                    Column(Modifier.weight(1f)) { Guide(state.firstRun, macUrl, actions, startOpen = true) }
                }
            } else {
                Connect(state, actions, onManual = { manual = true })
                Spacer(Modifier.height(32.dp))
                Guide(state.firstRun, macUrl, actions, startOpen = state.firstRun)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (manual) ManualDialog(lastHost, onDismiss = { manual = false }, onConnect = {
        manual = false
        actions.connectManual(it)
    })
}

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(40.dp)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
        }
    }
}

@Composable
fun AppIcon(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f))
            .background(Brush.verticalGradient(listOf(Color(0xFF4A8FFF), Color(0xFF294DEB)))),
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Connect(state: HomeState, actions: HomeActions, onManual: () -> Unit) {
    val b = LocalBrand.current
    Column(Modifier.animateContentSize()) {
        if (state.targets.isEmpty()) {
            Searching()
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.home_searching_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.home_searching_body), style = MaterialTheme.typography.bodyLarge, color = b.muted)
        } else {
            Text(
                stringResource(R.string.home_pick_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.targets.forEach { t -> TargetRow(t, waiting = state.waitingFor == t.host) { actions.connect(t) } }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onManual, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
            Text(stringResource(R.string.home_manual))
        }
    }
}

/** Mac を探している間だけ動く波紋（画面で唯一のアニメーション） */
@Composable
private fun Searching() {
    val b = LocalBrand.current
    val t = rememberInfiniteTransition(label = "search")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0..1) {
                val q = (p + i * 0.5f) % 1f
                drawCircle(b.blue.copy(alpha = (1f - q) * 0.35f), radius = size.minDimension / 2 * (0.35f + q * 0.65f), style = Stroke(2.dp.toPx()))
            }
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(b.glass), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Laptop, contentDescription = null, tint = b.blue, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun TargetRow(t: Target, waiting: Boolean, onClick: () -> Unit) {
    val b = LocalBrand.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(b.surface)
            .border(1.dp, b.line, RoundedCornerShape(20.dp)).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(b.glass), contentAlignment = Alignment.Center) {
            Icon(if (t.usb) Icons.Outlined.Usb else Icons.Outlined.Wifi, contentDescription = null, tint = b.blue)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (t.usb) stringResource(R.string.home_via_usb) else stringResource(R.string.home_via_wifi, t.host),
                style = MaterialTheme.typography.bodyMedium, color = b.muted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onClick, enabled = !waiting) {
            Text(stringResource(if (waiting) R.string.home_connecting else R.string.home_connect))
        }
    }
}

@Composable
private fun Guide(firstRun: Boolean, macUrl: String, actions: HomeActions, startOpen: Boolean) {
    val b = LocalBrand.current
    var open by rememberSaveable { mutableStateOf(startOpen) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(b.surface)
            .border(1.dp, b.line, RoundedCornerShape(24.dp)).animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (firstRun) R.string.guide_title_first else R.string.guide_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                if (!open) Text(stringResource(R.string.guide_collapsed), style = MaterialTheme.typography.bodyMedium, color = b.muted)
            }
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = b.muted)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Step(1, stringResource(R.string.guide_step1_title), stringResource(R.string.guide_step1_body)) {
                    Text(
                        macUrl, style = MonoStyle, color = b.ink,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(b.paper).padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = actions.shareMacLink) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.guide_share))
                        }
                        OutlinedButton(onClick = actions.copyMacLink) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.guide_copy))
                        }
                    }
                }
                Step(2, stringResource(R.string.guide_step2_title), stringResource(R.string.guide_step2_body))
                Step(3, stringResource(R.string.guide_step3_title), null) {
                    Way(Icons.Outlined.Wifi, stringResource(R.string.guide_wifi_title), stringResource(R.string.guide_wifi_body))
                    Spacer(Modifier.height(14.dp))
                    Way(Icons.Outlined.Usb, stringResource(R.string.guide_usb_title), stringResource(R.string.guide_usb_body))
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = actions.openDeveloperOptions, modifier = Modifier.padding(start = 34.dp)) {
                        Text(stringResource(R.string.guide_open_dev))
                    }
                    Text(
                        stringResource(R.string.guide_dev_hint), style = MaterialTheme.typography.bodySmall, color = b.muted,
                        modifier = Modifier.padding(start = 34.dp, top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, title: String, body: String?, extra: @Composable (() -> Unit)? = null) {
    val b = LocalBrand.current
    Row {
        Box(Modifier.size(28.dp).clip(CircleShape).background(b.glass), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelLarge, color = b.ink)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) {
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = b.muted)
            }
            if (extra != null) {
                Spacer(Modifier.height(12.dp))
                extra()
            }
        }
    }
}

@Composable
private fun Way(icon: ImageVector, title: String, body: String) {
    val b = LocalBrand.current
    Row {
        Icon(icon, contentDescription = null, tint = b.blue, modifier = Modifier.size(22.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = b.ink)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = b.muted)
        }
    }
}

@Composable
private fun ManualDialog(lastHost: String, onDismiss: () -> Unit, onConnect: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(lastHost) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_title)) },
        text = {
            Column {
                Text(stringResource(R.string.manual_body), style = MaterialTheme.typography.bodyMedium, color = LocalBrand.current.muted)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it.trim() }, singleLine = true,
                    placeholder = { Text("192.168.1.10") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.widthIn(min = 240.dp),
                )
            }
        },
        confirmButton = { Button(onClick = { onConnect(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.home_connect)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
