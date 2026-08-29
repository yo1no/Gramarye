# P5-A Server Runtime Event Kernel

## 1. Status and Scope

This record is the consolidated P5-A architecture authority and the consolidated read-only review
closure for the server runtime event kernel. It governs the later P5 implementation but does not
implement it. The repository delta that creates this record is documentation-only. P5 production
source, tests, scripts, Gradle tasks, workflow jobs, runtime tasks, and reads of user world or
playerdata remain zero.

The kernel is server-authoritative, deterministic, bounded, synchronous on the server logic
thread, queue-driven, and isolated from gameplay mutation. Its scope is runtime ownership,
lifecycle, immutable event scheduling, exact-revision leases, identity, cancellation, generic
stable-reference resolution, execution and pending budgets, circuit breaking, diagnostics, and
cleanup. P6 owns gameplay-specific resolution and mutation. The future persistent schedule
product remains outside this transient kernel.

P4-E is complete. The P5-A original review is partially superseded by P5-A-R1 and P5-A-R2. Both
reconciliations passed, and the R2 companion metadata seal completed the evidence identity. The
consolidated design is complete and passed review. P5 is not split. P5 implementation and P6 have
not started.

## 2. Authority and Supersession

The applicable authority order is:

1. Frozen repository authority.
2. P5-A-R2 for cancellation handles; creation, schedule, and deadline coordinates; persistence;
   generic reference resolution; the global execution boundary; effective limits; and directly
   derived results, retention, and tests.
3. P5-A-R1 for skill-instance identity, budget attribution, multi-tier execution budgets, pending
   circuit breakers, deterministic eligibility filtering, diagnostics, and directly derived
   results, retention, and tests not superseded by R2.
4. Original P5-A for the remaining owner, lifecycle, event, queue, scheduler, P6, test, and CI
   decisions.

The frozen specifications, P3 data-model amendment, and P4 persistence/composition amendment were
checked under their scoped precedence. No active conflict remains. The persistence qualification
is mandatory: frozen persistent Schedule truth belongs to a future `RuntimePersistentStore` with
data-only `ScheduledTaskDefinition` state; the P4 carrier expressly does not own that product.
P5-A represents the persistence choice but accepts only transient work. This record neither
discharges nor substitutes for the future persistent-product obligation.

The final model has per-instance, per-attribution, and per-server execution ceilings; pending-only
circuit breakers; one canonical skill-instance identity; independent schedule and deadline
coordinates; an explicit persistence value; P5-owned generic stable-reference resolution; a
server-level no-peek global boundary; and one immutable downward-configured limit snapshot per
server slot.

## 3. Existing Repository Inventory

The existing composition root is `com.yo1no.gramarye.Gramarye`. Existing public values reused by
P5 are `EventId`, `SkillInstanceId`, `SkillReference`, and `TriggerEventKind`; their public surface
does not authorize a new public P5 API. `SkillDefinitionStoreService` already provides controlled
exact-reference `find` and `pin`, and `ControlledSkillPin` is the transient revision-root handle.
`ValidatedSkillDefinition` and `ValidatedNodeDefinition` are immutable runtime projections.
`MagicRegistries` remains the only formal Trigger/Action registry truth. `ActionType` validates
definitions but does not execute gameplay.

At this record's base, production has no `SkillRuntimeService`, P5 tick listener, P5 queue,
runtime persistent store, P5 SERVER config, generic runtime resolver, P6 execution port,
EffectPipeline, ManaTransaction service, or P5 root caller. `EventId`, `SkillInstanceId`, and
`ScheduleId` have no production consumer outside their declarations. Product Trigger and Action
entries are both zero. `MagicPolicyLimits` is not a NeoForge SERVER config and must not be reused
as one.

The architecture index is non-exhaustive, and repository gates do not require this record to
modify it. No existing repository file is part of this closure delta.

## 4. Runtime Ownership

The sole owner is the package-private final class:

```text
com.yo1no.gramarye.SkillRuntimeService
```

Exactly one instance is retained by `Gramarye`. It owns at most one exact `MinecraftServer`
identity slot in an instance-owned identity map. It is not static, global, per-player, per-skill,
or discoverable through a service locator. New public top-level runtime types remain zero. There
is no background executor, future, parallel stream, worker thread, wall-clock scheduler, or
asynchronous mutation path.

The service retains the controlled Store read/pin dependency, the existing one
`SkillSubmissionPolicyProvider`, a package-private runtime projector, and one nominal typed P6
port. It retains no P5 config owner, config value, raw config, or runtime-limit provider. The
`Gramarye` composition root retains one package-private `P5ServerRuntimeConfig`; its sole Started
bridge snapshots limits once and passes the immutable value directly to the service. The service
alone publishes and owns `ServerSlot`.

## 5. Server Lifecycle and Threading

The exact lifecycle graph is:

```text
ServerStartedEvent
  -> Gramarye.handleP5RuntimeStarted
  -> P5ServerRuntimeConfig.snapshotForStarted() exactly once
  -> SkillRuntimeService.handleRuntimeStarted(event, limits)
  -> validate server thread and liveness
  -> install one RUNNING slot
  -> open root admission

ServerTickEvent.Post at EventPriority.LOWEST
  -> validate exact slot, thread, state, and liveness
  -> checked P5 runtime-tick advance
  -> reset or lazily reset current-tick budgets
  -> bounded nonrecursive due-event drain

ServerStoppingEvent
  -> enter STOPPING idempotently
  -> close root and child admission
  -> cancel and clear bounded pending runtime work

ServerStoppedEvent
  -> defensively clear even if Stopping was skipped
  -> release all remaining revision leases and runtime references
  -> remove the exact server slot
```

`Gramarye` registers the only Started bridge. `SkillRuntimeService` directly registers the Post,
Stopping, and Stopped callbacks. The four lifecycle coordinates and the Post priority are fixed;
registration order among other listeners at the same priority is not authority.

All admission, scheduling, queue/index/counter mutation, generic resolution, and P6 port invocation
occur synchronously on `server.isSameThread()`. Every operational entry requires the exact RUNNING
slot, `server.isRunning()`, and `!server.isStopped()`. Integrated pause advances no P5 tick because
no base Post is emitted. P4 Starting audit/reclaim completes before P5 opens at Started. P5 makes
zero P4 audit, reclaim, persistence-mutation, startup-root, or playerdata calls.

## 6. Server-Slot State Machine

The normal externally relevant lifecycle is:

```text
ABSENT -> RUNNING -> STOPPING -> REMOVED
```

`EXHAUSTED` and `FAULTED` are bounded internal terminal states. Either closes admission and
dispatch, then converges through stop cleanup to removal. State behavior is:

| State | Root/child admission | Dispatch | Cancellation | Retention |
|---|---|---|---|---|
| ABSENT or REMOVED | `ServerNotRunning` | tick-before-install is a fixed programming failure | `ServerNotRunning` | none |
| RUNNING and live | bounded typed result | bounded drain | bounded typed result | hard-bounded |
| RUNNING but not live | `ServerStopping` | enter STOPPING and clear | cleanup only | clearing |
| EXHAUSTED | `TickExhausted` / rejected | none | cleanup only | already cleared |
| FAULTED | `KernelFaulted` / rejected | none | cleanup only | bounded leases may await Stopped after Error |
| STOPPING | `ServerStopping` | none | idempotent cleanup | empty slot only |

Duplicate install, a second active server identity, install on a stopped server, wrong-thread
lifecycle/drain/child admission, and nested drain are fixed-code programming failures. Double
Stopping and double Stopped are idempotent. Stopped without a slot is a no-op. A service-lifetime
checked server-slot token starts at zero, publishes `1..Long.MAX_VALUE` once each, survives slot
removal, never wraps or reuses, and fails before publication after exhaustion.

## 7. Runtime Event and Schedule Envelope

All new declarations below are package-private. Existing public ID/reference values are reused.
The closed stable values are:

```java
record RuntimeServerToken(long value) {}
record RuntimeSkillInstanceSequence(long value) {}
record RuntimePlayerId(UUID value) {}
record RuntimeEntityId(UUID value) {}

enum RuntimeEntityKind { ANY_ENTITY, LIVING_ENTITY }

sealed interface RuntimeOrigin
        permits ServerOrigin, PlayerOrigin, EntityOrigin, BlockOrigin {}
record ServerOrigin(RuntimeServerToken server) implements RuntimeOrigin {}
record PlayerOrigin(RuntimeServerToken server, ResourceKey<Level> dimension,
                    RuntimePlayerId player) implements RuntimeOrigin {}
record EntityOrigin(RuntimeServerToken server, ResourceKey<Level> dimension,
                    RuntimeEntityId entity, RuntimeEntityKind expectedKind)
        implements RuntimeOrigin {}
record BlockOrigin(RuntimeServerToken server, ResourceKey<Level> dimension,
                   BlockPos position) implements RuntimeOrigin {}

sealed interface RuntimeTarget
        permits PlayerTarget, EntityTarget, BlockTarget {}
record PlayerTarget(RuntimeServerToken server, ResourceKey<Level> dimension,
                    RuntimePlayerId player) implements RuntimeTarget {}
record EntityTarget(RuntimeServerToken server, ResourceKey<Level> dimension,
                    RuntimeEntityId entity, RuntimeEntityKind expectedKind)
        implements RuntimeTarget {}
record BlockTarget(RuntimeServerToken server, ResourceKey<Level> dimension,
                   BlockPos position) implements RuntimeTarget {}

sealed interface RuntimeTriggerCause permits RootTriggerCause, ChildTriggerCause {
    TriggerEventKind eventKind();
}
record RootTriggerCause(TriggerEventKind eventKind) implements RuntimeTriggerCause {}
record ChildTriggerCause(TriggerEventKind eventKind) implements RuntimeTriggerCause {}

sealed interface RuntimeExecutionData permits NoRuntimeExecutionData {}
enum NoRuntimeExecutionData implements RuntimeExecutionData { INSTANCE }

enum RuntimeSchedulePersistence { MEMORY_ONLY, PERSISTENT }

record RuntimeScheduleSpec(
        int delayTicks,
        int deadlineHorizonTicks,
        RuntimeSchedulePersistence persistence) {}

record RuntimeRootEventSpec(
        SkillReference skillReference,
        int nodeIndex,
        RuntimeScheduleSpec schedule,
        RuntimeBudgetAttribution budgetAttribution,
        RuntimeOrigin origin,
        Optional<RuntimeTarget> target,
        RootTriggerCause triggerCause,
        RuntimeExecutionData executionData) {}

record RuntimeEvent(
        EventId eventId,
        SkillInstanceId skillInstanceId,
        RuntimeSkillInstanceSequence skillInstanceSequence,
        RuntimeCancellationToken cancellationToken,
        Optional<EventId> parentEventId,
        SkillReference skillReference,
        int nodeIndex,
        long createdRuntimeTick,
        long scheduledRuntimeTick,
        long deadlineRuntimeTick,
        int depth,
        int childSequence,
        RuntimeSchedulePersistence persistence,
        RuntimeBudgetAttribution budgetAttribution,
        RuntimeOrigin origin,
        Optional<RuntimeTarget> target,
        RuntimeTriggerCause triggerCause,
        RuntimeExecutionData executionData) {}
```

Constructors reject null, every `Optional` object is non-null, and `BlockPos` is defensively copied.
Root parent is absent, depth and child sequence are zero. A child has its parent EventId, depth
`parent + 1`, and canonical child sequence `1..N`. P5 mints every runtime identity and token and
publishes all fields atomically.

A queued event never strongly retains `MinecraftServer`, `ServerLevel`, `ServerPlayer`, `Entity`,
a NeoForge event, mutable definition, Store/service/carrier, tag/NBT, arbitrary map or collection,
callback/function, `Class`, `MethodHandle`, `Object` payload, `Throwable`, config value, provider,
or mutable config.

## 8. Skill-Instance Identity

`SkillInstanceId(UUID)` is the sole canonical product identity. The checked positive
`RuntimeSkillInstanceSequence(long)` is only its deterministic ordering coordinate. There is no
second lineage identity or second canonical sequence.

One successful ordinary root publication creates one server-minted ID, one sequence, one runtime
instance state, and one root lineage. The first sequence is 1; values never wrap or reuse. Root
publication preflights sequence headroom. No caller, client, child plan, or P6 port may supply or
change the ID or sequence. A duplicate live UUID is rejected with zero publication; its distinct
source-level result name is intentionally not invented here because the reviewed vocabulary does
not assign one.

Every split, repeat, chain, zero-delay, and delayed descendant inherits the ID, sequence,
attribution, persistence, exact revision, and owner cancellation binding. A later continuation
belonging to an existing instance must use a named instance-bound seam; it cannot masquerade as a
new root. No such product seam exists in P5 V0.

## 9. Skill Revision Pinning

Each queued event carries one exact immutable `SkillReference`. Dispatch uses that same pinned
revision, never latest, equipped, or a current draft. The slot owns at most the effective active
instance count of `RuntimeRevisionLease` entries keyed by exact reference. Each lease owns one
`ControlledSkillPin`, one immutable `ValidatedSkillDefinition`, and a positive instance reference
count. Instances sharing the same exact revision share the lease.

Root admission performs controlled exact `find`, deterministic projection through existing
registry truth and policy, and controlled `pin` before publication. A normal unavailable revision
is typed and closes any provisional handle. The final instance release decrements the lease and
closes the pin exactly once. Cancellation, breaking, stopping, normal completion, and bounded
fault cleanup all release the same transient root according to their terminal path.

The lease is transient P3 runtime-root state. It is not dirty, persisted, copied into events, or a
new Store truth. P5 performs zero Store audit, reclaim, journal, carrier, world/playerdata, or P4
persistence mutation. A future controlled reclaim sees the existing transient active pin.

## 10. Source, Target and Budget Attribution

The closed attribution types are:

```java
sealed interface RuntimeBudgetAttribution
        permits PlayerRuntimeBudgetAttribution,
                NonPlayerRuntimeBudgetAttribution {
    RuntimeServerToken server();
}

record PlayerRuntimeBudgetAttribution(
        RuntimeServerToken server,
        RuntimePlayerId playerId) implements RuntimeBudgetAttribution {}

record NonPlayerRuntimeBudgetAttribution(
        RuntimeServerToken server,
        NonPlayerRuntimeBudgetDomain domain) implements RuntimeBudgetAttribution {}

enum NonPlayerRuntimeBudgetDomain { SERVER_AUTOMATION }
```

A player root is charged to the authenticated acting player's UUID. All descendants keep that
attribution even when their immediate stable origin is an entity, block, or server. Target,
definition author, mana payer, resource payer, P6, and child plans never rewrite it. Missing,
offline, dead, or dimension-changed players retain their UUID attribution and are counted before
generic resolution. Initial non-player entity, console, ritual, server, and automation roots share
the single `SERVER_AUTOMATION` domain.

Player-owned Marker, Construct, repeat, split, chain, delayed, and server-generated descendants
retain the creating instance's player attribution even when a future product changes the immediate
stable origin. This rule does not add Marker or Construct origin variants in P5 V0; those products
and their instance-bound seams remain absent.

All attribution values are non-null and current-slot-bound. A direct player root's `PlayerOrigin`
must match its player attribution; mismatch returns
`InvalidEvent(BUDGET_ATTRIBUTION_MISMATCH)` with zero queue, identity, or counter mutation. Other
origin shapes may be player-attributed only through a trusted root or inherited instance seam. P7
supplies authenticated attribution but cannot replace P5 counters, keys, or identity. P6 has no
attribution setter.

## 11. Cancellation Handles

Cancellation has one package-private entry seam:

```java
sealed interface RuntimeCancellationHandle
        permits RuntimeCancellationToken, RuntimeEventToken {}

enum RuntimeCancellationTokenInvalidReason {
    EVENT_OWNER_MISMATCH
}

record RuntimeCancellationToken(
        RuntimeServerToken serverSlotToken,
        SkillInstanceId skillInstanceId) implements RuntimeCancellationHandle {}

record RuntimeEventToken(
        RuntimeServerToken serverSlotToken,
        SkillInstanceId skillInstanceId,
        EventId eventId) implements RuntimeCancellationHandle {}

RuntimeCancellationResult cancel(
        MinecraftServer server,
        RuntimeCancellationHandle handle);
```

The whole-instance identity is server-slot token plus `SkillInstanceId`; the exact-event identity
adds `EventId`. Equality is structural. Every event contains exactly one owner token, descendants
inherit it, and there is no cancellation sequence or strong runtime-instance reference. A token
from a prior slot is invalid against a replacement slot. A current-slot event token whose owner
does not match the indexed event yields
`CancellationTokenInvalid(EVENT_OWNER_MISMATCH)`.

Cancellation is eager and bounded. It scans/rebuilds the primary queue and compacts the deferred
buffer; it creates no tombstone or token history. An in-flight event is never preempted. A
whole-instance request removes queued descendants, marks the current frame requested, suppresses
its returned children, and finishes after that frame terminalizes.

## 12. Runtime Tick Coordinates

The slot-owned runtime tick is a checked signed long with baseline 0; the first Post advances it to
1. It resets only with a new server slot, pauses when base Post is absent, never uses the base
server's wrapping tick counter, and never uses wall time.

The three event coordinates are distinct:

```text
createdRuntimeTick   = atomic event-publication tick
scheduledRuntimeTick = earliest eligible execution tick
deadlineRuntimeTick  = last eligible execution tick, inclusive
```

Reservation is not creation. Every published event satisfies
`createdRuntimeTick <= scheduledRuntimeTick <= deadlineRuntimeTick`. Deferral never rewrites any
coordinate. The root base is checked `currentRuntimeTick + 1`; child scheduling is relative to the
current dispatch tick. A failed Post increment returns `RuntimeTickAdvanceResult.EXHAUSTED`, clears
bounded state before any drain, and never wraps.

## 13. Scheduled Tick and Independent Deadline

For a root:

```text
baseTick             = checked(currentRuntimeTick + 1)
scheduledRuntimeTick = checked(baseTick + delayTicks)
deadlineRuntimeTick  = checked(baseTick + deadlineHorizonTicks)
```

For a child:

```text
scheduledRuntimeTick = checked(currentRuntimeTick + delayTicks)
requestedDeadline    = checked(currentRuntimeTick + deadlineHorizonTicks)
deadlineRuntimeTick  = min(parent.deadlineRuntimeTick, requestedDeadline)
```

Delay and deadline horizon are independently validated in `0..effectiveMaximum`; their hard and
default maximum is 12,000 and their minimum is 0. Delay must not exceed the horizon, and a child's
deadline can never extend its parent's. Checked addition never saturates or wraps.

Eligibility is inclusive: `currentRuntimeTick <= deadlineRuntimeTick` may execute. Once
`currentRuntimeTick > deadlineRuntimeTick`, the event terminates as
`DeadlineExpired(deadlineRuntimeTick, observedRuntimeTick)`. Expiry removes only that event,
decrements pending, releases the last lease when applicable, and updates bounded expiry
diagnostics. It consumes zero execution attempts, invokes P6 zero times, creates no breaker or new
root, and leaves siblings intact.

## 14. Schedule Persistence

Every root request and published event carries the closed enum `MEMORY_ONLY` or `PERSISTENT`.
P5-A accepts only `MEMORY_ONLY`. A `PERSISTENT` root returns
`PersistentScheduleUnsupported` after the authorized root-attempt debit but before instance UUID,
instance/event sequence, cancellation token, lease/pin, reservation, pending count, or queue
publication. Children have no persistence setter and inherit the accepted parent value.

There is no silent downgrade, restart/offline catch-up, P4 Store write, playerdata write,
persistent journal, or transient-queue reconstruction claim. `MAX_PERSISTENT_SCHEDULES_PER_SERVER`
is zero. The future `RuntimePersistentStore` and data-only `ScheduledTaskDefinition` remain an open
frozen product obligation outside P5-A.

## 15. Deterministic Ordering

The priority-queue comparator is exactly:

```text
scheduledRuntimeTick
-> EventId.value
-> RuntimeSkillInstanceSequence.value
-> nodeIndex
-> childSequence
```

It uses `Long.compare` and `Integer.compare`, never subtraction, UUID order, map iteration, world
iteration, wall time, randomness, or listener timing. `EventId` is the globally increasing event
sequence. All five keys remain represented even though a live EventId normally resolves the
second key. Cancelled and unused reserved IDs remain permanent holes.

Before IDs are assigned, a returned child plan is copied and canonicalized by
`delayTicks -> nodeIndex -> original ordinal`. Duplicates remain distinct. Lookup maps never order
work. Every `scheduledRuntimeTick <= currentRuntimeTick` item is due; overdue older keys retain
priority over newer work.

## 16. Same-Tick Chaining and Reentrancy

A root delay of zero targets the next P5 drain. A child delay of zero returns to the queue at the
current P5 tick, obtains later IDs than all previously accepted work, and may execute later in the
same drain after earlier due events. A child delay of one targets the next runtime tick.

Java recursive child execution is zero. `dispatching` guards nested drain as a fixed programming
failure. The P6 port receives no owner or enqueue method and can only return one bounded immutable
plan. A reentrant external root during P6 is scheduled from the root base and receives IDs after
the already reserved child range. Reentrant cancellation or stopping never rolls back the current
attempt and suppresses returned children.

## 17. Memory-Only Scheduler

Each `ServerSlot` owns one `PriorityQueue<RuntimeEvent>`, lookup-only bounded indexes, one current
event, fixed-size deferred and cleanup arrays, primitive reservations/counters, bounded instance
and attribution states, and revision leases. The queue exists only for the server-slot lifetime.

The scheduler supports immediate child chaining, next-tick work, bounded positive delay,
cancellation, deadline expiry, and stop/fault clearing. Restart catch-up, offline catch-up,
wall-clock scheduling, cross-server transfer, and persistent work are all zero. Effective delay and
deadline ceilings can be lowered by the slot's immutable SERVER snapshot but cannot exceed 12,000.

## 18. Lineage and Cycle Protection

One `SkillInstanceId` owns one root lineage. Root depth is 0, parent is absent, admitted lifetime
starts at 1, and pending starts at 1. Children have one present parent EventId, depth `parent + 1`,
and inherit all owner coordinates. The resulting parent chain, checked depth, finite lifetime, and
queue-only emission prevent unbounded synchronous cycles.

At default effective values, an instance admits at most 512 lifetime events, 511 descendants,
depth 32, 32 direct children per event, and 16 zero-delay children per event. All child-plan
structural validation is all-or-none. A lifetime, depth, direct-child, zero-delay, schedule, node,
cause, or EventId-capacity failure publishes no child prefix. An instance is removed and its lease
released exactly when committed pending and in-flight counts are both zero.

## 19. Multi-Tier Execution Budgets

The hard and default execution ceilings per P5 tick are:

```text
SkillInstanceId       64
player attribution   128
SERVER_AUTOMATION    128
ServerSlot           512
```

The effective values may be lowered by SERVER configuration while preserving
`1 <= instance <= attribution < server <= 512`; the minimum valid tuple is `1/1/2`. A player and
the non-player domain use equal but distinct attribution states. One player can run two complete
64-attempt instance cohorts. Four attribution maxima equal the default server maximum.

The active internal event-level decision set is:

```java
enum RuntimeBudgetDecision {
    EXECUTE,
    DEFER_SKILL_INSTANCE_TICK_LIMIT,
    DEFER_PLAYER_TICK_LIMIT,
    DEFER_NON_PLAYER_DOMAIN_TICK_LIMIT
}
```

The server limit is not an event decision; it is the separate drain-loop stop reason
`RuntimeDrainStopReason.SERVER_EXECUTION_LIMIT_REACHED`.

The server counter resets after checked tick advance. Instance and attribution counters use the
current P5 tick as a lazy-reset epoch. A zero-reference attribution state remains until the next
tick, preventing same-tick owner churn from resetting a budget. The bounded attribution-state
capacity is the checked sum of effective active server instances and effective root attempts, at
most 192.

## 20. Pending-Work Ceilings

The hard and default pending ceilings are 256 per skill instance, 1,024 per player attribution,
1,024 for `SERVER_AUTOMATION`, and 4,096 per server slot. Every effective ceiling may be lowered to
at least 1 while preserving instance `<=` attribution `<=` server. Exact maximum passes;
projected maximum plus one selects the most specific applicable pending tier.

Committed pending includes primary queue, budget-deferred buffer, and the current in-flight event.
Firm child reservations are separate primitive counts but participate in every projected instance,
attribution, and server check. Polling, deferral, reinsertion, and current execution do not free
the current event's pending unit. Root and child publication share all applicable ceilings.

Pending overflow permanently circuit-breaks only the source `SkillInstanceId`. An unpublished root
candidate publishes no event or instance, consumes no event/instance sequence, acquires no pin,
and changes no pending count. A child trip suppresses the entire plan and removes all queued and
deferred descendants of the source instance; unrelated instances under the same attribution or
server survive. Execution-budget exhaustion only defers and never breaks.

## 21. Execution Count Coordinate

The legally observed due-event sequence is exact:

```text
slot/thread/liveness
-> index, owner and token invariant
-> cancellation/terminal-owner state
-> deadline expiry
-> global loop already proved remaining server budget
-> instance and attribution execution eligibility
-> claim current/in-flight event
-> increment instance, attribution, server execution counters
-> generic P5 exact-revision/reference resolution
-> firm child capacity and EventId reservation
-> P6 port
```

The server equality check occurs at loop entry before any event observation. Index/owner/token
programming invariants precede typed cancellation or owner terminals; cancellation precedes expiry;
expiry precedes tier eligibility. Claim is the single current/in-flight transition, and the three
primitive counter increments occur in fixed instance, attribution, server order before any external
code or allocation.

The increments call no external code and cannot allocate. Once incremented they never roll back.
Revision loss, missing references, the typed unavailable port, gameplay rejection, normal success,
reentrant cancellation/stopping after claim, `RuntimeException`, `Error`, and OOME all retain the
started attempt. Cancellation, expiry, owner loss, invalid queued structure, pre-claim stopping,
and budget deferral count zero. Port-entry diagnostics increment separately immediately before P6
and are not budget authority.

The 64th instance attempt, 128th attribution attempt, and 512th server attempt are allowed. An
otherwise due 65th or 129th event is deferred unchanged. After the 512th server attempt, the next
loop boundary stops without observing an event.

## 22. Dispatch Eligibility and Fairness

Fairness is the five-key total order plus hard instance and attribution eligibility filtering. It
is deterministic bounded waiting, not round-robin equality. There is no borrowing, random
scheduling, wall-clock quantum, background queue, or map-order selection.

When a due event's instance or attribution is ineligible, the event is polled once into the
preallocated 4,096-reference deferred array. Its keys, index, pending count, and capacity remain
unchanged. The drain continues to other due eligible work. Each due event is inspected at most
once per drain. Every exit reoffers deferred references through the unchanged comparator and
nulls all used cells.

The canonical decision precedence while server capacity remains positive is instance, then player
or non-player attribution. Structural and pending checks precede pending breaker selection as
specified elsewhere. A saturated owner cannot block the queue head indefinitely within a tick;
other eligible owners continue in comparator order. On the next tick every survivor re-enters the
same global order without a key rewrite.

## 23. Global Execution Boundary

At every dispatch-loop entry, P5 first compares the current server execution count with the
effective server limit. At equality it must:

```text
not peek
not poll
not inspect owner, deadline, EventId, or any event field
not emit RuntimeBudgetDecision
not create a limit-plus-one execution attempt
not attribute lag, deferral, expiry, breaker, or top-offender data
```

It reinserts and nulls any deferred references, records
`RuntimeDrainStopReason.SERVER_EXECUTION_LIMIT_REACHED`, sets the current-tick server-exhaustion
boolean once, saturating-increments the server-exhaustion tick total once, and stops. The head is
unchanged and no event-specific result exists. Equality with an empty queue still means only that
capacity was consumed, not that work was deferred. On the next checked tick the current flag and
execution count reset; the queue resumes in exact comparator order and only then can lag or expiry
be observed.

## 24. Circuit-Breaker Semantics

The closed breaker reasons are:

```java
enum RuntimeCircuitBreakReason {
    SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED,
    PLAYER_PENDING_EVENTS_EXCEEDED,
    NON_PLAYER_DOMAIN_PENDING_EVENTS_EXCEEDED,
    SERVER_PENDING_EVENTS_EXCEEDED
}
```

Breaker checks occur only after lifecycle, cancellation, owner, and structural schedule/child
checks. Pending precedence is instance, then the applicable attribution, then server. Crossing a
threshold and successfully cleaning up are ordinary typed control flow; the slot remains RUNNING.

For a root candidate, `RuntimeAdmissionResult.CircuitBroken(RuntimeCircuitBreakerSummary)` records
the prospective server-minted instance identity and publishes nothing. For a live child source,
`RuntimeExecutionOutcome.CircuitBroken(RuntimeCircuitBreakerSummary)` transitions only that
instance to `CIRCUIT_BROKEN`, releases reservations, preserves unused EventId holes, suppresses the
entire plan, eagerly cleans matching descendants, and terminalizes the current event without
preemption or counter rollback. Cancellation or stopping observed first wins and creates no
breaker record. A live instance trips at most once; its old handle later becomes `NotPending`.

The immutable summary contains reason, pending-before, requested additional count, maximum,
removed queued/deferred count, and whether an event was in flight. It contains no live object or
unbounded detail.

## 25. Breaker Reasons and Diagnostics

The slot owns a fixed overwrite ring of 256 breaker diagnostic records. A successful record holds
only closed values and primitives: reason, runtime tick, `SkillInstanceId`, optional published
sequence, optional triggering EventId, optional player ID, before/request/maximum, removed count,
and in-flight flag. A root-candidate record has absent sequence and EventId. Record 257
deterministically overwrites the oldest.

Exactly four server-lifetime reason totals saturate at `Long.MAX_VALUE`. Current-tick primitives
cover admission, dispatch, port calls, typed outcomes, breaker trips/removals, three tier-deferral
counts, and maximum lag. Two top-offender slots retain one instance summary and one attribution
summary. Selection is highest count, then smallest instance sequence; attribution ties use the
smallest contributing instance sequence. No map or UUID order participates.

R2 adds bounded deadline primitives
`deadlineExpiredEventsThisTick`, `maximumDeadlineLatenessTicksThisTick`, and
`deadlineExpiredEventTotal`, plus `serverExecutionLimitReachedThisTick` and
`serverExecutionLimitReachedTickTotal`. Current fields reset after checked tick advance; lifetime
totals saturate. Deadline/global outcomes never enter the breaker ring. Diagnostics retain no
event history, event object, world/server/player/entity, Store, definition, payload, callback,
config object, or `Throwable`, and have no disk/network/public publication.

Every observed instance, player, or non-player tier deferral immediately increments its exact
current-tick deferral counter and updates `maximumLagTicksThisTick` with
`currentRuntimeTick - scheduledRuntimeTick`. A later execution or expiry observation recomputes
exact lag. Cancellation or stopping before observation fabricates none. The global no-peek boundary
observes no event and therefore adds no lag, deferral, expiry, breaker, or offender attribution.

## 26. Queue Cleanup Algorithm

The slot preallocates one 4,096-reference cleanup scratch array. On a pending break or whole-instance
cancellation it:

1. Marks the source terminal and releases firm reservations.
2. Drains the primary priority queue into scratch, dropping only matching `SkillInstanceId`
   references and updating event index and committed pending counters.
3. Scans and compacts the budget-deferred array, dropping only matching references.
4. Reoffers primary survivors under the unchanged five-key comparator.
5. Nulls every scratch cell and terminalizes the current frame as required.
6. Removes empty instance/attribution state and releases the final revision lease exactly once.

Primary plus deferred membership is at most 4,096. A cleanup uses at most 4,096 membership checks,
bounded `O(4,096 log 4,096)` heap work, and at most 98,304 comparator levels for two heap passes.
There are no tombstones, retries, drop-oldest policies, unrelated evictions, callbacks, or map-order
survivor choices. A cleanup invariant failure enters FAULTED handling and never returns a false
successful breaker result.

## 27. Stable-Reference Resolution Boundary

P5 adopts loaded-only generic resolution. It owns exact revision/node availability, current server
token, dimension availability and equality, player/entity/block existence, entity kind, and the
generic source/optional-target shape. It never force-loads a chunk and never mutates the world.
The exact failure reasons are:

```java
enum RuntimeReferenceFailureReason {
    WRONG_SERVER,
    DIMENSION_UNAVAILABLE,
    WRONG_DIMENSION,
    MISSING,
    MISSING_OR_UNLOADED,
    UNLOADED,
    TYPE_MISMATCH
}
```

P6 owns effect-specific capability, range, visibility, protection, and gameplay validity. Its
closed rejection reasons are:

```java
enum RuntimePortRejectionReason {
    PORT_UNAVAILABLE,
    EFFECT_SPECIFIC_SOURCE_REJECTED,
    EFFECT_SPECIFIC_TARGET_REJECTED
}
```

A missing exact revision during root admission has execution/port cardinality `0/0`. After a due
event is claimed, defensive revision loss or generic source/target failure has `1/0` and
terminalizes the event. An absent optional target is generically valid and reaches P6. Typed
unavailable/effect-specific rejection and successful P6 outcomes have `1/1`. Cancellation,
expiry, owner loss, and pre-claim stopping have `0/0`. The resolved context is call-scoped,
ephemeral, cannot be retained by the port, and is never queued or diagnosed.

An unexpected `RuntimeException` or `Error` thrown by the P5 generic resolver after claim retains
execution/port cardinality `1/0`; the same classes thrown after P6 port entry retain `1/1`. Both
paths enter the fixed FAULTED cleanup policy and rethrow the identical object.

## 28. SERVER Effective-Limit Model

Future P5 implementation adds one package-private `P5ServerRuntimeConfig`, one strict
`P5RawServerConfigSpec implements IConfigSpec`, one immutable request/load-state model, and one
immutable `P5RuntimeLimits` per RUNNING slot. Current production has no P5 SERVER config.

The exact 16 requested keys, in deterministic decode/range order, are:

```java
enum P5RuntimeLimitKey {
    PENDING_EVENTS_PER_SKILL_INSTANCE,
    PENDING_EVENTS_PER_ATTRIBUTION,
    PENDING_EVENTS_PER_SERVER,
    ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION,
    ACTIVE_SKILL_INSTANCES_PER_SERVER,
    ROOT_ADMISSIONS_PER_TICK,
    EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK,
    EXECUTIONS_PER_ATTRIBUTION_PER_TICK,
    EXECUTIONS_PER_SERVER_PER_TICK,
    EVENTS_PER_SKILL_INSTANCE,
    MAXIMUM_DEPTH,
    DIRECT_CHILDREN_PER_EVENT,
    ZERO_DELAY_CHILDREN_PER_EVENT,
    MAXIMUM_DELAY_TICKS,
    MAXIMUM_DEADLINE_HORIZON_TICKS,
    CANCELLATIONS_PER_TICK
}

enum P5RuntimeConfigurationFailureReason {
    CONFIG_UNAVAILABLE,
    MISSING_REQUIRED_VALUE,
    WRONG_VALUE_TYPE,
    BELOW_MINIMUM,
    ABOVE_HARD_MAXIMUM,
    RELATION_VIOLATION,
    DERIVATION_OVERFLOW
}

enum P5RuntimeReloadDisposition {
    DEFERRED_UNTIL_NEXT_SERVER_SLOT,
    INVALID_FOR_NEXT_SERVER_SLOT
}

record P5RuntimeConfigurationFailure(
        P5RuntimeConfigurationFailureReason reason,
        Optional<P5RuntimeLimitKey> primaryKey,
        Optional<P5RuntimeLimitKey> relatedKey) {}

record P5RuntimeRequestedLimits(
        int pendingEventsPerSkillInstance,
        int pendingEventsPerAttribution,
        int pendingEventsPerServer,
        int activeSkillInstancesPerAttribution,
        int activeSkillInstancesPerServer,
        int rootAdmissionsPerTick,
        int executionsPerSkillInstancePerTick,
        int executionsPerAttributionPerTick,
        int executionsPerServerPerTick,
        int eventsPerSkillInstance,
        int maximumDepth,
        int directChildrenPerEvent,
        int zeroDelayChildrenPerEvent,
        int maximumDelayTicks,
        int maximumDeadlineHorizonTicks,
        int cancellationsPerTick) {}

record P5RuntimeLimits(
        int pendingEventsPerSkillInstance,
        int pendingEventsPerAttribution,
        int pendingEventsPerServer,
        int activeSkillInstancesPerAttribution,
        int activeSkillInstancesPerServer,
        int rootAdmissionsPerTick,
        int executionsPerSkillInstancePerTick,
        int executionsPerAttributionPerTick,
        int executionsPerServerPerTick,
        int eventsPerSkillInstance,
        int maximumDepth,
        int directChildrenPerEvent,
        int zeroDelayChildrenPerEvent,
        int maximumDelayTicks,
        int maximumDeadlineHorizonTicks,
        int cancellationsPerTick,
        int descendantsPerSkillInstance,
        int definitionLeasesPerServer,
        int runtimeBudgetAttributionStatesPerServer) {}

sealed interface P5RuntimeLimitLoadState
        permits P5RuntimeLimitLoadState.Requested,
                P5RuntimeLimitLoadState.Invalid,
                P5RuntimeLimitLoadState.Unavailable {
    record Requested(P5RuntimeRequestedLimits limits) implements P5RuntimeLimitLoadState {}
    record Invalid(P5RuntimeConfigurationFailure failure) implements P5RuntimeLimitLoadState {}
    enum Unavailable implements P5RuntimeLimitLoadState { INSTANCE }
}
```

`P5RuntimeRequestedLimits` contains exactly the 16 primitive requested values.
`P5RuntimeLimits` contains those 16 effective primitives plus derived
`descendantsPerSkillInstance`, `definitionLeasesPerServer`, and
`runtimeBudgetAttributionStatesPerServer`. `P5RuntimeConfigurationFailure` contains the closed
reason and optional primary/related keys. The sealed load states are `Requested`, `Invalid`, and
`Unavailable.INSTANCE`. `P5RuntimeConfigurationException` retains only the closed failure.

Failure key shape is exact: `CONFIG_UNAVAILABLE` has both keys absent; a missing, wrong-type, or
range failure has only `primaryKey`; a relation failure has both keys; and a derivation failure has
its source as `primaryKey` plus the other contributing key only when that derivation has one.
For each relation, the left/condition key is primary and the right/consequence key is related.

Raw keys accept integral `Integer` or `Long` only. Missing wins before wrong type for a key; all
range checks follow declaration order. Relations then run exactly:

1. pending instance `<=` pending attribution;
2. pending attribution `<=` pending server;
3. active attribution `<=` active server;
4. active attribution `<=` pending attribution;
5. active server `<=` pending server;
6. execution instance `<=` execution attribution;
7. execution attribution `<` execution server;
8. pending instance `<=` events per instance;
9. direct children `<=` events per instance minus one;
10. zero-delay children `<=` direct children;
11. depth zero implies direct children zero;
12. maximum delay `<=` maximum deadline horizon.

Checked derivations then run in order: descendants equals events minus one; leases equals active
server instances; attribution-state capacity equals checked active server instances plus root
attempts. The factory and compact record constructor both validate the complete model.

The strict SERVER registration is for mod `gramarye`, filename `gramarye-server.toml`, and the
exact spec identity. It uses raw validation rather than a clamping range definition.
`P5RawServerConfigSpec.isEmpty()` is false. An absent selected target with no same-name
`defaultconfigs` file receives the exact defaults; an existing or copied parseable file is raw
requested input. Invalid or unavailable state fails before server-token or RUNNING publication,
with no clamp, fallback, or admission.

`isCorrect` returns true for every parseable raw config so platform correction cannot clamp or
replace requested values. `correct` writes the exact 16 defaults only to an empty config.
`acceptConfig(null)` publishes `Unavailable`; non-null input decodes all raw keys and atomically
publishes one complete `Requested` or `Invalid` state without mutating raw input or throwing a
typed product configuration failure. The platform config tracker retains the exact raw spec; its
only runtime-facing edge is the shared bounded load-state cell.

Registration mismatch throws `IllegalStateException("P5_RUNTIME_CONFIG_SPEC_REGISTRATION_MISMATCH")`.
`correct` accepts only empty config and otherwise throws
`IllegalStateException("P5_RUNTIME_CONFIG_CORRECT_NONEMPTY")`. The next-slot atomic state begins at
`P5RuntimeLimitLoadState.Unavailable.INSTANCE`, and the reload disposition begins empty; neither is
ever null. Unavailable Started state produces `CONFIG_UNAVAILABLE` with both keys absent.

`Gramarye.handleP5RuntimeStarted` performs one atomic candidate read through
`snapshotForStarted()`, revalidates, and passes the immutable snapshot directly to the service.
The slot alone retains it. Roots, children, same-tick, delayed, and deferred work read that same
object; events retain none. No raw config, provider, epoch, or history enters the runtime graph.

`Gramarye` contributes exactly one game-bus registration for the Started bridge.
`P5ServerRuntimeConfig` contributes exactly two bound mod-bus registrations:
`handleRuntimeConfigReloading(ModConfigEvent.Reloading)` and
`handleRuntimeConfigUnloading(ModConfigEvent.Unloading)`. Both filter the exact spec, SERVER type,
mod ID, and filename; unrelated events are no-ops.

Hot reload is next-slot-only. The config owner has one
`AtomicReference<P5RuntimeLimitLoadState>` and a separate
`AtomicReference<Optional<P5RuntimeReloadDisposition>>`. Raw acceptance replaces the complete
next-slot state before the Reloading event. Reloading records
`DEFERRED_UNTIL_NEXT_SERVER_SLOT` for `Requested`, otherwise
`INVALID_FOR_NEXT_SERVER_SLOT`; it never changes an active snapshot, work item, counter, deadline,
comparator, or breaker. `latestReloadDisposition()` performs exactly one atomic read and is the sole
consumer surface. Unloading first publishes `Unavailable` and then clears the disposition. Active-
slot hot reload is unsupported.

## 29. Full Hard / Default / Minimum / Effective Limits

This is the complete one-row-for-one-row final R2 limit inventory. `H`, `D`, and `M` mean hard
maximum, default effective, and minimum effective. A row marked retired has no independent Java
symbol, storage, coordinate, or action; it is included only to preserve the complete reviewed
inventory without reviving earlier semantics.

The complete reviewed TSV is reproduced verbatim so every classification, coordinate, reset,
relation, typed failure, action, binding, and reconciliation status remains exact:

```text
name	hard maximum	default effective	minimum effective	configurable	classification	domain/key	count/admission coordinate	reset coordinate	relation constraints	typed failure	breaker/defer/terminal action	snapshot binding	status
MAX_SERVER_SLOTS_PER_MOD_INSTANCE	1	1	1	NO	FIXED_CARDINALITY	SkillRuntimeService exact MinecraftServer identity	ServerStarted install after stopped-server liveness gate	ServerStopped exact remove	exactly one	RuntimeKernelException(Code.DUPLICATE_SERVER_INSTALL) for same identity; RuntimeKernelException(Code.SECOND_ACTIVE_SERVER) for different active identity; RuntimeKernelException(Code.STOPPED_SERVER_INSTALL) is the earlier liveness failure	fail fast; no duplicate or second slot publication	service constant	PRESERVED
MAX_PENDING_EVENTS_PER_SERVER	4096	4096	1	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.pendingEventsPerServer / ServerSlot	root and child committed+deferred+in-flight+firm-reservation projected publication	ServerStopped clear	pendingAttribution<=pendingServer; activeServer<=pendingServer	SERVER_PENDING_EVENTS_EXCEEDED	permanently break only source SkillInstanceId; unrelated work survives	one P5RuntimeLimits per slot	PRESERVED
MAX_PENDING_EVENTS_PER_SKILL_INSTANCE	256	256	1	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.pendingEventsPerSkillInstance / SkillInstanceId	root/child projected committed plus reservation check	instance terminal removal	pendingInstance<=pendingAttribution and <=eventsPerLineage	SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED	break source instance; eager same-owner cleanup	one P5RuntimeLimits per slot	PRESERVED
MAX_PENDING_EVENTS_PER_PLAYER	1024	1024	1	YES	DERIVED_EFFECTIVE_LIMIT	runtime.pendingEventsPerAttribution / player UUID	root/child projected pending check	attribution state purge after zero-ref next tick	pendingInstance<=value<=pendingServer	PLAYER_PENDING_EVENTS_EXCEEDED	break only source instance; other player instances survive	one P5RuntimeLimits per slot	PRESERVED
MAX_PENDING_EVENTS_PER_NON_PLAYER_DOMAIN	1024	1024	1	YES	DERIVED_EFFECTIVE_LIMIT	runtime.pendingEventsPerAttribution / SERVER_AUTOMATION	root/child projected pending check	attribution state purge after zero-ref next tick	identical to player value	NON_PLAYER_DOMAIN_PENDING_EVENTS_EXCEEDED	break only source instance; unrelated automation instances survive	one P5RuntimeLimits per slot	PRESERVED
MAX_ACTIVE_LINEAGES_PER_SERVER	128	128	1	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.activeSkillInstancesPerServer / ServerSlot	root publication preflight	instance removal/stop	activeAttribution<=activeServer<=pendingServer	ActiveLineageCapacityExceeded	typed root rejection; no breaker/pin leak	one P5RuntimeLimits per slot	PRESERVED
MAX_ACTIVE_SKILL_INSTANCES_PER_BUDGET_ATTRIBUTION	32	32	1	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.activeSkillInstancesPerAttribution / attribution	root publication preflight	instance removal	value<=activeServer and value<=pendingAttribution	ActiveBudgetAttributionCapacityExceeded	typed root rejection; no breaker	one P5RuntimeLimits per slot	PRESERVED
MAX_ROOT_ADMISSIONS_PER_TICK	64	64	1	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.rootAdmissionsPerTick / ServerSlot	after same-thread RUNNING/liveness and before persistence/schedule/definition validation	each checked P5 tick advance	1..64	RootAdmissionBudgetExceeded	no breaker or definition work after failure	one P5RuntimeLimits per slot	PRESERVED
MAX_EXECUTIONS_PER_TICK	512	512	2	NO	DERIVED_EFFECTIVE_LIMIT	retired evidence alias only	no independent coordinate	follows server execution counter	equals effective MAX_EXECUTIONS_PER_SERVER_PER_TICK	SUPERSEDED_BY_MAX_EXECUTIONS_PER_SERVER_PER_TICK	no independent action/symbol/storage	derived view only	R1_SUPERSEDED
MAX_EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK	64	64	1	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.executionsPerSkillInstancePerTick / SkillInstanceId	after expiry and all tier eligibility; claim then increment before resolution/port	lazy lastExecutionTick reset	execInstance<=execAttribution	DEFER_SKILL_INSTANCE_TICK_LIMIT	retain unchanged event in deferred buffer; no breaker	one P5RuntimeLimits per slot	PRESERVED
MAX_EXECUTIONS_PER_PLAYER_PER_TICK	128	128	1	YES	DERIVED_EFFECTIVE_LIMIT	runtime.executionsPerAttributionPerTick / player UUID	same execution claim coordinate	lazy attribution tick reset	execInstance<=value<execServer	DEFER_PLAYER_TICK_LIMIT	retain same-player event; other owners continue	one P5RuntimeLimits per slot	PRESERVED
MAX_EXECUTIONS_PER_NON_PLAYER_DOMAIN_PER_TICK	128	128	1	YES	DERIVED_EFFECTIVE_LIMIT	runtime.executionsPerAttributionPerTick / SERVER_AUTOMATION	same execution claim coordinate	lazy attribution tick reset	equal player and <execServer	DEFER_NON_PLAYER_DOMAIN_TICK_LIMIT	retain same-domain event; other owners continue	one P5RuntimeLimits per slot	PRESERVED
MAX_EXECUTIONS_PER_SERVER_PER_TICK	512	512	2	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.executionsPerServerPerTick / ServerSlot	increment on eligible current claim; equality checked at next loop entry	each checked P5 tick advance	execAttribution<execServer<=512	RuntimeDrainStopReason.SERVER_EXECUTION_LIMIT_REACHED	no peek/poll/event decision; stop drain and retain queue	one P5RuntimeLimits per slot	R2_SUPERSEDED
MAX_EVENTS_PER_LINEAGE	512	512	1	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.eventsPerSkillInstance / SkillInstanceId	root initializes 1; child structural all-or-none preflight	instance removal	pendingInstance<=events; descendants=events-1	RuntimeExecutionOutcome.BudgetRejected(LINEAGE_EVENT_LIMIT_EXCEEDED)	structural whole-plan rejection; no pending breaker	one P5RuntimeLimits per slot	PRESERVED
MAX_DESCENDANTS_PER_LINEAGE	511	511	0	NO	DERIVED_EFFECTIVE_LIMIT	SkillInstanceId	child lifetime preflight	instance removal	exactly effective eventsPerLineage-1	RuntimeExecutionOutcome.BudgetRejected(LINEAGE_EVENT_LIMIT_EXCEEDED)	whole child plan rejected	derived in P5RuntimeLimits	PRESERVED
MAX_DEPTH_PER_LINEAGE	32	32	0	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.maximumDepth / SkillInstanceId	child structural preflight	instance removal	root0; depth0 requires direct0	RuntimeExecutionOutcome.BudgetRejected(DEPTH_LIMIT_EXCEEDED)	whole child plan rejected	one P5RuntimeLimits per slot	R2_SUPERSEDED
MAX_DIRECT_CHILDREN_PER_EVENT	32	32	0	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.directChildrenPerEvent / current event	raw list size hard-check before copy; lowered effective preflight after bounded copy	end current dispatch	direct<=events-1; zeroDelay<=direct	RuntimeKernelException(Code.CHILD_PLAN_HARD_CAPACITY_EXCEEDED) above hard; RuntimeExecutionOutcome.BudgetRejected(DIRECT_CHILD_LIMIT_EXCEEDED) above effective through hard	programming fault above hard; typed whole-plan rejection above lowered effective	one P5RuntimeLimits per slot	R2_SUPERSEDED
MAX_ZERO_DELAY_CHILDREN_PER_EVENT	16	16	0	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.zeroDelayChildrenPerEvent / current event	child plan preflight	end current dispatch	zeroDelay<=direct<=32	RuntimeExecutionOutcome.BudgetRejected(ZERO_DELAY_CHILD_LIMIT_EXCEEDED)	whole child plan rejected	one P5RuntimeLimits per slot	R2_SUPERSEDED
MAX_DELAY_TICKS	12000	12000	0	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.maximumDelayTicks / root or child schedule	before checked scheduled tick addition	no counter	delay<=deadlineHorizon	root RuntimeAdmissionResult.DelayOutOfRange or DelayOverflow; child RuntimeExecutionOutcome.ScheduleRejected(DELAY_OUT_OF_RANGE or DELAY_OVERFLOW)	root zero publication; child whole plan rejection	one P5RuntimeLimits per slot	R2_SUPERSEDED
MAX_DEADLINE_HORIZON_TICKS	12000	12000	0	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.maximumDeadlineHorizonTicks / root or child schedule	before checked absolute deadline addition	no counter	delay<=deadlineHorizon; scheduled<=derived deadline	root RuntimeAdmissionResult.DeadlineOutOfRange, DeadlineOverflow, or DeadlineBeforeScheduledTick; child RuntimeExecutionOutcome.ScheduleRejected(DEADLINE_OUT_OF_RANGE, DEADLINE_OVERFLOW, or DEADLINE_BEFORE_SCHEDULED_TICK)	root zero publication; child whole plan rejection	one P5RuntimeLimits per slot	R2_NEW
MAX_CANCELLATIONS_PER_TICK	128	128	1	YES	CONFIGURABLE_RUNTIME_CEILING	runtime.cancellationsPerTick / ServerSlot	after thread/lifecycle validation and before PQ+deferred scan	each checked P5 tick advance	1..128	RuntimeCancellationResult.CancellationBudgetExceeded(maximum)	no scan/mutation on excess	one P5RuntimeLimits per slot	PRESERVED
MAX_RUNTIME_DIAGNOSTICS	0	0	0	NO	ZERO_INVARIANT	retired original evidence alias	no active coordinate	never allocated	general retained record count=0	SUPERSEDED_BY_BOUNDED_DIAGNOSTICS	no active symbol/storage/action	no slot field	R1_SUPERSEDED
MAX_RETAINED_EVENT_HISTORY	0	0	0	NO	ZERO_INVARIANT	ServerSlot terminal/deferral paths	all paths	terminal immediately; never stored	exactly 0	general record insertion forbidden	no completed/deferred per-event history	constant	PRESERVED
MAX_BREAKER_DIAGNOSTIC_RECORDS_PER_SERVER	256	256	256	NO	FIXED_INTERNAL_CAPACITY	ServerSlot fixed overwrite ring	after fully successful typed pending-break cleanup	ServerStopped clear; 257th overwrites oldest	exact physical/logical 256	oldest deterministic overwrite	no gameplay effect; no false record if cleanup faults	service hard constant	PRESERVED
MAX_CURRENT_TICK_TOP_OFFENDER_SLOTS	2	2	2	NO	FIXED_CARDINALITY	one instance plus one attribution	started execution increment	each checked P5 tick advance	exactly 2; global boundary has no offender	stable count then smallest contributing instance sequence	observability only	service hard constant	PRESERVED
MAX_DEFINITION_LEASES_PER_SERVER	128	128	1	NO	DERIVED_EFFECTIVE_LIMIT	SkillReference lease map	lease insertion	last instance release/stop	equals effective activeLineagesPerServer	ActiveLineageCapacityExceeded	typed root rejection; provisional pin closed	derived in P5RuntimeLimits	PRESERVED
MAX_TRANSIENT_CHILD_PLAN_ENTRIES	32	32	32	NO	FIXED_INTERNAL_CAPACITY	one RuntimeChildPlan physical defensive-copy bound	raw list hard-check then copy at most 32; effective validation follows	end current dispatch	copied entries<=32; admitted entries<=effective directChildrenPerEvent	RuntimeKernelException(Code.CHILD_PLAN_HARD_CAPACITY_EXCEEDED) above hard; RuntimeExecutionOutcome.BudgetRejected(DIRECT_CHILD_LIMIT_EXCEEDED) above effective through hard	programming fault before copy above hard; typed whole-plan rejection above lowered effective	service hard constant; not a P5RuntimeLimits component	R2_SUPERSEDED
MAX_RUNTIME_BUDGET_ATTRIBUTION_STATES_PER_SERVER	192	192	2	NO	DERIVED_EFFECTIVE_LIMIT	ServerSlot attribution map	root publication; dispatch changes primitives	zero-ref purge next tick/stop	checked(activeLineagesPerServer+rootAdmissionsPerTick)<=192	RuntimeKernelException(Code.ATTRIBUTION_STATE_CAPACITY_INVARIANT)	fail fast only on impossible invariant	derived in P5RuntimeLimits	PRESERVED
MAX_BUDGET_DEFERRED_EVENTS	4096	4096	4096	NO	FIXED_INTERNAL_CAPACITY	ServerSlot preallocated RuntimeEvent reference array	one due-event eligibility scan	reoffer/null every drain exit/stop	usable refs<=effective pendingServer	RuntimeKernelException(Code.DEFERRED_BUFFER_OVERFLOW)	FAULTED bounded cleanup	service hard constant; effective work from slot snapshot	PRESERVED
MAX_BREAKER_CLEANUP_SCRATCH_EVENTS	4096	4096	4096	NO	FIXED_INTERNAL_CAPACITY	ServerSlot preallocated RuntimeEvent reference array	one pending-break/cancel eager rebuild	null after rebuild/stop	usable refs<=effective pendingServer	RuntimeKernelException(Code.BREAKER_SCRATCH_OVERFLOW)	FAULTED bounded cleanup	service hard constant; effective work from slot snapshot	PRESERVED
MAX_PERSISTENT_SCHEDULES_PER_SERVER	0	0	0	NO	ZERO_INVARIANT	P5 SkillRuntimeService	root persistence validation	always zero	P5 accepts MEMORY_ONLY only	PersistentScheduleUnsupported	zero identity/sequence/pin/pending publication	service invariant	R2_NEW
RUNTIME_SCHEDULE_PERSISTENCE_VARIANTS	2	2	2	NO	FIXED_CARDINALITY	RuntimeSchedulePersistence enum documentation-only schema row; no MagicSafetyCeilings symbol	root spec construction	type lifetime	exact MEMORY_ONLY/PERSISTENT	null rejected by constructor	closed representation only	type declaration, not numeric runtime authority	R2_NEW
P5_ACCEPTED_PERSISTENCE_VARIANTS	1	1	1	NO	FIXED_CARDINALITY	P5 admission documentation-only schema row; no MagicSafetyCeilings symbol	persistence validation after root-attempt debit	product lifetime	exact MEMORY_ONLY subset	PersistentScheduleUnsupported for PERSISTENT	no silent downgrade	service invariant, not numeric constant	R2_NEW
CANCELLATION_OWNER_TOKENS_PER_EVENT	1	1	1	NO	FIXED_CARDINALITY	RuntimeEvent documentation-only schema row; no MagicSafetyCeilings symbol	atomic root/child publication	event terminal/stop	token server+instance equals event	CancellationTokenInvalid on mismatched current-slot handle	typed rejection or exact cancellation	immutable field invariant, not numeric constant	R2_NEW
```

| Limit | H | D | M | Configuration / classification | Coordinate, effective formula, and relation | Typed failure, action, binding, and status |
|---|---:|---:|---:|---|---|---|
| `MAX_SERVER_SLOTS_PER_MOD_INSTANCE` | 1 | 1 | 1 | No; fixed cardinality | Exact `MinecraftServer` identity; exactly one | Duplicate, second-active, or stopped install uses the corresponding fixed `RuntimeKernelException.Code`; service constant; preserved |
| `MAX_PENDING_EVENTS_PER_SERVER` | 4096 | 4096 | 1 | Yes; configurable runtime ceiling | Root/child committed, deferred, in-flight, and firm reservations; attribution pending `<=` server pending | `SERVER_PENDING_EVENTS_EXCEEDED`; break only source instance; one slot snapshot; preserved |
| `MAX_PENDING_EVENTS_PER_SKILL_INSTANCE` | 256 | 256 | 1 | Yes; configurable runtime ceiling | Projected committed plus reservation by `SkillInstanceId`; instance pending `<=` attribution pending and lifetime | `SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED`; eager source cleanup; one slot snapshot; preserved |
| `MAX_PENDING_EVENTS_PER_PLAYER` | 1024 | 1024 | 1 | Yes; derived effective limit from shared attribution request | Player UUID attribution; instance pending `<=` value `<=` server pending | `PLAYER_PENDING_EVENTS_EXCEEDED`; unrelated instances survive; one slot snapshot; preserved |
| `MAX_PENDING_EVENTS_PER_NON_PLAYER_DOMAIN` | 1024 | 1024 | 1 | Yes; derived effective limit from shared attribution request | `SERVER_AUTOMATION`; identical to player value | `NON_PLAYER_DOMAIN_PENDING_EVENTS_EXCEEDED`; unrelated automation survives; one slot snapshot; preserved |
| `MAX_ACTIVE_LINEAGES_PER_SERVER` | 128 | 128 | 1 | Yes; configurable runtime ceiling | Root publication by server slot; attribution active `<=` server active `<=` server pending | `ActiveLineageCapacityExceeded`; typed root rejection and no pin leak; one slot snapshot; preserved |
| `MAX_ACTIVE_SKILL_INSTANCES_PER_BUDGET_ATTRIBUTION` | 32 | 32 | 1 | Yes; configurable runtime ceiling | Root publication by attribution; value `<=` active server and attribution pending | `ActiveBudgetAttributionCapacityExceeded`; typed root rejection; one slot snapshot; preserved |
| `MAX_ROOT_ADMISSIONS_PER_TICK` | 64 | 64 | 1 | Yes; configurable runtime ceiling | Debit after same-thread RUNNING/liveness and before persistence/schedule/definition validation; `1..64` | `RootAdmissionBudgetExceeded`; no later definition work; one slot snapshot; preserved |
| `MAX_EXECUTIONS_PER_TICK` | 512 | 512 | 2 | No; retired derived alias | No independent coordinate; equals effective server execution limit | No active symbol, storage, or action; derived view only; retired by R1/R2 |
| `MAX_EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK` | 64 | 64 | 1 | Yes; configurable runtime ceiling | Started attempt by instance; instance execution `<=` attribution execution | `DEFER_SKILL_INSTANCE_TICK_LIMIT`; unchanged event to deferred buffer; one slot snapshot; preserved |
| `MAX_EXECUTIONS_PER_PLAYER_PER_TICK` | 128 | 128 | 1 | Yes; derived effective limit from shared attribution request | Player attribution; instance execution `<=` value `<` server execution | `DEFER_PLAYER_TICK_LIMIT`; other owners continue; one slot snapshot; preserved |
| `MAX_EXECUTIONS_PER_NON_PLAYER_DOMAIN_PER_TICK` | 128 | 128 | 1 | Yes; derived effective limit from shared attribution request | `SERVER_AUTOMATION`; equals player value and is `<` server execution | `DEFER_NON_PLAYER_DOMAIN_TICK_LIMIT`; other owners continue; one slot snapshot; preserved |
| `MAX_EXECUTIONS_PER_SERVER_PER_TICK` | 512 | 512 | 2 | Yes; configurable runtime ceiling | Increment on eligible claim; equality checked at next loop entry; attribution execution `<` server execution `<= 512` | `RuntimeDrainStopReason.SERVER_EXECUTION_LIMIT_REACHED`; no peek/poll/event result; one slot snapshot; final R2 authority |
| `MAX_EVENTS_PER_LINEAGE` | 512 | 512 | 1 | Yes; configurable runtime ceiling | Root initializes 1; all-or-none child lifetime check; instance pending `<=` lifetime | `BudgetRejected(LINEAGE_EVENT_LIMIT_EXCEEDED)`; no pending breaker; one slot snapshot; preserved |
| `MAX_DESCENDANTS_PER_LINEAGE` | 511 | 511 | 0 | No; derived effective limit | Exactly effective events per instance minus 1 | Same lifetime budget rejection; whole plan rejected; derived in `P5RuntimeLimits`; preserved |
| `MAX_DEPTH_PER_LINEAGE` | 32 | 32 | 0 | Yes; configurable runtime ceiling | Root depth 0; depth 0 requires direct children 0 | `BudgetRejected(DEPTH_LIMIT_EXCEEDED)`; whole plan rejected; one slot snapshot; final R2 authority |
| `MAX_DIRECT_CHILDREN_PER_EVENT` | 32 | 32 | 0 | Yes; configurable runtime ceiling | Raw hard check before copy; effective check after bounded copy; direct `<=` lifetime minus 1 and zero-delay `<=` direct | Raw excess throws `CHILD_PLAN_HARD_CAPACITY_EXCEEDED`; bounded effective excess is `BudgetRejected(DIRECT_CHILD_LIMIT_EXCEEDED)`; final R2 authority |
| `MAX_ZERO_DELAY_CHILDREN_PER_EVENT` | 16 | 16 | 0 | Yes; configurable runtime ceiling | Whole-plan check; zero-delay `<=` direct `<= 32` | `BudgetRejected(ZERO_DELAY_CHILD_LIMIT_EXCEEDED)`; whole plan rejected; one slot snapshot; final R2 authority |
| `MAX_DELAY_TICKS` | 12000 | 12000 | 0 | Yes; configurable runtime ceiling | Checked scheduled-tick addition; delay `<=` deadline horizon | Root `DelayOutOfRange` or `DelayOverflow`; child `ScheduleRejected(DELAY_OUT_OF_RANGE)` or `ScheduleRejected(DELAY_OVERFLOW)`; final R2 authority |
| `MAX_DEADLINE_HORIZON_TICKS` | 12000 | 12000 | 0 | Yes; configurable runtime ceiling | Checked absolute deadline; scheduled tick `<=` derived deadline | Root deadline range/overflow/before-scheduled result; child matching `ScheduleRejected` reason; final R2 authority |
| `MAX_CANCELLATIONS_PER_TICK` | 128 | 128 | 1 | Yes; configurable runtime ceiling | Debit after thread/lifecycle validation and before queue/deferred scan; `1..128` | `CancellationBudgetExceeded(maximum)`; no scan or mutation on excess; one slot snapshot; preserved |
| `MAX_RUNTIME_DIAGNOSTICS` | 0 | 0 | 0 | No; retired zero-invariant alias | No active coordinate or allocation | No active symbol, storage, or action; bounded named diagnostics replace the stale aggregate; retired by R1 |
| `MAX_RETAINED_EVENT_HISTORY` | 0 | 0 | 0 | No; zero invariant | Every terminal/deferral path; exactly zero completed/deferred per-event history | General record insertion forbidden; slot invariant; preserved |
| `MAX_BREAKER_DIAGNOSTIC_RECORDS_PER_SERVER` | 256 | 256 | 256 | No; fixed internal capacity | Fixed overwrite ring; record 257 replaces oldest | Deterministic overwrite; no gameplay effect or false record on cleanup fault; service constant; preserved |
| `MAX_CURRENT_TICK_TOP_OFFENDER_SLOTS` | 2 | 2 | 2 | No; fixed cardinality | One instance plus one attribution; global boundary has no offender | Stable count/sequence selection; observability only; service constant; preserved |
| `MAX_DEFINITION_LEASES_PER_SERVER` | 128 | 128 | 1 | No; derived effective limit | Equals effective active server instances | `ActiveLineageCapacityExceeded`; provisional pin closes; derived in `P5RuntimeLimits`; preserved |
| `MAX_TRANSIENT_CHILD_PLAN_ENTRIES` | 32 | 32 | 32 | No; fixed internal capacity | Physical defensive-copy bound 32; admitted count obeys lower effective direct limit | Raw excess fixed fault; lower effective excess typed rejection; not a `P5RuntimeLimits` field; final R2 authority |
| `MAX_RUNTIME_BUDGET_ATTRIBUTION_STATES_PER_SERVER` | 192 | 192 | 2 | No; derived effective limit | Checked effective active server instances plus root admissions | `ATTRIBUTION_STATE_CAPACITY_INVARIANT` only if an impossible invariant fails; derived in snapshot; preserved |
| `MAX_BUDGET_DEFERRED_EVENTS` | 4096 | 4096 | 4096 | No; fixed internal capacity | Preallocated event-reference array; usable references `<=` effective server pending | `DEFERRED_BUFFER_OVERFLOW` faults boundedly; physical storage grants no product work; preserved |
| `MAX_BREAKER_CLEANUP_SCRATCH_EVENTS` | 4096 | 4096 | 4096 | No; fixed internal capacity | Preallocated event-reference array; usable references `<=` effective server pending | `BREAKER_SCRATCH_OVERFLOW` faults boundedly; physical storage grants no product work; preserved |
| `MAX_PERSISTENT_SCHEDULES_PER_SERVER` | 0 | 0 | 0 | No; zero invariant | Persistence validation before publication; P5 accepts memory-only | `PersistentScheduleUnsupported`; zero identity, sequence, pin, pending, or event publication; new in R2 |
| `RUNTIME_SCHEDULE_PERSISTENCE_VARIANTS` | 2 | 2 | 2 | No; fixed schema cardinality | Exactly `MEMORY_ONLY` and `PERSISTENT`; no numeric ceiling symbol | Null rejected by constructor; representation only; new in R2 |
| `P5_ACCEPTED_PERSISTENCE_VARIANTS` | 1 | 1 | 1 | No; fixed schema cardinality | Exactly the `MEMORY_ONLY` subset; no numeric ceiling symbol | `PERSISTENT` is typed unsupported with no downgrade; new in R2 |
| `CANCELLATION_OWNER_TOKENS_PER_EVENT` | 1 | 1 | 1 | No; fixed field cardinality | Event token server and instance must equal event ownership | `CancellationTokenInvalid` for current-slot owner mismatch; immutable field invariant; new in R2 |

All configurable product ceilings are downward-only SERVER values. Fixed arrays/rings support the
hard maximum but never grant work above the effective snapshot. The complete minimum valid tuple
is pending instance/attribution/server `1/1/1`, active attribution/server `1/1`, roots `1`,
execution instance/attribution/server `1/1/2`, lifetime `1`, depth/direct/zero-delay `0/0/0`,
delay/deadline `0/0`, and cancellations `1`. Derived minima are descendants 0, leases 1, and
attribution states 2; fixed physical minima remain 32, 256, 2, 4,096, and 4,096 as shown.

## 30. Queue Capacity and Tick Budget

The server slot has at most effective server pending work and never more than 4,096 committed plus
firm-reserved units. Per-instance and per-attribution committed/reserved counts are simultaneously
enforced. One current event remains pending while in flight, so its unit cannot be recycled for
children. Reentrant root admission sees every firm reservation.

Immediately before a P6 call, P5 computes the immutable call-scoped budget:

```java
record RuntimeChildSpec(
        int nodeIndex,
        int delayTicks,
        int deadlineHorizonTicks,
        RuntimeOrigin origin,
        Optional<RuntimeTarget> target,
        ChildTriggerCause triggerCause,
        RuntimeExecutionData executionData) {}

record RuntimeChildPlan(List<RuntimeChildSpec> children) {
    RuntimeChildPlan {
        java.util.Objects.requireNonNull(children, "children");
        if (children.size() > MagicSafetyCeilings.MAX_DIRECT_CHILDREN_PER_EVENT) {
            throw new RuntimeKernelException(
                    RuntimeKernelException.Code.CHILD_PLAN_HARD_CAPACITY_EXCEEDED);
        }
        children = List.copyOf(children);
    }
}

record RuntimeExecutionBudget(
        int directChildCapacity,
        int zeroDelayChildCapacity,
        int remainingLineageEvents,
        int remainingDepth,
        int maximumDelayTicks,
        int maximumDeadlineHorizonTicks,
        int remainingSkillInstancePending,
        int remainingAttributionPending,
        int remainingServerPending) {}
```

`RuntimeChildSpec` rejects null reference fields but deliberately accepts every `int`, allowing P5
to select the exact typed range or schedule outcome for negative and excessive values.
`RuntimeExecutionBudget` rejects every negative capacity. It is an immutable call-scoped lower
bound on what P5 can admit, not a second scheduler counter owner.

Direct capacity is the minimum of effective direct children, remaining lifetime, each pending
headroom, remaining depth feasibility, and EventId headroom. Zero-delay capacity is a separate
minimum with the effective zero-delay sub-cap. Remaining depth is not sibling capacity. EventId
range and firm pending capacity are reserved before port entry; unused IDs are permanent holes.

`RuntimeChildPlan` rejects a raw list larger than hard 32 before `List.copyOf`. A copied list of at
most 32 that exceeds a lower effective direct limit is an ordinary typed whole-plan rejection.
Accepted `A` children convert `A` reserved units to committed pending and release unused
reservations. No prefix can publish.

## 31. Admission and Scheduler Results

The final admission family and accounting are closed as follows. Every name is nested under the
package-private `RuntimeAdmissionResult` owner unless another owner is stated.

| Coordinate | Exact result | Publication and accounting |
|---|---|---|
| Valid ordinary memory-only root | `AcceptedMemoryOnly(RuntimeEventToken, RuntimeCancellationToken)` | One event, one instance, one instance sequence, one EventId, one exact lease, and applicable pending counts |
| Explicit persistent root | `PersistentScheduleUnsupported` | Root-attempt diagnostic only; all publication counts zero |
| Delay outside effective range | `DelayOutOfRange(requestedDelayTicks, maximumDelayTicks)` | Zero identity, sequence, lease, pending, or event publication |
| Scheduled-tick checked overflow | `DelayOverflow` | Zero publication |
| Deadline horizon outside effective range | `DeadlineOutOfRange(requestedHorizonTicks, maximumHorizonTicks)` | Zero publication |
| Deadline checked overflow | `DeadlineOverflow` | Zero publication |
| Scheduled tick later than derived deadline | `DeadlineBeforeScheduledTick(scheduledRuntimeTick, deadlineRuntimeTick)` | Zero publication |
| Root generic reference has wrong server or other generic failure | `InvalidRuntimeReference(RuntimeReferenceFailureReason)` | Execution/port `0/0`; zero publication |
| Exact revision unavailable at root | `SkillRevisionUnavailable(reason)` | Zero publication; provisional lease closed |
| Invalid node/cause/reference/data/attribution shape | `InvalidEvent(reason)` | Zero publication |
| Instance-bound owner absent or terminal | `OwnerInstanceUnavailable` | Zero publication |
| Active server instance capacity reached | `ActiveLineageCapacityExceeded(current, maximum)` | Typed rejection; no breaker or pin leak |
| Active attribution instance capacity reached | `ActiveBudgetAttributionCapacityExceeded(current, maximum)` | Typed rejection; no breaker |
| Root-attempt budget reached | `RootAdmissionBudgetExceeded(maximum)` | No later schedule/definition work |
| Prospective attribution/server pending overflow | `CircuitBroken(RuntimeCircuitBreakerSummary)` | Source candidate terminal; no event/instance/sequence/pin/pending publication |
| No active exact slot | `ServerNotRunning` | Zero mutation |
| STOPPING or non-live server | `ServerStopping` | Zero new publication; bounded cleanup as applicable |
| Wrong logic thread | `WrongThread` | Zero mutation |
| Event or instance sequence unavailable | `SequenceExhausted(EVENT_SEQUENCE)` or `SequenceExhausted(SKILL_INSTANCE_SEQUENCE)` | No wrap/reuse; accepted work may drain |
| Root base tick cannot be represented | `TickExhausted` | Zero publication; final current-tick work may remain drainable |
| Slot already faulted | `KernelFaulted` | Admission closed until Stopped removal |

Total root precedence is fixed:

```text
programming shape/null
-> thread/slot/liveness
-> RootAdmissionBudgetExceeded
-> PersistentScheduleUnsupported
-> DelayOutOfRange
-> DeadlineOutOfRange
-> checked currentTick+1 base (TickExhausted on failure)
-> checked base+delay (DelayOverflow)
-> checked base+horizon (DeadlineOverflow)
-> DeadlineBeforeScheduledTick
-> reference/cause/data/attribution/server-token validation
-> active server/attribution, lease, SkillInstance sequence and EventId headroom
-> attribution then server pending-breaker preflight
-> exact definition/project/provisional pin/node validation
-> atomic zero-partial publication
```

A prospective pending break therefore occurs before provisional pin acquisition. Simultaneous
persistence and schedule errors choose persistence; delay range precedes deadline range; if both
additions overflow, delay overflow wins. Root-base overflow leaves accepted final-tick work intact
until a later failed Post advance enters EXHAUSTED and clears.

Scheduler-control results are separate from event admission:

```java
enum RuntimeSequenceKind { EVENT_SEQUENCE, SKILL_INSTANCE_SEQUENCE }
enum RuntimeDrainStopReason { SERVER_EXECUTION_LIMIT_REACHED }
enum RuntimeTickAdvanceResult { ADVANCED, EXHAUSTED }
```

At zero EventId headroom, P6 is still called with direct capacity zero when every earlier check
passes. An empty plan completes normally; a nonempty plan becomes
`BudgetRejected(EVENT_SEQUENCE_CAPACITY_EXCEEDED)`. A simultaneous pending deficit selects its
pending circuit breaker first.

## 32. Cancellation Outcomes

The exact closed `RuntimeCancellationResult` variants are:

| Variant | Meaning and mutation |
|---|---|
| `CancelledEvent` | Remove one exact queued/deferred event, decrement pending, and release empty state |
| `CancelledSkillInstance(int removedCount)` | Remove all queued/deferred work when no frame is in flight; close final lease |
| `CancellationRequested(int removedCount)` | Remove queued/deferred descendants, retain only current frame, suppress its children |
| `InFlight` | Exact event is current; no preemption, rollback, or mutation |
| `AlreadyCancelled` | Whole-instance cancellation is already requested; idempotent |
| `NotPending` | Executed, expired, cancelled, broken, unknown, or old removed handle; no history created |
| `WrongServer` | Stale/wrong slot token while a replacement RUNNING slot exists; zero mutation |
| `WrongThread` | Not on the server logic thread; zero mutation |
| `ServerNotRunning` | No active exact slot; zero mutation |
| `ServerStopping` | STOPPING/non-live slot; cleanup only |
| `CancellationBudgetExceeded(int maximum)` | More than effective per-tick attempts; no queue scan or mutation |
| `CancellationTokenInvalid(RuntimeCancellationTokenInvalidReason reason)` | Current-slot event owner mismatch; exact reason `EVENT_OWNER_MISMATCH` |

Cancellation-attempt accounting is independent of execution. The exact maximum is accepted and
attempt maximum plus one rejects. Event cancellation uses its index plus bounded queue/deferred
removal; whole-instance cancellation performs the bounded rebuild. No cancellation operation
creates per-token history or changes ordering keys.

## 33. Execution and Port Outcomes

The structural rejection enums are:

```java
enum RuntimeScheduleRejectionReason {
    DELAY_OUT_OF_RANGE,
    DELAY_OVERFLOW,
    DEADLINE_OUT_OF_RANGE,
    DEADLINE_OVERFLOW,
    DEADLINE_BEFORE_SCHEDULED_TICK
}

enum RuntimeBudgetRejectionReason {
    LINEAGE_EVENT_LIMIT_EXCEEDED,
    DEPTH_LIMIT_EXCEEDED,
    DIRECT_CHILD_LIMIT_EXCEEDED,
    ZERO_DELAY_CHILD_LIMIT_EXCEEDED,
    EVENT_SEQUENCE_CAPACITY_EXCEEDED
}
```

The final `RuntimeExecutionOutcome` cases are:

| Outcome | Execution / port | Event and child action |
|---|---:|---|
| `Completed` | 1 / 1 | Current terminal; zero children; remove empty instance/lease |
| `CompletedWithChildren(count)` | 1 / 1 | Current terminal; exactly all conforming children atomically publish |
| `RejectedByExecutionPort(PORT_UNAVAILABLE)` | 1 / 1 | Current terminal; empty plan; current production path |
| `RejectedByExecutionPort(EFFECT_SPECIFIC_SOURCE_REJECTED)` | 1 / 1 | Current terminal; zero children |
| `RejectedByExecutionPort(EFFECT_SPECIFIC_TARGET_REJECTED)` | 1 / 1 | Current terminal; zero children |
| `SkillRevisionUnavailable` | 1 / 0 | Defensive exact-revision loss; fail-closed terminal; no fallback |
| `SourceMissing(RuntimeReferenceFailureReason)` | 1 / 0 | P5 generic source failure; terminal; no force-load |
| `TargetMissing(RuntimeReferenceFailureReason)` | 1 / 0 | P5 generic target failure; terminal; no force-load |
| `InvalidRuntimeReference(RuntimeReferenceFailureReason)` | 1 / 0 | Wrong server/dimension/type or unavailable dimension; terminal |
| `DeadlineExpired(deadlineRuntimeTick, observedRuntimeTick)` | 0 / 0 | Remove only event; bounded expiry diagnostics; no breaker |
| `Cancelled` | 0 / 0 before claim, otherwise already-started count retained | Suppress children; terminalize according to cancellation state |
| `OwnerInstanceUnavailable` | 0 / 0 | Remove defensive stale owner work; no breaker |
| `ServerStopping` | 0 / 0 before claim, otherwise already-started count retained | Suppress children and boundedly stop-clear |
| `BudgetRejected(RuntimeBudgetRejectionReason)` | 1 / 1 for returned plan | Reject whole plan; zero children; current terminal |
| `ScheduleRejected(RuntimeScheduleRejectionReason)` | 1 / 1 | Reject whole plan; unused reserved IDs remain holes |
| `CircuitBroken(RuntimeCircuitBreakerSummary)` | 1 / 1 for returned plan | Break source instance, remove same-owner descendants, zero children |

After the port returns, total child precedence is fixed:

```text
reentrant stopping/cancellation
-> outcome/plan/null pairing programming invariant
-> direct count
-> zero-delay count
-> every child origin/target stable token equals the current RuntimeServerToken
-> node/capability/depth
-> delay range
-> deadline-horizon range
-> checked scheduled addition
-> checked requested-deadline addition
-> scheduled/deadline invariant
-> lifetime
-> instance/attribution/server pending breaker
-> returned count versus reserved EventId capacity
-> zero-or-all publication
```

Stopping or cancellation suppresses a simultaneously invalid plan without inventing a rejection or
breaker. Lifetime wins over pending projection; pending breaker precedence is instance,
attribution, server. EventId capacity is selected only when it is the sole remaining deficit. A
wrong stable server token faults with `INVALID_CHILD_PLAN_INVARIANT` before schedule/pending
validation, retains execution/port `1/1`, preserves every reserved ID as a hole, and publishes no
prefix.

Only `Completed` may pair with a plan, and only a conforming successful completion may publish
children. A null batch, an invalid outcome/plan pairing, a raw child list above hard capacity, or a
bounded child carrying a wrong server-slot token is a fixed programming failure rather than a
typed gameplay outcome.

## 34. RuntimeException / Error / OOME

The fixed package-private kernel programming-failure codes are exactly:

```java
enum Code {
    DUPLICATE_SERVER_INSTALL,
    SECOND_ACTIVE_SERVER,
    STOPPED_SERVER_INSTALL,
    TICK_BEFORE_INSTALL,
    WRONG_THREAD_LIFECYCLE,
    WRONG_THREAD_DRAIN,
    WRONG_THREAD_CHILD_ADMISSION,
    NESTED_DRAIN,
    QUEUED_EVENT_IDENTITY_INVARIANT,
    EVENT_INDEX_INVARIANT,
    RESERVATION_ACCOUNTING_INVARIANT,
    LEASE_ACCOUNTING_INVARIANT,
    ATTRIBUTION_STATE_CAPACITY_INVARIANT,
    DEFERRED_BUFFER_OVERFLOW,
    BREAKER_SCRATCH_OVERFLOW,
    CHILD_PLAN_HARD_CAPACITY_EXCEEDED,
    NULL_EXECUTION_BATCH,
    INVALID_OUTCOME_PLAN_PAIRING,
    INVALID_CHILD_PLAN_INVARIANT,
    SERVER_SLOT_TOKEN_EXHAUSTED
}
```

`RuntimeKernelException` retains only the non-null fixed code and uses its name as the message.
Constructor null misuse, strict-config programming failures, typed configuration/schedule/gameplay
results, and runtime-kernel fixed codes remain separate categories.

An unexpected `RuntimeException` after claim marks the slot FAULTED, preserves all started
counters, terminalizes current primitive accounting, performs bounded normal queue/deferred/index/
state cleanup and lease close, and rethrows the identical primary object. A secondary cleanup
failure is ignored only to avoid masking that primary object; it is not attached or retained.

If the primary is thrown by generic P5 resolution, execution/port remains `1/0`. If it is thrown
after entry to the P6 port, execution/port remains `1/1`. No exception path changes these observed
cardinalities.

`Error`, including OOME, performs no-intentional-allocation best-effort primitive terminalization
and queue/deferred/index clearing, may retain only the already bounded instance/lease graph until
Stopped or process exit, and rethrows the identical object. There is no catch-and-continue, wrap,
retry, counter rollback, log allocation, suppression attachment, or `Throwable` retention. The
service never catches `Throwable` as a general policy.

## 35. Completion and Cleanup

Every ordinary terminal decrements the current event's committed pending and in-flight counts.
Successful children are published atomically before unused reservations release. Typed rejection,
generic resolution failure, deadline expiry, cancellation, owner loss, and unavailable port emit
no children. When an instance has zero pending and zero in-flight work, it is removed, its
attribution reference is decremented, and its shared revision lease reference is released exactly
once. A zero-reference attribution state remains only through the same tick and is purged next
tick.

Stopping rejects new work, suppresses returned children, clears primary queue, deferred and scratch
arrays, indexes, reservations, instances, attribution states, diagnostics, and normal-path leases.
Stopped repeats defensive clear, closes any Error-path leases, removes the exact slot, and discards
all remaining runtime references. There is no completed event, completed instance, token, lag, or
config history.

## 36. Retention Model

The bounded retained graph is:

| Owner | Retained field/value | Maximum and lifetime | Cleanup rule |
|---|---|---|---|
| `Gramarye` | One `SkillRuntimeService` and one `P5ServerRuntimeConfig` | One each; mod composition lifetime | Direct Started bridge only; neither becomes a runtime-limit provider edge |
| Game event bus | Bound `Gramarye.handleP5RuntimeStarted` | Exactly one registration; bus lifetime | Platform bus disposal; bridge reaches the two composition fields only |
| Mod event bus | Bound Reloading and Unloading config handlers | Exactly two registrations; bus lifetime | Platform bus disposal; exact-config filters; no service edge |
| Platform config tracker / `ModConfig` | Exact `P5RawServerConfigSpec` | One registered spec; platform config lifetime | Platform disposal; reaches only bounded raw-spec/load-state intake |
| Config owner | Raw spec plus `AtomicReference<P5RuntimeLimitLoadState>` | One spec and one complete next-slot state | Atomic replace; initialize/unload to `Unavailable`; no history/world/server edge |
| Config owner | `AtomicReference<Optional<P5RuntimeReloadDisposition>>` | One empty-or-enum cell | Reload replace; unload clears; accessor performs one read |
| `SkillRuntimeService` | Store service, one policy provider, projector, nominal port | One each; mod composition lifetime | Object disposal; no carrier snapshot or gameplay cache |
| `SkillRuntimeService` | Exact server identity to `ServerSlot` | One active entry, Started through Stopped | Exact identity removal at Stopped |
| `SkillRuntimeService` | Next server-slot token high-water | One checked primitive; service lifetime | Never reset, wrap, or reuse |
| `ServerSlot` | One immutable `P5RuntimeLimits` | One per slot | Drop at exact slot removal |
| `ServerSlot` | Priority queue and event index | At most effective pending, never above 4,096 | Poll/remove/clear; no tombstones |
| `ServerSlot` | Runtime instance states and revision leases | At most effective active server instances, never above 128 | Last event/cancel/break/stop releases lease |
| `ServerSlot` | Attribution states | Derived effective capacity, never above 192 | Zero-ref next-tick purge or stop |
| `ServerSlot` | Deferred event array | 4,096 physical references; one drain | Reoffer and null every exit; clear on fault/stop |
| `ServerSlot` | Breaker cleanup scratch | 4,096 physical references; one cleanup | Rebuild and null; clear on fault/stop |
| `ServerSlot` | Breaker ring and offender slots | 256 records and 2 current summaries | Oldest overwrite; tick reset/stop clear |
| `ServerSlot` | Current event and firm reservation primitives | One event and at most 32 child units; one synchronous call | Finally release; unused EventIds remain holes |
| `RuntimeEvent` | IDs, exact reference, ticks, schedule/owner/attribution, stable origin/target/cause/data | Fixed immutable fields; queued/deferred/in-flight | Terminal/cancel/break/stop |
| `RuntimeRevisionLease` | One `ControlledSkillPin` and one immutable projection | At most 128 shared exact references | Last-instance close or stop; Error may defer to Stopped |
| `RuntimeExecutionContext` | Resolved server/level/player/entity/block context and immutable node/budget | One call-scoped graph | Drop in `finally`; port must not retain |
| `RuntimeChildPlan` | Immutable child specs | At most 32 physical entries; one port return | Publish all or drop all |
| Caller | Cancellation/event handle | Value-only IDs; service retains zero caller handles | No cleanup or history required |

Forbidden queued or diagnostic retention is exactly zero for server/level/player/entity live
objects, NeoForge events, Store/definition objects, tags/NBT, arbitrary collections or payloads,
callbacks/functions, raw config/config providers, persistent journals, completed/deferred event
history, and `Throwable`. The hard graph is bounded by 4,096 events, 128 instance/lease/projection
roots, 192 attribution states, two 4,096-reference arrays, 256 breaker records, two offender slots,
and one 32-child plan. This is a fixed-count model, not an exact byte-size theorem.

## 37. P6 Nominal Execution Boundary

The exact nominal seam is package-private:

```java
interface RuntimeExecutionPort {
    RuntimeExecutionBatch execute(RuntimeEvent event, RuntimeExecutionContext context);
}

record RuntimeExecutionBatch(
        RuntimePortOutcome outcome,
        RuntimeChildPlan children) {}
```

The package-private `RuntimeReferenceResolver` produces one call-scoped
`ResolvedRuntimeReferenceContext`, which is carried only by `RuntimeExecutionContext`. P6 receives
the immutable current event and a
call-scoped context containing the exact server, exact immutable definition/node, current runtime
tick, slot token, resolved generic reference context, and firm execution budget. It exposes no
runtime owner, queue, enqueue/cancel method, generic callback, `Object` payload, or background
primitive, and it may not retain the context.

P5 performs generic exact-revision and loaded-only source/target resolution before entry. P6 owns
effect-specific capability, range, visibility, protection, gameplay validity, and the later
Request -> Resolve -> CommitPlan -> ordered Commit path. P6 may return only a closed typed outcome
and bounded child plan. It cannot mutate the queue directly, recursively execute a child, change
instance identity or attribution, choose persistence, extend a deadline, or bypass counters.

Current production P5 installs the typed unavailable value
`UnavailableRuntimeExecutionPort.INSTANCE`. It returns `PORT_UNAVAILABLE` with an empty plan. It
does not mutate world or mana and cannot return false success. The real P6 adapter and gameplay
pipeline remain unimplemented.

## 38. P4 / P7 / P8 Boundaries

P4-E completes its Starting install, bootstrap, audit, and possible controlled reclaim before P5
Started admission opens. P5 uses only existing controlled exact find/pin and performs zero P4 root
audit, reclaim, persistence mutation, startup-root addition, or playerdata read/write. The P5
transient queue is not `RuntimePersistentStore`, a carrier, or a P4 startup root.

P7 authenticates client intent, sequence/replay and payload shape, cooldown/ownership/target
preconditions, and request-rate policy, then supplies a trusted root request and authenticated
attribution. It cannot reset or bypass P5 counters, choose comparator keys, forge instance IDs,
replace attribution, or alter the slot limit snapshot, deadline, or persistence.

P8 receives presentation work only after a successful P6 gameplay outcome. The P5 runtime queue is
not the presentation queue. P5 performs no packet, particle, sound, HUD, world, mana, or other
presentation/gameplay mutation.

## 39. Implementation Plan

The later P5 implementation is one semantic work unit; this plan is not an exact path-count
authority. It must:

- compose one `P5ServerRuntimeConfig` and one `SkillRuntimeService` in `Gramarye`, register the
  strict SERVER config, and use the sole Started snapshot bridge;
- implement the package-private owner, Post/Stopping/Stopped callbacks, checked slot-token
  allocator, one per-server slot, root/cancel seams, and exact state/liveness/thread guards;
- implement immutable event/schedule/stable-reference/identity/attribution/cancellation/result
  values, the five-key priority queue, child canonicalization, and checked tick/sequence arithmetic;
- implement exact revision projection/leases through existing Store and registry seams without a
  second registry, cache, persistence truth, or public runtime facade;
- implement immutable `P5RuntimeLimits`, strict raw config/load/failure/reload vocabulary,
  range/relation/derivation order, and one slot-bound snapshot;
- implement instance/attribution/server pending reservations and execution counters, the
  no-peek server boundary, bounded eligibility filter, breaker cleanup, expiry, cancellation, and
  bounded diagnostics;
- implement the loaded-only generic resolver and call-scoped resolved context;
- implement the nominal typed unavailable P6 port, hard-bounded child-plan construction, typed
  outcomes, and fixed exception identity/cleanup semantics;
- add the canonical hard ceilings once and add focused ordinary JUnit, static/source/API, lifecycle,
  retention, and one pure-Java hard-limit workload;
- narrowly synchronize an existing static test only if P5 makes it directly stale, without
  weakening P3/P4-local assertions.

There is no P4 Store/lifecycle/persistence delta; no registry descriptor or `ActionType.execute`;
no `RuntimePersistentStore`, Schedule persistence, world/playerdata schema, Trigger/Action product,
P6 gameplay pipeline, P8 presentation, GameTest holder, custom source set, script, Gradle task,
workflow job, heap setting, timeout, cache/output change, or public top-level runtime API in P5.
Directly related stale tests may be repaired within the semantic phase without creating a scope
amendment.

## 40. Unit and Hard-Limit Workload Plan

The implementation phase must preserve every non-superseded original test coordinate, all 42 R1
focused coordinates, and all 40 R2 focused coordinates. The union is not asserted to be 82 because
R2 replaces expectations at overlapping coordinates.

```text
R1 focused cases = 42
R2 focused cases = 40
```

The 30 original coordinates, interpreted through R1 and R2, are:

```text
O01  Root acceptance, first EventId/instance sequence 1, first P5-tick execution.
O02  Wrong server object/token and wrong thread: typed result, zero mutation.
O03  Same-tick roots follow acceptance/EventId order, independent of map order.
O04  Child canonical order delay/node/original ordinal and deterministic IDs.
O05  Delay-zero children queue and run later in the same drain; recursion zero.
O06  Root delay zero next cutoff; child delay one and bounded delay exact ticks.
O07  Server attempt 512 passes; equality then stops before observing another event.
O08  Server pending 4096 passes; projected 4097 circuit-breaks only source instance.
O09  Active server instances 128 pass; next root rejects and closes provisional pin.
O10  Lifetime 512 passes; excess child plan is whole-plan lifetime BudgetRejected.
O11  Depth 0 through 32 passes; excess is whole-plan depth BudgetRejected.
O12  Direct 32 and zero-delay 16 pass; lowered-effective excess typed; raw 33 fixed fault.
O13  Delay/horizon 12000 pass; ranges and independent checked overflows are exact.
O14  EventId final value, reservation holes, exhaustion, no wrap or saturation.
O15  Instance sequence and service slot-token final value, exhaustion, restart isolation.
O16  Cancel one event, whole instance, and current frame; eager exact accounting.
O17  Executed/cancelled/expired/unknown handles stay bounded and history-free.
O18  Stopping/non-live server rejects admission and dispatch.
O19  Stopping clears; Stopped defensively clears/removes; double stop idempotent.
O20  Event-retention reflection excludes every forbidden live/config/payload type.
O21  Injected RuntimeException preserves counters, boundedly cleans, assertSame rethrows.
O22  Preallocated Error/OOME best-effort cleanup, assertSame, Stopped lease release.
O23  Nested drain is a fixed failure; returned child still enters queue only.
O24  Randomized lookup-map insertion never changes the five-key trace.
O25  Port surface has no owner/enqueue/callback/Object/background primitive.
O26  Exact revision R survives later/latest R+1 and releases its pin at completion.
O27  Projection/registry or pin unavailability is typed; no revision fallback.
O28  P5 loaded-only reference results cover missing/wrong dimension/server/type/unloaded.
O29  Firm child reservation survives reentrant root admission; all-or-none and ID holes.
O30  Integrated pause without Post advances neither runtime tick nor delay.
```

The 42 R1 coordinates remain, with R2's corrected expectation at overlaps:

```text
R1-01  Instance attempts 1..64 start; 65th defers unchanged until reset.
R1-02  Player attempts 1..128 start; later same-player work defers while others run.
R1-03  SERVER_AUTOMATION shares one 128-attempt attribution domain.
R1-04  Attempt 512 starts; next loop equality performs the R2 no-peek stop.
R1-05  Observed precedence is instance then attribution while global capacity is positive.
R1-06  Four grouped player attributions can each run 128; no round-robin or borrowing.
R1-07  More owners retain deterministic bounded waiting under 1024/4096 pending caps.
R1-08  Multiple player instances share attribution count and keep separate instance counts.
R1-09  Same exact revision under different players has independent IDs/sequences/budgets.
R1-10  Single-player throughput is exactly 128 and allows two 64-attempt cohorts.
R1-11  Root mints one ID/sequence; duplicate live UUID rejects; exhaustion publishes zero.
R1-12  New work after cancel/break gets fresh identity/sequence and may reuse exact lease.
R1-13  All child forms inherit identity, sequence, exact revision, attribution, persistence.
R1-14  Player-owned entity/block/server-origin descendants retain player attribution.
R1-15  Offline/missing/dimension-changed player still consumes attribution before resolution.
R1-16  Attribution/source mismatch is typed; root wrong-server is InvalidRuntimeReference.
R1-17  Same-tick zero-ref attribution cannot reset budget; next-tick purge is bounded.
R1-18  Instance pending 256 passes; projected 257 selects instance reason.
R1-19  Player pending 1024 passes; projected 1025 breaks only source instance.
R1-20  Non-player pending 1024 uses its distinct reason and source-only cleanup.
R1-21  Server pending 4096 passes; projected 4097 never evicts unrelated work.
R1-22  Structural rejection precedes pending; pending precedence is instance/attribution/server.
R1-23  Root pending break publishes no event/instance/sequence/lease/pin/pending.
R1-24  Child break suppresses whole plan, releases reservation, preserves ID holes/current frame.
R1-25  Break cleanup covers primary and deferred buffers and preserves survivor order/counts.
R1-26  Cleanup bound is 4096 membership checks and no active instance trips twice.
R1-27  Cancellation/Stopping first wins without breaker record; old handle becomes NotPending.
R1-28  Each due event defers at most once; scratch reinserts/nulls on every exit.
R1-29  Deferred keys remain exact and lag is aggregate-only.
R1-30  Reentrant same-owner root sees counters and every firm pending reservation.
R1-31  Generic P5 terminal cardinality is 1/0; P6 rejection/success is 1/1.
R1-32  Cancel/invalid structure/wrong thread/pre-claim stop/deferral count zero.
R1-33  Reentrant stop/cancel after count suppresses children without rollback.
R1-34  RuntimeException preserves three counts, cleans FAULTED, assertSame rethrows.
R1-35  Error/OOME preserves counts/identity and Stopped releases bounded retained leases.
R1-36  Breaker cleanup failure never returns false CircuitBroken or retains Throwable.
R1-37  Ring stays 256 with exact reasons and absent root sequence/EventId fields.
R1-38  Four reason totals saturate; execution identity/sequences never saturate.
R1-39  Top ties use smallest instance/contributing sequence, never map or UUID order.
R1-40  Stop clears queue, arrays, maps, counters, diagnostics, reservations, and leases.
R1-41  Reflection excludes forbidden live/config/payload/Throwable retention everywhere.
R1-42  Config uses R2 exact maxima, legal minimum tuple, and fail-closed relations.
```

The 40 R2 coordinates are:

```text
R2-01  scheduled < deadline and current inside interval executes with exact lag.
R2-02  scheduled == deadline == current executes; equality is not expiry.
R2-03  overdue scheduled < current <= deadline executes with unchanged five keys.
R2-04  current == deadline+1 expires only event with execution/port/breaker 0/0/0.
R2-05  Tier deferral at deadline records lag; next observation expires; global stop fabricates none.
R2-06  Child deadline is min(parent deadline, checked current+horizon) for all delays.
R2-07  Child cannot extend parent; scheduled-after-derived-deadline rejects whole plan.
R2-08  Delay and deadline last-value/overflow results remain distinct and checked.
R2-09  12000 and zero effective delay/horizon boundary tuples behave exactly.
R2-10  Explicit MEMORY_ONLY is accepted and stored on immutable event.
R2-11  Explicit PERSISTENT is typed unsupported with no downgrade or write.
R2-12  Persistent rejection debits root attempt but publishes no runtime identity/state.
R2-13  Child has no persistence setter and inherits; fabricated pairing is a fixed fault.
R2-14  Root revision miss is 0/0; defensive post-claim loss is 1/0; no fallback.
R2-15  Missing player/entity/block source yields exact P5 reason at 1/0, no force-load.
R2-16  Equivalent target failures are 1/0; absent optional target reaches P6.
R2-17  Wrong dimension and unavailable dimension produce their exact distinct reasons.
R2-18  Root wrong-server 0/0; post-claim loss 1/0; stale handle semantics exact.
R2-19  Full result matrix asserts P5 1/0, P6 1/1, and pre-claim 0/0 cardinalities.
R2-20  P6 effect-specific source/target rejection is 1/1 and retains no context.
R2-21  Server count limit-1 allows the due eligible event to become exact limit.
R2-22  Next loop equality records one aggregate and stops.
R2-23  Equality does no peek/poll/owner/deadline/EventId/event decision/extra attempt.
R2-24  Global diagnostics carry no head attribution, breaker, or offender.
R2-25  Same-tick repeated stop increments once; empty-queue equality proves only capacity used.
R2-26  Next checked tick resets and resumes unchanged exact queue order.
R2-27  Absent target/config-copy path creates exact 16 defaults; copied values are raw input.
R2-28  Every configurable field accepts exact hard maximum in a valid tuple.
R2-29  Every configurable field accepts the exact legal combined minimum tuple.
R2-30  Every above-hard value yields ABOVE_HARD_MAXIMUM and no RUNNING slot/clamp.
R2-31  Every below-minimum value rejects; only specified zero minima accept zero.
R2-32  Each of 12 relations independently yields deterministic RELATION_VIOLATION.
R2-33  Unavailable/Invalid/multi-error config fails deterministically before slot/token.
R2-34  Snapshot reflection proves immutable values, one slot ref, zero event refs.
R2-35  Root/child/same-tick/delayed/deferred paths read the identical snapshot.
R2-36  Reload/unload atomically update next-slot state/disposition only; unrelated events no-op.
R2-37  Reload below active counts leaves current slot unchanged; next slot uses full tuple.
R2-38  Physical arrays/ring retain hard size under lowered product ceilings.
R2-39  Per-save/global selected config governs only the next slot; offline work remains zero.
R2-40  Reflection excludes config/provider/live-world/Throwable/journal/history retention.
```

Additional exact edge checks cover root and Post tick exhaustion at `Long.MAX_VALUE`, schedule
precedence, EventId-only zero capacity, pending-over-EventId precedence, hard-plan check before
copy, wrong child stable token, exact strict-config registration and nonempty correction failures,
FML syntax-failure qualifications, deadline/global saturation and reset, deferral lag updates,
attribution contributor tie state, immutable creation tick, one Started bridge with no config
provider retention, and package-private top-level API.

One ordinary pure-Java workload runs with defaults and one valid lower snapshot. It uses at least
four player attributions plus `SERVER_AUTOMATION`, reaches 128 active instances and 4,096 live
events, exercises delays 0/1/12,000, execution boundaries 64/128/512, all pending breaker tiers,
inclusive expiry, persistence rejection, loaded-only reference failures, at most 128 live-instance
cleanup scans, cancellation, and complete drain. Final queue, deferred/scratch cells, index,
instances, attributions, leases, and reservations are all zero. It uses named deterministic fakes,
no actual world/playerdata, wall clock, heap measurement, benchmark, worker, GameTest, or fixed heap.

## 41. Gradle and CI Plan

Focused tests, static/source/API gates, and the pure-Java workload run under the existing ordinary
JUnit `test` task and default heap. The minimum local qualification command is:

```text
./gradlew verifyPlatformBaseline compileJava test --console=plain
```

P5 adds zero GameTest holders, custom source sets, scripts, Gradle tasks, fixed-heap/stress tasks,
heap/timeout/cache/output policies, workflow steps/jobs, or matrices. Standard results remain under
`build/test-results/test` and `build/reports/tests/test`. The existing Build workflow remains the
only CI topology and retains exactly these six jobs:

| Job | Dependency order |
|---|---|
| `build` | root |
| `P4-A3 memory gates` | after `build` |
| `P4-B memory gates` | after prior chain |
| `P4-C memory gates` | after prior chain |
| `P4-D memory gates` | after prior chain |
| `P4-E memory gates` | after prior chain |

The existing build job continues to run its already-governed integration commands. This record
does not add or claim a P5 runtime job, runtime Gate, or implemented-test pass.

## 42. Split Verdict

```text
P5 = NO SPLIT
```

Ownership, schedule arithmetic, generic resolution, identity, leases, immutable effective limits,
pending reservations, execution counters, eligibility filtering, global boundary, circuit breaker,
queue/index/arrays, cancellation, diagnostics, and cleanup share one `ServerSlot` invariant set.
Splitting them would create multiple counter/queue/lifecycle truths. The authorized flow is one
consolidated record, one implementation semantic phase, direct bounded unit/hard-limit tests, and
the existing CI topology.

## 43. Anti-Recursion Rule

Runtime recursion is prohibited: children return only as immutable bounded data, enter the same
priority queue, and execute only through a later drain iteration. Nested drain is a fixed failure;
the port has no queue access. Architecture/process recursion is also prohibited: this record does
not spawn numeric qualification, benchmark authority, a verifier branch, an implementation
path-count amendment, a third-level design split, or another closure ledger.

## 44. Known Limitations

- Production P5 root callers are zero.
- Actual Trigger and Action product entries are zero.
- The nominal P6 port is typed unavailable; real gameplay execution is absent.
- Deterministic bounded fairness is not round-robin equality.
- All initial non-player automation shares `SERVER_AUTOMATION` and may contend.
- The 256-record breaker ring is bounded observability, not complete history.
- Runtime events and delays are memory-only; persistent schedules are typed unsupported.
- The future persistent schedule store/data product remains open.
- No gameplay continuation carrier or existing-instance continuation seam exists.
- Current production has no P5 SERVER config; the strict owner/snapshot model is implementation
  scope, not current code.
- Generic missing/unloaded entity distinctions are limited to the closed R2 loaded-only model and
  never force-load.
- Active-slot hot reload is unsupported; reload applies only to a later slot.
- New public top-level P5 runtime API is zero.
- The firm execution budget exposes bounded capacities, not a full future free-space vector for
  unimplemented Marker, Construct, Schedule, or continuation products.
- Integrated pause advances no P5 time. Exceptional GameTest process exit may rely on process
  reclamation if normal Stopped is never posted.
- Fixed-count retention is not an exact component byte-size or retained-heap theorem.

## 45. Review Closure and Phase Status

The consolidated review found no active frozen-authority conflict and no need for a second
repository path or architecture-index delta. The phase state recorded by this commit is:

```text
P4-E
= COMPLETE

P5-A original review
= PARTIALLY SUPERSEDED

P5-A-R1 MULTI-TIER EXECUTION BUDGET
AND CIRCUIT-BREAKER RECONCILIATION
= COMPLETE — PASS

P5-A-R2 SCHEDULER / RESOLUTION /
GLOBAL BOUNDARY / EFFECTIVE-LIMIT RECONCILIATION
= COMPLETE — PASS

P5-A-R2 EVIDENCE METADATA SEAL
= COMPLETE

P5-A consolidated design
= COMPLETE — PASS

P5-A CONSOLIDATED ARCHITECTURE RECORD /
REVIEW CLOSURE
= COMPLETE UPON THIS COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  SIX-JOB REMOTE GATE PASS

P5 split
= NO SPLIT

P5 implementation
= READY UPON THE SAME CONDITION;
  NOT STARTED

P6
= NOT STARTED
```

This commit is the closure artifact. After its unique exact-SHA attempt-1 six-job remote Gate
passes, no second closure document or commit is required. This record does not declare P5
implementation complete, a P5 runtime Gate passed, P5 complete, or P6 ready.

## 46. Provenance

External review evidence is historical provenance; this repository architecture record is the
consolidated authority. The external roots are recorded as code text only and are not required to
understand the product semantics above:

```text
Original P5-A evidence root
/private/tmp/gramarye-p5-runtime-kernel-review-evidence-20260826T061026Z
4-column projection  b00f4a9349611c26d374fe972c7bc80d0a78cb1db50ae91baf386a0e165a038b
extended projection  df612cb9d9f58c39f49ba8892b51d6c9274cb35f3667528ba851ad22b7141688

P5-A-R1 evidence root
/private/tmp/gramarye-p5-a-r1-budget-reconciliation-evidence-20260826T110023Z
4-column projection  9bff5c731bbc8443a0ba7b6633565172390c6c0f527cebd178870aa763cf2810
extended projection  ef31e06c033446c178ea2f96e9aef91fd5948759fd2ed3446ee54572bf132976

P5-A-R2 evidence root
/private/tmp/gramarye-p5-a-r2-scheduler-limits-evidence-20260826T151426Z
4-column projection  fa287b90c69d2eb4ddc429b14ba14d4d5b3a8f1a26ee64b01bc7417f6a6038af
extended projection  498bbdbfda710bfcc3626ab3170a24ac07258431ee01469eb5278c0dfaace636

P5-A-R2 companion metadata seal root
/private/tmp/gramarye-p5-a-r2-evidence-metadata-seal-20260827T040819Z
R2 inventory SHA-256 498bbdbfda710bfcc3626ab3170a24ac07258431ee01469eb5278c0dfaace636
projections SHA-256   7668ed2be35543d24e4807e06327f354083ae168d42036fb72c596a268ac5da2
verdict SHA-256       865157ce10cdc1a948790be00a308e91dece2b625da528e3f3429544f84d1a61

Canonical base commit
bb2ae618be8f340d46a417c5c9bb95b623afe972
Canonical base tree
50a20e566aaf3c3c51a5fcf53189436b80067e86
```

The companion seal supplies immutable metadata and projection provenance for the original R2
evidence. It does not replace, reinterpret, or extend R2 product authority.


## 47. P5-A-V1 Compile-Closed Runtime Result Vocabulary Amendment

### 47.1 Status and Supersession

This V1 amendment is the compile-closure authority for the P5 V0 runtime vocabulary. It
supersedes Sections 7, 10, 11, 24, 27, 28, 30 through 34, and 37 only where those sections name a
Java type, family, variant, component, constructor invariant, absence representation, or
conversion without a compile-complete declaration. Every queue, counter, lifecycle, scheduler,
budget, breaker, deadline, persistence, generic-resolution, P6, exception, and cardinality rule in
the consolidated record remains unchanged.

The two previously unnamed product coordinates are closed without a new action: a duplicate live
server-minted UUID is `RuntimeAdmissionResult.InvalidEvent` with
`DUPLICATE_LIVE_SKILL_INSTANCE_ID`, and the already specified all-or-none post-port child
node/capability structural rejection is `RuntimeExecutionOutcome.InvalidEvent`. Neither mapping
adds a retry, publication, breaker, fault, or gameplay rule.

### 47.2 Package and Visibility

Every declaration in the following block belongs to `com.yo1no.gramarye`. Every new top-level P5
type is package-private. Nested permitted records are addressable only through their inaccessible
package-private owner. V1 creates zero public top-level types, zero public result entry methods,
and zero public callback or generic payload types. Existing public immutable P3 IDs, definition
values, and bounded subsystem reasons are imported rather than duplicated.

### 47.3 Exact Java Declarations

The following source is the exact declaration segment compiled by the repository-external Java
21 probe. It is normative Java, not pseudocode.

```java
package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.EventId;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemUnavailableReason;
import com.yo1no.gramarye.magic.definition.validation.ValidatedNodeDefinition;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

record RuntimeServerToken(long value) {
    RuntimeServerToken {
        if (value <= 0) {
            throw new IllegalArgumentException("runtime server token must be positive");
        }
    }
}

record RuntimeSkillInstanceSequence(long value) {
    RuntimeSkillInstanceSequence {
        if (value <= 0) {
            throw new IllegalArgumentException("runtime skill instance sequence must be positive");
        }
    }
}

record RuntimePlayerId(UUID value) {
    RuntimePlayerId {
        Objects.requireNonNull(value, "value");
    }
}

record RuntimeEntityId(UUID value) {
    RuntimeEntityId {
        Objects.requireNonNull(value, "value");
    }
}

enum RuntimeEntityKind {
    ANY_ENTITY,
    LIVING_ENTITY
}

sealed interface RuntimeOrigin
        permits ServerOrigin, PlayerOrigin, EntityOrigin, BlockOrigin {}

record ServerOrigin(RuntimeServerToken server) implements RuntimeOrigin {
    ServerOrigin {
        Objects.requireNonNull(server, "server");
    }
}

record PlayerOrigin(
        RuntimeServerToken server,
        ResourceKey<Level> dimension,
        RuntimePlayerId player) implements RuntimeOrigin {
    PlayerOrigin {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(player, "player");
    }
}

record EntityOrigin(
        RuntimeServerToken server,
        ResourceKey<Level> dimension,
        RuntimeEntityId entity,
        RuntimeEntityKind expectedKind) implements RuntimeOrigin {
    EntityOrigin {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(expectedKind, "expectedKind");
    }
}

record BlockOrigin(
        RuntimeServerToken server,
        ResourceKey<Level> dimension,
        BlockPos position) implements RuntimeOrigin {
    BlockOrigin {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        position = new BlockPos(Objects.requireNonNull(position, "position"));
    }
}

sealed interface RuntimeTarget permits PlayerTarget, EntityTarget, BlockTarget {}

record PlayerTarget(
        RuntimeServerToken server,
        ResourceKey<Level> dimension,
        RuntimePlayerId player) implements RuntimeTarget {
    PlayerTarget {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(player, "player");
    }
}

record EntityTarget(
        RuntimeServerToken server,
        ResourceKey<Level> dimension,
        RuntimeEntityId entity,
        RuntimeEntityKind expectedKind) implements RuntimeTarget {
    EntityTarget {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(expectedKind, "expectedKind");
    }
}

record BlockTarget(
        RuntimeServerToken server,
        ResourceKey<Level> dimension,
        BlockPos position) implements RuntimeTarget {
    BlockTarget {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        position = new BlockPos(Objects.requireNonNull(position, "position"));
    }
}

sealed interface RuntimeTriggerCause permits RootTriggerCause, ChildTriggerCause {
    TriggerEventKind eventKind();
}

record RootTriggerCause(TriggerEventKind eventKind) implements RuntimeTriggerCause {
    RootTriggerCause {
        Objects.requireNonNull(eventKind, "eventKind");
    }
}

record ChildTriggerCause(TriggerEventKind eventKind) implements RuntimeTriggerCause {
    ChildTriggerCause {
        Objects.requireNonNull(eventKind, "eventKind");
    }
}

sealed interface RuntimeExecutionData permits NoRuntimeExecutionData {}

enum NoRuntimeExecutionData implements RuntimeExecutionData {
    INSTANCE
}

enum RuntimeSchedulePersistence {
    MEMORY_ONLY,
    PERSISTENT
}

record RuntimeScheduleSpec(
        int delayTicks,
        int deadlineHorizonTicks,
        RuntimeSchedulePersistence persistence) {
    RuntimeScheduleSpec {
        Objects.requireNonNull(persistence, "persistence");
    }
}

sealed interface RuntimeBudgetAttribution
        permits PlayerRuntimeBudgetAttribution, NonPlayerRuntimeBudgetAttribution {
    RuntimeServerToken server();
}

record PlayerRuntimeBudgetAttribution(
        RuntimeServerToken server,
        RuntimePlayerId playerId) implements RuntimeBudgetAttribution {
    PlayerRuntimeBudgetAttribution {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(playerId, "playerId");
    }
}

record NonPlayerRuntimeBudgetAttribution(
        RuntimeServerToken server,
        NonPlayerRuntimeBudgetDomain domain) implements RuntimeBudgetAttribution {
    NonPlayerRuntimeBudgetAttribution {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(domain, "domain");
    }
}

enum NonPlayerRuntimeBudgetDomain {
    SERVER_AUTOMATION
}

record RuntimeRootEventSpec(
        SkillReference skillReference,
        int nodeIndex,
        RuntimeScheduleSpec schedule,
        RuntimeBudgetAttribution budgetAttribution,
        RuntimeOrigin origin,
        Optional<RuntimeTarget> target,
        RootTriggerCause triggerCause,
        RuntimeExecutionData executionData) {
    RuntimeRootEventSpec {
        Objects.requireNonNull(skillReference, "skillReference");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(budgetAttribution, "budgetAttribution");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(triggerCause, "triggerCause");
        Objects.requireNonNull(executionData, "executionData");
    }
}

sealed interface RuntimeCancellationHandle
        permits RuntimeCancellationToken, RuntimeEventToken {}

enum RuntimeCancellationTokenInvalidReason {
    EVENT_OWNER_MISMATCH
}

record RuntimeCancellationToken(
        RuntimeServerToken serverSlotToken,
        SkillInstanceId skillInstanceId) implements RuntimeCancellationHandle {
    RuntimeCancellationToken {
        Objects.requireNonNull(serverSlotToken, "serverSlotToken");
        Objects.requireNonNull(skillInstanceId, "skillInstanceId");
    }
}

record RuntimeEventToken(
        RuntimeServerToken serverSlotToken,
        SkillInstanceId skillInstanceId,
        EventId eventId) implements RuntimeCancellationHandle {
    RuntimeEventToken {
        Objects.requireNonNull(serverSlotToken, "serverSlotToken");
        Objects.requireNonNull(skillInstanceId, "skillInstanceId");
        Objects.requireNonNull(eventId, "eventId");
        if (eventId.value() <= 0) {
            throw new IllegalArgumentException("published EventId must be positive");
        }
    }
}

record RuntimeEvent(
        EventId eventId,
        SkillInstanceId skillInstanceId,
        RuntimeSkillInstanceSequence skillInstanceSequence,
        RuntimeCancellationToken cancellationToken,
        Optional<EventId> parentEventId,
        SkillReference skillReference,
        int nodeIndex,
        long createdRuntimeTick,
        long scheduledRuntimeTick,
        long deadlineRuntimeTick,
        int depth,
        int childSequence,
        RuntimeSchedulePersistence persistence,
        RuntimeBudgetAttribution budgetAttribution,
        RuntimeOrigin origin,
        Optional<RuntimeTarget> target,
        RuntimeTriggerCause triggerCause,
        RuntimeExecutionData executionData) {
    RuntimeEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(skillInstanceId, "skillInstanceId");
        Objects.requireNonNull(skillInstanceSequence, "skillInstanceSequence");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(parentEventId, "parentEventId");
        Objects.requireNonNull(skillReference, "skillReference");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(budgetAttribution, "budgetAttribution");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(triggerCause, "triggerCause");
        Objects.requireNonNull(executionData, "executionData");
        if (eventId.value() <= 0
                || parentEventId.isPresent() && parentEventId.get().value() <= 0
                || nodeIndex < 0
                || nodeIndex >= MagicSafetyCeilings.MAX_NODES
                || createdRuntimeTick < 0
                || scheduledRuntimeTick < createdRuntimeTick
                || deadlineRuntimeTick < scheduledRuntimeTick
                || depth < 0
                || childSequence < 0
                || childSequence > RuntimeChildPlan.PHYSICAL_MAXIMUM) {
            throw new IllegalArgumentException("invalid published runtime event coordinate");
        }
        if (persistence != RuntimeSchedulePersistence.MEMORY_ONLY) {
            throw new IllegalArgumentException("published P5 event must be memory-only");
        }
        if (!skillInstanceId.equals(cancellationToken.skillInstanceId())) {
            throw new RuntimeKernelException(
                    RuntimeKernelException.Code.QUEUED_EVENT_IDENTITY_INVARIANT);
        }
        var server = cancellationToken.serverSlotToken();
        if (!server.equals(serverOf(origin))
                || !server.equals(budgetAttribution.server())
                || target.isPresent()
                        && !server.equals(serverOf(target.get()))) {
            throw new RuntimeKernelException(
                    RuntimeKernelException.Code.QUEUED_EVENT_IDENTITY_INVARIANT);
        }
        if (origin instanceof PlayerOrigin playerOrigin
                && (!(budgetAttribution instanceof PlayerRuntimeBudgetAttribution playerAttribution)
                        || !playerOrigin.player().equals(playerAttribution.playerId()))) {
            throw new RuntimeKernelException(
                    RuntimeKernelException.Code.QUEUED_EVENT_IDENTITY_INVARIANT);
        }
        var rootShape = parentEventId.isEmpty()
                && depth == 0
                && childSequence == 0
                && triggerCause instanceof RootTriggerCause;
        var childShape = parentEventId.isPresent()
                && depth > 0
                && childSequence > 0
                && triggerCause instanceof ChildTriggerCause;
        if (!rootShape && !childShape) {
            throw new RuntimeKernelException(
                    RuntimeKernelException.Code.QUEUED_EVENT_IDENTITY_INVARIANT);
        }
    }

    private static RuntimeServerToken serverOf(RuntimeOrigin origin) {
        return switch (origin) {
            case ServerOrigin value -> value.server();
            case PlayerOrigin value -> value.server();
            case EntityOrigin value -> value.server();
            case BlockOrigin value -> value.server();
        };
    }

    private static RuntimeServerToken serverOf(RuntimeTarget target) {
        return switch (target) {
            case PlayerTarget value -> value.server();
            case EntityTarget value -> value.server();
            case BlockTarget value -> value.server();
        };
    }
}

enum RuntimeBudgetDecision {
    EXECUTE,
    DEFER_SKILL_INSTANCE_TICK_LIMIT,
    DEFER_PLAYER_TICK_LIMIT,
    DEFER_NON_PLAYER_DOMAIN_TICK_LIMIT
}

enum RuntimeCircuitBreakReason {
    SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED,
    PLAYER_PENDING_EVENTS_EXCEEDED,
    NON_PLAYER_DOMAIN_PENDING_EVENTS_EXCEEDED,
    SERVER_PENDING_EVENTS_EXCEEDED
}

record RuntimeCircuitBreakerSummary(
        RuntimeCircuitBreakReason reason,
        int pendingBefore,
        int requestedAdditionalCount,
        int maximum,
        int removedQueuedAndDeferredCount,
        boolean eventInFlight) {
    RuntimeCircuitBreakerSummary {
        Objects.requireNonNull(reason, "reason");
        var hardMaximum = switch (reason) {
            case SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED -> 256;
            case PLAYER_PENDING_EVENTS_EXCEEDED,
                    NON_PLAYER_DOMAIN_PENDING_EVENTS_EXCEEDED -> 1_024;
            case SERVER_PENDING_EVENTS_EXCEEDED -> 4_096;
        };
        if (pendingBefore < 0
                || requestedAdditionalCount <= 0
                || requestedAdditionalCount > RuntimeChildPlan.PHYSICAL_MAXIMUM
                || maximum <= 0
                || maximum > hardMaximum
                || pendingBefore > maximum
                || removedQueuedAndDeferredCount < 0
                || removedQueuedAndDeferredCount > pendingBefore - (eventInFlight ? 1 : 0) || (eventInFlight && removedQueuedAndDeferredCount > 255)
                || requestedAdditionalCount <= maximum - pendingBefore) {
            throw new IllegalArgumentException("invalid circuit-breaker summary");
        }
    }
}

enum RuntimeReferenceFailureReason {
    WRONG_SERVER,
    DIMENSION_UNAVAILABLE,
    WRONG_DIMENSION,
    MISSING,
    MISSING_OR_UNLOADED,
    UNLOADED,
    TYPE_MISMATCH
}

enum RuntimePortRejectionReason {
    PORT_UNAVAILABLE,
    EFFECT_SPECIFIC_SOURCE_REJECTED,
    EFFECT_SPECIFIC_TARGET_REJECTED
}

enum RuntimeSequenceKind {
    EVENT_SEQUENCE,
    SKILL_INSTANCE_SEQUENCE
}

enum RuntimeDrainStopReason {
    SERVER_EXECUTION_LIMIT_REACHED
}

enum RuntimeTickAdvanceResult {
    ADVANCED,
    EXHAUSTED
}

enum RuntimeScheduleRejectionReason {
    DELAY_OUT_OF_RANGE,
    DELAY_OVERFLOW,
    DEADLINE_OUT_OF_RANGE,
    DEADLINE_OVERFLOW,
    DEADLINE_BEFORE_SCHEDULED_TICK
}

enum RuntimeBudgetRejectionReason {
    LINEAGE_EVENT_LIMIT_EXCEEDED,
    DEPTH_LIMIT_EXCEEDED,
    DIRECT_CHILD_LIMIT_EXCEEDED,
    ZERO_DELAY_CHILD_LIMIT_EXCEEDED,
    EVENT_SEQUENCE_CAPACITY_EXCEEDED
}

sealed interface SkillRevisionUnavailableReason
        permits SkillRevisionUnavailableReason.DefinitionSubsystemUnavailable,
                SkillRevisionUnavailableReason.ExactRevisionMissing,
                SkillRevisionUnavailableReason.RuntimeProjectionUnavailable,
                SkillRevisionUnavailableReason.TransientPinUnavailable {
    record DefinitionSubsystemUnavailable(SkillSubsystemUnavailableReason reason)
            implements SkillRevisionUnavailableReason {
        public DefinitionSubsystemUnavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record ExactRevisionMissing() implements SkillRevisionUnavailableReason {}

    record RuntimeProjectionUnavailable() implements SkillRevisionUnavailableReason {}

    record TransientPinUnavailable() implements SkillRevisionUnavailableReason {}
}

enum InvalidEventReason {
    INVALID_NODE_COORDINATE,
    INVALID_NODE_CAPABILITY,
    INVALID_TRIGGER_CAUSE,
    INVALID_REFERENCE_SHAPE,
    INVALID_EXECUTION_DATA,
    INVALID_BUDGET_ATTRIBUTION,
    BUDGET_ATTRIBUTION_MISMATCH,
    DUPLICATE_LIVE_SKILL_INSTANCE_ID
}

record RuntimeChildSpec(
        int nodeIndex,
        int delayTicks,
        int deadlineHorizonTicks,
        RuntimeOrigin origin,
        Optional<RuntimeTarget> target,
        ChildTriggerCause triggerCause,
        RuntimeExecutionData executionData) {
    RuntimeChildSpec {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(triggerCause, "triggerCause");
        Objects.requireNonNull(executionData, "executionData");
    }
}

record RuntimeChildPlan(List<RuntimeChildSpec> children) {
    static final int PHYSICAL_MAXIMUM = 32;
    static final RuntimeChildPlan EMPTY = new RuntimeChildPlan(List.of());

    RuntimeChildPlan {
        Objects.requireNonNull(children, "children");
        if (children.size() > PHYSICAL_MAXIMUM) {
            throw new RuntimeKernelException(
                    RuntimeKernelException.Code.CHILD_PLAN_HARD_CAPACITY_EXCEEDED);
        }
        children = List.copyOf(children);
    }
}

record RuntimeExecutionBudget(
        int directChildCapacity,
        int zeroDelayChildCapacity,
        int remainingLineageEvents,
        int remainingDepth,
        int maximumDelayTicks,
        int maximumDeadlineHorizonTicks,
        int remainingSkillInstancePending,
        int remainingAttributionPending,
        int remainingServerPending) {
    RuntimeExecutionBudget {
        if (directChildCapacity < 0
                || zeroDelayChildCapacity < 0
                || remainingLineageEvents < 0
                || remainingDepth < 0
                || maximumDelayTicks < 0
                || maximumDeadlineHorizonTicks < 0
                || remainingSkillInstancePending < 0
                || remainingAttributionPending < 0
                || remainingServerPending < 0) {
            throw new IllegalArgumentException("runtime execution budget cannot be negative");
        }
        if (directChildCapacity > RuntimeChildPlan.PHYSICAL_MAXIMUM
                || zeroDelayChildCapacity > 16
                || remainingLineageEvents > 511
                || remainingDepth > 32
                || maximumDelayTicks > 12_000
                || maximumDeadlineHorizonTicks > 12_000
                || remainingSkillInstancePending > 255
                || remainingAttributionPending > 1_023
                || remainingServerPending > 4_095
                || zeroDelayChildCapacity > directChildCapacity
                || directChildCapacity > remainingLineageEvents
                || directChildCapacity > remainingSkillInstancePending
                || directChildCapacity > remainingAttributionPending
                || directChildCapacity > remainingServerPending
                || remainingDepth == 0 && directChildCapacity != 0
                || maximumDelayTicks > maximumDeadlineHorizonTicks) {
            throw new IllegalArgumentException("runtime execution budget relation violated");
        }
    }
}

sealed interface RuntimeAdmissionResult
        permits RuntimeAdmissionResult.AcceptedMemoryOnly,
                RuntimeAdmissionResult.PersistentScheduleUnsupported,
                RuntimeAdmissionResult.DelayOutOfRange,
                RuntimeAdmissionResult.DelayOverflow,
                RuntimeAdmissionResult.DeadlineOutOfRange,
                RuntimeAdmissionResult.DeadlineOverflow,
                RuntimeAdmissionResult.DeadlineBeforeScheduledTick,
                RuntimeAdmissionResult.InvalidRuntimeReference,
                RuntimeAdmissionResult.SkillRevisionUnavailable,
                RuntimeAdmissionResult.InvalidEvent,
                RuntimeAdmissionResult.OwnerInstanceUnavailable,
                RuntimeAdmissionResult.ActiveLineageCapacityExceeded,
                RuntimeAdmissionResult.ActiveBudgetAttributionCapacityExceeded,
                RuntimeAdmissionResult.RootAdmissionBudgetExceeded,
                RuntimeAdmissionResult.CircuitBroken,
                RuntimeAdmissionResult.ServerNotRunning,
                RuntimeAdmissionResult.ServerStopping,
                RuntimeAdmissionResult.WrongThread,
                RuntimeAdmissionResult.SequenceExhausted,
                RuntimeAdmissionResult.TickExhausted,
                RuntimeAdmissionResult.KernelFaulted {
    record AcceptedMemoryOnly(
            RuntimeEventToken eventToken,
            RuntimeCancellationToken cancellationToken) implements RuntimeAdmissionResult {
        public AcceptedMemoryOnly {
            Objects.requireNonNull(eventToken, "eventToken");
            Objects.requireNonNull(cancellationToken, "cancellationToken");
            if (!eventToken.serverSlotToken().equals(cancellationToken.serverSlotToken())
                    || !eventToken.skillInstanceId().equals(cancellationToken.skillInstanceId())) {
                throw new IllegalArgumentException("accepted handles must share ownership");
            }
        }
    }

    record PersistentScheduleUnsupported() implements RuntimeAdmissionResult {}

    record DelayOutOfRange(int requestedDelayTicks, int maximumDelayTicks)
            implements RuntimeAdmissionResult {
        public DelayOutOfRange {
            requireMaximumInRange(maximumDelayTicks, 12_000);
            if (requestedDelayTicks >= 0 && requestedDelayTicks <= maximumDelayTicks) {
                throw new IllegalArgumentException("delay is not out of range");
            }
        }
    }

    record DelayOverflow() implements RuntimeAdmissionResult {}

    record DeadlineOutOfRange(int requestedHorizonTicks, int maximumHorizonTicks)
            implements RuntimeAdmissionResult {
        public DeadlineOutOfRange {
            requireMaximumInRange(maximumHorizonTicks, 12_000);
            if (requestedHorizonTicks >= 0 && requestedHorizonTicks <= maximumHorizonTicks) {
                throw new IllegalArgumentException("deadline horizon is not out of range");
            }
        }
    }

    record DeadlineOverflow() implements RuntimeAdmissionResult {}

    record DeadlineBeforeScheduledTick(long scheduledRuntimeTick, long deadlineRuntimeTick)
            implements RuntimeAdmissionResult {
        public DeadlineBeforeScheduledTick {
            if (scheduledRuntimeTick < 0 || deadlineRuntimeTick < 0
                    || scheduledRuntimeTick <= deadlineRuntimeTick) {
                throw new IllegalArgumentException("deadline must precede scheduled tick");
            }
        }
    }

    record InvalidRuntimeReference(RuntimeReferenceFailureReason reason)
            implements RuntimeAdmissionResult {
        public InvalidRuntimeReference {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record SkillRevisionUnavailable(SkillRevisionUnavailableReason reason)
            implements RuntimeAdmissionResult {
        public SkillRevisionUnavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record InvalidEvent(InvalidEventReason reason) implements RuntimeAdmissionResult {
        public InvalidEvent {
            Objects.requireNonNull(reason, "reason");
            if (reason == InvalidEventReason.INVALID_NODE_CAPABILITY) {
                throw new IllegalArgumentException(
                        "root admission has no node-capability result coordinate");
            }
        }
    }

    record OwnerInstanceUnavailable() implements RuntimeAdmissionResult {}

    record ActiveLineageCapacityExceeded(int current, int maximum)
            implements RuntimeAdmissionResult {
        public ActiveLineageCapacityExceeded {
            requireReached(current, maximum, 128);
        }
    }

    record ActiveBudgetAttributionCapacityExceeded(int current, int maximum)
            implements RuntimeAdmissionResult {
        public ActiveBudgetAttributionCapacityExceeded {
            requireReached(current, maximum, 32);
        }
    }

    record RootAdmissionBudgetExceeded(int maximum) implements RuntimeAdmissionResult {
        public RootAdmissionBudgetExceeded {
            requirePositiveMaximum(maximum, 64);
        }
    }

    record CircuitBroken(RuntimeCircuitBreakerSummary summary)
            implements RuntimeAdmissionResult {
        public CircuitBroken {
            Objects.requireNonNull(summary, "summary");
            if (summary.reason() == RuntimeCircuitBreakReason.SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED
                    || summary.requestedAdditionalCount() != 1
                    || summary.removedQueuedAndDeferredCount() != 0
                    || summary.eventInFlight()) {
                throw new IllegalArgumentException("invalid root-admission breaker summary");
            }
        }
    }

    record ServerNotRunning() implements RuntimeAdmissionResult {}

    record ServerStopping() implements RuntimeAdmissionResult {}

    record WrongThread() implements RuntimeAdmissionResult {}

    record SequenceExhausted(RuntimeSequenceKind sequenceKind)
            implements RuntimeAdmissionResult {
        public SequenceExhausted {
            Objects.requireNonNull(sequenceKind, "sequenceKind");
        }
    }

    record TickExhausted() implements RuntimeAdmissionResult {}

    record KernelFaulted() implements RuntimeAdmissionResult {}

    private static void requirePositiveMaximum(int maximum, int hardMaximum) {
        if (maximum <= 0 || maximum > hardMaximum) {
            throw new IllegalArgumentException("maximum is outside its hard range");
        }
    }

    private static void requireMaximumInRange(int maximum, int hardMaximum) {
        if (maximum < 0 || maximum > hardMaximum) {
            throw new IllegalArgumentException("maximum is outside its hard range");
        }
    }

    private static void requireReached(int current, int maximum, int hardMaximum) {
        requirePositiveMaximum(maximum, hardMaximum);
        if (current != maximum) {
            throw new IllegalArgumentException("capacity result requires exact equality");
        }
    }
}

sealed interface RuntimeCancellationResult
        permits RuntimeCancellationResult.CancelledEvent,
                RuntimeCancellationResult.CancelledSkillInstance,
                RuntimeCancellationResult.CancellationRequested,
                RuntimeCancellationResult.InFlight,
                RuntimeCancellationResult.AlreadyCancelled,
                RuntimeCancellationResult.NotPending,
                RuntimeCancellationResult.WrongServer,
                RuntimeCancellationResult.WrongThread,
                RuntimeCancellationResult.ServerNotRunning,
                RuntimeCancellationResult.ServerStopping,
                RuntimeCancellationResult.CancellationBudgetExceeded,
                RuntimeCancellationResult.CancellationTokenInvalid {
    record CancelledEvent() implements RuntimeCancellationResult {}

    record CancelledSkillInstance(int removedCount) implements RuntimeCancellationResult {
        public CancelledSkillInstance {
            if (removedCount <= 0 || removedCount > 256) {
                throw new IllegalArgumentException("removedCount is outside 1..256");
            }
        }
    }

    record CancellationRequested(int removedCount) implements RuntimeCancellationResult {
        public CancellationRequested {
            if (removedCount < 0 || removedCount > 255) {
                throw new IllegalArgumentException("removedCount is outside 0..255");
            }
        }
    }

    record InFlight() implements RuntimeCancellationResult {}

    record AlreadyCancelled() implements RuntimeCancellationResult {}

    record NotPending() implements RuntimeCancellationResult {}

    record WrongServer() implements RuntimeCancellationResult {}

    record WrongThread() implements RuntimeCancellationResult {}

    record ServerNotRunning() implements RuntimeCancellationResult {}

    record ServerStopping() implements RuntimeCancellationResult {}

    record CancellationBudgetExceeded(int maximum) implements RuntimeCancellationResult {
        public CancellationBudgetExceeded {
            if (maximum <= 0 || maximum > 128) {
                throw new IllegalArgumentException("maximum is outside 1..128");
            }
        }
    }

    record CancellationTokenInvalid(RuntimeCancellationTokenInvalidReason reason)
            implements RuntimeCancellationResult {
        public CancellationTokenInvalid {
            Objects.requireNonNull(reason, "reason");
        }
    }
}

sealed interface RuntimeExecutionOutcome
        permits RuntimeExecutionOutcome.Completed,
                RuntimeExecutionOutcome.CompletedWithChildren,
                RuntimeExecutionOutcome.RejectedByExecutionPort,
                RuntimeExecutionOutcome.SkillRevisionUnavailable,
                RuntimeExecutionOutcome.SourceMissing,
                RuntimeExecutionOutcome.TargetMissing,
                RuntimeExecutionOutcome.InvalidRuntimeReference,
                RuntimeExecutionOutcome.DeadlineExpired,
                RuntimeExecutionOutcome.Cancelled,
                RuntimeExecutionOutcome.OwnerInstanceUnavailable,
                RuntimeExecutionOutcome.ServerStopping,
                RuntimeExecutionOutcome.BudgetRejected,
                RuntimeExecutionOutcome.ScheduleRejected,
                RuntimeExecutionOutcome.CircuitBroken,
                RuntimeExecutionOutcome.InvalidEvent {
    record Completed() implements RuntimeExecutionOutcome {}

    record CompletedWithChildren(int count) implements RuntimeExecutionOutcome {
        public CompletedWithChildren {
            if (count <= 0 || count > RuntimeChildPlan.PHYSICAL_MAXIMUM) {
                throw new IllegalArgumentException("completed child count out of range");
            }
        }
    }

    record RejectedByExecutionPort(RuntimePortRejectionReason reason)
            implements RuntimeExecutionOutcome {
        public RejectedByExecutionPort {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record SkillRevisionUnavailable() implements RuntimeExecutionOutcome {}

    record SourceMissing(RuntimeReferenceFailureReason reason)
            implements RuntimeExecutionOutcome {
        public SourceMissing {
            requireMissingReason(reason);
        }
    }

    record TargetMissing(RuntimeReferenceFailureReason reason)
            implements RuntimeExecutionOutcome {
        public TargetMissing {
            requireMissingReason(reason);
        }
    }

    record InvalidRuntimeReference(RuntimeReferenceFailureReason reason)
            implements RuntimeExecutionOutcome {
        public InvalidRuntimeReference {
            requireInvalidReferenceReason(reason);
        }
    }

    record DeadlineExpired(long deadlineRuntimeTick, long observedRuntimeTick)
            implements RuntimeExecutionOutcome {
        public DeadlineExpired {
            if (deadlineRuntimeTick < 0 || observedRuntimeTick <= deadlineRuntimeTick) {
                throw new IllegalArgumentException("deadline has not expired");
            }
        }
    }

    record Cancelled() implements RuntimeExecutionOutcome {}

    record OwnerInstanceUnavailable() implements RuntimeExecutionOutcome {}

    record ServerStopping() implements RuntimeExecutionOutcome {}

    record BudgetRejected(RuntimeBudgetRejectionReason reason)
            implements RuntimeExecutionOutcome {
        public BudgetRejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record ScheduleRejected(RuntimeScheduleRejectionReason reason)
            implements RuntimeExecutionOutcome {
        public ScheduleRejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    record CircuitBroken(RuntimeCircuitBreakerSummary summary)
            implements RuntimeExecutionOutcome {
        public CircuitBroken {
            Objects.requireNonNull(summary, "summary");
            if (!summary.eventInFlight()) {
                throw new IllegalArgumentException(
                        "execution breaker requires the current in-flight event");
            }
        }
    }

    record InvalidEvent(InvalidEventReason reason) implements RuntimeExecutionOutcome {
        public InvalidEvent {
            Objects.requireNonNull(reason, "reason");
            if (reason != InvalidEventReason.INVALID_NODE_COORDINATE
                    && reason != InvalidEventReason.INVALID_NODE_CAPABILITY
                    && reason != InvalidEventReason.INVALID_TRIGGER_CAUSE
                    && reason != InvalidEventReason.INVALID_REFERENCE_SHAPE
                    && reason != InvalidEventReason.INVALID_EXECUTION_DATA) {
                throw new IllegalArgumentException(
                        "execution InvalidEvent reason is not a child structural rejection");
            }
        }
    }

    private static void requireMissingReason(RuntimeReferenceFailureReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason != RuntimeReferenceFailureReason.MISSING
                && reason != RuntimeReferenceFailureReason.MISSING_OR_UNLOADED
                && reason != RuntimeReferenceFailureReason.UNLOADED) {
            throw new IllegalArgumentException("reason is not a missing/unloaded reference");
        }
    }

    private static void requireInvalidReferenceReason(RuntimeReferenceFailureReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason != RuntimeReferenceFailureReason.WRONG_SERVER
                && reason != RuntimeReferenceFailureReason.DIMENSION_UNAVAILABLE
                && reason != RuntimeReferenceFailureReason.WRONG_DIMENSION
                && reason != RuntimeReferenceFailureReason.TYPE_MISMATCH) {
            throw new IllegalArgumentException("reason is not an invalid runtime reference");
        }
    }
}

sealed interface RuntimePortOutcome
        permits RuntimePortOutcome.Completed, RuntimePortOutcome.Rejected {
    record Completed() implements RuntimePortOutcome {}

    record Rejected(RuntimePortRejectionReason reason) implements RuntimePortOutcome {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}

sealed interface ResolvedRuntimeOrigin
        permits ResolvedServerOrigin,
                ResolvedPlayerOrigin,
                ResolvedEntityOrigin,
                ResolvedBlockOrigin {}

record ResolvedServerOrigin(MinecraftServer server) implements ResolvedRuntimeOrigin {
    ResolvedServerOrigin {
        Objects.requireNonNull(server, "server");
    }
}

record ResolvedPlayerOrigin(ServerPlayer player) implements ResolvedRuntimeOrigin {
    ResolvedPlayerOrigin {
        Objects.requireNonNull(player, "player");
    }
}

record ResolvedEntityOrigin(Entity entity) implements ResolvedRuntimeOrigin {
    ResolvedEntityOrigin {
        Objects.requireNonNull(entity, "entity");
    }
}

record ResolvedBlockOrigin(ServerLevel level, BlockPos position)
        implements ResolvedRuntimeOrigin {
    ResolvedBlockOrigin {
        Objects.requireNonNull(level, "level");
        position = new BlockPos(Objects.requireNonNull(position, "position"));
    }
}

sealed interface ResolvedRuntimeTarget
        permits NoResolvedRuntimeTarget,
                ResolvedPlayerTarget,
                ResolvedEntityTarget,
                ResolvedBlockTarget {}

enum NoResolvedRuntimeTarget implements ResolvedRuntimeTarget {
    INSTANCE
}

record ResolvedPlayerTarget(ServerPlayer player) implements ResolvedRuntimeTarget {
    ResolvedPlayerTarget {
        Objects.requireNonNull(player, "player");
    }
}

record ResolvedEntityTarget(Entity entity) implements ResolvedRuntimeTarget {
    ResolvedEntityTarget {
        Objects.requireNonNull(entity, "entity");
    }
}

record ResolvedBlockTarget(ServerLevel level, BlockPos position)
        implements ResolvedRuntimeTarget {
    ResolvedBlockTarget {
        Objects.requireNonNull(level, "level");
        position = new BlockPos(Objects.requireNonNull(position, "position"));
    }
}

record ResolvedRuntimeReferenceContext(
        ResolvedRuntimeOrigin origin,
        ResolvedRuntimeTarget target) {
    ResolvedRuntimeReferenceContext {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");
    }
}

record RuntimeExecutionContext(
        MinecraftServer server,
        ValidatedSkillDefinition definition,
        ValidatedNodeDefinition node,
        long currentRuntimeTick,
        RuntimeServerToken serverSlotToken,
        ResolvedRuntimeReferenceContext resolvedReferences,
        RuntimeExecutionBudget executionBudget) {
    RuntimeExecutionContext {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(serverSlotToken, "serverSlotToken");
        Objects.requireNonNull(resolvedReferences, "resolvedReferences");
        Objects.requireNonNull(executionBudget, "executionBudget");
        if (currentRuntimeTick < 0) {
            throw new IllegalArgumentException("current runtime tick must be non-negative");
        }
        var nodeIndex = node.nodeIndex();
        if (nodeIndex < 0
                || nodeIndex >= definition.nodes().size()
                || definition.nodes().get(nodeIndex) != node) {
            throw new IllegalArgumentException(
                    "runtime node must be the exact indexed node from the definition");
        }
    }
}

sealed interface RuntimeReferenceResolutionOutcome
        permits RuntimeReferenceResolutionOutcome.Resolved,
                RuntimeReferenceResolutionOutcome.SourceMissing,
                RuntimeReferenceResolutionOutcome.TargetMissing,
                RuntimeReferenceResolutionOutcome.InvalidRuntimeReference {
    record Resolved(ResolvedRuntimeReferenceContext context)
            implements RuntimeReferenceResolutionOutcome {
        public Resolved {
            Objects.requireNonNull(context, "context");
        }
    }

    record SourceMissing(RuntimeReferenceFailureReason reason)
            implements RuntimeReferenceResolutionOutcome {
        public SourceMissing {
            requireMissingReason(reason);
        }
    }

    record TargetMissing(RuntimeReferenceFailureReason reason)
            implements RuntimeReferenceResolutionOutcome {
        public TargetMissing {
            requireMissingReason(reason);
        }
    }

    record InvalidRuntimeReference(RuntimeReferenceFailureReason reason)
            implements RuntimeReferenceResolutionOutcome {
        public InvalidRuntimeReference {
            Objects.requireNonNull(reason, "reason");
            if (reason != RuntimeReferenceFailureReason.WRONG_SERVER
                    && reason != RuntimeReferenceFailureReason.DIMENSION_UNAVAILABLE
                    && reason != RuntimeReferenceFailureReason.WRONG_DIMENSION
                    && reason != RuntimeReferenceFailureReason.TYPE_MISMATCH) {
                throw new IllegalArgumentException("reason is not an invalid runtime reference");
            }
        }
    }

    private static void requireMissingReason(RuntimeReferenceFailureReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason != RuntimeReferenceFailureReason.MISSING
                && reason != RuntimeReferenceFailureReason.MISSING_OR_UNLOADED
                && reason != RuntimeReferenceFailureReason.UNLOADED) {
            throw new IllegalArgumentException("reason is not a missing/unloaded reference");
        }
    }
}

interface RuntimeReferenceResolver {
    RuntimeReferenceResolutionOutcome resolve(MinecraftServer server, RuntimeEvent event);
}

interface RuntimeExecutionPort {
    RuntimeExecutionBatch execute(RuntimeEvent event, RuntimeExecutionContext context);
}

record RuntimeExecutionBatch(RuntimePortOutcome outcome, RuntimeChildPlan children) {
    RuntimeExecutionBatch {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(children, "children");
        if (!(outcome instanceof RuntimePortOutcome.Completed) && !children.children().isEmpty()) {
            throw new RuntimeKernelException(
                    RuntimeKernelException.Code.INVALID_OUTCOME_PLAN_PAIRING);
        }
    }
}

enum UnavailableRuntimeExecutionPort implements RuntimeExecutionPort {
    INSTANCE;

    @Override
    public RuntimeExecutionBatch execute(RuntimeEvent event, RuntimeExecutionContext context) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(context, "context");
        return new RuntimeExecutionBatch(
                new RuntimePortOutcome.Rejected(RuntimePortRejectionReason.PORT_UNAVAILABLE),
                RuntimeChildPlan.EMPTY);
    }
}

@SuppressWarnings("serial")
final class RuntimeKernelException extends RuntimeException {
    enum Code {
        DUPLICATE_SERVER_INSTALL,
        SECOND_ACTIVE_SERVER,
        STOPPED_SERVER_INSTALL,
        TICK_BEFORE_INSTALL,
        WRONG_THREAD_LIFECYCLE,
        WRONG_THREAD_DRAIN,
        WRONG_THREAD_CHILD_ADMISSION,
        NESTED_DRAIN,
        QUEUED_EVENT_IDENTITY_INVARIANT,
        EVENT_INDEX_INVARIANT,
        RESERVATION_ACCOUNTING_INVARIANT,
        LEASE_ACCOUNTING_INVARIANT,
        ATTRIBUTION_STATE_CAPACITY_INVARIANT,
        DEFERRED_BUFFER_OVERFLOW,
        BREAKER_SCRATCH_OVERFLOW,
        CHILD_PLAN_HARD_CAPACITY_EXCEEDED,
        NULL_EXECUTION_BATCH,
        INVALID_OUTCOME_PLAN_PAIRING,
        INVALID_CHILD_PLAN_INVARIANT,
        SERVER_SLOT_TOKEN_EXHAUSTED
    }

    private final Code code;

    RuntimeKernelException(Code code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    Code code() {
        return code;
    }
}

enum P5RuntimeLimitKey {
    PENDING_EVENTS_PER_SKILL_INSTANCE,
    PENDING_EVENTS_PER_ATTRIBUTION,
    PENDING_EVENTS_PER_SERVER,
    ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION,
    ACTIVE_SKILL_INSTANCES_PER_SERVER,
    ROOT_ADMISSIONS_PER_TICK,
    EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK,
    EXECUTIONS_PER_ATTRIBUTION_PER_TICK,
    EXECUTIONS_PER_SERVER_PER_TICK,
    EVENTS_PER_SKILL_INSTANCE,
    MAXIMUM_DEPTH,
    DIRECT_CHILDREN_PER_EVENT,
    ZERO_DELAY_CHILDREN_PER_EVENT,
    MAXIMUM_DELAY_TICKS,
    MAXIMUM_DEADLINE_HORIZON_TICKS,
    CANCELLATIONS_PER_TICK
}

enum P5RuntimeConfigurationFailureReason {
    CONFIG_UNAVAILABLE,
    MISSING_REQUIRED_VALUE,
    WRONG_VALUE_TYPE,
    BELOW_MINIMUM,
    ABOVE_HARD_MAXIMUM,
    RELATION_VIOLATION,
    DERIVATION_OVERFLOW
}

record P5RuntimeConfigurationFailure(
        P5RuntimeConfigurationFailureReason reason,
        Optional<P5RuntimeLimitKey> primaryKey,
        Optional<P5RuntimeLimitKey> relatedKey) {
    P5RuntimeConfigurationFailure {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(primaryKey, "primaryKey");
        Objects.requireNonNull(relatedKey, "relatedKey");
        switch (reason) {
            case CONFIG_UNAVAILABLE -> {
                if (primaryKey.isPresent() || relatedKey.isPresent()) {
                    throw new IllegalArgumentException("config unavailable has no keys");
                }
            }
            case MISSING_REQUIRED_VALUE, WRONG_VALUE_TYPE, BELOW_MINIMUM, ABOVE_HARD_MAXIMUM -> {
                if (primaryKey.isEmpty() || relatedKey.isPresent()) {
                    throw new IllegalArgumentException("single-key config failure shape");
                }
            }
            case RELATION_VIOLATION -> {
                if (primaryKey.isEmpty()
                        || relatedKey.isEmpty()
                        || !isAuthorizedRelation(primaryKey.get(), relatedKey.get())) {
                    throw new IllegalArgumentException("unauthorized relation failure key pair");
                }
            }
            case DERIVATION_OVERFLOW -> {
                if (primaryKey.isEmpty()
                        || !isAuthorizedDerivation(primaryKey.get(), relatedKey)) {
                    throw new IllegalArgumentException("unauthorized derivation failure key shape");
                }
            }
        }
    }

    private static boolean isAuthorizedRelation(
            P5RuntimeLimitKey primary,
            P5RuntimeLimitKey related) {
        return switch (primary) {
            case PENDING_EVENTS_PER_SKILL_INSTANCE ->
                    related == P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION
                            || related == P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE;
            case PENDING_EVENTS_PER_ATTRIBUTION ->
                    related == P5RuntimeLimitKey.PENDING_EVENTS_PER_SERVER;
            case ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION ->
                    related == P5RuntimeLimitKey.ACTIVE_SKILL_INSTANCES_PER_SERVER
                            || related == P5RuntimeLimitKey.PENDING_EVENTS_PER_ATTRIBUTION;
            case ACTIVE_SKILL_INSTANCES_PER_SERVER ->
                    related == P5RuntimeLimitKey.PENDING_EVENTS_PER_SERVER;
            case EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK ->
                    related == P5RuntimeLimitKey.EXECUTIONS_PER_ATTRIBUTION_PER_TICK;
            case EXECUTIONS_PER_ATTRIBUTION_PER_TICK ->
                    related == P5RuntimeLimitKey.EXECUTIONS_PER_SERVER_PER_TICK;
            case DIRECT_CHILDREN_PER_EVENT ->
                    related == P5RuntimeLimitKey.EVENTS_PER_SKILL_INSTANCE;
            case ZERO_DELAY_CHILDREN_PER_EVENT ->
                    related == P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT;
            case MAXIMUM_DEPTH -> related == P5RuntimeLimitKey.DIRECT_CHILDREN_PER_EVENT;
            case MAXIMUM_DELAY_TICKS ->
                    related == P5RuntimeLimitKey.MAXIMUM_DEADLINE_HORIZON_TICKS;
            case PENDING_EVENTS_PER_SERVER,
                    ROOT_ADMISSIONS_PER_TICK,
                    EXECUTIONS_PER_SERVER_PER_TICK,
                    EVENTS_PER_SKILL_INSTANCE,
                    MAXIMUM_DEADLINE_HORIZON_TICKS,
                    CANCELLATIONS_PER_TICK -> false;
        };
    }

    private static boolean isAuthorizedDerivation(
            P5RuntimeLimitKey primary,
            Optional<P5RuntimeLimitKey> related) {
        return switch (primary) {
            case EVENTS_PER_SKILL_INSTANCE -> related.isEmpty();
            case ACTIVE_SKILL_INSTANCES_PER_SERVER ->
                    related.isEmpty()
                            || related.get() == P5RuntimeLimitKey.ROOT_ADMISSIONS_PER_TICK;
            case PENDING_EVENTS_PER_SKILL_INSTANCE,
                    PENDING_EVENTS_PER_ATTRIBUTION,
                    PENDING_EVENTS_PER_SERVER,
                    ACTIVE_SKILL_INSTANCES_PER_ATTRIBUTION,
                    ROOT_ADMISSIONS_PER_TICK,
                    EXECUTIONS_PER_SKILL_INSTANCE_PER_TICK,
                    EXECUTIONS_PER_ATTRIBUTION_PER_TICK,
                    EXECUTIONS_PER_SERVER_PER_TICK,
                    MAXIMUM_DEPTH,
                    DIRECT_CHILDREN_PER_EVENT,
                    ZERO_DELAY_CHILDREN_PER_EVENT,
                    MAXIMUM_DELAY_TICKS,
                    MAXIMUM_DEADLINE_HORIZON_TICKS,
                    CANCELLATIONS_PER_TICK -> false;
        };
    }
}

@SuppressWarnings("serial")
final class P5RuntimeConfigurationException extends RuntimeException {
    private final P5RuntimeConfigurationFailure failure;

    P5RuntimeConfigurationException(P5RuntimeConfigurationFailure failure) {
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    P5RuntimeConfigurationFailure failure() {
        return failure;
    }
}
```

### 47.4 Result-Family Responsibilities

| Family | Sole responsibility | Explicit exclusions |
|---|---|---|
| `RuntimeAdmissionResult` | Typed root admission before root event/instance publication | P6 return, post-claim failure, cancellation, completed execution, unexpected throwable |
| `RuntimeCancellationResult` | Typed result of one cancellation request | Event execution terminal, breaker, expiry, stop-cleanup history |
| `RuntimeExecutionOutcome` | P5-owned terminal result after dispatch observation, including post-port child-plan validation | Root admission, cancellation request, tier defer, global no-peek stop, unexpected throwable |
| `RuntimePortOutcome` | Closed nominal result returned only after P6 port entry | Generic resolution, expiry, cancellation, pending breaker, raw-plan programming fault |
| `RuntimeReferenceResolutionOutcome` | Ephemeral internal loaded-only generic resolution result | P6 gameplay validity, retention, queueing, diagnostics, persistence |

`RuntimeBudgetDecision` owns per-event instance/attribution defer. `RuntimeDrainStopReason` owns
the server equality no-peek stop. Neither is an execution terminal or breaker reason.

### 47.5 Admission Result Family

The admission family has exactly 21 permitted variants. It is the root-admission family; child
plans returned after a P6 call are validated by `SkillRuntimeService` and map to execution
outcomes, not to a second admission call or family.

| Variant | Components | Publication / accounting authority |
|---|---|---|
| `AcceptedMemoryOnly` | `RuntimeEventToken eventToken`, `RuntimeCancellationToken cancellationToken` | Atomic one root event, instance, sequence, EventId, lease, and pending publication |
| `PersistentScheduleUnsupported` | none | Root-attempt debit only; zero publication |
| `DelayOutOfRange` | requested and maximum `int` | Zero publication |
| `DelayOverflow` | none | Zero publication |
| `DeadlineOutOfRange` | requested and maximum `int` | Zero publication |
| `DeadlineOverflow` | none | Zero publication |
| `DeadlineBeforeScheduledTick` | scheduled and deadline `long` | Zero publication |
| `InvalidRuntimeReference` | `RuntimeReferenceFailureReason` | Generic prepublication failure; execution/port `0/0` |
| `SkillRevisionUnavailable` | `SkillRevisionUnavailableReason` | Zero publication; provisional handle closed |
| `InvalidEvent` | `InvalidEventReason` | Zero publication; duplicate UUID uses its exact reason and never retries |
| `OwnerInstanceUnavailable` | none | No new P5 V0 instance-bound seam; zero publication |
| `ActiveLineageCapacityExceeded` | current and maximum `int` | Typed capacity rejection; no breaker or pin leak |
| `ActiveBudgetAttributionCapacityExceeded` | current and maximum `int` | Typed capacity rejection; no breaker |
| `RootAdmissionBudgetExceeded` | maximum `int` | No later schedule or definition work |
| `CircuitBroken` | `RuntimeCircuitBreakerSummary` | Prospective source terminal; zero event/instance/sequence/pin/pending publication |
| `ServerNotRunning` | none | Zero mutation |
| `ServerStopping` | none | Zero new publication |
| `WrongThread` | none | Zero mutation |
| `SequenceExhausted` | `RuntimeSequenceKind` | No wrap or reuse |
| `TickExhausted` | none | Zero publication |
| `KernelFaulted` | none | Admission remains closed until exact Stopped removal |

There is no queue-capacity variant: effective pending overflow is the already fixed circuit-breaker
coordinate. Wrong stable server identity is the existing typed reference reason, not a new root
variant. Root `InvalidEvent` accepts every exact root structural/attribution/identity reason except
`INVALID_NODE_CAPABILITY`; that reason is reserved for the existing post-port child coordinate.

### 47.6 Cancellation Result Family

The cancellation family has exactly 12 permitted variants:

| Variant | Components | Mutation |
|---|---|---|
| `CancelledEvent` | none | Remove one exact queued/deferred event |
| `CancelledSkillInstance` | positive `removedCount` | Remove all queued/deferred work with no frame in flight |
| `CancellationRequested` | nonnegative `removedCount` | Remove descendants, retain current frame, suppress children |
| `InFlight` | none | Exact event is current; no preemption |
| `AlreadyCancelled` | none | Idempotent whole-instance request |
| `NotPending` | none | Executed, expired, cancelled, broken, unknown, or old removed handle; no history |
| `WrongServer` | none | Stale/wrong slot while replacement slot is running; zero mutation |
| `WrongThread` | none | Zero mutation |
| `ServerNotRunning` | none | No exact active slot; zero mutation |
| `ServerStopping` | none | Cleanup only |
| `CancellationBudgetExceeded` | positive `maximum` | No scan or mutation |
| `CancellationTokenInvalid` | `RuntimeCancellationTokenInvalidReason` | Current-slot event owner mismatch |

`Already terminal`, `instance unavailable`, and an unknown old handle are not extra variants; the
no-history authority intentionally folds them into `NotPending`. A prior-slot handle maps to
`WrongServer` only while the replacement exact slot exists.

### 47.7 Execution Outcome Family

The execution family has exactly 15 permitted variants. The three port rejection coordinates
share one component-bearing Java variant.

| Variant | Execution / port | Terminal and child action |
|---|---:|---|
| `Completed` | `1/1` | Current terminal; empty plan |
| `CompletedWithChildren(count)` | `1/1` | Current terminal; exactly `count` children publish atomically |
| `RejectedByExecutionPort(reason)` | `1/1` | Current terminal; zero children |
| `SkillRevisionUnavailable` | `1/0` | Componentless defensive post-claim loss; no latest fallback |
| `SourceMissing(reason)` | `1/0` | Missing/unloaded source; terminal |
| `TargetMissing(reason)` | `1/0` | Missing/unloaded target; terminal |
| `InvalidRuntimeReference(reason)` | `1/0` | Wrong server/dimension/type or unavailable dimension; terminal |
| `InvalidEvent(reason)` | `1/1` | Existing post-port P5 structural child rejection; whole plan rejected |
| `DeadlineExpired(deadline, observed)` | `0/0` | Remove only the expired event |
| `Cancelled` | fixed pre/post-claim count | Suppress children and terminalize |
| `OwnerInstanceUnavailable` | `0/0` | Remove defensive stale-owner work |
| `ServerStopping` | fixed pre/post-claim count | Suppress children and stop-clear |
| `BudgetRejected(reason)` | `1/1` | Whole returned plan rejected; zero children |
| `ScheduleRejected(reason)` | `1/1` | Whole returned plan rejected; reserved IDs remain holes |
| `CircuitBroken(summary)` | `1/1` | Break only source instance; remove same-owner descendants |

`RuntimeExecutionOutcome` excludes execution-budget defer and the global server stop. It also
excludes unexpected `RuntimeException`, `Error`, and OOME.

### 47.8 P6 Port Outcome Family

`RuntimePortOutcome` has exactly `Completed()` and
`Rejected(RuntimePortRejectionReason reason)`. The bounded child plan is the separate, mandatory
second component of `RuntimeExecutionBatch`; it is never nullable. `Completed` may pair with an
empty or nonempty bounded plan. `Rejected` must pair with the empty plan. The production nominal
unavailable port returns `Rejected(PORT_UNAVAILABLE)` plus `RuntimeChildPlan.EMPTY`.

P6 cannot return a generic missing-reference result, deadline result, cancellation result,
breaker, mutable emission buffer, callback, queue owner, or live `RuntimeEvent` component inside
the outcome. P6 receives the event separately and may not retain it or the context.

### 47.9 Child Plan Vocabulary

`RuntimeChildPlan` is a package-private immutable record over an ordered defensive
`List.copyOf` of `RuntimeChildSpec`. `RuntimeChildPlan.EMPTY` is the sole no-child value. The raw
physical maximum is exactly 32. The constructor checks raw size before copying; a null list or
entry is rejected, and raw size 33 throws the fixed
`RuntimeKernelException.Code.CHILD_PLAN_HARD_CAPACITY_EXCEEDED`.

Each child spec contains only node index, delay, deadline-horizon input, stable origin, explicit
optional stable target, child cause, and the closed execution-data value. It cannot carry an
instance ID, sequence, attribution, persistence setter, parent deadline override, callback, live
object, or unbounded payload. Those owner coordinates inherit from the current event. Integers are
accepted by the child-spec constructor so P5 can select the typed schedule/budget result.

A copied plan of size `1..32` above a lower effective direct limit maps to
`BudgetRejected(DIRECT_CHILD_LIMIT_EXCEEDED)`. It never maps to the raw programming fault and never
publishes a prefix.

### 47.10 Skill Revision Unavailable Reasons

The root reason family is exactly:

| Variant | Component | Coordinate |
|---|---|---|
| `DefinitionSubsystemUnavailable` | non-null existing `SkillSubsystemUnavailableReason` | Controlled exact find or pin reports the bounded P3 subsystem unavailable result |
| `ExactRevisionMissing` | none | Exact find is available and empty |
| `RuntimeProjectionUnavailable` | none | Exact immutable document cannot produce the required P3 runtime projection |
| `TransientPinUnavailable` | none | Exact controlled pin is available and empty |

There is no `UNKNOWN`, `OTHER`, invalidated/currentness, wrong-server, latest, equipped, source,
target, expiry, or persistence reason. Post-claim defensive revision loss remains the exact
componentless `RuntimeExecutionOutcome.SkillRevisionUnavailable` because a live lease already owns
the exact immutable projection.

### 47.11 Invalid Event Reasons

`InvalidEventReason` has exactly eight constants:

| Constant | Authorized use |
|---|---|
| `INVALID_NODE_COORDINATE` | Root or child node coordinate fails the fixed P5 structural predicate |
| `INVALID_NODE_CAPABILITY` | Child origin/target/reference shape fails the selected validated node's already-declared static trigger source/target capability requirement; it does not inspect event kind or P6 gameplay validity |
| `INVALID_TRIGGER_CAUSE` | Root or child cause variant/event kind is incompatible with the selected node's declared trigger event-kind set; it does not re-evaluate source/target capability |
| `INVALID_REFERENCE_SHAPE` | Stable origin/target closed shape is invalid, distinct from loaded-only resolution |
| `INVALID_EXECUTION_DATA` | Value is outside the sealed V0 execution-data shape |
| `INVALID_BUDGET_ATTRIBUTION` | Root attribution shape or current-slot binding is invalid |
| `BUDGET_ATTRIBUTION_MISMATCH` | Direct player origin and player attribution identify different players |
| `DUPLICATE_LIVE_SKILL_INSTANCE_ID` | Server-minted candidate UUID collides with a live instance; zero publication and no retry |

The admission `InvalidEvent` constructor admits the seven root reasons other than
`INVALID_NODE_CAPABILITY`. The execution constructor admits only the first five structural reasons.
The three root-only attribution/identity reasons cannot be misused as post-port execution outcomes.
Depth, direct count, zero-delay count, lifetime, and EventId capacity stay in `BudgetRejected`;
delay and deadline stay in `ScheduleRejected`; wrong child stable slot, null batch/pairing, and raw
size 33 stay fixed programming failures. Effect-specific P6 capability remains a port rejection.

### 47.12 Other Dependent Reason / Decision Families

The already closed enum families remain exact and unchanged:

- `RuntimeBudgetDecision`: `EXECUTE` and the three instance/player/non-player defer values.
- `RuntimeCircuitBreakReason`: the four instance/player/non-player/server pending-overflow values.
- `RuntimeReferenceFailureReason`: the seven wrong-server/dimension/missing/unloaded/type values.
- `RuntimePortRejectionReason`: unavailable, effect-specific source, and effect-specific target.
- `RuntimeScheduleRejectionReason`: the five delay/deadline structural values.
- `RuntimeBudgetRejectionReason`: lifetime, depth, direct, zero-delay, and EventId capacity.
- `RuntimeSequenceKind`, `RuntimeDrainStopReason`, `RuntimeTickAdvanceResult`, and
  `RuntimeCancellationTokenInvalidReason` retain their exact existing constants.

The ephemeral resolver outcome is closed as `Resolved`, `SourceMissing`, `TargetMissing`, or
`InvalidRuntimeReference`. Missing variants accept only `MISSING`, `MISSING_OR_UNLOADED`, or
`UNLOADED`; the invalid variant accepts only `WRONG_SERVER`, `DIMENSION_UNAVAILABLE`,
`WRONG_DIMENSION`, or `TYPE_MISMATCH`. An absent optional target resolves successfully to
`NoResolvedRuntimeTarget.INSTANCE`.

### 47.13 Constructor and Factory Invariants

All reference components and `Optional` containers are non-null. Runtime server and instance
sequences are positive. Published EventIds, including a present parent, are positive. The
`RuntimeEvent` compact constructor owns only self-contained facts: node index `0..255`,
nonnegative tick/depth coordinates, `created <= scheduled <= deadline`, child sequence `0..32`,
memory-only persistence, directly comparable cancellation/slot/attribution bindings, direct-player
attribution equality, and exactly one local root or child shape. Constructor misuse is a
programming invariant failure and is not a typed admission result.

`SkillRuntimeService` is the sole named root/child event construction-publication factory. Before
constructing an event it proves the exact immutable definition/node membership, active slot and
owner, reserved EventId range, current-tick schedule derivation, and atomic queue/accounting state.
For a child it additionally proves parent EventId, `depth = parent.depth + 1`, inherited instance
ID and instance sequence, exact skill reference/revision, attribution, memory-only persistence,
deadline upper bound, stable slot binding, and reserved child ordinal. Ordinary caller/P6 invalidity
selects its typed result before construction; a violation of P5-minted cross-object state uses the
fixed kernel invariant path. No second factory owner and no Store/queue/parent component is added
to `RuntimeEvent`.

Range-result constructors prove the result they name. Delay/deadline maxima are `0..12000`; active
server/attribution capacity results require `current == maximum` with maximum `1..128` / `1..32`;
root-attempt and cancellation maxima are `1..64` / `1..128`. Idle whole-instance removal count is
`1..256`; in-flight descendant removal count is `0..255`. `CompletedWithChildren.count` is
`1..32`, and expiry requires `observed > deadline`.

`RuntimeCircuitBreakerSummary` requires a positive effective maximum bounded by its reason hard
cap (`256`, `1024`, or `4096`), `0 <= pendingBefore <= maximum`, request count `1..32`, and
projected overflow computed without arithmetic overflow. Removal is between zero and
pending-before; an in-flight summary requires pending-before at least one and same-source removal
at most the smaller of pending-before minus one and the source-instance hard bound `255`.
Admission summaries exclude the instance reason and require request
`1`, removed `0`, and no in-flight event. Execution summaries require an in-flight event.
`SkillRuntimeService` additionally proves equality to the current slot snapshot and actual
removal accounting.

`RuntimeExecutionBudget` enforces the architecture hard bounds: direct `0..32`, zero-delay
`0..16`, remaining lineage `0..511`, remaining depth `0..32`, delay/deadline `0..12000`, and
in-flight pending headrooms `0..255` / `0..1023` / `0..4095`. It also enforces zero-delay `<=`
direct, direct `<=` remaining lifetime and all three headrooms, zero remaining depth implies zero
direct capacity, and maximum delay `<=` maximum deadline horizon. Remaining depth is not sibling
capacity. The sole service factory proves the exact current snapshot/minimum and EventId headroom.

`P5RuntimeConfigurationFailure` accepts only the twelve ordered relation pairs and three ordered
derivation shapes fixed in Section 28. Reversed or unrelated keys are constructor failures.
`RuntimeExecutionBatch` rejects null and rejected/nonempty pairing with the exact fixed kernel code.
Resolved block positions are defensively copied. `RuntimeExecutionContext` is non-null, has a
nonnegative current runtime tick, and carries the exact node object at its indexed position in the
exact immutable definition.

### 47.14 Absence and Null Policy

Nullable components are zero. Stable optional target and parent absence use the already approved
typed `Optional<RuntimeTarget>` and `Optional<EventId>` containers. Resolved target absence uses
the explicit sealed singleton `NoResolvedRuntimeTarget.INSTANCE`. No children use
`RuntimeChildPlan.EMPTY`. Componentless result variants represent result-state absence.

No absence uses null, `Optional<Object>`, empty string, negative-ID sentinel, or maximum-long
sentinel. There is no nullable plan, reason, summary, context, execution data, or token.

### 47.15 Conversion Boundaries

`SkillRuntimeService` is the sole conversion owner:

| Source | Exact conversion | Forbidden conversion |
|---|---|---|
| Root validation | Directly returns one `RuntimeAdmissionResult` | Never a port outcome |
| Resolver `SourceMissing` / `TargetMissing` / `InvalidRuntimeReference` | Identically named execution outcome with the same reason | Never a P6 rejection |
| Resolver `Resolved` | Constructs the one-call context and enters P6 once | Never queues or retains the helper result |
| Port `Completed` + empty plan | `RuntimeExecutionOutcome.Completed` | Never false child success |
| Port `Completed` + valid nonempty all-published plan | `CompletedWithChildren(count)` | Never publishes a prefix |
| Port `Rejected(reason)` + empty plan | `RejectedByExecutionPort(reason)` | Never maps to generic reference failure |
| Port `Rejected` + nonempty plan | Fixed `INVALID_OUTCOME_PLAN_PAIRING` programming failure | Never typed gameplay rejection |
| Tier budget decision | Execute or unchanged defer | Never an execution outcome or breaker |
| Pending breaker reason | Admission/execution `CircuitBroken(summary)` at its fixed coordinate | Never a defer |

### 47.16 Coordinate-to-Variant Matrix

| Product coordinate | Owning Java value | Execution / port | Publication / action |
|---|---|---:|---|
| Valid memory-only root | `Admission.AcceptedMemoryOnly` | `0/0` | Atomic root publication |
| Duplicate live UUID | `Admission.InvalidEvent(DUPLICATE_LIVE_SKILL_INSTANCE_ID)` | `0/0` | Zero publication; no retry |
| Root revision unavailable | `Admission.SkillRevisionUnavailable(reason)` | `0/0` | Zero publication; provisional close |
| Persistent root | `Admission.PersistentScheduleUnsupported` | `0/0` | Root-attempt debit only |
| Root delay/deadline invalid | Exact range/overflow/relation admission variant | `0/0` | Zero publication |
| Root sequence/tick exhausted | Exact admission exhaustion variant | `0/0` | No wrap or reuse |
| Root pending overflow | `Admission.CircuitBroken(summary)` | `0/0` | Prospective source terminal; zero publication |
| Bounded valid child plan | Port `Completed` then execution `CompletedWithChildren` | `1/1` | Zero-or-all publication |
| Child P5 structural invalidity | `Execution.InvalidEvent(reason)` | `1/1` | Current terminal; zero children |
| Child effective direct/zero/depth/lifetime/EventId limit | `Execution.BudgetRejected(reason)` | `1/1` | Whole plan rejected |
| Child schedule invalid | `Execution.ScheduleRejected(reason)` | `1/1` | Whole plan rejected |
| Raw child list 33 | `RuntimeKernelException(CHILD_PLAN_HARD_CAPACITY_EXCEEDED)` | `1/1` | Programming fault; zero children |
| Null P6 execution batch | `RuntimeKernelException(NULL_EXECUTION_BATCH)` | `1/1` after port return | Programming fault; bounded FAULTED cleanup |
| Cancel instance/event | Exact cancellation variant | not an execution conversion | Bounded eager removal |
| Stale/wrong/old cancellation token | `WrongServer`, `CancellationTokenInvalid`, or `NotPending` | none | Zero mutation unless exact cancellation |
| Cancellation wrong thread / no slot / stopping | `WrongThread`, `ServerNotRunning`, or `ServerStopping` | none | Zero mutation or idempotent lifecycle cleanup only |
| Deadline expired | `Execution.DeadlineExpired` | `0/0` | Remove only event |
| Eligible event-level tiers | `RuntimeBudgetDecision.EXECUTE` | `0/0` before claim | Proceed to the one current/in-flight claim |
| Instance/player/non-player tier exhausted | `RuntimeBudgetDecision` defer | `0/0` | Event unchanged in bounded deferred buffer |
| Server execution equality | `RuntimeDrainStopReason.SERVER_EXECUTION_LIMIT_REACHED` | no event observation | No peek/poll/result |
| Checked runtime-tick advance | `RuntimeTickAdvanceResult.ADVANCED` or `.EXHAUSTED` | `0/0` | Begin drain, or clear before drain without wrap |
| Generic resolver helper return | Exact `RuntimeReferenceResolutionOutcome` variant | `1/0` | Resolve to call-scoped context or map once to the matching execution outcome |
| Generic source/target missing | Resolver then matching execution outcome | `1/0` | Current terminal |
| Wrong server/dimension/type | Resolver `InvalidRuntimeReference`, then matching execution outcome | `1/0` | Current terminal |
| P6 unavailable/effect rejection | Port `Rejected(reason)`, then execution port rejection | `1/1` | Current terminal; zero children |
| P6 success without children | Port `Completed` + empty, then execution `Completed` | `1/1` | Current terminal |
| P6 success with children | Port `Completed` + nonempty, then execution child validation | `1/1` | Atomic children or typed whole-plan rejection |
| Pending child breaker | `Execution.CircuitBroken(summary)` | `1/1` | Break source only; eager descendants cleanup |
| Server stopping | `Execution.ServerStopping` at the fixed pre/post-claim count | fixed | Suppress children and bounded clear |
| Unexpected runtime exception | no result variant | fixed by throw coordinate | Bounded FAULTED cleanup; same identity rethrow |
| `Error` / OOME | no result variant | fixed by throw coordinate | No-intentional-allocation cleanup; same identity rethrow |
| Next-slot configuration load | `P5RuntimeLimitLoadState.Requested`, `.Invalid`, or `.Unavailable.INSTANCE` | not runtime dispatch | Complete closed base configuration value; never an admission/port outcome |

Every architecture coordinate has one Java owner and one mapping. Coordinates without a Java
variant and coordinates with conflicting variants are both zero.

### 47.17 Exception Separation

`RuntimeKernelException` retains exactly one non-null fixed `Code` and uses `code.name()` as its
message. The 20 existing codes remain exact; duplicate UUID does not add a code.
`P5RuntimeConfigurationException` retains exactly one non-null closed configuration failure, fixes
no message identity, and is separate from runtime results.

Unexpected `RuntimeException`, `Error`, OOME, constructor misuse, null batch, invalid pairing, raw
plan overflow, and internal impossible state are not normal result variants. No result carries a
`Throwable`. The existing same-object rethrow, cardinality, bounded cleanup, and Error/OOME rules
remain unchanged.

### 47.18 Public API and Retention Boundary

The V1 delta has zero public top-level types. Retained terminal result, reason, decision, token,
summary, stable event/value, batch, and child-plan graphs contain no `Object`, string-only reason,
callback, `Throwable`, mutable definition, Store/carrier, provider, config object, or live Minecraft
object. They retain only bounded primitives, closed enums/records, immutable IDs/references,
explicit absence, and the bounded child plan.

The already authorized resolved-reference records, ephemeral
`RuntimeReferenceResolutionOutcome.Resolved` wrapper, and execution context record are the sole
live-object exception: they are package-private, synchronous, one-call-scoped, never queued,
diagnosed, persisted, or returned as a terminal result, and must be dropped in `finally`. Their
exact closed origin/target variants add no P6 gameplay flag or resolution policy. P6 may not retain
a context.

### 47.19 Compile-Probe Result

Repository-external Java 21 evidence records:

```text
positive declarations compile = PASS
exhaustive no-default switches = PASS
compile-negative expected failures = 8/8
runtime-negative invariant rejections = 2/2
unexpected successful negative compile = 0
negative compile class output = 0
invariant harness marker = P5_A_V1_VOCABULARY_PROBE_PASS
public top-level runtime result types = 0
retained result/value/plan live Minecraft dependencies = 0
unexpected callback dependencies = 0
Throwable result components = 0
```

The full skeleton intentionally contains the exact live Minecraft dependencies only in the
call-scoped resolver/context exception above; the separately enumerated retained set contains none.

The two runtime negatives are intentionally not misreported as compiler checks: Java accepts a
null reference expression at compile time and cannot encode list cardinality 32 in the generic
type system. Their constructors reject null and raw size 33 at execution.

### 47.20 Implementation Authority

The later P5 implementation must use these exact package names, top-level visibility, kinds,
permits sets, variant names, record components, constructor/factory boundaries, absence values,
and conversions. It may split package-private declarations among source files and choose private
owner/helper storage names, but may not add a variant, widen visibility, use a generic payload,
change an invariant into a normal result, or choose a different conversion.

`SkillRuntimeService` remains the sole queue/scheduler/resolution/conversion owner. This amendment
does not implement P5, P6, a gameplay action, a persistent schedule, a second owner, or a new
public seam.

### 47.21 Phase Status

This documentation amendment is complete only when its own unique exact-SHA, attempt-1 Build run
contains exactly the six existing jobs and all six complete successfully. That same condition
makes P5 implementation ready but does not start or complete it. There is no P5 split.

### 47.22 Provenance

The compile-closure scan and probes are ordinary repository-external evidence, not product
authority or a required runtime dependency:

```text
canonical base commit = 193e23a672b613f8d333993dfde2d42f9843488e
canonical base tree = ae775617d90795dd27f5998498383995f3464a98
architecture prefix bytes = 110200
architecture prefix lines = 1701
architecture prefix SHA-256 = ea0ecb6271c1e640a95fa8c9539280caed528838ad33a09f9196daa64fbf1287
evidence root = /private/tmp/gramarye-p5-a-v1-vocabulary-evidence-20260827T083906Z
probe root = /private/tmp/gramarye-p5-a-v1-vocabulary-probe-20260827T083906Z
```

The terminal phase state recorded by this amendment is:

```text
P4-E
= COMPLETE

P5-A consolidated architecture record /
review closure
= COMPLETE

P5-A-V1 COMPILE-CLOSED RUNTIME RESULT
VOCABULARY AMENDMENT
= COMPLETE UPON THIS COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  SIX-JOB REMOTE GATE PASS

P5 split
= NO SPLIT

P5 implementation
= READY UPON THE SAME CONDITION;
  NOT STARTED

P6
= NOT STARTED
```

## 48. P5-A-V2 Corrected Termination-Safe Production-Packaged GameTest Wrong-Thread Authority Amendment

### 48.1 Status and Scope

This strict EOF append-only, documentation-only amendment promotes the completed P5-A-V2.2
design verdict into repository authority. The unique chosen model is **C1 — SHARED
PRODUCT-OWNED PURE THREAD-PRECONDITION SEAM**. C1 is the only passing model; no failed candidate
is retained as an implementation alternative.

This amendment becomes complete only when this amendment commit's unique exact-SHA, attempt-1
`Build` workflow run contains exactly the six existing jobs and all six complete successfully.
That condition authorizes the later C1 source/test correction and synchronized P4B2B Gate work;
it does not perform either change here. This round changes no Java, test, script, Gradle file,
workflow, resource, codex-spec, GameTest topology, runtime-kernel behavior, or public API.

### 48.2 Evidence Baselines and Historical Limitation

The current prospective V2.2 baseline is fixed as:

```text
P5_A_V2_2_CURRENT_BUNDLE_IDENTITY_V1
= 7b10bb9788a571acd437a94f65f4a71263daecf3a75336d12848333a1ccd62ea

P5_A_V2_2_EVIDENCE_CURRENT_IDENTITY_V1
= 476f66fcbdb3cc94d5718f8b6b801c6410d8f482c80f043ccb2f12ccc63306c9

P5_A_V2_2_PROBE_TREE_IDENTITY_V1
= 5d52d041e9b0a947810bb72838dc60d59e29e9e0342c948859dbae274bdae58b
```

The authority inputs are the V2 original review evidence, the V2 current evidence seal, the V2.1
worker/timeout review, the V2.2 termination-safe harness review, its compile probe, and the V2.2
current evidence/probe companion seal. The exact V2.2 surface, current-baseline call graphs, two
wrong-thread control coordinates, future six-path delta, future P4B2B Gate, and future test plan
are incorporated below.

The original V2.2 POSTFLIGHT did not seal its own final identity, the complete exact-22 evidence
projection, or the complete recursive probe tree. V2.2 completion-time evidence and probe
identities therefore remain **UNAVAILABLE**. The current companion is a prospective baseline from
the state observed at seal creation time; it does not reconstruct or certify the historical
completion-time bytes. The V2.2 design verdict remains `COMPLETE — PASS`, and the chosen C1 model
and candidate decisions remain unchanged.

### 48.3 Historical Failure Closure

The candidate closure is exact and final:

```text
Candidate A
= FAIL — moving the control outside the production JAR requires an unapproved GameTest topology

B1
= FAIL — raw-worker timeout escape has no hard bound

B2
= FAIL — interrupt plus a bounded second wait cannot guarantee termination

B3
= FAIL — synchronous execution of the exact operation destroys the wrong-thread controls

C2
= FAIL — raw worker still has no hard terminal and Control 2 moves live authority before its gate

C3
= FAIL — the platform provides no hard-contained task primitive

C4
= FAIL — a child JVM tests a surrogate and cannot obtain the exact live identity

C1
= PASS — UNIQUE CHOSEN MODEL
```

Candidate A and B1/B2/B3 remain historical failures and are not reopened. C2/C3/C4 remain failed
and are not implementation alternatives.

### 48.4 Defective Work-Order Withdrawal

The following prior instruction is recorded only to withdraw it:

```text
WRONG_THREAD
→ no move
→ no consume
→ no clear

= WITHDRAWN
= DEFECTIVE WORK-ORDER TEXT
= NON-AUTHORITATIVE
= NEVER REPOSITORY AUTHORITY
```

It is replaced by the active authority:

```text
thread classification
→ closed Decision
→ existing single-use claim transition
→ consumed = true
→ move / clear into bounded local ownership
→ bounded cleanup or allowed audit continuation
```

`ProductThreadPrecondition` is pure, total, nonblocking, and side-effect-free. It does not consume
capture authority. The existing single-use claim lifecycle owns consumption. A `WRONG_THREAD`
decision consumes the one-shot capture, performs bounded cleanup, and reports the exact mismatch;
the capture is never reusable after that attempt.

### 48.5 V2.2 Chosen C1 Model

C1 introduces one package-private, product-owned classifier shared by both exact product controls
and by their existing production-packaged GameTest holder. Product wrappers observe only the
current live Thread ID and delegate to package-private same-owner cores. GameTest supplies the
synthetic impossible live Thread ID `0L` to those same cores; it does not use a copied classifier,
mock operation, worker, task, process, or alternative product path.

The two controls preserve exactly:

1. `SkillDefinitionStoreService.latestReference` maps rejection to
   `SkillSubsystemLifecycleException.Code.WRONG_THREAD`.
2. `P4E1GroupedStoreAudit.audit` maps rejection to
   `BindingException("P4E1_GROUPED_AUDIT_THREAD_MISMATCH")` and preserves the immediate
   `P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED` follow-up.

Normal product runtime reachability to the holder remains zero; GameTest route reachability remains
one; registration and normal GameTest count remain unchanged at 12. Worker/task/process terminal
liveness and retained runtime authority are structurally zero.

### 48.6 ProductThreadPrecondition Exact Java Surface

The exact surface identity is `0644`, 3430 bytes, SHA-256
`57a632e9419207e2b7cc96a7ddd614ee9a33f238dfeaa9909e260e8f4085e119`. The exact future placement
is `src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java`.
The following block is copied byte-for-byte from the sealed surface evidence:

```java
package com.yo1no.gramarye.magic.definition.store;

/**
 * Exact future package-private, product-owned, pure thread-precondition surface.
 * This top-level class is final and has no public or protected surface.
 */
final class ProductThreadPrecondition {
    private ProductThreadPrecondition() {
    }

    static Decision classify(long expectedLogicThreadId, long observedThreadId) {
        return expectedLogicThreadId > 0L && expectedLogicThreadId == observedThreadId
                ? Decision.ALLOWED
                : Decision.WRONG_THREAD;
    }

    enum Decision {
        ALLOWED,
        WRONG_THREAD
    }
}

/*
Exact placement:
  src/main/java/com/yo1no/gramarye/magic/definition/store/
  ProductThreadPrecondition.java

Constructor/factory invariant:
  no instance is constructible outside the class; there is no factory and no retained state.

Parameter/return invariant:
  both parameters are primitive long values; nullable parameters = 0; Object payload = 0;
  callback = 0; Throwable = 0. A non-positive expectedLogicThreadId fails closed.

Exact product operation 1 binding:
  public latestReference(server, skillId)
  -> package-private latestReference(server, skillId, Thread.currentThread().threadId())
  -> requireServerThread(server, observedThreadId)
  -> ProductThreadPrecondition.classify(
         server.getRunningThread().threadId(), observedThreadId)
  -> WRONG_THREAD maps in SkillDefinitionStoreService to
     new SkillSubsystemLifecycleException(Code.WRONG_THREAD)
  -> ALLOWED enters installedAdapter marker/cache access.

Exact product operation 2 binding:
  audit(capture)
  -> package-private audit(capture, Thread.currentThread().threadId())
  -> compute the closed observed-thread decision before capture mutation with
     ProductThreadPrecondition.classify(
         serverIdentity.getRunningThread().threadId(), observedThreadId)
  -> carry Decision into Captured.claim(this, decision)
  -> Claimed.requireActive(this, decision)
  -> requireCaptureBinding(..., decision)
  -> retain the existing exact-server, owner, captured-creation-Thread reference,
     and same-tick binding checks; MinecraftServer.serverThread is final and the
     audit owner was constructed only after the same-server-thread precondition
  -> WRONG_THREAD maps in P4E1GroupedStoreAudit to
     new BindingException("P4E1_GROUPED_AUDIT_THREAD_MISMATCH")
     after existing consume/move/clear and failed-claim cleanup
  -> ALLOWED continues to same-tick validation and audit.

Exact GameTest call shape:
  long expectedThreadId = server.getRunningThread().threadId();
  helper.assertTrue(
      ProductThreadPrecondition.classify(expectedThreadId, expectedThreadId)
          == ProductThreadPrecondition.Decision.ALLOWED,
      "shared product thread gate must allow the server logic thread");
  helper.assertTrue(
      ProductThreadPrecondition.classify(expectedThreadId, 0L)
          == ProductThreadPrecondition.Decision.WRONG_THREAD,
      "shared product thread gate must reject the synthetic no-thread observation");
  isolated.latestReference(server, skillId, 0L);
  exactThreadOwner.audit(wrongThreadCapture, 0L);

The two exact typed exceptions are caught only by their exact exception types for assertion.
No Thread object is constructed, no Thread is started, and no Thread ID is stored in a field,
result, queue, diagnostic, persistent object, or callback. Public top-level type delta = 0.
*/
```

The binary is `com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition`; the method is
package-private static `classify` with descriptor
`(JJ)Lcom/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition$Decision;`. The class is
package-private and final; results are exactly `ALLOWED` and `WRONG_THREAD`. There is no public,
nullable, boolean-shortcut, `Object`, callback, `Throwable`, retained `Thread`, extra enum value,
default result, or unknown result surface.

### 48.7 Thread-ID Authority and C1 Retention Scope

A live Java Thread ID is positive, unique during the live-thread lifetime, and stable during that
lifetime. Product expected identity is the live positive server-thread ID from
`MinecraftServer.getRunningThread().threadId()`. Product observed identity is obtained only from
`Thread.currentThread().threadId()`. GameTest's synthetic wrong observation is `0L`; it is an
impossible live Thread ID and must not appear in the product observed-thread path. A non-positive
expected ID fails closed to `WRONG_THREAD`.

The no-retention rule is limited to the new C1 observed-thread seam. In that seam, the observed
`Thread` exists only within the synchronous call scope, observed identity is a method-local
primitive `long`, and field/result/queue/diagnostic/static retention is zero.
`ProductThreadPrecondition` retains no `Thread` object or observed identity.

Existing P4E1 `creationThreadIdentity`, binding metadata, and thread-binding fields remain governed
by existing P4E1 authority and are preserved. C1 neither removes nor changes their types or
meaning. This is not a repository-wide prohibition on Thread-related binding fields.

### 48.8 Closed Decision Injection and Handoff Boundary

External callers and GameTest may not inject a precomputed `Decision`, directly inject `ALLOWED`
or `WRONG_THREAD`, bypass `classify` with a boolean or Decision, or use a copied/mock helper to
manufacture a Decision. A classification result may not be externally rewritten or escape into a
field, queue, diagnostic, or static state.

GameTest may directly assert the result returned by `classify`; it may not inject that asserted
Decision into either product core or the claim path. Both product-core negative controls supply
only primitive observed ID `0L` and let the product classifier create the Decision.

`ProductThreadPrecondition.classify(...)` must produce the closed Decision. That exact Decision is
then preserved and carried within the same synchronous product call chain. Control 2 must carry it
through the existing single-use claim chain after classification. The internal Decision is neither
recomputed nor rewritten. Prohibiting externally supplied precomputed Decisions does not prohibit
the required internal handoff of the classifier-produced closed Decision.

### 48.9 Product Control 1 Corrected Contract

Control 1 owns these exact future coordinates:

```text
path = src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java
binary = com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService
public wrapper = latestReference
public descriptor = (Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/magic/api/id/SkillId;)Lcom/yo1no/gramarye/magic/definition/store/SkillSubsystemResult;
core descriptor = (Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/magic/api/id/SkillId;J)Lcom/yo1no/gramarye/magic/definition/store/SkillSubsystemResult;
observed binding = Thread.currentThread().threadId()
expected binding = server.getRunningThread().threadId()
wrong mapping = SkillSubsystemLifecycleException.Code.WRONG_THREAD
```

The active order is exact:

```text
public latestReference wrapper
→ package-private core
→ requireServerThread(server, observedThreadId)
→ ProductThreadPrecondition.classify(expectedThreadId, observedThreadId)
→ exact WRONG_THREAD mapping or ALLOWED continuation
→ installedAdapter
→ installedServers.containsKey(server) as first effective authority access
```

Before classification, installed-server marker/cache access, adapter lookup, Store access, journal
access, world access, mutation, allocation, and blocking are all zero. The wrong result maps to the
existing `SkillSubsystemLifecycleException.Code.WRONG_THREAD`.

`PRODUCT_THREAD_GATE_CALL_GRAPH.tsv` records the pre-amendment current baseline, including its
gate-order defect. It is historical observation, not the active future sequence. The sequence above
is controlled by the exact surface and `P4B2B_FUTURE_GATE_CONTRACT.md`.

The sealed current-baseline graph is reproduced exactly for historical correlation:

```tsv
operation	method_descriptor	entry_coordinate	pre_gate_instructions_calls	thread_gate_coordinate	gate_dependency	wrong_thread_result_construction	post_gate_first_call	side_effect_before_gate	allocation_before_gate	blocking_before_gate	authority_access_before_gate
SkillDefinitionStoreService.latestReference	(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/magic/api/id/SkillId;)Lcom/yo1no/gramarye/magic/definition/store/SkillSubsystemResult;	SkillDefinitionStoreService.java:108-112;BCI0	latestReference invokes installedAdapter;installedAdapter immediately invokes requireServerThread;requireServerThread performs Objects.requireNonNull	SkillDefinitionStoreService.java:534-538;requireServerThread BCI8-21	MinecraftServer.isSameThread;platform implementation compares Thread.currentThread()==getRunningThread()	lifecycle(Code.WRONG_THREAD) creates exact SkillSubsystemLifecycleException at source537/BCI15-21	IdentityHashMap.containsKey at installedAdapter source513/BCI4-12	0	0 on pre-gate success path	0	server reference/null validation only;isSameThread is gate dependency
P4E1GroupedStoreAudit.audit	(Lcom/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture$Captured;)Lcom/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit$Result;	P4E1GroupedStoreAudit.java:26-30;BCI0	Objects.requireNonNull(capture);Captured.claim calls requireUnconsumed,writes consumed=true,allocates Claimed,moves and clears capture refs;Claimed.requireActive validates active/owner before requireCaptureBinding	P4E1GroupedStoreAudit.java:36-40;requireCaptureBinding BCI23-62 reached through Captured.claim BCI187-220 and Claimed.requireActive BCI47-63	creationThreadIdentity;captured thread identity;Thread.currentThread;MinecraftServer.isSameThread	new BindingException(P4E1_GROUPED_AUDIT_THREAD_MISMATCH) at source39/BCI53-62;failed-claim finally discards moved authority	creationTick comparison then MinecraftServer.getTickCount at source41/BCI63-79	YES: Captured.consumed=true plus move/clear before gate;mechanically factorable by first classifying and carrying closed access decision into same claim path	YES: Claimed allocation before gate	0	YES: capture live-authority fields are read/moved before gate;no world/disk/network API invoked
```

### 48.10 Product Control 2 Consume-on-Mismatch Contract

Control 2 owns these exact future coordinates:

```text
path = src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.java
binary = com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit
wrapper = audit
wrapper descriptor = (Lcom/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture$Captured;)Lcom/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit$Result;
core descriptor = (Lcom/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture$Captured;J)Lcom/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit$Result;
observed binding = Thread.currentThread().threadId()
expected binding = serverIdentity.getRunningThread().threadId()
wrong mapping = BindingException("P4E1_GROUPED_AUDIT_THREAD_MISMATCH")
follow-up = P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED
```

The only authorized sequence is:

1. Read only bounded immutable thread-binding coordinates. The ordinary wrapper binds
   `observedThreadId` from `Thread.currentThread().threadId()`; GameTest binds only primitive `0L`.
2. In the core, call `ProductThreadPrecondition.classify(expectedLiveServerThreadId,
   observedThreadId)` before capture mutation.
3. Preserve that classifier-produced closed Decision.
4. Enter the existing single-use claim transition.
5. Set `consumed = true`.
6. Move and clear live capture authority into existing bounded local ownership and cleanup.
7. For `WRONG_THREAD`, execute existing failed-claim bounded cleanup, perform zero grouped-audit
   product work and zero Store/journal truth reads, establish no second claim, then throw the exact
   `BindingException("P4E1_GROUPED_AUDIT_THREAD_MISMATCH")`.
8. For `ALLOWED`, use the moved claimed state and continue the existing same-tick audit.
9. Any reuse of that capture must produce `P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED`.

Classification occurs before live-authority move/clear, but classification does not preserve
capture reusability. The classifier does not consume the capture; the existing one-shot claim
lifecycle does.

### 48.11 Claim Classification and Single-Use Handoff

The exact claim chain is:

```text
read bounded immutable binding coordinates
→ classify(expectedThreadId, observedThreadId)
→ receive one closed Decision
→ Captured.claim(this, decision)
→ consumed = true
→ move / clear into bounded local ownership
→ Claimed.requireActive(this, decision)
→ requireCaptureBinding(..., decision)
→ WRONG_THREAD: failed-claim discard then exact mismatch
→ ALLOWED: existing grouped-audit continuation
```

Existing exact owner, exact server, captured creation-Thread reference, and same-tick checks remain
unchanged. The claim state machine and cleanup ownership remain unchanged. Moving, clearing, or
consuming live claim payload before classification; recomputing the Decision; adding a second claim;
performing product audit work on `WRONG_THREAD`; or removing the immediate
`P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED` result is forbidden.

### 48.12 GameTest Zero-Worker Negative-Control Model

`SkillSavedDataLifecycleGameTests` remains in the main source set, remains included in the
production JAR, remains normally unreachable from product runtime, and retains the existing
GameTest registration and count of 12. Its future role is exactly
`PRODUCTION_PACKAGED_GAMETEST_THREAD_GATE_NEGATIVE_CONTROL`.

The two sealed current control coordinates are reproduced exactly as
`PRE-AMENDMENT HISTORICAL OBSERVATION`; every worker mechanism named in these rows is
`NON-AUTHORITATIVE` for the future harness:

```tsv
control_id	source_path	harness_method	callsite_coordinate	operation_owner	operation_method	operation_descriptor	arguments	captured_runtime_authority	expected_typed_result	assertion_coordinate	thread_precondition_coordinate	first_post_gate_interaction	test_purpose
P4_B2_A_STORE_WRONG_THREAD	src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java	startupInstalledExactReadyAdapterInOverworldCache(Lnet/minecraft/gametest/framework/GameTestHelper;)V	source:96-101;bytecode:BCI426-439	com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService	latestReference	(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/magic/api/id/SkillId;)Lcom/yo1no/gramarye/magic/definition/store/SkillSubsystemResult;	server;SkillId(UUID(0,0xB2D))	worker captures SkillDefinitionStoreService isolated and MinecraftServer server;outer worker also captures Runnable and AtomicReference<Throwable>	SkillSubsystemLifecycleException with code WRONG_THREAD	source:102-106;bytecode:BCI441-475	SkillDefinitionStoreService.java:511-538;installedAdapter BCI0-4;requireServerThread BCI0-21	installedServers.containsKey(server) at source513/BCI4-12	Prove service access rejects wrong thread before marker/cache/world/Store inspection;worker scheduling and timeout are not product semantics.
P4_E1_GROUPED_AUDIT_WRONG_THREAD	src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java	exerciseB2AGroupedAuditLifecycle(Lnet/minecraft/gametest/framework/GameTestHelper;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/world/level/storage/DimensionDataStorage;Lcom/yo1no/gramarye/magic/definition/store/GramaryeSkillSavedData;Lnet/minecraft/world/level/saveddata/SavedData$Factory;)V	source:269-271;bytecode:BCI428-443	com.yo1no.gramarye.magic.definition.store.P4E1GroupedStoreAudit	audit	(Lcom/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture$Captured;)Lcom/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit$Result;	wrongThreadCapture	worker captures P4E1GroupedStoreAudit exactThreadOwner and P4E1GlobalSourceCapture.Captured wrongThreadCapture;capture retains server,thread,player-list,Store/journal/inventory/directory/integrated witnesses,claims,sources,selected-files,summary;outer worker captures Runnable and AtomicReference<Throwable>	P4E1GroupedStoreAudit.BindingException with message P4E1_GROUPED_AUDIT_THREAD_MISMATCH	source:272-280;bytecode:BCI445-501 including follow-up P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED	P4E1GroupedStoreAudit.java:26-40 via Captured.claim:722-768 and Claimed.requireActive:1038-1046;requireCaptureBinding BCI23-62	allowed path next reads tick identity then server.getTickCount at source41/BCI63-79	Prove grouped audit rejects a distinct observed thread and that the one-shot capture is consumed/cleared on failed claim;worker scheduling/blocking/timeout are not product semantics but cross-thread capture rejection plus consumption is.
```

The C1 harness removes raw `Thread`, `AtomicReference<Throwable>`, `join(5000)`, `isAlive`,
`interrupt`, timeout, worker lambda, worker-captured runtime authority, and `catch(Throwable)`.
The same classifier directly proves the live-ID allowed case and synthetic-`0L` wrong case. The
same two package-private product cores are then invoked with `0L`, and only the two exact exception
types are caught for assertion.

The dynamic controls must prove the exact Control 1 mapping, the exact Control 2 mismatch, and the
immediate Control 2 `P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED` result. Worker/task/process creation is
`0/0/0`; timeout, Throwable handoff, captured async runtime authority, and method-terminal live
execution are all zero. The proof type is `MECHANICAL_STRUCTURAL_ZERO`.

### 48.13 Dynamic and Static Proof Composition

Dynamic GameTest and the static/API Gate are jointly required to preserve the original exact product
invariant. Dynamic proof uses the same classifier for `ALLOWED` and `WRONG_THREAD`, synthetic `0L`,
both exact typed product mappings, and the Control 2 consumed follow-up, with no worker, timeout, or
generic Throwable catch.

Static/source/bytecode proof locks both actual operations to that same classifier, product observed
identity to `Thread.currentThread().threadId()`, Control 1 classification before first effective
authority access, Control 2 classification before move/clear while preserving consume-on-mismatch,
and the absence of a duplicate comparison or boolean bypass. It proves GameTest and product refer
to the same binary type and method. Neither proof component is sufficient alone.

### 48.14 Method-Terminal Structural-Zero Invariant

At every future harness terminal:

```text
worker alive = 0
task alive = 0
process alive = 0
captured live runtime authority = 0
captured Throwable = 0
AtomicReference<Throwable> = absent
background product work = 0
proof = MECHANICAL_STRUCTURAL_ZERO
```

This terminal invariant concerns async/worker capture and method-terminal retention; it does not
deny the bounded method-local capture used synchronously by Control 2. The harness uses no
interrupt, join, liveness poll, timeout, daemon setting, `Future.cancel`, executor shutdown, or
process termination to manufacture this result.

### 48.15 Throwable and Error-Catch Boundary

The existing product failure policy remains exact:

```text
PRODUCT_PRIMARY_FAILURE = 6
PRODUCT_SECONDARY_CLEANUP_FAILURE = 5
primary same caught-object rethrow = 6/6
secondary same-secondary rethrow = 0/5 by design
secondary eventual original-primary preservation = 5/5
production catch(Throwable) = 0
production-packaged GameTest catch(Throwable) = 0
production-packaged GameTest worker creation = 0
unexpected Error/Throwable catches = 0
exact GameTest thread-negative controls = 2
```

No file, package, or class-name wildcard may exclude a catch from this policy.

### 48.16 Future P4B2B Exact Gate

The future P4B2B Gate must preserve the product failure policy in Section 48.15 and lock all of the
following by repository-relative path, binary owner, method descriptor, shared-gate descriptor,
callsite/control-flow shape, source/bytecode digest, and typed mapping. Line numbers or filename,
package, or class wildcards are insufficient.

The Gate's exact path allowlist is the six rows reproduced in Section 48.17. No filename or
package wildcard may substitute for, extend, or reduce that exact allowlist.

#### 48.16.1 Shared Gate

```text
path = src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java
binary = com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition
method = classify
descriptor = (JJ)Lcom/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition$Decision;
visibility = package-private static
top-level kind = final class
result variants = ALLOWED, WRONG_THREAD
type/method count = exact 1
actual product callsites = exact 2
GameTest controls = exact 2
product observed provenance = Thread.currentThread().threadId()
GameTest synthetic observed = 0L
external/precomputed Decision injection = 0
internal classifier-produced Decision handoff = required for Control 2
boolean bypass = 0
duplicate comparison helper = 0
```

The classifier has one closed positive-ID/equality classification and no callback, `Object`,
nullable component, `Throwable`, field mutation, allocation on invocation, blocking call, or
external dependency.

#### 48.16.2 Control 1

The Gate locks the exact Control 1 paths, owners, wrapper/core descriptors, and caller allowlist in
Section 48.9. The wrapper delegates directly to the core, the core reaches the shared gate before
marker/cache/world/Store access, authority access before gate is zero, the observed binding is the
current Thread ID, and the typed result is `SkillSubsystemLifecycleException.Code.WRONG_THREAD`.
The only core callers are the public wrapper and exact GameTest control.

#### 48.16.3 Control 2

The Gate locks the exact Control 2 paths, owners, wrapper/core descriptors, and caller allowlist in
Sections 48.10–48.11. It proves classification before move/clear, required consume-on-mismatch,
move/clear only after classification, required internal closed-Decision handoff, zero product audit
work on `WRONG_THREAD`, the exact mismatch result, and the subsequent
`P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED` result. Existing owner/server/captured creation-Thread
reference/same-tick checks remain. The only core callers are the wrapper and exact GameTest control.
The locked platform source must also prove that `MinecraftServer.serverThread` is final and is the
value returned by `getRunningThread()`.

#### 48.16.4 GameTest and Topology Absence

The Gate proves the holder's production-JAR membership, GameTest route reachability exactly one,
normal runtime reachability zero, count 12, registration delta zero, and synthetic observation
constant `0L`. It proves exact two core calls, exact typed mappings, direct allowed/wrong classifier
assertions, and zero `runOffThread`, `new Thread`, `Thread.start`, `AtomicReference`, `join`,
`isAlive`, worker name, timeout, executor, `Future`, task, child process, or `catch(Throwable)`.
New public top-level types, public gate members, source sets, Gradle tasks, workflows, GameTest
holders, and timeout policies are zero. Static/source plus bytecode proof is mandatory.

### 48.17 Future Six-Path Source Delta

The future semantic delta is copied exactly from the V2.2 TSV:

```tsv
path	owner	required_semantic_delta	why_needed	product_semantics_changed	public_API_delta	test_only_product
src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java	shared P4 product thread gate	Add package-private final pure classify(JJ)->Decision surface with positive expected-ID fail-closed rule.	Single product-owned comparison used by both exact operations and GameTest.	0	0	product
src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java	SkillDefinitionStoreService	Public latestReference binds current threadId to package-private core; core uses shared gate before installedAdapter access and preserves exact exception mapping.	Makes exact operation directly testable without worker or copied gate.	0	0	product
src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.java	P4E1GroupedStoreAudit	One-arg audit binds current threadId to package-private core; core classifies first and carries Decision into binding mapping.	Preserves exact operation and moves first-effective gate before capture mutation.	0	0	product
src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture.java	P4E1GlobalSourceCapture	Carry non-null closed Decision through Captured.claim and initial Claimed.requireActive into requireCaptureBinding while preserving consume/move/clear/failure cleanup.	Preserves the second control's one-shot consumed-after-mismatch purpose.	0	0	product
src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java	production-packaged GameTest holder	Remove runOffThread/worker/AtomicReference/timeout/catch(Throwable); directly assert shared gate and call two exact package-private operation cores with 0L.	Eliminates all executing-worker escape while preserving both controls and count.	0	0	test-only route in production source set
src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java	P4B2B static/API Gate	Replace stale helper-catch allowance with exact shared-gate descriptors, caller/provenance/mapping/claim-chain/no-worker/no-catch/no-topology assertions.	Mechanically locks production use of the same gate and prevents surrogate/bypass drift.	0	0	test-only
```

Product semantics, public top-level API, build topology, GameTest count, and Gradle/workflow deltas
are zero. Existing P4E1 binding identity fields and Control 2 single-use semantics are preserved.
This amendment does not declare the final implementation path count; the next round must
mechanically union this semantic set with the preserved exact-17 candidate.

### 48.18 Future Test and Qualification Plan

The future correction and fresh qualification must prove all of the following:

1. `ProductThreadPrecondition` returns `ALLOWED` for the same positive live expected/observed ID.
2. It returns `WRONG_THREAD` for the live expected ID and synthetic `0L`.
3. A non-positive expected ID fails closed.
4. Control 1 actual callsite is gate-first.
5. Control 2 classifies before move/clear.
6. Control 2 `WRONG_THREAD` still consumes the one-shot capture.
7. Reuse immediately returns `P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED`.
8. Product observed identity comes only from `Thread.currentThread().threadId()`.
9. Synthetic `0L` exists only in GameTest.
10. External/precomputed Decision injection is rejected.
11. Internal classifier-produced Decision handoff is accepted and required.
12. Control 1 retains the exact typed mapping.
13. Control 2 retains the exact typed mapping.
14. Worker creation remains zero.
15. `AtomicReference<Throwable>` remains zero.
16. `catch(Throwable)` remains zero.
17. Timeout, join, `isAlive`, and interrupt remain zero.
18. No duplicate expected/observed comparison exists.
19. Existing P4E1 `creationThreadIdentity` and binding fields are preserved.
20. Public top-level API delta remains zero.
21. GameTest registration and count remain unchanged at 12.
22. Normal runtime reachability to the holder remains zero.
23. Product primary/secondary policy remains exact 6/5.
24. Focused P5 tests pass.
25. Full ordinary unit inventory continuity is preserved.
26. GameTest result is 12/12.
27. Dedicated-server smoke passes.
28. Warning, production-JAR membership, and static/API Gates pass.

Fresh qualification must start as a new continuation campaign and rerun the complete required
sequence. It must not resume midway from a historical failed attempt.

### 48.19 Public API / Retention / Build Boundary

The gate class, nested Decision, and both operation-core overloads are package-private. Public
top-level API delta is zero. No new source set, Gradle task, workflow job, GameTest holder, timeout
policy, heap policy, build topology, or qualification route is authorized. GameTest registration,
template, timeout ticks, count, and normal-runtime reachability remain unchanged.

The new C1 seam introduces no retained Thread, observed identity, Decision, Throwable, or runtime
authority. Existing P4E1 binding identities remain preserved under their existing authority.

### 48.20 Supersession

The V2 Candidate A failure and historical STOP remain historical record. V2.1 B1/B2/B3 failures
remain historical record. V2.2 C1 is the unique safe-model authority. V2.2 completion-time
evidence/probe identities remain unavailable; the current companion provides only the prospective
cryptographic baseline.

The defective no-consume instruction in Section 48.4 was never repository authority. C1 supersedes
only the raw-worker GameTest harness, timeout/Throwable handoff, and future catch-boundary Gate.
It does not change the P5 runtime kernel, product Error primary/secondary policy, P4 persistence or
audit semantics, existing P4E1 binding identity metadata, Control 2 one-shot semantics, GameTest
registration, normal runtime reachability, or P6.

### 48.21 Implementation Authority

```text
C1 six-path source correction
= AUTHORIZED UPON THIS AMENDMENT COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  SIX-JOB REMOTE GATE PASS

P4B2B stale Gate synchronization
= AUTHORIZED UPON THE SAME CONDITION

P5 fresh qualification
= MAY RESUME ONLY AFTER
  THE C1 SOURCE / TEST CORRECTION

P5 implementation commit
= NOT AUTHORIZED BY THIS DOCS-ONLY ROUND
```

This block does not claim that source correction is complete, P4B2B is synchronized,
qualification has passed, or P5 implementation has passed.

### 48.22 Phase Status

The terminal phase state is:

```text
P4-E
= COMPLETE

P5-A consolidated architecture record
= COMPLETE

P5-A-V1
= COMPLETE

P5-A-V2 current evidence seal
= COMPLETE

P5-A-V2.1
= COMPLETE — HISTORICAL STOP;
  B1 / B2 / B3 FAILURES CLOSED

P5-A-V2.2
= COMPLETE — PASS

P5-A-V2.2 current evidence /
compile-probe companion seal
= COMPLETE

P5-A-V2 CORRECTED GAMETEST HARNESS /
TERMINATION-SAFE WRONG-THREAD
AUTHORITY AMENDMENT
= COMPLETE UPON THIS COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  SIX-JOB REMOTE GATE PASS

Chosen model
= C1

Control 2 single-use semantics
= CONSUME-ON-MISMATCH PRESERVED

P5 split
= NO SPLIT

C1 exact source correction
= READY UPON THE SAME CONDITION;
  NOT STARTED

P4B2B stale Gate synchronization
= READY UPON THE SAME CONDITION;
  NOT STARTED

P5 fresh qualification
= BLOCKED UNTIL C1 SOURCE /
  TEST CORRECTION

P5
= INCOMPLETE

P6
= NOT STARTED
```

### 48.23 Provenance

The external evidence coordinates are code text and are not repository dependencies:

```text
V2 original evidence root = /private/tmp/gramarye-p5-a-v2-gametest-throwable-review-20260828T050145Z
V2 current seal root = /private/tmp/gramarye-p5-a-v2-current-evidence-seal-20260828T075104Z
V2.1 evidence root = /private/tmp/gramarye-p5-a-v2-1-worker-timeout-review-20260828T082604Z
V2.2 evidence root = /private/tmp/gramarye-p5-a-v2-2-termination-safe-harness-review-20260828T091141Z
V2.2 compile probe root = /private/tmp/gramarye-p5-a-v2-2-harness-probe-20260828T091141Z
V2.2 current evidence/probe companion seal root = /private/tmp/gramarye-p5-a-v2-2-current-evidence-probe-seal-20260828T103650Z
canonical base SHA = 1adcede8f4efb69295b57f3f091736f210318280
architecture prefix bytes = 186829
architecture prefix lines = 3411
architecture prefix SHA-256 = 3c03b38353376d84b0fe4aa896df971c7446f5470d0763625d094bad505b7bde
```

external review evidence is historical provenance; this corrected architecture amendment is the
repository authority.

## 49. P5-A-V2.3 C1 Closed-Decision Claim-Handoff and Exact-10 Continuation Scope Authority Correction

### 49.1 Status and Scope

This strict EOF append-only, documentation-only amendment corrects the implementation-continuation
scope of Section 48. It closes the exact future `Captured.claim` and initial
`Claimed.requireActive` signatures needed to carry one classifier-produced closed `Decision`, and
it replaces the insufficient future six-path semantic delta with an exact ten-path delta. The C1
product model, the `ProductThreadPrecondition` surface, and all runtime semantics remain unchanged.

This amendment becomes complete only when this amendment commit's unique exact-SHA, attempt-1
`Build` workflow run contains exactly the six existing jobs and all six complete successfully.
Until that condition holds, the additional four paths are not implementation authority. This
round changes no Java, test, script, Gradle file, workflow, resource, codex-spec, GameTest topology,
runtime-kernel behavior, or public API, and it does not resume C1 implementation or qualification.

### 49.2 Source-Correction Stop Record

The first C1 source-correction continuation created a detached worktree from
`e94a229b44ea3309139dffb4627b421d2dd55fe0`, migrated the preserved exact seventeen paths with
`17 / 17` mode, byte, and SHA agreement, and then stopped before any C1 source edit. Its actual
repository delta remains the migrated seventeen paths: six tracked modifications and eleven
untracked new files. All six paths originally authorized for C1 remain unchanged or absent relative
to that worktree's base, its index is empty, and `git diff --check` passes.

The stop was required and correct. The exact-six authority omitted one stale API Gate whose current
reflection contract conflicts with the required closed-Decision claim signature, and it omitted
three active continuation verifiers whose exact path allowlists reject the corrected candidate.
Adding a seventh path ad hoc, retaining the obsolete signature, adding an overload, or weakening a
verifier would all have violated the then-active authority.

The formal-failed implementation, the old exact-seventeen candidate, the stopped source-correction
candidate, the failed-campaign companion, and all V2.2 evidence and probe bytes remain immutable.

### 49.3 Exact Blockers

The current `P4E1GlobalSourceCapture.Captured` declares exactly one package-private `claim` method.
It returns `P4E1GlobalSourceCapture.Claimed` and accepts only the exact
`P4E1GroupedStoreAudit` owner. The current descriptor is:

```text
(Lcom/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit;)Lcom/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture$Claimed;
```

The current `P4E1GlobalSourceCapture.Claimed` declares exactly one private `requireActive` method.
It returns `void` and accepts only the same exact owner. The current descriptor is:

```text
(Lcom/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit;)V
```

`P4E1B1ApiGateTest.capturedHandoffIsBoundOnlyToTheExactGroupedAuditOwner` currently requires one
declared `claim`, rejects a zero-argument variant, and requires the sole parameter list to be exactly
`[P4E1GroupedStoreAudit]`. Replacing the sole signature therefore requires synchronizing that test;
adding a second signature is forbidden.

The active continuation verifier chain is the existing
`verifyP4B2Configuration -> verifyP4C2Configuration -> verifyP4D3Configuration ->
verifyP4E3Configuration` chain, with `verifyP4A3BConfiguration` retained as the first prerequisite.
After the exact-seventeen migration, P4-C2 and P4-D3 reject the new
`ProductThreadPrecondition.java`; P4-E3 additionally rejects the exact C1 production and stale-test
paths enumerated below. These are exact-token allowlist defects, not authority to add a wildcard.

### 49.4 Claim-Handoff Java Surface

The following block is signature-only Java declaration notation. It freezes the exact method names,
nesting, visibility, return types, parameter types, parameter order, and absence of an explicit
throws clause; it deliberately does not freeze either method body or add an overload:

```java
// P4E1GlobalSourceCapture.Captured
Claimed claim(
        P4E1GroupedStoreAudit owner,
        ProductThreadPrecondition.Decision decision);

// P4E1GlobalSourceCapture.Claimed
private void requireActive(
        P4E1GroupedStoreAudit owner,
        ProductThreadPrecondition.Decision decision);
```

The exact future descriptors are:

```text
Captured.claim:
(Lcom/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit;Lcom/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition$Decision;)Lcom/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture$Claimed;

Claimed.requireActive:
(Lcom/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit;Lcom/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition$Decision;)V
```

These surfaces are unique. The sole existing signature is replaced in each owner. Existing
visibility, return type, first parameter, nesting, and no-explicit-throws behavior are preserved.
The `Decision` parameter is non-null and must be rejected explicitly if an internal programming
error supplies null; null is not a bypass and is not an externally injectable result.

### 49.5 Exactly-One Claim and No-Overload Contract

The future `Captured` contract is exact:

```text
declared methods named claim = 1
claim overloads = 0
claim parameter count = 2
claim parameter 1 = P4E1GroupedStoreAudit
claim parameter 2 = ProductThreadPrecondition.Decision
claim return = P4E1GlobalSourceCapture.Claimed
claim visibility = package-private
```

The future `Claimed` contract is also exact:

```text
relevant declared methods named requireActive = 1
requireActive overloads = 0
requireActive parameter count = 2
requireActive parameter 1 = P4E1GroupedStoreAudit
requireActive parameter 2 = ProductThreadPrecondition.Decision
requireActive return = void
requireActive visibility = private
```

The implementation may rename later post-claim active-owner checks when needed to keep the initial
closed-Decision handoff unique. It may not retain the obsolete one-parameter `requireActive`, create
a second `requireActive`, store a `Decision`, or manufacture `Decision.ALLOWED` for later access.

### 49.6 Closed Decision Identity and Handoff

The exact synchronous identity chain is:

```text
ProductThreadPrecondition.classify(...)
-> one method-local, non-null decision
-> capture.claim(this, decision)
-> moved.requireActive(owner, decision)
-> owner.requireCaptureBinding(..., decision)
```

The same local value produced by the one classification is passed at each edge. Reclassification,
replacement, mutation, boolean substitution, `Object` substitution, field or static retention,
queue or diagnostic retention, cross-thread escape, cross-tick escape, and a second claim are all
zero. Source and bytecode Gates must prove the callsites and descriptors, not infer identity from a
count alone.

No public API exposes either method or the nested `Decision`. Package-external callers cannot
inject it. GameTest supplies only the primitive synthetic observed thread ID `0L` to the authorized
package-private operation core and may directly assert the classifier result; it may not call
`claim(..., Decision.WRONG_THREAD)` or otherwise inject a precomputed result.

### 49.7 Control 2 Consume-on-Mismatch Preservation

The only authorized Control 2 order remains:

```text
read bounded immutable binding coordinates
-> classify expected live thread ID and observed thread ID
-> preserve one closed Decision
-> Captured.claim(owner, decision)
-> consumed = true
-> move and clear into bounded local ownership
-> Claimed.requireActive(owner, decision)
-> existing exact owner/server/creation-Thread/same-tick binding validation
-> bounded cleanup or allowed audit continuation
```

`WRONG_THREAD` consumes the one-shot capture, performs existing failed-claim bounded cleanup,
performs zero grouped-audit product work and zero Store or journal truth reads, and throws the exact
`BindingException("P4E1_GROUPED_AUDIT_THREAD_MISMATCH")`. The same capture is not reusable; its
immediate subsequent use must produce `P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED`. The claim state
machine, cleanup ownership, creation-thread identity, server identity, tick identity, Store truth,
journal truth, and audit result semantics are unchanged.

### 49.8 P4E1B1ApiGateTest Synchronization Contract

The future change to
`src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java` is narrowly limited
to synchronizing the claim-handoff contract. It must preserve all unrelated assertions and prove:

1. `Captured` declares exactly one `claim`, with no overload;
2. its parameters are exactly owner then `ProductThreadPrecondition.Decision`;
3. its package-private visibility and `Claimed` return are unchanged;
4. `Claimed` declares exactly one private `void requireActive` with the same two parameters;
5. the same classifier-produced local is passed through `claim` and `requireActive` into binding;
6. no boolean, `Object`, nullable, duplicate-test, public, package-external, or GameTest injection
   route exists; and
7. no `Decision` field, static, queue, callback, or diagnostic retention exists.

The Gate must combine reflection with exact source and bytecode callsite proof. It may not relax
other P4E1 B1 surface, ownership, construction, static-retention, or zero-argument rejection rules.

### 49.9 Verifier Stale-Consumer Audit

An exhaustive audit covered the canonical and stopped continuation source under `src/test`,
`scripts`, `build.gradle`, the active workflow, and active verifier inventory. Java consumers were
classified using comment, string, character-literal, and text-block-aware source reasoning;
shell consumers were classified from exact case-arm tokens and rejection loops. The exact
additional semantic-path set outside the original C1 six is:

| Path | Stale coordinate | Required future delta |
| --- | --- | --- |
| `src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java` | current claim reflection contract | synchronize the sole owner-only signature to owner plus Decision and lock the same initial handoff |
| `scripts/verify-p4-c2-b-configuration.sh` | production path case arm and rejection loops | add the one exact ProductThreadPrecondition path |
| `scripts/verify-p4-d3-configuration.sh` | production path case arm and rejection loops | add the one exact ProductThreadPrecondition path |
| `scripts/verify-p4-e3-configuration.sh` | exact repository path case arm and rejection loop | add five exact C10 paths currently rejected by the migrated verifier |

There is no fifth additional direct consumer. `build.gradle` and `.github/workflows/build.yml` have
no stale C1 path inventory. Other historical phase verifier allowlists are not in the active C1
continuation verifier chain and already reject portions of the preserved M17 migration itself;
they are not additional C1 semantic consumers. The direct claim call and internal initial
`requireActive` call are already owned by original-C6 production paths.

### 49.10 P4-C2 Verifier Contract

After M17 migration, `verify-p4-c2-b-configuration.sh` has an exact 57-path production allowlist.
The future correction adds only:

```text
src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java
```

The continuation allowlist becomes exact 58. For provenance, the un-migrated canonical script has
51 entries; that historical base count is not the continuation contract. Unknown production-path
rejection, resource freeze, regular-file handling, source-set separation, and every other verifier
assertion remain unchanged. Package, directory, and `src/main/java/**` wildcards remain forbidden.

### 49.11 P4-D3 Verifier Contract

After M17 migration, `verify-p4-d3-configuration.sh` has an exact 57-path production allowlist.
The future correction adds only:

```text
src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java
```

The continuation allowlist becomes exact 58. The un-migrated canonical script has 51 entries and
is provenance only. Unknown production-path rejection, resource and probe separation, symlink and
regular-file checks, P4-D authority assertions, and every unrelated verifier behavior remain
unchanged. Wildcard or blanket exemptions are zero.

### 49.12 P4-E3 Verifier Contract

After M17 migration, `verify-p4-e3-configuration.sh` has an exact 45-path repository allowlist.
The future correction adds exactly these five currently rejected paths:

```text
src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture.java
src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.java
src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java
src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java
src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java
```

The first four are rejected original-C6 production paths; the fifth is the newly authorized stale
API Gate. The continuation allowlist and its diagnostic count become exact 50. The un-migrated
canonical script has 30 entries and would become 35 under the same token delta, but that base count
is not the continuation contract. `SkillDefinitionStoreService.java` and `P4B2BApiGateTest.java`
are already accepted after M17 migration and are not added again.

Unknown-path rejection, resource and documentation freeze, probe and GameTest inventories,
production/test separation, regular-file and symlink checks, P4-E authority assertions, and all
unrelated verifier behavior remain unchanged. Wildcard count and blanket exemptions remain zero.
Five new tokens inside this one already-counted verifier file do not create an eleventh semantic
path.

### 49.13 Original C1 Exact-Six Scope

The original six rows are copied from the sealed V2.2 `FUTURE_SOURCE_DELTA.tsv` path set:

| Path | Corrected category | Original semantic role |
| --- | --- | --- |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java` | `NEW_PRODUCTION` | package-private pure shared classifier |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java` | `MODIFIED_PRODUCTION` | Control 1 wrapper/core and gate-first order |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.java` | `MODIFIED_PRODUCTION` | Control 2 wrapper/core and pre-claim classification |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture.java` | `MODIFIED_PRODUCTION` | same-Decision claim handoff and one-shot cleanup |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java` | `MODIFIED_PRODUCTION` | production-packaged zero-worker GameTest holder |
| `src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java` | `MODIFIED_EXISTING_TEST` | exact C1 static/API and failure-policy Gate |

The original set has six unique rows: one new production file, four modified production files, and
one modified existing test. Its sorted-path-LF SHA-256 is
`6dc4050450f652a920ced4443520bf767618da2af0541c2a4f7af147a4322ed8`.

### 49.14 Additional Exact-Four Scope

The exhaustive audit authorizes exactly four additional semantic paths:

| Path | Category | Exact purpose |
| --- | --- | --- |
| `src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java` | `MODIFIED_EXISTING_TEST` | synchronize exact-one owner-plus-Decision claim and requireActive surfaces |
| `scripts/verify-p4-c2-b-configuration.sh` | `MODIFIED_EXISTING_VERIFIER` | add exact ProductThreadPrecondition production path |
| `scripts/verify-p4-d3-configuration.sh` | `MODIFIED_EXISTING_VERIFIER` | add exact ProductThreadPrecondition production path |
| `scripts/verify-p4-e3-configuration.sh` | `MODIFIED_EXISTING_VERIFIER` | add exact five rejected C10 repository paths and synchronize the exact count |

This set has four unique rows and sorted-path-LF SHA-256
`9c1f705a1f2c3af096bc04cbea2679811353ddbba03b307f37ab95a0f50cdb53`.
It does not authorize any other test or verifier change.

### 49.15 Corrected C1 Exact-Ten Scope

The corrected C1 semantic delta is the union of Sections 49.13 and 49.14, sorted here by repository
path:

| Path | Category | Scope source |
| --- | --- | --- |
| `scripts/verify-p4-c2-b-configuration.sh` | `MODIFIED_EXISTING_VERIFIER` | `V2_3_ADDITIONAL_SCOPE_CORRECTION` |
| `scripts/verify-p4-d3-configuration.sh` | `MODIFIED_EXISTING_VERIFIER` | `V2_3_ADDITIONAL_SCOPE_CORRECTION` |
| `scripts/verify-p4-e3-configuration.sh` | `MODIFIED_EXISTING_VERIFIER` | `V2_3_ADDITIONAL_SCOPE_CORRECTION` |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture.java` | `MODIFIED_PRODUCTION` | `V2_2_ORIGINAL_C1_SCOPE` |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.java` | `MODIFIED_PRODUCTION` | `V2_2_ORIGINAL_C1_SCOPE` |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java` | `NEW_PRODUCTION` | `V2_2_ORIGINAL_C1_SCOPE` |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java` | `MODIFIED_PRODUCTION` | `V2_2_ORIGINAL_C1_SCOPE` |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java` | `MODIFIED_PRODUCTION` | `V2_2_ORIGINAL_C1_SCOPE` |
| `src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java` | `MODIFIED_EXISTING_TEST` | `V2_2_ORIGINAL_C1_SCOPE` |
| `src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java` | `MODIFIED_EXISTING_TEST` | `V2_3_ADDITIONAL_SCOPE_CORRECTION` |

The inventory is exact: ten rows, ten unique paths, six original rows, four additional rows, zero
duplicate, missing, or extra paths. Category counts are
`NEW_PRODUCTION = 1`, `MODIFIED_PRODUCTION = 4`, `MODIFIED_EXISTING_TEST = 2`, and
`MODIFIED_EXISTING_VERIFIER = 3`. Its sorted-path-LF SHA-256 is
`e9e7298e4018f510e5cb2c9c76a274ef0b07cca927506d59067ef0f3ce60fa75`.

Within C1 implementation-continuation scope, this exact-ten inventory supersedes the exact-six
inventory in Section 48.17. It does not supersede the V2.2 product surface or product semantics.

### 49.16 M/C Overlap and Continuation Arithmetic

The continuation sets were computed from actual sealed path inventories, not from expected counts:

```text
M = preserved exact-17 migration scope
C = corrected exact-10 C1 semantic scope
O = M intersection C
U = M union C

|M| = 17
|C| = 10
|O| = 3
|U| = 24
24 = 17 + 10 - 3
```

The exact overlap is:

```text
scripts/verify-p4-c2-b-configuration.sh
scripts/verify-p4-d3-configuration.sh
scripts/verify-p4-e3-configuration.sh
```

The sorted-path-LF SHA-256 identities are:

```text
M17 = 0b6d9a37d1c5c337115f115fd72ca10b961c545cd7f9b76eccf7f32e805a71b3
C10 = e9e7298e4018f510e5cb2c9c76a274ef0b07cca927506d59067ef0f3ce60fa75
O3  = 6e968ecdf5af2e206fcda0a125c0da2650bcee1c8b0eec05d004d6d2f0587583
U24 = 43cf3ebfd505a18aeea9bfaa0619c042136b0df71ef956b4fa708a92b8dfc426
```

These values are continuation arithmetic, not a permanent product path count. The future source
round must recompute them from the then-current sealed M17 and C10 inputs before migration or edit.

### 49.17 Unchanged Product Semantics

```text
P5 runtime semantics changed = 0
P4 persistence semantics changed = 0
P4E1 single-use capture semantics changed = 0
Control 2 consume-on-mismatch changed = 0
P4E1 binding identity fields changed = 0
C1 ProductThreadPrecondition surface changed = 0
P6 semantics changed = 0
GameTest topology changed = 0
public top-level API delta = 0
```

This amendment supplies only the compile-closed claim-handoff surface and exact stale
test/verifier continuation scope. It does not change Error handling, the P5 result vocabulary,
budget, breaker, queue, deadline, persistence, configuration, or any execution semantics.

### 49.18 Implementation Authority

```text
Corrected C1 semantic delta
= EXACT 10 PATHS

The additional exact-four paths
= AUTHORIZED UPON THIS COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  SIX-JOB REMOTE GATE PASS

C1 exact source/test/verifier correction
= READY UPON THE SAME CONDITION;
  NOT STARTED

Final implementation union
= MUST BE MECHANICALLY RECOMPUTED
  FROM M17 AND C10

P5 formal qualification
= BLOCKED UNTIL CORRECTED
  SOURCE/TEST/VERIFIER BYTES ARE FROZEN

P5 implementation commit
= NOT AUTHORIZED BY THIS DOCS-ONLY ROUND
```

No source correction, candidate freeze, qualification, implementation commit, or P5 closure is
claimed by this authority correction.

### 49.19 Phase Status

```text
P4-E
= COMPLETE

P5-A consolidated architecture record
= COMPLETE

P5-A-V1
= COMPLETE

P5-A-V2 corrected authority amendment
= COMPLETE

P5-A-V2.3 C1 CLOSED-DECISION
CLAIM-HANDOFF / EXACT-10 SCOPE
AUTHORITY CORRECTION
= COMPLETE UPON THIS COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  SIX-JOB REMOTE GATE PASS

Chosen model
= C1

Control 2 single-use semantics
= CONSUME-ON-MISMATCH PRESERVED

Corrected C1 semantic delta
= EXACT 10 PATHS

C1 source/test/verifier correction
= READY UPON THE SAME CONDITION;
  NOT STARTED

Corrected candidate source freeze
= NOT CREATED

P5 fresh qualification
= BLOCKED UNTIL CORRECTED
  CANDIDATE FREEZE

P5
= INCOMPLETE

P6
= NOT STARTED
```

This phase state does not declare `P5 implementation PASS`, `P5 READY TO COMMIT`, `P5 COMPLETE`,
or `P6 READY`.

### 49.20 Provenance

The external evidence coordinates are provenance and are not repository dependencies:

```text
canonical base SHA = e94a229b44ea3309139dffb4627b421d2dd55fe0
canonical base tree = 19fd60f574aa375e5f24ba77cc26c3078124c0ea
architecture prefix bytes = 224044
architecture prefix lines = 4098
architecture prefix SHA-256 = 089a052e18eeb932969c01eb2a2a3e2ec9b2e8f05c77bbffd2d9245327a20044
V2.2 current bundle identity = 7b10bb9788a571acd437a94f65f4a71263daecf3a75336d12848333a1ccd62ea
V2.2 evidence projection = 476f66fcbdb3cc94d5718f8b6b801c6410d8f482c80f043ccb2f12ccc63306c9
V2.2 probe projection = 5d52d041e9b0a947810bb72838dc60d59e29e9e0342c948859dbae274bdae58b
V2.3 ordinary evidence root = /private/tmp/gramarye-p5-a-v2-3-scope-amendment-evidence-20260829T050516Z
V2.3 backup root = /private/tmp/gramarye-p5-a-v2-3-scope-amendment-20260829T050516Z-backup
stopped source-correction worktree = /private/tmp/gramarye-p5-c1-source-correction-20260828T155947Z
```

The V2.2 evidence is historical input. Upon the exact remote condition in Section 49.1, this V2.3
append becomes the active repository authority for the corrected C1 continuation scope.

## 50. P5-A-V2.4-R1 P3D3 Thread-Surface Gate and P4-E3 Exact-51 Paired Synchronization Authority Correction

### 50.1 Status and Scope

This section is a docs-only, strict EOF append to the active P5-A, V2, and V2.3 authority. It
corrects the future synchronization contract discovered after the frozen U24 source correction
and formal Q1 failure. It authorizes no Java, test, script, Gradle, workflow, resource, or
codex-spec byte in this round. Its paired subject is exactly:

```text
outside-C10 additional stale path
= src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3ApiGateTest.java

inside-C10 second-generation stale path
= scripts/verify-p4-e3-configuration.sh
```

The sets remain `C11 = C10 + exact P3D3 A1` and `U25 = M17 union C11`. P4-E3 remains one existing
C10 path and is not a twelfth C1 path. This section becomes implementation authority only upon
this documentation commit's unique exact-SHA Build run, attempt 1, completing all six jobs
successfully. Until that condition, both source synchronizations are ready and not started.

### 50.2 Formal Q1 Failure Record

The preserved formal Q1 campaign ran once with no retry. Focused compile, affected JUnit
`97/97`, P5 focused `70/70`, P4B2B `9/9`, P4E1B1 `7/7`, hard-limit digest
`994d53cb0c302785`, verifier syntax `4/4`, and verifier matrix `5/5` all passed. The full-unit
inventory was exactly 1562 IDs and did not drift. Execution ended with 1561 passing tests and one
failure:

```text
com.yo1no.gramarye.magic.definition.store.P3D3ApiGateTest
#storePackageContainsNoP4RuntimeDiscoveryOrDestructiveSurface()

ProductThreadPrecondition.java contains Thread; expected false but was true
```

The failure is a stale baseline static/API Gate. It is not a product runtime failure. GameTest,
dedicated smoke, warning compile, JAR, and later formal static Gates were not executed. The failed
candidate and its exact 16-file partial evidence remain immutable historical input; this round
does not resume or rerun that campaign.

### 50.3 Initial V2.4 Stop Record

The initial V2.4 review stopped before repository modification, Gradle, staging, commit, or push.
Its exact nine-file partial evidence and detached clean documentation worktree are preserved. The
success-only evidence files absent at the stop were not reconstructed. The first work order
combined three incompatible requirements:

1. make the canonical tracked P3D3 test the sole future changed path outside frozen U24;
2. preserve every frozen U24 path byte-for-byte; and
3. require the frozen-U24 P4-E3 exact-scope verifier to accept that new tracked test delta.

The frozen verifier contains an exact 50-path predicate and rejects P3D3. Therefore the test
correction and verifier pass require a paired delta. The initial byte-preservation contract was
defective and has no future authority.

### 50.4 Defective Byte-Preservation Contract Withdrawal

The historical all-frozen-path byte-preservation requirement is withdrawn. The replacement is the
closed `23 + 1 + 1` model:

```text
F24 = frozen U24 exact 24-path set
R1  = { scripts/verify-p4-e3-configuration.sh }
A1  = { src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3ApiGateTest.java }

F24 minus R1 = exact 23 paths with frozen mode, bytes, and SHA-256 preserved
R1            = exact one frozen path receiving the narrow 50-to-51 revision
A1            = exact one canonical tracked path receiving the thread-surface Gate correction
U25           = F24 union A1 = exact 25 paths
```

No third relative-U24 byte-delta path is authorized. P4-E3 stays inside C11, O3, and U25; its
second-generation revision changes no set cardinality.

### 50.5 Two-Dimensional Stale-Consumer Model

The exhaustive scan covered `src/test`, `scripts`, `build.gradle`, `.github/workflows`,
`docs/architecture`, and `docs/codex-spec`, including the required P3D3, C1 thread-identity,
P4-E3 scope, U24, C10, and background-execution search families. Sixty-seven relevant unique
paths were classified. The closed result is:

| Dimension | Exact count | Exact path | C11 effect | Relative-U24 byte delta |
| --- | ---: | --- | --- | --- |
| `OUTSIDE_C10_ADDITIONAL_PATH` | 1 | `src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3ApiGateTest.java` | add exact A1 | yes |
| `INSIDE_C10_SECOND_GENERATION_DELTA` | 1 | `scripts/verify-p4-e3-configuration.sh` | retain existing C10 row | yes |
| `NOT_STALE` | 65 | all other audited paths | none | no |

The audit-scope intersection with C10 is exactly the C2-B, D3, and E3 verifier scripts plus
`P4B2BApiGateTest.java` and `P4E1B1ApiGateTest.java`. Only E3 is stale. No second path exists in
either stale dimension, and the exact future byte-delta set has cardinality two.

### 50.6 Exact P3D3 Blocker

The target is the existing test method at annotation line 431 and declaration line 432:

```text
binary owner = com.yo1no.gramarye.magic.definition.store.P3D3ApiGateTest
method = storePackageContainsNoP4RuntimeDiscoveryOrDestructiveSurface
descriptor = ()V
scan setup = source lines 433-446
broad token = source line 444, "Thread"
assertion = source line 450
```

The method scans sanitized production sources under the store package, masks the exact reviewed
D1 Store integration slice, and applies 37 case-sensitive substring tokens. The broad token
rejects the C1-authorized type at the first path
`src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java`. Its
executable matches are the type at line 7, private constructor at line 8, type and primitive
parameter names at line 11, and primitive comparisons at line 12. None creates, owns, starts,
schedules, retains, or returns a Thread object.

The P3D3 source has canonical Git blob `9ad51e6d91e5bea2de0c714ccd3c34a3eff74843` and SHA-256
`704f437d7add5a072584905b14e277fbac71fbdce94af50b618e38c0869524d9`. It has zero U24 manifest
rows and is the exact additional tracked A1. The C1 product source is frozen at 3430 bytes and
SHA-256 `57a632e9419207e2b7cc96a7ddd614ee9a33f238dfeaa9909e260e8f4085e119`.
Changing production names to evade the test would violate active C1 authority.

### 50.7 Store-Package Thread-Surface Taxonomy

The frozen U24 store package was inspected with a comment-, string-, character-literal-, and
text-block-aware linear lexical scan and cross-checked against preserved classfiles, descriptors,
instructions, and constant pools. The evidence inventory aggregates exact source and bytecode
coordinates into these closed roles:

```text
AUTHORIZED_C1_TYPE_NAME
AUTHORIZED_C1_OBSERVED_THREAD_ID
AUTHORIZED_C1_EXPECTED_SERVER_THREAD_ID
AUTHORIZED_EXISTING_P4E1_BINDING_IDENTITY
FORBIDDEN_THREAD_CREATION
FORBIDDEN_THREAD_LIFECYCLE
FORBIDDEN_THREAD_RETENTION
FORBIDDEN_BACKGROUND_EXECUTION
UNRELATED_IDENTIFIER_SUBSTRING
NON_EXECUTABLE_COMMENT_OR_STRING
```

The scan covers 114 Java files and 331 lexical candidates. It classifies 234 executable tokens:
228 authorized Thread-related identifiers across 158 lines and 12 files, plus 6 resolved unrelated
identifier substrings. It separately classifies 97 non-executable tokens: 69 in comments and 28 in
strings, with zero character-literal or text-block candidates. The coordinate inventory has 53
aggregate rows: 5 C1 type-name rows / 19 tokens, 5 observed-ID rows / 32 tokens, 4 expected-ID rows /
13 tokens, 11 existing P4E1 binding rows / 164 tokens, 6 unrelated rows / 6 tokens, and 22
non-executable rows / 97 tokens.

Fourteen preserved store binaries reference `java/lang/Thread`; their owner-set SHA-256 is
`536a3ac214f07a7dc147bcb5392774fb682479775bb791a939fc50b4faa08658`. ThreadLocal, ThreadGroup,
ThreadFactory, executor, future/completion, fork-join, parallel, and lifecycle-method bytecode
symbols are zero. The 114-path set SHA-256 is
`9ce0d3ddad81bd5526f9272c895abe37e8eb9d8c09036193d8b5e375b313099c`; the authorized
Thread-executable 12-path set is
`a1cbc78c7da7a2d946c3b6c17f8d2fece42f7eaebab97020040314cdbb4b4522`. The line-independent C1
semantic projection is `8385132680bd372a65da7725a33ad47f1d1a93bf9d29d25fe0a2bf917609b9ec`, and the existing-binding
projection is `f278dad69c7f52687ad5744a6fa5f3ebf26ef9b1a818ae2520e2e60ee1df25f0`.

Actual unauthorized creation, lifecycle, retention, and background-execution coordinates are zero.
Unclassified executable coordinates and raw-substring-only classifications are zero.

### 50.8 Authorized C1 Thread-Identity Surface

The exact C1 type surface is:

```text
source/binary type count = 1
type = com.yo1no.gramarye.magic.definition.store.ProductThreadPrecondition
visibility = package-private
shape = final class; private constructor; no retained state
classify descriptor =
(JJ)Lcom/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition$Decision;
Decision constants = ALLOWED, WRONG_THREAD
```

Control 1 binds the public `latestReference(server, skillId)` wrapper to the exact package-private
core with `Thread.currentThread().threadId()`, then compares that primitive observation with
`server.getRunningThread().threadId()` through `ProductThreadPrecondition.classify`. The exact
future source coordinates are `SkillDefinitionStoreService.java:111,117-118,549,551-554`.

Control 2 binds `audit(capture)` to `audit(capture, long)` at
`P4E1GroupedStoreAudit.java:27,30,32-33`, preserving one method-local `Decision` before claim. The
expected ID is the exact `serverIdentity.getRunningThread().threadId()` expression, and the
observed ID is the primitive wrapper/core parameter. The same local continues through the exact
owner-plus-Decision claim and `requireActive` descriptors already frozen by Section 49.

The production-packaged GameTest holder uses only its exact synchronous server-thread
preconditions, one local expected ID, the direct `ALLOWED`/`WRONG_THREAD` classifier assertions,
and primitive `0L` negative-control calls. Its exact reviewed families are
`SkillSavedDataLifecycleGameTests.java:28-37,104-118,206,273-294,339`.

Existing P4E1 binding identities remain authorized only at the exact frozen owners, fields,
constructors, move/clear operations, currentness predicates, and callsites inventoried for
`ControlledSkillPin`, `P4E1AuditedCapture`, `P4E1GlobalSourceCapture`,
`P4E1GroupedStoreAudit`, `P4E1IntegratedSnapshotTraversal`,
`P4E2OnlineReconciliationCoordinator`,
`SkillDefinitionStoreService`, `SkillDefinitionStoreSubmissionPort`,
`SkillRetentionRootAuditService`, `SkillSavedDataLifecycleGameTests`, and
`SkillSubmissionRecoveryGameTests`. This is a finite preservation of existing P4 ownership checks,
not permission for a new Thread coordinate.

Every executable allowance key is the tuple:

```text
repository-relative path
+ binary owner
+ method descriptor
+ resolved symbol
+ closed source role
+ authority family
+ normalized-expression digest
```

Line numbers are diagnostics and never the sole key. A new or moved executable coordinate must
fail closed until a later authority explicitly supplies its complete key.

### 50.9 Forbidden Thread-Lifecycle and Async Surface

The future Gate must prove all of the following remain zero throughout its exact scan scope:

```text
new Thread
Thread.start / join / sleep / interrupt / isAlive
ThreadLocal / InheritableThreadLocal
ThreadGroup / ThreadFactory
Executor / ExecutorService / Executors
ScheduledExecutorService
Future / FutureTask
CompletableFuture / CompletionStage
ForkJoinPool / ForkJoinTask
parallelStream / parallel
Thread object field / array / collection / return retention outside exact preserved P4E1 bindings
```

It must also reject an unexpected executable `Thread`-related coordinate, worker, callback,
queue, scheduler, task, retained diagnostic, or cross-thread/cross-tick escape. Resolved domain
identifiers such as `FUTURE_SCHEMA_VERSION` and the synchronous `AtomicInteger` GameTest counter
are classified by owner and descriptor; they do not weaken the concurrency prohibitions.

### 50.10 Exact P3D3 Future Gate

The future correction edits only the existing
`src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3ApiGateTest.java`, within the existing
test method, private constants, and private lexical helpers needed by that method. It creates no
helper file and no new test. The original exact source-root selection, runtime-discovery checks,
destructive Store protections, and the other 36 forbidden source tokens remain active.

The replacement decision is symbol- and role-aware. It recognizes only the exact C1 and existing
P4E1 keys in Section 50.8, preserves the zero inventories in Section 50.9, and fails on every
unclassified executable coordinate. The source type and binary type each occur exactly once, the
classifier descriptor is exact, and the enum constants are exactly `ALLOWED` and `WRONG_THREAD`.

```text
unexpected executable Thread-related coordinates = 0
unclassified coordinates = 0
wildcard entries = 0
whole-file exemptions = 0
package or directory blanket entries = 0
count-only exemptions = 0
new test IDs = 0
```

The correction is test-only. Product bytes, product names, the consume-on-mismatch sequence, and
existing binding fields are not adapted to satisfy this Gate.

### 50.11 P3D3 Test-ID Continuity

The base and U24 inventories contain the same exact eleven P3D3 IDs. The future U25 correction
must retain these method names, annotations, and IDs exactly:

```text
constructionScannerRecognizesQualifiedImportedWildcardAndReferenceSpellings
finalArchitectureLedgerRecordsP4MigrationAndDirtyObligations
persistenceSnapshotShapeStillContainsNoPinRootOrReportState
reclaimConstructionPointsAreCentralizedAtTheStoreBoundary
reclaimFailureResultAndReportHaveOnlyBoundedTypedComponents
reclaimSourceKeepsFixedPrecedenceAndPrebuildsBeforePublication
retentionRootCeilingHasOneProductionTruthAndFactoryUsesIt
rootSnapshotHasExactSealedShapeAndNoFactoryBypass
storeHasExactlySevenReviewedDomainMethodsAndTwoTruthStateMaps
storePackageContainsNoP4RuntimeDiscoveryOrDestructiveSurface
storedHistoryAddsOnlyTheReviewedPackagePrivateRetainHelper
```

The sorted exact-ID SHA-256 is
`e11e5b834af3a75647c1aab755d5b804d782c18910b4d09d3ac2f3eea4d35a12`; the base/U24 subset-row
projection is `8bc609faf6e5a0286e5691dcbc978de2dad145a48a68d7c2c80bbefbbf83d684`. Added, removed,
renamed, and disabled IDs are each zero. The class remains independent of the affected 9-class /
97-test subset while remaining part of the full-unit 1562-ID set.

### 50.12 Frozen-U24 P4-E3 Blocker

The required baseline is the frozen-U24 script, not the pre-U24 canonical script:

```text
path = scripts/verify-p4-e3-configuration.sh
mode = 100755
bytes = 57569
lines = 1210
SHA-256 = d3b822a6467173c50c5e6f5ce08a8ee177c9bc6677ffeba38c16e31c7adba8ac
is_exact_p4e3_path = lines 408-466; exact entries at 410-459
verify_repository_scope = lines 489-511; changed/untracked decision at 501-503
```

Mechanical extraction produces 50 entries, 50 unique values, and no wildcard. P3D3 has zero exact
token occurrences, reaches the predicate's default rejection, and makes the future tracked delta
fail at line 502. Therefore a P3D3 test correction without the paired script revision cannot pass
the active continuation verifier. No verifier was executed during this read-only review.

### 50.13 P4-E3 Exact-50 Inventory

The frozen case order is authoritative. Its ordered LF projection SHA-256 is
`c5b6c033081fd8ca44bfb3f4049759641acdf082df68002659d06a4da9acc913`, and its sorted-set
SHA-256 is `d8bbaab4d2afd777e3f32dca202ee1b2bb0d7d6893f52a7cf896beaddbaa5b1e`.
The exact entries, in order, are:

```text
01 .github/workflows/build.yml
02 build.gradle
03 scripts/verify-p4-b2-b-configuration.sh
04 scripts/verify-p4-c2-b-configuration.sh
05 scripts/verify-p4-d3-configuration.sh
06 scripts/verify-p4-e0-r-configuration.sh
07 scripts/verify-p4-e0-r2q-configuration.sh
08 scripts/verify-p4-e1-configuration.sh
09 scripts/verify-p4-e2-configuration.sh
10 scripts/verify-p4-e3-configuration.sh
11 src/main/java/com/yo1no/gramarye/Gramarye.java
12 src/main/java/com/yo1no/gramarye/P5LoadedReferenceResolver.java
13 src/main/java/com/yo1no/gramarye/P5RuntimeConfiguration.java
14 src/main/java/com/yo1no/gramarye/P5RuntimeProjector.java
15 src/main/java/com/yo1no/gramarye/P5RuntimeVocabulary.java
16 src/main/java/com/yo1no/gramarye/P5ServerRuntimeConfig.java
17 src/main/java/com/yo1no/gramarye/SkillRuntimeService.java
18 src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java
19 src/main/java/com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java
20 src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java
21 src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture.java
22 src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.java
23 src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java
24 src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java
25 src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java
26 src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java
27 src/p4E3GameTest/java/com/yo1no/gramarye/P4E3StartupObservationTestAccess.java
28 src/p4E3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupMemoryGameTests.java
29 src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/player/P4E3PlayerDataFixture.java
30 src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FileVerifier.java
31 src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureBuilder.java
32 src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureManifest.java
33 src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3ProbeMain.java
34 src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeTest.java
35 src/test/java/com/yo1no/gramarye/P5RuntimeConfigurationTest.java
36 src/test/java/com/yo1no/gramarye/P5RuntimeHardLimitWorkloadTest.java
37 src/test/java/com/yo1no/gramarye/P5RuntimeKernelTest.java
38 src/test/java/com/yo1no/gramarye/P5RuntimeStaticGateTest.java
39 src/test/java/com/yo1no/gramarye/P5RuntimeVocabularyTest.java
40 src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeVisibilityCompileTest.java
41 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java
42 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java
43 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BCompleteHandoffTest.java
44 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java
45 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java
46 src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java
47 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java
48 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3ApiGateTest.java
49 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3LeaseTerminalTest.java
50 src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupLifecycleTest.java
```

### 50.14 P4-E3 Exact-51 Paired Synchronization

Future exact-51 retains entries 1 through 50 in the same relative order and adds only:

```text
51 src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3ApiGateTest.java
```

The insertion coordinate is fixed immediately after frozen ordinal 50 and before the closing
case-pattern parenthesis. The store-unit-test group is last, while its historical entries do not
define a total lexical order; this authority therefore fixes ordinal 51 directly. The future
ordered LF projection SHA-256 is
`1470d5d29b37e91f650421158b038e39ddb66f625d065a9c8b020e2c14f2fffc`, and the future sorted-set
SHA-256 is `19e4e7c4ee9171efe54a84be8efa392ffc4666b149686fff0cb7b8763321ab2c`.

Exactly three directly inventory-linked clauses synchronize:

| Clause | Frozen value | Future value |
| --- | --- | --- |
| `is_exact_p4e3_path`, lines 410-459 | 50 exact unique tokens | 51 exact unique tokens; append ordinal 51 |
| `verify_repository_scope`, diagnostic line 502 | `exact P4-E3 50-path scope` | `exact P4-E3 51-path scope` |
| success diagnostic, line 1207 | `50 paths` | `51 paths` |

The exact word `50` occurs nowhere else in the frozen script. Added, removed, renamed, duplicate,
wildcard, package-blanket, and directory-blanket entries are `1/0/0/0/0/0/0`. Unknown-path
rejection, changed-plus-untracked coverage, existing symlink protections, resource/documentation
freeze, probe and GameTest inventories, production/test classification, regular-file checks,
P4-E limits, runtime ceilings, test counts, fixture sizes, timeout values, fixed-heap behavior,
workflow isolation, and every P4-E semantic assertion remain unchanged.

### 50.15 Original C10 Scope

The V2.3 authority file has mode `0644`, 3597 bytes, eleven lines, and SHA-256
`03259878fdcd5ef53096818dfa3393060443b7f4e633998d5545502f92f53251`. Its ten unique rows are
retained without removal or category change:

| Path | Category | V2.3 semantic delta |
| --- | --- | --- |
| `scripts/verify-p4-c2-b-configuration.sh` | `MODIFIED_EXISTING_VERIFIER` | exact ProductThreadPrecondition token; continuation 57 to 58 |
| `scripts/verify-p4-d3-configuration.sh` | `MODIFIED_EXISTING_VERIFIER` | exact ProductThreadPrecondition token; continuation 57 to 58 |
| `scripts/verify-p4-e3-configuration.sh` | `MODIFIED_EXISTING_VERIFIER` | five exact C10 tokens; continuation 45 to 50 |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture.java` | `MODIFIED_PRODUCTION` | same closed Decision through claim and initial requireActive |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.java` | `MODIFIED_PRODUCTION` | classify before claim and pass the same Decision |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java` | `NEW_PRODUCTION` | package-private pure `classify(JJ)` surface |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java` | `MODIFIED_PRODUCTION` | wrapper/core primitive observation and pre-adapter classification |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java` | `MODIFIED_PRODUCTION` | zero-worker direct classifier and exact-core controls |
| `src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java` | `MODIFIED_EXISTING_TEST` | exact classifier/API/call-chain/no-worker Gate |
| `src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java` | `MODIFIED_EXISTING_TEST` | exact owner-plus-Decision claim-handoff Gate |

Its category counts are one new production, four modified production, two modified existing tests,
and three modified existing verifiers. Its sorted-path-LF SHA-256 is
`e9e7298e4018f510e5cb2c9c76a274ef0b07cca927506d59067ef0f3ce60fa75`.

### 50.16 Corrected C11 Scope

The corrected cumulative scope retains all ten C10 paths and adds one path. The closed scope-source
labels are `P5_A_V2_3_C10`, `P5_A_V2_3_C10_AND_V2_4_R1_REVISION`, and
`P5_A_V2_4_R1_ADDITIONAL_A1`.

| Path | Category | Scope source | R1-specific role | Test-ID delta |
| --- | --- | --- | --- | ---: |
| `scripts/verify-p4-c2-b-configuration.sh` | `MODIFIED_EXISTING_VERIFIER` | `P5_A_V2_3_C10` | none | 0 |
| `scripts/verify-p4-d3-configuration.sh` | `MODIFIED_EXISTING_VERIFIER` | `P5_A_V2_3_C10` | none | 0 |
| `scripts/verify-p4-e3-configuration.sh` | `MODIFIED_EXISTING_VERIFIER` | `P5_A_V2_3_C10_AND_V2_4_R1_REVISION` | exact 50-to-51 paired inventory revision | 0 |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture.java` | `MODIFIED_PRODUCTION` | `P5_A_V2_3_C10` | none | 0 |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.java` | `MODIFIED_PRODUCTION` | `P5_A_V2_3_C10` | none | 0 |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/ProductThreadPrecondition.java` | `NEW_PRODUCTION` | `P5_A_V2_3_C10` | none | 0 |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java` | `MODIFIED_PRODUCTION` | `P5_A_V2_3_C10` | none | 0 |
| `src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java` | `MODIFIED_PRODUCTION` | `P5_A_V2_3_C10` | none | 0 |
| `src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java` | `MODIFIED_EXISTING_TEST` | `P5_A_V2_3_C10` | none | 0 |
| `src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java` | `MODIFIED_EXISTING_TEST` | `P5_A_V2_3_C10` | none | 0 |
| `src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3ApiGateTest.java` | `MODIFIED_EXISTING_TEST` | `P5_A_V2_4_R1_ADDITIONAL_A1` | exact C1 thread-surface Gate synchronization | 0 |

The inventory is eleven rows and eleven unique paths. C10 retention is `10/10`; duplicate,
missing, and extra paths are `0/0/0`. Category counts are:

```text
NEW_PRODUCTION = 1
MODIFIED_PRODUCTION = 4
MODIFIED_EXISTING_TEST = 3
MODIFIED_EXISTING_VERIFIER = 3
```

The sorted-path-LF C11 SHA-256 is
`9c52ae3e140342cc8933b58defc7baf9e51322ab7af9aa3878ec3b8ea46e7c81`.

### 50.17 M17 / C11 / O3 / U25 Arithmetic

The sets were recomputed from the sealed M17 input and corrected C11 rows:

```text
M = exact 17 migration paths
C = corrected exact 11 C1 paths
O = M intersection C
U = M union C

|M| = 17
|C| = 11
|O| = 3
|U| = 25
25 = 17 + 11 - 3
```

The exact overlap is unchanged:

```text
scripts/verify-p4-c2-b-configuration.sh
scripts/verify-p4-d3-configuration.sh
scripts/verify-p4-e3-configuration.sh
```

The sorted-path-LF identities are:

```text
M17 = 0b6d9a37d1c5c337115f115fd72ca10b961c545cd7f9b76eccf7f32e805a71b3
C11 = 9c52ae3e140342cc8933b58defc7baf9e51322ab7af9aa3878ec3b8ea46e7c81
O3  = 6e968ecdf5af2e206fcda0a125c0da2650bcee1c8b0eec05d004d6d2f0587583
U25 = 23d324e2c20599e01a26138ac3096b0c12014ab45f62b30cae45b2069c8a5f8a
```

The future source-correction round must mechanically recompute the actual path set from the sealed
inputs. These values do not authorize an inferred path.

### 50.18 Relative-U24 Two-Path Delta Model

Every U25 path has exactly one closed role:

| Role | Count | Exact member or preservation rule |
| --- | ---: | --- |
| `U24_UNCHANGED` | 23 | every path in `F24 minus R1`; frozen mode, bytes, and SHA-256 exact |
| `U24_REVISED_EXISTING_PATH` | 1 | `scripts/verify-p4-e3-configuration.sh`; exact narrow 50-to-51 contract |
| `U25_ADDED_TRACKED_PATH` | 1 | `src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3ApiGateTest.java`; canonical tracked base to exact Gate delta |

Each role has test-ID delta zero. The future final path set is exact U25. Based on the frozen
migration topology, the expected Git shape is 13 tracked modified paths and 12 untracked new paths;
the future round must derive those counts from Git after migration and before validation.

### 50.19 P5-Q1 Continuity

```text
P5_Q1_TEST_INVENTORY_CONTRACT_V1
= d00e39ebd140131949232c2c95515e113ce0f6a16f7c20c17f1821b11c6f56e5

base test IDs = 1491
U24 test IDs = 1562
future U25 test IDs = exact same 1562-ID set
P3D3 base / U24 / future = 11 / 11 / 11
P3D3 added / removed / renamed / disabled = 0 / 0 / 0 / 0
P4-E3 script test-ID effect = 0
affected inventory = 9 classes / 97 tests
P5 focused inventory = 5 classes / 70 tests
hard-limit digest = 994d53cb0c302785
full-unit expected = 1562
Q1 projection = unchanged
```

The U24 exact-ID-set SHA-256 is
`be07c0453cbd39d46435ea2d2c178b68d6e7218716abafef15a1c464e7069726`. The future source round
must run the Q1 scanner and prove the U25 ID set has this exact identity before creating U25 freeze.
This section creates no Q2 authority.

### 50.20 Future U25 Source-Correction Protocol

After the exact remote condition in Section 50.1, the next source-correction work item is fixed:

1. create a fresh detached worktree from the new canonical V2.4-R1 commit;
2. migrate the frozen U24 exact 24 paths one file at a time;
3. after migration, permit relative-U24 byte changes only in P3D3 and P4-E3;
4. prove frozen mode, bytes, and SHA-256 for all 23 `F24 minus R1` paths;
5. prove P4-E3 begins at the exact frozen 50 inventory and ends at exact 51 with only P3D3 added;
6. prove P3D3 begins at canonical tracked bytes, ends at the exact C1 thread-surface Gate, and
   keeps the exact eleven IDs;
7. prove the final actual path set is exact U25 and recompute its sorted-path-LF identity;
8. derive and record the final tracked/untracked counts from Git;
9. execute only the limited validation in Section 50.21;
10. create a new exact U25 source freeze; and
11. stop without staging, committing, or pushing.

A required third relative-U24 byte-delta path stops the U25 correction. It does not expand this
authority.

### 50.21 Future Limited Validation

Before U25 freeze, the source-correction round must execute in this closed scope:

1. focused compile;
2. the complete existing `P3D3ApiGateTest` class;
3. P3D3 exact `11/11`;
4. affected 9 classes / 97 tests;
5. P5 focused 5 classes / 70 tests;
6. hard-limit digest `994d53cb0c302785`;
7. verifier syntax `4/4`;
8. verifier matrix `5/5`;
9. P4-E3 exact-51 static contract;
10. P3D3 thread-surface source/bytecode contract;
11. Q1 source-derived exact test-ID continuity; and
12. full unit `1562/1562` as a limited correction observation.

That full-unit observation is not formal qualification. A failure may be corrected only within the
exact paired byte-delta paths. Needing any other path stops the round before freeze.

### 50.22 Future Formal Qualification Order

Only after exact U25 freeze, a fresh formal campaign follows this order:

```text
fresh preflight
-> Q1 contract continuity
-> exact U25 migration
-> frozen-byte continuity
-> focused compile
-> complete P3D3ApiGateTest Gate
-> affected 9 classes / 97 tests
-> hard-limit digest
-> verifier syntax 4/4
-> verifier matrix 5/5
-> full unit 1562
-> GameTest 12/12
-> dedicated smoke
-> warning compile
-> fresh JAR
-> static/API/callsite/retention/Error Gates
-> P3D3 exact thread-surface Gate
-> P4-E3 exact-51 Gate
-> candidate immutability
-> evidence/postflight
```

Each formal Gate has attempt 1 and retry zero. Legitimate execution of the same test in different
formal invocations is not a duplicate within one invocation.

### 50.23 Unchanged Product Semantics

```text
P5 runtime semantics changed = 0
C1 ProductThreadPrecondition semantics changed = 0
Control 1 semantics changed = 0
Control 2 semantics changed = 0
Control 2 consume-on-mismatch changed = 0
P4 persistence/audit semantics changed = 0
Error primary/secondary policy changed = 0
GameTest topology changed = 0
Q1 test-ID inventory changed = 0
full-unit expected changed = 0
public top-level API delta = 0
Gradle/workflow delta = 0
P4-E verifier product semantics changed = 0
```

This amendment corrects only the stale P3D3 test Gate, the P4-E3 continuation-scope inventory, and
the future byte-preservation contract. Server authority, Store truth, journal truth, bounded
failure behavior, result vocabulary, budget, breaker, queue, deadline, configuration, Error
primary/secondary `6/5`, and all other P5/P4 semantics remain governed by the preceding authority.

### 50.24 Implementation Authority

```text
P3D3ApiGateTest exact synchronization
= AUTHORIZED UPON THIS COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  SIX-JOB REMOTE GATE PASS

P4-E3 exact-50 → exact-51 synchronization
= AUTHORIZED UPON THE SAME CONDITION

Corrected cumulative C1 scope
= EXACT 11 PATHS

Future implementation union
= EXACT 25 PATHS
  AFTER MECHANICAL RECOMPUTATION

Relative-to-U24 future byte deltas
= EXACT 2 PATHS

Frozen U24 unchanged subset
= EXACT 23 PATHS

P5-Q1 test inventory
= EXACT 1562;
  PRESERVED

U25 source correction / freeze
= READY UPON THE SAME REMOTE-PASS CONDITION;
  NOT STARTED

Fresh P5 qualification
= BLOCKED UNTIL U25 FREEZE

P5 implementation commit
= NOT AUTHORIZED BY THIS DOCS-ONLY ROUND
```

This authority does not claim that either synchronization is implemented, that U25 is frozen, that
qualification passed, that the implementation passed, or that P5 is complete.

### 50.25 Phase Status

```text
P4-E
= COMPLETE

P5-A consolidated architecture record
= COMPLETE

P5-A-V1
= COMPLETE

P5-A-V2 corrected authority amendment
= COMPLETE

P5-A-V2.3
= COMPLETE

P5-A-V2.4 INITIAL WORK ORDER
= STOPPED BEFORE REPOSITORY MODIFICATION;
  DEFECTIVE BYTE-PRESERVATION CONTRACT

P5-A-V2.4-R1 P3D3 THREAD-SURFACE /
P4-E3 EXACT-51 PAIRED AUTHORITY CORRECTION
= COMPLETE UPON THIS COMMIT'S
  UNIQUE EXACT-SHA ATTEMPT-1
  SIX-JOB REMOTE GATE PASS

Chosen model
= C1

Corrected C1 semantic delta
= EXACT 11 PATHS

Future M / C / O / U
= 17 / 11 / 3 / 25

Relative-to-U24 byte delta
= EXACT 2 PATHS

Frozen U24 unchanged subset
= EXACT 23 PATHS

P5-Q1 test-inventory contract
= COMPLETE;
  EXACT 1562

P3D3 stale Gate synchronization
= READY UPON THE SAME CONDITION;
  NOT STARTED

P4-E3 exact-51 synchronization
= READY UPON THE SAME CONDITION;
  NOT STARTED

Corrected U25 source freeze
= NOT CREATED

P5 fresh qualification
= BLOCKED UNTIL U25 FREEZE

P5 implementation
= LOCALLY CORRECTED;
  UNQUALIFIED;
  UNCOMMITTED

P5
= INCOMPLETE

P6
= NOT STARTED
```

No stronger phase verdict is implied.

### 50.26 Provenance

The external evidence is historical or prospective provenance; this V2.4-R1 architecture
amendment is the repository authority. These plain-text coordinates are not repository links or
runtime dependencies:

```text
canonical base SHA = 3804ee3f1be234af748426838c896a5fd19eb794
canonical base tree = 97cb4177ec6b7f419acfd144af3cf05c88a3a803
architecture prefix bytes = 246809
architecture prefix lines = 4579
architecture prefix SHA-256 = 085ec526ea9ce7abb6ebc46e9dbc6ef88c625e7005ded81c757d040cbcfa88e0
U24 source candidate = /private/tmp/gramarye-p5-c1-exact10-source-correction-20260829T073123Z
U24 source-freeze evidence = /private/tmp/gramarye-p5-c1-exact10-source-correction-evidence-20260829T073123Z
P5-Q1 evidence = /private/tmp/gramarye-p5-q1-test-inventory-contract-20260829T120534Z
failed Q1 qualification worktree = /private/tmp/gramarye-p5-q1-fresh-complete-qualification-20260829T124226Z
failed Q1 partial evidence = /private/tmp/gramarye-p5-q1-fresh-complete-qualification-evidence-20260829T124226Z
initial V2.4 partial evidence = /private/tmp/gramarye-p5-a-v2-4-p3d3-thread-gate-evidence-20260829T133100Z
initial V2.4 docs worktree = /private/tmp/gramarye-p5-a-v2-4-p3d3-thread-gate-authority-20260829T133100Z
V2.3 scope evidence = /private/tmp/gramarye-p5-a-v2-3-scope-amendment-evidence-20260829T050516Z
```
