package sanctuary.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import sanctuary.app.core.ui.theme.SanctuaryDimens

/** Where to show [SanctuaryButton]'s optional icon relative to the label (LTR: [Start] = left, [End] = right). */
enum class SanctuaryButtonIconPlacement {
    /** Icon before the text. */
    Start,

    /** Icon after the text. */
    End,
}

/**
 * Generic labeled action: optional [icon] on [iconPlacement] side, customizable colors and
 * [textStyle]. Pass [icon] `null` for text-only. Disabled state follows Material3 content color.
 */
@Composable
fun SanctuaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    iconPlacement: SanctuaryButtonIconPlacement = SanctuaryButtonIconPlacement.Start,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = SanctuaryDimens.space20,
        vertical = SanctuaryDimens.space12,
    ),
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = SanctuaryDimens.minTouchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.38f),
            disabledContentColor = contentColor.copy(alpha = 0.38f),
        ),
        contentPadding = contentPadding,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            when {
                icon == null -> {
                    SanctuaryButtonLabel(
                        text = text,
                        textStyle = textStyle,
                        enabled = enabled,
                    )
                }
                iconPlacement == SanctuaryButtonIconPlacement.Start -> {
                    SanctuaryButtonIcon(painter = icon)
                    Spacer(Modifier.width(SanctuaryDimens.space8))
                    SanctuaryButtonLabel(
                        text = text,
                        textStyle = textStyle,
                        enabled = enabled,
                    )
                }
                else -> {
                    SanctuaryButtonLabel(
                        text = text,
                        textStyle = textStyle,
                        enabled = enabled,
                    )
                    Spacer(Modifier.width(SanctuaryDimens.space8))
                    SanctuaryButtonIcon(painter = icon)
                }
            }
        }
    }
}

@Composable
private fun SanctuaryButtonLabel(
    text: String,
    textStyle: TextStyle,
    enabled: Boolean,
) {
    Text(
        text = text,
        style = textStyle,
        color = when {
            textStyle.color == Color.Unspecified -> LocalContentColor.current
            enabled -> textStyle.color
            else -> textStyle.color.copy(alpha = 0.38f)
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SanctuaryButtonIcon(painter: Painter) {
    Icon(
        painter = painter,
        contentDescription = null,
        modifier = Modifier.size(SanctuaryDimens.iconSmall),
        tint = LocalContentColor.current,
    )
}
