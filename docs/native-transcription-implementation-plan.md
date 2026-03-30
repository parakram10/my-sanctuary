# Plan: Replace Groq Transcription with Native Platform Speech APIs

## Context

The app currently uses Groq's Whisper API (`https://api.groq.com/openai/v1/audio/transcriptions`) for transcription, which requires an API key, a network call, and a `FileByteProvider` expect/actual that is broken on iOS (throws `NotImplementedError`). The goal is to replace this with native OS speech recognition APIs:
- **Android**: `SpeechRecognizer` — live transcription running concurrently during recording
- **iOS**: `SFSpeechRecognizer` — file-based transcription after recording using `SFSpeechURLRecognitionRequest`

`TranscriptionRepository` interface and `SaveRecordingWithTranscriptionUseCase` remain **unchanged**. The ViewModel and domain layer require **zero changes**.

---

## Phase 1: Delete Groq Infrastructure

Delete these files:
- `feature_dump/src/dataMain/kotlin/.../datasource/TranscriptionRemoteDataSourceImpl.kt`
- `feature_dump/src/dataMain/kotlin/.../datasource/TranscriptionConfig.kt`
- `feature_dump/src/dataMain/kotlin/.../datasource/FileByteProvider.kt` (expect class)
- `feature_dump/src/dataMain/androidMain/kotlin/.../datasource/FileByteProvider.android.kt`
- `feature_dump/src/dataMain/iosMain/kotlin/.../datasource/FileByteProvider.ios.kt`
- `feature_dump/src/dataMain/kotlin/.../repository/TranscriptionRepositoryImpl.kt`
- `core_network/src/commonMain/kotlin/.../model/GroqTranscriptionResponse.kt`

---

## Phase 2: Update DI Pattern in `DumpDataModule.kt`

Replace the current single common `TranscriptionRepositoryImpl` binding with a platform-split `expect val` module pattern (same pattern as `providePlatformFileByteProvider` but as an includable Koin module so platform impls can use `get()`).

**`feature_dump/src/dataMain/kotlin/.../di/DumpDataModule.kt`** — remove Groq imports, `FileByteProvider` binding, `coreNetworkModule` include, and the `TranscriptionRepositoryImpl` binding. Replace with:

```kotlin
val dumpDataModule = module {
    singleOf(::RecordingLocalDataSourceImpl) bind RecordingLocalDataSource::class
    singleOf(::RecordingRepositoryImpl) bind RecordingRepository::class
    includes(platformTranscriptionModule)
}

internal expect val platformTranscriptionModule: Module
```

Remove the `expect fun providePlatformFileByteProvider()` declaration too.

---

## Phase 3: Android — Live `SpeechRecognizer` During Recording

### Why live?
Android's `SpeechRecognizer` does **not** support file-based input — it only captures live audio from the mic. So transcription must run concurrently alongside `MediaRecorder` during the recording session.

### 3a. Expand `AndroidAudioRecorder`
File: `feature_dump/src/androidMain/kotlin/.../platform/AndroidAudioRecorder.kt`

Add alongside the existing `MediaRecorder` logic:
- `private val transcriptHolder = AtomicReference<String?>(null)`
- `private var speechRecognizer: SpeechRecognizer? = null`
- In `startRecording()`: post to main looper → `createSpeechRecognizer(context)`, set `RecognitionListener`, call `startListening()` with `LANGUAGE_MODEL_FREE_FORM`, `EXTRA_PARTIAL_RESULTS = true`, `EXTRA_PREFER_OFFLINE = true`
- `RecognitionListener.onPartialResults` / `onResults` → write best result string to `transcriptHolder`
- In `stopRecording()`: post to main looper → `speechRecognizer?.stopListening()` → `destroy()`
- In `cancelRecording()`: post to main looper → `speechRecognizer?.cancel()` → `destroy()` + clear `transcriptHolder`
- Add `fun getTranscript(): String? = transcriptHolder.get()`

> `SpeechRecognizer` must be created and called on the main thread. Use `Handler(Looper.getMainLooper()).post { ... }` for all interactions.

### 3b. Update `DumpPlatformModule.android.kt`
Register `AndroidAudioRecorder` as **both** its concrete type and the `AudioRecorder` interface, so the transcription data source can receive the same singleton:

```kotlin
single { AndroidAudioRecorder(androidContext()) }       // concrete — for transcription DS
single<AudioRecorder> { get<AndroidAudioRecorder>() }  // interface — for ViewModel (unchanged)
```

### 3c. Create `AndroidTranscriptionDataSource.kt`
File: `feature_dump/src/dataMain/androidMain/kotlin/.../datasource/AndroidTranscriptionDataSource.kt`

Polls `recorder.getTranscript()` on `Dispatchers.IO` for up to 3 seconds. This handles the async gap between calling `stopListening()` and the `onResults` callback firing. The poll must run on `IO` (not `Main`) so `onResults` can fire on the main thread concurrently.

```kotlin
internal class AndroidTranscriptionDataSource(
    private val recorder: AndroidAudioRecorder
) {
    suspend fun getTranscript(): Result<String> = withContext(Dispatchers.IO) {
        val deadline = SystemClock.elapsedRealtime() + 3000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val t = recorder.getTranscript()
            if (t != null) return@withContext Result.success(t)
            delay(100)
        }
        Result.failure(TimeoutException("Speech recognition timed out"))
    }
}
```

### 3d. Create `AndroidTranscriptionRepositoryImpl.kt`
File: `feature_dump/src/dataMain/androidMain/kotlin/.../repository/AndroidTranscriptionRepositoryImpl.kt`

```kotlin
internal class AndroidTranscriptionRepositoryImpl(
    private val dataSource: AndroidTranscriptionDataSource
) : TranscriptionRepository {
    override suspend fun transcribe(filePath: String): UsecaseResult<String, Throwable> =
        dataSource.getTranscript().fold(
            onSuccess = { UsecaseResult.Success(it) },
            onFailure = { UsecaseResult.Failure(it) }
        )
}
```

`filePath` is intentionally ignored — the transcript was already captured live during recording.

### 3e. Create `DumpDataModule.android.kt`
File: `feature_dump/src/dataMain/androidMain/kotlin/.../di/DumpDataModule.android.kt`

```kotlin
internal actual val platformTranscriptionModule = module {
    single<TranscriptionRepository> {
        AndroidTranscriptionRepositoryImpl(
            AndroidTranscriptionDataSource(get<AndroidAudioRecorder>())
        )
    }
}
```

---

## Phase 4: iOS — File-Based `SFSpeechRecognizer` After Recording

iOS's `SFSpeechRecognizer` supports file-based recognition via `SFSpeechURLRecognitionRequest`, so we can simply process the saved `.m4a` file after `stopRecording()` returns — no changes to `IosAudioRecorder`.

### 4a. Create `IosSpeechRecognizerDataSource.kt`
File: `feature_dump/src/dataMain/iosMain/kotlin/.../datasource/IosSpeechRecognizerDataSource.kt`

```kotlin
internal class IosSpeechRecognizerDataSource {
    suspend fun transcribe(filePath: String): Result<String> = runCatching {
        // 1. Request authorization if not already granted
        if (SFSpeechRecognizer.authorizationStatus() != SFSpeechRecognizerAuthorizationStatusAuthorized) {
            val granted = suspendCancellableCoroutine { cont ->
                SFSpeechRecognizer.requestAuthorization { status ->
                    cont.resume(status == SFSpeechRecognizerAuthorizationStatusAuthorized)
                }
            }
            if (!granted) throw SecurityException("Speech recognition permission denied")
        }
        // 2. Recognize from recorded file
        suspendCancellableCoroutine { cont ->
            val recognizer = SFSpeechRecognizer()
                ?: throw IllegalStateException("SFSpeechRecognizer unavailable for current locale")
            val request = SFSpeechURLRecognitionRequest(NSURL.fileURLWithPath(filePath))
            request.shouldReportPartialResults = false
            val task = recognizer.recognitionTaskWithRequest(request) { result, error ->
                when {
                    error != null -> cont.resumeWithException(RuntimeException(error.localizedDescription))
                    result != null && result.isFinal -> cont.resume(result.bestTranscription.formattedString)
                }
            }
            cont.invokeOnCancellation { task.cancel() }
        }
    }
}
```

### 4b. Create `IosTranscriptionRepositoryImpl.kt`
File: `feature_dump/src/dataMain/iosMain/kotlin/.../repository/IosTranscriptionRepositoryImpl.kt`

```kotlin
internal class IosTranscriptionRepositoryImpl(
    private val dataSource: IosSpeechRecognizerDataSource
) : TranscriptionRepository {
    override suspend fun transcribe(filePath: String): UsecaseResult<String, Throwable> =
        dataSource.transcribe(filePath).fold(
            onSuccess = { UsecaseResult.Success(it) },
            onFailure = { UsecaseResult.Failure(it) }
        )
}
```

### 4c. Create `DumpDataModule.ios.kt`
File: `feature_dump/src/dataMain/iosMain/kotlin/.../di/DumpDataModule.ios.kt`

```kotlin
internal actual val platformTranscriptionModule = module {
    single<TranscriptionRepository> {
        IosTranscriptionRepositoryImpl(IosSpeechRecognizerDataSource())
    }
}
```

### 4d. Update `iosApp/iosApp/Info.plist`
`SFSpeechRecognizer` requires a usage description key in the iOS app's `Info.plist`:

```xml
<key>NSSpeechRecognitionUsageDescription</key>
<string>Sanctuary uses speech recognition to transcribe your voice recordings.</string>
```

---

## Files Summary

| Action | File |
|--------|------|
| DELETE | `dataMain/.../datasource/TranscriptionRemoteDataSourceImpl.kt` |
| DELETE | `dataMain/.../datasource/TranscriptionConfig.kt` |
| DELETE | `dataMain/.../datasource/FileByteProvider.kt` + `.android.kt` + `.ios.kt` |
| DELETE | `dataMain/.../repository/TranscriptionRepositoryImpl.kt` |
| DELETE | `core_network/.../model/GroqTranscriptionResponse.kt` |
| MODIFY | `dataMain/.../di/DumpDataModule.kt` — replace with `expect val platformTranscriptionModule` |
| MODIFY | `androidMain/.../platform/AndroidAudioRecorder.kt` — add `SpeechRecognizer` + `getTranscript()` |
| MODIFY | `androidMain/.../di/DumpPlatformModule.android.kt` — add concrete `AndroidAudioRecorder` binding |
| CREATE | `dataMain/androidMain/.../datasource/AndroidTranscriptionDataSource.kt` |
| CREATE | `dataMain/androidMain/.../repository/AndroidTranscriptionRepositoryImpl.kt` |
| CREATE | `dataMain/androidMain/.../di/DumpDataModule.android.kt` |
| CREATE | `dataMain/iosMain/.../datasource/IosSpeechRecognizerDataSource.kt` |
| CREATE | `dataMain/iosMain/.../repository/IosTranscriptionRepositoryImpl.kt` |
| CREATE | `dataMain/iosMain/.../di/DumpDataModule.ios.kt` |
| MODIFY | `iosApp/iosApp/Info.plist` — add `NSSpeechRecognitionUsageDescription` |

**No changes to:** `TranscriptionRepository.kt`, `SaveRecordingWithTranscriptionUseCase.kt`, `TranscribeAudioUseCase.kt`, `DumpViewModel.kt`, `IosAudioRecorder.kt`

---

## Timing & Edge Case Notes

- **Android timing gap**: `stopListening()` is async — `onResults` fires 100–500ms later. The 3s poll in `AndroidTranscriptionDataSource` covers this. Poll is on `Dispatchers.IO` so it doesn't block main thread where `onResults` fires.
- **Android: no match / silence**: `onError(ERROR_NO_MATCH)` leaves `transcriptHolder` null → poll times out → `updateTranscriptionFailure` in DB. Correct behavior, no crash.
- **Android: network dependency**: Default `SpeechRecognizer` may route to Google servers. `EXTRA_PREFER_OFFLINE = true` requests on-device where available (Android 10+). On-device model availability varies by device.
- **iOS: `AVAudioSession` conflict**: `IosAudioRecorder.stopRecording()` deactivates the audio session. `SFSpeechURLRecognitionRequest` is file-based and does not require an active audio session. No conflict.
- **iOS locale**: `SFSpeechRecognizer()` uses device locale. Recognition quality degrades if spoken language differs from device locale — future enhancement.

---

## Verification Steps

1. **Android**: Build and install on device. Record a clear voice note → stop → wait a few seconds → recording entry should show transcript text.
2. **iOS**: Build via Xcode. First transcription triggers a system permission prompt for speech recognition. After granting, record → stop → transcript appears.
3. **Silent/short recordings**: Expect `FAILED` status in the recording list with no crash.
4. **Cancel recording**: Transcript should not appear; `transcriptHolder` is cleared on cancel.
