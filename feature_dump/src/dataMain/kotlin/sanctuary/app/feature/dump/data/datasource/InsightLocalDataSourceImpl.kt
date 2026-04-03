package sanctuary.app.feature.dump.data.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import sanctuary.app.core.database.SanctuaryDatabase
import sanctuary.app.feature.dump.data.mapper.toDomain
import sanctuary.app.feature.dump.data.mapper.toEntity
import sanctuary.app.feature.dump.domain.model.Insight
import sanctuary.app.feature.dump.domain.model.InsightStatus
import sanctuary.app.feature.dump.domain.model.RateLimit
import sanctuary.app.feature.dump.domain.model.InsightGenerationRequest
import sanctuary.app.feature.dump.domain.model.RequestStatus

/**
 * Implementation of [InsightLocalDataSource] that provides database access for AI-powered insights.
 *
 * This class serves as the data access layer for three main entities:
 * - **Insights**: Generated AI-powered emotional insights from recordings
 * - **RateLimits**: Tracks daily API call usage (max 4 calls per day)
 * - **RequestQueue**: Queues failed insight generation requests for retry
 *
 * All operations are suspend functions designed to work with Kotlin coroutines.
 * Database queries are executed on [Dispatchers.Default] for optimal performance.
 */
internal class InsightLocalDataSourceImpl(
    private val database: SanctuaryDatabase,
) : InsightLocalDataSource {

    // Lazy properties to access SQLDelight generated query objects
    private val insightsQueries get() = database.insightsQueries
    private val rateLimitsQueries get() = database.rate_limitsQueries
    private val requestQueueQueries get() = database.request_queueQueries

    // ===== Insight Operations =====

    /**
     * Saves a new insight to the database.
     *
     * The insight domain model is converted to a database entity, and all fields
     * (title, summary, emotions, sentiment, etc.) are persisted. The insight is
     * created in SAVED status and marked as not archived.
     *
     * @param insight The insight domain model to save
     */
    override suspend fun insertInsight(insight: Insight) {
        val entity = insight.toEntity()
        insightsQueries.insert(
            id = entity.id,
            recording_id = entity.recording_id,
            title = entity.title,
            summary = entity.summary,
            full_summary = entity.full_summary,
            emotions_json = entity.emotions_json,
            path_forward = entity.path_forward,
            recording_type = entity.recording_type,
            sentiment = entity.sentiment,
            created_at = entity.created_at,
            is_archived = entity.is_archived,
            status = entity.status,
        )
    }

    /**
     * Retrieves an insight by its unique identifier.
     *
     * Uses SQLDelight's Flow-based query API with [Dispatchers.Default] for efficient
     * database access. Returns null if the insight does not exist.
     *
     * @param id The unique identifier of the insight
     * @return The insight if found, null otherwise
     */
    override suspend fun getInsightById(id: String): Insight? {
        return insightsQueries.selectById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() } // Convert database entity to domain model
            .first()
    }

    /**
     * Retrieves the insight associated with a specific recording.
     *
     * Since each recording can have at most one insight, this returns a single
     * insight or null if no insight has been generated for that recording yet.
     *
     * @param recordingId The recording's unique identifier
     * @return The insight if found, null if not yet generated
     */
    override suspend fun getInsightByRecordingId(recordingId: String): Insight? {
        return insightsQueries.selectByRecordingId(recordingId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }
            .first()
    }

    /**
     * Retrieves all insights, optionally filtered by archive status.
     *
     * Active insights (archived = false) are returned in descending order by creation time.
     * Archived insights are returned in descending order by archive time.
     *
     * @param archived If true, returns archived insights; if false, returns active insights
     * @return List of insights matching the filter criteria
     */
    override suspend fun getAllInsights(archived: Boolean): List<Insight> {
        // Query either archived or active insights based on the parameter
        val entities = if (archived) {
            insightsQueries.selectArchived()
        } else {
            insightsQueries.selectAll()
        }
            .asFlow()
            .mapToList(Dispatchers.Default)
            .first()

        // Convert all database entities to domain models
        return entities.map { it.toDomain() }
    }

    /**
     * Updates the status of an insight (e.g., PENDING → GENERATING → SAVED → ARCHIVED).
     *
     * This is used to track the lifecycle of insight generation, from initial request
     * through completion.
     *
     * @param insightId The insight's unique identifier
     * @param status The new status to set
     */
    override suspend fun updateInsightStatus(insightId: String, status: InsightStatus) {
        insightsQueries.updateStatus(status.name, insightId)
    }

    /**
     * Archives an insight and records the archive timestamp.
     *
     * When an insight is archived, the is_archived flag is set to 1 and the current
     * time is recorded in archived_at. This allows users to view archived insights
     * and the system can clean up old archived insights (> 15 days old).
     *
     * Uses [Clock.System] for multiplatform time compatibility (iOS + Android).
     *
     * @param insightId The insight's unique identifier
     */
    @OptIn(ExperimentalTime::class)
    override suspend fun archiveInsight(insightId: String) {
        // Get current time in milliseconds since epoch for multiplatform support
        val currentTime = Clock.System.now().toEpochMilliseconds()
        insightsQueries.updateIsArchived(1L, currentTime, insightId)
    }

    /**
     * Restores an archived insight back to active status.
     *
     * Sets is_archived flag to 0 and clears the archived_at timestamp so the
     * insight reappears in the main insights list.
     *
     * @param insightId The insight's unique identifier
     */
    override suspend fun unarchiveInsight(insightId: String) {
        insightsQueries.updateIsArchived(0L, null, insightId)
    }

    /**
     * Deletes all insights older than a specified time.
     *
     * This is typically called as part of the auto-cleanup process to remove
     * archived insights older than 15 days. Only archived insights are deleted
     * by this query.
     *
     * @param epochMs The threshold time in milliseconds since epoch; insights
     *                created before this time will be deleted
     */
    override suspend fun deleteOlderThan(epochMs: Long) {
        insightsQueries.deleteOlderThan(epochMs)
    }

    // ===== Rate Limit Operations =====

    /**
     * Retrieves the rate limit record for a specific date.
     *
     * Rate limits are tracked per calendar day. This fetches the existing limit
     * for a given date, or returns null if no limit record exists yet (which means
     * zero API calls have been made that day).
     *
     * @param dateKey The date key (typically days since epoch)
     * @return The rate limit for that date, or null if no record exists
     */
    override suspend fun getRateLimitForDate(dateKey: Long): RateLimit? {
        return rateLimitsQueries.getByDate(dateKey)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }
            .first()
    }

    /**
     * Creates a new rate limit record for a date.
     *
     * Initializes a rate limit with api_calls_used = 0 and max_api_calls = 4.
     * This is typically called at the start of a new day.
     *
     * @param rateLimit The rate limit object to persist
     */
    override suspend fun createRateLimit(rateLimit: RateLimit) {
        val entity = rateLimit.toEntity()
        rateLimitsQueries.insert(
            id = entity.id,
            date = entity.date,
            api_calls_used = entity.api_calls_used,
            max_api_calls = entity.max_api_calls,
        )
    }

    /**
     * Increments the API call usage count for a specific date.
     *
     * First retrieves the current rate limit for the date, then increments the
     * api_calls_used counter by 1. If no rate limit exists for that date, this
     * operation does nothing (the rate limit should be created first).
     *
     * @param dateKey The date key for which to increment the counter
     */
    override suspend fun incrementApiCallsUsed(dateKey: Long) {
        val currentRateLimit = getRateLimitForDate(dateKey)
        if (currentRateLimit != null) {
            // Increment the counter and update in database
            val newCount = currentRateLimit.apiCallsUsed + 1
            rateLimitsQueries.updateCallsUsed(newCount.toLong(), dateKey)
        }
    }

    // ===== Request Queue Operations =====

    /**
     * Inserts a new request into the queue for retry processing.
     *
     * Failed insight generation requests are queued here with status PENDING
     * so they can be retried later. The request includes the recording ID and
     * initial retry count (0).
     *
     * @param request The request to queue
     */
    override suspend fun insertRequest(request: InsightGenerationRequest) {
        val entity = request.toEntity()
        requestQueueQueries.insert(
            id = entity.id,
            recording_id = entity.recording_id,
            created_at = entity.created_at,
            status = entity.status,
        )
    }

    /**
     * Retrieves all pending requests from the queue.
     *
     * Returns requests with status = 'PENDING' ordered by creation time (FIFO).
     * These requests should be processed in order, with the oldest request
     * processed first.
     *
     * @return List of pending requests ordered by creation time
     */
    override suspend fun getPendingRequests(): List<InsightGenerationRequest> {
        val entities = requestQueueQueries.selectPending()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .first()

        // Convert all database entities to domain models
        return entities.map { it.toDomain() }
    }

    /**
     * Updates the status of a request in the queue.
     *
     * Used to mark requests as PROCESSING, FAILED, or COMPLETED. When updating,
     * the retry count is reset to 0 (it will be incremented separately if needed).
     * An error message can be provided if the status is FAILED.
     *
     * @param requestId The request's unique identifier
     * @param status The new status
     * @param errorMessage Optional error message if the status is FAILED
     */
    override suspend fun updateRequestStatus(
        requestId: String,
        status: RequestStatus,
        errorMessage: String?
    ) {
        requestQueueQueries.updateStatus(
            status = status.name,
            error_message = errorMessage,
            retry_count = 0L, // Reset retry count on status update
            id = requestId,
        )
    }

    /**
     * Deletes a request from the queue.
     *
     * Used to remove completed or abandoned requests. Once a request is successfully
     * processed, it should be deleted from the queue.
     *
     * @param requestId The request's unique identifier
     */
    override suspend fun deleteRequest(requestId: String) {
        requestQueueQueries.deleteById(requestId)
    }

    /**
     * Increments the retry count for a request.
     *
     * When a request fails and needs to be retried, this increments the retry
     * counter. Once retry_count reaches max_retries (typically 3), the request
     * should be marked as FAILED and removed from the queue.
     *
     * First retrieves the complete request, increments its retry count, then
     * updates it back while preserving other fields (status, error message).
     *
     * @param requestId The request's unique identifier
     */
    override suspend fun incrementRetryCount(requestId: String) {
        // Fetch the complete request to preserve all fields
        val request = requestQueueQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .first()
            .firstOrNull { it.id == requestId }

        if (request != null) {
            // Increment retry count, handling null default
            val newRetryCount = ((request.retry_count ?: 0L) + 1L)
            requestQueueQueries.updateStatus(
                status = request.status,
                error_message = request.error_message,
                retry_count = newRetryCount,
                id = requestId,
            )
        }
    }
}
