# Mihon Fork — Authority-First Manga Management

This fork adds an **authority-first identity system** on top of Mihon's source-based model. Every manga can have a canonical identity from MAL, AniList, or MangaUpdates that persists across sources, enabling use cases beyond just reading online.

## User Stories

### 1. The Cataloger (information-only)

*"I read manga in print, on other apps, or at the library. I want to track what I've read and remember where I left off."*

**What works today:**
- Add any series via tracker search (MAL, AniList, MangaUpdates, Kitsu, Bangumi, Shikimori)
- Track reading progress, scores, start/finish dates, and reading status per tracker
- Write personal **notes** on any manga entry (dedicated notes screen per manga)
- Organize into **categories** with custom names and ordering
- No source needed — the app stores a canonical ID (`al:21`) and won't show health warnings for manga that never had chapters
- Sync progress back to tracker sites to keep your online profile current
- **Backup/restore** preserves all tracking data, notes, canonical IDs, and categories

### 2. The Local Reader (existing library)

*"I have manga files (CBZ, EPUB, folders) on my device. I want to organize and read them with progress tracking."*

**What works today:**
- **Local source** imports manga from device storage (CBZ archives, EPUB, folder structures)
- Link local manga to trackers for canonical identity and progress sync
- Organize local manga into categories, add personal notes
- Local sources are **excluded from health detection** — no false DEAD/DEGRADED warnings
- Health banner hidden for local manga in detail view
- Download chapters for offline reading; downloaded content survives source changes

### 3. The Local Content Creator

*"I add my own scans or downloads to the local library over time."*

**What works today:**
- Add files to the local source directory; they appear on next library refresh
- Same organization tools as any other manga: categories, notes, tracker links
- Mix local and online manga in the same library with unified filtering and sorting

### 4. The Online Reader

*"I read from online sources and want a smooth reading experience with progress tracking."*

**What works today:**
- Browse and search across installed extension sources
- Track progress via MAL/AniList/MangaUpdates — canonical ID auto-set on tracker bind
- **Alternative titles** pulled from AniList (romaji, native, synonyms) stored as JSON
- Download chapters for offline reading
- Library filters: downloaded, unread, started, bookmarked, completed status
- Sort by title, chapters, latest update, date added, or unread count
- Display modes: compact grid, comfortable grid, or list — configurable per category

### 5. The Migrator (source changes)

*"My source died or I want to switch. I need to move my manga without losing progress."*

**What works today:**
- **Automatic health detection** during library updates — classifies sources as HEALTHY, DEGRADED, or DEAD
- **Visual warnings everywhere**: ⚠ badge on library covers, colored source name in detail, warning banner with "Migrate" button
- **Notifications**: dead/degraded alerts after updates, migration reminder after 3 days dead
- **Smart migration search**: canonical ID (free, instant) → primary title → alt titles → near-match → deep search, with match confidence percentage
- **Library filter** to show only dead/degraded manga for batch triage
- Health status and dead_since timestamps survive backup/restore

### 6. The Progress Tracker (offline sync)

*"I read offline or across devices. I want my reading position to stay synced."*

**What works today:**
- Tracker sync on chapter read — updates MAL/AniList/MangaUpdates automatically
- Reading progress persisted locally per chapter (last page read)
- History tracks when each chapter was read
- **Backup/restore** captures full state: library, chapters, tracking, history, categories, notes
- Restore on a new device picks up exactly where you left off

### 7. The Organizer

*"I have a large library and need to keep it tidy."*

**What works today:**
- **Categories** with custom names, drag-to-reorder, per-category display and sort settings
- **Filters**: downloaded, unread, started, bookmarked, completed, dead/degraded sources — all combinable as include/exclude
- **Sorting**: alphabetical, by chapter count, latest update, date added, unread count
- **Display modes**: compact grid, comfortable grid, list — set globally or per category
- **Notes** per manga for personal annotations
- Library health banner shows at-a-glance count of manga needing attention

### 8. The Sharer

*"I want to share my favorites or export parts of my library."*

**What works today:**
- **Share manga** link directly from manga detail screen (shares source URL)
- **Share cover** image from manga detail
- **Backup export** with granular options: library entries, categories, chapters, tracking, history, app settings — mix and match
- Backup files are portable `.tachibk` format for sharing between devices

**What could be built later** (not currently planned):
- Share a curated list of series (subset export by category)
- Share notes or recommendations as formatted text
- Group/list sharing with friends

### 9. The Explorer

*"I want to find new series similar to what I already enjoy."*

**What works today:**
- **Browse sources** with per-source filters (genre, status, popularity)
- **Global search** across all installed sources simultaneously
- Source-specific popular/latest listings

**What could be built later** (not currently planned):
- "More like this" recommendations based on linked tracker data
- Import reading lists or favorites from tracker sites to populate library

## Fork Features Summary

| Feature | Status |
|---------|--------|
| Canonical ID from trackers | ✅ Auto-set on bind |
| Alternative titles (AniList) | ✅ JSON storage, used in search |
| Source health detection | ✅ DEAD/DEGRADED/HEALTHY on update |
| Health UI (banner, badge, color) | ✅ All library + detail views |
| Health notifications | ✅ Post-update + 3-day migration reminder |
| Smart migration search | ✅ 4-tier with confidence % |
| Library health filter | ✅ TriState include/exclude |
| Local/stub source safety | ✅ Excluded from health detection |
| Backup completeness | ✅ Canonical ID, status, dead_since |
| Design tokens | ✅ Consistent spacing system |
| Manga notes | ✅ Upstream feature, fully supported |
| Categories + organization | ✅ Upstream feature, fully supported |
| Tracker sync | ✅ Upstream feature, fully supported |

135 unit tests. Zero compiler warnings.

## Architecture

| Aspect | Choice |
|--------|--------|
| Identity | Canonical ID from tracker (`al:21`, `mal:30013`, `mu:12345`) |
| Alt titles | JSON array in DB, backward-compatible with legacy pipe-separated |
| Health detection | Chapter count comparison (70% threshold), zero extra API calls |
| Search | 4-tier: canonical ID (free) → title (1 call) → alt titles → deep search |
