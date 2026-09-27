# ADR-0010: Region-decoded slices, not a tile engine

- **Status:** Accepted
- **Date:** 2026-09-28
- **Supersedes:** [`0003`](0003-document-viewport-zoom.md) — the *tile engine* half only. The zoom-ownership
  half of ADR-0003 remains in force and is restated below, because it is what fixed `DEF-003`.
- **Decision:** The continuous reader keeps `LazyColumn` as its scroll owner and renders
  fixed-height slices through `BitmapRegionDecoder` with `inSampleSize` chosen by a scale bucket.
  `DocumentViewport`, `DocumentTilePartition`, `TileScalePolicy` and `WebtoonViewport` are retired
  rather than wired.

## Context

ADR-0003 chose a virtualized tile pipeline for continuous webtoon zoom. `B-025` later recorded that
this model cannot be wired into the shipping surface: `DocumentViewport` owns `offset.y` itself,
while `ComposeWebtoonReader`'s `LazyColumn` owns vertical scroll. Wiring it would have given the
surface two owners of vertical position, which would fight and surface as content that jumps — on
the exact surface `DEF-002` and `DEF-003` exist to improve.

That left the cluster unreconciled: four tested contracts, no production consumer, and a live ADR
pointing at them. The choice now is to build the tile engine or to retire it.

Two facts decided it.

**The dimension argument.** Tiling exists to bound a *single* allocation. A fixed-height slice plus
`inSampleSize` bounds it without a tile scheduler at all:

```text
slice decode cost = sourceWidth × sliceHeight × 4 bytes
```

At 800 px slice height and a 1080–1600 px source width, that is **3.4–5.1 MB per slice**. A
`LazyColumn` keeps only visible slices plus prefetch alive, so a 250-slice chapter holds roughly
10–20 MB of decoded pixels — inside the 64 MiB budget in
[`../PERFORMANCE_BUDGETS.md`](../PERFORMANCE_BUDGETS.md) — and virtualization and disposal come from
`LazyColumn` rather than from code we would own and maintain.

**The zoom-detail argument.** ADR-0003 assumed zooming reveals detail a whole-strip render cannot.
For webtoon, fit-width is already approximately 1:1: a 1080-wide source on a 1080-wide screen at fit
*is* native resolution. Real additional detail exists only when the source is wider than the
viewport, and the correct response is to choose `inSampleSize` so decoded width never exceeds source
width. That is exactly what `ScaleBucket` already computes and what `MED-003`'s scale-bucket
hysteresis exists to make settle instead of re-decoding. **Tiling solves a problem that sample
sizing already solves.**

## Decision

- **Kept from ADR-0003, and still binding:** one transform owns the rendered surface; layout scroll
  geometry is authoritative and is not visually scaled per item; zoom is isotropic and
  focal-anchored; one clip. This is precisely what `DEF-002`/`DEF-003` are, and both are fixed,
  production-wired and at `E3`. **This ADR does not touch that arrangement.**
- The scroll owner remains `LazyColumn`. There is no custom canvas viewport.
- Slices decode through the platform `BitmapRegionDecoder` with `inSampleSize` from `ScaleBucket`.
- `DocumentViewport`, `DocumentTilePartition` and `WebtoonViewport` are deleted.
- `TileScalePolicy`'s scale-bucket hysteresis is **migrated into `DecodePlanner`**, not discarded.
  `WebtoonViewportState`'s chapter-boundary guard moves to `ReaderChapterWindowPolicy`.

## Consequences

- Four verified contracts (~400 lines and their tests) are deleted. That is the deliberate price of
  this decision, and it is the point: a test suite passing against a model the app will never use
  is not an asset, it is a maintenance obligation that reads like progress.
- The tile scheduler, tile cache, prefetch policy and GPU composition path are never built, so the
  maintenance surface ADR-0003 implied does not arise.
- The media contracts that *are* the right model get wired for real: `PageSource`/`PageMetadata` for
  identity and intrinsic geometry, `DecodePlan`/`DecodePlanner` for crop and sample sizing,
  `RenderPathPolicy` for slice-vs-single-page, `AnimationPolicy` replacing seven files that animate
  inline.
- **Stated limit, so this can be re-litigated with evidence:** a scan of genuinely extreme
  dimensions (multi-gigapixel single-image pages) would exceed the slice budget and would need
  re-tiling. No such case is evidenced in this project's fixtures or sources, and the honest
  response to one appearing is a measurement, not a speculative re-architecture.

## Rejected alternatives

- **Build the tile engine anyway.** Retained as insurance against hypothetical extreme scans. Rejected
  because it costs a scheduler, a cache and a prefetch policy forever, in exchange for headroom
  against a case with no evidence behind it.
- **Wire `DocumentViewport` into the `LazyColumn` surface.** Rejected by `B-025`: two owners of
  vertical position.
- **Keep `LazyColumn` + `WebtoonDocumentZoom` and leave ADR-0003 standing.** Rejected because the
  ADR would point at four deleted contracts, which is the documentation failure ADR-0006 forbids.

## Evidence required

Decoded working-set measurement against the 64 MiB budget on a long webtoon, continuous scroll at
fit and zoomed, low-memory background/foreground cycle, and a zoom-to-native-resolution check
confirming real detail without OOM. **Every gate falsification-verified** — a deliberate violation
must be observed red before it counts.

## Supersedes / superseded by

Supersedes [`0003`](0003-document-viewport-zoom.md) on the rendering-engine choice. ADR-0003's
zoom-ownership principles (one transform, isotropic focal zoom, authoritative layout geometry)
remain accepted and are restated in this record's Decision section.
