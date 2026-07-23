# P3-C skill submission decision ledger

This ledger records the implemented P3-C phase seams. Architecture authority remains the approved [P3 scoped amendment §9-A](../codex-spec/17_P3資料模型修正案.md#9-a-p3-c-submission-preparation-與-p3-d-commit-邊界), the [frozen skeleton](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md), the [implementation contract](../codex-spec/Codex_實作總規格Prompt.md), and the [detailed phases](../codex-spec/NeoForge1.21.1_詳細實作步驟.md). The earlier [P3-C0 boundary](P3-C0-submission-boundary.md) remains in force, and the resolved Store-boundary decisions are indexed by [P3-D0](P3-D0-store-boundary.md). This is a compact decision index, not a second submission specification.

## C0: prepare and commit ownership

- P3-C prepares an immutable, transient plan; it neither mutates Store state nor formally allocates a revision. A prepared plan is not a committed revision.
- P3-D owns a production pure-Java Store aggregate, committed owner/history truth, atomic admission／CAS／insert, formal revision allocation, pin／unpin／reclaim, and the plan commit boundary. Store's greatest retained revision is the allocator truth and implicit root; an Attachment latest pointer is not.
- The later composition facade acquires current authority and state, invokes P3-C prepare, then invokes P3-D commit. Neither domain boundary depends on Minecraft player classes.
- P4 owns the sole Overworld SavedData persistence adapter, snapshot encoding, complete offline roots, dirty marking, and Store／Attachment ordering, recovery, and reconciliation; it does not reimplement P3-D domain policy.

## C1: Draft provenance and formalization

- `SkillSubmissionInput.direct` and `fromReadResult` are the controlled Draft provenance entry points. Read facts become bounded warnings before the Draft schema gate.
- Unsupported Draft schema and incomplete Trigger/Action slots fail without a partial document. An empty but complete Draft reaches B3, which is the sole empty-skill rejection policy.
- Report combination retains source issue order and then inherits `truncated` and `omittedError`; hidden errors may not be lost.

## C2: identity and authority

- `SkillIdSource` is the server-side mint contract. Minting is neither reservation nor authorization, and a transient mint grant is not a restart-stable submission credential.
- `SkillOwnerId` is pure domain identity. Its lack of a Codec or StreamCodec applies through P3-D; P4 persistence is the reviewed point for adding the one canonical UUID Codec.
- Authoritative state is the sealed New/Existing snapshot. Optimistic base-revision conflicts are classified before C3, while identity rejection remains opaque about existence, owner, and latest revision.
- `NOT_AUTHORIZED` and `QUOTA_EXCEEDED` are the bounded preparation-precheck vocabulary. C4 only preserves an existing rejection; it performs no quota lookup or admission decision, while P3-D Store admission remains authoritative at commit.
- An accepted authority snapshot does not remove time-of-check/time-of-use risk and is not a commit credential.

## C3: proposal and validated preparation

- `SubmissionRevisionProposer` is the current proposal policy: New proposes revision zero, Existing proposes the successor, and the maximum revision produces exhaustion. P3-D1 adds the canonical `SkillRevision.successor()` value operation and refactors both this proposer and P3-D2 arithmetic to use it; a proposal or successor remains distinct from formal allocation.
- Formalization creates a current-schema transient `SkillDocument` while preserving Draft identity, ordering, envelopes, and appearance. It does not invoke a writer or re-read the Draft.
- Typed-document resolution, analysis, and projection each run exactly once. The synthetic empty document-read report records that this document was built from a typed Draft rather than decoded from storage.
- Formalization and B3 reports merge in stage order with hidden report state preserved. Any error yields `Invalid`; only a warning-only result can form `SubmissionPreparationCheck.Prepared`.
- Prepared is a short-lived sanctioned pairing of the proposed document and validated definition. It remains an internal stage token, not a public plan or committed revision.

## C4: public preparation boundary

- `SkillCommitPrecondition.ExpectedAbsent` expresses an expected empty Store history; `ExpectedLatest` expresses an expected greatest committed reference. Neither proves ownership, reserves quota, or carries the proposed revision.
- A standalone `ExpectedLatest` at `Integer.MAX_VALUE` is a valid CAS state value. C3 converts that state to revision exhaustion, so it cannot form a valid plan. The plan factory still fails fast if the proposer defensively reports exhaustion; this branch is unreachable under the current C3 Prepared invariant and does not justify an illegal construction seam.
- `SkillSubmissionPlan` retains the prepare-time owner, precondition, proposed document, and validated definition from one Prepared token. It excludes the report and exposes no persistence, network, retry, or commit operation.
- `SkillSubmissionOutcome` is specifically a preparation outcome with `Prepared`, `Invalid`, `Conflict`, `IdentityRejected`, and `RevisionExhausted`. Prepared means a transient plan may be attempted at P3-D; it does not mean stored, allocated, quota-admitted, committed, or runtime-visible.
- `IdentityRejected` represents an opaque authorization or admission rejection. Only `Prepared` exposes a plan; every other outcome exposes no partial document, definition, precondition, or owner.
- The internal mapper preserves each source report and domain payload reference without copying, merging, filtering, or reordering. Public outcome records are data-transfer values, not unforgeable capability tokens.
- `Prepared`, `Conflict`, `IdentityRejected`, and `RevisionExhausted` accept warning-only reports, including truncation without an omitted error. `Invalid` requires an error, including an omitted error hidden by the report bound.
- The mapper is the only production construction point in P3-C4. That is a phase-local repository gate, not a security boundary; a future sanctioned producer requires a P3-D design decision and a corresponding gate update.

### Invalid report provenance

The one public `Invalid` variant deliberately preserves three report shapes without normalization: C1 schema rejection contains read provenance followed by the schema error; C3 formalization rejection contains prior warnings followed by completeness errors; C3 B3 rejection contains the formalization report followed by B3 issues. The mapper keeps the exact source report reference and order in every case.

### Plan lifetime and commit handoff

A plan is discarded after server restart, policy or descriptor/registry reload, commit conflict, failed reauthorization, owner mismatch, or authoritative quota rejection. It is never edited and retried. Before commit, composition must reacquire authority, match the plan owner, obtain an immutable quota snapshot, and invoke Store compare-and-insert. The Store atomically performs final owner, quota, precondition, capacity, and insert checks.

## Resolved P3-D0 commit obligations

P3-D0 resolved the former blocker: immutable quota snapshot admission, committed-owner check, `ExpectedAbsent`／`ExpectedLatest` CAS, technical capacity, and revision insert share one server-thread-confined Store aggregate mutation. All typed failures precede the first truth mutation, and an inserted revision can never later produce quota rejection. The normal Store result vocabulary has committed, conflict, quota, capacity, and opaque owner rejection; it has no commit-time `RevisionExhausted`. P3-C's preparation `RevisionExhausted` remains unchanged. Whether a later composition result reuses the Prepared warning report remains deferred.
