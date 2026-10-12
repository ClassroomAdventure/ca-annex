package com.classroomadventure.annex

import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.classroomadventure.annex.ui.AnnexTheme
import kotlin.math.roundToInt

/**
 * Mac が無くても試せるデモ。Mac の拡張ディスプレイを模したデスクトップを指で操作できる。
 * （Google Play の審査担当者が Mac なしで動作を確かめられるようにする目的もある）
 */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AnnexTheme { DemoDesktop(onExit = { finish() }) } }
        // ウィンドウ（DecorView）ができてからでないと insetsController が使えない
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}

private val Wallpaper = Brush.linearGradient(
    listOf(Color(0xFF2C4FD8), Color(0xFF5D7DF2), Color(0xFFF0956B), Color(0xFFF6C47C), Color(0xFF8A6BE0)),
)
private val TileColors = listOf(
    Color(0xFF3A6EFA), Color(0xFF7B61FF), Color(0xFFE255C8), Color(0xFFF06464), Color(0xFFF5A03C), Color(0xFF2FC28A),
)

@Composable
private fun DemoDesktop(onExit: () -> Unit) {
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var front by remember { mutableIntStateOf(0) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Wallpaper)
            // 触った位置にカーソルを出す（実物と同じく、カーソルは指の下に付いてくる）
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        e.changes.firstOrNull()?.let { pointer = it.position }
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        MenuBar()
        Dock(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))

        // 縦長（スマホ）では2つのウィンドウを上下に、横長（タブレット）では左右に並べる
        val portrait = h > w
        DemoWindow(
            title = stringResource(R.string.demo_board),
            start = if (portrait) Offset(w * 0.06f, h * 0.15f) else Offset(w * 0.08f, h * 0.16f), bounds = Offset(w, h),
            width = 300.dp, height = 300.dp, z = if (front == 0) 2f else 1f, onFocus = { front = 0 },
        ) { Board() }
        DemoWindow(
            title = stringResource(R.string.demo_notes),
            start = if (portrait) Offset(w * 0.34f, h * 0.60f) else Offset(w * 0.52f, h * 0.30f), bounds = Offset(w, h),
            width = 260.dp, height = 170.dp, z = if (front == 1) 2f else 1f, onFocus = { front = 1 },
        ) { Notes() }

        pointer?.let { Pointer(it) }
        Banner(onExit, Modifier.align(Alignment.TopCenter).padding(top = 30.dp))
    }
}

@Composable
private fun MenuBar() {
    Row(
        Modifier.fillMaxWidth().height(22.dp).background(Color.White.copy(alpha = 0.32f)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xB314213D)))
        listOf(34, 30, 38).forEach { Box(Modifier.width(it.dp).height(6.dp).clip(CircleShape).background(Color(0x5914213D))) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(70.dp).height(6.dp).clip(CircleShape).background(Color(0x4D14213D)))
    }
}

@Composable
private fun Dock(modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.35f)).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(Color.White, Color(0xFF3A6EFA), Color.White, Color(0xFFF5A03C), Color.White, Color.White).forEach {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(it))
        }
    }
}

@Composable
private fun DemoWindow(
    title: String, start: Offset, bounds: Offset, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp,
    z: Float, onFocus: () -> Unit, content: @Composable () -> Unit,
) {
    var pos by remember { mutableStateOf(start) }
    val density = LocalDensity.current
    val wPx = with(density) { width.toPx() }
    val hPx = with(density) { height.toPx() }
    Column(
        Modifier.offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }.zIndex(z)
            .size(width, height).shadow(16.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F9FC)),
    ) {
        // タイトル部分をドラッグで移動（画面の外へは出さない）
        Row(
            Modifier.fillMaxWidth().height(30.dp).background(Color(0xFFE4E9F1))
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { onFocus() }) { change, drag ->
                        change.consume()
                        pos = Offset(
                            (pos.x + drag.x).coerceIn(-wPx * 0.6f, bounds.x - wPx * 0.4f),
                            (pos.y + drag.y).coerceIn(0f, bounds.y - 40f),
                        )
                    }
                }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840)).forEach {
                Box(Modifier.padding(end = 6.dp).size(10.dp).clip(CircleShape).background(it))
            }
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF556074))
        }
        Box(Modifier.fillMaxSize().clickable(onClick = onFocus, indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() })) { content() }
    }
}

@Composable
private fun Board() {
    var picked by remember { mutableIntStateOf(-1) }
    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(36) { i ->
            Box(
                Modifier.height(54.dp).clip(RoundedCornerShape(8.dp)).background(TileColors[i % TileColors.size])
                    .then(if (picked == i) Modifier.border(3.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                    .clickable { picked = i },
            )
        }
    }
}

@Composable
private fun Notes() {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.demo_note1, R.string.demo_note2, R.string.demo_note3).forEach {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF3A6EFA), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(it), fontSize = 14.sp, color = Color(0xFF14213D))
            }
        }
    }
}

@Composable
private fun Pointer(at: Offset) {
    Canvas(Modifier.fillMaxSize().zIndex(5f)) {
        val s = 1.4f * density
        val p = Path().apply {
            moveTo(at.x, at.y); lineTo(at.x, at.y + 22 * s); lineTo(at.x + 6 * s, at.y + 16.5f * s)
            lineTo(at.x + 10 * s, at.y + 25.5f * s); lineTo(at.x + 14 * s, at.y + 23.7f * s)
            lineTo(at.x + 10 * s, at.y + 15 * s); lineTo(at.x + 18 * s, at.y + 15 * s); close()
        }
        drawPath(p, Color.White)
        drawPath(p, Color(0xFF14213D), style = Stroke(1.6f * density))
    }
}

@Composable
private fun Banner(onExit: () -> Unit, modifier: Modifier) {
    Row(
        modifier.zIndex(10f).padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(stringResource(R.string.demo_banner_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.demo_banner_body), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onExit) { Text(stringResource(R.string.demo_exit)) }
    }
}
