# Cache Retention Policy (Phases 4/5/6 consolidation)

Verdict on "does it need a redesign": **no.** The bones are good (durable URL-keyed
cover store with LRU touch, tier-sized self-bounding chapter DiskCache, format
versioning, library protection). What was missing is a *stated policy* tying retention
to user context. This doc is that policy; implementations reference it.

## Tiers

| Tier | Contents | Retention |
|---|---|---|
| **Hot** | Library covers, covers visible±N in front-facing grids, current chapter's pages | Keep hot: protected from pruning, prefetched, never evicted by context rules |
| **Warm** | Browse/search/history/updates covers | LRU under byte budget (existing behavior) + cold-start audit |
| **Cold** | Chapter page lists & page images | Aggressive context eviction: a chapter's pages leave cache once the reader has moved more than 2 chapters away (finished or skipped). LRU remains the backstop |

Rationale: covers are small, displayed constantly, and expensive to re-fetch visually
(pop-in); chapter pages are large, displayed once sequentially, and re-fetchable in
the rare backward jump. Retention follows revisit probability, not arrival order.

## Rules

1. **Sliding reading window.** The reader keeps the current chapter ±2 hot. Anything
   that falls out of the window is evicted (page list + its images). Backward jumps
   within the window are free; beyond it cost one re-fetch — the right trade.
   **Reader exit keeps one.** The window only slides within a series and each series
   has its own reader ViewModel, so on `onCleared` (genuine exit, not rotation) every
   windowed chapter except the current one is decached: reading the latest chapter
   of N series leaves N chapters cached (for instant resume), not N × window.
2. **Front-facing prefetch.** Library/Updates grids prefetch covers for visible±N
   items once scrolling *settles* (no prefetch storm mid-fling). Coil dedupes in-flight
   requests; durable hits make prefetches cheap no-ops.
3. **API politeness.** Prefetch is capped (small N), settle-debounced, and never fires
   for items already in the durable store. Chapter eviction makes no network calls.
   Nothing here adds speculative remote traffic.
4. **Explicit lifecycle events still win.** Unfavorite/delete → cover delete (exists);
   per-manga chapter eviction on removal stays on the queue (needs chapter list at
   removal time); manual clear stays in Settings.
5. **Budgets stay tier-sized by device RAM** (existing DeviceUtil tiers).

## Validation plan (objective check)

- Unit: `ChapterCache.removeChapter` removes list + images, leaves others intact;
  window logic keeps ±2, evicts the 3-away chapter; reader exit keeps only the
  current chapter.
- Manual: read 5 chapters forward, confirm cache size drops after each window slide;
  re-open previous chapter inside window = instant; outside = one re-fetch.
- Size invariants: chapter cache ≤ tier cap; cover cache ≤ 256MB after audit.
