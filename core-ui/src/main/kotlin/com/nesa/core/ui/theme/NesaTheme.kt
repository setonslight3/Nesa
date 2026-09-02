package com.nesa.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.nesa.core.model.ThemeMode

private val LightScheme = lightColorScheme(
    primary = NesaPalette.Primary,
    onPrimary = NesaPalette.OnPrimary,
    primaryContainer = NesaPalette.PrimaryContainer,
    onPrimaryContainer = NesaPalette.OnPrimaryContainer,
    secondary = NesaPalette.Secondary,
    onSecondary = NesaPalette.OnSecondary,
    secondaryContainer = NesaPalette.SecondaryContainer,
    onSecondaryContainer = NesaPalette.OnSecondaryContainer,
    tertiary = NesaPalette.Tertiary,
    onTertiary = NesaPalette.OnTertiary,
    tertiaryContainer = NesaPalette.TertiaryContainer,
    onTertiaryContainer = NesaPalette.OnTertiaryContainer,
    error = NesaPalette.Error,
    onError = NesaPalette.OnError,
    errorContainer = NesaPalette.ErrorContainer,
    onErrorContainer = NesaPalette.OnErrorContainer,
    background = NesaPalette.Background,
    onBackground = NesaPalette.OnBackground,
    surface = NesaPalette.Surface,
    onSurface = NesaPalette.OnSurface,
    surfaceVariant = NesaPalette.SurfaceVariant,
    onSurfaceVariant = NesaPalette.OnSurfaceVariant,
    outline = NesaPalette.Outline,
    outlineVariant = NesaPalette.OutlineVariant
)

private val DarkScheme = darkColorScheme(
    primary = NesaPalette.DarkPrimary,
    onPrimary = NesaPalette.DarkOnPrimary,
    primaryContainer = NesaPalette.DarkPrimaryContainer,
    onPrimaryContainer = NesaPalette.DarkOnPrimaryContainer,
    secondary = NesaPalette.DarkSecondary,
    onSecondary = NesaPalette.DarkOnSecondary,
    secondaryContainer = NesaPalette.DarkSecondaryContainer,
    onSecondaryContainer = NesaPalette.DarkOnSecondaryContainer,
    tertiary = NesaPalette.DarkTertiary,
    onTertiary = NesaPalette.DarkOnTertiary,
    tertiaryContainer = NesaPalette.DarkTertiaryContainer,
    onTertiaryContainer = NesaPalette.DarkOnTertiaryContainer,
    error = NesaPalette.DarkError,
    onError = NesaPalette.DarkOnError,
    errorContainer = NesaPalette.DarkErrorContainer,
    onErrorContainer = NesaPalette.DarkOnErrorContainer,
    background = NesaPalette.DarkBackground,
    onBackground = NesaPalette.DarkOnBackground,
    surface = NesaPalette.DarkSurface,
    onSurface = NesaPalette.DarkOnSurface,
    surfaceVariant = NesaPalette.DarkSurfaceVariant,
    onSurfaceVariant = NesaPalette.DarkOnSurfaceVariant,
    outline = NesaPalette.DarkOutline,
    outlineVariant = NesaPalette.DarkOutlineVariant
)

/** State and cycle colours for the current theme. */
val LocalNesaSemanticColors = staticCompositionLocalOf { NesaSemanticColors.Light }

/**
 * The NESA theme.
 *
 * Light and dark are the same interface with a different palette — never a
 * different layout, and never different information. Dynamic colour is
 * deliberately not used yet: the green identity is part of what NESA is, and
 * Stage 5 is where personalisation gets designed properly.
 */
@Composable
fun NesaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    CompositionLocalProvider(
        LocalNesaSemanticColors provides if (dark) NesaSemanticColors.Dark else NesaSemanticColors.Light
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = NesaTypography,
            content = content
        )
    }
}
