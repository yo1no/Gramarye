package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.definition.lookup.RegistryActionTypeLookup;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardDecision;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardPort;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.Invocation;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.SpawnProjectileInvocation;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class P6RuntimeExecutionPortAdapter implements RuntimeExecutionPort {
    private final P6RuntimeExecutionCapability capability;
    private final P8ServerPresentationService presentationService;
    private final P6ExecutionBridgeInvoker bridgeInvoker;
    private final P6RuntimeExecutionInputMapper inputMapper;

    P6RuntimeExecutionPortAdapter(
            P6RuntimeExecutionCapability capability,
            P8ServerPresentationService presentationService) {
        this(
                capability,
                presentationService,
                P6RuntimeExecutionBridge::execute,
                ProductionP6RuntimeExecutionInputMapper.INSTANCE);
    }

    P6RuntimeExecutionPortAdapter(
            P6RuntimeExecutionCapability capability,
            P8ServerPresentationService presentationService,
            P6ExecutionBridgeInvoker bridgeInvoker,
            P6RuntimeExecutionInputMapper inputMapper) {
        this.capability = Objects.requireNonNull(capability, "capability");
        this.presentationService =
                Objects.requireNonNull(presentationService, "presentationService");
        this.bridgeInvoker = Objects.requireNonNull(bridgeInvoker, "bridgeInvoker");
        this.inputMapper = Objects.requireNonNull(inputMapper, "inputMapper");
    }

    @Override
    public RuntimeExecutionBatch execute(RuntimeEvent event, RuntimeExecutionContext context) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(context, "context");
        Optional<P6RuntimeExecutionInput> input = inputMapper.map(event, context);
        if (input.isEmpty()) {
            return completedEmpty();
        }
        return executeMapped(event, context, input.orElseThrow());
    }

    RuntimeExecutionBatch executeMapped(
            RuntimeEvent event,
            RuntimeExecutionContext context,
            P6RuntimeExecutionInput input) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(input, "input");
        Optional<RuntimeProjectileContinuationOpenResult.Opened> opened =
                openSpawnContinuation(input.invocation(), context);
        if (input.invocation() instanceof SpawnProjectileInvocation && opened.isEmpty()) {
            return completedEmpty();
        }

        try {
            var commitPort = new P9WorldEffectHandoff(
                    context.server(), input.actor(), event.executionData(), opened);
            GuardPort guard = (point, stepIndex) -> {
                var decision = mapGuardDecision(context.executionGuard().check());
                if (decision != null && decision != GuardDecision.ALLOWED) {
                    closeOpened(
                            opened,
                            context.server(),
                            decision == GuardDecision.DEADLINE_EXCEEDED
                                    ? ProjectileClosureReason.DEADLINE_REACHED
                                    : ProjectileClosureReason.OWNER_INVALIDATED);
                }
                return decision;
            };
            P6RuntimeExecutionBridge.AppliedFactObserver observer = ignoredFact -> {
                // P9-S4 owns the first production P8 applied-fact mapping.
            };
            bridgeInvoker.execute(
                    capability,
                    input.actor(),
                    input.invocation(),
                    guard,
                    commitPort,
                    observer);
        } catch (RuntimeException failure) {
            if (opened.isPresent()
                    && isAdapterOwnedReservationState(
                            opened.orElseThrow().permit().state)) {
                bestEffortCloseOpened(
                        opened, context.server(), ProjectileClosureReason.RUNTIME_FAULT);
            }
            throw failure;
        } catch (Error failure) {
            throw failure;
        }
        return completedEmpty();
    }

    private static Optional<RuntimeProjectileContinuationOpenResult.Opened>
            openSpawnContinuation(Invocation invocation, RuntimeExecutionContext context) {
        if (!(invocation instanceof SpawnProjectileInvocation)) {
            return Optional.empty();
        }
        var result = context.projectileContinuationOpener()
                .openProjectileContinuation(ActionOutputKind.PROJECTILE, 0);
        if (result instanceof RuntimeProjectileContinuationOpenResult.Opened opened) {
            return Optional.of(opened);
        }
        return Optional.empty();
    }

    private static void closeOpened(
            Optional<RuntimeProjectileContinuationOpenResult.Opened> opened,
            MinecraftServer server,
            ProjectileClosureReason reason) {
        if (opened.isEmpty()) {
            return;
        }
        var disposition = opened.orElseThrow().permit().closeWithoutHit(server, reason);
        if (disposition != RuntimePermitCloseDisposition.CLOSED
                && disposition != RuntimePermitCloseDisposition.ALREADY_CLOSED) {
            throw new IllegalStateException("P9 continuation close was rejected");
        }
    }

    private static void bestEffortCloseOpened(
            Optional<RuntimeProjectileContinuationOpenResult.Opened> opened,
            MinecraftServer server,
            ProjectileClosureReason reason) {
        try {
            closeOpened(opened, server, reason);
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // Preserve the already-caught primary P6/runtime failure.
        }
    }

    private static boolean isAdapterOwnedReservationState(
            RuntimeProjectileContinuationPermit.State state) {
        return state == RuntimeProjectileContinuationPermit.State.RESERVED;
    }

    private static GuardDecision mapGuardDecision(RuntimeExecutionGuardDecision decision) {
        if (decision == null) {
            return null;
        }
        return switch (decision) {
            case ALLOWED -> GuardDecision.ALLOWED;
            case CANCELLED -> GuardDecision.CANCELLED;
            case DEADLINE_EXCEEDED -> GuardDecision.DEADLINE_EXCEEDED;
        };
    }

    static RuntimeExecutionBatch completedEmpty() {
        return new RuntimeExecutionBatch(
                new RuntimePortOutcome.Completed(), RuntimeChildPlan.EMPTY);
    }
}

@FunctionalInterface
interface P6ExecutionBridgeInvoker {
    void execute(
            P6RuntimeExecutionCapability capability,
            ServerPlayer actor,
            Invocation input,
            GuardPort guard,
            P6RuntimeExecutionBridge.WorldCommitPort commitPort,
            P6RuntimeExecutionBridge.AppliedFactObserver observer);
}

@FunctionalInterface
interface P6RuntimeExecutionInputMapper {
    Optional<P6RuntimeExecutionInput> map(
            RuntimeEvent event, RuntimeExecutionContext context);
}

record P6RuntimeExecutionInput(
        ServerPlayer actor,
        Invocation invocation) {
    P6RuntimeExecutionInput {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(invocation, "invocation");
    }
}

record P6RuntimeExecutionIdentity(long requestId, long sourceEventId) {
    static P6RuntimeExecutionIdentity fromPublishedEventId(long publishedEventId) {
        if (publishedEventId <= 0) {
            throw new IllegalArgumentException("published EventId must be positive");
        }
        return new P6RuntimeExecutionIdentity(publishedEventId, publishedEventId);
    }
}

enum ProductionP6RuntimeExecutionInputMapper implements P6RuntimeExecutionInputMapper {
    INSTANCE;

    private final RegistryActionTypeLookup actionTypes = new RegistryActionTypeLookup();

    @Override
    public Optional<P6RuntimeExecutionInput> map(
            RuntimeEvent event, RuntimeExecutionContext context) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(context, "context");
        if (!(context.resolvedReferences().origin()
                instanceof ResolvedPlayerOrigin playerOrigin)) {
            return Optional.empty();
        }
        Optional<ResourceLocation> actionTypeKey =
                actionTypes.keyOf(context.node().action().descriptor());
        if (actionTypeKey.isEmpty()) {
            return Optional.empty();
        }
        var key = actionTypeKey.orElseThrow();
        var identity = P6RuntimeExecutionIdentity.fromPublishedEventId(
                event.eventId().value());

        if (context.node().nodeIndex() == 0
                && key.equals(P9StarterSkillContent.SPAWN_PROJECTILE_ID)
                && context.node().action().descriptor()
                        == P9SpawnProjectileActionType.INSTANCE
                && context.node().action().payload()
                        instanceof P9SpawnProjectileActionPayloadV0 action
                && event.executionData()
                        instanceof CastGeometryExecutionDataV0 geometry
                && context.resolvedReferences().target()
                        instanceof NoResolvedRuntimeTarget) {
            return Optional.of(new P6RuntimeExecutionInput(
                    playerOrigin.player(),
                    new SpawnProjectileInvocation(
                            key,
                            identity.requestId(),
                            identity.sourceEventId(),
                            geometry.dimension(),
                            geometry.originX(),
                            geometry.originY(),
                            geometry.originZ(),
                            geometry.directionXQ15(),
                            geometry.directionYQ15(),
                            geometry.directionZQ15(),
                            action.profileCode(),
                            action.manaCost())));
        }

        // P9-S4 owns the real node-1 damage mapping. S3 consumes that queued event
        // through P5's ordinary empty-port terminal without invoking P6 damage.
        return Optional.empty();
    }
}
