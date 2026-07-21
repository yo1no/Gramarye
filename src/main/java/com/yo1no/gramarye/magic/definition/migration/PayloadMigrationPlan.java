package com.yo1no.gramarye.magic.definition.migration;

import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable descriptor-owned plan of unique adjacent payload-schema migration edges. */
public final class PayloadMigrationPlan {
    private static final PayloadMigrationPlan EMPTY = new PayloadMigrationPlan(List.of());

    private final List<PayloadMigrationStep> steps;
    private final Map<Integer, PayloadMigrationStep> stepsByFromVersion;

    public PayloadMigrationPlan(List<? extends PayloadMigrationStep> steps) {
        Objects.requireNonNull(steps, "steps");
        var sorted = new ArrayList<PayloadMigrationStep>(steps.size());
        var byVersion = new HashMap<Integer, PayloadMigrationStep>();
        for (var step : steps) {
            Objects.requireNonNull(step, "step");
            var fromVersion = step.fromVersion();
            if (fromVersion < 0) {
                throw new IllegalArgumentException("fromVersion must be non-negative");
            }
            if (fromVersion == Integer.MAX_VALUE || step.toVersion() != fromVersion + 1) {
                throw new IllegalArgumentException("payload migration steps must connect adjacent versions");
            }
            if (byVersion.putIfAbsent(fromVersion, step) != null) {
                throw new IllegalArgumentException(
                        "duplicate payload migration edge from version " + fromVersion);
            }
            sorted.add(step);
        }
        sorted.sort(java.util.Comparator.comparingInt(PayloadMigrationStep::fromVersion));
        this.steps = List.copyOf(sorted);
        this.stepsByFromVersion = Map.copyOf(byVersion);
        // Unique N -> N+1 edges make cycles structurally impossible.
    }

    public static PayloadMigrationPlan empty() {
        return EMPTY;
    }

    public List<PayloadMigrationStep> steps() {
        return steps;
    }

    public Optional<PayloadMigrationStep> stepFrom(int version) {
        return Optional.ofNullable(stepsByFromVersion.get(version));
    }

    /** Verifies exact continuous coverage of every edge from zero through current version. */
    public DataResult<Void> verifyCoverage(int currentPayloadSchemaVersion) {
        if (currentPayloadSchemaVersion < 0) {
            return DataResult.error(() -> "currentPayloadSchemaVersion must be non-negative");
        }
        for (var version = 0; version < currentPayloadSchemaVersion; version++) {
            var step = stepsByFromVersion.get(version);
            if (step == null || step.toVersion() != version + 1) {
                var missingVersion = version;
                return DataResult.error(
                        () -> "missing payload migration edge from version " + missingVersion);
            }
        }
        if (steps.size() != currentPayloadSchemaVersion) {
            return DataResult.error(
                    () -> "payload migration plan contains an edge outside required coverage");
        }
        return DataResult.success(null);
    }
}
