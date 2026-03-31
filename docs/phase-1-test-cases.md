# Phase 1: Database Schema, Encryption & Migrations - Test Cases

## Overview

Phase 1 test cases verify database schema creation, migrations, table relationships, and encryption setup. All tests should pass after implementation.

---

## Test Execution Environment

- **Android**: Unit tests run on JVM or Android emulator
- **iOS**: Tests run on iOS simulator or device
- **Common**: Tests in commonMain can verify schema across platforms

---

## 1. Schema Validation Tests

### Test 1.1: Recordings Table Schema
**Objective**: Verify recordings table has correct columns including new is_archived field

**Steps**:
1. Open database
2. Query table schema using SQLiteDatabase inspector or PRAGMA table_info
3. Verify columns: id, user_id, file_path, duration_ms, created_at, title, transcription, is_archived

**Expected Result**: 
- All 8 columns present
- id is PRIMARY KEY
- is_archived has DEFAULT 0
- Other columns have correct types (TEXT, INTEGER)

**Test Case**:
```kotlin
@Test
fun testRecordingsTableSchema() {
    val db = createTestDatabase()
    // Query table schema
    val cursor = db.rawQuery("PRAGMA table_info(recordings)", null)
    val columns = mutableMapOf<String, String>()
    while (cursor.moveToNext()) {
        columns[cursor.getString(1)] = cursor.getString(2) // name, type
    }
    cursor.close()
    
    assertTrue(columns.containsKey("id"))
    assertTrue(columns.containsKey("user_id"))
    assertTrue(columns.containsKey("file_path"))
    assertTrue(columns.containsKey("duration_ms"))
    assertTrue(columns.containsKey("created_at"))
    assertTrue(columns.containsKey("title"))
    assertTrue(columns.containsKey("transcription"))
    assertTrue(columns.containsKey("is_archived"))
}
```

---

### Test 1.2: Insights Table Schema
**Objective**: Verify insights table created with correct columns and foreign key

**Steps**:
1. Open database
2. Query insights table schema
3. Verify all required columns
4. Verify foreign key relationship to recordings table

**Expected Result**:
- Table exists with columns: id, recording_id, title, summary, full_summary, emotions_json, path_forward, recording_type, sentiment, created_at, is_archived, archived_at, status
- Foreign key constraint on recording_id → recordings(id) with CASCADE delete

**Test Case**:
```kotlin
@Test
fun testInsightsTableSchema() {
    val db = createTestDatabase()
    
    // Verify table exists
    val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='insights'", null)
    assertTrue(cursor.moveToFirst(), "insights table should exist")
    cursor.close()
    
    // Verify columns
    val schemaCursor = db.rawQuery("PRAGMA table_info(insights)", null)
    val columns = mutableListOf<String>()
    while (schemaCursor.moveToNext()) {
        columns.add(schemaCursor.getString(1))
    }
    schemaCursor.close()
    
    val expectedColumns = listOf(
        "id", "recording_id", "title", "summary", "full_summary",
        "emotions_json", "path_forward", "recording_type", "sentiment",
        "created_at", "is_archived", "archived_at", "status"
    )
    expectedColumns.forEach { column ->
        assertTrue(columns.contains(column), "Column '$column' should exist")
    }
}
```

---

### Test 1.3: Rate Limits Table Schema
**Objective**: Verify rate_limits table created correctly

**Steps**:
1. Verify table exists
2. Verify columns: id, date, api_calls_used, max_api_calls

**Expected Result**:
- Table has 4 columns with correct types
- date is INTEGER (epoch / 86400)
- api_calls_used, max_api_calls have defaults (0, 4)

**Test Case**:
```kotlin
@Test
fun testRateLimitsTableSchema() {
    val db = createTestDatabase()
    
    val cursor = db.rawQuery("PRAGMA table_info(rate_limits)", null)
    val columns = mutableMapOf<String, String>()
    while (cursor.moveToNext()) {
        columns[cursor.getString(1)] = cursor.getString(2)
    }
    cursor.close()
    
    assertEquals(4, columns.size, "rate_limits should have 4 columns")
    assertTrue(columns.containsKey("id"))
    assertTrue(columns.containsKey("date"))
    assertTrue(columns.containsKey("api_calls_used"))
    assertTrue(columns.containsKey("max_api_calls"))
}
```

---

### Test 1.4: Request Queue Table Schema
**Objective**: Verify request_queue table created correctly

**Steps**:
1. Verify table exists
2. Verify columns: id, recording_id, created_at, retry_count, max_retries, status, error_message

**Expected Result**:
- Table has 7 columns
- Foreign key on recording_id with CASCADE delete
- status is NOT NULL

**Test Case**:
```kotlin
@Test
fun testRequestQueueTableSchema() {
    val db = createTestDatabase()
    
    val cursor = db.rawQuery("PRAGMA table_info(request_queue)", null)
    val columns = mutableListOf<String>()
    while (cursor.moveToNext()) {
        columns.add(cursor.getString(1))
    }
    cursor.close()
    
    val expectedColumns = listOf(
        "id", "recording_id", "created_at", "retry_count",
        "max_retries", "status", "error_message"
    )
    assertEquals(expectedColumns, columns)
}
```

---

## 2. Foreign Key Constraint Tests

### Test 2.1: Insights → Recordings Cascade Delete
**Objective**: Verify deleting a recording cascades delete to insights

**Steps**:
1. Insert a recording
2. Insert an insight with that recording_id
3. Delete the recording
4. Verify insight is also deleted

**Expected Result**: Insight deleted when recording deleted

**Test Case**:
```kotlin
@Test
fun testInsightCascadeDeleteOnRecordingDelete() = runTest {
    val recordingId = "rec-123"
    val insightId = "ins-456"
    
    // Insert recording
    recordingLocalDataSource.insert(Recording(
        id = recordingId,
        filePath = "/path/to/audio.m4a",
        durationMs = 10000L,
        createdAt = System.currentTimeMillis(),
        title = null,
        transcription = "Test transcription",
        userId = null,
    ))
    
    // Insert insight
    insightLocalDataSource.insertInsight(Insight(
        id = insightId,
        recordingId = recordingId,
        content = InsightContent(
            title = "Test Insight",
            summary = "Summary",
            fullSummary = "Full summary",
            emotions = listOf("Happy"),
            pathForward = "Keep going",
            recordingType = "REFLECTION",
            sentiment = Sentiment.POSITIVE,
        ),
        createdAt = System.currentTimeMillis(),
    ))
    
    // Verify insight exists
    assertNotNull(insightLocalDataSource.getInsightById(insightId))
    
    // Delete recording
    recordingLocalDataSource.deleteById(recordingId)
    
    // Verify insight is also deleted
    assertNull(insightLocalDataSource.getInsightById(insightId))
}
```

---

### Test 2.2: Request Queue → Recordings Cascade Delete
**Objective**: Verify deleting a recording cascades delete to request_queue entries

**Steps**:
1. Insert recording
2. Insert request queue entry for that recording
3. Delete recording
4. Verify request queue entry deleted

**Expected Result**: Request deleted when recording deleted

**Test Case**:
```kotlin
@Test
fun testRequestQueueCascadeDeleteOnRecordingDelete() = runTest {
    val recordingId = "rec-789"
    val requestId = "req-999"
    
    // Insert recording
    recordingLocalDataSource.insert(Recording(...))
    
    // Insert request queue
    insightLocalDataSource.insertRequest(InsightGenerationRequest(
        id = requestId,
        recordingId = recordingId,
        transcription = "Test",
        createdAt = System.currentTimeMillis(),
    ))
    
    // Verify request exists
    assertNotNull(insightLocalDataSource.getPendingRequests().find { it.id == requestId })
    
    // Delete recording
    recordingLocalDataSource.deleteById(recordingId)
    
    // Verify request is deleted
    assertNull(insightLocalDataSource.getPendingRequests().find { it.id == requestId })
}
```

---

## 3. Migration Tests

### Test 3.1: Migration 1 - Add Transcription Column
**Objective**: Verify migration 1 adds transcription column to recordings

**Steps**:
1. Create v1 database (without migration 1)
2. Apply migration 1
3. Verify transcription column exists

**Expected Result**: transcription column added without data loss

**Test Case**:
```kotlin
@Test
fun testMigration1AddTranscriptionColumn() {
    // This test would require creating a v1 schema database
    // For SQLDelight, migrations are managed automatically
    // Verify by checking that existing data is preserved
    val recordingId = "old-rec"
    
    // Insert using v1 schema (before migration)
    // Then verify column exists with null values
}
```

---

### Test 3.2: Migration 2 - Add is_archived Column to Recordings
**Objective**: Verify migration 2 adds is_archived column with default 0

**Steps**:
1. Apply migrations 1 and 2
2. Verify is_archived column exists on recordings table
3. Insert recording without is_archived, verify defaults to 0

**Expected Result**: New recordings have is_archived = 0 by default

**Test Case**:
```kotlin
@Test
fun testMigration2AddIsArchivedColumn() = runTest {
    val recording = Recording(
        id = "rec-123",
        userId = null,
        filePath = "/path/audio.m4a",
        durationMs = 5000L,
        createdAt = System.currentTimeMillis(),
        title = "Test",
        transcription = "Test transcription",
    )
    
    recordingLocalDataSource.insert(recording)
    
    val retrieved = recordingLocalDataSource.selectById("rec-123")
    assertEquals(0, retrieved?.is_archived)
}
```

---

### Test 3.3: Migration 3 - Create Insights, Rate Limits, Request Queue Tables
**Objective**: Verify migration 3 creates all three new tables

**Steps**:
1. Apply all 3 migrations
2. Verify all 3 tables exist with correct schema

**Expected Result**: Tables created with correct structure

**Test Case**:
```kotlin
@Test
fun testMigration3CreatesNewTables() {
    val db = getDatabase()
    
    // Verify insights table
    val insightsCursor = db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='insights'",
        null
    )
    assertTrue(insightsCursor.moveToFirst(), "insights table should exist")
    insightsCursor.close()
    
    // Verify rate_limits table
    val rateLimitsCursor = db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='rate_limits'",
        null
    )
    assertTrue(rateLimitsCursor.moveToFirst(), "rate_limits table should exist")
    rateLimitsCursor.close()
    
    // Verify request_queue table
    val queueCursor = db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='request_queue'",
        null
    )
    assertTrue(queueCursor.moveToFirst(), "request_queue table should exist")
    queueCursor.close()
}
```

---

### Test 3.4: Backward Compatibility - Existing Data Preserved
**Objective**: Verify existing recordings and transcriptions are preserved during migrations

**Steps**:
1. Create v1 database with existing recording + transcription
2. Apply all migrations
3. Retrieve recording, verify all original data intact
4. Verify new columns added with defaults

**Expected Result**: Original data preserved, new columns have defaults

**Test Case**:
```kotlin
@Test
fun testMigrationsPreserveExistingData() = runTest {
    val originalRecording = Recording(
        id = "old-rec",
        userId = "user-1",
        filePath = "/old/path.m4a",
        durationMs = 30000L,
        createdAt = 1609459200000L, // Some past date
        title = "Old Recording",
        transcription = "This is old transcription text",
    )
    
    // Insert before migrations (simulated)
    recordingLocalDataSource.insert(originalRecording)
    
    // Retrieve after migrations
    val retrieved = recordingLocalDataSource.selectById("old-rec")
    
    // Verify all original fields preserved
    assertEquals("old-rec", retrieved?.id)
    assertEquals("user-1", retrieved?.user_id)
    assertEquals("/old/path.m4a", retrieved?.file_path)
    assertEquals(30000L, retrieved?.duration_ms)
    assertEquals("Old Recording", retrieved?.title)
    assertEquals("This is old transcription text", retrieved?.transcription)
    
    // Verify new fields have defaults
    assertEquals(0, retrieved?.is_archived)
}
```

---

## 4. JSON Storage Tests

### Test 4.1: Emotions JSON Serialization
**Objective**: Verify emotions stored as JSON can be inserted and retrieved

**Steps**:
1. Create InsightContent with emotions list: ["Happy", "Grateful"]
2. Insert into insights table
3. Retrieve insight
4. Verify emotions list correctly deserialized

**Expected Result**: Emotions list preserved through serialization

**Test Case**:
```kotlin
@Test
fun testEmotionsJsonSerialization() = runTest {
    val emotions = listOf("Happy", "Grateful", "Peaceful")
    val insight = Insight(
        id = "ins-001",
        recordingId = "rec-001",
        content = InsightContent(
            title = "Good Day",
            summary = "Had a great day",
            fullSummary = "Full description",
            emotions = emotions,
            pathForward = "Keep it up",
            recordingType = "REFLECTION",
            sentiment = Sentiment.POSITIVE,
        ),
        createdAt = System.currentTimeMillis(),
    )
    
    insightLocalDataSource.insertInsight(insight)
    val retrieved = insightLocalDataSource.getInsightById("ins-001")!!
    
    assertEquals(emotions, retrieved.content.emotions)
}
```

---

## 5. NULL Handling Tests

### Test 5.1: archived_at Null When Not Archived
**Objective**: Verify archived_at is null for non-archived insights

**Steps**:
1. Insert insight without archiving (is_archived = 0)
2. Retrieve insight
3. Verify archived_at is null

**Expected Result**: archived_at is null

**Test Case**:
```kotlin
@Test
fun testArchivedAtNullWhenNotArchived() = runTest {
    val insight = Insight(
        id = "ins-001",
        recordingId = "rec-001",
        content = InsightContent(...),
        createdAt = System.currentTimeMillis(),
        isArchived = false,
        archivedAt = null,
    )
    
    insightLocalDataSource.insertInsight(insight)
    val retrieved = insightLocalDataSource.getInsightById("ins-001")!!
    
    assertNull(retrieved.archivedAt)
    assertEquals(false, retrieved.isArchived)
}
```

---

### Test 5.2: error_message Null for Successful Requests
**Objective**: Verify error_message is null for completed requests

**Steps**:
1. Insert completed request with null error_message
2. Retrieve request
3. Verify error_message is null

**Expected Result**: error_message is null

**Test Case**:
```kotlin
@Test
fun testErrorMessageNullForSuccessfulRequests() = runTest {
    val request = InsightGenerationRequest(
        id = "req-001",
        recordingId = "rec-001",
        transcription = "Test",
        createdAt = System.currentTimeMillis(),
        status = RequestStatus.COMPLETED,
        errorMessage = null,
    )
    
    insightLocalDataSource.insertRequest(request)
    val pending = insightLocalDataSource.getPendingRequests()
    val completed = insightLocalDataSource.updateRequestStatus("req-001", RequestStatus.COMPLETED)
    
    // Verify no error message
}
```

---

## 6. Performance Tests

### Test 6.1: Bulk Insert Performance
**Objective**: Verify inserting 1000+ insights doesn't cause significant slowdown

**Steps**:
1. Insert 1000 insights
2. Measure time taken
3. Retrieve all insights
4. Verify no timeout/crash

**Expected Result**: Completes in < 5 seconds

**Test Case**:
```kotlin
@Test
fun testBulkInsertPerformance() = runTest {
    val startTime = System.currentTimeMillis()
    
    repeat(1000) { i ->
        val insight = Insight(
            id = "ins-$i",
            recordingId = "rec-$i",
            content = InsightContent(...),
            createdAt = System.currentTimeMillis(),
        )
        insightLocalDataSource.insertInsight(insight)
    }
    
    val elapsed = System.currentTimeMillis() - startTime
    assertTrue(elapsed < 5000, "Bulk insert should complete in < 5 seconds, took $elapsed ms")
    
    val allInsights = insightLocalDataSource.getAllInsights()
    assertEquals(1000, allInsights.size)
}
```

---

### Test 6.2: Query Performance - SELECT by Recording ID
**Objective**: Verify queries by recording_id are fast

**Steps**:
1. Insert insights for 100 different recordings
2. Query insight by recording_id
3. Verify completes in < 100ms

**Expected Result**: Query returns in < 100ms

**Test Case**:
```kotlin
@Test
fun testQueryByRecordingIdPerformance() = runTest {
    val recordingId = "rec-benchmark"
    
    // Insert insight
    insightLocalDataSource.insertInsight(Insight(
        id = "ins-001",
        recordingId = recordingId,
        content = InsightContent(...),
        createdAt = System.currentTimeMillis(),
    ))
    
    val startTime = System.currentTimeMillis()
    val insight = insightLocalDataSource.getInsightByRecordingId(recordingId)
    val elapsed = System.currentTimeMillis() - startTime
    
    assertNotNull(insight)
    assertTrue(elapsed < 100, "Query should complete in < 100ms, took $elapsed ms")
}
```

---

## 7. Encryption Tests (TODO - Future Enhancement)

### Test 7.1: Database File is Encrypted
**Objective**: Verify database file is encrypted and unreadable without passphrase

**Steps**:
1. Create database with passphrase
2. Close database
3. Read database file directly
4. Verify file is encrypted (contains binary data, not SQL text)

**Expected Result**: File is encrypted

**Test Case**:
```kotlin
@Test
fun testDatabaseFileIsEncrypted() {
    // TODO: Implement when SQLCipher integration is complete
    // Read sanctuary.db file directly
    // Verify it's not readable as plain SQL text
}
```

---

## 8. Integration Tests

### Test 8.1: Full Table Relationships
**Objective**: Verify complete data flow through related tables

**Steps**:
1. Insert recording
2. Insert insight for that recording
3. Insert request queue entry for same recording
4. Query all three tables
5. Verify relationships intact

**Expected Result**: All data accessible through relationships

**Test Case**:
```kotlin
@Test
fun testFullTableRelationships() = runTest {
    val recordingId = "rec-integration"
    
    // Insert recording
    recordingLocalDataSource.insert(Recording(
        id = recordingId,
        filePath = "/path.m4a",
        durationMs = 10000L,
        createdAt = System.currentTimeMillis(),
        userId = null,
        title = null,
        transcription = "Transcription",
    ))
    
    // Insert insight
    insightLocalDataSource.insertInsight(Insight(
        id = "ins-001",
        recordingId = recordingId,
        content = InsightContent(...),
        createdAt = System.currentTimeMillis(),
    ))
    
    // Insert request
    insightLocalDataSource.insertRequest(InsightGenerationRequest(
        id = "req-001",
        recordingId = recordingId,
        transcription = "Transcription",
        createdAt = System.currentTimeMillis(),
    ))
    
    // Verify all three are related
    val recording = recordingLocalDataSource.selectById(recordingId)
    val insight = insightLocalDataSource.getInsightByRecordingId(recordingId)
    val requests = insightLocalDataSource.getPendingRequests()
    
    assertNotNull(recording)
    assertNotNull(insight)
    assertTrue(requests.any { it.id == "req-001" })
}
```

---

## Test Execution Checklist

Run these tests before marking Phase 1 complete:

- [ ] Test 1.1: Recordings table schema
- [ ] Test 1.2: Insights table schema
- [ ] Test 1.3: Rate limits table schema
- [ ] Test 1.4: Request queue table schema
- [ ] Test 2.1: Insights cascade delete
- [ ] Test 2.2: Request queue cascade delete
- [ ] Test 3.1: Migration 1 verification
- [ ] Test 3.2: Migration 2 verification
- [ ] Test 3.3: Migration 3 verification
- [ ] Test 3.4: Data preservation
- [ ] Test 4.1: Emotions JSON serialization
- [ ] Test 5.1: archived_at null handling
- [ ] Test 5.2: error_message null handling
- [ ] Test 6.1: Bulk insert performance
- [ ] Test 6.2: Query performance
- [ ] Test 8.1: Full table relationships

---

## Notes

1. **Test Framework**: Use JUnit 4 for Android, XCTest for iOS, or common test framework for KMP
2. **Test Isolation**: Each test should create a fresh database instance to avoid state pollution
3. **Async Testing**: Use `runTest` for coroutine-based tests
4. **Database Access**: Create helper functions to get test database instances
5. **Cleanup**: Ensure database is deleted after each test to maintain isolation

---

## Future Enhancements

- [ ] SQLCipher encryption tests (Test 7.1+)
- [ ] Performance benchmarks with 10,000+ records
- [ ] Concurrent access tests (multiple threads/coroutines)
- [ ] Database recovery tests (corruption simulation)
- [ ] Platform-specific tests (Android Keystore, iOS Keychain integration)
