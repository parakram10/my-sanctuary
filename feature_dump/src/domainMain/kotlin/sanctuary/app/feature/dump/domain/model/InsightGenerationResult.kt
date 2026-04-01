package sanctuary.app.feature.dump.domain.model

/**
 * Result of attempting to generate an AI-powered insight from a recording.
 *
 * This sealed class represents the three possible outcomes when trying to generate
 * an insight:
 * 1. **Success**: Insight was generated successfully
 * 2. **RateLimitExceeded**: Daily API call limit (4/day) has been reached
 * 3. **Failure**: An error occurred during generation
 *
 * The caller should handle each case appropriately to provide feedback to the user.
 */
sealed class InsightGenerationResult {
    /**
     * Insight generation succeeded and is ready to display.
     *
     * @property insight The successfully generated insight
     */
    data class Success(val insight: Insight) : InsightGenerationResult()

    /**
     * Daily API call limit has been reached; try again tomorrow.
     *
     * The user is limited to 4 AI-generated insights per calendar day.
     * This result is returned when they exceed that limit.
     *
     * @property remainingCalls Number of calls still available today (always 0 in this case)
     */
    data class RateLimitExceeded(val remainingCalls: Int) : InsightGenerationResult()

    /**
     * An error occurred during insight generation.
     *
     * This could be due to network failures, API errors, invalid input, etc.
     * If retryable, the request will be automatically queued for later retry.
     *
     * @property error Human-readable error message for logging or user display
     * @property isRetryable true if the request should be queued for retry; false if unrecoverable
     */
    data class Failure(val error: String, val isRetryable: Boolean) : InsightGenerationResult()
}
