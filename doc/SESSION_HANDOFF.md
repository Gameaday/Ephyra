# Session Handoff — prompt for a fresh session

> Paste this whole file as the first message of a new session. It is self-contained:
> state, strategy, roadmap, mindset, and the traps that cost us time.

---

## 1. Role and mindset

You are the sole engineer on **Ephyra**, a hard fork of Mihon (Android manga reader).
The owner is the only user. We are **dog-fooding**: breaking changes are fine, and
stability is not a goal until the owner says so. There is no external user base to
protect and no release deadline.

**How to work here:**

- **Verify before you claim.** Every "it works" must be backed by a command you
  actually ran *at the exact commit*. This is the single most important rule. Past
  sessions shipped three defects that reached `main` because a green run predated
  the final state.
- **`spotlessCheck`, not `spotlessApply`.** CI runs check. They are different tasks
  and can disagree — `apply` has silently fixed my formatting while `check` still
  failed on a ktlint rule.
- **Falsify every new test.** Break the code on purpose, watch the test fail with a
  useful message, then restore. A test I have not seen fail is not evidence.
- **Prefer deleting to commenting.** If code needs a paragraph, the design is wrong.
  If an invariant must never regress, write a test, not a comment.
- **Never fix a failure you cannot reproduce.** Speculative "fixes" were shipped and
  reverted this session. Diagnosis first; the fix must be provable.
- **Correct the record when you were wrong.** The owner values this over looking
  consistent. Say what you got wrong, what the evidence was, and what changed.

**Tools that have burned us:**
- PowerShell `Set-Content` / `Get-Content | Set-Content` rewrites line endings
  (repo is LF; CRLF caused whole-file spotless diffs) and mangles non-ASCII
  (UTF‑8 → CP1252). It has corrupted files four times.
- Use the `editor` tool for structural edits. Use Python for anything
  encoding-sensitive. Never pipe a regex result into `Set-Content` on a Kotlin file.
- `Resolve-Path` fails on a file that does not exist yet — use an absolute path.
- Commit messages via a file (`git commit -F`), ASCII only. Inline `-m` breaks on
  apostrophes and emits a BOM on PowerShell 5.1.
- **Kill Gradle daemons with `./gradlew --stop` before killing processes.**
  Mid-build kills leave file locks (`bundleLibRuntimeToJarDebug ... being used by
  another process`) that cost four build cycles.
- **A local gate can report green without running** (`UP-TO-DATE`). Use
  `--rerun-tasks` when you must trust a result.

## 2. Where the project is

`main` == `origin/main` == **`04c63a35`** at the time of writing (verify with
`git rev-parse --short=8 main`). Tree clean.

**Architecture:** single‑activity Compose, Hilt DI, Room, multi‑module.

```
app ──► feature:* ──► core:{domain,data,common,download,archive,metadata}
                  └──► presentation-core ──► core:common
```

- `:app` (195 KLOC prod) — shell, navigation, DI, startup
- `feature/{library,manga,reader,settings,browse,updates,history,stats,…}`
- `core/domain` — **17 KLOC prod / 10 KLOC test (0.61 ratio)**. The cleanest module.
- `core/data` — Room, mappers, Coil integration, backup/restore
- `core/common` — prefs, network, image utils
- `presentation-core` — shared Compose components + theme
- `source-api` / `source-local` — **vendored Mihon extension ABI, keep pristine**

**Extension policy (owner decision):** extensions are a **hard requirement**. Only
the **latest** extension versions matter; no backwards compatibility needed. That
means we can be *more* aggressive than upstream Mihon, which targets old devices.
We are `minSdk 36` / `targetSdk 37` — extremely modern by design, and that is the
point: drop compatibility, use the newest platform APIs.

**Platform‑floor opportunities (minSdk 36 means these are dead code):**
`SDK_INT >= S` (31), `>= TIRAMISU` (33), `>= UPSIDE_DOWN_CAKE` (34),
`>= VANILLA_ICE_CREAM` (35) can all collapse to their unconditional branch.

## 3. What is done (all merged, all green)

- **CI**: `--max-workers=2` + `forkEvery = 1` on Robolectric modules fixed the
  `feature:reader` `junit‑vintage` worker‑death. A failure‑only diagnostic step names
  the cause if it recurs. Confirmed green remotely on `eea32fbdb`.
- **UDF**: all 49 production ViewModels take `@IoDispatcher CoroutineDispatcher` and
  launch on it. Debt 10 → 0. The gate enforces **three** properties: no
  `Dispatchers.IO`, dispatcher actually **used** (not decorative), and a test
  **injects** one.
- **Reader**: tall paged pages region‑decode into GPU‑safe slices; a single
  `PagePixelSurface` renderer serves every mode; pixel‑exact round‑trip test.
- **ABI decoupling**: `UpdateStrategy`, `Page`→`PageState`/`ReaderPage`, `CachedPage`.
  The reader no longer extends `eu.kanade.tachiyomi.source.model.Page`.
- **Chapter numbers**: `Double` end‑to‑end; the float tolerance is deleted. Backup
  format widened (breaking).
- **Theme**: packages consolidated to `ephyra.presentation.core.theme`.
- **presentation‑core deps**: nine `api()` → two `implementation()`; six modules now
  declare what they use instead of inheriting it.
- **Covers**: interned by `mangaId`; both ends use `asMangaCover()` so Coil keys
  agree; no crossfade on a shared element at *either* end; the memory cache is no
  longer halved at benign `TRIM_MEMORY_RUNNING_LOW` — that was the reload‑on‑back bug.
- **Library**: stale‑while‑revalidate snapshot (no spinner on return); the debounce
  no longer delays first paint; 9 new tests.
- **Scrub**: `collectLatest` so a new target cancels the in‑flight animation;
  adjacent‑only animation; thumbnail bubble from resident bytes, off by default.
- **Architecture gates**: feature isolation now covers all 16 packages (was vacuous);
  a new test pins declared edges == actual edges; the unwired ceiling is documented.
- **Docs**: 1.9 MB → 393 KB. As‑is summary atop the ledger; completed plans archived.

## 4. Roadmap — in priority order

### P0 — Finish the ABI quarantine (`core:domain` must not import `eu.kanade.tachiyomi.*`)

**Do not** move the 29 importing files into a bridge. I audited this: most are
`SyncChaptersWithSource`, `FindContentSource`, `MigrateMangaUseCase`,
`DownloadManager`, `SourceManager`. Moving them would relocate domain logic into an
adapter and invert the dependency the quarantine exists to create.

Replace the ABI **type** in each domain API instead, one type at a time:

| Type | Status | Notes |
|---|---|---|
| `UpdateStrategy` | ✅ | domain‑owned enum |
| `Page` → `PageState`/`ReaderPage` | ✅ | |
| `CachedPage` | ✅ | |
| `SManga` / `SChapter` | ⬜ ~8 files | mappers already exist |
| `Source` / `CatalogueSource` / `HttpSource` | ⬜ ~11 files | `SourceGateway` already exists |
| `FilterList`, network helpers | ⬜ ~4 files | |

Then add the **import gate**: no module except `:source-bridge` may import
`eu.kanade.tachiyomi.*`. Model it on `ArchitectureTest`'s `DECLARED_FEATURE_EDGES`.

### P1 — Coverage where a re‑theme will cut
`feature:manga` 11.6 KLOC / 1 K test; `feature:library` 4 KLOC / 603 test. But
**measure logic vs UI first** — I once reported "0.09 coverage" for manga when its
*logic* ratio was 0.57; the rest is Compose. Never trust a raw `test LOC ÷ total LOC`.

### P2 — Re‑theme
Unblocked: theme packages consolidated, `EphyraTheme` is the single entry point.
The remaining coupling is **composables taking domain models** (98 UI files import
`ephyra.domain`). Do the UI model layer first — `HistoryUiItem` is the proof it works
(zero domain params left). Then predictive back / `SharedTransitionScope` covers.

### P3 — `core:data` capability split
Largest and riskiest. The audit's own note: it needs P2's module map.

### Needs a device — cannot be verified in CI
- Tall‑page rendering sharpness
- Nav back‑motion feel (reported "not smooth, not 60–90fps")
- Page‑load parity vs Mihon
- Cache/memory exhaustion over a long series
- Whether the scrub thumbnail looks right
- **"Pages stop loading, needs app restart"** — I traced this to the worker lifecycle
  and **disproved** two hypotheses: the queue‑removal race (identity `equals`, so a
  re‑queued entry can't be removed) and worker death (`internalLoadPage` catches
  `Throwable` and rethrows only `CancellationException`). Cause still unknown. Next
  suspects: `ChapterLoadingCoordinator` prefetch boundary, `ReaderSessionCoordinator`.
  **Ask for a logcat filtered on `HttpPageLoader`/`ChapterLoadingCoordinator`/`ReaderSession`.**

## 5. Commands

```powershell
# The standard pre-commit gate — what CI runs
./gradlew spotlessCheck testDebugUnitTest --max-workers=2

# One module (faster feedback; use --max-workers=1 for Robolectric modules)
./gradlew :feature:reader:spotlessCheck :feature:reader:testDebugUnitTest --max-workers=1

# One test class
./gradlew :app:testDebugUnitTest --tests "*InjectableViewModelDispatcherTest*" --max-workers=1
```

**Always `--max-workers=1` for a single module** — parallel Robolectric workers
contend for the same sandbox resources and produce spurious failures.

**Kill daemons properly before killing processes:**
```
./gradlew --stop
```
Then `Stop-Process`. Mid-build kills leave file locks
(`bundleLibRuntimeToJarDebug ... being used by another process`).

## 6. Gates (all in `app/src/test/java/ephyra/app/architecture/`)

| Gate | What it enforces |
|---|---|
| `ArchitectureTest` | feature isolation (16 packages); no `ephyra.presentation.theme` outside theme |
| `ModuleDependencyGraphTest` | declared edges == actual edges; hyphenated modules resolve |
| `InjectableViewModelDispatcherTest` | 6 tests: no IO, dispatcher used, test injects one, allowlist matches |
| `SourceEncodingGateTest` | no CP1252‑mangled UTF‑8 in `*.kt` |
| `LedgerWiringClaimTest` | cited files exist; cited line numbers are in range |
| `ReaderPreferenceConsumptionTest` | every reader pref offered in settings is read |
| `UnwiredTargetComponentTest` | reachable‑component ceiling (currently **85**) |
| `ReaderPageResourceOwnershipTest` | `ReaderScreen` must use `currentReaderPage`, not `pages[currentPage]` |
| `LibrarySeriesTransitionTest` | cover keying, shared‑element shape, motion policy |

**`UnwiredTargetComponentTest`'s ceiling is a proxy.** It measures reachability from
outside target areas, so any type used only within `feature/reader` is invisible.
Raising the ceiling was correct; do not delete live types to lower it.

## 7. Weaknesses to carry forward

- **My PowerShell editing has caused four file corruptions.** Use `editor`.
- **The gates read source text, not behaviour.** They prove a shape. A ViewModel can
  declare a dispatcher and ignore it — the gate now checks this, but only textually.
- **`REBUILD_STATUS.md` encoding defect.** Lines 2, 7, 20 carry a double‑encoded UTF‑8
  BOM (`ï»¿`) and CP1252‑mangled arrows. **Do not attempt a scripted repair** — six
  attempts failed and two made it worse. It needs `iconv`, or a rebuild from a
  pre‑corruption commit. Cosmetic; the ledger renders acceptably.
- **Local‑dev gotcha:** after adding a Hilt `@Module`, a stale KSP cache can silently
  drop its `@IntoSet` contribution until a clean build.
- **`MangaCoverKeyer` doesn't key on the resolved URL**, so a changed thumbnail URL
  can serve a stale bitmap.
- **Dead SDK checks** at `minSdk 36`: six `SDK_INT >=` guards can collapse.

## 8. Conventions

- Package roots: `ephyra.<module>.<subpackage>`. No exceptions — gated.
- KDoc: one line where a non-obvious purpose needs naming. **No prose blocks.** Git
  history holds the archaeology.
- Comments cite *why*, never *when* or *what changed* or *previously*
- Tests: JUnit 5 (`org.junit.jupiter.api`), MockK, `StandardTestDispatcher` with
  `advanceUntilIdle()`. Robolectric 4.18 for anything touching Android.
- Falsify every new test. Say when a claim is compile‑verified vs behaviour‑verified.
- Commit: `type(scope): imperative summary`; body explains *why*; ASCII only; via
  `git commit -F <file>`.

## 9. First actions for a new session

1. `git rev-parse --short=8 main` and `git status --short` — confirm the starting point.
2. `./gradlew spotlessCheck testDebugUnitTest --max-workers=2` — establish a green baseline.
3. Check the remote CI run on `main` — the last unverified signal.
4. Then pick up **P0** (`SManga`/`SChapter` quarantine) or **P1** (manga/library coverage).