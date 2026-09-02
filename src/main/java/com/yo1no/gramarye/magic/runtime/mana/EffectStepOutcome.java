package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import java.util.Optional;

enum EffectStepOutcomeKind {
    APPLIED,
    NOT_APPLIED,
    APPLIED_WITH_FAILURE
}

record EffectStepOutcome(
        EffectStepOutcomeKind kind,
        int actualPrimaryMutationCount,
        Optional<EffectFailureReason> failureReason) {
    EffectStepOutcome {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(failureReason, "failureReason");
        if (actualPrimaryMutationCount < 0) {
            throw new IllegalArgumentException("actual primary mutation count must not be negative");
        }
        switch (kind) {
            case APPLIED -> {
                if (actualPrimaryMutationCount == 0 || failureReason.isPresent()) {
                    throw new IllegalArgumentException("APPLIED requires mutation and no failure");
                }
            }
            case NOT_APPLIED -> {
                if (actualPrimaryMutationCount != 0
                        || !failureReason.equals(Optional.of(
                                EffectFailureReason.PRIMARY_STEP_NOT_APPLIED))) {
                    throw new IllegalArgumentException(
                            "NOT_APPLIED requires zero mutation and its closed failure");
                }
            }
            case APPLIED_WITH_FAILURE -> {
                if (actualPrimaryMutationCount == 0
                        || !failureReason.equals(Optional.of(
                                EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE))) {
                    throw new IllegalArgumentException(
                            "APPLIED_WITH_FAILURE requires mutation and its closed failure");
                }
            }
        }
    }

    static EffectStepOutcome applied(int actualPrimaryMutationCount) {
        return new EffectStepOutcome(
                EffectStepOutcomeKind.APPLIED,
                actualPrimaryMutationCount,
                Optional.empty());
    }

    static EffectStepOutcome notApplied() {
        return new EffectStepOutcome(
                EffectStepOutcomeKind.NOT_APPLIED,
                0,
                Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED));
    }

    static EffectStepOutcome appliedWithFailure(int actualPrimaryMutationCount) {
        return new EffectStepOutcome(
                EffectStepOutcomeKind.APPLIED_WITH_FAILURE,
                actualPrimaryMutationCount,
                Optional.of(EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE));
    }
}
