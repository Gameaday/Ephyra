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

### Phase 1: De-KMP & Build System Simplification
- Remove `org.jetbrains.kotlin.multiplatform` from all build scripts.
- Convert `:i18n`, `:source-api`, `:source-local` to standard Android libraries.
- Collapse all `commonMain` and `androidMain` directories into `main`.

### Phase 2: Hilt Integration & DI Purge
- Setup Hilt/KSP globally.
- Purge `AppDependencyContainer`, `CoreContainer`, and any Koin remnants.
- Annotate `App` with `@HiltAndroidApp` and implement standard `@Module` and `@Inject` bindings.

### Phase 3: Localization Simplification
- Strip out `moko-resources`.
- Migrate all `commonMain/moko-resources` content to `src/main/res/values/strings.xml`.

### Phase 4: Navigation & State Modernization
- Replace Voyager `Screen` objects with Jetpack Navigation Compose graphs.
- Convert `ScreenModel` implementations to `ViewModel` with `@HiltViewModel`.

### Phase 5: Database & Persistence
- Standardize on Room (SQLite) for structured relational data.
- Standardize on DataStore (Preferences) for key-value configuration.
