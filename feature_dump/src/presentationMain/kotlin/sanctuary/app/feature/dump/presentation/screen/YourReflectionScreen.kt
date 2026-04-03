package sanctuary.app.feature.dump.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sanctuary.app.core.ui.components.SanctuaryButton
import sanctuary.app.core.ui.theme.SanctuaryDimens
import sanctuary.app.feature.dump.presentation.state.InsightUiModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YourReflectionScreen(
    insight: InsightUiModel,
    onSaveToJournal: () -> Unit,
    onViewRecording: () -> Unit,
    onViewTranscription: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sentimentBackgroundColor = getSentimentBackgroundColor(insight.sentiment)
    val emotionChipColors = AssistChipDefaults.assistChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(SanctuaryDimens.space16),
        ) {
            // Header with sentiment background
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = SanctuaryDimens.space16),
                shape = RoundedCornerShape(SanctuaryDimens.space12),
                color = sentimentBackgroundColor,
            ) {
                Column(
                    modifier = Modifier.padding(SanctuaryDimens.space16),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = insight.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(modifier = Modifier.height(SanctuaryDimens.space4))
                            Text(
                                text = insight.sentiment,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            text = insight.recordingType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(modifier = Modifier.height(SanctuaryDimens.space8))
                    Text(
                        text = "${insight.formattedDate} at ${insight.formattedTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            // Summary Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = SanctuaryDimens.space16),
            ) {
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(SanctuaryDimens.space8))
                Text(
                    text = insight.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Full Summary Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = SanctuaryDimens.space16),
            ) {
                Text(
                    text = "Detailed Insight",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(SanctuaryDimens.space8))
                Text(
                    text = insight.fullSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Path Forward Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = SanctuaryDimens.space16),
            ) {
                Text(
                    text = "Path Forward",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(SanctuaryDimens.space8))
                Text(
                    text = insight.pathForward,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            // Emotions Section
            if (insight.emotions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = SanctuaryDimens.space16),
                ) {
                    Text(
                        text = "Emotions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(SanctuaryDimens.space8))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SanctuaryDimens.space8),
                        verticalArrangement = Arrangement.spacedBy(SanctuaryDimens.space8),
                    ) {
                        insight.emotions.take(3).forEach { emotion ->
                            AssistChip(
                                onClick = { },
                                label = { Text(emotion) },
                                colors = emotionChipColors,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SanctuaryDimens.space16),
                verticalArrangement = Arrangement.spacedBy(SanctuaryDimens.space12),
            ) {
                SanctuaryButton(
                    text = "💾 Save to Journal",
                    onClick = onSaveToJournal,
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SanctuaryDimens.space12),
                ) {
                    OutlinedButton(
                        onClick = onViewRecording,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("🎧 Recording")
                    }

                    OutlinedButton(
                        onClick = onViewTranscription,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("📝 Transcript")
                    }
                }

                OutlinedButton(
                    onClick = onRegenerate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("🔄 Regenerate")
                }
            }
        }
    }
}

@Composable
private fun getSentimentBackgroundColor(sentiment: String): Color {
    return when (sentiment.lowercase()) {
        "positive" -> MaterialTheme.colorScheme.primaryContainer
        "negative" -> Color(0xFFFFEBEE)
        "neutral" -> MaterialTheme.colorScheme.surfaceVariant
        "anxious" -> Color(0xFFFFF9C4)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}
