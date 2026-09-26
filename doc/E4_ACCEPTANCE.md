# Device Acceptance Runbook

> **Status:** binding evidence procedure for device-level claims.
> **Authority:** [`ROADMAP.md`](../ROADMAP.md) → [`REBUILD_PROGRAM.md`](../REBUILD_PROGRAM.md) →
> [`adr/0009`](adr/0009-evidence-channels-match-validation.md) → this document.
> **Covers:** `E4-lab` (in-tree device capture) and `E4-user` (validation on real user devices from a
> pushed build). Each ledger row declares which one it waits on.

This runbook exists because five defect rows and two fixture tasks are `CODE_COMPLETE` at `E2` and
owe device evidence, and because an informal screenshot is explicitly *not* evidence under
[ADR-0006](adr/0006-evidence-before-completion.md).

## 0. Which channel

This project validates by pushing a build and exercising it on **real user devices**. That is the
primary device channel and it is `E4-user`. `E4-lab` — agent-driven capture against an attached
emulator or lab device — is available and worthwhile for `E3` regression gates, but it is **not** a
default obligation, and no row is permitted to stay blocked waiting for it.

| Channel | Produced by | Use for |
|---|---|---|
| `E4-user` | the release/validation process on real devices | user-facing acceptance |
| `E4-lab` | an in-tree capture run (§2–§5) | deterministic reproduction, claim archaeology |

If a row needs a channel that is not available, it is `BLOCKED` naming the channel. It is never
`pass`. See §6.

## 1. The rule this runbook enforces

A claim reaches its declared device level only when all five are present:

1. the **exact user-reported reproduction**, not a proxy for it;
2. the **command or gesture** that was performed, written out;
3. **device identity** — for `E4-lab`, model, API level, ABI, resolution, density, build SHA; for
   `E4-user`, the build SHA and the device class;
4. the **artifact** — screenshot, screen recording, benchmark report, or validation record;
5. a **verdict** — pass or fail — recorded against the defect row.

Missing any one of these, the row stays `CODE_COMPLETE` at `E2` with the device level outstanding. A
partial record is not a partial pass; it is no record.

**A record may only describe a run that occurred.** If the reproduction cannot be re-performed by
someone else from the record, the record is void — regardless of whether the outcome was favourable.
A record of an idle screen is not a record of a gesture. A record whose fixture ID does not exist in
`ReaderFixtureCatalog` is not a record. This rule exists because it was broken once: five sidecars
were written claiming `verdict: "pass"` over recordings of a static screen, and the only thing that
caught it was rereading them.

**A claim that could regress silently again is gated at `E3`, not at a device level.** Device
validation confirms a behaviour; only a repeatable in-repository test guards it. Most reader claims
therefore target an `E3` pointer-event or screenshot test, with device validation as confirmation of
the specific defect.

## 2. Preconditions

`adb` must be resolvable. On this workstation the SDK is at
`%LOCALAPPDATA%\Android\Sdk` (per `local.properties`), but `platform-tools` is **not on `PATH`**, so
a bare `adb` fails in a non-interactive shell. Resolve it explicitly first:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"   # or wherever your SDK lives
if (-not (Test-Path $adb)) { $adb = (Get-Command adb -ErrorAction SilentlyContinue).Source }
if (-not $adb) { throw "adb not found: add platform-tools to PATH or set `$adb above" }

& $adb devices                                  # expect exactly one line, state `device`
& $adb shell getprop ro.build.version.sdk       # record
& $adb shell getprop ro.product.model           # record
& $adb shell getprop ro.product.cpu.abi         # record
& $adb shell wm size
& $adb shell wm density
& $adb shell pm list packages | findstr ephyra  # record applicationId
git --no-pager rev-parse HEAD                   # record the SHA under test
```

Use `& $adb` for the remainder of the run so the resolved path is not re-resolved by `PATH` lookup.

If `adb devices` lists no device, **stop and record the blocker for `E4-lab`**. Do not substitute a
JVM test, do not mark the row complete, and do not hold an `E4-user` row hostage to it: a row waiting
on `E4-user` proceeds through §6 regardless of whether a local device is attached.

## 3. What a screenshot can and cannot prove

This is the part most often done wrong, so it is stated as a hard rule.

| Claim | Screenshot sufficient? | Required evidence |
|---|---|---|
| Crop toggle enlarges content (`DEF-004`/`DEF-008`) | **Yes** | Before/after pair, same page, same zoom |
| Content renders, no crash, correct page order | **Yes** | Screenshot |
| Shared element resolves and does not duplicate | **Yes** | Before/after pair |
| **Pinch zoom is anchored to the fingers** (`DEF-001`, `DEF-002`) | **No** | Recorded gesture, or a pointer-event test |
| **Slices do not overlap under zoom** (`DEF-003`) | **No** | Video, or a coverage assertion at zoom |
| **Transition frame pacing** (`DEF-005`) | **No** | Screen recording or macrobenchmark |

A still frame cannot demonstrate focal invariance or temporal pacing. Claiming it does is the
"inspected a screenshot informally" pattern ADR-0006 lists as rejected. When a gesture or timing
claim is at stake, the screenshot is corroboration only, and the primary evidence is the recording
or the executable test.

## 4. Evidence artifact format

Store under `doc/evidence/`, named `<task-id>-<seq>-<slug>`, with a sidecar JSON of the same stem.

```text
doc/evidence/
  DEF-001-01-paged-pinch-anchored.png
  DEF-001-01-paged-pinch-anchored.json
```

Required sidecar fields — a file without these is not evidence and must not be committed:

```json
{
  "taskId": "DEF-001",
  "defectId": "DEF-001",
  "verdict": "pass",
  "recordedAt": "2026-09-25T00:00:00Z",
  "gitSha": "550644d07...",
  "buildType": "debug",
  "applicationId": "app.ephyra.dev",
  "device": {
    "serial": "emulator-5554",
    "avd": "Pixel_10",
    "model": "sdk_gphone16k_x86_64",
    "fingerprint": "google/sdk_gphone16k_x86_64/emu64xa16k:17/CP41.260828.004.A7/16296984:user/dev-keys",
    "apiLevel": 37,
    "abi": "x86_64",
    "screen": "1080x2424",
    "density": 420,
    "isEmulator": true
  },
  "fixture": {
    "catalogId": "<ReaderFixtureCatalog id>",
    "sha256": "<fixture hash>"
  },
  "reproduction": "One paragraph: exact steps taken, from app launch to observed result.",
  "command": "<adb / gradle command that produced the artifact>",
  "artifact": "DEF-001-01-paged-pinch-anchored.png",
  "primaryEvidence": "recording | pointer-test | screenshot | benchmark",
  "notes": "Anything a reviewer needs that is not inferable from the image."
}
```

`isEmulator: true` is mandatory honesty. An emulator is a *representative* device under ADR-0006,
not a physical one; a row closed on emulator evidence says so and is not generalised to hardware.

## 5. Recording the result

Update `doc/REBUILD_STATUS.md` **in the same commit as the evidence**:

- the defect row moves to `DEVICE_VERIFIED` only on `verdict: "pass"`;
- a `verdict: "fail"` **reopens the defect immediately** with the observed behaviour quoted, and
  does **not** carry a fix in the same commit. A reopened defect with an unreviewed speculative
  patch is worse than a reopened defect.
- the phase gate row lists what is still outstanding.

## 6. `E4-user` validation record

The primary channel. A pushed build is installed on real devices and exercised; this records what
was asked, what came back, and against which build. It is lighter than §4 on purpose: the artifact
is the user's report, not a file this repository can manufacture.

```text
doc/evidence/
  DEF-001-10-user-validation.md
```

```markdown
# DEF-001 — user-device validation

- Build: `<versionCode>` / `<versionName>`, commit `<sha>`
- Channel: release-candidate (or: nightly)
- Devices: <n> installs across <device classes, e.g. "Pixel 7a API 34, Galaxy S23 API 35">
- Asked: <the exact question, phrased so a user can answer it — "pinch to zoom on a page and confirm
  the content under your fingers stays under your fingers">
- Result: pass | fail | partial
- Observed: <what users reported, quoted>
- Follow-up: <defect reopened with a quoted observation, or none>
```

Rules specific to this channel:

- **The question is written before the run**, in user terms, not implementation terms. If a user
  cannot be asked the question, the row is not ready for `E4-user`.
- **Partial is a real result** and is recorded as such. "Two of three users saw the old behaviour"
  is a `partial` that reopens the defect, not a pass.
- **A fail does not carry a fix in the same commit.** Reopen the defect with the quoted observation
  and fix it separately, matching §5.
- `E4-user` covers the *shipped* surface. Unwired contract work is in no build and is therefore
  owed no `E4-user`; that is the coherent form of the ADR-0008 exception.

## 7. Known open items in this runbook

- `TST-001C3` (JXL fixture) is `IN_PROGRESS`. The only available codec is Android-native, so the
  fixture must be produced on a device runtime; the fixture binary is immutable — hash-verify it
  before use and do not regenerate it.
- The local device is API 37 while CI boots API 35. These are complementary, not interchangeable.
  Both must be recorded where a claim spans API levels.
- The reader composables are not yet hostable in an instrumentation test: `feature/reader` declares
  no `androidTest` compose dependencies, and only one instrumentation test exists in the repository.
  That blocks `E3` pointer-event tests, not `E4-user`. See §8.

## 8. Related

- [`REBUILD_STATUS.md`](REBUILD_STATUS.md) — evidence levels `E0`–`E5` and the task ledger.
- [`adr/0006-evidence-before-completion.md`](adr/0006-evidence-before-completion.md) — why a
  screenshot is not sufficient evidence.
- [`adr/0009-evidence-channels-match-validation.md`](adr/0009-evidence-channels-match-validation.md)
  — why the ladder is defined by producing system, and why a missing channel is a blocker.
- [`FIXTURE_MANIFEST.md`](FIXTURE_MANIFEST.md) — fixture identity; never invent a fixture ID.