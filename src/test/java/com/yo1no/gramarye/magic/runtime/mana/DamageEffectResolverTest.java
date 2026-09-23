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
    void acceptsCanonicalP9DamageRequestWithNonemptyFixedPlan() {
        EffectResolution resolution = resolver.resolve(EffectTestFixtures.request(), 0);
        assertEquals(EffectResolutionKind.ACCEPTED, resolution.kind());
        EffectCommitPlan plan = ((AcceptedEffectResolution) resolution).plan();
        assertEquals(1, plan.steps().size());
        assertEquals(0, plan.steps().getFirst().index());
    }

    @Test
    void acceptedPlanPreservesTargetAndCanonicalAbstractMagnitude() {
        DamageEffectRequest request = EffectTestFixtures.request();
        DamageEffectStep step = (DamageEffectStep) ((AcceptedEffectResolution)
                resolver.resolve(request, 0)).plan().steps().getFirst();
        assertEquals(request.target(), step.target());
        assertEquals(request.magnitude(), step.magnitude());
        assertEquals(0, step.declaredChildIntentUpperBound());
    }

    @Test
    void p10DiscreteDamageSetPreservesExactMagnitudeInTheOrderedPlan() {
        for (long magnitude : new long[] {4_000L, 5_000L}) {
            var request = EffectTestFixtures.request(magnitude, 0L);
            var accepted = (AcceptedEffectResolution) resolver.resolve(request, 0);
            var step = (DamageEffectStep) accepted.plan().steps().getFirst();
            assertEquals(1, accepted.plan().steps().size());
            assertEquals(request.target(), step.target());
            assertEquals(magnitude, step.magnitude());
            assertEquals(0, step.index());
            assertEquals(0, step.declaredChildIntentUpperBound());
        }
        for (long magnitude : new long[] {1L, 3_999L, 4_001L, 4_500L, 5_001L, 6_000L}) {
            assertEquals(new RejectedEffectResolution(EffectRejectReason.INVALID_REQUEST),
                    resolver.resolve(EffectTestFixtures.request(magnitude, 0L), 0));
        }
    }

    @Test
    void rejectsNoncanonicalP9DamageMagnitudeCostAndCurrentEventIdentity() {
        DamageEffectRequest canonical = EffectTestFixtures.request();
        for (DamageEffectRequest invalid : List.of(
                EffectTestFixtures.request(3_000L, 0L),
                EffectTestFixtures.request(4_001L, 0L),
                EffectTestFixtures.request(4_000L, 1L),
                new DamageEffectRequest(
                        canonical.requestId(),
                        new SourceEventId(canonical.sourceEventId().value() + 1L),
                        canonical.target(),
                        canonical.magnitude(),
                        canonical.manaCost(),
                        canonical.compensationPolicy()))) {
            assertEquals(
                    new RejectedEffectResolution(EffectRejectReason.INVALID_REQUEST),
                    resolver.resolve(invalid, 0));
        }
    }

    @Test
    void directInjectedResolverPreservesGenericPositiveCostDamageSemantics() {
        DamageEffectRequest generic = EffectTestFixtures.request(987L, 23L);
        EffectResolution resolution = ActionTransactionTestFixtures.directDamageResolver()
                .resolve(generic, 0);
        DamageEffectStep step = (DamageEffectStep) ((AcceptedEffectResolution) resolution)
                .plan().steps().getFirst();

        assertEquals(EffectResolutionKind.ACCEPTED, resolution.kind());
        assertEquals(generic.target(), step.target());
        assertEquals(987L, step.magnitude());
        assertEquals(23L, generic.manaCost());
    }

    @Test
    void repeatedResolutionIsDeterministic() {
        DamageEffectRequest request = EffectTestFixtures.request();
        assertEquals(resolver.resolve(request, 0), resolver.resolve(request, 0));
    }

    @Test
    void absentRequestUsesClosedInvalidRequestRejection() {
        assertEquals(
                new RejectedEffectResolution(EffectRejectReason.INVALID_REQUEST),
                resolver.resolve(null, 0));
    }

    @Test
    void acceptsCanonicalP9SpawnRequestWithExactSingleSpawnStep() {
        SpawnProjectileRequest request = EffectTestFixtures.spawnRequest();
        EffectCommitPlan plan = ((AcceptedEffectResolution) resolver.resolve(request, 0)).plan();
        SpawnProjectileStep step = (SpawnProjectileStep) plan.steps().getFirst();

        assertEquals(0, step.index());
        assertEquals(EffectStepKind.SPAWN_PROJECTILE, step.kind());
        assertEquals(request.dimension(), step.dimension());
        assertEquals(request.originX(), step.originX());
        assertEquals(request.originY(), step.originY());
        assertEquals(request.originZ(), step.originZ());
        assertEquals(request.directionXQ15(), step.directionXQ15());
        assertEquals(request.directionYQ15(), step.directionYQ15());
        assertEquals(request.directionZQ15(), step.directionZQ15());
        assertEquals(request.profileCode(), step.profileCode());
        assertEquals(1, step.declaredPrimaryMutationUpperBound());
        assertEquals(0, step.declaredChildIntentUpperBound());
    }

    @Test
    void rejectsNoncanonicalP9SpawnCostAndCurrentEventIdentity() {
        SpawnProjectileRequest canonical = EffectTestFixtures.spawnRequest();
        SpawnProjectileRequest positiveCost = EffectTestFixtures.spawnRequest(1L);
        SpawnProjectileRequest differentCurrentEvent = new SpawnProjectileRequest(
                canonical.requestId(),
                new SourceEventId(canonical.sourceEventId().value() + 1L),
                canonical.dimension(),
                canonical.originX(),
                canonical.originY(),
                canonical.originZ(),
                canonical.directionXQ15(),
                canonical.directionYQ15(),
                canonical.directionZQ15(),
                canonical.profileCode(),
                canonical.manaCost(),
                canonical.compensationPolicy());

        assertEquals(
                new RejectedEffectResolution(EffectRejectReason.INVALID_REQUEST),
                resolver.resolve(positiveCost, 0));
        assertEquals(
                new RejectedEffectResolution(EffectRejectReason.INVALID_REQUEST),
                resolver.resolve(differentCurrentEvent, 0));
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

}
