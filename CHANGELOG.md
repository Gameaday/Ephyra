
# Changelog

All notable changes to Ephyra will be documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]
### 🧭 Upstream alignment (plan)
- **Adoption plan for Mihon v0.21.1** added at
  [`doc/UPSTREAM_MIHON_0_21_1_ADOPTION_PLAN.md`](doc/UPSTREAM_MIHON_0_21_1_ADOPTION_PLAN.md). It maps
  each upstream 0.21.1 change (and the open 0.21.0 recap items) to a decision and a workstream. The
  headline: upstream 0.21.1 is the last version to restore Tachiyomi's `.tachibk` backups and to
  support 1.4 extensions. Ephyra already refuses pre-1.6 extensions at load time
  (`ExtensionLoader.SUPPORTED_LIB_VERSIONS = listOf(1.6)`), but still carries the now-unreachable
  1.4/1.5 ABI (RxJava, `fetchImageUrl`/`imageUrlRequest`/`imageUrlParse`). On backups, Ephyra's
  gzip-wrapped `.tachibk` is the **same** container Mihon 0.21.1 writes and reads, so there is no
  interop break today; upstream's replacement format is not yet published, so WS3 is a watch item.
  Planned P0 work: finish the 1.4/1.5 ABI removal, narrow the extension `Injekt` allowlist to
  upstream's set, harden the extension installer's concurrent maps, and survive a WebView renderer
  death. **WS1's ABI removal is a deliberate Ephyra divergence:** upstream `main` still ships the
  `rx.Observable` chain (`fetchImageUrl`/`imageUrlRequest`/`imageUrlParse`), so it is not a mirror of a
  landed upstream change — but the loader refuses anything below 1.6, so no loadable extension can
  reach it. It has since landed; see "Extension API" below.
  Since this plan was written, WS1, WS7 (pager realignment gate), WS8 (locale-safe data
  formatting) and WS10 (obsolete settings) have landed.

### 🧩 Extension API
- **The 1.4/1.5 ABI surface is gone from `source-api` (WS1).** The deprecated RxJava chain that no
  loadable extension can reach — `fetchImageUrl`, `imageUrlRequest`, `imageUrlParse`, the `Observable`
  methods on `Source`/`CatalogueSource`/`HttpSource` and `ParsedHttpSource.imageUrlParse` — was
  removed, together with the `rxjava` dependency (version catalog, `source-api`, `core/common`), the
  `asObservable*` helpers and both `RxCoroutineBridge` files. `HttpSource.getImageUrl` no longer has a
  default: a 1.6 source populates `Page.imageUrl` in `getPageList` or overrides `getImageUrl`, and a
  source that does neither is refused with the diagnostic instead of a fetch of a field that is not an
  address. `SourceCapabilities.customisesImageUrlChain` is now `getImageUrl` alone, and
  `PageImageAddress.of` reads `Page.imageUrl` with no per-builder field selection.
  `HttpSource.getMangaUpdate` became the concrete implementation, so `getMangaDetails`/`getChapterList`
  keep routing an override-only 1.6 source through it. Gated by `SourceApiContractTest` (removed
  members absent from the compiled ABI), `SourceApiBoundaryTest` (no `rx.*`/`Observable` token in
  `source-api/src/main`) and `ExtensionLoaderVersionTest` (`SUPPORTED_LIB_VERSIONS = listOf(1.6)`).

### 🧩 Fixes
- **The library screen no longer crashes on an unrecognised display mode.** A device report surfaced
  `kotlin.NoWhenBranchMatchedException` from
  `ephyra.feature.library.presentation.components.LibraryPagerKt.LibraryPager$lambda$0`
  (`LibraryPager.kt:82`) - the `when (displayMode)` that picks each library page's renderer. Every
  value path in the current tree is provably safe: `LibraryDisplayMode` is a sealed interface whose
  four variants are all matched (and have been in every commit since the pager's creation), its
  `deserialize` maps unknown strings to `default`, and the DataStore-backed preference seeds unset or
  corrupt reads with the default too, so no in-source input can miss a branch. The JVM backend
  nevertheless compiles a sealed `when` without `else` with a defensive fallback throw, and that
  fallback fired on device - the signature of the pager's compiled arms not recognising an instance
  of the same-named hierarchy (the binary-skew case the exception exists for; the skew itself is not
  reproducible from source). The render decision is now total: the `when` names `List` and
  `CompactGrid`/`CoverOnlyGrid` explicitly, and its final arm is an `else` rendering the comfortable
  grid - an unrecognised value degrades to a plain but readable library instead of killing the home
  screen, consistent with the columns gate one branch above, which already treats every non-List
  mode as a grid (the same preference for an imperfect render over a crash as the sort comparator's
  `Random` arm in `LibraryViewModel`). The closed set is pinned by the new `LibraryDisplayModeTest`:
  `values` exposes exactly the four canonical modes (a new variant now fails that test until the
  pager and this entry are consciously updated), `serialize` round-trips every mode, and
  `deserialize` maps corrupt strings to the default - never to nothing.
- **Resuming from another app no longer crashes the reader mid-retry.** The recovery ladder's
  replacement-address fetch — a fresh page list, taken when a page's failure indicted its URL —
  ran unguarded from inside `HttpPageLoader.internalLoadPage`'s `catch` block. Right after the
  app returns from the background, DNS is often not ready again yet, so the image request failed
  with `UnknownHostException` **and so did the replacement fetch** (`Unable to resolve host
  "api.mangadex.org"`), and a throw from a `catch` block escapes the retry loop and its worker
  coroutine (a standalone coroutine with no `CoroutineExceptionHandler`), killing the process —
  `StartupFailureActivity` then presented the crash on the next launch. The fetch now goes
  through `FreshPageAddresses.atOrNull`, the guarded form: a page that cannot get a replacement
  is left address-less and still flagged, and the ladder's own decision still runs — a GIVE_UP
  surfaces the failure that actually stopped the page (the image request, not the replacement
  machinery) and a retry re-asks the source for a fresh list once the network is back.
  `loadPage`'s proactive fetch of the same list was already guarded; the ladder's was the one
  unguarded call site. Guarded by `HttpPageLoaderFreshAddressFailureTest`.
- **Chapters start at the first page again.** The reader addressed content by *item* index — the
  chapter's pages plus a `ChapterTransition.Prev` before them and a `ChapterTransition.Next` after
  them — but derived "start at the beginning" from a fixed offset (`item 1`). That holds only while
  the list is exactly `[Prev, page0, …]`; with a hidden leading page it lands on the second page,
  and before the page list arrives `[Prev, Next]` makes index 1 the *end-of-chapter* card, which is
  how a forward transition could continue straight into the following chapter. Both Compose readers
  now resolve the first displayed page object through `resolveReaderStartIndex` instead. Three
  related leaks are closed: `TargetPage` requests carry their chapter id, so the flow's replayed
  item index can no longer be applied to a different chapter's list (the transition "affecting the
  numbers" when swiping off it); `ChapterTransition` no longer treats `Prev(N, N+1)` and
  `Next(N+1, N)` as equal, which made `items.indexOf` resolve the opposite transition after a
  boundary and broke the equals/hashCode contract; and `ReaderViewModel` only restores a saved page
  index into the chapter that was saved, instead of writing the previous chapter's live progress
  into the next chapter's start position (and clears the page indicator across a chapter boundary so
  a stale "2 / 40" is never shown). Policies and contracts are pinned by
  `ReaderStartPositionTest`, `ChapterStartPolicyTest` and `ChapterTransitionEqualityTest`.
- **Samsung Android 9–11 launch crash guarded** (WS9): `MainActivity.reportFullyDrawn()` now catches
  the `SecurityException` some Samsung builds throw from the platform's fully-drawn broadcast.
  Mirrors upstream `abb126ad`.
- **WebView renderer death no longer kills the app** (WS5): the Compose webview client and the
  Cloudflare interceptor now override `onRenderProcessGone`, log the crash, notify the user and close
  the page; `stopLoading()`/`destroy()` are guarded when the renderer is gone. Mirrors upstream
  `649fc83f`.
- **Extension installs can no longer cancel each other** (WS4): active installs are keyed by package
  name in `ConcurrentHashMap`s and cleaned up by `(key, value)`, so a stale, cancelled or superseded
  install can only remove its own entries. Mirrors upstream `07b9fb69`/`ae749bb4`.
- **“Source not installed” no longer flashes on resume**: `AndroidSourceManager` marked itself
  initialized on the *first* emission of the installed-extension list — the empty list emitted before
  the extension scan finishes — so a screen restored after a process death resolved a placeholder
  stub and rendered it as a missing source. Readiness is now gated on `ExtensionManager.isInitialized`,
  the authoritative extension map is read synchronously at the moment readiness flips, readiness is
  published even if the scan throws, the manager is primed when the app is foregrounded, and the
  browse-source screen stays on its loading state until readiness is known.
- **Locale-safe data formatting** (WS8): chapter-number parsing, `TitleNormalizer`, alternative-title
  dedup, stable-id hex, `dHashToHex`, URL escaping and Kitsu score formatting now pin `Locale.ROOT`,
  so a Turkish or Arabic device locale can no longer change identity keys, dedup results or the ASCII
  of a tracker payload. Guarded by `LocaleIndependenceTest`.
- **Pager realignment locked** (WS7): a structural gate asserts `ComposePagerReader` re-seeks to the
  current item's new index whenever the item list shifts.
- **Obsolete “Custom hardware bitmap threshold” setting retired** (WS10): the preference, its
  advanced-settings row and the option list are removed; the device texture limit is applied directly.- **Source existence and identity have one owner, so an entry stops orphaning the moment a
  URL's spelling changes.** `SRC-011` (`ADR-0012`) closes the mechanism behind `DEF-029`: a new
  `SourceRegistry` is the only reader of the profiled-domains preference, and identity is derived
  once, from the canonical origin (`SourceOriginPolicy`/`SourceIdentityPolicy`) instead of each
  component hashing whatever spelling it happened to see. `DynamicHttpSource`, the Sources list
  and the source map now agree by construction; a spelling change resolves as `SpellingChanged`
  naming the claiming origin instead of an unexplained "source not registered"; health failures
  degrade a registration (`SourceAvailability`) instead of erasing the source from existence; and
  a new ownership gate fails the build if any component opens the preference directly again.

### 📚 Chapter Loading Coordinator (R-A: Stage 3 seam 1 — loading and preload leave the view model)
- **New `ChapterLoadingCoordinator`** (`feature/reader/src/main/kotlin/ephyra/feature/reader/loading/`):
  the view model drives it; it owns the open sequence (session open -> page load -> filter wiring ->
  background queue -> window install -> history -> read timer -> session report — the exact order
  `loadChapter` already ran), the preload eligibility ladder (Loaded/Loading never preload; online
  preload candidates are promoted to `Wait` when their download exists; only Wait/Error attempt),
  the opportunistic next-chapter image prefetch (only while fewer than `preloadAheadPages` pages
  remain pending ahead of the reading position) and the direction-aware adjacent start (next
  begins, previous ends, with the clamp-me fallback index when pages are not yet known).
- **`ReaderViewModel` implements `ChapterLoadingEffectHandler`** (11 members): every override body is
  the code that used to live inline in `loadChapter`/`preload`, moved, not rethought. **No behaviour
  change.** The forward-buffer count previously existed twice — in `preload()` and
  `tryPreloadNextChapterImages()` — and that duplication is the drift risk the reader redesign
  audit's F1/F3 recorded; it now has one home (`pendingAheadOf`). `B-010`'s background-queue call
  site is re-owned by the coordinator (audit F3's named follow-up).
- **Gates:** new `ChapterLoadingCoordinatorTest` (open ordering, no-loader stop, preload
  eligibility, buffer-health deferral, cancellation propagation vs. swallow, and decision tables
  for `shouldPreloadNextChapterImages`/`pendingAheadOf`/`adjacentStartPlan`) and new
  `ChapterLoadingOwnershipTest` (the view model drives the coordinator and consumes its four
  public decisions; the buffer count has one home — no `Page.State.Queue` in the view model;
  every effect the coordinator drives is implemented). Remaining Stage 3 seams
  (`ReaderImageActions`, `ReaderDownloadPolicy`, `ReaderProgressRecorder`, `ViewerModeResolver`)
  stay proposed.

### 🔌 Reader Viewer Host (R-D: Stage 2 — callbacks replace the cast in the rendering path)
- **`ReaderScreen` no longer casts its context to `ReaderActivity`** (audit F6): the cast put the
  concrete Activity class inside the rendering path, and its silent null was load-bearing - with any
  context that is not `ReaderActivity`, both Compose readers silently stop rendering and the empty
  `AndroidView` fallback paints nothing. The screen now takes **`ReaderViewerHost`**, a four-method
  interface (`onPageSelected`, `onPageLongTap`, `toggleMenu`, `requestPreloadChapter`) covering exactly the set
  of callbacks the Compose viewer surfaces invoke; `ReaderActivity` implements it by delegating to the
  same members as before and passes itself at the single production call site. **No viewer behaviour
  change:** every callback lands exactly where it did; the only path that changes is the one where
  the cast returned null and the Compose readers silently did not render - a context no production
  path produces. **Gate:** new `ReaderViewerHostOwnershipTest` (4 rules: no cast to the concrete Activity
  in reader main sources; the screen takes the contract and routes all four callbacks through it; the
  activity implements the contract and passes itself; every interface member is consumed by the
  screen - an unwired contract member fails the build).

### 🖌️ Reader Render Ownership (R-B: Stage 1 — one overlay owner; the dead View paint path is deleted, not migrated)

- **The Compose `ReaderContentOverlay` is the single pixel owner of colour-filter and brightness
  effects.** The Stage 1 inventory (E1 at `0b3dd1bf`) proved the View half of the audit's F4 never
  rendered: every `Viewer.getView()` returns an empty `View(activity)` fallback, so `ReaderActivity`'s
  night-mode grayscale+invert `ColorMatrix` (`applyColorLayerPaint` -> `setLayerType(LAYER_TYPE_HARDWARE,
  paint)`) was applied to a view that draws nothing — dead since the Compose migration, with
  `setLayerPaint` an unused duplicate. Deleted: both paint functions, the six call sites that drove
  them (the `trueColor` collectors x2, the `colorFilter`/`colorFilterValue`/`colorFilterMode`
  collectors and the `updateViewer()` tail) and the now-unused imports. The colour filter renders
  exactly as before; only the dead half is gone.
- **Brightness reaches the overlay reactively, and the window setter honours the documented
  contract.** `ReaderScreen`'s overlay reads `customBrightness()`/`customBrightnessValue()` directly;
  the relayed `State.brightnessOverlayValue` it previously read was fed only by
  `ReaderEvent.SetBrightnessOverlayValue` — an event nobody emitted, so the dim scrim was dead and the
  field stayed `0` (B-032-class unwired contract, with a green unit test proving the relay worked in
  isolation). Event, dispatch, state field and the relay's test are deleted.
  `setCustomBrightnessValue` maps the slider's documented range (-75..100): positive -> `value/100f`,
  0 -> `BRIGHTNESS_OVERRIDE_NONE` (system), negative -> `0f` window minimum while the Compose scrim
  dims — previously negative values were divided straight into `screenBrightness`, an undefined
  negative float.
- **Honest consequences, gated:** `trueColor`/`grayscale`/`invertedColors` are now read by nobody —
  restoring night-mode invert/grayscale as Compose effects is a named owner decision (device-bound,
  unchanged from the audit); the unreachable `AndroidView` fallback branch stays as an interface
  safety net (`Viewer.getView()` retirement is Stage 2/4 scope). New `ReaderRenderOwnershipTest` gates
  four rules: no View-layer paint in reader main sources, the overlay is the sole pixel owner reading
  preferences reactively, the window-brightness three-way contract, and no unwired brightness relay.
  CI-verified (no JDK locally); no device evidence claimed — the restored scrim's look on a panel is
  a device judgement.

### 📖 Reader Modular Redesign Audit (docs-only: the seams are real and gated; the debt is three owners)

- **Audit + staged redesign decision record** added at
  [`doc/READER_MODULAR_REDESIGN_AUDIT.md`](doc/READER_MODULAR_REDESIGN_AUDIT.md). E1 source audit at
  `10f9bd76`, triggered by the owner's "improve the reader and navigation" priority (the navigation half
  was C7 / Motion v2). Verdict: the reader's seams (session, chapter window, slices, zoom, gestures, byte
  budget, load recovery) are real, production-wired and gated; the remaining debt is concentrated in
  `ReaderViewModel` (`B-008` confirmed with measured spans: 1645 lines, 25 constructor dependencies, six
  responsibilities), `ReaderActivity`/`ReaderScreen` (dual color-filter/brightness render paths; the
  Activity is inside the rendering path by cast) and `ReaderPage` (mutable resource carrier, blocking the
  `MED-002` end-state). Staged plan: overlay owner, then callback interface, then per-seam coordinators
  (`ReaderSessionCoordinator` as the template), then the immutable page model deliberately last - each
  stage independently landable; a proposal, not begun work.
- **Ledger corrections, in place:** `B-009` superseded (one container transform; gated by
  `WebtoonZoomRenderTest`), `B-010` harm-bounded by the preload priority ladder (the fact stands), and
  `DOCUMENT_VIEWPORT_CONTRACT.md`'s "remaining wiring in `RDR-005`" sentence corrected (the gesture
  arbiter is production-wired in both viewers). `doc/README.md` gains the new audit's index row and the
  `MOTION_UI_EXPERIENCE_AUDIT.md` row missing since C7.

### 🎬 Motion v2 (C7: one policy, quick cohesion — the undeclared-route fallback became a policy pair)

- **`MotionRoutePair.HIERARCHICAL` closes the `motionPlanFor`-null gap.** The undeclared-route fallback
  — previously hardcoded as direct `m3SharedAxisX*` calls inside `MainActivity`'s NavHost slots — is now
  a declared pair in `MotionPolicy` (300ms forward, 250ms back, shared-axis container), so every
  transition resolves to a plan and the policy's tests govern the fallback too. Disclosed behaviour
  deltas: an undeclared push/pop runs 300/250ms instead of the old 450/350/300 legs, and a cover pair
  with an unreadable id degrades to the policy crossfade (the contract's invalid-element rule) instead
  of a local slide.
- **The shared axis is quicker.** Push: a 300ms slide with a 200ms fade that starts immediately (was a
  450ms slide with a fade delayed 100ms in). Pop enter 250ms, pop exit 200ms. The four
  `shared_axis_x_*.xml` Activity renderings mirror the Compose legs, and `MotionConsistencyTest` now
  asserts the XML↔Compose duration parity — the two layers cannot drift again.
- **The motion vocabulary is pruned and gated.** Deleted with zero call sites: three spring specs,
  three tween presets, `sharedElementContainerDuration`, `DURATION_SHORT_1`/`LONG_1`/`LONG_3`/
  `LONG_4`/`EXTRA_LONG_1`–`3`, two standard easings, and the `MaterialTheme.motion` accessor. The
  alias members stay — they are the live vocabulary of ten consumer files — and the new
  `MotionVocabularyTest` fails the build if any public `MotionTokens` member loses its last consumer.
- **Audit F7's last inline curves name tokens** (behaviour-identical): `ReaderScreen.kt` uses
  `MotionTokens.EasingStandard`, and `CalendarHeader.kt`'s local 200ms constant is
  `MotionTokens.DURATION_SHORT_4`.
- **Direction-aware pop rendering fixes a regression round 1 of this change shipped to CI.** A pop
  resolved the policy's back plan but was rendered with the push functions — the 300ms forward
  slide — where pre-v2 undeclared pops ran the reversed 250ms axis. `MotionTokens.containerPopEnter`/
  `containerPopExit` now render the reversed axis (SHARED_AXIS to the pop legs; the crossfade and
  no-op halves are direction-symmetric), the NavHost pop slots call them, and a new
  `MotionConsistencyTest` gate asserts the policy's HIERARCHICAL durations are the numbers the
  Compose legs and the XML window renderings play.
- **`Motion.kt` is renamed `MotionTokens.kt`** (ktlint `standard:filename`): the file has held a
  single top-level declaration since the `MaterialTheme.motion` accessor was deleted. All path
  references follow; the vocabulary itself is untouched.
- **Honesty: five authoring defects in this PR were caught by its own CI across four red rounds, all
  fixed before merge.**
  (1) a missing final newline; (2) the filename rename above; (3) `MotionVocabularyTest`'s
  member-parsing regexes carried doubled backslashes inside raw strings — a `PatternSyntaxException`
  before its first assertion — and its transitive-liveness rule was inverted ("built from live
  parts" instead of "used by a live member"), which would have false-flagged five duration aliases
  once the crash was fixed; (4) a double blank line the pop-function insertion left behind
  (Spotless, round 4); (5) the new policy-triangle gate's duration regex required `MILLIS\s*=`
  while `MotionPolicy` declares its durations as `: Int =` literals, so it asserted against an
  empty set before its first real check. All disclosed and repaired before merge; nothing merged red.
- **Docs:** `MOTION_NAVIGATION_CONTRACT.md` gains the Motion v2 rules and a repaired, complete Gates
  list (the old list was truncated mid-sentence); `MOTION_UI_EXPERIENCE_AUDIT.md` F4/F7 are current;
  `REBUILD_STATUS.md` and `2_0_COMPLETION_PLAN.md` record the landing (C7 done).

### 📈 Health Re-Measure (E3: the `OPS-002`/`OPS-003` numbers are re-measured at the frozen tree)

- **Repository-shape metrics are current again.** `BUILD_HEALTH.md`'s metrics table was last measured 2026-09-26 and predated D2's `macrobenchmark` retirement. Re-measured at the frozen tree (`de60404`): **module count 28**, **inter-project dependency edges 139** (was 166) — the corrected extractor's semantics replicated over all 30 tracked build scripts, with two exact cross-checks against the gate's own declared-edge sets (12 feature-to-feature, 3 `core:domain`).
- **`OPS-003` green at the frozen tree, evidenced by consumption.** The nightly published `app-*-nightly-de60404.apk` only after `require-green-checks` verified every check on that exact commit — the release-path gate fails closed, so the publish is a consumed green verdict (`REL-001` executing as designed).
- **Nightly per-ABI sizes recorded as publish-channel observations** (arm64-v8a 94.9 MiB … universal 122.8 MiB) — unminified by design (`initWith(release)`, `isMinifyEnabled = false`), explicitly not comparable to the minified release metric.
- **What cannot be honestly re-measured from this channel is recorded as owner-side pending** with each procedure: clean-build 93s re-run and warm Tier B medians (same-host requirement), `git ls-files`-walk file/TODO/`@Deprecated` counts, release APK per ABI, baseline-profile regen, `E4-lab` availability. Docs-only; no production code changed.

### 📒 Accepted-Debt Register (E2: every deferral-by-decision is consolidated with its expiry condition — `B-017` first)

- **One register, one authority.** `doc/ACCEPTED_DEBT_REGISTER.md` consolidates everything consciously deferred by decision, with the review or expiry condition each decision named. `REBUILD_STATUS.md` remains the ledger; where register and ledger disagree, the ledger wins and the disagreement is a defect.
- **`B-017` first** — the tracked nightly keystore: rotation deferred by owner to a post-programme project because it invalidates every existing nightly's update signature; Phase 1 may not record `VERIFIED` until the rotation runs. A scheduled gap, not an oversight.
- **The decision-governed deferrals follow**: the D3 target-data cluster (14 cutover gates, `DATA-001I`), the B6 search-wiring deferrals (R-004 mapping + R-008 for SRC-007/009; `DATA-001I` for SRC-010), and D1's `OpportunisticMergeManager` residue — the one deferral whose deciding decision is still to be made.
- **Coverage reductions are recorded with their losses stated**: the frame-deadline budget has no automated gate (`B-043`/`B-051`; manual `E4-user` capture), and `E4-user` evidence is outstanding for every fixed defect row — owner decisions, both.
- **Not-debt is recorded too, so completeness is not mistaken for debt**: `B-050` (the runner emulator that cannot boot — a standing blocker, deliberately absent from publish paths since E1), ADR-0008's `RendererSelection` seam, `IngestEngine` greenfield, `B-055`'s recorded findings (no review condition, owned by their contracts), and the open decisions (`DATA-001`/B4, O2/`RFC-0001`) — undecided is not accepted.
- **Maintenance is structural**: entries are append-only; a retirement moves to the retired table with its date and the decision that retired it; a new entry without a named condition is rejected by the register's own rule.

### 🚦 Release Gates (E1: `REL-001` — publish paths consume the gate verdict; release artifacts are verified)

- **A tag can no longer ship an unverified commit.** `release.yml` gains a `gates` job that its `build` job needs, so nothing is signed or published until the gate verdict on the exact tagged SHA is green. The verdict is consumed, not re-computed: `WorkflowRedundancyTest` keeps the test suite in exactly one workflow (`build.yml`) precisely so there is a single authority for whether a change is acceptable, and re-running it on the release path would create a second.
- **The mechanism is one composite action**, `.github/actions/require-green-checks`: it polls the shipped SHA's check runs and fails unless every one is completed and green and the named core gates (`Quality Checks`, `Build & Test App`) are present. The calling run's own jobs are excluded, or the nightly would wait on itself; a commit that reports no checks at all fails closed after a bounded grace as never-verified.
- **The nightly is gated identically** (`nightly.yml`): a nightly is an installable publish to a release channel, so an unverified push to `main` no longer ships an APK.
- **Release artifacts are gated after signing, before any publish exists:** `apksigner verify` on every signed APK, `versionName` matched against the tag (`aapt2 dump badging`), and the R8 mapping required and attached to the release — a minified build without its mapping ships blind.
- **Deliberately not faked:** connected instrumentation stays `B-050`-blocked (the API 35 emulator does not boot on GitHub runners) and is never wired into publish; benchmark/screenshot channels stay manual per `B-051`/D2. `ReleaseGateTest` (6) holds the structure: non-bypassable, single-implementation, no emulator gate on publish paths, artifact gates present.
- **Honesty:** no JDK locally; remote CI is the verification channel. The tag-path run executes on the next `v*` tag, which this authoring environment cannot cut — owner-side. The nightly-path gate executes on the merge commit.

### 🔎 Source/Search Wiring Decision (B6: SRC-007/SRC-009/SRC-010 product wiring is deferred by decision)

- **Executing the plan's "finish the product wiring" literally would have broken the app's search.**
  `GlobalSearchCoordinator` runs over `NativeSourceRegistry`, which refuses `LEGACY_COMPATIBILITY`
  gateways by construction (SRC-006) — and `LegacySourceGateway` is exactly that kind, so no extension
  source can enter it. Wiring the coordinator wholesale into `GlobalSearchViewModel`/`UnifiedSearchTab`
  (the app's single search front door) removes every extension source from search; the dual-path
  variant duplicates local/OPDS content as a second search architecture; and driving the legacy fleet
  through `SearchSession` via the bridge makes a compatibility adapter a dependency of a new target
  feature — forbidden by `SOURCE_REMOVAL_PLAN.md` R-004's rollback boundary and R-001's principles.
  R-004 already pins the answer: the ViewModel stays on the legacy execution path until the
  `SourceContentItem`→product mapping is approved and R-008 is authorized.
- **`TargetSearchCommand`'s handlers are gated the same way.** OPEN_DETAILS and ADD_TO_LIBRARY must
  land the item in Room v3 to navigate or favourite (production navigation and library membership are
  keyed on legacy numeric source ids), which needs the unapproved mapping; PERSIST to the target
  `SeriesRepository` would cross the `DATA-001H` rollback boundary ("target writes are not allowed
  from production UI/background workers"). The validated command factory is unchanged — complete and
  tested.
- **`SeriesPersistence`'s "Room/backup implementation remains open" was stale.** The isolated
  implementation exists and is tested (`TargetSeriesRepository`, `TargetBackupMapper`,
  `TargetSeriesLinkRepository` — DATA-001D/E/G); its production cutover is D3's `ACCEPTED_DEBT`
  decision under the `DATA-001I` gates. The SRC-010 row is corrected in place.
- **Decision honesty.** All three items rest as `ACCEPTED_DEBT` with named review conditions (R-004
  mapping approval + R-008 native-coverage preconditions; `DATA-001I` cutover gates) — the "future
  version not yet wired" class, not the `B-032` orphan class. No production code changed, by decision.
  Docs-only; the audit read every file the rows name before classifying it. No JDK locally — remote CI
  is the verification channel.

### ⚡ Startup Split (B5: `OPS-001` executes — one startup state model, `Application` split)

- **One startup vocabulary.** `StartupGuard` and `StartupTracker` are re-keyed on `StartupStep`; `StartupWorkflowHost.settled` is the single reporting seam (an accepted successful settle fans out to both), so `App` reports through the workflow alone. The tracker's parallel `Phase` enum — whose `APP_CREATED` shadowed the workflow step of the same name — is replaced by a typed `Milestone` enum for the six non-workflow events, with ascending timeout budgets pinned by test.
- **Guard no-op defect fixed — behaviour change disclosed.** `completePhase` was `phases[name]?.complete()`: a no-op for a phase nobody registered, and production registered none. Every `App` completion was silently lost, `awaitPhase` passed the startup tests vacuously through its unknown-phase fallback, and the crash handler always read DI as not ready — every crash routed to `StartupFailureActivity` instead of `CrashActivity`. `completePhase` now records register-if-absent, so crash attribution reflects real progress and post-DI crashes route to `CrashActivity`, the designed route.
- **The split, moved verbatim.** `EphyraImageLoaderFactory` (the Coil image pipeline), `ImageCacheMemoryPolicy` (the image-cache trim/restore policy) and `IncognitoModeMonitor` (the incognito notification + receiver surface) leave `App`, which keeps startup coordination, the WorkManager configuration, the migrator, notification channels and the WebView spoofing.
- **Wiring honesty.** Startup tests rewritten and green at `E2` on CI (`StartupTrackerTest` (25), `StartupGuardConcurrencyTest` (6) incl. the register-on-complete regression, `AppStartupTest`/`AppStartupLifecycleTest` typed, `StartupWorkflowHostTest` +3 seam tests). No JDK locally; remote CI is the verification channel; `E3`/`E4` startup timing evidence not produced — not claimed.

### 🖼️ Reader Media Pipeline (A1: the media tuple is wired into the slice renderer)

- **The webtoon slice renderer consumes the media tuple instead of re-deriving its own guard.**
  `ComposeWebtoonReader` already decided *whether* a page is sliceable with `RenderPathPolicy`;
  `SlicedWebtoonImage` then re-derived a `!cropBorders && !isAnimated` conjunction from raw
  booleans — a second, untestable copy of the same decision. The call site now threads
  `PageMetadata`, `PageSource` and the chosen `PageRenderPath` into the composable, and a new
  pure `WebtoonSlicePlanner` turns them into concrete geometry and a decode sample against
  `BitmapRegionDecoder`. Every tuple member is wired: `RenderPathPolicy.sliceSourceRect` and
  `isSliceRectValid` (previously tested but dead) select and validate the rectangle the slices
  tile, so a measured crop rect makes slicing crop-aware instead of crop-forbidden;
  `DecodePlanner` records the whole-page plan (for a long strip it names
  `EXCEEDS_TEXTURE_LIMIT` — the failure slicing exists to avoid); `SampleSizePolicy` hysteresis
  refines the region sample across re-plans, clamped to the width-fit rule so it can never
  decode a strip narrower than the screen; `AnimationPolicy`/`PageMetadata.animation` keep
  animated content on the whole-image path. `WebtoonRegionDecoder.decodeSlices` takes the slice
  rect and the planned `SampleSize` instead of computing its own sample. Production still
  passes `contentRect = null` — `BorderCropTransformation` never exposes its measured insets —
  so every page renders exactly as before; the change is the seam, not the pixels. `E2` is
  covered by the new `WebtoonSlicePlannerTest` (13 tests); the `E3` slice-render obligation is
  `BLOCKED` naming the channel (no emulator in this environment, `B-050`), never claimed.

### 📖 Reader Session Wiring (A2: `ReaderSession` drives the reader, `ReaderChapterWindowPolicy` selects the window)

- **`ReaderViewModel` drives `ReaderSessionCoordinator` (RDR-001).** The session now owns chapter/page
  identity, page index, arrival direction, menu visibility and phase for the wired slice — the
  loose-fields shape that produced `DEF-011` and `DEF-015`. `loadChapter` opens the session before
  the load, `chapterLoaded` reports the resolved pages, the three load-failure catch sites report
  `chapterFailed`, `onPageSelected` records the selection and settles it with `pageLoaded`, and
  `showMenus`/`onActivityFinish` drive `toggleMenu`/`closeChapter`. `sessionState` is exposed.
  `lastSelectedChapterId` and `chapterPageIndex` deliberately stay view-model-local: the session
  cannot express opened-but-nothing-selected-yet, and absorbing that is a `ReaderSession` change,
  not a wiring change.
- **`ReaderSessionEffectLog` acknowledges effects rather than executing them.** Every effect the
  session emits names work the dispatching path already performs — the load is in flight when
  `OpenChapter` is dispatched, progress persists through the same seams, and page bytes are
  bounded by the chapter window ref/unref — so re-issuing would duplicate loads, persists and
  prefetches. The log is the seam the replacement resource owners delegate through when they
  land; the choice is documented in the handler rather than dressed up as execution.
- **`selectPage` can finally record direction (coordinator fix).** The old composite dispatched
  `SelectPage` then `Move`, but `SelectPage` lands in `LoadingPage` and `Move` requires `Ready`,
  so the move was always rejected and no direction was ever recorded. An adjacent selection now
  dispatches the move itself from `Ready`, recording the direction with the page; a jump lands as
  a direction-less `SelectPage`, which is the machine's semantics — completion gating keeps the
  caller's `NavigationVector` for jumps.
- **`ReaderChapterWindowAdapter` selects the prev/next window through the domain
  `ReaderChapterWindowPolicy` (RDR-002).** Candidates are pinned positionally (`SOURCE` order
  reproduces the caller's canonical list order — a chapter-number re-sort would reorder
  scanlator-filled chapters and change behaviour), `skipReadForward` is threaded, DB-unknown
  numbers (`-1.0`) become null candidates rather than policy rejections, and the series-screen
  filters stay upstream in `getChapterList` with `skipFilteredForward` off by the documented
  decision. The feature-local `selectPreviousNavigationItem`/`selectNextNavigationItem` helpers
  are deleted with `ViewerChapters` slimmed to the window type; the contracts of the deleted
  `ChapterNavigationPolicyTest` moved to `ReaderChapterWindowAdapterTest` (6 tests) rather than
  silently disappearing with their subjects, and the coordinator suite gained 4
  direction/recovery tests.
- **Wiring honesty.** `ReaderSessionWiringTest` pins the JVM-reachable seams of the full view
  model: the menu event drives compose state while the closed session's rejection is *reported*
  through the effect log, a page selection before any chapter is open is rejected and reported,
  and activity finish closes the session. The chapter-open lifecycle seams need a real
  `ChapterLoader` and are CI's to run. No JDK locally — remote CI is the verification channel, and
  the `UnwiredTargetComponentTest` ceiling is not re-measured (wiring only lowers the count).

### 🗛️ Startup Workflow (A3: `StartupReducer` is production-wired, `RECONCILE_SOURCES` exists)

- **`App.kt` drives `StartupReducer` through a new `StartupWorkflowHost`.** The 2026-09-28 ledger entry held that `App` is the wrong host if wiring means forcing lifecycle callbacks into dispatch-and-report — the critical phases rethrow so the process dies rather than launching half-initialised. The wiring honours that instead of overriding it: the callbacks remain the effect executor and the workflow becomes the state model. `App.kt` calls `begin()` before the first phase and settles each phase after its existing block; a rethrow path deliberately never settles, so the state dies with the process — nothing gates on this workflow, and `StartupGuard`/`StartupTracker` keep their crash-attribution role (dual tracking disclosed; `OPS-001`/B5 owns unification). The host keeps rule-8 correlation in its own requested-effect set (the terminal `ASYNC_INIT` request is non-cancellable and the `EffectLedger` removes such ids at track time), and its methods are synchronised because the last two steps settle from the async init scope. Every phase settles `ok = true` under its existing containment policy — the `Failed`/`Retry` machinery remains contract capability, demonstrated by test. Production does not persist the snapshot; cold start always begins from `begin()`.
- **`RECONCILE_SOURCES` — the startup step the `B-034` audit found absent — has an implementation.** Added to `StartupStep` between `REACTIVE_BINDINGS` and `ASYNC_INIT` (non-critical, pinned by a new `StartupStepTest` case) and executed by `SourceReconciler` on the **shipping** source authority: it constructs the `SourceManager` (priming the map build at startup rather than at first foreground), awaits `isInitialized` with a bounded 15s timeout, and audits `SourceRegistry` registrations against the loaded catalogue map. Deliberately **not** through `ReconcileSourceRegistry`: its only `SourceLifecycleRepository` implementation is the target-data store `DATA-001H` forbids from production writes, so that DI-orphan stays a decision, not an oversight. The reconciler never throws — a timeout or a disagreement is outcome data, because a non-critical startup step must not be able to abort the launch it audits.
- **Wiring honesty.** `E2` on CI: `StartupWorkflowHostTest` (8), `SourceReconcilerTest` (7), `StartupStepTest` +1. No JDK locally — remote CI is the verification channel, and the branch merges only on green checks. `E3`/`E4` startup timing evidence NOT produced — not claimed.

### 🤏 Continuous Reader Zoom (A4: the webtoon zoom path is hostable — pinch injection at `E2`, cancelled gestures restore)

- **`Modifier.webtoonDocumentTransform` + `WebtoonZoomGestureHost` replace the inline zoom wiring in `ComposeWebtoonReader`.** The Phase-7 entry recorded that inline wiring as having no hostable path for gesture injection, which left the continuous reader's zoom untestable below device level and the `E3` capture impossible to re-drive. The extraction is verbatim — same graphicsLayer, same arbiter/reducer routing, same scroll-owner correction — so behaviour is unchanged by construction except the defect below.
- **Real defect closed: `GestureCancelled` was a production no-op.** The inline call passed `committed = null` to `reduceWebtoonEffect`, so the reducer's cancel-restore branch never ran — an abandoned swipe left the strip at the drifted transform while `WebtoonGestureAdapter`'s contract claimed a cancelled gesture restores the committed one. The host records the commit point on `TransformCommitted` and passes it on every reduce; the restore is pinned by `WebtoonZoomGestureHostTest`.
- **Wiring honesty.** `E2` on CI: `WebtoonZoomInjectionTest` drives a real two-pointer pinch through the production `webtoonGestureStream` modifier under Robolectric (the `B-022` harness) — zoom clamps at 4x, focal corrections reach the list owner, a disabled stream moves nothing, a vertical drag at fit delegates, a horizontal pan while zoomed clamps to `WebtoonZoomPolicy.maxOffsetX`. No JDK locally — remote CI is the verification channel, and the branch merges only on green checks. `E3`/`E4-lab` raster + pinch capture on the extracted path: BLOCKED naming the channel (`B-050` — no emulator/device in this environment), never claimed as pass; the owner device leg is to re-run `WebtoonZoomRenderTest` + pinch capture on an attached device and record it under `doc/evidence/`.

### 🧹 Superseded Reader Paths (A6: verified — nothing left to delete)

- **The RDR-004/RDR-005 cutovers had already deleted their superseded paths at replacement time** (`detectPagerGestures`, `shouldClaimPagerTransform`, `detectWebtoonGestures`, `WebtoonViewportState`/`WebtoonViewport` per `ADR-0010`) — rule 7 (never two live paths) was applied when the replacements landed, not deferred. A6's deliverable was the proof.
- **`UnwiredTargetComponentTest` ran at ceiling 0 to enumerate every unwired 2.0 target type (83), and the reader-area entries were audited per file**: all are live (referenced from Compose-only production files the scan cannot see — `ZoomPolicy`, the `PagerViewport` family, the A1 media tuple and slice pipeline, `WebtoonVisibility`, `WebtoonZoomState`, the gesture-reducer types, `WebtoonZoomGestureHost`), `ADR-0008` contracts awaiting their production call site (`ReaderTapSequencer`, `RendererSelection`), or D3/D4 wire-or-delete decisions (the book-reader/launcher/feature-API cluster). No old-generation reader path ships beside its replacement.
- **The unwired ceiling is tightened 99 → 83** — the falsification `2_0_CLEANUP_AUDIT.md` CU-2 asked for — and the scan's declared-name blind spot is documented in the test's KDoc so the metric is not over-read as a deadness claim.

### 🧹 Project Surface Shrink (D2: `CLEAN-001` executed — the `macrobenchmark` module is retired)

- **The `macrobenchmark` module is deleted** (6 files) together with its `ephyra.benchmark` convention plugin, the `include(":macrobenchmark")` line, and the now-unused `benchmark-macro`/`test-uiautomator` version-catalog entries — the retirement `B-051` recorded as `CLEAN-001` scope. The module was unwired and unreferenced by any workflow; nothing depended on `:macrobenchmark`, so the project edge count is unchanged while the module count drops 29 → 28.
- **The app `benchmark` build type is deleted.** Its only consumer was the retired module, so every build configures one fewer app variant; `ApplicationIdConsistencyTest`'s variant inventory is updated to the four variants that remain.
- **The superseded reader/navigation/media generations are verified gone, not deleted here.** The dependency/import scan (`CLEAN-001`'s evidence gate) found they were already deleted at their cutovers — reader at the A1—A6 cutovers (A6's ceiling-0 inventory + per-file audit), navigation at `NAV-001` (the global mutable navigation singletons) and `ADR-0011`, media at `MED-004`/`ADR-0010`. The ledger passage that said `CLEAN-001` would delete the production Compose readers is corrected in place.
- **The `E5` evidence level and the current docs now name the real measurement channels** (manual frame capture, scripted `adb shell am start -W`) instead of the retired framework, and `baseline-prof.txt` states that its generator is gone and its rules are maintained by hand until a device harness replaces it.

### 🔍 Wiring Audit (D4: the `B-032` surface is closed — misclassifications corrected in place, nothing deleted)

- **`ReaderTapSequencer` and `ReaderGesturePointerAdapter` are production-wired, and never were unwired.**
  Both readers route taps through `detectReaderGestures`: the paged reader via `pagerGestureStream`
  (`PagerViewportAdapter` → `ZoomableMangaPage`), the continuous reader via `webtoonGestureStream`
  (`WebtoonGestureAdapter` → `ComposeWebtoonReader`). The sequencer is the live double-tap deferral
  and the sole producer of `SingleTap`. A6's ledger row called it "still without a call site" — the
  declared-name scan's blind spot, contradicted by the ledger's own `RDR-003` row. Deleting it would
  have broken tap handling in both production readers; it stays, untouched.
- **The "built-ahead entry cluster" is wired, scan-invisibly.** `ReaderModule` binds the viewer
  launchers and `ReaderFeatureApi` `@IntoSet`; `MediaViewerRegistry` dispatches by content type and
  `MangaScreen` calls `mediaViewerRegistry.launch(...)`; `MainActivity` registers every `FeatureApi`
  route (`Screen.BookReader` → `BookReaderViewModel`); `ReaderViewModel` calls
  `resolveChapterStartRequest(...)`. Multibindings and function references are invisible to the
  reachability scan — the same blind spot, second pattern.
- **`RendererSelection`/`RendererDispatch` stay by decision** (`ADR-0008`'s contract-first exception):
  the four-content-type plan's dispatch seam, refusal semantics included; its wiring arrives with
  that plan, not before it.
- **The `B-032` row's status cell is closed.** (i) executed at the cutovers; (ii) D3 accepted debt;
  (iii) both migrations landed (A2 `ReaderSession`; A3 `StartupWorkflowHost`); (iv) A1 media tuple;
  (v) `IngestEngine` greenfield, acknowledged. `READER_GESTURE_CONTRACT.md`'s stale "no production
  call site" passage is corrected in place. Docs-only; no production code changed.

### 📌 Decision Record (D3: the five DI-orphans are unwired by decision — nothing bound, nothing deleted)

- **`TargetSeriesRepository`, `TargetMigrationWriter`, `LegacyRoomMigrationAdapter` and `ReconcileSourceRegistry` stay isolated.** [`doc/TARGET_DATA_CUTOVER.md`](doc/TARGET_DATA_CUTOVER.md) (`DATA-001H`) forbids target writes from production UI/background workers and keeps the target path unwired until its 14 cutover gates are evidenced (`DATA-001I`) — binding them would create the split-brain identity model the decision exists to prevent, and deleting them would discard the staged future authority and its rehearsal fixtures (the "future version not yet wired" class that must not be mistaken for dead code).
- **`verifyDownloadArtifact` stays re-scoped to `DATA-001I` blocker 2.** `DownloadArtifactIndex` has no implementation in any module and `UniFileDownloadArtifactProbe` has no construction site, so wiring the verifier into `Downloader` would compute the verification and discard it — strictly worse than not computing it.
- **Bookkeeping corrected:** the completion plan, `ROADMAP.md` and this ledger no longer classify the five as `B-032`-class orphans needing bind-or-delete; they are recorded `ACCEPTED_DEBT` with the `DATA-001I` cutover gates as the expiry/review condition. No production code changed, by decision.

### 🗑️ Deleted (D1 executes `ADR-0017` — the profile path is gone)

- **The unreachable custom-source profile path is deleted, not deprecated.** `ADR-0017` recorded
  that the profile pipeline could not execute — nothing creates a profile since the UI stopped
  offering custom sources — and `SRC-011` completed its precondition by giving source existence
  and identity one owner: every consumer now projects from `SourceRegistry`, so the residual
  profile code was strictly dead. Deleted in full: `SourceProfile`, `SourceProfileCache`,
  `SourceProfileStore`, `RoomSourceProfileStore`, the Room `SourceProfileEntity` and
  `SourceProfileDao` (database version 5; `MIGRATION_4_5` drops the `source_profiles` table),
  `RemoteSource`, `ContentSourceOrchestrator`, `DynamicHttpSource`, `SourceIdentityPolicy`,
  `MigrationCandidate`, the `UpdateCustomSource` and `RemoveCustomSource` interactors, and their
  tests. **What survives, and why:** `SourceType` moves to its own file with the retired
  `fromString` aliases intact so persisted spellings stay readable; `ContentSourceEngine` remains
  as the profile-free engine contract `SRC-012` targets; `SourceOriginPolicy` and its test stay,
  because the canonical origin is how the registry derives identity. Consumers shrank with the
  deletion: `GetAvailableSources` is `combine(catalogueSources, installedExtensionsFlow)`,
  `AndroidSourceManager` derives readiness from the extension flow and
  `ExtensionManager.isInitialized`, and `MigrateMangaUseCase` syncs chapters only through
  resolvable `CatalogueSource`s — an unresolvable source syncs no chapters, by test. **Stated
  openly:** the `DEF-029` ledger row is deleted with the code it described, and its
  device-reproduction obligation dies with it; per `ADR-0017` the defect's mechanism is closed at
  E2 and no shipped path can trigger it again.

### 🧯 Extension Compatibility (critical fix)
- **MangaDex 1.6 could not load any pages**: the reader's `HttpPageLoader.internalLoadPage` resolved a populated `Page.imageUrl` against the source's `baseUrl` and wrote the result back before fetching, and `resolvePageImage` did the same in its populated branch (so `Downloader`'s retry path inherited the corruption). A real MangaDex extension stores a **relative path** in `Page.imageUrl` (`/data/<hash>/<file>`) and an MD@Home cache key in `Page.url`, and its own overridden `imageRequest` joins them at request time (`GET(mdAtHomeServerUrl + page.imageUrl)`). The host-side rewrite turned every request into `<at-home-host>https://mangadex.org/data/...` — two URLs spliced into a host that can never resolve — so **every page of every chapter failed identically**, presenting as a DNS error (`Unable to resolve host "…mangadex.network,https"`). The recovery ladder could not escape it: a fresh page list restored a fresh relative address that was immediately rewritten again. Fixed by treating a populated `Page.imageUrl` as opaque to the host, exactly as upstream Mihon does: `ResolvedImageUrl.opaque(...)` passes it through untouched (`resolvePageImage`), and the loader no longer rewrites the field — the disk-cache key and the request are the raw string the source produced, while the base `HttpSource.imageRequest` still resolves at the request boundary where a source override inherits nothing by design. Guarded by `MangaDexOpaqueImageUrlContractTest` (a source shaped like the real extension: relative `imageUrl`, at-home cache key, overridden `imageRequest`) and by structural gates in `PageLoadRecoveryStructuralTest`. Documented in `doc/EXTENSION_COMPATIBILITY.md` under "`Page.imageUrl` is opaque to the host once populated", including the general rule: never write an interpretation back into an extension-owned field that the extension is still going to read.

### 🎬 Motion & Navigation (Library ↔ Series)
- **The series page no longer paints its own cover backdrop**: `MangaInfoBox` drew a full-bleed, blurred (`blur(4.dp)`) 20%-alpha copy of the cover behind the header. It read as atmosphere going *in*, but it was a second rendering that was **not** a shared element, so on the way back three alphas ran at once — the shared cover shrinking into its grid cell, the blurry twin fading out over it, and the library grid fading in underneath. That is the "suck" in the return. The series page now uses the same flat `colorScheme.background` as the library, so the cover is the only thing that moves.
- **Motion is now directional**: `MotionPolicy.plan` takes a `MotionDirection` (`FORWARD` / `BACKWARD`) and `MotionPlan` carries it. Material 3's shared-element spec is deliberately asymmetric — the user already knows where back goes, so the return runs a shorter timeline (200ms vs 300ms) instead of the full forward length, which is what made going back read as slow. The policy was previously direction-free and a test asserted that symmetry.
- **The container is held still, not crossfaded**: `ContainerMotion.NONE` now maps to `EnterTransition.None`. With the backdrop gone, nothing should be animating underneath the travelling cover.
- **The cover keeps one shape and one geometry across the flight**: `MangaCover`'s default shape is now `ShapeTokens.coverImage` rather than `shapes.extraSmall`, so the corner radius no longer changes mid-transition (it previously depended on whether the library was in grid or list layout). `.aspectRatio(ratio)` is now applied **before** the shared-element modifier, so the element measures its final box instead of the pre-ratio one — the library cell and the series header request different sizing, and interpolating between non-uniform shapes read as a squash.
- **Predictive back on the series page**: the manifest opts into `enableOnBackInvokedCallback`, but this screen used a bare `popBackStack()`, so the back *gesture* played the system default while the toolbar arrow played the shared-cover reverse. Both now run the same model. Disabled while chapters are selected, where the chapter list's own `BackHandler` owns back.
- **The motion policy is actually consulted**: `MainActivity` resolved this pair with a hand-written `if` while `MotionPolicy` encoded the rule and was unit tested. The policy is now the single owner; `MotionPolicy.mangaCoverKey` is likewise the single definition of the shared-element key, which previously existed as a duplicated string literal across modules where a typo would silently degrade the transition to a crossfade.

### 📖 Reader Navigation, State Machines & Chapter Lifecycle (Phase 1)
- **Direction-Aware Chapter Completion**: Introduced `NavigationVector { FORWARD, BACKWARD }` and guarded read-completion dispatch through it. Swiping backward from page `0` of chapter *N* into chapter *N-1* no longer marks chapter *N* as read; completion requires the final page index **and** a `FORWARD` dispatch (webtoon coverage crossing ≥95% forward). Backward boundary crossings transition chapters without mutating read/progress state.
- **Semantic "Mark Previous as Read"**: `MangaViewModel.markPreviousChapterRead` now compares against `Chapter.sourceOrder` (reading order) instead of raw display indices, so reverse-sorted or filtered chapter lists update exactly the chapters that precede the selection — never the ones merely rendered above it.
- **Transition State Machine & "Caught Up" Terminal State**: Added the sealed `TransitionState { ToPrevious, ToNext, EndOfSeries }` hierarchy with `ChapterTransition.toTransitionState()`. Forward navigation past the final available chapter now renders a terminal summary card with a high-prominence **"Return to Series"** button wired to `onNavigateUp` in both the pager and webtoon readers.
- **Directional Transition Arrows**: Added `TransitionDirection { LTR, RTL, VERTICAL }`, resolved from the active viewer (`R2LPagerViewer`/`VerticalPagerViewer`), so previous/next transition arrows point along the reader's own reading flow instead of a hard-coded horizontal direction.

### 🐛 Critical Fixes
- **A chapter could read successfully and still fail to download** (`DEF-028`): when a page image failed, the reader asked the shared `TransientErrors` whether the URL was at fault, dropped it and asked the source for a different one — while the downloader consulted the *same* classifier and then re-requested the identical URL anyway. So for any source that hands out short-lived signed image URLs, the retry ladder could never recover: the classifier's verdict was computed and discarded. The two also disagreed on timing (1s/2s/4s against 2s/4s/8s) and neither had jitter, so a chapter whose pages all expired together — which is what batch-minted signed URLs produce — retried in lockstep at a source already asking to be left alone. `PageLoadRecovery` is now the single owner of the retry decision, the budget, the jittered backoff and the record of what was tried; the reader and the downloader ask the same object, so they cannot drift again. The request budget is deliberately **unchanged** at three retries — recovery cannot quietly raise the worst-case requests per page, only the delay differs. Failures that indict the *host* rather than the connection — an untrusted certificate, a refused connection — now ask the source for a different URL instead of retrying the same address four times; timeouts are excluded, since nothing distinguishes a connect-phase timeout from a slow read. When a page finally fails it now records what it tried (`cdn-a (HTTP 403), cdn-b (timeout)`), which is the difference between a diagnosable report and one that has to be reconstructed from first principles. The pager also shows the rate-limit countdown retry that the webtoon reader already had, for the identical failure from the identical shared loader. When a replacement URL cannot be obtained, the page still reports the failure that actually stopped it rather than the error from our own retry, and a page's *first* image URL is never delayed — the pacing that protects a source from a burst of re-resolutions is applied only after something has already failed. A failed page now also shows a progress spinner that is honest rather than frozen at whatever percentage a dead download reached, and the automatic retry ladder is bounded in wall-clock so the Retry button arrives in seconds rather than after the full backoff ladder. The rate-limit countdown re-arms on every new failure instead of being spent on the first.
- **Pages a source named relatively could not be requested at all** (`DEF-027`): viewing a page failed with `Expected URL scheme 'http' or 'https' but no scheme was found for …` — OkHttp's own message, thrown while a request is *built*. Nothing was sent, which is why the page could be live at the source and un-rate-limited at the same time. Sources routinely name a page relatively (`img.attr("src")` rather than `absUrl("src")`, or the protocol-relative `//cdn…` a `<base>`-tagged site emits) and nothing completed the address: the reader copied `Page.url` verbatim and this fork's `HttpSource.getImageUrl` is the deprecated *network* variant, so upstream's `baseUrl` concatenation was absent. The `DEF-023` pre-flight check could not catch it — it runs after `getImageUrl` and inspects `imageUrl`, never `Page.url` — and as an `IllegalArgumentException` it classified as **permanent**, so there was no retry ladder and the user's own Retry repeated the identical failure. Both readers were affected; the fault was in the shared loader, not the viewer. **The fix completes the address rather than reporting it** — a relative URL is incomplete, not broken, and the missing half is a value the app already holds, so no new error path was added. `ImageUrlPolicy.resolve` is the single owner of the rule and is applied wherever a request can be built: `HttpSource`'s image-URL and image request builders, the reader loader (covering URLs restored from the chapter cache or set directly by a source), and the downloader, which previously had no URL policy call at all. Absolute URLs — notably signed CDN URLs, whose exact spelling is the credential — are passed through byte-identical. `DynamicHttpSource` also stops rewriting `//cdn…` into a URL pointing at a host that does not exist.
- **Extension zstd Crash (`Lokhttp3/zstd/Zstd;`)**: Bundled `com.squareup.okhttp3:okhttp-zstd` (5.5.0) via `core:common`. Dynamically loaded extension APKs (MangaDex, Mangabat, and most modern Mihon/Keiyoushi extensions) reference the `okhttp3.zstd` package, which OkHttp ships in a separate optional module. Without it, every source network call failed with `NoClassDefFoundError` (wrapped by `ExtensionCallBoundary` as "Source 'X' encountered an error"), breaking chapter loading, search results, covers, and making library buttons appear unresponsive on source-driven screens.
- **Silent Chapter-Sync Failures**: `MangaChapterInteractor.syncChaptersWithSource` no longer swallows source fetch failures. Failed manga-details/chapter-list fetches are logged, manual refreshes now surface a toast via `MangaScreenEffect.ShowToast`, and background refreshes keep the local chapter list instead of wiping it.
- **Markdown Renderer Build Break**: Implemented the new `alert: MarkdownAlertPadding` property required by `multiplatform-markdown-renderer` 0.45.0's `MarkdownPadding` interface in `MarkdownRender.kt` (fixes `:feature:manga:compileDebugKotlin`).
- **Markdown Renderer Build Break (0.46.0 highlight API)**: the dependency sweep bumped
  `com.mikepenz:multiplatform-markdown-renderer` to 0.46.0, whose new highlight-span feature made
  `highlightText` and `highlightBackground` required parameters of `DefaultMarkdownColors`, so
  `:feature:manga:compileReleaseKotlin` stopped compiling with `No value passed for parameter
  'highlightText'`. `getMarkdownColors()` now passes both, styled to match Mihon: highlighted
  (`==marked==`) text renders `onSecondaryContainer` over `secondaryContainer`, so it follows the
  Material scheme in light and dark themes. Compile-time adaptation only — content without
  highlight spans renders exactly as before.
- **Instant Launch Crash / Silent Exit**: The Shizuku purge dropped `dev.rikka.shizuku:provider` from the build but left its `<provider android:name="rikka.shizuku.ShizukuProvider">` declaration behind in `app/src/main/AndroidManifest.xml`. Android instantiates manifest-declared providers in `ActivityThread.handleBindApplication` *before* `Application.onCreate()`, so every launch died with `ClassNotFoundException: rikka.shizuku.ShizukuProvider` before `GlobalExceptionHandler` or `StartupFailureActivity` could run — the process closed instantly with no crash dialog and no error screen. Removed the stale provider entry and added `ManifestComponentClassTest`, which fails the build whenever a manifest-declared Android component class is missing from the app classpath.

### 🧹 Anti-Pattern & Correctness Sweep (core audit)
- **Kitsu Dates Were Thread-Unsafe and Timezone-Shifted**: `KitsuDateHelper` shared a single `SimpleDateFormat` across the tracker (manga lists are parsed entry by entry) and used a quoted `'Z'`, so Kitsu's UTC timestamps were read as local time and every date moved by the device's UTC offset — plus a malformed value could throw `ParseException` into a sync. Replaced with UTC-pinned, tolerant `java.time` parsers that fall back to the "no date" sentinel (`0`).
- **EPUB Dates Depended on the Device Calendar**: `fillMetadata` parsed `dc:date`/`dcterms:modified` with `SimpleDateFormat(..., Locale.getDefault())`, which on a non-Gregorian default locale (e.g. `th-TH`) silently shifted the year by the Buddhist era offset, and only understood one colon-less offset shape. Now parsed with locale-independent ISO parsers (`+00:00`, `Z`, fractional seconds, and bare dates).
- **Tracker Date Formatting De-Duplicated**: The same `yyyy-MM-dd`/`SimpleDateFormat` conversion existed in four places (AniList, Kitsu, MyAnimeList) with inconsistent safety. All now go through a single tested `TrackDateFormats` helper that parses strictly — `2024-02-30` is rejected instead of being rolled over to March 1st.
- **Default Storage Fallback Could Not Work**: `AndroidStorageFolderProvider` fell back to the deprecated `Environment.getExternalStorageDirectory()`, which no app targeting a modern SDK can write to, so the "fallback" produced a permanently unusable default path. It now falls back to internal `filesDir`, which is always writable. The obsolete `requestLegacyExternalStorage`/`preserveLegacyExternalStorage` manifest attributes were removed with it — both are ignored at `targetSdk 37` and were the false promise that made the shared-storage root look usable.
- **Startup Phase Table Was Racy**: `StartupGuard.phases` was a plain `HashMap` written by the crashing thread (the crash handler registers the phase it could not attribute) while the main thread reported progress. Now a `ConcurrentHashMap` with idempotent registration.
- **CI Static Analysis Was a No-Op**: The `detekt` task was registered as an empty stub that no plugin backed, so the CI "static analysis" step always passed without inspecting anything. The stub is gone (with a note on what a real detekt wiring requires) and CI now runs the checks that exist (`testDebugUnitTest`, `ktlintCheck`). Test fork/timeout/logging tuning that silently applied to nobody (root-project `tasks.withType` on a source-less project) moved into the `ephyra.*` convention plugin where it actually takes effect.

### 🔍 Failure Attribution & the Adapter Seam
- **Failures now name the layer that owns them**: a new `FailureLayer` (`SOURCE` / `ADAPTER` / `TRANSPORT` / `RENDER`) classifies a throwable via `LayeredFailure`, and the cause is retained so `TransientErrors` still classifies by type. The MangaDex report was `Unable to resolve host "cmdxd98sb0x3yprd.mangadex.network,https"` — a *transport* message describing the last thing that happened rather than the layer at fault, which is why it took several exchanges to locate. An unrecognised failure now defaults to `SOURCE`, not `TRANSPORT`, because defaulting to "network" is the bias that sent that investigation to the resolver. Rationale in [ADR-0014](doc/adr/0014-failures-name-the-layer-that-owns-them.md).
- **`ContentAdapter` marks where a provider's shape becomes ours**: extension sources, local archives and (later) Jellyfin are three implementations of one contract with three unrelated transports. An adapter validates its own output with the single owner of the rule (`ImageUrlPolicy`) and returns `AdapterOutput.Rejected` rather than forwarding an address no request could use.
- **`ContentConformance` harness**: a reusable suite every adapter must pass, demonstrated against the reported defect. It is the only one of these layers testable without a device, which is why this class of defect can now be caught in a JVM test in seconds instead of on a user's report.
- **Source-type selection stays additive**: `SourceTypeRegistryStructuralTest` fails if the orchestrator regains an exhaustive `when` over `SourceType`, if a declared type is missing from `fromString`, or if the retired `JS_SCRAPER` name stops mapping to a live type. Adding Jellyfin must not require editing the orchestrator.

### 🔌 Source Types That Actually Work
- **Transpiler and JS Scraper Runtime Removed**: Removed on-device Kotlin-to-JavaScript transpilation (`transpiler.js` + its bundled `MiniDOM`) and the QuickJS scraper runtime (`ScriptableContentSourceEngine`, `JavaScriptEngine`, `DynamicScraperUpdater`, `ScraperScriptUpdater`, the background scraper-update worker, and the scraper UI in the Browse and Settings screens). The transpiler located parsing code by searching for the literal text `pageListParse` and matching braces, assumed every source is HTML addressed by `<img>`, and could not represent API-based sources — so for MangaDex it emitted well-formed JavaScript that could not work, and the failure presented as a malformed image URL (`host,https`) instead of an unsupported source. A mechanism that fails plausibly is worse than one that is absent, because the two are indistinguishable until a user reports it. Rationale in [ADR-0013](doc/adr/0013-source-types-that-exist.md).
- **Heuristic Discovery Engine Removed**: Removed `AdaptiveHeuristicEngine`, `AddCustomSource`, `SourceType.HEURISTIC`, and its UI. It never resolved a source successfully, and as the orchestrator's fallback for any unbound type it made every unclaimed request look handled. `REPOSITORY` now raises `NoEngineBoundException` — the previous `REPOSITORY -> heuristicEngine // for now` placeholder would have routed Jellyfin to HTML scraping and failed quietly. Heuristic discovery is not forbidden, only unearned: it returns as an engine with a fixture proving it works. Rationale in [ADR-0015](doc/adr/0015-one-source-type-until-another-works.md).
- **Content Sourcing Hub Removed**: the discovery screen and its Sources-tab button. Its only action was `discover()`, which raises `NoEngineBoundException` with an empty engine registry. A button in the primary Sources tab that leads to a screen which cannot succeed is a dead end presented as a feature.
- **Update Check Loop Resolved**: Updated `AppUpdateChecker.kt` to dynamically resolve the app version from `PackageManager` instead of using the hardcoded `:core:data` BuildConfig value.
- **ArchUnit Feature Isolation**: Resolved all feature module package/dependency boundary violations by moving shared components (`MangaCover`, `LibraryBottomActionMenu`, `CategoryExtensions`, `ChangeCategoryDialog`) to the common `:presentation-core` module, and introducing compositional navigation interfaces via `LocalAppNavigator`.
- **Injekt Confined To The Extension-Bridge ABI (Here's What Actually Happened)**: The prior changelog claimed `uy.kohesive.injekt` was "cleanly removed" and "eradicated"; that was false. What really happened — `core:common`'s internal `uy.kohesive.injekt` shim package is gone, every app/feature DI lookup now uses standard Hilt constructor injection, *and* a single Injekt container still runs on purpose inside the dynamic `eu.kanade.tachiyomi` extension bridge (`:core:common` / `:source-api`), because legacy extensions can't be wired through Hilt. Injekt is alive, but confined to exactly one place: the extension ABI.

### 🏛️ Architecture & Clean Separation (Phase 8)
- **Domain Abstractions**: Introduced generic, media-agnostic domain interfaces `ContentDatabase`, `RemoteSource`, and `TrackingService` inside `:core:domain`, separating high-level business rules from concrete database/network frames.
- **Result Envelopes**: Wrapped database and remote crawling transactions in standard `Result<T>` sealed envelopes, guaranteeing explicit data mapping and strict exception boundaries.
- **DI Bindings**: Bound concrete data implementations (`ContentDatabaseImpl`, `ContentSourceOrchestrator`, `TrackingServiceImpl`) inside `AppModule` using standard `@Provides @Singleton` annotations.

### 🧹 Dependency & Legacy Purges (Phase 11)
- **SQLDelight Complete Purge**: Systematically removed SQLDelight drivers, configuration files, and references from all module build scripts and version catalogs, completing the transition of mangas, chapters, categories, and history to standard Room DAOs.
- **DI & Navigation Purges**: Eradicated all remnants of Voyager and Koin from all build configurations. (Narratively adjacent to "Injekt purged" above, but Injekt was *not* eradicated — it remains in `:core:common` / `:source-api` as a compatibility shim for legacy `eu.kanade.tachiyomi` extensions; app code is 100% Hilt, 100% Jetpack Navigation & Compose coverage.)
- **CoreContainer Isolation**: Fully decoupled `CoreContainer` from all internal classes, confining it strictly as a bridge for external dynamic extensions in `:source-api`.

### 🛡️ Startup Resiliency & Safety (Phase 12)
- **Early Logging & Crash Trapping**: Overrode `attachBaseContext(base: Context)` inside `App.kt` to initialize `LogcatLogger` and `GlobalExceptionHandler` *before* the application executes Hilt dependency injection, content providers, or WorkManager initializers in `super.onCreate()`. Any early bootstrap crash is now gracefully captured.
- **Splash Screen Freeze Elimination**: Added a public `cancelAndRelease()` function to `Migrator.kt` that instantly completes the synchronization `initGate` on preference load failures, bypassing the 30-second splash screen freeze and permitting degraded boot.
- **Preference Stream Crash Guard**: Appended `.catch` flow operators to all reactive preference observation streams inside `App.kt` to ensure unhandled I/O failures never terminate the application scope.
- **Recovery Diagnostics Mismatch Solved**: Refactored `StartupFailureActivity.kt` to query `GlobalExceptionHandler.getThrowableFromIntent(intent)` directly, ensuring the exact Hilt/DI traceback is displayed on screen rather than a generic `"Unknown error"`.
- **Compose Migration Gating**: Gated `NavHost` rendering and update checker execution in `MainActivity.kt` behind database migration completion (`didMigration != null`), resolving a fundamental startup race condition where active ViewModels queried the database before Room migrations finished.

---

## [0.20.0] — 2026-05-18

- **Compile-Time Deterministic DI**: Removed Koin and Koin Annotations from the dependency
  graph. All singletons are now wired explicitly via `AppDependencyContainer` and `CoreContainer`,
  eliminating any reflective or annotation-processed runtime resolution.
- **`VoyagerKoinShim`**: Added a thin compatibility shim so existing Voyager screen-model call
  sites compile without changes while the underlying DI is now pure Kotlin.
- **`AppWorkerFactory`**: Extracted worker creation into a dedicated factory, decoupling
  `WorkManager` initialization from the application-level DI graph.

### 📚 Content-Agnostic Library UI

- **`ILibraryItem` interface**: Introduced a generic contract (`id`, `title`, `coverUrl`,
  `unreadCount`, `downloadCount`, …) that decouples the library presentation layer from the
  concrete `LibraryManga` / `Manga` domain types.
- **`LibraryItem` implements `ILibraryItem`**: The existing library item is now an adapter that
  wraps `LibraryManga` and satisfies the new interface, preserving all existing behavior.
- **Library grids & lists consume `ILibraryItem`**: `LibraryCompactGrid`, `LibraryComfortableGrid`,
  `LibraryList`, `LibraryContent`, and `LibraryPager` now operate on the generic interface,
  making it straightforward to surface future media types (anime, novels, etc.).
- **`LibraryScreenModel` selection callbacks**: `toggleSelection` and `toggleRangeSelection`
  now accept `ILibraryItem` instead of `LibraryManga`.

### 🔧 Build Configuration

- **AGP 9.0 compliance (`android.builtInKotlin=true`)**: Enabled the AGP 9.0 built-in Kotlin
  integration globally. Removed deprecated `kotlin.incremental.useClasspathSnapshot` and
  `kotlin.compiler.preciseCompilationResultsBackup` properties.
- **`MangaScreenModelFactory`**: Moved screen-model construction for the manga feature into an
  explicit compile-time factory, removing the last Koin `ScreenModel` injection from
  `:feature:manga`.
- **Missing feature build files**: Created `build.gradle.kts` stubs for previously unconfigured
  modules (`feature/category`, `feature/download`, `feature/history`, `feature/more`,
  `feature/security`, `feature/stats`, `feature/updates`, `feature/webview`), resolving
  "no variants exist" Gradle resolution errors.
- **Compose BOM in `:domain`**: Added `compileOnly(platform(compose.compose.bom))` so that
  `compose.runtime.annotation` resolves correctly without pulling runtime Compose into the
  domain layer.

### 🗺️ Documentation

- **`ROADMAP.md`**: Updated to reflect completed Phase 1 (Pure DI), in-progress Phase 3
  (Content-Agnostic UI) and Phase 4 (Build Config), and planned Phase 2 (Sandboxed Scripting).
- **`CHANGELOG.md`**: This file — initialized.

