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
        )
    }

    override suspend fun deleteRecording(id: String) {
        queries.deleteById(id)
    }
}
