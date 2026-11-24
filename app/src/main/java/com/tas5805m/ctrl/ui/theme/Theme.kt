package com.tas5805m.ctrl.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tas5805m.ctrl.data.ThemeMode

// ========================================================
// Palet warna yang dipakai semua screen
// ========================================================
val OkGreen       = Color(0xFF4ADE80)
val WarnAmber     = Color(0xFFFBBF24)
val BadRed        = Color(0xFFF87171)
val AccentCyan    = Color(0xFF7DD3FC)
val AccentAmber   = Color(0xFFFBBF24)

val DarkBackground = Color(0xFF05070A)   // HTML --panel
val DarkSurface    = Color(0xFF0A0C10)
val DarkSurfaceVar = Color(0xFF111827)
val TextPrimary    = Color(0xFFE5E7EB)   // HTML --fg
val TextSecondary  = Color(0xFFCBD5E1)
val TextMuted      = Color(0xFF94A3B8)   // HTML --muted
val TextDim        = Color(0xFF64748B)

/** Palet meter / level (dipakai LevelMeter). */
object BgapColors {
    val Danger = BadRed
    val Warn = WarnAmber
    val TextMuted = com.tas5805m.ctrl.ui.theme.TextMuted
    val TextSecondary = com.tas5805m.ctrl.ui.theme.TextSecondary
    val TextDim = com.tas5805m.ctrl.ui.theme.TextDim
    val MeterGreen = Color(0xFF22C55E)
    val MeterLime = Color(0xFF84CC16)
    val MeterYellow = Color(0xFFEAB308)
    val MeterOrange = Color(0xFFF59E0B)
    val MeterRed = Color(0xFFEF4444)
    val MeterSubStart = Color(0xFF06B6D4)
}

// ========================================================
// ThemeMode — enum pilihan user
// ========================================================

// ========================================================
// resolveDark — function (bukan property) supaya bisa
// dipanggil `resolveDark(settings.theme)` dari MainActivity
// ========================================================
@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK   -> true
    ThemeMode.LIGHT  -> false
}

// Local dark-mode flag untuk diakses di subtree
private val LocalResolveDark = compositionLocalOf { true }

// ========================================================
// Color schemes
// ========================================================
private val AmoledDarkColors = darkColorScheme(
    primary          = AccentCyan,          // #7DD3FC
    onPrimary        = Color(0xFF001824),
    secondary        = AccentAmber,         // #FBBF24
    tertiary         = AccentAmber,
    onSecondary      = Color(0xFF1F1300),
    background       = Color.Black,         // HTML --bg #000
    onBackground     = TextPrimary,
    surface          = DarkBackground,      // #05070A
    onSurface        = TextPrimary,
    surfaceVariant   = DarkSurfaceVar,
    onSurfaceVariant = TextMuted,
    outline          = Color(0xFF334155),
    outlineVariant   = Color(0xFF1E293B),
    error            = BadRed,
    onError          = Color.Black
)

private val LightColors = lightColorScheme(
    primary          = Color(0xFF0369A1),
    secondary        = Color(0xFFB45309),
    tertiary         = Color(0xFFB45309),
    background       = Color(0xFFF8FAFC),
    onBackground     = Color(0xFF0F172A),
    surface          = Color.White,
    onSurface        = Color(0xFF0F172A),
    surfaceVariant   = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline          = Color(0xFFCBD5E1),
    outlineVariant   = Color(0xFFE2E8F0),
    error            = Color(0xFFDC2626)
)

// ========================================================
// TasTheme — signature: (ThemeMode, Boolean, content)
// ========================================================
@Composable
fun TasTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark  -> AmoledDarkColors
        else  -> LightColors
    }
    CompositionLocalProvider(LocalResolveDark provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
