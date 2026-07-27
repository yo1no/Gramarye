package com.yo1no.gramarye.magic.definition.document;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Independent migration axis for the Draft byte encoding discriminator. */
final class DraftPersistenceMigration {
    private DraftPersistenceMigration() {
    }

    static Result migrate(SkillDraftPersistenceFacade.EncodedSkillDraft source) {
        return migrate(source, Plans.production());
    }

    static Result migrate(
            SkillDraftPersistenceFacade.EncodedSkillDraft source,
            Plan plan) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(plan, "plan");
        var current = source;
        var visited = new HashSet<String>();
        var migrated = false;
        try {
            while (!current.draftEncoding().equals(
                    SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING)) {
                if (!visited.add(current.draftEncoding())) {
                    return new Failure();
                }
                var step = plan.stepFrom(current.draftEncoding());
                if (step == null) {
                    return new Failure();
                }
                var bytes = Objects.requireNonNull(
                        step.migrate(current.copyBytes()), "migration result");
                var captured = SkillDraftPersistenceFacade.EncodedSkillDraft.capturePersisted(
                        step.targetEncoding(), bytes.length, () -> bytes);
                if (!(captured instanceof SkillDraftPersistenceFacade.Captured accepted)) {
                    return new Failure();
                }
                current = accepted.draft();
                migrated = true;
            }
            return new Success(current, migrated);
        } catch (RuntimeException exception) {
            return new ExceptionFailure(exception.getClass().getName());
        }
    }

    sealed interface Result permits Success, Failure, ExceptionFailure {
    }

    record Success(
            SkillDraftPersistenceFacade.EncodedSkillDraft draft,
            boolean migrated) implements Result {
        Success {
            Objects.requireNonNull(draft, "draft");
        }
    }

    record Failure() implements Result {
    }

    record ExceptionFailure(String exceptionClassName) implements Result {
        ExceptionFailure {
            Objects.requireNonNull(exceptionClassName, "exceptionClassName");
        }
    }

    record Plan(Map<String, Step> steps) {
    Plan {
        Objects.requireNonNull(steps, "steps");
        var copied = new HashMap<String, Step>();
        for (var entry : steps.entrySet()) {
            var source = Objects.requireNonNull(entry.getKey(), "sourceEncoding");
            var step = Objects.requireNonNull(entry.getValue(), "step");
            if (source.isEmpty()
                    || !source.equals(step.sourceEncoding())
                    || step.targetEncoding().isEmpty()
                    || source.equals(step.targetEncoding())
                    || copied.put(source, step) != null) {
                throw new IllegalArgumentException("invalid Draft physical migration edge");
            }
        }
        steps = Map.copyOf(copied);
    }

    static Plan of(List<Step> steps) {
        Objects.requireNonNull(steps, "steps");
        var mapped = new HashMap<String, Step>();
        for (var step : steps) {
            Objects.requireNonNull(step, "step");
            if (mapped.put(step.sourceEncoding(), step) != null) {
                throw new IllegalArgumentException("duplicate Draft physical migration edge");
            }
        }
        return new Plan(mapped);
    }

    Step stepFrom(String encoding) {
        return steps.get(encoding);
    }
}

@FunctionalInterface
    interface ByteMigration {
    byte[] migrate(byte[] source);
}

    record Step(
        String sourceEncoding,
        String targetEncoding,
        ByteMigration migration) {
    Step {
        Objects.requireNonNull(sourceEncoding, "sourceEncoding");
        Objects.requireNonNull(targetEncoding, "targetEncoding");
        Objects.requireNonNull(migration, "migration");
    }

    byte[] migrate(byte[] source) {
        return migration.migrate(Objects.requireNonNull(source, "source").clone());
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
