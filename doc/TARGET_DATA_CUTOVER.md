# Target Data Cutover Decision

> **Decision ID:** DATA-001H
> **Decision:** DEFER PRODUCTION CUTOVER
> **Evidence level:** E2 — isolated implementation and fixture tests are complete; production/device evidence is not complete.

## Decision

Do not add the target tables to the live `EphyraDatabase`, do not replace production repository bindings, and do not remove the legacy schema yet.

The target data path remains an explicitly isolated future authority:

```text
TargetDatabase
TargetSeriesRepository
TargetSeriesLinkRepository
TargetMigrationWriter
TargetBackupMapper
```

Room v3 remains the production authority until the cutover gates below are satisfied.

## Why the decision is correct

The isolated target implementation has proven useful contracts, but it is not yet a production-equivalent authority:

- Production Room v3 still owns manga, chapters, categories, history, updates, tracks, downloads, extensions, and source profiles.
- Production backup creation still emits legacy `BackupManga` records through `MangaBackupCreator`.
- Production restore still writes through `MangaRestorer` and `EphyraDatabase`.
- The target backup is versioned and source-neutral, but is not wired into production backup/restore.
- The target repository currently models one source representation; cross-source grouping and all user-owned relationships are not yet equivalent to the production model.
- Source identity, category membership, tracking, downloads, update policy, and reader progress need a complete production parity matrix.
- No clean-install, upgrade, restore, rollback, or device rehearsal has been run against the target authority.

Activating the target tables now would create a dangerous split-brain system: search and discovery may use one identity model while the library, backup, reader, and extension compatibility paths continue using another.

## Non-negotiable cutover gates

A future `DATA-001I` may authorize cutover only when all of the following are evidenced:

1. Target Room schema is exported and validated by Room.
2. Target schema is activated behind an explicit, reversible versioned migration.
3. Every production series relationship has a target owner and tested mapping:
   - source references;
   - canonical links;
   - categories and category order;
   - library membership and update policy;
   - chapter identity, ordering, read/bookmark/progress state;
   - history;
   - downloads;
   - tracking;
   - source-specific metadata and revisions.
4. Production read paths are cut over behind a single repository boundary, not one feature at a time.
5. Production writes are cut over behind the same boundary and tested transactionally.
6. Backup creation and restore use the versioned target format or a documented conversion bridge.
7. Legacy backup restore is proven to create a valid target database without data loss.
8. Clean install creates a valid target-only database.
9. Legacy upgrade creates a valid target database and preserves all user-owned state.
10. A failed migration leaves the previous database valid and usable.
11. Rollback does not require destructive fallback.
12. Device tests cover library, search, source details, reader open, history, backup, restore, process death, and upgrade.
13. No production call site imports legacy source DTOs for the cutover path.
14. `CLEAN-SOURCE-001` removal prerequisites are satisfied for the affected paths.

## Migration rehearsal requirements

Before cutover, create a separate production-like fixture containing:

- clean install;
- Room v3 upgrade;
- legacy SQLDelight adoption;
- library with categories;
- multiple source representations;
- confirmed and rejected canonical links;
- downloaded chapters;
- read and unread chapters;
- bookmarks and page progress;
- history rows;
- tracking rows;
- source revisions and thumbnail changes.

Run the target migration against each fixture and compare:

```text
series count
source reference count
library membership
categories/order
chapter count/order
read/bookmark/progress
history
download references
tracking
canonical link lifecycle
```

A migration is not complete merely because target tables contain rows.

## Rollback boundary

Until the cutover gate is approved:

- Room v3 remains authoritative.
- Target writes are not allowed from production UI/background workers.
- Target backup is not accepted as the only production backup.
- No legacy table or compatibility test is deleted.
- No destructive fallback is used to recover from a failed target migration.

If a future cutover is approved, rollback must be an explicit database release/migration decision, not a runtime destructive reset.

## Current next task

`DATA-001I — production parity matrix and migration rehearsal fixture`

The next task must document and test the missing user-owned relationships and state preservation. It must not mutate the live database.

## Related documents

- [Target data schema](TARGET_DATA_SCHEMA.md)
- [Target data cutover decision](TARGET_DATA_CUTOVER.md)
- [Reconstruction status](REBUILD_STATUS.md)
- [Source execution](SOURCE_DISCOVERY_EXECUTION.md)
- [Source removal plan](source/SOURCE_REMOVAL_PLAN.md)
- [One owner per truth ADR](adr/0001-one-owner-per-truth.md)
