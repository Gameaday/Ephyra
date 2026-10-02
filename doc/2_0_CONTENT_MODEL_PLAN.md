# Implementation Plan

## Overview

> Extension-API compatibility (which `extension-lib` versions are supported, how behaviour keys off probed
> capabilities rather than the declared version, and how to adopt a new generation) is documented in
> [`EXTENSION_COMPATIBILITY.md`](EXTENSION_COMPATIBILITY.md). That document is the authority for the extension boundary.


Complete the 2.0 rework by resolving the now-dead profile path, unifying the split content
vocabulary, and hardening the adapter seam so that Jellyfin can be added as a peer rather than a
fork — while explicitly *not* guessing at the one defect that still requires on-device evidence.

### Why this plan exists

Three findings from the current tree drive everything below. All three were verified against the
code, not inferred from documentation.

1. **The entire profile path is unreachable.** `AndroidSourceManager` consults
   `profiled_domains_list` to decide whether to build a `DynamicHttpSource`. Nothing writes that
   preference any more — `ADR-0015` removed `AddCustomSource`, its only writer. `orchestrator.discover`
   requires an engine, `provideContentSourceEngines()` returns `emptyList()`, so it raises
   `NoEngineBoundException`. `UpdateCustomSource`'s only caller was the deleted
   `ContentSourcingViewModel`. Result: `SourceProfile` → `SourceProfileCache` → Room →
   `DynamicHttpSource` is live code that can never execute.

2. **Two content vocabularies still coexist.** `ContentItem`/`ContentUnit` (with
   `ContentPage` as the page type) has real adoption. `CatalogEntry`/`ChapterInfo` via
   `UnifiedContentSource` has three consumers. Every overlap is a mapper that exists only to
   convert between them — exactly the cost `doc/SOURCE_ROADMAP.md` Phase 2 exists to remove.

3. **The image pipeline is now consolidated and gated** (commit `4e10321d1`): `ResolvedImageUrl`
   and `PageImageAddress` own resolve-then-judge, and a structural gate forbids direct
   `page.imageUrl` access. This plan does not change that pipeline's behaviour; it builds on it.

### Scope

In scope: profile-path deletion, vocabulary unification, `DEF-029` retirement, the Room migration
that follows, doc reconciliation, and the structural gates that keep all of it from regressing.

Out of scope, deliberately: any further `ImageUrlPolicy` tightening, the MangaDex producer fix,
Jellyfin implementation itself, and heuristic discovery. The first two require a device logcat
line that has not been supplied; the second two are sequenced after this plan lands.

### Governing documents

- `doc/SOURCE_ROADMAP.md` — sourcing authority; this plan executes its Phases 4 and 2.
- `ROADMAP.md` — top-level phases; this plan sits inside its 2.0 rework.
- `doc/adr/README.md` — new ADRs are indexed here.

---

## Baseline: what is proven today

State to preserve. Any regression against this list is a defect in this work.

| Property | Evidence |
|---|---|
| Tree clean, in sync with `origin/main` | `git status -sb` |
| 9 modules green, zero failures | full unit-test sweep |
| APKs assemble | `:app:assembleDebug` |
| Spotless clean; pre-commit gate runs on commit | `build.gradle.kts` / hooks |
| One working source type: extension APKs + local | `ADR-0015` |
| Engine registry is extensible, no orchestrator `when` | `SourceTypeRegistryStructuralTest` |
| Errors name their owning layer | `FailureLayer`, `LayeredFailure` |
| Image URL validated at every request boundary | `ImageUrlPolicyStructuralTest` |
| No direct `page.imageUrl` access outside the owner | `PageLoadRecoveryStructuralTest` |

## Types

### New: `ContentLocator`

```kotlin
/**
 * The one way the reader and downloader are told where an item's content lives.
 *
 * Exists so a [ephyra.domain.content.model.ContentItem] can be served by more than one engine
 * without the reader learning any engine's vocabulary. Deliberately opaque: callers may not read
 * the underlying URL string, because doing so is how the image pipeline grew four copies of
 * resolve-then-judge.
 */
@JvmInline
value class ContentLocator private constructor(private val token: String) {
    companion object {
        fun of(url: String, engineId: String): ContentLocator
        fun engineId(): String
        fun value(): String   // internal to the owning module; NOT for reader/downloader
    }
}
```

**Why a value class with a private constructor.** The reader and downloader are the two consumers
that must not branch on URL shape. Giving them a type they cannot decompose means the decision
"is this address usable" has exactly one home, and the compiler enforces it rather than a code
review.

### New: `EngineId`

```kotlin
@JvmInline
value class EngineId(val value: String)   // e.g. "extension-apk", "local-archive", "jellyfin"
```

Stable across restarts and persisted in the `content_item.locator` column. Deliberately *not* an
enum: an enum would require editing a shared file to add an engine, which is the coupling
`ADR-0015` removed for source types. This is the same mistake in a new place, avoided.

### Modified: `ContentItem`

```kotlin
data class ContentItem(
    // ... existing fields unchanged ...
    val locator: ContentLocator,          // replaces the implicit "orchestrator knows the baseUrl"
)
```

### Modified: `UnifiedContentSource` → deleted

Its three consumers migrate to `ContentSourceEngine` + `ContentAdapter`. See *Classes*.

### Removed: `SourceProfile`, `SourceProfileCache`, `SourceType`

The profile path is unreachable (Baseline finding 1). Deleting it removes a Room table, a
migration, and the `DEF-029` three-owners disagreement in one move.

`SourceType` is replaced by `EngineId`. Persisted `source_type` strings are handled by the
migration in *Files*.

---

## Files

### Delete

| Path | Reason |
|---|---|
| `core/domain/src/main/java/ephyra/domain/content/source/SourceProfile.kt` | Unreachable; superseded by `ContentLocator` |
| `core/domain/src/main/java/ephyra/domain/content/source/SourceProfileCache.kt` | Only reader of `profiled_domains_list`, which nothing writes |
| `core/domain/src/main/java/ephyra/domain/content/source/ContentSourceOrchestrator.kt` | Its `resolveEngineForProfile` is the last reason it exists; engines bind directly |
| `core/domain/src/main/java/ephyra/domain/content/source/UnifiedContentSource.kt` | Folded into `ContentSourceEngine` + `ContentAdapter` |
| `core/domain/src/main/java/ephyra/domain/content/model/CatalogEntry.kt` | Folded into `ContentItem` |
| `core/domain/src/main/java/ephyra/domain/content/model/ChapterInfo.kt` | Folded into `ContentUnit` |
| `core/domain/src/main/java/ephyra/domain/content/model/ContentMappers.kt` | Exists only to convert between the two vocabularies |
| `core/data/src/main/java/ephyra/data/sourcing/RoomSourceProfileStore.kt` | Backing store for a dead table |
| `app/src/main/java/eu/kanade/tachiyomi/source/online/DynamicHttpSource.kt` | The unreachable adapter |
| `core/domain/src/main/java/ephyra/domain/source/diagnostics/SourceResolutionDiagnostics.kt` | `DEF-029` instrument; the disagreement it reports ceases to exist |
| `core/domain/src/main/java/ephyra/domain/source/diagnostics/SourceResolutionReports.kt` | ditto |

### Modify

| Path | Change |
|---|---|
| `core/data/src/main/java/ephyra/data/room/EphemeraDatabase.kt` | Bump version `vN` → `vN+1`; add migration |
| `core/data/src/main/java/ephyra/data/room/Migrations.kt` | New migration: drop `source_profiles` table, drop `scraper_filename`, remap `source_type` values |
| `core/domain/.../content/model/ContentItem.kt` | Add `locator` |
| `core/domain/.../content/source/ContentSourceEngine.kt` | Rename `handles: Set<SourceType>` → `handles: Set<EngineId>`; drop profile-returning methods |
| `core/domain/.../content/source/ContentAdapter.kt` | Add `locatorFor(item)` |
| `core/domain/.../content/interactor/GetAvailableSources.kt` | Drop the `profileCache` branch; read only engines |
| `source-local/.../LocalArchiveContentSource.kt` | Produce `ContentItem` with a `ContentLocator`; delete its `CatalogEntry` mapping |
| `app/src/main/java/ephyra/app/di/AppModule.kt` | Remove `provideContentSourceEngines` stub indirection; bind engines directly |
| `app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt` | Construct `DynamicHttpSource`-equivalent from the extension's own base URL, not a profile |
| `feature/reader/.../loader/ChapterLoader.kt` | Resolve pages via `ContentLocator` |
| `core/download/.../Downloader.kt` | Resolve pages via `ContentLocator` |
| `doc/SOURCE_ROADMAP.md` | Phases 4 and 2 → done; record the decision + gate |
| `ROADMAP.md` | Mark the phases complete; fix the garbled merge at `SOURCE_ROADMAP.md:194-198` |
| `doc/adr/README.md` | Index ADR-0016 (locator contract) |

### Add

| Path | Purpose |
|---|---|
| `core/domain/.../content/source/ContentLocator.kt` | The type |
| `core/domain/.../content/source/EngineId.kt` | The type |
| `doc/adr/0016-content-locator-is-the-only-address.md` | Why, and why not a String |
| `app/src/test/java/ephyra/app/architecture/ContentVocabularyStructuralTest.kt` | Gate: no `CatalogEntry`, no `SourceProfile`, no `sourceType` |

---

## Functions

### New

| Signature | Path | Purpose |
|---|---|---|
| `fun of(url: String, engineId: String): ContentLocator` | `ContentLocator.kt` | Single construction site; validates non-blank |
| `fun engineId(): String` | `ContentLocator.kt` | Which engine owns this address |
| `internal fun value(): String` | `ContentLocator.kt` | The only escape hatch, module-scoped |
| `fun locatorFor(item: ContentItem): ContentLocator` | `ContentAdapter.kt` | Adapter answers "where does this live" |
| `suspend fun getPages(locator: ContentLocator): List<ContentPage>` | `ContentSourceEngine.kt` | Replaces profile-based lookup |

### Modified

| Signature | Path | Change |
|---|---|---|
| `suspend fun getAllProfiles()` | `ContentSourceOrchestrator.kt` | **Removed** — no profiles exist |
| `suspend fun resolveEngineForProfile(...)` | same | **Removed** — engines bind directly by `EngineId` |
| `fun get(url: String): SourceProfile?` | `SourceProfileCache.kt` | **Removed with the class** |
| `private fun createDynamicHttpSource(...)` | `AndroidSourceManager.kt` | Read the base URL off the extension instance, not a profile row |
| `suspend fun loadChapter(...)` | `ChapterLoader.kt` | Accept `ContentLocator` rather than a source + URL pair |

### Removed — no migration needed

`AddCustomSource.*` (deleted in `ADR-0015`), `UpdateCustomSource.*` (its only caller was the deleted
`ContentSourcingViewModel`), `RemoveCustomSource.*`. These are already unreferenced; this plan
deletes the files rather than leaving them as dead code.

---

## Classes

### New

**`ContentLocator`** (`core/domain/.../content/source/ContentLocator.kt`)
`@JvmInline value class`, private constructor, companion `of`/`engineId`, internal `value`.

**`EngineId`** (`core/domain/.../content/source/EngineId.kt`)
`@JvmInline value class` wrapping a `String`. Has `companion object` constants for
`EXTENSION_APK`, `LOCAL_ARCHIVE`, `OPDS` — but adding one is additive, not an edit to a sealed set.

### Modified

**`ContentSourceEngine`** — `handles: Set<SourceType>` → `handles: Set<EngineId>`;
`discover`/`getAllProfiles` removed. Search/list/detail stay. This is the interface Jellyfin
implements, and the reason adding it needs no orchestrator edit.

**`ContentAdapter`** — gains `locatorFor`. Its existing `AdapterOutput.Rejected` already carries a
`FailureLayer`, so a source that produces an unusable locator is blamed on the adapter rather than
being handed to DNS. That is the exact property the MangaDex report lacked.

**`LocalArchiveContentSource`** — returns `ContentItem`/`ContentUnit`/`ContentPage` directly.
Its `CatalogEntry` mapping goes.

### Removed

**`SourceProfile`**, **`SourceProfileCache`**, **`ContentSourceOrchestrator`**,
**`UnifiedContentSource`**, **`CatalogEntry`**, **`ChapterInfo`**, **`RoomSourceProfileStore`**,
**`DynamicHttpSource`**, **`SourceResolutionDiagnostics`**, **`SourceResolutionReports`**.

`DynamicHttpSource` deserves a note: it is deleted as a *class*, but its logic — the extension-APK
adapter, including the `ImageUrlPolicy.requireUsable` call added for MangaDex — moves into a new
`ExtensionApkAdapter` under `source-api`. Deleting the class is removing an indirection that
consulted a dead preference, not removing capability. That distinction must be stated in the
commit message, because the file name reads like a capability removal.

---

## Dependencies

**No new dependencies.** This is the point of the plan: the vocabulary split and the dead profile
path are structural debt, and structural debt is not paid down with libraries.

Room's schema-exported JSON under `core/data/schemas/` must be regenerated for the new version.
That is a build artifact, not a dependency.

---

## Testing

### New test files

**`core/domain/src/test/java/ephyra/domain/content/source/ContentLocatorTest.kt`**
- `of` accepts an absolute `https` URL, a relative path (resolved against a base), and an empty string.
- `of("")` yields `Rejected(ADAPTER)`, not a locator — an empty address must never reach transport.
- A comma-bearing host (`cmdxd98sb0x3yprd.mangadex.network,https`) is rejected at construction, so
  the defect cannot be represented even accidentally.
- `EngineId` equality/hash is value-based, so two adapters declaring the same id collide in the
  registry rather than silently shadowing.

**`core/domain/src/test/java/ephyra/domain/content/source/UnifiedVocabularyTest.kt`**
- `LocalArchiveContentSource` output satisfies the canonical shape for a CBZ, an EPUB, and a
  single-image archive.
- No test in the module constructs a `CatalogEntry` or `ChapterInfo`. This is the executable form of
  "Phase 2 is done": if either type returns from a branch, the suite fails to compile.

**`source-api/src/test/kotlin/eu/kanade/tachiyomi/source/online/ExtensionApkAdapterTest.kt`**
- Migrated from `DynamicHttpSource` coverage: search/detail/pages round-trip.
- The MangaDex-shaped case is preserved verbatim — `Page(index, url = "", imageUrl = "<at-home>")`
  resolves through `PageImageAddress`, keeps host/port/path, and round-trips byte-identical.
- A spliced image URL is refused before a request is built, and the refusal is reported as `ADAPTER`.

### Modified test files

- **`ImageUrlPolicyTest.kt`, `ImageUrlPolicyResolveTest.kt`** — unchanged. They are the behavioural
  floor for the pipeline this plan must not disturb; a diff here is a warning sign.
- **`HttpSourceImageUrlRequestTest.kt`** — assertions retargeted from the deleted `HttpSource` loops
  to `ExtensionApkAdapter`, preserving every case including the non-default-port one (OkHttp
  canonicalises away `:443`, so that assertion must stay on a non-default port).
- **`SourceTypeRegistryStructuralTest.kt`** — must be retargeted, since `SourceProfile`/`fromString`
  disappear. It becomes an assertion that no exhaustive `when` over source types exists anywhere.
- **`ImageUrlPolicyStructuralTest.kt`, `PageLoadRecoveryStructuralTest.kt`**, **`SourceApiBoundaryTest.kt`**
  — unchanged. These are the gates that keep the consolidation in `4e10321d1` from eroding; this plan
  adds a path *through* them, not around them.

### New structural gate

**`app/src/test/java/ephyra/app/architecture/UnifiedVocabularyStructuralTest.kt`**

Fails the build if `CatalogEntry`, `ChapterInfo`, `UnifiedContentSource`, or `SourceProfile` appear
in any `src/main` file. It reads file contents rather than filenames, so a production reference
cannot be masked by a path exclusion.

## Implementation Order

Ordered so each step leaves the tree green and independently revertible. Steps 0–4 are pruning;
Step 5 is the substantive refactor.

**Step 0 — Record the decision (no code).**
Add ADR `0017-dead-profile-path.md` recording that the profile path is unreachable, that deleting it
is chosen over keeping it as a Jellyfin landing pad, and — critically — *why* Jellyfin does not need
it: `ContentLocator` + `ContentAdapter` are what an engine implements, and `SourceProfile` was a
discovery-time cache that Jellyfin's explicit server URL makes redundant. Index it in
`doc/adr/README.md`. **Do this first** so the deletions that follow cite a decision, not a preference.

**Step 1 — Introduce the types, unused.**
Add `EngineId` and `ContentLocator` with their tests. No call sites yet, so this is additive and
cannot break anything. Compile and test.

**Step 2 — Migrate the local source to the unified vocabulary.**
`LocalArchiveContentSource` returns `ContentItem`/`ContentUnit`/`ContentPage` directly. Delete
`CatalogEntry`, `ChapterInfo`, and `UnifiedContentSource` with their tests. Widest blast radius of
any step — the three consumers move — so it lands alone.
*Gate:* `:core:domain:test` green; `LocalArchiveContentSource` compiles against the unified model.

**Step 3 — Port the extension-APK adapter.**
Create `ExtensionApkAdapter` in `source-api`, moving `DynamicHttpSource`'s logic including the
MangaDex `requireUsable` call. Wire it as a `ContentSourceEngine` handling `EngineId.EXTENSION_APK`.
Both live for this commit, so behaviour is unchanged and the port is reviewable as a move.
*Gate:* `ExtensionApkAdapterTest` green, including the byte-identical MangaDex round-trip.

**Step 4 — Cut over, then delete.**
Repoint `AndroidSourceManager` and `GetAvailableSources` at `ExtensionApkAdapter`. Only once nothing
references it, delete `DynamicHttpSource`, `ContentSourceOrchestrator`, `SourceProfileCache`,
`SourceProfile`, `RoomSourceProfileStore`, and the three already-unreferenced interactors
(`AddCustomSource`, `UpdateCustomSource`, `RemoveCustomSource`).
*Gate:* app assembles; no main-source reference to any deleted type.

**Step 5 — Room migration.**
Delete the profile table and the `scraper_filename` column (the third unresolved item from earlier
this session). Bump the schema version, add the migration, export the new JSON under
`core/data/schemas/`.
*Gate:* `:core:data:test` green; existing extension-APK sources survive the upgrade. This is the
step that touches the user's actual data, so it is committed alone and called out in its message.

**Step 6 — Delete `DEF-029` outright.**
`SourceResolutionDiagnostics` and `SourceResolutionReports` existed to report disagreement between
three owners of profile state. With one owner gone there is nothing to disagree about, so they are
deleted rather than reduced — including the DEF-029 instrumentation `ReaderViewModel:348` calls.
Remove that call site and the `ReaderReadCompletionWiringTest` constructor argument added for it.
*Gate:* `:app:testDebugUnitTest` green, with that test updated to the new `ReaderViewModel` signature.

**Step 7 — Gates.**
Add `UnifiedVocabularyStructuralTest`; retarget `SourceTypeRegistryStructuralTest`; confirm the two
image gates are untouched and still pass.

**Step 8 — Documentation reconciliation.**
Update `doc/SOURCE_ROADMAP.md` (mark Phases 2 and 4 complete; re-scope Phase 5, now about the
Tachiyomi ABI rather than profiles), `doc/REBUILD_STATUS.md` (close the SRC rows this satisfies),
`doc/source/SOURCE_INVENTORY.md` and `SOURCE_COMPATIBILITY_MATRIX.md` (both still reference deleted
components), `README.md`, and `CHANGELOG.md`. Repair the garbled merge at
`doc/SOURCE_ROADMAP.md:194-198` while that file is open.
*Gate:* `git grep -i -E 'transpil|scraper|CatalogEntry|SourceProfile|UnifiedContentSource'` over
`*.md` returns only historical or explicitly-removed references.

**Step 9 — Full validation and push.**
Run steps 1–5 of the validation strategy. Push as **ten commits** (one per step), not one — the
ordering exists so a red build bisects to a single phase.

---

## What this plan does not close

Stated plainly so it is not mistaken for progress:

- **The MangaDex producer remains unknown.** Nothing here identifies who emits `host,https`. The
  pipeline that would catch it is already in place (`4e10321d1`) and gated; the remaining input is a
  logcat line from a device build: `Giving up: <LAYER> image request [page N of X] …`. Every phase
  here proceeds *without* it, by design — none of it guesses at the producer.
- **Jellyfin is untouched.** This plan makes it a peer rather than a fork; it does not implement it.
- **Heuristic discovery stays retired** per `ADR-0015`.
- **Phase 5 (retire the Tachiyomi ABI) is still blocked**, and deleting the profile path does not
  unblock it — `Manga`/`SManga`/`Page` remain load-bearing for the reader and downloader.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Step 2 breaks a local-content path not covered by tests | Medium | `LocalArchiveContentSource` is the single consumer; its tests are extended before the switch |
| Step 5 loses extension-APK sources on upgrade | Low | The migration drops only the profile table/column; extension sources live in the extension map, not Room. Verified by `:core:data:test`, stated in the commit message |
| Steps 2–4 read as removing capability because the file names say so | Medium | ADR-0017 and every commit message state that `DynamicHttpSource`'s logic *moved* rather than disappeared |
| Deleting `DEF-029` loses a diagnostic that was never replaced | Low | It is genuinely obsolete — three owners are now one. Stated in ADR-0017 |
| Plan grows past one reviewable unit | Medium | Ten commits, each green; Phase 5 explicitly left for later |

## Handoff

Nothing in this plan has been implemented. Refer to `@implementation_plan.md` for the full
breakdown. Steps 0–1 are safe to start immediately; Steps 2–5 are the substantive work and are
worth confirming before I begin.

### Validation strategy

Run per-commit, not just at the end: each phase is separately committable and separately green, so a
failure localises to one phase rather than the whole plan.

1. `./gradlew :core:common:test :core:domain:test :source-api:test :core:data:test`
2. `./gradlew :feature:reader:test :feature:browse:test :core:download:test`
3. `./gradlew :app:testDebugUnitTest` (slow — `forkEvery = 1`, expect ~6 min)
4. `./gradlew :app:assembleDebug` — the CI gate, and the one this plan must not regress
5. `spotlessCheck` across all touched modules

