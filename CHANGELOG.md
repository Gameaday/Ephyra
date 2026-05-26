# Changelog

All notable changes to Ephyra will be documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### 🏛️ Architecture & Clean Separation (Phase 8)
- **Domain Abstractions**: Introduced generic, media-agnostic domain interfaces `ContentDatabase`, `RemoteSource`, and `TrackingService` inside `:core:domain`, separating high-level business rules from concrete database/network frames.
- **Result Envelopes**: Wrapped database and remote crawling transactions in standard `Result<T>` sealed envelopes, guaranteeing explicit data mapping and strict exception boundaries.
- **DI Bindings**: Bound concrete data implementations (`ContentDatabaseImpl`, `ContentSourceOrchestrator`, `TrackingServiceImpl`) inside `AppModule` using standard `@Provides @Singleton` annotations.

### 🧹 Dependency & Legacy Purges (Phase 11)
- **SQLDelight Complete Purge**: Systematically removed SQLDelight drivers, configuration files, and references from all module build scripts and version catalogs, completing the transition of mangas, chapters, categories, and history to standard Room DAOs.
- **DI & Navigation Purges**: Eradicated all remnants of Voyager, Koin, and uy.kohesive.injekt libraries from all build configurations, achieving 100% compile-time determinism with Hilt and 100% Jetpack Navigation & Compose coverage.
- **CoreContainer Isolation**: Fully decoupled `CoreContainer` from all internal classes, confining it strictly as a bridge for external dynamic extensions in `:source-api`.

### 🛡️ Startup Resiliency & Safety (Phase 12)
- **Early Logging & Crash Trapping**: Overrode `attachBaseContext(base: Context)` inside `App.kt` to initialize `LogcatLogger` and `GlobalExceptionHandler` *before* the application executes Hilt dependency injection, content providers, or WorkManager initializers in `super.onCreate()`. Any early bootstrap crash is now gracefully captured.
- **Splash Screen Freeze Elimination**: Added a public `cancelAndRelease()` function to `Migrator.kt` that instantly completes the synchronization `initGate` on preference load failures, bypassing the 30-second splash screen freeze and permitting degraded boot.
- **Preference Stream Crash Guard**: Appended `.catch` flow operators to all reactive preference observation streams inside `App.kt` to ensure unhandled I/O failures never terminate the application scope.
- **Recovery Diagnostics Mismatch Solved**: Refactored `StartupFailureActivity.kt` to query `GlobalExceptionHandler.getThrowableFromIntent(intent)` directly, ensuring the exact Hilt/DI traceback is displayed on screen rather than a generic `"Unknown error"`.
- **Compose Migration Gating**: Gated `NavHost` rendering and update checker execution in `MainActivity.kt` behind database migration completion (`didMigration != null`), resolving a fundamental startup race condition where active ViewModels queried the database before Room migrations finished.

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
