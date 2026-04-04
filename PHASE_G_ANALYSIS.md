# Phase G: Android WorkManager Integration — Detailed Analysis

**Date:** 2026-04-04  
**Phase:** G (Android WorkManager)  
**Status:** ANALYSIS COMPLETE  
**Complexity:** HIGH — Glues background processing to OS-level scheduler

---

## 1. Executive Summary

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.

### Goal
Integrate WorkManager to periodically process eligible (PENDING/FAILED) recordings in the background.

### Key Components
1. **RecordingProcessingWorker** — WorkManager worker that queries eligible recordings and calls `RecordingProcessingEngine.process()`
2. **AndroidBackgroundWorkScheduler** — Implements `BackgroundWorkScheduler` interface to enqueue/schedule work
3. **WorkManagerSetup** — One-time configuration (periodic job, constraints, tags)
4. **DI Integration** — Wire into DumpPlatformModule.android.kt

### WorkManager Flow
```
WorkManager periodic trigger
  ↓
RecordingProcessingWorker.doWork()
  ├─ Query eligible recordings
  ├─ Increment WM attempt counter
  └─ For each: call RecordingProcessingEngine.process(id)
       ├─ (same FSM as foreground)
       ├─ Updates DB status
       └─ Returns
  ↓
WorkManager schedules retry if FAILURE
```

---

## 2. Architecture & Dependencies

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.


### Dependency Graph

```
RecordingProcessingWorker
├── RecordingRepository (query eligible, increment attempts)
└── RecordingProcessingEngine (same process(id) as foreground)
    ├── RecordingRepository
    ├── InsightPort
    └── OnDeviceTranscriber

AndroidBackgroundWorkScheduler (implements BackgroundWorkScheduler)
└── WorkManager enqueueUniqueWork()

WorkManagerSetup
└── WorkManager periodic job configuration
```

### Eligibility Logic (Critical)

```
A recording is eligible for WorkManager if:

1. Status == PENDING
   OR
   Status == FAILED && ErrorCode in {NETWORK, TIMEOUT, UNKNOWN_TRANSIENT}

2. AND background_wm_attempts < 1  (configurable cap)

3. AND no other WM job with same recordingId is enqueued
   (enforced by ExistingWorkPolicy.KEEP)
```

---

## 3. Files to Create/Modify

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.


### New Files (to create)

```
feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/scheduling/
├── BackgroundWorkScheduler.kt          (interface - domain port)
└── (will also be in androidMain + iosMain for implementations)

feature_dump/src/androidMain/kotlin/sanctuary/app/feature/dump/platform/
├── RecordingProcessingWorker.kt        (WorkManager Worker)
├── AndroidBackgroundWorkScheduler.kt   (implements BackgroundWorkScheduler)
└── WorkManagerSetup.kt                 (one-time setup)
```

### Modified Files

```
feature_dump/src/androidMain/kotlin/sanctuary/app/feature/dump/di/
└── DumpPlatformModule.android.kt       (wire AndroidBackgroundWorkScheduler + setup)
```

---

## 4. Detailed Design


### 4.1 Domain Interface: BackgroundWorkScheduler

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.

**Location:** `feature_dump/src/domainMain/kotlin/.../domain/scheduling/`

```kotlin
/**
 * Platform-agnostic interface for scheduling background recording processing.
 *
 * Implementations:
 * - Android: WorkManager-based scheduler
 * - iOS: BGProcessingTask or foreground flush
 */
interface BackgroundWorkScheduler {
    /**
     * Schedule a recording for background processing.
     *
     * Implementation details (WM, periodic job, etc.) are platform-specific.
     * Caller doesn't care about timing; it's the platform's job to pick it.
     *
     * @param recordingId The recording to process
     */
    suspend fun scheduleRecordingProcessing(recordingId: String)

    /**
     * One-time setup: configure periodic job, constraints, etc.
     *
     * Called once during app initialization.
     */
    suspend fun setup()
}
```

### 4.2 Android Worker: RecordingProcessingWorker

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.


**Location:** `feature_dump/src/androidMain/kotlin/.../platform/`  
**Purpose:** WorkManager worker that processes eligible recordings

```kotlin
internal class RecordingProcessingWorker(
    context: Context,
    params: WorkerParameters,
    private val recordingRepository: RecordingRepository,
    private val processingEngine: RecordingProcessingEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // STEP 1: Query eligible recordings (PENDING or FAILED with transient error)
            val eligible = recordingRepository.queryEligibleForBackgroundRetry()

            if (eligible.isEmpty()) {
                return Result.success()  // Nothing to do
            }

            // STEP 2: Process each eligible recording
            for (recording in eligible) {
                try {
                    // Increment WM attempt counter
                    recordingRepository.incrementBackgroundWmAttempts(recording.id)

                    // Call engine (same as foreground)
                    processingEngine.process(recording.id)

                    // Engine updated DB status; WM just observes
                } catch (e: Exception) {
                    // Log but continue with next recording
                }
            }

            // STEP 3: All processed; report success to WM
            Result.success()

        } catch (e: Exception) {
            // Fatal error (e.g., can't query DB); let WM retry
            Result.retry()
        }
    }
}
```

**Key Points:**
- ✅ Queries eligible recordings (`queryEligibleForBackgroundRetry()`)
- ✅ Increments attempt counter before calling engine
- ✅ Calls `processingEngine.process(id)` (same as foreground)
- ✅ Engine updates DB status, worker just observes
- ✅ Returns `Result.success()` if all processed
- ✅ Returns `Result.retry()` only on fatal errors

### 4.3 Android Scheduler: AndroidBackgroundWorkScheduler

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.


**Location:** `feature_dump/src/androidMain/kotlin/.../platform/`  
**Purpose:** Implements BackgroundWorkScheduler to enqueue WM work

```kotlin
internal class AndroidBackgroundWorkScheduler(
    private val context: Context,
) : BackgroundWorkScheduler {

    override suspend fun scheduleRecordingProcessing(recordingId: String) {
        // Enqueue a unique work request for this recording
        val uniqueWorkName = "process_recording_$recordingId"

        val workRequest = OneTimeWorkRequestBuilder<RecordingProcessingWorker>()
            .addTag("recording_processing")
            .addTag(recordingId)  // For cancellation if needed
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,  // If already queued, don't add another
            workRequest
        )
    }

    override suspend fun setup() {
        // Configure periodic job (runs even if app is closed)
        val periodicWorkRequest = PeriodicWorkRequestBuilder<RecordingProcessingWorker>(
            repeatInterval = 15,  // Every 15 minutes
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
            flexInterval = 5,     // Flex window: 10-15 minutes
            flexTimeUnit = TimeUnit.MINUTES
        )
            .addTag("recording_processing_periodic")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)  // Need network for insights
                    .setRequiresCharging(false)                     // Can run on battery
                    .setRequiresBatteryNotLow(false)                // Not strict on battery
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "recording_processing_periodic",
            ExistingPeriodicWorkPolicy.KEEP,  // Don't reschedule if already running
            periodicWorkRequest
        )
    }
}
```

**Key Points:**
- ✅ Unique work per recording (prevents duplicates)
- ✅ `ExistingWorkPolicy.KEEP` = if already queued, don't enqueue again
- ✅ Periodic job every 15 minutes (configurable)
- ✅ Network constraint (needed for insight generation)
- ✅ Called once at app startup (in setup())

### 4.4 WorkManager Setup (Optional Helper)

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.


**Location:** `feature_dump/src/androidMain/kotlin/.../platform/`  
**Purpose:** One-time initialization (can be inlined into scheduler)

```kotlin
/**
 * Initialize WorkManager and set up periodic job.
 * Called once during app startup.
 */
suspend fun setupRecordingProcessingWorker(context: Context) {
    val scheduler = AndroidBackgroundWorkScheduler(context)
    scheduler.setup()  // Enqueue periodic job
}
```

### 4.5 DI Integration

**Location:** `feature_dump/src/androidMain/kotlin/.../di/DumpPlatformModule.android.kt`

```kotlin
val dumpPlatformModule = module {
    // Wire RecordingProcessingWorker (dependency injection for worker)
    factory { (context: Context, params: WorkerParameters) ->
        RecordingProcessingWorker(
            context = context,
            params = params,
            recordingRepository = get(),
            processingEngine = get()
        )
    }

    // Wire AndroidBackgroundWorkScheduler
    single<BackgroundWorkScheduler> {
        AndroidBackgroundWorkScheduler(context = get())
    }

    // One-time setup on app startup
    single {
        applicationLifecycleScope.launch {
            setupRecordingProcessingWorker(get())
        }
    }
}
```

---

## 5. Eligibility Logic Deep-Dive

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.


### Query Implementation

```kotlin
// In RecordingRepositoryImpl
suspend fun queryEligibleForBackgroundRetry(): List<Recording> {
    return recordingLocalDataSource.queryEligibleForBackgroundRetry()
}

// In RecordingLocalDataSourceImpl (delegates to SQLDelight)
suspend fun queryEligibleForBackgroundRetry(): List<Recording> {
    return withContext(Dispatchers.IO) {
        val rows = database.recordingsQueries
            .queryEligibleForBackgroundRetry(
                status = ProcessingStatus.PENDING.name,
                failedStatus = ProcessingStatus.FAILED.name
            )
            .executeAsList()
        
        rows.map { it.toDomain() }
    }
}
```

### SQLDelight Query

```sql
-- In recordings.sq
queryEligibleForBackgroundRetry:
SELECT * FROM recordings
WHERE
  (
    -- PENDING: always eligible
    processing_status = ?
    OR
    -- FAILED: only if transient error AND not yet retried
    (
      processing_status = ?
      AND error_code IN ('NETWORK', 'TIMEOUT', 'UNKNOWN_TRANSIENT')
      AND background_wm_attempts < 1
    )
  )
AND deleted = 0
ORDER BY created_at ASC;
```

### Attempt Counter

```kotlin
// In RecordingRepositoryImpl
suspend fun incrementBackgroundWmAttempts(recordingId: String) {
    recordingLocalDataSource.incrementBackgroundWmAttempts(recordingId)
}

// In SQLDelight
incrementBackgroundWmAttempts:
UPDATE recordings
SET background_wm_attempts = background_wm_attempts + 1
WHERE id = ?;
```

---

## 6. WorkManager Constraints & Policies

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.


### Constraints

| Constraint | Value | Rationale |
|-----------|-------|-----------|
| Network | CONNECTED | Insight generation needs network |
| Charging | Not required | Can run on battery |
| Battery | Not required | Non-critical background task |
| Device idle | Not required | Can run anytime |

### Retry Policy

| Aspect | Setting | Reason |
|--------|---------|--------|
| Unique work policy | KEEP | Don't queue duplicate work for same recording |
| Periodic work policy | KEEP | Don't reschedule if already running |
| Backoff policy | Default (exponential) | WM's built-in backoff (don't override) |
| Flex interval | 5 minutes | Allow WM to batch jobs more efficiently |

---

## 7. Integration Points

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.


### When Work is Enqueued

```
1. App startup: setup() enqueues periodic job
2. DumpViewModel saves recording: scheduleRecordingProcessing(id)
3. Manual retry: user clicks "Retry" → calls scheduleRecordingProcessing(id)
```

### When Work Executes

```
1. WorkManager checks constraints (network available?)
2. Launches RecordingProcessingWorker.doWork()
3. Worker queries eligible recordings
4. For each: increment attempt counter, call processingEngine.process()
5. Engine updates DB status
6. Worker returns Result.success()
7. WorkManager schedules next periodic run
```

---

## 8. Edge Cases & Handling

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.


### Edge Case 1: Duplicate WM Jobs

**Scenario:** User manually retries a recording while WM job for same recording is enqueued

**Handling:**
```
ExistingWorkPolicy.KEEP ensures only one job per unique work name
→ Manual retry enqueues with same unique work name
→ WM skips enqueue (already in queue)
→ Job runs once, processes both manual retry intent + WM eligibility
```

### Edge Case 2: Recording Not Found

**Scenario:** WM job has recordingId, but recording was deleted

**Handling:**
```
recordingRepository.queryEligibleForBackgroundRetry() returns empty
→ Worker sees no eligible recordings
→ Returns Result.success()
```

### Edge Case 3: Network Unavailable

**Scenario:** Insight generation needs network, but device is offline

**Handling:**
```
RecordingProcessingEngine.process() classifies error as NETWORK
→ If attempt < 1: marks PENDING (will retry next WM cycle)
→ If attempt >= 1: marks FAILED (defers to future WM)

WorkManager constraints ensure only runs when network available
→ But transient errors are still possible (WiFi flaky, API timeout)
```

### Edge Case 4: WorkManager Disabled

**Scenario:** User disables background activity in Settings

**Handling:**
```
WorkManager respects device settings
→ setup() enqueues job, but it may not run if device has restrictions
→ App still works: foreground processing via DumpViewModel + manual retry
```

### Edge Case 5: Attempt Counter Race

**Scenario:** Two WM jobs for same recording execute concurrently

**Handling:**
```
RecordingProcessingEngine single-flight logic prevents concurrent process() calls
→ First WM job increments counter, calls process()
→ Second WM job increments counter, waits for first to complete
→ Both see same processed state (DB is source of truth)
```

---

## 9. Testing Strategy

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.

### Unit Tests

```kotlin
class RecordingProcessingWorkerTest {
    @Test
    fun `doWork() queries eligible, increments attempts, processes`() { ... }
    
    @Test
    fun `doWork() handles empty eligible list`() { ... }
    
    @Test
    fun `doWork() returns success even if processing throws`() { ... }
    
    @Test
    fun `doWork() returns retry on fatal errors`() { ... }
}

class AndroidBackgroundWorkSchedulerTest {
    @Test
    fun `scheduleRecordingProcessing() enqueues unique work`() { ... }
    
    @Test
    fun `setup() enqueues periodic job with constraints`() { ... }
}
```

### Robolectric Integration Tests

```kotlin
@RunWith(RobolectricTestRunner::class)
class WorkManagerIntegrationTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    @Test
    fun `periodic job picks up PENDING recording`() {
        // 1. Insert PENDING recording
        // 2. Trigger WorkManager
        // 3. Assert recording processed (status changed)
    }
    
    @Test
    fun `unique work prevents duplicate jobs`() {
        // 1. Enqueue job for rec-123
        // 2. Enqueue again for rec-123
        // 3. Assert only one job in queue
    }
}
```

### Manual Test Plan

```
1. Save a recording → should be PENDING
2. Simulate WM trigger (adb shell am service call...) → should process
3. Check DB: should be COMPLETED (if network available)
4. Save another recording, turn off WiFi, trigger WM
5. Check DB: should be FAILED with NETWORK error code
6. Turn on WiFi, trigger WM again → should auto-retry
7. Delete app data, verify WM still queued (OS-level)
```

---

## 10. Implementation Checklist

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.


### Phase G Implementation Steps

```
PRE-REQUISITES:
☑ Phase A ✅ (domain models)
☑ Phase B ✅ (database)
☑ Phase C ✅ (repositories)
☑ Phase D ✅ (transcription)
☑ Phase E ✅ (insights)
☑ Phase F ✅ (engine)

PHASE G:
☐ Step 1: Create BackgroundWorkScheduler interface (domain)
☐ Step 2: Create RecordingProcessingWorker (androidMain)
☐ Step 3: Create AndroidBackgroundWorkScheduler (androidMain)
☐ Step 4: Create WorkManagerSetup helper (androidMain)
☐ Step 5: Update DumpPlatformModule.android.kt (DI)
☐ Step 6: Wire RecordingProcessingWorker factory in DI
☐ Step 7: Verify queryEligibleForBackgroundRetry() works
☐ Step 8: Verify incrementBackgroundWmAttempts() works
☐ Step 9: Compile (metadata, Android)
☐ Step 10: Write unit tests
☐ Step 11: Write Robolectric tests
☐ Step 12: Manual testing
```

---

## 11. Compilation & Verification

### Build Commands

```bash
# Shared KMP (BackgroundWorkScheduler interface)
./gradlew :feature_dump:compileKotlinMetadata -q

# Android (RecordingProcessingWorker + AndroidBackgroundWorkScheduler)
./gradlew :feature_dump:compileDebugKotlinAndroid -q

# Run unit tests
./gradlew :feature_dump:testDebugUnitTest -q

# Robolectric tests
./gradlew :feature_dump:testDebugRobolectric -q
```

---

## 12. Configuration Constants

### Periodic Job Settings

```kotlin
val PERIODIC_INTERVAL_MINUTES = 15       // Every 15 min
val FLEX_INTERVAL_MINUTES = 5            // 10-15 min window
val WM_ATTEMPT_CAP = 1                   // Max 1 WM attempt
val NETWORK_TYPE = NetworkType.CONNECTED // Require internet
```

### WorkManager Tags

```kotlin
val TAG_RECORDING_PROCESSING = "recording_processing"
val TAG_PERIODIC = "recording_processing_periodic"
val UNIQUE_WORK_PREFIX = "process_recording_"
```

---

## 13. Dependencies & Imports

### Gradle Dependencies (already in build.gradle.kts)

```kotlin
implementation("androidx.work:work-runtime-ktx:2.8.1")  // WorkManager
```

### Key Imports

```kotlin
// Worker base class
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

// WorkManager API
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType

// Context for DI
import android.content.Context

// Coroutines
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
```

---

## 14. Success Criteria

### Phase G is Complete When:

1. ✅ `BackgroundWorkScheduler` interface created (domain)
2. ✅ `RecordingProcessingWorker` queries eligible recordings
3. ✅ `RecordingProcessingWorker` increments attempt counter
4. ✅ `RecordingProcessingWorker` calls `processingEngine.process(id)`
5. ✅ `AndroidBackgroundWorkScheduler` enqueues unique work
6. ✅ `setup()` configures periodic job with constraints
7. ✅ DI wiring complete (worker factory + scheduler single)
8. ✅ `queryEligibleForBackgroundRetry()` returns correct recordings
9. ✅ `incrementBackgroundWmAttempts()` increments counter
10. ✅ Unit tests written and passing
11. ✅ Robolectric integration tests passing
12. ✅ Compiles on shared + Android
13. ✅ Manual testing: periodic job processes PENDING recordings

---

## 15. Risks & Mitigation

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Worker takes too long | Device throttles/kills | Keep worker lightweight; engine does heavy lifting |
| Duplicate processing | Data inconsistency | ExistingWorkPolicy.KEEP + engine single-flight |
| Network constraint too strict | Never processes | Allow offline retries; network required only for insights |
| Attempt counter not incremented | Infinite loops | Increment before calling engine; persist immediately |
| DI misconfiguration | Runtime crash | Test worker injection early with unit tests |
| SQLDelight query wrong | Misses eligible recordings | Test query with real DB in Robolectric |

---

## 16. Future Enhancements

### Post-Phase G

1. **Batch Processing** — Process multiple recordings per WM job
2. **Job Scheduling API** — Use JobScheduler instead of WorkManager (more control)
3. **Battery Optimization** — Check battery level before processing intensive transcriptions
4. **User Preferences** — "Process in background?" toggle in settings
5. **Observability** — Log WM job metrics, crashes, timing

### Post-Phase H (iOS)

1. **Cross-platform** — Share eligibility logic in common domain
2. **Metrics** — Track WM success rate, average processing time
3. **Adaptive Retry** — Exponential backoff based on error history

---

## Summary Table

| Aspect | Detail |
|--------|--------|
| **Complexity** | HIGH (glues WorkManager to engine) |
| **Files to Create** | 4 (interface + 3 implementations) |
| **Files to Modify** | 1 (DI module) |
| **Lines of Code** | ~300-400 |
| **Test Cases** | 6+ unit, 2+ integration |
| **Estimated Time** | 4-5 hours (including testing) |
| **Blocked By** | Phase F ✅ |
| **Blocks** | Phase H, Phase I (partially) |

