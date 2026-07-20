# ADR-0004: Effect pipeline

- Status: Accepted
- Scope: Architecture boundary; implementation begins after P0

## Decision

Gameplay effects follow `ActionExecutor → EffectRequest → EffectResolver → EffectCommitPlan → EffectCommitter → EventEmitter`. Actions do not mutate the world or mana directly, and commit plans do not promise database-style rollback.

P0 creates none of these interfaces or gameplay effects.

## Consequences

Later effects require ordered commit steps, explicit partial-failure results, idempotent compensation where applicable, trace output, and protection against automatic whole-plan retries.

## Authority

See [Frozen skeleton §16–19](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md).
