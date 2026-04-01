package sanctuary.app.feature.dump.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Unit tests for InsightGenerationRequest domain model.
 *
 * Tests verify:
 * - Request creation with defaults
 * - Status transitions
 * - Retry count tracking
 * - Error message handling
 */
class InsightGenerationRequestTest {

    @Test
    fun testRequestCreationWithDefaults() {
        // Arrange & Act: Create a request with defaults
        val request = InsightGenerationRequest(
            id = "req-1",
            recordingId = "rec-1",
            transcription = "Test transcription",
            createdAt = 1000L
        )

        // Assert: Verify defaults
        assertEquals(0, request.retryCount)
        assertEquals(3, request.maxRetries)
        assertEquals(RequestStatus.PENDING, request.status)
        assertNull(request.errorMessage)
    }

    @Test
    fun testRequestCreationWithAllFields() {
        // Arrange & Act: Create a request with all fields specified
        val request = InsightGenerationRequest(
            id = "req-2",
            recordingId = "rec-2",
            transcription = "Another transcription",
            createdAt = 2000L,
            retryCount = 2,
            maxRetries = 5,
            status = RequestStatus.PROCESSING,
            errorMessage = "Previous timeout"
        )

        // Assert: Verify all fields
        assertEquals("req-2", request.id)
        assertEquals("rec-2", request.recordingId)
        assertEquals("Another transcription", request.transcription)
        assertEquals(2000L, request.createdAt)
        assertEquals(2, request.retryCount)
        assertEquals(5, request.maxRetries)
        assertEquals(RequestStatus.PROCESSING, request.status)
        assertEquals("Previous timeout", request.errorMessage)
    }

    @Test
    fun testRequestStatusLifecycle() {
        // Test all status transitions in a typical flow

        // 1. Start with PENDING
        var request = InsightGenerationRequest(
            id = "req-1",
            recordingId = "rec-1",
            transcription = "Text",
            createdAt = 0L,
            status = RequestStatus.PENDING
        )
        assertEquals(RequestStatus.PENDING, request.status)

        // 2. Move to PROCESSING
        request = request.copy(status = RequestStatus.PROCESSING)
        assertEquals(RequestStatus.PROCESSING, request.status)

        // 3. If failed, move to FAILED
        request = request.copy(
            status = RequestStatus.FAILED,
            errorMessage = "API error"
        )
        assertEquals(RequestStatus.FAILED, request.status)
        assertEquals("API error", request.errorMessage)

        // 4. If successful, move to COMPLETED
        request = request.copy(status = RequestStatus.COMPLETED)
        assertEquals(RequestStatus.COMPLETED, request.status)
    }

    @Test
    fun testRetryCountIncrement() {
        // Test retry count logic
        var request = InsightGenerationRequest(
            id = "req-1",
            recordingId = "rec-1",
            transcription = "Text",
            createdAt = 0L,
            retryCount = 0,
            maxRetries = 3
        )

        // Simulate retry attempts
        assertEquals(0, request.retryCount)
        assertEquals(false, request.retryCount >= request.maxRetries)

        request = request.copy(retryCount = 1)
        assertEquals(1, request.retryCount)
        assertEquals(false, request.retryCount >= request.maxRetries)

        request = request.copy(retryCount = 2)
        assertEquals(2, request.retryCount)
        assertEquals(false, request.retryCount >= request.maxRetries)

        request = request.copy(retryCount = 3)
        assertEquals(3, request.retryCount)
        assertEquals(true, request.retryCount >= request.maxRetries) // Max reached
    }

    @Test
    fun testErrorMessageHandling() {
        // Test with error message
        val requestWithError = InsightGenerationRequest(
            id = "req-1",
            recordingId = "rec-1",
            transcription = "Text",
            createdAt = 0L,
            status = RequestStatus.FAILED,
            errorMessage = "Network connection failed"
        )
        assertNotNull(requestWithError.errorMessage)
        assertEquals("Network connection failed", requestWithError.errorMessage)

        // Test without error message
        val requestNoError = InsightGenerationRequest(
            id = "req-2",
            recordingId = "rec-2",
            transcription = "Text",
            createdAt = 0L,
            status = RequestStatus.PENDING
        )
        assertNull(requestNoError.errorMessage)
    }

    @Test
    fun testRequestQueueScenario() {
        // Simulate a full queue processing scenario

        // 1. Create initial request
        var request = InsightGenerationRequest(
            id = "req-123",
            recordingId = "rec-456",
            transcription = "User said...",
            createdAt = 1000L
        )

        // 2. First attempt - failed due to timeout
        request = request.copy(
            status = RequestStatus.FAILED,
            retryCount = 1,
            errorMessage = "Network timeout"
        )
        assertEquals(1, request.retryCount)

        // 3. Second attempt - still pending retry
        request = request.copy(status = RequestStatus.PENDING)
        assertEquals(RequestStatus.PENDING, request.status)

        // 4. Second attempt failed
        request = request.copy(
            status = RequestStatus.FAILED,
            retryCount = 2,
            errorMessage = "API unavailable"
        )
        assertEquals(2, request.retryCount)

        // 5. Third and final attempt
        request = request.copy(status = RequestStatus.PENDING)

        // 6. Finally succeeded
        request = request.copy(
            status = RequestStatus.COMPLETED,
            errorMessage = null
        )
        assertEquals(RequestStatus.COMPLETED, request.status)
        assertNull(request.errorMessage)
    }

    @Test
    fun testMultipleRequestsWithDifferentMaxRetries() {
        // Test that max retries can vary per request
        val quickRetryRequest = InsightGenerationRequest(
            id = "quick",
            recordingId = "rec-1",
            transcription = "Text",
            createdAt = 0L,
            maxRetries = 1 // Only 1 retry
        )
        assertEquals(1, quickRetryRequest.maxRetries)

        val standardRetryRequest = InsightGenerationRequest(
            id = "standard",
            recordingId = "rec-2",
            transcription = "Text",
            createdAt = 0L,
            maxRetries = 3 // Standard 3 retries
        )
        assertEquals(3, standardRetryRequest.maxRetries)

        val aggressiveRetryRequest = InsightGenerationRequest(
            id = "aggressive",
            recordingId = "rec-3",
            transcription = "Text",
            createdAt = 0L,
            maxRetries = 5 // More retries
        )
        assertEquals(5, aggressiveRetryRequest.maxRetries)
    }
}
