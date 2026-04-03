package sanctuary.app.feature.dump.domain.repository

import kotlinx.coroutines.flow.Flow
import sanctuary.app.feature.dump.domain.model.ProcessingErrorCode
import sanctuary.app.feature.dump.domain.model.ProcessingStatus
import sanctuary.app.feature.dump.domain.model.Recording

interface RecordingRepository {
    fun getAllRecordings(): Flow<List<Recording>>
    fun getRecordingsByUser(userId: String): Flow<List<Recording>>
    suspend fun getRecording(id: String): Recording?
    suspend fun saveRecording(recording: Recording)
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

    /**
     * Query recordings eligible for WorkManager background retry.
     * Returns PENDING or FAILED recordings where:
     * - background_wm_attempts < 1 (or WM cap)
     * - If FAILED: error_code is in the transient allowlist
     */
    suspend fun queryEligibleForBackgroundRetry(): List<Recording>
}
