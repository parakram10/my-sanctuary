package sanctuary.app.feature.dump.data.datasource

import sanctuary.app.feature.dump.domain.model.Insight
import sanctuary.app.feature.dump.domain.model.InsightStatus
import sanctuary.app.feature.dump.domain.model.RateLimit
import sanctuary.app.feature.dump.domain.model.InsightGenerationRequest
import sanctuary.app.feature.dump.domain.model.RequestStatus

internal interface InsightLocalDataSource {
    // ===== Insight Operations =====

    suspend fun insertInsight(insight: Insight)
    suspend fun getInsightById(id: String): Insight?
    suspend fun getInsightByRecordingId(recordingId: String): Insight?
    suspend fun getAllInsights(archived: Boolean = false): List<Insight>
    suspend fun updateInsightStatus(insightId: String, status: InsightStatus)
    suspend fun archiveInsight(insightId: String)
    suspend fun unarchiveInsight(insightId: String)
    suspend fun deleteOlderThan(epochMs: Long)

    // ===== Rate Limit Operations =====

    suspend fun getRateLimitForDate(dateKey: Long): RateLimit?
    suspend fun createRateLimit(rateLimit: RateLimit)
    suspend fun incrementApiCallsUsed(dateKey: Long)

    // ===== Request Queue Operations =====

    suspend fun insertRequest(request: InsightGenerationRequest)
    suspend fun getPendingRequests(): List<InsightGenerationRequest>
    suspend fun updateRequestStatus(
        requestId: String,
        status: RequestStatus,
        errorMessage: String? = null
    )
    suspend fun deleteRequest(requestId: String)
    suspend fun incrementRetryCount(requestId: String)
}
