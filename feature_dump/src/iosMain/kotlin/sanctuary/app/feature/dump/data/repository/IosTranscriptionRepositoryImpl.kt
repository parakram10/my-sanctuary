package sanctuary.app.feature.dump.data.repository

import sanctuary.app.feature.dump.domain.repository.TranscriptionRepository
import sanctuary.app.shared.domain.usecase.UsecaseResult

internal class IosTranscriptionRepositoryImpl : TranscriptionRepository {
    override suspend fun transcribe(filePath: String): UsecaseResult<String, Throwable> {
        throw NotImplementedError("iOS transcription implementation coming in Phase 4")
    }
}
