# ADR-0001: One owner per truth

- **Status:** Accepted
- **Date:** 2026-09-24
- **Decision:** Every user-visible fact and resource has one authoritative owner at each layer.

## Context

The current reader and UI distribute state across ViewModels, mutable models, viewers, Compose configuration objects, preferences, and loaders. Recent crop, zoom, transition, and navigation defects are consequences of competing representations.

## Decision

- Persistent data is owned by its data store.
- Domain policy is pure and owns derived decisions.
- A workflow store owns session state.
- A viewport owns transient transform/gesture state.
- A resource/cache owner owns bytes and decoded artifacts.
- UI renders immutable snapshots and sends semantic commands.
- No layer writes another layer's state directly.

## Consequences

- Tests can isolate policy, Android lifecycle, and rendering.
- Resource release has a clear owner.
- Some global convenience state is removed.
- A small amount of explicit command plumbing is accepted.

## Rejected alternatives

- A single global state object: creates hidden coupling and broad recomposition.
- Mutable Compose state as the database: loses process and ownership guarantees.
- Multiple defensive caches: duplicates invalidation and memory.

## Evidence required

Reducer tests, state-owner diagrams, architecture rules, and recreation/cancellation tests for the affected workflow.
