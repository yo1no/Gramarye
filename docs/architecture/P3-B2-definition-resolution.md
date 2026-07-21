# P3-B2 definition resolution boundary

This note records the implemented P3-B2 integration boundary. The authoritative architecture remains the [frozen skeleton](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md), refined for P3 by the approved [data-model amendment](../codex-spec/17_P3資料模型修正案.md). The [implementation contract](../codex-spec/Codex_實作總規格Prompt.md) and [detailed phases](../codex-spec/NeoForge1.21.1_詳細實作步驟.md) remain authoritative outside that amendment.

## Orchestration entries

`SkillCandidateResolver.resolve(document, readReport)` accepts an already-read document and never reruns skill migration or the Reader. `resolveFromRaw(raw)` performs raw snapshot capture, P3-B1 skill migration, one tolerant P3-A Reader pass, and then definition resolution. Its typed result distinguishes pre-snapshot rejection, skill-migration failure with the original snapshot, Reader rejection, and success. Reader failures retain only the fixed machine code; DFU diagnostics and raw trees are not copied into the failure.

The successful candidate preserves the Reader's immutable report and combines facts in deterministic order: all skill-level migration facts first, followed by payload migration facts in node traversal order. Both sources share `MAX_PIPELINE_FACTS`; reaching the cap drops later facts and sets `truncated=true`.

## Descriptor lookup and typed decode

Each Trigger and Action envelope is looked up exactly once. A missing key becomes the existing P2 unknown definition without migration or decode. A found descriptor stays in private generic capture; its current schema and descriptor-owned migration plan are used before `decodeWithDescriptor`. That decode entry performs no lookup, migration, or semantic validation. P3-B2 therefore does not create another type-ID map or payload-migration registry.

Per-envelope states are `Resolved`, `Unknown`, `MigrationFailed`, and `DecodeFailed`. Unknown stores only the P2 unknown definition because that object already owns the source envelope. Migration and decode failures retain the original source envelope and descriptor; a migrated envelope is transient to one resolution call stack and is never stored in a candidate, cache, writer, or persistence model.

P2 `DefinitionFailure` diagnostic behavior remains unchanged. New payload-migration and Reader failures do not retain exception messages, stack traces, arbitrary strings, or raw payloads. Future logging paths must not emit unbounded Codec diagnostics directly.

## Payload migration

`TriggerType` and `ActionType` expose an additive immutable `payloadMigrationPlan()`, defaulting to an empty plan. `currentPayloadSchemaVersion()` remains the single current-version source. Plans contain unique adjacent `N -> N+1` edges and can verify complete coverage from zero.

Every step receives a defensive payload copy and returns only `migratedPayload`. The orchestrator owns schema advancement and `PAYLOAD_STEP_APPLIED` facts. It rejects missing edges, future schemas, errors, partials, unexpected `RuntimeException`, exact `DynamicOps` instance changes, and over-limit output. `Error` is not caught. Output bounds reuse `DynamicTreeBounds` with payload-root depth one and the global skill-document depth/node hard ceilings.

Payload migration may change only payload data and the transient envelope schema version; registry-routed `type` remains unchanged. Migrated data never overwrites the stored `SkillDocument`, and compatibility migration does not allocate a player revision. Repeated migration cost is accepted in B2 because migration is rare, chains are expected to be short, and this phase intentionally has no cache.

## Candidate boundary and startup audit

`ResolvedSkillCandidate` is immutable and non-persistent. It carries the source `SkillDocument.schemaVersion()` as `skillSchemaVersion`, plus `SkillReference`, position-derived nodes, the storage-level `AppearanceDocument`, the exact `SkillDocumentReadReport`, and the merged `PipelineFactReport`. This source value is not a current-version constant, migration version, registry generation, Codec field, or persistence truth. Each node carries its Trigger/Action resolution and `AppearanceOverrideDocument`. The candidate has no Codec, writer, execution API, validation projection, or profile availability state.

After custom registry registration has completed, common setup audits Trigger and Action descriptor payload-plan coverage plus the skill-level plan against their current schema versions. Invalid coverage is a developer wiring error and fails server startup with a bounded machine-readable code. Empty registries and empty plans are valid while all current schemas are zero.

P3-B2 stops before semantic or cross-node validation, `ValidatedSkillDefinition`, runtime projection, caching, submission, stores, SavedData, attachments, networking, or gameplay execution.
