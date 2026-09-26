# E4 Acceptance Runbook

> **Status:** binding evidence procedure for `E4`-level claims.
> **Authority:** [`ROADMAP.md`](../ROADMAP.md) → [`REBUILD_PROGRAM.md`](REBUILD_PROGRAM.md) → this document.
> **Device state:** see [`BUILD_HEALTH.md`](BUILD_HEALTH.md) § "E4 device availability".

This runbook exists because five defect rows and two fixture tasks are `CODE_COMPLETE` at `E2`
and owe device evidence, and because an informal screenshot is explicitly *not* evidence under
[ADR-0006](adr/0006-evidence-before-completion.md).

## 1. The rule this runbook enforces

A claim reaches `E4` only when all five are present:

1. the **exact user-reported reproduction**, not a proxy for it;
2. the **command or gesture** that was performed, written out;
3. **device identity** — model, API level, ABI, resolution, density, build SHA;
4. the **artifact** — screenshot, screen recording, or benchmark report;
5. a **verdict** — pass or fail — recorded against the defect row.

Missing any one of these, the row stays `CODE_COMPLETE` at `E2` with E4 outstanding. A partial
record is not a partial pass; it is no record.

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

If `adb devices` lists no device, **stop and record the blocker**. Do not substitute a JVM test
and do not mark the row complete.

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

## 6. Known open items in this runbook

- `TST-001C3` (JXL fixture) was `BLOCKED` because the only available codec is Android-native. With
  an emulator now attached, that runtime exists; the blocker reason is void. The fixture binary is
  immutable — hash-verify it before use and do not regenerate it.
- The local device is API 37 while CI boots API 35. These are complementary, not interchangeable.
  Both must be recorded where a claim spans API levels.
- The `doc/evidence/e4-01-home.png` file found in the working tree predates this runbook and has
  no sidecar. It must be given one or removed; an untracked image is not evidence.

## 7. Related

- [`REBUILD_STATUS.md`](REBUILD_STATUS.md) — evidence levels `E0`–`E5` and the task ledger.
- [`adr/0006-evidence-before-completion.md`](adr/0006-evidence-before-completion.md) — the decision this enforces.
- [`FIXTURE_MANIFEST.md`](FIXTURE_MANIFEST.md) — fixture identity; never invent a fixture ID.