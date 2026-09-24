# ADR-0003: Document viewport for continuous zoom

- **Status:** Accepted
- **Date:** 2026-09-24
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
