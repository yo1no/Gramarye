package com.yo1no.gramarye.magic.definition.store;

import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, exact-coverage adjacent migration plan for the SavedData outer carrier. */
final class SkillSavedDataCarrierMigrationPlan {
    private static final SkillSavedDataCarrierMigrationPlan EMPTY =
            new SkillSavedDataCarrierMigrationPlan(List.of());

    private final List<SkillSavedDataCarrierMigrationStep> steps;
    private final Map<Integer, SkillSavedDataCarrierMigrationStep> stepsByVersion;

    SkillSavedDataCarrierMigrationPlan(
            List<? extends SkillSavedDataCarrierMigrationStep> steps) {
        Objects.requireNonNull(steps, "steps");
        var ordered = new ArrayList<SkillSavedDataCarrierMigrationStep>(steps.size());
        var byVersion = new HashMap<Integer, SkillSavedDataCarrierMigrationStep>();
        for (var step : steps) {
            Objects.requireNonNull(step, "step");
            var from = step.fromVersion();
            if (from < 0 || from == Integer.MAX_VALUE || step.toVersion() != from + 1) {
                throw new IllegalArgumentException(
                        "SavedData carrier migrations must be non-negative adjacent edges");
            }
            if (byVersion.putIfAbsent(from, step) != null) {
                throw new IllegalArgumentException("duplicate SavedData carrier migration edge");
            }
            ordered.add(step);
        }
        ordered.sort(java.util.Comparator.comparingInt(
                SkillSavedDataCarrierMigrationStep::fromVersion));
        this.steps = List.copyOf(ordered);
        this.stepsByVersion = Map.copyOf(byVersion);
    }

    static SkillSavedDataCarrierMigrationPlan empty() {
        return EMPTY;
    }

    List<SkillSavedDataCarrierMigrationStep> steps() {
        return steps;
    }

    Optional<SkillSavedDataCarrierMigrationStep> stepFrom(int version) {
        return Optional.ofNullable(stepsByVersion.get(version));
    }

    DataResult<SkillSavedDataCarrierMigrationPlan> verifyCoverage(int currentVersion) {
        if (currentVersion < 0) {
            return DataResult.error(() -> "current SavedData schema must be non-negative");
        }
        for (var version = 0; version < currentVersion; version++) {
            var step = stepsByVersion.get(version);
            if (step == null || step.toVersion() != version + 1) {
                var missing = version;
                return DataResult.error(
                        () -> "missing SavedData carrier migration edge from " + missing);
            }
        }
        return steps.size() == currentVersion
                ? DataResult.success(this)
                : DataResult.error(
                        () -> "SavedData carrier migration plan has an edge outside coverage");
    }
}
