package sanctuary.app.feature.dump.presentation.state

import sanctuary.app.feature.dump.domain.model.Recording

data class DumpDataState(
    val recordingStatus: RecordingStatus = RecordingStatus.Idle,
    val elapsedMs: Long = 0L,
    val currentFilePath: String? = null,
    val recordings: List<Recording> = emptyList(),
    val selectedRecordingId: String? = null,
    val showPlaybackDialog: Boolean = false,
    val isPlayingSelectedRecording: Boolean = false,
    val permissionGranted: Boolean = false,
    val amplitudes: List<Float> = emptyList(),
)
