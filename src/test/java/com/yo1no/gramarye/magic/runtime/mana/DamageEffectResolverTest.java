package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DamageEffectResolverTest {
    private final DamageEffectResolver resolver = new DamageEffectResolver();

    @Test
    void acceptsValidDamageRequestWithNonemptyFixedPlan() {
        EffectResolution resolution = resolver.resolve(EffectTestFixtures.request(), 0);
        assertEquals(EffectResolutionKind.ACCEPTED, resolution.kind());
        EffectCommitPlan plan = ((AcceptedEffectResolution) resolution).plan();
        assertEquals(1, plan.steps().size());
        assertEquals(0, plan.steps().getFirst().index());
    }

    @Test
    void acceptedPlanPreservesTargetAndAbstractMagnitude() {
        DamageEffectRequest request = EffectTestFixtures.request(987L, 23L);
        DamageEffectStep step = (DamageEffectStep) ((AcceptedEffectResolution)
                resolver.resolve(request, 0)).plan().steps().getFirst();
        assertEquals(request.target(), step.target());
        assertEquals(request.magnitude(), step.magnitude());
        assertEquals(0, step.declaredChildIntentUpperBound());
    }

    @Test
    void repeatedResolutionIsDeterministic() {
        DamageEffectRequest request = EffectTestFixtures.request();
        assertEquals(resolver.resolve(request, 0), resolver.resolve(request, 0));
    }

    @Test
    void absentAndUnsupportedRequestUseClosedInvalidRequestRejection() {
        assertEquals(
                new RejectedEffectResolution(EffectRejectReason.INVALID_REQUEST),
                resolver.resolve(null, 0));
        UnsupportedRequest unsupported = new UnsupportedRequest(EffectTestFixtures.request());
        assertEquals(
                new RejectedEffectResolution(EffectRejectReason.INVALID_REQUEST),
                resolver.resolve(unsupported, 0));
    }

    @Test
    void invalidSuppliedChildCapacityUsesBoundExceededRejection() {
        assertEquals(
                new RejectedEffectResolution(EffectRejectReason.BOUND_EXCEEDED),
                resolver.resolve(EffectTestFixtures.request(), -1));
        assertEquals(
                new RejectedEffectResolution(EffectRejectReason.BOUND_EXCEEDED),
                resolver.resolve(
                        EffectTestFixtures.request(),
                        P6EffectBounds.MAX_CHILD_INTENTS_PER_EXECUTION + 1));
    }

    @Test
    void resolverHasNoPortWorldOrRetainedStateDependency() {
        assertEquals(0, DamageEffectResolver.class.getDeclaredFields().length);
        assertTrue(Arrays.stream(DamageEffectResolver.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .map(Class::getName)
                .noneMatch(name -> name.startsWith("net.minecraft.")
                        || name.startsWith("net.neoforged.")
                        || name.contains("CommitPort")));
    }

    @Test
    void engineRejectsAbsentRequestBeforeResolverOrPort() {
        int[] resolutions = {0};
        EffectResolver countingResolver = (request, capacity) -> {
            resolutions[0]++;
            return new AcceptedEffectResolution(EffectTestFixtures.plan(1));
        };
        RecordingDamageCommitPort port = RecordingDamageCommitPort.applyingAll();
        EffectExecutionResult result = new EffectExecutionEngine().execute(
                null, countingResolver, RecordingEffectGuard.allowing(), port);
        assertEquals(EffectTerminalStatus.REJECTED, result.status());
        assertEquals(EffectRejectReason.INVALID_REQUEST, result.rejectReason().orElseThrow());
        assertEquals(0, resolutions[0]);
        assertEquals(0, port.availabilityChecks());
    }

    @Test
    void engineRejectsInvalidChildCapacityBeforeResolverOrPort() {
        int[] resolutions = {0};
        EffectResolver countingResolver = (request, capacity) -> {
            resolutions[0]++;
            return new AcceptedEffectResolution(EffectTestFixtures.plan(1));
        };
        RecordingDamageCommitPort port = RecordingDamageCommitPort.applyingAll();
        EffectExecutionResult result = new EffectExecutionEngine().execute(
                EffectTestFixtures.request(),
                -1,
                countingResolver,
                RecordingEffectGuard.allowing(),
                port);
        assertEquals(EffectTerminalStatus.REJECTED, result.status());
        assertEquals(EffectRejectReason.BOUND_EXCEEDED, result.rejectReason().orElseThrow());
        assertEquals(0, resolutions[0]);
        assertEquals(0, port.availabilityChecks());
    }

    @Test
    void rejectedResolutionPreservesReasonAndNeverTouchesPort() {
        RecordingDamageCommitPort port = RecordingDamageCommitPort.applyingAll();
        EffectExecutionResult result = new EffectExecutionEngine().execute(
                EffectTestFixtures.request(),
                (request, capacity) -> new RejectedEffectResolution(
                        EffectRejectReason.TARGET_UNAVAILABLE),
                RecordingEffectGuard.allowing(),
                port);
        assertEquals(EffectTerminalStatus.REJECTED, result.status());
        assertEquals(
                EffectRejectReason.TARGET_UNAVAILABLE,
                result.rejectReason().orElseThrow());
        assertEquals(List.of(), port.committedIndexes());
        assertEquals(0, port.availabilityChecks());
    }

    @Test
    void engineRechecksAcceptedPlanAgainstCurrentChildCapacity() {
        EffectCommitPlan oversizedForCall = new EffectCommitPlan(
                List.of(EffectTestFixtures.step(0, 1, 1)), 1);
        RecordingDamageCommitPort port = RecordingDamageCommitPort.applyingAll();
        P6ExecutionInvariantException failure = org.junit.jupiter.api.Assertions.assertThrows(
                P6ExecutionInvariantException.class,
                () -> new EffectExecutionEngine().execute(
                        EffectTestFixtures.request(),
                        0,
                        EffectTestFixtures.resolverFor(oversizedForCall),
                        RecordingEffectGuard.allowing(),
                        port));
        assertEquals(P6ExecutionInvariantCode.INVALID_ACCEPTED_PLAN, failure.code());
        assertEquals(0, port.availabilityChecks());
    }

    @Test
    void acceptedResolutionWithoutPlanIsAnInternalInvariantFailure() {
        P6ExecutionInvariantException failure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> new AcceptedEffectResolution(null));
        assertEquals(P6ExecutionInvariantCode.INVALID_ACCEPTED_PLAN, failure.code());
    }

    private record UnsupportedRequest(DamageEffectRequest delegate) implements EffectRequest {
        @Override
        public EffectRequestId requestId() {
            return delegate.requestId();
        }

        @Override
        public SourceEventId sourceEventId() {
            return delegate.sourceEventId();
        }

        @Override
        public DamageTargetReference target() {
            return delegate.target();
        }

        @Override
        public CompensationPolicy compensationPolicy() {
            return delegate.compensationPolicy();
        }
    }
}
