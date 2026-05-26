# Ephyra Project Architecture Index (AS-IS State)

## 📜 Project Overview
Ephyra is a large-scale, modular Android application designed to provide a comprehensive, content viewing and library management experience. Its primary functions include:
1. **Library & Organization:** Categorizing, tracking, and managing content sources and user libraries.
2. **Content Viewing:** Core functionality for reading serialized content (manga, webtoons, and comics) in diverse layouts.
3. **Synchronization & Metadata:** Seamless bidirectional sync with self-hosted media servers (Jellyfin) and tracking services (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi).

The application is highly modularized into distinct, single-responsibility modules to isolate business logic, presentation details, and data-layer services.

## 🧩 Module Breakdown

| Module Path | Primary Responsibility | Key Components |
| :--- | :--- | :--- |
| `:app` | Application Entry Point & UI Composition | `MainActivity`, `App`, `AppWorkerFactory` |
| `:core` | Core library structures and domain helpers | `ephyra.core.download`, `ephyra.core.archive` |
| `:core-metadata` | Rich metadata handling & tracking interfaces | `ephyra.core.metadata` |
| `:data` | Data persistence implementation, mapping, and APIs | `ephyra.data.database` (Room DAOs, mappers) |
| `:domain` | Pure Kotlin business logic interactors (use-cases) | `ephyra.domain.manga`, `ephyra.domain.chapter` |
| `:feature:browse` | Content source browsing and catalog extensions | `BrowseScreen`, `BrowseSourceScreenModel` |
| `:feature:category` | Library category configuration & sorting | `CategoryScreen`, `CategoryScreenModel` |
| `:feature:download` | Download queue management & background down-loader | `DownloadQueueScreen`, `DownloadQueueScreenModel` |
| `:feature:history` | Content reading history and progress charts | `HistoryScreen`, `HistoryScreenModel` |
| `:feature:library` | Main library grid/list screen & custom filters | `LibraryScreen`, `LibraryScreenModel` |
| `:feature:manga` | Series details, chapter list, and cover configuration | `MangaScreen`, `MangaScreenModel` |
| `:feature:migration` | Series migration and deduplication flow | `MigrationListScreen`, `MigrationListScreenModel` |
| `:feature:more` | Onboarding, settings menu, and about features | `MoreScreen`, `MoreScreenModel` |
| `:feature:player` | Dedicated viewer for continuous formats | `PlayerActivity` |
| `:feature:reader` | Multi-mode image-viewer and webtoon reader | `ReaderActivity`, `ReaderViewModel` |
| `:feature:security` | App-lock and biometric access guards | `SecuritySettingsScreen` |
| `:feature:settings` | Modular application preferences | `SettingsMainScreen` |
| `:feature:stats` | Statistics and charts reflecting reading habits | `StatsScreen` |
| `:feature:upcoming` | Scheduling and upcoming chapter calendars | `UpcomingScreen` |
| `:feature:updates` | Unread chapter feed and recent additions | `UpdatesScreen`, `UpdatesScreenModel` |
| `:feature:webview` | In-app browser for Cloudflare bypass & authentication | `WebViewActivity` |
| `:presentation-core`| Common design tokens, styling resources, and UI utils | `Theme.kt`, `PreferenceMutableState` |
| `:presentation-widget`| Android Glance Home screen widgets | `BaseUpdatesGridGlanceWidget` |
| `:source-api` | Stable public interface for external extensions | `eu.kanade.tachiyomi.source` |
| `:source-local` | Local storage-based content resolution | `LocalSource` |
| `:telemetry` | Loggers, diagnostics, and crash-reporting services | `StartupGuard`, `GlobalExceptionHandler` |

## 💾 Data Persistence Layer (CRITICAL)

The data model is fully modernized, relying on Jetpack-native persistent storage frameworks.

1. **Room Database (`EphyraDatabase`):**
   - Room is the authoritative database engine, utilizing native `Flow` and `Paging 3` integration.
   - Primary entities like `MangaEntity`, `ChapterEntity`, `HistoryEntity`, and `CategoryEntity` represent local relational records.
   - SQLDelight is permanently retired from core application logic.
2. **Jetpack DataStore (`DataStorePreferenceStore`):**
   - Jetpack DataStore manages key-value configuration settings asynchronously.
   - Relies strictly on non-blocking thread operations, utilizing `.getSync()` cached memory lookups during composition and `Flow` observation for updates.
   - Legacy `SharedPreferences` wrappers have been expunged with custom `SharedPreferencesMigration` routines providing seamless upgrade transitions.

## 🚀 Major Code Flows

### 1. App Startup Resiliency
- **Sequence:** `App.onCreate()` $\rightarrow$ `StartupGuard` watchdog starts $\rightarrow$ Try/catch wrapped asynchronous startup phases (Logging, DI, WorkManager, Migrations) execute $\rightarrow$ `MainActivity` launched.
- **Analysis:** Phased diagnostics prevent application hangs. If non-essential systems (e.g. notifications or telemetry) fail, `StartupGuard` allows the app to start in a degraded mode instead of crashing or locking up the UI thread.

### 2. Composition State & UDF Loop
- **Sequence:** Compose UI $\rightarrow$ Emits discrete `Event` intents $\rightarrow$ `ViewModel` (hiltViewModel) $\rightarrow$ Runs domain interactors on `Dispatchers.IO` $\rightarrow$ Emits immutable `ViewState` state-flow $\rightarrow$ Re-composes UI screen.
- **Analysis:** Unidirectional Data Flow is strictly enforced. ViewModels contain zero reference to Android framework contexts and communicate side-effects asynchronously via a channel-backed `Effect` stream.

### 3. Asynchronous Media Loading
- **Sequence:** `ReaderActivity` $\rightarrow$ `ReaderViewModel` $\rightarrow$ Interactor retrieves page metadata $\rightarrow$ Preloading windows scale dynamically based on device RAM constraints $\rightarrow$ `Coil` fetches and decodes images asynchronously without blocking UI interactions.
