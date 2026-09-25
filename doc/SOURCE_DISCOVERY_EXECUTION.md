# Source and Discovery Execution Contract

> **Status:** binding execution order for the source/search/discovery workstream.

## Purpose

Turn the target architecture in `SOURCE_DISCOVERY_ARCHITECTURE.md` into implementation-sized tasks. Do not add a new source capability, fallback, or UI surface before its contract and evidence are defined.

## Task order

### SRC-000A — Static inventory

Produce a machine-readable inventory of:

- source API types;
- adapters and engines;
- source registry/profile stores;
- source callers;
- search callers;
- extension loading and trust;
- service-locator and legacy DTO references;
- user-visible source/search entry points.

Classify each item as `target`, `adapter`, `compatibility`, or `delete`.

### SRC-000B — Call-site verification

For every inventory item record:

- owner module;
- callers;
- inputs/outputs;
- current tests;
- current failure semantics;
- migration destination;
- deletion blocker.

### SRC-001A — Capability contract

Implement source-neutral identity, capability declarations, typed outcomes, and the four core source operations (search, details, units, resources). Do not implement script or heuristic engines in this task.

### SRC-001B — Verified extension adapter

Adapt the currently working legacy extension path into the target `SourceGateway` without exposing legacy DTOs to new callers. This is the first executable source path. The bridge is deliberately one-way: unsupported legacy filters return `Unsupported`, empty results remain `Empty`, and legacy failures are translated into typed outcomes. It is not a permanent second source model and must be removed after product callers migrate.

### SRC-001C — Local/native adapter

Implement and test a local/native source adapter as the first target-native path. This establishes offline behavior and proves the gateway independently of extensions.

The native local adapter is implemented in `source-local` and does not use `ContentSourceAdapter`, `LocalSource`, `SManga`, or `SChapter`.

### SRC-001D — Controlled native HTTP adapter

Implement and test one controlled native HTTP source. Do not add script or heuristic sources until they have their own test and security gates.

**Current implementation:** `core:data` provides `OpdsSourceGateway`, adapting the existing tested `OpdsContentSource` (OPDS 1.2 XML and OPDS 2.0 JSON) to `SourceGateway`. It reuses the existing OkHttp transport and parser; it does not create a second HTTP stack. Its descriptor advertises only search, details, units, and resources until separate catalog/popular capability methods exist. Product search/browse callers are not migrated by this task.

**Evidence:** `OpdsSourceGatewayTest` plus the existing `OpdsContentSourceTest` parser/transport tests. This is an E2 contract implementation, not device acceptance.

## Implementation sequence

1. `SRC-001A`: `SourceDescriptor`, capabilities, `SourceGateway`, typed result types, and source-protocol DTOs.
2. `SRC-001B`: adapt the currently working legacy extension path to `SourceGateway` behind a compatibility boundary.
3. `SRC-001C`: local/native adapter and offline acceptance fixtures.
4. `SRC-001D`: one controlled native HTTP adapter with tested capability/outcome behavior.
5. `SRC-002`: `SearchSession`, per-source jobs, deadlines, progressive state, cancellation, and exact provenance-preserving aggregation. **Implemented and tested**.
6. `SRC-003`: capability-gated discovery and source health. **Implemented and tested**; health persistence and product-surface wiring remain separate tasks.
7. `SRC-007`: `GlobalSearchCoordinator`, the target product seam over `SearchSession` and `NativeSourceRegistry`. **Implemented and tested**; it does not import legacy DTOs and does not yet replace `GlobalSearchViewModel`.
8. `SRC-008`: `TargetSearchMapper`, the source-neutral product presentation boundary. **Implemented and tested**; rows preserve source provenance and source failures remain typed; persistence and navigation remain explicit later commands.
9. `SRC-009`: `TargetSearchCommand`, explicit capability-gated actions for open-details, persist, and add-to-library. **Implemented and tested**; handlers remain separate from search execution.
10. `SRC-010`: `DurableSeriesIdentity`, `DurableSeriesSnapshot`, and `SeriesRepository`, the target persistence contract. **Implemented and tested as a pure contract**; Room tables, migration, backup mapping, and adapters remain open.
8. `SRC-004`: explicit migration candidates and reversible application. **Implemented and tested** as a pure source-api contract; persistence, UI, and actual library mutation remain separate tasks.
9. `SRC-005`: source trust, credentials, install, and permission lifecycle. **Implemented and tested** as a pure source-api contract; Android Keystore/DataStore adapters, OAuth callbacks, and permission UI remain separate tasks.
10. `SRC-006`: zero-legacy-source product path. **Implemented and tested** as a native-only registry boundary; product UI wiring and legacy bridge removal remain separate tasks.
11. `CLEAN-SOURCE-001`: freeze new legacy source dependencies. **Implemented and tested** with `SourceApiBoundaryTest`; deletion of the compatibility bridge remains blocked by app composition, browse/search, updates, migration, deep-link, and reader call sites.

Deferred technology tasks are separate from the initial sequence: script sources require their own repair/security/test milestone; heuristic discovery requires a validated proposal milestone; Jellyfin requires an authenticated source/collections milestone.

## Search implementation rules

- `SearchSession` is the only owner of active search jobs.
- Every source job is cancellable and deadline-bounded.
- Cancellation propagates through structured coroutine cancellation; it is not a normal [SourceResult] value.
- A completed source result is immutable.
- Empty, unsupported, rate-limited, transient failure, and permanent failure remain distinct in [SourceResult].
- Cancellation propagates through structured coroutine cancellation and is not converted into a source result.
- Partial results are valid product state.
- Ranking is pure and deterministic.
- Provenance survives deduplication.
- Search never mutates the library or silently migrates a source.

## Discovery implementation rules

- Browse/catalog, popular, latest, recommendations, library-aware discovery, and migration candidates are separate capabilities.
- A source is hidden from a surface that it cannot serve.
- Source health is visible and explainable.
- Migration is user-confirmed and reversible.
- Offline results carry stale/source-age state.

## Required contract tests

- capability matrix;
- every typed outcome;
- cancellation and deadline;
- partial search results;
- ranking and provenance;
- health/quarantine;
- migration confidence/rollback;
- zero-legacy startup;
- no source writes UI state or Room directly.

## Exit gate

The source workstream is not complete until the app is useful with no legacy extensions installed, the compatibility bridge is isolated, and all user-visible source/search behavior has device evidence.