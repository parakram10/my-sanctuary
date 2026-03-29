package sanctuary.app.feature.dump.presentation.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import sanctuary.app.core.ui.components.SanctuaryCard
import sanctuary.app.core.ui.theme.SanctuaryDimens
import sanctuary.app.core.ui.theme.SanctuaryPalette
import sanctuary.app.feature.dump.presentation.state.RecordingUiModel
import sanctuary.core_ui.generated.resources.Res
import sanctuary.core_ui.generated.resources.cross
import sanctuary.core_ui.generated.resources.mic

@Composable
fun RecordingListItem(
    recording: RecordingUiModel,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SanctuaryCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(SanctuaryDimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.mic),
                contentDescription = null,
                tint = SanctuaryPalette.Primary,
                modifier = Modifier.size(SanctuaryDimens.iconMedium),
            )
            Spacer(modifier = Modifier.width(SanctuaryDimens.space12))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SanctuaryDimens.space2),
            ) {
                Text(
                    text = recording.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SanctuaryPalette.OnNeutral,
                )
                Text(
                    text = recording.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = SanctuaryPalette.OnNeutralMuted,
                )
            }
            Spacer(modifier = Modifier.width(SanctuaryDimens.space8))
            Text(
                text = recording.duration,
                style = MaterialTheme.typography.bodySmall,
                color = SanctuaryPalette.OnNeutralMuted,
            )
            IconButton(onClick = { onDelete(recording.id) }) {
                Icon(
                    painter = painterResource(Res.drawable.cross),
                    contentDescription = "Delete recording",
                    tint = SanctuaryPalette.OnNeutralMuted,
                    modifier = Modifier.size(SanctuaryDimens.iconSmall),
                )
            }
        }
    }
}
