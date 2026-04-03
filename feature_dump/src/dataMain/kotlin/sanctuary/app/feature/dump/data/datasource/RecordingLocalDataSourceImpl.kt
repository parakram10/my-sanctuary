package sanctuary.app.feature.dump.data.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import sanctuary.app.core.database.SanctuaryDatabase
import sanctuary.app.feature.dump.data.mapper.toDomain
import sanctuary.app.feature.dump.data.mapper.toEntity
import sanctuary.app.feature.dump.domain.model.ProcessingErrorCode
import sanctuary.app.feature.dump.domain.model.ProcessingStatus
import sanctuary.app.feature.dump.domain.model.Recording

internal class RecordingLocalDataSourceImpl(
    private val database: SanctuaryDatabase,
) : RecordingLocalDataSource {

    private val queries get() = database.recordingsQueries

    override fun getAllRecordings(): Flow<List<Recording>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }

    override fun getRecordingsByUser(userId: String): Flow<List<Recording>> =
        queries.selectByUserId(userId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getRecordingById(id: String): Recording? =
        queries.selectById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }
            .first()

    override suspend fun insertRecording(recording: Recording) {
        val entity = recording.toEntity()
        queries.insert(
            id = entity.id,
            user_id = entity.user_id,
            file_path = entity.file_path,
            duration_ms = entity.duration_ms,
            created_at = entity.created_at,
            title = entity.title,
            transcription = entity.transcription,
            processing_status = entity.processing_status,
            recording_locale = entity.recording_locale,
        )
    }

    override suspend fun deleteRecording(id: String) {
        queries.deleteById(id)
    }

    // v2 Pipeline: Processing status and checkpoint management

    override suspend fun getRecordingsByStatus(status: ProcessingStatus): List<Recording> =
        queries.selectByProcessingStatus(status.name)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }
            .first()

    override suspend fun updateProcessingStatus(
        id: String,
        status: ProcessingStatus,
        errorCode: ProcessingErrorCode?,
        errorMessage: String?,
    ) {
        queries.updateProcessingStatus(
            processing_status = status.name,
            error_code = errorCode?.name,
            id = id,
        )
    }

    override suspend fun updateTranscription(id: String, transcription: String) {
        queries.updateTranscription(
            transcription = transcription,
            id = id,
        )
    }

    override suspend fun incrementBackgroundWmAttempts(id: String) {
        queries.incrementBackgroundWmAttempts(id)
    }

    override suspend fun queryEligibleForBackgroundRetry(): List<Recording> =
        queries.queryEligibleForBackgroundRetry()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }
            .first()
}
