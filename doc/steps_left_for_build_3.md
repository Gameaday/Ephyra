# Steps Left for Build 3 — [Room Migrated & SQLDelight Decommissioned]

This document summarizes the current progress of Ephyra's Build 3 database and widget performance modernization (Phase 11: Room Persistence Migration & Decommissioning).

---

## 🏛️ Build 3 Achievements

- [x] **Room Entity & DAO Migration**: Relational schemas (`MangaEntity`, `ChapterEntity`, `HistoryEntity`, `CategoryEntity`, `TrackEntity`, `ExtensionRepoEntity`, `ExcludedScanlatorEntity`, `SourceEntity`) are fully migrated to Jetpack Room, utilising native `Flow` and `Paging 3`.
- [x] **Repository Layer Porting**: Fully transitioned repository classes (such as `MangaRepositoryImpl` and `ChapterRepositoryImpl`) from SQLDelight to native Room DAOs.
- [x] **Decommissioned SQLDelight**:
  - [x] Deleted all unused SQLDelight source files (`AndroidDatabaseHandler.kt`, `DatabaseHandler.kt`, `QueryPagingSource.kt`, `TransactionContext.kt`, `DatabaseAdapter.kt`).
  - [x] Deleted all legacy `.sq` database schema folders and files.
  - [x] Excised the SQLDelight compiler plugin and dependencies from `build.gradle.kts`, `data/build.gradle.kts`, and `libs.versions.toml`.
- [x] **Verified Compilation and Tests**: Green build compilation and all 362+ JVM unit tests successfully passing cleanly.

---

- [x] **Pre-Caching Cover Bitmaps**: Refactored [BaseUpdatesGridGlanceWidget.kt](file:///C:/Project/Android/Ephyra/presentation-widget/src/main/java/ephyra/presentation/widget/BaseUpdatesGridGlanceWidget.kt) to enforce a strict local cache-only strategy during composition rendering.
- [x] **Implement Background Updates Worker**: Added [WidgetUpdatesJob.kt](file:///c:/Project/Android/Ephyra/app/src/main/java/ephyra/app/data/work/WidgetUpdatesJob.kt) (WorkManager) registered in [AppWorkerFactory.kt](file:///c:/Project/Android/Ephyra/app/src/main/java/ephyra/app/data/work/AppWorkerFactory.kt) to pre-cache cover images asynchronously and reflectively update the widgets.

