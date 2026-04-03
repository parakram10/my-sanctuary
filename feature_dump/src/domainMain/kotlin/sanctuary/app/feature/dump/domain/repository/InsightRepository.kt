package sanctuary.app.feature.dump.domain.repository

import sanctuary.app.feature.dump.domain.model.Insight
import sanctuary.app.feature.dump.domain.model.InsightGenerationResult
import sanctuary.app.feature.dump.domain.model.RateLimit
import sanctuary.app.feature.dump.domain.model.InsightGenerationRequest

/**
 * High-level repository for AI-powered insight operations.
 *
 * This interface abstracts the business logic for insight generation, retrieval,
 * and management. It combines data layer operations (database access) with
 * service layer operations (API calls, rate limiting, retry logic) into a
 * cohesive interface for the presentation layer (ViewModels, Screens).
 *
 * Key responsibilities:
 * - **Insight Generation**: Call Claude API with proper rate limiting and retry
 * - **Insight Management**: CRUD operations, archival, deletion
 * - **Queue Processing**: Retry failed requests, respect rate limits
 * - **Cleanup**: Automatically delete old archived insights
 */
interface InsightRepository {
    /**
     * Generates an AI-powered insight from a user's recording.
     *
     * This is the main entry point for the insight generation feature.
     * The function will:
     * 1. Check if today's API call limit has been reached
     * 2. Call the Claude API with the transcription
     * 3. Save the generated insight to the database
     * 4. Return the result (Success, RateLimitExceeded, or Failure)
     *
     * If the request fails but is retryable, it will be queued for automatic retry.
     *
     * @param recordingId The ID of the recording to generate an insight for
     * @param transcription The transcribed text from the recording
     * @return Result indicating success, rate limit exceeded, or failure with error details
     */
    suspend fun generateInsight(
        recordingId: String,
        transcription: String
    ): InsightGenerationResult

    /**
     * Retrieves a specific insight by its ID.
     *
     * @param insightId The insight's unique identifier
     * @return The insight if found, null otherwise
     */
    suspend fun getInsight(insightId: String): Insight?

    /**
     * Retrieves the insight associated with a specific recording.
     *
     * Each recording can have at most one insight.
     *
     * @param recordingId The recording's unique identifier
     * @return The associated insight if generated, null if not yet created
     */
    suspend fun getInsightByRecording(recordingId: String): Insight?

    /**
     * Retrieves all active (non-archived) insights.
     *
     * Insights are returned in descending order by creation time (newest first).
     *
     * @return List of all active insights
     */
    suspend fun getAllInsights(): List<Insight>

    /**
     * Archives an insight so it no longer appears in the main list.
     *
     * Archived insights are retained for 15 days to allow recovery,
     * then automatically deleted.
     *
     * @param insightId The insight's unique identifier
     */
    suspend fun archiveInsight(insightId: String)

    /**
     * Restores an archived insight back to the active list.
     *
     * @param insightId The insight's unique identifier
     */
    suspend fun unarchiveInsight(insightId: String)

    /**
     * Permanently deletes an insight immediately.
     *
     * Note: Archived insights are typically deleted after 15 days automatically.
     * This method forces immediate deletion.
     *
     * @param insightId The insight's unique identifier
     */
    suspend fun deleteInsight(insightId: String)

    /**
     * Automatically deletes all archived insights older than a specified age.
     *
     * This is called periodically (typically daily) to clean up old insights.
     * Archived insights are retained for 15 days by default to allow recovery.
     *
     * @param daysOld Age threshold in days; insights older than this are deleted (default: 15)
     */
    suspend fun deleteOldInsights(daysOld: Int = 15)

    /**
     * Gets the current API rate limit status for today.
     *
     * Returns the rate limit record for today, showing:
     * - How many API calls have been made today
     * - How many calls remain before hitting the limit
     *
     * @return Current rate limit state (4 calls max per day)
     */
    suspend fun checkRateLimit(): RateLimit

    /**
     * Retrieves all pending requests in the retry queue.
     *
     * These are requests that failed previously and are waiting to be retried.
     * Returned in FIFO order (oldest first).
     *
     * @return List of pending requests ordered by creation time
     */
    suspend fun getPendingRequests(): List<InsightGenerationRequest>

    /**
     * Processes the queue of pending requests.
     *
     * This is typically called by a background job or when the app
     * comes to the foreground. It will:
     * 1. Fetch all pending requests
     * 2. Process them in order (respecting rate limits)
     * 3. Retry failed requests up to 3 times
     * 4. Remove completed or abandoned requests from the queue
     *
     * This is a background operation that should not block the UI.
     */
    suspend fun processPendingRequests()
}
