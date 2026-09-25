# Target Series Persistence Schema Contract

- **Status:** Proposed implementation contract
- **Date:** 2026-09-25
- **Scope:** Ephyra 2.0 target library and source persistence
- **Current implementation:** Room v3 remains the compatibility and production authority. `TargetDatabase` and its entities exist as an isolated test/future-migration boundary. `TargetSeriesRepository` provides an isolated, idempotent one-source-representation repository projection; canonical cross-source grouping is intentionally not implemented yet. A separate versioned `TargetBackupDocument` and mapper now preserve target series/source/category/chapter/library/reading/history records without reusing legacy `BackupManga`; neither target persistence nor target backup is wired into production yet. The explicit cutover decision is documented in [TARGET_DATA_CUTOVER.md](TARGET_DATA_CUTOVER.md), and the capability gap matrix is in [TARGET_DATA_PARITY.md](TARGET_DATA_PARITY.md): production cutover is deferred until parity, migration rehearsal, backup/restore, rollback, and device evidence are complete.

## Decision summary

The current `mangas` table is not the target series table. It combines legacy numeric source identity, URL identity, source metadata, library membership, reader flags, update health, migration metadata, and content type. New target code must not add more responsibilities to that row.

The target persistence model separates:

```text
series identity and metadata
source-specific references
user-owned library membership
chapters and units
reading/history state
download state
tracking state
```

## Target tables

### `series`

One row per target series identity.

Required columns:

```text
local_id                 TEXT PRIMARY KEY
content_type             TEXT NOT NULL
title                    TEXT NOT NULL
author                   TEXT NULL
artist                   TEXT NULL
description              TEXT NULL
status                   TEXT NULL
created_at               INTEGER NOT NULL
updated_at               INTEGER NOT NULL
```

Rules:

- `local_id` is opaque and app-owned.
- A series row is source-neutral.
- It must not contain numeric legacy source IDs.
- It must not contain source URLs as its primary identity.
- It must not contain credentials, library membership, reader state, download state, or tracker state.
- Source-neutral metadata is updated by explicit refresh commands.

### `series_sources`

One row per source representation of a series.

Required columns:

```text
series_id                TEXT NOT NULL REFERENCES series(local_id) ON DELETE CASCADE
source_id                TEXT NOT NULL
external_id              TEXT NULL
url                      TEXT NOT NULL
revision                 INTEGER NOT NULL DEFAULT 1
display_title            TEXT NOT NULL
thumbnail_url            TEXT NULL
source_metadata_json     TEXT NULL
last_seen_at             INTEGER NOT NULL
PRIMARY KEY (series_id, source_id)
```

Rules:

- `source_id` is a stable string from the target `SourceId`.
- `external_id` is preferred for identity when supplied.
- `url` is a fallback reference and may change.
- `revision` is monotonically increasing for a source representation.
- A source row is not library membership.
- A source row is never the canonical series identity.

### `sources`

One row per source definition available to the target application, separate from any series source reference.

Required columns:

```text
source_id                TEXT PRIMARY KEY
display_name             TEXT NOT NULL
kind                     TEXT NOT NULL
revision                 INTEGER NOT NULL
capabilities_json        TEXT NOT NULL
trust_level              TEXT NOT NULL
compatibility_level      TEXT NOT NULL
content_types_json       TEXT NOT NULL
installation_state       TEXT NOT NULL
enabled                  INTEGER NOT NULL
first_seen_at            INTEGER NOT NULL
last_changed_at          INTEGER NOT NULL
```

Rules:

- Rows are created by explicit descriptor discovery, not by searching or browsing.
- Legacy numeric source IDs are never promoted into lifecycle records without a real target descriptor.
- Descriptor changes require a monotonic revision.
- Discovery preserves install/enabled state.
- Uninstall disables the source but preserves its definition/history until an explicit retention policy removes it.
- Credentials, permission grants, tokens, and security-sensitive trust decisions are not stored here.
- Backup restores series source references; it does not silently reinstall or re-trust sources.

### `library_entries`

One row per user-owned library relationship.

Required columns:

```text
series_id                TEXT PRIMARY KEY REFERENCES series(local_id) ON DELETE CASCADE
added_at                 INTEGER NOT NULL
library_sort_position    INTEGER NULL
update_policy            TEXT NOT NULL
update_enabled           INTEGER NOT NULL
last_checked_at          INTEGER NULL
last_changed_at          INTEGER NULL
```

### `series_source_links`

One row per explicitly reviewed relationship between source representations of one canonical series.

Required columns:

```text
link_id                   TEXT PRIMARY KEY
canonical_series_id       TEXT NOT NULL REFERENCES series(local_id) ON DELETE CASCADE
canonical_source_id       TEXT NOT NULL
canonical_external_id     TEXT NULL
canonical_url             TEXT NOT NULL
source_id                 TEXT NOT NULL
external_id               TEXT NULL
url                       TEXT NOT NULL
confidence                REAL NOT NULL
evidence_json             TEXT NOT NULL
state                     TEXT NOT NULL
revision                  INTEGER NOT NULL DEFAULT 1
created_at                INTEGER NOT NULL
decided_at                INTEGER NULL
```

Rules:

- The canonical and linked representations must have different source IDs.
- Only `CONFIRMED` is active; `PROPOSED`, `REJECTED`, and `REVOKED` remain auditable history.
- A link cannot be activated automatically, including when confidence is high or evidence is exact.
- Titles and fuzzy metadata may rank candidates but cannot create a link without explicit confirmation.
- Rejection and revocation do not delete the source representation or user-owned series state.
- The target backup must preserve proposed, confirmed, rejected, and revoked links.



```text
DurableSeriesIdentity {
    sourceId
    externalId?
    url
    contentType
}
```

Resolution order:

1. Match `(sourceId, externalId)` when external ID exists.
2. Otherwise match `(sourceId, normalizedUrl)` only within the same source.
3. Otherwise create a new local series.
4. A different source may create another source representation, but it must not silently merge series by title.
5. Cross-source migration/canonical linking is an explicit user-confirmed operation with a reversible relationship.

URL normalization must be source-owned and must not be invented in the database layer.

## Write rules

Search/browse:

```text
may present a SourceContentItem
may not write series
may not write library_entries
```

Open details:

```text
may resolve a source representation
may not add the series to the library
```

Add to library:

```text
upsert series
upsert source representation
insert library_entry
```

Refresh:

```text
match source representation
update source revision
update source-neutral metadata only when policy allows
preserve user-owned library state
```

Delete source representation:

```text
never implicitly delete the series
never implicitly remove library membership
never delete user-owned reading history
```

## Migration policy

The current Room v3 schema and backup format are compatibility assets, not target design.

Before target tables are activated:

1. Define explicit Room entities and DAOs for the target tables.
2. Define a reversible legacy-to-target mapping.
3. Preserve all user-owned state: library membership, categories, reading history, bookmarks, downloads, tracking, and reader progress.
4. Add schema export files and migration tests.
5. Add backup/restore round-trip tests.
6. Run a clean-install and legacy-upgrade fixture matrix.
7. Verify source references with and without stable external IDs.
8. Verify duplicate and cross-source identity cases.
9. Only then migrate production call sites.

The target schema must not be added as unused tables while the old repository remains authoritative. Either the target store is isolated behind an adapter with explicit dual-read/write rules, or the migration is a deliberate clean-slate boundary.

## Forbidden design shortcuts

- Adding `sourceId: String` columns to the legacy `mangas` table and calling it a target schema.
- Treating URL as globally unique across sources.
- Treating title as a unique identity.
- Storing encrypted credentials or plaintext credentials on a series row.
- Making search insert library records.
- Making browse own source health.
- Making the ViewModel reconcile persistence.
- Adding a second mutable series cache beside Room.
- Deleting legacy tables before backup, restore, and legacy readers are proven.

## Acceptance tests

The schema is not accepted until tests prove:

- identity precedence and URL fallback;
- source-reference replacement;
- explicit library membership;
- no search-time library writes;
- source removal preserving user state;
- cross-source migration remaining explicit;
- backup/restore preserving target records;
- legacy migration preserving user-owned state;
- schema upgrade and rollback policy;
- no target repository imports legacy numeric source IDs.

## Current next gate

```text
DATA-001H — target repository production cutover decision
```

The target backup format is source-neutral and versioned, but production backup/restore remains legacy-only until the target repository owns reads/writes and the cutover is explicitly approved.

Rules:

- Only this table owns library membership.
- Search and browse must not insert into this table.
- `add-to-library` is an explicit command.
- Removing a source reference must not remove the series from the library while another valid source reference exists.

### `chapters`

One row per target chapter/unit identity.

Required columns:

```text
local_id                 TEXT PRIMARY KEY
series_id                TEXT NOT NULL REFERENCES series(local_id) ON DELETE CASCADE
source_id                TEXT NOT NULL
external_id              TEXT NULL
url                      TEXT NOT NULL
title                    TEXT NOT NULL
unit_number              REAL NULL
scanlator                TEXT NULL
sort_key                 TEXT NOT NULL
revision                 INTEGER NOT NULL DEFAULT 1
published_at             INTEGER NULL
fetched_at               INTEGER NULL
```

Rules:

- Chapters belong to a series.
- A chapter may have multiple source representations only when the explicit unit identity model supports it; otherwise source-specific duplicates are not silently merged.
- `sort_key` is the canonical order used by navigation.
- Read/bookmark state does not belong in the source-neutral series row.

### Reading and user state

Reading state remains separate:

```text
chapter_read_state
reading_history
reader_sessions
reader_page_progress
```

Minimum ownership:

- `chapter_read_state`: read/bookmark/last page, keyed by chapter identity.
- `reading_history`: session timestamps and read duration.
- `reader_sessions`: active/recoverable reader session snapshots.
- `reader_page_progress`: last page and completion state, independent of current session.

Downloads and trackers must also remain separate stores. A series row must never become a catch-all persistence object.
