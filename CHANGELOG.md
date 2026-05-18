# Changelog

All notable changes to Ephyra will be documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased] — 2026-05-18

### 🏗️ Architecture

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
