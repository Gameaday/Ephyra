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

### SRC-000C — Compatibility decision

For each legacy path choose exactly one:

- keep behind `LegacyExtensionAdapter`;
- migrate to native/local/new-format adapter;
- replace with platform capability;
- delete.

No path may remain “temporarily” without an owner and removal task.

## Implementation sequence

1. `SRC-001`: `SourceDescriptor`, capabilities, `SourceGateway`, typed result types.
2. `SRC-002`: `SearchSession`, per-source jobs, deadlines, progressive state, cancellation.
3. `SRC-003`: deterministic deduplication and ranking with provenance.
4. `SRC-004`: capability-gated discovery and source health.
5. `SRC-005`: explicit migration candidates and reversible application.
6. `SRC-006`: source trust, credentials, install, and permission lifecycle.
7. `SRC-007`: zero-legacy-source product path.
8. `CLEAN-SOURCE-001`: remove legacy bridge and its service-locator support.

## Search implementation rules

- `SearchSession` is the only owner of active search jobs.
- Every source job is cancellable and deadline-bounded.
- A completed source result is immutable.
- Empty, unsupported, rate-limited, transient failure, permanent failure, and cancellation remain distinct.
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