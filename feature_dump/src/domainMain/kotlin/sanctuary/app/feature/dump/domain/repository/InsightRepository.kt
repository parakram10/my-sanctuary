package sanctuary.app.feature.dump.domain.repository

import sanctuary.app.feature.dump.domain.model.Insight
import sanctuary.app.feature.dump.domain.model.InsightGenerationResult
import sanctuary.app.feature.dump.domain.model.RateLimit
import sanctuary.app.feature.dump.domain.model.InsightGenerationRequest

interface InsightRepository {
    suspend fun generateInsight(
        recordingId: String,
        transcription: String
    ): InsightGenerationResult

    suspend fun getInsight(insightId: String): Insight?
    suspend fun getInsightByRecording(recordingId: String): Insight?
    suspend fun getAllInsights(): List<Insight>
    suspend fun archiveInsight(insightId: String)
    suspend fun unarchiveInsight(insightId: String)
    suspend fun deleteInsight(insightId: String)
    suspend fun deleteOldInsights(daysOld: Int = 15)
    suspend fun checkRateLimit(): RateLimit
    suspend fun getPendingRequests(): List<InsightGenerationRequest>
    suspend fun processPendingRequests()
}
