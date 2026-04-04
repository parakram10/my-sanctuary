# Phase F: Core Processing Engine — Detailed Implementation Plan

**Date:** 2026-04-04  
**Phase:** F (Core Processing Engine)  
**Status:** PLANNING  
**Scope:** Implement `RecordingProcessingEngine` with FSM, single-flight, and checkpoint logic  
**Complexity:** HIGH — Central to the v2 pipeline  
**Dependencies:** Phase A, B, C, D, E (all must be complete)  
**Blocks:** Phase G (Android WorkManager), Phase H (iOS Scheduler), Phase I (Presentation)

---

## 1. Overview

### 1.1 Purpose
`RecordingProcessingEngine` is the **orchestration layer** that coordinates:
- Recording state transitions via FSM
- On-device transcription (Phase D)
- AI insight generation (Phase E)
- Database persistence (Phase C)
- Single-flight mutex (prevent duplicate processing)
- Checkpoint logic (skip transcription if already done)
- Error handling and eligibility for WorkManager retry

### 1.2 Key Concepts

#### FSM (Finite State Machine)
```
PENDING
  ├─ [transcribe]
  └─> TRANSCRIBING
       ├─ [success]
       ├─> [generate insight]
       └─> GENERATING_INSIGHT
            ├─ [success] → COMPLETED
            └─ [error] → FAILED (if retry exhausted)
       ├─ [error, retry < 1] → PENDING (queue for auto-retry)
       └─ [error, retry >= 1] → FAILED

FAILED
  └─> [manual retry via use case] → PENDING

COMPLETED
  └─> [end state, no further processing]
```

#### Single-Flight Guarantee
At most one `process(recordingId)` executes at a time. If two callers invoke `process("same-id")` concurrently:
- First caller: proceeds with processing
- Second caller: returns immediately, waiting for first to complete
- Both callers see the same result

#### Checkpoint Logic
Before transcribing, check if `recording.transcription` already exists:
- If exists: skip transcription, go directly to insight generation
- If null: perform transcription, update DB, then generate insight

---

## 2. Architecture & Design Decisions

### 2.1 Dependency Graph

```
RecordingProcessingEngine
├── RecordingRepository (for reading/writing)
├── InsightPort (for generating insights)
├── OnDeviceTranscriber (for transcription)
└── [internal] Mutex<Job> per recordingId (single-flight)
```

### 2.2 Method Signatures

#### Public API
```kotlin
interface RecordingProcessingEngine {
    /**
     * Process a single recording through the full FSM pipeline.
     *
     * - Fetches the recording by ID
     * - Validates status (skips if COMPLETED or non-retryable FAILED)
     * - Transcribes (checkpoint: skip if transcript exists)
     * - Generates insight
     * - Updates DB with final status
     *
     * Single-flight guarantee: At most one process() per recordingId executes.
     *
     * @param recordingId ID of the recording to process
     * @throws IllegalArgumentException if recording not found
     * @throws Exception if processing fails (caught by caller, not rethrown)
     */
    suspend fun process(recordingId: String)
}
```

#### Internal Implementation Details
```kotlin
internal class RecordingProcessingEngineImpl(
    private val recordingRepository: RecordingRepository,
    private val insightPort: InsightPort,
    private val transcriber: OnDeviceTranscriber,
) : RecordingProcessingEngine {
    
    // Single-flight mutex: Map<recordingId, Job>
    private val processingJobs = ConcurrentHashMap<String, Job>()
    
    // Main entry point
    override suspend fun process(recordingId: String) { ... }
    
    // Internal FSM helpers
    private suspend fun executeProcessing(recording: Recording) { ... }
    private suspend fun performTranscription(recording: Recording): String? { ... }
    private suspend fun performInsightGeneration(recording: Recording, transcription: String) { ... }
    private suspend fun handleError(recording: Recording, error: Exception, attempt: Int) { ... }
}
```

### 2.3 Error Classification

Errors fall into two categories, determining WM retry eligibility:

| Error Type | Examples | Retryable | WM-Eligible | Action |
|-----------|----------|-----------|-------------|--------|
| **Transient** | Network timeout, server 5xx | ✅ Yes | ✅ Yes | Auto-retry once, then queue for WM |
| **Permanent** | Bad transcription locale, rate limit, corrupt file | ❌ No | ❌ No | Mark FAILED, no further retry |

**Implementation:** Check `ProcessingErrorCode.isEligibleForBackgroundRetry` flag

---

## 3. Implementation Details

### 3.1 File Location & Structure

```
feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/processing/
├── RecordingProcessingEngine.kt          (interface)
└── RecordingProcessingEngineImpl.kt       (implementation)
```

### 3.2 Class Skeleton

```kotlin
package sanctuary.app.feature.dump.data.processing

import kotlinx.coroutines.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import sanctuary.app.feature.dump.domain.port.InsightPort
import sanctuary.app.feature.dump.domain.repository.RecordingRepository
import sanctuary.app.feature.dump.domain.transcription.OnDeviceTranscriber
import sanctuary.app.feature.dump.domain.model.Recording
import sanctuary.app.feature.dump.domain.model.ProcessingStatus
import sanctuary.app.feature.dump.domain.model.ProcessingErrorCode
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
    suspend fun process(recordingId: String)
}

/**
 * Implementation of RecordingProcessingEngine with FSM + single-flight + checkpoint.
 */
internal class RecordingProcessingEngineImpl(
    private val recordingRepository: RecordingRepository,
    private val insightPort: InsightPort,
    private val transcriber: OnDeviceTranscriber,
) : RecordingProcessingEngine {

    // Single-flight mutex: prevents concurrent processing of same recording
    private val processingJobs = ConcurrentHashMap<String, Job>()

    /**
     * Process a single recording through the pipeline.
     *
     * Enforces single-flight: if process(id) is already running, waits for it.
     * If process(id) fails, subsequent calls will retry.
     */
    @OptIn(ExperimentalTime::class)
    override suspend fun process(recordingId: String) {
        // TODO: Implement single-flight logic
        // 1. Check if already processing (processingJobs.containsKey)
        // 2. If yes: wait for that Job to complete, return
        // 3. If no: create new Job, store in map, execute, remove from map
    }

    /**
     * Execute the full FSM pipeline for a recording.
     *
     * Flow:
     * 1. Fetch recording
     * 2. Validate status (skip if COMPLETED or non-retryable FAILED)
     * 3. Transcribe (checkpoint: skip if transcript exists)
     * 4. Generate insight
     * 5. Mark COMPLETED
     * 6. On error: classify and handle (retry or mark FAILED)
     */
    private suspend fun executeProcessing(recordingId: String) {
        try {
            // TODO: Implement FSM pipeline
        } catch (e: Exception) {
            // TODO: Handle errors
        } finally {
            // TODO: Clean up
        }
    }

    /**
     * Perform on-device transcription with checkpoint.
     *
     * Checkpoint: if recording already has a transcript, skip transcription.
     * Otherwise, call transcriber, catch errors, and return result or null.
     */
    private suspend fun performTranscription(
        recording: Recording
    ): String? {
        // TODO: Implement checkpoint + transcription
        return null
    }

    /**
     * Generate insight from transcription.
     *
     * Calls InsightPort to generate insight, handles errors, updates DB.
     */
    private suspend fun performInsightGeneration(
        recording: Recording,
        transcription: String
    ) {
        // TODO: Implement insight generation
    }

    /**
     * Handle processing errors with transient vs permanent classification.
     *
     * - Transient: auto-retry once, then queue for WorkManager
     * - Permanent: mark FAILED, do not retry
     */
    private suspend fun handleError(
        recording: Recording,
        error: Exception,
        attempt: Int
    ) {
        // TODO: Classify error + decide retry strategy
    }
}
```

---

## 4. Detailed Implementation Steps

### Step 1: Single-Flight Mutex (Core Foundation)

**File:** `RecordingProcessingEngineImpl.kt`  
**Purpose:** Ensure at most one `process(recordingId)` executes at a time

#### Implementation
```kotlin
override suspend fun process(recordingId: String) {
    coroutineScope {
        // Try to get existing job
        val existingJob = processingJobs[recordingId]
        
        if (existingJob != null && existingJob.isActive) {
            // Another process() is already running; wait for it
            existingJob.join()
            return@coroutineScope
        }
        
        // Create new job and store in map
        val currentJob = Job()
        processingJobs[recordingId] = currentJob
        
        try {
            executeProcessing(recordingId)
        } finally {
            // Clean up from map
            processingJobs.remove(recordingId)
            currentJob.complete()
        }
    }
}
```

**Edge Cases:**
- ✅ Two concurrent calls with same ID: second waits for first
- ✅ Job completes before second call: first call removes from map, second call creates new job
- ✅ Job fails: error is logged, cleaned from map, next call creates new job
- ✅ Thread safety: `ConcurrentHashMap` is thread-safe; `Job.join()` is suspension-safe

---

### Step 2: FSM Pipeline (Main Processing Logic)

**File:** `RecordingProcessingEngineImpl.kt`  
**Purpose:** Orchestrate state transitions

#### Implementation Flow
```kotlin
private suspend fun executeProcessing(recordingId: String) {
    // Step 1: Fetch recording
    val recording = recordingRepository.getRecording(recordingId)
        ?: throw IllegalArgumentException("Recording $recordingId not found")
    
    // Step 2: Validate status — skip if already completed or non-retryable failed
    if (recording.processingStatus == ProcessingStatus.COMPLETED) {
        // Already done; nothing to do
        return
    }
    
    if (recording.processingStatus == ProcessingStatus.FAILED) {
        // Check if error is retryable (WorkManager eligibility)
        val errorCode = recording.errorCode
        if (errorCode != null && !errorCode.isEligibleForBackgroundRetry) {
            // Non-retryable error; don't process again
            return
        }
        // Transient error: proceed with retry
    }
    
    // Step 3: Mark TRANSCRIBING
    recordingRepository.updateProcessingStatus(
        id = recordingId,
        status = ProcessingStatus.TRANSCRIBING
    )
    
    try {
        // Step 4: Transcribe (with checkpoint)
        val transcription = performTranscription(recording)
            ?: throw Exception("Transcription returned null") // Should not happen
        
        // Step 5: Update DB with transcription
        recordingRepository.updateTranscription(recordingId, transcription)
        
        // Step 6: Mark GENERATING_INSIGHT
        recordingRepository.updateProcessingStatus(
            id = recordingId,
            status = ProcessingStatus.GENERATING_INSIGHT
        )
        
        // Step 7: Generate insight
        performInsightGeneration(recording.copy(transcription = transcription), transcription)
        
        // Step 8: Mark COMPLETED
        recordingRepository.updateProcessingStatus(
            id = recordingId,
            status = ProcessingStatus.COMPLETED
        )
        
    } catch (e: Exception) {
        // Step 9: Handle error with retry logic
        handleError(recording, e, attempt = recording.backgroundWmAttempts)
    }
}
```

**State Transition Table:**
| Current Status | Action | Result |
|---|---|---|
| PENDING | Start processing | → TRANSCRIBING |
| TRANSCRIBING | Transcription succeeds | → GENERATING_INSIGHT |
| GENERATING_INSIGHT | Insight succeeds | → COMPLETED |
| (any) | Transient error, attempt=0 | → PENDING (auto-retry) |
| (any) | Transient error, attempt≥1 | → FAILED (defer to WM) |
| (any) | Permanent error | → FAILED (no retry) |
| COMPLETED | Already done | → No change |
| FAILED (non-retryable) | Non-retryable error | → No change |

---

### Step 3: Checkpoint Logic (Transcription Optimization)

**File:** `RecordingProcessingEngineImpl.kt`  
**Purpose:** Skip transcription if already done

#### Implementation
```kotlin
private suspend fun performTranscription(
    recording: Recording
): String? {
    // Checkpoint: if transcript exists, skip transcription
    if (!recording.transcription.isNullOrBlank()) {
        // Already transcribed; return cached result
        return recording.transcription
    }
    
    // Not yet transcribed; perform transcription
    return try {
        transcriber.transcribe(
            filePath = recording.filePath,
            locale = recording.recordingLocale
        )
    } catch (e: Exception) {
        // Transcription failed; let caller handle
        throw e
    }
}
```

**Benefits:**
- ✅ If insight generation fails after successful transcription, next retry skips transcription
- ✅ Reduces API calls (transcription service/network)
- ✅ Speeds up retry attempts
- ✅ Handles case where only insight generation needs retry

---

### Step 4: Error Classification & Retry Strategy

**File:** `RecordingProcessingEngineImpl.kt`  
**Purpose:** Decide auto-retry vs WorkManager retry vs permanent failure

#### Implementation
```kotlin
@OptIn(ExperimentalTime::class)
private suspend fun handleError(
    recording: Recording,
    error: Exception,
    attempt: Int
) {
    // Classify error into ProcessingErrorCode
    val errorCode = classifyError(error)
    
    // Decision tree:
    // 1. If permanent error → mark FAILED
    // 2. If transient error AND attempt < 1 → mark PENDING (auto-retry)
    // 3. If transient error AND attempt >= 1 → mark FAILED (queue for WM)
    
    if (!errorCode.isEligibleForBackgroundRetry) {
        // Permanent error
        recordingRepository.updateProcessingStatus(
            id = recording.id,
            status = ProcessingStatus.FAILED,
            errorCode = errorCode,
            errorMessage = error.message
        )
        return
    }
    
    // Transient error
    if (attempt < 1) {
        // First attempt; auto-retry
        recordingRepository.updateProcessingStatus(
            id = recording.id,
            status = ProcessingStatus.PENDING,
            errorCode = null,  // Clear error code
            errorMessage = null
        )
        // Will be retried by WorkManager or next app session
    } else {
        // Already retried; give up, let WorkManager handle
        recordingRepository.updateProcessingStatus(
            id = recording.id,
            status = ProcessingStatus.FAILED,
            errorCode = errorCode,
            errorMessage = error.message
        )
        // WorkManager may retry later if eligible
    }
}

private fun classifyError(error: Exception): ProcessingErrorCode {
    val message = error.message?.lowercase() ?: ""
    
    return when {
        // Transient errors
        message.contains("timeout") -> ProcessingErrorCode.TIMEOUT
        message.contains("network") -> ProcessingErrorCode.NETWORK
        message.contains("connection") -> ProcessingErrorCode.NETWORK
        message.contains("unavailable") -> ProcessingErrorCode.NETWORK
        
        // Permanent errors
        message.contains("language not supported") -> ProcessingErrorCode.ON_DEVICE_LANGUAGE_NOT_SUPPORTED
        message.contains("corrupt") -> ProcessingErrorCode.CORRUPT_FILE
        message.contains("bad request") || message.contains("invalid") -> ProcessingErrorCode.BAD_REQUEST
        message.contains("rate limit") -> ProcessingErrorCode.RATE_LIMIT
        
        // Default: unknown transient (safest to retry)
        else -> ProcessingErrorCode.UNKNOWN_TRANSIENT
    }
}
```

**Retry Policy Table:**
| Error | Retryable | Attempt 0 | Attempt ≥1 |
|-------|-----------|-----------|-----------|
| NETWORK | Yes | → PENDING | → FAILED |
| TIMEOUT | Yes | → PENDING | → FAILED |
| RATE_LIMIT | No | → FAILED | → FAILED |
| ON_DEVICE_LANGUAGE_NOT_SUPPORTED | No | → FAILED | → FAILED |
| UNKNOWN_TRANSIENT | Yes | → PENDING | → FAILED |

---

### Step 5: DI Integration

**File:** `feature_dump/src/dataMain/kotlin/.../di/DumpDataModule.kt`  
**Purpose:** Wire `RecordingProcessingEngine` into Koin

#### Implementation
```kotlin
// Add to dumpDataModule
val dumpDataModule = module {
    singleOf(::RecordingLocalDataSourceImpl) bind RecordingLocalDataSource::class
    singleOf(::RecordingRepositoryImpl) bind RecordingRepository::class
    
    // Wire RecordingProcessingEngine (requires dependencies from DI)
    single<RecordingProcessingEngine> {
        RecordingProcessingEngineImpl(
            recordingRepository = get(),
            insightPort = get(),
            transcriber = get()  // From platform-specific module
        )
    }
    
    includes(providePlatformTranscriptionModule())
    includes(insightModule)
}
```

**Dependency Resolution:**
- ✅ `recordingRepository`: provided by `singleOf(::RecordingRepositoryImpl)`
- ✅ `insightPort`: provided by `insightModule` (bound as `single<InsightPort>`)
- ✅ `transcriber`: provided by platform module (Android/iOS implementation of `OnDeviceTranscriber`)

---

## 5. Testing Strategy

### 5.1 Unit Tests

**File:** `feature_dump/src/commonTest/kotlin/.../processing/RecordingProcessingEngineTest.kt`

#### Test Cases

```kotlin
class RecordingProcessingEngineTest {
    
    // Mocks
    private lateinit var recordingRepository: RecordingRepository
    private lateinit var insightPort: InsightPort
    private lateinit var transcriber: OnDeviceTranscriber
    private lateinit var engine: RecordingProcessingEngine
    
    @Before
    fun setup() {
        recordingRepository = mockk()
        insightPort = mockk()
        transcriber = mockk()
        engine = RecordingProcessingEngineImpl(
            recordingRepository = recordingRepository,
            insightPort = insightPort,
            transcriber = transcriber
        )
    }
    
    // TEST 1: Happy path (PENDING → COMPLETED)
    @Test
    fun `process() happy path: transcribe + insight → COMPLETED`() {
        // Given
        val recordingId = "rec-123"
        val recording = Recording(
            id = recordingId,
            filePath = "/tmp/recording.m4a",
            transcription = null,  // Not yet transcribed
            processingStatus = ProcessingStatus.PENDING,
            recordingLocale = "en"
        )
        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { transcriber.transcribe(any(), any()) } returns "Hello world"
        coEvery { insightPort.generateInsight(any(), any()) } returns Insight(
            id = "insight-1",
            recordingId = recordingId,
            content = InsightContent(...),
            createdAt = Clock.System.now().toEpochMilliseconds(),
            isArchived = false,
            status = InsightStatus.SAVED
        )
        
        // When
        runBlocking {
            engine.process(recordingId)
        }
        
        // Then
        verify {
            // Verify state transitions: TRANSCRIBING → GENERATING_INSIGHT → COMPLETED
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.TRANSCRIBING
            )
            recordingRepository.updateTranscription(recordingId, "Hello world")
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.GENERATING_INSIGHT
            )
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.COMPLETED
            )
        }
    }
    
    // TEST 2: Checkpoint — skip transcription if already done
    @Test
    fun `process() checkpoint: skip transcription if transcript exists`() {
        // Given
        val recordingId = "rec-456"
        val recording = Recording(
            id = recordingId,
            filePath = "/tmp/recording.m4a",
            transcription = "Already transcribed",  // Already has transcript
            processingStatus = ProcessingStatus.PENDING,
            recordingLocale = "en"
        )
        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { insightPort.generateInsight(any(), any()) } returns Insight(...)
        
        // When
        runBlocking {
            engine.process(recordingId)
        }
        
        // Then
        // Verify transcriber was NOT called (checkpoint skipped it)
        verify(exactly = 0) {
            transcriber.transcribe(any(), any())
        }
        // But insight generation was called with cached transcript
        verify {
            insightPort.generateInsight(recordingId, "Already transcribed")
        }
    }
    
    // TEST 3: Transient error, attempt 0 → auto-retry (mark PENDING)
    @Test
    fun `process() transient error, attempt 0: mark PENDING for auto-retry`() {
        // Given
        val recordingId = "rec-789"
        val recording = Recording(
            id = recordingId,
            filePath = "/tmp/recording.m4a",
            transcription = null,
            processingStatus = ProcessingStatus.PENDING,
            backgroundWmAttempts = 0,  // First attempt
            recordingLocale = "en"
        )
        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { transcriber.transcribe(any(), any()) } throws Exception("Network timeout")
        
        // When
        runBlocking {
            engine.process(recordingId)
        }
        
        // Then
        // Should mark PENDING for auto-retry (not FAILED)
        verify {
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.PENDING,
                errorCode = null
            )
        }
    }
    
    // TEST 4: Transient error, attempt ≥1 → mark FAILED (defer to WM)
    @Test
    fun `process() transient error, attempt ≥1: mark FAILED, defer to WM`() {
        // Given
        val recordingId = "rec-101"
        val recording = Recording(
            id = recordingId,
            filePath = "/tmp/recording.m4a",
            transcription = null,
            processingStatus = ProcessingStatus.FAILED,
            backgroundWmAttempts = 1,  // Already attempted once
            errorCode = ProcessingErrorCode.NETWORK,
            recordingLocale = "en"
        )
        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { transcriber.transcribe(any(), any()) } throws Exception("Network timeout")
        
        // When
        runBlocking {
            engine.process(recordingId)
        }
        
        // Then
        // Should mark FAILED, not retry locally
        verify {
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.FAILED,
                errorCode = ProcessingErrorCode.TIMEOUT
            )
        }
    }
    
    // TEST 5: Permanent error → mark FAILED (no retry)
    @Test
    fun `process() permanent error: mark FAILED, no retry`() {
        // Given
        val recordingId = "rec-202"
        val recording = Recording(
            id = recordingId,
            filePath = "/tmp/recording.m4a",
            transcription = null,
            processingStatus = ProcessingStatus.PENDING,
            backgroundWmAttempts = 0,
            recordingLocale = "fr"  // Unsupported locale
        )
        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { transcriber.transcribe(any(), any()) } throws 
            IllegalArgumentException("Language 'fr' not supported")
        
        // When
        runBlocking {
            engine.process(recordingId)
        }
        
        // Then
        // Should mark FAILED with permanent error code
        verify {
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.FAILED,
                errorCode = ProcessingErrorCode.ON_DEVICE_LANGUAGE_NOT_SUPPORTED
            )
        }
    }
    
    // TEST 6: Single-flight — concurrent calls with same ID wait
    @Test
    fun `process() single-flight: concurrent calls with same ID serialize`() {
        // Given
        val recordingId = "rec-303"
        val recording = Recording(
            id = recordingId,
            filePath = "/tmp/recording.m4a",
            transcription = null,
            processingStatus = ProcessingStatus.PENDING,
            recordingLocale = "en"
        )
        
        // Transcription takes 100ms
        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { transcriber.transcribe(any(), any()) } coAnswers {
            delay(100)
            "Hello world"
        }
        coEvery { insightPort.generateInsight(any(), any()) } returns Insight(...)
        
        // When: two calls to process(same-id) in parallel
        val job1 = async { engine.process(recordingId) }
        val job2 = async { engine.process(recordingId) }
        
        runBlocking {
            awaitAll(job1, job2)
        }
        
        // Then: transcriber should be called only once (single-flight)
        coVerify(exactly = 1) {
            transcriber.transcribe(any(), any())
        }
    }
    
    // TEST 7: Already completed — skip processing
    @Test
    fun `process() completed recording: skip all processing`() {
        // Given
        val recordingId = "rec-404"
        val recording = Recording(
            id = recordingId,
            filePath = "/tmp/recording.m4a",
            transcription = "Done",
            processingStatus = ProcessingStatus.COMPLETED,  // Already completed
            recordingLocale = "en"
        )
        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        
        // When
        runBlocking {
            engine.process(recordingId)
        }
        
        // Then: no further processing
        verify(exactly = 0) {
            transcriber.transcribe(any(), any())
            insightPort.generateInsight(any(), any())
        }
    }
    
    // TEST 8: Rate limit error from insight API
    @Test
    fun `process() rate limit error: mark FAILED with RATE_LIMIT code`() {
        // Given
        val recordingId = "rec-505"
        val recording = Recording(
            id = recordingId,
            filePath = "/tmp/recording.m4a",
            transcription = null,
            processingStatus = ProcessingStatus.PENDING,
            backgroundWmAttempts = 0,
            recordingLocale = "en"
        )
        coEvery { recordingRepository.getRecording(recordingId) } returns recording
        coEvery { transcriber.transcribe(any(), any()) } returns "Hello"
        coEvery { insightPort.generateInsight(any(), any()) } throws 
            Exception("Rate limit exceeded")
        
        // When
        runBlocking {
            engine.process(recordingId)
        }
        
        // Then
        verify {
            recordingRepository.updateProcessingStatus(
                id = recordingId,
                status = ProcessingStatus.FAILED,
                errorCode = ProcessingErrorCode.RATE_LIMIT
            )
        }
    }
}
```

### 5.2 Integration Tests

**Context:** Requires Phase B, C, D, E to be complete

```kotlin
class RecordingProcessingEngineIntegrationTest {
    // Integration tests with real DB + mock API
    // Test full flow: insert → process → verify DB state
}
```

---

## 6. Edge Cases & Gotchas

### 6.1 Race Condition: Multiple Updates

**Scenario:** Insight generation succeeds, but DB update for status fails

**Mitigation:**
- Wrap state updates in try-catch
- If update fails, log and let WorkManager retry later
- Status will be out-of-sync temporarily, but will resolve

### 6.2 Cancellation: Job Cancelled During Processing

**Scenario:** User closes app, Coroutine.cancel() is called mid-processing

**Mitigation:**
- Use `ensureActive()` checks in long-running operations
- Finally block ensures cleanup (remove from processingJobs map)
- Next app session will retry from where it left off (DB status is checkpoint)

### 6.3 Duplicate Insight Generation

**Scenario:** Insight generation succeeds, but DB write fails; next retry generates again

**Current Design:** OK — InsightRepository handles this (queries by recordingId, replaces old insight)

### 6.4 Checkpoint Update Race

**Scenario:** Transcription succeeds, updates DB, but then DB read fails before insight generation

**Current Design:** 
- If transcription updates DB successfully, next retry will checkpoint-skip transcription
- OK because DB is source of truth

### 6.5 Memory Leak: processingJobs Map

**Scenario:** If Job is never removed from map, memory accumulates

**Mitigation:**
- Always remove in finally block
- Job is only stored during processing; cleared after completion/error
- ConcurrentHashMap has no size limit, but map is cleared per recording

---

## 7. Implementation Order

### Checklist
- [ ] **Step 1:** Create `RecordingProcessingEngine.kt` interface
- [ ] **Step 2:** Create `RecordingProcessingEngineImpl.kt` with skeleton
- [ ] **Step 3:** Implement `process()` single-flight logic
- [ ] **Step 4:** Implement `executeProcessing()` FSM pipeline
- [ ] **Step 5:** Implement `performTranscription()` checkpoint logic
- [ ] **Step 6:** Implement `performInsightGeneration()` 
- [ ] **Step 7:** Implement `handleError()` classification & retry
- [ ] **Step 8:** Implement `classifyError()` helper
- [ ] **Step 9:** Add to Koin DI (DumpDataModule.kt)
- [ ] **Step 10:** Compile & test on metadata, Android, iOS
- [ ] **Step 11:** Write unit tests (RecordingProcessingEngineTest.kt)
- [ ] **Step 12:** Run all tests, verify coverage

---

## 8. Compilation & Verification

### 8.1 Incremental Compilation Checks

After implementing each step, run:

```bash
# Shared KMP (domainMain + dataMain)
./gradlew :feature_dump:compileKotlinMetadata -q

# Android
./gradlew :feature_dump:compileDebugKotlinAndroid -q

# Optional: iOS (Phase D skeleton errors may still exist)
./gradlew :feature_dump:compileKotlinIosArm64 -q
```

### 8.2 Final Verification

```bash
# Run unit tests (once written)
./gradlew :feature_dump:testDebugUnitTest -q

# Build full feature_dump module
./gradlew :feature_dump:build -q
```

---

## 9. Success Criteria

### Phase F is Complete When:

1. ✅ `RecordingProcessingEngine` interface created
2. ✅ `RecordingProcessingEngineImpl` implements all methods
3. ✅ Single-flight mutex prevents concurrent processing
4. ✅ FSM transitions through PENDING → TRANSCRIBING → GENERATING_INSIGHT → COMPLETED/FAILED
5. ✅ Checkpoint: skips transcription if already done
6. ✅ Error classification: transient vs permanent
7. ✅ Retry strategy: auto-retry once, then defer to WorkManager
8. ✅ Wired into Koin DI
9. ✅ Unit tests cover all 8+ scenarios
10. ✅ Compiles clean on shared + Android
11. ✅ No new errors (Phase D skeleton errors OK)

---

## 10. Notes & Assumptions

### Assumptions
- Phase B (Database Schema) is complete
- Phase C (Repository Implementations) is complete
- Phase D (On-Device Transcription) is available (skeleton + implementation)
- Phase E (Insight Generation Service) is complete
- All dependencies are available in Koin

### Constraints
- **No nested `process()` calls:** Engine assumes single-level invocation
- **Locale fixed at save:** Recording locale set when saved, not changed during processing
- **No local queue:** Engine retries once, defers to WorkManager for persistent retry
- **English-only insights:** Transcription may be Hindi, but insights are English

---

## 11. Next Phase Dependencies

### Phase G (Android WorkManager) depends on:
- ✅ RecordingProcessingEngine.process() implemented
- ✅ Can call engine.process(recordingId) from WorkManager worker

### Phase H (iOS Background Scheduler) depends on:
- ✅ RecordingProcessingEngine.process() implemented
- ✅ Can call engine.process(recordingId) from app foreground delegate

### Phase I (Presentation Layer) depends on:
- ✅ RecordingProcessingEngine.process() implemented
- ✅ Can be injected into ViewModel for testing

---

## Appendix: FSM Diagram (ASCII)

```
                    ┌─────────────┐
                    │   PENDING   │
                    └──────┬──────┘
                           │
                   [process() called]
                           │
                    ┌──────▼────────┐
                    │ TRANSCRIBING  │
                    └──────┬────────┘
                           │
                    ┌──────┴──────────────┐
                    │                     │
            [success]                [error]
                    │                     │
            ┌───────▼─────┐      ┌────────▼────────┐
            │  [checkpoint]  │      │ classify error  │
            │ [transcribed]│      └────┬────────────┘
            └───────┬─────┘             │
                    │       ┌──────────┤
                    │       │          │
                    │  [transient] [permanent]
                    │       │          │
              ┌─────▼────┐  │    ┌─────▼──────┐
              │ GENERATING_INSIGHT │  │    │  FAILED  │
              │(or skip if exists)│  │    └──────────┘
              └─────┬────┘        │
                    │     ┌───────┘
            ┌───────┴─────┐
            │             │
        [success]    [error]
            │             │
            │    ┌────────┴─────────┐
            │    │                  │
            │ [attempt < 1]  [attempt ≥ 1]
            │    │                  │
            │  ┌─▼────────────┐  ┌──▼───┐
            │  │   PENDING    │  │FAILED│
            │  │(auto-retry)  │  └──────┘
            │  └──────────────┘
            │
       ┌────▼─────────┐
       │  COMPLETED   │
       └──────────────┘
```

