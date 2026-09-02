package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class ActionDamageTransactionPreDebitTest {
    @Test
    void unsupportedActionRejectsBeforeExecutorManaGuardResolverAndPort() {
        var resolver = new ActionTransactionTestFixtures.TransactionRecordingResolver(
                (request, capacity) -> {
                    throw new AssertionError("resolver must not be called");
                });
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100L);
        var guard = ActionTransactionTestFixtures.TransactionRecordingGuard.allowing();
        var port = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();
        var engine = ActionTransactionTestFixtures.engine(
                new ActionExecutorRegistry(List.of()), resolver);

        ActionDamageTransactionResult result = engine.execute(
                ActionTransactionTestFixtures.invocation(
                        ResourceLocation.fromNamespaceAndPath("gramarye", "missing"), 25L, 10L),
                account,
                0,
                guard,
                port);

        assertRejected(result, EffectRejectReason.UNSUPPORTED_ACTION);
        assertInstanceOf(ManaNotRequired.class, result.manaSummary());
        assertEquals(0, result.manaMutationCount());
        assertEquals(0, account.totalAccesses());
        assertEquals(0, resolver.calls());
        assertEquals(List.of(), guard.checks());
        assertEquals(0, port.availabilityChecks());
        assertEquals(List.of(), port.committedIndexes());
    }

    @Test
    void noActionRequestRejectsBeforeManaGuardResolverAndPort() {
        var resolver = new ActionTransactionTestFixtures.TransactionRecordingResolver(
                (request, capacity) -> {
                    throw new AssertionError("resolver must not be called");
                });
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100L);
        var guard = ActionTransactionTestFixtures.TransactionRecordingGuard.allowing();
        var port = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();
        var engine = ActionTransactionTestFixtures.engine(
                ActionTransactionTestFixtures.registry(input -> NoActionRequest.INSTANCE),
                resolver);

        ActionDamageTransactionResult result = engine.execute(
                ActionTransactionTestFixtures.invocation(10L), account, 0, guard, port);

        assertRejected(result, EffectRejectReason.INVALID_REQUEST);
        assertInstanceOf(ManaNotRequired.class, result.manaSummary());
        assertEquals(0, result.manaMutationCount());
        assertEquals(0, account.totalAccesses());
        assertEquals(0, resolver.calls());
        assertEquals(List.of(), guard.checks());
        assertEquals(0, port.availabilityChecks());
        assertEquals(List.of(), port.committedIndexes());
    }

    @Test
    void invalidTargetRejectsBeforeDebitAndPort() {
        var resolver = new ActionTransactionTestFixtures.TransactionRecordingResolver(
                (request, capacity) ->
                        new RejectedEffectResolution(EffectRejectReason.INVALID_TARGET));
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100L);
        var guard = ActionTransactionTestFixtures.TransactionRecordingGuard.allowing();
        var port = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();

        ActionDamageTransactionResult result = ActionTransactionTestFixtures.engine(resolver)
                .execute(ActionTransactionTestFixtures.invocation(10L), account, 0, guard, port);

        assertRejected(result, EffectRejectReason.INVALID_TARGET);
        assertInstanceOf(ManaNotRequired.class, result.manaSummary());
        assertEquals(0, account.totalAccesses());
        assertEquals(1, resolver.calls());
        assertEquals(List.of(EffectGuardPoint.entry()), guard.checks());
        assertEquals(0, port.availabilityChecks());
        assertEquals(List.of(), port.committedIndexes());
    }

    @Test
    void childCapacityBoundRejectsBeforeDebitResolutionAndPort() {
        var resolver = new ActionTransactionTestFixtures.TransactionRecordingResolver(
                ActionTransactionTestFixtures.resolverFor(
                        ActionTransactionTestFixtures.plan(1)));
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100L);
        var guard = ActionTransactionTestFixtures.TransactionRecordingGuard.allowing();
        var port = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();

        ActionDamageTransactionResult result = ActionTransactionTestFixtures.engine(resolver)
                .execute(ActionTransactionTestFixtures.invocation(10L), account, -1, guard, port);

        assertRejected(result, EffectRejectReason.BOUND_EXCEEDED);
        assertInstanceOf(ManaNotRequired.class, result.manaSummary());
        assertEquals(0, account.totalAccesses());
        assertEquals(0, resolver.calls());
        assertEquals(List.of(EffectGuardPoint.entry()), guard.checks());
        assertEquals(0, port.availabilityChecks());
        assertEquals(List.of(), port.committedIndexes());
    }

    @Test
    void unavailablePortRejectsBeforeDebitAndStepCommit() {
        var resolver = new ActionTransactionTestFixtures.TransactionRecordingResolver(
                ActionTransactionTestFixtures.resolverFor(
                        ActionTransactionTestFixtures.plan(1)));
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100L);
        var guard = ActionTransactionTestFixtures.TransactionRecordingGuard.allowing();
        var port = new ActionTransactionTestFixtures.TransactionRecordingPort(
                false, List.of());

        ActionDamageTransactionResult result = ActionTransactionTestFixtures.engine(resolver)
                .execute(ActionTransactionTestFixtures.invocation(10L), account, 0, guard, port);

        assertRejected(result, EffectRejectReason.COMMIT_PORT_UNAVAILABLE);
        assertInstanceOf(ManaNotRequired.class, result.manaSummary());
        assertEquals(0, account.totalAccesses());
        assertEquals(1, resolver.calls());
        assertEquals(List.of(EffectGuardPoint.entry()), guard.checks());
        assertEquals(1, port.availabilityChecks());
        assertEquals(List.of(), port.committedIndexes());
    }

    @Test
    void entryAndPreCommitCancellationOrDeadlineRejectBeforeDebitAndStepCommit() {
        for (EffectGuardDecision decision : List.of(
                EffectGuardDecision.CANCELLED,
                EffectGuardDecision.DEADLINE_EXCEEDED)) {
            var entryResolver = new ActionTransactionTestFixtures.TransactionRecordingResolver(
                    ActionTransactionTestFixtures.resolverFor(
                            ActionTransactionTestFixtures.plan(1)));
            var entryAccount = new ActionTransactionTestFixtures.RecordingManaAccount(100L);
            var entryGuard = new ActionTransactionTestFixtures.TransactionRecordingGuard(
                    point -> decision);
            var entryPort = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();

            ActionDamageTransactionResult entryResult =
                    ActionTransactionTestFixtures.engine(entryResolver)
                            .execute(
                                    ActionTransactionTestFixtures.invocation(10L),
                                    entryAccount,
                                    0,
                                    entryGuard,
                                    entryPort);

            assertRejected(entryResult, rejectReason(decision));
            assertEquals(0, entryAccount.totalAccesses());
            assertEquals(0, entryResolver.calls());
            assertEquals(List.of(EffectGuardPoint.entry()), entryGuard.checks());
            assertEquals(0, entryPort.availabilityChecks());
            assertEquals(List.of(), entryPort.committedIndexes());

            var preCommitResolver =
                    new ActionTransactionTestFixtures.TransactionRecordingResolver(
                            ActionTransactionTestFixtures.resolverFor(
                                    ActionTransactionTestFixtures.plan(1)));
            var preCommitAccount = new ActionTransactionTestFixtures.RecordingManaAccount(100L);
            var preCommitGuard = new ActionTransactionTestFixtures.TransactionRecordingGuard(
                    point -> point.kind() == EffectGuardPointKind.PRE_COMMIT
                            ? decision
                            : EffectGuardDecision.ALLOWED);
            var preCommitPort =
                    ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();

            ActionDamageTransactionResult preCommitResult =
                    ActionTransactionTestFixtures.engine(preCommitResolver)
                            .execute(
                                    ActionTransactionTestFixtures.invocation(10L),
                                    preCommitAccount,
                                    0,
                                    preCommitGuard,
                                    preCommitPort);

            assertRejected(preCommitResult, rejectReason(decision));
            assertEquals(0, preCommitAccount.totalAccesses());
            assertEquals(1, preCommitResolver.calls());
            assertEquals(
                    List.of(EffectGuardPoint.entry(), EffectGuardPoint.preCommit()),
                    preCommitGuard.checks());
            assertEquals(1, preCommitPort.availabilityChecks());
            assertEquals(List.of(), preCommitPort.committedIndexes());
        }
    }

    @Test
    void zeroCostRequestSkipsManaServiceAndExecutesOneStep() {
        var resolver = new ActionTransactionTestFixtures.TransactionRecordingResolver(
                ActionTransactionTestFixtures.resolverFor(
                        ActionTransactionTestFixtures.plan(1)));
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100L);
        var guard = ActionTransactionTestFixtures.TransactionRecordingGuard.allowing();
        var port = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();

        ActionDamageTransactionResult result = ActionTransactionTestFixtures.engine(resolver)
                .execute(ActionTransactionTestFixtures.invocation(), account, 0, guard, port);

        assertEquals(EffectTerminalStatus.SUCCEEDED, result.effectResult().status());
        assertInstanceOf(ManaNotRequired.class, result.manaSummary());
        assertEquals(0, result.manaMutationCount());
        assertEquals(0, account.totalAccesses());
        assertEquals(1, resolver.calls());
        assertEquals(
                List.of(
                        EffectGuardPoint.entry(),
                        EffectGuardPoint.preCommit(),
                        EffectGuardPoint.beforeStep(0)),
                guard.checks());
        assertEquals(1, port.availabilityChecks());
        assertEquals(List.of(0), port.committedIndexes());
        assertEquals(
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.TERMINAL_SUCCEEDED),
                ActionTransactionTestFixtures.stages(result));
    }

    private static EffectRejectReason rejectReason(EffectGuardDecision decision) {
        return switch (decision) {
            case CANCELLED -> EffectRejectReason.CANCELLED;
            case DEADLINE_EXCEEDED -> EffectRejectReason.DEADLINE_EXCEEDED;
            case ALLOWED -> throw new AssertionError("expected a rejecting decision");
        };
    }

    private static void assertRejected(
            ActionDamageTransactionResult result, EffectRejectReason reason) {
        assertEquals(EffectTerminalStatus.REJECTED, result.effectResult().status());
        assertEquals(reason, result.effectResult().rejectReason().orElseThrow());
        assertEquals(0, result.effectResult().executedStepCount());
        assertEquals(0, result.effectResult().primaryMutationCount());
    }
}
