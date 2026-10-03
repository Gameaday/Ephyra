# Extension compatibility

How Ephyra handles Tachiyomi/Mihon extension APIs, why behaviour keys off **probed capabilities**
rather than the declared `extension-lib` version, and what to do when a new generation ships.

This document is the authority for the extension boundary. Where it disagrees with another document,
**this one is right and the other is a defect.**

## The two questions, kept apart

| Question | Answered by | Where |
|---|---|---|
"Can this extension be loaded at all?" | the declared `extension-lib` version | `ExtensionLoader.SUPPORTED_LIB_VERSIONS` |
"What will this source do when asked for an image URL?" | probed capability | `SourceCapabilities` |

Conflating them is what produced the reported MangaDex failure. The version says an extension was
*loadable*; it says nothing about which of its methods it overrides.

## Supported versions

`ExtensionLoader` accepts `1.4`, `1.5` and `1.6`, read from the `tachiyomix.extensionLib` manifest
key, falling back to a version parsed out of the version name for older APKs. An extension declaring
anything else is rejected with `LoadFailureReason.UNSUPPORTED_LIB_VERSION` rather than loaded and
left to fail later - which is the right trade, because a contract we have not implemented is a class
of failures we cannot predict.

**Adding a version** is a one-line change to `SUPPORTED_LIB_VERSIONS`, but it is not sufficient on
its own: read the "adopting a new generation" section below first.

## Why capability probing, and not version branching

An extension may override whichever methods it likes regardless of the version it declares.

MangaDex is the worked example. It declares `libVersion = "1.6"`, yet it does **not** override the
modern `getImageUrl`, nor any of the deprecated URL-resolving chain. What it overrides is
`pageListParse` — which builds `Page(index, url = "$host,$tokenRequestUrl,$now", imageUrl = "/data/…")`,
a **relative path** — and `imageRequest`, which is the only code that knows how to read both:

```kotlin
val (host, tokenRequestUrl, time) = page.url.split(",")
…
GET(mdAtHomeServerUrl + page.imageUrl, headers)
```

`Page.url` is a `(host, tokenUrl, fetchTime)` **at-home token cache key**, stored in `Page.url` by design -
MangaDex@Home tokens expire after five minutes, so the extension caches the server, the URL that
refreshes it, and when it was fetched. `Page.imageUrl` is deliberately left relative, because the
host half of the address is only known at request time. Only its own code knows how to read either.

An app that assumed `Page.url` was an image address would fetch the key, get a DNS failure for a host
that could never exist, and report a network fault for what is a contract misunderstanding. An app
that "helpfully" resolved `Page.imageUrl` against `baseUrl` — which is what broke MangaDex completely
here, see "`Page.imageUrl` is opaque to the host once populated" below — spliced a second host onto
the path and failed **every** page. Both halves of that failure, and the fact that `imageRequest`
itself is an override point the app must respect, are why the diagnostic probe enumerates it too.
## The image-URL chain: four entry points, and upstream is deleting them

| Entry point | Signature | Role | Upstream status |
|---|---|---|---|
`getImageUrl` | `suspend fun (Page): String` | modern; overrides everything | current |
`fetchImageUrl` | `fun (Page): Observable<String>` | replaces the whole chain; no request built, no response parsed | **removed** in `[Unreleased]` |
`imageUrlRequest` | `protected fun (Page): Request` | what to fetch | **removed** with no replacement |
`imageUrlParse` | `protected fun (Response): String` | read the address out of the response | **removed** with no replacement |

`SourceCapabilities.customisesImageUrlChain` reports true for **any of the four**. When true, the
app must not treat `Page.url` as an image address. When false, the app's own chain runs and
`Page.url` is expected to be fetchable.

Detection walks `getDeclaredMethods` up the hierarchy rather than using `Class.getMethods`, because
`imageUrlRequest` and `imageUrlParse` are `protected` and `getMethods` returns public members only.

**Two ways this probe has already been wrong, and both were expensive.**

*Missing `getMethods`.* A probe written against `Class.getMethods` silently misses both `protected`
methods even with the right names — and reported MangaDex as uncustomising, blocking the very chain
it was written to accommodate.

*Missing an entry point.* The list itself omitted `fetchImageUrl`. MangaDex overrides exactly that
one, so the probe answered **"no" for a source that answers "yes"** and the reader refused pages
that were perfectly readable. A probe that is wrong in that direction is worse than no probe: it
converts a working source into a failure and attaches a confident diagnostic asserting no
customisation existed. **The chain is closed over four names because upstream closed it over four
names — the enumeration comes from the upstream class, not from what this app happens to call.**

Both classes of bug were caught by a fixture shaped like the extension in question, not by reading
the code. That is the argument for step 5 below.

## `Page.imageUrl` is opaque to the host once populated

**This is the rule that broke MangaDex 1.6 completely** — a chapter listed, was selected, and *no
page loaded*, for every chapter, with the failure reading as a DNS error. It is recorded here in
full because the defect was not a missing feature; it was an over-eager one.

What a real MangaDex extension does (verified against `keiyoushi/extensions-source`,
`MangaDex.kt` / `MangaDexHelper.kt`):

```kotlin
// getPageList — Page.url is an MD@Home cache key, Page.imageUrl is a RELATIVE path:
Page(index, "$host,$atHomeRequestUrl,$now", "/data/$hash/$file")

// its own imageRequest override — the source joins them:
override fun imageRequest(page: Page) = GET(mdAtHomeServerUrl + page.imageUrl, headers)
```

The at-home host expires in ~30 minutes, so the extension keeps the path *relative* and resolves
the host at request time, from `Page.url`. Only the source's overridden `imageRequest` knows how to
interpret a populated `Page.imageUrl`. **The host must therefore never rewrite it**: upstream
Mihon's reader reads it back and hands the page verbatim to `source.getImage`. Resolution against
`baseUrl` belongs exactly at the request boundary — the base `HttpSource.imageRequest` — where a
source override inherits nothing by design.

**What went wrong here.** `HttpPageLoader.internalLoadPage` resolved `page.imageUrl` against
`source.baseUrl` and wrote the result back *before* fetching, "to mirror what `HttpSource.imageRequest`
does". That justification was false for any source that overrides `imageRequest`: the rewrite turned
MangaDex's relative path into `https://mangadex.org/data/...` (the wrong host — the website, not the
MD@Home image server), and MangaDex's own request builder then produced

```
https://cmdxd98sb0x3yprd.mangadex.networkhttps://mangadex.org/data/...
```

two URLs spliced into a host that can never resolve. Every page of every chapter took that path, so
every page failed identically — and the recovery ladder could not escape, because
`FreshPageAddresses` restored a fresh *relative* address that the loader immediately rewrote again.
The failure surfaced as `Unable to resolve host "…mangadex.network,https"`, which read as a network
or source defect. The same rewrite existed in `resolvePageImage`'s populated branch, so the
downloader's retry path (`Downloader`) corrupted the field the same way.

**The fix, and the invariants that keep it fixed:**

1. `resolvePageImage` passes a populated `Page.imageUrl` through untouched as
   `ResolvedImageUrl.opaque(value)` — not resolved against `baseUrl`, not judged (a relative path
   is *incomplete*, not broken; the missing half is knowledge only the source's `imageRequest`
   holds). The `getImageUrl` fallback path — a page that arrived *without* an address — still
   resolves and judges the value the source returned in answer to our question.
2. `HttpPageLoader` never assigns a baseUrl-resolved value into `page.imageUrl`; the disk-cache key
   and the request are both the raw string the source produced, read back off the page.
3. Guarded by tests:
   - `MangaDexOpaqueImageUrlContractTest` (source-api) — a source shaped exactly like the real
     MangaDex extension: relative `imageUrl`, at-home cache key in `url`, overridden
     `imageRequest`. The "loader write-back pattern" test drives resolve → write back →
     `imageRequest` and asserts the request URL is the correctly joined MD@Home URL; under the
     old rewrite it was the spliced, never-resolvable string.
   - `PageLoadRecoveryStructuralTest` (app) — the loader must not contain `PageImageAddress` at
     all, and `resolvePageImage` must contain the `ResolvedImageUrl.opaque(populated)` passthrough.

**The general lesson, worth stating once and applying everywhere:** fields an extension writes are
the extension's. The host may *judge* what it can judge and may resolve what *it* is about to
request itself, at the boundary where a source override takes over — but it must never write an
interpretation back into a field whose producer is still going to read it. A "helpful" normalisation
on the host side is indistinguishable from corruption on the extension side, and the failure it
produces is always attributed to the wrong layer, because the string the source produced is never
the string that failed.

## Model fields, and how they were closed

The `library.api` audit above covers `HttpSource`. The **data models** diverged further, and this was
the more consequential gap: an extension assigning a field we did not declare failed with
`NoSuchFieldError` - after it had loaded, mid-browse, with no useful message.

**Now closed.** Upstream declares, and this fork now declares:

| Model | Fields added |
|---|---|
`SManga` | `genres`, `banner`, `altTitles`, `contentRating`, `score`, `readingMode`, `language` |
`SChapter` | `number`, `volume`, `scanlators`, `note`, `language`, `locked` |
`SMangaUpdate` | primary constructor taking two suspending lambdas, plus the eager secondary form |

**The deprecated fields are not a substitute, and upstream does not mirror between them.** It
deprecates `genre`/`scanlator` and `chapter_number` rather than removing them and keeps the new field
authoritative - but the two are independent `var`s. A source compiled against 1.4 leaves `genres`
null; one compiled against 1.7 leaves `genre` null.

That was not hypothetical: `SManga.getGenres()` read **only** the deprecated field, so it returned
`null` for every extension compiled against 1.7. The field existed upstream and was invisible here.
`effectiveGenres()` and `effectiveNumber()` now consult the current field first and fall back, so
neither generation of source has its data silently vanish.

`chapter_number` is a `Float` and `number` a `String`, deliberately - a chapter labelled `"12.5a"`
cannot round-trip through a `Float`. That is data loss, not a rename, and it is why the fallback
reads the float rather than the other way round.

**Read from source, not inferred.** These shapes came from `tachiyomix` master's `SManga.kt`,
`SChapter.kt` and `SMangaUpdate.kt`. An earlier attempt inferred `SMangaUpdate` from the ABI dump and
put `runBlocking` inside a getter; it was reverted rather than shipped. Inferring from a dump is how
this document was wrong three times before - see *Tracking upstream mechanically*.

`ExtensionModelCompatibilityTest` covers the assignments, both-field reads, the string numbering, and
that a deferred chapters fetch does not run until it is awaited.

## Adopting a new extension generation

When a new `extension-lib` version ships:

1. **Read the upstream diff for one representative extension of each shape** — an HTML scraper, an API
   source, and a source with custom client/headers.
2. **Diff `source-api` against upstream `tachiyomix`**, and read its `CHANGELOG.md`. That file is the
   authoritative record of what moved and in which version; inferring it from method signatures is how
   this document was wrong three times.
3. **Add the version to `SUPPORTED_LIB_VERSIONS` only after step 2.** Loading an extension against an
   unimplemented contract turns a clear refusal into unpredictable failures.
4. **Add a probe** to `SourceCapabilities` if the new generation introduces a behaviour the app must
   accommodate, with a comment saying what the app must do differently. Do **not** add a version
   comparison at the call site — that is the mistake this document exists to prevent.
5. **Add a fixture** in `HttpPageLoaderUrlResolutionTest` shaped like an extension of that generation,
   asserting **both** directions: recognised as customising, and asked regardless of what `url` holds.
6. **Run the device check.** ADR-0006 requires device evidence for user-visible behaviour, and every
   extension-API change is user-visible.

**Step 5 is not optional.** Both probe failures above were found by a fixture, not by reading the
probe. An enumeration derived from memory is a list of the cases someone remembered.
## Known divergences from upstream

| Divergence | Why | Risk |
|---|---|---|
`Source.getImageUrl` still exists | Ephyra has not migrated to the 1.6 removal; extensions still use it | An extension written against the removed API keeps working. One written against only 1.6 must populate `Page.imageUrl` in `getPageList`, which our loader already reads first. |
`fetchImageUrl` / `imageUrlRequest` / `imageUrlParse` still exist | Upstream `[Unreleased]` removes all three **with no replacement** and drops RxJava; Ephyra carries them so 1.4/1.5 extensions keep working | Correct until the minimum supported version rises. **This is the divergence that will cost us when a generation with no RxJava ships** — see below. |
`Page.url` is resolved before use (`DEF-027`) | upstream requires absolute URLs; a relative `img.attr("src")` was unrequestable | None: absolute URLs pass through byte-identical, preserving the signed-URL guarantee. |
Image URLs are validated at the request boundary | Gives a named layer instead of a DNS error for a host that cannot exist | Only rejects values that could never be requested. |
`PageImageAddress` reads one field per builder | Collapsing them onto one `imageUrl`-preferring rule diverged from upstream and changed what a deprecated-path extension receives | None: the reference reads `page.url` in `imageUrlRequest` and `page.imageUrl` in `imageRequest`. |

## Tracking upstream mechanically

Reading blog posts and inferring from method signatures is how this document was wrong three times.
There is a better source, and it is diffable.

Upstream publishes a binary-compatibility-validator dump at
`library/api/library.api` in [`mihonapp/tachiyomix`](https://github.com/mihonapp/tachiyomix). It is a
flat, one-member-per-line listing of the **entire public ABI** an extension is compiled against.

```
https://raw.githubusercontent.com/mihonapp/tachiyomix/master/library/api/library.api
```

Three things make this the right thing to track:

1. **It is authoritative.** It is generated from the code upstream actually ships, not described in
   prose. `CHANGELOG.md` says *what changed and why*; this says *what exists now*.
2. **It is diffable.** Two fetches and a diff is the whole procedure.
3. **It cannot omit a method.** The omission that caused the reported failure — `fetchImageUrl`
   missing from the probe's enumeration — is a single grep against this file.

As of the fetch this document was written from, upstream `HttpSource` exposes exactly:

```
getBaseUrl  getChapterUrl  getClient  getFilterList  getHeaders  getHomeUrl  getId
getImageUrl  getLanguage  getMangaUrl  getNetwork  getVersionId  headersBuilder
imageRequest  setUrlWithoutDomain x2  toString
```

Note what is **absent**: `fetchImageUrl`, `imageUrlRequest`, `imageUrlParse`, `prepareNewChapter`,
and every `fetch*` catalogue method — those moved to `CatalogueSource`/`Source`. Our `source-api`
still carries all of them, which is the concrete measure of how far behind we are.

**Procedure when checking for drift:**

1. Fetch `library.api` and `CHANGELOG.md` from `master`.
2. Diff the `HttpSource` / `Source` / `CatalogueSource` / `Page` blocks against ours.
3. Anything removed upstream that we still carry is a candidate for removal *with* the version bump
   that makes it legal — see "When upstream finishes the removal".
4. Anything added upstream needs a decision: implement, or record as a known divergence.

## When upstream finishes the removal

Recorded now because it is a **decision**, not a task, and it should not be made by whoever happens
to hit the first breakage.

Upstream `[Unreleased]` removes `fetchImageUrl`, `imageUrlRequest` and `imageUrlParse` with no
replacement, and drops RxJava entirely. After that lands, an extension can only populate
`Page.imageUrl` in `getPageList` or override `getImageUrl` — and, as ever, override `imageRequest`,
which upstream keeps. MangaDex already populates `Page.imageUrl` (a relative path) and overrides
`imageRequest`; it overrides none of the removed members, which is why it kept working on upstream
through the removal.

Ephyra's `source-api` keeps the dead chain, which is correct while we support 1.4 and 1.5. When the
minimum supported version rises, these go **together**:

1. Drop `fetchImageUrl`, `imageUrlRequest`, `imageUrlParse` and the RxJava dependency in one change.
   Keeping some without the others leaves a chain no extension can reach — the state that made the
   reported failure look like a malformed URL when it was a contract mismatch.
2. Reduce the probe to `getImageUrl` alone, and reconsider whether it is worth its keep: a 1.7
   extension that populated `Page.imageUrl` never reaches the fallback the probe guards.
3. Drop `PageImageAddress`'s per-builder field selection with them, since `imageUrlRequest` was the
   only builder reading `page.url`.

**Until then the five-entry-point probe is the correct shape, not a leftover.** (`imageRequest` is
in the probe list even though it is not URL-resolving: the diagnostic reports what a source
overrides, and the sources that override `imageRequest` are precisely the ones whose `Page.imageUrl`
the host must not interpret. When the list named only the four URL-chain entry points, the real
MangaDex was reported as `overrides = none` in the on-screen rejection diagnostic — a claim the
failure itself contradicted.)

## Why this is a capability model and not a flag

Each probe is a named property with a comment saying what the app must do differently when it is
present. A future extension generation therefore adds a probe in one file rather than another `when`
at every call site that has to care - and the call sites read the same way whatever generation an
extension turns out to be.

The negative tests matter as much as the positive ones. The first version of the probe reported every
source as customising, because its own fixture overrode `imageUrlParse` to throw and so *was* a
customising source wearing a baseline's name. A probe with no "nothing is customised" test fails in
exactly that direction and looks green.
