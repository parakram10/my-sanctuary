package sanctuary.app.feature.dump.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import sanctuary.app.feature.dump.domain.model.Recording
import sanctuary.app.feature.dump.domain.repository.RecordingRepository
import sanctuary.app.shared.domain.usecase.UsecaseResult

class GetRecordingsUseCase(private val repository: RecordingRepository) {
    operator fun invoke(userId: String? = null): Flow<UsecaseResult<List<Recording>, Throwable>> =
        (if (userId != null) repository.getRecordingsByUser(userId) else repository.getAllRecordings())
            .map<List<Recording>, UsecaseResult<List<Recording>, Throwable>> { UsecaseResult.Success(it) }
            .catch { emit(UsecaseResult.Failure(it)) }
}
