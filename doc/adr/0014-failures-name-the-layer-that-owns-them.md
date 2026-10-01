# 14 — Failures name the layer that owns them

Status: Accepted
Date: 2026-09-30

## Decision

A content failure is attributed to the layer that **owns** it — `SOURCE`, `ADAPTER`, `TRANSPORT`, or
`RENDER` — and the crossing from a provider's shape to ours is an explicit seam (`ContentAdapter`) that
validates its own output.

Three consequences:

1. `LayeredFailure` classifies a throwable into a layer and keeps the cause, so the existing
   `TransientErrors` classifier still sees the type it needs.
2. `ContentAdapter` is the one place a provider's response becomes canonical. An adapter that emits an
   unusable address must refuse it there rather than forward it.
3. `ContentConformance` is a reusable harness every adapter must pass, and
   `SourceTypeRegistryStructuralTest` keeps source-type selection additive.

## Context

The MangaDex failure was reported as `Unable to resolve host
"cmdxd98sb0x3yprd.mangadex.network,https"`. That is a **transport** fact — and it is the last thing
that happened, not the first. The string was malformed when the layer that produced it produced it;
every layer after that carried a broken value faithfully and reported on the wrong subject. Diagnosing
it took a number of exchanges, and **none could have been avoided from the error text alone**, because
the text described the layer that noticed rather than the layer at fault.

Two things made that specific mistake possible, and both are now fixed structurally rather than by
convention:

**There was no place to name the layer.** Errors carried a message and a type. A type can be
misleading — `MalformedImageUrlException` extends `IOException`, so an `IOException`-shaped classifier
reads it as a network fault — and a message describes what happened last. `FailureLayer` is the
missing field, and `LayeredFailure.classify` defaults an unrecognised failure to `SOURCE` rather than
`TRANSPORT` precisely because defaulting to "network" is the bias that sent this investigation to the
resolver.

**There was no seam to validate at.** The tree held two content contracts — `ContentItem`/`ContentUnit`
behind `RemoteSource`, and `CatalogEntry`/`ChapterInfo`/`ContentPage` behind `UnifiedContentSource` —
and an extension source used *neither* directly: APK → Tachiyomi ABI → `DynamicHttpSource` →
`ContentItem`. So "where does a source's data become ours" had three answers, and a defect introduced
in one could surface in another with nothing naming the crossing.

## Consequences

**The honest limit of a content contract.** A shape validator at the adapter seam would *not* have
caught the MangaDex defect on its own: the string was well-formed enough for every layer to accept, and
it only failed at DNS. What caught it was `ImageUrlPolicy`, which was already the single owner of "is
this address requestable" — the fix was routing every request boundary through it, and this ADR
records that the *seam* is where that judgement belongs, not each caller's judgement. That is the
real lesson: a contract is worth having at the boundary where the shape is produced, and the defect
existed because one boundary did not consult the owner.

**Four layers, not three.** "Renderer, source, adapter" was the right instinct and it needed a fourth
bucket. Image transport is downstream of the adapter and upstream of the renderer, and folding it into
either is what made the failure ambiguous. `TRANSPORT` is where a problem is *observed*; it is not
necessarily where it originated.

**The conformance harness is the deliverable, not the tests.** `ContentConformance` is what makes the
adapter/source distinction checkable rather than asserted in a comment. Jellyfin arrives as a second
implementation of the same contract, and the harness is what says whether it is a peer or a fork. It is
also the only one of these layers fully testable without a device — which is why it would have caught
this class of defect in seconds rather than on a user's report.

**What is deliberately deferred.** Unifying `ContentItem`/`ContentUnit` with
`CatalogEntry`/`ChapterInfo` is *not* done here. `ContentItem` wins on adoption (~20 consumers against
3), but the migration is safer once a second adapter exists to validate the shape against. Doing it now
would be a large refactor of repositories, tracking and ingest, validated only by the one adapter
already in the tree.