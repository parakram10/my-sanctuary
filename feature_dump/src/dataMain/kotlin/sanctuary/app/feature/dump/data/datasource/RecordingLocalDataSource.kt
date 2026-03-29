package sanctuary.app.feature.dump.data.datasource

import kotlinx.coroutines.flow.Flow
import sanctuary.app.feature.dump.domain.model.Recording

internal interface RecordingLocalDataSource {
    fun getAllRecordings(): Flow<List<Recording>>
    fun getRecordingsByUser(userId: String): Flow<List<Recording>>
    suspend fun getRecordingById(id: String): Recording?
    suspend fun insertRecording(recording: Recording)
    suspend fun deleteRecording(id: String)
}
