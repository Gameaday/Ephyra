# Accepted-Debt Register

> **Status:** CURRENT. The consolidated record of everything this programme has consciously deferred
> **by decision** — each entry with the condition that must be met before it is retired.
> **Authority:** [`REBUILD_STATUS.md`](REBUILD_STATUS.md) remains the ledger; this register is a view
> over it, not a second authority. Where the two disagree, the ledger wins and the disagreement is a
> documentation defect to fix in the same commit it is found.
> **Created:** 2026-11-08 (E2 — [`2_0_COMPLETION_PLAN.md`](2_0_COMPLETION_PLAN.md) Track E).

## The rule

An item belongs in this register only if a recorded owner decision deferred it **and** the decision
names a review or expiry condition — the ledger's `ACCEPTED_DEBT` vocabulary: *consciously deferred
with expiry/review condition*. Three neighbouring classes are recorded separately below so that
completeness is not mistaken for debt: standing channel blockers (§3), decision-governed contracts
and recorded findings (§4), and decisions not yet made (§5). An entry without a condition is a wish,
not debt; it does not belong here.

## 1. Accepted debt — deferred by owner decision

| # | Item | What is deferred | Decided | Review / expiry condition | Governing record |
|---|---|---|---|---|---|
| 1 | `B-017` tracked nightly keystore | Rotation of the published nightly signing identity. The literal password is removed and gated (`SigningSecretTest`); a published key cannot be unpublished, so the residual is the key itself. | 2026-09-28, owner | The post-programme rotation project runs. **Phase 1 may not record `VERIFIED` until it does** — a scheduled gap, not an oversight. Rotation is owner-side because it invalidates the update signature of every existing nightly install, a user-visible consequence to schedule deliberately. | `REBUILD_STATUS.md` `B-017` row |
| 2 | Target-data cluster isolation | Production cutover of `TargetSeriesRepository`, `TargetMigrationWriter`, `LegacyRoomMigrationAdapter`, `ReconcileSourceRegistry`, `verifyDownloadArtifact` — nothing bound, nothing deleted. | 2026-11-08 (D3) | The 14 cutover gates are evidenced (`DATA-001I`); `DATA-001H`'s rollback boundary forbids target writes from production UI/background workers until then. `verifyDownloadArtifact` is re-scoped to blocker 2 (`DownloadArtifactIndex` has no implementation in any module). | [`TARGET_DATA_CUTOVER.md`](TARGET_DATA_CUTOVER.md); D3 row |
| 3 | `SRC-007` global-search product wiring | `GlobalSearchViewModel` stays on the legacy execution path; the coordinator is not wired into the search front door. | 2026-11-08 (B6) | The `SourceContentItem`→product mapping is approved (R-004) and R-008's native-coverage preconditions are authorized. | [`SOURCE_REMOVAL_PLAN.md`](SOURCE_REMOVAL_PLAN.md) R-004/R-008; `SRC-007` row |
| 4 | `SRC-009` search-command handlers | The OPEN_DETAILS / ADD_TO_LIBRARY / PERSIST handlers stay unwired; the command factory is complete and tested. | 2026-11-08 (B6) | The same mapping approval; PERSIST additionally requires the `DATA-001I` gates — it would cross `DATA-001H`'s rollback boundary. | `SRC-009` row |
| 5 | `SRC-010` target persistence cutover | The tested target persistence cluster (`TargetSeriesRepository`, `TargetBackupMapper`, `TargetSeriesLinkRepository`) stays isolated from production. | 2026-11-08 (B6/D3) | The `DATA-001H`/`DATA-001I` gates. | `SRC-010` row |
| 6 | `OpportunisticMergeManager` dead class | The class file stays, dead, its DI wiring removed; `ADR-0017` deleted its path and deliberately left the class. | 2026-11-06 (D1) | The separate cleanup decision D1 recorded is actually made — wire it or delete it. Until then it is the one piece of D1 residue with no owner. | D1 row; `ADR-0017` |

## 2. Coverage reductions — accepted with the loss stated

| # | Item | What was given up | Decided | Review / expiry condition | Governing record |
|---|---|---|---|---|---|
| 7 | Frame-deadline automation (`B-043`/`B-051`) | The frame-deadline budget has **no automated gate**; it is measured by manual `E4-user` capture. The `macrobenchmark` module is retired (D2, 2026-11-07). | 2026-09-27, owner | A device-bound performance channel exists (C6 is owner-side; `baseline-prof.txt` is hand-maintained until a device harness replaces it). | [`PERFORMANCE_BUDGETS.md`](PERFORMANCE_BUDGETS.md); `B-043`/`B-051` rows |
| 8 | `E4-user` device evidence | `E4-user` capture is outstanding for every fixed defect row — owner decision; not the same thing as `E4-lab`, which `ADR-0009` accepts as a channel. | owner decision | The release/validation process produces `E4-user` captures per the runbook. | [`E4_ACCEPTANCE.md`](E4_ACCEPTANCE.md); `ADR-0009`; ROADMAP "Current truth" |

## 3. Standing channel blockers — not debt, recorded for completeness

| Item | The standing fact | Retires when |
|---|---|---|
| `B-050` CI emulator | The API 35 emulator does not boot on GitHub runners, so device runs are local — and publish paths deliberately never depend on connected instrumentation (E1's `ReleaseGateTest` holds that absence). | A bootable runner image or rooted/physical-hardware channel exists. |

## 4. Decision-governed contracts and recorded findings — not debt

- **`RendererSelection`/`RendererDispatch`** — `ADR-0008`'s contract-first exception: the
  four-content-type plan's dispatch seam, refusal semantics included. Wiring arrives with that
  plan, not before it. A decision, not deferred work.
- **`IngestEngine`** — acknowledged greenfield, not an orphan and not debt.
- **`B-055` recorded-not-fixed findings** — recorded with reasons in the `B-055` row and owned by
  their contract records; partially superseded since (the `DecodePlanner` surface is wired by A1;
  `verifyDownloadArtifact` is re-scoped by D3). They carry no review condition, so they are not
  registered as debt; their disposition belongs to the media-pipeline and download contracts, not
  to a deferral decision.

## 5. Open decisions — excluded, not debt

- **`DATA-001` chapter-number type (B4)** — widen the source model or keep the tolerant policy;
  owned by its plan row.
- **O2 unified progress model / `RFC-0001`** — needs its RFC before C2's multi-provider bindings.

These are decisions not yet made. Recording them as debt would launder "undecided" into "accepted";
when each is decided, its outcome lands wherever the decision says — in this register only if it is
a deferral with a condition.

## Maintenance

Append-oriented, like the ledger. Update this register in the same commit as the decision that
creates or retires an entry. A retired entry is never deleted silently: it moves to §6 with the
retiring decision and date. A new entry requires the deciding record to name the review or expiry
condition — an entry without one is rejected by this file's own rule.

## 6. Retired entries

_None yet. The first retirement lands here with its date and the decision that retired it._
