# ADR-0002: One main navigation owner

- **Status:** Accepted, **amended** by [ADR-0011](0011-one-graph-coordinator-not-one-navhost.md) (2026-09-28)
- **Date:** 2026-09-24
- **Decision:** The main application uses one typed Navigation Compose graph and one navigation coordinator.

> **Amended 2026-09-28.** The requirement of one coordinator and typed routes is retained. The
> claim below that nested navigation code is *removed* is **withdrawn**: a nested `NavHost` per
> bottom tab is the standard Android pattern, because each tab needs an independent back stack.
> `B-028` checked this against the tree and found the flattening claim unfounded — including its
> instruction to delete "duplicate string route hierarchies", of which exactly one exists. The
> defect ADR-0002 was reaching for is duplicate *mutable navigation state*, not physical nesting;
> removing the global navigation event objects is the real work, and `NAV-001` is re-scoped to it.

## Context

The current app has an outer `NavHost` in `MainActivity` and a nested bottom-tab `NavHost` in `HomeScreen`. Series details live in the outer graph while the cover source lives in the inner graph. This makes shared transitions, back-stack state, and transition ownership ambiguous.

## Decision

- Library, Updates, History, Browse, More, Series, Settings, and detail destinations live in one main graph.
- The bottom bar/rail is a shell around that graph.
- Cross-feature navigation is resolved by the app coordinator, not sibling feature callbacks.
- A reader Activity may remain separate for immersive, secure, and hardware lifecycle requirements.
- No global mutable navigation event singleton is used.

## Consequences

- Shared elements have one navigation owner.
- Predictive back has one back stack.
- Feature modules require small public navigation contracts.
- Existing nested navigation code is removed rather than incrementally duplicated.

## Rejected alternatives

- A second outer graph for shared destinations.
- Global navigation events.
- Keeping both graphs and coordinating them with ad hoc callbacks.

## Evidence required

Navigation contract tests, back-stack/deep-link tests, predictive-back tests, and compact/expanded screenshots.

## Supersedes / superseded by

Amended by [ADR-0011](0011-one-graph-coordinator-not-one-navhost.md), which retains the single
coordinator and typed routes and withdraws only the requirement to collapse nested `NavHost`s.
