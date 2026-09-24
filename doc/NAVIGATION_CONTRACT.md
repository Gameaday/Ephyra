# Navigation Contract

> **Status:** binding technical contract. The application has one navigation owner.

## Graph

```text
Main NavController
  -> Shell destinations
  -> Library
  -> Updates
  -> History
  -> Browse
  -> More
  -> Series
  -> Settings
  -> Detail/configuration destinations
```

The bottom bar is a shell control over the same graph. It is not a second `NavHost`.

## Typed destinations

Every destination is a serializable typed route. Cross-feature navigation uses a small public contract provided by the destination feature and resolved by the app navigation coordinator.

Features do not import sibling implementation screens. Features do not own global navigation events.

## State restoration

Navigation restoration must preserve:

- selected shell destination;
- selected Series;
- reader entry point;
- search query where appropriate;
- back stack;
- pending deep-link destination.

The reader Activity may be separate for immersive/secure behavior, but its return target is a typed route and its result is explicit.

## Back

- System back follows the single main graph.
- Bottom-tab reselect is not a history mutation unless a documented rule says otherwise.
- Predictive back uses the same destination and transition model as completed back.
- A back event during loading does not create duplicate pops.
- Reader exit persists progress before returning to the source destination.

## Evidence

- typed route serialization tests;
- deep-link tests;
- process recreation tests;
- compact/expanded back-stack tests;
- predictive-back instrumentation;
- reader return-state tests.