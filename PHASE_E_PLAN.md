# Phase E: Insight Generation Service — Implementation Plan

## Context

The v2 recording pipeline uses a hexagonal ("ports & adapters") architecture where domain ports define interfaces and data-layer implementations depend on them. Phase A was supposed to create `InsightPort.kt` in `domain/port/` but the file was never created — instead, `InsightGenerationService` in `domain/service/` exists and both Claude and Groq services already implement it.

Phase E completes the port migration:
1. Create the missing `InsightPort` in `domain/port/`
2. Redirect both service implementations to implement `InsightPort` (instead of `InsightGenerationService`)
3. Rebind `InsightPort` in Koin DI
4. Update `InsightRepositoryImpl` (the sole runtime caller) to depend on `InsightPort`

This is pure refactoring — zero logic changes. After Phase E, `RecordingProcessingEngine` (Phase F) can safely declare a constructor dependency on `InsightPort` without touching any pre-existing service code.

---

## Current State

| File | Status |
|------|--------|
| `domain/service/InsightGenerationService.kt` | Exists — old location for the interface |
| `domain/port/InsightPort.kt` | **Missing** — should have been created in Phase A |
| `ClaudeInsightGenerationService.kt` | Implements `InsightGenerationService` (needs to implement `InsightPort`) |
| `GroqInsightGenerationService.kt` | Implements `InsightGenerationService` (needs to implement `InsightPort`) |
| `InsightModule.kt` | Binds `single<InsightGenerationService>` (needs to bind `InsightPort`) |
| `InsightRepositoryImpl.kt` | Constructor-injects `InsightGenerationService` (needs `InsightPort`) |

---

## Files to Create

### 1. `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/port/InsightPort.kt` *(new)*

```kotlin
package sanctuary.app.feature.dump.domain.port

import sanctuary.app.feature.dump.domain.model.Insight

/**
 * Domain port for AI-powered insight generation.
 *
 * Defines the boundary between the domain and any external AI provider.
 * Implementations live in the data layer (Claude, Groq, etc.) and are
 * wired via DI — the domain and processing engine never depend on a
 * concrete provider.
 *
 * Throws on failure; the caller is responsible for checking retryability
 * against [ProcessingErrorCode].
 */
interface InsightPort {
    suspend fun generateInsight(recordingId: String, transcription: String): Insight
}
```

---

## Files to Modify

### 2. `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/service/ClaudeInsightGenerationService.kt`

- Remove: `import sanctuary.app.feature.dump.domain.service.InsightGenerationService`
- Add: `import sanctuary.app.feature.dump.domain.port.InsightPort`
- Change: `internal class ClaudeInsightGenerationService(...) : InsightGenerationService`
  → `internal class ClaudeInsightGenerationService(...) : InsightPort`

No other changes.

### 3. `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/service/GroqInsightGenerationService.kt`

Same changes as (2) — swap `InsightGenerationService` → `InsightPort` in import and class declaration.

### 4. `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/di/InsightModule.kt`

- Remove: `import sanctuary.app.feature.dump.domain.service.InsightGenerationService`
- Add: `import sanctuary.app.feature.dump.domain.port.InsightPort`
- Change: `single<InsightGenerationService> { ... }` → `single<InsightPort> { ... }`

### 5. `feature_dump/src/dataMain/kotlin/sanctuary/app/feature/dump/data/repository/InsightRepositoryImpl.kt`

- Remove: `import sanctuary.app.feature.dump.domain.service.InsightGenerationService`
- Add: `import sanctuary.app.feature.dump.domain.port.InsightPort`
- Change constructor param: `generationService: InsightGenerationService` → `generationService: InsightPort`

> **Why this file is included (not in original Phase E list):**
> The Koin `single<InsightPort>` binding and the `InsightRepositoryImpl` constructor must agree on the type. Changing one without the other causes a runtime DI injection failure.

### 6. `feature_dump/src/domainMain/kotlin/sanctuary/app/feature/dump/domain/service/InsightGenerationService.kt`

**Delete** this file. It is fully replaced by `InsightPort`. Confirmed no other callers exist.

---

## What Does NOT Change

- Method signatures on any service (no logic changes whatsoever)
- `InsightModule.android.kt` / `InsightModule.ios.kt` — `expect` functions for API keys are unaffected
- Any use case, presentation layer, or other repository code

---

## Verification

```bash
# Compiles shared KMP (domainMain + dataMain)
./gradlew :feature_dump:compileKotlinMetadata -q

# Compiles Android platform
./gradlew :feature_dump:compileDebugKotlinAndroid -q
```

Both must be green. Phase E is zero-logic refactoring — the only success criterion is clean compilation.
