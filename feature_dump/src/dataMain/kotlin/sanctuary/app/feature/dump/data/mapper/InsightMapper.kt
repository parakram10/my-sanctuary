package sanctuary.app.feature.dump.data.mapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import sanctuary.app.core.database.Insights
import sanctuary.app.core.database.Rate_limits
import sanctuary.app.core.database.Request_queue
import sanctuary.app.feature.dump.domain.model.Insight
import sanctuary.app.feature.dump.domain.model.InsightContent
import sanctuary.app.feature.dump.domain.model.InsightGenerationRequest
import sanctuary.app.feature.dump.domain.model.InsightStatus
import sanctuary.app.feature.dump.domain.model.RateLimit
import sanctuary.app.feature.dump.domain.model.RequestStatus

/**
 * Mapper functions for converting between database entities and domain models.
 *
 * This module provides bidirectional conversion functions for:
 * - Insights: AI-generated emotional insights with rich content
 * - RateLimits: API usage tracking
 * - InsightGenerationRequest: Queued requests for retry processing
 *
 * The mappers handle type conversions (Long ↔ Int, String ↔ Enum) and
 * complex serialization (JSON for emotions and content).
 */

// ===== Insight Mappers =====

/**
 * Converts a database Insights entity to a domain Insight model.
 *
 * - Deserializes the emotions_json string to InsightContent
 * - Converts is_archived Long to Boolean (0 = false, 1 = true)
 * - Parses status String to InsightStatus enum
 * - Maps all fields from snake_case (DB) to camelCase (domain)
 *
 * @return The domain model representation of this database entity
 */
internal fun Insights.toDomain(): Insight {
    // Deserialize the JSON-encoded insight content
    val content = Json.decodeFromString<InsightContent>(emotions_json)
    return Insight(
        id = id,
        recordingId = recording_id,
        content = content,
        createdAt = created_at,
        isArchived = (is_archived ?: 0L) != 0L, // Convert 1L to true, 0L to false
        archivedAt = archived_at,
        status = InsightStatus.valueOf(status), // Parse status string to enum
    )
}

/**
 * Converts a domain Insight model to a database Insights entity.
 *
 * - Serializes InsightContent to a JSON string for emotions_json column
 * - Converts isArchived Boolean to Long (false = 0, true = 1)
 * - Converts status enum to its String name
 * - Extracts fields from InsightContent to flat columns
 *
 * @return The database entity representation of this domain model
 */
internal fun Insight.toEntity(): Insights {
    // Serialize the insight content to JSON for storage
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
        sentiment = content.sentiment.name, // Convert enum to string
        created_at = createdAt,
        is_archived = if (isArchived) 1L else 0L, // Convert boolean to long
        archived_at = archivedAt,
        status = status.name, // Convert enum to string
    )
}

// ===== RateLimit Mappers =====

/**
 * Converts a database Rate_limits entity to a domain RateLimit model.
 *
 * - Converts Long API counters to Int (suitable for UI display)
 * - Handles nullable fields with sensible defaults:
 *   - api_calls_used defaults to 0
 *   - max_api_calls defaults to 4 (the app's limit)
 *
 * @return The domain model representation of this database entity
 */
internal fun Rate_limits.toDomain(): RateLimit = RateLimit(
    id = id,
    date = date,
    apiCallsUsed = (api_calls_used ?: 0L).toInt(), // Default to 0 if null
    maxApiCalls = (max_api_calls ?: 4L).toInt(),    // Default to 4 if null
)

/**
 * Converts a domain RateLimit model to a database Rate_limits entity.
 *
 * - Converts Int counters back to Long for database storage
 *
 * @return The database entity representation of this domain model
 */
internal fun RateLimit.toEntity(): Rate_limits = Rate_limits(
    id = id,
    date = date,
    api_calls_used = apiCallsUsed.toLong(),
    max_api_calls = maxApiCalls.toLong(),
)

// ===== InsightGenerationRequest Mappers =====

/**
 * Converts a database Request_queue entity to a domain InsightGenerationRequest model.
 *
 * - Converts Long retry counters to Int for business logic
 * - Handles nullable retry fields with sensible defaults:
 *   - retry_count defaults to 0
 *   - max_retries defaults to 3
 * - Sets transcription to empty string (will be fetched separately if needed)
 * - Parses status String to RequestStatus enum
 *
 * @return The domain model representation of this database entity
 */
internal fun Request_queue.toDomain(): InsightGenerationRequest = InsightGenerationRequest(
    id = id,
    recordingId = recording_id,
    transcription = "", // Transcription fetched separately from recordings table
    createdAt = created_at,
    retryCount = (retry_count ?: 0L).toInt(),     // Default to 0 if null
    maxRetries = (max_retries ?: 3L).toInt(),     // Default to 3 if null
    status = RequestStatus.valueOf(status),       // Parse status string to enum
    errorMessage = error_message,
)

/**
 * Converts a domain InsightGenerationRequest model to a database Request_queue entity.
 *
 * - Converts Int retry counters back to Long for database storage
 * - Converts status enum to its String name
 * - Note: Recording ID and transcription must be set by caller
 *
 * @return The database entity representation of this domain model
 */
internal fun InsightGenerationRequest.toEntity(): Request_queue = Request_queue(
    id = id,
    recording_id = recordingId,
    created_at = createdAt,
    retry_count = retryCount.toLong(),
    max_retries = maxRetries.toLong(),
    status = status.name, // Convert enum to string
    error_message = errorMessage,
)
