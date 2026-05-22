# Ephyra Project Architecture Plan (TO-BE State)

## ✨ Guiding Principles

The refactor will adhere to the following modern Android architectural principles:
1. **Unidirectional Data Flow (UDF):** UI state is a single source of truth, flowing down from the ViewModel/UI layer. All interactions follow the pattern: **Event $\rightarrow$ State $\rightarrow$ View**.
2. **Compile-Time Safety:** Maximize the use of Kotlin's type system, sealed classes, and Flow to eliminate runtime casting and null pointer exceptions.
3. **Separation of Concerns (Clean Architecture):** Strictly enforce boundaries between the Presentation, Domain, and Data layers. The Domain layer must contain no Android framework dependencies.
4. **Modern Declarative UI:** Transition all View components to Jetpack Compose for a single, cohesive UI development experience.

## 💻 Technology Stack

| Component | Technology | Rationale |
| :--- | :--- | :--- |
| **UI Layer** | Jetpack Compose | Modern, declarative, and highly efficient for complex UIs. |
| **Dependency Injection** | Hilt (Dagger) | Simplifies module-level dependency graph management and promotes clean, testable dependency scoping. |
| **Data Persistence** | Room (Kotlin Extensions) | Used for structured object storage. Will be utilized alongside SQLDelight for complex, read-heavy querying, maintaining type safety. |
| **Asynchrony/State** | Kotlin Coroutines & StateFlow | Provides structured concurrency, making state management predictable and robust, especially for background data fetching. |
| **Settings/Key-Value** | DataStore | Replacement for legacy `SharedPreferences`, offering asynchronous, type-safe data persistence. |

## 🔄 Architectural Flow Diagram: Fetching Manga List

The following flow illustrates the mandated layered structure, ensuring clean separation and testability.

```mermaid
graph TD
    A[UI/Compose Composable] -->|1. User Action (Event)| B(ViewModel);
    B -->|2. State Update (Flow Emit)| C{UiState: Loading/Success/Error};
    C -->|3. Request Data (Use Case)| D[Interactor (Domain Layer)];
    D -->|4. Business Logic & Aggregation| E[Repository (Data Layer)];
    E -->|5. Data Source Call| F(DAO/Mapper Layer);
    F -->|6. Raw Data/Flow| G[Room/SQLDelight/Network API];
    G -->|7. Domain Object| E;
    E -->|8. Success State| B;
    B -->|9. State Emit| A;
```

**Layers Detailed:**
*   **UI:** Observes the `StateFlow<UiState>` exposed by the ViewModel.
*   **ViewModel:** Maps UI Events to Domain use cases and manages the state lifecycle.
*   **Interactor (Domain):** Contains pure business logic (e.g., *Can this manga be shown?*). It coordinates repositories but knows nothing about Android APIs.
*   **Repository:** Acts as the data source coordinator, deciding whether to fetch data from the network or from the local database.
*   **DAO/Mapper:** Handles the technical execution (e.g., converting a `Cursor` into a `data class`).

## 🛣️ Migration Strategy Roadmap

The refactoring will be tackled in phased increments to minimize risk:

1. **Phase 1: Foundation & UI:**
    *   **Action:** Implement a unified `Theme` module using Jetpack Compose Styles API.
    *   **Action:** Migrate the `UserPref.sq` data persistence mechanism from `SharedPreferences` to `DataStore` within the `:core:preferences` module.
    *   **Goal:** Establish a Compose-first development environment and modernize simple data persistence.

2. **Phase 2: Data Layer Refinement:**
    *   **Action:** Refactor the data access logic (`dao` packages) to strictly use Kotlin `Flow` wrappers for all database reads, ensuring asynchronous operations are non-blocking.
    *   **Action:** Introduce the **Interactor** layer between the Repository and the use-case logic, ensuring domain models are strictly defined and used.
    *   **Goal:** Achieve true separation between data fetching and business logic.

3. **Phase 3: UI & Core Flows:**
    *   **Action:** Migrate the `ContentViewing` flow: Replace `ContentActivity` with a Compose Composable.
    *   **Action:** Update all ViewModels to expose `StateFlow` and handle UI state lifecycle management.
    *   **Goal:** Complete the migration of the core user journey to a fully modern, testable, and declarative architecture.
