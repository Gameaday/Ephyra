# Phase 4 & 6 — Cache Audit Findings

> **Status:** HISTORICAL - completed improvement-session record. Retained for archaeology only;
> not current guidance. Current forward plan: [`../2_0_COMPLETION_PLAN.md`](../2_0_COMPLETION_PLAN.md).

## Cover cache (durable store, `CoverCache` + Coil `MangaCoverFetcher`)

Already correct:
- **One slot per cover, everywhere.** Keys are `hash(thumbnailUrl + coverRevision)`,
  so the same cover in Library, Updates, History, and Browse resolves to one durable
  file regardless of which screen asked. The "same cover, one cache spot" requirement
  holds for identical URLs (cross-source duplicates with *different* URLs are
  different bytes — properly a Works problem, RFC-0001).
- **LRU is real**: durable hits `touch()` the file; pruning is age + 256MB LRU.
- **Library protection**: maintenance computes protected filenames from the current
  library before pruning.
- **Custom covers** live in a separate directory, never remote-pruned.
- Shared-element covers skip Coil crossfade (Phase 1 fix) — the biggest visible
  pop-in source.

Gaps / candidates (not yet done):
- **Revision orphans**: a cover refresh (new `coverLastModified`) writes a new file;
  the old revision lingers until age-prune. Candidate: delete the prior revision file
  eagerly on successful write of a new one.
- **No prefetch**: lists don't prefetch covers just outside the viewport; on fast
  scroll the first durable-read still costs a frame or two. Candidate: Coil prefetch
  of visible±N on Library/Updates grids (pairs with Phase 5 perf work).
- Placeholder is a solid color with fixed aspect ratio — layout pop-in is avoided;
  *image* pop-in on cold memory cache is the residual, which prefetch addresses.

## Chapter cache (`ChapterCache`)

Already correct:
- DiskLruCache with device-tier sizing (100/256/512 MB) — self-bounding.
- Page-list format versioning: stale-format lists are discarded by key, not read.
- Manual clear in Settings → Data.

Gaps:
- **No per-manga eviction on library removal.** Entries for a removed series orphan
  and age out via LRU only. Candidate: evict by chapter keys on unfavorite/delete
  (needs the manga's chapter list at removal time).
- No source-change invalidation (chapter URLs are source-scoped by key, so a source
  change can't *misread* cache — it only orphans; same LRU resolution).

## Done this pass
- **Cold-start audit (Phase 6 core ask)**: `CoverCacheMaintenanceWorker.enqueueOneTimeAudit`
  runs once per process launch (KEEP policy) with the same library-protection logic as
  the weekly task — guarantees the audit happens even after crashes/killed schedules.
  Commit `a54d42d`.
- **Removed dead `ChapterCache` injection from MainActivity** — Hilt was constructing
  it (and its DiskLruCache journal) at every activity start for no use. `a54d42d`.

## Done since
- Reader sliding-window eviction + exit-keeps-one (`ee11b21`, `83e8ef6`).
- Library cover prefetch of first screenfuls (`c2caf52`).
- Per-manga chapter eviction on library removal, both single (series page) and bulk
  (library delete) paths (`b88c25a`).
- CI green on PR #285 after fix rounds (ktlint/spotless/interface/test doubles).

## Remaining queue
1. Eager prior-revision delete on cover refresh.
2. Migration-source-change chapter-cache orphaning (keys embed source URLs; LRU
   handles today; candidate for the same interactor at migration time).
3. Device validation: reading-window feel, prefetch cost on first library load.
