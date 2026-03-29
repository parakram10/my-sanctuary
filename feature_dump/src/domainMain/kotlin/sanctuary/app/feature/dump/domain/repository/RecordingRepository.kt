package sanctuary.app.feature.dump.domain.repository

import kotlinx.coroutines.flow.Flow
import sanctuary.app.feature.dump.domain.model.Recording

interface RecordingRepository {
    fun getAllRecordings(): Flow<List<Recording>>
    fun getRecordingsByUser(userId: String): Flow<List<Recording>>
    suspend fun getRecording(id: String): Recording?
    suspend fun saveRecording(recording: Recording)
    suspend fun deleteRecording(id: String)
}
