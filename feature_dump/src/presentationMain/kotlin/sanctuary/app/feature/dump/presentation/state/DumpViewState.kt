package sanctuary.app.feature.dump.presentation.state

data class DumpViewState(
    val isRecording: Boolean = false,
    val isSaving: Boolean = false,
    val timerText: String = "00:00",
    val recordings: List<RecordingUiModel> = emptyList(),
    val selectedRecording: RecordingUiModel? = null,
    val showPlaybackDialog: Boolean = false,
    val isPlayingSelectedRecording: Boolean = false,
    val amplitudes: List<Float> = emptyList(),
    val showPermissionRationale: Boolean = false,
)
