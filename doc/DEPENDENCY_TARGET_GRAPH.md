# Target Dependency Graph

> **Status:** binding target graph. Existing Gradle dependencies are debt until architecture checks pass.

## Allowed flow

```text
:app
  -> feature public contracts
  -> domain/media/navigation contracts
  -> platform adapters

:data
  -> domain/media contracts
  -> Android/network/database implementations

:source adapters
  -> source contracts
  -> transport/platform contracts

:reader:core
  -> domain/media/navigation contracts

:reader:engine
  -> reader:core + media contracts

features
  -> domain/media/navigation/UI contracts
  -> no data implementations
  -> no sibling feature implementations
```

## Forbidden

- feature -> concrete data implementation;
- presentation module exporting data APIs;
- sibling feature -> sibling feature implementation;
- domain -> Android;
- contracts -> implementations;
- source adapter -> Compose/UI state;
- reader engine -> Room/Coil business decisions;
- app global service locator as an internal dependency mechanism.

## Enforcement

1. Gradle dependency verification.
2. Import/package architecture tests.
3. Explicit sibling-feature allowlist.
4. Public API inspection for contracts.
5. CI failure on new forbidden edges.

## Migration

Existing edges are inventoried and removed in bounded tasks. A legacy edge may remain only when it is listed in the status ledger with an owner, reason, replacement task, and removal date.