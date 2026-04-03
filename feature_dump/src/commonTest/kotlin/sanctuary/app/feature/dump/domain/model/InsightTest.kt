package sanctuary.app.feature.dump.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for Insight domain model and related classes.
 *
 * Tests verify:
 * - Insight creation with defaults
 * - InsightContent structure
 * - Sentiment enum
 * - Archive/unarchive logic
 * - Status transitions
 */
class InsightTest {

    @Test
    fun testInsightCreationWithDefaults() {
        // Arrange & Act: Create insight with minimal fields
        val content = InsightContent(
            title = "Test Insight",
            summary = "A test",
            fullSummary = "A longer test summary",
            emotions = listOf("Happy"),
            pathForward = "Keep going",
            recordingType = "dump",
            sentiment = Sentiment.POSITIVE
        )

        val insight = Insight(
            id = "insight-1",
            recordingId = "recording-1",
            content = content,
            createdAt = 1000L
        )

        // Assert: Verify defaults
        assertEquals(false, insight.isArchived)
        assertNull(insight.archivedAt)
        assertEquals(InsightStatus.SAVED, insight.status)
    }

    @Test
    fun testInsightCreationWithAllFields() {
        // Arrange & Act: Create insight with all fields
        val content = InsightContent(
            title = "Complete Insight",
            summary = "Summary text",
            fullSummary = "Full summary with more details",
            emotions = listOf("Anxious", "Hopeful"),
            pathForward = "Take action",
            recordingType = "concern",
            sentiment = Sentiment.NEGATIVE
        )

        val insight = Insight(
            id = "insight-2",
            recordingId = "recording-2",
            content = content,
            createdAt = 2000L,
            isArchived = true,
            archivedAt = 3000L,
            status = InsightStatus.ARCHIVED
        )

        // Assert: Verify all fields
        assertEquals("insight-2", insight.id)
        assertEquals("recording-2", insight.recordingId)
        assertEquals("Complete Insight", insight.content.title)
        assertEquals(2000L, insight.createdAt)
        assertEquals(true, insight.isArchived)
        assertEquals(3000L, insight.archivedAt)
        assertEquals(InsightStatus.ARCHIVED, insight.status)
    }

    @Test
    fun testInsightContentEmotionsList() {
        // Test that emotions list is correctly stored
        val emotions = listOf("Happy", "Hopeful", "Grateful", "Energized")
        val content = InsightContent(
            title = "Test",
            summary = "Test",
            fullSummary = "Test",
            emotions = emotions,
            pathForward = "Test",
            recordingType = "dump",
            sentiment = Sentiment.POSITIVE
        )

        assertEquals(4, content.emotions.size)
        assertEquals("Happy", content.emotions[0])
        assertEquals("Energized", content.emotions[3])
        assertTrue(content.emotions.contains("Grateful"))
    }

    @Test
    fun testSentimentValues() {
        // Test all sentiment enum values
        val positiveContent = InsightContent(
            title = "", summary = "", fullSummary = "",
            emotions = emptyList(), pathForward = "",
            recordingType = "", sentiment = Sentiment.POSITIVE
        )
        assertEquals(Sentiment.POSITIVE, positiveContent.sentiment)

        val negativeContent = positiveContent.copy(sentiment = Sentiment.NEGATIVE)
        assertEquals(Sentiment.NEGATIVE, negativeContent.sentiment)

        val neutralContent = positiveContent.copy(sentiment = Sentiment.NEUTRAL)
        assertEquals(Sentiment.NEUTRAL, neutralContent.sentiment)
    }

    @Test
    fun testInsightArchivingScenario() {
        // Start with unarchived insight
        var insight = Insight(
            id = "insight-1",
            recordingId = "rec-1",
            content = createTestInsightContent(),
            createdAt = 1000L,
            isArchived = false,
            archivedAt = null
        )

        assertEquals(false, insight.isArchived)
        assertNull(insight.archivedAt)

        // Archive the insight
        insight = insight.copy(
            isArchived = true,
            archivedAt = 5000L
        )

        assertEquals(true, insight.isArchived)
        assertEquals(5000L, insight.archivedAt)

        // Unarchive the insight
        insight = insight.copy(
            isArchived = false,
            archivedAt = null
        )

        assertEquals(false, insight.isArchived)
        assertNull(insight.archivedAt)
    }

    @Test
    fun testInsightStatusLifecycle() {
        // Create insight in PENDING state
        var insight = Insight(
            id = "insight-1",
            recordingId = "rec-1",
            content = createTestInsightContent(),
            createdAt = 1000L,
            status = InsightStatus.PENDING
        )
        assertEquals(InsightStatus.PENDING, insight.status)

        // Move to GENERATING
        insight = insight.copy(status = InsightStatus.GENERATING)
        assertEquals(InsightStatus.GENERATING, insight.status)

        // Move to SAVED (success)
        insight = insight.copy(status = InsightStatus.SAVED)
        assertEquals(InsightStatus.SAVED, insight.status)

        // Move to ARCHIVED
        insight = insight.copy(
            status = InsightStatus.ARCHIVED,
            isArchived = true,
            archivedAt = System.currentTimeMillis()
        )
        assertEquals(InsightStatus.ARCHIVED, insight.status)
        assertEquals(true, insight.isArchived)
    }

    @Test
    fun testRecordingTypeVariations() {
        // Test different recording types
        val recordingTypes = listOf("dump", "question", "concern", "reflection", "gratitude")

        for (type in recordingTypes) {
            val content = InsightContent(
                title = "Test",
                summary = "Test",
                fullSummary = "Test",
                emotions = emptyList(),
                pathForward = "Test",
                recordingType = type,
                sentiment = Sentiment.NEUTRAL
            )
            assertEquals(type, content.recordingType)
        }
    }

    @Test
    fun testMultipleInsightsForDifferentRecordings() {
        // Verify that one recording can be associated with multiple insights
        // (in real scenario, each recording has ONE insight, but test the data structure)

        val recordings = listOf("rec-1", "rec-2", "rec-3")
        val insights = mutableListOf<Insight>()

        for (i in recordings.indices) {
            val insight = Insight(
                id = "insight-${i+1}",
                recordingId = recordings[i],
                content = createTestInsightContent(),
                createdAt = 1000L * (i + 1)
            )
            insights.add(insight)
        }

        assertEquals(3, insights.size)
        assertEquals("rec-1", insights[0].recordingId)
        assertEquals("rec-3", insights[2].recordingId)

        // Verify each has unique timestamp
        assertEquals(1000L, insights[0].createdAt)
        assertEquals(3000L, insights[2].createdAt)
    }

    @Test
    fun testPathForwardVariations() {
        // Test that pathForward field captures different advice
        val pathForwards = listOf(
            "Take a 15-minute walk to clear your mind",
            "Reach out to a trusted friend and share your feelings",
            "Practice deep breathing exercises",
            "Write down your thoughts in a journal",
            "Take a break and rest",
            "Schedule a conversation with a counselor"
        )

        for (path in pathForwards) {
            val content = InsightContent(
                title = "Test",
                summary = "Test",
                fullSummary = "Test",
                emotions = emptyList(),
                pathForward = path,
                recordingType = "dump",
                sentiment = Sentiment.NEUTRAL
            )
            assertEquals(path, content.pathForward)
        }
    }

    // Helper function to create test content
    private fun createTestInsightContent(): InsightContent {
        return InsightContent(
            title = "Test Title",
            summary = "Test Summary",
            fullSummary = "Test Full Summary",
            emotions = listOf("Hopeful"),
            pathForward = "Move forward",
            recordingType = "dump",
            sentiment = Sentiment.POSITIVE
        )
    }
}
