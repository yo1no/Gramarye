package com.yo1no.gramarye.magic.runtime.mana;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class EffectTestFixtures {
    private static final UUID TARGET_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000123");

    private EffectTestFixtures() {}

    static DamageEffectRequest request() {
        return request(25L, 0L);
    }

    static DamageEffectRequest request(long magnitude, long manaCost) {
        return new DamageEffectRequest(
                new EffectRequestId(11L),
                new SourceEventId(29L),
                new DamageTargetReference(TARGET_ID),
                magnitude,
                manaCost,
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION);
    }

    static DamageEffectStep step(int index) {
        return step(index, 1, 0);
    }

    static DamageEffectStep step(
            int index, int declaredPrimaryMutations, int declaredChildIntents) {
        return new DamageEffectStep(
                index,
                request().target(),
                request().magnitude(),
                declaredPrimaryMutations,
                declaredChildIntents);
    }

    static EffectCommitPlan plan(int stepCount) {
        return plan(stepCount, 0);
    }

    static EffectCommitPlan plan(int stepCount, int suppliedChildCapacity) {
        List<EffectStep> steps = new ArrayList<>();
        for (int index = 0; index < stepCount; index++) {
            steps.add(step(index));
        }
        return new EffectCommitPlan(steps, suppliedChildCapacity);
    }

    static EffectResolver resolverFor(EffectCommitPlan plan) {
        return (request, suppliedChildCapacity) -> new AcceptedEffectResolution(plan);
    }

    static EffectTrace trace(EffectTraceStage... stages) {
        List<EffectTraceEntry> entries = new ArrayList<>();
        int stepIndex = 0;
        for (EffectTraceStage stage : stages) {
            entries.add(stage.requiresStepIndex()
                    ? EffectTraceEntry.forStep(entries.size(), stage, stepIndex++)
                    : EffectTraceEntry.withoutStep(entries.size(), stage));
        }
        return new EffectTrace(entries);
    }

    static EffectExecutionResult result(
            EffectTerminalStatus status,
            Optional<EffectRejectReason> rejectReason,
            Optional<EffectFailureReason> failureReason,
            int failureStepIndex,
            int planned,
            int executed,
            int mutations,
            EffectTraceStage... stages) {
        return new EffectExecutionResult(
                status,
                rejectReason,
                failureReason,
                failureStepIndex,
                planned,
                executed,
                mutations,
                trace(stages));
    }
}
