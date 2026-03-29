package sanctuary.app.feature.dump.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sanctuary.app.feature.dump.data.preferences.PermissionsPreferences
import sanctuary.app.feature.dump.domain.audio.AudioFileProvider
import sanctuary.app.feature.dump.domain.audio.AudioPlayer
import sanctuary.app.feature.dump.domain.audio.AudioRecorder
import sanctuary.app.feature.dump.domain.model.Recording
import sanctuary.app.feature.dump.domain.usecase.DeleteRecordingUseCase
import sanctuary.app.feature.dump.domain.usecase.GetRecordingsUseCase
import sanctuary.app.feature.dump.domain.usecase.SaveRecordingUseCase
import sanctuary.app.feature.dump.presentation.state.DumpDataState
import sanctuary.app.feature.dump.presentation.state.DumpSideEffect
import sanctuary.app.feature.dump.presentation.state.DumpViewIntent
import sanctuary.app.feature.dump.presentation.state.DumpViewState
import sanctuary.app.feature.dump.presentation.state.RecordingStatus
import sanctuary.app.feature.dump.presentation.state.RecordingUiModel
import sanctuary.app.core.ui.viewmodel.BaseStateMviViewModel
import sanctuary.app.feature.dump.presentation.utils.TimeUtils
import sanctuary.app.feature.dump.presentation.utils.TimeUtils.toTimerText
import sanctuary.app.shared.domain.usecase.UsecaseResult

class DumpViewModel(
    private val getRecordingsUseCase: GetRecordingsUseCase,
    private val saveRecordingUseCase: SaveRecordingUseCase,
    private val deleteRecordingUseCase: DeleteRecordingUseCase,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
    private val audioFileProvider: AudioFileProvider,
    private val permissionsPreferences: PermissionsPreferences,
) : BaseStateMviViewModel<DumpViewIntent, DumpDataState, DumpViewState, DumpSideEffect>() {

    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null
    private var playbackMonitorJob: Job? = null

    init {
        loadRecordings()
    }

    override fun initialDataState(): DumpDataState = DumpDataState(
        permissionGranted = permissionsPreferences.isMicrophonePermissionGranted()
    )

    override suspend fun convertToUiState(dataState: DumpDataState): DumpViewState = DumpViewState(
        isRecording = dataState.recordingStatus == RecordingStatus.Recording,
        isSaving = dataState.recordingStatus == RecordingStatus.Saving,
        timerText = dataState.elapsedMs.toTimerText(),
        recordings = dataState.recordings.map { it.toUiModel() },
        selectedRecording = dataState.selectedRecordingId
            ?.let { id -> dataState.recordings.firstOrNull { it.id == id } }
            ?.toUiModel(),
        showPlaybackDialog = dataState.showPlaybackDialog,
        isPlayingSelectedRecording = dataState.isPlayingSelectedRecording,
        amplitudes = dataState.amplitudes,
        showPermissionRationale = !dataState.permissionGranted,
    )

    override fun processIntent(intent: DumpViewIntent) {
        when (intent) {
            is DumpViewIntent.StartRecording -> handleStartRecording()
            is DumpViewIntent.StopRecording -> handleStopRecording()
            is DumpViewIntent.CancelRecording -> handleCancelRecording()
            is DumpViewIntent.DismissScreen -> emitSideEffect(DumpSideEffect.NavigateBack)
            is DumpViewIntent.OpenRecording -> handleOpenRecording(intent.id)
            is DumpViewIntent.ToggleSelectedRecordingPlayback -> handleToggleSelectedPlayback()
            is DumpViewIntent.DismissPlaybackDialog -> handleDismissPlaybackDialog()
            is DumpViewIntent.DeleteRecording -> handleDeleteRecording(intent.id)
            is DumpViewIntent.PermissionResult -> handlePermissionResult(intent.granted)
        }
    }

    private fun loadRecordings() {
        repeatOnStarted {
            getRecordingsUseCase().collect { result ->
                when (result) {
                    is UsecaseResult.Success -> updateState { it.copy(recordings = result.data) }
                    is UsecaseResult.Failure -> emitSideEffect(DumpSideEffect.ShowError("Failed to load recordings"))
                }
            }
        }
    }

    private fun handleStartRecording() {
        if (!dataState.value.permissionGranted) {
            emitSideEffect(DumpSideEffect.RequestMicrophonePermission)
            return
        }
        val filePath = audioFileProvider.newRecordingFilePath()
        audioRecorder.startRecording(filePath)
        updateState {
            it.copy(
                recordingStatus = RecordingStatus.Recording,
                elapsedMs = 0L,
                currentFilePath = filePath,
                amplitudes = emptyList(),
            )
        }
        startTimer()
        startAmplitudeCollection()
    }

    private fun handleStopRecording() {
        timerJob?.cancel()
        amplitudeJob?.cancel()
        audioRecorder.stopRecording()

        val filePath = dataState.value.currentFilePath ?: return
        val durationMs = dataState.value.elapsedMs

        updateState { it.copy(recordingStatus = RecordingStatus.Saving) }

        viewModelScope.launch {
            val recording = Recording(
                id = generateId(),
                userId = null,
                filePath = filePath,
                durationMs = durationMs,
                createdAt = currentEpochMs(),
                title = null,
            )
            when (val result = saveRecordingUseCase(recording)) {
                is UsecaseResult.Success -> Unit
                is UsecaseResult.Failure -> emitSideEffect(DumpSideEffect.ShowError("Failed to save recording"))
            }
            updateState { it.copy(recordingStatus = RecordingStatus.Idle, currentFilePath = null, elapsedMs = 0L) }
        }
    }

    private fun handleCancelRecording() {
        timerJob?.cancel()
        amplitudeJob?.cancel()
        audioRecorder.cancelRecording()
        dataState.value.currentFilePath?.let { audioFileProvider.deleteFile(it) }
        updateState {
            it.copy(
                recordingStatus = RecordingStatus.Idle,
                currentFilePath = null,
                elapsedMs = 0L,
                amplitudes = emptyList(),
            )
        }
    }

    private fun handleDeleteRecording(id: String) {
        viewModelScope.launch {
            when (deleteRecordingUseCase(id)) {
                is UsecaseResult.Success -> Unit
                is UsecaseResult.Failure -> emitSideEffect(DumpSideEffect.ShowError("Failed to delete recording"))
            }
        }
    }

    private fun handleOpenRecording(id: String) {
        val recording = dataState.value.recordings.firstOrNull { it.id == id } ?: return
        playbackMonitorJob?.cancel()
        audioPlayer.stop()
        val started = audioPlayer.play(recording.filePath)
        updateState {
            it.copy(
                selectedRecordingId = id,
                showPlaybackDialog = true,
                isPlayingSelectedRecording = started,
            )
        }
        if (started) {
            monitorPlaybackCompletion()
        } else {
            emitSideEffect(DumpSideEffect.ShowError("Unable to play this recording"))
        }
    }

    private fun handleToggleSelectedPlayback() {
        val state = dataState.value
        val selectedId = state.selectedRecordingId ?: return
        val recording = state.recordings.firstOrNull { it.id == selectedId } ?: return

        if (state.isPlayingSelectedRecording) {
            playbackMonitorJob?.cancel()
            audioPlayer.stop()
            updateState { it.copy(isPlayingSelectedRecording = false) }
            return
        }

        val started = audioPlayer.play(recording.filePath)
        updateState { it.copy(isPlayingSelectedRecording = started) }
        if (started) {
            monitorPlaybackCompletion()
        } else {
            emitSideEffect(DumpSideEffect.ShowError("Unable to play this recording"))
        }
    }

    private fun handleDismissPlaybackDialog() {
        playbackMonitorJob?.cancel()
        audioPlayer.stop()
        updateState {
            it.copy(
                selectedRecordingId = null,
                showPlaybackDialog = false,
                isPlayingSelectedRecording = false,
            )
        }
    }

    private fun handlePermissionResult(granted: Boolean) {
        permissionsPreferences.setMicrophonePermissionGranted(granted)
        updateState { it.copy(permissionGranted = granted) }
        if (granted) handleStartRecording()
    }

    private fun startTimer() {
        timerJob = repeatOnStarted {
            while (audioRecorder.isRecording()) {
                delay(1000)
                updateState { it.copy(elapsedMs = it.elapsedMs + 1000L) }
            }
        }
    }

    private fun startAmplitudeCollection() {
        amplitudeJob = repeatOnStarted {
            audioRecorder.amplitudeFlow().collect { amplitude ->
                updateState { state ->
                    val updated = (state.amplitudes + amplitude).takeLast(30)
                    state.copy(amplitudes = updated)
                }
            }
        }
    }

    private fun monitorPlaybackCompletion() {
        playbackMonitorJob?.cancel()
        playbackMonitorJob = repeatOnStarted {
            while (audioPlayer.isPlaying()) {
                delay(250)
            }
            updateState { it.copy(isPlayingSelectedRecording = false) }
        }
    }

    private fun Recording.toUiModel(): RecordingUiModel = RecordingUiModel(
        id = id,
        title = title ?: "Voice Note",
        duration = durationMs.toTimerText(),
        date = TimeUtils.formatEpochMs(createdAt),
    )

    private fun currentEpochMs(): Long = TimeUtils.currentEpochMs()

    private fun generateId(): String =
        "${currentEpochMs()}_${kotlin.random.Random.nextInt(10000, 99999)}"

    override fun onDestroy() {
        playbackMonitorJob?.cancel()
        audioPlayer.stop()
    }
}
