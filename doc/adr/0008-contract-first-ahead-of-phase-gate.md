# ADR-0008: Contract-first work may precede its phase gate

- **Status:** Accepted
- **Date:** 2026-09-25
- **Decision:** Pure, unwired contract work is permitted to proceed ahead of a `VERIFIED` phase gate. It earns `CODE_COMPLETE`, never `VERIFIED`, and must be unwired from production until its integration task completes.

## Context

`ROADMAP.md` states a non-negotiable rule: *"No phase may begin implementation until the prior
phase exit criteria are recorded as `VERIFIED` in `doc/REBUILD_STATUS.md`."*

As of 2026-09-25 that rule and the ledger are in direct contradiction. The phase gate table records
`Phase 0 Baseline | NOT_STARTED`, while Phases 3, 4, and 6 carry `CODE_COMPLETE` rows and roughly
1,468 Kotlin files exist in the tree. Phase 0's own constituent tasks (`GOV-001` `VERIFIED`,
`GOV-002`/`GOV-003` `DOC-001` `TST-001A`/`TST-001B` `CODE_COMPLETE`) are substantively complete, so
the `NOT_STARTED` cell is itself inaccurate.

Either the rule is wrong or the ledger is wrong. Both cannot hold, and an agent instructed to
"skip no steps" cannot proceed coherently while they disagree.

## Decision

- The sequencing rule is **retained** and remains binding for anything that changes production
  behaviour, touches user-visible state, or adds a production dependency edge.
- An exception is made for **pure contract work**: domain types, pure policy, and their JVM tests,
  which by construction have no production call sites and no runtime effect.
- Such work is recorded `CODE_COMPLETE` at `E2` and is explicitly marked **not production-wired**
  in its ledger row.
- The gate for *integration* — the adapter step, resource lifecycle, and device evidence — remains
  unsatisfied and is not bypassed by the exception.
- No phase advances to `VERIFIED` until its full exit criteria, including device evidence, are met.

## Consequences

- The ledger's `NOT_STARTED` and `CODE_COMPLETE` cells can be trusted again: a task is either
  unwired pure work or integrated work, and the row says which.
- The ratchet ceilings (`mainSourceFiles`, dependency edges) remain the backstop against contract
  code accumulating without bound, since the exception permits only additive, unwired work.
- The exception is **narrow and self-limiting**. It does not authorise a second active
  architecture; `RDR-004`/`RDR-005` integration is still gated, and non-negotiable rule 7 (no two
  active reader/navigation architectures) continues to bind at integration time.
- The cost is accepted openly: the repository is ahead of its documentation, and this ADR is the
  record of that fact rather than a claim that it did not happen.

## Rejected alternatives

- **Mark Phase 0 `VERIFIED` to make the table consistent.** Rejected: it manufactures a record to
  tidy a document. Phase 0's exit criteria were not independently confirmed, and ADR-0006 forbids
  treating absence of evidence as evidence.
- **Delete or rewrite the "no phase may begin" rule.** Rejected: the rule is sound. The exception
  is narrower than removing it, and removing it would permit unwired contract work on the reader
  or navigation graph to proceed without a gate at all.
- **Revert the unwired contract work.** Rejected: it is additive, tested, and consumed by the
  replacement sequence. Deleting it would destroy verified work to satisfy a documentation rule.
- **Ignore the contradiction.** Rejected: this is the failure mode that makes every other
  governance rule optional.

## Evidence required

- The ledger row for every exception task states "not production-wired" explicitly.
- The `Phase 0` gate cell is corrected from `NOT_STARTED` to its substantively accurate value.
- `HealthRatchetTest` continues to pass, so the exception cannot silently grow into unbounded
  addition.
- This ADR is listed in [`README.md`](README.md) and linked from `ROADMAP.md`.

## Supersedes / superseded by

- Amends the phase-sequencing rule stated in [`../ROADMAP.md`](../ROADMAP.md) § "Phase summary".
  The rule text is retained and unchanged; this ADR defines its one documented exception.