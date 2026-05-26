# Steps Left for Build 2 — [100% COMPLETED]

This document summarizes the final progress of Ephyra's Build 2 modernization (Phase 4: Navigation, Hilt DI, & State Modernization). 

**All targets for Build 2 have been successfully accomplished with zero compile-time errors and green test validation!** The entire presentation layer, including all leaf feature screens, has been transitioned fully to official Jetpack Compose and Hilt ViewModels.

---

## 🏛️ Build 2 Achievements

- [x] **Pioneer Flow Validation**: Hilt and Compose initialization succeeds without crashes.
- [x] **Voyager Purge**: Completely decommissioned Voyager `TabNavigator` and Voyager `Screen` objects in favor of official `rememberNavController()` and standard Jetpack Navigation `composable` destinations in the root `NavHost`.
- [x] **Hilt @HiltViewModel Transition**: Replaced Koin injection with compile-safe `@Inject` and `@HiltViewModel` lifecycle containers.
- [x] **Main Thread de-blocking**: Replaced legacy main-thread `runBlocking` calls fetching preferences inside Compose screens, ViewModels, and activity lifecycles with memory-cached non-blocking `.getSync()` operations or standard background thread coroutines.
- [x] **Storage Migrations**: Integrated explicit `SharedPreferencesMigration` rules in all app-owned DataStore factories to prevent data loss.

---

## 🚀 Moving to Build 3 Action Items (High Priority)

The next step is **Build 3: Room Persistence Migration & Widget Performance**:

### Step 3.1: Room Persistence Migration (Phase 5)
* Convert all SQLDelight `.sq` database queries to native Room entities and DAOs:
  * `mangas.sq` $\rightarrow$ `MangaEntity` & `MangaDao`
  * `chapters.sq` $\rightarrow$ `ChapterEntity` & `ChapterDao`
  * `history.sq` $\rightarrow$ `HistoryEntity` & `HistoryDao`
  * `categories.sq` $\rightarrow$ `CategoryEntity` & `CategoryDao`
* Replace `SqlDriver` usages in all repositories with safe suspending Room DAOs.

### Step 3.2: Glance Widget Performance Optimization (Phase 3)
* Move heavy image loading out of the Glance composition lifecycle in `BaseUpdatesGridGlanceWidget`.
* Implement a background worker to handle async pre-caching of cover bitmaps for the updates widget.
