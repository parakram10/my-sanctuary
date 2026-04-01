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

internal class InsightLocalDataSourceImpl(
    private val database: SanctuaryDatabase,
) : InsightLocalDataSource {

    private val insightsQueries get() = database.insightsQueries
    private val rateLimitsQueries get() = database.rate_limitsQueries
    private val requestQueueQueries get() = database.request_queueQueries

    // ===== Insight Operations =====

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

    override suspend fun getInsightById(id: String): Insight? {
        return insightsQueries.selectById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }
            .first()
    }

    override suspend fun getInsightByRecordingId(recordingId: String): Insight? {
        return insightsQueries.selectByRecordingId(recordingId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }
            .first()
    }

    override suspend fun getAllInsights(archived: Boolean): List<Insight> {
        val entities = if (archived) {
            insightsQueries.selectArchived()
        } else {
            insightsQueries.selectAll()
        }
            .asFlow()
            .mapToList(Dispatchers.Default)
            .first()

        return entities.map { it.toDomain() }
    }

    override suspend fun updateInsightStatus(insightId: String, status: InsightStatus) {
        insightsQueries.updateStatus(status.name, insightId)
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun archiveInsight(insightId: String) {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        insightsQueries.updateIsArchived(1L, currentTime, insightId)
    }

    override suspend fun unarchiveInsight(insightId: String) {
        insightsQueries.updateIsArchived(0L, null, insightId)
    }

    override suspend fun deleteOlderThan(epochMs: Long) {
        insightsQueries.deleteOlderThan(epochMs)
    }

    // ===== Rate Limit Operations =====

    override suspend fun getRateLimitForDate(dateKey: Long): RateLimit? {
        return rateLimitsQueries.getByDate(dateKey)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }
            .first()
    }

    override suspend fun createRateLimit(rateLimit: RateLimit) {
        val entity = rateLimit.toEntity()
        rateLimitsQueries.insert(
            id = entity.id,
            date = entity.date,
            api_calls_used = entity.api_calls_used,
            max_api_calls = entity.max_api_calls,
        )
    }

    override suspend fun incrementApiCallsUsed(dateKey: Long) {
        val currentRateLimit = getRateLimitForDate(dateKey)
        if (currentRateLimit != null) {
            val newCount = currentRateLimit.apiCallsUsed + 1
            rateLimitsQueries.updateCallsUsed(newCount.toLong(), dateKey)
        }
    }

    // ===== Request Queue Operations =====

    override suspend fun insertRequest(request: InsightGenerationRequest) {
        val entity = request.toEntity()
        requestQueueQueries.insert(
            id = entity.id,
            recording_id = entity.recording_id,
            created_at = entity.created_at,
            status = entity.status,
        )
    }

    override suspend fun getPendingRequests(): List<InsightGenerationRequest> {
        val entities = requestQueueQueries.selectPending()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .first()

        return entities.map { it.toDomain() }
    }

    override suspend fun updateRequestStatus(
        requestId: String,
        status: RequestStatus,
        errorMessage: String?
    ) {
        requestQueueQueries.updateStatus(
            status = status.name,
            error_message = errorMessage,
            retry_count = 0L,
            id = requestId,
        )
    }

    override suspend fun deleteRequest(requestId: String) {
        requestQueueQueries.deleteById(requestId)
    }

    override suspend fun incrementRetryCount(requestId: String) {
        val request = requestQueueQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .first()
            .firstOrNull { it.id == requestId }

        if (request != null) {
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
