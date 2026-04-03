package sanctuary.app.feature.dump.data.repository

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import sanctuary.app.feature.dump.data.datasource.InsightLocalDataSource
import sanctuary.app.feature.dump.domain.model.Insight
import sanctuary.app.feature.dump.domain.model.InsightGenerationResult
import sanctuary.app.feature.dump.domain.model.InsightGenerationRequest
import sanctuary.app.feature.dump.domain.model.InsightStatus
import sanctuary.app.feature.dump.domain.model.RateLimit
import sanctuary.app.feature.dump.domain.model.RequestStatus
import sanctuary.app.feature.dump.domain.repository.InsightRepository
import sanctuary.app.feature.dump.domain.service.InsightGenerationService

/**
 * Implementation of [InsightRepository] that orchestrates insight generation,
 * storage, rate limiting, and queue processing.
 *
 * This repository is the main orchestrator between the presentation layer (ViewModels)
 * and the lower-level data layer (database) + service layer (AI API).
 *
 * Key responsibilities:
 * - **Rate Limiting**: Enforce 4-calls-per-day limit on Claude API
 * - **Insight Generation**: Call AI service, save results, handle errors
 * - **Retry Queue**: Queue failed requests and process them automatically
 * - **Cleanup**: Automatically delete archived insights older than 15 days
 * - **CRUD Operations**: Full insight lifecycle management
 *
 * The repository uses dependency injection for the data source and AI service,
 * making it testable and loosely coupled.
 */
internal class InsightRepositoryImpl(
    private val localDataSource: InsightLocalDataSource,
    private val generationService: InsightGenerationService,
) : InsightRepository {

    /**
     * Generates an AI-powered insight from a user's recording.
     *
     * This is the main entry point for the insight generation feature.
     * The process:
     * 1. Calculate today's date key
     * 2. Fetch or create today's rate limit record
     * 3. Check if API call limit (4/day) has been reached
     * 4. If limit OK, call the AI service to generate the insight
     * 5. Save the insight to the database
     * 6. Increment today's API call counter
     * 7. Return the generated insight
     *
     * If any step fails with a retryable error, a request is queued for automatic
     * retry later (up to 3 times).
     *
     * @param recordingId The recording to generate an insight for
     * @param transcription The transcribed text from the recording
     * @return Result containing: Success(insight), RateLimitExceeded, or Failure(error, retryable)
     */
    @OptIn(ExperimentalTime::class)
    override suspend fun generateInsight(
        recordingId: String,
        transcription: String
    ): InsightGenerationResult {
        return try {
            // Get today's date key and rate limit
            val todayDateKey = getTodayDateKey()
            val rateLimit = checkRateLimit()

            // Check if daily API call limit has been reached
            if (rateLimit.isLimitReached()) {
                return InsightGenerationResult.RateLimitExceeded(
                    remainingCalls = rateLimit.remainingCalls()
                )
            }

            // Call the AI service to generate the insight
            val generatedInsight = generationService.generateInsight(
                recordingId = recordingId,
                transcription = transcription
            )

            // Save the generated insight to the database
            localDataSource.insertInsight(generatedInsight)

            // Increment today's API call counter
            localDataSource.incrementApiCallsUsed(todayDateKey)

            // Return the successful result
            InsightGenerationResult.Success(generatedInsight)

        } catch (e: Exception) {
            // Determine if the error is retryable
            val isRetryable = isErrorRetryable(e)

            // If retryable, queue the request for later retry
            if (isRetryable) {
                @OptIn(ExperimentalUuidApi::class)
                val request = InsightGenerationRequest(
                    id = Uuid.random().toString(),
                    recordingId = recordingId,
                    transcription = transcription,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                    status = RequestStatus.PENDING,
                )
                localDataSource.insertRequest(request)
            }

            // Return the error result
            InsightGenerationResult.Failure(
                error = e.message ?: "Unknown error occurred",
                isRetryable = isRetryable
            )
        }
    }

    /**
     * Retrieves a specific insight by its unique ID.
     *
     * @param insightId The insight's identifier
     * @return The insight if found, null otherwise
     */
    override suspend fun getInsight(insightId: String): Insight? {
        return localDataSource.getInsightById(insightId)
    }

    /**
     * Retrieves the insight associated with a specific recording.
     *
     * Each recording can have at most one insight generated.
     * This is useful for checking if a recording already has an insight.
     *
     * @param recordingId The recording's identifier
     * @return The associated insight if generated, null if not yet created
     */
    override suspend fun getInsightByRecording(recordingId: String): Insight? {
        return localDataSource.getInsightByRecordingId(recordingId)
    }

    /**
     * Retrieves all active (non-archived) insights.
     *
     * Insights are returned in descending order by creation time (newest first).
     * This is the main source of data for the "My Journey" screen showing
     * all of the user's insights.
     *
     * @return List of all active insights, newest first
     */
    override suspend fun getAllInsights(): List<Insight> {
        return localDataSource.getAllInsights(archived = false)
    }

    /**
     * Archives an insight so it no longer appears in the main list.
     *
     * Archived insights are hidden from view but retained for 15 days
     * to allow recovery. After 15 days, they are automatically deleted.
     *
     * @param insightId The insight to archive
     */
    override suspend fun archiveInsight(insightId: String) {
        localDataSource.archiveInsight(insightId)
    }

    /**
     * Restores an archived insight back to the active list.
     *
     * This allows users to recover accidentally archived insights.
     *
     * @param insightId The insight to unarchive
     */
    override suspend fun unarchiveInsight(insightId: String) {
        localDataSource.unarchiveInsight(insightId)
    }

    /**
     * Permanently deletes an insight immediately.
     *
     * Note: Archived insights are typically deleted automatically after 15 days
     * by the deleteOldInsights() method. This method allows manual deletion.
     *
     * @param insightId The insight to delete
     */
    override suspend fun deleteInsight(insightId: String) {
        localDataSource.deleteOlderThan(0L) // Delete all older than epoch (all of them)
    }

    /**
     * Deletes all archived insights older than a specified age.
     *
     * This is the auto-cleanup mechanism that removes old archived insights.
     * By default, insights archived more than 15 days ago are deleted.
     *
     * This should be called:
     * - Daily by a background job
     * - When the app comes to the foreground
     * - On demand via settings
     *
     * @param daysOld Age threshold in days; insights archived longer ago are deleted
     */
    @OptIn(ExperimentalTime::class)
    override suspend fun deleteOldInsights(daysOld: Int) {
        // Calculate the cutoff time: current time minus daysOld days
        val currentTimeMs = Clock.System.now().toEpochMilliseconds()
        val millisPerDay = 24 * 60 * 60 * 1000L
        val cutoffTimeMs = currentTimeMs - (daysOld * millisPerDay)

        // Delete all insights older than the cutoff
        localDataSource.deleteOlderThan(cutoffTimeMs)
    }

    /**
     * Gets the current API rate limit status for today.
     *
     * Returns information about today's API call usage:
     * - How many calls have been made
     * - How many calls remain
     * - When the limit will reset (midnight UTC)
     *
     * If no rate limit record exists yet for today, one is created automatically.
     *
     * @return Rate limit object showing usage and remaining quota
     */
    @OptIn(ExperimentalTime::class)
    override suspend fun checkRateLimit(): RateLimit {
        val todayDateKey = getTodayDateKey()

        // Try to fetch existing rate limit for today
        val existingLimit = localDataSource.getRateLimitForDate(todayDateKey)
        if (existingLimit != null) {
            return existingLimit
        }

        // Create a new rate limit record for today
        @OptIn(ExperimentalUuidApi::class)
        val newLimit = RateLimit(
            id = Uuid.random().toString(),
            date = todayDateKey,
            apiCallsUsed = 0,
            maxApiCalls = 4, // 4 calls per day max
        )
        localDataSource.createRateLimit(newLimit)
        return newLimit
    }

    /**
     * Retrieves all pending requests from the retry queue.
     *
     * These are requests that failed previously and are waiting to be retried.
     * Requests are returned in FIFO order (oldest first) for fair processing.
     *
     * @return List of pending requests, oldest first
     */
    override suspend fun getPendingRequests(): List<InsightGenerationRequest> {
        return localDataSource.getPendingRequests()
    }

    /**
     * Processes the queue of pending requests.
     *
     * This is the background job that retries failed insight generation requests.
     * The process:
     * 1. Fetch all pending requests
     * 2. For each request:
     *    a. Attempt to regenerate the insight
     *    b. If successful, mark as COMPLETED and delete from queue
     *    c. If failed, increment retry count
     *    d. If retry count >= max retries, mark as FAILED and delete
     *    e. If rate limit reached, stop processing (try again tomorrow)
     *
     * This method should be called:
     * - Periodically by a background job (e.g., hourly)
     * - When the app comes to the foreground
     * - After successful API calls to process queued requests
     *
     * This is a background operation that should not block the UI thread.
     */
    override suspend fun processPendingRequests() {
        val pendingRequests = localDataSource.getPendingRequests()

        for (request in pendingRequests) {
            try {
                // Check if we've hit today's rate limit
                val rateLimit = checkRateLimit()
                if (rateLimit.isLimitReached()) {
                    // Stop processing; will retry tomorrow
                    break
                }

                // Mark request as processing
                localDataSource.updateRequestStatus(
                    requestId = request.id,
                    status = RequestStatus.PROCESSING
                )

                // Attempt to generate the insight
                val generatedInsight = generationService.generateInsight(
                    recordingId = request.recordingId,
                    transcription = request.transcription
                )

                // Save the generated insight
                localDataSource.insertInsight(generatedInsight)

                // Increment API calls used
                val todayDateKey = getTodayDateKey()
                localDataSource.incrementApiCallsUsed(todayDateKey)

                // Mark request as completed and remove from queue
                localDataSource.updateRequestStatus(
                    requestId = request.id,
                    status = RequestStatus.COMPLETED
                )
                localDataSource.deleteRequest(request.id)

            } catch (e: Exception) {
                // Error occurred; determine if retryable
                if (isErrorRetryable(e)) {
                    // Increment retry count
                    localDataSource.incrementRetryCount(request.id)

                    // Check if we've exhausted retries
                    val updatedRequest = localDataSource.getPendingRequests()
                        .firstOrNull { it.id == request.id }

                    if (updatedRequest != null && updatedRequest.retryCount >= updatedRequest.maxRetries) {
                        // Max retries exceeded; mark as failed and delete
                        localDataSource.updateRequestStatus(
                            requestId = request.id,
                            status = RequestStatus.FAILED,
                            errorMessage = e.message
                        )
                        localDataSource.deleteRequest(request.id)
                    }
                    // Otherwise, leave in PENDING status for next retry
                } else {
                    // Non-retryable error; mark as failed and remove
                    localDataSource.updateRequestStatus(
                        requestId = request.id,
                        status = RequestStatus.FAILED,
                        errorMessage = e.message
                    )
                    localDataSource.deleteRequest(request.id)
                }
            }
        }
    }

    // ===== Helper Functions =====

    /**
     * Calculates today's date key for rate limit tracking.
     *
     * The date key is used to track API calls per calendar day.
     * It's calculated as the number of days since the Unix epoch (Jan 1, 1970).
     *
     * This ensures that:
     * - Each calendar day has a unique date key
     * - Rate limits reset at midnight UTC
     * - The system works across timezones consistently
     *
     * @return Number of days since Jan 1, 1970
     */
    @OptIn(ExperimentalTime::class)
    private fun getTodayDateKey(): Long {
        val currentTimeMs = Clock.System.now().toEpochMilliseconds()
        val millisPerDay = 24 * 60 * 60 * 1000L
        return currentTimeMs / millisPerDay
    }

    /**
     * Determines whether an error should be retried.
     *
     * Retryable errors include:
     * - Network timeouts (temporary API unavailability)
     * - Connection errors (network problems)
     * - 5xx server errors (temporary API issues)
     *
     * Non-retryable errors include:
     * - 4xx client errors (invalid input, auth failed)
     * - Invalid transcription format
     * - Validation failures
     *
     * Currently, this is a simple heuristic. In a real app, this would check
     * the exception type and HTTP status code (if available).
     *
     * @param e The exception that occurred
     * @return true if the request should be retried later
     */
    private fun isErrorRetryable(e: Exception): Boolean {
        // Simple heuristic: network and timeout errors are retryable
        val message = e.message?.lowercase() ?: ""
        return when {
            message.contains("timeout") -> true
            message.contains("network") -> true
            message.contains("connection") -> true
            message.contains("unavailable") -> true
            else -> false
        }
    }
}
