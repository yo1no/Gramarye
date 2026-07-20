# ADR-0002: Single source of truth

- Status: Accepted
- Scope: Architecture boundary; implementation begins after P0

## Decision

Every persisted data category has exactly one authoritative location. Attachments, SavedData, entity persistence, memory indexes, and client snapshots must follow the frozen truth-ownership table. Rebuildable indexes and sync snapshots are never additional persisted truth.

P0 introduces no persisted gameplay data or schema.

## Consequences

Every later persistence change must identify its authoritative location, dirty handling, reconciliation policy, lifecycle, and migration impact before implementation.

## Authority

See [Frozen skeleton §7–10](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md).
