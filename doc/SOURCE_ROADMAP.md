# Source Architecture Roadmap

> **Authority:** this file decides what is current, what is compatibility, and what is planned for
> content sourcing. Where an older document disagrees, **this file wins** and the other is a defect.
>
> **Last updated:** 2026-09-30, immediately after `ADR-0013` (JS removal) and `ADR-0014` (failure
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

Two source types, and only two. That is the whole product surface.

| Type | Transport | Notes |
|---|---|---|
| **CURRENT** Extension APK | Remote repository (incl. private/self-hosted), or local import | Installed into app-private storage, driven through `HttpSource`/`CatalogueSource` |
| **CURRENT** Heuristic | HTTP + Jsoup | `AdaptiveHeuristicEngine`, the only bound engine; also the declared fallback |

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
- [ ] **Reproduce on device and read the log line** — `Giving up on page N … (the host "…" is not a hostname)`. No path ⇒ host/scheme comma-join. Has a path ⇒ the join is upstream of the whole URL.
- [ ] Fix the producer, once the seam is known

**Gate:** a MangaDex chapter renders on device, and the reproduction names the layer.

### Phase 2 — Unify the content vocabulary · **NOT STARTED · after Jellyfin is scoped**
- [ ] Fold `CatalogEntry`/`ChapterInfo` into `ContentItem`/`ContentUnit`
- [ ] Keep `ContentPage` as the page type; migrate the 3 `UnifiedContentSource` consumers
- [ ] Retire `UnifiedContentSource`, or reduce it to an adapter over the unified model

**Gate:** one vocabulary, and no mapper that exists only to convert between the two.

### Phase 3 — Jellyfin · **NEXT MAJOR FEATURE, after 2.0**
The first non-APK, non-HTML provider, and the thing that proves the adapter seam.

- [ ] `JellyfinEngine` declaring `handles = setOf(REPOSITORY)` — **no orchestrator edit**
- [ ] `JellyfinAdapter : ContentAdapter`, passing `ContentConformance`
- [ ] Token auth. `AuthType.TOKEN` exists and is **unused**; `REPOSITORY -> heuristicEngine // for now` is a placeholder that will fail loudly rather than silently
- [ ] Cover manga, webtoon, light novel, anime

**Gate:** Jellyfin content renders through the same reader path as an APK source, and
`ContentConformance` passes for its adapter.

### Phase 4 — Adopt the adapter seam on the existing path · **NOT STARTED**
- [ ] Wrap `DynamicHttpSource` as the first real `ContentAdapter`
- [ ] Emit `FailureLayer` from orchestrator, reader and downloader call sites
- [ ] Delete `DEF-029`'s three-owners situation (`profiled_domains_list` vs `SourceProfileCache` vs `GetAvailableSources`) per `ADR-0012`

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

- Is `scraperFilename` still worth a Room column now that no source type uses it? Nothing writes it.
- `RoomSourceProfileStore.DEFAULT_DOMAINS` hardcodes a MangaDex/manganato/asuratoons trio as a fallback
  for an empty preference set. That makes "no sources configured" indistinguishable from "these three",
  and it is a hidden default. Delete it with the Phase 4 registry work.
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