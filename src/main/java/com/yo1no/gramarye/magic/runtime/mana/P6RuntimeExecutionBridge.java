package com.yo1no.gramarye.magic.runtime.mana;

import com.yo1no.gramarye.P6RuntimeExecutionCapability;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Capability-gated public entry into the package-private P6 transaction core. */
public final class P6RuntimeExecutionBridge {
    private static final ActionExecutorRegistry PRODUCTION_EXECUTORS =
            new ActionExecutorRegistry(List.of());
    private static final ActionDamageTransactionEngine PRODUCTION_ENGINE =
            new ActionDamageTransactionEngine(
                    PRODUCTION_EXECUTORS,
                    new DamageEffectResolver(),
                    new EffectExecutionEngine(),
                    new ManaTransactionService());
    private static final DamageEffectCommitPort PRODUCTION_DAMAGE_PORT =
            UnavailableProductionDamageEffectCommitPort.INSTANCE;

    private P6RuntimeExecutionBridge() {}

    public static void execute(
            P6RuntimeExecutionCapability capability,
            ServerPlayer actor,
            ResourceLocation actionTypeKey,
            long requestId,
            long sourceEventId,
            UUID targetId,
            long magnitude,
            long manaCost,
            GuardPort guard) {
        if (capability == null) {
            throw invariant();
        }
        if (actor == null
                || actionTypeKey == null
                || targetId == null
                || guard == null) {
            throw invariant();
        }

        DamageActionInvocation invocation = invocation(
                actionTypeKey,
                requestId,
                sourceEventId,
                targetId,
                magnitude,
                manaCost);
        ManaAccountAccess account = new PlayerManaAccountAccess(actor);
        EffectExecutionGuard executionGuard = adaptGuard(guard);
        executeCore(
                invocation,
                account,
                executionGuard,
                PRODUCTION_ENGINE,
                PRODUCTION_DAMAGE_PORT);
    }

    static DamageActionInvocation invocation(
            ResourceLocation actionTypeKey,
            long requestId,
            long sourceEventId,
            UUID targetId,
            long magnitude,
            long manaCost) {
        if (actionTypeKey == null
                || targetId == null
                || requestId <= 0
                || sourceEventId <= 0
                || magnitude <= 0
                || magnitude > P6EffectBounds.MAX_EFFECT_MAGNITUDE
                || manaCost < 0
                || manaCost > P6EffectBounds.MAX_MANA_OPERATION_AMOUNT) {
            throw invariant();
        }
        return new DamageActionInvocation(
                actionTypeKey,
                new EffectRequestId(requestId),
                new SourceEventId(sourceEventId),
                new DamageTargetReference(targetId),
                magnitude,
                manaCost,
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION);
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

    static void executeCore(
            DamageActionInvocation invocation,
            ManaAccountAccess account,
            EffectExecutionGuard guard,
            ActionDamageTransactionEngine engine,
            DamageEffectCommitPort commitPort) {
        if (invocation == null
                || account == null
                || guard == null
                || engine == null
                || commitPort == null) {
            throw invariant();
        }
        handleResult(engine.execute(invocation, account, 0, guard, commitPort));
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

    private static P6ExecutionInvariantException invariant() {
        return new P6ExecutionInvariantException(
                P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
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
}

final class UnavailableProductionDamageEffectCommitPort implements DamageEffectCommitPort {
    static final UnavailableProductionDamageEffectCommitPort INSTANCE =
            new UnavailableProductionDamageEffectCommitPort();

    private UnavailableProductionDamageEffectCommitPort() {}

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public EffectStepOutcome commitDamage(DamageEffectStep step) {
        throw new P6ExecutionInvariantException(
                P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
    }
}
