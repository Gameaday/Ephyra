# Cover Art and Cache Systems Audit

> **Status:** HISTORICAL - completed improvement-session record. Retained for archaeology only;
> not current guidance. Current forward plan: [`../2_0_COMPLETION_PLAN.md`](../2_0_COMPLETION_PLAN.md).

Status: audit complete; two defects fixed in this pass, the rest are recommendations with evidence.
Scope: Coil image loader configuration (`app/src/main/java/ephyra/app/App.kt`), cover requests
(`presentation-core/.../components/MangaCover.kt`, `MangaCoverFetcher`), reader page requests
(pager and webtoon viewers), and the disk/memory cache policies around them.

Every claim was verified by reading the cited code.

---

## What is already correct (do not re-audit)

- **Webtoon strips are width-constrained, not decoded at original size.**
  `ComposeWebtoonReader.kt:821-837` decodes at `targetWidthPx * densityScale` — physical pixels, so
  high-dpi devices get crisp strips — and the memory cache key includes that width *and* the source
  byte size (`_w${decodeWidth}_b$bytesSize`). Two different devices or two re-encoded sources cannot
  collide on one entry, which is the usual cause of "the strip is blurry after I changed zoom".
- **Page requests bypass the global crossfade.** Both reader paths set `.crossfade(false)`
  (`ComposeWebtoonReader.kt:830`, `ZoomableMangaPage.kt:444`), so a page never fades in over the
  previous page.
- **Long strips are routed away from hardware bitmaps** that would exceed `GL_MAX_TEXTURE_SIZE`
  (`HardwareGuardDecoder`), and JXL is decoded in software by the bridge. This is the reason long
  webtoon chapters do not fail to decode on mid-range GPUs.
- **Disk cache is tier-scaled** (50/150/300 MiB by `DeviceUtil.performanceTier`), so a low-end device
  is not asked to hold a high-end device's cache.
- **Cover requests are sized by layout, not by original pixels.** `MangaCover` builds an
  `ImageRequest` with `Precision.EXACT` and no explicit size, so Coil resolves the ratio box the
  grid actually laid out and downsamples to it. Covers are not a memory problem.
- **The cover cache key is shared between grid and detail** through `MangaCoverFetcher` + `MangaKeyer`
  / `MangaCoverKeyer`, so opening a series does not refetch the cover it just displayed.
---

## Fixed in this pass

### 1. Memory-cache trim ratcheted down for the life of the process (fixed)

`App.onTrimMemory` set `memoryCache.maxSize = memoryCache.maxSize / 2`. Each `TRIM_MEMORY_RUNNING_LOW`
therefore halved the *previous* halving, so a session that hit pressure a few times (common during
long webtoon reading, which the surrounding comment itself describes) ended up with an eighth of its
configured cache and nothing ever restored it. Every cover and page scrolled past after that point
was re-decoded instead of being found in memory, which reads as the app getting slower the longer it
is used.

Fix: the configured size is captured once when the loader is built (`imageCacheBaselineMaxSize`),
trims compute from that baseline (idempotent), and `restoreImageCacheSize()` restores it the next time
the app is foregrounded.

### 2. Cover placeholder was a hardcoded grey (fixed)

`CoverPlaceholderColor = Color(0x1F888888)` was the same in all 17 themes, so on Monochrome and Monet
the placeholder was the one surface in the grid that did not belong to the palette around it. Now
derived from `MaterialTheme.colorScheme.surfaceContainerHighest`.

---

## Recommendations (evidence-backed, not yet implemented)

### R1. Pager mode decodes at `ORIGINAL` for every page (highest memory cost observed)

`ZoomableMangaPage.kt:443` requests `.size(CoilSize.ORIGINAL)` with `bitmapConfig(HARDWARE)` and
`allowRgb565(false)`. A 2400x3600 page is ~33 MB as ARGB_8888, and the pager keeps an adjacent-page
window plus preload, so a handful of neighbour pages can hold 100-200 MB of graphics memory on a
device that chose the LOW tier. The intent (zoom without blur) is right, but the cost is unbounded by
device.

Suggested: cap the decode at `min(sourceWidth, screenWidthPx * 2)` unless the device tier is HIGH and
the page is the current one; keep `ORIGINAL` for the focused page on HIGH tier. Measure with
`macrobenchmark` before changing, since the failure mode is "zoomed text is softer".

### R2. `allowRgb565(false)` is global, including webtoon strips

`App.kt:398` disables the 50% memory saving everywhere. For webtoon strips (usually JPEG art rather
than line-art PNGs) this is the single largest available saving, and it is exactly the case the
`onTrimMemory` comment describes as memory-hungry. Suggested: keep `false` as the default for pages
and JXL, and allow 565 per-request for webtoon strips on LOW/MEDIUM tiers. Risk to check: banding on
gradients.

### R3. Cover crossfade is a 300 ms global default applied inside scrolling grids

`App.kt:397` sets a global crossfade, and `MangaCover` does not override it (`MangaCover.kt:51-58`
only overrides `precision`). Coil crossfades even when the bitmap came from the memory cache, so
every recycled cell in a fast fling can visibly fade — the "cover art flickers while scrolling"
complaint. Suggested: expose a `crossfade` parameter on `MangaCover` defaulting to off for list
callers and on for the single-cover detail header, or set `crossfade(false)` on the cover request and
rely on the placeholder.

### R4. Pager memory-cache key does not include the requested size

The webtoon request includes its decode width in the key; the pager request does not
(`ZoomableMangaPage.kt:434-437`). If R1 introduces a per-device decode cap, the key must include it,
or a device that decodes at 2x screen width and one that decodes at ORIGINAL would share entries and
one of them would render the other's resolution.

### R5. Cover `filterQuality = High` at grid sizes

`MangaCover.kt:117` uses `FilterQuality.High` for every cover including 100 dp cells. High-quality
filtering on large downscales is measurably more expensive during flings; `Medium` is usually
indistinguishable at cell sizes. Low priority, measure first.

---

## Verification plan

1. `macrobenchmark` scroll of a 500-item library on a LOW-tier device, before/after R3 (frame time).
2. Webtoon chapter read on a LOW-tier device with `dumpsys meminfo` sampled across 20 pages, before/
   after R1+R2 (graphics memory).
3. Cover cache-hit assertion: open a series, return, re-open with the network disabled — the cover
   must render from cache (validates R4 if R1 changes the size).