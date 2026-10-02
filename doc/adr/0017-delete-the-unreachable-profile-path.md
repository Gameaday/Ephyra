# 0017 — Delete the unreachable profile path

**Status:** Accepted
**Date:** 2026-09-28
**Supersedes:** nothing
**Related:** ADR-0012 (diagnostics), ADR-0014 (failures name their layer), ADR-0015 (retire the heuristic engine), `doc/2_0_CONTENT_MODEL_PLAN.md`

## Context

`SourceProfile` → `SourceProfileCache` → Room → `DynamicHttpSource` is a four-stage path that
bridges a discovered site layout to the Tachiyomi `HttpSource` ABI. It was the landing pad for
generic HTML scraping.

After ADR-0015 removed `AdaptiveHeuristicEngine` and `AddCustomSource`, the path became
unreachable rather than merely unused. The evidence, verified at `accc449c`:

- `AndroidSourceManager` consults the `profiled_domains_list` preference before constructing a
  `DynamicHttpSource`. **Nothing writes that preference.** Its only writer was `AddCustomSource`.
- `SourceProfileCache.get(domain)` is read-only on the production path.
- The only remaining profile writer is `ContentSourceOrchestrator.discover`, which requires an
  engine to be bound. `provideContentSourceEngines()` returns `emptyList()`, so it raises
  `NoEngineBoundException`.
- `UpdateCustomSource`'s only caller was the deleted `ContentSourcingViewModel`.

Live code that cannot execute is worse than absent code. It still compiles, still has tests, and
still appears in searches — which is how `DEF-029` ended up reporting disagreement between three
owners of a fact that only one owner could ever have written.

## Decision

**Delete the profile path.** Remove `SourceProfile`, `SourceProfileCache`, `SourceProfileStore`,
`RoomSourceProfileStore`, the Room entity and its migration, `DynamicHttpSource`,
`RemoteSource`, `ContentSourceOrchestrator`, `GetAvailableSources`'s profile branch, and `DEF-029`.

Jellyfin — the reason the path existed — does not need it. A Jellyfin engine receives an explicit
server URL from the user; there is no discovery step to cache. So the profile path was never going
to be Jellyfin's landing pad, and keeping it as one would have meant preserving a shape the next
adapter has no use for.

`SourceType`, the engine registry, and `ContentSourceEngine` survive. They are the contract the
next adapter implements.

## Consequences

- One content vocabulary, reached through `ContentLocator` + `ContentAdapter`, rather than a
  profile cache feeding a parallel `HttpSource` hierarchy.
- Extension-APK sources are unaffected: `AndroidSourceManager` registers them directly from
  `installedExtensionsFlow`, never through this path.
- `getAllProfiledDomains` losing its hardcoded fallback (removed earlier) is now unobservable
  rather than merely unreachable — the list has no producer at all, so it goes away with the rest.
- Jellyfin lands as `JellyfinEngine` + `JellyfinAdapter` with **no** orchestrator edit and no
  Room migration, because nothing above survives to be migrated past.

## Alternatives rejected

**Keep it as Jellyfin's landing pad.** Rejected: Jellyfin has an explicit server URL, so a
discovery-time cache is redundant for it. Keeping dead code on the speculation that a future
feature might want it is how the current situation arose.

**Leave it and mark it deprecated.** Rejected: deprecation is a comment on working code. This code
cannot run. Removing it is the only action that actually reduces the surface.

**Delete only the Room layer.** Rejected: the preference-based cache has the same producer
problem as the table. A partial deletion leaves two implementations of one absent feature.