package sanctuary.app.feature.dump.domain.usecase

import sanctuary.app.feature.dump.domain.repository.RecordingRepository
import sanctuary.app.shared.domain.usecase.UsecaseResult

class DeleteRecordingUseCase(private val repository: RecordingRepository) {
    suspend operator fun invoke(id: String): UsecaseResult<Unit, Throwable> = runCatching {
        repository.deleteRecording(id)
    }.fold(
        onSuccess = { UsecaseResult.Success(Unit) },
        onFailure = { UsecaseResult.Failure(it) },
    )
}
