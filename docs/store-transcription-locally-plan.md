# Plan: Store Transcription in DB with Audio Recording

## Context

When a user stops a recording, the app already stops the audio recorder and saves the `Recording` to SQLite via SQLDelight. However, the `TranscriptionRepository` (already wired in DI) is never called — the transcription is discarded after polling. This plan wires transcription into the stop-recording flow and persists the result alongside the audio file path in the `recordings` table.

---

## Implementation Phases

### Phase 1: Database Schema & Migration

#### Objective
Update the SQLite database schema to store transcriptions and ensure backward compatibility with existing data.

#### Changes Required

**File:** `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/recordings.sq`

Add `transcription TEXT` column to `CREATE TABLE`, update `insert` query:

```sql
CREATE TABLE recordings (
    id TEXT NOT NULL PRIMARY KEY,
    user_id TEXT,
    file_path TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    title TEXT,
    transcription TEXT          -- new
);

insert:
INSERT INTO recordings (id, user_id, file_path, duration_ms, created_at, title, transcription)
VALUES (?, ?, ?, ?, ?, ?, ?);
```

**New file:** `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/migrations/1.sqm`

```sql
ALTER TABLE recordings ADD COLUMN transcription TEXT;
```

**File:** `core_database/build.gradle.kts`

Add `schemaVersion = 2` inside the `create("SanctuaryDatabase")` block so SQLDelight picks up the migration.

#### Required Test Cases

- [ ] **Schema Validation Test**: Verify the `recordings` table schema includes the `transcription` column after compilation
- [ ] **Migration Execution Test**: Execute migration on a v1 database and verify the column is added successfully
- [ ] **Data Preservation Test**: Verify existing rows from pre-migration state retain their original data (id, file_path, duration_ms, created_at, title remain unchanged)
- [ ] **NULL Default Test**: Verify that existing rows get `transcription = NULL` after migration
- [ ] **Insert Query Test**: Verify the new insert query accepts all 7 parameters including transcription
- [ ] **Query Generation Test**: Verify SQLDelight generates proper Kotlin query wrapper classes with transcription parameter

---

### Phase 2: Domain Model & Data Mapping

#### Objective
Add transcription field to domain model and ensure bidirectional mapping between database entities and domain objects.

#### Changes Required

**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/model/Recording.kt`

Add `transcription: String?` field to the data class:

```kotlin
data class Recording(
    val id: String,
    val userId: String?,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long,
    val title: String?,
    val transcription: String?,  // new
)
```

**File:** `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/mapper/RecordingMapper.kt`

Map `transcription` in both `toDomain()` and `toEntity()` extension functions:

```kotlin
// Example mapper updates
fun RecordingEntity.toDomain(): Recording = Recording(
    id = id,
    userId = userId,
    filePath = filePath,
    durationMs = durationMs,
    createdAt = createdAt,
    title = title,
    transcription = transcription,  // new
)

fun Recording.toEntity(): RecordingEntity = RecordingEntity(
    id = id,
    userId = userId,
    filePath = filePath,
    durationMs = durationMs,
    createdAt = createdAt,
    title = title,
    transcription = transcription,  // new
)
```

#### Required Test Cases

- [ ] **Domain Model Test**: Create Recording instances with and without transcription and verify they're created correctly
- [ ] **Null Transcription Test**: Verify Recording can be created with `transcription = null` without errors
- [ ] **Entity-to-Domain Mapping Test**: Map a RecordingEntity with transcription to Recording and verify all fields match
- [ ] **Domain-to-Entity Mapping Test**: Map a Recording with transcription to RecordingEntity and verify all fields match
- [ ] **Null Transcription Mapping Test**: Map entities with `transcription = null` and verify it transfers correctly
- [ ] **Round-trip Mapping Test**: Entity → Domain → Entity and verify transcription data is preserved

---

### Phase 3: Data Layer — Local Persistence

#### Objective
Implement database save and retrieval operations with transcription support.

#### Changes Required

**File:** `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/datasource/RecordingLocalDataSourceImpl.kt`

Update the `insert()` method to pass transcription to the database:

```kotlin
suspend fun insert(recording: Recording) {
    queries.insert(
        id = recording.id,
        userId = recording.userId,
        filePath = recording.filePath,
        durationMs = recording.durationMs,
        createdAt = recording.createdAt,
        title = recording.title,
        transcription = recording.transcription,  // new parameter
    )
}
```

Also ensure any `getAll()` or retrieval queries properly map transcription from the database.

#### Required Test Cases

- [ ] **Insert with Transcription Test**: Insert a Recording with non-null transcription and query it back, verifying transcription is preserved
- [ ] **Insert without Transcription Test**: Insert a Recording with `transcription = null` and verify it saves correctly
- [ ] **Retrieval Test**: Save a Recording with transcription, then retrieve it and verify all fields including transcription match
- [ ] **Bulk Retrieval Test**: Insert multiple recordings (some with transcription, some without) and verify `getAll()` returns correct data
- [ ] **Null Handling Test**: Verify that `null` transcriptions don't cause database errors or casting failures
- [ ] **Long Transcription Test**: Insert a Recording with a very long transcription (e.g., 10KB text) and verify it's stored and retrieved correctly

---

### Phase 4: ViewModel Integration — Transcription Flow

#### Objective
Wire the TranscriptionRepository into the stop-recording flow so transcriptions are captured before saving.

#### Changes Required

**File:** `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/viewmodel/DumpViewModel.kt`

- Inject `TranscriptionRepository` in the constructor (it's already registered in DI).
- In `handleStopRecording()`, after `audioRecorder.stopRecording()` and before creating the `Recording`, call `transcriptionRepository.transcribe(filePath)` and capture the result.
- Pass the transcription text (or `null` on failure) into the `Recording` constructor.

```kotlin
// handleStopRecording() updated flow:
private suspend fun handleStopRecording() {
    audioRecorder.stopRecording()
    val filePath = dataState.value.currentFilePath ?: return
    val durationMs = dataState.value.elapsedMs
    updateState { it.copy(recordingStatus = RecordingStatus.Saving) }

    viewModelScope.launch {
        val transcription = when (val t = transcriptionRepository.transcribe(filePath)) {
            is UsecaseResult.Success -> t.data
            is UsecaseResult.Failure -> {
                // Log failure but don't prevent saving
                null
            }
        }
        val recording = Recording(
            id = generateId(),
            userId = null,
            filePath = filePath,
            durationMs = durationMs,
            createdAt = currentEpochMs(),
            title = null,
            transcription = transcription,
        )
        when (val result = saveRecordingUseCase(recording)) {
            is UsecaseResult.Success -> emitSideEffect(DumpSideEffect.RecordingSaved)
            is UsecaseResult.Failure -> emitSideEffect(DumpSideEffect.ShowError("Failed to save recording"))
        }
        updateState { it.copy(recordingStatus = RecordingStatus.Idle, currentFilePath = null, elapsedMs = 0L) }
    }
}
```

#### Required Test Cases

- [ ] **Transcription Call Test**: Mock TranscriptionRepository and verify it's called with correct filePath during stop-recording
- [ ] **Successful Transcription Test**: Mock TranscriptionRepository to return success, verify Recording includes the transcription text
- [ ] **Failed Transcription Test**: Mock TranscriptionRepository to return failure, verify Recording saves with `transcription = null` and no side effect error is emitted
- [ ] **Timeout Handling Test**: Mock TranscriptionRepository to timeout, verify the recording still saves (resilience test)
- [ ] **State Transition Test**: Verify `recordingStatus` transitions to `Saving` then back to `Idle` regardless of transcription success/failure
- [ ] **Side Effect Test**: Verify correct side effects are emitted (RecordingSaved on success, ShowError on save failure)
- [ ] **Concurrent Operations Test**: Start two recordings in quick succession, verify both transcription calls complete without interference
- [ ] **Concurrent Save Test**: Verify that if transcription takes time, the UI state correctly reflects "Saving" status

---

### Phase 5: UI Layer — Presentation Model

#### Objective
Add transcription to UI models and update mapping from domain to presentation layer.

#### Changes Required

**File:** `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/state/RecordingUiModel.kt`

Add `transcription: String?` field:

```kotlin
data class RecordingUiModel(
    val id: String,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long,
    val title: String?,
    val transcription: String?,  // new
)
```

**File:** `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/viewmodel/DumpViewModel.kt`

Update the `Recording.toUiModel()` extension (or equivalent) to pass transcription:

```kotlin
fun Recording.toUiModel(): RecordingUiModel = RecordingUiModel(
    id = id,
    filePath = filePath,
    durationMs = durationMs,
    createdAt = createdAt,
    title = title,
    transcription = transcription,  // new
)
```

#### Required Test Cases

- [ ] **UI Model Creation Test**: Create RecordingUiModel with and without transcription
- [ ] **Mapping Test**: Map a Recording to RecordingUiModel and verify transcription is transferred
- [ ] **Null Transcription UI Test**: Verify RecordingUiModel with `transcription = null` doesn't cause UI rendering issues
- [ ] **Empty String Test**: Test RecordingUiModel with `transcription = ""` (empty string) vs `null`

---

### Phase 6: Integration & End-to-End Testing

#### Objective
Verify the complete flow works across all layers on both Android and iOS.

#### Changes Required

No code changes required; this phase focuses on comprehensive testing.

#### Required Test Cases

- [ ] **Full Record-Transcribe-Save Flow Test (Android)**: Record audio → trigger transcription → verify saved in DB with transcription
- [ ] **Full Record-Transcribe-Save Flow Test (iOS)**: Same as above on iOS
- [ ] **Migration + Existing Data Test**: Apply migration to a pre-existing database with recordings (no transcription), verify old records are preserved and new records include transcription
- [ ] **Retrieve & Display Test**: Save a recording with transcription, retrieve from history, verify transcription is displayed in UI
- [ ] **Large Transcription Test**: Test with a very long transcription (stress test)
- [ ] **Empty/Short Audio Test**: Record very short audio (< 1 second), verify transcription handles gracefully
- [ ] **Network Failure During Transcription Test**: Simulate network failure during transcription request, verify recording still saves
- [ ] **Database Corruption Recovery Test**: Intentionally corrupt migration/schema, verify error handling
- [ ] **Performance Test**: Record 10+ recordings back-to-back, verify no memory leaks and all transcriptions are captured
- [ ] **Platform Consistency Test**: Run same tests on Android and iOS, verify identical behavior

---

## File Change Summary

| File | Change | Phase |
|------|--------|-------|
| `core_database/src/commonMain/sqldelight/.../recordings.sq` | Add `transcription TEXT` column + update `insert` query | 1 |
| `core_database/src/commonMain/sqldelight/.../migrations/1.sqm` | New — `ALTER TABLE recordings ADD COLUMN transcription TEXT;` | 1 |
| `core_database/build.gradle.kts` | Add `schemaVersion = 2` | 1 |
| `feature_dump/src/domainMain/.../domain/model/Recording.kt` | Add `transcription: String?` | 2 |
| `feature_dump/src/dataMain/.../data/mapper/RecordingMapper.kt` | Map transcription in both directions | 2 |
| `feature_dump/src/dataMain/.../data/datasource/RecordingLocalDataSourceImpl.kt` | Pass transcription to `queries.insert(...)` | 3 |
| `feature_dump/src/presentationMain/.../presentation/viewmodel/DumpViewModel.kt` | Inject TranscriptionRepository + wire transcription into stop-recording flow | 4 |
| `feature_dump/src/presentationMain/.../presentation/state/RecordingUiModel.kt` | Add `transcription: String?` | 5 |

---

## Implementation Order

1. **Phase 1** (Database) — Foundation, all other layers depend on this
2. **Phase 2** (Domain & Mapping) — Enables data flow through layers
3. **Phase 3** (Local Persistence) — Enables saving transcriptions
4. **Phase 4** (ViewModel Integration) — Enables capture of transcriptions
5. **Phase 5** (UI Layer) — Enables display of transcriptions
6. **Phase 6** (Integration Testing) — Validates entire flow

---

## Verification Checklist

- [ ] **Build**: `./gradlew :composeApp:assembleDebug` — compiles cleanly
- [ ] **All test cases** from each phase are passing
- [ ] **Migration**: Test on an existing pre-v2 database to ensure data integrity
- [ ] **Record & Stop**: Recording with transcription is saved to database
- [ ] **Retrieval**: Verify via Android Studio's App Inspection → Database Inspector or iOS debugging tools
- [ ] **Transcription Failure**: If speech recognition times out, `transcription` stores `null` and recording still saves
- [ ] **No Data Loss**: Migration doesn't lose existing recordings; old records retain their data
- [ ] **Platform Testing**: Both Android and iOS apps compile and function correctly
- [ ] **Performance**: No noticeable lag during recording-to-transcription-to-save flow
