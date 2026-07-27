package com.yo1no.gramarye.magic.definition.document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;

/** Independent adjacent migration axis for SkillDraft.draftSchemaVersion. */
final class SkillDraftLogicalMigration {
    private SkillDraftLogicalMigration() {
    }

    static Result migrate(CompoundTag tokenizedDraft) {
        return migrate(tokenizedDraft, Plans.production());
    }

    static Result migrate(CompoundTag tokenizedDraft, Plan plan) {
        return migrate(tokenizedDraft, plan, SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION);
    }

    static Result migrate(
            CompoundTag tokenizedDraft,
            Plan plan,
            int currentVersion) {
        Objects.requireNonNull(tokenizedDraft, "tokenizedDraft");
        Objects.requireNonNull(plan, "plan");
        if (currentVersion < 0) {
            throw new IllegalArgumentException("currentVersion must be non-negative");
        }
        var current = tokenizedDraft.copy();
        var migrated = false;
        try {
            while (true) {
                if (!(current.get("draft_schema_version") instanceof IntTag versionTag)
                        || versionTag.getAsInt() < 0) {
                    return new Failure();
                }
                var version = versionTag.getAsInt();
                if (version == currentVersion) {
                    return new Success(current.copy(), migrated);
                }
                if (version > currentVersion) {
                    return new Failure();
                }
                var step = plan.stepFrom(version);
                if (step == null) {
                    return new Failure();
                }
                var migratedTree = Objects.requireNonNull(
                        step.migrate(current.copy()), "migration result");
                if (!(migratedTree.get("draft_schema_version") instanceof IntTag next)
                        || next.getAsInt() != Math.addExact(version, 1)) {
                    return new Failure();
                }
                current = migratedTree.copy();
                migrated = true;
            }
        } catch (RuntimeException exception) {
            return new ExceptionFailure(exception.getClass().getName());
        }
    }

    sealed interface Result permits Success, Failure, ExceptionFailure {
    }

    record Success(CompoundTag draft, boolean migrated) implements Result {
        Success {
            draft = Objects.requireNonNull(draft, "draft").copy();
        }

        @Override
        public CompoundTag draft() {
            return draft.copy();
        }
    }

    record Failure() implements Result {
    }

    record ExceptionFailure(String exceptionClassName) implements Result {
        ExceptionFailure {
            Objects.requireNonNull(exceptionClassName, "exceptionClassName");
        }
    }

    record Plan(Map<Integer, Step> steps) {
    Plan {
        Objects.requireNonNull(steps, "steps");
        var copied = new HashMap<Integer, Step>();
        for (var entry : steps.entrySet()) {
            var source = Objects.requireNonNull(entry.getKey(), "sourceVersion");
            var step = Objects.requireNonNull(entry.getValue(), "step");
            if (source < 0
                    || source != step.sourceVersion()
                    || step.targetVersion() != Math.addExact(source, 1)
                    || copied.put(source, step) != null) {
                throw new IllegalArgumentException("invalid adjacent Draft logical migration edge");
            }
        }
        steps = Map.copyOf(copied);
    }

    static Plan of(List<Step> steps) {
        Objects.requireNonNull(steps, "steps");
        var mapped = new HashMap<Integer, Step>();
        for (var step : steps) {
            Objects.requireNonNull(step, "step");
            if (mapped.put(step.sourceVersion(), step) != null) {
                throw new IllegalArgumentException("duplicate Draft logical migration edge");
            }
        }
        return new Plan(mapped);
    }

    Step stepFrom(int version) {
        return steps.get(version);
    }
}

@FunctionalInterface
    interface TreeMigration {
    CompoundTag migrate(CompoundTag source);
}

    record Step(
        int sourceVersion,
        int targetVersion,
        TreeMigration migration) {
    Step {
        Objects.requireNonNull(migration, "migration");
    }

    CompoundTag migrate(CompoundTag source) {
        return migration.migrate(Objects.requireNonNull(source, "source").copy());
    }
}

    static final class Plans {
    private static final Plan PRODUCTION = Plan.of(List.of());

    private Plans() {
    }

    static Plan production() {
        return PRODUCTION;
    }
}
}
