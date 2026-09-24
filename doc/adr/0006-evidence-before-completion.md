# ADR-0006: Evidence before completion

- **Status:** Accepted
- **Date:** 2026-09-24
- **Decision:** User-visible Android behavior is complete only with executable evidence appropriate to the behavior.

## Context

The project has extensive JVM tests, but current reader zoom, crop, slice continuity, and transition issues were discovered only on a device. Unit tests around pure helpers did not prove pointer dispatch, layout, compositing, or frame pacing.

## Decision

- Pure policy requires unit tests.
- Android lifecycle/pointer/navigation behavior requires instrumentation or screenshot tests.
- Performance claims require macrobenchmark or measured device evidence.
- Physical behavior is marked pending when no device is available; it is never silently treated as passed.
- A skipped test is not evidence.
- A passing test is attached to a named fixture and build SHA.

## Consequences

- CI requires a device/emulator path.
- A release may be blocked by missing runtime infrastructure.
- Device-only issues become first-class backlog items.
- The status ledger records evidence level, not confidence language.

## Rejected alternatives

- Manual QA as the only acceptance process.
- Treating compile success as runtime correctness.
- Marking device checks complete because a screenshot was inspected informally.

## Evidence required

The relevant test path, device/API/build details, command output, and artifact link or local path.
