package com.yo1no.gramarye.magic.definition.migration;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import java.util.Objects;
import java.util.OptionalInt;

/** Orchestrates bounded, adjacent skill-level migrations without parsing the SkillDocument. */
public final class SkillDocumentMigrator {
    private SkillDocumentMigrator() {
    }

    public static SkillMigrationResult migrate(
            RawSkillDocumentSnapshot originalSnapshot,
            SkillMigrationPlan plan) {
        return migrateTo(originalSnapshot, plan, SkillDocument.CURRENT_SCHEMA_VERSION);
    }

    static SkillMigrationResult migrateTo(
            RawSkillDocumentSnapshot originalSnapshot,
            SkillMigrationPlan plan,
            int currentSchemaVersion) {
        Objects.requireNonNull(originalSnapshot, "originalSnapshot");
        Objects.requireNonNull(plan, "plan");
        if (currentSchemaVersion < 0) {
            throw new IllegalArgumentException("currentSchemaVersion must be non-negative");
        }

        var facts = new PipelineFactCollector();
        var initialProbe = SkillSchemaVersionProbe.probe(originalSnapshot);
        if (initialProbe instanceof SkillSchemaVersionProbe.Result.Failure failure) {
            return failure(originalSnapshot, failure.failure(), facts);
        }
        var version = ((SkillSchemaVersionProbe.Result.Success) initialProbe).schemaVersion();
        if (version > currentSchemaVersion) {
            return failure(
                    originalSnapshot,
                    SkillMigrationFailure.forObserved(
                            SkillMigrationFailure.Code.FUTURE_SCHEMA_VERSION,
                            version),
                    facts);
        }

        var currentSnapshot = originalSnapshot;
        var stepIndex = 0;
        while (version < currentSchemaVersion) {
            var step = plan.stepFrom(version);
            if (step.isEmpty()) {
                return failure(
                        originalSnapshot,
                        SkillMigrationFailure.forStep(
                                SkillMigrationFailure.Code.MISSING_MIGRATION_EDGE,
                                version,
                                version + 1,
                                stepIndex),
                        facts);
            }

            var selectedStep = step.orElseThrow();
            var defensiveInput = currentSnapshot.copyRawDocument();
            StepAttempt attempt;
            try {
                attempt = applyStep(selectedStep, defensiveInput);
            } catch (RuntimeException exception) {
                return failure(
                        originalSnapshot,
                        SkillMigrationFailure.forStepException(
                                version, version + 1, stepIndex, exception),
                        facts);
            }
            if (attempt instanceof StepAttempt.Failure failed) {
                return failure(
                        originalSnapshot,
                        SkillMigrationFailure.forStep(
                                failed.code(), version, version + 1, stepIndex),
                        facts);
            }

            var output = ((StepAttempt.Success) attempt).output();
            var migratedTree = output.migratedTree();
            if (migratedTree.getOps() != defensiveInput.getOps()) {
                return failure(
                        originalSnapshot,
                        SkillMigrationFailure.forStep(
                                SkillMigrationFailure.Code.STEP_CHANGED_DYNAMIC_OPS,
                                version,
                                version + 1,
                                stepIndex),
                        facts);
            }

            var captured = RawSkillDocumentSnapshot.capture(migratedTree);
            if (captured instanceof RawSkillDocumentSnapshot.CaptureResult.Failure failed) {
                return failure(originalSnapshot, withStepMetadata(failed.failure(), version, stepIndex), facts);
            }
            var migratedSnapshot = ((RawSkillDocumentSnapshot.CaptureResult.Success) captured).snapshot();
            var outputProbe = SkillSchemaVersionProbe.probe(migratedSnapshot);
            if (outputProbe instanceof SkillSchemaVersionProbe.Result.Failure) {
                return failure(
                        originalSnapshot,
                        SkillMigrationFailure.forStep(
                                SkillMigrationFailure.Code.STEP_OUTPUT_VERSION_MISMATCH,
                                version,
                                version + 1,
                                stepIndex),
                        facts);
            }
            var outputVersion = ((SkillSchemaVersionProbe.Result.Success) outputProbe).schemaVersion();
            if (outputVersion != version + 1) {
                return failure(
                        originalSnapshot,
                        SkillMigrationFailure.forStepVersion(
                                SkillMigrationFailure.Code.STEP_OUTPUT_VERSION_MISMATCH,
                                version,
                                version + 1,
                                stepIndex,
                                outputVersion),
                        facts);
            }

            facts.add(new SkillMigrationFact(
                    SkillMigrationFactCode.STEP_APPLIED,
                    version,
                    version + 1,
                    OptionalInt.of(stepIndex)));
            currentSnapshot = migratedSnapshot;
            version++;
            stepIndex++;
        }
        return new SkillMigrationResult.Success(currentSnapshot, facts.report());
    }

    private static StepAttempt applyStep(SkillMigrationStep step, Dynamic<?> input) {
        DataResult<SkillMigrationStepOutput> result = step.migrate(input);
        if (result == null) {
            return new StepAttempt.Failure(SkillMigrationFailure.Code.STEP_FAILED);
        }
        if (result.error().isPresent()) {
            return result.resultOrPartial().isPresent()
                    ? new StepAttempt.Failure(SkillMigrationFailure.Code.STEP_RETURNED_PARTIAL)
                    : new StepAttempt.Failure(SkillMigrationFailure.Code.STEP_FAILED);
        }
        var output = result.result();
        return output.isPresent()
                ? new StepAttempt.Success(output.orElseThrow())
                : new StepAttempt.Failure(SkillMigrationFailure.Code.STEP_FAILED);
    }

    private static SkillMigrationFailure withStepMetadata(
            SkillMigrationFailure captureFailure,
            int fromVersion,
            int stepIndex) {
        return new SkillMigrationFailure(
                captureFailure.code(),
                OptionalInt.of(fromVersion),
                OptionalInt.of(fromVersion + 1),
                captureFailure.observedVersion(),
                OptionalInt.of(stepIndex),
                captureFailure.exceptionClassName());
    }

    private static SkillMigrationResult.Failure failure(
            RawSkillDocumentSnapshot original,
            SkillMigrationFailure failure,
            PipelineFactCollector facts) {
        return new SkillMigrationResult.Failure(original, failure, facts.report());
    }

    private sealed interface StepAttempt permits StepAttempt.Success, StepAttempt.Failure {
        record Success(SkillMigrationStepOutput output) implements StepAttempt {
        }

        record Failure(SkillMigrationFailure.Code code) implements StepAttempt {
        }
    }
}
