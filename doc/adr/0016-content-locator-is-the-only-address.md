# 0016 — A `ContentLocator` is the only address the reader accepts

**Status:** Accepted
**Date:** 2026-10-02
**Related:** ADR-0012 (source identity has one owner), ADR-0014 (failures name their layer),
`doc/2_0_CONTENT_MODEL_PLAN.md`

## Context

The reader and the downloader are handed content and asked to fetch it. They must not know where it
came from — an extension APK, an OPDS catalogue, an archive on disk, and a Jellyfin server each have
their own idea of an address, and none of them is the reader's business.

Before this, "where does this come from" was a bare `String`, so every consumer had to interpret it.
Interpretation is where the reported MangaDex failure lived: a three-part composite,
`host,https://api.example.com/chapter,1790986074071`, reached DNS and came back as
`Unable to resolve host`. Nothing between the source and the socket refused it, because refusing it
required understanding a shape that varies by source.

## Decision

Introduce `ContentLocator` and `EngineId`, both in `core/domain`.

- The locator's value is **private**. The only way to obtain one is `ContentLocator.of`, which
  resolves and then judges. Callers cannot read the address back and branch on its shape.
- `EngineId` is a value class, not an enum, so adding Jellyfin is additive rather than an edit to a
  shared file. An enum would reintroduce the coupling ADR-0015 removed for source types.
- A locator joins its engine id to its address with `U+001F`, a control character no URL can contain,
  so the split is unambiguous and cannot be undone by trimming — the failure mode that produced the
  reported `hosthttps://…` splice.
- `fromPersisted` is the only path that rehydrates without re-judging, and it exists because a stored
  value was already judged when it was written. It does not accept a bare URL.

## Consequences

- "Is this address usable" has exactly one home, and the compiler enforces the boundary rather than a
  code review.
- A consumer needing the raw string must go through an adapter — which is where a Jellyfin or
  local-archive implementation plugs in without the reader learning its vocabulary.
- Locators are persisted, so `EngineId` values are effectively permanent; renaming one is a migration.

## Alternatives rejected

**A `String` with a documented convention.** Rejected: a convention is unenforceable, and the whole
history of this bug is a consumer not following one.

**Validate later, at the request boundary.** Rejected: that is what let a malformed value be
indistinguishable from a good one. The consumer cannot tell which it holds.

## Evidence required

ADR-0006: this is user-visible and needs device evidence. Constructing a locator in a unit test
proves the type, not the loading.