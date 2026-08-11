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
- P4-B1 owns `saved_data_schema_version`, strict whole-root／inner-carrier framing, the canonical
  zero-length no-journal sentinel, bounded opaque pending bytes, outer migration, A2 Store loading,
  A3 carrier rebuild, and a matching Ready candidate. It has no world, filesystem, cache, or dirty
  lifecycle.
- P4-B2-A owns primary-file ingress, strict single-member gzip, the one-time Overworld cache install,
  Ready／Quarantined／Unavailable SavedData lifecycle, live Store／carrier publication, save callback,
  controlled read／pin／reclaim, dirty decisions, and the normal unit／GameTest／dedicated-smoke gates.
  It does not parse the journal or reimplement Store encoding.
- P4-B2-B owns the isolated full-size fixed-heap load／save／restart probes, the exact-maximum legal
  hostile-FNAME first／restart pair, invalid-file restart preservation, Gradle task isolation, and
  its required CI gate. P4-B is complete only when B1, B2-A, and every required B2-B local and
  remote gate are complete.
- P4-C0 owns the documentation-only Attachment totality, quarantine-preservation,
  destructive-oversize-marker, byte-coordinate, duplicate-capability, generation／no-op, editor,
  and implementation-gate decisions.
- P4-C1 owns physical V0, the bounded total serializer, Ready／PreservedRaw／OversizeMarker, Draft
  persistence／migration, exact bounds, and the prebuilt Ready carrier; it owns no registration or
  player mutation lifecycle.
- P4-C2-A owns Attachment registration, the immutable `setData` service, generation transition,
  the controlled P4-D transition seam, bounded per-player P4-E root projection, and normal lifecycle
  GameTests. P4-C2-B owns only the isolated fixed-heap playerdata／restart／death／End probes,
  external verifiers, task／CI wiring, and phase gates.
- P4-D0 owns only the documentation authority for journal framing／availability, submission policy
  ownership, recovery, and the combined memory Gate.
- P4-D1 owns strict journal framing／migration／operational state, the single Store authority
  snapshot, narrow Store submission port, prospective Store／journal preflight, opaque commit handle,
  Store／journal publication, and journal roots; it owns no facade or event listener.
- P4-D2 is forcibly split into P4-D2-A followed by P4-D2-B. D2-A owns the refined D1 preparation
  taxonomy, policy／Draft-creation primitives, typed composition outcome, package-private P3-C
  exactly-once pipeline, and P4-C single-observation／currentness seams. D2-B owns the
  authenticated submission facade and complete Store／Attachment composition.
- P4-D3 is forcibly split into P4-D3-A followed by P4-D3-B. D3-A owns production bootstrap
  ordering, bounded owner recovery projection, one-shot persisted Attachment observation, login
  recovery, readback-confirmed clear／replay, normal GameTests, and local phase gates. D3-B owns the
  paired restart/crash matrix and combined fixed-heap／Gradle／CI Gates.
- P4-E0-B owns the V0 numeric／heap／truth／completeness authority synchronization. P4-E0-B.1
  owns only the documentation clarification for integrated loaded-player source selection, logical
  counting, post-DFU handling, freshness／alias limits, and the E3 integrated Gate. P4-E0-B.2 owns
  only the documentation correction from `Runtime.maxMemory()` to effective HotSpot `MaxHeapSize`
  VM-option bytes, its three-state／precedence contract, and process-control roles; it changes no
  floor, numeric maximum, R2Q evidence, or implementation. P4-E0-B.3 owns only the documentation
  clarification for online source counter applicability, `online > integrated > disk` arbitration,
  unified UUID ordering, final online freshness, and the E3 online qualification obligation; it too
  changes no numeric profile, evidence, or implementation. P4-E1 owns the read-only bounded online／
  integrated／disk scanner; full P4-C applies only to disk／integrated Tags while online observes the
  existing admitted state. Its reviewed global-composition work is forcibly split into B1 then B2.
  B1 owns closed inventory coverage, global source arbitration, player／journal raw-root capture,
  capacity-before-dedup, and freshness witnesses, producing only a package-private single-use
  unpublished capture. B2 owns grouped Store audit, the bounded public result, memory-only index,
  and ephemeral same-call-chain Complete handoff. E1 owns no mutation or reclaim call. P4-E2 owns only login-time
  immutable reconciliation after P4-D recovery and owns no offline, Store, journal, or reclaim
  mutation. P4-E3 owns the unique same-`ServerStartingEvent` fresh-audit-to-single-controlled-reclaim
  composition, restart verification, and fixed-1,536-MiB production Gate. P4-B2 continues to own
  the resulting carrier publication and Store SavedData dirty decision. These three implementation
  phases may not be merged.
- P4 delegates Store owner, quota, CAS, allocation, validation, and reclaim policy to P3. P4-D owns
  the server-side acquisition of one combined quota／ValidationContext snapshot, while P3-C and P3-D
  consume its respective validation and quota members. The `gramarye_skill_definitions` carrier does
  not absorb RuntimePersistentStore or Marker schemas.

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

Ordered lists preserve duplicate routes until P3-D restore can reject them. The Store carrier is a
derived encoding. The exact opaque pending blob is the sole persistent truth for pending Attachment
transitions; decoded journal Ready／Unavailable is a derived operational view bound to that blob.
Exact fields, canonical ordering, encoded byte ceilings, and quarantine limits are owned by the P4
amendment and `MagicSafetyCeilings`.

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
build and bound the prospective Store and journal replacements. Only a P3-D Store `Committed` result
permits publication of the prebuilt carrier／journal and dirty; the final composition result may still
be `CommittedPendingAttachmentRecovery` if later Attachment publication fails. Reclaim uses A3
filtering of already encoded retained entries and never cross-family re-encodes raw trees.

## Migration and load boundary

The version axes and load order are distinct:

```text
P4-B2 bounded compressed-file ingress -> P4-B1 strict whole-root/inner-carrier decode
-> saved_data_schema_version outer migration -> P4-A2 Store load
-> per-document P3-B1 migration -> exact raw reinsertion -> P4-A1 family-aware hydration
-> current Store snapshot -> P3-D restore -> P4-A3 full carrier rebuild
-> Store/carrier match -> Ready publication
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
non-dirty `Quarantined` state and retains the original file. Runtime Store/carrier pairing failure
instead becomes non-saving `Unavailable`; it is not load corruption. The SavedData save callback
writes only a prebuilt immutable carrier and provides no fsync or cross-location durability promise.

## Attachment and composition boundary

The permanent `gramarye:player_skills` Attachment stores bounded Draft, latest, equipped, and editor
state. Owner identity is derived from the authenticated player rather than duplicated in the
Attachment. Missing alone creates empty Ready. An in-bound malformed value is Quarantined as
PreservedRaw with a defensive logical-tree snapshot; an oversize value is Quarantined as
OversizeMarker and is explicitly replaced by the bounded reserved marker on a later normal save.
Neither variant is treated as missing or empty Ready.

Submission derives a fresh server principal, reads one Store authority snapshot, and obtains one
immutable policy snapshot containing both quota and ValidationContext. It invokes each P3-C stage
exactly once, prebuilds all persistence state, performs the final authority／identity recheck, and
delegates commit to P3-D. Successful submission retains the Draft. Pre-preparation failures invent no
report; `PreparationRejected` preserves the exact existing P3-C non-Prepared outcome and report;
every post-Prepared result preserves the exact same warning-only report reference. Its result is a
distinct composition outcome, not `SkillSubmissionOutcome.Prepared`. The compact
[P4-D0 ledger](P4-D0-submission-journal-boundary.md) indexes the exact handoff.

Store mutation precedes Attachment mutation. A bounded world journal records the expected and target
generation/pointer transition. An in-memory `setData` does not prove durability; journal entries
clear only after later persisted playerdata readback confirms the target.

## Player roots and reclaim

The exact V0 authority is indexed by the
[P4-E0 bounded root-audit authority boundary](P4-E0-root-audit-boundary.md) and remains normative in
the P4 amendment. V0's compile-time closed source inventory contains only
`PLAYER_SKILL_ATTACHMENT` and `PENDING_ATTACHMENT_JOURNAL`; Store latest and active pins are P3-D
implicit roots, while SkillInstance, Marker, Construct, and Schedule root persistence is not yet
enabled. Future families require an inventory member, provider, completeness gate, and tests in one
reviewed change; dynamic registration cannot prove completeness.

The read-only audit uses the amendment's 25 independent inclusive counters, exact disk-playerdata
`IntTag(3955)`, zero P4-E DFU, strict disk gzip／NBT language, product-selected 1,536-MiB heap floor,
and `INCOMPLETE_AND_CONTINUE`. Its sole heap-floor coordinate is the strict canonical nonnegative
`long` from HotSpot's `MaxHeapSize` VM option: below the unchanged
`MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES = 1_610_612_736`-byte floor is
`HEAP_FLOOR_NOT_MET`, at／above is `QUALIFIED_FLOOR_PRESENT`, and an unavailable／invalid observation
is `HEAP_FLOOR_UNVERIFIABLE`. Both nonqualified statuses short-circuit before journal／source work
with startup continuing and reclaim／mutation zero; Runtime／heap／pool memory values are diagnostic
only. Online, integrated, and disk primary／old are source kinds within the one
`PLAYER_SKILL_ATTACHMENT` family; per-UUID precedence is `online > integrated > disk` and all
selected owners share one UUID-natural order. Online observes only the existing admitted
Missing／Ready／Quarantined state: it has no per-file counter instance, contributes zero byte／
structural aggregates and admissions, and contributes actual Ready roots. The integrated source is
platform-post-DFU: it has no
compressed coordinate, contributes a single-pass as-if unnamed-Compound logical width and the same
structural／aggregate counters, excludes its UUID's disk pair from selection, and performs an exact
reference-identity freshness recheck without a whole-tree copy or cross-tick retention. Only
disk／integrated materialized Tags undergo full P4-C admission. Online and integrated winners exclude
same-UUID disk ingress, while physical entries still count toward directory/race evidence. Raw
latest/equipped/journal claims count toward the 65,536 ceiling before deduplication and then undergo
grouped exact-reference/owner Store audit. Missing or foreign offline pointers defer to login and
leave disk unchanged. The index is memory-only, starts Incomplete after restart, retains no raw
playerdata／Attachment data or public `Complete` permit, and loses completeness on any source race or
reconciliation. Only after B2-A grouped Store audit and B2-B final freshness both pass may ownership
of the original segmented reference backing transfer into the index; no second full root vector is
created.

P4-E1 and P4-E2 never invoke reclaim. Only P4-E3 may keep a fresh `Complete` as a local value in the
single logic-thread call chain `P4-B install -> P4-D bootstrap -> P4-E1 audit -> immediate controlled
reclaim exactly once`. There is no second listener, forced chunk load, background/periodic sweep, or
cross-tick reuse. An unavailable journal or any unreadable, over-limit, unaudited, quarantined,
reconciliation-pending, or invalid Store source keeps the global result Incomplete and reclaim at
zero. Audit N reconciliation never permits same-round reclaim; only a complete reread at restart
N+1 can do so. P4-B2 retains publication-before-dirty ownership for positive reclaim.

## Implementation gate

P4-A1 starts only after the P4 amendment is committed and remote CI passes. P4-A2 starts only after
the P4-A2.0 clarification is committed and remote CI passes. The P4-A1 gate proves
same-family structural preservation for JSON, NBT, and mixed-family documents, and proves that every
migration/decode/restore failure installs neither a partial nor an empty Store.

P4-C1 starts only after the complete P4-C0 authority patch is committed and remote CI passes. P4-C2
starts only after P4-C1's physical／serializer Gate passes. P4-C completes only after C1, C2, and the
required fixed-1-GiB exact-limit quarantine lifecycle job pass locally and remotely.

P4-D1 starts only after the documentation-only P4-D0 authority patch is complete. P4-D2-A starts
only after D1 closure and the completed D2 read-only design review; P4-D2-B starts only after D2-A
completes. D3-A starts only after D2-B normal submission and the D3 read-only design review pass;
D3-B starts only after D3-A closure. P4-D completes only after D1, D2-A, D2-B, D3-A, D3-B, and the
required paired fixed-1-GiB combined first／restart remote Gate pass; the first JVM alone carries the
complete simultaneous submission envelope.

P4-E0 is complete. The first P4-E1 read-only design review stopped because P4-E0-B had not defined
the integrated loaded-player snapshot's counting and freshness coordinate; P4-E0-B.1 resolved that
blocker, and the renewed read-only design review passed. The following E1-A attempt stopped at an
active heap-floor authority-coordinate conflict. P4-E0-B.2 is complete, its closure remote
prerequisites passed, and E1-A has been restored, re-preflighted, implemented, committed, pushed,
and remotely qualified. P4-E1-A is complete. The E1-B read-only design review then stopped at the
online source counter applicability authority gap. P4-E0-B.3 authority and exact-SHA remote closure
are now complete. The renewed E1-B review then stopped at the tag-free P4-C admission bridge Gate;
its focused A.1 review, implementation commit, push, and exact-SHA remote qualification have now
passed. P4-E1-A.1 is complete. The renewed P4-E1-B read-only design review passed without a Stop
Condition and forced the B1／B2 implementation split. P4-E1-B1 is committed, pushed, locally
verified, and qualified by the exact-SHA remote run; it is complete. The P4-E1-B2 read-only design
review is complete and forcibly split implementation into B2-A followed by B2-B. That historical
review verdict made B2-A ready to start; B2-A is now implemented, committed, pushed, and qualified
by unique attempt-1 exact-SHA remote run `31415157794`, so it is complete. The first B2-B read-only
design review stopped at the index-generation／exhaustion authority gap. B.4 now defines that
authority; its commit, push, and unique attempt-1 exact-SHA remote Gate have passed. The renewed
B2-B read-only design review passed without a Stop Condition and requires no further implementation
split. Its implementation is ready but has not started. P4-E1-B2 and P4-E1-B remain incomplete,
and E2／E3 remain blocked. E1 must
have zero player/Store/journal mutation and zero reclaim calls; E2 may publish at most one online
Attachment replacement after P4-D recovery but still has zero offline/Store/journal/reclaim
mutation. E3 alone owns the fresh-complete reclaim composition and the exact fixed-1,536-MiB
production-shaped first／restart Gate. R2Q research qualification and the E0-B remote jobs do not
substitute for that Gate, and the selected heap tier is not a universal safe minimum.

## P4-B0 clarification ledger

The amendment now closes the three P4-B implementation stop gates without adding another
amendment. The SavedData file has one strict unnamed whole-root Compound containing only `data` and
platform-owned `DataVersion`; the inner `data` Compound has exactly the three Gramarye carrier
fields. A zero-length `pending_attachment_updates_blob` payload is the sole canonical no-journal
sentinel. Non-zero pending bytes remain bounded, defensive, and opaque to P4-B; Journal V0 remains
owned by P4-D.

The amendment is the sole source for byte coordinates and derivations: the carrier ceiling measures
the complete unnamed uncompressed encoding of the inner `data` Compound, the whole V0 root adds 26
bytes, and the approved finite allocation quota is separately golden-tested. The primary file must
contain exactly one gzip member and one decompressed unnamed root, with compressed and decompressed
EOF immediately afterward.

P4-B is split into pure B1 framing/load-state work and B2 platform lifecycle work. B2 installs the
custom-loaded exact instance into the Overworld cache during `ServerStartingEvent`; no cache miss may
fall through to fail-open `computeIfAbsent`. Ready contains the complete Store, its matching A3
carrier, opaque pending bytes, and an explicit rewrite flag. That flag covers outer migration, A2
migration, and a current but noncanonical source Store blob. A package-private comparison seam may
compare the source blob to the rebuilt carrier without exposing bytes or retaining another Store-size
copy; this seam was not part of P4-A3 and remains B implementation work.

P4-B2 owns controlled reclaim publication and dirty mapping, while P4-E owns root collection and
completeness. A post-mutation carrier invariant failure transitions to non-saving `Unavailable` and
never saves the stale carrier. Quarantined does not retain raw fragments, so the quarantine byte
ceilings have no P4-B consumer and do not authorize a raw-copy store.

```text
P4-B1   = COMPLETE
P4-B2-A = COMPLETE
P4-B2-B = COMPLETE
P4-B    = COMPLETE
```

The recorded external results are PASS for `build`, `P4-A3 memory gates`, and `P4-B memory gates`,
including the exact-maximum hostile-FNAME first／restart pair and packaged runtime smoke. The P4-C
read-only design review is complete. The P4-C0 framing conflict is resolved by the
`NbtIo.writeAnyTag` byte-coordinate decision, and the explicit destructive-oversize quarantine
policy closes the preservation-policy stop gate. P4-C0.1, P4-C1, P4-C2-A, and P4-C2-B are complete,
and the required remote `P4-C memory gates` result passed. P4-C is therefore complete. The P4-D
read-only review exposed the non-zero journal-framing gap; P4-D0 closed that authority gap without
Java changes. P4-D1 closure includes strict pending-journal framing, the journal operational
lifecycle, authority observation, the Store-side submission port, and
prepare／commit／bootstrap／clear. The local full regression passed, and the externally reported
remote `build`, `P4-A3 memory gates`, `P4-B memory gates`, and `P4-C memory gates` jobs passed. D1
intentionally adds no Gradle／CI memory Gate; the combined P4-D memory Gate remains D3-owned.

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

## P4-A2 implementation ledger

P4-A2 implements its cross-package boundary with exactly the two approved facade classes:
`SkillDocumentStorePersistenceFacade` owns current document encoding and always-migrating document
loading, while `OpaqueSkillDocumentMigrationFacade` owns the raw-free logical migration seam. The
token side table, physical Store DTOs, Store persistence bridge, and both current-only hydration and
restore seams remain package-internal. `SkillMigrationPlans.production()` and the Store migration
plan provider are the respective immutable production-plan truths; migration facts are merged in
bounded left-before-right order and are never persisted.

The V0 Store, History, and Revision envelopes use strict uncompressed arbitrary-NBT framing with
schema-aware exact-field preflight before nested byte-array materialization. Physical list checks
cover framing and arithmetic only; owner, history, and retained-revision limits continue to belong
exclusively to P3-D restore. The measured V0 Revision wrapper is 85 bytes, so a current maximum-size
document produces a 1,048,661-byte Revision envelope under the inclusive 1,114,112-byte outer
ceiling.

Document migration receives only the logical outer document and location-bound opaque sentinels.
Raw family, context, and bytes stay in the document-package side table; token count, location,
context, definition type, and payload schema are verified before exact reinsertion. Store loading
therefore remains fail closed in the order Store migration, document migration, current hydration,
and one P3-D restore call. P4-A2 introduces no carrier, SavedData lifecycle, Attachment, journal,
dirty state, or commit preflight; those remain later-phase responsibilities.

Only the document package may mint `TokenizedSkillDocumentMigrationInput`; callers cannot wrap
arbitrary bytes as migration input. Migration returns the distinct nominal
`MigratedTokenizedDocument`, which cannot be supplied back to the public migration entrypoint.

## P4-A3-A implementation ledger

P4-A3-A represents the derived persistence carrier as one immutable root Store blob plus checked,
immutable History／Revision route and range indexes. Slices refer only to verified ranges of that
root; the stable carrier does not retain duplicate nested byte arrays, domain documents, validation
projections, or a Store snapshot. It remains package-internal, provides no gameplay or authority
lookup, and never acts as owner, latest-revision, quota, CAS, or commit truth.

Layout construction is bound to sealed writer-produced frames: the document/Store bridge and pure
carrier builder are the only production index-composition call sites, and each index range must
match the corresponding range emitted by that same framing operation. Route identity is carried
from the same snapshot, plan, or already-verified carrier slice; no second schema parser derives
layout after encoding.

Full rebuild consumes a detached current Store snapshot and delegates canonical bytes and layout to
the P4-A2 persistence seam. New／Existing prospective builders produce complete immutable,
base-identity-bound replacements before a future commit; they neither invoke nor prove a Store
commit and own no publication or dirty state. Reclaim filtering consumes the post-reclaim snapshot,
reuses verified existing Revision slices, and rebuilds only enclosing History／Store framing without
re-encoding documents or duplicating P3-D retention policy. All byte totals reuse the P4-A2
Revision／History／Store ceilings and checked framing arithmetic.

The future P4-B save path may consume the carrier's package-private immutable root/copy seam without
re-encoding. SavedData lifecycle, live carrier publication, dirty mapping, journal, Attachment, and
composition remain outside A3-A. Fixed-heap and dedicated-server validation are the separate
P4-A3-B gate recorded below.

## P4-A3-B implementation ledger

P4-A3-B keeps deterministic builders and assertions in the isolated `p4A3Probe` source set and its
single GameTest holder in `p4A3GameTest`; neither output enters the production JAR. The dedicated
run uses a generated test-only `gramarye_p4_a3` structure namespace, so the P4-A3 probe remains
absent from normal GameTest while the full-size dedicated run executes exactly one probe. All probe JVMs use Java 21,
`-Xms512m -Xmx1024m -XX:+ExitOnOutOfMemoryError`; plain task timeouts are 180 seconds and the
dedicated task timeout is 300 seconds.

The local fixed-heap Gate on 2026-07-24 produced these bounded summaries. `heap_peak` is the maximum
aggregate heap usage sampled at major full-operation boundaries. `heap_pool_peak_sum` is separately
reported as the sum of each pool's independently recorded peak and may exceed the process heap
maximum because those pool peaks need not be simultaneous.

| workload / task | Store bytes | histories / revisions | elapsed | heap peak | pool peak sum |
| --- | ---: | ---: | ---: | ---: | ---: |
| `many-small` / `p4A3HeapProbeManySmall` | 66,062,342 | 4,095 / 32,767 | 4,872 ms | 1,040,711,680 | 1,257,208,272 |
| `near-entry` / `p4A3HeapProbeNearEntry` | 66,367,484 | 8 / 64 | 2,006 ms | 843,464,896 | 1,096,171,712 |
| `mixed` / `p4A3HeapProbeMixed` | 66,060,348 | 8 / 64 | 1,941 ms | 915,401,008 | 1,047,843,456 |
| `dedicated-mixed` / `runP4A3HeapProbeServer` | 66,060,348 | 8 / 64 | 2,338 ms | 829,332,128 | 1,365,517,280 |

Each workload performed a full carrier rebuild, A2 encode/load/hydrate/restore, prospective update
with base and prospective carriers simultaneously live, comparison with a committed Store full
rebuild, reclaim filtering compared byte-for-byte with a post-reclaim full rebuild, and a complete
save-shaped copy/checksum. The dedicated workload obtained its provider from the real server level
and preserved plain and RegistryOps JSON (normal and compressed) plus NBT contexts in mixed-family
documents.

The aggregate plain task is `p4A3HeapProbe`; the dedicated task is
`runP4A3HeapProbeServer`, and the configuration/isolation gate is
`verifyP4A3BConfiguration`. CI defines the stable `P4-A3 memory gates` job, dependent on the normal
`build` job, with five-minute plain and six-minute dedicated step timeouts. After the portable
verifier hotfix, the remote normal build and `P4-A3 memory gates` both passed; P4-A3 is complete.
Whether the memory job is configured as a branch-protection required check is external governance
state and is not proven by this repository.

## P4-B1 implementation ledger

P4-B1 remains package-internal under `com.yo1no.gramarye.magic.definition.store`. Its boundary is
formed by `SkillSavedDataPersistenceSchema`, shared `StrictNbtFramingInput`,
`SkillSavedDataNbtFraming`, `OpaquePendingAttachmentUpdatesBlob`, `SkillSavedDataInnerCarrier`,
`SkillSavedDataCarrierMigrationStep`, `SkillSavedDataCarrierMigrationPlan`,
`SkillSavedDataCarrierMigrator`, their typed migration result／failure, the sole production-plan
provider, `OpaqueSavedDataBlobTokens`, `SkillSavedDataCarrierPersistenceBridge`,
`SkillSavedDataCarrierLoadResult`, `SkillSavedDataCarrierFailure`, and
`SkillSavedDataReadyCandidate`; none of these widens the public Store or carrier API.

The golden framing Gate confirms that the complete unnamed whole root is exactly 26 bytes larger
than the standalone unnamed inner `data` Compound. The approved inner ceiling of 69,206,016 bytes
therefore yields a 69,206,042-byte whole-root ceiling, and the finite NBT quota of 69,206,405 accepts
the exact legal fixture. The strict whole-root reader shares the low-level framing primitive with
P4-A2, enforces exact root／inner fields and trailing EOF before general Compound materialization,
and treats a zero-length `pending_attachment_updates_blob` as the sole canonical no-journal
sentinel. Non-zero pending bytes remain bounded, immutable, defensive, and opaque.

SavedData outer migration exposes only deterministic Store／pending sentinels while their exact
bytes remain in a typed side table; reinsertion validates location, identity, and exactly-once use.
The load bridge runs this outer migration before delegating Store loading to P4-A2, then performs a
full P4-A3 carrier rebuild. `EncodedSkillStoreCarrier.matchesStoreBlob` compares the complete bytes
without exposing or copying a raw Store-sized array, so the immutable Ready candidate contains a
domain Store, matching inner carrier, bounded facts, and the explicit
`outerMigrationApplied || a2RewritePending || !sourceMatchesRebuiltCarrier` rewrite decision.

P4-B1 owns only decompressed strict framing and this fail-closed candidate construction. P4-B2
still exclusively owns gzip and filesystem ingress, the SavedData subclass and Overworld cache
lifecycle, Ready／Quarantined／Unavailable installation, save callbacks, publication, and dirty
state.

## P4-B2-A implementation ledger

All P4-B2-A integration types live in
`com.yo1no.gramarye.magic.definition.store`. The package-private boundary consists of
`SkillSavedDataPrimaryIngress`, its bounded metadata／result／failure types, the strict gzip stream
helpers, and `GramaryeSkillSavedData` with its Ready／Quarantined／Unavailable state. The narrow
public port is `SkillDefinitionStoreService` together with `SkillSubsystemResult`,
`SkillSubsystemUnavailableReason`, and `ControlledSkillPin`; none exposes the Store, carrier,
SavedData adapter, filesystem path, NBT, or gzip internals.

The sole primary resolver is
`server.getWorldPath(LevelResource.ROOT)/data/gramarye_skill_definitions.dat`, for stable storage
name `gramarye_skill_definitions`; all cache access delegates through `server.overworld()`. The
world root and its `data` directory are a trusted server-owned directory boundary. P4-B2-A rejects
symlinks and non-regular files, requires a non-null platform `fileKey`, and compares path fileKey,
size, mtime, and file kind before open, after open, and after parse while also checking the same
channel size. Portable Java exposes no opened-channel fileKey, so this boundary does not claim to
defeat a hostile trusted-directory writer performing an undetectable ABA or same-inode mutation
with restored metadata. Detected replacement／growth／shrink races fail closed; a null fileKey is
quarantined rather than falling back to size／mtime identity, and no untested Windows filesystem is
claimed as supported.

Compressed ingress is exactly `FileChannel` → max+1 `BoundedChannelInputStream` → pass-through
`GzipHeaderVerifier`／per-load bounded failure recorder → caller-owned 8192-byte
`BufferedInputStream` → Commons Compress 1.26.0 in non-concatenating mode → the P4-B1-derived
decompressed max+1 stream → P4-B1. The verifier validates magic, method, reserved flags, optional
field framing and streaming FHCRC without retaining filename, comment, header bytes, exceptions, or
messages. Commons owns deflate plus mandatory trailer CRC／ISIZE. Only a fully consumed B1 Ready
root may continue to member EOF and then prove compressed EOF from that same buffered stream; a
second-member marker, arbitrary trailing byte, or zero padding is rejected.

The locked platform-writer golden confirms that each standalone unnamed NBT root contains the
two-byte zero-length UTF root name after its type byte. The V0 standalone inner fixed framing is
therefore 91 bytes; because the platform whole root and standalone inner both contain that root-name
framing, the authoritative whole-root delta remains exactly +26 and every approved ceiling remains
unchanged. A platform `DimensionDataStorage.save()` fixture round-trips through the strict ingress.

`GramaryeSkillSavedData` is the sole SavedData subclass for this cache entry.
`ServerStartingEvent` performs the custom bounded load once, calls `DimensionDataStorage.set`,
proves an exact cache hit with a constructor／deserializer-throwing `SavedData.Factory`, marks an
already-published rewrite candidate dirty when required, and only then installs the server-identity
marker. `ServerStoppedEvent` removes that exact marker. The service instance is held by the
Gramarye composition root; no static Store, carrier, or adapter truth exists, and reload does not
reinstall.

Ready alone retains the live P3-D Store, matching immutable inner carrier, and rewrite flag.
Quarantined retains only a bounded primary-load failure and remains clean. If Store reclaim has
already mutated and carrier filtering or replacement construction throws a runtime invariant
failure, Unavailable replaces the whole Ready state before dirty is cleared; neither Quarantined
nor Unavailable may save. Controlled reads and pins never affect dirty state. Rejected and zero-row
reclaim preserve both state identity and prior dirty state; positive reclaim snapshots immediately,
filters the existing carrier, publishes the matching replacement with the same opaque pending bytes,
then marks dirty. The save callback reads state once and returns a fresh exact three-field tag from
the prebuilt inner carrier without Store access, encoding, rebuild, reclaim, or migration.

Production never resolves, reads, opens, promotes, deletes, or renames `.dat_old`: absent primary
means empty clean Ready even when old exists, invalid primary remains Quarantined even when old is
valid, and valid primary always wins. The normal GameTest infrastructure now runs five required
tests total and covers startup installation, exact Overworld cache identity, Ready and controlled
Quarantined observations. The fresh-world dedicated smoke proves absent empty Ready stays clean
through shutdown and creates no primary `.dat`.

An already deep-copied and enqueued old save tag cannot be cancelled by a later Unavailable
transition.

## P4-B2-B implementation ledger

P4-B2-B adds only the isolated `p4B2Probe` and `p4B2GameTest` source sets. The former contains the
deterministic fixture, manifest, hashing, file-verification, bounded-summary, command-line, and
packaging-verification helpers; the latter contains one property-dispatched GameTest and its
test-only lifecycle sampler. Each of the ten server modes runs exactly one required GameTest from
the generated `gramarye_p4_b2` structure. Neither source set, that structure, nor a fixture manifest
is present in the production JAR.

The P4-B2-R packaging repair makes `commons_compress_version=1.26.0` the sole dependency-version
truth. The official `jarJar(implementation(...))` seam negotiates exact range `[1.26.0]`, while the
global `additionalRuntimeClasspath` seam supplies every NeoForge 1.21.1 ModDev run. The locked
Minecraft／NeoForge runtime already provides Commons IO 2.15.1 and Commons Lang 3.14.0 under their
distinct module names, so they are not duplicated; Commons Compress optional zstd, xz, brotli,
codec, and ASM paths are not packaged. This repair changes no strict-gzip, SavedData, carrier,
failure-taxonomy, or ceiling semantics.

`jarJar` produces the embedded-input directory consumed by `jar`; the sole deployable artifact is
`build/libs/gramarye-1.0.0.jar`, also selected by `assemble`, `build`, and the Java component
publication. Its parsed `META-INF/jarjar/metadata.json` names exactly
`org.apache.commons:commons-compress:1.26.0`, range `[1.26.0]`, and nested path
`META-INF/jarjar/commons-compress-1.26.0.jar`. The nested original contains
`GzipCompressorInputStream`, `LICENSE`, and `NOTICE`; no Commons Compress class is unpacked or
relocated into the Gramarye JAR root. A real NeoForge 21.1.241 installed-server smoke, with only the
Gramarye artifact in `mods`, loaded that class from the nested JAR, consumed a present legal primary,
reached ready, rewrote it to canonical Ready, and stopped cleanly. That small primary changed from
SHA-256 `1fd1de176cc3d92ec351a010520afc8862b9e64f94e2e2613c00491f80b5b296` to
`5316aac4a2b0f06b22bcb1241186f43a3930fd258d2e6bd90c02adfe76eb5c61`; its canonical Store is
2,486 bytes with two histories and four revisions.

The full fixture is a domain-valid P4-A3 mixed Store whose exact carrier is 66,060,348 bytes, with
eight histories and 64 revisions. Its current-schema source Store differs only in legal Compound
field order: source Store SHA-256
`b5575f0a2341bbac4bbf60b9a10161b4cc10e9ee134414a3c013a821d8919dd8` rebuilds to canonical
`2fc9d556e5189b2b6087feb4287cf7fc781a11d810939a62450511829de4b30a`. The first process loaded
Ready with rewrite required and dirty, retained the old whole root while the platform made its
Store-sized callback/deep copy, waited through `IOUtilities.waitUntilIOWorkerComplete()`, and wrote
the canonical primary. Its 131,814-byte source SHA-256
`b506a64a25aea0e7e589331ee810cd006f97e13d2c9d24187d65d69bdc7da012` became the 131,816-byte
canonical SHA-256 `1f0bb87944bc48667b8b11acb6f8937459882b5b8aa2f220f9c60b98f7880421`.
The separate restart process loaded that same domain and carrier as clean Ready with no rewrite and
performed no unnecessary write.

The hostile-FNAME pair reuses that exact 66,060,348-byte Store carrier and its 131,814-byte legal
noncanonical gzip member. Its test-only builder sets only the gzip FNAME flag, streams 73,268,505
non-NUL ISO-8859-1 `0xE9` bytes followed by the required single NUL terminator, and copies the
original deflate payload and trailer unchanged. The resulting single-member primary is exactly the
inclusive 73,400,320-byte file ceiling, with source SHA-256
`837beda80b72e0cc9baa0a72d2609ec61676c4ae886fecace30b230bd2b84664`; it has no second member or
trailing bytes. Under the fixed 1 GiB heap, the first process installed dirty Ready with rewrite
required and completed the actual platform save. That save produced the same 131,816-byte canonical
SHA-256 `1f0bb87944bc48667b8b11acb6f8937459882b5b8aa2f220f9c60b98f7880421` with exact gzip flags zero.
The separate restart installed clean Ready, performed no unnecessary write, and preserved that
canonical no-FNAME file.

Every full-size and invalid-pair B2-B GameTest child used Java 21 with `-Xms512m`, `-Xmx1024m`, and
`-XX:+ExitOnOutOfMemoryError`. `heap peak` is the sampled used-heap high-water mark. `pool peak sum`
adds independently observed pool peaks that need not be simultaneous and therefore is not compared
directly with Xmx.

| phase | Store bytes | compressed bytes | heap peak | pool peak sum | elapsed ms | checksum witness |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| full first/save | 66,060,348 | 131,816 | 935,887,480 | 1,312,216,992 | 2,476 | `2fc9d556e5189b2b` |
| full restart | 66,060,348 | 131,816 | 872,604,696 | 1,340,080,128 | 2,151 | `2fc9d556e5189b2b` |
| hostile-FNAME first/save | 66,060,348 | 131,816 | 620,570,928 | 1,394,480,776 | 5,211 | `2fc9d556e5189b2b` |
| hostile-FNAME restart | 66,060,348 | 131,816 | 877,744,320 | 1,343,121,696 | 2,294 | `2fc9d556e5189b2b` |
| malformed first | 0 | 138 | 459,015,176 | 492,451,016 | 787 | `c5d53c3d5f24f753` |
| malformed restart | 0 | 138 | 447,540,776 | 497,348,136 | 736 | `c5d53c3d5f24f753` |
| trailing first | 0 | 139 | 452,742,920 | 501,397,712 | 804 | `7c69902157328295` |
| trailing restart | 0 | 139 | 460,125,864 | 494,139,184 | 738 | `7c69902157328295` |
| second-member first | 0 | 276 | 466,505,760 | 495,341,600 | 927 | `bb2fdd957ad9d82d` |
| second-member restart | 0 | 276 | 463,270,336 | 494,203,328 | 754 | `bb2fdd957ad9d82d` |

All ten processes reported heap maximum 1,073,741,824 bytes and initial committed heap
536,870,912 bytes. The three Quarantined pairs preserved primary size, mtime, and complete SHA-256
across first run and restart: malformed
`c5d53c3d5f24f7530f1647392d2069083233747f3e35f008c75901641119847d`, trailing
`7c69902157328295c60d31370731b80b134cd1ebddb6d6c86052f61090546f4a`, and second member
`bb2fdd957ad9d82d80089896cad5bca076dde5829a40b6d92091e96040df99c3`. Their 138-byte canonical
`.dat_old` fixtures also remained unchanged at
`3e54170fefff7267ebbd9ad73d264c1989a8de7d2543eeb7efdc71d609ad4ae6` and were never promoted.

`verifyP4B2Configuration`, `p4B2RuntimePackagingGate`, `p4B2InvalidRestartGate`, and
`p4B2FixedHeapGate` lock the portable configuration, packaged present-primary server, ordinary and
exact-maximum hostile-FNAME valid first／restart pairs, and three invalid paired restarts. The
local aggregate on 2026-07-26 passed 70 tasks in 2m44s without OOME or timeout. The
required-on-failure `P4-B memory gates` CI job
depends on `build` and `P4-A3 memory gates`, has no conditional or allow-failure escape, and runs the
configuration and aggregate fixed-heap gates. The remote `build`, `P4-A3 memory gates`, and
`P4-B memory gates` passed for this closure, including the exact-maximum hostile-FNAME pair.
Repository contents prove those job definitions and dependencies, but whether branch protection
configures any of them as required checks remains external governance unknown／pending.

## P4-C0 quarantine authority ledger

P4-C totality begins only after NeoForge has materialized playerdata, the outer
`neoforge:attachments` value is a `CompoundTag`, and the `gramarye:player_skills` value has reached
the custom `IAttachmentSerializer<Tag, ...>` body. Every non-null Tag delivered at that boundary must
return a non-null Ready or Quarantined state. Whole-playerdata decode failure, a wrong-type outer
attachments value, and information erased or ignored before that call are outside this guarantee.

`MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES` measures the **canonical arbitrary-Tag counting
coordinate** produced by `NbtIo.writeAnyTag` for the complete Attachment value: one Tag type byte
followed directly by the complete Tag payload, with no root-name, Attachment key, enclosing
attachments Compound, whole-playerdata, or gzip framing. `NbtIo.writeUnnamedTag` is prohibited for
P4-C admission because it adds two empty UTF root-name bytes to every non-End Tag. A bounded
long-counting `DataOutput` stops at maximum + 1 before any raw copy and never materializes an
equal-sized byte array. This is a post-materialization bound and does not limit Minecraft's initial
playerdata allocation. It is deliberately distinct from P4-B SavedData unnamed-root framing.
`MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES` continues to measure only the raw `draft_bytes` ByteArray
payload and does not use this Attachment-total coordinate.

An in-bound malformed value becomes PreservedRaw with a bounded machine failure and an immutable
deep copy of the materialized Tag; future writes return `raw.copy()` and guarantee logical NBT-tree
structural preservation only. An oversize value becomes OversizeMarker without retaining raw data
and writes the exact reserved `__gramarye_attachment_quarantine_v0` marker with
`encoded_capacity_exceeded`, observed-at-least maximum + 1, and maximum metadata. The marker itself
is measured with the same `writeAnyTag` coordinate. This is an explicit destructive quarantine: a
subsequent normal player save may replace the original oversize representation, but restart must
remain `Quarantined.OversizeMarker` rather than missing or empty Ready. V0 creates no sidecar, save blocker,
export directory, retry queue, login-denial protocol, mixin, or access transformer.

`CompoundTag` materialization uses last-write-wins map insertion, so P4-C does not claim to detect
duplicate materialized Compound field names. Route-bearing `drafts`, `latest_states`, and
`equipped_slots` remain Lists so their duplicate routes survive until typed validation; duplicate
fields inside `draft_bytes` remain visible to the strict byte parser.

Locked `ListTag.write` canonicalizes an empty list to element type End and count zero while counting.
That inert pre-count element-type byte is outside logical-tree structural preservation; canonical
empty Ready uses End／zero, non-empty route lists require Compound elements, and tests compare logical
structure rather than physical bytes or `getElementType()` for an empty list.

The fixed Ready V0 outer schema remains
`attachment_schema_version`／`drafts`／`latest_states`／`equipped_slots`／`editor`; the quarantine
marker is an alternative representation, not an additional Ready field. Generation remains an
`int`／`IntTag` in `0..Integer.MAX_VALUE`; an absent latest route means empty pointer at generation
zero, an explicit empty pointer with positive generation is retained, and a same-pointer target is a
no-op with no increment, `setData` call, or P4-D journal. Hard-invalid editor indexes quarantine the
whole value, while structurally valid stale editor selections are retained unchanged. Attachment
outer schema, Draft physical encoding, and logical SkillDraft schema remain separate migration axes.

Missing remains default empty Ready. Ready, PreservedRaw, and OversizeMarker cross death and End
clone only through serialize-then-read under `copyOnDeath`; no manual clone copy or sync is added.
The required fixed-heap gate uses `-Xms512m -Xmx1024m -XX:+ExitOnOutOfMemoryError` and covers the
exact-16-MiB PreservedRaw load／save／restart／death／End lifecycle plus the maximum + 1 marker path.

Known limits are explicit: P4-C cannot preserve an oversize original representation, detect
duplicate Compound fields already erased by platform materialization, guarantee physical byte
identity or an empty List's pre-count declared element-type byte, or bound the platform's initial
whole-playerdata materialization. Rejecting the destructive
oversize tradeoff keeps the implementation gate closed and requires separately approved file-level
quarantine or sidecar authority.

## P4-C1 implementation ledger

P4-C1 is confined to `com.yo1no.gramarye.magic.definition.player` plus one narrow public document
seam, `SkillDraftPersistenceFacade`. The player package owns the count-only
`BoundedCountingDataOutput`／`AttachmentTagSize` boundary, immutable
`PlayerSkillAttachmentReady`, `PlayerSkillAttachmentPreservedRaw`, and
`PlayerSkillAttachmentOversizeMarker` states, the exact marker, current physical schema, outer
migration, total `IAttachmentSerializer<Tag, PlayerSkillAttachmentState>`, typed Draft／latest／
equipped／editor records, and the matching prebuilt `EncodedPlayerSkillAttachment`. Every player
top-level type remains package-private; C1 adds no public player service.

The locked `NbtIo.writeAnyTag` golden counts are: End `1`, Byte `2`, Int `5`, empty List `6`, empty
Compound `2`, one nested empty Compound field `7`, and the canonical oversize marker `142` bytes.
The inclusive `16_777_216` boundary succeeds, and the count-only output reports
`observedAtLeast = 16_777_217` without retaining output bytes. The non-End
`writeUnnamedTag` negative control is exactly two bytes larger and remains absent from production.

The serializer admits every Tag delivered to its body by counting first. Oversize input becomes
the bounded marker state without a raw copy; the exact marker is recognized before Ready decoding;
an in-bound expected failure becomes PreservedRaw only after counting and with a defensive deep
copy. Ready owns immutable canonical typed lists and a matching prebuilt carrier, so serializer
write only returns a fresh carrier／raw／marker copy and performs no first-time migration, Draft
encode, or capacity admission.

`SkillDraftPersistenceFacade` is the sole new public top-level type. Its opaque
`EncodedSkillDraft` and typed nested results route current encode and always-migrating load through
package-private Draft physical code. Attachment outer `attachment_schema_version`, Draft physical
`draft_encoding`, and logical `draft_schema_version` remain independent axes. Outer migration sees
at most 32 location-bound sentinels and never Draft bytes; logical Draft migration sees sentinels
and never family-tagged raw payloads; hydration rebinds the existing JSON／NBT context without
SkillDocument migration, payload migration, registry resolution, Store access, or validation.

The five C1 ceilings retain their distinct admission owners: complete Attachment `writeAnyTag`
count, Draft-entry byte capture, Draft route count, latest-state count, and equipped count／slot
range. Generation remains `int`／`IntTag`; `MutationGeneration` is the sole successor arithmetic
owner and returns exhausted at `Integer.MAX_VALUE`. Hard-invalid editor indexes reject Ready,
while structurally valid stale selections are preserved.

## P4-C2-A implementation ledger

P4-C2-A adds the sole permanent player-skill Attachment registration owner,
`PlayerSkillAttachments`, at stable ID `gramarye:player_skills`. It uses the C1
`IAttachmentSerializer<Tag, PlayerSkillAttachmentState>`, supplies a fresh canonical empty Ready
identity for every platform default request, enables `copyOnDeath()` after serializer wiring, and
has no sync handler. `Gramarye` registers it on the mod bus through the single stateless
`PlayerSkillAttachmentService` instance held by the composition root; the Attachment type, holder,
serializer, physical state, and carrier remain package-private.

`PlayerSkillAttachmentPersistenceBridge.freshEmptyReady()` and the universal
`rebuildReady(...)` admission seam are the only C1 additions needed by C2-A. Every rebuild repeats
null, count, duplicate route／slot, slot-range, Draft-carrier pairing, latest pointer／generation,
editor, canonical-order, matching-carrier, and whole-Attachment byte admission before returning a
fresh Ready. `PlayerSkillAttachmentBuildResult` keeps rejection details inside the player package;
the service never constructs Ready or physical NBT itself.

Every service call performs a server-thread check and uses one non-installing
`ObservedPlayerSkillAttachment` observation. Missing reads expose empty semantics without calling
platform `getData`; failed and no-op mutations leave the holder missing; a successful mutation
publishes one complete replacement with exactly one final `setData`. Existing PreservedRaw and
OversizeMarker states map to distinct bounded public unavailable reasons and never become missing
or empty Ready. Draft, equipped, and editor mutations are immutable; they do not allocate skill
IDs, consult the Store, change latest generation, or normalize structurally valid stale editor
metadata.

Latest-pointer preparation treats a missing route as implicit empty pointer／generation zero,
checks expected pointer and `int` generation, gives same-pointer changes a no-op token, and delegates
the sole changed successor arithmetic to `MutationGeneration`. The opaque prepared token binds the
exact server, authenticated player owner, and an internal original state of either Missing or exact
Present Ready identity. Publication revalidates that identity and the expected route state; no-op
publishes nothing, while a valid change performs one final `setData`. C2-A creates no journal,
Store plan, commit, retry, or merge behavior.

The controlled owner is always `SkillOwnerId(player.getUUID())`. The per-player root projection is
an immutable canonical list of latest-present references followed by equipped references; Draft,
base revision, editor, empty latest entries, and owner are excluded. Its maximum is derived from
the already-admitted `256 + 64` entries, cross-category duplicates remain visible, and quarantine
returns unavailable rather than an empty or complete root set. C2-A performs no offline player
enumeration, Store lookup, reconciliation, root-completeness claim, or reclaim invocation.

`PlayerSkillAttachmentGameTests` contributes two required normal GameTests, raising the normal
total from five to seven. They exercise the registered total serializer, fresh defaults,
non-installing missing observation, actual synchronous playerdata save／primary readback／same-UUID
reload, and small Ready／PreservedRaw／OversizeMarker death and End-equivalent clone paths. The
default NeoForge serializer-write／serializer-read copy path produces a fresh state identity for
each clone; no manual clone handler or network synchronization is registered.

## P4-C2-B fixed-heap lifecycle ledger

P4-C2-B adds only test infrastructure. `p4C2Probe` owns deterministic worlds, bounded manifests,
logical-tree／payload checksums, fixture preparation, and external strict playerdata verification;
`p4C2GameTest` owns the single property-dispatched dedicated holder and actual player lifecycle.
Every dedicated run loads exactly main + those two source sets under namespace
`gramarye_p4_c2`; neither source set nor its generated structure enters runtime publication or the
production JAR. The worlds are `build/p4-c2/ready-world`, `preserved-raw-world`, and
`oversize-world`. The preserved world alone reuses the existing P4-B2 full noncanonical Store
builder through its separate preparation process; the six C2 servers do not load P4-A3／P4-B2 probe
classes.

The exact raw payloads are `16_777_211` bytes for a complete `writeAnyTag` count of `16_777_216`,
and `16_777_212` bytes for the first rejected count of `16_777_217`. The latter saves as the exact
`142`-byte canonical OversizeMarker. Ready contains the reviewed mixed JSON／NBT Draft with
RegistryOps context, present and explicit-empty latest routes, two equipped slots, and stale but
structurally valid editor metadata. Each first and restart process performed synchronous
`PlayerList.saveAll()` followed by finite strict readback, one actual death respawn, and one actual
End non-death return. Each path observed exactly one clone and one respawn event, produced a fresh
Attachment state identity, retained its variant, wrote and strictly read back a phase-specific
non-Attachment witness, and required no sleep or direct serializer substitute. PreservedRaw
additionally proved deep-copy alias isolation.

The local fixed-heap run used Java 21 with `-Xms512m -Xmx1024m` and
`-XX:+ExitOnOutOfMemoryError`; all six processes completed their single required GameTest and their
external verifier:

| Process | State | Attachment bytes | Playerdata compressed bytes | Heap peak bytes | Pool peak sum | Elapsed ms | Attachment checksum witness |
|---|---:|---:|---:|---:|---:|---:|---:|
| Ready first | Ready | 1,199 | 1,276 | 455,911,424 | 500,475,904 | 1,186 | `10a2b5a1171a6277` |
| Ready restart | Ready | 1,199 | 1,278 | 457,362,624 | 495,635,648 | 949 | `10a2b5a1171a6277` |
| PreservedRaw first | PreservedRaw | 16,777,216 | 2,157,869 | 963,910,032 | 1,387,726,256 | 4,228 | `e8ba519155fe9501` |
| PreservedRaw restart | PreservedRaw | 16,777,216 | 2,157,870 | 962,181,784 | 1,467,973,528 | 3,435 | `e8ba519155fe9501` |
| Oversize first | OversizeMarker | 142 | 927 | 455,606,272 | 530,579,456 | 1,021 | `125b1abac92c9935` |
| Oversize restart | OversizeMarker | 142 | 924 | 468,832,192 | 492,425,152 | 959 | `125b1abac92c9935` |

Every process reported `heap_max = 1_073_741_824` and initial committed heap
`536_870_912`. `heap_pool_peak_sum` is the sum of each heap pool's independently observed peak;
those peaks need not be simultaneous, so that diagnostic is not compared directly with Xmx. The
sampled whole-heap peak is the Xmx-relevant value in the table.

The full PreservedRaw process simultaneously retained and rewrote the P4-B Store carrier at
`66_060_348` bytes, 8 histories, and 64 revisions. First load required rewrite and dirty save;
restart was canonical, clean Ready. Both phases produced Store checksum witness
`2fc9d556e5189b2b` (full SHA-256
`2fc9d556e5189b2b6087feb4287cf7fc781a11d810939a62450511829de4b30a`). The exact raw payload
checksum is `92dc83c6b53f0c69c481820911eb246d49d6c88413b511eee342eb92d85210fb`;
Ready's canonical logical-tree checksum is
`10a2b5a1171a62773a40edb6ae47d642abf3934a523564ad83edee1eb42c43a1`; and the marker checksum is
`125b1abac92c99354f91ef26014eb8bca6fb51ba899f8659924ccd8ffd448c8d`.

The serialized task chain is prepare → Ready first／verify／restart／verify → PreservedRaw
first／verify／restart／verify → Oversize first／verify／restart／verify. Portable configuration and
JAR gates are exposed through `verifyP4C2Configuration`; the aggregate local workload is
`p4C2FixedHeapGate`. CI adds the required-on-failure `P4-C memory gates` job after `build`,
`P4-A3 memory gates`, and `P4-B memory gates`, with no conditional or allow-failure escape.

## P4-D1 strict journal and Store-port ledger

P4-D1 adds the package-private journal layers `PendingAttachmentJournalSchema`,
`PendingAttachmentJournalWireScan`, `PendingAttachmentJournalFraming`,
`PendingAttachmentJournalMigration`, `PendingAttachmentJournal`,
`PendingAttachmentJournalState`, and `PendingAttachmentJournalLifecycle`. Zero reuses the exact
zero-length pending handle without invoking the parser or Store audit. Nonzero framing is precisely
`NbtIo.writeAnyTag` with no root name. The iterative scanner covers every NBT payload kind, uses
modified UTF, records bounded duplicate evidence while preserving late truncation／trailing
precedence, and extracts only a non-negative schema version before strict current decode. Legacy
materialization, when a production edge exists, is finite and follows exact adjacent migration
coverage; the V0 production plan currently has no edge.

The current immutable journal retains input order through validation, then canonicalizes by owner
UUID, SkillId UUID, and target generation. It rejects route mismatch, duplicate stable keys,
generation／pointer gaps, branches, non-final append, 4,097 raw entries, and encoded bytes above
1,048,576. Its canonical encoding owns one `OpaquePendingAttachmentUpdatesBlob`; source and
canonical bytes are never both retained as competing truth. Rewrite is permitted only after domain
validation and exact Store-target audit.

SavedData Ready now explicitly carries an `Uninitialized` or `Installed` journal lifecycle.
Installed contains operational `Ready` or bounded `Unavailable`; the latter preserves the exact
P4-B inner carrier and pending handle, remains non-dirty, keeps controlled Store read and pin
available, and makes submission, authority, roots, and clear unavailable. Reclaim carries the exact
journal-lifecycle identity into its replacement, while the save callback continues to use only the
prebuilt inner carrier.

`SkillDefinitionStore` owns both the one-history authority observation and the distinct-SkillId
target audit. The nominal audit proof is exactly `AuditedExisting` or
`ConditionalOnExactCommit`. The conditional proof is installed only in a prospective journal
Ready, binds the exact base journal／carrier update／owner／route／target／commit, and clears all heavy
base bindings when satisfied immediately before publication.

`SkillDefinitionStoreSubmissionPort` is the only new public top-level and the only production
caller of `SkillDefinitionStore.commit`. One instance is owned by `SkillDefinitionStoreService`.
Its eight methods expose bounded authority, status, root, prepare, commit, and prefix-clear results;
opaque handles are port/server bound, single-use, and clear their payload on every owned terminal
attempt. Preparation performs all Store-carrier, journal append, canonical encode, pending sharing,
inner-carrier capacity, proof, and fallback work before mutation. A typed Store rejection publishes
nothing; exact success publishes the prebuilt Store／journal Ready and then dirty. A defensive
postcommit mismatch publishes prebuilt SavedData Unavailable and `setDirty(false)`. Bootstrap and
prefix clear likewise publish before their required dirty delta.

The two additive P4-C seams are `isChangedGenerationSuccessor`, which delegates to the sole
`MutationGeneration` arithmetic owner, and exact-server `PreparedPlayerSkillTransition.isBoundTo`.
D1 adds no authenticated `ServerPlayer` facade, policy provider, Attachment transition
publication, recovery event, offline root enumeration, Store reclaim caller, network surface,
Gradle source set, or CI job. Repository contents do not establish branch-protection required-check
configuration, which remains external governance unknown／pending.

The completed P4-D2 read-only design review forces the D2-A／D2-B split, authorized D2-A
implementation, and requires no D0.1 authority patch.

## P4-D2-A completed implementation ledger

D2-A keeps `SkillDefinitionStoreSubmissionPort` as the sole production Store commit owner and keeps
its eight public methods unchanged. Its nested preparation taxonomy now distinguishes document,
revision, history, Store, journal-count, and journal-byte capacity from Store-carrier,
journal-chain, SavedData-carrier, plan／transition, authority, server, and normal no-op failures.
The mapping consumes the existing typed A1／A2／A3／journal results through exhaustive switches; the
SavedData inner carrier has no independent capacity branch because the existing maxima prove
`68,157,531 <= 69,206,016`.

`PlayerSkillAttachmentService.prepareLatestTransitionToCurrent` observes the Attachment once and
derives the exact current pointer／generation without installing a missing default. Its new
non-mutating currentness query and the existing publication path share one private validator;
currentness does not consume the prepared token, build a replacement, or call `setData`.

The actual D2-A submission-package top levels are public `SkillSubmissionPolicyProvider`,
`SkillSubmissionPolicySnapshot`, `SkillDraftCreationService`, and
`SkillSubmissionCompositionOutcome`, plus package-private
`DefaultSkillSubmissionPolicyProvider`, `RandomUuidSkillIdSource`, and
`SkillSubmissionPreparationPipeline`. The default provider alone owns the immutable Unlimited／
`MagicPolicyLimits.DEFAULTS` snapshot, the UUID adapter alone mints random IDs, and Draft creation
uses the controlled P4-C service without reservation or Store access. The eleven composition
outcomes preserve the exact warning-only report reference after preparation and expose no plan,
document, carrier, journal, or raw state. The package-private pipeline is the only new C1–C4
orchestration seam and retains every existing exactly-once stage boundary.

D2-A has no authenticated facade, Gramarye D2 wiring, additional D2 GameTest entry or holder,
recovery event, fixed-heap source set, Gradle／CI change, offline roots, reconciliation, reclaim, or
network code. Its seam assertions reuse the existing P4-C normal GameTest holder without changing
the required-test count. Local full regression and the existing A3／B／C configuration and memory
Gates passed. The D2-A production commit is present at `HEAD`／`origin/main`, and the externally
reported remote `build`, `P4-A3 memory gates`, `P4-B memory gates`, and `P4-C memory gates` jobs all
passed. D2-A is therefore complete. At D2-A closure, D2-B was ready for implementation and had not
yet created the authenticated facade, Gramarye composition-root wiring, or normal submission
GameTests.

## P4-D2-B completed implementation ledger

The actual D2-B facade is public-final `SkillDefinitionSubmissionService`, with authenticated
`submit(ServerPlayer, SkillId)` as its only public domain operation. It is stateless, derives owner
only from the server player UUID, and uses a production registry-backed P3-C pipeline. `Gramarye`
owns one UUID source, Draft-creation service, default policy provider, and submission facade next to
the existing P4-C service and D1 Store port; it exposes no global locator and installs no event.

One attempt has the strict runtime order Draft lookup → C1 precheck → Store authority observation →
C2 authority check → policy snapshot → C3 prepare／C4 map → P4-C transition prepare → D1 preflight →
P4-C currentness recheck → D1 commit → P4-C publication. Runtime counters lock every selected step
to one invocation and every downstream short circuit to zero. Thus C1 invalidity wins over journal
unavailability, C2 rejection wins over policy failure, policy is sampled exactly once only after C2
passes, currentness is checked after preflight and before commit, and publication is unreachable
until an exact Store `Committed(target)` result.

All transition, persistence-capacity, Store-domain, prepared-base, unavailability, postcommit, and
Attachment-publication results map exhaustively into the existing composition vocabulary. Every
post-Prepared outcome retains the identical warning-only P3-C report; the Draft remains present and
unchanged. Publication failure does not roll back Store, clear the journal, retry, or reprepare.
Two normal required GameTests cover full success and postcommit Attachment drift, raising the normal
required total from seven to nine. `scripts/verify-p4-d2-configuration.sh` plus the D2-B API／phase
gates lock this local surface without a P4-D source set, Gradle／CI change, recovery listener,
offline-root／reconciliation／reclaim code, or network surface. All nine normal required GameTests
passed, as did the local full regression and existing fixed-heap Gates. The D2-B production commit
is present at `HEAD`／`origin/main`, and the externally reported remote `build`, `P4-A3 memory gates`,
`P4-B memory gates`, and `P4-C memory gates` jobs all passed. D2-B and P4-D2 are therefore complete.
The completed D3 read-only design review forces the D3-A／D3-B split.

## P4-D3-A closure ledger

Production server startup now uses one `SkillDefinitionStoreService` callback with the exact order
`install(server)` then `submissionPort.bootstrapJournal(server)`. The install primitive itself
remains reusable and unbootstrapped. Journal data failures finish as fail-closed journal
Unavailable while Store reads/pins and server startup remain available; duplicate lifecycle or
threading failures remain fail-fast.

The D1 Store port adds only `observePendingRecovery(server, requestedOwner)`. It first selects that
owner's bounded canonical journal subset, performs the existing distinct-SkillId target audit, and
returns immutable recovery chains or bounded target/unavailable results without exposing journal
entries, bytes, Store, carrier, or foreign owners. P4-C adds only
`observeLatestStates(ServerPlayer)`: one server-thread gate and one `observeChecked`, Missing without
default installation, canonical explicit pointer/generation tuples for Ready, and Unavailable for
either quarantine.

One composition-root-owned `SkillSubmissionRecoveryService` registers only
`PlayerLoggedInEvent`. It derives the owner from the authenticated UUID, observes journal before
Attachment, and compares exact pointer-plus-int-generation tuples. Base replays the complete chain
without clear; an intermediate target clears its confirmed prefix before replaying the suffix; a
final target clears the complete route with no Attachment transition; a third state fails closed.
Replay uses only P4-C prepare-to-current, currentness, and publication, never submission, Store
mutation, retry, local generation arithmetic, or post-replay clear. Multi-chain processing is
canonical and first-failure fail-fast, retaining earlier progress without rollback. Outcomes carry
only bounded clear/replay counts, identifiers/codes, and an optional bounded RuntimeException class
name from the controlled P4-C boundary; `Error` is not caught.

Three normal required GameTests use actual playerdata save, removal, same-UUID `placeNewPlayer`, and
the production login listener for base, intermediate, and final recovery. They verify final
Attachment tuples, unchanged Draft and Store/carrier identity, retained or cleared journal suffixes
and roots, and dirty behavior. Together with the prior nine, all 12 normal required GameTests pass
locally. The D3-A unit/API/portable gates keep Gradle, workflow, D3-B source sets/tasks, offline
enumeration, root indexing, reconciliation, Store reclaim, and network/sync absent.

D3-A's production commit is present at `HEAD`／`origin/main`. Its local full regression, all 12
normal required GameTests, and the existing P4-A3／P4-B／P4-C fixed-heap Gates passed. The externally
reported remote `build`, `P4-A3 memory gates`, `P4-B memory gates`, and `P4-C memory gates` jobs all
passed, so D3-A is complete. At D3-A closure, D3-B became ready for implementation.

## P4-D3-B implementation and closure evidence ledger

D3-B is confined to the isolated `p4D3Probe` and `p4D3GameTest` source sets with eleven and two
exact-reviewed Java files respectively. The dedicated runtime contains only `main` plus those two
outputs; no JUnit, ordinary `test`, or prior A3／B／C GameTest output enters it. Production Java and
resources have no D3-B diff, and the production JAR remains free of all D3 fixtures.

The production Store restore and A2／A3 encoder create the D3-specific starting carrier at exactly
66,060,348 bytes, 2,048 histories, and 4,095 retained revisions, with SHA-256
`21fbe23089d8cccc6fc835678e20e89ec23a3b4d686b94af50fcec110396c303`. Of those histories,
2,047 have two revisions and one has one; the deterministic owner distribution remains within the
256-per-owner and 4,096-global ceilings. The production journal model／writer creates 4,095 entries,
1,048,324 bytes, and 4,095 roots with SHA-256
`236be560c7610b3bc29c0d7dc22daab1dd27bfd8e1fc895d0d007d115545cea2`. One actual authenticated
D2 submission adds the 4,096th route and produces exactly 1,048,538 bytes／4,096 roots, SHA-256
`dec06aad3b931bb92d8f68122500006e35cb70105b0fd934f4208a1b0a79f687`, without handwritten
Store data or journal padding.

Paired worlds cover D through J1. D／E prove canonical disk states plus production ordering, not
actual power-loss interruptions; both restart from expected playerdata and replay while retaining
the pending journal. F loads a persisted final pointer and clears without Attachment publication.
G hard-halts after replay but before playerdata save; H hard-halts after in-memory clear／dirty but
before SavedData reaches disk. Their restarts respectively replay again and idempotently reclear the
old disk journal. I retains all state on `THIRD_STATE`. J1 keeps an invalid persisted journal as
bootstrap Unavailable while controlled Store reads／pins remain usable. Package-private J2 separately
proves defensive post-bootstrap target re-audit maps to `TargetInvalid`; it is not a natural restart
case. Exactly two `Runtime.halt(0)` call sites exist, both in the D3 dedicated holder. External
verifiers strictly reload Store, journal, and playerdata against bounded 4 KiB manifests.

The combined first JVM retains the current and prospective Store／carriers, current and prospective
journals, roots, prospective inner carrier, platform whole-root deep copy behind the existing IO
latch, P3-C plan／validated definition／warning-only report, current and prepared Attachment states,
D1 prepared handle, and D2 dependencies while traversing the actual authenticated D2 facade. It
neither constructs the plan／definition nor calls Store commit directly. With Xms 512 MiB, Xmx 1
GiB, and ExitOnOOME, its sampled peak was 888,783,336 bytes, pool-peak sum 1,452,246,528 bytes, and
elapsed time 6,785 ms. The committed Store was 66,060,980 bytes／2,049 histories／4,096 revisions and
the journal was 1,048,538 bytes／4,096 entries／roots; Store and journal witnesses were
`9441d813d3210b56` and `dec06aad3b931bb9`.

The same fixed-heap restart clears the canonical-leading FINAL chain before replaying the BASE
chain, then saves, removes, and reloads the same UUID so the now-final BASE chain clears without a
second replay. Other owners remain pending. The final journal is 1,047,514 bytes／4,092 entries／roots
and the Store remains 66,060,980 bytes with SHA-256
`9441d813d3210b56243d4d777a3a008c21a2300fc0a00bb9dc8a2dfbfa81b06a`; the restart sampled
806,879,232 bytes with a 1,201,683,760-byte pool-peak sum in 4,256 ms. Pool-peak sums are diagnostic,
not a comparison-to-Xmx pass criterion.

Gradle supplies exactly 16 fixed-heap server configurations, each followed by an external verifier,
in one immediate-`dependsOn` chain beginning with world preparation. `p4D3FixedHeapGate` depends
only on the last verifier, and the full local chain passed in seven minutes without OOME or timeout.
The executable minimal-PATH portable verifier, `verifyP4D3Configuration`, JUnit API／phase gates,
production no-diff scan, task-order scan, and JAR isolation scan lock the boundary. CI adds `P4-D
memory gates`, dependent on `build` and the A3／B／C memory jobs, with a 45-minute timeout and no
conditional or allow-failure escape. The D3-B implementation commit is present at
`HEAD`／`origin/main`. The externally reported remote `build`, `P4-A3 memory gates`, `P4-B memory
gates`, `P4-C memory gates`, and `P4-D memory gates` jobs all passed. Branch-protection
required-check configuration remains external governance unknown／pending.

The post-change local regression also passed all 1,024 JUnit tests with zero failures, errors, or
skips; all 12 normal required GameTests; the dedicated-server smoke; the P4-A3, P4-B (including the
packaged runtime), and P4-C fixed-heap Gates; every phase configuration verifier; production-JAR
isolation; warning-mode production compilation; and the final static／diff scans.

Together D0 through D3 now close journal framing／migration／operational state, Store authority／
preflight／commit／publication, authenticated submission and report identity, login recovery with
prefix clear／replay, and the D–J1 paired-restart／J2 defensive verification boundary. D3-B, D3, and
P4-D are complete. At P4-D closure, P4-E was ready only for read-only design review; no P4-E implementation, offline
root-completeness proof, Store-reclaim composition, or general reconciliation exists. P4-D does not
claim a cross-SavedData／playerdata transaction or fsync durability.

## P4-E0 authority and research closure ledger

The compact [P4-E0 bounded root-audit authority boundary](P4-E0-root-audit-boundary.md) records the
research lineage, exact formal-evidence identity, product-policy adoption, heap headroom, and phase
gates. P4-E0-A exposed the missing authority; R1, R2, R2R, and R2Q supplied isolated exploratory
instrumentation, clean-revision regeneration, a locked candidate, 29-case formal evidence, and
read-only adjudication. The formal run completed one exact case, 25 independent MAX+1 cases, and
three DataVersion controls without OOME or timeout. That evidence supports, but does not universally
prove, the adopted `BALANCED_V0_1536_QUALIFICATION` product boundary.

E0-B places the exact 25-dimensional vector, counting coordinates, strict playerdata truth
selection, zero-DFU policy, closed V0 family inventory, memory-only index, defer-to-login
reconciliation, same-ServerStarting fresh-complete reclaim composition, and product-selected
1,536-MiB heap floor into the P4 amendment and synchronized lower authorities. The documentation-only
authority patch is commit `be4dc13bd9ae651b1b99999c06cecf67595a0cdd`, tree
`cdbc3e2d591754c0d436af4e521228064ba3f3f2`; its closure is commit
`c7a2aaf01161550758feaeb6b0a73a277d4cbe4e`, and both commits are reachable from `main`／
`origin/main`. Maintainer-provided evidence
records remote `build`, `P4-A3 memory gates`, `P4-B memory gates`, `P4-C memory gates`, and
`P4-D memory gates` as PASS. Those jobs did not rerun the R2Q formal study and do not replace its
gitignored official evidence. At the E0-B closure revision, production code and official evidence
were unchanged and E1／E2／E3 implementation had not started. That is superseded historical wording;
the later current status is recorded below. Historical P4-D0 and P3-C phase-status snapshots remain
valid as history; this section is the later current-status index for P4-E.

The first P4-E1 read-only design review then stopped because E0-B had not assigned the already
materialized integrated snapshot an exact counting／freshness coordinate. Its Stop Rule prohibited a
ledger edit, leaving the stale `OPEN` label. P4-E0-B.1 corrected that status and fixed the four-state
selection matrix, logical unnamed-Compound width, counter applicability, post-DFU/no-DataVersion
rule, mutable-alias threat boundary, and final identity freshness witness.

The documentation-only B.1 authority patch is commit
`1b0832e9f8b42891654d15b364a18cd509653acd`, tree
`950d6bc0337d83c298db909ee342b38f22175759`, on `main`／`origin/main`. Its exact changed-path
scope is:

```text
docs/architecture/P4-0-persistence-boundary.md
docs/architecture/P4-E0-root-audit-boundary.md
docs/codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md
docs/codex-spec/18_P4持久化與組合修正案.md
docs/codex-spec/Codex_實作總規格Prompt.md
docs/codex-spec/NeoForge1.21.1_詳細實作步驟.md
```

Local `verifyPlatformBaseline`, `compileJava`, and `test` passed with 1,181 unit tests and zero
failures, errors, or skips. The official six-file R2Q evidence set and SHA-256 manifest remained
unchanged and verified successfully; B.1 did not rerun the formal study. Maintainer-provided
evidence records remote `build`, `P4-A3 memory gates`, `P4-B memory gates`, `P4-C memory gates`, and
`P4-D memory gates` as PASS for this authority commit. The commit changed no production code,
numeric maximum, heap floor, or R2Q evidence. Branch-protection required-check configuration remains
external governance unknown. At the B.1 closure revision this opened only P4-E1 read-only design
review, not implementation. That is superseded historical wording; the renewed review later passed
and the first E1-A attempt then stopped at the B.2 conflict below.

P4-E0-B.2 is the documentation-only correction discovered by the approved E1-A implementation
attempt. It makes
`HotSpotDiagnosticMXBean -> getVMOption("MaxHeapSize") -> VMOption.value -> strict canonical
base-10 nonnegative long` the sole normative heap-floor observation. The unchanged floor is
`MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES = 1_610_612_736` bytes: lower is `HEAP_FLOOR_NOT_MET`,
at／above is
`QUALIFIED_FLOOR_PRESENT`, and an absent／invalid／overflowing observation or approved observation
`RuntimeException` is `HEAP_FLOOR_UNVERIFIABLE`; `Error`／OOME are not caught. Runtime heap and
collector-pool values are diagnostic only and never fallback, min／max, tolerance, or authority
inputs. Both nonqualified statuses return Incomplete before journal／directory／source work and keep
all admission／root／Store audit／reclaim／mutation counts at zero while startup continues.

The process controls are 1,536 MiB G1／Parallel／Serial／ZGC qualified at effective floor; the locked
Temurin 21.0.8+9／macOS aarch64 1,535 MiB G1 alignment-positive qualified control; the 1,024 MiB G1
real below-floor control; and a pure injected floor − 1／floor／floor + 1 comparator. They add no
ceiling. B.2 leaves the R2Q profile／case plan／25 maxima／floor／29 results／six-file official evidence
unchanged and does not rerun the study. The stopped E1-A worktree was externally checksummed and
replay-verified before this clean patch and is not part of the documentation commit. The P4-E3
production-shaped fixed-1,536-MiB first／restart Gate remains mandatory.

The B.2 authority patch is commit `005cb43ac4feb875e28c346ca8ceb6ba256c1661`, tree
`3e8c621edfa92bd9e986f6fd508cc95c5b50689d`, on `main`／`origin/main`. Its exact changed-path scope
is:

```text
docs/architecture/P4-0-persistence-boundary.md
docs/architecture/P4-E0-root-audit-boundary.md
docs/codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md
docs/codex-spec/18_P4持久化與組合修正案.md
docs/codex-spec/Codex_實作總規格Prompt.md
docs/codex-spec/NeoForge1.21.1_詳細實作步驟.md
```

The compact [P4-E0 ledger](P4-E0-root-audit-boundary.md) records the same evidence. Local platform
baseline, production compile, and all
1,181 unit tests passed, as did Markdown／link／conflict／scope／production-no-diff scans. The exact
six-file R2Q evidence and its HEAD／tree／study identity remained unchanged and checksummed; the
formal study was not rerun.

GitHub Actions run `31160683149`, bound to the exact authority commit, completed `build`,
`P4-A3 memory gates`, `P4-B memory gates`, `P4-C memory gates`, and `P4-D memory gates` successfully.
Branch-protection required-check configuration remains external governance unknown. The external
E1-A archive is not part of either documentation commit. At that closure revision it could be
restored only after the closure commit's own remote jobs passed. Those prerequisites later passed;
the controlled restore recorded below then used fresh diff review, preflight, and heap child-matrix
verification rather than old test evidence.

P4-E1-A has now completed that controlled local restore and fresh re-preflight. The clean base was
commit `5e97b278c4e51bab64c00ebbf3ec453f9cdeb825`, tree
`6f6585b6e0b997bb09d515fc9520b79b0f0d57af`, with `HEAD == origin/main` and zero ahead／behind.
The untouched external archive was
`/private/tmp/gramarye-p4-e1-a-stopped-20260807TYoCp4R`, manifest SHA-256
`c2ed4c95a768230b1bac914d2268e27d04bae08a7c95df17841b036bf7c4c079`. Its unique 39-entry
manifest, per-file hashes, 14-path tracked patch, empty staged patch, 29-path untracked inventory,
source HEAD／tree, stop reason, and phase scope passed before a clean non-`--3way` restore. Restored
tracked bytes matched patch SHA-256
`3c5f2c7e0adcbe6a3c10422d1795b6135d7c63b4848c5282d22424fa062c6d6c`, and every untracked
per-file hash matched the archive. No authority, Gradle, workflow, resource,
formal-evidence, user-world, E1-B, E2, or E3 path was restored.

The reviewed implementation is commit `55755da795aff5a12c29234918f5231acb1165b7`, tree
`c3ffb1c67726c0dc4297f96e7996eb7fa704d4b8`, and is present at `main`／`origin/main`.

Fresh adjudication implements only E1-A source-local admission. Effective HotSpot `MaxHeapSize` is
the sole heap verdict input, and only its package-private qualified capability can create the
25-counter budget. The Java 21 child matrix qualified G1／Parallel／Serial／ZGC at `-Xmx1536m` with
effective `1_610_612_736`; G1 `-Xmx1535m` aligned to and qualified at the same floor; G1
`-Xmx1024m` observed `1_073_741_824` and was below floor. Parallel and Serial diagnostics were
respectively `1_431_830_528` and `1_556_938_752`, proving `Runtime.maxMemory()` does not affect the
verdict. Qualified children derived zero source-work calls from the checked sum of all 25 budget
counters; the below-floor child received no qualified capability or budget and reported zero source
work. A nonzero sentinel locks the qualified derivation. Pure floor − 1／floor／floor + 1 plus
unavailable-observation regressions passed.

The local implementation includes the checked 25-counter budget, bounded directory and
primary／old admission, the shared P4-B strict single-member gzip core, streaming player-NBT scan,
integrated single-tree traversal, P4-C serializer-equivalent pure admission, and identity-bound
online Attachment observation. It builds no global inventory, index, root snapshot, grouped Store
audit, or later-phase service, and performs zero Store dirtying／reclaim, journal mutation,
Attachment `setData`, playerdata write, event registration, background work, or network work.
All 25 counter coordinates passed inclusive MAX, MAX+1 rejection, and checked-`long` overflow.
P4-B and E1 use one strict gzip core with no classification or EOF drift. P4-C serializer and
pure-admission classifications remained equivalent; E1 rejection performed zero raw copies. The
closure counters record E1-A mutation `0`, Store dirty delta `0`, and Store reclaim calls `0`.
Actual global journal／disk／online／Store ordering remains E1-B responsibility. Targeted tests and
all 1,282 unit tests passed with zero failures, errors, or skips; normal GameTest passed exactly
12／12. Dedicated smoke, portable／phase verifiers, and all existing P4-A3／P4-B／
P4-C／P4-D fixed-heap Gates passed locally in full; production JAR isolation held. The P4-C run's
Log4j rollover warning was a generated-output `NoSuchFileException` while deleting
`logs/debug-5.log.gz`, not a Gate failure, OOME, timeout, or semantic failure; the confirmed
rebuildable root `logs/` output was precisely removed. Official R2Q evidence remained byte-identical
and was not rerun. Maintainer-provided evidence records remote `build`, `P4-A3 memory gates`,
`P4-B memory gates`, `P4-C memory gates`, and `P4-D memory gates` as PASS. P4-E1-A is complete; the
E1-B read-only design review subsequently stopped at the online source counter applicability
authority gap. At that stop E1-B implementation had not started; E2／E3 remained blocked, and P4-E
remained incomplete.

P4-E0-B.3 is the documentation-only correction for that gap. It fixes
`ONLINE_PLAYER_ATTACHMENT` as a source kind within `PLAYER_SKILL_ATTACHMENT`, per-UUID precedence
`online > integrated > disk`, `relevant_records` once per selected authoritative owner UUID, and a single UUID-natural owner
order. Online uses only E1-A's already-admitted Missing／Ready／Quarantined observation: per-file
counters are not applicable, byte／structural aggregates and admission contribute zero, and Ready
contributes actual latest／equipped claims. Exact initial／final player／server／presence／state witness
is required, with final freshness only after journal claims and grouped Store audit on a
Complete-candidate path. It cannot overwrite an earlier terminal failure.

B.3 does not change the 25 maxima, the effective-`MaxHeapSize` floor, the R2Q profile／case plan／
identity, or official evidence, and it does not rerun R2Q or change production. R2Q did not naturally
execute online players; E3 must therefore run online Missing＋Ready／exclusion／freshness in the same
1,536-MiB envelope, or provide a machine-checked domination proof plus an actual freshness runtime
test, while retaining relevant 2,048 and raw roots 65,536 exact. The authority patch was committed as
`e23a2a6c0df298315fc726ec509d3f953d559a08` with tree
`ce1d6d379c763ed2824f831f6a1e81c73c3fec65`, pushed to `main`, and qualified by exact-SHA workflow
run `31251807408` attempt 1. Its `build`, `P4-A3 memory gates`, `P4-B memory gates`, `P4-C memory
gates`, and `P4-D memory gates` jobs all completed successfully. B.3 is complete.
The pre-patch ledger still said `OPEN` only because the stopped review's Stop Rule prohibited a
documentation change; B.3 records the exact stop. At the B.3 closure revision, a fresh E1-B
read-only design review opened from preflight. That `OPEN` state authorized only that review, not
implementation. The renewed review subsequently stopped at the A.1 tag-free bridge Gate.

## P4-E1-A.1 implementation closure ledger

The renewed E1-B review stopped at the tag-free P4-C admission bridge Gate. A.1 implements one
sealed store-owned capability, one package-private `Tag`／provider binding, and immediate disk／
integrated adapters into the existing unique P4-C semantic admission core,
`PlayerSkillAttachmentAdmission.admit`. The public boundary
exposes neither `Tag` nor raw roots. Inputs are single-use and cleared before checks; admitted
projections expose only a bounded count plus single-use ordered drain／discard. E1 performs zero raw
copies, re-encodes, extra whole-tree traversals, mutations, dirty operations, journal operations,
or reclaim calls.

Local targeted bridge／visibility／API regressions passed exactly 18／18, and full regression passed
with 1,300 unit tests and zero failures, errors, or skips. Normal GameTest passed 12／12, as did
dedicated smoke, the existing A3／B／C／D configuration and fixed-heap Gates, phase／portable gates,
`javap`, and production-JAR isolation.

The implementation commit is `5fea6f36aff2512ed7e232e45d9bfbd3cc0ad2ef`
(`feat(persistence): add tag-free root audit admission bridge`) with tree
`9e5f87e1063b4f4a42ef4c4b6bf7bbc2b7a85cdc`. Its exact stat is 20 files changed, 1,995 insertions,
and 84 deletions: 3 production Java paths, 8 test／phase-gate paths, 7 portable-verifier paths, and
these 2 architecture ledgers. It was pushed to `main`.

Exact-SHA Build workflow run
[31291725341](https://github.com/yo1no/Gramarye/actions/runs/31291725341), attempt 1, is the unique
run for that implementation SHA. Its exact `build`, `P4-A3 memory gates`, `P4-B memory gates`,
`P4-C memory gates`, and `P4-D memory gates` jobs all completed successfully. The reviewed A.1
boundary retains one public sealed source, one package-private final `Tag`／provider binding, zero
public／protected `Tag` exposure, and no public raw-root collection. E1 raw-copy calls are zero; the
unique semantic core, source single-use claim／clear, and projection reserve-before-drain contract
remain enforced. The actual global reservation composition remains E1-B work. E1-B production
types remain absent, and A.1 adds zero mutation, dirty, or reclaim call-site delta. Official R2Q
evidence and checksums remained unchanged and no R1／R2／R2Q study or smoke was rerun. Gradle,
workflow, resources, and authority deltas are zero.

## P4-E1-B read-only design review closure ledger

The renewed P4-E1-B read-only design review is PASS. Its clean review base was `main` commit
`468ac130b2441456c4213b4fc213d62c34316fc2`, tree
`eb6395cbec748e892e67fa23bfa53bd52893811e`, with `HEAD == origin/main`. The prior closures are
P4-E0-B.3 commit `ac28d456780047aa210a3b687b418561faed9ee7`／tree
`3270d897800c55ce90d332e3c03031f58b429d18`／run `31253153118`, P4-E1-A commit
`b55e2440d1cf947de154f7cb703e475b674951d0`／tree
`423249daf8adb1860443942bf5c5cb2d611f0152`／run `31241737755`, and P4-E1-A.1 commit
`468ac130b2441456c4213b4fc213d62c34316fc2`／tree
`eb6395cbec748e892e67fa23bfa53bd52893811e`／run `31292884093`. All are reachable from
`origin/main`; each exact-SHA workflow contains five successful jobs: `build` and the P4-A3／B／C／D
memory gates.

No Stop Condition was hit. The active amendment and synchronized lower authorities now supply the
complete inventory, arbitration, counter, freshness, grouped Store-audit, index, result, and E3
handoff contract. The completed A.1 bridge closes the last public-boundary risk by providing a
tag-free, single-use P4-C admission projection. The review requires no authority amendment,
persistent-schema change, numeric change, network surface, second persistent truth, offline write,
chunk force, or background work.

The implementation split is mandatory and sequential:

- **P4-E1-B1** owns the compile-time closed inventory and exact provider coverage; the global
  `online > integrated > disk` arbitration and one UUID-natural owner order; ordered player and
  journal raw-root capture; the raw-claim capacity check before append and before deduplication; and
  the directory／file／source plus online／integrated freshness witnesses. Its only success value is
  a package-private, single-use, unpublished capture. That capture may carry a single-use final
  freshness check for later orchestration, but it cannot escape as a public result or index value.
- **P4-E1-B2** alone consumes the capture, performs grouped exact-reference／expected-owner Store
  audit, invokes final freshness after that audit, publishes the bounded public audit result,
  maintains the memory-only index, and creates the ephemeral same-call-chain `Complete` handoff.
  The index retains neither the B1 capture, source witnesses, nor unaudited raw-claim authority.
  After B2-A audit and B2-B freshness pass, the original segmented `SkillReference` backing may
  transfer into index ownership as audited references; no second full root vector or public
  `Complete` permit is retained.

B1 must not construct `SkillRetentionRootSnapshot`, call raw or controlled reclaim, perform grouped
Store audit, publish raw roots, add the public audit result, or build the memory-only index. B2
remained blocked until B1 closure; that prerequisite has closed. Both remain within E1's zero
player／Store／journal mutation, zero dirty, and zero reclaim boundary. B2's handoff is not a
retention snapshot; E3 alone may convert a fresh local Complete handoff to
`SkillRetentionRootSnapshot.Complete` and call controlled reclaim exactly once.

The fixed `-Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError` P4-E3 production-shaped first／restart
Gate remains mandatory and cannot be waived by this review, the split, R2Q, or existing memory jobs.
Branch-protection required-check configuration remains external governance unknown. This closure is
documentation-only and starts no B1 or B2 implementation.

## P4-E1-B1 implementation closure ledger

The implementation commit is `011658f122809af7f9e63f40c980587d3b3d4c76`, tree
`55f55927e0045a96afef1147e48de55343cb7a3f`, with the exact committed stat `34 files changed,
2,571 insertions(+), 33 deletions(-)`. Its committed scope is exactly these 34 paths:

```text
M docs/architecture/P4-0-persistence-boundary.md
M docs/architecture/P4-E0-root-audit-boundary.md
M scripts/verify-p4-c2-b-configuration.sh
M scripts/verify-p4-d1-configuration.sh
M scripts/verify-p4-d3-a-configuration.sh
M scripts/verify-p4-d3-configuration.sh
M scripts/verify-p4-e0-r-configuration.sh
M scripts/verify-p4-e0-r2q-configuration.sh
M scripts/verify-p4-e1-configuration.sh
M src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java
M src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSourceObservation.java
M src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditBudget.java
A src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture.java
M src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1IntegratedSnapshotTraversal.java
A src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PendingJournalObservation.java
M src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataDirectorySnapshot.java
M src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataSourceSelector.java
A src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1RawClaimBuffer.java
A src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1RootSourceFamily.java
M src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceFailure.java
A src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceInventory.java
M src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java
M src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort.java
M src/test/java/com/yo1no/gramarye/magic/definition/store/P4A2ApiGateTest.java
M src/test/java/com/yo1no/gramarye/magic/definition/store/P4A3AApiGateTest.java
M src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2AApiGateTest.java
M src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2PhaseTypes.java
M src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2ApiGateTest.java
M src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1AApiGateTest.java
A src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java
A src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1CoreTest.java
M src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1IntegratedSnapshotTraversalTest.java
M src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataSourceSelectorTest.java
M src/test/java/com/yo1no/gramarye/magic/definition/store/P4EPhaseTypes.java
```

The closed inventory contains exactly `PLAYER_SKILL_ATTACHMENT` and `PENDING_ATTACHMENT_JOURNAL`.
Arbitration is UUID-natural `online > integrated > disk`, with every player claim ordered before the
journal claims. Each source reserves its complete declared claim count before drain; publication
uses one progressive segmented raw-claim backing and remains package-private, single-use, and
unpublished. The implementation has zero player／Store／journal mutation, zero dirty delta, zero
reclaim calls, and no event, network, filesystem-write, or new persistent-truth side effect. B1 has
no grouped Store audit, memory-only index, public `Complete`, `SkillRetentionRootSnapshot`, or
reclaim composition; those later responsibilities were not implemented.

Local verification passed with 183 test classes and 1,316 tests, all with zero failures, errors, or
skips; the focused API/core results were `6/6` and `8/8`. Normal GameTest passed `12/12`, and the
dedicated-server smoke passed through Ready startup, clean SavedData absence, and normal shutdown.
Warning-mode production compilation, production JAR isolation, 38-class `javap -p -s -v`, and all 11
portable configuration verifiers in normal, minimal-PATH, and `bash -n` modes passed. The unchanged
fixed-heap gates passed: P4-A3's three fixed-1-GiB workloads; P4-B's valid, hostile, malformed,
trailing, second-member, restart, and packaged-runtime matrix; P4-C's six lifecycle／restart cases;
and P4-D's D–J1 crash matrix plus combined first／restart. The official R2Q evidence was not rerun or
modified.

Remote workflow run `31320097058` (attempt 1) completed successfully for the exact implementation
SHA. Its five jobs—`build`, `P4-A3 memory gates`, `P4-B memory gates`, `P4-C memory gates`, and
`P4-D memory gates`—all passed. Branch-protection required-check configuration remains external
governance unknown.

## P4-E1-B2 read-only design review closure ledger

The P4-E1-B2 read-only design review is complete and hit no Stop Condition. It forcibly splits the
remaining implementation into B2-A followed by B2-B. At that historical precommit coordinate, the
review closure was `IMPLEMENTED LOCALLY; COMMIT / PUSH / REMOTE PENDING`; its subsequent unique
attempt-1 exact-SHA remote Gate passed and qualified B2-A to start.

The split is a semantic closure boundary rather than a file-count split. Grouped Store audit and the
D1-sensitive Store seam form an independently reviewable surface. B2-A can produce a
package-private, single-use, same-tick, unpublished `AuditedCapture`; B2-B can consume that exact
value without rescanning playerdata, rerunning P4-C admission, reprojecting roots, or rerunning the
grouped Store audit. B2-A needs no temporary public raw API, creates no persistent intermediate
truth, and creates no index, `Complete`, snapshot, or reclaim call.

B2-A's fixed call shape is:

```text
B1 Captured
-> exact B2-A owner/server/thread/tick consume
-> raw first pass builds distinct SkillId insertion order
-> exactly one opaque Store-history lookup per distinct SkillId
-> raw second pass performs domain terminal mapping in original order
-> preserve D1 journal-audit failure order, lookup timing, privacy, and proof identity
-> package-private, single-use, same-tick, unpublished AuditedCapture
```

B2-A owns the B1 single-use transfer; exact owner／server／thread／tick binding; an opaque exact-history
observation primitive; the bounded distinct-ID table; player owner and revision checks; journal exact
target checks; raw-order first-terminal mapping; internal reconciliation／incomplete facts; history
observation cleanup; and the unpublished `AuditedCapture`. It does not own a public result,
`SkillRetentionRootAuditService`, final freshness, the memory-only index, public `Complete`, an E3
handoff, `SkillRetentionRootSnapshot`, reclaim, reconciliation mutation, or event wiring.

B2-B's fixed call shape is:

```text
AuditedCapture
-> final freshness
-> transfer the same segmented backing to memory-only index ownership
-> bounded diagnostics result
-> nonforgeable same-tick Complete permit
-> package-private single-iterator E3 handoff
```

B2-B owns final Store／journal／online／directory／selected-file／integrated／inventory freshness;
memory-only index publication, generation, and invalidation; a bounded public diagnostics facade;
the single-use `Complete` permit; a package-private E3 handoff; and package-private server-cleanup and
future E2-invalidation seams. It still does not call the snapshot factory or reclaim, perform E2
mutation, install E3 event／composition wiring, add a fixed-heap task, or modify Gradle／CI.

The grouped audit decision is fixed for B2-A. Its first raw traversal validates metadata and builds a
bounded `LinkedHashMap` in first-occurrence order. The lookup phase observes every distinct
`SkillId` exactly once through an opaque Store-history primitive. A second raw traversal, never map
iteration, decides the domain terminal. Player claims classify history absence as missing, owner
mismatch as owner mismatch, exact-revision absence as missing, and otherwise as valid. Journal owner
correctness comes from the fresh D1 proof; missing journal history or revision is journal-target
invalid. Valid nonlatest revisions remain valid. The seam never queries Store latest or exposes the
actual foreign owner, history, document, Store snapshot, or carrier. History observations are
cleared after success or failure; unexpected `RuntimeException` becomes a bounded internal failure,
while `Error`／`OutOfMemoryError` propagates after cleanup.

D1 and E1 share only that low-level opaque exact-history observation primitive. They do not share a
high-level coordinator that would force D1 to preload every distinct ID. D1's raw journal order,
early terminal, owner-before-revision precedence, machine codes, entry-index／route metadata, proof
identity, actual-owner privacy, and lookup count must remain unchanged.

The successful `AuditedCapture` may retain the original B1 segmented root backing, source and B1
summary tables, witness bundle, exact Store Ready witness, journal proof／lifecycle witness,
distinct-ID count, and owner／server／thread／tick binding. It may not retain Store-history
observations, the actual foreign owner, a public root collection, a second flattened root list,
persistent truth, index, `Complete` token, or P3-D snapshot.

All player claims precede all journal claims. If a player stale claim and journal invalid target
coexist, the first raw-order player stale claim wins. Domain classification stops at that claim;
diagnostics record only `staleObservedAtLeast = 1`, and a later journal failure cannot overwrite the
reconciliation result. Hash-map iteration never decides terminal order.

The prior statement that the index retains no raw claims means it must not retain a B1 capture,
source witnesses, or unaudited raw-claim authority. After B2-A Store audit and B2-B final freshness
both pass, the same segmented `SkillReference` backing may change ownership and become the
index-owned audited-reference backing. This creates exactly zero second full root vectors. The index
remains rebuildable evidence rather than truth and never independently authorizes reclaim.

B2-A and B2-B preserve this zero-side-effect matrix:

```text
Store mutation calls        = 0
Store reclaim calls         = 0
Store dirty delta           = 0
Attachment setData          = 0
journal mutation            = 0
playerdata/filesystem write = 0
DFU calls                   = 0
event registration          = 0
network                     = 0
chunk load/force            = 0
background work             = 0
```

The exact P4-E3 fixed `-Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError` production-shaped
first／restart Gate remains mandatory; R2Q evidence cannot replace it. Branch-protection
required-check configuration remains external governance unknown.

## P4-E1-B2-A implementation and remote closure ledger

P4-E1-B2-A is implemented, committed, pushed, and remote-validated. The implementation commit is
`407471bfc01c3a3bb46d94d412be9bc86d80fa24`, its tree is
`973b36ee0e6adc092ad1b1354b52bdabd11c1be9`, and its parent is
`b910320271a09f5cbe24a5a78ed81a71ea27192f`. Its exact stat is 32 paths, 3,142 insertions, and 145
deletions. The 30 non-ledger paths comprise three new package-private production
owners (`P4E1StoreHistoryObservation`, `P4E1GroupedStoreAudit`, and `P4E1AuditedCapture`), six
narrow production modifications in the existing B1／D1 Store package and normal GameTest holder,
three new focused tests, ten exact existing API／phase-gate updates, and eight portable-verifier
updates; the remaining two paths are this ledger and the P4-E0 root-audit ledger. There is no
Gradle, workflow, resource, codex-spec, research-source, or README delta.

The Store owns one package-private `observeExactHistoryForRootAudit(SkillId)` primitive. Each call
performs exactly one `histories.get`, including absent routes, and returns an opaque clearable
observation with only owner-match and exact-reference containment operations. D1 delegates only to
that low-level primitive and retains its raw-order early terminal, owner-before-revision precedence,
machine codes, entry metadata, proof identity, actual-owner privacy, and lookup timing. Its
high-level coordinator was not shared or rewritten.

The B2-A coordinator consumes a B1 capture with exact nominal owner, server, creation-thread, logic-
thread, and captured-tick binding. Its first raw traversal validates claim metadata and establishes
first-occurrence `SkillId` order; it then observes every distinct route exactly once. Its second raw
traversal alone determines the terminal result. Player missing／owner mismatch wins before any later
journal invalidity; journal ownership remains backed by the exact current D1 proof; valid nonlatest
revisions remain valid; and reconciliation diagnostics intentionally publish only
`staleObservedAtLeast = 1`. The actual Store owner never leaves the opaque observation.

Every opaque history observation is cleared on success, domain failure, bounded
`RuntimeException`, `Error`, and `OutOfMemoryError`. Runtime failures become bounded internal facts;
`Error`／OOME are not caught or reclassified. Cleanup uses index／linked-slot walks, and the result-
publication fallback now clears a just-created `AuditedCapture` directly without allocating a
`Transfer`, preserving the original Error identity. Success transfers the original segmented B1
backing into a package-private, same-tick, single-use, unpublished `AuditedCapture`; the second full
root vector count is zero. No Store-history observation survives that transfer.

Local verification passed across 186 suites and 1,342 unit tests, with zero failures, errors, or
skips. Normal GameTest remained exactly `12/12`; the dedicated-server smoke passed. Warning-mode
production compilation,
`jar`, `tasks --all`, `javap`, JAR/source-set isolation, focused B2-A／D1 tests, all eleven portable
verifiers under `bash -n` and minimal PATH, and the unchanged configuration Gates passed. The full
fixed-heap Gates also passed unchanged: P4-A3's three fixed-1-GiB workloads; P4-B's packaged,
valid／hostile／malformed／trailing／second-member paired-restart matrix; P4-C's six
Attachment-lifecycle first／restart cases; and P4-D's 16-JVM D–J1 plus combined first／restart Gate.
The generated root Log4j rollover files were inventoried as run-owned, non-authoritative output and
precisely removed; the rollover warning was not a Gate failure. Official R2Q remains the exact same
six-file set and all controlled checksums pass; no research study or smoke was rerun.

B2-A performs zero Store mutation, dirty publication, pin, snapshot, reclaim, Attachment `setData`,
journal mutation, playerdata／filesystem write, DFU, event registration, network, chunk force, or
background work. It creates no public audit service／result, index, generation, `Complete`, E3
handoff, `SkillRetentionRootSnapshot`, or reconciliation mutation. Complete final freshness and
index publication remain exclusively B2-B work.

The bounded evidence limitations remain explicit: the wrong-server rejection branch is locked by
static/API evidence while the real GameTest exercises wrong owner, thread, and tick; outer
coordinator runtime/result-wrapper allocation failures are covered by the pure core and static
cleanup Gate rather than a separate injected server case; the zero-player lifecycle fixture makes
Attachment non-mutation vacuous and is paired with the exact no-`setData` call-site Gate; and the
nonempty Store side-effect evidence is provided by the pure/D1 tests rather than the empty full-
coordinator fixture. Nested implementation classfiles may carry JLS-mandated public member flags,
but their enclosing top-level owners and every operational entry remain package-private and are not
externally nameable.

The exact implementation-SHA push produced one canonical `Build` run, `31415157794`, at attempt 1;
it completed successfully with the exact five-job set: `build` (`93542316416`), `P4-A3 memory
gates` (`93543416201`), `P4-B memory gates` (`93544003612`), `P4-C memory gates`
(`93545441197`), and `P4-D memory gates` (`93546527193`). Every job was bound to the exact
implementation SHA and completed with `success`. The official R2Q root remains the exact six-file
set, its manifest still passes, and `SHA256SUMS.txt` remains
`cb296db6f2aae653a0db2af25b20df4a5107e90096eff9766e40fa2798f24da9`; no research study or smoke
was rerun. Branch-protection required-check configuration remains external governance unknown.

## P4-E0-B.4 memory-only index-generation authority closure ledger

The subsequent P4-E1-B2-B read-only design review stopped at the exact
`INDEX GENERATION / EXHAUSTION AUTHORITY GAP`; it did not approve an implementation. P4-E0-B.4
closes that authority gap in the scoped codex-spec documents and records the resulting lifecycle
here. The documentation-only authority patch was committed as
`b294791409bd34289b7c079a504ccd538c1c78bc` (`docs(persistence): define P4-E index generation
authority`), with tree `4ccb6a093497799633ff548d376888555889f404`, parent
`8a2d5033af1448cc037fb01191c985aa6e86d937`, and exact stat of six files changed, 561 insertions,
and 32 deletions. Its unique exact-SHA remote Gate passed. At that B.4 closure point, the B2-B
read-only design review was reopened from the clean closure HEAD; B2-B implementation had not
started.

Local `verifyPlatformBaseline`, `compileJava`, and `test` all passed. The JUnit XML total was 186
suites, 1,342 tests, zero failures, zero errors, and zero skipped tests. The authority commit changed
only the exact six approved Markdown files; Java, tests, scripts, Gradle, workflow, resources,
architecture README, and official evidence remained unchanged. Its unique canonical `Build` run
was `31468874016`, attempt 1. The exact five successful jobs, all bound to the authority SHA and
that run／attempt, were `build` (`93707549612`), `P4-A3 memory gates` (`93708054139`), `P4-B memory
gates` (`93708447417`), `P4-C memory gates` (`93709597170`), and `P4-D memory gates`
(`93710471670`). A post-completion exact-SHA query still returned one run and exactly five jobs.
The official R2Q root remained the exact six-file set, its manifest passed, and `SHA256SUMS.txt`
remained `cb296db6f2aae653a0db2af25b20df4a5107e90096eff9766e40fa2798f24da9`; no study or smoke
was rerun.

The sole generation owner is one `SkillRetentionRootAuditService` identity crossed with one exact
`MinecraftServer` object identity. Each service owns independent per-server slots; no static,
cross-server, per-`Complete`, tick, P4-C, Store, journal, or SavedData coordinate may substitute.
Generation is memory-only `long` in `0..Long.MAX_VALUE`, is never serialized, and has an internal
baseline of `0` for a newly created slot. No entry means `Incomplete` authority with no published
generation; the first accepted audit reservation uses generation `1`.

Exactly two operations consume generation: one accepted global-audit attempt reserves exactly one
next generation, regardless of its eventual terminal, and one accepted P4-E2 explicit index
invalidation reserves exactly one next generation for the entire reconciliation operation. Audit
reservation occurs after null／programming, exact-server, logic-thread, active-lease, and reentrancy
checks, but before any of the existing 18-step source work. A successful reservation immediately
invalidates the old `Complete` authority and enters `AuditInProgress(reserved)`; success publishes
`CompleteIndex(reserved)`, while `Incomplete`, `OverLimit`, `ReconciliationRequired`, final-
freshness failure, or bounded operational `RuntimeException` publishes
`IncompleteIndex(reserved)`. `Error`／OOME propagates unchanged and leaves a non-Complete state at
the already-reserved generation. Success, failure, freshness, and result publication never consume
a second generation, and an old `Complete` is never restored after reservation.

P4-E2 may act only through its future reviewed package-private invalidation seam. With no active
lease, one accepted invalidation advances once and publishes `Incomplete`; it neither re-audits nor
projects roots, creates a snapshot, reclaims, or mutates Store／journal data. A reconciliation batch
does not advance once per pointer. Programming or wrong-thread rejection, reentrant audit, active-
lease rejection, permit misuse, lease open／close, result construction, diagnostics, and
`removeServer` consume no generation.

`Long.MAX_VALUE - 1 -> Long.MAX_VALUE` is legal. When an audit or E2 invalidation needs to advance
from `Long.MAX_VALUE`, it does not wrap, retry, preserve old `Complete`, or begin source work. It
clears old permit／backing authority and installs the terminal
`GenerationExhausted(Long.MAX_VALUE)`, reported as bounded
`Incomplete(GENERATION_EXHAUSTED)`. Repeated audit／invalidation is idempotent; source capture,
Store audit, snapshot, and reclaim counts remain zero. Exhaustion affects only that exact server
slot, and only exact-server `removeServer` after server stop can delete the slot.

Every `Complete` permit is single-use: each consume attempt first marks it used. Wrong service,
server, thread, tick, state identity, or generation rejects fail-fast after clearing the permit's
own authority references, but neither increments generation nor modifies the current index. A
successful consume changes `CompleteIndex(g)` to `CompleteIndexWithActiveLease(g)` without changing
generation or backing identity. The lease is bound to exact service, server, thread, tick, active-
lease state identity, generation, and lease identity. Close returns to `CompleteIndex(g)` without
republishing a permit. While active, audit and E2 invalidation fail-fast with no state delta; server
stop is the sole forced-cleanup exception and invalidates the lease, clears backing／cursor
authority, and removes the exact slot without advancing generation.

The required internal state machine includes:

```text
NoEntry -> baseline 0 -> reserve 1 -> AuditInProgress(1)
Incomplete(g) / CompleteIndex(g) -> reserve g+1 -> AuditInProgress(g+1)
AuditInProgress(g) -> success -> CompleteIndex(g)
AuditInProgress(g) -> normal terminal -> Incomplete(g)
AuditInProgress(g) -> Error/OOME -> non-Complete(g), then propagate
CompleteIndex(g) -> permit consume -> CompleteIndexWithActiveLease(g)
CompleteIndexWithActiveLease(g) -> close -> CompleteIndex(g)
non-lease state(g < MAX) -> E2 invalidate -> Incomplete(g+1)
state(g = MAX) requiring advance -> GenerationExhausted(MAX)
GenerationExhausted(MAX) -> audit/invalidate -> GenerationExhausted(MAX)
any state -> exact stopped-server removeServer -> Removed / no map entry
```

Reentrant audit preserves the original `AuditInProgress` state and generation. A different new
server object begins from its own baseline `0`; the removed server object cannot be reinserted to
reset exhaustion. `Complete` and handoff currentness require both exact state identity and exact
generation—generation equality alone is insufficient—and all existing Store／journal／source
witness checks remain required. First-failure precedence is programming／server／thread, then active
lease／reentrancy, generation reservation, exhaustion, and only then checkpoints 2–18 of the existing
audit order, beginning with the heap-floor Gate. This lifecycle does not add a 26th budget counter, change the 25-counter profile or heap
floor, alter R2Q identity, or waive the mandatory production-shaped P4-E3 Gate.

## P4-E1-B2-B read-only design review closure ledger

The renewed P4-E1-B2-B read-only design review is `PASS`; no Stop Condition was hit and the fixed
split decision is `NO FURTHER SPLIT`. The clean review base was `main` commit
`3854e06f29e5a3187f25975546095f6956f4e335`, tree
`f1952b17217d7bec2ffc3cfa83f6fc06aa778852`, with `HEAD == origin/main`, zero ahead／behind, and a
clean worktree／index. The locked NeoForge 21.1.241 source JAR remained a regular non-symlink with
SHA-256 `0e1dcae8e21cd8d8c656e7fe76efe1e31260cf0f956f07aa749b86992cf4fe23`. The official R2Q root
remained the exact six-file set and its manifest passed; `SHA256SUMS.txt` remained
`cb296db6f2aae653a0db2af25b20df4a5107e90096eff9766e40fa2798f24da9`. No R1／R2／R2Q study or
smoke was rerun. This section records a design decision only: no B2-B production type, test, index,
result, permit, handoff, mutation, snapshot, or reclaim implementation existed at the review base.

The only approved B2-B implementation chain is:

```text
P4E1AuditedCapture
-> final freshness
-> same segmented backing memory-only index publication
-> bounded result
-> same-tick nonforgeable Complete permit
-> package-private single-use E3 handoff
```

The sole future coordinator is package-private final `SkillRetentionRootAuditService`, with a
package-private constructor. Each service instance owns one
`IdentityHashMap<MinecraftServer, IndexSlot>` keyed by exact server-object identity; there is no
static, persistent, cross-server, or background index. B2-B creates only the reviewed types and their
tests, not a production lifecycle instance or a second start／stop listener. Future E3 makes the
existing `SkillDefinitionStoreService` lifecycle owner construct and invoke the audit service in its
existing startup／shutdown composition. Future E2 may use only the reviewed package-private
invalidation seam, and future E3 removes only the exact stopped server. The index never retains a B1
capture, `P4E1AuditedCapture` handle, public `Complete` permit, `Tag`／`CompoundTag`, `Path`／filename,
`ServerPlayer`, Attachment state, Store history／actual owner, journal entry／proof, carrier／SavedData,
or P3-D snapshot.

The one physical reference backing has this lifecycle:

```text
UNPUBLISHED_RAW -> B2-A AUDITED -> B2-B AUDITED_INDEX -> DISCARDED
```

The exact same `P4E1RawClaimBuffer` transfers ownership. Raw order, duplicates, source-table index,
source-local ordinal, claim kind, and equipped slot remain unchanged. B2-B may construct only a
bounded witness-free source metadata table of at most 2,049 entries; it may not construct a second
full `SkillReference` vector. Runtime witnesses and the original witness-bearing source table are
cleared before publication. The only later root-vector copy is the P3-D snapshot materialization
owned by E3.

The B.4 generation authority remains exact. Physical no-entry means externally Incomplete with an
internal baseline `0`; the first accepted audit reserves `1`. An accepted audit from
`Incomplete(g)` or `CompleteIndex(g)` revokes the old authority and reserves exactly `r = g + 1` as
`AuditInProgress(r)`. Success publishes `CompleteIndex(r)`; every normal terminal publishes
`Incomplete(r)`. `Error`／OOME propagates unchanged after the already-reserved generation is left
non-Complete. Valid permit consumption enters `CompleteIndexWithActiveLease(g)`; valid close returns
to `CompleteIndex(g)` without a new permit or generation. One accepted E2 invalidation from any
non-lease state with `g < Long.MAX_VALUE` reserves once and publishes `Incomplete(g + 1)` without
audit, projection, snapshot, reclaim, or Store／journal mutation; `Long.MAX_VALUE - 1 ->
Long.MAX_VALUE` is the last legal reservation. Advancing from `Long.MAX_VALUE` clears the old
permit／backing authority, installs `GenerationExhausted(Long.MAX_VALUE)`, returns bounded
`Incomplete(GENERATION_EXHAUSTED)`, and performs zero source work. Repeated audit／invalidation in
that terminal is idempotent. Null／programming, wrong-server／
wrong-thread, removed-server, reentrant-audit, and active-lease rejection occur before reservation.
Permit misuse, lease open／close, and exact-server removal consume no generation. Every authority
check binds both exact state identity and generation; tick, P4-C generation, Store, journal, and
SavedData are not generation substitutes.

Only a B2-A `Audited` candidate enters final freshness. Its deterministic order is:

1. exact audit-service／server／logic-thread／captured-tick and exact `PlayerList` identity;
2. exact reserved `AuditInProgress` state identity and generation;
3. Store service／adapter／Ready／Store／inner-carrier／Store-carrier／pending identity;
4. journal lifecycle／Ready／source-pending／inner-pending identity;
5. D1 proof identity, satisfaction, and exact-journal binding;
6. exact V0 inventory coverage and Attachment／journal provider identities;
7. one complete directory entry-set and metadata verification, including ignored entries;
8. selected primary／old kind, classification, fileKey, size, and mtime;
9. complete online UUID set plus exact player／server／presence／Attachment-state identity; and
10. integrated profile／snapshot four-state plus metadata-only `online > integrated > disk` winner
    arbitration.

The bounded final-freshness codes are exactly:

```text
SERVER_FRESHNESS_LOST
CALL_CHAIN_FRESHNESS_LOST
INDEX_RESERVATION_LOST
STORE_SOURCE_FRESHNESS_LOST
JOURNAL_FRESHNESS_LOST
JOURNAL_TARGET_PROOF_LOST
INVENTORY_PROVIDER_FRESHNESS_LOST
DIRECTORY_RACE_DETECTED
SELECTED_FILE_FRESHNESS_LOST
ONLINE_SOURCE_FRESHNESS_LOST
INTEGRATED_OWNER_FRESHNESS_LOST
```

That phase performs no selected-file content reopen, gzip／NBT read, P4-C admission, root
reprojection, integrated-tree retraversal, journal redrain, Store-history re-audit, retry, second
traversal, whole-tree copy, checksum, or lock. It cannot run on or overwrite any earlier B1／B2-A
terminal result.

The future public diagnostics boundary is one sealed `SkillRetentionRootAuditResult` with exactly
`Complete`, `Incomplete`, `OverLimit`, and `ReconciliationRequired` variants. `Complete` is a public
final non-record with a private constructor and package-private issuance; its only public accessor is
`summary()`. It has zero root accessors, excludes its permit from equality／hash／bounded `toString`,
and has no Codec. Public `AuditSummary` uses `OptionalLong`／`OptionalInt` rather than fabricated zero
for exactly `indexGeneration`, `selectedOwnerCount`, `onlineOwnerCount`, `integratedOwnerCount`,
`diskOwnerCount`, `playerRootClaimCount`, `journalRootClaimCount`, `totalRawRootClaimCount`,
`distinctSkillIdCount`, `auditedValidClaimCount`, and `sourceCount`. Operational failure retains
only the already established bounded facts and an exception class name under the existing 160-character
ceiling—never a message, stack, or `Throwable`.

The nonforgeable permit binds exact service, server, thread, tick, `CompleteIndex` state identity,
generation, freshness seal, and unused state. Every consume attempt first marks it used and clears
the public `Complete` authority reference, then validates service, server, thread, tick, state,
generation, and absence of a lease. Wrong-service, wrong-server, wrong-thread, cross-tick, stale-state,
stale-generation, and second-use rejection fail fast, cannot retry, and leave the current index,
backing, and generation—including any newer replacement—unchanged.

The E3 bridge is package-private final `P4E1CompleteRootHandoff implements Iterable<SkillReference>,
AutoCloseable`, with a nonpublic constructor. Only the audit service creates it. It binds exact
service／server／thread／tick／active-state／generation／lease identity, permits exactly one iterator,
preserves raw order and duplicates, throws `NoSuchElementException` after exhaustion, supports
partial iteration, and has idempotent close. Close does not increment generation, reissue a permit,
or clear the indexed backing. An active or leaked lease blocks audit and E2; exact server stop is the
only forced cleanup. There is no Cleaner, finalizer, timeout, or background recovery, and a cross-
tick operation fails closed; a wrong-tick close clears only local cursor authority and cannot mutate
the lease, which remains blocked until exact-server stop. Future E3 alone performs
`Complete -> consume -> try-with-resources handoff -> SkillRetentionRootSnapshot.fromCompleteRoots
-> require Snapshot.Complete -> controlled reclaim exactly once -> close`. B2-B calls the snapshot
factory and reclaim zero times.

The existing public `SkillRetentionRootSnapshot.fromCompleteRoots(Iterable)` is a caller claim, not
an authorization seam. It requests exactly one iterator, consumes it synchronously in order,
preserves duplicates, observes at most the root maximum plus one, and does not retain the iterable.
Only the E1 permit plus package-private handoff authorizes that future E3 call. E3's fixed-heap Gate
must cover the simultaneous E1 indexed backing and P3-D snapshot materialization／copy.

The exact future type plan is:

| Type | Visibility／shape | Responsibility |
| --- | --- | --- |
| `SkillRetentionRootAuditService` | package-private final, package-private constructor | fixed dependencies, per-server index, audit／consume／invalidate／remove |
| `SkillRetentionRootAuditResult` | public sealed diagnostics result | bounded terminal diagnostics; `Complete` hides the permit |
| `AuditSummary` | public nested record | bounded optional counters／generation only |
| `P4E1FinalFreshness` | package-private final coordinator | deterministic ordered witness currentness |
| `P4E1CompleteRootHandoff` | package-private final, single-use `Iterable`／`AutoCloseable` | one-iterator E3 active lease |
| `IndexSlot`／`IndexState`／`IndexedBacking`／`PermitCell`／`LeaseCell`／`IndexedSource` | private nested implementation types | generation, same backing, compact metadata, permit／lease revocation |

No generic audit framework, `AuditUtils`, persistent index, public root query, or second backing is
approved. Future focused tests include at least `P4E1B2BFinalFreshnessTest`,
`P4E1B2BIndexLifecycleTest`, `P4E1B2BCompleteHandoffTest`, `P4E1B2BApiGateTest`, and final
`P4E1BApiGateTest`, with exact-path／type allowlists; blanket `P4E1*`, `Audit*`, and `B2*`
exemptions are forbidden. They must
cover all freshness codes and precedence; NoEntry／baseline／first reservation／ordinary audit and E2
invalidation; two-server isolation; exact-server `removeServer`; `Long.MAX_VALUE - 1 ->
Long.MAX_VALUE`; idempotent exhaustion with source-work count zero; every normal terminal;
`Error`／OOME identity with same-generation non-Complete fallback; permit misuse; lease leak and
forced stop cleanup; same-backing／zero-second-vector; one iterator／order／duplicates／partial close;
bounded public API with no root or internal exposure; and all zero-side-effect call counts. B2-B adds
no normal GameTest, source set, Gradle task, workflow job, or P4-E fixed-heap Gate; the production-
shaped fixed-1,536-MiB first／restart Gate remains E3-owned.

The B2-B implementation must preserve:

```text
Store mutation calls        = 0
Store reclaim calls         = 0
Store dirty delta           = 0
Attachment setData          = 0
journal mutation            = 0
playerdata/filesystem write = 0
DFU calls                   = 0
event registration          = 0
network                     = 0
chunk load/force            = 0
background work             = 0
snapshot factory calls      = 0
```

B2-B does not begin P4-E2 or P4-E3.

Known limits remain explicit: hostile same-object in-place mutation of the integrated
`CompoundTag` is outside the identity witness; hostile disk rewrite preserving fileKey／size／mtime
is outside V0; an unused `Complete` may remain reachable until GC but loses authority after tick／
state change; a leaked handoff blocks audit／E2 until server stop; R2Q is neither a universal theorem
nor the complete E3 production envelope. The exact
`-Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError` E3 first／restart Gate remains mandatory, and
branch-protection required-check configuration remains external governance unknown.

The documentation-only closure worktree passed `verifyPlatformBaseline`, `compileJava`, and the
full unit suite. JUnit XML recorded 186 suites and 1,342 tests with zero failures, errors, or skips.
No GameTest, dedicated-server smoke, fixed-heap Gate, or research study／smoke was run. The phase
values below become effective only after this closure commit is pushed and its unique attempt-1
exact-SHA `Build` run completes with exactly `build` and the P4-A3／P4-B／P4-C／P4-D memory jobs
successful. Until that Gate passes, B2-B implementation remains blocked; the commit records no
implementation evidence.

```text
P4-C0.1 = COMPLETE
P4-C1   = COMPLETE
P4-C2-A = COMPLETE
P4-C2-B = COMPLETE
P4-C    = COMPLETE
P4-D0               = COMPLETE
P4-D1               = COMPLETE
P4-D2 design review = COMPLETE
P4-D2-A             = COMPLETE
P4-D2-B             = COMPLETE
P4-D2               = COMPLETE
P4-D3 design review = COMPLETE
P4-D3-A             = COMPLETE
P4-D3-B             = COMPLETE
P4-D3               = COMPLETE
P4-D                = COMPLETE

P4-E0-A research/adjudication = COMPLETE
P4-E0-R1/R2/R2R/R2Q           = COMPLETE
P4-E0-B authority patch        = COMPLETE
P4-E0-B.1 authority patch      = COMPLETE
P4-E0-B.2 authority patch      = COMPLETE
P4-E0-B.3                      = COMPLETE
P4-E0-B.3 authority commit     = e23a2a6c0df298315fc726ec509d3f953d559a08
P4-E0-B.3 authority tree       = ce1d6d379c763ed2824f831f6a1e81c73c3fec65
P4-E0-B.3 authority remote run = 31251807408 (attempt 1)
P4-E0-B.3 authority remote jobs = build + P4-A3/B/C/D memory gates PASS
P4-E0-B.4 authority commit          = b294791409bd34289b7c079a504ccd538c1c78bc
P4-E0-B.4 authority tree            = 4ccb6a093497799633ff548d376888555889f404
P4-E0-B.4 authority stat            = 6 files; 561 insertions; 32 deletions
P4-E0-B.4 authority remote run      = 31468874016 (attempt 1)
P4-E0-B.4 authority remote jobs     = build + P4-A3/B/C/D memory gates PASS
P4-E0-B.4 index generation/exhaustion authority = COMPLETE
P4-E0-B.4                     = COMPLETE
P4-E0                          = COMPLETE
P4-E1 prior read-only review   = STOPPED AT INTEGRATED SNAPSHOT AUTHORITY GATE
P4-E1-A enabling read-only review = PASS (HISTORICAL)
P4-E1-A previous implementation attempt = STOPPED AT ACTIVE HEAP-FLOOR AUTHORITY COORDINATE CONFLICT
P4-E1-A                        = COMPLETE
P4-E1-B prior read-only review = STOPPED AT ONLINE SOURCE COUNTER APPLICABILITY AUTHORITY GAP
P4-E1-B renewed read-only review = STOPPED AT TAG-FREE P4-C ADMISSION BRIDGE GATE
P4-E1-A.1 tag-free bridge review = PASS
P4-E1-A.1 implementation commit = 5fea6f36aff2512ed7e232e45d9bfbd3cc0ad2ef
P4-E1-A.1 implementation tree   = 9e5f87e1063b4f4a42ef4c4b6bf7bbc2b7a85cdc
P4-E1-A.1 implementation stat   = 20 files; 1,995 insertions; 84 deletions
P4-E1-A.1 implementation remote run = 31291725341 (attempt 1)
P4-E1-A.1 implementation remote jobs = build + P4-A3/B/C/D memory gates PASS
P4-E1-A.1                      = COMPLETE
P4-E1-B read-only design review  = PASS
P4-E1-B1                         = COMPLETE
P4-E1-B2 read-only design review = COMPLETE — FORCED B2-A / B2-B SPLIT
P4-E1-B2-A implementation commit = 407471bfc01c3a3bb46d94d412be9bc86d80fa24
P4-E1-B2-A implementation tree   = 973b36ee0e6adc092ad1b1354b52bdabd11c1be9
P4-E1-B2-A implementation parent = b910320271a09f5cbe24a5a78ed81a71ea27192f
P4-E1-B2-A implementation stat   = 32 files; 3,142 insertions; 145 deletions
P4-E1-B2-A implementation remote run = 31415157794 (attempt 1)
P4-E1-B2-A implementation remote jobs = build + P4-A3/B/C/D memory gates PASS
P4-E1-B2-A                       = COMPLETE
P4-E1-B2-B prior read-only design review = STOPPED AT INDEX GENERATION / EXHAUSTION AUTHORITY GAP
P4-E1-B2-B read-only design review = COMPLETE
P4-E1-B2-B split                = NO FURTHER SPLIT
P4-E1-B2-B implementation       = READY; NOT STARTED
P4-E1-B2                         = INCOMPLETE
P4-E1-B                          = INCOMPLETE
P4-E2 / P4-E3                   = BLOCKED
P4-E                            = INCOMPLETE
```

The required remote `P4-C memory gates` job passed. P4-D0 authority is indexed by
[P4-D0 submission journal and composition boundary](P4-D0-submission-journal-boundary.md). P4-D1 is
complete: its production commit is present at `HEAD`／`origin/main`, local full regression passed,
and the externally reported remote build／A3／B／C jobs passed. The P4-D2 design review is complete;
D2-A is complete with its production commit present at `HEAD`／`origin/main`, local regression and
existing memory Gates passed, and the externally reported remote build／A3／B／C jobs passed. D2-B is
complete: its production commit is present at `HEAD`／`origin/main`; its authenticated facade,
composition-root wiring, two normal submission GameTests, and portable local configuration gate
passed local regression and the existing fixed-heap Gates; and the externally reported remote
build／A3／B／C jobs passed. P4-D2 is complete. The P4-D3 design review is complete; D3-A's production
commit is present at `HEAD`／`origin/main`, its local regression and existing fixed-heap Gates
passed, and the externally reported remote build／A3／B／C jobs passed. D3-A is complete. D3-B is
complete with the crash D–J matrix, combined first／restart fixed-heap Gate, Gradle task graph,
portable configuration verifier, and P4-D memory CI job committed at `HEAD`／`origin/main`; the
externally reported remote build／A3／B／C／D jobs passed. P4-D is complete. P4-E was ready for
read-only design review only as a historical P4-D closure statement; the E0 research／adjudication
lineage and E0-B authority closure are now complete. The first P4-E1 read-only design review stopped
at the integrated-snapshot counting／freshness authority Gate; B.1 resolved it and the renewed review
passed. The subsequent E1-A attempt stopped at the active heap-floor coordinate conflict. B.2 is
complete; the controlled restore, fresh re-preflight, E1-A implementation, commit／push, and remote
closure now pass. P4-E1-A is complete. E1-B's prior read-only review stopped at the online counter
applicability gap; B.3 authority and exact-SHA remote closure passed. The renewed E1-B review then
stopped at the tag-free P4-C admission bridge Gate. Its A.1 review passed, and A.1 was implemented,
committed, pushed, and qualified by its exact-SHA remote Gate. P4-E1-A.1 is complete. The P4-E1-B
read-only design review passed without a Stop Condition. B1 was committed at
`011658f122809af7f9e63f40c980587d3b3d4c76`, pushed, locally verified, and qualified by exact-SHA
remote run `31320097058`; it is complete. The B2 read-only design review is complete and forcibly
split implementation into B2-A followed by B2-B. B2-A implementation commit
`407471bfc01c3a3bb46d94d412be9bc86d80fa24` and its unique attempt-1 exact-SHA remote run
`31415157794` passed the build and P4-A3／B／C／D memory jobs, so B2-A is complete. The first B2-B
read-only design review stopped at the index-generation／exhaustion authority gap. B.4 now defines
that authority; its commit, push, and unique attempt-1 exact-SHA remote Gate have passed. The
renewed B2-B read-only design review passed without a Stop Condition and requires no further split.
Its implementation is ready but has not started. B2 and E1-B remain incomplete; E2 and E3 remain
blocked, and P4-E remains incomplete. The
E0-B／B.1／B.2／B.3
remote jobs did not rerun the R2Q formal
study, and P4-E3 still
requires the production-shaped fixed-1,536-MiB first／restart Gate including the B.3 online
actual-path／domination-proof obligation.
Branch-protection required-check configuration remains external governance unknown.
