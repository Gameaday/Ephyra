# Document Viewport Contract

> **Status:** binding technical contract. Existing slices are inputs to this model, not the target layout model.

## Coordinate model

The document has one coordinate space. Choose one unit at the boundary and never mix it with Compose layout pixels.

Recommended model:

```text
DocumentPoint   logical document coordinates
ViewportRect    visible logical document rectangle
TileKey         sourceId + pageIndex + tileIndex + scaleBucket
```

Device pixel conversion happens only at decode/render boundaries.

## Structure

```text
ReaderDocument
  ordered source pages
  page rectangles
  chapter boundaries
  source metadata
  optional animated/unsupported regions
```

Source page dimensions are immutable once accepted. A page's content rectangle can be updated only by a source revision or an explicit crop/decode-plan change, which produces a new document revision.

## Viewport

The viewport owns:

- document offset;
- scale;
- visible rectangle;
- tile requests;
- prefetch rectangle;
- clip;
- focal-point zoom;
- cancellation;
- tile cache integration.

Layout code does not scale individual LazyColumn items. The document is virtualized by visible tiles, not by page-item geometry.

## Tiles

Tiles are renderer artifacts. A tile has:

- document rectangle;
- source page identity;
- source pixel rectangle;
- target scale bucket;
- decoder/source identity;
- state: queued/loading/ready/failed/cancelled.

A failed tile is retryable without invalidating the entire document. A source page failure does not create a permanent gap.

## Geometry rules

- Tile partitions cover the document without gaps or overlap.
- Pixel rounding preserves document boundaries within a declared tolerance.
- Tile overlap, if used for seams, is explicit and bounded.
- Scale changes never change document geometry.
- Scroll and zoom use the same coordinate transform.
- Focal zoom keeps the document point under the gesture centroid stable.
- Chapter boundaries are represented as document regions, not as independent transform owners.

## Animated/unsupported content

The document planner declares animation and decode policy per page. Animated pages are not silently passed to a static region decoder. Unsupported codecs use a declared fallback render artifact and remain visible as a stable placeholder/loading/error state.

## Cancellation and memory

- Offscreen tile work is cancelled when no longer useful.
- Completed bitmap references are disposable by the viewport owner.
- No bitmap is recycled while a renderer may still read it.
- Visible tiles are never evicted before nearby prefetch tiles without a policy reason.
- Memory budget and prefetch distance are measured, not guessed.

## Evidence

- tile partition property tests;
- document/page rectangle tests;
- focal-point zoom tests;
- scroll-after-zoom tests;
- page/chapter boundary continuity tests;
- cancellation and failure recovery tests;
- screenshot sequence tests;
- low-memory benchmark.