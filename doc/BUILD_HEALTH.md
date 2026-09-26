# Build & Repository Health

> **Status:** binding program health contract. Structural gates and domain-purity gates run on every
> change; repository size and timing are measured on a schedule and are **not** gates.

Build time and repository health are a standing workstream, not a once-off check. The purpose is
that the end state is **structurally** better than the start, not merely intentionally better.

## The rule

> **A measurement is a metric until it earns promotion to a gate. Promotion requires a named
> antipattern.** A number becomes a gate only when it measures a violation we must ratchet down or
> prevent, **and** the rule is structural — asserting a property of the code's shape, so it cannot be
> satisfied by editing the number.

**Counts do not qualify, and are not gates here.** A count is always satisfiable by editing the
number, so it cannot detect anything about the product; it drifts for reasons unrelated to quality;
and during this programme it moves in both legitimate directions — file and module counts **rise**
while the replacement is built alongside the shipping app and **fall** at `CLEAN-001`. Test count in
particular must never gate, because deleting legacy tests after their replacements exist is a
required outcome, not a regression.

**If a metric matters enough to gate, write the structural rule that catches the actual defect.**
That is what `ModuleDependencyGraphTest` does for layering: it asserts graph *shape*, so retiring a
legacy edge satisfies it automatically instead of requiring a number to be edited, and a count of
dependency edges is therefore redundant.

## Gates

| Gate | Rule | Enforced by |
|---|---|---|
| Module layering | no core→feature, feature→feature, presentation→feature, or →`:app` edges | `ModuleDependencyGraphTest` |
| Declared debt does not drift | every tolerated edge is declared, and a stale declaration fails | `ModuleDependencyGraphTest`, `SigningSecretTest`, `ManifestPrivilegeTest` |
| Domain purity | no `android.*` imports in `core/domain` | `ArchitectureTest` + CI grep |
| No unwired data shortcuts | bounded `ephyra.data.*` imports in feature modules | CI grep |
| Signing credentials | no credential literal; no unacknowledged keystore | `SigningSecretTest` |
| Manifest privileges | no sensitive permission without a written justification and removal condition | `ManifestPrivilegeTest` |

### Every gate must be falsifiable

A gate that cannot fail is indistinguishable from a gate that is passing, so a green run proves
nothing on its own. Each gate therefore carries the property that makes it meaningful:

- **It must fail when the condition it forbids is introduced.** Verified by staging a deliberate
  violation and observing the failure, not by reading the assertion. `ModuleDependencyGraphTest` was
  falsification-tested on 2026-09-26 and **failed to fail** — see the correction below.
- **It must assert against a non-empty subject.** "No offenders" is vacuously true over an empty
  list, so `the dependency graph is non-empty` exists to distinguish *the graph is clean* from *the
  graph was not read*.
- **Its own helpers are asserted.** The edge extractor and the layer matcher have direct tests, so a
  regression in either cannot quietly turn the rules into no-ops.

## Correction, 2026-09-26: `ModuleDependencyGraphTest` was inert

Five of the six graph rules **could not fail.** Two independent defects combined:

1. The edge extractor was `projects\.([a-zA-Z][a-zA-Z0-9_]*)` followed by
   `filter { it.startsWith(":") }`. The repository writes every dependency in type-safe accessor form
   (`projects.core.data`), and that regex captures one segment — `projects.feature.reader` yielded
   `"feature"`, which does not start with a colon. Since the accessor form never contains a colon,
   **every real edge was discarded**, `allEdges()` was always empty, and all five rules asserted over
   an empty list.
2. The prefix filters compared `startsWith("core")` against coordinates like `:core:domain`. The
   leading colon meant no prefix test could ever match.

This is the failure mode `REBUILD_EXECUTION_GUIDE.md` §7 names: *a test that passes because behavior
is skipped is not passing.* It was found by staging a deliberate `core → feature` edge and observing
the gate stay green — not by reading the code.

**What the corrected gates then revealed**, having been invisible until now:

| Debt | Edges | Ledger |
|---|---:|---|
| `core:domain` → project dependencies | 3 | B-006 class |
| feature → feature | 24 | B-006 |

These are **declared individually**, with the same two-way staleness check `SEC-003` applies to
manifest permissions: a *new* edge fails, and a *stale* declaration fails. A count could not have
distinguished them, and could not have named which edge to remove. This is the concrete case for the
rule above.

## Metrics — recorded, never gating

Measured on a cadence, recorded here, never gating a change. Last measured 2026-09-26.

| Metric | Value | Expected direction |
|---|---:|---|
| Module count | 29 | may rise for a 2.0 quarantine module |
| Inter-project dependency edges | 166 | falls as `CLEAN-001` removes legacy edges |
| Main source files | 1219 | falls as legacy reader and routes are deleted |
| Test source files | 251 | falls as legacy tests are replaced; not a quality signal |
| TODO/FIXME markers | 14 | intentional debt belongs in `REBUILD_STATUS.md` |
| `@Deprecated` markers | 39 | each is a bridge this programme intends to remove |
| Release APK per ABI | 30.7 MiB | track release, not the ~126 MiB unminified debug build |
| Clean build time | **93s** | measured 2026-09-26 on a cold cache; see "Timing budgets" |

These figures are **informational**. A change to any of them is not a reviewable event unless it also
breaks a structural gate.

### How these are counted

The walk is `git ls-files`-based, not a filesystem walk. A filesystem walk needs an exclusion list for
every directory that can appear inside the repository — `build/`, `.git/`, and previously a duplicate
agent worktree — and such a list fails *open*, because an unseen tree is simply not checked.
`SigningSecretTest` is the sharp case: a keystore in an unscanned tree is exactly the leak it exists
to catch. `git ls-files` cannot see untracked or ignored trees at all, so the exclusion is structural
rather than remembered.

**Stated limitation:** if git cannot be executed the list is empty, and the gates pass without having
checked anything. That is acceptable for a repository-shape gate, whose real risk is noise across
~1,200 files, and both CI and developer checkouts necessarily have git. A future gate that becomes
load-bearing for security should assert the list is non-empty rather than rely on this.

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
| Clean build | **93s** | 300s | `:app:assembleDebug`, measured 2026-09-26 |

### How the clean-build number was taken

Recorded because a bare number is not reproducible, and an unreproducible measurement is a
guess with a decimal point.

- **2026-09-26**, `:app:assembleDebug`, Gradle 9.7.1 on JDK 21.0.12 LTS.
- **Host:** AMD Ryzen 7 9800X3D (8 cores / 16 threads), 61.6 GB RAM, `-Xmx8g`, `org.gradle.parallel=true`.
- **Cold means cold:** the Gradle build cache (`~/.gradle/caches/build-cache-1`, 10.4 GB at the
  time) was deleted *and* every module `build/` directory was removed before the run. The
  dependency cache (`modules-2`) was left in place, because re-downloading dependencies measures the
  network, not the build. The cache is repopulated afterwards, so a repeat run is warm and will be
  far quicker — this number is for the first build on a fresh machine or a cleared cache.
- **Result:** `BUILD SUCCESSFUL in 1m 33s`, five per-ABI debug APKs written, against a 300s budget.

**Caveat worth stating:** 93s is for a debug assembly on a high-core desktop. It is not a CI figure
and not a release figure. Treat it as evidence that the budget is generous rather than as a
performance claim about the project.

## Agent working rules

These exist because measurement showed the cost, not because of convention.

1. **Scope every Gradle gate to the modules actually touched.** Repo-wide `spotlessCheck` costs
   81.3s on a warm cache. Running it on every iteration is the single largest avoidable cost in
   this program's development loop.
2. **Prefer `:module:testDebugUnitTest --tests <FQCN>`** over full multi-module runs.
3. **Do not run repo-wide tasks to validate a local change.** Validate, then run the full gate
   once at the end of a slice.

## Why modules and edges still matter

Each module costs configuration time, KSP/Hilt aggregation, and IDE indexing. 29 modules for a
project of this size is already high. Adding a module should be the exception, justified by a
distinct build/runtime contract — not a convenient way to avoid a package boundary.

This is a reason to prefer a structural rule over a count, and it is why the module and edge numbers
above are metrics. A `maxModuleCount` would be satisfiable by raising the number, would fire for
legitimate work during the replacement, and would say nothing about whether the graph is sound. The
graph rules say something specific: they name *which* edge inverts the layering, and they stop
firing on their own as the debt is retired.

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