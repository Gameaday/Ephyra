# Ephyra Architecture

The re-architecture of Ephyra represents a shift from a decade of technical debt—characterized by
tightly coupled "God Objects" and synchronous data access—to a modern, enterprise-grade mobile
system. This evolution is guided by a **"Heal to Enable Selection"** philosophy, ensuring that core
components can be selectively replaced in the future without destabilizing the entire platform.

## Architectural Principles

### 1. The Interactor (Use-Case) Mandate

The most significant shift in business logic is the migration to **Domain Interactors**.

- **The Decision**: Moving logic out of the `ScreenModel` and `Repository` into single-purpose
  classes (e.g., `GetManga`, `SetReadStatus`).
- **The Rationale**: This detangles the Presentation layer from the Data layer. The UI no longer
  knows how data is stored; it only knows what action it wants to perform. This isolation ensures
  that if the database engine changes (e.g., from SQLDelight to Room), the UI remains untouched.
- **Enforcement**: Domain Interactors are the **sole bridge** between ViewModel and data-layer
  services. Direct injection of `DownloadManager`, `SourceManager`, `TrackerManager`, or `CoverCache`
  into ViewModels is prohibited. Use a domain use-case wrapper instead.

### 2. Domain Layer Purity (Zero Android Framework)

The `core/domain` module **must not** contain any Android framework dependencies. This is enforced
via `build.gradle.kts`:

- ✅ Allowed: `kotlinx.coroutines`, `kotlinx.serialization`, `kotlinx.immutables`, `paging-common` (pure Kotlin)
- ❌ Prohibited: `androidx.workmanager`, `androidx.compose.runtime.Immutable`, `androidx.paging.runtime`

Domain models use **Kotlin data classes** without `@Immutable` (Compose annotation). The `@Immutable`
annotation is applied at the UI layer via mappers.

### 3. The Migration to Room: A New Engine

A cornerstone of the future roadmap is the complete replacement of SQLDelight with **Room**.

- **The Decision**: Transitioning from SQL-first (SQLDelight) to an Entity-DAO paradigm (Room).
- **The Rationale**: While SQLDelight provided the foundation, Room is the industry standard for
  professional Android development. The transition provides:
  - **Superior Observability**: Native integration with `Flow` and `Paging 3` reducing boilerplate
    to keep the UI in sync.
  - **Modern Tooling**: Deep integration with the Android Studio Database Inspector for
    professional-level debugging and performance profiling.
  - **Automated Migrations**: Room simplifies handling a decade-old, complex schema via automated
    migration paths and compile-time SQL verification.
  - **Reduced Manual Friction**: Shifts the burden of writing manual SQL for basic operations back
    to the compiler.

### 4. Dependency Injection: Compile-Time Determinism

The codebase has transitioned to **Hilt (Dagger)** for true Dependency Injection.

- **Refactoring by Intent**: Architecture now enforces **Constructor Injection** and static graph validation. Service locators (Injekt) and runtime reflective registries (Koin) are strictly prohibited for internal code.
- **Hilt-Backed Bridge**: A temporary `CoreContainer` shim provides dependencies to legacy components (like Voyager screens) during the incremental migration, but all new features must be 100% Hilt-native.
- **The Power of Compile-Time Safety**: By utilizing Dagger's static analysis, we ensure that missing dependencies are caught at build time, preventing runtime crashes and making the dependency graph transparent.

### 5. Unidirectional Data Flow (UDF) — Strict

To eliminate "Main Thread Jank" and race conditions, the UI paradigm follows strict **Unidirectional Data Flow**.

- **The Decision**: Every `ViewModel` is mandated to emit a single, immutable `ViewState` object (typically via `StateFlow`) and receive discrete `Event` models. One-time side-effects (navigation, toasts) flow out through a separate `Effect` stream (Channel-based).
- **The Rationale**: Standardizing state management ensures UI recompositions are predictable and performant. It prevents bugs where multiple independent data streams fall out of sync.
- **Enforcement**:
  - ✅ Use `BaseUdfViewModel<State, Event, Effect>` as the base class.
  - ✅ UI emits Events via `screenModel.onEvent(...)`.
  - ✅ ViewModel handles Events internally; all public API is through Events + StateFlow + Effect Channel.
  - ❌ **No public ViewModel properties** that expose domain services (like `libraryUpdateScheduler`).
  - ❌ **No `runBlocking`** in ViewModels — use `viewModelScope.launchIO` for async init.
  - ❌ **No direct method calls** from UI to ViewModel bypassing the event channel.

### 6. Asynchronous Data Persistence

The transition from `AndroidPreferenceStore` (legacy SharedPreferences) to
`DataStorePreferenceStore` is a critical "Healing" operation.

- **The Rationale**: Legacy storage was synchronous and frequently blocked the Main UI thread. The
  new architecture leverages **AndroidX DataStore**, which is fundamentally asynchronous and `Flow`
  -based.
- **The Principle**: No disk I/O should ever occur on the Main thread. All persistence logic is
  shifted to `Dispatchers.IO`, ensuring the UI remains fluid at 120 FPS.
- **Synchronous Access**: Where unavoidable (sort comparators), use `Preference.getSync()` which
  returns the in-memory cached value without blocking on disk I/O.

### 7. Host-Extension API Preservation

As a host environment for dynamic, APK-based plugins, the re-architecture respects the **Public API
Surface**.

- **The Decision**: While internal modules move to the `ephyra.*` namespace, the `source-api` module
  strictly preserves the `eu.kanade.tachiyomi.source` namespace.
- **Legacy Shims**: Certain legacy compatibility shims (such as `uy.kohesive.injekt.Injekt`) must be
  preserved exclusively to provide a stable API for legacy extensions. Internal codebase files must
  not use these shims, but they must exist for external plugins.
- **The Rationale**: This creates a "Bridge" allowing the app to be modernized internally while
  maintaining 100% compatibility with thousands of external extensions.

### 8. R8/Proguard as Security & API Boundary

In this re-architecture, the Proguard file is treated as a **Contract** rather than just a shrinking
tool.

- **The Principle**: Surgical retention rules protect shared libraries (like `okhttp3` and `jsoup`)
  that extensions depend on. This prevents "Transitive DLL Bloat" and ensures that minification does
  not accidentally strip the "Public API" used by dynamic plugins.

---

## Content-Agnostic Core Architecture

### ContentItem — The Universal Domain Model

All cross-cutting features (Library, History, Downloads) now operate on `ContentItem`,
a generic model that accepts ANY content type:

```kotlin
data class ContentItem(
    val id: Long, val sourceId: Long, val url: String,
    val title: String, val author: String?, val artist: String?,
    val description: String?, val genres: List<String>,
    val status: ContentStatus, val thumbnailUrl: String?,
    val contentType: ContentType,       // MANGA, NOVEL, VIDEO, AUDIO, BOOK
    val metadata: Map<String, String>,  // Type-specific: "chapter_count", "duration"
    val favorite: Boolean, val dateAdded: Long, val lastUpdate: Long,
)
```

The existing `Manga` type becomes a specialization; new content types target `ContentItem` directly.

### Source Engine Abstraction — Swappable Resolution

Content sourcing follows a "try known → fall back to heuristic → fail" pipeline:

```
ContentSourceOrchestrator
  ├── knownEngines: Map<String, ContentSourceEngine>  // Hardcoded adapters (fast path)
  └── heuristicEngine: ContentSourceEngine              // Auto-discovery (fallback)
```

- `ContentSourceEngine` — sealed interface with `discover()`, `search()`, `getItem()`, `getPopular()`, `getLatest()`
- `SourceProfile` — describes a source's API endpoints, selectors, JSON paths, pagination, auth, etc.
- `SourceProfileCache` — persists discovered profiles in DataStore to avoid re-discovery
- `ContentSourceOrchestrator` — single entry point; callers never touch engines directly

This design supports any future engine (heuristic, WASM/JS sandbox, AI-grokked) without changing core code.

### Reader Plugin Architecture

Each content type registers a reader:

| ContentType | Reader Implementation |
|-------------|----------------------|
| MANGA | Existing paged/webtoon reader |
| NOVEL | Text-based reader |
| VIDEO | ExoPlayer-based player |
| AUDIO | Media3 audio player |
| BOOK | Fixed-layout renderer |

## Architecture Summary

| Aspect               | Choice                                                                  |
|----------------------|-------------------------------------------------------------------------|
| Identity             | Canonical ID from tracker (`al:21`, `mal:30013`, `mu:12345`)            |
| Dependency Injection | Hilt (Dagger) - Compile-time static graph                               |
| Database Engine      | Room (Flow-based Observability)                                         |
| Navigation           | Jetpack Navigation Compose (replacing Voyager)                          |
| State Management     | Unidirectional Data Flow (ViewModel + ViewState/Event/Effect)           |
| Persistence          | AndroidX DataStore (Asynchronous)                                       |
| Business Logic       | Domain Interactors (Use-Cases)                                          |
| Extension API        | Legacy Compatibility Bridge (`eu.kanade.*` + Injekt shim)               |

## Layer Enforcement Rules

| Dependency Flow         | Permitted? | Notes                                         |
|------------------------|------------|-----------------------------------------------|
| UI → Domain            | ✅ Yes     | ViewModels inject Use Cases/Domain interfaces |
| Domain → Data          | ✅ Yes     | Via interfaces defined in Domain              |
| Data → Data            | ✅ Yes     | Room entities, DAOs, network sources          |
| UI → Data              | ❌ No      | Must go through Domain Use Cases              |
| Domain → Android SDK   | ❌ No      | Pure Kotlin enforced in build.gradle.kts      |
| ViewModel → Data Layer | ❌ No      | Must wrap in Domain Use Cases                 |

## Startup Resilience & Fault Tolerance

### StartupGuard (New)
`StartupGuard` is a phased startup watchdog that prevents the app from hanging or ANRing if a
non-critical dependency fails to initialize:

- **7 startup phases** are tracked independently: `logging`, `crash_handler`, `di_container`,
  `telemetry`, `notifications`, `reactive_bindings`, `async_init`
- Each phase has a **10-second timeout** via `CountDownLatch`
- Any phase can fail without blocking the others — the app starts with degraded functionality
- ViewModels and features can call `StartupGuard.awaitPhase("di_container")` before accessing DI

### App.onCreate() Resiliency (Updated)
Every critical initialization block in `App.onCreate()` is wrapped in `try/catch`:

| Initialization | Failure Behavior |
|---------------|------------------|
| Logcat logger | Falls back to `android.util.Log` |
| Global crash handler | Startup continues without crash recovery |
| DI CoreContainer | App runs with degraded features |
| Telemetry/Crashlytics | Analytics silently disabled |
| WebView setup | Non-fatal warning logged |
| Notification channels | Silent failure |
| Preference reactive bindings | No reactive updates until next app restart |
| Theme/Migration/Widget | Async failure — app starts with defaults |

### GlobalExceptionHandler (Enhanced)
- Detects whether DI (`CoreContainer`) is ready to decide between `CrashActivity` (Hilt-aware)
  and `StartupFailureActivity` (zero dependencies)
- Double-crash guard via `isCrashing` volatile flag
- Falls back to the default handler if crash activity itself crashes

## Known Violations (Audit Completed)

| Violation | Severity | Status | Description |
|-----------|----------|--------|-------------|
| Framework deps in domain | P1 | ✅ **Fixed** | Removed WorkManager, Compose annotations from `core/domain/build.gradle.kts` |
| `runBlocking` in ViewModels | P0 | ✅ **Fixed** | 4 instances eliminated across `LibraryScreenModel` and `BrowseSourceScreenModel` |
| Public ViewModel service leaks | P1 | ✅ **Fixed** | `libraryUpdateScheduler` made private; 3 new UDF Events added |
| SManga DTO leaking into domain/UI | P1 | ✅ **Fixed** | Created `MangaStatus` sealed interface; replaced all `SManga.COMPLETED` references |
| Error handling standardization | P2 | ✅ **Fixed** | Created `DomainResult<T>` sealed class with `Success`, `Error`, and `runCatching{}` factory |
| Data-layer services in ViewModel | P1 | 🔶 **Partial** | Created `GetDownloadCount` and `DownloadChapters` domain use-cases; `SourceManager`/`TrackerManager`/`CoverCache` still direct |
| `core/common` Android contamination | P1 | ✅ **Fixed** | Removed AndroidX `parseAsHtml` from `StringExtensions.kt` (replaced with pure-Kotlin entity decoder); removed `Context` from `LocaleHelper.kt` (pure Kotlin `java.util.Locale` only); removed Android imports from `DateExtensions.kt` (pure Kotlin functions split from Context-dependent overload) |
