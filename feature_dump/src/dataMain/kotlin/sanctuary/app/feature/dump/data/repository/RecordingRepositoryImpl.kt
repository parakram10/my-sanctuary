package sanctuary.app.feature.dump.data.repository

import kotlinx.coroutines.flow.Flow
import sanctuary.app.feature.dump.data.datasource.RecordingLocalDataSource
import sanctuary.app.feature.dump.domain.model.ProcessingErrorCode
import sanctuary.app.feature.dump.domain.model.ProcessingStatus
import sanctuary.app.feature.dump.domain.model.Recording
import sanctuary.app.feature.dump.domain.repository.RecordingRepository

internal class RecordingRepositoryImpl(
    private val localDataSource: RecordingLocalDataSource,
    // Future: inject RecordingRemoteDataSource here for sync
) : RecordingRepository {

    override fun getAllRecordings(): Flow<List<Recording>> =
        localDataSource.getAllRecordings()

    override fun getRecordingsByUser(userId: String): Flow<List<Recording>> =
        localDataSource.getRecordingsByUser(userId)

    override suspend fun getRecording(id: String): Recording? =
        localDataSource.getRecordingById(id)

    override suspend fun saveRecording(recording: Recording) {
        localDataSource.insertRecording(recording)
        // Future: remoteDataSource.uploadRecording(recording)
    }

    override suspend fun deleteRecording(id: String) {
        localDataSource.deleteRecording(id)
        // Future: remoteDataSource.deleteRecording(id)
    }

    // v2 Pipeline: Processing status and checkpoint management

    override suspend fun getRecordingsByStatus(status: ProcessingStatus): List<Recording> =
        localDataSource.getRecordingsByStatus(status)

    override suspend fun updateProcessingStatus(
        id: String,
        status: ProcessingStatus,
        errorCode: ProcessingErrorCode?,
        errorMessage: String?,
    ) {
        localDataSource.updateProcessingStatus(id, status, errorCode, errorMessage)
    }

    override suspend fun updateTranscription(id: String, transcription: String) {
        localDataSource.updateTranscription(id, transcription)
    }

    override suspend fun incrementBackgroundWmAttempts(id: String) {
        localDataSource.incrementBackgroundWmAttempts(id)
    }

    override suspend fun queryEligibleForBackgroundRetry(): List<Recording> =
        localDataSource.queryEligibleForBackgroundRetry()
}
