# Document Viewport Contract

> **Status:** binding technical contract, rewritten 2026-09-28.
> **Supersedes:** the tile-pipeline model defined here previously, per
> [`adr/0010`](adr/0010-region-decoded-slices-not-tile-engine.md).

## Which surface this describes

The continuous reader is a `LazyColumn` of slices, not a canvas. `LazyColumn` owns vertical scroll;
one graphics-layer transform on the scroll container owns the rendered transform. That arrangement
is what fixed `DEF-002` and `DEF-003`, and it is unchanged by this rewrite.

The earlier version of this document specified a virtualized tile pipeline (`DocumentViewport`,
`DocumentTilePartition`) and was wrong about how it would reach production. `B-025` established
that `DocumentViewport` owns `offset.y` while the shipping `LazyColumn` also owns vertical
position, so wiring it in would have created two owners of the same axis. Rather than build the
engine, the programme retired it. The reasoning and the dimension arithmetic are in ADR-0010; this
document records what the surface actually is.

## Coordinate model

One coordinate space, chosen at the boundary, never mixed with Compose layout pixels.

```text
slice source rect   logical source pixels of one region
viewport size       view units (dp) at scale 1
sample size         inSampleSize passed to the region decoder
```

Device pixel conversion happens only at the decode/render boundary.

## Bounding memory without a tile scheduler

The reason a tile engine exists is to bound a single allocation. A fixed-height slice plus
`inSampleSize` bounds it without any scheduler:

```text
slice decode cost = sourceWidth × sliceHeight × 4 bytes
```

At 800 px slice height and a 1080–1600 px source width that is **3.4–5.1 MB per slice**.
`LazyColumn` keeps only visible slices plus prefetch composed, so a 250-slice chapter holds roughly
10–20 MB of decoded pixels — inside the 64 MiB budget in
[`PERFORMANCE_BUDGETS.md`](PERFORMANCE_BUDGETS.md). Virtualization and disposal come from
`LazyColumn` rather than from code this project would own forever.

`BitmapRegionDecoder` and `BitmapFactory.Options.inSampleSize` are platform APIs. They are
first-party, maintained by Google, and add no dependency.

## Why sample sizing is sufficient here

Tiling solves a problem that sample sizing already solves, for webtoon specifically. Fit-width is
already approximately 1:1: a 1080-wide source on a 1080-wide screen at fit *is* native resolution.
Real additional detail exists only when the source is wider than the viewport, and the correct
response is to choose `inSampleSize` so decoded width never exceeds source width. That is
`SampleSize`'s job.

**The deadband is what makes zoom feel sharp rather than stuttering.** Without it, a pinch hovering
at one threshold flips the sample size every frame, each flip invalidating every cached region at the
previous scale, and the strip re-decodes continuously for the whole gesture. `SampleSizePolicy`
holds the current sample between thresholds so a gesture must move decisively to pay a re-decode.
See `SampleSizePolicyTest`, ported with its assertions intact from the retired `TileScalePolicyTest`.

## Ownership

```text
LazyColumn          owns vertical scroll position and slice disposal
one transform       owns the rendered scale/offset, applied once to the scroll container
sliced image        owns its own decode and sample size for the visible region
ReaderChapter       owns the bounded encoded-byte working set (PageByteStoreOwner)
```

Layout code does not scale individual items. The transform is applied once, to the container.

## Gesture ownership

At fit, a vertical gesture scrolls the chapter. Zoomed, the same gesture pans within the enlarged
content. The reader has exactly one pointer path: `ReaderGestureArbiter` arbitrates and emits
`DelegateSingleScroll` when the viewport declines a gesture, which is how the outer scroll is handed
the vertical axis at fit scale. The continuous reader's call site is the remaining wiring in
`RDR-005`.

## Animated, unsupported and corrupt content

The decode plan declares animation and policy per page. Animated pages are not silently passed to a
static region decoder. An undecided animation verdict is explicit rather than inferred. Crop is
judged against content size, not intrinsic size, and `DEF-008`'s transposed-inset defect is guarded
on device by `BorderCropDeviceTest`.

## Cancellation and memory

- Offscreen slice decode work is cancelled when it leaves the window.
- Decoded bitmaps are composition-scoped and are not recycled while Compose may still read them.
- Encoded working bytes are bounded by `PageByteBudget` and released at chapter disposal.
- No bitmap is ever allocated for an entire long strip.
- Memory budget and prefetch distance are measured, not guessed.

## Re-litigation condition

A scan of genuinely extreme dimensions — multi-gigapixel single-image pages — would exceed the
slice budget and would need re-tiling. No such case is evidenced in this project's fixtures or
sources. If one appears, the response is a measurement, not a speculative re-architecture.

## Evidence

- decoded working-set measurement against the 64 MiB budget on a long webtoon;
- continuous scroll at fit and zoomed, with `dumpsys meminfo`;
- zoom-to-native-resolution check confirming real detail without OOM;
- `SampleSizePolicyTest` for sample settling (falsification-verified);
- `WebtoonZoomRenderTest.sliceBoundariesMoveWithTheDocumentTransform` for `DEF-003`;
- low-memory background/foreground cycle.

## Related

- [`adr/0010`](adr/0010-region-decoded-slices-not-tile-engine.md) — the decision and its arithmetic.
- [`adr/0003`](adr/0003-document-viewport-zoom.md) — superseded in part; its zoom-ownership
  principles remain in force.
- [`READER_GESTURE_CONTRACT.md`](READER_GESTURE_CONTRACT.md) — pointer state machine.
- [`MEDIA_PIPELINE_CONTRACT.md`](MEDIA_PIPELINE_CONTRACT.md) — source identity and decode plans.
