# Source, Search, and Discovery Architecture

> **Status:** binding target contract for the Ephyra reconstruction program. Legacy source code may be reused only behind these contracts.

## Purpose

Ephyra needs a source system that is useful even when no legacy Tachiyomi/Mihon extension is installed, and that can evolve without inheriting legacy DTOs, service locators, or ambiguous fallback behavior. Sources are product capabilities, not UI adapters.

The target product has four related systems:

```text
Source capability registry
  -> source execution adapters
  -> normalized source results
  -> search/discovery/ranking orchestration
  -> user-facing Browse, Search, Updates, and Series surfaces
```

A source is not a feature screen. A feature consumes typed capabilities and never chooses an implementation by legacy class name.

## Compatibility boundary

The Tachiyomi/Mihon source API is a temporary import/compatibility boundary, not the product model.

- The app must work with zero legacy extensions installed.
- Native Ephyra sources, local sources, and approved external source formats use the same capability contracts.
- Legacy extension loading may remain only while an explicit compatibility milestone requires it.
- Every legacy adapter must have a removal task, an owner, and an exit condition.
- No new feature may depend directly on `eu.kanade.tachiyomi.*`, `Injekt`, `CoreContainer`, `SManga`, `SChapter`, or a concrete extension class.
- Compatibility tests prove a boundary works; they do not prove that the boundary is part of the target architecture.

## Source identity and capabilities

A `SourceDescriptor` is the stable identity of a source:

```text
id, displayName, sourceKind, revision, trustLevel, capabilities, compatibilityLevel
```

Capabilities are explicit and independently testable:

```text
Search
Details
Chapters
Pages
Popular
Latest
Updates
Recommendations
Catalog
Migration
```

An adapter must advertise a capability before a UI can offer it. Unsupported means unsupported; it must not be represented as an empty successful result.

## Result semantics

Every operation returns a typed outcome:

```text
Success<T>
Empty
Unsupported
TransientFailure
PermanentFailure
RateLimited
Cancelled
```

Rules:

- `Empty` is a valid result and is not automatically a source failure.
- `Unsupported` never triggers an unrelated fallback.
- `TransientFailure` may retry or use a declared fallback.
- `PermanentFailure` is shown with an actionable explanation and may be quarantined.
- `RateLimited` carries retry-after information and must not create a retry storm.
- `Cancelled` is never rendered as an error.
- An empty result cannot be used to infer that a scraper is broken.

## Adapter architecture

```text
SourceGateway
  NativeSourceAdapter
  LocalSourceAdapter
  LegacyExtensionAdapter (current verified path, temporary)
  ExternalSourceAdapter (future, only after contract/test work)
  HeuristicDiscoveryAssistant (future, experimental; never authoritative)
```

Adapters are responsible only for protocol translation and validation. They do not own ranking, navigation, database writes, or UI state.

Each adapter must provide capability discovery, request identity/version, cancellation and timeout behavior, safe network/filesystem access, normalized domain results, structured failure classification, and health observations without automatic destructive mutations.

## Current verified source path

The only currently verified working source path is the legacy extension path. It is retained behind a compatibility boundary while the target gateway is built. Script and heuristic engines are not considered working source implementations and are not part of the initial source sequence.

## Deferred source technologies

- External scripts may be revisited only as a separately tested/trusted adapter task.
- Heuristic discovery may be revisited only as a proposal/validation assistant, never as an authoritative opaque source.
- Jellyfin remains a future authenticated source milestone; the current Jellyfin tracker is progress sync only.

## Initial source sequence

1. Local/native source: deterministic offline baseline.
2. Controlled native HTTP source: validates search/details/units/resources and health.
3. Jellyfin authenticated source and collections: only after authentication, source identity, and collection mapping are tested.
4. External script source: only after a dedicated security, revision, sandbox, and contract test task is accepted.
5. Heuristic discovery assistant: only after it can produce validated proposals without becoming an implicit source.

| Current area | Current owner/type | Target classification | Required action |
|---|---|---|---|
| `:source-api` `eu.kanade.tachiyomi.*` | Legacy extension ABI | Temporary adapter boundary | Keep only behind `LegacyExtensionAdapter`; remove after exit gate |
| `:source-api` `ephyra.source.api.*` | Emerging content-source API | Target contract candidate | Normalize into capability/result contracts; no legacy DTO leakage |
| `ContentSourceOrchestrator` | Domain orchestration with heuristic fallback | Target policy | Replace empty-list inference with typed outcomes and explicit capabilities |
| `ScriptableContentSourceEngine` | Script-backed source engine, currently not verified | Deferred experimental adapter | Do not include in the initial product path; repair and test under a separate task before any release claim. |
| `AdaptiveHeuristicEngine` | Heuristic DOM engine, currently not verified | Deferred discovery assistant | Do not use as an authoritative source. A future task may repair and test it as a proposal/validation tool. |
| `DynamicHttpSource` | Legacy-shaped bridge over orchestrator | Temporary adapter | Migrate callers to `SourceGateway`; delete when no callers remain |
| `ExtensionLoader` | APK install/load/trust boundary | Temporary platform adapter | Isolate trust, credentials, lifecycle, and removal gate |
| `GlobalSearchViewModel` | Search orchestration/UI state | Target session owner | Replace with progressive `SearchSession` and pure ranking |
| `SearchViewModel` | Per-source/legacy search state | Target adapter/session code | Remove concrete source preferences and direct legacy calls |
| `SourceManager` | Source registry/lifecycle facade | Target registry | Expose descriptors, capabilities, health, and trust |
| `SourceProfileStore` | Persistent source profile state | Target source registry state | Define revision, ownership, and deletion semantics |

The inventory is evidence, not approval. `SRC-000` must verify each row against actual call sites before implementation begins.

## Search architecture

Search is a session, not a single ViewModel callback.

```text
SearchQuery
  -> SearchPlanner
  -> per-source SearchJob
  -> bounded concurrency
  -> normalized result set
  -> deduplication
  -> ranking
  -> progressive SearchState
  -> explicit cancel/retry
```

Requirements:

- Search is cancellable and has a total deadline.
- Results stream as sources complete; the screen must not wait for the slowest source.
- A slow or failed source is visible as a partial result, not hidden as a universal failure.
- Empty source results remain visible in source diagnostics.
- Deduplication preserves source provenance and canonical identity.
- Ranking is deterministic, explainable, and separate from execution.
- Search does not write to the library or mutate source health.
- Search queries and result pages are not retained forever by default.

## Discovery architecture

Discovery is a first-class product surface with explicit modes:

- source browse/catalog;
- popular/latest from capable sources;
- recommendations;
- library-aware discovery;
- migration candidates;
- source health and trust explanations.

Discovery must not claim a capability a source does not advertise. “Recommended” means a documented ranking policy, not an opaque fallback order.

## Ranking and surfacing

Ranking policy belongs in pure domain code and receives explicit signals:

```text
title relevance, source reliability, freshness, completion confidence,
user-library overlap, user preference, duplicate confidence, latency
```

Ranking must be deterministic for a fixed input and explain why a result is surfaced. It must not silently prefer one legacy adapter or permanently bury a source.

Surfacing rules:

- Library content gets a separate library-first path.
- Search results show provenance and source confidence where useful.
- Dead-source migration suggestions are recommendations, never automatic destructive changes.
- Cached results are marked stale when their source revision is old.
- User-configured source/language/content preferences are applied before ranking.

## Health, repair, and migration

Source health is an observation stream:

```text
Healthy
Degraded
RateLimited
Unreachable
Unsupported
Quarantined
```

Health changes cannot silently migrate user data. Migration is an explicit, reviewable command:

- find candidates;
- show match evidence and confidence;
- preserve reading progress;
- preserve library metadata unless confirmed;
- record an undo/history event;
- make partial failure recoverable.

## Data ownership

```text
source definition       -> source registry
source result           -> immutable domain result
library relationship    -> Room
search session          -> bounded memory
health observations     -> bounded database/history
source credentials      -> Keystore-backed credential store
```

No source adapter owns Room tables, Compose state, or navigation state.

## Removal plan

The legacy boundary is removed when:

1. Native/local/new-format sources cover the required product capabilities.
2. Source installation and trust have an independent lifecycle.
3. Search, browse, updates, migration, and offline behavior pass the product matrix without a legacy extension.
4. No feature imports legacy source types.
5. The release notes explicitly state the compatibility removal.
6. A clean-slate install and backup/restore path are verified.

Until then, legacy adapters are isolated, measured, and treated as an accepted migration risk—not as a target architecture.

## Required tests

- capability matrix per adapter;
- success/empty/unsupported/transient/permanent/rate-limited/cancelled outcomes;
- cancellation and deadline tests;
- progressive search and partial failure tests;
- deterministic deduplication and ranking tests;
- provenance preservation tests;
- source trust and permission tests;
- migration candidate confidence tests;
- zero-legacy-source app startup test;
- backup/restore and process-death tests;
- source health/quarantine tests.

## Exit criteria

The source/discovery workstream is complete only when:

- the app is useful without legacy extensions;
- all source operations have typed outcomes;
- search is bounded, progressive, cancellable, and explainable;
- discovery surfaces only declared capabilities;
- ranking is deterministic and tested;
- migration is explicit and recoverable;
- legacy compatibility is isolated and has a deletion gate;
- user-visible source/search states are device-verified.
