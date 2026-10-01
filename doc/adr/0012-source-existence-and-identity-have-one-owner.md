# ADR-0012: Source existence and identity have one owner

- **Status:** Accepted
- **Date:** 2026-09-30
- **Decision:** One registry owns which sources exist and what each source's identity is. Every consumer — registration into the legacy `SourceManager` map, the Sources UI, URL/entry resolution in the reader, and the downloader — reads that owner. A second copy of either fact is a defect, not an optimisation.
- **Supersedes / superseded by:** Amends the enforcement of [ADR-0007](0007-source-is-not-a-ui-adapter.md); it does not contradict it. No ADR is superseded.

## Context

Owner-reported 2026-09-30: opening a MangaDex chapter URL failed with `Unknown source for gallery: https://mangadex.org/chapter/…`, on a build dated the day after the same entry had opened. The message itself is produced by no file in this repository — 1,811 source, resource, asset and script files were swept and the only `gallery` hits are two unrelated comments — which is the first finding: the failure is reported by a runtime-loaded component rather than by the layer that knows why.

Reading the tree produced a mechanism that is **state, not code**. Nothing in the day's commits provisioned or removed a source (`6b3508603`, `e8980ed5b`, `eef62f4f2`, `f121ebc80`, `01f706cb3` all changed URL resolution, retry policy, or an orphaned adapter):

- **Three owners answer "which sources exist".** `AndroidSourceManager` builds the source map from the preference set `profiled_domains_list` alone, with no fallback (`AndroidSourceManager.kt:59`); `GetAvailableSources` reads the same set for the Sources UI and states in a comment that a hardcoded fallback was deliberately removed because injected built-ins made removed sources undeletable (`GetAvailableSources.kt:37-41`); `SourceProfileCache.getAllProfiledDomains()` returns the preference set *or a hardcoded trio containing `https://mangadex.org`* (`SourceProfileCache.kt:87-97`), and `RoomSourceProfileStore` carries its own copy of that same trio (`RoomSourceProfileStore.kt:17-23`). So the resolution machinery can assert that `mangadex.org` is a profiled domain while the source map contains no such source.
- **Identity has no owner at all.** A dynamic source's id is `profile.baseUrl.hashCode().toLong()` (`DynamicHttpSource.kt:30`), and the UI recomputes the same id independently (`GetAvailableSources.kt:73`). A base URL that changes spelling — trailing slash, case, `www.` — yields a different source id, so every entry written against the old id becomes unresolvable while search and browse keep working. Nothing canonicalises the value at either write site.
- **The fact is destructively erased.** `SourceProfileCache.invalidate()` deletes the domain from `profiled_domains_list` (`:80-81`), and `RemoveCustomSource` does the same (`:45-47`), so one failed health check or one removal unregisters the source while entries still reference its id. Health is a statement about a source; existence is not.

- **An adapter was deleted the same day** (`SOURCE_REMOVAL_PLAN.md:92`). That deletion was correct and is not implicated: `ContentSourceAdapter` adapted legacy → target (`delegate: Source` in, `ContentItem`/`ContentUnit` out) and had zero consumers. It is worth stating why, because the natural instinct — "an adapter would have made this easier to repair" — points at the wrong artifact. Adapters do not own facts; they translate protocols. Deleting one cannot create an ownership hole, and restoring one cannot fill it.

The consequence is a failure that is not a function of the build but of preference and database state at launch, which is why it presents as "it worked yesterday".

## Decision

1. A single registry owns source existence and identity. Consumers read it; they do not keep a second copy of either.
2. Source identity is a canonical, pure function of the source's origin (scheme, lowercased host, no default port, no trailing slash), persisted alongside the value it was derived from, so re-deriving it after a spelling change is a migration rather than an orphaning.
3. Health and existence are separate. A failing source is `Degraded`/`Quarantined`, never absent, and no observation stream may erase a source definition.
4. An unresolved entry is a typed outcome rendered by this app (`Unsupported` / `PermanentFailure(SourceUnavailable)`), never a string produced by a loaded component.
5. Adapters remain, named and directed, each with an owner, an exit condition and a removal task, as [ADR-0007](0007-source-is-not-a-ui-adapter.md) requires. The two bridges that exist today are the legacy-extension adapter and the legacy `HttpSource` bridge (`DynamicHttpSource`). Keeping them is what makes a MangaDex-shaped source fixable once; letting a feature consume them directly is what ADR-0007 forbids.

## Consequences

- `AndroidSourceManager` becomes a projection of the registry rather than an independent authority, and `GetAvailableSources` reads descriptors rather than the preference.
- The two hardcoded default-domain sets are demoted to discovery *proposals*: they may seed a proposed profile, they may not claim that a source exists.
- Source ids change shape for new sources and are migrated for existing ones; the migration is a fixture-backed step, not an implicit re-derivation at read time.
- Entry resolution gains a recovery path (`discover(origin)` → register → retry), so a missing source is recoverable rather than terminal.
- Scheduled work: `SRC-011` (this decision) and `SRC-012` (the MangaDex adapter that depends on it), recorded in [`ROADMAP.md`](../../ROADMAP.md).

## Rejected alternatives

- **Keep the preference set as the registration source and add a fallback.** That is what `SourceProfileCache` does today, and it is the defect: two layers disagreeing about one fact is indistinguishable from corruption at the point of use.
- **Keep `baseUrl.hashCode()` and normalise at read time.** Normalising at read time makes identity depend on how many times a value passed through the pipeline, and both the disk-cache key and the database column *are* the id. Canonicalise at the write boundary and migrate.
- **Restore `ContentSourceAdapter` to have "an adapter for such cases".** It adapted the direction that is not failing, had no consumers, and adapters do not own facts. Its restoration would not have changed this outcome; it would have re-added dead code with a reassuring name.
- **Delete `DynamicHttpSource` now that it is implicated.** Its removal is R-008 work gated on native coverage; deleting the only route by which profile-based sources reach the reader would replace a recoverable defect with an unreadable MangaDex.
- **Treat the foreign error string as the defect and localise it.** The string is a symptom of an unowned fact; localising it would make the failure legible without making it recoverable.

## Evidence required

Behavioural, not structural, and each item is falsifiable by removing the thing it names:

- `SourceResolutionDiagnosticsTest` (`SRC-011`): a registered source per profiled domain, an identity mismatch detected, and a missing source reported rather than silent.
- A single-owner gate in the `SourceApiBoundaryTest` style: only the registry package reads `profiled_domains_list`, and no module carries a second default-domain set.
- An orphan-entry gate: every persisted `Manga.source` resolves in the registry, else the entry surfaces a typed outcome.
- The zero-legacy startup test already required by [`SOURCE_DISCOVERY_ARCHITECTURE.md`](../SOURCE_DISCOVERY_ARCHITECTURE.md).
- `DEF-029` in [`REBUILD_STATUS.md`](../REBUILD_STATUS.md) carries the owner-reported symptom and stays `IN_PROGRESS` until a reproduction names which owner lost the fact.

6. A removal step must answer one question before it is scheduled: *what fact or capability did this component own, and who owns it now?* "Nobody, and nothing read it" is the only answer that authorises deletion without a replacement.
