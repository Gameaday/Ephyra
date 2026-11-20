
# Ephyra Roadmap

> **Baseline:** `02fc665b` · **Verified:** 2026-11-20 · **Target:** `2.0-clean`
>
> Single entry point. If another doc disagrees here, this file wins.

## Documents

**Read first**

| Doc | What it is |
|---|---|
| [`doc/README.md`](doc/README.md) | Documentation index and owners |
| [`doc/REBUILD_STATUS.md`](doc/REBUILD_STATUS.md) | As‑is state, task ledger, open defects — start with its As‑is summary |
| [`doc/REBUILD_PROGRAM.md`](doc/REBUILD_PROGRAM.md) | Architecture and phased delivery plan |
| [`doc/REBUILD_EXECUTION_GUIDE.md`](doc/REBUILD_EXECUTION_GUIDE.md) | Operating procedure for humans and agents |
| [`doc/BUILD_HEALTH.md`](doc/BUILD_HEALTH.md) | Health ratchets, timing budgets |
| [`doc/E4_ACCEPTANCE.md`](doc/E4_ACCEPTANCE.md) | Device‑evidence runbook |

**Contracts** — binding rules, not background reading

[`NAVIGATION_CONTRACT`](doc/NAVIGATION_CONTRACT.md) ·
[`MOTION_NAVIGATION_CONTRACT`](doc/MOTION_NAVIGATION_CONTRACT.md) ·
[`READER_ARCHITECTURE`](doc/READER_ARCHITECTURE.md) ·
[`READER_GESTURE_CONTRACT`](doc/READER_GESTURE_CONTRACT.md) ·
[`DOCUMENT_VIEWPORT_CONTRACT`](doc/DOCUMENT_VIEWPORT_CONTRACT.md) ·
[`MEDIA_PIPELINE_CONTRACT`](doc/MEDIA_PIPELINE_CONTRACT.md) ·
[`DOWNLOAD_ARTIFACT_CONTRACT`](doc/DOWNLOAD_ARTIFACT_CONTRACT.md) ·
[`SOURCE_DISCOVERY_ARCHITECTURE`](doc/SOURCE_DISCOVERY_ARCHITECTURE.md) ·
[`cache-retention-policy`](doc/cache-retention-policy.md) ·
[`EXTENSION_COMPATIBILITY`](doc/EXTENSION_COMPATIBILITY.md) ·
[`phase2-search-v1-spec`](doc/phase2-search-v1-spec.md)

**Source program** — [`SOURCE_INVENTORY`](doc/source/SOURCE_INVENTORY.md) ·
[`SOURCE_CALL_GRAPH`](doc/source/SOURCE_CALL_GRAPH.md) ·
[`SOURCE_COMPATIBILITY_MATRIX`](doc/source/SOURCE_COMPATIBILITY_MATRIX.md) ·
[`SOURCE_REMOVAL_PLAN`](doc/source/SOURCE_REMOVAL_PLAN.md) ·
[`UPSTREAM_MIHON_0_21_1_ADOPTION_PLAN`](doc/UPSTREAM_MIHON_0_21_1_ADOPTION_PLAN.md)

**Decisions** — [`doc/adr/`](doc/adr/) 17 accepted ADRs. Do not silently reverse one.

**Governance** — [`DOCUMENTATION_GOVERNANCE`](doc/DOCUMENTATION_GOVERNANCE.md)

## Outcome

A modern Android app where:

- every state has one authoritative owner;
- every setting has an end‑to‑end effect;
- navigation is one graph with typed routes and back behaviour that reads the same everywhere;
- media is decoded at the size it is drawn at, in slices where a page exceeds a GPU texture;
- every rule that matters is enforced by a gate in `app/src/test`, not by prose.

## Current phase

**Structural cleanup, then UI.** Done: CI green, UDF/dispatcher debt 0 with a gate,
encoding corruption repaired and gated, dead code removed, library first‑paint latency fixed,
updates stacked by series, covers interned per series.

Next, in order:

1. **Coverage on `feature:library` and `feature:manga`** — the least‑tested, most‑visible modules.
   Blocks every UI change after it.
2. **Finish the UI model layer** on library and manga (history is done).
3. **Design‑system split** in `presentation-core`.
4. **Re‑theme** — needs device verification.

Blocked on device: nav ViewModel retention across tab switches, tall‑page sharpness,
back‑motion feel, page‑load parity vs MiHon, cache exhaustion over a long series.
