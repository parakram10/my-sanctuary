package sanctuary.app.feature.dump.data.repository

import kotlinx.coroutines.flow.Flow
import sanctuary.app.feature.dump.data.datasource.RecordingLocalDataSource
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
}
