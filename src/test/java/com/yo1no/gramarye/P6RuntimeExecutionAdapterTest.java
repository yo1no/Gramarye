package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardPort;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

final class P6RuntimeExecutionAdapterTest {
    @Test
    void preBridgeUnavailableMappingCompletesWithTheExactEmptyPlan() {
        RuntimeExecutionBatch batch = P6RuntimeExecutionPortAdapter.completedEmpty();

        assertAll(
                () -> assertInstanceOf(RuntimePortOutcome.Completed.class, batch.outcome()),
                () -> assertSame(RuntimeChildPlan.EMPTY, batch.children()),
                () -> assertEquals(0, batch.children().children().size()));
    }

    @Test
    void publishedEventIdentityMapsLosslesslyToBothBridgeIds() {
        P6RuntimeExecutionIdentity identity =
                P6RuntimeExecutionIdentity.fromPublishedEventId(9_223_372_036_854L);

        assertAll(
                () -> assertEquals(9_223_372_036_854L, identity.requestId()),
                () -> assertEquals(9_223_372_036_854L, identity.sourceEventId()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P6RuntimeExecutionIdentity.fromPublishedEventId(0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P6RuntimeExecutionIdentity.fromPublishedEventId(-1)));
    }

    @Test
    void productionConstructorAndTestInvokerKeepTheObserverFinalContract() throws Exception {
        var production = P6RuntimeExecutionPortAdapter.class.getDeclaredConstructor(
                P6RuntimeExecutionCapability.class,
                P8ServerPresentationService.class);
        var invokerOperation = P6ExecutionBridgeInvoker.class.getDeclaredMethod(
                "execute",
                P6RuntimeExecutionCapability.class,
                ServerPlayer.class,
                ResourceLocation.class,
                long.class,
                long.class,
                UUID.class,
                long.class,
                long.class,
                GuardPort.class,
                P6RuntimeExecutionBridge.AppliedFactObserver.class);

        assertAll(
                () -> assertFalse(Modifier.isPublic(production.getModifiers())),
                () -> assertFalse(Modifier.isProtected(production.getModifiers())),
                () -> assertEquals(void.class, invokerOperation.getReturnType()),
                () -> assertEquals(0, invokerOperation.getExceptionTypes().length),
                () -> assertEquals(
                        P6RuntimeExecutionBridge.AppliedFactObserver.class,
                        invokerOperation.getParameterTypes()[9]),
                () -> assertEquals(1L, Arrays.stream(P6ExecutionBridgeInvoker.class
                                .getDeclaredMethods())
                        .filter(method -> Modifier.isAbstract(method.getModifiers()))
                        .count()));
    }
}
