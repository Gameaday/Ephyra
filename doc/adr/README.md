# Architecture Decision Records

These records describe decisions that shape multiple Ephyra 2.0 phases. They are intentionally short and evidence-oriented.

## Accepted decisions

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-one-owner-per-truth.md) | One authoritative owner for every state and resource | Accepted |
| [0002](0002-single-navigation-owner.md) | One main navigation owner; reader Activity is explicit | Accepted |
| [0003](0003-document-viewport-zoom.md) | Continuous zoom uses a virtualized document viewport | Accepted |
| [0004](0004-media-pipeline-and-cache.md) | Media separates source identity, decode plans, and render caches | Accepted |
| [0005](0005-feature-boundaries.md) | Features depend on contracts, not implementations or siblings | Accepted |
| [0006](0006-evidence-before-completion.md) | Device behavior requires executable evidence | Accepted |
| [0007](0007-source-is-not-a-ui-adapter.md) | Sources are capability-based platform components; legacy extensions are temporary adapters | Accepted |

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
