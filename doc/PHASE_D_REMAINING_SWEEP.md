# Phase D Working Doc — Remaining Sweep & Cross-Doc Reconciliation

> **Handoff note**: live progress log for Phase D of the audit. If interrupted, resume from
> **Next steps** at the bottom.

**Status: ✅ COMPLETE (2026-09-10), pending commit/push.**

## What Phase D covered

Phase D was the "everything else" sweep — the items that didn't fit Phases A/B/C. The bulk of
it turned out to be **verification + doc reconciliation** rather than new code, because the
underlying work was already done; the plan documents were simply stale.

## Survey results (the good news)

| Check | Result |
|---|---|
| `core/domain` → `ephyra.data.*` imports | **0** (CI gate passes) |
| `feature/*` → `ephyra.data.*` imports | **0** (CI baseline = 0) |
| ViewModels injecting Repository directly | **0** — all 52 ViewModels use `@HiltViewModel` + constructor-injected Interactors |
| `StateScreenModel` / `screenModelScope` remaining | **0** — ScreenModel→ViewModel conversion complete |
| Glance widget pre-caching worker | **Code complete** — `WidgetUpdatesJob` (WorkManager) + `BaseUpdatesGridGlanceWidget` Hilt `@EntryPoint` |

## Delivered

| Item | File | Notes |
|---|---|---|
| okhttp-zstd CI pin guard | `.github/workflows/build.yml` | New "Dependency fitness – okhttp-zstd pinned to okhttp_version" step; fails build if `okhttp-zstd` drifts from `okhttp_version` (the bug that caused the original Mangabat `NoClassDefFoundError`) |
| Cross-doc reconciliation | `MIGRATION_PLAN.md`, `COMPLIANCE_CHECKLIST.md`, `PROJECT_TO_BE_PLAN.md` | Checked off completed items, fixed stale numbers (baseline 41→0), added zstd guard to checklist |

## Doc reconciliation details

- `MIGRATION_PLAN.md` Phase 4: checked off ScreenModel audit, Repository→Interactor breakdown,
  backup/restore porting, and the `feature → ephyra.data.*` ratchet (7→0).
- `MIGRATION_PLAN.md` Phase 6: already updated in Phase C.
- `MIGRATION_PLAN.md` Phase 12: checked off the okhttp-zstd regression guard.
- `MIGRATION_PLAN.md` Phase 14: already updated in Phase B/C.
- `COMPLIANCE_CHECKLIST.md`: fixed `ephyra.data.*` baseline 41→0; added zstd guard row.
- `PROJECT_TO_BE_PLAN.md` Build 3: checked off the Glance widget item (code was already done;
  only this doc lagged).

## Intentionally deferred (not code; measurement / device work)

- **Global-search latency measurement** — requires running the app, not automatable from the
  workspace. Left as a manual step before tuning `SearchViewModel` parallelism/timeout.
- **End-to-end runtime smoke sweep** (browse→search→library→reader→downloads→backup) —
  requires a running app/device; documented in `doc/VALIDATION_CRITERIA.md` as a manual
  pre-release gate.
- **On-device extension testing** — deferred by user until phases are pushed to git.

## Validation

- `:core:data:testDebugUnitTest` — ✅ (57 tests, all green incl. 3 new migration tests)
- `:app:compileDebugKotlin` — ✅ (AppModule wiring)
- `assembleDebug` + `:source-api:testDebugUnitTest` + `:core:domain:testDebugUnitTest` + `spotlessCheck` — ✅ (see pc_final.log run)

## Next steps (resume here)

1. ☐ Commit + push Phase D.
2. ☐ Manual: global-search latency measurement on device.
3. ☐ Manual: on-device extension testing (Mangabat/MangaDex) once phases pushed.
4. ☐ Manual: end-to-end smoke sweep before release.
