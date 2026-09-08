package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.lookup.RegistryActionTypeLookup;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardDecision;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardPort;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
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
        P6RuntimeExecutionIdentity identity =
                P6RuntimeExecutionIdentity.fromPublishedEventId(event.eventId().value());
        GuardPort guard = (point, stepIndex) ->
                mapGuardDecision(context.executionGuard().check());
        var observer = new P8AppliedFactHandoff(presentationService, event, context);
        bridgeInvoker.execute(
                capability,
                input.actor(),
                input.actionTypeKey(),
                identity.requestId(),
                identity.sourceEventId(),
                input.targetId(),
                input.magnitude(),
                input.manaCost(),
                guard,
                observer);
        return completedEmpty();
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
            ResourceLocation actionTypeKey,
            long requestId,
            long sourceEventId,
            UUID targetId,
            long magnitude,
            long manaCost,
            GuardPort guard,
            P6RuntimeExecutionBridge.AppliedFactObserver observer);
}

@FunctionalInterface
interface P6RuntimeExecutionInputMapper {
    Optional<P6RuntimeExecutionInput> map(
            RuntimeEvent event, RuntimeExecutionContext context);
}

record P6RuntimeExecutionInput(
        ServerPlayer actor,
        ResourceLocation actionTypeKey,
        UUID targetId,
        long magnitude,
        long manaCost) {}

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
        UUID targetId = switch (context.resolvedReferences().target()) {
            case ResolvedPlayerTarget playerTarget -> playerTarget.player().getUUID();
            case ResolvedEntityTarget entityTarget -> entityTarget.entity().getUUID();
            case NoResolvedRuntimeTarget ignoredTarget -> null;
            case ResolvedBlockTarget ignoredBlock -> null;
        };
        if (targetId == null) {
            return Optional.empty();
        }
        Optional<ResourceLocation> actionTypeKey =
                actionTypes.keyOf(context.node().action().descriptor());
        if (actionTypeKey.isEmpty()) {
            return Optional.empty();
        }
        return withoutAuthorizedScalars(
                playerOrigin.player(), actionTypeKey.orElseThrow(), targetId);
    }

    private static Optional<P6RuntimeExecutionInput> withoutAuthorizedScalars(
            ServerPlayer actor, ResourceLocation actionTypeKey, UUID targetId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(actionTypeKey, "actionTypeKey");
        Objects.requireNonNull(targetId, "targetId");
        return Optional.empty();
    }
}
