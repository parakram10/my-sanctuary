package sanctuary.app.feature.dump.presentation.state

sealed interface DumpSideEffect {
    data object RequestMicrophonePermission : DumpSideEffect
    data object NavigateBack : DumpSideEffect
    data class ShowError(val message: String) : DumpSideEffect
}
