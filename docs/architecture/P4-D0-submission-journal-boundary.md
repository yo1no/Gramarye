# P4-D0 submission journal and composition authority boundary

This ledger records the documentation-only P4-D0 decisions. Complete authority remains the
[P4 amendment §§14–16](../codex-spec/18_P4持久化與組合修正案.md#14-submission-composition); this
page is a compact phase index, not a second P4-D specification. P3-C and P3-D domain policy remain
unchanged.

## Phase split

- D0 fixes authority only: journal framing, partial availability, policy ownership, submission
  composition, recovery semantics, and the combined memory obligation.
- D1 owns the strict journal model and migration, derived operational state, single Store authority
  snapshot, narrow Store submission port, opaque prepared commit handle, Store／journal publication,
  and journal-root projection. It owns no authenticated facade or event listener.
- D2 is forcibly split into D2-A followed by D2-B. Together they own the authenticated facade,
  unique policy provider, Draft creation／SkillId mint adapter, exactly-once P3-C composition,
  prepared Attachment transition, and composition outcome. They own no recovery listener.
- D3 owns bootstrap and login recovery, persisted-readback prefix clear, paired crash／restart probes,
  the combined fixed-heap Gate, Gradle／CI wiring, and final phase gates.

D1, D2-A, D2-B, D3, and the required remote memory Gate must all pass before P4-D is complete. P4-E
remains blocked until P4-D completes.

## Journal physical boundary

The zero-length `pending_attachment_updates_blob` remains the sole canonical empty-journal sentinel.
A non-zero blob is exactly the no-root-name arbitrary-Tag coordinate emitted by
`NbtIo.writeAnyTag`: one Compound type byte followed by its payload. `writeUnnamedTag`, named-root
framing, mixed coordinates, and `+2` compensation are prohibited. The outer raw payload ceiling is
1,048,576 bytes; raw entries count toward the existing 4,096 ceiling before deduplication or semantic
validation. A valid nonzero journal whose entries list is empty loads with rewrite required and later
becomes the zero sentinel through a safe publication.

The amendment owns the exact V0 fields, strict pre-materialization duplicate／type／EOF checks,
migration axis, failure precedence, UUID／reference representations, and canonical writer. This
ledger deliberately does not repeat that schema.

## Canonical chains and operational availability

The stable key is `(owner, skillId, targetGeneration)`. Canonical order is owner UUID, SkillId UUID,
then target generation. Multiple pending entries for one owner／skill are allowed only as one bounded,
continuous generation-and-pointer chain. Duplicate keys, gaps, branches, or partial chains make the
whole derived journal operational state Unavailable; no entry is silently repaired or discarded.

The exact P4-B pending blob is the sole persistent pending-transition truth. Decoded journal Ready／
Unavailable is a derived operational view bound to that blob and the exact live SavedData Ready
identity. On malformed, future, migration-failed, chain-invalid, or Store-target-invalid input:

- Store find/latest/owner/count and pin/close remain available;
- submission, journal append／clear, recovery, and production reclaim composition stop;
- journal root projection is Unavailable and P4-E global roots are Incomplete;
- the original opaque bytes remain exact, non-dirty, and unmodified.

Only a fully decoded, chain-valid, Store-audited journal may publish canonical rewrite bytes and then
mark SavedData dirty.

## Submission ownership

P4-D uses one server-thread Store observation rather than composing separate owner and latest reads.
The selected cross-package seam is a distinct narrow
`com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort`; it may expose only
bounded snapshots, statuses, and opaque prepared handles, never the raw Store, carrier, SavedData
Ready, or pending bytes.

The Gramarye composition root owns exactly one `SkillSubmissionPolicyProvider`. Each submission
attempt obtains one immutable snapshot containing both the `SkillQuota` used by P3-D and the
`ValidationContext` used by P3-C. The V0 default is Unlimited plus `MagicPolicyLimits.DEFAULTS`,
created only by that provider. D2's `SkillDraftCreationService` is the first production
`SkillIdSource` consumer, using the one composition-root-owned random UUID adapter.

Authenticated owner and Draft authority come from the server player and that player's Ready
Attachment. Successful submission retains the Draft and does not mutate editor state. P3-C stages
run exactly once. Pre-preparation failure does not invent a report; `PreparationRejected` wraps the
exact existing P3-C non-Prepared outcome and its report; every post-Prepared outcome keeps the exact
same warning-only report reference. A normal same-pointer result is not a duplicate commit.

## Commit, recovery, and dirty

All Store carrier, journal, and inner-carrier work plus the final identity／authority recheck precedes
the single P3-D commit. A Store `Committed` result permits publication of the prebuilt Store carrier
and journal, followed by dirty, then the prepared Attachment transition. Attachment publication
failure leaves the committed Store and dirty journal intact for recovery.

Journal clear requires later persisted playerdata readback. A persisted chain base replays the chain
without clearing it; a persisted intermediate or final target confirms and clears the matching
prefix. Third-state, missing-target, and owner-mismatch cases retain the journal and fail closed.
Clear prebuilds the remaining journal and inner carrier, publishes, and then marks dirty; recurrence
after a crash is idempotent.

The contract is in-memory Store-first with scheduled persistence, not a cross-file transaction.
Minecraft may synchronously replace playerdata before the later SavedData write is durable; a crash
in that outer window can leave a new Attachment pointer with an old Store and no journal. P4-D does
not claim to repair that missing Store target; P4-E later reconciles against Store truth.

## Fixed-heap and implementation gates

P4-D completion requires one fixed
`-Xms512m -Xmx1024m -XX:+ExitOnOutOfMemoryError` process containing the full current and prospective
Store carriers, the largest valid 4,096-entry V0 journal, prospective inner carrier, SavedData deep
copy, P3-C plan/report, prepared and current Attachment states, and journal root projection while it
performs commit, save, restart, recovery, and clear. Separate earlier memory Gates cannot substitute
for this combined workload.

## D1 implementation ledger

D1 is implemented under `com.yo1no.gramarye.magic.definition.store`. The package-private
`PendingAttachmentJournalSchema`, `PendingAttachmentJournalWireScan`,
`PendingAttachmentJournalFraming`, and `PendingAttachmentJournalMigration` layers enforce the
zero sentinel, the exact `writeAnyTag` nonzero coordinate, iterative all-tag duplicate-aware scan,
finite legacy materialization, adjacent migration coverage, current V0 exact decode, and the
1,048,576-byte／4,096-entry bounds. Golden tests lock zero, minimal nonzero, one-entry,
`writeUnnamedTag` negative, modified-UTF, duplicate／late-framing precedence, all NBT payload kinds,
and exact byte／entry boundaries.

`PendingAttachmentJournal` is the immutable canonical domain journal. It validates the stable key,
owner／SkillId／target-generation order, generation and pointer continuity, route pairing, and
chain-final-only append. `EncodedPendingAttachmentJournal` owns only the matching opaque P4-B
pending handle. `PendingAttachmentJournalState` and `PendingAttachmentJournalLifecycle` add the
derived `Ready`／`Unavailable` and `Uninitialized`／`Installed` layers to SavedData Ready; failures
retain the exact source pending carrier, leave Store read／pin available, and never become an empty
journal.

`SkillDefinitionStore` now owns the distinct-target audit and the one-history submission-authority
observation. `JournalTargetAuditProof` is exactly `AuditedExisting` or
`ConditionalOnExactCommit`; the conditional variant binds the exact base journal, carrier update,
owner, route, target, and committed reference, then releases those heavy bindings when discharged
immediately before publication.

The sole new public top-level is `SkillDefinitionStoreSubmissionPort`, owned once by
`SkillDefinitionStoreService`. Its eight bounded operations expose only authority/status/root
results and opaque single-use prepare or clear handles. Submission preparation prebuilds the Store
carrier, appended canonical journal, shared pending handle, inner carrier, SavedData Ready,
success result, and fail-closed fallback without mutation. Commit consumes the handle, rechecks
exact identities and authority, calls `SkillDefinitionStore.commit` exactly once, publishes only an
exact committed target, then marks dirty. A postcommit pairing failure publishes prebuilt SavedData
Unavailable and clears dirty. Prefix clear removes only a confirmed route prefix, preserves its
canonical suffix, publishes the prebuilt carrier, and then marks dirty. Bootstrap publishes a
canonical operational view without a dirty delta, or publishes a validated rewrite before dirty;
Unavailable bootstrap preserves the source carrier and dirty state.

D1 adds no authenticated facade, provider, Attachment publication, event listener, recovery
orchestration, Gradle source set, CI job, reclaim caller, or P4-E collector. P4-D1 closure includes
strict pending-journal framing, the journal operational lifecycle, authority observation, the
Store-side submission port, and prepare／commit／bootstrap／clear. The local full regression passed.
The externally reported remote `build`, `P4-A3 memory gates`, `P4-B memory gates`, and `P4-C memory
gates` jobs passed. D1 intentionally adds no Gradle／CI memory Gate; the combined P4-D memory Gate
remains D3-owned. Repository contents do not establish branch-protection required-check
configuration, which remains external governance unknown／pending.

The completed P4-D2 read-only design review forces the D2-A／D2-B split, authorized D2-A
implementation, and requires no D0.1 authority patch.

## D2-A completed implementation ledger

D2-A refines the existing D1 `PreparationFailure` vocabulary without changing the public Store-port
method set. Four typed Store blob-capacity failures and the journal entry-count／encoded-byte
failures now map to six distinct capacity codes. Plan／transition pairing, authority-precondition
drift, Store-carrier invariants, journal-chain invariants, and the unreachable SavedData
inner-carrier invariant remain separate machine codes. The Store and journal classifiers are
exhaustive typed switches with no default, message, exception-class, or enum-name inference. The
inner-carrier maximum remains dominated by its existing component ceilings:
`91 + 67,108,864 + 1,048,576 = 68,157,531 <= 69,206,016`; no false capacity outcome was added.

The P4-C service adds `prepareLatestTransitionToCurrent`, non-mutating
`checkPreparedTransitionCurrent`, and `TransitionCurrentness`. Prepare-to-current derives pointer
and generation from one `observeChecked` call and does not install a missing Attachment. Currentness
and publication use one shared private validator over exact server, player, original Ready identity,
pointer, and generation; a currentness check neither consumes the token nor calls `setData`.

The submission package now contains public `SkillSubmissionPolicyProvider`, immutable
`SkillSubmissionPolicySnapshot`, `SkillDraftCreationService`, and the sealed eleven-variant
`SkillSubmissionCompositionOutcome`. Package-private `DefaultSkillSubmissionPolicyProvider` is the
only V0 owner of Unlimited plus `MagicPolicyLimits.DEFAULTS`; package-private
`RandomUuidSkillIdSource` is the only production `UUID.randomUUID()` owner. Draft creation performs
one authenticated Attachment availability gate, one mint, one collision lookup, and at most one
immutable `putDraft`, with no retry or Store call. Package-private
`SkillSubmissionPreparationPipeline` composes the existing C1–C4 tokens and mappers exactly once
without adding a public stage token or raw ingress.

D2-A adds no authenticated `SkillDefinitionSubmissionService`, composition-root wiring, additional
D2 GameTest entry or holder, Store／Attachment commit composition, event or recovery listener,
Gradle source set, CI job, offline root enumeration, reconciliation, reclaim caller, or network
surface. Its seam assertions reuse the existing P4-C normal GameTest holder without changing the
required-test count. Local unit, API, normal GameTest, dedicated-smoke, full regression, and the
existing A3／B／C configuration and memory Gates passed. The D2-A production commit is present at
`HEAD`／`origin/main`, and the externally reported remote `build`, `P4-A3 memory gates`, `P4-B
memory gates`, and `P4-C memory gates` jobs all passed. D2-A is therefore complete. At D2-A closure,
D2-B was ready for implementation and had not yet created the authenticated facade,
composition-root wiring, or normal submission GameTests.

## D2-B completed implementation ledger

D2-B adds the stateless public-final `SkillDefinitionSubmissionService`. Its sole public domain
operation is authenticated `submit(ServerPlayer, SkillId)`; null, missing-server, and wrong-thread
conditions fail before any domain mapping. The service derives `SkillOwnerId` only from the server
player UUID and retains no server, player, Draft, Store, journal, prepared handle, report, or mutable
attempt state. Its production factory constructs the P3-C pipeline with deferred registry-backed
trigger／action lookup, the production analyzer, and projector. `Gramarye` owns one random-UUID
`SkillIdSource`, one `SkillDraftCreationService`, one default policy provider, and one submission
service alongside the existing P4-C Attachment service and D1 Store submission port; none is a
static locator or per-submit allocation.

The runtime order is fixed and invocation-count tested as Draft lookup, C1 precheck, one Store
authority observation, C2 authority check, one policy snapshot, C3 prepare and C4 map, P4-C latest
transition prepare, D1 Store／journal preflight, the non-mutating P4-C currentness recheck, one D1
commit, and only then P4-C Attachment publication. C1 invalidity therefore precedes journal
unavailability, while C2 rejection precedes provider failure. Every short circuit leaves all later
counts at zero: there is no retry, policy resnapshot, authority resnapshot, reprepare, second commit,
or early Attachment publication.

Only C2 `Passed` obtains the immutable quota／validation policy pair, and the exact two members feed
D1 preflight and C3 respectively. Only P3-C `Prepared` enters persistence composition. Transition
capacity, generation, route, no-op, and Attachment-unavailable results and all D1 preparation,
domain-commit, prepared-base, unavailable, and postcommit-invariant results map exhaustively to the
existing eleven-variant composition outcome; no message or enum-name inference is used. A fresh
currentness failure prevents commit. Only an exact D1 `Committed(target)` permits publication;
postcommit publication no-op, drift, quarantine, or bounded runtime failure retains Store and
journal truth as `CommittedPendingAttachmentRecovery`, without rollback, clear, or retry.

Every post-Prepared branch retains the exact warning-only P3-C report reference. The authoritative
Draft is preserved on success and every failure, and editor state is not rewritten. Two normal
required GameTests cover the complete authenticated success path and drift injected after Store
commit but before Attachment publication, raising the normal required total from seven to nine.
The portable `scripts/verify-p4-d2-configuration.sh` and D2-B API／phase gates constrain the facade,
root wiring, holder, and test seam while continuing to forbid recovery listeners, fixed-heap／Gradle／
CI additions, offline roots, reconciliation, reclaim, and network code. All nine normal required
GameTests passed, as did the local full regression and existing fixed-heap Gates. The D2-B
production commit is present at `HEAD`／`origin/main`, and the externally reported remote `build`,
`P4-A3 memory gates`, `P4-B memory gates`, and `P4-C memory gates` jobs all passed. D2-B and P4-D2
are therefore complete. Recovery／readback／clear, the crash matrix, and the combined P4-D fixed-heap
Gate remain D3-owned. D3 is ready for read-only design review; P4-D remains incomplete, and P4-E
remains blocked.

```text
P4-D0               = COMPLETE
P4-D1               = COMPLETE
P4-D2 design review = COMPLETE
P4-D2-A             = COMPLETE
P4-D2-B             = COMPLETE
P4-D2               = COMPLETE
P4-D3               = READY FOR READ-ONLY DESIGN REVIEW
P4-D                = INCOMPLETE
P4-E                = BLOCKED
```
