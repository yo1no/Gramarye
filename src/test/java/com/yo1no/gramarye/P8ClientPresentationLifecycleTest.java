package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.junit.jupiter.api.Test;

/** Focused throwable-isolation and generation guards for the P8 client lifecycle. */
final class P8ClientPresentationLifecycleTest {
    private static final Constructor<P8ClientPresentationLifecycle> LIFECYCLE_CONSTRUCTOR =
            constructor();
    private static final Field PENDING_WORLD_LOAD = field("pendingWorldLoad");
    private static final Field PENDING_WORLD_LOAD_GENERATION =
            field("pendingWorldLoadGeneration");
    private static final Method ON_CLIENT_PRE_TICK = method(
            "onClientPreTick", ClientTickEvent.Pre.class);

    @Test
    void maintenancePrecedesCatalogDrainAndOnlyClearsItsExactPendingGeneration() {
        var lifecycle = lifecycle();
        setPendingWorldLoad(lifecycle, 7L);
        var matchingDrain = new ObservingDispatchTask(() -> assertAll(
                () -> assertFalse(pendingWorldLoad(lifecycle)),
                () -> assertEquals(0L, pendingWorldLoadGeneration(lifecycle))));

        var selectedMatchingDrain = lifecycle.consumeOpenMaintenanceAndSelectDrain(
                openResult(
                        8L,
                        matchingDrain,
                        P8ClientCleanupDisposition.CLEARED,
                        7L));
        assertSame(matchingDrain, selectedMatchingDrain);
        selectedMatchingDrain.run();
        assertTrue(matchingDrain.ran);

        setPendingWorldLoad(lifecycle, 9L);
        var oldResultDrain = new ObservingDispatchTask(() -> assertAll(
                () -> assertTrue(pendingWorldLoad(lifecycle)),
                () -> assertEquals(9L, pendingWorldLoadGeneration(lifecycle))));
        var selectedOldResultDrain = lifecycle.consumeOpenMaintenanceAndSelectDrain(
                openResult(
                        9L,
                        oldResultDrain,
                        P8ClientCleanupDisposition.SUPERSEDED,
                        8L));
        assertSame(oldResultDrain, selectedOldResultDrain);
        selectedOldResultDrain.run();
        assertAll(
                () -> assertTrue(oldResultDrain.ran),
                () -> assertTrue(pendingWorldLoad(lifecycle)),
                () -> assertEquals(9L, pendingWorldLoadGeneration(lifecycle)));
    }

    @Test
    void actualMaintenanceAndExceptionalPreTickConsumeOnlyOwnedPendingWorldGeneration() {
        var state = new P8ClientPresentationState(() -> true);
        var lifecycle = lifecycle(state);
        try (var transport = new P8ClientPlayConnection()) {
            var first = state.onConnectionOpened(
                    transport.connection(), transport.listener());
            var secondListener = transport.replacePlayListener();
            var second = state.onConnectionOpened(
                    transport.connection(), secondListener);
            assertAll(
                    () -> assertEquals(1L, first.publishedGeneration()),
                    () -> assertEquals(3L, second.publishedGeneration()),
                    () -> assertEquals(
                            1L,
                            second.maintenance()
                                    .invalidatedPublishedGeneration()
                                    .orElseThrow()),
                    () -> assertTrue(state.isCurrentPublishedPlayGeneration(3L)));

            setPendingWorldLoad(lifecycle, 3L);
            lifecycle.consumeMaintenance(second.maintenance());
            assertAll(
                    () -> assertTrue(pendingWorldLoad(lifecycle)),
                    () -> assertEquals(3L, pendingWorldLoadGeneration(lifecycle)));

            transport.replacePlayListener();
            var currentInvalidation = state.maintainTransportLiveness();
            assertAll(
                    () -> assertEquals(
                            P8ClientCleanupDisposition.CLEARED,
                            currentInvalidation.cleanupDisposition()),
                    () -> assertEquals(
                            3L,
                            currentInvalidation
                                    .invalidatedPublishedGeneration()
                                    .orElseThrow()));
            lifecycle.consumeMaintenance(currentInvalidation);
            assertAll(
                    () -> assertFalse(pendingWorldLoad(lifecycle)),
                    () -> assertEquals(0L, pendingWorldLoadGeneration(lifecycle)));
        }

        var primary = new IllegalStateException("expected maintenance invariant failure");
        var threadCheck = new ArmedThreadCheck(primary);
        var currentState = new P8ClientPresentationState(threadCheck);
        var currentLifecycle = lifecycle(currentState);
        try (var transport = new P8ClientPlayConnection()) {
            var opened = currentState.onConnectionOpened(
                    transport.connection(), transport.listener());
            setPendingWorldLoad(currentLifecycle, opened.publishedGeneration());
            var callsBeforePreTick = threadCheck.calls();
            threadCheck.failOnUpcomingCall(2);

            var thrown = assertThrows(
                    IllegalStateException.class,
                    () -> invokeClientPreTick(currentLifecycle));
            assertAll(
                    () -> assertSame(primary, thrown),
                    () -> assertEquals(callsBeforePreTick + 3, threadCheck.calls()),
                    () -> assertTrue(pendingWorldLoad(currentLifecycle)),
                    () -> assertEquals(
                            opened.publishedGeneration(),
                            pendingWorldLoadGeneration(currentLifecycle)),
                    () -> assertTrue(currentState.isCurrentPublishedPlayGeneration(
                            opened.publishedGeneration())));
        }
    }

    @Test
    void cleanupAggregatorPreservesUncheckedPrimaryIdentityWithoutSelfSuppression() {
        var runtimePrimary = new IllegalStateException("runtime-primary");
        var runtimeThrown = assertThrows(
                IllegalStateException.class,
                () -> P8ClientPresentationLifecycle.finishLifecycle(
                        runtimePrimary,
                        () -> {
                            throw runtimePrimary;
                        }));
        assertAll(
                () -> assertSame(runtimePrimary, runtimeThrown),
                () -> assertEquals(0, runtimeThrown.getSuppressed().length));

        var errorPrimary = new AssertionError("error-primary");
        var secondary = new IllegalArgumentException("secondary");
        var errorThrown = assertThrows(
                AssertionError.class,
                () -> P8ClientPresentationLifecycle.finishLifecycle(
                        errorPrimary,
                        () -> {
                            throw secondary;
                        }));
        assertAll(
                () -> assertSame(errorPrimary, errorThrown),
                () -> assertEquals(1, errorThrown.getSuppressed().length),
                () -> assertSame(secondary, errorThrown.getSuppressed()[0]));
    }

    @Test
    void releaseFailureCannotSkipTheFollowingStaleGuard() {
        var releaseFailure = new IllegalStateException("release");
        var staleGuardFailure = new AssertionError("stale-guard");
        var staleGuardRan = new AtomicBoolean();
        var unclaimedDrain = new P8ClientDispatchTask() {
            @Override
            public void run() {}

            @Override
            public void releaseAfterFailedEnqueue() {
                throw releaseFailure;
            }
        };

        var thrown = assertThrows(
                IllegalStateException.class,
                () -> P8ClientPresentationLifecycle.finishLifecycle(
                        null,
                        unclaimedDrain::releaseAfterFailedEnqueue,
                        () -> {
                            staleGuardRan.set(true);
                            throw staleGuardFailure;
                        }));
        assertAll(
                () -> assertSame(releaseFailure, thrown),
                () -> assertTrue(staleGuardRan.get()),
                () -> assertEquals(1, thrown.getSuppressed().length),
                () -> assertSame(staleGuardFailure, thrown.getSuppressed()[0]));
    }

    private static P8ClientPresentationLifecycle lifecycle() {
        return lifecycle(new P8ClientPresentationState(() -> true));
    }

    private static P8ClientPresentationLifecycle lifecycle(
            P8ClientPresentationState state) {
        try {
            return LIFECYCLE_CONSTRUCTOR.newInstance(
                    state,
                    P8ClientPresentationExecution.production());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void invokeClientPreTick(
            P8ClientPresentationLifecycle lifecycle) {
        try {
            ON_CLIENT_PRE_TICK.invoke(lifecycle, new ClientTickEvent.Pre());
        } catch (InvocationTargetException failure) {
            var cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (cause instanceof Error errorFailure) {
                throw errorFailure;
            }
            throw new AssertionError(cause);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static P8ClientConnectionOpenResult openResult(
            long generation,
            P8ClientDispatchTask drain,
            P8ClientCleanupDisposition disposition,
            long invalidatedGeneration) {
        return new P8ClientConnectionOpenResult(
                true,
                generation,
                Optional.of(drain),
                new P8ClientTransportMaintenanceResult(
                        disposition, OptionalLong.of(invalidatedGeneration)));
    }

    private static void setPendingWorldLoad(
            P8ClientPresentationLifecycle lifecycle, long generation) {
        try {
            PENDING_WORLD_LOAD.setBoolean(lifecycle, true);
            PENDING_WORLD_LOAD_GENERATION.setLong(lifecycle, generation);
        } catch (IllegalAccessException failure) {
            throw new AssertionError(failure);
        }
    }

    private static boolean pendingWorldLoad(P8ClientPresentationLifecycle lifecycle) {
        try {
            return PENDING_WORLD_LOAD.getBoolean(lifecycle);
        } catch (IllegalAccessException failure) {
            throw new AssertionError(failure);
        }
    }

    private static long pendingWorldLoadGeneration(
            P8ClientPresentationLifecycle lifecycle) {
        try {
            return PENDING_WORLD_LOAD_GENERATION.getLong(lifecycle);
        } catch (IllegalAccessException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Field field(String name) {
        try {
            var field = P8ClientPresentationLifecycle.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Constructor<P8ClientPresentationLifecycle> constructor() {
        try {
            var constructor = P8ClientPresentationLifecycle.class.getDeclaredConstructor(
                    P8ClientPresentationState.class,
                    P8ClientPresentationExecution.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static Method method(String name, Class<?>... parameterTypes) {
        try {
            var method = P8ClientPresentationLifecycle.class.getDeclaredMethod(
                    name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static final class ObservingDispatchTask implements P8ClientDispatchTask {
        private final Runnable observation;
        private boolean ran;

        private ObservingDispatchTask(Runnable observation) {
            this.observation = observation;
        }

        @Override
        public void run() {
            observation.run();
            ran = true;
        }

        @Override
        public void releaseAfterFailedEnqueue() {}
    }

    private static final class ArmedThreadCheck implements BooleanSupplier {
        private final RuntimeException failure;
        private final AtomicInteger calls = new AtomicInteger();
        private volatile int failingCall = Integer.MAX_VALUE;

        private ArmedThreadCheck(RuntimeException failure) {
            this.failure = failure;
        }

        private int calls() {
            return calls.get();
        }

        private void failOnUpcomingCall(int offset) {
            failingCall = Math.addExact(calls.get(), offset);
        }

        @Override
        public boolean getAsBoolean() {
            if (calls.incrementAndGet() == failingCall) {
                throw failure;
            }
            return true;
        }
    }
}
