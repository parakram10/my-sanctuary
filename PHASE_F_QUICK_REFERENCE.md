# Phase F Quick Reference

**Document:** PHASE_F_IMPLEMENTATION_PLAN.md (full details)  
**Status:** Ready to implement  
**Estimated Time:** 6-8 hours

---

## 1-Minute Summary

**Goal:** Build the **orchestration engine** that coordinates recording processing through these states:

```
PENDING → TRANSCRIBING → GENERATING_INSIGHT → COMPLETED
  ↑                              ↓
  └──────── (on transient error, attempt < 1)
            (on permanent error or attempt ≥ 1) → FAILED
```

**Key Features:**
- Single-flight mutex (prevent duplicate processing)
- Checkpoint (skip transcription if already done)
- Transient vs permanent error classification
- Auto-retry once, then defer to WorkManager

---

## File to Create

```
feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/processing/
├── RecordingProcessingEngine.kt          (interface)
```

**Implementation File:**
```
(same directory)
└── RecordingProcessingEngineImpl.kt       (impl)
```

**Modified Files:**
```
feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/di/
└── DumpDataModule.kt                     (add DI binding)
```

---

## Class Signature

```kotlin
interface RecordingProcessingEngine {
    suspend fun process(recordingId: String)
}

internal class RecordingProcessingEngineImpl(
    private val recordingRepository: RecordingRepository,
    private val insightPort: InsightPort,
    private val transcriber: OnDeviceTranscriber,
) : RecordingProcessingEngine {
    // Implement process() with FSM + single-flight + checkpoint
}
```

---

## 5 Core Concepts

### 1. Single-Flight Mutex
```
Call 1: process("rec-123") ─→ [executes]
Call 2: process("rec-123") ─→ [waits for Call 1]
Call 3: process("rec-456") ─→ [executes in parallel with Call 1]
```
**Implementation:** `ConcurrentHashMap<recordingId, Job>`

### 2. FSM (5 States)
| State | Meaning |
|-------|---------|
| PENDING | Ready to process (or retrying) |
| TRANSCRIBING | Transcription in progress |
| GENERATING_INSIGHT | Insight generation in progress |
| COMPLETED | Done, no further processing needed |
| FAILED | Error occurred, check errorCode for retry eligibility |

### 3. Checkpoint Logic
```
if (recording.transcription != null) {
    // Skip transcription, use cached result
    return recording.transcription
} else {
    // Perform transcription
    return transcriber.transcribe(filePath, locale)
}
```

### 4. Error Classification
```
TRANSIENT (retry-eligible):
├── NETWORK, TIMEOUT, UNKNOWN_TRANSIENT
└── retry strategy: attempt 0 → PENDING, attempt ≥1 → FAILED

PERMANENT (no retry):
├── RATE_LIMIT, ON_DEVICE_LANGUAGE_NOT_SUPPORTED, CORRUPT_FILE
└── retry strategy: always → FAILED
```

### 5. Retry Strategy
```
Attempt 0 + Transient Error:
└─ Mark PENDING (auto-retry on next cycle)

Attempt ≥1 + Transient Error:
└─ Mark FAILED (defer to WorkManager)

Any Error + Permanent:
└─ Mark FAILED (no retry)

Success:
└─ Mark COMPLETED
```

---

## Implementation Checklist

```
PRE-REQUISITES:
☑ Phase A ✅ (domain models + skeleton)
☑ Phase B ✅ (database schema)
☑ Phase C ✅ (repository implementations)
☑ Phase D ✅ (on-device transcription)
☑ Phase E ✅ (insight generation service)

PHASE F:
☐ Step 1: Create RecordingProcessingEngine interface
☐ Step 2: Create RecordingProcessingEngineImpl skeleton
☐ Step 3: Implement process() single-flight logic
☐ Step 4: Implement executeProcessing() FSM
☐ Step 5: Implement performTranscription() checkpoint
☐ Step 6: Implement performInsightGeneration()
☐ Step 7: Implement handleError() + classifyError()
☐ Step 8: Add Koin DI binding
☐ Step 9: Compile (metadata, Android, iOS)
☐ Step 10: Write unit tests
☐ Step 11: Verify all tests pass
```

---

## Key Methods

### `process(recordingId: String)` [PUBLIC]
**Purpose:** Entry point for processing a recording

**Logic:**
1. Check if already processing (single-flight)
2. If yes: wait for completion, return
3. If no: execute processing, clean up

### `executeProcessing(recordingId: String)` [PRIVATE]
**Purpose:** FSM pipeline

**Logic:**
1. Fetch recording
2. Validate status (skip if COMPLETED/non-retryable FAILED)
3. Update to TRANSCRIBING
4. Transcribe (with checkpoint)
5. Update transcript in DB
6. Update to GENERATING_INSIGHT
7. Generate insight
8. Update to COMPLETED
9. On error: call handleError()

### `performTranscription(recording: Recording): String?` [PRIVATE]
**Purpose:** Checkpoint-aware transcription

**Logic:**
```
if (recording.transcription != null) return it
else return transcriber.transcribe(...)
```

### `handleError(recording, error, attempt)` [PRIVATE]
**Purpose:** Classify error and decide retry strategy

**Logic:**
1. Classify error → ProcessingErrorCode
2. If permanent → FAILED
3. If transient + attempt < 1 → PENDING
4. If transient + attempt ≥ 1 → FAILED

### `classifyError(error: Exception): ProcessingErrorCode` [PRIVATE]
**Purpose:** Map exception to error code

**Logic:**
- Check error message for keywords
- Map to ProcessingErrorCode enum
- Default to UNKNOWN_TRANSIENT

---

## Critical Edge Cases

| Case | Handling |
|------|----------|
| Two calls to `process("same-id")` | Single-flight mutex: 2nd waits for 1st |
| Recording already completed | Skip all processing (early return) |
| Non-retryable FAILED | Skip all processing (early return) |
| Transcription fails, attempt 0 | Mark PENDING (will retry auto) |
| Transcription fails, attempt ≥1 | Mark FAILED (defer to WM) |
| Insight generation fails | Classify error, apply same retry logic |
| Job cancelled mid-processing | Cleanup in finally, status checkpoint in DB |
| Transcript already exists | Skip transcription (checkpoint) |

---

## DI Integration

**Add to `DumpDataModule.kt`:**
```kotlin
single<RecordingProcessingEngine> {
    RecordingProcessingEngineImpl(
        recordingRepository = get(),
        insightPort = get(),
        transcriber = get()  // From platform module
    )
}
```

**Dependencies provided by:**
- `recordingRepository`: `singleOf(::RecordingRepositoryImpl)`
- `insightPort`: `insightModule` (Phase E)
- `transcriber`: `providePlatformTranscriptionModule()`

---

## Testing (8 Core Tests)

```
1. Happy path: PENDING → COMPLETED
2. Checkpoint: skip transcription if exists
3. Transient error, attempt 0 → PENDING
4. Transient error, attempt ≥1 → FAILED
5. Permanent error → FAILED (always)
6. Single-flight: concurrent same-id calls serialize
7. Already completed: skip all processing
8. Rate limit error: mark FAILED with RATE_LIMIT code
```

---

## Compilation Commands

```bash
# After each step
./gradlew :feature_dump:compileKotlinMetadata -q

# Final verification
./gradlew :feature_dump:compileDebugKotlinAndroid -q
./gradlew :feature_dump:compileKotlinIosArm64 -q

# Run tests (after writing)
./gradlew :feature_dump:testDebugUnitTest -q
```

---

## Success Criteria

Phase F is complete when:

1. ✅ Interface created
2. ✅ Implementation covers all 5 steps
3. ✅ Single-flight prevents duplicates
4. ✅ FSM transitions correct
5. ✅ Checkpoint skips transcription
6. ✅ Errors classified (transient/permanent)
7. ✅ Retry logic correct
8. ✅ Wired into Koin
9. ✅ Unit tests pass (8+)
10. ✅ Compiles clean

---

## Common Mistakes to Avoid

❌ **DON'T:**
- Auto-retry insight generation in foreground (let WM handle)
- Remove from single-flight map before completion
- Forget to update DB status transitions
- Ignore checkpoint (transcribe every time)
- Classify all errors as transient (rate limit is permanent)
- Create nested process() calls (not supported)

✅ **DO:**
- Always clean up processingJobs in finally
- Mark PENDING for auto-retry (attempt 0, transient)
- Mark FAILED for WM retry (attempt ≥1, transient)
- Check checkpoint before calling transcriber
- Use errorCode.isEligibleForBackgroundRetry flag
- Log state transitions for debugging

---

## Next Phase

After Phase F is complete:
- ✅ Phase G (Android WorkManager) can call `engine.process(recordingId)`
- ✅ Phase H (iOS Background Scheduler) can call `engine.process(recordingId)`
- ✅ Phase I (Presentation Layer) can inject engine for retry handling

---

## References

- Full plan: `PHASE_F_IMPLEMENTATION_PLAN.md`
- Architecture: `docs/recording-pipeline-detailed-plan.md`
- Phase status: `IMPLEMENTATION.md`
- v2 roadmap: `docs/v2-implementation-roadmap.md`

