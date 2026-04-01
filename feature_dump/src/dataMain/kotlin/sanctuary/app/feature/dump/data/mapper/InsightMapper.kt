package sanctuary.app.feature.dump.data.mapper

import kotlinx.serialization.json.Json
import sanctuary.app.core.database.Insights
import sanctuary.app.core.database.Rate_limits
import sanctuary.app.core.database.Request_queue
import sanctuary.app.feature.dump.domain.model.Insight
import sanctuary.app.feature.dump.domain.model.InsightContent
import sanctuary.app.feature.dump.domain.model.InsightGenerationRequest
import sanctuary.app.feature.dump.domain.model.InsightStatus
import sanctuary.app.feature.dump.domain.model.RateLimit
import sanctuary.app.feature.dump.domain.model.RequestStatus

// ===== Insight Mappers =====

internal fun Insights.toDomain(): Insight {
    val content = Json.decodeFromString<InsightContent>(emotions_json)
    return Insight(
        id = id,
        recordingId = recording_id,
        content = content,
        createdAt = created_at,
        isArchived = (is_archived ?: 0L) != 0L,
        archivedAt = archived_at,
        status = InsightStatus.valueOf(status),
    )
}

internal fun Insight.toEntity(): Insights {
    val emotionsJson = Json.encodeToString(content)
    return Insights(
        id = id,
        recording_id = recordingId,
        title = content.title,
        summary = content.summary,
        full_summary = content.fullSummary,
        emotions_json = emotionsJson,
        path_forward = content.pathForward,
        recording_type = content.recordingType,
        sentiment = content.sentiment.name,
        created_at = createdAt,
        is_archived = if (isArchived) 1L else 0L,
        archived_at = archivedAt,
        status = status.name,
    )
}

// ===== RateLimit Mappers =====

internal fun Rate_limits.toDomain(): RateLimit = RateLimit(
    id = id,
    date = date,
    apiCallsUsed = (api_calls_used ?: 0L).toInt(),
    maxApiCalls = (max_api_calls ?: 4L).toInt(),
)

internal fun RateLimit.toEntity(): Rate_limits = Rate_limits(
    id = id,
    date = date,
    api_calls_used = apiCallsUsed.toLong(),
    max_api_calls = maxApiCalls.toLong(),
)

// ===== InsightGenerationRequest Mappers =====

internal fun Request_queue.toDomain(): InsightGenerationRequest = InsightGenerationRequest(
    id = id,
    recordingId = recording_id,
    transcription = "",
    createdAt = created_at,
    retryCount = (retry_count ?: 0L).toInt(),
    maxRetries = (max_retries ?: 3L).toInt(),
    status = RequestStatus.valueOf(status),
    errorMessage = error_message,
)

internal fun InsightGenerationRequest.toEntity(): Request_queue = Request_queue(
    id = id,
    recording_id = recordingId,
    created_at = createdAt,
    retry_count = retryCount.toLong(),
    max_retries = maxRetries.toLong(),
    status = status.name,
    error_message = errorMessage,
)
