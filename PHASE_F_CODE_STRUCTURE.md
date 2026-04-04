# Phase F: Code Structure & Implementation Guide

**Purpose:** Visual guide to Phase F implementation with code examples and patterns

---

## 1. Overall Architecture

```
RecordingProcessingEngine
│
├── Single-Flight Mutex
│   └── ConcurrentHashMap<recordingId, Job>
│
├── FSM Pipeline
│   ├── process(recordingId) [entry]
│   ├── executeProcessing(recordingId) [FSM]
│   ├── performTranscription() [checkpoint]
│   ├── performInsightGeneration() [insight]
│   └── handleError() [retry logic]
│
└── Dependencies (via DI)
    ├── RecordingRepository
    ├── InsightPort
    └── OnDeviceTranscriber
```

---

## 2. File Structure

### Create Two Files

#### File 1: `RecordingProcessingEngine.kt`
```
location: feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/processing/
size: ~20 lines
content: Interface only
```

#### File 2: `RecordingProcessingEngineImpl.kt`
```
location: feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/processing/
size: ~300-400 lines
content: Full implementation with FSM, single-flight, checkpoint
```

#### Modify: `DumpDataModule.kt`
```
location: feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/di/
change: Add DI binding for RecordingProcessingEngine
```

---

## 3. Detailed Code Example

### Part 1: Interface Definition

**File:** `RecordingProcessingEngine.kt`

```kotlin
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
```

---

### Part 2: Implementation Skeleton

**File:** `RecordingProcessingEngineImpl.kt` (Part A: Imports + Class Definition)

```kotlin
package sanctuary.app.feature.dump.data.processing

import kotlinx.coroutines.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import sanctuary.app.feature.dump.domain.model.ProcessingErrorCode
import sanctuary.app.feature.dump.domain.model.ProcessingStatus
import sanctuary.app.feature.dump.domain.model.Recording
import sanctuary.app.feature.dump.domain.port.InsightPort
import sanctuary.app.feature.dump.domain.repository.RecordingRepository
import sanctuary.app.feature.dump.domain.transcription.OnDeviceTranscriber
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Implementation of RecordingProcessingEngine with FSM + single-flight + checkpoint.
 *
 * Orchestrates the full pipeline:
 * 1. Fetch recording from DB
 * 2. Validate status (skip if completed/non-retryable failed)
 * 3. Transcribe with checkpoint (skip if already done)
 * 4. Generate insight
 * 5. Mark COMPLETED
 * 6. On error: classify and handle (retry or mark FAILED)
 *
 * Single-flight mutex ensures only one process(recordingId) executes at a time,
 * while process(different_id) can run in parallel.
 */
internal class RecordingProcessingEngineImpl(
    private val recordingRepository: RecordingRepository,
    private val insightPort: InsightPort,
    private val transcriber: OnDeviceTranscriber,
) : RecordingProcessingEngine {

    // Single-flight mutex: Map<recordingId, Job>
    // Prevents concurrent processing of the same recording
    private val processingJobs = ConcurrentHashMap<String, Job>()

    // Rest of implementation follows...
}
```

---

### Part 3: Single-Flight Logic

**File:** `RecordingProcessingEngineImpl.kt` (Part B: process() method)

```kotlin
override suspend fun process(recordingId: String) {
    coroutineScope {
        // Attempt to get an existing job for this recording
        while (true) {
            val existingJob = processingJobs[recordingId]
            
            if (existingJob != null && existingJob.isActive) {
                // Another process() call is already running for this recording
                // Wait for it to complete, then return
                try {
                    existingJob.join()
                    return@coroutineScope
                } catch (e: Exception) {
                    // Job failed; we'll try again below
                    continue
                }
            }
            
            // No active job; safe to create one
            break
        }
        
        // Create a new Job and store it in the map
        val currentJob = Job()
        val wasInserted = processingJobs.putIfAbsent(recordingId, currentJob)
        
        if (wasInserted != null) {
            // Another thread inserted a job; recursively wait for it
            process(recordingId)
            return@coroutineScope
        }
        
        // Now we own the processing for this recording
        try {
            executeProcessing(recordingId)
        } finally {
            // Clean up from map when done (success or error)
            processingJobs.remove(recordingId)
            currentJob.complete()
        }
    }
}
```

**Key Points:**
- ✅ ConcurrentHashMap is thread-safe
- ✅ putIfAbsent() prevents race condition
- ✅ finally block always runs (cleanup)
- ✅ join() is suspension-safe (not blocking)

---

### Part 4: FSM Pipeline

**File:** `RecordingProcessingEngineImpl.kt` (Part C: executeProcessing() method)

```kotlin
@OptIn(ExperimentalTime::class)
private suspend fun executeProcessing(recordingId: String) {
    // STEP 1: Fetch recording from DB
    val recording = recordingRepository.getRecording(recordingId)
        ?: throw IllegalArgumentException("Recording $recordingId not found")
    
    // STEP 2: Validate status — skip if already completed
    if (recording.processingStatus == ProcessingStatus.COMPLETED) {
        // Already successfully processed; nothing to do
        return
    }
    
    // STEP 3: Validate status — skip if non-retryable failed
    if (recording.processingStatus == ProcessingStatus.FAILED) {
        val errorCode = recording.errorCode
        if (errorCode != null && !errorCode.isEligibleForBackgroundRetry) {
            // Non-retryable error; don't attempt again
            return
        }
        // Otherwise: transient error, proceed with retry
    }
    
    // STEP 4: Transition to TRANSCRIBING
    recordingRepository.updateProcessingStatus(
        id = recordingId,
        status = ProcessingStatus.TRANSCRIBING
    )
    
    try {
        // STEP 5: Transcribe (with checkpoint logic)
        val transcription = performTranscription(recording)
            ?: throw IllegalStateException("Transcription returned null")
        
        // STEP 6: Update DB with transcript
        recordingRepository.updateTranscription(recordingId, transcription)
        
        // STEP 7: Transition to GENERATING_INSIGHT
        recordingRepository.updateProcessingStatus(
            id = recordingId,
            status = ProcessingStatus.GENERATING_INSIGHT
        )
        
        // STEP 8: Generate insight from transcript
        performInsightGeneration(
            recording = recording.copy(transcription = transcription),
            transcription = transcription
        )
        
        // STEP 9: Transition to COMPLETED (success!)
        recordingRepository.updateProcessingStatus(
            id = recordingId,
            status = ProcessingStatus.COMPLETED
        )
        
    } catch (e: Exception) {
        // STEP 10: Handle error with retry classification
        handleError(recording, e, attempt = recording.backgroundWmAttempts)
    }
}
```

**State Transitions:**
```
PENDING
  ├─ [updateProcessingStatus(TRANSCRIBING)]
  ├─ [on transcription success]
  ├─ [updateProcessingStatus(GENERATING_INSIGHT)]
  ├─ [on insight success]
  └─ [updateProcessingStatus(COMPLETED)]  ← SUCCESS

[on error]
  └─ [handleError()] → PENDING or FAILED
```

---

### Part 5: Checkpoint Logic

**File:** `RecordingProcessingEngineImpl.kt` (Part D: performTranscription() method)

```kotlin
private suspend fun performTranscription(recording: Recording): String? {
    // CHECKPOINT: If transcript already exists, skip transcription
    if (!recording.transcription.isNullOrBlank()) {
        // Optimization: cached transcript exists
        // Use it instead of re-transcribing
        return recording.transcription
    }
    
    // Not yet transcribed; perform transcription
    return try {
        transcriber.transcribe(
            filePath = recording.filePath,
            locale = recording.recordingLocale
        )
    } catch (e: Exception) {
        // Transcription failed; let caller handle via handleError()
        throw e
    }
}
```

**Benefits:**
```
Scenario 1: PENDING [no transcript]
  └─ performTranscription() → calls transcriber → returns transcript

Scenario 2: FAILED, auto-retry [has transcript from previous attempt]
  └─ performTranscription() → returns cached transcript (no transcriber call)

Scenario 3: PENDING → TRANSCRIBING → (DB error updating)
  └─ next retry: checks transcript in DB → uses cached
```

---

### Part 6: Error Handling

**File:** `RecordingProcessingEngineImpl.kt` (Part E: handleError() method)

```kotlin
@OptIn(ExperimentalTime::class)
private suspend fun handleError(
    recording: Recording,
    error: Exception,
    attempt: Int
) {
    // STEP 1: Classify error into ProcessingErrorCode
    val errorCode = classifyError(error)
    
    // STEP 2: Decide retry strategy based on error type
    when {
        // Permanent errors → always FAILED (no retry)
        !errorCode.isEligibleForBackgroundRetry -> {
            recordingRepository.updateProcessingStatus(
                id = recording.id,
                status = ProcessingStatus.FAILED,
                errorCode = errorCode,
                errorMessage = error.message
            )
        }
        
        // Transient errors → auto-retry once, then defer to WM
        attempt < 1 -> {
            // First attempt; mark PENDING for auto-retry
            recordingRepository.updateProcessingStatus(
                id = recording.id,
                status = ProcessingStatus.PENDING,
                errorCode = null,  // Clear error code (will retry)
                errorMessage = null
            )
        }
        
        else -> {
            // Already retried; mark FAILED, defer to WorkManager
            recordingRepository.updateProcessingStatus(
                id = recording.id,
                status = ProcessingStatus.FAILED,
                errorCode = errorCode,
                errorMessage = error.message
            )
        }
    }
}

private fun classifyError(error: Exception): ProcessingErrorCode {
    val message = error.message?.lowercase() ?: ""
    val cause = error.cause?.message?.lowercase() ?: ""
    val fullMessage = "$message $cause"
    
    return when {
        // Transient errors (retry-eligible)
        fullMessage.contains("timeout") -> ProcessingErrorCode.TIMEOUT
        fullMessage.contains("connection refused") -> ProcessingErrorCode.NETWORK
        fullMessage.contains("connection reset") -> ProcessingErrorCode.NETWORK
        fullMessage.contains("connection lost") -> ProcessingErrorCode.NETWORK
        fullMessage.contains("network") -> ProcessingErrorCode.NETWORK
        fullMessage.contains("unavailable") -> ProcessingErrorCode.NETWORK
        
        // Permanent errors (no retry)
        fullMessage.contains("language") && fullMessage.contains("not supported") -> 
            ProcessingErrorCode.ON_DEVICE_LANGUAGE_NOT_SUPPORTED
        fullMessage.contains("corrupt") -> ProcessingErrorCode.CORRUPT_FILE
        fullMessage.contains("file not found") -> ProcessingErrorCode.CORRUPT_FILE
        fullMessage.contains("permission denied") -> ProcessingErrorCode.CORRUPT_FILE
        fullMessage.contains("bad request") -> ProcessingErrorCode.BAD_REQUEST
        fullMessage.contains("invalid") -> ProcessingErrorCode.BAD_REQUEST
        fullMessage.contains("rate limit") -> ProcessingErrorCode.RATE_LIMIT
        fullMessage.contains("quota") -> ProcessingErrorCode.RATE_LIMIT
        
        // Default: treat as transient (safer to retry)
        else -> ProcessingErrorCode.UNKNOWN_TRANSIENT
    }
}
```

**Error Classification Table:**
```
Error Message Pattern         → ErrorCode                              → Retryable?
─────────────────────────────────────────────────────────────────────────────────
"timeout"                     → TIMEOUT                                → ✅ Yes
"network", "connection"       → NETWORK                                → ✅ Yes
"language not supported"      → ON_DEVICE_LANGUAGE_NOT_SUPPORTED       → ❌ No
"corrupt", "not found"        → CORRUPT_FILE                           → ❌ No
"bad request", "invalid"      → BAD_REQUEST                            → ❌ No
"rate limit", "quota"         → RATE_LIMIT                             → ❌ No
(default)                     → UNKNOWN_TRANSIENT                      → ✅ Yes
```

---

### Part 7: Insight Generation

**File:** `RecordingProcessingEngineImpl.kt` (Part F: performInsightGeneration() method)

```kotlin
private suspend fun performInsightGeneration(
    recording: Recording,
    transcription: String
) {
    // Generate insight from transcription
    val insight = insightPort.generateInsight(
        recordingId = recording.id,
        transcription = transcription
    )
    
    // Note: InsightPort.generateInsight() returns Insight
    // It does NOT save to DB — that's done by InsightRepository
    // For v2 pipeline, we assume insight is saved elsewhere
    // or we could call insight repository here if needed
    
    // If this method throws (from InsightPort), 
    // it's caught by executeProcessing() and passed to handleError()
}
```

**Note:** Depending on your architecture:
- Option A: InsightPort only returns Insight (no DB save)
- Option B: InsightPort saves to DB internally
- Option C: RecordingProcessingEngine should save to DB

Verify this with your Phase E implementation!

---

### Part 8: DI Module Integration

**File:** `DumpDataModule.kt` (add to existing module)

```kotlin
val dumpDataModule = module {
    singleOf(::RecordingLocalDataSourceImpl) bind RecordingLocalDataSource::class
    singleOf(::RecordingRepositoryImpl) bind RecordingRepository::class
    
    // NEW: Wire RecordingProcessingEngine
    single<RecordingProcessingEngine> {
        RecordingProcessingEngineImpl(
            recordingRepository = get(),
            insightPort = get(),              // From insightModule
            transcriber = get()               // From platform module
        )
    }
    
    includes(providePlatformTranscriptionModule())
    includes(insightModule)
}
```

**Dependency Resolution:**
```
get<RecordingRepository>()
  ← RecordingRepositoryImpl (bound via singleOf)
  
get<InsightPort>()
  ← insightModule provides single<InsightPort>
  ← Wired to ClaudeInsightGenerationService or GroqInsightGenerationService
  
get<OnDeviceTranscriber>()
  ← providePlatformTranscriptionModule() [expect/actual]
  ← androidMain: AndroidOnDeviceTranscriber
  ← iosMain: IosOnDeviceTranscriber
```

---

## 4. Implementation Checklist

### Phase 1: Scaffolding
- [ ] Create `RecordingProcessingEngine.kt` (interface)
- [ ] Create `RecordingProcessingEngineImpl.kt` (skeleton with all methods)
- [ ] Add imports and class definition

### Phase 2: Single-Flight
- [ ] Implement `process()` with ConcurrentHashMap
- [ ] Handle race condition with putIfAbsent()
- [ ] Test: concurrent calls to same ID

### Phase 3: FSM Pipeline
- [ ] Implement `executeProcessing()`
- [ ] Add state transitions: PENDING → TRANSCRIBING → GENERATING_INSIGHT → COMPLETED
- [ ] Add early returns for COMPLETED and non-retryable FAILED

### Phase 4: Checkpoint
- [ ] Implement `performTranscription()`
- [ ] Check if transcript exists, skip if yes
- [ ] Call transcriber if no

### Phase 5: Insight
- [ ] Implement `performInsightGeneration()`
- [ ] Call InsightPort.generateInsight()
- [ ] Handle errors (will be caught by executeProcessing)

### Phase 6: Error Handling
- [ ] Implement `handleError()`
- [ ] Implement `classifyError()`
- [ ] Classify transient vs permanent
- [ ] Apply retry strategy

### Phase 7: DI
- [ ] Add binding to `DumpDataModule.kt`
- [ ] Verify dependencies are available

### Phase 8: Testing
- [ ] Create `RecordingProcessingEngineTest.kt`
- [ ] Write 8+ test cases (see PHASE_F_IMPLEMENTATION_PLAN.md)
- [ ] Run tests, verify all pass

### Phase 9: Verification
- [ ] Compile: `./gradlew :feature_dump:compileKotlinMetadata -q`
- [ ] Compile: `./gradlew :feature_dump:compileDebugKotlinAndroid -q`
- [ ] Verify no new errors

---

## 5. Common Patterns

### Pattern 1: Safe State Update
```kotlin
// ✅ GOOD: Update status with error code
recordingRepository.updateProcessingStatus(
    id = recordingId,
    status = ProcessingStatus.FAILED,
    errorCode = ProcessingErrorCode.NETWORK,
    errorMessage = error.message
)

// ❌ BAD: Partial update (missing error code)
recordingRepository.updateProcessingStatus(
    id = recordingId,
    status = ProcessingStatus.FAILED
    // missing errorCode → defaults to null
)
```

### Pattern 2: Checkpoint Pattern
```kotlin
// ✅ GOOD: Check then use cached
if (!recording.transcription.isNullOrBlank()) {
    return recording.transcription  // Use cached
}
// Perform transcription if not cached
```

### Pattern 3: Error Classification
```kotlin
// ✅ GOOD: Use message content
val message = error.message?.lowercase() ?: ""
return when {
    message.contains("timeout") -> ProcessingErrorCode.TIMEOUT
    message.contains("network") -> ProcessingErrorCode.NETWORK
    else -> ProcessingErrorCode.UNKNOWN_TRANSIENT
}

// ❌ BAD: Catch exception type (too specific)
return when (error) {
    is SocketTimeoutException -> ProcessingErrorCode.TIMEOUT
    is IOException -> ProcessingErrorCode.NETWORK
    else -> ProcessingErrorCode.UNKNOWN_TRANSIENT
}
// Why: error source may vary (Ktor wraps, etc.)
```

### Pattern 4: Cleanup in Finally
```kotlin
try {
    executeProcessing(recordingId)
} finally {
    // Always runs, even on exception or cancel
    processingJobs.remove(recordingId)
    currentJob.complete()
}
```

---

## 6. Testing Patterns

### Mock Setup
```kotlin
class RecordingProcessingEngineTest {
    private val recordingRepository = mockk<RecordingRepository>()
    private val insightPort = mockk<InsightPort>()
    private val transcriber = mockk<OnDeviceTranscriber>()
    
    private val engine = RecordingProcessingEngineImpl(
        recordingRepository = recordingRepository,
        insightPort = insightPort,
        transcriber = transcriber
    )
}
```

### Test Pattern: Happy Path
```kotlin
@Test
fun `process() happy path`() {
    // Arrange
    coEvery { recordingRepository.getRecording(id) } returns recording
    coEvery { transcriber.transcribe(any(), any()) } returns "Hello"
    coEvery { insightPort.generateInsight(any(), any()) } returns insight
    
    // Act
    runBlocking { engine.process(id) }
    
    // Assert
    coVerify {
        recordingRepository.updateProcessingStatus(
            id = id,
            status = ProcessingStatus.COMPLETED
        )
    }
}
```

---

## 7. Debugging Tips

### Enable Logging
```kotlin
private suspend fun executeProcessing(recordingId: String) {
    println("START processing: $recordingId")
    try {
        println("  TRANSCRIBING...")
        val transcription = performTranscription(recording)
        println("  GENERATING_INSIGHT...")
        performInsightGeneration(recording, transcription)
        println("  COMPLETED!")
    } catch (e: Exception) {
        println("  ERROR: ${e.message}")
        handleError(recording, e, attempt)
    }
}
```

### Single-Flight Debugging
```kotlin
override suspend fun process(recordingId: String) {
    val isProcessing = processingJobs.containsKey(recordingId)
    println("process($recordingId) - already processing: $isProcessing")
    // ... rest of logic
}
```

---

## 8. Common Errors & Fixes

| Error | Cause | Fix |
|-------|-------|-----|
| `NullPointerException` in `performTranscription()` | `transcription` is null after transcriber | Add null-check before returning |
| `ClassCastException` with `insightPort` | Wrong type in DI binding | Verify `single<InsightPort>` in DumpDataModule |
| Infinite wait in single-flight | Job never removed from map | Check finally block cleanup |
| Duplicate processing | Single-flight not working | Verify ConcurrentHashMap.putIfAbsent() logic |
| Status transitions wrong | Missing updateProcessingStatus() calls | Trace FSM, add all 4 status updates |
| Tests timeout | Mock not configured | Use `coEvery` for suspend functions |

