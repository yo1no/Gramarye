package com.yo1no.gramarye.magic.runtime.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class P6EffectVocabularyTest {
    @Test
    void boundsMatchP6A1Exactly() {
        assertArrayEquals(
                new long[] {1, 1, 8, 8, 2, 32, 1_000_000L, 1_000_000_000L,
                        1_000_000_000L, 10, 32},
                new long[] {
                    P6EffectBounds.MAX_EFFECT_REQUESTS_PER_EXECUTION,
                    P6EffectBounds.MAX_TARGETS_PER_REQUEST,
                    P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN,
                    P6EffectBounds.MAX_PRIMARY_WORLD_MUTATIONS_PER_EXECUTION,
                    P6EffectBounds.MAX_MANA_MUTATIONS_PER_EXECUTION,
                    P6EffectBounds.MAX_TRACE_ENTRIES,
                    P6EffectBounds.MAX_EFFECT_MAGNITUDE,
                    P6EffectBounds.MAX_MANA_VALUE,
                    P6EffectBounds.MAX_MANA_OPERATION_AMOUNT,
                    P6EffectBounds.MAX_DEADLINE_CHECKS_PER_EXECUTION,
                    P6EffectBounds.MAX_CHILD_INTENTS_PER_EXECUTION
                });
    }

    @Test
    void closedVocabulariesHaveExactValues() {
        assertArrayEquals(
                new EffectTerminalStatus[] {
                    EffectTerminalStatus.SUCCEEDED,
                    EffectTerminalStatus.REJECTED,
                    EffectTerminalStatus.FAILED,
                    EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                    EffectTerminalStatus.COMPENSATED,
                    EffectTerminalStatus.COMPENSATION_FAILED
                },
                EffectTerminalStatus.values());
        assertArrayEquals(
                new EffectStepOutcomeKind[] {
                    EffectStepOutcomeKind.APPLIED,
                    EffectStepOutcomeKind.NOT_APPLIED,
                    EffectStepOutcomeKind.APPLIED_WITH_FAILURE
                },
                EffectStepOutcomeKind.values());
        assertArrayEquals(
                new EffectGuardDecision[] {
                    EffectGuardDecision.ALLOWED,
                    EffectGuardDecision.CANCELLED,
                    EffectGuardDecision.DEADLINE_EXCEEDED
                },
                EffectGuardDecision.values());
        assertEquals(1, CompensationPolicy.values().length);
        assertEquals(
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION,
                CompensationPolicy.values()[0]);
    }

    @Test
    void rejectFailureResolutionAndTraceVocabulariesAreExact() {
        assertArrayEquals(
                new EffectRejectReason[] {
                    EffectRejectReason.UNSUPPORTED_ACTION,
                    EffectRejectReason.INVALID_REQUEST,
                    EffectRejectReason.INVALID_TARGET,
                    EffectRejectReason.TARGET_UNAVAILABLE,
                    EffectRejectReason.INSUFFICIENT_MANA,
                    EffectRejectReason.MANA_STATE_UNAVAILABLE,
                    EffectRejectReason.BOUND_EXCEEDED,
                    EffectRejectReason.CANCELLED,
                    EffectRejectReason.DEADLINE_EXCEEDED,
                    EffectRejectReason.COMMIT_PORT_UNAVAILABLE
                },
                EffectRejectReason.values());
        assertArrayEquals(
                new EffectFailureReason[] {
                    EffectFailureReason.PRIMARY_STEP_NOT_APPLIED,
                    EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE,
                    EffectFailureReason.EXECUTION_CANCELLED,
                    EffectFailureReason.EXECUTION_DEADLINE_EXCEEDED,
                    EffectFailureReason.COMPENSATION_REFUND_FAILED
                },
                EffectFailureReason.values());
        assertArrayEquals(
                new EffectResolutionKind[] {
                    EffectResolutionKind.ACCEPTED,
                    EffectResolutionKind.REJECTED
                },
                EffectResolutionKind.values());
        assertArrayEquals(
                new EffectTraceStage[] {
                    EffectTraceStage.REQUEST_VALIDATED,
                    EffectTraceStage.TARGET_RESOLVED,
                    EffectTraceStage.MANA_DEBITED,
                    EffectTraceStage.STEP_APPLIED,
                    EffectTraceStage.STEP_NOT_APPLIED,
                    EffectTraceStage.STEP_APPLIED_WITH_FAILURE,
                    EffectTraceStage.REFUND_APPLIED,
                    EffectTraceStage.REFUND_FAILED,
                    EffectTraceStage.TERMINAL_REJECTED,
                    EffectTraceStage.TERMINAL_SUCCEEDED,
                    EffectTraceStage.TERMINAL_FAILED,
                    EffectTraceStage.TERMINAL_PARTIAL,
                    EffectTraceStage.TERMINAL_COMPENSATED,
                    EffectTraceStage.TERMINAL_COMPENSATION_FAILED
                },
                EffectTraceStage.values());
    }
}
