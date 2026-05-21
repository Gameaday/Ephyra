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

### 2. The Migration to Room: A New Engine

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

### 3. Dependency Injection: Compile-Time Determinism

The codebase has transitioned to **Hilt (Dagger)** for true Dependency Injection.

- **Refactoring by Intent**: Architecture now enforces **Constructor Injection** and static graph validation. Service locators (Injekt) and runtime reflective registries (Koin) are strictly prohibited for internal code.
- **Hilt-Backed Bridge**: A temporary `CoreContainer` shim provides dependencies to legacy components (like Voyager screens) during the incremental migration, but all new features must be 100% Hilt-native.
- **The Power of Compile-Time Safety**: By utilizing Dagger's static analysis, we ensure that missing dependencies are caught at build time, preventing runtime crashes and making the dependency graph transparent.

### 4. Unidirectional Data Flow (UDF)

To eliminate "Main Thread Jank" and race conditions, the UI paradigm follows strict **Unidirectional Data Flow**.

- **The Decision**: Every `ViewModel` is mandated to emit a single, immutable `ViewState` object (typically via `StateFlow`) and receive discrete `Intent` or `Event` models.
- **The Rationale**: Standardizing state management ensures UI recompositions are predictable and performant. It prevents bugs where multiple independent data streams fall out of sync. This pattern is enforced in our Hilt-annotated ViewModels.

### 5. Asynchronous Data Persistence

The transition from `AndroidPreferenceStore` (legacy SharedPreferences) to
`DataStorePreferenceStore` is a critical "Healing" operation.

- **The Rationale**: Legacy storage was synchronous and frequently blocked the Main UI thread. The
  new architecture leverages **AndroidX DataStore**, which is fundamentally asynchronous and `Flow`
  -based.
- **The Principle**: No disk I/O should ever occur on the Main thread. All persistence logic is
  shifted to `Dispatchers.IO`, ensuring the UI remains fluid at 120 FPS.

### 6. Host-Extension API Preservation

As a host environment for dynamic, APK-based plugins, the re-architecture respects the **Public API
Surface**.

- **The Decision**: While internal modules move to the `ephyra.*` namespace, the `source-api` module
  strictly preserves the `eu.kanade.tachiyomi.source` namespace.
- **Legacy Shims**: Certain legacy compatibility shims (such as `uy.kohesive.injekt.Injekt`) must be
  preserved exclusively to provide a stable API for legacy extensions. Internal codebase files must
  not use these shims, but they must exist for external plugins.
- **The Rationale**: This creates a "Bridge" allowing the app to be modernized internally while
  maintaining 100% compatibility with thousands of external extensions.

### 7. R8/Proguard as Security & API Boundary

In this re-architecture, the Proguard file is treated as a **Contract** rather than just a shrinking
tool.

- **The Principle**: Surgical retention rules protect shared libraries (like `okhttp3` and `jsoup`)
  that extensions depend on. This prevents "Transitive DLL Bloat" and ensures that minification does
  not accidentally strip the "Public API" used by dynamic plugins.

---

## Architecture Summary

| Aspect               | Choice                                                                  |
|----------------------|-------------------------------------------------------------------------|
| Identity             | Canonical ID from tracker (`al:21`, `mal:30013`, `mu:12345`)            |
| Dependency Injection | Hilt (Dagger) - Compile-time static graph                               |
| Database Engine      | Room (Flow-based Observability)                                         |
| Navigation           | Jetpack Navigation Compose (replacing Voyager)                          |
| State Management     | Unidirectional Data Flow (ViewModel + ViewState/Event)                  |
| Persistence          | AndroidX DataStore (Asynchronous)                                       |
| Business Logic       | Domain Interactors (Use-Cases)                                          |
| Extension API        | Legacy Compatibility Bridge (`eu.kanade.*` + Injekt shim)               |
