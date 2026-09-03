package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.EventId;
import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.store.ControlledSkillPin;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Sole synchronous owner of transient P5 server runtime work. */
final class SkillRuntimeService {
    static final Comparator<RuntimeEvent> EVENT_ORDER = SkillRuntimeService::compareEvents;

    private final SkillDefinitionStoreService storeService;
    private final SkillSubmissionPolicyProvider policyProvider;
    private final P5RuntimeProjector projector;
    private final RuntimeReferenceResolver referenceResolver;
    private final RuntimeExecutionPort executionPort;
    private final IdentityHashMap<MinecraftServer, ServerSlot> slots = new IdentityHashMap<>(1);
    private long serverTokenHighWater;

    SkillRuntimeService(
            SkillDefinitionStoreService storeService,
            SkillSubmissionPolicyProvider policyProvider,
            P5RuntimeProjector projector,
            RuntimeReferenceResolver referenceResolver,
            RuntimeExecutionPort executionPort) {
        this.storeService = Objects.requireNonNull(storeService, "storeService");
        this.policyProvider = Objects.requireNonNull(policyProvider, "policyProvider");
        this.projector = Objects.requireNonNull(projector, "projector");
        this.referenceResolver = Objects.requireNonNull(referenceResolver, "referenceResolver");
        this.executionPort = Objects.requireNonNull(executionPort, "executionPort");
    }

    static SkillRuntimeService create(
            IEventBus gameBus,
            SkillDefinitionStoreService storeService,
            SkillSubmissionPolicyProvider policyProvider) {
        Objects.requireNonNull(gameBus, "gameBus");
        var service = new SkillRuntimeService(
                storeService,
                policyProvider,
                new P5RuntimeProjector(),
                new P5LoadedReferenceResolver(),
                new P6RuntimeExecutionPortAdapter());
        gameBus.addListener(EventPriority.LOWEST, service::handleRuntimePost);
        gameBus.addListener(service::handleRuntimeStopping);
        gameBus.addListener(service::handleRuntimeStopped);
        return service;
    }

    ServerSlot newRunningSlot(RuntimeServerToken token, P5RuntimeLimits limits) {
        return new ServerSlot(token, limits);
    }

    Optional<RuntimeAdmissionResult> persistenceFailure(RuntimeScheduleSpec schedule) {
        Objects.requireNonNull(schedule, "schedule");
        return schedule.persistence() == RuntimeSchedulePersistence.PERSISTENT
                ? Optional.of(new RuntimeAdmissionResult.PersistentScheduleUnsupported())
                : Optional.empty();
    }

    void handleRuntimeStarted(ServerStartedEvent event, P5RuntimeLimits limits) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(limits, "limits");
        var server = event.getServer();
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_LIFECYCLE);
        }
        if (!server.isRunning() || server.isStopped()) {
            throw kernel(RuntimeKernelException.Code.STOPPED_SERVER_INSTALL);
        }
        if (slots.containsKey(server)) {
            throw kernel(RuntimeKernelException.Code.DUPLICATE_SERVER_INSTALL);
        }
        if (!slots.isEmpty()) {
            throw kernel(RuntimeKernelException.Code.SECOND_ACTIVE_SERVER);
        }
        var nextToken = checkedPositiveSuccessor(serverTokenHighWater);
        if (nextToken.isEmpty()) {
            throw kernel(RuntimeKernelException.Code.SERVER_SLOT_TOKEN_EXHAUSTED);
        }
        var token = new RuntimeServerToken(nextToken.orElseThrow());
        var slot = newRunningSlot(token, limits);
        slots.put(server, slot);
        serverTokenHighWater = token.value();
    }

    RuntimeAdmissionResult admitRoot(MinecraftServer server, RuntimeRootEventSpec spec) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(spec, "spec");
        var slot = slots.get(server);
        if (slot == null) {
            return new RuntimeAdmissionResult.ServerNotRunning();
        }
        if (!server.isSameThread()) {
            return new RuntimeAdmissionResult.WrongThread();
        }
        if (slot.state == ServerSlot.State.FAULTED) {
            return new RuntimeAdmissionResult.KernelFaulted();
        }
        if (slot.state == ServerSlot.State.EXHAUSTED) {
            return new RuntimeAdmissionResult.TickExhausted();
        }
        if (slot.state != ServerSlot.State.RUNNING || !server.isRunning() || server.isStopped()) {
            stopSlot(slot);
            return new RuntimeAdmissionResult.ServerStopping();
        }
        if (slot.rootAdmissionsThisTick == slot.limits.rootAdmissionsPerTick()) {
            return new RuntimeAdmissionResult.RootAdmissionBudgetExceeded(
                    slot.limits.rootAdmissionsPerTick());
        }
        slot.rootAdmissionsThisTick++;
        slot.diagnostics.rootAdmissionAttemptsThisTick++;

        var schedule = spec.schedule();
        var persistenceFailure = persistenceFailure(schedule);
        if (persistenceFailure.isPresent()) {
            return persistenceFailure.orElseThrow();
        }
        if (schedule.delayTicks() < 0
                || schedule.delayTicks() > slot.limits.maximumDelayTicks()) {
            return new RuntimeAdmissionResult.DelayOutOfRange(
                    schedule.delayTicks(), slot.limits.maximumDelayTicks());
        }
        if (schedule.deadlineHorizonTicks() < 0
                || schedule.deadlineHorizonTicks()
                        > slot.limits.maximumDeadlineHorizonTicks()) {
            return new RuntimeAdmissionResult.DeadlineOutOfRange(
                    schedule.deadlineHorizonTicks(),
                    slot.limits.maximumDeadlineHorizonTicks());
        }

        final long baseTick;
        final long scheduledTick;
        final long deadlineTick;
        try {
            baseTick = Math.addExact(slot.runtimeTick, 1L);
        } catch (ArithmeticException ignored) {
            return new RuntimeAdmissionResult.TickExhausted();
        }
        try {
            scheduledTick = Math.addExact(baseTick, schedule.delayTicks());
        } catch (ArithmeticException ignored) {
            return new RuntimeAdmissionResult.DelayOverflow();
        }
        try {
            deadlineTick = Math.addExact(baseTick, schedule.deadlineHorizonTicks());
        } catch (ArithmeticException ignored) {
            return new RuntimeAdmissionResult.DeadlineOverflow();
        }
        if (scheduledTick > deadlineTick) {
            return new RuntimeAdmissionResult.DeadlineBeforeScheduledTick(
                    scheduledTick, deadlineTick);
        }

        var invalid = validateRootStableShape(slot, spec);
        if (invalid.isPresent()) {
            return new RuntimeAdmissionResult.InvalidEvent(invalid.orElseThrow());
        }
        if (!stableTokensMatch(slot.token, spec.origin(), spec.target())) {
            return new RuntimeAdmissionResult.InvalidRuntimeReference(
                    RuntimeReferenceFailureReason.WRONG_SERVER);
        }
        var rootResolution = P5LoadedReferenceResolver.resolveLoadedReferences(
                server, slot.token, spec.origin(), spec.target());
        if (slot.state != ServerSlot.State.RUNNING) {
            return admissionForClosedSlot(slot);
        }
        if (!server.isRunning() || server.isStopped()) {
            stopSlot(slot);
            return new RuntimeAdmissionResult.ServerStopping();
        }
        if (!(rootResolution instanceof RuntimeReferenceResolutionOutcome.Resolved)) {
            return new RuntimeAdmissionResult.InvalidRuntimeReference(
                    referenceFailure(rootResolution));
        }
        if (slot.instances.size() == slot.limits.activeSkillInstancesPerServer()) {
            return new RuntimeAdmissionResult.ActiveLineageCapacityExceeded(
                    slot.instances.size(), slot.limits.activeSkillInstancesPerServer());
        }
        var attribution = slot.attributions.get(spec.budgetAttribution());
        var activeAttribution = attribution == null ? 0 : attribution.activeInstances;
        if (activeAttribution == slot.limits.activeSkillInstancesPerAttribution()) {
            return new RuntimeAdmissionResult.ActiveBudgetAttributionCapacityExceeded(
                    activeAttribution, slot.limits.activeSkillInstancesPerAttribution());
        }
        var nextInstanceSequence = checkedPositiveSuccessor(slot.skillInstanceSequenceHighWater);
        if (nextInstanceSequence.isEmpty()) {
            return new RuntimeAdmissionResult.SequenceExhausted(
                    RuntimeSequenceKind.SKILL_INSTANCE_SEQUENCE);
        }
        var nextEventSequence = checkedPositiveSuccessor(slot.eventSequenceHighWater);
        if (nextEventSequence.isEmpty()) {
            return new RuntimeAdmissionResult.SequenceExhausted(RuntimeSequenceKind.EVENT_SEQUENCE);
        }

        var prospectiveSequence = nextInstanceSequence.orElseThrow();
        var prospectiveId = new SkillInstanceId(new UUID(slot.token.value(), prospectiveSequence));
        if (slot.instances.containsKey(prospectiveId)) {
            return new RuntimeAdmissionResult.InvalidEvent(
                    InvalidEventReason.DUPLICATE_LIVE_SKILL_INSTANCE_ID);
        }
        var attributionPending = attribution == null
                ? 0
                : attribution.committedPending + attribution.reservedPending;
        if (attributionPending == slot.limits.pendingEventsPerAttribution()) {
            var reason = spec.budgetAttribution() instanceof PlayerRuntimeBudgetAttribution
                    ? RuntimeCircuitBreakReason.PLAYER_PENDING_EVENTS_EXCEEDED
                    : RuntimeCircuitBreakReason.NON_PLAYER_DOMAIN_PENDING_EVENTS_EXCEEDED;
            var summary = new RuntimeCircuitBreakerSummary(
                    reason,
                    attributionPending,
                    1,
                    slot.limits.pendingEventsPerAttribution(),
                    0,
                    false);
            observeBreaker(
                    slot,
                    prospectiveId,
                    Optional.empty(),
                    Optional.empty(),
                    playerId(spec.budgetAttribution()),
                    summary);
            return new RuntimeAdmissionResult.CircuitBroken(summary);
        }
        if (slot.committedPending + slot.reservedPending
                == slot.limits.pendingEventsPerServer()) {
            var summary = new RuntimeCircuitBreakerSummary(
                    RuntimeCircuitBreakReason.SERVER_PENDING_EVENTS_EXCEEDED,
                    slot.committedPending + slot.reservedPending,
                    1,
                    slot.limits.pendingEventsPerServer(),
                    0,
                    false);
            observeBreaker(
                    slot,
                    prospectiveId,
                    Optional.empty(),
                    Optional.empty(),
                    playerId(spec.budgetAttribution()),
                    summary);
            return new RuntimeAdmissionResult.CircuitBroken(summary);
        }

        var prospectiveEventId = new EventId(nextEventSequence.orElseThrow());
        var cancellationToken = new RuntimeCancellationToken(slot.token, prospectiveId);
        var accepted = new RuntimeAdmissionResult.AcceptedMemoryOnly(
                new RuntimeEventToken(slot.token, prospectiveId, prospectiveEventId),
                cancellationToken);

        try {
            return acquireAndPublishRoot(
                    server,
                    slot,
                    spec,
                    attribution,
                    prospectiveSequence,
                    prospectiveId,
                    prospectiveEventId,
                    cancellationToken,
                    accepted,
                    scheduledTick,
                    deadlineTick);
        } catch (RuntimeException primary) {
            throw preserveRuntimeFault(slot, primary);
        } catch (Error primary) {
            throw preserveErrorFault(slot, primary);
        }
    }

    private RuntimeAdmissionResult acquireAndPublishRoot(
            MinecraftServer server,
            ServerSlot slot,
            RuntimeRootEventSpec spec,
            ServerSlot.AttributionState attribution,
            long prospectiveSequence,
            SkillInstanceId prospectiveId,
            EventId prospectiveEventId,
            RuntimeCancellationToken cancellationToken,
            RuntimeAdmissionResult.AcceptedMemoryOnly accepted,
            long scheduledTick,
            long deadlineTick) {
        var leaseAcquisition = acquireLease(server, slot, spec.skillReference());
        try {
            if (slot.state != ServerSlot.State.RUNNING) {
                releaseProvisionalLease(slot, leaseAcquisition);
                return admissionForClosedSlot(slot);
            }
            if (!server.isRunning() || server.isStopped()) {
                stopSlot(slot);
                releaseProvisionalLease(slot, leaseAcquisition);
                return new RuntimeAdmissionResult.ServerStopping();
            }
            if (leaseAcquisition instanceof LeaseAcquisition.Unavailable unavailable) {
                return new RuntimeAdmissionResult.SkillRevisionUnavailable(unavailable.reason());
            }
            var lease = ((LeaseAcquisition.Available) leaseAcquisition).lease();
            var nodeFailure = validateRootDefinitionShape(lease.definition, spec);
            if (nodeFailure.isPresent()) {
                releaseProvisionalLease(slot, leaseAcquisition);
                return new RuntimeAdmissionResult.InvalidEvent(nodeFailure.orElseThrow());
            }
            var prospectiveEvent = new RuntimeEvent(
                    prospectiveEventId,
                    prospectiveId,
                    new RuntimeSkillInstanceSequence(prospectiveSequence),
                    cancellationToken,
                    Optional.empty(),
                    spec.skillReference(),
                    spec.nodeIndex(),
                    slot.runtimeTick,
                    scheduledTick,
                    deadlineTick,
                    0,
                    0,
                    RuntimeSchedulePersistence.MEMORY_ONLY,
                    spec.budgetAttribution(),
                    spec.origin(),
                    spec.target(),
                    spec.triggerCause(),
                    spec.executionData());
            publishRoot(slot, prospectiveEvent, lease, leaseAcquisition, attribution);
            return accepted;
        } catch (RuntimeException | Error primary) {
            closeProvisionalAfterRootFault(leaseAcquisition);
            throw primary;
        }
    }

    RuntimeCancellationResult cancel(
            MinecraftServer server, RuntimeCancellationHandle handle) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(handle, "handle");
        var slot = slots.get(server);
        if (slot == null) {
            return new RuntimeCancellationResult.ServerNotRunning();
        }
        if (!server.isSameThread()) {
            return new RuntimeCancellationResult.WrongThread();
        }
        if (slot.state != ServerSlot.State.RUNNING || !server.isRunning() || server.isStopped()) {
            stopSlot(slot);
            return new RuntimeCancellationResult.ServerStopping();
        }
        return cancelInSlot(slot, handle);
    }

    private RuntimeCancellationResult cancelInSlot(
            ServerSlot slot, RuntimeCancellationHandle handle) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(handle, "handle");
        if (!slot.token.equals(serverToken(handle))) {
            return new RuntimeCancellationResult.WrongServer();
        }
        if (!cancellationBudgetAvailable(
                slot.cancellationsThisTick, slot.limits.cancellationsPerTick())) {
            return new RuntimeCancellationResult.CancellationBudgetExceeded(
                    slot.limits.cancellationsPerTick());
        }
        slot.cancellationsThisTick++;
        try {
            if (handle instanceof RuntimeCancellationToken token) {
                return cancelInstance(slot, token.skillInstanceId());
            }
            var token = (RuntimeEventToken) handle;
            var indexed = slot.eventIndex.get(token.eventId());
            if (indexed == null) {
                return new RuntimeCancellationResult.NotPending();
            }
            if (!indexed.skillInstanceId().equals(token.skillInstanceId())) {
                return new RuntimeCancellationResult.CancellationTokenInvalid(
                        RuntimeCancellationTokenInvalidReason.EVENT_OWNER_MISMATCH);
            }
            if (slot.currentEvent == indexed) {
                return new RuntimeCancellationResult.InFlight();
            }
            if (!removeExactQueuedOrDeferred(slot, indexed)) {
                throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
            }
            removeCommittedEvent(slot, indexed);
            maybeRemoveInstance(slot, token.skillInstanceId());
            return new RuntimeCancellationResult.CancelledEvent();
        } catch (RuntimeException primary) {
            throw preserveRuntimeFault(slot, primary);
        } catch (Error primary) {
            throw preserveErrorFault(slot, primary);
        }
    }

    boolean cancellationBudgetAvailable(
            int cancellationsThisTick, int cancellationLimit) {
        if (cancellationsThisTick < 0
                || cancellationLimit <= 0
                || cancellationsThisTick > cancellationLimit) {
            throw new IllegalArgumentException("invalid cancellation boundary state");
        }
        return cancellationsThisTick < cancellationLimit;
    }

    private void handleRuntimePost(ServerTickEvent.Post event) {
        var server = event.getServer();
        var slot = slots.get(server);
        if (slot == null) {
            throw kernel(RuntimeKernelException.Code.TICK_BEFORE_INSTALL);
        }
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_DRAIN);
        }
        if (slot.state != ServerSlot.State.RUNNING) {
            return;
        }
        if (!server.isRunning() || server.isStopped()) {
            stopSlot(slot);
            return;
        }
        if (slot.dispatching) {
            throw kernel(RuntimeKernelException.Code.NESTED_DRAIN);
        }
        if (advanceRuntimeTick(slot) == RuntimeTickAdvanceResult.EXHAUSTED) {
            slot.state = ServerSlot.State.EXHAUSTED;
            clearSlotNormal(slot);
            return;
        }
        resetTickState(slot);
        drain(server, slot);
    }

    private void handleRuntimeStopping(ServerStoppingEvent event) {
        var server = event.getServer();
        var slot = slots.get(server);
        if (slot == null) {
            return;
        }
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_LIFECYCLE);
        }
        stopSlot(slot);
    }

    private void handleRuntimeStopped(ServerStoppedEvent event) {
        var server = event.getServer();
        var slot = slots.get(server);
        if (slot == null) {
            return;
        }
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_LIFECYCLE);
        }
        slot.state = ServerSlot.State.STOPPING;
        clearSlotNormal(slot);
        slot.state = ServerSlot.State.REMOVED;
        slots.remove(server);
    }

    private void drain(MinecraftServer server, ServerSlot slot) {
        if (slot.dispatching) {
            throw kernel(RuntimeKernelException.Code.NESTED_DRAIN);
        }
        slot.dispatching = true;
        try {
            while (true) {
                if (slot.executionsThisTick == slot.limits.executionsPerServerPerTick()) {
                    reintegrateDeferred(slot);
                    observeDrainStop(
                            slot, RuntimeDrainStopReason.SERVER_EXECUTION_LIMIT_REACHED);
                    return;
                }
                var event = slot.queue.peek();
                if (event == null || event.scheduledRuntimeTick() > slot.runtimeTick) {
                    reintegrateDeferred(slot);
                    return;
                }
                event = slot.queue.poll();
                verifyQueuedIdentity(slot, event);
                var instance = slot.instances.get(event.skillInstanceId());
                if (instance == null) {
                    removeStaleOwnerEvent(slot, event);
                    observeOutcome(slot, new RuntimeExecutionOutcome.OwnerInstanceUnavailable());
                    continue;
                }
                verifyQueuedOwnerIdentity(instance, event);
                if (instance.terminal) {
                    removeCommittedEvent(slot, event);
                    maybeRemoveInstance(slot, instance.id);
                    observeOutcome(slot, new RuntimeExecutionOutcome.OwnerInstanceUnavailable());
                    continue;
                }
                if (instance.cancellationRequested) {
                    removeCommittedEvent(slot, event);
                    maybeRemoveInstance(slot, instance.id);
                    observeOutcome(slot, new RuntimeExecutionOutcome.Cancelled());
                    continue;
                }
                if (deadlineExpired(slot, event)) {
                    observeExpired(slot, event);
                    removeCommittedEvent(slot, event);
                    maybeRemoveInstance(slot, instance.id);
                    observeOutcome(
                            slot,
                            new RuntimeExecutionOutcome.DeadlineExpired(
                                    event.deadlineRuntimeTick(), slot.runtimeTick));
                    continue;
                }
                var attribution = requireAttribution(slot, instance.attribution);
                resetExecutionEpoch(instance, slot.runtimeTick);
                resetExecutionEpoch(attribution, slot.runtimeTick);
                var decision = executionDecision(slot, instance, attribution);
                if (decision != RuntimeBudgetDecision.EXECUTE) {
                    defer(slot, event, instance, attribution, decision);
                    continue;
                }
                claim(slot, event, instance, attribution);
                dispatchClaimed(server, slot, instance, attribution, event);
                if (slot.state != ServerSlot.State.RUNNING) {
                    reintegrateDeferred(slot);
                    return;
                }
            }
        } catch (RuntimeException primary) {
            throw preserveRuntimeFault(slot, primary);
        } catch (Error primary) {
            throw preserveErrorFault(slot, primary);
        } finally {
            slot.dispatching = false;
            if (slot.state == ServerSlot.State.RUNNING) {
                reintegrateDeferred(slot);
            }
        }
    }

    private void dispatchClaimed(
            MinecraftServer server,
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent event) {
        RuntimeExecutionOutcome outcome;
        var lease = instance.lease;
        if (lease.pin.isClosed()) {
            outcome = new RuntimeExecutionOutcome.SkillRevisionUnavailable();
        } else {
            var invocation = invokeRuntimeBoundary(
                    server, slot, instance, attribution, event, lease);
            if (invocation instanceof AbortedInvocation) {
                return;
            }
            outcome = switch (invocation) {
                case TerminalInvocation terminal -> terminal.outcome();
                case PortInvocation returned -> finishPort(
                        server, slot, instance, attribution, event, returned);
                case NullBatchInvocation returned -> finishPort(
                        server, slot, instance, attribution, event, returned);
                case AbortedInvocation ignored ->
                        throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            };
        }
        if (slot.state != ServerSlot.State.RUNNING) {
            return;
        }
        if (slot.currentEvent != event) {
            throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
        }
        var breakerPlayer = playerId(instance.attribution);
        terminalizeCurrent(slot, instance, event);
        observeOutcome(slot, outcome);
        if (outcome instanceof RuntimeExecutionOutcome.CircuitBroken broken) {
            observeBreaker(
                    slot,
                    instance.id,
                    Optional.of(instance.sequence),
                    Optional.of(event.eventId()),
                    breakerPlayer,
                    broken.summary());
        }
    }

    private DetachedInvocation invokeRuntimeBoundary(
            MinecraftServer server,
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent event,
            RuntimeRevisionLease lease) {
        RuntimeReferenceResolutionOutcome resolution = null;
        ResolvedRuntimeReferenceContext resolvedReferences = null;
        RuntimeExecutionContext context = null;
        try {
            resolution = referenceResolver.resolve(server, event);
            if (slot.state != ServerSlot.State.RUNNING) {
                return AbortedInvocation.INSTANCE;
            }
            if (!server.isRunning() || server.isStopped()) {
                stopSlot(slot);
                return AbortedInvocation.INSTANCE;
            }
            if (instance.cancellationRequested) {
                return new TerminalInvocation(
                        new RuntimeExecutionOutcome.Cancelled());
            }
            if (!(resolution instanceof RuntimeReferenceResolutionOutcome.Resolved)) {
                return new TerminalInvocation(referenceFailureOutcome(resolution));
            }
            resolvedReferences = ((RuntimeReferenceResolutionOutcome.Resolved) resolution).context();
            var reservation = reserveForPort(slot, instance, attribution, event);
            var node = lease.definition.nodes().get(event.nodeIndex());
            context = new RuntimeExecutionContext(
                    server,
                    lease.definition,
                    node,
                    slot.runtimeTick,
                    slot.token,
                    resolvedReferences,
                    reservation.budget,
                    () -> runtimeExecutionGuardDecision(slot, instance, event));
            slot.diagnostics.portInvocationsThisTick++;
            var batch = executionPort.execute(event, context);
            return batch == null
                    ? new NullBatchInvocation(reservation)
                    : new PortInvocation(reservation, batch);
        } finally {
            context = null;
            resolvedReferences = null;
            resolution = null;
        }
    }

    private RuntimeExecutionOutcome finishPort(
            MinecraftServer server,
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent event,
            DetachedInvocation invocation) {
        var reservation = switch (invocation) {
            case PortInvocation returned -> returned.reservation();
            case NullBatchInvocation returned -> returned.reservation();
            case AbortedInvocation ignored ->
                    throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            case TerminalInvocation ignored ->
                    throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        };
        if (slot.state != ServerSlot.State.RUNNING) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.ServerStopping();
        }
        if (!server.isRunning() || server.isStopped()) {
            stopSlot(slot);
            return new RuntimeExecutionOutcome.ServerStopping();
        }
        if (instance.cancellationRequested) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.Cancelled();
        }
        if (invocation instanceof NullBatchInvocation) {
            throw kernel(RuntimeKernelException.Code.NULL_EXECUTION_BATCH);
        }
        var batch = ((PortInvocation) invocation).batch();
        if (batch.outcome() instanceof RuntimePortOutcome.Rejected rejected) {
            releaseCurrentReservation(slot, instance, attribution);
            return portRejectionOutcome(rejected);
        }
        return processCompletedPlan(slot, instance, attribution, event, reservation, batch.children());
    }

    static RuntimeExecutionOutcome referenceFailureOutcome(
            RuntimeReferenceResolutionOutcome resolution) {
        Objects.requireNonNull(resolution, "resolution");
        return switch (resolution) {
            case RuntimeReferenceResolutionOutcome.SourceMissing missing ->
                    new RuntimeExecutionOutcome.SourceMissing(missing.reason());
            case RuntimeReferenceResolutionOutcome.TargetMissing missing ->
                    new RuntimeExecutionOutcome.TargetMissing(missing.reason());
            case RuntimeReferenceResolutionOutcome.InvalidRuntimeReference invalid ->
                    new RuntimeExecutionOutcome.InvalidRuntimeReference(invalid.reason());
            case RuntimeReferenceResolutionOutcome.Resolved ignored ->
                    throw new IllegalArgumentException("resolved reference has no failure outcome");
        };
    }

    static RuntimeExecutionOutcome portRejectionOutcome(RuntimePortOutcome.Rejected rejection) {
        Objects.requireNonNull(rejection, "rejection");
        return new RuntimeExecutionOutcome.RejectedByExecutionPort(rejection.reason());
    }

    private RuntimeExecutionOutcome processCompletedPlan(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent parent,
            ChildReservation reservation,
            RuntimeChildPlan plan) {
        var children = canonicalize(plan.children());
        var childCount = children.size();
        if (childCount == 0) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.Completed();
        }
        if (childCount > slot.limits.directChildrenPerEvent()) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.BudgetRejected(
                    RuntimeBudgetRejectionReason.DIRECT_CHILD_LIMIT_EXCEEDED);
        }
        var zeroDelayCount = 0;
        for (var child : children) {
            if (child.delayTicks() == 0) {
                zeroDelayCount++;
            }
        }
        if (zeroDelayCount > slot.limits.zeroDelayChildrenPerEvent()) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.BudgetRejected(
                    RuntimeBudgetRejectionReason.ZERO_DELAY_CHILD_LIMIT_EXCEEDED);
        }
        for (var child : children) {
            if (!stableTokensMatch(slot.token, child.origin(), child.target())) {
                releaseCurrentReservation(slot, instance, attribution);
                throw kernel(RuntimeKernelException.Code.INVALID_CHILD_PLAN_INVARIANT);
            }
        }
        for (var child : children) {
            var structural = validateChildShape(
                    instance.lease.definition, instance.attribution, child);
            if (structural.isPresent()) {
                releaseCurrentReservation(slot, instance, attribution);
                return new RuntimeExecutionOutcome.InvalidEvent(structural.orElseThrow());
            }
        }
        if (parent.depth() == slot.limits.maximumDepth()) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.BudgetRejected(
                    RuntimeBudgetRejectionReason.DEPTH_LIMIT_EXCEEDED);
        }
        for (var child : children) {
            if (child.delayTicks() < 0
                    || child.delayTicks() > slot.limits.maximumDelayTicks()) {
                releaseCurrentReservation(slot, instance, attribution);
                return new RuntimeExecutionOutcome.ScheduleRejected(
                        RuntimeScheduleRejectionReason.DELAY_OUT_OF_RANGE);
            }
        }
        for (var child : children) {
            if (child.deadlineHorizonTicks() < 0
                    || child.deadlineHorizonTicks()
                            > slot.limits.maximumDeadlineHorizonTicks()) {
                releaseCurrentReservation(slot, instance, attribution);
                return new RuntimeExecutionOutcome.ScheduleRejected(
                        RuntimeScheduleRejectionReason.DEADLINE_OUT_OF_RANGE);
            }
        }
        var scheduledTicks = new long[childCount];
        for (var index = 0; index < childCount; index++) {
            try {
                scheduledTicks[index] = Math.addExact(
                        slot.runtimeTick, children.get(index).delayTicks());
            } catch (ArithmeticException ignored) {
                releaseCurrentReservation(slot, instance, attribution);
                return new RuntimeExecutionOutcome.ScheduleRejected(
                        RuntimeScheduleRejectionReason.DELAY_OVERFLOW);
            }
        }
        var requestedDeadlines = new long[childCount];
        for (var index = 0; index < childCount; index++) {
            try {
                requestedDeadlines[index] = Math.addExact(
                        slot.runtimeTick, children.get(index).deadlineHorizonTicks());
            } catch (ArithmeticException ignored) {
                releaseCurrentReservation(slot, instance, attribution);
                return new RuntimeExecutionOutcome.ScheduleRejected(
                        RuntimeScheduleRejectionReason.DEADLINE_OVERFLOW);
            }
        }
        for (var index = 0; index < childCount; index++) {
            if (scheduledTicks[index]
                    > Math.min(parent.deadlineRuntimeTick(), requestedDeadlines[index])) {
                releaseCurrentReservation(slot, instance, attribution);
                return new RuntimeExecutionOutcome.ScheduleRejected(
                        RuntimeScheduleRejectionReason.DEADLINE_BEFORE_SCHEDULED_TICK);
            }
        }
        if (instance.lifetimeEvents + childCount > slot.limits.eventsPerSkillInstance()) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.BudgetRejected(
                    RuntimeBudgetRejectionReason.LINEAGE_EVENT_LIMIT_EXCEEDED);
        }

        var pendingBreak = pendingBreak(slot, instance, attribution, childCount);
        if (pendingBreak != null) {
            releaseCurrentReservation(slot, instance, attribution);
            var selection = pendingBreak;
            instance.terminal = true;
            var removed = removeInstanceQueuedAndDeferred(slot, instance.id);
            var summary = new RuntimeCircuitBreakerSummary(
                    selection.reason,
                    selection.pendingBefore,
                    childCount,
                    selection.maximum,
                    removed,
                    true);
            return new RuntimeExecutionOutcome.CircuitBroken(summary);
        }
        if (childCount > reservation.capacity) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.BudgetRejected(
                    RuntimeBudgetRejectionReason.EVENT_SEQUENCE_CAPACITY_EXCEEDED);
        }

        var published = new RuntimeEvent[childCount];
        for (var index = 0; index < childCount; index++) {
            var child = children.get(index);
            published[index] = new RuntimeEvent(
                    new EventId(Math.addExact(reservation.eventIdStart, index + 1L)),
                    instance.id,
                    instance.sequence,
                    new RuntimeCancellationToken(slot.token, instance.id),
                    Optional.of(parent.eventId()),
                    parent.skillReference(),
                    child.nodeIndex(),
                    slot.runtimeTick,
                    scheduledTicks[index],
                    Math.min(parent.deadlineRuntimeTick(), requestedDeadlines[index]),
                    parent.depth() + 1,
                    index + 1,
                    parent.persistence(),
                    parent.budgetAttribution(),
                    child.origin(),
                    child.target(),
                    child.triggerCause(),
                    child.executionData());
        }
        for (var child : published) {
            convertReservedChildToCommitted(slot, instance, attribution, child);
        }
        releaseCurrentReservation(slot, instance, attribution);
        instance.lifetimeEvents += childCount;
        return new RuntimeExecutionOutcome.CompletedWithChildren(childCount);
    }

    private ChildReservation reserveForPort(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent event) {
        var remainingLineage = slot.limits.eventsPerSkillInstance() - instance.lifetimeEvents;
        var remainingDepth = slot.limits.maximumDepth() - event.depth();
        var instanceHeadroom = slot.limits.pendingEventsPerSkillInstance()
                - instance.committedPending - instance.reservedPending;
        var attributionHeadroom = slot.limits.pendingEventsPerAttribution()
                - attribution.committedPending - attribution.reservedPending;
        var serverHeadroom = slot.limits.pendingEventsPerServer()
                - slot.committedPending - slot.reservedPending;
        var eventIdHeadroom = Long.MAX_VALUE - slot.eventSequenceHighWater;
        var capacity = minimum(
                slot.limits.directChildrenPerEvent(),
                remainingLineage,
                remainingDepth == 0 ? 0 : RuntimeChildPlan.PHYSICAL_MAXIMUM,
                instanceHeadroom,
                attributionHeadroom,
                serverHeadroom,
                eventIdHeadroom > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) eventIdHeadroom);
        var zeroCapacity = Math.min(slot.limits.zeroDelayChildrenPerEvent(), capacity);
        var budget = new RuntimeExecutionBudget(
                capacity,
                zeroCapacity,
                remainingLineage,
                remainingDepth,
                slot.limits.maximumDelayTicks(),
                slot.limits.maximumDeadlineHorizonTicks(),
                instanceHeadroom,
                attributionHeadroom,
                serverHeadroom);
        var eventIdStart = slot.eventSequenceHighWater;
        if (capacity > 0) {
            slot.eventSequenceHighWater = Math.addExact(slot.eventSequenceHighWater, capacity);
            instance.reservedPending += capacity;
            attribution.reservedPending += capacity;
            slot.reservedPending += capacity;
            slot.currentReservationCount = capacity;
            slot.currentReservationOwner = instance.id;
        }
        return new ChildReservation(
                capacity,
                eventIdStart,
                budget);
    }

    private void releaseCurrentReservation(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution) {
        var count = slot.currentReservationCount;
        if (count == 0) {
            return;
        }
        if (!instance.id.equals(slot.currentReservationOwner)
                || instance.reservedPending < count
                || attribution.reservedPending < count
                || slot.reservedPending < count) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        instance.reservedPending -= count;
        attribution.reservedPending -= count;
        slot.reservedPending -= count;
        slot.currentReservationCount = 0;
        slot.currentReservationOwner = null;
    }

    private PendingBreak pendingBreak(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            int requested) {
        return selectPendingBreak(
                slot.limits,
                instance.committedPending,
                attribution.committedPending,
                slot.committedPending,
                instance.attribution,
                requested);
    }

    PendingBreak selectPendingBreak(
            P5RuntimeLimits limits,
            int instancePending,
            int attributionPending,
            int serverPending,
            RuntimeBudgetAttribution attribution,
            int requested) {
        return pendingBreak(
                limits,
                instancePending,
                attributionPending,
                serverPending,
                attribution,
                requested);
    }

    static PendingBreak pendingBreak(
            P5RuntimeLimits limits,
            int instancePending,
            int attributionPending,
            int serverPending,
            RuntimeBudgetAttribution attribution,
            int requested) {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(attribution, "attribution");
        if (instancePending < 0
                || instancePending > limits.pendingEventsPerSkillInstance()
                || attributionPending < 0
                || attributionPending > limits.pendingEventsPerAttribution()
                || serverPending < 0
                || serverPending > limits.pendingEventsPerServer()
                || requested <= 0
                || requested > RuntimeChildPlan.PHYSICAL_MAXIMUM) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        if (instancePending + requested > limits.pendingEventsPerSkillInstance()) {
            return new PendingBreak(
                    RuntimeCircuitBreakReason.SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED,
                    instancePending,
                    limits.pendingEventsPerSkillInstance());
        }
        if (attributionPending + requested > limits.pendingEventsPerAttribution()) {
            return new PendingBreak(
                    attribution instanceof PlayerRuntimeBudgetAttribution
                            ? RuntimeCircuitBreakReason.PLAYER_PENDING_EVENTS_EXCEEDED
                            : RuntimeCircuitBreakReason.NON_PLAYER_DOMAIN_PENDING_EVENTS_EXCEEDED,
                    attributionPending,
                    limits.pendingEventsPerAttribution());
        }
        if (serverPending + requested > limits.pendingEventsPerServer()) {
            return new PendingBreak(
                    RuntimeCircuitBreakReason.SERVER_PENDING_EVENTS_EXCEEDED,
                    serverPending,
                    limits.pendingEventsPerServer());
        }
        return null;
    }

    private static int compareEvents(RuntimeEvent left, RuntimeEvent right) {
        var compared = Long.compare(left.scheduledRuntimeTick(), right.scheduledRuntimeTick());
        if (compared != 0) {
            return compared;
        }
        compared = Long.compare(left.eventId().value(), right.eventId().value());
        if (compared != 0) {
            return compared;
        }
        compared = Long.compare(
                left.skillInstanceSequence().value(), right.skillInstanceSequence().value());
        if (compared != 0) {
            return compared;
        }
        compared = Integer.compare(left.nodeIndex(), right.nodeIndex());
        return compared != 0
                ? compared
                : Integer.compare(left.childSequence(), right.childSequence());
    }

    static List<RuntimeChildSpec> canonicalize(List<RuntimeChildSpec> children) {
        record IndexedChild(RuntimeChildSpec child, int originalOrdinal) {}
        var indexed = new java.util.ArrayList<IndexedChild>(children.size());
        for (var index = 0; index < children.size(); index++) {
            indexed.add(new IndexedChild(children.get(index), index));
        }
        indexed.sort((left, right) -> {
            var compared = Integer.compare(left.child().delayTicks(), right.child().delayTicks());
            if (compared != 0) {
                return compared;
            }
            compared = Integer.compare(left.child().nodeIndex(), right.child().nodeIndex());
            return compared != 0
                    ? compared
                    : Integer.compare(left.originalOrdinal(), right.originalOrdinal());
        });
        var ordered = new java.util.ArrayList<RuntimeChildSpec>(children.size());
        for (var value : indexed) {
            ordered.add(value.child());
        }
        return List.copyOf(ordered);
    }

    private LeaseAcquisition acquireLease(
            MinecraftServer server, ServerSlot slot, SkillReference reference) {
        var existing = slot.leases.get(reference);
        if (existing != null) {
            return new LeaseAcquisition.Available(existing, false);
        }
        var found = storeService.find(server, reference);
        if (found instanceof SkillSubsystemResult.Unavailable<Optional<SkillDocument>> unavailable) {
            return new LeaseAcquisition.Unavailable(
                    new SkillRevisionUnavailableReason.DefinitionSubsystemUnavailable(
                            unavailable.reason()));
        }
        var document = ((SkillSubsystemResult.Available<Optional<SkillDocument>>) found).value();
        if (document.isEmpty()) {
            return new LeaseAcquisition.Unavailable(
                    new SkillRevisionUnavailableReason.ExactRevisionMissing());
        }
        var context = policyProvider.snapshot(server).validationContext();
        var projection = projector.project(reference, document.orElseThrow(), context);
        if (projection instanceof P5RuntimeProjector.Projection.Unavailable) {
            return new LeaseAcquisition.Unavailable(
                    new SkillRevisionUnavailableReason.RuntimeProjectionUnavailable());
        }
        var definition = ((P5RuntimeProjector.Projection.Available) projection).definition();
        var pinned = storeService.pin(server, reference);
        if (pinned instanceof SkillSubsystemResult.Unavailable<Optional<ControlledSkillPin>> unavailable) {
            return new LeaseAcquisition.Unavailable(
                    new SkillRevisionUnavailableReason.DefinitionSubsystemUnavailable(
                            unavailable.reason()));
        }
        var pin = ((SkillSubsystemResult.Available<Optional<ControlledSkillPin>>) pinned).value();
        if (pin.isEmpty()) {
            return new LeaseAcquisition.Unavailable(
                    new SkillRevisionUnavailableReason.TransientPinUnavailable());
        }
        var exactPin = pin.orElseThrow();
        try {
            var lease = new RuntimeRevisionLease(reference, exactPin, definition);
            return new LeaseAcquisition.Available(lease, true);
        } catch (RuntimeException primary) {
            closePinAfterLeaseConstructionFailure(exactPin);
            throw primary;
        } catch (Error primary) {
            closePinAfterLeaseConstructionFailure(exactPin);
            throw primary;
        }
    }

    private static void closePinAfterLeaseConstructionFailure(ControlledSkillPin pin) {
        try {
            pin.close();
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // The lease-construction primary remains authoritative.
        }
    }

    private static void publishRoot(
            ServerSlot slot,
            RuntimeEvent event,
            RuntimeRevisionLease lease,
            LeaseAcquisition acquisition,
            ServerSlot.AttributionState existingAttribution) {
        if (acquisition instanceof LeaseAcquisition.Available available && available.provisional()) {
            slot.leases.put(lease.reference, lease);
        }
        lease.retain();
        var attribution = existingAttribution;
        if (attribution == null) {
            if (slot.attributions.size() == slot.limits.runtimeBudgetAttributionStatesPerServer()) {
                throw kernel(RuntimeKernelException.Code.ATTRIBUTION_STATE_CAPACITY_INVARIANT);
            }
            attribution = new ServerSlot.AttributionState(event.budgetAttribution());
            slot.attributions.put(event.budgetAttribution(), attribution);
        }
        attribution.activeInstances++;
        var instance = new ServerSlot.InstanceState(
                event.skillInstanceId(),
                event.skillInstanceSequence(),
                event.budgetAttribution(),
                lease);
        slot.instances.put(instance.id, instance);
        slot.skillInstanceSequenceHighWater = instance.sequence.value();
        slot.eventSequenceHighWater = event.eventId().value();
        addCommittedEvent(slot, instance, attribution, event);
        instance.lifetimeEvents = 1;
    }

    private static void releaseProvisionalLease(
            ServerSlot slot, LeaseAcquisition acquisition) {
        if (acquisition instanceof LeaseAcquisition.Available available
                && available.provisional()) {
            available.lease().close();
        }
    }

    private static void closeProvisionalAfterRootFault(
            LeaseAcquisition acquisition) {
        if (acquisition instanceof LeaseAcquisition.Available available
                && available.provisional()) {
            try {
                available.lease().close();
            } catch (RuntimeException | Error ignoredCleanupFailure) {
                // Publication's already-caught primary remains authoritative.
            }
        }
    }

    private static RuntimeCancellationResult cancelInstance(
            ServerSlot slot, SkillInstanceId instanceId) {
        var instance = slot.instances.get(instanceId);
        if (instance == null || instance.terminal) {
            return new RuntimeCancellationResult.NotPending();
        }
        if (instance.cancellationRequested) {
            return new RuntimeCancellationResult.AlreadyCancelled();
        }
        instance.cancellationRequested = true;
        var inFlight = slot.currentEvent != null
                && slot.currentEvent.skillInstanceId().equals(instanceId);
        var attribution = requireAttribution(slot, instance.attribution);
        if (inFlight) {
            releaseCurrentReservationStatic(slot, instance, attribution);
        }
        var removed = removeInstanceQueuedAndDeferred(slot, instanceId);
        if (inFlight) {
            return new RuntimeCancellationResult.CancellationRequested(removed);
        }
        instance.terminal = true;
        maybeRemoveInstance(slot, instanceId);
        return new RuntimeCancellationResult.CancelledSkillInstance(removed);
    }

    private static int removeInstanceQueuedAndDeferred(
            ServerSlot slot, SkillInstanceId instanceId) {
        var scratchCount = 0;
        var removed = 0;
        while (!slot.queue.isEmpty()) {
            var event = slot.queue.poll();
            if (event.skillInstanceId().equals(instanceId)) {
                removeCommittedEvent(slot, event);
                removed++;
            } else {
                if (scratchCount == slot.cleanupScratch.length) {
                    throw kernel(RuntimeKernelException.Code.BREAKER_SCRATCH_OVERFLOW);
                }
                slot.cleanupScratch[scratchCount++] = event;
            }
        }
        for (var index = 0; index < scratchCount; index++) {
            slot.queue.add(slot.cleanupScratch[index]);
            slot.cleanupScratch[index] = null;
        }
        var write = 0;
        for (var read = 0; read < slot.deferredCount; read++) {
            var event = slot.deferred[read];
            if (event.skillInstanceId().equals(instanceId)) {
                removeCommittedEvent(slot, event);
                removed++;
            } else {
                slot.deferred[write++] = event;
            }
        }
        Arrays.fill(slot.deferred, write, slot.deferredCount, null);
        slot.deferredCount = write;
        return removed;
    }

    static boolean removeExactQueuedOrDeferred(ServerSlot slot, RuntimeEvent event) {
        if (slot.queue.remove(event)) {
            return true;
        }
        for (var index = 0; index < slot.deferredCount; index++) {
            if (slot.deferred[index] == event) {
                var move = slot.deferredCount - index - 1;
                if (move > 0) {
                    System.arraycopy(slot.deferred, index + 1, slot.deferred, index, move);
                }
                slot.deferred[--slot.deferredCount] = null;
                return true;
            }
        }
        return false;
    }

    private static void addCommittedEvent(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent event) {
        if (slot.committedPending == MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SERVER) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        slot.queue.add(event);
        var previous = slot.eventIndex.put(event.eventId(), event);
        if (previous != null) {
            throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
        }
        instance.committedPending++;
        attribution.committedPending++;
        slot.committedPending++;
    }

    private static void convertReservedChildToCommitted(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent child) {
        if (slot.currentReservationCount <= 0
                || !instance.id.equals(slot.currentReservationOwner)
                || instance.reservedPending <= 0
                || attribution.reservedPending <= 0
                || slot.reservedPending <= 0) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        instance.reservedPending--;
        attribution.reservedPending--;
        slot.reservedPending--;
        slot.currentReservationCount--;
        if (slot.currentReservationCount == 0) {
            slot.currentReservationOwner = null;
        }
        addCommittedEvent(slot, instance, attribution, child);
    }

    private static void removeCommittedEvent(ServerSlot slot, RuntimeEvent event) {
        var indexed = slot.eventIndex.remove(event.eventId());
        if (indexed != event) {
            throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
        }
        var instance = slot.instances.get(event.skillInstanceId());
        if (instance == null || instance.committedPending <= 0 || slot.committedPending <= 0) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        var attribution = requireAttribution(slot, instance.attribution);
        if (attribution.committedPending <= 0) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        instance.committedPending--;
        attribution.committedPending--;
        slot.committedPending--;
    }

    private static void removeStaleOwnerEvent(ServerSlot slot, RuntimeEvent event) {
        var indexed = slot.eventIndex.remove(event.eventId());
        if (indexed != event || slot.committedPending <= 0) {
            throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
        }
        var attribution = requireAttribution(slot, event.budgetAttribution());
        if (attribution.committedPending <= 0) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        attribution.committedPending--;
        slot.committedPending--;
    }

    private RuntimeBudgetDecision executionDecision(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution) {
        return decideExecution(
                slot.limits,
                instance.executionsThisTick,
                attribution.executionsThisTick,
                instance.attribution);
    }

    RuntimeBudgetDecision decideExecution(
            P5RuntimeLimits limits,
            int instanceExecutions,
            int attributionExecutions,
            RuntimeBudgetAttribution attribution) {
        return executionDecision(
                limits, instanceExecutions, attributionExecutions, attribution);
    }

    static RuntimeBudgetDecision executionDecision(
            P5RuntimeLimits limits,
            int instanceExecutions,
            int attributionExecutions,
            RuntimeBudgetAttribution attribution) {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(attribution, "attribution");
        if (instanceExecutions < 0
                || instanceExecutions > limits.executionsPerSkillInstancePerTick()
                || attributionExecutions < 0
                || attributionExecutions > limits.executionsPerAttributionPerTick()) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        if (instanceExecutions == limits.executionsPerSkillInstancePerTick()) {
            return RuntimeBudgetDecision.DEFER_SKILL_INSTANCE_TICK_LIMIT;
        }
        if (attributionExecutions == limits.executionsPerAttributionPerTick()) {
            return attribution instanceof PlayerRuntimeBudgetAttribution
                    ? RuntimeBudgetDecision.DEFER_PLAYER_TICK_LIMIT
                    : RuntimeBudgetDecision.DEFER_NON_PLAYER_DOMAIN_TICK_LIMIT;
        }
        return RuntimeBudgetDecision.EXECUTE;
    }

    private static void claim(
            ServerSlot slot,
            RuntimeEvent event,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution) {
        slot.currentEvent = event;
        instance.inFlight = true;
        instance.executionsThisTick++;
        attribution.executionsThisTick++;
        slot.executionsThisTick++;
        slot.diagnostics.executionAttemptsThisTick++;
        var lag = schedulingLag(slot.runtimeTick, event);
        slot.diagnostics.maximumLagTicksThisTick = Math.max(
                slot.diagnostics.maximumLagTicksThisTick, lag);
        if (attribution.smallestContributingSequenceThisTick == null
                || instance.sequence.value()
                        < attribution.smallestContributingSequenceThisTick.value()) {
            attribution.smallestContributingSequenceThisTick = instance.sequence;
        }
        slot.diagnostics.observeInstanceOffender(instance);
        slot.diagnostics.observeAttributionOffender(attribution);
    }

    private static void terminalizeCurrent(
            ServerSlot slot, ServerSlot.InstanceState instance, RuntimeEvent event) {
        if (slot.currentEvent != event || !instance.inFlight) {
            throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
        }
        var attribution = requireAttribution(slot, instance.attribution);
        releaseCurrentReservationStatic(slot, instance, attribution);
        removeCommittedEvent(slot, event);
        instance.inFlight = false;
        slot.currentEvent = null;
        maybeRemoveInstance(slot, instance.id);
    }

    private static void releaseCurrentReservationStatic(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution) {
        var count = slot.currentReservationCount;
        if (count == 0) {
            return;
        }
        if (!instance.id.equals(slot.currentReservationOwner)
                || instance.reservedPending < count
                || attribution.reservedPending < count
                || slot.reservedPending < count) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        instance.reservedPending -= count;
        attribution.reservedPending -= count;
        slot.reservedPending -= count;
        slot.currentReservationCount = 0;
        slot.currentReservationOwner = null;
    }

    private static void maybeRemoveInstance(ServerSlot slot, SkillInstanceId instanceId) {
        var instance = slot.instances.get(instanceId);
        if (instance == null
                || instance.committedPending != 0
                || instance.reservedPending != 0
                || instance.inFlight) {
            return;
        }
        slot.instances.remove(instanceId);
        var attribution = requireAttribution(slot, instance.attribution);
        if (attribution.activeInstances <= 0) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        attribution.activeInstances--;
        if (instance.lease.release()) {
            var removed = slot.leases.remove(instance.lease.reference);
            if (removed != instance.lease) {
                throw kernel(RuntimeKernelException.Code.LEASE_ACCOUNTING_INVARIANT);
            }
        }
    }

    static void verifyQueuedIdentity(ServerSlot slot, RuntimeEvent event) {
        if (slot.eventIndex.get(event.eventId()) != event
                || !slot.token.equals(event.cancellationToken().serverSlotToken())
                || !event.skillInstanceId().equals(event.cancellationToken().skillInstanceId())) {
            throw kernel(RuntimeKernelException.Code.QUEUED_EVENT_IDENTITY_INVARIANT);
        }
    }

    private static void verifyQueuedOwnerIdentity(
            ServerSlot.InstanceState instance, RuntimeEvent event) {
        if (!instance.id.equals(event.skillInstanceId())
                || !instance.sequence.equals(event.skillInstanceSequence())
                || !instance.attribution.equals(event.budgetAttribution())
                || !instance.lease.reference.equals(event.skillReference())) {
            throw kernel(RuntimeKernelException.Code.QUEUED_EVENT_IDENTITY_INVARIANT);
        }
    }

    private static void defer(
            ServerSlot slot,
            RuntimeEvent event,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeBudgetDecision decision) {
        if (slot.deferredCount == slot.deferred.length) {
            throw kernel(RuntimeKernelException.Code.DEFERRED_BUFFER_OVERFLOW);
        }
        slot.deferred[slot.deferredCount++] = event;
        var lag = schedulingLag(slot.runtimeTick, event);
        slot.diagnostics.maximumLagTicksThisTick = Math.max(
                slot.diagnostics.maximumLagTicksThisTick, lag);
        switch (decision) {
            case EXECUTE -> throw new IllegalArgumentException("execute cannot be deferred");
            case DEFER_SKILL_INSTANCE_TICK_LIMIT -> {
                slot.diagnostics.instanceDeferralsThisTick++;
            }
            case DEFER_PLAYER_TICK_LIMIT -> {
                slot.diagnostics.playerDeferralsThisTick++;
            }
            case DEFER_NON_PLAYER_DOMAIN_TICK_LIMIT -> {
                slot.diagnostics.nonPlayerDeferralsThisTick++;
            }
        }
    }

    static void finishDeferred(ServerSlot slot) {
        for (var index = 0; index < slot.deferredCount; index++) {
            var event = slot.deferred[index];
            if (event != null) {
                slot.queue.add(event);
                slot.deferred[index] = null;
            }
        }
        slot.deferredCount = 0;
    }

    void reintegrateDeferred(ServerSlot slot) {
        finishDeferred(Objects.requireNonNull(slot, "slot"));
    }

    static void resetTickState(ServerSlot slot) {
        slot.rootAdmissionsThisTick = 0;
        slot.executionsThisTick = 0;
        slot.cancellationsThisTick = 0;
        slot.diagnostics.resetCurrentTick();
        slot.attributions.entrySet().removeIf(entry -> {
            var state = entry.getValue();
            return state.activeInstances == 0
                    && state.committedPending == 0
                    && state.reservedPending == 0
                    && state.executionEpoch < slot.runtimeTick;
        });
    }

    static RuntimeTickAdvanceResult advanceRuntimeTick(ServerSlot slot) {
        if (slot.runtimeTick == Long.MAX_VALUE) {
            return RuntimeTickAdvanceResult.EXHAUSTED;
        }
        slot.runtimeTick = Math.incrementExact(slot.runtimeTick);
        return RuntimeTickAdvanceResult.ADVANCED;
    }

    static OptionalLong checkedPositiveSuccessor(long highWater) {
        return highWater >= 0 && highWater < Long.MAX_VALUE
                ? OptionalLong.of(highWater + 1L)
                : OptionalLong.empty();
    }

    static boolean preferOffender(
            long candidateCount,
            long candidateSequence,
            long incumbentCount,
            long incumbentSequence) {
        return candidateCount > incumbentCount
                || candidateCount == incumbentCount && candidateSequence < incumbentSequence;
    }

    static long schedulingLag(long observedRuntimeTick, RuntimeEvent event) {
        Objects.requireNonNull(event, "event");
        if (observedRuntimeTick < event.scheduledRuntimeTick()) {
            throw new IllegalArgumentException("event has not reached its scheduled runtime tick");
        }
        return Math.subtractExact(observedRuntimeTick, event.scheduledRuntimeTick());
    }

    static void observeDrainStop(
            ServerSlot slot, RuntimeDrainStopReason reason) {
        switch (reason) {
            case SERVER_EXECUTION_LIMIT_REACHED -> {
                if (!slot.diagnostics.serverExecutionLimitReachedThisTick) {
                    slot.diagnostics.serverExecutionLimitReachedThisTick = true;
                    slot.diagnostics.serverExecutionLimitReachedTickTotal = saturatingIncrement(
                            slot.diagnostics.serverExecutionLimitReachedTickTotal);
                }
            }
        }
    }

    private static void resetExecutionEpoch(
            ServerSlot.InstanceState instance, long runtimeTick) {
        if (instance.executionEpoch != runtimeTick) {
            instance.executionEpoch = runtimeTick;
            instance.executionsThisTick = 0;
        }
    }

    private static void resetExecutionEpoch(
            ServerSlot.AttributionState attribution, long runtimeTick) {
        if (attribution.executionEpoch != runtimeTick) {
            attribution.executionEpoch = runtimeTick;
            attribution.executionsThisTick = 0;
            attribution.smallestContributingSequenceThisTick = null;
        }
    }

    static void observeExpiry(ServerSlot slot, RuntimeEvent event) {
        slot.diagnostics.deadlineExpiredEventsThisTick++;
        slot.diagnostics.deadlineExpiredEventTotal = saturatingIncrement(
                slot.diagnostics.deadlineExpiredEventTotal);
        slot.diagnostics.maximumDeadlineLatenessTicksThisTick = Math.max(
                slot.diagnostics.maximumDeadlineLatenessTicksThisTick,
                slot.runtimeTick - event.deadlineRuntimeTick());
        slot.diagnostics.maximumLagTicksThisTick = Math.max(
                slot.diagnostics.maximumLagTicksThisTick,
                schedulingLag(slot.runtimeTick, event));
    }

    static boolean deadlineExpired(ServerSlot slot, RuntimeEvent event) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(event, "event");
        return slot.runtimeTick > event.deadlineRuntimeTick();
    }

    static RuntimeExecutionGuardDecision runtimeExecutionGuardDecision(
            ServerSlot slot, ServerSlot.InstanceState instance, RuntimeEvent event) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(event, "event");
        if (slot.state != ServerSlot.State.RUNNING || instance.cancellationRequested) {
            return RuntimeExecutionGuardDecision.CANCELLED;
        }
        return deadlineExpired(slot, event)
                ? RuntimeExecutionGuardDecision.DEADLINE_EXCEEDED
                : RuntimeExecutionGuardDecision.ALLOWED;
    }

    void observeExpired(ServerSlot slot, RuntimeEvent event) {
        observeExpiry(
                Objects.requireNonNull(slot, "slot"),
                Objects.requireNonNull(event, "event"));
    }

    private static void observeOutcome(ServerSlot slot, RuntimeExecutionOutcome outcome) {
        slot.diagnostics.typedOutcomesThisTick++;
    }

    static void recordBreaker(
            ServerSlot slot,
            SkillInstanceId instanceId,
            Optional<RuntimeSkillInstanceSequence> sequence,
            Optional<EventId> eventId,
            Optional<RuntimePlayerId> player,
            RuntimeCircuitBreakerSummary summary) {
        slot.diagnostics.breakerRing[slot.diagnostics.breakerWriteIndex] =
                new ServerSlot.BreakerDiagnostic(
                        summary.reason(),
                        slot.runtimeTick,
                        instanceId,
                        sequence,
                        eventId,
                        player,
                        summary.pendingBefore(),
                        summary.requestedAdditionalCount(),
                        summary.maximum(),
                        summary.removedQueuedAndDeferredCount(),
                        summary.eventInFlight());
        slot.diagnostics.breakerWriteIndex =
                (slot.diagnostics.breakerWriteIndex + 1)
                        % MagicSafetyCeilings.MAX_BREAKER_DIAGNOSTIC_RECORDS_PER_SERVER;
        var reasonIndex = summary.reason().ordinal();
        slot.diagnostics.breakerTotals[reasonIndex] = saturatingIncrement(
                slot.diagnostics.breakerTotals[reasonIndex]);
        slot.diagnostics.breakerTripsThisTick++;
        slot.diagnostics.breakerRemovalsThisTick += summary.removedQueuedAndDeferredCount();
    }

    void observeBreaker(
            ServerSlot slot,
            SkillInstanceId instanceId,
            Optional<RuntimeSkillInstanceSequence> sequence,
            Optional<EventId> eventId,
            Optional<RuntimePlayerId> player,
            RuntimeCircuitBreakerSummary summary) {
        recordBreaker(slot, instanceId, sequence, eventId, player, summary);
    }

    private static Optional<RuntimePlayerId> playerId(
            RuntimeBudgetAttribution attribution) {
        return attribution instanceof PlayerRuntimeBudgetAttribution player
                ? Optional.of(player.playerId())
                : Optional.empty();
    }

    private static Optional<InvalidEventReason> validateRootStableShape(
            ServerSlot slot, RuntimeRootEventSpec spec) {
        if (spec.nodeIndex() < 0 || spec.nodeIndex() >= 256) {
            return Optional.of(InvalidEventReason.INVALID_NODE_COORDINATE);
        }
        if (!(spec.executionData() instanceof NoRuntimeExecutionData)) {
            return Optional.of(InvalidEventReason.INVALID_EXECUTION_DATA);
        }
        if (spec.origin() instanceof PlayerOrigin playerOrigin) {
            if (!(spec.budgetAttribution() instanceof PlayerRuntimeBudgetAttribution attribution)) {
                return Optional.of(InvalidEventReason.INVALID_BUDGET_ATTRIBUTION);
            }
            if (!playerOrigin.player().equals(attribution.playerId())) {
                return Optional.of(InvalidEventReason.BUDGET_ATTRIBUTION_MISMATCH);
            }
        }
        if (!(spec.budgetAttribution() instanceof PlayerRuntimeBudgetAttribution)
                && !(spec.budgetAttribution() instanceof NonPlayerRuntimeBudgetAttribution)) {
            return Optional.of(InvalidEventReason.INVALID_BUDGET_ATTRIBUTION);
        }
        if (!slot.token.equals(spec.budgetAttribution().server())) {
            return Optional.of(InvalidEventReason.INVALID_BUDGET_ATTRIBUTION);
        }
        return Optional.empty();
    }

    private static Optional<InvalidEventReason> validateRootDefinitionShape(
            ValidatedSkillDefinition definition, RuntimeRootEventSpec spec) {
        if (!definition.reference().equals(spec.skillReference())
                || spec.nodeIndex() < 0
                || spec.nodeIndex() >= definition.nodes().size()) {
            return Optional.of(InvalidEventReason.INVALID_NODE_COORDINATE);
        }
        var capabilities = definition.nodes().get(spec.nodeIndex())
                .trigger().descriptor().capabilities();
        if (!capabilities.eventKinds().contains(spec.triggerCause().eventKind())) {
            return Optional.of(InvalidEventReason.INVALID_TRIGGER_CAUSE);
        }
        if (capabilities.sourceRequirement() != SourceRequirement.NONE) {
            return Optional.of(InvalidEventReason.INVALID_REFERENCE_SHAPE);
        }
        if (capabilities.targetRequirement() == TargetRequirement.NONE
                && spec.target().isPresent()
                || capabilities.targetRequirement() == TargetRequirement.REQUIRED
                        && spec.target().isEmpty()) {
            return Optional.of(InvalidEventReason.INVALID_REFERENCE_SHAPE);
        }
        if (capabilities.requiresContinuationState()
                && spec.executionData() == NoRuntimeExecutionData.INSTANCE) {
            return Optional.of(InvalidEventReason.INVALID_EXECUTION_DATA);
        }
        return Optional.empty();
    }

    private static Optional<InvalidEventReason> validateChildShape(
            ValidatedSkillDefinition definition,
            RuntimeBudgetAttribution inheritedAttribution,
            RuntimeChildSpec child) {
        if (child.nodeIndex() < 0 || child.nodeIndex() >= definition.nodes().size()) {
            return Optional.of(InvalidEventReason.INVALID_NODE_COORDINATE);
        }
        if (child.origin() instanceof PlayerOrigin playerOrigin
                && (!(inheritedAttribution instanceof PlayerRuntimeBudgetAttribution playerAttribution)
                        || !playerOrigin.player().equals(playerAttribution.playerId()))) {
            return Optional.of(InvalidEventReason.INVALID_REFERENCE_SHAPE);
        }
        var capabilities = definition.nodes().get(child.nodeIndex())
                .trigger().descriptor().capabilities();
        if (!capabilities.eventKinds().contains(child.triggerCause().eventKind())) {
            return Optional.of(InvalidEventReason.INVALID_TRIGGER_CAUSE);
        }
        if (capabilities.sourceRequirement() != SourceRequirement.PRIOR_NODE) {
            return Optional.of(InvalidEventReason.INVALID_NODE_CAPABILITY);
        }
        if (capabilities.targetRequirement() == TargetRequirement.NONE
                && child.target().isPresent()
                || capabilities.targetRequirement() == TargetRequirement.REQUIRED
                        && child.target().isEmpty()) {
            return Optional.of(InvalidEventReason.INVALID_NODE_CAPABILITY);
        }
        if (capabilities.requiresContinuationState()
                && child.executionData() == NoRuntimeExecutionData.INSTANCE) {
            return Optional.of(InvalidEventReason.INVALID_EXECUTION_DATA);
        }
        return Optional.empty();
    }

    private static boolean stableTokensMatch(
            RuntimeServerToken token,
            RuntimeOrigin origin,
            Optional<RuntimeTarget> target) {
        if (!token.equals(serverOf(origin))) {
            return false;
        }
        return target.isEmpty() || token.equals(serverOf(target.orElseThrow()));
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

    private static RuntimeReferenceFailureReason referenceFailure(
            RuntimeReferenceResolutionOutcome outcome) {
        return switch (outcome) {
            case RuntimeReferenceResolutionOutcome.Resolved ignored ->
                    throw new IllegalArgumentException("resolved outcome has no failure");
            case RuntimeReferenceResolutionOutcome.SourceMissing failure -> failure.reason();
            case RuntimeReferenceResolutionOutcome.TargetMissing failure -> failure.reason();
            case RuntimeReferenceResolutionOutcome.InvalidRuntimeReference failure -> failure.reason();
        };
    }

    private static RuntimeServerToken serverToken(RuntimeCancellationHandle handle) {
        return switch (handle) {
            case RuntimeCancellationToken value -> value.serverSlotToken();
            case RuntimeEventToken value -> value.serverSlotToken();
        };
    }

    private static RuntimeAdmissionResult admissionForClosedSlot(ServerSlot slot) {
        return switch (slot.state) {
            case FAULTED -> new RuntimeAdmissionResult.KernelFaulted();
            case EXHAUSTED -> new RuntimeAdmissionResult.TickExhausted();
            case STOPPING -> new RuntimeAdmissionResult.ServerStopping();
            case REMOVED -> new RuntimeAdmissionResult.ServerNotRunning();
            case RUNNING -> throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        };
    }

    private static ServerSlot.AttributionState requireAttribution(
            ServerSlot slot, RuntimeBudgetAttribution key) {
        var attribution = slot.attributions.get(key);
        if (attribution == null) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        return attribution;
    }

    static void enterStopping(ServerSlot slot) {
        if (slot.state == ServerSlot.State.REMOVED || slot.state == ServerSlot.State.STOPPING) {
            return;
        }
        slot.state = ServerSlot.State.STOPPING;
        clearSlotNormal(slot);
    }

    void stopSlot(ServerSlot slot) {
        enterStopping(Objects.requireNonNull(slot, "slot"));
    }

    RuntimeException preserveRuntimeFault(ServerSlot slot, RuntimeException primary) {
        Objects.requireNonNull(primary, "primary");
        enterFaultAfterRuntimeException(Objects.requireNonNull(slot, "slot"));
        return primary;
    }

    Error preserveErrorFault(ServerSlot slot, Error primary) {
        Objects.requireNonNull(primary, "primary");
        enterFaultAfterError(Objects.requireNonNull(slot, "slot"));
        return primary;
    }

    static void enterFaultAfterRuntimeException(ServerSlot slot) {
        slot.state = ServerSlot.State.FAULTED;
        try {
            clearSlotAfterRuntimeException(slot);
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // The already-caught primary is authoritative and is never masked or retained.
        }
    }

    private static void enterFaultAfterError(ServerSlot slot) {
        slot.state = ServerSlot.State.FAULTED;
        try {
            clearSlotAfterError(slot);
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // Error cleanup is best effort and never masks or retains the primary identity.
        }
    }

    private static void clearSlotNormal(ServerSlot slot) {
        slot.queue.clear();
        slot.eventIndex.clear();
        Arrays.fill(slot.deferred, null);
        Arrays.fill(slot.cleanupScratch, null);
        slot.deferredCount = 0;
        slot.currentEvent = null;
        slot.currentReservationCount = 0;
        slot.currentReservationOwner = null;
        slot.committedPending = 0;
        slot.reservedPending = 0;
        slot.rootAdmissionsThisTick = 0;
        slot.executionsThisTick = 0;
        slot.cancellationsThisTick = 0;
        slot.instances.clear();
        slot.attributions.clear();
        for (var lease : slot.leases.values()) {
            lease.close();
        }
        slot.leases.clear();
        slot.diagnostics.clear();
    }

    private static void clearSlotAfterRuntimeException(ServerSlot slot) {
        slot.queue.clear();
        slot.eventIndex.clear();
        Arrays.fill(slot.deferred, null);
        Arrays.fill(slot.cleanupScratch, null);
        slot.deferredCount = 0;
        slot.currentEvent = null;
        slot.currentReservationCount = 0;
        slot.currentReservationOwner = null;
        slot.committedPending = 0;
        slot.reservedPending = 0;
        slot.rootAdmissionsThisTick = 0;
        slot.cancellationsThisTick = 0;
        slot.instances.clear();
        slot.attributions.clear();
        slot.diagnostics.clearAfterFaultPreservingStartedCounters();
        for (var lease : slot.leases.values()) {
            try {
                lease.close();
            } catch (RuntimeException | Error ignoredCleanupFailure) {
                // Continue the bounded close pass; the primary must keep its identity.
            }
        }
        slot.leases.clear();
    }

    static void clearSlotAfterError(ServerSlot slot) {
        slot.queue.clear();
        slot.eventIndex.clear();
        for (var index = 0; index < slot.deferred.length; index++) {
            slot.deferred[index] = null;
        }
        for (var index = 0; index < slot.cleanupScratch.length; index++) {
            slot.cleanupScratch[index] = null;
        }
        slot.deferredCount = 0;
        slot.currentEvent = null;
        slot.currentReservationCount = 0;
        slot.currentReservationOwner = null;
        slot.committedPending = 0;
        slot.reservedPending = 0;
        slot.rootAdmissionsThisTick = 0;
        slot.cancellationsThisTick = 0;
        slot.instances.clear();
        slot.attributions.clear();
        slot.diagnostics.clearAfterFaultPreservingStartedCounters();
    }

    private static int minimum(int first, int... remaining) {
        var minimum = first;
        for (var value : remaining) {
            minimum = Math.min(minimum, value);
        }
        return minimum;
    }

    private static long saturatingIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    private static RuntimeKernelException kernel(RuntimeKernelException.Code code) {
        return new RuntimeKernelException(code);
    }

    private sealed interface LeaseAcquisition
            permits LeaseAcquisition.Available, LeaseAcquisition.Unavailable {
        record Available(RuntimeRevisionLease lease, boolean provisional)
                implements LeaseAcquisition {
            public Available {
                Objects.requireNonNull(lease, "lease");
            }
        }

        record Unavailable(SkillRevisionUnavailableReason reason)
                implements LeaseAcquisition {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    private sealed interface DetachedInvocation
            permits AbortedInvocation,
                    TerminalInvocation,
                    PortInvocation,
                    NullBatchInvocation {}

    private enum AbortedInvocation implements DetachedInvocation {
        INSTANCE
    }

    private record TerminalInvocation(RuntimeExecutionOutcome outcome)
            implements DetachedInvocation {
        TerminalInvocation {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    private record PortInvocation(
            ChildReservation reservation, RuntimeExecutionBatch batch)
            implements DetachedInvocation {
        PortInvocation {
            Objects.requireNonNull(reservation, "reservation");
            Objects.requireNonNull(batch, "batch");
        }
    }

    private record NullBatchInvocation(ChildReservation reservation)
            implements DetachedInvocation {
        NullBatchInvocation {
            Objects.requireNonNull(reservation, "reservation");
        }
    }

    private record ChildReservation(
            int capacity,
            long eventIdStart,
            RuntimeExecutionBudget budget) {}

    record PendingBreak(
            RuntimeCircuitBreakReason reason, int pendingBefore, int maximum) {}
}

/** One exact server-slot state graph owned exclusively by {@link SkillRuntimeService}. */
final class ServerSlot {
    enum State {
        RUNNING,
        STOPPING,
        REMOVED,
        EXHAUSTED,
        FAULTED
    }

    final RuntimeServerToken token;
    final P5RuntimeLimits limits;
    final PriorityQueue<RuntimeEvent> queue = new PriorityQueue<>(
            MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SERVER,
            SkillRuntimeService.EVENT_ORDER);
    final Map<EventId, RuntimeEvent> eventIndex = new HashMap<>(5_462);
    final Map<SkillInstanceId, InstanceState> instances = new HashMap<>(171);
    final Map<RuntimeBudgetAttribution, AttributionState> attributions = new HashMap<>(256);
    final Map<SkillReference, RuntimeRevisionLease> leases = new HashMap<>(171);
    final RuntimeEvent[] deferred =
            new RuntimeEvent[MagicSafetyCeilings.MAX_BUDGET_DEFERRED_EVENTS];
    final RuntimeEvent[] cleanupScratch =
            new RuntimeEvent[MagicSafetyCeilings.MAX_BREAKER_CLEANUP_SCRATCH_EVENTS];
    final Diagnostics diagnostics = new Diagnostics();
    State state = State.RUNNING;
    long runtimeTick;
    long eventSequenceHighWater;
    long skillInstanceSequenceHighWater;
    int rootAdmissionsThisTick;
    int executionsThisTick;
    int cancellationsThisTick;
    int committedPending;
    int reservedPending;
    int deferredCount;
    int currentReservationCount;
    SkillInstanceId currentReservationOwner;
    RuntimeEvent currentEvent;
    boolean dispatching;

    ServerSlot(RuntimeServerToken token, P5RuntimeLimits limits) {
        this.token = Objects.requireNonNull(token, "token");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    static final class InstanceState {
        final SkillInstanceId id;
        final RuntimeSkillInstanceSequence sequence;
        final RuntimeBudgetAttribution attribution;
        final RuntimeRevisionLease lease;
        int committedPending;
        int reservedPending;
        int lifetimeEvents;
        long executionEpoch = -1L;
        int executionsThisTick;
        boolean inFlight;
        boolean cancellationRequested;
        boolean terminal;

        InstanceState(
                SkillInstanceId id,
                RuntimeSkillInstanceSequence sequence,
                RuntimeBudgetAttribution attribution,
                RuntimeRevisionLease lease) {
            this.id = Objects.requireNonNull(id, "id");
            this.sequence = Objects.requireNonNull(sequence, "sequence");
            this.attribution = Objects.requireNonNull(attribution, "attribution");
            this.lease = Objects.requireNonNull(lease, "lease");
        }
    }

    static final class AttributionState {
        final RuntimeBudgetAttribution attribution;
        int activeInstances;
        int committedPending;
        int reservedPending;
        long executionEpoch = -1L;
        int executionsThisTick;
        RuntimeSkillInstanceSequence smallestContributingSequenceThisTick;

        AttributionState(RuntimeBudgetAttribution attribution) {
            this.attribution = Objects.requireNonNull(attribution, "attribution");
        }
    }

    record BreakerDiagnostic(
            RuntimeCircuitBreakReason reason,
            long runtimeTick,
            SkillInstanceId instanceId,
            Optional<RuntimeSkillInstanceSequence> sequence,
            Optional<EventId> eventId,
            Optional<RuntimePlayerId> playerId,
            int pendingBefore,
            int requested,
            int maximum,
            int removed,
            boolean eventInFlight) {
        BreakerDiagnostic {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(sequence, "sequence");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    static final class Diagnostics {
        final BreakerDiagnostic[] breakerRing = new BreakerDiagnostic[
                MagicSafetyCeilings.MAX_BREAKER_DIAGNOSTIC_RECORDS_PER_SERVER];
        final long[] breakerTotals = new long[4];
        int breakerWriteIndex;
        long rootAdmissionAttemptsThisTick;
        long executionAttemptsThisTick;
        long portInvocationsThisTick;
        long typedOutcomesThisTick;
        long breakerTripsThisTick;
        long breakerRemovalsThisTick;
        long instanceDeferralsThisTick;
        long playerDeferralsThisTick;
        long nonPlayerDeferralsThisTick;
        long maximumLagTicksThisTick;
        long deadlineExpiredEventsThisTick;
        long maximumDeadlineLatenessTicksThisTick;
        long deadlineExpiredEventTotal;
        boolean serverExecutionLimitReachedThisTick;
        long serverExecutionLimitReachedTickTotal;
        InstanceOffender instanceOffender;
        AttributionOffender attributionOffender;

        void resetCurrentTick() {
            rootAdmissionAttemptsThisTick = 0;
            executionAttemptsThisTick = 0;
            portInvocationsThisTick = 0;
            typedOutcomesThisTick = 0;
            breakerTripsThisTick = 0;
            breakerRemovalsThisTick = 0;
            instanceDeferralsThisTick = 0;
            playerDeferralsThisTick = 0;
            nonPlayerDeferralsThisTick = 0;
            maximumLagTicksThisTick = 0;
            deadlineExpiredEventsThisTick = 0;
            maximumDeadlineLatenessTicksThisTick = 0;
            serverExecutionLimitReachedThisTick = false;
            instanceOffender = null;
            attributionOffender = null;
        }

        void observeInstanceOffender(InstanceState instance) {
            var candidate = new InstanceOffender(instance.id, instance.sequence, instance.executionsThisTick);
            if (instanceOffender == null
                    || SkillRuntimeService.preferOffender(
                            candidate.count,
                            candidate.sequence.value(),
                            instanceOffender.count,
                            instanceOffender.sequence.value())) {
                instanceOffender = candidate;
            }
        }

        void observeAttributionOffender(AttributionState attribution) {
            var sequence = Objects.requireNonNull(
                    attribution.smallestContributingSequenceThisTick,
                    "smallestContributingSequenceThisTick");
            var candidate = new AttributionOffender(
                    attribution.attribution, sequence, attribution.executionsThisTick);
            if (attributionOffender == null
                    || SkillRuntimeService.preferOffender(
                            candidate.count,
                            candidate.sequence.value(),
                            attributionOffender.count,
                            attributionOffender.sequence.value())) {
                attributionOffender = candidate;
            }
        }

        void clear() {
            Arrays.fill(breakerRing, null);
            Arrays.fill(breakerTotals, 0L);
            breakerWriteIndex = 0;
            deadlineExpiredEventTotal = 0;
            serverExecutionLimitReachedTickTotal = 0;
            resetCurrentTick();
        }

        void clearAfterFaultPreservingStartedCounters() {
            var startedExecutions = executionAttemptsThisTick;
            var enteredPorts = portInvocationsThisTick;
            clear();
            executionAttemptsThisTick = startedExecutions;
            portInvocationsThisTick = enteredPorts;
        }
    }

    private record InstanceOffender(
            SkillInstanceId id, RuntimeSkillInstanceSequence sequence, int count) {}

    private record AttributionOffender(
            RuntimeBudgetAttribution attribution,
            RuntimeSkillInstanceSequence sequence,
            int count) {}
}

/** One shared exact-revision transient pin and immutable runtime projection. */
final class RuntimeRevisionLease {
    final SkillReference reference;
    final ControlledSkillPin pin;
    final ValidatedSkillDefinition definition;
    private int instanceReferences;
    private boolean closed;

    RuntimeRevisionLease(
            SkillReference reference,
            ControlledSkillPin pin,
            ValidatedSkillDefinition definition) {
        this.reference = Objects.requireNonNull(reference, "reference");
        this.pin = Objects.requireNonNull(pin, "pin");
        this.definition = Objects.requireNonNull(definition, "definition");
        if (!reference.equals(definition.reference()) || !reference.equals(pin.reference())) {
            throw new IllegalArgumentException("runtime revision lease reference mismatch");
        }
    }

    void retain() {
        if (closed || instanceReferences == Integer.MAX_VALUE) {
            throw new RuntimeKernelException(RuntimeKernelException.Code.LEASE_ACCOUNTING_INVARIANT);
        }
        instanceReferences++;
    }

    boolean release() {
        if (closed || instanceReferences <= 0) {
            throw new RuntimeKernelException(RuntimeKernelException.Code.LEASE_ACCOUNTING_INVARIANT);
        }
        instanceReferences--;
        if (instanceReferences == 0) {
            close();
            return true;
        }
        return false;
    }

    void close() {
        if (!closed) {
            pin.close();
            closed = true;
            instanceReferences = 0;
        }
    }
}
