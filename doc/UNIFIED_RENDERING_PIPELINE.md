# Unified page-rendering pipeline

Status: **Phase 1 partially done, Phase 3 done, Phase 2 open.**

- **Phase 1 (in progress).** The reader-agnostic *machinery* now lives in
  `feature/reader/.../viewer/render/`: `WebtoonSlicer`, `WebtoonRegionDecoder`, `WebtoonSlicePlanner`
  (pure, JVM-tested). Nothing about them is webtoon-specific, and the name is now the only
  webtoon-flavoured thing left. Still outstanding: the two **composables** are still two renderers
  (`SlicedWebtoonImage` in `viewer/webtoon/`, `PagedSliceColumn` in `viewer/pager/`), not one
  `PagePixelSurface`. Folding them together is Phase 2's job — see below.
- **Phase 2 (open, and it is not cosmetic).** `PagedSliceColumn` does not call `WebtoonSlicePlanner`.
  It re-derives the plan inline and is **strictly worse** than the planner it duplicates:
  1. it calls `WebtoonSlicer.maxSliceDisplayPx(textureLimitPx = …)` **without `viewportHeightPx`**,
     so slices are cut to the full texture ceiling rather than the viewport;
  2. it computes the sample with `sampleSizeForWidth` only, **skipping `SampleSizePolicy`
     hysteresis** — so a rotation or viewport change re-decodes every slice instead of holding the
     previous legal sample;
  3. it is **not crop-aware**: it passes `srcWidth/srcHeight` instead of
     `RenderPathPolicy.sliceSourceRect(...)`. Today the call site guards `!cropBorders`, so this is
     latent rather than live — but it is one removed guard away from decoding the wrong rectangle;
  4. it produces no `DecodePlan`, so `EXCEEDS_TEXTURE_LIMIT` — the reason the strip is sliced — is
     not recorded anywhere.
  The fix is to give the paged host the same tuple the webtoon host already builds inline
  (`PageMetadata`, `PageSource.InMemory`, `RenderPathPolicy.choose`, `targetWidthPx`,
  `viewportHeightPx`) and render `plan.slices`. The paged host already has every input **except**
  `viewportHeightPx` and an image format; both must be threaded before the planner can be shared.
- **Phase 3 (done).** `ZoomableMangaPage` slices tall pages via `PagedSliceColumn` instead of one
  oversized `AsyncImage` (`feat(reader): slice tall paged pages…`), guarded by
  `fix(reader): re-decode paged slices when the page bytes change`.
- **Remaining gate for all of it:** visual QA on a real tall page — no GPU in CI.

## The symptom

A **tall page in a paged reading mode** (Left-to-Right, Right-to-Left, Vertical) renders
**blurry**. The same page in a **webtoon / continuous** mode is sharp. The user report
("every single pixel of quality") is the acceptance bar.

## Root cause (traced, not guessed)

Two readers, two renderers, one of which downsamples tall pages:

| Mode(s) | Viewer | Renderer | Tall-page behaviour |
|---|---|---|---|
| `WEBTOON`, `CONTINUOUS_VERTICAL` | `ComposeWebtoonReader` (LazyColumn) | `SlicedWebtoonImage` | Region-decodes **slices** at screen width; each tile is under the GPU texture ceiling → **sharp** |
| `LEFT_TO_RIGHT`, `RIGHT_TO_LEFT`, `VERTICAL` | `ComposePagerReader` (Pager) | `ZoomableMangaPage` | One Coil `AsyncImage` at `Size.ORIGINAL` → one oversized bitmap → **GPU downsamples → blur** |

The paged path (`feature/reader/.../viewer/pager/ZoomableMangaPage.kt`) decodes the whole page
at intrinsic width (`PageZoomPolicy.ZOOMABLE` → `PageDecodeWidth.plan` returns the intrinsic
width) into a **single** `Bitmap`. When the page is taller than the device's
`GL_MAX_TEXTURE_SIZE` (commonly 4096–16384 px), the hardware renderer cannot upload it as one
texture and **downsamples it to fit** — that is the blur. A 1080×24000 page is also ~100 MB of
`ARGB_8888` for one bitmap, so it is a memory problem as well as a quality one.

The webtoon path already solves exactly this with `WebtoonSlicer` + `WebtoonRegionDecoder`
(each slice `< maxTextureSize`, region-decoded at the display width, stacked with
`ContentScale.FillWidth`). Nothing about that machinery is webtoon-specific — it is
page-rendering machinery that happens to live in `viewer/webtoon/`.

## The existing, well-factored pieces (reuse, do not rewrite)

- `core/domain/.../media/RenderPathPolicy.kt` — `choose(...)` → `SLICED | WHOLE_IMAGE | MERGED_BITMAP`,
  with a named `SlicingBlocker` per fallback. Pure, tested.
- `core/domain/.../media/PageDecodeWidth.kt` — `plan(intrinsicWidth, displayWidthPx, density, zoom)`
  → decode width. Already parameterises `ZOOMABLE` (paged) vs `FIXED_SCALE` (webtoon). **This is the
  seam a unified renderer needs.**
- `feature/reader/.../viewer/webtoon/WebtoonSlicePlanner.kt` — geometry + sample (width-fit + hysteresis).
- `feature/reader/.../viewer/webtoon/WebtoonRegionDecoder.kt` — `decodeSlices(...)` against
  `BitmapRegionDecoder`, cooperative cancellation.
- `feature/reader/.../viewer/webtoon/WebtoonSlicer.kt` — pure exact-partition math.
- `feature/reader/.../viewer/webtoon/SlicedWebtoonImage.kt` — the composable (slices or `fallback`).

## Proposed unified design

Extract a **reader-agnostic pixel renderer** and call it from both readers. One decision path
(`RenderPathPolicy` → `PageDecodeWidth` → `WebtoonSlicePlanner` → `WebtoonRegionDecoder`), two hosts.

```
PagePixelSurface(
  page, bytes, metadata, source, renderPath,     // the media tuple (already threaded in webtoon)
  targetWidthPx, viewportHeightPx,               // from PageDecodeWidth.plan(zoom)
  fallback: @Composable () -> Unit,              // reader-specific whole-image (Coil / crop)
)
```
- `SLICED` → `Column` of region-decoded slices (the current `SliceColumn`).
- `WHOLE_IMAGE` / `MERGED_BITMAP` / decode failure → `fallback()`.
- **No zoom, no gestures** of its own — the host applies the transform:
  - webtoon: `WebtoonZoomSurface` around the item.
  - paged: `pagerZoomLayer(viewportState.transform)` around the page box.
  This is the invariant that makes it hostable by both (same rule `SlicedWebtoonImage` already
  documents: slices render bare `Image`s, gestures live at the item level).

The paged host keeps its existing box structure; only the **image content** changes:

```
// today:  Box(zoomModifier) { verticalScroll { AsyncImage(Size.ORIGINAL) } }
// unified: Box(zoomModifier) { verticalScroll { PagePixelSurface(..., targetWidthPx = PageDecodeWidth.plan(ZOOMABLE)) } }
```
For a tall page the surface emits the slice `Column` (still `verticalScroll`-able); for a normal
page it emits the whole-image `fallback` (the existing Coil `AsyncImage`). Zoom, crop, transitions
and the surrounding Box are untouched.

## Phasing (each step independently shippable / revertible)

1. **Pure move (no behaviour change).** — *Partly done.* `WebtoonSlicer`, `WebtoonRegionDecoder`
   and `WebtoonSlicePlanner` now live in `feature/reader/.../viewer/render/` (git-tracked renames,
   package/import updates only; `WebtoonSlicerTest`, `WebtoonRegionDecoderTest`,
   `WebtoonSlicePlannerTest` moved with them and stay green). Still to do: move and merge the two
   composables (`SlicedWebtoonImage` + `PagedSliceColumn`) into one `PagePixelSurface`, which is
   Phase 2.
2. **One decision, two callers.** Add a pure `PageRenderDecision`/reuse of `RenderPathPolicy` +
   `PageDecodeWidth` so the paged host computes `renderPath` and `targetWidthPx` the same way the
   webtoon host does. JVM-testable.
3. **Wire the paged host (the fix).** In `ZoomableMangaPage`, when
   `WebtoonSlicer.needsSlicing(...)`, render `PagePixelSurface` instead of the single `AsyncImage`;
   otherwise keep the current `AsyncImage`. This is the user-visible quality fix.
4. **Cleanup.** The "is this a tall page?" decision is **not** three copies of one rule — audit
   correction: it is two *distinct* decisions that happen to share a name:
   - `ZoomableMangaPage`'s `aspectRatio < 0.5f` — the **render-layout** decision (tall → `FillWidth`
     + `verticalScroll`), and the `_tall` Coil cache-key suffix is derived from this same boolean.
   - `ImageUtil.isTallImage` (`h/w > 3`) — the **legacy download-time split** (`splitTallImage`,
     now marked "no longer needed" in `DownloadPreferences`). Its integer division was a real bug
     (see below), fixed here.
   Do not force these two to share a threshold: they answer different questions and a page that is
   "tall enough to split on disk" need not be "tall enough to lay out width-filled". What *is* worth
   doing is naming the render threshold (`PageGeometry.TALL_ASPECT`) so the magic `0.5f` and the
   derived `_tall` key cannot drift apart.

## Why not a big-bang rewrite

The renderer is the most-reached code in the app and cannot be verified from CI (no GPU, no
eyes). The phases above keep every step green and reviewable; step 3 is the only behaviour change
and is gated by the pure math in steps 1–2. Each step is small enough to revert in isolation.

## Risks / mitigations

- **Crop + slicing**: slicing already supports a crop rect (`RenderPathPolicy.sliceSourceRect`).
  Step 3 must pass the paged host's crop state into the tuple, exactly as the webtoon host does.
- **Zoom quality**: paged decode stays `ZOOMABLE` (intrinsic width) via `PageDecodeWidth`, so
  zoomed slices stay sharp — do not route the paged host through `FIXED_SCALE`.
- **Pager item sizing**: a sliced `Column` must sit inside the existing `verticalScroll` so the
  pager item keeps its one-page-one-item contract.