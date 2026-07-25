package com.yo1no.gramarye.magic.definition.store;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.definition.migration.SkillMigrationFact;
import com.yo1no.gramarye.magic.definition.migration.SkillMigrationFactCode;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Applies adjacent SavedData outer migrations without exposing either opaque blob. */
final class SkillSavedDataCarrierMigrator {
    private SkillSavedDataCarrierMigrator() {
    }

    static SkillSavedDataCarrierMigrationResult migrate(
            TokenizedSavedDataCarrierSnapshot source) {
        return migrateTo(
                source,
                SkillSavedDataCarrierMigrationPlans.production(),
                SkillSavedDataPersistenceSchema.CURRENT_SCHEMA_VERSION);
    }

    static SkillSavedDataCarrierMigrationResult migrateTo(
            TokenizedSavedDataCarrierSnapshot source,
            SkillSavedDataCarrierMigrationPlan plan,
            int currentVersion) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(plan, "plan");
        if (currentVersion < 0) {
            throw new IllegalArgumentException("currentVersion must be non-negative");
        }

        var facts = new FactCollector();
        var current = source.copyTokenizedTree();
        var probed = probe(current);
        if (probed instanceof Probe.Failure failure) {
            return failure(failure.failure(), facts);
        }
        var version = ((Probe.Success) probed).version();
        if (version > currentVersion) {
            return failure(SkillSavedDataCarrierMigrationFailure.observed(
                    SkillSavedDataCarrierMigrationFailure.Code.FUTURE_SCHEMA_VERSION,
                    version), facts);
        }

        var index = 0;
        while (version < currentVersion) {
            var step = plan.stepFrom(version);
            if (step.isEmpty()) {
                return failure(SkillSavedDataCarrierMigrationFailure.step(
                        SkillSavedDataCarrierMigrationFailure.Code.MISSING_MIGRATION_EDGE,
                        version, index), facts);
            }
            var input = new Dynamic<Tag>(NbtOps.INSTANCE, current.copy());
            StepAttempt attempt;
            try {
                attempt = apply(step.orElseThrow(), input);
            } catch (RuntimeException exception) {
                return failure(
                        SkillSavedDataCarrierMigrationFailure.stepException(
                                version, index, exception),
                        facts);
            }
            if (attempt instanceof StepAttempt.Failed failed) {
                return failure(
                        SkillSavedDataCarrierMigrationFailure.step(
                                failed.code(), version, index),
                        facts);
            }

            var output = ((StepAttempt.Applied) attempt).output().migratedTree();
            if (output.getOps() != input.getOps()) {
                return failure(SkillSavedDataCarrierMigrationFailure.step(
                        SkillSavedDataCarrierMigrationFailure.Code.STEP_CHANGED_DYNAMIC_OPS,
                        version, index), facts);
            }
            if (!(output.getValue() instanceof CompoundTag compound)) {
                return failure(SkillSavedDataCarrierMigrationFailure.step(
                        SkillSavedDataCarrierMigrationFailure.Code.INVALID_ROOT,
                        version, index), facts);
            }
            var outputProbe = probe(compound);
            if (!(outputProbe instanceof Probe.Success success)
                    || success.version() != version + 1) {
                var observed = outputProbe instanceof Probe.Success success
                        ? success.version()
                        : version;
                return failure(SkillSavedDataCarrierMigrationFailure.stepVersion(
                        SkillSavedDataCarrierMigrationFailure.Code.STEP_OUTPUT_VERSION_MISMATCH,
                        version, index, observed), facts);
            }
            try {
                OpaqueSavedDataBlobTokens.requireAllTokensPresent(compound);
            } catch (OpaqueSavedDataTokenException exception) {
                return failure(SkillSavedDataCarrierMigrationFailure.step(
                        SkillSavedDataCarrierMigrationFailure.Code
                                .OPAQUE_TOKEN_INVARIANT_VIOLATION,
                        version, index), facts);
            }

            facts.add(new SkillMigrationFact(
                    SkillMigrationFactCode.SAVED_DATA_STEP_APPLIED,
                    version, version + 1, OptionalInt.of(index)));
            current = compound.copy();
            version++;
            index++;
        }

        try {
            var carrier = OpaqueSavedDataBlobTokens.reinsertCurrent(
                    current, source.blobTable(), currentVersion);
            return new SkillSavedDataCarrierMigrationResult.Success(
                    carrier, facts.report(), index > 0);
        } catch (OpaqueSavedDataTokenException exception) {
            return failure(SkillSavedDataCarrierMigrationFailure.simple(
                    SkillSavedDataCarrierMigrationFailure.Code
                            .OPAQUE_TOKEN_INVARIANT_VIOLATION), facts);
        }
    }

    private static StepAttempt apply(
            SkillSavedDataCarrierMigrationStep step,
            Dynamic<Tag> input) {
        DataResult<SkillSavedDataCarrierMigrationStepOutput> result = step.migrate(input);
        if (result == null) {
            return new StepAttempt.Failed(
                    SkillSavedDataCarrierMigrationFailure.Code.STEP_FAILED);
        }
        if (result.error().isPresent()) {
            return result.resultOrPartial().isPresent()
                    ? new StepAttempt.Failed(
                            SkillSavedDataCarrierMigrationFailure.Code.STEP_RETURNED_PARTIAL)
                    : new StepAttempt.Failed(
                            SkillSavedDataCarrierMigrationFailure.Code.STEP_FAILED);
        }
        return result.result()
                .<StepAttempt>map(StepAttempt.Applied::new)
                .orElseGet(() -> new StepAttempt.Failed(
                        SkillSavedDataCarrierMigrationFailure.Code.STEP_FAILED));
    }

    private static Probe probe(CompoundTag root) {
        if (!root.contains(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD)) {
            return new Probe.Failure(SkillSavedDataCarrierMigrationFailure.simple(
                    SkillSavedDataCarrierMigrationFailure.Code.MISSING_SCHEMA_VERSION));
        }
        if (!(root.get(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD)
                        instanceof IntTag version)
                || version.getAsInt() < 0) {
            return new Probe.Failure(SkillSavedDataCarrierMigrationFailure.simple(
                    SkillSavedDataCarrierMigrationFailure.Code.INVALID_SCHEMA_VERSION));
        }
        return new Probe.Success(version.getAsInt());
    }

    private static SkillSavedDataCarrierMigrationResult.Failure failure(
            SkillSavedDataCarrierMigrationFailure failure,
            FactCollector facts) {
        return new SkillSavedDataCarrierMigrationResult.Failure(failure, facts.report());
    }

    private sealed interface Probe permits Probe.Success, Probe.Failure {
        record Success(int version) implements Probe {
        }

        record Failure(SkillSavedDataCarrierMigrationFailure failure) implements Probe {
        }
    }

    private sealed interface StepAttempt permits StepAttempt.Applied, StepAttempt.Failed {
        record Applied(SkillSavedDataCarrierMigrationStepOutput output) implements StepAttempt {
        }

        record Failed(SkillSavedDataCarrierMigrationFailure.Code code) implements StepAttempt {
        }
    }

    private static final class FactCollector {
        private final List<SkillMigrationFact> facts = new ArrayList<>();
        private boolean truncated;

        void add(SkillMigrationFact fact) {
            if (facts.size() < MagicSafetyCeilings.MAX_PIPELINE_FACTS) {
                facts.add(Objects.requireNonNull(fact, "fact"));
            } else {
                truncated = true;
            }
        }

        PipelineFactReport report() {
            return new PipelineFactReport(facts, truncated);
        }
    }
}
