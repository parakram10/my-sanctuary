package sanctuary.app.feature.dump.data.processing

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import sanctuary.app.feature.dump.domain.model.Insight
import sanctuary.app.feature.dump.domain.model.InsightContent
import sanctuary.app.feature.dump.domain.model.InsightStatus
import sanctuary.app.feature.dump.domain.model.ProcessingErrorCode
import sanctuary.app.feature.dump.domain.model.ProcessingStatus
import sanctuary.app.feature.dump.domain.model.Recording
import sanctuary.app.feature.dump.domain.model.Sentiment
import sanctuary.app.feature.dump.domain.port.InsightPort
import sanctuary.app.feature.dump.domain.repository.RecordingRepository
import sanctuary.app.feature.dump.domain.transcription.OnDeviceTranscriber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RecordingProcessingEngineTest {

    // Mocks
    private val recordingRepository = mockk<RecordingRepository>()
    private val insightPort = mockk<InsightPort>()
    private val transcriber = mockk<OnDeviceTranscriber>()

    // Engine under test
    private val engine = RecordingProcessingEngineImpl(
        recordingRepository = recordingRepository,
        insightPort = insightPort,
        transcriber = transcriber
    )

    // Helper: create a sample recording
    private fun createRecording(
        id: String = "rec-123",
        status: ProcessingStatus = ProcessingStatus.PENDING,
        transcription: String? = null,
        errorCode: ProcessingErrorCode? = null,
        attempts: Int = 0,
        locale: String = "en"
    ) = Recording(
        id = id,
        userId = "user-1",
        filePath = "/tmp/$id.m4a",
        durationMs = 30_000L,
        createdAt = Clock.System.now().toEpochMilliseconds(),
        title = "Test Recording",
        transcription = transcription,
        isArchived = false,
        processingStatus = status,
        errorCode = errorCode,
        backgroundWmAttempts = attempts,
        recordingLocale = locale
    )

    // Helper: create a sample insight
    private fun createInsight(recordingId: String = "rec-123") = Insight(
        id = "insight-1",
        recordingId = recordingId,
        content = InsightContent(
            title = "Test Title",
            summary = "Test summary",
            fullSummary = "Test full summary",
            emotions = listOf("Happy", "Excited"),
            pathForward = "Test path forward",
            recordingType = "dump",
            sentiment = Sentiment.POSITIVE
        ),
        createdAt = Clock.System.now().toEpochMilliseconds(),
        isArchived = false,
        archivedAt = null,
        status = InsightStatus.SAVED
    )

    // TEST 1: Happy path (PENDING → COMPLETED)
    @Test
    fun `test_happy_path_pending_to_completed`() {
        val recordingId = "rec-123"
        val recording = createRecording(id = recordingId, transcription = null)
        val insight = createInsight(recordingId)

        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { transcriber.transcribe(any(), any()) } returns "Hello world"
        coEvery { insightPort.generateInsight(any(), any()) } returns insight
        coEvery { recordingRepository.updateProcessingStatus(any(), any(), any(), any()) } returns Unit
        coEvery { recordingRepository.updateTranscription(any(), any()) } returns Unit

        runBlocking {
            engine.process(recordingId)
        }

        coVerify {
            // Verify transcriber was called (no cached transcript)
            transcriber.transcribe(any(), any())
            // Verify insight generation was called
            insightPort.generateInsight(recordingId, "Hello world")
            // Verify final status update to COMPLETED
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.COMPLETED
            )
        }
    }

    // TEST 2: Checkpoint — skip transcription if already done
    @Test
    fun `test_checkpoint_skip_transcription_if_exists`() {
        val recordingId = "rec-456"
        val cachedTranscript = "Already transcribed"
        val recording = createRecording(id = recordingId, transcription = cachedTranscript)
        val insight = createInsight(recordingId)

        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { insightPort.generateInsight(any(), any()) } returns insight
        coEvery { recordingRepository.updateProcessingStatus(any(), any(), any(), any()) } returns Unit
        coEvery { recordingRepository.updateTranscription(any(), any()) } returns Unit

        runBlocking {
            engine.process(recordingId)
        }

        coVerify(exactly = 0) {
            // Transcriber should NOT be called (cached transcript exists)
            transcriber.transcribe(any(), any())
        }

        coVerify {
            // But insight generation should be called with cached transcript
            insightPort.generateInsight(recordingId, cachedTranscript)
        }
    }

    // TEST 3: Transient error, attempt 0 → mark PENDING (auto-retry)
    @Test
    fun `test_transient_error_attempt_0_mark_pending`() {
        val recordingId = "rec-789"
        val recording = createRecording(
            id = recordingId,
            transcription = null,
            attempts = 0  // First attempt
        )

        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { transcriber.transcribe(any(), any()) } throws Exception("Connection timeout")
        coEvery { recordingRepository.updateProcessingStatus(any(), any(), any(), any()) } returns Unit

        runBlocking {
            engine.process(recordingId)
        }

        coVerify {
            // Should update to PENDING for auto-retry
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.PENDING,
                errorCode = null
            )
        }
    }

    // TEST 4: Transient error, attempt ≥1 → mark FAILED
    @Test
    fun `test_transient_error_attempt_1_mark_failed`() {
        val recordingId = "rec-101"
        val recording = createRecording(
            id = recordingId,
            transcription = null,
            status = ProcessingStatus.FAILED,
            errorCode = ProcessingErrorCode.NETWORK,
            attempts = 1  // Already attempted once
        )

        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { transcriber.transcribe(any(), any()) } throws Exception("Network error")
        coEvery { recordingRepository.updateProcessingStatus(any(), any(), any(), any()) } returns Unit

        runBlocking {
            engine.process(recordingId)
        }

        coVerify(atLeast = 1) {
            // Should mark FAILED (defer to WorkManager)
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.FAILED,
                errorCode = any(),
                errorMessage = any()
            )
        }
    }

    // TEST 5: Permanent error (language not supported) → mark FAILED
    @Test
    fun `test_permanent_error_language_not_supported_mark_failed`() {
        val recordingId = "rec-202"
        val recording = createRecording(
            id = recordingId,
            transcription = null,
            locale = "fr"  // Unsupported locale
        )

        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { transcriber.transcribe(any(), any()) } throws
            IllegalArgumentException("language not supported")
        coEvery { recordingRepository.updateProcessingStatus(any(), any(), any(), any()) } returns Unit

        runBlocking {
            engine.process(recordingId)
        }

        coVerify(atLeast = 1) {
            // Should mark FAILED with permanent error code (no retry)
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.FAILED,
                errorCode = ProcessingErrorCode.ON_DEVICE_LANGUAGE_NOT_SUPPORTED,
                errorMessage = any()
            )
        }
    }

    // TEST 6: Already completed — skip all processing
    @Test
    fun `test_already_completed_skip_processing`() {
        val recordingId = "rec-303"
        val recording = createRecording(
            id = recordingId,
            status = ProcessingStatus.COMPLETED,
            transcription = "Done"
        )

        coEvery { recordingRepository.getRecording(recordingId) } returns recording

        runBlocking {
            engine.process(recordingId)
        }

        coVerify(exactly = 0) {
            // Transcriber and insight should NOT be called
            transcriber.transcribe(any(), any())
            insightPort.generateInsight(any(), any())
        }
    }

    // TEST 7: Non-retryable FAILED — skip all processing
    @Test
    fun `test_non_retryable_failed_skip_processing`() {
        val recordingId = "rec-404"
        val recording = createRecording(
            id = recordingId,
            status = ProcessingStatus.FAILED,
            errorCode = ProcessingErrorCode.RATE_LIMIT  // Non-retryable
        )

        coEvery { recordingRepository.getRecording(recordingId) } returns recording

        runBlocking {
            engine.process(recordingId)
        }

        coVerify(exactly = 0) {
            // Transcriber and insight should NOT be called
            transcriber.transcribe(any(), any())
            insightPort.generateInsight(any(), any())
        }
    }

    // TEST 8: Rate limit error from insight API
    @Test
    fun `test_rate_limit_error_mark_failed`() {
        val recordingId = "rec-505"
        val recording = createRecording(
            id = recordingId,
            transcription = null,
            attempts = 0
        )

        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { transcriber.transcribe(any(), any()) } returns "Hello"
        coEvery { insightPort.generateInsight(any(), any()) } throws Exception("rate limit")
        coEvery { recordingRepository.updateProcessingStatus(any(), any(), any(), any()) } returns Unit
        coEvery { recordingRepository.updateTranscription(any(), any()) } returns Unit

        runBlocking {
            engine.process(recordingId)
        }

        coVerify(atLeast = 1) {
            // Should mark FAILED (non-retryable, permanent)
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.FAILED,
                errorCode = ProcessingErrorCode.RATE_LIMIT,
                errorMessage = any()
            )
        }
    }

    // TEST 9: Recording not found
    @Test
    fun `test_recording_not_found_throws_error`() {
        val recordingId = "rec-999"

        coEvery { recordingRepository.getRecording(recordingId) } returns null

        try {
            runBlocking {
                engine.process(recordingId)
            }
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Recording $recordingId not found", e.message)
        }
    }

    // TEST 10: All state transitions verified
    @Test
    fun `test_state_transitions_verified`() {
        val recordingId = "rec-610"
        val recording = createRecording(id = recordingId, transcription = null)
        val insight = createInsight(recordingId)

        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { transcriber.transcribe(any(), any()) } returns "Test transcript"
        coEvery { insightPort.generateInsight(any(), any()) } returns insight
        coEvery { recordingRepository.updateProcessingStatus(any(), any(), any(), any()) } returns Unit
        coEvery { recordingRepository.updateTranscription(any(), any()) } returns Unit

        runBlocking {
            engine.process(recordingId)
        }

        coVerify {
            // Verify TRANSCRIBING transition
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.TRANSCRIBING
            )
            // Verify transcript update
            recordingRepository.updateTranscription(recordingId, "Test transcript")
            // Verify GENERATING_INSIGHT transition
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.GENERATING_INSIGHT
            )
            // Verify COMPLETED transition
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.COMPLETED
            )
        }
    }
}
