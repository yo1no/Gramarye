# P3-C skill submission decision ledger

This ledger records the implemented P3-C phase seams. Architecture authority remains the approved [P3 scoped amendment §9-A](../codex-spec/17_P3資料模型修正案.md#9-a-p3-c-submission-preparation-與-p3-d-commit-邊界), the [frozen skeleton](../codex-spec/16_骨架定案清單_NeoForge1.21.1_凍結版.md), the [implementation contract](../codex-spec/Codex_實作總規格Prompt.md), and the [detailed phases](../codex-spec/NeoForge1.21.1_詳細實作步驟.md). The earlier [P3-C0 boundary](P3-C0-submission-boundary.md) remains in force, the resolved Store-boundary decisions are indexed by [P3-D0](P3-D0-store-boundary.md), and the later composition handoff is indexed by [P4-D0](P4-D0-submission-journal-boundary.md). This is a compact decision index, not a second submission specification.

## C0: prepare and commit ownership

- P3-C prepares an immutable, transient plan; it neither mutates Store state nor formally allocates a revision. A prepared plan is not a committed revision.
- P3-D owns a production pure-Java Store aggregate, committed owner/history truth, atomic admission／CAS／insert, formal revision allocation, pin／unpin／reclaim, and the plan commit boundary. Store's greatest retained revision is the allocator truth and implicit root; an Attachment latest pointer is not.
- The P4-D2 authenticated composition facade acquires one Store authority observation and the player's authoritative Draft, invokes P3-C prepare, then invokes P3-D commit through the P4-D1 narrow Store port. Neither domain boundary depends on Minecraft player classes.
- P4 owns the sole Overworld SavedData persistence adapter, snapshot encoding, complete offline roots, dirty marking, and Store／Attachment ordering, recovery, and reconciliation; it does not reimplement P3-D domain policy.

## C1: Draft provenance and formalization

- `SkillSubmissionInput.direct` and `fromReadResult` are the controlled Draft provenance entry points. Read facts become bounded warnings before the Draft schema gate.
- Unsupported Draft schema and incomplete Trigger/Action slots fail without a partial document. An empty but complete Draft reaches B3, which is the sole empty-skill rejection policy.
- Report combination retains source issue order and then inherits `truncated` and `omittedError`; hidden errors may not be lost.

## C2: identity and authority

- `SkillIdSource` is the server-side mint contract. Minting is neither reservation nor authorization, and a transient mint grant is not a restart-stable submission credential. P4-D2's `SkillDraftCreationService` is its first production consumer; the composition root owns the sole random-UUID adapter, while P3-C retains only the pure domain port.
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

A plan is discarded after server restart, policy or descriptor/registry reload, commit conflict, failed reauthorization, owner mismatch, or authoritative quota rejection. It is never edited and retried. Before commit, composition must reacquire one Store authority snapshot, match the plan owner, and use the same immutable `SkillSubmissionPolicySnapshot` acquired exactly once for that attempt: its `ValidationContext` feeds P3-C and its `SkillQuota` feeds Store compare-and-insert. P3-C and P3-D own neither the provider nor a second default snapshot. The Store atomically performs final owner, quota, precondition, capacity, and insert checks.

## Resolved P3-D0 commit obligations

P3-D0 resolved the former blocker: immutable quota snapshot admission, committed-owner check, `ExpectedAbsent`／`ExpectedLatest` CAS, technical capacity, and revision insert share one server-thread-confined Store aggregate mutation. All typed failures precede the first truth mutation, and an inserted revision can never later produce quota rejection. The normal Store result vocabulary has committed, conflict, quota, capacity, and opaque owner rejection; it has no commit-time `RevisionExhausted`. P3-C's preparation `RevisionExhausted` remains unchanged.

The approved [P4 amendment](../codex-spec/18_P4持久化與組合修正案.md) closes the former composition-report deferral: P4-D uses a distinct composition outcome, and once preparation produced a warning-only report, every later commit／quota／capacity／journal outcome preserves that same report reference without rebuilding, merging, or persisting it.

## P4-D0 composition handoff

P4-D preserves a successfully submitted Draft; it only publishes the prepared latest-pointer
transition. A pre-preparation failure invents no `ValidationResult`; `PreparationRejected` wraps the
exact existing P3-C non-Prepared outcome and its report; every post-Prepared result holds the exact
same warning-only report reference. A normal same-pointer result is a stale／invariant failure with
zero Store commit, not an idempotent duplicate submission. Exact public outcomes,
journal semantics, and the Store-side port remain owned by the [P4 amendment §§14–16](../codex-spec/18_P4持久化與組合修正案.md#14-submission-composition)
and the compact [P4-D0 ledger](P4-D0-submission-journal-boundary.md).

## P4-D2-A exactly-once handoff

P4-D2-A adds package-private `SkillSubmissionPreparationPipeline` in the submission package. Its
only operations are the existing stage-shaped calls: direct Draft precheck, authority check, the
three short-circuit mappings, and preparation plus mapping. `SkillSubmissionInput.direct` is called
once, each selected C1／C2／C3 stage is called once, and exactly one C4 mapper terminates a path. It
does not call a Reader, Writer, raw resolver, Store, Attachment mutation, or manually reconstruct a
plan, revision, definition, or report. Its single package-private nested stage adapter exists only
for invocation-count tests and does not make a P3-C token public.

`SkillDraftCreationService` is now the first production `SkillIdSource` consumer. Its injected
package-private `RandomUuidSkillIdSource` is the sole production random-UUID owner; minting remains
neither reservation nor authorization. The service constructs only the current empty Draft and
publishes it through the controlled immutable P4-C replacement API after an exact collision check;
it performs no Store call or retry.

The immutable `SkillSubmissionPolicySnapshot` keeps the exact quota／validation pair for later D2-B
composition, while the eleven-variant `SkillSubmissionCompositionOutcome` is distinct from the
five-variant P3-C preparation outcome. `PreparationRejected` rejects `Prepared` and derives the
exact existing report; all post-preparation variants require and retain the exact warning-only
report object. D2-A does not invoke the provider or build the authenticated facade: those ordering
and publication responsibilities remain owned by D2-B.

D2-A is complete: its production commit is present at `HEAD`／`origin/main`, local full regression
and the existing memory Gates passed, and the externally reported remote `build`, `P4-A3 memory
gates`, `P4-B memory gates`, and `P4-C memory gates` jobs all passed. At D2-A closure, D2-B was ready
for implementation, while the authenticated facade, composition-root wiring, and normal submission
GameTests did not yet exist.

## P4-D2-B authenticated handoff

`SkillDefinitionSubmissionService.submit(ServerPlayer, SkillId)` now owns the Minecraft-facing
composition without moving player types into P3. The authenticated player UUID is the sole owner
source, while the authoritative Draft comes from the controlled P4-C service. The production
pipeline uses registry-backed definition lookups and preserves the existing C1–C4 domain stages.

The facade executes Draft lookup, C1 precheck, one Store authority observation, C2 authority, one
combined quota／validation policy snapshot, C3 prepare, and C4 map in that order. Runtime invocation
counters prove every selected P3-C stage runs exactly once and that invalid／conflict／identity paths
invoke neither the policy provider nor any downstream persistence step. The same policy snapshot's
validation context feeds C3 and its quota feeds the D1 preflight; neither authority nor policy is
resampled.

Only the exact P3-C `Prepared` plan proceeds to P4-C transition preparation, D1 Store／journal
preflight, a fresh non-mutating Attachment currentness check, one D1 commit, and then Attachment
publication. Every later branch retains the exact original warning-only report reference. A commit
or publication result never rebuilds, merges, normalizes, or persists that report, and normal
submission preserves the authoritative Draft unchanged. Publication drift after Store commit maps
to pending Attachment recovery without Store rollback, journal clear, retry, or P3-C re-execution.

Two normal required GameTests exercise the full success path and postcommit Attachment drift; the
normal required total rises from seven to nine. The D2-B API／phase tests and portable
`scripts/verify-p4-d2-configuration.sh` guard the exactly-once handoff and retain the phase-local
prohibitions on recovery, fixed-heap／Gradle／CI, offline roots, reconciliation, reclaim, and network
work. All nine normal required GameTests passed, as did the local full regression and existing
fixed-heap Gates. The D2-B production commit is present at `HEAD`／`origin/main`, and the externally
reported remote `build`, `P4-A3 memory gates`, `P4-B memory gates`, and `P4-C memory gates` jobs all
passed. D2-B and P4-D2 are therefore complete. Recovery／readback／clear, the crash matrix, and the
combined P4-D fixed-heap Gate remain D3-owned. D3 is ready for read-only design review; P4-D remains
incomplete, and P4-E remains blocked.
