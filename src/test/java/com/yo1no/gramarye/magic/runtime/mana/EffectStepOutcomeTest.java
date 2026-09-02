package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

final class EffectStepOutcomeTest {
    @Test
    void acceptsEachClosedOutcomeShape() {
        assertEquals(EffectStepOutcomeKind.APPLIED, EffectStepOutcome.applied(1).kind());
        assertEquals(EffectStepOutcomeKind.NOT_APPLIED, EffectStepOutcome.notApplied().kind());
        assertEquals(
                EffectStepOutcomeKind.APPLIED_WITH_FAILURE,
                EffectStepOutcome.appliedWithFailure(1).kind());
    }

    @Test
    void appliedRequiresPositiveMutationAndNoFailure() {
        assertThrows(IllegalArgumentException.class, () -> EffectStepOutcome.applied(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectStepOutcome(
                        EffectStepOutcomeKind.APPLIED,
                        1,
                        Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED)));
    }

    @Test
    void notAppliedRequiresZeroMutationAndItsClosedFailure() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectStepOutcome(
                        EffectStepOutcomeKind.NOT_APPLIED,
                        1,
                        Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectStepOutcome(
                        EffectStepOutcomeKind.NOT_APPLIED,
                        0,
                        Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectStepOutcome(
                        EffectStepOutcomeKind.NOT_APPLIED,
                        0,
                        Optional.of(EffectFailureReason.EXECUTION_CANCELLED)));
    }

    @Test
    void appliedWithFailureRequiresPositiveMutationAndItsClosedFailure() {
        assertThrows(IllegalArgumentException.class, () -> EffectStepOutcome.appliedWithFailure(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectStepOutcome(
                        EffectStepOutcomeKind.APPLIED_WITH_FAILURE,
                        1,
                        Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectStepOutcome(
                        EffectStepOutcomeKind.APPLIED_WITH_FAILURE,
                        1,
                        Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED)));
    }

    @Test
    void rejectsNegativeMutationAndNullOutcomeMembers() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectStepOutcome(
                        EffectStepOutcomeKind.NOT_APPLIED,
                        -1,
                        Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED)));
        assertThrows(
                NullPointerException.class,
                () -> new EffectStepOutcome(null, 0, Optional.empty()));
        assertThrows(
                NullPointerException.class,
                () -> new EffectStepOutcome(EffectStepOutcomeKind.APPLIED, 1, null));
    }
}
