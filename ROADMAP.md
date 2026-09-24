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

## Immediate queue

1. `GOV-001` baseline tag recorded: `reconstruction-baseline-2026-09-24` at `4ec5b2c15`.
2. `GOV-002` this program supersedes stale roadmap claims.
3. `GOV-003` enforce the documentation authority and deletion policy.
4. `CON-001` complete and link the technical contract layer.
5. `TST-001` define deterministic reader fixtures and acceptance fixtures.
6. `SRC-000A/B/C` verify and classify the source/search compatibility inventory.
7. `SRC-001` capability model and typed source results.
8. `SRC-002` progressive search session, deduplication, ranking, and cancellation.

Do **not** resume ad hoc reader zoom, crop, transition, or source fallback patches before the relevant contract/fixture task is complete.

## Current truth

The current application is not considered a complete native reader architecture. These user-reported defects remain open at the baseline:

- paged reader zoom does not produce the intended result;
- webtoon zoom primarily widens content rather than providing coherent document zoom;
- sliced webtoon content can overlap or destabilize during zoom/scroll;
- Series-to-Library return motion remains visually unacceptable;
- crop-borders behavior requires fresh device acceptance after the Coil 3 rewrite.

These are program inputs, not isolated patch opportunities. See [`doc/REBUILD_STATUS.md`](doc/REBUILD_STATUS.md).
