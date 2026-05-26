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

### Phase 4: Navigation & State Modernization [COMPLETE]
- [x] Integrate Jetpack Navigation `NavHost` in `MainActivity`.
- [x] Decommission Voyager `TabNavigator` in `HomeScreen`:
    - [x] Map `LibraryTab`, `UpdatesTab`, `HistoryTab`, `BrowseTab`, `MoreTab` to Jetpack Navigation routes.
    - [x] Implement a Hilt-aware `BottomNavigationBar` that interacts with `NavController`.
- [x] Migrate `Screen` objects to Composable functions:
    - [x] `LibraryTab` -> `LibraryScreen` (Composable)
    - [x] `UpdatesTab` -> `UpdatesScreen` (Composable)
    - [x] `HistoryTab` -> `HistoryScreen` (Composable)
    - [x] `BrowseTab` -> `BrowseScreen` (Composable)
    - [x] `MoreTab` -> `MoreScreen` (Composable)
- [x] Complete `ScreenModel` to `ViewModel` conversion:
    - [x] Remove `cafe.adriel.voyager.core.model.StateScreenModel` from all classes.
    - [x] Replace `screenModelScope` with `viewModelScope`.
    - [x] Standardize on `StateFlow<ViewState>` and `onEvent(Intent)` pattern.
    - [x] Key targets: `DeepLinkScreenModel`, `MigrationListScreenModel`, `CoverSearchScreenModel`.

### Phase 5: Database & Persistence [COMPLETE]
- [x] Initialize Room (`EphyraDatabase.kt`).
- [x] Progressively migrate SQLDelight `.sq` definitions to Room:
    - [x] `mangas.sq` -> `MangaEntity` & `MangaDao`.
    - [x] `chapters.sq` -> `ChapterEntity` & `ChapterDao`.
    - [x] `history.sq` -> `HistoryEntity` & `HistoryDao`.
    - [x] `categories.sq` -> `CategoryEntity` & `CategoryDao`.
- [x] Replace `SQLDelight` drivers with Room in all Repositories.
- [x] Standardize on DataStore (Preferences) for all key-value configuration, removing legacy `SharedPreferences` wrappers and integrating explicit `SharedPreferencesMigration` rules for all core stores.

### Phase 6: UI & Feature Parity [COMPLETE]
- [x] **Settings**: Implement `PreferenceFragmentCompat` styled with Material 3.
- [x] **Library**: Implement robust grid/list views with two-handed gesture support.
- [x] **Reader**: Implement "Two-Handed" reading gesture (swipe bottom for next page).

### Phase 7: Remote Content Retrieval & Management [COMPLETE]
- [x] Add Remote Content Source Manager
- [x] Add Remote Tracking Service Manager
- [x] Add Remote Content Scanner
- [x] Add Remote Content Search
- [x] Add Local Content Manager
- [x] Add Local Content Scanner
- [x] Add Local Content Search
- [x] Add Merge Content
- [x] Add Content Details
- [x] Add Content opportunistic Merge
- [x] Add Content Sync Services (Remote and Local)
- [x] Add Content retrieval preference hierarchy
- [x] Add Update Checker
- [x] Add Downloader

### Phase 8: Clean Arch & Domain Separation [COMPLETE]
- [x] Identify core business logic in Repository and UseCase layers.
- [x] Abstract `ContentDatabase`, `RemoteSource`, `TrackingService` behind interfaces in the domain layer.
- [x] Enforce `sealed class Result` wrappers to handle remote/local data operations explicitly.

### Phase 9: Advanced Features & Polish
- [ ] Add Migration tools (Tachiyomi and Mihon).
- [ ] Implement **AniList Reading List Import** (parity with MAL import).
- [ ] Implement **Two-way Tracker Sync**: push library additions back to AniList/MAL.
- [ ] Implement **Collections**: custom, smart, and auto-generated groups (beyond categories).
- [ ] Implement **Personal Notes Sharing**: export manga cards with user-written notes.
- [ ] Improve performance and memory usage.
- [ ] Improve battery efficiency.
- [ ] Add support for more content types and tracking services.

### Phase 10: Improve app polish
- improve ui and user experience
- improve app onboarding
- improve app performance and memory usage
- improve app battery efficiency
- improve app stability
- improve app security
- improve app accessibility
- improve app test coverage

### Phase 11: Refine app architecture and organization [COMPLETE]
- [x] Deprecate and remove `CoreContainer` compat-shim once all consumers are Hilt-native.
- [x] Remove Voyager, Koin, and Injekt from `libs.versions.toml` and build scripts.
- [x] Remove SQLDelight from the build system once Room migration is 100% complete.
- [x] Ensure 100% compile time determinism via Hilt.
- [x] Ensure 100% Jetpack Navigation & Compose coverage.

### Phase 12: Release
- Release app to Play Store.

---

1. **Glance Widget Performance**: Move image loading out of the Glance lifecycle and implement a background worker to handle async pre-caching of bitmaps for `BaseUpdatesGridGlanceWidget`.





