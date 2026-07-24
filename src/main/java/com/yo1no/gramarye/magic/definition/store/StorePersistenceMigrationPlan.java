package com.yo1no.gramarye.magic.definition.store;

import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, deterministic adjacent-edge plan for Store physical schemas. */
final class StorePersistenceMigrationPlan {
    private static final StorePersistenceMigrationPlan EMPTY =
            new StorePersistenceMigrationPlan(List.of());

    private final List<StorePersistenceMigrationStep> steps;
    private final Map<Integer, StorePersistenceMigrationStep> stepsByVersion;

    StorePersistenceMigrationPlan(List<? extends StorePersistenceMigrationStep> steps) {
        Objects.requireNonNull(steps, "steps");
        var ordered = new ArrayList<StorePersistenceMigrationStep>(steps.size());
        var byVersion = new HashMap<Integer, StorePersistenceMigrationStep>();
        for (var step : steps) {
            Objects.requireNonNull(step, "step");
            var from = step.fromVersion();
            if (from < 0 || from == Integer.MAX_VALUE || step.toVersion() != from + 1) {
                throw new IllegalArgumentException("Store migrations must be non-negative adjacent edges");
            }
            if (byVersion.putIfAbsent(from, step) != null) {
                throw new IllegalArgumentException("duplicate Store migration edge");
            }
            ordered.add(step);
        }
        ordered.sort(java.util.Comparator.comparingInt(StorePersistenceMigrationStep::fromVersion));
        this.steps = List.copyOf(ordered);
        this.stepsByVersion = Map.copyOf(byVersion);
    }

    static StorePersistenceMigrationPlan empty() {
        return EMPTY;
    }

    List<StorePersistenceMigrationStep> steps() {
        return steps;
    }

    Optional<StorePersistenceMigrationStep> stepFrom(int version) {
        return Optional.ofNullable(stepsByVersion.get(version));
    }

    DataResult<StorePersistenceMigrationPlan> verifyCoverage(int currentVersion) {
        if (currentVersion < 0) {
            return DataResult.error(() -> "current Store schema must be non-negative");
        }
        for (var version = 0; version < currentVersion; version++) {
            var step = stepsByVersion.get(version);
            if (step == null || step.toVersion() != version + 1) {
                var missing = version;
                return DataResult.error(() -> "missing Store migration edge from " + missing);
            }
        }
        return steps.size() == currentVersion
                ? DataResult.success(this)
                : DataResult.error(() -> "Store migration plan has an edge outside coverage");
    }
}

final class StorePersistenceMigrationPlans {
    private static final StorePersistenceMigrationPlan PRODUCTION =
            StorePersistenceMigrationPlan.empty();

    private StorePersistenceMigrationPlans() {
    }

    static StorePersistenceMigrationPlan production() {
        return PRODUCTION;
    }
}
