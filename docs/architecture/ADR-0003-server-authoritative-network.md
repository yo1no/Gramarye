# ADR-0003: Server-authoritative network

- Status: Accepted
- Scope: Architecture boundary; implementation begins after P0

## Decision

Clients send bounded intent only. The server reconstructs and validates operations from its own skill definitions, player state, world state, distance, dimension, and sequence data. Client claims never directly select gameplay results.

P0 registers no payloads and defines no network protocol.

## Consequences

Every future serverbound payload needs explicit bounds, replay protection, rate limiting, protocol compatibility, and server-main-thread execution. Client-only classes must remain unreachable on dedicated servers.

## Authority

See [Frozen skeleton §20 and §23–25](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md).
