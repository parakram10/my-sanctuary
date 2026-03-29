# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Sanctuary is a Kotlin Multiplatform (KMP) mental health/wellness mobile app targeting Android and iOS using Jetpack Compose Multiplatform. It uses an MVI architecture pattern with clean layered separation.

## Build & Run Commands

```bash
# Build Android debug APK
./gradlew :composeApp:assembleDebug

# Build all modules
./gradlew build

# Run Android app (requires emulator/device)
./gradlew :composeApp:installDebug

# Clean build
./gradlew clean

# iOS: Open and build via Xcode
open iosApp/iosApp.xcodeproj
```

No test targets exist yet. No linting/formatting tools are configured.

## Module Structure

| Module | Purpose |
|---|---|
| `composeApp/` | Android app entry point + shared Compose root (`App()`) |
| `shared/` | Shared business logic, string localization, platform abstractions |
| `core_ui/` | Reusable Compose components, theming (`SanctuaryTheme`), dimensions |
| `core_network/` | Ktor HTTP client setup with platform-specific engines |
| `core_database/` | SQLDelight database (tables: thoughts, emotions, summaries) |
| `feature_dump/` | Mental dump feature |
| `feature_history/` | History feature |
| `feature_summary/` | Summary feature |
| `feature_home/` | Home screen feature |
| `iosApp/` | iOS Xcode project, Swift entry point, localization files |

## Architecture

### Layered Source Sets per Feature Module

Each feature module uses custom Gradle source sets with strict dependency rules:

- **domainMain** → depends only on `:shared` (use cases, domain models)
- **presentationMain** → depends on `core_ui`, Compose (ViewModels, screens)
- **dataMain** → depends on `core_network`, `core_database` (repositories, data sources)
- **androidMain / iosMain** → platform-specific implementations

### MVI ViewModel Pattern

Base class: `BaseStateMviViewModel<ViewIntent, DataState, ViewState, ViewSideEffect>` in `composeApp/src/commonMain/.../presentation/`

- `DataState` = internal state; `ViewState` = UI-facing state (mapped via `mapDataToViewState`)
- Side effects dispatched via a `Channel` for one-shot events
- All ViewModels extend this base and implement `handleIntent()`

### Dependency Injection

Koin (v4.1.0) with `expect/actual` platform modules:
- Common: `sharedKoinModules()` in `composeApp/.../di/PlatformKoinModule.kt`
- Platform-specific: `platformAppModule()` via `expect/actual`

### Navigation

Bottom tab navigation with 3 destinations: Home, History, Settings. Enum-based routing in `core_ui/.../components/BottomNavigationBar.kt`.

## Localization System

Enum-based: `AppString` enum in `shared/` where each enum name (lowercased) maps to a localization key.

- **Android**: `StringResolver` uses `Context.getIdentifier()` → `getString()`. Resources in `composeApp/src/androidMain/res/values/strings.xml` (English) and `values-hi/strings.xml` (Hindi).
- **iOS**: `StringResolver` uses `NSBundle.localizedStringForKey()`. Resources in `iosApp/iosApp/en.lproj/Localizable.strings`.
- Supports format specifiers: `%@`, `%s`, `%d`, `%f`, indexed (`%1$d`), and escaped `%%`.
- Locale pruning Gradle task keeps only en/hi resource directories.

## Key Conventions

- Package namespace: `sanctuary.app.*`
- Platform abstraction via Kotlin `expect/actual`
- Anchor objects (`*Anchor.kt`) mark logical groupings within modules
- Compose resources (drawables) auto-generated in `generated/resources/`
- SQLDelight generates type-safe query code from `.sq` files in `core_database/src/commonMain/sqldelight/`

## Build Configuration

- Kotlin: 2.2.20, Compose Multiplatform: 1.10.3, AGP: 8.9.3
- Android: compileSdk 35, minSdk 26, Java 17
- iOS: arm64 + simulator targets, static KMP framework
- Gradle wrapper: 8.11.1, JVM heap: 2048MB
