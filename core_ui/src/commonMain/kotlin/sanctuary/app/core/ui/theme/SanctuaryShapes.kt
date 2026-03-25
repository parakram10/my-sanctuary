package sanctuary.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
/** Pill-friendly radii aligned with the style guide (rounded bars, chips, nav). */
val SanctuaryShapes = Shapes(
    extraSmall = RoundedCornerShape(SanctuaryDimens.radiusXs),
    small = RoundedCornerShape(SanctuaryDimens.radiusSm),
    medium = RoundedCornerShape(SanctuaryDimens.radiusMd),
    large = RoundedCornerShape(SanctuaryDimens.radiusLg),
    extraLarge = RoundedCornerShape(SanctuaryDimens.radiusXl),
)

/** Full pill for search fields, primary buttons, and chips when used explicitly. */
val SanctuaryPillShape = RoundedCornerShape(percent = 50)
