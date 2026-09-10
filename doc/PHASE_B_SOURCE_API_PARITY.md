# Phase B Working Doc — Source-API Parity Audit (tachiyomix 1.6)

> **Handoff note**: this file is the live progress log for Phase B of
> `doc/MIGRATION_PLAN.md` (Phase 14). Update it as work proceeds; check off items with
> evidence. If this session is interrupted, the next person resumes from the
> **Next steps** section at the bottom.

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

## Audit checklist

| File | Status | Findings |
|---|---|---|
| `model/SManga.kt` | ✅ done (Phase A) | Added `var memo: JsonObject` + `copy()` copy — matches upstream |
| `model/SMangaImpl.kt` | ✅ done (Phase A) | `override var memo = JsonObject(emptyMap())` |
| `model/SChapter.kt` | ✅ done (Phase A) | Added `var memo: JsonObject` + `copyFrom()` copy |
| `model/SChapterImpl.kt` | ✅ done (Phase A) | `override var memo = JsonObject(emptyMap())` |
| `model/Page.kt` | 🔍 in progress | pending diff |
| `model/Filter.kt` | 🔍 in progress | pending diff |
| `model/FilterList.kt` | 🔍 in progress | pending diff |
| `model/MangasPage.kt` | 🔍 in progress | pending diff |
| `model/SMangaUpdate.kt` | 🔍 in progress | pending diff |
| `model/UpdateStrategy.kt` | 🔍 in progress | pending diff |
| `Source.kt` | 🔍 in progress | pending diff |
| `CatalogueSource.kt` | 🔍 in progress | pending diff |
| `ConfigurableSource.kt` | 🔍 in progress | pending diff |
| `SourceFactory.kt` | 🔍 in progress | pending diff |
| `UnmeteredSource.kt` | 🔍 in progress | pending diff |
| `online/HttpSource.kt` | 🔍 in progress | pending member diff (large file) |
| `online/ParsedHttpSource.kt` | 🔍 in progress | pending member diff |
| `online/ResolvableSource.kt` | 🔍 in progress | pending diff |
| `AppInfo.kt` | ✅ already ABI-tested | `AppInfoTest > AppInfo conforms to Tachiyomi extension-lib ABI` exists |
| Injekt shim / `CoreContainer` bridge | ⬜ pending | `uy.kohesive.injekt.Injekt` shim retained for extensions (documented in MIGRATION_PLAN Phase 2); verify getter surface matches extension-lib expectations |

## Key ABI details already established

- `SManga.memo` / `SChapter.memo` were the **known** gap (fixed in Phase A) — upstream javadoc
  says `@since tachiyomix 1.6`.
- Extensions resolve `JsonObject` against `kotlinx-serialization-json` — Ephyra's
  `source-api` exposes it as `api(kotlinx.serialization.json)` ✅.
- Contract test added in Phase A:
  `source-api/src/test/kotlin/eu/kanade/tachiyomi/source/SourceModelContractTest.kt`
  (reflection over `getMemo`/`setMemo`, defaults, `copy()`/`copyFrom()` memo preservation).
- Upstream persists **nothing** of `memo` (it is runtime-only metadata for sources; the
  extension re-attaches it during parse via `copyFrom` merge) — host storage keeps it empty;
  `ChapterImpl.memo` default empty is correct.

## Next steps (resume here)

1. Diff `model/Page.kt`, `Filter.kt`, `FilterList.kt`, `MangasPage.kt`, `SMangaUpdate.kt`,
   `UpdateStrategy.kt` member-by-member against upstream raw files.
2. Diff `Source.kt`, `CatalogueSource.kt`, `ConfigurableSource.kt`, `SourceFactory.kt`,
   `UnmeteredSource.kt`, `online/ResolvableSource.kt`.
3. Member-diff `online/HttpSource.kt` (18 KB upstream) and `online/ParsedHttpSource.kt`
   (8 KB) — highest extension-traffic surface (all Keiyoushi/Mihon extensions subclass
   `HttpSource`).
4. Apply any missing members; keep Ephyra-specific additions intact.
5. Extend `SourceModelContractTest` to pin the newly-verified members.
6. `gradlew :source-api:test compileDebugKotlin` + full `testDebugUnitTest` + spotless.
7. Update this file + `doc/MIGRATION_PLAN.md` Phase 14 checkboxes with results.
