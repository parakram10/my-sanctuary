package sanctuary.app.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout and spacing tokens for Sanctuary. Prefer these over raw [dp] literals in UI code.
 */
object SanctuaryDimens {
    val space2: Dp = 2.dp
    val space4: Dp = 4.dp
    val space8: Dp = 8.dp
    val space12: Dp = 12.dp
    val space16: Dp = 16.dp
    val space20: Dp = 20.dp
    val space24: Dp = 24.dp
    val space32: Dp = 32.dp
    val space40: Dp = 40.dp
    val space48: Dp = 48.dp
    val space64: Dp = 64.dp

    /** Default horizontal inset for screen content. */
    val screenPaddingHorizontal: Dp = space24

    /** Default vertical inset for screen content. */
    val screenPaddingVertical: Dp = space24

    /** Padding inside cards and grouped surfaces. */
    val cardPadding: Dp = space16

    /** Minimum tap target (accessibility). */
    val minTouchTarget: Dp = 48.dp

    val iconSmall: Dp = 18.dp
    val iconMedium: Dp = 24.dp
    val iconLarge: Dp = 32.dp

    /** Matches Material [Shapes] mapping in [SanctuaryShapes]. */
    val radiusXs: Dp = 10.dp
    val radiusSm: Dp = 16.dp
    val radiusMd: Dp = 20.dp
    val radiusLg: Dp = 24.dp
    val radiusXl: Dp = 28.dp

    val borderThin: Dp = 1.dp
    val borderMedium: Dp = 2.dp

    val contentMaxWidth: Dp = 600.dp
}
