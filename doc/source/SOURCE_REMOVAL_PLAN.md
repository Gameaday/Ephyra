# Source Legacy Removal Plan

> **Task:** `SRC-000C`
> **Status:** planning artifact; removal is not yet authorized.

## Removal principles

- Do not delete a working path before its replacement passes the same product scenario.
- Do not preserve legacy behavior merely because it is tested.
- Do not let a compatibility adapter become a dependency of a new feature.
- Every removal step must have a replacement, evidence, and rollback boundary.

## Removal sequence

### R-001 — Freeze new legacy dependencies (complete)

- Add architecture checks for new feature imports of `eu.kanade.tachiyomi.source.*`.
- New source code must use target contracts.
- Existing legacy imports become an explicit allowlist.

`SourceApiBoundaryTest` enforces this for the target source API. The current allowlist is limited to the existing compatibility ABI/adapter files; it is not permission for new target code to use legacy types.

### R-002 — Introduce the target gateway (complete)

- Add `SourceDescriptor`.
- Add capability declarations.
- Add typed operation outcomes.
- Add `SourceGateway`.
- Add capability contract tests.

### R-003 — Introduce local and native sources

- Implement local source adapter.
- Implement one controlled native HTTP source adapter.
- Prove search, details, units, resources, health, offline behavior, and cancellation.

### R-004 — Replace global search execution (in progress)

- Introduce `GlobalSearchCoordinator` as the target product seam over `NativeSourceRegistry` and `SearchSession`.
- Introduce `TargetSearchMapper` as a pure, source-neutral product presentation boundary.
- Introduce `TargetSearchCommand` for explicit open-details, persist, and add-to-library actions.
- Define `DurableSeriesIdentity` and `SeriesRepository` before changing Room. **Complete as a pure contract**; follow `../TARGET_DATA_SCHEMA.md` for the target schema.
- `DATA-001A`: implement target Room entity/DAO contracts and migration fixtures in an isolated boundary; do not mutate `@Database(version = 3)` yet. **Complete as an isolated contract.**
- `DATA-001B`: define and test reversible legacy-to-target mapping plans. **Complete as a pure mapper**; Room and backup execution remain open.
- Move fan-out, deadlines, partial results, and cancellation out of `SearchViewModel` in a later product seam.
- Move deduplication and ranking into pure domain code.
- Remove search-time persistence.

`SearchSession`, `NativeSourceRegistry`, `GlobalSearchCoordinator`, and `TargetSearchMapper` are implemented and tested. `GlobalSearchViewModel` remains on the legacy execution path until an explicit `SourceContentItem` to product-library/series presentation mapping is approved. R-008 is not authorized yet.

### R-005 — Replace browse and updates surfaces

- Move browse to capability-gated catalog queries.
- Move updates to a capability-gated updates gateway.
- Keep source-specific filters in adapter-owned query models.
- Remove `FilterList` from target feature contracts.

### R-006 — Add external source lifecycle

- Define external source package format.
- Add source revision and trust metadata.
- Add install/update/remove lifecycle.
- Add sandbox and permission contract.
- Preserve source identity across updates.

### R-007 — Add authenticated sources and Jellyfin

- Add authentication capability.
- Add credential store integration.
- Add `ContentCollection`.
- Add Jellyfin source adapter.
- Keep Jellyfin progress sync separate from content retrieval.

### R-008 — Remove legacy bridge

Preconditions:

- zero-legacy startup and core product scenarios pass;
- native/local sources cover required product capabilities;
- search, browse, updates, migration, and reader no longer import legacy types;
- source installation/trust has independent lifecycle;
- backup/restore and clean install are verified;
- user-facing compatibility is documented.

Delete:

- `ContentSourceAdapter` (after all callers are migrated);
- `LegacySourceGateway`;
- `DynamicHttpSource`;
- `SourceManager` compatibility interface;
- legacy `SourceRepository` search methods;
- direct `FilterList` usage;
- `SManga`/`SChapter`/legacy `Page` usage outside compatibility;
- extension service-locator support;
- obsolete source fallback and heuristic-as-authority paths.

## Rollback boundary

Until R-008 is complete, legacy paths remain available. They are not allowed to be used by new target features. A release may temporarily ship with both paths only when the compatibility path is isolated, measured, and covered by a removal date/task.

## Related documents

- [Compatibility matrix](SOURCE_COMPATIBILITY_MATRIX.md)
- [Inventory](SOURCE_INVENTORY.md)
- [Source execution contract](../SOURCE_DISCOVERY_EXECUTION.md)
