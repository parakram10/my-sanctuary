package sanctuary.app.feature.dump.data.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import sanctuary.app.core.database.Rate_limits
import sanctuary.app.core.database.Request_queue
import sanctuary.app.core.database.Insights
import sanctuary.app.feature.dump.domain.model.RateLimit
import sanctuary.app.feature.dump.domain.model.InsightStatus
import sanctuary.app.feature.dump.domain.model.InsightGenerationRequest
import sanctuary.app.feature.dump.domain.model.RequestStatus

/**
 * Unit tests for mapper functions that convert between database entities and domain models.
 *
 * Tests verify:
 * - Type conversions (Long ↔ Int, Boolean ↔ Long)
 * - Enum conversions
 * - Null safety and default values
 */
class InsightMapperTest {

    // ===== RateLimit Mapper Tests =====

    @Test
    fun testRateLimitEntityToDomain() {
        val entity = Rate_limits(
            id = "limit-1",
            date = 20000L,
            api_calls_used = 2L,
            max_api_calls = 4L
        )

        val domain = entity.toDomain()

        assertEquals("limit-1", domain.id)
        assertEquals(20000L, domain.date)
        assertEquals(2, domain.apiCallsUsed)
        assertEquals(4, domain.maxApiCalls)
    }

    @Test
    fun testRateLimitDomainToEntity() {
        val domain = RateLimit(
            id = "limit-2",
            date = 20001L,
            apiCallsUsed = 3,
            maxApiCalls = 4
        )

        val entity = domain.toEntity()

        assertEquals("limit-2", entity.id)
        assertEquals(20001L, entity.date)
        assertEquals(3L, entity.api_calls_used)
        assertEquals(4L, entity.max_api_calls)
    }

    @Test
    fun testRateLimitNullableFieldDefaults() {
        val entity1 = Rate_limits(
            id = "1", date = 20000L,
            api_calls_used = null,
            max_api_calls = 4L
        )
        assertEquals(0, entity1.toDomain().apiCallsUsed)

        val entity2 = Rate_limits(
            id = "1", date = 20000L,
            api_calls_used = 2L,
            max_api_calls = null
        )
        assertEquals(4, entity2.toDomain().maxApiCalls)
    }

    // ===== InsightGenerationRequest Mapper Tests =====

    @Test
    fun testInsightGenerationRequestEntityToDomain() {
        val entity = Request_queue(
            id = "req-1",
            recording_id = "rec-1",
            created_at = 5000L,
            retry_count = 1L,
            max_retries = 3L,
            status = "PENDING",
            error_message = "Network timeout"
        )

        val domain = entity.toDomain()

        assertEquals("req-1", domain.id)
        assertEquals("rec-1", domain.recordingId)
        assertEquals(5000L, domain.createdAt)
        assertEquals(1, domain.retryCount)
        assertEquals(3, domain.maxRetries)
        assertEquals(RequestStatus.PENDING, domain.status)
        assertEquals("Network timeout", domain.errorMessage)
    }

    @Test
    fun testInsightGenerationRequestDomainToEntity() {
        val domain = InsightGenerationRequest(
            id = "req-2",
            recordingId = "rec-2",
            transcription = "This is a test transcription",
            createdAt = 5001L,
            retryCount = 2,
            maxRetries = 3,
            status = RequestStatus.PROCESSING,
            errorMessage = null
        )

        val entity = domain.toEntity()

        assertEquals("req-2", entity.id)
        assertEquals("rec-2", entity.recording_id)
        assertEquals(5001L, entity.created_at)
        assertEquals(2L, entity.retry_count)
        assertEquals(3L, entity.max_retries)
        assertEquals("PROCESSING", entity.status)
        assertNull(entity.error_message)
    }

    @Test
    fun testRequestStatusEnumConversion() {
        val statusValues = listOf(
            "PENDING" to RequestStatus.PENDING,
            "PROCESSING" to RequestStatus.PROCESSING,
            "FAILED" to RequestStatus.FAILED,
            "COMPLETED" to RequestStatus.COMPLETED
        )

        for ((statusString, statusEnum) in statusValues) {
            val entity = Request_queue(
                id = "1", recording_id = "1",
                created_at = 0L, retry_count = 0L,
                max_retries = 3L, status = statusString,
                error_message = null
            )
            assertEquals(statusEnum, entity.toDomain().status)
        }
    }


    @Test
    fun testTypeLongToIntConversions() {
        val entity = Rate_limits(
            id = "test",
            date = 12345L,
            api_calls_used = 999L,
            max_api_calls = 777L
        )

        val domain = entity.toDomain()
        assertEquals(999, domain.apiCallsUsed)
        assertEquals(777, domain.maxApiCalls)
    }
}
