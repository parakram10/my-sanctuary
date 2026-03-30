package sanctuary.app.feature.dump.domain.repository

import sanctuary.app.shared.domain.usecase.UsecaseResult

interface TranscriptionRepository {
    suspend fun transcribe(filePath: String): UsecaseResult<String, Throwable>
}
