# Structural Audit and Modernization Options

**Status:** AS-IS assessment, 2026-11-19. Measurements taken from the tree at `7252c537b`.
**Purpose:** enumerate the *next* major efforts — organizational, modular-replacement, and UI-overhaul —
with the evidence for each, so a decision can be made from measurements rather than impressions.

This is a decision input, not a plan of record. It does not replace `REBUILD_STATUS.md` (task ledger)
or `2_0_COMPLETION_PLAN.md` (forward plan); it supplies the reasoning those documents would need if
any of these efforts is chosen.

---

## 1. Where the code actually is

Module sizes (`src/main`, Kotlin+Java), measured:

| Module | LOC | Files | Module | LOC | Files |
|---|---|---|---|---|---|
| `core:data` | 17,484 | 188 | `core:domain` | 17,134 | 271 |
| `feature:reader` | 16,592 | 102 | `app` | 11,773 | 86 |
| `presentation-core` | 12,630 | 148 | `feature:manga` | 11,629 | 50 |
| `feature:browse` | 10,981 | 72 | `feature:settings` | 10,055 | 64 |
| `core:common` | 6,208 | 74 | `feature:library` | 4,065 | 24 |
| `source-api` | 3,448 | 35 | `core:download` | 3,335 | 11 |
| `feature:migration` | 2,198 | 13 | `feature:history` | 1,238 | 7 |
| `feature:more` | 1,197 | 15 | `feature:upcoming` | 833 | 13 |
| `feature:webview` | 695 | 7 | `feature:updates` | 1,389 | 8 |
| `feature:stats` | 520 | 8 | `core:archive` | 416 | 6 |
| `presentation-widget` | 513 | 10 | `feature:download` | 639 | 3 |
| `source-local` | 962 | 9 | `feature:category` | 947 | 12 |
| `feature:player` | 246 | 5 | `feature:security` | 51 | 1 |

Test-to-production ratio (a proxy for how safely each area can be refactored):

| Well covered | Ratio | Thinly covered | Ratio |
|---|---|---|---|
| `feature:reader` | 10,170 / 16,592 | `feature:updates` | 241 / 1,389 |
| `core:domain` | 10,420 / 17,134 | `feature:settings` | 1,039 / 10,055 |
| `core:data` | 5,125 / 17,484 | `feature:manga` | 1,009 / 11,629 |
| `core:common` | 2,575 / 6,208 | `feature:library` | 578 / 4,065 |
| `feature:browse` | 2,916 / 10,981 | `feature:category` | 165 / 947 |

**The asymmetry is the single most important fact for planning.** `feature:reader` and `core:domain`
have more test code than most modules have production code, so they can be refactored with
confidence. `feature:settings` (10k LOC, 1k test) and `feature:manga` (11.6k LOC, 1k test) are the
largest *under-tested* surfaces — and they are exactly the two most user-visible.

---

## 2. Organizational / structural issues found

These are measured, not stylistic. Each is small, self-contained, and independently shippable.

### 2.1 `feature:manga` has a split personality

42 of its 50 files declare `package ephyra.feature.manga`; **8 still declare `ephyra.presentation.manga`**:

```
feature/manga/src/main/kotlin/ephyra/presentation/manga/   (8 files, wrong root)
feature/manga/src/main/kotlin/ephyra/feature/manga/        (42 files, correct)
```

Nothing enforces this. A file can be added under either root and compile fine, because both are on
the same source set. `ArchitectureTest` asserts cross-feature *edges*, not package roots, so this
drift is invisible to the gates.

**Why it matters beyond tidiness:** `ephyra.presentation.manga` reads as if it were part of
`presentation-core`. Any tooling that groups by package — a UI audit, a dependency report, an IDE
refactor — will silently misattribute 8 files.

**Effort:** one commit, mechanical, `ArchitectureTest` gains a new assertion that every file in
`feature/X` declares `ephyra.feature.X`. **No test exists today that would catch this recurring.**

### 2.2 `theme` lives in two packages

```
presentation-core/.../presentation/core/theme/    5 files  BrandedThemeConfig, Color, MotionTokens, Shapes, Typography
presentation-core/.../presentation/theme/         1 file   EphyraTheme.kt
```

`EphyraTheme.kt` sits one package level *above* the tokens it composes, at the `presentation` root
rather than `presentation.core.theme`. Ten feature files import the odd one
(`import ephyra.presentation.theme.*`).

This matters more than a naming nit because `EphyraTheme` is the single entry point a UI overhaul
would rewrite. Having it outside the token package means "where do I change the theme?" has two
answers.

**Effort:** one commit; move `EphyraTheme.kt` into `presentation.core.theme`, update 10 imports.

### 2.3 Literal colours outside the theme package

Audited: **22 occurrences across 12 files** outside `theme/colorscheme/`. Verified they are not a
false positive from the colour-scheme files (those hold ~700 literals and are *correctly* placed).

| File | Count | Assessment |
|---|---|---|
| `feature/library/.../LibraryBadges.kt` | 6 | real — badge semantics, should be tokens |
| `feature/manga/.../MangaInfoHeader.kt` | 6 | real — header affordances |
| `presentation-core/.../icons/{Github,Discord,Reddit,Facebook,X}.kt` | 5 | **correct** — brand marks keep exact colours |
| `feature/library/.../CommonMangaItem.kt` | 1 | real |
| `feature/manga/.../CommonMangaGridItem.kt` | 1 | real |
| `feature/manga/.../CoverSearchDialog.kt` | 1 | real |
| `feature/manga/.../TrackInfoDialogHome.kt` | 1 | real |
| `feature/reader/.../ReaderImagePainters.kt` | 1 | **correct** — bitmap compositing, not UI theming |

So: **10 genuine violations**, all in library/manga badges and headers. Small, but they are the exact
files a re-theme would have to hunt through.

**Effort:** one commit. Add a gate that allows `theme/colorscheme/**` and `icons/**` and fails on
`Color(0x...)` elsewhere — cheap because the exception list is small and stable.

### 2.4 `MainActivity.kt` is 738 lines and `HomeScreen.kt` is 530

The two largest `app`-module files, and both are UI-host code: activity lifecycle, nav setup, and
theme bootstrapping in one class. For a UI overhaul these are the files touched most and understood
least.

**Effort:** moderate (needs care — activity lifecycle is subtle). Splitting into a `MainActivity` +
`AppNavHost` + `ThemingHost` triple is mechanical once the seams are named, and `app` has no test
module at all today, so this should land with the first `app`-level test.

### 2.5 `presentation-core` is a grab-bag, not a design system

Its 148 files break down as:

| Package | Files | What it is |
|---|---|---|
| `components` | 34 (+17 `material`, +1 `adaptive`) | the design system — good |
| `util` | 22 (+`lang`, `manga`, `system`, `view`) | **not design system** — utilities |
| `ui` | 8 (+`activity`, `delegate`, `navigation`, `viewer`) | **not design system** — app plumbing |
| `theme` | 5 | tokens — good |
| `icons` | 6 | brand marks |
| `screens`, `feature`, `crash`, `preference`, `i18n`, `data/coil` | 8 | misc |

Roughly **40 of 148 files are neither components nor tokens** — they are `util`, `ui/activity`,
`ui/delegate`, and `data/coil`. Those are *app-shell* concerns that happen to live in the module
every feature depends on.

That is the structural obstacle to a clean UI overhaul: you cannot separate "the design system" from
"the app plumbing" because they are the same module. It is not a large refactor to fix — the files
are already in named subpackages — but it must happen *before* a re-theme, or the re-theme drags
`ui/delegate` along with it.

---

## 3. The UI-overhaul question

The question posed: *where could a complete UI overhaul fit, once UI is completely separated from
everything else?* The audit says: **UI is not separated, and the gap is measurable.**

### 3.1 The measurement

**98 UI files import `ephyra.domain` directly**, totalling **817 domain imports across 241 distinct
symbols**. Distribution by domain area:

| Domain area | Imports in UI | Domain area | Imports in UI |
|---|---|---|---|
| `manga` | 175 | `category` | 45 |
| `reader` | 128 | `content` | 34 |
| `source` | 76 | `download` | 33 |
| `track` | 74 | `extension` | 27 |
| `library` | 68 | `ui` | 26 |
| `chapter` | 51 | `extensionrepo` | 17 |

And concretely: composables in `feature/library`, `feature/manga`, `feature/history`,
`feature:browse` take **domain models as parameters** — `Manga`, `Chapter`, `HistoryRecord` —
not UI models. `LibraryScreen`, `MangaScreen`, `HistoryItem`, `LibraryList`, `CommonMangaItem`,
`BaseMangaListItem` and ~15 others.

**What this means:** the UI is separated *by convention* (ViewModels own state, `ScreenEvent` types
carry intents — 22 of them), but not *by type*. A composable that accepts `Manga` is coupled to the
domain schema, so a domain change is a UI change and a re-theme must be done file-by-file rather
than by swapping a component library.

### 3.2 What "separated" would require

The standard fix is a **UI model layer**: one `*UiModel`/`*Item` per screen, mapped from domain in
the ViewModel, consumed by composables. Ephyra has *partial* versions of this
(`UpdatesUiItem.kt`, `MangaChapterListItem`), which proves the pattern is understood — it is just
applied to a minority of screens.

Estimated scope from the measurement: **~98 files**, of which the composables taking domain models
are the ~25 that actually need conversion (the rest import a domain type for a single field or a
constant). This is the highest-leverage structural change available, and it is the *prerequisite*
for a UI overhaul rather than a competitor to it.

### 3.3 Sequencing

| Phase | Work | Why this order |
|---|---|---|
| 1 | UI model layer on the **most-used** screens (`library`, `manga`, `history`) | largest domain coupling, best test coverage in `library`/`manga` tests |
| 2 | Design-system extraction: `components`+`theme` split from `util`+`ui` in `presentation-core` | makes the theme replaceable as a unit |
| 3 | Re-theme against the new seam | only now is it a *swap*, not a rewrite |
| 4 | `browse`, `settings`, `upcoming`, `updates`, `more` conversions | lower traffic, thinner tests |

Phase 1+2 is the real work. Phase 3 is what everyone *thinks* the UI overhaul is, and it is the
cheap part **if and only if** 1 and 2 are done.

---

## 4. Modular replacement / improvement candidates

Ranked by (impact × tractability) ÷ risk, using the measurements above.

### 4.1 `core:data` (17,484 LOC, 188 files) — the largest surface, thinnest ratio

At 17.5k LOC this is bigger than any feature and has a 29% test ratio. It is also the module
everything depends on. It is *not* one thing: it holds repositories, mappers, network clients,
caches, and source-specific parsing.

**Candidate:** split by capability rather than by layer — `data:source`, `data:chapter`,
`data:library`, `data:track`, `data:cache`. The package structure may already imply the seams.

**Why it's attractive:** it is the single biggest contributor to build time and the module most
likely to be blamed for "where does X live?" confusion.

**Why it's risky:** it is the most-depended-on module. Any split must be additive first (create the
new modules, move code, re-point `app`) and the dependency graph gate
(`ModuleDependencyGraphTest`) must be updated in lockstep.

**Recommendation:** do *not* start here. Do 4.2 first, because it produces the map this needs.

### 4.2 `feature:settings` (10,055 LOC, 64 files, 1,039 test) — the best value/risk ratio

Largest under-tested feature. Settings screens are naturally independent (each `Settings*Screen.kt`
is a leaf), they are low-traffic, and they are the classic "UI overhaul" target because they are
mostly forms.

**Candidate:** split into `settings:appearance`, `settings:reader`, `settings:download`,
`settings:general` — or, more cheaply, keep one module but enforce the screen-per-file boundary
with a gate and add tests as each screen is touched.

**Why first:** it is the only large module where a mistake is cheap (settings has no data
correctness risk) and where the payoff is visible (it is the module a re-theme touches most).

### 4.3 `feature:reader` (16,592 LOC, 10,170 test) — already modular, genuinely improvable

The best-tested module in the app, and it shows: `viewer/render`, `viewer/pager`, `viewer/webtoon`,
`loader`, `model`, `session` are already separated, and the render seam was unified this cycle
(`PagePixelSurface`).

**Candidate:** the remaining work is *within* `viewer/webtoon` (10 files) and `viewer/pager` (7) —
the two hosts that still own mode-specific gesture/zoom policy. The unified-renderer doc
(`doc/UNIFIED_RENDERING_PIPELINE.md`) already specifies the target: one `PagePixelSurface`, with
modes differing only in container and transform.

**Why it's attractive:** highest test coverage, clearest specification, and it is the feature users
judge the app on.

**Why it's not first:** it needs device QA to validate (pixel identity is proven; gesture feel is
not).

### 4.4 `presentation-core` split (see §2.5)

Prerequisite for §3. Moderate effort, high leverage.

### 4.5 `core:download` (3,335 LOC, 100 test) — smallest, sharpest seam

Small enough to rewrite rather than refactor. If any module is a candidate for *replacement*
(delete and reimplement) rather than restructuring, this is it — and it has a contract document
(`doc/DOWNLOAD_ARTIFACT_CONTRACT.md`) to rewrite against.

### 4.6 Updates does not stack the way History does (owner request, 2026-11-16)

**History already solves this.** `HistoryUiModel` is a sealed interface with
`Header` / `Item` / `Series(latest, older)`; the ViewModel groups by `mangaId`, and
`withExpandedSeriesChildren` lifts an expanded series' children into real lazy items so they
animate and keep identity. Reading 100 chapters of one series produces **one row plus a toggle**.

**Updates has the grouping but only uses it for actions.** `UpdatesViewModel` calls
`groupBy { it.update.mangaId }` in exactly two places — `downloadChapters` (line 331) and
`deleteChapters` (line 351). The *display* list is flat: `UpdatesUiModel.Item` per chapter, one row
each. So 50 updates from one series is 50 rows.

**Why it matters beyond scrolling:** each row is a `MangaCover.Square` request, so a stacked series
would also cut cover requests 50 → 1 (see §4.7).

**Effort:** low. The pattern is already written and tested in `feature:history` (which has 463 test
LOC). Port `Series(latest, older)` + the expand toggle + `withExpandedSeriesChildren` into
`UpdatesUiModel`, and group in `UpdatesViewModel`'s state mapping. The actions already group
correctly, so selection/delete/download semantics should be preserved by keying selection on
`mangaId`+`chapterId` rather than list position.

### 4.7 The same series' cover is requested once per row, not once per series (owner request, 2026-11-16)

`MangaCover` is a 5-field data class (`mangaId`, `sourceId`, `isMangaFavorite`, `url`,
`lastModified`), built per row by `asMangaCover()`. Both `HistoryItem` (line 54) and
`UpdatesUiItem` (line 171) pass `history.coverData` / `update.coverData` into `MangaCover.Book` /
`MangaCover.Square`, which does `remember(data, isSharedElement) { ImageRequest... }`.

**The consequence:** a series with 50 updates constructs **50 equal-but-distinct `MangaCover`
instances**, so:
- 50 `remember` blocks each build their own `ImageRequest`;
- 50 Coil requests are issued (Coil's memory cache dedupes the *result*, but the requests are still
  in flight and the key includes decoded size, so a different aspect ratio on another screen is a
  genuine second decode of the same bytes);
- `data` differs by **identity** across rows even when it is equal by value.

**Fix:** intern `coverData` by `mangaId` in the ViewModel — one `MangaCover` instance per series,
shared by reference across every row of that series. Then `remember(data, ...)` hits for all of them,
one request is in flight, and a long updates/history list stops re-decoding the same cover.

**Why this is the right level:** interning in the *ViewModel* (not in Coil) keeps the fix explicit
and testable — a `coverDataByMangaId` map, asserted by a test that two rows of one series receive
the *same instance* (`assertSame`). A Coil-level cache already exists and is not the problem; the
problem is the N duplicate requests and N `remember` allocations upstream of it.

**Effort:** low. Touches the two ViewModels' state mapping. Pairs naturally with §4.6, since
stacking a series already collapses its rows to one.

---

## 5. Recommendation

Do these in order. Each is independently shippable and each one makes the next cheaper.

| # | Effort | Size | Why now |
|---|---|---|---|
| 1 | **Package-root gate + fix** `feature:manga` (§2.1), `theme` (§2.2) | ~1 day | cheapest real win; adds the gate that stops recurrence |
| 2 | **Literal-colour gate + fix** (§2.3) | ~0.5 day | 10 violations, small stable exception list |
| 3 | **Cover interning by `mangaId`** (§4.7) | ~0.5 day | owner request; one instance per series, kills N duplicate cover requests |
| 4 | **Updates stacking like History** (§4.6) | ~2 days | owner request; pattern already written and tested in `feature:history` |
| 5 | **`feature:settings` decomposition + tests** (§4.2) | ~1 week | best value/risk; builds the pattern for the rest |
| 6 | **UI model layer** on library/manga/history (§3.2) | ~1–2 weeks | the actual prerequisite for a UI overhaul |
| 7 | **`presentation-core` design-system split** (§2.5) | ~1 week | makes the theme swappable as a unit |
| 8 | **Re-theme** (§3.3 phase 3) | ~1 week | the cheap part, once 6+7 land |
| 9 | **`core:data` capability split** (§4.1) | ~2 weeks | biggest payoff, highest risk — needs 5's map |

**The headline:** the app's architecture is in good shape where it has tests (`core:domain`,
`feature:reader`) and weakest exactly where it is least tested (`feature:settings`,
`feature:manga`, `core:data`). The highest-value next effort is not a new abstraction — it is
**raising coverage on the modules a UI overhaul would touch**, because that is what makes every
later change safe.

**Two of these are owner-requested and are cheap** (3 and 4): both reuse a pattern the codebase
already has, and both are user-visible immediately. They are deliberately placed ahead of the
structural work because they pay back in a day rather than a week, and because #4 *reduces* the
surface #3 has to cover.

---

## 6. What this audit does *not* claim

- **No performance measurement.** No build-time, startup-time, or frame-time profiling was done.
  `doc/PERFORMANCE_BUDGETS.md` exists but was not re-measured.
- **The LOC figures are raw line counts**, not complexity. A 200-line file of pure data classes is
  not harder to change than a 100-line file with deep nesting.
- **The 98-file domain-coupling figure counts imports**, not *usage*. Some imports may be a single
  constant reference; the actual conversion count is likely lower (est. ~25 composables).
- **Effort estimates are order-of-magnitude**, calibrated against this session's work (a 49-ViewModel
  mechanical refactor with gate updates took ~6 commits).
- **Device-dependent work is excluded by design** — nav motion, render feel, and cache behaviour
  under real memory pressure cannot be assessed from source.

```
presentation-core/.../presentation/core/theme/    5 files  BrandedThemeConfig, Color, MotionTokens, Shapes, Typography
presentation-core/.../presentation/theme/         1 file   EphyraTheme.kt
```

`EphyraTheme.kt` sits one package level *above* the tokens it composes, at the `presentation` root
rather than `presentation.core.theme`. Ten feature files import the odd one
(`import ephyra.presentation.theme.*`).

This matters more than a naming nit because `EphyraTheme` is the single entry point a UI overhaul
would rewrite. Having it outside the token package means "where do I change the theme?" has two
answers.

**Effort:** one commit; move `EphyraTheme.kt` into `presentation.core.theme`, update 10 imports.