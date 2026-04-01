package sanctuary.app.feature.dump.domain.model

data class RateLimit(
    val id: String,
    val date: Long,
    val apiCallsUsed: Int,
    val maxApiCalls: Int = 4,
) {
    fun isLimitReached(): Boolean = apiCallsUsed >= maxApiCalls
    fun remainingCalls(): Int = (maxApiCalls - apiCallsUsed).coerceAtLeast(0)
}
