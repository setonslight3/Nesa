package com.nesa.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The NESA palette: a calm, desaturated green.
 *
 * Stage 5 replaces this single palette with a theme engine. Until then every
 * colour in the application comes from here through the Material scheme, so
 * that swap stays a one-file change rather than a hunt through screens.
 */
internal object NesaPalette {

    // Light
    val Primary = Color(0xFF1F6F52)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFA8F2CE)
    val OnPrimaryContainer = Color(0xFF00210F)

    val Secondary = Color(0xFF4C6358)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFCFE9DA)
    val OnSecondaryContainer = Color(0xFF082018)

    val Tertiary = Color(0xFF3D6473)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFC1E9FB)
    val OnTertiaryContainer = Color(0xFF001F29)

    val Error = Color(0xFFBA1A1A)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF410002)

    val Background = Color(0xFFFBFDF9)
    val OnBackground = Color(0xFF191C1A)
    val Surface = Color(0xFFFBFDF9)
    val OnSurface = Color(0xFF191C1A)
    val SurfaceVariant = Color(0xFFDCE5DD)
    val OnSurfaceVariant = Color(0xFF414942)
    val Outline = Color(0xFF717972)
    val OutlineVariant = Color(0xFFC0C9C1)

    // Dark
    val DarkPrimary = Color(0xFF8CD6B0)
    val DarkOnPrimary = Color(0xFF003825)
    val DarkPrimaryContainer = Color(0xFF005138)
    val DarkOnPrimaryContainer = Color(0xFFA8F2CE)

    val DarkSecondary = Color(0xFFB3CCBE)
    val DarkOnSecondary = Color(0xFF1E352B)
    val DarkSecondaryContainer = Color(0xFF344C41)
    val DarkOnSecondaryContainer = Color(0xFFCFE9DA)

    val DarkTertiary = Color(0xFFA5CDDF)
    val DarkOnTertiary = Color(0xFF063544)
    val DarkTertiaryContainer = Color(0xFF244C5B)
    val DarkOnTertiaryContainer = Color(0xFFC1E9FB)

    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)

    val DarkBackground = Color(0xFF191C1A)
    val DarkOnBackground = Color(0xFFE1E3DF)
    val DarkSurface = Color(0xFF191C1A)
    val DarkOnSurface = Color(0xFFE1E3DF)
    val DarkSurfaceVariant = Color(0xFF414942)
    val DarkOnSurfaceVariant = Color(0xFFC0C9C1)
    val DarkOutline = Color(0xFF8B938B)
    val DarkOutlineVariant = Color(0xFF414942)
}

/**
 * Colours that carry meaning beyond the Material roles: the state of an
 * activity, and the phase of the day.
 *
 * They live in their own holder rather than being hard-coded at call sites so
 * that "missed" looks the same everywhere and stays legible in both themes.
 */
data class NesaSemanticColors(
    val completed: Color,
    val active: Color,
    val upcoming: Color,
    val later: Color,
    val skipped: Color,
    val missed: Color,
    val cancelled: Color,
    val anchor: Color
) {
    companion object {
        val Light = NesaSemanticColors(
            completed = Color(0xFF2E7D52),
            active = Color(0xFF1F6F52),
            upcoming = Color(0xFF5B6B62),
            later = Color(0xFF7A5C1E),
            skipped = Color(0xFF6B6B6B),
            missed = Color(0xFFA33A2E),
            cancelled = Color(0xFF8A8A8A),
            anchor = Color(0xFF3D6473)
        )

        val Dark = NesaSemanticColors(
            completed = Color(0xFF7FCFA2),
            active = Color(0xFF8CD6B0),
            upcoming = Color(0xFFAFBCB4),
            later = Color(0xFFE3C07A),
            skipped = Color(0xFF9E9E9E),
            missed = Color(0xFFFFB4AB),
            cancelled = Color(0xFF8A8A8A),
            anchor = Color(0xFFA5CDDF)
        )
    }
}
