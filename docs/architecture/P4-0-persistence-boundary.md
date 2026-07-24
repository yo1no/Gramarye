# P4-0 persistence, Attachment, and composition boundary

This ledger indexes the approved
[P4 persistence, Attachment, and composition amendment](../codex-spec/18_P4持久化與組合修正案.md).
The frozen skeleton and P3 amendment remain authoritative outside that amendment's explicit scope.
This page is a compact phase boundary, not a second persistence specification.

## Phase split

- P4-A owns the canonical owner Codec, family-tagged storage envelopes, bounded physical Store
  format, storage migration, family-aware hydration, and the reviewed Store persistence bridge. It
  contains no SavedData or Attachment lifecycle.
- P4-B owns the sole Overworld SavedData lifecycle, bounded one-time load path,
  Ready／Quarantined states, prebuilt carrier publication, and dirty decisions.
- P4-C owns the permanent player skill Attachment, Draft/reference/editor persistence, total
  serializer, Attachment migration, and clone policy.
- P4-D owns authenticated submission composition, persistence preflight, Store commit, pending
  Attachment-update journal, typed outcome, and crash recovery.
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
are derived encodings, not domain truth. Exact fields, canonical ordering, count ceilings, encoded
byte ceilings, and quarantine limits are owned by the P4 amendment and `MagicSafetyCeilings`.

Before a live commit, P4 builds and bounds the prospective revision/history/Store/journal/carrier
delta. Only `Committed` publishes that prebuilt carrier and marks dirty. Reclaim rebuilds its carrier
by filtering already encoded retained entries and never cross-family re-encodes raw trees.

## Migration and load boundary

The only valid load order is:

```text
bounded raw ingress -> storage migration -> per-document P3-B1 migration
-> family-aware hydration -> current Store snapshot -> P3-D restore
```

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

P4-A starts only after the P4 amendment is committed and remote CI passes. Its first gate proves
same-family structural preservation for JSON, NBT, and mixed-family documents, and proves that every
migration/decode/restore failure installs neither a partial nor an empty Store.
