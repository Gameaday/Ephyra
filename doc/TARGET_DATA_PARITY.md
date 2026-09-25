# Target Data Parity Matrix

> **Task:** DATA-001I
> **Status:** audit complete; production cutover remains blocked
> **Authority:** target schema is isolated; Room v3 remains production authority

## Purpose

This matrix compares the current production Room/backup model with the isolated target model. It exists to prevent a false sense of progress from “target tables exist” or “target rows migrate successfully.”

A target capability is cutover-ready only when it has a target owner, deterministic legacy mapping, transactional behavior, backup representation, rehearsal fixture, rollback behavior, product migration, and device evidence.

## Parity matrix

| Production capability | Current target owner | Status | Cutover requirement |
|---|---|---|---|
| Series core metadata | `TargetSeriesEntity` | **Covered** | Preserve title, author, artist, description, genres, status, timestamps, and content type. |
| Source identity | `TargetSeriesSourceEntity` | **Covered, narrow** | One source representation is currently projected; full multi-source aggregation is not production-equivalent. |
| Source revision | `TargetSeriesSourceEntity.revision` | **Covered** | Require monotonic revisions and explicit stale-write conflicts. |
| Thumbnail/cover reference | `TargetSeriesSourceEntity.thumbnailUrl` | **Covered as reference** | Cover bytes/cache remain owned by the media pipeline. |
| Library membership | `TargetLibraryEntryEntity` | **Covered** | Preserve add/remove semantics and timestamps. |
| Library update enablement | `TargetLibraryEntryEntity.updateEnabled` | **Partial** | Legacy update strategy/interval mapping is incomplete. |
| Library sort position | `TargetLibraryEntryEntity.librarySortPosition` | **Partial** | Production sort semantics and UI ownership are not proven. |
| Categories | No target entity yet | **Missing** | Model definitions, membership, order, flags, and transactions. |
| Excluded scanlators | No target entity yet | **Missing** | Preserve as explicit series/policy state. |
| Chapter metadata | `TargetChapterEntity` | **Covered** | Preserve URL, title, scanlator, number, order, revision, timestamps. |
| Chapter source identity | `TargetChapterEntity` | **Partial** | Stable external chapter IDs need source-specific mapping. |
| Read state | `TargetChapterStateEntity.isRead` | **Covered** | Preserve source refresh versus user-state separation. |
| Bookmark | `TargetChapterStateEntity.bookmarked` | **Covered** | Preserve explicit user-owned state. |
| Reader progress | `TargetChapterStateEntity.lastPageRead` | **Covered in field** | Validate semantics across filtered pages and reader modes. |
| History | `TargetHistoryEntity` | **Covered** | Preserve timestamp, duration, and referential integrity. |
| Canonical links | `TargetSeriesLinkEntity` | **Covered, not integrated** | Requires lifecycle integration and production migration. |
| Tracking | No target entity yet | **Missing** | Preserve tracker identity, remote ID, progress, status, score, dates, privacy. |
| Downloads | No target entity yet | **Missing** | Define file-system relationship and restore rules. |
| Source lifecycle | No target owner yet | **Missing** | Persist descriptors, trust, credentials, installation, enabled state. |
| Preferences | No target entity | **Outside series schema** | Keep in preference infrastructure; do not duplicate. |
| Backup format | `TargetBackupDocument` | **Covered, not wired** | Production creator/restorer must use a bridge or target format. |
| Clean install | Target fixture only | **Not proven** | Target-only database startup fixture required. |
| Legacy upgrade | Target fixture only | **Not proven** | Production-like rehearsal and rollback required. |

## Rehearsal fixture requirements

The next fixture must include at least two series and three source references:

- one library series with two source representations;
- one non-library series;
- categories with explicit order;
- tracking records;
- excluded scanlators;
- downloaded and non-downloaded chapters;
- read, unread, bookmarked, and partially-read chapters;
- history rows;
- confirmed and rejected canonical links;
- source revisions and changed cover URLs;
- one source with no stable external ID.

For each fixture, migration must report:

```text
preserved
conflicted
unsupported
requires user action
```

It must never silently drop a production capability.

## Decision

Do not expand the target schema speculatively just to make the checklist look complete. Expand it only when the owning product workflow and migration semantics are defined:

```text
parity contract
→ target entity/DAO
→ migration mapping
→ isolated fixture
→ production repository adapter
→ backup/restore bridge
→ device rehearsal
→ cutover decision
```

## Related documents

- [Target data cutover decision](TARGET_DATA_CUTOVER.md)
- [Target data schema](TARGET_DATA_SCHEMA.md)
- [Reconstruction status](REBUILD_STATUS.md)
- [Source execution](SOURCE_DISCOVERY_EXECUTION.md)
- [One owner per truth ADR](adr/0001-one-owner-per-truth.md)

## Confirmed blockers

1. No target category model; user organization would be lost.
2. No target tracking model; tracking is user-owned data.
3. No download relationship model; download directories are not derivable from series metadata.
4. No target source lifecycle store; source trust/credentials/install state are not production-persisted.
5. Multi-source aggregation is incomplete; canonical links are not yet a complete series aggregate.
6. Production backup still emits and restores legacy models.
7. No production cutover rehearsal exists.

The initial multi-series/source rehearsal is implemented in `core/data/src/test/java/ephyra/data/room/target/TargetMigrationRehearsalTest.kt`. It proves that same-title series remain distinct, source identities remain isolated, library membership is preserved, chapter state/history survive, and rerunning both migration plans is idempotent. It does not claim parity for categories, tracking, downloads, source lifecycle, or production backup restore; those remain explicit blockers.
