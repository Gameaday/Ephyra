# Phase C Working Doc — Room Versioned Migrations (Database Upgrade Safety)

> **Handoff note**: live progress log for Phase C of `doc/MIGRATION_PLAN.md` (Phase 6 +
> Phase 14). Update as work proceeds; check off items with evidence. If this session is
> interrupted, resume from the **Next steps** section at the bottom.

**Status: ✅ COMPLETE (2026-09-10), pending commit/push.**

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
| `Migrations` registry + `MIGRATION_1_2` | `core/data/src/main/java/ephyra/data/room/Migrations.kt` | `DB_VERSION = 2`; steps: normalize indices (legacy dropped, Room's recreated), rebuild `excluded_scanlators` (composite PK), rebuild `manga_sync` + `extension_repos` (drop legacy UNIQUE constraints, preserve rows/ids), drop all legacy triggers (dynamically enumerated), recreate all 3 views from canonical `createSql` (raw strings, byte-identical to `1.json`) |
| Schema version bump | `core/data/src/main/java/ephyra/data/room/EphyraDatabase.kt` | `version = 2` with strategy comment; KSP re-exports `2.json` on compile |
| Migration wiring | `app/src/main/java/ephyra/app/di/AppModule.kt` | `.addMigrations(*Migrations.ALL)` added; `fallbackToDestructiveMigration` KEPT as documented last resort (now inert for 1→2; only fires on a *missing* migration — a schema-change mistake; remove at schema freeze) |
| Legacy upgrade test | `core/data/src/test/java/ephyra/data/room/LegacySqlDelightAdoptionTest.kt` + fixture `LegacySqlDelightSchema.kt` | Creates the **verbatim legacy SQLDelight schema** (tables/indices/triggers/views recovered byte-for-byte from `91a56d738^`), seeds every table, runs `runMigrationsAndValidate(…, 2, MIGRATION_1_2)`, asserts: data survived, views canonical & queryable, 0 triggers remain, legacy index names gone, Room's 7 expected indices present |
| Room v1→v2 regression test | `EphyraDatabaseMigrationTest.testMigrateRoomV1ToV2` | Proves `MIGRATION_1_2` is idempotent for already-canonical Room-v1 databases (existing nightly installs) |
| Coverage build gate | `core/data/src/test/java/ephyra/data/room/MigrationCoverageTest.kt` | Plain JVM: asserts `Migrations.DB_VERSION == @Database(version)` and `Migrations.ALL` covers every step `1 .. version-1` — a forgotten migration now fails the **build**, not user databases |

## Upgrade-path matrix (all covered)

| Existing install | Path | Covered by |
|---|---|---|
| Legacy SQLDelight `tachiyomi.db` (`user_version = 1`, no `room_master_table`) | `MIGRATION_1_2` upgrade | `LegacySqlDelightAdoptionTest` |
| Room v1 (current nightly, has `room_master_table` + v1 identity) | `MIGRATION_1_2` (no-op-normalizing) | `EphyraDatabaseMigrationTest.testMigrateRoomV1ToV2` |
| Fresh install | Room creates v2 directly | `EphyraDatabaseMigrationTest.testDatabaseCreation` (now validates v2) |
| Unknown future schema (dev mistake) | destructive fallback (documented last resort) | gated by `MigrationCoverageTest` |

## Policy going forward (recorded in MIGRATION_PLAN Phase 6)

1. Every entity/view change: bump `@Database(version = N+1)`, add `MIGRATION_N_N+1`, append
   to `Migrations.ALL`. `MigrationCoverageTest` fails the build otherwise.
2. Commit the re-exported `schemas/ephyra.data.room.EphyraDatabase/(N+1).json`.
3. Add a `runMigrationsAndValidate` test for the new step (pattern: the two tests above).
4. `fallbackToDestructiveMigration` stays only until schema freeze (pre-production); remove
   it once the migration policy has proven itself for one release.

## Validation

- `:core:data:testDebugUnitTest` (incl. 3 migration tests + coverage gate) — ✅ (see run)
- `:app:compileDebugKotlin` (AppModule wiring) — ✅
- spotless — ✅

## Next steps (resume here)

1. ☐ Commit + push Phase C (per user requirement: every phase pushed at the end).
2. ☐ Update `doc/MIGRATION_PLAN.md` Phase 6 + Phase 14 checkboxes (Room versioned migrations ✅,
   migration unit tests ✅, legacy SQLDelight → Room v1+ ✅; only "remove destructive fallback
   at schema freeze" remains open).
3. ☐ Phase D (larger sweep): Phase 4 ScreenModel→Interactor audit, Glance pre-caching worker,
   okhttp-zstd CI pin guard, global-search latency measurement, cross-doc reconciliation.
4. ☐ Device testing (user: blocked until these phases are pushed to git).
