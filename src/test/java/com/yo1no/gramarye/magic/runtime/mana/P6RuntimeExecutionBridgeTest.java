package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.P6RuntimeExecutionCapability;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

final class P6RuntimeExecutionBridgeTest {
    private static final ResourceLocation KEY =
            ActionTransactionTestFixtures.DAMAGE_ACTION_KEY;
    private static final UUID TARGET_ID =
            UUID.fromString("70000000-0000-4000-8000-000000000002");

    @Test
    void publicBridgeSurfaceIsExactAndCapabilityGated() throws Exception {
        var execute = P6RuntimeExecutionBridge.class.getDeclaredMethod(
                "execute",
                P6RuntimeExecutionCapability.class,
                ServerPlayer.class,
                ResourceLocation.class,
                long.class,
                long.class,
                UUID.class,
                long.class,
                long.class,
                P6RuntimeExecutionBridge.GuardPort.class);
        Set<String> nested = Arrays.stream(P6RuntimeExecutionBridge.class.getDeclaredClasses())
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        assertTrue(Modifier.isPublic(P6RuntimeExecutionBridge.class.getModifiers()));
        assertTrue(Modifier.isFinal(P6RuntimeExecutionBridge.class.getModifiers()));
        assertEquals(void.class, execute.getReturnType());
        assertTrue(Modifier.isPublic(execute.getModifiers()));
        assertTrue(Modifier.isStatic(execute.getModifiers()));
        assertEquals(Set.of("GuardPort", "GuardPoint", "GuardDecision"), nested);
        assertEquals(1, Arrays.stream(P6RuntimeExecutionBridge.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .count());
        assertEquals(1, P6RuntimeExecutionBridge.GuardPort.class
                .getDeclaredMethods().length);
        assertTrue(P6RuntimeExecutionBridge.class.getDeclaredConstructors().length == 1
                && Modifier.isPrivate(P6RuntimeExecutionBridge.class
                        .getDeclaredConstructors()[0].getModifiers()));
    }

    @Test
    void nullCapabilityFailsBeforeActorArgumentsGuardAndCore() {
        AtomicInteger guardCalls = new AtomicInteger();
        P6ExecutionInvariantException failure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> P6RuntimeExecutionBridge.execute(
                        null,
                        null,
                        null,
                        0,
                        0,
                        null,
                        0,
                        -1,
                        (point, index) -> {
                            guardCalls.incrementAndGet();
                            throw new AssertionError("guard must not be called");
                        }));

        assertEquals(P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT, failure.code());
        assertEquals(0, guardCalls.get());
    }

    @Test
    void invocationSeamAcceptsExactScalarBoundsAndFixedCompensation() {
        DamageActionInvocation minimum = P6RuntimeExecutionBridge.invocation(
                KEY, 1, 1, TARGET_ID, 1, 0);
        DamageActionInvocation maximum = P6RuntimeExecutionBridge.invocation(
                KEY,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                TARGET_ID,
                P6EffectBounds.MAX_EFFECT_MAGNITUDE,
                P6EffectBounds.MAX_MANA_OPERATION_AMOUNT);

        assertEquals(1, minimum.requestId().value());
        assertEquals(1, minimum.sourceEventId().value());
        assertEquals(1, minimum.magnitude());
        assertEquals(0, minimum.manaCost());
        assertEquals(P6EffectBounds.MAX_EFFECT_MAGNITUDE, maximum.magnitude());
        assertEquals(P6EffectBounds.MAX_MANA_OPERATION_AMOUNT, maximum.manaCost());
        assertEquals(
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION,
                minimum.compensationPolicy());
        assertEquals(
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION,
                maximum.compensationPolicy());
    }

    @Test
    void invocationSeamRejectsEveryNullAndOutOfRangeScalar() {
        assertInvocationRejected(null, 1, 1, TARGET_ID, 1, 0);
        assertInvocationRejected(KEY, 1, 1, null, 1, 0);
        assertInvocationRejected(KEY, 0, 1, TARGET_ID, 1, 0);
        assertInvocationRejected(KEY, 1, 0, TARGET_ID, 1, 0);
        assertInvocationRejected(KEY, 1, 1, TARGET_ID, 0, 0);
        assertInvocationRejected(
                KEY, 1, 1, TARGET_ID, P6EffectBounds.MAX_EFFECT_MAGNITUDE + 1, 0);
        assertInvocationRejected(KEY, 1, 1, TARGET_ID, 1, -1);
        assertInvocationRejected(
                KEY, 1, 1, TARGET_ID, 1,
                P6EffectBounds.MAX_MANA_OPERATION_AMOUNT + 1);
    }

    @Test
    void guardAdapterMapsAllPointsIndexesAndDecisionsOneToOne() {
        var observedPoints = new java.util.ArrayList<P6RuntimeExecutionBridge.GuardPoint>();
        var observedIndexes = new java.util.ArrayList<Integer>();
        var decisions = List.of(
                P6RuntimeExecutionBridge.GuardDecision.ALLOWED,
                P6RuntimeExecutionBridge.GuardDecision.CANCELLED,
                P6RuntimeExecutionBridge.GuardDecision.DEADLINE_EXCEEDED);
        AtomicInteger calls = new AtomicInteger();
        EffectExecutionGuard guard = P6RuntimeExecutionBridge.adaptGuard((point, index) -> {
            observedPoints.add(point);
            observedIndexes.add(index);
            return decisions.get(calls.getAndIncrement());
        });

        assertEquals(EffectGuardDecision.ALLOWED, guard.check(EffectGuardPoint.entry()));
        assertEquals(EffectGuardDecision.CANCELLED, guard.check(EffectGuardPoint.preCommit()));
        assertEquals(
                EffectGuardDecision.DEADLINE_EXCEEDED,
                guard.check(EffectGuardPoint.beforeStep(7)));
        assertEquals(
                List.of(
                        P6RuntimeExecutionBridge.GuardPoint.ENTRY,
                        P6RuntimeExecutionBridge.GuardPoint.PRE_COMMIT,
                        P6RuntimeExecutionBridge.GuardPoint.BEFORE_STEP),
                observedPoints);
        assertEquals(List.of(-1, -1, 7), observedIndexes);
        assertEquals(3, calls.get());
    }

    @Test
    void guardAdapterRejectsNullAndPropagatesUncheckedThrowablesAsSameObject() {
        EffectExecutionGuard nullGuard = P6RuntimeExecutionBridge.adaptGuard(
                (point, index) -> null);
        P6ExecutionInvariantException nullFailure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> nullGuard.check(EffectGuardPoint.entry()));
        assertEquals(P6ExecutionInvariantCode.GUARD_RETURNED_NULL, nullFailure.code());

        RuntimeException runtime = new IllegalStateException("bridge-guard-runtime");
        Error error = new AssertionError("bridge-guard-error");
        EffectExecutionGuard runtimeGuard = P6RuntimeExecutionBridge.adaptGuard(
                (point, index) -> { throw runtime; });
        EffectExecutionGuard errorGuard = P6RuntimeExecutionBridge.adaptGuard(
                (point, index) -> { throw error; });

        assertSame(runtime, assertThrows(
                RuntimeException.class,
                () -> runtimeGuard.check(EffectGuardPoint.entry())));
        assertSame(error, assertThrows(
                Error.class,
                () -> errorGuard.check(EffectGuardPoint.beforeStep(0))));
    }

    @Test
    void emptyRegistryReturnsNormallyWithoutAccountGuardResolverOrPortAccess() {
        var resolver = new ActionTransactionTestFixtures.TransactionRecordingResolver(
                (request, capacity) -> { throw new AssertionError("resolver called"); });
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100);
        AtomicInteger guardCalls = new AtomicInteger();
        AtomicInteger portCalls = new AtomicInteger();
        ActionDamageTransactionEngine engine = ActionTransactionTestFixtures.engine(
                new ActionExecutorRegistry(List.of()), resolver);

        assertDoesNotThrow(() -> P6RuntimeExecutionBridge.executeCore(
                invocation(10),
                account,
                point -> {
                    guardCalls.incrementAndGet();
                    throw new AssertionError("guard called");
                },
                engine,
                new DamageEffectCommitPort() {
                    @Override
                    public boolean isAvailable() {
                        portCalls.incrementAndGet();
                        throw new AssertionError("port called");
                    }

                    @Override
                    public EffectStepOutcome commitDamage(DamageEffectStep step) {
                        portCalls.incrementAndGet();
                        throw new AssertionError("port called");
                    }
                }));
        assertEquals(0, resolver.calls());
        assertEquals(0, account.totalAccesses());
        assertEquals(0, guardCalls.get());
        assertEquals(0, portCalls.get());
    }

    @Test
    void fiveNormalTerminalStatusesReturnNormallyThroughCore() {
        assertCoreReturnsNormally(0, ActionTransactionTestFixtures.plan(1),
                List.of(EffectStepOutcome.applied(1)));
        assertCoreReturnsNormally(0, ActionTransactionTestFixtures.plan(1),
                List.of(EffectStepOutcome.notApplied()));
        assertCoreReturnsNormally(10, ActionTransactionTestFixtures.plan(2),
                List.of(EffectStepOutcome.applied(1), EffectStepOutcome.notApplied()));
        assertCoreReturnsNormally(10, ActionTransactionTestFixtures.plan(1),
                List.of(EffectStepOutcome.notApplied()));

        var unavailable = new ActionTransactionTestFixtures.TransactionRecordingPort(
                false, List.of());
        assertDoesNotThrow(() -> P6RuntimeExecutionBridge.executeCore(
                invocation(10),
                new ActionTransactionTestFixtures.RecordingManaAccount(100),
                ActionTransactionTestFixtures.TransactionRecordingGuard.allowing(),
                ActionTransactionTestFixtures.engine(
                        ActionTransactionTestFixtures.resolverFor(
                                ActionTransactionTestFixtures.plan(1))),
                unavailable));
    }

    @Test
    void compensationFailedThrowsInvariantThroughCore() {
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100);
        DamageEffectCommitPort port = new DamageEffectCommitPort() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public EffectStepOutcome commitDamage(DamageEffectStep step) {
                account.setAvailability(ManaAvailability.UNAVAILABLE);
                return EffectStepOutcome.notApplied();
            }
        };

        P6ExecutionInvariantException failure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> P6RuntimeExecutionBridge.executeCore(
                        invocation(10),
                        account,
                        ActionTransactionTestFixtures.TransactionRecordingGuard.allowing(),
                        ActionTransactionTestFixtures.engine(
                                ActionTransactionTestFixtures.resolverFor(
                                        ActionTransactionTestFixtures.plan(1))),
                        port));
        assertEquals(P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT, failure.code());
        assertEquals(1, account.balanceWrites());
        assertEquals(90, account.currentBalance());
    }

    @Test
    void unavailableProductionDamagePortIsFinalStatelessAndFailClosed() {
        var port = UnavailableProductionDamageEffectCommitPort.INSTANCE;

        assertTrue(Modifier.isFinal(
                UnavailableProductionDamageEffectCommitPort.class.getModifiers()));
        assertFalse(port.isAvailable());
        assertThrows(
                P6ExecutionInvariantException.class,
                () -> port.commitDamage(ActionTransactionTestFixtures.step(0)));
    }

    private static DamageActionInvocation invocation(long manaCost) {
        return P6RuntimeExecutionBridge.invocation(
                KEY, 101, 303, TARGET_ID, 25, manaCost);
    }

    private static void assertInvocationRejected(
            ResourceLocation key,
            long requestId,
            long sourceEventId,
            UUID targetId,
            long magnitude,
            long manaCost) {
        assertThrows(
                P6ExecutionInvariantException.class,
                () -> P6RuntimeExecutionBridge.invocation(
                        key, requestId, sourceEventId, targetId, magnitude, manaCost));
    }

    private static void assertCoreReturnsNormally(
            long manaCost,
            EffectCommitPlan plan,
            List<EffectStepOutcome> outcomes) {
        assertDoesNotThrow(() -> P6RuntimeExecutionBridge.executeCore(
                invocation(manaCost),
                new ActionTransactionTestFixtures.RecordingManaAccount(100),
                ActionTransactionTestFixtures.TransactionRecordingGuard.allowing(),
                ActionTransactionTestFixtures.engine(
                        ActionTransactionTestFixtures.resolverFor(plan)),
                new ActionTransactionTestFixtures.TransactionRecordingPort(true, outcomes)));
    }
}
