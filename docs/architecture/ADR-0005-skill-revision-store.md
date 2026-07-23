# ADR-0005: Skill revision store

- Status: Accepted
- Scope: Architecture boundary; implementation begins after P0

## Decision

A submitted `(SkillId, SkillRevision)` identifies an immutable skill definition in the P3-D pure-Java `SkillDefinitionStore` aggregate. Submission commits a new revision to that domain truth; P4 persists its detached snapshot through the sole Overworld SavedData adapter. Casting only pins an existing revision. Drafts, player references, runtime pins, and reclaimable revisions retain distinct responsibilities.

P0 adds no skill IDs, revisions, definitions, drafts, stores, or schema.

## Consequences

The domain Store must support fixed-revision lookup, atomic admission／CAS／insert, idempotent pin release, and verifiable mark-and-sweep reclamation without depending on an online owner. The P4 adapter owns encoding, persistence, and dirty marking; Store and Attachment updates remain separate locations requiring reconciliation.

## Authority

See [P3 scoped amendment §§9-B–9-D](../codex-spec/17_P3資料模型修正案.md#9-b-p3-d0-store-truththread-confinement-與-persistence-seam) and [Frozen skeleton §§6, 8, 8-B](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md).
