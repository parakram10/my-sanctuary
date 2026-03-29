package sanctuary.app.feature.dump.presentation.state

sealed interface DumpViewIntent {
    data object StartRecording : DumpViewIntent
    data object StopRecording : DumpViewIntent
    data object CancelRecording : DumpViewIntent
    data object DismissScreen : DumpViewIntent
    data class OpenRecording(val id: String) : DumpViewIntent
    data object ToggleSelectedRecordingPlayback : DumpViewIntent
    data object DismissPlaybackDialog : DumpViewIntent
    data class DeleteRecording(val id: String) : DumpViewIntent
    data class PermissionResult(val granted: Boolean) : DumpViewIntent
}
