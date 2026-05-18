# Ephyra: Modern Android Native Architecture Roadmap

Ephyra has undergone a fundamental architectural pivot. We have deliberately severed ties with legacy codebases, "historical shackles," and prior works (such as Tachiyomi and Mihon). Ephyra is no longer a fork; it is a ground-up, modern Android-exclusive application.

This document serves as the authoritative guide for our target architectural state.

---

## 🌟 Core Architectural Pillars

We prioritize native, mature Android solutions to drastically reduce maintenance overhead and maximize development speed.

### 1. 100% Android Exclusive
- **No Kotlin Multiplatform (KMP)**: All modules are standard Android modules (`com.android.application` or `com.android.library`). 
- **Simplified Source Sets**: We do not use `commonMain` or `androidMain`. All code resides in standard `src/main/` directories.

### 2. 100% Compile-Time Determinism
- **Dependency Injection**: We use **Hilt (Dagger)**. Every single dependency must be explicitly and statically resolved at compile time. Runtime reflective service locators (like Koin or custom registries) are strictly prohibited.
- **Fail Fast**: If the dependency graph is incomplete, the app must fail to compile.

### 3. Jetpack Native UI & State
- **Jetpack Navigation Compose**: We use official Android Navigation rather than third-party wrappers (like Voyager).
- **Android ViewModels**: State holders are built on `androidx.lifecycle.ViewModel` integrated directly with Hilt via `@HiltViewModel`.
- **Strict Unidirectional Data Flow (UDF)**: ViewModels expose a single, immutable `StateFlow<ViewState>` and consume discrete `Intent` or `Event` models. No two-way data binding.

### 4. Native Android Resources
- **Localization**: We rely entirely on native Android `res/values/strings.xml` for internationalization.
- **No Third-Party Asset Managers**: Libraries like `moko-resources` are prohibited. Android natively handles plurals, formatting, and RTL languages perfectly.

### 5. Content-Agnostic & Source-Agnostic Engine
- **Unified Identity**: Library content uses a Canonical ID, preventing duplicate series entries across different tracking services.
- **No Legacy Extensions**: The legacy `eu.kanade.*` extension API surface is permanently deprecated. We do not support insecure dynamic APK extensions.
- **Sandboxed Scrapers**: We favor sandboxed Javascript (QuickJS) or declarative scraping models that are Play Store compliant.

---

## 🛠️ Ground-Up Rewrite Phases

We are currently executing a phased transition to reach the target state described above.

### Phase 1: De-KMP & Build System Simplification [COMPLETE]
- Remove `org.jetbrains.kotlin.multiplatform` from all build scripts.
- Convert `:i18n`, `:source-api`, `:source-local` to standard Android libraries.
- Collapse all `commonMain` and `androidMain` directories into `main`.
- Remove Legacy references and verbiage such as i18n, source-api, source-local.

### Phase 2: Hilt Integration & DI Purge [COMPLETE]
- Setup Hilt/KSP globally in `libs.versions.toml` and root `build.gradle.kts`.
- Purge manual Injekt/Koin registries, `AppDependencyContainer`, and all Koin remnants.
- Annotate `App` with `@HiltAndroidApp` and implement standard `@Module` and `@Inject` bindings.
- Bridge WorkManager background jobs via an elegant Hilt `@EntryPoint` factory (`AppWorkerFactory`).
- Implement Hilt-backed deterministic `CoreContainer` compat-shim to facilitate zero-downtime incremental migration.

### Phase 3: Localization Simplification [COMPLETE]
- Strip out `moko-resources` completely from gradle and application code.
- Migrate all `commonMain/moko-resources` XML definitions directly to standard Android `src/main/res/values/strings.xml` structures.
- Systematically replace all class types (`StringResource`, `PluralsResource`) and imports with native Android resource `Int` IDs (`@StringRes`, `@PluralsRes`).

### Phase 4: Navigation & State Modernization [IN PROGRESS]
- Replace Voyager `Screen` objects with Jetpack Navigation Compose graphs.
- Convert `ScreenModel` implementations to `ViewModel` with `@HiltViewModel`.

### Phase 5: Database & Persistence [UPCOMING]
- Standardize on Room (SQLite) for structured relational data.
- Standardize on DataStore (Preferences) for key-value configuration.

### Phase 6: UI & Feature Parity
- **Settings**: Implement `PreferenceFragmentCompat` styled with Material 3.
- **Library**: Implement robust grid/list views with two-handed gesture support.
- **Reader**: Implement "Two-Handed" reading gesture (swipe bottom for next page).

### Phase 7: Remote Content Retrieval & Management
- Add Remote Content Source Manager 
- Add Remote Tracking Service Manager 
- Add Remote Content Scanner
- Add Remote Content Search
- Add Local Content Manager 
- Add Local Content Scanner
- Add Local Content Search
- Add Merge Content
- Add Content Details
- Add Content opprotunistic Merge
- Add Content Sync Services (Remote and Local)
- Add Content retrieval preferance heirachy
- Add Update Checker
- Add Downloader

### Phase 8: Clean Arch & Domain Separation
- Identify core business logic in Repository and UseCase layers.
- Abstract `ContentDatabase`, `RemoteSource`, `TrackingService` behind interfaces in the domain layer.
- Enforce `sealed class Result` wrappers to handle remote/local data operations explicitly.

### Phase 9: Advanced Features & Polish
- Add Migration tools (Tachiyomi and Mihon)
- Improve performance and memory usage
- Improve battery efficiency
- Add support for more content types
- Add support for more tracking services
- Add support for more content sources
- Add support for more content formats
- Add support for more content encodings
- Add support for more content protocols
- Add support for more content providers

### Phase 10: Improve app polish
- improve ui and user experience
- improve app onboarding
- improve app performance and memory usage
- improve app battery efficiency
- improve app stability
- improve app security
- improve app accessibility
- improve app test coverage

### Phase 11: Refine app architecture and organization
- improve app architecture
- improve app organization and heirachy
- ensure 100% compile time determinism
- ensure 100% jetpack native 

#### Long Term Goals
- release app to play store
- maintain app
- include other platform ports
- include media manager and organizer
- improve cross-platform features and parity
- improve android native integrations
- add desktop mode support
- add tablet mode support
- add chromeos support
- add Wear OS support
- add Android TV support
- improve support for other platforms
- improve support for other content types
- improve support for other tracking services
- improve support for other content sources
- improve support for other content formats
- improve support for other content encodings
- improve support for other content protocols
- improve support for other content providers


### Phase 12: Release
- release app to play store




