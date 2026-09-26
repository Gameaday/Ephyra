# Build & Repository Health

> **Status:** binding program health contract. Structural gates and domain-purity gates run on every
> change; repository size and timing are measured on a schedule.

Build time and repository health are a standing workstream, not a once-off check. The purpose is
that the end state is **structurally** better than the start, not merely intentionally better.

## The rule

A check earns the right to fail a build only if it is **structural** — asserting a property of the
code's shape, so it cannot be satisfied by editing a number — or if it detects a **regression the
programme is not trying to cause**.

| Gate | Rule | Enforced by |
|---|---|---|
| Module layering | no core→feature, feature→feature, presentation→feature, or →`:app` edges | `ModuleDependencyGraphTest` |
| Domain purity | no `android.*` imports in `core/domain` | `ArchitectureTest` + CI grep |
| No unwired data shortcuts | bounded `ephyra.data.*` imports in feature modules | CI grep |
| Signing credentials | no credential literal; no unacknowledged keystore | `SigningSecretTest` |
| Manifest privileges | no sensitive permission without a written justification and removal condition | `ManifestPrivilegeTest` |

**Repository size is not gated.** Module count, dependency-edge count, main source files, test source
files, TODO/FIXME markers, and `@Deprecated` markers are measured on a schedule and recorded below.

A count is a poor gate for three reasons: it is always satisfiable by editing the number, so it
cannot detect anything about the product; it drifts for reasons unrelated to quality; and the 2.0
programme legitimately increases file and module counts while it builds the replacement alongside the
shipping app, then decreases them at `CLEAN-001`. Test count in particular must not be gated, because
deleting legacy tests after their replacements exist is a required outcome, not a regression.

**If a metric matters enough to gate on, write a structural rule.** That is what
`ModuleDependencyGraphTest` does for layering, and why counting edges is redundant: the graph rules
catch the actual defect and cannot be satisfied by changing a number.

## Scheduled measurements

Measured on a cadence, recorded here, never gating a change. Last measured 2026-09-26.

| Metric | Value | Expected direction |
|---|---:|---|
| Module count | 29 | may rise for a 2.0 quarantine module |
| Inter-project dependency edges | 166 | falls as `CLEAN-001` removes legacy edges |
| Main source files | 1219 | falls as legacy reader and routes are deleted |
| Test source files | 233 | falls as legacy tests are replaced; not a quality signal |
| TODO/FIXME markers | 14 | intentional debt belongs in `REBUILD_STATUS.md` |
| `@Deprecated` markers | 39 | each is a bridge this programme intends to remove |
| Release APK per ABI | 30.7 MiB | track release, not the ~126 MiB unminified debug build |
| Clean build time | not yet measured | `OPS-002` owes this number |

The file and module walk behind these measurements must exclude `build/`, `.git/`, and `.kilo/`.
`.kilo/worktrees/<name>/` is a complete second copy of the repository; counting it reports the
project as roughly doubled.

## `E4-lab` device availability (re-verified 2026-09-25)

**`E4-lab` evidence is reachable on this workstation.** A working emulator is attached and the
application is installed on it.

`E4-lab` is the in-tree capture channel. It is available but optional; the primary device channel is
`E4-user`, validation on real user devices from a pushed build. See
[`adr/0009`](adr/0009-evidence-channels-match-validation.md).

Verified state:

| Fact | Value | Command |
|---|---|---|
| `adb` | 1.0.41 (37.0.1-15733141) | `adb version` |
| Emulator | 37.2.11.0 (build 16416033) | `emulator -version` |
| AVD | `Pixel_10` | `emulator -list-avds` |
| Device | `emulator-5554`, state `device` | `adb devices` |
| API / release | 37 / 17 | `adb shell getprop ro.build.version.sdk` |
| ABI | `x86_64` | `adb shell getprop ro.product.cpu.abi` |
| Model | `sdk_gphone16k_x86_64` | `adb shell getprop ro.product.model` |
| Fingerprint | `google/sdk_gphone16k_x86_64/emu64xa16k:17/CP41.260828.004.A7/16296984:user/dev-keys` | `adb shell getprop ro.build.fingerprint` |
| Display | 1080x2424 @ 420 dpi | `adb shell wm size`, `adb shell wm density` |
| App installed | `app.ephyra.dev` | `adb shell pm list packages \| findstr ephyra` |
| Bootloader | emulator (`ro.kernel.qemu` = 1) | `adb shell getprop ro.kernel.qemu` |

### Residual limitations, stated precisely

These are still true and must not be reported as solved:

- `cmdline-tools` is **absent**, so there is no `avdmanager` and no `sdkmanager`. A new AVD or a
  new system image cannot be installed from the command line. The existing `Pixel_10` AVD is the
  only bootable target here.
- This is an **emulator on x86_64**, not physical hardware. ADR-0006 reserves E4 for
  "physical-device or representative-device evidence"; an AVD is a representative device, so E4
  claims must record the model and ABI and must not be generalised to physical hardware.
- The API 37 device is **not** the API 35 emulator the execution guide pins and that
  `connected-instrumentation.yml` boots in CI. Local E4 runs and CI E3 runs therefore cover
  different API levels. Both must be recorded; neither substitutes for the other.

### What this changes

Seven ledger rows were waiting on evidence that this workspace could not produce. That reason is
gone, so `TST-002`, `TST-001C3`, and the `E4` acceptance obligation on `DEF-001`, `DEF-002`,
`DEF-003`, `DEF-005`, and `DEF-008` are actionable now. See [`E4_ACCEPTANCE.md`](E4_ACCEPTANCE.md)
for the runbook and evidence format.

Recording this is not a licence to claim completion. A device being reachable raises the
*obligation* to produce device evidence; it does not satisfy it. Until the runs are executed and
recorded, those rows stay `CODE_COMPLETE` at `E2` with E4 outstanding, and no phase advances.

### Tier B — timing budgets (measured on a schedule)

Build times vary by machine, cache state, and load, so they are recorded and alerted rather than
gated per-PR. They still inform every architectural decision.

| Measurement | Observed | Budget | Note |
|---|---:|---:|---|
| `:core:domain:compileDebugKotlin` (warm) | 15.5s | 25s | Typical pure-contract module |
| `:core:domain:testDebugUnitTest` (warm) | 15.3s | 30s | |
| `:feature:reader:testDebugUnitTest` (warm) | 44.0s | 60s | Largest existing suite |
| `:app:compileDebugKotlin` (warm) | 40.8s | 60s | |
| Repo-wide `spotlessCheck` | 81.3s | n/a | **Not a budget. See "Agent working rules".** |
| Clean build | not yet measured | 300s | Establish on a cold cache |

## Agent working rules

These exist because measurement showed the cost, not because of convention.

1. **Scope every Gradle gate to the modules actually touched.** Repo-wide `spotlessCheck` costs
   81.3s on a warm cache. Running it on every iteration is the single largest avoidable cost in
   this program's development loop.
2. **Prefer `:module:testDebugUnitTest --tests <FQCN>`** over full multi-module runs.
3. **Do not run repo-wide tasks to validate a local change.** Validate, then run the full gate
   once at the end of a slice.

## Why the module count is a ceiling

Each module costs configuration time, KSP/Hilt aggregation, and IDE indexing. 29 modules for a
project of this size is already high. Adding a module should be the exception, justified by a
distinct build/runtime contract — not a convenient way to avoid a package boundary.

The same logic applies to dependency edges: a new `projects.*` edge is usually a layering leak and
directly increases build coupling.

## Bounded scope

Build optimization is a classic way to consume an entire program and ship nothing. The finish
line is explicit:

1. Establish budgets once (this document).
2. Work until budgets are met.
3. **Stop optimizing and return the remaining effort to the reader and product work.**
4. Ratchets then *prevent regression*; they do not invite further optimization.

## Measurement procedure

Timing must be comparable or it is noise.

- Warm Gradle daemon, warm build cache.
- Same machine, no other heavy processes.
- Median of three runs; discard the first cold run.
- Record Gradle version, JDK, and the commit SHA alongside the number.
- Clean-build numbers require a cleared build directory and are recorded separately.

## Related

- [`PERFORMANCE_BUDGETS.md`](PERFORMANCE_BUDGETS.md) — runtime frame and memory budgets.
- [`REBUILD_STATUS.md`](REBUILD_STATUS.md) — `OPS-002` and `OPS-003` task rows and the phase-gate health column.