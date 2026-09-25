# Reconstruction Status Ledger

> Baseline: `4ec5b2c15`
> Program: [Ephyra Reconstruction Program](../ROADMAP.md)
> This ledger is append-oriented. Update it in the same commit as the work it describes.

## Status vocabulary

- `NOT_STARTED`: no implementation accepted.
- `IN_PROGRESS`: work exists but no phase gate is satisfied.
- `CODE_COMPLETE`: implementation and automated tests pass.
- `DEVICE_VERIFIED`: verified on declared physical/emulator matrix.
- `BLOCKED`: cannot proceed; blocker and owner are recorded.
- `ACCEPTED_DEBT`: consciously deferred with expiry/review condition.
- `VERIFIED`: all phase exit criteria satisfied.

## Evidence levels

- `E0`  claim only; no executable evidence.
- `E1`  static inspection.
- `E2`  JVM/Robolectric unit test.
- `E3`  Android instrumentation or screenshot test.
- `E4`  physical-device or representative-device evidence.
- `E5`  macrobenchmark/performance evidence.

A task may be `CODE_COMPLETE` at `E2`; it may not be `DEVICE_VERIFIED` without `E3` and, where declared, `E4`.

## Baseline facts

| ID | Fact | Evidence | Status |
|---|---|---|---|
| B-001 | `main` and `origin/main` were synchronized at `4ec5b2c15`. | Git | VERIFIED |
| B-002 | Domain purity and several ViewModel architecture rules have executable tests. | `ArchitectureTest.kt` | E2 |
| B-003 | Room v3 migration registry exists without destructive fallback. | `Migrations.kt`, migration tests | E2 |
| B-004 | Remote cover bytes have a dedicated persistent cache with atomic writes and bounded pruning. | Cover cache tests | E2 |
| B-005 | Unit/lint/build gates were run successfully before the reconstruction baseline. | Prior Gradle logs | E2 |
| B-006 | `presentation-core` exports `core:data`; feature modules broadly depend on data. | Gradle dependency scan | E1, confirmed debt |
| B-007 | Main navigation and bottom-tab navigation have separate `NavHost` owners. | `MainActivity.kt`, `HomeScreen.kt` | E1, confirmed debt |
| B-008 | `ReaderViewModel` and `ReaderActivity` span session, loading, navigation, platform, and viewer responsibilities. | Source inspection | E1, confirmed debt |
| B-009 | The current continuous reader transforms LazyColumn item geometry and paint. | `ComposeWebtoonReader.kt` | E1, confirmed debt |
| B-010 | `preloadAllPages()` can enqueue an entire online chapter. | `HttpPageLoader.kt` | E1, confirmed debt |
| B-011 | No connected device is available in the current workspace. | `adb devices` | VERIFIED environmental limit |
| B-012 | Source/search is a mixed compatibility system with no single capability, result, ranking, or discovery contract. | Source/search inventory and current modules | E1, confirmed debt |
| B-013 | Several historical documents make current-looking or Ã¢â‚¬Å“100% completeÃ¢â‚¬Â claims that are superseded by the reconstruction program. | Documentation authority audit | E1, confirmed debt |
| B-014 | `SRC-000` classifies source/search paths as target, adapter, compatibility, or delete; legacy removal is gated by zero-legacy product evidence. | `doc/source/` inventory artifacts, `tools/source/source-inventory.json` | E1, CODE_COMPLETE |

## Open user-reported defects

| ID | Report | Required acceptance evidence | Status |
|---|---|---|---|
| DEF-001 | Paged pinch zoom does nothing or does not visibly transform the page. | E3 input test + E4 matrix | IN_PROGRESS, legacy |
| DEF-002 | Webtoon pinch mainly widens content. | E3 document transform test + E4 matrix | IN_PROGRESS, legacy |
| DEF-003 | Sliced webtoon elements overlap or destabilize after zoom/scroll. | E3 continuity/clipping test + E4 matrix | IN_PROGRESS, legacy |
| DEF-004 | Crop toggle is inconsistent and does not reliably enlarge content immediately. | E3 same-page toggle test + E4 static/animated matrix | IN_PROGRESS, legacy |
| DEF-005 | Series-to-Library return motion is still visually unacceptable. | E3 transition test + E4 predictive-back matrix | IN_PROGRESS, legacy |
| DEF-006 | Updates must remain library-scoped unless a consumer explicitly opts out. | DAO contract + ViewModel test | CODE_COMPLETE |
| DEF-007 | Backward chapter navigation must remain available with skip-read enabled. | Pure navigation contract + viewer tests | CODE_COMPLETE |
| DEF-008 | Crop-borders transformation measured insets transposed, aborted on a single artifact, and cropped contentless images. | Robolectric bitmap tests + E4 matrix | CODE_COMPLETE at E2, E4 pending |
| DEF-009 | Long-strip reading-mode precedence lived in `ReaderViewModel` with untested content-type handling. | Pure resolver contract tests | CODE_COMPLETE at E2 |

Legacy defects are not fixed by changing the current implementation unless the task explicitly says so. They are acceptance inputs for the replacement.


## Task ledger

| ID | Task | Owner | Evidence | Status |
|---|---|---|---|---|
| GOV-001 | Tag and record reconstruction baseline. | Agent | Git tag/checksum | VERIFIED |
| GOV-002 | Supersede stale roadmap claims and remove obsolete plans from the current tree. | Agent | This ledger + documentation governance | CODE_COMPLETE |
| GOV-003 | Enforce documentation authority and deletion policy. | Agent | Documentation governance + link/index gate | CODE_COMPLETE |
| DOC-001 | Delete superseded Ã¢â‚¬Å“completedÃ¢â‚¬Â documents after contract migration. | Agent | Git diff + link/index gate | CODE_COMPLETE |
| SRC-000A | Static source/search inventory. | Agent | Inventory artifact | CODE_COMPLETE |
| SRC-000B | Source/search call-site verification. | Agent | `doc/source/SOURCE_CALL_GRAPH.md` | CODE_COMPLETE |
| SRC-000C | Legacy compatibility classification and removal decision. | Agent | Compatibility matrix and removal plan | CODE_COMPLETE |
| SRC-001A | SourceDescriptor, capabilities, SourceGateway, typed outcomes, and source-protocol DTOs. | Agent | Source-api contract tests | CODE_COMPLETE |
| SRC-001B | Adapt the currently verified legacy extension path to SourceGateway. | Agent | `LegacySourceGateway` contract tests + zero-legacy callers guard | CODE_COMPLETE |
| SRC-001C | Local/native source adapter and offline fixtures. | Agent | `LocalSourceGateway` contract tests + local source test suite | CODE_COMPLETE |
| SRC-001D | Controlled native HTTP source adapter. | Agent | `OpdsSourceGateway` contract tests + existing OPDS parser tests | CODE_COMPLETE |
| SRC-002 | Progressive search session, deduplication, ranking, cancellation. | Agent | `SearchSession` contract tests | CODE_COMPLETE |
| SRC-003 | Capability-gated discovery, health, quarantine, recommendations. | Agent | `SourceHealth` contract tests | CODE_COMPLETE |
| SRC-004 | Explicit confidence-scored reversible migration. | Agent | Migration contract tests | CODE_COMPLETE |
| SRC-005 | Isolated credentials, trust, install, permission lifecycle. | Agent | Source trust contract tests | CODE_COMPLETE |
| SRC-006 | Zero-legacy-source product path. | Agent | `NativeSourceRegistry` contract tests | CODE_COMPLETE |
| CLEAN-SOURCE-001 | Freeze new legacy source dependencies and record removal blockers. | Agent | `SourceApiBoundaryTest` + explicit compatibility allowlist | CODE_COMPLETE |
| SRC-007 | Target-native global search coordinator seam. | Agent | `GlobalSearchCoordinator` contract tests; product ViewModel migration remains open | CODE_COMPLETE |
| SRC-008 | Source-neutral product search presentation mapping. | Agent | `TargetSearchMapper` contract tests; no legacy DTO or persistence imports | CODE_COMPLETE |
| SRC-009 | Explicit, capability-gated target search commands. | Agent | `TargetSearchCommand` contract tests; persistence/navigation handlers remain open | CODE_COMPLETE |
| SRC-010 | Durable target series identity and persistence contract. | Agent | `SeriesPersistenceContractTest`; Room/backup implementation remains open | CODE_COMPLETE |
| DATA-001A | Target Room entity/DAO contract and migration fixture boundary. | Agent | `TARGET_DATA_SCHEMA.md` + target entity/DAO compile gate | CODE_COMPLETE |
| DATA-001B | Reversible legacy-to-target migration plan and fixture mapping. | Agent | `LegacySeriesMigrationTest` | CODE_COMPLETE |
| DATA-001C | One-way Room-entity-to-migration-input adapter. | Agent | `LegacyRoomMigrationAdapterTest` | CODE_COMPLETE |
| DATA-001D | Isolated target Room transaction writer, repository, and migration fixture. | Agent | `TargetMigrationWriterTest` + `TargetSeriesRepositoryTest`; live Room v3 unchanged; cross-source grouping deferred | CODE_COMPLETE |
| DATA-001E | Versioned source-neutral target backup/restore mapping. | Agent | `TargetBackupMapperTest`; protobuf round-trip, genre/library/chapter/history preservation, and referential guards | CODE_COMPLETE |
| DATA-001F | Explicit canonical cross-source identity/link policy. | Agent | `CanonicalSeriesLinkTest`; no automatic linking, auditable confirm/reject/revoke lifecycle | CODE_COMPLETE |
| DATA-001G | Isolated target link repository and Room execution contract. | Agent | `TargetSeriesLinkRepositoryTest`; proposal idempotency, required durable sources, evidence/content-type round-trip, lifecycle transitions | CODE_COMPLETE |
| DATA-001H | Target repository production cutover decision. | Agent | [Target data cutover decision](TARGET_DATA_CUTOVER.md); production cutover deferred | CODE_COMPLETE |
| DATA-001I | Production parity matrix and migration rehearsal fixture. | Agent | [Target data parity matrix](TARGET_DATA_PARITY.md) + `TargetMigrationRehearsalTest` + `TargetCanonicalSeriesReadRepositoryTest` + `SourceLifecycleTest` + `ReconcileSourceRegistryTest` + `TargetSourceRepositoryTest` + `DownloadArtifactVerifier`; category, tracking, excluded-scanlator policy, target backup, confirmed-link aggregate projection, source lifecycle persistence/reconciliation, and core:download `UniFileDownloadArtifactProbe`; pure download artifact verification and UniFile observation covered; transactional index publication and legacy read-only adoption remain open | IN_PROGRESS |
| CON-001 | Technical contract layer for reader, media, navigation, motion, source, fixtures, budgets, and dependencies. | Agent | Contract links + execution review | CODE_COMPLETE |
| TST-001A | Executable reader/media fixture catalog and interaction case registry. | Agent | `ReaderFixtureCatalog` + `ReaderFixtureCatalogTest` | CODE_COMPLETE |
| TST-001B | Deterministic static PNG/JPEG/corrupt/missing media artifacts plus crop and slicing acceptance tests. | Agent | `SyntheticMediaFixtures` + `SyntheticMediaFixturesTest` | CODE_COMPLETE |
| TST-001C1 | Static WebP fixture through the Android bitmap encoder/decoder. | Agent | `SyntheticMediaFixtures.staticWebp` + `SyntheticMediaFixturesTest` | CODE_COMPLETE |
| TST-001C2 | Immutable animated WebP/GIF fixtures through app policy and Android animated decoding. | Agent | `animated-two-frame-v1.webp/.gif` + hash/policy/decode tests; connected playback remains open | CODE_COMPLETE |
| TST-001C3 | Static JXL artifact through the project Coil bridge decoder. | Agent | Blocked: host cannot execute Android-native jxl-coder; requires connected Android evidence or independently reviewed immutable artifact | BLOCKED |
| TST-002 | Connected instrumentation source set, real app launch/recreate smoke test, normal-CI instrumentation APK compile, and pinned API 35 emulator workflow. | Agent | `MainActivityConnectedTest`; `:app:assembleDebugAndroidTest` passes; connected workflow execution pending | IN_PROGRESS |
| TST-003 | Screenshot and macrobenchmark release gates. | Agent | CI artifacts | NOT_STARTED |
| SEC-001 | Remove hardcoded signing secrets from build logic. | Agent | Secret scan | NOT_STARTED |
| SEC-002 | Encrypt source/tracker credentials with Keystore-backed storage. | Agent | Threat-model tests | NOT_STARTED |
| SEC-003 | Remove or justify `largeHeap` and battery exemption permission. | Agent | Manifest audit | NOT_STARTED |
| ARC-001 | Enforce target module dependency graph. | Agent | Architecture/Gradle gate | NOT_STARTED |
| ARC-002 | Remove nested main navigation ownership. | Agent | Navigation tests | NOT_STARTED |
| ARC-003 | Implement effect/state contract for long workflows. | Agent | Pure reducer tests | NOT_STARTED |
| MED-001 | Page source, metadata, content-rect, and decode-plan contracts. | Agent | `PageSourceId`/`PageSource`/`PageMetadata`/`DecodePlan`/`PageImage` with 43 contract tests. Identity carries a revision; crop is judged against content size, not intrinsic; oversized and animated pages plan a software decode instead of failing. | CODE_COMPLETE at E2 |
| MED-002 | Page working byte budget and durable source ownership. | Agent | Memory tests | NOT_STARTED |
| MED-003 | Crop-aware geometry and animated-image policy. | Agent | Synthetic image tests + E4 | NOT_STARTED |
| MED-004 | Virtualized continuous-document tile pipeline. | Agent | `DocumentViewport` + `DocumentTilePartition` (19 tests: one transform, focal zoom, clamped bounds, exact gap-free tiling, stable grid identity) are CODE_COMPLETE at E2; tile decode/cache integration and RDR-005 rendering remain open | CODE_COMPLETE |
| RDR-001 | Pure reader session state machine. | Agent | `ReaderSession.kt` + `ReaderSessionReducerTest`; deterministic command/effect matrix, restore invariants, retry/resource cleanup; not production-wired | CODE_COMPLETE |
| RDR-002 | Chapter window and directional navigation policy. | Agent | `ReaderChapterWindowPolicy` + `ReaderChapterWindowPolicyTest`; explicit ordering, hard filters, forward-only read/filter skipping, downloaded-only boundaries, stable duplicate reduction, and current retention; not production-wired | CODE_COMPLETE |
| RDR-003 | One gesture arbiter per viewport. | Agent | `ReaderGestureArbiter` (9 tests) + `ReaderTapSequencer` (6 tests) are CODE_COMPLETE at E2; `ReaderGesturePointerAdapter` compiles but has no production call sites, no Robolectric pointer-event coverage, and no E4 device evidence. Per-viewport integration belongs to RDR-004/RDR-005. | IN_PROGRESS |
| RDR-004 | Paged reader replacement. | Agent | E3 + E4 matrix | NOT_STARTED |
| RDR-005 | Continuous reader replacement. | Agent | E3 + E4 matrix | NOT_STARTED |
| NAV-001 | Single main NavHost and adaptive shell. | Agent | Navigation contract | NOT_STARTED |
| NAV-002 | Series shared-element/fade/predictive-back policy. | Agent | E3 + E4 matrix | NOT_STARTED |
| DATA-001 | Clean-slate schema policy for replacement release. | Agent | Schema tests | NOT_STARTED |
| OPS-001 | One startup state model; split Application responsibilities. | Agent | Startup tests/benchmarks | NOT_STARTED |
| OPS-002 | Build performance budgets established and tracked. | Agent | `doc/BUILD_HEALTH.md` baselines + scheduled timing measurement; clean-build number still outstanding | IN_PROGRESS |
| OPS-003 | Deterministic repository health ratchets enforced. | Agent | `HealthRatchetTest` + `health-baseline.json`; module/edge/TODO/deprecated ceilings, exact main-source match, and test-source floor gated on every change. Release APK size is recorded but NOT gated: measuring it needs a full release build. Deprecated-marker ceiling (39) was previously unenforced config and is now enforced. | CODE_COMPLETE |
| REL-001 | Enforce lint, instrumentation, benchmark, and release artifact gates. | Agent | CI run | NOT_STARTED |
| CLEAN-001 | Delete superseded reader/navigation/media code. | Agent | Dependency/import scan | NOT_STARTED |

## Phase gates

| Phase | Status | Evidence required to mark VERIFIED |
|---|---|---|
| 0  Baseline | NOT_STARTED | GOV-001, GOV-002, GOV-003, DOC-001, CON-001, TST-001A, TST-001B |
| 0A Technical contracts | CODE_COMPLETE | CON-001 |
| 1  Security/platform | NOT_STARTED | SEC-001 through SEC-003 |
| 2  Quality infrastructure | NOT_STARTED | TST-001A, TST-001B, TST-001C, TST-002, TST-003 |
| 3  Foundations | NOT_STARTED | ARC-001 through ARC-003 |
| 4  Media planning | IN_PROGRESS | MED-001 page/source/metadata/decode-plan contracts are CODE_COMPLETE at E2. MED-003 crop-aware geometry and animated policy remain. |
| 5  Working memory | IN_PROGRESS | MED-004 geometry/tile partition is CODE_COMPLETE at E2; MED-002 byte budget and tile decode/cache integration remain open. |
| 6  Reader core | IN_PROGRESS | RDR-001 and RDR-002 are CODE_COMPLETE at E2. RDR-003 has a CODE_COMPLETE arbiter/tap policy at E2 but remains IN_PROGRESS until a replacement viewport owns it and E4 pointer evidence exists. |
| 7  Paged reader | NOT_STARTED | RDR-004 |
| 8  Continuous reader | NOT_STARTED | RDR-005 |
| 9  Shell/navigation | NOT_STARTED | NAV-001 |
| 10  Library/Series | NOT_STARTED | NAV-002 |
| 11  Remaining slices | NOT_STARTED | Product parity checklist |
| 12  Source/data/ops | IN_PROGRESS | SRC-000 through SRC-003 are CODE_COMPLETE at E1/E2; SRC-004 through SRC-005, DATA-001, and OPS-001 remain open. |
| 13  Release/cleanup | NOT_STARTED | REL-001, CLEAN-001, DOC-001 |
| Health  Build and repository health | IN_PROGRESS | OPS-002, OPS-003. No phase advances to VERIFIED while a health ceiling or timing budget is breached. |

## Change log

| Date | Change | Evidence/commit |
|---|---|---|
| 2026-09-24 | Created annotated baseline tag `reconstruction-baseline-2026-09-24` at `4ec5b2c15`. | Git tag `20ab06c7255b4c649c82af5bf614299b5a5fca61` |
| 2026-09-24 | Program initialized; legacy reader defects explicitly recorded. | This document |
| 2026-09-24 | Added binding source/search/discovery contract, source-boundary ADR, documentation governance, and deleted superseded plans. | Documentation links, `git diff --check`, `spotlessCheck --offline` |
| 2026-09-25 | Added native `LocalSourceGateway` over `UnifiedContentSource`, inline local resource bytes, offline contract tests, and independent of legacy source DTOs. | `:source-local:test`, `:app:compileDebugKotlin` |
| 2026-09-25 | Completed the isolated pure reader session owner, command/effect reducer, restore validation, resource cleanup, and full reducer matrix. | `ReaderSession.kt`, `ReaderSessionReducerTest`, `:feature:reader:testDebugUnitTest` |
| 2026-09-25 | Added the source-neutral chapter-window policy with explicit ordering, hard exclusions, directional skip rules, downloaded-only boundaries, and stable duplicate reduction. | `ReaderChapterWindowPolicy.kt`, `ReaderChapterWindowPolicyTest`, `:core:domain:testDebugUnitTest` |
| 2026-09-25 | Added the pure document viewport and tile partition model. Tile edges are derived from the grid index so neighbours share the identical expression; the first implementation derived each edge from the previous tile's rounded edge and produced sub-pixel overlaps. The partition tests are the regression guard. | `DocumentViewport`, `DocumentTilePartition`, 19 tests; `:core:domain:testDebugUnitTest` |
| 2026-09-25 | Added the pure reader gesture arbiter and token-based single/double-tap sequencer, plus a thin uncalled pointer adapter. Arbiter emitted a raw `Tap` effect that had no double-tap gate; it is now `TapCandidate`, and only the sequencer may emit `SingleTap`/`DoubleTap`. | `ReaderGestureArbiter`, `ReaderTapSequencer`, `ReaderGesturePointerAdapter`; 15 domain gesture tests; `:core:domain:testDebugUnitTest`, `:feature:reader:testDebugUnitTest` |
