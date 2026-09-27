# ADR-0011: One graph coordinator, not one physical NavHost

- **Status:** Accepted
- **Date:** 2026-09-28
- **Amends:** [`0002`](0002-single-navigation-owner.md)
- **Decision:** The main application has one navigation *coordinator* and one typed route hierarchy.
  A nested `NavHost` per bottom tab is retained, because it is the standard Android pattern rather
  than a defect.

## Context

ADR-0002 stated that library, updates, history, browse, more, series, settings and detail
destinations live in **one main graph**, and listed "existing nested navigation code is removed" as a
consequence. `B-028` then checked that claim against the tree and found it did not hold:

- The two `NavHost`s are `MainActivity.kt:251` (app-level destinations) and `HomeScreen.kt:178`
  (the five bottom tabs).
- **Nested `NavHost`s for bottom tabs is the documented Android pattern.** Each tab needs an
  independent back stack, so that pressing back in Library returns to the Library tab's own root
  rather than unwinding to whichever tab was visited before it. A single flat graph cannot express
  that without reimplementing per-tab stacks by hand.
- `NAV-001`'s instruction to "delete duplicate string route hierarchies" was also unfounded: there is
  exactly **one** `ScreenRoutes.kt`, in `presentation-core`. There was nothing to delete.
- The feature modules already register through a `FeatureApi` contract
  (`presentation-core/.../feature/FeatureApi.kt`) that each implements, which is a coordinator, not a
  sibling-feature callback arrangement.

So the ledger and the ADR disagreed about the product, and the ADR was the document that had been
treated as authoritative.

## Decision

- **One coordinator, one typed route hierarchy.** Cross-feature navigation resolves through the app
  coordinator. Sibling features never navigate to each other directly.
- **A nested `NavHost` per bottom tab is correct and is retained.** `HomeScreen`'s tab host is not
  a second navigation owner competing with `MainActivity`; it owns tab-scoped back stacks, which is
  a different job from owning app-level destinations.
- The real defect ADR-0002 was reaching for is **duplicate mutable navigation state**, not the
  physical nesting: the global `MutableStateFlow`/`MutableSharedFlow` navigation events in
  `presentation-core/.../ui/navigation/ScreenRoutes.kt`. Those are the thing to remove.
- A reader Activity remains separate for immersive and secure-window requirements.

## Consequences

- `NAV-001` is **re-scoped** from "collapse the nested `NavHost`s" to: remove the global mutable
  navigation event objects, make every destination typed and coordinator-resolved, and prove
  predictive back and shared-element motion against the resulting graph. This is a far smaller and
  safer piece of work than flattening, and it addresses the ambiguity the ADR actually described.
- Phase 9 is unblocked without a risky navigation rewrite on a surface that already works.
- The five tabs keep independent back stacks, which is the behaviour users expect from any
  bottom-navigation app.
- **What is not claimed:** that the current arrangement is free of ambiguity. The outer graph hands
  destinations to the inner one, and that seam is where shared-element and back behaviour must be
  proven. `NAV-001` owns proving it.

## Rejected alternatives

- **Flatten both `NavHost`s into one graph.** Rejected: it discards per-tab back stacks, which is a
  real behavioural regression, in exchange for a tidier diagram. `B-028` classified this as "a
  design decision with real costs, not a cleanup", and that assessment stands.
- **Delete this ADR and leave ADR-0002 standing.** Rejected: ADR-0002 would continue to assert that
  nested navigation code is removed while the nested code ships. That is the documentation failure
  ADR-0006 exists to prevent.
- **Amend ADR-0002 in place.** Rejected per the ADR protocol in
  [`README.md`](README.md): "To reverse a decision, create a new ADR. Do not edit history in place."

## Evidence required

- Navigation contract tests over the typed route hierarchy.
- Back-stack tests proving per-tab independence is preserved.
- Predictive-back tests across the app↔tab seam.
- Compact/expanded screenshots for the adaptive shell.
- **Falsification-verified:** removing the coordinator must break a declared test, or the change is
  decoration.

## Supersedes / superseded by

Amends [`0002`](0002-single-navigation-owner.md). ADR-0002's requirement of a single coordinator and
typed routes is retained and restated; only its claim that nested `NavHost`s must be collapsed is
withdrawn.
