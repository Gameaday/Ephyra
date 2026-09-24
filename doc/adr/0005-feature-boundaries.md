# ADR-0005: Feature boundaries

- **Status:** Accepted
- **Date:** 2026-09-24
- **Decision:** Features depend on domain/media/navigation contracts and the app shell coordinates cross-feature flows.

## Context

The current Gradle graph gives nearly every feature a direct `core:data` dependency, and `presentation-core` exports data APIs. Several features depend directly on sibling features. This makes implementation replacement and isolated testing difficult.

## Decision

- Feature modules do not depend on data implementations.
- Sibling-feature dependencies require an explicit public contract and allowlist entry.
- `presentation-core` is split by reason to change and does not export data.
- The app shell owns cross-feature navigation and composition.
- Shared UI is grouped by platform contract or responsibility, not convenience.
- Domain remains pure Kotlin and Android-free.

## Consequences

- More explicit navigation contracts are required.
- Some existing feature dependencies must be rewritten.
- Gradle and architecture checks become release gates.
- Shared components cannot silently import infrastructure types.

## Rejected alternatives

- A universal `core` module containing all shared types.
- A feature importing sibling implementation classes.
- Baseline counts that allow new data leaks.
- Interface-per-class repository proliferation.

## Evidence required

Gradle dependency tests, ArchUnit/grep rules, feature contract tests, and a complete dependency inventory.
