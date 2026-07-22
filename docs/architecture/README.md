# Architecture index

The primary authoritative architecture specification is the [NeoForge 1.21.1 frozen skeleton](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md), as refined within its stated scope by the approved [P3 data-model amendment](../codex-spec/17_P3資料模型修正案.md). This directory records decisions and phase baselines; it does not duplicate or replace either authority.

Implementation guidance is provided by the [Codex implementation contract](../codex-spec/Codex_實作總規格Prompt.md) and the [detailed implementation phases](../codex-spec/NeoForge1.21.1_詳細實作步驟.md), in the priority order defined by `AGENTS.md`.

## Accepted decisions

- [ADR-0001: Platform lock](ADR-0001-platform-lock.md)
- [ADR-0002: Single source of truth](ADR-0002-single-source-of-truth.md)
- [ADR-0003: Server-authoritative network](ADR-0003-server-authoritative-network.md)
- [ADR-0004: Effect pipeline](ADR-0004-effect-pipeline.md)
- [ADR-0005: Skill revision store](ADR-0005-skill-revision-store.md)

## Phase baselines

- [P0 platform and repository baseline](P0-baseline.md)
- [P2-A descriptor registry baseline](P2-A-registry-baseline.md)
- [P2-B definition envelope baseline](P2-B-definition-envelope.md)
- [P3-A skill document model baseline](P3-A-skill-document-model.md)
- [P3-B1 skill migration boundary](P3-B1-skill-migration.md)
- [P3-B2 definition resolution boundary](P3-B2-definition-resolution.md)
- [P3-B3 validation and projection decisions](P3-B3-validation.md)
