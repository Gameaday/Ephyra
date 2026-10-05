# Phase 2.1 — Search Experience: Problem Analysis & Redesign

> **Status:** HISTORICAL - completed improvement-session record. Retained for archaeology only;
> not current guidance. Current forward plan: [`../2_0_COMPLETION_PLAN.md`](../2_0_COMPLETION_PLAN.md).

Not a merge of two screens — a redesign from the problem up. Supersedes the "just
combine them" framing in phase2-discover-plan.md step 4.

## What each search was actually for

**Authority search** (tracker-backed: AniList/MAL/etc.) solves *identity and metadata
quality*. Source catalogs have inconsistent titles, missing authors, wrong covers.
The authority gives: a canonical id (`anilist:123`), clean metadata, alternative
titles, and — via `FindContentSource` — an answer to "which of my installed sources
can actually serve this?" Its add flow dedupes against the library and can merge
metadata into an existing entry. **This is a proto-implementation of RFC-0001's
Work + Binding**: the authority result is the work; `FindContentSource` is binding
resolution done manually, late, and by prompting the user.

**Global search** solves *availability*. It fans out to every source and returns
merged-by-title rows. It knows nothing about identity beyond fuzzy titles, so the
same work appears per-source with divergent metadata, and tapping a row commits you
to that source's copy.

**Library search** (in the Library tab) solves *recall of what I already have*.

Three searches = the user manually doing the join that identity should do.

## The friction worth killing (authority search specifically)

- Tracker picker as a mandatory first decision — the user must know which database
  to ask before asking.
- Logged-in-tracker gating hides the feature entirely for many users.
- The add flow is a three-prompt gauntlet: merge prompt → source prompt → done.
- `FindContentSource` runs *after* the user commits — availability should be visible
  *before*, on the result.
- It lives as a separate screen from global search, so "search for content" has two
  front doors (the D1 violation).

## The experience to deliver (v1, grouped per D4)

One field. One result list, grouped:

1. **In your library** — local matches first (free, instant, offline).
2. **Matches** — authority-backed works when reachable (one row per work, canonical
   metadata, "on N sources" availability badge computed from already-installed
   sources); plain source results when authority is unreachable/unused.
3. **From sources** — raw source fan-out rows, merged by `SearchResultMerger`, for
   long-tail content no authority knows (doujin, web originals, regional stuff).

Behavior principles:
- **Authority is an enhancer, not a gate.** No tracker configured → section silently
  degrades to source results. No picker: query all usable authorities in priority
  order, first confident hit wins (same politeness budget as D5).
- **Availability before commitment.** Each work row shows which sources have it
  (reuse `FindContentSource` logic, but lazily per visible row, not for the whole
  list — D5 politeness).
- **One tap adds the work**; source selection is automatic (healthiest) with the
  picker in details (D2). Merge prompt survives *only* for the fuzzy-match case (D3);
  canonical-id hits add silently.
- Sources section rows behave as today (open details for that source) but adding a
  source row that matches an authority work attaches it as a binding, not a new work.

## Layers to *remove* (answering "what can go")

| Candidate | Verdict |
|---|---|
| Separate `GlobalSearchScreen` route | Remove as a destination; its fan-out engine becomes the Sources section's data source. |
| Tracker picker UI in search | Remove; authority choice becomes automatic with a settings override. |
| Merge prompt | Keep, narrowed to D3 fuzzy cases only. |
| "Find content source?" post-add prompt | Remove; availability is pre-computed on the row. |
| `MatchResultsScreen` (527 lines) | Absorb into the row-level availability badge + details picker. |
| Library tab's local search | Stays (different job: filtering your stuff), but unified search also surfaces library hits, making it redundant for find-and-open. Revisit after v1. |

## What this needs from RFC-0001

v1 can ship *without* the work/binding schema: the grouped list + lazy availability
badges + silent canonical-id dedupe are all achievable on current tables (canonical
ids already exist on rows; `TitleNormalizer.canonicalKey` now exists). But the merged
ranked list (D4's endgame) and cross-provider progress (O2) require the schema. So:
**v1 = this doc on current schema; v2 = RFC-0001 schema absorbs it.**

## Open design questions (for owner)

- Q1. Authority auto-priority when multiple trackers are usable: fixed built-in order
  (AniList → MAL → …) or user-orderable in settings? (Leaning: built-in v1.)
- Q2. Should the Sources section appear at all when an authority hit exists and is on
  ≥1 source? (Leaning: collapsed behind "more results".)
- Q3. Content-type filter chips (manga/novel/comic): keep on the unified field or
  drop until v2? (Leaning: keep, they already exist and are cheap.)

## Owner refinements (brainstorm round 2)

- **R1. The authority's core value is dedup, not metadata.** "One title, available in
  a few places" is the experience. Metadata quality is secondary to that.
- **R2. Metadata authority and content authority are different things.** The best
  cover/description may come from the tracker, while the best *reading* source is
  chosen independently — by repository quality: most thorough coverage, fewest chapter
  gaps, highest/latest chapter number without gaps. A work binds to the best-quality
  source for content while keeping authority metadata. Quality is a property of the
  binding's unit list, computable: coverage ratio, gap count, latest unit number,
  update recency. This becomes the binding-preference score in RFC-0001 (D2's
  "auto = healthiest" made concrete).
- **R3. Paired-add must be one action in both directions.** Today: authority-saved
  entries need a separate step to attach a content source; source-saved entries need a
  separate step to attach authority metadata. The add action must always try to
  complete the pair: adding a work attaches the best source automatically; adding from
  a source attaches the authority identity when one matches (D3 confidence rules).
- **R4. Availability-first results.** Default: only show titles the user can actually
  read (in library or on an installed source). Unavailable titles (authority hits with
  no installed source) are hidden behind an explicit "include unavailable" toggle,
  persisted per session at most — searching should answer "what can I read", with
  discovery-of-the-unavailable as an opt-in.

### Design impact

- The Matches section badge ("on N sources") is now load-bearing, not decorative:
  with R4 it is the *filter*, and with R2 it feeds the binding-preference score.
- Binding-preference scoring needs a `SourceQuality` computation (coverage, gaps,
  latest unit, recency) — new domain component, unit-testable, fed by chapter/unit
  lists we already fetch for details. Cheap enough per candidate source; must stay
  lazy (D5).
- R3 means `FindContentSource` stops being a post-add prompt entirely and becomes a
  silent part of add (already the v1 direction; now it is mandatory, not optional).

### New open questions

- Q4. R4 default for titles on sources that are installed but disabled?
  (Leaning: treated as unavailable, with a hint row "N results on disabled sources".)
- Q5. Quality-score ties or near-ties: auto-pick silently or surface the picker?
  (Leaning: silent within a threshold; picker always available in details per D2.)
- Q6. Does "include unavailable" deserve a persistent setting or session-only?
  (Leaning: session-only toggle; persistent risks a permanently noisy result list.)
