# Source Call-Site Verification

> **Task:** `SRC-000B`
> **Status:** `CODE_COMPLETE` at evidence level `E1`
> **Purpose:** record the actual call paths that the target source boundary must replace or isolate.

## Legacy global search path

```text
GlobalSearchViewModel
  -> SearchViewModel
  -> SourceManager
  -> CatalogueSource
  -> UnifiedSearchEngine.searchSource
  -> SmartSourceSearchEngine
  -> NetworkToLocalManga
  -> SearchItemResult
  -> SearchResultMerger
```

### Current ownership problems

- The ViewModel selects enabled sources and filters.
- The domain engine performs fan-out and fallback.
- The search operation persists to local manga.
- Ranking/merging occurs in feature presentation state.
- Errors and empty results are not capability-typed.
- Cancellation is job-local, not an explicit `SearchSession` contract.

### Target owner

```text
SearchSession
  -> SourceGateway jobs
  -> typed source outcomes
  -> immutable SearchResultSet
  -> pure RankingPolicy
  -> feature state
```

## Legacy browse and catalogue path

```text
feature browse
  -> SourceRepository.search/getPopular/getLatest
  -> SourceManager / CatalogueSource
  -> legacy FilterList + Manga
  -> source-specific browse UI
```

### Decision

`SourceRepository` remains compatibility only. New browse surfaces use `CatalogQuery` and capability-gated `SourceGateway`.

## Dynamic source path

```text
DynamicHttpSource
  -> ContentSourceOrchestrator
  -> SourceProfile
  -> ScriptableContentSourceEngine or AdaptiveHeuristicEngine
  -> legacy SManga/SChapter/Page
  -> existing UI
```

### Decision

`DynamicHttpSource` is a migration bridge. It proves the modern engine can feed legacy consumers, but it must not remain the registration path for the target source registry.

## Modern profile path

```text
ContentSourceOrchestrator
  -> SourceProfileCache
  -> resolveEngineForProfile
  -> script/heuristic engine
  -> discover/search/details/units/pages
```

### Decision

Retain the orchestrator role, but separate:

- source definition;
- source profile/revision;
- health observations;
- gateway execution;
- capability declaration.

A cached profile must not be the only source identity or health record.

## Migration path

```text
MigrateMangaUseCase
  -> target source
  -> source details/chapters
  -> candidate matching
  -> user confirmation
  -> local migration
```

### Decision

Split into:

```text
MigrationCandidateFinder
MigrationEvidence
MigrationReviewState
MigrationApplier
MigrationRollback
```

No migration candidate search may mutate the library.

## Updates and background path

```text
LibraryUpdateJob / update services
  -> source details and chapters
  -> local chapter metadata
  -> update feed
  -> library UI
```

### Decision

Updates must call a capability-gated `UpdatesGateway`; an update source is not assumed to be a search source.

## Jellyfin path

```text
Jellyfin tracker
  -> JellyfinApi
  -> read progress and metadata sync
```

### Decision

Jellyfin remains a progress provider until a separate source adapter is implemented. The tracker API must not be used as the future content source API.

## Direct legacy dependency locations

The following must be treated as compatibility debt:

- `feature/browse` global search ViewModels;
- `core/domain` unified/smart search engines;
- `core/domain` migration use cases;
- `app` metadata/library update workers;
- `DynamicHttpSource`;
- extension loader and extension bridge;
- source settings and source installation surfaces;
- any new feature that imports `eu.kanade.tachiyomi.source.*`.

## Verification requirements

Before replacing a path, add:

- call-site test;
- capability matrix test;
- typed-outcome test;
- cancellation/deadline test;
- zero-legacy startup/product test for the target path;
- migration rollback test where applicable.

## Related documents

- [Inventory](SOURCE_INVENTORY.md)
- [Compatibility matrix](SOURCE_COMPATIBILITY_MATRIX.md)
- [Removal plan](SOURCE_REMOVAL_PLAN.md)
