package com.yo1no.gramarye.magic.runtime.mana;

import java.util.List;
import java.util.Objects;

enum EffectTraceStage {
    REQUEST_VALIDATED,
    TARGET_RESOLVED,
    MANA_DEBITED,
    STEP_APPLIED,
    STEP_NOT_APPLIED,
    STEP_APPLIED_WITH_FAILURE,
    REFUND_APPLIED,
    REFUND_FAILED,
    TERMINAL_REJECTED,
    TERMINAL_SUCCEEDED,
    TERMINAL_FAILED,
    TERMINAL_PARTIAL,
    TERMINAL_COMPENSATED,
    TERMINAL_COMPENSATION_FAILED;

    boolean requiresStepIndex() {
        return this == STEP_APPLIED
                || this == STEP_NOT_APPLIED
                || this == STEP_APPLIED_WITH_FAILURE;
    }

    boolean isTerminal() {
        return this == TERMINAL_REJECTED
                || this == TERMINAL_SUCCEEDED
                || this == TERMINAL_FAILED
                || this == TERMINAL_PARTIAL
                || this == TERMINAL_COMPENSATED
                || this == TERMINAL_COMPENSATION_FAILED;
    }
}

/** Trace and step coordinates are contiguous and zero-based throughout P6 V0. */
record EffectTraceEntry(int sequence, EffectTraceStage stage, int stepIndex) {
    static final int NOT_APPLICABLE_STEP_INDEX = -1;

    EffectTraceEntry {
        Objects.requireNonNull(stage, "stage");
        if (sequence < 0) {
            throw new IllegalArgumentException("trace sequence must be non-negative");
        }
        if (stage.requiresStepIndex()) {
            if (stepIndex < 0 || stepIndex >= P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN) {
                throw new IllegalArgumentException("step trace requires a valid zero-based index");
            }
        } else if (stepIndex != NOT_APPLICABLE_STEP_INDEX) {
            throw new IllegalArgumentException("non-step trace requires the absence sentinel");
        }
    }

    static EffectTraceEntry withoutStep(int sequence, EffectTraceStage stage) {
        return new EffectTraceEntry(sequence, stage, NOT_APPLICABLE_STEP_INDEX);
    }

    static EffectTraceEntry forStep(int sequence, EffectTraceStage stage, int stepIndex) {
        return new EffectTraceEntry(sequence, stage, stepIndex);
    }
}

record EffectTrace(List<EffectTraceEntry> entries) {
    EffectTrace {
        Objects.requireNonNull(entries, "entries");
        List<EffectTraceEntry> copied = List.copyOf(entries);
        if (copied.isEmpty() || copied.size() > P6EffectBounds.MAX_TRACE_ENTRIES) {
            throw new IllegalArgumentException("trace must contain one through 32 entries");
        }
        for (int index = 0; index < copied.size(); index++) {
            if (copied.get(index).sequence() != index) {
                throw new IllegalArgumentException(
                        "trace uses contiguous zero-based sequence numbers");
            }
        }
        entries = copied;
    }

    EffectTraceStage terminalStage() {
        return entries.getLast().stage();
    }
}
