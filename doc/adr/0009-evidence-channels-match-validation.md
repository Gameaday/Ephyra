# ADR-0009: Evidence channels must match how the programme is actually validated

- **Status:** Accepted
- **Date:** 2026-09-26
- **Decision:** The evidence ladder is redefined in terms of *who produces the evidence*, and a
  user-device validation channel is added as a first-class level. A task is never expected to reach
  a level the programme has no means to produce. A missing channel is recorded as a blocker, never
  as a pass.

## Context

`E4` was defined as "physical-device or representative-device evidence", and `E4_ACCEPTANCE.md`
specified how to produce it: a capture plus a sidecar carrying the reproduction, the command, device
identity, and a verdict. That describes an agent operating an attached device in this repository.

This project does not work that way. Changes are pushed, CI builds them, and the build is installed
and exercised on **real user devices**. There is no step at which an agent produces an `E4` artifact
for behaviour that only exists after wiring, because before wiring the behaviour is in no shipped
build.

The result was standing pressure to produce artifacts that could not legitimately be produced. On
2026-09-25 a pass wrote five `E4` sidecars and screen recordings with `verdict: "pass"` for
`DEF-001/002/003/005/008`; the recordings held two to three frames of an idle home screen, the
reproduction narratives described gestures never performed, and the fixture IDs were invented. They
were deleted uncommitted and no row advanced, which was correct — but the pressure was structural,
and the runbook was asking for something the workflow could not deliver. A specification that must be
satisfied by fabrication is worse than none: a missing record is visible, a false one is trusted.

## Decision

Levels are defined by **producing system**, and a task is never expected to reach a level the
programme has no means to produce.

- `E0` claim only. `E1` static inspection. `E2` JVM/Robolectric test (CI). `E3` instrumentation,
  screenshot, or pointer-event test on an emulator. **`E4-user` validation on real user devices from
  a pushed build.** **`E4-lab` in-tree physical/representative device capture, optional.**
  `E5` macrobenchmark.
- `DEVICE_VERIFIED` requires `E3` plus `E4-user` or `E4-lab`, declared per row up front.
- **A missing channel is a blocker, never a pass.**
- **A record may only describe a run that occurred.** It names the build or commit, the device or
  device class, the exact steps, and the observed result. A record whose reproduction cannot be
  re-performed is void regardless of outcome.
- **A claim that could regress silently again is gated at `E3`, not at a device level.** Device
  validation confirms; only a repeatable in-repository test guards.

## Consequences

- The `DEF` rows become honestly blocked or honestly user-validated instead of permanently stuck.
- The pressure to invent evidence is removed at the source.
- Unwired contract work is in no build, so it owes no device validation — which is the coherent form
  of the ADR-0008 exception.
- Rows must state their channel at planning time, surfacing "nobody can check this" early.
- `E4-lab` is optional, so the emulator instrumentation harness is a quality investment rather than
  an unfunded obligation blocking the programme.

## Rejected alternatives

- **Keep in-tree device evidence as a mandatory `E4`.** Retained as `E4-lab` and as the basis for
  `E3` regression gates; rejected as a universal obligation because it would gate the programme on
  agent-operated device capture, duplicating the user-device channel.
- **Accept `E2` as sufficient for device behaviour.** Rejected: `ROADMAP.md` rule 1 and ADR-0006
  both forbid it, and unit tests cannot see focal invariance, transition pacing, or memory behaviour.
- **Allow `pass` when a channel is unavailable.** Rejected explicitly; named here so it cannot be
  reintroduced quietly.

## Evidence required

- Every row declares its evidence channel.
- `DEF` rows state `E3` plus `E4-user` or `E4-lab`, or are `BLOCKED` naming the missing channel.
- No `DEVICE_VERIFIED` row has a reproduction that cannot be re-performed.

## Supersedes / superseded by

- Amends the `E4` definition in `doc/REBUILD_STATUS.md` and the obligations in
  `doc/E4_ACCEPTANCE.md`.
