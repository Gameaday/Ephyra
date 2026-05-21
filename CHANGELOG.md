# Changelog

All notable changes to Ephyra will be documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### 🏗️ Architecture

- **Transition to Hilt**: Fully migrated from Koin to Hilt for Dependency Injection across all modules.
- **Voyager Purged**: Removed Voyager navigation library in favor of official Jetpack Navigation Compose.
- **Composable Screens**: All navigation-level screens are now standard @Composable functions, improving testability and standard Android tool support.
- **Hilt ViewModels**: Standardized on `@HiltViewModel` for state management, eliminating all remaining Voyager `ScreenModel` dependencies.
- **CoreContainer Modernization**: Refactored `CoreContainer` as a Hilt-backed service locator to bridge legacy extension points while maintaining compile-time safety for the internal app.

### 🔧 Build & Performance

- **Non-Transitive R Classes**: Enabled `android.nonTransitiveRClass=true` globally to reduce build times and APK size.
- **Gradle 10 Readiness**: Enabled Configuration Cache and Configure-on-demand for significantly faster incremental builds.
- **Resource Optimization**: Consolidated redundant resource folders (v27, v31, v33) into default directories, leveraging `minSdk 34`.
- **Lint Hardening**: Cleared hundreds of lint warnings, including MissingPermission, PluralsCandidate, and ObsoleteSdkInt.
- **Coroutines Cleanup**: Purged delicate top-level `launchIO`/`launchUI` globals in favor of structured concurrency and explicit scopes.

### 🛡️ Security & Reliability

- **Intent Sanitization**: Implemented `IntentSanitizer` for deep-link handling to prevent unsafe intent launch vulnerabilities.
- **Safe Notifications**: Added runtime permission checks for `POST_NOTIFICATIONS` in all app-facing notifiers.
- **UDF Compliance**: Verified all major features follow strict Unidirectional Data Flow patterns.

---

## [0.20.0] — 2026-05-18

- **Compile-Time Deterministic DI**: Removed Koin and Koin Annotations from the dependency
  graph. All singletons are now wired explicitly via `AppDependencyContainer` and `CoreContainer`,
  eliminating any reflective or annotation-processed runtime resolution.
- **`VoyagerKoinShim`**: Added a thin compatibility shim so existing Voyager screen-model call
  sites compile without changes while the underlying DI is now pure Kotlin.
- **`AppWorkerFactory`**: Extracted worker creation into a dedicated factory, decoupling
  `WorkManager` initialization from the application-level DI graph.

### 📚 Content-Agnostic Library UI

- **`ILibraryItem` interface**: Introduced a generic contract (`id`, `title`, `coverUrl`,
  `unreadCount`, `downloadCount`, …) that decouples the library presentation layer from the
  concrete `LibraryManga` / `Manga` domain types.
- **`LibraryItem` implements `ILibraryItem`**: The existing library item is now an adapter that
  wraps `LibraryManga` and satisfies the new interface, preserving all existing behavior.
- **Library grids & lists consume `ILibraryItem`**: `LibraryCompactGrid`, `LibraryComfortableGrid`,
  `LibraryList`, `LibraryContent`, and `LibraryPager` now operate on the generic interface,
  making it straightforward to surface future media types (anime, novels, etc.).
- **`LibraryScreenModel` selection callbacks**: `toggleSelection` and `toggleRangeSelection`
  now accept `ILibraryItem` instead of `LibraryManga`.

### 🔧 Build Configuration

- **AGP 9.0 compliance (`android.builtInKotlin=true`)**: Enabled the AGP 9.0 built-in Kotlin
  integration globally. Removed deprecated `kotlin.incremental.useClasspathSnapshot` and
  `kotlin.compiler.preciseCompilationResultsBackup` properties.
- **`MangaScreenModelFactory`**: Moved screen-model construction for the manga feature into an
  explicit compile-time factory, removing the last Koin `ScreenModel` injection from
  `:feature:manga`.
- **Missing feature build files**: Created `build.gradle.kts` stubs for previously unconfigured
  modules (`feature/category`, `feature/download`, `feature/history`, `feature/more`,
  `feature/security`, `feature/stats`, `feature/updates`, `feature/webview`), resolving
  "no variants exist" Gradle resolution errors.
- **Compose BOM in `:domain`**: Added `compileOnly(platform(compose.compose.bom))` so that
  `compose.runtime.annotation` resolves correctly without pulling runtime Compose into the
  domain layer.

### 🗺️ Documentation

- **`ROADMAP.md`**: Updated to reflect completed Phase 1 (Pure DI), in-progress Phase 3
  (Content-Agnostic UI) and Phase 4 (Build Config), and planned Phase 2 (Sandboxed Scripting).
- **`CHANGELOG.md`**: This file — initialized.
