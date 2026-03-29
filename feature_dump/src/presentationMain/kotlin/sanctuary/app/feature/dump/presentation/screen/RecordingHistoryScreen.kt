package sanctuary.app.feature.dump.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import sanctuary.app.core.ui.theme.SanctuaryDimens
import sanctuary.app.core.ui.theme.SanctuaryPalette
import sanctuary.app.feature.dump.presentation.screen.components.RecordingListItem
import sanctuary.app.feature.dump.presentation.state.DumpSideEffect
import sanctuary.app.feature.dump.presentation.state.DumpViewIntent
import sanctuary.app.feature.dump.presentation.viewmodel.DumpViewModel
import sanctuary.core_ui.generated.resources.Res
import sanctuary.core_ui.generated.resources.back

@Composable
fun RecordingHistoryScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DumpViewModel = koinViewModel(),
) {
    val viewState by viewModel.viewState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is DumpSideEffect.NavigateBack -> onNavigateBack()
                else -> {} // Handle other effects if needed
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE8F0F8), // light blue at top
                        Color(0xFFF3E8F1), // light pink at bottom
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = SanctuaryDimens.screenPaddingHorizontal),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SanctuaryDimens.space16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(SanctuaryDimens.minTouchTarget),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.back),
                        contentDescription = "Back",
                        tint = SanctuaryPalette.OnNeutral,
                        modifier = Modifier.size(SanctuaryDimens.iconMedium),
                    )
                }
                Text(
                    text = "RECORDINGS",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = SanctuaryPalette.OnNeutral,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = SanctuaryDimens.space12),
                )
            }

            Spacer(modifier = Modifier.height(SanctuaryDimens.space16))

            // Recordings list
            if (viewState.recordings.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(SanctuaryDimens.space32),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No recordings yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SanctuaryPalette.OnNeutralMuted,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(SanctuaryDimens.space8),
                ) {
                    items(
                        items = viewState.recordings,
                        key = { it.id },
                    ) { recording ->
                        RecordingListItem(
                            recording = recording,
                            onClick = { viewModel.processIntent(DumpViewIntent.OpenRecording(it)) },
                            onDelete = { viewModel.processIntent(DumpViewIntent.DeleteRecording(it)) },
                        )
                    }
                }
            }
        }
    }
}
