# Search Systems Audit

> **Status:** HISTORICAL - completed improvement-session record. Retained for archaeology only;
> not current guidance. Current forward plan: [`../2_0_COMPLETION_PLAN.md`](../2_0_COMPLETION_PLAN.md).

Status: inventory complete; one divergence fixed in this pass (the debounce value), the rest is a
proposed convergence plan. Scope: every search/filter surface in the app, whether it filters in
memory, queries a source, or resolves to a navigation target.

Every surface below was verified by reading the cited code.

---

## Inventory

| # | Surface | Entry / UI | State owner | Debounce | Query grammar | Result model |
|---|---|---|---|---|---|---|
| 1 | Library | `LibraryToolbar.kt:76` (`SearchToolbar`) | `LibraryViewModel` (`searchQuery`) | `SEARCH_DEBOUNCE_MILLIS` (`LibraryViewModel.kt:164`) | `null` = off | In-memory filter over the library |
| 2 | Global search | `GlobalSearchToolbar` -> `SearchToolbar` | `GlobalSearchViewModel` + `SearchViewModel` | operator default (`SearchFlowExtensions.kt`) | `null` = off | Merged per-source results (`SearchResultMerger`), `GlobalSearchCache`, `RecentSearches` |
| 3 | Source browse | `BrowseSourceToolbar.kt:47` | `BrowseSourceViewModel.search(query, filters)` | none (explicit submit) | blank = clear | Remote source results + filter list |
| 4 | Source list | `SourcesTab` | `SourcesViewModel` | `SEARCH_DEBOUNCE_MILLIS` (`SourcesViewModel.kt:46`) | `isNullOrBlank` = all | In-memory filter over sources |
| 5 | Extensions | `ExtensionsScreen` | `ExtensionsViewModel.search` | none (filter in `remember`) | `isNullOrBlank` = all | In-memory filter over extensions |
| 6 | History | `HistoryScreen.kt:65` | `HistoryViewModel` | `searchResults(debounce = 0L)` | `""` = all | DB query results |
| 7 | Migration list | `MigrationListScreen` | `MigrationListViewModel` | n/a | n/a | In-memory filter |
| 8 | Migrate search | `MigrateSearchScreen` (reuses `GlobalSearchToolbar`) | `MigrateSearchViewModel` (extends `SearchViewModel`) | operator default | `null` = off | Source search results for migration |
| 9 | Settings | `SettingsSearchScreen` | `SearchableSettings.highlightKey` (global mutable) | n/a | n/a | Navigation to a screen + scroll-to/highlight a preference |
| 10 | Authority search | `AuthoritySearchScreen` | `AuthoritySearchViewModel.search(query)` | none | blank = clear | Remote source results |
---

## Divergences found

### D1. Two debounce values for the same interaction (fixed)

`AppBar.kt` declared `SEARCH_DEBOUNCE_MILLIS = 250L` while the shared operator
(`SearchFlowExtensions.kt`) defaulted to `300L`. Library and Sources used the app-bar constant;
everything routed through the operator used 300ms, so the same typing speed produced results at
different moments depending on the screen. Fixed: both names now resolve to one constant
(`SEARCH_DEBOUNCE_MILLIS = 300L`), and the old name is deprecated rather than deleted.

### D2. Two meanings of "no query": `null` and `""`

Surfaces 1, 2 and 8 use `null` to mean *search mode is off* (normal toolbar, no results pane) and a
string — including `""` — to mean *search mode is on*. Surfaces 3, 4, 5, 6 and 10 treat blank as
"show everything" and have no off state. The user-visible consequence is inconsistent clearing: on
the library, clearing the field leaves the field open with everything listed; on the source list,
clearing closes nothing but silently shows all sources. A single grammar is needed, not two.

### D3. Three query-normalisation rules

`GlobalSearchCache.kt:51` keys on `query.trim().lowercase()`; `RecentSearches.kt:33` trims but does
not lowercase; the in-memory filters (4, 5, 7) call `contains(query, ignoreCase = true)` with no trim
at all. So "  one piece " behaves differently in each place, and a trailing space changes whether the
recent-search list records a duplicate.

### D4. Two query-state lifetimes

Surfaces 1-8 and 10 keep the query in their own ViewModel (survives rotation and tab switches).
Surface 9 keeps it in a process-global mutable variable (`SearchableSettings.highlightKey`), which is
cleared by `PreferenceScreen` after use (`PreferenceScreen.kt:40`) — so it is not restored on rotation
and can leak into an unrelated settings screen if a highlight is set and never consumed.

### D5. Same toolbar, different contract

`GlobalSearchToolbar` is used by both surface 2 (global search across enabled sources) and surface 8
(migration search). They look identical and behave differently, because migration only searches
sources the migration can use. That is a fair distinction, but it currently has no visual affordance,
so a user who searched in one and then the other sees "the search is broken".

### D6. No shared result contract

Each surface defines its own result list type and its own empty/loading/error rendering. There is no
one place that answers "what is a search result, what is a search error, and when is a query too short
to send".

---

## Proposed convergence

Ordered so each step is shippable on its own:

1. **One query type.** Introduce `SearchQuery(val text: String)` with a single `normalize()` (trim;
   lowercase only for cache keys) and one documented grammar: `SearchQuery.off` vs `SearchQuery("")`.
   Migrate surfaces 1, 2, 8 off `String?` first — they already have an off state and are the least
   invasive.
2. **One debounce token.** Done for the constant; next, route surfaces 4 and 5 through
   `Flow<String?>.searchResults` so local filters use the same operator as DB/network search.
3. **One result contract.** A `SearchState<T>` (idle / searching / results / error / empty) rendered
   by one shared composable, so empty states and errors stop being re-authored per screen.
4. **Move the settings search off the global variable** into the settings ViewModel (survives
   rotation, cannot leak between screens).
5. **Signal the scope of a search** in the toolbar (e.g. "All sources" vs "Migration sources"), which
   resolves D5 without merging the two code paths.

## Verification

- A unit test per migrated surface asserting the normalisation rule (the same input string produces
  the same normalized key).
- A UI test that clears the field on surfaces 1 and 4 and asserts the same visible outcome.