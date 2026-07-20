# ADR-0005: Skill revision store

- Status: Accepted
- Scope: Architecture boundary; implementation begins after P0

## Decision

A submitted `(SkillId, SkillRevision)` identifies an immutable skill definition in the Overworld `SkillDefinitionStore`. Submission persists a new revision; casting only pins an existing revision. Drafts, player references, runtime pins, and reclaimable revisions retain distinct responsibilities.

P0 adds no skill IDs, revisions, definitions, drafts, stores, or schema.

## Consequences

The later store must support fixed-revision lookup, submission-time persistence, dirty marking, idempotent pin release, and verifiable mark-and-sweep reclamation without depending on an online owner.

## Authority

See [Frozen skeleton §5–6](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md).
