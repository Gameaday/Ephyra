# ADR-0007: Source is not a UI adapter

- **Status:** Accepted
- **Date:** 2026-09-24
- **Decision:** Ephyra sources are capability-based platform components. Tachiyomi/Mihon extensions are a temporary compatibility adapter, not the source product model.

## Context

The current source layer combines legacy DTOs, extension loading, service locators, script execution, heuristic extraction, source health, search orchestration, ranking, migration, and UI-facing ViewModels. Search and discovery behavior are therefore difficult to test independently and can depend on whether a legacy extension is installed.

## Decision

- A `SourceDescriptor` declares stable identity and capabilities.
- A `SourceGateway` exposes typed results: `Success`, `Empty`, `Unsupported`, `TransientFailure`, `PermanentFailure`, `RateLimited`, and `Cancelled`.
- Native, local, new-format, and temporary legacy adapters implement the same gateway contract.
- Empty is a valid result and does not trigger failure fallback.
- Search is a bounded, cancellable session with progressive results, provenance-preserving deduplication, deterministic ranking, and explicit retry.
- Discovery surfaces only declared capabilities.
- Health and migration are observable, confidence-scored, reversible, and non-destructive by default.
- No feature imports legacy source types, service locators, or concrete adapters directly.
- The app must be useful with zero legacy extensions installed.

## Consequences

- The source boundary must be redesigned before adding more sourcing features.
- Compatibility code becomes explicitly removable and testable.
- Search and discovery need pure domain policies and adapter contract tests.
- Source credentials and trust need an independent lifecycle.
- Legacy source removal becomes a product release decision with measurable exit criteria.

## Rejected alternatives

- Treating the Tachiyomi/Mihon API as the permanent source model.
- Adding another fallback path without typed failure semantics.
- Letting source ViewModels decide ranking, health, and migration policy.
- Requiring a legacy extension for core app utility.

## Evidence required

Capability matrix, zero-legacy startup/product tests, typed-result tests, search deadline/partial-result tests, deterministic ranking tests, migration rollback tests, and source-health/quarantine tests.