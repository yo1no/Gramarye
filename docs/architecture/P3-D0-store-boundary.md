# P3-D0 Store truth and atomic commit boundary

This page indexes the P3-D0 scoped decisions. Architecture authority remains the amended [P3 scoped amendment §§9-B–9-D](../codex-spec/17_P3資料模型修正案.md#9-b-p3-d0-store-truththread-confinement-與-persistence-seam), the [frozen skeleton](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md), the [implementation contract](../codex-spec/Codex_實作總規格Prompt.md), and the [detailed phases](../codex-spec/NeoForge1.21.1_詳細實作步驟.md). It is a boundary index, not a second complete Store specification.

## Phase ownership

- P3-C prepares a transient `SkillSubmissionPlan`; it neither writes Store state nor formally allocates a revision.
- P3-D owns the production pure-Java aggregate, committed owner/history truth, reads, atomic admission／CAS／insert, formal allocation, pin／unpin／reclaim, and a detached persistence snapshot seam. It has no SavedData or Minecraft dependency.
- P4 owns the sole Overworld SavedData persistence adapter, snapshot Codec／NBT, load/save, dirty marking, quarantine, complete offline roots, and Store／Attachment reconciliation. It delegates rather than reimplements P3-D policy.

## Store and mutation boundary

One active `SkillId` maps to one immutable owner binding plus immutable retained `SkillDocument` entries. Latest, owner counts, and allocation counters are not independent truth. The aggregate is server-logic-thread-confined and does not promise arbitrary-thread linearizability or database transactions.

Final quota admission, owner verification, precondition CAS, technical capacity, and revision insertion share one method. Every typed failure occurs before the first truth mutation; success publishes one fully built replacement history. Exact hard ceilings, CAS precedence, capacity scopes, and result variants are canonical in [the scoped amendment §9-C](../codex-spec/17_P3資料模型修正案.md#9-c-p3-d0-quotacapacitycas-與-commit-result).

## Revision lifecycle

P3-D1 adds the one `SkillRevision.successor()` operation shared by proposal and commit arithmetic. Retained history may be sparse, while its maximum revision is an implicit root and cannot be removed by ordinary reclaim. Normal Store commit results have no `RevisionExhausted`; P3-C preparation keeps that outcome for an authoritative latest at MAX.

P3-D remains split into D1 truth/read/snapshot, D2 atomic admission/CAS/insert, and D3 pin/unpin/reclaim. D3 receives a complete bounded external-root snapshot and fails closed rather than sweeping truncated, incomplete, or over-limit roots.

### P3-D1 implementation boundary

- D1 accepts only an empty aggregate or a validated detached snapshot; it exposes exact reads and deterministic snapshot output, but no normal mutation entrypoint.
- Snapshot transport uses ordered immutable lists rather than maps so restore can detect duplicate routed `SkillId` and revision entries before any map construction. Histories are canonically emitted by `SkillId` UUID natural order and revisions by ascending numeric value.
- Restore returns a package-private typed result for bounded Store-domain corruption. Snapshot／restore DTOs stay package-private; the P4 SavedData adapter is expected to live in the same Store package unless a later integration constraint justifies a reviewed bridge.
- Snapshot DTO `toString()` output is bounded metadata only; it must never traverse or expose a `SkillDocument`, DefinitionEnvelope, or raw payload／appearance tree.
- Restore capacity failures and future D2 commit capacity results share the sole `SkillStoreCapacityScope`; restore does not introduce a parallel capacity-scope vocabulary.
- Stored histories and detached snapshots may share the already-immutable `SkillOwnerId` and `SkillDocument` values. They never canonicalize raw payloads or convert DynamicOps families; only their collection graph is rebuilt and sealed.
- D2 must preserve snapshot detachment: later successful Store replacement must not mutate any snapshot produced before that commit.
- `SkillRevision.successor()` computes only a candidate value. P3-C proposal uses it, while formal allocation remains exclusively a successful D2 commit effect.

### P3-D2 implementation boundary

- `SkillQuota` is an immutable policy snapshot obtained by composition after fresh authorization. Neither it nor `SkillSubmissionPlan` is an authorization credential, and Store commit is not an authentication boundary.
- The one normal mutation accepts a plan plus quota snapshot and performs owner verification, quota admission, technical-capacity checks, precondition CAS, and revision insertion before publishing one complete replacement history.
- `ExpectedAbsent` and `ExpectedLatest` use the precedence fixed by the scoped amendment. In particular, an existing-owner mismatch precedes latest comparison and exposes neither the stored owner nor observed latest.
- Every typed rejection precedes the first truth mutation. Success prebuilds the replacement history and `Committed` result, performs one outer-map insert／replace, and returns without a fallible callback or retry.
- A repeated plan follows its original precondition and therefore conflicts; Store does not infer idempotence from document equality and has no retry or attempt cache.
- The normal commit vocabulary has no revision-exhaustion variant. `ExpectedLatest(MAX)` is invalid plan state, while a race that advances Store latest to MAX is a latest mismatch.
- Production construction of commit-result and commit-conflict values is centralized at the Store mutation boundary. Public result records remain data-transfer values rather than capability tokens.
- D1 snapshots remain detached after D2 mutations because existing histories are replaced rather than modified. Immutable document references may be shared without canonicalization or copying.
- `Committed` means the proposed revision was formally allocated and inserted into the in-memory domain aggregate. It does not mean SavedData was encoded or written, `setDirty()` ran, an Attachment changed, or cross-location reconciliation completed.

## Deferred persistence and retirement

Removing a player Attachment reference is not Store retirement and does not release quota. A future retire operation needs a persistent tombstone or equivalent no-reuse truth and a separate scoped amendment. Until then, the default policy quota is Unlimited while all technical ceilings remain mandatory.

Before P4 Java work, P4-0 must fix encoded-byte ceilings and a family-tagged raw storage envelope. Existing Unknown／Unparsed preservation does not promise arbitrary JSON-to-NBT losslessness, so P3-D retains documents without canonicalizing or converting their raw trees.

The completed D0–D3 phase seams, reclaim dirty matrix, and migration-before-restore obligations are
recorded in the [P3-D skill definition Store decision ledger](P3-D-skill-definition-store.md).
