package sanctuary.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import sanctuary.app.core.ui.theme.SanctuaryDimens
import sanctuary.app.core.ui.theme.SanctuaryPalette
import sanctuary.core_ui.generated.resources.Res
import sanctuary.core_ui.generated.resources.history_selected
import sanctuary.core_ui.generated.resources.history_unselected
import sanctuary.core_ui.generated.resources.home_selected
import sanctuary.core_ui.generated.resources.home_unselected
import sanctuary.core_ui.generated.resources.setting_selected
import sanctuary.core_ui.generated.resources.settings_unselected

/**
 * Bottom tabs: filled icon + bold caps + dot when selected; outline icon + regular caps when idle.
 * Drawables: [home_selected], [home_unselected], [history_selected], [history_unselected],
 * [setting_selected], [settings_unselected].
 */
enum class BottomNavDestination {
    Home,
    History,
    Settings;

    val label: String
        get() = when (this) {
            Home -> "HOME"
            History -> "HISTORY"
            Settings -> "SETTINGS"
        }

    val contentDescription: String
        get() = when (this) {
            Home -> "Home"
            History -> "History"
            Settings -> "Settings"
        }

    val iconFilled: DrawableResource
        get() = when (this) {
            Home -> Res.drawable.home_selected
            History -> Res.drawable.history_selected
            Settings -> Res.drawable.setting_selected
        }

    val iconOutline: DrawableResource
        get() = when (this) {
            Home -> Res.drawable.home_unselected
            History -> Res.drawable.history_unselected
            Settings -> Res.drawable.settings_unselected
        }
}

@Composable
fun BottomNavigationBar(
    selected: BottomNavDestination,
    onDestinationSelected: (BottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.surface,
) {
    val active = SanctuaryPalette.BottomNavActive
    val inactive = SanctuaryPalette.BottomNavInactive

    Surface(
        modifier = modifier
            .padding(horizontal = SanctuaryDimens.space16),
        color = barColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SanctuaryDimens.space8,
                    vertical = SanctuaryDimens.space12,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            BottomNavDestination.entries.forEach { destination ->
                val isSelected = destination == selected
                val color = if (isSelected) active else inactive
                val iconRes = if (isSelected) destination.iconFilled else destination.iconOutline

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SanctuaryDimens.minTouchTarget)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onDestinationSelected(destination) },
                        )
                        .padding(vertical = SanctuaryDimens.space4),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = destination.contentDescription,
                        modifier = Modifier.size(SanctuaryDimens.iconMedium),
                        tint = color,
                    )
                    Spacer(Modifier.height(SanctuaryDimens.space8))
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            letterSpacing = 0.6.sp,
                        ),
                        color = color,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(SanctuaryDimens.space4))
                    Box(
                        modifier = Modifier.size(width = 8.dp, height = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(active),
                            )
                        }
                    }
                }
            }
        }
    }
}
