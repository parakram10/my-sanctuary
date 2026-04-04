# Async Background Processing Pipeline — Implementation Plan

## Context

The current recording pipeline is fully blocking: after the user stops recording, the app polls a broken native SpeechRecognizer for 10 seconds, then calls the AI insight API, all inside `viewModelScope` on the main thread. The UI is frozen during this time, and the native speech recognizer never fires `onResults()`. The goal is to replace this with a true async background pipeline where:

- Recording saves to DB immediately with status `PENDING`
- A `RecordingProcessingManager` (ApplicationScope singleton) drives the pipeline: transcribe via Groq Whisper API → generate insight
- The UI observes DB state via `Flow<List<Recording>>` and shows a "Processing" section at the top while jobs are active
- Native SpeechRecognizer is deleted entirely; Groq Whisper handles transcription (same API key, server-side)

User decisions already made:
- **Job lifetime**: App process only (ApplicationScope coroutines, not WorkManager)
- **Transcription**: Groq Whisper API
- **Progress UI**: Dedicated "Processing" section at top of recordings list
- **Failure**: Auto-retry once, then show manual Retry button on the recording card

---

## Architecture Overview

```
[User stops recording]
         │
         ▼
DumpViewModel.handleStopRecording()
  → save Recording to DB (status=PENDING)
  → processingManager.enqueue(recordingId)    ← returns immediately
         │
         ▼ (ApplicationScope, background)
RecordingProcessingManager
  → launches RecordingProcessingOrchestrator.process(id)
         │
         ├─ PENDING → TRANSCRIBING
         │      GroqWhisperTranscriptionService.transcribe(filePath)
         │      updateTranscription(id, text)
         │
         ├─ TRANSCRIBING → GENERATING_INSIGHT
         │      InsightRepository.generateInsight(recordingId, transcript)
         │
         └─ GENERATING_INSIGHT → COMPLETED (or FAILED)
                     │
                     ▼
              DB update triggers Flow<List<Recording>>
                     │
                     ▼
         DumpViewModel.loadRecordings() (already running)
              → splits recordings into processingRecordings / completedRecordings
              → UI updates automatically
```

**New classes (in `dataMain`):**
- `GroqWhisperTranscriptionService` — calls Groq Whisper API
- `RecordingProcessingOrchestrator` — pure pipeline logic, one recording at a time
- `RecordingProcessingManager` — ApplicationScope singleton, enqueues + drives jobs

**New domain:**
- `ProcessingStatus` enum: `PENDING, TRANSCRIBING, GENERATING_INSIGHT, COMPLETED, FAILED`
- `AudioFileReader` interface + platform actuals (reads .m4a bytes for HTTP upload)

**Deleted:**
- `AndroidSpeechRecognitionManager`, `AndroidTranscriptionDataSource`, `AndroidTranscriptionRepositoryImpl`
- `IosSpeechRecognitionManager`, `IosTranscriptionDataSource`, `IosTranscriptionRepositoryImpl`
- `TranscriptionRepository` interface
- `InsightGenerationScreen` (blocking spinner screen no longer needed)

---

## Phase 1 — DB Schema: Add processing columns to `recordings`

**File:** `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/recordings.sq`

Add three columns to `CREATE TABLE recordings`:
```sql
processing_status TEXT NOT NULL DEFAULT 'PENDING',
retry_count INTEGER NOT NULL DEFAULT 0,
error_message TEXT
```

Update `insert` query to include `processing_status`:
```sql
insert:
INSERT INTO recordings (id, user_id, file_path, duration_ms, created_at, title, transcription, processing_status)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);
```

Add four new queries:
```sql
selectByProcessingStatus:
SELECT * FROM recordings WHERE processing_status = ? ORDER BY created_at ASC;

updateProcessingStatus:
UPDATE recordings SET processing_status = ?, error_message = ? WHERE id = ?;

updateTranscription:
UPDATE recordings SET transcription = ? WHERE id = ?;

incrementRetryCount:
UPDATE recordings SET retry_count = retry_count + 1 WHERE id = ?;
```

> **Note on migrations**: SQLDelight migrations are not configured in this project (no `schemaVersion` in `core_database/build.gradle.kts`, `SanctuaryDatabase(driver)` has no `Schema.migrate` call). During development, clear app data (uninstall/reinstall) after this schema change. When moving toward production, set `schemaVersion = 2` in the SQLDelight config and create a migrations directory.

**Verify:** Run `./gradlew :core_database:generateSqlDelightInterface`. Check that `build/generated/.../Recordings.kt` has the three new fields and `RecordingsQueries.kt` has the four new query methods.

---

## Phase 2 — Domain Models: `ProcessingStatus` + updated `Recording`

### Create `ProcessingStatus`
**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/model/ProcessingStatus.kt`
```kotlin
enum class ProcessingStatus { PENDING, TRANSCRIBING, GENERATING_INSIGHT, COMPLETED, FAILED }
```

### Update `Recording`
**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/model/Recording.kt`

Add fields:
```kotlin
val processingStatus: ProcessingStatus = ProcessingStatus.PENDING,
val retryCount: Int = 0,
val errorMessage: String? = null,
```

### Update `RecordingRepository` interface
**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/repository/RecordingRepository.kt`

Add:
```kotlin
suspend fun getRecordingsByStatus(status: ProcessingStatus): List<Recording>
suspend fun updateProcessingStatus(id: String, status: ProcessingStatus, errorMessage: String? = null)
suspend fun updateTranscription(id: String, transcription: String)
suspend fun incrementRetryCount(id: String)
```

### Delete `TranscriptionRepository`
**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/repository/TranscriptionRepository.kt` — DELETE

> The project will not compile cleanly until Phase 3 removes all references to it.

---

## Phase 3 — Remove Native Speech Recognition

### Files to delete:
- `feature_dump/src/androidMain/.../platform/AndroidSpeechRecognitionManager.kt`
- `feature_dump/src/androidMain/.../data/datasource/AndroidTranscriptionDataSource.kt`
- `feature_dump/src/androidMain/.../data/repository/AndroidTranscriptionRepositoryImpl.kt`
- `feature_dump/src/iosMain/.../platform/IosSpeechRecognitionManager.kt`
- `feature_dump/src/iosMain/.../data/datasource/IosTranscriptionDataSource.kt`
- `feature_dump/src/iosMain/.../data/repository/IosTranscriptionRepositoryImpl.kt`

### Simplify `AndroidAudioRecorder`
**File:** `feature_dump/src/androidMain/.../platform/AndroidAudioRecorder.kt`

Remove `speechRecognitionManager` from constructor. Delegate only to `AndroidMediaRecordingManager`.

### Simplify `IosAudioRecorder`
**File:** `feature_dump/src/iosMain/.../platform/IosAudioRecorder.kt`

Same — remove `IosSpeechRecognitionManager` from constructor.

### Stub out `expect/actual` platform transcription modules
**Files:**
- `feature_dump/src/androidMain/.../data/di/DumpDataModule.android.kt`
- `feature_dump/src/iosMain/.../data/di/DumpDataModule.ios.kt`

Change both `actual` implementations to: `internal actual fun providePlatformTranscriptionModule(): Module = module { }`

### Fix platform DI modules
**Files:**
- `feature_dump/src/androidMain/.../di/DumpPlatformModule.android.kt` — remove `AndroidSpeechRecognitionManager` singleton; fix `AndroidAudioRecorder(get())` (was `get(), get()`)
- `feature_dump/src/iosMain/.../di/DumpPlatformModule.ios.kt` — same for iOS

**Verify:** Project compiles and runs. Recording starts and stops. The app saves recordings (without transcription, expected at this point).

---

## Phase 4 — DB Layer: Mapper + DataSource + Repository

### Update `RecordingMapper`
**File:** `feature_dump/src/dataMain/.../data/mapper/RecordingMapper.kt`

Map the three new columns in both `Recordings.toDomain()` and `Recording.toEntity()`. Use `ProcessingStatus.valueOf(processing_status)` for DB → domain, `.name` for domain → DB.

### Update `RecordingLocalDataSource` interface
**File:** `feature_dump/src/dataMain/.../data/datasource/RecordingLocalDataSource.kt`

Add the four new method signatures matching `RecordingRepository` additions from Phase 2.

### Update `RecordingLocalDataSourceImpl`
**File:** `feature_dump/src/dataMain/.../data/datasource/RecordingLocalDataSourceImpl.kt`

- Update `insertRecording` to pass `processing_status` parameter
- Implement the four new methods using the new SQLDelight queries

### Update `RecordingRepositoryImpl`
**File:** `feature_dump/src/dataMain/.../data/repository/RecordingRepositoryImpl.kt`

Implement the four new `RecordingRepository` interface methods by delegating to `localDataSource`.

**Verify:** DB round-trip test: insert a recording with `PENDING`, call `updateProcessingStatus(id, TRANSCRIBING)`, fetch back and assert status changed.

---

## Phase 5 — Groq Whisper Transcription Service

### `AudioFileReader` interface + platform actuals
**Files to create:**
- `feature_dump/src/domainMain/.../domain/audio/AudioFileReader.kt` — interface with `fun readBytes(filePath: String): ByteArray`
- `feature_dump/src/androidMain/.../platform/AndroidAudioFileReader.kt` — `java.io.File(filePath).readBytes()`
- `feature_dump/src/iosMain/.../platform/IosAudioFileReader.kt` — `NSData.dataWithContentsOfFile(filePath)` → `ByteArray`

Register in platform DI modules:
- `DumpPlatformModule.android.kt`: `single<AudioFileReader> { AndroidAudioFileReader() }`
- `DumpPlatformModule.ios.kt`: `single<AudioFileReader> { IosAudioFileReader() }`

### `TranscriptionService` interface
**File:** `feature_dump/src/domainMain/.../domain/service/TranscriptionService.kt`
```kotlin
interface TranscriptionService {
    suspend fun transcribe(filePath: String): String  // throws on failure
}
```

### `GroqWhisperTranscriptionService`
**File:** `feature_dump/src/dataMain/.../data/service/GroqWhisperTranscriptionService.kt`

Constructor: `(httpClient: HttpClient, apiKey: String, fileReader: AudioFileReader)`

Logic:
1. `fileReader.readBytes(filePath)` → `ByteArray`
2. Ktor `httpClient.post("https://api.groq.com/openai/v1/audio/transcriptions")` with:
   - Header `Authorization: Bearer $apiKey`
   - Multipart body: `file` (audio bytes, filename `audio.m4a`, content-type `audio/m4a`), `model` = `whisper-large-v3-turbo`, `response_format` = `text`
3. Return `response.bodyAsText()` as the transcript
4. Throw descriptive `Exception` on any HTTP error

### Wire in DI
**File:** `feature_dump/src/dataMain/.../data/di/InsightModule.kt`

Add: `single<TranscriptionService> { GroqWhisperTranscriptionService(get(), get(named("groqApiKey")), get()) }`

**Verify:** Integration test — hardcode a file path from a real recording, call `GroqWhisperTranscriptionService.transcribe(path)` in a coroutine, print the result. Should get real text from Groq Whisper.

---

## Phase 6 — `RecordingProcessingOrchestrator`

**File:** `feature_dump/src/dataMain/.../data/processing/RecordingProcessingOrchestrator.kt`

```kotlin
internal class RecordingProcessingOrchestrator(
    private val recordingRepository: RecordingRepository,
    private val transcriptionService: TranscriptionService,
    private val insightRepository: InsightRepository,
) {
    sealed interface ProcessingResult {
        data object Completed : ProcessingResult
        data class Failed(val reason: String) : ProcessingResult
    }

    suspend fun process(recordingId: String): ProcessingResult
}
```

`process()` logic:
1. Fetch recording → `Failed("not found")` if null
2. `updateProcessingStatus(id, TRANSCRIBING)`
3. Try `transcriptionService.transcribe(filePath)` → on success: `updateTranscription` + advance to `GENERATING_INSIGHT`; on failure: call `handleStageFailure`
4. Try `insightRepository.generateInsight(recordingId, transcript)`:
   - `Success` → `updateProcessingStatus(id, COMPLETED)` → return `Completed`
   - `RateLimitExceeded` → `updateProcessingStatus(id, FAILED, "Rate limit reached")` → return `Failed`
   - `Failure` → call `handleStageFailure`

`handleStageFailure(id, errorMsg, retryCount)` private method:
- If `retryCount == 0`: `incrementRetryCount(id)` + `updateProcessingStatus(id, PENDING)` → return `Failed("retry_scheduled")`
- If `retryCount >= 1`: `updateProcessingStatus(id, FAILED, errorMsg)` → return `Failed(errorMsg)`

> The `"retry_scheduled"` sentinel is the manager's cue to re-enqueue immediately.

**No DI yet** — wired in Phase 7.

**Verify:** Unit test with fake `RecordingRepository`, `TranscriptionService`, `InsightRepository`:
- Happy path → `Completed`
- Transcription failure, retry_count=0 → `Failed("retry_scheduled")`, status reset to PENDING
- Transcription failure, retry_count=1 → `Failed(...)`, status=FAILED
- Rate limit → `Failed("Rate limit reached")`, status=FAILED

---

## Phase 7 — `RecordingProcessingManager`

**File:** `feature_dump/src/dataMain/.../data/processing/RecordingProcessingManager.kt`

```kotlin
class RecordingProcessingManager(
    private val orchestrator: RecordingProcessingOrchestrator,
    private val recordingRepository: RecordingRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start()                       // called at startup — recovers in-flight jobs
    fun enqueue(recordingId: String)  // called by ViewModel after save
}
```

`start()`: queries `PENDING`, `TRANSCRIBING`, `GENERATING_INSIGHT` recordings and calls `launchJob` for each.

`enqueue(id)`: calls `launchJob(id)`.

`launchJob(id)` (private):
```kotlin
scope.launch {
    val result = orchestrator.process(id)
    if (result is Failed && result.reason == "retry_scheduled") launchJob(id)
}
```

### Wire in DI
**File:** `feature_dump/src/dataMain/.../data/di/DumpDataModule.kt`

```kotlin
val dumpDataModule = module {
    singleOf(::RecordingLocalDataSourceImpl) bind RecordingLocalDataSource::class
    singleOf(::RecordingRepositoryImpl) bind RecordingRepository::class
    includes(insightModule)

    singleOf(::RecordingProcessingOrchestrator)
    single(createdAtStart = true) {
        RecordingProcessingManager(get(), get()).also { it.start() }
    }
}
```

Remove `includes(providePlatformTranscriptionModule())` and the `expect fun providePlatformTranscriptionModule()` declaration. Delete the two empty `actual` files from Phase 3.

**Verify:**
- On app launch, any `PENDING` recordings in DB start processing automatically
- Navigate away from dump screen mid-processing → job continues (not tied to ViewModel lifecycle)
- Logcat shows status transitions: `PENDING → TRANSCRIBING → GENERATING_INSIGHT → COMPLETED`

---

## Phase 8 — ViewModel + UI Simplification

### Phase 8a: State model cleanup

**`DumpDataState`** — remove `insightGenerationState`, `currentInsight`, `generationError`, `rateLimitRemaining`. Recordings list already exists; statuses now come from the `Recording` domain model.

**`DumpViewState`** — replace single `recordings` list with two:
- `processingRecordings: List<RecordingUiModel>` — status is PENDING/TRANSCRIBING/GENERATING_INSIGHT
- `completedRecordings: List<RecordingUiModel>` — status is COMPLETED or FAILED

Remove `insightGenerationState` and `currentInsight` fields.

**`RecordingUiModel`** — add `processingStatus: ProcessingStatus` and `errorMessage: String?`

**`DumpViewIntent`** — add `data class RetryProcessing(val id: String) : DumpViewIntent`

**`DumpSideEffect`** — remove `NavigateToInsightDetail`, `ShowInsightGenerationError`, `ShowRateLimitError`

**`InsightGenerationState` enum** — DELETE

### Phase 8b: New use case for retry

**File:** `feature_dump/src/domainMain/.../domain/usecase/RetryRecordingProcessingUseCase.kt`
```kotlin
class RetryRecordingProcessingUseCase(private val repository: RecordingRepository) {
    suspend operator fun invoke(id: String): UsecaseResult<Unit, Throwable>
}
```
Calls `repository.updateProcessingStatus(id, ProcessingStatus.PENDING, null)`.

Register in `DumpDomainModule.kt`: `factoryOf(::RetryRecordingProcessingUseCase)`

### Phase 8c: ViewModel simplification
**File:** `feature_dump/src/presentationMain/.../viewmodel/DumpViewModel.kt`

Remove constructor params: `transcriptionRepository`, `insightRepository`.
Add constructor params: `processingManager: RecordingProcessingManager`, `retryRecordingProcessingUseCase: RetryRecordingProcessingUseCase`.

New `handleStopRecording()`:
1. Cancel timer/amplitude jobs, call `audioRecorder.stopRecording()`
2. Create `Recording` with `processingStatus = PENDING`
3. Call `saveRecordingUseCase(recording)`
4. On success: `processingManager.enqueue(recording.id)`, reset state immediately
5. On failure: show error, reset state

New `RetryProcessing` handler:
```kotlin
is DumpViewIntent.RetryProcessing -> viewModelScope.launch {
    retryRecordingProcessingUseCase(intent.id)
    processingManager.enqueue(intent.id)
}
```

Update `convertToUiState`: split `dataState.recordings` into two lists by `processingStatus`.

### Phase 8d: UI updates

**`DumpRecordingScreen.kt`** — add "PROCESSING" section at top of `LazyColumn`, visible only when `viewState.processingRecordings.isNotEmpty()`.

**`RecordingListItem`** — add conditional rendering based on `processingStatus`:
- `TRANSCRIBING` / `GENERATING_INSIGHT`: small `CircularProgressIndicator` + status label
- `PENDING`: subtle pending indicator
- `FAILED`: error text + "Retry" `TextButton` → `onRetry(id)`
- `COMPLETED`: current UI, unchanged

**`RecordingHistoryScreen.kt`** — same "PROCESSING" section treatment.

**Delete:** `InsightGenerationScreen.kt`

**Simplify `DumpScreenContainer.kt`** — remove `when (insightGenerationState)` routing; render `DumpRecordingScreen` directly. Delete if trivial after simplification.

**Verify (end-to-end):**
1. Record → stop → card appears in "Processing" section immediately
2. Card moves to main list when `COMPLETED`
3. Navigate away → come back → processing continues
4. Disconnect network → record → after 1 retry, card shows FAILED + Retry button
5. Tap Retry → card goes back to Processing section and completes

---

## Phase Summary

| # | Phase | Key deliverable |
|---|-------|-----------------|
| 1 | DB migration | Add `processing_status`, `retry_count`, `error_message` to `recordings` table |
| 2 | Domain models | `ProcessingStatus` enum, updated `Recording` + `RecordingRepository` interface |
| 3 | Remove native STT | Delete 6 speech recognition files, simplify both platform `AudioRecorder`s |
| 4 | DB layer | Update mapper, data source, repository to handle new columns |
| 5 | Groq Whisper service | `TranscriptionService` interface + `GroqWhisperTranscriptionService` via Ktor multipart |
| 6 | Orchestrator | Pure `RecordingProcessingOrchestrator` — pipeline logic, auto-retry once, testable |
| 7 | Manager | `RecordingProcessingManager` (ApplicationScope singleton), start on app boot |
| 8 | ViewModel + UI | Simplify ViewModel, add "Processing" section, status-aware cards, Retry button |

## Critical File Paths

| Phase | Files |
|-------|-------|
| 1 | `core_database/src/commonMain/sqldelight/.../recordings.sq` |
| 2 | `feature_dump/src/domainMain/.../model/ProcessingStatus.kt` (new), `Recording.kt`, `RecordingRepository.kt` |
| 3 | Delete 6 files; modify `AndroidAudioRecorder.kt`, `IosAudioRecorder.kt`, both platform DI modules |
| 4 | `RecordingMapper.kt`, `RecordingLocalDataSource.kt`, `RecordingLocalDataSourceImpl.kt`, `RecordingRepositoryImpl.kt` |
| 5 | `AudioFileReader.kt` (new), `AndroidAudioFileReader.kt` (new), `IosAudioFileReader.kt` (new), `TranscriptionService.kt` (new), `GroqWhisperTranscriptionService.kt` (new), `InsightModule.kt` |
| 6 | `RecordingProcessingOrchestrator.kt` (new) |
| 7 | `RecordingProcessingManager.kt` (new), `DumpDataModule.kt` |
| 8 | `DumpDataState.kt`, `DumpViewState.kt`, `RecordingUiModel.kt`, `DumpViewIntent.kt`, `DumpSideEffect.kt`, `RetryRecordingProcessingUseCase.kt` (new), `DumpViewModel.kt`, `DumpDomainModule.kt`, `DumpRecordingScreen.kt`, `RecordingHistoryScreen.kt`, delete `InsightGenerationScreen.kt`, simplify/delete `DumpScreenContainer.kt` |
