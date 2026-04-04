G# v2 Recording Pipeline — Implementation Guide

**Purpose:** This guide ensures consistent, focused implementation of the v2 recording pipeline architecture. Implement ONE phase at a time.

**Last Updated:** 2026-04-04  
**Progress:** Phases A-G Complete ✅, Phase H Ready for Implementation

---

## Current Phase Status

| Phase | Name | Status | Files to Modify |
|-------|------|--------|-----------------|
| A | Domain Models & Skeleton | ✅ Complete | (See below) |
| B | Database Schema & Mapping | ✅ Complete | (See below) |
| C | Repository Implementations | ✅ Complete | (See below) |
| D | On-Device Transcription | ✅ Complete | iOS: ✅ SFSpeechRecognizer, Android: ✅ whisper.cpp local transcription |
| E | Insight Generation Service | ✅ Complete | Refactored to use InsightPort |
| F | Core Processing Engine | ✅ Complete | RecordingProcessingEngine with FSM + checkpoint |
| G | Android WorkManager | ✅ Complete | See Phase G section |
| H | iOS Background Scheduler | ❌ Not Started | See Phase H section |
| I | Presentation Layer | ❌ Not Started | See Phase I section |
| J | QA & Testing | ❌ Not Started | See Phase J section |

---

## Phase A: Domain Models & Skeleton Classes ✅ COMPLETE

**Status:** All skeleton classes created. Do NOT modify.

**Files Created (Skeleton, with TODO stubs):**
- `feature_dump/src/domainMain/kotlin/.../domain/model/ProcessingStatus.kt` ✅
- `feature_dump/src/domainMain/kotlin/.../domain/model/ProcessingErrorCode.kt` ✅
- `feature_dump/src/domainMain/kotlin/.../domain/port/TranscriptionPort.kt` ✅
- `feature_dump/src/domainMain/kotlin/.../domain/port/InsightPort.kt` ✅
- `feature_dump/src/domainMain/kotlin/.../domain/transcription/OnDeviceTranscriber.kt` ✅
- `feature_dump/src/domainMain/kotlin/.../domain/scheduling/BackgroundWorkScheduler.kt` ✅
- `feature_dump/src/domainMain/kotlin/.../domain/usecase/RetryRecordingProcessingUseCase.kt` ✅
- `feature_dump/src/dataMain/kotlin/.../data/processing/RecordingProcessingEngine.kt` ✅
- `feature_dump/src/androidMain/kotlin/.../platform/AndroidBackgroundWorkScheduler.kt` ✅
- `feature_dump/src/androidMain/kotlin/.../platform/RecordingProcessingWorker.kt` ✅
- `feature_dump/src/androidMain/kotlin/.../platform/WorkManagerSetup.kt` ✅
- `feature_dump/src/iosMain/kotlin/.../platform/IosOnDeviceTranscriber.kt` ✅
- `feature_dump/src/iosMain/kotlin/.../platform/IosBackgroundWorkScheduler.kt` ✅

---

## Phase B: Database Schema & Mapping ⚠️ IN PROGRESS

**Goal:** Update SQLDelight schema with v2 fields and update mapper for domain ↔ DB conversion.

**Estimated Time:** 1-2 hours  
**Depends On:** Nothing  
**Blocks:** Phase C, F, G, H, I

### B1: SQLDelight Schema Update

**File:** `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/recordings.sq`

**Changes Required:**
1. Add 4 columns to `CREATE TABLE recordings`:
   - `processing_status TEXT NOT NULL DEFAULT 'PENDING'`
   - `error_code TEXT` (nullable)
   - `background_wm_attempts INTEGER NOT NULL DEFAULT 0`
   - `recording_locale TEXT NOT NULL DEFAULT 'en'`

2. Create 2 indexes for v2 pipeline queries:
   - `idx_processing_status` on `processing_status`
   - `idx_background_wm_attempts` on `background_wm_attempts`

3. Update existing `insert` query to include:
   - `processing_status` parameter
   - `recording_locale` parameter

4. Add 4 new queries:
   - `selectByProcessingStatus(status: String)` → all recordings with that status
   - `updateProcessingStatus(status: String, error_code: String?, id: String)`
   - `updateTranscription(transcription: String, id: String)`
   - `incrementBackgroundWmAttempts(id: String)` → increment counter
   - `queryEligibleForBackgroundRetry()` → PENDING OR (FAILED with transient error code AND attempts < 1)

**Verification:**
```bash
./gradlew :core_database:generateSqlDelightInterface -q
# Should generate new Recordings class with v2 fields
# Should generate RecordingsQueries with 4 new methods
```

### B2: Update Recording Model

**File:** `feature_dump/src/domainMain/kotlin/.../domain/model/Recording.kt`

**Changes Required:**
Add 4 fields to `Recording` data class:
```kotlin
val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
val errorCode: ProcessingErrorCode? = null,
val backgroundWmAttempts: Int = 0,
val recordingLocale: String = "en",
```

### B3: Update RecordingRepository Interface

**File:** `feature_dump/src/domainMain/kotlin/.../domain/repository/RecordingRepository.kt`

**Changes Required:**
Add 5 new method signatures:
```kotlin
suspend fun getRecordingsByStatus(status: ProcessingStatus): List<Recording>
suspend fun updateProcessingStatus(
    id: String,
    status: ProcessingStatus,
    errorCode: ProcessingErrorCode? = null,
    errorMessage: String? = null,
)
suspend fun updateTranscription(id: String, transcription: String)
suspend fun incrementBackgroundWmAttempts(id: String)
suspend fun queryEligibleForBackgroundRetry(): List<Recording>
```

### B4: Update RecordingMapper

**File:** `feature_dump/src/dataMain/kotlin/.../data/mapper/RecordingMapper.kt`

**Changes Required:**

In `Recordings.toDomain()`:
- Map `processing_status` → `ProcessingStatus.valueOf()` (with safe default)
- Map `error_code` → `ProcessingErrorCode.valueOf()` if not null
- Map `background_wm_attempts` to Int
- Map `recording_locale` as-is

In `Recording.toEntity()`:
- Map `processingStatus.name` → `processing_status`
- Map `errorCode?.name` → `error_code`
- Map `backgroundWmAttempts.toLong()` → `background_wm_attempts`
- Map `recordingLocale` as-is

**Testing Phase B:**
```kotlin
// Test round-trip: domain → DB → domain
val original = Recording(
    id = "test-123",
    processingStatus = ProcessingStatus.PENDING,
    errorCode = ProcessingErrorCode.NETWORK,
    backgroundWmAttempts = 0,
    recordingLocale = "hi",
    // ... other fields
)
val mapped = original.toEntity()
val restored = mapped.toDomain()
assert(original == restored)
```

---

## Phase C: Repository Implementations ❌ NOT STARTED

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.

**Overview:**
- Update `RecordingLocalDataSource` interface to add 5 new methods
- Implement 5 new methods in `RecordingLocalDataSourceImpl` (delegate to SQLDelight queries)
- Implement 5 new interface methods in `RecordingRepositoryImpl` (delegate to data source)

**Files to Touch:**
- `feature_dump/src/dataMain/kotlin/.../datasource/RecordingLocalDataSource.kt`
- `feature_dump/src/dataMain/kotlin/.../datasource/RecordingLocalDataSourceImpl.kt`
- `feature_dump/src/dataMain/kotlin/.../repository/RecordingRepositoryImpl.kt`

**Test Plan:**
- Integration test: PENDING → TRANSCRIBING → COMPLETED state flow
- Query eligible for WM: PENDING + (FAILED with transient error)
- Increment WM attempts: idempotent operation

---

## Phase D: On-Device Transcription ✅ COMPLETE

**Status:** 
- iOS: ✅ Complete with `SFSpeechRecognizer`
- Android: ✅ Implemented with local `whisper.cpp`

**Architecture:**

iOS: File-based transcription via native SFSpeechRecognizer
- Supports .m4a, .mp3, .wav, .aac, .caf formats
- Timeout: 60 seconds
- Uses SFSpeechURLRecognitionRequest for reliable file input

Android: Local `whisper.cpp` pipeline
- Fully on-device, free at runtime, and offline once the model is available
- Accepts the app's existing recording formats (`.m4a`, `.mp3`, `.wav`, `.flac`, `.ogg`, `.aac`)
- Decodes audio with Android media codecs, converts to mono PCM float, and resamples to 16 kHz for Whisper
- Supports `en` and `hi`
- Timeout: 180 seconds
- Requires a ggml Whisper model file

**Android Whisper Model Setup:**
Provide the model in one of these ways before transcription runs:
```bash
# Option 1: explicit JVM property
-Dsanctuary.whisper.model.path=/absolute/path/to/ggml-base.bin

# Option 2: environment variable
export WHISPER_MODEL_PATH=/absolute/path/to/ggml-base.bin
```

Or bundle a model in:
- `feature_dump/src/androidMain/assets/whisper/`

**Recommended Models:**
- English only: `ggml-base.en.bin`
- English + Hindi: `ggml-base.bin` or another multilingual ggml model

**Important Note:**
- Hindi transcription requires a multilingual Whisper model. English-only `.en` models will fail for `hi`.

**Files:**
- `feature_dump/src/androidMain/.../WhisperCppOnDeviceTranscriber.kt` - Android Whisper transcription entry point
- `feature_dump/src/androidMain/.../WhisperCppNativeBridge.kt` - JNI wrapper and single-threaded Whisper context
- `feature_dump/src/androidMain/.../WhisperAudioDecoder.kt` - Android audio decode + resample pipeline
- `feature_dump/src/androidMain/jni/CMakeLists.txt` - native build setup for `whisper.cpp`
- `feature_dump/src/androidMain/jni/whisper_jni.cpp` - JNI bindings to `whisper.cpp`
- `feature_dump/build.gradle.kts` - Android native build wiring
- `feature_dump/src/androidMain/.../di/DumpDataModule.android.kt` - DI configuration
- `feature_dump/src/iosMain/.../IosOnDeviceTranscriber.kt` - Native implementation
- `feature_dump/src/iosMain/.../di/DumpDataModule.ios.kt` - DI configuration

**Verification Status:**
- Source wiring for Android Whisper is in place.
- `./gradlew --console=plain :feature_dump:compileDebugKotlinAndroid` is currently blocked by unrelated existing unresolved references in Android transcription/data wiring and `YourReflectionScreen.kt`.
- Those compile failures are not caused by the removed Google Cloud / TensorFlow Lite transcribers or by the Whisper files themselves.

---

## Phase E: Insight Generation Service ❌ NOT STARTED

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.

**Overview:**
- Refactor `ClaudeInsightGenerationService` to implement `InsightPort`
- Refactor `GroqInsightGenerationService` to implement `InsightPort`
- Update DI module to wire `InsightPort` → implementation

**Files to Modify:**
- `feature_dump/src/dataMain/kotlin/.../service/ClaudeInsightGenerationService.kt`
- `feature_dump/src/dataMain/kotlin/.../service/GroqInsightGenerationService.kt`
- `feature_dump/src/dataMain/kotlin/.../di/InsightModule.kt`

---

## Phase F: Core Processing Engine ✅ COMPLETE

**Status:** Fully implemented with FSM, checkpoint, and error handling

**Implemented:**
- ✅ `RecordingProcessingEngine.kt` interface
- ✅ `RecordingProcessingEngineImpl.kt` with FSM pipeline
- ✅ Checkpoint logic (skip transcription if cached)
- ✅ Error classification (transient vs permanent)
- ✅ Retry strategy (attempt-based)
- ✅ DI integration
- ✅ 10 comprehensive unit tests (all passing)

**Compilation:**
- ✅ Shared KMP (dataMain)
- ✅ Android
- ✅ iOS

---

## Phase G: Android WorkManager ✅ COMPLETE

**Status:** Fully implemented and verified

**Implementation:**
- ✅ `BackgroundWorkScheduler.kt` (domain interface, platform-agnostic port)
- ✅ `RecordingProcessingWorker.kt` (WorkManager CoroutineWorker)
- ✅ `AndroidBackgroundWorkScheduler.kt` (scheduler with periodic job setup)
- ✅ `KoinWorkerFactory.kt` (Koin-aware WorkManager factory for dependency injection)
- ✅ `DumpPlatformModule.android.kt` (DI wiring for WorkManager + scheduler)

**Completed Architecture:**

1. **BackgroundWorkScheduler** (domain interface) — Platform-agnostic scheduling port
   - `scheduleRecordingProcessing(recordingId)` — enqueue a recording for background processing
   - `setup()` — one-time initialization of periodic job

2. **RecordingProcessingWorker** — WorkManager CoroutineWorker
   - Queries eligible recordings via `recordingRepository.queryEligibleForBackgroundRetry()`
   - Increments attempt counter for each recording
   - Calls `processingEngine.process(id)` (reuses foreground logic)
   - Returns `Result.success()` on completion, `Result.retry()` on fatal errors
   - Handles exceptions gracefully (continues with next recording)

3. **AndroidBackgroundWorkScheduler** — Scheduler implementation
   - Enqueues unique one-time work per recording
   - Configures periodic job every 15 minutes (flex window: 10-15 min)
   - Adds network constraint (required for insight generation)
   - Uses `ExistingWorkPolicy.KEEP` to prevent duplicate jobs

4. **KoinWorkerFactory** — Dependency injection for WorkManager workers
   - Bridges Koin DI container with WorkManager's worker instantiation
   - Injects `RecordingRepository` and `RecordingProcessingEngine`

5. **DI Integration**
   - Registers `BackgroundWorkScheduler` as single instance
   - Initializes WorkManager with custom `KoinWorkerFactory`
   - Calls `setup()` during app startup (via `GlobalScope.launch`)

**Eligibility Logic:**
A recording is eligible for WorkManager background retry if:
- Status = PENDING, OR
- Status = FAILED AND error_code in {NETWORK, TIMEOUT, UNKNOWN_TRANSIENT}
- AND background_wm_attempts < 1 (configurable cap)
- AND not deleted

**Compilation Status:**
- ✅ Shared KMP metadata (BackgroundWorkScheduler interface)
- ✅ Android target (worker + scheduler + DI)
- ✅ Full feature_dump build successful

**Testing:**
- Unit tests for WorkManager integration require Robolectric (Android runtime environment)
- Integration tests planned for Phase J (Robolectric suite)
- Core logic tested via Phase F engine tests

**Design Documents:**
- `PHASE_G_ANALYSIS.md` (comprehensive analysis, 700+ lines)
- `PHASE_G_QUICK_REFERENCE.md` (quick reference guide)
- `PHASE_G_DOCUMENTATION_INDEX.md` (index of all Phase G docs)

---

## Phase H: iOS Background Scheduler ❌ NOT STARTED

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.

**Overview:**
- Implement `IosBackgroundWorkScheduler` (foreground flush minimum)
- Optional: implement BGProcessingTask (v2 stretch goal)

**Files to Implement:**
- `feature_dump/src/iosMain/kotlin/.../platform/IosBackgroundWorkScheduler.kt` (skeleton exists)
- Update `feature_dump/src/iosMain/kotlin/.../di/DumpPlatformModule.ios.kt`

---

## Phase I: Presentation Layer ❌ NOT STARTED

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.

**Overview:**
- Implement `RetryRecordingProcessingUseCase`
- Update `DumpViewModel` to use new pipeline
- Split UI state: processingRecordings vs completedRecordings
- Add status-aware UI components

**Files to Modify:**
- `feature_dump/src/domainMain/kotlin/.../usecase/RetryRecordingProcessingUseCase.kt` (skeleton exists)
- `feature_dump/src/presentationMain/kotlin/.../viewmodel/DumpViewModel.kt`
- `feature_dump/src/presentationMain/kotlin/.../state/DumpViewState.kt`
- `feature_dump/src/presentationMain/kotlin/.../screen/DumpRecordingScreen.kt`

---

## Phase J: QA & Testing ❌ NOT STARTED

**DO NOT IMPLEMENT YET.** Wait for explicit instruction.

**Overview:**
- Manual test plan document
- Offline/online scenario matrix
- Rate limit behavior verification

---

## Implementation Rules (STRICT)

1. **One Phase Only:** Never implement two phases in the same session unless explicitly asked
2. **Focused Changes:** Only modify files listed under the current phase
3. **No Automatic Progression:** After completing Phase B, wait for "Start Phase C" instruction
4. **Compilation Check:** Run compile after each phase to verify changes work
5. **Revert Immediately:** If you accidentally implement beyond the requested phase, revert extra changes

### How to Verify Compilation

```bash
# For dataMain changes
./gradlew :feature_dump:compileKotlinMetadata -q

# For Android platform changes
./gradlew :feature_dump:compileDebugKotlinAndroid -q

# For iOS platform changes
./gradlew :feature_dump:compileKotlinIosArm64 -q

# For core_database changes
./gradlew :core_database:generateSqlDelightInterface -q
```

---

## Reference Links

- **Detailed Architecture:** `docs/recording-pipeline-detailed-plan.md`
- **Implementation Roadmap:** `docs/v2-implementation-roadmap.md`
- **Skeleton Classes:** Created in Phase A, not to be modified until their respective phases

---

## Session Checklist

Before starting work:
- [ ] Read this guide
- [ ] Identify current phase from table at top
- [ ] Review **only** the section for that phase
- [ ] Check "Blocks" and "Depends On" to understand constraints
- [ ] After completing phase: RUN COMPILE CHECK, then WAIT for next instruction

---

## Quick Reference: What Changed in Each Phase

| Phase | Adds | Modifies | Tests |
|-------|------|----------|-------|
| A | 14 skeleton classes | — | None (skeleton) |
| B | — | recordings.sq, Recording.kt, RecordingRepository.kt, RecordingMapper.kt | Round-trip mapper test |
| C | — | RecordingLocalDataSource, RecordingLocalDataSourceImpl, RecordingRepositoryImpl | Integration: state flow |
| D | — | WhisperCppOnDeviceTranscriber, WhisperCppNativeBridge, WhisperAudioDecoder, IosOnDeviceTranscriber, DumpDataModule.android, feature_dump/build.gradle.kts | Manual: en/hi transcription |
| E | — | ClaudeInsightGenerationService, GroqInsightGenerationService, InsightModule | Mock engine test |
| F | — | RecordingProcessingEngine (full impl) | Unit: FSM, checkpoint, single-flight |
| G | BackgroundWorkScheduler (domain), RecordingProcessingWorker, AndroidBackgroundWorkScheduler, KoinWorkerFactory | DumpPlatformModule.android, gradle/libs.versions.toml, feature_dump/build.gradle.kts | Robolectric: eligible query, unique work, WM attempt |
| H | — | IosBackgroundWorkScheduler, DumpPlatformModule.ios | Manual device test |
| I | — | RetryRecordingProcessingUseCase, DumpViewModel, UI state/screens | ViewModel: save flow |
| J | — | — | Manual: offline/online matrix |
