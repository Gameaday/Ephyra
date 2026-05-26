# Ephyra Project Architecture Plan (TO-BE State)

## ✨ Guiding Principles

The refactored architecture adheres strictly to modern "Android First Principles":
1. **Unidirectional Data Flow (UDF):** The UI observes a single immutable `ViewState` flow. User interactions are dispatched as events: **Event $\rightarrow$ State $\rightarrow$ View**.
2. **Compile-Time Safety & Static Verification:** Dependency Injection graphs are verified completely at build time using Dagger Hilt.
3. **Domain Layer Purity:** Business rules reside in the pure Kotlin `:domain` and `:core` modules, completely isolated from Android framework dependencies (`android.*`).
4. **Jetpack Compose Native Layouts:** A fully declarative, responsive UI designed around structured design systems, eliminating legacy XML layouts.

## 💻 Technology Stack

| Component | Technology | Rationale | Status |
| :--- | :--- | :--- | :--- |
| **UI & Layouts** | Jetpack Compose | Modern, declarative, reactive layout engine. | **100% Implemented** |
| **Dependency Injection** | Hilt (Dagger) | Compile-time deterministic wiring; static validation. | **100% Implemented** |
| **Navigation** | Jetpack Navigation | Official Compose-first navigation graph. | **100% Implemented** |
| **State Holders** | Android Architecture ViewModels | Hilt-lifecycle-integrated `@HiltViewModel`s. | **100% Implemented** |
| **Data Persistence** | Room Database | Asynchronous object relational mapper with reactive Flow. | **In Progress (Build 3)** |
| **Settings Storage** | Jetpack DataStore | Asynchronous, flow-driven key-value storage. | **100% Implemented** |
| **Diagnostics** | StartupGuard | Diagnostic watchdog tracking app initialization. | **100% Implemented** |

## 🔄 Architectural Flow Diagram

The mandated clean-architecture layered data flow:

```mermaid
graph TD
    A[UI Composable Screen] -->|1. Dispatch Event| B(Hilt ViewModel);
    B -->|2. Invoke Interactor| C[Domain Interactor (Use-Case)];
    C -->|3. Access Data| D[Repository Interface (Domain)];
    E[Repository Implementation (Data)] -.->|Implements| D;
    E -->|4. Query Database/Network| F[Room DAO / Network Service];
    F -->|5. Return Entity/DTO| E;
    E -->|6. Map to Domain Model| B;
    B -->|7. Emit ViewState Flow| A;
```

**Architectural Boundaries:**
- **Presentation Layer (`feature/*`, `presentation-core`):** Pure UI components and ViewModels. ViewModels inject single-purpose use-cases (Interactors). Direct database or raw settings access is strictly prohibited.
- **Domain Layer (`domain`, `core`):** Contains business logic (e.g. `GetManga`, `UpdateReadStatus`). Defines repository interfaces. Zero reference to `android.*` classes.
- **Data Layer (`data`):** Implements repository contracts. Coordinates queries via Room DAOs or external HTTP interceptors. Maps backend schemas into clean domain objects.

## 🛣️ Modernization Roadmap

The migration plan is structured in sequential builds:

### 🟩 Build 1: Foundation, Styling & DataStore [COMPLETE]
- Strip out `moko-resources` and KMP dependencies; establish native `:presentation-core` design token.
- Convert all core SharedPreferences instances to Jetpack DataStore with custom migrations.
- Set up Hilt compilation support.

### 🟩 Build 2: UI, DI & Navigation Purge [COMPLETE]
- Decommission Voyager Navigation; implement Jetpack Navigation `NavHost` in `MainActivity`.
- Eradicate legacy Koin and Injekt registries in favor of Hilt compile-time DI.
- Migrate leaf screens to pure `@Composable` layouts with `@HiltViewModel`s.
- Clean up main-thread blocking calls (`runBlocking` preferences queries) with cached `.getSync()` and async loaders.

### 🟦 Build 3: Database & Widget Performance [ACTIVE]
- [x] Convert all legacy SQLDelight `.sq` queries into reactive Room Entity and DAO layers.
- [ ] Optimize Glance Widgets (`BaseUpdatesGridGlanceWidget`) by introducing background pre-caching workers.
- [x] Decommission `AndroidDatabaseHandler` and completely remove the SQLDelight framework.

### ⬜ Build 4: Domain Refinement & Clean Arch
- Purge any remaining direct data dependencies in ViewModels, ensuring 100% coverage of single-purpose Domain Interactors.
- Deprecate `CoreContainer` once all legacy widgets and plugins leverage standard Hilt EntryPoints.
