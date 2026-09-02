package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ActionDamageTransactionResultTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("40000000-0000-4000-8000-000000000001");

    @Test
    void acceptsAllFiveClosedManaSummaryVariants() {
        List<ActionDamageTransactionResult> results = List.of(
                result(noManaSucceeded(), new ManaNotRequired(), 0, Optional.empty()),
                result(
                        debitRejected(EffectRejectReason.INSUFFICIENT_MANA),
                        new ManaDebitRejected(ManaRejectReason.INSUFFICIENT_MANA),
                        0,
                        Optional.empty()),
                result(debitedSucceeded(), new ManaDebited(openDebit()), 1, Optional.empty()),
                result(
                        compensated(),
                        new ManaRefunded(refundedDebit(), refundReceipt()),
                        2,
                        Optional.of(provisional())),
                result(
                        compensationFailed(),
                        new ManaRefundFailed(
                                openDebit(), ManaRejectReason.MANA_STATE_UNAVAILABLE),
                        1,
                        Optional.of(provisional())));

        assertEquals(
                List.of(
                        ManaExecutionSummaryKind.MANA_NOT_REQUIRED,
                        ManaExecutionSummaryKind.DEBIT_REJECTED,
                        ManaExecutionSummaryKind.DEBITED,
                        ManaExecutionSummaryKind.REFUNDED,
                        ManaExecutionSummaryKind.REFUND_FAILED),
                results.stream().map(value -> value.manaSummary().kind()).toList());
        assertEquals(
                List.of(0, 0, 1, 2, 1),
                results.stream().map(ActionDamageTransactionResult::manaMutationCount).toList());
    }

    @Test
    void rejectsSummaryAndFinalStatusMismatch() {
        assertInvalid(() -> result(
                noManaSucceeded(), new ManaDebited(openDebit()), 1, Optional.empty()));
        assertInvalid(() -> result(
                debitedSucceeded(), new ManaNotRequired(), 0, Optional.empty()));
        assertInvalid(() -> result(
                compensated(),
                new ManaRefundFailed(openDebit(), ManaRejectReason.MANA_STATE_UNAVAILABLE),
                1,
                Optional.of(provisional())));
        assertInvalid(() -> result(
                compensationFailed(),
                new ManaRefunded(refundedDebit(), refundReceipt()),
                2,
                Optional.of(provisional())));
    }

    @Test
    void rejectsMutationCountMismatch() {
        assertInvalid(() -> result(
                noManaSucceeded(), new ManaNotRequired(), 1, Optional.empty()));
        assertInvalid(() -> result(
                debitedSucceeded(), new ManaDebited(openDebit()), 0, Optional.empty()));
        assertInvalid(() -> result(
                compensated(),
                new ManaRefunded(refundedDebit(), refundReceipt()),
                1,
                Optional.of(provisional())));
        assertInvalid(() -> result(
                compensationFailed(),
                new ManaRefundFailed(openDebit(), ManaRejectReason.MANA_STATE_UNAVAILABLE),
                2,
                Optional.of(provisional())));
    }

    @Test
    void rejectsReceiptAndReasonPresenceMismatch() {
        assertThrows(NullPointerException.class, () -> new ManaDebitRejected(null));
        assertThrows(NullPointerException.class, () -> new ManaDebited(null));
        assertThrows(
                NullPointerException.class,
                () -> new ManaRefunded(null, refundReceipt()));
        assertThrows(
                NullPointerException.class,
                () -> new ManaRefunded(refundedDebit(), null));
        assertThrows(
                NullPointerException.class,
                () -> new ManaRefundFailed(openDebit(), null));
        assertInvalid(() -> new ManaDebitRejected(ManaRejectReason.WRONG_THREAD));
        assertInvalid(() -> new ManaDebited(refundedDebit()));
        assertInvalid(() -> new ManaRefunded(openDebit(), refundReceipt()));
        assertInvalid(() -> new ManaRefundFailed(
                openDebit(), ManaRejectReason.RECEIPT_ACCOUNT_MISMATCH));
        assertThrows(NullPointerException.class, () -> new ActionDamageTransactionResult(
                noManaSucceeded(), new ManaNotRequired(), 0, null));
    }

    @Test
    void debitedSummaryAcceptsOnlySucceededOrPartiallySucceeded() {
        ActionDamageTransactionResult succeeded = result(
                debitedSucceeded(), new ManaDebited(openDebit()), 1, Optional.empty());
        ActionDamageTransactionResult partial = result(
                debitedPartial(), new ManaDebited(openDebit()), 1, Optional.empty());

        assertEquals(EffectTerminalStatus.SUCCEEDED, succeeded.effectResult().status());
        assertEquals(
                EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                partial.effectResult().status());
        assertInvalid(() -> result(
                noManaFailed(), new ManaDebited(openDebit()), 1, Optional.empty()));
        assertInvalid(() -> result(
                debitRejected(EffectRejectReason.MANA_STATE_UNAVAILABLE),
                new ManaDebited(openDebit()),
                1,
                Optional.empty()));
    }

    @Test
    void compensatedResultRetainsOriginalProvisionalReason() {
        ActionDamageTransactionResult result = result(
                compensated(),
                new ManaRefunded(refundedDebit(), refundReceipt()),
                2,
                Optional.of(provisional()));

        ProvisionalEffectFailure retained = result.provisionalFailure().orElseThrow();
        assertEquals(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED, retained.reason());
        assertEquals(0, retained.stepIndex());
        assertEquals(
                Optional.of(retained.reason()), result.effectResult().failureReason());
        assertEquals(ManaExecutionSummaryKind.REFUNDED, result.manaSummary().kind());
    }

    @Test
    void compensationFailedResultRetainsOriginalAndManaRejectReasons() {
        ActionDamageTransactionResult result = result(
                compensationFailed(),
                new ManaRefundFailed(
                        openDebit(), ManaRejectReason.INVALID_TRANSACTION_STATE),
                1,
                Optional.of(provisional()));

        ManaRefundFailed summary = assertInstanceOf(
                ManaRefundFailed.class, result.manaSummary());
        assertEquals(
                EffectFailureReason.PRIMARY_STEP_NOT_APPLIED,
                result.provisionalFailure().orElseThrow().reason());
        assertEquals(
                EffectFailureReason.COMPENSATION_REFUND_FAILED,
                result.effectResult().failureReason().orElseThrow());
        assertEquals(
                ManaRejectReason.INVALID_TRANSACTION_STATE,
                summary.refundRejectReason());
        assertEquals(ManaRefundState.OPEN, summary.debitReceipt().refundState());

        assertInvalid(() -> result(
                compensationFailedBeforeStep(),
                new ManaRefundFailed(
                        openDebit(), ManaRejectReason.MANA_STATE_UNAVAILABLE),
                1,
                Optional.of(provisional())));
        assertInvalid(() -> result(
                compensationFailed(),
                new ManaRefundFailed(
                        openDebit(), ManaRejectReason.MANA_STATE_UNAVAILABLE),
                1,
                Optional.of(new ProvisionalEffectFailure(
                        EffectFailureReason.EXECUTION_CANCELLED, 0))));
    }

    @Test
    void resultAndSummaryAreImmutableAndRetainNoLiveOrThrowableState() {
        ManaReceipt receipt = ManaReceipt.create(
                ManaOperationKind.DEBIT,
                ManaReason.SKILL_COST,
                ACCOUNT_ID,
                10L,
                100L,
                90L);
        ManaReceiptSnapshot snapshot = ManaReceiptSnapshot.from(receipt);
        assertNotSame(receipt.identity(), snapshot.identity());
        receipt.markRefunded();
        assertEquals(ManaRefundState.OPEN, snapshot.refundState());

        List<Class<?>> valueTypes = List.of(
                ManaReceiptSnapshot.class,
                ManaNotRequired.class,
                ManaDebitRejected.class,
                ManaDebited.class,
                ManaRefunded.class,
                ManaRefundFailed.class,
                ProvisionalEffectFailure.class,
                ActionDamageTransactionResult.class);
        assertTrue(valueTypes.stream().allMatch(Class::isRecord));
        assertTrue(valueTypes.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())
                        && !Modifier.isStatic(field.getModifiers())));
        assertFalse(valueTypes.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .anyMatch(field -> field.getType() == Object.class
                        || Throwable.class.isAssignableFrom(field.getType())
                        || field.getType() == ManaReceipt.class
                        || field.getType() == ManaAccountAccess.class
                        || field.getType() == DamageEffectCommitPort.class));

        ActionDamageTransactionResult result = result(
                noManaSucceeded(), new ManaNotRequired(), 0, Optional.empty());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.effectResult().trace().entries().add(
                        EffectTraceEntry.withoutStep(
                                0, EffectTraceStage.TERMINAL_REJECTED)));
    }

    private static ActionDamageTransactionResult result(
            EffectExecutionResult effect,
            ManaExecutionSummary summary,
            int mutationCount,
            Optional<ProvisionalEffectFailure> provisional) {
        return new ActionDamageTransactionResult(
                effect, summary, mutationCount, provisional);
    }

    private static EffectExecutionResult noManaSucceeded() {
        return effect(
                EffectTerminalStatus.SUCCEEDED,
                Optional.empty(),
                Optional.empty(),
                -1,
                1,
                1,
                1,
                EffectTraceStage.REQUEST_VALIDATED,
                EffectTraceStage.TARGET_RESOLVED,
                EffectTraceStage.STEP_APPLIED,
                EffectTraceStage.TERMINAL_SUCCEEDED);
    }

    private static EffectExecutionResult debitRejected(EffectRejectReason reason) {
        return effect(
                EffectTerminalStatus.REJECTED,
                Optional.of(reason),
                Optional.empty(),
                -1,
                1,
                0,
                0,
                EffectTraceStage.REQUEST_VALIDATED,
                EffectTraceStage.TARGET_RESOLVED,
                EffectTraceStage.TERMINAL_REJECTED);
    }

    private static EffectExecutionResult debitedSucceeded() {
        return effect(
                EffectTerminalStatus.SUCCEEDED,
                Optional.empty(),
                Optional.empty(),
                -1,
                1,
                1,
                1,
                EffectTraceStage.REQUEST_VALIDATED,
                EffectTraceStage.TARGET_RESOLVED,
                EffectTraceStage.MANA_DEBITED,
                EffectTraceStage.STEP_APPLIED,
                EffectTraceStage.TERMINAL_SUCCEEDED);
    }

    private static EffectExecutionResult debitedPartial() {
        return effect(
                EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                Optional.empty(),
                Optional.of(EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE),
                0,
                1,
                1,
                1,
                EffectTraceStage.REQUEST_VALIDATED,
                EffectTraceStage.TARGET_RESOLVED,
                EffectTraceStage.MANA_DEBITED,
                EffectTraceStage.STEP_APPLIED_WITH_FAILURE,
                EffectTraceStage.TERMINAL_PARTIAL);
    }

    private static EffectExecutionResult noManaFailed() {
        return effect(
                EffectTerminalStatus.FAILED,
                Optional.empty(),
                Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED),
                0,
                1,
                1,
                0,
                EffectTraceStage.REQUEST_VALIDATED,
                EffectTraceStage.TARGET_RESOLVED,
                EffectTraceStage.STEP_NOT_APPLIED,
                EffectTraceStage.TERMINAL_FAILED);
    }

    private static EffectExecutionResult compensated() {
        return effect(
                EffectTerminalStatus.COMPENSATED,
                Optional.empty(),
                Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED),
                0,
                1,
                1,
                0,
                EffectTraceStage.REQUEST_VALIDATED,
                EffectTraceStage.TARGET_RESOLVED,
                EffectTraceStage.MANA_DEBITED,
                EffectTraceStage.STEP_NOT_APPLIED,
                EffectTraceStage.REFUND_APPLIED,
                EffectTraceStage.TERMINAL_COMPENSATED);
    }

    private static EffectExecutionResult compensationFailed() {
        return effect(
                EffectTerminalStatus.COMPENSATION_FAILED,
                Optional.empty(),
                Optional.of(EffectFailureReason.COMPENSATION_REFUND_FAILED),
                -1,
                1,
                1,
                0,
                EffectTraceStage.REQUEST_VALIDATED,
                EffectTraceStage.TARGET_RESOLVED,
                EffectTraceStage.MANA_DEBITED,
                EffectTraceStage.STEP_NOT_APPLIED,
                EffectTraceStage.REFUND_FAILED,
                EffectTraceStage.TERMINAL_COMPENSATION_FAILED);
    }

    private static EffectExecutionResult compensationFailedBeforeStep() {
        return effect(
                EffectTerminalStatus.COMPENSATION_FAILED,
                Optional.empty(),
                Optional.of(EffectFailureReason.COMPENSATION_REFUND_FAILED),
                -1,
                1,
                0,
                0,
                EffectTraceStage.REQUEST_VALIDATED,
                EffectTraceStage.TARGET_RESOLVED,
                EffectTraceStage.MANA_DEBITED,
                EffectTraceStage.REFUND_FAILED,
                EffectTraceStage.TERMINAL_COMPENSATION_FAILED);
    }

    private static EffectExecutionResult effect(
            EffectTerminalStatus status,
            Optional<EffectRejectReason> rejectReason,
            Optional<EffectFailureReason> failureReason,
            int failureStepIndex,
            int planned,
            int executed,
            int mutations,
            EffectTraceStage... stages) {
        return EffectTestFixtures.result(
                status,
                rejectReason,
                failureReason,
                failureStepIndex,
                planned,
                executed,
                mutations,
                stages);
    }

    private static ProvisionalEffectFailure provisional() {
        return new ProvisionalEffectFailure(
                EffectFailureReason.PRIMARY_STEP_NOT_APPLIED, 0);
    }

    private static ManaReceiptSnapshot openDebit() {
        return new ManaReceiptSnapshot(debitIdentity(), ManaRefundState.OPEN);
    }

    private static ManaReceiptSnapshot refundedDebit() {
        return new ManaReceiptSnapshot(debitIdentity(), ManaRefundState.REFUNDED);
    }

    private static ManaReceiptSnapshot refundReceipt() {
        return new ManaReceiptSnapshot(
                new ManaReceiptIdentity(
                        ManaOperationKind.REFUND,
                        ManaReason.COMPENSATION_REFUND,
                        ACCOUNT_ID,
                        10L,
                        90L,
                        100L),
                ManaRefundState.NON_REFUNDABLE);
    }

    private static ManaReceiptIdentity debitIdentity() {
        return new ManaReceiptIdentity(
                ManaOperationKind.DEBIT,
                ManaReason.SKILL_COST,
                ACCOUNT_ID,
                10L,
                100L,
                90L);
    }

    private static void assertInvalid(org.junit.jupiter.api.function.Executable executable) {
        P6ExecutionInvariantException failure = assertThrows(
                P6ExecutionInvariantException.class, executable);
        assertEquals(P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT, failure.code());
    }
}
