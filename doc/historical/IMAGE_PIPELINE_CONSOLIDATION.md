# Implementation Plan

> **Status:** HISTORICAL - completed improvement-session record. Retained for archaeology only;
> not current guidance. Current forward plan: [`../2_0_COMPLETION_PLAN.md`](../2_0_COMPLETION_PLAN.md).

## Overview

Consolidate the image address pipeline so that resolving, validating, attributing and caching a page image each happen in exactly one place, rather than a "resolve, then judge" two-step repeated at eight call sites with two different field-preference rules.

The pipeline currently works and its tests are green. This is not a bug fix — it removes a class of defect where a call site can resolve an address but forget to judge it, which is how a malformed URL reached DNS unreported in the first place.

Scope is `core/common` (policy), `source-api` (the extension contract), `feature/reader` and `core/download` (the two consumers). No behaviour change is intended for any URL that is already clean; every change is about making the *same* decision in one place instead of several.

## Types

### `ResolvedImageUrl` (new, `core/common/.../network/ResolvedImageUrl.kt`)

The outcome of preparing one page image's address. Its purpose is to make the two-step pattern unrepresentable: you cannot hold a resolved URL without having already been judged.

```kotlin
@JvmInline
value class ResolvedImageUrl private constructor(val value: String) {
    companion object {
        /** Resolve [url] against [baseUrl] and judge the result, or throw. Sole constructor. */
        fun of(url: String?, baseUrl: String?): ResolvedImageUrl
    }
}
```

`of` performs `resolve` then `defectOf` internally, throwing `MalformedImageUrlException(url, reason)` on a defect. On success it returns exactly what `resolve` returned — byte-identical, preserving the signed-URL guarantee documented at `ImageUrlPolicy.kt:192-195`.

### `PageImageAddress` (new, `source-api/.../online/PageImageAddress.kt`)

The field-preference rule, currently duplicated at `HttpSource.kt:446-449` and `501-504`, and **absent** from the reader and downloader.

```kotlin
data class PageImageAddress(
    val url: ResolvedImageUrl,
    /** Which `Page` field it came from — `imageUrl` preferred, `url` the fallback. */
    val field: Field,
    /** The value the source produced, before resolution. For diagnostics. */
    val raw: String,
) {
    enum class Field { IMAGE_URL, URL }
}
```

`PageImageAddress.of(page: Page, baseUrl: String?)` holds the only implementation of "try `imageUrl`, then `url`, return the first that can address a host". It throws naming the *preferred* field when neither works, matching existing behaviour and reasoning at `HttpSource.kt:457-462`.

It lives in `source-api` rather than `core/common` because it references `eu.kanade.tachiyomi.source.model.Page`, and `core/common` must not depend on `source-api`. The reader and downloader already depend on both, so every consumer gets access without inverting the graph.

### No changes to existing types

`ImageUrlPolicy`, `MalformedImageUrlException`, `FailureLayer`, `LayeredFailure`, `PageLoadRecovery` keep their current public shapes. `PageLoadRecovery` gains no constructor parameters; only the line it logs changes.

## Files

### New

| Path | Purpose |
|---|---|
| `core/common/src/main/java/ephyra/core/common/util/network/ResolvedImageUrl.kt` | The judged-address wrapper. |
| `source-api/src/main/kotlin/eu/kanade/tachiyomi/source/online/PageImageAddress.kt` | The field-preference rule, in the module that can see `Page`. |
| `source-api/src/test/kotlin/eu/kanade/tachiyomi/source/online/PageImageAddressTest.kt` | Preference order and failure cases. |

### Modified

| Path | Change |
|---|---|
| `source-api/.../online/HttpSource.kt` | `imageUrlRequest` (445) and `imageRequest` (~501) collapse to `PageImageAddress.of(page, baseUrl)`, removing ~30 duplicated lines. |
| `feature/reader/.../loader/HttpPageLoader.kt` | Sites 538/545 and 559/569 become one `PageImageAddress.of` each. Site 752's `isUsable(resolve(...))` becomes `runCatching { ResolvedImageUrl.of(...) }.isSuccess`. Line 656 unchanged. |
| `core/download/.../Downloader.kt` | Sites 418 and 663 use `PageImageAddress.of`. Its give-up log names the layer, matching the reader. |
| `app/.../online/DynamicHttpSource.kt` | Line 48 `resolveUrl` and line 129 `requireUsable` use `ResolvedImageUrl`. |

### Unchanged, deliberately

- `ImageUrlPolicy.kt` — `resolve`, `defectOf`, `isUsable`, `requireUsable` all stay. They are the primitives; this plan changes who calls them, not what they do.
- `PageLoadRecovery.kt` — already owns retry and backoff. Only its terminal log line gains a layer prefix.
- `ChapterCache.kt` — keyed on the resolved string both consumers already pass.

### No deletions

This is consolidation, not removal.

## Functions

**New**

- `ResolvedImageUrl.of(url: String?, baseUrl: String?): ResolvedImageUrl` — `resolve` then `defectOf`; throws `MalformedImageUrlException` on defect. Sole constructor.
- `PageImageAddress.of(page: Page, baseUrl: String?): PageImageAddress` — the preference loop, lifted verbatim from `HttpSource.kt:446-462`.

**Modified**

- `HttpSource.imageUrlRequest(page): Request` — becomes `GET(PageImageAddress.of(page, baseUrl).url.value, headers)`.
- `HttpSource.imageRequest(page): Request` — same. **The two must stay behaviourally identical**; a new test asserts equal requests for the same page across every fixture shape. Their divergence is what the duplicated loops caused once already.
- `HttpPageLoader` page-fetch arms (538-569) and the requeue guard (752).
- `Downloader` resolution (418) and re-resolve (663).
- `PageLoadRecovery` terminal log line — prefix with `LayeredFailure.classify(...).layer.name`, matching `HttpPageLoader.kt:656`.

**Unchanged**

`ImageUrlPolicy.resolve`, `defectOf`, `isUsable`, `requireUsable`, `repair`, `LayeredFailure.classify`.

## Classes

No new classes beyond the two types above; none modified or removed. `ImageUrlPolicy` and `PageLoadRecovery` remain objects with unchanged APIs.

## Dependencies

**No new packages.** One module-boundary constraint, explained under `PageImageAddress` above: it needs `Page`, so it goes in `source-api`; `ResolvedImageUrl` has no such constraint and stays in `core/common` so `ImageUrlPolicy`'s own tests can use it.

## Testing

**New tests**

- `PageImageAddressTest` — `imageUrl` preferred when both usable; falls back to `url` when `imageUrl` is blank; falls back when `imageUrl` is present but defective and `url` is good; throws naming `imageUrl` when both defective; throws when neither is populated.
- `PageImageAddress` cleanliness — `raw` is recorded before resolution, so a diagnostic can show what the source actually emitted.
- Parity assertion in `HttpSourceImageUrlRequestTest`: both builders produce equal requests for every fixture shape.

**Existing tests that must stay green**

- `ImageUrlPolicyTest`, `ImageUrlPolicyResolveTest` — signed-URL byte-identity, `,https` rejection.
- `HttpPageLoaderCdnSwapTest` — including the MangaDex case asserting *no request is spent* and the fault attributed to `ADAPTER`.
- `HttpPageLoaderUrlResolutionTest`, `PageLoadRecoveryTest`, `FailureLayerTest`.
- `app` structural gates.

**The gate that matters most**

A structural test asserting no main-source file resolves and judges the same value in two steps — a `resolve(` paired with a `requireUsable(`/`defectOf(` on one value. Without it the duplication returns within a month, because the two-step form is currently the established idiom here and nothing says otherwise.

## Implementation Order

1. Add `ResolvedImageUrl` + tests. No callers changed; `:core:common` compiles and passes.
2. Migrate `DynamicHttpSource` (one site) — proves the type against a real consumer before anything depends on it.
3. Add `PageImageAddress` + tests in `source-api`.
4. Collapse `HttpSource.imageUrlRequest` and `imageRequest`; add the parity assertion.
5. Migrate `HttpPageLoader` (538/545, 559/569, 752). Run `:feature:reader` — the CDN-swap and MangaDex tests are what prove behaviour is unchanged.
6. Migrate `Downloader` (418, 663); add the layer to its terminal log. Run `:core:download`.
7. Add the structural no-two-step gate.
8. Full sweep: `:core:common`, `:source-api`, `:feature:reader`, `:core:download`, `:core:data`, `:app`, plus `spotlessCheck`.

Steps 1-4 are behaviour-neutral by construction. Steps 5-6 are the only ones that could alter observable behaviour, and both are covered by existing tests asserting the properties that matter — no request spent on a malformed URL, a `,https` URL rejected, signed URLs untouched, a CDN swap still recovering.

