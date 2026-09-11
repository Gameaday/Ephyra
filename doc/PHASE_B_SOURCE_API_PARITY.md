# Phase B Working Doc — Source-API Parity Audit (tachiyomix 1.6)

> **Handoff note**: this file is the live progress log for Phase B of
> `doc/MIGRATION_PLAN.md` (Phase 14). Update it as work proceeds; check off items with
> evidence. If this session is interrupted, the next person resumes from the
> **Next steps** section at the bottom.

**Status: ✅ COMPLETE (2026-09-10).** See *Audit checklist* below for the full evidence table.

**Session log**
- Phase A (reader viewer fix, `memo` fix, Room boot-safety, doc reconciliation, 2 broken tests
  fixed) — committed as `a3a1a8ca1` ("test").
- Phase B (this session): parity audit + `SourceApiContractTest.kt` (8 tests) + doc updates —
  **uncommitted working tree at time of writing**; commit it when convenient.

**Baseline**: Mihon upstream `main` @ sha `1e054ea14d551f5f16c8dd892bfb5962be2426d2`
(source-api tree listing fetched via GitHub contents API on 2026-09-10).

## Scope & method

Extensions are compiled against upstream tachiyomix source-api and **dynamically linked**
against the host's classes at runtime. Any member an extension references (property getter /
setter, method, constant, class) that is missing or binary-incompatible on the host crashes
the extension with `IncompatibleClassChangeError`, surfaced by `ExtensionCallBoundary` as
"Source 'X' encountered an error". The Kotlin compiler cannot catch this — only a member-level
diff against upstream (plus contract tests) can.

### Upstream `source-api` file inventory (complete, from contents API)

- `source/`: `CatalogueSource.kt`, `ConfigurableSource.kt`, `Source.kt`, `SourceFactory.kt`,
  `UnmeteredSource.kt`
- `source/model/`: `Filter.kt`, `FilterList.kt`, `MangasPage.kt`, `Page.kt`, `SChapter.kt`,
  `SChapterImpl.kt`, `SManga.kt`, `SMangaImpl.kt`, `SMangaUpdate.kt`, `UpdateStrategy.kt`
- `source/online/`: `HttpSource.kt`, `ParsedHttpSource.kt`, `ResolvableSource.kt`

Ephyra's `source-api` contains the same set **plus** Ephyra-specific additions
(`source/PreferenceScreen.kt`, `ephyra.source.api.*` content-source layer, `AppInfo.kt`,
`util/JsoupExtensions.kt`). Extra host-side types are additive and ABI-safe; **missing
members are what we're hunting**.

## Audit checklist — **COMPLETE (2026-09-10)**

| File | Verdict | Findings |
|---|---|---|
| `model/SManga.kt` | ✅ fixed (Phase A) | Added `var memo: JsonObject` + `copy()` copy — was the only ABI gap |
| `model/SMangaImpl.kt` | ✅ fixed (Phase A) | `override var memo = JsonObject(emptyMap())` |
| `model/SChapter.kt` | ✅ fixed (Phase A) | Added `var memo: JsonObject` + `copyFrom()` copy |
| `model/SChapterImpl.kt` | ✅ fixed (Phase A) | `override var memo = JsonObject(emptyMap())` |
| `model/Page.kt` | ✅ identical | `@Serializable open class Page(...)` — byte-for-byte match incl. `uri`, `statusFlow`, `progressFlow`, `State` entries |
| `model/Filter.kt` | ✅ identical | all `Header/Separator/Select/Text/CheckBox/TriState/Group/Sort` + `STATE_*` constants |
| `model/FilterList.kt` | ✅ identical | `@Stable data class` delegating to `List` |
| `model/MangasPage.kt` | ✅ compatible superset | Upstream switched to a regular class with *deprecated* `component1/component2/copy`; Ephyra's `data class` generates the **same JVM signatures** for those members plus `equals/hashCode/toString`. ABI-superset — pinned by contract test |
| `model/SMangaUpdate.kt` | ✅ identical | |
| `model/UpdateStrategy.kt` | ✅ identical | `ALWAYS_UPDATE` / `ONLY_FETCH_ONCE` |
| `Source.kt` | ✅ compatible superset | All 1.6 suspend members present (`getPopularManga/getLatestUpdates/getSearchManga/getMangaUpdate(4-arg)/getPageList`) + deprecated `fetch*` trio. Ephyra additionally keeps 1.5-era `getMangaDetails`/`getChapterList` suspend helpers with default impls (additive; old extensions that override them still bind) |
| `CatalogueSource.kt` | ✅ matches | `fetch*` observables + `awaitSingle`-bridged suspend overrides identical; Ephyra re-declares `supportsLatest` (additive) |
| `ConfigurableSource.kt` | ✅ ABI-identical | Same members; Ephyra resolves `Context` via `CoreContainer`/Injekt shim instead of `Injekt.get<Context>()` — same JVM signatures, and the shim registers `Context` (verified in `CoreContainerInitializer` L89) |
| `SourceFactory.kt` | ✅ identical | |
| `UnmeteredSource.kt` | ✅ identical | |
| `online/HttpSource.kt` | ✅ compatible superset | Every upstream member present and matching: `baseUrl`, `getHomeUrl`, `versionId`, `id`(lazy `generateId`), `headers`, `client`, `network` (Ephyra marks it `open` — additive), `headersBuilder`, `toString`, full `fetch*`/`*Request`/`*Parse` set, `fetchMangaDetails`+`getMangaDetails`, `fetchChapterList`+`getChapterList`, `fetchPageList`+`getPageList`, `fetchImageUrl`+`getImageUrl` (1.6 suspend), `getImage(page, existingSize)`, `setUrlWithoutDomain` ×2, `getMangaUrl`, `getChapterUrl`, `prepareNewChapter` |
| `online/ParsedHttpSource.kt` | ✅ matches | All selectors/from-element/parse members identical (Ephyra drops the `@Deprecated` annotations on overrides — annotation-only difference, no ABI impact) |
| `online/ResolvableSource.kt` | ✅ identical | incl. `UriType` sealed entries |
| `util/JsoupExtensions.kt` (`eu.kanade.tachiyomi.util`) | ✅ present | `asJsoup(Response, String?)`, `selectText`, `selectInt`, `attrOrText` — the package extensions import |
| Injekt shim / `CoreContainer` bridge | ✅ verified | `CoreContainerInitializer` registers `Context`, `Application`, `SharedPreferences`, `NetworkHelper`, `OkHttpClient`, `PreferenceStore`, `BasePreferences`, `CoverCache`, `SourceManager`, `Json`, `XML` into the Injekt shim **and** the `CoreContainer` fallback — so `HttpSource.network by injectLazy()` and `ConfigurableSource.getSourcePreferences()` resolve at runtime. Covered by `CoreContainerContractTest` |
| `PreferenceScreen.kt` | ✅ verified | `typealias PreferenceScreen = androidx.preference.PreferenceScreen`; `source-api` exposes `preferencektx` as an `api` dependency so the class is on the extension classpath |

**Verdict: the only ABI gap in the entire `source-api` surface was `memo` (fixed in Phase A).**

## New guards added (Phase B)

`source-api/src/test/kotlin/eu/kanade/tachiyomi/source/SourceApiContractTest.kt` — 8 tests
pinning the verified ABI so future refactors can't silently break extensions:
1. `Source` suspend surface (1.6 + 1.5 helpers + deprecated `fetch*`)
2. `CatalogueSource` fetch observables
3. `HttpSource` full surface (incl. suspend `getImageUrl`, `setUrlWithoutDomain` receiver extensions, `generateId`)
4. `MangasPage` destructuring/copy ABI
5. `Page.State` entries + `Filter.TriState` constants + `UpdateStrategy` entries
6. `UriType` entries for `ResolvableSource` implementors
7. `ConfigurableSource` preference helpers (file facade + interface)
8. `JsoupExtensionsKt` helpers in the package extensions import

Together with Phase A's `SourceModelContractTest` (memo accessors/copy semantics).

## Validation (Phase B)

- `:source-api:test` + `:source-api:spotlessCheck` — ✅ BUILD SUCCESSFUL
- Full `compileDebugKotlin` + `testDebugUnitTest` across all modules — ✅ (see pb_full.log run)

## Next steps (resume here)

1. ☐ On-device validation with a real Mangabat/MangaDex extension: details → chapter list →
   reader render on all five reading modes; no `IncompatibleClassChangeError` in logcat.
   *(cannot be automated from this workspace — requires an emulator/device)*
2. ☐ Phase C: Room versioned migrations + legacy SQLDelight → Room v1 migration +
   `MigrationTestHelper` unit tests; replace `fallbackToDestructiveMigration`.
3. ☐ Phase D: remaining MIGRATION_PLAN items (Phase 4 ScreenModel→Interactor audit,
   Glance pre-caching worker, okhttp-zstd CI pin guard, global-search latency measurement,
   cross-doc reconciliation).

