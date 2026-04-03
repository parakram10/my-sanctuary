package sanctuary.app.feature.dump.domain.service

import sanctuary.app.feature.dump.domain.model.Insight

/**
 * Service contract for AI-powered insight generation.
 *
 * This interface abstracts the external AI API (Claude) from the rest of the application.
 * Implementations are responsible for:
 * - Calling the AI API with a user's recording transcription
 * - Parsing the API response into a structured Insight object
 * - Handling errors and timeouts gracefully
 * - NOT making medical or therapy recommendations
 *
 * The service is called only after rate limit checks pass.
 * If a call fails with a retryable error, the request is queued for automatic retry.
 */
interface InsightGenerationService {
    /**
     * Generates an AI-powered insight from a user's recording transcription.
     *
     * The insight includes:
     * - Title and summaries of the user's emotional state
     * - Detected emotions and overall sentiment
     * - A constructive "path forward" for emotional growth
     * - Recording type classification
     *
     * This function should timeout after 5-10 seconds if the API doesn't respond.
     * If a timeout or network error occurs, it should throw a retryable exception.
     *
     * @param recordingId The unique ID of the recording being analyzed
     * @param transcription The transcribed text from the user's recording
     * @return A complete Insight object with all AI-generated data
     * @throws Exception if the API call fails; the caller should check if retryable
     */
    suspend fun generateInsight(recordingId: String, transcription: String): Insight
}
