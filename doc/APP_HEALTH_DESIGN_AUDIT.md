# App Health & Design Audit

> **Status:** CURRENT — audit record + staged remediation plan.
> **Audited at:** `e781388ef` (main, 2026-11-12; PR #359 head).
> **Method:** E1 source inspection, findings cited to file:line. No device evidence
> (A5/B-050); no JDK locally — remote CI is the verification channel.
> **Origin:** owner directive 2026-11-12 — start an app health & design audit,
> improving the app modularly for function, performance, design, and safety; fold in
> the cache/memory-exhaustion report and two new user reports (delayed cover
> appearance; next-chapter press doing nothing with no feedback).

## Scope

Whole app, four lenses, each finding owned by a named file and a named decision
record where one exists:

1. **Function** — user-initiated actions that fail silently.
2. **Performance** — image pipeline latency, cache-key path work, memory growth.
3. **Design** — affordances and motion that read as broken or slow.
4. **Safety** — storage scoping, silent-failure patterns, data boundaries.

## Findings

### Function

**F1 — Next-chapter press can do nothing, with no feedback (user report 2026-11-12).**

The full chain, cited:

- `feature/reader/.../ReaderScreen.kt:421` — the navigator's next control is gated by
  `enabledNext = state.viewerChapters?.nextChapter != null`.
- `feature/reader/.../components/ChapterNavigator.kt:65-68` — the navigator's
  `filledIconButtonColors` set `disabledContainerColor = backgroundColor`, the *same*
  value as the enabled `containerColor`. A disabled next button keeps its enabled
  container; only the icon's default disabled content-alpha differs. The button reads
  as pressable.
- `feature/reader/.../ReaderViewModel.kt:1142-1144` — `loadNextChapter()` begins
  `val nextChapter = state.value.viewerChapters?.nextChapter ?: return` — a silent
  return. Same shape for the viewer edge-swipe path, which calls the same handler.

So when `nextChapter` is null the press produces no observable result, which is
exactly the report. The deeper question is why `nextChapter` is null while the series
page still offers a next chapter. Four source-level causes, all in the reader's
navigation-universe construction (`getChapterList()`, `ReaderViewModel.kt:289-331`,
windowed at install by `ReaderChapterWindowAdapter.neighbours`):

- **a. `skipRead` (ReaderViewModel.kt:584-586).** The forward neighbour skips read
  chapters. Re-reading a series, or reading behind tracker-synced read marks, leaves
  no unread forward neighbour — the window ends, the series page still lists the
  (read) chapters.
- **b. `skipDupe` (ReaderViewModel.kt:305-309).** `removeDuplicates(selectedChapter)`
  may collapse the literal next chapter out of the navigation universe as a duplicate.
- **c. `downloadedOnly` (ReaderViewModel.kt:311-317).** With the global
  downloaded-only preference on, an undownloaded next chapter is filtered out.
- **d. Window staleness (ReaderViewModel.kt:290, `chapterListCache`).** The window is
  computed once per chapter install against a session-cached list; a chapter list
  that gains entries after the reader opened never extends the window.

Precedent for the fix's feedback half already exists in-repo: the History tab's
reselect path surfaces an explicit `no_next_chapter` snackbar when it cannot open a
chapter (`feature/history/.../HistoryScreen.kt`, `HistoryTabScreen` reselect
collector). The reader has no equivalent.

**Verdict:** real defect, three stacked causes (affordance, silence, universe
filters). The universe filters themselves are RDR-002 policy — changing *which*
chapters are navigable is an owner decision, not part of the fix.

### Performance

**P1 — Delayed cover appearance, especially the most-recent series cover (user
report 2026-11-12).**

The cover pipeline is healthy end to end — `MangaCoverFetcher` serves the durable
`CoverCache` file on a durable hit (`core/data/.../MangaCoverFetcher.kt`, `httpLoader`
durable-hit branch), so a repeated cover is never re-downloaded. The delay is
downstream, in decode and presentation:

- Every memory-cache miss requires a disk decode before anything can be drawn.
- The memory cache is evicted from three directions:
  `EphyraImageLoaderFactory.kt:108` `memoryCacheMaxSizePercentWhileInBackground(0.1)`
  drops the working set to 10% whenever the process backgrounds;
  `ImageCacheMemoryPolicy.trim` (`ImageCacheMemoryPolicy.kt:54-58`) halves the cache
  at `TRIM_MEMORY_RUNNING_LOW` (floor 16 MiB) — and long reader sessions pinning page
  bytes are precisely when foreground pressure trims fire; and ordinary LRU pressure
  under grid scrolling.
- `EphyraImageLoaderFactory.kt:123` applies a 300 ms (× animator scale) crossfade to
  every load that was not a memory hit.

Net effect: after a reading session, returning to History or Library re-decodes every
visible cover from disk and fades each in over ~300 ms. The row the user is looking
for — the series just read, the top of the stacked History — is the one whose cover
was most likely evicted mid-session by the pressure trims, so the report's "especially
the most recent series cover" is the expected shape of this defect, not a separate bug.

**Stage 2 remediation (2026-11-13):**
- Background retention raised 10% → 25% (`EphyraImageLoaderFactory.kt`): a background
  trip no longer drops nearly the whole working set, so returning to History/Library
  keeps roughly the last screenfuls decoded.
- Hard cap: the memory cache is clamped to 96 MiB at baseline capture
  (`ImageCacheMemoryPolicy.cappedBaseline`) — percent-of-heap sizing under `largeHeap`
  otherwise reaches ~150 MiB on big-heap devices.
- Trim floor reviewed and kept at 16 MiB: on LOW-tier devices the baseline (20% of a
  small heap) already sits near the floor, so raising it would neuter the pressure
  trim; the no-grow correction (`trimTarget` never exceeds the baseline) fixes the
  small-baseline case where the old formula *raised* `maxSize` under pressure.
- Resume-time memory-tier cover prefetch: **investigated, not shipped.** Coil 3.6.3's
  memory key excludes the request size when there are no transformations, but
  `MemoryCacheService.isCacheValueValidForSize` validates the cached decode against
  the request's layout-resolved size at the default `Precision.EXACT` (±1 px). A
  data-layer prefetch cannot know the size a grid cell will resolve, so its entries
  could never satisfy a grid request — dead code by construction. Revisit only as a
  UI-owned prefetch (composition knows its constraints) or a reviewed
  `Precision.INEXACT` decision, which would trade full-resolution decodes for hit
  flexibility.

**P2 — Cache-key computation performs filesystem I/O.**
`MangaCoverKeyer.kt` (`MangaKeyer.key`, `MangaCoverKeyer.key`) call
`coverCache.getCustomCoverFile(...).exists()` / `hasCustomCover(coverCache)` — a
synchronous `stat` per key computation, including memory-cache lookups. Per call it is
cheap; on grids of hundreds of covers re-keying on every recomposition-driven request
it is measurable, and it is I/O on a path that is otherwise pure string work.

**Fixed (Stage 2, 2026-11-13):** the keyers now branch on `favorite` /
`isMangaFavorite` — library content keys by manga identity, browse by URL — and never
probe the filesystem. The custom-cover signal the `exists()` probe carried is carried
by `coverLastModified`, which every cover-bytes mutation bumps; the one path that did
not (`MigrateMangaUseCase`, copying a custom cover to a migration target) now bumps it
too, closing a latent stale-custom-cover-after-migration bug the probe had been
masking. Disclosed consequence: a favorite series without a custom cover re-keys once
from `url;lm` to `id;lm` on upgrade — a one-time re-decode from the durable store,
never a re-download.

**P3 — Cache & memory exhaustion when reading a very long series or many series
(user report; requires closing and reopening to clear).**

What is already bounded in source at `e781388ef`:

- Chapter page disk cache: Coil `DiskCache` LRU, tiered 100/256/512 MiB
  (`core/data/.../cache/ChapterCache.kt`).
- Image disk cache: 50/150/300 MiB; durable cover store: 256 MiB / 30-day LRU
  (`CoverCache.kt`).
- Reader encoded-page bytes: `PageByteBudget` via `BoundedPageByteStore` — exact
  accounting, oversized-value refusal, viewport pins, and the owner's eviction
  callback clearing the page handle so the budget describes memory, not a map
  (`PageByteStoreOwner.kt`, `core/domain/.../BoundedPageByteStore.kt`); chapter-scoped
  teardown plus series-switch eviction on `onCleared` (Stage 4 / MED-002, PR #343).

Remaining candidates for the reported exhaustion — all device-evidence questions, not
source-verifiable:

- Decoded hardware bitmaps for long-strip pages resident in the Coil memory cache
  during a long session: hardware bitmaps live in graphics memory, not the Java heap,
  so heap-based symptoms (and heap profilers) under-report them.
- The trim interplay: the policy halves the Coil cache under pressure but the reader's
  own byte budget is untouched; the two budgets are not coordinated.
- Report vintage: the structural fixes landed through PR #343 (merged 2026-11-10); a
  report against an older nightly may describe the pre-Stage-4 build.

**Verdict:** no unbounded structure found in source. The report needs a device
reproduction against the current nightly before any code change (record per
B-050/A5), measuring memory + graphics counters over a long-series session.

### Design

**D1 — Disabled-state affordance (F1's UI half).** The navigator's disabled
container equals its enabled container (ChapterNavigator.kt:65-68). Whatever the
functional fix, the disabled state must be visually distinct.

**D2 — Cover presentation.** The 300 ms process-wide crossfade is right for full
artwork and wrong for small thumbnails, where it reads as lag (P1's perceived half).
Options, in increasing scope: shorter crossfade for thumbnail-scale requests;
placeholder tint from a stored dominant colour so the slot never renders empty
(schema check required before proposing — Ephyra's manga table must be verified for a
dominant-colour column).

### Safety

**S1 — Silent-failure pattern class.** F1 is an instance of a class: user-initiated
actions whose failure path is `?: return` with no observable feedback. A behavioural
convention — every user-initiated action either performs or surfaces feedback — is not
expressible as an ArchUnit rule; it lands as a review checklist item plus F1's fix as
the exemplar.

**S2 — Storage scoping verified clean.** Covers live in app-scoped external storage
(`CoverCache.getCacheDir` → `getExternalFilesDir`), the chapter cache in `cacheDir`
(system-clearable), and the persisted-cover cap is 8 MiB
(`MangaCoverFetcher.MAX_PERSISTED_COVER_BYTES`). No public-storage writes. No action.

## Resource bounds (analytic, source-cited)

Every long-session memory consumer and its bound, as of the Stage 2 merge. This is the
"no unbounded structure" table for P3: each row is checkable in source, and the
cap-pinning tests (`ImageCacheMemoryPolicyTest`, `ImageCacheBoundsGateTest`) keep the
memory-tier rows honest.

| Resource | Bound | Owner |
|---|---|---|
| Chapter page disk cache | LRU, tiered 100 / 256 / 512 MiB | `core/data/.../cache/ChapterCache.kt` |
| Image disk cache | LRU, tiered 50 / 150 / 300 MiB | `EphyraImageLoaderFactory.kt` |
| Durable cover store | 256 MiB, 30-day LRU prune | `core/data/.../cache/CoverCache.kt` |
| Single persisted cover write | 8 MiB cap, oversized served uncached | `MangaCoverFetcher.kt` |
| Reader encoded-page bytes | `PageByteBudget` via `BoundedPageByteStore`; viewport pins; series-switch eviction | `PageByteStoreOwner.kt` |
| Decoded-image memory cache | 20 / 25 / 30% of heap by tier, hard cap 96 MiB | `EphyraImageLoaderFactory.kt`, `ImageCacheMemoryPolicy.kt` |
| Memory cache while backgrounded | 25% of the capped cache | `EphyraImageLoaderFactory.kt` |
| Foreground pressure trim | half the baseline, floor 16 MiB, never grows | `ImageCacheMemoryPolicy.kt` |
| Cover fetch / decode parallelism | 8 / 3 concurrent | `EphyraImageLoaderFactory.kt` |
| Library durable cover prefetch | 24 covers per library change | `LibraryScreen.kt` |

Binge and sampling both resolve against the same rows: a 200+-chapter binge is bounded
by the page byte budget plus the capped decoded cache (LRU evicts scrolled-out strips),
and a ~30-series sampling hop is bounded by the same capped cache plus the 256 MiB
durable cover store. Nothing in the table grows with session length without hitting a
named ceiling.

## Remediation plan

Sequenced PRs, smallest user-facing first:

- **Stage 1 (F1 + D1): next-chapter feedback.** Make the disabled state visually
  distinct; on a press (or edge-swipe) with no navigable next chapter, surface
  feedback that distinguishes "no further chapters" from "the next chapter is outside
  the reader's navigation universe (read / duplicate-collapsed / downloaded-only
  filter)". Keep RDR-002's window semantics; whether the reader should fall back to
  the unfiltered next chapter is an owner decision, recorded as a question, not
  decided here. Regression tests + CHANGELOG.
- **Stage 2 (P1 + P2 + D2): cover pipeline.** SHIPPED 2026-11-13, minus the motion
  half: keyer I/O removed (P2); background retention raised to 25% under a 96 MiB hard
  cap; trim floor reviewed and kept at 16 MiB with a no-grow correction; the
  resume-time memory-tier prefetch investigated and recorded as infeasible under Coil
  3's EXACT-precision size validation (see P1). The thumbnail crossfade (D2) stays
  deferred per the owner's motion policy — motion/crossfade tuning waits until after
  the memory/caching work and is a whole-page decision. Device before/after evidence
  on the delayed-appearance report remains owner-side (Stage 3).
- **Stage 3 (P3): device measurement.** Long-series reading session on the current
  nightly with memory + graphics counters; only a reproduced exhaustion produces code
  changes. Owner-side (A5/B-050).

## Honesty

E1 source inspection only, at `e781388ef`. No device evidence claimed anywhere in
this document; the two user reports are diagnosed to source-level causes but stand
unconfirmed until Stage 1/Stage 2/Stage 3 land and are reproduced on device. No JDK
locally; remote CI is the verification channel for every change these stages ship.
