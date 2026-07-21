package com.yo1no.gramarye.magic.definition.migration;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.tree.DynamicTreeBounds;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.OptionalInt;

/** Internal bounded orchestrator for one descriptor-owned payload migration chain. */
final class PayloadMigrator {
    private PayloadMigrator() {
    }

    static Result migrate(
            DefinitionEnvelope source,
            int currentSchemaVersion,
            PayloadMigrationPlan plan,
            int nodeIndex,
            PipelineFactCollector facts) {
        if (currentSchemaVersion < 0) {
            throw new IllegalArgumentException("currentSchemaVersion must be non-negative");
        }
        if (source.schemaVersion() > currentSchemaVersion) {
            return new Result.Failure(PayloadMigrationFailure.future(source.schemaVersion()));
        }

        var currentEnvelope = source;
        var version = source.schemaVersion();
        var stepIndex = 0;
        while (version < currentSchemaVersion) {
            var step = plan.stepFrom(version);
            if (step.isEmpty()) {
                return new Result.Failure(PayloadMigrationFailure.forStep(
                        PayloadMigrationFailure.Code.MISSING_MIGRATION_EDGE,
                        version,
                        version + 1,
                        stepIndex));
            }

            StepAttempt attempt;
            var selectedStep = step.orElseThrow();
            try {
                attempt = applyStep(
                        source.typeId(),
                        selectedStep,
                        currentEnvelope.copyRawPayload());
            } catch (RuntimeException exception) {
                return new Result.Failure(PayloadMigrationFailure.forStepException(
                        version, version + 1, stepIndex, exception));
            }
            if (attempt instanceof StepAttempt.Failure failed) {
                return new Result.Failure(PayloadMigrationFailure.forStep(
                        failed.code(), version, version + 1, stepIndex));
            }

            currentEnvelope = ((StepAttempt.Success) attempt).envelope();
            facts.add(new SkillMigrationFact(
                    SkillMigrationFactCode.PAYLOAD_STEP_APPLIED,
                    version,
                    version + 1,
                    OptionalInt.of(stepIndex),
                    OptionalInt.of(nodeIndex)));
            version++;
            stepIndex++;
        }
        return new Result.Success(currentEnvelope);
    }

    private static StepAttempt applyStep(
            net.minecraft.resources.ResourceLocation typeId,
            PayloadMigrationStep step,
            Dynamic<?> defensiveInput) {
        return applyStepCaptured(typeId, step, defensiveInput);
    }

    private static <T> StepAttempt applyStepCaptured(
            net.minecraft.resources.ResourceLocation typeId,
            PayloadMigrationStep step,
            Dynamic<T> defensiveInput) {
        DataResult<PayloadMigrationStepOutput<T>> result = step.migrate(defensiveInput);
        if (result == null) {
            return new StepAttempt.Failure(PayloadMigrationFailure.Code.STEP_FAILED);
        }
        if (result.error().isPresent()) {
            return result.resultOrPartial().isPresent()
                    ? new StepAttempt.Failure(
                            PayloadMigrationFailure.Code.STEP_RETURNED_PARTIAL)
                    : new StepAttempt.Failure(PayloadMigrationFailure.Code.STEP_FAILED);
        }
        var output = result.result();
        if (output.isEmpty()) {
            return new StepAttempt.Failure(PayloadMigrationFailure.Code.STEP_FAILED);
        }

        var migratedPayload = output.orElseThrow().migratedPayload();
        if (migratedPayload.getOps() != defensiveInput.getOps()) {
            return new StepAttempt.Failure(
                    PayloadMigrationFailure.Code.STEP_CHANGED_DYNAMIC_OPS);
        }
        var bounds = DynamicTreeBounds.check(
                migratedPayload,
                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH,
                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES);
        var boundsFailure = switch (bounds) {
            case WITHIN_LIMITS -> null;
            case DEPTH_EXCEEDED -> PayloadMigrationFailure.Code.PAYLOAD_TREE_DEPTH_EXCEEDED;
            case NODE_COUNT_EXCEEDED ->
                    PayloadMigrationFailure.Code.PAYLOAD_TREE_NODE_LIMIT_EXCEEDED;
            case KEY_LENGTH_EXCEEDED ->
                    PayloadMigrationFailure.Code.PAYLOAD_KEY_LENGTH_EXCEEDED;
            case UNSUPPORTED -> PayloadMigrationFailure.Code.STEP_FAILED;
        };
        if (boundsFailure != null) {
            return new StepAttempt.Failure(boundsFailure);
        }

        return new StepAttempt.Success(new DefinitionEnvelope(
                typeId,
                step.toVersion(),
                migratedPayload));
    }

    sealed interface Result permits Result.Success, Result.Failure {
        record Success(DefinitionEnvelope transientEnvelope) implements Result {
        }

        record Failure(PayloadMigrationFailure failure) implements Result {
        }
    }

    private sealed interface StepAttempt permits StepAttempt.Success, StepAttempt.Failure {
        record Success(DefinitionEnvelope envelope) implements StepAttempt {
        }

        record Failure(PayloadMigrationFailure.Code code) implements StepAttempt {
        }
    }
}
