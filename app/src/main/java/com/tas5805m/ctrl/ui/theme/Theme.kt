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

@Composable
fun resolveDark(mode: ThemeMode): Boolean = mode.isDark(isSystemInDarkTheme())

private val LocalResolveDark = compositionLocalOf { true }

// ========================================================
// Color schemes — 7 tema ala HTML Final v4
// ========================================================

private val DarkColors = darkColorScheme(
    primary          = Color(0xFF22D3EE),
    onPrimary        = Color(0xFF001A1F),
    secondary        = Color(0xFFA78BFA),
    tertiary         = Color(0xFFFBBF24),
    onSecondary      = Color(0xFF1A0A2E),
    background       = Color(0xFF040608),
    onBackground     = TextPrimary,
    surface          = Color(0xFF0A0C10),
    onSurface        = TextPrimary,
    surfaceVariant   = Color(0xFF111827),
    onSurfaceVariant = TextMuted,
    outline          = Color(0xFF334155),
    outlineVariant   = Color(0xFF1E293B),
    error            = BadRed,
    onError          = Color.Black
)

private val AmoledColors = darkColorScheme(
    primary          = Color(0xFFC084FC),
    onPrimary        = Color(0xFF1A0533),
    secondary        = Color(0xFF818CF8),
    tertiary         = Color(0xFFFBBF24),
    onSecondary      = Color(0xFF0F0A2E),
    background       = Color.Black,
    onBackground     = Color(0xFFF5F3FF),
    surface          = Color(0xFF050508),
    onSurface        = Color(0xFFF5F3FF),
    surfaceVariant   = Color(0xFF12101A),
    onSurfaceVariant = Color(0xFFA78BFA),
    outline          = Color(0xFF4C1D95),
    outlineVariant   = Color(0xFF2E1065),
    error            = BadRed,
    onError          = Color.Black
)

private val NordColors = darkColorScheme(
    primary          = Color(0xFF88C0D0),
    onPrimary        = Color(0xFF0B1A20),
    secondary        = Color(0xFFB48EAD),
    tertiary         = Color(0xFFEBCB8B),
    onSecondary      = Color(0xFF1A0F18),
    background       = Color(0xFF2E3440),
    onBackground     = Color(0xFFECEFF4),
    surface          = Color(0xFF3B4252),
    onSurface        = Color(0xFFECEFF4),
    surfaceVariant   = Color(0xFF434C5E),
    onSurfaceVariant = Color(0xFF81A1C1),
    outline          = Color(0xFF4C566A),
    outlineVariant   = Color(0xFF3B4252),
    error            = Color(0xFFBF616A),
    onError          = Color.Black
)

private val SunsetColors = darkColorScheme(
    primary          = Color(0xFFFB923C),
    onPrimary        = Color(0xFF2A1000),
    secondary        = Color(0xFFF472B6),
    tertiary         = Color(0xFFFBBF24),
    onSecondary      = Color(0xFF2A0018),
    background       = Color(0xFF1A0F0A),
    onBackground     = Color(0xFFFFF7ED),
    surface          = Color(0xFF221510),
    onSurface        = Color(0xFFFFF7ED),
    surfaceVariant   = Color(0xFF2E1C14),
    onSurfaceVariant = Color(0xFFFDBA74),
    outline          = Color(0xFF9A3412),
    outlineVariant   = Color(0xFF7C2D12),
    error            = Color(0xFFEF4444),
    onError          = Color.Black
)

private val ForestColors = darkColorScheme(
    primary          = Color(0xFF34D399),
    onPrimary        = Color(0xFF002218),
    secondary        = Color(0xFF22D3EE),
    tertiary         = Color(0xFFFBBF24),
    onSecondary      = Color(0xFF001A1F),
    background       = Color(0xFF06110D),
    onBackground     = Color(0xFFECFDF5),
    surface          = Color(0xFF0A1A14),
    onSurface        = Color(0xFFECFDF5),
    surfaceVariant   = Color(0xFF0F241C),
    onSurfaceVariant = Color(0xFF6EE7B7),
    outline          = Color(0xFF047857),
    outlineVariant   = Color(0xFF065F46),
    error            = BadRed,
    onError          = Color.Black
)

private val LightColors = lightColorScheme(
    primary          = Color(0xFF0891B2),
    onPrimary        = Color.White,
    secondary        = Color(0xFF7C3AED),
    tertiary         = Color(0xFFD97706),
    onSecondary      = Color.White,
    background       = Color(0xFFEEF2F7),
    onBackground     = Color(0xFF0B1220),
    surface          = Color.White,
    onSurface        = Color(0xFF0B1220),
    surfaceVariant   = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline          = Color(0xFFCBD5E1),
    outlineVariant   = Color(0xFFE2E8F0),
    error            = Color(0xFFDC2626),
    onError          = Color.White
)

@Composable
fun TasTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = themeMode.isDark(systemDark)
    val context = LocalContext.current

    val useDynamic = dynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        themeMode in setOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT)

    val scheme = when {
        useDynamic ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        themeMode == ThemeMode.AMOLED -> AmoledColors
        themeMode == ThemeMode.NORD -> NordColors
        themeMode == ThemeMode.SUNSET -> SunsetColors
        themeMode == ThemeMode.FOREST -> ForestColors
        themeMode == ThemeMode.LIGHT || (!dark && themeMode == ThemeMode.SYSTEM) -> LightColors
        else -> DarkColors
    }
    CompositionLocalProvider(LocalResolveDark provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
