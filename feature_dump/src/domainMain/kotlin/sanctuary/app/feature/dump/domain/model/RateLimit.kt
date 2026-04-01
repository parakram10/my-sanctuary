package sanctuary.app.feature.dump.domain.model

/**
 * Tracks API usage limits for AI insight generation.
 *
 * The app is limited to 4 Claude API calls per calendar day.
 * This model tracks how many calls have been made on a given date
 * and provides utility functions to check the remaining quota.
 *
 * Rate limits are reset daily at midnight (UTC).
 *
 * @property id Unique identifier for this rate limit record
 * @property date Date key (typically days since epoch) this limit applies to
 * @property apiCallsUsed Number of API calls used on this date
 * @property maxApiCalls Maximum allowed API calls per day (default: 4)
 */
data class RateLimit(
    val id: String,
    val date: Long,
    val apiCallsUsed: Int,
    val maxApiCalls: Int = 4,
) {
    /**
     * Checks if the daily API call limit has been reached.
     *
     * @return true if apiCallsUsed >= maxApiCalls, false otherwise
     */
    fun isLimitReached(): Boolean = apiCallsUsed >= maxApiCalls

    /**
     * Calculates the number of API calls remaining for the day.
     *
     * @return Number of remaining calls, minimum 0 (never negative)
     */
    fun remainingCalls(): Int = (maxApiCalls - apiCallsUsed).coerceAtLeast(0)
}
