# Native Transcription Implementation Plan

## Overview

Sanctuary implements real-time speech-to-text transcription during audio recording on both Android and iOS platforms. The implementation uses platform-native speech recognition APIs to capture user speech while recording audio files.

## Architecture

### Layered Design

The transcription system follows a clean layered architecture across data, domain, and platform layers:

```
Presentation Layer (ViewModel)
    ↓
Domain Layer (TranscriptionRepository interface)
    ↓
Data Layer (Repository implementation + DataSource)
    ↓
Platform Layer (Native managers)
    ↓
Native APIs (SpeechRecognizer / SFSpeechRecognizer)
```

### Component Breakdown

#### Platform-Specific Managers

**Audio Recording Manager** (`AndroidMediaRecordingManager` / `IosMediaRecordingManager`)
- Handles MediaRecorder lifecycle (start, stop, cancel)
- Provides real-time amplitude monitoring via `amplitudeFlow()`
- Manages audio session setup and teardown
- File path and state management

**Speech Recognition Manager** (`AndroidSpeechRecognitionManager` / `IosSpeechRecognitionManager`)
- Concurrent session management with token-based lifecycle
- Thread-safe transcript caching via `AtomicReference<String?>`
- Handles partial and final recognition results
- Graceful error handling and state cleanup

**Audio Recorder** (`AndroidAudioRecorder` / `IosAudioRecorder`)
- Facade orchestrating recording + speech recognition
- Coordinates lifecycle between both managers
- Handles cancellation signals (clear transcript on cancel)

#### Data Layer

**Transcription DataSource** (`AndroidTranscriptionDataSource` / `IosTranscriptionDataSource`)
- Polls speech recognition manager with 100ms intervals
- 3-second timeout window for capturing transcript
- Returns `Result<String>` type for error handling

**Transcription Repository** (`AndroidTranscriptionRepositoryImpl` / `IosTranscriptionRepositoryImpl`)
- Wraps datasource in domain-layer `UsecaseResult<String, Throwable>`
- Converts platform results to domain models

## Platform Implementations

### Android

**Dependencies:**
- `android.speech.SpeechRecognizer` - native speech recognition
- `android.media.MediaRecorder` - audio recording
- `android.content.Context` - system services

**Key Features:**
- `SpeechRecognizer.createSpeechRecognizer(context)` - speech API
- `RecognitionListener` callbacks - handles partial/final results
- Offline-first preference via `EXTRA_PREFER_OFFLINE`
- Free-form language model (`LANGUAGE_MODEL_FREE_FORM`)

**File Structure:**
```
feature_dump/src/androidMain/kotlin/sanctuary/app/feature/dump/
├── platform/
│   ├── AndroidAudioRecorder.kt
│   ├── AndroidMediaRecordingManager.kt
│   ├── AndroidSpeechRecognitionManager.kt
│   └── AndroidAudioSessionManager.kt
├── data/
│   ├── datasource/AndroidTranscriptionDataSource.kt
│   ├── repository/AndroidTranscriptionRepositoryImpl.kt
│   └── di/DumpDataModule.android.kt
└── di/DumpPlatformModule.android.kt
```

### iOS

**Dependencies:**
- `Speech.framework` - `SFSpeechRecognizer` for transcription
- `AVFoundation.framework` - `AVAudioRecorder` for recording
- `AVFAudio` - audio session management

**Key Features:**
- `SFSpeechRecognizer` with `SFSpeechAudioBufferRecognitionRequest`
- Auto-updating locale support
- Metering-enabled recording for amplitude extraction
- Completion handler-based result callbacks

**File Structure:**
```
feature_dump/src/iosMain/kotlin/sanctuary/app/feature/dump/
├── platform/
│   ├── IosAudioRecorder.kt
│   ├── IosMediaRecordingManager.kt
│   ├── IosSpeechRecognitionManager.kt
│   └── IosAudioSessionManager.kt
├── data/
│   ├── datasource/IosTranscriptionDataSource.kt
│   ├── repository/IosTranscriptionRepositoryImpl.kt
│   └── di/DumpDataModule.ios.kt
└── di/DumpPlatformModule.ios.kt
```

## Concurrency & Thread Safety

### Synchronization Strategy

**Android:**
- `Mutex` for serializing speech recognizer creation/destruction
- Token-based request invalidation to handle rapid start/stop calls
- `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` for UI-thread operations
- `AtomicReference<String?>` for lock-free transcript reads

**iOS:**
- `Mutex` for thread-safe recorder state management
- Token-based lifecycle management
- `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` for main-thread APIs
- `AtomicReference<String?>` for transcript caching

### Lifecycle Tokens

Both platforms use atomic token-increment pattern to invalidate old operations:

```kotlin
val token = recognizerToken.incrementAndGet()
// ... async operation captures token
// At callback time, only proceed if token matches current value
if (token != recognizerToken.get()) return
```

This prevents race conditions where old async operations complete after a new session has started.

## Dependency Injection

### Koin Module Configuration

**Android Data Module:**
```kotlin
single<TranscriptionRepository> {
    AndroidTranscriptionRepositoryImpl(
        AndroidTranscriptionDataSource(get<AndroidSpeechRecognitionManager>())
    )
}
```

**Android Platform Module:**
```kotlin
single { AndroidMediaRecordingManager(androidContext()) }
single { AndroidSpeechRecognitionManager(androidContext()) }
single { AndroidAudioRecorder(get(), get()) }
```

**iOS Data Module:**
```kotlin
single<TranscriptionRepository> {
    IosTranscriptionRepositoryImpl(
        IosTranscriptionDataSource(get<IosSpeechRecognitionManager>())
    )
}
```

**iOS Platform Module:**
```kotlin
single { IosAudioSessionManager() }
single { IosMediaRecordingManager(get()) }
single { IosSpeechRecognitionManager() }
single<AudioRecorder> { IosAudioRecorder(get(), get()) }
```

## Error Handling & Timeouts

### Timeout Mechanism

Both platforms implement a 3-second timeout in the transcription datasource:

```kotlin
val deadline = System.currentTimeMillis() + 3000L
while (System.currentTimeMillis() < deadline) {
    val t = speechRecognitionManager.getTranscript()
    if (t != null) return@withContext Result.success(t)
    delay(100)
}
Result.failure(TimeoutException("Failed to get transcript within 3 seconds"))
```

### Error States

- **No transcript available**: Returns timeout error after 3 seconds
- **Speech recognition unavailable**: Manager handles gracefully, returns null transcripts
- **Recording failure**: Audio session cleanup triggered, session marked inactive
- **Cancellation**: Clears transcript and listeners, deletes audio file

## Usage Flow

### Recording with Transcription

1. **Start Recording:**
   - User initiates dump recording
   - `AudioRecorder.startRecording(filePath)` called
   - Both `MediaRecordingManager` and `SpeechRecognitionManager` start
   - Amplitude monitoring begins

2. **Live Transcription:**
   - As user speaks, speech recognizer captures partial results
   - `AtomicReference` is updated with latest transcript
   - UI can poll or observe amplitude/transcript in real-time

3. **Stop Recording:**
   - User completes recording
   - `AudioRecorder.stopRecording()` called
   - Recording manager finalizes file
   - Speech recognition stops gracefully (stopListening, not cancel)
   - Transcript preserved for repository access

4. **Transcription Retrieval:**
   - `TranscriptionRepository.transcribe(filePath)` called
   - Datasource polls speech manager with 3-second timeout
   - Returns final transcript wrapped in `UsecaseResult`

5. **Cancellation (Optional):**
   - `AudioRecorder.cancelRecording()` clears transcript
   - Recording manager deletes audio file
   - Speech recognition cancelled with transcript cleared

## Limitations & Known Behaviors

### Android
- Requires `RECORD_AUDIO` and `INTERNET` permissions
- Speech recognition requires active SpeechRecognizer (may not be available on all devices)
- Offline recognition preferred via `EXTRA_PREFER_OFFLINE`, but online fallback available

### iOS
- Requires `NSMicrophoneUsageDescription` in Info.plist
- Requires `NSSpeechRecognitionUsageDescription` in Info.plist
- `SFSpeechRecognizer` availability varies by locale
- Completion handler-based API requires proper lifecycle management

## Testing Considerations

- Mock managers can substitute real platform APIs for UI testing
- Result types provide clear success/failure branches for unit tests
- Token-based lifecycle prevents timing-related flakiness
- Timeout errors are explicit and testable

## Future Enhancements

- [ ] Support for multiple languages per session
- [ ] Confidence score tracking for partial results
- [ ] Audio format customization (currently AAC/MPEG4)
- [ ] Batch transcription for multiple audio files
- [ ] Fallback transcription service (cloud-based)
- [ ] Real-time transcript display in UI
- [ ] Transcript editing/correction UI
