# User Stories

> **Status:** binding intent register. Every story names the executable evidence that proves it, so
> "does this work?" is answered by a named test rather than by opinion.
> **Authority:** [`ROADMAP.md`](../ROADMAP.md) → [`REBUILD_PROGRAM.md`](../REBUILD_PROGRAM.md) →
> this document → [`REBUILD_STATUS.md`](REBUILD_STATUS.md).
> **Evidence levels:** `E2` (JVM/Robolectric) is the current floor for a story to be counted as met.
> `E3`/`E4` are recorded in the status ledger and are not claimed by this file.

## Why this file exists

A passing test proves a function behaves as written. It does not prove the function is the right
function. Stories close that gap by stating, in the user's terms, what outcome was wanted — and then
naming the test that demonstrates it. When a story's test is deleted, the story becomes visibly
unproven rather than silently unreferenced.

Format is deliberately narrow. `E2` means the evidence runs in a JVM test with no device. Anything
requiring a device is recorded in [`REBUILD_STATUS.md`](REBUILD_STATUS.md) instead of here.

---

## US-SEC-001 — Signing credentials are not published

**As a** user installing a build from this project,
**I want** the signing key to be something I control and not something a stranger can copy,
**so that** a build I install is not silently replaceable by anyone with repository access.

**Evidence**

| Claim | Test |
|---|---|
| No build script assigns a literal password to a signing key | `SigningSecretTest` |
| Nightly signing resolves secrets through the indirection, not a literal | `SigningSecretTest` |
| No *unacknowledged* keystore is committed | `SigningSecretTest` |
| Every acknowledged keystore still exists (exception cannot outlive its cause) | `SigningSecretTest` |

**Known gap.** `app/nightly.keystore` is tracked in git and its password was a literal. Both are
recorded in [`REBUILD_STATUS.md`](REBUILD_STATUS.md) as B-017. The story is *not* met: the
remediation is key rotation, which is operational, not a code change. The gate prevents a repeat.

---

## US-SEC-003 — Every privilege the app asks for is a decision, not an accident

**As a** user,
**I want** the app to ask only for permissions someone can explain,
**so that** I can make an informed decision when granting it.

**Evidence**

| Claim | Test |
|---|---|
| No sensitive permission is requested without a written justification | `ManifestPrivilegeTest` |
| No declaration outlives the permission it describes (record cannot drift) | `ManifestPrivilegeTest` |
| Every declaration states both a reason and a removal condition | `ManifestPrivilegeTest` |
| `largeHeap`, if requested, is explicitly justified | `ManifestPrivilegeTest` |

**Why a gate and not a deletion.** Six sensitive privileges remain because the code that needs them
has not been replaced yet. Deleting them now would change the behaviour of legacy code this program
exists to replace. The gate makes each one individually accountable and fails on anything new.

---

## US-ARC-001 — The module graph has a shape

**As a** maintainer,
**I want** the dependency graph to obey layering rules,
**so that** I can change a contract module without discovering an unrelated feature depends on it.

**Evidence**

| Claim | Test |
|---|---|
| `core:domain` depends on no other project (it is the pure contract layer) | `ModuleDependencyGraphTest` |
| Core, source, and presentation modules never depend on a feature module | `ModuleDependencyGraphTest` |
| Feature modules never depend on each other | `ModuleDependencyGraphTest` |
| Presentation modules never depend on feature modules | `ModuleDependencyGraphTest` |
| Nothing depends on `:app` | `ModuleDependencyGraphTest` |
| The graph has no self edges | `ModuleDependencyGraphTest` |

**Why this shape.** The rules are about graph *shape*, not about named modules. Retiring a legacy
edge therefore satisfies the rule automatically instead of requiring the test to be rewritten around
the exception. This is what lets B-006 (`presentation-core` exports `core:data`) be closed by
deleting an edge rather than by editing a test.

---

## US-OPS-003 — The health gate measures the project, not the tooling

**As a** maintainer working in a branch or worktree,
**I want** health ratchets to reflect my project's size,
**so that** a green ratchet means something.

**Evidence:** `HealthRatchetTest` (6 ratchets), all green with **every ceiling unchanged**.

**Why this is a story and not a chore.** The walk originally counted `.kilo/worktrees/<name>/`, which
is a second complete copy of the repository. With one worktree present, `mainSourceFiles` read 2432
against a 1217 ceiling and `projectDependencyEdges` read 332 against 166 — while the real tree
measured exactly 1217 and exactly 166. The ratchet was reporting "an agent is working" as "this
project is twice as big as its budget".

That failure mode is worse than a missing ratchet. A ratchet that fires on routine activity gets
either disabled or re-baselined, and both destroy the signal. So the correct response to a red
ratchet is to establish what actually changed, not to move the number. Four of the four apparent
regressions here were measurement artifacts; the two that survived scrutiny were the published
keystore and the undeclared permissions, both of which became stories above.

---

## How to use this file

1. A new behaviour gets a story before it gets a test name.
2. Every story names its evidence. A story with no runnable evidence is an intention, not a claim.
3. When a test is deleted, the story loses its evidence and must be re-marked, not left dangling.
4. Stories are the unit of intent. Ledger rows are the unit of status. Neither substitutes for the
   other: a row can be `CODE_COMPLETE` at `E2` while its story is unmet, and that is the normal state
   of this program.
