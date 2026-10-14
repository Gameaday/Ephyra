# Upstream Mihon 0.21.1 → Ephyra adoption plan

> **Status:** PROPOSED. This is a plan, not a contract. It records what upstream
> [`mihonapp/mihon` `v0.21.1`](https://github.com/mihonapp/mihon/releases/tag/v0.21.1) changed
> relative to `v0.21.0`, which of those changes Ephyra should adopt, mirror, or explicitly decline,
> and the concrete work each decision implies. Execute the workstreams in priority order; each one
> lands with its own tests and its own ledger entry.
>
> **Authority.** Where this document and a subsystem contract disagree, the contract wins and this
> document is the defect. The extension boundary remains owned by
> [`EXTENSION_COMPATIBILITY.md`](EXTENSION_COMPATIBILITY.md).

## 1. Scope and sources

| Source | What it gives us |
|---|---|
| Release note `v0.21.1` (2026-10-12) | Headline decision on Tachiyomi backups/1.4 extensions; the 0.21.1 fix list; 0.21.1 removals and under-the-hood items; the recap of 0.21.0 |
| Compare `v0.21.0...v0.21.1` (15 commits) | The exact commits and diffs behind the 0.21.1 fixes |
| Upstream `CHANGELOG.md` on `main` | PR/commit numbers behind the recap items, so each can be read individually |

Ephyra is at `versionName = "0.21.0"` (`app/build.gradle.kts`), i.e. it tracks the same release line
as upstream. This plan therefore covers the delta *into* `0.21.1` and flags the `0.21.0` recap items
that are still open here.

## 2. The headline upstream decision

Upstream `v0.21.1` is the **last major version to support Tachiyomi's backup format (`.tachibk`)
and Tachiyomi's 1.4 extensions**. After it:

- Mihon will not restore `.tachibk` backups at all (the only migration path is "restore on 0.21.1,
  then update").
- Mihon drops compatibility with extensions made for Tachiyomi (1.4 extensions).

Ephyra's position is **already stricter on extensions and already interoperable on backups**:

- `ExtensionLoader.SUPPORTED_LIB_VERSIONS = listOf(1.6)` — 1.4 and 1.5 are *already refused*. Ephyra
  has effectively completed the extension half of the decision.
- Ephyra writes gzip-wrapped protobuf `.tachibk` (`BackupCreator.createBackup`), which is **exactly
  what Mihon 0.21.1 itself writes and reads** (`BackupCreator` uses `sink().gzip()`;
  `BackupDecoder` reads gzip or raw protobuf; there is no `ZipOutputStream` anywhere in mihon). So
  there is **no interop break today**. What upstream is dropping is the *Tachiyomi-format* restore
  path, and its replacement format is not yet on upstream `main`, so Ephyra cannot adopt it yet.

So the headline maps to one workstream — finish removing the now-unreachable 1.4/1.5 ABI surface
(§3 WS1) — plus a **watch item** for the replacement backup format (§3 WS3).

## 3. Workstreams

Priority: **P0** correctness/security/interop, **P1** robustness, **P2** parity/polish,
**P3** feature adoption.

### WS1 — P0 — Finish the 1.4/1.5 ABI removal (`source-api`)

**Upstream:** the 0.21.1 note says the *next* version drops 1.4 extensions. **Verified correction
(2026-10-12):** upstream `main`'s `source-api/.../HttpSource.kt` **still ships** `rx.Observable`,
`asObservableSuccess`, `awaitSingle`, `imageUrlRequest` and `imageUrlParse`, and upstream
`CHANGELOG.md` `[Unreleased]` does **not** mention removing them. So this is **not** a mirror of a
landed upstream change — it would be a deliberate Ephyra divergence, and it must be decided as one.

**Ephyra now:**

- `ExtensionLoader.SUPPORTED_LIB_VERSIONS = listOf(1.6)` — only 1.6 loads, so the 1.4/1.5 surface is
  unreachable.
- Yet `source-api` still declares the dead chain: `CatalogueSource.fetchPopularManga/…`,
  `Source.fetchMangaDetails/fetchChapterList/fetchPageList`, `HttpSource`'s `Observable` overrides,
  `ParsedHttpSource.imageUrlParse`, and `PageImageAddress`'s per-builder field selection.
- `rxjava` is an `api` dependency in **both** `source-api/build.gradle.kts` and
  `core/common/build.gradle.kts` (`gradle/libs.versions.toml`, `rxJava = "1.3.8"`).
- `EXTENSION_COMPATIBILITY.md` is **stale and self-contradictory**: it states `ExtensionLoader`
  accepts "1.4, 1.5 and 1.6", and justifies keeping the dead chain "while we support 1.4 and 1.5" —
  neither is true of the code.

**Work**

1. Remove `fetchImageUrl`, `imageUrlRequest`, `imageUrlParse`, and every `Observable` method from
   `Source`/`CatalogueSource`/`HttpSource`/`ParsedHttpSource` in one change.
2. Drop the `rxjava` dependency from `source-api` and `core/common` and the version catalog entry.
3. Reduce the capability probe to `getImageUrl` alone; drop `PageImageAddress`'s per-builder field
   selection.
4. Rewrite the "Supported versions" and "When upstream finishes the removal" sections of
   `EXTENSION_COMPATIBILITY.md` to state 1.6-only and record the removal as done.
5. Add structural gates: no `rx.` / `Observable` token in `source-api/src/main`; no
   `SUPPORTED_LIB_VERSIONS` entry other than 1.6; the model-field gate keeps passing.

**Acceptance:** `source-api` and `core/common` compile with no RxJava on the classpath; the existing
`ExtensionModelCompatibilityTest`, `PageListFreshnessTest` and `MangaDexOpaqueImageUrlContractTest`
stay green; the probe still reports the real MangaDex contract.

**Risk:** an extension that linked against a removed symbol. Mitigated by the loader refusing
anything below 1.6 at load time (a clear, reported refusal) and by upstream making the identical
removal.

**Status:** the `EXTENSION_COMPATIBILITY.md` correction has landed. The code removal is **not done,
and should not be done blind.** Verified blast radius: it removes members from `Source.kt`,
`CatalogueSource.kt`, `HttpSource.kt` and `ParsedHttpSource.kt`; rewrites `getImageUrl`'s default
(currently `fetchImageUrl(page).awaitSingle()`, so the two cannot be removed independently); touches
the 400-line safety-critical `PageImageAddress.kt` probe; deletes `OkHttpExtensions.asObservable*`
and both `RxCoroutineBridge` files; drops `rxjava` from `source-api`/`core/common` and the catalog;
and rewrites at least six tests that exercise the removed members (`SourceApiContractTest`,
`SourceBridgeTest`, `HttpSourceImageUrlRequestTest`, `RxCoroutineBridgeTest`,
`MangaDexOpaqueImageUrlContractTest`, `HttpPageLoaderUrlResolutionTest`). It needs a compiler and a
deliberate divergence decision — not a copy of a landed upstream change.

### WS2 — P0 — Narrow what extensions can reach (`Injekt` bridge)

**Upstream (#3965, "Limited what extensions can access from the app"):** the Injekt container an
extension sees is restricted to **Application, Context, Json, ProtoBuf, XML, NetworkHelper,
JavaScriptEngine**. `ExtensionManager`, `CoverCache`, `PreferenceStore` and `TrackPreferences` were
deliberately moved out.

**Ephyra now** (`app/.../di/CoreContainerInitializer.kt`) registers a **wider** set — Context,
Application, SharedPreferences, NetworkHelper, OkHttpClient, PreferenceStore, BasePreferences,
CoverCache, SourceManager, Json, XML — and is **missing two upstream allows**, ProtoBuf and
JavaScriptEngine. `ExtensionBridgeContractTest` asserts only a subset of this is resolvable; it does
not assert that *nothing else* is.

**Work**

1. Reduce the registered allowlist to upstream's set. Anything an extension is not meant to hold
   must not be resolvable from `Injekt` (Context/Application keep their existing role).
2. Add `ProtoBuf` and `JavaScriptEngine` so extensions that resolve them do not crash.
3. Extend `ExtensionBridgeContractTest` into a two-sided contract: the allowed types resolve, and a
   named set of internal types (CoverCache, SourceManager, PreferenceStore, BasePreferences,
   SharedPreferences) **fails** to resolve.
4. Cross-reference the allowlist from `EXTENSION_COMPATIBILITY.md` so the next generation changes it
   in one place.

**Acceptance:** the allowlist test passes both ways; an extension relying only on the upstream set
loads and runs.

**Status:** the narrowing and the two-sided contract test have landed. `JavaScriptEngine` is the one
upstream allow that could not be added because the type does not exist in Ephyra yet; that is the
remaining divergence.

### WS3 — Watch — Replacement backup format (the `.tachibk` deadline)

**Upstream:** 0.21.1 is the last version that *restores* Tachiyomi's `.tachibk`. Its replacement
format is not yet on upstream `main`, so its container is currently undetermined.

**Ephyra now:** `BackupCreator.createBackup` writes gzip-wrapped protobuf with a `.tachibk` filename
— the *same* container Mihon 0.21.1 writes and reads — and `BackupDecoder` additionally reads raw
protobuf and a ZIP container. **Correction:** an earlier draft of this plan claimed Ephyra wrote a
legacy container Mihon would refuse; that was wrong. Upstream's own writer is `sink().gzip()`
(`BackupCreator`), its decoder reads gzip or raw protobuf, and `ZipOutputStream` does not appear
anywhere in mihon. There is no interop break today.

**Work (only when upstream defines the new format)**

1. Do **not** change the writer speculatively. Monitor upstream for the new format's container and
   extension, then adopt it while keeping the `.tachibk` writer/reader for backward compatibility.
2. Keep the multi-format **reader** indefinitely: Ephyra's own historic backups are gzip, so this is
   a preservation requirement, not a nicety.
3. Add a checked-in interop fixture: a Mihon-0.21.1-produced `.tachibk` restores here, and one Ephyra
   produces restores in Mihon 0.21.1.
4. Revisit `BackupDecoder`'s ZIP branch: confirm what actually writes that container before relying
   on it, since upstream does not.

**Acceptance:** a backup written by Ephyra restores in Mihon 0.21.1, and vice versa; the reader
still accepts every format Ephyra has ever written.


### WS4 — P0 — Extension installer concurrency

**Upstream (`07b9fb69`, `ae749bb4`):** a cancellation from a non-main thread crashed the installer;
and cancelling/updating one extension could cancel another. Fixed by guarding the queue, making the
waiting-install handoff atomic, and cancelling **only** the entry that was asked for.

**Ephyra now** (`app/.../extension/util/ExtensionInstaller.kt`): `activeJobs: MutableMap<String, Job>`
and `activeSteps: MutableMap<Long, MutableStateFlow<InstallStep>>` are plain maps mutated from IO
coroutines, `downloadAndInstall` (caller thread) and `cancelInstall` (any thread). The step is keyed
by `extension.pkgName.hashCode().toLong()` — a collision would let one install drive another's step.

**Work**

1. Guard both maps (synchronised access or concurrent collections) and make the
   `cancelInstall`/`activeSteps` mutations race-free.
2. Guarantee cancellation targets exactly the requested package; a finished/failed install must not
   clear another's step.
3. Replace the `hashCode()` step key with a collision-free identity.
4. Add concurrency tests: cancel-while-installing, cancel-one-of-many, install-while-installing.

**Acceptance:** stress test of concurrent install/cancel leaves each extension's step consistent.

**Status:** landed. A dedicated concurrency stress test is still to be added.

### WS5 — P0 — Survive a WebView renderer death

**Upstream (`649fc83f`):** added `onRenderProcessGone` to the WebView screen and to
`CloudflareInterceptor`, so a dead renderer no longer kills the app; `stopLoading`/`destroy` are
guarded when the renderer is gone; a user-facing string was added.

**Ephyra now:** no `onRenderProcessGone` anywhere in the tree; `CloudflareInterceptor` calls
`stopLoading()`/`destroy()` unconditionally (lines 153–154).

**Work**

1. Override `onRenderProcessGone` in Ephyra's webview screen(s) and `CloudflareInterceptor`; return
   `true`, log the detail, notify the user, and navigate away/close the flow.
2. Skip `stopLoading`/`destroy` when the renderer is gone (only `destroy` is legal).
3. Add the "WebView stopped working…" string to `core/common` resources.
4. Add a test around the interceptor's gone-renderer branch.

**Acceptance:** killing the WebView renderer process leaves the app running with a visible message.

**Status:** landed. Device verification is still to be recorded.

### WS6 — P1 — Migration robustness and orphan cleanup

**Upstream (`9dbb7148`, `010ac71a`):** a throwing migration no longer stalls the updating screen
forever — each migration is isolated, failures are logged with name+version, and the database open
is surfaced uncaught so the crash screen reports it. A DB migration also deletes rows orphaned by
deleted entries. Separately, the "always update uninitialized library entries" bypass of smart-update
restrictions was **reverted**.

**Ephyra now:**

- `MigrationJobFactory` runs `migration(migrationContext) || prev` with **no** try/catch, so one
  failure cancels the rest of the chain and surfaces only as a generic `false`. `MainActivity` does
  bound it (`withTimeoutOrNull` + catch → `StartupFailureActivity`), so it cannot hang forever, but
  it loses the precise failure.
- Ephyra uses Room, and has **no `.sqm`/`.sq` migration files**, so the duplicate-entry index and the
  `favorite_at` fallback have no direct equivalent — but the *orphan-cleanup* intent still applies to
  any Ephyra schema that can hold rows pointing at deleted parents.
- `LibraryUpdateJob` already uses `fetchDetails = autoUpdateMetadata` and has **no**
  `!manga.initialized` bypass — i.e. Ephyra already matches the reverted (correct) upstream state.

**Work**

1. Wrap each migration in try/catch (rethrow `CancellationException`), log name+version, return
   `false` so one failure does not cancel the chain.
2. Ensure a failed database open surfaces through `StartupFailureActivity` with the real trace
   (Ephyra's `GlobalExceptionHandler` already exists — wire the open path to it if not already).
3. Audit the Room schema for orphaned rows after a delete and add a migration/cleanup if any exist.
4. Add a regression test pinning the smart-update behaviour (uninitialized entries obey restrictions)
   so the upstream regression cannot be reintroduced here.

**Acceptance:** a deliberately failing migration leaves the app usable and reports the failure; the
smart-update test fails if the bypass returns.

**Status:** migration isolation landed. The orphan-row audit and the smart-update regression test are
still open.

### WS7 — P1 — Reader pager page stability (verify + lock)

**Upstream (`51b61a36`):** the pager keeps its page when chapters are added before first layout —
the RTL reader used to land in the next chapter.

**Ephyra now:** `ComposePagerReader` already does the equivalent — it captures `viewer.currentPage`,
computes `items.indexOf(cur)`, and re-seeks when it differs (lines ~141–145). Ephyra is **already
aligned**; the gap is test coverage, not code.

**Work**

1. Add a regression test: in RTL, resume a chapter while the next chapter's pages are prepended and
   assert the visible page and saved progress stay on the same page.
2. Confirm the "resume opens next chapter partway through" scenario is covered by that test.

**Status:** landed as a **structural gate**, not the behavioural RTL test the plan called for.
`ComposePagerReader` already re-seeks to the current item's new index whenever the item list changes
(`LaunchedEffect(items)` → `items.indexOf(viewer.currentPage)` → `scrollToPage`), which is the
behaviour upstream fixed; the realignment lives inline in a `@Composable`, so
`PagerRealignmentStructuralTest` asserts its shape (with matcher self-tests) rather than faking a
`PagerState`, a `PagerViewer` and a reader activity. The on-device RTL resume remains unverified.

### WS8 — P1 — Locale-safe chapter-number formatting

**Upstream (`8457fb91`):** chapter refresh failed with an Arabic (or other non-ASCII-digit) app
locale because `DecimalFormat` used locale-default symbols.

**Ephyra now:** `ChapterRecognition` does not use `DecimalFormat` at all (it parses with regex +
`toDouble()`), so the specific bug is likely absent. But `lowercase()` is called without a locale, and
other number-formatting paths (`ChapterNumber`, chapter display, `BackupCreator`'s
`SimpleDateFormat(Locale.ENGLISH)`) should be audited for default-locale dependence.

**Work**

1. Audit chapter-number and backup-date paths for `DecimalFormat`/`String.format`/`lowercase()` with
   an implicit locale; pin to `Locale.ROOT`/`Locale.ENGLISH` where the output is data, not UI.
2. Add a test that runs under an Arabic locale and asserts ASCII digits and stable chapter matching.


**Status:** landed. Chapter-number parsing, `TitleNormalizer`, alternative-title dedup, stable-id
hex, `dHashToHex`, URL control-char escaping and Kitsu score formatting now pin `Locale.ROOT`;
`LocaleIndependenceTest` runs the parsing and normalization inputs under `ar-EG` and `tr` and asserts
the results match `Locale.ROOT`. `BackupCreator`/`KitsuDateHelper` already used `Locale.ENGLISH`.

### WS9 — P2 — Startup and tablet polish

**Upstream (`abb126ad`, `c279291e`):** some Samsung Android 9–11 builds throw `SecurityException`
from `reportFullyDrawn`, crashing on launch; and the start/resume control went under the taskbar on
tablets.

**Ephyra now:** `reportFullyDrawn` is never overridden (`grep` finds no call/override); Ephyra's
`MangaScreen` has no `bottomBar`/`floatingActionButton`, so the exact tablet FAB bug may not exist.

**Work**

1. Add a defensive `reportFullyDrawn` override in `MainActivity`/`BaseActivity` that catches
   `SecurityException` and logs — cheap, and protects against a framework/library call.
2. Verify the reader start/resume control and any FAB against the navigation-bar/taskbar insets on a
   tablet; apply `systemBars`/`displayCutout` padding if it can be occluded.

### WS10 — P2 — Retire obsolete advanced settings

**Upstream (`00fb597ae9` / #3786):** removed the "Use legacy decoder for long strip reader" setting,
the custom image decoder, and the "Custom hardware bitmap threshold" / "Custom display profile"
advanced settings.

**Ephyra now:** no "legacy decoder" setting exists (good), but `core/common` still ships
`pref_hardware_bitmap_threshold` ("Custom hardware bitmap threshold") and ICC display profiles are
already retired (`ReaderActivity` comment).

**Work**

1. Remove the obsolete hardware-bitmap-threshold setting and its plumbing, matching upstream.
2. Confirm no dead decoder code remains in the long-strip path.

**Status:** landed. The `pref_hardware_bitmap_threshold` preference, its advanced-settings row, the
`GLUtil.CUSTOM_TEXTURE_LIMIT_OPTIONS` list and the App wiring are removed; `ImageUtil`'s texture
threshold is applied directly from `GLUtil.DEVICE_TEXTURE_LIMIT`, which is the value the preference
already defaulted to, so behaviour is unchanged. ICC display profiles were already retired.

### WS11 — P3 — Extension trust / content-warning model (0.21.0 recap)

These landed in the 0.21.0 recap and are worth a follow-on phase rather than the 0.21.1 sprint:

- **Three-tier content warnings (Safe / Mixed / 18+) (#3951)** plus "keep loading installed
  extensions regardless of the allowed content warnings" (#3952). Ephyra still gates on a single
  NSFW bit and returns `NSFW_NOT_ALLOWED`; it already reads `tachiyomix.contentWarning` but flattens
  it to a boolean.
- **Install/update only from a repo whose signing key matches (#0938411)** and show which repo an
  extension comes from. Ephyra installs privately and has `SourceTrust`/`TrustExtension`; evaluate
  adding repo-key matching.
- **Show installed extensions that failed to load, with reason and stack trace (#3953).** Ephyra
  already models `LoadFailureReason` and renders a message — extend to a copyable trace.
- **Load extensions off the main thread (#3788) and wait before listing sources (#3841); apply
  trust/repo/content changes without restart (#3954).**

### WS12 — P3 — Feature opportunities (0.21.0 recap, separate track)

Listed for completeness; none is a correctness fix: Comick tracker support; "Load ahead only on
Wi-Fi"; skip-an-app-update option; an in-app data-update progress screen; `id:` search in trackers;
refresh buttons in tracker settings; local-source `ComicInfo.xml` chapter dates.

## 4. Ordering and dependencies

1. **WS1, WS2, WS4, WS5** are independent and can land in any order.
2. **WS3** is a watch item, not an implementation: no code changes until upstream publishes the
   replacement format.
3. **WS6, WS7, WS8** are follow-ups that touch data/reader paths.
4. **WS9, WS10** are small parity items.
5. **WS11, WS12** are a separate phase; do not start until P0/P1 are green.

## 5. Verification and evidence

- `ktlint` and the module unit-test suites stay green (the tree is currently baseline-clean).
- Structural gates added by WS1/WS2 fail if the removed surface or the wider Injekt allowlist returns.
- Interop fixtures (WS3) are checked in both directions against Mihon 0.21.1.
- Concurrency stress tests (WS4) and renderer-death tests (WS5) are recorded as evidence.
- Each workstream appends to `REBUILD_STATUS.md` and, where behaviour changes, to `CHANGELOG.md`
  under `[Unreleased]`.

## 6. Risks and non-goals

- **Risk:** removing the 1.4/1.5 ABI breaks a still-used extension. Accepted, and identical to
  upstream's decision; the loader already refuses those versions, so the exposure is the code, not
  the users.
- **Risk:** narrowing the Injekt allowlist breaks an extension that reached a now-removed type.
  Accepted in favour of the smaller attack surface; the allowlist test makes the set explicit.
- **Non-goal:** mirroring upstream file paths or class names. Ephyra adopts the *decision* and the
  *contract*, not the layout.
- **Non-goal:** dropping Ephyra's legacy-gzip backup *reader*. The deadline is on what upstream will
  restore, not on what Ephyra must keep reading for its own users.

