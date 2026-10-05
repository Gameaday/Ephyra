# System Touchpoint Audit — writes / caches / anti-patterns / UDF

Second-pass audit across cache and write paths, checking modern Android + UDF practice.

## Swept and verdicts

| Area | Verdict |
|---|---|
| `runBlocking` in prod code | Only `NetworkHelper` init (once at startup, documented justification) and tests. OK |
| `GlobalScope` | None found in prod. OK |
| `Preference.getSync()` on main | **Not a jank source**: `DataStorePreferenceStore.getSync()` reads an in-memory snapshot; `set()` launches async. Reader config `getSync()` calls happen at viewer init — acceptable. OK |
| Recent-searches in search suggestions | **Fixed**: read was snapshot-cheap but non-reactive — a recorded search only appeared after a query/library change. Now combines `recentSearches.observe()`. |
| `GlobalSearchCache` | Bounded LinkedHashMap LRU (12), domain models only, process-scoped, documented staleness trade for back-nav. OK |
| `SourceProfileCache` | Deprecated PreferenceStore constructor confined to tests; production on Room. OK — migration in progress, no action |
| `LibraryUpdateJob` writes | Per-source `async` + `awaitAll` batching. OK |
| Library update interval / WorkManager scheduling | Periodic workers with KEEP policies; cold-start audit added earlier. OK |
| Coil fetcher disk handling | Snapshot close-before-remove contract documented and followed. OK |
| Reader/viewer config construction | Built once per reader open, off the scroll path. OK |
| UDF shape (screens touched this arc) | Screens consume StateFlow + emit events; no direct repository calls from composables found in the sweep. OK |

## Fixed this pass
- Search suggestions now observe recents reactively (commit with this doc).

## Flagged for follow-up (larger work, not addressed)
1. **Baseline profile regeneration + macrobenchmark on device** (owner, S24) — see
   phase5-performance-audit.md.
2. **Scheduled Connected Instrumentation failure on main** — infra (adb/emulator never
   came up), unrelated to code; fix the workflow or the runner pool.
3. **SourceProfile migration completion** — retire the deprecated constructor + tests'
   fixture path once Room store is the only caller.
4. **Reader config `getSync()` cluster** (theme/brightness/paint at viewer init) —
   works today, but if reader-open latency ever profiles poorly, hoist these into a
   single remembered config object built once per chapter load.
