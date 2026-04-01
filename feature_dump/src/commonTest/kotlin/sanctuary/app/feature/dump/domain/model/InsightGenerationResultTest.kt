package sanctuary.app.feature.dump.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs

/**
 * Unit tests for InsightGenerationResult sealed class.
 *
 * Tests verify:
 * - All three result variants (Success, RateLimitExceeded, Failure)
 * - Correct data in each variant
 * - Type checking with sealed class
 */
class InsightGenerationResultTest {

    @Test
    fun testSuccessResult() {
        // Arrange: Create a successful result
        val insight = Insight(
            id = "insight-1",
            recordingId = "recording-1",
            content = InsightContent(
                title = "Test",
                summary = "Summary",
                fullSummary = "Full",
                emotions = listOf("Happy"),
                pathForward = "Move forward",
                recordingType = "dump",
                sentiment = Sentiment.POSITIVE
            ),
            createdAt = System.currentTimeMillis()
        )

        val result: InsightGenerationResult = InsightGenerationResult.Success(insight)

        // Act & Assert: Verify it's a Success and contains the insight
        assertIs<InsightGenerationResult.Success>(result)
        assertEquals(insight, (result as InsightGenerationResult.Success).insight)
        assertEquals("insight-1", result.insight.id)
    }

    @Test
    fun testRateLimitExceededResult() {
        // Arrange: Create a rate limit exceeded result
        val result: InsightGenerationResult = InsightGenerationResult.RateLimitExceeded(
            remainingCalls = 0
        )

        // Act & Assert: Verify it's RateLimitExceeded and contains remaining calls
        assertIs<InsightGenerationResult.RateLimitExceeded>(result)
        assertEquals(0, (result as InsightGenerationResult.RateLimitExceeded).remainingCalls)
    }

    @Test
    fun testRateLimitExceededWithRemainingCalls() {
        // Test that remainingCalls value is preserved
        val result = InsightGenerationResult.RateLimitExceeded(remainingCalls = 2)
        assertEquals(2, (result as InsightGenerationResult.RateLimitExceeded).remainingCalls)
    }

    @Test
    fun testFailureResultRetryable() {
        // Arrange: Create a retryable failure result
        val result: InsightGenerationResult = InsightGenerationResult.Failure(
            error = "Network timeout",
            isRetryable = true
        )

        // Act & Assert: Verify it's Failure and is retryable
        assertIs<InsightGenerationResult.Failure>(result)
        val failure = result as InsightGenerationResult.Failure
        assertEquals("Network timeout", failure.error)
        assertTrue(failure.isRetryable)
    }

    @Test
    fun testFailureResultNonRetryable() {
        // Arrange: Create a non-retryable failure result
        val result: InsightGenerationResult = InsightGenerationResult.Failure(
            error = "Invalid transcription",
            isRetryable = false
        )

        // Act & Assert: Verify it's Failure and is not retryable
        assertIs<InsightGenerationResult.Failure>(result)
        val failure = result as InsightGenerationResult.Failure
        assertEquals("Invalid transcription", failure.error)
        assertTrue(!failure.isRetryable)
    }

    @Test
    fun testSealdClassBehavior() {
        // Verify that sealed class can be used in when expression
        val results: List<InsightGenerationResult> = listOf(
            InsightGenerationResult.Success(
                Insight(
                    "1", "1",
                    InsightContent("", "", "", emptyList(), "", "", Sentiment.NEUTRAL),
                    0L
                )
            ),
            InsightGenerationResult.RateLimitExceeded(0),
            InsightGenerationResult.Failure("error", true)
        )

        var successCount = 0
        var rateLimitCount = 0
        var failureCount = 0

        for (result in results) {
            when (result) {
                is InsightGenerationResult.Success -> successCount++
                is InsightGenerationResult.RateLimitExceeded -> rateLimitCount++
                is InsightGenerationResult.Failure -> failureCount++
            }
        }

        assertEquals(1, successCount)
        assertEquals(1, rateLimitCount)
        assertEquals(1, failureCount)
    }

    @Test
    fun testErrorMessageVariations() {
        // Test various error messages are preserved
        val errorMessages = listOf(
            "Network timeout",
            "API rate limit exceeded",
            "Invalid JSON response",
            "Connection refused",
            "Unknown error",
            ""
        )

        for (errorMsg in errorMessages) {
            val result = InsightGenerationResult.Failure(errorMsg, isRetryable = true)
            assertEquals(errorMsg, (result as InsightGenerationResult.Failure).error)
        }
    }
}
