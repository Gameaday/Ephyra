# Documentation Index

> **Status:** CURRENT. Map of the doc set. Anything not listed here does not exist.
> Authority order: [`DOCUMENTATION_GOVERNANCE.md`](DOCUMENTATION_GOVERNANCE.md).
> Program entry point: [`../ROADMAP.md`](../ROADMAP.md).

## Authority order (highest first)

1. [`../ROADMAP.md`](../ROADMAP.md) — scope, phase order, non-negotiables
2. [`REBUILD_PROGRAM.md`](REBUILD_PROGRAM.md) — architecture and delivery rules
3. [`REBUILD_EXECUTION_GUIDE.md`](REBUILD_EXECUTION_GUIDE.md) — how to execute a task
4. [`REBUILD_STATUS.md`](REBUILD_STATUS.md) — evidence, task state, phase gates
5. [`adr/`](adr/) — accepted decisions; never edit history, add an ADR
6. Contracts below — binding rules
7. This index — context only

## Read in this order

**As-is (how it works today)** — `REBUILD_PROGRAM.md`, `adr/`, then the contracts:
[`NAVIGATION_CONTRACT`](NAVIGATION_CONTRACT.md) ·
[`MOTION_NAVIGATION_CONTRACT`](MOTION_NAVIGATION_CONTRACT.md) ·
[`READER_ARCHITECTURE`](READER_ARCHITECTURE.md) ·
[`READER_GESTURE_CONTRACT`](READER_GESTURE_CONTRACT.md) ·
[`DOCUMENT_VIEWPORT_CONTRACT`](DOCUMENT_VIEWPORT_CONTRACT.md) ·
[`MEDIA_PIPELINE_CONTRACT`](MEDIA_PIPELINE_CONTRACT.md) ·
[`DOWNLOAD_ARTIFACT_CONTRACT`](DOWNLOAD_ARTIFACT_CONTRACT.md) ·
[`SOURCE_DISCOVERY_ARCHITECTURE`](SOURCE_DISCOVERY_ARCHITECTURE.md) ·
[`cache-retention-policy`](cache-retention-policy.md) ·
[`EXTENSION_COMPATIBILITY`](EXTENSION_COMPATIBILITY.md) ·
[`phase2-search-v1-spec`](phase2-search-v1-spec.md)

**In flight** — [`REBUILD_STATUS.md`](REBUILD_STATUS.md) (read its As-is summary first;
if a ledger row disagrees with it, the summary wins), [`source/`](source/).

**Forward** — [`../ROADMAP.md`](../ROADMAP.md),
[`UPSTREAM_MIHON_0_21_1_ADOPTION_PLAN`](UPSTREAM_MIHON_0_21_1_ADOPTION_PLAN.md),
[`rfcs/`](rfcs/).

**Reference** — [`BUILD_HEALTH.md`](BUILD_HEALTH.md) health ratchets ·
[`E4_ACCEPTANCE.md`](E4_ACCEPTANCE.md) device-evidence runbook ·
[`evidence/`](evidence/) captured screenshots ·
[`source/`](source/) inventory, call graph, compatibility matrix, removal plan.

## Rules

- One authority per truth; where docs disagree, higher rank wins and the loser is a defect.
- Contracts change only with an ADR.
- A doc not listed here is not current.
