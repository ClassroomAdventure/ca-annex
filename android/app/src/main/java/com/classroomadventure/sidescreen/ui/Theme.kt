package com.classroomadventure.sidescreen.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 紹介ページ・アイコンと同じ配色 */
data class Brand(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val blue: Color,
    val glass: Color,
    val line: Color,
    val ok: Color,
)

private val LightBrand = Brand(
    paper = Color(0xFFEEF1F5), surface = Color(0xFFFFFFFF), ink = Color(0xFF14213D), muted = Color(0xFF556074),
    blue = Color(0xFF3A6EFA), glass = Color(0xFFDCEBFF), line = Color(0xFFD3D9E3), ok = Color(0xFF1F9D55),
)
private val DarkBrand = Brand(
    paper = Color(0xFF0F1626), surface = Color(0xFF172036), ink = Color(0xFFE6ECF7), muted = Color(0xFF9AA6BC),
    blue = Color(0xFF6F95FF), glass = Color(0xFF26365E), line = Color(0xFF2A3654), ok = Color(0xFF4CC987),
)

val LocalBrand = staticCompositionLocalOf { LightBrand }

@Composable
fun SideScreenTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val b = if (dark) DarkBrand else LightBrand
    val scheme = if (dark) {
        darkColorScheme(
            primary = b.blue, onPrimary = Color(0xFF0F1626), background = b.paper, onBackground = b.ink,
            surface = b.surface, onSurface = b.ink, onSurfaceVariant = b.muted, surfaceVariant = b.glass,
            primaryContainer = b.glass, onPrimaryContainer = b.ink, outline = b.line, outlineVariant = b.line,
            surfaceContainerHigh = b.surface, surfaceContainerHighest = b.surface,
            secondary = b.blue, onSecondary = Color.White, secondaryContainer = b.glass, onSecondaryContainer = b.ink,
            tertiary = b.blue, surfaceContainer = b.surface, surfaceContainerLow = b.surface,
        )
    } else {
        lightColorScheme(
            primary = b.blue, onPrimary = Color.White, background = b.paper, onBackground = b.ink,
            surface = b.surface, onSurface = b.ink, onSurfaceVariant = b.muted, surfaceVariant = b.glass,
            primaryContainer = b.glass, onPrimaryContainer = b.ink, outline = b.line, outlineVariant = b.line,
            surfaceContainerHigh = b.surface, surfaceContainerHighest = b.surface,
            secondary = b.blue, onSecondary = Color.White, secondaryContainer = b.glass, onSecondaryContainer = b.ink,
            tertiary = b.blue, surfaceContainer = b.surface, surfaceContainerLow = b.surface,
        )
    }
    val base = Typography()
    val type = base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalBrand provides b) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}

val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 14.sp)
