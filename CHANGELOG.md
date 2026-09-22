# Changelog

All notable changes to Ephyra will be documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### 📖 Reader Navigation, State Machines & Chapter Lifecycle (Phase 1)
- **Direction-Aware Chapter Completion**: Introduced `NavigationVector { FORWARD, BACKWARD }` and guarded read-completion dispatch through it. Swiping backward from page `0` of chapter *N* into chapter *N-1* no longer marks chapter *N* as read; completion requires the final page index **and** a `FORWARD` dispatch (webtoon coverage crossing ≥95% forward). Backward boundary crossings transition chapters without mutating read/progress state.
- **Semantic "Mark Previous as Read"**: `MangaViewModel.markPreviousChapterRead` now compares against `Chapter.sourceOrder` (reading order) instead of raw display indices, so reverse-sorted or filtered chapter lists update exactly the chapters that precede the selection — never the ones merely rendered above it.
- **Transition State Machine & "Caught Up" Terminal State**: Added the sealed `TransitionState { ToPrevious, ToNext, EndOfSeries }` hierarchy with `ChapterTransition.toTransitionState()`. Forward navigation past the final available chapter now renders a terminal summary card with a high-prominence **"Return to Series"** button wired to `onNavigateUp` in both the pager and webtoon readers.
- **Directional Transition Arrows**: Added `TransitionDirection { LTR, RTL, VERTICAL }`, resolved from the active viewer (`R2LPagerViewer`/`VerticalPagerViewer`), so previous/next transition arrows point along the reader's own reading flow instead of a hard-coded horizontal direction.

### 🐛 Critical Fixes
- **Extension zstd Crash (`Lokhttp3/zstd/Zstd;`)**: Bundled `com.squareup.okhttp3:okhttp-zstd` (5.5.0) via `core:common`. Dynamically loaded extension APKs (MangaDex, Mangabat, and most modern Mihon/Keiyoushi extensions) reference the `okhttp3.zstd` package, which OkHttp ships in a separate optional module. Without it, every source network call failed with `NoClassDefFoundError` (wrapped by `ExtensionCallBoundary` as "Source 'X' encountered an error"), breaking chapter loading, search results, covers, and making library buttons appear unresponsive on source-driven screens.
- **Silent Chapter-Sync Failures**: `MangaChapterInteractor.syncChaptersWithSource` no longer swallows source fetch failures. Failed manga-details/chapter-list fetches are logged, manual refreshes now surface a toast via `MangaScreenEffect.ShowToast`, and background refreshes keep the local chapter list instead of wiping it.
- **Markdown Renderer Build Break**: Implemented the new `alert: MarkdownAlertPadding` property required by `multiplatform-markdown-renderer` 0.45.0's `MarkdownPadding` interface in `MarkdownRender.kt` (fixes `:feature:manga:compileDebugKotlin`).
- **Instant Launch Crash / Silent Exit**: The Shizuku purge dropped `dev.rikka.shizuku:provider` from the build but left its `<provider android:name="rikka.shizuku.ShizukuProvider">` declaration behind in `app/src/main/AndroidManifest.xml`. Android instantiates manifest-declared providers in `ActivityThread.handleBindApplication` *before* `Application.onCreate()`, so every launch died with `ClassNotFoundException: rikka.shizuku.ShizukuProvider` before `GlobalExceptionHandler` or `StartupFailureActivity` could run — the process closed instantly with no crash dialog and no error screen. Removed the stale provider entry and added `ManifestComponentClassTest`, which fails the build whenever a manifest-declared Android component class is missing from the app classpath.

### 🔌 Dynamic Sourcing & Legacy Extension Purge
- **UI Scraper Management**: Reworked the "Extensions" tab in the Browse screen to act as a **Source Management** tab. It lists JS scrapers, heuristic profiles, and linked custom sources, supporting download from GitHub, script import, heuristic configuration, and custom source linking. Removed all legacy APK installer, Shizuku, and untrusted extension elements.
- **Legacy Extension JS Converter**: Built a CLI tool `scripts/convert_legacy_extension.js` that compiles/transpiles legacy Tachiyomi/Mihon Kotlin extensions into sandboxed JS scrapers. Bundles a custom JavaScript HTML/DOM parser `MiniDOM` so that standard Jsoup selectors run natively inside the sandboxed QuickJS execution context.
- **Update Check Loop Resolved**: Updated `AppUpdateChecker.kt` to dynamically resolve the app version from `PackageManager` instead of using the hardcoded `:core:data` BuildConfig value.
- **JS Payload Security**: Added string escaping for payloads in `ScriptableSourceEngine.kt` to prevent command injection and parser crashes.

### 🔌 Complete Dynamic Sourcing & Scraping System
- **Layout Heuristics Engine**: Implemented DOM structure auto-discovery in `AdaptiveHeuristicEngine.kt` using Jsoup. Automatically resolves and fallbacks to standard selectors for titles, covers, chapter links (e.g., `.wp-manga-chapter`, `a[href*=chapter]`), and reader pages.
- **Sandboxed QuickJS Scripting**: Leveraged a sandboxed QuickJS execution runtime in `ScriptableContentSourceEngine.kt` to run dynamic JS scraper scripts securely and compliant with Play Store dynamic code guidelines.
- **Dynamic Source Registry Bridge**: Created `DynamicHttpSource.kt` to bridge generic `SourceProfile` and scraper orchestration outputs into Tachiyomi's legacy `HttpSource` / `CatalogueSource` contracts.
- **Dynamic Sources Registration**: Configured `AndroidSourceManager.kt` to listen to the preference flow for profiled domains, dynamically instantiate `DynamicHttpSource` wrappers, and register them concurrently alongside legacy extension sources.
- **Hilt Dependency Cycle Resolution**: Fixed the circular dependency in the DI graph between `AndroidSourceManager` and `DownloadManager` / `Downloader` by injecting a deferred Hilt `Provider<ContentSourceOrchestrator>` inside `AndroidSourceManager`.
- **ArchUnit Feature Isolation**: Resolved all feature module package/dependency boundary violations by moving shared components (`MangaCover`, `LibraryBottomActionMenu`, `CategoryExtensions`, `ChangeCategoryDialog`) to the common `:presentation-core` module, and introducing compositional navigation interfaces via `LocalAppNavigator`.
- **Injekt Confined To The Extension-Bridge ABI (Here's What Actually Happened)**: The prior changelog claimed `uy.kohesive.injekt` was "cleanly removed" and "eradicated"; that was false. What really happened — `core:common`'s internal `uy.kohesive.injekt` shim package is gone, every app/feature DI lookup now uses standard Hilt constructor injection, *and* a single Injekt container still runs on purpose inside the dynamic `eu.kanade.tachiyomi` extension bridge (`:core:common` / `:source-api`), because legacy extensions can't be wired through Hilt. Injekt is alive, but confined to exactly one place: the extension ABI.

### 🏛️ Architecture & Clean Separation (Phase 8)
- **Domain Abstractions**: Introduced generic, media-agnostic domain interfaces `ContentDatabase`, `RemoteSource`, and `TrackingService` inside `:core:domain`, separating high-level business rules from concrete database/network frames.
- **Result Envelopes**: Wrapped database and remote crawling transactions in standard `Result<T>` sealed envelopes, guaranteeing explicit data mapping and strict exception boundaries.
- **DI Bindings**: Bound concrete data implementations (`ContentDatabaseImpl`, `ContentSourceOrchestrator`, `TrackingServiceImpl`) inside `AppModule` using standard `@Provides @Singleton` annotations.

### 🧹 Dependency & Legacy Purges (Phase 11)
- **SQLDelight Complete Purge**: Systematically removed SQLDelight drivers, configuration files, and references from all module build scripts and version catalogs, completing the transition of mangas, chapters, categories, and history to standard Room DAOs.
- **DI & Navigation Purges**: Eradicated all remnants of Voyager and Koin from all build configurations. (Narratively adjacent to "Injekt purged" above, but Injekt was *not* eradicated — it remains in `:core:common` / `:source-api` as a compatibility shim for legacy `eu.kanade.tachiyomi` extensions; app code is 100% Hilt, 100% Jetpack Navigation & Compose coverage.)
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
