# ADR-0004: Media pipeline and cache ownership

- **Status:** Accepted
- **Date:** 2026-09-24
- **Decision:** Image source identity, decode planning, durable bytes, decoded images, and render artifacts are separate contracts with separate owners.

## Context

The current app has a persistent cover cache, Coil memory/disk caches, chapter/page byte storage, merged bitmaps, and slice bitmaps. Features build image requests directly, making crop and identity invalidation easy to get wrong.

## Decision

- Cover durable bytes remain in the dedicated cover store.
- Page durable bytes belong to chapter/download storage.
- Working page bytes have an explicit byte budget.
- Decoded images use scale-aware memory/tile caches.
- Render artifacts are viewport-owned and disposable.
- Features request semantic images, not raw Coil request construction.
- Every transform participates in cache identity.
- Animated images have an explicit crop/render policy.

## Consequences

- Cache ownership can be tested without Compose.
- A crop toggle invalidates derived output through the plan revision.
- Media implementation may use Coil, but UI state does not depend on Coil request details.
- A durable store is never treated as a decoded bitmap cache.

## Rejected alternatives

- One universal image cache.
- UI-managed bitmap mutation.
- Post-decode transformations without a geometry plan.
- A second Coil disk copy for remote covers.

## Evidence required

Cache contract tests, atomic-write failure tests, corruption recovery, memory-budget tests, scale-bucket tests, and device persistence tests.
