package sanctuary.app.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Design tokens from the Sanctuary style guide.
 * Primary #A9C5D3, secondary #B8C9B5, tertiary #D1C4E9, neutral #FDFBF7.
 */
object SanctuaryPalette {
    val Primary = Color(0xFFA9C5D3)
    val PrimaryDark = Color(0xFF2A3F48)
    val PrimaryLight = Color(0xFFD4E4EB)

    val Secondary = Color(0xFFB8C9B5)
    val SecondaryDark = Color(0xFF3D4F3A)
    val SecondaryLight = Color(0xFFDCE6DA)

    val Tertiary = Color(0xFFD1C4E9)
    val TertiaryDark = Color(0xFF4A3D5C)
    val TertiaryLight = Color(0xFFEBE4F5)

    val Neutral = Color(0xFFFDFBF7)
    val NeutralSurface = Color(0xFFF5F2EC)
    val NeutralVariant = Color(0xFFEDEAE3)
    val OnNeutral = Color(0xFF1C1B19)
    val OnNeutralMuted = Color(0xFF5C5954)
    val Outline = Color(0xFFB0ACA3)
    val OutlineVariant = Color(0xFFD9D5CD)

    val Inverted = Color(0xFF0D0D0D)
    val OnInverted = Color(0xFFFFFFFF)

    /** Bottom nav selected icon, label, and indicator (Material serene slate). */
    val BottomNavActive = Color(0xFF48636F)

    /** Bottom nav unselected icon and label (muted blue-gray). */
    val BottomNavInactive = Color(0xFF9DB0B8)

    val Error = Color(0xFFB3261E)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFF9DEDC)
    val OnErrorContainer = Color(0xFF410E0B)
}

fun sanctuaryLightColorScheme() = lightColorScheme(
    primary = SanctuaryPalette.PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = SanctuaryPalette.Primary,
    onPrimaryContainer = SanctuaryPalette.PrimaryDark,

    secondary = SanctuaryPalette.SecondaryDark,
    onSecondary = Color.White,
    secondaryContainer = SanctuaryPalette.Secondary,
    onSecondaryContainer = SanctuaryPalette.SecondaryDark,

    tertiary = SanctuaryPalette.TertiaryDark,
    onTertiary = Color.White,
    tertiaryContainer = SanctuaryPalette.Tertiary,
    onTertiaryContainer = SanctuaryPalette.TertiaryDark,

    background = SanctuaryPalette.Neutral,
    onBackground = SanctuaryPalette.OnNeutral,

    surface = SanctuaryPalette.Neutral,
    onSurface = SanctuaryPalette.OnNeutral,
    surfaceVariant = SanctuaryPalette.NeutralVariant,
    onSurfaceVariant = SanctuaryPalette.OnNeutralMuted,

    outline = SanctuaryPalette.Outline,
    outlineVariant = SanctuaryPalette.OutlineVariant,

    error = SanctuaryPalette.Error,
    onError = SanctuaryPalette.OnError,
    errorContainer = SanctuaryPalette.ErrorContainer,
    onErrorContainer = SanctuaryPalette.OnErrorContainer,

    inverseSurface = SanctuaryPalette.Inverted,
    inverseOnSurface = SanctuaryPalette.OnInverted,
    inversePrimary = SanctuaryPalette.PrimaryLight,
)

fun sanctuaryDarkColorScheme() = darkColorScheme(
    primary = SanctuaryPalette.PrimaryLight,
    onPrimary = SanctuaryPalette.PrimaryDark,
    primaryContainer = SanctuaryPalette.PrimaryDark,
    onPrimaryContainer = SanctuaryPalette.PrimaryLight,

    secondary = SanctuaryPalette.SecondaryLight,
    onSecondary = SanctuaryPalette.SecondaryDark,
    secondaryContainer = SanctuaryPalette.SecondaryDark,
    onSecondaryContainer = SanctuaryPalette.SecondaryLight,

    tertiary = SanctuaryPalette.TertiaryLight,
    onTertiary = SanctuaryPalette.TertiaryDark,
    tertiaryContainer = SanctuaryPalette.TertiaryDark,
    onTertiaryContainer = SanctuaryPalette.TertiaryLight,

    background = Color(0xFF121416),
    onBackground = Color(0xFFE8E6E1),

    surface = Color(0xFF1A1D20),
    onSurface = Color(0xFFE8E6E1),
    surfaceVariant = Color(0xFF2A2E32),
    onSurfaceVariant = Color(0xFFC4C0B8),

    outline = Color(0xFF8E8A82),
    outlineVariant = Color(0xFF454943),

    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),

    inverseSurface = SanctuaryPalette.Neutral,
    inverseOnSurface = SanctuaryPalette.OnNeutral,
    inversePrimary = SanctuaryPalette.PrimaryDark,
)
