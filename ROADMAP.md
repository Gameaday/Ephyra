# Ephyra Reconstruction Program

> **Authoritative status:** active program
> **Baseline:** `4ec5b2c15` (`nightly-43-g4ec5b2c15`) — in progress toward `2.0-clean`
> **Last verified:** 2026-10-05 (reconciled against `4f2f8e4`)
> **Target:** Android-native Ephyra 2.0 with explicit state, resource, navigation, and media ownership
> **Forward plan:** [`doc/2_0_COMPLETION_PLAN.md`](doc/2_0_COMPLETION_PLAN.md)

This file is the single entry point for the reconstruction program. If another document disagrees with this file, the disagreement is a documentation defect and must be corrected before implementation continues.

> **Sourcing has its own authority:** [`doc/SOURCE_ROADMAP.md`](doc/SOURCE_ROADMAP.md) decides what is
> current, compatibility, or planned in content sourcing. Read it before any sourcing work — it
> supersedes the sourcing sections of the documents listed below.

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
24. [`doc/SOURCE_ROADMAP.md`](doc/SOURCE_ROADMAP.md)  **sourcing authority** — current vs compatibility vs planned, phases, gates, and what must not be reintroduced.
25. [`doc/README.md`](doc/README.md)  documentation index — the current document set and its owners.
26. [`doc/2_0_COMPLETION_PLAN.md`](doc/2_0_COMPLETION_PLAN.md)  **forward plan** — validated state and ordered steps to the `2.0-clean` baseline.

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
- a **sourcing** authority exists that marks every component current, compatibility, or planned, and
  says what must not be reintroduced (`doc/SOURCE_ROADMAP.md`);
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
| 2 | Quality infrastructure | Format, layering, unit, device and build-budget gates run on every change |
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

## Open queue

Only open items are listed. Completed items and their evidence live in
[`doc/REBUILD_STATUS.md`](doc/REBUILD_STATUS.md); [`doc/2_0_COMPLETION_PLAN.md`](doc/2_0_COMPLETION_PLAN.md)
gives the ordered steps to close them.

- **Execute `ADR-0017`.** The profile path is accepted-for-deletion but still in the tree
  (`SourceProfile`, `SourceProfileCache`, `SourceProfileStore`, `RoomSourceProfileStore`,
  `DynamicHttpSource`, `RemoteSource`, `ContentSourceOrchestrator`). Largest single cleanup item.
- **Wire or delete the unwired 2.0 contracts.** The media tuple
  (`PageSource`/`PageMetadata`/`DecodePlan`/`SampleSizePolicy`/`RenderPathPolicy`/`AnimationPolicy`),
  `ReaderSession`, `IngestEngine`, and the five DI-orphans (`TargetSeriesRepository`,
  `TargetMigrationWriter`, `LegacyRoomMigrationAdapter`, `ReconcileSourceRegistry`,
  `verifyDownloadArtifact`). "Verified but unwired" is not an allowed resting state.
- **`SRC-011`/`SRC-012`.** One owner for source existence and identity (`ADR-0012`), and the
  MangaDex-shaped gateway adapter; closes `DEF-029` and the sourcing Phase 1 producer fix.
- **`DATA-001`.** Clean-slate schema policy; decide the `SChapter.chapter_number: Float` question
  (`DEF-017`). Not a migration — the live entity is already `Double`.
- **`OPS-001`.** One startup state model; split `Application` responsibilities.
- **`ARC-002`/`ARC-003`.** Close the remaining dependency-graph rows; migrate a production feature
  onto the effect/state contract.
- **Improvement-session remainder.** Unified search v1 Stages B–E; `RFC-0001` identity (resolve O2
  first); Jellyfin; Discover Steps 3/5/6; cache queue; performance device work.
- **`REL-001`/`CLEAN-001`.** Release gates and deletion of the superseded reader/navigation/media
  generation (plus the unwired `macrobenchmark` module).

## Current truth

Validated against the tree on 2026-10-05. The detailed state and the ordered completion steps are
in [`doc/2_0_COMPLETION_PLAN.md`](doc/2_0_COMPLETION_PLAN.md); this is the short form.

- **Reader defects are fixed.** `DEF-001` is `DEVICE_VERIFIED`; `DEF-002`/`DEF-003`/`DEF-005`/
  `DEF-008` are fixed, production-wired, and at `E3`. `E4-user` remains outstanding for every row by
  owner decision, and is not the same thing as `E4-lab`.
- **The canvas viewport deletion is done** (`ADR-0010`): `DocumentViewport`, `DocumentTilePartition`,
  `TileScalePolicy`, `WebtoonViewport` no longer exist.
- **The remaining gap is wiring and cleanup, and it is finite.** A set of tested contracts still has
  no production consumer (the media tuple, `ReaderSession`, `IngestEngine`, five DI-orphans), and a
  superseded generation still ships beside its replacement. See the open queue above.
- **`ADR-0017` is accepted but not executed.** The profile path is still in the tree. That is the
  largest single cleanup item and the claim most likely to mislead a reader.
- **A second workstream exists.** The improvement session (navigation motion, Discover, unified
  search, cache/cover, performance) is recorded in [`doc/historical/`](doc/historical/); its forward
  remainder is in the completion plan.
- **Do not resume ad hoc reader zoom, crop, transition, or source fallback patches** before the
  relevant contract/fixture task is complete.

The `RDR-005` replacement (`RDR-004` is done and Phase 7 is `VERIFIED`) and the `CLEAN-001` deletion
have not begun, so the superseded reader, navigation, and media code all still exist alongside their
replacements.
