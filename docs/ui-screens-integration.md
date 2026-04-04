# UI Screens Integration - Phase 10 Complete

## Overview
Integrated three new insight screens into the dump feature's navigation flow with state management and side effects.

## Files Created

### UI Screens
1. **InsightGenerationScreen** (`feature_dump/src/presentationMain/.../InsightGenerationScreen.kt`)
   - Loading screen shown while AI generates insight
   - Circular progress indicator with descriptive text
   - Displayed when `InsightGenerationState = Loading`

2. **YourReflectionScreen** (`feature_dump/src/presentationMain/.../YourReflectionScreen.kt`)
   - Displays the generated insight with full details
   - Shows: title, summary, detailed insight, path forward, emotions
   - Action buttons: Save, View Recording, View Transcript, Regenerate
   - Displayed when `InsightGenerationState = Success`

3. **InsightHistoryScreen** (`feature_dump/src/presentationMain/.../InsightHistoryScreen.kt`)
   - Lists all user insights with search/filter
   - Shows insight cards with preview, emotions, date
   - 15-day deletion policy info message
   - Empty state when no insights
   - (Ready for future integration)

4. **InsightUiModel** (`feature_dump/src/presentationMain/.../InsightUiModel.kt`)
   - Data class for UI-friendly insight representation
   - Contains formatted date/time and sentiment color

### Container & Navigation
5. **DumpScreenContainer** (`feature_dump/src/presentationMain/.../DumpScreenContainer.kt`)
   - Main navigation container that switches between screens based on state
   - Routes to appropriate screen based on `InsightGenerationState`:
     - `Idle` → DumpRecordingScreen
     - `Loading` → InsightGenerationScreen
     - `Success` → YourReflectionScreen
     - `RateLimited` / `Error` → DumpRecordingScreen (with error handling)

## State Management Updates

### DumpDataState (Domain State)
```kotlin
enum class InsightGenerationState {
    Idle, Loading, Success, RateLimited, Error
}

// Added fields:
val insightGenerationState: InsightGenerationState
val currentInsight: Insight?
val generationError: String?
val rateLimitRemaining: Int?
```

### DumpViewState (UI State)
```kotlin
enum class DumpScreen {
    Recording, InsightGenerating, InsightDetail, InsightHistory
}

// Added fields:
val insightGenerationState: InsightGenerationState
val currentInsight: InsightUiModel?
val generationError: String?
val rateLimitRemaining: Int?
```

### DumpSideEffect (Navigation Events)
```kotlin
data class NavigateToInsightDetail(val insightId: String)
data class ShowInsightGenerationError(val message: String)
data class ShowRateLimitError(val remainingCalls: Int)
```

## ViewModel Integration

### DumpViewModel Changes
1. Added `Insight.toUiModel()` extension method
2. Added `getSentimentColor()` helper for sentiment-based UI
3. Updated `convertToUiState()` to map insight-related state
4. Side effect handling in DumpRecordingScreen updated for new effects

## Navigation Flow

```
DumpRecordingScreen
         ↓
    User finishes recording
         ↓
    [handleStopRecording()]
         ↓
    InsightGenerationState = Loading
    InsightGenerationScreen (loading UI)
         ↓
    AI generates insight (via Phase 9.3)
         ↓
    InsightGenerationState = Success
    YourReflectionScreen (displays insight)
         ↓
    User action (save, view, regenerate, etc)
         ↓
    Back to DumpRecordingScreen
```

## Integration Points (Still TODO)

These are wired in MentalDumpHomePlaceholder as stubs:
- `onSaveToJournal` - Save insight to journal feature
- `onViewRecording` - Play recording playback
- `onViewTranscription` - Show transcription details
- `onRegenerateInsight` - Trigger insight regeneration
- `onInsightClick` - Navigate to insight history detail
- `onFilterClick` - Filter/sort insights
- `onSearchClick` - Search insights

## Usage

The screens are automatically displayed based on ViewModel state. No manual routing needed:

```kotlin
DumpRecordingScreen(
    onNavigateBack = onNavigateBack,
    onViewAllRecordings = onViewAllRecordings,
    onRequestPermission = onRequestPermission,
)
// Internally uses DumpScreenContainer which handles screen switching
```

## Next Steps

- Implement Phase 9.3: Insight generation in handleStopRecording()
- Wire up action button callbacks (save, view recording, etc)
- Add InsightHistoryScreen integration with actual data
- Add error dialogs/snackbars for rate limit and error states
- Implement insight regeneration flow
