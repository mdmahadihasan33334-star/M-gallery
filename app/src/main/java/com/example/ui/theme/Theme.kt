package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.domain.model.AccentPreset
import com.example.domain.model.ThemeMode

private fun buildDarkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.22f),
    onPrimaryContainer = PrimaryText,
    secondary = accent.copy(alpha = 0.85f),
    onSecondary = Color.White,
    secondaryContainer = ElevatedCard,
    onSecondaryContainer = PrimaryText,
    tertiary = CyanAccent,
    onTertiary = Color.White,
    background = ObsidianBackground,
    onBackground = PrimaryText,
    surface = SlateCard,
    onSurface = PrimaryText,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = SecondaryText,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = Color(0xFF15171B),
    surfaceContainer = SlateCard,
    surfaceContainerHigh = ElevatedCard,
    surfaceContainerHighest = Color(0xFF2D313B),
    outline = SubtleOutline,
    outlineVariant = SubtleOutline.copy(alpha = 0.6f),
    error = DangerRed,
    onError = Color.White
)

private fun buildLightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.14f),
    onPrimaryContainer = LightPrimaryText,
    secondary = accent.copy(alpha = 0.85f),
    onSecondary = Color.White,
    secondaryContainer = SoftGrayElevated,
    onSecondaryContainer = LightPrimaryText,
    tertiary = CyanAccent,
    onTertiary = Color.White,
    background = AlabasterBackground,
    onBackground = LightPrimaryText,
    surface = CrispWhiteCard,
    onSurface = LightPrimaryText,
    surfaceVariant = SoftGrayElevated,
    onSurfaceVariant = LightSecondaryText,
    surfaceContainerLowest = CrispWhiteCard,
    surfaceContainerLow = AlabasterBackground,
    surfaceContainer = CrispWhiteCard,
    surfaceContainerHigh = SoftGrayElevated,
    surfaceContainerHighest = Color(0xFFE3E6EE),
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.7f),
    error = DangerRed,
    onError = Color.White
)

@Composable
fun MGalleryTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentPreset: AccentPreset = AccentPreset.VIOLET,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val context = LocalContext.current

    val colorScheme = if (accentPreset == AccentPreset.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val accentColor = when (accentPreset) {
            AccentPreset.VIOLET -> VioletAccent
            AccentPreset.CYAN -> CyanAccent
            AccentPreset.ROSE -> RoseAccent
            AccentPreset.AMBER -> AmberAccent
            AccentPreset.EMERALD -> EmeraldAccent
            AccentPreset.DYNAMIC -> VioletAccent
        }
        if (useDark) buildDarkScheme(accentColor) else buildLightScheme(accentColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MGalleryTypography,
        shapes = MGalleryShapes,
        content = content
    )
}
