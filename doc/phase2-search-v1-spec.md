# Phase 2.1 — Unified Search v1 Spec

Owner call: **no dedup in v1** (not even SearchResultMerger grouping). v1 wins on
speed, clarity, and actionability — refinement, not takeaway. Two sections:
Library, Sources. One field. No authority UI (D13).

## What makes v1 feel like an upgrade

### 1. Speed hierarchy the user can feel
- **Library results are instant** — local DB query, renders as-you-type before any
  network returns. The search feels fast even on a cold connection because the first
  section is already populated.
- Sources section streams in per-source as each responds (no waiting for the slowest
  source before showing anything), with a thin "searching N sources…" progress line
  that resolves to per-source completion.

### 2. Clarity instead of dedup (duplicates become information)
- Every source row carries: cover, title, **source name chip**, latest chapter when
  known.
- Rows are **ordered so duplicates cluster**: exact normalized-title matches sort
  first and adjacent. Without merging anything, "the same title on 3 sources" reads
  as availability the user can choose between — the choice D2 says belongs to them
  anyway — instead of noise.
- Library rows show reading progress ("Ch 42 · 3 unread"), so the top section is
  immediately actionable, not just a filter.

### 3. Actionable rows (D1: search must link to content)
- Tap library row → reader/details as today.
- Tap source row → details for that source's copy (existing flow).
- **Long-press → add to library** directly from the row, no details detour.

### 4. Reliability you can see (D5 etiquette, made visible)
- Debounced (~300ms), every keystroke cancels in-flight requests (`flatMapLatest`).
- Per-source timeout; a failed/slow source collapses to a small inline row
  ("SourceName: failed — retry") instead of poisoning the list. Partial results are
  the norm, all-or-nothing errors are gone.
- No speculative calls: nothing fires until ≥2 characters, nothing prefetches details.

### 5. Small sharp edges
- Recent searches (exists) shown on empty query, one-tap re-run, clearable.
- Query survives tab switches and process death (saved state).
- Content-type filter chips retained on the field (Q3, cheap).
- Availability-first toggle (Stage B) ships *in* v1 since it is pure presentation
  over fan-out data — default: installed+enabled sources only.

## Add-time duplicate check (owner addition)

Dedup is banned from *results*, not from *adds*. On add-to-library (tap or
long-press), check for existing entries of the same work from other sources using the
existing normalizers (`TitleNormalizer.forEquality` exact first, fuzzy as secondary
suggestion only). Outcomes: **confirm** (attach/merge path per existing behavior) or
**add anyway** (bypass — user may want both editions). This is deliberately the test
bed for the comparison/matching rules RFC-0001 will depend on: every confirm/bypass
is evidence about whether our confidence gates are calibrated, and it exercises the
matching code where a wrong answer is visible and reversible instead of silent.

## Explicit non-goals for v1
- No merging/dedup of any kind in search results (add-time check above excepted).
- No authority/tracker UI.
- No quality scoring, no availability badges beyond "which source row exists".
- No pairing/attach logic.

## Composition (Stage A build notes)
- One screen + one ViewModel owning the field, debounce, cancellation, section state.
  Library section: local query interactor. Sources section: existing global-search
  fan-out engine, minus `SearchResultMerger`, plus ordering + per-source status.
- Both old destinations (authority tab page, `Screen.GlobalSearch`) redirect here.
- Ordering rule: exact `TitleNormalizer.forEquality` title match → top of section,
  stable otherwise; library section always first.
