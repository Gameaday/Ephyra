# Ephyra Reconstruction Program

> **Authoritative status:** active program
> **Baseline:** `4ec5b2c15` (`nightly-43-g4ec5b2c15`)
> **Last verified:** 2026-09-24
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
- documentation has one authority order; superseded â€œcompletedâ€ documents are deleted or clearly historical.

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

Build time and repository health are a standing workstream, not a one-time task. Deterministic
ceilings are committed in `app/src/test/resources/health-baseline.json` and enforced by
`HealthRatchetTest` on every change. Timing budgets and agent working rules live in
[`doc/BUILD_HEALTH.md`](doc/BUILD_HEALTH.md). No phase may be recorded `VERIFIED` while a health
budget is breached.

Debt is removed by **tightening** a ceiling, never by raising it.

## Immediate queue

1. `GOV-001` baseline tag recorded: `reconstruction-baseline-2026-09-24` at `4ec5b2c15`.
2. `GOV-002` this program supersedes stale roadmap claims.
3. `GOV-003` enforce the documentation authority and deletion policy.
4. `CON-001` complete and link the technical contract layer.
5. `TST-001A` define the executable fixture catalog â€” `CODE_COMPLETE`; see `ReaderFixtureCatalog` and `ReaderFixtureCatalogTest`.
6. `TST-001B` generate deterministic static media artifacts and bind them to crop, slice, and decoder tests â€” `CODE_COMPLETE`; see `SyntheticMediaFixtures` and `SyntheticMediaFixturesTest`.
7. `TST-001C1` static WebP fixture â€” `CODE_COMPLETE` at E2 through Android's bitmap encoder and decoder.
8. `TST-001C2` immutable animated WebP/GIF fixtures â€” `CODE_COMPLETE` at E2; app animation-policy detection and Android animated decoding pass.
9. `TST-001C3` static JXL fixture â€” `BLOCKED` on the current host: the available encoder/decoder is Android-native and requires connected Android execution or an independently reviewed immutable artifact.
10. `TST-002` connected instrumentation infrastructure â€” `CODE_COMPLETE` at compile level; the pinned API 35 emulator workflow must complete once before E3 evidence is recorded.
11. `SRC-000A/B/C` verify and classify the source/search compatibility inventory â€” `CODE_COMPLETE` at E1; see `doc/source/` and `tools/source/source-inventory.json`.
12. `SRC-001A` capability model and typed source results â€” `CODE_COMPLETE` at E2; source-api contract tests pass.
13. `RDR-001` pure reader session state machine â€” `CODE_COMPLETE` at E2; isolated from production until RDR-002/003 and viewport adapters are proven.
14. `RDR-002` canonical chapter window and directional navigation policy â€” `CODE_COMPLETE` at E2; isolated from production until the replacement session/viewport consumes it.
15. `RDR-003` pure gesture arbiter plus a thin Android pointer adapter â€” `IN_PROGRESS`; the arbiter and token-based tap sequencer are `CODE_COMPLETE` at E2, while the adapter remains unwired and lacks E4 pointer evidence.
16. `SRC-001B` adapt the currently verified legacy extension path.
17. `SRC-001C` local/native adapter and offline fixtures.
18. `SRC-001D` controlled native HTTP adapter.
19. `SRC-002` progressive search session, deduplication, ranking, and cancellation.
20. `MED-004` pure document viewport and exact tile partition â€” `CODE_COMPLETE` at E2 with 19 geometry and partition tests. Tile decode/cache integration and the `RDR-005` continuous reader remain open.
21. `DEF-009` long-strip reading-mode precedence extracted from `ReaderViewModel` into `DefaultReadingModeResolver` with focused tests — `CODE_COMPLETE` at E2. This is an ownership fix, not a behaviour fix: detection already worked for the reported series, so it does not address the user-reported zoom defects below.
22. `DEF-008` `BorderCropTransformation` corrected for transposed per-edge insets, single-outlier intolerance, and contentless cropping — `CODE_COMPLETE` at E2, with each defect proven by a failing test before the fix. Device acceptance still required.
23. `MED-001` page source, identity/revision, metadata, content-rect, animation, and decode-plan contracts — `CODE_COMPLETE` at E2 with 43 tests. Pure and not production-wired; the adapters and viewport consumption come later.
24. `MED-002` byte-budgeted working page store — `CODE_COMPLETE` at E2 with 22 tests. Replaces the unbounded `ReaderPage.cachedBytes` that grows with scroll distance. Pure and not production-wired; retiring the field itself waits on the viewport cutover.
25. `MED-003` crop-aware render-path, animation, and tile-scale policy — `CODE_COMPLETE` at E2 with 54 tests. Fixes crop disabling webtoon slicing, separates JXL from an animation verdict, and adds scale-bucket hysteresis so zoom settles instead of re-decoding.26. `DEF-001` paged pinch zoom fixed at the root: the transform was computed but never applied (`graphicsLayer` was imported and unused), and the gesture centroid was discarded — `CODE_COMPLETE` at E2 with 16 tests, **production-wired**. This is the first pass to change reader rendering behaviour; E4 device acceptance is now the only thing missing.

26. `DEF-001` paged pinch zoom fixed at the root — the transform was computed but never applied (`graphicsLayer` was imported and unused), and the gesture centroid was discarded. `CODE_COMPLETE` at E2 with 16 tests, **production-wired**. First pass to change reader rendering behaviour; E4 acceptance is the only thing missing.
**Program audit, 2026-09-25.** Re-read the plan, program, status, execution guide, and the
media/cache contracts against the code, and reconciled the ledger with reality. Findings:

- Phase 4 is `CODE_COMPLETE`, not `VERIFIED`: `FIXTURE_MANIFEST.md` is explicit that E3 needs a retained connected run, and no connected run exists. The phase row now says so.
- The ledger overstated MED-003 at 52 tests; the suites hold 54 (21 + 13 + 15 + 5). Corrected, with per-suite counts recorded so the number is checkable.
- `OPS-003`'s main-source rule was an exact match while the baseline note called it ungated. It fired on four consecutive commits, each forcing a manual edit, which trains people to bump the number without reading it. Now a ceiling; the legacy-deletion target is judged by that value falling over time.
- The crop toggle was re-verified end to end: `toggleCropBorders` writes `cropBorders`/`cropBordersWebtoon`, `ReaderScreen` observes both via `collectAsState`, and both the pager and webtoon path key their Coil request and cache key on the flag. The plumbing was never the defect; the transform was (`DEF-008`).
- Not done: no contract added this pass is production-wired, and nothing here is device-verified. The gap is the Android/Compose adapter step the execution guide puts at step 4 of 10.

Do **not** resume ad hoc reader zoom, crop, transition, or source fallback patches before the relevant contract/fixture task is complete.

## Current truth

The current application is not considered a complete native reader architecture. These user-reported defects remain open at the baseline:

- paged reader zoom was broken at the root and is now fixed in code (`DEF-001`, production-wired); it still needs E4 device acceptance.
- webtoon zoom primarily widens content rather than providing coherent document zoom;
- sliced webtoon content can overlap or destabilize during zoom/scroll;
- Series-to-Library return motion remains visually unacceptable;
- crop-borders behaviour is corrected in code (`DEF-008`) but still requires fresh device acceptance after the Coil 3 rewrite.

The first four are program inputs, not isolated patch opportunities, and are owned by the `MED-001` -> `MED-003` -> `RDR-004`/`RDR-005` sequence. The crop fix is a confirmed code defect that is fixed and unit-proven; it is expected to change what you see, but that is only claimable once a device confirms it. See [`doc/REBUILD_STATUS.md`](doc/REBUILD_STATUS.md).
