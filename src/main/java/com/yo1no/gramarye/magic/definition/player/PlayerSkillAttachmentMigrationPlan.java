package com.yo1no.gramarye.magic.definition.player;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable adjacent migration plan for the Attachment outer schema only. */
final class PlayerSkillAttachmentMigrationPlan {
    private final int currentVersion;
    private final Map<Integer, PlayerSkillAttachmentMigrationStep> steps;

    PlayerSkillAttachmentMigrationPlan(Map<Integer, PlayerSkillAttachmentMigrationStep> steps) {
        this(PlayerSkillAttachmentSchema.CURRENT_VERSION, steps);
    }

    PlayerSkillAttachmentMigrationPlan(
            int currentVersion,
            Map<Integer, PlayerSkillAttachmentMigrationStep> steps) {
        if (currentVersion < 0) {
            throw new IllegalArgumentException("currentVersion must be non-negative");
        }
        this.currentVersion = currentVersion;
        Objects.requireNonNull(steps, "steps");
        var sorted = new TreeMap<Integer, PlayerSkillAttachmentMigrationStep>();
        steps.forEach((version, step) -> {
            if (version == null || version < 0 || version >= currentVersion) {
                throw new IllegalArgumentException("migration source version is outside current coverage");
            }
            if (sorted.put(version, Objects.requireNonNull(step, "step")) != null) {
                throw new IllegalArgumentException("duplicate migration source version");
            }
        });
        this.steps = Map.copyOf(sorted);
    }

    PlayerSkillAttachmentMigrationStep stepFrom(int version) {
        return steps.get(version);
    }

    int currentVersion() {
        return currentVersion;
    }
}
