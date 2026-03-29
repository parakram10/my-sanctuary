package sanctuary.app.feature.dump.domain.usecase

import kotlinx.coroutines.flow.Flow
import sanctuary.app.feature.dump.domain.model.Recording
import sanctuary.app.feature.dump.domain.repository.RecordingRepository

class GetRecordingsUseCase(private val repository: RecordingRepository) {
    operator fun invoke(userId: String? = null): Flow<List<Recording>> =
        if (userId != null) repository.getRecordingsByUser(userId)
        else repository.getAllRecordings()
}
