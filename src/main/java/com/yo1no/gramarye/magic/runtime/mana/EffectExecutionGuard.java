package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;

enum EffectGuardDecision {
    ALLOWED,
    CANCELLED,
    DEADLINE_EXCEEDED
}

enum EffectGuardPointKind {
    ENTRY,
    PRE_COMMIT,
    BEFORE_STEP
}

record EffectGuardPoint(EffectGuardPointKind kind, int stepIndex) {
    static final int NOT_APPLICABLE_STEP_INDEX = -1;

    EffectGuardPoint {
        Objects.requireNonNull(kind, "kind");
        if (kind == EffectGuardPointKind.BEFORE_STEP) {
            if (stepIndex < 0 || stepIndex >= P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN) {
                throw new IllegalArgumentException("BEFORE_STEP requires a valid step index");
            }
        } else if (stepIndex != NOT_APPLICABLE_STEP_INDEX) {
            throw new IllegalArgumentException("non-step guard point requires typed absence");
        }
    }

    static EffectGuardPoint entry() {
        return new EffectGuardPoint(
                EffectGuardPointKind.ENTRY, NOT_APPLICABLE_STEP_INDEX);
    }

    static EffectGuardPoint preCommit() {
        return new EffectGuardPoint(
                EffectGuardPointKind.PRE_COMMIT, NOT_APPLICABLE_STEP_INDEX);
    }

    static EffectGuardPoint beforeStep(int stepIndex) {
        return new EffectGuardPoint(EffectGuardPointKind.BEFORE_STEP, stepIndex);
    }
}

interface EffectExecutionGuard {
    EffectGuardDecision check(EffectGuardPoint point);
}
