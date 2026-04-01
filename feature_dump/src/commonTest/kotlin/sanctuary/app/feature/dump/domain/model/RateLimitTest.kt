package sanctuary.app.feature.dump.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Unit tests for RateLimit domain model.
 *
 * Tests verify:
 * - Rate limit enforcement (4 calls max per day)
 * - Calculation of remaining calls
 * - Boundary conditions (exactly 4 calls, 0 calls, etc.)
 */
class RateLimitTest {

    @Test
    fun testIsLimitReachedWhenNotReached() {
        // Arrange: Create a rate limit with 3 calls used (limit is 4)
        val rateLimit = RateLimit(
            id = "limit-1",
            date = 20000L,
            apiCallsUsed = 3,
            maxApiCalls = 4
        )

        // Act & Assert: Limit should not be reached
        assertFalse(rateLimit.isLimitReached())
    }

    @Test
    fun testIsLimitReachedWhenExactlyReached() {
        // Arrange: Create a rate limit with exactly 4 calls used (max is 4)
        val rateLimit = RateLimit(
            id = "limit-1",
            date = 20000L,
            apiCallsUsed = 4,
            maxApiCalls = 4
        )

        // Act & Assert: Limit should be reached
        assertTrue(rateLimit.isLimitReached())
    }

    @Test
    fun testIsLimitReachedWhenExceeded() {
        // Arrange: Create a rate limit with 5 calls used (shouldn't happen, but test it)
        val rateLimit = RateLimit(
            id = "limit-1",
            date = 20000L,
            apiCallsUsed = 5,
            maxApiCalls = 4
        )

        // Act & Assert: Limit should be reached (exceeded)
        assertTrue(rateLimit.isLimitReached())
    }

    @Test
    fun testRemainingCallsCalculation() {
        // Test various remaining calls scenarios
        val testCases = listOf(
            Triple(0, 4, 4),  // 0 used, 4 remain
            Triple(1, 4, 3),  // 1 used, 3 remain
            Triple(2, 4, 2),  // 2 used, 2 remain
            Triple(3, 4, 1),  // 3 used, 1 remain
            Triple(4, 4, 0),  // 4 used, 0 remain
            Triple(5, 4, 0),  // 5 used (exceeded), 0 remain (never negative)
        )

        for ((used, max, expected) in testCases) {
            val rateLimit = RateLimit(
                id = "test",
                date = 20000L,
                apiCallsUsed = used,
                maxApiCalls = max
            )
            assertEquals(expected, rateLimit.remainingCalls(),
                "Failed for $used used, $max max - expected $expected remaining")
        }
    }

    @Test
    fun testRemainingCallsNeverNegative() {
        // Arrange: Create a rate limit where used > max (shouldn't happen, but test safety)
        val rateLimit = RateLimit(
            id = "limit-1",
            date = 20000L,
            apiCallsUsed = 10,
            maxApiCalls = 4
        )

        // Act: Get remaining calls
        val remaining = rateLimit.remainingCalls()

        // Assert: Should be 0, not negative
        assertEquals(0, remaining)
        assertTrue(remaining >= 0)
    }

    @Test
    fun testDefaultMaxApiCalls() {
        // Arrange: Create a rate limit without specifying maxApiCalls
        val rateLimit = RateLimit(
            id = "limit-1",
            date = 20000L,
            apiCallsUsed = 2
            // maxApiCalls defaults to 4
        )

        // Act & Assert: Default should be 4
        assertEquals(4, rateLimit.maxApiCalls)
        assertEquals(2, rateLimit.remainingCalls())
    }

    @Test
    fun testMultipleLimitScenarios() {
        // Test realistic daily quota scenarios

        // Start of day: 0 calls used
        val morningLimit = RateLimit("1", 20000L, apiCallsUsed = 0)
        assertEquals(4, morningLimit.remainingCalls())
        assertFalse(morningLimit.isLimitReached())

        // Mid day: 2 calls used
        val afternoonLimit = RateLimit("2", 20000L, apiCallsUsed = 2)
        assertEquals(2, afternoonLimit.remainingCalls())
        assertFalse(afternoonLimit.isLimitReached())

        // Late day: 3 calls used, 1 remaining
        val eveningLimit = RateLimit("3", 20000L, apiCallsUsed = 3)
        assertEquals(1, eveningLimit.remainingCalls())
        assertFalse(eveningLimit.isLimitReached())

        // End of day: 4 calls used, limit reached
        val nightLimit = RateLimit("4", 20000L, apiCallsUsed = 4)
        assertEquals(0, nightLimit.remainingCalls())
        assertTrue(nightLimit.isLimitReached())
    }
}
