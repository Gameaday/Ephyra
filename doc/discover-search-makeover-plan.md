# Discover Search Page — Makeover Plan

> Companion to `doc/historical/phase2-discover-plan.md` and `doc/phase2-search-v1-spec.md`.
> Records the diagnosed defects, the fixes already shipped on `cline/tj9m33yf`, and the
> ordered backlog for the rest of the page refresh.

## 1. Symptom report (from the field)

1. "Discover search page doesn't return anything (searching only pinned?)."
2. "Clicking the chips for installed material also returns nothing."
3. "The search button is out of the way."
4. "Whole page could get a make over."

The Discover tab is `BrowseTabScreen` (`feature/browse/.../BrowseScreen.kt`): a two-page
`TabbedScreen` pager — **Search** (default, `UnifiedSearchTab`) and **Sources**
(`sourcesTab`). Everything below is about these two pages unless noted.

## 2. Root-cause analysis

### 2.1 Search returns nothing — `SourceFilter.PinnedOnly` was the default

`SearchViewModel.State` (in `SearchViewModel.kt`) declared:

```kotlin
val sourceFilter: SourceFilter = SourceFilter.PinnedOnly
```

`GlobalSearchViewModel.getEnabledSources()` honours that filter:

```kotlin
override fun getEnabledSources(): List<CatalogueSource> {
    return super.getEnabledSources()
        .filter { state.value.sourceFilter != SourceFilter.PinnedOnly || it.id in pinnedSourceIds }
}
```

`getSelectedSources()` (which the fan-out `search()` iterates) is built from
`getEnabledSources()`. So with the default filter, **only pinned sources are ever queried**.
A user who has not pinned any sources — or whose pinned sources return nothing for the query —
gets an empty result set on every search. That is the defect, and it fully explains (1).

The `UnifiedSearchTab` (the actual Discover search page) never sets the filter, so it was
stuck on `PinnedOnly` with **no visible control to change it**: the pinned/all filter chips
only live in the standalone `GlobalSearchToolbar` (the deep-link `Screen.GlobalSearch` route),
not in the tab chrome. The user is stranded on pinned-only with no affordance to widen the
search.

### 2.2 Suggestion chips return nothing — same engine

The one-tap chip row (`GlobalSearchSuggestions`) is fed by the
`GlobalSearchViewModel.suggestions` flow (recent queries + fuzzy-matched library titles — i.e.
the user's "installed material"). Its `onSuggestionClick` simply runs
`UpdateSearchQuery(query)` + `Search`. Because the engine defaulted to pinned-only, every chip
tap hit the same empty path. So (2) is a direct symptom of the defect in 2.1.

### 2.3 Search entry is out of the way — hidden behind an icon

`TabbedScreen` owns one shared search field in the top app bar. `SearchToolbar` only renders
the text field when `searchQuery != null`; otherwise it shows the page title ("Discover") with
a **small search icon** in the top-right actions. To start searching the user must first tap
that icon to enter search mode, then type, then submit via the IME action. There is no
always-visible search affordance on the tab — hence "out of the way" (3).

### 2.4 "Whole page make over"

The page has no empty state: a query that yields nothing renders the source rows as empty
cards (or, with the pinned-only bug, nothing at all) with no friendly message. The sectioning
described in `phase2-search-v1-spec.md` (Library section streaming local matches as-you-type,
Sources section streaming per-source results with per-source status rows) has not been built
into the tab-hosted `UnifiedSearchTab` — results are still rendered purely per-source.

## 3. Already implemented (this session, `cline/tj9m33yf`)

These directly resolve (1), (2), and the worst of (3):

| # | Change | File(s) |
|---|--------|---------|
| A | **Default `sourceFilter` is now `All`.** Search fans out across every enabled source by default; `PinnedOnly` remains available as an explicit opt-in (still reachable via the filter chips in the standalone route, and via the `SetSourceFilter` event). | `SearchViewModel.kt` |
| A | **Test updated** to assert the `All` default and to toggle both ways. | `GlobalSearchViewModelTest.kt` |
| B | **Always-visible search field on the Discover Search tab.** Added `TabContent.alwaysShowSearch`; `TabbedScreen` renders the field (empty, with placeholder) up-front instead of waiting for an icon tap. The Sources tab is unchanged (keeps icon-first, local-filter behaviour). | `TabbedScreen.kt`, `UnifiedSearchTab.kt` |
| C | **Empty state.** `GlobalSearchContent` now shows `EmptyScreen("No results found")` when a submitted query has no successful non-empty results, is not still loading, and has no library suggestions to fall back on. `searchQuery` is threaded through all three call sites. | `GlobalSearchScreen.kt`, `MigrateSearchScreen.kt`, `UnifiedSearchTab.kt` |
| C | **UI tests** for the empty state (shown on no results; suppressed during loading / when suggestions exist). | `GlobalSearchComponentsUiTest.kt` |

Net effect: open Discover → the search bar is already there and focused → type → Enter →
results stream in from all enabled sources. Tap a library/suggestion chip → it searches across
all sources and returns hits. A query with genuinely no hits shows "No results found" instead
of a silent blank page.

## 4. Phased backlog — the rest of the make over

### 4.1 — Make the source filter visible & switchable on the tab (next)

Today the pinned/all choice is invisible on the Discover tab (only on the standalone route).
Now that the default is `All`, surface a small segmented control / filter chip group inside the
search page content (above the suggestions row) so users can narrow to **Pinned** intentionally.

- Add `sourceFilter` + `onChangeSearchFilter` params to `GlobalSearchContent` (nullable,
  default `null`), and render a `SourceFilterChips` row only when they are supplied.
- `UnifiedSearchTab` passes `state.sourceFilter` and a `SetSourceFilter` event handler.
- Extract the existing `FilterChip` block from `GlobalSearchToolbar` into a shared
  `@Composable SourceFilterChips(...)` used by both the toolbar (standalone route) and the new
  content row — one component, two placements.
- State guard: switching to `PinnedOnly` with zero pinned sources should not silently produce
  an empty page — surface a one-line hint ("No sources pinned — search all sources instead?"
  with a quick-swap chip) so the user can recover.

### 4.2 — Section the results (Library + Sources), per `phase2-search-v1-spec.md`

- **Library section first, instant.** Query the local library DB as-you-type (debounced) and
  render library matches as full rows with reading progress ("Ch 42 · 3 unread"), ahead of any
  network result. Today library titles only appear inside the suggestions chip row.
- **Sources section streams.** Render per-source result headers + inline status: a slim
  "searching N sources…" progress line, per-source success/empty/error/slow-collapse rows,
  and retry on error rows. Today a failed source shows `GlobalSearchErrorResultItem` inline but
  there is no aggregate progress or retry affordance.
- **Ordering.** Exact `TitleNormalizer.forEquality` match to top of each section; pinned
  sources first within the Sources section (preserve existing `sortComparator` intent).
- **Actionability.** Long-press a source row → add to library directly (the add-time duplicate
  check from the v1 spec), bypassing the details detour.

### 4.3 — Smart Merge banner + merged-row toggle on the tab

`SearchViewModel.State.mergedResults` already exists and is surfaced as a banner on the
standalone route (`GlobalSearchMergedBanner`). Surface the merged-duplicate count on the tab
too (banner above the per-source rows), and add a "hide merged" toggle so users who want the
raw per-source view can opt out. (See `SearchResultMergerTest` for the matching rules to keep
stable.)

### 4.4 — Matching refresh to the Sources tab (the "other discover page")

The Sources tab (`sourcesTab` → `SourcesScreen`) is the sibling Discover page. Matching
changes:

- It already has an `EmptyScreen` for no-results / no-sources (good — keep it).
- Consider applying `alwaysShowSearch = true` here too so the local-filter bar is always
  visible and consistent with the Search tab. **Risk:** the Sources tab's top bar also carries
  the "Add Source or Repo", "Global search", and "Filter" action icons; rendering the field
  alongside all three narrows the bar. Decide placement (field + overflow the three actions)
  in a design review before implementing; treat as a follow-up, not a regression.
- The Sources tab already shows an "extension updates available" assist chip
  (`ElevatedAssistChip` in `sourcesTab`). Consider unifying that chip's shape/style with the
  search page's filter chips for visual consistency.
- Keep the Sources tab's `BackHandler` (clears the local filter query) behaviour intact when
  adopting always-show-search.

### 4.5 — Migrate the Discover search engine to the target-native path

`source-api` now ships a target-native search stack
(`NativeSourceRegistry` → `GlobalSearchCoordinator` → `SearchSession`) with per-source
timeout, concurrency cap, and typed `SearchFailure` kinds (`TRANSIENT` / `PERMANENT` /
`RATE_LIMITED` / timed-out). The legacy fan-out in `SearchViewModel.search()` (manual
`async`/`withContext(Dispatchers.IO.limitedParallelism(5))`) predates that and lacks
per-source timeout cancellation. A later phase should drive the Discover Search tab from
`GlobalSearchCoordinator` instead of `UnifiedSearchEngine`, converting
`SourceContentItem`/`MergedSourceItem` into the existing `Manga`-based rows at the UI
boundary. (Out of scope for this change set — large, needs the local-source gateway wired
first; tracked in `doc/SOURCE_DISCOVERY_ARCHITECTURE.md`.)

## 5. Verification

- `GlobalSearchViewModelTest` — asserts `All` default and toggle (JVM, no Android deps).
- `GlobalSearchComponentsUiTest` — Robolectric Compose tests for the empty state.
- No Android SDK / Gradle toolchain is available in this sandbox, so changes were verified by
  static review against the exact call sites and signatures in `AppBar.kt` / `SearchToolbar` /
  `TabbedScreen.kt` / `GlobalSearchContent`. Gate on CI before release.

## 6. Risk register

- **Always-visible field auto-opens the soft keyboard** on tab entry because
  `SearchToolbar.showSoftKeyboard` keys off `searchQuery.isEmpty()`. Acceptable for a
  search-centric tab; if it proves too aggressive, gate `showSoftKeyboard` on a new `autoFocus`
  param rather than reverting the always-visible behaviour.
- **Back-arrow now clears the field** on the Search tab (previously there was no up button).
  This matches standard "search bar in an app bar" UX; the Sources tab is unaffected.
