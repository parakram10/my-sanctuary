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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import sanctuary.app.core.ui.components.SanctuaryButton
import sanctuary.app.core.ui.theme.SanctuaryDimens
import sanctuary.app.core.ui.theme.SanctuaryPalette
import sanctuary.app.feature.dump.presentation.screen.components.ConcentricMicrophoneIcon
import sanctuary.app.feature.dump.presentation.screen.components.RecordingListItem
import sanctuary.app.feature.dump.presentation.screen.components.TimerCard
import sanctuary.app.feature.dump.presentation.screen.components.WaveformVisualizer
import sanctuary.app.feature.dump.presentation.state.DumpSideEffect
import sanctuary.app.feature.dump.presentation.state.DumpViewIntent
import sanctuary.app.feature.dump.presentation.viewmodel.DumpViewModel
import sanctuary.core_ui.generated.resources.Res
import sanctuary.core_ui.generated.resources.cross
import sanctuary.core_ui.generated.resources.lock

@Composable
fun DumpRecordingScreen(
    onNavigateBack: () -> Unit = {},
    onViewAllRecordings: () -> Unit = {},
    onRequestPermission: (onPermissionResult: (Boolean) -> Unit) -> Unit = { onResult -> onResult(false) },
    modifier: Modifier = Modifier,
    viewModel: DumpViewModel = koinViewModel(),
) {
    val viewState by viewModel.viewState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is DumpSideEffect.NavigateBack -> onNavigateBack()
                is DumpSideEffect.RequestMicrophonePermission -> {
                    onRequestPermission { granted ->
                        viewModel.processIntent(DumpViewIntent.PermissionResult(granted))
                    }
                }
                is DumpSideEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
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
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = SanctuaryDimens.screenPaddingHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    // Top bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = SanctuaryDimens.space16),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.lock),
                            contentDescription = null,
                            tint = SanctuaryPalette.OnNeutralMuted,
                            modifier = Modifier.size(SanctuaryDimens.iconSmall),
                        )
                        Text(
                            text = "ONLY YOU CAN HEAR THIS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = SanctuaryPalette.OnNeutralMuted,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = SanctuaryDimens.space8),
                        )
                        IconButton(
                            onClick = { viewModel.processIntent(DumpViewIntent.DismissScreen) },
                            modifier = Modifier.size(SanctuaryDimens.minTouchTarget),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.cross),
                                contentDescription = "Close",
                                tint = SanctuaryPalette.OnNeutralMuted,
                                modifier = Modifier.size(SanctuaryDimens.iconMedium),
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(SanctuaryDimens.space32)) }

                item {
                    // Concentric mic icon
                    ConcentricMicrophoneIcon(
                        isRecording = viewState.isRecording,
                    )
                }

                item { Spacer(modifier = Modifier.height(SanctuaryDimens.space16)) }

                if (viewState.isRecording) {
                    item {
                        // Timer card — visible only when recording
                        TimerCard(timerText = viewState.timerText)
                    }
                }

                item { Spacer(modifier = Modifier.height(SanctuaryDimens.space24)) }

                item {
                    // Waveform
                    WaveformVisualizer(
                        amplitudes = viewState.amplitudes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                    )
                }

                item { Spacer(modifier = Modifier.height(SanctuaryDimens.space24)) }

                item {
                    // "Let it out." tagline
                    Text(
                        text = "Let it out.",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                        ),
                        color = SanctuaryPalette.OnNeutral,
                    )
                }

                item { Spacer(modifier = Modifier.height(SanctuaryDimens.space32)) }

                // Recording action buttons
                if (viewState.isRecording || viewState.isSaving) {
                    item {
                        SanctuaryButton(
                            text = if (viewState.isSaving) "Saving…" else "Stop Recording",
                            onClick = { viewModel.processIntent(DumpViewIntent.StopRecording) },
                            enabled = !viewState.isSaving,
                            backgroundColor = SanctuaryPalette.PrimaryDark,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    item { Spacer(modifier = Modifier.height(SanctuaryDimens.space12)) }

                    item {
                        TextButton(
                            onClick = { viewModel.processIntent(DumpViewIntent.CancelRecording) },
                            enabled = !viewState.isSaving,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = SanctuaryPalette.OnNeutralMuted,
                            ),
                        ) {
                            Text(
                                text = "CANCEL",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    letterSpacing = 1.sp,
                                ),
                            )
                        }
                    }
                } else {
                    item {
                        SanctuaryButton(
                            text = "Start Recording",
                            onClick = { viewModel.processIntent(DumpViewIntent.StartRecording) },
                            backgroundColor = SanctuaryPalette.PrimaryDark,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Recent recordings section
                if (viewState.recordings.isNotEmpty()) {
                    item { Spacer(modifier = Modifier.height(SanctuaryDimens.space32)) }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "RECENT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = SanctuaryPalette.OnNeutralMuted,
                            )
                            TextButton(
                                onClick = onViewAllRecordings,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = SanctuaryPalette.Primary,
                                ),
                            ) {
                                Text(
                                    text = "See all",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 0.5.sp,
                                    ),
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(SanctuaryDimens.space8)) }

                    items(
                        items = viewState.recordings.take(3),
                        key = { it.id },
                    ) { recording ->
                        RecordingListItem(
                            recording = recording,
                            onClick = { viewModel.processIntent(DumpViewIntent.OpenRecording(it)) },
                            onDelete = { viewModel.processIntent(DumpViewIntent.DeleteRecording(it)) },
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(SanctuaryDimens.space16)) }
            }
        }
    }

    val selectedRecording = viewState.selectedRecording
    if (viewState.showPlaybackDialog && selectedRecording != null) {
        AlertDialog(
            onDismissRequest = { viewModel.processIntent(DumpViewIntent.DismissPlaybackDialog) },
            title = { Text(selectedRecording.title) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SanctuaryDimens.space8),
                ) {
                    Text(
                        text = "Duration: ${selectedRecording.duration}",
                        color = SanctuaryPalette.OnNeutralMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Recorded: ${selectedRecording.date}",
                        color = SanctuaryPalette.OnNeutralMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    selectedRecording.transcription?.let { transcription ->
                        if (transcription.isNotBlank()) {
                            Spacer(modifier = Modifier.height(SanctuaryDimens.space8))
                            Text(
                                text = "Transcription:",
                                style = MaterialTheme.typography.labelSmall,
                                color = SanctuaryPalette.OnNeutral,
                            )
                            Text(
                                text = transcription,
                                color = SanctuaryPalette.OnNeutralMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.processIntent(DumpViewIntent.ToggleSelectedRecordingPlayback)
                    },
                ) {
                    Text(if (viewState.isPlayingSelectedRecording) "Stop" else "Play")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.processIntent(DumpViewIntent.DismissPlaybackDialog) },
                ) {
                    Text("Close")
                }
            },
        )
    }
}
