# AI-Powered Insights Feature - Granular Implementation Plan

## Overview

This document breaks down the insight generation feature into **25 focused micro-phases**, each with a single, well-defined deliverable. This approach enables:

- ✅ Better understanding of each component
- ✅ Incremental implementation and testing
- ✅ Clear progress tracking
- ✅ Easier debugging and verification
- ✅ Lower cognitive load per phase

---

## Phase Structure

Each phase includes:
- **Objective**: What we're building (1 sentence)
- **Deliverable**: Specific files/changes
- **Test Cases**: 2-3 focused tests
- **Verification**: How to confirm it works
- **Dependencies**: Phases that must complete first

---

## PHASE GROUP 1: Database Schema (Phases 1.1 - 1.6)

### Phase 1.1: Create Insights Table Schema

**Objective**: Define the database schema for insights storage.

**Deliverable**:
- Create `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/insights.sq`

**Content**:
```sql
CREATE TABLE insights (
    id TEXT NOT NULL PRIMARY KEY,
    recording_id TEXT NOT NULL,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    full_summary TEXT NOT NULL,
    emotions_json TEXT NOT NULL,
    path_forward TEXT NOT NULL,
    recording_type TEXT NOT NULL,
    sentiment TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    is_archived INTEGER DEFAULT 0,
    archived_at INTEGER,
    status TEXT NOT NULL,
    FOREIGN KEY (recording_id) REFERENCES recordings(id) ON DELETE CASCADE
);

selectAll:
SELECT * FROM insights WHERE is_archived = 0 ORDER BY created_at DESC;

selectById:
SELECT * FROM insights WHERE id = ?;

selectByRecordingId:
SELECT * FROM insights WHERE recording_id = ?;

selectArchived:
SELECT * FROM insights WHERE is_archived = 1 ORDER BY archived_at DESC;

insert:
INSERT INTO insights (id, recording_id, title, summary, full_summary, emotions_json, path_forward, recording_type, sentiment, created_at, is_archived, status)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateStatus:
UPDATE insights SET status = ? WHERE id = ?;

updateIsArchived:
UPDATE insights SET is_archived = ?, archived_at = ? WHERE id = ?;

deleteById:
DELETE FROM insights WHERE id = ?;

deleteOlderThan:
DELETE FROM insights WHERE is_archived = 0 AND created_at < ?;
```

**Test Cases**:
- [ ] SQLDelight generates query code without errors
- [ ] Schema includes all required columns
- [ ] Foreign key constraint defined correctly

**Verification**:
```bash
./gradlew :core_database:build
# Check generated code in core_database/build/generated/sqldelight/
```

**Dependencies**: None

---

### Phase 1.2: Create Rate Limits Table Schema

**Objective**: Define schema for tracking API rate limits.

**Deliverable**:
- Create `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/rate_limits.sq`

**Content**:
```sql
CREATE TABLE rate_limits (
    id TEXT NOT NULL PRIMARY KEY,
    date INTEGER NOT NULL,
    api_calls_used INTEGER DEFAULT 0,
    max_api_calls INTEGER DEFAULT 4
);

getByDate:
SELECT * FROM rate_limits WHERE date = ?;

insert:
INSERT INTO rate_limits (id, date, api_calls_used, max_api_calls)
VALUES (?, ?, ?, ?);

updateCallsUsed:
UPDATE rate_limits SET api_calls_used = ? WHERE date = ?;

deleteByDate:
DELETE FROM rate_limits WHERE date = ?;
```

**Test Cases**:
- [ ] Table created with correct columns
- [ ] Default values set correctly (api_calls_used=0, max_api_calls=4)
- [ ] Query code generated without errors

**Verification**:
```bash
./gradlew :core_database:build
```

**Dependencies**: None

---

### Phase 1.3: Create Request Queue Table Schema

**Objective**: Define schema for queuing pending insight generation requests.

**Deliverable**:
- Create `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/request_queue.sq`

**Content**:
```sql
CREATE TABLE request_queue (
    id TEXT NOT NULL PRIMARY KEY,
    recording_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    status TEXT NOT NULL,
    error_message TEXT,
    FOREIGN KEY (recording_id) REFERENCES recordings(id) ON DELETE CASCADE
);

selectPending:
SELECT * FROM request_queue WHERE status = 'PENDING' ORDER BY created_at ASC;

selectAll:
SELECT * FROM request_queue ORDER BY created_at DESC;

insert:
INSERT INTO request_queue (id, recording_id, created_at, status)
VALUES (?, ?, ?, ?);

updateStatus:
UPDATE request_queue SET status = ?, error_message = ?, retry_count = ? WHERE id = ?;

deleteById:
DELETE FROM request_queue WHERE id = ?;
```

**Test Cases**:
- [ ] Table created with cascade delete on recording_id
- [ ] PENDING status queries work correctly
- [ ] Default retry counts set (0, 3)

**Verification**:
```bash
./gradlew :core_database:build
```

**Dependencies**: None

---

### Phase 1.4: Create Database Migrations

**Objective**: Define migration scripts for backward compatibility.

**Deliverable**:
- Create `2.sqm` - Add is_archived to recordings
- Create `3.sqm` - Create insights, rate_limits, request_queue tables

**Test Cases**:
- [ ] Migration 2 applies without errors
- [ ] Migration 3 applies without errors
- [ ] All three tables exist after migrations

**Verification**:
```bash
./gradlew :core_database:build
# Verify migrations in core_database/build/generated/sqldelight/
```

**Dependencies**: Phases 1.1, 1.2, 1.3

---

### Phase 1.5: Update Recordings Table Schema

**Objective**: Add is_archived column to existing recordings table.

**Deliverable**:
- Update `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/recordings.sq`
- Add `is_archived INTEGER DEFAULT 0` column
- Update Recording model to include isArchived field

**Test Cases**:
- [ ] Existing queries still work
- [ ] is_archived defaults to 0
- [ ] Recording mapper handles new field

**Verification**:
```bash
./gradlew :core_database:build
./gradlew :composeApp:assembleDebug
```

**Dependencies**: Phase 1.4

---

### Phase 1.6: Database Build Verification

**Objective**: Verify all database changes compile and integrate correctly.

**Deliverable**:
- No new code, just verification

**Test Cases**:
- [ ] `./gradlew :core_database:build` succeeds
- [ ] `./gradlew :composeApp:assembleDebug` succeeds
- [ ] No compilation errors in generated SQLDelight code

**Verification**:
```bash
./gradlew :core_database:build --info
./gradlew :composeApp:assembleDebug
```

**Dependencies**: Phases 1.1-1.5

---

## PHASE GROUP 2: Security & Encryption (Phases 2.1 - 2.4)

### Phase 2.1: Create Common PassphraseManager Interface

**Objective**: Define expect/actual pattern for secure passphrase management.

**Deliverable**:
- Create `core_database/src/commonMain/kotlin/sanctuary/app/core/database/security/PassphraseManager.kt`

**Content**:
```kotlin
package sanctuary.app.core.database.security

expect object PassphraseManager {
    fun getOrCreatePassphrase(): String
}
```

**Test Cases**:
- [ ] Expect class compiles
- [ ] Interface definition clear

**Verification**:
```bash
./gradlew :core_database:compileCommonMainKotlinMetadata
```

**Dependencies**: None

---

### Phase 2.2: Implement Android PassphraseManager

**Objective**: Store passphrases securely in Android Keystore.

**Deliverable**:
- Create `core_database/src/androidMain/kotlin/sanctuary/app/core/database/security/PassphraseManager.android.kt`
- Add `androidx-security-crypto` to `gradle/libs.versions.toml`

**Content**:
- Use EncryptedSharedPreferences with Android Keystore
- Generate 32-character passphrase on first call
- Reuse passphrase on subsequent calls

**Test Cases**:
- [ ] Passphrase generated successfully
- [ ] Passphrase persists across calls
- [ ] Uses EncryptedSharedPreferences correctly
- [ ] Passphrase is 32 characters

**Verification**:
```bash
./gradlew :core_database:compileDebugKotlinAndroid
# Test on Android emulator or device
```

**Dependencies**: Phase 2.1

---

### Phase 2.3: Implement iOS PassphraseManager

**Objective**: Create placeholder for iOS Keychain integration.

**Deliverable**:
- Create `core_database/src/iosMain/kotlin/sanctuary/app/core/database/security/PassphraseManager.ios.kt`

**Content**:
- In-memory passphrase generation per session
- Placeholders and documentation for future Keychain integration
- Reference implementation in comments

**Test Cases**:
- [ ] Passphrase generated successfully
- [ ] Passphrase is 32 characters
- [ ] Compiles for iOS targets

**Verification**:
```bash
./gradlew :core_database:compileKotlinIosArm64
```

**Dependencies**: Phase 2.1

---

### Phase 2.4: Create Database Encryption Factory

**Objective**: Define platform-agnostic driver creation with encryption.

**Deliverable**:
- Create `core_database/src/commonMain/kotlin/sanctuary/app/core/database/db/DatabaseFactory.kt`
- Define `expect fun createDriver()`
- Define `fun createEncryptedDatabase()`

**Test Cases**:
- [ ] Expect function defined
- [ ] Android actual implementation compiles
- [ ] iOS actual implementation compiles

**Verification**:
```bash
./gradlew :core_database:build
```

**Dependencies**: Phases 2.1, 2.2, 2.3

---

## PHASE GROUP 3: Domain Models (Phases 3.1 - 3.5)

### Phase 3.1: Create Insight Domain Model

**Objective**: Define domain model for insights.

**Deliverable**:
- Create `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/model/Insight.kt`

**Content**:
```kotlin
@Serializable
data class InsightContent(
    val title: String,
    val summary: String,
    val fullSummary: String,
    val emotions: List<String>,
    val pathForward: String,
    val recordingType: String,
    val sentiment: Sentiment,
)

enum class Sentiment {
    POSITIVE, NEGATIVE, NEUTRAL
}

data class Insight(
    val id: String,
    val recordingId: String,
    val content: InsightContent,
    val createdAt: Long,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val status: InsightStatus = InsightStatus.SAVED,
)

enum class InsightStatus {
    PENDING, GENERATING, SAVED, ARCHIVED
}
```

**Test Cases**:
- [ ] Insight created with all fields
- [ ] Sentiment enum has 3 values
- [ ] InsightContent serializable to/from JSON
- [ ] InsightStatus enum valid

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: None

---

### Phase 3.2: Create RateLimit Domain Model

**Objective**: Define model for rate limiting state.

**Deliverable**:
- Create `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/model/RateLimit.kt`

**Content**:
```kotlin
data class RateLimit(
    val id: String,
    val date: Long,
    val apiCallsUsed: Int,
    val maxApiCalls: Int = 4,
) {
    fun isLimitReached(): Boolean = apiCallsUsed >= maxApiCalls
    fun remainingCalls(): Int = (maxApiCalls - apiCallsUsed).coerceAtLeast(0)
}
```

**Test Cases**:
- [ ] RateLimit created with default maxApiCalls=4
- [ ] isLimitReached() returns true when apiCallsUsed >= maxApiCalls
- [ ] remainingCalls() calculated correctly

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: None

---

### Phase 3.3: Create InsightGenerationRequest Model

**Objective**: Define model for queued insight generation requests.

**Deliverable**:
- Create `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/model/InsightGenerationRequest.kt`

**Content**:
```kotlin
data class InsightGenerationRequest(
    val id: String,
    val recordingId: String,
    val transcription: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val status: RequestStatus = RequestStatus.PENDING,
    val errorMessage: String? = null,
)

enum class RequestStatus {
    PENDING, PROCESSING, FAILED, COMPLETED
}
```

**Test Cases**:
- [ ] Request created with default values
- [ ] Status enum has 4 values
- [ ] errorMessage nullable

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: None

---

### Phase 3.4: Create InsightGenerationResult Sealed Class

**Objective**: Define result type for insight generation outcomes.

**Deliverable**:
- Create `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/model/InsightGenerationResult.kt`

**Content**:
```kotlin
sealed class InsightGenerationResult {
    data class Success(val insight: Insight) : InsightGenerationResult()
    data class RateLimitExceeded(val remainingCalls: Int) : InsightGenerationResult()
    data class Failure(val error: String, val isRetryable: Boolean) : InsightGenerationResult()
}
```

**Test Cases**:
- [ ] Success variant contains Insight
- [ ] RateLimitExceeded contains remainingCalls
- [ ] Failure contains error and isRetryable flag

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phase 3.1

---

### Phase 3.5: Verify All Domain Models Compile

**Objective**: Ensure all domain models integrate correctly.

**Test Cases**:
- [ ] All domain model files compile
- [ ] No import errors in domain layer
- [ ] Models follow KMP best practices

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
./gradlew :feature_dump:compileKotlinIosArm64
```

**Dependencies**: Phases 3.1-3.4

---

## PHASE GROUP 4: Data Mappers (Phases 4.1 - 4.2)

### Phase 4.1: Create Insight Mapper

**Objective**: Map between Insight domain model and database entity.

**Deliverable**:
- Create `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/mapper/InsightMapper.kt`

**Content**:
```kotlin
internal fun Insights.toDomain(): Insight {
    val content = Json.decodeFromString<InsightContent>(emotions_json)
    return Insight(
        id = id,
        recordingId = recording_id,
        content = content,
        createdAt = created_at,
        isArchived = is_archived != 0L,
        archivedAt = archived_at,
        status = InsightStatus.valueOf(status),
    )
}

internal fun Insight.toEntity(): Insights {
    val emotionsJson = Json.encodeToString(content.emotions)
    return Insights(
        id = id,
        recording_id = recordingId,
        title = content.title,
        summary = content.summary,
        full_summary = content.fullSummary,
        emotions_json = emotionsJson,
        path_forward = content.pathForward,
        recording_type = content.recordingType,
        sentiment = content.sentiment.name,
        created_at = createdAt,
        is_archived = if (isArchived) 1L else 0L,
        archived_at = archivedAt,
        status = status.name,
    )
}
```

**Test Cases**:
- [ ] Entity to Domain: All fields map correctly
- [ ] Domain to Entity: All fields map correctly
- [ ] Emotions JSON serializes and deserializes correctly
- [ ] Sentiment enum converts to/from string
- [ ] is_archived INTEGER maps to Boolean

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phase 3.1

---

### Phase 4.2: Create RateLimit & RequestQueue Mappers

**Objective**: Map rate limit and request queue models to/from database entities.

**Deliverable**:
- Add to `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/mapper/InsightMapper.kt`

**Content**:
```kotlin
internal fun RateLimits.toDomain(): RateLimit = RateLimit(
    id = id,
    date = date,
    apiCallsUsed = api_calls_used.toInt(),
    maxApiCalls = max_api_calls.toInt(),
)

internal fun RateLimit.toEntity(): RateLimits = RateLimits(
    id = id,
    date = date,
    api_calls_used = apiCallsUsed.toLong(),
    max_api_calls = maxApiCalls.toLong(),
)

internal fun RequestQueues.toDomain(): InsightGenerationRequest = InsightGenerationRequest(
    id = id,
    recordingId = recording_id,
    transcription = "",
    createdAt = created_at,
    retryCount = retry_count.toInt(),
    maxRetries = max_retries.toInt(),
    status = RequestStatus.valueOf(status),
    errorMessage = error_message,
)

internal fun InsightGenerationRequest.toEntity(): RequestQueues = RequestQueues(
    id = id,
    recording_id = recordingId,
    created_at = createdAt,
    retry_count = retryCount.toLong(),
    max_retries = maxRetries.toLong(),
    status = status.name,
    error_message = errorMessage,
)
```

**Test Cases**:
- [ ] RateLimit maps correctly (include toInt/toLong conversions)
- [ ] RequestQueue maps correctly
- [ ] Null errorMessage handled properly

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phases 3.2, 3.3

---

## PHASE GROUP 5: Data Interfaces (Phases 5.1 - 5.2)

### Phase 5.1: Create InsightLocalDataSource Interface

**Objective**: Define contract for local insight storage operations.

**Deliverable**:
- Create `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/datasource/InsightLocalDataSource.kt`

**Content**:
```kotlin
internal interface InsightLocalDataSource {
    suspend fun insertInsight(insight: Insight)
    suspend fun getInsightById(id: String): Insight?
    suspend fun getInsightByRecordingId(recordingId: String): Insight?
    suspend fun getAllInsights(archived: Boolean = false): List<Insight>
    suspend fun updateInsightStatus(insightId: String, status: InsightStatus)
    suspend fun archiveInsight(insightId: String)
    suspend fun unarchiveInsight(insightId: String)
    suspend fun deleteOlderThan(epochMs: Long)
    
    suspend fun getRateLimitForDate(dateKey: Long): RateLimit?
    suspend fun createRateLimit(rateLimit: RateLimit)
    suspend fun incrementApiCallsUsed(dateKey: Long)
    
    suspend fun insertRequest(request: InsightGenerationRequest)
    suspend fun getPendingRequests(): List<InsightGenerationRequest>
    suspend fun updateRequestStatus(requestId: String, status: RequestStatus, errorMessage: String? = null)
    suspend fun deleteRequest(requestId: String)
    suspend fun incrementRetryCount(requestId: String)
}
```

**Test Cases**:
- [ ] Interface compiles
- [ ] All methods are suspend functions
- [ ] Method signatures correct

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phases 3.1-3.4

---

### Phase 5.2: Create InsightRepository Interface

**Objective**: Define contract for high-level insight operations.

**Deliverable**:
- Create `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/repository/InsightRepository.kt`

**Content**:
```kotlin
interface InsightRepository {
    suspend fun generateInsight(recordingId: String, transcription: String): InsightGenerationResult
    suspend fun getInsight(insightId: String): Insight?
    suspend fun getInsightByRecording(recordingId: String): Insight?
    suspend fun getAllInsights(): List<Insight>
    suspend fun archiveInsight(insightId: String)
    suspend fun unarchiveInsight(insightId: String)
    suspend fun deleteInsight(insightId: String)
    suspend fun deleteOldInsights(daysOld: Int = 15)
    suspend fun checkRateLimit(): RateLimit
    suspend fun getPendingRequests(): List<InsightGenerationRequest>
    suspend fun processPendingRequests()
}
```

**Test Cases**:
- [ ] Interface compiles
- [ ] All methods are suspend functions
- [ ] Return types match expected results

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phases 3.1-3.4

---

## PHASE GROUP 6: Data Layer Implementation (Phases 6.1 - 6.3)

### Phase 6.1: Implement InsightLocalDataSource

**Objective**: Implement database access layer for insights.

**Deliverable**:
- Create `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/datasource/InsightLocalDataSourceImpl.kt`

**Test Cases**:
- [ ] Insert and retrieve insight works
- [ ] Get pending requests returns only PENDING status
- [ ] Archive/unarchive updates is_archived flag
- [ ] Delete older than removes old insights
- [ ] Rate limit increment updates counter

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
# Run data layer unit tests (create them in Phase 7)
```

**Dependencies**: Phases 5.1, 4.1, 4.2

---

### Phase 6.2: Implement InsightRepository

**Objective**: Implement high-level insight business logic.

**Deliverable**:
- Create `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/repository/InsightRepositoryImpl.kt`

**Test Cases**:
- [ ] generateInsight creates and saves insight
- [ ] checkRateLimit returns correct limit
- [ ] processPendingRequests retries failed requests
- [ ] deleteOldInsights removes 15+ day old insights

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phases 5.2, 6.1

---

### Phase 6.3: Test Data Layer

**Objective**: Verify data layer works correctly.

**Test Cases**:
- [ ] Insert insight with all fields
- [ ] Retrieve insight maintains data integrity
- [ ] Rate limit tracks correctly across days
- [ ] Request queue retries work
- [ ] Cascade delete removes related data

**Verification**:
```bash
# Create and run integration tests
./gradlew :feature_dump:test
```

**Dependencies**: Phases 6.1, 6.2

---

## PHASE GROUP 7: API Layer (Phases 7.1 - 7.3)

### Phase 7.1: Create InsightGenerationService Interface

**Objective**: Define contract for AI-powered insight generation.

**Deliverable**:
- Create `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/service/InsightGenerationService.kt`

**Content**:
```kotlin
interface InsightGenerationService {
    suspend fun generateInsight(recordingId: String, transcription: String): Insight
}
```

**Test Cases**:
- [ ] Interface compiles
- [ ] Method is suspend function

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phase 3.1

---

### Phase 7.2: Implement Claude API Service

**Objective**: Implement insight generation using Claude API.

**Deliverable**:
- Create `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/service/ClaudeInsightGenerationService.kt`

**Content**:
- Call Claude API with transcript
- Parse response JSON
- Create Insight object
- Handle errors and timeouts (5-10 sec)

**Test Cases**:
- [ ] Claude API called with correct prompt
- [ ] JSON response parsed correctly
- [ ] Timeout handled (5-10 sec max)
- [ ] Error responses handled gracefully
- [ ] No medication suggestions in response

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
# Mock API tests
```

**Dependencies**: Phase 7.1

---

### Phase 7.3: Create DI Module for Services

**Objective**: Wire services into dependency injection.

**Deliverable**:
- Create `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/di/InsightModule.kt`

**Content**:
```kotlin
val insightModule = module {
    single<InsightGenerationService> {
        ClaudeInsightGenerationService(
            httpClient = get(),
            apiKey = "YOUR_API_KEY",
        )
    }
    
    single<InsightLocalDataSource> {
        InsightLocalDataSourceImpl(db = get())
    }
    
    single<InsightRepository> {
        InsightRepositoryImpl(
            localDataSource = get(),
            generationService = get(),
        )
    }
}
```

**Test Cases**:
- [ ] Module provides all dependencies
- [ ] No circular dependencies
- [ ] Interfaces bound to implementations

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phases 6.2, 7.2

---

## PHASE GROUP 8: Rate Limiting & Queue (Phases 8.1 - 8.3)

### Phase 8.1: Create RateLimitManager Interface

**Objective**: Define rate limit checking and management.

**Deliverable**:
- Create `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/service/RateLimitManager.kt`

**Test Cases**:
- [ ] Interface compiles
- [ ] canMakeRequest() logic clear
- [ ] getRemainingCalls() calculation correct

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phase 3.2

---

### Phase 8.2: Implement RateLimitManager

**Objective**: Implement 4-calls-per-day rate limiting.

**Deliverable**:
- Create `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/service/RateLimitManagerImpl.kt`

**Test Cases**:
- [ ] Tracks 4 calls per calendar day
- [ ] Resets at midnight (new date key)
- [ ] remainingCalls() calculated correctly
- [ ] isLimitReached() returns true when >= 4

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phases 6.1, 8.1

---

### Phase 8.3: Create RequestQueueManager

**Objective**: Implement request queuing for failed insight generations.

**Deliverable**:
- Create `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/service/RequestQueueManager.kt`
- Create `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/service/RequestQueueManagerImpl.kt`

**Test Cases**:
- [ ] Queue pending requests
- [ ] Process queue in FIFO order
- [ ] Retry up to 3 times
- [ ] Stop when daily limit reached
- [ ] Delete completed requests

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phases 6.1, 6.2

---

## PHASE GROUP 9: ViewModel Integration (Phases 9.1 - 9.3)

### Phase 9.1: Extend DumpDataState

**Objective**: Add insight generation state to ViewModel data state.

**Deliverable**:
- Update `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/state/DumpDataState.kt`

**Content**:
```kotlin
data class DumpDataState(
    // ... existing fields ...
    val insightGenerationState: InsightGenerationState = InsightGenerationState.Idle,
    val generatedInsight: Insight? = null,
    val generationError: String? = null,
    val rateLimitRemaining: Int = 4,
)

enum class InsightGenerationState {
    Idle, Loading, Success, Error, RateLimited
}
```

**Test Cases**:
- [ ] State enum has all required values
- [ ] insightGenerationState defaults to Idle
- [ ] generatedInsight nullable

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phase 3.1

---

### Phase 9.2: Add Insight Generation to DumpSideEffect

**Objective**: Define side effects for insight generation.

**Deliverable**:
- Update `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/state/DumpSideEffect.kt`

**Content**:
```kotlin
sealed class DumpSideEffect : BaseSideEffect {
    // ... existing side effects ...
    data class NavigateToInsightDetail(val insightId: String) : DumpSideEffect()
    data class ShowRateLimitError(val remainingCalls: Int) : DumpSideEffect()
    data class ShowInsightGenerationError(val error: String) : DumpSideEffect()
}
```

**Test Cases**:
- [ ] Side effects defined
- [ ] NavigateToInsightDetail contains insightId
- [ ] ShowRateLimitError contains remainingCalls

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
```

**Dependencies**: Phase 3.1

---

### Phase 9.3: Implement Insight Generation in handleStopRecording

**Objective**: Trigger insight generation when user stops recording.

**Deliverable**:
- Update `DumpViewModel.handleStopRecording()`
- Add call to `generateInsightForRecording()`
- Add new method `private suspend fun generateInsightForRecording()`

**Content**:
```kotlin
// In handleStopRecording(), after saveRecordingUseCase:
when (val result = saveRecordingUseCase(recording)) {
    is UsecaseResult.Success -> {
        updateState { it.copy(insightGenerationState = InsightGenerationState.Loading) }
        generateInsightForRecording(result.data.id, transcription ?: "")
    }
    // ... error handling ...
}

private suspend fun generateInsightForRecording(recordingId: String, transcription: String) {
    try {
        withTimeoutOrNull(10000) { // 10 second timeout
            when (val result = insightRepository.generateInsight(recordingId, transcription)) {
                is InsightGenerationResult.Success -> {
                    updateState {
                        it.copy(
                            insightGenerationState = InsightGenerationState.Success,
                            generatedInsight = result.insight,
                            recordingStatus = RecordingStatus.Idle,
                        )
                    }
                    emitSideEffect(DumpSideEffect.NavigateToInsightDetail(result.insight.id))
                }
                is InsightGenerationResult.RateLimitExceeded -> {
                    updateState {
                        it.copy(
                            insightGenerationState = InsightGenerationState.RateLimited,
                            rateLimitRemaining = result.remainingCalls,
                        )
                    }
                    emitSideEffect(DumpSideEffect.ShowRateLimitError(result.remainingCalls))
                }
                is InsightGenerationResult.Failure -> {
                    updateState {
                        it.copy(
                            insightGenerationState = InsightGenerationState.Error,
                            generationError = result.error,
                        )
                    }
                    emitSideEffect(DumpSideEffect.ShowInsightGenerationError(result.error))
                }
            }
        } ?: run {
            updateState { it.copy(insightGenerationState = InsightGenerationState.Error) }
            emitSideEffect(DumpSideEffect.ShowError("Insight generation timeout"))
        }
    } catch (e: Exception) {
        updateState { it.copy(insightGenerationState = InsightGenerationState.Error) }
        emitSideEffect(DumpSideEffect.ShowError(e.message ?: "Unknown error"))
    }
}
```

**Test Cases**:
- [ ] State transitions Saving → Loading → Success/Error → Idle
- [ ] Timeout handled at 5-10 sec
- [ ] Correct side effects emitted
- [ ] Rate limit response triggers ShowRateLimitError

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
# ViewModel unit tests
```

**Dependencies**: Phases 9.1, 9.2, 7.3

---

## PHASE GROUP 10: UI Components (Phases 10.1 - 10.3)

### Phase 10.1: Create InsightGenerationScreen

**Objective**: Loading screen shown while generating insight.

**Deliverable**:
- Create `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/screen/InsightGenerationScreen.kt`

**Content**:
```kotlin
@Composable
fun InsightGenerationScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Generating your insight...",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This usually takes a few seconds",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

**Test Cases**:
- [ ] Renders without errors
- [ ] Shows loading indicator
- [ ] Shows appropriate text
- [ ] Responsive to different screen sizes

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
# Compose preview
```

**Dependencies**: None

---

### Phase 10.2: Create InsightUiModel & YourReflectionScreen

**Objective**: Display generated insight to user.

**Deliverable**:
- Create `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/state/InsightUiModel.kt`
- Create `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/screen/YourReflectionScreen.kt`

**Content**:
```kotlin
data class InsightUiModel(
    val id: String,
    val recordingId: String,
    val title: String,
    val summary: String,
    val fullSummary: String,
    val emotions: List<String>,
    val pathForward: String,
    val recordingType: String,
    val sentiment: String,
    val sentimentColor: String,
    val createdAt: Long,
    val isArchived: Boolean,
    val formattedDate: String,
    val formattedTime: String,
)

@Composable
fun YourReflectionScreen(
    insight: InsightUiModel,
    onSaveToJournal: () -> Unit,
    onViewRecording: () -> Unit,
    onViewTranscription: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Display insight with all fields
    // Show buttons for save, recording, transcription, regenerate
}
```

**Test Cases**:
- [ ] Renders insight with all fields
- [ ] Buttons visible and clickable
- [ ] Background color matches sentiment
- [ ] Emotion tags displayed (max 3)
- [ ] Date/time formatted correctly

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
# Compose preview
```

**Dependencies**: Phase 3.1

---

### Phase 10.3: Create InsightHistoryScreen

**Objective**: List all insights with search/filter.

**Deliverable**:
- Create `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/screen/InsightHistoryScreen.kt`

**Content**:
```kotlin
@Composable
fun InsightHistoryScreen(
    insights: List<InsightUiModel>,
    onInsightClick: (String) -> Unit,
    onFilterClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Header: "My Journey"
        // Search bar with filter button
        // Info message about 15-day deletion
        // LazyColumn of insight cards
        // Each card shows: type, title, preview, emotions, date
    }
}
```

**Test Cases**:
- [ ] Shows all insights in list
- [ ] Cards clickable
- [ ] Search field present
- [ ] Filter button present
- [ ] Info message about 15-day deletion
- [ ] Empty state message when no insights

**Verification**:
```bash
./gradlew :feature_dump:compileDebugKotlinAndroid
# Compose preview
```

**Dependencies**: Phase 10.2

---

## PHASE GROUP 11: Testing & Verification (Phases 11.1 - 11.3)

### Phase 11.1: Unit Tests - Domain Layer

**Objective**: Test all domain models and logic.

**Deliverable**:
- Create `feature_dump/src/test/kotlin/sanctuary/app/feature/dump/domain/...`
- Test Insight, RateLimit, InsightGenerationRequest models
- Test RateLimitManager logic
- Test InsightStatus transitions

**Test Cases**:
- [ ] Model creation and validation (10+ tests)
- [ ] RateLimit isLimitReached() and remainingCalls()
- [ ] Sentiment enum serialization
- [ ] Request status transitions

**Verification**:
```bash
./gradlew :feature_dump:test
```

**Dependencies**: Phases 3.1-3.4, 8.1

---

### Phase 11.2: Integration Tests - Data Layer

**Objective**: Test database operations end-to-end.

**Deliverable**:
- Create `feature_dump/src/androidTest/kotlin/sanctuary/app/feature/dump/data/...`
- Test InsightLocalDataSource with real database
- Test InsightRepository
- Test cascade deletes

**Test Cases**:
- [ ] Insert and retrieve insight (5+ tests)
- [ ] Rate limit tracking (5+ tests)
- [ ] Request queue operations (5+ tests)
- [ ] Cascade deletes work correctly
- [ ] 15-day auto-deletion works

**Verification**:
```bash
./gradlew :feature_dump:connectedAndroidTest
```

**Dependencies**: Phases 6.1, 6.2, 1.1-1.6

---

### Phase 11.3: Feature Tests - ViewModel

**Objective**: Test insight generation flow in ViewModel.

**Deliverable**:
- Create tests for DumpViewModel insight generation
- Mock InsightRepository and PassphraseManager
- Test state transitions and side effects

**Test Cases**:
- [ ] Successful generation triggers navigation
- [ ] Rate limit error shows message
- [ ] Generation timeout handled
- [ ] State transitions correct

**Verification**:
```bash
./gradlew :feature_dump:test
```

**Dependencies**: Phase 9.3

---

## PHASE SUMMARY

| Phase Group | Phases | Focus | Deliverable |
|---|---|---|---|
| 1 | 1.1-1.6 | Database Schema | 3 tables, 2 migrations |
| 2 | 2.1-2.4 | Encryption | PassphraseManager, DatabaseFactory |
| 3 | 3.1-3.5 | Domain Models | 5 domain classes |
| 4 | 4.1-4.2 | Mappers | Entity ↔ Domain mapping |
| 5 | 5.1-5.2 | Data Interfaces | 2 interfaces |
| 6 | 6.1-6.3 | Data Layer | Implementation + tests |
| 7 | 7.1-7.3 | API Layer | Claude API service |
| 8 | 8.1-8.3 | Rate Limiting | Rate manager + queue |
| 9 | 9.1-9.3 | ViewModel | State + side effects + logic |
| 10 | 10.1-10.3 | UI Screens | 3 screens |
| 11 | 11.1-11.3 | Testing | Unit + Integration + Feature tests |

---

## Implementation Order

**Strict Dependency Order** (each phase depends on previous):

1. **Group 1** (Phases 1.1-1.6): Database must be ready first
2. **Group 2** (Phases 2.1-2.4): Encryption depends on database
3. **Group 3** (Phases 3.1-3.5): Models independent, can parallel with 2
4. **Group 4** (Phases 4.1-4.2): Mappers depend on models (3) and DB (1)
5. **Group 5** (Phases 5.1-5.2): Interfaces depend on models (3)
6. **Group 6** (Phases 6.1-6.3): Data layer depends on interfaces (5) and mappers (4)
7. **Group 7** (Phases 7.1-7.3): API layer depends on models (3)
8. **Group 8** (Phases 8.1-8.3): Rate limiting depends on data layer (6)
9. **Group 9** (Phases 9.1-9.3): ViewModel depends on API (7) and data (6)
10. **Group 10** (Phases 10.1-10.3): UI depends on ViewModel (9) and models
11. **Group 11** (Phases 11.1-11.3): Testing depends on everything above

---

## Recommendations

1. **Start with Groups 1-2**: Foundation work, needed by everything
2. **Parallelize Groups 3-4**: Models and mappers can progress together
3. **Focus on Group 6**: Data layer is critical path
4. **Don't rush UI**: Groups 9-10 are easy once data/API done
5. **Test incrementally**: Run Phase 11 tests after each phase group

---

## Progress Tracking

Mark phases as you complete:
- ✅ Phase 1.1: Insights table
- ✅ Phase 1.2: Rate limits table
- ... (continue marking as you progress)

Use `git commit` after each phase with message format:
```
Phase X.Y: [Brief description]

- [Changed file 1]
- [Changed file 2]
```

This keeps history clear and makes it easy to revert specific phases if needed.
