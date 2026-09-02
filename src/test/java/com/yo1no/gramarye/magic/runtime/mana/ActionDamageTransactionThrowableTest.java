package com.yo1no.gramarye.magic.runtime.mana;

import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.ACCOUNT_ID;
import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.engine;
import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.invocation;
import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.plan;
import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.registry;
import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.resolverFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.RecordingManaAccount;
import com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.TransactionRecordingGuard;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

final class ActionDamageTransactionThrowableTest {
    private static final long INITIAL_BALANCE = 100L;
    private static final long MANA_COST = 10L;

    @Test
    void executorRuntimeExceptionAndErrorPropagateSameObjectOnce() {
        assertExecutorThrowable(new IllegalStateException("executor-runtime"));
        assertExecutorThrowable(new AssertionError("executor-error"));
    }

    @Test
    void resolverRuntimeExceptionAndErrorPropagateSameObject() {
        assertResolverThrowable(new IllegalStateException("resolver-runtime"));
        assertResolverThrowable(new AssertionError("resolver-error"));
    }

    @Test
    void entryAndPreCommitGuardThrowablesPropagateSameObjectBeforeDebit() {
        assertGuardThrowable(
                EffectGuardPointKind.ENTRY,
                new IllegalStateException("entry-runtime"));
        assertGuardThrowable(
                EffectGuardPointKind.ENTRY,
                new AssertionError("entry-error"));
        assertGuardThrowable(
                EffectGuardPointKind.PRE_COMMIT,
                new IllegalStateException("pre-commit-runtime"));
        assertGuardThrowable(
                EffectGuardPointKind.PRE_COMMIT,
                new AssertionError("pre-commit-error"));
    }

    @Test
    void portAvailabilityThrowablesPropagateSameObjectBeforeDebit() {
        assertPortAvailabilityThrowable(
                new IllegalStateException("availability-runtime"));
        assertPortAvailabilityThrowable(new AssertionError("availability-error"));
    }

    @Test
    void beforeStepGuardAndCommitPortThrowablesAfterDebitDoNotRefund() {
        assertAfterDebitGuardThrowable(
                new IllegalStateException("before-step-runtime"));
        assertAfterDebitGuardThrowable(new AssertionError("before-step-error"));
        assertAfterDebitPortThrowable(new IllegalStateException("commit-runtime"));
        assertAfterDebitPortThrowable(new AssertionError("commit-error"));
    }

    @Test
    void manaAccountDependencyThrowablesPropagateSameObjectWithoutRetryOrAutomaticRefund() {
        assertAccountThrowableBeforeDebit(
                AccountAccessPoint.LOGIC_THREAD,
                new IllegalStateException("mana-runtime"));
        assertAccountThrowableBeforeDebit(
                AccountAccessPoint.AVAILABILITY,
                new AssertionError("mana-error"));
        assertAccountThrowableAfterDebit(
                AccountAccessPoint.LOGIC_THREAD,
                new IllegalStateException("refund-runtime"));
        assertAccountThrowableAfterDebit(
                AccountAccessPoint.AVAILABILITY,
                new AssertionError("refund-error"));
    }

    private static void assertExecutorThrowable(Throwable expected) {
        AtomicInteger calls = new AtomicInteger();
        ActionExecutor executor = input -> {
            calls.incrementAndGet();
            throwSame(expected);
            return NoActionRequest.INSTANCE;
        };
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        ThrowablePort port = ThrowablePort.applying();

        assertSameThrowable(
                expected,
                () -> engine(registry(executor), resolverFor(plan(1)))
                        .execute(
                                invocation(MANA_COST),
                                account,
                                0,
                                TransactionRecordingGuard.allowing(),
                                port));

        assertEquals(1, calls.get());
        assertEquals(0, account.totalAccesses());
        assertEquals(0, port.availabilityCalls());
        assertEquals(0, port.commitCalls());
    }

    private static void assertResolverThrowable(Throwable expected) {
        AtomicInteger calls = new AtomicInteger();
        EffectResolver resolver = (request, capacity) -> {
            calls.incrementAndGet();
            throwSame(expected);
            return new RejectedEffectResolution(EffectRejectReason.INVALID_REQUEST);
        };
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        ThrowablePort port = ThrowablePort.applying();

        assertSameThrowable(
                expected,
                () -> engine(resolver)
                        .execute(
                                invocation(MANA_COST),
                                account,
                                0,
                                TransactionRecordingGuard.allowing(),
                                port));

        assertEquals(1, calls.get());
        assertEquals(0, account.totalAccesses());
        assertEquals(0, port.availabilityCalls());
        assertEquals(0, port.commitCalls());
    }

    private static void assertGuardThrowable(
            EffectGuardPointKind failurePoint, Throwable expected) {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        TransactionRecordingGuard guard = new TransactionRecordingGuard(point -> {
            if (point.kind() == failurePoint) {
                throwSame(expected);
            }
            return EffectGuardDecision.ALLOWED;
        });
        ThrowablePort port = ThrowablePort.applying();

        assertSameThrowable(
                expected,
                () -> engine(resolverFor(plan(1)))
                        .execute(invocation(MANA_COST), account, 0, guard, port));

        assertEquals(0, account.totalAccesses());
        assertEquals(0, port.commitCalls());
        assertEquals(
                failurePoint == EffectGuardPointKind.PRE_COMMIT ? 1 : 0,
                port.availabilityCalls());
    }

    private static void assertPortAvailabilityThrowable(Throwable expected) {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        ThrowablePort port = ThrowablePort.throwingAvailability(expected);

        assertSameThrowable(
                expected,
                () -> engine(resolverFor(plan(1)))
                        .execute(
                                invocation(MANA_COST),
                                account,
                                0,
                                TransactionRecordingGuard.allowing(),
                                port));

        assertEquals(0, account.totalAccesses());
        assertEquals(1, port.availabilityCalls());
        assertEquals(0, port.commitCalls());
    }

    private static void assertAfterDebitGuardThrowable(Throwable expected) {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        TransactionRecordingGuard guard = new TransactionRecordingGuard(point -> {
            if (point.kind() == EffectGuardPointKind.BEFORE_STEP) {
                throwSame(expected);
            }
            return EffectGuardDecision.ALLOWED;
        });
        ThrowablePort port = ThrowablePort.applying();

        assertSameThrowable(
                expected,
                () -> engine(resolverFor(plan(1)))
                        .execute(invocation(MANA_COST), account, 0, guard, port));

        assertDebitRemainsWithoutRefund(account);
        assertEquals(1, port.availabilityCalls());
        assertEquals(0, port.commitCalls());
    }

    private static void assertAfterDebitPortThrowable(Throwable expected) {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        ThrowablePort port = ThrowablePort.throwingCommit(expected);

        assertSameThrowable(
                expected,
                () -> engine(resolverFor(plan(1)))
                        .execute(
                                invocation(MANA_COST),
                                account,
                                0,
                                TransactionRecordingGuard.allowing(),
                                port));

        assertDebitRemainsWithoutRefund(account);
        assertEquals(1, port.availabilityCalls());
        assertEquals(1, port.commitCalls());
    }

    private static void assertAccountThrowableBeforeDebit(
            AccountAccessPoint point, Throwable expected) {
        ThrowingManaAccount account = new ThrowingManaAccount(point, 1, expected);
        ThrowablePort port = ThrowablePort.applying();

        assertSameThrowable(
                expected,
                () -> engine(resolverFor(plan(1)))
                        .execute(
                                invocation(MANA_COST),
                                account,
                                0,
                                TransactionRecordingGuard.allowing(),
                                port));

        assertEquals(0, account.balanceWrites());
        assertEquals(INITIAL_BALANCE, account.currentBalance());
        assertEquals(1, account.calls(point));
        assertEquals(0, port.commitCalls());
    }

    private static void assertAccountThrowableAfterDebit(
            AccountAccessPoint point, Throwable expected) {
        ThrowingManaAccount account = new ThrowingManaAccount(point, 2, expected);
        ThrowablePort port = new ThrowablePort(
                null, null, List.of(EffectStepOutcome.notApplied()));

        assertSameThrowable(
                expected,
                () -> engine(resolverFor(plan(1)))
                        .execute(
                                invocation(MANA_COST),
                                account,
                                0,
                                TransactionRecordingGuard.allowing(),
                                port));

        assertEquals(1, account.balanceWrites());
        assertEquals(INITIAL_BALANCE - MANA_COST, account.currentBalance());
        assertEquals(2, account.calls(point));
        assertTrue(account.logicThreadCalls() <= 2);
        assertTrue(account.availabilityCalls() <= 2);
        assertEquals(1, port.commitCalls());
    }

    private static void assertDebitRemainsWithoutRefund(RecordingManaAccount account) {
        assertEquals(1, account.balanceWrites());
        assertEquals(INITIAL_BALANCE - MANA_COST, account.currentBalance());
        assertEquals(1, account.availabilityReads());
        assertEquals(1, account.balanceReads());
    }

    private static void assertSameThrowable(Throwable expected, Executable executable) {
        Throwable actual;
        if (expected instanceof RuntimeException) {
            actual = assertThrows(RuntimeException.class, executable);
        } else if (expected instanceof Error) {
            actual = assertThrows(Error.class, executable);
        } else {
            throw new AssertionError("test fixture requires an unchecked throwable");
        }
        assertSame(expected, actual);
    }

    private static void throwSame(Throwable expected) {
        if (expected instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (expected instanceof Error error) {
            throw error;
        }
        throw new AssertionError("test fixture requires an unchecked throwable");
    }

    private enum AccountAccessPoint {
        LOGIC_THREAD,
        AVAILABILITY
    }

    private static final class ThrowablePort implements DamageEffectCommitPort {
        private final Throwable availabilityFailure;
        private final Throwable commitFailure;
        private final List<EffectStepOutcome> outcomes;
        private int availabilityCalls;
        private int commitCalls;

        private ThrowablePort(
                Throwable availabilityFailure,
                Throwable commitFailure,
                List<EffectStepOutcome> outcomes) {
            this.availabilityFailure = availabilityFailure;
            this.commitFailure = commitFailure;
            this.outcomes = List.copyOf(outcomes);
        }

        private static ThrowablePort applying() {
            return new ThrowablePort(null, null, List.of());
        }

        private static ThrowablePort throwingAvailability(Throwable expected) {
            return new ThrowablePort(expected, null, List.of());
        }

        private static ThrowablePort throwingCommit(Throwable expected) {
            return new ThrowablePort(null, expected, List.of());
        }

        @Override
        public boolean isAvailable() {
            availabilityCalls++;
            if (availabilityFailure != null) {
                throwSame(availabilityFailure);
            }
            return true;
        }

        @Override
        public EffectStepOutcome commitDamage(DamageEffectStep step) {
            int invocation = commitCalls++;
            if (commitFailure != null) {
                throwSame(commitFailure);
            }
            return invocation < outcomes.size()
                    ? outcomes.get(invocation)
                    : EffectStepOutcome.applied(1);
        }

        private int availabilityCalls() {
            return availabilityCalls;
        }

        private int commitCalls() {
            return commitCalls;
        }
    }

    private static final class ThrowingManaAccount implements ManaAccountAccess {
        private final AccountAccessPoint failurePoint;
        private final int throwOnCall;
        private final Throwable expected;
        private long balance = INITIAL_BALANCE;
        private int logicThreadCalls;
        private int availabilityCalls;
        private int balanceWrites;

        private ThrowingManaAccount(
                AccountAccessPoint failurePoint, int throwOnCall, Throwable expected) {
            this.failurePoint = failurePoint;
            this.throwOnCall = throwOnCall;
            this.expected = expected;
        }

        @Override
        public boolean isLogicThread() {
            logicThreadCalls++;
            failIfConfigured(AccountAccessPoint.LOGIC_THREAD, logicThreadCalls);
            return true;
        }

        @Override
        public UUID accountId() {
            return ACCOUNT_ID;
        }

        @Override
        public ManaAvailability availability() {
            availabilityCalls++;
            failIfConfigured(AccountAccessPoint.AVAILABILITY, availabilityCalls);
            return ManaAvailability.AVAILABLE;
        }

        @Override
        public long balance() {
            return balance;
        }

        @Override
        public void writeBalance(long replacement) {
            balanceWrites++;
            balance = replacement;
        }

        private void failIfConfigured(AccountAccessPoint point, int call) {
            if (failurePoint == point && throwOnCall == call) {
                throwSame(expected);
            }
        }

        private int calls(AccountAccessPoint point) {
            return point == AccountAccessPoint.LOGIC_THREAD
                    ? logicThreadCalls
                    : availabilityCalls;
        }

        private long currentBalance() {
            return balance;
        }

        private int logicThreadCalls() {
            return logicThreadCalls;
        }

        private int availabilityCalls() {
            return availabilityCalls;
        }

        private int balanceWrites() {
            return balanceWrites;
        }
    }
}
