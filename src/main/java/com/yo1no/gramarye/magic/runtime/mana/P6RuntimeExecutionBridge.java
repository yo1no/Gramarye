package com.yo1no.gramarye.magic.runtime.mana;

import com.yo1no.gramarye.P6RuntimeExecutionCapability;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Capability-gated public entry into the package-private P6 transaction core. */
public final class P6RuntimeExecutionBridge {
    private static final ActionExecutorRegistry PRODUCTION_EXECUTORS =
            new ActionExecutorRegistry(List.of(
                    new ActionExecutorRegistration(
                            ResourceLocation.fromNamespaceAndPath(
                                    "gramarye", "spawn_projectile"),
                            new SpawnProjectileActionExecutor()),
                    new ActionExecutorRegistration(
                            ResourceLocation.fromNamespaceAndPath("gramarye", "damage"),
                            new DamageActionExecutor())));
    private static final ActionDamageTransactionEngine PRODUCTION_ENGINE =
            new ActionDamageTransactionEngine(
                    PRODUCTION_EXECUTORS,
                    new DamageEffectResolver(),
                    new EffectExecutionEngine(),
                    new ManaTransactionService());

    private P6RuntimeExecutionBridge() {}

    public static void execute(
            P6RuntimeExecutionCapability capability,
            ServerPlayer actor,
            Invocation input,
            GuardPort guard,
            WorldCommitPort commitPort,
            AppliedFactObserver observer) {
        if (capability == null) {
            throw invariant();
        }
        if (actor == null
                || input == null
                || guard == null
                || commitPort == null
                || observer == null) {
            throw invariant();
        }

        ActionInvocation invocation = invocation(input);
        ManaAccountAccess account = new PlayerManaAccountAccess(actor);
        EffectExecutionGuard executionGuard = adaptGuard(guard);
        EffectCommitPort effectCommitPort = adaptCommitPort(commitPort);
        executeCore(
                invocation,
                account,
                executionGuard,
                PRODUCTION_ENGINE,
                effectCommitPort,
                observer);
    }

    public static V0TerminalReceipt executeV0(
            P6RuntimeExecutionCapability.V0 capability,
            ServerPlayer actor,
            Invocation input,
            int statementOrdinal,
            P6RuntimeExecutionCapability.V0Call call) {
        if (capability == null) {
            throw invariant();
        }
        if (actor == null || input == null || call == null) {
            throw invariant();
        }
        ActionInvocation action = invocation(input);
        if (statementOrdinal != (input instanceof SpawnProjectileInvocation ? -1 : 0)) {
            return receipt(action, statementOrdinal,
                    V0Terminal.REJECTED_BEFORE_COMMIT, V0Reason.INVALID_REQUEST);
        }
        if (!actor.getServer().isSameThread()) {
            return receipt(action, statementOrdinal,
                    V0Terminal.REJECTED_BEFORE_COMMIT, V0Reason.ACTOR_OR_SOURCE_INVALID);
        }
        long magnitude = input instanceof DamageInvocation damage ? damage.magnitude() : 0;
        V0TransactionOutcome outcome = PRODUCTION_ENGINE.executeV0(action,
                new V0ManaAccountAccess(actor), adaptGuard(call),
                new V0EffectResolver(action.manaCost(), magnitude), new V0NativeCommitAdapter(call),
                new V0TransactionGuard(call));
        if (outcome instanceof V0UnresolvedTransaction) {
            return receipt(action, statementOrdinal,
                    V0Terminal.OUTCOME_UNKNOWN, V0Reason.NATIVE_OUTCOME_UNCERTAIN);
        }
        ActionDamageTransactionResult transaction = ((V0KnownTransaction) outcome).result();
        handleResult(transaction);
        return projectV0(action, statementOrdinal, transaction.effectResult());
    }

    private static V0TerminalReceipt projectV0(
            ActionInvocation input, int statementOrdinal, EffectExecutionResult result) {
        return switch (result.status()) {
            case REJECTED -> receipt(input, statementOrdinal, V0Terminal.REJECTED_BEFORE_COMMIT,
                    switch (result.rejectReason().orElseThrow()) {
                        case UNSUPPORTED_ACTION, INVALID_REQUEST, BOUND_EXCEEDED ->
                                V0Reason.INVALID_REQUEST;
                        case INVALID_TARGET, TARGET_UNAVAILABLE, COMMIT_PORT_UNAVAILABLE ->
                                V0Reason.TARGET_OR_PORT_UNAVAILABLE;
                        case INSUFFICIENT_MANA -> V0Reason.INSUFFICIENT_MANA;
                        case MANA_STATE_UNAVAILABLE -> V0Reason.MANA_STATE_UNAVAILABLE;
                        case CANCELLED -> V0Reason.ACTOR_OR_SOURCE_INVALID;
                        case DEADLINE_EXCEEDED -> V0Reason.DEADLINE_EXCEEDED;
                    });
            case FAILED -> receipt(input, statementOrdinal,
                    V0Terminal.CONFIRMED_NOT_APPLIED, V0Reason.NATIVE_NOT_APPLIED);
            case SUCCEEDED -> receipt(input, statementOrdinal,
                    V0Terminal.CONFIRMED_APPLIED, V0Reason.NONE);
            case PARTIALLY_SUCCEEDED -> receipt(input, statementOrdinal,
                    V0Terminal.CONFIRMED_APPLIED_WITH_FAILURE, V0Reason.APPLIED_POSTCONDITION_FAILED);
            case COMPENSATED -> receipt(input, statementOrdinal,
                    V0Terminal.COMPENSATED_NO_PRIMARY_MUTATION,
                    switch (result.failureReason().orElseThrow()) {
                        case PRIMARY_STEP_NOT_APPLIED -> V0Reason.NATIVE_NOT_APPLIED;
                        case EXECUTION_CANCELLED -> V0Reason.ACTOR_OR_SOURCE_INVALID;
                        case EXECUTION_DEADLINE_EXCEEDED -> V0Reason.DEADLINE_EXCEEDED;
                        case PRIMARY_STEP_APPLIED_WITH_FAILURE, COMPENSATION_REFUND_FAILED ->
                                throw invariant();
                    });
            case COMPENSATION_FAILED -> throw invariant();
        };
    }

    private static V0TerminalReceipt receipt(
            ActionInvocation input, int ordinal, V0Terminal terminal, V0Reason reason) {
        return new V0TerminalReceipt(input.requestId().value(), input.sourceEventId().value(),
                ordinal, terminal, reason);
    }

    static ActionInvocation invocation(Invocation input) {
        if (input == null) {
            throw invariant();
        }
        if (input instanceof SpawnProjectileInvocation spawn) {
            return new SpawnProjectileActionInvocation(
                    spawn.actionTypeKey(),
                    new EffectRequestId(spawn.requestId()),
                    new SourceEventId(spawn.sourceEventId()),
                    spawn.dimension(),
                    spawn.originX(),
                    spawn.originY(),
                    spawn.originZ(),
                    spawn.directionXQ15(),
                    spawn.directionYQ15(),
                    spawn.directionZQ15(),
                    spawn.profileCode(),
                    spawn.manaCost(),
                    CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION);
        }
        if (input instanceof DamageInvocation damage) {
            return new DamageActionInvocation(
                    damage.actionTypeKey(),
                    new EffectRequestId(damage.requestId()),
                    new SourceEventId(damage.sourceEventId()),
                    new DamageTargetReference(damage.targetId()),
                    damage.magnitude(),
                    damage.manaCost(),
                    CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION);
        }
        throw invariant();
    }

    static EffectExecutionGuard adaptGuard(GuardPort guard) {
        if (guard == null) {
            throw invariant();
        }
        return point -> {
            if (point == null) {
                throw invariant();
            }
            GuardPoint publicPoint = switch (point.kind()) {
                case ENTRY -> GuardPoint.ENTRY;
                case PRE_COMMIT -> GuardPoint.PRE_COMMIT;
                case BEFORE_STEP -> GuardPoint.BEFORE_STEP;
            };
            GuardDecision decision = guard.check(publicPoint, point.stepIndex());
            if (decision == null) {
                throw new P6ExecutionInvariantException(
                        P6ExecutionInvariantCode.GUARD_RETURNED_NULL);
            }
            return switch (decision) {
                case ALLOWED -> EffectGuardDecision.ALLOWED;
                case CANCELLED -> EffectGuardDecision.CANCELLED;
                case DEADLINE_EXCEEDED -> EffectGuardDecision.DEADLINE_EXCEEDED;
            };
        };
    }

    static EffectCommitPort adaptCommitPort(WorldCommitPort port) {
        if (port == null) {
            throw invariant();
        }
        return new EffectCommitPort() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public EffectStepOutcome commitSpawn(
                    SpawnProjectileRequest request,
                    SpawnProjectileStep step) {
                CommitDisposition disposition = port.commitSpawn(new SpawnCommit(
                        request.requestId().value(),
                        request.sourceEventId().value(),
                        step.dimension(),
                        step.originX(),
                        step.originY(),
                        step.originZ(),
                        step.directionXQ15(),
                        step.directionYQ15(),
                        step.directionZQ15(),
                        step.profileCode(),
                        request.manaCost()));
                return outcome(disposition);
            }

            @Override
            public EffectStepOutcome commitDamage(
                    DamageEffectRequest request,
                    DamageEffectStep step) {
                CommitDisposition disposition = port.commitDamage(new DamageCommit(
                        request.requestId().value(),
                        request.sourceEventId().value(),
                        step.target().value(),
                        step.magnitude(),
                        request.manaCost()));
                return outcome(disposition);
            }
        };
    }

    private static EffectStepOutcome outcome(CommitDisposition disposition) {
        if (disposition == null) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.PORT_RETURNED_NULL);
        }
        return switch (disposition) {
            case APPLIED -> EffectStepOutcome.applied(1);
            case NOT_APPLIED -> EffectStepOutcome.notApplied();
        };
    }

    static void executeCore(
            ActionInvocation invocation,
            ManaAccountAccess account,
            EffectExecutionGuard guard,
            ActionDamageTransactionEngine engine,
            EffectCommitPort commitPort,
            AppliedFactObserver observer) {
        if (invocation == null
                || account == null
                || guard == null
                || engine == null
                || commitPort == null
                || observer == null) {
            throw invariant();
        }
        ActionDamageTransactionResult result =
                engine.execute(invocation, account, 0, guard, commitPort);
        handleResult(result);
        appliedFact(result.effectResult()).ifPresent(observer::observe);
    }

    static Optional<AppliedFact> appliedFact(EffectExecutionResult result) {
        if (result == null) {
            throw invariant();
        }
        AppliedTerminal terminal = switch (result.status()) {
            case SUCCEEDED -> AppliedTerminal.SUCCEEDED;
            case PARTIALLY_SUCCEEDED -> AppliedTerminal.PARTIALLY_SUCCEEDED;
            case REJECTED, FAILED, COMPENSATED, COMPENSATION_FAILED -> null;
        };
        if (terminal == null || result.primaryMutationCount() == 0) {
            return Optional.empty();
        }

        var copied = new ArrayList<AppliedStep>(
                P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN);
        for (EffectTraceEntry entry : result.trace().entries()) {
            AppliedStepKind kind = switch (entry.stage()) {
                case STEP_APPLIED -> AppliedStepKind.APPLIED;
                case STEP_APPLIED_WITH_FAILURE -> AppliedStepKind.APPLIED_WITH_FAILURE;
                default -> null;
            };
            if (kind != null) {
                copied.add(new AppliedStep(entry.stepIndex(), kind));
            }
        }
        return Optional.of(new AppliedFact(
                terminal, result.primaryMutationCount(), copied));
    }

    static void handleResult(ActionDamageTransactionResult result) {
        if (result == null) {
            throw invariant();
        }
        switch (result.effectResult().status()) {
            case SUCCEEDED,
                    REJECTED,
                    FAILED,
                    PARTIALLY_SUCCEEDED,
                    COMPENSATED -> {
                return;
            }
            case COMPENSATION_FAILED -> throw invariant();
        }
    }

    private static boolean validOrigin(double x, double y, double z) {
        return Double.isFinite(x)
                && Double.isFinite(y)
                && Double.isFinite(z)
                && x >= -30_000_000.0
                && x < 30_000_000.0
                && y >= -20_000_000.0
                && y < 20_000_000.0
                && z >= -30_000_000.0
                && z < 30_000_000.0;
    }

    private static boolean validDirection(int x, int y, int z) {
        return x >= -32_767
                && x <= 32_767
                && y >= -32_767
                && y <= 32_767
                && z >= -32_767
                && z <= 32_767
                && (x != 0 || y != 0 || z != 0);
    }

    private static boolean nonzero(UUID value) {
        return value != null
                && (value.getMostSignificantBits() != 0L
                        || value.getLeastSignificantBits() != 0L);
    }

    private static void requireCommon(
            ResourceLocation actionTypeKey,
            long requestId,
            long sourceEventId,
            long manaCost) {
        if (actionTypeKey == null
                || requestId <= 0
                || sourceEventId <= 0
                || manaCost < 0
                || manaCost > P6EffectBounds.MAX_MANA_OPERATION_AMOUNT) {
            throw invariant();
        }
    }

    private static void requireSpawn(
            ResourceLocation actionTypeKey,
            long requestId,
            long sourceEventId,
            ResourceLocation dimension,
            double originX,
            double originY,
            double originZ,
            int directionXQ15,
            int directionYQ15,
            int directionZQ15,
            int profileCode,
            long manaCost) {
        requireCommon(actionTypeKey, requestId, sourceEventId, manaCost);
        if (dimension == null
                || !validOrigin(originX, originY, originZ)
                || !validDirection(directionXQ15, directionYQ15, directionZQ15)
                || profileCode != 0) {
            throw invariant();
        }
    }

    private static void requireDamage(
            ResourceLocation actionTypeKey,
            long requestId,
            long sourceEventId,
            UUID targetId,
            long magnitude,
            long manaCost) {
        requireCommon(actionTypeKey, requestId, sourceEventId, manaCost);
        if (!nonzero(targetId)
                || magnitude <= 0
                || magnitude > P6EffectBounds.MAX_EFFECT_MAGNITUDE) {
            throw invariant();
        }
    }

    private static void requireSpawnCommit(
            long requestId,
            long sourceEventId,
            ResourceLocation dimension,
            double originX,
            double originY,
            double originZ,
            int directionXQ15,
            int directionYQ15,
            int directionZQ15,
            int profileCode,
            long manaCost) {
        if (requestId <= 0
                || sourceEventId <= 0
                || dimension == null
                || !validOrigin(originX, originY, originZ)
                || !validDirection(directionXQ15, directionYQ15, directionZQ15)
                || profileCode != 0
                || manaCost < 0
                || manaCost > P6EffectBounds.MAX_MANA_OPERATION_AMOUNT) {
            throw invariant();
        }
    }

    private static void requireDamageCommit(
            long requestId,
            long sourceEventId,
            UUID targetId,
            long magnitude,
            long manaCost) {
        if (requestId <= 0
                || sourceEventId <= 0
                || !nonzero(targetId)
                || magnitude <= 0
                || magnitude > P6EffectBounds.MAX_EFFECT_MAGNITUDE
                || manaCost < 0
                || manaCost > P6EffectBounds.MAX_MANA_OPERATION_AMOUNT) {
            throw invariant();
        }
    }

    private static P6ExecutionInvariantException invariant() {
        return new P6ExecutionInvariantException(
                P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
    }

    public sealed interface Invocation
            permits SpawnProjectileInvocation, DamageInvocation {}

    public record SpawnProjectileInvocation(
            ResourceLocation actionTypeKey,
            long requestId,
            long sourceEventId,
            ResourceLocation dimension,
            double originX,
            double originY,
            double originZ,
            int directionXQ15,
            int directionYQ15,
            int directionZQ15,
            int profileCode,
            long manaCost) implements Invocation {
        public SpawnProjectileInvocation {
            requireSpawn(
                    actionTypeKey,
                    requestId,
                    sourceEventId,
                    dimension,
                    originX,
                    originY,
                    originZ,
                    directionXQ15,
                    directionYQ15,
                    directionZQ15,
                    profileCode,
                    manaCost);
        }
    }

    public record DamageInvocation(
            ResourceLocation actionTypeKey,
            long requestId,
            long sourceEventId,
            UUID targetId,
            long magnitude,
            long manaCost) implements Invocation {
        public DamageInvocation {
            requireDamage(
                    actionTypeKey,
                    requestId,
                    sourceEventId,
                    targetId,
                    magnitude,
                    manaCost);
        }
    }

    @FunctionalInterface
    public interface GuardPort {
        GuardDecision check(GuardPoint point, int stepIndex);
    }

    public enum GuardPoint {
        ENTRY,
        PRE_COMMIT,
        BEFORE_STEP
    }

    public enum GuardDecision {
        ALLOWED,
        CANCELLED,
        DEADLINE_EXCEEDED
    }

    public interface WorldCommitPort {
        CommitDisposition commitSpawn(SpawnCommit command);

        CommitDisposition commitDamage(DamageCommit command);
    }

    public record SpawnCommit(
            long requestId,
            long sourceEventId,
            ResourceLocation dimension,
            double originX,
            double originY,
            double originZ,
            int directionXQ15,
            int directionYQ15,
            int directionZQ15,
            int profileCode,
            long manaCost) {
        public SpawnCommit {
            requireSpawnCommit(
                    requestId,
                    sourceEventId,
                    dimension,
                    originX,
                    originY,
                    originZ,
                    directionXQ15,
                    directionYQ15,
                    directionZQ15,
                    profileCode,
                    manaCost);
        }
    }

    public record DamageCommit(
            long requestId,
            long sourceEventId,
            UUID targetId,
            long magnitude,
            long manaCost) {
        public DamageCommit {
            requireDamageCommit(
                    requestId, sourceEventId, targetId, magnitude, manaCost);
        }
    }

    public enum CommitDisposition {
        APPLIED,
        NOT_APPLIED
    }

    @FunctionalInterface
    public interface AppliedFactObserver {
        void observe(AppliedFact fact);
    }

    public record AppliedFact(
            AppliedTerminal terminal,
            int primaryMutationCount,
            List<AppliedStep> appliedSteps) {
        public AppliedFact {
            if (terminal == null || appliedSteps == null) {
                throw invariant();
            }
            for (AppliedStep step : appliedSteps) {
                if (step == null) {
                    throw invariant();
                }
            }
            appliedSteps = List.copyOf(appliedSteps);
            if (appliedSteps.isEmpty()
                    || appliedSteps.size() > P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN
                    || primaryMutationCount < 1
                    || primaryMutationCount
                            > P6EffectBounds.MAX_PRIMARY_WORLD_MUTATIONS_PER_EXECUTION
                    || primaryMutationCount < appliedSteps.size()) {
                throw invariant();
            }
            var appliedWithFailureCount = 0;
            for (var index = 0; index < appliedSteps.size(); index++) {
                AppliedStep step = appliedSteps.get(index);
                if (step.stepIndex() != index) {
                    throw invariant();
                }
                if (step.kind() == AppliedStepKind.APPLIED_WITH_FAILURE) {
                    appliedWithFailureCount++;
                    if (terminal != AppliedTerminal.PARTIALLY_SUCCEEDED
                            || index != appliedSteps.size() - 1) {
                        throw invariant();
                    }
                }
            }
            if (appliedWithFailureCount > 1
                    || terminal == AppliedTerminal.SUCCEEDED
                            && appliedWithFailureCount != 0) {
                throw invariant();
            }
        }
    }

    public record AppliedStep(int stepIndex, AppliedStepKind kind) {
        public AppliedStep {
            if (kind == null
                    || stepIndex < 0
                    || stepIndex >= P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN) {
                throw invariant();
            }
        }
    }

    public enum AppliedStepKind {
        APPLIED,
        APPLIED_WITH_FAILURE
    }

    public enum AppliedTerminal {
        SUCCEEDED,
        PARTIALLY_SUCCEEDED
    }

    public enum V0NativeOutcome {
        CONFIRMED_NOT_APPLIED,
        CONFIRMED_APPLIED,
        APPLIED_WITH_FAILURE,
        OUTCOME_UNKNOWN
    }

    public enum V0Terminal {
        REJECTED_BEFORE_COMMIT,
        CONFIRMED_NOT_APPLIED,
        CONFIRMED_APPLIED,
        CONFIRMED_APPLIED_WITH_FAILURE,
        COMPENSATED_NO_PRIMARY_MUTATION,
        OUTCOME_UNKNOWN
    }

    public enum V0Reason {
        NONE,
        INVALID_REQUEST,
        ACTOR_OR_SOURCE_INVALID,
        TARGET_OR_PORT_UNAVAILABLE,
        DEADLINE_EXCEEDED,
        INSUFFICIENT_MANA,
        MANA_STATE_UNAVAILABLE,
        NATIVE_NOT_APPLIED,
        APPLIED_POSTCONDITION_FAILED,
        NATIVE_OUTCOME_UNCERTAIN
    }

    public static final class V0TerminalReceipt {
        private final long requestId;
        private final long sourceEventId;
        private final int statementOrdinal;
        private final V0Terminal terminal;
        private final V0Reason reason;

        private V0TerminalReceipt(
                long requestId, long sourceEventId, int statementOrdinal,
                V0Terminal terminal, V0Reason reason) {
            this.requestId = requestId;
            this.sourceEventId = sourceEventId;
            this.statementOrdinal = statementOrdinal;
            this.terminal = terminal;
            this.reason = reason;
        }

        public long requestId() {
            return requestId;
        }

        public long sourceEventId() {
            return sourceEventId;
        }

        public int statementOrdinal() {
            return statementOrdinal;
        }

        public V0Terminal terminal() {
            return terminal;
        }

        public V0Reason reason() {
            return reason;
        }
    }
}
