package sanctuary.app.feature.dump.data.datasource

import sanctuary.app.feature.dump.domain.model.Insight
import sanctuary.app.feature.dump.domain.model.InsightStatus
import sanctuary.app.feature.dump.domain.model.RateLimit
import sanctuary.app.feature.dump.domain.model.InsightGenerationRequest
import sanctuary.app.feature.dump.domain.model.RequestStatus

/**
 * Data source contract for local storage operations on AI-generated insights.
 *
 * This interface defines low-level database operations for three main entities:
 * - **Insights**: AI-powered emotional insights generated from user recordings
 * - **RateLimits**: Tracks API usage to enforce a 4-calls-per-day limit
 * - **RequestQueue**: Queues failed requests for automatic retry
 *
 * All methods are suspend functions designed for coroutine-based async access.
 * Implementations should ensure thread-safe database operations.
 */
internal interface InsightLocalDataSource {
    // ===== Insight Operations =====

    /**
     * Saves a new AI-generated insight to the database.
     * @param insight The insight domain model to persist
     */
    suspend fun insertInsight(insight: Insight)

    /**
     * Retrieves an insight by its unique ID.
     * @param id The insight's unique identifier
     * @return The insight if found, null otherwise
     */
    suspend fun getInsightById(id: String): Insight?

    /**
     * Retrieves the insight associated with a specific recording.
     * Each recording can have at most one insight.
     * @param recordingId The recording's unique identifier
     * @return The associated insight if generated, null if not yet created
     */
    suspend fun getInsightByRecordingId(recordingId: String): Insight?

    /**
     * Retrieves all insights, optionally filtered by archive status.
     * Active insights are returned in descending creation-time order.
     * Archived insights are returned in descending archive-time order.
     * @param archived If true, returns archived insights; if false, returns active insights (default)
     * @return List of insights matching the filter criteria
     */
    suspend fun getAllInsights(archived: Boolean = false): List<Insight>

    /**
     * Updates the status of an insight (PENDING, GENERATING, SAVED, or ARCHIVED).
     * @param insightId The insight's unique identifier
     * @param status The new status to set
     */
    suspend fun updateInsightStatus(insightId: String, status: InsightStatus)

    /**
     * Archives an insight with a timestamp.
     * Archived insights are hidden from the main view but retained for 15 days.
     * @param insightId The insight's unique identifier
     */
    suspend fun archiveInsight(insightId: String)

    /**
     * Restores an archived insight back to active status.
     * @param insightId The insight's unique identifier
     */
    suspend fun unarchiveInsight(insightId: String)

    /**
     * Deletes all insights older than a specified epoch time.
     * Typically called as part of the auto-cleanup process for archived insights.
     * @param epochMs The threshold time in milliseconds since epoch
     */
    suspend fun deleteOlderThan(epochMs: Long)

    // ===== Rate Limit Operations =====

    /**
     * Retrieves the rate limit record for a specific date.
     * Rate limits are tracked per calendar day with a max of 4 API calls.
     * @param dateKey The date key (typically days since epoch)
     * @return The rate limit for that date, or null if no record exists yet
     */
    suspend fun getRateLimitForDate(dateKey: Long): RateLimit?

    /**
     * Creates a new rate limit record for a date.
     * Initializes with api_calls_used = 0 and max_api_calls = 4.
     * @param rateLimit The rate limit object to persist
     */
    suspend fun createRateLimit(rateLimit: RateLimit)

    /**
     * Increments the API call counter for a specific date.
     * Should only be called after an API call is made to track usage.
     * @param dateKey The date key for which to increment the counter
     */
    suspend fun incrementApiCallsUsed(dateKey: Long)

    // ===== Request Queue Operations =====

    /**
     * Inserts a new request into the retry queue.
     * Failed insight generation requests are queued here for retry processing.
     * @param request The request to queue
     */
    suspend fun insertRequest(request: InsightGenerationRequest)

    /**
     * Retrieves all pending requests from the queue.
     * Returns requests in FIFO order (oldest first) for fair processing.
     * @return List of pending requests ordered by creation time
     */
    suspend fun getPendingRequests(): List<InsightGenerationRequest>

    /**
     * Updates a request's status and optionally records an error message.
     * Used to track request lifecycle: PENDING → PROCESSING → FAILED/COMPLETED.
     * @param requestId The request's unique identifier
     * @param status The new status
     * @param errorMessage Optional error message if status is FAILED
     */
    suspend fun updateRequestStatus(
        requestId: String,
        status: RequestStatus,
        errorMessage: String? = null
    )

    /**
     * Deletes a request from the queue.
     * Called after successful processing or when max retries exceeded.
     * @param requestId The request's unique identifier
     */
    suspend fun deleteRequest(requestId: String)

    /**
     * Increments the retry count for a failed request.
     * Once retry_count reaches max_retries (3), the request should be marked FAILED.
     * @param requestId The request's unique identifier
     */
    suspend fun incrementRetryCount(requestId: String)
}
