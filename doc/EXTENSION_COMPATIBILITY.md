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
modern `getImageUrl`. It overrides `fetchImageUrl` instead, because its `Page.url` is not an image
address at all:

```kotlin
val (host, tokenRequestUrl, time) = page.url.split(",")
```

That is a `(host, tokenUrl, fetchTime)` **at-home token cache key**, stored in `Page.url` by design -
MangaDex@Home tokens expire after five minutes, so the extension caches the server, the URL that
refreshes it, and when it was fetched. Only its own code knows how to read that.

An app that assumed `Page.url` was an image address would fetch the key, get a DNS failure for a host
that could never exist, and report a network fault for what is a contract misunderstanding.
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
`Page.imageUrl` in `getPageList` or override `getImageUrl`. MangaDex already does the latter.

Ephyra's `source-api` keeps the dead chain, which is correct while we support 1.4 and 1.5. When the
minimum supported version rises, these go **together**:

1. Drop `fetchImageUrl`, `imageUrlRequest`, `imageUrlParse` and the RxJava dependency in one change.
   Keeping some without the others leaves a chain no extension can reach — the state that made the
   reported failure look like a malformed URL when it was a contract mismatch.
2. Reduce the probe to `getImageUrl` alone, and reconsider whether it is worth its keep: a 1.7
   extension that populated `Page.imageUrl` never reaches the fallback the probe guards.
3. Drop `PageImageAddress`'s per-builder field selection with them, since `imageUrlRequest` was the
   only builder reading `page.url`.

**Until then the four-entry-point probe is the correct shape, not a leftover.**

## Why this is a capability model and not a flag

Each probe is a named property with a comment saying what the app must do differently when it is
present. A future extension generation therefore adds a probe in one file rather than another `when`
at every call site that has to care - and the call sites read the same way whatever generation an
extension turns out to be.

The negative tests matter as much as the positive ones. The first version of the probe reported every
source as customising, because its own fixture overrode `imageUrlParse` to throw and so *was* a
customising source wearing a baseline's name. A probe with no "nothing is customised" test fails in
exactly that direction and looks green.