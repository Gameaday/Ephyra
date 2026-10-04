# Improvement Session Plan

Branch: `cline/0crpxa7t`. Each phase ships independently; commit per milestone.

## Phase 1 — Navigation transitions & back animations
- Audit current nav host (feature screens → `presentation-core`) transitions: default vs per-destination.
- Adopt predictive back (Android 14+) where supported; unify enter/exit/popEnter/popExit specs.
- Add shared-axis/slide transitions for browse↔details, fade for tabs; respect reduced-motion setting.
- Acceptance: no flash-of-blank on back; consistent motion language; 60fps on S24 (profile with macrobenchmark).

## Phase 2 — Discover tab simplification
- Reduce top-level tabs/rows; consolidate into: Search bar + curated feeds.
- Flatten navigation depth (discover → source browse → details in ≤2 taps).
- Acceptance: fewer destinations in back stack from Discover; scroll jitter-free.

### Phase 2.1 — Unified singular content search (brainstorm → RFC)
- Goal: one search box querying library, history, local sources, remote sources, and (Phase 3) Jellyfin; grouped/merged ranked results.
- Open questions to brainstorm: per-source fan-out vs orchestrator (`ContentSourceEngine`) federation; result dedupe across sources; ranking (local-first?); debounce/cancellation policy; empty/error-per-source UX.
- Deliverable: short RFC in `doc/rfcs/` before implementation.

## Phase 3 — Jellyfin as a content source
- Implement `ContentAdapter`/`SourceProfile` for Jellyfin (auth, libraries, items, chapters/progress).
- Wire into Browse (as a source) and unified search (Phase 2.1); cover art via Jellyfin image API into shared cover cache.
- Tests: unit (DTO mapping, naming via `JellyfinNamingTest`), conformance via `ContentConformanceTest`, manual against a live server.
- Acceptance: browse, open, read/play, progress sync; results appear in search.

## Phase 4 — Cache & cover cache audit
- Key insight: same content must map to ONE cache key everywhere (updates, history, library, browse). Canonical key = stable content ID, not per-screen URL variant.
- Preload covers for visible±N items; placeholder with exact aspect ratio to kill layout pop-in.
- Audit Coil/memory+disk cache sizes; shared `ImageRequest` builder (size hints, crossfade budget).
- Acceptance: no visible pop-in on re-entry to Updates/History; single disk entry per cover.

## Phase 5 — Performance on high-memory devices (S24) / jitter reduction
- Raise in-memory cache budgets on high-RAM devices (runtime class check); avoid recomposition churn (stable types, derivedStateOf, lazy keys).
- Eliminate main-thread IO/writes; batch DB writes; defer non-critical work to background dispatchers.
- Baseline profile update + macrobenchmark pass; fix top jank offenders.

## Phase 6 — Cache lifecycle correctness
- Chapter cache eviction rules: clear on library removal, source change, chapter re-fetch; TTL policy documented.
- Cold-start (fresh open) cache audit job: prune orphaned chapters, stale covers, oversized disk caches.
- Acceptance: cache size bounded across sessions; no stale chapter content after source changes.

## Ordering & dependencies
- Phase 2.1 RFC → informs Phase 3 search integration.
- Phase 4 key canonicalization must land before/with Phase 3 (Jellyfin covers reuse it).
- Phases 5–6 can parallelize after 1–4 land.
