# Plan: AI-Powered Summary & Emotional Insights Feature

## Overview

This document outlines the implementation strategy for the AI-powered insights feature that generates intelligent summaries, emotional analysis, and path-forward suggestions from user transcriptions. The feature includes:

- Real-time insight generation after recording stops
- Rate-limited API calls (4/day) with request queuing
- Three new screens: Insight Generation (loading), Your Reflection (detail), and Insights History
- Local persistence with SQLCipher encryption
- 15-day auto-deletion with archive protection
- Future V2 extensions: action layer, external integrations, plugin architecture

---

## Architecture Overview

### Key Design Decisions

1. **Separate IDs**: Recording, Transcription, and Insight are separate entities with foreign key relationships
   - Allows flexibility for future multi-insight-per-recording scenarios
   - Maintains data integrity during deletion/archival

2. **Rate Limiting + Queue System**:
   - Tracks 4 API calls per day per user
   - Failed/pending requests stored in local queue
   - Automatic retry when quota refreshes (next calendar day)

3. **Encryption**: SQLCipher for entire `core_database` to protect sensitive user data

4. **Sentiment-Based UI**: Card background color derived from overall sentiment (positive/negative/neutral)

5. **AI Provider Abstraction**: Interface-based design allows swapping Claude API for OpenAI or other providers

### Data Model Relationships

```
Recording (1) ──-> (1) Transcription
       ↓
       └─────────────> (1) Insight
                          ├── emotions: List<String>
                          ├── sentiment: POSITIVE | NEGATIVE | NEUTRAL
                          ├── status: PENDING | GENERATING | SAVED | ARCHIVED
                          └── archivedAt: Long? (null if not archived)

RateLimitEntry (tracks API usage per day)
RequestQueue (stores pending insight generation requests)
```

---

## Implementation Phases

### Phase 1: Database Schema, Encryption & Migrations

#### Objective
Set up SQLCipher encryption, add insights and related tables, establish relationships.

#### Changes Required

**File:** `core_database/build.gradle.kts`

Add SQLCipher dependency and enable encryption:

```gradle
dependencies {
    // ... existing dependencies
    implementation("net.zetetic:android-database-sqlcipher:4.5.4") // Android
    // iOS uses SQLCipher via CocoaPods
}

sqldelight {
    databases {
        create("SanctuaryDatabase") {
            packageName = "sanctuary.app.core.database"
            schemaOutputDirectory = file("src/commonMain/sqldelight")
            deriveSchemaFromMigrations = true
            verifyMigrations = true
            // Enable encryption
            dialect = "sqlite:3"
        }
    }
}
```

**File:** `core_database/src/commonMain/kotlin/sanctuary/app/core/database/SanctuaryDatabase.kt`

Update database initialization to use SQLCipher passphrase:

```kotlin
expect class DatabaseDriver {
    companion object {
        fun create(encryptionPassphrase: String): SanctuaryDatabase
    }
}

// Android implementation: use SQLCipher driver
// iOS implementation: use SQLCipher via platform-specific configuration
```

**File:** `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/recordings.sq`

Update recordings table schema version:

```sql
CREATE TABLE recordings (
    id TEXT NOT NULL PRIMARY KEY,
    user_id TEXT,
    file_path TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    title TEXT,
    transcription TEXT,
    -- Version 2+ field (can be null for backward compatibility)
    is_archived INTEGER DEFAULT 0
);
```

**File:** `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/insights.sq` (NEW)

```sql
CREATE TABLE insights (
    id TEXT NOT NULL PRIMARY KEY,
    recording_id TEXT NOT NULL,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    full_summary TEXT NOT NULL,
    emotions_json TEXT NOT NULL, -- JSON array: ["emotion1", "emotion2"]
    path_forward TEXT NOT NULL,
    recording_type TEXT NOT NULL, -- REFLECTION, VENT, BREAKTHROUGH, etc.
    sentiment TEXT NOT NULL, -- POSITIVE, NEGATIVE, NEUTRAL
    created_at INTEGER NOT NULL,
    is_archived INTEGER DEFAULT 0,
    archived_at INTEGER,
    status TEXT NOT NULL, -- PENDING, GENERATING, SAVED, ARCHIVED
    FOREIGN KEY (recording_id) REFERENCES recordings(id) ON DELETE CASCADE
);

selectAll:
SELECT * FROM insights WHERE is_archived = 0 ORDER BY created_at DESC;

selectById:
SELECT * FROM insights WHERE id = ?;

selectByRecordingId:
SELECT * FROM insights WHERE recording_id = ?;

selectArchived:
SELECT * FROM insights WHERE is_archived = 1 ORDER BY created_at DESC;

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

**File:** `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/rate_limits.sq` (NEW)

```sql
CREATE TABLE rate_limits (
    id TEXT NOT NULL PRIMARY KEY,
    date INTEGER NOT NULL, -- Day identifier (epoch / 86400)
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

**File:** `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/request_queue.sq` (NEW)

```sql
CREATE TABLE request_queue (
    id TEXT NOT NULL PRIMARY KEY,
    recording_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    status TEXT NOT NULL, -- PENDING, PROCESSING, FAILED, COMPLETED
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

**File:** `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/migrations/2.sqm` (NEW)

```sql
-- Migration for insights table
ALTER TABLE recordings ADD COLUMN is_archived INTEGER DEFAULT 0;
-- Note: insights, rate_limits, request_queue tables created in schema v3+
```

**File:** `core_database/src/commonMain/sqldelight/sanctuary/app/core/database/migrations/3.sqm` (NEW)

```sql
-- Create insights table with v3 schema
CREATE TABLE IF NOT EXISTS insights (
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

CREATE TABLE IF NOT EXISTS rate_limits (
    id TEXT NOT NULL PRIMARY KEY,
    date INTEGER NOT NULL,
    api_calls_used INTEGER DEFAULT 0,
    max_api_calls INTEGER DEFAULT 4
);

CREATE TABLE IF NOT EXISTS request_queue (
    id TEXT NOT NULL PRIMARY KEY,
    recording_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    status TEXT NOT NULL,
    error_message TEXT,
    FOREIGN KEY (recording_id) REFERENCES recordings(id) ON DELETE CASCADE
);
```

**File:** `core_database/build.gradle.kts`

Update schema version and encryption config:

```gradle
sqldelight {
    databases {
        create("SanctuaryDatabase") {
            packageName = "sanctuary.app.core.database"
            schemaOutputDirectory = file("src/commonMain/sqldelight")
            deriveSchemaFromMigrations = true
            verifyMigrations = true
            schemaVersion = 3  // Updated from previous version
        }
    }
}

dependencies {
    // SQLCipher
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
}
```

#### Test Cases - Phase 1

- [ ] **Schema Validation**: Verify `insights`, `rate_limits`, `request_queue` tables are created with correct columns
- [ ] **Foreign Key Constraint**: Verify deleting a recording cascades delete to related insights
- [ ] **Encryption Initialization**: Verify database is encrypted with SQLCipher passphrase
- [ ] **Migration Execution**: Apply migrations on v1/v2 databases, verify all tables created
- [ ] **Backward Compatibility**: Verify existing recordings + transcriptions are preserved
- [ ] **Timestamp Accuracy**: Verify `created_at` and `archived_at` store epoch milliseconds correctly
- [ ] **JSON Storage**: Insert insight with emotions as JSON array, retrieve and verify parsing
- [ ] **Null Handling**: Verify `archived_at` and `error_message` correctly handle NULL values
- [ ] **Index Performance**: Verify queries on `recording_id`, `status`, `created_at` use indexes (add if needed)
- [ ] **Large Data**: Insert insights with 1000+ character fields, verify no truncation

---

### Phase 2: Domain Models & Data Structures

#### Objective
Define domain models for insights, rate limiting, and request queuing.

#### Changes Required

**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/model/Insight.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InsightContent(
    val title: String,
    val summary: String,
    val fullSummary: String,
    val emotions: List<String>,
    val pathForward: String,
    val recordingType: String, // REFLECTION, VENT, BREAKTHROUGH, etc.
    val sentiment: Sentiment, // POSITIVE, NEGATIVE, NEUTRAL
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

**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/model/RateLimit.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.domain.model

data class RateLimit(
    val id: String,
    val date: Long, // Day identifier (epoch / 86400)
    val apiCallsUsed: Int,
    val maxApiCalls: Int = 4,
) {
    fun isLimitReached(): Boolean = apiCallsUsed >= maxApiCalls
    fun remainingCalls(): Int = (maxApiCalls - apiCallsUsed).coerceAtLeast(0)
}
```

**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/model/InsightGenerationRequest.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.domain.model

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

**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/model/InsightGenerationResult.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.domain.model

sealed class InsightGenerationResult {
    data class Success(val insight: Insight) : InsightGenerationResult()
    data class RateLimitExceeded(val remainingCalls: Int) : InsightGenerationResult()
    data class Failure(val error: String, val isRetryable: Boolean) : InsightGenerationResult()
}
```

#### Test Cases - Phase 2

- [ ] **Insight Data Class**: Create Insight instances, verify all fields are correctly set
- [ ] **Sentiment Enum**: Verify all 3 sentiment values exist and can be serialized
- [ ] **InsightContent Serialization**: Serialize/deserialize InsightContent to/from JSON
- [ ] **RateLimit Calculation**: Test `isLimitReached()` and `remainingCalls()` logic
- [ ] **Request Status Enum**: Verify all status values used correctly
- [ ] **Null Safety**: Verify `archivedAt` and `errorMessage` handle null correctly
- [ ] **Emotions List**: Create insight with 0, 1, 2, 3+ emotions, verify all cases work

---

### Phase 3: Data Layer - Local Persistence

#### Objective
Implement data sources and repositories for insights, rate limiting, and request queuing.

#### Changes Required

**File:** `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/mapper/InsightMapper.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.data.mapper

import kotlinx.serialization.json.Json
import sanctuary.app.feature.dump.domain.model.*
import sanctuary.app.core.database.Insights
import sanctuary.app.core.database.RateLimits
import sanctuary.app.core.database.RequestQueues

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
    transcription = "", // Fetched separately via join or separate query
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

**File:** `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/datasource/InsightLocalDataSource.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.data.datasource

import sanctuary.app.feature.dump.domain.model.*

internal interface InsightLocalDataSource {
    suspend fun insertInsight(insight: Insight)
    suspend fun getInsightById(id: String): Insight?
    suspend fun getInsightByRecordingId(recordingId: String): Insight?
    suspend fun getAllInsights(archived: Boolean = false): List<Insight>
    suspend fun updateInsightStatus(insightId: String, status: InsightStatus)
    suspend fun archiveInsight(insightId: String)
    suspend fun unarchiveInsight(insightId: String)
    suspend fun deleteOlderThan(epochMs: Long)
    
    // Rate limit operations
    suspend fun getRateLimitForDate(dateKey: Long): RateLimit?
    suspend fun createRateLimit(rateLimit: RateLimit)
    suspend fun incrementApiCallsUsed(dateKey: Long)
    
    // Request queue operations
    suspend fun insertRequest(request: InsightGenerationRequest)
    suspend fun getPendingRequests(): List<InsightGenerationRequest>
    suspend fun updateRequestStatus(requestId: String, status: RequestStatus, errorMessage: String? = null)
    suspend fun deleteRequest(requestId: String)
    suspend fun incrementRetryCount(requestId: String)
}
```

**File:** `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/datasource/InsightLocalDataSourceImpl.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.data.datasource

import sanctuary.app.core.database.SanctuaryDatabase
import sanctuary.app.feature.dump.data.mapper.*
import sanctuary.app.feature.dump.domain.model.*

internal class InsightLocalDataSourceImpl(
    private val db: SanctuaryDatabase,
) : InsightLocalDataSource {
    
    private val queries = db.insightsQueries
    private val rateLimitQueries = db.rateLimitsQueries
    private val queueQueries = db.requestQueuesQueries

    override suspend fun insertInsight(insight: Insight) {
        val entity = insight.toEntity()
        queries.insert(
            id = entity.id,
            recording_id = entity.recording_id,
            title = entity.title,
            summary = entity.summary,
            full_summary = entity.full_summary,
            emotions_json = entity.emotions_json,
            path_forward = entity.path_forward,
            recording_type = entity.recording_type,
            sentiment = entity.sentiment,
            created_at = entity.created_at,
            is_archived = entity.is_archived,
            status = entity.status,
        )
    }

    override suspend fun getInsightById(id: String): Insight? {
        return queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getInsightByRecordingId(recordingId: String): Insight? {
        return queries.selectByRecordingId(recordingId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getAllInsights(archived: Boolean): List<Insight> {
        return if (archived) {
            queries.selectArchived().executeAsList().map { it.toDomain() }
        } else {
            queries.selectAll().executeAsList().map { it.toDomain() }
        }
    }

    override suspend fun updateInsightStatus(insightId: String, status: InsightStatus) {
        queries.updateStatus(status.name, insightId)
    }

    override suspend fun archiveInsight(insightId: String) {
        val now = System.currentTimeMillis()
        queries.updateIsArchived(1, now, insightId)
    }

    override suspend fun unarchiveInsight(insightId: String) {
        queries.updateIsArchived(0, null, insightId)
    }

    override suspend fun deleteOlderThan(epochMs: Long) {
        queries.deleteOlderThan(epochMs)
    }

    override suspend fun getRateLimitForDate(dateKey: Long): RateLimit? {
        return rateLimitQueries.getByDate(dateKey).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun createRateLimit(rateLimit: RateLimit) {
        val entity = rateLimit.toEntity()
        rateLimitQueries.insert(
            id = entity.id,
            date = entity.date,
            api_calls_used = entity.api_calls_used,
            max_api_calls = entity.max_api_calls,
        )
    }

    override suspend fun incrementApiCallsUsed(dateKey: Long) {
        val current = rateLimitQueries.getByDate(dateKey).executeAsOneOrNull()
        if (current != null) {
            rateLimitQueries.updateCallsUsed(current.api_calls_used + 1, dateKey)
        }
    }

    override suspend fun insertRequest(request: InsightGenerationRequest) {
        val entity = request.toEntity()
        queueQueries.insert(
            id = entity.id,
            recording_id = entity.recording_id,
            created_at = entity.created_at,
            status = entity.status,
        )
    }

    override suspend fun getPendingRequests(): List<InsightGenerationRequest> {
        return queueQueries.selectPending().executeAsList().map { it.toDomain() }
    }

    override suspend fun updateRequestStatus(requestId: String, status: RequestStatus, errorMessage: String?) {
        queueQueries.updateStatus(status.name, errorMessage, 0, requestId)
    }

    override suspend fun deleteRequest(requestId: String) {
        queueQueries.deleteById(requestId)
    }

    override suspend fun incrementRetryCount(requestId: String) {
        val current = queueQueries.selectAll().executeAsList()
            .find { it.id == requestId } ?: return
        queueQueries.updateStatus(current.status, current.error_message, current.retry_count + 1, requestId)
    }
}
```

**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/repository/InsightRepository.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.domain.repository

import sanctuary.app.feature.dump.domain.model.*

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

**File:** `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/repository/InsightRepositoryImpl.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.data.repository

import sanctuary.app.feature.dump.domain.repository.InsightRepository
import sanctuary.app.feature.dump.domain.model.*
import sanctuary.app.feature.dump.data.datasource.InsightLocalDataSource
import sanctuary.app.feature.dump.data.service.InsightGenerationService

internal class InsightRepositoryImpl(
    private val localDataSource: InsightLocalDataSource,
    private val generationService: InsightGenerationService,
) : InsightRepository {

    override suspend fun generateInsight(recordingId: String, transcription: String): InsightGenerationResult {
        // Check rate limit
        val rateLimit = checkRateLimit()
        if (rateLimit.isLimitReached()) {
            return InsightGenerationResult.RateLimitExceeded(rateLimit.remainingCalls())
        }

        return try {
            val insight = generationService.generateInsight(recordingId, transcription)
            localDataSource.insertInsight(insight)
            localDataSource.incrementApiCallsUsed(getTodayDateKey())
            InsightGenerationResult.Success(insight)
        } catch (e: Exception) {
            // Queue request for retry
            val request = InsightGenerationRequest(
                id = generateId(),
                recordingId = recordingId,
                transcription = transcription,
                createdAt = System.currentTimeMillis(),
                status = RequestStatus.PENDING,
            )
            localDataSource.insertRequest(request)
            InsightGenerationResult.Failure(e.message ?: "Unknown error", isRetryable = true)
        }
    }

    override suspend fun getInsight(insightId: String): Insight? {
        return localDataSource.getInsightById(insightId)
    }

    override suspend fun getInsightByRecording(recordingId: String): Insight? {
        return localDataSource.getInsightByRecordingId(recordingId)
    }

    override suspend fun getAllInsights(): List<Insight> {
        return localDataSource.getAllInsights(archived = false)
    }

    override suspend fun archiveInsight(insightId: String) {
        localDataSource.archiveInsight(insightId)
    }

    override suspend fun unarchiveInsight(insightId: String) {
        localDataSource.unarchiveInsight(insightId)
    }

    override suspend fun deleteInsight(insightId: String) {
        // Implement soft delete if needed, or hard delete
        localDataSource.archiveInsight(insightId)
    }

    override suspend fun deleteOldInsights(daysOld: Int) {
        val cutoffEpoch = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        localDataSource.deleteOlderThan(cutoffEpoch)
    }

    override suspend fun checkRateLimit(): RateLimit {
        val dateKey = getTodayDateKey()
        var rateLimit = localDataSource.getRateLimitForDate(dateKey)
        
        if (rateLimit == null) {
            rateLimit = RateLimit(
                id = generateId(),
                date = dateKey,
                apiCallsUsed = 0,
                maxApiCalls = 4,
            )
            localDataSource.createRateLimit(rateLimit)
        }
        
        return rateLimit
    }

    override suspend fun getPendingRequests(): List<InsightGenerationRequest> {
        return localDataSource.getPendingRequests()
    }

    override suspend fun processPendingRequests() {
        val requests = getPendingRequests()
        for (request in requests) {
            if (request.retryCount >= request.maxRetries) {
                localDataSource.updateRequestStatus(request.id, RequestStatus.FAILED, "Max retries exceeded")
                continue
            }

            try {
                val rateLimit = checkRateLimit()
                if (rateLimit.isLimitReached()) {
                    // Stop processing, will retry next day
                    break
                }

                // Get transcription from recording
                val transcription = getTranscriptionForRecording(request.recordingId)
                val insight = generationService.generateInsight(request.recordingId, transcription)
                
                localDataSource.insertInsight(insight)
                localDataSource.updateRequestStatus(request.id, RequestStatus.COMPLETED)
                localDataSource.incrementApiCallsUsed(getTodayDateKey())
            } catch (e: Exception) {
                localDataSource.incrementRetryCount(request.id)
                localDataSource.updateRequestStatus(request.id, RequestStatus.PENDING, e.message)
            }
        }
    }

    private suspend fun getTranscriptionForRecording(recordingId: String): String {
        // Fetch from recording using RecordingRepository or direct DB access
        // Implementation depends on how you structure the recording access
        return "" // Placeholder
    }

    private fun getTodayDateKey(): Long {
        return System.currentTimeMillis() / (24 * 60 * 60 * 1000)
    }

    private fun generateId(): String = java.util.UUID.randomUUID().toString()
}
```

#### Test Cases - Phase 3

- [ ] **Insert & Retrieve Insight**: Insert insight, retrieve by ID, verify all fields
- [ ] **JSON Emotions Serialization**: Insert insight with emotions list, verify JSON storage and retrieval
- [ ] **Rate Limit Creation**: Create rate limit for date key, verify initial values
- [ ] **Increment API Calls**: Increment calls, verify counter updates correctly
- [ ] **Rate Limit Exceeded**: Set to 4 calls, verify `isLimitReached()` returns true
- [ ] **Request Queue Insert**: Insert generation request, verify PENDING status
- [ ] **Get Pending Requests**: Insert multiple requests, retrieve only PENDING ones
- [ ] **Update Request Status**: Change request from PENDING to COMPLETED, verify
- [ ] **Archive/Unarchive**: Archive insight, verify `is_archived` flag, unarchive and verify
- [ ] **Delete Older Than**: Insert insights from various dates, delete older than cutoff, verify only new ones remain
- [ ] **Concurrent Operations**: Insert multiple insights concurrently, verify no race conditions
- [ ] **Transaction Rollback**: Simulate error during insert, verify database state rolls back

---

### Phase 4: API Layer - Insight Generation Service

#### Objective
Abstract insight generation behind an interface, implement Claude API provider (flexible for future providers).

#### Changes Required

**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/service/InsightGenerationService.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.domain.service

import sanctuary.app.feature.dump.domain.model.Insight

interface InsightGenerationService {
    suspend fun generateInsight(recordingId: String, transcription: String): Insight
}
```

**File:** `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/service/ClaudeInsightGenerationService.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.data.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sanctuary.app.feature.dump.domain.model.*
import sanctuary.app.feature.dump.domain.service.InsightGenerationService

internal class ClaudeInsightGenerationService(
    private val httpClient: HttpClient,
    private val apiKey: String,
) : InsightGenerationService {

    override suspend fun generateInsight(recordingId: String, transcription: String): Insight {
        val response = callClaudeAPI(transcription)
        val parsedInsight = parseAPIResponse(response)
        
        return Insight(
            id = generateId(),
            recordingId = recordingId,
            content = parsedInsight,
            createdAt = System.currentTimeMillis(),
            status = InsightStatus.SAVED,
        )
    }

    private suspend fun callClaudeAPI(transcription: String): String {
        val prompt = buildPrompt(transcription)
        
        val request = ClaudeRequest(
            model = "claude-3-5-sonnet-20241022",
            max_tokens = 1024,
            messages = listOf(
                ClaudeMessage(
                    role = "user",
                    content = prompt,
                )
            ),
        )

        val response = httpClient.post("https://api.anthropic.com/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        return if (response.status.isSuccess()) {
            val body = response.body<ClaudeResponse>()
            body.content.firstOrNull()?.text ?: throw Exception("Empty response from Claude API")
        } else {
            throw Exception("Claude API error: ${response.status}")
        }
    }

    private fun buildPrompt(transcription: String): String {
        return """
            Analyze this voice transcription and generate a wellness insight. Respond ONLY with a valid JSON object (no markdown, no code blocks).
            
            Required JSON format (strictly):
            {
                "title": "Short, engaging title (max 200 chars)",
                "summary": "Short 1-2 sentence preview (max 200 chars)",
                "fullSummary": "Detailed 2-3 paragraph analysis (max 200 chars per paragraph)",
                "emotions": ["emotion1", "emotion2"],
                "pathForward": "Actionable suggestions (max 200 chars)",
                "recordingType": "REFLECTION|VENT|BREAKTHROUGH|[other type]",
                "sentiment": "POSITIVE|NEGATIVE|NEUTRAL"
            }
            
            Guidelines:
            - Be warm, supportive, and empathetic
            - Max 2-3 emotion tags from natural language
            - Never suggest medication or medical treatment
            - If self-harm/suicidal ideation detected, respond with professional support suggestion
            - Keep language encouraging and insightful
            
            Transcription:
            "$transcription"
        """.trimIndent()
    }

    private fun parseAPIResponse(jsonString: String): InsightContent {
        return try {
            // Handle case where API returns markdown-wrapped JSON
            val cleanJson = jsonString
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            Json.decodeFromString<InsightContent>(cleanJson)
        } catch (e: Exception) {
            throw Exception("Failed to parse insight response: ${e.message}")
        }
    }

    private fun generateId(): String = java.util.UUID.randomUUID().toString()
}

@Serializable
private data class ClaudeRequest(
    val model: String,
    val max_tokens: Int,
    val messages: List<ClaudeMessage>,
)

@Serializable
private data class ClaudeMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ClaudeResponse(
    val content: List<ContentBlock>,
)

@Serializable
private data class ContentBlock(
    val type: String,
    val text: String,
)
```

**File:** `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/di/InsightModule.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.data.di

import org.koin.dsl.module
import sanctuary.app.feature.dump.data.repository.InsightRepositoryImpl
import sanctuary.app.feature.dump.data.service.ClaudeInsightGenerationService
import sanctuary.app.feature.dump.data.datasource.InsightLocalDataSourceImpl
import sanctuary.app.feature.dump.domain.repository.InsightRepository
import sanctuary.app.feature.dump.domain.service.InsightGenerationService

val insightModule = module {
    single<InsightGenerationService> {
        ClaudeInsightGenerationService(
            httpClient = get(),
            apiKey = "YOUR_CLAUDE_API_KEY", // Should be injected from config
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

#### Test Cases - Phase 4

- [ ] **Claude API Integration**: Mock API response, verify insight is correctly parsed
- [ ] **JSON Response Parsing**: Test with valid/invalid JSON responses from API
- [ ] **API Error Handling**: Test 401 (auth error), 429 (rate limit), 500 (server error)
- [ ] **Timeout Handling**: Test timeout scenario (5-10 sec max wait)
- [ ] **Response Validation**: Verify all required fields are present in parsed response
- [ ] **Emotion Tags**: Verify emotions list contains 1-3 items
- [ ] **Sentiment Classification**: Verify sentiment is one of POSITIVE/NEGATIVE/NEUTRAL
- [ ] **Recording Type Detection**: Verify recordingType is valid (REFLECTION, VENT, BREAKTHROUGH, etc.)
- [ ] **Title Length**: Verify title is within max character limit
- [ ] **Self-Harm Detection**: Test prompt includes sensitivity guidance for suicidal ideation
- [ ] **No Medical Suggestions**: Verify AI response never includes medication/treatment recommendations
- [ ] **Empty Transcription**: Handle edge case of very short transcription (< 10 words)
- [ ] **Long Transcription**: Handle edge case of very long transcription (> 10,000 words)

---

### Phase 5: Rate Limiting & Request Queue System

#### Objective
Implement daily rate limiting (4 calls/day), queuing for failed requests, automatic retry on new day.

#### Changes Required

**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/service/RateLimitManager.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.domain.service

import sanctuary.app.feature.dump.domain.model.RateLimit

interface RateLimitManager {
    suspend fun checkTodayLimit(): RateLimit
    suspend fun canMakeRequest(): Boolean
    suspend fun recordRequest()
    suspend fun getRemainingCalls(): Int
    suspend fun resetDailyLimit()
}
```

**File:** `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/service/RateLimitManagerImpl.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.data.service

import sanctuary.app.feature.dump.domain.service.RateLimitManager
import sanctuary.app.feature.dump.domain.model.RateLimit
import sanctuary.app.feature.dump.data.datasource.InsightLocalDataSource

internal class RateLimitManagerImpl(
    private val localDataSource: InsightLocalDataSource,
) : RateLimitManager {

    override suspend fun checkTodayLimit(): RateLimit {
        return localDataSource.getRateLimitForDate(getTodayDateKey())
            ?: createTodayLimit()
    }

    override suspend fun canMakeRequest(): Boolean {
        val limit = checkTodayLimit()
        return !limit.isLimitReached()
    }

    override suspend fun recordRequest() {
        localDataSource.incrementApiCallsUsed(getTodayDateKey())
    }

    override suspend fun getRemainingCalls(): Int {
        val limit = checkTodayLimit()
        return limit.remainingCalls()
    }

    override suspend fun resetDailyLimit() {
        val today = getTodayDateKey()
        val limit = checkTodayLimit()
        // Create new limit entry for today if not exists
        if (limit.date != today) {
            val newLimit = RateLimit(
                id = generateId(),
                date = today,
                apiCallsUsed = 0,
                maxApiCalls = 4,
            )
            localDataSource.createRateLimit(newLimit)
        }
    }

    private suspend fun createTodayLimit(): RateLimit {
        val limit = RateLimit(
            id = generateId(),
            date = getTodayDateKey(),
            apiCallsUsed = 0,
            maxApiCalls = 4,
        )
        localDataSource.createRateLimit(limit)
        return limit
    }

    private fun getTodayDateKey(): Long {
        return System.currentTimeMillis() / (24 * 60 * 60 * 1000)
    }

    private fun generateId(): String = java.util.UUID.randomUUID().toString()
}
```

**File:** `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/service/RequestQueueManager.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.domain.service

import sanctuary.app.feature.dump.domain.model.InsightGenerationRequest

interface RequestQueueManager {
    suspend fun enqueueRequest(recordingId: String, transcription: String)
    suspend fun processQueue()
    suspend fun getPendingRequests(): List<InsightGenerationRequest>
    suspend fun getQueueStatus(): QueueStatus
}

data class QueueStatus(
    val pendingCount: Int,
    val nextProcessTime: Long? = null,
)
```

**File:** `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/service/RequestQueueManagerImpl.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.data.service

import sanctuary.app.feature.dump.domain.service.RequestQueueManager
import sanctuary.app.feature.dump.domain.service.QueueStatus
import sanctuary.app.feature.dump.domain.model.InsightGenerationRequest
import sanctuary.app.feature.dump.data.datasource.InsightLocalDataSource
import sanctuary.app.feature.dump.data.repository.InsightRepositoryImpl

internal class RequestQueueManagerImpl(
    private val localDataSource: InsightLocalDataSource,
    private val insightRepository: InsightRepositoryImpl,
) : RequestQueueManager {

    override suspend fun enqueueRequest(recordingId: String, transcription: String) {
        val request = InsightGenerationRequest(
            id = generateId(),
            recordingId = recordingId,
            transcription = transcription,
            createdAt = System.currentTimeMillis(),
        )
        localDataSource.insertRequest(request)
    }

    override suspend fun processQueue() {
        insightRepository.processPendingRequests()
    }

    override suspend fun getPendingRequests(): List<InsightGenerationRequest> {
        return localDataSource.getPendingRequests()
    }

    override suspend fun getQueueStatus(): QueueStatus {
        val pending = getPendingRequests()
        return QueueStatus(
            pendingCount = pending.size,
            nextProcessTime = pending.firstOrNull()?.createdAt,
        )
    }

    private fun generateId(): String = java.util.UUID.randomUUID().toString()
}
```

**File:** `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/di/RateLimitModule.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.data.di

import org.koin.dsl.module
import sanctuary.app.feature.dump.data.service.RateLimitManagerImpl
import sanctuary.app.feature.dump.data.service.RequestQueueManagerImpl
import sanctuary.app.feature.dump.domain.service.RateLimitManager
import sanctuary.app.feature.dump.domain.service.RequestQueueManager

val rateLimitModule = module {
    single<RateLimitManager> {
        RateLimitManagerImpl(localDataSource = get())
    }
    
    single<RequestQueueManager> {
        RequestQueueManagerImpl(
            localDataSource = get(),
            insightRepository = get(),
        )
    }
}
```

#### Test Cases - Phase 5

- [ ] **Daily Limit Check**: Verify 4 calls allowed per calendar day
- [ ] **Limit Exceeded**: 5th call returns rate limit exceeded error
- [ ] **Day Boundary**: Verify limit resets at midnight
- [ ] **Remaining Calls**: Test `getRemainingCalls()` after various numbers of API calls
- [ ] **Queue Insertion**: Enqueue request, verify it's stored in DB
- [ ] **Process Pending**: Queue 2 requests, process them, verify both complete
- [ ] **Max Retries**: Queue request with max_retries = 3, simulate 3 failures, verify marked as FAILED
- [ ] **Concurrent Queue Processing**: Queue 3 requests, process concurrently, verify no race conditions
- [ ] **Daily Quota Refresh**: Hit limit on day 1, move to day 2, verify quota resets
- [ ] **Queue Status**: Verify queue status accurately reflects pending count
- [ ] **Request Ordering**: Queue requests, verify processed in FIFO order

---

### Phase 6: ViewModel Integration - Insight Generation Flow

#### Objective
Wire InsightRepository into DumpViewModel, implement real-time insight generation after recording stops.

#### Changes Required

**File:** `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/viewmodel/DumpViewModel.kt`

Update existing `DumpViewModel`:

```kotlin
// Add to imports
import sanctuary.app.feature.dump.domain.repository.InsightRepository
import sanctuary.app.feature.dump.domain.service.RateLimitManager
import sanctuary.app.feature.dump.domain.model.InsightGenerationResult

// Add to constructor
private val insightRepository: InsightRepository,
private val rateLimitManager: RateLimitManager,

// Add to DumpDataState
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

// Update handleStopRecording()
private suspend fun handleStopRecording() {
    audioRecorder.stopRecording()
    val filePath = dataState.value.currentFilePath ?: return
    val durationMs = dataState.value.elapsedMs

    updateState { it.copy(recordingStatus = RecordingStatus.Saving) }

    viewModelScope.launch {
        try {
            // Transcription logic (existing code)
            val transcription = when (val t = transcriptionRepository.transcribe(filePath)) {
                is UsecaseResult.Success -> t.data
                is UsecaseResult.Failure -> null
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
                is UsecaseResult.Success -> {
                    // NEW: Start insight generation
                    updateState { it.copy(insightGenerationState = InsightGenerationState.Loading) }
                    generateInsightForRecording(result.data.id, transcription ?: "")
                }
                is UsecaseResult.Failure -> {
                    emitSideEffect(DumpSideEffect.ShowError("Failed to save recording"))
                    updateState { it.copy(recordingStatus = RecordingStatus.Idle) }
                }
            }
        } catch (e: Exception) {
            emitSideEffect(DumpSideEffect.ShowError(e.message ?: "Unknown error"))
            updateState { it.copy(recordingStatus = RecordingStatus.Idle) }
        }
    }
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
                            recordingStatus = RecordingStatus.Idle,
                        )
                    }
                    emitSideEffect(DumpSideEffect.ShowRateLimitError(result.remainingCalls))
                }
                is InsightGenerationResult.Failure -> {
                    updateState {
                        it.copy(
                            insightGenerationState = InsightGenerationState.Error,
                            generationError = result.error,
                            recordingStatus = RecordingStatus.Idle,
                        )
                    }
                    emitSideEffect(DumpSideEffect.ShowInsightGenerationError(result.error))
                }
            }
        } ?: run {
            updateState {
                it.copy(
                    insightGenerationState = InsightGenerationState.Error,
                    generationError = "Insight generation timeout",
                    recordingStatus = RecordingStatus.Idle,
                )
            }
            emitSideEffect(DumpSideEffect.ShowError("Insight generation took too long"))
        }
    } catch (e: Exception) {
        updateState {
            it.copy(
                insightGenerationState = InsightGenerationState.Error,
                generationError = e.message,
                recordingStatus = RecordingStatus.Idle,
            )
        }
        emitSideEffect(DumpSideEffect.ShowError(e.message ?: "Unknown error"))
    }
}

// Add new side effects
sealed class DumpSideEffect : BaseSideEffect {
    // ... existing side effects ...
    data class NavigateToInsightDetail(val insightId: String) : DumpSideEffect()
    data class ShowRateLimitError(val remainingCalls: Int) : DumpSideEffect()
    data class ShowInsightGenerationError(val error: String) : DumpSideEffect()
}
```

#### Test Cases - Phase 6

- [ ] **Successful Generation**: Record → Generate insight → Verify insight saved and navigates to detail
- [ ] **Rate Limit Flow**: Hit 4/day limit → 5th attempt triggers RateLimitExceeded → Shows error
- [ ] **Generation Timeout**: Set timeout to 5s, mock slow API, verify timeout error after 5s
- [ ] **Empty Transcription**: Record with empty/null transcription → Handle gracefully
- [ ] **API Error Handling**: Mock API failure → Shows error, recording still saved
- [ ] **Concurrent Recordings**: Start 2 recordings rapidly → Verify both queued correctly
- [ ] **State Transitions**: Verify correct state transitions (Saving → Loading → Success/Error → Idle)
- [ ] **Side Effect Emission**: Verify correct side effects emitted for success/failure scenarios

---

### Phase 7: UI Layer - Screens & Components

#### Objective
Implement three new screens: InsightGenerationScreen (loading), YourReflectionScreen (detail), InsightHistoryScreen (list).

#### Changes Required

**File:** `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/state/InsightUiModel.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.presentation.state

data class InsightUiModel(
    val id: String,
    val recordingId: String,
    val title: String,
    val summary: String,
    val fullSummary: String,
    val emotions: List<String>,
    val pathForward: String,
    val recordingType: String,
    val sentiment: String, // POSITIVE, NEGATIVE, NEUTRAL
    val sentimentColor: String, // Hex color based on sentiment
    val createdAt: Long,
    val isArchived: Boolean,
    val formattedDate: String,
    val formattedTime: String,
)
```

**File:** `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/screen/InsightGenerationScreen.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sanctuary.app.core_ui.theme.SanctuaryTheme

@Composable
fun InsightGenerationScreen(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
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
            color = MaterialTheme.colorScheme.onBackground,
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

**File:** `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/screen/YourReflectionScreen.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sanctuary.app.feature.dump.presentation.state.InsightUiModel

@Composable
fun YourReflectionScreen(
    insight: InsightUiModel,
    onSaveToJournal: () -> Unit,
    onViewRecording: () -> Unit,
    onViewTranscription: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Header
        Text(
            text = "Your Reflection",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Insight Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(android.graphics.Color.parseColor(insight.sentimentColor))),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                // Recording type badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = insight.recordingType,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }

                // Title
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // Emotions
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    insight.emotions.take(3).forEach { emotion ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = emotion,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                // Summary
                Text(
                    text = insight.fullSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                // Path Forward
                Text(
                    text = "Path Forward",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                Text(
                    text = insight.pathForward,
                    style = MaterialTheme.typography.bodySmall,
                )

                // Timestamp
                Text(
                    text = "${insight.formattedDate} at ${insight.formattedTime}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Button(
            onClick = onSaveToJournal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save to Journal")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onViewRecording,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Recording")
            }

            OutlinedButton(
                onClick = onViewTranscription,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Notes, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Text")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRegenerate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Regenerate")
        }
    }
}
```

**File:** `feature_dump/src/presentationMain/kotlin/sanctuary/app/feature/dump/presentation/screen/InsightHistoryScreen.kt` (NEW)

```kotlin
package sanctuary.app.feature.dump.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import sanctuary.app.feature.dump.presentation.state.InsightUiModel

@Composable
fun InsightHistoryScreen(
    insights: List<InsightUiModel>,
    onInsightClick: (String) -> Unit,
    onFilterClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Header
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "My Journey",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Reflecting on your past thoughts and breakthroughs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("Search your thoughts...") },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(8.dp),
            )
            IconButton(onClick = onFilterClick) {
                Icon(Icons.Default.Tune, contentDescription = "Filters")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Info message about 15-day deletion
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Insights older than 15 days will be deleted. Archive important ones to keep them safe.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Insights list
        if (insights.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("No insights yet. Start recording to create your first insight!")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(insights, key = { it.id }) { insight ->
                    InsightCard(
                        insight = insight,
                        onClick = { onInsightClick(insight.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightCard(
    insight: InsightUiModel,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Color(android.graphics.Color.parseColor(insight.sentimentColor))),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Type and date
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = insight.recordingType,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
                Text(
                    text = insight.formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Preview
            Text(
                text = insight.summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = androidx.compose.material3.TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Emotions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                insight.emotions.take(2).forEach { emotion ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = emotion,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action
            Text(
                text = "Read insight →",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
```

#### Test Cases - Phase 7

- [ ] **InsightGenerationScreen**: Renders loading indicator + text, no errors
- [ ] **YourReflectionScreen**: Displays all insight fields correctly (title, summary, emotions, path forward)
- [ ] **Sentiment Color**: Verify card background color matches sentiment (positive/negative/neutral)
- [ ] **Emotion Tags**: Display 2-3 emotion tags in list, 3 in detail
- [ ] **Recording/Transcription Access**: Buttons navigate to respective views
- [ ] **Regenerate Button**: Triggers regeneration flow
- [ ] **InsightHistoryScreen**: Displays list of insights
- [ ] **Info Message**: Shows "15-day deletion" notice
- [ ] **Search/Filter**: Search bar functional, filter button triggers filter screen
- [ ] **Empty State**: Shows message when no insights
- [ ] **Card Clickability**: Click insight card opens detail view
- [ ] **Date/Time Formatting**: Verify formatted dates display correctly
- [ ] **Archive Button**: Archive/unarchive insights from detail or list view

---

### Phase 8: Testing & Integration

#### Objective
End-to-end testing across all layers, edge cases, and platform-specific validation.

#### Changes Required

**Test Suite Locations**: `feature_dump/src/*/kotlin/sanctuary/app/feature/dump/test/`

**Categories of Tests**:

1. **Unit Tests** (Domain Layer)
   - Model creation and validation
   - Rate limit calculations
   - Request status transitions

2. **Integration Tests** (Data Layer)
   - Database operations with migrations
   - Encryption/decryption
   - Foreign key constraints
   - Concurrent insertions

3. **Feature Tests** (ViewModel + Repository)
   - Full insight generation flow
   - Rate limiting behavior
   - Request queuing and retry
   - Error handling and fallbacks

4. **UI Tests** (Compose)
   - Screen rendering
   - Button interactions
   - Navigation between screens
   - List scrolling and filtering

5. **End-to-End Tests** (Platform)
   - Record audio → Generate insight → View detail → Archive
   - Hit rate limit → Queue request → Process next day
   - 15-day auto-deletion
   - SQLCipher encryption

#### Test Cases - Phase 8

- [ ] **E2E Record-Generate-Save**: Record audio → Generate insight → Save → View in history
- [ ] **Rate Limit E2E**: Generate 4 insights → 5th triggers queue → Next day processes queue
- [ ] **Archive/Delete E2E**: Archive insight → Verify not in active list → Search still finds it
- [ ] **Timeout Recovery**: Timeout during generation → User retries → Succeeds
- [ ] **Network Offline**: Start generation offline → Queue automatically → Online later → Process
- [ ] **Encryption**: Insert insight with transcription → Verify encrypted in DB → Decrypt on retrieve
- [ ] **15-Day Auto-Delete**: Insert insights → 16 days pass → Old ones deleted, archived ones preserved
- [ ] **Special Characters**: Transcription with emojis/unicode → Insight generates correctly
- [ ] **Very Short Audio**: < 5 second recording → Insight still generates
- [ ] **Very Long Transcription**: 10,000+ word transcription → Handled correctly
- [ ] **Self-Harm Detection**: Transcription mentions suicide → AI suggests professional help
- [ ] **No Medical Suggestions**: All generated insights reviewed to ensure no medication advice
- [ ] **Concurrent Operations**: 2 simultaneous insight generations → Both complete without conflicts
- [ ] **Platform Consistency**: Run same tests on Android + iOS → Identical results
- [ ] **Performance**: Generate 10+ insights → Memory stable, no leaks
- [ ] **Database Integrity**: Force crash during insight save → Recovery works, no data loss

---

## File Change Summary

| File | Change | Phase |
|------|--------|-------|
| `core_database/build.gradle.kts` | Add SQLCipher, update schema version to 3 | 1 |
| `core_database/src/commonMain/kotlin/.../SanctuaryDatabase.kt` | Encryption initialization | 1 |
| `core_database/src/commonMain/sqldelight/.../insights.sq` | NEW - Insights table schema | 1 |
| `core_database/src/commonMain/sqldelight/.../rate_limits.sq` | NEW - Rate limit tracking | 1 |
| `core_database/src/commonMain/sqldelight/.../request_queue.sq` | NEW - Request queue table | 1 |
| `core_database/src/commonMain/sqldelight/.../migrations/2.sqm` | Migration for v2 schema | 1 |
| `core_database/src/commonMain/sqldelight/.../migrations/3.sqm` | Migration for v3 (insights tables) | 1 |
| `feature_dump/src/domainMain/.../domain/model/Insight.kt` | NEW - Insight domain model | 2 |
| `feature_dump/src/domainMain/.../domain/model/RateLimit.kt` | NEW - RateLimit model | 2 |
| `feature_dump/src/domainMain/.../domain/model/InsightGenerationRequest.kt` | NEW - Queue request model | 2 |
| `feature_dump/src/domainMain/.../domain/model/InsightGenerationResult.kt` | NEW - Result sealed class | 2 |
| `feature_dump/src/domainMain/.../domain/repository/InsightRepository.kt` | NEW - Insight repository interface | 3 |
| `feature_dump/src/domainMain/.../domain/service/InsightGenerationService.kt` | NEW - Generation service interface | 4 |
| `feature_dump/src/domainMain/.../domain/service/RateLimitManager.kt` | NEW - Rate limit manager interface | 5 |
| `feature_dump/src/domainMain/.../domain/service/RequestQueueManager.kt` | NEW - Queue manager interface | 5 |
| `feature_dump/src/dataMain/.../data/datasource/InsightLocalDataSource.kt` | NEW - Data source interface | 3 |
| `feature_dump/src/dataMain/.../data/datasource/InsightLocalDataSourceImpl.kt` | NEW - Data source implementation | 3 |
| `feature_dump/src/dataMain/.../data/mapper/InsightMapper.kt` | NEW - Insight mappers | 3 |
| `feature_dump/src/dataMain/.../data/repository/InsightRepositoryImpl.kt` | NEW - Repository implementation | 3 |
| `feature_dump/src/dataMain/.../data/service/ClaudeInsightGenerationService.kt` | NEW - Claude API provider | 4 |
| `feature_dump/src/dataMain/.../data/service/RateLimitManagerImpl.kt` | NEW - Rate limit implementation | 5 |
| `feature_dump/src/dataMain/.../data/service/RequestQueueManagerImpl.kt` | NEW - Queue implementation | 5 |
| `feature_dump/src/dataMain/.../data/di/InsightModule.kt` | NEW - DI configuration | 4 |
| `feature_dump/src/dataMain/.../data/di/RateLimitModule.kt` | NEW - DI configuration | 5 |
| `feature_dump/src/presentationMain/.../presentation/viewmodel/DumpViewModel.kt` | Add insight generation logic | 6 |
| `feature_dump/src/presentationMain/.../presentation/state/InsightUiModel.kt` | NEW - Insight UI model | 7 |
| `feature_dump/src/presentationMain/.../presentation/screen/InsightGenerationScreen.kt` | NEW - Loading screen | 7 |
| `feature_dump/src/presentationMain/.../presentation/screen/YourReflectionScreen.kt` | NEW - Detail screen | 7 |
| `feature_dump/src/presentationMain/.../presentation/screen/InsightHistoryScreen.kt` | NEW - List/history screen | 7 |

---

## Verification Checklist

### Phase 1: Database
- [ ] `./gradlew :composeApp:assembleDebug` compiles cleanly
- [ ] SQLCipher encryption configured correctly
- [ ] All 3 new tables created (insights, rate_limits, request_queue)
- [ ] Migrations applied without errors on fresh install and upgrades
- [ ] Foreign key relationships verified

### Phase 2: Domain Models
- [ ] All data classes serialize/deserialize correctly
- [ ] Enums cover all required values
- [ ] Null handling works as expected

### Phase 3: Data Layer
- [ ] Insert, retrieve, update, delete operations work
- [ ] Emotions stored as JSON, parsed correctly
- [ ] Rate limit calculations accurate
- [ ] Request queue ordering (FIFO)

### Phase 4: API Layer
- [ ] Claude API integration tested with mock responses
- [ ] JSON parsing handles valid/invalid responses
- [ ] Timeout (5-10 sec) enforced
- [ ] No medication suggestions in generated content
- [ ] Self-harm detection included in prompt

### Phase 5: Rate Limiting & Queue
- [ ] 4 calls/day limit enforced
- [ ] Daily quota resets at midnight
- [ ] Failed requests queued for retry
- [ ] Max retry limit (3) respected
- [ ] Queue processed in order

### Phase 6: ViewModel
- [ ] Insight generation triggered after recording save
- [ ] State transitions correct (Saving → Loading → Success/Error)
- [ ] Side effects emitted properly
- [ ] Error handling doesn't crash app

### Phase 7: UI
- [ ] All 3 screens render without errors
- [ ] Navigation between screens works
- [ ] Sentiment colors update dynamically
- [ ] Archive/unarchive functionality
- [ ] Delete confirmation dialog
- [ ] Empty state message

### Phase 8: Integration & E2E
- [ ] Record → Generate → Save → View full flow works
- [ ] Rate limit → Queue → Retry full flow works
- [ ] 15-day deletion with archive protection works
- [ ] Encryption/decryption transparent to user
- [ ] Android & iOS both functional
- [ ] No memory leaks during long usage
- [ ] No data loss on app crash/restart

---

## V2 Roadmap (Future Enhancements)

These features are out of scope for V1 but the architecture is designed to support them:

1. **Action Layer**
   - Add "actions" field to Insight (free-text suggestions)
   - Future: Convert to calendar events, task reminders
   - External system tagging (#calendar, #reminder, #task)

2. **External Integrations**
   - Google Calendar integration: "Add this meeting to calendar"
   - Task managers: "Create task from insight"
   - Slack: Share insights with teammates
   - Plugin architecture for extensibility

3. **Advanced Analytics**
   - Emotion trends over time
   - Sentiment distribution
   - Recurring themes/patterns
   - Peer comparisons (if multi-user)

4. **Personalization**
   - Custom emotion taxonomies per user
   - AI provider selection (Claude vs OpenAI vs other)
   - Tone customization (clinical vs warm vs poetic)
   - Content filtering/sensitivity levels

5. **Backup & Sync**
   - Backend API sync for insights
   - Cloud backup with end-to-end encryption
   - Cross-device sync
   - Data export/import

---

## Implementation Order & Timeline

**Recommended Phasing:**

1. **Phase 1 (Database)** - Foundation, must complete first
2. **Phase 2 (Domain Models)** - Enables all layers above
3. **Phase 3 (Data Layer)** - Must complete before API
4. **Phase 4 (API Layer)** - Can be tested with mocks
5. **Phase 5 (Rate Limiting)** - Can be tested in isolation
6. **Phase 6 (ViewModel)** - Wires everything together
7. **Phase 7 (UI)** - Can be built in parallel with Phase 6
8. **Phase 8 (Testing)** - Final validation across all layers

---

## Notes for Implementation

1. **Encryption Passphrase**: How should the SQLCipher passphrase be generated/stored? Consider using Android Keystore / iOS Keychain.

2. **API Key Management**: Claude API key should be injected from a secure config, not hardcoded. Consider BuildConfig or environment variables.

3. **Background Tasks**: For queue processing and auto-deletion, consider using WorkManager (Android) or equivalent on iOS.

4. **Sentiment Color Mapping**: Define exact color codes for POSITIVE (green-ish), NEGATIVE (red-ish), NEUTRAL (gray-ish).

5. **Search & Filter Implementation**: Phase 7 shows UI; actual filtering logic can be added in Phase 7 details.

6. **Accessibility**: Ensure all screens follow Material 3 accessibility guidelines (contrast, text size, keyboard navigation).

