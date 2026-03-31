package sanctuary.app.feature.dump.data.mapper

import sanctuary.app.core.database.Recordings
import sanctuary.app.feature.dump.domain.model.Recording

internal fun Recordings.toDomain(): Recording = Recording(
    id = id,
    userId = user_id,
    filePath = file_path,
    durationMs = duration_ms,
    createdAt = created_at,
    title = title,
    transcription = transcription,
)

internal fun Recording.toEntity(): Recordings = Recordings(
    id = id,
    user_id = userId,
    file_path = filePath,
    duration_ms = durationMs,
    created_at = createdAt,
    title = title,
    transcription = transcription,
)
