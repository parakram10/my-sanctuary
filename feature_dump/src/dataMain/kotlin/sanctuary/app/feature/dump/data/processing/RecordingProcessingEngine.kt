package sanctuary.app.feature.dump.data.processing

/**
 * Orchestrates the full recording processing pipeline.
 *
 * Responsibilities:
 * - FSM state transitions (PENDING → TRANSCRIBING → GENERATING_INSIGHT → COMPLETED/FAILED)
 * - Single-flight processing (at most one per recordingId)
 * - Checkpoint: skip transcription if transcript exists
 * - Error handling with transient vs permanent classification
 * - Auto-retry on transient errors (once), then defer to WorkManager
 *
 * Thread-safe for concurrent calls to process(different_ids) and process(same_id).
 */
interface RecordingProcessingEngine {
    /**
     * Process a single recording through the pipeline.
     *
     * State Flow:
     *   PENDING → TRANSCRIBING → GENERATING_INSIGHT → COMPLETED
     *     or
     *   PENDING → FAILED (error) → PENDING (if auto-retry) → ...
     *     or
     *   PENDING → FAILED (permanent error) → [no further retry]
     *
     * Single-flight guarantee: At most one process() per recordingId executes.
     *
     * @param recordingId ID of the recording to process
     * @throws IllegalArgumentException if recording not found
     * @throws Exception if processing fails (caught, not rethrown)
     */
    suspend fun process(recordingId: String)
}
