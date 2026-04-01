package sanctuary.app.feature.dump.domain.model

sealed class InsightGenerationResult {
    data class Success(val insight: Insight) : InsightGenerationResult()
    data class RateLimitExceeded(val remainingCalls: Int) : InsightGenerationResult()
    data class Failure(val error: String, val isRetryable: Boolean) : InsightGenerationResult()
}
