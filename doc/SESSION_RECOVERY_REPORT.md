# Session recovery report — crashed sandbox (upstream adoption work)

> **Prepared:** 2026-10-13, from a fresh sandbox.
> **Question asked:** "The last sandbox crashed without finishing fixing upstream changes.
> Find the branch and its progress and report."

## 1. Which branch

The crashed sandbox's work-in-progress is on **`origin/cline/8ppdf9b3`**.

| Fact | Value |
|---|---|
| Branch | `cline/8ppdf9b3` (tip `87f67de43a73c3e58a8b36881104c041160d30be`) |
| Base | `main` @ `01354968cb` — "fix(build): repair remaining baseline syntax breaks; make the tree pass ktlint" |
| Relationship to `main` | **0 behind, 9 ahead** — the only `cline/*` branch based directly on the current `main` |
| Last commit | 2026-10-12 14:51:40 +0000 |
| Pull request | **none** — no PR has ever been opened for this branch |
| Working tree | clean; every commit is pushed to origin |

Note: the branch named in this session's instructions, `cline/7yhmdxj0`, **did not exist**; it is this
session's own backup branch (derived from this session's task id), not the crashed sandbox's. The
other `cline/*` branches (`5fckjgty`, `g9ap9y10`, `tj9m33yf`) are older (Oct 4–6) and diverge from
`main` by ~9,200 commits, so they are unrelated prior work.

## 2. What the branch is doing

It is the **adoption of upstream Mihon `v0.21.1`** into Ephyra, planned and executed as workstreams
`WS1`–`WS12` in `doc/UPSTREAM_MIHON_0_21_1_ADOPTION_PLAN.md`. The 9 commits, oldest to newest:

```
818799812e  docs: add Mihon 0.21.1 upstream adoption plan
7665705cf9  fix(webview): survive a WebView renderer death (WS5)
38ef522925  fix(extension): make installer cancellation identity-safe (WS4)
00724c41e8  docs: correct backup-format claim in Mihon 0.21.1 plan (WS3)
46f0dfaca1  fix(extension): narrow the extension Injekt allowlist (WS2)
5e16b4f4e1  fix(startup): guard Samsung reportFullyDrawn crash; correct extension doc
5cb5cfbd73  fix(migration): isolate a failing migration instead of cancelling the chain (WS6)
5fbad295f9  docs: record WS6 status in the adoption plan
87f67de43a  docs: reclassify WS1 after verifying upstream still ships the Rx chain
```

Code touched: `CoreContainerInitializer.kt`, `ExtensionManager.kt`, `ExtensionInstaller.kt`,
`MainActivity.kt`, `MigrationJobFactory.kt`, `ExtensionBridgeContractTest.kt`,
`CloudflareInterceptor.kt`, `WebViewScreenContent.kt`, `core/common` strings, plus the plan doc,
`EXTENSION_COMPATIBILITY.md`, `doc/README.md` and `CHANGELOG.md`.

## 3. Progress by workstream

| WS | Pri | Scope | Status |
|---|---|---|---|
| WS1 | P0 | Remove dead 1.4/1.5 ABI (RxJava chain) from `source-api` | **Deferred / reclassified.** Docs correction landed. Code removal NOT done — and upstream `main` still ships the `rx.Observable` chain, so this is a deliberate Ephyra divergence needing a compiler + product decision, not a mirror. Blast radius recorded (6+ test files, `PageImageAddress.kt`, both `RxCoroutineBridge` files). |
| WS2 | P0 | Narrow extension `Injekt` allowlist to upstream's set | **Landed**, two-sided contract test included. One divergence: `JavaScriptEngine` allow could not be added because the type does not exist in Ephyra yet. |
| WS3 | Watch | Replacement `.tachibk` backup format | **Watch item.** Doc corrected (Ephyra's gzip `.tachibk` already interops with Mihon 0.21.1; no code change until upstream publishes the new format). |
| WS4 | P0 | Extension installer concurrency / identity-safe cancel | **Landed.** Still open: a dedicated concurrency stress test. |
| WS5 | P0 | Survive a WebView renderer death | **Landed.** Still open: on-device verification. |
| WS6 | P1 | Migration robustness (isolate a failing migration) | **Partially landed.** Migration isolation done; orphan-row audit and smart-update regression test still open. |
| WS7 | P1 | Reader pager page stability | **Not started.** Code already aligned; only a regression test is missing. |
| WS8 | P1 | Locale-safe chapter-number formatting | **Not started.** Audit + Arabic-locale test pending. |
| WS9 | P2 | Startup / tablet polish | **Partially landed.** Samsung `reportFullyDrawn` guard done; tablet inset verification pending. |
| WS10 | P2 | Retire obsolete advanced settings | **Not started.** |
| WS11 | P3 | Extension trust / content-warning model | **Not started** (separate phase). |
| WS12 | P3 | Feature opportunities | **Not started** (separate track). |

## 4. Where it stopped / what remains

The sandbox was working through the P0 items and crashed after the last docs commit
(`87f67de43a`, "reclassify WS1"). Everything it completed is committed and pushed; there is **no
uncommitted work to recover**. The concrete unfinished items are:

1. **WS1** — the `source-api` Rx/1.4-ABI removal itself (deferred pending a build-validated edit and
   a divergence decision).
2. **WS4** — concurrency stress test for the installer.
3. **WS5** — record on-device renderer-death verification.
4. **WS6** — orphan-row audit and smart-update regression test.
5. **WS7, WS8, WS9(tablet), WS10** — untouched P1/P2 items.
6. **WS11, WS12** — P3, explicitly deferred to a later phase.
7. No PR was opened, so the branch has not been reviewed or merged.

## 5. How this was determined

- `git ls-remote` / `git fetch` of all `refs/heads/cline/*`; commit dates and
  `git rev-list --left-right --count main...<branch>` showed `cline/8ppdf9b3` as the only branch based
  on the current `main` (0 behind, 9 ahead) and the most recently touched (Oct 12).
- `doc/UPSTREAM_MIHON_0_21_1_ADOPTION_PLAN.md` on that branch carries an explicit `**Status:**`
  line per workstream; those are quoted above.
- `gh pr list --head cline/8ppdf9b3` returns empty.
- No session log for the crashed sandbox survived (`.cline-home/data/sessions/` contains only this
  session's live log), so the branch and its documents are the sole record of that sandbox's work.
