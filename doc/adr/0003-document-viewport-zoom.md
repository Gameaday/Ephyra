# ADR-0003: Document viewport for continuous zoom

- **Status:** Superseded in part by [ADR-0010](0010-region-decoded-slices-not-tile-engine.md) (2026-09-28)
- **Date:** 2026-09-24

> **Superseded in part.** The *tile engine* half of this record is retired: the continuous reader
> keeps `LazyColumn` and decodes fixed-height slices through `BitmapRegionDecoder`, and
> `DocumentViewport`/`DocumentTilePartition`/`WebtoonViewport` are deleted. See ADR-0010 for the
> reasoning and for the dimension arithmetic that decided it.
>
> **The following remain in force and are not superseded**, because they are precisely what fixed
> `DEF-002`/`DEF-003`: one transform owns the rendered surface; layout scroll geometry is
> authoritative and is not visually scaled per item; zoom is isotropic and focal-anchored; one clip.
> Do not "fix" those by reintroducing per-item transforms.
- **Decision:** Continuous webtoon zoom is implemented in document coordinates through a virtualized viewport, not by transforming LazyColumn item layout and paint independently.

## Context

The current implementation applies a graphics-layer transform and dynamic layout scaling to independent page/slice items. User testing reports overlap, gaps, and a zoom experience that widens content rather than revealing coherent detail.

## Decision

- The document owns source page and slice coordinates.
- The viewport owns scale, translation, and visible rectangle.
- Tiles are decoded progressively for visible and adjacent regions.
- The viewport applies one clip.
- Layout scroll geometry remains authoritative and is not visually scaled per item.
- If a future product requirement needs 2D document zoom, it is implemented at the document canvas level.

## Consequences

- Zoom and scroll share one coordinate system.
- Memory is bounded by visible/nearby tiles.
- Continuous rendering requires a new engine rather than more LazyColumn modifiers.
- Existing slice geometry can be reused only after conversion into document coordinates.

## Rejected alternatives

- Per-page graphics layers.
- Dynamic LazyColumn item height during pinch.
- A global `scaleX`/`scaleY` applied to the whole list without scroll compensation.
- Calling horizontal stretching zoom.

## Evidence required

Tile partition properties, visible-rectangle tests, pinch focal-point tests, screenshot sequences, scroll-after-zoom tests, and low-memory benchmark results.

## Supersedes / superseded by

The rendering-engine decision is superseded by
[ADR-0010](0010-region-decoded-slices-not-tile-engine.md). The zoom-ownership principles above remain
accepted; the evidence requirements that named tile-partition properties are satisfied instead by the
slice decode path's decoded-working-set measurement.
