# Ephyra Reconstruction Program

> **Authoritative status:** active program
> **Baseline:** `4ec5b2c15` (`nightly-43-g4ec5b2c15`)
> **Last verified:** 2026-09-25
> **Target:** Android-native Ephyra 2.0 with explicit state, resource, navigation, and media ownership

This file is the single entry point for the reconstruction program. If another document disagrees with this file, the disagreement is a documentation defect and must be corrected before implementation continues.

## Program documents

1. [`doc/REBUILD_PROGRAM.md`](doc/REBUILD_PROGRAM.md)  complete phased architecture and delivery plan.
2. [`doc/REBUILD_EXECUTION_GUIDE.md`](doc/REBUILD_EXECUTION_GUIDE.md)  operating procedure for humans and coding agents.
3. [`doc/REBUILD_STATUS.md`](doc/REBUILD_STATUS.md)  verified baseline, open defects, and task status ledger.
4. [`doc/CACHE_POLICY.md`](doc/CACHE_POLICY.md)  current cache invariants; preserve or supersede them through an ADR.
5. [`doc/SOURCE_DISCOVERY_ARCHITECTURE.md`](doc/SOURCE_DISCOVERY_ARCHITECTURE.md)  current source, search, ranking, compatibility, and discovery contract.
6. [`doc/SOURCE_DISCOVERY_EXECUTION.md`](doc/SOURCE_DISCOVERY_EXECUTION.md)  source/search implementation order and task decomposition.
7. [`doc/READER_ARCHITECTURE.md`](doc/READER_ARCHITECTURE.md)  reader session/state/resource contract.
8. [`doc/READER_GESTURE_CONTRACT.md`](doc/READER_GESTURE_CONTRACT.md)  pointer state machine and gesture ownership.
9. [`doc/DOCUMENT_VIEWPORT_CONTRACT.md`](doc/DOCUMENT_VIEWPORT_CONTRACT.md)  document coordinates, tiles, and viewport behavior.
10. [`doc/MEDIA_PIPELINE_CONTRACT.md`](doc/MEDIA_PIPELINE_CONTRACT.md)  source, geometry, decode, and artifact contracts.
11. [`doc/NAVIGATION_CONTRACT.md`](doc/NAVIGATION_CONTRACT.md)  one graph, typed routes, and back behavior.
12. [`doc/MOTION_NAVIGATION_CONTRACT.md`](doc/MOTION_NAVIGATION_CONTRACT.md)  route-pair motion and predictive-back policy.
13. [`doc/FIXTURE_MANIFEST.md`](doc/FIXTURE_MANIFEST.md)  fixture schema and evidence levels.
14. [`doc/PERFORMANCE_BUDGETS.md`](doc/PERFORMANCE_BUDGETS.md)  initial resource and frame targets.
15. [`doc/DEPENDENCY_TARGET_GRAPH.md`](doc/DEPENDENCY_TARGET_GRAPH.md)  allowed and forbidden dependency flow.
16. [`doc/DOCUMENTATION_GOVERNANCE.md`](doc/DOCUMENTATION_GOVERNANCE.md)  current, historical, archived, and deleted-document policy.
17. [`doc/adr/`](doc/adr/)  accepted architectural decisions that must not be silently reversed.
18. [`doc/source/SOURCE_INVENTORY.md`](doc/source/SOURCE_INVENTORY.md)  `SRC-000` target/adapter/compatibility/delete classification.
19. [`doc/source/SOURCE_CALL_GRAPH.md`](doc/source/SOURCE_CALL_GRAPH.md)  current source/search paths and replacement owners.
20. [`doc/source/SOURCE_COMPATIBILITY_MATRIX.md`](doc/source/SOURCE_COMPATIBILITY_MATRIX.md)  explicit compatibility decisions and deletion blockers.
21. [`doc/source/SOURCE_REMOVAL_PLAN.md`](doc/source/SOURCE_REMOVAL_PLAN.md)  ordered legacy bridge removal gates.
22. [`doc/E4_ACCEPTANCE.md`](doc/E4_ACCEPTANCE.md)  device-evidence runbook, artifact format, and what a screenshot may and may not prove.
23. [`doc/BUILD_HEALTH.md`](doc/BUILD_HEALTH.md)  repository health ratchets, timing budgets, and verified device availability.

## Program outcome

Build a modern Android application in which:

- every state has one authoritative owner;
- every visible setting has an end-to-end effect;
- rendering, persistence, and navigation do not share mutable ownership;
- source bytes, decoded images, and render artifacts have distinct bounded lifecycles;
- the reader has one gesture arbiter per viewport;
- the continuous reader uses document coordinates rather than per-item transforms;
- the main application has one navigation owner;
- Material 3 defines interaction semantics without forcing specialized media behavior into generic components;
- correctness is proven by executable contracts, instrumentation, screenshots, and benchmarks rather than optimistic comments;
- the app is useful with zero legacy extensions installed; the Tachiyomi/Mihon API is a temporary compatibility boundary;
- source adapters expose capabilities and typed outcomes; empty results are not failures;
- search is progressive, cancellable, bounded, provenance-preserving, and deterministically ranked;
- source health and migration are observable, explicit, reversible, and non-destructive by default;
- no feature imports legacy source types, service locators, or concrete adapters directly;
- documentation has one authority order; superseded “completed” documents are deleted or clearly historical.

## Non-negotiable rules

1. **Do not declare a device behavior complete from code inspection or JVM tests alone.**
2. **Do not add a workaround on top of an unresolved ownership conflict.**
3. **Do not preserve an option merely because it exists in the current UI.**
4. **Do not introduce another cache layer without a documented identity, budget, and invalidation rule.**
5. **Do not allow features to depend on data implementations or on each other without an explicit allowlist.**
6. **Do not transform LazyColumn webtoon items vertically; continuous zoom belongs to a document viewport.**
7. **Do not keep two active reader/navigation architectures after replacement.**
8. **Every phase must compile, pass its automated gates, update status evidence, and remain independently reviewable.**

## Phase summary

| Phase | Outcome | Exit gate |
|---|---|---|
| 0 | Freeze and establish truth | Reproducible baseline and defect ledger |
| 0A | Technical contracts | Reader, gesture, viewport, media, navigation, motion, source, fixture, budget, and dependency contracts are executable |
| 1 | Security and platform safety | No hardcoded secrets or unjustified exemptions |
| 2 | Quality infrastructure | Device, screenshot, instrumentation, and benchmark gates exist |
| 3 | Dependency and state foundations | Target graph and effect model enforced |
| 4 | Media source and image planning | Stable identity, crop geometry, decode policy |
| 5 | Bounded working and tile caches | Byte-budgeted ownership |
| 6 | Pure reader core | Deterministic state, commands, chapter policy |
| 7 | Paged reader | Proven fit/pinch/pan/page navigation |
| 8 | Continuous reader | Proven virtualized document zoom and scroll |
| 9 | Application shell and navigation | One main NavHost and adaptive shell |
| 10 | Library and Series slice | Shared transition, back, predictive back proven |
| 11 | Remaining product slices | Updates, browse, downloads, history, settings migrated |
| 12 | Source, search, discovery, data, startup, workers | Typed outcomes, capability-aware adapters, progressive search, safe startup, bounded work |
| 13 | Release and legacy deletion | New architecture is the only production architecture |

No phase may begin implementation until the prior phase exit criteria are recorded as `VERIFIED` in [`doc/REBUILD_STATUS.md`](doc/REBUILD_STATUS.md).

## Program health and build performance

Build time and repository health are a standing workstream, not a one-time task. Structural gates —
module layering, domain purity, signing credentials, manifest privileges — run on every change.
Repository size and timing are measured on a schedule and never gate a change; see
[`doc/BUILD_HEALTH.md`](doc/BUILD_HEALTH.md) for the rule and the current numbers. No phase may be
recorded `VERIFIED` while a health budget is breached.

## Immediate queue

1. `GOV-001` baseline tag recorded: `reconstruction-baseline-2026-09-24` at `4ec5b2c15`.
2. `GOV-002` this program supersedes stale roadmap claims.
3. `GOV-003` enforce the documentation authority and deletion policy.
4. `CON-001` complete and link the technical contract layer.
5. `TST-001A` define the executable fixture catalog — `CODE_COMPLETE`; see `ReaderFixtureCatalog` and `ReaderFixtureCatalogTest`.
6. `TST-001B` generate deterministic static media artifacts and bind them to crop, slice, and decoder tests — `CODE_COMPLETE`; see `SyntheticMediaFixtures` and `SyntheticMediaFixturesTest`.
7. `TST-001C1` static WebP fixture — `CODE_COMPLETE` at E2 through Android's bitmap encoder and decoder.
8. `TST-001C2` immutable animated WebP/GIF fixtures — `CODE_COMPLETE` at E2; app animation-policy detection and Android animated decoding pass.
9. `TST-001C3` static JXL artifact through the Coil bridge — **`CODE_COMPLETE` at E3** (2026-09-27). The "no `.jxl` fixture exists" blocker was a false premise: the bundled `jxl-coder` can encode as well as decode, so the test produces its own artifact on the device. See `B-047`; it also records that the encoder emits a *bare codestream*, so the container branch needed its own tests or the original `0x4A` defect would have passed unnoticed.
10. `TST-002` connected instrumentation infrastructure — **`CODE_COMPLETE` at E3** (2026-09-27). A connected run on `emulator-5554` (API 37) has passed, and device tests now exist in `core:data` (12), `feature:reader` (6) and `app`. **Caveat, `B-050`:** the *CI* workflow cannot run them — the API 35 emulator does not boot on GitHub's runners, so those runs are local-only. That is a CI capability gap, not an evidence gap: `ADR-0009` defines `E4-lab` as agent-driven capture against an attached emulator, which is what these runs are.
11. `SRC-000A/B/C` verify and classify the source/search compatibility inventory — `CODE_COMPLETE` at E1; see `doc/source/` and `tools/source/source-inventory.json`.
12. `SRC-001A` capability model and typed source results — `CODE_COMPLETE` at E2; source-api contract tests pass.
13. `RDR-001` pure reader session state machine — `CODE_COMPLETE` at E2; isolated from production until RDR-002/003 and viewport adapters are proven.
14. `RDR-002` canonical chapter window and directional navigation policy — `CODE_COMPLETE` at E2; isolated from production until the replacement session/viewport consumes it.
15. `RDR-003` pure gesture arbiter plus a thin Android pointer adapter — **production-wired for the paged reader** (2026-09-26). `ReaderGesturePointerAdapter` is now the only pointer path in the paged reader: the hand-rolled `detectPagerGestures` and `shouldClaimPagerTransform` are deleted, satisfying non-negotiable rule 7. The row stays `IN_PROGRESS` because the continuous reader has no call site — that is `RDR-005`, blocked on `B-025` — and because the arbiter still lacks `E4-user` evidence, which is deferred by decision.
16. `SRC-001B` adapt the currently verified legacy extension path.
17. `SRC-001C` local/native adapter and offline fixtures.
18. `SRC-001D` controlled native HTTP adapter.
19. `SRC-002` progressive search session, deduplication, ranking, and cancellation.
20. `MED-004` pure document viewport and exact tile partition — `CODE_COMPLETE` at E2 with 19 geometry and partition tests. Tile decode/cache integration and the `RDR-005` continuous reader remain open.
21. `DEF-009` long-strip reading-mode precedence extracted from `ReaderViewModel` into `DefaultReadingModeResolver` with focused tests — `CODE_COMPLETE` at E2. This is an ownership fix, not a behaviour fix: detection already worked for the reported series, so it does not address the user-reported zoom defects below.
22. `DEF-008` `BorderCropTransformation` corrected for transposed per-edge insets, single-outlier intolerance, and contentless cropping — **`CODE_COMPLETE` at E3** (2026-09-26). `core:data` has a working `androidTest` source set; `BorderCropDeviceTest` (4) measures asymmetric insets per edge on a device, which is the case a symmetric fixture cannot detect. Falsified by reintroducing the transposition, which turns the test red.
23. `MED-001` page source, identity/revision, metadata, content-rect, animation, and decode-plan contracts — `CODE_COMPLETE` at E2 with 43 tests. Pure and not production-wired; the adapters and viewport consumption come later.
24. `MED-002` byte-budgeted working page store — `CODE_COMPLETE` at E2 with 22 tests. Replaces the unbounded `ReaderPage.cachedBytes` that grows with scroll distance. Pure and not production-wired. **Correction 2026-09-27 (`DEF-017`), twice-revised:** the first claim was that the number is narrowed on every *database* write; the second was that `DATA-001` is therefore a schema migration. **Both wrong.** `ChapterRepositoryImpl`, the repository bound in `AppModule`, uses `ChapterDao`/`ChapterEntity`, and `ChapterEntity.chapterNumber` is already `Double`; the `Float` entity `ChapterImpl` is legacy and off the live path. The real narrow point is the **source model** — `SChapter.chapter_number` is a `Float` and four call sites assign `.toFloat()` into it — and `ChapterRecognition.parseChapterNumber` returns that widened value whenever it is recognised, so the loss is real for online decimal chapters but happens at the source boundary, not in storage. **So `DATA-001` is not a migration:** no schema change, no data rewrite, no version bump. It reduces to whether to widen `SChapter.chapter_number` to `Double` or to keep the tolerant `ChapterNumber` policy that already compensates correctly. The store's own wiring is separately understood: `cachedBytes` is written at three sites and released only at *chapter* disposal, so a 200-slice webtoon holds 200 pages of encoded bytes. **That wiring must be all-or-nothing** — putting the store in without releasing the field adds a second copy and makes memory worse.
25. `MED-003` crop-aware render-path, animation, and tile-scale policy — `CODE_COMPLETE` at E2 with 54 tests. Fixes crop disabling webtoon slicing, separates JXL from an animation verdict, and adds scale-bucket hysteresis so zoom settles instead of re-decoding.

26. `DEF-001` paged pinch zoom fixed at the root — the transform was computed but never applied (`graphicsLayer` was imported and unused), and the gesture centroid was discarded. `CODE_COMPLETE` at E2 with 16 tests, **production-wired**. First pass to change reader rendering behaviour; E4 acceptance is now the only thing missing.
27. `DEF-002`/`DEF-003` webtoon zoom made document-space and focal-anchored — `CODE_COMPLETE` at E2 with 22 tests, **production-wired**. Replaces the per-item X-only transform that caused widening-only pinch and item overlap.
28. `DEF-005`/`NAV-002` shared-element motion given symmetric durations, linear easing, and live reduced motion — `CODE_COMPLETE` at E2 with 17 tests, **production-wired**.
29. **Wave 0, 2026-09-25 — the record was reconciled against reality.** The recorded E4 blocker was
    false. A working emulator is attached (`Pixel_10`, API 37, x86_64, 1080x2424 @ 420dpi) with
    `app.ephyra.dev` installed, so `TST-002`, `TST-001C3`, and the E4 obligation on `DEF-001`,
    `DEF-002`, `DEF-003`, `DEF-005`, and `DEF-008` are actionable now. `BUILD_HEALTH.md` § E4 was
    rewritten with the verified facts and its residual limits: `cmdline-tools` is still absent, so
    no new AVD or system image can be created; the emulator is a *representative* device, not
    physical hardware; and local API 37 is not the API 35 emulator CI boots.
30. **Device evidence is no longer the highest-priority work — it is done, and that changed the
    priority.** *(Superseded 2026-09-27.)* The original claim was that executing it would convert
    seven stalled rows and that adding more contract code while the hardware sat attached was the
    wrong order of work. That was right, and it has now been done: five rows reached `E3` and
    `DEF-001` reached `DEVICE_VERIFIED`. The binding constraint has moved. It is now **wiring** —
    `B-032`'s 27 contracts with no production consumer — and the unglamorous root cause work that
    will not visibly pay off for weeks: `DATA-001`'s schema, where the `Float` column makes the
    chapter number lossy on every write for every user (`DEF-017`). Runbook for the remaining
    `E4-user` obligation, when it is taken up: [`doc/E4_ACCEPTANCE.md`](doc/E4_ACCEPTANCE.md).
31. **The phase-gate contradiction is resolved by
    [`ADR-0008`](doc/adr/0008-contract-first-ahead-of-phase-gate.md), not by marking a phase
    `VERIFIED`.** Phases 3–6 were `CODE_COMPLETE` while `Phase 0` read `NOT_STARTED`, contradicting
    the "no phase may begin implementation" rule. Marking a phase `VERIFIED` to tidy the table
    would have manufactured a record, which ADR-0006 forbids. ADR-0008 instead permits pure,
    unwired contract work to precede its gate, and such rows still earn `CODE_COMPLETE` only —
    never `VERIFIED` — until device evidence lands. The `Phase 0` cell was corrected to its
    substantively accurate value.
32. `DEF-004` is closed as **resolved by `DEF-008`**. The audit below traced the crop toggle end to
    end and found the plumbing correct and the transform defective. Leaving both rows open
    invited a future agent to "fix `DEF-004`" by touching already-verified plumbing, which is
    precisely the workaround-on-top-of-a-resolved-conflict that non-negotiable rule 2 forbids.

**Program audit, 2026-09-25 (continued).** `DEF-004` is closed as resolved by `DEF-008`, per the
crop-toggle finding below. The E4 environmental limit recorded earlier in this ledger was false; a
working emulator is attached, and [`doc/E4_ACCEPTANCE.md`](doc/E4_ACCEPTANCE.md) now defines how
those runs are executed and recorded. No phase was promoted to `VERIFIED` in this pass, because
reaching a device is not the same as producing evidence from it.

**Program audit, 2026-09-25.** Re-read the plan, program, status, execution guide, and the
media/cache contracts against the code, and reconciled the ledger with reality. Findings:

- Phase 4 is `CODE_COMPLETE`, not `VERIFIED`: `FIXTURE_MANIFEST.md` is explicit that E3 needs a retained connected run, and no connected run exists. The phase row now says so. **(2026-09-25: the "no connected run" part is now actionable — a device is attached — but the run has still not been executed, so the cell remains `CODE_COMPLETE`.)**
- The ledger overstated MED-003 at 52 tests; the suites hold 54 (21 + 13 + 15 + 5). Corrected, with per-suite counts recorded so the number is checkable.
- `OPS-003`'s main-source rule was an exact match while the baseline note called it ungated. It fired on four consecutive commits, each forcing a manual edit, which trains people to bump the number without reading it. Now a ceiling; the legacy-deletion target is judged by that value falling over time.
- The crop toggle was re-verified end to end: `toggleCropBorders` writes `cropBorders`/`cropBordersWebtoon`, `ReaderScreen` observes both via `collectAsState`, and both the pager and webtoon path key their Coil request and cache key on the flag. The plumbing was never the defect; the transform was (`DEF-008`).
- Not done: no contract added this pass is production-wired, and nothing here is device-verified. The gap is the Android/Compose adapter step the execution guide puts at step 4 of 10.

Do **not** resume ad hoc reader zoom, crop, transition, or source fallback patches before the relevant contract/fixture task is complete.

## Current truth

The current application is not considered a complete native reader architecture. The user-reported
defects and where they actually stand as of 2026-09-27:

- paged reader zoom was broken at the root (`DEF-001`) — **fixed, production-wired, and
  `DEVICE_VERIFIED`**, the first row to reach that level.
- webtoon pinch only widened content and strips overlapped (`DEF-002`/`DEF-003`) — both **fixed,
  production-wired, and at `E3`**. `DEF-003` is measured by slice-boundary displacement and
  falsified by moving the transform back onto each item.
- Series-to-Library return motion had mismatched durations and no reduced-motion support
  (`DEF-005`) — fixed and production-wired; at `E3`, with the caveat that motion quality is judged
  from duration and easing arithmetic rather than from frames.
- crop-borders behaviour (`DEF-008`) — fixed and at `E3` on a real device.
- `E4-user` acceptance remains outstanding for every row, by owner decision, and is not the same
  thing as `E4-lab`.

Five rows are therefore at `E3` and one is `DEVICE_VERIFIED`. That is a real change from the
previous state of this file, which described all five as awaiting device acceptance that has since
been produced. `ADR-0009` defines `DEVICE_VERIFIED` as `E3` plus (`E4-user` or `E4-lab`); `E4-lab`
is agent-driven capture against an attached emulator, and the connected runs in `core:data` and
`feature/reader` are exactly that. The standing limits still apply: a gesture-anchoring or
frame-pacing defect cannot be discharged by a still screenshot, and an emulator is a representative
device rather than physical hardware.

**The larger gap is not defects, it is wiring.** `B-032` records that 27 contracts in `core:domain`
have dedicated tests and zero production consumers. That is the same class of failure the `DEF` audit
found in `DEF-005`/`006`/`007`, at scale, and it is what actually separates this from 2.0 — not the
five reader defects, which are fixed. The `MED` -> `RDR-004`/`RDR-005` replacement sequence is still
open, and `CLEAN-001` has not begun, so the superseded reader, navigation and media code all still
exist alongside their replacements.
