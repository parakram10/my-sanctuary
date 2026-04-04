# Phase G Quick Reference: Android WorkManager

**Document:** PHASE_G_ANALYSIS.md (full details)  
**Status:** Ready to implement  
**Estimated Time:** 4-5 hours

---

## 1-Minute Overview

**Goal:** Schedule background processing of PENDING/FAILED recordings using Android WorkManager

**Flow:**
```
WorkManager periodic trigger (every 15 min)
  ↓
RecordingProcessingWorker.doWork()
  ├─ Query eligible recordings
  ├─ For each: increment attempt counter
  └─ Call RecordingProcessingEngine.process(id)
       (same FSM as foreground)
  ↓
Update DB status (COMPLETED or FAILED)
```

**Key Constraint:** Network required (for insights)

---

## Files to Create

```
domain/scheduling/
├── BackgroundWorkScheduler.kt (interface)

androidMain/platform/
├── RecordingProcessingWorker.kt (WorkManager Worker)
├── AndroidBackgroundWorkScheduler.kt (implements interface)
└── WorkManagerSetup.kt (optional helper)
```

## File to Modify

```
androidMain/di/DumpPlatformModule.android.kt (DI binding)
```

---

## Class Signatures

### Interface (Domain)

```kotlin
interface BackgroundWorkScheduler {
    suspend fun scheduleRecordingProcessing(recordingId: String)
    suspend fun setup()  // One-time initialization
}
```

### Worker

```kotlin
internal class RecordingProcessingWorker(
    context: Context,
    params: WorkerParameters,
    private val recordingRepository: RecordingRepository,
    private val processingEngine: RecordingProcessingEngine,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { ... }
}
```

### Scheduler

```kotlin
internal class AndroidBackgroundWorkScheduler(
    private val context: Context,
) : BackgroundWorkScheduler {
    override suspend fun scheduleRecordingProcessing(recordingId: String) { ... }
    override suspend fun setup() { ... }
}
```

---

## 3 Core Concepts

### 1. Eligibility Query
```
Recording is eligible if:
  - Status == PENDING
    OR
  - Status == FAILED && ErrorCode in {NETWORK, TIMEOUT, UNKNOWN_TRANSIENT}
  AND
  - background_wm_attempts < 1
```

### 2. Unique Work Per Recording
```
WorkManager enqueues with unique work name per recording:
  "process_recording_{recordingId}"
  
ExistingWorkPolicy.KEEP prevents duplicate jobs
```

### 3. Attempt Counter
```
Worker increments before calling engine:
  recordingRepository.incrementBackgroundWmAttempts(recordingId)
  
Engine sees attempt count and decides retry eligibility
```

---

## Implementation Checklist

```
PHASE G STEPS:
☐ Create BackgroundWorkScheduler interface (domain)
☐ Create RecordingProcessingWorker (androidMain)
☐ Create AndroidBackgroundWorkScheduler (androidMain)
☐ Create WorkManagerSetup helper (androidMain)
☐ Update DI module (wire scheduler + setup)
☐ Compile (metadata, Android)
☐ Write unit tests (worker + scheduler)
☐ Write Robolectric tests (WorkManager integration)
☐ Manual testing (periodic job execution)
```

---

## Key Methods

### Worker.doWork()
```kotlin
override suspend fun doWork(): Result {
    // 1. Query eligible recordings
    val eligible = recordingRepository.queryEligibleForBackgroundRetry()
    
    // 2. For each: increment attempts, process
    for (recording in eligible) {
        recordingRepository.incrementBackgroundWmAttempts(recording.id)
        processingEngine.process(recording.id)
    }
    
    // 3. Return success (WM handles retries)
    return Result.success()
}
```

### Scheduler.setup()
```kotlin
override suspend fun setup() {
    // Configure periodic job (15-minute interval)
    val request = PeriodicWorkRequestBuilder<RecordingProcessingWorker>(
        15, TimeUnit.MINUTES
    )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()
    
    // Enqueue (prevents duplicates)
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "recording_processing_periodic",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}
```

---

## Configuration Constants

| Constant | Value | Reason |
|----------|-------|--------|
| Periodic Interval | 15 minutes | Balance responsiveness vs battery |
| Flex Window | 5 minutes | Let WM batch jobs |
| Network Constraint | CONNECTED | Need internet for insights |
| Attempt Cap | 1 | Don't overwhelm device |
| Unique Work Policy | KEEP | Prevent duplicate jobs |

---

## Edge Cases

| Case | Handling |
|------|----------|
| Recording not found | Query returns empty → worker skips |
| Network unavailable | Engine marks PENDING → retry next cycle |
| Duplicate WM jobs | ExistingWorkPolicy.KEEP → only one runs |
| Attempt counter race | Engine single-flight + DB is source of truth |
| Device offline | WorkManager constraints prevent execution |

---

## Compilation Commands

```bash
# Shared KMP (interface)
./gradlew :feature_dump:compileKotlinMetadata -q

# Android (implementations)
./gradlew :feature_dump:compileDebugKotlinAndroid -q

# Tests
./gradlew :feature_dump:testDebugUnitTest -q
./gradlew :feature_dump:testDebugRobolectric -q
```

---

## Success Criteria

Phase G is complete when:

1. ✅ Queries eligible recordings correctly
2. ✅ Increments attempt counter before processing
3. ✅ Calls `processingEngine.process()` for each
4. ✅ Enqueues unique work (no duplicates)
5. ✅ Periodic job configured (15-minute interval)
6. ✅ Network constraint enforced
7. ✅ DI wiring complete
8. ✅ Unit tests pass
9. ✅ Robolectric tests pass
10. ✅ Compiles clean (metadata + Android)

---

## Common Mistakes to Avoid

❌ **DON'T:**
- Forget to increment attempt counter (infinite loops)
- Enqueue without ExistingWorkPolicy.KEEP (duplicate jobs)
- Remove network constraint (insights need network)
- Call engine synchronously (use suspend/async)
- Ignore worker exceptions (handle and continue)

✅ **DO:**
- Increment counter before calling engine
- Use unique work name + KEEP policy
- Handle errors gracefully (return appropriate Result)
- Process each recording in a loop
- Test with real DB in Robolectric

---

## DI Wiring Example

```kotlin
val dumpPlatformModule = module {
    // Worker factory (for WorkManager)
    factory { (context: Context, params: WorkerParameters) ->
        RecordingProcessingWorker(context, params, get(), get())
    }
    
    // Scheduler implementation
    single<BackgroundWorkScheduler> {
        AndroidBackgroundWorkScheduler(get())
    }
    
    // One-time setup
    single {
        setupRecordingProcessingWorker(get())
    }
}
```

---

## Testing Structure

### Unit Tests (RecordingProcessingWorkerTest)
```
✅ doWork() queries eligible recordings
✅ doWork() increments attempts
✅ doWork() calls engine for each
✅ doWork() handles empty list
✅ doWork() returns success
```

### Integration Tests (WorkManagerIntegrationTest)
```
✅ Periodic job executes
✅ Processes PENDING recordings
✅ Unique work prevents duplicates
✅ Respects network constraint
```

---

## References

- Full analysis: `PHASE_G_ANALYSIS.md`
- Architecture: `docs/recording-pipeline-detailed-plan.md`
- Phase status: `IMPLEMENTATION.md`

---

## Next Phases

After Phase G:
- **Phase H:** iOS background scheduler (similar pattern, different API)
- **Phase I:** UI updates to show processing status
- **Phase J:** QA & manual testing

