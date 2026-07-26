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
- P4-C owns the permanent player skill Attachment, Draft/reference/editor persistence, total
  serializer, Attachment migration, and clone policy.
- P4-D owns authenticated submission composition, invocation of A3 prospective Store builders,
  prospective journal replacement, commit-oriented persistence preflight, Store commit, carrier／
  journal publication, Attachment transition, typed outcome, and crash recovery.
- P4-E owns complete offline root audit, rebuildable root indexing, reconciliation, and reclaim
  composition. It supplies the complete root snapshot; P4-B2 owns the resulting carrier publication
  and Store SavedData dirty decision.
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
ceilings have no P4-B consumer and do not authorize a raw-copy store. P4-B1 and P4-B2-A are
complete. P4-B2-B, and therefore P4-B, remain incomplete until the required exact-maximum legal
hostile-FNAME fixed-heap pair passes locally and in the remote P4-B memory gates. P4-C remains
unstarted.

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
configuration and aggregate fixed-heap gates. Whether repository branch protection requires that
job is external governance and is not asserted here. Until that remote job passes with the
exact-maximum hostile-FNAME pair, P4-B2-B and P4-B remain incomplete. P4-C, P4-D, and P4-E have not
started.
