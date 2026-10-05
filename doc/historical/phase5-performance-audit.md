# Phase 5 — Performance & Jank Audit (S24 / 120Hz context)

> **Status:** HISTORICAL - completed improvement-session record. Retained for archaeology only;
> not current guidance. Current forward plan: [`../2_0_COMPLETION_PLAN.md`](../2_0_COMPLETION_PLAN.md).

Frame budget at 120Hz adaptive: ~8ms. Findings and fixes, landed separately.

## Fixed (this pass, CI-verified)

1. **`MangaCover` rebuilt its Coil `ImageRequest` on every recomposition** — every
   visible cover, every state change, handed Coil a new model instance (re-running
   request setup, risking fetch restarts). Now `remember(data, isSharedElement)`.
   Highest-leverage fix in the pass: this code runs in every grid in the app.
2. **History item keyless `remember`** — recycled list slots showed the previous
   item's timestamp (correctness bug masquerading as stale UI). Now keyed.
3. **Coil memory cache was one flat default** — now tiered by device RAM
   (LOW 20% / MEDIUM 25% / HIGH 30% of heap). On S24-class devices the decoded-bitmap
   working set survives scrolls, so grids recompose from memory instead of
   re-decoding from disk — the actual 120Hz jitter source. Chapter/disk caches were
   already tiered; this completes the pattern.
4. (Earlier in the arc, same goal:) dead `ChapterCache` injection removed from
   MainActivity startup; library cover prefetch; reader window eviction reducing
   cache pressure and IO.

## Verified healthy (no change needed)

- Lazy grids/lists use stable keys (`libraryManga.manga.id`).
- ViewModels emit `persistentList`/`toPersistentList` — list identity stable, no
  recomposition storms from list churn.
- History/updates item composables already `remember` their expensive values.
- High-refresh: only the reader forces it (`applyHighRefreshRate`); the rest of the
  app defers to the system LTPO policy — correct, forcing is what fights adaptive
  refresh and wastes battery.
- Disk cache + chapter cache tier sizing already in place.

## Remaining — needs the device (owner)

1. **Regenerate `baseline-prof.txt` on the S24.** Current committed profile is 84
   lines (stale, nearly empty) and predates the nav/motion/cover changes. The
   generator journey (startup → library scroll → open item) already exists in
   `macrobenchmark`; run: `./gradlew :app:generateReleaseBaselineProfile`.
2. **Run `ScrollBenchmark` + `StartupBenchmark`** pre/post this branch; capture
   frame-time P50/P90/P99 for library scroll at 120Hz.
3. If P99 still misses: recomposition tracing on `LibraryComfortableGrid` item
   content (badge row is the next suspect) and `UpdatesUiItem`.

## Not done (deliberately)

- No speculative prefetch beyond the bounded first-screenful batch (API politeness).
- No display-mode forcing outside the reader.
- No allocation micro-tuning without benchmark evidence — measure first (item 2).
