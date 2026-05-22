# Ephyra Project Architecture Index (AS-IS State)

## 📜 Project Overview
Ephyra is a large-scale, modular Android application designed to provide a comprehensive, personalized content viewing experience. Its primary functions include:
1. **Identity Management:** Handling user authentication, profile synchronization, and session management.
2. **Content Viewing:** Core functionality for consuming rich, serialized content (e.g., manga, articles, streams).
3. **Personalization & Settings:** Allowing users to customize their experience, manage local data, and configure application features.

The application structure is modular, separating concerns into distinct subprojects to manage complexity, though some coupling exists between legacy components.

## 🧩 Module Breakdown

| Subproject Path | Primary Responsibility | Key Components/Packages | Notes |
| :--- | :--- | :--- | :--- |
| `:app` | Application Entry Point & UI Composition | `com.ephyra.ui.main`, `MainActivity`, `AppNavigator` | Contains the main Activity and orchestrates feature module navigation. |
| `:core` | Shared Infrastructure & Common Logic | `com.ephyra.core.network`, `com.ephyra.core.util`, `di` | Holds utility classes, networking interfaces, and common dependency injection setup. |
| `:data` | Data Persistence Layer & Sources | `com.ephyra.data.database`, `dao`, `remote` | Manages data access objects (DAO) and handles the aggregation of local and remote data sources. |
| `:feature:identity` | User Authentication & Profile Management | `com.ephyra.identity.viewmodel`, `LoginScreen`, `AuthRepository` | Handles sign-in/sign-up flows and manages user session tokens. |
| `:feature:content` | Core Content Display Logic | `com.ephyra.content.view`, `ContentViewModel`, `ContentRepository` | The most complex module; responsible for displaying and navigating serialized content. |
| `:macrobenchmark` | Performance Testing Suite | `androidx.benchmark` | Used for running critical user flow benchmarks and performance profiling. |

## 💾 Data Persistence Layer (CRITICAL)

The current data model relies heavily on a combination of SQLDelight for structured relational data and various Java/Kotlin objects for transient state.

**SQLDelight Schemas (`*.sq` files):**
The primary data schema is managed within the `:data` module using SQLDelight. Key schemas include:
1. **`Manga.sq`:** Defines core manga metadata (Title, Author, Status, etc.).
2. **`Chapter.sq`:** Stores chapter-level details, linking to `Manga` and containing chapter metadata (Page count, release date).
3. **`UserPref.sq`:** Stores user-specific settings and preferences that persist across sessions.

**Primary Entities:**
*   **`Manga`:** Represents the overarching comic or story. Primary key: `manga_id`.
*   **`Chapter`:** Represents a single unit of content within a manga. Foreign key to `Manga`.
*   **`UserPref`:** Stores application settings (e.g., reading mode, dark theme preference).

**Current Data Flow:**
The `ContentRepository` (in `:data`) is the primary consumer. It uses **`MangaDao`** (SQLDelight DAO) to execute queries. For example, to load a chapter's details, it typically executes `SELECT * FROM Chapter WHERE chapter_id = ?` and then maps the resulting Cursor/List into a domain object, often using a `Flow` wrapper around the DAO call. This data is then exposed to the `ContentViewModel`.

## 🚀 Major Code Flows

### 1. User Logs In
*   **Sequence:** `MainActivity` (View) $\rightarrow$ `LoginViewModel` (ViewModel) $\rightarrow$ `IdentityRepository` (Repository) $\rightarrow$ `AuthService` (Networking/External API call) $\rightarrow$ `UserDefaults/SharedPreferences` (Local Token Storage).
*   **Analysis:** The flow is linear but relies heavily on synchronous calls or simple `suspend` functions for token retrieval and storage, which can block the main thread if not careful.

### 2. Viewing Chapter Content
*   **Sequence:** `ContentActivity` (Activity) $\rightarrow$ `ContentViewModel` (ViewModel) $\rightarrow$ `ContentRepository` (Repository) $\rightarrow$ `ChapterDao` (SQLDelight DAO) $\rightarrow$ `Domain Object` (Flow).
*   **Analysis:** This is the most critical path. It involves multiple data sources (local database for metadata, remote service for images) and currently uses a combination of coroutine streams and reactive streams, leading to potential state management complexity when merging data.

### 3. Updating User Preferences
*   **Sequence:** `SettingsFragment` (Fragment) $\rightarrow$ `SettingsViewModel` (ViewModel) $\rightarrow$ `UserSettingsRepository` (Repository) $\rightarrow$ `UserPrefDao` (SQLDelight DAO) $\rightarrow$ `Room/SharedPreferences` (Write).
*   **Analysis:** The interaction is simple, but the coupling between UI state changes and data persistence writes is managed directly within the ViewModel, violating some separation principles.
