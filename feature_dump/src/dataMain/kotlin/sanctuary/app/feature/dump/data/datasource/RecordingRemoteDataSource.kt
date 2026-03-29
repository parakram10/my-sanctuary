package sanctuary.app.feature.dump.data.datasource

import sanctuary.app.feature.dump.domain.model.Recording

/**
 * Stub for future remote API integration via core_network (Ktor).
 * Implement this when the backend is ready and wire it into RecordingRepositoryImpl.
 */
internal interface RecordingRemoteDataSource {
    suspend fun uploadRecording(recording: Recording): Result<Unit>
    suspend fun fetchRecordingsByUser(userId: String): Result<List<Recording>>
    suspend fun deleteRecording(id: String): Result<Unit>
}
