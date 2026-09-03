package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.P6RuntimeExecutionCapability;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    void normalTerminalRetainsNoCallScopedP6Objects() {
        ActionExecutorRegistry registry = ActionTransactionTestFixtures.damageRegistry();
        DamageEffectResolver resolver = new DamageEffectResolver();
        EffectExecutionEngine effects = new EffectExecutionEngine();
        ManaTransactionService manaTransactions = new ManaTransactionService();
        ActionDamageTransactionEngine engine = new ActionDamageTransactionEngine(
                registry, resolver, effects, manaTransactions);
        SuccessfulDamagePort commitPort = new SuccessfulDamagePort();

        CountingAllowingGuardPort coreGuardPort = new CountingAllowingGuardPort();
        EffectExecutionGuard coreGuard = P6RuntimeExecutionBridge.adaptGuard(coreGuardPort);
        DamageActionInvocation coreInvocation = invocation(10);
        var coreAccount = new ActionTransactionTestFixtures.RecordingManaAccount(100);
        assertDoesNotThrow(() -> P6RuntimeExecutionBridge.executeCore(
                coreInvocation, coreAccount, coreGuard, engine, commitPort));

        CountingAllowingGuardPort guardPort = new CountingAllowingGuardPort();
        EffectExecutionGuard guard = P6RuntimeExecutionBridge.adaptGuard(guardPort);
        DamageActionInvocation invocation = invocation(10);
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100);

        ActionDamageTransactionResult result = engine.execute(
                invocation, account, 0, guard, commitPort);
        EffectExecutionResult effectResult = result.effectResult();
        EffectTrace trace = effectResult.trace();
        assertTrue(result.manaSummary() instanceof ManaDebited);
        ManaDebited manaSummary = (ManaDebited) result.manaSummary();
        ManaReceiptSnapshot receipt = manaSummary.debitReceipt();
        P6RuntimeExecutionBridge.handleResult(result);

        IdentityHashMap<Object, Boolean> reachable =
                P6TemporalReachabilityAssertions.snapshot(
                        registry,
                        resolver,
                        effects,
                        manaTransactions,
                        engine,
                        commitPort);
        assertAll(
                () -> assertEquals(EffectTerminalStatus.SUCCEEDED, effectResult.status()),
                () -> assertEquals(ManaExecutionSummaryKind.DEBITED,
                        result.manaSummary().kind()),
                () -> assertTrue(result.provisionalFailure().isEmpty()),
                () -> assertEquals(3, coreGuardPort.calls()),
                () -> assertEquals(1, coreAccount.balanceWrites()),
                () -> assertEquals(90, coreAccount.currentBalance()),
                () -> assertEquals(3, guardPort.calls()),
                () -> assertEquals(1, account.balanceWrites()),
                () -> assertEquals(90, account.currentBalance()),
                () -> assertEquals(16, P6TemporalReachabilityAssertions.MAXIMUM_DEPTH),
                () -> assertEquals(4096,
                        P6TemporalReachabilityAssertions.MAXIMUM_VISITED_NODES),
                () -> assertEquals(
                        List.of(
                                "com.yo1no.gramarye.magic.runtime.mana."
                                        + "P6RuntimeExecutionBridge#PRODUCTION_EXECUTORS",
                                "com.yo1no.gramarye.magic.runtime.mana."
                                        + "P6RuntimeExecutionBridge#PRODUCTION_ENGINE",
                                "com.yo1no.gramarye.magic.runtime.mana."
                                        + "P6RuntimeExecutionBridge#PRODUCTION_DAMAGE_PORT",
                                "com.yo1no.gramarye.magic.runtime.mana."
                                        + "UnavailableProductionDamageEffectCommitPort#INSTANCE",
                                "com.yo1no.gramarye.P6RuntimeExecutionCapability#INSTANCE",
                                "com.yo1no.gramarye."
                                        + "ProductionP6RuntimeExecutionInputMapper#INSTANCE"),
                        P6TemporalReachabilityAssertions.staticRootCoordinates()),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, guardPort),
                        "guard port"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, coreGuardPort),
                        "executeCore guard port"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(reachable, guard),
                        "adapted guard"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, coreGuard),
                        "executeCore adapted guard"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, invocation),
                        "invocation"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, coreInvocation),
                        "executeCore invocation"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, invocation.requestId()),
                        "request identity"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, invocation.sourceEventId()),
                        "source-event identity"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, invocation.target()),
                        "target"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, coreInvocation.target()),
                        "executeCore target"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(reachable, account),
                        "account access"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, coreAccount),
                        "executeCore account access"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(reachable, result),
                        "transaction result"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, effectResult),
                        "effect result"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(reachable, trace),
                        "trace"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, manaSummary),
                        "mana result"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, receipt),
                        "receipt snapshot"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, receipt.identity()),
                        "receipt identity"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, ManaReceipt.class),
                        "live receipt"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, DamageEffectRequest.class),
                        "request"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, EffectCommitPlan.class),
                        "plan"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, PreparedEffectExecution.class),
                        "preparation"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, EffectExecutionAttempt.class),
                        "attempt"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, DamageEffectStep.class),
                        "step"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, EffectTraceEntry.class),
                        "trace entry"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, Throwable.class),
                        "Throwable"));
    }

    @Test
    void exceptionalTerminalRetainsNeitherCallScopedObjectsNorThrowable() {
        ActionExecutorRegistry registry = ActionTransactionTestFixtures.damageRegistry();
        DamageEffectResolver resolver = new DamageEffectResolver();
        EffectExecutionEngine effects = new EffectExecutionEngine();
        ManaTransactionService manaTransactions = new ManaTransactionService();
        ActionDamageTransactionEngine engine = new ActionDamageTransactionEngine(
                registry, resolver, effects, manaTransactions);
        ThrowingDamagePort commitPort = new ThrowingDamagePort();
        CountingAllowingGuardPort guardPort = new CountingAllowingGuardPort();
        EffectExecutionGuard guard = P6RuntimeExecutionBridge.adaptGuard(guardPort);
        DamageActionInvocation invocation = invocation(10);
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100);

        ProbeRuntimeException caught = assertThrows(
                ProbeRuntimeException.class,
                () -> P6RuntimeExecutionBridge.executeCore(
                        invocation, account, guard, engine, commitPort));

        IdentityHashMap<Object, Boolean> reachable =
                P6TemporalReachabilityAssertions.snapshot(
                        registry,
                        resolver,
                        effects,
                        manaTransactions,
                        engine,
                        commitPort);
        assertAll(
                () -> assertEquals(ProbeRuntimeException.class, caught.getClass()),
                () -> assertNull(caught.getCause()),
                () -> assertEquals(ThrowingDamagePort.class.getName(),
                        caught.getStackTrace()[0].getClassName()),
                () -> assertEquals("commitDamage",
                        caught.getStackTrace()[0].getMethodName()),
                () -> assertEquals(0, ThrowingDamagePort.class.getDeclaredFields().length),
                () -> assertSame(invocation.target(), caught.step().target()),
                () -> assertEquals(3, guardPort.calls()),
                () -> assertEquals(1, account.threadChecks()),
                () -> assertEquals(1, account.accountIdReads()),
                () -> assertEquals(1, account.availabilityReads()),
                () -> assertEquals(1, account.balanceReads()),
                () -> assertEquals(1, account.balanceWrites()),
                () -> assertEquals(90, account.currentBalance()),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(reachable, caught),
                        "caught Throwable"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, guardPort),
                        "guard port"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(reachable, guard),
                        "adapted guard"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, invocation),
                        "invocation"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, invocation.target()),
                        "target"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(
                                reachable, caught.step()),
                        "step"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.identityCount(reachable, account),
                        "account access"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, ManaReceipt.class),
                        "receipt"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, ActionDamageTransactionResult.class),
                        "transaction result"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, EffectExecutionResult.class),
                        "effect result"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, EffectTrace.class),
                        "trace"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, EffectTraceEntry.class),
                        "trace entry"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, DamageEffectRequest.class),
                        "request"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, EffectCommitPlan.class),
                        "plan"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, PreparedEffectExecution.class),
                        "preparation"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, EffectExecutionAttempt.class),
                        "attempt"),
                () -> assertEquals(0,
                        P6TemporalReachabilityAssertions.instanceCount(
                                reachable, Throwable.class),
                        "Throwable"));
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

    private static final class CountingAllowingGuardPort
            implements P6RuntimeExecutionBridge.GuardPort {
        private int calls;

        @Override
        public P6RuntimeExecutionBridge.GuardDecision check(
                P6RuntimeExecutionBridge.GuardPoint point, int stepIndex) {
            calls++;
            return P6RuntimeExecutionBridge.GuardDecision.ALLOWED;
        }

        int calls() {
            return calls;
        }
    }

    private static final class SuccessfulDamagePort implements DamageEffectCommitPort {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public EffectStepOutcome commitDamage(DamageEffectStep step) {
            return EffectStepOutcome.applied(1);
        }
    }

    private static final class ThrowingDamagePort implements DamageEffectCommitPort {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public EffectStepOutcome commitDamage(DamageEffectStep step) {
            throw new ProbeRuntimeException(step);
        }
    }

    private static final class ProbeRuntimeException extends RuntimeException {
        private final DamageEffectStep step;

        private ProbeRuntimeException(DamageEffectStep step) {
            this.step = step;
        }

        DamageEffectStep step() {
            return step;
        }
    }

    private static final class P6TemporalReachabilityAssertions {
        static final int MAXIMUM_DEPTH = 16;
        static final int MAXIMUM_VISITED_NODES = 4096;

        private static final List<StaticRoot> STATIC_ROOTS = List.of(
                new StaticRoot(
                        "com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge",
                        "PRODUCTION_EXECUTORS"),
                new StaticRoot(
                        "com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge",
                        "PRODUCTION_ENGINE"),
                new StaticRoot(
                        "com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge",
                        "PRODUCTION_DAMAGE_PORT"),
                new StaticRoot(
                        "com.yo1no.gramarye.magic.runtime.mana."
                                + "UnavailableProductionDamageEffectCommitPort",
                        "INSTANCE"),
                new StaticRoot(
                        "com.yo1no.gramarye.P6RuntimeExecutionCapability",
                        "INSTANCE"),
                new StaticRoot(
                        "com.yo1no.gramarye.ProductionP6RuntimeExecutionInputMapper",
                        "INSTANCE"));
        private static final Set<String> FIELD_TRAVERSAL_TYPES = Set.of(
                ActionExecutorRegistry.class.getName(),
                ActionDamageTransactionEngine.class.getName(),
                DamageActionExecutor.class.getName(),
                DamageEffectResolver.class.getName(),
                EffectExecutionEngine.class.getName(),
                ManaTransactionService.class.getName(),
                UnavailableProductionDamageEffectCommitPort.class.getName(),
                P6RuntimeExecutionCapability.class.getName(),
                SuccessfulDamagePort.class.getName(),
                ThrowingDamagePort.class.getName(),
                "com.yo1no.gramarye.ProductionP6RuntimeExecutionInputMapper",
                "com.yo1no.gramarye.magic.definition.lookup.RegistryActionTypeLookup");
        private static final Set<Class<?>> SCALAR_LEAVES = Set.of(
                Boolean.class,
                Byte.class,
                Short.class,
                Integer.class,
                Long.class,
                Float.class,
                Double.class,
                Character.class,
                String.class,
                Class.class,
                UUID.class,
                ResourceLocation.class);

        private P6TemporalReachabilityAssertions() {}

        static List<String> staticRootCoordinates() {
            return STATIC_ROOTS.stream().map(StaticRoot::coordinate).toList();
        }

        static IdentityHashMap<Object, Boolean> snapshot(Object... reusableRoots) {
            IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
            for (StaticRoot root : STATIC_ROOTS) {
                visit(root.value(), 0, visited);
            }
            for (Object root : reusableRoots) {
                visit(root, 0, visited);
            }
            return visited;
        }

        static int identityCount(
                IdentityHashMap<Object, Boolean> reachable, Object probe) {
            return reachable.containsKey(probe) ? 1 : 0;
        }

        static int instanceCount(
                IdentityHashMap<Object, Boolean> reachable, Class<?> type) {
            return Math.toIntExact(reachable.keySet().stream().filter(type::isInstance).count());
        }

        private static void visit(
                Object value,
                int depth,
                IdentityHashMap<Object, Boolean> visited) {
            if (value == null || visited.containsKey(value)) {
                return;
            }
            if (depth > MAXIMUM_DEPTH) {
                throw new AssertionError("P6 reachability depth exceeded: " + depth);
            }
            visited.put(value, Boolean.TRUE);
            if (visited.size() > MAXIMUM_VISITED_NODES) {
                throw new AssertionError(
                        "P6 reachability node bound exceeded: " + visited.size());
            }

            Class<?> type = value.getClass();
            if (SCALAR_LEAVES.contains(type)
                    || (type.isEnum() && !FIELD_TRAVERSAL_TYPES.contains(type.getName()))) {
                return;
            }
            if (type.isArray()) {
                if (!type.getComponentType().isPrimitive()) {
                    for (int index = 0; index < Array.getLength(value); index++) {
                        visit(Array.get(value, index), depth + 1, visited);
                    }
                }
                return;
            }
            if (value instanceof Optional<?> optional) {
                optional.ifPresent(element -> visit(element, depth + 1, visited));
                return;
            }
            if (value instanceof Map<?, ?> map) {
                var entries = new ArrayList<>(map.entrySet());
                entries.sort(Comparator.comparing(entry -> stableTraversalKey(entry.getKey())));
                assertUniqueTraversalKeys(entries.stream()
                        .map(entry -> stableTraversalKey(entry.getKey()))
                        .toList());
                for (var entry : entries) {
                    visit(entry.getKey(), depth + 1, visited);
                    visit(entry.getValue(), depth + 1, visited);
                }
                return;
            }
            if (value instanceof Iterable<?> iterable) {
                var elements = new ArrayList<>();
                iterable.forEach(elements::add);
                if (!(value instanceof List<?>)) {
                    elements.sort(Comparator.comparing(
                            P6TemporalReachabilityAssertions::stableTraversalKey));
                    assertUniqueTraversalKeys(elements.stream()
                            .map(P6TemporalReachabilityAssertions::stableTraversalKey)
                            .toList());
                }
                for (Object element : elements) {
                    visit(element, depth + 1, visited);
                }
                return;
            }
            if (!FIELD_TRAVERSAL_TYPES.contains(type.getName())) {
                throw new AssertionError(
                        "unapproved P6 reachability type: " + type.getName());
            }

            Arrays.stream(type.getDeclaredFields())
                    .filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .sorted(java.util.Comparator.comparing(Field::getName))
                    .forEach(field -> visit(read(field, value), depth + 1, visited));
        }

        private static String stableTraversalKey(Object value) {
            if (value == null) {
                return "<null>";
            }
            Class<?> type = value.getClass();
            if (SCALAR_LEAVES.contains(type) || type.isEnum()) {
                return type.getName() + ":" + value;
            }
            throw new AssertionError(
                    "unordered P6 traversal element has no closed scalar key: "
                            + type.getName());
        }

        private static void assertUniqueTraversalKeys(List<String> keys) {
            if (keys.size() != Set.copyOf(keys).size()) {
                throw new AssertionError("ambiguous unordered P6 traversal keys: " + keys);
            }
        }

        private static Object read(Field field, Object owner) {
            if (!field.trySetAccessible()) {
                throw new AssertionError("unreadable field: " + field);
            }
            try {
                return field.get(owner);
            } catch (IllegalAccessException exception) {
                throw new AssertionError("unreadable field: " + field, exception);
            }
        }

        private record StaticRoot(String ownerName, String fieldName) {
            String coordinate() {
                return ownerName + "#" + fieldName;
            }

            Object value() {
                try {
                    Class<?> owner = Class.forName(ownerName);
                    Field field = owner.getDeclaredField(fieldName);
                    if (!Modifier.isStatic(field.getModifiers())
                            || !Modifier.isFinal(field.getModifiers())
                            || !field.trySetAccessible()) {
                        throw new AssertionError("invalid closed P6 root: " + coordinate());
                    }
                    return field.get(null);
                } catch (ClassNotFoundException
                        | NoSuchFieldException
                        | IllegalAccessException exception) {
                    throw new AssertionError(
                            "unavailable closed P6 root: " + coordinate(), exception);
                }
            }
        }
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
