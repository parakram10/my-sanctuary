package sanctuary.app.feature.dump.data.repository

import sanctuary.app.feature.dump.data.datasource.IosTranscriptionDataSource
import sanctuary.app.feature.dump.domain.repository.TranscriptionRepository
import sanctuary.app.shared.domain.usecase.UsecaseResult

internal class IosTranscriptionRepositoryImpl(
    private val dataSource: IosTranscriptionDataSource
) : TranscriptionRepository {
    override suspend fun transcribe(filePath: String): UsecaseResult<String, Throwable> =
        dataSource.getTranscript().fold(
            onSuccess = { UsecaseResult.Success(it) },
            onFailure = { UsecaseResult.Failure(it) }
        )
}
