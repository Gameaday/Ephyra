# Ephyra Architectural Roadmap & Next-Phase Initiatives

This document defines the architectural roadmap for Ephyra's continuous MAD evolution, multi-method content sourcing system, and autonomous library resilience.

---

## 1. Multi-Method Sourcing & Unified Reader Architecture (Current Status: ✅ Complete)

Ephyra has transitioned from a legacy single-source architecture (monolithic APK extensions and legacy Android View readers) into a unified, resilient, MAD-compliant multi-format platform:

1. **Pure Jetpack Compose Readers**:
   - `ComposePagerReader`: Horizontal (L2R, R2L) and Vertical paginated reading with smart combining, stub pre-scanning, and zero dependency on legacy `DirectionalViewPager`.
   - `ComposeWebtoonReader`: GPU-accelerated vertical strip reading powered by `LazyColumn`, preloading triggers, hardware key scrolling (`scrollByRequest`), and native zoom/gestures.
   - Complete retirement of ~1,200 lines of legacy XML/View reader classes (`WebtoonRecyclerView`, `WebtoonLayoutManager`, `WebtoonFrame`, `WebtoonAdapter`, `WebtoonPageHolder`, `WebtoonTransitionHolder`, `WebtoonBaseHolder`).

2. **Multi-Method Content Sourcing Ecosystem**:
   - **Method 1 (Legacy Transpilation)**: In-memory AST transpilation of Tachiyomi-compatible APK extension classes into play-store safe standalone scrapers without runtime APK installations.
   - **Method 2 (Multi-Source Transpilation)**: Shared theme transpilation (MangaThemesia, Madara, etc.) injecting custom selectors dynamically.
   - **Method 3 (Adaptive Layout Heuristics)**: On-device structural DOM pattern discovery (`AdaptiveHeuristicEngine`) that probes websites, extracts selectors, and learns endpoints autonomously.
   - **Method 4 (Autonomous Fallback Orchestration)**: `ContentSourceOrchestrator` dynamically cascades from sandboxed JS scrapers to heuristic extractors when remote sources alter their layout or encounter network errors.
   - **Live Layout Inspector**: Live diagnostic probing in the Content Sourcing Hub allowing users to enter any website URL, test discovery live, and inspect extracted metadata and selectors before saving.

3. **Room Database Evolution**:
   - `EphyraDatabase` canonical Room migration from schema v2 to v3 introducing the `source_profiles` table.
   - `RoomSourceProfileStore` backed by `SourceProfileDao` with automatic transparent migration from legacy `PreferenceStore` entries.

---

## 2. Upcoming Initiatives: Autonomous Source Health & Self-Healing

The following two initiatives represent the next evolutionary phase for Ephyra's reading and library experience.

### Idea 2: Reader-Level Inline Dead Source Prompt & 1-Click Migration

#### Motivation
When a remote website changes its domain, dies, or permanently enforces Cloudflare/captchas, users currently encounter blank pages or load errors in the reader with no immediate recourse other than manually exiting to the browse screen.

#### Architecture & Design
- **Trigger Conditions**:
  - `ReaderScreen` / `ReaderViewModel` detects 3 consecutive page load failures on a chapter (`Page.State.Error`), or `manga.deadSince != null`.
  - The reader checks `ContentSourceOrchestrator.suggestMigration(manga)` in the background without interrupting the user.
- **UI Integration**:
  - A non-intrusive bottom banner or overlay appears inside `ComposeWebtoonReader` / `ComposePagerReader`:
    > *"Source appears unreachable. Alternative source found: [Source Name] (Chapter N available). Tap to migrate."*
  - Tapping opens a Compose `ModalBottomSheet` displaying candidate sources scored by `TitleNormalizer` fuzzy similarity, chapter counts, and latency.
- **1-Click Atomic Switch**:
  - The user confirms migration.
  - `MigrateMangaUseCase` updates `manga.source`, fetches chapter list, matches current chapter progress by chapter number, and re-initializes the reader seamlessly on the same page index without leaving the reading session.

---

### Idea 3: Proactive Background Library Health & Auto-Migration Worker

#### Motivation
Over time, library entries may silently break when websites shut down or alter URLs. Rather than waiting for the user to open a dead manga months later, the application should monitor source health proactively and heal library entries in the background.

#### Architecture & Design
- **WorkManager Periodic Task**:
  - A low-priority background worker (`LibrarySourceHealthWorker`) scheduled via AndroidX `WorkManager` (e.g., weekly or on unmetered Wi-Fi during device idle).
  - Inspects library entries with `favorite == true` and verifies source endpoints against `SourceProfile.lastHealthCheck`.
- **Pre-computed Candidate Matching**:
  - When a source is detected dead or unreachable (`failureCount >= 3`), the worker runs `ContentSourceOrchestrator.suggestMigration(manga)`.
  - It records candidates in a local cache or a `library_healing_suggestions` table without altering user data destructively.
- **Library Healing Notification & Badge**:
  - The Library screen displays a subtle "Source Health" badge or notification:
    > *"3 series in your library have migrated to new domains. Review and update."*
  - A batch-review screen displays each broken series alongside its top suggested match, allowing 1-tap migration or single-click "Migrate All Verified".
- **Zero Framework Contamination**:
  - The domain logic (`CheckLibraryHealthUseCase`, `FindLibraryMigrationsUseCase`) remains 100% pure Kotlin in `:core:domain`.
  - The `WorkManager` worker lives in `:app` or `:core:data`, calling domain use cases cleanly.

---

## 3. Room Schema Governance & Migration Checklist

For all future database additions:
1. Every entity change MUST bump `@Database(version = N)` in `EphyraDatabase.kt`.
2. Implement idempotent `MIGRATION_(N-1)_N` in `Migrations.kt` (`CREATE TABLE IF NOT EXISTS`, etc.).
3. Append `MIGRATION_(N-1)_N` to `Migrations.ALL`.
4. Update `Migrations.DB_VERSION = N`.
5. Compile `:core:data` with KSP to export the schema JSON `N.json` into `core/data/schemas/ephyra.data.room.EphyraDatabase/`.
6. Run `MigrationCoverageTest` to ensure all historical steps from 1 to N are tested.
