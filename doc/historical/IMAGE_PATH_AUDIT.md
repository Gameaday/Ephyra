# Image path audit — what we added, and what to remove

> **Status:** HISTORICAL - completed improvement-session record. Retained for archaeology only;
> not current guidance. Current forward plan: [`../2_0_COMPLETION_PLAN.md`](../2_0_COMPLETION_PLAN.md).

Written while a device test of the reported MangaDex failure is in flight. **No behaviour change
accompanies this document**, deliberately: changing the image path mid-test would invalidate the
result it is being collected for.

The comparison baseline is `4fc861d9`, the last state known to work on device.

## Volume added

| File | Before | After |
|---|---|---|
`core/common/.../ImageUrlPolicy.kt` | — (new) | 375 |
`source-api/.../PageImageAddress.kt` | — (new) | 382 |
`source-api/.../HttpSource.kt` | 476 | 580 |

**757 lines of entirely new code on the image path**, plus 104 added to the class extensions link
against. The regression window is somewhere inside that.

## Classifying it: three kinds of code, not one

### 1. Validation — keep

`defectOf`, `isUsable`, `requireUsable`. These replace a DNS verdict about a host that can never
exist with a precise statement of why the value is unusable, before a request is spent. Pure gain, no
behaviour change for any source that produces a valid address.

### 2. Resolution — keep

`resolve()` turning a relative value into an absolute one against `baseUrl`. This is DEF-027; without
it a source returning `img.attr("src")` is unrequestable. The old code did not have this and it is a
real improvement.

### 3. Repair — **questionable, and I recommend narrowing**

`repair()` **modifies what the source produced**: it trims, strips control characters, and decodes
`&amp;`. This is the one place we alter a source's bytes rather than check them, and it is the same
category of act as the `page.imageUrl` write-back that was removed for exactly that reason.

It has already caused one problem. It deletes the character that separates two URLs, turning a
visibly-malformed composite into a well-formed host — a silent corruption guarded by an explicit
check (`it contains more than one scheme separator`). Guarding a hazard we introduced is a worse
position than not having the hazard.

`&amp;` decoding is also not free: a URL with a literal `&amp;` in a **path segment** — unusual but
real — would be corrupted.

**Recommendation:** keep trimming (it fixed a real silent-join bug), drop control-character stripping
and `&amp;` decoding. Pass the source's bytes through otherwise unchanged. A malformed value is then
rejected with a reason rather than quietly reshaped into a different malformed value.

### 4. Decision — **remove**

`SourceCapabilities` (29 lines of code, 103 with comments) probes the loaded class hierarchy by name to
decide whether a source customises the image-URL chain. Two behaviours are gated on its answer:

- `resolvePageImage` refuses pages when it says "does not customise"
- `cachedPagesAreUsable` rejects cached lists when it says "does not customise"

**A probe has two failure directions and both are bad.** Saying "does not customise" for a source that
does refuses pages that were readable — which happened **twice** today, first because `fetchImageUrl`
was missing from the enumeration, then because `getMethods` returns only public methods and two entry
points are `protected`. Saying "customises" for a source that does not lets the inherited chain treat
`page.url` as an address — the original failure.

The pre-regression code had no equivalent. It asked one question and trusted the answer:

```kotlin
if (page.imageUrl.isNullOrEmpty()) page.imageUrl = source.getImageUrl(page)
```

**Every one of today's failures was a wrong decision. None was a missing validation.** That is the
finding.

## What removal means concretely

1. `resolvePageImage` returns to the old two-line rule — `imageUrl` if populated, otherwise
   `getImageUrl` — then validates the result. No probe, no gate, no field preference.
2. `SourceCapabilities`, `resolvesOwnPageImages` and the gate branch are deleted.
3. `cachedPagesAreUsable` needs a replacement for its capability argument. **It should not be replaced
   with another inference.** The loader already knows whether the pages it just fetched had addresses;
   that is a recorded fact, not a probe. Storing it beside the cached page list turns a guess into
   data.

## Cost

Nine of the files matching `capabilities` in tests are about the unrelated target/Jellyfin source
descriptor. The probe's actual test surface is small — `HttpPageLoaderUrlResolutionTest` and a few
cases in `HttpSourceImageUrlRequestTest` — so removal is mechanical.

## What this does not claim

It does not claim the probe caused the current failure. It claims the probe is **net-negative**: it
has broken working sources twice, its absence is the pre-regression behaviour, and no test can prove
it correct for a class we have not yet loaded. If the device test shows `at = resolvePageImage/getImageUrl`
and a populated `pageImageUrl`, the cause is elsewhere and removal is still the right cleanup — it just
won't be the fix.

## Order of work

Removal first, in one commit, with the cache signal moved to recorded data. If the device test then
passes, the probe was the cause. If it fails identically, we are back to the pre-regression behaviour
with validation intact, which is a strictly better position than the one we are in now.