# Phase 2 — Discover Tab Simplification: Implementation Plan

Decisions: RFC-0001 D1–D9. Current state: `BrowseTabScreen` is a 4-page pager
(Discover, Sources, Extensions, Migrate) with per-tab hoisted ViewModels and a search
bar that changes meaning per tab.

## Target structure

**Discover tab (bottom nav) = two pages:**
1. **Search** (default) — the one authoritative search (D1). Grouped results:
   Library → Sources → Jellyfin (when Phase 3 lands). Query fans out debounced with
   cancellation; per-source partial results + inline error rows (D4); no speculative
   requests (D5).
2. **Sources** — enabled sources list → tap into source catalog browsing (existing
   `BrowseSource` flow). Sources keep their own filter/search *inside* a catalog only.

**Moved out:**
- **Extensions** → Settings (management is settings-shaped, D8). Discover shows an
  "extension updates available" chip when `ExtensionsViewModel` reports updates;
  tapping deep-links to the settings extensions screen.
- **Migrate source** → Settings (already exists as `feature/migration`; link from
  settings screen).

## Steps (discrete commits)

1. Remove Extensions + Migrate pages from `BrowseTabScreen` pager; drop hoisted
   ViewModels and per-tab query routing (the search bar then has exactly one meaning).
2. Add Settings entries: "Extensions" and "Source migration" (reuse existing screens —
   they are nav-reachable composables; check `BrowseFeatureApi` registrations so routes
   stay alive after the tab removal).
3. Add updates-available chip on the Discover/search page (data already computed by
   `ExtensionsViewModel`; expose a lightweight count flow rather than hoisting the VM).
4. Rebuild the Discover page around the unified search field (single `SearchTextField`
   pinned at top, not per-tab). v1 groups: reuse `GlobalSearchViewModel` +
   `SearchResultMerger` for the sources section; add a "Library" section querying the
   local DB; Jellyfin section arrives with Phase 3.
5. Search etiquette layer (D5): shared debounce (~300ms) + `flatMapLatest` cancellation
   + per-source timeout and error capture. One component owns this so Phase 3 just
   registers another provider.
6. Structural test updates: tab count, chip visibility rules, single-search-field
   invariant.

## Splash damage (accepted per owner)
- Settings screen gains two entries.
- `BrowseFeatureApi` route registrations move/stay consistent.
- Any deep links into `Browse` tab page indices (e.g. intents opening the extensions
  page) must be redirected — audit `handleIntentAction` in MainActivity.
- The hoisted-ViewModel pattern in `BrowseTabScreen` goes away; `ExtensionsViewModel`
  must not be created on Discover anymore (it does network work on init).

## Non-goals for Phase 2
- Merged ranked results (needs work identity — RFC-0001).
- Jellyfin section (Phase 3).
- Extension management UX redesign (just relocated).

## Progress log

- [x] Step 1 — Extensions + Migrate removed from the Discover pager; hoisted
  `ExtensionsViewModel` (did repo network work on every Discover visit) and the
  `switchToExtensionTabChannel` page-scroll hack deleted. `0157ad6`
- [x] Step 2 — Standalone routes `extensions` + `source_migration` hosting the exact
  same content, registered in `BrowseFeatureApi`; Settings gained both entries.
  `11369eb`, `1ba0b43`
- [ ] Step 3 — Updates-available chip: pending. Note: must NOT instantiate
  `ExtensionsViewModel` (network-on-init); needs a lightweight updates-count flow.
- [ ] Step 4 — Unified search rebuild. **Key finding while in the code:** the Discover
  page today is `AuthoritySearchScreen` (tracker/authority search) while source fan-out
  search is a *separate* `GlobalSearchScreen` — exactly the "two discovery searches"
  problem D1 rejects. Step 4 must merge these into one field with grouped sections
  (Library / Authority / Sources), not just restyle one of them.
- [ ] Steps 5–6 pending.

- [x] Step 4 (revised per v1 spec + D13) — **Stage A landed** (`df25f94`): the
  Discover tab's search page is now the unified global-search fan-out hosted in the
  tab chrome (`UnifiedSearchTab`), replacing the authority search page. One toolbar
  field; submit-only fan-out (D5). Authority code preserved but no longer reachable
  from the tab.

Follow-ups still open:
- Library section as full rows with reading progress (currently library matches
  appear via the suggestions row only).
- `Screen.GlobalSearch` standalone route still exists for deep links (manga details
  "search globally"); keep for now, but it shares the engine so no second front door
  is exposed in UI chrome.
- Add-time duplicate check (D14) not yet wired.
- Steps 3 (update chip), 5 (etiquette hardening: debounce, per-source timeout surfacing),
  6 (structural tests) pending.

- [x] Authority retraction cleanup (D13) — `13f93b9`:
  - Series page: manual Identify/Refresh-from-authority action removed from
    EditMetadataDialog (`onIdentify = null`); authority badge + Unlink kept (unlink is
    the reversible escape hatch for existing/future links).
  - Dead `match_results` route unregistered (nothing navigated to it post-retraction).
  - Settings → Tracking: authority group (tracker ordering, match-unlinked job,
    import/link) gated off with a salvage note; the manual MatchUnlinkedJob is
    consequently unreachable — silent enrichment replaces it.
  - Remaining authority mentions are intentionally retained: MangaInfoHeader badge for
    already-linked entries, LibraryBadges brand colors, background chapter generation.

- [x] D14 add-time duplicate check (`c575253`): the series page already had a
  confirm-or-bypass duplicate dialog; its matching now also runs an exact
  normalized-title pass (`TitleNormalizer.forEquality`) so punctuation/spacing
  variants across sources collide. Fuzzy deliberately excluded from the blocking
  prompt (suggestion-grade only).
