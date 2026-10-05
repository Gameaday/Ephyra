# 13 — Only source types that work ship

Status: Accepted
Date: 2026-09-30

## Decision

Ephyra ships exactly two source types:

1. **Extension APK** — installed from a remote repository (including private and self-hosted ones)
   or imported locally, and driven through the standard `HttpSource`/`CatalogueSource` contract.
2. **Heuristic** — `AdaptiveHeuristicEngine` discovering selectors at runtime for sites no extension
   covers.

Two mechanisms are removed rather than left in the tree:

- **On-device Kotlin-to-JavaScript transpilation.** `transpiler.js` (with its bundled `MiniDOM`) and
  `scripts/convert_legacy_extension.js` translated Kotlin extension sources into sandboxed JS scrapers
  at runtime.
- **The JavaScript scraper runtime.** `ScriptableSourceEngine`, `ScriptableContentSourceEngine`,
  `JavaScriptEngine`, `DynamicScraperUpdater`, `ScraperScriptUpdater`, and the background
  `DynamicScraperUpdateWorker`.

Jellyfin is **not** implemented yet. It will arrive as a new engine rather than as a resurrected
mechanism.

## Context

The transpiler could not represent every source faithfully. It located a source's parsing routine by
searching for the literal text `pageListParse` and matching braces, then rewrote Kotlin to JavaScript by
regular expression, and it assumed every source is HTML addressed by `<img>` elements. A source that
authenticates against an API and then addresses images on a rotating host — MangaDex's at-home
handshake being the canonical example — has no `pageListParse` to find and no `<img>` to scrape. The
pipeline emitted a well-formed JavaScript scraper that could not work, and the failure surfaced as an
obscure malformed image URL rather than as an unsupported source.

That last part is the reason this is a removal and not a repair. A mechanism which fails *silently and
plausibly* is worse than an absent one, because it is indistinguishable from a working one until a user
reports a source that "doesn't work" — which is exactly the report that prompted this decision.

Keeping it also cost clarity in the surrounding architecture. `ContentSourceOrchestrator` routed
`SourceType.REMOTE_EXTENSION` to the script engine on the stated rationale that "remote extensions use
script engine via mapping", which is false for the APK path that was actually working. A source type
whose only purpose was to be fed by the transpiler was threaded through the orchestrator, four
interactors, two screens and a Room column.

## Consequences

**Removing a mechanism nobody can reliably use is cheaper than maintaining one nobody can rely on.**
The app's sourcing surface is now small enough to state in one sentence, which is what makes a report
like "MangaDex images don't load" a question about one path rather than a search across four.

The cost is real and is not being minimised: there is now no mechanism for adding a source without
writing Kotlin. If JS-authored sources are wanted later, they should be hand-authored and run on a
rebuilt QuickJS runtime; regenerating them from Kotlin is what did not survive contact with a real
source. The runtime and `MiniDOM` are recoverable from git history at any time.

`SourceType.JS_SCRAPER` is retired. Persisted profiles carrying it are remapped to
`REMOTE_EXTENSION` by a Room migration rather than left to resolve against a deleted engine, so an
existing install keeps its sources instead of silently losing them.

Adding a source type must not require editing a `when` over every type that exists. Engine selection
belongs to a registry, so Jellyfin arrives as one engine plus one binding. That is the constraint this
decision exists to make room for, and it is why the transpiler's removal and the registry are one piece
of work rather than two.