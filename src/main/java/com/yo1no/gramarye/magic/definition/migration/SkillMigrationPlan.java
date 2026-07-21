package com.yo1no.gramarye.magic.definition.migration;

import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable adjacent-edge plan for skill-level schema migration. */
public final class SkillMigrationPlan {
    private static final SkillMigrationPlan EMPTY = new SkillMigrationPlan(List.of());

    private final List<SkillMigrationStep> steps;
    private final Map<Integer, SkillMigrationStep> stepsByFromVersion;

    public SkillMigrationPlan(List<? extends SkillMigrationStep> steps) {
        Objects.requireNonNull(steps, "steps");
        var sorted = new ArrayList<SkillMigrationStep>(steps.size());
        var byVersion = new HashMap<Integer, SkillMigrationStep>();
        for (var step : steps) {
            Objects.requireNonNull(step, "step");
            var fromVersion = step.fromVersion();
            var toVersion = step.toVersion();
            if (fromVersion < 0) {
                throw new IllegalArgumentException("fromVersion must be non-negative");
            }
            if (fromVersion == Integer.MAX_VALUE || toVersion != fromVersion + 1) {
                throw new IllegalArgumentException("migration steps must connect adjacent versions");
            }
            if (byVersion.putIfAbsent(fromVersion, step) != null) {
                throw new IllegalArgumentException("duplicate migration edge from version " + fromVersion);
            }
            sorted.add(step);
        }
        sorted.sort(java.util.Comparator.comparingInt(SkillMigrationStep::fromVersion));
        this.steps = List.copyOf(sorted);
        this.stepsByFromVersion = Map.copyOf(byVersion);
        // With unique from-versions and mandatory N -> N+1 edges, a cycle is structurally impossible.
    }

    public static SkillMigrationPlan empty() {
        return EMPTY;
    }

    public List<SkillMigrationStep> steps() {
        return steps;
    }

    public Optional<SkillMigrationStep> stepFrom(int version) {
        return Optional.ofNullable(stepsByFromVersion.get(version));
    }

    /** Verifies that the plan contains exactly every edge 0 -> 1 through current-1 -> current. */
    public DataResult<SkillMigrationPlan> verifyCoverage(int currentSchemaVersion) {
        if (currentSchemaVersion < 0) {
            return DataResult.error(() -> "currentSchemaVersion must be non-negative");
        }
        for (var version = 0; version < currentSchemaVersion; version++) {
            var step = stepsByFromVersion.get(version);
            if (step == null || step.toVersion() != version + 1) {
                var missingVersion = version;
                return DataResult.error(() -> "missing migration edge from version " + missingVersion);
            }
        }
        if (steps.size() != currentSchemaVersion) {
            return DataResult.error(() -> "migration plan contains an edge outside the required coverage");
        }
        return DataResult.success(this);
    }
}
