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
- D2 owns the authenticated facade, unique policy provider, Draft creation／SkillId mint adapter,
  exactly-once P3-C composition, prepared Attachment transition, and composition outcome. It owns no
  recovery listener.
- D3 owns bootstrap and login recovery, persisted-readback prefix clear, paired crash／restart probes,
  the combined fixed-heap Gate, Gradle／CI wiring, and final phase gates.

D1, D2, D3, and the required remote memory Gate must all pass before P4-D is complete. P4-E remains
not started.

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

```text
P4-D0 = DOCUMENTATION COMPLETE
P4-D1 = NOT STARTED
P4-D2 = NOT STARTED
P4-D3 = NOT STARTED
P4-D  = INCOMPLETE
P4-E  = NOT STARTED
```
