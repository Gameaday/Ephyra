# Reconstruction Fixtures and Evidence Manifest

> **Status:** binding fixture contract. `TST-001A` and `TST-001B` are code-complete; `TST-001C` and device evidence remain open.

## Manifest shape

```yaml
version: 1
fixtureId: webtoon-static-bordered-v1
kind: reader-page
media:
  format: jpeg
  width: 1080
  height: 24000
  borderPx: 12
  animated: false
  codec: static
expected:
  contentRect: [12, 12, 1056, 23976]
  requiresSlicing: true
  supportsCrop: true
  sourceModes: [online, downloaded, local]
  viewportModes: [paged, continuous]
```

## Required fixture classes

- static short page;
- static long webtoon;
- bordered page with measurable uniform border;
- noisy artwork where crop must decline;
- tall paged image;
- animated static-image-compatible page;
- GIF/WebP animation;
- JXL page;
- corrupt image;
- missing image;
- page with duplicate/changed source revision;
- compact, expanded, portrait, landscape, and foldable navigation scene;
- multi-pointer pinch sequence;
- predictive-back sequence.

## Evidence rules

Every fixture records:

- source commit/build SHA;
- expected geometry;
- supported modes;
- known failure behavior;
- test paths;
- device/API requirements;
- artifact location.

A fixture that is only a screenshot is not a complete fixture. A fixture without a test or acceptance reference is incomplete.

## Evidence levels

- `E1`: static inventory;
- `E2`: JVM/Robolectric test;
- `E3`: instrumentation/screenshot;
- `E4`: representative device;
- `E5`: macrobenchmark.

## Privacy

Fixtures must not contain real user tokens, cookies, private library data, or remote credentials. Synthetic pages and redacted recordings are preferred.