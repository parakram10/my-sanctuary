package sanctuary.app.feature.dump.data.datasource

import kotlinx.coroutines.flow.Flow
import sanctuary.app.feature.dump.domain.model.ProcessingErrorCode
import sanctuary.app.feature.dump.domain.model.ProcessingStatus
import sanctuary.app.feature.dump.domain.model.Recording

internal interface RecordingLocalDataSource {
    fun getAllRecordings(): Flow<List<Recording>>
    fun getRecordingsByUser(userId: String): Flow<List<Recording>>
    suspend fun getRecordingById(id: String): Recording?
    suspend fun insertRecording(recording: Recording)
    suspend fun deleteRecording(id: String)

    // v2 Pipeline: Processing status and checkpoint management
    suspend fun getRecordingsByStatus(status: ProcessingStatus): List<Recording>
    suspend fun updateProcessingStatus(
        id: String,
        status: ProcessingStatus,
        errorCode: ProcessingErrorCode? = null,
        errorMessage: String? = null,
    )
    suspend fun updateTranscription(id: String, transcription: String)
    suspend fun incrementBackgroundWmAttempts(id: String)
    suspend fun queryEligibleForBackgroundRetry(): List<Recording>
}
