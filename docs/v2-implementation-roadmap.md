# v2 Recording Pipeline — Implementation Roadmap

**Last Updated:** 2026-04-03  
**Status:** Phase A Complete, Phase B Ready to Start

---

## Current State

### ✅ Completed (Skeleton Phase)
These classes exist with TODO stubs or basic structure:

| Class | Location | Status | Notes |
|-------|----------|--------|-------|
| `ProcessingStatus` enum | domain/model | ✅ Complete | 5 states: PENDING→TRANSCRIBING→GENERATING_INSIGHT→COMPLETED/FAILED |
| `ProcessingErrorCode` enum | domain/model | ✅ Complete | Transient vs permanent, WM eligibility flag |
| `Recording` (updated) | domain/model | ✅ v2 fields added | processingStatus, errorCode, backgroundWmAttempts, recordingLocale |
| `RecordingRepository` (extended) | domain/repository | ✅ Interface extended | 5 new methods for v2 pipeline |
| `TranscriptionPort` | domain/port | ✅ Interface only | For pluggable transcription (on-device/cloud) |
| `InsightPort` | domain/port | ✅ Interface only | For pluggable insight generation |
| `OnDeviceTranscriber` expect/actual | domain/transcription + android/ios | ⚠️ Skeleton | Interface + Android/iOS stubs (no implementation) |
| `BackgroundWorkScheduler` expect/actual | domain/scheduling + android/ios | ⚠️ Skeleton | Interface + Android/iOS stubs |
| `RecordingProcessingEngine` | data/processing | ⚠️ Skeleton | Core FSM logic (TODO) |
| `RecordingProcessingWorker` | androidMain/platform | ⚠️ Basic impl | Queries eligible + delegates to engine |
| `AndroidBackgroundWorkScheduler` | androidMain/platform | ⚠️ Basic impl | Enqueues WM work |
| `IosBackgroundWorkScheduler` | iosMain/platform | ⚠️ Skeleton | Minimum foreground flush (TODO) |
| `RetryRecordingProcessingUseCase` | domain/usecase | ⚠️ Skeleton | Reset to PENDING + clear WM counter |
| `WorkManagerSetup` | androidMain/platform | ✅ Complete | Periodic job configuration |

---

## Implementation Order (Dependency Chain)

### Phase B: Database Schema & Mapping
**Status:** ❌ Not Started  
**Deliverable:** SQLDelight schema update + mapper changes  
**Why First:** Recording model needs DB persistence; everything else depends on this

```
Recording → [DB]
     ↓
RecordingRepositoryImpl → [Needs DB queries]
     ↓
RecordingProcessingEngine → [Reads/writes Recording]
```

**Classes to Implement:**

| # | Class | File | Task |
|---|-------|------|------|
| B1 | (none - SQL only) | `core_database/src/commonMain/sqldelight/recordings.sq` | Add columns: `processing_status`, `error_code`, `background_wm_attempts`, `recording_locale` |
| B2 | `RecordingMapper` | `dataMain/.../mapper/RecordingMapper.kt` | Map new fields: ProcessingStatus ↔ DB enum, etc. |

**Test Plan:**
- Insert recording with v2 fields
- Query by status, update status, verify round-trip
- Check index on `processing_status` for WM queries

---

### Phase C: Repository Implementations
**Status:** ❌ Not Started  
**Deliverable:** Data source + repository methods  
**Why After B:** Need DB schema to implement queries

**Classes to Implement:**

| # | Class | File | Task |
|---|-------|------|------|
| C1 | `RecordingLocalDataSource` interface update | `dataMain/.../datasource/RecordingLocalDataSource.kt` | Add 5 new method signatures |
| C2 | `RecordingLocalDataSourceImpl` | `dataMain/.../datasource/RecordingLocalDataSourceImpl.kt` | Implement 5 new methods using SQLDelight queries |
| C3 | `RecordingRepositoryImpl` | `dataMain/.../repository/RecordingRepositoryImpl.kt` | Implement 5 new interface methods |

**Test Plan:**
- Integration test: PENDING → TRANSCRIBING → COMPLETED state flow
- Query eligible for WM: should return PENDING + FAILED(transient)
- Increment WM attempts: idempotent

**Blocking:** Phase B must be done first

---

### Phase D: On-Device Transcription (Dual Platform)
**Status:** ❌ Not Started  
**Deliverable:** Android + iOS STT implementations  
**Why Here:** Engine needs transcription service; can be done in parallel with Phase C

**Classes to Implement:**

| # | Class | File | Platform | Task |
|---|-------|------|----------|------|
| D1 | `AndroidOnDeviceTranscriber` | `androidMain/.../platform/AndroidOnDeviceTranscriber.kt` | Android | SpeechRecognizer for en + hi; map errors → `ProcessingErrorCode` |
| D2 | `IosOnDeviceTranscriber` | `iosMain/.../platform/IosOnDeviceTranscriber.kt` | iOS | SFSpeechRecognizer for en + hi; block on unavailable language |

**Test Plan (Android):**
- English: record → transcribe → assert output
- Hindi: record → transcribe → assert output
- Unsupported locale (e.g., "fr"): throw `ON_DEVICE_LANGUAGE_NOT_SUPPORTED`
- Network error: throw `NETWORK` (caught by engine, retried)

**Test Plan (iOS):**
- Manual device test: en + hi in Settings
- On unsupported: block with user-friendly message

**Not Blocking:** Can parallel with C

---

### Phase E: Insight Generation Service (English-Only)
**Status:** ❌ Not Started  
**Deliverable:** Implement `InsightPort` in existing Claude/Groq services  
**Why Here:** Engine depends on insight service

**Classes to Implement:**

| # | Class | File | Task |
|---|-------|------|------|
| E1 | `ClaudeInsightGenerationService` | `dataMain/.../service/ClaudeInsightGenerationService.kt` | ✅ Already exists; update to implement `InsightPort` interface |
| E2 | `GroqInsightGenerationService` | `dataMain/.../service/GroqInsightGenerationService.kt` | ✅ Already exists; update to implement `InsightPort` interface |
| E3 | `InsightModule` | `dataMain/.../di/InsightModule.kt` | Wire `InsightPort` → implementation (selectable via env var) |

**Test Plan:**
- Mock engine test: happy path → Insight returned
- Error mapping: network → NETWORK, rate limit → RATE_LIMIT

**Dependencies:** None (existing services refactored)

---

### Phase F: Core Processing Engine
**Status:** ❌ Not Started  
**Deliverable:** Complete FSM + checkpoint logic + single-flight  
**Why Here:** Engine orchestrates all the above; all prior phases must be done

**Classes to Implement:**

| # | Class | File | Task |
|---|-------|------|------|
| F1 | `RecordingProcessingEngine` | `dataMain/.../processing/RecordingProcessingEngine.kt` | Implement `process(id)` with: fetch → checkpoint check → transcribe → update transcript → generate insight → mark COMPLETED/FAILED |
| F2 | (internal) | Same file | Implement `scheduleImmediate(id)` to launch on IO dispatcher without blocking |
| F3 | (internal) | Same file | Implement single-flight mutex (ConcurrentHashMap of Jobs) |

**Test Plan (Unit):**
- Happy path: PENDING → TRANSCRIBING → GENERATING_INSIGHT → COMPLETED
- Checkpoint: if transcript exists, skip transcription
- Transcription failure, retry count 0 → PENDING (auto-retry queued)
- Transcription failure, retry count ≥1 → FAILED + error code
- Single-flight: two calls to `process(same_id)` → only one executes
- Rate limit: FAILED(RATE_LIMIT) + code set correctly

**Blocking:** Needs B, C, D, E

---

### Phase G: Android WorkManager Integration
**Status:** ⚠️ Partially done  
**Deliverable:** Worker + scheduler implementation + DI setup  
**Why Here:** Pulls everything together for background processing

**Classes to Implement:**

| # | Class | File | Task |
|---|-------|------|------|
| G1 | `RecordingProcessingWorker` | `androidMain/.../platform/RecordingProcessingWorker.kt` | ✅ Basic impl done; verify it queries eligible + calls engine |
| G2 | `AndroidBackgroundWorkScheduler` | `androidMain/.../platform/AndroidBackgroundWorkScheduler.kt` | ✅ Basic impl done; verify unique work enqueue |
| G3 | `WorkManagerSetup` | `androidMain/.../platform/WorkManagerSetup.kt` | ✅ Complete; periodic job + constraints |
| G4 | DI updates | `androidMain/.../di/DumpPlatformModule.android.kt` | ✅ Done; call setupRecordingProcessingWorker at init |

**Test Plan:**
- Robolectric: enqueue recording → WM runs → asserts process called
- Unique work: two calls with same ID → only one in queue
- Constraints: network required, verify set correctly

**Blocking:** Needs F complete

---

### Phase H: iOS Background Scheduler
**Status:** ❌ Not Started  
**Deliverable:** Minimum foreground flush; optional BGProcessingTask  
**Why Here:** iOS counterpart to Android WM; separate implementation path

**Classes to Implement:**

| # | Class | File | Task |
|---|-------|------|------|
| H1 | `IosBackgroundWorkScheduler` | `iosMain/.../platform/IosBackgroundWorkScheduler.kt` | Hook into app foreground event → call engine.process() for eligible recordings |
| H2 | (optional) | Same file | Implement BGProcessingTask if time permits (v2 stretch) |

**Test Plan:**
- Simulate app foreground → asserts eligible recordings processed
- Manual device test: background processing behavior

**Blocking:** Needs F complete

---

### Phase I: Presentation Layer Updates
**Status:** ❌ Not Started  
**Deliverable:** ViewModel + UI updates to show processing state  
**Why Here:** UI observes DB; DB is fully functional now

**Classes to Implement:**

| # | Class | File | Task |
|---|-------|------|------|
| I1 | `RetryRecordingProcessingUseCase` | `domainMain/.../usecase/RetryRecordingProcessingUseCase.kt` | Implement: FAILED → PENDING, clear error code, reset background_wm_attempts |
| I2 | `DumpViewModel` | `presentationMain/.../viewmodel/DumpViewModel.kt` | Remove old insight generation flow; add `RetryProcessing` intent; call `processingManager.enqueueRecording()` after save |
| I3 | `DumpViewState` | `presentationMain/.../state/DumpViewState.kt` | Split `recordings` → `processingRecordings` + `completedRecordings` |
| I4 | `RecordingUiModel` | `presentationMain/.../state/RecordingUiModel.kt` | Add `processingStatus`, `errorMessage` for UI display |
| I5 | `DumpRecordingScreen` | `presentationMain/.../screen/DumpRecordingScreen.kt` | Add "PROCESSING" section at top; status-aware card rendering |
| I6 | `RecordingListItem` | `presentationMain/.../screen/RecordingListItem.kt` | Show spinner for TRANSCRIBING/GENERATING_INSIGHT; error message + Retry button for FAILED |

**Test Plan:**
- ViewModel: save recording → calls enqueueRecording() with correct ID
- State split: processingRecordings shows only PENDING/TRANSCRIBING/GENERATING_INSIGHT
- Retry intent: calls use case + re-enqueues
- UI: TRANSCRIBING card shows spinner; FAILED card shows error + Retry button

**Blocking:** Needs F complete; DB + processing all working

---

### Phase J: E2E Testing & QA
**Status:** ❌ Not Started  
**Deliverable:** Manual test plan document  
**Why Last:** Done after all code works

**Classes to Implement:**
None (QA checklist)

**Test Plan:**
- Record → save → processing starts (Processing section visible)
- Navigate away → come back → processing continues (not tied to ViewModel)
- Offline → fail → FAILED shown → Retry → completes (online again)
- WM runs periodically → picks up PENDING recordings
- Rate limit → show message + remaining calls

---

## Dependency Diagram (Visual)

```
Phase A: Domain Models ✅
    ↓
Phase B: DB Schema ❌
    ├─→ Phase C: Repositories ❌
    │   ├─→ Phase F: Engine ❌
    │   │   ├─→ Phase G: WorkManager (Android) ❌
    │   │   ├─→ Phase H: iOS Scheduler ❌
    │   │   └─→ Phase I: Presentation ❌
    │   └─→ Phase I (depends on C too)
    │
    ├─→ Phase D: On-Device STT ❌ (parallel with C)
    │   └─→ Phase F
    │
    ├─→ Phase E: Insight Service ❌ (parallel with C)
    │   └─→ Phase F
    │
    └─→ Phase J: QA ❌ (last)
```

---

## Recommended Start Order (Today)

1. **Phase B — SQLDelight** (2-3 hours)
   - Add columns + queries
   - Verify schema compiles
   
2. **Phase C — Repositories** (2-3 hours)
   - Implement datasource + repository methods
   - Integration test: full state flow
   
3. **Phase D & E — Parallel** (4-6 hours combined)
   - D: Android STT (2-3h), then iOS (1-2h)
   - E: Refactor existing services to implement `InsightPort` (1h)
   
4. **Phase F — Engine** (4-6 hours)
   - Core FSM logic (2-3h)
   - Single-flight mutex (1h)
   - Checkpoint logic (1h)
   - Unit tests (1-2h)
   
5. **Phase G — WorkManager** (1-2 hours)
   - Verify worker implementation + DI
   - Robolectric test
   
6. **Phase H — iOS** (2-3 hours)
   - Foreground flush implementation
   - Manual device test

7. **Phase I — UI** (3-4 hours)
   - ViewModel refactor
   - State split
   - Composables for processing section
   - E2E manual test

8. **Phase J — QA** (2-3 hours)
   - Document manual checklist
   - Test offline/online matrix

---

## Critical Path (Fastest Route)

**~4 days of focused work** (assuming full-time):
- Day 1: B + C
- Day 2: D + E
- Day 3: F (core logic)
- Day 4: G + H + I
- Remaining: J + polish

---

## Unblocked Work (Can Start Now)

If you want to parallelize:
- **Phase D (On-Device STT)** can start alongside B/C (just provides interfaces)
- **Phase E (Insight Service)** can start alongside B/C (refactor existing)
- **Phase J (QA Checklist)** can be written now (high-level scenarios)
