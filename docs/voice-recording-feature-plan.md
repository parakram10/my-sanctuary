# Voice Recording Feature — Implementation Plan

## Context

Sanctuary needs a voice recording screen as the core "mental dump" feature — letting users record their thoughts via voice. The recording screen matches the screenshot UI: microphone icon with concentric circles, timer, waveform visualization, "Stop Recording" + "Cancel" buttons, and a "ONLY YOU CAN HEAR THIS" header. Saved recordings are visible both on this screen (recent) and in the History tab (full list).

## Decisions

- **Module**: `feature_dump` (already scaffolded with correct layered source sets)
- **Buttons**: "Stop Recording" + "Cancel" (matching screenshot)
- **User scoping**: Add nullable `user_id` column now, populate when auth is built
- **Recordings list**: Moved to separate `RecordingHistoryScreen` (full list) — accessible via navigation
- **Recording playback**: Dialog-based playback with Play/Stop controls
- **UI Theme**: Gradient background (light blue → light pink), timer in pill-shaped card

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
- Recording: `StartRecording`, `StopRecording`, `CancelRecording`
- Playback: `OpenRecording(id)`, `ToggleSelectedRecordingPlayback`, `DismissPlaybackDialog`, `DeleteRecording(id)`
- Navigation: `DismissScreen`, `PermissionResult(granted: Boolean)`

**DataState** (`DumpDataState`):
- `recordingStatus: RecordingStatus` (Idle, Recording, Saving)
- `elapsedMs: Long`, `currentFilePath: String?`, `recordings: List<Recording>`, `permissionGranted: Boolean`, `amplitudes: List<Float>`
- Playback: `selectedRecordingId: String?`, `showPlaybackDialog: Boolean`, `isPlayingSelectedRecording: Boolean`

**ViewState** (`DumpViewState`):
- `isRecording: Boolean`, `isSaving: Boolean`, `timerText: String` ("MM:SS"), `recordings: List<RecordingUiModel>`, `amplitudes: List<Float>`
- `selectedRecording: RecordingUiModel?`, `showPlaybackDialog: Boolean`, `isPlayingSelectedRecording: Boolean`, `showPermissionRationale: Boolean`

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

**Main screens:**

| File | Purpose |
|---|---|
| `.../screen/DumpRecordingScreen.kt` | Recording screen — header, microphone icon, timer card, waveform, buttons; includes playback dialog |
| `.../screen/RecordingHistoryScreen.kt` | History of all recordings — list of RecordingListItems with delete capability |
| `.../screen/MentalDumpHomePlaceholder.kt` | Wrapper that routes to DumpRecordingScreen |

**Components:**

| File | Purpose |
|---|---|
| `.../components/ConcentricMicrophoneIcon.kt` | Mic icon in concentric circles with pulse animation when recording |
| `.../components/TimerCard.kt` | Pill-shaped card with red dot + "MM:SS" timer (visible only when recording) |
| `.../components/WaveformVisualizer.kt` | Canvas with vertical bars from amplitude data, animates during recording |
| `.../components/RecordingListItem.kt` | Card showing recording title, duration, recorded date; click to play, delete button |

**DumpRecordingScreen layout** (top to bottom):
1. Top bar: "ONLY YOU CAN HEAR THIS" + lock icon + X close button (gradient background)
2. Concentric circles with mic icon
3. Timer card (pill shape, red dot + time) — visible only when recording
4. Waveform visualizer (30 bars, gray)
5. "Let it out." heading
6. "Stop Recording" button (SanctuaryButton, dark) — visible only when recording
7. "CANCEL" text button — visible only when recording
8. "Start Recording" button — visible when idle
9. **Recent recordings section** (visible if recordings exist):
   - Header: "RECENT" label + "See all" button (clickable, links to RecordingHistoryScreen)
   - Last 3 recordings as RecordingListItem cards (click to play, delete per item)
10. **Playback dialog** (separate AlertDialog):
    - Title: recording title
    - Body: duration + recorded date
    - Actions: Play/Stop button + Close button

**RecordingHistoryScreen layout** (top to bottom):
1. Top bar: back arrow + "RECORDINGS" title
2. Full list of recordings (LazyColumn) with delete per item
3. Empty state: "No recordings yet" when list is empty

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

### Database & Domain
- `core_database/src/commonMain/sqldelight/.../recordings.sq` — ✅ **Done** (schema with queries)
- `feature_dump/src/domainMain/kotlin/...` — ✅ **Done** (model, repository, use cases, audio interfaces)

### Data Layer
- `feature_dump/src/dataMain/kotlin/...` — ✅ **Done** (datasource, repository impl, mappers, audio expect)
- `feature_dump/src/androidMain/kotlin/...` — ✅ **Done** (AudioRecorder, AudioPlayer, AudioFileProvider actual)
- `feature_dump/src/iosMain/kotlin/...` — ✅ **Done** (AudioRecorder, AudioPlayer, AudioFileProvider actual)

### Presentation Layer
- `feature_dump/src/presentationMain/kotlin/...` — ✅ **Done**
  - `DumpViewModel.kt` — MVI ViewModel with recording + playback logic
  - `DumpRecordingScreen.kt` — Main recording UI with gradient, timer card, waveform, playback dialog
  - `RecordingHistoryScreen.kt` — Full recordings list (NEW)
  - `state/` — MVI state classes (intents, data state, view state, side effects) (UPDATED)
  - `utils/TimeUtils.kt` — Date/time formatting utilities
  - Components: `ConcentricMicrophoneIcon.kt`, `TimerCard.kt` (NEW), `WaveformVisualizer.kt`, `RecordingListItem.kt`

### DI & App Integration
- `feature_dump/src/presentationMain/kotlin/.../di/DumpPresentationModule.kt` — ✅ **Done** (viewModel registration)
- `feature_dump/src/dataMain/kotlin/.../di/DumpDataModule.kt` — ✅ **Done** (datasource, repository)
- `feature_dump/src/domainMain/kotlin/.../di/DumpDomainModule.kt` — ✅ **Done** (use cases)
- `feature_dump/src/androidMain/kotlin/.../di/DumpPlatformModule.android.kt` — ✅ **Done** (audio impls)
- `feature_dump/src/iosMain/kotlin/.../di/DumpPlatformModule.ios.kt` — ✅ **Done** (audio impls)
- `composeApp/src/commonMain/kotlin/sanctuary/app/App.kt` — ⚠️ **Pending** (navigation integration)
- `composeApp/src/commonMain/kotlin/sanctuary/app/di/PlatformKoinModule.kt` — ✅ **Done** (module aggregation)
- `composeApp/src/androidMain/kotlin/sanctuary/app/di/PlatformKoinModule.android.kt` — ✅ **Done**
- `composeApp/src/iosMain/kotlin/sanctuary/app/di/PlatformKoinModule.ios.kt` — ✅ **Done**

### Platform Manifests
- `composeApp/src/androidMain/AndroidManifest.xml` — ✅ **Done** (RECORD_AUDIO permission)
- `iosApp/iosApp/Info.plist` — ✅ **Done** (NSMicrophoneUsageDescription)

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

### Build & Compilation
1. `./gradlew :feature_dump:build` — verify compilation across all source sets
2. `./gradlew :composeApp:build` — full app build

### Recording Functionality (Android)
1. `./gradlew :composeApp:installDebug` — install on emulator/device
2. **Permission flow**: Verify microphone permission dialog on first record attempt
3. **Recording lifecycle**: Start → timer ticks (MM:SS card) → waveform animates → Stop
4. **Cancel flow**: Start → Cancel → no recording saved, file deleted
5. **Playback**: Tap recording → dialog opens → Play → sound plays → Stop works
6. **History**: Navigate to RecordingHistoryScreen → see all recordings → delete works
7. **Persistence**: Kill app → reopen → recordings still listed

### Recording Functionality (iOS)
1. `open iosApp/iosApp.xcodeproj` — build and run via Xcode
2. Same tests as Android above
3. Verify audio session management (microphone setup, background behavior if needed)
