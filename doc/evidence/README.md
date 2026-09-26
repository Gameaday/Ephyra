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
