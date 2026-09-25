# Source Compatibility Inventory

> **Task:** `SRC-000`  
> **Status:** `CODE_COMPLETE` at evidence level `E1` (static inspection)  
> **Scope:** source, search, discovery, migration, and compatibility boundaries  
> **Rule:** this document describes the target migration boundary. It does not claim that the legacy product path is already replaced.

## Executive decision

The current source/search stack contains two overlapping architectures:

```text
legacy CatalogueSource path
  -> SourceManager / SourceRepository
  -> GlobalSearchViewModel / SearchViewModel
  -> UnifiedSearchEngine / SmartSourceSearchEngine

modern profile path
  -> ContentSourceOrchestrator
  -> ScriptableContentSourceEngine / AdaptiveHeuristicEngine
```

The target is one capability-based path:

```text
SourceRegistry
  -> SourceDescriptor
  -> SourceGateway
  -> typed operation results
  -> SearchSession / DiscoverySession
  -> deterministic ranking and provenance
```

The second modern path is a useful foundation, but its current interfaces still need capability and outcome refinement. The first path remains compatibility code.

## Strictly necessary going forward

These concepts are required by the target product and should be retained or redesigned:

- Stable source identity and revision.
- Explicit capabilities.
- Typed operation outcomes.
- Search session ownership.
- Progressive, cancellable, bounded search.
- Source provenance.
- Deterministic deduplication and ranking.
- Explicit migration and rollback.
- Source health and quarantine.
- Local/native source support.
- Authenticated source support, including Jellyfin.
- Explicit offline/stale result state.

## Relics to leave behind

These are not target architecture and must not receive new features:

- `SourceManager` as the primary source registry.
- `SourceRepository` search methods using `FilterList`.
- Direct `CatalogueSource` use from feature ViewModels.
- `SManga`, `SChapter`, and `MangasPage` outside the compatibility adapter.
- Search-time persistence through `NetworkToLocalManga`.
- Empty list as a substitute for unsupported or failed.
- Search ViewModels that own ranking, fallback, migration, and source health policy.
- Heuristic discovery as an authoritative opaque source.
- Jellyfin-specific branches in global search.

## Source classes and engines

| Component | Current role | Classification | Decision |
|---|---|---|---|
| `source-api/ContentSource` | Generic content identity/details/units/resources contract | **target foundation** | Keep; evolve into capability-gated contract. |
| `source-api/ContentCatalogueSource` | Catalogue/search extension of generic source | **adapter/target bridge** | Keep only where it serves native sources; do not make it universal. |
| `source-api/ContentSourceAdapter` | Tachiyomi/Mihon `Source` → generic content adapter | **compatibility** | Isolate as `LegacyExtensionAdapter`; remove after bridge gate. |
| `source-api/SourceHierarchy` | Transport-oriented hierarchy | **compatibility concept** | Reuse transport descriptors, not interface inheritance as the product model. |
| `domain/content/source/ContentSourceEngine` | Profile-based source engine contract | **compatibility contract** | Replace with capability-gated `SourceGateway`. |
| `ContentSourceOrchestrator` | Modern profile engine selection and fallback | **target concept, incomplete implementation** | Retain orchestration role; remove profile-as-source-truth assumptions. |
| `ScriptableContentSourceEngine` | QuickJS source execution | **external adapter technology** | Retain; move behind trusted `ExternalSourceAdapter` with typed results. |
| `AdaptiveHeuristicEngine` | DOM discovery and extraction | **experimental discovery assistant** | Retain only as profile proposal/validation tooling, not authoritative runtime. |
| `DynamicHttpSource` | Modern orchestrator exposed through legacy `HttpSource` | **compatibility bridge** | Isolate; delete after UI/features use the modern gateway. |
| `StubSource` | Legacy source placeholder | **compatibility** | Replace with source registry unavailable/trust state. |

## Search classes

| Component | Current role | Classification | Decision |
|---|---|---|---|
| `SearchViewModel` | Global search state, fan-out, source filtering, caching, migration dialogs | **legacy controller** | Replace with feature state + `SearchSession`; no new policy here. |
| `GlobalSearchViewModel` | Global search UX, suggestions, source filter | **feature state** | Retain UI state only; move execution/ranking out. |
| `UnifiedSearchEngine` | Legacy source fan-out, smart matching, progressive results | **compatibility engine** | Migrate to `SearchSession`; no direct `CatalogueSource` target dependency. |
| `SmartSourceSearchEngine` | Legacy title/deep-search fallback | **migration/search compatibility** | Retain for migration only until candidate matching is redesigned. |
| `SearchResultMerger` | Conservative title deduplication | **target candidate** | Generalize to normalized `ContentItem`; preserve provenance and deterministic ranking. |
| `GlobalSearchCache` | In-memory search cache | **working cache** | Keep bounded; never treat as source truth. |
| `MigrateMangaUseCase` | Source migration application | **compatibility plus target candidate** | Split candidate discovery from explicit, reversible application. |

## Jellyfin and collections

Current Jellyfin implementation is a tracker/progress integration, not a content source. The target integration must be split into:

```text
JellyfinSourceAdapter
  Search
  Details
  Catalog
  Collections
  Units
  Resources
  ProgressSync
```

Jellyfin collections must map to a generic `ContentCollection` result. Search and discovery must consume the generic capability, not branch on Jellyfin.

## Current failure semantics

The following are compatibility debt and must not be copied into the new architecture:

- `emptyList()` means unsupported.
- `emptyList()` means source failure.
- `emptyList()` triggers deep search or heuristic fallback.
- `catch (Throwable)` turns failures into empty search results.
- Search writes results into local persistence during a search operation.
- A source operation that returns no list cannot explain why it returned no list.

## Initial source sequence

1. **Local/native source:** deterministic offline baseline.
2. **Controlled native HTTP source:** validates search/details/units/resources and health.
3. **External script source:** validates revisions, trust, sandboxing, and typed failures.
4. **Jellyfin source adapter:** validates authenticated sources, collections, and progress sync.

## Evidence boundary

This inventory is static. It does not prove runtime behavior, source compatibility, or product acceptance. The next task must add call-site verification and executable contract tests before implementation begins.

## Related documents

- [Source architecture](../SOURCE_DISCOVERY_ARCHITECTURE.md)
- [Source execution contract](../SOURCE_DISCOVERY_EXECUTION.md)
- [Source boundary ADR](../adr/0007-source-is-not-a-ui-adapter.md)
- [Call-site verification](SOURCE_CALL_GRAPH.md)
- [Compatibility matrix](SOURCE_COMPATIBILITY_MATRIX.md)
- [Removal plan](SOURCE_REMOVAL_PLAN.md)

