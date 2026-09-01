package com.yo1no.gramarye.magic.runtime.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class DamageEffectCommitPortTest {
    @Test
    void unavailablePortRejectsAndMakesZeroStepCalls() {
        RecordingDamageCommitPort port = new RecordingDamageCommitPort(false, List.of());
        EffectExecutionResult result = engine(port).execute(EffectTestFixtures.request());
        assertEquals(EffectTerminalStatus.REJECTED, result.status());
        assertEquals(
                EffectRejectReason.COMMIT_PORT_UNAVAILABLE,
                result.rejectReason().orElseThrow());
        assertEquals(1, port.availabilityChecks());
        assertEquals(List.of(), port.committedIndexes());
    }

    @Test
    void availabilityIsCheckedBeforePreCommitGuard() {
        List<String> order = new ArrayList<>();
        DamageEffectCommitPort port = new DamageEffectCommitPort() {
            @Override
            public boolean isAvailable() {
                order.add("availability");
                return true;
            }

            @Override
            public EffectStepOutcome commitDamage(DamageEffectStep step) {
                order.add("commit");
                return EffectStepOutcome.applied(1);
            }
        };
        EffectExecutionGuard guard = point -> {
            order.add(point.kind().name());
            return EffectGuardDecision.ALLOWED;
        };
        engine(guard, port).execute(EffectTestFixtures.request());
        assertEquals(
                List.of("ENTRY", "availability", "PRE_COMMIT", "BEFORE_STEP", "commit"),
                order);
    }

    @Test
    void nullPortOutcomeIsInvariantFailure() {
        DamageEffectCommitPort port = availablePort(step -> null);
        P6ExecutionInvariantException failure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> engine(port).execute(EffectTestFixtures.request()));
        assertEquals(P6ExecutionInvariantCode.PORT_RETURNED_NULL, failure.code());
    }

    @Test
    void actualAboveDeclaredIsInvariantFailure() {
        DamageEffectCommitPort port = availablePort(step -> EffectStepOutcome.applied(2));
        P6ExecutionInvariantException failure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> engine(port).execute(EffectTestFixtures.request()));
        assertEquals(
                P6ExecutionInvariantCode.ACTUAL_MUTATION_EXCEEDS_DECLARED,
                failure.code());
    }

    @Test
    void checkedCumulativeActualAboveEightIsInvariantFailure() {
        P6ExecutionInvariantException failure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> EffectMutationAccumulator.add(
                        P6EffectBounds.MAX_PRIMARY_WORLD_MUTATIONS_PER_EXECUTION,
                        EffectTestFixtures.step(0),
                        EffectStepOutcome.applied(1)));
        assertEquals(P6ExecutionInvariantCode.TOTAL_MUTATION_EXCEEDS_BOUND, failure.code());
    }

    @Test
    void portRuntimeExceptionAndErrorPropagateAsSameObjectWithoutRetry() {
        RuntimeException runtimeFailure = new RuntimeException("port-test-runtime");
        Error errorFailure = new AssertionError("port-test-error");
        AtomicInteger runtimeCalls = new AtomicInteger();
        AtomicInteger errorCalls = new AtomicInteger();
        DamageEffectCommitPort runtimePort = availablePort(step -> {
            runtimeCalls.incrementAndGet();
            throw runtimeFailure;
        });
        DamageEffectCommitPort errorPort = availablePort(step -> {
            errorCalls.incrementAndGet();
            throw errorFailure;
        });
        RuntimeException observedRuntime = assertThrows(
                RuntimeException.class,
                () -> engine(runtimePort).execute(EffectTestFixtures.request()));
        Error observedError = assertThrows(
                Error.class,
                () -> engine(errorPort).execute(EffectTestFixtures.request()));
        assertSame(runtimeFailure, observedRuntime);
        assertSame(errorFailure, observedError);
        assertEquals(1, runtimeCalls.get());
        assertEquals(1, errorCalls.get());
    }

    @Test
    void availabilityThrowablePropagatesAsSameObjectWithoutCommit() {
        RuntimeException failure = new RuntimeException("availability-test");
        AtomicInteger commits = new AtomicInteger();
        DamageEffectCommitPort port = new DamageEffectCommitPort() {
            @Override
            public boolean isAvailable() {
                throw failure;
            }

            @Override
            public EffectStepOutcome commitDamage(DamageEffectStep step) {
                commits.incrementAndGet();
                return EffectStepOutcome.applied(1);
            }
        };
        RuntimeException observed = assertThrows(
                RuntimeException.class,
                () -> engine(port).execute(EffectTestFixtures.request()));
        assertSame(failure, observed);
        assertEquals(0, commits.get());
    }

    @Test
    void resolverThrowableAndNullResultPreserveBoundaryContract() {
        RuntimeException runtimeFailure = new RuntimeException("resolver-test-runtime");
        Error errorFailure = new AssertionError("resolver-test-error");
        RuntimeException observedRuntime = assertThrows(
                RuntimeException.class,
                () -> new EffectExecutionEngine(
                        (request, capacity) -> { throw runtimeFailure; },
                        RecordingEffectGuard.allowing(),
                        RecordingDamageCommitPort.applyingAll())
                        .execute(EffectTestFixtures.request()));
        Error observedError = assertThrows(
                Error.class,
                () -> new EffectExecutionEngine(
                        (request, capacity) -> { throw errorFailure; },
                        RecordingEffectGuard.allowing(),
                        RecordingDamageCommitPort.applyingAll())
                        .execute(EffectTestFixtures.request()));
        P6ExecutionInvariantException nullFailure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> new EffectExecutionEngine(
                        (request, capacity) -> null,
                        RecordingEffectGuard.allowing(),
                        RecordingDamageCommitPort.applyingAll())
                        .execute(EffectTestFixtures.request()));
        assertSame(runtimeFailure, observedRuntime);
        assertSame(errorFailure, observedError);
        assertEquals(P6ExecutionInvariantCode.RESOLVER_RETURNED_NULL, nullFailure.code());
    }

    private static EffectExecutionEngine engine(DamageEffectCommitPort port) {
        return engine(RecordingEffectGuard.allowing(), port);
    }

    private static EffectExecutionEngine engine(
            EffectExecutionGuard guard, DamageEffectCommitPort port) {
        return new EffectExecutionEngine(
                EffectTestFixtures.resolverFor(EffectTestFixtures.plan(1)), guard, port);
    }

    private static DamageEffectCommitPort availablePort(
            java.util.function.Function<DamageEffectStep, EffectStepOutcome> commit) {
        return new DamageEffectCommitPort() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public EffectStepOutcome commitDamage(DamageEffectStep step) {
                return commit.apply(step);
            }
        };
    }
}
