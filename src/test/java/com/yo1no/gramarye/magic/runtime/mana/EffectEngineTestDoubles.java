package com.yo1no.gramarye.magic.runtime.mana;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

abstract class DamageOnlyEffectCommitPort implements EffectCommitPort {
    @Override
    public final EffectStepOutcome commitSpawn(
            SpawnProjectileRequest request, SpawnProjectileStep step) {
        throw new AssertionError("spawn commit was not expected by this damage fixture");
    }
}

final class RecordingEffectGuard implements EffectExecutionGuard {
    private final Function<EffectGuardPoint, EffectGuardDecision> decisions;
    private final List<EffectGuardPoint> checks = new ArrayList<>();

    RecordingEffectGuard(Function<EffectGuardPoint, EffectGuardDecision> decisions) {
        this.decisions = Objects.requireNonNull(decisions, "decisions");
    }

    static RecordingEffectGuard allowing() {
        return new RecordingEffectGuard(point -> EffectGuardDecision.ALLOWED);
    }

    @Override
    public EffectGuardDecision check(EffectGuardPoint point) {
        checks.add(point);
        return decisions.apply(point);
    }

    List<EffectGuardPoint> checks() {
        return List.copyOf(checks);
    }
}

final class RecordingDamageCommitPort extends DamageOnlyEffectCommitPort {
    private final boolean available;
    private final List<EffectStepOutcome> outcomes;
    private final List<Integer> committedIndexes = new ArrayList<>();
    private int availabilityChecks;

    RecordingDamageCommitPort(boolean available, List<EffectStepOutcome> outcomes) {
        this.available = available;
        this.outcomes = List.copyOf(outcomes);
    }

    static RecordingDamageCommitPort applyingAll() {
        return new RecordingDamageCommitPort(true, List.of());
    }

    @Override
    public boolean isAvailable() {
        availabilityChecks++;
        return available;
    }

    @Override
    public EffectStepOutcome commitDamage(
                        DamageEffectRequest request, DamageEffectStep step) {
        int invocation = committedIndexes.size();
        committedIndexes.add(step.index());
        return invocation < outcomes.size()
                ? outcomes.get(invocation)
                : EffectStepOutcome.applied(1);
    }

    List<Integer> committedIndexes() {
        return List.copyOf(committedIndexes);
    }

    int availabilityChecks() {
        return availabilityChecks;
    }
}
