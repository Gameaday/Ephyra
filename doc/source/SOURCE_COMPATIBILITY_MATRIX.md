# Source Compatibility Matrix

> **Task:** `SRC-000C`
> **Status:** `CODE_COMPLETE` at evidence level `E1`

| Path | Classification | Target destination | Removal blocker | Current decision |
|---|---|---|---|---|
| `ContentSource` | target foundation | Capability-based `SourceGateway` | None | Keep and evolve. |
| `ContentCatalogueSource` | adapter/target bridge | Capability-specific contracts | Feature callers still use it | Keep temporarily. |
| `ContentSourceAdapter` | compatibility | `LegacyExtensionAdapter` | Extensions still installed | Isolate. |
| `SourceManager` | compatibility | `SourceRegistry` | Search, browse, updates, reader | Keep only as bridge. |
| `SourceRepository` | compatibility | Catalog/query gateways | Browse/migration call sites | Keep only as bridge. |
| `DynamicHttpSource` | compatibility | Native/external source adapter | Legacy UI registration | Isolate. |
| `ScriptableContentSourceEngine` | deferred | None until a dedicated repair/security/test task exists | Current path is unverified; do not claim support. |
| `AdaptiveHeuristicEngine` | deferred | Proposal/validation assistant only in a future task | Current path is unverified; never authoritative. |
| `UnifiedSearchEngine` | compatibility | `SearchSession` | Global/migration search | Retain until replacement. |
| `SmartSourceSearchEngine` | compatibility | Candidate matching policy | Migration matching relies on it | Restrict to migration. |
| `SearchResultMerger` | target candidate | Pure ranking/deduplication | Manga-specific model | Generalize. |
| Jellyfin tracker | compatibility/progress | `ProgressSync` capability | Existing settings/progress flow | Keep tracker separate. |
| Jellyfin API client | adapter candidate | `JellyfinSourceAdapter` | No content source contract | Extract transport later. |
| `FilterList` | compatibility | Typed `SourceQuery` | Extension APIs | No new feature usage. |
| `SManga`/`SChapter`/`Page` | compatibility | Immutable target models | Extension bridge | Adapter-only. |
| `SourceProfile` | transitional | `SourceDescriptor` + revisioned profile | Orchestrator/profile cache | Split identity from health. |
| `ContentSourceOrchestrator` | target concept | Single source gateway orchestrator | Current fallback semantics | Retain, narrow. |
| Heuristic fallback | delete as default | Explicit proposed profile only | Current user flows | Remove from default path. |
| Search-time `NetworkToLocalManga` | delete as target | Explicit import/use-case | Existing search persistence | Move persistence out. |

## Required compatibility properties

A compatibility path may remain only if:

- it is isolated from new feature code;
- it has capability tests;
- it preserves provenance and typed error translation;
- it has an explicit replacement task;
- it has a deletion condition;
- it cannot become an accidental target dependency.

## “Unsupported” rule

No adapter may represent an unsupported operation as an empty successful list. Translation must produce:

```text
Unsupported
```

with a capability name and source identity.

## “Empty” rule

A valid zero-result search remains:

```text
Empty
```

It must not automatically invoke deep search, heuristic fallback, or another unrelated engine.

## Initial source implementation order

1. Legacy extension adapter: currently verified compatibility path.
2. Local/native source.
3. Controlled native HTTP source.
4. Jellyfin authenticated source and collections.
5. Script source: deferred until separately repaired, secured, and tested.
6. Heuristic discovery assistant: deferred until separately validated.

## Related documents

- [Inventory](SOURCE_INVENTORY.md)
- [Call graph](SOURCE_CALL_GRAPH.md)
- [Removal plan](SOURCE_REMOVAL_PLAN.md)
