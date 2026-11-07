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
- **[executed 2026-11-06 — D1] `ADR-0017` is executed.** All seven targets are deleted:
  `SourceProfile.kt`, `SourceProfileCache.kt`, `SourceProfileStore.kt`,
  `RoomSourceProfileStore.kt`, `DynamicHttpSource.kt`, `RemoteSource.kt`,
  `ContentSourceOrchestrator.kt`, with the Room entity/DAO (database v5 drops `source_profiles`),
  the custom-source interactors and their tests. See the D1 row in §4.
- **[executed 2026-11-06 — A1] Media tuple wired.** `DecodePlanner`, `DecodePlan`,
  `SampleSizePolicy`, `RenderPathPolicy` and `AnimationPolicy` now have a production consumer:
  `ComposeWebtoonReader` threads `PageMetadata`/`PageSource`/`PageRenderPath` into
  `SlicedWebtoonImage` through the new pure `WebtoonSlicePlanner` (see the A1 row in §4).
- **[executed 2026-11-06 — A2] `ReaderSession` consumed.** `ReaderViewModel` now drives `ReaderSessionCoordinator` at every reader seam (open/loaded/failed, selection + settlement, menu, close), with `ReaderSessionEffectLog` acknowledging effects, and the prev/next window is selected through `ReaderChapterWindowAdapter` and the domain `ReaderChapterWindowPolicy`. See the A2 row in §4.
- **`IngestEngine` still greenfield** (own file + test only).
- **[reconciled 2026-11-08 — D3] The five "DI-orphans" are unwired by decision, not by neglect.**
  `TargetSeriesRepository`, `TargetMigrationWriter`, `LegacyRoomMigrationAdapter` and
  `ReconcileSourceRegistry` are the isolated target-data/source-lifecycle path
  [`TARGET_DATA_CUTOVER.md`](TARGET_DATA_CUTOVER.md) (`DATA-001H`) deliberately keeps out of
  production until its 14 cutover gates are evidenced (`DATA-001I`): binding them would cross the
  recorded rollback boundary ("Target writes are not allowed from production UI/background
  workers"), and deleting them would discard the staged future authority and its rehearsal
  fixtures. `verifyDownloadArtifact` stays re-scoped to `DATA-001I` blocker 2 (no
  `DownloadArtifactIndex` implementation exists, so a call site would compute-and-discard).
  Resting state: `ACCEPTED_DEBT` with the `DATA-001I` gates as the review condition — not the
  `B-032` orphan class.
- **[re-audited 2026-11-08 — D4] The `B-032` wiring sequence is closed; the remaining unwired
  classifications are corrected in place.** `ReaderTapSequencer` and the pointer adapter are
  production-wired in both readers (`pagerGestureStream` and `webtoonGestureStream` both run
  `detectReaderGestures`, the sole producer of `SingleTap`), the "built-ahead entry cluster"
  (`BookReaderViewModel`, the launchers, `ReaderFeatureApi`, `ReaderModule`, `ChapterStartRequest`)
  is wired scan-invisibly (Hilt `@IntoSet` multibindings through `MediaViewerRegistry` and
  `MainActivity`, plus function references in `ReaderViewModel`), `RendererSelection`'s four-kind
  dispatch is kept by decision under `ADR-0008` (wiring belongs to the four-content-type plan),
  and `IngestEngine` remains the acknowledged greenfield. No production code changed.
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
| A1 | Wire the media tuple (`PageSource`/`PageMetadata`/`DecodePlan`/`SampleSizePolicy`/`RenderPathPolicy`/`AnimationPolicy`) into `SlicedWebtoonImage` against `BitmapRegionDecoder`. | Done 2026-11-06: the call site threads the tuple into `SlicedWebtoonImage` via the new pure `WebtoonSlicePlanner`; `sliceSourceRect`/`isSliceRectValid` are consumed (crop-aware geometry), `DecodePlanner` records the page plan, `SampleSizePolicy` hysteresis is clamped to the width-fit rule, and `WebtoonRegionDecoder` takes the slice rect + planned sample. `E2` via `WebtoonSlicePlannerTest` on CI. `E3` slice render: BLOCKED naming the channel — no emulator/device in this environment (`B-050`), never claimed. Production passes `contentRect = null` (`BorderCropTransformation` exposes no rect), so rendered pixels are unchanged; the seam is the deliverable. | — |
| A2 | Migrate `ReaderSession` onto `ReaderViewModel` (consume `RDR-001`/`RDR-002`). | Done 2026-11-06: `ReaderViewModel` drives `ReaderSessionCoordinator` (session open before the load, `chapterLoaded` on success, `chapterFailed` at the three load-failure catch sites, selection recorded and settled in `onPageSelected`, `toggleMenu` in `showMenus`, `closeChapter` on activity finish; `sessionState` exposed) with `ReaderSessionEffectLog` acknowledging effects — each names work the dispatching path already performs. `selectPage` composite fixed: the old SelectPage-then-Move could never record direction (`SelectPage` lands in `LoadingPage`, `Move` requires `Ready`); adjacent moves now dispatch from `Ready`, jumps land direction-less by machine semantics. `ReaderChapterWindowAdapter` selects the window through the domain policy (positional SOURCE order, `skipReadForward` threaded, `-1.0` numbers to null, series-screen filters left upstream); feature-local sibling helpers and `ChapterNavigationPolicyTest` deleted, contracts moved to `ReaderChapterWindowAdapterTest`. `E2` on CI (`ReaderSessionCoordinatorTest` +4, `ReaderSessionWiringTest`, adapter suite); no JDK locally. `E4` pointer evidence NOT produced — not claimed. | — |
| A3 | Migrate `StartupReducer` onto `App.kt`; add the missing `RECONCILE_SOURCES` implementation the audit found absent (`B-034`). | Done 2026-11-06: `App.kt` drives `StartupReducer` through `StartupWorkflowHost` (callbacks remain the effect executor; rethrow paths never settle — the state dies with the process; the host owns rule-8 correlation since the terminal request is non-cancellable and never sits in the `EffectLedger`; every phase settles ok = true under its existing containment policy, with `Failed`/`Retry` as contract capability). `RECONCILE_SOURCES` added between `REACTIVE_BINDINGS` and `ASYNC_INIT` (pinned by a new `StartupStepTest` case) and implemented by `SourceReconciler`: primes the `SourceManager` map at startup, awaits `isInitialized` with a bounded 15s timeout, audits `SourceRegistry` against the loaded map — on the shipping source authority, not target-data `ReconcileSourceRegistry` (`DATA-001H` decision). `StartupGuard`/`StartupTracker` retained for crash attribution. `E2` on CI (`StartupWorkflowHostTest` (8), `SourceReconcilerTest` (7), `StartupStepTest` +1); no JDK locally. `E3`/`E4` startup timing evidence NOT produced — not claimed. | — |
| A4 | Complete `RDR-005`: continuous reader on the media tuple; one container transform. | Done in code 2026-11-07: A1 had already wired the tuple, so A4 took the remainder the Phase-7 entry recorded — the zoom path was wired inline in `ComposeWebtoonReader` with "no hostable path for gesture injection". Extracted verbatim into `Modifier.webtoonDocumentTransform` + `WebtoonZoomGestureHost`; the extraction closed a real defect: the inline call passed `committed = null` to `reduceWebtoonEffect`, so `GestureCancelled` never restored the committed transform the `WebtoonGestureAdapter` contract promises. Guarded at `E2` by real two-pointer pinch injection through the production `webtoonGestureStream` modifier (`WebtoonZoomInjectionTest`, the `B-022` harness) + `WebtoonZoomGestureHostTest`. `E3` re-capture on the extracted path: BLOCKED naming the channel (`B-050` — no emulator/device in this environment), never claimed; the `E4-lab` owner device leg is to re-run `WebtoonZoomRenderTest` + pinch capture on an attached device, recorded under `doc/evidence/`. No JDK locally — remote CI is the verification channel. | A1 |
| A5 | Resolve `RDR-003`'s `E4-user` obligation: produce it or record an owner waiver with an expiry. | `E4-user` or waiver | — |
| A6 | `RDR-004`/`RDR-005` replacement: delete the superseded reader paths once A1–A4 land. | Done 2026-11-07: the deletion had already happened at the cutovers (rule 7 was applied at replacement time — `detectPagerGestures`, `shouldClaimPagerTransform`, `detectWebtoonGestures`, `WebtoonViewportState`/`WebtoonViewport` per `ADR-0010`), so A6's work was the proof: the reachability scan ran at ceiling 0 and enumerated the complete 83-type inventory, every reader-area entry was audited per file (live-but-scan-invisible through Compose-only files, `ADR-0008` contracts awaiting a call site, or D3/D4's entry-cluster decision — none superseded; D4's re-audit found the sequencer/adapter and the entry cluster wired scan-invisibly and kept `RendererSelection` by decision), and the ceiling was tightened 99 → 83 (CU-2 closed). Record: `REBUILD_STATUS.md` change log, 2026-11-07. | A1–A4 |

### Track B — Source, data, ops completion (Phases 3, 12)

| ID | Action | Evidence | Depends on |
|---|---|---|---|
| B1 | Finish `ARC-002`: close the remaining dependency-graph rows. | `ModuleDependencyGraphTest` | — |
| B2 | `SRC-011` one owner for source existence/identity (`ADR-0012`); close `DEF-029`. | E2 landed 2026-11-06: registry + policies + consumer rewiring + ownership gate; `DEF-029`'s row was then deleted by D1 (2026-11-06) with the profile path, per `ADR-0017` — see the D1 row. | — |
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
| C6 | Performance device work: regenerate `baseline-prof.txt` on S24 — by the manual/scripted channels, since the `macrobenchmark` generator is retired (`B-051`, D2); only then tune P99. | `E5` measurement record | - |
| C7 | Navigation candidates: shared-cover for browse/search-to-details; decide the `motionPlanFor`-null gap. | `MotionConsistencyTest` | - |

### Track D - Legacy deletion & cleanup (the crispness work)

Reconciliation items from the merged-work audit are in [`2_0_CLEANUP_AUDIT.md`](2_0_CLEANUP_AUDIT.md) (CU-1..CU-8); absorb them into D1/D2/D3.

| ID | Action | Evidence | Depends on |
|---|---|---|---|
| D1 | **Execute `ADR-0017`:** delete `SourceProfile`, `SourceProfileCache`, `SourceProfileStore`, `RoomSourceProfileStore`, the Room entity + migration, `DynamicHttpSource`, `RemoteSource`, `ContentSourceOrchestrator`, `GetAvailableSources`'s profile branch, and `DEF-029`. | Done 2026-11-06: all seven targets deleted with the entity/DAO, the custom-source interactors and their tests; Room v5 (`MIGRATION_4_5`) drops `source_profiles`; `SourceType` and `ContentSourceEngine` survive on named reasons; remote CI is the verification channel (no JDK in the authoring environment) | B2 (identity owner must exist first) |
| D2 | `CLEAN-001`: delete superseded reader/navigation/media code; retire the unwired `macrobenchmark` module. | Done 2026-11-07: the superseded generations were already deleted at their cutovers — reader (verified by A6's ceiling-0 scan + per-file audit), navigation (the global mutable navigation singletons, deleted at `NAV-001`), media (the tile pipeline, retired by `ADR-0010`) — so the dependency/import scan found nothing left to delete, and the deliverable is the recorded retirement: the `macrobenchmark` module (6 files), its `ephyra.benchmark` convention plugin, the app `benchmark` build type, and the now-unused `benchmark-macro`/`test-uiautomator` catalog entries are gone (module count 29 → 28). Record: `REBUILD_STATUS.md` change log, 2026-11-07. | A6, D1 |
| D3 | Retire the five DI-orphans: bind + call, or delete. Decide per item; do not leave "tested, unwired". | Done 2026-11-08: decided per item — all five **stay isolated: unwired by decision, neither bound nor deleted**. The governing record is [`TARGET_DATA_CUTOVER.md`](TARGET_DATA_CUTOVER.md): `DATA-001H`'s rollback boundary forbids target writes from production UI/background workers and keeps the target path isolated until the 14 cutover gates are evidenced (`DATA-001I`), so binding `TargetSeriesRepository`/`TargetMigrationWriter`/`LegacyRoomMigrationAdapter`/`ReconcileSourceRegistry` would create the split-brain identity model the decision exists to prevent, and deleting them would discard the staged future authority and its rehearsal fixtures. `verifyDownloadArtifact` stays re-scoped to `DATA-001I` blocker 2 — `DownloadArtifactIndex` has no implementation, so a `Downloader` call site would compute the verification and discard it. The deliverable is the recorded reconciliation (§3 bullet, ROADMAP open queue, `REBUILD_STATUS.md` change log): the five are accepted debt under §2 item 7 with the `DATA-001I` gates as the expiry/review condition. No production code changed, by decision; docs-only change, CI green. | - |
| D4 | Re-audit the `B-032` contract surface; every remaining unwired contract is either wired or deleted. | Done 2026-11-08: the audit read the tree at file level and the deliverable is the corrections, not deletions — `ReaderTapSequencer` and the pointer adapter are production-wired in both readers (`pagerGestureStream`/`webtoonGestureStream` → `detectReaderGestures`, the sole `SingleTap` producer), the "entry cluster" (`BookReaderViewModel`, the launchers, `ReaderFeatureApi`, `ReaderModule`, `ChapterStartRequest`) is wired scan-invisibly (`@IntoSet` multibindings via `MediaViewerRegistry`/`MainActivity`, plus function references in `ReaderViewModel`), `RendererSelection`'s four-kind seam is kept by decision (`ADR-0008`; wiring belongs to the four-content-type plan), and the five target-data items are D3's `ACCEPTED_DEBT`. The `B-032` row's status cell is closed and `READER_GESTURE_CONTRACT.md` is corrected. No production code changed; docs-only, CI green. Record: `REBUILD_STATUS.md` change log, 2026-11-08. | wiring audit (read the tree, not a count) — done at file level | A1, D3 |
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
  has **no automated gate** and is measured by manual `E4-user` capture. The module itself is now retired (`D2`, 2026-11-07).
- **Target-data cutover (`DATA-001H`/`DATA-001I`)** - deferred by owner decision, not abandoned:
  the target-data cluster stays isolated until the 14 gates in
  [`TARGET_DATA_CUTOVER.md`](TARGET_DATA_CUTOVER.md) are evidenced; wiring earlier is forbidden by
  its rollback boundary. D3 (2026-11-08) records the cluster as accepted debt with those gates as
  the review condition. A *scheduled* deferral, the same class as `B-017`.

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
