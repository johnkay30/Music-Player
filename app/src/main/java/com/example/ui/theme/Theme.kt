package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class GbeduThemeType {
    AURA_OBSIDIAN,
    NORDIC_FROST,
    SUNSET_COPPER,
    NEON_CYBER,
    LIGHT_MONOCHROME
}

private val AuraObsidianColorScheme = darkColorScheme(
    primary = ObsidianPrimary,
    onPrimary = Color.Black,
    secondary = ObsidianSecondary,
    onSecondary = Color.White,
    tertiary = ObsidianTertiary,
    background = ObsidianBackground,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianTextSecondary
)

private val NordicFrostColorScheme = darkColorScheme(
    primary = FrostPrimary,
    onPrimary = Color.Black,
    secondary = FrostSecondary,
    onSecondary = Color.Black,
    tertiary = FrostTertiary,
    background = FrostBackground,
    onBackground = FrostTextPrimary,
    surface = FrostSurface,
    onSurface = FrostTextPrimary,
    surfaceVariant = FrostSurfaceVariant,
    onSurfaceVariant = FrostTextSecondary
)

private val SunsetCopperColorScheme = darkColorScheme(
    primary = CopperPrimary,
    onPrimary = Color.White,
    secondary = CopperSecondary,
    onSecondary = Color.White,
    tertiary = CopperTertiary,
    background = CopperBackground,
    onBackground = CopperTextPrimary,
    surface = CopperSurface,
    onSurface = CopperTextPrimary,
    surfaceVariant = CopperSurfaceVariant,
    onSurfaceVariant = CopperTextSecondary
)

private val NeonCyberColorScheme = darkColorScheme(
    primary = CyberPrimary,
    onPrimary = Color.White,
    secondary = CyberSecondary,
    onSecondary = Color.Black,
    tertiary = CyberTertiary,
    background = CyberBackground,
    onBackground = CyberTextPrimary,
    surface = CyberSurface,
    onSurface = CyberTextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = CyberTextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    secondary = LightSecondary,
    onSecondary = Color.White,
    tertiary = LightTertiary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary
)

@Composable
fun GbeduPlayerTheme(
    themeType: GbeduThemeType = GbeduThemeType.AURA_OBSIDIAN,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeType) {
        GbeduThemeType.AURA_OBSIDIAN -> AuraObsidianColorScheme
        GbeduThemeType.NORDIC_FROST -> NordicFrostColorScheme
        GbeduThemeType.SUNSET_COPPER -> SunsetCopperColorScheme
        GbeduThemeType.NEON_CYBER -> NeonCyberColorScheme
        GbeduThemeType.LIGHT_MONOCHROME -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
