# Mihon Fork — Roadmap & Next Actions

> **Last updated:** 2026-03-05
> **Branch:** `copilot/improve-design-and-performance`

---

## Current State Summary

### ✅ Fully Implemented

| Feature | Key Files | Tests |
|---------|-----------|-------|
| **Database schema** — `canonical_id`, `source_status`, `alternative_titles`, `dead_since` columns on `mangas` table | Migrations 12.sqm–15.sqm | Schema validated via build |
| **Canonical ID auto-population** — Sets `canonical_id` from tracker remote IDs on first tracker bind (MAL → `mal:`, AniList → `al:`, MangaUpdates → `mu:`) | `AddTracks.setCanonicalIdIfAbsent()` | 7 unit tests |
| **Alternative titles pipeline** — AniList romaji/english/native/synonyms merged into manga, case-insensitive dedup. JSON array storage with pipe-separated fallback. | `AddTracks.mergeAlternativeTitles()`, `ALSearchItem.buildAlternativeTitles()`, `MangaMapper.parseAlternativeTitles()` | 8 + 16 unit tests |
| **Tiered search engine** — 4-tier migration search with cross-title evaluation, dedup, near-match tracking | `BaseSmartSearchEngine.multiTitleSearch()`, `MigrationListScreenModel.searchSource()` | 25 unit tests |
| **Canonical ID lookup** — Zero-API-call local DB match via partial index | `GetFavoritesByCanonicalId`, `mangas.sq:getFavoritesByCanonicalId`, `14.sqm` index | 6 unit tests |
| **Source health detection** — Automatic HEALTHY/DEGRADED/DEAD classification during library updates with recovery support. Skips local sources. | `LibraryUpdateJob.detectSourceHealth()` | 18 unit tests |
| **Source health UI** — Warning banner on manga detail screen for DEGRADED/DEAD sources. Skips local manga and stub (uninstalled) sources. | `SourceHealthBanner.kt`, `MangaScreen.kt` | 7 unit tests |
| **Source health notification** — Post-update notification listing dead/degraded sources | `LibraryUpdateNotifier.showSourceHealthNotification()` | N/A |
| **Bulk migration prompt** — `dead_since` timestamp tracks persistently DEAD sources, suggests migration after 3+ days | `LibraryUpdateJob`, `LibraryUpdateNotifier.showMigrationSuggestionNotification()` | 11 unit tests |
| **GetDeadFavorites interactor** — Queries persistently DEAD manga by dead_since cutoff | `GetDeadFavorites`, `mangas.sq:getFavoritesByDeadSinceBefore` | 5 unit tests |
| **Backup/restore completeness** — `canonicalId`, `sourceStatus`, `deadSince` persisted across backup cycles | `BackupManga`, `MangaBackupCreator`, `MangaRestorer` | Verified via integration |
| **Library source health filter** — TriState filter toggle to show/hide DEAD/DEGRADED manga in library | `LibraryPreferences`, `LibraryScreenModel`, `LibrarySettingsDialog` | 19 unit tests |
| **Design token system** — Padding, Shape, Motion, Typography, Color, Navigation, Badge, Pill tokens with full adoption across all presentation components | `Constants.kt`, `Motion.kt`, `Shapes.kt`, `Typography.kt`, `Color.kt`, `NavigationTokens.kt`, `BadgeTokens.kt`, `PillTokens.kt` | N/A |
| **Library health summary banner** — Animated banner at top of library showing dead/degraded source counts. Click to enable health filter. | `LibraryHealthBanner.kt`, `LibraryContent.kt`, `LibraryTab.kt` | N/A |
| **Migration match quality** — Confidence percentage displayed on migration search results. Color-coded: green (≥90%), tertiary (≥70%), red (<70%). Hidden for exact matches. | `MigrationListScreenContent.kt`, `SmartSourceSearchEngine.kt`, `BaseSmartSearchEngine.kt` | 25 unit tests |
| **Source health color in info header** — Source name tinted red (DEAD) or tertiary/yellow (DEGRADED) in manga detail header | `MangaInfoHeader.kt`, `MangaScreen.kt` | N/A |

### ⚠️ Partially Implemented

*None — all tracked features are either complete or not yet started.*

### ❌ Not Yet Implemented (Documented in BRAINSTORM.md)

| Feature | Brainstorm Section | Complexity |
|---------|-------------------|------------|
| **source_mappings table** — Full multi-source discovery (Approach A) | Parts 1-12 | High |
| **Source discovery protocol** — Automated cross-source search with confidence scoring | Part 2 | High |
| **Chapter resolution strategies** — HIERARCHY, ROUND_ROBIN, QUALITY strategies | Part 3 | High |

---

## Technical Debt

### 1. Hardcoded Design Values → Should Use Tokens

| Component | File | Hardcoded Values | Status |
|-----------|------|------------------|--------|
| ~~`Badges.kt`~~ | ~~`presentation-core/.../Badges.kt:50,95`~~ | ~~`3.dp` horizontal, `1.dp` vertical~~ | ✅ Fixed — BadgeTokens.HorizontalPadding, BadgeTokens.VerticalPadding |
| ~~`Pill.kt`~~ | ~~`presentation-core/.../Pill.kt:35`~~ | ~~`6.dp, 1.dp`~~ | ✅ Fixed — PillTokens.HorizontalPadding, PillTokens.VerticalPadding; `4.dp` → Padding.extraSmall |
| ~~`LazyColumnWithAction.kt`~~ | ~~`presentation-core/.../LazyColumnWithAction.kt:41`~~ | ~~`16.dp, 8.dp`~~ | ✅ Fixed — Padding.medium, Padding.small |
| ~~`SettingsItems.kt`~~ | ~~`presentation-core/.../SettingsItems.kt:391,449`~~ | ~~`4.dp`, `24.dp`~~ | ✅ Fixed — Padding.extraSmall, Padding.large |
| ~~`VerticalFastScroller.kt`~~ | ~~`presentation-core/.../VerticalFastScroller.kt`~~ | ~~`8.dp`~~ | ✅ Fixed — Padding.small (thumb dimensions kept component-specific) |
| ~~`LinkIcon.kt`~~ | ~~`presentation-core/.../LinkIcon.kt:22`~~ | ~~`4.dp`~~ | ✅ Fixed — Padding.extraSmall |
| ~~`Tabs.kt`~~ | ~~`presentation-core/.../material/Tabs.kt:29`~~ | ~~`10.sp`~~ | ✅ Fixed — Typography.badgeLabel extension |
| ~~`NavigationBar.kt`~~ | ~~`presentation-core/.../material/NavigationBar.kt:43`~~ | ~~`80.dp`~~ | ✅ Fixed — NavigationTokens.NavBarHeight |
| ~~`NavigationRail.kt`~~ | ~~`presentation-core/.../material/NavigationRail.kt`~~ | ~~`80.dp`~~ | ✅ Fixed — NavigationTokens.NavRailMinWidth, NavigationTokens.NavRailTonalElevation |
| ~~`CircularProgressIndicator.kt`~~ | ~~`presentation-core/.../CircularProgressIndicator.kt`~~ | ~~`tween(2000)`~~ | ✅ Fixed — ROTATION_DURATION_MS constant |

### 2. Missing Test Coverage

| Area | Current | Needed |
|------|---------|--------|
| ~~`AddTracks.setCanonicalIdIfAbsent()`~~ | ~~No unit tests~~ | ✅ 7 tests added |
| ~~`AddTracks.mergeAlternativeTitles()`~~ | ~~No unit tests~~ | ✅ 8 tests added |
| ~~`LibraryUpdateJob` health detection~~ | ~~No unit tests~~ | ✅ 18 tests (DEAD, DEGRADED, HEALTHY, recovery, edge cases, dead_since tracking) |
| ~~`MangaMapper` alt title parsing~~ | ~~No unit tests~~ | ✅ 16 tests (JSON, pipe, round-trip, special chars) |
| ~~`GetFavoritesByCanonicalId`~~ | ~~No unit tests~~ | ✅ 6 tests (match, exclude self, exclude non-favorite, null canonical, multiple sources) |
| ~~`GetDeadFavorites`~~ | ~~No unit tests~~ | ✅ 5 tests (before cutoff, after cutoff, non-favorites, null/zero deadSince) |
| ~~Library health filter~~ | ~~No unit tests~~ | ✅ 19 tests (SourceStatus mapping, TriState filter logic for all states) |
| Design tokens | No tests | Snapshot tests for token values to prevent regression |

### 3. Code-Level Issues

- ~~**Pipe-separated `alternative_titles`** — Stored as `TEXT` with `|` delimiter. If a title contains `|` character, it breaks parsing.~~ ✅ Fixed — now serialized as JSON array, with backward-compatible parsing for legacy pipe-separated data.
- ~~**`AddTracks.kt:105`** — Non-null assertion `toDomainTrack(idRequired = false)!!` could crash.~~ ✅ Fixed — replaced with safe `?: return@let`.
- ~~**Backup/restore** — `canonicalId`, `sourceStatus`, `deadSince` not persisted across backups.~~ ✅ Fixed — added to `BackupManga` (ProtoNumber 115-117) and `MangaBackupCreator`.
- ~~**Compiler warnings** — `LibraryUpdateJob.kt:426` and `MangaScreenModel.kt:296` had always-true conditions due to redundant null checks alongside boolean.~~ ✅ Fixed — removed intermediate `usingMetadataSource` boolean, use direct null checks for Kotlin smart cast.
- **`AddTracks.kt:52`** — Type check `item is TrackSearch` only works when caller passes `TrackSearch` instance; `Track` instances from general tracker bind paths won't trigger alt title merge. This is intentional: only `TrackSearch` instances carry alt titles from tracker API results.
- ~~**`MigrationListScreenModel.kt`** — `findByCanonicalId()` returns first match per source; doesn't consider confidence scoring or prefer certain tracker prefixes.~~ Kept as-is — canonical IDs are unique per series, so multiple matches on the same source is a theoretical edge case not worth the complexity.

---

## Next Actions (Prioritized)

### Phase 1: Quick Wins (Low Effort, High Value) — ✅ COMPLETE

#### 1.1 Source Health UI Indicator ✅
**Done:** Added `SourceHealthBanner` composable with DEGRADED (tertiary) and DEAD (error) color scheme. Wired into both small and large manga detail layouts between action row and description. Added `source_health_degraded` and `source_health_dead` string resources.

#### 1.2 Migrate Remaining Hardcoded Values to Tokens ✅
**Done:** All presentation components now use design tokens. Initial migration: LazyColumnWithAction (→Padding.medium/small), LinkIcon (→Padding.extraSmall), SettingsItems (→Padding.extraSmall/large), VerticalFastScroller (→Padding.small), CircularProgressIndicator (→ROTATION_DURATION_MS). Final migration: Badges.kt (→BadgeTokens), Pill.kt (→PillTokens + Padding.extraSmall), Tabs.kt (→Typography.badgeLabel), NavigationBar (→NavigationTokens.NavBarHeight), NavigationRail (→NavigationTokens.NavRailMinWidth + NavRailTonalElevation).

#### 1.3 Unit Tests for AddTracks Pipeline ✅
**Done:** Added 15 tests in `AddTracksTest.kt`: 7 for `setCanonicalIdIfAbsent()` (prefix mapping, first-wins, skip zero/negative/unknown tracker), 8 for `mergeAlternativeTitles()` (add, dedup, case-insensitive, blank filter, preserve existing). Changed method visibility to `internal` for testability.

### Phase 2: Feature Completion (Medium Effort) — ✅ COMPLETE

#### 2.1 Source Health Recovery Logic + Tests ✅
**Done:** Extracted `detectSourceHealth()` as a pure companion function in `LibraryUpdateJob` for testability. Added logging for status transitions (recovery from DEAD/DEGRADED → HEALTHY). Added 15 unit tests covering DEAD detection (0 chapters), DEGRADED detection (<70% threshold), HEALTHY (at/above threshold, growth), recovery from DEAD, edge cases (small manga).

#### 2.2 Dead/Degraded Source Notification ✅
**Done:** Added `showSourceHealthNotification()` to `LibraryUpdateNotifier` — after each library update, scans updated manga for DEAD/DEGRADED status and shows a grouped notification with affected titles. Uses error channel, auto-cancel on tap. Added `notification_source_health_title`, `notification_dead_sources`, `notification_degraded_sources` string resources.

#### 2.3 Alternative Title Delimiter Safety ✅
**Done:** Migrated from pipe-separated to JSON array storage. `MangaMapper.parseAlternativeTitles()` handles both formats (JSON first, pipe fallback for backward compatibility). `serializeAlternativeTitles()` always writes JSON. Updated `MangaRepositoryImpl` and `MangaRestorer`. Added 16 unit tests covering JSON/pipe parsing, round-trip, special characters (including `|` in titles), null/blank handling.

### Phase 3: Advanced Features (High Effort)

#### 3.1 Bulk Migration Prompt for DEAD Sources ✅
**Done:** Added `dead_since` column (Migration 15.sqm) to track when manga first became DEAD. `LibraryUpdateJob` sets timestamp on DEAD transition, clears on recovery. After library update, manga DEAD for 3+ days trigger `showMigrationSuggestionNotification()` with affected titles. SQL query `getFavoritesByDeadSinceBefore` supports direct DB lookup. 11 unit tests covering model, constants, and threshold logic.

#### 3.2 Backup/Restore Completeness ✅
**Done:** `BackupManga` now persists `canonicalId` (ProtoNumber 115), `sourceStatus` (116), `deadSince` (117) across backup cycles. `MangaBackupCreator` includes all three. `MangaRestorer` handles both update and insert paths. Backward compatible with old backups.

#### 3.3 Code Quality Fixes ✅
**Done:** Fixed non-null assertion crash in `AddTracks.bindEnhancedTrackers()` (replaced `!!` with safe `?: return@let`). Added deep search empty title guard. Local source and stub source filtering for health detection and UI.

#### 3.4 Library Source Health Filter ✅
**Done:** Added `filterSourceHealthDead` TriState preference in `LibraryPreferences`. Filter toggle in `LibrarySettingsDialog` filter tab. `LibraryScreenModel.applyFilters()` checks `sourceStatus` for DEAD/DEGRADED. ENABLED_IS shows only affected manga, ENABLED_NOT hides them.

#### 3.5 UX/UI Improvements ✅
**Done:**
- **Library Health Summary Banner** — `LibraryHealthBanner` composable with animated visibility at top of library. Shows dead/degraded counts with warning icon. Error container color for dead sources, tertiary for degraded only. Click-to-filter enables health filter (`ENABLED_IS`). Wired into `LibraryContent` between tabs and pager.
- **Migration Match Quality Indicator** — `multiTitleSearch()` now returns `SearchEntry<T>` with similarity score. `SearchResult.Success.matchConfidence` field (0.0-1.0). Color-coded display: primary (≥90%), tertiary (≥70%), error (<70%). Hidden for exact matches (100%) to reduce clutter. Canonical ID matches get 1.0 confidence.
- **Source Health Color in Info Header** — Source name in `MangaContentInfo` tinted by health status: error (DEAD), tertiary (DEGRADED), default (HEALTHY). Threaded through `MangaInfoBox` → `MangaAndSourceTitles*` → `MangaContentInfo` in both phone and tablet layouts.

#### 3.6 Source Health History
**What:** Track status transitions over time to distinguish temporary outages from permanent source death.
**Why:** A single failed update shouldn't mark a source DEAD permanently.
**Where:** New column or table for health history timestamps.
**Effort:** ~6-8 hours

#### 3.7 Cross-Source Discovery
**What:** Implement the automated source discovery protocol from BRAINSTORM.md Part 2.
**Why:** Enables proactive source failover instead of manual migration.
**Where:** New discovery service, rate limiting, confidence scoring.
**Effort:** ~20-30 hours (major feature)

#### 3.8 Multi-Source Chapter Resolution
**What:** Implement HIERARCHY/ROUND_ROBIN/QUALITY strategies from BRAINSTORM.md Part 3.
**Why:** Enables automatic chapter fetching from multiple sources per manga.
**Where:** New `source_mappings` table, resolution engine.
**Effort:** ~30-40 hours (major feature)

---

## Decision Points

1. **Should we add `source_mappings` table now or later?**
   - Current approach (Approach C) is lean: 3 columns, no new tables.
   - BRAINSTORM.md Approach A adds `source_mappings` table for full multi-source.
   - **Recommendation:** Stay with Approach C until Phase 3 features are started. Approach C → A is additive (no breaking changes).

2. ~~**Should alt titles use JSON instead of pipe-delimited?**~~
   ✅ **Resolved:** Migrated to JSON array storage with backward-compatible pipe-separated parsing. Titles with `|` characters are now safe.

3. ~~**Should we add a `Padding.micro` (2.dp) token?**~~
   ✅ **Resolved:** Created component-specific tokens (`BadgeTokens`, `PillTokens`) instead. Values like 1.dp/3.dp/6.dp are component-specific and don't warrant a general-purpose padding token.

---

## Architecture Decisions (Already Made)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Identity system | Approach C (Curated Search) | Minimal schema, zero API cost increase, upgradable to Approach A |
| Canonical ID format | `prefix:remoteId` (e.g., `al:21`) | Human-readable, supports multiple tracker backends |
| Health detection | Chapter count comparison (70% threshold) | Zero additional API calls, uses data already fetched |
| Search strategy | 4-tier with cross-title evaluation | Balances API cost vs match quality |
| Design tokens | Material Expressive system | Padding, Shape, Motion, Typography, Color, Navigation, Badge, Pill tokens |
| Alt title storage | JSON array (backward-compatible) | Handles `\|` in titles, round-trip safe, legacy pipe-separated still readable |
| Alt title source | AniList (romaji/english/native/synonyms) | Most complete metadata; MangaDex alt titles planned but SManga lacks field |
