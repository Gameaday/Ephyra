# Ephyra Migration Inventory & Plan

This document records identified legacy/compat implementations and a prioritized migration checklist so contributors can track and implement the modern Android architecture changes.

## Purpose
- Centralize all findings (file locations + brief notes).
- Provide a prioritized actionable migration checklist and contribution guidance.

---

## Key Findings (by category)

### 1) Service locator / global DI (`CoreContainer`)
- Usage sites (examples):
  - [source-local/src/main/kotlin/ephyra/source/local/LocalSource.kt](source-local/src/main/kotlin/ephyra/source/local/LocalSource.kt#L54) — `CoreContainer.get<Json>()`
  - [source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt](source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/HttpSource.kt#L31) — `protected val network: NetworkHelper = CoreContainer.get()`
  - [presentation-widget/src/main/java/ephyra/presentation/widget/BaseUpdatesGridGlanceWidget.kt](presentation-widget/src/main/java/ephyra/presentation/widget/BaseUpdatesGridGlanceWidget.kt#L63) — reads `CoreContainer.applicationContext`
  - Compose usages (remember blocks): [feature/more/src/main/kotlin/ephyra/feature/more/OnboardingScreen.kt](feature/more/src/main/kotlin/ephyra/feature/more/OnboardingScreen.kt#L25)

Notes: Replace with Hilt injection or well-defined EntryPoints; keep `CoreContainer` only for legacy extension boundary code and document it.

update: do not keep anything for legacy extensions, shims, or other historic reasons. we are not providing support for legacy extensions.


---

### 2) Synchronous/preference storage (`SharedPreferences`)
- App-owned synchronous preferences:
  - [core/download/src/main/kotlin/ephyra/core/download/DownloadStore.kt](core/download/src/main/kotlin/ephyra/core/download/DownloadStore.kt#L28)
  - [core/download/src/main/kotlin/ephyra/core/download/DownloadPendingDeleter.kt](core/download/src/main/kotlin/ephyra/core/download/DownloadPendingDeleter.kt#L18)
  - [app/src/main/java/ephyra/app/track/DelayedTrackingStore.kt](app/src/main/java/ephyra/app/track/DelayedTrackingStore.kt#L14)
  - Source preferences shim: [source-api/src/main/kotlin/eu/kanade/tachiyomi/source/ConfigurableSource.kt](source-api/src/main/kotlin/eu/kanade/tachiyomi/source/ConfigurableSource.kt#L15)
  - PreferenceRestorer (restore commit): [core/data/src/main/java/ephyra/data/backup/restore/restorers/PreferenceRestorer.kt](core/data/src/main/java/ephyra/data/backup/restore/restorers/PreferenceRestorer.kt#L54)

Notes: Migrate to AndroidX DataStore (Preferences or Proto). Wrap any extension-required SharedPreferences behind adapters.

---

### 3) Blocking / coroutine anti-patterns (`runBlocking`, constructor-blocking reads)
- Constructor/initialization blocking examples:
  - [feature/migration/list/MigrationListScreenModel.kt](app/src/main/java/ephyra/feature/migration/list/MigrationListScreenModel.kt#L42) — `runBlocking` used to read preferences at ViewModel init
  - [presentation-core/src/main/java/ephyra/presentation/core/util/system/DisplayExtensions.kt](presentation-core/src/main/java/ephyra/presentation/core/util/system/DisplayExtensions.kt#L26) — `runBlocking` during Context prepare
  - DB internals: [data/src/main/java/ephyra/data/TransactionContext.kt](data/src/main/java/ephyra/data/TransactionContext.kt#L57) — complex `runBlocking` usage for transactions

Notes: Replace constructor-level blocking reads with `Flow`/`StateFlow` + async initialization; refactor DB transaction helpers to suspending patterns.

---

### 4) Thread synchronization / JVM locks in coroutine code
- `@Synchronized` and `synchronized(...)` sites:
  - [core/download/DownloadPendingDeleter.kt](core/download/src/main/kotlin/ephyra/core/download/DownloadPendingDeleter.kt#L31)
  - [core/common/network/interceptor/RateLimitInterceptor.kt](core/common/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/RateLimitInterceptor.kt#L61)

Notes: Replace JVM locks with coroutine `Mutex`, `Semaphore` or actor model when interacting with coroutine-based flows.

---

### 5) Blocking startup constructs and interceptors
- `CountDownLatch` usage: [app/src/main/java/ephyra/app/startup/StartupGuard.kt](app/src/main/java/ephyra/app/startup/StartupGuard.kt#L7)
- Interceptors that use latches: [core/common/.../WebViewInterceptor.kt](core/common/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/WebViewInterceptor.kt#L19)

Notes: Prefer `withTimeoutOrNull` or non-blocking suspend designs to avoid ANRs and thread starvation.

---

### 6) Widget image loading / blocking Coil calls
- `Coil.executeBlocking` in widget: [presentation-widget/BaseUpdatesGridGlanceWidget.kt](presentation-widget/src/main/java/ephyra/presentation/widget/BaseUpdatesGridGlanceWidget.kt#L153)

Notes: Glance constraints may force blocking; enforce timeouts and safe fallbacks. Consider pre-caching bitmaps in an async worker or using `ImageProvider` flows.

---

## Prioritized Migration Checklist (short)
1. Remove `runBlocking` from UI constructors and replace with Flow/async init. (High priority)
2. Convert app-owned `SharedPreferences` stores to DataStore (DownloadStore, PendingDeleter, TrackingStore). (High)
3. Phase out `CoreContainer` in UI/Compose; replace with Hilt injection/EntryPoints. (High)
4. Replace JVM locks in coroutine code with coroutine primitives. (Medium)
5. Refactor DB transaction helpers (`TransactionContext`) to suspending and remove `runBlocking` thread hacks. (High)
6. Replace blocking interceptors and startup latches with coroutine timeouts. (Medium)
7. Harden widget image loading and remove blocking calls where possible. (Low/medium)
8. Remove legacy extension shims where safe and document compatibility boundaries. (Low)
9. Update manifest/R8 rules, tests, and docs; open incremental PRs. (High)

---

## Contributor Guide / PR process
- Branch naming: `migration/<category>-<short-desc>` (e.g. `migration/datastore-downloads`)
- Open small incremental PRs per checklist item.
- Each PR must include:
  - Migration description and rationale
  - Tests (unit/integration) demonstrating behavior parity
  - Performance notes (if applicable)
- Run `./gradlew assembleDebug` and unit tests locally.

## Next steps (immediate)
- Start migrating `DownloadStore` → DataStore as the first concrete implementation (I can create the DataStore adaptor and update usages in `core/download`).

---

## References (quick links)
- `DownloadStore`: [core/download/src/main/kotlin/ephyra/core/download/DownloadStore.kt](core/download/src/main/kotlin/ephyra/core/download/DownloadStore.kt#L1)
- `DownloadPendingDeleter`: [core/download/src/main/kotlin/ephyra/core/download/DownloadPendingDeleter.kt](core/download/src/main/kotlin/ephyra/core/download/DownloadPendingDeleter.kt#L1)
- `MigrationListScreenModel` (runBlocking in VM): [app/src/main/java/ephyra/feature/migration/list/MigrationListScreenModel.kt](app/src/main/java/ephyra/feature/migration/list/MigrationListScreenModel.kt#L1)

---

(If you want, I can now implement the `DownloadStore` → DataStore migration in a small PR and run the build/tests.)
