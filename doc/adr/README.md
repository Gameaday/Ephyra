# Architecture Decision Records

These records describe decisions that shape multiple Ephyra 2.0 phases. They are intentionally short and evidence-oriented.

## Accepted decisions

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-one-owner-per-truth.md) | One authoritative owner for every state and resource | Accepted |
| [0002](0002-single-navigation-owner.md) | One main navigation owner; reader Activity is explicit | **Amended** by [0011](0011-one-graph-coordinator-not-one-navhost.md) — one coordinator and typed routes, but a nested `NavHost` per tab is correct |
| [0003](0003-document-viewport-zoom.md) | Continuous zoom uses a virtualized document viewport | **Superseded in part** by [0010](0010-region-decoded-slices-not-tile-engine.md) — the tile engine is retired; the zoom-ownership principles remain in force |
| [0004](0004-media-pipeline-and-cache.md) | Media separates source identity, decode plans, and render caches | Accepted |
| [0005](0005-feature-boundaries.md) | Features depend on contracts, not implementations or siblings | Accepted |
| [0006](0006-evidence-before-completion.md) | Device behavior requires executable evidence | Accepted |
| [0007](0007-source-is-not-a-ui-adapter.md) | Sources are capability-based platform components; legacy extensions are temporary adapters | Accepted |
| [0008](0008-contract-first-ahead-of-phase-gate.md) | Pure unwired contract work may precede its phase gate; it earns `CODE_COMPLETE`, never `VERIFIED` | Accepted |
| [0009](0009-evidence-channels-match-validation.md) | Evidence levels are defined by producing system; user-device validation is a first-class channel; a missing channel is a blocker, never a pass | Accepted |
| [0010](0010-region-decoded-slices-not-tile-engine.md) | Continuous reader keeps `LazyColumn` and decodes fixed-height slices via `BitmapRegionDecoder`; the tile engine is retired | Accepted |
| [0011](0011-one-graph-coordinator-not-one-navhost.md) | One navigation coordinator and typed routes; a nested `NavHost` per bottom tab is retained | Accepted |
| [0012](0012-source-existence-and-identity-have-one-owner.md) | Source existence and identity are owned by one registry; adapters are directed, named, and removable | Accepted |
| [0013](0013-source-types-that-exist.md) | Only source types that work ship; Kotlin-to-JS transpilation and the JS scraper runtime are removed | Accepted |
| [0014](0014-failures-name-the-layer-that-owns-them.md) | A failure names the layer that owns it; the adapter seam is where a provider's shape becomes ours | Accepted |
| [0015](0015-one-source-type-until-another-works.md) | The heuristic source type is removed; extension APKs are the only working source until Jellyfin | Accepted |
| [0016](0016-content-locator-is-the-only-address.md) | A `ContentLocator` is the only address the reader and downloader accept, and callers cannot decompose it | Accepted |
| [0017](0017-delete-the-unreachable-profile-path.md) | The unreachable profile path is deleted rather than kept as a Jellyfin landing pad | Accepted |

## ADR protocol

Each ADR must contain:

```text
Status: Accepted | Proposed | Superseded
Date:
Decision:
Context:
Consequences:
Rejected alternatives:
Evidence required:
Supersedes / superseded by:
```

To reverse a decision, create a new ADR. Do not edit history in place.
