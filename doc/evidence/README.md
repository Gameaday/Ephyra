# E4-lab evidence

Produced by the agent against an attached lab device, per `E4_ACCEPTANCE.md`. Every record here
must be re-derivable by re-running the listed steps. A record that cannot be reproduced is void
regardless of its verdict, and a step that could not be run is recorded as `BLOCKED` — never as a
pass.

## e4lab-reader-page1

| Field | Value |
|---|---|
| Capture date | 2026-09-26 |
| Commit | `85511edd2f9f` (working tree: the `DEF-010` fix) |
| Device | `sdk_gphone16k_x86_64`, API 37 (Android 17), x86_64, 1080x2424, density 420 |
| APK | `app-x86_64-debug.apk`, SHA-256 `03fa7f2be0dbf63023d36829db3034fc6ecc5518cdd59bb7f30c1c8edaf231ce` |
| Artifact | `e4lab-reader-page1.png`, SHA-256 `817c8321bb225037731b7dd4e867ea700996da2ba793e5d82c594a17b7703fa9`, 134771 bytes |
| Seed | `E4-Lab Series/chapter-001/001..006.jpg`, from `app/src/debug/assets/ephyra-e4lab/`, written by `E4LabFixtureReceiver` |
| Verdict | **PASS — the reader renders seeded fixture content on a real device** |

### Reproduction

```bash
adb install -r -d app/build/outputs/apk/debug/app-x86_64-debug.apk
adb shell am broadcast -a app.ephyra.dev.E4LAB_SEED \
  -n app.ephyra.dev/ephyra.app.debug.E4LabFixtureReceiver
# Discover -> Sources -> Local source -> E4-Lab Series -> chapter-001
```

### What this does and does not establish

**Establishes:** the reader opens, decodes and paginates the committed fixtures on a device; the
`DEF-010` fix is live — the Local source now lists `E4-Lab Series` where it previously spun
forever; the `E4-lab` capture path exists end to end and is repeatable.

**Does not establish:** `DEF-001` or `DEF-002`. Per `E4_ACCEPTANCE.md` §3 a screenshot cannot prove
a transform reaches the screen — those rows rest on the `E3` pointer tests
(`PagerViewportRenderTest`, `WebtoonZoomRenderTest`), and a future `E4-lab` record for them must
carry a recorded gesture or pointer trace, not this image.

`E4-user` remains deferred by owner decision. Nothing in this directory substitutes for it.

## e4lab-reader-controls-toggle

| Field | Value |
|---|---|
| Capture date | 2026-09-26 |
| Commit | `476e4e119` |
| Device | `sdk_gphone16k_x86_64`, API 37, x86_64, 1080x2424, density 420 |
| Gesture | Single tap at (540, 1200) in the reader, twice, separated by a `screencap` |
| Verdict | **PASS — a single tap toggles the reader controls, and the toggle round-trips** |

Observed: with the controls hidden a tap shows them (`C` and `E1` both capture the controls
visible, 175749 and 175742 bytes), and a second tap hides them again (`E2`, 167749 bytes, matching
the post-gesture frame `D` byte-for-byte by hash). This is the documented at-fit routing in
`ZoomableMangaPage`: at or below `ZoomPolicy.ZOOM_GATE` every tap routes, which is how the menu is
toggled at all.

## BLOCKED — pinch and double-tap `E4-lab` capture

`DEF-001` and `DEF-002` are **not** advanced by this directory, and the reason is a tooling limit
rather than a product one.

`adb shell input` synthesises one pointer per process invocation. It cannot place two taps inside
the 350 ms double-tap window — each `input` call is a separate process spawn, so an attempted
double-tap arrives as two independent single taps — and it has no multi-touch form at all, so a
pinch cannot be produced. Driving these gestures needs either raw `sendevent` sequences with
hand-timed event codes, or an instrumentation test that injects pointers.

Per `E4_ACCEPTANCE.md` §3 a screenshot cannot establish a transform reaching the screen. Rather
than relabel an uncontrolled frame as a zoom capture, those rows stay on their `E3` pointer tests:
`PagerViewportRenderTest` and `WebtoonZoomRenderTest`, which do inject real pointer events.

An earlier attempt in this session produced frames that could not be attributed to a specific
gesture — the menu overlay and any zoom were confounded. Those captures were discarded rather than
recorded, and this note exists so the next attempt does not repeat them.

