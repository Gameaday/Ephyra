# Source Architecture Roadmap

> **Authority:** this file decides what is current, what is compatibility, and what is planned for
> content sourcing. Where an older document disagrees, **this file wins** and the other is a defect.
>
> **Last updated:** 2026-10-05, after the documentation reconciliation. Sourcing Phase 4 is now
> decided by [`ADR-0017`](adr/0017-delete-the-unreachable-profile-path.md).
> Supersedes `ADR-0013` (JS) and `ADR-0014`
> attribution). Supersedes the sourcing sections of `SOURCE_DISCOVERY_ARCHITECTURE.md` and
> `SOURCE_DISCOVERY_EXECUTION.md`.

## Why this file exists

The sourcing documentation had drifted into three incompatible vocabularies at once — a legacy path, a
"modern profile" path, and an aspirational `SourceGateway` layer that was never built. A reader could
follow any of them and be wrong. This file collapses them into one status per component, and says
plainly which parts are real.

## Status legend

| Mark | Meaning |
|---|---|
| **CURRENT** | Ships, exercised, and intended to stay. |
| **COMPAT** | Works, but exists only to bridge something. Has a named exit. |
| **PLANNED** | Not built. Named here so it is not reinvented or confused for existing code. |
| **REMOVED** | Deleted. Listed so it is not reintroduced. `ADR-0013`. |

## 1. What ships today

**One** working source type. That is the whole product surface until Jellyfin lands.

| Type | Transport | Notes |
|---|---|---|
| **CURRENT** Extension APK | Remote repository (incl. private/self-hosted), or local import | Installed into app-private storage, driven through `HttpSource`/`CatalogueSource` |
| **PLANNED** Jellyfin (`REPOSITORY`) | Not implemented | Awaiting 2.0. Declared in `SourceType` so it lands as an addition, not a new concept |
| ~~**REMOVED** Heuristic~~ | ~~HTTP + Jsoup~~ | `AdaptiveHeuristicEngine`, `AddCustomSource`, `SourceType.HEURISTIC`, and its UI. `ADR-0015`. |

The engine registry is **empty**, and that is the correct state: extension-APK sources never consult
the orchestrator, and `NoEngineBoundException` names an unbound type rather than substituting a stand-in.

Engine selection is a **registry**, not a `when`: each engine declares the types it serves via
`ContentSourceEngine.handles`, and `SourceTypeRegistryStructuralTest` fails if that is ever replaced by
an exhaustive branch. Adding a source type is therefore additive by construction.

## 2. What is compatibility, and why

These are real and working. None of them is the target; each has an exit.

| Component | Why it exists | Exit |
|---|---|---|
| `eu.kanade.tachiyomi.*` (`:source-api`) — **COMPAT** | Tachiyomi/Mihon extension ABI. Extensions are APKs written against it. | When no shipped source needs it. Not before. |
| `DynamicHttpSource` — **COMPAT** | Bridges orchestrator profiles into the legacy ABI so reader and library see one shape. | When library/reader call the gateway directly. |
| `StubSource` — **COMPAT** | Holds the id of a source that is referenced but absent, so a missing extension does not crash a library entry. | Explicit registry trust state (`SRC-011`). |
| `AndroidSourceManager` — **COMPAT** | Registers sources from the `profiled_domains_list` preference. | When the registry owns existence (`ADR-0012`). |
| `Manga`/`SManga`/`SChapter`/`Page` — **COMPAT** | The library's persistence model. | Migrate to `ContentItem`/`ContentUnit`. **Not yet started** — Phase 2. |
## 3. The target shape, and what is actually built of it

The contract is `ContentAdapter`: the point where a provider's shape becomes ours.

```
provider (APK / Jellyfin / local folder / OPDS)
   -> ContentAdapter            <-- validates its own output, names the layer on refusal
   -> ContentItem / ContentUnit  <-- canonical, one vocabulary
   -> library, reader, downloader
```

| Piece | State |
|---|---|
| `ContentAdapter` + `AdapterOutput` | **CURRENT** — built, not yet implemented by any production adapter |
| `ContentConformance` harness | **CURRENT** — built, demonstrated against the MangaDex defect |
| `FailureLayer` / `LayeredFailure` | **CURRENT** — call sites adopt incrementally |
| Unifying `ContentItem` with `CatalogEntry`/`ChapterInfo` | **PLANNED** — Phase 2 |
| `SourceGateway` / `ExternalSourceAdapter` | **PLANNED (renamed)** — older docs use these names; the contract that exists is `ContentAdapter`. Do not create a second one. |

### The two content vocabularies — the thing most likely to cause confusion

There are still two, and pretending otherwise is how this got confusing:

- **`ContentItem` / `ContentUnit`** (`RemoteSource`) — ~20 consumers: repositories, tracking, ingest,
  OPDS, local scanner, and the reader via `DynamicHttpSource`.
- **`CatalogEntry` / `ChapterInfo` / `ContentPage`** (`UnifiedContentSource`) — 3 consumers:
  `LocalArchiveContentSource`, `OpdsContentSource`, `UnifiedScraperSource`.

`ContentItem` wins on adoption and is the target. `ContentPage` is worth *keeping* — its sealed
`ImagePage`/`TextPage`/`StreamPage` hierarchy is exactly what light novel and anime need, and it is
the only place those three shapes are modelled. Merging means folding `CatalogEntry`/`ChapterInfo` into
`ContentItem`/`ContentUnit` and keeping `ContentPage` as the page type.

**Do not do this before Jellyfin exists.** One adapter validates a contract weakly; two validate it.

## 4. Removed, and not to be reintroduced

`ADR-0013`. The short form: the transpiler located parsing code by searching for the literal
## 5. Phases

Ordered by dependency, not by appeal. Each phase is independently shippable.

### Phase 1 — Close the MangaDex defect · **IN PROGRESS**
*Blocks everything: a broken primary source makes every later phase untestable.*

- [x] Validate the image URL at the request boundary, not only in the reader (`HttpSource.imageRequest`)
- [x] Prefer `Page.imageUrl` when populated (`HttpSource.imageUrlRequest`)
- [x] Remove the JS confound so the failing path is unambiguous
- [x] Reader emits `FailureLayer`; the give-up log reads `Giving up: ADAPTER image request [page N of X]…`
- [x] Pin the reported case as a behavioural test: no request is spent, and the fault is the adapter's
- [ ] **Reproduce on device and read the log line** — no path ⇒ host/scheme comma-join. Has a path ⇒ the join is upstream of the whole URL.
- [ ] Fix the producer, once the seam is known

**Gate:** a MangaDex chapter renders on device, and the reproduction names the layer.

**Related, not a gate:** `historical/IMAGE_PIPELINE_CONSOLIDATION.md` consolidates the eight
resolve-then-judge call sites into one wrapper type, so a future defect is harder to
introduce. It is behaviour-neutral and has landed.

**Step-by-step for Phases 2 and 4:** `2_0_CONTENT_MODEL_PLAN.md` is the ten-step
execution plan for this phase and for the Phase 4 profile-path decision, in dependency order.
It is subordinate to this document: where the two disagree, this file is right and the plan
is a defect. It is a forward plan, not a historical record, so it is retained under
`DOCUMENTATION_GOVERNANCE.md` and deleted when the last step lands.

### Phase 2 — Unify the content vocabulary · **NOT STARTED · after Jellyfin is scoped**
- [ ] Fold `CatalogEntry`/`ChapterInfo` into `ContentItem`/`ContentUnit`
- [ ] Keep `ContentPage` as the page type; migrate the 3 `UnifiedContentSource` consumers
- [ ] Retire `UnifiedContentSource`, or reduce it to an adapter over the unified model

**Gate:** one vocabulary, and no mapper that exists only to convert between the two.

### Phase 3 — Jellyfin · **NEXT MAJOR FEATURE, after 2.0**
The first non-APK, non-HTML provider, and the thing that proves the adapter seam.

- [ ] `JellyfinEngine` declaring `handles = setOf(REPOSITORY)` — **no orchestrator edit**
- [ ] `JellyfinAdapter : ContentAdapter`, passing `ContentConformance`
- [ ] Token auth. `AuthType.TOKEN` exists and is **unused**. `REPOSITORY` currently resolves to
      `NoEngineBoundException`, which is deliberate: the old `REPOSITORY -> heuristicEngine // for now`
      placeholder routed Jellyfin to HTML scraping and failed quietly
- [ ] Cover manga, webtoon, light novel, anime

**Gate:** Jellyfin content renders through the same reader path as an APK source, and
`ContentConformance` passes for its adapter.

### Phase 4 — Resolve the profile path · **DECIDED (ADR-0017) · deletion not yet executed**
*The profile machinery is now unreachable, which is a planning fact rather than a bug.*

`AndroidSourceManager` builds `DynamicHttpSource` only for domains in `profiled_domains_list`.
`ADR-0015` removed the only writer of that preference (the heuristic engine, via `AddCustomSource`),
so the list is always empty and `DynamicHttpSource` is never constructed. The whole path —
`SourceProfile` → `SourceProfileCache` → Room → `DynamicHttpSource` — is dead code. `ADR-0017`
resolved whether to keep it as Jellyfin's landing pad: it does not need one, because a Jellyfin
engine receives an explicit server URL and has no discovery step to cache.

- [x] Reader emits `FailureLayer`; the give-up log names the owning layer
- [x] `HttpSource.imageRequest` / `imageUrlRequest` validate before a request exists
- [x] `DynamicHttpSource.getPageList` validates at the adapter seam
- [x] Remove both hardcoded `DEFAULT_DOMAINS` fallbacks (they were permanently the answer)
- [x] Orchestrator tolerates an empty registry and raises `NoEngineBoundException`
- [x] Remove the orphaned QuickJS AAR — `core/common` still declared `libs.bundles.js.engine` after the
      JS engine was deleted, so a clean build packaged `libquickjs.so` into all five ABIs for a runtime
      nothing calls. Found with `:app:dependencies --configuration debugRuntimeClasspath`, which showed
      it as a *direct* dependency rather than transitive. Removed the module dependency, the
      `keepDebugSymbols` entry, and the two orphaned `libs.versions.toml` lines. Jsoup stays: it is
      still used by `EpubReader` and `CloudflareInterceptor`
- [x] Delete the Content Sourcing hub — the one user-reachable screen that could only ever fail,
      since `discover()` now raises `NoEngineBoundException`. Screen, view model, test, route and
      the Sources-tab button. The button was a dead end in the primary Sources tab
- [x] Drop `source_profiles.scraper_filename` (Room v3 → v4) — nothing ever wrote it, so it was null
      on every row; it was the last piece of the JS runtime still in the schema. The migration
      rebuilds the table, and `testMigrateV3ToV4DropsScraperFilenameAndKeepsProfiles` proves a
      profile survives with the column gone — verified non-vacuous by mutating the migration to
      discard rows and watching it fail
- [x] **Decided 2026-09-28: delete the profile path ([`ADR-0017`](adr/0017-delete-the-unreachable-profile-path.md)).**
      **Not yet executed — the largest cleanup item in the programme.** All seven targets are still in
      the tree: `SourceProfile`, `SourceProfileCache`, `SourceProfileStore`, `RoomSourceProfileStore`,
      the Room entity and its migration, `DynamicHttpSource`, `RemoteSource`, `ContentSourceOrchestrator`,
      plus `GetAvailableSources`'s profile branch. `SourceType`, the engine registry, and
      `ContentSourceEngine` survive as the next adapter's contract. See `2_0_COMPLETION_PLAN.md` step D1.
- [ ] Delete `DEF-029`'s remaining three-owners disagreement (`profiled_domains_list` vs
      `SourceProfileCache` vs `GetAvailableSources`) per `ADR-0012`

### Phase 5 — Retire the compatibility layer · **BLOCKED ON 2/3/4**
Only once library and reader call the gateway directly. `DynamicHttpSource` and the Tachiyomi ABI go
last, and only when no shipped source needs them.

## 6. Rules that keep this from drifting again

1. **Do not add a source type by editing a `when`.** Add an engine, declare `handles`, bind it.
2. **Do not add a second content vocabulary.** Extend `ContentItem`/`ContentUnit`/`ContentPage`.
3. **Do not reintroduce a mechanism that fails plausibly.** If it cannot be tested off-device, it does
   not ship (`ADR-0013`).
4. **Do not let an adapter forward an address it did not check.** `ImageUrlPolicy` is the one owner.
5. **When this file and another document disagree, this file is right and the other is a defect** —
   fix the other, do not re-open the decision.

## 7. Open questions needing a decision, not more analysis

- ~~Is `scraperFilename` still worth a Room column now that no source type uses it?~~ **RESOLVED** —
      dropped in Room v3 → v4. Nothing ever wrote it.
- ~~`DEFAULT_DOMAINS`~~ **RESOLVED.** Both hardcoded fallbacks are gone (`SourceProfileCache` and
  `RoomSourceProfileStore`). With no engine they were permanently the answer, so the Sources list
  always showed three sites the app could not serve.
- ~~Does the heuristic engine stay a product feature?~~ **RESOLVED** by `ADR-0015`: removed, with a
  stated re-entry bar.
- Does the heuristic engine stay a product feature or become an authoring/validation tool? Older docs
  called it "deferred, never authoritative" while it was in fact the only engine. It is now the
  fallback, which is a stronger role than those docs allowed — that contradiction needs resolving.
`pageListParse`, assumed every source is HTML addressed by `<img>`, and emitted *well-formed
JavaScript that could not work* for an API-based source.

| Removed |
|---|
| On-device Kotlin→JS transpiler (`transpiler.js`, `MiniDOM`) — **REMOVED** |
| QuickJS scraper runtime (`ScriptableSourceEngine`, `JavaScriptEngine`, `ScriptableContentSourceEngine`, `DynamicScraperUpdater`, `ScraperScriptUpdater`, `DynamicScraperUpdateWorker`) — **REMOVED** |
| `SourceType.JS_SCRAPER` — **REMOVED** from the enum; kept only as a persisted-name alias mapping to `REMOTE_EXTENSION` |

The runtime is recoverable from git history if hand-authored JS sources are ever wanted. **Regenerating
them from Kotlin is what did not survive contact with a real source.**