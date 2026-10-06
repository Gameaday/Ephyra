# 2.0 Cleanup Audit — merged work vs. current tree

> **Status:** CURRENT. Audit of code merged into `main` that was authored *before* the recent cleanup,
> removal, and refactor passes, and therefore needs reconciling with the project's current state.
> **Companion to:** [`2_0_COMPLETION_PLAN.md`](2_0_COMPLETION_PLAN.md) (steps D1–D5 are the deletion
> track; this file lists the reconciliation items those steps must absorb).

## Why this file exists

Branches written weeks apart get merged into a tree that has since moved. The merge is textually
clean — GitHub reports no conflict — but the *meaning* can be stale: a component that was the target
when written may now be superseded, or newly unwired, or duplicating something that landed later.
This is the audit of that gap for the work merged on 2026-10-05.

## Method

Every claim below was checked by reading the tree (`git diff` of the merge against its first parent,
plus `grep` for consumers in `src/main`). No claim is taken from a PR description.

## Findings

### CU-1 — `ReaderSessionCoordinator` / `ReaderSessionIdentity` are built but unwired

- **Evidence.** Both files exist in `feature/reader/src/main/kotlin/ephyra/feature/reader/session/`
  and are referenced **only by themselves and their tests** — no production consumer.
- **Impact.** This is the exact "built but never wired" shape the new
  `UnwiredTargetComponentTest` exists to cap. The session state machine (`ReaderSession`) already had
  no consumer; the merge added a coordinator *around* it that also has none, so the reader-session
  layer grew by two types while the shipping reader is unchanged. It is also why the merge's own
  `ReaderViewModel` note explains that a mechanical swap is *not* safe (arrival is not representable
  in `ReaderSessionState`).
- **Cleanup.** This is plan step **A2**. Either wire the coordinator into `ReaderViewModel` (the
  intended end state) or, if A2 is not imminent, do not leave two unconsumed session types — collapse
  to the pure session until the wiring lands.

### CU-2 — `UnwiredTargetComponentTest` ceiling is loose and unfalsified

- **Evidence.** `MAX_UNWIRED = 99`, "measured 2026-09-30". No falsification record in the ledger.
  The merge itself added unwired types (CU-1) and still passed.
- **Impact.** A ceiling with headroom is a metric, not a ratchet: it will not fail when the backlog
  grows by one or two, which is precisely when a reviewer should be told. The programme's own rule is
  that a gate is not trusted until it is watched to fail.
- **Cleanup.** Falsify it (delete one wiring, confirm red, restore), then **lower the ceiling to the
  measured value** so the next unwired type fails. Record the falsification in the ledger.

### CU-3 — Legacy vocabulary inside the target `session/` area

- **Evidence.** `ReaderSessionCoordinator` imports `eu.kanade.tachiyomi.source.model.Page`.
- **Impact.** The file documents this as the compatibility boundary (entities in, identities out),
  which is a defensible position — but it means the *session* package, a 2.0 target area, carries a
  legacy import. Left unstated it looks like drift rather than a decision.
- **Cleanup.** Decide and record: either keep the translation here with an explicit "compat boundary"
  note (current state, needs to be in the ledger), or move entity→identity translation to the adapter
  so `session/` is legacy-free.

### CU-4 — `PageDecodeWidth` / `PageZoomPolicy` overlap with `ScaleBucket` — reviewed, keep

- **Evidence.** The file argues explicitly why it is *not* a `ScaleBucket` (factor vs. pixel target).
  It is imported and used by `ComposeWebtoonReader`, so it is wired.
- **Impact.** None. Recorded so a future pass does not "deduplicate" it into `ScaleBucket`.
- **Cleanup.** None. Add to the "reviewed, keep" list in the ledger so it is not re-litigated.

### CU-5 — `SourceGateway.homeUrl` / `unmetered` exist to delete legacy reach-throughs

- **Evidence.** Both fields were added with docstrings stating they replace reaching through
  `HttpSource` / `UnmeteredSource`.
- **Impact.** Adding the field does not remove the reach-through; it only enables removal. Until the
  call sites move, both paths exist.
- **Cleanup.** Follow through: delete the legacy reach-throughs at the call sites (ties to **D1** /
  `ADR-0017`). Otherwise these fields are new surface with no removal.

### CU-6 — `SourceRepositoryImpl` / `SourcePagingSource` now bridge gateway → legacy DTO

- **Evidence.** `toLegacyManga()` rebuilds `eu.kanade.tachiyomi.source.model.SManga` from
  `SourceContentItem`; the paging layer still speaks the legacy DTO.
- **Impact.** This is a *temporary* adapter. It is progress (popular no longer downcasts to
  `CatalogueSource`), but it must not become permanent, or the gateway is just a detour around the
  legacy type.
- **Cleanup.** Record the exit condition: delete the bridge when the paging layer consumes
  `SourceContentItem` directly (plan **B6** / `SRC-007`).

### CU-7 — Two reader-session abstractions, no single owner

- **Evidence.** `ReaderSession` (pure reducer) + `ReaderSessionCoordinator` + `ReaderSessionIdentity`
  + the loose fields still in `ReaderViewModel`.
- **Impact.** Four places model "the reader session". The plan's A2 names one migration; the merge
  added a second seam. Without a decision this becomes the ownership conflict the programme exists to
  remove.
- **Cleanup.** Fold CU-1 into A2 and state the single owner in the ledger.

### CU-8 — The merge changed shipping reader behaviour but has no ledger row

- **Evidence.** `ComposeWebtoonReader` render-path decision changed (animation/format as two facts,
  fixing a static-JXL-reported-as-animated bug and a throwing-probe-treated-as-static bug). No
  `REBUILD_STATUS.md` row records it.
- **Impact.** `ADR-0006` requires device behaviour to carry executable evidence; a JVM test is `E2`,
  and this path is a device surface. Unrecorded, the change is invisible to the evidence ledger.
- **Cleanup.** Add a ledger row with its evidence level; schedule `E3` for the slice-path change.

## Summary

| ID | Item | Severity | Cleanup |
|---|---|---|---|
| CU-1 | Session coordinator/identity unwired | High | Wire (A2) or collapse |
| CU-2 | Ratchet ceiling loose + unfalsified | High | Falsify, lower to measured |
| CU-3 | Legacy import in target `session/` | Medium | Decide + record |
| CU-4 | `PageDecodeWidth` vs `ScaleBucket` | None | Record "keep" |
| CU-5 | Gateway fields without call-site removal | Medium | Follow through (D1) |
| CU-6 | Gateway→legacy DTO bridge | Medium | Record exit condition (B6) |
| CU-7 | Multiple session owners | High | Single owner in A2 |
| CU-8 | Behaviour change not in ledger | High | Add ledger row + `E3` |
