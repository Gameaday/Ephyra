# Mihon Fork — Feature & Architecture Brainstorm

> Living document of ideas, architectural visions, and refinement opportunities.
> Categorized by area with implementation complexity and priority indicators.

---

## 🌟 Central Feature Vision: Unified Catalog & Automatic Source Resolution

The defining feature of this fork — a system where users **add manga by identity** (not by source), and the app automatically resolves chapters from the best available source.

### Core Concept

Instead of "search MangaDex for One Piece," users would:
1. Search a **unified catalog** (backed by tracker databases like AniList/MAL/MangaUpdates)
2. Add "One Piece" to their library — no source selection needed
3. The app **automatically matches** the series across all installed extension sources
4. Chapters are sourced from the **best available provider** per a configurable strategy

### Source Resolution Strategies

| Strategy | Description | Use Case |
|----------|-------------|----------|
| **Local First** | Prefer locally downloaded chapters, then fall back to remote | Offline-heavy users |
| **Preference Hierarchy** | User-defined ordered list of preferred sources | Power users who trust certain scanlators |
| **Round Robin** | Rotate across sources to spread server costs | API-respectful users |
| **Quality Selection** | Check first page from each source, pick highest resolution | Quality maximizers |
| **Fastest Response** | Race sources, use whichever responds first | Speed-focused users |

### Automatic Series Matching — The Hard Problem

This is the make-or-break technical challenge. Approaches:

#### Approach 1: Canonical ID Matching (Most Reliable)
- Use **AniList ID** or **MyAnimeList ID** as the canonical identifier
- Many extensions already embed tracker IDs in their metadata
- For sources that don't: use the existing `SmartSourceSearchEngine` fuzzy title matching, then confirm via chapter count/author/description similarity scoring
- Store the canonical ID in the existing `metadataSource`/`metadataUrl` fields or a new `canonicalId` field

#### Approach 2: Title Normalization + Fingerprinting
- Normalize titles: strip punctuation, lowercase, remove common suffixes ("manga", "raw", etc.)
- Generate a fingerprint from: normalized title + author + approximate chapter count
- Use Levenshtein/Jaro-Winkler distance for fuzzy matching with configurable threshold
- Cross-reference with description similarity using simple TF-IDF or keyword overlap

#### Approach 3: Community-Sourced Mapping (Long-Term)
- Build a shared mapping database: `{anilist_id → [(source_id, source_url), ...]}` 
- Users who manually confirm matches contribute to the mapping
- New users benefit from accumulated community knowledge
- Privacy-preserving: only share anonymous source↔ID mappings, not library data

#### Approach 4: Hybrid (Recommended)
1. **First pass**: Check if tracker data provides canonical IDs → exact match
2. **Second pass**: Title normalization + fuzzy search across installed sources
3. **Third pass**: Score candidates by metadata similarity (author, genre, chapter count, description)
4. **Confidence threshold**: Auto-match if score > 0.85, prompt user confirmation if 0.6–0.85, skip if < 0.6
5. **Learn from corrections**: When user manually corrects a match, store the mapping for future reference

### Architecture Changes Required

```
New Domain Models:
  CatalogEntry(canonicalId, title, altTitles, author, anilistId?, malId?, ...)
  SourceMapping(catalogEntryId, sourceId, sourceUrl, confidence, confirmedByUser)
  SourcePreference(priority, strategy, enabledSources)

New Use Cases:
  ResolveCatalogEntryUseCase — Find/create canonical entry for a manga
  MatchSourcesUseCase — Auto-match across installed extensions  
  ResolveChapterSourceUseCase — Pick best source for a chapter
  SyncCatalogMappingsUseCase — Sync community mappings (optional)

Modified:
  Manga.kt — Add canonicalId field linking to CatalogEntry
  Chapter fetch — Route through source resolver instead of single source
  Migration — Becomes a "re-resolve sources" operation instead of manual search
```

### Implementation Phases

1. **Phase 1: Foundation** — Add `canonicalId` to Manga, build title normalization engine
2. **Phase 2: Auto-Match** — On library add, search all installed sources and store mappings
3. **Phase 3: Source Resolution** — Chapter fetching goes through resolver with strategy selection
4. **Phase 4: UI** — Settings for strategy selection, per-manga source override, match confidence display
5. **Phase 5: Community** — Optional anonymous mapping sharing

**Complexity**: Very High | **Impact**: Transformative | **Breaking Changes**: Yes (schema migration)

---

## 📖 Reader Improvements

### Page Turn Animations
- Add configurable page transition effects: **slide**, **curl**, **fade**, **flip**
- Use `MotionTokens` for consistent timing
- Implement as a `PageTransition` sealed interface with `Compose` animation specs
- Settings: None (instant), Slide, Curl (skeuomorphic), Fade, Flip 3D
- **Complexity**: Medium | **Priority**: High

### Thumbnail Page Scrubber
- While holding and dragging on the page timeline/seekbar, show a **floating thumbnail preview** of the target page
- Preload low-res thumbnails for all pages in current chapter
- Use Coil's thumbnail/resize transformations for memory efficiency
- Similar to video player scrubbing UX (YouTube, VLC)
- Show page number overlay alongside the thumbnail
- **Complexity**: Medium | **Priority**: High

### More Reader Themes
- **Sepia** — Warm parchment tone for comfortable extended reading
- **Night Blue** — Deep navy with subtle blue-shift, easier on eyes than pure black
- **Solarized Dark/Light** — Popular dev-friendly color schemes
- **Custom Tint** — User picks any overlay color + opacity
- **Auto** — Switch based on time-of-day or ambient light sensor
- Implementation: Reader background color + optional color matrix filter on pages
- **Complexity**: Low-Medium | **Priority**: Medium

### Reader Polish
- **Double-tap smart zoom**: Zoom to tapped panel region using content-aware detection
- **Immersive chapter transitions**: Smooth loading shimmer instead of hard cuts between chapters
- **Page gap indicator**: Subtle visual separator between pages in webtoon mode
- **Reading position memory**: Remember exact scroll position within long webtoon pages
- **Complexity**: Mixed | **Priority**: Medium

---

## 🎨 UI / Visual Design

### Cover-Based Dynamic Color Extraction
- Extract dominant colors from manga cover thumbnails using Palette API
- Tint the manga detail screen header/toolbar with extracted colors
- Subtle gradient overlay on cover images using extracted palette
- Cache extracted palettes alongside cover cache for performance
- Already have Coil for image loading — add Palette integration
- **Complexity**: Medium | **Priority**: High

### Shared Element Transitions
- Animate cover image from library grid → manga detail screen
- Use Compose `SharedTransitionLayout` and `animatedVisibilityScope`
- Cover morphs position/size/shape smoothly between screens
- Requires Voyager navigation integration or migration to Navigation Compose
- **Complexity**: High (navigation framework coupling) | **Priority**: High

### Additional Visual Polish
- **Animated navigation indicators**: Sliding pill between nav items (M3 Expressive)
- **Staggered grid layout**: Pinterest-style for library (natural aspect ratios)
- **Reading progress ring**: Circular indicator on cover showing % chapters read
- **Custom app icon packs**: Themed icons matching each color scheme
- **Glassmorphism overlays**: Blur/frosted-glass on reader overlays and bottom sheets
- **Complexity**: Mixed | **Priority**: Medium

---

## 🧭 UX / Interaction Design

### Smart Search with Suggestions
- Show **recent searches** in dropdown when search field is focused
- Display **trending/popular** titles from installed sources
- **Auto-complete** based on local library titles + source catalog
- **Fuzzy matching**: Tolerate typos using edit distance
- Tag-based filtering: Type "genre:action" or "author:Oda" for structured search
- **Complexity**: Medium | **Priority**: High

### Smart Collections / Dynamic Lists
- User-created collections with **rule-based auto-population**:
  - "Unread from tracked sources"
  - "Completed this month" 
  - "Downloaded but not started"
  - "Highly rated (score > 8)" via tracker integration
  - "Updating weekly" based on fetch interval
- Combine conditions with AND/OR logic
- Pin collections to library tabs alongside categories
- **Complexity**: Medium-High | **Priority**: High

### Quick Actions & Gestures
- **Long-press radial menu**: Mark read, download, share, track — accessible without entering detail
- **Swipe actions on list items**: Swipe chapter for quick download/mark read
- **Pull-down quick settings**: Pull down on library for quick filter toggles
- **Haptic feedback tokens**: Light (selection), medium (confirmation), heavy (destructive)
- **Complexity**: Medium | **Priority**: Medium

### Batch Operations
- **Floating action bar** during multi-select showing available actions
- **Select all in category**, **select by filter** (all unread, all downloaded)
- **Batch migrate**: Select multiple manga → auto-match → migrate in batch
- **Complexity**: Medium | **Priority**: Medium

### Onboarding Improvements
- Animated step-by-step with progressive disclosure
- Quick-start wizard: pick theme → add first source → browse → add first manga
- Contextual tips on first use of each feature
- **Complexity**: Medium | **Priority**: Low

### Adaptive Chapter Sorting
- Learn per-manga sort preference (newest-first vs oldest-first)
- Default: oldest-first for new series, newest-first for caught-up series
- Remember user override per-manga
- **Complexity**: Low | **Priority**: Low

---

## ⚡ Performance & Technology (API-Respectful)

### Baseline Profiles
- Generate startup profiles for critical paths: library scroll, reader page flip
- Use Macrobenchmark module (already exists in repo) to measure and optimize
- Target: 30%+ faster cold start, smoother initial scroll
- **Complexity**: Medium | **Priority**: High

### Compose Stability Optimization
- Run Compose compiler reports to find unnecessary recompositions
- Annotate data classes with `@Immutable`/`@Stable` where appropriate
- Convert hot-path lambdas to remembered instances
- Target: Eliminate jank in library grid scrolling
- **Complexity**: Medium | **Priority**: High

### Intelligent Prefetch Pipeline
- Prefetch next 2-3 pages while reading current page
- Prefetch next chapter's first pages when approaching chapter end
- **Respect API costs**: Configurable prefetch depth, honor rate limits
- Use priority queue: current page > next page > prefetch pages
- Cancel prefetch on rapid page navigation (don't waste bandwidth)
- **Complexity**: Medium | **Priority**: High

### Image Format Optimization
- Prefer AVIF/WebP decoding for cache storage (30-50% smaller)
- Re-encode downloaded pages to WebP before writing to disk cache
- Configurable: original format vs re-encoded (trade CPU for storage)
- **Complexity**: Low-Medium | **Priority**: Medium

### Reactive Database Queries
- Migrate from manual refresh to SQLDelight Flow-based queries
- UI automatically updates when underlying data changes
- Eliminate `forceRefresh()` patterns throughout codebase
- **Complexity**: Medium (incremental migration) | **Priority**: Medium

### Smart Background Sync
- Adaptive WorkManager scheduling based on manga update patterns
- Manga that updates daily → check daily; monthly → check weekly
- **Respect server costs**: Stagger requests, honor rate limits, exponential backoff
- Low battery / metered network → defer non-critical updates
- **Complexity**: Medium | **Priority**: Medium

### Memory-Mapped Image Cache
- Use memory-mapped files for large image caches to reduce GC pressure
- Particularly beneficial for webtoon reader with many large images
- **Complexity**: High | **Priority**: Low

### Startup Optimization
- Profile with Macrobenchmark, identify and defer non-critical initialization
- Lazy-load extension manager, tracker services, telemetry
- **Complexity**: Medium | **Priority**: Medium

---

## 🔧 Architecture & Code Quality

### Design Token Documentation
- Auto-generate living style guide from MotionTokens, ShapeTokens, Typography, Color
- Compose Preview catalog showing every token in light/dark/AMOLED modes
- **Complexity**: Low | **Priority**: Medium

### Compose Preview Catalog
- `@Preview` for every reusable component in presentation-core
- Preview variants: light/dark, compact/expanded, empty/populated states
- Use Paparazzi or Roborazzi for automated screenshot regression tests
- **Complexity**: Medium | **Priority**: Medium

### Module Boundary Enforcement
- Strict dependency rules: domain never imports from app, presentation-core never imports from data
- Use Gradle module dependency linting or custom detekt rules
- **Complexity**: Low | **Priority**: High

### Snapshot Testing
- Add Paparazzi/Roborazzi screenshot tests for key screens
- CI catches visual regressions before merge
- Start with: library grid, manga detail, reader, settings
- **Complexity**: Medium | **Priority**: Medium

### Dependency Injection Migration
- Current: Injekt (lightweight but dated)
- Consider: Koin (Kotlin-native, simpler) or Hilt (Google-backed, compile-time safe)
- Migration can be incremental — introduce new DI alongside Injekt
- **Complexity**: Very High | **Priority**: Low (works fine currently)

### Coroutine Structured Concurrency Audit
- Ensure all background work uses proper scoping and cancellation
- Audit `GlobalScope` usage, replace with structured scopes
- **Complexity**: Medium | **Priority**: Medium

### Accessibility Audit
- TalkBack/Switch Access testing for all screens
- Add missing `contentDescription` on all interactive elements
- Focus ordering for complex layouts (manga detail, reader controls)
- **Complexity**: Medium | **Priority**: Medium

### Strict Kotlin API Mode
- Enable explicit API mode for library modules (domain, source-api, presentation-core)
- Forces all public APIs to have explicit visibility modifiers
- Prevents accidental API surface expansion
- **Complexity**: Low | **Priority**: Medium

---

## 🌟 Additional Features

### Reading Statistics Dashboard
- Charts: reading velocity, genre breakdown, daily/weekly reading patterns
- Streak tracking with optional notifications
- Monthly/yearly reading summaries
- Data sourced from existing history table
- **Complexity**: Medium | **Priority**: Medium

### Community Recommendations (Optional/Opt-In)
- "Users who read X also enjoyed Y" based on anonymized library patterns
- Completely opt-in, privacy-first (differential privacy or k-anonymity)
- Could integrate with AniList/MAL recommendation APIs as simpler alternative
- **Complexity**: Very High | **Priority**: Low

### Export/Share Reading List
- Export library as shareable link, image card, or structured data (JSON/CSV)
- "Share collection" with curated picks and notes
- Import from shared lists
- **Complexity**: Medium | **Priority**: Low

### Offline Mode Indicator
- Clear visual badge/icon on manga and chapters showing offline availability
- "Available offline" filter in library
- Estimated storage usage per manga in detail screen
- **Complexity**: Low | **Priority**: Medium

### Theme Scheduling
- Auto-switch light↔dark at user-defined times or sunrise/sunset
- Per-time-slot theme selection (e.g., Lavender during day, Midnight Dusk at night)
- **Complexity**: Low | **Priority**: Low

---

## 📊 Priority Matrix

### Immediate (Next Sprint)
| Feature | Complexity | Impact |
|---------|-----------|--------|
| Baseline Profiles | Medium | High |
| Compose Stability Audit | Medium | High |
| Cover-Based Color Extraction | Medium | High |
| More Reader Themes | Low-Medium | High |
| Thumbnail Page Scrubber | Medium | High |

### Near-Term (Next Quarter)
| Feature | Complexity | Impact |
|---------|-----------|--------|
| Page Turn Animations | Medium | High |
| Smart Search Suggestions | Medium | High |
| Smart Collections | Medium-High | High |
| Shared Element Transitions | High | High |
| Unified Catalog — Phase 1 (Foundation) | High | Transformative |

### Medium-Term (3-6 Months)
| Feature | Complexity | Impact |
|---------|-----------|--------|
| Unified Catalog — Phase 2-3 (Auto-Match + Resolution) | Very High | Transformative |
| Intelligent Prefetch Pipeline | Medium | High |
| Snapshot Testing | Medium | Medium |
| Reading Statistics Dashboard | Medium | Medium |

### Long-Term (6+ Months)
| Feature | Complexity | Impact |
|---------|-----------|--------|
| Unified Catalog — Phase 4-5 (UI + Community) | Very High | Transformative |
| Community Recommendations | Very High | Medium |
| DI Migration | Very High | Medium |

---

## 🏗️ Technical Debt to Address

- [ ] Replace hardcoded animation durations with `MotionTokens` throughout app module
- [ ] Replace hardcoded padding values with `MaterialTheme.padding` tokens  
- [ ] Replace hardcoded shape radii with `ShapeTokens` / `MaterialTheme.shapes`
- [ ] Audit and add `@Immutable`/`@Stable` annotations to data classes used in Compose
- [ ] Increase test coverage (currently only `MigratorTest` exists)
- [ ] Add KDoc to all public APIs in domain and source-api modules
- [ ] Migrate remaining uses of `titleSmall` to `sectionLabel` for section headers
