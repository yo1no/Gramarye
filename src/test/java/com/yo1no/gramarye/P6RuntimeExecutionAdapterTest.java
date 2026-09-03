package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardDecision;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardPoint;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class P6RuntimeExecutionAdapterTest {
    private static final ResourceLocation ACTION_KEY =
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "s4_test_only");
    private static final UUID TARGET_ID =
            UUID.fromString("d30c53ad-19f5-4cc2-b467-37ba62ff9476");

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
    void syntheticMappedCallInvokesTypedBridgeOnceWithExactValues() {
        var calls = new AtomicInteger();
        var guardCalls = new AtomicInteger();
        var capability = P6RuntimeExecutionCapability.forRuntimeAdapter();
        P6ExecutionBridgeInvoker invoker = (
                actualCapability,
                actor,
                actionTypeKey,
                requestId,
                sourceEventId,
                targetId,
                magnitude,
                manaCost,
                guard) -> {
            calls.incrementAndGet();
            assertAll(
                    () -> assertSame(capability, actualCapability),
                    () -> assertSame(ACTION_KEY, actionTypeKey),
                    () -> assertEquals(37L, requestId),
                    () -> assertEquals(37L, sourceEventId),
                    () -> assertSame(TARGET_ID, targetId),
                    () -> assertEquals(1_000_000L, magnitude),
                    () -> assertEquals(1_000_000_000L, manaCost),
                    () -> assertEquals(
                            GuardDecision.ALLOWED,
                            guard.check(GuardPoint.ENTRY, -1)));
        };
        var adapter = new P6RuntimeExecutionPortAdapter(
                capability, invoker, (event, context) -> Optional.empty());
        RuntimeExecutionBatch batch = adapter.executeMapped(
                37L,
                () -> {
                    guardCalls.incrementAndGet();
                    return RuntimeExecutionGuardDecision.ALLOWED;
                },
                new P6RuntimeExecutionInput(
                        null, ACTION_KEY, TARGET_ID, 1_000_000L, 1_000_000_000L));

        assertAll(
                () -> assertEquals(1, calls.get()),
                () -> assertEquals(1, guardCalls.get()),
                () -> assertInstanceOf(RuntimePortOutcome.Completed.class, batch.outcome()),
                () -> assertSame(RuntimeChildPlan.EMPTY, batch.children()));
    }

    @Test
    void p5GuardDecisionsMapOneToOneThroughTheTypedInvoker() {
        for (RuntimeExecutionGuardDecision decision : RuntimeExecutionGuardDecision.values()) {
            var calls = new AtomicInteger();
            P6ExecutionBridgeInvoker invoker = (
                    capability,
                    actor,
                    actionTypeKey,
                    requestId,
                    sourceEventId,
                    targetId,
                    magnitude,
                    manaCost,
                    guard) -> {
                calls.incrementAndGet();
                GuardDecision expected = GuardDecision.valueOf(decision.name());
                assertAll(
                        () -> assertEquals(expected, guard.check(GuardPoint.ENTRY, -1)),
                        () -> assertEquals(
                                expected, guard.check(GuardPoint.PRE_COMMIT, -1)),
                        () -> assertEquals(
                                expected, guard.check(GuardPoint.BEFORE_STEP, 7)));
            };
            var adapter = new P6RuntimeExecutionPortAdapter(
                    P6RuntimeExecutionCapability.forRuntimeAdapter(),
                    invoker,
                    (event, context) -> Optional.empty());

            adapter.executeMapped(
                    1,
                    () -> decision,
                    new P6RuntimeExecutionInput(null, ACTION_KEY, TARGET_ID, 1, 0));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void bridgeRuntimeExceptionAndErrorPropagateAsTheSameObjectWithoutRetry() {
        var runtimeCalls = new AtomicInteger();
        var errorCalls = new AtomicInteger();
        var runtimeFailure = new IllegalStateException("same-runtime");
        var errorFailure = new AssertionError("same-error");
        var input = new P6RuntimeExecutionInput(null, ACTION_KEY, TARGET_ID, 1, 0);
        var runtimeAdapter = adapterThrowing(runtimeCalls, runtimeFailure);
        var errorAdapter = adapterThrowing(errorCalls, errorFailure);

        RuntimeException actualRuntime = assertThrows(
                RuntimeException.class,
                () -> runtimeAdapter.executeMapped(
                        1, () -> RuntimeExecutionGuardDecision.ALLOWED, input));
        Error actualError = assertThrows(
                Error.class,
                () -> errorAdapter.executeMapped(
                        1, () -> RuntimeExecutionGuardDecision.ALLOWED, input));

        assertAll(
                () -> assertSame(runtimeFailure, actualRuntime),
                () -> assertSame(errorFailure, actualError),
                () -> assertEquals(1, runtimeCalls.get()),
                () -> assertEquals(1, errorCalls.get()));
    }

    private static P6RuntimeExecutionPortAdapter adapterThrowing(
            AtomicInteger calls, Throwable failure) {
        P6ExecutionBridgeInvoker invoker = (
                capability,
                actor,
                actionTypeKey,
                requestId,
                sourceEventId,
                targetId,
                magnitude,
                manaCost,
                guard) -> {
            calls.incrementAndGet();
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw (Error) failure;
        };
        return new P6RuntimeExecutionPortAdapter(
                P6RuntimeExecutionCapability.forRuntimeAdapter(),
                invoker,
                (event, context) -> Optional.empty());
    }
}
