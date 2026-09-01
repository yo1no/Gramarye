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
