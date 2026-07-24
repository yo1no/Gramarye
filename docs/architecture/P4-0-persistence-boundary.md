# P4-0 persistence, Attachment, and composition boundary

This ledger indexes the approved
[P4 persistence, Attachment, and composition amendment](../codex-spec/18_P4持久化與組合修正案.md).
The frozen skeleton and P3 amendment remain authoritative outside that amendment's explicit scope.
This page is a compact phase boundary, not a second persistence specification.

## Phase split

- P4-A1 owns the canonical owner Codec, family/context boundary, bounded raw codecs,
  `RawTreeEnvelope`, current mixed-family document bridge, appearance mapping, and shared logical
  document bounds.
- P4-A2 owns `store_schema_version`, bounded Store／History／Revision blobs, Store physical
  migration, a location-bound opaque-token logical conformance view, migration-before-hydration, current
  snapshot／restore orchestration, bounded facts, and current Store blob encode／load.
- P4-A3 owns only immutable hierarchical carrier rebuild／replacement／filter primitives, checked
  totals, and the 64 MiB fixed-heap probe; it owns no lifecycle, publication, dirty state, commit,
  journal, Attachment, or composition.
- P4-B owns `saved_data_schema_version`, the outer SavedData carrier, sole Overworld SavedData
  lifecycle, bounded one-time load path, Ready／Quarantined states, live carrier publication, save
  callback, and dirty decisions. It reuses A3 and does not reimplement Store encoding.
- P4-C owns the permanent player skill Attachment, Draft/reference/editor persistence, total
  serializer, Attachment migration, and clone policy.
- P4-D owns authenticated submission composition, invocation of A3 prospective Store builders,
  prospective journal replacement, commit-oriented persistence preflight, Store commit, carrier／
  journal publication, Attachment transition, typed outcome, and crash recovery.
- P4-E owns complete offline root audit, rebuildable root indexing, reconciliation, reclaim
  composition, and its dirty decisions.
- P4 delegates Store owner, quota, CAS, allocation, validation, and reclaim policy to P3. The
  `gramarye_skill_definitions` carrier does not absorb RuntimePersistentStore or Marker schemas.

## Mixed-family storage boundary

A `SkillDocument`, and even one node, may contain JSON and NBT raw subtrees simultaneously. Every
Trigger/Action payload and every top-level/node `Unparsed` appearance therefore receives its own V0
`RawTreeEnvelope`; whole-document and whole-node family tags are insufficient. `RegistryOps` is
context rather than a third family and is rebound to the current authoritative provider. Raw bytes
are never converted between JSON and NBT.

Decoded appearance uses its typed canonical representation. Rejected top-level appearance saves as
default, and a rejected override saves as none; rejected raw or diagnostics are not persisted.
Exact envelope fields, legal context combinations, byte encoding, and appearance states are defined
only by the P4 amendment.

## Bounded physical Store

The physical hierarchy is length-delimited:

```text
SavedData carrier -> Store blob -> History blobs -> Revision blobs -> document storage tree
```

Ordered lists preserve duplicate routes until P3-D restore can reject them. The carrier and journal
are derived encodings, not domain truth. Exact fields, canonical ordering, encoded byte ceilings,
and quarantine limits are owned by the P4 amendment and `MagicSafetyCeilings`.

Physical exact-field preflight validates only count arithmetic and framing: counts must be
non-negative and compatible with element type, remaining bytes, minimum framing, checked
arithmetic, nested byte lengths, and trailing-input rules. It must not apply P3-D owner/global skill
or retained-revision domain ceilings, and it must not allocate a huge collection from an
untrusted declared count. A physically impossible count is a malformed physical envelope with zero
restore calls. A physically valid list that exceeds a domain ceiling remains list-based and reaches
`SkillDefinitionStore.restore` exactly once, where it becomes `StoreRestoreRejected` with the P3-D
capacity scope rather than a P4 malformed or parallel count-capacity failure.

The Revision hard ceiling of `1_114_112` bytes is an inclusive outer-envelope admission predicate,
not a promise that V0 can produce a successful canonical revision of exactly that size. V0 uses an
85-byte wrapper; with the `1_048_576`-byte document ceiling, its largest complete canonical revision
is `1_048_661` bytes. Exact-field Store／History／Revision NBT preflight rejects duplicate, unknown,
missing, or wrong-type fields and oversized nested byte arrays before full Compound materialization;
list duplicate routes remain intact for restore.

P4-A3 performs only pure carrier calculations. Before a live commit, P4-D uses those primitives to
build and bound the prospective Store and journal replacements. Only `Committed` lets the P4-B
lifecycle publish the prebuilt carrier／journal and mark dirty. Reclaim uses A3 filtering of already
encoded retained entries and never cross-family re-encodes raw trees.

## Migration and load boundary

The version axes and load order are distinct:

```text
P4-B bounded raw ingress -> saved_data_schema_version outer migration -> store_blob
-> P4-A2 store_schema_version physical migration -> per-document P3-B1 migration
-> exact raw reinsertion -> P4-A1 family-aware hydration -> current Store snapshot -> P3-D restore
```

P4-A2 uses exactly two narrow public cross-package facade classes for document/migration package
visibility: `SkillDocumentStorePersistenceFacade` and `OpaqueSkillDocumentMigrationFacade`. The
first is the sole Store-to-document persistence seam: public `encodeCurrent` delegates to the A1
package-private current encoder, while public load always probes and runs the production migration
orchestration before it delegates to the A1 hydrate seam. Public current encode is therefore valid;
public current-only decode, hydrate, load, or skip-migration remains forbidden. Store encoding must
use this facade for every document and may not copy the A1 mixed-family serializer.

`EncodedSkillDocument` is a defensive, bounded, immutable whole-document byte handle and therefore
contains the persisted raw subtrees, but exposes no per-subtree, physical-field, or mutable-tree
API. The document facade parses that physical representation, extracts every raw envelope's context
and exact immutable bytes into a side table, and builds an NBT logical conformance view for P3-B1. Its non-opaque fields use the
logical `SkillDocument` outer schema; a generated sentinel replaces each Trigger/Action payload root
and each `Unparsed` appearance raw root. Physical `family`, registry-context, map-compression, and raw
byte fields never enter the migration-visible tree.

The document-to-migration tokenized handle therefore contains no raw subtree bytes and is a distinct
nominal type, never the same wrapper or a bare byte array. Tokens bind ID, typed original location,
serialized-tree context, and exact immutable raw bytes in the document-package side table. V0
requires each token exactly once at its original location and preserves the envelope `type`, payload
`schema_version`, and context; it rejects relocation, exchange, rewrite, deletion, addition,
unknown IDs, missing IDs, and duplicates. The migration step cannot inspect or branch on the
sentinel. After migration, the document facade validates these invariants before exact reinsertion.

`resolveFromRaw` remains the formal P3-B2 direct-raw ingress, but P4 load does not call it because a
single `DynamicOps` tree cannot carry the mixed-family persisted document. Both paths use the same
production `SkillMigrationPlan`; P4 invokes it through the raw-free logical conformance view and
then uses the A1 hydration seam. The P4 view adapts to the P3-B1 contract rather than defining a
physical migration schema.

Bootstrap audit and load share the sole production `SkillMigrationPlan` provider. Immutable
`PipelineFactReport` merging is bounded, ordered, and propagates truncation without exposing its
mutable collector. Minimal handles/results and the P4-D composition facade are outside this
phase-local two-facade count.

P4 introduces no second skill migration plan and does not run payload migration during load. Any
migration, decode, or restore failure installs neither a partial nor an empty Store. The custom
one-time loader distinguishes an absent file from an invalid existing file; the latter becomes
non-dirty `Quarantined` state and retains the original file. The SavedData save callback writes only
a prebuilt immutable carrier and provides no fsync or cross-location durability promise.

## Attachment and composition boundary

The permanent `gramarye:player_skills` Attachment stores bounded Draft, latest, equipped, and editor
state. Owner identity is derived from the authenticated player rather than duplicated in the
Attachment. Existing malformed data is Quarantined, not treated as missing.

Submission derives a fresh server principal, reads current Store authority, obtains one immutable
quota snapshot, delegates preparation to P3-C, rechecks authority, performs persistence preflight,
and delegates commit to P3-D. Its result is a distinct composition outcome, not
`SkillSubmissionOutcome.Prepared`.

Store mutation precedes Attachment mutation. A bounded world journal records the expected and target
generation/pointer transition. An in-memory `setData` does not prove durability; journal entries
clear only after later persisted playerdata readback confirms the target.

## Offline roots and reclaim

Complete roots include offline player latest/equipped references, journal targets, and every enabled
future persistent runtime source. A rebuildable index is never truth and starts incomplete after
restart. Unreadable, truncated, unknown, or unaudited sources keep the aggregate incomplete, so
reclaim is not invoked. Root capture and reclaim occur immediately in one logic-thread call chain,
without forced chunk loads, background sweeping, or cross-tick reuse of `Complete`.

## Implementation gate

P4-A1 starts only after the P4 amendment is committed and remote CI passes. P4-A2 starts only after
the P4-A2.0 clarification is committed and remote CI passes. The P4-A1 gate proves
same-family structural preservation for JSON, NBT, and mixed-family documents, and proves that every
migration/decode/restore failure installs neither a partial nor an empty Store.

## P4-A1 implementation ledger

P4-A1 adds only the current-document raw persistence seam. The public tree boundary lives under
`magic.definition.tree`: `SerializedTreeFamily`, immutable `SerializedTreeContext`, and
`SupportedDynamicTrees` are the sole family/context classifier and defensive-copy utility. The
bounded byte encoders, strict JSON/NBT codecs, V0 raw envelope, physical document DTO, typed
failure/result, and current encode/hydrate mixed-family bridge remain package-internal under
`magic.definition.document`; this co-location preserves the existing package-private P3-A
appearance and canonical-ID seams without widening mutable-tree or byte-array APIs.

JSON uses strict UTF-8 with duplicate-key/trailing-input rejection and deterministic recursive key
ordering. NBT uses uncompressed arbitrary-tag framing, an encoded-byte bound, and a separate finite
allocation/tree-complexity accounter. Neither codec converts families. Each raw subtree retains its
own family, registry-context, and JSON map-compression flags; hydration rebinds registry context to
the supplied current provider.

The document bridge applies one logical depth/node budget across the outer document and every
hydrated raw subtree at its logical insertion depth. Physical envelope metadata and byte-array
storage are excluded from that logical count. Rejected appearance states follow the amendment's
default/none persistence fallback, and `SkillOwnerId.CODEC` reuses the canonical UUID codec already
used by `SkillId`.

The current encoder and hydration shortcut remain deliberately package-private. The public P4-A2
document persistence facade may delegate to the current encoder for typed current-domain output;
its load path may invoke current hydration only after schema probe, migration orchestration, token
validation, and exact reinsertion. P4-A1 introduces no Store envelope, migration plan, carrier,
SavedData, Attachment, or world lifecycle.

## P4-A2.0 clarification ledger

The authoritative amendment now fixes A1／A2／A3／B／D ownership, separates SavedData and Store
version axes, distinguishes the inclusive Revision outer ceiling from the V0 canonical maximum,
approves exactly two P4-A2 opaque cross-package facade classes, including one bidirectional document
persistence facade with public current encode and always-migrating load, binds migration tokens to
typed locations in a P3-B1 logical conformance view, and separates physical count sanity from P3-D
domain-capacity enforcement. It also requires one production skill-migration-plan provider, bounded
fact merging, and exact-field NBT preflight. This ledger records closure only; the exact contracts
and A2 phase-local stop conditions remain defined by the amendment rather than duplicated here.
