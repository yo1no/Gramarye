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

The authoritative `SkillMigrationStep` input model is the logical `SkillDocument` outer schema, not
a persistence-specific tree. Skill-level migration may change the root skill schema version and
only the document/node shell fields explicitly owned by that adjacent skill-schema edge. It does
not own a second physical representation.

Every Trigger/Action payload and every top-level/node `Unparsed` appearance raw subtree is an opaque
atom to a step. A step must not traverse, type-test, compare, hash, branch on, relocate, delete,
add, duplicate, or otherwise depend on an opaque value, its JSON/NBT family, registry context, or
map compression state. It must preserve each `DefinitionEnvelope.type`, payload `schema_version`, and
opaque slot. Only payload migration may transform payload data or advance its envelope schema
version; registry-routed `type` remains unchanged. This keeps registry identity singular while
allowing outer document and payload schemas to evolve independently.

The opacity rule is representation-independent. For one outer shell, direct JSON, direct NBT,
`RegistryOps`, and a persistence-generated token sentinel must produce the same outer output and
the same orchestrator facts while leaving the opaque value unchanged. Java cannot sandbox a
trusted migration step, so every production edge requires source review and paired
representation-independence tests. A future edge that needs payload-dependent behavior belongs to
payload migration or requires an approved architecture change first.

## Facts and failures

Pipeline facts are deterministic, non-persistent machine data emitted by the orchestrator. At most 1,024 facts are retained; further facts set `truncated=true` without failing migration. Facts contain no raw tree, payload, type ID, free-form text, exception or stack trace.

Migration failures are non-persistent and machine-readable. They may retain bounded numeric metadata and a bounded exception class name. They never retain an exception message, stack trace or raw tree; a post-capture failure instead carries the original immutable snapshot separately. `Error` is not caught.

## B2 integration point

P3-B1 itself still stops at a current-schema raw snapshot. P3-B2's formal `resolveFromRaw` entry
consumes a successful migrated snapshot, invokes the P3-A tolerant reader once, then performs
descriptor lookup, payload migration, and typed decode. It is not a test-only or deprecated path.

P4 persistence does not call `resolveFromRaw`: one `DynamicOps` tree cannot represent a
mixed-family persisted document. Instead, the P4 document facade keeps each physical raw envelope
outside migration and builds an NBT logical conformance view whose non-opaque fields match this
P3-B1 input model and whose opaque roots contain generated sentinels. It invokes the same production
plan through the P4 migration facade, validates and reinserts the original envelopes, then hydrates
the current document. The persistence view is an adapter to this contract, not another migration
schema. Validation remains outside this boundary.
