package sanctuary.app.feature.dump.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sanctuary.app.feature.dump.domain.audio.AudioFileProvider
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
import sanctuary.app.shared.domain.usecase.UsecaseResult
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DumpViewModel(
    private val getRecordingsUseCase: GetRecordingsUseCase,
    private val saveRecordingUseCase: SaveRecordingUseCase,
    private val deleteRecordingUseCase: DeleteRecordingUseCase,
    private val audioRecorder: AudioRecorder,
    private val audioFileProvider: AudioFileProvider,
) : BaseStateMviViewModel<DumpViewIntent, DumpDataState, DumpViewState, DumpSideEffect>() {

    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null

    override fun initialDataState(): DumpDataState = DumpDataState()

    override suspend fun convertToUiState(dataState: DumpDataState): DumpViewState = DumpViewState(
        isRecording = dataState.recordingStatus == RecordingStatus.Recording,
        isSaving = dataState.recordingStatus == RecordingStatus.Saving,
        timerText = dataState.elapsedMs.toTimerText(),
        recordings = dataState.recordings.map { it.toUiModel() },
        amplitudes = dataState.amplitudes,
        showPermissionRationale = !dataState.permissionGranted,
    )

    override fun onViewStateActive() {
        loadRecordings()
    }

    override fun processIntent(intent: DumpViewIntent) {
        when (intent) {
            is DumpViewIntent.StartRecording -> handleStartRecording()
            is DumpViewIntent.StopRecording -> handleStopRecording()
            is DumpViewIntent.CancelRecording -> handleCancelRecording()
            is DumpViewIntent.DismissScreen -> emitSideEffect(DumpSideEffect.NavigateBack)
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

    private fun handlePermissionResult(granted: Boolean) {
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

    private fun Long.toTimerText(): String {
        val totalSeconds = this / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

    private fun Recording.toUiModel(): RecordingUiModel = RecordingUiModel(
        id = id,
        title = title ?: "Voice Note",
        duration = durationMs.toTimerText(),
        date = formatEpochMs(createdAt),
    )

    private fun formatEpochMs(epochMs: Long): String {
        val totalSeconds = epochMs / 1000
        val minutes = (totalSeconds / 60) % 60
        val hours = (totalSeconds / 3600) % 24
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
        // TODO: Replace with kotlinx-datetime for full date formatting (e.g. "Mar 29, 2026")
    }

    @OptIn(ExperimentalTime::class)
    private fun currentEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

    private fun generateId(): String =
        "${currentEpochMs()}_${kotlin.random.Random.nextInt(10000, 99999)}"
}
