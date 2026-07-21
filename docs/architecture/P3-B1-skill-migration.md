# P3-B1 skill migration boundary

This phase note records the implemented P3-B1 boundary. The authoritative architecture remains the [frozen skeleton](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md), refined for P3 by the approved [data-model amendment](../codex-spec/17_P3資料模型修正案.md). The [implementation contract](../codex-spec/Codex_實作總規格Prompt.md) and [detailed phases](../codex-spec/NeoForge1.21.1_詳細實作步驟.md) remain authoritative outside that amendment.

## Raw snapshot boundary

`RawSkillDocumentSnapshot` accepts JSON and NBT trees, including the standard JSON/NBT `RegistryOps` wrappers. Capture first applies the global parsed-tree proxies and then takes a defensive deep snapshot. Every raw accessor returns another deep copy; equality is the raw family plus structural tree, not `DynamicOps` identity. `toString()` exposes only the family.

The global hard proxies are depth 64 and 65,536 tree nodes. The root, every object/compound value, every array/list element and every scalar leaf count as nodes; keys do not, but remain subject to the string ceiling. Appearance quarantine and whole-document capture delegate to the same short-circuiting traversal implementation. These are parsed-tree proxies, not parser-level byte protection; the raw byte ceiling belongs at a later true byte-input boundary. Unsupported value families and limit failures are rejected before a snapshot is admitted.

## Schema probe

The schema probe reads only root `schema_version`. The root must be a map/compound, the field must exist, and its value must be an exact integer in `0..Integer.MAX_VALUE`. JSON integral values and finite values exactly equal to an integer (for example `2.0`) are accepted. NBT byte, short, int and in-range long tags are accepted. Fractional, non-numeric and out-of-range values fail without a partial result. `SkillDocument.CURRENT_SCHEMA_VERSION` remains the only current-version truth.

## Adjacent migration plan

`SkillMigrationPlan` is an immutable set of unique adjacent edges `N -> N+1`; under those constraints a cycle is structurally impossible. `verifyCoverage(currentVersion)` verifies exact continuous coverage of `0..currentVersion-1`. The plan may be empty for current schema zero. P3-B2 wires this coverage check into the post-registration common-setup audit; no production dummy step is registered.

Each step receives a defensive tree copy and returns only a migrated tree. The orchestrator owns all facts. A `DataResult` partial is a failure and is never promoted. After every successful step, the orchestrator checks exact `DynamicOps` instance identity, captures a new bounded snapshot, and probes the required output version. Losing a `RegistryOps` wrapper or replacing the ops instance fails with `STEP_CHANGED_DYNAMIC_OPS`; matching only the JSON/NBT family is insufficient.

The defensive input copy followed by output re-snapshot intentionally performs two deep copies per applied step. This cost is accepted because document migrations are rare and migration chains are expected to stay short.

## Envelope ownership boundary

Skill-level migration owns the skill-document outer shape. It may restructure an Envelope container only when a skill schema migration explicitly defines that container change. Trigger/action payload subtrees remain data for the later payload-migration component. Only payload migration may transform those payloads and advance their Envelope `schema_version`; it may not alter the registry-routed `type`. This keeps registry identity singular while allowing outer document and payload schemas to evolve independently.

## Facts and failures

Pipeline facts are deterministic, non-persistent machine data emitted by the orchestrator. At most 1,024 facts are retained; further facts set `truncated=true` without failing migration. Facts contain no raw tree, payload, type ID, free-form text, exception or stack trace.

Migration failures are non-persistent and machine-readable. They may retain bounded numeric metadata and a bounded exception class name. They never retain an exception message, stack trace or raw tree; a post-capture failure instead carries the original immutable snapshot separately. `Error` is not caught.

## B2 integration point

P3-B1 itself still stops at a current-schema raw snapshot. P3-B2 orchestration now consumes a successful migrated snapshot, invokes the P3-A tolerant reader once, then performs descriptor lookup, payload migration and typed decode. Validation remains outside this boundary.
