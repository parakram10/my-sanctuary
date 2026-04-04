# Phase G: Android WorkManager — Detailed Implementation Plan

**Date:** 2026-04-04  
**Phase:** G (Android WorkManager)  
**Status:** PLANNING  
**Scope:** Integrate WorkManager for background recording processing  
**Complexity:** HIGH — Critical infrastructure component  
**Dependencies:** Phase A, B, C, D, E, F (all must be complete)  
**Blocks:** Phase H (parallel iOS scheduler), Phase I (depends on background processing working)

---

## 1. Overview

### 1.1 Purpose
Integrate Android WorkManager to periodically process PENDING and FAILED (transient error) recordings in the background without requiring the app to be open.

### 1.2 Key Responsibilities
- Query eligible recordings from database
- Increment WorkManager attempt counter
- Call `RecordingProcessingEngine.process()` for each
- Handle retries and failures
- Respect network constraints

### 1.3 WorkManager Flow
```
WorkManager periodic timer (every 15 min)
  ↓
WorkManager checks constraints (network available?)
  ↓
Launches RecordingProcessingWorker
  ├─ Query eligibleforBackgroundRetry()
  ├─ For each recording:
  │  ├─ incrementBackgroundWmAttempts()
  │  └─ processingEngine.process()
  └─ Return Result.success() or Result.retry()
  ↓
WorkManager schedules next execution
```

---

## 2. Architecture & Design

### 2.1 Component Diagram

```
DumpViewModel (foreground)
  ├─ saveRecording() → engine.process()
  └─ scheduleRecordingProcessing(id) → scheduler.scheduleRecordingProcessing()

AndroidBackgroundWorkScheduler (implements BackgroundWorkScheduler)
  ├─ scheduleRecordingProcessing(id) → WorkManager.enqueueUniqueWork()
  └─ setup() → WorkManager.enqueueUniquePeriodicWork()

WorkManager (OS-level)
  ↓
RecordingProcessingWorker
  ├─ recordingRepository.queryEligibleForBackgroundRetry()
  ├─ recordingRepository.incrementBackgroundWmAttempts()
  └─ processingEngine.process(recordingId)
```

### 2.2 Eligibility Decision Tree

```
Is recording eligible for WM processing?

1. Status == PENDING
   → YES, process it

2. Status == FAILED
   ├─ errorCode == null
   │  → NO (shouldn't happen)
   └─ errorCode != null
      ├─ isEligibleForBackgroundRetry == true (transient)
      │  └─ background_wm_attempts < 1
      │     → YES, process it
      └─ isEligibleForBackgroundRetry == false (permanent)
         → NO (don't retry)

3. Status == COMPLETED
   → NO (already done)

4. Status == TRANSCRIBING or GENERATING_INSIGHT
   → NO (still in progress, shouldn't happen)
```

### 2.3 Attempt Counter Logic

```
Worker calls: recordingRepository.incrementBackgroundWmAttempts(recordingId)

This increments:
  background_wm_attempts: 0 → 1

Then engine processes with attempt count = 1

If error is transient and attempt >= 1:
  → Mark FAILED (don't retry locally, let next WM cycle handle)

If error is transient and attempt < 1:
  → Mark PENDING (wait for next WM cycle)
```

---

## 3. File Structure & Locations

### 3.1 New Files to Create

#### File 1: `BackgroundWorkScheduler.kt` (Domain Interface)
**Location:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/scheduling/`  
**Size:** ~40 lines  
**Purpose:** Domain port for scheduling (platform-agnostic)

#### File 2: `RecordingProcessingWorker.kt` (Android Worker)
**Location:** `feature_dump/src/androidMain/kotlin/sanctuary/app/feature/dump/platform/`  
**Size:** ~80 lines  
**Purpose:** WorkManager worker implementation

#### File 3: `AndroidBackgroundWorkScheduler.kt` (Android Scheduler)
**Location:** `feature_dump/src/androidMain/kotlin/sanctuary/app/feature/dump/platform/`  
**Size:** ~100 lines  
**Purpose:** Schedules work with WorkManager

#### File 4: `WorkManagerSetup.kt` (Optional Helper)
**Location:** `feature_dump/src/androidMain/kotlin/sanctuary/app/feature/dump/platform/`  
**Size:** ~30 lines  
**Purpose:** One-time setup function

### 3.2 Modified Files

**File:** `DumpPlatformModule.android.kt`  
**Location:** `feature_dump/src/androidMain/kotlin/sanctuary/app/feature/dump/di/`  
**Changes:**
- Import `BackgroundWorkScheduler`, `AndroidBackgroundWorkScheduler`, `RecordingProcessingWorker`
- Add factory for `RecordingProcessingWorker` (for WorkManager DI)
- Add `single<BackgroundWorkScheduler>` binding
- Add setup initialization

---

## 4. Detailed Implementation

### Step 1: Create Domain Interface

**File:** `BackgroundWorkScheduler.kt`

```kotlin
package sanctuary.app.feature.dump.domain.scheduling

/**
 * Platform-agnostic interface for scheduling background recording processing.
 *
 * Implementations:
 * - Android: WorkManager-based scheduler
 * - iOS: BGProcessingTask or foreground flush
 *
 * The domain never knows or cares how scheduling is implemented.
 */
interface BackgroundWorkScheduler {
    /**
     * Schedule a recording for background processing.
     *
     * Platform-specific implementation details (WorkManager, BGProcessingTask, etc.)
     * are hidden from the domain layer.
     *
     * @param recordingId The recording to process in the background
     */
    suspend fun scheduleRecordingProcessing(recordingId: String)

    /**
     * One-time setup: configure periodic job, constraints, etc.
     *
     * Called once during app initialization.
     * Sets up the background processing infrastructure.
     */
    suspend fun setup()
}
```

**Key Points:**
- ✅ Simple, focused interface
- ✅ Two methods: schedule one recording, setup once
- ✅ Suspend functions for async operations
- ✅ Platform-agnostic (iOS can implement differently)

---

### Step 2: Create RecordingProcessingWorker

**File:** `RecordingProcessingWorker.kt`

```kotlin
package sanctuary.app.feature.dump.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import sanctuary.app.feature.dump.data.processing.RecordingProcessingEngine
import sanctuary.app.feature.dump.domain.repository.RecordingRepository

/**
 * WorkManager worker that processes eligible recordings in the background.
 *
 * Responsibility: Query eligible recordings, increment attempt counter,
 * and delegate to the RecordingProcessingEngine (which does the heavy lifting).
 *
 * Does NOT contain business logic; that lives in RecordingProcessingEngine.
 */
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

            // STEP 2: If nothing to do, report success
            if (eligible.isEmpty()) {
                return Result.success()
            }

            // STEP 3: Process each eligible recording
            for (recording in eligible) {
                try {
                    // Increment WM attempt counter BEFORE calling engine
                    recordingRepository.incrementBackgroundWmAttempts(recording.id)

                    // Call engine (same process() as foreground)
                    // Engine updates DB status: PENDING→TRANSCRIBING→... or FAILED
                    processingEngine.process(recording.id)

                } catch (e: Exception) {
                    // Log error but continue with next recording
                    // (Don't let one failure stop processing others)
                    // In production, log to Crashlytics or similar
                }
            }

            // STEP 4: All processed successfully
            Result.success()

        } catch (e: Exception) {
            // Fatal error (e.g., can't access database)
            // Let WorkManager retry later
            Result.retry()
        }
    }
}
```

**Key Points:**
- ✅ Extends `CoroutineWorker` (async/suspend support)
- ✅ Queries eligible recordings
- ✅ Increments attempt counter BEFORE processing
- ✅ Calls engine for each recording
- ✅ Returns `Result.success()` on success
- ✅ Returns `Result.retry()` on fatal errors
- ✅ Handles individual errors without stopping loop

---

### Step 3: Create AndroidBackgroundWorkScheduler

**File:** `AndroidBackgroundWorkScheduler.kt`

```kotlin
package sanctuary.app.feature.dump.platform

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import sanctuary.app.feature.dump.domain.scheduling.BackgroundWorkScheduler

/**
 * Android WorkManager implementation of BackgroundWorkScheduler.
 *
 * Schedules recording processing using Android's WorkManager API.
 * Handles:
 * - One-time work requests (for manual retry)
 * - Periodic work (automatic background processing)
 * - Network constraints (only process when network available)
 * - Unique work names (prevent duplicate jobs)
 */
internal class AndroidBackgroundWorkScheduler(
    private val context: Context,
) : BackgroundWorkScheduler {

    override suspend fun scheduleRecordingProcessing(recordingId: String) {
        // Create unique work name per recording
        val uniqueWorkName = "process_recording_$recordingId"

        // Build one-time work request
        val workRequest = OneTimeWorkRequestBuilder<RecordingProcessingWorker>()
            .addTag("recording_processing")
            .addTag(recordingId)  // For future cancellation if needed
            .build()

        // Enqueue with unique work name + KEEP policy
        // If already queued, don't add another (KEEP existing)
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,  // Critical: prevents duplicates
            workRequest
        )
    }

    override suspend fun setup() {
        // Configure periodic job (runs automatically at interval)
        val periodicWorkRequest = PeriodicWorkRequestBuilder<RecordingProcessingWorker>(
            repeatInterval = 15,                    // Every 15 minutes
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
            flexInterval = 5,                       // Flex window: 10-15 minutes
            flexTimeUnit = TimeUnit.MINUTES
        )
            .addTag("recording_processing_periodic")
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)  // Must have internet
                    .setRequiresCharging(false)                     // Can run on battery
                    .setRequiresBatteryNotLow(false)                // Not strict
                    .build()
            )
            .build()

        // Enqueue with unique name + KEEP policy
        // If already enqueued, don't reschedule (KEEP existing)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "recording_processing_periodic",
            ExistingPeriodicWorkPolicy.KEEP,  // Critical: prevents duplicate periodic jobs
            periodicWorkRequest
        )
    }
}
```

**Key Points:**
- ✅ `scheduleRecordingProcessing()` enqueues one-time work
- ✅ Unique work name per recording prevents duplicates
- ✅ `ExistingWorkPolicy.KEEP` ensures only one job per recording
- ✅ `setup()` configures periodic job (15-minute interval)
- ✅ Network constraint enforced (CONNECTED)
- ✅ Flex interval allows WorkManager to batch jobs

---

### Step 4: Create WorkManagerSetup Helper (Optional)

**File:** `WorkManagerSetup.kt`

```kotlin
package sanctuary.app.feature.dump.platform

import android.content.Context
import sanctuary.app.feature.dump.domain.scheduling.BackgroundWorkScheduler

/**
 * Helper function to initialize WorkManager for recording processing.
 *
 * Called once during app startup to set up the periodic job.
 */
suspend fun setupRecordingProcessingWorker(context: Context) {
    val scheduler = AndroidBackgroundWorkScheduler(context)
    scheduler.setup()  // Enqueue periodic job
}
```

**Alternative:** Can be inlined into DI setup if preferred.

---

### Step 5: Update DI Module

**File:** `DumpPlatformModule.android.kt`

```kotlin
package sanctuary.app.feature.dump.di

import android.content.Context
import androidx.work.WorkerParameters
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import sanctuary.app.feature.dump.data.processing.RecordingProcessingEngine
import sanctuary.app.feature.dump.domain.repository.RecordingRepository
import sanctuary.app.feature.dump.domain.scheduling.BackgroundWorkScheduler
import sanctuary.app.feature.dump.platform.AndroidBackgroundWorkScheduler
import sanctuary.app.feature.dump.platform.RecordingProcessingWorker
import sanctuary.app.feature.dump.platform.setupRecordingProcessingWorker

val dumpPlatformModule = module {
    // ===== RECORDING PROCESSING WORKER =====
    
    // Factory for RecordingProcessingWorker
    // WorkManager will call this factory to create worker instances
    factory { (context: Context, params: WorkerParameters) ->
        RecordingProcessingWorker(
            context = context,
            params = params,
            recordingRepository = get(),          // From dumpDataModule
            processingEngine = get()              // From dumpDataModule
        )
    }

    // ===== BACKGROUND WORK SCHEDULER =====
    
    // Bind BackgroundWorkScheduler to Android implementation
    single<BackgroundWorkScheduler> {
        AndroidBackgroundWorkScheduler(context = androidContext())
    }

    // ===== ONE-TIME SETUP =====
    
    // Call setup() once at app startup
    single {
        launchSetup(get<BackgroundWorkScheduler>())
    }
}

// Helper function to safely call async setup
private fun launchSetup(scheduler: BackgroundWorkScheduler) {
    // In real app, use viewModelScope or applicationScope from Lifecycle
    // For now, fire-and-forget (WorkManager is persistent at OS level)
    GlobalScope.launch {
        scheduler.setup()
    }
}
```

**Key Points:**
- ✅ Factory for `RecordingProcessingWorker` (WorkManager creates instances)
- ✅ `single<BackgroundWorkScheduler>` binding (one instance)
- ✅ Setup called once at startup
- ✅ Dependencies injected via `get()`

---

## 5. Implementation Checklist

### Phase G Implementation Steps

#### Scaffolding (30 min)
- [ ] Create `BackgroundWorkScheduler.kt` interface
- [ ] Create `RecordingProcessingWorker.kt` skeleton with imports
- [ ] Create `AndroidBackgroundWorkScheduler.kt` skeleton
- [ ] Create `WorkManagerSetup.kt` helper
- [ ] Compile: `./gradlew :feature_dump:compileKotlinMetadata -q`

#### Implementation (2-3 hours)
- [ ] Implement `BackgroundWorkScheduler` interface
- [ ] Implement `RecordingProcessingWorker.doWork()` full logic
- [ ] Implement `AndroidBackgroundWorkScheduler.scheduleRecordingProcessing()`
- [ ] Implement `AndroidBackgroundWorkScheduler.setup()` with periodic job
- [ ] Implement `setupRecordingProcessingWorker()` helper
- [ ] Update `DumpPlatformModule.android.kt` with DI bindings
- [ ] Compile: `./gradlew :feature_dump:compileDebugKotlinAndroid -q`

#### Testing (1-2 hours)
- [ ] Write `RecordingProcessingWorkerTest.kt` (unit tests)
- [ ] Write `AndroidBackgroundWorkSchedulerTest.kt` (unit tests)
- [ ] Write `WorkManagerIntegrationTest.kt` (Robolectric tests)
- [ ] Run tests: `./gradlew :feature_dump:testDebugUnitTest -q`
- [ ] Run Robolectric: `./gradlew :feature_dump:testDebugRobolectric -q`

#### Verification (30 min)
- [ ] Compile all: `./gradlew :feature_dump:build -q`
- [ ] Manual testing (see Testing Strategy section)
- [ ] Code review checklist

---

## 6. Testing Strategy

### 6.1 Unit Tests

**File:** `feature_dump/src/androidTest/kotlin/.../platform/RecordingProcessingWorkerTest.kt`

```kotlin
@RunWith(RobolectricTestRunner::class)
class RecordingProcessingWorkerTest {
    
    private val recordingRepository = mockk<RecordingRepository>()
    private val processingEngine = mockk<RecordingProcessingEngine>()
    
    @Test
    fun `doWork() queries eligible and processes each recording`() {
        // Arrange
        val eligible = listOf(
            Recording(id = "rec-1", ...),
            Recording(id = "rec-2", ...)
        )
        coEvery { recordingRepository.queryEligibleForBackgroundRetry() } returns eligible
        coEvery { recordingRepository.incrementBackgroundWmAttempts(any()) } returns Unit
        coEvery { processingEngine.process(any()) } returns Unit
        
        // Act
        val result = runBlocking {
            val worker = RecordingProcessingWorker(context, params, recordingRepository, processingEngine)
            worker.doWork()
        }
        
        // Assert
        assertEquals(Result.success(), result)
        coVerify { recordingRepository.incrementBackgroundWmAttempts("rec-1") }
        coVerify { recordingRepository.incrementBackgroundWmAttempts("rec-2") }
        coVerify { processingEngine.process("rec-1") }
        coVerify { processingEngine.process("rec-2") }
    }
    
    @Test
    fun `doWork() returns success when no eligible recordings`() {
        coEvery { recordingRepository.queryEligibleForBackgroundRetry() } returns emptyList()
        
        val result = runBlocking {
            val worker = RecordingProcessingWorker(context, params, recordingRepository, processingEngine)
            worker.doWork()
        }
        
        assertEquals(Result.success(), result)
    }
    
    @Test
    fun `doWork() handles individual processing errors`() {
        val eligible = listOf(
            Recording(id = "rec-1", ...),
            Recording(id = "rec-2", ...)
        )
        coEvery { recordingRepository.queryEligibleForBackgroundRetry() } returns eligible
        coEvery { recordingRepository.incrementBackgroundWmAttempts(any()) } returns Unit
        coEvery { processingEngine.process("rec-1") } throws Exception("Network error")
        coEvery { processingEngine.process("rec-2") } returns Unit  // Second one succeeds
        
        val result = runBlocking {
            val worker = RecordingProcessingWorker(context, params, recordingRepository, processingEngine)
            worker.doWork()
        }
        
        // Should still return success (continued despite first error)
        assertEquals(Result.success(), result)
    }
    
    @Test
    fun `doWork() returns retry on fatal errors`() {
        coEvery { recordingRepository.queryEligibleForBackgroundRetry() } throws Exception("DB error")
        
        val result = runBlocking {
            val worker = RecordingProcessingWorker(context, params, recordingRepository, processingEngine)
            worker.doWork()
        }
        
        assertEquals(Result.retry(), result)
    }
}
```

### 6.2 Integration Tests (Robolectric)

```kotlin
@RunWith(RobolectricTestRunner::class)
class WorkManagerIntegrationTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    @Test
    fun `periodic job picks up PENDING recording and processes it`() {
        // 1. Insert PENDING recording
        val recording = Recording(
            id = "rec-123",
            status = ProcessingStatus.PENDING,
            backgroundWmAttempts = 0
        )
        recordingRepository.saveRecording(recording)
        
        // 2. Setup scheduler (enqueues periodic job)
        setupRecordingProcessingWorker(context)
        
        // 3. Simulate WorkManager trigger
        TestListenableWorker.testWorker<RecordingProcessingWorker>(
            context,
            RecordingProcessingWorker::class.java
        ).also { worker ->
            runBlocking {
                val result = worker.doWork()
                assertEquals(Result.success(), result)
            }
        }
        
        // 4. Verify recording was processed
        val updated = recordingRepository.getRecording("rec-123")
        assertNotNull(updated)
        assertEquals(ProcessingStatus.COMPLETED, updated?.processingStatus)
    }
    
    @Test
    fun `unique work prevents duplicate jobs`() {
        val scheduler = AndroidBackgroundWorkScheduler(context)
        
        // Enqueue job for rec-123
        runBlocking {
            scheduler.scheduleRecordingProcessing("rec-123")
        }
        
        // Enqueue same job again
        runBlocking {
            scheduler.scheduleRecordingProcessing("rec-123")
        }
        
        // Should still only have one job in queue
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("process_recording_rec-123")
            .get()
        
        assertEquals(1, workInfos.size)
    }
}
```

### 6.3 Manual Testing

```
MANUAL TEST PLAN:

1. Save a recording
   → Should show PENDING status in DB

2. Manually trigger WorkManager
   $ adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
   → Or use WorkManager testing library

3. Check DB
   → If network available: should be COMPLETED
   → If network unavailable: should be FAILED with NETWORK error

4. Retry scenario
   → Turn off WiFi, trigger WM
   → Recording should be FAILED
   → Turn on WiFi, trigger WM again
   → Recording should auto-process (because transient error + attempt 0)

5. Permanent error scenario
   → Set recording locale to unsupported language
   → Trigger WM
   → Should be FAILED (non-retryable)
   → Trigger WM again
   → Should still be FAILED (no retry)
```

---

## 7. Compilation & Verification

### Build Commands

```bash
# Step 1: Compile shared KMP (interfaces)
./gradlew :feature_dump:compileKotlinMetadata -q
# Expected: Clean

# Step 2: Compile Android (implementations)
./gradlew :feature_dump:compileDebugKotlinAndroid -q
# Expected: Clean

# Step 3: Run unit tests
./gradlew :feature_dump:testDebugUnitTest -q
# Expected: All tests pass

# Step 4: Run Robolectric integration tests
./gradlew :feature_dump:testDebugRobolectric -q
# Expected: All tests pass

# Step 5: Full module build
./gradlew :feature_dump:build -q
# Expected: Clean, no warnings
```

---

## 8. Configuration & Constants

### WorkManager Settings

```kotlin
// Periodic job timing
const val PERIODIC_INTERVAL_MINUTES = 15       // Every 15 min
const val FLEX_INTERVAL_MINUTES = 5            // Can be 10-15 min

// Attempt policy
const val WM_ATTEMPT_CAP = 1                   // Max 1 WM attempt

// Network requirement
const val REQUIRE_NETWORK = NetworkType.CONNECTED

// WorkManager tags
const val TAG_RECORDING_PROCESSING = "recording_processing"
const val TAG_PERIODIC = "recording_processing_periodic"

// Unique work name prefix
const val UNIQUE_WORK_PREFIX = "process_recording_"
```

---

## 9. Success Criteria

### Phase G is Complete When:

1. ✅ `BackgroundWorkScheduler` interface created in domain
2. ✅ `RecordingProcessingWorker` queries eligible recordings
3. ✅ `RecordingProcessingWorker` increments attempt counter before processing
4. ✅ `RecordingProcessingWorker` calls `processingEngine.process()` for each
5. ✅ `RecordingProcessingWorker` returns `Result.success()` on success
6. ✅ `RecordingProcessingWorker` returns `Result.retry()` on fatal errors
7. ✅ `AndroidBackgroundWorkScheduler.scheduleRecordingProcessing()` enqueues unique work
8. ✅ `AndroidBackgroundWorkScheduler.setup()` enqueues periodic job
9. ✅ Periodic job configured with 15-minute interval
10. ✅ Network constraint enforced (CONNECTED)
11. ✅ `ExistingWorkPolicy.KEEP` prevents duplicate jobs
12. ✅ DI wiring complete (factory + single + setup)
13. ✅ Unit tests written and passing (7+)
14. ✅ Robolectric integration tests passing (2+)
15. ✅ Compiles clean on metadata + Android
16. ✅ Manual testing verified

---

## 10. Risk Mitigation

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Worker takes too long | Device throttles/kills | Keep lightweight; engine does heavy lifting |
| Duplicate jobs | Data inconsistency | ExistingWorkPolicy.KEEP + engine single-flight |
| Attempt counter not incremented | Infinite loops | Increment BEFORE calling engine |
| Network constraint too strict | Never processes | Only require for insights, not transcription |
| DI misconfiguration | Runtime crash | Test worker factory early |
| SQLDelight query wrong | Misses eligible recordings | Test query in Robolectric |

---

## 11. Next Phases

### Phase H (iOS Background Scheduler)
- Implement same interface with iOS APIs
- Use BGProcessingTask or foreground flush
- Share eligibility logic

### Phase I (Presentation Layer)
- UI updates to show processing status
- Manual retry button
- Processing indicators

### Phase J (QA & Testing)
- Manual test plan
- Offline/online scenarios
- Rate limit behavior

---

## 12. Key Learnings & Notes

### Single-Flight Guarantee
Phase F's single-flight mutex prevents concurrent `process()` calls for same recording. Phase G relies on this + DB checkpoint.

### Eligibility Filter
Only eligible recordings returned by `queryEligibleForBackgroundRetry()`. Implementation is critical:
```
PENDING: always eligible
FAILED: only if transient error + attempts < 1
COMPLETED: never
```

### WorkManager Guarantees
- Executes at least once (may execute more than once)
- Persists across app restart
- Respects constraints (network, battery, etc.)
- Exponential backoff on retry

### Network Constraint
Required because insights need network. Transcription can be offline (cached).

---

## Summary Table

| Aspect | Detail |
|--------|--------|
| **Complexity** | HIGH (OS integration + concurrency) |
| **Files to Create** | 4 (interface + 3 implementations) |
| **Files to Modify** | 1 (DI module) |
| **Total Code** | ~250-300 lines |
| **Test Cases** | 6+ unit, 2+ integration |
| **Estimated Time** | 4-5 hours |
| **Blocked By** | Phase F ✅ |
| **Blocks** | Phase H, I (partial) |
| **Critical Path** | doWork() logic + DI wiring |

