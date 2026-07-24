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

/** Applies adjacent physical Store migrations without consulting domain Store state. */
final class StorePersistenceMigrator {
    private StorePersistenceMigrator() {
    }

    static StorePersistenceMigrationResult migrate(
            CompoundTag source,
            StorePersistenceMigrationPlan plan) {
        return migrateTo(source, plan, StorePersistenceSchema.CURRENT_SCHEMA_VERSION);
    }

    static StorePersistenceMigrationResult migrateTo(
            CompoundTag source,
            StorePersistenceMigrationPlan plan,
            int currentVersion) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(plan, "plan");
        if (currentVersion < 0) {
            throw new IllegalArgumentException("currentVersion must be non-negative");
        }

        var facts = new StoreFactCollector();
        var probed = probe(source);
        if (probed instanceof Probe.Failure failure) {
            return failure(failure.failure(), facts);
        }
        var version = ((Probe.Success) probed).version();
        if (version > currentVersion) {
            return failure(StorePersistenceMigrationFailure.observed(
                    StorePersistenceMigrationFailure.Code.FUTURE_SCHEMA_VERSION, version), facts);
        }

        var current = source.copy();
        var index = 0;
        while (version < currentVersion) {
            var step = plan.stepFrom(version);
            if (step.isEmpty()) {
                return failure(StorePersistenceMigrationFailure.step(
                        StorePersistenceMigrationFailure.Code.MISSING_MIGRATION_EDGE,
                        version, index), facts);
            }

            var input = new Dynamic<Tag>(NbtOps.INSTANCE, current.copy());
            StepAttempt attempt;
            try {
                attempt = apply(step.orElseThrow(), input);
            } catch (RuntimeException exception) {
                return failure(
                        StorePersistenceMigrationFailure.stepException(version, index, exception),
                        facts);
            }
            if (attempt instanceof StepAttempt.Failed failed) {
                return failure(
                        StorePersistenceMigrationFailure.step(failed.code(), version, index), facts);
            }

            var output = ((StepAttempt.Applied) attempt).output().migratedTree();
            if (output.getOps() != input.getOps()) {
                return failure(StorePersistenceMigrationFailure.step(
                        StorePersistenceMigrationFailure.Code.STEP_CHANGED_DYNAMIC_OPS,
                        version, index), facts);
            }
            if (!(output.getValue() instanceof CompoundTag compound)) {
                return failure(StorePersistenceMigrationFailure.step(
                        StorePersistenceMigrationFailure.Code.INVALID_ROOT,
                        version, index), facts);
            }
            var outputProbe = probe(compound);
            if (!(outputProbe instanceof Probe.Success success)
                    || success.version() != version + 1) {
                var observed = outputProbe instanceof Probe.Success success
                        ? success.version() : version;
                return failure(StorePersistenceMigrationFailure.stepVersion(
                        StorePersistenceMigrationFailure.Code.STEP_OUTPUT_VERSION_MISMATCH,
                        version, index, observed), facts);
            }

            facts.add(new SkillMigrationFact(
                    SkillMigrationFactCode.STORE_STEP_APPLIED,
                    version, version + 1, OptionalInt.of(index)));
            current = compound.copy();
            version++;
            index++;
        }

        return new StorePersistenceMigrationResult.Success(current, facts.report(), index > 0);
    }

    private static StepAttempt apply(
            StorePersistenceMigrationStep step,
            Dynamic<Tag> input) {
        DataResult<StorePersistenceMigrationStepOutput> result = step.migrate(input);
        if (result == null) {
            return new StepAttempt.Failed(StorePersistenceMigrationFailure.Code.STEP_FAILED);
        }
        if (result.error().isPresent()) {
            return result.resultOrPartial().isPresent()
                    ? new StepAttempt.Failed(
                            StorePersistenceMigrationFailure.Code.STEP_RETURNED_PARTIAL)
                    : new StepAttempt.Failed(StorePersistenceMigrationFailure.Code.STEP_FAILED);
        }
        return result.result()
                .<StepAttempt>map(StepAttempt.Applied::new)
                .orElseGet(() -> new StepAttempt.Failed(
                        StorePersistenceMigrationFailure.Code.STEP_FAILED));
    }

    private static Probe probe(CompoundTag root) {
        if (!root.contains("store_schema_version")) {
            return new Probe.Failure(StorePersistenceMigrationFailure.simple(
                    StorePersistenceMigrationFailure.Code.MISSING_SCHEMA_VERSION));
        }
        if (!(root.get("store_schema_version") instanceof IntTag version)
                || version.getAsInt() < 0) {
            return new Probe.Failure(StorePersistenceMigrationFailure.simple(
                    StorePersistenceMigrationFailure.Code.INVALID_SCHEMA_VERSION));
        }
        return new Probe.Success(version.getAsInt());
    }

    private static StorePersistenceMigrationResult.Failure failure(
            StorePersistenceMigrationFailure failure,
            StoreFactCollector facts) {
        return new StorePersistenceMigrationResult.Failure(failure, facts.report());
    }

    private sealed interface Probe permits Probe.Success, Probe.Failure {
        record Success(int version) implements Probe {
        }

        record Failure(StorePersistenceMigrationFailure failure) implements Probe {
        }
    }

    private sealed interface StepAttempt permits StepAttempt.Applied, StepAttempt.Failed {
        record Applied(StorePersistenceMigrationStepOutput output) implements StepAttempt {
        }

        record Failed(StorePersistenceMigrationFailure.Code code) implements StepAttempt {
        }
    }

    private static final class StoreFactCollector {
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
