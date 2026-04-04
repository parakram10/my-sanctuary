# Phase F Documentation Index

**Created:** 2026-04-04  
**Status:** Complete — Ready for Implementation  
**Scope:** Core Processing Engine (RecordingProcessingEngine)

---

## 📚 Documentation Files

### 1. **PHASE_F_IMPLEMENTATION_PLAN.md** (110 sections)
**Read this FIRST for complete understanding**

- ✅ Full architecture overview
- ✅ 11 implementation steps with code snippets
- ✅ 8+ unit test cases with examples
- ✅ Edge cases & gotchas analysis
- ✅ Compilation & verification steps
- ✅ Success criteria checklist
- ✅ Detailed FSM diagram

**Time to read:** 30-45 minutes  
**Best for:** Understanding the full scope and design

---

### 2. **PHASE_F_QUICK_REFERENCE.md** (compact)
**Read this for a quick overview**

- ✅ 1-minute summary
- ✅ File structure
- ✅ Class signature
- ✅ 5 core concepts (single-flight, FSM, checkpoint, errors, retry)
- ✅ Implementation checklist
- ✅ Key methods summary
- ✅ Critical edge cases
- ✅ Testing checklist

**Time to read:** 10-15 minutes  
**Best for:** Quick reference while coding

---

### 3. **PHASE_F_CODE_STRUCTURE.md** (detailed)
**Read this while implementing**

- ✅ 8 part-by-part code examples
- ✅ Architecture diagram
- ✅ File locations and structure
- ✅ Complete method implementations with comments
- ✅ DI integration
- ✅ Implementation checklist with phases
- ✅ Common patterns & examples
- ✅ Testing patterns
- ✅ Debugging tips
- ✅ Common errors & fixes

**Time to read:** 20-30 minutes  
**Best for:** Step-by-step implementation guide

---

## 🎯 How to Use These Documents

### Scenario 1: "I want to understand Phase F"
1. Read: **PHASE_F_QUICK_REFERENCE.md** (10 min)
2. Read: **PHASE_F_IMPLEMENTATION_PLAN.md** (30 min)
3. Result: Full understanding of architecture, FSM, single-flight, retry logic

### Scenario 2: "I'm ready to start implementing"
1. Read: **PHASE_F_QUICK_REFERENCE.md** (quick overview)
2. Open: **PHASE_F_CODE_STRUCTURE.md** (side by side with IDE)
3. Follow: Implementation checklist (Step 1 → Step 9)
4. Reference: **PHASE_F_IMPLEMENTATION_PLAN.md** (for details)

### Scenario 3: "I'm stuck on a specific issue"
1. Check: **PHASE_F_CODE_STRUCTURE.md** → Common Errors & Fixes
2. Check: **PHASE_F_IMPLEMENTATION_PLAN.md** → Edge Cases section
3. Check: **PHASE_F_QUICK_REFERENCE.md** → Critical Edge Cases

### Scenario 4: "I'm writing tests"
1. Reference: **PHASE_F_IMPLEMENTATION_PLAN.md** → Section 5 (Testing Strategy)
2. Reference: **PHASE_F_CODE_STRUCTURE.md** → Section 6 (Testing Patterns)
3. Copy test cases from PHASE_F_IMPLEMENTATION_PLAN.md

---

## 📋 Quick Navigation

### By Topic

#### Single-Flight Mutex
- PHASE_F_IMPLEMENTATION_PLAN.md → Step 1 (page ~30)
- PHASE_F_CODE_STRUCTURE.md → Part 3 (code example)
- PHASE_F_QUICK_REFERENCE.md → Concept 1

#### FSM (State Machine)
- PHASE_F_IMPLEMENTATION_PLAN.md → Step 2 (page ~35), FSM Diagram (appendix)
- PHASE_F_CODE_STRUCTURE.md → Part 4 (code example)
- PHASE_F_QUICK_REFERENCE.md → Concept 2

#### Checkpoint Logic
- PHASE_F_IMPLEMENTATION_PLAN.md → Step 3 (page ~40)
- PHASE_F_CODE_STRUCTURE.md → Part 5 (code example)
- PHASE_F_QUICK_REFERENCE.md → Concept 3

#### Error Handling & Retry
- PHASE_F_IMPLEMENTATION_PLAN.md → Step 4 (page ~45), Section 6 (edge cases)
- PHASE_F_CODE_STRUCTURE.md → Part 6 (code example)
- PHASE_F_QUICK_REFERENCE.md → Concept 4

#### DI Integration
- PHASE_F_IMPLEMENTATION_PLAN.md → Step 5 (page ~50)
- PHASE_F_CODE_STRUCTURE.md → Part 8 (code example)
- PHASE_F_QUICK_REFERENCE.md → DI Integration section

#### Testing
- PHASE_F_IMPLEMENTATION_PLAN.md → Section 5 (page ~55)
- PHASE_F_CODE_STRUCTURE.md → Section 6 (testing patterns)
- PHASE_F_QUICK_REFERENCE.md → Testing section

---

## 🔧 Implementation Workflow

```
1. PRE-IMPLEMENTATION
   ├─ Read PHASE_F_QUICK_REFERENCE.md (10 min)
   ├─ Read PHASE_F_IMPLEMENTATION_PLAN.md sections 1-2 (15 min)
   └─ Understand: single-flight, FSM, checkpoint, error handling

2. SCAFFOLDING (30 min)
   ├─ Create RecordingProcessingEngine.kt interface
   ├─ Create RecordingProcessingEngineImpl.kt skeleton
   ├─ Add imports and class definition
   └─ Compile: ./gradlew :feature_dump:compileKotlinMetadata -q

3. IMPLEMENTATION (3-4 hours)
   ├─ Step 1: Implement process() single-flight
   │  └─ Reference: PHASE_F_CODE_STRUCTURE.md Part 3
   ├─ Step 2: Implement executeProcessing() FSM
   │  └─ Reference: PHASE_F_CODE_STRUCTURE.md Part 4
   ├─ Step 3: Implement performTranscription() checkpoint
   │  └─ Reference: PHASE_F_CODE_STRUCTURE.md Part 5
   ├─ Step 4: Implement performInsightGeneration()
   │  └─ Reference: PHASE_F_CODE_STRUCTURE.md Part 7
   ├─ Step 5: Implement handleError() + classifyError()
   │  └─ Reference: PHASE_F_CODE_STRUCTURE.md Part 6
   ├─ Step 6: Add DI binding
   │  └─ Reference: PHASE_F_CODE_STRUCTURE.md Part 8
   └─ Compile after each step

4. TESTING (1-2 hours)
   ├─ Create RecordingProcessingEngineTest.kt
   ├─ Write 8+ test cases
   │  └─ Reference: PHASE_F_IMPLEMENTATION_PLAN.md Section 5
   ├─ Run tests
   └─ All tests must pass

5. VERIFICATION (30 min)
   ├─ Compile metadata
   ├─ Compile Android
   ├─ Compile iOS (expect Phase D errors)
   ├─ Check success criteria
   └─ Ready for Phase G
```

---

## 📊 Document Sizes

| Document | Size | Sections | Read Time |
|----------|------|----------|-----------|
| PHASE_F_IMPLEMENTATION_PLAN.md | ~400 lines | 11 major | 30-45 min |
| PHASE_F_QUICK_REFERENCE.md | ~250 lines | 11 major | 10-15 min |
| PHASE_F_CODE_STRUCTURE.md | ~500 lines | 8 major | 20-30 min |

**Total documentation:** ~1150 lines, 60-90 minutes to fully read

---

## ✅ Success Checklist

### Before Starting
- [ ] Phase A (domain models) ✅ complete
- [ ] Phase B (database) ✅ complete
- [ ] Phase C (repositories) ✅ complete
- [ ] Phase D (transcription) ✅ available
- [ ] Phase E (insights) ✅ complete
- [ ] All documents read and understood

### During Implementation
- [ ] RecordingProcessingEngine.kt created
- [ ] RecordingProcessingEngineImpl.kt created (all methods)
- [ ] Single-flight mutex works
- [ ] FSM transitions correct (5 states)
- [ ] Checkpoint skips transcription
- [ ] Error classification (transient/permanent)
- [ ] Retry logic correct
- [ ] DI binding added
- [ ] Compiles clean

### After Implementation
- [ ] Unit tests written (8+)
- [ ] All tests pass
- [ ] Edge cases tested
- [ ] Compiles: metadata ✅
- [ ] Compiles: Android ✅
- [ ] Compiles: iOS (Phase D errors OK)

---

## 🚀 What Comes Next

After Phase F is complete:

1. **Phase G (Android WorkManager)** — can call `engine.process(recordingId)`
2. **Phase H (iOS Background Scheduler)** — can call `engine.process(recordingId)`
3. **Phase I (Presentation Layer)** — can inject engine for retry handling

---

## 💡 Key Insights from Phase F

### 1. Single-Flight Mutex
The most critical pattern. Prevents duplicate processing via `ConcurrentHashMap<recordingId, Job>`.

### 2. Checkpoint Optimization
Skip expensive transcription if already cached. Reduces API calls on retry.

### 3. Transient vs Permanent Classification
Not all errors should retry. Rate limit is permanent (no retry). Network timeout is transient (retry once, then queue for WM).

### 4. Auto-Retry Once
The engine retries transient errors **once** (locally). If that fails, marks FAILED and lets WorkManager handle further retries. This prevents infinite local retries.

### 5. DB as Checkpoint
The database is the single source of truth. If processing fails mid-way, the next attempt reads the current DB state and continues (e.g., uses cached transcript).

---

## 📞 Questions?

### Common Questions

**Q: Why single-flight?**  
A: Prevents duplicate work if called twice simultaneously. One process() executes, second waits for result.

**Q: Why checkpoint?**  
A: Skip expensive transcription if already done in previous attempt. Speeds up retries.

**Q: Why auto-retry once?**  
A: Handles transient errors immediately (network hiccup). If still fails after one retry, defer to WorkManager (won't hammer the API).

**Q: Why not use database lock?**  
A: In-memory mutex is faster. Database locks are for persistence, not concurrency control within app.

**Q: What if the app crashes mid-processing?**  
A: The recording's status is checkpointed in DB. Next app launch reads DB state and continues.

---

## 📖 Additional References

### Related Documents
- `CODE_REVIEW_PHASE_E.md` — Phase E review (dependency)
- `IMPLEMENTATION.md` — Overall implementation guide
- `docs/v2-implementation-roadmap.md` — Roadmap
- `docs/recording-pipeline-detailed-plan.md` — Architecture details

### Key Classes (Domain)
- `ProcessingStatus.kt` — PENDING, TRANSCRIBING, GENERATING_INSIGHT, COMPLETED, FAILED
- `ProcessingErrorCode.kt` — Error classification with retry eligibility
- `Recording.kt` — Model with status, transcription, error code
- `RecordingRepository.kt` — Interface for data access
- `InsightPort.kt` — Interface for insight generation
- `OnDeviceTranscriber.kt` — Interface for transcription

---

## 🎓 Learning Outcome

After completing Phase F, you will understand:

1. **Finite State Machines (FSM)** — State transitions, guard conditions, actions
2. **Concurrency Patterns** — Single-flight mutex, race condition handling
3. **Error Classification** — Transient vs permanent, retry eligibility
4. **Checkpoint Pattern** — Cache optimization, skip expensive operations
5. **Dependency Injection** — Koin integration, constructor injection
6. **Coroutine Patterns** — suspend functions, error handling, finally cleanup
7. **Testing Async Code** — Mocking suspend functions, testing concurrency

---

## 📝 Document Versions

| Document | Version | Date | Status |
|----------|---------|------|--------|
| PHASE_F_IMPLEMENTATION_PLAN.md | 1.0 | 2026-04-04 | Final |
| PHASE_F_QUICK_REFERENCE.md | 1.0 | 2026-04-04 | Final |
| PHASE_F_CODE_STRUCTURE.md | 1.0 | 2026-04-04 | Final |
| PHASE_F_DOCUMENTATION_INDEX.md | 1.0 | 2026-04-04 | Final |

---

**Happy implementing! 🚀**

