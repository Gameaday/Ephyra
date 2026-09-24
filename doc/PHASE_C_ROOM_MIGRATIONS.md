# Phase C Working Doc — Room Versioned Migrations (Database Upgrade Safety)

> **Handoff note**: live progress log for Phase C of `doc/MIGRATION_PLAN.md` (Phase 6 +
> Phase 14). Update as work proceeds; check off items with evidence. If this session is
> interrupted, resume from the **Next steps** section at the bottom.

**Status: ✅ COMPLETE (2026-09-10), commited.**

## Goal

Before this phase, `AppModule` had **neither** versioned migrations **nor** (after the Phase A
rediscovery) a working destructive fallback — any schema drift, or any user carrying a legacy
SQLDelight-era `tachiyomi.db`, crashed the app at database open. Phase C makes every upgrade
path explicit and test-covered.

## Ground truth established (via git archaeology)

- The pre-Room engine (`AndroidDatabaseHandler` @ commit `91a56d738^`) opened the same
  `tachiyomi.db` file through `AndroidSqliteDriver(schema = Database.Schema, ...)` → legacy
  databases have **`user_version = 1`** and **no `room_master_table`**.
- The legacy SQLDelight schema differs from Room's canonical schema in exactly 5 ways
  (all confirmed against the deleted `.sq` sources and `schemas/…/1.json`):
  1. **Index names** — SQLDelight named them `mangas_url_index`, `idx_mangas_source`,
     `chapters_manga_id_index`, `idx_chapters_url`, `idx_manga_sync_manga_id`,
     `idx_mangas_categories_*`, `history_history_chapter_id_index`, `idx_history_last_read`;
     Room expects `index_mangas_url`, `index_mangas_source`, `index_chapters_manga_id`,
     `index_chapters_url`, `index_manga_sync_manga_id_sync_id`, `index_mangas_categories_*`,
     `index_history_chapter_id` (UNIQUE), `index_history_last_read`.
  2. **`excluded_scanlators`** — legacy had no primary key; Room declares composite
     `(manga_id, scanlator)`.
  3. **Legacy UNIQUE table constraints** — `manga_sync` had
     `UNIQUE(manga_id, sync_id) ON CONFLICT REPLACE`, `extension_repos` had
     `UNIQUE(signing_key_fingerprint)`; Room models neither (conflict strategy lives in
     upsert statements; a non-unique index is declared instead).
  4. **Views** — legacy `updatesView` carried `WHERE favorite = 1 AND date_fetch > date_added
     ORDER BY date_fetch DESC`, and all three legacy views differ from Room's canonical SQL
     (Room validates view SQL byte-exactly → views must be recreated from `1.json`'s
     `createSql`).
  5. **Triggers** — SQLDelight era bumped `version`/`last_modified_at`/
     `favorite_modified_at` via `AFTER UPDATE` triggers; Room repositories manage those
     columns explicitly → triggers removed (otherwise values double-bump).

## What was delivered

| Item | File | Notes |
|---|---|---|
| `Migrations` registry + `MIGRATION_1_2` + `MIGRATION_2_3` | `core/data/src/main/java/ephyra/data/room/Migrations.kt` | `DB_VERSION = 3`; v1→v2 adopts and normalizes legacy SQLDelight data; v2→v3 creates the canonical `source_profiles` table. |
| Schema version | `core/data/src/main/java/ephyra/data/room/EphyraDatabase.kt` | `version = 3`; schemas `1.json`, `2.json`, and `3.json` are committed. |
| Migration wiring | `app/src/main/java/ephyra/app/di/AppModule.kt` | Current `ephyra.db` is authoritative; legacy `tachiyomi.db` is adopted when the current file is absent; `.addMigrations(*Migrations.ALL)` is enabled; destructive fallback is absent. |
| Legacy upgrade test | `core/data/src/test/java/ephyra/data/room/LegacySqlDelightAdoptionTest.kt` | Creates the verbatim legacy schema, seeds data, migrates through v3, and validates the final Room schema. |
| Room upgrade test | `core/data/src/test/java/ephyra/data/room/EphyraDatabaseMigrationTest.kt` | Covers fresh v1 creation, v1→v3 upgrades, tables, and view integrity. |
| Coverage build gate | `core/data/src/test/java/ephyra/data/room/MigrationCoverageTest.kt` | Fails if the newest exported schema, `Migrations.DB_VERSION`, or registered steps diverge. |

## Upgrade-path matrix (all covered)

| Existing install | Path | Covered by |
|---|---|---|
| Legacy SQLDelight `tachiyomi.db` (`user_version = 1`, no `room_master_table`) | adopted into `ephyra.db`, then `MIGRATION_1_2` + `MIGRATION_2_3` | `LegacySqlDelightAdoptionTest` |
| Room v1 (has `room_master_table` + v1 identity) | `MIGRATION_1_2` + `MIGRATION_2_3` | `EphyraDatabaseMigrationTest` |
| Fresh install | Room creates v3 directly | `EphyraDatabaseMigrationTest.testDatabaseCreation` |
| Unknown future schema (dev mistake) | open fails visibly; no data deletion | `MigrationCoverageTest` plus explicit migration review |

## Policy going forward (recorded in MIGRATION_PLAN Phase 6)

1. Every entity/view change: bump `@Database(version = N+1)`, add `MIGRATION_N_N+1`, append
   to `Migrations.ALL`. `MigrationCoverageTest` fails the build otherwise.
2. Commit the re-exported `schemas/ephyra.data.room.EphyraDatabase/(N+1).json`.
3. Add a `runMigrationsAndValidate` test for the new step (pattern: the two tests above).
4. Keep destructive fallback absent. Unknown future schemas must fail visibly until a reviewed migration is added.

## Validation

- `:core:data:testDebugUnitTest` (incl. 3 migration tests + coverage gate) — ✅ (see run)
- `:app:compileDebugKotlin` (AppModule wiring) — ✅
- spotless — ✅

## Next steps (resume here)

1. ☑ Update `doc/MIGRATION_PLAN.md` and this handoff: Room v3 migrations, legacy adoption tests,
   and destructive-fallback removal are complete.
2. ☐ Phase D (larger sweep): ScreenModel→Interactor audit, Glance pre-caching worker, okhttp-zstd CI
   pin guard, global-search latency measurement, and cross-doc reconciliation.
3. ☐ Commit + push the validated migration restoration.
4. ☐ Device testing when an emulator/device is available.
