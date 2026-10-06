# Ephyra 2.0 — Completion & Cleanup Plan

> **Status:** forward plan (current). Supersedes the "Current truth" narrative and immediate queue
> in [`../ROADMAP.md`](../ROADMAP.md) where those disagree — this file was validated against the
> tree on 2026-10-05 and the roadmap was reconciled to match.
> **Authority:** [`../ROADMAP.md`](../ROADMAP.md) → [`REBUILD_PROGRAM.md`](REBUILD_PROGRAM.md) →
> this file → [`REBUILD_STATUS.md`](REBUILD_STATUS.md).
> **Baseline at writing:** `4f2f8e4` (2026-10-05). **Target baseline:** `2.0-clean` (defined below).

## 1. Why this file exists

The reconstruction programme accumulated two diverging tracks and a roadmap narrative that lagged
the code by roughly a week:

- **The 2.0 program track** — the phased architecture rebuild. Its contracts are largely built and
  tested, but a finite set of them is still not wired into production, and a superseded generation
  of code still ships beside its replacement.
- **The improvement-session track** (`doc/historical/IMPROVEMENT_SESSION_PLAN.md`, branch
  `cline/0crpxa7t`, merged as PR #286) — product work: navigation motion, Discover simplification,
  unified search, cache/cover fixes, performance, and the Jellyfin decision.

The roadmap's "Current truth" section described the state as of 2026-09-28. Several of its claims
are now false. This file states the validated current state and the ordered steps to a clean
baseline, so the next contributor does not have to re-derive it.

## 2. Definition of done — the `2.0-clean` baseline

The repository reaches `2.0-clean` when **all** of the following hold. This is the acceptance test
for the plan, not a wish list.

1. **One architecture.** No superseded reader, navigation, media, or source generation ships beside
   its replacement (`CLEAN-001`, `MED → RDR-004/005`).
2. **Every 2.0 contract is either wired or deleted.** No tested contract has zero production
   consumers (`B-032` class). "Verified but unwired" is not an allowed resting state.
3. **All 14 phase gates (`0`–`13`) are `VERIFIED`** in [`REBUILD_STATUS.md`](REBUILD_STATUS.md).
4. **Health budgets green and measured**, including a regenerated baseline profile and a
   clean-build number (`OPS-002`, `OPS-003`).
5. **Documentation is current and indexed** — [`doc/README.md`](README.md) lists every current
   document with an owner; superseded documents are historical, not competing for authority.
6. **The `2.0-clean` tag is cut** and `ROADMAP.md`'s baseline line points at it.
7. **Known, accepted debt is enumerated** with an expiry/review condition (`B-017` keystore
   rotation is the standing example).

## 3. Validated current state (2026-10-05)

Each line was checked against the tree, not read from a summary. Corrections to the roadmap are
marked **[corrected]**.

- **Deletion done:** `DocumentViewport`, `DocumentTilePartition`, `TileScalePolicy`,
  `WebtoonViewport` no longer exist. `ADR-0010` is executed.
- **`MED-002` byte store is production-wired** via `feature/reader/.../model/PageByteStoreOwner.kt`.
- **[corrected] `ADR-0017` is accepted but not executed.** All seven targets still exist:
  `SourceProfile.kt`, `SourceProfileCache.kt`, `SourceProfileStore.kt`,
  `RoomSourceProfileStore.kt`, `DynamicHttpSource.kt`, `RemoteSource.kt`,
  `ContentSourceOrchestrator.kt`. The profile path is still live code that cannot execute — the
  exact condition the ADR was written to remove. This is the single largest cleanup item.
- **Media tuple still unwired:** `DecodePlanner`, `DecodePlan`, `SampleSizePolicy`,
  `RenderPathPolicy`, `AnimationPolicy` have no consumers outside `core/domain`.
- **`ReaderSession` still unconsumed** — it lives in `feature/reader/session/` but nothing uses it.
- **`IngestEngine` still greenfield** (own file + test only).
- **Five DI-orphans still unwired:** `TargetSeriesRepository`, `TargetMigrationWriter`,
  `LegacyRoomMigrationAdapter`, `ReconcileSourceRegistry`, `verifyDownloadArtifact`.
- **[corrected] Sourcing Phase 4's "open decision" is resolved** — `ADR-0017` chose deletion. What
  remains is execution, not a decision.
- **Improvement session progress:** navigation motion done; Discover Steps 1/2/4 landed; unified
  search **Stage A** landed (`df25f94`); cache/cover and performance fixes landed; Jellyfin not
  started.
- **Phase-gate drift [corrected]:** Phase 12's cell says `SRC-004`/`SRC-005` are open, but the task
  ledger records both `CODE_COMPLETE`. Phase 13 lists `DOC-001`, which is `CODE_COMPLETE`.

## 4. The plan

Step IDs are stable and may be used in commit subjects. Each step names its evidence gate; a step
is not done until that evidence exists. `E2` = JVM/Robolectric, `E3` = emulator/pointer/screenshot,
`E4-lab`/`E4-user` = device, `E5` = benchmark (see [`E4_ACCEPTANCE.md`](E4_ACCEPTANCE.md)).

### Track A — Reader & media completion (Phases 5, 6, 8)

| ID | Action | Evidence | Depends on |
|---|---|---|---|
| A1 | Wire the media tuple (`PageSource`/`PageMetadata`/`DecodePlan`/`SampleSizePolicy`/`RenderPathPolicy`/`AnimationPolicy`) into `SlicedWebtoonImage` against `BitmapRegionDecoder`. | `E2` decode-plan tests + `E3` slice render | — |
| A2 | Migrate `ReaderSession` onto `ReaderViewModel` (consume `RDR-001`/`RDR-002`). | reducer-table tests + wiring test | — |
| A3 | Migrate `StartupReducer` onto `App.kt`; add the missing `RECONCILE_SOURCES` implementation the audit found absent (`B-034`). | startup tests | — |
| A4 | Complete `RDR-005`: continuous reader on the media tuple; one container transform. | `E3` + `E4-lab` capture | A1 |
| A5 | Resolve `RDR-003`'s `E4-user` obligation: produce it or record an owner waiver with an expiry. | `E4-user` or waiver | — |
| A6 | `RDR-004`/`RDR-005` replacement: delete the superseded reader paths once A1–A4 land. | import/dependency scan | A1–A4 |

### Track B — Source, data, ops completion (Phases 3, 12)

| ID | Action | Evidence | Depends on |
|---|---|---|---|
| B1 | Finish `ARC-002`: close the remaining dependency-graph rows. | `ModuleDependencyGraphTest` | — |
| B2 | `SRC-011` one owner for source existence/identity (`ADR-0012`); close `DEF-029`. | `SourceResolutionDiagnosticsTest` + device reproduction | — |
| B3 | `SRC-012` MangaDex-shaped gateway adapter; fix the producer named by the sourcing Phase 1 repro. | capability + typed-outcome tests; `ImageUrlPolicyResolveTest` | B2 |
| B4 | `DATA-001` clean-slate schema policy. Decide the `SChapter.chapter_number: Float` question (`DEF-017`): widen to `Double` **or** keep the tolerant `ChapterNumber` policy — with a test either way. | schema tests | — |
| B5 | `OPS-001` one startup state model; split `Application` responsibilities. | startup tests | A3 |
| B6 | Finish `SRC-007`/`SRC-009`/`SRC-010` product wiring (search ViewModel migration, persistence/navigation handlers). | product wiring tests | — |

### Track C - Product / improvement completion

| ID | Action | Evidence | Depends on |
|---|---|---|---|
| C1 | Unified search v1 Stages B-E: availability-first, paired add + undo, lazy quality scoring, removals. | `E2` + `E3` per stage | - |
| C2 | Land `RFC-0001` Work/Binding identity. **Resolve open question O2 (unified progress model) first** - it gates multi-provider bindings. | RFC accepted + migration tests | C1 |
| C3 | Jellyfin provider (`JellyfinEngine` + `JellyfinAdapter`), consumption-only v1 (D7). | `ContentConformance` pass + live-server manual | C2, D1 |
| C4 | Discover Steps 3 (updates chip), 5 (etiquette hardening), 6 (structural tests). | structural tests | C1 |
| C5 | Cache queue: eager prior-revision cover delete; migration-source-change chapter-cache orphaning. | `E2` + `E3` | - |
| C6 | Performance device work: regenerate `baseline-prof.txt` on S24; run `ScrollBenchmark`/`StartupBenchmark`; only then tune P99. | `E5` benchmark record | - |
| C7 | Navigation candidates: shared-cover for browse/search-to-details; decide the `motionPlanFor`-null gap. | `MotionConsistencyTest` | - |

### Track D - Legacy deletion & cleanup (the crispness work)

Reconciliation items from the merged-work audit are in [`2_0_CLEANUP_AUDIT.md`](2_0_CLEANUP_AUDIT.md) (CU-1..CU-8); absorb them into D1/D2/D3.

| ID | Action | Evidence | Depends on |
|---|---|---|---|
| D1 | **Execute `ADR-0017`:** delete `SourceProfile`, `SourceProfileCache`, `SourceProfileStore`, `RoomSourceProfileStore`, the Room entity + migration, `DynamicHttpSource`, `RemoteSource`, `ContentSourceOrchestrator`, `GetAvailableSources`'s profile branch, and `DEF-029`. | compile + `SourceApiBoundaryTest` + migration tests | B2 (identity owner must exist first) |
| D2 | `CLEAN-001`: delete superseded reader/navigation/media code; retire the unwired `macrobenchmark` module. | dependency/import scan; build green | A6, D1 |
| D3 | Retire the five DI-orphans: bind + call, or delete. Decide per item; do not leave "tested, unwired". | `LedgerWiringClaimTest` + build | - |
| D4 | Re-audit the `B-032` contract surface; every remaining unwired contract is either wired or deleted. | wiring audit (read the tree, not a count) | A1, D3 |
| D5 | Documentation cleanup: archive completed session/audit docs; keep the index current (`DOC-001`). | `doc/README.md` + link check | - |

### Track E - Baseline freeze

| ID | Action | Evidence | Depends on |
|---|---|---|---|
| E1 | `REL-001`: enforce lint, instrumentation, release-artifact gates on the release path. | CI run | D2 |
| E2 | Record the accepted-debt register with expiry conditions (`B-017` first). | ledger row | - |
| E3 | Re-measure and record health numbers (`OPS-002`, `OPS-003`) at the frozen tree. | `BUILD_HEALTH.md` | D2 |
| E4 | Cut the `2.0-clean` tag; point `ROADMAP.md`'s baseline line at it. | git tag + roadmap diff | all above |

## 5. Blockers and decisions that are not more analysis

- **`B-017` keystore rotation** - owner-deferred to a post-programme project (rotation invalidates
  nightly update signatures). Phase 1 cannot read `VERIFIED` until it runs. This is a *scheduled*
  gap, not an oversight.
- **`B-050` CI emulator** - the API 35 image does not boot on GitHub runners, so device runs are
  local. `ADR-0009` accepts local `E4-lab`; the loss is automated re-verification, stated openly.
- **Device availability** - Tracks A4, C6 and E3 need an attached emulator/device. Without one they
  are `BLOCKED` naming the channel, never `pass` (`ADR-0009`).
- **O2 unified progress model** - needs a short RFC before multi-provider bindings ship (C2).
- **`DATA-001` type decision** - widen the source model or keep the tolerant policy (B4). One
  decision, applied uniformly.
- **`B-043`/`TST-003`** - macrobenchmark was dropped by owner decision; the frame-deadline budget
  has **no automated gate** and is measured by manual `E4-user` capture.

## 6. Recommended sequencing

```text
Immediate (no blockers):   B1, B2, B4, D1, D3, D5, C5, C7
Reader/media (the core):   A1 -> A2 -> A3 -> A4 -> A6 -> D2
Product:                   C1 -> C4 -> C2 (after O2) -> C3
Data/ops:                  B3 (after B2), B5 (after A3), B6
Device-bound:              A5, C6, E3
Freeze:                    D2/D4 complete -> E1 -> E2 -> E3 -> E4
```

The critical path to a clean baseline is **D1 (execute ADR-0017) -> A1-A4 -> D2 (delete the
superseded generation) -> D3/D4 (no unwired contracts) -> E4 (tag)**. Everything else can proceed in
parallel because it does not own the same files or state models.
