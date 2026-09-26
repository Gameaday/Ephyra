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

## e4lab-def-001-pinch-transform

| Field | Value |
|---|---|
| Capture date | 2026-09-26 |
| Commit | `954fdee0e` (`test(reader): B-041 resolved -- a real pinch, on a real device`) |
| Device | `emulator-5554` — `sdk_gphone16k_x86_64`, API 37 (Android 17), x86_64, 1080x2424 |
| Gesture | Real two-finger pinch, injected as `MotionEvent`s through Compose — **not** `adb shell input` |
| Harness | `PagerGestureInjectTest` (`feature/reader/src/androidTest/.../PagerGestureInjectTest.kt`) |
| Verdict | **PASS — a real pinch produces a real transform, and that transform reaches the screen** |

### Observed

```
def-001 pinch: scale 1.0 -> 5.0; raster 1080x2424/26 stripes -> 5400x12120/25 stripes; effects=28
```

The viewport left fit scale, and the captured raster grew by exactly the same factor (5x) in both
dimensions. 28 arbiter effects were observed: one `TransformStarted`, 25 `TransformUpdated`, one
`TransformCommitted`, plus the delegated effect.

### Reproduction

```bash
./gradlew :feature:reader:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=ephyra.feature.reader.viewer.pager.PagerGestureInjectTest'
# measurements are logged under the E4Lab tag in the preserved logcat:
# feature/reader/build/outputs/androidTest-results/connected/debug/*/logcat-*PagerGestureInject*.txt
```

### Falsification

The record is only worth something because the assertion bites. Replacing the production seam
`pagerZoomLayer(state.transform)` with an identity layer left the **scale still moving to 5.0** while
the raster stayed at `1080x2424`, and the test reported:

> A changed scale with an unchanged raster is the exact DEF-001 defect: state moved and nothing drew.

That is the reported defect itself, caught at the reported seam.

### What this establishes, and what it does not

**Establishes:** that a real multi-finger pinch, delivered to the real `pagerGestureStream` on real
hardware, produces a `PagerViewportState` transform **and** that transform is rendered. This is the
link `E4_ACCEPTANCE.md` §3 says a screenshot cannot provide, and it is the link that was previously
declared unproducible by `B-041`.

**Does not establish:** anything about `DEF-002`. The continuous reader's zoom is wired inline in
`ComposeWebtoonReader` rather than through an extractable adapter, so it has no equivalent hostable
path, and `WebtoonZoomRenderTest` remains a static-transform test. `DEF-002` stays on its own `E3`
evidence.

**Frames are not durable.** Gradle uninstalls both APKs when a connected run ends, so the before and
after PNGs written to the device are deleted with them. The measurements are logged instead. A
durable image would need the APKs retained across the run, which is not wired.
