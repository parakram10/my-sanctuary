# Voice Recording Feature — Implementation Plan

## Context

Sanctuary needs a voice recording screen as the core "mental dump" feature — letting users record their thoughts via voice. The recording screen matches the screenshot UI: microphone icon with concentric circles, timer, waveform visualization, "Stop Recording" + "Cancel" buttons, and a "ONLY YOU CAN HEAR THIS" header. Saved recordings are visible both on this screen (recent) and in the History tab (full list).

## Decisions

- **Module**: `feature_dump` (already scaffolded with correct layered source sets)
- **Buttons**: "Stop Recording" + "Cancel" (matching screenshot)
- **User scoping**: Add nullable `user_id` column now, populate when auth is built
- **Recordings list**: Recent recordings below controls on dump screen + full list in History tab

---

## Implementation Phases

### Phase 1: Database Schema
**File**: `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/recordings.sq`

```sql
CREATE TABLE recordings (
    id TEXT NOT NULL PRIMARY KEY,
    user_id TEXT,
    file_path TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    title TEXT
);
```

Queries: `selectAll`, `selectByUserId`, `selectById`, `insert`, `deleteById`

### Phase 2: Domain Layer (`feature_dump/src/domainMain`)

| File | Purpose |
|---|---|
| `.../domain/model/Recording.kt` | `data class Recording(id, userId, filePath, durationMs, createdAt, title)` |
| `.../domain/repository/RecordingRepository.kt` | Interface: `getAllRecordings(): Flow<List<Recording>>`, `saveRecording()`, `deleteRecording()` |
| `.../domain/usecase/GetRecordingsUseCase.kt` | Wraps `repository.getAllRecordings()` |
| `.../domain/usecase/SaveRecordingUseCase.kt` | Wraps `repository.saveRecording()` |
| `.../domain/usecase/DeleteRecordingUseCase.kt` | Wraps `repository.deleteRecording()` |

### Phase 3: Data Layer (`feature_dump/src/dataMain`)

The data layer is structured for **local-first with future remote sync**. The repository mediates between a local data source (SQLDelight) and a remote data source (Ktor — to be implemented when the backend is ready). All reads/writes go through the repository, so the domain layer never changes when remote is added.

| File | Purpose |
|---|---|
| `.../data/datasource/RecordingLocalDataSource.kt` | Interface wrapping SQLDelight queries |
| `.../data/datasource/RecordingLocalDataSourceImpl.kt` | Implementation using `SanctuaryDatabase.recordingsQueries` |
| `.../data/datasource/RecordingRemoteDataSource.kt` | Interface for remote API calls (stub now, implemented when backend exists) |
| `.../data/mapper/RecordingMapper.kt` | Maps SQLDelight generated class ↔ domain `Recording` |
| `.../data/repository/RecordingRepositoryImpl.kt` | Implements `RecordingRepository` — local-first: always writes to local, remote sync deferred |

**Future remote sync strategy (when backend is ready):**
- `RecordingRemoteDataSource` implemented via `core_network` (Ktor client already set up)
- Repository uploads audio file + metadata after local save (fire-and-forget or queued sync)
- Add `sync_status` column to `recordings` table (`PENDING`, `SYNCED`, `FAILED`) to track upload state
- User-scoped via `user_id` (already in schema) — remote API will filter by authenticated user

### Phase 4: Audio Recording Abstraction (expect/actual)

**Expect declarations in `feature_dump/src/dataMain`:**

| File | Purpose |
|---|---|
| `.../data/audio/AudioRecorder.kt` | `expect class AudioRecorder` — `startRecording(path)`, `stopRecording()`, `cancelRecording()`, `amplitudeFlow(): Flow<Float>` |
| `.../data/audio/AudioFileProvider.kt` | `expect class AudioFileProvider` — `recordingsDirectory()`, `newRecordingFilePath()` |

**Actual implementations:**

| Platform | AudioRecorder | AudioFileProvider |
|---|---|---|
| `androidMain` | `MediaRecorder` (AAC/M4A) | `context.filesDir/recordings/` |
| `iosMain` | `AVAudioRecorder` (AAC/M4A) | `NSDocumentDirectory/recordings/` |

> **Risk**: `expect` in custom `dataMain` source set with `actual` in `androidMain`/`iosMain`. If this causes compilation issues, fallback: move expects to `commonMain`.

### Phase 5: Presentation — MVI State (`feature_dump/src/presentationMain`)

**Intents** (`DumpViewIntent`):
- `StartRecording`, `StopRecording`, `CancelRecording`, `DismissScreen`, `DeleteRecording(id)`, `LoadRecordings`, `PermissionResult(granted: Boolean)`

**DataState** (`DumpDataState`):
- `recordingStatus: RecordingStatus` (Idle, Recording, Saving)
- `elapsedMs: Long`, `currentFilePath: String?`, `recordings: List<Recording>`, `permissionGranted: Boolean`, `amplitudes: List<Float>`

**ViewState** (`DumpViewState`):
- `isRecording: Boolean`, `timerText: String` ("MM:SS"), `recordings: List<RecordingUiModel>`, `amplitudes: List<Float>`

**SideEffects** (`DumpSideEffect`):
- `RequestMicrophonePermission`, `NavigateBack`, `ShowError(message)`

### Phase 6: ViewModel (`feature_dump/src/presentationMain`)

**File**: `.../presentation/viewmodel/DumpViewModel.kt`

Extends `BaseStateMviViewModel<DumpViewIntent, DumpDataState, DumpViewState, DumpSideEffect>`.

Key logic:
- `StartRecording` → check permission → if granted: call `audioRecorder.startRecording()`, launch timer coroutine (1s tick), collect `amplitudeFlow()`; if not: emit `RequestMicrophonePermission` side effect
- `StopRecording` → stop recorder, save metadata via `SaveRecordingUseCase`, reset to Idle
- `CancelRecording` → cancel recorder, delete temp file, reset to Idle
- `convertToUiState()` → format `elapsedMs` to "MM:SS", map recordings to `RecordingUiModel`
- On init: collect `GetRecordingsUseCase()` flow

### Phase 7: UI Composables (`feature_dump/src/presentationMain`)

**Replace** existing `MentalDumpHomePlaceholder.kt` with:

| File | Purpose |
|---|---|
| `.../screen/DumpRecordingScreen.kt` | Main screen — header bar, microphone icon, timer, waveform, buttons, recent recordings list |
| `.../screen/components/ConcentricMicrophoneIcon.kt` | Microphone icon centered in 2-3 concentric semi-transparent circles (Canvas/layered Box) |
| `.../screen/components/WaveformVisualizer.kt` | Canvas drawing vertical bars from amplitude list, animated during recording |
| `.../screen/components/RecordingListItem.kt` | Card showing recording title, duration, date |

**Screen layout** (top to bottom):
1. Top bar: "ONLY YOU CAN HEAR THIS" + X close button
2. Concentric circles with mic icon + "• MM:SS" timer
3. Waveform visualizer
4. "Let it out." text
5. "Stop Recording" button (SanctuaryButton) — visible only when recording
6. "CANCEL" text button — visible only when recording
7. Start recording button (mic icon) — visible when idle
8. Recent recordings list (LazyColumn of RecordingListItem, limited to ~3-5 items)

### Phase 8: Dependency Injection (Koin)

| Layer | Module file | Provides |
|---|---|---|
| `dataMain` | `DumpDataModule.kt` | `RecordingLocalDataSource`, `RecordingRepository` |
| `domainMain` | `DumpDomainModule.kt` | Use cases (factory scope) |
| `presentationMain` | `DumpPresentationModule.kt` | `DumpViewModel` (viewModel scope) |
| `androidMain` | `DumpPlatformModule.kt` | `AudioRecorder`, `AudioFileProvider` (actual impls) |
| `iosMain` | `DumpPlatformModule.kt` | `AudioRecorder`, `AudioFileProvider` (actual impls) |

Register all in `composeApp/.../di/PlatformKoinModule.kt` → `allAppModules()`.

### Phase 9: Platform Configuration

- **Android**: Add `<uses-permission android:name="android.permission.RECORD_AUDIO"/>` to `composeApp/src/androidMain/AndroidManifest.xml`
- **iOS**: Add `NSMicrophoneUsageDescription` to `iosApp/iosApp/Info.plist`

### Phase 10: Wire Into App

Update `composeApp/src/commonMain/kotlin/sanctuary/app/App.kt`:
- Replace placeholder column with basic navigation state (`BottomNavDestination`)
- Home tab → `DumpRecordingScreen`
- Wire `BottomNavigationBar` into the scaffold
- Register dump feature Koin modules

---

## Critical Files to Modify

- `core_database/src/commonMain/sqldelight/.../recordings.sq` — **new**
- `feature_dump/src/domainMain/kotlin/...` — **new** (model, repository interface, use cases)
- `feature_dump/src/dataMain/kotlin/...` — **new** (datasource, repository impl, expect classes)
- `feature_dump/src/androidMain/kotlin/...` — **new** (actual AudioRecorder, AudioFileProvider)
- `feature_dump/src/iosMain/kotlin/...` — **new** (actual AudioRecorder, AudioFileProvider)
- `feature_dump/src/presentationMain/kotlin/...` — **replace** placeholder with real screen + ViewModel
- `composeApp/src/commonMain/kotlin/sanctuary/app/App.kt` — **modify** (navigation + dump screen)
- `composeApp/src/commonMain/kotlin/sanctuary/app/di/PlatformKoinModule.kt` — **modify** (add dump modules)
- `composeApp/src/androidMain/AndroidManifest.xml` — **modify** (add RECORD_AUDIO permission)
- `iosApp/iosApp/Info.plist` — **modify** (add NSMicrophoneUsageDescription)

## Implementation Order

1. Database schema (`recordings.sq`)
2. Domain layer (model, repository interface, use cases)
3. Data layer (datasource, repository impl, mappers)
4. Audio expect/actual (AudioRecorder, AudioFileProvider) — Android first, then iOS
5. Presentation state (intents, states, side effects)
6. ViewModel
7. UI composables (screen, components)
8. Koin DI modules
9. App integration (navigation, permissions, manifest)

## Verification

1. **Build**: `./gradlew :feature_dump:build` — verify compilation across all source sets
2. **Android run**: `./gradlew :composeApp:installDebug` — test recording flow on emulator/device
3. **Permission flow**: Verify microphone permission dialog appears on first record attempt
4. **Recording lifecycle**: Start → timer ticks → Stop → recording appears in list
5. **Cancel flow**: Start → Cancel → no recording saved, file deleted
6. **Persistence**: Kill app → reopen → recordings still listed
7. **iOS**: Build and test via Xcode (`open iosApp/iosApp.xcodeproj`)
