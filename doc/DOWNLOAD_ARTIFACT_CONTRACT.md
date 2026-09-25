# Download Artifact Contract

## Decision

Downloaded chapter bytes are **filesystem-owned user data**. Room may maintain a reconciled index for fast reads, but Room is never proof that an artifact exists.

The current active queue, page progress, and pending-deletion list are work state. They are not target series data and must not be inserted into the target Room schema or restored as completed downloads.

## Ownership split

```text
Download work coordinator
  owns queue, ordering, retry, progress, pause, and cancellation

Filesystem artifact store
  owns downloaded bytes and final directory/archive names

Reconciled artifact index (optional)
  maps target chapter/source/series identity to a verified relative artifact path

Reader media pipeline
  consumes verified filesystem artifacts or falls back to the source
```

No layer may infer that a chapter is downloadable from queue state, backup metadata, or a stale index row.

## Durable artifact identity

A verified artifact record contains:

```text
artifact_id
chapter_id
series_id
source_id
relative_path
container (directory or CBZ)
page_count
byte_size
verified_at
```

Rules:

- IDs are stable opaque strings; legacy numeric IDs are not target identity.
- `relative_path` is relative to the configured downloads root.
- Absolute paths and parent traversal are rejected.
- `page_count` must be positive.
- `byte_size` must not be negative.
- The index row is written only after the final artifact is atomically published and verified.
- A missing, corrupt, or renamed artifact invalidates the row until reconciliation.

## Lifecycle

### Download

```text
queue work
  → write temporary artifact
  → verify pages/archive
  → atomically publish final artifact
  → verify final filesystem object
  → publish reconciled index row
  → mark work completed
```

Failure before publication leaves only removable temporary data. Failure after filesystem publication but before index publication is repaired by reconciliation; it is not re-downloaded blindly.

### Reconciliation

Reconciliation runs:

- after process restart;
- after the configured storage root changes;
- after an external filesystem modification is detected;
- before reporting a fast indexed download as available.

Reconciliation compares indexed paths with verified filesystem objects and returns explicit `Added`, `Updated`, `Removed`, or `Unchanged` outcomes.

### Rename

Source, series, and chapter display-name changes may rename paths for compatibility. A successful filesystem rename updates the index path. Failure leaves the old verified artifact authoritative and reports the rename failure; the index must not be changed optimistically.

### Deletion

The filesystem deletion coordinator owns ordering:

1. stop/remove relevant work;
2. delete the filesystem artifact;
3. remove the index row only after confirmed deletion;
4. persist pending deletion work separately when filesystem deletion must resume later.

A crash may leave an orphan index row, which reconciliation removes. It must not leave a false completed-download record after confirmed file deletion.

## Backup and restore

Download bytes are not embedded in metadata backups.

A restored database starts with no verified download index unless the target device independently contains matching artifacts and reconciliation verifies them. Backup metadata must never assert `downloaded = true` without a present, verified filesystem artifact.

Queue position, transient progress, retry count, and pending deletions are device-local work state and are not backup data.

## Legacy adoption

Legacy Room rows contain no durable download state. Legacy download adoption is therefore a filesystem reconciliation task:

1. migrate target series/chapter identities;
2. scan the configured legacy downloads root;
3. match existing path conventions to target chapter/source representations;
4. verify each candidate artifact;
5. publish index rows only for verified matches;
6. report ambiguous or orphaned artifacts explicitly;
7. never delete or rename legacy files during the first read-only pass.

## Acceptance criteria

- Absolute and traversal paths are rejected.
- A zero-page or negative-size artifact is invalid.
- Publication occurs only after final filesystem verification.
- Reconciliation removes missing artifacts from the index.
- Rename failure never corrupts the index.
- Backup restore cannot invent downloaded content.
- Queue/work state never appears in target series tables.
- Legacy filesystem migration is read-only until an explicit repair plan is approved.
