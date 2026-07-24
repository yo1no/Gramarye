# P3-D skill definition Store decision ledger

This page closes the P3-D engineering phase. Architecture authority remains the approved
[P3 scoped amendment §§9-B–9-D](../codex-spec/17_P3資料模型修正案.md#9-b-p3-d0-store-truththread-confinement-與-persistence-seam),
the [frozen skeleton](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md), the
[implementation contract](../codex-spec/Codex_實作總規格Prompt.md), and the
[detailed phases](../codex-spec/NeoForge1.21.1_詳細實作步驟.md). This is a phase-seam
ledger, not a second complete Store specification.

## D0 — aggregate boundary

- The production Store is a pure-Java, server-logic-thread-confined aggregate. Owner binding and
  retained immutable `SkillDocument` entries are its domain truth.
- Quota, owner, capacity, CAS, and insert belong to one mutation boundary. P3-D provides no
  persistence adapter, Minecraft lifecycle, Codec, dirty marking, or cross-location transaction.
- Retained histories may be sparse. Each maximum retained revision is the implicit latest root;
  ordinary reclaim cannot remove it or make allocation move backward.

## D1 — truth, reads, and persistence seam

- Exact reads derive latest and owner counts from retained histories and their owner bindings; no
  parallel index or allocation counter exists. Active pin counts remain transient lifecycle state,
  not persistence truth.
- The list-based snapshot is deterministic, detached, immutable, and contains only owner/history
  truth. Typed restore revalidates routing, current document schema, and hard ceilings.
- `SkillRevision.successor()` is the sole candidate-successor operation. It does not allocate a
  revision.

## D2 — atomic revision admission

- An immutable quota snapshot is policy input, not authorization. Composition must freshly
  authorize before commit.
- Every typed owner/CAS/quota/capacity failure precedes the first Store mutation. Success publishes
  one prebuilt replacement history and formally allocates the proposed revision.
- A repeated plan conflicts; commit does not retry, canonicalize documents, or imply that
  persistence completed.

## D3-A — active revision pins

- Active pin counts are transient exact-reference lifecycle state. Multiple handles count
  independently and close idempotently.
- Pins are excluded from snapshots, restore to empty, and never alter latest, quota, owner, or
  dirty state.

## D3-B — bounded retention roots and reclaim

- A caller-claimed complete root sequence is materialized through one bounded factory. Raw order
  and duplicates count toward the 65,536 hard ceiling; over-limit, truncated, and incomplete input
  fail closed.
- A `Complete` snapshot is fresh only for immediate authoritative composition in the same
  Store/world/logic-thread call chain. It is not a credential and is never retained by the Store.
- Missing external roots are rejected in raw order before deduplication. Active-pin invariants are
  checked before planning.
- Retention is the exact union of each history's implicit latest root, external exact references,
  and active exact pins. Owner identity, latest, history presence, quota count, and pins do not
  change.
- All replacement histories, bounded count-only report state, and the completed result are built
  before publication. Typed rejection changes neither histories nor pins.

## Dirty-state matrix for P4 composition

| Aggregate operation/result | P4 dirty decision |
| --- | --- |
| commit `Committed` | dirty |
| commit typed failure | not dirty |
| pin / close | not dirty |
| reclaim `Rejected` | not dirty |
| reclaim `Completed`, reclaimed = 0 | not dirty |
| reclaim `Completed`, reclaimed > 0 | dirty |
| snapshot / read | not dirty |

## P4 obligations

P4 owns complete offline root enumeration, including unloaded player, entity, construct, and
schedule references; Store/Attachment ordering and reconciliation; the SavedData adapter and
`setDirty()` decisions; `SkillOwnerId` persistence Codec; snapshot Codec/NBT; family-tagged raw
storage; encoded-byte ceilings; and bounded corruption/quarantine policy.

The fixed P4 rule is **migration before restore**. P3-D restore accepts a typed current-schema
snapshot and never runs or guesses migration. P4 must preserve these paired tests:

```text
old schema -> migration -> current-schema snapshot -> restore success
old schema without migration -> UnsupportedDocumentSchema
```

The migrated-success case must verify identity/revision routing and the existing same-family
Unknown Envelope／Unparsed appearance preservation contract; only that success path may proceed to
save or dirty handling. The skipped-migration case uses the same legacy-schema semantic fixture and
verifies the typed failure reference plus actual/expected schema metadata, no partial Store, and no
snapshot mutation.

Migration failure, restore rejection, and corruption/quarantine are distinct outcomes:

- Migration failure or a missing migration edge calls restore zero times and does not overwrite old
  persistent input; P4 routes it to its bounded quarantine policy.
- Migration success followed by Store-shape rejection is a restore rejection, not a migration
  failure, and creates no partial Store.
- Canonical save or dirty processing is eligible only after migration, decode, and restore all
  succeed.

P3-D intentionally contains no P4 migration adapter or fixture. The future P4 test suite must own
both the migrated-success and skipped-migration-rejection cases above.

## Deferred retirement

P3-D does not delete a whole skill identity, release quota, or create tombstones. Removing an
external reference only makes an unpinned non-latest revision reclaimable. A future retire workflow
requires a separate amendment and persistent no-reuse truth.
