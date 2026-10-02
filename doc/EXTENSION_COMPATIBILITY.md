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
modern `getImageUrl`. It overrides `imageUrlRequest` and `imageUrlParse` instead, because its
`Page.url` is not an image address at all:

```kotlin
val (host, tokenRequestUrl, time) = page.url.split(",")
```

That is a `(host, tokenUrl, fetchTime)` **at-home token cache key**, stored in `Page.url` by design -
MangaDex@Home tokens expire after five minutes, so the extension caches the server, the URL that
refreshes it, and when it was fetched. Only its own code knows how to read that.

An app that assumed `Page.url` was an image address would fetch the key, get a DNS failure for a host
that could never exist, and report a network fault for what is a contract misunderstanding.
## The image-URL chain, and the three ways to customise it

| Entry point | Signature | Role |
|---|---|---|
`getImageUrl` | `suspend fun (Page): String` | modern; overrides everything |
`imageUrlRequest` | `protected fun (Page): Request` | deprecated chain: what to fetch |
`imageUrlParse` | `protected fun (Response): String` | deprecated chain: read the address out of the response |

Overriding **either** deprecated method is a complete customisation. A source that overrides only
`imageUrlParse` still needs the app to fetch the page and hand over the response.

`SourceCapabilities.customisesImageUrlChain` reports true for any of the three. When it is true, the
app must not treat `Page.url` as an image address. When it is false, the app's own chain runs and
`Page.url` is expected to be fetchable.

Detection walks `getDeclaredMethods` up the hierarchy rather than using `Class.getMethods`, because
`imageUrlRequest` and `imageUrlParse` are `protected` and `getMethods` returns public members only.
A probe written against `getMethods` silently misses both even with the right names - which is
exactly how an earlier version reported MangaDex as uncustomising and blocked the very chain it was
written to accommodate.

## Adopting a new extension generation

When a new `extension-lib` version ships:

1. **Read the upstream diff for one representative extension of each shape** - an HTML scraper, an API
   source, and a source with custom client/headers. MangaDex moved to 1.6 in keiyoushi
   `extensions-source` commit `237a13600d`, which changed its base class and dropped the deprecated
   observables.
2. **Diff `source-api` against upstream.** Newer Mihon has *removed* `getImageUrl` from the `Source`
   interface entirely; Ephyra still carries it. That divergence is deliberate for now - see "Known
   divergences" - but it is the thing to re-examine first.
3. **Add the version to `SUPPORTED_LIB_VERSIONS` only after step 2.** Loading an extension against an
   unimplemented contract turns a clear refusal into unpredictable failures.
4. **Add a probe** to `SourceCapabilities` if the new generation introduces a behaviour the app must
   accommodate, with a comment saying what the app must do differently. Do **not** add a version
   comparison at the call site - that is the mistake this document exists to prevent.
5. **Add a fixture** in `HttpPageLoaderUrlResolutionTest` shaped like an extension of that generation,
   asserting both directions: recognised as customising, and asked regardless of what `url` holds.
6. **Run the device check.** ADR-0006 requires device evidence for user-visible behaviour, and every
   extension-API change is user-visible.
## Known divergences from upstream

| Divergence | Why | Risk |
|---|---|---|
`Source.getImageUrl` still exists | Ephyra has not migrated to the 1.6 removal; extensions still use it | An extension written against the removed API keeps working. One written against only 1.6 must populate `Page.imageUrl` in `getPageList`, which our loader already reads first. |
`Page.url` is resolved before use (`DEF-027`) | upstream requires absolute URLs; a relative `img.attr("src")` was unrequestable | None: absolute URLs pass through byte-identical, preserving the signed-URL guarantee. |
Image URLs are validated at the request boundary | Gives a named layer instead of a DNS error for a host that cannot exist | Only rejects values that could never be requested. |
`PageImageAddress` reads one field per builder | Collapsing them onto one `imageUrl`-preferring rule diverged from upstream and changed what a deprecated-path extension receives | None: the reference reads `page.url` in `imageUrlRequest` and `page.imageUrl` in `imageRequest`. |

## Why this is a capability model and not a flag

Each probe is a named property with a comment saying what the app must do differently when it is
present. A future extension generation therefore adds a probe in one file rather than another `when`
at every call site that has to care - and the call sites read the same way whatever generation an
extension turns out to be.

The negative tests matter as much as the positive ones. The first version of the probe reported every
source as customising, because its own fixture overrode `imageUrlParse` to throw and so *was* a
customising source wearing a baseline's name. A probe with no "nothing is customised" test fails in
exactly that direction and looks green.