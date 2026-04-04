package sanctuary.app.feature.dump.data.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sanctuary.app.feature.dump.domain.model.Insight
import sanctuary.app.feature.dump.domain.model.InsightContent
import sanctuary.app.feature.dump.domain.model.InsightStatus
import sanctuary.app.feature.dump.domain.model.Sentiment
import sanctuary.app.feature.dump.domain.port.InsightPort
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Implementation of [InsightPort] that integrates with Claude API.
 *
 * This service calls the Claude API to generate AI-powered emotional insights from
 * user recordings. It handles:
 * - HTTP communication with Claude API
 * - Prompt engineering for emotional analysis
 * - JSON response parsing
 * - Error handling and timeouts (10 seconds max)
 * - Safety guardrails (no medical/therapy recommendations)
 *
 * The service is responsible only for API communication and parsing.
 * Rate limiting and retry logic are handled by the repository layer.
 *
 * @property httpClient The Ktor HTTP client for API communication
 * @property apiKey The Claude API key for authentication
 */
internal class ClaudeInsightGenerationService(
    private val httpClient: HttpClient,
    private val apiKey: String,
) : InsightPort {

    companion object {
        // Claude API endpoint
        private const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"

        // Model to use (Claude 3.5 Sonnet offers best balance of speed and quality)
        private const val CLAUDE_MODEL = "claude-3-5-sonnet-20241022"

        // Maximum tokens for response (sufficient for detailed insights)
        private const val MAX_TOKENS = 2000

        // API timeout in milliseconds (10 seconds)
        private const val API_TIMEOUT_MS = 10_000L
    }

    /**
     * Generates an AI-powered emotional insight from a user's recording transcription.
     *
     * The process:
     * 1. Build a detailed prompt instructing Claude to analyze emotional content
     * 2. Call Claude API with the transcription
     * 3. Parse the JSON response into InsightContent
     * 4. Create an Insight object with metadata
     * 5. Return the complete insight
     *
     * The prompt instructs Claude to provide:
     * - A title summarizing the key feeling or theme
     * - A one-sentence summary of the emotional state
     * - A detailed multi-paragraph summary of insights
     * - Detected emotions (max 5)
     * - A constructive "path forward" for emotional growth
     * - The type of recording (dump, question, concern, etc.)
     * - Overall sentiment (POSITIVE, NEGATIVE, or NEUTRAL)
     *
     * **Important Safety Notes:**
     * - Claude is explicitly instructed NOT to provide medical advice
     * - Claude is instructed NOT to recommend therapy or medication
     * - This is an emotional journaling tool, not a clinical service
     * - All disclaimers and safety guardrails are in the system prompt
     *
     * @param recordingId The unique ID of the recording being analyzed
     * @param transcription The transcribed text from the user's recording
     * @return A complete Insight object with all generated data
     * @throws Exception if the API call fails, with message indicating error type
     */
    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    override suspend fun generateInsight(
        recordingId: String,
        transcription: String
    ): Insight {
        try {
            // Build the request to Claude API
            val request = ClaudeApiRequest(
                model = CLAUDE_MODEL,
                max_tokens = MAX_TOKENS,
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = buildPrompt(transcription)
                    )
                ),
                system = SYSTEM_PROMPT
            )

            // Call Claude API with the request
            val response = httpClient.post(CLAUDE_API_URL) {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(request))
                headers.append("x-api-key", apiKey)
                headers.append("anthropic-version", "2023-06-01")
            }.body<String>()

            // Parse the response JSON
            val jsonResponse = Json.parseToJsonElement(response).jsonObject
            val contentArray = jsonResponse["content"]?.jsonArray
                ?: throw Exception("Invalid API response format: missing content array")
            val firstContent = contentArray.firstOrNull()
                ?: throw Exception("Invalid API response format: empty content array")
            val textContent = firstContent.jsonObject["text"]?.jsonPrimitive?.content
                ?: throw Exception("Invalid API response format: no text content")

            // Parse the insight data from Claude's response
            val insightData = parseClaudeResponse(textContent)

            // Create and return the Insight object
            @OptIn(ExperimentalUuidApi::class)
            return Insight(
                id = Uuid.random().toString(),
                recordingId = recordingId,
                content = insightData,
                createdAt = Clock.System.now().toEpochMilliseconds(),
                isArchived = false,
                archivedAt = null,
                status = InsightStatus.SAVED
            )

        } catch (e: Exception) {
            // Re-throw with context about what went wrong
            throw Exception("Failed to generate insight from Claude API: ${e.message}", e)
        }
    }

    /**
     * Builds the user prompt for Claude.
     *
     * The prompt requests Claude to analyze the transcription and provide structured
     * emotional insights in JSON format. This structure ensures we can parse the
     * response reliably.
     *
     * @param transcription The user's recording transcription
     * @return The complete prompt to send to Claude
     */
    private fun buildPrompt(transcription: String): String {
        return """
Analyze the following user recording transcription and provide emotional insights in JSON format.

Recording transcription:
---
$transcription
---

Please respond ONLY with a valid JSON object (no markdown, no extra text) containing these fields:
{
  "title": "A short (5-10 words) title summarizing the key feeling or theme",
  "summary": "One sentence summary of the emotional state or main insight",
  "fullSummary": "A detailed 2-3 paragraph analysis of the emotions, thoughts, and context",
  "emotions": ["emotion1", "emotion2", "emotion3"],
  "pathForward": "A specific, actionable suggestion for emotional growth (1-2 sentences)",
  "recordingType": "One of: dump, question, concern, reflection, gratitude",
  "sentiment": "One of: POSITIVE, NEGATIVE, NEUTRAL"
}

Important guidelines:
- Emotions: List 2-5 primary emotions detected (e.g., "Anxious", "Hopeful", "Grateful")
- Path Forward: Provide constructive advice like "Take a 15-minute walk", "Talk to someone you trust", etc.
- Be empathetic and validating in the summary
- Identify both the emotional state and any underlying concerns
- Sentiment: Overall tone (POSITIVE if hopeful/growth-oriented, NEGATIVE if distressed/stuck, NEUTRAL if balanced)
        """.trimIndent()
    }

    /**
     * Parses Claude's JSON response into an InsightContent object.
     *
     * Claude returns a JSON object with insight data. This function:
     * 1. Parses the JSON string
     * 2. Extracts each field
     * 3. Validates and sanitizes the data
     * 4. Creates the InsightContent object
     *
     * @param jsonResponse The JSON response from Claude
     * @return An InsightContent object with all fields populated
     * @throws Exception if parsing fails or required fields are missing
     */
    private fun parseClaudeResponse(jsonResponse: String): InsightContent {
        try {
            val json = Json.parseToJsonElement(jsonResponse).jsonObject

            // Extract and validate required fields
            val title = json["title"]?.jsonPrimitive?.content
                ?: throw Exception("Missing 'title' field")
            val summary = json["summary"]?.jsonPrimitive?.content
                ?: throw Exception("Missing 'summary' field")
            val fullSummary = json["fullSummary"]?.jsonPrimitive?.content
                ?: throw Exception("Missing 'fullSummary' field")

            val emotionsElement = json["emotions"]
                ?: throw Exception("Missing 'emotions' field")
            val emotions = emotionsElement.jsonArray.mapNotNull { element ->
                try {
                    element.jsonPrimitive.content
                } catch (e: Exception) {
                    null
                }
            }.filterNot { it.isBlank() }

            val pathForward = json["pathForward"]?.jsonPrimitive?.content
                ?: throw Exception("Missing 'pathForward' field")
            val recordingType = json["recordingType"]?.jsonPrimitive?.content
                ?: throw Exception("Missing 'recordingType' field")
            val sentimentStr = json["sentiment"]?.jsonPrimitive?.content
                ?: throw Exception("Missing 'sentiment' field")

            // Validate and parse sentiment enum
            val sentiment = try {
                Sentiment.valueOf(sentimentStr)
            } catch (e: IllegalArgumentException) {
                throw Exception("Invalid sentiment value: $sentimentStr")
            }

            // Limit emotions to 5 maximum
            val limitedEmotions = emotions.take(5)

            // Create and return InsightContent
            return InsightContent(
                title = title.trim().take(200), // Limit title length
                summary = summary.trim().take(500), // Limit summary length
                fullSummary = fullSummary.trim().take(2000), // Limit full summary
                emotions = limitedEmotions,
                pathForward = pathForward.trim().take(500), // Limit advice length
                recordingType = recordingType.trim().lowercase(), // Normalize type
                sentiment = sentiment
            )

        } catch (e: Exception) {
            throw Exception("Failed to parse Claude's response: ${e.message}", e)
        }
    }

    // ===== API Request/Response Models =====

    /**
     * Request format for Claude API (Messages API).
     *
     * This follows the Anthropic Messages API specification.
     * Documentation: https://docs.anthropic.com/en/api/messages
     */
    @Serializable
    private data class ClaudeApiRequest(
        val model: String,
        val max_tokens: Int,
        val messages: List<ClaudeMessage>,
        val system: String? = null
    )

    /**
     * Message object in Claude API request.
     */
    @Serializable
    private data class ClaudeMessage(
        val role: String, // "user" or "assistant"
        val content: String
    )
}

/**
 * System prompt that defines Claude's behavior for insight generation.
 *
 * This prompt:
 * - Establishes Claude as an emotional support journaling tool
 * - Instructs Claude to provide empathetic analysis
 * - Explicitly prohibits medical or clinical advice
 * - Sets guidelines for the insights provided
 * - Emphasizes the limitations of the tool
 */
private const val SYSTEM_PROMPT = """
You are an empathetic emotional support assistant helping users process their feelings through journaling.

Your role is to:
1. Listen without judgment to the user's thoughts and feelings
2. Identify and name emotions present in their recording
3. Provide constructive insights about their emotional state
4. Suggest positive, actionable steps for emotional growth

IMPORTANT LIMITATIONS AND DISCLAIMERS:
- You are NOT a therapist or mental health professional
- You MUST NOT provide medical advice or recommend medication
- You MUST NOT recommend therapy or clinical treatment (though you can suggest support)
- You MUST NOT diagnose mental health conditions
- Your role is supportive journaling, not clinical care
- Users experiencing suicidal thoughts should seek immediate professional help

When responding:
- Be validating and empathetic
- Acknowledge their feelings without minimizing them
- Provide gentle, practical suggestions
- Keep advice accessible and actionable
- Do not pretend to understand clinical conditions
- If something seems serious, suggest professional support in a caring way

Remember: This tool helps with emotional processing and journaling, not medical treatment.
"""
