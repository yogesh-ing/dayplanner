package com.yourname.dayplanner

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours the timetable draws with, kept in one place so the light and dark
 * palettes can be swapped without touching layout code. The individual colours
 * are exposed as composable properties below, so call sites keep reading `Ink`,
 * `CardBg` and friends.
 */
internal data class Palette(
    val pageBg: Color,
    val card: Color,
    val hairline: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val good: Color,
    val faint: Color,
    val track: Color,
    val danger: Color
)

private val LightPalette = Palette(
    pageBg = Color(0xFFF7F8FA),
    card = Color(0xFFFFFFFF),
    hairline = Color(0xFFE4E7EC),
    ink = Color(0xFF101828),
    muted = Color(0xFF667085),
    accent = Color(0xFF4F46E5),
    accentSoft = Color(0xFFEEF0FF),
    good = Color(0xFF12B76A),
    faint = Color(0xFF98A2B3),
    track = Color(0xFFEDEFF3),
    danger = Color(0xFFB42318)
)

private val DarkPalette = Palette(
    pageBg = Color(0xFF0D1117),
    card = Color(0xFF161B22),
    hairline = Color(0xFF2A313C),
    ink = Color(0xFFE6EAF2),
    muted = Color(0xFF9AA4B2),
    accent = Color(0xFF8B87FF),
    accentSoft = Color(0xFF232145),
    good = Color(0xFF3DD68C),
    faint = Color(0xFF7C8698),
    track = Color(0xFF2A313C),
    danger = Color(0xFFFF8A80)
)

private val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Follows the system light/dark setting; there is no in-app override yet. */
@Composable
fun DayPlannerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = if (dark) {
                darkColorScheme(
                    primary = palette.accent,
                    onPrimary = Color.White,
                    background = palette.pageBg,
                    onBackground = palette.ink,
                    surface = palette.card,
                    onSurface = palette.ink,
                    surfaceVariant = palette.accentSoft,
                    onSurfaceVariant = palette.muted,
                    outline = palette.hairline,
                    error = palette.danger
                )
            } else {
                lightColorScheme(
                    primary = palette.accent,
                    onPrimary = Color.White,
                    background = palette.pageBg,
                    onBackground = palette.ink,
                    surface = palette.card,
                    onSurface = palette.ink,
                    surfaceVariant = palette.accentSoft,
                    onSurfaceVariant = palette.muted,
                    outline = palette.hairline,
                    error = palette.danger
                )
            },
            content = content
        )
    }
}

internal val PageBg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.pageBg
internal val CardBg: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.card
internal val Hairline: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.hairline
internal val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
internal val Muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
internal val Accent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
internal val AccentSoft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentSoft
internal val Good: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.good
internal val Faint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.faint
internal val Track: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.track
internal val Danger: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.danger
