package sanctuary.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun SanctuaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) sanctuaryDarkColorScheme() else sanctuaryLightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SanctuaryTypography,
        shapes = SanctuaryShapes,
        content = content,
    )
}
