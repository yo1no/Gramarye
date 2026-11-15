package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0NativeOutcome;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0Reason;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0Terminal;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0TerminalReceipt;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class V0StatementTerminalTest {
    private final EffectExecutionEngine engine = new EffectExecutionEngine();

    @Test
    void resolverPreservesSeparateIdentifiersAndUsesOneEffectWithNoChildren() {
        var oldSpawn = EffectTestFixtures.spawnRequest();
        var spawn = new SpawnProjectileRequest(
                new EffectRequestId(19), new SourceEventId(7),
                oldSpawn.dimension(), oldSpawn.originX(), oldSpawn.originY(), oldSpawn.originZ(),
                oldSpawn.directionXQ15(), oldSpawn.directionYQ15(), oldSpawn.directionZQ15(),
                0, 4, CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION);
        var funded = new V0EffectResolver(4, 0);
        var accepted = assertInstanceOf(AcceptedEffectResolution.class, funded.resolve(spawn, 0));
        assertEquals(1, accepted.plan().declaredPrimaryMutationCount());
        assertEquals(0, accepted.plan().declaredChildIntentCount());
        assertInstanceOf(RejectedEffectResolution.class, funded.resolve(spawn, 1));
        assertInstanceOf(RejectedEffectResolution.class, new V0EffectResolver(3, 0).resolve(spawn, 0));
        assertInstanceOf(RejectedEffectResolution.class, new DamageEffectResolver().resolve(spawn, 0));

        var legacyDamage = EffectTestFixtures.request(4_000, 0);
        for (long magnitude : List.of(1_000L, 2_000L, 3_000L, 4_000L)) {
            var damage = new DamageEffectRequest(new EffectRequestId(20), new SourceEventId(9),
                    legacyDamage.target(), magnitude, 0, CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION);
            var resolved = assertInstanceOf(AcceptedEffectResolution.class,
                    new V0EffectResolver(0, magnitude).resolve(damage, 0));
            assertEquals(magnitude, ((DamageEffectStep) resolved.plan().steps().getFirst()).magnitude());
            assertInstanceOf(RejectedEffectResolution.class, new DamageEffectResolver().resolve(damage, 0));
        }
        var outOfRange = EffectTestFixtures.request(5_000L, 0);
        assertInstanceOf(RejectedEffectResolution.class, new V0EffectResolver(0, 5_000).resolve(outOfRange, 0));
        assertInstanceOf(AcceptedEffectResolution.class, new DamageEffectResolver().resolve(outOfRange, 0));
    }

    @Test
    void orderedLoopKeepsUnknownOutOfLegacyZeroMutationAndStopsAfterOneAttempt() {
        var probe = new NativeProbe(V0NativeUnknown.INSTANCE, null);
        var prepared = prepared(probe);
        assertEquals(new V0UnresolvedAttempt(0, 0),
                engine.executePreparedV0(prepared, ManaTraceState.DEBITED));
        assertEquals(1, probe.attempts);
    }

    @Test
    void confirmedNativeResultsPreserveAppliedFactsAndCompensationEligibility() {
        var applied = new NativeProbe(new V0Confirmed(EffectStepOutcome.applied(1)), null);
        var failed = new NativeProbe(new V0Confirmed(EffectStepOutcome.appliedWithFailure(1)), null);
        var absent = new NativeProbe(new V0Confirmed(EffectStepOutcome.notApplied()), null);

        var succeeded = ((V0KnownAttempt) engine.executePreparedV0(
                prepared(applied), ManaTraceState.DEBITED)).attempt();
        var partial = ((V0KnownAttempt) engine.executePreparedV0(
                prepared(failed), ManaTraceState.DEBITED)).attempt();
        var zero = ((V0KnownAttempt) engine.executePreparedV0(
                prepared(absent), ManaTraceState.DEBITED)).attempt();

        assertEquals(EffectTerminalStatus.SUCCEEDED, engine.finalizeDebited(succeeded).status());
        assertEquals(EffectTerminalStatus.PARTIALLY_SUCCEEDED, engine.finalizeDebited(partial).status());
        assertEquals(EffectTerminalStatus.COMPENSATED, engine.finalizeCompensated(zero).status());
        assertThrows(P6ExecutionInvariantException.class, () -> engine.finalizeCompensated(partial));
        assertEquals(1, applied.attempts);
        assertEquals(1, failed.attempts);
        assertEquals(1, absent.attempts);
    }

    @Test
    void nativeThrowablesEscapeTheOrderedV0LoopUnchanged() {
        var error = new AssertionError("native Error");
        var exception = new IllegalStateException("native RuntimeException");
        var errorPort = new NativeProbe(null, error);
        var exceptionPort = new NativeProbe(null, exception);

        assertSame(error, assertThrows(AssertionError.class, () -> engine.executePreparedV0(
                prepared(errorPort), ManaTraceState.DEBITED)));
        assertSame(exception, assertThrows(IllegalStateException.class, () -> engine.executePreparedV0(
                prepared(exceptionPort), ManaTraceState.DEBITED)));
        assertEquals(1, errorPort.attempts);
        assertEquals(1, exceptionPort.attempts);
    }

    @Test
    void outcomeFamilyIsClosedAndReceiptHasOnlyExactScalarFields() {
        assertEquals(List.of("CONFIRMED_NOT_APPLIED", "CONFIRMED_APPLIED",
                "APPLIED_WITH_FAILURE", "OUTCOME_UNKNOWN"),
                Arrays.stream(V0NativeOutcome.values()).map(Enum::name).toList());
        assertEquals(List.of("REJECTED_BEFORE_COMMIT", "CONFIRMED_NOT_APPLIED", "CONFIRMED_APPLIED",
                "CONFIRMED_APPLIED_WITH_FAILURE", "COMPENSATED_NO_PRIMARY_MUTATION", "OUTCOME_UNKNOWN"),
                Arrays.stream(V0Terminal.values()).map(Enum::name).toList());

        assertFalse(V0TerminalReceipt.class.isRecord());
        assertEquals(1, V0TerminalReceipt.class.getDeclaredConstructors().length);
        var constructor = V0TerminalReceipt.class.getDeclaredConstructors()[0];
        assertEquals(List.of(long.class, long.class, int.class, V0Terminal.class, V0Reason.class),
                List.of(constructor.getParameterTypes()));
        assertEquals(Modifier.PRIVATE, constructor.getModifiers());
        assertEquals(List.of("reason", "requestId", "sourceEventId", "statementOrdinal", "terminal"),
                Arrays.stream(V0TerminalReceipt.class.getDeclaredMethods()).map(method -> method.getName())
                        .sorted().toList());
        assertEquals(5, V0TerminalReceipt.class.getDeclaredFields().length);
    }

    private PreparedV0EffectExecution prepared(NativeProbe probe) {
        var request = EffectTestFixtures.spawnRequest(1);
        return assertInstanceOf(PreparedV0EffectExecution.class,
                engine.prepareV0(request, new V0EffectResolver(1, 0),
                        ignored -> EffectGuardDecision.ALLOWED, probe));
    }

    private static final class NativeProbe implements V0EffectCommitPort {
        private final V0PrimaryOutcome outcome;
        private final Throwable fault;
        private int attempts;

        private NativeProbe(V0PrimaryOutcome outcome, Throwable fault) {
            this.outcome = outcome;
            this.fault = fault;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public V0PrimaryOutcome commitSpawn(SpawnProjectileRequest request, SpawnProjectileStep step) {
            attempts++;
            if (fault instanceof RuntimeException failure) {
                throw failure;
            }
            if (fault instanceof Error failure) {
                throw failure;
            }
            return outcome;
        }

        @Override
        public V0PrimaryOutcome commitDamage(DamageEffectRequest request, DamageEffectStep step) {
            throw new AssertionError("damage reached the spawn plan");
        }
    }
}
