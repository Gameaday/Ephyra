# Source Compatibility Matrix

> **Task:** `SRC-000C`
> **Status:** `CODE_COMPLETE` at evidence level `E1`

## How to read this matrix

Three independent signals decide whether a component is retired or wired. All three must agree;
no single one is sufficient.

| Signal | Question | Source of truth |
|---|---|---|
| **Age** | Which generation? Does it predate the component that replaced it? | `git log --follow --diff-filter=A` |
| **Intent** | What does the record of record call it? | this matrix, `doc/adr/` |
| **Reachability** | Does production call it? | callers in `src/main` |

**Reachability alone cannot decide it.** Zero consumers is ambiguous: it means either "dead
compatibility code" or "target solution not yet wired", and those are indistinguishable from the
source. The second is how this programme accumulated a parallel unwired source stack, so a
zero-consumer component is a question, not a verdict.

**`--follow` is required when reading the Introduced column.** Without it git reports the date a
file was *moved* to its current path, not created. `FilterList.kt` reads as added 2026-05-18
without `--follow` and 2017-01-08 with it, because `source-api` was restructured and the file
moved. Reading the bare form would date genuine 2016-2017 upstream types as recent Ephyra work —
the exact inverse of the error the column exists to prevent.

| Path | Introduced | Classification | Target destination | Removal blocker | Current decision |
|---|---|---|---|---|---|
| `ContentSource` | 2026-05-24 | target foundation | Capability-based `SourceGateway` | None | Keep and evolve. |
| `ContentCatalogueSource`, `ContentSourceAdapter` | 2026-05-24 *(removed 2026-09-30)* | removed 2026-09-30 | `LegacySourceGateway` covers the same direction | none — one implementor, zero consumers | **Deleted.** The native path never used them; `SOURCE_DISCOVERY_EXECUTION.md` records that explicitly. |
| `LegacySourceGateway` | 2026-09-24 | temporary compatibility | No permanent target model; callers move to native gateways | Product callers still use legacy source types | Keep isolated; delete at R-008. |
| `LocalSourceGateway` | 2026-09-24 | target native | `SourceGateway` over `UnifiedContentSource` | Product callers have not been migrated yet | Keep; first offline/native source path. |
| `OpdsSourceGateway` | 2026-09-24 | target native | `SourceGateway` over existing OPDS HTTP source | Product callers have not been migrated; device acceptance pending | Keep; controlled native HTTP path. |
| `SourceManager` | 2023-03-05 | compatibility | `SourceRegistry` | Search, browse, updates, reader | Keep only as bridge. |
| `SourceRepository` | 2022-04-24 | compatibility | Catalog/query gateways | Browse/migration call sites | Keep only as bridge. |
| `DynamicHttpSource` | 2026-06-04 | compatibility | Native/external source adapter | Legacy UI registration | Isolate. |
| `ScriptableContentSourceEngine` | 2026-05-26 | **REMOVED** | None | None — deleted with the JS runtime (`ADR-0013`) | Do not reference; the JS source type does not exist. |
| `AdaptiveHeuristicEngine` | 2026-05-26 | **current** | — | Sole engine for `HEURISTIC` profiles, and the orchestrator's fallback for any unbound source type | Keep. It is now the only bound engine, so "unbound" is a reachable state rather than a hypothetical. |
| `UnifiedSearchEngine` | 2026-09-12 | compatibility | `SearchSession` | Global/migration search | Retain until replacement. |
| `SmartSourceSearchEngine` | 2025-06-13 | compatibility | Candidate matching policy | Migration matching relies on it | Restrict to migration. |
| `SearchResultMerger` | 2026-09-03 | target candidate | Pure ranking/deduplication | Manga-specific model | Generalize. |
| Jellyfin tracker | — | compatibility/progress | `ProgressSync` capability | Existing settings/progress flow | Keep tracker separate. |
| Jellyfin API client | — | adapter candidate | `JellyfinSourceAdapter` | No content source contract | Extract transport later. |
| `FilterList` | 2017-01-08 | compatibility | Typed `SourceQuery` | Extension APIs | No new feature usage. |
| `SManga`/`SChapter`/`Page` | 2016-06-15 – 2025-03-31 | compatibility | Immutable target models | Extension bridge | Adapter-only. |
| `SourceProfile` | 2026-05-24 | transitional | `SourceDescriptor` + revisioned profile | Orchestrator/profile cache | Split identity from health. |
| `ContentSourceOrchestrator` | 2026-05-24 | target concept | Single source gateway orchestrator | Current fallback semantics | Retain, narrow. |
| Heuristic fallback | — | delete as default | Explicit proposed profile only | Current user flows | Remove from default path. |
| Search-time `NetworkToLocalManga` | 2022-10-26 | delete as target | Explicit import/use-case | Existing search persistence | Move persistence out. |

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
