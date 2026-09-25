# Reconstruction Execution Guide

This guide tells a human developer or coding agent how to continue the program without guessing. It is intentionally procedural. If a step cannot be completed, stop and record the blocker instead of inventing a substitute.

## 1. First ten minutes

1. Read [`../ROADMAP.md`](../ROADMAP.md).
2. Read [`REBUILD_PROGRAM.md`](REBUILD_PROGRAM.md).
3. Read [`REBUILD_STATUS.md`](REBUILD_STATUS.md).
4. Read the relevant ADR files in [`adr/`](adr/).
5. For reader/media work, read [`READER_ARCHITECTURE.md`](READER_ARCHITECTURE.md), [`READER_GESTURE_CONTRACT.md`](READER_GESTURE_CONTRACT.md), [`DOCUMENT_VIEWPORT_CONTRACT.md`](DOCUMENT_VIEWPORT_CONTRACT.md), and [`MEDIA_PIPELINE_CONTRACT.md`](MEDIA_PIPELINE_CONTRACT.md).
6. For navigation/motion work, read [`NAVIGATION_CONTRACT.md`](NAVIGATION_CONTRACT.md) and [`MOTION_NAVIGATION_CONTRACT.md`](MOTION_NAVIGATION_CONTRACT.md).
7. For fixture work, read [`FIXTURE_MANIFEST.md`](FIXTURE_MANIFEST.md), then use the executable `ReaderFixtureCatalog` as the identity registry. Do not invent new fixture IDs in individual tests.
8. For source/search/discovery work, read [`SOURCE_DISCOVERY_ARCHITECTURE.md`](SOURCE_DISCOVERY_ARCHITECTURE.md), [`SOURCE_DISCOVERY_EXECUTION.md`](SOURCE_DISCOVERY_EXECUTION.md), and the `SRC-000` artifacts under [`source/`](source/).
9. For documentation changes, read [`DOCUMENTATION_GOVERNANCE.md`](DOCUMENTATION_GOVERNANCE.md).
10. Run:

```powershell
git status --short
git log -5 --oneline --decorate
./gradlew projects
```

11. Confirm the working tree is clean. If not, identify whether changes belong to the current task.
12. Select exactly one `NOT_STARTED` task whose prerequisites are `VERIFIED`.
13. If the task is `TST-001B`, use the `ReaderFixtureCatalog` as the source of fixture identity and do not add production code.
14. Create a branch named `program/<phase-number>-<task-id>-<short-slug>`.
15. Add the task ID to the branch and commit subject.
16. Do not combine unrelated cleanup into the first commit.
17. Before source/search work, read the source contracts and classify the change as target contract, adapter boundary, or deletion.

## 2. Task selection

A task is executable only when:

- its phase prerequisites are `VERIFIED`;
- its ADR dependencies are accepted;
- its required test fixture exists;
- its acceptance evidence is named;
- no other task owns the same files or state model.

If two tasks appear ready, choose the one that unblocks the most downstream tasks. If that is unclear, update the status ledger with the dependency and stop for a decision.

## 3. Before writing code

Write a short implementation note in the task commit or handoff containing:

```text
Task:
Problem:
Invariant:
State owner:
Resource owner:
Event/command path:
Effect path:
Failure/cancellation path:
Tests:
Not doing:
```

Example:

```text
Task: RDR-003
Problem: viewport and pager compete for the same pointer stream
Invariant: one viewport owns a gesture after the first pointer
State owner: GestureArbiter
Resource owner: viewport coroutine scope
Event path: PointerEvent -> GestureState -> TransformCommand
Failure path: pointer cancellation restores fit ownership
Tests: pure transition matrix + E3 multi-pointer fixture
Not doing: document tile rendering or navigation redesign
```

## 4. Implementation order inside a task

Use this order unless the task explicitly says otherwise:

1. Define the contract/type.
2. Write pure tests.
3. Implement the pure policy.
4. Add the Android/Compose adapter.
5. Add resource lifecycle and cancellation.
6. Add instrumentation/screenshot coverage.
7. Add observability.
8. Format and run gates.
9. Update status and handoff.
10. Commit.

Do not begin with a Composable and then invent the state model from its callbacks.

## 5. Required local gates

For every Kotlin/Gradle change, at minimum run:

```powershell
./gradlew spotlessKotlinApply
./gradlew spotlessCheck
./gradlew testDebugUnitTest
./gradlew :app:compileDebugKotlin
./gradlew :app:lintDebug
```

Also run `git diff --check`.

For resource, manifest, or navigation changes:

```powershell
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```



## 6. Device gates

No device-dependent claim is complete without explicit evidence. Before device tests:

```powershell
adb devices
adb shell getprop ro.build.version.sdk
adb shell wm size
adb shell wm density
```

Record device/API/ABI/build. Run at minimum:

- compact phone;
- target API;
- one low-memory device;
- expanded/foldable layout if supported;
- process recreation;
- rotation;
- offline/network failure;
- back/predictive-back;
- rapid repeated gesture input.

For each failure capture:

```text
build SHA:
device/API:
fixture:
steps:
expected:
actual:
logcat:
screenshot/video:
status:
```

## 7. Test design rules

- A pure unit test must not require an Activity, Compose runtime, network, or real clock when avoidable.
- An Android test must exercise the actual pointer/lifecycle path when the bug is Android-specific.
- A screenshot test must use deterministic fixtures and stable clock/seed data.
- A benchmark must record device, build, mode, and metric; a raw duration without context is not evidence.
- A test that passes because behavior is skipped is not passing.
- Do not weaken an assertion to make a flaky test green; fix ownership or isolate the flake.
- Do not add `Thread.sleep` to production code.

## 8. Failure and cancellation rules

Every async operation must answer:

- What cancels it?
- What happens if the input disappears?
- What happens if the same retry is requested twice?
- What happens if the Activity/owner is destroyed?
- What partial resource can remain?
- How is it recovered on the next attempt?

Every cache write must answer:

- What is the identity?
- Is replacement atomic?
- Is the old valid copy preserved on failure?
- How is corruption detected?
- What evicts it?

Every navigation transition must answer:

- What is the source item identity?
- What is the destination item identity?
- What happens if bounds are invalid?
- What happens on predictive cancellation?
- What is reduced-motion behavior?

## 9. Commit rules

Use one coherent concern per commit:

```text
fix(reader): enforce one viewport gesture owner
test(reader): cover pinch ownership transitions
refactor(media): separate page source identity from decode plan
fix(navigation): remove nested main navigation owner
docs(program): record baseline and phase gate
```

A commit may contain implementation, tests, and the status update for the same task. It may not contain unrelated formatting, dependency upgrades, or speculative refactors.

Never rewrite or amend a pushed shared commit merely to hide a mistake. Add a corrective commit with a clear explanation.

## 10. Handoff format

Every task handoff must contain:

```markdown
## Task
`TASK-ID  title`

## Status
`CODE_COMPLETE | DEVICE_VERIFIED | BLOCKED | ACCEPTED_DEBT`

## Changed
- Files and responsibilities changed.

## Evidence
- Commands and results.
- Device/API details if applicable.
- Test/benchmark/screenshot paths.

## Decisions
- ADR IDs followed or created.

## Remaining risks
- Explicit unresolved risks.

## Next task
- The single recommended next task ID.
```

An AI or new contributor must be able to continue from the handoff without reading the entire conversation.

For media changes run affected data and reader suites first. For database changes run all Room migration and backup tests. For CI/build changes run the complete `./gradlew check` task where supported.

## 11. Documentation changes

Before editing documentation:

1. Read [`DOCUMENTATION_GOVERNANCE.md`](DOCUMENTATION_GOVERNANCE.md) and the authority order.
2. Update the current contract and status ledger before deleting or archiving an old document.
3. Search every repository reference to the old file, including test comments and changelogs.
4. Do not delete a document in a production-behavior commit.
5. If a document contains useful evidence but is not current guidance, move it to a clearly historical state or delete it only after Git history and replacement evidence are verified.
6. Run the documentation link/index check and `git diff --check`.

A documentation cleanup commit must state which documents were retained, marked historical, archived, or deleted and why.
