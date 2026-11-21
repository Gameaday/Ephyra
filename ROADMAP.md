
# Ephyra Roadmap

> **Baseline:** `fc853de03` · **Verified:** 2026-11-20 · **Target:** `2.0-clean`
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

1. **Finish the UI model layer** on library and manga (history is done).
2. **Design‑system split** in `presentation-core`.
3. **Re‑theme** — needs device verification.

### Coverage — measured, not assumed

Test ratios measured per module, splitting Compose UI from logic, because a raw
`test LOC / total LOC` ratio counts UI as untested code and reads far worse than reality:

| module | logic | ui | tests | logic cov | raw cov |
|---|---|---|---|---|---|
| history | 628 | 1,217 | 533 | 0.85 | 0.29 |
| updates | 581 | 1,563 | 508 | 0.87 | 0.24 |
| browse | 3,808 | 10,151 | 2,916 | 0.77 | 0.21 |
| library | 791 | 3,893 | 603 | 0.76 | 0.13 |
| settings | 1,557 | 7,798 | 1,039 | 0.67 | 0.11 |
| reader | 16,618 | 10,921 | 10,197 | 0.61 | 0.37 |
| manga | 1,765 | 10,866 | 1,009 | 0.57 | 0.08 |

Every module's logic is covered between 0.57x and 0.87x. `feature:manga` is the
lowest at 0.57, but it is **not** the 0.08 a raw ratio suggests — 10,866 of its
11,622 lines are Compose UI. **Do not open a coverage effort on the strength of the
raw column.** The earlier audit that recommended exactly that was measuring UI.

### Architecture quarantine — the extension ABI

Extensions are a hard requirement, so `eu.kanade.tachiyomi.*` stays. The defect is not that
it exists; it is that **domain code is written against it**.

**Do not try to move 29 files into a bridge module.** Measured: the files that import the ABI
are not adapters. They are `SyncChaptersWithSource`, `FindContentSource`, `MigrateMangaUseCase`,
`DownloadManager`, `SourceManager` and the rest of the source/download layer. Moving them would
put domain logic in a bridge module, which is backwards.

The real fix is to **replace the ABI type in each domain API**, one type at a time:

| Type | Domain replacement | Scope | Note |
|---|---|---|---|
| `UpdateStrategy` | `domain.manga.model.UpdateStrategy` | ✅ done (`cd3d4207c`) | 2 values, ordinal‑compatible |
| `Page` | own immutable page model | **39 files, 275 refs** | mutable, holds `android.net.Uri` + `MutableStateFlow`; `ChapterCache` JSON‑persists it |
| `SManga` / `SChapter` | `Manga` / `Chapter` | ~8 files | mappers already exist |
| `Source` / `CatalogueSource` / `HttpSource` | `SourceGateway` | ~11 files | the modern contract already exists |
| `FilterList`, network types | domain equivalents | ~4 files | |

`Page` is the worst offender and the highest‑value target: it is a **mutable, Android‑coupled
source DTO** used by the domain's download and cache layers, while a well‑designed sealed
`domain.content.model.ContentPage` sits unused beside it. Replacing it also removes shared
mutable state from the download path.

**What to keep:** `source-api` stays pristine for upstream merges. `androidx.preference` and
`injekt` are part of the ABI (`PreferenceScreen` is a typealias; `HttpSource.network` is
`by injectLazy()`) — do not remove them.

Blocked on device: nav ViewModel retention across tab switches, tall‑page sharpness,
back‑motion feel, page‑load parity vs MiHon, cache exhaustion over a long series.

### Open question: `popUpTo(0)` in `HomeScreen` (needs a device)

`HomeScreen` clears the tab stack at three sites with `popUpTo(0) { inclusive = true }`.
`0` is not a destination id anywhere in this graph (`DeepLinkScreen` correctly uses
`ScreenRoutes.Home.route`). In Navigation Compose an unmatched id pops **nothing**, which
would mean the stack is never cleared and `LibraryViewModel` is recreated on every tab
switch — re-running the five‑way `combine` and re‑showing the spinner.

`TabNavControllerLifetimeTest` asserts `popUpTo(0)` as *required*, so this cannot be changed
on inspection alone: the assertion encodes the owner's two defect reports, and `popUpTo(0)`
may be behaving as intended for a reason not visible from the source. Confirm on a device
whether the back stack is actually empty on return to a tab root before touching it.
