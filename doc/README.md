# Documentation Index

> **Status:** CURRENT. This is the map of the documentation set. If a document is not listed here as
> current, treat it as historical. Authority order is defined by
> [`DOCUMENTATION_GOVERNANCE.md`](DOCUMENTATION_GOVERNANCE.md).

The programme entry point is [`../ROADMAP.md`](../ROADMAP.md). The forward plan to the next baseline
is [`2_0_COMPLETION_PLAN.md`](2_0_COMPLETION_PLAN.md).

## Authority order (highest first)

1. [`../ROADMAP.md`](../ROADMAP.md) — scope, phase order, non-negotiable rules.
2. [`REBUILD_PROGRAM.md`](REBUILD_PROGRAM.md) — normative architecture and delivery rules.
3. [`REBUILD_EXECUTION_GUIDE.md`](REBUILD_EXECUTION_GUIDE.md) — how to execute a task.
4. [`REBUILD_STATUS.md`](REBUILD_STATUS.md) — evidence, task state, phase gates.
5. [`adr/`](adr/) — accepted architectural decisions.
6. Subsystem contracts (`*CONTRACT.md`, `CACHE_POLICY.md`, `SOURCE_ROADMAP.md`, …).
7. This index and everything else — context only.

## Current documents

| Document | Purpose | Owner / update rule |
|---|---|---|
| [`2_0_COMPLETION_PLAN.md`](2_0_COMPLETION_PLAN.md) | Ordered steps to the `2.0-clean` baseline. | Update when a step lands or the state changes. |
| [`2_0_CLEANUP_AUDIT.md`](2_0_CLEANUP_AUDIT.md) | Reconciliation of merged work against the current tree. | Update when an item is cleaned up. |
| [`2_0_CONTENT_MODEL_PLAN.md`](2_0_CONTENT_MODEL_PLAN.md) | Subordinate 10-step plan for content model / profile path. | Delete when its last step lands. |
| [`REBUILD_PROGRAM.md`](REBUILD_PROGRAM.md) | Phased architecture and delivery rules. | Change only with an ADR. |
| [`REBUILD_EXECUTION_GUIDE.md`](REBUILD_EXECUTION_GUIDE.md) | Operating procedure for humans and agents. | Update when procedure changes. |
| [`REBUILD_STATUS.md`](REBUILD_STATUS.md) | Evidence ledger, task state, phase gates. | Append in the same commit as the work. |
| [`USER_STORIES.md`](USER_STORIES.md) | Intent register; each story names its test. | Update when a story's evidence changes. |
| [`adr/`](adr/) | Accepted decisions that must not be silently reversed. | Add a new ADR; never edit history. |
| [`rfcs/RFC-0001-content-identity-unification.md`](rfcs/RFC-0001-content-identity-unification.md) | Work/Binding identity proposal (Draft). | Promote to an ADR when accepted. |
| [`READER_ARCHITECTURE.md`](READER_ARCHITECTURE.md) | Reader session/state/resource contract. | Update with reader architecture changes. |
| [`READER_GESTURE_CONTRACT.md`](READER_GESTURE_CONTRACT.md) | Pointer state machine and gesture ownership. | Update with gesture changes. |
| [`DOCUMENT_VIEWPORT_CONTRACT.md`](DOCUMENT_VIEWPORT_CONTRACT.md) | Document coordinates and zoom ownership. | Zoom-ownership current; tile half superseded by ADR-0010. |
| [`MEDIA_PIPELINE_CONTRACT.md`](MEDIA_PIPELINE_CONTRACT.md) | Source identity, decode plans, render caches. | Update with media-pipeline changes. |
| [`DOWNLOAD_ARTIFACT_CONTRACT.md`](DOWNLOAD_ARTIFACT_CONTRACT.md) | Download artifact identity and verification. | Update with download changes. |
| [`NAVIGATION_CONTRACT.md`](NAVIGATION_CONTRACT.md) | One coordinator, typed routes, back behavior. | Update with navigation changes. |
| [`MOTION_NAVIGATION_CONTRACT.md`](MOTION_NAVIGATION_CONTRACT.md) | Route-pair motion and predictive back. | Update with motion changes. |
| [`CACHE_POLICY.md`](CACHE_POLICY.md) | Cache invariants (supersede via ADR). | Update with cache changes. |
| [`cache-retention-policy.md`](cache-retention-policy.md) | Cache retention rules. | Update with retention changes. |
| [`SOURCE_ROADMAP.md`](SOURCE_ROADMAP.md) | **Sourcing authority**: current/compat/planned. | Supersedes sourcing sections of other docs. |
| [`SOURCE_DISCOVERY_ARCHITECTURE.md`](SOURCE_DISCOVERY_ARCHITECTURE.md) | Source/search/ranking/discovery contract. | Subordinate to `SOURCE_ROADMAP.md`. |
| [`SOURCE_DISCOVERY_EXECUTION.md`](SOURCE_DISCOVERY_EXECUTION.md) | Source/search implementation order. | Subordinate to `SOURCE_ROADMAP.md`. |
| [`source/`](source/) | `SRC-000` inventory, call graph, compatibility, removal plan. | Update with source work. |
| [`EXTENSION_COMPATIBILITY.md`](EXTENSION_COMPATIBILITY.md) | Extension ABI boundary and `Page.imageUrl` opacity rule. | Referenced by tests; keep current. |
| [`TARGET_DATA_SCHEMA.md`](TARGET_DATA_SCHEMA.md) | Target Room schema and migration boundary. | Update with target schema. |
| [`TARGET_DATA_CUTOVER.md`](TARGET_DATA_CUTOVER.md) | Cutover plan to the target store. | Update with cutover progress. |
| [`TARGET_DATA_PARITY.md`](TARGET_DATA_PARITY.md) | Legacy-to-target parity matrix. | Update with parity progress. |
| [`FIXTURE_MANIFEST.md`](FIXTURE_MANIFEST.md) | Fixture schema and evidence levels. | Update when fixtures change. |
| [`E4_ACCEPTANCE.md`](E4_ACCEPTANCE.md) | Device-evidence runbook and artifact format. | Update with evidence policy. |
| [`BUILD_HEALTH.md`](BUILD_HEALTH.md) | Health gates, timing budgets, device availability. | Update with measured numbers. |
| [`DEPENDENCY_TARGET_GRAPH.md`](DEPENDENCY_TARGET_GRAPH.md) | Allowed/forbidden dependency flow. | Change only with an ADR. |
| [`PERFORMANCE_BUDGETS.md`](PERFORMANCE_BUDGETS.md) | Resource and frame targets. | Update with budget changes. |
| [`DOCUMENTATION_GOVERNANCE.md`](DOCUMENTATION_GOVERNANCE.md) | Document states and change protocol. | Update with governance changes. |
| [`phase2-search-v1-spec.md`](phase2-search-v1-spec.md) | Unified search v1 acceptance spec (Stages B-E). | Delete when search v1 ships. |
| [`evidence/`](evidence/) | Retained device-evidence records. | Append-only. |

## Historical

[`historical/`](historical/) holds completed improvement-session and audit records. They are
retained for archaeology and are **not** current guidance. Each carries a `HISTORICAL` banner.
They are archived rather than deleted because they name the reasoning behind fixes that are still in
the tree; Git history alone would lose the index.

## Adding or retiring a document

Follow the change protocol in [`DOCUMENTATION_GOVERNANCE.md`](DOCUMENTATION_GOVERNANCE.md):
update the current contract, update this index and the ledger, fix inbound links, then archive or
delete in a separate explicit commit.
