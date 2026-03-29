package sanctuary.app.feature.dump.domain.usecase

import sanctuary.app.feature.dump.domain.model.Recording
import sanctuary.app.feature.dump.domain.repository.RecordingRepository
import sanctuary.app.shared.domain.usecase.UsecaseResult

class SaveRecordingUseCase(private val repository: RecordingRepository) {
    suspend operator fun invoke(recording: Recording): UsecaseResult<Unit, Throwable> = runCatching {
        repository.saveRecording(recording)
    }.fold(
        onSuccess = { UsecaseResult.Success(Unit) },
        onFailure = { UsecaseResult.Failure(it) },
    )
}
