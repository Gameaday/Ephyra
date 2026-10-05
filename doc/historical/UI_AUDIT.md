# UI / Transitions / Effects / Menu / Settings Audit

> **Status:** HISTORICAL - completed improvement-session record. Retained for archaeology only;
> not current guidance. Current forward plan: [`../2_0_COMPLETION_PLAN.md`](../2_0_COMPLETION_PLAN.md).

Scope: full UI audit of Ephyra at commit `39cedf9` (branch `cline/ezxqydet`). Areas: settings
(incl. More, onboarding), reader/player, library/browse/manga/updates/upcoming/history/category,
and the shared presentation layer (theme, navigation, components, effects).

Every finding below was verified by reading the cited code. "Verified OK" sections list things
checked and found correct, to prevent re-auditing.

## Status: resolved in follow-up commits

The findings below were triaged and implemented in phases (see the commits on `cline/ezxqydet`):

- **Phase 1** (`fix(ui): phase 1 - data-loss/state-loss defects`) — R-H1, R-H2, L-H3, L-H4, L-H5,
  L-H6, L-H1, L-H7.
- **Phase 2** (`fix(settings): phase 2 ...`) — S-H1, S-H3, S-H4, S-H2, list keys, localized errors.
- **Tab transitions** (`feat(nav): slide main tab area by tab order ...`) — the P-H1 reduced-motion gap
  in the main area, plus the owner-reported "discrete fade" navigation; recorded in
  `MOTION_NAVIGATION_CONTRACT.md`.
- **Hierarchical transitions + extensions loading** (`perf(extensions) ... ; feat(nav) ...`) — the
  back-transition quality and the slow available-extensions scroll.
- **Cache and cover art** (`fix(cache): ...`) — L-6 and the cover placeholder theming, with the
  remaining items in `doc/historical/CACHE_AND_COVER_AUDIT.md`.
- **Search** (`refactor(search): ...`) — the divergent debounce constants, with the full inventory in
  `doc/historical/SEARCH_SYSTEM_AUDIT.md`.

Findings not listed above remain open; they are unchanged below.

---

## 1. Settings & More

### HIGH

| ID | Finding | Location | Suggested fix |
|---|---|---|---|
| S-H1 | Tablet two-pane settings is dead: `destinationId` is always passed `null` (`SettingsScreen(null, navController)` is the only registration). The phone dispatch is dead code; on tablets `currentDetail` is initialized to Appearance and *never updated* — `SettingsMainScreen` item clicks still `navController.navigate(...)`, pushing a full-screen destination instead of switching the detail pane. Master/detail is non-functional. | `feature/settings/.../SettingsScreen.kt:29-48`, `SettingsFeatureApi.kt:37-39` | Pass a route arg / observe back-stack entry; on tablet intercept item clicks to set `currentDetail` instead of navigating. |
| S-H2 | Long-running network work (tracker list import, tracker login/logout) launched in `rememberCoroutineScope()` — cancelled mid-flight on rotation/process death; the `importingTrackerId`/dialog UI state is plain `remember` and is lost too. | `screen/SettingsTrackingScreen.kt:109, 138, 927, 960, 1087, 1142` | Move work into `SettingsTrackingViewModel` (or `viewModelScope` / `NonCancellable`); expose state via `collectAsStateWithLifecycle`. |
| S-H3 | Unchecked `context as FragmentActivity` cast inside `onValueChanged` — `ClassCastException` if hosted outside a `FragmentActivity` (previews, locale activity contexts). Sibling code uses safe casts. | `screen/SettingsSecurityScreen.kt:60, 83` | Safe-cast; no-op + feedback when not a `FragmentActivity`. |
| S-H4 | Sliders persist to DataStore on every drag tick — no debounce, no `onValueChangeFinished`; thumb position also round-trips through DataStore (janky, final value can be dropped on fast drags). | `presentation-core/.../components/SettingsItems.kt:249-258`, `feature/settings/.../PreferenceItem.kt:90-102` (e.g. `SettingsDownloadScreen.kt:97, 104`) | Keep a local `remember` value while dragging; persist on `onValueChangeFinished` / debounce. |
| S-H5 | "Lock when idle" biometric gate discards the selected `newValue` (used purely as an auth gate), so the preference semantics depend on cast side-effects. | `SettingsSecurityScreen.kt:82-86` + `PreferenceItem.kt:119-125` | Apply the value explicitly only after successful auth. |

### MEDIUM / LOW (summary)

- Version string inconsistency in About screen (M1) — verify `AboutScreen` reads the same build
  version source as the rest of the app.
- Settings-screen navigation lacks `launchSingleTop` in some entry points (rapid duplicate taps push
  duplicate destinations) — contrast with `SettingsMainScreen.kt:94-96` which has it.
- `MoreScreen` parameter-explosion overload is only used by its sibling + tests — dead API surface.
- Raw exception text shown to users via `context.toast(e.message ?: "")` (also empty toast when
  `message == null`) — `SettingsTrackingScreen.kt:1098, 1151`. Use localized error resources.
- `PreferenceScreen` LazyColumn items lack stable keys; every preference write rebuilds the whole
  `List<Preference>` tree, so keyless items recompose/re-order wholesale. Add `key = { it.title }`.
- `BasePreferenceWidget` highlight `LaunchedEffect(Unit)` captures `highlighted` once — key the
  effect on `highlighted`.
- `OnboardingScreen` `steps` remembered on `storageDirPref`/`telemetryIncluded`; `PermissionStep.isComplete = true`
  always enables "Next" (verify intent — permissions optional).
- `LocalPreferenceMinHeight = 56.dp` CompositionLocal is never provided anywhere — dead config point.

### Verified OK

- `SwitchPreference`/`ListPreference` persistence gating in `PreferenceItem.kt:80-125`.
- Dual-page-split vs rotate-to-fit mutual exclusion (`SettingsReaderScreen.kt:284-301`).
- `EditTextPreferenceWidget` uses `rememberSaveable(TextFieldValue.Saver)` + suspend-safe confirm.
- Onboarding permission re-check on `onResume` survives rotation.


---

## 2. Reader & Player (transitions, effects, gestures, menus)

### HIGH

| ID | Finding | Location | Suggested fix |
|---|---|---|---|
| R-H1 | Remove-after-read chapters deleted on **every `onPause`**, not session exit — ReaderActivity.onPause unconditionally sends `ActivityFinish` → `deletePendingChapters()`. Triggered by "Open in browser"/Share from the reader menu, split-screen, phone call, replying to a notification: pending chapters are wiped while still being read. The `finish()` override already sends the event, so the `onPause` path is redundant *and* harmful. | `ReaderActivity.kt:253-256` → `ReaderViewModel.kt:320-325, 1395-1401` | Send `ActivityFinish` only from `finish()` / `onDestroy` gated on `isFinishing`. |
| R-H2 | Final chapter's history (read-at time + session duration) never saved on exit — `updateHistory()` is only called from `loadNewChapter`. Exit/process death leaves `sessionReadDuration = 0` and a stale `readAt` for the last chapter of the session. (`lastPageRead` *is* saved per page.) | `ReaderViewModel.kt:916-927`, only caller `:455` | Call `updateHistory()` in a `launchNonCancellable` from `onDestroy`/`finish()`. |

### MEDIUM

- **R-M1** System bars forced hidden regardless of the `fullscreen` preference —
  `setMenuVisibility(false)` always calls `hide(systemBars())`; with fullscreen off, one tap in the
  menu zone hides bars, and `onWindowFocusChanged` makes the hidden state sticky
  (`ReaderActivity.kt:295-300, 550-558, 263-270`). Gate on `readerPreferences.fullscreen().getSync()`.
- **R-M2** Tap dead zones in webtoon mode: tap handlers exist only on page items; chapter-transition
  cards and container gaps (incl. 32.dp padding) do nothing — no menu toggle, no navigation
  (`ComposeWebtoonReader.kt:365-378, 416-470, 596-606`).
- **R-M3** Stale-lambda capture in webtoon tap detector: `pointerInput(page.index)` restarts only on
  index change but captures `onSingleTap` (closing over `navigator` config and `scrollDistance`) —
  settings changed mid-session don't take effect until strip recomposition
  (`ComposeWebtoonReader.kt:598`). Use `rememberUpdatedState` or key on captured values.
- **R-M4** Hardcoded animation durations in reader components bypass MotionTokens/reduced-motion
  (see also shared-layer findings).

### LOW

- **R-L1** Overscroll velocity threshold uses raw px/s (`available.y < -800f`) while the distance
  threshold is density-scaled — velocity path triggers ~2x easier on high-dpi devices
  (`ComposeWebtoonReader.kt:331-357`, `ComposePagerReader.kt:347-351`).
- **R-L2** `DisplayRefreshHost` `timesCalled % flashInterval` crashes on 0 (UI slider clamps 1..10,
  but no guard at the read) — `DisplayRefreshHost.kt:35-40`.
- **R-L3** Overscroll `NestedScrollConnection` remembered with `items` as key — any
  absorb/filter/rebuild resets `accumulatedOverscroll` mid-gesture
  (`ComposePagerReader.kt:241-244`).
- **R-L4** Tap zones are gesture-only with no `Modifier.semantics`/`customActions` — TalkBack users
  cannot toggle the reader menu from most of the screen (hardware-key fallback exists — good).
- **R-L5** Player: `ExoPlayer` rebuilt on config change — no position save/restore; keeps playing
  when backgrounded (no `ON_STOP` observer). `PlayerUiEvent.PlayPause/SeekTo/Retry` are dead —
  nothing dispatches them (`VideoPlayerScreen.kt:44-54`, `VideoPlayerViewModel.kt:66-74`).

### Verified OK

- Slider nav mode (instant vs smooth) is genuinely wired and persisted
  (`GeneralSettingsPage.kt:96-108` → `PagerViewer.kt:239` → `animateScrollToPage`/`scrollToPage`);
  `pageTransitions` pref likewise.
- Per-page `lastPageRead` persisted inside `launchNonCancellable`; `chapter_id`/`page_index` saved in
  `SavedStateHandle` — survives process death.
- `menuVisible` lives in the ViewModel — rotation restores it; no stuck-hidden state beyond R-M1.
- `targetPageRequest` (conflated/replay=1) + `StepOriginPolicy` correctly avoid seek-bar lag and
  phantom-index drift.


---

## 3. Library / Browse / Manga Detail / Updates / Upcoming / History / Category

### HIGH

| ID | Finding | Location | Suggested fix |
|---|---|---|---|
| L-H1 | **Restored category lost**: `rememberPagerState(currentPage)` uses the VM's async-loaded index only as initial value; VM reads `lastUsedCategory()` on IO after first composition, so the pager is at 0 and the `LaunchedEffect(pagerState.currentPage)` echo immediately persists 0 over the restored index. Last-used category never restored on cold start. | `LibraryContent.kt:91, 171-173`, `LibraryViewModel.kt:157-160, 878-884` | Sync `LaunchedEffect(currentPage) { scrollToPage }` when not scrolling; suppress the persist-echo until first sync. |
| L-H2 | Two-finger "open settings" gesture conflicts with pinch-to-zoom columns: raw `awaitPointerEvent` detector (unlatched, 15f raw px threshold, no consumption) overlapping `detectTransformGestures` — pinching columns also opens the settings sheet, can re-fire per event; no a11y alternative. | `LibraryContent.kt:74-81`, `LazyLibraryGrid.kt:34-46` | Remove or confine the raw detector; use dp; latch per gesture; keep toolbar entry. |
| L-H3 | Manga detail: swiping a DOWNLOADED chapter **deletes it with no confirmation/undo** (multi-select path does confirm). Single accidental swipe silently destroys a download. | `MangaViewModel.kt:380-385` | Route swipe-delete of DOWNLOADED chapters through `ShowDeleteChapterDialog` or undo snackbar. |
| L-H4 | Chapter download-indicator DELETE action deletes instantly, small touch target, no confirmation. | `components/ChapterDownloadIndicator.kt:220` → `MangaViewModel.kt:654-656, 745-747` | Same as L-H3. |
| L-H5 | Browse: uninstalling an installed extension has no confirmation — one tap removes the extension and all its sources (custom-source removal *is* confirmed, proving the pattern was missed). | `ExtensionsScreen.kt:330-336` → `ExtensionsViewModel.kt:176-179` | Add confirmation dialog mirroring `showRemoveConfirmDialog`. |
| L-H6 | Browse: repository deletion has no confirmation (delete icon → gone + reload). | `ExtensionsScreen.kt:833, 873-879` → `ExtensionsViewModel.kt:119-127` | Confirm before delete, naming the repo. |
| L-H7 | Category reorder persists a DB write **per drag frame**; each write re-triggers the categories subscription → recomposition/re-emission storm during drag. | `presentation/CategoryScreen.kt:87-91` → `CategoryViewModel.kt:76-83` | Persist on drag end / debounce. |

### MEDIUM (summary of verified items)

- Library search/filter state loss on back-navigation paths (screen only sets query when arg
  non-null — stale in-screen filter remains; dispatch `Search(null)` on arg-less entry).
- GlobalSearch auto-redirect flag captured once in un-`rememberSaveable` `remember` at first
  composition (before VM init produces results) — the redirect feature can silently never trigger
  and the guard stays true after auto-navigation, leaving a bare loading screen on back-out.
- `TrackInfoDialog` sub-screen dismiss does `stack.removeAt(stack.lastIndex)` unguarded —
  `IndexOutOfBoundsException` on empty stack (`track/TrackInfoDialog.kt:135-194`).
- ScanlatorFilterDialog holds selection in local `remember` (edits lost on config change) and its
  list items lack keys.
- Manga genre-remove chip is a dead click target: `FilterChip(onClick = {})` with a 16dp removal
  icon (`EditMetadataDialog.kt`) — make the chip itself remove the genre.
- Library pager manual page-window check duplicates/under-restricts `beyondViewportPageCount`
  (blank pages during fast flings) — prefer `beyondViewportPageCount = 0` and drop manual check.

### LOW (summary)

- Dead global `Channel<SearchType>` collected per screen instance; nothing sends to it
  (`BrowseSourceScreen.kt:354, 343-351`). No-op `pointerInput(Unit) {}` modifier at
  `BrowseSourceScreen.kt:143-145`.
- Hardcoded English strings in Browse (`SourcesTab.kt:53`, `ExtensionsTab.kt:33-43`,
  `ExtensionsScreen.kt:730, 876, 915, ...`) while neighbors use `stringResource`.
- `SourceOptionsDialog` uses clickable `Text` rows with no button role/semantics
  (`SourcesScreen.kt:180-211`).
- `UpcomingScreen` capital-V `ViewModel` parameter name shadows the type (`UpcomingScreen.kt:15-18`).
- Calendar weekday order remembered without locale/first-day key — desyncs on dynamic locale
  changes (`components/calendar/Calendar.kt:74-78`).
- Updates `collectAsStateWithLifecycle(initialValue = getSync())` reads prefs synchronously in
  composition — fine today (in-memory snapshots), becomes a main-thread read if delegates move to
  DataStore.

### Verified OK

- Stable keys/contentType across library grids, browse lists, updates, history, upcoming, chapter lists.
- `collectAsStateWithLifecycle` used consistently for VM state; plain `collectAsState()` hits are
  the project's lifecycle-safe preference wrapper.
- Destructive confirmations present: library delete, manga multi-select delete, updates multi-delete,
  history delete/delete-all, category delete.
- Back handling: selection mode handled before navigation; bottom nav hidden in selection;
  predictive back disabled during chapter selection.
- No main-thread DB reads in composition; no swipe-action conflicts in updates/history.


---

## 4. Shared presentation layer (theme, navigation, components, effects)

### HIGH

| ID | Finding | Location | Suggested fix |
|---|---|---|---|
| P-H1 | **Reduced-motion honored only by the root NavHost.** `LocalMotionPreference` has exactly one consumer (MainActivity root graph). Home tab NavHost always fades; `AdaptiveSheet` open/close/dismiss uses fixed 300 ms tween; health banners and reader zoom-reset use raw `tween(300)` literals — all ignore the reduced-motion setting. | `MainActivity.kt:340-343`, `HomeScreen.kt:226-230`, `AdaptiveSheet.kt:314-317`, `SourceHealthBanner.kt:47-63`, `LibraryHealthBanner.kt:38-54`, `ZoomableMangaPage.kt:238-257` | Consult `LocalMotionPreference.current` in `AdaptiveSheet` (use `snap()`), Home tab NavHost, banners; extend the existing architecture-test pattern to ban raw `tween(n)` outside `MotionTokens`. |
| P-H2 | `rememberSystemReducedMotion()` cached for the Activity's lifetime — `remember(context) { Settings.Global.getFloat(ANIMATOR_DURATION_SCALE) == 0f }`, never re-evaluated. Toggling "Remove animations" in system settings while the app is open has no effect until recreation. | `LocalMotionPreference.kt:28-38` | Re-read on lifecycle resume (`LifecycleResumeEffect` / resume-keyed callbackFlow). |

### MEDIUM

- **P-M1** `AdaptiveSheetDialog`/`AdaptiveSheet` set `decorFitsSystemWindows = true` *and* apply
  manual `.navigationBarsPadding()`/`.statusBarsPadding()` — double-inset risk (excessive gap under
  sheets on gesture-nav devices), contradicting the app-wide edge-to-edge strategy
  (`AdaptiveSheetDialog.kt:20-23`, `AdaptiveSheet.kt:86-89, 133, 225-226` vs
  `MainActivity.kt:322-328`). Pick one insets owner.
- **P-M2** App-state banners (Indexing/Downloaded-only/Incognito) use hardcoded colors + white text,
  bypassing MaterialTheme — don't adapt to any of the 17 themes (Monochrome/Monet)
  (`AppStateBanners.kt:19-21, 44-85`). Use theme roles.
- **P-M3** Toast vs Snackbar split is inconsistent: `Effect.ShowToast` and snackbar effects
  coexist, producing mixed transient-feedback UX (duplicate messages across channels). Alias Toast
  effects to snackbars where a host exists; reserve toasts for contextless callers (workers,
  interceptors).

### LOW

- Duplicate `WindowSize` helpers (`app/.../designsystem/utils/WindowSize.kt` vs
  `presentation-core/.../util/WindowSize.kt`) — app copy unreferenced, can drift.
- `EphyraTheme` color-scheme `remember` omits `context` as key — MONET dynamic colors can go stale
  on context/rotation swap (`EphyraTheme.kt:79-86, 100-101`).
- Hardcoded cover placeholder color `0x1F888888` in all 17 themes (`MangaCover.kt:121`) — bypasses
  the token system.
- Inline tween literals outside MotionTokens: `SettingsSearchScreen.kt:222-227`,
  `ReaderScreen.kt:126`, `CalendarHeader.kt:76-86`.
- `App.onTrimMemory` permanently halves Coil's memory cache with no restore path — after a few trim
  cycles the cache bottoms out even after pressure clears (`App.kt:439-448`); affects image scroll
  smoothness after backgrounding.

### Verified OK (repo-wide greps)

- `collectAsState(` (99 non-test hits) all resolve to the project wrapper around
  `collectAsStateWithLifecycle(initialValue = getSync())` (`presentation-core/.../Preference.kt:11-14`)
  — lifecycle-safe.
- Zero `GlobalScope`; zero `Handler(Looper.getMainLooper())`; no dp-based text sizes.
- Tab back-stack correct (`popUpTo{saveState} + launchSingleTop + restoreState`, hoisted to
  Activity lifetime).
- AMOLED overlay, predictive-back sheet routing, system-bar icon derivation, and startup paths
  (guarded phases, IO-offloaded backup staging) all check out.

---

## Priority fix order (highest leverage first)

1. **R-H1** — remove-after-read data destruction on every `onPause` (user data loss).
2. **L-H3/L-H4/L-H5/L-H6** — one-tap/one-swipe deletions without confirmation (data loss).
3. **L-H1** — persisted library category never restored (state loss on every cold start).
4. **R-H2** — final-chapter history duration lost on exit.
5. **S-H1** — tablet two-pane settings non-functional.
6. **P-H1/P-H2** — reduced-motion accessibility not honored beyond root NavHost, and stale detection.
7. **S-H4 + L-H7** — DataStore/DB write storms on slider drags and category reorder.
8. **S-H2** — tracker import/login cancelled by rotation.
9. Remaining medium/low: dead zones and stale lambdas in reader gestures, insets double-padding in
   sheets, banner theming, missing list keys, hardcoded strings, a11y semantics.

## Cross-cutting opportunities

- Enforce "no raw `tween(n)` outside MotionTokens" and "no hardcoded `Color(...)` outside theme
  tokens" via the existing architecture-test pattern (both violations already exist in-tree).
- Standardize destructive-action UX: one shared confirm-or-undo pattern for all delete/uninstall
  actions (several screens already implement it; at least six do not).
- Standardize transient feedback (snackbar host) and localized error strings — several screens leak
  raw exception text or empty toasts.
- Expose `semantics { customActions }` for gesture-only interactions (reader tap zones, swipe
  actions) to close the TalkBack gap.
