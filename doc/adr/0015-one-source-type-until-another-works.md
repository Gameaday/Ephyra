# 15 — One source type until another one works

Status: Accepted
Date: 2026-09-30

## Decision

Ephyra ships **one** working source type: the extension APK. The heuristic source type is removed, and
with it everything that existed only to create or serve a heuristic profile.

Removed: `AdaptiveHeuristicEngine`, `AddCustomSource` (which by then did nothing but add heuristic
profiles), `SourceType.HEURISTIC`, and the heuristic UI in both the Extensions and Source Management
screens.

`JS_SCRAPER` and `HEURISTIC` are both retired from the enum but **kept as persisted-name aliases** in
`SourceType.fromString`, both folding into `REMOTE_EXTENSION`. An install that persisted one before the
removal must not fall through to a default and change behaviour with no error.

## Context

This follows `ADR-0013` directly and applies the same test. The heuristic engine discovered a site's DOM
structure at runtime and inferred selectors for search grids, item details, chapter lists and reader
images. It did not work well enough to be worth keeping: it never became the source of a working
chapter, and it carried a large surface — a 347-line engine, its own source type, a dedicated interactor,
four screens' worth of UI, and its own branch in the orchestrator's dispatch.

The decisive fact is what it *wasn't* doing. `AndroidSourceManager` registers extension-APK sources
straight from the installed extensions; only **profiled** domains went through the orchestrator, and the
heuristic engine was the only thing that ever created a profile. Removing it therefore touches nothing
on the APK path — verified by reading the source-map construction before deleting anything.

Leaving it in was not free. It was the only bound engine, which made it the orchestrator's *fallback* —
so a source type with no engine silently got scraped as HTML. That is the failure mode `ADR-0014`
describes, and it was live: `REPOSITORY` resolved to it via a comment reading `// Repositories use
heuristic for now`.

## Consequences

**The engine registry is now empty, and that is correct.** `provideContentSourceEngines()` returns
`emptyList()`. The orchestrator no longer assumes a fallback exists: `fallbackEngine` is nullable and
`NoEngineBoundException` names the unbound type. A constructor that threw `NoSuchElementException`
because `engines.first()` found nothing would have turned an ordinary state — "no engine until Jellyfin"
— into a startup crash.

**Two source types remain in the enum, not two *working* types.** `REMOTE_EXTENSION` works;
`REPOSITORY` is declared and unimplemented, awaiting Jellyfin. Declaring it is what lets Jellyfin land
as an addition rather than a new concept.

**The `AddWebSource` button now closes the dialog without adding.** The heuristic path was the only
thing behind it. Leaving it silently doing nothing would have been worse than removing the plumbing, so
the handler is explicit about why.

**What this costs.** Generic HTML scraping was a real capability, and sites with no extension lose
their fallback. That is accepted deliberately: the fallback did not work, and a broken fallback that
looks like a feature is what made "MangaDex is not working" so hard to reason about.

**What it buys.** The sourcing surface is now one sentence long, and a report like "MangaDex images
don't load" has exactly one path to investigate. That was the goal.

## Re-entry

Heuristic discovery is not forbidden — it is *not yet earned*. If it returns, it returns as an engine
declaring `handles`, passing `ContentConformance`, with a fixture proving it works against real sites.
`ADR-0013`'s test applies: a mechanism that cannot be tested off-device does not ship.